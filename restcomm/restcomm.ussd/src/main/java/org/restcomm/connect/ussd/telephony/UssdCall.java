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
package org.restcomm.connect.ussd.telephony;

import akka.actor.ActorRef;
import akka.actor.UntypedActorContext;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import org.joda.time.DateTime;
import org.restcomm.connect.commons.configuration.RestcommConfiguration;
import org.restcomm.connect.commons.dao.Sid;
import org.restcomm.connect.commons.faulttolerance.RestcommUntypedActor;
import org.restcomm.connect.commons.fsm.Action;
import org.restcomm.connect.commons.fsm.FiniteStateMachine;
import org.restcomm.connect.commons.fsm.State;
import org.restcomm.connect.commons.fsm.Transition;
import org.restcomm.connect.commons.fsm.TransitionEndListener;
import org.restcomm.connect.commons.patterns.Observe;
import org.restcomm.connect.commons.patterns.Observing;
import org.restcomm.connect.commons.patterns.StopObserving;
import org.restcomm.connect.commons.telephony.CreateCallType;
import org.restcomm.connect.commons.util.DNSUtils;
import org.restcomm.connect.dao.CallDetailRecordsDao;
import org.restcomm.connect.dao.entities.CallDetailRecord;
import org.restcomm.connect.telephony.api.Answer;
import org.restcomm.connect.telephony.api.CallInfo;
import org.restcomm.connect.telephony.api.CallResponse;
import org.restcomm.connect.telephony.api.CallStateChanged;
import org.restcomm.connect.telephony.api.GetCallInfo;
import org.restcomm.connect.telephony.api.GetCallObservers;
import org.restcomm.connect.telephony.api.InitializeOutbound;
import org.restcomm.connect.telephony.api.events.UssdStreamEvent;
import org.restcomm.connect.ussd.commons.UssdRestcommResponse;
import org.restcomm.connect.ussd.interpreter.UssdInterpreter;
import scala.concurrent.duration.Duration;

import javax.servlet.sip.Address;
import javax.servlet.sip.ServletParseException;
import javax.servlet.sip.SipApplicationSession;
import javax.servlet.sip.SipFactory;
import javax.servlet.sip.SipServletMessage;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;
import javax.servlet.sip.SipSession;
import javax.servlet.sip.SipURI;
import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Currency;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.concurrent.TimeUnit;

/**
 * @author <a href="mailto:gvagenas@gmail.com">gvagenas</a>
 *
 */
public class UssdCall extends RestcommUntypedActor implements TransitionEndListener {

    private final LoggingAdapter logger = Logging.getLogger(getContext().system(), this);

    private final String ussdContentType = "application/vnd.3gpp.ussd+xml";
    private static final String OUTBOUND_API = "outbound-api";
    private static final String OUTBOUND_DIAL = "outbound-dial";
    private UssdCallType ussdCallType;

    private final FiniteStateMachine fsm;
    // States for the FSM.
    private final State uninitialized;
    private final State ringing;
    private final State inProgress;
    private final State ready;
    private final State processingUssdMessage;
    private final State completed;
    private final State queued;
    private final State dialing;
    private final State disconnecting;
    private final State cancelling;

    // SIP runtime stuff.
    private final SipFactory factory;
    private String apiVersion;
    private Sid accountId;
    private String name;
    private SipURI from;
    private SipURI to;
    private String transport;
    private UssdRestcommResponse userUssdRequest;
    private String username;
    private String password;
    private CreateCallType type;
    private long timeout;
    private SipServletRequest invite;
    private SipServletRequest outgoingInvite;
    private SipServletResponse lastResponse;
    private Map<String, String> headers;
    private boolean isFromApi;

    // Runtime stuff.
    private final Sid id;
    private CallStateChanged.State external;
    private String direction;
    private DateTime created;
    private final List<ActorRef> observers;

    private CallDetailRecordsDao callDetailrecordsDao;
    private CallDetailRecord outgoingCallRecord;
    private ActorRef ussdInterpreter;

