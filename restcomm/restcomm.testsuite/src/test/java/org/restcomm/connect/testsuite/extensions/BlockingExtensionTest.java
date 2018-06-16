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
package org.restcomm.connect.testsuite.extensions;

import com.cloudhopper.commons.charset.CharsetUtil;
import com.cloudhopper.smpp.type.SmppInvalidArgumentException;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;

import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import javax.sip.address.SipURI;
import javax.sip.message.Response;

import org.apache.log4j.Logger;
import org.cafesip.sipunit.SipCall;
import org.cafesip.sipunit.SipPhone;
import org.cafesip.sipunit.SipStack;
import org.jboss.arquillian.container.mss.extension.SipStackTool;
import org.jboss.arquillian.container.test.api.Deployer;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.FixMethodOrder;
import org.junit.runners.MethodSorters;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.restcomm.connect.commons.annotations.ParallelClassTests;
import org.restcomm.connect.testsuite.NetworkPortAssigner;
import org.restcomm.connect.testsuite.WebArchiveUtil;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.filter.HTTPBasicAuthFilter;
import com.sun.jersey.core.util.MultivaluedMapImpl;
import java.io.IOException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import junit.framework.Assert;
import org.cafesip.sipunit.Credential;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.restcomm.connect.testsuite.provisioning.number.nexmo.NexmoIncomingPhoneNumbersEndpointTestUtils;
import org.restcomm.connect.commons.annotations.FeatureAltTests;
import org.restcomm.connect.testsuite.http.NotificationEndpointTool;
import org.restcomm.connect.testsuite.provisioning.number.nexmo.NexmoAvailablePhoneNumbersEndpointTestUtils;
import org.restcomm.connect.testsuite.smpp.MockSmppServer;
import org.restcomm.connect.testsuite.tools.MonitoringServiceTool;

/**
 * Test for Dial Action attribute. Reference:
 * https://www.twilio.com/docs/api/twiml/dial#attributes-action The 'action'
 * attribute takes a URL as an argument. When the dialed call ends, Restcomm
 * will make a GET or POST request to this URL
 *
 * @author <a href="mailto:gvagenas@gmail.com">gvagenas</a>
 *
 */
