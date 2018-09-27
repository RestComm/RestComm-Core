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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.jersey.spi.resource.Singleton;
import com.thoughtworks.xstream.XStream;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
import static javax.ws.rs.core.Response.*;
import static javax.ws.rs.core.Response.Status.*;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;
import org.apache.commons.configuration.Configuration;
import org.restcomm.connect.commons.annotations.concurrency.ThreadSafe;
import org.restcomm.connect.commons.configuration.RestcommConfiguration;
import org.restcomm.connect.commons.dao.Sid;
import org.restcomm.connect.dao.DaoManager;
import org.restcomm.connect.dao.NotificationsDao;
import org.restcomm.connect.dao.common.Sorting;
import org.restcomm.connect.dao.entities.Account;
import org.restcomm.connect.dao.entities.Notification;
import org.restcomm.connect.dao.entities.NotificationFilter;
import org.restcomm.connect.dao.entities.NotificationList;
import org.restcomm.connect.dao.entities.RestCommResponse;
import org.restcomm.connect.http.converter.NotificationConverter;
import org.restcomm.connect.http.converter.NotificationListConverter;
import org.restcomm.connect.http.converter.RestCommResponseConverter;
import org.restcomm.connect.http.security.ContextUtil;
import org.restcomm.connect.http.security.PermissionEvaluator.SecuredType;
import org.restcomm.connect.identity.UserIdentityContext;

/**
 * @author quintana.thomas@gmail.com (Thomas Quintana)
 */
@Path("/Accounts/{accountSid}/Notifications")
@ThreadSafe
@Singleton
public class NotificationsEndpoint extends AbstractEndpoint {
    private static final String SORTING_URL_PARAM_DATE_CREATED = "DateCreated";
    private static final String SORTING_URL_PARAM_LOG = "Log";
    private static final String SORTING_URL_PARAM_ERROR_CODE = "ErrorCode";
    private static final String SORTING_URL_PARAM_CALLSID = "CallSid";
    private static final String SORTING_URL_PARAM_MESSAGE_TEXT = "MessageText";

    @Context
    private ServletContext context;
    private Configuration configuration;
    private NotificationsDao dao;
    private Gson gson;
    private XStream xstream;
    private NotificationListConverter listConverter;
    private String instanceId;

    public NotificationsEndpoint() {
        super();
    }

    @PostConstruct
    public void init() {
        final DaoManager storage = (DaoManager) context.getAttribute(DaoManager.class.getName());
        configuration = (Configuration) context.getAttribute(Configuration.class.getName());
        configuration = configuration.subset("runtime-settings");
        super.init(configuration);
        dao = storage.getNotificationsDao();
        final NotificationConverter converter = new NotificationConverter(configuration);
        listConverter = new NotificationListConverter(configuration);
        final GsonBuilder builder = new GsonBuilder();
        builder.registerTypeAdapter(Notification.class, converter);
        builder.registerTypeAdapter(NotificationList.class, listConverter);
        builder.setPrettyPrinting();
        gson = builder.create();
        xstream = new XStream();
        xstream.alias("RestcommResponse", RestCommResponse.class);
        xstream.registerConverter(converter);
        xstream.registerConverter(new NotificationListConverter(configuration));
        xstream.registerConverter(new RestCommResponseConverter(configuration));
        xstream.registerConverter(listConverter);

        instanceId = RestcommConfiguration.getInstance().getMain().getInstanceId();
    }

    protected Response getNotification(final String accountSid,
            final String sid,
            final MediaType responseType,
            UserIdentityContext userIdentityContext) {
        Account operatedAccount = accountsDao.getAccount(accountSid);
        permissionEvaluator.secure(operatedAccount,
                "RestComm:Read:Notifications",
                userIdentityContext);
        final Notification notification = dao.getNotification(new Sid(sid));
        if (notification == null) {
            return status(NOT_FOUND).build();
        } else {
            permissionEvaluator.secure(operatedAccount,
                    notification.getAccountSid(),
                    SecuredType.SECURED_STANDARD,
                    userIdentityContext);
            if (APPLICATION_JSON_TYPE.equals(responseType)) {
                return ok(gson.toJson(notification), APPLICATION_JSON).build();
            } else if (APPLICATION_XML_TYPE.equals(responseType)) {
                final RestCommResponse response = new RestCommResponse(notification);
                return ok(xstream.toXML(response), APPLICATION_XML).build();
            } else {
                return null;
            }
        }
    }

