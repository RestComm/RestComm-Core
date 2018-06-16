/*
 * TeleStax, Open Source Cloud Communications
 * Copyright 2011-2014, Telestax Inc and individual contributors
 * by the @authors tag.
 *
 * This program is free software: you can redistribute it and/or modify
 * under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation; either version 3 of
 * the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 *
 */

package org.restcomm.connect.mscontrol.mms;

import akka.actor.ActorRef;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import jain.protocol.ip.mgcp.message.parms.ConnectionIdentifier;
import jain.protocol.ip.mgcp.message.parms.ConnectionMode;
import jain.protocol.ip.mgcp.pkg.MgcpEvent;
import org.apache.commons.configuration.Configuration;
import org.mobicents.protocols.mgcp.jain.pkg.AUMgcpEvent;
import org.restcomm.connect.commons.configuration.RestcommConfiguration;
import org.restcomm.connect.commons.fsm.Action;
import org.restcomm.connect.commons.fsm.FiniteStateMachine;
import org.restcomm.connect.commons.fsm.State;
import org.restcomm.connect.commons.fsm.Transition;
import org.restcomm.connect.commons.patterns.Observe;
import org.restcomm.connect.commons.patterns.Observing;
import org.restcomm.connect.commons.patterns.StopObserving;
import org.restcomm.connect.mgcp.AsrSignal;
import org.restcomm.connect.mgcp.CreateIvrEndpoint;
import org.restcomm.connect.mgcp.CreateLink;
import org.restcomm.connect.mgcp.DestroyEndpoint;
import org.restcomm.connect.mgcp.DestroyLink;
import org.restcomm.connect.mgcp.EndpointState;
import org.restcomm.connect.mgcp.EndpointStateChanged;
import org.restcomm.connect.mgcp.InitializeLink;
import org.restcomm.connect.mgcp.IvrEndpointResponse;
import org.restcomm.connect.mgcp.LinkStateChanged;
import org.restcomm.connect.mgcp.MediaGatewayResponse;
import org.restcomm.connect.mgcp.MediaSession;
import org.restcomm.connect.mgcp.OpenLink;
import org.restcomm.connect.mgcp.PlayCollect;
import org.restcomm.connect.mgcp.PlayRecord;
import org.restcomm.connect.mgcp.StopEndpoint;
import org.restcomm.connect.mgcp.UpdateLink;
import org.restcomm.connect.mscontrol.api.MediaGroup;
import org.restcomm.connect.mscontrol.api.messages.Collect;
import org.restcomm.connect.mscontrol.api.messages.CollectedResult;
import org.restcomm.connect.mscontrol.api.messages.Join;
import org.restcomm.connect.mscontrol.api.messages.MediaGroupResponse;
import org.restcomm.connect.mscontrol.api.messages.MediaGroupStateChanged;
import org.restcomm.connect.mscontrol.api.messages.MediaGroupStatus;
import org.restcomm.connect.mscontrol.api.messages.Play;
import org.restcomm.connect.mscontrol.api.messages.Record;
import org.restcomm.connect.mscontrol.api.messages.StartMediaGroup;
import org.restcomm.connect.mscontrol.api.messages.Stop;
import org.restcomm.connect.mscontrol.api.messages.StopMediaGroup;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @author quintana.thomas@gmail.com (Thomas Quintana)
 * @author maria.farooq@telestax.com (Maria Farooq)
 */
public class MgcpMediaGroup extends MediaGroup {
    private final LoggingAdapter logger = Logging.getLogger(getContext().system(), this);

    // Finite state machine stuff.
    protected final State uninitialized;
    protected final State active;
    protected final State inactive;
    // Special intermediate states.
    protected final State acquiringIvr;
    protected final State acquiringLink;
    protected final State initializingLink;
    protected final State openingLink;
    protected final State updatingLink;
    protected final State deactivating;
    // Join Outboundcall Bridge endpoint to the IVR
    protected final State acquiringInternalLink;
    protected final State initializingInternalLink;
    protected final State openingInternalLink;
    protected final State updatingInternalLink;

