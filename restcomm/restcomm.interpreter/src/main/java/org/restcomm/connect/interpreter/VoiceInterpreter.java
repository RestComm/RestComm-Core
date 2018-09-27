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
import akka.actor.ReceiveTimeout;
import akka.actor.UntypedActorContext;
import akka.actor.UntypedActorFactory;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.pattern.AskTimeoutException;
import akka.util.Timeout;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.message.BasicNameValuePair;
import org.joda.time.DateTime;
import org.joda.time.Interval;
import org.restcomm.connect.asr.AsrResponse;
import org.restcomm.connect.commons.cache.DiskCacheResponse;
import org.restcomm.connect.commons.dao.Sid;
import org.restcomm.connect.commons.fsm.Action;
import org.restcomm.connect.commons.fsm.FiniteStateMachine;
import org.restcomm.connect.commons.fsm.State;
import org.restcomm.connect.commons.fsm.Transition;
import org.restcomm.connect.commons.fsm.TransitionFailedException;
import org.restcomm.connect.commons.fsm.TransitionNotFoundException;
import org.restcomm.connect.commons.fsm.TransitionRollbackException;
import org.restcomm.connect.commons.patterns.Observe;
import org.restcomm.connect.commons.patterns.StopObserving;
import org.restcomm.connect.commons.telephony.CreateCallType;
import org.restcomm.connect.core.service.RestcommConnectServiceProvider;
import org.restcomm.connect.core.service.util.UriUtils;
import org.restcomm.connect.dao.CallDetailRecordsDao;
import org.restcomm.connect.dao.NotificationsDao;
import org.restcomm.connect.dao.entities.CallDetailRecord;
import org.restcomm.connect.dao.entities.MediaAttributes;
import org.restcomm.connect.dao.entities.Notification;
import org.restcomm.connect.email.api.EmailResponse;
import org.restcomm.connect.extension.api.ExtensionResponse;
import org.restcomm.connect.extension.api.IExtensionFeatureAccessRequest;
import org.restcomm.connect.extension.controller.ExtensionController;
import org.restcomm.connect.fax.FaxResponse;
import org.restcomm.connect.http.client.DownloaderResponse;
import org.restcomm.connect.http.client.HttpRequestDescriptor;
import org.restcomm.connect.interpreter.rcml.Attribute;
import org.restcomm.connect.interpreter.rcml.End;
import org.restcomm.connect.interpreter.rcml.GetNextVerb;
import org.restcomm.connect.interpreter.rcml.Nouns;
import org.restcomm.connect.interpreter.rcml.ParserFailed;
import org.restcomm.connect.interpreter.rcml.Tag;
import org.restcomm.connect.interpreter.rcml.Verbs;
import org.restcomm.connect.interpreter.rcml.domain.GatherAttributes;
import org.restcomm.connect.mscontrol.api.messages.CollectedResult;
import org.restcomm.connect.mscontrol.api.messages.JoinComplete;
import org.restcomm.connect.mscontrol.api.messages.Left;
import org.restcomm.connect.mscontrol.api.messages.MediaGroupResponse;
import org.restcomm.connect.mscontrol.api.messages.Mute;
import org.restcomm.connect.mscontrol.api.messages.Play;
import org.restcomm.connect.mscontrol.api.messages.StartRecording;
import org.restcomm.connect.mscontrol.api.messages.StopMediaGroup;
import org.restcomm.connect.mscontrol.api.messages.Unmute;
import org.restcomm.connect.sms.api.SmsServiceResponse;
import org.restcomm.connect.sms.api.SmsSessionResponse;
import org.restcomm.connect.telephony.api.AddParticipant;
import org.restcomm.connect.telephony.api.Answer;
import org.restcomm.connect.telephony.api.BridgeManagerResponse;
import org.restcomm.connect.telephony.api.BridgeStateChanged;
import org.restcomm.connect.telephony.api.CallFail;
import org.restcomm.connect.telephony.api.CallHoldStateChange;
import org.restcomm.connect.telephony.api.CallInfo;
import org.restcomm.connect.telephony.api.CallManagerResponse;
import org.restcomm.connect.telephony.api.CallResponse;
import org.restcomm.connect.telephony.api.CallStateChanged;
import org.restcomm.connect.telephony.api.Cancel;
import org.restcomm.connect.telephony.api.ConferenceCenterResponse;
import org.restcomm.connect.telephony.api.ConferenceInfo;
import org.restcomm.connect.telephony.api.ConferenceModeratorPresent;
import org.restcomm.connect.telephony.api.ConferenceResponse;
import org.restcomm.connect.telephony.api.ConferenceStateChanged;
import org.restcomm.connect.telephony.api.CreateBridge;
import org.restcomm.connect.telephony.api.CreateCall;
import org.restcomm.connect.telephony.api.CreateConference;
import org.restcomm.connect.telephony.api.DestroyCall;
import org.restcomm.connect.telephony.api.DestroyConference;
import org.restcomm.connect.telephony.api.Dial;
import org.restcomm.connect.telephony.api.FeatureAccessRequest;
import org.restcomm.connect.telephony.api.GetCallInfo;
import org.restcomm.connect.telephony.api.GetConferenceInfo;
import org.restcomm.connect.telephony.api.GetRelatedCall;
import org.restcomm.connect.telephony.api.Hangup;
import org.restcomm.connect.telephony.api.JoinCalls;
import org.restcomm.connect.telephony.api.Reject;
import org.restcomm.connect.telephony.api.RemoveParticipant;
import org.restcomm.connect.telephony.api.StartBridge;
import org.restcomm.connect.telephony.api.StopBridge;
import org.restcomm.connect.telephony.api.StopConference;
import org.restcomm.connect.telephony.api.StopWaiting;
import org.restcomm.connect.tts.api.SpeechSynthesizerResponse;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;

import javax.servlet.sip.SipServletMessage;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;
import javax.servlet.sip.SipSession;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Currency;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static akka.pattern.Patterns.ask;

/**
 * @author thomas.quintana@telestax.com (Thomas Quintana)
 * @author jean.deruelle@telestax.com
 * @author gvagenas@telestax.com
 * @author amit.bhayani@telestax.com (Amit Bhayani)
 * @author pavel.slegr@telestax.com
 * @author maria.farooq@telestax.com
 */
public class VoiceInterpreter extends BaseVoiceInterpreter {
    // Logger.
    private final LoggingAdapter logger = Logging.getLogger(getContext().system(), this);

    // States for the FSM.
    private final State startDialing;
    private final State processingDialChildren;
    private final State acquiringOutboundCallInfo;
    private final State forking;
    // private final State joiningCalls;
    private final State creatingBridge;
    private final State initializingBridge;
    private final State bridging;
    private final State bridged;
    private final State finishDialing;
    private final State acquiringConferenceInfo;
    private final State joiningConference;
    private final State conferencing;
    private final State finishConferencing;
    private final State downloadingRcml;
    private final State downloadingFallbackRcml;
    private final State initializingCall;
    // private final State initializedCall;
    private final State ready;
    private final State notFound;
    private final State rejecting;
    private final State finished;

    // FSM.
    // The conference Ceneter.
    private final ActorRef conferenceCenter;

    // State for outbound calls.
    private boolean isForking;
    private List<ActorRef> dialBranches;
    private List<Tag> dialChildren;
    private Map<ActorRef, Tag> dialChildrenWithAttributes;

    // The conferencing stuff
    private int maxParticipantLimit = 40;
    private ActorRef conference;
    private Sid conferenceSid;
    private ConferenceInfo conferenceInfo;
    private ConferenceStateChanged.State conferenceState;
    private boolean muteCall;
    private boolean startConferenceOnEnter = true;
    private boolean endConferenceOnExit = false;
    private boolean confModeratorPresent = false;
    private ActorRef confSubVoiceInterpreter;
    private Attribute dialRecordAttribute;
    private boolean dialActionExecuted = false;
    private ActorRef sender;
    private boolean liveCallModification = false;
    private boolean recordingCall = true;
    protected boolean isParserFailed = false;
    protected boolean playWaitUrlPending = false;
    Tag conferenceVerb;
    List<URI> conferenceWaitUris;
    private boolean playMusicForConference = false;
    private MediaAttributes mediaAttributes;
    //Used for system apps, such as when WebRTC client is dialing out.
    //The rcml will be used instead of download the RCML
    private String rcml;

    // Call bridging
    private final ActorRef bridgeManager;
    private ActorRef bridge;
    private boolean beep;

    private boolean enable200OkDelay;

    // IMS authentication
    private boolean asImsUa;
    private String imsUaLogin;
    private String imsUaPassword;
    private String forwardedFrom;
    private Attribute action;

    private String conferenceNameWithAccountAndFriendlyName;
    private Sid callSid;

    // Controls if VI will wait MS response to move to the next verb
    protected boolean msResponsePending;
    private boolean msStopingRingTone = false;

    private boolean callWaitingForAnswer = false;

    private Tag callWaitingForAnswerPendingTag;

    private long timeout;

    private UriUtils uriUtils;

    public VoiceInterpreter(VoiceInterpreterParams params) {
        super();
        final ActorRef source = self();
        downloadingRcml = new State("downloading rcml", new DownloadingRcml(source), null);
        downloadingFallbackRcml = new State("downloading fallback rcml", new DownloadingFallbackRcml(source), null);
        initializingCall = new State("initializing call", new InitializingCall(source), null);
        // initializedCall = new State("initialized call", new InitializedCall(source), new PostInitializedCall(source));
        ready = new State("ready", new Ready(source), null);
        notFound = new State("notFound", new NotFound(source), null);
        rejecting = new State("rejecting", new Rejecting(source), null);
        startDialing = new State("start dialing", new StartDialing(source), null);
        processingDialChildren = new State("processing dial children", new ProcessingDialChildren(source), null);
        acquiringOutboundCallInfo = new State("acquiring outbound call info", new AcquiringOutboundCallInfo(source), null);
        forking = new State("forking", new Forking(source), null);
        // joiningCalls = new State("joining calls", new JoiningCalls(source), null);
        this.creatingBridge = new State("creating bridge", new CreatingBridge(source), null);
        this.initializingBridge = new State("initializing bridge", new InitializingBridge(source), null);
        this.bridging = new State("bridging", new Bridging(source), null);
        bridged = new State("bridged", new Bridged(source), null);
        finishDialing = new State("finish dialing", new FinishDialing(source), null);
        acquiringConferenceInfo = new State("acquiring conference info", new AcquiringConferenceInfo(source), null);
        joiningConference = new State("joining conference", new JoiningConference(source), null);
        conferencing = new State("conferencing", new Conferencing(source), null);
        finishConferencing = new State("finish conferencing", new FinishConferencing(source), null);
        finished = new State("finished", new Finished(source), null);
        /*
         * dialing = new State("dialing", null, null); bridging = new State("bridging", null, null); conferencing = new
         * State("conferencing", null, null);
         */
        transitions.add(new Transition(acquiringAsrInfo, finished));
        transitions.add(new Transition(acquiringSynthesizerInfo, finished));
        transitions.add(new Transition(acquiringCallInfo, initializingCall));
        transitions.add(new Transition(acquiringCallInfo, downloadingRcml));
        transitions.add(new Transition(acquiringCallInfo, finished));
        transitions.add(new Transition(acquiringCallInfo, ready));
        transitions.add(new Transition(initializingCall, downloadingRcml));
        transitions.add(new Transition(initializingCall, ready));
        transitions.add(new Transition(initializingCall, finishDialing));
        transitions.add(new Transition(initializingCall, hangingUp));
        transitions.add(new Transition(initializingCall, finished));
        transitions.add(new Transition(downloadingRcml, ready));
        transitions.add(new Transition(downloadingRcml, notFound));
        transitions.add(new Transition(downloadingRcml, downloadingFallbackRcml));
        transitions.add(new Transition(downloadingRcml, hangingUp));
        transitions.add(new Transition(downloadingRcml, finished));
        transitions.add(new Transition(downloadingFallbackRcml, ready));
        transitions.add(new Transition(downloadingFallbackRcml, hangingUp));
        transitions.add(new Transition(downloadingFallbackRcml, finished));
        transitions.add(new Transition(downloadingFallbackRcml, notFound));
        transitions.add(new Transition(ready, initializingCall));
        transitions.add(new Transition(ready, faxing));
        transitions.add(new Transition(ready, sendingEmail));
        transitions.add(new Transition(ready, pausing));
        transitions.add(new Transition(ready, checkingCache));
        transitions.add(new Transition(ready, caching));
        transitions.add(new Transition(ready, synthesizing));
        transitions.add(new Transition(ready, rejecting));
        transitions.add(new Transition(ready, redirecting));
        transitions.add(new Transition(ready, processingGatherChildren));
        transitions.add(new Transition(ready, creatingRecording));
        transitions.add(new Transition(ready, creatingSmsSession));
        transitions.add(new Transition(ready, startDialing));
        transitions.add(new Transition(ready, hangingUp));
        transitions.add(new Transition(ready, finished));
        transitions.add(new Transition(pausing, ready));
        transitions.add(new Transition(pausing, finished));
        transitions.add(new Transition(rejecting, finished));
        transitions.add(new Transition(faxing, ready));
        transitions.add(new Transition(faxing, finished));
        transitions.add(new Transition(sendingEmail, ready));
        transitions.add(new Transition(sendingEmail, finished));
        transitions.add(new Transition(sendingEmail, finishDialing));
        transitions.add(new Transition(checkingCache, caching));
        transitions.add(new Transition(checkingCache, conferencing));
        transitions.add(new Transition(caching, finished));
        transitions.add(new Transition(caching, conferencing));
        transitions.add(new Transition(caching, finishConferencing));
        transitions.add(new Transition(playing, ready));
        transitions.add(new Transition(playing, finishConferencing));
        transitions.add(new Transition(playing, finished));
        transitions.add(new Transition(synthesizing, finished));
        transitions.add(new Transition(redirecting, ready));
        transitions.add(new Transition(redirecting, finished));
        transitions.add(new Transition(creatingRecording, finished));
        transitions.add(new Transition(finishRecording, ready));
        transitions.add(new Transition(finishRecording, finished));
        transitions.add(new Transition(processingGatherChildren, finished));
        transitions.add(new Transition(gathering, finished));
        transitions.add(new Transition(finishGathering, ready));
        transitions.add(new Transition(finishGathering, finishGathering));
        transitions.add(new Transition(finishGathering, finished));
        transitions.add(new Transition(creatingSmsSession, finished));
        transitions.add(new Transition(sendingSms, ready));
        transitions.add(new Transition(sendingSms, startDialing));
        transitions.add(new Transition(sendingSms, finished));
        transitions.add(new Transition(startDialing, processingDialChildren));
        transitions.add(new Transition(startDialing, acquiringConferenceInfo));
        transitions.add(new Transition(startDialing, faxing));
        transitions.add(new Transition(startDialing, sendingEmail));
        transitions.add(new Transition(startDialing, pausing));
        transitions.add(new Transition(startDialing, checkingCache));
        transitions.add(new Transition(startDialing, caching));
        transitions.add(new Transition(startDialing, synthesizing));
        transitions.add(new Transition(startDialing, redirecting));
        transitions.add(new Transition(startDialing, processingGatherChildren));
        transitions.add(new Transition(startDialing, creatingRecording));
        transitions.add(new Transition(startDialing, creatingSmsSession));
        transitions.add(new Transition(startDialing, startDialing));
        transitions.add(new Transition(startDialing, hangingUp));
        transitions.add(new Transition(startDialing, finished));
        transitions.add(new Transition(processingDialChildren, processingDialChildren));
        transitions.add(new Transition(processingDialChildren, forking));
        transitions.add(new Transition(processingDialChildren, startDialing));
        transitions.add(new Transition(processingDialChildren, checkingCache));
        transitions.add(new Transition(processingDialChildren, sendingEmail));
        transitions.add(new Transition(processingDialChildren, faxing));
        transitions.add(new Transition(processingDialChildren, sendingSms));
        transitions.add(new Transition(processingDialChildren, playing));
        transitions.add(new Transition(processingDialChildren, pausing));
        transitions.add(new Transition(processingDialChildren, ready));
        transitions.add(new Transition(processingDialChildren, hangingUp));
        transitions.add(new Transition(processingDialChildren, finished));
        transitions.add(new Transition(forking, acquiringOutboundCallInfo));
        transitions.add(new Transition(forking, finishDialing));
        transitions.add(new Transition(forking, hangingUp));
        transitions.add(new Transition(forking, finished));
        transitions.add(new Transition(forking, ready));
        transitions.add(new Transition(forking, checkingCache));
        transitions.add(new Transition(forking, caching));
        transitions.add(new Transition(forking, faxing));
        transitions.add(new Transition(forking, sendingEmail));
        transitions.add(new Transition(forking, pausing));
        transitions.add(new Transition(forking, synthesizing));
        transitions.add(new Transition(forking, redirecting));
        transitions.add(new Transition(forking, processingGatherChildren));
        transitions.add(new Transition(forking, creatingRecording));
        transitions.add(new Transition(forking, creatingSmsSession));
        // transitions.add(new Transition(acquiringOutboundCallInfo, joiningCalls));
        transitions.add(new Transition(acquiringOutboundCallInfo, hangingUp));
        transitions.add(new Transition(acquiringOutboundCallInfo, finished));
        transitions.add(new Transition(acquiringOutboundCallInfo, creatingBridge));
        transitions.add(new Transition(creatingBridge, initializingBridge));
        transitions.add(new Transition(creatingBridge, finishDialing));
        transitions.add(new Transition(initializingBridge, bridging));
        transitions.add(new Transition(initializingBridge, hangingUp));
        transitions.add(new Transition(initializingBridge, finished));
        transitions.add(new Transition(bridging, bridged));
        transitions.add(new Transition(bridging, finishDialing));
        transitions.add(new Transition(bridged, finishDialing));
        transitions.add(new Transition(bridged, finished));
        transitions.add(new Transition(finishDialing, ready));
        transitions.add(new Transition(finishDialing, faxing));
        transitions.add(new Transition(finishDialing, sendingEmail));
        transitions.add(new Transition(finishDialing, pausing));
        transitions.add(new Transition(finishDialing, checkingCache));
        transitions.add(new Transition(finishDialing, caching));
        transitions.add(new Transition(finishDialing, synthesizing));
        transitions.add(new Transition(finishDialing, redirecting));
        transitions.add(new Transition(finishDialing, processingGatherChildren));
        transitions.add(new Transition(finishDialing, creatingRecording));
        transitions.add(new Transition(finishDialing, creatingSmsSession));
        transitions.add(new Transition(finishDialing, startDialing));
        transitions.add(new Transition(finishDialing, hangingUp));
        transitions.add(new Transition(finishDialing, finished));
        transitions.add(new Transition(finishDialing, initializingCall));
        transitions.add(new Transition(acquiringConferenceInfo, joiningConference));
        transitions.add(new Transition(acquiringConferenceInfo, hangingUp));
        transitions.add(new Transition(acquiringConferenceInfo, finished));
        transitions.add(new Transition(joiningConference, conferencing));
        transitions.add(new Transition(joiningConference, acquiringConferenceInfo));
        transitions.add(new Transition(joiningConference, hangingUp));
        transitions.add(new Transition(joiningConference, finished));
        transitions.add(new Transition(conferencing, finishConferencing));
        transitions.add(new Transition(conferencing, hangingUp));
        transitions.add(new Transition(conferencing, finished));
        transitions.add(new Transition(conferencing, checkingCache));
        transitions.add(new Transition(conferencing, caching));
        transitions.add(new Transition(conferencing, playing));
        transitions.add(new Transition(conferencing, startDialing));
        transitions.add(new Transition(conferencing, creatingSmsSession));
        transitions.add(new Transition(conferencing, sendingEmail));
        transitions.add(new Transition(finishConferencing, ready));
        transitions.add(new Transition(finishConferencing, faxing));
        transitions.add(new Transition(finishConferencing, sendingEmail));
        transitions.add(new Transition(finishConferencing, pausing));
        transitions.add(new Transition(finishConferencing, checkingCache));
        transitions.add(new Transition(finishConferencing, caching));
        transitions.add(new Transition(finishConferencing, synthesizing));
        transitions.add(new Transition(finishConferencing, redirecting));
        transitions.add(new Transition(finishConferencing, processingGatherChildren));
        transitions.add(new Transition(finishConferencing, creatingRecording));
        transitions.add(new Transition(finishConferencing, creatingSmsSession));
        transitions.add(new Transition(finishConferencing, startDialing));
        transitions.add(new Transition(finishConferencing, hangingUp));
        transitions.add(new Transition(finishConferencing, finished));
        transitions.add(new Transition(hangingUp, finished));
        transitions.add(new Transition(hangingUp, finishConferencing));
        transitions.add(new Transition(hangingUp, finishDialing));
        transitions.add(new Transition(hangingUp, ready));
        transitions.add(new Transition(uninitialized, finished));
        transitions.add(new Transition(notFound, finished));
        // Initialize the FSM.
        this.fsm = new FiniteStateMachine(uninitialized, transitions);
        // Initialize the runtime stuff.
        this.accountId = params.getAccount();
        this.phoneId = params.getPhone();
        this.version = params.getVersion();
        this.url = params.getUrl();
        this.method = params.getMethod();
        this.fallbackUrl = params.getFallbackUrl();
        this.fallbackMethod = params.getFallbackMethod();
        this.viStatusCallback = params.getStatusCallback();
        this.viStatusCallbackMethod = params.getStatusCallbackMethod();
        this.referTarget = params.getReferTarget();
        this.transferor = params.getTransferor();
        this.transferee = params.getTransferee();
        this.emailAddress = params.getEmailAddress();
        this.configuration = params.getConfiguration();
        this.callManager = params.getCallManager();
        this.conferenceCenter = params.getConferenceCenter();
        this.bridgeManager = params.getBridgeManager();
        this.smsService = params.getSmsService();
        this.smsSessions = new HashMap<Sid, ActorRef>();
        this.storage = params.getStorage();
        final Configuration runtime = configuration.subset("runtime-settings");
        playMusicForConference = Boolean.parseBoolean(runtime.getString("play-music-for-conference","false"));
        this.enable200OkDelay = this.configuration.subset("runtime-settings").getBoolean("enable-200-ok-delay",false);
        this.downloader = downloader();
        this.monitoring = params.getMonitoring();
        this.rcml = params.getRcml();
        this.asImsUa = params.isAsImsUa();
        this.imsUaLogin = params.getImsUaLogin();
        this.imsUaPassword = params.getImsUaPassword();
        this.timeout = params.getTimeout();
        this.msResponsePending = false;
        this.mediaAttributes = new MediaAttributes();

        this.uriUtils = RestcommConnectServiceProvider.getInstance().uriUtils();
    }

