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

import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;

import com.sun.jersey.api.client.UniformInterfaceException;
import junit.framework.Assert;
import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.junit.Test;
import org.junit.FixMethodOrder;
import org.junit.runners.MethodSorters;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.restcomm.connect.commons.annotations.FeatureAltTests;
import org.restcomm.connect.commons.dao.MessageError;
import org.restcomm.connect.dao.entities.SmsMessage;
import org.restcomm.connect.testsuite.sms.SmsEndpointTool;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * @author <a href="mailto:n.congvu@gmail.com">vunguyen</a>
 *
 */
@RunWith(Arquillian.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class SmsMessagesEndpointTest extends EndpointTest{
    private static Logger logger = Logger.getLogger(SmsMessagesEndpointTest.class);

    private String adminAccountSid = "ACae6e420f425248d6a26948c17a9e2acf";
    private String adminAuthToken = "77f8c12cc7b8f8423e5c38b035249166";

    @Test
    public void getSmsMessageList() {
        JsonObject firstPage = SmsEndpointTool.getInstance().getSmsMessageList(deploymentUrl.toString(), adminAccountSid,
                adminAuthToken);
        int totalSize = firstPage.get("total").getAsInt();
        JsonArray firstPageMessagesArray = firstPage.get("messages").getAsJsonArray();
        int firstPageMessagesArraySize = firstPageMessagesArray.size();
        assertTrue(firstPageMessagesArraySize == 34);
        assertTrue(firstPage.get("start").getAsInt() == 0);
        assertTrue(firstPage.get("end").getAsInt() == 34);
        assertTrue(totalSize == 34);
    }

    @Test
    @Category(FeatureAltTests.class)
    public void getSmsMessageListUsingPageSize() {
        JsonObject firstPage = SmsEndpointTool.getInstance().getSmsMessageList(deploymentUrl.toString(), adminAccountSid,
                adminAuthToken, null, 10, null, true);
        JsonArray firstPageMessagesArray = firstPage.get("messages").getAsJsonArray();
        assertTrue(firstPageMessagesArray.size() == 10);
        assertTrue(firstPage.get("start").getAsInt() == 0);
        assertTrue(firstPage.get("end").getAsInt() == 9);

        JsonObject secondPage = (JsonObject) SmsEndpointTool.getInstance().getSmsMessageList(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, 2, 10, null, true);
        JsonArray secondPageMessagesArray = secondPage.get("messages").getAsJsonArray();
        assertTrue(secondPageMessagesArray.size() == 10);
        assertTrue(secondPage.get("start").getAsInt() == 20);
        assertTrue(secondPage.get("end").getAsInt() == 29);

        JsonObject lastPage = (JsonObject) SmsEndpointTool.getInstance().getSmsMessageList(deploymentUrl.toString(), adminAccountSid,
        adminAuthToken, firstPage.get("num_pages").getAsInt(), 10, null, true);
        JsonArray lastPageMessagesArray = lastPage.get("messages").getAsJsonArray();
        assertTrue(lastPageMessagesArray.size() == 4);
        assertTrue(lastPage.get("start").getAsInt() == 30);
        assertTrue(lastPage.get("end").getAsInt() == 34);
    }

    @Test
    @Category(FeatureAltTests.class)
    public void getSmsMessageListFilteredBySender() {

        Map<String, String> filters = new HashMap<String, String>();
        filters.put("From", "49376%");

        JsonObject filteredMessagesBySender = SmsEndpointTool.getInstance().getSmsMessageListUsingFilter(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, filters);

        assertTrue(filteredMessagesBySender.get("messages").getAsJsonArray().size() == 4);
        assertTrue(filteredMessagesBySender.get("start").getAsInt() == 0);
        assertTrue(filteredMessagesBySender.get("end").getAsInt() == 4);
    }

    @Test
    @Category(FeatureAltTests.class)
    public void getSmsMessageListFilteredByRecipent() {

        Map<String, String> filters = new HashMap<String, String>();
        filters.put("To", "13557%");

        JsonObject filteredMessagesByRecipent = SmsEndpointTool.getInstance().getSmsMessageListUsingFilter(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, filters);

        assertTrue(filteredMessagesByRecipent.get("messages").getAsJsonArray().size() == 12);
        assertTrue(filteredMessagesByRecipent.get("start").getAsInt() == 0);
        assertTrue(filteredMessagesByRecipent.get("end").getAsInt() == 12);
    }

    @Test
    @Category(FeatureAltTests.class)
    public void getSmsMessageListFilteredByStartTime() {
        Map<String, String> filters = new HashMap<String, String>();
        filters.put("StartTime", "2013-07-07 18:15:12.018000000");

        JsonObject filteredMessagesByStartTime = SmsEndpointTool.getInstance().getSmsMessageListUsingFilter(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, filters);

        assertTrue(filteredMessagesByStartTime.get("messages").getAsJsonArray().size() == 24);
        assertTrue(filteredMessagesByStartTime.get("start").getAsInt() == 0);
        assertTrue(filteredMessagesByStartTime.get("end").getAsInt() == 24);
    }

    @Test
    @Category(FeatureAltTests.class)
    public void getSmsMessageListFilteredByEndTime() {
        Map<String, String> filters = new HashMap<String, String>();
        filters.put("EndTime", "2013-07-09 16:22:35.146000000");

        JsonObject filteredMessagesByEndTime = SmsEndpointTool.getInstance().getSmsMessageListUsingFilter(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, filters);

        assertTrue(filteredMessagesByEndTime.get("messages").getAsJsonArray().size() == 26);
        assertTrue(filteredMessagesByEndTime.get("start").getAsInt() == 0);
        assertTrue(filteredMessagesByEndTime.get("end").getAsInt() == 26);
    }

    @Test
    @Category(FeatureAltTests.class)
    public void getSmsMessageListFilteredByBody() {
        Map<String, String> filters = new HashMap<String, String>();
        filters.put("Body", "Hello");

        JsonObject filteredMessagesByBody = SmsEndpointTool.getInstance().getSmsMessageListUsingFilter(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, filters);

        assertTrue(filteredMessagesByBody.get("messages").getAsJsonArray().size() == 6);
        assertTrue(filteredMessagesByBody.get("start").getAsInt() == 0);
        assertTrue(filteredMessagesByBody.get("end").getAsInt() == 6);
    }

    @Test
    @Category(FeatureAltTests.class)
    public void getSmsMessageBySidVerifyError() {

        JsonObject smsMessageJson = SmsEndpointTool.getInstance().getSmsMessage(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, "SMfe8a9e566f4544eab21c2ec94ae9e790");
        assertNotNull(smsMessageJson);
        assertEquals(SmsMessage.Status.SENT.toString(), smsMessageJson.get("status").getAsString());
        assertEquals(MessageError.UNKNOWN_ERROR.getErrorCode().intValue(), smsMessageJson.get("error_code").getAsInt());
        assertEquals(MessageError.UNKNOWN_ERROR.getErrorMessage(), smsMessageJson.get("error_message").getAsString());
    }

    @Test
    public void getSmsMessageListUsingSorting() {
        int pageSize = 30;
        // Provide both sort field and direction
        // Provide ascending sorting and verify that the first row is indeed the earliest one
        JsonObject response = SmsEndpointTool.getInstance().getSmsMessageList(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, 0, pageSize, "DateCreated:asc", true);
        // Remember there is a discrepancy between the sort parameters and the result attribute in the .json response. For example DateCreated:asc, means
        // to sort based of DateCreated field, but in the response the field is called 'date_created', not DateCreated. This only happens only for .json; in
        // .xml the naming seems to be respected.
        // Notice that we are removing the timezone part from the end of the string, because CI potentially uses different timezone that messes the test up
        assertEquals("Fri, 5 Jul 2013 21:32:40", ((JsonObject)response.get("messages").getAsJsonArray().get(0)).get("date_created").getAsString().replaceFirst("\\s*\\+.*", ""));

        // Provide only sort field; all fields default to ascending
        response = SmsEndpointTool.getInstance().getSmsMessageList(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, 0, pageSize, "DateCreated", true);
        assertEquals("Fri, 5 Jul 2013 21:32:40", ((JsonObject)response.get("messages").getAsJsonArray().get(0)).get("date_created").getAsString().replaceFirst("\\s*\\+.*", ""));

        response = SmsEndpointTool.getInstance().getSmsMessageList(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, 0, pageSize, "DateCreated:desc", true);
        assertEquals("Thu, 11 Jul 2013 15:39:32", ((JsonObject)response.get("messages").getAsJsonArray().get(0)).get("date_created").getAsString().replaceFirst("\\s*\\+.*", ""));

        try {
            // provide only direction, should cause an exception
            SmsEndpointTool.getInstance().getSmsMessageList(deploymentUrl.toString(),
                    adminAccountSid, adminAuthToken, 0, pageSize, ":asc", true);
        }
        catch (UniformInterfaceException e) {
            assertEquals(BAD_REQUEST.getStatusCode(), e.getResponse().getStatus());
        }

        try {
            // provide sort field and direction, but direction is invalid (neither of asc or desc)
            SmsEndpointTool.getInstance().getSmsMessageList(deploymentUrl.toString(),
                    adminAccountSid, adminAuthToken, 0, pageSize, "DateCreated:invalid", true);
        }
        catch (UniformInterfaceException e) {
            assertEquals(BAD_REQUEST.getStatusCode(), e.getResponse().getStatus());
        }

        // From
        response = SmsEndpointTool.getInstance().getSmsMessageList(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, 0, pageSize, "From:asc", true);
        assertEquals("+13213557674", ((JsonObject)response.get("messages").getAsJsonArray().get(0)).get("from").getAsString());
        response = SmsEndpointTool.getInstance().getSmsMessageList(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, 0, pageSize, "From:desc", true);
        assertEquals("19549376176", ((JsonObject)response.get("messages").getAsJsonArray().get(0)).get("from").getAsString());

        // To
        response = SmsEndpointTool.getInstance().getSmsMessageList(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, 0, pageSize, "To:asc", true);
        assertEquals("+13213557674", ((JsonObject)response.get("messages").getAsJsonArray().get(0)).get("to").getAsString());
        response = SmsEndpointTool.getInstance().getSmsMessageList(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, 0, pageSize, "To:desc", true);
        assertEquals("19549376176", ((JsonObject)response.get("messages").getAsJsonArray().get(0)).get("to").getAsString());

        // Direction
        response = SmsEndpointTool.getInstance().getSmsMessageList(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, 0, pageSize, "Direction:asc", true);
        assertEquals("inbound", ((JsonObject)response.get("messages").getAsJsonArray().get(0)).get("direction").getAsString());
        response = SmsEndpointTool.getInstance().getSmsMessageList(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, 0, pageSize, "Direction:desc", true);
        assertEquals("outbound-reply", ((JsonObject)response.get("messages").getAsJsonArray().get(0)).get("direction").getAsString());

        // Status
        response = SmsEndpointTool.getInstance().getSmsMessageList(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, 0, pageSize, "Status:asc", true);
        assertEquals("failed", ((JsonObject)response.get("messages").getAsJsonArray().get(0)).get("status").getAsString());
        response = SmsEndpointTool.getInstance().getSmsMessageList(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, 0, pageSize, "Status:desc", true);
        assertEquals("sent", ((JsonObject)response.get("messages").getAsJsonArray().get(0)).get("status").getAsString());

        // Body
        response = SmsEndpointTool.getInstance().getSmsMessageList(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, 0, 34, "Body:asc", true);
        assertEquals("Hello", ((JsonObject)response.get("messages").getAsJsonArray().get(0)).get("body").getAsString());
        response = SmsEndpointTool.getInstance().getSmsMessageList(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, 0, pageSize, "Body:desc", true);
        assertEquals("take 3", ((JsonObject)response.get("messages").getAsJsonArray().get(0)).get("body").getAsString());

        // Price
        response = SmsEndpointTool.getInstance().getSmsMessageList(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, 0, pageSize, "Price:asc", true);
        assertEquals("0.00", ((JsonObject)response.get("messages").getAsJsonArray().get(0)).get("price").getAsString());
        response = SmsEndpointTool.getInstance().getSmsMessageList(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, 0, pageSize, "Price:desc", true);
        assertEquals("120.00", ((JsonObject)response.get("messages").getAsJsonArray().get(0)).get("price").getAsString());
    }

    @Deployment(name = "SmsMessagesEndpointTest", managed = true, testable = false)
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
        archive.addAsWebInfResource("restcomm_for_SMSEndpointTest.xml", "conf/restcomm.xml");
        archive.addAsWebInfResource("restcomm_with_Data.script", "data/hsql/restcomm.script");
        logger.info("Packaged Test App");
        return archive;
    }
}
