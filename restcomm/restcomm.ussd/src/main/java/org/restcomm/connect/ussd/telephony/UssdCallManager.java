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

import static javax.servlet.sip.SipServlet.OUTBOUND_INTERFACES;
import static javax.servlet.sip.SipServletResponse.SC_BAD_REQUEST;
import static javax.servlet.sip.SipServletResponse.SC_FORBIDDEN;
import static javax.servlet.sip.SipServletResponse.SC_NOT_FOUND;
import static javax.servlet.sip.SipServletResponse.SC_OK;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.regex.Pattern;

import javax.servlet.ServletContext;
import javax.servlet.sip.ServletParseException;
import javax.servlet.sip.SipApplicationSession;
import javax.servlet.sip.SipFactory;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;
import javax.servlet.sip.SipURI;

import org.apache.commons.configuration.Configuration;
import org.joda.time.DateTime;
import org.restcomm.connect.commons.configuration.RestcommConfiguration;
import org.restcomm.connect.commons.configuration.sets.RcmlserverConfigurationSet;
import org.restcomm.connect.commons.dao.Sid;
import org.restcomm.connect.commons.faulttolerance.RestcommUntypedActor;
import org.restcomm.connect.commons.util.UriUtils;
import org.restcomm.connect.core.service.api.NumberSelectorService;
import org.restcomm.connect.dao.AccountsDao;
import org.restcomm.connect.dao.ApplicationsDao;
import org.restcomm.connect.dao.DaoManager;
import org.restcomm.connect.dao.NotificationsDao;
import org.restcomm.connect.dao.common.OrganizationUtil;
import org.restcomm.connect.dao.entities.Account;
import org.restcomm.connect.dao.entities.Application;
import org.restcomm.connect.dao.entities.IncomingPhoneNumber;
import org.restcomm.connect.dao.entities.Notification;
import org.restcomm.connect.extension.api.ExtensionResponse;
import org.restcomm.connect.extension.api.ExtensionType;
import org.restcomm.connect.extension.api.IExtensionFeatureAccessRequest;
import org.restcomm.connect.extension.api.RestcommExtensionGeneric;
import org.restcomm.connect.extension.controller.ExtensionController;
import org.restcomm.connect.http.client.rcmlserver.resolver.RcmlserverResolver;
import org.restcomm.connect.interpreter.SIPOrganizationUtil;
import org.restcomm.connect.interpreter.StartInterpreter;
import org.restcomm.connect.telephony.api.CallManagerResponse;
import org.restcomm.connect.telephony.api.CreateCall;
import org.restcomm.connect.telephony.api.ExecuteCallScript;
import org.restcomm.connect.telephony.api.FeatureAccessRequest;
import org.restcomm.connect.telephony.api.InitializeOutbound;
import org.restcomm.connect.telephony.api.util.CallControlHelper;
import org.restcomm.connect.ussd.interpreter.UssdInterpreter;
import org.restcomm.connect.ussd.interpreter.UssdInterpreterParams;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.actor.UntypedActorFactory;
import akka.event.Logging;
import akka.event.LoggingAdapter;

/**
 * @author <a href="mailto:gvagenas@gmail.com">gvagenas</a>
 */
public class UssdCallManager extends RestcommUntypedActor {

    static final int ERROR_NOTIFICATION = 0;
    static final int WARNING_NOTIFICATION = 1;
    static final Pattern PATTERN = Pattern.compile("[\\*#0-9]{1,12}");

    static final int ACCOUNT_NOT_ACTIVE_FAILURE_RESPONSE_CODE = SC_FORBIDDEN;

    private final Configuration configuration;
    private final ServletContext context;
    private final SipFactory sipFactory;
    private final DaoManager storage;
    private final String ussdGatewayUri;
    private final String ussdGatewayUsername;
    private final String ussdGatewayPassword;
    private final NumberSelectorService numberSelector;
    private final LoggingAdapter logger = Logging.getLogger(getContext().system(), this);
    //List of extensions for UssdCallManager
    List<RestcommExtensionGeneric> extensions;
    private CreateCall createCallRequest;
    // configurable switch whether to use the To field in a SIP header to determine the callee address
    // alternatively the Request URI can be used
    private boolean useTo;

