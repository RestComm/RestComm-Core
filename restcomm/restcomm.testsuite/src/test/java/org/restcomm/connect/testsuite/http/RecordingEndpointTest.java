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
public class RecordingEndpointTest extends EndpointTest{
    private static Logger logger = Logger.getLogger(RecordingEndpointTest.class);

    private String adminAccountSid = "ACae6e420f425248d6a26948c17a9e2acf";
    private String adminAuthToken = "77f8c12cc7b8f8423e5c38b035249166";

    @Test
    public void getRecordingList() {
        JsonObject firstPage = RecordingEndpointTool.getInstance().getRecordingList(deploymentUrl.toString(), adminAccountSid,
                adminAuthToken);
        int totalSize = firstPage.get("total").getAsInt();
        JsonArray firstPageRecordingsArray = firstPage.get("recordings").getAsJsonArray();
        int firstPageRecordingsArraySize = firstPageRecordingsArray.size();
        assertTrue(firstPageRecordingsArraySize == 34);
        assertTrue(firstPage.get("start").getAsInt() == 0);
        assertTrue(firstPage.get("end").getAsInt() == 34);
        assertTrue(totalSize == 34);
    }

    @Test
    @Category(FeatureAltTests.class)
    public void getRecordingListUsingPageSize() {
        JsonObject firstPage = RecordingEndpointTool.getInstance().getRecordingList(deploymentUrl.toString(), adminAccountSid,
                adminAuthToken, null, 10, null, true);
        JsonArray firstPageRecordingsArray = firstPage.get("recordings").getAsJsonArray();
        assertTrue(firstPageRecordingsArray.size() == 10);
        assertTrue(firstPage.get("start").getAsInt() == 0);
        assertTrue(firstPage.get("end").getAsInt() == 9);

        JsonObject secondPage = (JsonObject) RecordingEndpointTool.getInstance().getRecordingList(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, 2, 10, null,  true);
        JsonArray secondPageRecordingsArray = secondPage.get("recordings").getAsJsonArray();
        assertTrue(secondPageRecordingsArray.size() == 10);
        assertTrue(secondPage.get("start").getAsInt() == 20);
        assertTrue(secondPage.get("end").getAsInt() == 29);

        JsonObject lastPage = (JsonObject) RecordingEndpointTool.getInstance().getRecordingList(deploymentUrl.toString(), adminAccountSid,
        adminAuthToken, firstPage.get("num_pages").getAsInt(), 10, null,  true);
        JsonArray lastPageRecordingsArray = lastPage.get("recordings").getAsJsonArray();
        assertTrue(lastPageRecordingsArray.size() == 4);
        assertTrue(lastPage.get("start").getAsInt() == 30);
        assertTrue(lastPage.get("end").getAsInt() == 34);
    }

    @Test
    @Category(FeatureAltTests.class)
    public void getRecordingListFilteredByStartTime() {
        Map<String, String> filters = new HashMap<String, String>();
        filters.put("StartTime", "2015-01-08 08:51:07.955000");

        JsonObject filteredRecordingsByStartTime = RecordingEndpointTool.getInstance().getRecordingListUsingFilter(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, filters);

        assertTrue(filteredRecordingsByStartTime.get("recordings").getAsJsonArray().size() == 32);
        assertTrue(filteredRecordingsByStartTime.get("start").getAsInt() == 0);
        assertTrue(filteredRecordingsByStartTime.get("end").getAsInt() == 32);
    }

    @Test
    @Category(FeatureAltTests.class)
    public void getRecordingListFilteredByEndTime() {
        Map<String, String> filters = new HashMap<String, String>();
        filters.put("EndTime", "2015-01-08 08:51:07.955000");

        JsonObject filteredRecordingsByEndTime = RecordingEndpointTool.getInstance().getRecordingListUsingFilter(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, filters);

        assertTrue(filteredRecordingsByEndTime.get("recordings").getAsJsonArray().size() == 14);
        assertTrue(filteredRecordingsByEndTime.get("start").getAsInt() == 0);
        assertTrue(filteredRecordingsByEndTime.get("end").getAsInt() == 14);
    }

    @Test
    public void getRecordingListFilteredByCallSid() {
        Map<String, String> filters = new HashMap<String, String>();
        filters.put("CallSid", "CAfe9ce46f104f5beeb10c83a5dad2be66");

        JsonObject filteredRecordingsByCallSid = RecordingEndpointTool.getInstance().getRecordingListUsingFilter(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, filters);

        assertTrue(filteredRecordingsByCallSid.get("recordings").getAsJsonArray().size() == 9);
        assertTrue(filteredRecordingsByCallSid.get("start").getAsInt() == 0);
        assertTrue(filteredRecordingsByCallSid.get("end").getAsInt() == 9);
    }

