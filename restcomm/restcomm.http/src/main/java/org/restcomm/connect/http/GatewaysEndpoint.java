package org.restcomm.connect.http;

import akka.actor.ActorRef;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.jersey.spi.resource.Singleton;
import com.thoughtworks.xstream.XStream;
import java.net.URI;
import java.util.List;
import javax.annotation.PostConstruct;
import javax.annotation.security.RolesAllowed;
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
import javax.ws.rs.core.SecurityContext;

import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static javax.ws.rs.core.Response.Status.NOT_FOUND;
import static javax.ws.rs.core.Response.ok;
import static javax.ws.rs.core.Response.status;
import org.apache.commons.configuration.Configuration;
import org.restcomm.connect.commons.annotations.concurrency.ThreadSafe;
import org.restcomm.connect.commons.dao.Sid;
import org.restcomm.connect.dao.DaoManager;
import org.restcomm.connect.dao.GatewaysDao;
import org.restcomm.connect.dao.entities.Gateway;
import org.restcomm.connect.dao.entities.GatewayList;
import org.restcomm.connect.dao.entities.RestCommResponse;
import org.restcomm.connect.http.converter.GatewayConverter;
import org.restcomm.connect.http.converter.GatewayListConverter;
import org.restcomm.connect.http.converter.RestCommResponseConverter;
import org.restcomm.connect.http.security.ContextUtil;

import static org.restcomm.connect.http.security.AccountPrincipal.SUPER_ADMIN_ROLE;
import org.restcomm.connect.identity.UserIdentityContext;
import org.restcomm.connect.telephony.api.RegisterGateway;

@Path("/Accounts/{accountSid}/Management/Gateways")
@ThreadSafe
@RolesAllowed(SUPER_ADMIN_ROLE)
@Singleton
public class GatewaysEndpoint extends AbstractEndpoint {
    @Context
    protected ServletContext context;
    protected Configuration configuration;
    protected GatewaysDao dao;
    protected Gson gson;
    protected XStream xstream;
    private ActorRef proxyManager;




    public GatewaysEndpoint() {
        super();
    }

    @PostConstruct
    public void init() {
        final DaoManager storage = (DaoManager) context.getAttribute(DaoManager.class.getName());
        configuration = (Configuration) context.getAttribute(Configuration.class.getName());
        configuration = configuration.subset("runtime-settings");
        super.init(configuration);
        dao = storage.getGatewaysDao();
        final GatewayConverter converter = new GatewayConverter(configuration);
        final GsonBuilder builder = new GsonBuilder();
        builder.registerTypeAdapter(Gateway.class, converter);
        builder.setPrettyPrinting();
        gson = builder.create();
        xstream = new XStream();
        xstream.alias("RestcommResponse", RestCommResponse.class);
        xstream.registerConverter(converter);
        xstream.registerConverter(new GatewayListConverter(configuration));
        xstream.registerConverter(new RestCommResponseConverter(configuration));
        proxyManager = (ActorRef) context.getAttribute("org.restcomm.connect.telephony.proxy.ProxyManager");
    }

    private Gateway createFrom(final MultivaluedMap<String, String> data) {
        final Gateway.Builder builder = Gateway.builder();
        final Sid sid = Sid.generate(Sid.Type.GATEWAY);
        builder.setSid(sid);
        String friendlyName = data.getFirst("FriendlyName");
        if (friendlyName == null || friendlyName.isEmpty()) {
            friendlyName = data.getFirst("UserName");
        }
        builder.setFriendlyName(friendlyName);
        builder.setPassword(data.getFirst("Password"));
        builder.setProxy(data.getFirst("Proxy"));
        final boolean register = Boolean.parseBoolean(data.getFirst("Register"));
        builder.setRegister(register);
        builder.setUserName(data.getFirst("UserName"));
        final int ttl = Integer.parseInt(data.getFirst("TTL"));
        builder.setTimeToLive(ttl);
        final StringBuilder buffer = new StringBuilder();
        buffer.append("/").append(getApiVersion(data)).append("/Management/").append("Gateways/").append(sid.toString());
        builder.setUri(URI.create(buffer.toString()));
        return builder.build();
    }

    protected Response getGateway(final String accountSid, final String sid, final MediaType responseType) {
        final Gateway gateway = dao.getGateway(new Sid(sid));
        if (gateway == null) {
            return status(NOT_FOUND).build();
        } else {
            if (APPLICATION_XML_TYPE.equals(responseType)) {
                final RestCommResponse response = new RestCommResponse(gateway);
                return ok(xstream.toXML(response), APPLICATION_XML).build();
            } else if (APPLICATION_JSON_TYPE.equals(responseType)) {
                return ok(gson.toJson(gateway), APPLICATION_JSON).build();
            } else {
                return null;
            }
        }
    }

    protected Response getGateways(final String accountSid, final MediaType responseType) {
        final List<Gateway> gateways = dao.getGateways();
        if (APPLICATION_XML_TYPE.equals(responseType)) {
            final RestCommResponse response = new RestCommResponse(new GatewayList(gateways));
            return ok(xstream.toXML(response), APPLICATION_XML).build();
        } else if (APPLICATION_JSON_TYPE.equals(responseType)) {
            return ok(gson.toJson(gateways), APPLICATION_JSON).build();
        } else {
            return null;
        }
    }