    public static Props props(final VoiceInterpreterParams params) {
        return new Props(new UntypedActorFactory() {
            @Override
            public Actor create() throws Exception {
                return new VoiceInterpreter(params);
            }
        });
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
            logger.error("URISyntaxException when trying to resolve Error-Dictionary URI: " + e);
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

    @SuppressWarnings("unchecked")
    @Override
    public void onReceive(final Object message) throws Exception {
        final Class<?> klass = message.getClass();
        final State state = fsm.state();
        sender = sender();
        ActorRef self = self();

        if (logger.isInfoEnabled()) {
            logger.info(" ********** VoiceInterpreter's " + self().path() + " Current State: " + state.toString() + "\n"
            + ", Processing Message: " + klass.getName() + " Sender is: "+sender.path());
        }

        if (StartInterpreter.class.equals(klass)) {
            final StartInterpreter request = (StartInterpreter) message;
            call = request.resource();
            fsm.transition(message, acquiringAsrInfo);
        } else if (AsrResponse.class.equals(klass)) {
            onAsrResponse(message);
        } else if (SpeechSynthesizerResponse.class.equals(klass)) {
            onSpeechSynthesizerResponse(message);
        } else if (CallResponse.class.equals(klass)) {
            onCallResponse(message, state);
        } else if (CallStateChanged.class.equals(klass)) {
            onCallStateChanged(message, sender);
        } else if (CallManagerResponse.class.equals(klass)) {
            onCallManagerResponse(message);
        } else if (StartForking.class.equals(klass)) {
            fsm.transition(message, processingDialChildren);
        } else if (ConferenceCenterResponse.class.equals(klass)) {
            onConferenceCenterResponse(message);
        } else if (Fork.class.equals(klass)) {
            onForkMessage(message);
        } else if (ConferenceResponse.class.equals(klass)) {
            onConferenceResponse(message);
        } else if (ConferenceStateChanged.class.equals(klass)) {
            onConferenceStateChanged(message);
        } else if (DownloaderResponse.class.equals(klass)) {
            onDownloaderResponse(message, state);
        } else if (DiskCacheResponse.class.equals(klass)) {
            onDiskCacheResponse(message);
        } else if (ParserFailed.class.equals(klass)) {
            onParserFailed(message);
        } else if (Tag.class.equals(klass)) {
            onTagMessage(message);
        } else if (End.class.equals(klass)) {
            onEndMessage(message);
        } else if (StartGathering.class.equals(klass)) {
            fsm.transition(message, gathering);
        } else if (MediaGroupResponse.class.equals(klass)) {
            onMediaGroupResponse(message);
        } else if (SmsServiceResponse.class.equals(klass)) {
            onSmsServiceResponse(message);
        } else if (SmsSessionResponse.class.equals(klass)) {
            smsResponse(message);
        } else if (FaxResponse.class.equals(klass)) {
            fsm.transition(message, ready);
        } else if (EmailResponse.class.equals(klass)) {
            onEmailResponse(message);
        } else if (StopInterpreter.class.equals(klass)) {
            onStopInterpreter(message);
        } else if (message instanceof ReceiveTimeout) {
            onReceiveTimeout(message);
        } else if (BridgeManagerResponse.class.equals(klass)) {
            onBridgeManagerResponse((BridgeManagerResponse) message, self, sender);
        } else if (BridgeStateChanged.class.equals(klass)) {
            onBridgeStateChanged((BridgeStateChanged) message, self, sender);
        } else if (GetRelatedCall.class.equals(klass)) {
            onGetRelatedCall((GetRelatedCall) message, self, sender);
        } else if (JoinComplete.class.equals(klass)) {
            onJoinComplete((JoinComplete)message);
        } else if (CallHoldStateChange.class.equals(klass)) {
            onCallHoldStateChange((CallHoldStateChange)message, sender);
        }
    }

    private void onJoinComplete(JoinComplete message) throws TransitionNotFoundException, TransitionFailedException, TransitionRollbackException {
        if (logger.isInfoEnabled()) {
            logger.info("JoinComplete received, sender: " + sender().path() + ", VI state: " + fsm.state());
        }
        if (is(joiningConference)) {
            fsm.transition(message, conferencing);
        }
    }

    private void onAsrResponse(Object message) throws TransitionFailedException, TransitionNotFoundException, TransitionRollbackException {
        if (outstandingAsrRequests > 0) {
            asrResponse(message);
        } else {
            fsm.transition(message, acquiringSynthesizerInfo);
        }
    }

    private void onForkMessage(Object message) throws TransitionFailedException, TransitionNotFoundException, TransitionRollbackException {
        if (is(processingDialChildren)) {
            fsm.transition(message, forking);
        }
    }

    private void onConferenceCenterResponse(Object message) throws TransitionFailedException, TransitionNotFoundException, TransitionRollbackException {
        if (is(startDialing) || is(joiningConference)) {
            ConferenceCenterResponse ccReponse = (ConferenceCenterResponse)message;
            if(ccReponse.succeeded()){
                fsm.transition(message, acquiringConferenceInfo);
            }else{
                fsm.transition(message, hangingUp);
            }
        }
    }

    private void onConferenceResponse(Object message) throws TransitionFailedException, TransitionNotFoundException, TransitionRollbackException {
        final ConferenceResponse<ConferenceInfo> response = (ConferenceResponse<ConferenceInfo>) message;
        final Class<?> klass = ((ConferenceResponse)message).get().getClass();
        if (logger.isDebugEnabled()) {
            logger.debug("New ConferenceResponse received with message: "+klass.getName());
        }
        if (Left.class.equals(klass)) {
            Left left = (Left) ((ConferenceResponse)message).get();
            ActorRef leftCall = left.get();
            if (leftCall.equals(call) && conference != null) {
                if(conferenceInfo.globalParticipants() !=0 ){
                    String path = configuration.subset("runtime-settings").getString("prompts-uri");
                    if (!path.endsWith("/")) {
                        path += "/";
                    }
                    String exitAudio = configuration.subset("runtime-settings").getString("conference-exit-audio");
                    path += exitAudio == null || exitAudio.equals("") ? "alert.wav" : exitAudio;
                    URI uri = null;
                    try {
                        uri = uriUtils.resolve(new URI(path), accountId);
                    } catch (final Exception exception) {
                        final Notification notification = notification(ERROR_NOTIFICATION, 12400, exception.getMessage());
                        final NotificationsDao notifications = storage.getNotificationsDao();
                        notifications.addNotification(notification);
                        sendMail(notification);
                        final StopInterpreter stop = new StopInterpreter();
                        self().tell(stop, self());
                        return;
                    }
                    if (logger.isInfoEnabled()) {
                        logger.info("going to play conference-exit-audio beep");
                    }
                    final Play play = new Play(uri, 1);
                    conference.tell(play, self());
                }

                if (endConferenceOnExit) {
                    // Stop the conference if endConferenceOnExit is true
                    final StopConference stop = new StopConference();
                    conference.tell(stop, self());
                }

                Attribute attribute = null;
                if (verb != null) {
                    attribute = verb.attribute(GatherAttributes.ATTRIBUTE_ACTION);
                }

                if (attribute == null) {
                    if (logger.isInfoEnabled()) {
                        logger.info("Attribute is null, will ask for the next verb from parser");
                    }
                    final GetNextVerb next = new GetNextVerb();
                    parser.tell(next, self());
                } else {
                    if (logger.isInfoEnabled()) {
                        logger.info("Dial Action is set, executing Dial Action");
                    }
                    executeDialAction(message, sender);
                }
                conference.tell(new StopObserving(self()), null);
            }
        } else if (ConferenceInfo.class.equals(klass)) {
            conferenceInfo = response.get();
            if (logger.isInfoEnabled()) {
                logger.info("VoiceInterpreter received ConferenceResponse from Conference: " + conferenceInfo.name() + ", path: " + sender().path() + ", current confernce size: " + conferenceInfo.globalParticipants() + ", VI state: " + fsm.state());
            }
            if (is(acquiringConferenceInfo)) {
                fsm.transition(message, joiningConference);
            }
        }
    }

    private void onConferenceStateChanged(Object message) throws TransitionFailedException, TransitionNotFoundException, TransitionRollbackException {
        final ConferenceStateChanged event = (ConferenceStateChanged) message;
        if(logger.isInfoEnabled()) {
            logger.info("onConferenceStateChanged: "+event.state());
        }
        switch (event.state()) {
            case RUNNING_MODERATOR_PRESENT:
                conferenceState = event.state();
                conferenceStateModeratorPresent(message);
                break;
            case COMPLETED:
                conferenceState = event.state();
                //Move to finishConferencing only if we are not in Finished state already
                //There are cases were we have already finished conferencing, for example when there is
                //conference timeout
                if (!is(finished))
                    fsm.transition(message, finishConferencing);
                break;
            case STOPPING:
                conferenceState = event.state();
                if(is(joiningConference)){
                    if(logger.isInfoEnabled()) {
                        logger.info("We tried to join a stopping conference. Will ask Conference Center to create a new conference for us.");
                    }
                    final CreateConference create = new CreateConference(conferenceNameWithAccountAndFriendlyName, callSid);
                    conferenceCenter.tell(create, self());
                }
            default:
                break;
        }

        // !!IMPORTANT!!
        // Do not listen to COMPLETED nor FAILED conference state changes
        // When a conference stops it will ask all its calls to Leave
        // Then the call state will change and the voice interpreter will take proper action then
    }

    private void onParserFailed(Object message) throws TransitionFailedException, TransitionNotFoundException, TransitionRollbackException {
        if(logger.isInfoEnabled()) {
            logger.info("ParserFailed received. Will stop the call");
        }
        isParserFailed = true;
        fsm.transition(message, hangingUp);
    }

    private void onStopInterpreter(Object message) throws TransitionFailedException, TransitionNotFoundException, TransitionRollbackException {
        this.liveCallModification = ((StopInterpreter) message).isLiveCallModification();
        if (logger.isInfoEnabled()) {
            String msg = String.format("Got StopInterpreter, liveCallModification %s, CallState %s", liveCallModification, callState);
            logger.info(msg);
        }
        if (CallStateChanged.State.IN_PROGRESS.equals(callState) && !liveCallModification) {
            fsm.transition(message, hangingUp);
        } else {
            fsm.transition(message, finished);
        }
    }

    private void onReceiveTimeout(Object message) throws TransitionFailedException, TransitionNotFoundException, TransitionRollbackException {
        if (logger.isInfoEnabled()) {
            logger.info("Timeout received");
        }
        if (is(pausing)) {
            fsm.transition(message, ready);
        } else if (is(conferencing)) {
            fsm.transition(message, finishConferencing);
        } else if (is(forking)) {
            fsm.transition(message, finishDialing);
        } else if (is(bridged)) {
            fsm.transition(message, finishDialing);
        } else if (is(bridging)) {
            fsm.transition(message, finishDialing);
        } else if (is(playing) || is(initializingCall)) {
            fsm.transition(message, finished);
        }
    }

    private void onEmailResponse(Object message) throws TransitionFailedException, TransitionNotFoundException, TransitionRollbackException {
        final EmailResponse response = (EmailResponse) message;
        if (!response.succeeded()) {
            logger.error(
                    "There was an error while sending an email :" + response.error(),
                    response.cause());
            return;
        }
        fsm.transition(message, ready);
    }

    private void onSmsServiceResponse(Object message) throws TransitionFailedException, TransitionNotFoundException, TransitionRollbackException {
        final SmsServiceResponse<ActorRef> response = (SmsServiceResponse<ActorRef>) message;
        if (response.succeeded()) {
            if (is(creatingSmsSession)) {
                fsm.transition(message, sendingSms);
            }
        } else {
            fsm.transition(message, hangingUp);
        }
    }

    private void onMediaGroupResponse(Object message) throws TransitionFailedException, TransitionNotFoundException, TransitionRollbackException {
        final MediaGroupResponse response = (MediaGroupResponse) message;
        if(logger.isInfoEnabled()) {
            logger.info("MediaGroupResponse, succeeded: " + response.succeeded() + "  " + response.cause());
        }
        if (response.succeeded()) {
            if (is(playingRejectionPrompt)) {
                fsm.transition(message, hangingUp);
            } else if (is(playing)) {
                logger.info ("Is restcomm stoping ring tone: " + msStopingRingTone);
                // When RestComm is requesting stop ring tone, the first NTFY response is for stop ring tone,
                // instead for play/say verb. When enable200OkDelay = true, there is no NTFY for stop ring tone.
                if (!msStopingRingTone || enable200OkDelay) {
                    fsm.transition(message, ready);
                }
            } else if (is(creatingRecording)) {
                if (logger.isInfoEnabled()) {
                    logger.info("Will move to finishRecording because of MediaGroupResponse");
                }
                fsm.transition(message, finishRecording);
            }
            // This is either MMS collected digits or SIP INFO DTMF. If the DTMF is from SIP INFO, then more DTMF might
            // come later
            else if (is(gathering) || (is(finishGathering) && !super.dtmfReceived)) {
                final MediaGroupResponse dtmfResponse = (MediaGroupResponse) message;
                Object data = dtmfResponse.get();
                if (data instanceof CollectedResult && ((CollectedResult)data).isAsr() && ((CollectedResult)data).isPartial()) {
                    try {
                        //we dont need a new state for this action. The notification
                        //is purely async, and the Interpreter logic will resume
                        //regardless notification result.
                        new PartialGathering(self()).execute(message);
                    } catch (Exception ex) {
                        this.logger.debug("Notifying partial result", ex);
                    }
                } else if (data instanceof CollectedResult && ((CollectedResult)data).isAsr() && !((CollectedResult)data).isPartial() && collectedDigits.length() == 0) {
                    speechResult = ((CollectedResult)data).getResult();
                    fsm.transition(message, finishGathering);
                } else {
                    if (sender == call) {
                        // DTMF using SIP INFO, check if all digits collected here
                        collectedDigits.append(dtmfResponse.get());
                        // Collected digits == requested num of digits the complete the collect digits
                        //Zendesk_34592: if collected digits smaller than numDigits in gather verb
                        // when timeout on gather occur, garthering cannot move to finishGathering
                        // If collected digits have finish on key at the end then complete the collect digits
                        if (collectedDigits.toString().endsWith(finishOnKey)) {
                            dtmfReceived = true;
                            fsm.transition(message, finishGathering);
                        } else {
                            if (numberOfDigits != Short.MAX_VALUE) {
                                if (collectedDigits.length() == numberOfDigits) {
                                    dtmfReceived = true;
                                    fsm.transition(message, finishGathering);
                                } else {
                                    dtmfReceived = false;
                                    return;
                                }
                            } else {
                                dtmfReceived = false;
                                return;
                            }
                        }
                    } else {
                        collectedDigits.append(((CollectedResult)data).getResult());
                        fsm.transition(message, finishGathering);
                    }
                }
            } else if (is(bridging)) {
                // Finally proceed with call bridging
                if (logger.isInfoEnabled()) {
                    String msg = String.format("About to join calls, inbound call %s, outbound call %s", call, outboundCall);
                    logger.info(msg);
                }
                final JoinCalls bridgeCalls = new JoinCalls(call, outboundCall);
                bridge.tell(bridgeCalls, self());
            } else if (msResponsePending) {
                // Move to next verb once media server completed Play
                msResponsePending = false;
                final boolean noBranches = dialBranches == null || dialBranches.size() == 0;
                final boolean activeParser = parser != null;
                final boolean noDialAction = action == null;
                if (noBranches && activeParser && noDialAction) {
                    final GetNextVerb next = new GetNextVerb();
                    parser.tell(next, self());
                }
            }

            if (msStopingRingTone) {
                msStopingRingTone = !(((MediaGroupResponse) message).get() instanceof CollectedResult);
            }
        } else {
            fsm.transition(message, hangingUp);
        }
    }

    private void onEndMessage(Object message) throws TransitionFailedException, TransitionNotFoundException, TransitionRollbackException {
        //Because of RMS issue https://github.com/RestComm/mediaserver/issues/158 we cannot have List<URI> for waitUrl
        if (playWaitUrlPending && conferenceWaitUris != null && conferenceWaitUris.size() > 0) {
            if (logger.isInfoEnabled()) {
                String msg = String.format("End tag received, playWaitUrlPending is %s, conferenceWaitUris.size() %d",playWaitUrlPending, conferenceWaitUris.size());
                logger.info(msg);
            }
            fsm.transition(conferenceWaitUris, conferencing);
            return;
        }
        if (callState.equals(CallStateChanged.State.COMPLETED) || callState.equals(CallStateChanged.State.CANCELED)) {
            if(logger.isInfoEnabled()) {
                String msg = String.format("End tag received, Call state %s , VI state %s will move to finished state",callState, fsm.state());
                logger.info(msg);
            }
            fsm.transition(message, finished);
        } else {
            if (!isParserFailed) {
                if(logger.isInfoEnabled()) {
                    logger.info("End tag received will move to hangup the call, VI state: "+fsm.state());
                }
                fsm.transition(message, hangingUp);
            } else {
                if(logger.isInfoEnabled()) {
                    logger.info("End tag received but parser failed earlier so hangup would have been already sent to the call");
                }
            }
        }
    }

    private void onTagMessage(Object message) throws TransitionFailedException, TransitionNotFoundException, TransitionRollbackException {
        verb = (Tag) message;
        if (logger.isDebugEnabled()) {
            logger.debug("Tag received, name: "+verb.name()+", text: "+verb.text());
        }

        // if 200 ok delay is enabled we need to answer only the calls
        // which are having any further call enabler verbs in their RCML.
        if(enable200OkDelay && callWaitingForAnswer && Verbs.isThisVerbCallEnabler(verb)){
            if(logger.isInfoEnabled()) {
                logger.info("Tag received, but callWaitingForAnswer: will tell call to stop waiting");
            }
            callWaitingForAnswerPendingTag = verb;
            call.tell(new StopWaiting(), self());
        } else {
            if (playWaitUrlPending) {
                if (!(Verbs.play.equals(verb.name()) || Verbs.say.equals(verb.name()))) {
                    if (logger.isInfoEnabled()) {
                        logger.info("Tag for waitUrl is neither Play or Say");
                    }
                    fsm.transition(message, hangingUp);
                }
                if (Verbs.say.equals(verb.name())) {
                    fsm.transition(message, checkingCache);
                } else if (Verbs.play.equals(verb.name())) {
                    fsm.transition(message, caching);
                }
                return;
            }
            if (CallStateChanged.State.RINGING == callState) {
                if (Verbs.reject.equals(verb.name())) {
                    fsm.transition(message, rejecting);
                } else if (Verbs.pause.equals(verb.name())) {
                    fsm.transition(message, pausing);
                } else {
                    fsm.transition(message, initializingCall);
                }
            } else if (Verbs.dial.equals(verb.name()) && callState != CallStateChanged.State.COMPLETED) {
                action = verb.attribute(GatherAttributes.ATTRIBUTE_ACTION);
                if (action != null && dialActionExecuted) {
                    //We have a new Dial verb that contains Dial Action URL again.
                    //We set dialActionExecuted to false in order to execute Dial Action again
                    dialActionExecuted = false;
                }
                dialRecordAttribute = verb.attribute("record");
                fsm.transition(message, startDialing);
            } else if (Verbs.fax.equals(verb.name())) {
                fsm.transition(message, caching);
            } else if (Verbs.play.equals(verb.name()) && callState != CallStateChanged.State.COMPLETED) {
                fsm.transition(message, caching);
            } else if (Verbs.say.equals(verb.name()) && callState != CallStateChanged.State.COMPLETED) {
                // fsm.transition(message, synthesizing);
                fsm.transition(message, checkingCache);
            } else if (Verbs.gather.equals(verb.name()) && callState != CallStateChanged.State.COMPLETED) {
                gatherVerb = verb;
                fsm.transition(message, processingGatherChildren);
            } else if (Verbs.pause.equals(verb.name()) && callState != CallStateChanged.State.COMPLETED) {
                fsm.transition(message, pausing);
            } else if (Verbs.hangup.equals(verb.name()) && callState != CallStateChanged.State.COMPLETED) {
                if (logger.isInfoEnabled()) {
                    String msg = String.format("Next verb is Hangup, current state is %s , callInfo state %s", fsm.state(), callInfo.state());
                    logger.info(msg);
                }
                if (is(finishDialing)) {
                    fsm.transition(message, finished);
                } else {
                    fsm.transition(message, hangingUp);
                }
            } else if (Verbs.redirect.equals(verb.name()) && callState != CallStateChanged.State.COMPLETED) {
                fsm.transition(message, redirecting);
            } else if (Verbs.record.equals(verb.name()) && callState != CallStateChanged.State.COMPLETED) {
                fsm.transition(message, creatingRecording);
            } else if (Verbs.sms.equals(verb.name())) {

                //Check if Outbound SMS is allowed
                ExtensionController ec = ExtensionController.getInstance();
                final IExtensionFeatureAccessRequest far = new FeatureAccessRequest(FeatureAccessRequest.Feature.OUTBOUND_SMS, accountId);
                ExtensionResponse er = ec.executePreOutboundAction(far, this.extensions);

                if (er.isAllowed()) {
                    fsm.transition(message, creatingSmsSession);
                    ec.executePostOutboundAction(far, extensions);
                } else {
                    if (logger.isDebugEnabled()) {
                        final String errMsg = "Outbound SMS is not Allowed";
                        logger.debug(errMsg);
                    }
                    final NotificationsDao notifications = storage.getNotificationsDao();
                    final Notification notification = notification(WARNING_NOTIFICATION, 11001, "Outbound SMS is now allowed");
                    notifications.addNotification(notification);
                    fsm.transition(message, rejecting);
                    ec.executePostOutboundAction(far, extensions);
                    return;
                }
            } else if (Verbs.email.equals(verb.name())) {
                fsm.transition(message, sendingEmail);
            } else {
                invalidVerb(verb);
            }
        }
    }

    private void onDiskCacheResponse(Object message) throws TransitionFailedException, TransitionNotFoundException, TransitionRollbackException {
        final DiskCacheResponse response = (DiskCacheResponse) message;
        if (response.succeeded()) {
            //Because of RMS issue https://github.com/RestComm/mediaserver/issues/158 we cannot have List<URI> for waitUrl
            if (playWaitUrlPending) {
                if (conferenceWaitUris == null)
                    conferenceWaitUris = new ArrayList<URI>();
                URI waitUrl = response.get();
                conferenceWaitUris.add(waitUrl);
                final GetNextVerb next = new GetNextVerb();
                parser.tell(next, self());
                return;
            }
            if (is(caching) || is(checkingCache)) {
                if (Verbs.play.equals(verb.name()) || Verbs.say.equals(verb.name())) {
                    fsm.transition(message, playing);
                } else if (Verbs.fax.equals(verb.name())) {
                    fsm.transition(message, faxing);
                } else if (Verbs.email.equals(verb.name())) {
                    fsm.transition(message, sendingEmail);
                }
            } else if (is(processingGatherChildren)) {
                fsm.transition(message, processingGatherChildren);
            }
        } else {
            if (logger.isDebugEnabled()) {
                logger.debug("DiskCacheResponse is " + response.toString());
            }
            if (is(checkingCache) || is(processingGatherChildren)) {
                fsm.transition(message, synthesizing);
            } else {
                if(response.cause() != null){
                    Notification notification = notification(WARNING_NOTIFICATION, 13233, response.cause().getMessage());
                    final NotificationsDao notifications = storage.getNotificationsDao();
                    notifications.addNotification(notification);
                    sendMail(notification);
                }
                fsm.transition(message, hangingUp);
            }
        }
    }

    private void onCallManagerResponse(Object message) throws TransitionFailedException, TransitionNotFoundException, TransitionRollbackException {
        final CallManagerResponse<Object> response = (CallManagerResponse<Object>) message;
        if (response.succeeded()) {
            if (is(startDialing)) {
                fsm.transition(message, processingDialChildren);
            } else if (is(processingDialChildren)) {
                fsm.transition(message, processingDialChildren);
            }
        } else {
            if (logger.isDebugEnabled()) {
                String msg = String.format("CallManager failed to create Call for %s, current state %s, dialChilder.size %s, dialBranches.size %s", response.getCreateCall().to(), fsm.state().toString(), dialChildren.size(), dialBranches.size());
                logger.debug(msg);
            }
            if (dialChildren != null && dialChildren.size() > 0) {
                fsm.transition(message, processingDialChildren);
            } else {
                fsm.transition(message, hangingUp);
            }
        }
    }

    private void onSpeechSynthesizerResponse(Object message) throws TransitionFailedException, TransitionNotFoundException, TransitionRollbackException {
        if (is(acquiringSynthesizerInfo)) {
            fsm.transition(message, acquiringCallInfo);
        } else if (is(processingGatherChildren) || processingGather) {
            final SpeechSynthesizerResponse<URI> response = (SpeechSynthesizerResponse<URI>) message;
            if (response.succeeded()) {
                fsm.transition(message, processingGatherChildren);
            } else {
                fsm.transition(message, hangingUp);
            }
        } else if (is(synthesizing)) {
            final SpeechSynthesizerResponse<URI> response = (SpeechSynthesizerResponse<URI>) message;
            if (response.succeeded()) {
                fsm.transition(message, caching);
            } else {
                fsm.transition(message, hangingUp);
            }
        }
    }

    private void onCallResponse(Object message, State state) throws TransitionFailedException, TransitionNotFoundException, TransitionRollbackException {
        if (forking.equals(state)) {
            // Allow updating of the callInfo at the VoiceInterpreter so that we can do Dial SIP Screening
            // (https://bitbucket.org/telestax/telscale-restcomm/issue/132/implement-twilio-sip-out) accurately from latest
            // response received
            final CallResponse<CallInfo> response = (CallResponse<CallInfo>) message;
            // Check from whom is the message (initial call or outbound call) and update info accordingly
            if (sender == call) {
                callInfo = response.get();
            } else {
                outboundCall = sender;
                outboundCallInfo = response.get();
            }
        } else if (acquiringCallInfo.equals(state)) {
            final CallResponse<CallInfo> response = (CallResponse<CallInfo>) message;
            // Check from whom is the message (initial call or outbound call) and update info accordingly
            if (sender == call) {
                callInfo = response.get();
                if (callInfo.state() == CallStateChanged.State.CANCELED || (callInfo.invite() != null && callInfo.invite().getSession().getState().equals(SipSession.State.TERMINATED))) {
                    fsm.transition(message, finished);
                    return;
                } else {
                    call.tell(new Observe(self()), self());
                    //Enable Monitoring Service for the call
                    if (monitoring != null)
                        call.tell(new Observe(monitoring), self());
                }
            } else {
                outboundCallInfo = response.get();
            }

            final String direction = callInfo.direction();
            if ("inbound".equals(direction)) {
                if (rcml!=null && !rcml.isEmpty()) {
                    if (logger.isInfoEnabled()) {
                        logger.info("System app is present will proceed to ready state, system app: "+rcml);
                    }
                    createInitialCallRecord((CallResponse<CallInfo>) message);
                    fsm.transition(message, ready);
                } else {
                    fsm.transition(message, downloadingRcml);
                }
            } else {
                fsm.transition(message, initializingCall);
            }
        } else if (acquiringOutboundCallInfo.equals(state)) {
            final CallResponse<CallInfo> response = (CallResponse<CallInfo>) message;
            this.outboundCallInfo = response.get();
            fsm.transition(message, creatingBridge);
        }
    }

    private void onDownloaderResponse(Object message, State state) throws IOException, TransitionFailedException, TransitionNotFoundException, TransitionRollbackException {
        final DownloaderResponse response = (DownloaderResponse) message;
        if (logger.isDebugEnabled()) {
            logger.debug("Download Rcml response succeeded " + response.succeeded());
            if (response.get() != null )
                logger.debug("statusCode " + response.get().getStatusCode());
        }
        if (response.succeeded() && HttpStatus.SC_OK == response.get().getStatusCode()) {
            if (gathering.equals(state)) {
                //no need change state
                return;
            }
            if (conferencing.equals(state)) {
                //This is the downloader response for Conferencing waitUrl
                if (parser != null) {
                    getContext().stop(parser);
                    parser = null;
                }
                final String type = response.get().getContentType();
                if (type != null) {
                    if (type.contains("text/xml") || type.contains("application/xml") || type.contains("text/html")) {
                        parser = parser(response.get().getContentAsString());
                    } else if (type.contains("audio/wav") || type.contains("audio/wave") || type.contains("audio/x-wav")) {
                        parser = parser("<Play>" + request.getUri() + "</Play>");
                    } else if (type.contains("text/plain")) {
                        parser = parser("<Say>" + response.get().getContentAsString() + "</Say>");
                    }
                } else {
                    //If the waitUrl is invalid then move to notFound
                    fsm.transition(message, hangingUp);
                }
                final GetNextVerb next = new GetNextVerb();
                parser.tell(next, self());
                return;
            }
            if (dialBranches == null || dialBranches.size()==0) {
                if(logger.isInfoEnabled()) {
                    logger.info("Downloader response is success, moving to Ready state");
                }
                fsm.transition(message, ready);
            } else {
                return;
            }
        } else if (downloadingRcml.equals(state) && fallbackUrl != null) {
            fsm.transition(message, downloadingFallbackRcml);
        } else if (response.succeeded() && HttpStatus.SC_NOT_FOUND == response.get().getStatusCode()) {
            fsm.transition(message, notFound);
        } else {
            call.tell(new CallFail(response.error()), self());
//                    fsm.transition(message, finished);
        }
    }

    private void onCallStateChanged(Object message, ActorRef sender) throws TransitionFailedException, TransitionNotFoundException, TransitionRollbackException {
        final CallStateChanged event = (CallStateChanged) message;
        if (sender == call)
            callState = event.state();
        else
            if(event.sipResponse()!=null && event.sipResponse()>=400){
                outboundCallResponse = event.sipResponse();
            }
        if(logger.isInfoEnabled()){
            String msg = String.format("VoiceInterpreter received CallStateChanged event: [%s] , from sender path: [%s] , sender is initial call: [%s] , current VI state: [%s] , current call state: [%s] , current outboundCall actor is: [%s]", event, sender.path(),(sender == call), fsm.state(), callState != null ? callState.toString() : "null", outboundCall != null ? outboundCall.path(): "null");
            logger.info(msg);
        }

        switch (event.state()) {
            case QUEUED:
                //Do nothing
                break;
            case RINGING:
                break;
            case CANCELED:
                if (is(creatingBridge) || is(initializingBridge) || is(acquiringOutboundCallInfo) || is(bridging) || is(bridged)) {
                    //This is a canceled branch from a previous forking call. We need to destroy the branch
//                    removeDialBranch(message, sender);
                    callManager.tell(new DestroyCall(sender), self());
                    return;
                } else {
                    if (enable200OkDelay && dialBranches != null && sender.equals(call)) {
                        if (callRecord != null) {
                            final CallDetailRecordsDao records = storage.getCallDetailRecordsDao();
                            callRecord = records.getCallDetailRecord(callRecord.getSid());
                            callRecord = callRecord.setStatus(callState.toString());
                            records.updateCallDetailRecord(callRecord);
                        }
                        fsm.transition(message, finishDialing);
                    } else if (sender == call) {
                        //Move to finished state only if the call actor send the Cancel.
                        fsm.transition(message, finished);
                    } else {
                        //This is a Cancel from a dial branch previously canceled

                        if (dialBranches != null && dialBranches.contains(sender)) {
                            removeDialBranch(message, sender);
                            checkDialBranch(message, sender, action);
                        }
                        else {
                            //case for LCM testTerminateDialForkCallWhileRinging_LCM_to_dial_branches
                            callState = event.state();
                    }
                }
                }
                break;
            case BUSY:
                if (is(forking)) {
                    if (sender == call) {
                        //Move to finishDialing to clear the call and cancel all branches
                        fsm.transition(message, finishDialing);
                    } else {
                        if (dialBranches != null && dialBranches.contains(sender)) {
                            removeDialBranch(message, sender);
                        }
                        if (dialBranches == null || dialBranches.size() == 0){
                            // Stop playing the ringing tone from inbound call
                            stopRingTone();
                        }
                        checkDialBranch(message, sender, action);
                        return;
                    }
                } if (is(initializingCall)) {
                    fsm.transition(message, finished);
                } else {
                    fsm.transition(message, finishDialing);
                    return;
                }
                break;
            case NOT_FOUND:
                //Do nothing
                break;
            case NO_ANSWER:
                //NOANSWER calls should be canceled. At CANCELED event will be removed from
                //dialBranches and will be destroyed.
                if (is(bridging) || (is(bridged) && !sender.equals(call))) {
                    fsm.transition(message, finishDialing);
                } else if (is(forking)){
                    if (!sender.equals(call)) {
                        //One of the dial branches sent NO-ANSWER and we should ask to CANCEL
//                        sender.tell(new Cancel(), self());
                    }
                } else if (is(finishDialing)) {
                    if ((dialBranches == null || dialBranches.size()==0) && sender.equals(call)) {
                        //TODO HERE
                        logger.info("No-Answer event received, and dialBrances is either null or 0 size, sender: "+sender.path()+", vi state: "+fsm.state());
                        checkDialBranch(message, sender, action);
                    }
                }
                if (is(initializingCall)) {
                    sender.tell(new Hangup(), self());
                }
                break;
            case FAILED:
                if (!sender.equals(call)) {
                    if (dialBranches != null && dialBranches.contains(sender)) {
                        dialBranches.remove(sender);
                    }
                    if (dialBranches == null || dialBranches.size() == 0){
                        // Stop playing the ringing tone from inbound call
                        stopRingTone();
                    }
                    checkDialBranch(message,sender,action);
                } else if (sender.equals(call)) {
                    fsm.transition(message, finished);
                }
                break;
            case COMPLETED:
                //NO_ANSWER, COMPLETED and FAILED events are handled the same
                if (is(bridging) || is(bridged)) {
                    if (sender == outboundCall || sender == call) {
                        if(logger.isInfoEnabled()) {
                            String msg = String.format("Received CallStateChanged COMPLETED from call %s, current fsm state %s, will move to finishDialingState ", sender(), fsm.state());
                            logger.info(msg);
                        }
                        fsm.transition(message, finishDialing);
                    } else {
                        if (dialBranches != null && dialBranches.contains(sender)) {
                            removeDialBranch(message, sender);
                        }
                        callManager.tell(new DestroyCall(sender()), self());
                    }
                    return;
                }
                else
                    // changed for https://bitbucket.org/telestax/telscale-restcomm/issue/132/ so that we can do Dial SIP Screening
                    if (is(forking) && ((dialBranches != null && dialBranches.contains(sender)) || outboundCall == null)) {
                        if (!sender.equals(call)) {
                            removeDialBranch(message, sender);
                            //Properly clean up FAILED or BUSY outgoing calls
                            //callManager.tell(new DestroyCall(sender), self());
                            checkDialBranch(message,sender,action);
                            return;
                        } else {
                            fsm.transition(message, finishDialing);
                        }
                    } else if (is(creatingRecording)) {
                        fsm.transition(message, finishRecording);
                    } else if ((is(bridged) || is(forking)) && call == sender()) {
                        if (!dialActionExecuted) {
                            fsm.transition(message, finishDialing);
                        }
                    } else if (is(finishDialing)) {
                        if (sender.equals(call)) {
                            fsm.transition(message, finished);
                        } else {
                            checkDialBranch(message, sender(), action);
                        }
                        break;
                    } else if (is(conferencing) || is(finishConferencing)) {
                        //If the CallStateChanged.Completed event from the Call arrived before the ConferenceStateChange.Completed
                        //event, then return and wait for the FinishConferencing to deal with the event (either execute dial action or
                        //get next verb from parser
                        if (logger.isInfoEnabled()) {
                            logger.info("VoiceInterpreter received CallStateChanged.Completed VI in: " + fsm.state() + " state, will return and wait for ConferenceStateChanged.Completed event");
                        }
                        return;
                    } else {
                        if (!is(finishDialing) && !is(finished))
                            fsm.transition(message, finished);
                    }
                break;
            case WAIT_FOR_ANSWER:
            case IN_PROGRESS:
                if(call!=null && (call == sender) && event.state().equals(CallStateChanged.State.WAIT_FOR_ANSWER)){
                    callWaitingForAnswer  = true;
                }
                if (enable200OkDelay && sender.equals(call) && event.state().equals(CallStateChanged.State.IN_PROGRESS) && callWaitingForAnswerPendingTag != null) {
                    if (logger.isInfoEnabled()) {
                        logger.info("Waiting call is inProgress we can proceed to the pending tag execution");
                    }
                    callWaitingForAnswer = false;
                    onTagMessage(callWaitingForAnswerPendingTag);
                } else if (is(initializingCall) || is(rejecting)) {
                    if (parser != null) {
                        //This is an inbound call
                        fsm.transition(message, ready);
                    } else {
                        //This is a REST API created outgoing call
                        fsm.transition(message, downloadingRcml);
                    }
                } else if (is(forking)) {
                    if (outboundCall == null || !sender.equals(call)) {
                        if (logger.isInfoEnabled()) {
                            String msg = String.format("Got CallStateChanged.InProgress while forking, from outbound call %s, will proceed to cancel other branches", sender());
                            logger.info(msg);
                        }
                        outboundCall = sender;
                        fsm.transition(message, acquiringOutboundCallInfo);
                    }
                } else if (is(conferencing)) {
                    // Call left the conference successfully
                    if (!liveCallModification) {
                        // Hang up the call
                        final Hangup hangup = new Hangup();
                        call.tell(hangup, sender);
                    } else {
                        // XXX start processing new RCML and give instructions to call
                        // Ask the parser for the next action to take.
                        final GetNextVerb next = new GetNextVerb();
                        parser.tell(next, self());
                    }
                }

                // Update the storage for conferencing.
                if (callRecord != null && !is(initializingCall) && !is(rejecting)) {
                    final CallDetailRecordsDao records = storage.getCallDetailRecordsDao();
                    callRecord = records.getCallDetailRecord(callRecord.getSid());
                    callRecord = callRecord.setStatus(callState.toString());
                    records.updateCallDetailRecord(callRecord);
                }
                break;
            }
    }

    private void removeDialBranch(Object message, ActorRef sender) {
        //Just remove the branch from dialBranches and send the CANCEL
        //Later at onCallStateChanged.CANCEL we should ask call manager to destroy call and
        //either execute dial action or ask parser for next verb
        CallStateChanged.State state = null;
        if (message instanceof CallStateChanged) {
            state = ((CallStateChanged)message).state();
        } else if (message instanceof  ReceiveTimeout) {
            state = CallStateChanged.State.NO_ANSWER;
        }
        if(logger.isInfoEnabled()) {
            logger.info("Dial branch new call state: " + state + " call path: " + sender().path() + " VI state: " + fsm.state());
        }
        if (state != null && !state.equals(CallStateChanged.State.CANCELED)) {
            if (logger.isInfoEnabled()) {
                logger.info("At removeDialBranch() will cancel call: "+sender.path()+", isTerminated: "+sender.isTerminated());
            }
            sender.tell(new Cancel(), self());
        }
        if (outboundCall != null && outboundCall.equals(sender)) {
            outboundCall = null;
        }
        if (dialBranches != null && dialBranches.contains(sender))
            dialBranches.remove(sender);
    }

    private void checkDialBranch(Object message, ActorRef sender, Attribute attribute) {
        CallStateChanged.State state = null;
        if (message instanceof CallStateChanged) {
            state = ((CallStateChanged)message).state();
        } else if (message instanceof  ReceiveTimeout) {
            state = CallStateChanged.State.NO_ANSWER;
        }

        if (dialBranches == null || dialBranches.size() == 0) {
            dialBranches = null;
            //https://telestax.atlassian.net/browse/RESTCOMM-1738
            // If Finish Dial verb. RC need to cancel timeout for this verb before moving to next verb.
            context().setReceiveTimeout(Duration.Undefined());

            if (attribute == null) {
                if (logger.isInfoEnabled()) {
                    logger.info("Attribute is null, will destroy call and ask for the next verb from parser");
                }
                if (sender != null && !sender.equals(call)) {
                    callManager.tell(new DestroyCall(sender), self());
                }
                // VI cannot move to next verb if media still being reproduced by media server
                // GetNextVerb is skipped while StopMediaGroup request is sent to media server
                // RCML Parser/VI activity continues when media server successful response is received
                if (parser != null && !msResponsePending) {
                    final GetNextVerb next = new GetNextVerb();
                    parser.tell(next, self());
                }
            } else {
                if (logger.isInfoEnabled()) {
                    logger.info("Executing Dial Action for inbound call");
                }
                if (sender.equals(call)) {
                    executeDialAction(message, outboundCall);
                } else {
                    executeDialAction(message, sender);
                }
                if (sender != null && !sender.equals(call)) {
                    if (logger.isInfoEnabled()) {
                        logger.info("Will destroy sender");
                    }
                    callManager.tell(new DestroyCall(sender), self());
                }
            }
            if (bridge != null) {
                // Stop the bridge
                bridge.tell(new StopBridge(liveCallModification), self());
                recordingCall = false;
                bridge = null;
            }
        } else if (state != null && (state.equals(CallStateChanged.State.BUSY) ||
                state.equals(CallStateChanged.State.CANCELED) ||
                state.equals(CallStateChanged.State.FAILED))) {
            callManager.tell(new DestroyCall(sender), self());
        }
    }

    private void onBridgeManagerResponse(BridgeManagerResponse message, ActorRef self, ActorRef sender) throws Exception {
        if (is(creatingBridge)) {
            this.bridge = message.get();
            fsm.transition(message, initializingBridge);
        }
    }

    private void onBridgeStateChanged(BridgeStateChanged message, ActorRef self, ActorRef sender) throws Exception {
        switch (message.getState()) {
            case READY:
                if (is(initializingBridge)) {
                    fsm.transition(message, bridging);
                }
                break;
            case BRIDGED:
                if (is(bridging)) {
                    fsm.transition(message, bridged);
                }
                break;

            case FAILED:
                if (is(initializingBridge)) {
                    fsm.transition(message, hangingUp);
                }
            default:
                break;
        }
    }

    private void onGetRelatedCall(GetRelatedCall message, ActorRef self, ActorRef sender) {
        final ActorRef callActor = message.call();
        if (is(forking)) {
            sender.tell(dialBranches, self);
            return;
        }
        if (outboundCall != null) {
            if (callActor.equals(outboundCall)) {
                sender.tell(call, self);
            } else if (callActor.equals(call)) {
                sender.tell(outboundCall, self);
            }
        } else {
            // If previously that was a p2p call that changed to conference (for hold)
            // and now it changes again to a new url, the outbound call is null since
            // When we joined the call to the conference, we made outboundCall = null;
            sender.tell(new org.restcomm.connect.telephony.api.NotFound(), sender);
        }
    }

    private void onCallHoldStateChange(CallHoldStateChange message, ActorRef sender){
        if (logger.isInfoEnabled()) {
            logger.info("CallHoldStateChange received, state: " + message.state());
        }
        if (asImsUa){
            if (sender.equals(outboundCall)) {
                call.tell(message, self());
            } else if (sender.equals(call)) {
                outboundCall.tell(message, self());
            }
        }
    }

    private void conferenceStateModeratorPresent(final Object message) {
        if(logger.isInfoEnabled()) {
            logger.info("VoiceInterpreter#conferenceStateModeratorPresent will unmute the call: " + call.path().toString()+", direction: "+callInfo.direction());
        }
        call.tell(new Unmute(), self());

        if (confSubVoiceInterpreter != null) {
        if(logger.isInfoEnabled()) {
            logger.info("VoiceInterpreter stopping confSubVoiceInterpreter");
        }

            // Stop the conference back ground music
            final StopInterpreter stop = new StopInterpreter();
            confSubVoiceInterpreter.tell(stop, self());
        }
    }

    List<NameValuePair> parameters() {
        final List<NameValuePair> parameters = new ArrayList<NameValuePair>();
        final String callSid = callInfo.sid().toString();
        parameters.add(new BasicNameValuePair("CallSid", callSid));
        parameters.add(new BasicNameValuePair("InstanceId", restcommConfiguration.getMain().getInstanceId()));
        if (outboundCallInfo != null) {
            final String outboundCallSid = outboundCallInfo.sid().toString();
            parameters.add(new BasicNameValuePair("OutboundCallSid", outboundCallSid));
        }
        final String accountSid = accountId.toString();
        parameters.add(new BasicNameValuePair("AccountSid", accountSid));
        final String from = e164(callInfo.from());
        parameters.add(new BasicNameValuePair("From", from));
        final String to = e164(callInfo.to());
        parameters.add(new BasicNameValuePair("To", to));
        final String state = callState.toString();
        parameters.add(new BasicNameValuePair("CallStatus", state));
        parameters.add(new BasicNameValuePair("ApiVersion", version));
        final String direction = callInfo.direction();
        parameters.add(new BasicNameValuePair("Direction", direction));
        final String callerName = (callInfo.fromName() == null || callInfo.fromName().isEmpty()) ? "null" : callInfo.fromName();
        parameters.add(new BasicNameValuePair("CallerName", callerName));
        final String forwardedFrom = (callInfo.forwardedFrom() == null || callInfo.forwardedFrom().isEmpty()) ? "null" : callInfo.forwardedFrom();
        parameters.add(new BasicNameValuePair("ForwardedFrom", forwardedFrom));
        parameters.add(new BasicNameValuePair("CallTimestamp", callInfo.dateCreated().toString()));
        if (referTarget != null) {
            parameters.add(new BasicNameValuePair("ReferTarget", referTarget));
        }
        if (transferor != null) {
            parameters.add(new BasicNameValuePair("Transferor", transferor));
        }
        if (transferee != null) {
            parameters.add(new BasicNameValuePair("Transferee", transferee));
        }
        // logger.info("Type " + callInfo.type());
        SipServletResponse lastResponse = callInfo.lastResponse();
        if (CreateCallType.SIP == callInfo.type()) {
            // Adding SIP OUT Headers and SipCallId for
            // https://bitbucket.org/telestax/telscale-restcomm/issue/132/implement-twilio-sip-out
            // logger.info("lastResponse " + lastResponse);
            if (lastResponse != null) {
                final int statusCode = lastResponse.getStatus();
                final String method = lastResponse.getMethod();
                // See https://www.twilio.com/docs/sip/receiving-sip-headers
                // Headers on the final SIP response message (any 4xx or 5xx message or the final BYE/200) are posted to the
                // Dial action URL.
                if ((statusCode >= 400 && "INVITE".equalsIgnoreCase(method))
                        || (statusCode >= 200 && statusCode < 300 && "BYE".equalsIgnoreCase(method))) {
                    final String sipCallId = lastResponse.getCallId();
                    parameters.add(new BasicNameValuePair("DialSipCallId", sipCallId));
                    parameters.add(new BasicNameValuePair("DialSipResponseCode", "" + statusCode));
                    processCustomAndDiversionHeaders(lastResponse, "DialSipHeader_", parameters);
                }
            }
        }

        if (lastResponse == null) {
            // Restcomm VoiceInterpreter should check the INVITE for custom headers and pass them to RVD
            // https://telestax.atlassian.net/browse/RESTCOMM-710
            final SipServletRequest invite = callInfo.invite();
            // For outbound calls created with Calls REST API, the invite at this point will be null
            if (invite != null) {
                processCustomAndDiversionHeaders(invite, "SipHeader_", parameters);

            }
        } else {
            processCustomAndDiversionHeaders(lastResponse, "SipHeader_", parameters);
        }

        return parameters;
    }

    private void processCustomAndDiversionHeaders(SipServletMessage sipMessage, String prefix, List<NameValuePair> parameters) {
        Iterator<String> headerNames = sipMessage.getHeaderNames();
        while (headerNames.hasNext()) {
            String headerName = headerNames.next();
            if (headerName.startsWith("X-")) {
                if (logger.isDebugEnabled()) {
                    logger.debug("%%%%%%%%%%% Identified customer header: " + headerName);
                }
                parameters.add(new BasicNameValuePair(prefix + headerName, sipMessage.getHeader(headerName)));
            } else if (headerName.startsWith("Diversion")) {

                final String sipDiversionHeader = sipMessage.getHeader(headerName);
                if (logger.isDebugEnabled()) {
                    logger.debug("%%%%%%%%%%% Identified diversion header: " + sipDiversionHeader);
                }
                parameters.add(new BasicNameValuePair(prefix + headerName, sipDiversionHeader));

                try {
                    forwardedFrom = sipDiversionHeader.substring(sipDiversionHeader.indexOf("sip:") + 4,
                            sipDiversionHeader.indexOf("@"));

                    for(int i=0; i < parameters.size(); i++) {
                        if (parameters.get(i).getName().equals("ForwardedFrom")) {
                            if (parameters.get(i).getValue().equals("null")) {
                                parameters.remove(i);
                                parameters.add(new BasicNameValuePair("ForwardedFrom", forwardedFrom));
                                break;
                            } else {
                                // Not null, so it's not going to be overwritten with Diversion Header
                                break;
                            }
                        }
                    }
                } catch (Exception e) {
                    logger.warning("Error parsing SIP Diversion header"+ e.getMessage());
                }
            }
        }
    }

    private void stopRingTone() {
        // Stop playing the ringing tone from inbound call
        msResponsePending = true;
        msStopingRingTone = true;
        call.tell(new StopMediaGroup(), self());
    }

    private abstract class AbstractAction implements Action {
        protected final ActorRef source;

        public AbstractAction(final ActorRef source) {
            super();
            this.source = source;
        }

        protected Tag conference(final Tag container) {
            final List<Tag> children = container.children();
            for (final Tag child : children) {
                if (Nouns.conference.equals(child.name())) {
                    return child;
                }
            }
            return null;
        }

        protected Tag video(final Tag container) {
            final List<Tag> children = container.children();
            for (final Tag child : children) {
                if (Nouns.video.equals(child.name())) {
                    return child;
                }
            }
            return null;
        }
    }

    private final class InitializingCall extends AbstractAction {
        public InitializingCall(final ActorRef source) {
            super(source);
        }

        @SuppressWarnings("unchecked")
        @Override
        public void execute(final Object message) throws Exception {
            final Class<?> klass = message.getClass();

            if (CallResponse.class.equals(klass)) {
                // Update the interpreter state.
                final CallResponse<CallInfo> response = (CallResponse<CallInfo>) message;
                callInfo = response.get();
                callState = callInfo.state();
                if (callState.name().equalsIgnoreCase(CallStateChanged.State.IN_PROGRESS.name())) {
                    final CallStateChanged event = new CallStateChanged(CallStateChanged.State.IN_PROGRESS);
                    source.tell(event, source);
                    // fsm.transition(event, acquiringCallMediaGroup);
                    return;
                }

                // Update the storage.
                if (callRecord != null) {
                    callRecord = callRecord.setStatus(callState.toString());
                    final CallDetailRecordsDao records = storage.getCallDetailRecordsDao();
                    records.updateCallDetailRecord(callRecord);
                }

                // Update the application.
                callback();

                // Start dialing.
                call.tell(new Dial(), source);
                // Set the timeout period.
                final UntypedActorContext context = getContext();
                context.setReceiveTimeout(Duration.create(timeout, TimeUnit.SECONDS));
            } else if (Tag.class.equals(klass)) {
                // Update the interpreter state.
                verb = (Tag) message;

                // Answer the call.
                boolean confirmCall = true;
                if (enable200OkDelay && Verbs.dial.equals(verb.name())) {
                    confirmCall=false;
            }
                call.tell(new Answer(callRecord.getSid(),confirmCall), source);
        }
    }
    }

    private final class DownloadingRcml extends AbstractAction {
        public DownloadingRcml(final ActorRef source) {
            super(source);
        }

        @SuppressWarnings("unchecked")
        @Override
        public void execute(final Object message) throws Exception {
            final Class<?> klass = message.getClass();
            if (CallResponse.class.equals(klass)) {
                createInitialCallRecord((CallResponse<CallInfo>) message);
            }
            // Ask the downloader to get us the application that will be executed.
            final List<NameValuePair> parameters = parameters();
            request = new HttpRequestDescriptor(url, method, parameters);
            downloader.tell(request, source);
        }
    }

    private void createInitialCallRecord(CallResponse<CallInfo> message) {
        final CallDetailRecordsDao records = storage.getCallDetailRecordsDao();
        final CallResponse<CallInfo> response = message;
        callInfo = response.get();
        callState = callInfo.state();
        if (callInfo.direction().equals("inbound")) {
            callRecord = records.getCallDetailRecord(callInfo.sid());
            if (callRecord == null) {
                // Create a call detail record for the call.
                final CallDetailRecord.Builder builder = CallDetailRecord.builder();
                builder.setSid(callInfo.sid());
                builder.setInstanceId(restcommConfiguration.getInstance().getMain().getInstanceId());
                builder.setDateCreated(callInfo.dateCreated());
                builder.setAccountSid(accountId);
                builder.setTo(callInfo.to());
                if (callInfo.fromName() != null) {
                    builder.setCallerName(callInfo.fromName());
                } else {
                    builder.setCallerName("Unknown");
                }
                if (callInfo.from() != null) {
                    builder.setFrom(callInfo.from());
                } else {
                    builder.setFrom("Unknown");
                }
                builder.setForwardedFrom(callInfo.forwardedFrom());
                builder.setPhoneNumberSid(phoneId);
                builder.setStatus(callState.toString());
                final DateTime now = DateTime.now();
                builder.setStartTime(now);
                builder.setDirection(callInfo.direction());
                builder.setApiVersion(version);
                builder.setPrice(new BigDecimal("0.00"));
                builder.setMuted(false);
                builder.setOnHold(false);
                // TODO implement currency property to be read from Configuration
                builder.setPriceUnit(Currency.getInstance("USD"));
                final StringBuilder buffer = new StringBuilder();
                buffer.append("/").append(version).append("/Accounts/");
                buffer.append(accountId.toString()).append("/Calls/");
                buffer.append(callInfo.sid().toString());
                final URI uri = URI.create(buffer.toString());
                builder.setUri(uri);

                builder.setCallPath(call.path().toString());

                callRecord = builder.build();
                records.addCallDetailRecord(callRecord);
            }

            // Update the application.
            callback();
        }
    }

    private final class DownloadingFallbackRcml extends AbstractAction {
        public DownloadingFallbackRcml(final ActorRef source) {
            super(source);
        }

        @Override
        public void execute(final Object message) throws Exception {
            final Class<?> klass = message.getClass();
            // Notify the account of the issue.
            if (DownloaderResponse.class.equals(klass)) {
                final DownloaderResponse result = (DownloaderResponse) message;
                final Throwable cause = result.cause();
                Notification notification = null;
                if (cause instanceof ClientProtocolException) {
                    notification = notification(ERROR_NOTIFICATION, 11206, cause.getMessage());
                } else if (cause instanceof IOException) {
                    notification = notification(ERROR_NOTIFICATION, 11205, cause.getMessage());
                } else if (cause instanceof URISyntaxException) {
                    notification = notification(ERROR_NOTIFICATION, 11100, cause.getMessage());
                }
                if (notification != null) {
                    final NotificationsDao notifications = storage.getNotificationsDao();
                    notifications.addNotification(notification);
                    sendMail(notification);
                }
            }
            // Try to use the fall back url and method.
            final List<NameValuePair> parameters = parameters();
            request = new HttpRequestDescriptor(fallbackUrl, fallbackMethod, parameters);
            downloader.tell(request, source);
        }
    }

    private final class Ready extends AbstractAction {
        public Ready(final ActorRef source) {
            super(source);
        }

        @Override
        public void execute(final Object message) throws IOException {
            final UntypedActorContext context = getContext();
            final State state = fsm.state();
            if (initializingCall.equals(state)) {
                // Update the interpreter state.
                final CallStateChanged event = (CallStateChanged) message;
                callState = event.state();

                // Update the application.
                callback();

                // Update the storage.
                if (callRecord != null) {
                    final CallDetailRecordsDao records = storage.getCallDetailRecordsDao();
                    callRecord = records.getCallDetailRecord(callRecord.getSid());
                    callRecord = callRecord.setStatus(callState.toString());
                    callRecord = callRecord.setStartTime(DateTime.now());
                    callRecord = callRecord.setForwardedFrom(forwardedFrom);
                    records.updateCallDetailRecord(callRecord);
                }

                // Handle pending verbs.
                source.tell(verb, source);
                return;
            } else if (downloadingRcml.equals(state) || downloadingFallbackRcml.equals(state) || redirecting.equals(state)
                    || finishGathering.equals(state) || finishRecording.equals(state) || sendingSms.equals(state)
                    || finishDialing.equals(state) || finishConferencing.equals(state) || is(forking)) {
                response = ((DownloaderResponse) message).get();
                if (parser != null) {
                    context.stop(parser);
                    parser = null;
                }
                final String type = response.getContentType();
                if (type != null) {
                        if (type.contains("text/xml") || type.contains("application/xml") || type.contains("text/html")) {
                            parser = parser(response.getContentAsString());
                        } else if (type.contains("audio/wav") || type.contains("audio/wave") || type.contains("audio/x-wav")) {
                            parser = parser("<Play>" + request.getUri() + "</Play>");
                        } else if (type.contains("text/plain")) {
                            parser = parser("<Say>" + response.getContentAsString() + "</Say>");
                        }
                } else {
                    if (call != null) {
                        call.tell(new Hangup(outboundCallResponse), null);
                    }
                    final StopInterpreter stop = new StopInterpreter();
                    source.tell(stop, source);
                    return;
                }
            } else if ((message instanceof CallResponse) && (rcml != null && !rcml.isEmpty())) {
                if (parser != null) {
                    context.stop(parser);
                    parser = null;
                }
                parser = parser(rcml);
            } else if (pausing.equals(state)) {
                context.setReceiveTimeout(Duration.Undefined());
            }
            // Ask the parser for the next action to take.
            final GetNextVerb next = new GetNextVerb();
            if (parser != null) {
                parser.tell(next, source);
            } else if(logger.isInfoEnabled()) {
                logger.info("Parser is null");
            }

        }
    }

    private final class NotFound extends AbstractAction {
        public NotFound(final ActorRef source) {
            super(source);
        }

        @Override
        public void execute(final Object message) throws Exception {
            final DownloaderResponse response = (DownloaderResponse) message;
            if (logger.isDebugEnabled()) {
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

    private final class Rejecting extends AbstractAction {
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

    private abstract class AbstractDialAction extends AbstractAction {
        public AbstractDialAction(final ActorRef source) {
            super(source);
        }

        protected String callerId(final Tag container) {
            // Parse "from".
            String callerId = null;

            // Issue 210: https://telestax.atlassian.net/browse/RESTCOMM-210
            final boolean useInitialFromAsCallerId = configuration.subset("runtime-settings").getBoolean("from-address-to-proxied-calls");

            Attribute attribute = verb.attribute("callerId");
            if (attribute != null) {
                callerId = attribute.value();
                if (callerId != null && !callerId.isEmpty()) {
                    callerId = e164(callerId);
                    if (callerId == null) {
                        callerId = verb.attribute("callerId").value();
                        final NotificationsDao notifications = storage.getNotificationsDao();
                        final Notification notification = notification(ERROR_NOTIFICATION, 13214, callerId
                                + " is an invalid callerId.");
                        notifications.addNotification(notification);
                        sendMail(notification);
                        final StopInterpreter stop = new StopInterpreter();
                        source.tell(stop, source);
                        return null;
                    }
                }
            }

            if (callerId == null && useInitialFromAsCallerId)
                callerId = callInfo.from();

            return callerId;
        }

        protected int timeout(final Tag container) {
            int timeout = 30;
            Attribute attribute = container.attribute("timeout");
            if (attribute != null) {
                final String value = attribute.value();
                if (value != null && !value.isEmpty()) {
                    try {
                        timeout = Integer.parseInt(value);
                    } catch (final NumberFormatException exception) {
                        final NotificationsDao notifications = storage.getNotificationsDao();
                        final Notification notification = notification(WARNING_NOTIFICATION, 13212, value
                                + " is not a valid timeout value for <Dial>");
                        notifications.addNotification(notification);
                    }
                }
            }
            return timeout;
        }

        protected int timeLimit(final Tag container) {
            int timeLimit = 14400;
            Attribute attribute = container.attribute("timeLimit");
            if (attribute != null) {
                final String value = attribute.value();
                if (value != null && !value.isEmpty()) {
                    try {
                        timeLimit = Integer.parseInt(value);
                    } catch (final NumberFormatException exception) {
                        final NotificationsDao notifications = storage.getNotificationsDao();
                        final Notification notification = notification(WARNING_NOTIFICATION, 13216, value
                                + " is not a valid timeLimit value for <Dial>");
                        notifications.addNotification(notification);
                    }
                }
            }
            return timeLimit;
        }

        protected MediaAttributes.MediaType videoEnabled(final Tag container) {
            boolean videoEnabled = false;
            Attribute attribute = container.attribute("enable");
            if (attribute != null) {
                final String value = attribute.value();
                if (value != null && !value.isEmpty()) {
                    videoEnabled = Boolean.valueOf(value);
                }
            }
            if (videoEnabled) {
                return MediaAttributes.MediaType.AUDIO_VIDEO;
            } else {
                return MediaAttributes.MediaType.AUDIO_ONLY;
            }
        }

        protected MediaAttributes.VideoMode videoMode(final Tag container){
            MediaAttributes.VideoMode videoMode = MediaAttributes.VideoMode.MCU;
            Attribute attribute = container.attribute("mode");
            if (attribute != null) {
                final String value = attribute.value();
                if (value != null && !value.isEmpty()) {
                    try {
                        videoMode = MediaAttributes.VideoMode.getValueOf(value);
                    } catch (IllegalArgumentException e) {
                        final NotificationsDao notifications = storage.getNotificationsDao();
                        final Notification notification = notification(WARNING_NOTIFICATION, 15001, value
                                + " is not a valid mode value for <Video>");
                        notifications.addNotification(notification);
                    }
                }
            }
            return videoMode;
        }

        protected MediaAttributes.VideoResolution videoResolution(final Tag container) {
            MediaAttributes.VideoResolution videoResolution = MediaAttributes.VideoResolution.SEVEN_TWENTY_P;
            Attribute attribute = container.attribute("resolution");
            if (attribute != null) {
                final String value = attribute.value();
                if (value != null && !value.isEmpty()) {
                    try {
                        videoResolution = MediaAttributes.VideoResolution.getValueOf(value);
                    } catch (IllegalArgumentException e) {
                        final NotificationsDao notifications = storage.getNotificationsDao();
                        final Notification notification = notification(WARNING_NOTIFICATION, 15002, value
                                + " is not a valid resolution value for <Video>");
                        notifications.addNotification(notification);
                    }
                }
            }
            return videoResolution;
        }

        protected MediaAttributes.VideoLayout videoLayout(final Tag container) {
            MediaAttributes.VideoLayout videoLayout = MediaAttributes.VideoLayout.LINEAR;
            Attribute attribute = container.attribute("layout");
            if (attribute != null) {
                final String value = attribute.value();
                if (value != null && !value.isEmpty()) {
                    try {
                        videoLayout = MediaAttributes.VideoLayout.getValueOf(value);
                    } catch (IllegalArgumentException e) {
                        final NotificationsDao notifications = storage.getNotificationsDao();
                        final Notification notification = notification(WARNING_NOTIFICATION, 15003, value
                                + " is not a valid layout value for <Video>");
                        notifications.addNotification(notification);
                    }
                }
            }
            return videoLayout;
        }

        protected String videoOverlay(final Tag container) {
            String videoOverlay = null;
            Attribute attribute = container.attribute("overlay");
            if (attribute != null) {
                final String value = attribute.value();
                if (value != null && !value.isEmpty()) {
                    videoOverlay = value;
                }
            }
            return videoOverlay;
        }

        public void fetchMediaAttributes(final Tag container){
            Tag video = video(container);
            if(video != null){
                MediaAttributes.MediaType mediaType = videoEnabled(video);
                if (!MediaAttributes.MediaType.AUDIO_ONLY.equals(mediaType)) {
                    final MediaAttributes.VideoMode videoMode = videoMode(video);
                    final MediaAttributes.VideoResolution videoResolution = videoResolution(video);
                    final MediaAttributes.VideoLayout videoLayout = videoLayout(video);
                    final String videoOverlay = videoOverlay(video);
                    mediaAttributes = new MediaAttributes(mediaType, videoMode, videoResolution, videoLayout,
                            videoOverlay);
                }
            }
        }
    }

    private final class StartDialing extends AbstractDialAction {

        public StartDialing(final ActorRef source) {
            super(source);
        }

        @Override
        public void execute(final Object message) throws Exception {
            final Class<?> klass = message.getClass();
            if (Tag.class.equals(klass)) {
                verb = (Tag) message;
            }
            if (logger.isInfoEnabled()) {
                logger.info("At StartDialing state, preparing Dial for RCML: "+verb.toString().trim().replace("\\n",""));
            }
            final String text = verb.text();
            if (text != null && !text.isEmpty()) {
                // Build the appropriate tag for the text, such as Number, Client or SIP
                final Tag.Builder builder = Tag.builder();
                // Read the next tag.
                if (text.contains("@")) {
                    builder.setName(Nouns.SIP);
                } else if (text.startsWith("client")) {
                    builder.setName(Nouns.client);
                } else {
                    builder.setName(Nouns.number);
                }
                builder.setText(text);
                Tag numberTag = builder.build();

                // Change the Dial verb to include the Tag we created before
                Tag.Builder tagBuilder = Tag.builder();
                tagBuilder.addChild(numberTag);
                tagBuilder.setIterable(verb.isIterable());
                tagBuilder.setName(verb.name());
                tagBuilder.setParent(verb.parent());
                for (Attribute attribute : verb.attributes()) {
                    if (attribute != null)
                        tagBuilder.addAttribute(attribute);
                }
                verb = null;
                verb = tagBuilder.build();
            }

            if (verb.hasChildren()) {
                // Handle conferencing.
                final Tag child = conference(verb);
                if (child != null) {
                    String name = null;
                    final Tag grandchild = video(child);
                    if (grandchild == null) {
                        name = child.text();
                    } else {
                        // Handle video conferencing
                        Attribute attribute = child.attribute("name");
                        if (attribute != null && !StringUtils.isEmpty(attribute.toString())) {
                            name = attribute.value();
                        } else {
                            String errorMsg = "Attribute \"name\" not found or \"name\" value is empty inside <Conference> verb.";
                            logger.error(errorMsg);
                            throw new Exception(errorMsg);
                        }
                        fetchMediaAttributes(child);
                    }
                    final StringBuilder buffer = new StringBuilder();
                    //conference account should be phone account. i.e account of whoever owns that phone number.
                    //https://github.com/RestComm/Restcomm-Connect/issues/1939
                    Sid conferenceAccountId = phoneId == null? accountId : phoneId;
                    buffer.append(conferenceAccountId.toString()).append(":").append(name);
                    conferenceNameWithAccountAndFriendlyName = buffer.toString();
                    callSid = null;
                    if (callInfo != null && callInfo.sid() != null) {
                        callSid = callInfo.sid();
                    }
                    if (callSid == null && callRecord != null) {
                        callSid = callRecord.getSid();
                    }
                    final CreateConference create = new CreateConference(conferenceNameWithAccountAndFriendlyName, callSid, mediaAttributes);
                    conferenceCenter.tell(create, source);
                } else {
                    // Handle forking.
                    dialBranches = new ArrayList<ActorRef>();
                    dialChildren = new ArrayList<Tag>(verb.children());
                    dialChildrenWithAttributes = new HashMap<ActorRef, Tag>();
                    isForking = true;
                    final StartForking start = new StartForking();
                    source.tell(start, source);
                    if (logger.isInfoEnabled()) {
                        logger.info("Dial verb "+verb.toString().replace("\\n","")+" with more that one element, will start forking. Dial Children size: "+dialChildren.size());
                    }
                }
            } else {
                // Ask the parser for the next action to take.
                final GetNextVerb next = new GetNextVerb();
                parser.tell(next, source);
            }
        }
    }

    private final class ProcessingDialChildren extends AbstractDialAction {
        public ProcessingDialChildren(final ActorRef source) {
            super(source);
        }

        @SuppressWarnings("unchecked")
        @Override
        public void execute(final Object message) throws Exception {
            Class<?> klass = message.getClass();
            if (CallManagerResponse.class.equals(klass) && ((CallManagerResponse)message).succeeded()) {
                Tag child = dialChildren.get(0);
                final CallManagerResponse<Object> response = (CallManagerResponse<Object>) message;
                if (response.get() instanceof List) {
                    List<ActorRef> calls = (List<ActorRef>) response.get();
                    for (ActorRef branch: calls) {
                        dialBranches.add(branch);
                        if (child.hasAttributes()) {
                            dialChildrenWithAttributes.put(branch, child);
                        }
                    }
                } else {
                    final ActorRef branch = (ActorRef) response.get();
                    dialBranches.add(branch);
                    if (child.hasAttributes()) {
                        dialChildrenWithAttributes.put(branch, child);
                    }
                }
                dialChildren.remove(child);
            }
            else if (CallManagerResponse.class.equals(klass) && !((CallManagerResponse)message).succeeded()) {
                dialChildren.remove(0);
            }
            if (!dialChildren.isEmpty()) {
                CreateCall create = null;
                final Tag child = dialChildren.get(0);

                URI statusCallback = null;
                String statusCallbackMethod = "POST";
                List<String> statusCallbackEvent = new LinkedList<String>();

                if (child.hasAttribute("statusCallback")) {
                    statusCallback = new URI(child.attribute("statusCallback").value());
                }
                if (statusCallback != null) {
                    if (child.hasAttribute("statusCallbackMethod")) {
                        statusCallbackMethod = child.attribute("statusCallbackMethod").value();
                    }
                    if (child.hasAttribute("statusCallbackEvent")) {
                        statusCallbackEvent.addAll(Arrays.asList(child.attribute("statusCallbackEvent").value().replaceAll("\\s+","").split(",")));
                    } else {
                        statusCallbackEvent = new LinkedList<String>();
                        statusCallbackEvent.add("initiated");
                        statusCallbackEvent.add("ringing");
                        statusCallbackEvent.add("answered");
                        statusCallbackEvent.add("completed");
                    }
                }
                fetchMediaAttributes(child);

                //Get To
                String to;
                if(video(child) != null){
                    to = child.attribute("name").value();
                } else {
                    to = child.text();
                }

                String callee = to;

                //Extract and pass custom headers for Dial Client and Dial Number
                String customHeaders = getCustomHeaders(to);

                //Get Callee after removing any custom headers (this way there will be no impact on the Extensions execution)
                callee = customHeaders != null ? to.substring(0, to.indexOf("?")) : to;

                if (Nouns.client.equals(child.name())) {
                    if (call != null && callInfo != null) {
                        create = new CreateCall(e164(callerId(verb)), e164(callee), null, null, callInfo.isFromApi(), timeout(verb),
                                CreateCallType.CLIENT, accountId, callInfo.sid(), statusCallback, statusCallbackMethod, statusCallbackEvent, mediaAttributes, customHeaders);
                    } else {
                        create = new CreateCall(e164(callerId(verb)), e164(callee), null, null, false, timeout(verb),
                                CreateCallType.CLIENT, accountId, null, statusCallback, statusCallbackMethod, statusCallbackEvent, mediaAttributes, customHeaders);
                    }
                } else if (Nouns.number.equals(child.name())) {
                    if (call != null && callInfo != null) {
                        create = new CreateCall(e164(callerId(verb)), e164(callee), null, null, callInfo.isFromApi(), timeout(verb),
                                CreateCallType.PSTN, accountId, callInfo.sid(), statusCallback, statusCallbackMethod, statusCallbackEvent, customHeaders);
                    } else {
                        create = new CreateCall(e164(callerId(verb)), e164(callee), null, null, false, timeout(verb),
                                CreateCallType.PSTN, accountId, null, statusCallback, statusCallbackMethod, statusCallbackEvent, customHeaders);
                    }
                } else if (Nouns.uri.equals(child.name())) {
                    if (call != null && callInfo != null) {
                        create = new CreateCall(e164(callerId(verb)), e164(callee), null, null, callInfo.isFromApi(), timeout(verb),
                                CreateCallType.SIP, accountId, callInfo.sid(), statusCallback, statusCallbackMethod, statusCallbackEvent, mediaAttributes, customHeaders);
                    } else {
                        create = new CreateCall(e164(callerId(verb)), e164(callee), null, null, false, timeout(verb),
                                CreateCallType.SIP, accountId, null, statusCallback, statusCallbackMethod, statusCallbackEvent, mediaAttributes, customHeaders);
                    }
                } else if (Nouns.SIP.equals(child.name())) {
                    // https://bitbucket.org/telestax/telscale-restcomm/issue/132/implement-twilio-sip-out
                    String username = null;
                    String password = null;
                    if (asImsUa) {
                        username = imsUaLogin;
                        password = imsUaPassword;
                    } else {
                        if (child.attribute("username") != null) {
                            username = child.attribute("username").value();
                        }
                        if (child.attribute("password") != null) {
                            password = child.attribute("password").value();
                        }
                        if (username == null || username.isEmpty()) {
                            final Sid organizationSid = storage.getAccountsDao().getAccount(accountId).getOrganizationSid();
                            if (storage.getClientsDao().getClient(callInfo.from(), organizationSid) != null) {
                                username = callInfo.from();
                                password = storage.getClientsDao().getClient(callInfo.from(), organizationSid).getPassword();
                            }
                        }
                    }
                    if (call != null && callInfo != null) {
                        create = new CreateCall(e164(callerId(verb)), e164(callee), username, password, false, timeout(verb),
                                CreateCallType.SIP, accountId, callInfo.sid(), statusCallback, statusCallbackMethod, statusCallbackEvent, mediaAttributes, customHeaders);
                    } else {
                        create = new CreateCall(e164(callerId(verb)), e164(callee), username, password, false, timeout(verb),
                                CreateCallType.SIP, accountId, null, statusCallback, statusCallbackMethod, statusCallbackEvent, mediaAttributes, customHeaders);
                    }
                }
                callManager.tell(create, source);
            } else {
                if (dialBranches != null && dialBranches.size() > 0) {
                    // Fork.
                    final Fork fork = new Fork();
                    source.tell(fork, source);
                    dialChildren = null;
                } else {
//                    fsm.transition(message, hangingUp);
                    Attribute attribute = null;
                    if (verb != null) {
                        attribute = verb.attribute("action");
                    }
                    if (attribute == null) {
                        if (logger.isInfoEnabled()) {
                            logger.info("At ProcessingDialChildren with dialerBranches either null or 0 and attribute is null, will check for the next verb");
                        }
                        final GetNextVerb next = new GetNextVerb();
                        if (parser != null) {
                            parser.tell(next, source);
                        }
                    } else {
                        if (logger.isInfoEnabled()) {
                            logger.info("At ProcessingDialChildren with dialerBranches either null or 0 will execute Dial Action");
                        }
                        executeDialAction(message, outboundCall);
                    }
                }
            }
        }
    }

    private String getCustomHeaders(final String to) {
        String customHeaders = null;

        if (to.contains("?")) {
            customHeaders = to.substring(to.indexOf("?")+1, to.length());
        }

        return customHeaders;
    }

    private final class Forking extends AbstractDialAction {
        public Forking(final ActorRef source) {
            super(source);
        }

        @SuppressWarnings("unchecked")
        @Override
        public void execute(final Object message) throws Exception {
            final Class<?> klass = message.getClass();
            if (CallManagerResponse.class.equals(klass)) {
                final CallManagerResponse<ActorRef> response = (CallManagerResponse<ActorRef>) message;
                outboundCall = response.get();
                outboundCall.tell(new Observe(source), source);
                if (monitoring != null) {
                    outboundCall.tell(new Observe(monitoring), self());
                }
                outboundCall.tell(new Dial(), source);
            } else if (Fork.class.equals(klass)) {
                final Observe observe = new Observe(source);
                final Dial dial = new Dial();
                for (final ActorRef branch : dialBranches) {
                    branch.tell(observe, source);
                    if (monitoring != null) {
                        branch.tell(new Observe(monitoring), self());
                    }
                    branch.tell(dial, source);
                }
            }
            String path = configuration.subset("runtime-settings").getString("prompts-uri");
            if (!path.endsWith("/")) {
                path += "/";
            }
            path += "ringing.wav";
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
            final Play play = new Play(uri, Short.MAX_VALUE);
            call.tell(play, source);
            final UntypedActorContext context = getContext();
            context.setReceiveTimeout(Duration.create(timeout(verb), TimeUnit.SECONDS));
        }
    }

    private final class AcquiringOutboundCallInfo extends AbstractDialAction {
        public AcquiringOutboundCallInfo(final ActorRef source) {
            super(source);
        }

        @Override
        public void execute(final Object message) throws Exception {
            if (isForking) {
                if (logger.isInfoEnabled()) {
                    String msg = String.format("About to cancel Calls in dialBranches and keep outboundCall %s", outboundCall);
                    logger.info(msg);
                }
                dialBranches.remove(outboundCall);
                for (final ActorRef branch : dialBranches) {
                    branch.tell(new Cancel(), null);
                    if (logger.isInfoEnabled()) {
                        String msg = String.format("Canceled outbound Call %s", branch);
                        logger.info(msg);
                    }
                }
                dialBranches = null;
            }
            outboundCall.tell(new GetCallInfo(), source);
        }
    }

    private final class Bridged extends AbstractDialAction {
        public Bridged(final ActorRef source) {
            super(source);
        }

        @Override
        public void execute(final Object message) throws Exception {
            final int timeLimit = timeLimit(verb);
            final UntypedActorContext context = getContext();
            context.setReceiveTimeout(Duration.create(timeLimit, TimeUnit.SECONDS));

            if (dialRecordAttribute != null && "true".equalsIgnoreCase(dialRecordAttribute.value())) {
                if(logger.isInfoEnabled()) {
                    logger.info("Start recording of the bridge");
                }
                record(bridge);
            }
            if(enable200OkDelay && verb !=null && Verbs.dial.equals(verb.name())){
                call.tell(message, self());
        }
    }
    }

    private void record(ActorRef target) {
        if(logger.isInfoEnabled()) {
            logger.info("Start recording of the call: "+target.path()+", VI state: "+fsm.state());
        }
        Configuration runtimeSettings = configuration.subset("runtime-settings");
        recordingSid = Sid.generate(Sid.Type.RECORDING);
        String path = runtimeSettings.getString("recordings-path");
        String httpRecordingUri = runtimeSettings.getString("recordings-uri");
        if (!path.endsWith("/")) {
            path += "/";
        }
        if (!httpRecordingUri.endsWith("/")) {
            httpRecordingUri += "/";
        }
        path += recordingSid.toString() + ".wav";
        httpRecordingUri += recordingSid.toString() + ".wav";
        this.recordingUri = URI.create(path);
        try {
            this.publicRecordingUri = uriUtils.resolve(new URI(httpRecordingUri), accountId);
        } catch (URISyntaxException e) {
            logger.error("URISyntaxException when trying to resolve Recording URI: " + e);
        }
        recordingCall = true;
        StartRecording message = new StartRecording(accountId, callInfo.sid(), runtimeSettings, storage, recordingSid,
                recordingUri);
        target.tell(message, null);
    }

//    private void recordCall() {
//        if(logger.isInfoEnabled()) {
//            logger.info("Start recording of the call");
//        }
//        record(call);
//    }

    private void recordConference() {
        logger.info("Start recording of the conference");
        record(conference);
    }

    @SuppressWarnings("unchecked")
    private void executeDialAction(final Object message, final ActorRef outboundCall) {
        Attribute attribute = null;
        if (verb != null && Verbs.dial.equals(verb.name())) {
            attribute = verb.attribute("action");
        } else {
            if (logger.isInfoEnabled()) {
                logger.info("Either Verb is null OR not Dial, Dial Action will not be executed");
            }
        }
        if (attribute != null && !dialActionExecuted) {
            if(logger.isInfoEnabled()){
                logger.info("Proceeding to execute Dial Action attribute");
            }
            this.dialActionExecuted = true;

            if (call != null) {
                try {
                    if(logger.isInfoEnabled()) {
                        logger.info("Trying to get inbound call Info");
                    }
                    final Timeout expires = new Timeout(Duration.create(5, TimeUnit.SECONDS));
                    Future<Object> future = (Future<Object>) ask(call, new GetCallInfo(), expires);
                    CallResponse<CallInfo> callResponse = (CallResponse<CallInfo>) Await.result(future,
                            Duration.create(10, TimeUnit.SECONDS));
                    callInfo = callResponse.get();
                    callState = callInfo.state();
                } catch (Exception e) {
                    if(logger.isDebugEnabled()) {
                        logger.debug("Timeout waiting for inbound call info: \n" + e.getMessage());
                    }
                }
            }

            final List<NameValuePair> parameters = parameters();

            if (outboundCall != null && !outboundCall.isTerminated()) {
                try {
                    if(logger.isInfoEnabled()) {
                        logger.info("Trying to get outboundCall Info");
                    }
                    final Timeout expires = new Timeout(Duration.create(10, TimeUnit.SECONDS));
                    Future<Object> future = (Future<Object>) ask(outboundCall, new GetCallInfo(), expires);
                    CallResponse<CallInfo> callResponse = (CallResponse<CallInfo>) Await.result(future,
                            Duration.create(10, TimeUnit.SECONDS));
                    outboundCallInfo = callResponse.get();
                    final long dialRingDuration = new Interval(this.outboundCallInfo.dateCreated(), this.outboundCallInfo.dateConUpdated()).toDuration()
                            .getStandardSeconds();
                    parameters.add(new BasicNameValuePair("DialRingDuration", String.valueOf(dialRingDuration)));
                } catch (AskTimeoutException askTimeoutException){
                    logger.warning("Akka ask Timeout waiting for outbound call info: \n" + askTimeoutException.getMessage());
                } catch (Exception e) {
                    logger.error("Exception while waiting for outbound call info: \n" + e);
                }
            }

            // Handle Failed Calls
            if (message instanceof CallManagerResponse && !(((CallManagerResponse<ActorRef>) message).succeeded())) {
                if (outboundCallInfo != null) {
                    parameters.add(new BasicNameValuePair("DialCallSid", (outboundCallInfo.sid() == null) ? "null" : outboundCallInfo.sid().toString()));
                } else {
                    parameters.add(new BasicNameValuePair("DialCallSid", "null"));
                }
                parameters.add(new BasicNameValuePair("DialCallStatus", CallStateChanged.State.FAILED.toString()));
                parameters.add(new BasicNameValuePair("DialCallDuration", "0"));
                parameters.add(new BasicNameValuePair("RecordingUrl", null));
                parameters.add(new BasicNameValuePair("PublicRecordingUrl", null));
            }
            // Handle No-Answer calls
            else if (message instanceof ReceiveTimeout) {
                if (outboundCallInfo != null) {
                    final String dialCallSid = this.outboundCallInfo.sid().toString();
                    long dialCallDuration;
                    if (outboundCallInfo.state().toString().equalsIgnoreCase("Completed")) {
                        dialCallDuration = new Interval(this.outboundCallInfo.dateConUpdated(), DateTime.now()).toDuration()
                                .getStandardSeconds();
                    } else {
                        dialCallDuration = 0L;
                    }
                    final String recordingUrl = this.recordingUri == null ? null : this.recordingUri.toString();
                    final String publicRecordingUrl = this.publicRecordingUri == null ? null : this.publicRecordingUri.toString();

                    parameters.add(new BasicNameValuePair("DialCallSid", dialCallSid));
                    // parameters.add(new BasicNameValuePair("DialCallStatus", dialCallStatus == null ? null : dialCallStatus
                    // .toString()));
                    parameters.add(new BasicNameValuePair("DialCallStatus", outboundCallInfo.state().toString()));
                    parameters.add(new BasicNameValuePair("DialCallDuration", String.valueOf(dialCallDuration)));
                    parameters.add(new BasicNameValuePair("RecordingUrl", recordingUrl));
                    parameters.add(new BasicNameValuePair("PublicRecordingUrl", publicRecordingUrl));
                } else {
                    parameters.add(new BasicNameValuePair("DialCallSid", "null"));
                    parameters.add(new BasicNameValuePair("DialCallStatus", CallStateChanged.State.NO_ANSWER.toString()));
                    parameters.add(new BasicNameValuePair("DialCallDuration", "0"));
                    parameters.add(new BasicNameValuePair("RecordingUrl", null));
                    parameters.add(new BasicNameValuePair("PublicRecordingUrl", null));
                }
            } else {
                // Handle the rest of the cases
                if (outboundCallInfo != null) {
                    final String dialCallSid = this.outboundCallInfo.sid().toString();
                    final CallStateChanged.State dialCallStatus = this.outboundCallInfo.state();
                    long dialCallDuration = 0L;
                    //In some cases, such as when the outbound dial is busy, the dialCallDuration wont be possbile to be calculated and will throw exception
                    try {
                        dialCallDuration = new Interval(this.outboundCallInfo.dateConUpdated(), DateTime.now()).toDuration()
                                .getStandardSeconds();
                    } catch (Exception e) {}
                    final String recordingUrl = this.recordingUri == null ? null : this.recordingUri.toString();
                    final String publicRecordingUrl = this.publicRecordingUri == null ? null : this.publicRecordingUri.toString();

                    parameters.add(new BasicNameValuePair("DialCallSid", dialCallSid));
                    // If Caller sent the BYE request, at the time we execute this method, the outbound call status is still in
                    // progress
                    if (callInfo.state().equals(CallStateChanged.State.COMPLETED)) {
                        parameters.add(new BasicNameValuePair("DialCallStatus", callInfo.state().toString()));
                    } else {
                        parameters.add(new BasicNameValuePair("DialCallStatus", dialCallStatus == null ? null : dialCallStatus
                                .toString()));
                    }
                    if (callState == CallStateChanged.State.BUSY)
                        parameters.add(new BasicNameValuePair("DialCallDuration", "0"));
                    else
                        parameters.add(new BasicNameValuePair("DialCallDuration", String.valueOf(dialCallDuration)));
                    parameters.add(new BasicNameValuePair("RecordingUrl", recordingUrl));
                    parameters.add(new BasicNameValuePair("PublicRecordingUrl", publicRecordingUrl));
                } else {
                    parameters.add(new BasicNameValuePair("DialCallSid", "null"));
                    parameters.add(new BasicNameValuePair("DialCallStatus", "null"));
                    parameters.add(new BasicNameValuePair("DialCallDuration", "0"));
                    parameters.add(new BasicNameValuePair("RecordingUrl", null));
                    parameters.add(new BasicNameValuePair("PublicRecordingUrl", "null"));
                }
            }

            final NotificationsDao notifications = storage.getNotificationsDao();
            if (attribute != null) {
                if(logger.isInfoEnabled()) {
                    logger.info("Executing Dial Action attribute.");
                }
                String action = attribute.value();
                if (action != null && !action.isEmpty()) {
                    URI target = null;
                    try {
                        target = URI.create(action);
                    } catch (final Exception exception) {
                        final Notification notification = notification(ERROR_NOTIFICATION, 11100, action + " is an invalid URI.");
                        notifications.addNotification(notification);
                        sendMail(notification);
                        final StopInterpreter stop = new StopInterpreter();
                        self().tell(stop, self());
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
                                final Notification notification = notification(WARNING_NOTIFICATION, 13210, method
                                        + " is not a valid HTTP method for <Dial>");
                                notifications.addNotification(notification);
                                method = "POST";
                            }
                        } else {
                            method = "POST";
                        }
                    }
                    if(logger.isInfoEnabled()) {
                        logger.info("Dial Action URL: " + uri.toString() + " Method: " + method);
                    }
                    if(logger.isDebugEnabled()) {
                        logger.debug("Dial Action parameters: \n" + parameters);
                    }
                    // Redirect to the action url.
                    request = new HttpRequestDescriptor(uri, method, parameters);
                    // Tell the downloader to send the Dial Parameters to the Action url but we don't need a reply back so sender == null
                    downloader.tell(request, self());
                    return;
                }
            }
        } else {
            if (logger.isInfoEnabled()) {
                if (attribute == null) {
                    logger.info("DialAction URL is null, DialAction will not be executed");
                } else {
                    logger.info("DialAction has already been executed");
                }
            }
        }
    }

    private final class FinishDialing extends AbstractDialAction {
        public FinishDialing(final ActorRef source) {
            super(source);
        }

        @Override
        public void execute(final Object message) throws Exception {
            final State state = fsm.state();
            if(logger.isInfoEnabled()) {
                logger.info("At FinishDialing, current VI state: " + state);
            }

            if (message instanceof ReceiveTimeout) {
                if(logger.isInfoEnabled()) {
                    logger.info("Received timeout, will cancel branches, current VoiceIntepreter state: " + state);
                }
                if(enable200OkDelay){
                    outboundCallResponse = SipServletResponse.SC_REQUEST_TIMEOUT;
                }
                //The forking timeout reached, we have to cancel all dial branches
                final UntypedActorContext context = getContext();
                context.setReceiveTimeout(Duration.Undefined());

                if (dialBranches != null) {
                    Iterator<ActorRef> dialBranchesIterator = dialBranches.iterator();
                    while (dialBranchesIterator.hasNext()) {
                        ActorRef branch = dialBranchesIterator.next();
                        branch.tell(new Cancel(), source);
                        if(logger.isInfoEnabled()) {
                            logger.info("Canceled branch: " + branch.path()+", isTerminated: "+branch.isTerminated());
                        }
                    }
                    // Stop playing the ringing tone from inbound call
                    stopRingTone();
                } else if (outboundCall != null) {
                    outboundCall.tell(new Cancel(), source);
                    call.tell(new Hangup(outboundCallResponse), self());
                }
                if (dialBranches == null) {
                    checkDialBranch(message,sender,action);
                }
                dialChildren = null;
                callback();
                return;
            }

            if (message instanceof CallStateChanged) {
                if(logger.isInfoEnabled()) {
                    String msg = String.format("CallStateChanged state received: [%s] , sender path: [%s] , is initial call: [%s] , call state: [%s], fsm state: [%s]", ((CallStateChanged)message).toString(), sender().path(), (sender == call), callState.toString(), fsm.state());
                    logger.info(msg);
                }
                if (forking.equals(state) || finishDialing.equals(state) || is(bridged) || is(bridging) ) {
                    if (sender.equals(call)) {
                        //Initial call wants to finish dialing
                        if(logger.isInfoEnabled()) {
                            logger.info("Sender == call: " + sender.equals(call));
                        }
                        final UntypedActorContext context = getContext();
                        context.setReceiveTimeout(Duration.Undefined());

                        if (dialBranches != null) {
                            Iterator<ActorRef> dialBranchesIterator = dialBranches.iterator();
                            while (dialBranchesIterator.hasNext()) {
                                ActorRef branch = dialBranchesIterator.next();
                                branch.tell(new Cancel(), source);
                            }
                        } else if (outboundCall != null) {
                            outboundCall.tell(new Cancel(), source);
                        }
                        //Issue https://github.com/RestComm/Restcomm-Connect/issues/2157. If outbound call != null,
                        //then checking the DialBranch here will cause race condition that will prevent outbound call to move
                        //to completed state because checkDialBranch() method will ask for the next verb which could be the End.tag
                        if (dialBranches == null && outboundCall == null) {
                            checkDialBranch(message,sender,action);
                        }
                        dialChildren = null;
                        callback();
                        return;
                    } else if (dialBranches != null && dialBranches.contains(sender)) {
                        if (logger.isInfoEnabled()) {
                            logger.info("At FinishDialing. Sender in the dialBranches, will remove and check next verb");
                        }
                        removeDialBranch(message, sender);
                        return;
                    } else {
                        if (callState == CallStateChanged.State.IN_PROGRESS) {
                            if (logger.isInfoEnabled()) {
                                String msg = String.format("At finishDialingState, will ASK call to hangup. Current VI State %s Call State: %s ",state, callState);
                                logger.info(msg);
                            }
                            call.tell(new Hangup(), self());
                        } else {
                            if (logger.isInfoEnabled()) {
                                String msg = String.format("Didn't sent Hangup to call because current call state is: [%s]", callState.toString());
                                logger.info(msg);
                            }
                        }
                    }
                }
            }
        }
    }

    private final class AcquiringConferenceInfo extends AbstractDialAction {
        public AcquiringConferenceInfo(final ActorRef source) {
            super(source);
        }

        @Override
        public void execute(final Object message) throws Exception {
            final ConferenceCenterResponse response = (ConferenceCenterResponse) message;
            conference = response.get();
            conference.tell(new Observe(source), source);
            conference.tell(new GetConferenceInfo(), source);
        }
    }

    private final class JoiningConference extends AbstractDialAction {
        public JoiningConference(final ActorRef source) {
            super(source);
        }

        @SuppressWarnings("unchecked")
        @Override
        public void execute(final Object message) throws Exception {
            conferenceState = conferenceInfo.state();
            conferenceSid = conferenceInfo.sid();
            final Tag child = conference(verb);

            // If there is room join the conference.
            Attribute attribute = child.attribute("maxParticipants");
            if (attribute != null) {
                final String value = attribute.value();
                if (value != null && !value.isEmpty()) {
                    try {
                        maxParticipantLimit = Integer.parseInt(value);
                    } catch (final NumberFormatException ignored) {
                    }
                }
            }

            if (conferenceInfo.globalParticipants() < maxParticipantLimit) {
                // Play beep.
                beep = true;
                attribute = child.attribute("beep");
                if (attribute != null) {
                    final String value = attribute.value();
                    if (value != null && !value.isEmpty()) {
                        beep = Boolean.parseBoolean(value);
                    }
                }

                // Only play beep if conference is already running
                // Do not play it while participants are listening to background music
                if (beep && ConferenceStateChanged.State.RUNNING_MODERATOR_PRESENT.equals(conferenceInfo.state())) {
                    playBeepOnEnter(source);
                }else{
                    if (logger.isInfoEnabled()) {
                        logger.info("Wont play beep bcz: beep="+beep+" AND conferenceInfo.state()="+conferenceInfo.state());
                    }
                }
                if (logger.isInfoEnabled()) {
                    logger.info("About to join call to Conference: "+conferenceInfo.name()+", with state: "+conferenceInfo.state()+", with moderator present: "+conferenceInfo.isModeratorPresent()+", and current participants: "+conferenceInfo.globalParticipants());
                }
                // Join the conference.
                //Adding conference record in DB
                //For outbound call the CDR will be updated at Call.InProgress()
                addConferenceStuffInCDR(conferenceSid);
                final AddParticipant request = new AddParticipant(call, mediaAttributes);
                conference.tell(request, source);
            } else {
                // Ask the parser for the next action to take.
                final GetNextVerb next = new GetNextVerb();
                parser.tell(next, source);
            }

            // parse mute
            attribute = child.attribute("muted");
            if (attribute != null) {
                final String value = attribute.value();
                if (value != null && !value.isEmpty()) {
                    muteCall = Boolean.parseBoolean(value);
                }
            }
            // parse startConferenceOnEnter.
            attribute = child.attribute("startConferenceOnEnter");
            if (attribute != null) {
                final String value = attribute.value();
                if (value != null && !value.isEmpty()) {
                    startConferenceOnEnter = Boolean.parseBoolean(value);
                }
            }
            // Parse "endConferenceOnExit"
            attribute = child.attribute("endConferenceOnExit");
            if (attribute != null) {
                final String value = attribute.value();
                if (value != null && !value.isEmpty()) {
                    endConferenceOnExit = Boolean.parseBoolean(value);
                }
            }
        }

        private void addConferenceStuffInCDR(Sid conferenceSid) {
            //updating conferenceSid and other conference related info in cdr
            if (callRecord != null) {
                if (logger.isInfoEnabled()) {
                    logger.info("Updating CDR for call: "+callInfo.sid()+", call status: "+callInfo.state()+", to include Conference details, conference: "+conferenceSid);
                }
                callRecord = callRecord.setConferenceSid(conferenceSid);
                callRecord = callRecord.setMuted(muteCall);
                callRecord = callRecord.setStartConferenceOnEnter(startConferenceOnEnter);
                callRecord = callRecord.setEndConferenceOnExit(endConferenceOnExit);
                final CallDetailRecordsDao records = storage.getCallDetailRecordsDao();
                records.updateCallDetailRecord(callRecord);
            }
        }
    }

    private final class Conferencing extends AbstractDialAction {
        public Conferencing(final ActorRef source) {
            super(source);
        }

        @Override
        public void execute(final Object message) throws Exception {
            boolean onHoldInCDR = false;
            boolean onMuteInCDR = muteCall;
            if (message instanceof List<?>) {
                List<URI> waitUrls = (List<URI>) message;
                playWaitUrl(waitUrls, self());
                playWaitUrlPending = false;
                return;
            }
            final NotificationsDao notifications = storage.getNotificationsDao();
            final Tag child = conference(verb);
            conferenceVerb = verb;
            if (muteCall) {
                final Mute mute = new Mute();
                call.tell(mute, source);
            }
            // Parse start conference.
            Attribute attribute = child.attribute("startConferenceOnEnter");
            if (attribute != null) {
                final String value = attribute.value();
                if (value != null && !value.isEmpty()) {
                    startConferenceOnEnter = Boolean.parseBoolean(value);
                }
            } else {
                //Default values is startConferenceOnEnter = true
                startConferenceOnEnter = true;
            }

            confModeratorPresent = startConferenceOnEnter;
            if (logger.isInfoEnabled()) {
                logger.info("At conferencing, VI state: "+fsm.state()+" , playMusicForConference: "+playMusicForConference+" ConferenceState: "+conferenceState.name()+" startConferenceOnEnter: "+startConferenceOnEnter+"  conferenceInfo.globalParticipants(): "+conferenceInfo.globalParticipants());
            }
            if (playMusicForConference) { // && startConferenceOnEnter) {
                //playMusicForConference is true, take over control of startConferenceOnEnter
                if (conferenceInfo.globalParticipants() == 1) {
                    startConferenceOnEnter = false;
                } else if (conferenceInfo.globalParticipants() > 1) {
                    if (startConferenceOnEnter || conferenceInfo.isModeratorPresent()) {
                        startConferenceOnEnter = true;
                    } else {
                        startConferenceOnEnter = false;
                    }
                }
            }

            if (!startConferenceOnEnter && conferenceState == ConferenceStateChanged.State.RUNNING_MODERATOR_ABSENT) {
                if (!muteCall) {
                    final Mute mute = new Mute();
                    if(logger.isInfoEnabled()) {
                        logger.info("Muting the call as startConferenceOnEnter =" + startConferenceOnEnter + " , callMuted = "
                            + muteCall);
                    }
                    call.tell(mute, source);
                    onMuteInCDR = true;
                }

                // Only play background music if conference is not doing that already
                // If conference state is RUNNING_MODERATOR_ABSENT and participants > 0 then BG music is playing already
                if(logger.isInfoEnabled()) {
                    logger.info("Play background music? " + (conferenceInfo.globalParticipants() == 1));
                }
                boolean playBackground = conferenceInfo.globalParticipants() == 1;
                if (playBackground) {
                    // Parse wait url.
                    URI waitUrl = new URI("/restcomm/music/electronica/teru_-_110_Downtempo_Electronic_4.wav");
                    attribute = child.attribute("waitUrl");
                    if (attribute != null) {
                        String value = attribute.value();
                        if (value != null && !value.isEmpty()) {
                            try {
                                waitUrl = URI.create(value);
                            } catch (final Exception exception) {
                                final Notification notification = notification(ERROR_NOTIFICATION, 13233, method
                                        + " is not a valid waitUrl value for <Conference>");
                                notifications.addNotification(notification);
                                sendMail(notification);
                                final StopInterpreter stop = new StopInterpreter();
                                source.tell(stop, source);

                                // TODO shouldn't we return here?
                            }
                        }
                    }

                    final URI base = request.getUri();
                    waitUrl = uriUtils.resolveWithBase(base, waitUrl);
                    // Parse method.
                    String method = "POST";
                    attribute = child.attribute("waitMethod");
                    if (attribute != null) {
                        method = attribute.value();
                        if (method != null && !method.isEmpty()) {
                            if (!"GET".equalsIgnoreCase(method) && !"POST".equalsIgnoreCase(method)) {
                                final Notification notification = notification(WARNING_NOTIFICATION, 13234, method
                                        + " is not a valid waitMethod value for <Conference>");
                                notifications.addNotification(notification);
                                method = "POST";
                            }
                        } else {
                            method = "POST";
                        }
                    }

                    if (!waitUrl.getPath().toLowerCase().endsWith("wav")) {
                        if (logger.isInfoEnabled()) {
                            logger.info("WaitUrl for Conference will use RCML from URI: "+waitUrl.toString());
                        }
                        final List<NameValuePair> parameters = parameters();
                        request = new HttpRequestDescriptor(waitUrl, method, parameters);
                        downloader.tell(request, self());
                        playWaitUrlPending = true;
                        return;
                    }

                    // Tell conference to play music to participants on hold
                    if (waitUrl != null && !playWaitUrlPending) {
                        onHoldInCDR = true;
                        playWaitUrl(waitUrl, super.source);
                    }
                }
            } else if (conferenceState == ConferenceStateChanged.State.RUNNING_MODERATOR_ABSENT) {
                // Tell the conference the moderator is now present
                // Causes background music to stop playing and all participants will be unmuted
                conference.tell(new ConferenceModeratorPresent(beep), source);
                if (beep) {
                    playBeepOnEnter(source);
                }else{
                    if (logger.isInfoEnabled()) {
                        logger.info("Wont play beep bcz: beep="+beep+" AND conferenceInfo.state()="+conferenceInfo.state());
                    }
                }
                // Check if moderator wants to record the conference
                Attribute record = verb.attribute("record");
                if (record != null && "true".equalsIgnoreCase(record.value())) {
                    // XXX get record limit etc from dial verb
                    recordConference();
                }
                // Call is no more on hold
                //open this block when mute/hold functionality in conference API is fixed
                //updateMuteAndHoldStatusOfAllConferenceCalls(conferenceDetailRecord.getAccountSid(), conferenceDetailRecord.getSid(), false, false);
            } else {
                // Call is no more on hold
                //open this block when mute/hold functionality in conference API is fixed
                //updateMuteAndHoldStatusOfAllConferenceCalls(conferenceDetailRecord.getAccountSid(), conferenceDetailRecord.getSid(), false, false);
            }
            // update Call hold and mute status
            if(callRecord != null){
                callRecord = callRecord.setOnHold(onHoldInCDR);
                callRecord = callRecord.setMuted(onMuteInCDR);
                final CallDetailRecordsDao callRecords = storage.getCallDetailRecordsDao();
                callRecords.updateCallDetailRecord(callRecord);
            }
            // Set timer.
            final int timeLimit = timeLimit(verb);
            final UntypedActorContext context = getContext();
            context.setReceiveTimeout(Duration.create(timeLimit, TimeUnit.SECONDS));
        }
    }

    protected void playBeepOnEnter(ActorRef source){
        String path = configuration.subset("runtime-settings").getString("prompts-uri");
        if (!path.endsWith("/")) {
            path += "/";
        }
        String entryAudio = configuration.subset("runtime-settings").getString("conference-entry-audio");
        path += entryAudio == null || entryAudio.equals("") ? "beep.wav" : entryAudio;
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
        if (logger.isInfoEnabled()) {
            logger.info("Will ask conference: "+conferenceInfo.name()+" ,to play beep: "+uri);
        }
        final Play play = new Play(uri, 1);
        conference.tell(play, source);
    }
    //Because of RMS issue https://github.com/RestComm/mediaserver/issues/158 we cannot have List<URI> for waitUrl
    protected void playWaitUrl(final List<URI> waitUrls, final ActorRef source) {
        conference.tell(new Play(waitUrls, Short.MAX_VALUE, confModeratorPresent), source);
    }

    protected void playWaitUrl(final URI waitUrl, final ActorRef source) {
        conference.tell(new Play(waitUrl, Short.MAX_VALUE, confModeratorPresent), source);
    }

    /* open this block when mute/hold functionality in conference API is fixed
     * protected void updateMuteAndHoldStatusOfAllConferenceCalls(final Sid accountSid, final Sid conferenceSid, final boolean mute, final boolean hold) throws ParseException{
        if (conferenceSid != null){
            CallDetailRecordFilter filter = new CallDetailRecordFilter(accountSid.toString(), null, null, null, "in-progress", null, null, null, conferenceSid.toString(), 50, 0);
            CallDetailRecordsDao callRecordsDAO = storage.getCallDetailRecordsDao();
            List<CallDetailRecord> conferenceCallRecords = callRecordsDAO.getCallDetailRecords(filter);
            if(conferenceCallRecords != null){
                for(CallDetailRecord singleRecord:conferenceCallRecords){
                    singleRecord.setMuted(mute);
                    singleRecord.setOnHold(hold);
                    callRecordsDAO = storage.getCallDetailRecordsDao();
                    callRecordsDAO.updateCallDetailRecord(singleRecord);
                }
            }
        }
    }*/

    private final class FinishConferencing extends AbstractDialAction {
        public FinishConferencing(final ActorRef source) {
            super(source);
        }

        @Override
        public void execute(final Object message) throws Exception {
            if (message instanceof ReceiveTimeout) {
                if (logger.isInfoEnabled()) {
                    logger.info("At FinishConferencing received timeout, VI path: "+self().path()+", call path: "+call.path());
                }
                final UntypedActorContext context = getContext();
                context.setReceiveTimeout(Duration.Undefined());
                final RemoveParticipant remove = new RemoveParticipant(call);
                conference.tell(remove, source);
            }
            // Clean up
            if (message instanceof ConferenceStateChanged) {
                // Destroy conference if state changed to completed (last participant in call)
                ConferenceStateChanged confStateChanged = (ConferenceStateChanged) message;
                if (ConferenceStateChanged.State.COMPLETED.equals(confStateChanged.state())) {
                    DestroyConference destroyConference = new DestroyConference(conferenceInfo.name());
                    conferenceCenter.tell(destroyConference, super.source);
                }
            }
            conference = null;

            // Parse remaining conference attributes.
            final NotificationsDao notifications = storage.getNotificationsDao();

            // Parse "action".
            Attribute attribute = conferenceVerb.attribute("action");
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
                    attribute = conferenceVerb.attribute("method");
                    if (attribute != null) {
                        method = attribute.value();
                        if (method != null && !method.isEmpty()) {
                            if (!"GET".equalsIgnoreCase(method) && !"POST".equalsIgnoreCase(method)) {
                                final Notification notification = notification(WARNING_NOTIFICATION, 13210, method
                                        + " is not a valid HTTP method for <Dial>");
                                notifications.addNotification(notification);
                                method = "POST";
                            }
                        } else {
                            method = "POST";
                        }
                    }
                    // Redirect to the action url.
                    final List<NameValuePair> parameters = parameters();
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

    private final class Finished extends AbstractAction {
        public Finished(final ActorRef source) {
            super(source);
        }

        @Override
        public void execute(final Object message) throws Exception {
            if(logger.isInfoEnabled()) {
                logger.info("At Finished state, state: " + fsm.state()+", liveCallModification: "+liveCallModification);
            }
            final Class<?> klass = message.getClass();

            if (callRecord != null) {
                final CallDetailRecordsDao records = storage.getCallDetailRecordsDao();
                callRecord = records.getCallDetailRecord(callRecord.getSid());
                callRecord = callRecord.setStatus(callState.toString());
                final DateTime end = DateTime.now();
                callRecord = callRecord.setEndTime(end);
                final int seconds = (int) (end.getMillis() - callRecord.getStartTime().getMillis()) / 1000;
                callRecord = callRecord.setDuration(seconds);
                records.updateCallDetailRecord(callRecord);
            }
            if (!dialActionExecuted) {
                executeDialAction(message, outboundCall);
            }

            callback(true);

            // XXX review bridge cleanup!!

            // Cleanup bridge
//            if ((bridge != null) && (is(forking) || is(acquiringOutboundCallInfo) || is(bridged))) {
            if (bridge != null) {
                // Stop the bridge
                bridge.tell(new StopBridge(liveCallModification), super.source);
                recordingCall = false;
                bridge = null;
            }
            // Cleanup the outbound call if necessary.
            // XXX verify if this code is still necessary
            if (outboundCall != null && !liveCallModification) {
                outboundCall.tell(new StopObserving(source), null);
                outboundCall.tell(new Hangup(), null);
                callManager.tell(new DestroyCall(outboundCall), null);
            }

            // If the call is in a conference remove it.
//            if (conference != null) {
//                // Stop Observing the conference
//                conference.tell(new StopObserving(super.source), null);
//
//                // Play beep when participant leave the conference.
//                // Do not play beep if this was the last participant to leave the conference, because there is no one to listen to the beep.
//                if(conferenceInfo.participants() != null && conferenceInfo.participants().size() !=0 ){
//                    String path = configuration.subset("runtime-settings").getString("prompts-uri");
//                    if (!path.endsWith("/")) {
//                        path += "/";
//                    }
//                    String exitAudio = configuration.subset("runtime-settings").getString("conference-exit-audio");
//                    path += exitAudio == null || exitAudio.equals("") ? "alert.wav" : exitAudio;
//                    URI uri = null;
//                    try {
//                        uri = UriUtils.resolve(new URI(path));
//                    } catch (final Exception exception) {
//                        final Notification notification = notification(ERROR_NOTIFICATION, 12400, exception.getMessage());
//                        final NotificationsDao notifications = storage.getNotificationsDao();
//                        notifications.addNotification(notification);
//                        sendMail(notification);
//                        final StopInterpreter stop = new StopInterpreter();
//                        source.tell(stop, source);
//                        return;
//                    }
//                    final Play play = new Play(uri, 1);
//                    conference.tell(play, source);
//                }
//                if (endConferenceOnExit) {
//                    // Stop the conference if endConferenceOnExit is true
//                    final StopConference stop = new StopConference();
//                    conference.tell(stop, super.source);
//                } else {
//                    conference.tell(new RemoveParticipant(call), source);
//                }
//            }

            if (!liveCallModification) {
                // Destroy the Call(s).
                if (call!= null && !call.isTerminated()) { // && End.instance().equals(verb.name())) {
                    call.tell(new Hangup(), self());
                }
                if (outboundCall != null &&!outboundCall.isTerminated()) {
                    outboundCall.tell(new Hangup(), self());
                }
                callManager.tell(new DestroyCall(call), super.source);
                if (outboundCall != null) {
                    callManager.tell(new DestroyCall(outboundCall), super.source);
                } if (sender != call && !sender.equals(self())) {
                    callManager.tell(new DestroyCall(sender), super.source);
                }
            } else {
                // Make sure the media operations of the call are stopped
                // so we can start processing a new RestComm application
                call.tell(new StopMediaGroup(true), super.source);
//                if (is(conferencing))
//                    call.tell(new Leave(true), self());
            }

            // Stop the dependencies.
            final UntypedActorContext context = getContext();
            if (mailerNotify != null)
                context.stop(mailerNotify);
            if (mailerService != null)
                context.stop(mailerService);
            context.stop(getAsrService());
            context.stop(getFaxService());
            context.stop(getCache());
            context.stop(getSynthesizer());

            // Stop the interpreter.
            postCleanup();
        }
    }


    @Override
    public void postStop() {
        if (!fsm.state().equals(uninitialized)) {
            if(logger.isInfoEnabled()) {
                logger.info("VoiceIntepreter: " + self().path()
                    + "At the postStop() method. Will clean up Voice Interpreter. Keep calls: " + liveCallModification);
            }
            if (fsm.state().equals(bridged) && outboundCall != null && !liveCallModification) {
                if(logger.isInfoEnabled()) {
                    logger.info("At postStop(), will clean up outbound call");
                }
                outboundCall.tell(new Hangup(), null);
                callManager.tell(new DestroyCall(outboundCall), null);
                outboundCall = null;
            }

            if (call != null && !liveCallModification) {
                if(logger.isInfoEnabled()) {
                    logger.info("At postStop(), will clean up call");
                }
                callManager.tell(new DestroyCall(call), null);
                call = null;
            }

            getContext().stop(self());
            postCleanup();
        }
        if(asImsUa){
            if(callRecord != null){
                final CallDetailRecordsDao callRecords = storage.getCallDetailRecordsDao();
                callRecords.removeCallDetailRecord(callRecord.getSid());
            }
        }
        super.postStop();
    }

    private final class CreatingBridge extends AbstractAction {

        public CreatingBridge(ActorRef source) {
            super(source);
        }

        @Override
        public void execute(Object message) throws Exception {
            final CreateBridge create = new CreateBridge(outboundCallInfo.mediaAttributes());
            bridgeManager.tell(create, super.source);
        }

    }

    private final class InitializingBridge extends AbstractAction {

        public InitializingBridge(ActorRef source) {
            super(source);
        }

        @Override
        public void execute(Object message) throws Exception {
            // Start monitoring bridge state changes
            final Observe observe = new Observe(super.source);
            bridge.tell(observe, super.source);

            // Initialize bridge
            final StartBridge start = new StartBridge();
            bridge.tell(start, super.source);
        }

    }

    private final class Bridging extends AbstractAction {

        public Bridging(ActorRef source) {
            super(source);
        }

        private ActorRef buildSubVoiceInterpreter(Tag child) throws MalformedURLException, URISyntaxException {
//            URI url = new URL(child.attribute("url").value()).toURI();
            URI url = null;
            if (request != null) {
                final URI base = request.getUri();
                url = uriUtils.resolveWithBase(base, new URI(child.attribute("url").value()));
            } else {
                url = uriUtils.resolve(new URI(child.attribute("url").value()), accountId);
            }
            String method;
            if (child.hasAttribute("method")) {
                method = child.attribute("method").value().toUpperCase();
            } else {
                method = "POST";
            }

            final SubVoiceInterpreterParams.Builder builder = new SubVoiceInterpreterParams.Builder();
            builder.setConfiguration(configuration);
            builder.setStorage(storage);
            builder.setCallManager(super.source);
            builder.setSmsService(smsService);
            builder.setAccount(accountId);
            builder.setVersion(version);
            builder.setUrl(url);
            builder.setMethod(method);

            final Props props = SubVoiceInterpreter.props(builder.build());
            return getContext().actorOf(props);
        }

        @Override
        public void execute(Object message) throws Exception {
            if(logger.isInfoEnabled()) {
                String msg = String.format("Joining call %s from: %s to: %s with outboundCall %s from: %s to: %s", call, callInfo.from(), callInfo.to(), outboundCall, outboundCallInfo.from(), outboundCallInfo.to());
                logger.info(msg);
            }
            // Check for any Dial verbs with url attributes (call screening url)
            Tag child = dialChildrenWithAttributes.get(outboundCall);
            if (child != null && child.attribute("url") != null) {
                if (logger.isInfoEnabled()) {
                    String msg = String.format("Call screening is enabled and will execute for %s", child.attribute("url"));
                    logger.info(msg);
                }
                final ActorRef interpreter = buildSubVoiceInterpreter(child);
                StartInterpreter start = new StartInterpreter(outboundCall);
                try {
                    Timeout expires = new Timeout(Duration.create(60, TimeUnit.SECONDS));
                    Future<Object> future = (Future<Object>) ask(interpreter, start, expires);
                    Object object = Await.result(future, Duration.create(60, TimeUnit.SECONDS));

                    if (!End.class.equals(object.getClass())) {
                        fsm.transition(message, hangingUp);
                        return;
                    }
                } catch (Exception e) {
                    if(logger.isInfoEnabled()) {
                        logger.info("Exception while trying to execute call screening: "+e);
                    }
                    fsm.transition(message, hangingUp);
                    return;
                }

                // Stop SubVoiceInterpreter
                outboundCall.tell(new StopObserving(interpreter), null);
                getContext().stop(interpreter);
            }

            if (logger.isInfoEnabled()) {
                String msg = String.format("Will ask call %s to stop ringing", call);
                logger.info(msg);
            }
            // Stop ringing from inbound call
            final StopMediaGroup stop = new StopMediaGroup();
            call.tell(stop, super.source);
        }

    }
}
