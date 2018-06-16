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
import java.util.List;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.PathSegment;
import javax.ws.rs.core.UriBuilder;
import javax.ws.rs.ext.Provider;
import org.apache.log4j.Logger;

/**
 * Fixes the accept header taking into accoutn convention on URL (.json).
 *
 * ".json" suffix will be removed before matching the actual REST endpoint
 *
 * @author
 */
@Provider
public class AcceptFilter implements ResourceFilter, ContainerRequestFilter {

    protected Logger logger = Logger.getLogger(AcceptFilter.class);

    private static final String JSON_EXTENSION = ".json";
    private static final String ACCEPT_HDR = "Accept";

    private URI reworkJSONPath(ContainerRequest cr) {
        List<PathSegment> pathSegments = cr.getPathSegments();
        UriBuilder baseUriBuilder = cr.getBaseUriBuilder();
        for (PathSegment seg : pathSegments) {
            String newSeg = seg.getPath();
            if (seg.getPath().endsWith(JSON_EXTENSION)) {
                int n = newSeg.length();
                newSeg = newSeg.substring(0, n - JSON_EXTENSION.length());
            }
            baseUriBuilder.path(newSeg);

        }
        MultivaluedMap<String, String> queryParameters = cr.getQueryParameters();
        for (String qParam : queryParameters.keySet()) {
            baseUriBuilder.queryParam(qParam, queryParameters.getFirst(qParam));
        }
        return baseUriBuilder.build();
    }

    @Override
    public ContainerRequest filter(ContainerRequest cr) {
        if (cr.getHeaderValue(ACCEPT_HDR) == null) {
            if (cr.getPath().contains(JSON_EXTENSION)) {
                cr.getRequestHeaders().add(ACCEPT_HDR, MediaType.APPLICATION_JSON);
            } else {
                //by default assume client is expecting XML
                cr.getRequestHeaders().add(ACCEPT_HDR, MediaType.APPLICATION_XML);
            }
        }

        //Do not apply for Profiles endpoint, as is not following .json convention
        if (cr.getPath().contains(JSON_EXTENSION) &&
                !cr.getPath().contains("Profiles")) {
            cr.getRequestHeaders().remove(ACCEPT_HDR);
            cr.getRequestHeaders().add(ACCEPT_HDR, MediaType.APPLICATION_JSON);
            URI reworkedUri = reworkJSONPath(cr);
            cr.setUris(cr.getBaseUri(), reworkedUri);
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