    protected Response putGateway(final String accountSid, final MultivaluedMap<String, String> data, final MediaType responseType) {

        try {
            validate(data);
        } catch (final RuntimeException exception) {
            return status(BAD_REQUEST).entity(exception.getMessage()).build();
        }
        final Gateway gateway = createFrom(data);
        dao.addGateway(gateway);
        if (proxyManager == null) {
            proxyManager = (ActorRef) context.getAttribute("org.restcomm.connect.telephony.proxy.ProxyManager");
        }
        proxyManager.tell(new RegisterGateway(gateway), null);
        if (APPLICATION_XML_TYPE.equals(responseType)) {
            final RestCommResponse response = new RestCommResponse(gateway);
            return ok(xstream.toXML(response), APPLICATION_XML).build();
        } else if (APPLICATION_JSON_TYPE.equals(responseType)) {
            return ok(gson.toJson(gateway), APPLICATION_JSON).build();
        } else {
            return null;
        }
    }

    protected Response updateGateway(final String accountSid, final String sid, final MultivaluedMap<String, String> data, final MediaType responseType) {

        Gateway gateway = dao.getGateway(new Sid(sid));
        if (gateway == null) {
            return status(NOT_FOUND).build();
        } else {
            dao.updateGateway(update(gateway, data));
            gateway = dao.getGateway(new Sid(sid));
            if (APPLICATION_XML_TYPE.equals(responseType)) {
                final RestCommResponse response = new RestCommResponse(gateway);
                return ok(xstream.toXML(response), APPLICATION_XML).build();
            } else if (APPLICATION_JSON_TYPE.equals(responseType)) {
                return ok(gson.toJson(gateway), APPLICATION_JSON).build();
            } else {
                return null;
            }
        }
    }

    private void validate(final MultivaluedMap<String, String> data) {
        if (!data.containsKey("UserName")) {
            throw new NullPointerException("UserName can not be null.");
        } else if (!data.containsKey("Password")) {
            throw new NullPointerException("Password can not be null.");
        } else if (!data.containsKey("Proxy")) {
            throw new NullPointerException("The Proxy can not be null");
        } else if (!data.containsKey("Register")) {
            throw new NullPointerException("Register must be true or false");
        }
    }

    private Gateway update(final Gateway gateway, final MultivaluedMap<String, String> data) {
        Gateway result = gateway;
        if (data.containsKey("FriendlyName")) {
            result = result.setFriendlyName(data.getFirst("FriendlyName"));
        }
        if (data.containsKey("UserName")) {
            result = result.setUserName(data.getFirst("UserName"));
        }
        if (data.containsKey("Password")) {
            result = result.setPassword(data.getFirst("Password"));
        }
        if (data.containsKey("Proxy")) {
            result = result.setProxy(data.getFirst("Proxy"));
        }
        if (data.containsKey("Register")) {
            result = result.setRegister(Boolean.parseBoolean("Register"));
        }
        if (data.containsKey("TTL")) {
            result = result.setTimeToLive(Integer.parseInt(data.getFirst("TTL")));
        }
        return result;
    }

    private Response deleteGateway(final String accountSid,
            final String sid,
            UserIdentityContext userIdentityContext) {
        permissionEvaluator.secure(super.accountsDao.getAccount(accountSid),
                "RestComm:Modify:Gateways",
                userIdentityContext);
        dao.removeGateway(new Sid(sid));
        return ok().build();
    }

    @Path("/{sid}")
    @DELETE
    public Response deleteGatewayAsXml(@PathParam("accountSid") final String accountSid,
            @PathParam("sid") final String sid,
            @Context SecurityContext sec) {
        return deleteGateway(accountSid, sid,ContextUtil.convert(sec));
    }

    @Path("/{sid}")
    @GET
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public Response getGatewayAsXml(@PathParam("accountSid") final String accountSid,
            @PathParam("sid") final String sid,
            @HeaderParam("Accept") String accept) {
        return getGateway(accountSid, sid, retrieveMediaType(accept));
    }

    @GET
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public Response getGatewaysList(@PathParam("accountSid") final String accountSid,
            @HeaderParam("Accept") String accept) {
        return getGateways(accountSid, retrieveMediaType(accept));
    }

    @POST
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public Response createGateway(@PathParam("accountSid") final String accountSid,
            final MultivaluedMap<String, String> data,
            @HeaderParam("Accept") String accept) {
        return putGateway(accountSid, data, retrieveMediaType(accept));
    }

    @Path("/{sid}")
    @POST
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public Response updateGatewayAsXmlPost(@PathParam("accountSid") final String accountSid,
            @PathParam("sid") final String sid,
            final MultivaluedMap<String, String> data,
            @HeaderParam("Accept") String accept) {
        return updateGateway(accountSid, sid, data, retrieveMediaType(accept));
    }

    @Path("/{sid}")
    @PUT
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public Response updateGatewayAsXmlPut(@PathParam("accountSid") final String accountSid,
            @PathParam("sid") final String sid,
            final MultivaluedMap<String, String> data,
            @HeaderParam("Accept") String accept) {
        return updateGateway(accountSid, sid, data, retrieveMediaType(accept));
    }
}
