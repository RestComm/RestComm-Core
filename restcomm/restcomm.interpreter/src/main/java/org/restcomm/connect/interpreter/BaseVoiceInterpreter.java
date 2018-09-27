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
package org.restcomm.connect.interpreter;

import akka.actor.Actor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.actor.UntypedActorContext;
import akka.actor.UntypedActorFactory;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.util.Timeout;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.lang.StringUtils;
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.joda.time.DateTime;
import org.restcomm.connect.asr.AsrInfo;
import org.restcomm.connect.asr.AsrRequest;
import org.restcomm.connect.asr.AsrResponse;
import org.restcomm.connect.asr.GetAsrInfo;
import org.restcomm.connect.asr.ISpeechAsr;
import org.restcomm.connect.commons.cache.DiskCacheFactory;
import org.restcomm.connect.commons.cache.DiskCacheRequest;
import org.restcomm.connect.commons.cache.DiskCacheResponse;
import org.restcomm.connect.commons.cache.HashGenerator;
import org.restcomm.connect.commons.configuration.RestcommConfiguration;
import org.restcomm.connect.commons.dao.Sid;
import org.restcomm.connect.commons.faulttolerance.RestcommUntypedActor;
import org.restcomm.connect.commons.fsm.Action;
import org.restcomm.connect.commons.fsm.FiniteStateMachine;
import org.restcomm.connect.commons.fsm.State;
import org.restcomm.connect.commons.fsm.Transition;
import org.restcomm.connect.commons.patterns.Observe;
import org.restcomm.connect.commons.util.WavUtils;
import org.restcomm.connect.core.service.RestcommConnectServiceProvider;
import org.restcomm.connect.core.service.util.UriUtils;
import org.restcomm.connect.dao.CallDetailRecordsDao;
import org.restcomm.connect.dao.DaoManager;
import org.restcomm.connect.dao.NotificationsDao;
import org.restcomm.connect.dao.RecordingsDao;
import org.restcomm.connect.dao.SmsMessagesDao;
import org.restcomm.connect.dao.TranscriptionsDao;
import org.restcomm.connect.dao.entities.CallDetailRecord;
import org.restcomm.connect.dao.entities.MediaAttributes;
import org.restcomm.connect.dao.entities.Notification;
import org.restcomm.connect.dao.entities.Recording;
import org.restcomm.connect.dao.entities.SmsMessage;
import org.restcomm.connect.dao.entities.SmsMessage.Direction;
import org.restcomm.connect.dao.entities.SmsMessage.Status;
import org.restcomm.connect.dao.entities.Transcription;
import org.restcomm.connect.email.EmailService;
import org.restcomm.connect.email.api.EmailRequest;
import org.restcomm.connect.email.api.EmailResponse;
import org.restcomm.connect.email.api.Mail;
import org.restcomm.connect.extension.api.ExtensionResponse;
import org.restcomm.connect.extension.api.ExtensionType;
import org.restcomm.connect.extension.api.IExtensionFeatureAccessRequest;
import org.restcomm.connect.extension.api.RestcommExtensionGeneric;
import org.restcomm.connect.extension.controller.ExtensionController;
import org.restcomm.connect.fax.FaxRequest;
import org.restcomm.connect.fax.InterfaxService;
import org.restcomm.connect.http.asyncclient.HttpAsycClientHelper;
import org.restcomm.connect.http.client.Downloader;
import org.restcomm.connect.http.client.DownloaderResponse;
import org.restcomm.connect.http.client.HttpRequestDescriptor;
import org.restcomm.connect.http.client.HttpResponseDescriptor;
import org.restcomm.connect.interpreter.rcml.Attribute;
import org.restcomm.connect.interpreter.rcml.GetNextVerb;
import org.restcomm.connect.interpreter.rcml.Parser;
import org.restcomm.connect.interpreter.rcml.ParserFailed;
import org.restcomm.connect.interpreter.rcml.SmsVerb;
import org.restcomm.connect.interpreter.rcml.Tag;
import org.restcomm.connect.interpreter.rcml.Verbs;
import org.restcomm.connect.interpreter.rcml.domain.GatherAttributes;
import org.restcomm.connect.mscontrol.api.messages.Collect;
import org.restcomm.connect.mscontrol.api.messages.CollectedResult;
import org.restcomm.connect.mscontrol.api.messages.MediaGroupResponse;
import org.restcomm.connect.mscontrol.api.messages.Play;
import org.restcomm.connect.mscontrol.api.messages.Record;
import org.restcomm.connect.sms.api.CreateSmsSession;
import org.restcomm.connect.sms.api.DestroySmsSession;
import org.restcomm.connect.sms.api.SmsServiceResponse;
import org.restcomm.connect.sms.api.SmsSessionAttribute;
import org.restcomm.connect.sms.api.SmsSessionInfo;
import org.restcomm.connect.sms.api.SmsSessionRequest;
import org.restcomm.connect.sms.api.SmsSessionResponse;
import org.restcomm.connect.telephony.api.CallInfo;
import org.restcomm.connect.telephony.api.CallManagerResponse;
import org.restcomm.connect.telephony.api.CallStateChanged;
import org.restcomm.connect.telephony.api.FeatureAccessRequest;
import org.restcomm.connect.telephony.api.GetCallInfo;
import org.restcomm.connect.telephony.api.Hangup;
import org.restcomm.connect.telephony.api.Reject;
import org.restcomm.connect.tts.api.GetSpeechSynthesizerInfo;
import org.restcomm.connect.tts.api.SpeechSynthesizerInfo;
import org.restcomm.connect.tts.api.SpeechSynthesizerRequest;
import org.restcomm.connect.tts.api.SpeechSynthesizerResponse;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;

import javax.servlet.sip.SipServletResponse;
import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static akka.pattern.Patterns.ask;

/**
 * @author thomas.quintana@telestax.com (Thomas Quintana)
 * @author jean.deruelle@telestax.com
 * @author gvagenas@telestax.com
 * @author pavel.slegr@telestax.com
 */
public abstract class BaseVoiceInterpreter extends RestcommUntypedActor {
    // Logger.
    private final LoggingAdapter logger = Logging.getLogger(getContext().system(), this);

    static final int ERROR_NOTIFICATION = 0;
    static final int WARNING_NOTIFICATION = 1;
    static final Pattern PATTERN = Pattern.compile("[\\*#0-9]{1,12}");
    static String EMAIL_SENDER = "restcomm@restcomm.org";

    // States for the FSM.
    // ==========================
    final State uninitialized;
    final State acquiringAsrInfo;
    final State acquiringSynthesizerInfo;
    final State acquiringCallInfo;
    final State playingRejectionPrompt;
    final State pausing;
    final State caching;
    final State checkingCache;
    final State playing;
    final State synthesizing;
    final State redirecting;
    final State faxing;
    final State processingGatherChildren;
    final State gathering;
    final State finishGathering;
    final State creatingRecording;
    final State finishRecording;
    final State creatingSmsSession;
    final State sendingSms;
    final State hangingUp;
    final State sendingEmail;
    // final State finished;

    // FSM.
    FiniteStateMachine fsm = null;
    // The user specific configuration.
    Configuration configuration = null;
    // The block storage cache.
    private ActorRef cache;
    String cachePath = null;
    // The downloader will fetch resources for us using HTTP.
    ActorRef downloader = null;
    // The mail man that will deliver e-mail.
    ActorRef mailerNotify = null;
    ActorRef mailerService = null;
    // The call manager.
    ActorRef callManager = null;
    // The conference manager.
    ActorRef conferenceManager = null;
    // The automatic speech recognition service.
    private ActorRef asrService;
    int outstandingAsrRequests;
    // The fax service.
    private ActorRef faxService;
    // The SMS service = null.
    ActorRef smsService = null;
    Map<Sid, ActorRef> smsSessions = null;
    // The storage engine.
    DaoManager storage = null;
    // The text to speech synthesizer service.
    private ActorRef synthesizer;
    // The languages supported by the automatic speech recognition service.
    AsrInfo asrInfo = null;
    // The languages supported by the text to speech synthesizer service.
    SpeechSynthesizerInfo synthesizerInfo = null;
    // The call being handled by this interpreter.
    ActorRef call = null;
    // The information for this call.
    CallInfo callInfo = null;
    // The call state.
    CallStateChanged.State callState = null;
    // The last outbound call response.
    Integer outboundCallResponse = null;
    // A call detail record.
    CallDetailRecord callRecord = null;

    // State for outbound calls.
    ActorRef outboundCall = null;
    CallInfo outboundCallInfo = null;

    // State for the gather verb.
    List<Tag> gatherChildren = null;
    List<URI> gatherPrompts = null;
    // The call recording stuff.
    Sid recordingSid = null;
    URI recordingUri = null;
    URI publicRecordingUri = null;
    MediaAttributes.MediaType recordingMediaType = null;
    // Information to reach the application that will be executed
    // by this interpreter.
    Sid accountId;
    Sid phoneId;
    String version;
    URI url;
    String method;
    URI fallbackUrl;
    String fallbackMethod;
    URI referUrl;
    String referMethod;
    String referTarget;
    String transferor;
    String transferee;
    URI viStatusCallback;
    String viStatusCallbackMethod;
    String emailAddress;
    // application data.
    HttpRequestDescriptor request;
    HttpRequestDescriptor requestCallback;
    HttpResponseDescriptor response;
    // The RCML parser.
    ActorRef parser;
    Tag verb;
    Tag gatherVerb;
    Boolean processingGather = false;
    Boolean dtmfReceived = false;
    String finishOnKey;
    String startInputKey;
    int numberOfDigits = Short.MAX_VALUE;
    StringBuffer collectedDigits;
    String speechResult;
    //Monitoring service
    ActorRef monitoring;

    final Set<Transition> transitions = new HashSet<Transition>();
    int recordingDuration = -1;

    protected RestcommConfiguration restcommConfiguration;

    //List of extensions for VoiceInterpreter
    List<RestcommExtensionGeneric> extensions;

    private UriUtils uriUtils;

