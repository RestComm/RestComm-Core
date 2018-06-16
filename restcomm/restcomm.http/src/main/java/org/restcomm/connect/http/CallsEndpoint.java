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
package org.restcomm.connect.http;

import akka.actor.ActorRef;
import akka.pattern.AskTimeoutException;
import static akka.pattern.Patterns.ask;
import akka.util.Timeout;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat;
import com.sun.jersey.spi.resource.Singleton;
import com.thoughtworks.xstream.XStream;
import java.net.URI;
import java.net.URL;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import javax.annotation.PostConstruct;
import javax.servlet.ServletContext;
import javax.servlet.sip.SipServletResponse;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON_TYPE;
import static javax.ws.rs.core.MediaType.APPLICATION_XML;
import static javax.ws.rs.core.MediaType.APPLICATION_XML_TYPE;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static javax.ws.rs.core.Response.Status.GONE;
import static javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;
import static javax.ws.rs.core.Response.Status.NOT_ACCEPTABLE;
import static javax.ws.rs.core.Response.Status.NOT_FOUND;
import static javax.ws.rs.core.Response.ok;
import static javax.ws.rs.core.Response.status;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;
import org.apache.commons.configuration.Configuration;
import org.restcomm.connect.commons.amazonS3.RecordingSecurityLevel;
import org.restcomm.connect.commons.annotations.concurrency.ThreadSafe;
import org.restcomm.connect.commons.configuration.RestcommConfiguration;
import org.restcomm.connect.commons.dao.Sid;
import org.restcomm.connect.commons.telephony.CreateCallType;
import org.restcomm.connect.dao.AccountsDao;
import org.restcomm.connect.dao.CallDetailRecordsDao;
import org.restcomm.connect.dao.DaoManager;
import org.restcomm.connect.dao.RecordingsDao;
import org.restcomm.connect.dao.entities.Account;
import org.restcomm.connect.dao.entities.CallDetailRecord;
import org.restcomm.connect.dao.entities.CallDetailRecordFilter;
import org.restcomm.connect.dao.entities.CallDetailRecordList;
import org.restcomm.connect.dao.entities.Recording;
import org.restcomm.connect.dao.entities.RecordingList;
import org.restcomm.connect.dao.entities.RestCommResponse;
import org.restcomm.connect.http.converter.CallDetailRecordConverter;
import org.restcomm.connect.http.converter.CallDetailRecordListConverter;
import org.restcomm.connect.http.converter.RecordingConverter;
import org.restcomm.connect.http.converter.RecordingListConverter;
import org.restcomm.connect.http.converter.RestCommResponseConverter;
import org.restcomm.connect.http.security.ContextUtil;
import org.restcomm.connect.http.security.PermissionEvaluator.SecuredType;
import org.restcomm.connect.identity.UserIdentityContext;
import org.restcomm.connect.telephony.api.CallInfo;
import org.restcomm.connect.telephony.api.CallManagerResponse;
import org.restcomm.connect.telephony.api.CallResponse;
import org.restcomm.connect.telephony.api.CreateCall;
import org.restcomm.connect.telephony.api.ExecuteCallScript;
import org.restcomm.connect.telephony.api.GetCall;
import org.restcomm.connect.telephony.api.GetCallInfo;
import org.restcomm.connect.telephony.api.Hangup;
import org.restcomm.connect.telephony.api.UpdateCallScript;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;

//import org.joda.time.DateTime;

/**
 * @author quintana.thomas@gmail.com (Thomas Quintana)
 * @author gvagenas@gmail.com (George Vagenas)
 */
@Path("/Accounts/{accountSid}/Calls")
@ThreadSafe
@Singleton
public class CallsEndpoint extends AbstractEndpoint {
    @Context
    private ServletContext context;
    private Configuration configuration;
    private ActorRef callManager;
    private DaoManager daos;
    private Gson gson;
    private GsonBuilder builder;
    private XStream xstream;
    private CallDetailRecordListConverter listConverter;
    private AccountsDao accountsDao;
    private RecordingsDao recordingsDao;
    private String instanceId;
    private RecordingSecurityLevel securityLevel = RecordingSecurityLevel.SECURE;
    private boolean normalizePhoneNumbers;




