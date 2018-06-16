/*
 * TeleStax, Open Source Cloud Communications
 * Copyright 2011-2013, Telestax Inc and individual contributors
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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.jersey.spi.resource.Singleton;
import com.thoughtworks.xstream.XStream;
import javax.annotation.PostConstruct;
import javax.servlet.ServletContext;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import static javax.ws.rs.core.MediaType.*;
import javax.ws.rs.core.Response;
import static javax.ws.rs.core.Response.ok;
import javax.ws.rs.core.SecurityContext;
import org.apache.commons.configuration.Configuration;
import org.apache.log4j.Logger;
import org.restcomm.connect.commons.Version;
import org.restcomm.connect.commons.VersionEntity;
import org.restcomm.connect.commons.annotations.concurrency.ThreadSafe;
import org.restcomm.connect.dao.AccountsDao;
import org.restcomm.connect.dao.DaoManager;
import org.restcomm.connect.dao.UsageDao;
import org.restcomm.connect.dao.entities.RestCommResponse;
import org.restcomm.connect.http.converter.RestCommResponseConverter;
import org.restcomm.connect.http.converter.VersionConverter;
import org.restcomm.connect.http.security.ContextUtil;
import org.restcomm.connect.identity.UserIdentityContext;

/**
 * @author <a href="mailto:gvagenas@gmail.com">gvagenas</a>
 *
 */
@Path("/Accounts/{accountSid}/Version")
@ThreadSafe
@Singleton
public class VersionEndpoint extends AbstractEndpoint {
    private static Logger logger = Logger.getLogger(VersionEndpoint.class);

    @Context
    private ServletContext context;
    private Configuration configuration;
    private UsageDao dao;
    private Gson gson;
    private XStream xstream;
    private AccountsDao accountsDao;




    @PostConstruct
    public void init() {
        final DaoManager storage = (DaoManager) context.getAttribute(DaoManager.class.getName());
        configuration = (Configuration) context.getAttribute(Configuration.class.getName());
        configuration = configuration.subset("runtime-settings");
        super.init(configuration);
        dao = storage.getUsageDao();
        accountsDao = storage.getAccountsDao();
        final VersionConverter converter = new VersionConverter(configuration);
        final GsonBuilder builder = new GsonBuilder();
        builder.registerTypeAdapter(VersionEntity.class, converter);
        builder.setPrettyPrinting();
        gson = builder.create();
        xstream = new XStream();
        xstream.alias("RestcommResponse", RestCommResponse.class);
        xstream.registerConverter(converter);
        xstream.registerConverter(new RestCommResponseConverter(configuration));
    }

    protected Response getVersion(final String accountSid,
            final MediaType mediaType,
            UserIdentityContext userIdentityContext
    ) {
        permissionEvaluator.secure(accountsDao.getAccount(accountSid),
                "RestComm:Read:Usage",
                userIdentityContext);

        VersionEntity versionEntity = Version.getVersionEntity();

        if (versionEntity != null) {
            if (APPLICATION_XML_TYPE == mediaType) {
                final RestCommResponse response = new RestCommResponse(versionEntity);
                return ok(xstream.toXML(response), APPLICATION_XML).build();
            } else if (APPLICATION_JSON_TYPE == mediaType) {
                Response response = ok(gson.toJson(versionEntity), APPLICATION_JSON).build();
                if(logger.isDebugEnabled()){
                    logger.debug("Supervisor endpoint response: "+gson.toJson(versionEntity));
                }
                return response;
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    @GET
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public Response getVersion(@PathParam("accountSid") final String accountSid,
            @HeaderParam("Accept") String accept,
            @Context SecurityContext sec) {
        return getVersion(accountSid, retrieveMediaType(accept),
                ContextUtil.convert(sec));
    }

}