    // FSM.
    protected FiniteStateMachine fsm;
    // The user specific configuration.
    Configuration configuration = null;

    // MGCP runtime stuff.
    protected final ActorRef gateway;
    protected final ActorRef endpoint;
    protected final MediaSession session;
    protected ActorRef link;
    protected final String ivrEndpointName;
    protected ActorRef ivr;
    protected boolean ivrInUse;
    protected MgcpEvent lastEvent;

    // Runtime stuff.
    protected final List<ActorRef> observers;
    protected ActorRef originator;

    protected ActorRef internalLinkEndpoint;
    protected ActorRef internalLink;
    protected ConnectionMode internalLinkMode;

    protected ConnectionIdentifier ivrConnectionIdentifier;
    protected final String primaryEndpointId;
    protected final String secondaryEndpointId;

    private boolean recording;

    public MgcpMediaGroup(final ActorRef gateway, final MediaSession session, final ActorRef endpoint) {
        this(gateway, session, endpoint, null, null);
    }

    public MgcpMediaGroup(final ActorRef gateway, final MediaSession session, final ActorRef endpoint, final String ivrEndpointName) {
        this(gateway, session, endpoint, ivrEndpointName, null);
    }

    public MgcpMediaGroup(final ActorRef gateway, final MediaSession session, final ActorRef endpoint, final String ivrEndpointName, final ConnectionIdentifier ivrConnectionIdentifier) {
        this(gateway, session, endpoint, ivrEndpointName, ivrConnectionIdentifier, null, null);
    }

    public MgcpMediaGroup(final ActorRef gateway, final MediaSession session, final ActorRef endpoint, final String ivrEndpointName, final ConnectionIdentifier ivrConnectionIdentifier, final String primaryEndpointId, final String secondaryEndpointId) {
        super();
        final ActorRef source = self();
        if(logger.isDebugEnabled())
            logger.debug("MgcpMediaGroup: "+ source.path() + " gateway: "+gateway
                    + " session: "+session + " endpoint: "+endpoint + " ivrEndpointName: "
                    + ivrEndpointName + " ivrConnectionIdentifier: "+ivrConnectionIdentifier
                    + " primaryEndpointId: "+primaryEndpointId+" secondaryEndpointId: "
                    + secondaryEndpointId);
        // Initialize the states for the FSM.
        uninitialized = new State("uninitialized", null, null);
        active = new State("active", new Active(source), null);
        inactive = new State("inactive", new Inactive(source), null);
        acquiringIvr = new State("acquiring ivr", new AcquiringIvr(source), null);
        acquiringLink = new State("acquiring link", new AcquiringLink(source), null);
        initializingLink = new State("initializing link", new InitializingLink(source), null);
        openingLink = new State("opening link", new OpeningLink(source), null);
        updatingLink = new State("updating link", new UpdatingLink(source), null);
        deactivating = new State("deactivating", new Deactivating(source), null);
        acquiringInternalLink = new State("acquiring internal link", new AcquiringInternalLink(source), null);
        initializingInternalLink = new State("initializing internal link", new InitializingInternalLink(source), null);
        openingInternalLink = new State("opening internal link", new OpeningInternalLink(source), null);
        updatingInternalLink = new State("updating internal link", new UpdatingInternalLink(source), null);
        // Initialize the transitions for the FSM.
        final Set<Transition> transitions = new HashSet<Transition>();
        transitions.add(new Transition(uninitialized, acquiringIvr));
        transitions.add(new Transition(acquiringIvr, inactive));
        transitions.add(new Transition(acquiringIvr, acquiringLink));
        transitions.add(new Transition(acquiringLink, inactive));
        transitions.add(new Transition(acquiringLink, initializingLink));
        transitions.add(new Transition(initializingLink, inactive));
        transitions.add(new Transition(initializingLink, openingLink));
        transitions.add(new Transition(openingLink, inactive));
        transitions.add(new Transition(openingLink, deactivating));
        transitions.add(new Transition(openingLink, updatingLink));
        transitions.add(new Transition(updatingLink, active));
        transitions.add(new Transition(updatingLink, inactive));
        transitions.add(new Transition(updatingLink, deactivating));
        transitions.add(new Transition(active, deactivating));
        transitions.add(new Transition(deactivating, inactive));
        transitions.add(new Transition(active, acquiringIvr));
        // Join Outbound call Bridge endpoint to IVR endpoint
        transitions.add(new Transition(active, acquiringInternalLink));
        transitions.add(new Transition(acquiringInternalLink, initializingInternalLink));
        transitions.add(new Transition(initializingInternalLink, openingInternalLink));
        transitions.add(new Transition(openingInternalLink, updatingInternalLink));
        transitions.add(new Transition(updatingInternalLink, active));

        // Initialize the FSM.
        this.fsm = new FiniteStateMachine(uninitialized, transitions);
        // Initialize the MGCP state.
        this.gateway = gateway;
        this.session = session;
        this.endpoint = endpoint;
        this.ivrInUse = false;
        this.ivrEndpointName = ivrEndpointName;
        this.ivrConnectionIdentifier = ivrConnectionIdentifier;
        this.primaryEndpointId = primaryEndpointId;
        this.secondaryEndpointId = secondaryEndpointId;
        // Initialize the rest of the media group state.
        this.observers = new ArrayList<ActorRef>();
    }