    public CallsEndpoint() {
        super();
    }

    @PostConstruct
    public void init() {
        configuration = (Configuration) context.getAttribute(Configuration.class.getName());
        Configuration amazonS3Configuration = configuration.subset("amazon-s3");
        configuration = configuration.subset("runtime-settings");
        callManager = (ActorRef) context.getAttribute("org.restcomm.connect.telephony.CallManager");
        daos = (DaoManager) context.getAttribute(DaoManager.class.getName());
        accountsDao = daos.getAccountsDao();
        recordingsDao = daos.getRecordingsDao();
        super.init(configuration);
        CallDetailRecordConverter converter = new CallDetailRecordConverter(configuration);
        listConverter = new CallDetailRecordListConverter(configuration);
        final RecordingConverter recordingConverter = new RecordingConverter(configuration);
        builder = new GsonBuilder();
        builder.registerTypeAdapter(CallDetailRecord.class, converter);
        builder.registerTypeAdapter(CallDetailRecordList.class, listConverter);
        builder.registerTypeAdapter(Recording.class, recordingConverter);
        builder.setPrettyPrinting();
        gson = builder.create();
        xstream = new XStream();
        xstream.alias("RestcommResponse", RestCommResponse.class);
        xstream.registerConverter(converter);
        xstream.registerConverter(recordingConverter);
        xstream.registerConverter(new RecordingListConverter(configuration));
        xstream.registerConverter(new RestCommResponseConverter(configuration));
        xstream.registerConverter(listConverter);

        instanceId = RestcommConfiguration.getInstance().getMain().getInstanceId();

        normalizePhoneNumbers = configuration.getBoolean("normalize-numbers-for-outbound-calls");
        if(!amazonS3Configuration.isEmpty()) { // Do not fail with NPE is amazonS3Configuration is not present for older install
            boolean amazonS3Enabled = amazonS3Configuration.getBoolean("enabled");
            if (amazonS3Enabled) {
                securityLevel = RecordingSecurityLevel.valueOf(amazonS3Configuration.getString("security-level", "secure").toUpperCase());
                recordingConverter.setSecurityLevel(securityLevel);
            }
        }
    }

    protected Response getCall(final String accountSid, final String sid,
            final MediaType responseType,
            UserIdentityContext userIdentityContext) {
        Account account = daos.getAccountsDao().getAccount(accountSid);
        permissionEvaluator.secure(account, "RestComm:Read:Calls",userIdentityContext);
        final CallDetailRecordsDao dao = daos.getCallDetailRecordsDao();
        final CallDetailRecord cdr = dao.getCallDetailRecord(new Sid(sid));
        if (cdr == null) {
            return status(NOT_FOUND).build();
        } else {
            permissionEvaluator.secure(account, cdr.getAccountSid(),
                    SecuredType.SECURED_STANDARD, userIdentityContext);
            if (APPLICATION_XML_TYPE.equals(responseType)) {
                final RestCommResponse response = new RestCommResponse(cdr);
                return ok(xstream.toXML(response), APPLICATION_XML).build();
            } else if (APPLICATION_JSON_TYPE.equals(responseType)) {
                return ok(gson.toJson(cdr), APPLICATION_JSON).build();
            } else {
                return null;
            }
        }
    }

