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
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.annotation.PostConstruct;
import javax.annotation.security.RolesAllowed;
import javax.servlet.ServletContext;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON_TYPE;
import static javax.ws.rs.core.MediaType.APPLICATION_XML;
import static javax.ws.rs.core.MediaType.APPLICATION_XML_TYPE;
import javax.ws.rs.core.Response;
import static javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;
import static javax.ws.rs.core.Response.ok;
import static javax.ws.rs.core.Response.status;
import org.apache.commons.configuration.Configuration;
import org.restcomm.connect.commons.annotations.concurrency.ThreadSafe;
import org.restcomm.connect.commons.dao.Sid;
import org.restcomm.connect.dao.entities.RestCommResponse;
import org.restcomm.connect.http.converter.RestCommResponseConverter;
import static org.restcomm.connect.http.security.AccountPrincipal.SUPER_ADMIN_ROLE;
import org.restcomm.connect.telephony.api.GetActiveProxy;
import org.restcomm.connect.telephony.api.GetProxies;
import org.restcomm.connect.telephony.api.SwitchProxy;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;

/**
 * @author <a href="mailto:gvagenas@gmail.com">gvagenas</a>
 *
 */
@Path("/Accounts/{accountSid}/OutboundProxy")
@ThreadSafe
@RolesAllowed(SUPER_ADMIN_ROLE)
@Singleton
public class OutboundProxyEndpoint extends AbstractEndpoint {

    @Context
    protected ServletContext context;
    protected Configuration configuration;
    private ActorRef callManager;
    private Gson gson;
    private GsonBuilder builder;
    private XStream xstream;

    public OutboundProxyEndpoint() {
        super();
    }

    @PostConstruct
    public void init() {
        configuration = (Configuration) context.getAttribute(Configuration.class.getName());
        configuration = configuration.subset("runtime-settings");
        callManager = (ActorRef) context.getAttribute("org.restcomm.connect.telephony.CallManager");
        super.init(configuration);
        builder = new GsonBuilder();
        builder.setPrettyPrinting();
        gson = builder.create();
        xstream = new XStream();
        xstream.alias("RestcommResponse", RestCommResponse.class);
        xstream.registerConverter(new RestCommResponseConverter(configuration));
    }

    protected Response getProxies(final String accountSid, final MediaType responseType) {

        Map<String, String> proxies;

        final Timeout expires = new Timeout(Duration.create(60, TimeUnit.SECONDS));
        try {
            Future<Object> future = (Future<Object>) ask(callManager, new GetProxies(), expires);
            proxies = (Map<String, String>) Await.result(future, Duration.create(10, TimeUnit.SECONDS));
        } catch (Exception exception) {
            return status(INTERNAL_SERVER_ERROR).entity(exception.getMessage()).build();
        }

        if (APPLICATION_XML_TYPE.equals(responseType)) {
            final RestCommResponse response = new RestCommResponse(proxies);
            return ok(xstream.toXML(response), APPLICATION_XML).build();
        } else if (APPLICATION_JSON_TYPE.equals(responseType)) {
            return ok(gson.toJson(proxies), APPLICATION_JSON).build();
        } else {
            return null;
        }
    }

    protected Response switchProxy(final String accountSid, final MediaType responseType) {

        Map<String, String> proxyAfterSwitch;

        final Timeout expires = new Timeout(Duration.create(60, TimeUnit.SECONDS));
        try {
            Future<Object> future = (Future<Object>) ask(callManager, new SwitchProxy(new Sid(accountSid)), expires);
            proxyAfterSwitch = (Map<String, String>) Await.result(future, Duration.create(10, TimeUnit.SECONDS));
        } catch (Exception exception) {
            return status(INTERNAL_SERVER_ERROR).entity(exception.getMessage()).build();
        }

        if (APPLICATION_XML_TYPE.equals(responseType)) {
            final RestCommResponse response = new RestCommResponse(proxyAfterSwitch);
            return ok(xstream.toXML(response), APPLICATION_XML).build();
        } else if (APPLICATION_JSON_TYPE.equals(responseType)) {
            return ok(gson.toJson(proxyAfterSwitch), APPLICATION_JSON).build();
        } else {
            return null;
        }
    }

    protected Response getActiveProxy(final String accountSid, final MediaType responseType) {

        Map<String, String> activeProxy;

        final Timeout expires = new Timeout(Duration.create(60, TimeUnit.SECONDS));
        try {
            Future<Object> future = (Future<Object>) ask(callManager, new GetActiveProxy(), expires);
            activeProxy = (Map<String, String>) Await.result(future, Duration.create(10, TimeUnit.SECONDS));
        } catch (Exception exception) {
            return status(INTERNAL_SERVER_ERROR).entity(exception.getMessage()).build();
        }

        if (APPLICATION_XML_TYPE.equals(responseType)) {
            final RestCommResponse response = new RestCommResponse(activeProxy);
            return ok(xstream.toXML(response), APPLICATION_XML).build();
        } else if (APPLICATION_JSON_TYPE.equals(responseType)) {
            return ok(gson.toJson(activeProxy), APPLICATION_JSON).build();
        } else {
            return null;
        }
    }

    @GET
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public Response getProxies(@PathParam("accountSid") final String accountSid,
            @HeaderParam("Accept") String accept) {
        return getProxies(accountSid, retrieveMediaType(accept));
    }

    @GET
    @Path("/switchProxy")
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public Response switchProxy(@PathParam("accountSid") final String accountSid,
            @HeaderParam("Accept") String accept) {
        return switchProxy(accountSid, retrieveMediaType(accept));
    }

    @GET
    @Path("/getActiveProxy")
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public Response getActiveProxy(@PathParam("accountSid") final String accountSid,
            @HeaderParam("Accept") String accept) {
        return getActiveProxy(accountSid, retrieveMediaType(accept));
    }
}
