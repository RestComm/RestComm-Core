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
import static akka.pattern.Patterns.ask;
import akka.util.Timeout;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.jersey.spi.resource.Singleton;
import com.thoughtworks.xstream.XStream;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.annotation.PostConstruct;
import javax.servlet.ServletContext;
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
import static javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;
import static javax.ws.rs.core.Response.Status.NOT_ACCEPTABLE;
import static javax.ws.rs.core.Response.Status.NOT_FOUND;
import static javax.ws.rs.core.Response.Status.UNAUTHORIZED;
import static javax.ws.rs.core.Response.ok;
import static javax.ws.rs.core.Response.status;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;
import org.apache.commons.configuration.Configuration;
import org.apache.shiro.authz.AuthorizationException;
import org.restcomm.connect.commons.annotations.concurrency.ThreadSafe;
import org.restcomm.connect.commons.configuration.RestcommConfiguration;
import org.restcomm.connect.commons.dao.Sid;
import org.restcomm.connect.dao.AccountsDao;
import org.restcomm.connect.dao.CallDetailRecordsDao;
import org.restcomm.connect.dao.DaoManager;
import org.restcomm.connect.dao.RecordingsDao;
import org.restcomm.connect.dao.entities.Account;
import org.restcomm.connect.dao.entities.CallDetailRecord;
import org.restcomm.connect.dao.entities.CallDetailRecordList;
import org.restcomm.connect.dao.entities.Recording;
import org.restcomm.connect.dao.entities.RestCommResponse;
import org.restcomm.connect.http.converter.CallDetailRecordListConverter;
import org.restcomm.connect.http.converter.ConferenceParticipantConverter;
import org.restcomm.connect.http.converter.RecordingConverter;
import org.restcomm.connect.http.converter.RecordingListConverter;
import org.restcomm.connect.http.converter.RestCommResponseConverter;
import org.restcomm.connect.http.security.ContextUtil;
import org.restcomm.connect.http.security.PermissionEvaluator.SecuredType;
import org.restcomm.connect.identity.UserIdentityContext;
import org.restcomm.connect.telephony.api.CallInfo;
import org.restcomm.connect.telephony.api.CallResponse;
import org.restcomm.connect.telephony.api.GetCall;
import org.restcomm.connect.telephony.api.GetCallInfo;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;

/**
 * @author maria-farooq@live.com (Maria Farooq)
 */
@Path("/Accounts/{accountSid}/Conferences/{conferenceSid}/Participants")
@ThreadSafe
@Singleton
public class ParticipantsEndpoint extends AbstractEndpoint {
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




    public ParticipantsEndpoint() {
        super();
    }

    @PostConstruct
    public void init() {
        configuration = (Configuration) context.getAttribute(Configuration.class.getName());
        configuration = configuration.subset("runtime-settings");
        callManager = (ActorRef) context.getAttribute("org.restcomm.connect.telephony.CallManager");
        daos = (DaoManager) context.getAttribute(DaoManager.class.getName());
        accountsDao = daos.getAccountsDao();
        recordingsDao = daos.getRecordingsDao();
        super.init(configuration);
        ConferenceParticipantConverter converter = new ConferenceParticipantConverter(configuration);
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
    }