    public BaseVoiceInterpreter() {
        super();
        restcommConfiguration = RestcommConfiguration.getInstance();
        final ActorRef source = self();
        // 20 States in common
        uninitialized = new State("uninitialized", null, null);
        acquiringAsrInfo = new State("acquiring asr info", new AcquiringAsrInfo(source), null);
        acquiringSynthesizerInfo = new State("acquiring tts info", new AcquiringSpeechSynthesizerInfo(source), null);
        acquiringCallInfo = new State("acquiring call info", new AcquiringCallInfo(source), null);
        playingRejectionPrompt = new State("playing rejection prompt", new PlayingRejectionPrompt(source), null);
        pausing = new State("pausing", new Pausing(source), null);
        caching = new State("caching", new Caching(source), null);
        checkingCache = new State("checkingCache", new CheckCache(source), null);
        playing = new State("playing", new Playing(source), null);
        synthesizing = new State("synthesizing", new Synthesizing(source), null);
        redirecting = new State("redirecting", new Redirecting(source), null);
        faxing = new State("faxing", new Faxing(source), null);
        gathering = new State("gathering", new Gathering(source), null);
        processingGatherChildren = new State("processing gather children", new ProcessingGatherChildren(source), null);
        finishGathering = new State("finish gathering", new FinishGathering(source), null);
        creatingRecording = new State("creating recording", new CreatingRecording(source), null);
        finishRecording = new State("finish recording", new FinishRecording(source), null);
        creatingSmsSession = new State("creating sms session", new CreatingSmsSession(source), null);
        sendingSms = new State("sending sms", new SendingSms(source), null);
        hangingUp = new State("hanging up", new HangingUp(source), null);
        sendingEmail = new State("sending Email", new SendingEmail(source), null);
        // Initialize the transitions for the FSM.
        transitions.add(new Transition(uninitialized, acquiringAsrInfo));
        transitions.add(new Transition(acquiringAsrInfo, acquiringSynthesizerInfo));
        transitions.add(new Transition(acquiringSynthesizerInfo, acquiringCallInfo));
        transitions.add(new Transition(pausing, hangingUp));
        transitions.add(new Transition(playingRejectionPrompt, hangingUp));
        transitions.add(new Transition(faxing, faxing));
        transitions.add(new Transition(faxing, caching));
        transitions.add(new Transition(faxing, pausing));
        transitions.add(new Transition(faxing, redirecting));
        transitions.add(new Transition(faxing, synthesizing));
        transitions.add(new Transition(faxing, processingGatherChildren));
        transitions.add(new Transition(faxing, creatingRecording));
        transitions.add(new Transition(faxing, creatingSmsSession));
        transitions.add(new Transition(faxing, hangingUp));
        transitions.add(new Transition(sendingEmail, sendingEmail));
        transitions.add(new Transition(sendingEmail, caching));
        transitions.add(new Transition(sendingEmail, pausing));
        transitions.add(new Transition(sendingEmail, redirecting));
        transitions.add(new Transition(sendingEmail, synthesizing));
        transitions.add(new Transition(sendingEmail, processingGatherChildren));
        transitions.add(new Transition(sendingEmail, creatingRecording));
        transitions.add(new Transition(sendingEmail, creatingSmsSession));
        transitions.add(new Transition(sendingEmail, hangingUp));
        transitions.add(new Transition(caching, faxing));
        transitions.add(new Transition(caching, sendingEmail));
        transitions.add(new Transition(caching, playing));
        transitions.add(new Transition(caching, caching));
        transitions.add(new Transition(caching, pausing));
        transitions.add(new Transition(caching, redirecting));
        transitions.add(new Transition(caching, synthesizing));
        transitions.add(new Transition(caching, processingGatherChildren));
        transitions.add(new Transition(caching, creatingRecording));
        transitions.add(new Transition(caching, creatingSmsSession));
        transitions.add(new Transition(caching, hangingUp));
        transitions.add(new Transition(checkingCache, synthesizing));
        transitions.add(new Transition(checkingCache, playing));
        transitions.add(new Transition(checkingCache, checkingCache));
        transitions.add(new Transition(playing, hangingUp));
        transitions.add(new Transition(synthesizing, faxing));
        transitions.add(new Transition(synthesizing, sendingEmail));
        transitions.add(new Transition(synthesizing, pausing));
        transitions.add(new Transition(synthesizing, checkingCache));
        transitions.add(new Transition(synthesizing, caching));
        transitions.add(new Transition(synthesizing, redirecting));
        transitions.add(new Transition(synthesizing, processingGatherChildren));
        transitions.add(new Transition(synthesizing, creatingRecording));
        transitions.add(new Transition(synthesizing, creatingSmsSession));
        transitions.add(new Transition(synthesizing, synthesizing));
        transitions.add(new Transition(synthesizing, hangingUp));
        transitions.add(new Transition(redirecting, faxing));
        transitions.add(new Transition(redirecting, sendingEmail));
        transitions.add(new Transition(redirecting, pausing));
        transitions.add(new Transition(redirecting, checkingCache));
        transitions.add(new Transition(redirecting, caching));
        transitions.add(new Transition(redirecting, synthesizing));
        transitions.add(new Transition(redirecting, redirecting));
        transitions.add(new Transition(redirecting, processingGatherChildren));
        transitions.add(new Transition(redirecting, creatingRecording));
        transitions.add(new Transition(redirecting, creatingSmsSession));
        transitions.add(new Transition(redirecting, hangingUp));
        transitions.add(new Transition(creatingRecording, finishRecording));
        transitions.add(new Transition(creatingRecording, hangingUp));
        transitions.add(new Transition(finishRecording, faxing));
        transitions.add(new Transition(finishRecording, sendingEmail));
        transitions.add(new Transition(finishRecording, pausing));
        transitions.add(new Transition(finishRecording, checkingCache));
        transitions.add(new Transition(finishRecording, caching));
        transitions.add(new Transition(finishRecording, synthesizing));
        transitions.add(new Transition(finishRecording, redirecting));
        transitions.add(new Transition(finishRecording, processingGatherChildren));
        transitions.add(new Transition(finishRecording, creatingRecording));
        transitions.add(new Transition(finishRecording, creatingSmsSession));
        transitions.add(new Transition(finishRecording, hangingUp));
        transitions.add(new Transition(processingGatherChildren, processingGatherChildren));
        transitions.add(new Transition(processingGatherChildren, gathering));
        transitions.add(new Transition(processingGatherChildren, synthesizing));
        transitions.add(new Transition(processingGatherChildren, hangingUp));

        transitions.add(new Transition(gathering, finishGathering));
        transitions.add(new Transition(gathering, hangingUp));

        transitions.add(new Transition(finishGathering, faxing));
        transitions.add(new Transition(finishGathering, sendingEmail));
        transitions.add(new Transition(finishGathering, pausing));
        transitions.add(new Transition(finishGathering, checkingCache));
        transitions.add(new Transition(finishGathering, caching));
        transitions.add(new Transition(finishGathering, synthesizing));
        transitions.add(new Transition(finishGathering, redirecting));
        transitions.add(new Transition(finishGathering, processingGatherChildren));
        transitions.add(new Transition(finishGathering, creatingRecording));
        transitions.add(new Transition(finishGathering, creatingSmsSession));
        transitions.add(new Transition(finishGathering, hangingUp));
        transitions.add(new Transition(creatingSmsSession, sendingSms));
        transitions.add(new Transition(creatingSmsSession, hangingUp));
        transitions.add(new Transition(sendingSms, faxing));
        transitions.add(new Transition(sendingSms, sendingEmail));
        transitions.add(new Transition(sendingSms, pausing));
        transitions.add(new Transition(sendingSms, caching));
        transitions.add(new Transition(sendingSms, synthesizing));
        transitions.add(new Transition(sendingSms, redirecting));
        transitions.add(new Transition(sendingSms, processingGatherChildren));
        transitions.add(new Transition(sendingSms, creatingRecording));
        transitions.add(new Transition(sendingSms, creatingSmsSession));
        transitions.add(new Transition(sendingSms, hangingUp));

        extensions = ExtensionController.getInstance().getExtensions(ExtensionType.FeatureAccessControl);

        httpAsycClientHelper = httpAsycClientHelper();

        this.uriUtils = RestcommConnectServiceProvider.getInstance().uriUtils();
    }

    @Override
    public abstract void onReceive(Object arg0) throws Exception;

    abstract List<NameValuePair> parameters();

    public ActorRef getAsrService() {
        if (asrService == null || (asrService != null && asrService.isTerminated())) {
            asrService = asr(configuration.subset("speech-recognizer"));
        }
        return asrService;
    }

    private boolean checkAsrService() {
        boolean AsrActive=false;
        Configuration Asrconfiguration=configuration.subset("speech-recognizer");
        if (Asrconfiguration.getString("api-key") != null && !Asrconfiguration.getString("api-key").isEmpty()){
            AsrActive=true;
        }
        return AsrActive;
    }

    ActorRef asr(final Configuration configuration) {
        final Props props = new Props(new UntypedActorFactory() {
            private static final long serialVersionUID = 1L;
            @Override
            public Actor create() throws Exception {
                return new ISpeechAsr(configuration);
            }
        });
        return getContext().actorOf(props);
    }

    @SuppressWarnings("unchecked")
    void asrResponse(final Object message) {
        final Class<?> klass = message.getClass();
        if (AsrResponse.class.equals(klass)) {
            final AsrResponse<String> response = (AsrResponse<String>) message;
            Transcription transcription = (Transcription) response.attributes().get("transcription");
            if (response.succeeded()) {
                transcription = transcription.setStatus(Transcription.Status.COMPLETED);
                if (response.get() != null ) {
                    transcription = transcription.setTranscriptionText(response.get());
                }
            } else {
                transcription = transcription.setStatus(Transcription.Status.FAILED);
            }
            final TranscriptionsDao transcriptions = storage.getTranscriptionsDao();
            transcriptions.updateTranscription(transcription);
            // Notify the callback listener.
            final Object attribute = response.attributes().get("callback");
            if (attribute != null) {
                final URI callback = (URI) attribute;
                final List<NameValuePair> parameters = parameters();
                request = new HttpRequestDescriptor(callback, "POST", parameters);
                downloader.tell(request, null);
            }
            // Update pending asr responses.
            outstandingAsrRequests--;
            // Try to stop the interpreter.
            postCleanup();
        }
    }

    public ActorRef getFaxService() {
        if (faxService == null || (faxService != null && faxService.isTerminated())) {
            faxService = fax(configuration.subset("fax-service"));
        }
        return faxService;
    }

    ActorRef fax(final Configuration configuration) {
        final Props props = new Props(new UntypedActorFactory() {
            private static final long serialVersionUID = 1L;
            @Override
            public Actor create() throws Exception {
                return new InterfaxService(configuration);
            }
        });
        return getContext().actorOf(props);
    }

    LinkedList<String> states = new LinkedList<String>(Arrays.asList("queued", "ringing", "in-progress", "completed", "busy", "failed", "no-answer", "canceled"));