    protected void collect(final Object message) {
        final ActorRef self = self();
        final Collect request = (Collect) message;
        stop(lastEvent);

        Object signal;
        if (request.type() == Collect.Type.DTMF) {
            final PlayCollect.Builder builder = PlayCollect.builder();
            for (final URI prompt : request.prompts()) {
                builder.addPrompt(prompt);
            }
            builder.setClearDigitBuffer(true);
            builder.setDigitPattern(request.pattern());
            builder.setFirstDigitTimer(request.timeout());
            builder.setInterDigitTimer(request.timeout());
            builder.setEndInputKey(request.endInputKey());
            builder.setStartInputKey(request.startInputKey());
            builder.setMaxNumberOfDigits(request.numberOfDigits());
            signal = builder.build();
            this.lastEvent = AUMgcpEvent.aupc;
        } else {
            this.lastEvent = AsrSignal.REQUEST_ASR;
            signal = new AsrSignal(request.getDriver(), request.lang(), request.prompts(), request.endInputKey(), RestcommConfiguration.getInstance().getMgAsr().getAsrMRT(), request.timeout(),
                    request.timeout(), request.getHints(), request.type().toString() ,request.numberOfDigits(), request.needPartialResult());
        }
        this.originator = sender();
        ivr.tell(signal, self);
        ivrInUse = true;
    }

    protected void play(final Object message) {
        final ActorRef self = self();
        final Play request = (Play) message;
        final List<URI> uris = request.uris();
        final int iterations = request.iterations();
        final org.restcomm.connect.mgcp.Play play = new org.restcomm.connect.mgcp.Play(uris, iterations);
        stop(lastEvent);
        this.lastEvent = AUMgcpEvent.aupa;
        this.originator = sender();
        ivr.tell(play, self);
        ivrInUse = true;
    }

    @SuppressWarnings("unchecked")
    protected void notification(final Object message) {
        final IvrEndpointResponse response = (IvrEndpointResponse) message;
        Object ivrResponse = response.get();
        final ActorRef self = self();
        MediaGroupResponse<CollectedResult> event;
        org.restcomm.connect.mgcp.CollectedResult mgcpCollectedResult = null;
        if (response.succeeded()) {
            mgcpCollectedResult = (org.restcomm.connect.mgcp.CollectedResult)ivrResponse;
            event = new MediaGroupResponse<>(new CollectedResult(mgcpCollectedResult.getResult(), mgcpCollectedResult.isAsr(), mgcpCollectedResult.isPartial()));
        } else {
            event = new MediaGroupResponse<>(response.cause(), response.error());
        }
        if (originator != null) {
            this.originator.tell(event, self);
            if (recording) {
                recording = false;
            }
        }
        if (ivrResponse == null || (mgcpCollectedResult != null && !(mgcpCollectedResult.isPartial()))) {
            ivrInUse = false;
        }
    }

