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
package org.restcomm.connect.commons.configuration.sets.impl;

import org.apache.commons.lang.StringUtils;
import org.restcomm.connect.commons.annotations.concurrency.Immutable;
import org.restcomm.connect.commons.common.http.SslMode;
import org.restcomm.connect.commons.configuration.sets.MainConfigurationSet;
import org.restcomm.connect.commons.configuration.sources.ConfigurationSource;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;

/**
 * Provides a typed interface to a set of configuration options retrieved from a
 * configuration source.
 *
 * To add a new option in this set define its name as static fields and then
 * initialize, validate it in the constructor.
 *
 * @author orestis.tsakiridis@telestax.com (Orestis Tsakiridis)
 *
 */
@Immutable
public class MainConfigurationSetImpl extends ConfigurationSet implements MainConfigurationSet {

    private static final String SSL_MODE_KEY = "http-client.ssl-mode";
    private static final String HTTP_RESPONSE_TIMEOUT = "http-client.response-timeout";
    private static final String HTTP_CONNECTION_REQUEST_TIMEOUT = "http-client.connection-request-timeout";
    private static final String HTTP_MAX_CONN_TOTAL = "http-client.max-conn-total";
    private static final String HTTP_MAX_CONN_PER_ROUTE = "http-client.max-conn-per-route";
    private static final String HTTP_CONNECTION_TIME_TO_LIVE = "http-client.connection-time-to-live";
    private static final String HTTP_ROUTES_HOST = "http-client.routes-host";
    private static final String HTTP_ROUTES_PORT = "http-client.routes-port";
    private static final String HTTP_ROUTES_CONN = "http-client.routes-conn";
    private static final String CONFERENCE_TIMEOUT_KEY = "runtime-settings.conference-timeout";
    private static final String CLEAR_TEXT_PASSWORD_ALGORITHM = "cleartext";
    private static final String DEFAULT_CLIENT_PASSWORD = "MD5";
    private static final String DEFAULT_CLIENT_QOP = "auth";
    private static final long CONFERENCE_TIMEOUT_DEFAULT = 14400; //4 hours in seconds
    private static final SslMode SSL_MODE_DEFAULT = SslMode.strict;
    private SslMode sslMode;
    private int responseTimeout;
    private Integer connectionRequestTimeout;
    private Integer defaultHttpMaxConns;
    private Integer defaultHttpMaxConnsPerRoute;
    private Integer defaultHttpTTL;
    private Map<InetSocketAddress, Integer> defaultHttpRoutes = new HashMap();
    private static final String USE_HOSTNAME_TO_RESOLVE_RELATIVE_URL_KEY = "http-client.use-hostname-to-resolve-relative-url";
    private static final String HOSTNAME_TO_USE_FOR_RELATIVE_URLS_KEY = "http-client.hostname";
    private static final boolean RESOLVE_RELATIVE_URL_WITH_HOSTNAME_DEFAULT = true;
    private boolean useHostnameToResolveRelativeUrls;
    private String hostname;
    private String instanceId;
    private String apiVersion;
    private int recordingMaxDelay;
    private long conferenceTimeout;
    private String recordingPath;

    public static final String BYPASS_LB_FOR_CLIENTS = "bypass-lb-for-clients";
    private boolean bypassLbForClients = false;
    private String clientAlgorithm = DEFAULT_CLIENT_PASSWORD;
    private String clientQOP = DEFAULT_CLIENT_QOP;