    //Callback using the Akka ask pattern (http://doc.akka.io/docs/akka/2.2.5/java/untyped-actors.html#Ask__Send-And-Receive-Future) will force VoiceInterpter to wait until
    //Downloader finish with this callback before shutdown everything. Issue https://github.com/Mobicents/RestComm/issues/437
    void callback(boolean ask) {
        if (viStatusCallback != null) {
            if (states.remove(callState.toString().toLowerCase())) {
                if (logger.isInfoEnabled()) {
                    logger.info("About to execute viStatusCallback: " + viStatusCallback.toString());
                }
                if (viStatusCallbackMethod == null) {
                    viStatusCallbackMethod = "POST";
                }
                final List<NameValuePair> parameters = parameters();
                requestCallback = new HttpRequestDescriptor(viStatusCallback, viStatusCallbackMethod, parameters);
                if (!ask) {
                    downloader.tell(requestCallback, null);
                } else if (ask) {
                    final Timeout timeout = new Timeout(Duration.create(5, TimeUnit.SECONDS));
                    Future<Object> future = (Future<Object>) ask(downloader, requestCallback, timeout);
                    DownloaderResponse downloaderResponse = null;
                    try {
                        downloaderResponse = (DownloaderResponse) Await.result(future, Duration.create(10, TimeUnit.SECONDS));
                    } catch (Exception e) {
                        logger.error("Exception during callback with ask pattern");
                    }
                }
            } else {
                if (logger.isInfoEnabled()) {
                    logger.info("Status Callback has been already executed for state: ",callState.toString());
                }
            }
        } else if(logger.isInfoEnabled()){
            logger.info("status callback is null");
        }

    }

    void callback() {
        callback(false);
    }

    public ActorRef getCache() {
        if (cache == null || (cache != null && cache.isTerminated())) {
            final Configuration runtime = configuration.subset("runtime-settings");
            String path = runtime.getString("cache-path");
            if (!path.endsWith("/")) {
                path = path + "/";
            }
            path = path + accountId.toString();
            cachePath = path;
            String uri = runtime.getString("cache-uri");
            if (!uri.endsWith("/")) {
                uri = uri + "/";
            }
            try {
                uri = uriUtils.resolve(new URI(uri), accountId).toString();
            } catch (URISyntaxException e) {
                logger.error("URISyntaxException while trying to resolve Cache URI: " + e);
            }
            uri = uri + accountId.toString();
            cache = cache(path, uri);
        }
        return cache;
    }

    protected ActorRef cache(final String path, final String uri) {
        final Props props = new Props(new UntypedActorFactory() {
            private static final long serialVersionUID = 1L;
            @Override
            public UntypedActor create() throws Exception {
                return new DiskCacheFactory(configuration).getDiskCache(path, uri);
            }
        });
        return getContext().actorOf(props);
    }

    protected ActorRef downloader() {
        final Props props = new Props(new UntypedActorFactory() {
            private static final long serialVersionUID = 1L;

            @Override
            public UntypedActor create() throws Exception {
                return new Downloader();
            }
        });
        return getContext().actorOf(props);
    }

    String e164(final String number) {
        if (configuration.subset("runtime-settings").getBoolean("normalize-numbers-for-outbound-calls")) {
            final PhoneNumberUtil numbersUtil = PhoneNumberUtil.getInstance();
            try {
                final PhoneNumber result = numbersUtil.parse(number, "US");
                return numbersUtil.format(result, PhoneNumberFormat.E164);
            } catch (final NumberParseException ignored) {
                return number;
            }
        } else {
            return number;
        }
    }

    void invalidVerb(final Tag verb) {
        final ActorRef self = self();
        // Get the next verb.
        final GetNextVerb next = new GetNextVerb();
        parser.tell(next, self);
    }

    ActorRef mailer(final Configuration configuration) {
        final Props props = new Props(new UntypedActorFactory() {
            private static final long serialVersionUID = 1L;

            @Override
            public Actor create() throws Exception {
                return new EmailService(configuration);
            }
        });
        return getContext().actorOf(props);
    }

    private Notification notification(final int log, final int error, final String message) {
        final Notification.Builder builder = Notification.builder();
        final Sid sid = Sid.generate(Sid.Type.NOTIFICATION);
        builder.setSid(sid);
        builder.setAccountSid(accountId);
        builder.setCallSid(callInfo.sid());
        builder.setApiVersion(version);
        builder.setLog(log);
        builder.setErrorCode(error);
        String base = configuration.subset("runtime-settings").getString("error-dictionary-uri");
        try {
            base = uriUtils.resolve(new URI(base), accountId).toString();
        } catch (URISyntaxException e) {
            logger.error("URISyntaxException when trying to resolve Error-Dictionary URI: "+e);
        }
        StringBuilder buffer = new StringBuilder();
        buffer.append(base);
        if (!base.endsWith("/")) {
            buffer.append("/");
        }
        buffer.append(error).append(".html");
        final URI info = URI.create(buffer.toString());
        builder.setMoreInfo(info);
        builder.setMessageText(message);
        final DateTime now = DateTime.now();
        builder.setMessageDate(now);
        if (request != null) {
            builder.setRequestUrl(request.getUri());
            builder.setRequestMethod(request.getMethod());
            builder.setRequestVariables(request.getParametersAsString());
        }
        if (response != null) {
            builder.setResponseHeaders(response.getHeadersAsString());
            final String type = response.getContentType();
            if (type.contains("text/xml") || type.contains("application/xml") || type.contains("text/html")) {
                try {
                    builder.setResponseBody(response.getContentAsString());
                } catch (final IOException exception) {
                    logger.error(
                            "There was an error while reading the contents of the resource " + "located @ " + url.toString(),
                            exception);
                }
            }
        }
        buffer = new StringBuilder();
        buffer.append("/").append(version).append("/Accounts/");
        buffer.append(accountId.toString()).append("/Notifications/");
        buffer.append(sid.toString());
        final URI uri = URI.create(buffer.toString());
        builder.setUri(uri);
        return builder.build();
    }

    ActorRef parser(final String xml) {
        final Props props = new Props(new UntypedActorFactory() {
                private static final long serialVersionUID = 1L;

                @Override
                public UntypedActor create() throws IOException {
                    return new Parser(xml, self());
                }
            });
        return getContext().actorOf(props);
    }

    void postCleanup() {
        if (smsSessions.isEmpty() && outstandingAsrRequests == 0) {
            final UntypedActorContext context = getContext();
            if (parser != null)
                getContext().stop(parser);
            context.stop(self());
        }
        if (downloader != null && !downloader.isTerminated()) {
            getContext().stop(downloader);
        }
        if(httpAsycClientHelper != null && !httpAsycClientHelper.isTerminated()) {
            getContext().stop(httpAsycClientHelper);
        }
    }

    void sendMail(final Notification notification) {
        if (emailAddress == null || emailAddress.isEmpty()) {
            return;
        }
        final String EMAIL_SUBJECT = "RestComm Error Notification - Attention Required";
        final StringBuilder buffer = new StringBuilder();
        buffer.append("<strong>").append("Sid: ").append("</strong></br>");
        buffer.append(notification.getSid().toString()).append("</br>");
        buffer.append("<strong>").append("Account Sid: ").append("</strong></br>");
        buffer.append(notification.getAccountSid().toString()).append("</br>");
        buffer.append("<strong>").append("Call Sid: ").append("</strong></br>");
        buffer.append(notification.getCallSid().toString()).append("</br>");
        buffer.append("<strong>").append("API Version: ").append("</strong></br>");
        buffer.append(notification.getApiVersion()).append("</br>");
        buffer.append("<strong>").append("Log: ").append("</strong></br>");
        buffer.append(notification.getLog() == ERROR_NOTIFICATION ? "ERROR" : "WARNING").append("</br>");
        buffer.append("<strong>").append("Error Code: ").append("</strong></br>");
        buffer.append(notification.getErrorCode()).append("</br>");
        buffer.append("<strong>").append("More Information: ").append("</strong></br>");
        buffer.append(notification.getMoreInfo().toString()).append("</br>");
        buffer.append("<strong>").append("Message Text: ").append("</strong></br>");
        buffer.append(notification.getMessageText()).append("</br>");
        buffer.append("<strong>").append("Message Date: ").append("</strong></br>");
        buffer.append(notification.getMessageDate().toString()).append("</br>");
        buffer.append("<strong>").append("Request URL: ").append("</strong></br>");
        buffer.append(notification.getRequestUrl().toString()).append("</br>");
        buffer.append("<strong>").append("Request Method: ").append("</strong></br>");
        buffer.append(notification.getRequestMethod()).append("</br>");
        buffer.append("<strong>").append("Request Variables: ").append("</strong></br>");
        buffer.append(notification.getRequestVariables()).append("</br>");
        buffer.append("<strong>").append("Response Headers: ").append("</strong></br>");
        buffer.append(notification.getResponseHeaders()).append("</br>");
        buffer.append("<strong>").append("Response Body: ").append("</strong></br>");
        buffer.append(notification.getResponseBody()).append("</br>");
        final Mail emailMsg = new Mail(EMAIL_SENDER,emailAddress,EMAIL_SUBJECT, buffer.toString());
        if (mailerNotify == null){
            mailerNotify = mailer(configuration.subset("smtp-notify"));
        }
        mailerNotify.tell(new EmailRequest(emailMsg), self());
    }

    private final class SendingEmail extends AbstractAction {
        public SendingEmail(final ActorRef source){
            super(source);
        }

        @Override
        public void execute( final Object message) throws Exception {
            final Tag verb = (Tag)message;
            // Parse "from".
            String from;
            Attribute attribute = verb.attribute("from");
            if (attribute != null) {
                from = attribute.value();
            }else{
                Exception error = new Exception("From attribute was not defined");
                source.tell(new EmailResponse(error,error.getMessage()), source);
                return;
            }

            // Parse "to".
            String to;
            attribute = verb.attribute("to");
            if (attribute != null) {
                to = attribute.value();
            }else{
                Exception error = new Exception("To attribute was not defined");
                source.tell(new EmailResponse(error,error.getMessage()), source);
                return;
            }

            // Parse "cc".
            String cc="";
            attribute = verb.attribute("cc");
            if (attribute != null) {
                cc = attribute.value();
            }

            // Parse "bcc".
            String bcc="";
            attribute = verb.attribute("bcc");
            if (attribute != null) {
                bcc = attribute.value();
            }

            // Parse "subject"
            String subject;
            attribute = verb.attribute("subject");
            if (attribute != null) {
                subject = attribute.value();
            }else{
                subject="Restcomm Email Service";
            }

            // Send the email.
            final Mail emailMsg = new Mail(from, to, subject, verb.text(),cc,bcc);
            if (mailerService == null){
                mailerService = mailer(configuration.subset("smtp-service"));
            }
            mailerService.tell(new EmailRequest(emailMsg), self());
        }
    }

    void smsResponse(final Object message) {
        final Class<?> klass = message.getClass();
        final ActorRef self = self();
        if (SmsSessionResponse.class.equals(klass)) {
            final SmsSessionResponse response = (SmsSessionResponse) message;
            final SmsSessionInfo info = response.info();
            SmsMessage record = (SmsMessage) info.attributes().get("record");
            final SmsMessagesDao messages = storage.getSmsMessagesDao();
            messages.updateSmsMessage(record);
            // Notify the callback listener.
            final Object attribute = info.attributes().get("callback");
            if (attribute != null) {
                final URI callback = (URI) attribute;
                final List<NameValuePair> parameters = parameters();
                request = new HttpRequestDescriptor(callback, "POST", parameters);
                downloader.tell(request, null);
            }
            // Destroy the sms session.
            final ActorRef session = smsSessions.remove(record.getSid());
            final DestroySmsSession destroy = new DestroySmsSession(session);
            smsService.tell(destroy, self);
        }
    }