    protected void observe(final Object message) {
        final ActorRef self = self();
        final Observe request = (Observe) message;
        final ActorRef observer = request.observer();
        if (observer != null) {
            observers.add(observer);
            observer.tell(new Observing(self), self);
        }
    }

    // FSM logic.
    @Override
    public void onReceive(final Object message) throws Exception {
        final Class<?> klass = message.getClass();
        final State state = fsm.state();
        final ActorRef sender = sender();

        if(logger.isInfoEnabled()) {
            logger.info("********** Media Group " + self().path() + " Current State: \"" + state.toString());
            logger.info("********** Media Group " + self().path() + " Processing Message: \"" + klass.getName() + " sender : "
                + sender.getClass());
        }

        if (Observe.class.equals(klass)) {
            observe(message);
        } else if (StopObserving.class.equals(klass)) {
            stopObserving(message);
        } else if (MediaGroupStatus.class.equals(klass)) {
            if (active.equals(state)) {
                sender().tell(new MediaGroupStateChanged(MediaGroupStateChanged.State.ACTIVE, ivr, ivrConnectionIdentifier), self());
            } else {
                sender().tell(new MediaGroupStateChanged(MediaGroupStateChanged.State.INACTIVE, ivr, ivrConnectionIdentifier), self());
            }
        } else if (StartMediaGroup.class.equals(klass)) {
            if(logger.isInfoEnabled()) {
                logger.info("MediaGroup: " + self().path() + " got StartMediaGroup from: " + sender().path() + " endpoint: "
                    + endpoint.path() + " isTerminated: " + endpoint.isTerminated());
            }
            fsm.transition(message, acquiringIvr);
        } else if (Join.class.equals(klass)) {
            fsm.transition(message, acquiringInternalLink);
        } else if (MediaGatewayResponse.class.equals(klass)) {
            if (acquiringIvr.equals(state)) {
                fsm.transition(message, acquiringLink);
            } else if (acquiringLink.equals(state)) {
                fsm.transition(message, initializingLink);
            } else if (acquiringInternalLink.equals(state)) {
                fsm.transition(message, initializingInternalLink);
            }
        } else if (LinkStateChanged.class.equals(klass)) {
            final LinkStateChanged response = (LinkStateChanged) message;
            if (LinkStateChanged.State.CLOSED == response.state()) {
                if (initializingLink.equals(state)) {
                    fsm.transition(message, openingLink);
                } else if (openingLink.equals(state) || deactivating.equals(state) || updatingLink.equals(state)) {
                    fsm.transition(message, inactive);
                }
                if (initializingInternalLink.equals(state)) {
                    fsm.transition(message, openingInternalLink);
                }
            } else if (LinkStateChanged.State.OPEN == response.state()) {
                ivrConnectionIdentifier = response.connectionIdentifier();
                if (openingLink.equals(state)) {
                    fsm.transition(message, updatingLink);
                } else if (updatingLink.equals(state)) {
                    fsm.transition(message, active);
                }
                if (openingInternalLink.equals(state)) {
                    fsm.transition(message, updatingInternalLink);
                }
                if (updatingInternalLink.equals(state)) {
                    fsm.transition(message, active);
                }
            }
        } else if (StopMediaGroup.class.equals(klass)) {
            if (logger.isInfoEnabled()) {
                logger.info("Got StopMediaGroup, current state: "+fsm.state().toString());
            }
            if (acquiringLink.equals(state) || initializingLink.equals(state)) {
                fsm.transition(message, inactive);
            } else if (active.equals(state) || openingLink.equals(state) || updatingLink.equals(state)) {
                fsm.transition(message, deactivating);
            }
        } else if (EndpointStateChanged.class.equals(klass)) {
            onEndpointStateChanged((EndpointStateChanged) message, self(), sender);
        } else if (active.equals(state)) {
            if (Play.class.equals(klass)) {
                if(logger.isDebugEnabled())
                    logger.debug("MgcpMediaGroup: got a request to play something at conference ivr..");
                play(message);
            } else if (Collect.class.equals(klass)) {
                collect(message);
            } else if (Record.class.equals(klass)) {
                record(message);
            } else if (Stop.class.equals(klass)) {
                stop(lastEvent);
                // Send message to originator telling media group has been stopped
                // Needed for call bridging scenario, where inbound call must stop
                // ringing before attempting to perform join operation.

                if (!recording)
                    sender().tell(new MediaGroupResponse<String>("stopped"), self());
            } else if (IvrEndpointResponse.class.equals(klass)) {
                notification(message);
            }
        } else if (ivrInUse) {
            if (Stop.class.equals(klass)) {
                stop(lastEvent);
            }
        }
    }

