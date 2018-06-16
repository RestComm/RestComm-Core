/*
 * TeleStax, Open Source Cloud Communications
 * Copyright 2011-2015, Telestax Inc and individual contributors
 * by the @authors tag.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.restcomm.connect.testsuite.http;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Locale;

import javax.ws.rs.core.MultivaluedMap;

import junit.framework.Assert;
import org.apache.http.client.ClientProtocolException;
import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.Deployer;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.junit.After;
import org.junit.Test;
import org.junit.FixMethodOrder;
import org.junit.runners.MethodSorters;
import org.junit.runner.RunWith;
import org.restcomm.connect.commons.Version;
import org.restcomm.connect.commons.dao.Sid;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.jersey.core.util.MultivaluedMapImpl;
import org.junit.experimental.categories.Category;
import org.restcomm.connect.commons.annotations.UnstableTests;

/**
 * @author guilherme.jansen@telestax.com
 */
@RunWith(Arquillian.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class ApplicationsEndpointTest {

    private final static Logger logger = Logger.getLogger(ApplicationsEndpointTest.class);
    private static final String version = Version.getVersion();

    @ArquillianResource
    private Deployer deployer;
    @ArquillianResource
    URL deploymentUrl;

    private String adminUsername = "administrator@company.com";
    private String adminAccountSid = "ACae6e420f425248d6a26948c17a9e2acf";
    private String adminAuthToken = "77f8c12cc7b8f8423e5c38b035249166";

    @After
    public void after() throws InterruptedException {
        Thread.sleep(1000);
    }

    @Test
    @Category(UnstableTests.class)
    public void testCreateAndGetApplication() throws ParseException, IllegalArgumentException, ClientProtocolException,
            IOException {
        // Define application attributes
        String friendlyName, voiceCallerIdLookup, rcmlUrl, kind;

        // Test create application via POST
        MultivaluedMap<String, String> applicationParams = new MultivaluedMapImpl();
        applicationParams.add("FriendlyName", friendlyName = "APPCreateGet");
        applicationParams.add("VoiceCallerIdLookup", voiceCallerIdLookup = "true");
        applicationParams.add("RcmlUrl", rcmlUrl = "/restcomm/rcmlurl/test");
        applicationParams.add("Kind", kind = "voice");
        JsonObject applicationJson = RestcommApplicationsTool.getInstance().createApplication(deploymentUrl.toString(),
                adminAccountSid, adminUsername, adminAuthToken, applicationParams);
        Sid applicationSid = new Sid(applicationJson.get("sid").getAsString());

        // Test asserts via GET to a single application
        applicationJson = RestcommApplicationsTool.getInstance().getApplication(deploymentUrl.toString(), adminUsername,
                adminAuthToken, adminAccountSid, applicationSid.toString());

        SimpleDateFormat df = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss Z", Locale.US);
        assertTrue(df.parse(applicationJson.get("date_created").getAsString()) != null);
        assertTrue(df.parse(applicationJson.get("date_updated").getAsString()) != null);
        assertTrue(applicationJson.get("friendly_name").getAsString().equals(friendlyName));
        assertTrue(applicationJson.get("account_sid").getAsString().equals(adminAccountSid));
        assertTrue(applicationJson.get("api_version").getAsString().equals("2012-04-24"));
        assertTrue(applicationJson.get("voice_caller_id_lookup").getAsString().equals(voiceCallerIdLookup));
        assertTrue(applicationJson.get("rcml_url").getAsString().equals(rcmlUrl));
        assertTrue(applicationJson.get("kind").getAsString().equals(kind));

        // Test asserts via GET to a application list
        JsonArray applicationsListJson = RestcommApplicationsTool.getInstance().getApplications(deploymentUrl.toString(),
                adminUsername, adminAuthToken, adminAccountSid);
        applicationJson = applicationsListJson.get(0).getAsJsonObject();
        assertTrue(df.parse(applicationJson.get("date_created").getAsString()) != null);
        assertTrue(df.parse(applicationJson.get("date_updated").getAsString()) != null);
        assertTrue(applicationJson.get("friendly_name").getAsString().equals(friendlyName));
        assertTrue(applicationJson.get("account_sid").getAsString().equals(adminAccountSid));
        assertTrue(applicationJson.get("api_version").getAsString().equals("2012-04-24"));
        assertTrue(applicationJson.get("voice_caller_id_lookup").getAsString().equals(voiceCallerIdLookup));
        assertTrue(applicationJson.get("rcml_url").getAsString().equals(rcmlUrl));
        assertTrue(applicationJson.get("kind").getAsString().equals(kind));
    }

    @Test
    public void testGetApplicationAndNumbers() throws ParseException, IllegalArgumentException, IOException {
        // Define application attributes
        String friendlyName, voiceCallerIdLookup, rcmlUrl, kind;

        // Test asserts via GET to a single application
        JsonArray applicationJson = RestcommApplicationsTool.getInstance().getApplications(deploymentUrl.toString(), adminUsername,
                adminAuthToken, adminAccountSid, true);
        Assert.assertNotNull(applicationJson.get(0).getAsJsonObject().get("numbers"));
        JsonObject number = applicationJson.get(0).getAsJsonObject().get("numbers").getAsJsonArray().get(0).getAsJsonObject();
        Assert.assertEquals("+1240",number.get("phone_number").getAsString());
        Assert.assertEquals("AP73926e7113fa4d95981aa96b76eca854", number.get("voice_application_sid").getAsString());
    }

    @Test
    public void testUpdateApplication() throws ParseException, IllegalArgumentException, ClientProtocolException, IOException {
        // Create application
        MultivaluedMap<String, String> applicationParams = new MultivaluedMapImpl();
        applicationParams.add("FriendlyName", "APPUpdate");
        applicationParams.add("VoiceCallerIdLookup", "true");
        applicationParams.add("RcmlUrl", "/restcomm/rcmlurl/test");
        applicationParams.add("Kind", "voice");
        JsonObject applicationJson = RestcommApplicationsTool.getInstance().createApplication(deploymentUrl.toString(),
                adminAccountSid, adminUsername, adminAuthToken, applicationParams);
        Sid applicationSid = new Sid(applicationJson.get("sid").getAsString());

        // Define new values to the application attributes (POST test)
        String friendlyName, voiceCallerIdLookup, rcmlUrl, kind;

        MultivaluedMap<String, String> applicationParamsUpdate = new MultivaluedMapImpl();
        applicationParamsUpdate.add("FriendlyName", friendlyName = "APPUpdate2");
        applicationParamsUpdate.add("VoiceCallerIdLookup", voiceCallerIdLookup = "false");
        applicationParamsUpdate.add("RcmlUrl", rcmlUrl = "/restcomm/rcmlurl/test2");
        applicationParamsUpdate.add("Kind", kind = "voice");

        // Update application via POST
        RestcommApplicationsTool.getInstance().updateApplication(deploymentUrl.toString(), adminUsername, adminAuthToken,
                adminAccountSid, applicationSid.toString(), applicationParamsUpdate, false);

        // Test asserts via GET to a single application
        applicationJson = RestcommApplicationsTool.getInstance().getApplication(deploymentUrl.toString(), adminUsername,
                adminAuthToken, adminAccountSid, applicationSid.toString());

        SimpleDateFormat df = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss Z", Locale.US);
        assertTrue(df.parse(applicationJson.get("date_created").getAsString()) != null);
        assertTrue(df.parse(applicationJson.get("date_updated").getAsString()) != null);
        assertTrue(applicationJson.get("friendly_name").getAsString().equals(friendlyName));
        assertTrue(applicationJson.get("account_sid").getAsString().equals(adminAccountSid));
        assertTrue(applicationJson.get("api_version").getAsString().equals("2012-04-24"));
        assertTrue(applicationJson.get("voice_caller_id_lookup").getAsString().equals(voiceCallerIdLookup));
        assertTrue(applicationJson.get("rcml_url").getAsString().equals(rcmlUrl));
        assertTrue(applicationJson.get("kind").getAsString().equals(kind));

        // Define new values to the application attributes (PUT test)
        applicationParamsUpdate = new MultivaluedMapImpl();
        applicationParamsUpdate.add("FriendlyName", friendlyName = "APPUpdate23");
        applicationParamsUpdate.add("VoiceCallerIdLookup", voiceCallerIdLookup = "true");
        applicationParamsUpdate.add("RcmlUrl", rcmlUrl = "/restcomm/rcmlurl/test23");
        applicationParamsUpdate.add("Kind", kind = "voice");

        // Update application via PUT
        RestcommApplicationsTool.getInstance().updateApplication(deploymentUrl.toString(), adminUsername, adminAuthToken,
                adminAccountSid, applicationSid.toString(), applicationParamsUpdate, true);

        // Test asserts via GET to a single application
        applicationJson = RestcommApplicationsTool.getInstance().getApplication(deploymentUrl.toString(), adminUsername,
                adminAuthToken, adminAccountSid, applicationSid.toString());

        assertTrue(df.parse(applicationJson.get("date_created").getAsString()) != null);
        assertTrue(df.parse(applicationJson.get("date_updated").getAsString()) != null);
        assertTrue(applicationJson.get("friendly_name").getAsString().equals(friendlyName));
        assertTrue(applicationJson.get("account_sid").getAsString().equals(adminAccountSid));
        assertTrue(applicationJson.get("api_version").getAsString().equals("2012-04-24"));
        assertTrue(applicationJson.get("voice_caller_id_lookup").getAsString().equals(voiceCallerIdLookup));
        assertTrue(applicationJson.get("rcml_url").getAsString().equals(rcmlUrl));
        assertTrue(applicationJson.get("kind").getAsString().equals(kind));
    }

    @Test
    public void testDeleteApplication() throws IllegalArgumentException, ClientProtocolException, IOException {
        // Create application
        MultivaluedMap<String, String> applicationParams = new MultivaluedMapImpl();
        applicationParams.add("FriendlyName", "APPDelete");
        applicationParams.add("VoiceCallerIdLookup", "true");
        applicationParams.add("RcmlUrl", "/restcomm/rcmlurl/test");
        applicationParams.add("Kind", "voice");
        JsonObject applicationJson = RestcommApplicationsTool.getInstance().createApplication(deploymentUrl.toString(),
                adminAccountSid, adminUsername, adminAuthToken, applicationParams);
        Sid applicationSid = new Sid(applicationJson.get("sid").getAsString());

        // Delete application
        RestcommApplicationsTool.getInstance().deleteApplication(deploymentUrl.toString(), adminUsername, adminAuthToken,
                adminAccountSid, applicationSid.toString());

        // Check if it was removed
        applicationJson = RestcommApplicationsTool.getInstance().getApplication(deploymentUrl.toString(), adminUsername,
                adminAuthToken, adminAccountSid, applicationSid.toString());

        assertTrue(applicationJson == null);
    }

    @Deployment(name = "ApplicationsEndpointTest", managed = true, testable = false)
    public static WebArchive createWebArchiveNoGw() {
        logger.info("Packaging Test App");
        logger.info("version");
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
        archive.addAsWebInfResource("restcomm.script-applications", "data/hsql/restcomm.script");
        logger.info("Packaged Test App");
        return archive;
    }
}
