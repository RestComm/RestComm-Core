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
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.actor.UntypedActorContext;
import akka.actor.UntypedActorFactory;
import static akka.pattern.Patterns.ask;
import akka.util.Timeout;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat;
import com.sun.jersey.spi.resource.Singleton;
import com.thoughtworks.xstream.XStream;
import java.math.BigDecimal;
import java.net.URI;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Currency;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
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
import static javax.ws.rs.core.MediaType.*;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import static javax.ws.rs.core.Response.Status.*;
import static javax.ws.rs.core.Response.ok;
import static javax.ws.rs.core.Response.status;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;
import org.apache.commons.configuration.Configuration;
import org.restcomm.connect.commons.annotations.concurrency.ThreadSafe;
import org.restcomm.connect.commons.configuration.RestcommConfiguration;
import org.restcomm.connect.commons.dao.Sid;
import org.restcomm.connect.commons.faulttolerance.RestcommUntypedActor;
import org.restcomm.connect.commons.patterns.Observe;
import org.restcomm.connect.dao.DaoManager;
import org.restcomm.connect.dao.SmsMessagesDao;
import org.restcomm.connect.dao.entities.Account;
import org.restcomm.connect.dao.entities.RestCommResponse;
import org.restcomm.connect.dao.entities.SmsMessage;
import org.restcomm.connect.dao.entities.SmsMessageFilter;
import org.restcomm.connect.dao.entities.SmsMessageList;
import org.restcomm.connect.http.converter.RestCommResponseConverter;
import org.restcomm.connect.http.converter.SmsMessageConverter;
import org.restcomm.connect.http.converter.SmsMessageListConverter;
import org.restcomm.connect.http.security.ContextUtil;
import org.restcomm.connect.http.security.PermissionEvaluator.SecuredType;
import org.restcomm.connect.identity.UserIdentityContext;
import org.restcomm.connect.sms.api.CreateSmsSession;
import org.restcomm.connect.sms.api.SmsServiceResponse;
import org.restcomm.connect.sms.api.SmsSessionAttribute;
import org.restcomm.connect.sms.api.SmsSessionInfo;
import org.restcomm.connect.sms.api.SmsSessionRequest;
import org.restcomm.connect.sms.api.SmsSessionResponse;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;

/**
 * @author quintana.thomas@gmail.com (Thomas Quintana)
 */
@Path("/Accounts/{accountSid}/SMS/Messages")
@ThreadSafe
@Singleton
public class SmsMessagesEndpoint extends AbstractEndpoint {
    @Context
    protected ServletContext context;
    protected ActorSystem system;
    protected Configuration configuration;
    protected ActorRef aggregator;
    protected SmsMessagesDao dao;
    protected Gson gson;
    protected XStream xstream;
    protected SmsMessageListConverter listConverter;
    protected String instanceId;

    private boolean normalizePhoneNumbers;




    public SmsMessagesEndpoint() {
        super();
    }

    @PostConstruct
    public void init() {
        final DaoManager storage = (DaoManager) context.getAttribute(DaoManager.class.getName());
        configuration = (Configuration) context.getAttribute(Configuration.class.getName());
        configuration = configuration.subset("runtime-settings");
        dao = storage.getSmsMessagesDao();
        aggregator = (ActorRef) context.getAttribute("org.restcomm.connect.sms.SmsService");
        system = (ActorSystem) context.getAttribute(ActorSystem.class.getName());
        super.init(configuration);
        final SmsMessageConverter converter = new SmsMessageConverter(configuration);
        listConverter = new SmsMessageListConverter(configuration);
        final GsonBuilder builder = new GsonBuilder();
        builder.registerTypeAdapter(SmsMessage.class, converter);
        builder.registerTypeAdapter(SmsMessageList.class, listConverter);
        builder.setPrettyPrinting();
        gson = builder.create();
        xstream = new XStream();
        xstream.alias("RestcommResponse", RestCommResponse.class);
        xstream.registerConverter(converter);
        xstream.registerConverter(new SmsMessageListConverter(configuration));
        xstream.registerConverter(new RestCommResponseConverter(configuration));
        xstream.registerConverter(listConverter);

        instanceId = RestcommConfiguration.getInstance().getMain().getInstanceId();

        normalizePhoneNumbers = configuration.getBoolean("normalize-numbers-for-outbound-calls");
    }

