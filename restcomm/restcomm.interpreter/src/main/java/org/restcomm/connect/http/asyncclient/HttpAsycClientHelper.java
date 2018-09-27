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
package org.restcomm.connect.http.asyncclient;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;

import javax.xml.stream.XMLStreamException;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.restcomm.connect.commons.common.http.CustomHttpClientBuilder;
import org.restcomm.connect.commons.configuration.RestcommConfiguration;
import org.restcomm.connect.commons.faulttolerance.RestcommUntypedActor;
import org.restcomm.connect.commons.util.StringUtils;
import org.restcomm.connect.http.client.DownloaderResponse;
import org.restcomm.connect.http.client.HttpRequestDescriptor;
import org.restcomm.connect.http.client.HttpResponseDescriptor;

import akka.actor.ActorRef;
import akka.event.Logging;
import akka.event.LoggingAdapter;

/**
 * @author maria.farooq
 */
public final class HttpAsycClientHelper extends RestcommUntypedActor {

    public static final int LOGGED_RESPONSE_MAX_SIZE = 100;

    private CloseableHttpAsyncClient client = null;

    // Logger.
    private final LoggingAdapter logger = Logging.getLogger(getContext().system(), this);

    public HttpAsycClientHelper () {
        super();
        client = (CloseableHttpAsyncClient) CustomHttpClientBuilder.buildCloseableHttpAsyncClient(RestcommConfiguration.getInstance().getMain());
    }

    @Override
    public void onReceive (final Object message) throws Exception {
        final Class<?> klass = message.getClass();
        final ActorRef sender = sender();
        if (logger.isInfoEnabled()) {
            logger.info(" ********** HttpAsycClientHelper " + self().path() + " Sender: " + sender);
            logger.info(" ********** HttpAsycClientHelper " + self().path() + " Processing Message: " + klass.getName());
        }
        if (HttpRequestDescriptor.class.equals(klass)) {
            final HttpRequestDescriptor request = (HttpRequestDescriptor) message;
            if (logger.isDebugEnabled()) {
                logger.debug("New HttpRequestDescriptor, method: " + request.getMethod() + " URI: " + request.getUri() + " parameters: " + request.getParametersAsString());
            }
            try {
                execute(request, sender);
            } catch (final Exception exception) {
                DownloaderResponse response = new DownloaderResponse(exception, "Problem while trying to exceute request");
                sender.tell(response, self());
            }
        }
    }


    public void execute (final HttpRequestDescriptor descriptor, ActorRef sender) throws IllegalArgumentException, IOException,
            URISyntaxException, XMLStreamException {
        HttpUriRequest request = null;
        HttpRequestDescriptor temp = descriptor;
        try {

            request = request(temp);
            request.setHeader("http.protocol.content-charset", "UTF-8");
            if (descriptor.getTimeout() > 0){
                HttpContext httpContext = new BasicHttpContext();
                httpContext.setAttribute(HttpClientContext.REQUEST_CONFIG, RequestConfig.custom().
                setConnectTimeout(descriptor.getTimeout()).
                setSocketTimeout(descriptor.getTimeout()).
                setConnectionRequestTimeout(descriptor.getTimeout()).build());
                client.execute(request, httpContext, getFutureCallback(request, sender));
            } else {
                client.execute(request, getFutureCallback(request, sender));
            }
        } catch (Exception e) {
            logger.error("Problem while trying to execute http request {}, exception: {}", request.getRequestLine(), e);
            throw e;
        }
    }

    private FutureCallback<HttpResponse> getFutureCallback(final HttpUriRequest request, final ActorRef sender){
        return new FutureCallback<HttpResponse>(){

            @Override
            public void completed(HttpResponse result) {
                if (logger.isDebugEnabled()) {
                    logger.debug(String.format("success on execution of http request. result %s", result));
                }
                DownloaderResponse response = null;
                try {
                    response = new DownloaderResponse(response(request, result));
                } catch (IOException e) {
                    logger.error("Exception while parsing response", e);
                    response = new DownloaderResponse(e, "Exception while parsing response");
                }
                sender.tell(response, self());
            }

            @Override
            public void failed(Exception ex) {
                logger.error("got failure on executing http request {}, exception: {}", request.getRequestLine(), ex);
                DownloaderResponse response = new DownloaderResponse(ex, "got failure on executing http request");
                sender.tell(response, self());
            }

            @Override
            public void cancelled() {
                logger.warning("got cancellation on executing http request {}", request.getRequestLine());
                DownloaderResponse response = new DownloaderResponse(new Exception("got cancellation on executing http request"), "got cancellation on executing http request");
                sender.tell(response, self());
            }};
    }

    public HttpUriRequest request (final HttpRequestDescriptor descriptor) throws IllegalArgumentException, URISyntaxException,
            UnsupportedEncodingException {
        final URI uri = descriptor.getUri();
        final String method = descriptor.getMethod();
        HttpUriRequest httpUriRequest = null;
        if ("GET".equalsIgnoreCase(method)) {
            final String query = descriptor.getParametersAsString();
            URI result = null;
            if (query != null && !query.isEmpty()) {
                result = new URIBuilder()
                        .setScheme(uri.getScheme())
                        .setHost(uri.getHost())
                        .setPort(uri.getPort())
                        .setPath(uri.getPath())
                        .setQuery(query)
                        .build();
            } else {
                result = uri;
            }
            httpUriRequest = new HttpGet(result);
        } else if ("POST".equalsIgnoreCase(method)) {
            final List<NameValuePair> parameters = descriptor.getParameters();
            final HttpPost post = new HttpPost(uri);
            post.setEntity(new UrlEncodedFormEntity(parameters, "UTF-8"));
            httpUriRequest = post;
        } else {
            throw new IllegalArgumentException(method + " is not a supported downloader method.");
        }
        if(descriptor.getHeaders() != null && descriptor.getHeaders().length>0){
            httpUriRequest.setHeaders(descriptor.getHeaders());
        }
        return httpUriRequest;
    }

    private HttpResponseDescriptor response (final HttpRequest request, final HttpResponse response) throws IOException {
        final HttpResponseDescriptor.Builder builder = HttpResponseDescriptor.builder();
        final URI uri = URI.create(request.getRequestLine().getUri());
        builder.setURI(uri);
        builder.setStatusCode(response.getStatusLine().getStatusCode());
        builder.setStatusDescription(response.getStatusLine().getReasonPhrase());
        builder.setHeaders(response.getAllHeaders());
        final HttpEntity entity = response.getEntity();
        if (entity != null) {
            InputStream stream = entity.getContent();
            try {
                final Header contentEncoding = entity.getContentEncoding();
                if (contentEncoding != null) {
                    builder.setContentEncoding(contentEncoding.getValue());
                }
                final Header contentType = entity.getContentType();
                if (contentType != null) {
                    builder.setContentType(contentType.getValue());
                }
                builder.setContent(StringUtils.toString(stream));
                builder.setContentLength(entity.getContentLength());
                builder.setIsChunked(entity.isChunked());
            } finally {
                stream.close();
            }
        }
        return builder.build();
    }

    @Override
    public void postStop () {
        if (logger.isDebugEnabled()) {
            logger.debug("HttpAsycClientHelper at post stop");
        }
        getContext().stop(self());
        super.postStop();
    }
}