    // Issue 153: https://bitbucket.org/telestax/telscale-restcomm/issue/153
    // Issue 110: https://bitbucket.org/telestax/telscale-restcomm/issue/110
    protected Response getCalls(final String accountSid, UriInfo info,
            MediaType responseType,
            UserIdentityContext userIdentityContex) {
        Account account = daos.getAccountsDao().getAccount(accountSid);
        permissionEvaluator.secure(account, "RestComm:Read:Calls",userIdentityContex);

        boolean localInstanceOnly = true;
        try {
            String localOnly = info.getQueryParameters().getFirst("localOnly");
            if (localOnly != null && localOnly.equalsIgnoreCase("false"))
                localInstanceOnly = false;
        } catch (Exception e) {
        }
        // shall we include sub-accounts cdrs in our query ?
        boolean querySubAccounts = false; // be default we don't
        String querySubAccountsParam = info.getQueryParameters().getFirst("SubAccounts");
        if (querySubAccountsParam != null && querySubAccountsParam.equalsIgnoreCase("true"))
            querySubAccounts = true;

        String pageSize = info.getQueryParameters().getFirst("PageSize");
        String page = info.getQueryParameters().getFirst("Page");
        // String afterSid = info.getQueryParameters().getFirst("AfterSid");
        String recipient = info.getQueryParameters().getFirst("To");
        String sender = info.getQueryParameters().getFirst("From");
        String status = info.getQueryParameters().getFirst("Status");
        String startTime = info.getQueryParameters().getFirst("StartTime");
        String endTime = info.getQueryParameters().getFirst("EndTime");
        String parentCallSid = info.getQueryParameters().getFirst("ParentCallSid");
        String conferenceSid = info.getQueryParameters().getFirst("ConferenceSid");
        String reverse = info.getQueryParameters().getFirst("Reverse");

        if (pageSize == null) {
            pageSize = "50";
        }

        if (page == null) {
            page = "0";
        }

        int limit = Integer.parseInt(pageSize);
        int offset = (page == "0") ? 0 : (((Integer.parseInt(page) - 1) * Integer.parseInt(pageSize)) + Integer
                .parseInt(pageSize));

        // Shall we query cdrs of sub-accounts too ?
        // if we do, we need to find the sub-accounts involved first
        List<String> ownerAccounts = null;
        if (querySubAccounts) {
            ownerAccounts = new ArrayList<String>();
            ownerAccounts.add(accountSid); // we will also return parent account cdrs
            ownerAccounts.addAll(accountsDao.getSubAccountSidsRecursive(new Sid(accountSid)));
        }

        CallDetailRecordsDao dao = daos.getCallDetailRecordsDao();

        CallDetailRecordFilter filterForTotal;
        try {

            if (localInstanceOnly) {
                filterForTotal = new CallDetailRecordFilter(accountSid, ownerAccounts, recipient, sender, status, startTime, endTime,
                        parentCallSid, conferenceSid, null, null);
            } else {
                filterForTotal = new CallDetailRecordFilter(accountSid, ownerAccounts, recipient, sender, status, startTime, endTime,
                        parentCallSid, conferenceSid, null, null, instanceId);
            }
        } catch (ParseException e) {
            return status(BAD_REQUEST).build();
        }

        final int total = dao.getTotalCallDetailRecords(filterForTotal);

        if (reverse != null) {
            if (reverse.equalsIgnoreCase("true")){
                if (total > Integer.parseInt(pageSize)){
                    if (total > Integer.parseInt(pageSize)*(Integer.parseInt(page) + 1)){
                        offset = total - Integer.parseInt(pageSize)*(Integer.parseInt(page) + 1);
                        limit = Integer.parseInt(pageSize);
                    }
                    else{
                        offset = 0;
                        limit = total - Integer.parseInt(pageSize)*Integer.parseInt(page);
                    }
                }
            }
        }

        if (Integer.parseInt(page) > (total / limit)) {
            return status(javax.ws.rs.core.Response.Status.BAD_REQUEST).build();
        }

        CallDetailRecordFilter filter;
        try {
            if (localInstanceOnly) {
                filter = new CallDetailRecordFilter(accountSid, ownerAccounts, recipient, sender, status, startTime, endTime,
                        parentCallSid, conferenceSid, limit, offset);
            } else {
                filter = new CallDetailRecordFilter(accountSid, ownerAccounts, recipient, sender, status, startTime, endTime,
                        parentCallSid, conferenceSid, limit, offset, instanceId);
            }
        } catch (ParseException e) {
            return status(BAD_REQUEST).build();
        }

        final List<CallDetailRecord> cdrs = dao.getCallDetailRecords(filter);

        listConverter.setCount(total);
        listConverter.setPage(Integer.parseInt(page));
        listConverter.setPageSize(Integer.parseInt(pageSize));
        listConverter.setPathUri("/"+getApiVersion(null)+"/"+info.getPath());

        if (APPLICATION_XML_TYPE.equals(responseType)) {
            final RestCommResponse response = new RestCommResponse(new CallDetailRecordList(cdrs));
            return ok(xstream.toXML(response), APPLICATION_XML).build();
        } else if (APPLICATION_JSON_TYPE.equals(responseType)) {
            return ok(gson.toJson(new CallDetailRecordList(cdrs)), APPLICATION_JSON).build();
        } else {
            return null;
        }
    }