    public UssdCall(final SipFactory factory) {
        super();
        final ActorRef source = self();
        // Initialize the states for the FSM.
        uninitialized = new State("uninitialized", null, null);
        ringing = new State("ringing", new Ringing(source), null);
        inProgress = new State("in progress", new InProgress(source), null);
        ready = new State("answering", new Ready(source), null);
        processingUssdMessage = new State("processing UssdMessage", new ProcessingUssdMessage(source), null);
        completed = new State("Completed", new Completed(source), null);
        queued = new State("queued", new Queued(source), null);
        dialing = new State("dialing", new Dialing(source), null);
        disconnecting = new State("Disconnecting", new Disconnecting(source), null);
        cancelling = new State("Cancelling", new Cancelling(source), null);

        // Initialize the transitions for the FSM.
        final Set<Transition> transitions = new HashSet<Transition>();
        transitions.add(new Transition(uninitialized, ringing));
        transitions.add(new Transition(uninitialized, cancelling));
        transitions.add(new Transition(uninitialized, queued));
        transitions.add(new Transition(queued, dialing));
        transitions.add(new Transition(queued, cancelling));
        transitions.add(new Transition(dialing, processingUssdMessage));
        transitions.add(new Transition(dialing, completed));
        transitions.add(new Transition(ringing, inProgress));
        transitions.add(new Transition(ringing, cancelling));
        transitions.add(new Transition(inProgress, processingUssdMessage));
        transitions.add(new Transition(inProgress, disconnecting));
        transitions.add(new Transition(inProgress, completed));
        transitions.add(new Transition(processingUssdMessage, ready));
        transitions.add(new Transition(processingUssdMessage, inProgress));
        transitions.add(new Transition(processingUssdMessage, completed));
        transitions.add(new Transition(processingUssdMessage, processingUssdMessage));
        transitions.add(new Transition(processingUssdMessage, dialing));
        transitions.add(new Transition(processingUssdMessage, disconnecting));

        // Initialize the FSM.
        this.fsm = new FiniteStateMachine(uninitialized, transitions);
        this.fsm.addTransitionEndListener(this);
        // Initialize the SIP runtime stuff.
        this.factory = factory;
        // Initialize the runtime stuff.
        this.id = Sid.generate(Sid.Type.CALL);
        this.created = DateTime.now();
        this.observers = Collections.synchronizedList(new ArrayList<ActorRef>());
    }

    private void observe(final Object message) {
        final ActorRef self = self();
        final Observe request = (Observe) message;
        final ActorRef observer = request.observer();
        if (observer != null) {
            observers.add(observer);
            observer.tell(new Observing(self), self);
        }
    }

    private void stopObserving(final Object message) {
        final StopObserving request = (StopObserving) message;
        final ActorRef observer = request.observer();
        if (observer != null) {
            observers.remove(observer);
        }
    }

    private CallResponse<CallInfo> info() {
        if (from == null)
            from = (SipURI) invite.getFrom().getURI();
        if (to == null)
            to = (SipURI) invite.getTo().getURI();
        final String from = this.from.getUser();
        final String to = this.to.getUser();
        final CallInfo info = new CallInfo(id, accountId, external, type, direction, created, null, name, from, to, invite, lastResponse,
                false, false, isFromApi, null, null);
        return new CallResponse<CallInfo>(info);
    }

