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

import akka.actor.ActorRef;
import akka.event.Logging;
import akka.event.LoggingAdapter;

import org.apache.commons.io.IOUtils;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.restcomm.connect.commons.common.http.CustomHttpClientBuilder;
import org.restcomm.connect.commons.configuration.RestcommConfiguration;
import org.restcomm.connect.commons.faulttolerance.RestcommUntypedActor;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.stream.XMLStreamException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;

/**
 * @author quintana.thomas@gmail.com (Thomas Quintana)
 */
public final class Downloader extends RestcommUntypedActor {

    public static final int LOGGED_RESPONSE_MAX_SIZE = 100;

    private CloseableHttpClient client = null;

    // Logger.
    private final LoggingAdapter logger = Logging.getLogger(getContext().system(), this);

    public Downloader () {
        super();
        client = (CloseableHttpClient) CustomHttpClientBuilder.buildDefaultClient(RestcommConfiguration.getInstance().getMain());
    }


    public HttpResponseDescriptor fetch (final HttpRequestDescriptor descriptor) throws IllegalArgumentException, IOException,
            URISyntaxException, XMLStreamException {
        int code = -1;
        HttpRequest request = null;
        CloseableHttpResponse response = null;
        HttpRequestDescriptor temp = descriptor;
        HttpResponseDescriptor responseDescriptor = null;
        HttpResponseDescriptor rawResponseDescriptor = null;
        try {
            do {
                request = request(temp);
                //FIXME:should we externalize RVD encoding default?
                request.setHeader("http.protocol.content-charset", "UTF-8");
                if (descriptor.getTimeout() > 0){
                    HttpContext httpContext = new BasicHttpContext();
                    httpContext.setAttribute(HttpClientContext.REQUEST_CONFIG, RequestConfig.custom().
                    setConnectTimeout(descriptor.getTimeout()).
                    setSocketTimeout(descriptor.getTimeout()).
                    setConnectionRequestTimeout(descriptor.getTimeout()).build());
                    response = client.execute((HttpUriRequest) request, httpContext);
                } else {
                    response = client.execute((HttpUriRequest) request);
                }
                code = response.getStatusLine().getStatusCode();
                if (isRedirect(code)) {
                    final Header header = response.getFirstHeader(HttpHeaders.LOCATION);
                    if (header != null) {
                        final String location = header.getValue();
                        final URI uri = URI.create(location);
                        temp = new HttpRequestDescriptor(uri, temp.getMethod(), temp.getParameters());
                        continue;
                    } else {
                        break;
                    }
                }
//                HttpResponseDescriptor httpResponseDescriptor = response(request, response);
                rawResponseDescriptor = response(request, response);
                responseDescriptor = validateXML(rawResponseDescriptor);
            } while (isRedirect(code));
            if (isHttpError(code)) {
                // TODO - usually this part of code is not reached. Error codes are part of error responses that do not pass validateXML above and an exception is thrown. We need to re-thing this
                String requestUrl = request.getRequestLine().getUri();
                String errorReason = response.getStatusLine().getReasonPhrase();
                String httpErrorMessage = String.format(
                        "Problem while fetching http resource: %s \n Http status code: %d \n Http status message: %s", requestUrl,
                        code, errorReason);
                logger.warning(httpErrorMessage);
            }
        } catch (Exception e) {
            logger.warning("Problem while trying to download RCML from {}, exception: {}", request.getRequestLine(), e);
            String statusInfo = "n/a";
            String responseInfo = "n/a";
            if (response != null) {
                // Build additional information to log. Include http status, url and a small fragment of the response.
                statusInfo = response.getStatusLine().toString();
                if (rawResponseDescriptor != null) {
                    int truncatedSize = (int) Math.min(rawResponseDescriptor.getContentLength(), LOGGED_RESPONSE_MAX_SIZE);
                    if (rawResponseDescriptor.getContentAsString() != null) {
                        responseInfo = String.format("%s %s", rawResponseDescriptor.getContentAsString().substring(0, truncatedSize), (rawResponseDescriptor.getContentLength() < LOGGED_RESPONSE_MAX_SIZE ? "" : "..."));
                    }
                }
            }
            logger.warning(String.format("Problem while trying to download RCML. URL: %s, Status: %s, Response: %s ", request.getRequestLine(), statusInfo, responseInfo));
            throw e; // re-throw
        } finally {
            if (response != null) {
                response.close();
            }
        }
        return responseDescriptor;
    }