    protected Response getSmsMessage(final String accountSid,
            final String sid,
            final MediaType responseType,
            UserIdentityContext userIdentityContext) {
        Account operatedAccount = accountsDao.getAccount(accountSid);
        permissionEvaluator.secure(operatedAccount,
                "RestComm:Read:SmsMessages",
                userIdentityContext);
        final SmsMessage smsMessage = dao.getSmsMessage(new Sid(sid));
        if (smsMessage == null) {
            return status(NOT_FOUND).build();
        } else {
            permissionEvaluator.secure(operatedAccount,
                    smsMessage.getAccountSid(),
                    SecuredType.SECURED_STANDARD,
                    userIdentityContext);
            if (APPLICATION_JSON_TYPE.equals(responseType)) {
                return ok(gson.toJson(smsMessage), APPLICATION_JSON).build();
            } else if (APPLICATION_XML_TYPE.equals(responseType)) {
                final RestCommResponse response = new RestCommResponse(smsMessage);
                return ok(xstream.toXML(response), APPLICATION_XML).build();
            } else {
                return null;
            }
        }
    }

    protected Response getSmsMessages(final String accountSid,
            UriInfo info,
            final MediaType responseType,
            UserIdentityContext userIdentityContext) {
        permissionEvaluator.secure(accountsDao.getAccount(accountSid),
                "RestComm:Read:SmsMessages",
                userIdentityContext);

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
        String startTime = info.getQueryParameters().getFirst("StartTime");
        String endTime = info.getQueryParameters().getFirst("EndTime");
        String body = info.getQueryParameters().getFirst("Body");

        if (pageSize == null) {
            pageSize = "50";
        }

        if (page == null) {
            page = "0";
        }

        int limit = Integer.parseInt(pageSize);
        int offset = (page.equals("0")) ? 0 : (((Integer.parseInt(page) - 1) * Integer.parseInt(pageSize)) + Integer
                .parseInt(pageSize));

        // Shall we query cdrs of sub-accounts too ?
        // if we do, we need to find the sub-accounts involved first
        List<String> ownerAccounts = null;
        if (querySubAccounts) {
            ownerAccounts = new ArrayList<String>();
            ownerAccounts.add(accountSid); // we will also return parent account cdrs
            ownerAccounts.addAll(accountsDao.getSubAccountSidsRecursive(new Sid(accountSid)));
        }

        SmsMessageFilter filterForTotal;

        try {

            if (localInstanceOnly) {
                filterForTotal = new SmsMessageFilter(accountSid, ownerAccounts, recipient, sender, startTime, endTime,
                        body, null, null);
            } else {
                filterForTotal = new SmsMessageFilter(accountSid, ownerAccounts, recipient, sender, startTime, endTime,
                        body, null, null, instanceId);
            }
        } catch (ParseException e) {
            return status(BAD_REQUEST).build();
        }

        final int total = dao.getTotalSmsMessage(filterForTotal);

        if (Integer.parseInt(page) > (total / limit)) {
            return status(javax.ws.rs.core.Response.Status.BAD_REQUEST).build();
        }

        SmsMessageFilter filter;

        try {
            if (localInstanceOnly) {
                filter = new SmsMessageFilter(accountSid, ownerAccounts, recipient, sender, startTime, endTime,
                        body, limit, offset);
            } else {
                filter = new SmsMessageFilter(accountSid, ownerAccounts, recipient, sender, startTime, endTime,
                        body, limit, offset, instanceId);
            }
        } catch (ParseException e) {
            return status(BAD_REQUEST).build();
        }

        final List<SmsMessage> cdrs = dao.getSmsMessages(filter);

        listConverter.setCount(total);
        listConverter.setPage(Integer.parseInt(page));
        listConverter.setPageSize(Integer.parseInt(pageSize));
        listConverter.setPathUri(info.getRequestUri().getPath());

        if (APPLICATION_XML_TYPE.equals(responseType)) {
            final RestCommResponse response = new RestCommResponse(new SmsMessageList(cdrs));
            return ok(xstream.toXML(response), APPLICATION_XML).build();
        } else if (APPLICATION_JSON_TYPE.equals(responseType)) {
            return ok(gson.toJson(new SmsMessageList(cdrs)), APPLICATION_JSON).build();
        } else {
            return null;
        }
    }