    private void normalize(final MultivaluedMap<String, String> data) throws IllegalArgumentException {
        final PhoneNumberUtil phoneNumberUtil = PhoneNumberUtil.getInstance();
        final String from = data.getFirst("From");
        if (!from.contains("@")) {
            // https://github.com/Mobicents/RestComm/issues/150 Don't complain in case of URIs in the From header
            data.remove("From");
            try {
                data.putSingle("From", phoneNumberUtil.format(phoneNumberUtil.parse(from, "US"), PhoneNumberFormat.E164));
            } catch (final NumberParseException exception) {
                throw new IllegalArgumentException(exception);
            }
        }
        String to = data.getFirst("To");
        if (to.contains("?")) {
            to = to.substring(0, to.indexOf("?"));
        }
        // Only try to normalize phone numbers.
        if (to.startsWith("client")) {
            if (to.split(":").length != 2) {
                throw new IllegalArgumentException(to + " is an invalid client identifier.");
            }
        } else if (!to.contains("@")) {
            data.remove("To");
            try {
                data.putSingle("To", phoneNumberUtil.format(phoneNumberUtil.parse(to, "US"), PhoneNumberFormat.E164));
            } catch (final NumberParseException exception) {
                throw new IllegalArgumentException(exception);
            }
        }
        URI.create(data.getFirst("Url"));
    }