    protected boolean is(State state) {
        return state != null && state.equals(this.fsm.state());
    }

    protected void onEndpointStateChanged(EndpointStateChanged message, ActorRef self, ActorRef sender) throws Exception {
        if (is(deactivating)) {
            if (sender.equals(this.ivr) && (EndpointState.DESTROYED.equals(message.getState()) || EndpointState.FAILED.equals(message.getState()))) {
                if(EndpointState.FAILED.equals(message.getState()))
                    logger.error("Could not destroy ivr endpoint on media server: " + this.ivrEndpointName + ". corresponding actor path is: " + this.ivr.path());
                this.ivr.tell(new StopObserving(self), self);
                this.fsm.transition(message, inactive);
            }
        }
    }

    protected void record(final Object message) {
        final ActorRef self = self();
        final Record request = (Record) message;
        final PlayRecord.Builder builder = PlayRecord.builder();
        for (final URI prompt : request.prompts()) {
            builder.addPrompt(prompt);
        }
        builder.setClearDigitBuffer(true);
        builder.setPreSpeechTimer(request.timeout());
        builder.setPostSpeechTimer(request.timeout());
        builder.setRecordingLength(request.length());
        if (request.endInputKey() != null && !request.endInputKey().equals("-1")) {
            builder.setEndInputKey(request.endInputKey());
        } else {
            builder.setEndInputKey("null");
        }
        builder.setRecordingId(request.destination());
        stop(lastEvent);
        this.lastEvent = AUMgcpEvent.aupr;
        this.originator = sender();
        ivr.tell(builder.build(), self);
        ivrInUse = true;
        recording = true;
    }

    protected void stop(MgcpEvent signal) {
        if (ivrInUse || (lastEvent != null && lastEvent.getName().equalsIgnoreCase("pr"))) {
            if (logger.isInfoEnabled()) {
                String msg = String.format("About to stop IVR, with signal %s, last event %s", signal.toString(), lastEvent.toString());
                logger.info(msg);
            }
            final ActorRef self = self();
            ivr.tell(new StopEndpoint(signal), self);
            ivrInUse = false;
        }
    }

    protected void stopObserving(final Object message) {
        final StopObserving request = (StopObserving) message;
        final ActorRef observer = request.observer();
        if (observer != null) {
            observers.remove(observer);
        }
    }

    protected abstract class AbstractAction implements Action {
        protected final ActorRef source;

        public AbstractAction(final ActorRef source) {
            super();
            this.source = source;
        }
    }

    protected final class AcquiringIvr extends AbstractAction {
        public AcquiringIvr(final ActorRef source) {
            super(source);
        }

        @Override
        public void execute(final Object message) throws Exception {
            if (ivr != null && !ivr.isTerminated()) {
                if(logger.isInfoEnabled()) {
                    logger.info("MediaGroup :" + self().path()
                        + " got request to create ivr endpoint, will stop the existing one first: " + ivr.path());
                }
                gateway.tell(new DestroyEndpoint(ivr), null);
                getContext().stop(ivr);
                ivr = null;
            }
            if(logger.isInfoEnabled()) {
                logger.info("MediaGroup :" + self().path() + " state: " + fsm.state().toString() + " session: " + session.id()
                    + " will ask to get IvrEndpoint: "+ivrEndpointName);
            }
            gateway.tell(new CreateIvrEndpoint(session, ivrEndpointName), source);
        }
    }

