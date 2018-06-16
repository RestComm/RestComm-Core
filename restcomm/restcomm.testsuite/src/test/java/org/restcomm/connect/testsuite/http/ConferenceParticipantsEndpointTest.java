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

import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.Deployer;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.FixMethodOrder;
import org.junit.runners.MethodSorters;
import org.junit.runner.RunWith;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.restcomm.connect.commons.Version;

/**
 * @author maria
 */

@RunWith(Arquillian.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class ConferenceParticipantsEndpointTest {
    private final static Logger logger = Logger.getLogger(ConferenceParticipantsEndpointTest.class.getName());

    private static final String version = Version.getVersion();

    @ArquillianResource
    private Deployer deployer;
    @ArquillianResource
    URL deploymentUrl;

    private String adminAccountSid = "ACae6e420f425248d6a26948c17a9e2acf";
    private String adminAuthToken = "77f8c12cc7b8f8423e5c38b035249166";
    private String runningConferenceSid = "CFfb336a6d5c364a57ab365d80dc24538f";
    private String completedConferenceSid = "CF1e4a2e67ada54298a83b93818c0ea1e4";
    
    @Test
    public void getParticipantsListRunningConference() {
        JsonObject page = RestcommConferenceParticipantsTool.getInstance().getParticipants(deploymentUrl.toString(), adminAccountSid,
                adminAuthToken, runningConferenceSid);
        int totalSize = page.get("total").getAsInt();
        JsonArray pageCallsArray = page.get("calls").getAsJsonArray();
        int pageCallsArraySize = pageCallsArray.size();
        assertTrue(pageCallsArraySize == 3);
        assertTrue(page.get("start").getAsInt() == 0);
        assertTrue(page.get("end").getAsInt() == 3);

        assertTrue(totalSize == 3);
    }

    @Test
    public void getParticipantsListCompletedConference() {
        JsonObject page = RestcommConferenceParticipantsTool.getInstance().getParticipants(deploymentUrl.toString(), adminAccountSid,
                adminAuthToken, completedConferenceSid);
        int totalSize = page.get("total").getAsInt();
        JsonArray pageCallsArray = page.get("calls").getAsJsonArray();
        int pageCallsArraySize = pageCallsArray.size();
        assertTrue(pageCallsArraySize == 0);
        assertTrue(page.get("start").getAsInt() == 0);
        assertTrue(page.get("end").getAsInt() == 0);

        assertTrue(totalSize == 0);
    }

    @Test @Ignore //Pending issue https://github.com/RestComm/Restcomm-Connect/issues/1135
    public void muteParticipant() {
        JsonObject page = RestcommConferenceParticipantsTool.getInstance().getParticipants(deploymentUrl.toString(), adminAccountSid,
                adminAuthToken, runningConferenceSid);
        int totalSize = page.get("total").getAsInt();
        JsonArray pageCallsArray = page.get("calls").getAsJsonArray();
        int pageCallsArraySize = pageCallsArray.size();
        assertTrue(pageCallsArraySize == 0);
        assertTrue(page.get("start").getAsInt() == 0);
        assertTrue(page.get("end").getAsInt() == 0);

        assertTrue(totalSize == 0);
    }

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

}