    public ActorRef getSynthesizer() {
        if (synthesizer == null || (synthesizer != null && synthesizer.isTerminated())) {
            String ttsEngine = configuration.subset("speech-synthesizer").getString("[@active]");
            Configuration ttsConf = configuration.subset(ttsEngine);
            synthesizer = tts(ttsConf);
        }
        return synthesizer;
    }

    private ActorRef httpAsycClientHelper;

    protected ActorRef httpAsycClientHelper(){
        final Props props = new Props(new UntypedActorFactory() {
            private static final long serialVersionUID = 1L;

            @Override
            public UntypedActor create() throws Exception {
                return new HttpAsycClientHelper();
            }
        });
        return getContext().actorOf(props);

    }
    private void notifyAsyncWebHook(HttpRequestDescriptor httpReq) {
        httpAsycClientHelper.tell(httpReq, self());


    }

    ActorRef tts(final Configuration ttsConf) {
        final String classpath = ttsConf.getString("[@class]");
        final Props props = new Props(new UntypedActorFactory() {
            private static final long serialVersionUID = 1L;

            @Override
            public Actor create() throws Exception {
                return (UntypedActor) Class.forName(classpath).getConstructor(Configuration.class).newInstance(ttsConf);
            }
        });
        return getContext().actorOf(props);
    }

    protected boolean is(State state) {
        return this.fsm.state().equals(state);
    }

    abstract class AbstractAction implements Action {
        protected final ActorRef source;

        public AbstractAction(final ActorRef source) {
            super();
            this.source = source;
        }
    }

    final class AcquiringAsrInfo extends AbstractAction {
        public AcquiringAsrInfo(final ActorRef source) {
            super(source);
        }

        @Override
        public void execute(final Object message) throws Exception {
            getAsrService().tell(new GetAsrInfo(), source);
        }
    }

    final class AcquiringSpeechSynthesizerInfo extends AbstractAction {
        public AcquiringSpeechSynthesizerInfo(final ActorRef source) {
            super(source);
        }

        @SuppressWarnings({ "unchecked" })
        @Override
        public void execute(final Object message) throws Exception {
            final AsrResponse<AsrInfo> response = (AsrResponse<AsrInfo>) message;
            asrInfo = response.get();
            getSynthesizer().tell(new GetSpeechSynthesizerInfo(), source);
        }
    }

    final class AcquiringCallInfo extends AbstractAction {
        public AcquiringCallInfo(final ActorRef source) {
            super(source);
        }

        @SuppressWarnings({ "unchecked" })
        @Override
        public void execute(final Object message) throws Exception {
            final SpeechSynthesizerResponse<SpeechSynthesizerInfo> response = (SpeechSynthesizerResponse<SpeechSynthesizerInfo>) message;
            synthesizerInfo = response.get();
//            call.tell(new Observe(source), source);
//            //Enable Monitoring Service for the call
//            if (monitoring != null)
//               call.tell(new Observe(monitoring), source);
            call.tell(new GetCallInfo(), source);
        }
    }

    final class NotFound extends AbstractAction {
        public NotFound(final ActorRef source) {
            super(source);
        }

        @Override
        public void execute(final Object message) throws Exception {
            // final Class<?> klass = message.getClass();
            final DownloaderResponse response = (DownloaderResponse) message;
            if (logger.isDebugEnabled()){
                logger.debug("response succeeded " + response.succeeded() + ", statusCode " + response.get().getStatusCode());
            }
            final Notification notification = notification(WARNING_NOTIFICATION, 21402, "URL Not Found : "
                    + response.get().getURI());
            final NotificationsDao notifications = storage.getNotificationsDao();
            notifications.addNotification(notification);
            // Hang up the call.
            call.tell(new org.restcomm.connect.telephony.api.NotFound(), source);
        }
    }

    final class Rejecting extends AbstractAction {
        public Rejecting(final ActorRef source) {
            super(source);
        }

        @Override
        public void execute(final Object message) throws Exception {
            final Class<?> klass = message.getClass();
            if (Tag.class.equals(klass)) {
                verb = (Tag) message;
            }
            String reason = "rejected";
            Attribute attribute = verb.attribute("reason");
            if (attribute != null) {
                reason = attribute.value();
                if (reason != null && !reason.isEmpty()) {
                    if ("rejected".equalsIgnoreCase(reason)) {
                        reason = "rejected";
                    } else if ("busy".equalsIgnoreCase(reason)) {
                        reason = "busy";
                    } else {
                        reason = "rejected";
                    }
                } else {
                    reason = "rejected";
                }
            }
            // Reject the call.
            call.tell(new Reject(reason), source);
        }
    }

    final class PlayingRejectionPrompt extends AbstractAction {
        public PlayingRejectionPrompt(final ActorRef source) {
            super(source);
        }

        @Override
        public void execute(final Object message) throws Exception {
            String path = configuration.subset("runtime-settings").getString("prompts-uri");
            if (!path.endsWith("/")) {
                path += "/";
            }
            path += "reject.wav";
            URI uri = null;
            try {
                uri = uriUtils.resolve(new URI(path), accountId);
            } catch (final Exception exception) {
                final Notification notification = notification(ERROR_NOTIFICATION, 12400, exception.getMessage());
                final NotificationsDao notifications = storage.getNotificationsDao();
                notifications.addNotification(notification);
                sendMail(notification);
                final StopInterpreter stop = new StopInterpreter();
                source.tell(stop, source);
                return;
            }
            final Play play = new Play(uri, 1);
            call.tell(play, source);
        }
    }

    final class Faxing extends AbstractAction {
        public Faxing(final ActorRef source) {
            super(source);
        }

        @Override
        public void execute(Object message) throws Exception {
            final DiskCacheResponse response = (DiskCacheResponse) message;
            // Parse "from".
            String from = callInfo.to();
            Attribute attribute = verb.attribute("from");
            if (attribute != null) {
                from = attribute.value();
                if (from != null && from.isEmpty()) {
                    from = e164(from);
                    if (from == null) {
                        from = verb.attribute("from").value();
                        final StopInterpreter stop = new StopInterpreter();
                        source.tell(stop, source);
                        return;
                    }
                }
            }
            // Parse "to".
            String to = callInfo.from();
            attribute = verb.attribute("to");
            if (attribute != null) {
                to = attribute.value();
                if (to != null && !to.isEmpty()) {
                    to = e164(to);
                    if (to == null) {
                        to = verb.attribute("to").value();
                        final StopInterpreter stop = new StopInterpreter();
                        source.tell(stop, source);
                        return;
                    }
                }
            }
            // Send the fax.
            final String uri = response.get().toString();
            final int offset = uri.lastIndexOf("/");
            final String path = cachePath + "/" + uri.substring(offset + 1, uri.length());
            final FaxRequest fax = new FaxRequest(to, new File(path));
            getFaxService().tell(fax, source);
        }
    }

    final class Pausing extends AbstractAction {
        public Pausing(final ActorRef source) {
            super(source);
        }

        @Override
        public void execute(final Object message) throws Exception {
            final Class<?> klass = message.getClass();
            if (Tag.class.equals(klass)) {
                verb = (Tag) message;
            }
            int length = 1;
            final Attribute attribute = verb.attribute("length");
            if (attribute != null) {
                final String number = attribute.value();
                if (number != null && !number.isEmpty()) {
                    try {
                        length = Integer.parseInt(number);
                    } catch (final NumberFormatException exception) {
                        final Notification notification = notification(WARNING_NOTIFICATION, 13910, "Invalid length value.");
                        final NotificationsDao notifications = storage.getNotificationsDao();
                        notifications.addNotification(notification);
                    }
                }
            }
            final UntypedActorContext context = getContext();
            context.setReceiveTimeout(Duration.create(length, TimeUnit.SECONDS));
        }
    }

    final class CheckCache extends AbstractAction {
        public CheckCache(final ActorRef source) {
            super(source);
        }

        @Override
        public void execute(final Object message) throws Exception {
            final Class<?> klass = message.getClass();
            if (Tag.class.equals(klass)) {
                verb = (Tag) message;
            }
            // else {
            // logger.info("Can't check cache, message not verb. Moving to the next verb");
            // // final GetNextVerb next = GetNextVerb.instance();
            // // parser.tell(next, source);
            // return;
            // }
            String hash = hash(verb);
            DiskCacheRequest request = new DiskCacheRequest(hash);
            if (logger.isErrorEnabled()) {
                logger.info("Checking cache for hash: " + hash);
            }
            getCache().tell(request, source);
        }
    }

    final class Caching extends AbstractAction {
        public Caching(final ActorRef source) {
            super(source);
        }

        @SuppressWarnings("unchecked")
        @Override
        public void execute(final Object message) throws Exception {
            final Class<?> klass = message.getClass();
            if (SpeechSynthesizerResponse.class.equals(klass)) {
                final SpeechSynthesizerResponse<URI> response = (SpeechSynthesizerResponse<URI>) message;
                final DiskCacheRequest request = new DiskCacheRequest(response.get());
                getCache().tell(request, source);
            } else if (Tag.class.equals(klass)) {
                if (Tag.class.equals(klass)) {
                    verb = (Tag) message;
                }
                // Parse the URL.
                final String text = verb.text();
                if (text != null && !text.isEmpty()) {
                    // Try to cache the media.
                    URI target = null;
                    try {
                        target = URI.create(text);
                    } catch (final Exception exception) {
                        final Notification notification = notification(ERROR_NOTIFICATION, 11100, text + " is an invalid URI.");
                        final NotificationsDao notifications = storage.getNotificationsDao();
                        notifications.addNotification(notification);
                        sendMail(notification);
                        final StopInterpreter stop = new StopInterpreter();
                        source.tell(stop, source);
                        return;
                    }
                    final URI base = request.getUri();
                    final URI uri = uriUtils.resolveWithBase(base, target);
                    final DiskCacheRequest request = new DiskCacheRequest(uri);
                    getCache().tell(request, source);
                } else {
                    // Ask the parser for the next action to take.
                    final GetNextVerb next = new GetNextVerb();
                    parser.tell(next, source);
                }
            }
        }
    }

    final class Playing extends AbstractAction {
        public Playing(final ActorRef source) {
            super(source);
        }

        @Override
        public void execute(final Object message) throws Exception {
            final Class<?> klass = message.getClass();
            if (DiskCacheResponse.class.equals(klass)) {
                // Parse the loop attribute.
                int loop = 1;
                final Attribute attribute = verb.attribute("loop");
                if (attribute != null) {
                    final String number = attribute.value();
                    if (number != null && !number.isEmpty()) {
                        try {
                            loop = Integer.parseInt(number);
                        } catch (final NumberFormatException ignored) {
                            final NotificationsDao notifications = storage.getNotificationsDao();
                            Notification notification = null;
                            if (Verbs.say.equals(verb.name())) {
                                notification = notification(WARNING_NOTIFICATION, 13510, loop + " is an invalid loop value.");
                                notifications.addNotification(notification);
                            } else if (Verbs.play.equals(verb.name())) {
                                notification = notification(WARNING_NOTIFICATION, 13410, loop + " is an invalid loop value.");
                                notifications.addNotification(notification);
                            }
                        }
                    }
                }
                final DiskCacheResponse response = (DiskCacheResponse) message;
                final Play play = new Play(response.get(), loop);
                call.tell(play, source);
            }
        }
    }