    protected final class AcquiringLink extends AbstractAction {
        public AcquiringLink(final ActorRef source) {
            super(source);
        }

        @SuppressWarnings("unchecked")
        @Override
        public void execute(final Object message) throws Exception {
            final MediaGatewayResponse<ActorRef> response = (MediaGatewayResponse<ActorRef>) message;
            ivr = response.get();
            ivr.tell(new Observe(source), source);
            if (link != null && !link.isTerminated()) {
                if(logger.isInfoEnabled()) {
                    logger.info("MediaGroup :" + self().path()
                        + " got request to create link endpoint, will stop the existing one first: " + link.path());
                }
                gateway.tell(new DestroyLink(link), null);
                getContext().stop(link);
            }
            if(logger.isInfoEnabled()) {
                logger.info("MediaGroup :" + self().path() + " state: " + fsm.state().toString() + " session: " + session.id()
                    + " ivr endpoint: " + ivr.path() + " will ask to get Link");
            }
            gateway.tell(new CreateLink(session, ivrConnectionIdentifier), source);
        }
    }

    protected final class InitializingLink extends AbstractAction {
        public InitializingLink(final ActorRef source) {
            super(source);
        }

        @SuppressWarnings("unchecked")
        @Override
        public void execute(final Object message) throws Exception {
            final MediaGatewayResponse<ActorRef> response = (MediaGatewayResponse<ActorRef>) message;
            link = response.get();
            if (endpoint == null)
                if(logger.isInfoEnabled()) {
                    logger.info("MediaGroup :" + self().path() + " state: " + fsm.state().toString() + " session: " + session.id()
                        + " link: " + link.path() + " endpoint is null will have exception");
                }
            link.tell(new Observe(source), source);
            link.tell(new InitializeLink(endpoint, ivr), source);
            if(logger.isInfoEnabled()) {
                logger.info("MediaGroup :" + self().path() + " state: " + fsm.state().toString() + " session: " + session.id()
                    + " link: " + link.path() + " endpoint: " + endpoint.path()
                    + " initializeLink sent, endpoint isTerminated: " + endpoint.isTerminated());
            }
        }
    }

    protected final class OpeningLink extends AbstractAction {
        public OpeningLink(final ActorRef source) {
            super(source);
        }

        @Override
        public void execute(final Object message) throws Exception {
            if(logger.isInfoEnabled()) {
                logger.info("MediaGroup :" + self().path() + " state: " + fsm.state().toString() + " session: " + session.id()
                    + " link: " + link.path() + " will ask to open Link with primaryEndpointId: "+ primaryEndpointId+" secondaryEndpointId: "+secondaryEndpointId);
            }
            link.tell(new OpenLink(ConnectionMode.SendRecv, primaryEndpointId, secondaryEndpointId), source);
        }
    }

    protected final class UpdatingLink extends AbstractAction {
        public UpdatingLink(final ActorRef source) {
            super(source);
        }

        @Override
        public void execute(final Object message) throws Exception {
            final UpdateLink update = new UpdateLink(ConnectionMode.SendRecv, UpdateLink.Type.PRIMARY);
            link.tell(update, source);
        }
    }

    // Join OutboundCall Bridge endpoint to the IVR endpoint for recording - START
    protected final class AcquiringInternalLink extends AbstractAction {
        public AcquiringInternalLink(final ActorRef source) {
            super(source);
        }

        @Override
        public void execute(final Object message) throws Exception {
            final Class<?> klass = message.getClass();
            if (Join.class.equals(klass)) {
                final Join request = (Join) message;
                internalLinkEndpoint = request.endpoint();
                internalLinkMode = request.mode();
            }
            gateway.tell(new CreateLink(session, ivrConnectionIdentifier), source);
        }
    }

    protected final class InitializingInternalLink extends AbstractAction {
        public InitializingInternalLink(final ActorRef source) {
            super(source);
        }