    public MainConfigurationSetImpl(ConfigurationSource source) {
        super(source);
        SslMode sslMode;
        boolean resolveRelativeUrlWithHostname;
        String resolveRelativeUrlHostname;
        boolean bypassLb = false;

        try {
            responseTimeout = Integer.parseInt(source.getProperty(HTTP_RESPONSE_TIMEOUT, "5000"));
        } catch (Exception e) {
            throw new RuntimeException("Error initializing '" + HTTP_RESPONSE_TIMEOUT + "' configuration setting", e);
        }
        try {
            connectionRequestTimeout = Integer.parseInt(source.getProperty(HTTP_CONNECTION_REQUEST_TIMEOUT,String.valueOf(responseTimeout)));
        } catch (Exception e) {
            throw new RuntimeException("Error initializing '" + HTTP_RESPONSE_TIMEOUT + "' configuration setting", e);
        }
        try {
            defaultHttpMaxConns = Integer.parseInt(source.getProperty(HTTP_MAX_CONN_TOTAL, "2000"));
        } catch (Exception e) {
            throw new RuntimeException("Error initializing '" + HTTP_MAX_CONN_TOTAL + "' configuration setting", e);
        }
        try {
            defaultHttpMaxConnsPerRoute = Integer.parseInt(source.getProperty(HTTP_MAX_CONN_PER_ROUTE, "100"));
        } catch (Exception e) {
            throw new RuntimeException("Error initializing '" + HTTP_MAX_CONN_PER_ROUTE + "' configuration setting", e);
        }
        try {
            defaultHttpTTL = Integer.parseInt(source.getProperty(HTTP_CONNECTION_TIME_TO_LIVE, "30000"));
        } catch (Exception e) {
            throw new RuntimeException("Error initializing '" + HTTP_CONNECTION_TIME_TO_LIVE + "' configuration setting", e);
        }

        try {
            String delimiter = ",";
            String routesHostProp = source.getProperty(HTTP_ROUTES_HOST, "");
            if (!routesHostProp.isEmpty()) {
                String[] routesHostList = routesHostProp.split(delimiter);
                String routesPortProp = source.getProperty(HTTP_ROUTES_PORT, "");
                String[] routesPortList = routesPortProp.split(delimiter);
                String routesConnProp = source.getProperty(HTTP_ROUTES_CONN, "");
                String[] routesConnList = routesConnProp.split(delimiter);
                for (int i = 0; i < routesHostList.length; i++) {
                    Integer port = Integer.valueOf(routesPortList[i]);
                    Integer conn = Integer.valueOf(routesConnList[i]);
                    InetSocketAddress addr = new InetSocketAddress(routesHostList[i], port);
                    defaultHttpRoutes.put(addr, conn);
                }
            }

        } catch (Throwable e) {//to catch array index out of bounds
            throw new RuntimeException("Error initializing '" + HTTP_ROUTES_CONN + "' configuration setting", e);
        }

        // http-client.ssl-mode
        try {
            sslMode = SSL_MODE_DEFAULT;
            String sslModeRaw = source.getProperty(SSL_MODE_KEY);
            if (!StringUtils.isEmpty(sslModeRaw)) {
                sslMode = SslMode.valueOf(sslModeRaw);
            }
        } catch (Exception e) {
            throw new RuntimeException("Error initializing '" + SSL_MODE_KEY + "' configuration setting", e);
        }
        this.sslMode = sslMode;

        // http-client.hostname
        // http-client.use-hostname-to-resolve-relative-url
        try {
            resolveRelativeUrlWithHostname = RESOLVE_RELATIVE_URL_WITH_HOSTNAME_DEFAULT;
            resolveRelativeUrlWithHostname = Boolean.valueOf(source.getProperty(USE_HOSTNAME_TO_RESOLVE_RELATIVE_URL_KEY));
            resolveRelativeUrlHostname = source.getProperty("http-client.hostname");
            bypassLb = Boolean.valueOf(source.getProperty(BYPASS_LB_FOR_CLIENTS));
        } catch (Exception e) {
            throw new RuntimeException("Error initializing '" + USE_HOSTNAME_TO_RESOLVE_RELATIVE_URL_KEY + "' configuration setting", e);
        }
        this.useHostnameToResolveRelativeUrls = resolveRelativeUrlWithHostname;
        this.hostname = resolveRelativeUrlHostname;
        bypassLbForClients = bypassLb;
        apiVersion = source.getProperty("runtime-settings.api-version");

        this.recordingMaxDelay = Integer.parseInt(source.getProperty("runtime-settings.recording-max-delay", "2000"));
        try{
            this.conferenceTimeout = Long.parseLong(source.getProperty(CONFERENCE_TIMEOUT_KEY, ""+CONFERENCE_TIMEOUT_DEFAULT));
        }catch(NumberFormatException nfe){
            this.conferenceTimeout = CONFERENCE_TIMEOUT_DEFAULT;
        }

        recordingPath = source.getProperty("runtime-settings.recordings-path");

        clientAlgorithm = source.getProperty("runtime-settings.client-algorithm", DEFAULT_CLIENT_PASSWORD);
        clientQOP = source.getProperty("runtime-settings.client-qop", DEFAULT_CLIENT_QOP);
    }