    protected Response getCall(final String accountSid,
            final String sid,
            final MediaType responseType,
            UserIdentityContext userIdentityContext) {
        Account account = daos.getAccountsDao().getAccount(accountSid);
        try {
            permissionEvaluator.secure(account,
                    "RestComm:Read:Calls",
                    userIdentityContext);
        } catch (final AuthorizationException exception) {
            return status(UNAUTHORIZED).build();
        }
        final CallDetailRecordsDao dao = daos.getCallDetailRecordsDao();
        final CallDetailRecord cdr = dao.getCallDetailRecord(new Sid(sid));
        if (cdr == null) {
            return status(NOT_FOUND).build();
        } else {
            try {
                permissionEvaluator.secure(account,
                        cdr.getAccountSid(),
                        SecuredType.SECURED_STANDARD,
                        userIdentityContext);
            } catch (final AuthorizationException exception) {
                return status(UNAUTHORIZED).build();
            }
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

    protected Response getCalls(final String accountSid,
            final String conferenceSid,
            UriInfo info,
            MediaType responseType,
            UserIdentityContext userIdentityContext) {
        Account account = daos.getAccountsDao().getAccount(accountSid);
        try {
            permissionEvaluator.secure(account,
                    "RestComm:Read:Calls",
                    userIdentityContext);
        } catch (final AuthorizationException exception) {
            return status(UNAUTHORIZED).build();
        } catch (Exception e) {
        }

        String pageSize = info.getQueryParameters().getFirst("PageSize");
        String page = info.getQueryParameters().getFirst("Page");

        if (pageSize == null) {
            pageSize = "50";
        }

        if (page == null) {
            page = "0";
        }

        int limit = Integer.parseInt(pageSize);
        int offset = (page == "0") ? 0 : (((Integer.parseInt(page) - 1) * Integer.parseInt(pageSize)) + Integer
                .parseInt(pageSize));

        CallDetailRecordsDao dao = daos.getCallDetailRecordsDao();

        final int total = dao.getTotalRunningCallDetailRecordsByConferenceSid(new Sid(conferenceSid));

        if (Integer.parseInt(page) > (total / limit)) {
            return status(javax.ws.rs.core.Response.Status.BAD_REQUEST).build();
        }

        final List<CallDetailRecord> cdrs = dao.getRunningCallDetailRecordsByConferenceSid(new Sid(conferenceSid));
        if (logger.isDebugEnabled()) {
            final List<CallDetailRecord> allCdrs = dao.getCallDetailRecordsByAccountSid(new Sid(accountSid));
            logger.debug("CDR with filter size: "+ cdrs.size()+", all CDR with no filter size: "+allCdrs.size());
            logger.debug("CDRs for ConferenceSid: "+conferenceSid);
            for (CallDetailRecord cdr: allCdrs) {
                logger.debug("CDR sid: "+cdr.getSid()+", status: "+cdr.getStatus()+", conferenceSid: "+cdr.getConferenceSid());
            }
        }

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

    @SuppressWarnings("unchecked")
    protected Response updateCall(final String sid,
            final String callSid,
            final MultivaluedMap<String, String> data,
            final MediaType responseType,
            UserIdentityContext userIdentityContext) {
        final Sid accountSid = new Sid(sid);
        Account account = daos.getAccountsDao().getAccount(accountSid);
        try {
            permissionEvaluator.secure(account,
                    "RestComm:Modify:Calls",
                    userIdentityContext);
        } catch (final AuthorizationException exception) {
            return status(UNAUTHORIZED).build();
        }

        final Timeout expires = new Timeout(Duration.create(60, TimeUnit.SECONDS));
        final CallDetailRecordsDao dao = daos.getCallDetailRecordsDao();
        CallDetailRecord cdr = null;
        try {
            cdr = dao.getCallDetailRecord(new Sid(callSid));

            if (cdr != null) {
                try {
                    permissionEvaluator.secure(account,
                            cdr.getAccountSid(),
                            SecuredType.SECURED_STANDARD,
                            userIdentityContext);
                } catch (final AuthorizationException exception) {
                    return status(UNAUTHORIZED).build();
                }
            } else {
                return Response.status(NOT_ACCEPTABLE).build();
            }
        } catch (Exception e) {
            return status(BAD_REQUEST).build();
        }

        Boolean mute = Boolean.valueOf(data.getFirst("Mute"));
        // Mute/UnMute call
        if (mute != null) {
            String callPath = null;
            final ActorRef call;
            final CallInfo callInfo;
            try {
                callPath = cdr.getCallPath();
                Future<Object> future = (Future<Object>) ask(callManager, new GetCall(callPath), expires);
                call = (ActorRef) Await.result(future, Duration.create(100000, TimeUnit.SECONDS));

                future = (Future<Object>) ask(call, new GetCallInfo(), expires);
                CallResponse<CallInfo> response = (CallResponse<CallInfo>) Await.result(future,
                        Duration.create(100000, TimeUnit.SECONDS));
                callInfo = response.get();
            } catch (Exception exception) {
                return status(INTERNAL_SERVER_ERROR).entity(exception.getMessage()).build();
            }

            if(call != null){
                try{
                    CallsUtil.muteUnmuteCall(mute, callInfo, call, cdr, dao);
                } catch (Exception exception) {
                    return status(INTERNAL_SERVER_ERROR).entity(exception.getMessage()).build();
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

    @Path("/{callSid}")
    @GET
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public Response getParticipantAsXml(@PathParam("accountSid") final String accountSid,
            @PathParam("conferenceSid") final String conferenceSid,
            @PathParam("callSid") final String callSid,
            @HeaderParam("Accept") String accept,
            @Context SecurityContext sec) {
        return getCall(accountSid,
                callSid,
                retrieveMediaType(accept),
                ContextUtil.convert(sec));
    }

    @GET
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public Response getParticipants(@PathParam("accountSid") final String accountSid,
            @PathParam("conferenceSid") final String conferenceSid,
            @Context UriInfo info,
            @HeaderParam("Accept") String accept,
            @Context SecurityContext sec) {
        return getCalls(accountSid,
                conferenceSid,
                info,
                retrieveMediaType(accept),
                ContextUtil.convert(sec));
    }

    @Path("/{callSid}")
    @POST
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public Response modifyCall(@PathParam("accountSid") final String accountSid,
            @PathParam("conferenceSid") final String conferenceSid,
            @PathParam("callSid") final String callSid,
            final MultivaluedMap<String, String> data,
            @HeaderParam("Accept") String accept,
            @Context SecurityContext sec) {
        return updateCall(accountSid,
                callSid,
                data,
                retrieveMediaType(accept),
                ContextUtil.convert(sec));
    }
}