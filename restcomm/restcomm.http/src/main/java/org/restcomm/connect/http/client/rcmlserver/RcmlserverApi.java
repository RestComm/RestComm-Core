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
package org.restcomm.connect.http.client.rcmlserver;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.log4j.Logger;
import org.restcomm.connect.commons.common.http.CustomHttpClientBuilder;
import org.restcomm.connect.commons.configuration.sets.MainConfigurationSet;
import org.restcomm.connect.commons.configuration.sets.RcmlserverConfigurationSet;
import org.restcomm.connect.commons.dao.Sid;
import org.restcomm.connect.commons.util.SecurityUtils;
import org.restcomm.connect.core.service.RestcommConnectServiceProvider;
import org.restcomm.connect.core.service.util.UriUtils;
import org.restcomm.connect.dao.entities.Account;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.concurrent.FutureCallback;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.restcomm.connect.dao.entities.Account.Status;

/**
 * Utility class that handles notification submission to rcmlserver (typically
 * RVD)
 *
 * @author otsakir@gmail.com - Orestis Tsakiridis
 */
public class RcmlserverApi {

    static final Logger logger = Logger.getLogger(RcmlserverApi.class.getName());

    enum NotificationType {
        accountClosed, accountSuspended,
        accountActivated, accountUninitialized,
        accountInactivated
    }

    private static final Map<Status,NotificationType> status2NotMap = new HashMap();
    static {
        status2NotMap.put(Status.CLOSED, NotificationType.accountClosed);
        status2NotMap.put(Status.ACTIVE, NotificationType.accountActivated);
        status2NotMap.put(Status.SUSPENDED, NotificationType.accountSuspended);
        status2NotMap.put(Status.INACTIVE, NotificationType.accountInactivated);
        status2NotMap.put(Status.UNINITIALIZED, NotificationType.accountUninitialized);
    }

    URI apiUrl;
    MainConfigurationSet mainConfig;
    RcmlserverConfigurationSet rcmlserverConfig;
    UriUtils uriUtils = RestcommConnectServiceProvider.getInstance().uriUtils();

    public RcmlserverApi(MainConfigurationSet mainConfig, RcmlserverConfigurationSet rcmlserverConfig, Sid accountSid) {
        try {
            // if there is no baseUrl configured we use the resolver to guess the location of the rcml server and the path
            if (StringUtils.isEmpty(rcmlserverConfig.getBaseUrl())) {
                // resolveWithBase() should be run lazily to work. Make sure this constructor is invoked after the JBoss connectors have been set up.
                apiUrl = uriUtils.resolve(new URI(rcmlserverConfig.getApiPath()), accountSid);
            } // if baseUrl has been configured, concat baseUrl and path to find the location of rcml server. No resolving here.
            else {
                String path = rcmlserverConfig.getApiPath();
                apiUrl = new URI(rcmlserverConfig.getBaseUrl() + (path != null ? path : ""));
            }
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
        this.rcmlserverConfig = rcmlserverConfig;
        this.mainConfig = mainConfig;
    }

    class RCMLCallback implements FutureCallback<HttpResponse> {

        @Override
        public void completed(HttpResponse t) {
            logger.debug("RVD notification sent");
        }

        @Override
        public void failed(Exception excptn) {
            logger.error("RVD notification failed", excptn);
        }

        @Override
        public void cancelled() {
            logger.debug("RVD notification cancelled");
        }

    }

    public void transmitNotifications(List<JsonObject> notifications, String notifierUsername, String notifierPassword) {
        String notificationUrl = apiUrl + "/notifications";
        HttpPost request = new HttpPost(notificationUrl);
        String authHeader;
        authHeader = SecurityUtils.buildBasicAuthHeader(notifierUsername, notifierPassword);
        request.setHeader("Authorization", authHeader);
        Gson gson = new Gson();
        String json = gson.toJson(notifications);
        request.setEntity(new StringEntity(json, ContentType.APPLICATION_JSON));
        Integer totalTimeout = rcmlserverConfig.getTimeout() + notifications.size() * rcmlserverConfig.getTimeoutPerNotification();
        CloseableHttpAsyncClient httpClient = CustomHttpClientBuilder.buildCloseableHttpAsyncClient(mainConfig);
        if (logger.isDebugEnabled()) {
            logger.debug("Will transmit a set of " +
                    notifications.size() + " "
                    + "notifications and wait at most for " +
                    totalTimeout);
        }
        HttpContext httpContext = new BasicHttpContext();
        httpContext.setAttribute(HttpClientContext.REQUEST_CONFIG, RequestConfig.custom().
                setConnectTimeout(totalTimeout).
                setSocketTimeout(totalTimeout).
                setConnectionRequestTimeout(totalTimeout).build());
        httpClient.execute(request, httpContext, new RCMLCallback());
    }

    public JsonObject buildAccountStatusNotification(Account account) {
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("type", status2NotMap.get(account.getStatus()).toString());
        jsonObject.addProperty("accountSid", account.getSid().toString());
        return jsonObject;
    }

}
