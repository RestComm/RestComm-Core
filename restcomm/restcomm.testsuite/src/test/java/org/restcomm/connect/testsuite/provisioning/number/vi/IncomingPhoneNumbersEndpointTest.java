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
import static org.junit.Assert.assertEquals;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;

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
import org.restcomm.connect.commons.dao.Sid;
import org.restcomm.connect.commons.annotations.BrokenTests;
import org.restcomm.connect.commons.annotations.FeatureAltTests;
import org.restcomm.connect.commons.annotations.FeatureExpTests;
import org.restcomm.connect.commons.annotations.UnstableTests;

/**
 * @author <a href="mailto:jean.deruelle@telestax.com">Jean Deruelle</a>
 */

@RunWith(Arquillian.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class IncomingPhoneNumbersEndpointTest {
    private final static Logger logger = Logger.getLogger(IncomingPhoneNumbersEndpointTest.class.getName());

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
    private String migrateURL = "2012-04-24/Accounts/ACae6e420f425248d6a26948c17a9e2acg/";
    private String adminOrg2Username = "administrator@org2.restcomm.com";
    private String adminOrg2AccountSid = "ACae6e420f425248d6a26948c17a9e2acg";
    private String adminOrg2AuthToken = "77f8c12cc7b8f8423e5c38b035249166";
    private String baseURLOrg2 = "2012-04-24/Accounts/" + adminOrg2AccountSid + "/";

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(8090); // No-args constructor defaults to port 8080


    /*
     * https://github.com/RestComm/Restcomm-Connect/issues/2389
     * try deleting a number that does not exist
     */
    @Test
    @Category(FeatureExpTests.class)
    public void testDeletePhoneNumberNotFound() {
        stubFor(post(urlEqualTo("/test"))
                .withRequestBody(containing("queryDID"))
                .withRequestBody(containing("4196902867"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/xml")
                    .withBody(IncomingPhoneNumbersEndpointTestUtils.queryDIDSuccessResponse)));

        stubFor(post(urlEqualTo("/test"))
                .withRequestBody(containing("assignDID"))
                .withRequestBody(containing("4196902867"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/xml")
                    .withBody(IncomingPhoneNumbersEndpointTestUtils.purchaseNumberSuccessResponse)));

        stubFor(post(urlEqualTo("/test"))
                .withRequestBody(containing("releaseDID"))
                .withRequestBody(containing("4196902867"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/xml")
                    .withBody(IncomingPhoneNumbersEndpointTestUtils.deleteNumberSuccessResponse)));
        // Get Account using admin email address and user email address
        Client jerseyClient = Client.create();
        jerseyClient.addFilter(new HTTPBasicAuthFilter(adminUsername, adminAuthToken));

        String provisioningURL = deploymentUrl + baseURL + "IncomingPhoneNumbers.json";
        WebResource webResource = jerseyClient.resource(provisioningURL);

        String phoneNumberSid = Sid.generate(Sid.Type.PHONE_NUMBER).toString();
        provisioningURL = deploymentUrl + baseURL + "IncomingPhoneNumbers/" + phoneNumberSid + ".json";
        webResource = jerseyClient.resource(provisioningURL);
        ClientResponse clientResponse = webResource.type(MediaType.APPLICATION_FORM_URLENCODED_TYPE).accept("application/json").delete(ClientResponse.class);
        logger.info("clientResponse: "+clientResponse.getStatus());
        assertTrue(clientResponse.getStatus() == 404);
    }

    /*
     * https://github.com/RestComm/Restcomm-Connect/issues/2389
     * try updating a number that does not exist
     */
    @Test
    @Category(FeatureExpTests.class)
    public void testUpdatePhoneNumberNotFound() {
        stubFor(post(urlEqualTo("/test"))
                .withRequestBody(containing("queryDID"))
                .withRequestBody(containing("4206902867"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/xml")
                    .withBody(IncomingPhoneNumbersEndpointTestUtils.queryDIDSuccessResponse)));

        stubFor(post(urlEqualTo("/test"))
                .withRequestBody(containing("assignDID"))
                .withRequestBody(containing("4206902867"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/xml")
                    .withBody(IncomingPhoneNumbersEndpointTestUtils.purchaseNumberSuccessResponse)));

        stubFor(post(urlEqualTo("/test"))
                .withRequestBody(containing("releaseDID"))
                .withRequestBody(containing("4206902867"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/xml")
                    .withBody(IncomingPhoneNumbersEndpointTestUtils.deleteNumberSuccessResponse)));
        // Get Account using admin email address and user email address
        Client jerseyClient = Client.create();
        jerseyClient.addFilter(new HTTPBasicAuthFilter(adminUsername, adminAuthToken));

        String provisioningURL = deploymentUrl + baseURL + "IncomingPhoneNumbers.json";
        WebResource webResource = jerseyClient.resource(provisioningURL);

        MultivaluedMap<String, String> formData = new MultivaluedMapImpl();
        String phoneNumberSid = Sid.generate(Sid.Type.PHONE_NUMBER).toString();
        provisioningURL = deploymentUrl + baseURL + "IncomingPhoneNumbers/" + phoneNumberSid + ".json";
        webResource = jerseyClient.resource(provisioningURL);
        formData = new MultivaluedMapImpl();
        formData.add("VoiceUrl", "http://demo.telestax.com/docs/voice2.xml");
        formData.add("SmsUrl", "http://demo.telestax.com/docs/sms2.xml");
        formData.add("VoiceMethod", "POST");
        formData.add("SMSMethod", "GET");
        ClientResponse clientResponse = webResource.type(MediaType.APPLICATION_FORM_URLENCODED_TYPE).accept("application/json").post(ClientResponse.class, formData);
        logger.info("clientResponse.getStatus(): "+clientResponse.getStatus());
        assertTrue(clientResponse.getStatus() == 404);
    }

    @Test
    @Category(BrokenTests.class)
    public void getIncomingPhoneNumbersList() {
        JsonObject firstPage = RestcommIncomingPhoneNumberTool.getInstance().getIncomingPhoneNumbers(deploymentUrl.toString(), adminAccountSid,
                adminAuthToken);
        int totalSize = firstPage.get("total").getAsInt();
        JsonArray firstPageNumbersArray = firstPage.get("incomingPhoneNumbers").getAsJsonArray();
        int firstPageNumbersArraySize = firstPageNumbersArray.size();
        assertTrue(firstPageNumbersArraySize == 50);
        assertTrue(firstPage.get("start").getAsInt() == 0);
        assertTrue(firstPage.get("end").getAsInt() == 49);

        JsonObject secondPage = (JsonObject) RestcommIncomingPhoneNumberTool.getInstance().getIncomingPhoneNumbers(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, 2, null, true);
        JsonArray secondPageNumbersArray = secondPage.get("incomingPhoneNumbers").getAsJsonArray();
        assertTrue(secondPageNumbersArray.size() == 50);
        assertTrue(secondPage.get("start").getAsInt() == 100);
        assertTrue(secondPage.get("end").getAsInt() == 149);

        JsonObject lastPage = (JsonObject) RestcommIncomingPhoneNumberTool.getInstance().getIncomingPhoneNumbers(deploymentUrl.toString(), adminAccountSid,
                adminAuthToken, firstPage.get("num_pages").getAsInt(), null, true);
        JsonArray lastPageNumbersArray = lastPage.get("incomingPhoneNumbers").getAsJsonArray();
        assertTrue(lastPageNumbersArray.get(lastPageNumbersArray.size() - 1).getAsJsonObject().get("sid").getAsString()
                .equals("PHae6e420f425248d6a26948c17a9e2ap8"));
          assertTrue(lastPageNumbersArray.size() == 1);
        assertTrue(lastPage.get("start").getAsInt() == 500);
        assertTrue(lastPage.get("end").getAsInt() == 501);

        assertTrue(totalSize == 501);
    }

    @Test
    @Category(BrokenTests.class)
    public void getIncomingPhoneNumbersListUsingPageSize() {
        JsonObject firstPage = (JsonObject) RestcommIncomingPhoneNumberTool.getInstance().getIncomingPhoneNumbers(deploymentUrl.toString(), adminAccountSid,
                adminAuthToken, null, 100, true);
        int totalSize = firstPage.get("total").getAsInt();
        JsonArray firstPageNumbersArray = firstPage.get("incomingPhoneNumbers").getAsJsonArray();
        int firstPageNumbersArraySize = firstPageNumbersArray.size();
        assertTrue(firstPageNumbersArraySize == 100);
        assertTrue(firstPage.get("start").getAsInt() == 0);
        assertTrue(firstPage.get("end").getAsInt() == 99);

        JsonObject secondPage = (JsonObject) RestcommIncomingPhoneNumberTool.getInstance().getIncomingPhoneNumbers(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, 2, 100, true);
        JsonArray secondPageNumbersArray = secondPage.get("incomingPhoneNumbers").getAsJsonArray();
        assertTrue(secondPageNumbersArray.size() == 100);
        assertTrue(secondPage.get("start").getAsInt() == 200);
        assertTrue(secondPage.get("end").getAsInt() == 299);

        JsonObject lastPage = (JsonObject) RestcommIncomingPhoneNumberTool.getInstance().getIncomingPhoneNumbers(deploymentUrl.toString(), adminAccountSid,
                adminAuthToken, firstPage.get("num_pages").getAsInt(), 100, true);
        JsonArray lastPageNumbersArray = lastPage.get("incomingPhoneNumbers").getAsJsonArray();
        assertEquals("PHae6e420f425248d6a26948c17a9e2ap8",lastPageNumbersArray.get(lastPageNumbersArray.size() - 1).getAsJsonObject().get("sid").getAsString());
        assertTrue(lastPageNumbersArray.size() == 1);
        assertTrue(lastPage.get("start").getAsInt() == 500);
        assertTrue(lastPage.get("end").getAsInt() == 501);

        assertTrue(totalSize == 501);
    }

    /*
     * Check the list of available Countries
     */
    @Test
    public void testGetAvailableCountries() {
        // Get Account using admin email address and user email address
        Client jerseyClient = Client.create();
        jerseyClient.addFilter(new HTTPBasicAuthFilter(adminUsername, adminAuthToken));

        String provisioningURL = deploymentUrl + baseURL + "IncomingPhoneNumbers/AvailableCountries.json";
        WebResource webResource = jerseyClient.resource(provisioningURL);

        ClientResponse clientResponse = webResource.accept("application/json").get(ClientResponse.class);
        assertTrue(clientResponse.getStatus() == 200);
        String response = clientResponse.getEntity(String.class);
        logger.info(response);
        assertTrue(!response.trim().equalsIgnoreCase("[]"));
        JsonParser parser = new JsonParser();
        JsonArray jsonResponse = parser.parse(response).getAsJsonArray();

        logger.info(jsonResponse.toString());

        assertTrue(jsonResponse.size() == 1);
        logger.info(jsonResponse.get(0).getAsString());
        assertTrue(jsonResponse.get(0).getAsString().equals("US"));
    }


    /*
     * https://www.twilio.com/docs/api/rest/incoming-phone-numbers#list-post-example-1
     * Purchases a new phone number for your account. If a phone number is found for your request,
     * Twilio will add it to your account and bill you for the first month's cost of the phone number.
     */
    @Test
    public void testPurchasePhoneNumberSuccess() {
        stubFor(post(urlEqualTo("/test"))
                .withRequestBody(containing("queryDID"))
                .withRequestBody(containing("4156902867"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/xml")
                    .withBody(IncomingPhoneNumbersEndpointTestUtils.queryDIDSuccessResponse)));

        stubFor(post(urlEqualTo("/test"))
                .withRequestBody(containing("assignDID"))
                .withRequestBody(containing("4156902867"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/xml")
                    .withBody(IncomingPhoneNumbersEndpointTestUtils.purchaseNumberSuccessResponse)));
        // Get Account using admin email address and user email address
    	Client jerseyClient = Client.create();
        jerseyClient.addFilter(new HTTPBasicAuthFilter(adminUsername, adminAuthToken));

        String provisioningURL = deploymentUrl + baseURL + "IncomingPhoneNumbers.json";
        WebResource webResource = jerseyClient.resource(provisioningURL);

        MultivaluedMap<String, String> formData = new MultivaluedMapImpl();
        formData.add("PhoneNumber", "+14156902867");
        formData.add("VoiceUrl", "http://demo.telestax.com/docs/voice.xml");
        formData.add("FriendlyName", "My Company Line");
        formData.add("VoiceMethod", "GET");
        ClientResponse clientResponse = webResource.type(MediaType.APPLICATION_FORM_URLENCODED_TYPE).accept("application/json").post(ClientResponse.class, formData);
        assertTrue(clientResponse.getStatus() == 200);
        String response = clientResponse.getEntity(String.class);
        logger.info(response);
        assertTrue(!response.trim().equalsIgnoreCase("[]"));
        JsonParser parser = new JsonParser();
        JsonObject jsonResponse = parser.parse(response).getAsJsonObject();

        logger.info(jsonResponse.toString());

        assertTrue(IncomingPhoneNumbersEndpointTestUtils.match(jsonResponse.toString(),IncomingPhoneNumbersEndpointTestUtils.jSonResultPurchaseNumber));
    }

    /*
     * https://www.twilio.com/docs/api/rest/incoming-phone-numbers#list-post-example-1
     * Purchases a new phone number for your account.
     * If Twilio cannot find a phone number to match your request, you will receive an HTTP 400 with Twilio error code 21452.
     */
    @Test
    @Category(FeatureExpTests.class)
    public void testPurchasePhoneNumberNoPhoneNumberFound() {
        stubFor(post(urlEqualTo("/test"))
                .withRequestBody(containing("queryDID"))
                .withRequestBody(containing("4156902868"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/xml")
                    .withBody(IncomingPhoneNumbersEndpointTestUtils.queryDIDSuccessResponse)));

        stubFor(post(urlEqualTo("/test"))
                .withRequestBody(containing("assignDID"))
                .withRequestBody(containing("4156902868"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/xml")
                    .withBody(IncomingPhoneNumbersEndpointTestUtils.purchaseNumberSuccessResponse)));
        // Get Account using admin email address and user email address
        Client jerseyClient = Client.create();
        jerseyClient.addFilter(new HTTPBasicAuthFilter(adminUsername, adminAuthToken));

        String provisioningURL = deploymentUrl + baseURL + "IncomingPhoneNumbers.json";
        WebResource webResource = jerseyClient.resource(provisioningURL);

        MultivaluedMap<String, String> formData = new MultivaluedMapImpl();
        formData.add("PhoneNumber", "+14156902867");
        formData.add("VoiceUrl", "http://demo.telestax.com/docs/voice.xml");
        formData.add("FriendlyName", "My Company Line");
        formData.add("VoiceMethod", "GET");
        ClientResponse clientResponse = webResource.type(MediaType.APPLICATION_FORM_URLENCODED_TYPE).accept("application/json").post(ClientResponse.class, formData);
        if(logger.isDebugEnabled())
        	logger.debug("clientResponse for buying unavailable number: " + clientResponse);
        assertEquals(400, clientResponse.getStatus());
        String response = clientResponse.getEntity(String.class);
        logger.info(response);
        assertTrue(!response.trim().equalsIgnoreCase("[]"));
        JsonParser parser = new JsonParser();
        String jsonResponse = parser.parse(response).getAsString();
        assertTrue(jsonResponse.toString().equalsIgnoreCase("21452"));
    }

    /*
     * https://www.twilio.com/docs/api/rest/incoming-phone-numbers#list-post-example-1
     * Purchases a new phone number for your account. If a phone number is found for your request,
     * Twilio will add it to your account and bill you for the first month's cost of the phone number.
     */
    @Test
    public void testPurchaseLocalPhoneNumberSuccess() {
        stubFor(post(urlEqualTo("/test"))
                .withRequestBody(containing("queryDID"))
                .withRequestBody(containing("4166902867"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/xml")
                    .withBody(IncomingPhoneNumbersEndpointTestUtils.queryDIDSuccessResponse)));

        stubFor(post(urlEqualTo("/test"))
                .withRequestBody(containing("assignDID"))
                .withRequestBody(containing("4166902867"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/xml")
                    .withBody(IncomingPhoneNumbersEndpointTestUtils.purchaseNumberSuccessResponse)));
        // Get Account using admin email address and user email address
        Client jerseyClient = Client.create();
        jerseyClient.addFilter(new HTTPBasicAuthFilter(adminUsername, adminAuthToken));

        String provisioningURL = deploymentUrl + baseURL + "IncomingPhoneNumbers/Local.json";
        WebResource webResource = jerseyClient.resource(provisioningURL);

        MultivaluedMap<String, String> formData = new MultivaluedMapImpl();
        formData.add("PhoneNumber", "+14166902867");
        formData.add("VoiceUrl", "http://demo.telestax.com/docs/voice.xml");
        formData.add("FriendlyName", "My Company Line");
        formData.add("VoiceMethod", "GET");
        ClientResponse clientResponse = webResource.type(MediaType.APPLICATION_FORM_URLENCODED_TYPE).accept("application/json").post(ClientResponse.class, formData);
        assertTrue(clientResponse.getStatus() == 200);
        String response = clientResponse.getEntity(String.class);
        logger.info(response);
        assertTrue(!response.trim().equalsIgnoreCase("[]"));
        JsonParser parser = new JsonParser();
        JsonObject jsonResponse = parser.parse(response).getAsJsonObject();

        logger.info(jsonResponse.toString());

        assertTrue(IncomingPhoneNumbersEndpointTestUtils.match(jsonResponse.toString(),IncomingPhoneNumbersEndpointTestUtils.jSonResultLocalPurchaseNumber));
    }

    /*
     * https://www.twilio.com/docs/api/rest/incoming-phone-numbers#list-post-example-1
     * Purchases a new phone number for your account.
     * If Twilio cannot find a phone number to match your request, you will receive an HTTP 400 with Twilio error code 21452.
     */
    @Test
    @Category(FeatureExpTests.class)
    public void testPurchaseLocalPhoneNumberNoPhoneNumberFound() {
        stubFor(post(urlEqualTo("/test"))
                .withRequestBody(containing("queryDID"))
                .withRequestBody(containing("4156902868"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/xml")
                    .withBody(IncomingPhoneNumbersEndpointTestUtils.queryDIDSuccessResponse)));

        stubFor(post(urlEqualTo("/test"))
                .withRequestBody(containing("assignDID"))
                .withRequestBody(containing("4156902868"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/xml")
                    .withBody(IncomingPhoneNumbersEndpointTestUtils.purchaseNumberSuccessResponse)));
        // Get Account using admin email address and user email address
        Client jerseyClient = Client.create();
        jerseyClient.addFilter(new HTTPBasicAuthFilter(adminUsername, adminAuthToken));

        String provisioningURL = deploymentUrl + baseURL + "IncomingPhoneNumbers/Local.json";
        WebResource webResource = jerseyClient.resource(provisioningURL);

        MultivaluedMap<String, String> formData = new MultivaluedMapImpl();
        formData.add("PhoneNumber", "+14156902867");
        formData.add("VoiceUrl", "http://demo.telestax.com/docs/voice.xml");
        formData.add("FriendlyName", "My Company Line");
        formData.add("VoiceMethod", "GET");
        ClientResponse clientResponse = webResource.type(MediaType.APPLICATION_FORM_URLENCODED_TYPE).accept("application/json").post(ClientResponse.class, formData);
        if(logger.isDebugEnabled())
        	logger.debug("clientResponse for buying unavailable number: " + clientResponse);
        assertEquals(400, clientResponse.getStatus());
        String response = clientResponse.getEntity(String.class);
        logger.info(response);
        assertTrue(!response.trim().equalsIgnoreCase("[]"));
        JsonParser parser = new JsonParser();
        String jsonResponse = parser.parse(response).getAsString();
        assertTrue(jsonResponse.toString().equalsIgnoreCase("21452"));
    }

    /*
     * https://www.twilio.com/docs/api/rest/incoming-phone-numbers#list-post-example-1
     * Purchases a new phone number for your account. If a phone number is found for your request,
     * Twilio will add it to your account and bill you for the first month's cost of the phone number.
     */
    @Test
    public void testPurchaseTollFreePhoneNumberSuccess() {
        stubFor(post(urlEqualTo("/test"))
                .withRequestBody(containing("queryDID"))
                .withRequestBody(containing("4176902867"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/xml")
                    .withBody(IncomingPhoneNumbersEndpointTestUtils.queryDIDSuccessResponse)));

        stubFor(post(urlEqualTo("/test"))
                .withRequestBody(containing("assignDID"))
                .withRequestBody(containing("4176902867"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/xml")
                    .withBody(IncomingPhoneNumbersEndpointTestUtils.purchaseNumberSuccessResponse)));
        // Get Account using admin email address and user email address
        Client jerseyClient = Client.create();
        jerseyClient.addFilter(new HTTPBasicAuthFilter(adminUsername, adminAuthToken));

        String provisioningURL = deploymentUrl + baseURL + "IncomingPhoneNumbers/TollFree.json";
        WebResource webResource = jerseyClient.resource(provisioningURL);

        MultivaluedMap<String, String> formData = new MultivaluedMapImpl();
        formData.add("PhoneNumber", "+14176902867");
        formData.add("VoiceUrl", "http://demo.telestax.com/docs/voice.xml");
        formData.add("FriendlyName", "My Company Line");
        formData.add("VoiceMethod", "GET");
        ClientResponse clientResponse = webResource.type(MediaType.APPLICATION_FORM_URLENCODED_TYPE).accept("application/json").post(ClientResponse.class, formData);
        assertTrue(clientResponse.getStatus() == 200);
        String response = clientResponse.getEntity(String.class);
        logger.info(response);
        assertTrue(!response.trim().equalsIgnoreCase("[]"));
        JsonParser parser = new JsonParser();
        JsonObject jsonResponse = parser.parse(response).getAsJsonObject();

        logger.info(jsonResponse.toString());

        assertTrue(IncomingPhoneNumbersEndpointTestUtils.match(jsonResponse.toString(),IncomingPhoneNumbersEndpointTestUtils.jSonResultTollFreePurchaseNumber));
    }

    /*
     * https://www.twilio.com/docs/api/rest/incoming-phone-numbers#list-post-example-1
     * Purchases a new phone number for your account.
     * If Twilio cannot find a phone number to match your request, you will receive an HTTP 400 with Twilio error code 21452.
     */
    @Test
    @Category(FeatureExpTests.class)
    public void testPurchaseTollFreePhoneNumberNoPhoneNumberFound() {
        stubFor(post(urlEqualTo("/test"))
                .withRequestBody(containing("queryDID"))
                .withRequestBody(containing("4156902868"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/xml")
                    .withBody(IncomingPhoneNumbersEndpointTestUtils.queryDIDSuccessResponse)));

        stubFor(post(urlEqualTo("/test"))
                .withRequestBody(containing("assignDID"))
                .withRequestBody(containing("4156902868"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/xml")
                    .withBody(IncomingPhoneNumbersEndpointTestUtils.purchaseNumberSuccessResponse)));
        // Get Account using admin email address and user email address
        Client jerseyClient = Client.create();
        jerseyClient.addFilter(new HTTPBasicAuthFilter(adminUsername, adminAuthToken));

        String provisioningURL = deploymentUrl + baseURL + "IncomingPhoneNumbers/TollFree.json";
        WebResource webResource = jerseyClient.resource(provisioningURL);

        MultivaluedMap<String, String> formData = new MultivaluedMapImpl();
        formData.add("PhoneNumber", "+14156902867");
        formData.add("VoiceUrl", "http://demo.telestax.com/docs/voice.xml");
        formData.add("FriendlyName", "My Company Line");
        formData.add("VoiceMethod", "GET");
        ClientResponse clientResponse = webResource.type(MediaType.APPLICATION_FORM_URLENCODED_TYPE).accept("application/json").post(ClientResponse.class, formData);
        if(logger.isDebugEnabled())
        	logger.debug("clientResponse for buying unavailable number: " + clientResponse);
        assertEquals(400, clientResponse.getStatus());
        String response = clientResponse.getEntity(String.class);
        logger.info(response);
        assertTrue(!response.trim().equalsIgnoreCase("[]"));
        JsonParser parser = new JsonParser();
        String jsonResponse = parser.parse(response).getAsString();
        assertTrue(jsonResponse.toString().equalsIgnoreCase("21452"));
    }

    /*
     * https://www.twilio.com/docs/api/rest/incoming-phone-numbers#list-post-example-1
     * Purchases a new phone number for your account. If a phone number is found for your request,
     * Twilio will add it to your account and bill you for the first month's cost of the phone number.
     */
    @Test
    public void testPurchaseMobilePhoneNumberSuccess() {
        stubFor(post(urlEqualTo("/test"))
                .withRequestBody(containing("queryDID"))
                .withRequestBody(containing("4186902867"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/xml")
                    .withBody(IncomingPhoneNumbersEndpointTestUtils.queryDIDSuccessResponse)));

        stubFor(post(urlEqualTo("/test"))
                .withRequestBody(containing("assignDID"))
                .withRequestBody(containing("4186902867"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/xml")
                    .withBody(IncomingPhoneNumbersEndpointTestUtils.purchaseNumberSuccessResponse)));
        // Get Account using admin email address and user email address
        Client jerseyClient = Client.create();
        jerseyClient.addFilter(new HTTPBasicAuthFilter(adminUsername, adminAuthToken));

        String provisioningURL = deploymentUrl + baseURL + "IncomingPhoneNumbers/Mobile.json";
        WebResource webResource = jerseyClient.resource(provisioningURL);

        MultivaluedMap<String, String> formData = new MultivaluedMapImpl();
        formData.add("PhoneNumber", "+14186902867");
        formData.add("VoiceUrl", "http://demo.telestax.com/docs/voice.xml");
        formData.add("FriendlyName", "My Company Line");
        formData.add("VoiceMethod", "GET");
        ClientResponse clientResponse = webResource.type(MediaType.APPLICATION_FORM_URLENCODED_TYPE).accept("application/json").post(ClientResponse.class, formData);
        assertTrue(clientResponse.getStatus() == 200);
        String response = clientResponse.getEntity(String.class);
        logger.info(response);
        assertTrue(!response.trim().equalsIgnoreCase("[]"));
        JsonParser parser = new JsonParser();
        JsonObject jsonResponse = parser.parse(response).getAsJsonObject();

        logger.info(jsonResponse.toString());

        assertTrue(IncomingPhoneNumbersEndpointTestUtils.match(jsonResponse.toString(),IncomingPhoneNumbersEndpointTestUtils.jSonResultMobilePurchaseNumber));
    }

    /*
     * https://www.twilio.com/docs/api/rest/incoming-phone-numbers#list-post-example-1
     * Purchases a new phone number for your account.
     * If Twilio cannot find a phone number to match your request, you will receive an HTTP 400 with Twilio error code 21452.
     */
    @Test
    @Category(FeatureExpTests.class)
    public void testPurchaseMobilePhoneNumberNoPhoneNumberFound() {
        stubFor(post(urlEqualTo("/test"))
                .withRequestBody(containing("queryDID"))
                .withRequestBody(containing("4156902868"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/xml")
                    .withBody(IncomingPhoneNumbersEndpointTestUtils.queryDIDSuccessResponse)));

        stubFor(post(urlEqualTo("/test"))
                .withRequestBody(containing("assignDID"))
                .withRequestBody(containing("4156902868"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/xml")
                    .withBody(IncomingPhoneNumbersEndpointTestUtils.purchaseNumberSuccessResponse)));
        // Get Account using admin email address and user email address
        Client jerseyClient = Client.create();
        jerseyClient.addFilter(new HTTPBasicAuthFilter(adminUsername, adminAuthToken));

        String provisioningURL = deploymentUrl + baseURL + "IncomingPhoneNumbers/Mobile.json";
        WebResource webResource = jerseyClient.resource(provisioningURL);

        MultivaluedMap<String, String> formData = new MultivaluedMapImpl();
        formData.add("PhoneNumber", "+14156902867");
        formData.add("VoiceUrl", "http://demo.telestax.com/docs/voice.xml");
        formData.add("FriendlyName", "My Company Line");
        formData.add("VoiceMethod", "GET");
        ClientResponse clientResponse = webResource.type(MediaType.APPLICATION_FORM_URLENCODED_TYPE).accept("application/json").post(ClientResponse.class, formData);
        if(logger.isDebugEnabled())
        	logger.debug("clientResponse for buying unavailable number: " + clientResponse);
        assertEquals(400, clientResponse.getStatus());
        String response = clientResponse.getEntity(String.class);
        logger.info(response);
        assertTrue(!response.trim().equalsIgnoreCase("[]"));
        JsonParser parser = new JsonParser();
        String jsonResponse = parser.parse(response).getAsString();
        assertTrue(jsonResponse.toString().equalsIgnoreCase("21452"));
    }

    /*
     * https://www.twilio.com/docs/api/rest/incoming-phone-numbers#instance-delete
     * Release this phone number from your account. Twilio will no longer answer calls to this number, and you will stop being billed the monthly phone number fee. The phone number will eventually be recycled and potentially given to another customer, so use with care. If you make a mistake, contact us. We may be able to give you the number back.
     *
     * If successful, returns an HTTP 204 response with no body.
     */
    @Test
    public void testDeletePhoneNumberSuccess() {
        stubFor(post(urlEqualTo("/test"))
                .withRequestBody(containing("queryDID"))
                .withRequestBody(containing("4196902867"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/xml")
                    .withBody(IncomingPhoneNumbersEndpointTestUtils.queryDIDSuccessResponse)));

        stubFor(post(urlEqualTo("/test"))
                .withRequestBody(containing("assignDID"))
                .withRequestBody(containing("4196902867"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/xml")
                    .withBody(IncomingPhoneNumbersEndpointTestUtils.purchaseNumberSuccessResponse)));

        stubFor(post(urlEqualTo("/test"))
                .withRequestBody(containing("releaseDID"))
                .withRequestBody(containing("4196902867"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/xml")
                    .withBody(IncomingPhoneNumbersEndpointTestUtils.deleteNumberSuccessResponse)));
        // Get Account using admin email address and user email address
        Client jerseyClient = Client.create();
        jerseyClient.addFilter(new HTTPBasicAuthFilter(adminUsername, adminAuthToken));

        String provisioningURL = deploymentUrl + baseURL + "IncomingPhoneNumbers.json";
        WebResource webResource = jerseyClient.resource(provisioningURL);

        MultivaluedMap<String, String> formData = new MultivaluedMapImpl();
        formData.add("PhoneNumber", "+14196902867");
        formData.add("VoiceUrl", "http://demo.telestax.com/docs/voice.xml");
        formData.add("FriendlyName", "My Company Line");
        formData.add("VoiceMethod", "GET");
        ClientResponse clientResponse = webResource.type(MediaType.APPLICATION_FORM_URLENCODED_TYPE).accept("application/json").post(ClientResponse.class, formData);
        assertTrue(clientResponse.getStatus() == 200);
        String response = clientResponse.getEntity(String.class);
        logger.info(response);
        assertTrue(!response.trim().equalsIgnoreCase("[]"));
        JsonParser parser = new JsonParser();
        JsonObject jsonResponse = parser.parse(response).getAsJsonObject();
        logger.info(jsonResponse.toString());
        assertTrue(IncomingPhoneNumbersEndpointTestUtils.match(jsonResponse.toString(),IncomingPhoneNumbersEndpointTestUtils.jSonResultDeletePurchaseNumber));

        String phoneNumberSid = jsonResponse.get("sid").getAsString();
        provisioningURL = deploymentUrl + baseURL + "IncomingPhoneNumbers/" + phoneNumberSid + ".json";
        webResource = jerseyClient.resource(provisioningURL);
        clientResponse = webResource.type(MediaType.APPLICATION_FORM_URLENCODED_TYPE).accept("application/json").delete(ClientResponse.class);
        assertTrue(clientResponse.getStatus() == 204);
    }

    /*
     * https://www.twilio.com/docs/api/rest/incoming-phone-numbers#instance-post-example-1
     * Set the VoiceUrl and SmsUrl on a phone number
     */
    @Test
    public void testUpdatePhoneNumberSuccess() {
        stubFor(post(urlEqualTo("/test"))
                .withRequestBody(containing("queryDID"))
                .withRequestBody(containing("4206902867"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/xml")
                    .withBody(IncomingPhoneNumbersEndpointTestUtils.queryDIDSuccessResponse)));

        stubFor(post(urlEqualTo("/test"))
                .withRequestBody(containing("assignDID"))
                .withRequestBody(containing("4206902867"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/xml")
                    .withBody(IncomingPhoneNumbersEndpointTestUtils.purchaseNumberSuccessResponse)));

        stubFor(post(urlEqualTo("/test"))
                .withRequestBody(containing("releaseDID"))
                .withRequestBody(containing("4206902867"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/xml")
                    .withBody(IncomingPhoneNumbersEndpointTestUtils.deleteNumberSuccessResponse)));
        // Get Account using admin email address and user email address
        Client jerseyClient = Client.create();
        jerseyClient.addFilter(new HTTPBasicAuthFilter(adminUsername, adminAuthToken));

        String provisioningURL = deploymentUrl + baseURL + "IncomingPhoneNumbers.json";
        WebResource webResource = jerseyClient.resource(provisioningURL);

        MultivaluedMap<String, String> formData = new MultivaluedMapImpl();
        formData.add("PhoneNumber", "+14206902867");
        formData.add("VoiceUrl", "http://demo.telestax.com/docs/voice.xml");
        formData.add("FriendlyName", "My Company Line");
        formData.add("VoiceMethod", "GET");
        ClientResponse clientResponse = webResource.type(MediaType.APPLICATION_FORM_URLENCODED_TYPE).accept("application/json").post(ClientResponse.class, formData);
        assertTrue(clientResponse.getStatus() == 200);
        String response = clientResponse.getEntity(String.class);
        logger.info(response);
        assertTrue(!response.trim().equalsIgnoreCase("[]"));
        JsonParser parser = new JsonParser();
        JsonObject jsonResponse = parser.parse(response).getAsJsonObject();
        logger.info(jsonResponse.toString());
        assertTrue(IncomingPhoneNumbersEndpointTestUtils.match(jsonResponse.toString(),IncomingPhoneNumbersEndpointTestUtils.jSonResultUpdatePurchaseNumber));

        String phoneNumberSid = jsonResponse.get("sid").getAsString();
        provisioningURL = deploymentUrl + baseURL + "IncomingPhoneNumbers/" + phoneNumberSid + ".json";
        webResource = jerseyClient.resource(provisioningURL);
        formData = new MultivaluedMapImpl();
        formData.add("VoiceUrl", "http://demo.telestax.com/docs/voice2.xml");
        formData.add("SmsUrl", "http://demo.telestax.com/docs/sms2.xml");
        formData.add("VoiceMethod", "POST");
        formData.add("SMSMethod", "GET");
        clientResponse = webResource.type(MediaType.APPLICATION_FORM_URLENCODED_TYPE).accept("application/json").post(ClientResponse.class, formData);
        logger.info(clientResponse);
        assertTrue(clientResponse.getStatus() == 200);
        response = clientResponse.getEntity(String.class);
        logger.info(response);
        assertTrue(!response.trim().equalsIgnoreCase("[]"));
        parser = new JsonParser();
        jsonResponse = parser.parse(response).getAsJsonObject();
        logger.info(jsonResponse.toString());
        assertTrue(IncomingPhoneNumbersEndpointTestUtils.match(jsonResponse.toString(),IncomingPhoneNumbersEndpointTestUtils.jSonResultUpdateSuccessPurchaseNumber));
    }

    @Test
    @Category(FeatureAltTests.class)
    public void testMigratePhoneNumberSuccess() {
        Client jerseyClient = Client.create();
        jerseyClient.addFilter(new HTTPBasicAuthFilter(adminUsername, adminAuthToken));

        String provisioningURL = deploymentUrl + migrateURL + "IncomingPhoneNumbers/migrate";
        WebResource webResource = jerseyClient.resource(provisioningURL);
        MultivaluedMap<String, String> formData = new MultivaluedMapImpl();
        formData = new MultivaluedMapImpl();
        formData.add("OrganizationSid", "ORafbe225ad37541eba518a74248f0ac4c");
        ClientResponse clientResponse = webResource.type(MediaType.APPLICATION_FORM_URLENCODED_TYPE).accept("application/json").post(ClientResponse.class, formData);
        logger.info(clientResponse);
        assertEquals(200, clientResponse.getStatus());
    }

    @Test
    @Category(FeatureExpTests.class)
    public void testMigratePhoneNumberPermission() {
        Client jerseyClient = Client.create();
        jerseyClient.addFilter(new HTTPBasicAuthFilter(adminOrg2AccountSid, adminAuthToken));

        String provisioningURL = deploymentUrl + migrateURL + "IncomingPhoneNumbers/migrate";
        WebResource webResource = jerseyClient.resource(provisioningURL);
        MultivaluedMap<String, String> formData = new MultivaluedMapImpl();
        formData = new MultivaluedMapImpl();
        formData.add("OrganizationSid", "ORafbe225ad37541eba518a74248f0ac4c");
        ClientResponse clientResponse = webResource.type(MediaType.APPLICATION_FORM_URLENCODED_TYPE).accept("application/json").post(ClientResponse.class, formData);
        logger.info(clientResponse);
        assertEquals(403, clientResponse.getStatus());
    }

    /**
     * super admin's number migration is not allowed
     */
    @Test
    @Category(FeatureExpTests.class)
    public void testMigratePhoneNumberSuperAdminMigration() {
        Client jerseyClient = Client.create();
        jerseyClient.addFilter(new HTTPBasicAuthFilter(adminAccountSid, adminAuthToken));

        String provisioningURL = deploymentUrl + baseURL + "IncomingPhoneNumbers/migrate";
        WebResource webResource = jerseyClient.resource(provisioningURL);
        MultivaluedMap<String, String> formData = new MultivaluedMapImpl();
        formData = new MultivaluedMapImpl();
        formData.add("OrganizationSid", "ORafbe225ad37541eba518a74248f0ac4c");
        ClientResponse clientResponse = webResource.type(MediaType.APPLICATION_FORM_URLENCODED_TYPE).accept("application/json").post(ClientResponse.class, formData);
        logger.info(clientResponse);
        assertEquals(400, clientResponse.getStatus());
    }
    /*
     * https://www.twilio.com/docs/api/rest/incoming-phone-numbers#list-get-example-1
     */
    @Test
    public void testAccountAssociatedPhoneNumbers() {
        stubFor(post(urlEqualTo("/test"))
                .withRequestBody(containing("queryDID"))
                .withRequestBody(containing("4216902867"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/xml")
                    .withBody(IncomingPhoneNumbersEndpointTestUtils.queryDIDSuccessResponse)));

        stubFor(post(urlEqualTo("/test"))
                .withRequestBody(containing("assignDID"))
                .withRequestBody(containing("4216902867"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/xml")
                    .withBody(IncomingPhoneNumbersEndpointTestUtils.purchaseNumberSuccessResponse)));

        stubFor(post(urlEqualTo("/test"))
                .withRequestBody(containing("releaseDID"))
                .withRequestBody(containing("4216902867"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/xml")
                    .withBody(IncomingPhoneNumbersEndpointTestUtils.deleteNumberSuccessResponse)));

        stubFor(post(urlEqualTo("/test"))
                .withRequestBody(containing("queryDID"))
                .withRequestBody(containing("5216902867"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/xml")
                    .withBody(IncomingPhoneNumbersEndpointTestUtils.queryDIDSuccessResponse)));

        stubFor(post(urlEqualTo("/test"))
                .withRequestBody(containing("assignDID"))
                .withRequestBody(containing("5216902867"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/xml")
                    .withBody(IncomingPhoneNumbersEndpointTestUtils.purchaseNumberSuccessResponse)));

        stubFor(post(urlEqualTo("/test"))
                .withRequestBody(containing("releaseDID"))
                .withRequestBody(containing("5216902867"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/xml")
                    .withBody(IncomingPhoneNumbersEndpointTestUtils.deleteNumberSuccessResponse)));
        // Get Account using admin email address and user email address
        Client jerseyClient = Client.create();
        jerseyClient.addFilter(new HTTPBasicAuthFilter(adminUsername, adminAuthToken));

        String provisioningURL = deploymentUrl + baseURL + "IncomingPhoneNumbers.json";
        WebResource webResource = jerseyClient.resource(provisioningURL);

        MultivaluedMap<String, String> formData = new MultivaluedMapImpl();
        formData.add("PhoneNumber", "+14216902867");
        formData.add("VoiceUrl", "http://demo.telestax.com/docs/voice.xml");
        formData.add("FriendlyName", "My Company Line");
        formData.add("VoiceMethod", "GET");
        ClientResponse clientResponse = webResource.type(MediaType.APPLICATION_FORM_URLENCODED_TYPE).accept("application/json").post(ClientResponse.class, formData);
        assertTrue(clientResponse.getStatus() == 200);
        String response = clientResponse.getEntity(String.class);
        logger.info(response);
        assertTrue(!response.trim().equalsIgnoreCase("[]"));
        JsonParser parser = new JsonParser();
        JsonObject jsonResponse = parser.parse(response).getAsJsonObject();
        logger.info(jsonResponse.toString());
        assertTrue(IncomingPhoneNumbersEndpointTestUtils.match(jsonResponse.toString(),IncomingPhoneNumbersEndpointTestUtils.jSonResultAccountAssociatedPurchaseNumber));
        String phoneNumberSid = jsonResponse.get("sid").getAsString();

        formData = new MultivaluedMapImpl();
        formData.add("PhoneNumber", "+15216902867");
        formData.add("VoiceUrl", "http://demo.telestax.com/docs/voice.xml");
        formData.add("FriendlyName", "My 2nd Company Line");
        formData.add("VoiceMethod", "GET");
        clientResponse = webResource.type(MediaType.APPLICATION_FORM_URLENCODED_TYPE).accept("application/json").post(ClientResponse.class, formData);
        assertTrue(clientResponse.getStatus() == 200);
        response = clientResponse.getEntity(String.class);
        logger.info(response);
        assertTrue(!response.trim().equalsIgnoreCase("[]"));
        parser = new JsonParser();
        jsonResponse = parser.parse(response).getAsJsonObject();
        String secondPhoneNumberSid = jsonResponse.get("sid").getAsString();

//        formData = new MultivaluedMapImpl();
//        formData.add("VoiceUrl", "http://demo.telestax.com/docs/voice2.xml");

        Map<String, String> filters = new HashMap<>();
        filters.put("FriendlyName", "My 2nd Company Line");
        JsonObject jsonObject = RestcommIncomingPhoneNumberTool.getInstance().getIncomingPhoneNumbersUsingFilter(deploymentUrl.toString(), adminAccountSid,
                adminAuthToken, filters);
        JsonArray jsonArray = jsonObject.get("incomingPhoneNumbers").getAsJsonArray();


        logger.info(jsonArray + " \n " + jsonArray.size());

        assertTrue(jsonArray.size() >0);
        logger.info("testAccountAssociatedPhoneNumbers:" + (jsonArray.get(jsonArray.size()-1).getAsJsonObject().toString()));
        assertTrue(IncomingPhoneNumbersEndpointTestUtils.match(jsonArray.get(jsonArray.size()-1).getAsJsonObject().toString(),IncomingPhoneNumbersEndpointTestUtils.jSonResultAccountAssociatedPurchaseNumberResult));

        provisioningURL = deploymentUrl + baseURL + "IncomingPhoneNumbers/" + phoneNumberSid + ".json";
        webResource = jerseyClient.resource(provisioningURL);
        clientResponse = webResource.type(MediaType.APPLICATION_FORM_URLENCODED_TYPE).accept("application/json").delete(ClientResponse.class);
        assertTrue(clientResponse.getStatus() == 204);

        provisioningURL = deploymentUrl + baseURL + "IncomingPhoneNumbers/" + secondPhoneNumberSid + ".json";
        webResource = jerseyClient.resource(provisioningURL);
        clientResponse = webResource.type(MediaType.APPLICATION_FORM_URLENCODED_TYPE).accept("application/json").delete(ClientResponse.class);
        assertTrue(clientResponse.getStatus() == 204);
    }

    /*
     * https://www.twilio.com/docs/api/rest/incoming-phone-numbers#list-get-example-2
     */
    @Test
    public void testAccountAssociatedPhoneNumbersFilter() {
        stubFor(post(urlEqualTo("/test"))
                .withRequestBody(containing("queryDID"))
                .withRequestBody(containing("4216902867"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/xml")
                    .withBody(IncomingPhoneNumbersEndpointTestUtils.queryDIDSuccessResponse)));

        stubFor(post(urlEqualTo("/test"))
                .withRequestBody(containing("assignDID"))
                .withRequestBody(containing("4216902867"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/xml")
                    .withBody(IncomingPhoneNumbersEndpointTestUtils.purchaseNumberSuccessResponse)));

        stubFor(post(urlEqualTo("/test"))
                .withRequestBody(containing("releaseDID"))
                .withRequestBody(containing("4216902867"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/xml")
                    .withBody(IncomingPhoneNumbersEndpointTestUtils.deleteNumberSuccessResponse)));

        stubFor(post(urlEqualTo("/test"))
                .withRequestBody(containing("queryDID"))
                .withRequestBody(containing("5216902867"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/xml")
                    .withBody(IncomingPhoneNumbersEndpointTestUtils.queryDIDSuccessResponse)));

        stubFor(post(urlEqualTo("/test"))
                .withRequestBody(containing("assignDID"))
                .withRequestBody(containing("5216902867"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/xml")
                    .withBody(IncomingPhoneNumbersEndpointTestUtils.purchaseNumberSuccessResponse)));

        stubFor(post(urlEqualTo("/test"))
                .withRequestBody(containing("releaseDID"))
                .withRequestBody(containing("5216902867"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/xml")
                    .withBody(IncomingPhoneNumbersEndpointTestUtils.deleteNumberSuccessResponse)));
        // Get Account using admin email address and user email address
        Client jerseyClient = Client.create();
        jerseyClient.addFilter(new HTTPBasicAuthFilter(adminUsername, adminAuthToken));

        String provisioningURL = deploymentUrl + baseURL + "IncomingPhoneNumbers.json";
        WebResource webResource = jerseyClient.resource(provisioningURL);

        MultivaluedMap<String, String> formData = new MultivaluedMapImpl();
        formData.add("PhoneNumber", "+14216902867");
        formData.add("VoiceUrl", "http://demo.telestax.com/docs/voice.xml");
        formData.add("FriendlyName", "My Company Line");
        formData.add("VoiceMethod", "GET");
        ClientResponse clientResponse = webResource.type(MediaType.APPLICATION_FORM_URLENCODED_TYPE).accept("application/json").post(ClientResponse.class, formData);
        assertTrue(clientResponse.getStatus() == 200);
        String response = clientResponse.getEntity(String.class);
        logger.info(response);
        assertTrue(!response.trim().equalsIgnoreCase("[]"));
        JsonParser parser = new JsonParser();
        JsonObject jsonResponse = parser.parse(response).getAsJsonObject();
        logger.info(jsonResponse.toString());
        assertTrue(IncomingPhoneNumbersEndpointTestUtils.match(jsonResponse.toString(),IncomingPhoneNumbersEndpointTestUtils.jSonResultAccountAssociatedPurchaseNumber));
        String phoneNumberSid = jsonResponse.get("sid").getAsString();

        formData = new MultivaluedMapImpl();
        formData.add("PhoneNumber", "+15216902867");
        formData.add("VoiceUrl", "http://demo.telestax.com/docs/voice.xml");
        formData.add("FriendlyName", "My 2nd Company Line");
        formData.add("VoiceMethod", "GET");
        clientResponse = webResource.type(MediaType.APPLICATION_FORM_URLENCODED_TYPE).accept("application/json").post(ClientResponse.class, formData);
        assertTrue(clientResponse.getStatus() == 200);
        response = clientResponse.getEntity(String.class);
        logger.info(response);
        assertTrue(!response.trim().equalsIgnoreCase("[]"));
        parser = new JsonParser();
        jsonResponse = parser.parse(response).getAsJsonObject();
        String secondPhoneNumberSid = jsonResponse.get("sid").getAsString();

        provisioningURL = deploymentUrl + baseURL + "IncomingPhoneNumbers.json";
        Map<String, String> filters = new HashMap<>();
        filters.put("PhoneNumber", "+15216902867");
        JsonObject jsonObject =  RestcommIncomingPhoneNumberTool.getInstance().getIncomingPhoneNumbersUsingFilter(deploymentUrl.toString(), adminAccountSid, adminAuthToken, filters);
        JsonArray jsonArray = jsonObject.get("incomingPhoneNumbers").getAsJsonArray();

        logger.info(jsonArray + " \n " + jsonArray.size());

        assertTrue(jsonArray.size() == 1);
        logger.info((jsonArray.get(0).getAsJsonObject().toString()));
        assertTrue(IncomingPhoneNumbersEndpointTestUtils.match(jsonArray.get(0).getAsJsonObject().toString(),IncomingPhoneNumbersEndpointTestUtils.jSonResultAccountAssociatedPurchaseNumberResult));

        provisioningURL = deploymentUrl + baseURL + "IncomingPhoneNumbers/" + phoneNumberSid + ".json";
        webResource = jerseyClient.resource(provisioningURL);
        clientResponse = webResource.type(MediaType.APPLICATION_FORM_URLENCODED_TYPE).accept("application/json").delete(ClientResponse.class);
        assertTrue(clientResponse.getStatus() == 204);

        provisioningURL = deploymentUrl + baseURL + "IncomingPhoneNumbers/" + secondPhoneNumberSid + ".json";
        webResource = jerseyClient.resource(provisioningURL);
        clientResponse = webResource.type(MediaType.APPLICATION_FORM_URLENCODED_TYPE).accept("application/json").delete(ClientResponse.class);
        assertTrue(clientResponse.getStatus() == 204);
    }

    /*
     * https://www.twilio.com/docs/api/rest/incoming-phone-numbers#list-get-example-3
     * Return the set of all phone numbers containing the digits 867.
     */
    @Test
    public void testAccountAssociatedPhoneNumbersSecondFilter() {
        stubFor(post(urlEqualTo("/test"))
                .withRequestBody(containing("queryDID"))
                .withRequestBody(containing("4216902867"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/xml")
                    .withBody(IncomingPhoneNumbersEndpointTestUtils.queryDIDSuccessResponse)));

        stubFor(post(urlEqualTo("/test"))
                .withRequestBody(containing("assignDID"))
                .withRequestBody(containing("4216902867"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/xml")
                    .withBody(IncomingPhoneNumbersEndpointTestUtils.purchaseNumberSuccessResponse)));

        stubFor(post(urlEqualTo("/test"))
                .withRequestBody(containing("releaseDID"))
                .withRequestBody(containing("4216902867"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/xml")
                    .withBody(IncomingPhoneNumbersEndpointTestUtils.deleteNumberSuccessResponse)));

        stubFor(post(urlEqualTo("/test"))
                .withRequestBody(containing("queryDID"))
                .withRequestBody(containing("5216902867"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/xml")
                    .withBody(IncomingPhoneNumbersEndpointTestUtils.queryDIDSuccessResponse)));

        stubFor(post(urlEqualTo("/test"))
                .withRequestBody(containing("assignDID"))
                .withRequestBody(containing("5216902867"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/xml")
                    .withBody(IncomingPhoneNumbersEndpointTestUtils.purchaseNumberSuccessResponse)));

        stubFor(post(urlEqualTo("/test"))
                .withRequestBody(containing("releaseDID"))
                .withRequestBody(containing("5216902867"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/xml")
                    .withBody(IncomingPhoneNumbersEndpointTestUtils.deleteNumberSuccessResponse)));
        // Get Account using admin email address and user email address
        Client jerseyClient = Client.create();
        jerseyClient.addFilter(new HTTPBasicAuthFilter(adminUsername, adminAuthToken));

        String provisioningURL = deploymentUrl + baseURL + "IncomingPhoneNumbers.json";
        WebResource webResource = jerseyClient.resource(provisioningURL);

        MultivaluedMap<String, String> formData = new MultivaluedMapImpl();
        formData.add("PhoneNumber", "+14216902867");
        formData.add("VoiceUrl", "http://demo.telestax.com/docs/voice.xml");
        formData.add("FriendlyName", "My Company Line");
        formData.add("VoiceMethod", "GET");
        ClientResponse clientResponse = webResource.type(MediaType.APPLICATION_FORM_URLENCODED_TYPE).accept("application/json").post(ClientResponse.class, formData);
        assertEquals(200, clientResponse.getStatus());
        String response = clientResponse.getEntity(String.class);
        logger.info(response);
        assertTrue(!response.trim().equalsIgnoreCase("[]"));
        JsonParser parser = new JsonParser();
        JsonObject jsonResponse = parser.parse(response).getAsJsonObject();
        logger.info(jsonResponse.toString());
        assertTrue(IncomingPhoneNumbersEndpointTestUtils.match(jsonResponse.toString(),IncomingPhoneNumbersEndpointTestUtils.jSonResultAccountAssociatedPurchaseNumber));
        String phoneNumberSid = jsonResponse.get("sid").getAsString();

        formData = new MultivaluedMapImpl();
        formData.add("PhoneNumber", "+15216902867");
        formData.add("VoiceUrl", "http://demo.telestax.com/docs/voice.xml");
        formData.add("FriendlyName", "My 2nd Company Line");
        formData.add("VoiceMethod", "GET");
        clientResponse = webResource.type(MediaType.APPLICATION_FORM_URLENCODED_TYPE).accept("application/json").post(ClientResponse.class, formData);
        assertTrue(clientResponse.getStatus() == 200);
        response = clientResponse.getEntity(String.class);
        logger.info(response);
        assertTrue(!response.trim().equalsIgnoreCase("[]"));
        parser = new JsonParser();
        jsonResponse = parser.parse(response).getAsJsonObject();
        String secondPhoneNumberSid = jsonResponse.get("sid").getAsString();
        Map<String, String> filters = new HashMap<>();
        filters.put("PhoneNumber", "6902867");
        JsonObject jsonObject =  RestcommIncomingPhoneNumberTool.getInstance().getIncomingPhoneNumbersUsingFilter(deploymentUrl.toString(), adminAccountSid, adminAuthToken, filters);
        JsonArray jsonArray = jsonObject.get("incomingPhoneNumbers").getAsJsonArray();

        webResource = jerseyClient.resource(provisioningURL);

        logger.info(jsonArray + " \n " + jsonArray.size());

        assertTrue(jsonArray.size() >= 2);
        logger.info((jsonArray.get(jsonArray.size() - 1).getAsJsonObject().toString()));
        assertTrue(IncomingPhoneNumbersEndpointTestUtils.match(jsonArray.get(jsonArray.size() - 1).getAsJsonObject().toString(),IncomingPhoneNumbersEndpointTestUtils.jSonResultAccountAssociatedPurchaseNumberResult));

        provisioningURL = deploymentUrl + baseURL + "IncomingPhoneNumbers/" + phoneNumberSid + ".json";
        webResource = jerseyClient.resource(provisioningURL);
        clientResponse = webResource.type(MediaType.APPLICATION_FORM_URLENCODED_TYPE).accept("application/json").delete(ClientResponse.class);
        assertTrue(clientResponse.getStatus() == 204);

        provisioningURL = deploymentUrl + baseURL + "IncomingPhoneNumbers/" + secondPhoneNumberSid + ".json";
        webResource = jerseyClient.resource(provisioningURL);
        clientResponse = webResource.type(MediaType.APPLICATION_FORM_URLENCODED_TYPE).accept("application/json").delete(ClientResponse.class);
        assertTrue(clientResponse.getStatus() == 204);
    }

    /**
     * testCreatePureSipPhoneNumbersForOrganizations
     * https://github.com/RestComm/Restcomm-Connect/issues/2106
     */
    @Test
    public void testCreatePureSipPhoneNumbersForOrganizations() {
        stubFor(post(urlEqualTo("/test"))
                .withRequestBody(containing("queryDID"))
                .withRequestBody(containing("11223344"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/xml")
                    .withBody(IncomingPhoneNumbersEndpointTestUtils.queryDIDSuccessResponse)));

        stubFor(post(urlEqualTo("/test"))
                .withRequestBody(containing("assignDID"))
                .withRequestBody(containing("11223344"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/xml")
                    .withBody(IncomingPhoneNumbersEndpointTestUtils.purchaseNumberSuccessResponse)));
        // Get Account using admin email address and user email address
        Client jerseyClient = Client.create();
        jerseyClient.addFilter(new HTTPBasicAuthFilter(adminUsername, adminAuthToken));

        String provisioningURL = deploymentUrl + baseURL + "IncomingPhoneNumbers/Local.json";
        WebResource webResource = jerseyClient.resource(provisioningURL);

        MultivaluedMap<String, String> formData = new MultivaluedMapImpl();
        formData.add("PhoneNumber", "11223344");
        formData.add("VoiceUrl", "http://demo.telestax.com/docs/voice.xml");
        formData.add("FriendlyName", "My Company Line");
        formData.add("VoiceMethod", "GET");
        formData.add("isSIP", "TRUE");
        ClientResponse clientResponse = webResource.type(MediaType.APPLICATION_FORM_URLENCODED_TYPE).accept("application/json").post(ClientResponse.class, formData);
        assertTrue(clientResponse.getStatus() == 200);
        String response = clientResponse.getEntity(String.class);
        System.out.println(response);
        assertTrue(!response.trim().equalsIgnoreCase("[]"));
        JsonParser parser = new JsonParser();
        JsonObject jsonResponse = parser.parse(response).getAsJsonObject();

        logger.info("testCreatePureSipPhoneNumber from default org jsonResponse: " + jsonResponse.toString());

        assertTrue(IncomingPhoneNumbersEndpointTestUtils.match(jsonResponse.toString(),IncomingPhoneNumbersEndpointTestUtils.jSonResultSIPPurchaseNumber));

        /*
         * try to create same number again
         * under same organization/account,
         * it should not be allowed
         * */
        stubFor(post(urlEqualTo("/test"))
                .withRequestBody(containing("queryDID"))
                .withRequestBody(containing("11223344"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/xml")
                    .withBody(IncomingPhoneNumbersEndpointTestUtils.queryDIDSuccessResponse)));

        stubFor(post(urlEqualTo("/test"))
                .withRequestBody(containing("assignDID"))
                .withRequestBody(containing("11223344"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/xml")
                    .withBody(IncomingPhoneNumbersEndpointTestUtils.purchaseNumberSuccessResponse)));
        // Get Account using admin email address and user email address
        jerseyClient = Client.create();
        jerseyClient.addFilter(new HTTPBasicAuthFilter(adminUsername, adminAuthToken));
        provisioningURL = deploymentUrl + baseURL + "IncomingPhoneNumbers/Local.json";
        webResource = jerseyClient.resource(provisioningURL);
        formData = new MultivaluedMapImpl();
        formData.add("PhoneNumber", "11223344");
        formData.add("VoiceUrl", "http://demo.telestax.com/docs/voice.xml");
        formData.add("FriendlyName", "My Company Line");
        formData.add("VoiceMethod", "GET");
        formData.add("isSIP", "TRUE");
        clientResponse = webResource.type(MediaType.APPLICATION_FORM_URLENCODED_TYPE).accept("application/json").post(ClientResponse.class, formData);
        logger.info("testCreatePureSipPhoneNumber from default org TWICE clientResponse: " + clientResponse.toString());
        assertTrue(clientResponse.getStatus() != 200);

        /*
         * try to create same number again
         * under different organization/account,
         * it should be allowed
         * */
        stubFor(post(urlEqualTo("/test"))
                .withRequestBody(containing("queryDID"))
                .withRequestBody(containing("11223344"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/xml")
                    .withBody(IncomingPhoneNumbersEndpointTestUtils.queryDIDSuccessResponse)));

        stubFor(post(urlEqualTo("/test"))
                .withRequestBody(containing("assignDID"))
                .withRequestBody(containing("11223344"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/xml")
                    .withBody(IncomingPhoneNumbersEndpointTestUtils.purchaseNumberSuccessResponse)));
        jerseyClient = Client.create();
        jerseyClient.addFilter(new HTTPBasicAuthFilter(adminOrg2Username, adminOrg2AuthToken));

        provisioningURL = deploymentUrl + baseURLOrg2 + "IncomingPhoneNumbers/Local.json";
        webResource = jerseyClient.resource(provisioningURL);

        formData = new MultivaluedMapImpl();
        formData.add("PhoneNumber", "11223344");
        formData.add("VoiceUrl", "http://demo.telestax.com/docs/voice.xml");
        formData.add("FriendlyName", "My Company Line");
        formData.add("VoiceMethod", "GET");
        formData.add("isSIP", "TRUE");
        clientResponse = webResource.type(MediaType.APPLICATION_FORM_URLENCODED_TYPE).accept("application/json").post(ClientResponse.class, formData);
        assertTrue(clientResponse.getStatus() == 200);
        response = clientResponse.getEntity(String.class);
        System.out.println(response);
        assertTrue(!response.trim().equalsIgnoreCase("[]"));
        parser = new JsonParser();
        jsonResponse = parser.parse(response).getAsJsonObject();

        logger.info("testCreatePureSipPhoneNumber from Org2 jsonResponse: " + jsonResponse.toString());

        assertTrue(IncomingPhoneNumbersEndpointTestUtils.match(jsonResponse.toString(),IncomingPhoneNumbersEndpointTestUtils.jSonResultSIPPurchaseNumberOrg2));

    }

    /**
     * testCreateNonPureSipPhoneNumbersForOrganizations
     * https://github.com/RestComm/Restcomm-Connect/issues/2106
     */
    @Test
    @Category(UnstableTests.class)
    public void testCreateNonPureSipPhoneNumbersForOrganizations() {
        stubFor(post(urlEqualTo("/test"))
                .withRequestBody(containing("queryDID"))
                .withRequestBody(containing("4166902867"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/xml")
                    .withBody(IncomingPhoneNumbersEndpointTestUtils.queryDIDSuccessResponse)));

        stubFor(post(urlEqualTo("/test"))
                .withRequestBody(containing("assignDID"))
                .withRequestBody(containing("4166902867"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/xml")
                    .withBody(IncomingPhoneNumbersEndpointTestUtils.purchaseNumberSuccessResponse)));
        // Get Account using admin email address and user email address
        Client jerseyClient = Client.create();
        jerseyClient.addFilter(new HTTPBasicAuthFilter(adminUsername, adminAuthToken));

        String provisioningURL = deploymentUrl + baseURL + "IncomingPhoneNumbers/Local.json";
        WebResource webResource = jerseyClient.resource(provisioningURL);

        MultivaluedMap<String, String> formData = new MultivaluedMapImpl();
        formData.add("PhoneNumber", "+14166902867");
        formData.add("VoiceUrl", "http://demo.telestax.com/docs/voice.xml");
        formData.add("FriendlyName", "My Company Line");
        formData.add("VoiceMethod", "GET");
        ClientResponse clientResponse = webResource.type(MediaType.APPLICATION_FORM_URLENCODED_TYPE).accept("application/json").post(ClientResponse.class, formData);
        assertTrue(clientResponse.getStatus() == 200);
        String response = clientResponse.getEntity(String.class);
        System.out.println(response);
        assertTrue(!response.trim().equalsIgnoreCase("[]"));
        JsonParser parser = new JsonParser();
        JsonObject jsonResponse = parser.parse(response).getAsJsonObject();

        System.out.println(jsonResponse.toString());

        assertTrue(IncomingPhoneNumbersEndpointTestUtils.match(jsonResponse.toString(),IncomingPhoneNumbersEndpointTestUtils.jSonResultLocalPurchaseNumber));
        /*
         * try to create same number again
         * under same organization/account,
         * it should not be allowed
         * */
        stubFor(post(urlEqualTo("/test"))
                .withRequestBody(containing("queryDID"))
                .withRequestBody(containing("4156902867"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/xml")
                    .withBody(IncomingPhoneNumbersEndpointTestUtils.queryDIDSuccessResponse)));

        stubFor(post(urlEqualTo("/test"))
                .withRequestBody(containing("assignDID"))
                .withRequestBody(containing("4156902867"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/xml")
                    .withBody(IncomingPhoneNumbersEndpointTestUtils.purchaseNumberSuccessResponse)));
        // Get Account using admin email address and user email address
        jerseyClient = Client.create();
        jerseyClient.addFilter(new HTTPBasicAuthFilter(adminUsername, adminAuthToken));
        provisioningURL = deploymentUrl + baseURL + "IncomingPhoneNumbers/Local.json";
        webResource = jerseyClient.resource(provisioningURL);
        formData = new MultivaluedMapImpl();
        formData.add("PhoneNumber", "+4156902867");
        formData.add("VoiceUrl", "http://demo.telestax.com/docs/voice.xml");
        formData.add("FriendlyName", "My Company Line");
        formData.add("VoiceMethod", "GET");
        clientResponse = webResource.type(MediaType.APPLICATION_FORM_URLENCODED_TYPE).accept("application/json").post(ClientResponse.class, formData);
        logger.info("testCreatePureSipPhoneNumber from default org TWICE clientResponse: " + clientResponse.toString());
        assertEquals(400, clientResponse.getStatus());

        /*
         * try to create same number again
         * under different organization/account,
         * it should not be allowed
         * */
        jerseyClient.addFilter(new HTTPBasicAuthFilter(adminOrg2Username, adminOrg2AuthToken));

        provisioningURL = deploymentUrl + baseURLOrg2 + "IncomingPhoneNumbers/Local.json";
        webResource = jerseyClient.resource(provisioningURL);

        formData = new MultivaluedMapImpl();
        formData.add("PhoneNumber", "+4156902867");
        formData.add("VoiceUrl", "http://demo.telestax.com/docs/voice.xml");
        formData.add("FriendlyName", "My Company Line");
        formData.add("VoiceMethod", "GET");
        clientResponse = webResource.type(MediaType.APPLICATION_FORM_URLENCODED_TYPE).accept("application/json").post(ClientResponse.class, formData);
        assertEquals(400, clientResponse.getStatus());

    }

    @Deployment(name = "IncomingPhoneNumbersEndpointTest", managed = true, testable = false)
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
