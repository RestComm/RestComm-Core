/*
 * TeleStax, Open Source Cloud Communications
 * Copyright 2011-2013, Telestax Inc and individual contributors
 * by the @authors tag.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.restcomm.connect.mscontrol.jsr309;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.media.mscontrol.EventType;
import javax.media.mscontrol.MediaEvent;
import javax.media.mscontrol.MediaEventListener;
import javax.media.mscontrol.MediaSession;
import javax.media.mscontrol.MsControlException;
import javax.media.mscontrol.MsControlFactory;
import javax.media.mscontrol.Parameters;
import javax.media.mscontrol.join.Joinable.Direction;
import javax.media.mscontrol.mediagroup.MediaGroup;
import javax.media.mscontrol.mediagroup.Recorder;
import javax.media.mscontrol.mediagroup.RecorderEvent;
import javax.media.mscontrol.mediagroup.SpeechDetectorConstants;
import javax.media.mscontrol.mediagroup.signals.SignalDetector;
import javax.media.mscontrol.mixer.MediaMixer;
import javax.media.mscontrol.resource.AllocationEvent;
import javax.media.mscontrol.resource.AllocationEventListener;
import javax.media.mscontrol.resource.RTC;
import javax.sound.sampled.UnsupportedAudioFileException;

import org.apache.commons.configuration.Configuration;
import org.restcomm.connect.dao.DaoManager;
import org.restcomm.connect.dao.RecordingsDao;
import org.restcomm.connect.dao.entities.MediaAttributes;
import org.restcomm.connect.dao.entities.Recording;
import org.restcomm.connect.commons.dao.Sid;
import org.restcomm.connect.commons.fsm.FiniteStateMachine;
import org.restcomm.connect.commons.fsm.State;
import org.restcomm.connect.commons.fsm.Transition;
import org.restcomm.connect.commons.fsm.TransitionFailedException;
import org.restcomm.connect.commons.fsm.TransitionNotFoundException;
import org.restcomm.connect.commons.fsm.TransitionRollbackException;
import org.restcomm.connect.mscontrol.api.MediaServerController;
import org.restcomm.connect.mscontrol.api.MediaServerInfo;
import org.restcomm.connect.mscontrol.api.exceptions.MediaServerControllerException;
import org.restcomm.connect.mscontrol.api.messages.CreateMediaSession;
import org.restcomm.connect.mscontrol.api.messages.JoinBridge;
import org.restcomm.connect.mscontrol.api.messages.JoinCall;
import org.restcomm.connect.mscontrol.api.messages.MediaGroupResponse;
import org.restcomm.connect.mscontrol.api.messages.MediaServerControllerError;
import org.restcomm.connect.mscontrol.api.messages.MediaServerControllerStateChanged;
import org.restcomm.connect.mscontrol.api.messages.MediaServerControllerStateChanged.MediaServerControllerState;
import org.restcomm.connect.mscontrol.api.messages.StartRecording;
import org.restcomm.connect.mscontrol.api.messages.Stop;
import org.restcomm.connect.commons.patterns.Observe;
import org.restcomm.connect.commons.patterns.Observing;
import org.restcomm.connect.commons.patterns.StopObserving;
import org.restcomm.connect.commons.util.WavUtils;

import akka.actor.ActorRef;
import akka.event.Logging;
import akka.event.LoggingAdapter;

/**
 * @author Henrique Rosa (henrique.rosa@telestax.com)
 *
 */
public class Jsr309BridgeController extends MediaServerController {

    // Logging
    private final LoggingAdapter logger = Logging.getLogger(getContext().system(), this);

    // Finite State Machine
    private final FiniteStateMachine fsm;
    private final State uninitialized;
    private final State initializing;
    private final State active;
    private final State inactive;
    private final State failed;

    // Telephony actors
    private ActorRef bridge;

    // JSR-309 components
    private final MsControlFactory msControlFactory;
    private final MediaServerInfo mediaServerInfo;
    private MediaSession mediaSession;
    private MediaGroup mediaGroup;
    private MediaMixer mediaMixer;

    private final RecorderListener recorderListener;
    private final MixerAllocationListener mixerAllocationListener;

    // Media Operations
    private Boolean recording;
    private StartRecording recordingRequest;

    // Observers
    private final List<ActorRef> observers;

