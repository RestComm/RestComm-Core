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
package org.restcomm.connect.http.client;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.Header;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.client.utils.URLEncodedUtils;
import org.restcomm.connect.commons.annotations.concurrency.Immutable;

/**
 * @author quintana.thomas@gmail.com (Thomas Quintana)
 */
@Immutable
public final class HttpRequestDescriptor {
    private final URI uri;
    private final String method;
    private final List<NameValuePair> parameters;
    private final Integer timeout;
    private final Header[] headers;

    public HttpRequestDescriptor(final URI uri, final String method,
            final List<NameValuePair> parameters,
            final Integer timeout, final Header[] headers) {
        super();
        this.timeout = timeout;
        this.uri = base(uri);
        this.method = method;
        if (parameters != null) {
            this.parameters = parameters;
        } else {
            this.parameters = new ArrayList<NameValuePair>();
        }
        final String query = uri.getQuery();
        if (query != null) {
            //FIXME:should we externalize RVD encoding default?
            final List<NameValuePair> other = URLEncodedUtils.parse(uri, "UTF-8");
            parameters.addAll(other);
        }
        this.headers = headers;

    }

    public HttpRequestDescriptor(final URI uri, final String method,
            final List<NameValuePair> parameters,
            final Integer timeout){
        this(uri, method, parameters, timeout, null);
    }

    public HttpRequestDescriptor(final URI uri, final String method, final List<NameValuePair> parameters) {
        this(uri,method,parameters, -1);
    }

    public HttpRequestDescriptor(final URI uri, final String method) throws UnsupportedEncodingException, URISyntaxException {
        this(uri, method, null);
    }

    private URI base(final URI uri) {
        try {
            URIBuilder uriBuilder = new URIBuilder();
            uriBuilder.setScheme(uri.getScheme());
            uriBuilder.setHost(uri.getHost());
            uriBuilder.setPort(uri.getPort());
            uriBuilder.setPath(uri.getPath());

            if(uri.getUserInfo() != null){
                uriBuilder.setUserInfo(uri.getUserInfo());
            }
            return uriBuilder.build();
        } catch (final URISyntaxException ignored) {
            // This should never happen since we are using a valid URI to construct ours.
            return null;
        }
    }

    public String getMethod() {
        return method;
    }

    public List<NameValuePair> getParameters() {
        return parameters;
    }

    public String getParametersAsString() {
        //FIXME:should we externalize RVD encoding default?
        return URLEncodedUtils.format(parameters, "UTF-8");
    }

    public URI getUri() {
        return uri;
    }

    public Integer getTimeout() {
        return timeout;
    }

    public Header[] getHeaders() {
        return headers;
    }

}