    public MainConfigurationSetImpl(SslMode sslMode, int responseTimeout, boolean useHostnameToResolveRelativeUrls, String hostname, String instanceId, boolean bypassLbForClients) {
        super(null);
        this.sslMode = sslMode;
        this.responseTimeout = responseTimeout;
        this.useHostnameToResolveRelativeUrls = useHostnameToResolveRelativeUrls;
        this.hostname = hostname;
        this.instanceId = instanceId;
        this.bypassLbForClients = bypassLbForClients;
    }

    @Override
    public SslMode getSslMode() {
        return sslMode;
    }

    @Override
    public int getResponseTimeout() {
        return responseTimeout;
    }

    @Override
    public boolean isUseHostnameToResolveRelativeUrls() {
        return useHostnameToResolveRelativeUrls;
    }

    @Override
    public String getHostname() {
        return hostname;
    }

    @Override
    public boolean getBypassLbForClients() {
        return bypassLbForClients;
    }

    @Override
    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    @Override
    public String getInstanceId() {
        return this.instanceId;
    }

    public void setSslMode(SslMode sslMode) {
        this.sslMode = sslMode;
    }

    public void setResponseTimeout(int responseTimeout) {
        this.responseTimeout = responseTimeout;
    }

    public void setUseHostnameToResolveRelativeUrls(boolean useHostnameToResolveRelativeUrls) {
        this.useHostnameToResolveRelativeUrls = useHostnameToResolveRelativeUrls;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public void setBypassLbForClients(boolean bypassLbForClients) {
        this.bypassLbForClients = bypassLbForClients;
    }

    public String getApiVersion() {
        return apiVersion;
    }

    @Override
    public int getRecordingMaxDelay() {
        return recordingMaxDelay;
    }

    @Override
    public Integer getDefaultHttpMaxConns() {
        return defaultHttpMaxConns;
    }

    @Override
    public Integer getDefaultHttpMaxConnsPerRoute() {
        return defaultHttpMaxConnsPerRoute;
    }

    @Override
    public Integer getDefaultHttpTTL() {
        return defaultHttpTTL;
    }

    @Override
    public Map<InetSocketAddress, Integer> getDefaultHttpRoutes() {
        return defaultHttpRoutes;
    }

    @Override
    public Integer getDefaultHttpConnectionRequestTimeout() {
        return connectionRequestTimeout;
    }

    @Override
    public long getConferenceTimeout() {
        return conferenceTimeout;
    }

    @Override
    public void setConferenceTimeout(long conferenceTimeout) {
        this.conferenceTimeout = conferenceTimeout;
    }

    @Override
    public String getClientAlgorithm() {
        return clientAlgorithm;
    }

    @Override
    public String getClientQOP() {
        return clientQOP;
    }

    @Override
    public String getClearTextPasswordAlgorithm() { return CLEAR_TEXT_PASSWORD_ALGORITHM; }

    @Override
    public String getRecordingPath () {
        return recordingPath;
    }
}