    public Jsr309BridgeController(MsControlFactory msControlFactory, MediaServerInfo mediaServerInfo) {
        super();
        final ActorRef source = self();

        // JSR-309 resources
        this.msControlFactory = msControlFactory;
        this.mediaServerInfo = mediaServerInfo;
        this.recorderListener = new RecorderListener();
        this.mixerAllocationListener = new MixerAllocationListener();

        // Media Operations
        this.recording = Boolean.FALSE;

        // States for the FSM
        this.uninitialized = new State("uninitialized", null);
        this.initializing = new State("initializing", new Initializing(source));
        this.active = new State("active", new Active(source));
        this.inactive = new State("inactive", new Inactive(source));
        this.failed = new State("failed", new Failed(source));

        // Finite state machine
        final Set<Transition> transitions = new HashSet<Transition>();
        transitions.add(new Transition(uninitialized, initializing));
        transitions.add(new Transition(initializing, failed));
        transitions.add(new Transition(initializing, active));
        transitions.add(new Transition(initializing, inactive));
        transitions.add(new Transition(active, inactive));
        this.fsm = new FiniteStateMachine(this.uninitialized, transitions);

        // Observers
        this.observers = new ArrayList<ActorRef>(1);
    }

    private boolean is(State state) {
        return this.fsm.state().equals(state);
    }

    private void broadcast(Object message) {
        if (!this.observers.isEmpty()) {
            final ActorRef self = self();
            synchronized (this.observers) {
                for (ActorRef observer : observers) {
                    observer.tell(message, self);
                }
            }
        }
    }

    private void stopMediaOperations() throws MsControlException {
        // Stop ongoing recording
        if (recording) {
            mediaGroup.getRecorder().stop();
        }
    }

    private void cleanMediaResources() {
        // Release media resources
        mediaSession.release();
        mediaSession = null;
        mediaGroup = null;
        mediaMixer = null;
    }

    /*
     * JSR-309 - EVENT LISTENERS
     */
    private abstract class MediaListener<T extends MediaEvent<?>> implements MediaEventListener<T>, Serializable {

        private static final long serialVersionUID = 4712964810787577487L;

        protected ActorRef remote;

        public void setRemote(ActorRef sender) {
            this.remote = sender;
        }

    }

    private final class RecorderListener extends MediaListener<RecorderEvent> {

        private static final long serialVersionUID = 2145317407008648018L;

        private String endOnKey = "";

        public void setEndOnKey(String endOnKey) {
            this.endOnKey = endOnKey;
        }

        @Override
        public void onEvent(RecorderEvent event) {
            EventType eventType = event.getEventType();
            if(logger.isInfoEnabled()) {
                logger.info("********** Bridge Controller Current State: \"" + fsm.state().toString() + "\"");
                logger.info("********** Bridge Controller Processing Event: \"RecorderEvent\" (type = " + eventType + ")");
            }

            if (RecorderEvent.RECORD_COMPLETED.equals(eventType)) {
                MediaGroupResponse<String> response = null;
                if (event.isSuccessful()) {
                    String digits = "";
                    if (RecorderEvent.STOPPED.equals(event.getQualifier())) {
                        digits = endOnKey;
                    }
                    saveRecording();
                    response = new MediaGroupResponse<String>(digits);
                } else {
                    String reason = event.getErrorText();
                    MediaServerControllerException error = new MediaServerControllerException(reason);
                    logger.error("Recording event failed: " + reason);
                    response = new MediaGroupResponse<String>(error, reason);
                }

                recording = Boolean.FALSE;
                recordingRequest = null;

                super.remote.tell(response, self());
            }
        }

        private void saveRecording() {
            final Sid accountId = recordingRequest.getAccountId();
            final Sid callId = recordingRequest.getCallId();
            final DaoManager daoManager = recordingRequest.getDaoManager();
            final Sid recordingSid = recordingRequest.getRecordingSid();
            final URI recordingUri = recordingRequest.getRecordingUri();
            final Configuration runtimeSettings = recordingRequest.getRuntimeSetting();
            Double duration;

            try {
                duration = WavUtils.getAudioDuration(recordingUri);
            } catch (UnsupportedAudioFileException | IOException e) {
                logger.error("Could not measure recording duration: " + e.getMessage(), e);
                duration = 0.0;
            }

            if (!duration.equals(0.0)) {
                if(logger.isInfoEnabled()) {
                    logger.info("Call wraping up recording. File already exists, length: " + (new File(recordingUri).length()));
                }

                final Recording.Builder builder = Recording.builder();
                builder.setSid(recordingSid);
                builder.setAccountSid(accountId);
                builder.setCallSid(callId);
                builder.setDuration(duration);
                builder.setApiVersion(runtimeSettings.getString("api-version"));
                StringBuilder buffer = new StringBuilder();
                buffer.append("/").append(runtimeSettings.getString("api-version")).append("/Accounts/")
                        .append(accountId.toString());
                buffer.append("/Recordings/").append(recordingSid.toString());
                builder.setUri(URI.create(buffer.toString()));
                final Recording recording = builder.build();
                RecordingsDao recordsDao = daoManager.getRecordingsDao();
                recordsDao.addRecording(recording, MediaAttributes.MediaType.AUDIO_ONLY);
                getContext().system().eventStream().publish(recording);
            } else {
                if(logger.isInfoEnabled()) {
                    logger.info("Call wraping up recording. File doesn't exist since duration is 0");
                }
            }
        }

    }

