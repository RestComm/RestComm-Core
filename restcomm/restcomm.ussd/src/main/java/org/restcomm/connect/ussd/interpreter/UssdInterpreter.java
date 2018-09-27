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

package org.restcomm.connect.ussd.interpreter;

import akka.actor.Actor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.actor.UntypedActorContext;
import akka.actor.UntypedActorFactory;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import org.apache.commons.configuration.Configuration;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.message.BasicNameValuePair;
import org.joda.time.DateTime;
import org.restcomm.connect.commons.configuration.RestcommConfiguration;
import org.restcomm.connect.commons.dao.Sid;
import org.restcomm.connect.commons.faulttolerance.RestcommUntypedActor;
import org.restcomm.connect.commons.fsm.Action;
import org.restcomm.connect.commons.fsm.FiniteStateMachine;
import org.restcomm.connect.commons.fsm.State;
import org.restcomm.connect.commons.fsm.Transition;
import org.restcomm.connect.commons.patterns.Observe;
import org.restcomm.connect.commons.telephony.CreateCallType;
import org.restcomm.connect.core.service.RestcommConnectServiceProvider;
import org.restcomm.connect.core.service.util.UriUtils;
import org.restcomm.connect.dao.CallDetailRecordsDao;
import org.restcomm.connect.dao.DaoManager;
import org.restcomm.connect.dao.NotificationsDao;
import org.restcomm.connect.dao.entities.CallDetailRecord;
import org.restcomm.connect.dao.entities.Notification;
import org.restcomm.connect.email.EmailService;
import org.restcomm.connect.email.api.EmailRequest;
import org.restcomm.connect.email.api.Mail;
import org.restcomm.connect.extension.api.ExtensionResponse;
import org.restcomm.connect.extension.api.ExtensionType;
import org.restcomm.connect.extension.api.IExtensionFeatureAccessRequest;
import org.restcomm.connect.extension.api.RestcommExtensionGeneric;
import org.restcomm.connect.extension.controller.ExtensionController;
import org.restcomm.connect.http.client.Downloader;
import org.restcomm.connect.http.client.DownloaderResponse;
import org.restcomm.connect.http.client.HttpRequestDescriptor;
import org.restcomm.connect.http.client.HttpResponseDescriptor;
import org.restcomm.connect.interpreter.StartInterpreter;
import org.restcomm.connect.interpreter.StopInterpreter;
import org.restcomm.connect.interpreter.rcml.Attribute;
import org.restcomm.connect.interpreter.rcml.End;
import org.restcomm.connect.interpreter.rcml.GetNextVerb;
import org.restcomm.connect.interpreter.rcml.Parser;
import org.restcomm.connect.interpreter.rcml.ParserFailed;
import org.restcomm.connect.interpreter.rcml.Tag;
import org.restcomm.connect.telephony.api.Answer;
import org.restcomm.connect.telephony.api.CallInfo;
import org.restcomm.connect.telephony.api.CallResponse;
import org.restcomm.connect.telephony.api.CallStateChanged;
import org.restcomm.connect.telephony.api.FeatureAccessRequest;
import org.restcomm.connect.telephony.api.GetCallInfo;
import org.restcomm.connect.ussd.commons.UssdInfoRequest;
import org.restcomm.connect.ussd.commons.UssdMessageType;
import org.restcomm.connect.ussd.commons.UssdRestcommResponse;

import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;
import javax.servlet.sip.SipSession;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Currency;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.regex.Pattern;

import static org.restcomm.connect.interpreter.rcml.Verbs.ussdCollect;
import static org.restcomm.connect.interpreter.rcml.Verbs.ussdLanguage;
import static org.restcomm.connect.interpreter.rcml.Verbs.ussdMessage;

/**
 * @author <a href="mailto:gvagenas@gmail.com">gvagenas</a>
 */
public class UssdInterpreter extends RestcommUntypedActor {

    // Logger.
    private final LoggingAdapter logger = Logging.getLogger(getContext().system(), this);

    static final int ERROR_NOTIFICATION = 0;
    static final int WARNING_NOTIFICATION = 1;
    static final Pattern PATTERN = Pattern.compile("[\\*#0-9]{1,12}");
    static String EMAIL_SENDER;

    // States for the FSM.
    // ==========================
    final State uninitialized;
    final State observeCall;
    final State acquiringCallInfo;
    final State disconnecting;
    final State cancelling;
    final State finished;

