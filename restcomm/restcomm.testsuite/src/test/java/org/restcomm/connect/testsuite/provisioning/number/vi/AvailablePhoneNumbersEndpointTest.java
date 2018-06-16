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

package org.restcomm.connect.testsuite.provisioning.number.vi;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
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
import org.junit.Rule;
import org.junit.Test;
import org.junit.FixMethodOrder;
import org.junit.runners.MethodSorters;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.filter.HTTPBasicAuthFilter;
import org.restcomm.connect.commons.Version;
import org.restcomm.connect.commons.annotations.FeatureAltTests;

/**
 * @author <a href="mailto:jean.deruelle@telestax.com">Jean Deruelle</a>
 */

@RunWith(Arquillian.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class AvailablePhoneNumbersEndpointTest {
    private final static Logger logger = Logger.getLogger(AvailablePhoneNumbersEndpointTest.class.getName());

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
    @Category(FeatureAltTests.class)
    public void testSearchUSLocalPhoneNumbersWith501AreaCode() {
        stubFor(post(urlEqualTo("/test"))
                .withRequestBody(containing("getDIDs"))
                .withRequestBody(containing("501"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/xml")
                    .withBody(AvailablePhoneNumbersEndpointTestUtils.body501AreaCode)));
        // Get Account using admin email address and user email address
    	Client jerseyClient = Client.create();
        jerseyClient.addFilter(new HTTPBasicAuthFilter(adminUsername, adminAuthToken));

        String provisioningURL = deploymentUrl + baseURL + "US/Local.json";
        WebResource webResource = jerseyClient.resource(provisioningURL);

        ClientResponse clientResponse = webResource.queryParam("AreaCode","501").accept("application/json")
                .get(ClientResponse.class);
        assertTrue(clientResponse.getStatus() == 200);
        String response = clientResponse.getEntity(String.class);
        System.out.println(response);
        assertTrue(!response.trim().equalsIgnoreCase("[]"));
        JsonParser parser = new JsonParser();
        JsonArray jsonResponse = parser.parse(response).getAsJsonArray();
        
        System.out.println(jsonResponse);
        
        assertTrue(jsonResponse.size() == 33);
        System.out.println((jsonResponse.get(0).getAsJsonObject().toString()));
        assertTrue(jsonResponse.get(0).getAsJsonObject().toString().equalsIgnoreCase(AvailablePhoneNumbersEndpointTestUtils.firstJSonResult501AreaCode));
    }

    /*
     * https://www.twilio.com/docs/api/rest/available-phone-numbers#local-get-basic-example-2
     * Find local phone numbers in the United States starting with 510555.
     */
    @Test
    @Category(FeatureAltTests.class)
    public void testSearchUSLocalPhoneNumbersWithPattern() {
        stubFor(post(urlEqualTo("/test"))
                .withRequestBody(containing("getDIDs"))
                .withRequestBody(containing("501"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/xml")
                    .withBody(AvailablePhoneNumbersEndpointTestUtils.body501AreaCode)));
        // Get Account using admin email address and user email address
        Client jerseyClient = Client.create();
        jerseyClient.addFilter(new HTTPBasicAuthFilter(adminUsername, adminAuthToken));

        String provisioningURL = deploymentUrl + baseURL + "US/Local.json";
        WebResource webResource = jerseyClient.resource(provisioningURL);

        ClientResponse clientResponse = webResource.queryParam("Contains","501555****").accept("application/json")
                .get(ClientResponse.class);
        assertTrue(clientResponse.getStatus() == 200);
        String response = clientResponse.getEntity(String.class);
        System.out.println(response);
        assertTrue(!response.trim().equalsIgnoreCase("[]"));
        JsonParser parser = new JsonParser();
        JsonArray jsonResponse = parser.parse(response).getAsJsonArray();
        
        System.out.println(jsonResponse);
        
        assertTrue(jsonResponse.size() == 2);
        System.out.println((jsonResponse.get(0).getAsJsonObject().toString()));
        assertTrue(jsonResponse.get(0).getAsJsonObject().toString().equalsIgnoreCase(AvailablePhoneNumbersEndpointTestUtils.firstJSonResult501ContainsPattern));
    }
    
    /*
     * https://www.twilio.com/docs/api/rest/available-phone-numbers#local-get-basic-example-3
     * Find local phone numbers that match the pattern 'STORM'.
     */
    @Test
    @Category(FeatureAltTests.class)
    public void testSearchUSLocalPhoneNumbersWithLetterPattern() {
        stubFor(post(urlEqualTo("/test"))
                .withRequestBody(containing("getDIDs"))
                .withRequestBody(containing("675"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/xml")
                    .withBody(AvailablePhoneNumbersEndpointTestUtils.body501AreaCode)));
        // Get Account using admin email address and user email address
        Client jerseyClient = Client.create();
        jerseyClient.addFilter(new HTTPBasicAuthFilter(adminUsername, adminAuthToken));

        String provisioningURL = deploymentUrl + baseURL + "US/Local.json";
        WebResource webResource = jerseyClient.resource(provisioningURL);

        ClientResponse clientResponse = webResource.queryParam("Contains","STORM").accept("application/json")
                .get(ClientResponse.class);
        assertTrue(clientResponse.getStatus() == 200);
        String response = clientResponse.getEntity(String.class);
        System.out.println(response);
        assertTrue(!response.trim().equalsIgnoreCase("[]"));
        JsonParser parser = new JsonParser();
        JsonArray jsonResponse = parser.parse(response).getAsJsonArray();
        
        System.out.println(jsonResponse);
        
        assertTrue(jsonResponse.size() == 1);
        System.out.println((jsonResponse.get(0).getAsJsonObject().toString()));
        assertTrue(jsonResponse.get(0).getAsJsonObject().toString().equalsIgnoreCase(AvailablePhoneNumbersEndpointTestUtils.firstJSonResult501ContainsLetterPattern));
    }

    /*
     * https://www.twilio.com/docs/api/rest/available-phone-numbers#local-get-basic-example-4
     * Find local phone numbers in Arkansas.
     */
    @Test
    public void testSearchUSLocalPhoneNumbersWithInRegionFilter() {
        stubFor(post(urlEqualTo("/test"))
                .withRequestBody(containing("getDIDs"))
                .withRequestBody(containing("501"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/xml")
                    .withBody(AvailablePhoneNumbersEndpointTestUtils.body501AreaCode)));
        // Get Account using admin email address and user email address
        Client jerseyClient = Client.create();
        jerseyClient.addFilter(new HTTPBasicAuthFilter(adminUsername, adminAuthToken));

        String provisioningURL = deploymentUrl + baseURL + "US/Local.json";
        WebResource webResource = jerseyClient.resource(provisioningURL);

        ClientResponse clientResponse = webResource.queryParam("AreaCode","501").queryParam("InRegion","AR").accept("application/json")
                .get(ClientResponse.class);
        assertTrue(clientResponse.getStatus() == 200);
        String response = clientResponse.getEntity(String.class);
        System.out.println(response);
        assertTrue(!response.trim().equalsIgnoreCase("[]"));
        JsonParser parser = new JsonParser();
        JsonArray jsonResponse = parser.parse(response).getAsJsonArray();
        
        System.out.println(jsonResponse);
        
        assertTrue(jsonResponse.size() == 1);
        System.out.println((jsonResponse.get(0).getAsJsonObject().toString()));
        assertTrue(jsonResponse.get(0).getAsJsonObject().toString().equalsIgnoreCase(AvailablePhoneNumbersEndpointTestUtils.firstJSonResult501InRegionPattern));
    }
    
    /*
     * https://www.twilio.com/docs/api/rest/available-phone-numbers#local-get-basic-example-5
     * Find a phone number in the London prefix (+4420) which is Fax-enabled.
     */
    @Test
    public void testSearchUKFaxEnabledFilter() {
        stubFor(post(urlEqualTo("/test"))
                .withRequestBody(containing("getDIDs"))
//                .withRequestBody(containing("501"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/xml")
                    .withBody(AvailablePhoneNumbersEndpointTestUtils.body501AreaCode)));
        // Get Account using admin email address and user email address
        Client jerseyClient = Client.create();
        jerseyClient.addFilter(new HTTPBasicAuthFilter(adminUsername, adminAuthToken));

        String provisioningURL = deploymentUrl + baseURL + "GB/Local.json";
        WebResource webResource = jerseyClient.resource(provisioningURL);

        ClientResponse clientResponse = webResource.queryParam("Contains","4420").queryParam("FaxEnabled","true").accept("application/json")
                .get(ClientResponse.class);
        assertTrue(clientResponse.getStatus() == 200);
        String response = clientResponse.getEntity(String.class);
        System.out.println(response);
        assertTrue(!response.trim().equalsIgnoreCase("[]"));
        JsonParser parser = new JsonParser();
        JsonArray jsonResponse = parser.parse(response).getAsJsonArray();
        
        System.out.println(jsonResponse);
        
        assertTrue(jsonResponse.size() == 1);
        System.out.println((jsonResponse.get(0).getAsJsonObject().toString()));
        assertTrue(jsonResponse.get(0).getAsJsonObject().toString().equalsIgnoreCase(AvailablePhoneNumbersEndpointTestUtils.firstJSonResultUKPattern));
    }
    
    /*
     * https://www.twilio.com/docs/api/rest/available-phone-numbers#local-get-basic-example-5
     * Find a phone number in the London prefix (+4420) which is Fax-enabled.
     */
    @Test
    public void testSearchAdvancedFilter() {
        stubFor(post(urlEqualTo("/test"))
                .withRequestBody(containing("getDIDs"))
//                .withRequestBody(containing("501"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/xml")
                    .withBody(AvailablePhoneNumbersEndpointTestUtils.body501AreaCode)));
        // Get Account using admin email address and user email address
        Client jerseyClient = Client.create();
        jerseyClient.addFilter(new HTTPBasicAuthFilter(adminUsername, adminAuthToken));

        String provisioningURL = deploymentUrl + baseURL + "US/Local.json";
        WebResource webResource = jerseyClient.resource(provisioningURL);

        ClientResponse clientResponse = webResource.queryParam("NearLatLong","37.840699%2C-122.461853").queryParam("Distance","50").queryParam("Contains","501").queryParam("InRegion","CA").accept("application/json")
                .get(ClientResponse.class);
        assertTrue(clientResponse.getStatus() == 200);
        String response = clientResponse.getEntity(String.class);
        System.out.println(response);
        assertTrue(!response.trim().equalsIgnoreCase("[]"));
        JsonParser parser = new JsonParser();
        JsonArray jsonResponse = parser.parse(response).getAsJsonArray();
        
        System.out.println(jsonResponse);
        
        assertTrue(jsonResponse.size() == 2);
        System.out.println((jsonResponse.get(0).getAsJsonObject().toString()));
        assertTrue(jsonResponse.get(0).getAsJsonObject().toString().equalsIgnoreCase(AvailablePhoneNumbersEndpointTestUtils.firstJSonResultAdvancedPattern));
    }
    
    /*
     * https://www.twilio.com/docs/api/rest/available-phone-numbers#toll-free-get-example-3
     * Find toll-free phone numbers in the 800 area code that contain the pattern 'STORM'.
     */
    @Test
    @Category(FeatureAltTests.class)
    public void testSearchUSTollFreePhoneNumbersWithLetterPattern() {
        stubFor(post(urlEqualTo("/test"))
                .withRequestBody(containing("getDIDs"))
                .withRequestBody(containing("675"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/xml")
                    .withBody(AvailablePhoneNumbersEndpointTestUtils.body501AreaCode)));
        // Get Account using admin email address and user email address
        Client jerseyClient = Client.create();
        jerseyClient.addFilter(new HTTPBasicAuthFilter(adminUsername, adminAuthToken));

        String provisioningURL = deploymentUrl + baseURL + "US/TollFree.json";
        WebResource webResource = jerseyClient.resource(provisioningURL);

        ClientResponse clientResponse = webResource.queryParam("Contains","STORM").accept("application/json")
                .get(ClientResponse.class);
        assertTrue(clientResponse.getStatus() == 200);
        String response = clientResponse.getEntity(String.class);
        System.out.println(response);
        assertTrue(!response.trim().equalsIgnoreCase("[]"));
        JsonParser parser = new JsonParser();
        JsonArray jsonResponse = parser.parse(response).getAsJsonArray();
        
        System.out.println(jsonResponse);
        
        assertTrue(jsonResponse.size() == 1);
        System.out.println((jsonResponse.get(0).getAsJsonObject().toString()));
        assertTrue(jsonResponse.get(0).getAsJsonObject().toString().equalsIgnoreCase(AvailablePhoneNumbersEndpointTestUtils.firstJSonResult501ContainsLetterPattern));
    }
    
    /*
     * https://www.twilio.com/docs/api/rest/available-phone-numbers#mobile-get-example-1
     * Find a phone number in the London prefix (+4420) which is Fax-enabled.
     */
    @Test
    @Category(FeatureAltTests.class)
    public void testSearchMobileUKFaxEnabledFilter() {
        stubFor(post(urlEqualTo("/test"))
                .withRequestBody(containing("getDIDs"))
//                .withRequestBody(containing("501"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/xml")
                    .withBody(AvailablePhoneNumbersEndpointTestUtils.body501AreaCode)));
        // Get Account using admin email address and user email address
        Client jerseyClient = Client.create();
        jerseyClient.addFilter(new HTTPBasicAuthFilter(adminUsername, adminAuthToken));

        String provisioningURL = deploymentUrl + baseURL + "GB/Mobile.json";
        WebResource webResource = jerseyClient.resource(provisioningURL);

        ClientResponse clientResponse = webResource.queryParam("Contains","4420").queryParam("FaxEnabled","true").accept("application/json")
                .get(ClientResponse.class);
        assertTrue(clientResponse.getStatus() == 200);
        String response = clientResponse.getEntity(String.class);
        System.out.println(response);
        assertTrue(!response.trim().equalsIgnoreCase("[]"));
        JsonParser parser = new JsonParser();
        JsonArray jsonResponse = parser.parse(response).getAsJsonArray();
        
        System.out.println(jsonResponse);
        
        assertTrue(jsonResponse.size() == 1);
        System.out.println((jsonResponse.get(0).getAsJsonObject().toString()));
        assertTrue(jsonResponse.get(0).getAsJsonObject().toString().equalsIgnoreCase(AvailablePhoneNumbersEndpointTestUtils.firstJSonResultUKPattern));
    }
    
    @Deployment(name = "AvailablePhoneNumbersEndpointTest", managed = true, testable = false)
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
        archive.addAsWebInfResource("restcomm_AvailablePhoneNumbers_Test.xml", "conf/restcomm.xml");
        archive.addAsWebInfResource("restcomm.script_dialTest", "data/hsql/restcomm.script");
        logger.info("Packaged Test App");
        return archive;
    }

}