    private class MixerAllocationListener implements AllocationEventListener, Serializable {

        private static final long serialVersionUID = -8450656267936666492L;

        @Override
        public void onEvent(AllocationEvent event) {
            EventType eventType = event.getEventType();
            if(logger.isInfoEnabled()) {
                logger.info("********** Bridge Controller Current State: \"" + fsm.state().toString() + "\"");
                logger.info("********** Bridge Controller Processing Event: \"AllocationEventListener - Mixer\" (type = "
                    + eventType + ")");
            }

            try {
                if (AllocationEvent.ALLOCATION_CONFIRMED.equals(eventType)) {
                    // No need to be notified anymore
                    mediaMixer.removeListener(this);

                    // join the media group to the mixer
                    try {
                        mediaGroup.join(Direction.DUPLEX, mediaMixer);
                    } catch (MsControlException e) {
                        fsm.transition(e, failed);
                    }

                    // Conference room has been properly activated and is now ready to receive connections
                    fsm.transition(event, active);
                } else if (AllocationEvent.IRRECOVERABLE_FAILURE.equals(eventType)) {
                    // Failed to allocate media mixer
                    fsm.transition(event, failed);
                }
            } catch (TransitionFailedException | TransitionNotFoundException | TransitionRollbackException e) {
                logger.error(e.getMessage(), e);
            }
        }

    }

    /*
     * Events
     */
    @Override
    public void onReceive(Object message) throws Exception {
        final Class<?> klass = message.getClass();
        final ActorRef self = self();
        final ActorRef sender = sender();
        final State state = fsm.state();

        if(logger.isInfoEnabled()) {
            logger.info("********** Bridge Controller " + self().path() + " State: \"" + state.toString());
            logger.info("********** Bridge Controller " + self().path() + " Processing: \"" + klass.getName() + " Sender: "
                + sender.getClass());
        }

        if (Observe.class.equals(klass)) {
            onObserve((Observe) message, self, sender);
        } else if (StopObserving.class.equals(klass)) {
            onStopObserving((StopObserving) message, self, sender);
        } else if (CreateMediaSession.class.equals(klass)) {
            onCreateMediaSession((CreateMediaSession) message, self, sender);
        } else if (JoinCall.class.equals(klass)) {
            onJoinCall((JoinCall) message, self, sender);
        } else if (Stop.class.equals(klass)) {
            onStop((Stop) message, self, sender);
        } else if (StartRecording.class.equals(klass)) {
            onStartRecording((StartRecording) message, self, sender);
        }
    }

    private void onObserve(Observe message, ActorRef self, ActorRef sender) {
        final ActorRef observer = message.observer();
        if (observer != null) {
            synchronized (this.observers) {
                this.observers.add(observer);
                observer.tell(new Observing(self), self);
            }
        }
    }

    private void onStopObserving(StopObserving message, ActorRef self, ActorRef sender) {
        final ActorRef observer = message.observer();
        if (observer != null) {
            this.observers.remove(observer);
        }
    }

    private void onCreateMediaSession(CreateMediaSession message, ActorRef self, ActorRef sender) throws Exception {
        if (is(uninitialized)) {
            this.bridge = sender;
            this.fsm.transition(message, initializing);
        }
    }

    private void onJoinCall(JoinCall message, ActorRef self, ActorRef sender) {
        // Tell call to join bridge by passing reference to the media mixer
        final JoinBridge join = new JoinBridge(this.mediaMixer, message.getConnectionMode());
        message.getCall().tell(join, sender);
    }

    private void onStop(Stop message, ActorRef self, ActorRef sender) throws Exception {
        if (is(initializing) || is(active)) {
            this.fsm.transition(message, inactive);
        }
    }