    private void normalize(final MultivaluedMap<String, String> data) throws IllegalArgumentException {
        final PhoneNumberUtil phoneNumberUtil = PhoneNumberUtil.getInstance();
        final String from = data.getFirst("From");
        data.remove("From");
        try {
            data.putSingle("From", phoneNumberUtil.format(phoneNumberUtil.parse(from, "US"), PhoneNumberFormat.E164));
        } catch (final NumberParseException exception) {
            throw new IllegalArgumentException(exception);
        }
        final String to = data.getFirst("To");
        data.remove("To");
        try {
            data.putSingle("To", phoneNumberUtil.format(phoneNumberUtil.parse(to, "US"), PhoneNumberFormat.E164));
        } catch (final NumberParseException exception) {
            throw new IllegalArgumentException(exception);
        }
        final String body = data.getFirst("Body");
        if (body.getBytes().length > 160) {
            data.remove("Body");
            data.putSingle("Body", body.substring(0, 159));
        }
    }

    @SuppressWarnings("unchecked")
    protected Response putSmsMessage(final String accountSid,
            final MultivaluedMap<String, String> data,
            final MediaType responseType,
            UserIdentityContext userIdentityContext) {
        permissionEvaluator.secure(accountsDao.getAccount(accountSid),
                "RestComm:Create:SmsMessages",
                userIdentityContext);
        try {
            validate(data);
            if(normalizePhoneNumbers)
                normalize(data);
        } catch (final RuntimeException exception) {
            return status(BAD_REQUEST).entity(exception.getMessage()).build();
        }
        final String sender = data.getFirst("From");
        final String recipient = data.getFirst("To");
        final String body = data.getFirst("Body");
        final SmsSessionRequest.Encoding encoding;
        if (!data.containsKey("Encoding")) {
            encoding = SmsSessionRequest.Encoding.GSM;
        } else {
            encoding = SmsSessionRequest.Encoding.valueOf(data.getFirst("Encoding").replace('-', '_'));
        }
        ConcurrentHashMap<String, String> customRestOutgoingHeaderMap = new ConcurrentHashMap<String, String>();
        Iterator<String> iter = data.keySet().iterator();
        while (iter.hasNext()) {
            String name = iter.next();
            if (name.startsWith("X-")){
                customRestOutgoingHeaderMap.put(name, data.getFirst(name));
            }
        }
        final Timeout expires = new Timeout(Duration.create(60, TimeUnit.SECONDS));
        try {
            Future<Object> future = (Future<Object>) ask(aggregator, new CreateSmsSession(sender, recipient, accountSid, true), expires);
            Object object = Await.result(future, Duration.create(10, TimeUnit.SECONDS));
            Class<?> klass = object.getClass();
            if (SmsServiceResponse.class.equals(klass)) {
                final SmsServiceResponse<ActorRef> smsServiceResponse = (SmsServiceResponse<ActorRef>) object;
                if (smsServiceResponse.succeeded()) {
                    // Create an SMS record for the text message.
                    final SmsMessage record = sms(new Sid(accountSid), getApiVersion(data), sender, recipient, body,
                            SmsMessage.Status.SENDING, SmsMessage.Direction.OUTBOUND_API);
                    dao.addSmsMessage(record);
                    // Send the sms.
                    final ActorRef session = smsServiceResponse.get();
                    final ActorRef observer = observer();
                    session.tell(new Observe(observer), observer);
                    session.tell(new SmsSessionAttribute("record", record), null);
                    final SmsSessionRequest request = new SmsSessionRequest(sender, recipient, body, encoding, customRestOutgoingHeaderMap);
                    session.tell(request, null);
                    if (APPLICATION_JSON_TYPE.equals(responseType)) {
                        return ok(gson.toJson(record), APPLICATION_JSON).build();
                    } else if (APPLICATION_XML_TYPE.equals(responseType)) {
                        final RestCommResponse response = new RestCommResponse(record);
                        return ok(xstream.toXML(response), APPLICATION_XML).build();
                    } else {
                        return null;
                    }
                } else {
                    String msg = smsServiceResponse.cause().getMessage();
                    String error = "SMS_LIMIT_EXCEEDED";
                    return status(Response.Status.FORBIDDEN).entity(buildErrorResponseBody(msg, error, responseType)).build();
                }
            }
            return status(INTERNAL_SERVER_ERROR).build();
        } catch (final Exception exception) {
            return status(INTERNAL_SERVER_ERROR).entity(exception.getMessage()).build();
        }
    }

