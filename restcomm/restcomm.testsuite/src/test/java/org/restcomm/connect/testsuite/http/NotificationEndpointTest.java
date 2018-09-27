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
package org.restcomm.connect.testsuite.http;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.Map;

import com.sun.jersey.api.client.UniformInterfaceException;
import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;

import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import org.junit.FixMethodOrder;
import org.junit.runners.MethodSorters;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.restcomm.connect.commons.annotations.FeatureAltTests;

/**
 * @author <a href="mailto:n.congvu@gmail.com">vunguyen</a>
 *
 */
@RunWith(Arquillian.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class NotificationEndpointTest extends EndpointTest{
    private static Logger logger = Logger.getLogger(NotificationEndpointTest.class);

    private String adminAccountSid = "ACae6e420f425248d6a26948c17a9e2acf";
    private String adminAuthToken = "77f8c12cc7b8f8423e5c38b035249166";

    @Test
    public void getNotificationList() {
        JsonObject firstPage = NotificationEndpointTool.getInstance().getNotificationList(deploymentUrl.toString(), adminAccountSid,
                adminAuthToken);
        int totalSize = firstPage.get("total").getAsInt();
        JsonArray firstPageNotificationsArray = firstPage.get("notifications").getAsJsonArray();
        int firstPageNotificationsArraySize = firstPageNotificationsArray.size();

        assertEquals(34, firstPageNotificationsArraySize);
        assertEquals(0, firstPage.get("start").getAsInt());
        assertEquals(34, firstPage.get("end").getAsInt());
        assertEquals(34, totalSize);
    }

    @Test
    @Category(FeatureAltTests.class)
    public void getNotificationListUsingPageSize() {
        JsonObject firstPage = NotificationEndpointTool.getInstance().getNotificationList(deploymentUrl.toString(), adminAccountSid,
                adminAuthToken, null, 10, null, true);
        JsonArray firstPageNotificationsArray = firstPage.get("notifications").getAsJsonArray();
        assertEquals(10, firstPageNotificationsArray.size());
        assertEquals(0, firstPage.get("start").getAsInt());
        assertEquals(9, firstPage.get("end").getAsInt());

        JsonObject secondPage = (JsonObject) NotificationEndpointTool.getInstance().getNotificationList(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, 2, 10, null, true);
        JsonArray secondPageNotificationsArray = secondPage.get("notifications").getAsJsonArray();
        assertEquals(10, secondPageNotificationsArray.size());
        assertEquals(20, secondPage.get("start").getAsInt());
        assertEquals(29, secondPage.get("end").getAsInt());

        JsonObject lastPage = (JsonObject) NotificationEndpointTool.getInstance().getNotificationList(deploymentUrl.toString(), adminAccountSid,
        adminAuthToken, firstPage.get("num_pages").getAsInt(), 10, null,  true);
        JsonArray lastPageNotificationsArray = lastPage.get("notifications").getAsJsonArray();
        assertEquals(4, lastPageNotificationsArray.size());
        assertEquals(30, lastPage.get("start").getAsInt());
        assertEquals(34, lastPage.get("end").getAsInt());
    }

    @Test
    @Category(FeatureAltTests.class)
    public void getNotificationListFilteredByStartTime() {
        Map<String, String> filters = new HashMap<String, String>();
        filters.put("StartTime", "2013-09-30 16:28:33.403000000");

        JsonObject filteredNotificationsByStartTime = NotificationEndpointTool.getInstance().getNotificationListUsingFilter(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, filters);

        assertEquals(14, filteredNotificationsByStartTime.get("notifications").getAsJsonArray().size());
        assertEquals(0, filteredNotificationsByStartTime.get("start").getAsInt());
        assertEquals(14, filteredNotificationsByStartTime.get("end").getAsInt());
    }

    @Test
    @Category(FeatureAltTests.class)
    public void getNotificationListFilteredByEndTime() {
        Map<String, String> filters = new HashMap<String, String>();
        filters.put("EndTime", "2013-09-30 16:28:33.403000000");


        JsonObject filteredNotificationsByEndTime = NotificationEndpointTool.getInstance().getNotificationListUsingFilter(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, filters);

        assertEquals(30, filteredNotificationsByEndTime.get("notifications").getAsJsonArray().size());
        assertEquals(0, filteredNotificationsByEndTime.get("start").getAsInt());
        assertEquals(30, filteredNotificationsByEndTime.get("end").getAsInt());
    }

    @Test
    @Category(FeatureAltTests.class)
    public void getNotificationListFilteredByErrorCode() {
        Map<String, String> filters = new HashMap<String, String>();
        filters.put("ErrorCode", "1");

        JsonObject filteredNotificationsByErrorCode = NotificationEndpointTool.getInstance().getNotificationListUsingFilter(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, filters);

        assertEquals(9, filteredNotificationsByErrorCode.get("notifications").getAsJsonArray().size());
        assertEquals(0, filteredNotificationsByErrorCode.get("start").getAsInt());
        assertEquals(9, filteredNotificationsByErrorCode.get("end").getAsInt());
    }

    @Test
    @Category(FeatureAltTests.class)
    public void getNotificationListFilteredByRequestUrl() {
        Map<String, String> filters = new HashMap<String, String>();
        filters.put("RequestUrl", "http://instance1.restcomm.com:8080/restcomm/recordings/RE50675909d9c94acda36f0e119b6cb432.wav");

        JsonObject filteredNotificationsByRequestUrl = NotificationEndpointTool.getInstance().getNotificationListUsingFilter(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, filters);

        assertEquals(20, filteredNotificationsByRequestUrl.get("notifications").getAsJsonArray().size());
        assertEquals(0, filteredNotificationsByRequestUrl.get("start").getAsInt());
        assertEquals(20, filteredNotificationsByRequestUrl.get("end").getAsInt());
    }

    @Test
    @Category(FeatureAltTests.class)
    public void getNotificationListFilteredByMessageText() {
        Map<String, String> filters = new HashMap<String, String>();
        filters.put("MessageText", "Restcomm");

        JsonObject filteredNotificationsByMessageText = NotificationEndpointTool.getInstance().getNotificationListUsingFilter(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, filters);

        assertEquals(17, filteredNotificationsByMessageText.get("notifications").getAsJsonArray().size());
        assertEquals(0, filteredNotificationsByMessageText.get("start").getAsInt());
        assertEquals(17, filteredNotificationsByMessageText.get("end").getAsInt());
    }

    @Test
    public void getNotificationListUsingSorting() {
        int pageSize = 30;
        // Provide both sort field and direction
        // Provide ascending sorting and verify that the first row is indeed the earliest one
        JsonObject response = NotificationEndpointTool.getInstance().getNotificationList(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, 0, pageSize, "DateCreated:asc", true);
        // Remember there is a discrepancy between the sort parameters and the result attribute in the .json response. For example DateCreated:asc, means
        // to sort based of DateCreated field, but in the response the field is called 'date_created', not DateCreated. This only happens only for .json; in
        // .xml the naming seems to be respected.
        // Notice that we are removing the timezone part from the end of the string, because CI potentially uses different timezone that messes the test up
        assertEquals("Fri, 30 Aug 2013 16:28:33", ((JsonObject)response.get("notifications").getAsJsonArray().get(0)).get("date_created").getAsString().replaceFirst("\\s*\\+.*", ""));

        // Provide only sort field; all fields default to ascending
        response = NotificationEndpointTool.getInstance().getNotificationList(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, 0, pageSize, "DateCreated", true);
        assertEquals("Fri, 30 Aug 2013 16:28:33", ((JsonObject)response.get("notifications").getAsJsonArray().get(0)).get("date_created").getAsString().replaceFirst("\\s*\\+.*", ""));

        response = NotificationEndpointTool.getInstance().getNotificationList(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, 0, pageSize, "DateCreated:desc", true);
        assertEquals("Wed, 30 Oct 2013 16:28:33", ((JsonObject)response.get("notifications").getAsJsonArray().get(0)).get("date_created").getAsString().replaceFirst("\\s*\\+.*", ""));

        try {
            // provide only direction, should cause an exception
            NotificationEndpointTool.getInstance().getNotificationList(deploymentUrl.toString(),
                    adminAccountSid, adminAuthToken, 0, pageSize, ":asc", true);
        }
        catch (UniformInterfaceException e) {
            assertEquals(BAD_REQUEST.getStatusCode(), e.getResponse().getStatus());
        }

        try {
            // provide sort field and direction, but direction is invalid (neither of asc or desc)
            NotificationEndpointTool.getInstance().getNotificationList(deploymentUrl.toString(),
                    adminAccountSid, adminAuthToken, 0, pageSize, "DateCreated:invalid", true);
        }
        catch (UniformInterfaceException e) {
            assertEquals(BAD_REQUEST.getStatusCode(), e.getResponse().getStatus());
        }

        // Log
        response = NotificationEndpointTool.getInstance().getNotificationList(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, 0, pageSize, "Log:asc", true);
        assertEquals("0", ((JsonObject)response.get("notifications").getAsJsonArray().get(0)).get("log").getAsString());
        response = NotificationEndpointTool.getInstance().getNotificationList(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, 0, pageSize, "Log:desc", true);
        assertEquals("1", ((JsonObject)response.get("notifications").getAsJsonArray().get(0)).get("log").getAsString());

        // Error Code
        response = NotificationEndpointTool.getInstance().getNotificationList(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, 0, pageSize, "ErrorCode:asc", true);
        assertEquals("0", ((JsonObject)response.get("notifications").getAsJsonArray().get(0)).get("error_code").getAsString());
        response = NotificationEndpointTool.getInstance().getNotificationList(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, 0, pageSize, "ErrorCode:desc", true);
        assertEquals("100", ((JsonObject)response.get("notifications").getAsJsonArray().get(0)).get("error_code").getAsString());

        // CallSid
        response = NotificationEndpointTool.getInstance().getNotificationList(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, 0, pageSize, "CallSid:asc", true);
        assertEquals("CA5EB00000000000000000000000000002", ((JsonObject)response.get("notifications").getAsJsonArray().get(0)).get("call_sid").getAsString());
        response = NotificationEndpointTool.getInstance().getNotificationList(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, 0, pageSize, "CallSid:desc", true);
        assertEquals("CA5EB00000000000000000000000000009", ((JsonObject)response.get("notifications").getAsJsonArray().get(0)).get("call_sid").getAsString());

        // Message Text
        response = NotificationEndpointTool.getInstance().getNotificationList(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, 0, pageSize, "MessageText:asc", true);
        assertEquals("Another fictitious message for testing", ((JsonObject)response.get("notifications").getAsJsonArray().get(0)).get("message_text").getAsString());
        response = NotificationEndpointTool.getInstance().getNotificationList(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, 0, pageSize, "MessageText:desc", true);
        assertEquals("Workspace migration skipped in 2016-12-28 21:12:25.758", ((JsonObject)response.get("notifications").getAsJsonArray().get(0)).get("message_text").getAsString());
    }

    @Deployment(name = "NotificationEndpointTest", managed = true, testable = false)
    public static WebArchive createWebArchiveNoGw() {
        logger.info("Packaging Test App");
        WebArchive archive = ShrinkWrap.create(WebArchive.class, "restcomm.war");
        final WebArchive restcommArchive = Maven.resolver()
                .resolve("org.restcomm:restcomm-connect.application:war:" + version).withoutTransitivity()
                .asSingle(WebArchive.class);
        archive = archive.merge(restcommArchive);
        archive.delete("/WEB-INF/sip.xml");
archive.delete("/WEB-INF/web.xml");
        archive.delete("/WEB-INF/conf/restcomm.xml");
        archive.delete("/WEB-INF/data/hsql/restcomm.script");
        archive.addAsWebInfResource("sip.xml");
        archive.addAsWebInfResource("web.xml");
        archive.addAsWebInfResource("restcomm.xml", "conf/restcomm.xml");
        archive.addAsWebInfResource("restcomm_with_Data.script", "data/hsql/restcomm.script");
        logger.info("Packaged Test App");
        return archive;
    }
}