    /**
     * @param configuration
     * @param context
     * @param factory
     * @param storage
     */
    public UssdCallManager(Configuration configuration, ServletContext context, SipFactory factory, DaoManager storage) {
        super();
        this.configuration = configuration;
        this.context = context;
        this.sipFactory = factory;
        this.storage = storage;
        final Configuration runtime = configuration.subset("runtime-settings");
        final Configuration ussdGatewayConfig = runtime.subset("ussd-gateway");
        this.ussdGatewayUri = ussdGatewayConfig.getString("ussd-gateway-uri");
        this.ussdGatewayUsername = ussdGatewayConfig.getString("ussd-gateway-user");
        this.ussdGatewayPassword = ussdGatewayConfig.getString("ussd-gateway-password");
        numberSelector = (NumberSelectorService)context.getAttribute(NumberSelectorService.class.getName());

        extensions = ExtensionController.getInstance().getExtensions(ExtensionType.FeatureAccessControl);
    }

    private ActorRef ussdCall() {
        final Props props = new Props(new UntypedActorFactory() {
            private static final long serialVersionUID = 1L;

            @Override
            public UntypedActor create() throws Exception {
                return new UssdCall(sipFactory);
            }
        });
        return getContext().actorOf(props);
    }

    private void check(final Object message) throws IOException {
        final SipServletRequest request = (SipServletRequest) message;
        if (request.getContentLength() == 0) {
            String contentType = request.getContentType();
            if (!("application/vnd.3gpp.ussd+xml".equals(contentType))) {
                final SipServletResponse response = request.createResponse(SC_BAD_REQUEST);
                response.send();
            }
        }
    }

    @Override
    public void onReceive(final Object message) throws Exception {

        final Class<?> klass = message.getClass();
        final ActorRef self = self();
        final ActorRef sender = sender();
        if (message instanceof SipServletRequest) {
            final SipServletRequest request = (SipServletRequest) message;
            final String method = request.getMethod();
            if ("INVITE".equalsIgnoreCase(method)) {
                check(request);
                invite(request);
            } else if ("INFO".equalsIgnoreCase(method)) {
                processRequest(request);
            } else if ("ACK".equalsIgnoreCase(method)) {
                processRequest(request);
            } else if ("BYE".equalsIgnoreCase(method)) {
                processRequest(request);
            } else if ("CANCEL".equalsIgnoreCase(method)) {
                processRequest(request);
            }
        } else if (message instanceof SipServletResponse) {
            response(message);
        } else if (CreateCall.class.equals(klass)) {
            try {
                this.createCallRequest = (CreateCall) message;
                sender.tell(new CallManagerResponse<ActorRef>(outbound(message)), self);
            } catch (final Exception exception) {
                sender.tell(new CallManagerResponse<ActorRef>(exception), self);
            }
        } else if (ExecuteCallScript.class.equals(klass)) {
            execute(message);
        }

    }

    private void invite(final Object message) throws Exception {
        final SipServletRequest request = (SipServletRequest) message;
        // Make sure we handle re-invites properly.
        if (!request.isInitial()) {
            final SipServletResponse okay = request.createResponse(SC_OK);
            okay.send();
            return;
        }

        final AccountsDao accounts = storage.getAccountsDao();
        final ApplicationsDao applications = storage.getApplicationsDao();
        final String toUser = CallControlHelper.getUserSipId(request, useTo);
        final SipURI fromUri = (SipURI) request.getFrom().getURI();

        Sid sourceOrganizationSid = OrganizationUtil.getOrganizationSidBySipURIHost(storage, fromUri);
        Sid destOrg = SIPOrganizationUtil.searchOrganizationBySIPRequest(storage.getOrganizationsDao(), request);

        IncomingPhoneNumber number = getIncomingPhoneNumber(toUser, sourceOrganizationSid, destOrg);

        if (number != null) {
            Account numAccount = accounts.getAccount(number.getAccountSid());
            if (!numAccount.getStatus().equals(Account.Status.ACTIVE)) {
                //reject call since the number belongs to an an account which is not ACTIVE
                final SipServletResponse response = request.createResponse(ACCOUNT_NOT_ACTIVE_FAILURE_RESPONSE_CODE, "Account is not Active");
                response.send();

                String msg = String.format("Restcomm rejects this USSD Session because number's %s account %s is not ACTIVE, current state %s", number.getPhoneNumber(), numAccount.getSid(), numAccount.getStatus());
                if (logger.isDebugEnabled()) {
                    logger.debug(msg);
                }
                sendNotification(msg, 11005, "Error", true);
                return;
            }
        }

        if(logger.isDebugEnabled()) {
            logger.debug("sourceOrganizationSid: " + sourceOrganizationSid);
        }
        if(sourceOrganizationSid == null){
            logger.error("Null Organization: fromUri: "+fromUri);
        }
        if (redirectToHostedVoiceApp(request, accounts, applications, number)) {
            return;
        }

        // We didn't find anyway to handle the call.
        final SipServletResponse response = request.createResponse(SC_NOT_FOUND);
        response.send();
    }