    protected Response getNotifications(final String accountSid,
            UriInfo info,
            final MediaType responseType,
            UserIdentityContext userIdentityContext) {
        permissionEvaluator.secure(accountsDao.getAccount(accountSid),
                "RestComm:Read:Notifications",
                userIdentityContext);

        boolean localInstanceOnly = true;
        try {
            String localOnly = info.getQueryParameters().getFirst("localOnly");
            if (localOnly != null && localOnly.equalsIgnoreCase("false"))
                localInstanceOnly = false;
        } catch (Exception e) {
        }

        // shall we include sub-accounts cdrs in our query ?
        boolean querySubAccounts = false; // be default we don't
        String querySubAccountsParam = info.getQueryParameters().getFirst("SubAccounts");
        if (querySubAccountsParam != null && querySubAccountsParam.equalsIgnoreCase("true"))
            querySubAccounts = true;

        String pageSize = info.getQueryParameters().getFirst("PageSize");
        String page = info.getQueryParameters().getFirst("Page");
        String startTime = info.getQueryParameters().getFirst("StartTime");
        String endTime = info.getQueryParameters().getFirst("EndTime");
        String error_code = info.getQueryParameters().getFirst("ErrorCode");
        String request_url = info.getQueryParameters().getFirst("RequestUrl");
        String message_text = info.getQueryParameters().getFirst("MessageText");
        String sortParameters = info.getQueryParameters().getFirst("SortBy");

        NotificationFilter.Builder filterBuilder = NotificationFilter.Builder.builder();

        String sortBy = null;
        String sortDirection = null;

        if (sortParameters != null && !sortParameters.isEmpty()) {
            try {
                Map<String, String> sortMap = Sorting.parseUrl(sortParameters);
                sortBy = sortMap.get(Sorting.SORT_BY_KEY);
                sortDirection = sortMap.get(Sorting.SORT_DIRECTION_KEY);
            }
            catch (Exception e) {
                return status(BAD_REQUEST).entity(buildErrorResponseBody(e.getMessage(), responseType)).build();
            }
        }

        if (sortBy != null) {
            if (sortBy.equals(SORTING_URL_PARAM_DATE_CREATED)) {
                if (sortDirection != null) {
                    if (sortDirection.equalsIgnoreCase(Sorting.Direction.ASC.name())) {
                        filterBuilder.sortedByDate(Sorting.Direction.ASC);
                    } else {
                        filterBuilder.sortedByDate(Sorting.Direction.DESC);
                    }
                }
            }
            if (sortBy.equals(SORTING_URL_PARAM_LOG)) {
                if (sortDirection != null) {
                    if (sortDirection.equalsIgnoreCase(Sorting.Direction.ASC.name())) {
                        filterBuilder.sortedByLog(Sorting.Direction.ASC);
                    } else {
                        filterBuilder.sortedByLog(Sorting.Direction.DESC);
                    }
                }
            }
            if (sortBy.equals(SORTING_URL_PARAM_ERROR_CODE)) {
                if (sortDirection != null) {
                    if (sortDirection.equalsIgnoreCase(Sorting.Direction.ASC.name())) {
                        filterBuilder.sortedByErrorCode(Sorting.Direction.ASC);
                    } else {
                        filterBuilder.sortedByErrorCode(Sorting.Direction.DESC);
                    }
                }
            }
            if (sortBy.equals(SORTING_URL_PARAM_CALLSID)) {
                if (sortDirection != null) {
                    if (sortDirection.equalsIgnoreCase(Sorting.Direction.ASC.name())) {
                        filterBuilder.sortedByCallSid(Sorting.Direction.ASC);
                    } else {
                        filterBuilder.sortedByCallSid(Sorting.Direction.DESC);
                    }
                }
            }
            if (sortBy.equals(SORTING_URL_PARAM_MESSAGE_TEXT)) {
                if (sortDirection != null) {
                    if (sortDirection.equalsIgnoreCase(Sorting.Direction.ASC.name())) {
                        filterBuilder.sortedByMessageText(Sorting.Direction.ASC);
                    } else {
                        filterBuilder.sortedByMessageText(Sorting.Direction.DESC);
                    }
                }
            }
        }
        if (pageSize == null) {
            pageSize = "50";
        }

        if (page == null) {
            page = "0";
        }

        int limit = Integer.parseInt(pageSize);
        int offset = (page.equals("0")) ? 0 : (((Integer.parseInt(page) - 1) * Integer.parseInt(pageSize)) + Integer
                .parseInt(pageSize));

        // Shall we query cdrs of sub-accounts too ?
        // if we do, we need to find the sub-accounts involved first
        List<String> ownerAccounts = null;
        if (querySubAccounts) {
            ownerAccounts = new ArrayList<String>();
            ownerAccounts.add(accountSid); // we will also return parent account cdrs
            ownerAccounts.addAll(accountsDao.getSubAccountSidsRecursive(new Sid(accountSid)));
        }

        filterBuilder.byAccountSid(accountSid)
                .byAccountSidSet(ownerAccounts)
                .byStartTime(startTime)
                .byEndTime(endTime)
                .byErrorCode(error_code)
                .byRequestUrl(request_url)
                .byMessageText(message_text)
                .limited(limit, offset);
        if (!localInstanceOnly) {
            filterBuilder.byInstanceId(instanceId);
        }

        NotificationFilter filter;
        try {
            filter = filterBuilder.build();
        } catch (ParseException e) {
            return status(BAD_REQUEST).build();
        }
        final int total = dao.getTotalNotification(filter);

        if (Integer.parseInt(page) > (total / limit)) {
            return status(javax.ws.rs.core.Response.Status.BAD_REQUEST).build();
        }

        final List<Notification> cdrs = dao.getNotifications(filter);

        listConverter.setCount(total);
        listConverter.setPage(Integer.parseInt(page));
        listConverter.setPageSize(Integer.parseInt(pageSize));
        listConverter.setPathUri(info.getRequestUri().getPath());

        if (APPLICATION_XML_TYPE.equals(responseType)) {
            final RestCommResponse response = new RestCommResponse(new NotificationList(cdrs));
            return ok(xstream.toXML(response), APPLICATION_XML).build();
        } else if (APPLICATION_JSON_TYPE.equals(responseType)) {
            return ok(gson.toJson(new NotificationList(cdrs)), APPLICATION_JSON).build();
        } else {
            return null;
        }
    }

    @Path("/{sid}")
    @GET
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public Response getNotificationAsXml(@PathParam("accountSid") final String accountSid,
            @PathParam("sid") final String sid,
            @HeaderParam("Accept") String accept,
            @Context SecurityContext sec) {
        return getNotification(accountSid,
                sid,
                retrieveMediaType(accept),
                ContextUtil.convert(sec));
    }

    @GET
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public Response getNotifications(@PathParam("accountSid") final String accountSid,
            @Context UriInfo info,
            @HeaderParam("Accept") String accept,
            @Context SecurityContext sec) {
        return getNotifications(accountSid,
                info,
                retrieveMediaType(accept),
                ContextUtil.convert(sec));
    }
}
