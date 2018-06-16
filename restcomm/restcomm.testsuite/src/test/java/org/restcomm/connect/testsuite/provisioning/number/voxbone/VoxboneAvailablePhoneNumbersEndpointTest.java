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

package org.restcomm.connect.testsuite.provisioning.number.voxbone;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.Assert.assertTrue;

import java.net.URL;

import junit.framework.Assert;
import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.Deployer;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.junit.Rule;
import org.junit.Test;
import org.junit.FixMethodOrder;
import org.junit.runners.MethodSorters;
import org.junit.runner.RunWith;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.filter.HTTPBasicAuthFilter;
import org.restcomm.connect.commons.Version;

/**
 * @author <a href="mailto:jean.deruelle@telestax.com">Jean Deruelle</a>
 */

@RunWith(Arquillian.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class VoxboneAvailablePhoneNumbersEndpointTest {
    private final static Logger logger = Logger.getLogger(VoxboneAvailablePhoneNumbersEndpointTest.class.getName());

    private static final String version = Version.getVersion();

    @ArquillianResource
    private Deployer deployer;
    @ArquillianResource
    URL deploymentUrl;
    static boolean accountUpdated = false;

    private String adminUsername = "administrator@company.com";
    private String adminAccountSid = "ACae6e420f425248d6a26948c17a9e2acf";
    private String adminAuthToken = "77f8c12cc7b8f8423e5c38b035249166";
    private String baseURL = "2012-04-24/Accounts/" + adminAccountSid + "/AvailablePhoneNumbers/";

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(8090); // No-args constructor defaults to port 8080

    /*
     * https://www.twilio.com/docs/api/rest/available-phone-numbers#local-get-basic-example-1
     * available local phone numbers in the United States in the 510 area code.
     */
    @Test
    public void testSearchUSLocalPhoneNumbersWith501AreaCode() {
        stubFor(put(urlEqualTo("/test/configuration/voiceuri"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(VoxboneAvailablePhoneNumbersEndpointTestUtils.VoiceURIJSonResponse)));
        stubFor(get(urlEqualTo("/test/inventory/didgroup?countryCodeA3=USA&areaCode=501&pageNumber=0&pageSize=50"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(VoxboneAvailablePhoneNumbersEndpointTestUtils.body501AreaCode)));
        // Get Account using admin email address and user email address
        Client jerseyClient = Client.create();
        jerseyClient.addFilter(new HTTPBasicAuthFilter(adminUsername, adminAuthToken));

        String provisioningURL = deploymentUrl + baseURL + "US/Local.json";
        WebResource webResource = jerseyClient.resource(provisioningURL);

        ClientResponse clientResponse = webResource.
                queryParam("AreaCode","501").
                accept("application/json")
                .get(ClientResponse.class);
        Assert.assertEquals(200, clientResponse.getStatus());
        String response = clientResponse.getEntity(String.class);
        System.out.println(response);
        assertTrue(!response.trim().equalsIgnoreCase("[]"));
        JsonParser parser = new JsonParser();
        JsonArray jsonResponse = parser.parse(response).getAsJsonArray();

        System.out.println(jsonResponse);

        assertTrue(jsonResponse.size() == 15);
        System.out.println((jsonResponse.get(0).getAsJsonObject().toString()));
        assertTrue(jsonResponse.get(0).getAsJsonObject().toString().equalsIgnoreCase(VoxboneAvailablePhoneNumbersEndpointTestUtils.firstJSonResult501AreaCode));
    }

    /*
     * Testing https://docs.nexmo.com/index.php/developer-api/number-search Example 1
     */
    @Test
    public void testSearchESPhoneNumbers700Pattern() {
        stubFor(put(urlEqualTo("/test/configuration/voiceuri"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(VoxboneAvailablePhoneNumbersEndpointTestUtils.VoiceURIJSonResponse)));
        stubFor(get(urlEqualTo("/test/inventory/didgroup?countryCodeA3=ESP&pageNumber=0&pageSize=50"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(VoxboneAvailablePhoneNumbersEndpointTestUtils.jsonResponseES700)));
    	Client jerseyClient = Client.create();
        jerseyClient.addFilter(new HTTPBasicAuthFilter(adminUsername, adminAuthToken));

        String provisioningURL = deploymentUrl + baseURL + "ES/Local.json";
        WebResource webResource = jerseyClient.resource(provisioningURL);

        ClientResponse clientResponse = webResource.queryParam("Contains","700").accept("application/json")
                .get(ClientResponse.class);
        assertTrue(clientResponse.getStatus() == 200);
        String response = clientResponse.getEntity(String.class);
        System.out.println(response);
        assertTrue(!response.trim().equalsIgnoreCase("[]"));
        JsonParser parser = new JsonParser();
        JsonArray jsonResponse = parser.parse(response).getAsJsonArray();

        System.out.println(jsonResponse);

        assertTrue(jsonResponse.size() == 48);
        System.out.println((jsonResponse.get(0).getAsJsonObject().toString()));
        assertTrue(jsonResponse.get(0).getAsJsonObject().toString().equalsIgnoreCase(VoxboneAvailablePhoneNumbersEndpointTestUtils.jsonResultES700));
    }

    /*
     * Testing https://docs.nexmo.com/index.php/developer-api/number-search Example 2
     */
    @Test
    public void testSearchUSPhoneNumbersRangeIndexAndSize() {
        stubFor(put(urlEqualTo("/test/configuration/voiceuri"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(VoxboneAvailablePhoneNumbersEndpointTestUtils.VoiceURIJSonResponse)));
        stubFor(get(urlEqualTo("/test/inventory/didgroup?countryCodeA3=USA&pageNumber=2&pageSize=5"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(VoxboneAvailablePhoneNumbersEndpointTestUtils.jsonResponseUSRange)));
        // Get Account using admin email address and user email address
        Client jerseyClient = Client.create();
        jerseyClient.addFilter(new HTTPBasicAuthFilter(adminUsername, adminAuthToken));

        String provisioningURL = deploymentUrl + baseURL + "US/Local.json";
        WebResource webResource = jerseyClient.resource(provisioningURL);

        ClientResponse clientResponse = webResource.queryParam("RangeSize","5").queryParam("RangeIndex","2").accept("application/json")
                .get(ClientResponse.class);
        assertTrue(clientResponse.getStatus() == 200);
        String response = clientResponse.getEntity(String.class);
        System.out.println(response);
        assertTrue(!response.trim().equalsIgnoreCase("[]"));
        JsonParser parser = new JsonParser();
        JsonArray jsonResponse = parser.parse(response).getAsJsonArray();

        System.out.println(jsonResponse);

        assertTrue(jsonResponse.size() == 5);
        System.out.println((jsonResponse.get(0).getAsJsonObject().toString()));
        assertTrue(jsonResponse.get(0).getAsJsonObject().toString().equalsIgnoreCase(VoxboneAvailablePhoneNumbersEndpointTestUtils.jsonResultUSRange));
    }

    @Deployment(name = "VoxboneAvailablePhoneNumbersEndpointTest", managed = true, testable = false)
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
        archive.addAsWebInfResource("restcomm_voxbone_test.xml", "conf/restcomm.xml");
        archive.addAsWebInfResource("restcomm.script_dialTest", "data/hsql/restcomm.script");
        logger.info("Packaged Test App");
        return archive;
    }

}
