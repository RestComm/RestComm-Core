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

package org.restcomm.connect.testsuite.provisioning.number.bandwidth;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.filter.HTTPBasicAuthFilter;
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
import org.restcomm.connect.commons.Version;
import org.restcomm.connect.commons.annotations.FeatureAltTests;
import org.restcomm.connect.commons.annotations.FeatureExpTests;

import java.net.URL;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Created by sbarstow on 10/7/14.
 */
@RunWith(Arquillian.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class BandwidthAvailablePhoneNumbersEndpointTest {
    private final static Logger logger = Logger.getLogger(BandwidthAvailablePhoneNumbersEndpointTest.class.getName());

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

    @Test
    public void testReturnAreaCodeSearch(){
        stubFor(get(urlMatching("/v1.0/accounts/12345/availableNumbers.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/json")
                        .withBody(BandwidthAvailablePhoneNumbersEndpointTestUtils.areaCode201SearchResult)));

        Client jerseyClient = Client.create();
        jerseyClient.addFilter(new HTTPBasicAuthFilter(adminUsername, adminAuthToken));

        String provisioningURL = deploymentUrl + baseURL + "US/Local.json";
        WebResource webResource = jerseyClient.resource(provisioningURL);

        ClientResponse clientResponse = webResource.queryParam("AreaCode","201").accept("application/json")
                .get(ClientResponse.class);
        assertTrue(clientResponse.getStatus() == 200);
        String response = clientResponse.getEntity(String.class);
        System.out.println(response);

        JsonParser parser = new JsonParser();
        JsonArray jsonResponse = parser.parse(response).getAsJsonArray();

        System.out.println(jsonResponse);
        System.out.println(BandwidthAvailablePhoneNumbersEndpointTestUtils.firstJSonResult201AreaCode);

        assertTrue(jsonResponse.size() == 1);
        System.out.println((jsonResponse.get(0).getAsJsonObject().toString()));
        assertTrue(jsonResponse.get(0).getAsJsonObject().toString().equalsIgnoreCase(BandwidthAvailablePhoneNumbersEndpointTestUtils.firstJSonResult201AreaCode));
    }

    @Test
    @Category(FeatureExpTests.class)
    public void testSearchAreaCode205() {
        stubFor(get(urlMatching("/v1.0/accounts/12345/availableNumbers.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/json")
                        .withBody(BandwidthAvailablePhoneNumbersEndpointTestUtils.areaCode205SearchResult)));

        Client jerseyClient = Client.create();
        jerseyClient.addFilter(new HTTPBasicAuthFilter(adminUsername, adminAuthToken));

        String provisioningURL = deploymentUrl + baseURL + "US/Local.json";
        WebResource webResource = jerseyClient.resource(provisioningURL);

        ClientResponse clientResponse = webResource.queryParam("AreaCode","201").accept("application/json")
                .get(ClientResponse.class);
        assertTrue(clientResponse.getStatus() == 200);
        String response = clientResponse.getEntity(String.class);
        System.out.println(response);

        JsonParser parser = new JsonParser();
        JsonArray jsonResponse = parser.parse(response).getAsJsonArray();

        System.out.println(jsonResponse);

    }

    @Test
    public void testReturnZipCodeSearch(){
        stubFor(get(urlMatching("/v1.0/accounts/12345/availableNumbers.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/json")
                        .withBody(BandwidthAvailablePhoneNumbersEndpointTestUtils.zipCode27601SearchResult)));

        Client jerseyClient = Client.create();
        jerseyClient.addFilter(new HTTPBasicAuthFilter(adminUsername, adminAuthToken));

        String provisioningURL = deploymentUrl + baseURL + "US/Local.json";
        WebResource webResource = jerseyClient.resource(provisioningURL);

        ClientResponse clientResponse = webResource.queryParam("AreaCode","201").accept("application/json")
                .get(ClientResponse.class);
        assertTrue(clientResponse.getStatus() == 200);
        String response = clientResponse.getEntity(String.class);

        JsonParser parser = new JsonParser();
        JsonArray jsonResponse = parser.parse(response).getAsJsonArray();

        assertTrue(jsonResponse.size() == 1);
        assertTrue(jsonResponse.get(0).getAsJsonObject().toString().equalsIgnoreCase(BandwidthAvailablePhoneNumbersEndpointTestUtils.firstJSONResult27601ZipCode ));
    }


    @Test
    @Category(FeatureAltTests.class)
    public void testReturnEmptyResultsSearch() {
        stubFor(get(urlMatching("/v1.0/accounts/12345/availableNumbers.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/json")
                        .withBody(BandwidthAvailablePhoneNumbersEndpointTestUtils.emptySearchResult)));
        Client jerseyClient = Client.create();
        jerseyClient.addFilter(new HTTPBasicAuthFilter(adminUsername, adminAuthToken));

        String provisioningURL = deploymentUrl + baseURL + "US/Local.json";
        WebResource webResource = jerseyClient.resource(provisioningURL);

        ClientResponse clientResponse = webResource.accept("application/json")
                .get(ClientResponse.class);
        assertTrue(clientResponse.getStatus() == 200);
        String response = clientResponse.getEntity(String.class);

        JsonParser parser = new JsonParser();
        JsonArray jsonResponse = parser.parse(response).getAsJsonArray();

        assertTrue(jsonResponse.size() == 0);
    }


    @Test
    @Category(FeatureExpTests.class)
    public void testMalformedSearchResultXml() {
        stubFor(get(urlMatching("/v1.0/accounts/12345/availableNumbers.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/json")
                        .withBody(BandwidthAvailablePhoneNumbersEndpointTestUtils.emptySearchResult)));
        Client jerseyClient = Client.create();
        jerseyClient.addFilter(new HTTPBasicAuthFilter(adminUsername, adminAuthToken));

        String provisioningURL = deploymentUrl + baseURL + "US/Local.json";
        WebResource webResource = jerseyClient.resource(provisioningURL);

        ClientResponse clientResponse = webResource.accept("application/json")
                .get(ClientResponse.class);
        assertTrue(clientResponse.getStatus() == 200);
        String response = clientResponse.getEntity(String.class);

        JsonParser parser = new JsonParser();
        JsonArray jsonResponse = parser.parse(response).getAsJsonArray();

        assertTrue(jsonResponse.size() == 0);
    }

    @Test
    public void testSearchForTollFreeNumbers() {
        stubFor(get(urlMatching("/v1.0/accounts/12345/availableNumbers.*"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "text-json")
                .withBody(BandwidthAvailablePhoneNumbersEndpointTestUtils.validTollFreeSearchResult)));

        Client jerseyClient = Client.create();
        jerseyClient.addFilter(new HTTPBasicAuthFilter(adminUsername, adminAuthToken));

        String provisioningURL = deploymentUrl + baseURL + "US/TollFree.json";
        WebResource webResource = jerseyClient.resource(provisioningURL);

        ClientResponse clientResponse = webResource.queryParam("RangeSize","2").accept("application/json")
                .get(ClientResponse.class);
        assertTrue(clientResponse.getStatus() == 200);
        String response = clientResponse.getEntity(String.class);
        System.out.println(response);

        JsonParser parser = new JsonParser();
        JsonArray jsonResponse = parser.parse(response).getAsJsonArray();

        System.out.println(jsonResponse);

        assertTrue(jsonResponse.size() == 2);
        System.out.println(jsonResponse.get(0).getAsJsonObject().toString());
        assertTrue(jsonResponse.get(0).getAsJsonObject().toString().equalsIgnoreCase(BandwidthAvailablePhoneNumbersEndpointTestUtils.validTollFreeJsonResult));
    }

    @Test
    @Category(FeatureExpTests.class)
    public void testSearchForTollFreeNumbersInvalidPattern() {
        stubFor(get(urlMatching("/v1.0/accounts/12345/availableNumbers.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text-json")
                        .withBody(BandwidthAvailablePhoneNumbersEndpointTestUtils.invalidTollFreeSearchResult)));

        Client jerseyClient = Client.create();
        jerseyClient.addFilter(new HTTPBasicAuthFilter(adminUsername, adminAuthToken));

        String provisioningURL = deploymentUrl + baseURL + "US/TollFree.json";
        WebResource webResource = jerseyClient.resource(provisioningURL);

        ClientResponse clientResponse = webResource.queryParam("RangeSize","2").queryParam("Contains", "7**").accept("application/json")
                .get(ClientResponse.class);
        assertTrue(clientResponse.getStatus() == 200);
        String response = clientResponse.getEntity(String.class);

        JsonParser parser = new JsonParser();
        JsonArray jsonResponse = parser.parse(response).getAsJsonArray();
        System.out.println(jsonResponse);
        assertTrue(jsonResponse.size() == 0);
    }




    @Deployment(name = "BandwidthAvailablePhoneNumbersEndpointTest", managed = true, testable = false)
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
        archive.addAsWebInfResource("restcomm_bandwidth_test.xml", "conf/restcomm.xml");
        archive.addAsWebInfResource("restcomm.script_dialTest", "data/hsql/restcomm.script");
        logger.info("Packaged Test App");
        return archive;
    }


}