    @SuppressWarnings("unchecked")
    protected Response putCall(final String accountSid,
            final MultivaluedMap<String,
                    String> data,
            final MediaType responseType,
            UserIdentityContext userIdentityContex) {
        final Sid accountId;
        try {
            accountId = new Sid(accountSid);
        } catch (final IllegalArgumentException exception){
            return status(INTERNAL_SERVER_ERROR).entity(buildErrorResponseBody(exception.getMessage(),responseType)).build();
        }

        permissionEvaluator.secure(daos.getAccountsDao().getAccount(accountSid),
                "RestComm:Create:Calls", userIdentityContex);

        try {
            validate(data);
        } catch (final RuntimeException exception) {
            return status(BAD_REQUEST).entity(exception.getMessage()).build();
        }

        URI statusCallback = null;
        String statusCallbackMethod = "POST";
        List<String> statusCallbackEvent = new LinkedList<String>();
        statusCallbackEvent.add("initiated");
        statusCallbackEvent.add("ringing");
        statusCallbackEvent.add("answered");
        statusCallbackEvent.add("completed");

        final String from = data.getFirst("From").trim();
        String to = data.getFirst("To").trim();
        final String username = data.getFirst("Username");
        final String password = data.getFirst("Password");
        Integer timeout = getTimeout(data);
        timeout = timeout != null ? timeout : 30;
        final Timeout expires = new Timeout(Duration.create(60, TimeUnit.SECONDS));
        final URI rcmlUrl = getUrl("Url", data);

        String customHeaders = getCustomHeaders(to);

        if (customHeaders != null) {
            to = (customHeaders != null) ? to.substring(0, to.indexOf("?")) : to;
            data.remove(to);
            data.putSingle("To", to);
        }

        try {
            if (normalizePhoneNumbers)
                normalize(data);
        } catch (RuntimeException exception) {
            return status(BAD_REQUEST).entity(exception.getMessage()).build();
        }


        try {
            if (data.containsKey("StatusCallback")) {
                statusCallback = new URI(data.getFirst("StatusCallback").trim());
            }
        } catch (Exception e) {
            //Handle Exception
        }

        if (statusCallback != null) {
            if (data.containsKey("StatusCallbackMethod")) {
                statusCallbackMethod = data.getFirst("StatusCallbackMethod").trim();
            }
            if (data.containsKey("StatusCallbackEvent")) {
                statusCallbackEvent.addAll(Arrays.asList(data.getFirst("StatusCallbackEvent").trim().split(",")));
            }
        }

        CreateCall create = null;

        try {
            if (to.contains("@")) {
                create = new CreateCall(from, to, username, password, true, timeout, CreateCallType.SIP,
                        accountId, null, statusCallback, statusCallbackMethod, statusCallbackEvent, customHeaders);
            } else if (to.startsWith("client")) {
                create = new CreateCall(from, to, username, password, true, timeout, CreateCallType.CLIENT,
                        accountId, null, statusCallback, statusCallbackMethod, statusCallbackEvent, customHeaders);
            } else {
                create = new CreateCall(from, to, username, password, true, timeout, CreateCallType.PSTN,
                        accountId, null, statusCallback, statusCallbackMethod, statusCallbackEvent, customHeaders);
            }
            create.setCreateCDR(false);
            if (callManager == null)
                callManager = (ActorRef) context.getAttribute("org.restcomm.connect.telephony.CallManager");
            Future<Object> future = (Future<Object>) ask(callManager, create, expires);
            Object object = Await.result(future, Duration.create(10, TimeUnit.SECONDS));
            Class<?> klass = object.getClass();
            if (CallManagerResponse.class.equals(klass)) {
                final CallManagerResponse<ActorRef> managerResponse = (CallManagerResponse<ActorRef>) object;
                if (managerResponse.succeeded()) {
                    List<ActorRef> dialBranches;
                    if (managerResponse.get() instanceof List) {
                        dialBranches = (List<ActorRef>) managerResponse.get();
                    } else {
                        dialBranches = new CopyOnWriteArrayList<ActorRef>();
                        dialBranches.add(managerResponse.get());
                    }
                    List<CallDetailRecord> cdrs = new CopyOnWriteArrayList<CallDetailRecord>();
                    for (ActorRef call : dialBranches) {
                        future = (Future<Object>) ask(call, new GetCallInfo(), expires);
                        object = Await.result(future, Duration.create(10, TimeUnit.SECONDS));
                        klass = object.getClass();
                        if (CallResponse.class.equals(klass)) {
                            final CallResponse<CallInfo> callResponse = (CallResponse<CallInfo>) object;
                            if (callResponse.succeeded()) {
                                final CallInfo callInfo = callResponse.get();
                                // Execute the call script.
                                final String version = getApiVersion(data);
                                final URI url = rcmlUrl;
                                final String method = getMethod("Method", data);
                                final URI fallbackUrl = getUrl("FallbackUrl", data);
                                final String fallbackMethod = getMethod("FallbackMethod", data);
                                final ExecuteCallScript execute = new ExecuteCallScript(call, accountId, version, url, method,
                                        fallbackUrl, fallbackMethod, timeout);
                                callManager.tell(execute, null);
                                cdrs.add(daos.getCallDetailRecordsDao().getCallDetailRecord(callInfo.sid()));
                            }
                        }
                    }
                    if (APPLICATION_XML_TYPE.equals(responseType)) {
                        if (cdrs.size()==1) {
                            return ok(xstream.toXML(cdrs.get(0)), APPLICATION_XML).build();
                        } else {
                            final RestCommResponse response = new RestCommResponse(new CallDetailRecordList(cdrs));
                            return ok(xstream.toXML(response), APPLICATION_XML).build();
                        }
                    } else if (APPLICATION_JSON_TYPE.equals(responseType)) {
                        if (cdrs.size()==1) {
                            return ok(gson.toJson(cdrs.get(0)), APPLICATION_JSON).build();
                        } else {
                            return ok(gson.toJson(cdrs), APPLICATION_JSON).build();
                        }
                    } else {
                        return null;
                    }
//                    if (APPLICATION_JSON_TYPE.equals(responseType)) {
//                        return ok(gson.toJson(cdrs), APPLICATION_JSON).build();
//                    } else if (APPLICATION_XML_TYPE.equals(responseType)) {
//                        return ok(xstream.toXML(new RestCommResponse(cdrs)), APPLICATION_XML).build();
//                    } else {
//                        return null;
//                    }
                } else {
                    return status(INTERNAL_SERVER_ERROR).entity(managerResponse.cause().getMessage()).build();
                }
            }
            return status(INTERNAL_SERVER_ERROR).build();
        } catch (final Exception exception) {
            return status(INTERNAL_SERVER_ERROR).entity(exception.getMessage()).build();
        }
    }