    private final State preparingMessage;
    private final State processingInfoRequest;

    // FSM.
    FiniteStateMachine fsm = null;
    // Information to reach the application that will be executed
    // by this interpreter.
    Sid accountId;
    Sid phoneId;
    String version;

    URI statusCallback;
    String statusCallbackMethod;
    String emailAddress;
    ActorRef ussdCall = null;
    CallInfo callInfo = null;
    // State for outbound calls.
    ActorRef outboundCall = null;
    CallInfo outboundCallInfo = null;

    // The call state.
    CallStateChanged.State callState = null;
    // A call detail record.
    CallDetailRecord callRecord = null;

    ActorRef downloader = null;
    // application data.
    HttpRequestDescriptor request;
    HttpResponseDescriptor response;
    // The RCML parser.
    ActorRef parser;
    Tag verb;
    DaoManager storage = null;
    final Set<Transition> transitions = new HashSet<Transition>();
    ActorRef mailerNotify = null;
    URI url;
    String method;
    URI fallbackUrl;
    String fallbackMethod;

    Tag ussdLanguageTag = null;
    int maxMessageLength;
    private final int englishLength = 182;
    private final int nonEnglishLength = 80;
    Queue<Tag> ussdMessageTags = new LinkedBlockingQueue<Tag>();
    Tag ussdCollectTag = null;
    String ussdCollectAction = "";

    Configuration configuration = null;

    private final State downloadingRcml;
    private final State downloadingFallbackRcml;
    private final State ready;
    private final State notFound;

    private boolean receivedBye;
    private boolean sentBye;

    //List of extensions for UssdInterpreter
    List<RestcommExtensionGeneric> extensions;

    private UriUtils uriUtils;

    public UssdInterpreter(final UssdInterpreterParams params) {
        super();
        final ActorRef source = self();

        uninitialized = new State("uninitialized", null, null);
        observeCall = new State("observe call", new ObserveCall(source), null);
        acquiringCallInfo = new State("acquiring call info", new AcquiringCallInfo(source), null);
        downloadingRcml = new State("downloading rcml", new DownloadingRcml(source), null);
        downloadingFallbackRcml = new State("downloading fallback rcml", new DownloadingFallbackRcml(source), null);
        preparingMessage = new State("Preparing message", new PreparingMessage(source), null);
        processingInfoRequest = new State("Processing info request from client", new ProcessingInfoRequest(source), null);

        ready = new State("ready", new Ready(source), null);
        notFound = new State("notFound", new NotFound(source), null);

        cancelling = new State("Cancelling", new Cancelling(source), null);
        disconnecting = new State("Disconnecting", new Disconnecting(source), null);

        finished = new State("finished", new Finished(source), null);

        transitions.add(new Transition(uninitialized, acquiringCallInfo));
        transitions.add(new Transition(uninitialized, cancelling));
        transitions.add(new Transition(acquiringCallInfo, downloadingRcml));
        transitions.add(new Transition(acquiringCallInfo, cancelling));
        transitions.add(new Transition(downloadingRcml, ready));
        transitions.add(new Transition(downloadingRcml, cancelling));
        transitions.add(new Transition(downloadingRcml, notFound));
        transitions.add(new Transition(downloadingRcml, downloadingFallbackRcml));//??????
        transitions.add(new Transition(downloadingRcml, finished));
        transitions.add(new Transition(downloadingRcml, ready));//redundant
        transitions.add(new Transition(ready, preparingMessage));
        transitions.add(new Transition(preparingMessage, downloadingRcml));
        transitions.add(new Transition(preparingMessage, processingInfoRequest));
        transitions.add(new Transition(preparingMessage, disconnecting));
        transitions.add(new Transition(preparingMessage, finished));
        transitions.add(new Transition(processingInfoRequest, preparingMessage));
        transitions.add(new Transition(processingInfoRequest, ready));
        transitions.add(new Transition(processingInfoRequest, finished));
        transitions.add(new Transition(processingInfoRequest, disconnecting));
        transitions.add(new Transition(processingInfoRequest, cancelling));
        transitions.add(new Transition(processingInfoRequest, notFound));
        transitions.add(new Transition(disconnecting, finished));

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
        this.statusCallback = params.getStatusCallback();
        this.statusCallbackMethod = params.getStatusCallbackMethod();
        this.emailAddress = params.getEmailAddress();
        this.configuration = params.getConfiguration();

        this.storage = params.getStorage();
        final Configuration runtime = configuration.subset("runtime-settings");
        String path = runtime.getString("cache-path");
        if (!path.endsWith("/")) {
            path = path + "/";
        }
        path = path + accountId.toString();
        this.downloader = downloader();

        receivedBye = false;
        sentBye = false;

        extensions = ExtensionController.getInstance().getExtensions(ExtensionType.FeatureAccessControl);

        uriUtils = RestcommConnectServiceProvider.getInstance().uriUtils();
    }

