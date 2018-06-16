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

package org.restcomm.connect.testsuite.provisioning.number.nexmo;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
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
public class NexmoAvailablePhoneNumbersEndpointTest {
    private final static Logger logger = Logger.getLogger(NexmoAvailablePhoneNumbersEndpointTest.class.getName());

    private static final String version = Version.getVersion();
            //"7.4.0-SNAPSHOT";

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
     * Testing https://docs.nexmo.com/index.php/developer-api/number-search Example 1
     */
    @Test
    public void testSearchESPhoneNumbers700Pattern() {
        stubFor(get(urlMatching("/nexmo/number/search/.*/.*/ES\\?pattern=.*700.*"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/json")
                    .withBody(NexmoAvailablePhoneNumbersEndpointTestUtils.jsonResponseES700)));
        // Get Account using admin email address and user email address
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
        
        assertTrue(jsonResponse.size() == 1);
        System.out.println((jsonResponse.get(0).getAsJsonObject().toString()));
        assertTrue(jsonResponse.get(0).getAsJsonObject().toString().equalsIgnoreCase(NexmoAvailablePhoneNumbersEndpointTestUtils.jsonResultES700));
    }
    
    /*
     * Testing https://docs.nexmo.com/index.php/developer-api/number-search Example 2
     */
    @Test
    public void testSearchUSPhoneNumbersRangeIndexAndSize() {
        stubFor(get(urlMatching("/nexmo/number/search/.*/.*/US\\?index=2&size=5"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/json")
                    .withBody(NexmoAvailablePhoneNumbersEndpointTestUtils.jsonResponseUSRange)));
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
        assertTrue(jsonResponse.get(0).getAsJsonObject().toString().equalsIgnoreCase(NexmoAvailablePhoneNumbersEndpointTestUtils.jsonResultUSRange));
    }
    
    /*
     * https://www.twilio.com/docs/api/rest/available-phone-numbers#local-get-basic-example-1
     * available local phone numbers in the United States in the 510 area code.
     */
    @Test
    @Category(FeatureAltTests.class)
    public void testSearchUSLocalPhoneNumbersWith501AreaCode() {
        stubFor(get(urlMatching("/nexmo/number/search/.*/.*/US\\?pattern=1501&search_pattern=0"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/json")
                    .withBody(NexmoAvailablePhoneNumbersEndpointTestUtils.body501AreaCode)));
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
        
        assertTrue(jsonResponse.size() == 6);
        System.out.println((jsonResponse.get(0).getAsJsonObject().toString()));
        assertTrue(jsonResponse.get(0).getAsJsonObject().toString().equalsIgnoreCase(NexmoAvailablePhoneNumbersEndpointTestUtils.firstJSonResult501AreaCode));
    }
    
    /*
     * https://www.twilio.com/docs/api/rest/available-phone-numbers#local-get-basic-example-1
     * available local phone numbers in the United States in the 510 area code.
     */
    @Test
    @Category(FeatureAltTests.class)
    public void testSearchCALocalPhoneNumbersWith450AreaCode() {
        stubFor(get(urlMatching("/nexmo/number/search/.*/.*/CA\\?pattern=1450&search_pattern=0"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/json")
                    .withBody(NexmoAvailablePhoneNumbersEndpointTestUtils.bodyCA450AreaCode)));
        // Get Account using admin email address and user email address
        Client jerseyClient = Client.create();
        jerseyClient.addFilter(new HTTPBasicAuthFilter(adminUsername, adminAuthToken));

        String provisioningURL = deploymentUrl + baseURL + "CA/Local.json";
        WebResource webResource = jerseyClient.resource(provisioningURL);

        ClientResponse clientResponse = webResource.queryParam("AreaCode","450").accept("application/json")
                .get(ClientResponse.class);
        assertTrue(clientResponse.getStatus() == 200);
        String response = clientResponse.getEntity(String.class);
        System.out.println(response);
        assertTrue(!response.trim().equalsIgnoreCase("[]"));
        JsonParser parser = new JsonParser();
        JsonArray jsonResponse = parser.parse(response).getAsJsonArray();
        
        System.out.println(jsonResponse);
        
        assertTrue(jsonResponse.size() == 10);
        System.out.println((jsonResponse.get(0).getAsJsonObject().toString()));
        assertTrue(jsonResponse.get(0).getAsJsonObject().toString().equalsIgnoreCase(NexmoAvailablePhoneNumbersEndpointTestUtils.firstJSonResultCA450AreaCode));
    }
    
    /*
     * 
     */
    @Test
    public void testSearchCALocalPhoneNumbersWithContainsAreaCode() {
        stubFor(get(urlMatching("/nexmo/number/search/.*/.*/CA\\?pattern=418&search_pattern=1"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/json")
                    .withBody(NexmoAvailablePhoneNumbersEndpointTestUtils.bodyCA418AreaCode)));
        // Get Account using admin email address and user email address
        Client jerseyClient = Client.create();
        jerseyClient.addFilter(new HTTPBasicAuthFilter(adminUsername, adminAuthToken));

        String provisioningURL = deploymentUrl + baseURL + "CA/Local.json";
        WebResource webResource = jerseyClient.resource(provisioningURL);

        ClientResponse clientResponse = webResource.queryParam("Contains","418").accept("application/json")
                .get(ClientResponse.class);
        assertTrue(clientResponse.getStatus() == 200);
        String response = clientResponse.getEntity(String.class);
        System.out.println(response);
        assertTrue(!response.trim().equalsIgnoreCase("[]"));
        JsonParser parser = new JsonParser();
        JsonArray jsonResponse = parser.parse(response).getAsJsonArray();
        
        System.out.println(jsonResponse);
        
        assertTrue(jsonResponse.size() == 10);
        System.out.println((jsonResponse.get(0).getAsJsonObject().toString()));
        assertTrue(jsonResponse.get(0).getAsJsonObject().toString().equalsIgnoreCase(NexmoAvailablePhoneNumbersEndpointTestUtils.firstJSonResultCA418AreaCode));
    }
    
//    @Test
//    public void testSearchUSPhoneNumbersSMSOnly() {
////        stubFor(get(urlMatching("/nexmo/number/search/.*/.*/US\\?index=2&size=5"))
////                .willReturn(aResponse()
////                    .withStatus(200)
////                    .withHeader("Content-Type", "text/json")
////                    .withBody(NexmoAvailablePhoneNumbersEndpointTestUtils.jsonResponseUSRange)));
//        // Get Account using admin email address and user email address
//        Client jerseyClient = Client.create();
//        jerseyClient.addFilter(new HTTPBasicAuthFilter(adminUsername, adminAuthToken));
//
//        String provisioningURL = deploymentUrl + baseURL + "SK/Local.json";
//        WebResource webResource = jerseyClient.resource(provisioningURL);
//
//        ClientResponse clientResponse = webResource.queryParam("SmsEnabled","true").accept("application/json")
//                .get(ClientResponse.class);
//        assertTrue(clientResponse.getStatus() == 200);
//        String response = clientResponse.getEntity(String.class);
//        System.out.println(response);
//        assertTrue(!response.trim().equalsIgnoreCase("[]"));
//        JsonParser parser = new JsonParser();
//        JsonArray jsonResponse = parser.parse(response).getAsJsonArray();
//        
//        System.out.println(jsonResponse);
//        
//        assertTrue(jsonResponse.size() == 5);
//        System.out.println((jsonResponse.get(0).getAsJsonObject().toString()));
//        assertTrue(jsonResponse.get(0).getAsJsonObject().toString().equalsIgnoreCase(NexmoAvailablePhoneNumbersEndpointTestUtils.jsonResultUSRange));
//    }
  
    @Deployment(name = "NexmoAvailablePhoneNumbersEndpointTest", managed = true, testable = false)
    public static WebArchive createWebArchiveNoGw() {
        logger.info("Packaging Test App");
        logger.info("version " + version);
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
        archive.addAsWebInfResource("restcomm_nexmo_test.xml", "conf/restcomm.xml");
        archive.addAsWebInfResource("restcomm.script_dialTest", "data/hsql/restcomm.script");
        logger.info("Packaged Test App");
        return archive;
    }

}