    private String getCustomHeaders(final String to) {
        String customHeaders = null;

        if (to.contains("?")) {
            customHeaders = to.substring(to.indexOf("?")+1, to.length());
        }

        return customHeaders;
    }

    // Issue 139: https://bitbucket.org/telestax/telscale-restcomm/issue/139
    @SuppressWarnings("unchecked")
    protected Response updateCall(final String sid,
            final String callSid,
            final MultivaluedMap<String, String> data,
            final MediaType responseType,
            UserIdentityContext userIdentityContex) {
        final Sid accountSid = new Sid(sid);
        Account account = daos.getAccountsDao().getAccount(accountSid);
        permissionEvaluator.secure(account, "RestComm:Modify:Calls", userIdentityContex);

        final Timeout expires = new Timeout(Duration.create(60, TimeUnit.SECONDS));

        final CallDetailRecordsDao dao = daos.getCallDetailRecordsDao();
        CallDetailRecord cdr = null;
        try {
            cdr = dao.getCallDetailRecord(new Sid(callSid));

            if (cdr != null) {
                //allow super admin to perform LCM on any call - https://telestax.atlassian.net/browse/RESTCOMM-1171
                if(!permissionEvaluator.isSuperAdmin(userIdentityContex)){
                    permissionEvaluator.secure(account, cdr.getAccountSid(),
                            SecuredType.SECURED_STANDARD,
                            userIdentityContex);
                }
            } else {
                return Response.status(NOT_ACCEPTABLE).build();
            }
        } catch (Exception e) {
            return status(BAD_REQUEST).build();
        }

        final String url = data.getFirst("Url");
        String method = data.getFirst("Method");
        final String status = data.getFirst("Status");
        final String fallBackUrl = data.getFirst("FallbackUrl");
        String fallBackMethod = data.getFirst("FallbackMethod");
        final String statusCallBack = data.getFirst("StatusCallback");
        String statusCallbackMethod = data.getFirst("StatusCallbackMethod");
        //Restcomm-  Move connected call leg (if exists) to the new URL
        Boolean moveConnectedCallLeg = Boolean.valueOf(data.getFirst("MoveConnectedCallLeg"));
        Boolean mute = Boolean.valueOf(data.getFirst("Mute"));

        String callPath = null;
        final ActorRef call;
        final CallInfo callInfo;

        try {
            callPath = cdr.getCallPath();
            Future<Object> future = (Future<Object>) ask(callManager, new GetCall(callPath), expires);
            call = (ActorRef) Await.result(future, Duration.create(10, TimeUnit.SECONDS));

            future = (Future<Object>) ask(call, new GetCallInfo(), expires);
            CallResponse<CallInfo> response = (CallResponse<CallInfo>) Await.result(future,
                    Duration.create(10, TimeUnit.SECONDS));
            callInfo = response.get();
        } catch (AskTimeoutException ate) {
            final String msg ="Call is already completed.";
            if(logger.isDebugEnabled())
                logger.debug("Modify Call LCM for call sid:"+callSid+ " AskTimeout while getting call: "+ callPath +" restcomm will send "+GONE +" with msg: "+msg);
            return status(GONE).entity(msg).build();
        } catch (Exception exception) {
            logger.error("Exception while trying to update call callPath: "+callPath+" callSid: "+callSid, exception);
            return status(INTERNAL_SERVER_ERROR).entity(exception.getMessage()).build();
        }

        if (method == null)
            method = "POST";

        if (url == null && status == null && mute == null ) {
            // Throw exception. We can either redirect a running call using Url or change the state of a Call with Status
            final String errorMessage = "You can either redirect a running call using \"Url\" or change the state of a Call with \"Status\" or mute a call using \"Mute=true\"";
            return status(javax.ws.rs.core.Response.Status.CONFLICT).entity(errorMessage).build();
        }

        // Modify state of a call
        if (status != null) {
            if (status.equalsIgnoreCase("canceled")) {
                if (callInfo.state().name().equalsIgnoreCase("queued") || callInfo.state().name().equalsIgnoreCase("ringing")) {
                    if (call != null) {
                        call.tell(new Hangup(), null);
                    }
                } else if (callInfo.state().name().equalsIgnoreCase("wait_for_answer")){
                    // We can cancel Wait For Answer calls
                    if (call != null) {
                        call.tell(new Hangup(SipServletResponse.SC_REQUEST_TERMINATED), null);
                    }
                }
                else{
                    // Do Nothing. We can only cancel Queued or Ringing calls
                }
            }

            if (status.equalsIgnoreCase("completed")) {
                // Specifying "completed" will attempt to hang up a call even if it's already in progress.
                if (call != null) {
                    call.tell(new Hangup(SipServletResponse.SC_REQUEST_TERMINATED), null);
                }
            }
        }

        if(mute != null && call != null){
            try{
                CallsUtil.muteUnmuteCall(mute, callInfo, call, cdr, dao);
            } catch (Exception exception) {
                return status(INTERNAL_SERVER_ERROR).entity(exception.getMessage()).build();
            }
        }

        if (url != null && call != null) {
            try {
                final String version = getApiVersion(data);
                final URI uri = (new URL(url)).toURI();

                URI fallbackUri = (fallBackUrl != null) ? (new URL(fallBackUrl)).toURI() : null;
                fallBackMethod = (fallBackMethod == null) ? "POST" : fallBackMethod;
                URI callbackUri = (statusCallBack != null) ? (new URL(statusCallBack)).toURI() : null;
                statusCallbackMethod = (statusCallbackMethod == null) ? "POST" : statusCallbackMethod;

                final UpdateCallScript update = new UpdateCallScript(call, accountSid, version, uri, method, fallbackUri,
                        fallBackMethod, callbackUri, statusCallbackMethod, moveConnectedCallLeg);
                callManager.tell(update, null);
            } catch (Exception exception) {
                return status(INTERNAL_SERVER_ERROR).entity(exception.getMessage()).build();
            }
        } else {
            if (logger.isInfoEnabled()) {
                if (url == null) {
                    logger.info("Problem during Call Update, Url is null. Make sure you provide Url parameter");
                }
                if (call == null) {
                    logger.info("Problem during Call update, Call is null. Make sure you provide the proper Call SID");
                }
            }
        }

        if (APPLICATION_JSON_TYPE.equals(responseType)) {
            return ok(gson.toJson(cdr), APPLICATION_JSON).build();
        } else if (APPLICATION_XML_TYPE.equals(responseType)) {
            return ok(xstream.toXML(new RestCommResponse(cdr)), APPLICATION_XML).build();
        } else {
            return null;
        }
    }

