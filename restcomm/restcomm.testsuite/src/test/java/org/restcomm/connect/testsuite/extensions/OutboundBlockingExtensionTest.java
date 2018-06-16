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

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
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
import com.google.gson.JsonObject;
import com.sun.jersey.api.client.UniformInterfaceException;
import java.io.IOException;
import java.net.URISyntaxException;
import org.cafesip.sipunit.Credential;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.restcomm.connect.commons.annotations.FeatureAltTests;
import org.restcomm.connect.testsuite.sms.SmsEndpointTool;
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
public class OutboundBlockingExtensionTest {

    private final static Logger logger = Logger.getLogger(OutboundBlockingExtensionTest.class.getName());

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
    public void testOutboundVoiceBlocked() throws ParseException, InterruptedException, MalformedURLException, IOException, URISyntaxException {
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
            assertEquals(Response.RINGING, bobCall.getLastReceivedResponse().getStatusCode());
            logger.info("Last response: "+bobCall.getLastReceivedResponse().getStatusCode());
        }

        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        assertEquals(Response.OK, bobCall.getLastReceivedResponse().getStatusCode());
        assertTrue(bobCall.sendInviteOkAck());

        bobCall.listenForDisconnect();
        assertTrue(bobCall.waitForDisconnect(5000));
        assertTrue(bobCall.respondToDisconnect());


        JsonObject metrics = MonitoringServiceTool.getInstance().getMetrics(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
        Map<String, Integer> mgcpResources = MonitoringServiceTool.getInstance().getMgcpResources(metrics);
        int mgcpEndpoints = mgcpResources.get("MgcpEndpoints");
        int mgcpConnections = mgcpResources.get("MgcpConnections");

        assertEquals(0, mgcpEndpoints);
        assertEquals(0, mgcpConnections);

    }

    private String smsRcml = "<Response><Sms to=\"alice1\">Hello World!</Sms></Response>";


    @Test
    @Category(value = {FeatureAltTests.class})
    public void testOutboundSmsRcmlBlocked() throws ParseException, IOException, URISyntaxException {
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
            assertEquals(Response.FORBIDDEN, call.getLastReceivedResponse().getStatusCode());
            logger.info("Last response: " + call.getLastReceivedResponse().getStatusCode());
        }

        assertTrue(!aliceCall.waitForMessage(5000));
    }

    @Test
    @Category(value = {FeatureAltTests.class})
    public void testOutboundSmsEndpoint() throws ParseException, IOException, URISyntaxException {
        JsonObject callResult;
        try {
            callResult = SmsEndpointTool.getInstance().createSms(deploymentUrl.toString(), adminAccountSid, adminAuthToken, from, toUnresolvedNumber1, msgBody1, null);
            fail("expected error response");
        } catch (UniformInterfaceException e) {
            assertEquals(403, e.getResponse().getStatus());
        }

        try {
            callResult = SmsEndpointTool.getInstance().createSms(deploymentUrl.toString(), adminAccountSid, adminAuthToken, from, toUnresolvedNumber2, msgBody1, null);
            fail("expected error response");
        } catch (UniformInterfaceException e) {
            assertEquals(403, e.getResponse().getStatus());
        }
    }


    @Deployment(name = "DialAction", managed = true, testable = false)
    public static WebArchive createWebArchiveNoGw() {
        logger.info("Packaging Test App");
        reconfigurePorts();

        Map<String, String> replacements = new HashMap();
        //replace mediaport 2727
        replacements.put("2727", String.valueOf(mediaPort));
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
        webInfResources.put("org/restcomm/connect/testsuite/extensions/extensions_outbound_blocking.xml", "conf/extensions.xml");

        return WebArchiveUtil.createWebArchiveNoGw(
                webInfResources, new ArrayList(), replacements);
    }

}