    private SipURI getInitialIpAddressPort(SipServletMessage message) throws ServletParseException, UnknownHostException {
        // Issue #268 - https://bitbucket.org/telestax/telscale-restcomm/issue/268
        // First get the Initial Remote Address (real address that the request came from)
        // Then check the following:
        //    1. If contact header address is private network address
        //    2. If there are no "Record-Route" headers (there is no proxy in the call)
        //    3. If contact header address != real ip address
        // Finally, if all of the above are true, create a SIP URI using the realIP address and the SIP port
        // and store it to the sip session to be used as request uri later
        String realIP = message.getInitialRemoteAddr();
        int realPort = message.getInitialRemotePort();
        final ListIterator<String> recordRouteHeaders = message.getHeaders("Record-Route");
        final Address contactAddr = factory.createAddress(message.getHeader("Contact"));

        InetAddress contactInetAddress = DNSUtils.getByName(((SipURI) contactAddr.getURI()).getHost());
        InetAddress inetAddress = DNSUtils.getByName(realIP);

        int remotePort = message.getRemotePort();
        int contactPort = ((SipURI)contactAddr.getURI()).getPort();
        String remoteAddress = message.getRemoteAddr();

        //Issue #332: https://telestax.atlassian.net/browse/RESTCOMM-332
        final String initialIpBeforeLB = message.getHeader("X-Sip-Balancer-InitialRemoteAddr");
        String initialPortBeforeLB = message.getHeader("X-Sip-Balancer-InitialRemotePort");

        SipURI uri = null;

        if (initialIpBeforeLB != null) {
            if(initialPortBeforeLB == null)
                initialPortBeforeLB = "5060";
            logger.info("We are behind load balancer, storing Initial Remote Address " + initialIpBeforeLB+":"+initialPortBeforeLB
                    + " to the session for later use");
            realIP = initialIpBeforeLB+":"+initialPortBeforeLB;
            uri = factory.createSipURI(null, realIP);
        } else if (contactInetAddress.isSiteLocalAddress() && !recordRouteHeaders.hasNext()
                && !contactInetAddress.toString().equalsIgnoreCase(inetAddress.toString())) {
            logger.info("Contact header address " + contactAddr.toString()
                    + " is a private network ip address, storing Initial Remote Address " + realIP+":"+realPort
                    + " to the session for later use");
            realIP = realIP + ":" + realPort;
            uri = factory.createSipURI(null, realIP);
        }
//        //Assuming that the contactPort (from the Contact header) is the port that is assigned to the sip client,
//        //If RemotePort (either from Packet or from the Via header rport) is not the same as the contactPort, then we
//        //should use the remotePort and remoteAddres for the URI to use later for client behind NAT
//        else if(remotePort != contactPort) {
//            logger.info("RemotePort: "+remotePort+" is different than the Contact Address port: "+contactPort+" so storing for later use the "
//                    + remoteAddress+":"+remotePort);
//            realIP = remoteAddress+":"+remotePort;
//            uri = factory.createSipURI(null, realIP);
//        }
        return uri;
    }

    @Override
    public void onReceive(final Object message) throws Exception {
        final UntypedActorContext context = getContext();
        final Class<?> klass = message.getClass();
        final ActorRef self = self();
        final ActorRef sender = sender();
        final State state = fsm.state();
        logger.info("UssdCall's Current State: \"" + state.toString());
        logger.info("UssdCall Processing Message: \"" + klass.getName());

        if (Observe.class.equals(klass)) {
            observe(message);
        } else if (StopObserving.class.equals(klass)) {
            stopObserving(message);
        } else if (GetCallObservers.class.equals(klass)) {
            sender.tell(new CallResponse<List<ActorRef>>(observers), self);
        } else if (GetCallInfo.class.equals(klass)) {
            sender.tell(info(), self);
        } else if (message instanceof SipServletRequest) {
            final SipServletRequest request = (SipServletRequest) message;
            final String method = request.getMethod();
            if ("INVITE".equalsIgnoreCase(method)) {
                if (uninitialized.equals(state)) {
                    fsm.transition(message, ringing);
                }
            } else if ("BYE".equalsIgnoreCase(method)) {
                fsm.transition(message, disconnecting);
            } else if("CANCEL".equalsIgnoreCase(method)) {
                if (!request.getSession().getState().equals(SipSession.State.CONFIRMED) || !request.getSession().getState().equals(SipSession.State.TERMINATED))
                    fsm.transition(message, cancelling);
            }
        } else if (message instanceof SipServletResponse) {
            final SipServletResponse response = (SipServletResponse) message;
            lastResponse = response;
            if(response.getStatus() == SipServletResponse.SC_OK && response.getRequest().getMethod().equalsIgnoreCase("INVITE")){
                response.createAck().send();
            } if(response.getStatus() == SipServletResponse.SC_OK && (response.getRequest().getMethod().equalsIgnoreCase("BYE"))) {
                fsm.transition(message, completed);
            }
        } else if (UssdRestcommResponse.class.equals(klass)) {
            //If direction is outbound, get the message and create the Invite
            if (!direction.equalsIgnoreCase("inbound") && outgoingInvite == null) {
                this.ussdInterpreter = sender;
                fsm.transition(message, dialing);
                return;
            }
            fsm.transition(message, processingUssdMessage);
        } else if (Answer.class.equals(klass)) {
            fsm.transition(message, inProgress);
        } else if (InitializeOutbound.class.equals(klass)) {
            fsm.transition(message, queued);
        }
    }