    private void onStartRecording(StartRecording message, ActorRef self, ActorRef sender) throws Exception {
        if (is(active)) {
            try {
                if(logger.isInfoEnabled()) {
                    logger.info("Start recording bridged call");
                }

                Parameters params = this.mediaGroup.createParameters();

                // Finish on key
                String endOnKey = "1234567890*#";
                RTC[] rtcs;
                params.put(SignalDetector.PATTERN[0], endOnKey);
                params.put(SignalDetector.INTER_SIG_TIMEOUT, new Integer(10000));
                rtcs = new RTC[] { MediaGroup.SIGDET_STOPPLAY };

                // Recording length
                params.put(Recorder.MAX_DURATION, 3600 * 1000);

                // Recording timeout
                int timeout = 5;
                params.put(SpeechDetectorConstants.INITIAL_TIMEOUT, timeout);
                params.put(SpeechDetectorConstants.FINAL_TIMEOUT, timeout);

                // Other parameters
                params.put(Recorder.APPEND, Boolean.FALSE);
                // TODO set as definitive media group parameter - handled by RestComm
                params.put(Recorder.START_BEEP, Boolean.FALSE);

                this.recorderListener.setEndOnKey(endOnKey);
                this.recorderListener.setRemote(sender);
                this.mediaGroup.getRecorder().record(message.getRecordingUri(), rtcs, params);

                this.recording = Boolean.TRUE;
                this.recordingRequest = message;
            } catch (MsControlException e) {
                logger.error("Recording failed: " + e.getMessage());
                final MediaServerControllerError error = new MediaServerControllerError(e);
                bridge.tell(error, self);
            }
        }
    }

    /*
     * Actions
     */
    private final class Initializing extends AbstractAction {

        public Initializing(ActorRef source) {
            super(source);
        }

        @Override
        public void execute(Object message) throws Exception {
            try {
                CreateMediaSession msg = (CreateMediaSession) message;
                MediaAttributes mediaAttributes = msg.mediaAttributes();

                // Create media session
                mediaSession = msControlFactory.createMediaSession();

                // Create the media group with recording capabilities
                mediaGroup = mediaSession.createMediaGroup(MediaGroup.PLAYER_RECORDER_SIGNALDETECTOR);
                mediaGroup.getRecorder().addListener(recorderListener);

                if (!MediaAttributes.MediaType.AUDIO_ONLY.equals(mediaAttributes.getMediaType())) {
                    // video only or audio and video (video only is controlled by codec policy)
                    configureVideoMediaSession(mediaAttributes);
                    Parameters mixerParams = createMixerParams();
                    mediaMixer = mediaSession.createMediaMixer(MediaMixer.AUDIO_VIDEO, mixerParams);
                } else {
                    // audio only
                    Parameters mixerParams = createMixerParams();
                    mediaMixer = mediaSession.createMediaMixer(MediaMixer.AUDIO, mixerParams);
                }

                mediaMixer.addListener(mixerAllocationListener);
                mediaMixer.confirm();
                // Wait for event confirmation before sending response to the conference
            } catch (MsControlException e) {
                // Move to a failed state, cleaning all resources and closing media session
                fsm.transition(e, failed);
            }
        }

        private void configureVideoMediaSession(final MediaAttributes mediaAttributes) {
            // resolution configuration
            mediaSession.setAttribute("NC_VIDEO_SIZE", mediaAttributes.getVideoResolution().toString());
        }

        private Parameters createMixerParams() {
            // Allow only two participants and one media group
            Parameters mixerParams = mediaSession.createParameters();
            mixerParams.put(MediaMixer.MAX_PORTS, 3);
            return mixerParams;
        }

    }

    private final class Active extends AbstractAction {

        public Active(final ActorRef source) {
            super(source);
        }

        @Override
        public void execute(final Object message) throws Exception {
            broadcast(new MediaServerControllerStateChanged(MediaServerControllerState.ACTIVE));
        }
    }

    private final class Inactive extends AbstractAction {

        public Inactive(final ActorRef source) {
            super(source);
        }

        @Override
        public void execute(final Object message) throws Exception {
            try {
                stopMediaOperations();
            } catch (MsControlException e) {
                logger.error(e, "Could not stop ongoing media operations in an elegant manner");
            }
            cleanMediaResources();

            // Broadcast state changed
            broadcast(new MediaServerControllerStateChanged(MediaServerControllerState.INACTIVE));

            // Clear observers
            observers.clear();

            // Terminate actor
            getContext().stop(super.source);
        }
    }

    private final class Failed extends AbstractAction {

        public Failed(ActorRef source) {
            super(source);
        }

        @Override
        public void execute(Object message) throws Exception {
            // Clean resources
            cleanMediaResources();

            // Broadcast state changed
            broadcast(new MediaServerControllerStateChanged(MediaServerControllerState.FAILED));

            // Clear observers
            observers.clear();

            // Terminate actor
            getContext().stop(super.source);
        }

    }

}