    private boolean isRedirect (final int code) {
        return HttpStatus.SC_MOVED_PERMANENTLY == code || HttpStatus.SC_MOVED_TEMPORARILY == code
                || HttpStatus.SC_SEE_OTHER == code || HttpStatus.SC_TEMPORARY_REDIRECT == code;
    }

    private boolean isHttpError (final int code) {
        return (code >= 400);
    }

    private HttpResponseDescriptor validateXML (final HttpResponseDescriptor descriptor) throws XMLStreamException {
        if (descriptor.getContentLength() > 0) {
            try {
                // parse an XML document into a DOM tree
                String xml = descriptor.getContentAsString().trim().replaceAll("&([^;]+(?!(?:\\w|;)))", "&amp;$1");
                DocumentBuilder parser = DocumentBuilderFactory.newInstance().newDocumentBuilder();
                //FIXME:should we externalize RVD encoding default?
                parser.parse(new InputSource(new ByteArrayInputStream(xml.getBytes("utf-8"))));
                return descriptor;
            } catch (final Exception e) {
                throw new XMLStreamException("Error parsing the RCML:" + e);
            }
        }
        return descriptor;
    }

    @Override
    public void onReceive (final Object message) throws Exception {
        final Class<?> klass = message.getClass();
        final ActorRef self = self();
        final ActorRef sender = sender();
        if (HttpRequestDescriptor.class.equals(klass)) {
            final HttpRequestDescriptor request = (HttpRequestDescriptor) message;
            if (logger.isDebugEnabled()) {
                logger.debug("New HttpRequestDescriptor, method: " + request.getMethod() + " URI: " + request.getUri() + " parameters: " + request.getParametersAsString());
            }
            DownloaderResponse response = null;
            try {
                response = new DownloaderResponse(fetch(request));
            } catch (final Exception exception) {
                response = new DownloaderResponse(exception, "Problem while trying to download RCML");
            }
            if (sender != null && !sender.isTerminated()) {
                sender.tell(response, self);
            } else {
                if (logger.isInfoEnabled()) {
                    logger.info("DownloaderResponse wont be send because sender is :" + (sender.isTerminated() ? "terminated" : "null"));
                }
            }
        }
    }

    public HttpUriRequest request (final HttpRequestDescriptor descriptor) throws IllegalArgumentException, URISyntaxException,
            UnsupportedEncodingException {
        final URI uri = descriptor.getUri();
        final String method = descriptor.getMethod();
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
            return new HttpGet(result);
        } else if ("POST".equalsIgnoreCase(method)) {
            final List<NameValuePair> parameters = descriptor.getParameters();
            final HttpPost post = new HttpPost(uri);
            //FIXME:should we externalize RVD encoding default?
            post.setEntity(new UrlEncodedFormEntity(parameters, "UTF-8"));
            return post;
        } else {
            throw new IllegalArgumentException(method + " is not a supported downloader method.");
        }
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
                //FIXME:should we externalize RVD encoding default?
                String encodingValue = "UTF-8";
                if (contentEncoding != null && !contentEncoding.getValue().isEmpty()) {
                    encodingValue = contentEncoding.getValue();
                    builder.setContentEncoding(encodingValue);
                }
                final Header contentType = entity.getContentType();
                if (contentType != null) {
                    builder.setContentType(contentType.getValue());
                }
                builder.setContent(IOUtils.toString(stream, encodingValue));
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
            logger.debug("Downloader at post stop");
        }
        super.postStop();
    }
}
