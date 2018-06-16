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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;
import com.sun.jersey.spi.resource.Singleton;
import com.thoughtworks.xstream.XStream;
import java.net.URI;
import java.util.List;
import javax.annotation.PostConstruct;
import javax.servlet.ServletContext;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
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
import static javax.ws.rs.core.Response.Status.NOT_FOUND;
import static javax.ws.rs.core.Response.ok;
import static javax.ws.rs.core.Response.status;
import javax.ws.rs.core.SecurityContext;
import org.apache.commons.configuration.Configuration;
import org.joda.time.DateTime;
import org.restcomm.connect.commons.annotations.concurrency.ThreadSafe;
import org.restcomm.connect.commons.dao.Sid;
import org.restcomm.connect.dao.DaoManager;
import org.restcomm.connect.dao.OutgoingCallerIdsDao;
import org.restcomm.connect.dao.entities.Account;
import org.restcomm.connect.dao.entities.OutgoingCallerId;
import org.restcomm.connect.dao.entities.OutgoingCallerIdList;
import org.restcomm.connect.dao.entities.RestCommResponse;
import org.restcomm.connect.http.converter.OutgoingCallerIdConverter;
import org.restcomm.connect.http.converter.OutgoingCallerIdListConverter;
import org.restcomm.connect.http.converter.RestCommResponseConverter;
import org.restcomm.connect.http.security.ContextUtil;
import org.restcomm.connect.http.security.PermissionEvaluator.SecuredType;
import org.restcomm.connect.identity.UserIdentityContext;

/**
 * @author quintana.thomas@gmail.com (Thomas Quintana)
 */
@Path("/Accounts/{accountSid}/OutgoingCallerIds")
@ThreadSafe
@Singleton
public class OutgoingCallerIdsEndpoint extends AbstractEndpoint {

    @Context
    private ServletContext context;
    private Configuration configuration;
    private OutgoingCallerIdsDao dao;
    private Gson gson;
    private XStream xstream;

    public OutgoingCallerIdsEndpoint() {
        super();
    }

    @PostConstruct
    public void init() {
        final DaoManager storage = (DaoManager) context.getAttribute(DaoManager.class.getName());
        configuration = (Configuration) context.getAttribute(Configuration.class.getName());
        configuration = configuration.subset("runtime-settings");
        super.init(configuration);
        dao = storage.getOutgoingCallerIdsDao();
        final OutgoingCallerIdConverter converter = new OutgoingCallerIdConverter(configuration);
        final GsonBuilder builder = new GsonBuilder();
        builder.registerTypeAdapter(OutgoingCallerId.class, converter);
        builder.setPrettyPrinting();
        gson = builder.create();
        xstream = new XStream();
        xstream.alias("RestcommResponse", RestCommResponse.class);
        xstream.registerConverter(converter);
        xstream.registerConverter(new OutgoingCallerIdListConverter(configuration));
        xstream.registerConverter(new RestCommResponseConverter(configuration));
    }

    private OutgoingCallerId createFrom(final Sid accountSid, final MultivaluedMap<String, String> data) {
        final Sid sid = Sid.generate(Sid.Type.PHONE_NUMBER);
        final DateTime now = DateTime.now();
        final PhoneNumberUtil phoneNumberUtil = PhoneNumberUtil.getInstance();
        PhoneNumber phoneNumber = null;
        try {
            phoneNumber = phoneNumberUtil.parse(data.getFirst("PhoneNumber"), "US");
        } catch (final NumberParseException ignored) {
        }
        String friendlyName = phoneNumberUtil.format(phoneNumber, PhoneNumberFormat.NATIONAL);
        if (data.containsKey("FriendlyName")) {
            friendlyName = data.getFirst("FriendlyName");
        }
        final StringBuilder buffer = new StringBuilder();
        buffer.append("/").append(getApiVersion(null)).append("/Accounts/").append(accountSid.toString())
                .append("/OutgoingCallerIds/").append(sid.toString());
        final URI uri = URI.create(buffer.toString());
        return new OutgoingCallerId(sid, now, now, friendlyName, accountSid, phoneNumberUtil.format(phoneNumber,
                PhoneNumberFormat.E164), uri);
    }