    String hash(Object message) {
        Map<String, String> details = getSynthesizeDetails(message);
        if (details == null) {
            if (logger.isInfoEnabled()) {
                logger.info("Cannot generate hash, details are null");
            }
            return null;
        }
        String voice = details.get("voice");
        String language = details.get("language");
        String text = details.get("text");
        return HashGenerator.hashMessage(voice, language, text);
    }

    Map<String, String> getSynthesizeDetails(final Object message) {
        final Class<?> klass = message.getClass();

        Map<String, String> details = new HashMap<String, String>();

        if (Tag.class.equals(klass)) {
            verb = (Tag) message;
        } else {
            return null;
        }
        if (!Verbs.say.equals(verb.name()))
            return null;

        // Parse the voice attribute.
        String voice = "man";
        Attribute attribute = verb.attribute("voice");
        if (attribute != null) {
            voice = attribute.value();
            if (voice != null && !voice.isEmpty()) {
                if (!"man".equals(voice) && !"woman".equals(voice)) {
                    final Notification notification = notification(WARNING_NOTIFICATION, 13511, voice
                            + " is an invalid voice value.");
                    final NotificationsDao notifications = storage.getNotificationsDao();
                    notifications.addNotification(notification);
                    voice = "man";
                }
            } else {
                voice = "man";
            }
        }
        // Parse the language attribute.
        String language = "en";
        attribute = verb.attribute(GatherAttributes.ATTRIBUTE_LANGUAGE);
        if (attribute != null) {
            language = attribute.value();
            if (language != null && !language.isEmpty()) {
                if (!synthesizerInfo.languages().contains(language)) {
                    language = "en";
                }
            } else {
                language = "en";
            }
        }
        // Synthesize.
        String text = verb.text();

        details.put("voice", voice);
        details.put("language", language);
        details.put("text", text);

        return details;

    }

    final class Synthesizing extends AbstractAction {
        public Synthesizing(final ActorRef source) {
            super(source);
        }

        @Override
        public void execute(final Object message) throws Exception {

            final Class<?> klass = message.getClass();

            if (Tag.class.equals(klass)) {
                verb = (Tag) message;
            }

            Map<String, String> details = getSynthesizeDetails(verb);
            if (details != null && !details.isEmpty() && details.get("text") != null) {
                String voice = details.get("voice");
                String language = details.get("language");
                String text = details.get("text");
                final SpeechSynthesizerRequest synthesize = new SpeechSynthesizerRequest(voice, language, text);
                getSynthesizer().tell(synthesize, source);
            } else {
                // Ask the parser for the next action to take.
                final GetNextVerb next = new GetNextVerb();
                parser.tell(next, source);
            }
        }
    }

    final class HangingUp extends AbstractAction {
        public HangingUp(final ActorRef source) {
            super(source);
        }

        @Override
        public void execute(final Object message) throws Exception {
            final Class<?> klass = message.getClass();
            if (Tag.class.equals(klass)) {
                verb = (Tag) message;
            }
            // Hang up the call.
            if (ParserFailed.class.equals(klass)) {
                call.tell(new Hangup("Problem_to_parse_downloaded_RCML"), source);
            } else if (CallManagerResponse.class.equals(klass)) {
                String reason = ((CallManagerResponse)message).cause().getMessage().replaceAll("\\s","_");
                call.tell(new Hangup(reason), source);
            } else if (message instanceof SmsServiceResponse) {
                //Blocked SMS Session request
                call.tell(new Hangup(((SmsServiceResponse)message).cause().getMessage()), self());
            } else if (Tag.class.equals(klass) && Verbs.hangup.equals(verb.name())) {
                Integer sipResponse = outboundCallResponse != null ? outboundCallResponse : SipServletResponse.SC_REQUEST_TERMINATED;
                call.tell(new Hangup(sipResponse), source);
            } else {
                call.tell(new Hangup(outboundCallResponse), source);
            }
        }
    }

    final class Redirecting extends AbstractAction {
        public Redirecting(final ActorRef source) {
            super(source);
        }

        @Override
        public void execute(final Object message) throws Exception {
            final Class<?> klass = message.getClass();
            if (Tag.class.equals(klass)) {
                verb = (Tag) message;
            }
            final NotificationsDao notifications = storage.getNotificationsDao();
            String method = "POST";
            Attribute attribute = verb.attribute(GatherAttributes.ATTRIBUTE_METHOD);
            if (attribute != null) {
                method = attribute.value();
                if (method != null && !method.isEmpty()) {
                    if (!"GET".equalsIgnoreCase(method) && !"POST".equalsIgnoreCase(method)) {
                        final Notification notification = notification(WARNING_NOTIFICATION, 13710, method
                                + " is not a valid HTTP method for <Redirect>");
                        notifications.addNotification(notification);
                        method = "POST";
                    }
                } else {
                    method = "POST";
                }
            }
            final String text = verb.text();
            if (text != null && !text.isEmpty()) {
                // Try to redirect.
                URI target = null;
                try {
                    target = URI.create(text);
                } catch (final Exception exception) {
                    final Notification notification = notification(ERROR_NOTIFICATION, 11100, text + " is an invalid URI.");
                    notifications.addNotification(notification);
                    sendMail(notification);
                    final StopInterpreter stop = new StopInterpreter();
                    source.tell(stop, source);
                    return;
                }
                final URI base = request.getUri();
                final URI uri = uriUtils.resolveWithBase(base, target);
                final List<NameValuePair> parameters = parameters();
                request = new HttpRequestDescriptor(uri, method, parameters);
                downloader.tell(request, source);
            } else {
                // Ask the parser for the next action to take.
                final GetNextVerb next = new GetNextVerb();
                parser.tell(next, source);
            }
        }
    }

    abstract class AbstractGatherAction extends AbstractAction {
        public AbstractGatherAction(final ActorRef source) {
            super(source);
        }

        protected String finishOnKey(final Tag container) {
            String finishOnKey = "#";
            Attribute attribute = container.attribute(GatherAttributes.ATTRIBUTE_FINISH_ON_KEY);
            if (attribute != null) {
                finishOnKey = attribute.value();
                if (finishOnKey != null && !finishOnKey.isEmpty()) {
                    if (!PATTERN.matcher(finishOnKey).matches()) {
                        final NotificationsDao notifications = storage.getNotificationsDao();
                        final Notification notification = notification(WARNING_NOTIFICATION, 13310, finishOnKey
                                + " is not a valid finishOnKey value");
                        notifications.addNotification(notification);
                        finishOnKey = "#";
                    }
                } else {
                    finishOnKey = "#";
                }
            }
            return finishOnKey;
        }
    }

    final class ProcessingGatherChildren extends AbstractGatherAction {
        public ProcessingGatherChildren(final ActorRef source) {
            super(source);
        }

        @SuppressWarnings("unchecked")
        @Override
        public void execute(final Object message) throws Exception {
            processingGather = true;
            final Class<?> klass = message.getClass();
            final NotificationsDao notifications = storage.getNotificationsDao();
            if (SpeechSynthesizerResponse.class.equals(klass)) {
                final SpeechSynthesizerResponse<URI> response = (SpeechSynthesizerResponse<URI>) message;
                final DiskCacheRequest request = new DiskCacheRequest(response.get());
                getCache().tell(request, source);
            } else {
                if (Tag.class.equals(klass)) {
                    verb = (Tag) message;
                    gatherPrompts = new ArrayList<URI>();
                    gatherChildren = new ArrayList<Tag>(verb.children());
                } else if (DiskCacheResponse.class.equals(klass)) {
                    if (gatherPrompts == null)
                        gatherPrompts = new ArrayList<URI>();
                    if (gatherChildren == null)
                        gatherChildren = new ArrayList<Tag>(verb.children());
                    final DiskCacheResponse response = (DiskCacheResponse) message;
                    final URI uri = response.get();
                    Tag child = null;
                    if (!gatherChildren.isEmpty())
                        child = gatherChildren.remove(0);
                    // Parse the loop attribute.
                    int loop = 1;
                    Attribute attribute = null;
                    if (child != null)
                        attribute = child.attribute("loop");
                    if (attribute != null) {
                        final String number = attribute.value();
                        if (number != null && !number.isEmpty()) {
                            try {
                                loop = Integer.parseInt(number);
                            } catch (final NumberFormatException ignored) {
                                Notification notification = null;
                                if (Verbs.say.equals(child.name())) {
                                    notification = notification(WARNING_NOTIFICATION, 13322, loop
                                            + " is an invalid loop value.");
                                    notifications.addNotification(notification);
                                }
                            }
                        }
                    }
                    for (int counter = 0; counter < loop; counter++) {
                        gatherPrompts.add(uri);
                    }
                }
                for (int index = 0; index < gatherChildren.size(); index++) {
                    final Tag child = gatherChildren.get(index);
                    if (Verbs.play.equals(child.name())) {
                        final String text = child.text();
                        if (text != null && !text.isEmpty()) {
                            URI target = null;
                            try {
                                target = URI.create(text);
                            } catch (final Exception exception) {
                                final Notification notification = notification(ERROR_NOTIFICATION, 13325, text
                                        + " is an invalid URI.");
                                notifications.addNotification(notification);
                                sendMail(notification);
                                final StopInterpreter stop = new StopInterpreter();
                                source.tell(stop, source);
                                return;
                            }
                            final URI base = request.getUri();
                            final URI uri = uriUtils.resolveWithBase(base, target);
                            // Cache the prompt.
                            final DiskCacheRequest request = new DiskCacheRequest(uri);
                            getCache().tell(request, source);
                            break;
                        }
                    } else if (Verbs.say.equals(child.name())) {
                        // Parse the voice attribute.
                        String voice = "man";
                        Attribute attribute = child.attribute("voice");
                        if (attribute != null) {
                            voice = attribute.value();
                            if (voice != null && !voice.isEmpty()) {
                                if (!"man".equals(voice) && !"woman".equals(voice)) {
                                    final Notification notification = notification(WARNING_NOTIFICATION, 13321, voice
                                            + " is an invalid voice value.");
                                    notifications.addNotification(notification);
                                    voice = "man";
                                }
                            } else {
                                voice = "man";
                            }
                        }
                        // Parse the language attribute.
                        String language = "en";
                        attribute = child.attribute("language");
                        if (attribute != null) {
                            language = attribute.value();
                            if (language != null && !language.isEmpty()) {
                                if (!synthesizerInfo.languages().contains(language)) {
                                    language = "en";
                                }
                            } else {
                                language = "en";
                            }
                        }
                        String text = child.text();
                        if (text != null && !text.isEmpty()) {
                            // final SpeechSynthesizerRequest synthesize = new SpeechSynthesizerRequest(voice, language, text);
                            // synthesizer.tell(synthesize, source);
                            // break;
                            String hash = hash(child);
                            DiskCacheRequest request = new DiskCacheRequest(hash);
                            getCache().tell(request, source);
                            break;
                        }
                    } else if (Verbs.pause.equals(child.name())) {
                        int length = 1;
                        final Attribute attribute = child.attribute("length");
                        if (attribute != null) {
                            final String number = attribute.value();
                            if (number != null && !number.isEmpty()) {
                                try {
                                    length = Integer.parseInt(number);
                                } catch (final NumberFormatException ignored) {
                                }
                            }
                        }
                        String path = configuration.subset("runtime-settings").getString("prompts-uri");
                        if (!path.endsWith("/")) {
                            path += "/";
                        }
                        path += "one-second-silence.wav";
                        final URI uri = uriUtils.resolve(new URI(path));
                        for (int counter = 0; counter < length; counter++) {
                            gatherPrompts.add(uri);
                        }
                    }
                }
                // Make sure we don't leave any pauses at the beginning
                // since we can't concurrently modify the list.
                if (!gatherChildren.isEmpty()) {
                    Tag child = null;
                    do {
                        child = gatherChildren.get(0);
                        if (child != null) {
                            if (Verbs.pause.equals(child.name())) {
                                gatherChildren.remove(0);
                            }
                        }
                    } while (Verbs.pause.equals(child.name()));
                }
                // Start gathering.
                if (gatherChildren.isEmpty()) {
                    if (gatherVerb != null)
                        verb = gatherVerb;
                    final StartGathering start = StartGathering.instance();
                    source.tell(start, source);
                    processingGather = false;
                }
            }
        }
    }