    private Integer getTimeout(final MultivaluedMap<String, String> data) {
        Integer result = 60;
        if (data.containsKey("Timeout")) {
            result = Integer.parseInt(data.getFirst("Timeout"));
        }
        return result;
    }

    private void validate(final MultivaluedMap<String, String> data) throws NullPointerException {
        if (!data.containsKey("From")) {
            throw new NullPointerException("From can not be null.");
        } else if (!data.containsKey("To")) {
            throw new NullPointerException("To can not be null.");
        } else if (!data.containsKey("Url")) {
            throw new NullPointerException("Url can not be null.");
        }
    }

    protected Response getRecordingsByCall(final String accountSid,
            final String callSid,
            final MediaType responseType,
            UserIdentityContext userIdentityContext) {
        permissionEvaluator.secure(accountsDao.getAccount(accountSid),
                "RestComm:Read:Recordings", userIdentityContext);

        final List<Recording> recordings = recordingsDao.getRecordingsByCall(new Sid(callSid));
        if (APPLICATION_JSON_TYPE.equals(responseType)) {
            return ok(gson.toJson(recordings), APPLICATION_JSON).build();
        } else if (APPLICATION_XML_TYPE.equals(responseType)) {
            final RestCommResponse response = new RestCommResponse(new RecordingList(recordings));
            return ok(xstream.toXML(response), APPLICATION_XML).build();
        } else {
            return null;
        }

    }

