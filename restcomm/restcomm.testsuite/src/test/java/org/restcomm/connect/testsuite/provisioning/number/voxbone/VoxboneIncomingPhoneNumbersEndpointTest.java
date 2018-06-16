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
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.put;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.junit.Assert.assertTrue;

import java.net.URL;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;

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
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.filter.HTTPBasicAuthFilter;
import com.sun.jersey.core.util.MultivaluedMapImpl;
import org.junit.experimental.categories.Category;
import org.restcomm.connect.commons.Version;
import org.restcomm.connect.commons.annotations.BrokenTests;
import org.restcomm.connect.commons.annotations.FeatureExpTests;
import org.restcomm.connect.commons.annotations.UnstableTests;

/**
 * @author <a href="mailto:jean.deruelle@telestax.com">Jean Deruelle</a>
 */

@RunWith(Arquillian.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class VoxboneIncomingPhoneNumbersEndpointTest {
    private final static Logger logger = Logger.getLogger(VoxboneIncomingPhoneNumbersEndpointTest.class.getName());

    private static final String version = Version.getVersion();

    @ArquillianResource
    private Deployer deployer;
    @ArquillianResource
    URL deploymentUrl;
    static boolean accountUpdated = false;

    private String adminUsername = "administrator@company.com";
    private String adminAccountSid = "ACae6e420f425248d6a26948c17a9e2acf";
    private String adminAuthToken = "77f8c12cc7b8f8423e5c38b035249166";
    private String baseURL = "2012-04-24/Accounts/" + adminAccountSid + "/";

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(8090); // No-args constructor defaults to port 8080

    /*
     * Check the list of available Countries
     * http://www.voxbone.com/apidoc/resource_InventoryServiceRest.html#path__country.html
     */
    @Test
    public void testGetAvailableCountries() {
        stubFor(put(urlEqualTo("/test/configuration/voiceuri"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(VoxboneAvailablePhoneNumbersEndpointTestUtils.VoiceURIJSonResponse)));
        stubFor(get(urlEqualTo("/test/inventory/country?pageNumber=0&pageSize=300"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(VoxboneIncomingPhoneNumbersEndpointTestUtils.listCountries)));
        // Get Account using admin email address and user email address
        Client jerseyClient = Client.create();
        jerseyClient.addFilter(new HTTPBasicAuthFilter(adminUsername, adminAuthToken));

        String provisioningURL = deploymentUrl + baseURL + "IncomingPhoneNumbers/AvailableCountries.json";
        WebResource webResource = jerseyClient.resource(provisioningURL);

        ClientResponse clientResponse = webResource.accept("application/json").get(ClientResponse.class);
        assertTrue(clientResponse.getStatus() == 200);
        String response = clientResponse.getEntity(String.class);
        System.out.println(response);
        assertTrue(!response.trim().equalsIgnoreCase("[]"));
        JsonParser parser = new JsonParser();
        JsonArray jsonResponse = parser.parse(response).getAsJsonArray();

        System.out.println(jsonResponse.toString());
        System.out.println(jsonResponse.size());

        assertTrue(jsonResponse.size() == 57);
    }

    /*
     *
     */
    @Test
    @Category(BrokenTests.class)
    public void testPurchaseAndDeletePhoneNumberSuccess() {
        stubFor(put(urlEqualTo("/test/configuration/voiceuri"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(VoxboneAvailablePhoneNumbersEndpointTestUtils.VoiceURIJSonResponse)));
        stubFor(put(urlEqualTo("/test/ordering/cart"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(VoxboneIncomingPhoneNumbersEndpointTestUtils.purchaseOrderingCartSuccessResponse)));
        stubFor(post(urlEqualTo("/test/ordering/cart/30018/product"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(VoxboneIncomingPhoneNumbersEndpointTestUtils.addToCartSuccessResponse)));
        stubFor(get(urlEqualTo("/test/ordering/cart/30018/checkout?cartIdentifier=30018"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(VoxboneIncomingPhoneNumbersEndpointTestUtils.checkoutCartSuccessResponse)));
        stubFor(get(urlEqualTo("/test/inventory/did?orderReference=62252DS997341&pageNumber=0&pageSize=50"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(VoxboneIncomingPhoneNumbersEndpointTestUtils.inventoryDidSuccessResponse)));
        stubFor(post(urlEqualTo("/test/configuration/configuration"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(VoxboneIncomingPhoneNumbersEndpointTestUtils.updateDidSuccessResponse)));
        stubFor(post(urlEqualTo("/test/ordering/cancel"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(VoxboneIncomingPhoneNumbersEndpointTestUtils.cancelDidSuccessResponse)));

        // Get Account using admin email address and user email address
    	Client jerseyClient = Client.create();
        jerseyClient.addFilter(new HTTPBasicAuthFilter(adminUsername, adminAuthToken));

        String provisioningURL = deploymentUrl + baseURL + "IncomingPhoneNumbers.json";
        WebResource webResource = jerseyClient.resource(provisioningURL);

        MultivaluedMap<String, String> formData = new MultivaluedMapImpl();
        formData.add("PhoneNumber", "22073");
        formData.add("VoiceUrl", "http://demo.telestax.com/docs/voice.xml");
        formData.add("FriendlyName", "USA-ACKLEY-641");
        formData.add("VoiceMethod", "GET");
        ClientResponse clientResponse = webResource.type(MediaType.APPLICATION_FORM_URLENCODED_TYPE).accept("application/json").post(ClientResponse.class, formData);
        assertTrue(clientResponse.getStatus() == 200);
        String response = clientResponse.getEntity(String.class);
        System.out.println(response);
        assertTrue(!response.trim().equalsIgnoreCase("[]"));
        JsonParser parser = new JsonParser();
        JsonObject jsonResponse = parser.parse(response).getAsJsonObject();

        System.out.println(jsonResponse.toString());
        assertTrue(VoxboneIncomingPhoneNumbersEndpointTestUtils.match(jsonResponse.toString(),VoxboneIncomingPhoneNumbersEndpointTestUtils.jSonResultPurchaseNumber));

        String phoneNumberSid = jsonResponse.get("sid").getAsString();
        provisioningURL = deploymentUrl + baseURL + "IncomingPhoneNumbers/" + phoneNumberSid + ".json";
        webResource = jerseyClient.resource(provisioningURL);
        clientResponse = webResource.type(MediaType.APPLICATION_FORM_URLENCODED_TYPE).accept("application/json").delete(ClientResponse.class);
        assertTrue(clientResponse.getStatus() == 204);
    }

    /*
     */
//    @Test
//    public void testDeletePhoneNumberSuccess() {
//        stubFor(post(urlMatching("/nexmo/number/buy/.*/.*/ES/34911067000"))
//                .willReturn(aResponse()
//                    .withStatus(200)
//                    .withHeader("Content-Type", "application/json")
//                    .withBody(VoxboneIncomingPhoneNumbersEndpointTestUtils.purchaseNumberSuccessResponse)));
//
//        stubFor(post(urlMatching("/nexmo/number/update/.*/.*/ES/34911067000.*"))
//                .willReturn(aResponse()
//                    .withStatus(200)
//                    .withHeader("Content-Type", "application/json")
//                    .withBody(VoxboneIncomingPhoneNumbersEndpointTestUtils.purchaseNumberSuccessResponse)));
//
//        stubFor(post(urlMatching("/nexmo/number/cancel/.*/.*/ES/34911067000"))
//                .willReturn(aResponse()
//                    .withStatus(200)
//                    .withHeader("Content-Type", "application/json")
//                    .withBody(VoxboneIncomingPhoneNumbersEndpointTestUtils.deleteNumberSuccessResponse)));
//        // Get Account using admin email address and user email address
//        Client jerseyClient = Client.create();
//        jerseyClient.addFilter(new HTTPBasicAuthFilter(adminUsername, adminAuthToken));
//
//        String provisioningURL = deploymentUrl + baseURL + "IncomingPhoneNumbers.json";
//        WebResource webResource = jerseyClient.resource(provisioningURL);
//
//        MultivaluedMap<String, String> formData = new MultivaluedMapImpl();
//        formData.add("PhoneNumber", "+34911067000");
//        formData.add("VoiceUrl", "http://demo.telestax.com/docs/voice.xml");
//        formData.add("FriendlyName", "My Company Line");
//        formData.add("VoiceMethod", "GET");
//        ClientResponse clientResponse = webResource.type(MediaType.APPLICATION_FORM_URLENCODED_TYPE).accept("application/json").post(ClientResponse.class, formData);
//        assertTrue(clientResponse.getStatus() == 200);
//        String response = clientResponse.getEntity(String.class);
//        System.out.println(response);
//        assertTrue(!response.trim().equalsIgnoreCase("[]"));
//        JsonParser parser = new JsonParser();
//        JsonObject jsonResponse = parser.parse(response).getAsJsonObject();
//
//        System.out.println(jsonResponse.toString());
//        assertTrue(VoxboneIncomingPhoneNumbersEndpointTestUtils.match(jsonResponse.toString(),VoxboneIncomingPhoneNumbersEndpointTestUtils.jSonResultDeletePurchaseNumber));
//
//        String phoneNumberSid = jsonResponse.get("sid").getAsString();
//        provisioningURL = deploymentUrl + baseURL + "IncomingPhoneNumbers/" + phoneNumberSid + ".json";
//        webResource = jerseyClient.resource(provisioningURL);
//        clientResponse = webResource.type(MediaType.APPLICATION_FORM_URLENCODED_TYPE).accept("application/json").delete(ClientResponse.class);
//        assertTrue(clientResponse.getStatus() == 204);
//    }

    /*
     * https://www.twilio.com/docs/api/rest/incoming-phone-numbers#list-post-example-1
     * Purchases a new phone number for your account.
     * If Twilio cannot find a phone number to match your request, you will receive an HTTP 400 with Twilio error code 21452.
     */
    @Test
    @Category(FeatureExpTests.class)
    public void testPurchasePhoneNumberNoPhoneNumberFound() {
        stubFor(put(urlEqualTo("/test/configuration/voiceuri"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(VoxboneAvailablePhoneNumbersEndpointTestUtils.VoiceURIJSonResponse)));
        stubFor(put(urlEqualTo("/test/ordering/cart"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "application/json")
                    .withBody(VoxboneIncomingPhoneNumbersEndpointTestUtils.orderingCartSuccessResponse)));
        stubFor(post(urlEqualTo("/test/ordering/cart/30007/product"))
                .willReturn(aResponse()
                    .withStatus(400)
                    .withHeader("Content-Type", "application/json")));

        // Get Account using admin email address and user email address
        Client jerseyClient = Client.create();
        jerseyClient.addFilter(new HTTPBasicAuthFilter(adminUsername, adminAuthToken));

        String provisioningURL = deploymentUrl + baseURL + "IncomingPhoneNumbers.json";
        WebResource webResource = jerseyClient.resource(provisioningURL);

        MultivaluedMap<String, String> formData = new MultivaluedMapImpl();
        formData.add("PhoneNumber", "+14156902860");
        formData.add("VoiceUrl", "http://demo.telestax.com/docs/voice.xml");
        formData.add("FriendlyName", "My Company Line");
        formData.add("VoiceMethod", "GET");
        ClientResponse clientResponse = webResource.type(MediaType.APPLICATION_FORM_URLENCODED_TYPE).accept("application/json").post(ClientResponse.class, formData);
        assertTrue(clientResponse.getStatus() == 400);
        String response = clientResponse.getEntity(String.class);
        System.out.println(response);
        assertTrue(!response.trim().equalsIgnoreCase("[]"));
        JsonParser parser = new JsonParser();
        String jsonResponse = parser.parse(response).getAsString();
        assertTrue(jsonResponse.toString().equalsIgnoreCase("21452"));
    }

    /*
     * https://www.twilio.com/docs/api/rest/incoming-phone-numbers#instance-post-example-1
     * Set the VoiceUrl and SmsUrl on a phone number
     */
//    @Test
//    public void testUpdatePhoneNumberSuccess() {
//        stubFor(post(urlMatching("/nexmo/number/buy/.*/.*/FR/33911067000"))
//                .willReturn(aResponse()
//                    .withStatus(200)
//                    .withHeader("Content-Type", "application/json")
//                    .withBody(VoxboneIncomingPhoneNumbersEndpointTestUtils.purchaseNumberSuccessResponse)));
//
//        stubFor(post(urlMatching("/nexmo/number/update/.*/.*/FR/33911067000.*"))
//                .willReturn(aResponse()
//                    .withStatus(200)
//                    .withHeader("Content-Type", "application/json")
//                    .withBody(VoxboneIncomingPhoneNumbersEndpointTestUtils.purchaseNumberSuccessResponse)));
//
//        stubFor(post(urlMatching("/nexmo/number/cancel/.*/.*/FR/33911067000"))
//                .willReturn(aResponse()
//                    .withStatus(200)
//                    .withHeader("Content-Type", "application/json")
//                    .withBody(VoxboneIncomingPhoneNumbersEndpointTestUtils.deleteNumberSuccessResponse)));
//        // Get Account using admin email address and user email address
//        Client jerseyClient = Client.create();
//        jerseyClient.addFilter(new HTTPBasicAuthFilter(adminUsername, adminAuthToken));
//
//        String provisioningURL = deploymentUrl + baseURL + "IncomingPhoneNumbers.json";
//        WebResource webResource = jerseyClient.resource(provisioningURL);
//
//        MultivaluedMap<String, String> formData = new MultivaluedMapImpl();
//        formData.add("PhoneNumber", "+33911067000");
//        formData.add("VoiceUrl", "http://demo.telestax.com/docs/voice.xml");
//        formData.add("FriendlyName", "My Company Line");
//        formData.add("VoiceMethod", "GET");
//        ClientResponse clientResponse = webResource.type(MediaType.APPLICATION_FORM_URLENCODED_TYPE).accept("application/json").post(ClientResponse.class, formData);
//        assertTrue(clientResponse.getStatus() == 200);
//        String response = clientResponse.getEntity(String.class);
//        System.out.println(response);
//        assertTrue(!response.trim().equalsIgnoreCase("[]"));
//        JsonParser parser = new JsonParser();
//        JsonObject jsonResponse = parser.parse(response).getAsJsonObject();
//
//        System.out.println(jsonResponse.toString());
//        assertTrue(VoxboneIncomingPhoneNumbersEndpointTestUtils.match(jsonResponse.toString(),VoxboneIncomingPhoneNumbersEndpointTestUtils.jSonResultUpdatePurchaseNumber));
//
//        String phoneNumberSid = jsonResponse.get("sid").getAsString();
//        provisioningURL = deploymentUrl + baseURL + "IncomingPhoneNumbers/" + phoneNumberSid + ".json";
//        webResource = jerseyClient.resource(provisioningURL);
//        formData = new MultivaluedMapImpl();
//        formData.add("VoiceUrl", "http://demo.telestax.com/docs/voice2.xml");
//        formData.add("SmsUrl", "http://demo.telestax.com/docs/sms2.xml");
//        formData.add("VoiceMethod", "POST");
//        formData.add("SMSMethod", "GET");
//        clientResponse = webResource.type(MediaType.APPLICATION_FORM_URLENCODED_TYPE).accept("application/json").post(ClientResponse.class, formData);
//        assertTrue(clientResponse.getStatus() == 200);
//        response = clientResponse.getEntity(String.class);
//        System.out.println(response);
//        assertTrue(!response.trim().equalsIgnoreCase("[]"));
//        parser = new JsonParser();
//        jsonResponse = parser.parse(response).getAsJsonObject();
//        System.out.println(jsonResponse.toString());
//        assertTrue(VoxboneIncomingPhoneNumbersEndpointTestUtils.match(jsonResponse.toString(),VoxboneIncomingPhoneNumbersEndpointTestUtils.jSonResultUpdateSuccessPurchaseNumber));
//
//        provisioningURL = deploymentUrl + baseURL + "IncomingPhoneNumbers/" + phoneNumberSid + ".json";
//        webResource = jerseyClient.resource(provisioningURL);
//        clientResponse = webResource.type(MediaType.APPLICATION_FORM_URLENCODED_TYPE).accept("application/json").delete(ClientResponse.class);
//        assertTrue(clientResponse.getStatus() == 204);
//    }

    @Deployment(name = "VoxboneIncomingPhoneNumbersEndpointTest", managed = true, testable = false)
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