    public static Props props(final UssdInterpreterParams params) {
        return new Props(new UntypedActorFactory() {
            @Override
            public Actor create() throws Exception {
                return new UssdInterpreter(params);
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
        final String base = configuration.subset("runtime-settings").getString("error-dictionary-uri");
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

    ActorRef downloader() {
        final Props props = new Props(new UntypedActorFactory() {
            private static final long serialVersionUID = 1L;
            @Override
            public UntypedActor create() throws Exception {
                return new Downloader();
            }
        });
        return getContext().actorOf(props);
    }

    ActorRef parser(final String xml) {
        final Props props = new Props(new UntypedActorFactory() {
            private static final long serialVersionUID = 1L;

            @Override
            public UntypedActor create() throws Exception {
                return new Parser(xml, self());
            }
        });
        return getContext().actorOf(props);
    }

    void invalidVerb(final Tag verb) {
        final ActorRef self = self();
        // Get the next verb.
        final GetNextVerb next = new GetNextVerb();
        parser.tell(next, self);
    }

    List<NameValuePair> parameters() {
        CallInfo info = null;
        if (callInfo != null) {
            info = callInfo;
        } else if (outboundCallInfo != null){
            info = outboundCallInfo;
        }
        if (info != null) {
            final List<NameValuePair> parameters = new ArrayList<NameValuePair>();
            final String callSid = info.sid().toString();
            parameters.add(new BasicNameValuePair("CallSid", callSid));
            final String accountSid = accountId.toString();
            parameters.add(new BasicNameValuePair("AccountSid", accountSid));
            final String from = (info.from());
            parameters.add(new BasicNameValuePair("From", from));
            final String to = (info.to());
            parameters.add(new BasicNameValuePair("To", to));
            if (callState == null)
                callState = info.state();
            final String state = callState.toString();
            parameters.add(new BasicNameValuePair("CallStatus", state));
            parameters.add(new BasicNameValuePair("ApiVersion", version));
            final String direction = info.direction();
            parameters.add(new BasicNameValuePair("Direction", direction));
            final String callerName = info.fromName();
            parameters.add(new BasicNameValuePair("CallerName", callerName));
            final String forwardedFrom = info.forwardedFrom();
            parameters.add(new BasicNameValuePair("ForwardedFrom", forwardedFrom));
            // logger.info("Type " + callInfo.type());
            if (CreateCallType.SIP == info.type()) {
                // Adding SIP OUT Headers and SipCallId for
                // https://bitbucket.org/telestax/telscale-restcomm/issue/132/implement-twilio-sip-out
                SipServletResponse lastResponse = info.lastResponse();
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
                        Iterator<String> headerIt = lastResponse.getHeaderNames();
                        while (headerIt.hasNext()) {
                            String headerName = headerIt.next();
                            if (headerName.startsWith("X-")) {
                                parameters.add(new BasicNameValuePair("DialSipHeader_" + headerName, lastResponse
                                        .getHeader(headerName)));
                            }
                        }
                    }
                }
            }
            return parameters;
        } else {
            return null;
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

    void callback() {
        if (statusCallback != null) {
            if (statusCallbackMethod == null) {
                statusCallbackMethod = "POST";
            }
            final List<NameValuePair> parameters = parameters();
            request = new HttpRequestDescriptor(statusCallback, statusCallbackMethod, parameters);
            downloader.tell(request, null);
        }
    }

    @Override
    public void onReceive(final Object message) throws Exception {
        final Class<?> klass = message.getClass();
        final State state = fsm.state();
        final ActorRef sender = sender();
        final ActorRef source = self();

        if (logger.isInfoEnabled()) {
            logger.info(" ********** UssdInterpreter's Current State: " + state.toString());
            logger.info(" ********** UssdInterpreter's Processing Message: " + klass.getName());
        }

        if (StartInterpreter.class.equals(klass)) {
            ussdCall = ((StartInterpreter) message).resource();
            fsm.transition(message, acquiringCallInfo);
        } else if (message instanceof SipServletRequest) {
            SipServletRequest request = (SipServletRequest) message;
            String method = request.getMethod();
            if ("INFO".equalsIgnoreCase(method)) {
                fsm.transition(message, processingInfoRequest);
            } else if ("ACK".equalsIgnoreCase(method)) {
                fsm.transition(message, downloadingRcml);
            } else if ("BYE".equalsIgnoreCase(method)) {
                receivedBye = true;
                fsm.transition(message, disconnecting);
            } else if ("CANCEL".equalsIgnoreCase(method)) {
                fsm.transition(message, cancelling);
            }
        } else if (CallStateChanged.class.equals(klass)) {
            final CallStateChanged event = (CallStateChanged) message;
            callState = event.state();
            if (CallStateChanged.State.RINGING == event.state()) {
                if(logger.isInfoEnabled()) {
                    logger.info("CallStateChanged.State.RINGING");
                }
            } else if (CallStateChanged.State.IN_PROGRESS == event.state()) {
                if(logger.isInfoEnabled()) {
                    logger.info("CallStateChanged.State.IN_PROGRESS");
                }
            } else if (CallStateChanged.State.NO_ANSWER == event.state() || CallStateChanged.State.COMPLETED == event.state()
                    || CallStateChanged.State.FAILED == event.state() || CallStateChanged.State.CANCELED == event.state()) {
                if(logger.isInfoEnabled()) {
                    logger.info("CallStateChanged.State.NO_ANSWER OR  CallStateChanged.State.COMPLETED OR CallStateChanged.State.FAILED or CallStateChanged.State.CANCELED");
                }
                fsm.transition(message, finished);
            } else if (CallStateChanged.State.BUSY == event.state()) {
                if(logger.isInfoEnabled()) {
                    logger.info("CallStateChanged.State.BUSY");
                }
            }
//            else if (CallStateChanged.State.COMPLETED == event.state()) {
//                logger.info("CallStateChanged.State.Completed");
//                fsm.transition(message, finished);
//            }
        } else if (CallResponse.class.equals(klass)) {
            if (acquiringCallInfo.equals(state)) {
                @SuppressWarnings("unchecked")
                final CallResponse<CallInfo> response = (CallResponse<CallInfo>) message;
                // Check from whom is the message (initial call or outbound call) and update info accordingly

                if( response.get().direction().contains("inbound") ){
                    callInfo = response.get();
                    ussdCall.tell(new Answer(callInfo.sid()), source);
                    }
                else if ("outbound-api".equals(response.get().direction())){
                         outboundCallInfo = response.get();
                         fsm.transition(message, downloadingRcml);
                }else{
                    logger.debug("direction doesn't match inbound or outbound,  : " + response.get().direction().toString() );
                }

                /* if (sender == ussdCall) {
                    callInfo = response.get();
                } else {
                    outboundCallInfo = response.get();
                }
                final String direction = callInfo.direction();
                if ("inbound".equals(direction)) {
                    ussdCall.tell(new Answer(callInfo.sid()), source);
                    // fsm.transition(message, downloadingRcml);
                } else {
                    fsm.transition(message, downloadingRcml);
                    //                     fsm.transition(message, initializingCall);
                }*/
            }
        } else if (DownloaderResponse.class.equals(klass)) {
            final DownloaderResponse response = (DownloaderResponse) message;
            if (logger.isDebugEnabled()) {
                logger.debug("Rcml DownloaderResponse success=" + response.succeeded());
            }
            //FIXME: what if these ifblocks arent in downloadingRcml?
            if (response.succeeded()) {
                int sc = response.get().getStatusCode();
                //processingInfoRequest
                if (logger.isDebugEnabled()) {
                    logger.debug("Rcml DownloaderResponse URI : " + response.get().getURI()
                        + ", statusCode=" + response.get().getStatusCode()+" state="+state);
                }
                if(HttpStatus.SC_OK == sc){
                    fsm.transition(message, ready);
                } else if (HttpStatus.SC_NOT_FOUND == sc){
                    fsm.transition(message, notFound);
                }
            } else {
                if (logger.isDebugEnabled()) {
                    logger.debug("Rcml DownloaderResponse error : " + response.error()
                        + ", cause=" +
                        ((response.cause() != null) ? response.cause().getMessage() : ""));
                }
                if (downloadingRcml.equals(state) && fallbackUrl!=null) {
                    fsm.transition(message, downloadingFallbackRcml);
                } else {
                    //unexpected response
                    if(!sentBye && !receivedBye){
                        sendBye("UssdInterpreter Stopping. Unexpected State when receiving DownloaderResponse " + response.error());
                        sentBye = true;
                    }
                    fsm.transition(message, finished);
                }
            }
        } else if (ParserFailed.class.equals(klass)) {
            if(logger.isInfoEnabled()) {
                logger.info("ParserFailed received. Will stop the call");
            }
            fsm.transition(message, cancelling);
        } else if (Tag.class.equals(klass)) {
            final Tag verb = (Tag) message;

            ExtensionController ec = ExtensionController.getInstance();
            IExtensionFeatureAccessRequest far = new FeatureAccessRequest(FeatureAccessRequest.Feature.OUTBOUND_USSD, accountId);
            ExtensionResponse er = ec.executePreOutboundAction(far, extensions);

            if (!er.isAllowed()) {
                if (logger.isDebugEnabled()) {
                    final String errMsg = "Outbound USSD session is not Allowed";
                    logger.debug(errMsg);
                }
                String errMsg = "Outbound USSD session is not Allowed";

                final Notification notification = notification(WARNING_NOTIFICATION, 11001, errMsg);
                final NotificationsDao notifications = storage.getNotificationsDao();
                notifications.addNotification(notification);

                sendBye(errMsg);
                ec.executePostOutboundAction(far, extensions);
                return;
            }

            if (ussdLanguage.equals(verb.name())) {
                if (ussdLanguageTag == null) {
                    ussdLanguageTag = verb;
                    final GetNextVerb next = new GetNextVerb();
                    parser.tell(next, source);
                } else {
                    // We support only one Language element
                    invalidVerb(verb);
                }
                return;
            } else if (ussdMessage.equals(verb.name())) {
                ussdMessageTags.add(verb);
                final GetNextVerb next = new GetNextVerb();
                parser.tell(next, source);
                return;
            } else if (ussdCollect.equals(verb.name())) {
                if (ussdCollectTag == null) {
                    ussdCollectTag = verb;
                    final GetNextVerb next = new GetNextVerb();
                    parser.tell(next, source);
                } else {
                    // We support only one Collect element
                    invalidVerb(verb);
                }
                return;
            } else {
                invalidVerb(verb);
            }
        } else if (End.class.equals(klass)) {
            fsm.transition(message, preparingMessage);
        }
    }

    private void sendBye(String message) {
        CallInfo info = null;
        if (callInfo != null) {
            info = callInfo;
        } else if (outboundCallInfo != null){
            info = outboundCallInfo;
        }
        if (info != null) {
            final SipSession session = info.invite().getSession();
            final String sessionState = session.getState().name();

            if (sessionState == SipSession.State.TERMINATED.name()) {
                if (logger.isInfoEnabled()) {
                    logger.info("SipSession already TERMINATED, will not send BYE");
                }
                return;
            } else {
                if (logger.isInfoEnabled()) {
                    logger.info("About to send BYE, session state: " + sessionState);
                }
            }
            final SipServletRequest bye = session.createRequest("BYE");
            try {
                bye.addHeader("Reason",message);
                bye.send();
            } catch (Exception e) {
                if(logger.isErrorEnabled()){
                    logger.error("Error sending BYE "+e.getMessage());
                }
            }

        }
    }

    abstract class AbstractAction implements Action {
        protected final ActorRef source;

        public AbstractAction(final ActorRef source) {
            super();
            this.source = source;
        }
    }

    final class ObserveCall extends AbstractAction {
        public ObserveCall(final ActorRef source) {
            super(source);
        }

        @Override
        public void execute(final Object message) throws Exception {
            ussdCall.tell(new Observe(source), source);
        }
    }

    final class AcquiringCallInfo extends AbstractAction {
        public AcquiringCallInfo(final ActorRef source) {
            super(source);
        }

        @Override
        public void execute(final Object message) throws Exception {
            if(logger.isInfoEnabled()) {
                logger.info("Acquiring Call Info");
            }
            ussdCall.tell(new Observe(source), source);
            ussdCall.tell(new GetCallInfo(), source);
        }
    }

    private final class DownloadingRcml extends AbstractAction {
        public DownloadingRcml(final ActorRef source) {
            super(source);
        }

        @SuppressWarnings("unchecked")
        @Override
        public void execute(final Object message) throws Exception {
            if (callInfo != null) {
                final CallDetailRecordsDao records = storage.getCallDetailRecordsDao();
                final CallDetailRecord.Builder builder = CallDetailRecord.builder();
                builder.setSid(callInfo.sid());
                builder.setInstanceId(RestcommConfiguration.getInstance().getMain().getInstanceId());
                builder.setDateCreated(callInfo.dateCreated());
                builder.setAccountSid(accountId);
                builder.setTo(callInfo.to());
                builder.setCallerName(callInfo.fromName());
                builder.setFrom(callInfo.from());
                builder.setForwardedFrom(callInfo.forwardedFrom());
                builder.setPhoneNumberSid(phoneId);
                builder.setStatus(callState.toString());
                final DateTime now = DateTime.now();
                builder.setStartTime(now);
                builder.setDirection(callInfo.direction());
                builder.setApiVersion(version);
                builder.setPrice(new BigDecimal("0.00"));
                // TODO implement currency property to be read from Configuration
                builder.setPriceUnit(Currency.getInstance("USD"));
                final StringBuilder buffer = new StringBuilder();
                buffer.append("/").append(version).append("/Accounts/");
                buffer.append(accountId.toString()).append("/Calls/");
                buffer.append(callInfo.sid().toString());
                final URI uri = URI.create(buffer.toString());
                builder.setUri(uri);
                builder.setCallPath(ussdCall.path().toString());
                callRecord = builder.build();
                records.addCallDetailRecord(callRecord);
            }
                    // Update the application.
                    callback();
            // Ask the downloader to get us the application that will be executed.
            final List<NameValuePair> parameters = parameters();
            request = new HttpRequestDescriptor(url, method, parameters);
            downloader.tell(request, source);
        }

    }

    private final class DownloadingFallbackRcml extends AbstractAction {
        public DownloadingFallbackRcml(final ActorRef source) {
            super(source);
        }

        @Override
        public void execute(final Object message) throws Exception {
            if(logger.isInfoEnabled()) {
                logger.info("Downloading Fallback RCML");
            }
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
        public void execute(final Object message) throws Exception {
            if(logger.isInfoEnabled()) {
                logger.info("In Ready state");
            }
            // ussdCall.tell(new Answer(), source);
            // Execute the received RCML here
            final UntypedActorContext context = getContext();
            final State state = fsm.state();
            if (downloadingRcml.equals(state) || downloadingFallbackRcml.equals(state) || processingInfoRequest.equals(state)) {
                response = ((DownloaderResponse) message).get();
                if (parser != null) {
                    context.stop(parser);
                    parser = null;
                }
                final String type = response.getContentType();
                if (type.contains("text/xml") || type.contains("application/xml") || type.contains("text/html")) {
                    parser = parser(response.getContentAsString());
                } else if (type.contains("text/plain")) {
                    parser = parser("<UssdMessage>" + response.getContentAsString() + "</UssdMessage>");
                } else {
                    final StopInterpreter stop = new StopInterpreter();
                    source.tell(stop, source);
                    return;
                }
            }
            final GetNextVerb next = new GetNextVerb();
            parser.tell(next, source);
        }
    }

    private final class NotFound extends AbstractAction {
        public NotFound(final ActorRef source) {
            super(source);
        }

        @Override
        public void execute(final Object message) throws Exception {
            if(logger.isInfoEnabled()) {
                logger.info("In Not Found State");
            }
            final DownloaderResponse response = (DownloaderResponse) message;
            if (logger.isDebugEnabled()) {
                logger.debug("response succeeded " + response.succeeded() + ", statusCode " + response.get().getStatusCode());
            }
            final Notification notification = notification(WARNING_NOTIFICATION, 21402, "URL Not Found : "
                    + response.get().getURI());
            final NotificationsDao notifications = storage.getNotificationsDao();
            notifications.addNotification(notification);
            // Hang up the call.
            ussdCall.tell(new org.restcomm.connect.telephony.api.NotFound(), source);
        }
    }

    // RCML END received, construct the USSD Message and ask UssdCall to send it
    private final class PreparingMessage extends AbstractAction {
        public PreparingMessage(final ActorRef source) {
            super(source);
        }

        @Override
        public void execute(final Object message) throws Exception {
            if(logger.isInfoEnabled()) {
                logger.info("Preparing the USSD Message");
            }
            if (End.class.equals(message.getClass())) {

                Boolean hasCollect = false;
                UssdRestcommResponse ussdRestcommResponse = new UssdRestcommResponse();

                String language = "";
                if (ussdLanguageTag == null) {
                    language = "en";
                    ussdRestcommResponse.setLanguage(language);
                } else {
                    language = ussdLanguageTag.text();
                    ussdRestcommResponse.setLanguage(language);
                }

                if (language.equalsIgnoreCase("en")) {
                    maxMessageLength = englishLength;
                    ussdRestcommResponse.setMessageLength(englishLength);
                } else {
                    maxMessageLength = nonEnglishLength;
                    ussdRestcommResponse.setMessageLength(nonEnglishLength);
                }

                StringBuffer ussdText = processUssdMessageTags(ussdMessageTags);

                if (ussdCollectTag != null) {
                    hasCollect = true;
                    ussdCollectAction = ussdCollectTag.attribute("action").value();
                    ussdRestcommResponse.setUssdCollectAction(ussdCollectAction);
                    Queue<Tag> children = new java.util.concurrent.ConcurrentLinkedQueue<Tag>(ussdCollectTag.children());
                    if (children != null && children.size() > 0) {
                        ussdText.append(processUssdMessageTags(children));
                    } else if (ussdCollectTag.text() != null) {
                        ussdText.append(ussdCollectTag.text());
                    }
                }

                if (ussdText.length() > maxMessageLength) {
                    final String errorString = "Error while preparing the USSD response. Ussd text length more "
                            + "than the permitted for the selected language: "+maxMessageLength;
                    Notification notification = notification(ERROR_NOTIFICATION, 11100, errorString);
                    if (notification != null) {
                        final NotificationsDao notifications = storage.getNotificationsDao();
                        notifications.addNotification(notification);
                        sendMail(notification);
                    }
                    if(logger.isInfoEnabled()) {
                        logger.info(errorString);
                    }
                    ussdText = new StringBuffer();
                    ussdText.append("Error while preparing the response.\nMessage length exceeds the maximum.");
                }

                ussdRestcommResponse.setMessage(ussdText.toString());

                if(logger.isInfoEnabled()) {
                    logger.info("UssdMessage prepared, hasCollect: " + hasCollect);
                    logger.info("UssdMessage prepared: " + ussdMessage.toString() + " hasCollect: " + hasCollect);
                }

                if (callInfo != null && callInfo.direction().equalsIgnoreCase("inbound")) {
                    // USSD PULL
                    if (hasCollect) {
                        ussdRestcommResponse.setMessageType(UssdMessageType.unstructuredSSRequest_Request);
                        ussdRestcommResponse.setIsFinalMessage(false);
                    } else {
                        ussdRestcommResponse.setMessageType(UssdMessageType.processUnstructuredSSRequest_Response);
                        ussdRestcommResponse.setIsFinalMessage(true);
                    }
                } else {
                    //USSD PUSH
                    if (hasCollect) {
                        ussdRestcommResponse.setMessageType(UssdMessageType.unstructuredSSRequest_Request);
                        ussdRestcommResponse.setIsFinalMessage(false);
                    } else {
                        ussdRestcommResponse.setMessageType(UssdMessageType.unstructuredSSNotify_Request);
                        if (ussdRestcommResponse.getErrorCode() != null) {
                            ussdRestcommResponse.setIsFinalMessage(true);
                        } else {
                            ussdRestcommResponse.setIsFinalMessage(false);
                        }
                    }
                }
                if(logger.isInfoEnabled()) {
                    logger.info("UssdRestcommResponse message prepared: "+ussdRestcommResponse);
                }
                ussdCall.tell(ussdRestcommResponse, source);
            }
        }
    }

    private StringBuffer processUssdMessageTags(Queue<Tag> messageTags) {
        StringBuffer message = new StringBuffer();
        while (!messageTags.isEmpty()) {
            Tag tag = messageTags.poll();
            if (tag != null) {
                message.append(tag.text());
                if (!messageTags.isEmpty())
                    message.append("\n");
            } else {
                return message;
            }
        }
        return message;
    }

    private final class ProcessingInfoRequest extends AbstractAction {
        public ProcessingInfoRequest(final ActorRef source) {
            super(source);
        }

        @Override
        public void execute(final Object message) throws Exception {
            if(logger.isInfoEnabled()) {
                logger.info("UssdInterpreter Processing INFO request");
            }
            final NotificationsDao notifications = storage.getNotificationsDao();
            SipServletRequest info = (SipServletRequest) message;

            SipServletResponse okay = info.createResponse(200);
            okay.send();

            UssdInfoRequest ussdInfoRequest = new UssdInfoRequest(info);
            String ussdText = ussdInfoRequest.getMessage();
            UssdMessageType ussdMsgType = ussdInfoRequest.getUssdMessageType();
            if ( !ussdMsgType.equals(UssdMessageType.unstructuredSSNotify_Response) &&
                  ussdCollectAction != null &&
                  !ussdCollectAction.isEmpty() &&
                  ussdText != null) {
                URI target = null;
                try {
                    target = URI.create(ussdCollectAction);
                } catch (final Exception exception) {
                    final Notification notification = notification(ERROR_NOTIFICATION, 11100, ussdCollectAction
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
                Attribute attribute = null;
                try {
                    attribute = verb.attribute("method");
                } catch (Exception e) {}
                if (attribute != null) {
                    method = attribute.value();
                    if (method != null && !method.isEmpty()) {
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

                final List<NameValuePair> parameters = parameters();
                parameters.add(new BasicNameValuePair("Digits", ussdText));
                request = new HttpRequestDescriptor(uri, method, parameters);
                downloader.tell(request, source);
                ussdCollectTag = null;
                ussdLanguageTag = null;
                ussdMessageTags = new LinkedBlockingQueue<Tag>();
                return;
            } else if (ussdMsgType.equals(UssdMessageType.unstructuredSSNotify_Response)) {
                UssdRestcommResponse ussdRestcommResponse = new UssdRestcommResponse();
                ussdRestcommResponse.setErrorCode("1");
                ussdRestcommResponse.setIsFinalMessage(true);
                ussdCall.tell(ussdRestcommResponse, source);
                return;
            }
            // Ask the parser for the next action to take.
            final GetNextVerb next = new GetNextVerb();
            parser.tell(next, self());
        }
    }

    private final class Cancelling extends AbstractAction {
        public Cancelling(final ActorRef source) {
            super(source);
        }

        @Override
        public void execute(final Object message) throws Exception {
            if(logger.isInfoEnabled()) {
                logger.info("Cancelling state");
            }
            final Class<?> klass = message.getClass();
            if (message instanceof SipServletRequest) {
                SipServletRequest request = (SipServletRequest)message;
                if (ussdCall != null)
                    ussdCall.tell(request, self());
                if (outboundCall != null)
                    ussdCall.tell(request, self());
            }
        }
    }

    private final class Disconnecting extends AbstractAction {
        public Disconnecting(final ActorRef source) {
            super(source);
        }

        @Override
        public void execute(final Object message) throws Exception {
            if(logger.isInfoEnabled()) {
                logger.info("Disconnecting state");
            }
            final Class<?> klass = message.getClass();
            if (message instanceof SipServletRequest) {
                SipServletRequest request = (SipServletRequest)message;
                if (ussdCall != null)
                    ussdCall.tell(request, self());
                if (outboundCall != null)
                    ussdCall.tell(request, self());
            }
        }
    }

    private final class Finished extends AbstractAction {
        public Finished(final ActorRef source) {
            super(source);
        }

        @Override
        public void execute(final Object message) throws Exception {
            if(logger.isInfoEnabled()) {
                logger.info("In Finished state");
            }
            final Class<?> klass = message.getClass();
            if (CallStateChanged.class.equals(klass)) {
                final CallStateChanged event = (CallStateChanged) message;
                callState = event.state();
                if (callRecord != null) {
                    callRecord = callRecord.setStatus(callState.toString());
                    final DateTime end = DateTime.now();
                    callRecord = callRecord.setEndTime(end);
                    final int seconds = (int) (end.getMillis() - callRecord.getStartTime().getMillis()) / 1000;
                    callRecord = callRecord.setDuration(seconds);
                    final CallDetailRecordsDao records = storage.getCallDetailRecordsDao();
                    records.updateCallDetailRecord(callRecord);
                }
                callback();
            }
            context().stop(self());
        }
    }

    @Override
    public void postStop() {
        if(logger.isInfoEnabled()) {
            logger.info("UssdInterpreter postStop");
        }
        if (ussdCall != null)
            getContext().stop(ussdCall);
        if (outboundCall != null)
            getContext().stop(outboundCall);
        if (downloader != null)
            getContext().stop(downloader);
        if (parser != null)
            getContext().stop(parser);
        if (mailerNotify != null)
            getContext().stop(mailerNotify);
        super.postStop();
    }

}
