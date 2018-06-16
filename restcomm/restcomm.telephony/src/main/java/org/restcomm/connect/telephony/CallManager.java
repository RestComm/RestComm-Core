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
package org.restcomm.connect.telephony;

import static akka.pattern.Patterns.ask;
import static javax.servlet.sip.SipServlet.OUTBOUND_INTERFACES;
import static javax.servlet.sip.SipServletResponse.SC_ACCEPTED;
import static javax.servlet.sip.SipServletResponse.SC_BAD_REQUEST;
import static javax.servlet.sip.SipServletResponse.SC_FORBIDDEN;
import static javax.servlet.sip.SipServletResponse.SC_NOT_FOUND;
import static javax.servlet.sip.SipServletResponse.SC_OK;
import static javax.servlet.sip.SipServletResponse.SC_SERVER_INTERNAL_ERROR;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import javax.sdp.SdpParseException;
import javax.servlet.ServletContext;
import javax.servlet.sip.Address;
import javax.servlet.sip.AuthInfo;
import javax.servlet.sip.ServletParseException;
import javax.servlet.sip.SipApplicationSession;
import javax.servlet.sip.SipApplicationSessionEvent;
import javax.servlet.sip.SipFactory;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;
import javax.servlet.sip.SipSession;
import javax.servlet.sip.SipURI;
import javax.servlet.sip.TelURL;
import javax.sip.header.RouteHeader;
import javax.sip.message.Response;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.joda.time.DateTime;
import org.restcomm.connect.commons.configuration.RestcommConfiguration;
import org.restcomm.connect.commons.configuration.sets.RcmlserverConfigurationSet;
import org.restcomm.connect.commons.dao.Sid;
import org.restcomm.connect.commons.faulttolerance.RestcommUntypedActor;
import org.restcomm.connect.commons.patterns.StopObserving;
import org.restcomm.connect.commons.push.PushNotificationServerHelper;
import org.restcomm.connect.commons.telephony.CreateCallType;
import org.restcomm.connect.commons.telephony.ProxyRule;
import org.restcomm.connect.commons.util.DNSUtils;
import org.restcomm.connect.commons.util.SdpUtils;
import org.restcomm.connect.commons.util.UriUtils;
import org.restcomm.connect.core.service.api.NumberSelectorService;
import org.restcomm.connect.core.service.number.api.NumberSelectionResult;
import org.restcomm.connect.dao.AccountsDao;
import org.restcomm.connect.dao.ApplicationsDao;
import org.restcomm.connect.dao.CallDetailRecordsDao;
import org.restcomm.connect.dao.ClientsDao;
import org.restcomm.connect.dao.DaoManager;
import org.restcomm.connect.dao.NotificationsDao;
import org.restcomm.connect.dao.RegistrationsDao;
import org.restcomm.connect.dao.common.OrganizationUtil;
import org.restcomm.connect.dao.entities.Account;
import org.restcomm.connect.dao.entities.Application;
import org.restcomm.connect.dao.entities.CallDetailRecord;
import org.restcomm.connect.dao.entities.Client;
import org.restcomm.connect.dao.entities.IncomingPhoneNumber;
import org.restcomm.connect.dao.entities.Notification;
import org.restcomm.connect.dao.entities.Organization;
import org.restcomm.connect.dao.entities.Registration;
import org.restcomm.connect.extension.api.ExtensionResponse;
import org.restcomm.connect.extension.api.ExtensionType;
import org.restcomm.connect.extension.api.IExtensionCreateCallRequest;
import org.restcomm.connect.extension.api.IExtensionFeatureAccessRequest;
import org.restcomm.connect.extension.api.RestcommExtensionException;
import org.restcomm.connect.extension.api.RestcommExtensionGeneric;
import org.restcomm.connect.extension.controller.ExtensionController;
import org.restcomm.connect.http.client.rcmlserver.resolver.RcmlserverResolver;
import org.restcomm.connect.interpreter.SIPOrganizationUtil;
import org.restcomm.connect.interpreter.StartInterpreter;
import org.restcomm.connect.interpreter.StopInterpreter;
import org.restcomm.connect.interpreter.VoiceInterpreter;
import org.restcomm.connect.interpreter.VoiceInterpreterParams;
import org.restcomm.connect.monitoringservice.MonitoringService;
import org.restcomm.connect.mscontrol.api.MediaServerControllerFactory;
import org.restcomm.connect.telephony.api.CallInfo;
import org.restcomm.connect.telephony.api.CallManagerResponse;
import org.restcomm.connect.telephony.api.CallResponse;
import org.restcomm.connect.telephony.api.CallStateChanged;
import org.restcomm.connect.telephony.api.CreateCall;
import org.restcomm.connect.telephony.api.DestroyCall;
import org.restcomm.connect.telephony.api.ExecuteCallScript;
import org.restcomm.connect.telephony.api.FeatureAccessRequest;
import org.restcomm.connect.telephony.api.GetActiveProxy;
import org.restcomm.connect.telephony.api.GetCall;
import org.restcomm.connect.telephony.api.GetCallInfo;
import org.restcomm.connect.telephony.api.GetCallObservers;
import org.restcomm.connect.telephony.api.GetProxies;
import org.restcomm.connect.telephony.api.GetRelatedCall;
import org.restcomm.connect.telephony.api.Hangup;
import org.restcomm.connect.telephony.api.InitializeOutbound;
import org.restcomm.connect.telephony.api.SwitchProxy;
import org.restcomm.connect.telephony.api.UpdateCallScript;
import org.restcomm.connect.telephony.api.util.B2BUAHelper;
import org.restcomm.connect.telephony.api.util.CallControlHelper;

import com.google.i18n.phonenumbers.NumberParseException;

import akka.actor.ActorContext;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.actor.ReceiveTimeout;
import akka.actor.UntypedActor;
import akka.actor.UntypedActorContext;
import akka.actor.UntypedActorFactory;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.util.Timeout;
import gov.nist.javax.sip.header.UserAgent;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;

/**
 * @author quintana.thomas@gmail.com (Thomas Quintana)
 * @author ivelin.ivanov@telestax.com
 * @author jean.deruelle@telestax.com
 * @author gvagenas@telestax.com
 * @author maria.farooq@telestax.com
 */
public final class CallManager extends RestcommUntypedActor {

    static final int ERROR_NOTIFICATION = 0;
    static final int WARNING_NOTIFICATION = 1;
    static final Pattern PATTERN = Pattern.compile("[\\*#0-9]{1,12}");
    static final String EMAIL_SENDER = "restcomm@restcomm.org";
    static final String EMAIL_SUBJECT = "RestComm Error Notification - Attention Required";
    static final int DEFAUL_IMS_PROXY_PORT = -1;

    static final int ACCOUNT_NOT_ACTIVE_FAILURE_RESPONSE_CODE = SC_FORBIDDEN;

    private final ActorSystem system;
    private final Configuration configuration;
    private final ServletContext context;
    private final MediaServerControllerFactory msControllerFactory;
    private final ActorRef conferences;
    private final ActorRef bridges;
    private final ActorRef sms;
    private final SipFactory sipFactory;
    private final DaoManager storage;
    private final ActorRef monitoring;
    private final NumberSelectorService numberSelector;

    // configurable switch whether to use the To field in a SIP header to determine the callee address
    // alternatively the Request URI can be used
    private boolean useTo;
    private boolean authenticateUsers;

    private AtomicInteger numberOfFailedCalls;
    private AtomicBoolean useFallbackProxy;
    private boolean allowFallback;
    private boolean allowFallbackToPrimary;
    private int maxNumberOfFailedCalls;

    private String primaryProxyUri;
    private String primaryProxyUsername, primaryProxyPassword;
    private String fallBackProxyUri;
    private String fallBackProxyUsername, fallBackProxyPassword;
    private String activeProxy;
    private String activeProxyUsername, activeProxyPassword;
    private String mediaExternalIp;
    private String myHostIp;
    private String proxyIp;

    private final LoggingAdapter logger = Logging.getLogger(getContext().system(), this);
    private SwitchProxy switchProxyRequest;

    //Control whether Restcomm will patch Request-URI and SDP for B2BUA calls
    private boolean patchForNatB2BUASessions;
    private boolean useSbc;

    //List of extensions for CallManager
    List<RestcommExtensionGeneric> extensions;

    // IMS authentication
    private boolean actAsImsUa;
    private String imsProxyAddress;
    private int imsProxyPort;
    private String imsDomain;
    private String imsAccount;

    private boolean actAsProxyOut;
    private List<ProxyRule> proxyOutRules;

    private boolean isActAsProxyOutUseFromHeader;

    // Push notification server
    private final PushNotificationServerHelper pushNotificationServerHelper;

    // used for sending warning and error logs to notification engine and to the console
    private void sendNotification(Sid accountId, String errMessage, int errCode, String errType, boolean createNotification) {
        NotificationsDao notifications = storage.getNotificationsDao();
        Notification notification;

        if (errType == "warning") {
            if (logger.isDebugEnabled()) {
                // https://github.com/RestComm/Restcomm-Connect/issues/1419 moved to debug to avoid polluting logs
                logger.debug(errMessage); // send message to console
            }
            if (createNotification) {
                notification = notification(accountId, ERROR_NOTIFICATION, errCode, errMessage);
                notifications.addNotification(notification);
            }
        } else if (errType == "error") {
            // https://github.com/RestComm/Restcomm-Connect/issues/1419 moved to debug to avoid polluting logs
            if (logger.isDebugEnabled()) {
                logger.debug(errMessage); // send message to console
            }
            if (createNotification) {
                notification = notification(accountId, ERROR_NOTIFICATION, errCode, errMessage);
                notifications.addNotification(notification);
            }
        } else if (errType == "info") {
            // https://github.com/RestComm/Restcomm-Connect/issues/1419 moved to debug to avoid polluting logs
            if (logger.isDebugEnabled()) {
                logger.debug(errMessage); // send message to console
            }
        }

    }

    public CallManager(final Configuration configuration, final ServletContext context,
                       final MediaServerControllerFactory msControllerFactory, final ActorRef conferences, final ActorRef bridges,
                       final ActorRef sms, final SipFactory factory, final DaoManager storage) {
        super();
        this.system = context().system();
        this.configuration = configuration;
        this.context = context;
        this.msControllerFactory = msControllerFactory;
        this.conferences = conferences;
        this.bridges = bridges;
        this.sms = sms;
        this.sipFactory = factory;
        this.storage = storage;
        numberSelector = (NumberSelectorService)context.getAttribute(NumberSelectorService.class.getName());
        final Configuration runtime = configuration.subset("runtime-settings");
        final Configuration outboundProxyConfig = runtime.subset("outbound-proxy");
        SipURI outboundIntf = outboundInterface("udp");
        if (outboundIntf != null) {
            myHostIp = ((SipURI) outboundIntf).getHost().toString();
        } else {
            String errMsg = "SipURI outboundIntf is null";
            sendNotification(null, errMsg, 14001, "error", false);

            if (context == null)
                errMsg = "SipServlet context is null";
            sendNotification(null, errMsg, 14002, "error", false);
        }
        Configuration mediaConf = configuration.subset("media-server-manager");
        mediaExternalIp = mediaConf.getString("mgcp-server.external-address");
        proxyIp = runtime.subset("telestax-proxy").getString("uri").replaceAll("http://", "").replaceAll(":2080", "");

        if (mediaExternalIp == null || mediaExternalIp.isEmpty())
            mediaExternalIp = myHostIp;

        if (proxyIp == null || proxyIp.isEmpty())
            proxyIp = myHostIp;

        this.useTo = runtime.getBoolean("use-to");
        this.authenticateUsers = runtime.getBoolean("authenticate");

        this.primaryProxyUri = outboundProxyConfig.getString("outbound-proxy-uri");
        this.primaryProxyUsername = outboundProxyConfig.getString("outbound-proxy-user");
        this.primaryProxyPassword = outboundProxyConfig.getString("outbound-proxy-password");

        this.fallBackProxyUri = outboundProxyConfig.getString("fallback-outbound-proxy-uri");
        this.fallBackProxyUsername = outboundProxyConfig.getString("fallback-outbound-proxy-user");
        this.fallBackProxyPassword = outboundProxyConfig.getString("fallback-outbound-proxy-password");

        this.activeProxy = primaryProxyUri;
        this.activeProxyUsername = primaryProxyUsername;
        this.activeProxyPassword = primaryProxyPassword;

        numberOfFailedCalls = new AtomicInteger();
        numberOfFailedCalls.set(0);
        useFallbackProxy = new AtomicBoolean();
        useFallbackProxy.set(false);

        allowFallback = outboundProxyConfig.getBoolean("allow-fallback", false);

        maxNumberOfFailedCalls = outboundProxyConfig.getInt("max-failed-calls", 20);

        allowFallbackToPrimary = outboundProxyConfig.getBoolean("allow-fallback-to-primary", false);

        patchForNatB2BUASessions = runtime.getBoolean("patch-for-nat-b2bua-sessions", true);
        useSbc = runtime.getBoolean("use-sbc", false);
        if(useSbc) {
            if (logger.isDebugEnabled()) {
                logger.debug("CallManager: use-sbc is true, overriding patch-for-nat-b2bua-sessions to false");
            }
            patchForNatB2BUASessions = false;
        }

        //Monitoring Service
        this.monitoring = (ActorRef) context.getAttribute(MonitoringService.class.getName());

        extensions = ExtensionController.getInstance().getExtensions(ExtensionType.CallManager);
        if (logger.isInfoEnabled()) {
            logger.info("CallManager extensions: " + (extensions != null ? extensions.size() : "0"));
        }
        if (!runtime.subset("ims-authentication").isEmpty()) {
            final Configuration imsAuthentication = runtime.subset("ims-authentication");
            this.actAsImsUa = imsAuthentication.getBoolean("act-as-ims-ua");
            if (actAsImsUa) {
                this.imsProxyAddress = imsAuthentication.getString("proxy-address");
                this.imsProxyPort = imsAuthentication.getInt("proxy-port");
                if (imsProxyPort == 0) {
                    imsProxyPort = DEFAUL_IMS_PROXY_PORT;
                }
                this.imsDomain = imsAuthentication.getString("domain");
                this.imsAccount = imsAuthentication.getString("account");
                if (actAsImsUa && (imsProxyAddress == null || imsProxyAddress.isEmpty()
                        || imsDomain == null || imsDomain.isEmpty())) {
                    logger.warning("ims proxy-address or domain is not configured");
                }
                this.actAsImsUa = actAsImsUa && imsProxyAddress != null && !imsProxyAddress.isEmpty()
                        && imsDomain != null && !imsDomain.isEmpty();
            }
        }
        if (!runtime.subset("acting-as-proxy").isEmpty() && !runtime.subset("acting-as-proxy").subset("proxy-rules").isEmpty()) {
            final Configuration proxyConfiguration = runtime.subset("acting-as-proxy");
            final Configuration proxyOutRulesConf = proxyConfiguration.subset("proxy-rules");
            this.actAsProxyOut = proxyConfiguration.getBoolean("enabled", false);
            if (actAsProxyOut) {
                isActAsProxyOutUseFromHeader = proxyConfiguration.getBoolean("use-from-header", true);
                proxyOutRules = new ArrayList<ProxyRule>();

                List<HierarchicalConfiguration> rulesList = ((HierarchicalConfiguration) proxyOutRulesConf).configurationsAt("rule");
                for (HierarchicalConfiguration rule : rulesList) {
                    String fromHost = rule.getString("from-uri");
                    String toHost = rule.getString("to-uri");
                    final String username = rule.getString("proxy-to-username");
                    final String password = rule.getString("proxy-to-password");
                    ProxyRule proxyRule = new ProxyRule(fromHost, toHost, username, password);
                    proxyOutRules.add(proxyRule);
                }

                if (logger.isInfoEnabled()) {
                    String msg = String.format("`ActAsProxy` feature is enabled with %d rules.", proxyOutRules.size());
                    logger.info(msg);
                }

                actAsProxyOut = actAsProxyOut && (proxyOutRules != null) && !proxyOutRules.isEmpty();
            }
        }

        // Push notification server
        this.pushNotificationServerHelper = new PushNotificationServerHelper(system, configuration);

        firstTimeCleanup();
    }

