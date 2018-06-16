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

import static org.junit.Assert.assertTrue;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.Deployer;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.junit.Test;
import org.junit.FixMethodOrder;
import org.junit.runners.MethodSorters;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.restcomm.connect.commons.Version;
import org.restcomm.connect.commons.annotations.FeatureAltTests;

/**
 * @author Maria
 */

@RunWith(Arquillian.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class ConferenceEndpointTest {
    private final static Logger logger = Logger.getLogger(ConferenceEndpointTest.class.getName());

    private static final String version = Version.getVersion();

    @ArquillianResource
    private Deployer deployer;
    @ArquillianResource
    URL deploymentUrl;

    private String adminAccountSid = "ACae6e420f425248d6a26948c17a9e2acf";
    private String adminAuthToken = "77f8c12cc7b8f8423e5c38b035249166";

    @Deployment(name = "ClientsEndpointTest", managed = true, testable = false)
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
    
    @Test
    public void getConferences() {
        JsonObject page = RestcommConferenceTool.getInstance().getConferences(deploymentUrl.toString(), adminAccountSid,
                adminAuthToken);
        int totalSize = page.get("total").getAsInt();
        JsonArray firstPageConferenceArray = page.get("conferences").getAsJsonArray();
        int firstPageConferenceArraySize = firstPageConferenceArray.size();
        assertTrue(firstPageConferenceArraySize == 2);
        assertTrue(page.get("start").getAsInt() == 0);
        assertTrue(page.get("end").getAsInt() == 2);

        assertTrue(totalSize == 2);
    }

    @Test
    @Category(FeatureAltTests.class)
    public void getConferencesFilteredByStatus() {
        Map<String, String> filters = new HashMap<String, String>();
        filters.put("Status", "COMPLETED");

        JsonObject allConferencesObject = RestcommConferenceTool.getInstance().getConferences(deploymentUrl.toString(), adminAccountSid,
                adminAuthToken);

        JsonObject filteredConferencesByStatusObject = RestcommConferenceTool.getInstance().getConferencesUsingFilter(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, filters);

        assertTrue(filteredConferencesByStatusObject.get("conferences").getAsJsonArray().size() == 1);
        assertTrue(allConferencesObject.get("start").getAsInt() == 0);
        assertTrue(allConferencesObject.get("end").getAsInt() == 2);
        assertTrue(filteredConferencesByStatusObject.get("start").getAsInt() == 0);
        assertTrue(filteredConferencesByStatusObject.get("end").getAsInt() == 1);
        assertTrue(allConferencesObject.get("conferences").getAsJsonArray().size() > filteredConferencesByStatusObject.get("conferences")
                .getAsJsonArray().size());
    }

    @Test
    @Category(FeatureAltTests.class)
    public void getConferencesFilteredByFriendlyName() {
        Map<String, String> filters = new HashMap<String, String>();
        filters.put("FriendlyName", "1111");

        JsonObject allConferencesObject = RestcommConferenceTool.getInstance().getConferences(deploymentUrl.toString(), adminAccountSid,
                adminAuthToken);

        JsonObject filteredConferencesByFNObject = RestcommConferenceTool.getInstance().getConferencesUsingFilter(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, filters);

        assertTrue(filteredConferencesByFNObject.get("conferences").getAsJsonArray().size() == 2);
        assertTrue(allConferencesObject.get("start").getAsInt() == 0);
        assertTrue(allConferencesObject.get("end").getAsInt() == 2);
        assertTrue(filteredConferencesByFNObject.get("start").getAsInt() == 0);
        assertTrue(filteredConferencesByFNObject.get("end").getAsInt() == 2);
        assertTrue(allConferencesObject.get("conferences").getAsJsonArray().size() == filteredConferencesByFNObject.get("conferences")
                .getAsJsonArray().size());
    }

    @Test
    @Category(FeatureAltTests.class)
    public void getConferencesFilteredUsingMultipleFilters() {
        Map<String, String> filters = new HashMap<String, String>();
        filters.put("FriendlyName", "1111");
        filters.put("Status", "COMPLETED");

        JsonObject allConferencesObject = RestcommConferenceTool.getInstance().getConferences(deploymentUrl.toString(), adminAccountSid,
                adminAuthToken);

        JsonObject filteredConferencesByFNObject = RestcommConferenceTool.getInstance().getConferencesUsingFilter(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, filters);

        assertTrue(filteredConferencesByFNObject.get("conferences").getAsJsonArray().size() == 1);
        assertTrue(allConferencesObject.get("start").getAsInt() == 0);
        assertTrue(allConferencesObject.get("end").getAsInt() == 2);
        assertTrue(filteredConferencesByFNObject.get("start").getAsInt() == 0);
        assertTrue(filteredConferencesByFNObject.get("end").getAsInt() == 1);
        assertTrue(allConferencesObject.get("conferences").getAsJsonArray().size() > filteredConferencesByFNObject.get("conferences")
                .getAsJsonArray().size());
    }

}