        @SuppressWarnings("unchecked")
        @Override
        public void execute(Object message) throws Exception {
            final MediaGatewayResponse<ActorRef> response = (MediaGatewayResponse<ActorRef>) message;
            internalLink = response.get();
            internalLink.tell(new Observe(source), source);
            internalLink.tell(new InitializeLink(internalLinkEndpoint, ivr), source);
        }
    }

    protected final class OpeningInternalLink extends AbstractAction {
        public OpeningInternalLink(final ActorRef source) {
            super(source);
        }

        @Override
        public void execute(Object message) throws Exception {
            internalLink.tell(new OpenLink(internalLinkMode), source);
        }
    }

    protected final class UpdatingInternalLink extends AbstractAction {
        public UpdatingInternalLink(final ActorRef source) {
            super(source);
        }

        @Override
        public void execute(final Object message) throws Exception {
            final UpdateLink update = new UpdateLink(ConnectionMode.SendRecv, UpdateLink.Type.PRIMARY);
            internalLink.tell(update, source);
        }
    }

    // Join OutboundCall Bridge endpoint to the IVR endpoint for recording - END

    protected final class Active extends AbstractAction {
        public Active(final ActorRef source) {
            super(source);
        }

        @Override
        public void execute(final Object message) throws Exception {
            // Notify the observers.
            final MediaGroupStateChanged event = new MediaGroupStateChanged(MediaGroupStateChanged.State.ACTIVE, ivr, ivrConnectionIdentifier);
            for (final ActorRef observer : observers) {
                observer.tell(event, source);
            }
        }
    }

    protected final class Inactive extends AbstractAction {
        public Inactive(final ActorRef source) {
            super(source);
        }

        @Override
        public void execute(final Object message) throws Exception {
            if (link != null) {
                gateway.tell(new DestroyLink(link), source);
                link = null;
            }

            if (internalLink != null) {
                gateway.tell(new DestroyLink(internalLink), source);
                internalLink = null;
            }

            // Notify the observers.
            final MediaGroupStateChanged event = new MediaGroupStateChanged(MediaGroupStateChanged.State.INACTIVE, ivr, ivrConnectionIdentifier);
            for (final ActorRef observer : observers) {
                observer.tell(event, source);
            }
        }
    }

    protected final class Deactivating extends AbstractAction {
        public Deactivating(final ActorRef source) {
            super(source);
        }

        @Override
        public void execute(final Object message) throws Exception {
            ivr.tell(new DestroyEndpoint(), super.source);
//            if (link != null)
//                link.tell(new CloseLink(), source);
//            if (internalLink != null)
//                internalLink.tell(new CloseLink(), source);
        }
    }

    @Override
    public void postStop() {
        if (internalLinkEndpoint != null) {
            if(logger.isInfoEnabled()) {
                logger.info("MediaGroup: " + self().path() + " at postStop, about to stop intenalLinkEndpoint: "
                    + internalLinkEndpoint.path() + " sender: " + sender().path());
            }
            gateway.tell(new DestroyEndpoint(internalLinkEndpoint), null);
            getContext().stop(internalLinkEndpoint);
            internalLinkEndpoint = null;
        }
        if (ivr != null) {
            if(logger.isInfoEnabled()) {
                logger.info("MediaGroup :" + self().path() + " at postStop, about to stop ivr endpoint :" + ivr.path());
            }
            gateway.tell(new DestroyEndpoint(ivr), null);
            getContext().stop(ivr);
            ivr = null;
        }
        if(link != null) {
            if(logger.isInfoEnabled()) {
                logger.info("MediaGroup :" + self().path() + " at postStop, about to stop link :" + link.path());
            }
            getContext().stop(link);
            link = null;
        }
        if(internalLink != null) {
            if(logger.isInfoEnabled()) {
                logger.info("MediaGroup :" + self().path() + " at postStop, about to stop internal link :" + internalLink.path());
            }
            getContext().stop(link);
            link = null;
        }
        getContext().stop(self());
        super.postStop();
    }

}