    @Path("/{sid}")
    @GET
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public Response getCallAsXml(@PathParam("accountSid") final String accountSid,
            @PathParam("sid") final String sid,
            @HeaderParam("Accept") String accept,
            @Context SecurityContext sec) {
        return getCall(accountSid, sid, retrieveMediaType(accept),ContextUtil.convert(sec));
    }

    // Issue 153: https://bitbucket.org/telestax/telscale-restcomm/issue/153
    // Issue 110: https://bitbucket.org/telestax/telscale-restcomm/issue/110
    @GET
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public Response getCalls(@PathParam("accountSid") final String accountSid,
            @Context UriInfo info,
            @HeaderParam("Accept") String accept,
            @Context SecurityContext sec) {
        return getCalls(accountSid, info, retrieveMediaType(accept),ContextUtil.convert(sec));
    }

    @POST
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public Response putCall(@PathParam("accountSid") final String accountSid,
            final MultivaluedMap<String, String> data,
            @HeaderParam("Accept") String accept,
            @Context SecurityContext sec) {
        return putCall(accountSid, data, retrieveMediaType(accept),ContextUtil.convert(sec));
    }

    // Issue 139: https://bitbucket.org/telestax/telscale-restcomm/issue/139
    @Path("/{sid}")
    @POST
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public Response modifyCall(@PathParam("accountSid") final String accountSid,
            @PathParam("sid") final String sid,
            final MultivaluedMap<String, String> data,
            @HeaderParam("Accept") String accept,
            @Context SecurityContext sec) {
        return updateCall(accountSid, sid, data, retrieveMediaType(accept),
                ContextUtil.convert(sec));
    }

    @GET
    @Path("/{callSid}/Recordings")
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public Response getRecordingsByCallXml(@PathParam("accountSid") String accountSid,
            @PathParam("callSid") String callSid,
            @HeaderParam("Accept") String accept,
            @Context SecurityContext sec) {
        return getRecordingsByCall(accountSid, callSid, retrieveMediaType(accept),
                ContextUtil.convert(sec));
    }
}