    final class Gathering extends AbstractGatherAction {
        public Gathering(final ActorRef source) {
            super(source);
        }

        @Override
        public void execute(final Object message) throws Exception {

            // check mandatory attribute partialResultCallback
            Attribute partialResultCallbackAttr = verb.attribute(GatherAttributes.ATTRIBUTE_PARTIAL_RESULT_CALLBACK);
            final NotificationsDao notifications = storage.getNotificationsDao();
            // parse attribute "input"
            Attribute typeAttr = verb.attribute(GatherAttributes.ATTRIBUTE_INPUT);
            Collect.Type inputType = null;
            if (typeAttr == null) {
                inputType = Collect.Type.DTMF;
            } else {
                inputType = Collect.Type.parseOrDefault(typeAttr.value(), Collect.Type.DTMF);
            }

            if (inputType.equals(Collect.Type.SPEECH) || inputType.equals(Collect.Type.DTMF_SPEECH)) {
                ExtensionController ec = ExtensionController.getInstance();
                final IExtensionFeatureAccessRequest far = new FeatureAccessRequest(FeatureAccessRequest.Feature.ASR, accountId);
                ExtensionResponse er = ec.executePreInboundAction(far, extensions);
                if (!er.isAllowed()) {
                    if (logger.isDebugEnabled()) {
                        final String errMsg = "ASR feature is not allowed";
                        logger.debug(errMsg);
                    }
                    String errMsg = "ASR feature is not allowed";
                    notification(WARNING_NOTIFICATION, 11001, errMsg);
                    call.tell(new Hangup(errMsg), source);
                    return;
                }
                ec.executePostInboundAction(far, extensions);
            }

            // parse attribute "language"
            Attribute langAttr = verb.attribute(GatherAttributes.ATTRIBUTE_LANGUAGE);
            String defaultLang = restcommConfiguration.getMgAsr().getDefaultLanguage();
            String lang = parseAttrLanguage(langAttr, defaultLang);

            // parse attribute "hints"
            String hints = null;
            Attribute hintsAttr = verb.attribute(GatherAttributes.ATTRIBUTE_HINTS);
            if (hintsAttr != null && !StringUtils.isEmpty(hintsAttr.value())) {
                hints = hintsAttr.value();
            } else if (inputType != Collect.Type.DTMF) {
                logger.warning("'{}' attribute is null or empty", GatherAttributes.ATTRIBUTE_HINTS);
            }

            // Parse finish on key.
            finishOnKey = finishOnKey(verb);
            // Parse start Input key
            startInputKey = "*0123456789"; // Default value.
            Attribute attribute = verb.attribute(GatherAttributes.ATTRIBUTE_START_INPUT_KEY);
            if (attribute != null) {
                final String value = attribute.value();
                if (!StringUtils.isEmpty(value)) {
                    if (!PATTERN.matcher(value).matches()) {
                        final Notification notification = notification(WARNING_NOTIFICATION, 13315, value
                                + " is not a valid startInputKey value");
                        notifications.addNotification(notification);
                    } else {
                        startInputKey = value;
                    }
                }
            }
            // Parse the number of digits.
            attribute = verb.attribute(GatherAttributes.ATTRIBUTE_NUM_DIGITS);
            if (attribute != null) {
                final String value = attribute.value();
                if (!StringUtils.isEmpty(value)) {
                    try {
                        numberOfDigits = Integer.parseInt(value);
                    } catch (final NumberFormatException exception) {
                        final Notification notification = notification(WARNING_NOTIFICATION, 13314, numberOfDigits
                                + " is not a valid numDigits value");
                        notifications.addNotification(notification);
                    }
                }
            }
            // Parse timeout.
            int timeout = restcommConfiguration.getMgAsr().getDefaultGatheringTimeout();
            attribute = verb.attribute(GatherAttributes.ATTRIBUTE_TIME_OUT);
            if (attribute != null) {
                final String value = attribute.value();
                if (!StringUtils.isEmpty(value)) {
                    try {
                        timeout = Integer.parseInt(value);
                    } catch (final NumberFormatException exception) {
                        final Notification notification = notification(WARNING_NOTIFICATION, 13313, timeout
                                + " is not a valid timeout value");
                        notifications.addNotification(notification);
                    }
                }
            }
            // Start gathering.
            final Collect collect = new Collect(restcommConfiguration.getMgAsr().getDefaultDriver(), inputType, gatherPrompts,
                    null, timeout, finishOnKey, startInputKey, numberOfDigits, lang, hints, partialResultCallbackAttr != null && !StringUtils.isEmpty(partialResultCallbackAttr.value()));
            call.tell(collect, source);
            // Some clean up.
            gatherChildren = null;
            gatherPrompts = null;
            dtmfReceived = false;
            collectedDigits = new StringBuffer("");
        }

        private String parseAttrLanguage(Attribute langAttr, String defaultLang) {
            if (langAttr != null && !StringUtils.isEmpty(langAttr.value())) {
                return restcommConfiguration.getMgAsr().getLanguages().contains(langAttr.value()) ? langAttr.value() : defaultLang;
            } else {
                logger.warning("Illegal or unsupported attribute value: '{}'. Will be use default value '{}'", langAttr,
                        defaultLang);
                return defaultLang;
            }
        }
    }

    private abstract class CallbackGatherAction extends AbstractGatherAction {

        public CallbackGatherAction(ActorRef source) {
            super(source);
        }

        protected void execHttpRequest(final NotificationsDao notifications,
                                       final Attribute callbackAttr, final Attribute methodAttr,
                                       final List<NameValuePair> parameters) {
            if (callbackAttr == null) {
                final Notification notification = notification(ERROR_NOTIFICATION, 11101, "Callback attribute is null");
                notifications.addNotification(notification);
                sendMail(notification);
                final StopInterpreter stop = new StopInterpreter();
                source.tell(stop, source);
                logger.error("CallbackAttribute is null, CallbackGatherAction failed");
            }
            String action = callbackAttr.value();
            if (StringUtils.isEmpty(action)) {
                final Notification notification = notification(ERROR_NOTIFICATION, 11101, "Callback attribute value is null or empty");
                notifications.addNotification(notification);
                sendMail(notification);
                final StopInterpreter stop = new StopInterpreter();
                source.tell(stop, source);
                logger.error("Action url is null or empty");
            }
            URI target;
            try {
                target = URI.create(action);
            } catch (final Exception exception) {
                final Notification notification = notification(ERROR_NOTIFICATION, 11100, action
                        + " is an invalid URI.");
                notifications.addNotification(notification);
                sendMail(notification);
                final StopInterpreter stop = new StopInterpreter();
                source.tell(stop, source);
                return;
            }
            String method = "POST";
            if (methodAttr != null) {
                method = methodAttr.value();
                if (!StringUtils.isEmpty(method)) {
                    if (!"GET".equalsIgnoreCase(method) && !"POST".equalsIgnoreCase(method)) {
                        final Notification notification = notification(WARNING_NOTIFICATION, 14104, method
                                + " is not a valid HTTP method for <Gather>");
                        notifications.addNotification(notification);
                        method = "POST";
                    }
                } else {
                    method = "POST";
                }
            }
            final URI base = request.getUri();
            final URI uri = uriUtils.resolveWithBase(base, target);
            request = new HttpRequestDescriptor(uri, method, parameters);
            notifyAsyncWebHook(request);
        }
    }

    final class FinishGathering extends CallbackGatherAction {

        public FinishGathering(final ActorRef source) {
            super(source);
        }

        @Override
        public void execute(final Object message) throws Exception {
            final NotificationsDao notifications = storage.getNotificationsDao();

            String digits = collectedDigits.toString();
            collectedDigits = new StringBuffer();
            if (logger.isInfoEnabled()) {
                logger.info("Digits collected: " + digits);
            }
            if (digits.equals(finishOnKey)) {
                digits = "";
            }
            if (logger.isDebugEnabled()) {
                logger.debug("Digits collected : " + digits);
            }
            // https://bitbucket.org/telestax/telscale-restcomm/issue/150/verb-is-looping-by-default-and-never
            // If the 'timeout' is reached before the caller enters any digits, or if the caller enters the 'finishOnKey' value
            // before entering any other digits, Twilio will not make a request to the 'action' URL but instead continue
            // processing
            // the current TwiML document with the verb immediately following the <Gather>
            Attribute action = verb.attribute(GatherAttributes.ATTRIBUTE_ACTION);
            if (action != null && (!digits.trim().isEmpty() || !StringUtils.isEmpty(speechResult))) {
                // Redirect to the action url.
                if (digits.endsWith(finishOnKey)) {
                    final int finishOnKeyIndex = digits.lastIndexOf(finishOnKey);
                    digits = digits.substring(0, finishOnKeyIndex);
                }
                final List<NameValuePair> parameters = parameters();
                parameters.add(new BasicNameValuePair("Digits", digits));
                parameters.add(new BasicNameValuePair("SpeechResult", speechResult));

                execHttpRequest(notifications, action, verb.attribute(GatherAttributes.ATTRIBUTE_METHOD), parameters);
                speechResult = null;
                return;
            }
            if (logger.isInfoEnabled()) {
                logger.info("Attribute, Action or Digits is null, FinishGathering failed, moving to the next available verb");
            }
            speechResult = null;
            // Ask the parser for the next action to take.
            final GetNextVerb next = new GetNextVerb();
            parser.tell(next, source);
        }
    }

