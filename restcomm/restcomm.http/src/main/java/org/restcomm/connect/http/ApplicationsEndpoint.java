/*
 * TeleStax, Open Source Cloud Communications
 * Copyright 2011-2015, Telestax Inc and individual contributors
 * by the @authors tag.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.restcomm.connect.http;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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
import javax.ws.rs.core.UriInfo;
import org.apache.commons.configuration.Configuration;
import org.restcomm.connect.commons.annotations.concurrency.ThreadSafe;
import org.restcomm.connect.commons.dao.Sid;
import org.restcomm.connect.dao.AccountsDao;
import org.restcomm.connect.dao.ApplicationsDao;
import org.restcomm.connect.dao.DaoManager;
import org.restcomm.connect.dao.entities.Account;
import org.restcomm.connect.dao.entities.Application;
import org.restcomm.connect.dao.entities.ApplicationList;
import org.restcomm.connect.dao.entities.ApplicationNumberSummary;
import org.restcomm.connect.dao.entities.RestCommResponse;
import org.restcomm.connect.http.converter.ApplicationConverter;
import org.restcomm.connect.http.converter.ApplicationListConverter;
import org.restcomm.connect.http.converter.ApplicationNumberSummaryConverter;
import org.restcomm.connect.http.converter.RestCommResponseConverter;
import org.restcomm.connect.http.security.ContextUtil;
import org.restcomm.connect.http.security.PermissionEvaluator.SecuredType;
import org.restcomm.connect.identity.UserIdentityContext;

/**
 * @author guilherme.jansen@telestax.com
 */
@Path("/Accounts/{accountSid}/Applications")
@ThreadSafe
@Singleton
public class ApplicationsEndpoint extends AbstractEndpoint {
    @Context
    protected ServletContext context;
    protected Configuration configuration;
    protected ApplicationsDao dao;
    protected Gson gson;
    protected XStream xstream;
    protected AccountsDao accountsDao;




    public ApplicationsEndpoint() {
        super();
    }