    @Override
    public void onTransitionEnd(State was, State is, Object event) {
        UssdStreamEvent.Builder builder = UssdStreamEvent.builder()
                .setSid(id)
                .setAccountSid(accountId)
                .setDirection(UssdStreamEvent.Direction.valueOf(direction))
                .setFrom(from.getUser())
                .setTo(to.getUser());

        if (userUssdRequest != null){
            builder.setStatus(UssdStreamEvent.Status.processing)
                    .setRequest(getUssdRequestSdrData());
            userUssdRequest = null;
        } else {
            UssdStreamEvent.Status.valueOf(external.toString().toUpperCase().replaceAll("-", "_"));
        }

        getContext().system()
                .eventStream()
                .publish(builder.build());
    }

    private String getUssdRequestSdrData(){
        return userUssdRequest.getMessage().trim();
    }

    private abstract class AbstractAction implements Action {
        protected final ActorRef source;

        public AbstractAction(final ActorRef source) {
            super();
            this.source = source;
        }
    }

    private final class Ringing extends AbstractAction {
        public Ringing(final ActorRef source) {
            super(source);
        }

        @Override
        public void execute(final Object message) throws Exception {
            if (message instanceof SipServletRequest) {
                invite = (SipServletRequest) message;
                from = (SipURI) invite.getFrom().getURI();
                to = (SipURI) invite.getTo().getURI();
                timeout = -1;
                direction = "inbound";
                // Send a ringing response.
                final SipServletResponse ringing = invite.createResponse(SipServletResponse.SC_RINGING);
                ringing.send();

                SipURI initialInetUri = getInitialIpAddressPort(invite);

                if(initialInetUri != null)
                    invite.getSession().setAttribute("realInetUri", initialInetUri);

            } else if (message instanceof SipServletResponse) {
                final UntypedActorContext context = getContext();
                context.setReceiveTimeout(Duration.Undefined());
            }
            // Notify the observers.
            external = CallStateChanged.State.RINGING;
            final CallStateChanged event = new CallStateChanged(external);
            for (final ActorRef observer : observers) {
                logger.info("Telling observers that state changed to RINGING");
                observer.tell(event, source);
            }

            if (outgoingCallRecord != null && direction.contains("outbound")) {
                outgoingCallRecord = outgoingCallRecord.setStatus(external.name());
                callDetailrecordsDao.updateCallDetailRecord(outgoingCallRecord);
            }
        }
    }

    private final class InProgress extends AbstractAction {
        public InProgress(final ActorRef source) {
            super(source);
        }