@RunWith(Arquillian.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@Category(value = {ParallelClassTests.class})
public class BlockingExtensionTest {

    private final static Logger logger = Logger.getLogger(BlockingExtensionTest.class.getName());

    private static final byte[] bytes = new byte[]{118, 61, 48, 13, 10, 111, 61, 117, 115, 101, 114, 49, 32, 53, 51, 54, 53,
        53, 55, 54, 53, 32, 50, 51, 53, 51, 54, 56, 55, 54, 51, 55, 32, 73, 78, 32, 73, 80, 52, 32, 49, 50, 55, 46, 48, 46,
        48, 46, 49, 13, 10, 115, 61, 45, 13, 10, 99, 61, 73, 78, 32, 73, 80, 52, 32, 49, 50, 55, 46, 48, 46, 48, 46, 49,
        13, 10, 116, 61, 48, 32, 48, 13, 10, 109, 61, 97, 117, 100, 105, 111, 32, 54, 48, 48, 48, 32, 82, 84, 80, 47, 65,
        86, 80, 32, 48, 13, 10, 97, 61, 114, 116, 112, 109, 97, 112, 58, 48, 32, 80, 67, 77, 85, 47, 56, 48, 48, 48, 13, 10};
    private static final String body = new String(bytes);

    @ArquillianResource
    private Deployer deployer;
    @ArquillianResource
    URL deploymentUrl;

    private static int mediaPort = NetworkPortAssigner.retrieveNextPortByFile();

    private static int mockPort = NetworkPortAssigner.retrieveNextPortByFile();
    @Rule
    public WireMockRule wireMockRule = new WireMockRule(mockPort);

    private static int smppPort = NetworkPortAssigner.retrieveNextPortByFile();
    private static MockSmppServer mockSmppServer;

    private static SipStackTool tool1;
    private static SipStackTool tool2;

    // Bob is a simple SIP Client. Will not register with Restcomm
    private SipStack bobSipStack;
    private SipPhone bobPhone;
    private static String bobPort = String.valueOf(NetworkPortAssigner.retrieveNextPortByFile());
    private String bobContact = "sip:bob@127.0.0.1:" + bobPort;

    // Alice is a Restcomm Client with VoiceURL. This Restcomm Client can register with Restcomm and whatever will dial the RCML
    // of the VoiceURL will be executed.
    private SipStack aliceSipStack;
    private SipPhone alicePhone;
    private static String alicePort = String.valueOf(NetworkPortAssigner.retrieveNextPortByFile());
    private String aliceContact = "sip:alice1@127.0.0.1:" + alicePort;

    private String adminUsername = "first@company.com";
    private String adminAccountSid = "ACae6e420f425248d6a26948c17a9e2acf";
    private String adminAuthToken = "77f8c12cc7b8f8423e5c38b035249166";
    private String baseURL = "2012-04-24/Accounts/" + adminAccountSid + "/";
    private static String toUnresolvedNumber1 = "+19194347";
    private static String toUnresolvedNumber2 = "+69194347";
    private static String from = "9999";
    private static String msgBody1 = "Hello1!";

    private String allowBaseURL = "2012-04-24/Accounts/" + adminAccountSid + "/AvailablePhoneNumbers/";

    private static int restcommPort = 5080;
    private static int restcommHTTPPort = 8080;
    private static String restcommContact = "127.0.0.1:" + restcommPort;

    @BeforeClass
    public static void beforeClass() throws Exception {
        tool1 = new SipStackTool("DialActionTest1");
        tool2 = new SipStackTool("DialActionTest2");
        mockSmppServer = new MockSmppServer(smppPort);
        logger.info("Will wait for the SMPP link to be established");
        do {
            Thread.sleep(1000);
        } while (!mockSmppServer.isLinkEstablished());
        logger.info("SMPP link is now established");
    }

    public static void reconfigurePorts() {
        if (System.getProperty("arquillian_sip_port") != null) {
            restcommPort = Integer.valueOf(System.getProperty("arquillian_sip_port"));
            restcommContact = "127.0.0.1:" + restcommPort;
        }
        if (System.getProperty("arquillian_http_port") != null) {
            restcommHTTPPort = Integer.valueOf(System.getProperty("arquillian_http_port"));
        }
    }

    @Before
    public void before() throws Exception {
        bobSipStack = tool1.initializeSipStack(SipStack.PROTOCOL_UDP, "127.0.0.1", bobPort, restcommContact);
        bobPhone = bobSipStack.createSipPhone("127.0.0.1", SipStack.PROTOCOL_UDP, restcommPort, bobContact);

        aliceSipStack = tool2.initializeSipStack(SipStack.PROTOCOL_UDP, "127.0.0.1", alicePort, restcommContact);
        alicePhone = aliceSipStack.createSipPhone("127.0.0.1", SipStack.PROTOCOL_UDP, restcommPort, aliceContact);

        mockSmppServer.cleanup();
        Thread.sleep(5000);
    }

    @AfterClass
    public static void cleanup() {
        if (mockSmppServer != null) {
            mockSmppServer.stop();
        }
    }

    @After
    public void after() throws Exception {
        if (bobPhone != null) {
            bobPhone.dispose();
        }
        if (bobSipStack != null) {
            bobSipStack.dispose();
        }

        if (aliceSipStack != null) {
            aliceSipStack.dispose();
        }
        if (alicePhone != null) {
            alicePhone.dispose();
        }
        Thread.sleep(1000);
        wireMockRule.resetRequests();
        Thread.sleep(4000);
    }

    @AfterClass
    public static void afterClass() {
        System.gc();
        System.out.println("System.gc() run");
    }

    private String dialAliceRcml = "<Response><Dial><Client>alice1</Client></Dial></Response>";

    @Test
    public void inboundRcmlVoiceBlocked() throws ParseException, InterruptedException, MalformedURLException {

        stubFor(get(urlPathEqualTo("/1111"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(dialAliceRcml)));

        SipURI uri = aliceSipStack.getAddressFactory().createSipURI(null, restcommContact);
        assertTrue(alicePhone.register(uri, "alice1", "1234", aliceContact, 3600, 3600));

        // Prepare second phone to receive call
        SipCall aliceCall = alicePhone.createSipCall();
        aliceCall.listenForIncomingCall();

        // Create outgoing call with first phone
        final SipCall bobCall = bobPhone.createSipCall();
        bobCall.initiateOutgoingCall(bobContact, "sip:1111@" + restcommContact, null, body, "application", "sdp", null, null);
        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        final int response = bobCall.getLastReceivedResponse().getStatusCode();
        if (response == Response.TRYING) {
            assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
            assertEquals(Response.FORBIDDEN, bobCall.getLastReceivedResponse().getStatusCode());
            logger.info("Last response: " + bobCall.getLastReceivedResponse().getStatusCode());
        }

        JsonObject metrics = MonitoringServiceTool.getInstance().getMetrics(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        Map<String, Integer> mgcpResources = MonitoringServiceTool.getInstance().getMgcpResources(metrics);
        int mgcpEndpoints = mgcpResources.get("MgcpEndpoints");
        int mgcpConnections = mgcpResources.get("MgcpConnections");

        assertEquals(0, mgcpEndpoints);
        assertEquals(0, mgcpConnections);

    }

    private String smsRcml = "<Response><Sms to=\"alice1\">Hello World!</Sms></Response>";

    @Test
    @Category(value = {FeatureAltTests.class})
    public void inboundRcmlSmsBlocked() throws ParseException {
        stubFor(get(urlPathEqualTo("/smsApp1"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(smsRcml)));

        SipURI uri = aliceSipStack.getAddressFactory().createSipURI(null, restcommContact);
        assertTrue(alicePhone.register(uri, "alice1", "1234", aliceContact, 3600, 3600));

        Credential credential = new Credential("127.0.0.1", "alice1", "1234");
        alicePhone.addUpdateCredential(credential);

        // Prepare Alice to receive call
        final SipCall aliceCall = alicePhone.createSipCall();
        // Wait for a response sms.
        alicePhone.setLoopback(true);
        aliceCall.listenForMessage();

//        // Send restcomm an sms.
        final String proxy = bobPhone.getStackAddress() + ":" + restcommPort + ";lr/udp";
        final String to = "sip:6666@" + restcommContact;
        final String smsBody = "Hello, waiting your response!";
        final SipCall call = bobPhone.createSipCall();
        call.initiateOutgoingMessage(bobContact, to, proxy, null, null, smsBody);
        assertTrue(call.waitOutgoingCallResponse(5 * 1000));
        final int response = call.getLastReceivedResponse().getStatusCode();
        if (response == Response.TRYING) {
            assertTrue(call.waitOutgoingCallResponse(5 * 1000));
        }
        assertEquals(Response.FORBIDDEN, call.getLastReceivedResponse().getStatusCode());
        logger.info("Last response: " + call.getLastReceivedResponse().getStatusCode());
    }

    @Test
    public void incomingPhoneBlocked() throws Exception {
        stubFor(post(urlMatching("/nexmo/number/buy/.*/.*/US/14156902867"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(NexmoIncomingPhoneNumbersEndpointTestUtils.purchaseNumberSuccessResponse)));

        stubFor(post(urlMatching("/nexmo/number/update/.*/.*/US/14156902867.*"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(NexmoIncomingPhoneNumbersEndpointTestUtils.purchaseNumberSuccessResponse)));

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
        Assert.assertEquals(403, clientResponse.getStatus());

    }

    private static String to = "9999";
    private static String msgBodyResp = "Response from Restcomm to SMPP server";
    private static String msgBody = "Message from SMPP Server to Restcomm";
    private String smsEchoRcml = "<Response><Sms to=\"" + from + "\" from=\"" + to + "\">" + msgBodyResp + "</Sms></Response>";

    @Test
    public void inboundSMPPBlocked() throws SmppInvalidArgumentException, IOException, InterruptedException {

        stubFor(get(urlPathEqualTo("/smsApp"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(smsEchoRcml)));

        mockSmppServer.sendSmppMessageToRestcomm(msgBody, to, from, CharsetUtil.CHARSET_GSM);
        Thread.sleep(2000);
        assertTrue(mockSmppServer.isMessageSent());
        Thread.sleep(2000);
        assertFalse(mockSmppServer.isMessageReceived());

        JsonObject notifications = NotificationEndpointTool.getInstance().getNotificationList(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken
                );
        JsonArray notArray = notifications.get("notifications").getAsJsonArray();
        assertTrue("notification 11011 expected", notArray.size() > 0);
    }

    /*
     * https://www.twilio.com/docs/api/rest/available-phone-numbers#local-get-basic-example-1
     * available local phone numbers in the United States in the 510 area code.
     */
    @Test
    @Category(FeatureAltTests.class)
    public void avaiPhoneNumBlocked() {
        stubFor(get(urlMatching("/nexmo/number/search/.*/.*/CA\\?pattern=1450&search_pattern=0"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/json")
                        .withBody(NexmoAvailablePhoneNumbersEndpointTestUtils.bodyCA450AreaCode)));
        // Get Account using admin email address and user email address
        Client jerseyClient = Client.create();
        jerseyClient.addFilter(new HTTPBasicAuthFilter(adminUsername, adminAuthToken));

        String provisioningURL = deploymentUrl + allowBaseURL + "CA/Local.json";
        WebResource webResource = jerseyClient.resource(provisioningURL);

        ClientResponse clientResponse = webResource.queryParam("AreaCode", "450").accept("application/json")
                .get(ClientResponse.class);
        assertEquals(403, clientResponse.getStatus());
    }

    @Deployment(name = "DialAction", managed = true, testable = false)
    public static WebArchive createWebArchiveNoGw() {
        logger.info("Packaging Test App");
        reconfigurePorts();

        Map<String, String> replacements = new HashMap();
        //replace mediaport 2727
        replacements.put("2727", String.valueOf(mediaPort));
        replacements.put("2776", String.valueOf(smppPort));
        replacements.put("8080", String.valueOf(restcommHTTPPort));
        replacements.put("8090", String.valueOf(mockPort));
        replacements.put("5080", String.valueOf(restcommPort));
        replacements.put("5090", String.valueOf(bobPort));
        replacements.put("5091", String.valueOf(alicePort));
        Map<String, String> webInfResources = new HashMap();
        webInfResources.put("org/restcomm/connect/testsuite/extensions/restcomm.xml", "conf/restcomm.xml");
        webInfResources.put("org/restcomm/connect/testsuite/extensions/restcomm.script", "data/hsql/restcomm.script");
        webInfResources.put("akka_application.conf", "classes/application.conf");
        webInfResources.put("sip.xml", "/sip.xml");
        webInfResources.put("web.xml", "web.xml");
        webInfResources.put("org/restcomm/connect/testsuite/extensions/extensions_blocking.xml", "conf/extensions.xml");

        return WebArchiveUtil.createWebArchiveNoGw(
                webInfResources, new ArrayList(), replacements);
    }

}