    @PostConstruct
    public void init() {
        final DaoManager storage = (DaoManager) context.getAttribute(DaoManager.class.getName());
        dao = storage.getApplicationsDao();
        accountsDao = storage.getAccountsDao();
        configuration = (Configuration) context.getAttribute(Configuration.class.getName());
        configuration = configuration.subset("runtime-settings");
        super.init(configuration);
        final ApplicationConverter converter = new ApplicationConverter(configuration);
        final GsonBuilder builder = new GsonBuilder();
        builder.registerTypeAdapter(Application.class, converter);
        builder.setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES); // if custom converter is not provided, rename camelCase to camel_case. Needed for serializing ApplicationNumberSummary and hopefully other entities too at some point.
        builder.setPrettyPrinting();
        gson = builder.create();
        xstream = new XStream();
        xstream.alias("RestcommResponse", RestCommResponse.class);
        xstream.registerConverter(converter);
        xstream.registerConverter(new ApplicationListConverter(configuration));
        xstream.registerConverter(new RestCommResponseConverter(configuration));
        xstream.registerConverter(new ApplicationNumberSummaryConverter());
        xstream.alias("Number",ApplicationNumberSummary.class);
    }

    private Application createFrom(final Sid accountSid, final MultivaluedMap<String, String> data) {
        final Application.Builder builder = Application.builder();
        final Sid sid = Sid.generate(Sid.Type.APPLICATION);
        builder.setSid(sid);
        builder.setFriendlyName(data.getFirst("FriendlyName"));
        builder.setAccountSid(accountSid);
        builder.setApiVersion(getApiVersion(data));
        builder.setHasVoiceCallerIdLookup(new Boolean(data.getFirst("VoiceCallerIdLookup")));
        final StringBuilder buffer = new StringBuilder();
        buffer.append("/").append(getApiVersion(data)).append("/Accounts/").append(accountSid.toString())
                .append("/Applications/").append(sid.toString());
        builder.setUri(URI.create(buffer.toString()));
        builder.setRcmlUrl(getUrl("RcmlUrl", data));
        if (data.containsKey("Kind")) {
            builder.setKind(Application.Kind.getValueOf(data.getFirst("Kind")));
        }
        return builder.build();
    }

    protected Response getApplication(final String accountSid, final String sid,
            final MediaType responseType,
            UserIdentityContext userIdentityContext) {
        Account account;
        permissionEvaluator.secure(account = accountsDao.getAccount(accountSid),
                "RestComm:Read:Applications", userIdentityContext);
        Application application = null;
        if (Sid.pattern.matcher(sid).matches()) {
            application = dao.getApplication(new Sid(sid));
        } /*else {
            // disabled support for application retrieval based on FriendlyName. It makes no sense to have it if friendly-name based application uniqueness is no longer supported either.

            try {
                // Once not a valid sid, search using the parameter as name
                String name = URLDecoder.decode(String.valueOf(sid), "UTF-8");
                application = dao.getApplication(name);
            } catch (UnsupportedEncodingException e) {
                return status(BAD_REQUEST).entity(e.getMessage()).build();
            }
        }*/
        if (application == null) {
            return status(NOT_FOUND).build();
        } else {
            permissionEvaluator.secure(account, application.getAccountSid(),
                    SecuredType.SECURED_APP, userIdentityContext);
            if (APPLICATION_XML_TYPE.equals(responseType)) {
                final RestCommResponse response = new RestCommResponse(application);
                return ok(xstream.toXML(response), APPLICATION_XML).build();
            } else if (APPLICATION_JSON_TYPE.equals(responseType)) {
                return ok(gson.toJson(application), APPLICATION_JSON).build();
            } else {
                return null;
            }
        }
    }

    protected Response getApplications(final String accountSid,
            final MediaType responseType,
            UriInfo uriInfo,
            UserIdentityContext userIdentityContext) {
        Account account;
        account = accountsDao.getAccount(accountSid);
        permissionEvaluator.secure(account, "RestComm:Read:Applications",
                SecuredType.SECURED_APP, userIdentityContext);
        // shall we also return number information with the application ?
        boolean includeNumbers = false;
        String tmp = uriInfo.getQueryParameters().getFirst("includeNumbers");
        if (tmp != null && tmp.equalsIgnoreCase("true"))
            includeNumbers = true;

        final List<Application> applications = dao.getApplicationsWithNumbers(account.getSid());
        if (APPLICATION_XML_TYPE.equals(responseType)) {
            final RestCommResponse response = new RestCommResponse(new ApplicationList(applications));
            return ok(xstream.toXML(response), APPLICATION_XML).build();
        } else if (APPLICATION_JSON_TYPE.equals(responseType)) {
            return ok(gson.toJson(applications), APPLICATION_JSON).build();
        } else {
            return null;
        }
    }

    public Response putApplication(final String accountSid, final MultivaluedMap<String, String> data,
            final MediaType responseType,
            UserIdentityContext userIdentityContext) {
        Account account;
        account = accountsDao.getAccount(accountSid);
        permissionEvaluator.secure(account, "RestComm:Create:Applications",
                SecuredType.SECURED_APP,
                userIdentityContext);
        try {
            validate(data);
        } catch (final NullPointerException exception) {
            return status(BAD_REQUEST).entity(exception.getMessage()).build();
        }

//        Application application = dao.getApplication(data.getFirst("FriendlyName"));
//        if (application == null) {
//            application = createFrom(new Sid(accountSid), data);
//            dao.addApplication(application);
//        } else if (!application.getAccountSid().toString().equals(account.getSid().toString())) {
//            return status(CONFLICT)
//                    .entity("A application with the same name was already created by another account. Please, choose a different name and try again.")
//                    .build();
//        }

        // application uniqueness now relies only on application SID. No checks on the FriendlyName will be done.
        Application application = createFrom(new Sid(accountSid), data);
        dao.addApplication(application);


        if (APPLICATION_XML_TYPE.equals(responseType)) {
            final RestCommResponse response = new RestCommResponse(application);
            return ok(xstream.toXML(response), APPLICATION_XML).build();
        } else if (APPLICATION_JSON_TYPE.equals(responseType)) {
            return ok(gson.toJson(application), APPLICATION_JSON).build();
        } else {
            return null;
        }
    }

    private void validate(final MultivaluedMap<String, String> data) throws RuntimeException {
        if (!data.containsKey("FriendlyName")) {
            throw new NullPointerException("Friendly name can not be null.");
        }
    }

    protected Response updateApplication(final String accountSid, final String sid, final MultivaluedMap<String, String> data,
            final MediaType responseType,
            UserIdentityContext userIdentityContext) {
        Account account;
        permissionEvaluator.secure(account = accountsDao.getAccount(accountSid),
                "RestComm:Modify:Applications",userIdentityContext);
        final Application application = dao.getApplication(new Sid(sid));
        if (application == null) {
            return status(NOT_FOUND).build();
        } else {
            permissionEvaluator.secure(account, application.getAccountSid(),
                    SecuredType.SECURED_APP,
                    userIdentityContext);
            final Application applicationUpdate = update(application, data);
            dao.updateApplication(applicationUpdate);
            if (APPLICATION_XML_TYPE.equals(responseType)) {
                final RestCommResponse response = new RestCommResponse(applicationUpdate);
                return ok(xstream.toXML(response), APPLICATION_XML).build();
            } else if (APPLICATION_JSON_TYPE.equals(responseType)) {
                return ok(gson.toJson(applicationUpdate), APPLICATION_JSON).build();
            } else {
                return null;
            }
        }
    }

    private Application update(final Application application, final MultivaluedMap<String, String> data) {
        Application result = application;
        if (data.containsKey("FriendlyName")) {
            result = result.setFriendlyName(data.getFirst("FriendlyName"));
        }
        if (data.containsKey("VoiceCallerIdLookup")) {
            result = result.setVoiceCallerIdLookup(new Boolean(data.getFirst("VoiceCallerIdLookup")));
        }
        if (data.containsKey("RcmlUrl")) {
            result = result.setRcmlUrl(getUrl("RcmlUrl", data));
        }
        if (data.containsKey("Kind")) {
            result = result.setKind(Application.Kind.getValueOf(data.getFirst("Kind")));
        }
        return result;
    }


    private Response deleteApplication(final String accountSid, final String sid,
            UserIdentityContext userIdentityContext) {
        Account operatedAccount = accountsDao.getAccount(new Sid(accountSid));
        permissionEvaluator.secure(operatedAccount, "RestComm:Modify:Applications",
                SecuredType.SECURED_APP, userIdentityContext);
        Application application = dao.getApplication(new Sid(sid));
        if (application != null) {
            permissionEvaluator.secure(operatedAccount,
                    application.getAccountSid(), SecuredType.SECURED_APP,
                    userIdentityContext);
        }
        dao.removeApplication(new Sid(sid));
        return ok().build();
    }

    @GET
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public Response getApplications(@PathParam("accountSid") final String accountSid,
            @Context UriInfo uriInfo,
            @HeaderParam("Accept") String accept,
            @Context SecurityContext sec) {
        return getApplications(accountSid, retrieveMediaType(accept), uriInfo,
                ContextUtil.convert(sec));
    }

    @Path("/{sid}")
    @GET
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public Response getApplicationAsXml(@PathParam("accountSid") final String accountSid,
            @PathParam("sid") final String sid,
            @HeaderParam("Accept") String accept,
            @Context SecurityContext sec) {
        return getApplication(accountSid, sid, retrieveMediaType(accept),
                ContextUtil.convert(sec));
    }

    @POST
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public Response putApplication(@PathParam("accountSid") String accountSid,
            final MultivaluedMap<String, String> data,
            @HeaderParam("Accept") String accept,
            @Context SecurityContext sec) {
        return putApplication(accountSid, data, retrieveMediaType(accept),
                ContextUtil.convert(sec));
    }

    @Path("/{sid}")
    @POST
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public Response updateApplicationAsXmlPost(@PathParam("accountSid") final String accountSid,
            @PathParam("sid") final String sid,
            final MultivaluedMap<String, String> data,
            @HeaderParam("Accept") String accept,
            @Context SecurityContext sec) {
        return updateApplication(accountSid, sid, data, retrieveMediaType(accept),
                ContextUtil.convert(sec));
    }

    @Path("/{sid}")
    @PUT
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public Response updateApplicationAsXmlPut(@PathParam("accountSid") final String accountSid,
            @PathParam("sid") final String sid,
            final MultivaluedMap<String, String> data,
            @HeaderParam("Accept") String accept,
            @Context SecurityContext sec) {
        return updateApplication(accountSid, sid, data, retrieveMediaType(accept),
                ContextUtil.convert(sec));
    }

    @Path("/{sid}")
    @DELETE
    public Response deleteApplicationAsXml(@PathParam("accountSid") final String accountSid,
            @PathParam("sid") final String sid,
            @Context SecurityContext sec) {
        return deleteApplication(accountSid, sid,ContextUtil.convert(sec));
    }

}