        @Override
        public void execute(final Object message) throws Exception {
            final State state = fsm.state();
            final SipServletResponse okay = invite.createResponse(SipServletResponse.SC_OK);
            okay.send();

            invite.getApplicationSession().setExpires(0);

            // Notify the observers.
            external = CallStateChanged.State.IN_PROGRESS;
            final CallStateChanged event = new CallStateChanged(external);
            for (final ActorRef observer : observers) {
                observer.tell(event, source);
            }

            if (outgoingCallRecord != null && direction.contains("outbound")
                    && !outgoingCallRecord.getStatus().equalsIgnoreCase("in_progress")) {
                outgoingCallRecord = outgoingCallRecord.setStatus(external.name());
                outgoingCallRecord = outgoingCallRecord.setAnsweredBy(to.getUser());
                callDetailrecordsDao.updateCallDetailRecord(outgoingCallRecord);
            }
        }
    }

    private final class Ready extends AbstractAction {
        public Ready(final ActorRef source) {
            super(source);
        }

        @Override
        public void execute(final Object message) throws Exception {

        }
    }

    private final class ProcessingUssdMessage extends AbstractAction {
        public ProcessingUssdMessage(final ActorRef source) {
            super(source);
        }

        @Override
        public void execute(final Object message) throws Exception {
            UssdRestcommResponse ussdRequest = (UssdRestcommResponse) message;
            SipSession session = null;
            if (direction.equalsIgnoreCase("inbound")) {
                session = invite.getSession();
            } else {
                session = outgoingInvite.getSession();
            }
            SipServletRequest request = null;

            if(ussdRequest.getIsFinalMessage()) {
             request = session.createRequest("BYE");
            } else {
                request = session.createRequest("INFO");
            }

            userUssdRequest = ussdRequest;
            request.setContent(ussdRequest.createUssdPayload().toString().trim(), ussdContentType);

            logger.info("Prepared request: \n"+request);

            SipURI realInetUri = (SipURI) session.getAttribute("realInetUri");
            if (realInetUri != null) {
                logger.info("Using the real ip address of the sip client " + realInetUri.toString()
                        + " as a request uri of the BYE request");
                request.setRequestURI(realInetUri);
            }
            request.send();
            if(ussdRequest.getIsFinalMessage())
                fsm.transition(request, completed);
        }
    }

    private final class Cancelling extends AbstractAction {
        public Cancelling(final ActorRef source) {
            super(source);
        }

        @Override
        public void execute(final Object message) throws Exception {
            logger.info("Cancelling the call");
            SipServletRequest cancel = (SipServletRequest)message;
            SipServletResponse requestTerminated = invite.createResponse(SipServletResponse.SC_REQUEST_TERMINATED);
//            SipServletResponse requestTerminated = cancel.createResponse(SipServletResponse.SC_REQUEST_TERMINATED);
            requestTerminated.send();

            if (invite != null) {
                invite.getSession().invalidate();
            }
            if (outgoingInvite != null) {
                outgoingInvite.getSession().invalidate();
            }
            // Notify the observers.
            external = CallStateChanged.State.CANCELED;
            final CallStateChanged event = new CallStateChanged(external);
            for (final ActorRef observer : observers) {
                observer.tell(event, source);
            }
            if (outgoingCallRecord != null && direction.contains("outbound")) {
                outgoingCallRecord = outgoingCallRecord.setStatus(CallStateChanged.State.CANCELED.name());
                final DateTime now = DateTime.now();
                outgoingCallRecord = outgoingCallRecord.setEndTime(now);
                final int seconds = 0;
                outgoingCallRecord = outgoingCallRecord.setDuration(seconds);
                callDetailrecordsDao.updateCallDetailRecord(outgoingCallRecord);
            }
            logger.info("Call Cancelled");
        }
    }

    private final class Disconnecting extends AbstractAction {
        public Disconnecting(final ActorRef source) {
            super(source);
        }