    protected Response getCallerId(final String accountSid,
            final String sid,
            final MediaType responseType,
            UserIdentityContext userIdentityContext) {
        Account operatedAccount = accountsDao.getAccount(accountSid);
        permissionEvaluator.secure(operatedAccount, "RestComm:Read:OutgoingCallerIds",
                userIdentityContext);
        final OutgoingCallerId outgoingCallerId = dao.getOutgoingCallerId(new Sid(sid));
        if (outgoingCallerId == null) {
            return status(NOT_FOUND).build();
        } else {
            permissionEvaluator.secure(operatedAccount,
                    outgoingCallerId.getAccountSid(),
                    SecuredType.SECURED_STANDARD,
                    userIdentityContext);
            if (APPLICATION_JSON_TYPE.equals(responseType)) {
                return ok(gson.toJson(outgoingCallerId), APPLICATION_JSON).build();
            } else if (APPLICATION_XML_TYPE.equals(responseType)) {
                final RestCommResponse response = new RestCommResponse(outgoingCallerId);
                return ok(xstream.toXML(response), APPLICATION_XML).build();
            } else {
                return null;
            }
        }
    }

    protected Response getCallerIds(final String accountSid,
            final MediaType responseType,
            UserIdentityContext userIdentityContext) {
        permissionEvaluator.secure(accountsDao.getAccount(accountSid),
                "RestComm:Read:OutgoingCallerIds",
                userIdentityContext);
        final List<OutgoingCallerId> outgoingCallerIds = dao.getOutgoingCallerIds(new Sid(accountSid));
        if (APPLICATION_JSON_TYPE.equals(responseType)) {
            return ok(gson.toJson(outgoingCallerIds), APPLICATION_JSON).build();
        } else if (APPLICATION_XML_TYPE.equals(responseType)) {
            final RestCommResponse response = new RestCommResponse(new OutgoingCallerIdList(outgoingCallerIds));
            return ok(xstream.toXML(response), APPLICATION_XML).build();
        } else {
            return null;
        }
    }

    protected Response putOutgoingCallerId(final String accountSid,
            final MultivaluedMap<String, String> data,
            final MediaType responseType,
            UserIdentityContext userIdentityContext) {
        permissionEvaluator.secure(accountsDao.getAccount(accountSid),
                "RestComm:Create:OutgoingCallerIds",
                userIdentityContext);
        try {
            validate(data);
        } catch (final NullPointerException exception) {
            return status(BAD_REQUEST).entity(exception.getMessage()).build();
        }
        final OutgoingCallerId outgoingCallerId = createFrom(new Sid(accountSid), data);
        dao.addOutgoingCallerId(outgoingCallerId);
        if (APPLICATION_JSON_TYPE.equals(responseType)) {
            return ok(gson.toJson(outgoingCallerId), APPLICATION_JSON).build();
        } else if (APPLICATION_XML_TYPE.equals(responseType)) {
            final RestCommResponse response = new RestCommResponse(outgoingCallerId);
            return ok(xstream.toXML(response), APPLICATION_XML).build();
        } else {
            return null;
        }
    }