    private void firstTimeCleanup() {
        if (logger.isInfoEnabled())
            logger.info("Initial CallManager cleanup. Will check running state calls in DB and update state of the calls.");
        String instanceId = RestcommConfiguration.getInstance().getMain().getInstanceId();
        Sid sid = new Sid(instanceId);
        final CallDetailRecordsDao callDetailRecordsDao = storage.getCallDetailRecordsDao();
        callDetailRecordsDao.updateInCompleteCallDetailRecordsToCompletedByInstanceId(sid);
        List<CallDetailRecord> results = callDetailRecordsDao.getInCompleteCallDetailRecordsByInstanceId(sid);
        if (logger.isInfoEnabled()) {
            logger.info("There are: " + results.size() + " calls in progress after cleanup.");
        }
    }

    private ActorRef call(final Sid accountSid, final CreateCall request) {
        Props props = null;
        if (request == null) {
            props = new Props(new UntypedActorFactory() {
                private static final long serialVersionUID = 1L;

                @Override
                public UntypedActor create() throws Exception {
                    return new Call(accountSid, sipFactory, msControllerFactory, configuration,
                            null, null, null, null);
                }
            });
        } else {
            props = new Props(new UntypedActorFactory() {
                private static final long serialVersionUID = 1L;

                @Override
                public UntypedActor create() throws Exception {
                    return new Call(accountSid, sipFactory, msControllerFactory, configuration,
                            request.statusCallback(), request.statusCallbackMethod(), request.statusCallbackEvent(), request.getOutboundProxyHeaders());
                }
            });
        }
        return getContext().actorOf(props);
    }

    private boolean check(final Object message) throws IOException {
        final SipServletRequest request = (SipServletRequest) message;
        String content = null;
        if (request.getRawContent() != null) {
            content = new String(request.getRawContent());
        }
        if (content == null && request.getContentLength() == 0
                || !("application/sdp".equals(request.getContentType()) || content.contains("application/sdp"))) {
            final SipServletResponse response = request.createResponse(SC_BAD_REQUEST);
            response.send();
            return false;
        }
        return true;
    }

    private void destroy(final Object message) throws Exception {
        final UntypedActorContext context = getContext();
        final DestroyCall request = (DestroyCall) message;
        ActorRef call = request.call();
        if (call != null) {
            if (logger.isInfoEnabled()) {
                logger.info("About to destroy call: " + request.call().path() + ", call isTerminated(): " + sender().isTerminated() + ", sender: " + sender());
            }
            getContext().stop(call);
        }
    }

    private void rejectInvite(final SipServletRequest request) throws IOException {
        final SipServletResponse response = request.createResponse(ACCOUNT_NOT_ACTIVE_FAILURE_RESPONSE_CODE,
                "Account is not ACTIVE");
        response.send();
    }