    @Test
    public void recordingDelete() {
        String recordingSid = "RE10000000000000000000000000000032";

        RecordingEndpointTool.getInstance().deleteRecording(deploymentUrl.toString(), adminAccountSid, adminAuthToken, recordingSid);

        JsonObject firstPage = RecordingEndpointTool.getInstance().getRecordingList(deploymentUrl.toString(), adminAccountSid,
                adminAuthToken);
        int totalSize = firstPage.get("total").getAsInt();
        JsonArray firstPageRecordingsArray = firstPage.get("recordings").getAsJsonArray();
        int firstPageRecordingsArraySize = firstPageRecordingsArray.size();
        assertTrue(firstPageRecordingsArraySize == 33);
        assertTrue(firstPage.get("start").getAsInt() == 0);
        assertTrue(firstPage.get("end").getAsInt() == 33);
        assertTrue(totalSize == 33);
    }

    @Test
    public void getRecordingListUsingSorting() {
        int pageSize = 30;
        // Provide both sort field and direction
        // Provide ascending sorting and verify that the first row is indeed the earliest one
        JsonObject response = RecordingEndpointTool.getInstance().getRecordingList(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, 0, pageSize, "DateCreated:asc", true);
        // Remember there is a discrepancy between the sort parameters and the result attribute in the .json response. For example DateCreated:asc, means
        // to sort based of DateCreated field, but in the response the field is called 'date_created', not DateCreated. This only happens only for .json; in
        // .xml the naming seems to be respected.
        // Notice that we are removing the timezone part from the end of the string, because CI potentially uses different timezone that messes the test up
        assertEquals("Mon, 6 Jan 2014 08:51:08", ((JsonObject)response.get("recordings").getAsJsonArray().get(0)).get("date_created").getAsString().replaceFirst("\\s*\\+.*", ""));

        // Provide only sort field; all fields default to ascending
        response = RecordingEndpointTool.getInstance().getRecordingList(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, 0, pageSize, "DateCreated", true);
        assertEquals("Mon, 6 Jan 2014 08:51:08", ((JsonObject)response.get("recordings").getAsJsonArray().get(0)).get("date_created").getAsString().replaceFirst("\\s*\\+.*", ""));

        response = RecordingEndpointTool.getInstance().getRecordingList(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, 0, pageSize, "DateCreated:desc", true);
        assertEquals("Tue, 5 Jan 2016 08:51:07", ((JsonObject)response.get("recordings").getAsJsonArray().get(0)).get("date_created").getAsString().replaceFirst("\\s*\\+.*", ""));

        try {
            // provide only direction, should cause an exception
            RecordingEndpointTool.getInstance().getRecordingList(deploymentUrl.toString(),
                    adminAccountSid, adminAuthToken, 0, pageSize, ":asc", true);
        }
        catch (UniformInterfaceException e) {
            assertEquals(BAD_REQUEST.getStatusCode(), e.getResponse().getStatus());
        }

        try {
            // provide sort field and direction, but direction is invalid (neither of asc or desc)
            RecordingEndpointTool.getInstance().getRecordingList(deploymentUrl.toString(),
                    adminAccountSid, adminAuthToken, 0, pageSize, "DateCreated:invalid", true);
        }
        catch (UniformInterfaceException e) {
            assertEquals(BAD_REQUEST.getStatusCode(), e.getResponse().getStatus());
        }

        // Duration
        response = RecordingEndpointTool.getInstance().getRecordingList(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, 0, pageSize, "Duration:asc", true);
        assertEquals("1.0", ((JsonObject)response.get("recordings").getAsJsonArray().get(0)).get("duration").getAsString());
        response = RecordingEndpointTool.getInstance().getRecordingList(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, 0, pageSize, "Duration:desc", true);
        assertEquals("59.0", ((JsonObject)response.get("recordings").getAsJsonArray().get(0)).get("duration").getAsString());

        // CallSid
        response = RecordingEndpointTool.getInstance().getRecordingList(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, 0, pageSize, "CallSid:asc", true);
        assertEquals("CA009ce46f104f4beeb10c83a5dad2bf66", ((JsonObject)response.get("recordings").getAsJsonArray().get(0)).get("call_sid").getAsString());
        response = RecordingEndpointTool.getInstance().getRecordingList(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, 0, pageSize, "CallSid:desc", true);
        assertEquals("CAff9ce46f104f4beeb10c83a5dad2bf66", ((JsonObject)response.get("recordings").getAsJsonArray().get(0)).get("call_sid").getAsString());
    }
    
    @Deployment(name = "RecordingEndpointTest", managed = true, testable = false)
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