    private IncomingPhoneNumber getIncomingPhoneNumber(String phone, Sid sourceOrganizationSid, Sid destOrg) {
        IncomingPhoneNumber number = numberSelector.searchNumber(phone, sourceOrganizationSid, destOrg);

        return number;
    }

    /**
     * Try to locate a hosted voice app corresponding to the callee/To address. If one is found, begin execution, otherwise
     * return false;
     *
     * @param request
     * @param accounts
     * @param applications
     * @throws Exception
     */
    private boolean redirectToHostedVoiceApp(final SipServletRequest request, final AccountsDao accounts,
                                             final ApplicationsDao applications, IncomingPhoneNumber number) throws Exception {
        boolean isFoundHostedApp = false;

        // This is a USSD Invite
        if (number != null) {

            ExtensionController ec = ExtensionController.getInstance();
            IExtensionFeatureAccessRequest far = new FeatureAccessRequest(FeatureAccessRequest.Feature.INBOUND_USSD, number.getAccountSid());
            ExtensionResponse er = ec.executePreInboundAction(far, extensions);

            if (er.isAllowed()) {
                final UssdInterpreterParams.Builder builder = new UssdInterpreterParams.Builder();
                builder.setConfiguration(configuration);
                builder.setStorage(storage);
                builder.setAccount(number.getAccountSid());
                builder.setVersion(number.getApiVersion());
                final Account account = accounts.getAccount(number.getAccountSid());
                builder.setEmailAddress(account.getEmailAddress());
                final Sid sid = number.getUssdApplicationSid();
                if (sid != null) {
                    final Application application = applications.getApplication(sid);
                    RcmlserverConfigurationSet rcmlserverConfig = RestcommConfiguration.getInstance().getRcmlserver();
                    RcmlserverResolver resolver = RcmlserverResolver.getInstance(rcmlserverConfig.getBaseUrl(), rcmlserverConfig.getApiPath());
                    builder.setUrl(UriUtils.resolve(resolver.resolveRelative(application.getRcmlUrl())));
                } else {
                    builder.setUrl(UriUtils.resolve(number.getUssdUrl()));
                }
                final String ussdMethod = number.getUssdMethod();
                if (ussdMethod == null || ussdMethod.isEmpty()) {
                    builder.setMethod("POST");
                } else {
                    builder.setMethod(ussdMethod);
                }
                if (number.getUssdFallbackUrl() != null)
                    builder.setFallbackUrl(number.getUssdFallbackUrl());
                builder.setFallbackMethod(number.getUssdFallbackMethod());
                builder.setStatusCallback(number.getStatusCallback());
                builder.setStatusCallbackMethod(number.getStatusCallbackMethod());
                final Props props = UssdInterpreter.props(builder.build());
                final ActorRef ussdInterpreter = getContext().actorOf(props);
                final ActorRef ussdCall = ussdCall();
                ussdCall.tell(request, self());

                ussdInterpreter.tell(new StartInterpreter(ussdCall), self());

                SipApplicationSession applicationSession = request.getApplicationSession();
                applicationSession.setAttribute("UssdCall", "true");
                applicationSession.setAttribute(UssdInterpreter.class.getName(), ussdInterpreter);
                applicationSession.setAttribute(UssdCall.class.getName(), ussdCall);
                isFoundHostedApp = true;
                ec.executePostInboundAction(far, extensions);
            } else {
                if (logger.isDebugEnabled()) {
                    final String errMsg = "Inbound USSD session is not Allowed";
                    logger.debug(errMsg);
                }
                String errMsg = "Inbound USSD session to Number: " + number.getPhoneNumber()
                        + " is not allowed";

                sendNotification(errMsg, 11001, "warning", true);
                final SipServletResponse resp = request.createResponse(SC_FORBIDDEN, "Inbound USSD session is not Allowed");
                resp.send();
                ec.executePostInboundAction(far, extensions);
                return false;
            }
        } else {
            logger.info("USSD Number registration NOT FOUND");
            request.createResponse(SipServletResponse.SC_NOT_FOUND).send();
        }

        return isFoundHostedApp;
    }

