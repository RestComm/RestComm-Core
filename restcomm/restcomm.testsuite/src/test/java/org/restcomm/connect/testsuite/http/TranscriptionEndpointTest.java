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
public class TranscriptionEndpointTest extends EndpointTest{
    private static Logger logger = Logger.getLogger(TranscriptionEndpointTest.class);

    private String adminAccountSid = "ACae6e420f425248d6a26948c17a9e2acf";
    private String adminAuthToken = "77f8c12cc7b8f8423e5c38b035249166";

    @Test
    public void getTranscriptionList() {
        JsonObject firstPage = TranscriptionEndpointTool.getInstance().getTranscriptionList(deploymentUrl.toString(), adminAccountSid,
                adminAuthToken);
        int totalSize = firstPage.get("total").getAsInt();
        JsonArray firstPageTranscriptionsArray = firstPage.get("transcriptions").getAsJsonArray();
        int firstPageTranscriptionsArraySize = firstPageTranscriptionsArray.size();
        assertTrue(firstPageTranscriptionsArraySize == 34);
        assertTrue(firstPage.get("start").getAsInt() == 0);
        assertTrue(firstPage.get("end").getAsInt() == 34);
        assertTrue(totalSize == 34);
    }

    @Test
    @Category(FeatureAltTests.class)
    public void getTranscriptionListUsingPageSize() {
        JsonObject firstPage = TranscriptionEndpointTool.getInstance().getTranscriptionList(deploymentUrl.toString(), adminAccountSid,
                adminAuthToken, null, 10, true);
        JsonArray firstPageTranscriptionsArray = firstPage.get("transcriptions").getAsJsonArray();
        assertTrue(firstPageTranscriptionsArray.size() == 10);
        assertTrue(firstPage.get("start").getAsInt() == 0);
        assertTrue(firstPage.get("end").getAsInt() == 9);

        JsonObject secondPage = (JsonObject) TranscriptionEndpointTool.getInstance().getTranscriptionList(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, 2, 10, true);
        JsonArray secondPageTranscriptionsArray = secondPage.get("transcriptions").getAsJsonArray();
        assertTrue(secondPageTranscriptionsArray.size() == 10);
        assertTrue(secondPage.get("start").getAsInt() == 20);
        assertTrue(secondPage.get("end").getAsInt() == 29);

        JsonObject lastPage = (JsonObject) TranscriptionEndpointTool.getInstance().getTranscriptionList(deploymentUrl.toString(), adminAccountSid,
        adminAuthToken, firstPage.get("num_pages").getAsInt(), 10, true);
        JsonArray lastPageTranscriptionsArray = lastPage.get("transcriptions").getAsJsonArray();
        assertTrue(lastPageTranscriptionsArray.size() == 4);
        assertTrue(lastPage.get("start").getAsInt() == 30);
        assertTrue(lastPage.get("end").getAsInt() == 34);
    }

    @Test
    @Category(FeatureAltTests.class)
    public void getTranscriptionListFilteredByStartTime() {
        Map<String, String> filters = new HashMap<String, String>();
        filters.put("StartTime", "2013-11-30 16:28:33.403000000");

        JsonObject filteredTranscriptionsByStartTime = TranscriptionEndpointTool.getInstance().getTranscriptionListUsingFilter(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, filters);

        assertTrue(filteredTranscriptionsByStartTime.get("transcriptions").getAsJsonArray().size() == 20);
        assertTrue(filteredTranscriptionsByStartTime.get("start").getAsInt() == 0);
        assertTrue(filteredTranscriptionsByStartTime.get("end").getAsInt() == 20);
    }

    @Test
    @Category(FeatureAltTests.class)
    public void getTranscriptionListFilteredByEndTime() {
        Map<String, String> filters = new HashMap<String, String>();
        filters.put("EndTime", "2013-10-30 16:28:33.403000000");

        JsonObject filteredTranscriptionsByEndTime = TranscriptionEndpointTool.getInstance().getTranscriptionListUsingFilter(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, filters);

        assertTrue(filteredTranscriptionsByEndTime.get("transcriptions").getAsJsonArray().size() == 14);
        assertTrue(filteredTranscriptionsByEndTime.get("start").getAsInt() == 0);
        assertTrue(filteredTranscriptionsByEndTime.get("end").getAsInt() == 14);
    }

    @Test
    @Category(FeatureAltTests.class)
    public void getTranscriptionListFilteredByTranscriptionText() {
        Map<String, String> filters = new HashMap<String, String>();
        filters.put("TranscriptionText", "RestComm");

        JsonObject filteredTranscriptionsByTranscriptionText = TranscriptionEndpointTool.getInstance().getTranscriptionListUsingFilter(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, filters);

        assertTrue(filteredTranscriptionsByTranscriptionText.get("transcriptions").getAsJsonArray().size() == 13);
        assertTrue(filteredTranscriptionsByTranscriptionText.get("start").getAsInt() == 0);
        assertTrue(filteredTranscriptionsByTranscriptionText.get("end").getAsInt() == 13);
    }

    @Deployment(name = "TranscriptionEndpointTest", managed = true, testable = false)
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
