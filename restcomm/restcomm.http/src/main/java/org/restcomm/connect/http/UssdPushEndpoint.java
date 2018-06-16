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
import java.net.URI;
import java.util.concurrent.TimeUnit;
import javax.annotation.PostConstruct;
import javax.servlet.ServletContext;
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
import static javax.ws.rs.core.Response.ok;
import static javax.ws.rs.core.Response.status;
import javax.ws.rs.core.SecurityContext;
import org.apache.commons.configuration.Configuration;
import org.restcomm.connect.commons.dao.Sid;
import org.restcomm.connect.commons.telephony.CreateCallType;
import org.restcomm.connect.dao.DaoManager;
import org.restcomm.connect.dao.entities.CallDetailRecord;
import org.restcomm.connect.dao.entities.CallDetailRecordList;
import org.restcomm.connect.dao.entities.RestCommResponse;
import org.restcomm.connect.http.converter.CallDetailRecordConverter;
import org.restcomm.connect.http.converter.CallDetailRecordListConverter;
import org.restcomm.connect.http.converter.RestCommResponseConverter;
import org.restcomm.connect.http.security.ContextUtil;
import org.restcomm.connect.identity.UserIdentityContext;
import org.restcomm.connect.telephony.api.CallInfo;
import org.restcomm.connect.telephony.api.CallManagerResponse;
import org.restcomm.connect.telephony.api.CallResponse;
import org.restcomm.connect.telephony.api.CreateCall;
import org.restcomm.connect.telephony.api.ExecuteCallScript;
import org.restcomm.connect.telephony.api.GetCallInfo;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;

/**
 * @author <a href="mailto:gvagenas@gmail.com">gvagenas</a>
 *
 */
@Path("/Accounts/{accountSid}/UssdPush")
@Singleton
public class UssdPushEndpoint extends AbstractEndpoint {

    @Context
    protected ServletContext context;
    protected Configuration configuration;
    private ActorRef ussdCallManager;
    private DaoManager daos;
    private Gson gson;
    private GsonBuilder builder;
    private XStream xstream;
    private CallDetailRecordListConverter listConverter;




    public UssdPushEndpoint() {
        super();
    }

    @PostConstruct
    public void init() {
        configuration = (Configuration) context.getAttribute(Configuration.class.getName());
        configuration = configuration.subset("runtime-settings");
        ussdCallManager = (ActorRef) context.getAttribute("org.restcomm.connect.ussd.telephony.UssdCallManager");
        daos = (DaoManager) context.getAttribute(DaoManager.class.getName());
        super.init(configuration);
        CallDetailRecordConverter converter = new CallDetailRecordConverter(configuration);
        listConverter = new CallDetailRecordListConverter(configuration);
        builder = new GsonBuilder();
        builder.registerTypeAdapter(CallDetailRecord.class, converter);
        builder.registerTypeAdapter(CallDetailRecordList.class, listConverter);
        builder.setPrettyPrinting();
        gson = builder.create();
        xstream = new XStream();
        xstream.alias("RestcommResponse", RestCommResponse.class);
        xstream.registerConverter(converter);
        xstream.registerConverter(new RestCommResponseConverter(configuration));
        xstream.registerConverter(listConverter);
    }