    private void sendNotification(String errMessage, int errCode, String errType, boolean createNotification) {
        NotificationsDao notifications = storage.getNotificationsDao();
        Notification notification;

        if (errType == "warning") {
            logger.warning(errMessage); // send message to console
            if (createNotification) {
                notification = notification(WARNING_NOTIFICATION, errCode, errMessage);
                notifications.addNotification(notification);
            }
        } else if (errType == "error") {
            logger.error(errMessage); // send message to console
            if (createNotification) {
                notification = notification(ERROR_NOTIFICATION, errCode, errMessage);
                notifications.addNotification(notification);
            }
        } else if (errType == "info") {
            if(logger.isInfoEnabled()) {
                logger.info(errMessage); // send message to console
            }
        }
    }

    private Notification notification(final int log, final int error, final String message) {
        String version = configuration.subset("runtime-settings").getString("api-version");
        Sid accountId = new Sid("ACae6e420f425248d6a26948c17a9e2acf");
        //        Sid callSid = new Sid("CA00000000000000000000000000000000");
        final Notification.Builder builder = Notification.builder();
        final Sid sid = Sid.generate(Sid.Type.NOTIFICATION);
        builder.setSid(sid);
        // builder.setAccountSid(accountId);
        builder.setAccountSid(accountId);
        //        builder.setCallSid(callSid);
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
        /**
         * if (response != null) { builder.setRequestUrl(request.getUri()); builder.setRequestMethod(request.getMethod());
         * builder.setRequestVariables(request.getParametersAsString()); }
         **/

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

    private void processRequest(SipServletRequest request) throws IOException {
        final ActorRef ussdInterpreter = (ActorRef) request.getApplicationSession().getAttribute(UssdInterpreter.class.getName());
        if (ussdInterpreter != null) {
            logger.info("Dispatching Request: " + request.getMethod() + " to UssdInterpreter: " + ussdInterpreter);
            ussdInterpreter.tell(request, self());
        } else {
            final SipServletResponse notFound = request.createResponse(SipServletResponse.SC_NOT_FOUND);
            notFound.send();
        }
    }

    private ActorRef outbound(final Object message) throws ServletParseException {
        final CreateCall request = (CreateCall) message;
        final Configuration runtime = configuration.subset("runtime-settings");
        final String uri = ussdGatewayUri;
        final String ussdUsername = (request.username() != null) ? request.username() : ussdGatewayUsername;
        final String ussdPassword = (request.password() != null) ? request.password() : ussdGatewayPassword;

        SipURI from = (SipURI) sipFactory.createSipURI(request.from(), uri);
        SipURI to = (SipURI) sipFactory.createSipURI(request.to(), uri);

        String transport = (to.getTransportParam() != null) ? to.getTransportParam() : "udp";
        //from = outboundInterface(transport);
        SipURI obi = outboundInterface(transport);
        from = (obi == null) ? from : obi;

        final ActorRef ussdCall = ussdCall();
        final ActorRef self = self();
        final InitializeOutbound init = new InitializeOutbound(null, from, to, ussdUsername, ussdPassword, request.timeout(),
                request.isFromApi(), runtime.getString("api-version"), request.accountId(), request.type(), storage, false);
        ussdCall.tell(init, self);
        return ussdCall;
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

    private void execute(final Object message) {
        final ExecuteCallScript request = (ExecuteCallScript) message;
        final ActorRef self = self();
        final UssdInterpreterParams.Builder builder = new UssdInterpreterParams.Builder();
        builder.setConfiguration(configuration);
        builder.setStorage(storage);
        builder.setAccount(request.account());
        builder.setVersion(request.version());
        builder.setUrl(request.url());
        builder.setMethod(request.method());
        builder.setFallbackUrl(request.fallbackUrl());
        builder.setFallbackMethod(request.fallbackMethod());
        final Props props = UssdInterpreter.props(builder.build());
        final ActorRef interpreter = getContext().actorOf(props);
        interpreter.tell(new StartInterpreter(request.call()), self);
    }

    public void response(final Object message) throws IOException {
        final ActorRef self = self();
        final SipServletResponse response = (SipServletResponse) message;
        final SipApplicationSession application = response.getApplicationSession();
        if (application.isValid()) {
            // otherwise the response is coming back to a Voice app hosted by Restcomm
            final ActorRef ussdCall = (ActorRef) application.getAttribute(UssdCall.class.getName());
            ussdCall.tell(response, self);
        } else {
            if(logger.isErrorEnabled()){
                logger.debug("Application invalid "+ message.toString());
            }
        }
    }
}