    private SmsMessage sms(final Sid accountSid, final String apiVersion, final String sender, final String recipient,
            final String body, final SmsMessage.Status status, final SmsMessage.Direction direction) {
        final SmsMessage.Builder builder = SmsMessage.builder();
        final Sid sid = Sid.generate(Sid.Type.SMS_MESSAGE);
        builder.setSid(sid);
        builder.setAccountSid(accountSid);
        builder.setSender(sender);
        builder.setRecipient(recipient);
        builder.setBody(body);
        builder.setStatus(status);
        builder.setDirection(direction);
        builder.setPrice(new BigDecimal(0.00));
        // TODO - this needs to be added as property to Configuration somehow
        builder.setPriceUnit(Currency.getInstance("USD"));
        builder.setApiVersion(apiVersion);
        final StringBuilder buffer = new StringBuilder();
        buffer.append("/").append(apiVersion).append("/Accounts/");
        buffer.append(accountSid.toString()).append("/SMS/Messages/");
        buffer.append(sid.toString());
        final URI uri = URI.create(buffer.toString());
        builder.setUri(uri);
        return builder.build();
    }

    private void validate(final MultivaluedMap<String, String> data) throws NullPointerException {
        if (!data.containsKey("From")) {
            throw new NullPointerException("From can not be null.");
        } else if (!data.containsKey("To")) {
            throw new NullPointerException("To can not be null.");
        } else if (!data.containsKey("Body")) {
            throw new NullPointerException("Body can not be null.");
        }
    }

    private ActorRef observer() {
        final Props props = new Props(new UntypedActorFactory() {
            private static final long serialVersionUID = 1L;

            @Override
            public UntypedActor create() throws Exception {
                return new SmsSessionObserver();
            }
        });
        return system.actorOf(props);
    }

    private final class SmsSessionObserver extends RestcommUntypedActor {
        public SmsSessionObserver() {
            super();
        }

        @Override
        public void onReceive(final Object message) throws Exception {
            final Class<?> klass = message.getClass();
            if (SmsSessionResponse.class.equals(klass)) {
                final SmsSessionResponse response = (SmsSessionResponse) message;
                final SmsSessionInfo info = response.info();
                SmsMessage record = (SmsMessage) info.attributes().get("record");
                dao.updateSmsMessage(record);
                final UntypedActorContext context = getContext();
                final ActorRef self = self();
                context.stop(self);
            }
        }
    }

    @Path("/{sid}")
    @GET
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public Response getSmsMessageAsXml(@PathParam("accountSid") final String accountSid,
            @PathParam("sid") final String sid,
            @HeaderParam("Accept") String accept,
            @Context SecurityContext sec) {
        return getSmsMessage(accountSid, sid,
                retrieveMediaType(accept),
                ContextUtil.convert(sec));
    }

    @GET
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public Response getSmsMessages(@PathParam("accountSid") final String accountSid,
            @Context UriInfo info,
            @HeaderParam("Accept") String accept,
            @Context SecurityContext sec) {
        return getSmsMessages(accountSid, info, retrieveMediaType(accept),
                ContextUtil.convert(sec));
    }

    @POST
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public Response putSmsMessage(@PathParam("accountSid") final String accountSid,
            final MultivaluedMap<String, String> data,
            @HeaderParam("Accept") String accept,
            @Context SecurityContext sec) {
        return putSmsMessage(accountSid, data, retrieveMediaType(accept),
                ContextUtil.convert(sec));
    }
}
