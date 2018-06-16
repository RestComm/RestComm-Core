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
package org.restcomm.connect.http.filters;

import com.sun.jersey.spi.container.ContainerRequest;
import com.sun.jersey.spi.container.ContainerRequestFilter;
import com.sun.jersey.spi.container.ContainerResponseFilter;
import com.sun.jersey.spi.container.ResourceFilter;
import java.net.URI;
import javax.ws.rs.ext.Provider;
import org.apache.log4j.Logger;
import org.restcomm.connect.commons.util.UriUtils;

/**
 * Ensures UriInfo builders build proper link scheme depending on container
 * connector (http/https)
 *
 * This will apply to any jaxrs endpoint trying to build uris later.
 *
 * UriUtils is used to discover connectors in runtime, and check the scheme.
 *
 */
@Provider
public class SchemeRewriteFilter implements ResourceFilter, ContainerRequestFilter {

    protected Logger logger = Logger.getLogger(SchemeRewriteFilter.class);

    private static final String HTTPS_SCHEME = "https";


    @Override
    public ContainerRequest filter(ContainerRequest cr) {
        String connectorScheme = UriUtils.getHttpConnector().getScheme();
        if (connectorScheme.equalsIgnoreCase(HTTPS_SCHEME)) {
            URI newUri = cr.getRequestUriBuilder().scheme(HTTPS_SCHEME).build();
            URI baseUri = cr.getBaseUriBuilder().scheme(HTTPS_SCHEME).build();
            cr.setUris(baseUri, newUri);
            if (logger.isDebugEnabled()) {
                logger.debug("Detected https connector, rewriting URIs:" +
                        newUri +
                        "," +
                        baseUri);
            }
        }
        return cr;
    }

    @Override
    public ContainerRequestFilter getRequestFilter() {
        return this;
    }

    @Override
    public ContainerResponseFilter getResponseFilter() {
        return null;
    }

}