    final class PartialGathering extends CallbackGatherAction {

        public PartialGathering(final ActorRef source) {
            super(source);
        }

        @Override
        public void execute(final Object message) throws Exception {
            if (verb.attribute(GatherAttributes.ATTRIBUTE_PARTIAL_RESULT_CALLBACK) != null
                    && !StringUtils.isEmpty(verb.attribute(GatherAttributes.ATTRIBUTE_PARTIAL_RESULT_CALLBACK).value())) {
                final MediaGroupResponse<CollectedResult> asrResponse = (MediaGroupResponse<CollectedResult>) message;

                final List<NameValuePair> parameters = parameters();
                parameters.add(new BasicNameValuePair("UnstableSpeechResult", asrResponse.get().getResult()));

                final NotificationsDao notifications = storage.getNotificationsDao();
                execHttpRequest(notifications, verb.attribute(GatherAttributes.ATTRIBUTE_PARTIAL_RESULT_CALLBACK),
                        verb.attribute(GatherAttributes.ATTRIBUTE_PARTIAL_RESULT_CALLBACK_METHOD), parameters);
            }
        }
    }

    final class CreatingRecording extends AbstractAction {
        public CreatingRecording(final ActorRef source) {
            super(source);
        }

        @Override
        public void execute(final Object message) throws Exception {
            final Class<?> klass = message.getClass();
            if (Tag.class.equals(klass)) {
                verb = (Tag) message;
            }
            final NotificationsDao notifications = storage.getNotificationsDao();
            String finishOnKey = "1234567890*#";
            Attribute attribute = verb.attribute(GatherAttributes.ATTRIBUTE_FINISH_ON_KEY);
            if (attribute != null) {
                finishOnKey = attribute.value();
                if (finishOnKey != null && !finishOnKey.isEmpty()) {
                    //https://github.com/RestComm/Restcomm-Connect/issues/1886
                    if (!finishOnKey.equals("-1")) {
                        if (!PATTERN.matcher(finishOnKey).matches()) {
                            final Notification notification = notification(WARNING_NOTIFICATION, 13613, finishOnKey
                                    + " is not a valid finishOnKey value");
                            notifications.addNotification(notification);
                            //https://github.com/RestComm/Restcomm-Connect/issues/1925
                            finishOnKey = "#";
                        }
                    }
                } else {
                    //https://github.com/RestComm/Restcomm-Connect/issues/1925
                    finishOnKey = "#";
                }
            }
            boolean playBeep = true;
            attribute = verb.attribute("playBeep");
            if (attribute != null) {
                final String value = attribute.value();
                if (value != null && !value.isEmpty()) {
                    playBeep = Boolean.parseBoolean(value);
                }
            }
            int maxLength = 3600;
            attribute = verb.attribute("maxLength");
            if (attribute != null) {
                final String value = attribute.value();
                if (value != null && !value.isEmpty()) {
                    try {
                        maxLength = Integer.parseInt(value);
                    } catch (final NumberFormatException exception) {
                        final Notification notification = notification(WARNING_NOTIFICATION, 13612, maxLength
                                + " is not a valid maxLength value");
                        notifications.addNotification(notification);
                    }
                }
            }
            int timeout = 5;
            attribute = verb.attribute(GatherAttributes.ATTRIBUTE_TIME_OUT);
            if (attribute != null) {
                final String value = attribute.value();
                if (value != null && !value.isEmpty()) {
                    try {
                        timeout = Integer.parseInt(value);
                    } catch (final NumberFormatException exception) {
                        final Notification notification = notification(WARNING_NOTIFICATION, 13612, timeout
                                + " is not a valid timeout value");
                        notifications.addNotification(notification);
                    }
                }
            }
            recordingMediaType = MediaAttributes.MediaType.AUDIO_ONLY;
            attribute = verb.attribute("media");
            if (attribute != null) {
                final String value = attribute.value();
                if (value != null && !value.isEmpty()) {
                    try {
                        recordingMediaType = MediaAttributes.MediaType.getValueOf(value);
                    } catch (final IllegalArgumentException exception) {
                        final Notification notification = notification(WARNING_NOTIFICATION, 13612, value
                                + " is not a valid media value");
                        notifications.addNotification(notification);
                    }
                }
            }
            // Start recording.
            recordingSid = Sid.generate(Sid.Type.RECORDING);
            String path = configuration.subset("runtime-settings").getString("recordings-path");
            String httpRecordingUri = configuration.subset("runtime-settings").getString("recordings-uri");
            if (!path.endsWith("/")) {
                path += "/";
            }
            if (!httpRecordingUri.endsWith("/")) {
                httpRecordingUri += "/";
            }
            String fileExtension = recordingMediaType.equals(MediaAttributes.MediaType.AUDIO_ONLY) ? ".wav" : ".mp4";
            path += recordingSid.toString() + fileExtension;
            httpRecordingUri += recordingSid.toString() + fileExtension;
            recordingUri = URI.create(path);
            try {
                publicRecordingUri = uriUtils.resolve(new URI(httpRecordingUri));
            } catch (URISyntaxException e) {
                logger.error("URISyntaxException when trying to resolve Recording URI: "+e);
            }
            Record record = null;
            if (playBeep) {
                final List<URI> prompts = new ArrayList<URI>(1);
                path = configuration.subset("runtime-settings").getString("prompts-uri");
                if (!path.endsWith("/")) {
                    path += "/";
                }
                path += "beep.wav";
                try {
                    prompts.add(uriUtils.resolve(new URI(path)));
                } catch (final Exception exception) {
                    final Notification notification = notification(ERROR_NOTIFICATION, 12400, exception.getMessage());
                    notifications.addNotification(notification);
                    sendMail(notification);
                    final StopInterpreter stop = new StopInterpreter();
                    source.tell(stop, source);
                    return;
                }
                record = new Record(recordingUri, prompts, timeout, maxLength, finishOnKey, recordingMediaType);
            } else {
                record = new Record(recordingUri, timeout, maxLength, finishOnKey, recordingMediaType);
            }

            call.tell(record, source);
        }
    }

    final class FinishRecording extends AbstractAction {
        public FinishRecording(final ActorRef source) {
            super(source);
        }