    @SuppressWarnings("unchecked")
    protected Response putCall(final String accountSid,
            final MultivaluedMap<String, String> data,
            final MediaType responseType,
            UserIdentityContext userIdentityContext) {
        final Sid accountId = new Sid(accountSid);
        permissionEvaluator.secure(daos.getAccountsDao().getAccount(accountSid),
                "RestComm:Create:Calls",
                userIdentityContext);
        try {
            validate(data);
        } catch (final RuntimeException exception) {
            return status(BAD_REQUEST).entity(exception.getMessage()).build();
        }
        final String from = data.getFirst("From");
        final String to = data.getFirst("To");
        final String username = data.getFirst("Username");
        final String password = data.getFirst("Password");
        Integer timeout = getTimeout(data);
        timeout = timeout != null ? timeout : 30;
        final Timeout expires = new Timeout(Duration.create(60, TimeUnit.SECONDS));
        CreateCall create = null;
        //Currently we don't support StatusCallback for USSD Push requests
        try {
            create = new CreateCall(from, to, username, password, true, timeout, CreateCallType.USSD,
                    accountId, null, null, null, null, null);
            create.setCreateCDR(false);
            Future<Object> future = (Future<Object>) ask(ussdCallManager, create, expires);
            Object object = Await.result(future, Duration.create(10, TimeUnit.SECONDS));
            Class<?> klass = object.getClass();
            if (CallManagerResponse.class.equals(klass)) {
                final CallManagerResponse<ActorRef> managerResponse = (CallManagerResponse<ActorRef>) object;
                if (managerResponse.succeeded()) {
                    final ActorRef call = managerResponse.get();
                    future = (Future<Object>) ask(call, new GetCallInfo(), expires);
                    object = Await.result(future, Duration.create(10, TimeUnit.SECONDS));
                    klass = object.getClass();
                    if (CallResponse.class.equals(klass)) {
                        final CallResponse<CallInfo> callResponse = (CallResponse<CallInfo>) object;
                        if (callResponse.succeeded()) {
                            final CallInfo callInfo = callResponse.get();
                            // Execute the call script.
                            final String version = getApiVersion(data);
                            final URI url = getUrl("Url", data);
                            final String method = getMethod("Method", data);
                            final URI fallbackUrl = getUrl("FallbackUrl", data);
                            final String fallbackMethod = getMethod("FallbackMethod", data);
                            final ExecuteCallScript execute = new ExecuteCallScript(call, accountId, version, url, method,
                                    fallbackUrl, fallbackMethod, timeout);
                            ussdCallManager.tell(execute, null);
                            // Create a call detail record for the call.
//                            final CallDetailRecord.Builder builder = CallDetailRecord.builder();
//                            builder.setSid(callInfo.sid());
//                            builder.setDateCreated(callInfo.dateCreated());
//                            builder.setAccountSid(accountId);
//                            builder.setTo(to);
//                            builder.setCallerName(callInfo.fromName());
//                            builder.setFrom(from);
//                            builder.setForwardedFrom(callInfo.forwardedFrom());
//                            builder.setStatus(callInfo.state().toString());
//                            final DateTime now = DateTime.now();
//                            builder.setStartTime(now);
//                            builder.setDirection(callInfo.direction());
//                            builder.setApiVersion(version);
//                            final StringBuilder buffer = new StringBuilder();
//                            buffer.append("/").append(version).append("/Accounts/");
//                            buffer.append(accountId.toString()).append("/Calls/");
//                            buffer.append(callInfo.sid().toString());
//                            final URI uri = URI.create(buffer.toString());
//                            builder.setUri(uri);
//
//                            builder.setCallPath(call.path().toString());
//
//                            final CallDetailRecord cdr = builder.build();
//                            daos.getCallDetailRecordsDao().addCallDetailRecord(cdr);
                            CallDetailRecord cdr = daos.getCallDetailRecordsDao().getCallDetailRecord(callInfo.sid());
                            if (APPLICATION_JSON_TYPE.equals(responseType)) {
                                return ok(gson.toJson(cdr), APPLICATION_JSON).build();
                            } else if (APPLICATION_XML_TYPE.equals(responseType)) {
                                return ok(xstream.toXML(new RestCommResponse(cdr)), APPLICATION_XML).build();
                            } else {
                                return null;
                            }
                        }
                    }
                }
            }
            return status(INTERNAL_SERVER_ERROR).build();
        } catch (final Exception exception) {
            return status(INTERNAL_SERVER_ERROR).entity(exception.getMessage()).build();
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

    @POST
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public Response putCall(@PathParam("accountSid") final String accountSid,
            final MultivaluedMap<String, String> data,
            @HeaderParam("Accept") String accept,
            @Context SecurityContext sec) {
        return putCall(accountSid,
                data,
                retrieveMediaType(accept),
                ContextUtil.convert(sec));
    }
}