        @Override
        public void execute(final Object message) throws Exception {
            logger.info("Disconnecting the call");
            SipServletRequest bye = (SipServletRequest)message;
            SipServletResponse response = bye.createResponse(SipServletResponse.SC_OK);
            response.send();

            if (invite != null) {
                invite.getSession().invalidate();
            }
            if (outgoingInvite != null) {
                outgoingInvite.getSession().invalidate();
            }
            // Notify the observers.
            external = CallStateChanged.State.CANCELED;
            final CallStateChanged event = new CallStateChanged(external);
            for (final ActorRef observer : observers) {
                observer.tell(event, source);
            }
            if (outgoingCallRecord != null && direction.contains("outbound")) {
                outgoingCallRecord = outgoingCallRecord.setStatus(CallStateChanged.State.CANCELED.name());
                final DateTime now = DateTime.now();
                outgoingCallRecord = outgoingCallRecord.setEndTime(now);
                final int seconds = 0;
                outgoingCallRecord = outgoingCallRecord.setDuration(seconds);
                callDetailrecordsDao.updateCallDetailRecord(outgoingCallRecord);
            }
            logger.info("Call Disconnected");
        }
    }

    private final class Completed extends AbstractAction {
        public Completed(final ActorRef source) {
            super(source);
        }

        @Override
        public void execute(final Object message) throws Exception {
            logger.info("Completing the call");
            if (invite != null) {
                invite.getSession().invalidate();
            }
            if (outgoingInvite != null) {
                outgoingInvite.getSession().invalidate();
            }
            // Notify the observers.
            external = CallStateChanged.State.COMPLETED;
            final CallStateChanged event = new CallStateChanged(external);
            for (final ActorRef observer : observers) {
                observer.tell(event, source);
            }
            if (outgoingCallRecord != null && direction.contains("outbound")) {
                outgoingCallRecord = outgoingCallRecord.setStatus(CallStateChanged.State.COMPLETED.name());
                final DateTime now = DateTime.now();
                outgoingCallRecord = outgoingCallRecord.setEndTime(now);
                final int seconds = 0;
                outgoingCallRecord = outgoingCallRecord.setDuration(seconds);
                callDetailrecordsDao.updateCallDetailRecord(outgoingCallRecord);
            }
            logger.info("Call completed");
        }
    }

    private final class Queued extends AbstractAction {
        public Queued(final ActorRef source) {
            super(source);
        }

        @Override
        public void execute(Object message) throws Exception {
            final InitializeOutbound request = (InitializeOutbound) message;
            name = request.name();
            from = request.from();
            to = request.to();
            transport = (to.getTransportParam() != null) ? to.getTransportParam() : "udp";
            apiVersion = request.apiVersion();
            accountId = request.accountId();
            username = request.username();
            password = request.password();
            type = request.type();
            isFromApi = request.isFromApi();
            callDetailrecordsDao = request.getDaoManager().getCallDetailRecordsDao();
            String toHeaderString = to.toString();
            if (toHeaderString.indexOf('?') != -1) {
                // custom headers parsing for SIP Out
                // https://bitbucket.org/telestax/telscale-restcomm/issue/132/implement-twilio-sip-out
                headers = new HashMap<String, String>();
                // we keep only the to URI without the headers
                to = (SipURI) factory.createURI(toHeaderString.substring(0, toHeaderString.lastIndexOf('?')));
                String headersString = toHeaderString.substring(toHeaderString.lastIndexOf('?') + 1);
                StringTokenizer tokenizer = new StringTokenizer(headersString, "&");
                while (tokenizer.hasMoreTokens()) {
                    String headerNameValue = tokenizer.nextToken();
                    String headerName = headerNameValue.substring(0, headerNameValue.lastIndexOf('='));
                    String headerValue = headerNameValue.substring(headerNameValue.lastIndexOf('=') + 1);
                    headers.put(headerName, headerValue);
                }
            }
            timeout = request.timeout();
            if (request.isFromApi()) {
                direction = OUTBOUND_API;
            } else {
                direction = OUTBOUND_DIAL;
            }
            // Notify the observers.
            external = CallStateChanged.State.QUEUED;
            final CallStateChanged event = new CallStateChanged(external);
            for (final ActorRef observer : observers) {
                observer.tell(event, source);
            }

            if (callDetailrecordsDao != null) {
                final CallDetailRecord.Builder builder = CallDetailRecord.builder();
                builder.setSid(id);
                builder.setInstanceId(RestcommConfiguration.getInstance().getMain().getInstanceId());
                builder.setDateCreated(created);
                builder.setAccountSid(accountId);
                builder.setTo(to.getUser());
                builder.setCallerName(name);
                String fromString = from.getUser() != null ? from.getUser() : "USSD REST API";
                builder.setFrom(fromString);
                // builder.setForwardedFrom(callInfo.forwardedFrom());
                // builder.setPhoneNumberSid(phoneId);
                builder.setStatus(external.name());
                builder.setDirection("outbound-api");
                builder.setApiVersion(apiVersion);
                builder.setPrice(new BigDecimal("0.00"));
                // TODO implement currency property to be read from Configuration
                builder.setPriceUnit(Currency.getInstance("USD"));
                final StringBuilder buffer = new StringBuilder();
                buffer.append("/").append(apiVersion).append("/Accounts/");
                buffer.append(accountId.toString()).append("/Calls/");
                buffer.append(id.toString());
                final URI uri = URI.create(buffer.toString());
                builder.setUri(uri);
                outgoingCallRecord = builder.build();
                callDetailrecordsDao.addCallDetailRecord(outgoingCallRecord);
            }
        }
    }