    protected Response updateOutgoingCallerId(final String accountSid,
            final String sid,
            final MultivaluedMap<String, String> data,
            final MediaType responseType,
            UserIdentityContext userIdentityContext) {
        Account operatedAccount = accountsDao.getAccount(accountSid);
        permissionEvaluator.secure(operatedAccount,
                "RestComm:Modify:OutgoingCallerIds",
                userIdentityContext);
        OutgoingCallerId outgoingCallerId = dao.getOutgoingCallerId(new Sid(sid));
        if (outgoingCallerId == null) {
            return status(NOT_FOUND).build();
        } else {
            permissionEvaluator.secure(operatedAccount,
                    outgoingCallerId.getAccountSid(),
                    SecuredType.SECURED_STANDARD,
                    userIdentityContext);
            if (data.containsKey("FriendlyName")) {
                final String friendlyName = data.getFirst("FriendlyName");
                outgoingCallerId = outgoingCallerId.setFriendlyName(friendlyName);
            }
            dao.updateOutgoingCallerId(outgoingCallerId);
            if (APPLICATION_JSON_TYPE.equals(responseType)) {
                return ok(gson.toJson(outgoingCallerId), APPLICATION_JSON).build();
            } else if (APPLICATION_XML_TYPE.equals(responseType)) {
                final RestCommResponse response = new RestCommResponse(outgoingCallerId);
                return ok(xstream.toXML(response), APPLICATION_XML).build();
            } else {
                return null;
            }
        }
    }

    private void validate(final MultivaluedMap<String, String> data) throws RuntimeException {
        if (!data.containsKey("PhoneNumber")) {
            throw new NullPointerException("Phone number can not be null.");
        }
        try {
            PhoneNumberUtil.getInstance().parse(data.getFirst("PhoneNumber"), "US");
        } catch (final NumberParseException exception) {
            throw new IllegalArgumentException("Invalid phone number.");
        }
    }

    private Response deleteOutgoingCallerId(String accountSid,
            String sid,
            UserIdentityContext userIdentityContext) {
        Account operatedAccount = super.accountsDao.getAccount(accountSid);
        permissionEvaluator.secure(operatedAccount,
                "RestComm:Delete:OutgoingCallerIds",
                userIdentityContext);
        OutgoingCallerId oci = dao.getOutgoingCallerId(new Sid(sid));
        if (oci != null) {
            permissionEvaluator.secure(operatedAccount,
                    String.valueOf(oci.getAccountSid()),
                    SecuredType.SECURED_STANDARD,
                    userIdentityContext);
        } // TODO return a NOT_FOUND status code here if oci==null maybe ?
        dao.removeOutgoingCallerId(new Sid(sid));
        return ok().build();
    }

    @Path("/{sid}")
    @DELETE
    public Response deleteOutgoingCallerIdAsXml(@PathParam("accountSid") String accountSid,
            @PathParam("sid") String sid,
            @Context SecurityContext sec) {
        return deleteOutgoingCallerId(accountSid, sid, ContextUtil.convert(sec));
    }

    @Path("/{sid}")
    @GET
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public Response getCallerIdAsXml(@PathParam("accountSid") final String accountSid,
            @PathParam("sid") final String sid,
            @HeaderParam("Accept") String accept,
            @Context SecurityContext sec) {
        return getCallerId(accountSid, sid, retrieveMediaType(accept),
                ContextUtil.convert(sec));
    }

    @GET
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public Response getCallerIds(@PathParam("accountSid") final String accountSid,
            @HeaderParam("Accept") String accept,
            @Context SecurityContext sec) {
        return getCallerIds(accountSid, retrieveMediaType(accept),
                ContextUtil.convert(sec));
    }

    @POST
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public Response putOutgoingCallerId(@PathParam("accountSid") final String accountSid,
            final MultivaluedMap<String, String> data,
            @HeaderParam("Accept") String accept,
            @Context SecurityContext sec) {
        return putOutgoingCallerId(accountSid, data, retrieveMediaType(accept),
                ContextUtil.convert(sec));
    }

    @Path("/{sid}")
    @PUT
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public Response updateOutgoingCallerIdAsXml(@PathParam("accountSid") final String accountSid,
            @PathParam("sid") final String sid,
            final MultivaluedMap<String, String> data,
            @HeaderParam("Accept") String accept,
            @Context SecurityContext sec) {
        return updateOutgoingCallerId(accountSid, sid, data, retrieveMediaType(accept),
                ContextUtil.convert(sec));
    }
}