    private void invite(final Object message) throws IOException, NumberParseException, ServletParseException {
        final ActorRef self = self();
        final SipServletRequest request = (SipServletRequest) message;

        // Make sure we handle re-invites properly.
        if (!request.isInitial()) {
            SipApplicationSession appSession = request.getApplicationSession();
            ActorRef call = null;
            if (appSession.getAttribute(Call.class.getName()) != null) {
                call = (ActorRef) appSession.getAttribute(Call.class.getName());
            }
            if (call != null) {
                if (logger.isInfoEnabled()) {
                    logger.info("For In-Dialog INVITE dispatched to Call actor: " + call.path());
                }
                call.tell(request, self);
                return;
            }

            if (logger.isInfoEnabled()) {
                logger.info("No call actor found will respond 200OK for In-dialog INVITE: " + request.getRequestURI().toString());
            }
            final SipServletResponse okay = request.createResponse(SC_OK);
            okay.send();
            return;
        }

        if (actAsImsUa) {
            boolean isFromIms = isFromIms(request);
            if (!isFromIms) {
                //This is a WebRTC client that dials out to IMS
                String user = request.getHeader("X-RestComm-Ims-User");
                String pass = request.getHeader("X-RestComm-Ims-Password");
                request.removeHeader("X-RestComm-Ims-User");
                request.removeHeader("X-RestComm-Ims-Password");
                imsProxyThroughMediaServer(request, null, request.getTo().getURI(), user, pass, isFromIms);
                return;
            } else {
                //This is a IMS that dials out to WebRTC client
                imsProxyThroughMediaServer(request, null, request.getTo().getURI(), "", "", isFromIms);
                return;
            }
        }

        //Run proInboundAction Extensions here
        // If it's a new invite lets try to handle it.
        final AccountsDao accounts = storage.getAccountsDao();
        final ApplicationsDao applications = storage.getApplicationsDao();
        final ClientsDao clients = storage.getClientsDao();

        // Try to find an application defined for the client.
        final SipURI fromUri = (SipURI) request.getFrom().getURI();
        final String fromUser = fromUri.getUser();
        final SipURI toUri = (SipURI) request.getTo().getURI();
        String toUser = CallControlHelper.getUserSipId(request, useTo);
        final String ruri = ((SipURI) request.getRequestURI()).getHost();
        final String toHost = toUri.getHost();
        final String toHostIpAddress = DNSUtils.getByName(toHost).getHostAddress();
        final String toPort = String.valueOf(((SipURI) request.getTo().getURI()).getPort()).equalsIgnoreCase("-1") ? "5060"
                : String.valueOf(((SipURI) request.getTo().getURI()).getHost());
        final String transport = ((SipURI) request.getTo().getURI()).getTransportParam() == null ? "udp" : ((SipURI) request
                .getTo().getURI()).getTransportParam();
        SipURI outboundIntf = outboundInterface(transport);


        Sid sourceOrganizationSid = OrganizationUtil.getOrganizationSidBySipURIHost(storage, fromUri);
        Sid toOrganizationSid = SIPOrganizationUtil.searchOrganizationBySIPRequest(storage.getOrganizationsDao(), request);

        if(logger.isDebugEnabled()) {
            logger.debug("sourceOrganizationSid: " + sourceOrganizationSid +" fromUri: "+fromUri);
            logger.debug("toOrganizationSid: " + toOrganizationSid +" toUri: "+(SipURI) request.getTo().getURI());
        }

        if(sourceOrganizationSid == null){
            if(logger.isInfoEnabled())
                logger.info("Null Organization, call is probably coming from a provider: fromUri: "+fromUri);
        }

        final Client client = clients.getClient(fromUser,sourceOrganizationSid);
        final Client toClient = clients.getClient(toUser, toOrganizationSid);

        if (client != null) {
            Account fromAccount = accounts.getAccount(client.getAccountSid());
            if (!fromAccount.getStatus().equals(Account.Status.ACTIVE)) {
                //reject call since the Client belongs to an an account which is not ACTIVE
                rejectInvite(request);

                String msg = String.format("Restcomm rejects this call because client %s account %s is not ACTIVE, current state %s", client.getFriendlyName(), fromAccount.getSid(), fromAccount.getStatus());
                if (logger.isDebugEnabled()) {
                    logger.debug(msg);
                }
                sendNotification(null, msg, 11005, "error", true);
                return;
            }

            // Make sure we force clients to authenticate.
            if (!authenticateUsers // https://github.com/Mobicents/RestComm/issues/29 Allow disabling of SIP authentication
                    || CallControlHelper.checkAuthentication(request, storage, sourceOrganizationSid)) {
                // if the client has authenticated, try to redirect to the Client VoiceURL app
                // otherwise continue trying to process the Client invite
                if (redirectToClientVoiceApp(self, request, accounts, applications, client)) {
                    return;
                } // else continue trying other ways to handle the request
            } else {
                // Since the client failed to authenticate, we will take no further action at this time.
                return;
            }
        }

        if (toClient != null) {
            Account toAccount = accounts.getAccount(toClient.getAccountSid());

            if (!toAccount.getStatus().equals(Account.Status.ACTIVE)) {
                //reject call since the toClient belongs to an an account which is not ACTIVE
                rejectInvite(request);

                String msg = String.format("Restcomm rejects this call because client %s account %s is not ACTIVE, current state %s", toClient.getFriendlyName(), toAccount.getSid(), toAccount.getStatus());
                if (logger.isDebugEnabled()) {
                    logger.debug(msg);
                }
                sendNotification(null, msg, 11005, "error", true);
                return;
            }
        }

        IncomingPhoneNumber number = null;
        if (toClient == null) {

            number = getIncomingPhoneNumber(request, toUser, (client != null ? client.getSid() : null),
                    sourceOrganizationSid, toOrganizationSid);

            if (number != null) {
                Account numAccount = accounts.getAccount(number.getAccountSid());
                if (!numAccount.getStatus().equals(Account.Status.ACTIVE)) {
                    //reject call since the number belongs to an an account which is not ACTIVE
                    rejectInvite(request);

                    String msg = String.format("Restcomm rejects this call because number's %s account %s is not ACTIVE, current state %s", number.getPhoneNumber(), numAccount.getSid(), numAccount.getStatus());
                    if (logger.isDebugEnabled()) {
                        logger.debug(msg);
                    }
                    sendNotification(null, msg, 11005, "error", true);
                    return;
                }

                if (toOrganizationSid == null) {
                    toOrganizationSid = number.getOrganizationSid();
                }
            }
        }

        if (sourceOrganizationSid == null && toOrganizationSid == null) {
            //sourceOrganization is null which means we got a call from external provider or unregistered client
            // AND toOrganization is null which means there will be no client or number for this INVITE
            // THUS we should fail fast
            final SipServletResponse response = request.createResponse(SC_NOT_FOUND);
            response.send();
            // We didn't find anyway to handle the call.
            String msg = String.format("Restcomm cannot process this call to %s from %s. Source and To organizations are null", toUser, fromUser);
            if (logger.isInfoEnabled()) {
                logger.info(msg);
            }
            sendNotification(null, msg, 11005, "error", true);
        }

        if (logger.isInfoEnabled()) {
            logger.info("ToUser: " + toUser);
            logger.info("ToHost: " + toHost);
            logger.info("ruri: " + ruri);
            logger.info("myHostIp: " + myHostIp);
            logger.info("mediaExternalIp: " + mediaExternalIp);
            logger.info("proxyIp: " + proxyIp);
        }

        if (client != null) { // make sure the caller is a registered client and not some external SIP agent that we have little control over
            if (toClient != null) { // looks like its a p2p attempt between two valid registered clients, lets redirect to the b2bua
                if (logger.isInfoEnabled()) {
                    logger.info("Client is not null: " + client.getLogin() + " will try to proxy to client: " + toClient);
                }

                ExtensionController ec = ExtensionController.getInstance();
                final IExtensionCreateCallRequest er = new CreateCall(fromUser, toUser, "", "", false, 0, CreateCallType.CLIENT, client.getAccountSid(), null, null, null, null);
                ExtensionResponse extRes = ec.executePreOutboundAction(er, extensions);
                if (extRes.isAllowed()) {
                    long delay = pushNotificationServerHelper.sendPushNotificationIfNeeded(toClient.getPushClientIdentity());
                    system.scheduler().scheduleOnce(Duration.create(delay, TimeUnit.MILLISECONDS), new Runnable() {
                        @Override
                        public void run() {
                            try {
                                if (B2BUAHelper.redirectToB2BUA(system, request, client, toClient, storage, sipFactory, patchForNatB2BUASessions)) {
                                    if (logger.isInfoEnabled()) {
                                        logger.info("Call to CLIENT.  myHostIp: " + myHostIp + " mediaExternalIp: " + mediaExternalIp + " toHost: "
                                                + toHost + " fromClient: " + client.getUri() + " toClient: " + toClient.getUri());
                                    }
                                    // if all goes well with proxying the invitation on to the next client
                                    // then we can end further processing of this INVITE
                                } else {
                                    String errMsg = "Cannot Connect to Client: " + toClient.getFriendlyName()
                                            + " : Make sure the Client exist or is registered with Restcomm";
                                    sendNotification(client.getAccountSid(), errMsg, 11001, "warning", true);
                                    final SipServletResponse resp = request.createResponse(SC_NOT_FOUND, "Cannot complete P2P call");
                                    resp.send();
                                }

                                ExtensionController.getInstance().executePostOutboundAction(er, extensions);
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        }
                    }, system.dispatcher());
                } else {
                    //Extensions didn't allowed this call
                    if (logger.isDebugEnabled()) {
                        final String errMsg = "Client not Allowed to make this outbound call";
                        logger.debug(errMsg);
                    }
                    String errMsg = "Cannot Connect to Client: " + toClient.getFriendlyName()
                            + " : Make sure the Client exist or is registered with Restcomm";
                    sendNotification(client.getAccountSid(), errMsg, 11001, "warning", true);
                    final SipServletResponse resp = request.createResponse(SC_FORBIDDEN, "Call not allowed");
                    resp.send();
                }
                ec.executePostOutboundAction(er, extensions);
                return;
            } else {
                // toClient is null or we couldn't make the b2bua call to another client. check if this call is for a registered
                // DID (application)
                if (redirectToHostedVoiceApp(request, accounts, applications, toUser, client.getAccountSid(), number)) {
                    // This is a call to a registered DID (application)
                    return;
                }

                // This call is not a registered DID (application). Try to proxy out this call.
                // log to console and to notification engine
                String errMsg = "A Restcomm Client is trying to call a Number/DID that is not registered with Restcomm";
                sendNotification(client.getAccountSid(), errMsg, 11002, "info", true);

                if(useSbc) {
                    toUser = toUser+"@"+ruri;
                    if (logger.isDebugEnabled()) {
                        logger.debug("CallManager: use-sbc is true, overriding webrtc toUser to " + toUser);
                    }
                }

                ExtensionController ec = ExtensionController.getInstance();
                IExtensionCreateCallRequest er = new CreateCall(fromUser, toUser, "", "", false, 0, CreateCallType.PSTN, client.getAccountSid(), null, null, null, null);
                ExtensionResponse extRes = ec.executePreOutboundAction(er, this.extensions);
                if (extRes.isAllowed()) {
                    if (actAsProxyOut) {
                        processRequestAndProxyOut(request, client, toUser);
                    } else if (isWebRTC(request)) {
                        //This is a WebRTC client that dials out
                        //TODO: should we inject headers for this case?
                        proxyThroughMediaServerAsNumber(request, client, toUser);
                    } else {
                        // https://telestax.atlassian.net/browse/RESTCOMM-335
                        String proxyURI = activeProxy;
                        String proxyUsername = activeProxyUsername;
                        String proxyPassword = activeProxyPassword;
                        SipURI from = null;
                        SipURI to = null;
                        boolean callToSipUri = false;

                        if (er.getOutboundProxy() != null && !er.getOutboundProxy().isEmpty()) {
                            proxyURI = er.getOutboundProxy();
                        }
                        if (er.getOutboundProxyUsername() != null && !er.getOutboundProxyUsername().isEmpty()) {
                            proxyUsername = er.getOutboundProxyUsername();
                        }
                        if (er.getOutboundProxyPassword() != null && !er.getOutboundProxyPassword().isEmpty()) {
                            proxyPassword = er.getOutboundProxyPassword();
                        }
                        // proxy DID or number if the outbound proxy fields are not empty in the restcomm.xml
                        if (proxyURI != null && !proxyURI.isEmpty()) {
                            //FIXME: not so nice to just inject headers here
                            if (er.getOutboundProxyHeaders() != null) {
                                B2BUAHelper.addHeadersToMessage(request, er.getOutboundProxyHeaders(), sipFactory);
                                request.getSession().setAttribute(B2BUAHelper.EXTENSION_HEADERS, er.getOutboundProxyHeaders());
                            }
                            proxyOut(request, client, toUser, toHost, toHostIpAddress, toPort, outboundIntf, proxyURI, proxyUsername, proxyPassword, from, to, callToSipUri);
                        } else {
                            errMsg = "Restcomm tried to proxy this call to an outbound party but it seems the outbound proxy is not configured.";
                            sendNotification(client.getAccountSid(), errMsg, 11004, "warning", true);
                        }
                    }
                } else {
                    //Extensions didn't allow this call
                    final SipServletResponse response = request.createResponse(SC_FORBIDDEN, "Call request not allowed");
                    response.send();
                    if (logger.isDebugEnabled()) {
                        logger.debug("Call request not allowed: " + er.toString());
                    }
                }
                ec.executePostOutboundAction(er, this.extensions);
                return;
            }
        } else {
            // Client is null, check if this call is for a registered DID (application)
            // First try to check if the call is for a client
            if (toClient != null) {
                ExtensionController ec = ExtensionController.getInstance();
                final IExtensionCreateCallRequest cc = new CreateCall(fromUser, toUser, "", "", false, 0, CreateCallType.CLIENT, toClient.getAccountSid(), null, null, null, null);
                ExtensionResponse extRes = ec.executePreInboundAction(cc, this.extensions);

                if (extRes.isAllowed()) {
                    proxyDialClientThroughMediaServer(request, toClient, toClient.getLogin());
                    return;
                } else {
                    if (logger.isDebugEnabled()) {
                        final String errMsg = "Inbound PSTN Call to Client not Allowed";
                        logger.debug(errMsg);
                    }
                    String errMsg = "Inbound PSTN Call to Client: " + toClient.getFriendlyName()
                            + " is not Allowed";
                    sendNotification(client.getAccountSid(), errMsg, 11001, "warning", true);
                    final SipServletResponse resp = request.createResponse(SC_FORBIDDEN, "Call not allowed");
                    resp.send();
                }
                ec.executePostInboundAction(cc, extensions);
                return;
            }
            if (redirectToHostedVoiceApp(request, accounts, applications, toUser, null,number)) {
                // This is a call to a registered DID (application)
                return;
            }
            if (actAsProxyOut) {
                processRequestAndProxyOut(request, client, toUser);
                return;
            }
        }
        final SipServletResponse response = request.createResponse(SC_NOT_FOUND);
        response.send();
        // We didn't find anyway to handle the call.
        String errMsg = "Restcomm cannot process this call because the destination number " + toUser
                + "cannot be found or there is application attached to that";
        sendNotification(null, errMsg, 11005, "error", true);


    }

    private boolean proxyOut(SipServletRequest request, Client client, String toUser, String toHost, String toHostIpAddress, String toPort, SipURI outboundIntf, String proxyURI, String proxyUsername, String proxyPassword, SipURI from, SipURI to, boolean callToSipUri) throws UnknownHostException {
        final Configuration runtime = configuration.subset("runtime-settings");
        final boolean useLocalAddressAtFromHeader = runtime.getBoolean("use-local-address", false);
        final boolean outboudproxyUserAtFromHeader = runtime.subset("outbound-proxy").getBoolean(
                "outboudproxy-user-at-from-header", true);

        final String fromHost = ((SipURI) request.getFrom().getURI()).getHost();
        final String fromHostIpAddress = DNSUtils.getByName(fromHost).getHostAddress();
//                    final String fromPort = String.valueOf(((SipURI) request.getFrom().getURI()).getPort()).equalsIgnoreCase("-1") ? "5060"
//                            : String.valueOf(((SipURI) request.getFrom().getURI()).getHost());

        if (logger.isInfoEnabled()) {
            logger.info("fromHost: " + fromHost + "fromHostIP: " + fromHostIpAddress + "myHostIp: " + myHostIp + " mediaExternalIp: " + mediaExternalIp
                    + " toHost: " + toHost + " toHostIP: " + toHostIpAddress + " proxyUri: " + proxyURI);
        }
        if ((myHostIp.equalsIgnoreCase(toHost) || mediaExternalIp.equalsIgnoreCase(toHost)) ||
                (myHostIp.equalsIgnoreCase(toHostIpAddress) || mediaExternalIp.equalsIgnoreCase(toHostIpAddress))
                // https://github.com/RestComm/Restcomm-Connect/issues/1357
                || (fromHost.equalsIgnoreCase(toHost) || fromHost.equalsIgnoreCase(toHostIpAddress))
                || (fromHostIpAddress.equalsIgnoreCase(toHost) || fromHostIpAddress.equalsIgnoreCase(toHostIpAddress))) {
            if (logger.isInfoEnabled()) {
                logger.info("Call to NUMBER.  myHostIp: " + myHostIp + " mediaExternalIp: " + mediaExternalIp
                        + " toHost: " + toHost + " proxyUri: " + proxyURI);
            }
            try {
                if (useLocalAddressAtFromHeader) {
                    if (outboudproxyUserAtFromHeader) {
                        from = (SipURI) sipFactory.createSipURI(proxyUsername,
                                mediaExternalIp + ":" + outboundIntf.getPort());
                    } else {
                        from = sipFactory.createSipURI(((SipURI) request.getFrom().getURI()).getUser(),
                                mediaExternalIp + ":" + outboundIntf.getPort());
                    }
                } else {
                    if (outboudproxyUserAtFromHeader) {
                        // https://telestax.atlassian.net/browse/RESTCOMM-633. Use the outbound proxy username as
                        // the userpart of the sip uri for the From header
                        from = (SipURI) sipFactory.createSipURI(proxyUsername, proxyURI);
                    } else {
                        from = sipFactory.createSipURI(((SipURI) request.getFrom().getURI()).getUser(), proxyURI);
                    }
                }
                to = sipFactory.createSipURI(((SipURI) request.getTo().getURI()).getUser(), proxyURI);
            } catch (Exception exception) {
                if (logger.isInfoEnabled()) {
                    logger.info("Exception: " + exception);
                }
            }
        } else {
            if (logger.isInfoEnabled()) {
                logger.info("Call to SIP URI. myHostIp: " + myHostIp + " mediaExternalIp: " + mediaExternalIp
                        + " toHost: " + toHost + " proxyUri: " + proxyURI);
            }
            from = sipFactory.createSipURI(((SipURI) request.getFrom().getURI()).getUser(), outboundIntf.getHost()
                    + ":" + outboundIntf.getPort());
            to = sipFactory.createSipURI(toUser, toHost + ":" + toPort);
            callToSipUri = true;
        }
        if (B2BUAHelper.redirectToB2BUA(system, request, client, from, to, proxyUsername, proxyPassword, storage,
                sipFactory, callToSipUri, patchForNatB2BUASessions)) {
            return true;
        }
        return false;
    }

    private boolean isWebRTC(final SipServletRequest request) {
        String transport = request.getTransport();
        String userAgent = request.getHeader(UserAgent.NAME);
        //The check for request.getHeader(UserAgentHeader.NAME).equals("sipunit") has been added in order to be able to test this feature with sipunit at the Restcomm testsuite
        if (userAgent != null && !userAgent.isEmpty() && userAgent.equalsIgnoreCase("wss-sipunit")) {
            return true;
        }
        if (!request.getInitialTransport().equalsIgnoreCase(transport)) {
            transport = request.getInitialTransport();
            if ("ws".equalsIgnoreCase(transport) || "wss".equalsIgnoreCase(transport))
                return true;
        }
        try {
            if (SdpUtils.isWebRTCSDP(request.getContentType(), request.getRawContent())) {
                return true;
            }
        } catch (SdpParseException e) {
        } catch (IOException e) {
        }

        return false;
    }

    private void processRequestAndProxyOut(final SipServletRequest request, final Client client, final String destNumber) {
        String requestFromHost = null;

        ProxyRule matchedProxyRule = null;

        SipURI fromUri = null;
        try {
            if (isActAsProxyOutUseFromHeader) {
                fromUri = ((SipURI) request.getFrom().getURI());
            } else {
                fromUri = ((SipURI) request.getAddressHeader("Contact").getURI());
            }
        } catch (ServletParseException e) {
            logger.error("Problem while trying to process an `ActAsProxy` request, " + e);
        }
        requestFromHost = fromUri.getHost() + ":" + fromUri.getPort();

        for (ProxyRule proxyRule : proxyOutRules) {
            if (requestFromHost != null) {
                if (requestFromHost.equalsIgnoreCase(proxyRule.getFromUri())) {
                    matchedProxyRule = proxyRule;
                    break;
                }
            }
        }

        if (matchedProxyRule != null) {
            String sipUri = String.format("sip:%s@%s", destNumber, matchedProxyRule.getToUri());
            String rcml;
            if (matchedProxyRule.getUsername() != null && !matchedProxyRule.getUsername().isEmpty() && matchedProxyRule.getPassword() != null && !matchedProxyRule.getPassword().isEmpty()) {
                rcml = String.format("<Response><Dial><Sip username=\"%s\" password=\"%s\">%s</Sip></Dial></Response>", matchedProxyRule.getUsername(), matchedProxyRule.getPassword(), sipUri);
            } else {
                rcml = String.format("<Response><Dial><Sip>%s</Sip></Dial></Response>", sipUri);
            }

            final VoiceInterpreterParams.Builder builder = new VoiceInterpreterParams.Builder();
            builder.setConfiguration(configuration);
            builder.setStorage(storage);
            builder.setCallManager(self());
            builder.setConferenceCenter(conferences);
            builder.setBridgeManager(bridges);
            builder.setSmsService(sms);

            Sid accountSid = null;
            String apiVersion = null;
            if (client != null) {
                accountSid = client.getAccountSid();
                apiVersion = client.getApiVersion();
            } else {
                //Todo get Administrators account from RestcommConfiguration
                accountSid = new Sid("ACae6e420f425248d6a26948c17a9e2acf");
                apiVersion = RestcommConfiguration.getInstance().getMain().getApiVersion();
            }

            builder.setAccount(accountSid);
            builder.setVersion(apiVersion);
            final Account account = storage.getAccountsDao().getAccount(accountSid);
            builder.setEmailAddress(account.getEmailAddress());
            builder.setRcml(rcml);
            builder.setMonitoring(monitoring);
            final Props props = VoiceInterpreter.props(builder.build());
            final ActorRef interpreter = getContext().actorOf(props);
            final ActorRef call = call(accountSid, null);
            final SipApplicationSession application = request.getApplicationSession();
            application.setAttribute(Call.class.getName(), call);
            call.tell(request, self());
            interpreter.tell(new StartInterpreter(call), self());
        } else {
            if (logger.isInfoEnabled()) {
                logger.info("No rule matched for the `ActAsProxy` feature");
            }
        }
    }

    private void proxyThroughMediaServerAsNumber(final SipServletRequest request, final Client client, final String destNumber) {
        String number = destNumber;
        String customHeaders = customHeaders(request);
        if (customHeaders != null && !customHeaders.equals("")) {
            number = destNumber+"?"+customHeaders;
        }

        String rcml = "<Response><Dial>" + number + "</Dial></Response>";
        final VoiceInterpreterParams.Builder builder = new VoiceInterpreterParams.Builder();
        builder.setConfiguration(configuration);
        builder.setStorage(storage);
        builder.setCallManager(self());
        builder.setConferenceCenter(conferences);
        builder.setBridgeManager(bridges);
        builder.setSmsService(sms);
        builder.setAccount(client.getAccountSid());
        builder.setVersion(client.getApiVersion());
        final Account account = storage.getAccountsDao().getAccount(client.getAccountSid());
        builder.setEmailAddress(account.getEmailAddress());
        builder.setRcml(rcml);
        builder.setMonitoring(monitoring);
        final Props props = VoiceInterpreter.props(builder.build());
        final ActorRef interpreter = getContext().actorOf(props);

        final ActorRef call = call(client.getAccountSid(), null);
        final SipApplicationSession application = request.getApplicationSession();
        application.setAttribute(Call.class.getName(), call);
        call.tell(request, self());
        interpreter.tell(new StartInterpreter(call), self());
    }

    private void proxyDialClientThroughMediaServer(final SipServletRequest request, final Client client, final String destNumber) {
        String number = destNumber;
        String customHeaders = customHeaders(request);
        if (customHeaders != null && !customHeaders.equals("")) {
            number = destNumber+"?"+customHeaders;
        }

        String rcml = "<Response><Dial><Client>" + number + "</Client></Dial></Response>";
        final VoiceInterpreterParams.Builder builder = new VoiceInterpreterParams.Builder();
        builder.setConfiguration(configuration);
        builder.setStorage(storage);
        builder.setCallManager(self());
        builder.setConferenceCenter(conferences);
        builder.setBridgeManager(bridges);
        builder.setSmsService(sms);
        builder.setAccount(client.getAccountSid());
        builder.setVersion(client.getApiVersion());
        final Account account = storage.getAccountsDao().getAccount(client.getAccountSid());
        builder.setEmailAddress(account.getEmailAddress());
        builder.setRcml(rcml);
        builder.setMonitoring(monitoring);
        final Props props = VoiceInterpreter.props(builder.build());
        final ActorRef interpreter = getContext().actorOf(props);

        final ActorRef call = call(client.getAccountSid(), null);
        final SipApplicationSession application = request.getApplicationSession();
        application.setAttribute(Call.class.getName(), call);
        call.tell(request, self());
        interpreter.tell(new StartInterpreter(call), self());
    }

    private String customHeaders (final SipServletRequest request) {
        StringBuffer customHeaders = new StringBuffer();

        Iterator<String> headerNames = request.getHeaderNames();
        while (headerNames.hasNext()) {
            String headerName = headerNames.next();
            if (headerName.startsWith("X-")) {
                if (logger.isDebugEnabled()) {
                    logger.debug("Identified customer header at SipServletRequest : " + headerName);
                }
                if (customHeaders.length()>0) {
                    customHeaders.append("&");
                }
                customHeaders.append(headerName+"="+request.getHeader(headerName));
            }
        }

        return customHeaders.toString();
    }

    private void info(final SipServletRequest request) throws IOException {
        final ActorRef self = self();
        final SipApplicationSession application = request.getApplicationSession();

        // if this response is coming from a client that is in a p2p session with another registered client
        // we will just proxy the response
        SipSession linkedB2BUASession = B2BUAHelper.getLinkedSession(request);
        if (linkedB2BUASession != null) {
            if (logger.isInfoEnabled()) {
                logger.info(String.format("B2BUA: Got INFO request: \n %s", request));
            }
            request.getSession().setAttribute(B2BUAHelper.B2BUA_LAST_REQUEST, request);
            SipServletRequest clonedInfo = linkedB2BUASession.createRequest("INFO");
            linkedB2BUASession.setAttribute(B2BUAHelper.B2BUA_LAST_REQUEST, clonedInfo);

            // Issue #307: https://telestax.atlassian.net/browse/RESTCOMM-307
            SipURI toInetUri = (SipURI) request.getSession().getAttribute(B2BUAHelper.TO_INET_URI);
            SipURI fromInetUri = (SipURI) request.getSession().getAttribute(B2BUAHelper.FROM_INET_URI);
            InetAddress infoRURI = null;
            try {
                infoRURI = DNSUtils.getByName(((SipURI) clonedInfo.getRequestURI()).getHost());
            } catch (UnknownHostException e) {
            }
            if (patchForNatB2BUASessions) {
                if (toInetUri != null && infoRURI == null) {

                    if (logger.isInfoEnabled()) {
                        logger.info("Using the real ip address of the sip client " + toInetUri.toString()
                                + " as a request uri of the CloneBye request");
                    }
                    clonedInfo.setRequestURI(toInetUri);
                } else if (toInetUri != null
                        && (infoRURI.isSiteLocalAddress() || infoRURI.isAnyLocalAddress() || infoRURI.isLoopbackAddress())) {

                    if (logger.isInfoEnabled()) {
                        logger.info("Using the real ip address of the sip client " + toInetUri.toString()
                                + " as a request uri of the CloneInfo request");
                    }
                    clonedInfo.setRequestURI(toInetUri);
                } else if (fromInetUri != null
                        && (infoRURI.isSiteLocalAddress() || infoRURI.isAnyLocalAddress() || infoRURI.isLoopbackAddress())) {
                    if (logger.isInfoEnabled()) {
                        logger.info("Using the real ip address of the sip client " + fromInetUri.toString()
                                + " as a request uri of the CloneInfo request");
                    }

                    clonedInfo.setRequestURI(fromInetUri);
                }
            }
            clonedInfo.send();
        } else {
            final ActorRef call = (ActorRef) application.getAttribute(Call.class.getName());
            call.tell(request, self);
        }
    }

    private void transfer(SipServletRequest request) throws Exception {
        //Transferor is the one that initates the transfer
        String transferor = ((SipURI) request.getAddressHeader("Contact").getURI()).getUser();
        //Transferee is the one that gets transfered
        String transferee = ((SipURI) request.getAddressHeader("To").getURI()).getUser();
        //Trasnfer target, where the transferee will be transfered
        String transferTarget = ((SipURI) request.getAddressHeader("Refer-To").getURI()).getUser();

        CallDetailRecord cdr = null;
        CallDetailRecordsDao dao = storage.getCallDetailRecordsDao();

        SipServletResponse servletResponse = null;

        final SipApplicationSession appSession = request.getApplicationSession();
        //Initates the transfer
        ActorRef transferorActor = (ActorRef) appSession.getAttribute(Call.class.getName());
        if (transferorActor == null) {
            if (logger.isInfoEnabled()) {
                logger.info("Transferor Call Actor is null, cannot proceed with SIP Refer");
            }
            servletResponse = request.createResponse(SC_NOT_FOUND);
            servletResponse.setHeader("Reason", "SIP REFER should be sent in dialog");
            servletResponse.setHeader("Event", "refer");
            servletResponse.send();
            return;
        }

        final Timeout expires = new Timeout(Duration.create(60, TimeUnit.SECONDS));

        Future<Object> infoFuture = (Future<Object>) ask(transferorActor, new GetCallInfo(), expires);
        CallResponse<CallInfo> infoResponse = (CallResponse<CallInfo>) Await.result(infoFuture,
                Duration.create(10, TimeUnit.SECONDS));
        CallInfo callInfo = infoResponse.get();

        //Call must be in-progress to accept Sip Refer
        if (callInfo != null && callInfo.state().equals(CallStateChanged.State.IN_PROGRESS)) {
            try {
                if (callInfo.direction().equalsIgnoreCase("inbound")) {
                    //Transferror is the inbound leg of the call
                    cdr = dao.getCallDetailRecord(callInfo.sid());
                } else {
                    //Transferor is the outbound leg of the call
                    cdr = dao.getCallDetailRecord(dao.getCallDetailRecord(callInfo.sid()).getParentCallSid());
                }
            } catch (Exception e) {
                if (logger.isInfoEnabled()) {
                    logger.info("Problem while trying to get the CDR of the call");
                }
                servletResponse = request.createResponse(SC_SERVER_INTERNAL_ERROR);
                servletResponse.setHeader("Reason", "SIP Refer problem during execution");
                servletResponse.setHeader("Event", "refer");
                servletResponse.send();
                return;
            }
        } else {
            if (logger.isInfoEnabled()) {
                logger.info("CallInfo is null or call state not in-progress. Cannot proceed to call transfer");
            }
            servletResponse = request.createResponse(SC_NOT_FOUND);
            servletResponse.setHeader("Reason", "SIP Refer pre-conditions failed, call info is null or call not in progress");
            servletResponse.setHeader("Event", "refer");
            servletResponse.send();
            return;
        }

        String phone = cdr.getTo();
        Sid sourceOrganizationSid = storage.getAccountsDao().getAccount(cdr.getAccountSid()).getOrganizationSid();
        Sid destOrg = SIPOrganizationUtil.searchOrganizationBySIPRequest(storage.getOrganizationsDao(), request);
        IncomingPhoneNumber number = numberSelector.searchNumber(phone, sourceOrganizationSid, destOrg);

        if (number == null || (number.getReferUrl() == null && number.getReferApplicationSid() == null)) {
            if (logger.isInfoEnabled()) {
                logger.info("Refer URL or Refer Applicatio for incoming phone number is null, cannot proceed with SIP Refer");
            }
            servletResponse = request.createResponse(SC_NOT_FOUND);
            servletResponse.setHeader("Reason", "SIP Refer failed. Set Refer URL or Refer application for incoming phone number");
            servletResponse.setHeader("Event", "refer");
            servletResponse.send();
            return;
        }

        // Get first transferorActor leg observers
        Future<Object> future = (Future<Object>) ask(transferorActor, new GetCallObservers(), expires);
        CallResponse<List<ActorRef>> response = (CallResponse<List<ActorRef>>) Await.result(future,
                Duration.create(10, TimeUnit.SECONDS));
        List<ActorRef> callObservers = response.get();

        // Get the Voice Interpreter currently handling the transferorActor
        ActorRef existingInterpreter = callObservers.iterator().next();

        // Get the outbound leg of this transferorActor
        future = (Future<Object>) ask(existingInterpreter, new GetRelatedCall(transferorActor), expires);
        Object answer = (Object) Await.result(future, Duration.create(10, TimeUnit.SECONDS));

        //Transferee will be transfered to the transfer target
        ActorRef transfereeActor = null;
        if (answer instanceof ActorRef) {
            transfereeActor = (ActorRef) answer;
        } else {
            if (logger.isInfoEnabled()) {
                logger.info("Transferee is not a Call actor, probably call is on conference");
            }
            servletResponse = request.createResponse(SC_NOT_FOUND);
            servletResponse.setHeader("Reason", "SIP Refer failed. Transferee is not a Call actor, probably this is a conference");
            servletResponse.setHeader("Event", "refer");
            servletResponse.send();
            transferorActor.tell(new Hangup(), null);
            return;
        }

        servletResponse = request.createResponse(SC_ACCEPTED);
        servletResponse.setHeader("Event", "refer");
        servletResponse.send();

        if (logger.isInfoEnabled()) {
            logger.info("About to start Call Transfer");
            logger.info("Transferor Call path: " + transferorActor.path());
            if (transfereeActor != null) {
                logger.info("Transferee Call path: " + transfereeActor.path());
            }
            // Cleanup all observers from both transferorActor legs
            logger.info("Will tell Call actors to stop observing existing Interpreters");
        }
        if (logger.isDebugEnabled()) {
            logger.debug("Call Transfer account: " + cdr.getAccountSid() + ", new RCML url: " + number.getReferUrl());
        }
        transferorActor.tell(new StopObserving(), self());
        if (transfereeActor != null) {
            transfereeActor.tell(new StopObserving(), self());
        }
        if (logger.isInfoEnabled()) {
            logger.info("Existing observers removed from Calls actors");

            // Cleanup existing Interpreter
            logger.info("Existing Interpreter path: " + existingInterpreter.path() + " will be stopped");
        }
        existingInterpreter.tell(new StopInterpreter(true), null);

        // Build a new VoiceInterpreter
        final VoiceInterpreterParams.Builder builder = new VoiceInterpreterParams.Builder();
        builder.setConfiguration(configuration);
        builder.setStorage(storage);
        builder.setCallManager(self());
        builder.setConferenceCenter(conferences);
        builder.setBridgeManager(bridges);
        builder.setSmsService(sms);
        builder.setAccount(cdr.getAccountSid());
        builder.setVersion(cdr.getApiVersion());

        if (number.getReferApplicationSid() != null) {
            Application application = storage.getApplicationsDao().getApplication(number.getReferApplicationSid());
            RcmlserverConfigurationSet rcmlserverConfig = RestcommConfiguration.getInstance().getRcmlserver();
            RcmlserverResolver resolver = RcmlserverResolver.getInstance(rcmlserverConfig.getBaseUrl(), rcmlserverConfig.getApiPath());
            builder.setUrl(UriUtils.resolve(resolver.resolveRelative(application.getRcmlUrl())));
        } else {
            builder.setUrl(UriUtils.resolve(number.getReferUrl()));
        }

        builder.setMethod((number.getReferMethod() != null && number.getReferMethod().length() > 0) ? number.getReferMethod() : "POST");
        builder.setReferTarget(transferTarget);
        builder.setTransferor(transferor);
        builder.setTransferee(transferee);

        builder.setFallbackUrl(null);
        builder.setFallbackMethod("POST");
        builder.setStatusCallback(null);
        builder.setStatusCallbackMethod("POST");
        builder.setMonitoring(monitoring);

        // Ask first transferorActor leg to execute with the new Interpreter
        final Props props = VoiceInterpreter.props(builder.build());
        final ActorRef interpreter = getContext().actorOf(props);
        system.scheduler().scheduleOnce(Duration.create(500, TimeUnit.MILLISECONDS), interpreter,
                new StartInterpreter(transfereeActor), system.dispatcher());
        if (logger.isInfoEnabled()) {
            logger.info("New Intepreter for transfereeActor call leg: " + interpreter.path() + " started");
        }

        if (logger.isInfoEnabled()) {
            logger.info("will hangup transferorActor: " + transferorActor.path());
        }
        transferorActor.tell(new Hangup(), null);
    }


    private void sendNotFound(final SipServletRequest request, Sid sourceOrganizationSid, String phone, Sid fromClientAccountSid) throws IOException {
        //organization was not proper.
        final SipServletResponse response = request.createResponse(SC_NOT_FOUND);
        response.send();
        String sourceDomainName = "";
        if (sourceOrganizationSid != null) {
            sourceDomainName = storage.getOrganizationsDao().getOrganization(sourceOrganizationSid).getDomainName();
        }
        // We found the number but organization was not proper
        String errMsg = String.format("provided number %s does not belong to your domain %s.", phone, sourceDomainName);
        logger.warning(errMsg+" Requiested URI was: "+ request.getRequestURI());
        sendNotification(fromClientAccountSid, errMsg, 11005, "error", true);
    }

    private IncomingPhoneNumber getIncomingPhoneNumber (final SipServletRequest request, String phone, Sid fromClientAccountSid,
                                                        Sid sourceOrganizationSid, Sid toOrganization) {
        IncomingPhoneNumber number = null;

        try {
            NumberSelectionResult result = numberSelector.searchNumberWithResult(phone, sourceOrganizationSid, toOrganization);
            if (numberSelector.isFailedCall(result, sourceOrganizationSid, toOrganization)) {
                // We found the number but organization was not proper
                if (logger.isDebugEnabled()) {
                    String msg = String.format("Number found %s, but source org %s and destination org %s are not proper", number, sourceOrganizationSid.toString(), toOrganization.toString());
                    logger.debug(msg);
                }
                sendNotFound(request, sourceOrganizationSid, phone, fromClientAccountSid);
                return null;
            }
            number = result.getNumber();
        } catch (Exception notANumber) {
            String errMsg;
            if (number != null) {
                errMsg = String.format("IncomingPhoneNumber %s does not have a Restcomm hosted application attached, exception %s", number.getPhoneNumber(), notANumber);
            } else {
                errMsg = String.format("IncomingPhoneNumber for %s, does not exist, exception %s", phone, notANumber);
            }
            sendNotification(fromClientAccountSid, errMsg, 11007, "error", false);
            logger.warning(errMsg);
        }

        return number;
    }

    /**
     * Try to locate a hosted voice app corresponding to the callee/To address. If one is found, begin execution, otherwise
     * return false;
     *
     * @param request
     * @param accounts
     * @param applications
     * @param phone
     */
    private boolean redirectToHostedVoiceApp (final SipServletRequest request, final AccountsDao accounts,
                                              final ApplicationsDao applications, String phone, Sid fromClientAccountSid,
                                              IncomingPhoneNumber number) {
        boolean isFoundHostedApp = false;
        try {
            if (number != null) {
                ExtensionController ec = ExtensionController.getInstance();
                IExtensionFeatureAccessRequest far = new FeatureAccessRequest(FeatureAccessRequest.Feature.INBOUND_VOICE, number.getAccountSid());
                ExtensionResponse er = ec.executePreInboundAction(far, extensions);
                if (er.isAllowed()) {
                    final VoiceInterpreterParams.Builder builder = new VoiceInterpreterParams.Builder();
                    builder.setConfiguration(configuration);
                    builder.setStorage(storage);
                    builder.setCallManager(self());
                    builder.setConferenceCenter(conferences);
                    builder.setBridgeManager(bridges);
                    builder.setSmsService(sms);
                    //https://github.com/RestComm/Restcomm-Connect/issues/1939
                    Sid accSid = fromClientAccountSid == null ? number.getAccountSid() : fromClientAccountSid;
                    builder.setAccount(accSid);
                    builder.setPhone(number.getAccountSid());
                    builder.setVersion(number.getApiVersion());
                    // notifications should go to fromClientAccountSid email if not present then to number account
                    // https://github.com/RestComm/Restcomm-Connect/issues/2011
                    final Account account = accounts.getAccount(accSid);
                    builder.setEmailAddress(account.getEmailAddress());
                    final Sid sid = number.getVoiceApplicationSid();
                    if (sid != null) {
                        final Application application = applications.getApplication(sid);
                        RcmlserverConfigurationSet rcmlserverConfig = RestcommConfiguration.getInstance().getRcmlserver();
                        RcmlserverResolver rcmlserverResolver = RcmlserverResolver.getInstance(rcmlserverConfig.getBaseUrl(), rcmlserverConfig.getApiPath());
                        builder.setUrl(UriUtils.resolve(rcmlserverResolver.resolveRelative(application.getRcmlUrl())));
                    } else {
                        builder.setUrl(UriUtils.resolve(number.getVoiceUrl()));
                    }
                    final String voiceMethod = number.getVoiceMethod();
                    if (voiceMethod == null || voiceMethod.isEmpty()) {
                        builder.setMethod("POST");
                    } else {
                        builder.setMethod(voiceMethod);
                    }
                    URI uri = number.getVoiceFallbackUrl();
                    if (uri != null)
                        builder.setFallbackUrl(UriUtils.resolve(uri));
                    else
                        builder.setFallbackUrl(null);
                    builder.setFallbackMethod(number.getVoiceFallbackMethod());
                    builder.setStatusCallback(number.getStatusCallback());
                    builder.setStatusCallbackMethod(number.getStatusCallbackMethod());
                    builder.setMonitoring(monitoring);
                    final Props props = VoiceInterpreter.props(builder.build());
                    final ActorRef interpreter = getContext().actorOf(props);

                    final ActorRef call = call(accSid, null);
                    final SipApplicationSession application = request.getApplicationSession();
                    application.setAttribute(Call.class.getName(), call);
                    call.tell(request, self());
                    interpreter.tell(new StartInterpreter(call), self());
                    isFoundHostedApp = true;
                    ec.executePostInboundAction(far, extensions);
                } else {
                    //Extensions didn't allowed this call
                    String errMsg = "Inbound call to Number: " + number.getPhoneNumber()
                            + " is not allowed";
                    if (logger.isDebugEnabled()) {
                        logger.debug(errMsg);
                    }
                    sendNotification(number.getAccountSid(), errMsg, 11001, "warning", true);
                    final SipServletResponse resp = request.createResponse(SC_FORBIDDEN, "Call not allowed");
                    resp.send();
                    ec.executePostInboundAction(far, extensions);
                    return false;
                }
            }
        } catch (Exception notANumber) {
            String errMsg;
            if (number != null) {
                errMsg = String.format("IncomingPhoneNumber %s does not have a Restcomm hosted application attached, exception %s", number.getPhoneNumber(), notANumber);
            } else {
                errMsg = String.format("IncomingPhoneNumber for %s, does not exist, exception %s", phone, notANumber);
            }
            sendNotification(fromClientAccountSid, errMsg, 11007, "error", false);
            logger.warning(errMsg);
            isFoundHostedApp = false;
        }
        return isFoundHostedApp;
    }

    /**
     * If there is VoiceUrl provided for a Client configuration, try to begin execution of the RCML app, otherwise return false.
     *
     * @param self
     * @param request
     * @param accounts
     * @param applications
     * @param client
     */
    private boolean redirectToClientVoiceApp(final ActorRef self, final SipServletRequest request, final AccountsDao accounts,
                                             final ApplicationsDao applications, final Client client) {
        Sid applicationSid = client.getVoiceApplicationSid();
        URI clientAppVoiceUrl = null;
        if (applicationSid != null) {
            final Application application = applications.getApplication(applicationSid);
            RcmlserverConfigurationSet rcmlserverConfig = RestcommConfiguration.getInstance().getRcmlserver();
            RcmlserverResolver resolver = RcmlserverResolver.getInstance(rcmlserverConfig.getBaseUrl(), rcmlserverConfig.getApiPath());
            clientAppVoiceUrl = UriUtils.resolve(resolver.resolveRelative(application.getRcmlUrl()));
        }
        if (clientAppVoiceUrl == null) {
            clientAppVoiceUrl = client.getVoiceUrl();
        }
        boolean isClientManaged = ((applicationSid != null && !applicationSid.toString().isEmpty() && !applicationSid.toString().equals("")) ||
                (clientAppVoiceUrl != null && !clientAppVoiceUrl.toString().isEmpty() && !clientAppVoiceUrl.toString().equals("")));
        if (isClientManaged) {
            final VoiceInterpreterParams.Builder builder = new VoiceInterpreterParams.Builder();
            builder.setConfiguration(configuration);
            builder.setStorage(storage);
            builder.setCallManager(self);
            builder.setConferenceCenter(conferences);
            builder.setBridgeManager(bridges);
            builder.setSmsService(sms);
            builder.setAccount(client.getAccountSid());
            builder.setVersion(client.getApiVersion());
            final Account account = accounts.getAccount(client.getAccountSid());
            builder.setEmailAddress(account.getEmailAddress());
            final Sid sid = client.getVoiceApplicationSid();
            builder.setUrl(clientAppVoiceUrl);
            builder.setMethod(client.getVoiceMethod());
            URI uri = client.getVoiceFallbackUrl();
            if (uri != null)
                builder.setFallbackUrl(UriUtils.resolve(uri));
            else
                builder.setFallbackUrl(null);
            builder.setFallbackMethod(client.getVoiceFallbackMethod());
            builder.setMonitoring(monitoring);
            final Props props = VoiceInterpreter.props(builder.build());
            final ActorRef interpreter = getContext().actorOf(props);
            final ActorRef call = call(client.getAccountSid(), null);
            final SipApplicationSession application = request.getApplicationSession();
            application.setAttribute(Call.class.getName(), call);
            call.tell(request, self);
            interpreter.tell(new StartInterpreter(call), self);
        }
        return isClientManaged;
    }

    private void pong(final Object message) throws IOException {
        final SipServletRequest request = (SipServletRequest) message;
        final SipServletResponse response = request.createResponse(SC_OK);
        response.send();
    }

    @Override
    public void onReceive(final Object message) throws Exception {
        final Class<?> klass = message.getClass();
        final ActorRef self = self();
        final ActorRef sender = sender();
        if (logger.isDebugEnabled()) {
            logger.debug("######### CallManager new message received, message instanceof : " + klass + " from sender : "
                    + sender.getClass());
        }
        if (message instanceof SipServletRequest) {
            final SipServletRequest request = (SipServletRequest) message;
            final String method = request.getMethod();
            if (request != null) {
                if ("INVITE".equals(method)) {
                    if (check(request))
                        invite(request);
                } else if ("OPTIONS".equals(method)) {
                    pong(request);
                } else if ("ACK".equals(method)) {
                    ack(request);
                } else if ("CANCEL".equals(method)) {
                    cancel(request);
                } else if ("BYE".equals(method)) {
                    bye(request);
                } else if ("INFO".equals(method)) {
                    info(request);
                } else if ("REFER".equals(method)) {
                    transfer(request);
                }
            }
        } else if (CreateCall.class.equals(klass)) {
            outbound(message, sender);
        } else if (ExecuteCallScript.class.equals(klass)) {
            execute(message);
        } else if (UpdateCallScript.class.equals(klass)) {
            try {
                update(message);
            } catch (final Exception exception) {
                sender.tell(new CallManagerResponse<ActorRef>(exception), self);
            }

        } else if (DestroyCall.class.equals(klass)) {
            destroy(message);
        } else if (message instanceof SipServletResponse) {
            response(message);
        } else if (message instanceof SipApplicationSessionEvent) {
            timeout(message);
        } else if (GetCall.class.equals(klass)) {
            sender.tell(lookup(message), self);
        } else if (GetActiveProxy.class.equals(klass)) {
            sender.tell(getActiveProxy(), self);
        } else if (SwitchProxy.class.equals(klass)) {
            this.switchProxyRequest = (SwitchProxy) message;
            sender.tell(switchProxy(), self);
        } else if (GetProxies.class.equals(klass)) {
            sender.tell(getProxies(message), self);
        }
    }

    private void ack(SipServletRequest request) throws IOException {
        SipServletResponse response = B2BUAHelper.getLinkedResponse(request);
        // if this is an ACK that belongs to a B2BUA session, then we proxy it to the other client
        if (response != null) {
            SipServletRequest ack = response.createAck();
//            if (!ack.getHeaders("Route").hasNext() && patchForNatB2BUASessions) {
            if (patchForNatB2BUASessions) {
                InetAddress ackRURI = null;
                try {
                    ackRURI = DNSUtils.getByName(((SipURI) ack.getRequestURI()).getHost());
                } catch (UnknownHostException e) {
                }
                boolean isBehindLB = false;
                final String initialIpBeforeLB = response.getHeader("X-Sip-Balancer-InitialRemoteAddr");
                String initialPortBeforeLB = response.getHeader("X-Sip-Balancer-InitialRemotePort");
                if (initialIpBeforeLB != null) {
                    if (initialPortBeforeLB == null)
                        initialPortBeforeLB = "5060";
                    if (logger.isDebugEnabled()) {
                        logger.debug("We are behind load balancer, checking if the request URI needs to be patched");
                    }
                    isBehindLB = true;
                }
                // Issue #307: https://telestax.atlassian.net/browse/RESTCOMM-307
                SipURI toInetUri = (SipURI) request.getSession().getAttribute(B2BUAHelper.TO_INET_URI);
                if (toInetUri != null && ackRURI == null) {
                    if (isBehindLB) {
                        // https://github.com/RestComm/Restcomm-Connect/issues/1357
                        boolean patchRURI = isLBPatchRURI(ack, initialIpBeforeLB, initialPortBeforeLB);
                        if (patchRURI) {
                            if (logger.isDebugEnabled()) {
                                logger.debug("We are behind load balancer, but Using the real ip address of the sip client " + toInetUri.toString()
                                        + " as a request uri of the ACK request");
                            }
                            ack.setRequestURI(toInetUri);
                        } else {
                            // https://github.com/RestComm/Restcomm-Connect/issues/1357
                            if (logger.isDebugEnabled()) {
                                logger.debug("removing the toInetUri to avoid the other subsequent requests using it " + toInetUri.toString());
                            }
                            request.getSession().removeAttribute(B2BUAHelper.TO_INET_URI);
                        }
                    } else {
                        if (logger.isInfoEnabled()) {
                            logger.info("Using the real ip address of the sip client " + toInetUri.toString()
                                    + " as a request uri of the ACK request");
                        }
                        ack.setRequestURI(toInetUri);
                    }
                } else if (toInetUri != null
                        && (ackRURI.isSiteLocalAddress() || ackRURI.isAnyLocalAddress() || ackRURI.isLoopbackAddress())) {
                    if (isBehindLB) {
                        // https://github.com/RestComm/Restcomm-Connect/issues/1357
                        boolean patchRURI = isLBPatchRURI(ack, initialIpBeforeLB, initialPortBeforeLB);
                        if (patchRURI) {
                            if (logger.isDebugEnabled()) {
                                logger.debug("We are behind load balancer, but Using the real ip address of the sip client " + toInetUri.toString()
                                        + " as a request uri of the ACK request");
                            }
                            ack.setRequestURI(toInetUri);
                        } else {
                            // https://github.com/RestComm/Restcomm-Connect/issues/1357
                            if (logger.isDebugEnabled()) {
                                logger.debug("removing the toInetUri to avoid the other subsequent requests using it " + toInetUri.toString());
                            }
                            request.getSession().removeAttribute(B2BUAHelper.TO_INET_URI);
                        }
                    } else {
                        if (logger.isInfoEnabled()) {
                            logger.info("Using the real ip address of the sip client " + toInetUri.toString()
                                    + " as a request uri of the ACK request");
                        }
                        ack.setRequestURI(toInetUri);
                    }
                } else if (toInetUri == null
                        && (ackRURI.isSiteLocalAddress() || ackRURI.isAnyLocalAddress() || ackRURI.isLoopbackAddress())) {
                    if (logger.isInfoEnabled()) {
                        logger.info("Public IP toInetUri from SipSession is null, will check LB headers from last Response");
                    }
                    if (isBehindLB) {
                        String realIP = initialIpBeforeLB + ":" + initialPortBeforeLB;
                        SipURI uri = sipFactory.createSipURI(null, realIP);
                        boolean patchRURI = isLBPatchRURI(ack, initialIpBeforeLB, initialPortBeforeLB);
                        if (patchRURI) {
                            if (logger.isDebugEnabled()) {
                                logger.debug("We are behind load balancer, will use Initial Remote Address " + initialIpBeforeLB + ":"
                                        + initialPortBeforeLB + " for the ACK request");
                            }
                            ack.setRequestURI(uri);
                        }
                    } else {
                        if (logger.isInfoEnabled()) {
                            logger.info("LB Headers are also null");
                        }
                    }
                }
            }
            ack.send();
            SipApplicationSession sipApplicationSession = request.getApplicationSession();
            // Defaulting the sip application session to 1h
            sipApplicationSession.setExpires(60);
        } else {
            if (logger.isInfoEnabled()) {
                logger.info("Linked Response couldn't be found for ACK request");
            }
            final ActorRef call = (ActorRef) request.getApplicationSession().getAttribute(Call.class.getName());
            if (call != null) {
                if (logger.isInfoEnabled()) {
                    logger.info("Will send ACK to call actor: " + call.path());
                }
                call.tell(request, self());
            }
        }
        // else {
        // SipSession sipSession = request.getSession();
        // SipApplicationSession sipAppSession = request.getApplicationSession();
        // if(sipSession.getInvalidateWhenReady()){
        // logger.info("Invalidating sipSession: "+sipSession.getId());
        // sipSession.invalidate();
        // }
        // if(sipAppSession.getInvalidateWhenReady()){
        // logger.info("Invalidating sipAppSession: "+sipAppSession.getId());
        // sipAppSession.invalidate();
        // }
        // }
    }

    private boolean isLBPatchRURI(SipServletRequest request,
                                  final String initialIpBeforeLB, String initialPortBeforeLB) {
        try {
            // https://github.com/RestComm/Restcomm-Connect/issues/1336 checking if the initial IP and Port behind LB is part of the route set or not
            ListIterator<? extends Address> routes = request.getAddressHeaders(RouteHeader.NAME);
            while (routes.hasNext()) {
                SipURI route = (SipURI) routes.next().getURI();
                String routeHost = route.getHost();
                int routePort = route.getPort();
                if (routePort < 0) {
                    routePort = 5060;
                }
                if (logger.isDebugEnabled()) {
                    logger.debug("Checking if route " + routeHost + ":" + routePort + " is matching ip and port before LB " + initialIpBeforeLB + ":"
                            + initialPortBeforeLB + " for the " + request.getMethod() + " request");
                }
                if (routeHost.equalsIgnoreCase(initialIpBeforeLB) && routePort == Integer.parseInt(initialPortBeforeLB)) {
                    if (logger.isDebugEnabled()) {
                        logger.debug("route " + route + " is matching ip and port before LB " + initialIpBeforeLB + ":"
                                + initialPortBeforeLB + " for the " + request.getMethod() + " request, so not patching the Request-URI");
                    }
                    return false;
                }
            }
        } catch (ServletParseException e) {
            logger.error("Impossible to parse the route set from the request " + request, e);
        }
        return true;
    }

    private void execute(final Object message) {
        final ExecuteCallScript request = (ExecuteCallScript) message;
        final ActorRef self = self();
        final VoiceInterpreterParams.Builder builder = new VoiceInterpreterParams.Builder();
        builder.setConfiguration(configuration);
        builder.setStorage(storage);
        builder.setCallManager(self);
        builder.setConferenceCenter(conferences);
        builder.setBridgeManager(bridges);
        builder.setSmsService(sms);
        builder.setAccount(request.account());
        builder.setVersion(request.version());
        builder.setUrl(request.url());
        builder.setMethod(request.method());
        builder.setFallbackUrl(request.fallbackUrl());
        builder.setFallbackMethod(request.fallbackMethod());
        builder.setMonitoring(monitoring);
        builder.setTimeout(request.timeout());
        final Props props = VoiceInterpreter.props(builder.build());
        final ActorRef interpreter = getContext().actorOf(props);
        interpreter.tell(new StartInterpreter(request.call()), self);
    }

    @SuppressWarnings("unchecked")
    private void update(final Object message) throws Exception {
        final UpdateCallScript request = (UpdateCallScript) message;
        final ActorRef self = self();
        final ActorRef call = request.call();
        final Boolean moveConnectedCallLeg = request.moveConnecteCallLeg();

        // Get first call leg observers
        final Timeout expires = new Timeout(Duration.create(60, TimeUnit.SECONDS));
        Future<Object> future = (Future<Object>) ask(call, new GetCallObservers(), expires);
        CallResponse<List<ActorRef>> response = (CallResponse<List<ActorRef>>) Await.result(future,
                Duration.create(10, TimeUnit.SECONDS));
        List<ActorRef> callObservers = response.get();

        // Get the Voice Interpreter currently handling the call
        //TODO possible bug here. Since we have more than one call observer, later there might be the case that the first one is not the VI
        //TODO set the VI using specific message, also get the VI using specific message. The VI will still be in the observers list but it will set/get using specific method
        ActorRef existingInterpreter = callObservers.iterator().next();

        // Get the outbound leg of this call
        future = (Future<Object>) ask(existingInterpreter, new GetRelatedCall(call), expires);
        Object answer = (Object) Await.result(future, Duration.create(10, TimeUnit.SECONDS));

        ActorRef relatedCall = null;
        List<ActorRef> listOfRelatedCalls = null;
        if (answer instanceof ActorRef) {
            relatedCall = (ActorRef) answer;
        } else if (answer instanceof List) {
            listOfRelatedCalls = (List<ActorRef>) answer;
        }

        if (logger.isInfoEnabled()) {
            logger.info("About to start Live Call Modification, moveConnectedCallLeg: " + moveConnectedCallLeg);
            logger.info("Initial Call path: " + call.path());
            if (relatedCall != null) {
                logger.info("Related Call path: " + relatedCall.path());
            }
            if (listOfRelatedCalls != null) {
                logger.info("List of related calls received, size of the list: " + listOfRelatedCalls.size());
            }
            // Cleanup all observers from both call legs
            logger.info("Will tell Call actors to stop observing existing Interpreters");
        }
        if (logger.isDebugEnabled()) {
            logger.debug("LCM account: " + request.account() + ", moveConnectedCallLeg: " + moveConnectedCallLeg + ", new RCML url: " + request.url());
        }
        call.tell(new StopObserving(), self());
        if (relatedCall != null) {
            relatedCall.tell(new StopObserving(), self());
        }
        if (listOfRelatedCalls != null) {
            for (ActorRef branch : listOfRelatedCalls) {
                branch.tell(new StopObserving(), self());
            }
        }
        if (logger.isInfoEnabled()) {
            logger.info("Existing observers removed from Calls actors");

            // Cleanup existing Interpreter
            logger.info("Existing Interpreter path: " + existingInterpreter.path() + " will be stopped");
        }
        existingInterpreter.tell(new StopInterpreter(true), null);

        // Build a new VoiceInterpreter
        final VoiceInterpreterParams.Builder builder = new VoiceInterpreterParams.Builder();
        builder.setConfiguration(configuration);
        builder.setStorage(storage);
        builder.setCallManager(self);
        builder.setConferenceCenter(conferences);
        builder.setBridgeManager(bridges);
        builder.setSmsService(sms);
        builder.setAccount(request.account());
        builder.setVersion(request.version());
        builder.setUrl(request.url());
        builder.setMethod(request.method());
        builder.setFallbackUrl(request.fallbackUrl());
        builder.setFallbackMethod(request.fallbackMethod());
        builder.setStatusCallback(request.callback());
        builder.setStatusCallbackMethod(request.callbackMethod());
        builder.setMonitoring(monitoring);
        final Props props = VoiceInterpreter.props(builder.build());

        // Ask first call leg to execute with the new Interpreter
        final ActorRef interpreter = getContext().actorOf(props);
        system.scheduler().scheduleOnce(Duration.create(500, TimeUnit.MILLISECONDS), interpreter,
                new StartInterpreter(request.call()), system.dispatcher());
        // interpreter.tell(new StartInterpreter(request.call()), self);
        if (logger.isInfoEnabled()) {
            logger.info("New Intepreter for first call leg: " + interpreter.path() + " started");
        }

        // Check what to do with the second/outbound call leg of the call
        if (relatedCall != null && listOfRelatedCalls == null) {
            if (moveConnectedCallLeg) {
                final ActorRef relatedInterpreter = getContext().actorOf(props);
                if (logger.isInfoEnabled()) {
                    logger.info("About to redirect related Call :" + relatedCall.path()
                            + " with 200ms delay to related interpreter: " + relatedInterpreter.path());
                }
                system.scheduler().scheduleOnce(Duration.create(1000, TimeUnit.MILLISECONDS), relatedInterpreter,
                        new StartInterpreter(relatedCall), system.dispatcher());

                if (logger.isInfoEnabled()) {
                    logger.info("New Intepreter for Second call leg: " + relatedInterpreter.path() + " started");
                }
            } else {
                if (logger.isInfoEnabled()) {
                    logger.info("moveConnectedCallLeg is: " + moveConnectedCallLeg + " so will hangup relatedCall: " + relatedCall.path());
                }
                relatedCall.tell(new Hangup(), null);
//                getContext().stop(relatedCall);
            }
        }
        if (listOfRelatedCalls != null) {
            for (ActorRef branch : listOfRelatedCalls) {
                branch.tell(new Hangup(), null);
            }
            if (logger.isInfoEnabled()) {
                String msg = String.format("LiveCallModification request while dial forking, terminated %d calls", listOfRelatedCalls.size());
                logger.info(msg);
            }
        }
    }

    private void outbound(final Object message, final ActorRef sender) throws ServletParseException {
        final CreateCall request = (CreateCall) message;
        ExtensionController ec = ExtensionController.getInstance();
        ExtensionResponse extRes = ec.executePreOutboundAction(request, this.extensions);
        switch (request.type()) {
            case CLIENT: {
                if (extRes.isAllowed()) {
                    ClientsDao clients = storage.getClientsDao();
                    String clientName = request.to().replaceFirst("client:", "");
                    final Client client = clients.getClient(clientName, storage.getAccountsDao().getAccount(request.accountId()).getOrganizationSid());
                    if (client != null) {
                        long delay = pushNotificationServerHelper.sendPushNotificationIfNeeded(client.getPushClientIdentity());
                        system.scheduler().scheduleOnce(Duration.create(delay, TimeUnit.MILLISECONDS), new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    outboundToClient(request, sender, client);

                                    ExtensionController.getInstance().executePostOutboundAction(request, extensions);
                                } catch (ServletParseException e) {
                                    throw new RuntimeException(e);
                                }
                            }
                        }, system.dispatcher());
                    } else {
                        String errMsg = "The SIP Client " + request.to() + " is not registered or does not exist";
                        logger.warning(errMsg);
                        sendNotification(request.accountId(), errMsg, 11008, "error", true);
                        sender.tell(new CallManagerResponse<ActorRef>(new NullPointerException(errMsg), request), self());
                    }
                } else {
                    //Extensions didn't allowed this call
                    final String errMsg = "Not Allowed to make this outbound call";
                    logger.warning(errMsg);
                    sender.tell(new CallManagerResponse<ActorRef>(new RestcommExtensionException(errMsg), request), self());
                }
                ec.executePostOutboundAction(request, this.extensions);
                break;
            }
            case PSTN: {
                if (extRes.isAllowed()) {
                    outboundToPstn(request, sender);
                } else {
                    //Extensions didn't allowed this call
                    final String errMsg = "Not Allowed to make this outbound call";
                    logger.warning(errMsg);
                    sender.tell(new CallManagerResponse<ActorRef>(new RestcommExtensionException(errMsg), request), self());
                }
                ec.executePostOutboundAction(request, this.extensions);
                break;
            }
            case SIP: {
                if (actAsImsUa) {
                    outboundToIms(request, sender);
                } else if (request.isAllowed()) {
                    outboundToSip(request, sender);
                } else {
                    //Extensions didn't allowed this call
                    final String errMsg = "Not Allowed to make this outbound call";
                    logger.warning(errMsg);
                    sender.tell(new CallManagerResponse<ActorRef>(new RestcommExtensionException(errMsg), request), self());
                }
                ec.executePostOutboundAction(request, this.extensions);
                break;
            }
        }
    }

    private void outboundToClient(final CreateCall request, final ActorRef sender, final Client client) throws ServletParseException {
        SipURI outboundIntf = null;
        SipURI from = null;
        SipURI to = null;
        boolean webRTC = false;
        boolean isLBPresent = false;
        String customHeaders = request.getCustomHeaders();

        final RegistrationsDao registrationsDao = storage.getRegistrationsDao();

        //1, If this is a WebRTC client check if the instance is the current instance
        //2. Check if the client has more than one registrations

        List<Registration> registrationToDial = new CopyOnWriteArrayList<Registration>();
        Sid organizationSid = storage.getAccountsDao().getAccount(request.accountId()).getOrganizationSid();

        List<Registration> registrations = registrationsDao.getRegistrations(client.getLogin(), organizationSid);
        if (registrations != null && registrations.size() > 0) {
            if (logger.isInfoEnabled()) {
                logger.info("Preparing call for client: " + client + ". There are " + registrations.size() + " registrations at the database for this client");
            }
            for (Registration registration : registrations) {
                if (registration.isWebRTC()) {
                    if (registration.isLBPresent()) {
                        if (logger.isInfoEnabled())
                            logger.info("WebRTC registration behind LB. Will add WebRTC registration: " + registration.getLocation() + " to the list to be dialed for client: " + client);
                        registrationToDial.add(registration);
                    } else {
                        //If this is a WebRTC client registration, check that the InstanceId of the registration is for the current Restcomm instance
                        if ((registration.getInstanceId() != null && !registration.getInstanceId().equals(RestcommConfiguration.getInstance().getMain().getInstanceId()))) {
                            logger.warning("Cannot create call for user agent: " + registration.getLocation() + " since this is a webrtc client registered in another Restcomm instance.");
                        } else {
                            if (logger.isInfoEnabled())
                                logger.info("Will add WebRTC registration: " + registration.getLocation() + " to the list to be dialed for client: " + client);
                            registrationToDial.add(registration);
                        }
                    }
                } else {
                    if (logger.isInfoEnabled())
                        logger.info("Will add registration: " + registration.getLocation() + " to the list to be dialed for client: " + client);
                    registrationToDial.add(registration);
                }
            }
        } else {
            String errMsg = "The SIP Client " + request.to() + " is not registered or does not exist";
            logger.warning(errMsg);
            sendNotification(request.accountId(), errMsg, 11008, "error", true);
            sender.tell(new CallManagerResponse<ActorRef>(new NullPointerException(errMsg), request), self());
            return;
        }

        if (registrationToDial.size() > 0) {
            if (logger.isInfoEnabled()) {
                if (registrationToDial.size() > 1) {
                    logger.info("Preparing call for client: " + client + ", after WebRTC check, Restcomm have to dial :" + registrationToDial.size() + " registrations");
                }
            }
            List<ActorRef> calls = new CopyOnWriteArrayList<>();
            for (Registration registration : registrationToDial) {
                if (logger.isInfoEnabled())
                    logger.info("Will proceed to create call for client: " + registration.getLocation() + " registration instanceId: " + registration.getInstanceId() + " own InstanceId: " + RestcommConfiguration.getInstance().getMain().getInstanceId());
                String transport;
                if (registration.getLocation().contains("transport")) {
                    transport = registration.getLocation().split(";")[1].replace("transport=", "");
                    outboundIntf = outboundInterface(transport);
                } else {
                    transport = "udp";
                    outboundIntf = outboundInterface(transport);
                }
                if (outboundIntf == null) {
                    String errMsg = "The outbound interface for transport: " + transport + " is NULL, something is wrong with container, cannot proceed to call client " + request.to();
                    logger.error(errMsg);
                    sendNotification(request.accountId(), errMsg, 11008, "error", true);
                    sender.tell(new CallManagerResponse<ActorRef>(new NullPointerException(errMsg), request), self());
                    return;
                }
                if (request.from() != null && request.from().contains("@")) {
                    // https://github.com/Mobicents/RestComm/issues/150 if it contains @ it means this is a sip uri and we allow
                    // to use it directly
                    //from = (SipURI) sipFactory.createURI(request.from());
                    String[] f = request.from().split("@");
                    from = sipFactory.createSipURI(f[0], f[1]);
                } else if (request.from() != null) {
                    if (outboundIntf != null) {
                        from = sipFactory.createSipURI(request.from(), mediaExternalIp + ":" + outboundIntf.getPort());
                    } else {
                        logger.error("Outbound interface is null, cannot create From header to be used to Dial client: " + client);
                    }
                } else {
                    from = outboundIntf;
                }
                final String location = registration.getLocation();
                to = (SipURI) sipFactory.createURI(location);
                if (customHeaders != null) {
                    to = addCustomHeadersForToUri(customHeaders, to);
                }
                webRTC = registration.isWebRTC();
                if (from == null || to == null) {
                    //In case From or To are null we have to cancel outbound call and hnagup initial call if needed
                    final String errMsg = "From and/or To are null, we cannot proceed to the outbound call to: " + request.to();
                    logger.warning(errMsg);
                    sender.tell(new CallManagerResponse<ActorRef>(new NullPointerException(errMsg), request), self());
                } else {
                    if(useSbc) {
                        // To avoid using SDP with encryption enabled between RC and the SBC
                        // we need to disable webRTC as it will be handled on the last mile
                        // ie between SBC and Client and SBC will be responsible for that not RC
                        webRTC = false;
                    }
                    calls.add(createOutbound(request, from, to, webRTC));
                }
            }
            if (calls.size() > 0) {
                sender.tell(new CallManagerResponse<List<ActorRef>>(calls), self());
            }
        } else {
            String errMsg = "The SIP Client " + request.to() + " is not registered or does not exist";
            logger.warning(errMsg);
            sendNotification(request.accountId(), errMsg, 11008, "error", true);
            sender.tell(new CallManagerResponse<ActorRef>(new NullPointerException(errMsg), request), self());
        }
    }

    private SipURI addCustomHeadersForToUri (String customHeaders, SipURI to) {
        for (String customHeader: customHeaders.split("&")) {
            if (customHeader.contains("=")) {
                to.setHeader(customHeader.split("=")[0], customHeader.split("=")[1]);
            } else {
                String msg = String.format("Custom header %s not properly formatted", customHeader);
                if (logger.isDebugEnabled()) {
                    logger.debug(msg);
                }
            }
        }
        return to;
    }

    private void outboundToPstn(final CreateCall request, final ActorRef sender) throws ServletParseException {
        final String uri = (request.getOutboundProxy() != null && (!request.getOutboundProxy().isEmpty())) ? request.getOutboundProxy() : activeProxy;
        SipURI outboundIntf = null;
        SipURI from = null;
        SipURI to = null;
        String customHeaders = request.getCustomHeaders();

        final Configuration runtime = configuration.subset("runtime-settings");
        final boolean useLocalAddressAtFromHeader = runtime.getBoolean("use-local-address", false);

        final String proxyUsername = (request.username() != null) ? request.username() : activeProxyUsername;

        if (uri != null) {
            try {
                to = sipFactory.createSipURI(request.to(), uri);
                if (customHeaders != null) {
                    to = addCustomHeadersForToUri(customHeaders, to);
                }
                String transport = (to.getTransportParam() != null) ? to.getTransportParam() : "udp";
                outboundIntf = outboundInterface(transport);
                final boolean outboudproxyUserAtFromHeader = runtime.subset("outbound-proxy").getBoolean(
                        "outboudproxy-user-at-from-header");
                if (request.from() != null && request.from().contains("@")) {
                    // https://github.com/Mobicents/RestComm/issues/150 if it contains @ it means this is a sip uri and we allow
                    // to use it directly
                    from = (SipURI) sipFactory.createURI(request.from());
                } else if (useLocalAddressAtFromHeader) {
                    from = sipFactory.createSipURI(request.from(), mediaExternalIp + ":" + outboundIntf.getPort());
                } else {
                    if (outboudproxyUserAtFromHeader) {
                        // https://telestax.atlassian.net/browse/RESTCOMM-633. Use the outbound proxy username as the userpart
                        // of the sip uri for the From header
                        from = (SipURI) sipFactory.createSipURI(proxyUsername, uri);
                    } else {
                        from = sipFactory.createSipURI(request.from(), uri);
                    }
                }
                if (((SipURI) from).getUser() == null || ((SipURI) from).getUser() == "") {
                    if (uri != null) {
                        from = sipFactory.createSipURI(request.from(), uri);
                    } else {
                        from = (SipURI) sipFactory.createURI(request.from());
                    }
                }
            } catch (Exception exception) {
                sender.tell(new CallManagerResponse<ActorRef>(exception, request), self());
            }
            if (from == null || to == null) {
                //In case From or To are null we have to cancel outbound call and hnagup initial call if needed
                final String errMsg = "From and/or To are null, we cannot proceed to the outbound call to: " + request.to();
                logger.warning(errMsg);
                sender.tell(new CallManagerResponse<ActorRef>(new NullPointerException(errMsg), request), self());
            } else {
                sender.tell(new CallManagerResponse<ActorRef>(createOutbound(request, from, to, false)), self());
            }
        } else {
            String errMsg = "Cannot create call to: " + request.to() + ". The Active Outbound Proxy is null. Please check configuration";
            logger.warning(errMsg);
            sendNotification(request.accountId(), errMsg, 11008, "error", true);
            sender.tell(new CallManagerResponse<ActorRef>(new NullPointerException(errMsg), request), self());
        }
    }


    private void outboundToSip(final CreateCall request, final ActorRef sender) throws ServletParseException {
        final String uri = (request.getOutboundProxy() != null && (!request.getOutboundProxy().isEmpty())) ? request.getOutboundProxy() : "";
        SipURI outboundIntf = null;
        SipURI from = null;
        String customHeaders = request.getCustomHeaders();

        SipURI to = (SipURI) sipFactory.createURI(request.to());
        if (customHeaders != null) {
            to = addCustomHeadersForToUri(customHeaders, to);
        }
        SipURI outboundProxyURI;

        try {
            //NB: ifblock not really necessary, but we dont want
            //exceptions all the time
            if(!uri.isEmpty()){
                outboundProxyURI = (SipURI) sipFactory.createSipURI(null, uri);
                to.setHost(outboundProxyURI.getHost());
                if(outboundProxyURI.getPort()!= -1){
                    to.setPort(outboundProxyURI.getPort());
                }

                Iterator<String> params = outboundProxyURI.getParameterNames();
                while(params.hasNext()){
                    String param = params.next();
                    to.setParameter(param, outboundProxyURI.getParameter(param));
                }
            }
        } catch (Exception e) {
            if(logger.isDebugEnabled()){
                logger.debug("Exception: outboundProxy is "+uri+" "+e.getMessage());
            }
        }

        String transport = (to.getTransportParam() != null) ? to.getTransportParam() : "udp";
        outboundIntf = outboundInterface(transport);
        if (request.from() == null) {
            from = outboundInterface(transport);
        } else {
            if (request.from() != null && request.from().contains("@")) {
                // https://github.com/Mobicents/RestComm/issues/150 if it contains @ it means this is a sip uri and we
                // allow to use it directly
                from = (SipURI) sipFactory.createURI(request.from());
            } else {
                if(request.accountId() != null){
                    Organization fromOrganization = storage.getOrganizationsDao().getOrganization(storage.getAccountsDao().getAccount(request.accountId()).getOrganizationSid());
                    from = sipFactory.createSipURI(request.from(), fromOrganization.getDomainName());
                } else {
                    from = sipFactory.createSipURI(request.from(), outboundIntf.getHost() + ":" + outboundIntf.getPort());
                }
            }
        }
        if (from == null || to == null) {
            //In case From or To are null we have to cancel outbound call and hnagup initial call if needed
            final String errMsg = "From and/or To are null, we cannot proceed to the outbound call to: " + request.to();
            logger.warning(errMsg);
            sender.tell(new CallManagerResponse<ActorRef>(new NullPointerException(errMsg), request), self());
        } else {
            sender.tell(new CallManagerResponse<ActorRef>(createOutbound(request, from, to, false)), self());
        }
    }


    private ActorRef createOutbound(final CreateCall request, final SipURI from, final SipURI to, final boolean webRTC) {
        final Configuration runtime = configuration.subset("runtime-settings");
        final String proxyUsername = (request.username() != null) ? request.username() : activeProxyUsername;
        final String proxyPassword = (request.password() != null) ? request.password() : activeProxyPassword;

        final ActorRef call = call(null, request);
        final ActorRef self = self();
        final boolean userAtDisplayedName = runtime.subset("outbound-proxy").getBoolean("user-at-displayed-name");
        InitializeOutbound init;
        if (request.from() != null && !request.from().contains("@") && userAtDisplayedName) {
            init = new InitializeOutbound(request.from(), from, to, proxyUsername, proxyPassword, request.timeout(),
                    request.isFromApi(), runtime.getString("api-version"), request.accountId(), request.type(), storage, webRTC, request.mediaAttributes());
        } else {
            init = new InitializeOutbound(null, from, to, proxyUsername, proxyPassword, request.timeout(), request.isFromApi(),
                    runtime.getString("api-version"), request.accountId(), request.type(), storage, webRTC, request.mediaAttributes());
        }
        if (request.parentCallSid() != null) {
            init.setParentCallSid(request.parentCallSid());
        }
        call.tell(init, self);
        return call;
    }

    public void cancel(final Object message) throws IOException {
        final ActorRef self = self();
        final SipServletRequest request = (SipServletRequest) message;
        final SipApplicationSession application = request.getApplicationSession();

        // if this response is coming from a client that is in a p2p session with another registered client
        // we will just proxy the response
        SipServletRequest originalRequest = B2BUAHelper.getLinkedRequest(request);
        SipSession linkedB2BUASession = B2BUAHelper.getLinkedSession(request);
        if (originalRequest != null) {
            if (logger.isInfoEnabled()) {
                logger.info(String.format("B2BUA: Got CANCEL request: \n %s", request));
            }
            // SipServletRequest cancel = originalRequest.createCancel();
            request.getSession().setAttribute(B2BUAHelper.B2BUA_LAST_REQUEST, request);
            String sessionState = linkedB2BUASession.getState().name();
            SipServletResponse lastFinalResponse = (SipServletResponse) originalRequest.getSession().getAttribute(
                    B2BUAHelper.B2BUA_LAST_FINAL_RESPONSE);

            if ((sessionState == SipSession.State.INITIAL.name() || sessionState == SipSession.State.EARLY.name())
                    && !(lastFinalResponse != null && (lastFinalResponse.getStatus() == 401 || lastFinalResponse.getStatus() == 407))) {
                SipServletRequest clonedCancel = originalRequest.createCancel();
                linkedB2BUASession.setAttribute(B2BUAHelper.B2BUA_LAST_REQUEST, clonedCancel);
                clonedCancel.send();
            } else {
                SipServletRequest clonedBye = linkedB2BUASession.createRequest("BYE");
                linkedB2BUASession.setAttribute(B2BUAHelper.B2BUA_LAST_REQUEST, clonedBye);
                clonedBye.send();
            }
            // SipServletRequest cancel = originalRequest.createCancel();
            // cancel.send();
            // originalRequest.createCancel().send();
        } else {
            final ActorRef call = (ActorRef) application.getAttribute(Call.class.getName());
            if (call != null)
                call.tell(request, self);
        }
    }

    public void bye(final Object message) throws IOException {
        final ActorRef self = self();
        final SipServletRequest request = (SipServletRequest) message;
        final SipApplicationSession application = request.getApplicationSession();

        // if this response is coming from a client that is in a p2p session with another registered client
        // we will just proxy the response
        SipSession linkedB2BUASession = B2BUAHelper.getLinkedSession(request);
        if (linkedB2BUASession != null) {
            if (logger.isInfoEnabled()) {
                logger.info(String.format("B2BUA: Got BYE request: \n %s", request));
            }

            //Prepare the BYE request to the linked session
            request.getSession().setAttribute(B2BUAHelper.B2BUA_LAST_REQUEST, request);
            SipServletRequest clonedBye = linkedB2BUASession.createRequest("BYE");
            linkedB2BUASession.setAttribute(B2BUAHelper.B2BUA_LAST_REQUEST, clonedBye);

            if (patchForNatB2BUASessions) {
                // Issue #307: https://telestax.atlassian.net/browse/RESTCOMM-307
                SipURI toInetUri = (SipURI) request.getSession().getAttribute(B2BUAHelper.TO_INET_URI);
                SipURI fromInetUri = (SipURI) request.getSession().getAttribute(B2BUAHelper.FROM_INET_URI);
                InetAddress byeRURI = null;
                try {
                    byeRURI = DNSUtils.getByName(((SipURI) clonedBye.getRequestURI()).getHost());
                } catch (UnknownHostException e) {
                }
                boolean isBehindLB = false;
                final String initialIpBeforeLB = request.getHeader("X-Sip-Balancer-InitialRemoteAddr");
                String initialPortBeforeLB = request.getHeader("X-Sip-Balancer-InitialRemotePort");
                if (initialIpBeforeLB != null) {
                    if (initialPortBeforeLB == null)
                        initialPortBeforeLB = "5060";
                    if (logger.isDebugEnabled()) {
                        logger.debug("We are behind load balancer, checking if the request URI needs to be patched");
                    }
                    isBehindLB = true;
                }
                if (logger.isDebugEnabled()) {
                    logger.debug("toInetUri: " + toInetUri + " fromInetUri: " + fromInetUri + " byeRURI: " + byeRURI + " initialIpBeforeLB: " + initialIpBeforeLB
                            + " initialPortBeforeLB: " + initialPortBeforeLB);
                }
                if (toInetUri != null && byeRURI == null) {
                    if (logger.isInfoEnabled()) {
                        logger.info("Using the real To inet ip address of the sip client " + toInetUri.toString()
                                + " as a request uri of the CloneBye request");
                    }
                    clonedBye.setRequestURI(toInetUri);
                } else if (toInetUri != null
                        && (byeRURI.isSiteLocalAddress() || byeRURI.isAnyLocalAddress() || byeRURI.isLoopbackAddress())) {
                    if (logger.isInfoEnabled()) {
                        logger.info("Using the real To inet ip address of the sip client " + toInetUri.toString()
                                + " as a request uri of the CloneBye request");
                    }
                    clonedBye.setRequestURI(toInetUri);
                } else if (fromInetUri != null
                        && (byeRURI.isSiteLocalAddress() || byeRURI.isAnyLocalAddress() || byeRURI.isLoopbackAddress())) {
                    if (isBehindLB) {
                        // https://github.com/RestComm/Restcomm-Connect/issues/1357
                        boolean patchRURI = isLBPatchRURI(clonedBye, initialIpBeforeLB, initialPortBeforeLB);
                        if (patchRURI) {
                            if (logger.isDebugEnabled()) {
                                logger.debug("We are behind load balancer, but Using the real ip address of the sip client " + fromInetUri.toString()
                                        + " as a request uri of the CloneBye request");
                            }
                            clonedBye.setRequestURI(fromInetUri);
                        }
                    } else {
                        if (logger.isInfoEnabled()) {
                            logger.info("Using the real From inet ip  address of the sip client " + fromInetUri.toString()
                                    + " as a request uri of the CloneBye request");
                        }
                        clonedBye.setRequestURI(fromInetUri);
                    }
                } else if (toInetUri == null
                        && (byeRURI.isSiteLocalAddress() || byeRURI.isAnyLocalAddress() || byeRURI.isLoopbackAddress())) {
                    if (logger.isInfoEnabled()) {
                        logger.info("Public IP toInetUri from SipSession is null, will check LB headers from last Response");
                    }
                    if (isBehindLB) {
                        String realIP = initialIpBeforeLB + ":" + initialPortBeforeLB;
                        SipURI uri = sipFactory.createSipURI(null, realIP);
                        boolean patchRURI = isLBPatchRURI(clonedBye, initialIpBeforeLB, initialPortBeforeLB);
                        if (patchRURI) {
                            if (logger.isDebugEnabled()) {
                                logger.debug("We are behind load balancer, will use: " + initialIpBeforeLB + ":"
                                        + initialPortBeforeLB + " for the cloned BYE message");
                            }
                            clonedBye.setRequestURI(uri);
                        }
                        if (logger.isInfoEnabled()) {
                            logger.info("We are behind load balancer, will use Initial Remote Address " + initialIpBeforeLB + ":"
                                    + initialPortBeforeLB + " for the cloned BYE request");
                        }
                    } else {
                        if (logger.isInfoEnabled()) {
                            logger.info("LB Headers are also null");
                        }
                    }
                }
            }
            B2BUAHelper.updateCDR(system, request, CallStateChanged.State.COMPLETED);
            //Prepare 200 OK for received BYE
            SipServletResponse okay = request.createResponse(Response.OK);
            okay.send();
            //Send the Cloned BYE
            if (logger.isInfoEnabled()) {
                logger.info(String.format("B2BUA: Will send out Cloned BYE request: \n %s", clonedBye));
            }
            clonedBye.send();
        } else {
            final ActorRef call = (ActorRef) application.getAttribute(Call.class.getName());
            if (call != null)
                call.tell(request, self);
        }
    }

    public void response(final Object message) throws IOException {
        final ActorRef self = self();
        final SipServletResponse response = (SipServletResponse) message;

        // If Allow-Falback is true, check for error reponses and switch proxy if needed
        if (allowFallback)
            checkErrorResponse(response);

        final SipApplicationSession application = response.getApplicationSession();

        // if this response is coming from a client that is in a p2p session with another registered client
        // we will just proxy the response
        SipSession linkedB2BUASession = B2BUAHelper.getLinkedSession(response);
        if (linkedB2BUASession!=null) {
            if (response.getStatus() == SipServletResponse.SC_PROXY_AUTHENTICATION_REQUIRED
                    || response.getStatus() == SipServletResponse.SC_UNAUTHORIZED) {
                AuthInfo authInfo = sipFactory.createAuthInfo();
                String authHeader = response.getHeader("Proxy-Authenticate");
                if (authHeader == null) {
                    authHeader = response.getHeader("WWW-Authenticate");
                }
                String tempRealm = authHeader.substring(authHeader.indexOf("realm=\"") + "realm=\"".length());
                String realm = tempRealm.substring(0, tempRealm.indexOf("\""));
                authInfo.addAuthInfo(response.getStatus(), realm, activeProxyUsername, activeProxyPassword);
                SipServletRequest challengeRequest = response.getSession().createRequest(response.getRequest().getMethod());
                response.getSession().setAttribute(B2BUAHelper.B2BUA_LAST_FINAL_RESPONSE, response);
                challengeRequest.addAuthHeader(response, authInfo);
                SipServletRequest invite = response.getRequest();
                challengeRequest.setContent(invite.getContent(), invite.getContentType());
                invite = challengeRequest;

                Map<String,ArrayList<String>> extensionHeaders = (Map<String,ArrayList<String>>)linkedB2BUASession.getAttribute(B2BUAHelper.EXTENSION_HEADERS);
                B2BUAHelper.addHeadersToMessage(challengeRequest, extensionHeaders, sipFactory);
                challengeRequest.send();
            } else {
                B2BUAHelper.forwardResponse(system, response, patchForNatB2BUASessions);
            }
        } else {
            if (application.isValid()) {
                // otherwise the response is coming back to a Voice app hosted by Restcomm
                final ActorRef call = (ActorRef) application.getAttribute(Call.class.getName());
                call.tell(response, self);
            }
        }
    }

    public ActorRef lookup(final Object message) {
        final GetCall getCall = (GetCall) message;
        final String callPath = getCall.getIdentifier();

        final ActorContext context = getContext();

        // TODO: The context.actorFor has been depreciated for actorSelection at the latest Akka release.
        ActorRef call = null;
        if (callPath != null) {
            try {
                call = context.actorFor(callPath);
            } catch (Exception e) {
                if (logger.isInfoEnabled()) {
                    logger.info("Problem during call lookup, callPath: " + callPath);
                }
                return null;
            }
        } else {
            if (logger.isInfoEnabled()) {
                logger.info("CallPath is null, call lookup cannot be executed");
            }
        }
        return call;
    }

    public void timeout(final Object message) {
        final ActorRef self = self();
        final SipApplicationSessionEvent event = (SipApplicationSessionEvent) message;
        final SipApplicationSession application = event.getApplicationSession();
        final ActorRef call = (ActorRef) application.getAttribute(Call.class.getName());
        final ReceiveTimeout timeout = ReceiveTimeout.getInstance();
        call.tell(timeout, self);
    }

    public void checkErrorResponse(SipServletResponse response) {
        // Response should not be a proxy branch response and request should be initial and INVITE
        if (!response.isBranchResponse()
                && (response.getRequest().getMethod().equalsIgnoreCase("INVITE") && response.getRequest().isInitial())) {

            final int status = response.getStatus();
            // Response status should be > 400 BUT NOT 401, 404, 407
            if (status != SipServletResponse.SC_UNAUTHORIZED && status != SipServletResponse.SC_PROXY_AUTHENTICATION_REQUIRED
                    && status != SipServletResponse.SC_NOT_FOUND && status > 400) {

                int failures = numberOfFailedCalls.incrementAndGet();
                if (logger.isInfoEnabled()) {
                    logger.info("A total number of " + failures + " failures have now been counted.");
                }

                if (failures >= maxNumberOfFailedCalls) {
                    if (logger.isInfoEnabled()) {
                        logger.info("Max number of failed calls has been reached trying to switch over proxy.");
                        logger.info("Current proxy: " + getActiveProxy().get("ActiveProxy"));
                    }
                    switchProxy();
                    if (logger.isInfoEnabled()) {
                        logger.info("Switched to proxy: " + getActiveProxy().get("ActiveProxy"));
                    }
                    numberOfFailedCalls.set(0);
                }
            }
        }
    }

    public Map<String, String> getActiveProxy() {
        Map<String, String> activeProxyMap = new ConcurrentHashMap<String, String>();
        activeProxyMap.put("ActiveProxy", this.activeProxy);
        return activeProxyMap;
    }

    public Map<String, String> switchProxy() {
        if (activeProxy.equalsIgnoreCase(primaryProxyUri)) {
            activeProxy = fallBackProxyUri;
            activeProxyUsername = fallBackProxyUsername;
            activeProxyPassword = fallBackProxyPassword;
            useFallbackProxy.set(true);
        } else if (allowFallbackToPrimary) {
            activeProxy = primaryProxyUri;
            activeProxyUsername = primaryProxyUsername;
            activeProxyPassword = primaryProxyPassword;
            useFallbackProxy.set(false);
        }
        final Notification notification = notification(null, WARNING_NOTIFICATION, 14110,
                "Max number of failed calls has been reached! Outbound proxy switched");
        final NotificationsDao notifications = storage.getNotificationsDao();
        notifications.addNotification(notification);
        return getActiveProxy();
    }

    public Map<String, String> getProxies(final Object message) {
        Map<String, String> proxies = new ConcurrentHashMap<String, String>();

        proxies.put("ActiveProxy", activeProxy);
        proxies.put("UsingFallBackProxy", useFallbackProxy.toString());
        proxies.put("AllowFallbackToPrimary", String.valueOf(allowFallbackToPrimary));
        proxies.put("PrimaryProxy", primaryProxyUri);
        proxies.put("FallbackProxy", fallBackProxyUri);

        return proxies;
    }

    private Notification notification(Sid accountId, final int log, final int error, final String message) {
        String version = configuration.subset("runtime-settings").getString("api-version");
        if (accountId == null) {
            if (switchProxyRequest != null) {
                accountId = switchProxyRequest.getSid();
            } else {
                accountId = new Sid("ACae6e420f425248d6a26948c17a9e2acf");
            }
        }

        final Notification.Builder builder = Notification.builder();
        final Sid sid = Sid.generate(Sid.Type.NOTIFICATION);
        builder.setSid(sid);
        builder.setAccountSid(accountId);
        // builder.setCallSid(callSid);
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
        try {
            builder.setRequestUrl(new URI(""));
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }

        builder.setRequestMethod("");
        builder.setRequestVariables("");
        buffer = new StringBuilder();
        buffer.append("/").append(version).append("/Accounts/");
        buffer.append(accountId.toString()).append("/Notifications/");
        buffer.append(sid.toString());
        final URI uri = URI.create(buffer.toString());
        builder.setUri(uri);
        return builder.build();
    }

    private SipURI outboundInterface(String transport) {
        SipURI result = null;
        @SuppressWarnings("unchecked") final List<SipURI> uris = (List<SipURI>) context.getAttribute(OUTBOUND_INTERFACES);
        for (final SipURI uri : uris) {
            final String interfaceTransport = uri.getTransportParam();
            if (transport.equalsIgnoreCase(interfaceTransport)) {
                result = uri;
            }
        }
        return result;
    }

    private boolean isFromIms(final SipServletRequest request) throws ServletParseException {
        SipURI uri = (SipURI) request.getRequestURI();
        return uri.getUser() == null;
    }

    private Registration findRegistration(javax.servlet.sip.URI regUri) {
        if (regUri == null) {
            return null;
        }
        String formattedNumber = null;
        if (regUri.isSipURI()) {
            formattedNumber = ((SipURI) regUri).getUser().replaceFirst("\\+", "");
        } else {
            formattedNumber = ((TelURL) regUri).getPhoneNumber().replaceFirst("\\+", "");
        }
        if (logger.isInfoEnabled()) {
            logger.info("looking for registrations for number: " + formattedNumber);
        }
        final RegistrationsDao registrationsDao = storage.getRegistrationsDao();
        Sid orgSid = OrganizationUtil.getOrganizationSidBySipURIHost(storage, (SipURI)regUri);
        if(orgSid == null){
            logger.error("Null Organization: regUri: "+regUri);
        }
        List<Registration> registrations = registrationsDao.getRegistrations(formattedNumber, orgSid);

        if (registrations == null || registrations.size() == 0) {
            return null;
        } else {
            return registrations.get(0);
        }
    }

    private void imsProxyThroughMediaServer(final SipServletRequest request, final Client client,
                                            final javax.servlet.sip.URI destUri, final String user, final String password, boolean isFromIms)
            throws IOException {
        javax.servlet.sip.URI srcUri = request.getFrom().getURI();
        if (logger.isInfoEnabled()) {
            logger.info("imsProxyThroughMediaServer, isFromIms: " + isFromIms +
                    ", destUri: " + destUri + ", srcUri: " + srcUri);
        }
        final Configuration runtime = configuration.subset("runtime-settings");
        String destNumber = destUri.toString();
        String rcml = "<Response><Dial>" + destNumber + "</Dial></Response>";

        javax.servlet.sip.URI regUri = null;
        if (isFromIms) {
            regUri = destUri;
        } else {
            regUri = srcUri;
        }
        // Lookup of registration is based on srcUri (if call is toward IMS),
        // or is based on destUri (if call is from IMS)
        Registration reg = findRegistration(regUri);
        if (reg == null) {
            if (logger.isInfoEnabled()) {
                logger.info("registrations not found");
            }
            final SipServletResponse response = request.createResponse(SC_NOT_FOUND);
            response.send();
            // We didn't find anyway to handle the call.
            String errMsg = "Call cannot be processed because the registration: " + regUri.toString()
                    + "cannot be found";
            sendNotification(null, errMsg, 11005, "error", true);
            return;
        } else {
            if (isFromIms) {
                rcml = "<Response><Dial><Client>" + reg.getUserName() + "</Client></Dial></Response>";
            }

            if (logger.isInfoEnabled()) {
                logger.info("rcml: " + rcml);
            }

            final VoiceInterpreterParams.Builder builder = new VoiceInterpreterParams.Builder();
            builder.setConfiguration(configuration);
            builder.setStorage(storage);
            builder.setCallManager(self());
            builder.setConferenceCenter(conferences);
            builder.setBridgeManager(bridges);
            builder.setSmsService(sms);
            builder.setAccount(Sid.generate(Sid.Type.ACCOUNT, imsAccount));
            builder.setVersion(runtime.getString("api-version"));
            builder.setRcml(rcml);
            builder.setMonitoring(monitoring);
            builder.setAsImsUa(actAsImsUa);
            if (actAsImsUa) {
                builder.setImsUaLogin(user);
                builder.setImsUaPassword(password);
            }
            final Props props = VoiceInterpreter.props(builder.build());
            final ActorRef interpreter = getContext().actorOf(props);
            final ActorRef call = call(null, null);
            final SipApplicationSession application = request.getApplicationSession();
            application.setAttribute(Call.class.getName(), call);
            call.tell(request, self());
            interpreter.tell(new StartInterpreter(call), self());
        }
    }

    private void outboundToIms(final CreateCall request, final ActorRef sender) throws ServletParseException {
        if (logger.isInfoEnabled()) {
            logger.info("outboundToIms: " + request);
        }
        SipURI from;
        SipURI to;
        String customHeaders = request.getCustomHeaders();

        to = (SipURI) sipFactory.createURI(request.to());
        if (customHeaders != null) {
            to = addCustomHeadersForToUri(customHeaders, to);
        }
        if (request.from() == null) {
            from = sipFactory.createSipURI(null, imsDomain);
        } else {
            if (request.from() != null && request.from().contains("@")) {
                // https://github.com/Mobicents/RestComm/issues/150 if it contains @ it means this is a sip uri and we
                // allow to use it directly
                String[] f = request.from().split("@");
                from = sipFactory.createSipURI(f[0], f[1]);
            } else {
                from = sipFactory.createSipURI(request.from(), imsDomain);
            }
        }
        if (from == null || to == null) {
            //In case From or To are null we have to cancel outbound call and hnagup initial call if needed
            final String errMsg = "From and/or To are null, we cannot proceed to the outbound call to: " + request.to();
            logger.error(errMsg);
            sender.tell(new CallManagerResponse<ActorRef>(new NullPointerException(errMsg), request), self());
        } else {
            final ActorRef call = call(null, request);
            final ActorRef self = self();
            final Configuration runtime = configuration.subset("runtime-settings");
            if (logger.isInfoEnabled()) {
                logger.info("outboundToIms: from: " + from + ", to: " + to);
            }
            final String proxyUsername = (request.username() != null) ? request.username() : activeProxyUsername;
            final String proxyPassword = (request.password() != null) ? request.password() : activeProxyPassword;
            boolean isToWebRTC = false;
            Registration toReg = findRegistration(to);
            if (toReg != null) {
                isToWebRTC = toReg.isWebRTC();
            }
            if (logger.isInfoEnabled()) {
                logger.info("outboundToIms: isToWebRTC: " + isToWebRTC);
            }
            InitializeOutbound init = new InitializeOutbound(request.from(), from, to, proxyUsername, proxyPassword, request.timeout(),
                    request.isFromApi(), runtime.getString("api-version"), request.accountId(), request.type(), storage, isToWebRTC,
                    true, imsProxyAddress, imsProxyPort, request.mediaAttributes());
            if (request.parentCallSid() != null) {
                init.setParentCallSid(request.parentCallSid());
            }
            call.tell(init, self);
            sender.tell(new CallManagerResponse<ActorRef>(call), self());
        }
    }
}