    private final class Dialing extends AbstractAction {
        public Dialing(final ActorRef source) {
            super(source);
        }

        @Override
        public void execute(Object message) throws Exception {
            UssdRestcommResponse ussdRequest = (UssdRestcommResponse) message;
            final ActorRef self = self();
            // Create a SIP invite to initiate a new session.
            final StringBuilder buffer = new StringBuilder();
            buffer.append(to.getHost());
            if (to.getPort() > -1) {
                buffer.append(":").append(to.getPort());
            }
            if (!transport.equalsIgnoreCase("udp")) {
                buffer.append(";transport=").append(transport);
            }
            final SipURI uri = factory.createSipURI(null, buffer.toString());
            final SipApplicationSession application = factory.createApplicationSession();
            application.setAttribute("UssdCall","true");
            application.setAttribute(UssdCall.class.getName(), self);
            if(ussdInterpreter != null)
                application.setAttribute(UssdInterpreter.class.getName(), ussdInterpreter);
            outgoingInvite = factory.createRequest(application, "INVITE", from, to);
            if (!transport.equalsIgnoreCase("udp")) {
                ((SipURI)outgoingInvite.getRequestURI()).setTransportParam(transport);
                ((SipURI)outgoingInvite.getFrom().getURI()).setTransportParam(transport);
                ((SipURI)outgoingInvite.getTo().getURI()).setTransportParam(transport);
            }
            outgoingInvite.pushRoute(uri);

            if (headers != null) {
                // adding custom headers for SIP Out
                // https://bitbucket.org/telestax/telscale-restcomm/issue/132/implement-twilio-sip-out
                Set<Map.Entry<String, String>> entrySet = headers.entrySet();
                for (Map.Entry<String, String> entry : entrySet) {
                    outgoingInvite.addHeader("X-" + entry.getKey(), entry.getValue());
                }
            }
            outgoingInvite.addHeader("X-RestComm-ApiVersion", apiVersion);
            outgoingInvite.addHeader("X-RestComm-AccountSid", accountId.toString());
            outgoingInvite.addHeader("X-RestComm-CallSid", id.toString());
            final SipSession session = outgoingInvite.getSession();
            session.setHandler("CallManager");

            outgoingInvite.setContent(ussdRequest.createUssdPayload().toString(), ussdContentType);

            // Send the invite.
            outgoingInvite.send();
            // Set the timeout period.
            final UntypedActorContext context = getContext();
            context.setReceiveTimeout(Duration.create(timeout, TimeUnit.SECONDS));
        }
    }

    /* (non-Javadoc)
     * @see akka.actor.UntypedActor#postStop()
     */
    @Override
    public void postStop() {
        super.postStop();
    }
}
