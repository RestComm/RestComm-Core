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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.HashMap;
import java.util.Map;

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
                adminAuthToken, null, 10, true);
        JsonArray firstPageMessagesArray = firstPage.get("messages").getAsJsonArray();
        assertTrue(firstPageMessagesArray.size() == 10);
        assertTrue(firstPage.get("start").getAsInt() == 0);
        assertTrue(firstPage.get("end").getAsInt() == 9);

        JsonObject secondPage = (JsonObject) SmsEndpointTool.getInstance().getSmsMessageList(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, 2, 10, true);
        JsonArray secondPageMessagesArray = secondPage.get("messages").getAsJsonArray();
        assertTrue(secondPageMessagesArray.size() == 10);
        assertTrue(secondPage.get("start").getAsInt() == 20);
        assertTrue(secondPage.get("end").getAsInt() == 29);

        JsonObject lastPage = (JsonObject) SmsEndpointTool.getInstance().getSmsMessageList(deploymentUrl.toString(), adminAccountSid,
        adminAuthToken, firstPage.get("num_pages").getAsInt(), 10, true);
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
