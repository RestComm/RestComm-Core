/*
 *  TeleStax, Open Source Cloud Communications
 *  Copyright 2011-2018, Telestax Inc and individual contributors
 *  by the @authors tag.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  under the terms of the GNU Affero General Public License as
 *  published by the Free Software Foundation; either version 3 of
 *  the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>
 */

package org.restcomm.connect.testsuite.http;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.filter.HTTPBasicAuthFilter;
import com.sun.jersey.core.util.MultivaluedMapImpl;
import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.Deployer;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.junit.FixMethodOrder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.restcomm.connect.commons.Version;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import java.net.URL;

import static org.junit.Assert.assertTrue;

/**
 * @author gvagenas (gvagenas@gmail.com)
 */

@RunWith(Arquillian.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class IncomingPhoneNumbersEndpointTest {
    private final static Logger logger = Logger.getLogger(IncomingPhoneNumbersEndpointTest.class.getName());

    private static final String version = Version.getVersion();

    @ArquillianResource
    URL deploymentUrl;

    private String adminUsername = "administrator@company.com";
    private String adminAccountSid = "ACae6e420f425248d6a26948c17a9e2acf";
    private String adminAuthToken = "77f8c12cc7b8f8423e5c38b035249166";
    private String baseURL = "2012-04-24/Accounts/" + adminAccountSid + "/";
    private String migrateURL = "2012-04-24/Accounts/ACae6e420f425248d6a26948c17a9e2acg/";
    private String adminOrg2Username = "administrator@org2.restcomm.com";
    private String adminOrg2AccountSid = "ACae6e420f425248d6a26948c17a9e2acg";
    private String adminOrg2AuthToken = "77f8c12cc7b8f8423e5c38b035249166";
    private String baseURLOrg2 = "2012-04-24/Accounts/" + adminOrg2AccountSid + "/";

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(8090); // No-args constructor defaults to port 8080

    String number1 = "9966";
    String number2 = "9977";

    @Test
    public void testCreateSipNumberOrg1 () {

        // Get Account using admin email address and user email address
        Client jerseyClient = Client.create();
        jerseyClient.addFilter(new HTTPBasicAuthFilter(adminUsername, adminAuthToken));

        String provisioningURL = deploymentUrl + baseURL + "IncomingPhoneNumbers.json";
        WebResource webResource = jerseyClient.resource(provisioningURL);

        MultivaluedMap<String, String> formData = new MultivaluedMapImpl();
        formData.add("PhoneNumber", number1);
        formData.add("VoiceUrl", "http://demo.telestax.com/docs/voice.xml");
        formData.add("FriendlyName", "My Company Line");
        formData.add("VoiceMethod", "GET");
        formData.add("isSIP", "true");
        ClientResponse clientResponse = webResource.type(MediaType.APPLICATION_FORM_URLENCODED_TYPE).accept("application/json").post(ClientResponse.class, formData);
        assertTrue(clientResponse.getStatus() == 200);
        String response = clientResponse.getEntity(String.class);
        logger.info(response);
        assertTrue(!response.trim().equalsIgnoreCase("[]"));
        JsonParser parser = new JsonParser();
        JsonObject jsonResponse = parser.parse(response).getAsJsonObject();

        logger.info(jsonResponse.toString());
    }

    @Test
    public void testCreateSipNumberOrg2 () {

        // Get Account using admin email address and user email address
        Client jerseyClient = Client.create();
        jerseyClient.addFilter(new HTTPBasicAuthFilter(adminOrg2Username, adminOrg2AuthToken));

        String provisioningURL = deploymentUrl + baseURLOrg2 + "IncomingPhoneNumbers.json";
        WebResource webResource = jerseyClient.resource(provisioningURL);

        MultivaluedMap<String, String> formData = new MultivaluedMapImpl();
        formData.add("PhoneNumber", number1);
        formData.add("VoiceUrl", "http://demo.telestax.com/docs/voice.xml");
        formData.add("FriendlyName", "My Company Line");
        formData.add("VoiceMethod", "GET");
        formData.add("isSIP", "true");
        ClientResponse clientResponse = webResource.type(MediaType.APPLICATION_FORM_URLENCODED_TYPE).accept("application/json").post(ClientResponse.class, formData);
        assertTrue(clientResponse.getStatus() == 200);
        String response = clientResponse.getEntity(String.class);
        logger.info(response);
        assertTrue(!response.trim().equalsIgnoreCase("[]"));
        JsonParser parser = new JsonParser();
        JsonObject jsonResponse = parser.parse(response).getAsJsonObject();

        logger.info(jsonResponse.toString());
    }


    @Test
    public void testCreateSipNumberTwoOrgs () {

        // Get Account using admin email address and user email address
        Client jerseyClient = Client.create();
        jerseyClient.addFilter(new HTTPBasicAuthFilter(adminUsername, adminAuthToken));

        String provisioningURL = deploymentUrl + baseURL + "IncomingPhoneNumbers.json";
        WebResource webResource = jerseyClient.resource(provisioningURL);

        MultivaluedMap<String, String> formData = new MultivaluedMapImpl();
        formData.add("PhoneNumber", number2);
        formData.add("VoiceUrl", "http://demo.telestax.com/docs/voice.xml");
        formData.add("FriendlyName", "My Company Line");
        formData.add("VoiceMethod", "GET");
        formData.add("isSIP", "true");
        ClientResponse clientResponse = webResource.type(MediaType.APPLICATION_FORM_URLENCODED_TYPE).accept("application/json").post(ClientResponse.class, formData);
        assertTrue(clientResponse.getStatus() == 200);
        String response = clientResponse.getEntity(String.class);
        logger.info(response);
        assertTrue(!response.trim().equalsIgnoreCase("[]"));
        JsonParser parser = new JsonParser();
        JsonObject jsonResponse = parser.parse(response).getAsJsonObject();

        logger.info(jsonResponse.toString());


        Client jerseyClient2 = Client.create();
        jerseyClient2.addFilter(new HTTPBasicAuthFilter(adminOrg2Username, adminOrg2AuthToken));

        String provisioningURL2 = deploymentUrl + baseURLOrg2 + "IncomingPhoneNumbers.json";
        WebResource webResource2 = jerseyClient2.resource(provisioningURL2);

        MultivaluedMap<String, String> formData2 = new MultivaluedMapImpl();
        formData2.add("PhoneNumber", number2);
        formData2.add("VoiceUrl", "http://demo.telestax.com/docs/voice.xml");
        formData2.add("FriendlyName", "My Company Line");
        formData2.add("VoiceMethod", "GET");
        formData2.add("isSIP", "true");
        ClientResponse clientResponse2 = webResource2.type(MediaType.APPLICATION_FORM_URLENCODED_TYPE)
                .accept("application/json").post(ClientResponse.class, formData2);
        assertTrue(clientResponse2.getStatus() == 200);
        String response2 = clientResponse2.getEntity(String.class);
        logger.info(response2);
        assertTrue(!response2.trim().equalsIgnoreCase("[]"));
        JsonObject jsonResponse2 = parser.parse(response2).getAsJsonObject();

        logger.info(jsonResponse2.toString());

    }

    @Deployment(name = "IncomingPhoneNumbersEndpointTest", managed = true, testable = false)
    public static WebArchive createWebArchiveNoGw () {
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
        archive.addAsWebInfResource("restcomm.script_dialTest", "data/hsql/restcomm.script");
        logger.info("Packaged Test App");
        return archive;
    }
}