        @SuppressWarnings("unchecked")
        @Override
        public void execute(final Object message) throws Exception {
            if (logger.isInfoEnabled()) {
                logger.info("##### At FinishRecording, message: " + message.getClass());
            }
            boolean amazonS3Enabled = configuration.subset("amazon-s3").getBoolean("enabled");

            final Class<?> klass = message.getClass();
            if (CallStateChanged.class.equals(klass)) {
                final CallStateChanged event = (CallStateChanged) message;
                // Update the interpreter state.
                callState = event.state();
                // Update the storage.
                callRecord = callRecord.setStatus(callState.toString());
                final DateTime end = DateTime.now();
                callRecord = callRecord.setEndTime(end);
                recordingDuration = (int) (end.getMillis() - callRecord.getStartTime().getMillis()) / 1000;
                callRecord = callRecord.setDuration(recordingDuration);
                final CallDetailRecordsDao records = storage.getCallDetailRecordsDao();
                records.updateCallDetailRecord(callRecord);
                // Update the application.
//                callback();
            }

            Recording recording = null;

            final NotificationsDao notifications = storage.getNotificationsDao();
            // Create a record of the recording.
            Double duration = WavUtils.getAudioDuration(recordingUri);
            if (duration.equals(0.0)) {
                if (logger.isInfoEnabled()) {
                    String msg = String.format("Recording file %s, duration is 0 and will return",recordingUri);
                    logger.info(msg);
                }
            } else {
                if (logger.isDebugEnabled()) {
                    logger.debug("Recording File exists, length: " + (new File(recordingUri).length()));
                    logger.debug("Recording duration: " + duration);
                }

                final Recording.Builder builder = Recording.builder();
                builder.setSid(recordingSid);
                builder.setAccountSid(accountId);
                builder.setCallSid(callInfo.sid());
                builder.setDuration(duration);
                builder.setApiVersion(version);

                StringBuilder buffer = new StringBuilder();
                buffer.append("/").append(version).append("/Accounts/").append(accountId.toString());
                buffer.append("/Recordings/").append(recordingSid.toString());
                builder.setUri(URI.create(buffer.toString()));

                builder.setFileUri(RestcommConnectServiceProvider.getInstance().recordingService()
                        .prepareFileUrl(version, accountId.toString(), recordingSid.toString(), MediaAttributes.MediaType.AUDIO_ONLY));

                URI s3Uri = RestcommConnectServiceProvider.getInstance().recordingService().storeRecording(recordingSid, MediaAttributes.MediaType.AUDIO_ONLY);
                if (s3Uri != null) {
                    builder.setS3Uri(s3Uri);
                }

                recording = builder.build();

                final RecordingsDao recordings = storage.getRecordingsDao();
                recordings.addRecording(recording);
                getContext().system().eventStream().publish(recording);

                Attribute attribute = null;

                if (checkAsrService()) {
                    // ASR service is enabled. Start transcription.
                    URI transcribeCallback = null;
                    attribute = verb.attribute("transcribeCallback");
                    if (attribute != null) {
                        final String value = attribute.value();
                        if (value != null && !value.isEmpty()) {
                            try {
                                transcribeCallback = URI.create(value);
                            } catch (final Exception exception) {
                                final Notification notification = notification(ERROR_NOTIFICATION, 11100, transcribeCallback
                                        + " is an invalid URI.");
                                notifications.addNotification(notification);
                                sendMail(notification);
                                final StopInterpreter stop = new StopInterpreter();
                                source.tell(stop, source);
                                return;
                            }
                        }
                    }
                    boolean transcribe = false;
                    if (transcribeCallback != null) {
                        transcribe = true;
                    } else {
                        attribute = verb.attribute("transcribe");
                        if (attribute != null) {
                            final String value = attribute.value();
                            if (value != null && !value.isEmpty()) {
                                transcribe = Boolean.parseBoolean(value);
                            }
                        }
                    }
                    if (transcribe && checkAsrService()) {
                        final Sid sid = Sid.generate(Sid.Type.TRANSCRIPTION);
                        final Transcription.Builder otherBuilder = Transcription.builder();
                        otherBuilder.setSid(sid);
                        otherBuilder.setAccountSid(accountId);
                        otherBuilder.setStatus(Transcription.Status.IN_PROGRESS);
                        otherBuilder.setRecordingSid(recordingSid);
                        otherBuilder.setTranscriptionText("Transcription Text not available");
                        otherBuilder.setDuration(duration);
                        otherBuilder.setPrice(new BigDecimal("0.00"));
                        buffer = new StringBuilder();
                        buffer.append("/").append(version).append("/Accounts/").append(accountId.toString());
                        buffer.append("/Transcriptions/").append(sid.toString());
                        final URI uri = URI.create(buffer.toString());
                        otherBuilder.setUri(uri);
                        final Transcription transcription = otherBuilder.build();
                        final TranscriptionsDao transcriptions = storage.getTranscriptionsDao();
                        transcriptions.addTranscription(transcription);
                        try {
                            final Map<String, Object> attributes = new HashMap<String, Object>();
                            attributes.put("callback", transcribeCallback);
                            attributes.put("transcription", transcription);
                            getAsrService().tell(new AsrRequest(new File(recordingUri), "en", attributes), source);
                            outstandingAsrRequests++;
                        } catch (final Exception exception) {
                            logger.error(exception.getMessage(), exception);
                        }
                    }
                } else {
                    if (logger.isDebugEnabled()) {
                        logger.debug("AsrService is not enabled");
                    }
                }
            }

            // If action is present redirect to the action URI.
            String action = null;
            Attribute attribute = verb.attribute(GatherAttributes.ATTRIBUTE_ACTION);
            if (attribute != null) {
                action = attribute.value();
                if (action != null && !action.isEmpty()) {
                    URI target = null;
                    try {
                        target = URI.create(action);
                    } catch (final Exception exception) {
                        final Notification notification = notification(ERROR_NOTIFICATION, 11100, action
                                + " is an invalid URI.");
                        notifications.addNotification(notification);
                        sendMail(notification);
                        final StopInterpreter stop = new StopInterpreter();
                        source.tell(stop, source);
                        return;
                    }
                    final URI base = request.getUri();
                    final URI uri = uriUtils.resolveWithBase(base, target);
                    // Parse "method".
                    String method = "POST";
                    attribute = verb.attribute(GatherAttributes.ATTRIBUTE_METHOD);
                    if (attribute != null) {
                        method = attribute.value();
                        if (method != null && !method.isEmpty()) {
                            if (!"GET".equalsIgnoreCase(method) && !"POST".equalsIgnoreCase(method)) {
                                final Notification notification = notification(WARNING_NOTIFICATION, 13610, method
                                        + " is not a valid HTTP method for <Record>");
                                notifications.addNotification(notification);
                                method = "POST";
                            }
                        } else {
                            method = "POST";
                        }
                    }
                    final List<NameValuePair> parameters = parameters();
                    if (recording != null) {
                        if (amazonS3Enabled) {
                            //If Amazon S3 is enabled the Recordings DAO uploaded the wav file to S3 and changed the URI
                            parameters.add(new BasicNameValuePair("RecordingUrl", recording.getFileUri().toURL().toString()));
                            parameters.add(new BasicNameValuePair("PublicRecordingUrl", recording.getFileUri().toURL().toString()));
                        } else {
                            // Redirect to the action url.
                            String httpRecordingUri = configuration.subset("runtime-settings").getString("recordings-uri");
                            if (!httpRecordingUri.endsWith("/")) {
                                httpRecordingUri += "/";
                            }
                            String fileExtension = recordingMediaType.equals(MediaAttributes.MediaType.AUDIO_ONLY) ? ".wav" : ".mp4";
                            httpRecordingUri += recordingSid.toString() + fileExtension;
                            URI publicRecordingUri = uriUtils.resolve(new URI(httpRecordingUri));
                            parameters.add(new BasicNameValuePair("RecordingUrl", recordingUri.toString()));
                            parameters.add(new BasicNameValuePair("PublicRecordingUrl", publicRecordingUri.toString()));
                        }
                    }
                    parameters.add(new BasicNameValuePair("RecordingDuration", Double.toString(duration)));
                    if (MediaGroupResponse.class.equals(klass)) {
                        final MediaGroupResponse response = (MediaGroupResponse) message;
                        Object data = response.get();
                        if (data instanceof CollectedResult) {
                            parameters.add(new BasicNameValuePair("Digits", ((CollectedResult)data).getResult()));
                        } else if(data instanceof String) {
                            parameters.add(new BasicNameValuePair("Digits", (String)data));
                        } else {
                            logger.error("unidentified response recived in MediaGroupResponse: "+response);
                        }
                        request = new HttpRequestDescriptor(uri, method, parameters);
                        if (logger.isInfoEnabled()){
                            logger.info("About to execute Record action to: "+uri);
                        }
                        downloader.tell(request, self());
                    } else if (CallStateChanged.class.equals(klass)) {
                        parameters.add(new BasicNameValuePair("Digits", "hangup"));
                        request = new HttpRequestDescriptor(uri, method, parameters);
                        if (logger.isInfoEnabled()) {
                            logger.info("About to execute Record action to: "+uri);
                        }
                        downloader.tell(request, self());
                    }
//                    final StopInterpreter stop = new StopInterpreter();
//                    source.tell(stop, source);
                }
            } else {
                //Action is null here
                final GetNextVerb next = new GetNextVerb();
                parser.tell(next, source);
            }
            // A little clean up.
            recordingSid = null;
            recordingUri = null;
            recordingMediaType = null;
        }
    }

    final class CreatingSmsSession extends AbstractAction {
        public CreatingSmsSession(final ActorRef source) {
            super(source);
        }

        @Override
        public void execute(Object message) throws Exception {
            // Save <Sms> verb.
            final Class<?> klass = message.getClass();
            if (Tag.class.equals(klass)) {
                verb = (Tag) message;
            }
            // Create a new sms session to handle the <Sms> verb.
            smsService.tell(new CreateSmsSession(callInfo.from(), callInfo.to(), accountId.toString(), false), source);
        }
    }

    final class SendingSms extends AbstractAction {
        public SendingSms(final ActorRef source) {
            super(source);
        }

        @SuppressWarnings("unchecked")
        @Override
        public void execute(final Object message) throws Exception {
            final SmsServiceResponse<ActorRef> response = (SmsServiceResponse<ActorRef>) message;
            final ActorRef session = response.get();
            final NotificationsDao notifications = storage.getNotificationsDao();
            // Parse "from".
            String from = callInfo.to();
            Attribute attribute = verb.attribute("from");
            if (attribute != null) {
                from = attribute.value();
                if (from != null && !from.isEmpty()) {
                    from = e164(from);
                    if (from == null) {
                        from = verb.attribute("from").value();
                        final Notification notification = notification(ERROR_NOTIFICATION, 14102, from
                                + " is an invalid 'from' phone number.");
                        notifications.addNotification(notification);
                        sendMail(notification);
                        smsService.tell(new DestroySmsSession(session), source);
                        final StopInterpreter stop = new StopInterpreter();
                        source.tell(stop, source);
                        return;
                    }
                }
            }
            // Parse "to".
            String to = callInfo.from();
            attribute = verb.attribute("to");
            if (attribute != null) {
                to = attribute.value();
                if (to != null && !to.isEmpty()) {
                    to = e164(to);
                    if (to == null) {
                        to = verb.attribute("to").value();
                        final Notification notification = notification(ERROR_NOTIFICATION, 14101, to
                                + " is an invalid 'to' phone number.");
                        notifications.addNotification(notification);
                        sendMail(notification);
                        smsService.tell(new DestroySmsSession(session), source);
                        final StopInterpreter stop = new StopInterpreter();
                        source.tell(stop, source);
                        return;
                    }
                }
            }
            // Parse <Sms> text.
            String body = verb.text();
            if (body == null || body.isEmpty()) {
                final Notification notification = notification(ERROR_NOTIFICATION, 14103, body + " is an invalid SMS body.");
                notifications.addNotification(notification);
                sendMail(notification);
                smsService.tell(new DestroySmsSession(session), source);
                final StopInterpreter stop = new StopInterpreter();
                source.tell(stop, source);
                return;
            } else {
                // Start observing events from the sms session.
                session.tell(new Observe(source), source);
                // Create an SMS detail record.
                final Sid sid = Sid.generate(Sid.Type.SMS_MESSAGE);
                final SmsMessage.Builder builder = SmsMessage.builder();
                builder.setSid(sid);
                builder.setAccountSid(accountId);
                builder.setApiVersion(version);
                builder.setRecipient(to);
                builder.setSender(from);
                builder.setBody(body);
                builder.setDirection(Direction.OUTBOUND_REPLY);
                builder.setStatus(Status.SENDING);
                builder.setPrice(new BigDecimal("0.00"));
                final StringBuilder buffer = new StringBuilder();
                buffer.append("/").append(version).append("/Accounts/");
                buffer.append(accountId.toString()).append("/SMS/Messages/");
                buffer.append(sid.toString());
                final URI uri = URI.create(buffer.toString());
                builder.setUri(uri);
                SmsVerb.populateAttributes(verb, builder);
                final SmsMessage record = builder.build();
                final SmsMessagesDao messages = storage.getSmsMessagesDao();
                messages.addSmsMessage(record);
                // Store the sms record in the sms session.
                session.tell(new SmsSessionAttribute("record", record), source);
                // Send the SMS.
                final SmsSessionRequest sms = new SmsSessionRequest(from, to, body, null);
                session.tell(sms, source);
                smsSessions.put(sid, session);
            }
            // Parses "action".
            attribute = verb.attribute("action");
            if (attribute != null) {
                String action = attribute.value();
                if (action != null && !action.isEmpty()) {
                    URI target = null;
                    try {
                        target = URI.create(action);
                    } catch (final Exception exception) {
                        final Notification notification = notification(ERROR_NOTIFICATION, 11100, action
                                + " is an invalid URI.");
                        notifications.addNotification(notification);
                        sendMail(notification);
                        final StopInterpreter stop = new StopInterpreter();
                        source.tell(stop, source);
                        return;
                    }
                    final URI base = request.getUri();
                    final URI uri = uriUtils.resolveWithBase(base, target);
                    // Parse "method".
                    String method = "POST";
                    attribute = verb.attribute("method");
                    if (attribute != null) {
                        method = attribute.value();
                        if (method != null && !method.isEmpty()) {
                            if (!"GET".equalsIgnoreCase(method) && !"POST".equalsIgnoreCase(method)) {
                                final Notification notification = notification(WARNING_NOTIFICATION, 14104, method
                                        + " is not a valid HTTP method for <Sms>");
                                notifications.addNotification(notification);
                                method = "POST";
                            }
                        } else {
                            method = "POST";
                        }
                    }
                    // Redirect to the action url.
                    final List<NameValuePair> parameters = parameters();
                    final String status = Status.SENDING.toString();
                    parameters.add(new BasicNameValuePair("SmsStatus", status));
                    request = new HttpRequestDescriptor(uri, method, parameters);
                    downloader.tell(request, source);
                    return;
                }
            }
            // Ask the parser for the next action to take.
            final GetNextVerb next = new GetNextVerb();
            parser.tell(next, source);
        }
    }


}
