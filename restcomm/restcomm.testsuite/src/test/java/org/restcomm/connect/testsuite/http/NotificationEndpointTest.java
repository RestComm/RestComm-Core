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
import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
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

        assertTrue(firstPageNotificationsArraySize == 34);
        assertTrue(firstPage.get("start").getAsInt() == 0);
        assertTrue(firstPage.get("end").getAsInt() == 34);
        assertTrue(totalSize == 34);
    }

    @Test
    @Category(FeatureAltTests.class)
    public void getNotificationListUsingPageSize() {
        JsonObject firstPage = NotificationEndpointTool.getInstance().getNotificationList(deploymentUrl.toString(), adminAccountSid,
                adminAuthToken, null, 10, true);
        JsonArray firstPageNotificationsArray = firstPage.get("notifications").getAsJsonArray();
        assertTrue(firstPageNotificationsArray.size() == 10);
        assertTrue(firstPage.get("start").getAsInt() == 0);
        assertTrue(firstPage.get("end").getAsInt() == 9);

        JsonObject secondPage = (JsonObject) NotificationEndpointTool.getInstance().getNotificationList(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, 2, 10, true);
        JsonArray secondPageNotificationsArray = secondPage.get("notifications").getAsJsonArray();
        assertTrue(secondPageNotificationsArray.size() == 10);
        assertTrue(secondPage.get("start").getAsInt() == 20);
        assertTrue(secondPage.get("end").getAsInt() == 29);

        JsonObject lastPage = (JsonObject) NotificationEndpointTool.getInstance().getNotificationList(deploymentUrl.toString(), adminAccountSid,
        adminAuthToken, firstPage.get("num_pages").getAsInt(), 10, true);
        JsonArray lastPageNotificationsArray = lastPage.get("notifications").getAsJsonArray();
        assertTrue(lastPageNotificationsArray.size() == 4);
        assertTrue(lastPage.get("start").getAsInt() == 30);
        assertTrue(lastPage.get("end").getAsInt() == 34);
    }

    @Test
    @Category(FeatureAltTests.class)
    public void getNotificationListFilteredByStartTime() {
        Map<String, String> filters = new HashMap<String, String>();
        filters.put("StartTime", "2013-09-30 16:28:33.403000000");

        JsonObject filteredNotificationsByStartTime = NotificationEndpointTool.getInstance().getNotificationListUsingFilter(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, filters);

        assertTrue(filteredNotificationsByStartTime.get("notifications").getAsJsonArray().size() == 24);
        assertTrue(filteredNotificationsByStartTime.get("start").getAsInt() == 0);
        assertTrue(filteredNotificationsByStartTime.get("end").getAsInt() == 24);
    }

    @Test
    @Category(FeatureAltTests.class)
    public void getNotificationListFilteredByEndTime() {
        Map<String, String> filters = new HashMap<String, String>();
        filters.put("EndTime", "2013-09-30 16:28:33.403000000");


        JsonObject filteredNotificationsByEndTime = NotificationEndpointTool.getInstance().getNotificationListUsingFilter(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, filters);

        assertTrue(filteredNotificationsByEndTime.get("notifications").getAsJsonArray().size() == 20);
        assertTrue(filteredNotificationsByEndTime.get("start").getAsInt() == 0);
        assertTrue(filteredNotificationsByEndTime.get("end").getAsInt() == 20);
    }

    @Test
    @Category(FeatureAltTests.class)
    public void getNotificationListFilteredByErrorCode() {
        Map<String, String> filters = new HashMap<String, String>();
        filters.put("ErrorCode", "1");

        JsonObject filteredNotificationsByErrorCode = NotificationEndpointTool.getInstance().getNotificationListUsingFilter(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, filters);

        assertTrue(filteredNotificationsByErrorCode.get("notifications").getAsJsonArray().size() == 19);
        assertTrue(filteredNotificationsByErrorCode.get("start").getAsInt() == 0);
        assertTrue(filteredNotificationsByErrorCode.get("end").getAsInt() == 19);
    }

    @Test
    @Category(FeatureAltTests.class)
    public void getNotificationListFilteredByRequestUrl() {
        Map<String, String> filters = new HashMap<String, String>();
        filters.put("RequestUrl", "http://instance1.restcomm.com:8080/restcomm/recordings/RE50675909d9c94acda36f0e119b6cb432.wav");

        JsonObject filteredNotificationsByRequestUrl = NotificationEndpointTool.getInstance().getNotificationListUsingFilter(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, filters);

        assertTrue(filteredNotificationsByRequestUrl.get("notifications").getAsJsonArray().size() == 18);
        assertTrue(filteredNotificationsByRequestUrl.get("start").getAsInt() == 0);
        assertTrue(filteredNotificationsByRequestUrl.get("end").getAsInt() == 18);
    }

    @Test
    @Category(FeatureAltTests.class)
    public void getNotificationListFilteredByMessageText() {
        Map<String, String> filters = new HashMap<String, String>();
        filters.put("MessageText", "Restcomm");

        JsonObject filteredNotificationsByMessageText = NotificationEndpointTool.getInstance().getNotificationListUsingFilter(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, filters);

        assertTrue(filteredNotificationsByMessageText.get("notifications").getAsJsonArray().size() == 15);
        assertTrue(filteredNotificationsByMessageText.get("start").getAsInt() == 0);
        assertTrue(filteredNotificationsByMessageText.get("end").getAsInt() == 15);
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
