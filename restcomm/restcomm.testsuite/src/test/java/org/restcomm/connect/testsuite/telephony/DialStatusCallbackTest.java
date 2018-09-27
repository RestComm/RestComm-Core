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

package org.restcomm.connect.testsuite.telephony;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.findAll;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.cafesip.sipunit.SipAssert.assertLastOperationSuccess;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.sip.address.SipURI;
import javax.sip.message.Response;

import org.apache.log4j.Logger;
import org.cafesip.sipunit.SipCall;
import org.cafesip.sipunit.SipPhone;
import org.cafesip.sipunit.SipStack;
import org.cafesip.sipunit.SipTransaction;
import org.jboss.arquillian.container.mss.extension.SipStackTool;
import org.jboss.arquillian.container.test.api.Deployer;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.FixMethodOrder;
import org.junit.runners.MethodSorters;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.restcomm.connect.commons.Version;
import org.restcomm.connect.commons.annotations.FeatureAltTests;
import org.restcomm.connect.commons.annotations.ParallelClassTests;
import org.restcomm.connect.commons.annotations.UnstableTests;
import org.restcomm.connect.testsuite.NetworkPortAssigner;
import org.restcomm.connect.testsuite.WebArchiveUtil;
import org.restcomm.connect.testsuite.http.RestcommCallsTool;
import org.restcomm.connect.testsuite.tools.MonitoringServiceTool;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.google.gson.JsonObject;

/**
 * Test for Dial status callback attribute. Reference: The 'statuscallback'
 * attribute takes a URL as an argument. As the call moves states, Restcomm will make a GET or POST request to this URL
 *
 * @author <a href="mailto:gvagenas@gmail.com">gvagenas</a>
 *
 */
@RunWith(Arquillian.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@Category(value={ParallelClassTests.class})
public class DialStatusCallbackTest {

    private final static Logger logger = Logger.getLogger(DialStatusCallbackTest.class.getName());

    private static final String version = Version.getVersion();
    private static final byte[] bytes = new byte[] { 118, 61, 48, 13, 10, 111, 61, 117, 115, 101, 114, 49, 32, 53, 51, 54, 53,
        53, 55, 54, 53, 32, 50, 51, 53, 51, 54, 56, 55, 54, 51, 55, 32, 73, 78, 32, 73, 80, 52, 32, 49, 50, 55, 46, 48, 46,
        48, 46, 49, 13, 10, 115, 61, 45, 13, 10, 99, 61, 73, 78, 32, 73, 80, 52, 32, 49, 50, 55, 46, 48, 46, 48, 46, 49,
        13, 10, 116, 61, 48, 32, 48, 13, 10, 109, 61, 97, 117, 100, 105, 111, 32, 54, 48, 48, 48, 32, 82, 84, 80, 47, 65,
        86, 80, 32, 48, 13, 10, 97, 61, 114, 116, 112, 109, 97, 112, 58, 48, 32, 80, 67, 77, 85, 47, 56, 48, 48, 48, 13, 10 };
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
    private static SipStackTool tool3;
    private static SipStackTool tool4;

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
    private String aliceContact = "sip:alice@127.0.0.1:" + alicePort;

    // Henrique is a simple SIP Client. Will not register with Restcomm
    private SipStack henriqueSipStack;
    private SipPhone henriquePhone;
    private static String henriquePort = String.valueOf(NetworkPortAssigner.retrieveNextPortByFile());
    private String henriqueContact = "sip:henrique@127.0.0.1:" + henriquePort;

    // George is a simple SIP Client. Will not register with Restcomm
    private SipStack georgeSipStack;
    private SipPhone georgePhone;
    private static String georgePort = String.valueOf(NetworkPortAssigner.retrieveNextPortByFile());
    private String georgeContact = "sip:+131313@127.0.0.1:" + georgePort;

    private String dialRestcomm = "sip:1111@" + restcommContact; // Application: dial-client-entry_wActionUrl.xml

    private String adminAccountSid = "ACae6e420f425248d6a26948c17a9e2acf";
    private String adminAuthToken = "77f8c12cc7b8f8423e5c38b035249166";

    private static int restcommPort = 5080;
    private static int restcommHTTPPort = 8080;
    private static String restcommContact = "127.0.0.1:" + restcommPort;

    @BeforeClass
    public static void beforeClass() throws Exception {
        tool1 = new SipStackTool("DialActionTest1");
        tool2 = new SipStackTool("DialActionTest2");
        tool3 = new SipStackTool("DialActionTest3");
        tool4 = new SipStackTool("DialActionTest4");
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

        henriqueSipStack = tool3.initializeSipStack(SipStack.PROTOCOL_UDP, "127.0.0.1", henriquePort, restcommContact);
        henriquePhone = henriqueSipStack.createSipPhone("127.0.0.1", SipStack.PROTOCOL_UDP, restcommPort, henriqueContact);

        georgeSipStack = tool4.initializeSipStack(SipStack.PROTOCOL_UDP, "127.0.0.1", georgePort, restcommContact);
        georgePhone = georgeSipStack.createSipPhone("127.0.0.1", SipStack.PROTOCOL_UDP, restcommPort, georgeContact);
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

        if (henriqueSipStack != null) {
            henriqueSipStack.dispose();
        }
        if (henriquePhone != null) {
            henriquePhone.dispose();
        }

        if (georgePhone != null) {
            georgePhone.dispose();
        }
        if (georgeSipStack != null) {
            georgeSipStack.dispose();
        }
        Thread.sleep(1000);
        wireMockRule.resetRequests();
        Thread.sleep(4000);
    }

    private String dialStatusCallback = "<Response><Dial><Client statusCallback=\"http://127.0.0.1:" + mockPort + "/status\">alice</Client></Dial></Response>";
    @Test
    public void testDialStatusCallbackAliceDisconnects() throws ParseException, InterruptedException {

        stubFor(get(urlPathEqualTo("/1111"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(dialStatusCallback)));

        stubFor(post(urlPathMatching("/status.*"))
                .willReturn(aResponse()
                    .withStatus(200)));

        // Register Alice
        SipURI uri = aliceSipStack.getAddressFactory().createSipURI(null, restcommContact);
        assertTrue(alicePhone.register(uri, "alice", "1234", aliceContact, 3600, 3600));

        // Prepare Alice to receive call
        final SipCall aliceCall = alicePhone.createSipCall();
        aliceCall.listenForIncomingCall();

        // Create outgoing call with first phone
        final SipCall bobCall = bobPhone.createSipCall();
        bobCall.initiateOutgoingCall(bobContact, dialRestcomm, null, body, "application", "sdp", null, null);
        assertLastOperationSuccess(bobCall);
        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        final int response = bobCall.getLastReceivedResponse().getStatusCode();
        assertTrue(response == Response.TRYING || response == Response.RINGING);

        if (response == Response.TRYING) {
            assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
            assertEquals(Response.RINGING, bobCall.getLastReceivedResponse().getStatusCode());
        }

        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        assertEquals(Response.OK, bobCall.getLastReceivedResponse().getStatusCode());
        bobCall.sendInviteOkAck();

        assertTrue(aliceCall.waitForIncomingCall(5000));
        assertTrue(aliceCall.sendIncomingCallResponse(Response.TRYING, "Alice-Trying", 3600));
        assertTrue(aliceCall.sendIncomingCallResponse(Response.RINGING, "Alice-Ringing", 3600));
        String receivedBody = new String(aliceCall.getLastReceivedRequest().getRawContent());
        assertTrue(aliceCall.sendIncomingCallResponse(Response.OK, "Alice-OK", 3600, receivedBody, "application", "sdp",
                null, null));
        assertTrue(aliceCall.waitForAck(5000));

        Thread.sleep(1000);
        int liveCalls = MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
        int liveIncomingCalls = MonitoringServiceTool.getInstance().getLiveIncomingCallStatistics(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
        int liveOutgoingCalls = MonitoringServiceTool.getInstance().getLiveOutgoingCallStatistics(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
        int liveCallsArraySize = MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
        assertTrue(liveCalls==2);
        assertTrue(liveIncomingCalls==1);
        assertTrue(liveOutgoingCalls==1);
        assertTrue(liveCallsArraySize==2);


        Thread.sleep(3000);
        bobCall.listenForDisconnect();

        assertTrue(aliceCall.disconnect());
        Thread.sleep(500);
        assertTrue(bobCall.waitForDisconnect(5000));
        assertTrue(bobCall.respondToDisconnect());

        Thread.sleep(10000);

        logger.info("About to check the StatusCallback Requests");
        List<LoggedRequest> requests = findAll(postRequestedFor(urlPathMatching("/status.*")));
        assertEquals(4, requests.size());
        String requestBody = requests.get(0).getBodyAsString();
        assertTrue(requestBody.contains("SequenceNumber=0"));
        assertTrue(requestBody.contains("CallStatus=initiated"));

        requestBody = requests.get(1).getBodyAsString();
        assertTrue(requestBody.contains("SequenceNumber=1"));
        assertTrue(requestBody.contains("CallStatus=ringing"));

        requestBody = requests.get(2).getBodyAsString();
        assertTrue(requestBody.contains("SequenceNumber=2"));
        assertTrue(requestBody.contains("CallStatus=answered"));

        requestBody = requests.get(3).getBodyAsString();
        assertTrue(requestBody.contains("SequenceNumber=3"));
        assertTrue(requestBody.contains("CallStatus=completed"));

        liveCalls = MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
        liveIncomingCalls = MonitoringServiceTool.getInstance().getLiveIncomingCallStatistics(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
        liveOutgoingCalls = MonitoringServiceTool.getInstance().getLiveOutgoingCallStatistics(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
        liveCallsArraySize = MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
        assertTrue(liveCalls==0);
        assertTrue(liveIncomingCalls==0);
        assertTrue(liveOutgoingCalls==0);
        assertTrue(liveCallsArraySize==0);
    }

    @Test
    public void testDialStatusCallbackAliceUnavailable() throws ParseException, InterruptedException {

        stubFor(get(urlPathEqualTo("/1111"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(dialStatusCallback)));

        stubFor(post(urlPathMatching("/status.*"))
                .willReturn(aResponse()
                        .withStatus(200)));

        // Register Alice
        SipURI uri = aliceSipStack.getAddressFactory().createSipURI(null, restcommContact);
        assertTrue(alicePhone.register(uri, "alice", "1234", aliceContact, 3600, 3600));

        // Prepare Alice to receive call
        final SipCall aliceCall = alicePhone.createSipCall();
        aliceCall.listenForIncomingCall();

        // Create outgoing call with first phone
        final SipCall bobCall = bobPhone.createSipCall();
        bobCall.initiateOutgoingCall(bobContact, dialRestcomm, null, body, "application", "sdp", null, null);
        assertLastOperationSuccess(bobCall);
        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        final int response = bobCall.getLastReceivedResponse().getStatusCode();
        assertTrue(response == Response.TRYING || response == Response.RINGING);

        if (response == Response.TRYING) {
            assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
            assertEquals(Response.RINGING, bobCall.getLastReceivedResponse().getStatusCode());
        }

        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        assertEquals(Response.OK, bobCall.getLastReceivedResponse().getStatusCode());
        bobCall.sendInviteOkAck();

        assertTrue(aliceCall.waitForIncomingCall(5000));
        assertTrue(aliceCall.sendIncomingCallResponse(Response.TRYING, "Alice-Trying", 3600));
        assertTrue(aliceCall.sendIncomingCallResponse(Response.RINGING, "Alice-Ringing", 3600));
        String receivedBody = new String(aliceCall.getLastReceivedRequest().getRawContent());

        assertTrue(aliceCall.sendIncomingCallResponse(Response.TEMPORARILY_UNAVAILABLE, "Alice-Unavailable", 3600, receivedBody, "application", "sdp",
                null, null));
        assertTrue(aliceCall.waitForAck(5000));

        bobCall.listenForDisconnect();
        assertTrue(bobCall.waitForDisconnect(5000));
        assertTrue(bobCall.respondToDisconnect());

        Thread.sleep(5000);

        logger.info("About to check the StatusCallback Requests");
        List<LoggedRequest> requests = findAll(postRequestedFor(urlPathMatching("/status.*")));
        assertEquals(3, requests.size());
        String requestBody = requests.get(0).getBodyAsString();
        assertTrue(requestBody.contains("SequenceNumber=0"));
        assertTrue(requestBody.contains("CallStatus=initiated"));

        requestBody = requests.get(1).getBodyAsString();
        assertTrue(requestBody.contains("SequenceNumber=1"));
        assertTrue(requestBody.contains("CallStatus=ringing"));

        requestBody = requests.get(2).getBodyAsString();
        assertTrue(requestBody.contains("SequenceNumber=3"));
        assertTrue(requestBody.contains("CallStatus=completed"));

        int liveCalls = MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
        int liveIncomingCalls = MonitoringServiceTool.getInstance().getLiveIncomingCallStatistics(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
        int liveOutgoingCalls = MonitoringServiceTool.getInstance().getLiveOutgoingCallStatistics(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
        int liveCallsArraySize = MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
        assertTrue(liveCalls==0);
        assertTrue(liveIncomingCalls==0);
        assertTrue(liveOutgoingCalls==0);
        assertTrue(liveCallsArraySize==0);
    }

    private String dialStatusCallbackGetMethod = "<Response><Dial><Client statusCallback=\"http://127.0.0.1:" + mockPort + "/status\" " +
            "statusCallbackMethod=\"get\">alice</Client></Dial></Response>";
    @Test
    @Category(FeatureAltTests.class)
    public void testDialStatusCallbackMethodGET() throws ParseException, InterruptedException {

        stubFor(get(urlPathEqualTo("/1111"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(dialStatusCallbackGetMethod)));

        stubFor(get(urlPathMatching("/status.*"))
                .willReturn(aResponse()
                        .withStatus(200)));

        // Register Alice
        SipURI uri = aliceSipStack.getAddressFactory().createSipURI(null, restcommContact);
        assertTrue(alicePhone.register(uri, "alice", "1234", aliceContact, 3600, 3600));

        // Prepare Alice to receive call
        final SipCall aliceCall = alicePhone.createSipCall();
        aliceCall.listenForIncomingCall();

        // Create outgoing call with first phone
        final SipCall bobCall = bobPhone.createSipCall();
        bobCall.initiateOutgoingCall(bobContact, dialRestcomm, null, body, "application", "sdp", null, null);
        assertLastOperationSuccess(bobCall);
        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        final int response = bobCall.getLastReceivedResponse().getStatusCode();
        assertTrue(response == Response.TRYING || response == Response.RINGING);

        if (response == Response.TRYING) {
            assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
            assertEquals(Response.RINGING, bobCall.getLastReceivedResponse().getStatusCode());
        }

        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        assertEquals(Response.OK, bobCall.getLastReceivedResponse().getStatusCode());
        bobCall.sendInviteOkAck();

        assertTrue(aliceCall.waitForIncomingCall(5000));
        assertTrue(aliceCall.sendIncomingCallResponse(Response.TRYING, "Alice-Trying", 3600));
        assertTrue(aliceCall.sendIncomingCallResponse(Response.RINGING, "Alice-Ringing", 3600));
        String receivedBody = new String(aliceCall.getLastReceivedRequest().getRawContent());
        assertTrue(aliceCall.sendIncomingCallResponse(Response.OK, "Alice-OK", 3600, receivedBody, "application", "sdp",
                null, null));
        assertTrue(aliceCall.waitForAck(5000));

        Thread.sleep(1000);
        int liveCalls = MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
        int liveIncomingCalls = MonitoringServiceTool.getInstance().getLiveIncomingCallStatistics(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
        int liveOutgoingCalls = MonitoringServiceTool.getInstance().getLiveOutgoingCallStatistics(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
        int liveCallsArraySize = MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
        assertTrue(liveCalls==2);
        assertTrue(liveIncomingCalls==1);
        assertTrue(liveOutgoingCalls==1);
        assertTrue(liveCallsArraySize==2);


        Thread.sleep(3000);
        bobCall.listenForDisconnect();

        assertTrue(aliceCall.disconnect());
        Thread.sleep(500);
        assertTrue(bobCall.waitForDisconnect(5000));
        assertTrue(bobCall.respondToDisconnect());

        Thread.sleep(10000);

        logger.info("About to check the StatusCallback Requests");
        List<LoggedRequest> requests = findAll(getRequestedFor(urlPathMatching("/status.*")));
        assertEquals(4, requests.size());

        String requestUrl = requests.get(0).getUrl();
        assertTrue(requestUrl.contains("SequenceNumber=0"));
        assertTrue(requestUrl.contains("CallStatus=initiated"));

        requestUrl = requests.get(1).getUrl();
        assertTrue(requestUrl.contains("SequenceNumber=1"));
        assertTrue(requestUrl.contains("CallStatus=ringing"));

        requestUrl = requests.get(2).getUrl();
        assertTrue(requestUrl.contains("SequenceNumber=2"));
        assertTrue(requestUrl.contains("CallStatus=answered"));

        requestUrl = requests.get(3).getUrl();
        assertTrue(requestUrl.contains("SequenceNumber=3"));
        assertTrue(requestUrl.contains("CallStatus=completed"));

        liveCalls = MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
        liveIncomingCalls = MonitoringServiceTool.getInstance().getLiveIncomingCallStatistics(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
        liveOutgoingCalls = MonitoringServiceTool.getInstance().getLiveOutgoingCallStatistics(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
        liveCallsArraySize = MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
        assertTrue(liveCalls==0);
        assertTrue(liveIncomingCalls==0);
        assertTrue(liveOutgoingCalls==0);
        assertTrue(liveCallsArraySize==0);
    }

    private String dialStatusCallbackGet = "<Response><Dial><Client statusCallback=\"http://127.0.0.1:" + mockPort +"/status\" statusCallbackMethod=\"GET\">alice</Client></Dial></Response>";
    @Test
    @Category({UnstableTests.class, FeatureAltTests.class})
    public void testDialStatusCallbackBobDisconnects() throws ParseException, InterruptedException {

        stubFor(get(urlPathEqualTo("/1111"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(dialStatusCallback)));

        stubFor(post(urlPathMatching("/status.*"))
                .willReturn(aResponse()
                        .withStatus(200)));

        stubFor(get(urlPathMatching("/status.*"))
                .willReturn(aResponse()
                        .withStatus(200)));

        // Register Alice
        SipURI uri = aliceSipStack.getAddressFactory().createSipURI(null, restcommContact);
        assertTrue(alicePhone.register(uri, "alice", "1234", aliceContact, 3600, 3600));

        // Prepare Alice to receive call
        final SipCall aliceCall = alicePhone.createSipCall();
        aliceCall.listenForIncomingCall();

        // Create outgoing call with first phone
        final SipCall bobCall = bobPhone.createSipCall();
        bobCall.initiateOutgoingCall(bobContact, dialRestcomm, null, body, "application", "sdp", null, null);
        assertLastOperationSuccess(bobCall);
        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        final int response = bobCall.getLastReceivedResponse().getStatusCode();
        assertTrue(response == Response.TRYING || response == Response.RINGING);

        if (response == Response.TRYING) {
            assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
            assertEquals(Response.RINGING, bobCall.getLastReceivedResponse().getStatusCode());
        }

        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        assertEquals(Response.OK, bobCall.getLastReceivedResponse().getStatusCode());
        bobCall.sendInviteOkAck();

        assertTrue(aliceCall.waitForIncomingCall(5000));
        assertTrue(aliceCall.sendIncomingCallResponse(Response.TRYING, "Alice-Trying", 3600));
        assertTrue(aliceCall.sendIncomingCallResponse(Response.RINGING, "Alice-Ringing", 3600));
        String receivedBody = new String(aliceCall.getLastReceivedRequest().getRawContent());
        assertTrue(aliceCall.sendIncomingCallResponse(Response.OK, "Alice-OK", 3600, receivedBody, "application", "sdp",
                null, null));
        assertTrue(aliceCall.waitForAck(5000));

        Thread.sleep(1000);
        int liveCalls = MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
        int liveIncomingCalls = MonitoringServiceTool.getInstance().getLiveIncomingCallStatistics(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
        int liveOutgoingCalls = MonitoringServiceTool.getInstance().getLiveOutgoingCallStatistics(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
        int liveCallsArraySize = MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
        assertTrue(liveCalls==2);
        assertTrue(liveIncomingCalls==1);
        assertTrue(liveOutgoingCalls==1);
        assertTrue(liveCallsArraySize==2);


        Thread.sleep(3000);
        aliceCall.listenForDisconnect();

        assertTrue(bobCall.disconnect());
        Thread.sleep(500);
        assertTrue(aliceCall.waitForDisconnect(5000));
        assertTrue(aliceCall.respondToDisconnect());

        Thread.sleep(10000);

        logger.info("About to check the StatusCallback Requests");
        List<LoggedRequest> requests = findAll(postRequestedFor(urlPathMatching("/status.*")));
//        List<LoggedRequest> requests = findAll(getRequestedFor(urlPathMatching("/status.*")));
        assertEquals(4, requests.size());
        String requestBody = requests.get(0).getBodyAsString();
        assertTrue(requestBody.contains("SequenceNumber=0"));
        assertTrue(requestBody.contains("CallStatus=initiated"));

        requestBody = requests.get(1).getBodyAsString();
        assertTrue(requestBody.contains("SequenceNumber=1"));
        assertTrue(requestBody.contains("CallStatus=ringing"));

        requestBody = requests.get(2).getBodyAsString();
        assertTrue(requestBody.contains("SequenceNumber=2"));
        assertTrue(requestBody.contains("CallStatus=answered"));

        requestBody = requests.get(3).getBodyAsString();
        assertTrue(requestBody.contains("SequenceNumber=3"));
        assertTrue(requestBody.contains("CallStatus=completed"));

        liveCalls = MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
        liveIncomingCalls = MonitoringServiceTool.getInstance().getLiveIncomingCallStatistics(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
        liveOutgoingCalls = MonitoringServiceTool.getInstance().getLiveOutgoingCallStatistics(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
        liveCallsArraySize = MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
        assertTrue(liveCalls==0);
        assertTrue(liveIncomingCalls==0);
        assertTrue(liveOutgoingCalls==0);
        assertTrue(liveCallsArraySize==0);
    }

    private String dialStatusCallbackOnlyInitiatedAndAnswer = "<Response><Dial><Client statusCallback=\"http://127.0.0.1:"+ mockPort + "/status\" " +
            "statusCallbackEvent=\"initiated,  answered\">alice</Client></Dial></Response>";
    @Test
    @Category(FeatureAltTests.class)
    public void testDialStatusCallbackOnlyInitiatedAnswerEvent() throws ParseException, InterruptedException {

        stubFor(get(urlPathEqualTo("/1111"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(dialStatusCallbackOnlyInitiatedAndAnswer)));

        stubFor(post(urlPathMatching("/status.*"))
                .willReturn(aResponse()
                        .withStatus(200)));

        // Register Alice
        SipURI uri = aliceSipStack.getAddressFactory().createSipURI(null, restcommContact);
        assertTrue(alicePhone.register(uri, "alice", "1234", aliceContact, 3600, 3600));

        // Prepare Alice to receive call
        final SipCall aliceCall = alicePhone.createSipCall();
        aliceCall.listenForIncomingCall();

        // Create outgoing call with first phone
        final SipCall bobCall = bobPhone.createSipCall();
        bobCall.initiateOutgoingCall(bobContact, dialRestcomm, null, body, "application", "sdp", null, null);
        assertLastOperationSuccess(bobCall);
        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        final int response = bobCall.getLastReceivedResponse().getStatusCode();
        assertTrue(response == Response.TRYING || response == Response.RINGING);

        if (response == Response.TRYING) {
            assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
            assertEquals(Response.RINGING, bobCall.getLastReceivedResponse().getStatusCode());
        }

        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        assertEquals(Response.OK, bobCall.getLastReceivedResponse().getStatusCode());
        bobCall.sendInviteOkAck();

        assertTrue(aliceCall.waitForIncomingCall(5000));
        assertTrue(aliceCall.sendIncomingCallResponse(Response.TRYING, "Alice-Trying", 3600));
        assertTrue(aliceCall.sendIncomingCallResponse(Response.RINGING, "Alice-Ringing", 3600));
        String receivedBody = new String(aliceCall.getLastReceivedRequest().getRawContent());
        assertTrue(aliceCall.sendIncomingCallResponse(Response.OK, "Alice-OK", 3600, receivedBody, "application", "sdp",
                null, null));
        assertTrue(aliceCall.waitForAck(5000));

        Thread.sleep(1000);
        int liveCalls = MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
        int liveIncomingCalls = MonitoringServiceTool.getInstance().getLiveIncomingCallStatistics(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
        int liveOutgoingCalls = MonitoringServiceTool.getInstance().getLiveOutgoingCallStatistics(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
        int liveCallsArraySize = MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
        assertTrue(liveCalls==2);
        assertTrue(liveIncomingCalls==1);
        assertTrue(liveOutgoingCalls==1);
        assertTrue(liveCallsArraySize==2);


        Thread.sleep(3000);
        aliceCall.listenForDisconnect();

        assertTrue(bobCall.disconnect());
        Thread.sleep(500);
        assertTrue(aliceCall.waitForDisconnect(5000));
        assertTrue(aliceCall.respondToDisconnect());

        Thread.sleep(10000);

        logger.info("About to check the StatusCallback Requests");
        List<LoggedRequest> requests = findAll(postRequestedFor(urlPathMatching("/status.*")));
        assertEquals(2, requests.size());
        String requestBody = requests.get(0).getBodyAsString();
        assertTrue(requestBody.contains("SequenceNumber=0"));
        assertTrue(requestBody.contains("CallStatus=initiated"));

        requestBody = requests.get(1).getBodyAsString();
        assertTrue(requestBody.contains("SequenceNumber=2"));
        assertTrue(requestBody.contains("CallStatus=answered"));

        liveCalls = MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
        liveIncomingCalls = MonitoringServiceTool.getInstance().getLiveIncomingCallStatistics(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
        liveOutgoingCalls = MonitoringServiceTool.getInstance().getLiveOutgoingCallStatistics(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
        liveCallsArraySize = MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
        assertTrue(liveCalls==0);
        assertTrue(liveIncomingCalls==0);
        assertTrue(liveOutgoingCalls==0);
        assertTrue(liveCallsArraySize==0);
    }

    private String dialStatusCallbackOnlyRingingCompleted = "<Response><Dial><Client statusCallback=\"http://127.0.0.1:" + mockPort + "/status\" " +
            "statusCallbackEvent=\"ringing,completed\">alice</Client></Dial></Response>";
    @Test
    @Category({UnstableTests.class, FeatureAltTests.class})
    public void testDialStatusCallbackOnlyRingingCompleted() throws ParseException, InterruptedException {

        stubFor(get(urlPathEqualTo("/1111"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(dialStatusCallbackOnlyRingingCompleted)));

        stubFor(post(urlPathMatching("/status.*"))
                .willReturn(aResponse()
                        .withStatus(200)));

        // Register Alice
        SipURI uri = aliceSipStack.getAddressFactory().createSipURI(null, restcommContact);
        assertTrue(alicePhone.register(uri, "alice", "1234", aliceContact, 3600, 3600));

        // Prepare Alice to receive call
        final SipCall aliceCall = alicePhone.createSipCall();
        aliceCall.listenForIncomingCall();

        // Create outgoing call with first phone
        final SipCall bobCall = bobPhone.createSipCall();
        bobCall.initiateOutgoingCall(bobContact, dialRestcomm, null, body, "application", "sdp", null, null);
        assertLastOperationSuccess(bobCall);
        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        final int response = bobCall.getLastReceivedResponse().getStatusCode();
        assertTrue(response == Response.TRYING || response == Response.RINGING);

        if (response == Response.TRYING) {
            assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
            assertEquals(Response.RINGING, bobCall.getLastReceivedResponse().getStatusCode());
        }

        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        assertEquals(Response.OK, bobCall.getLastReceivedResponse().getStatusCode());
        bobCall.sendInviteOkAck();

        assertTrue(aliceCall.waitForIncomingCall(5000));
        assertTrue(aliceCall.sendIncomingCallResponse(Response.TRYING, "Alice-Trying", 3600));
        assertTrue(aliceCall.sendIncomingCallResponse(Response.RINGING, "Alice-Ringing", 3600));
        String receivedBody = new String(aliceCall.getLastReceivedRequest().getRawContent());
        assertTrue(aliceCall.sendIncomingCallResponse(Response.OK, "Alice-OK", 3600, receivedBody, "application", "sdp",
                null, null));
        assertTrue(aliceCall.waitForAck(5000));

        Thread.sleep(1000);
        int liveCalls = MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
        int liveIncomingCalls = MonitoringServiceTool.getInstance().getLiveIncomingCallStatistics(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
        int liveOutgoingCalls = MonitoringServiceTool.getInstance().getLiveOutgoingCallStatistics(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
        int liveCallsArraySize = MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
        assertTrue(liveCalls==2);
        assertTrue(liveIncomingCalls==1);
        assertTrue(liveOutgoingCalls==1);
        assertTrue(liveCallsArraySize==2);


        Thread.sleep(3000);
        aliceCall.listenForDisconnect();

        assertTrue(bobCall.disconnect());
        Thread.sleep(500);
        assertTrue(aliceCall.waitForDisconnect(5000));
        assertTrue(aliceCall.respondToDisconnect());

        Thread.sleep(12000);

        logger.info("About to check the StatusCallback Requests");
        List<LoggedRequest> requests = findAll(postRequestedFor(urlPathMatching("/status.*")));
        assertEquals(2, requests.size());
        String requestBody = requests.get(0).getBodyAsString();
        assertTrue(requestBody.contains("SequenceNumber=1"));
        assertTrue(requestBody.contains("CallStatus=ringing"));

        requestBody = requests.get(1).getBodyAsString();
        assertTrue(requestBody.contains("SequenceNumber=3"));
        assertTrue(requestBody.contains("CallStatus=completed"));

        liveCalls = MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
        liveIncomingCalls = MonitoringServiceTool.getInstance().getLiveIncomingCallStatistics(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
        liveOutgoingCalls = MonitoringServiceTool.getInstance().getLiveOutgoingCallStatistics(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
        liveCallsArraySize = MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
        assertTrue(liveCalls==0);
        assertTrue(liveIncomingCalls==0);
        assertTrue(liveOutgoingCalls==0);
        assertTrue(liveCallsArraySize==0);
    }

    private String dialStatusCallbackForSip = "<Response><Dial><Sip statusCallback=\"http://127.0.0.1:" + mockPort +"/status\">sip:henrique@127.0.0.1:" + henriquePort + "</Sip></Dial></Response>";
    @Test
    public void testDialStatusCallbackDialSip() throws ParseException, InterruptedException {

        stubFor(get(urlPathEqualTo("/1111"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(dialStatusCallbackForSip)));

        stubFor(post(urlPathMatching("/status.*"))
                .willReturn(aResponse()
                        .withStatus(200)));

        final SipCall henriqueCall = henriquePhone.createSipCall();
        henriqueCall.listenForIncomingCall();

        // Create outgoing call with first phone
        final SipCall bobCall = bobPhone.createSipCall();
        bobCall.initiateOutgoingCall(bobContact, dialRestcomm, null, body, "application", "sdp", null, null);
        assertLastOperationSuccess(bobCall);
        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        final int response = bobCall.getLastReceivedResponse().getStatusCode();
        assertTrue(response == Response.TRYING || response == Response.RINGING);

        if (response == Response.TRYING) {
            assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
            assertEquals(Response.RINGING, bobCall.getLastReceivedResponse().getStatusCode());
        }

        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        assertEquals(Response.OK, bobCall.getLastReceivedResponse().getStatusCode());
        bobCall.sendInviteOkAck();

        assertTrue(henriqueCall.waitForIncomingCall(5000));
        assertTrue(henriqueCall.sendIncomingCallResponse(Response.TRYING, "Henrique-Trying", 3600));
        assertTrue(henriqueCall.sendIncomingCallResponse(Response.RINGING, "Henrique-Ringing", 3600));
        String receivedBody = new String(henriqueCall.getLastReceivedRequest().getRawContent());
        assertTrue(henriqueCall.sendIncomingCallResponse(Response.OK, "Henrique-OK", 3600, receivedBody, "application", "sdp",
                null, null));
        assertTrue(henriqueCall.waitForAck(5000));

        Thread.sleep(1000);
        int liveCalls = MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
        int liveIncomingCalls = MonitoringServiceTool.getInstance().getLiveIncomingCallStatistics(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
        int liveOutgoingCalls = MonitoringServiceTool.getInstance().getLiveOutgoingCallStatistics(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
        int liveCallsArraySize = MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
        assertTrue(liveCalls==2);
        assertTrue(liveIncomingCalls==1);
        assertTrue(liveOutgoingCalls==1);
        assertTrue(liveCallsArraySize==2);


        Thread.sleep(3000);
        bobCall.listenForDisconnect();

        assertTrue(henriqueCall.disconnect());
        Thread.sleep(500);
        assertTrue(bobCall.waitForDisconnect(5000));
        assertTrue(bobCall.respondToDisconnect());

        Thread.sleep(10000);

        logger.info("About to check the StatusCallback Requests");
        List<LoggedRequest> requests = findAll(postRequestedFor(urlPathMatching("/status.*")));
        assertEquals(4, requests.size());
        String requestBody = requests.get(0).getBodyAsString();
        assertTrue(requestBody.contains("SequenceNumber=0"));
        assertTrue(requestBody.contains("CallStatus=initiated"));

        requestBody = requests.get(1).getBodyAsString();
        assertTrue(requestBody.contains("SequenceNumber=1"));
        assertTrue(requestBody.contains("CallStatus=ringing"));

        requestBody = requests.get(2).getBodyAsString();
        assertTrue(requestBody.contains("SequenceNumber=2"));
        assertTrue(requestBody.contains("CallStatus=answered"));

        requestBody = requests.get(3).getBodyAsString();
        assertTrue(requestBody.contains("SequenceNumber=3"));
        assertTrue(requestBody.contains("CallStatus=completed"));

        liveCalls = MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
        liveIncomingCalls = MonitoringServiceTool.getInstance().getLiveIncomingCallStatistics(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
        liveOutgoingCalls = MonitoringServiceTool.getInstance().getLiveOutgoingCallStatistics(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
        liveCallsArraySize = MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
        assertTrue(liveCalls==0);
        assertTrue(liveIncomingCalls==0);
        assertTrue(liveOutgoingCalls==0);
        assertTrue(liveCallsArraySize==0);
    }

    private String dialStatusCallbackForNumber = "<Response><Dial><Number statusCallback=\"http://127.0.0.1:" + mockPort + "/status\">+131313</Number></Dial></Response>";
    @Test
    public void testDialStatusCallbackDialNumber() throws ParseException, InterruptedException {

        stubFor(get(urlPathEqualTo("/1111"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(dialStatusCallbackForNumber)));

        stubFor(post(urlPathMatching("/status.*"))
                .willReturn(aResponse()
                        .withStatus(200)));

        final SipCall georgeCall = georgePhone.createSipCall();
        georgeCall.listenForIncomingCall();

        // Create outgoing call with first phone
        final SipCall bobCall = bobPhone.createSipCall();
        bobCall.initiateOutgoingCall(bobContact, dialRestcomm, null, body, "application", "sdp", null, null);
        assertLastOperationSuccess(bobCall);
        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        final int response = bobCall.getLastReceivedResponse().getStatusCode();
        assertTrue(response == Response.TRYING || response == Response.RINGING);

        if (response == Response.TRYING) {
            assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
            assertEquals(Response.RINGING, bobCall.getLastReceivedResponse().getStatusCode());
        }

        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        assertEquals(Response.OK, bobCall.getLastReceivedResponse().getStatusCode());
        bobCall.sendInviteOkAck();

        assertTrue(georgeCall.waitForIncomingCall(5000));
        assertTrue(georgeCall.sendIncomingCallResponse(Response.TRYING, "George-Trying", 3600));
        assertTrue(georgeCall.sendIncomingCallResponse(Response.RINGING, "George-Ringing", 3600));
        String receivedBody = new String(georgeCall.getLastReceivedRequest().getRawContent());
        assertTrue(georgeCall.sendIncomingCallResponse(Response.OK, "George-OK", 3600, receivedBody, "application", "sdp",
                null, null));
        assertTrue(georgeCall.waitForAck(5000));

        Thread.sleep(1000);
        int liveCalls = MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
        int liveIncomingCalls = MonitoringServiceTool.getInstance().getLiveIncomingCallStatistics(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
        int liveOutgoingCalls = MonitoringServiceTool.getInstance().getLiveOutgoingCallStatistics(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
        int liveCallsArraySize = MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
        assertTrue(liveCalls==2);
        assertTrue(liveIncomingCalls==1);
        assertTrue(liveOutgoingCalls==1);
        assertTrue(liveCallsArraySize==2);


        Thread.sleep(3000);
        bobCall.listenForDisconnect();

        assertTrue(georgeCall.disconnect());
        Thread.sleep(500);
        assertTrue(bobCall.waitForDisconnect(5000));
        assertTrue(bobCall.respondToDisconnect());

        Thread.sleep(10000);

        logger.info("About to check the StatusCallback Requests");
        List<LoggedRequest> requests = findAll(postRequestedFor(urlPathMatching("/status.*")));
        assertEquals(4, requests.size());
        String requestBody = requests.get(0).getBodyAsString();
        assertTrue(requestBody.contains("SequenceNumber=0"));
        assertTrue(requestBody.contains("CallStatus=initiated"));

        requestBody = requests.get(1).getBodyAsString();
        assertTrue(requestBody.contains("SequenceNumber=1"));
        assertTrue(requestBody.contains("CallStatus=ringing"));

        requestBody = requests.get(2).getBodyAsString();
        assertTrue(requestBody.contains("SequenceNumber=2"));
        assertTrue(requestBody.contains("CallStatus=answered"));

        requestBody = requests.get(3).getBodyAsString();
        assertTrue(requestBody.contains("SequenceNumber=3"));
        assertTrue(requestBody.contains("CallStatus=completed"));

        liveCalls = MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
        liveIncomingCalls = MonitoringServiceTool.getInstance().getLiveIncomingCallStatistics(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
        liveOutgoingCalls = MonitoringServiceTool.getInstance().getLiveOutgoingCallStatistics(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
        liveCallsArraySize = MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
        assertTrue(liveCalls==0);
        assertTrue(liveIncomingCalls==0);
        assertTrue(liveOutgoingCalls==0);
        assertTrue(liveCallsArraySize==0);
    }

    private String dialFork = "<Response><Dial><Client statusCallback=\"http://127.0.0.1:" + mockPort + "/status\" statusCallbackMethod=\"get\">alice</Client><Sip statusCallback=\"http://127.0.0.1:" + mockPort + "/status\" statusCallbackMethod=\"get\">sip:henrique@127.0.0.1:" + henriquePort + "</Sip><Number statusCallback=\"http://127.0.0.1:" + mockPort + "/status\" statusCallbackMethod=\"get\">+131313</Number></Dial></Response>";
    @Test
    @Category({UnstableTests.class, FeatureAltTests.class})
    public synchronized void testDialForkNoAnswerButHenriqueStatusCallbackOnAll() throws InterruptedException, ParseException, MalformedURLException {
        stubFor(get(urlPathEqualTo("/1111"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(dialFork)));

        stubFor(get(urlPathMatching("/status.*"))
                .willReturn(aResponse()
                        .withStatus(200)));

        // Register Alice
        SipURI uri = aliceSipStack.getAddressFactory().createSipURI(null, restcommContact);
        assertTrue(alicePhone.register(uri, "alice", "1234", aliceContact, 3600, 3600));

        // Prepare Alice to receive call
        final SipCall aliceCall = alicePhone.createSipCall();
        aliceCall.listenForIncomingCall();

        // Prepare George phone to receive call
        final SipCall georgeCall = georgePhone.createSipCall();
        georgeCall.listenForIncomingCall();

        // Prepare Henrique phone to receive call
        // henriquePhone.setLoopback(true);
        final SipCall henriqueCall = henriquePhone.createSipCall();
        henriqueCall.listenForIncomingCall();

        // Initiate a call using Bob
        final SipCall bobCall = bobPhone.createSipCall();

        bobCall.initiateOutgoingCall(bobContact, "sip:1111@" + restcommContact, null, body, "application", "sdp", null, null);
        assertLastOperationSuccess(bobCall);

        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));

        final int response = bobCall.getLastReceivedResponse().getStatusCode();
        assertTrue(response == Response.TRYING || response == Response.RINGING);
        if (response == Response.TRYING) {
            assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
            assertEquals(Response.RINGING, bobCall.getLastReceivedResponse().getStatusCode());
        }

        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        assertEquals(Response.OK, bobCall.getLastReceivedResponse().getStatusCode());
        bobCall.sendInviteOkAck();
        assertTrue(!(bobCall.getLastReceivedResponse().getStatusCode() >= 400));

        assertTrue(georgeCall.waitForIncomingCall(30 * 1000));
        assertTrue(georgeCall.sendIncomingCallResponse(Response.TRYING, "Trying-George", 3600));
        assertTrue(georgeCall.sendIncomingCallResponse(Response.RINGING, "Ringing-George", 3600));

        assertTrue(aliceCall.waitForIncomingCall(30 * 1000));
        assertTrue(aliceCall.sendIncomingCallResponse(Response.TRYING, "Trying-Alice", 3600));
        assertTrue(aliceCall.sendIncomingCallResponse(Response.RINGING, "Ringing-Alice", 3600));

        assertTrue(henriqueCall.waitForIncomingCall(30 * 1000));
        assertTrue(henriqueCall.sendIncomingCallResponse(Response.TRYING, "Trying-Henrique", 3600));
        assertTrue(henriqueCall.sendIncomingCallResponse(Response.RINGING, "Ringing-Henrique", 3600));


        String receivedBody = new String(henriqueCall.getLastReceivedRequest().getRawContent());
        assertTrue(henriqueCall.sendIncomingCallResponse(Response.OK, "OK-Henrique", 3600, receivedBody, "application", "sdp",
                null, null));
        assertTrue(henriqueCall.waitForAck(50 * 1000));

        georgeCall.listenForCancel();
        aliceCall.listenForCancel();

        SipTransaction georgeCancelTransaction = georgeCall.waitForCancel(30000);
        SipTransaction aliceCancelTransaction = aliceCall.waitForCancel(30000);
        assertNotNull(georgeCancelTransaction);
        assertNotNull(aliceCancelTransaction);
        georgeCall.respondToCancel(georgeCancelTransaction, 200, "OK-2-Cancel-George", 3600);
        aliceCall.respondToCancel(aliceCancelTransaction, 200, "OK-2-Cancel-Alice", 3600);

        //Wait to cancel the other branches
        Thread.sleep(2000);

        JsonObject metrics = MonitoringServiceTool.getInstance().getMetrics(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        int liveCalls = MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        int liveCallsArraySize = MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        assertTrue(liveCalls == 2);
        assertTrue(liveCallsArraySize == 2);

        henriqueCall.listenForDisconnect();

        Thread.sleep(8000);

        // hangup.

        bobCall.disconnect();

        assertTrue(henriqueCall.waitForDisconnect(30 * 1000));

        assertTrue(alicePhone.unregister(aliceContact, 3600));

        Thread.sleep(12000);

        logger.info("About to check the StatusCallback Requests");
        List<LoggedRequest> requests = findAll(getRequestedFor(urlPathMatching("/status.*")));
        assertEquals(10, requests.size());
        String requestUrl = requests.get(0).getUrl();
        assertTrue(requestUrl.contains("SequenceNumber=0"));
        assertTrue(requestUrl.contains("CallStatus=initiated"));

        requestUrl = requests.get(1).getUrl();
        assertTrue(requestUrl.contains("SequenceNumber=0"));
        assertTrue(requestUrl.contains("CallStatus=initiated"));

        requestUrl = requests.get(2).getUrl();
        assertTrue(requestUrl.contains("SequenceNumber=0"));
        assertTrue(requestUrl.contains("CallStatus=initiated"));

        requestUrl = requests.get(3).getUrl();
        assertTrue(requestUrl.contains("SequenceNumber=1"));
        assertTrue(requestUrl.contains("CallStatus=ringing"));

        requestUrl = requests.get(4).getUrl();
        assertTrue(requestUrl.contains("SequenceNumber=1"));
        assertTrue(requestUrl.contains("CallStatus=ringing"));

        requestUrl = requests.get(5).getUrl();
        assertTrue(requestUrl.contains("SequenceNumber=1"));
        assertTrue(requestUrl.contains("CallStatus=ringing"));

        requestUrl = requests.get(6).getUrl();
        assertTrue(requestUrl.contains("SequenceNumber=2"));
        assertTrue(requestUrl.contains("CallStatus=answered"));

        requestUrl = requests.get(7).getUrl();
        assertTrue(requestUrl.contains("SequenceNumber=3"));
        assertTrue(requestUrl.contains("CallStatus=completed"));

        requestUrl = requests.get(8).getUrl();
        assertTrue(requestUrl.contains("SequenceNumber=3"));
        assertTrue(requestUrl.contains("CallStatus=completed"));

        requestUrl = requests.get(9).getUrl();
        assertTrue(requestUrl.contains("SequenceNumber=3"));
        assertTrue(requestUrl.contains("CallStatus=completed"));

        assertTrue(MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken) == 0);
        assertTrue(MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(), adminAccountSid, adminAuthToken) == 0);
    }

    private String dialForkStatusCallbackWithPost = "<Response><Dial><Client statusCallback=\"http://127.0.0.1:" + mockPort + "/status\">alice</Client><Sip statusCallback=\"http://127.0.0.1:" + mockPort + "/status\">sip:henrique@127.0.0.1:" + henriquePort + "</Sip><Number statusCallback=\"http://127.0.0.1:" + mockPort + "/status\">+131313</Number></Dial></Response>";
    @Test
    @Category({FeatureAltTests.class,UnstableTests.class})
    public synchronized void testDialForkNoAnswerButHenriqueStatusCallbackOnAllPost() throws InterruptedException, ParseException, MalformedURLException {
        stubFor(get(urlPathEqualTo("/1111"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(dialForkStatusCallbackWithPost)));

        stubFor(post(urlPathMatching("/status.*"))
                .willReturn(aResponse()
                        .withStatus(200)));

        // Register Alice
        SipURI uri = aliceSipStack.getAddressFactory().createSipURI(null, restcommContact);
        assertTrue(alicePhone.register(uri, "alice", "1234", aliceContact, 3600, 3600));

        // Prepare Alice to receive call
        final SipCall aliceCall = alicePhone.createSipCall();
        aliceCall.listenForIncomingCall();

        // Prepare George phone to receive call
        final SipCall georgeCall = georgePhone.createSipCall();
        georgeCall.listenForIncomingCall();

        // Prepare Henrique phone to receive call
        // henriquePhone.setLoopback(true);
        final SipCall henriqueCall = henriquePhone.createSipCall();
        henriqueCall.listenForIncomingCall();

        // Initiate a call using Bob
        final SipCall bobCall = bobPhone.createSipCall();

        bobCall.initiateOutgoingCall(bobContact, "sip:1111@" + restcommContact, null, body, "application", "sdp", null, null);
        assertLastOperationSuccess(bobCall);

        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));

        final int response = bobCall.getLastReceivedResponse().getStatusCode();
        assertTrue(response == Response.TRYING || response == Response.RINGING);
        if (response == Response.TRYING) {
            assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
            assertEquals(Response.RINGING, bobCall.getLastReceivedResponse().getStatusCode());
        }

        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        assertEquals(Response.OK, bobCall.getLastReceivedResponse().getStatusCode());
        bobCall.sendInviteOkAck();
        assertTrue(!(bobCall.getLastReceivedResponse().getStatusCode() >= 400));

        assertTrue(georgeCall.waitForIncomingCall(30 * 1000));
        assertTrue(georgeCall.sendIncomingCallResponse(Response.TRYING, "Trying-George", 3600));
        assertTrue(georgeCall.sendIncomingCallResponse(Response.RINGING, "Ringing-George", 3600));

        assertTrue(aliceCall.waitForIncomingCall(30 * 1000));
        assertTrue(aliceCall.sendIncomingCallResponse(Response.TRYING, "Trying-Alice", 3600));
        assertTrue(aliceCall.sendIncomingCallResponse(Response.RINGING, "Ringing-Alice", 3600));

        assertTrue(henriqueCall.waitForIncomingCall(30 * 1000));
        assertTrue(henriqueCall.sendIncomingCallResponse(Response.TRYING, "Trying-Henrique", 3600));
        assertTrue(henriqueCall.sendIncomingCallResponse(Response.RINGING, "Ringing-Henrique", 3600));


        String receivedBody = new String(henriqueCall.getLastReceivedRequest().getRawContent());
        assertTrue(henriqueCall.sendIncomingCallResponse(Response.OK, "OK-Henrique", 3600, receivedBody, "application", "sdp",
                null, null));
        assertTrue(henriqueCall.waitForAck(50 * 1000));

        georgeCall.listenForCancel();
        aliceCall.listenForCancel();

        SipTransaction georgeCancelTransaction = georgeCall.waitForCancel(30000);
        SipTransaction aliceCancelTransaction = aliceCall.waitForCancel(30000);
        assertNotNull(georgeCancelTransaction);
        assertNotNull(aliceCancelTransaction);
        georgeCall.respondToCancel(georgeCancelTransaction, 200, "OK-2-Cancel-George", 3600);
        aliceCall.respondToCancel(aliceCancelTransaction, 200, "OK-2-Cancel-Alice", 3600);

        //Wait to cancel the other branches
        Thread.sleep(2000);

        JsonObject metrics = MonitoringServiceTool.getInstance().getMetrics(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        int liveCalls = MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        int liveCallsArraySize = MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        assertTrue(liveCalls == 2);
        assertTrue(liveCallsArraySize == 2);

        henriqueCall.listenForDisconnect();

        Thread.sleep(8000);

        // hangup.

        bobCall.disconnect();

        assertTrue(henriqueCall.waitForDisconnect(30 * 1000));

        assertTrue(alicePhone.unregister(aliceContact, 3600));

        Thread.sleep(12000);

        logger.info("About to check the StatusCallback Requests");
        List<LoggedRequest> requests = findAll(postRequestedFor(urlPathMatching("/status.*")));
        assertEquals(10, requests.size());
        String requestBody = requests.get(0).getBodyAsString();
        assertTrue(requestBody.contains("SequenceNumber=0"));
        assertTrue(requestBody.contains("CallStatus=initiated"));

        requestBody = requests.get(1).getBodyAsString();
        assertTrue(requestBody.contains("SequenceNumber=0"));
        assertTrue(requestBody.contains("CallStatus=initiated"));

        requestBody = requests.get(2).getBodyAsString();
        assertTrue(requestBody.contains("SequenceNumber=0"));
        assertTrue(requestBody.contains("CallStatus=initiated"));

        requestBody = requests.get(3).getBodyAsString();
        assertTrue(requestBody.contains("SequenceNumber=1"));
        assertTrue(requestBody.contains("CallStatus=ringing"));

        requestBody = requests.get(4).getBodyAsString();
        assertTrue(requestBody.contains("SequenceNumber=1"));
        assertTrue(requestBody.contains("CallStatus=ringing"));

        requestBody = requests.get(5).getBodyAsString();
        assertTrue(requestBody.contains("SequenceNumber=1"));
        assertTrue(requestBody.contains("CallStatus=ringing"));

        requestBody = requests.get(6).getBodyAsString();
        assertTrue(requestBody.contains("SequenceNumber=2"));
        assertTrue(requestBody.contains("CallStatus=answered"));

        requestBody = requests.get(7).getBodyAsString();
        assertTrue(requestBody.contains("SequenceNumber=3"));
        assertTrue(requestBody.contains("CallStatus=completed"));

        requestBody = requests.get(8).getBodyAsString();
        assertTrue(requestBody.contains("SequenceNumber=3"));
        assertTrue(requestBody.contains("CallStatus=completed"));

        requestBody = requests.get(9).getBodyAsString();
        assertTrue(requestBody.contains("SequenceNumber=3"));
        assertTrue(requestBody.contains("CallStatus=completed"));

        assertTrue(MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken) == 0);
        assertTrue(MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(), adminAccountSid, adminAuthToken) == 0);
    }

    private String dialForkWithTimeoutStatusCallbackWithPost = "<Response><Dial timeout=\"60\"><Client statusCallback=\"http://127.0.0.1:" + mockPort + "/status\">alice</Client><Sip statusCallback=\"http://127.0.0.1:" + mockPort + "/status\">sip:henrique@127.0.0.1:" + henriquePort + "</Sip><Number statusCallback=\"http://127.0.0.1:" + mockPort + "/status\">+131313</Number></Dial></Response>";
    @Test
    @Category({UnstableTests.class, FeatureAltTests.class})
    public synchronized void testDialForkNoAnswerButHenriqueStatusCallbackOnAllPostWithTimeout() throws InterruptedException, ParseException, MalformedURLException {
        stubFor(get(urlPathEqualTo("/1111"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(dialForkWithTimeoutStatusCallbackWithPost)));

        stubFor(post(urlPathMatching("/status.*"))
                .willReturn(aResponse()
                        .withStatus(200)));

        // Register Alice
        SipURI uri = aliceSipStack.getAddressFactory().createSipURI(null, restcommContact);
        assertTrue(alicePhone.register(uri, "alice", "1234", aliceContact, 3600, 3600));

        // Prepare Alice to receive call
        final SipCall aliceCall = alicePhone.createSipCall();
        aliceCall.listenForIncomingCall();

        // Prepare George phone to receive call
        final SipCall georgeCall = georgePhone.createSipCall();
        georgeCall.listenForIncomingCall();

        // Prepare Henrique phone to receive call
        // henriquePhone.setLoopback(true);
        final SipCall henriqueCall = henriquePhone.createSipCall();
        henriqueCall.listenForIncomingCall();

        // Initiate a call using Bob
        final SipCall bobCall = bobPhone.createSipCall();

        bobCall.initiateOutgoingCall(bobContact, "sip:1111@" + restcommContact, null, body, "application", "sdp", null, null);
        assertLastOperationSuccess(bobCall);

        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));

        final int response = bobCall.getLastReceivedResponse().getStatusCode();
        assertTrue(response == Response.TRYING || response == Response.RINGING);
        if (response == Response.TRYING) {
            assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
            assertEquals(Response.RINGING, bobCall.getLastReceivedResponse().getStatusCode());
        }

        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        assertEquals(Response.OK, bobCall.getLastReceivedResponse().getStatusCode());
        bobCall.sendInviteOkAck();
        assertTrue(!(bobCall.getLastReceivedResponse().getStatusCode() >= 400));

        assertTrue(georgeCall.waitForIncomingCall(30 * 1000));
        assertTrue(georgeCall.sendIncomingCallResponse(Response.TRYING, "Trying-George", 3600));
        assertTrue(georgeCall.sendIncomingCallResponse(Response.RINGING, "Ringing-George", 3600));

        assertTrue(aliceCall.waitForIncomingCall(30 * 1000));
        assertTrue(aliceCall.sendIncomingCallResponse(Response.TRYING, "Trying-Alice", 3600));
        assertTrue(aliceCall.sendIncomingCallResponse(Response.RINGING, "Ringing-Alice", 3600));

        assertTrue(henriqueCall.waitForIncomingCall(30 * 1000));
        assertTrue(henriqueCall.sendIncomingCallResponse(Response.TRYING, "Trying-Henrique", 3600));
        assertTrue(henriqueCall.sendIncomingCallResponse(Response.RINGING, "Ringing-Henrique", 3600));


        String receivedBody = new String(henriqueCall.getLastReceivedRequest().getRawContent());
        assertTrue(henriqueCall.sendIncomingCallResponse(Response.OK, "OK-Henrique", 3600, receivedBody, "application", "sdp",
                null, null));
        assertTrue(henriqueCall.waitForAck(50 * 1000));

        georgeCall.listenForCancel();
        aliceCall.listenForCancel();

        SipTransaction georgeCancelTransaction = georgeCall.waitForCancel(30000);
        SipTransaction aliceCancelTransaction = aliceCall.waitForCancel(30000);
        assertNotNull(georgeCancelTransaction);
        assertNotNull(aliceCancelTransaction);
        georgeCall.respondToCancel(georgeCancelTransaction, 200, "OK-2-Cancel-George", 3600);
        aliceCall.respondToCancel(aliceCancelTransaction, 200, "OK-2-Cancel-Alice", 3600);

        //Wait to cancel the other branches
        Thread.sleep(2000);

        JsonObject metrics = MonitoringServiceTool.getInstance().getMetrics(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        int liveCalls = MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        int liveCallsArraySize = MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        assertTrue(liveCalls == 2);
        assertTrue(liveCallsArraySize == 2);

        henriqueCall.listenForDisconnect();

        Thread.sleep(8000);

        // hangup.

        bobCall.disconnect();

        assertTrue(henriqueCall.waitForDisconnect(30 * 1000));

        assertTrue(alicePhone.unregister(aliceContact, 3600));

        Thread.sleep(10000);

        logger.info("About to check the StatusCallback Requests");
        List<LoggedRequest> requests = findAll(postRequestedFor(urlPathMatching("/status.*")));
        assertEquals(10, requests.size());
        String requestBody = requests.get(0).getBodyAsString();
        assertTrue(requestBody.contains("SequenceNumber=0"));
        assertTrue(requestBody.contains("CallStatus=initiated"));

        requestBody = requests.get(1).getBodyAsString();
        assertTrue(requestBody.contains("SequenceNumber=0"));
        assertTrue(requestBody.contains("CallStatus=initiated"));

        requestBody = requests.get(2).getBodyAsString();
        assertTrue(requestBody.contains("SequenceNumber=0"));
        assertTrue(requestBody.contains("CallStatus=initiated"));

        requestBody = requests.get(3).getBodyAsString();
        assertTrue(requestBody.contains("SequenceNumber=1"));
        assertTrue(requestBody.contains("CallStatus=ringing"));

        requestBody = requests.get(4).getBodyAsString();
        assertTrue(requestBody.contains("SequenceNumber=1"));
        assertTrue(requestBody.contains("CallStatus=ringing"));

        requestBody = requests.get(5).getBodyAsString();
        assertTrue(requestBody.contains("SequenceNumber=1"));
        assertTrue(requestBody.contains("CallStatus=ringing"));

        requestBody = requests.get(6).getBodyAsString();
        assertTrue(requestBody.contains("SequenceNumber=2"));
        assertTrue(requestBody.contains("CallStatus=answered"));

        requestBody = requests.get(7).getBodyAsString();
        assertTrue(requestBody.contains("SequenceNumber=3"));
        assertTrue(requestBody.contains("CallStatus=completed"));

        requestBody = requests.get(8).getBodyAsString();
        assertTrue(requestBody.contains("SequenceNumber=3"));
        assertTrue(requestBody.contains("CallStatus=completed"));

        requestBody = requests.get(9).getBodyAsString();
        assertTrue(requestBody.contains("SequenceNumber=3"));
        assertTrue(requestBody.contains("CallStatus=completed"));

        assertTrue(MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken) == 0);
        assertTrue(MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(), adminAccountSid, adminAuthToken) == 0);
    }

    private String dialForkWithActionUrl = "<Response><Dial timeLimit=\"1000\" timeout=\"2\" action=\"http://127.0.0.1:" + mockPort + "/action\">" +
            "<Number statusCallback=\"http://127.0.0.1:" + mockPort + "/status\" statusCallbackMethod=\"get\">+131313</Number>" +
            "<Sip statusCallback=\"http://127.0.0.1:" + mockPort + "/status\" statusCallbackMethod=\"get\">sip:henrique@127.0.0.1:" + henriquePort + "</Sip>" +
            "<Client statusCallback=\"http://127.0.0.1:" + mockPort + "/status\" statusCallbackMethod=\"get\">alice</Client></Dial></Response>";
    private String rcmlToReturn = "<Response><Dial timeout=\"50\"><Client statusCallback=\"http://127.0.0.1:" + mockPort + "/status\" statusCallbackMethod=\"get\">alice</Client>   </Dial></Response>";
    //Non regression test for https://telestax.atlassian.net/browse/RESTCOMM-585
    @Test //TODO Fails when the whole test class runs but Passes when run individually
    @Category({UnstableTests.class, FeatureAltTests.class})
    public synchronized void testDialForkNoAnswerExecuteRCML_ReturnedFromActionURLWithStatusCallbacks_BobDisconnects() throws InterruptedException, ParseException, MalformedURLException {

        stubFor(get(urlPathEqualTo("/1111"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(dialForkWithActionUrl)));

        stubFor(post(urlEqualTo("/action"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(rcmlToReturn)));

        stubFor(get(urlPathMatching("/status.*"))
                .willReturn(aResponse()
                        .withStatus(200)));

        // Register Alice
        SipURI uri = aliceSipStack.getAddressFactory().createSipURI(null, restcommContact);
        assertTrue(alicePhone.register(uri, "alice", "1234", aliceContact, 3600, 3600));

        // Prepare Alice to receive call
        final SipCall aliceCall = alicePhone.createSipCall();
        aliceCall.listenForIncomingCall();

        // Prepare George phone to receive call
        final SipCall georgeCall = georgePhone.createSipCall();
        georgeCall.listenForIncomingCall();

        // Prepare Henrique phone to receive call
        // henriquePhone.setLoopback(true);
        final SipCall henriqueCall = henriquePhone.createSipCall();
        henriqueCall.listenForIncomingCall();


        // Initiate a call using Bob
        final SipCall bobCall = bobPhone.createSipCall();

        bobCall.initiateOutgoingCall(bobContact, "sip:1111@" + restcommContact, null, body, "application", "sdp", null, null);
        assertLastOperationSuccess(bobCall);

        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));

        final int response = bobCall.getLastReceivedResponse().getStatusCode();
        assertTrue(response == Response.TRYING || response == Response.RINGING);
        if (response == Response.TRYING) {
            assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
            assertEquals(Response.RINGING, bobCall.getLastReceivedResponse().getStatusCode());
        }

        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        assertEquals(Response.OK, bobCall.getLastReceivedResponse().getStatusCode());
        bobCall.sendInviteOkAck();
        assertTrue(!(bobCall.getLastReceivedResponse().getStatusCode() >= 400));

        assertTrue(georgeCall.waitForIncomingCall(30 * 1000));
        assertTrue(georgeCall.sendIncomingCallResponse(100, "Trying-George", 600));
        assertTrue(georgeCall.sendIncomingCallResponse(180, "Ringing-George", 600));
        assertTrue(aliceCall.waitForIncomingCall(30 * 1000));
        assertTrue(aliceCall.sendIncomingCallResponse(100, "Trying-Alice", 600));
        assertTrue(aliceCall.sendIncomingCallResponse(180, "Ringing-Alice", 600));
        assertTrue(henriqueCall.waitForIncomingCall(30 * 1000));
        assertTrue(henriqueCall.sendIncomingCallResponse(100, "Trying-Henrique", 600));
        assertTrue(henriqueCall.sendIncomingCallResponse(Response.RINGING, "Ringing-Henrique", 3600));

        //No one will answer the call and RCML will move to the next verb to call Fotini

        assertTrue(georgeCall.listenForCancel());
        assertTrue(aliceCall.listenForCancel());
        assertTrue(henriqueCall.listenForCancel());

        Thread.sleep(1000);

        SipTransaction georgeCancelTransaction = georgeCall.waitForCancel(50 * 1000);
        SipTransaction henriqueCancelTransaction = henriqueCall.waitForCancel(50 * 1000);
        SipTransaction aliceCancelTransaction = aliceCall.waitForCancel(50 * 1000);
        assertNotNull(georgeCancelTransaction);
        assertNotNull(aliceCancelTransaction);
        assertNotNull(henriqueCancelTransaction);
        georgeCall.respondToCancel(georgeCancelTransaction, 200, "OK - George", 600);
        aliceCall.respondToCancel(aliceCancelTransaction, 200, "OK - Alice", 600);
        henriqueCall.respondToCancel(henriqueCancelTransaction, 200, "OK - Henrique", 600);

//        int liveCalls = MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
//        int liveCallsArraySize = MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
//        //There will be the initial call from Bob and the new call to Fotini
//        logger.info("&&&& LiveCalls: "+liveCalls);
//        logger.info("&&&& LiveCallsArraySize: "+liveCallsArraySize);
//        assertTrue(liveCalls == 2);
//        assertTrue(liveCallsArraySize == 2);

        Thread.sleep(1000);

        //Now Fotini should receive a call
        assertTrue(aliceCall.waitForIncomingCall(30 * 1000));
        assertTrue(aliceCall.sendIncomingCallResponse(100, "Trying-Alice", 600));
        assertTrue(aliceCall.sendIncomingCallResponse(180, "Ringing-Alice", 600));
        String receivedBody = new String(aliceCall.getLastReceivedRequest().getRawContent());
        assertTrue(aliceCall.sendIncomingCallResponse(Response.OK, "OK-Alice", 3600, receivedBody, "application", "sdp", null, null));
        assertTrue(aliceCall.waitForAck(5000));

        assertTrue(MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken) == 2);
        assertTrue(MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(), adminAccountSid, adminAuthToken) == 2);

        Thread.sleep(5000);

        // hangup.

        aliceCall.listenForDisconnect();

        assertTrue(bobCall.disconnect());

        assertTrue(aliceCall.waitForDisconnect(50 * 1000));
        assertTrue(aliceCall.respondToDisconnect());

        Thread.sleep(10000);

        logger.info("About to check the Requests");
        List<LoggedRequest> requests = findAll(getRequestedFor(urlPathMatching("/1111")));
        assertTrue(requests.size() == 1);
        //        requests.get(0).g;
        String requestBody = new URL(requests.get(0).getAbsoluteUrl()).getQuery();// .getQuery();// .getBodyAsString();
        List<String> params = Arrays.asList(requestBody.split("&"));
        String callSid = "";
        for (String param : params) {
            if (param.contains("CallSid")) {
                callSid = param.split("=")[1];
            }
        }
        JsonObject cdr = RestcommCallsTool.getInstance().getCall(deploymentUrl.toString(), adminAccountSid, adminAuthToken, callSid);
        JsonObject jsonObj = cdr.getAsJsonObject();
        assertTrue(jsonObj.get("status").getAsString().equalsIgnoreCase("completed"));
        assertTrue(MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken) == 0);
        assertTrue(MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(), adminAccountSid, adminAuthToken) == 0);

        logger.info("About to check the StatusCallback Requests");
        requests = findAll(getRequestedFor(urlPathMatching("/status.*")));
        assertEquals(13, requests.size());
    }

    //Non regression test for https://telestax.atlassian.net/browse/RESTCOMM-585
    @Test //TODO Fails when the whole test class runs but Passes when run individually
    @Category({UnstableTests.class, FeatureAltTests.class})
    public synchronized void testDialForkNoAnswerExecuteRCML_ReturnedFromActionURLWithStatusCallbacks_AliceDisconnects() throws InterruptedException, ParseException, MalformedURLException {

        stubFor(get(urlPathEqualTo("/1111"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(dialForkWithActionUrl)));

        stubFor(post(urlEqualTo("/action"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(rcmlToReturn)));

        stubFor(get(urlPathMatching("/status.*"))
                .willReturn(aResponse()
                        .withStatus(200)));

        // Register Alice
        SipURI uri = aliceSipStack.getAddressFactory().createSipURI(null, restcommContact);
        assertTrue(alicePhone.register(uri, "alice", "1234", aliceContact, 3600, 3600));

        // Prepare Alice to receive call
        final SipCall aliceCall = alicePhone.createSipCall();
        aliceCall.listenForIncomingCall();

        // Prepare George phone to receive call
        final SipCall georgeCall = georgePhone.createSipCall();
        georgeCall.listenForIncomingCall();

        // Prepare Henrique phone to receive call
        // henriquePhone.setLoopback(true);
        final SipCall henriqueCall = henriquePhone.createSipCall();
        henriqueCall.listenForIncomingCall();


        // Initiate a call using Bob
        final SipCall bobCall = bobPhone.createSipCall();

        bobCall.initiateOutgoingCall(bobContact, "sip:1111@" + restcommContact, null, body, "application", "sdp", null, null);
        assertLastOperationSuccess(bobCall);

        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));

        final int response = bobCall.getLastReceivedResponse().getStatusCode();
        assertTrue(response == Response.TRYING || response == Response.RINGING);
        if (response == Response.TRYING) {
            assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
            assertEquals(Response.RINGING, bobCall.getLastReceivedResponse().getStatusCode());
        }

        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        assertEquals(Response.OK, bobCall.getLastReceivedResponse().getStatusCode());
        bobCall.sendInviteOkAck();
        assertTrue(!(bobCall.getLastReceivedResponse().getStatusCode() >= 400));

        assertTrue(georgeCall.waitForIncomingCall(30 * 1000));
        assertTrue(georgeCall.sendIncomingCallResponse(100, "Trying-George", 600));
        assertTrue(georgeCall.sendIncomingCallResponse(180, "Ringing-George", 600));
        assertTrue(aliceCall.waitForIncomingCall(30 * 1000));
        assertTrue(aliceCall.sendIncomingCallResponse(100, "Trying-Alice", 600));
        assertTrue(aliceCall.sendIncomingCallResponse(180, "Ringing-Alice", 600));
        assertTrue(henriqueCall.waitForIncomingCall(30 * 1000));
        assertTrue(henriqueCall.sendIncomingCallResponse(100, "Trying-Henrique", 600));
        assertTrue(henriqueCall.sendIncomingCallResponse(Response.RINGING, "Ringing-Henrique", 3600));

        //No one will answer the call and RCML will move to the next verb to call Fotini

        assertTrue(georgeCall.listenForCancel());
        assertTrue(aliceCall.listenForCancel());
        assertTrue(henriqueCall.listenForCancel());

        Thread.sleep(1000);

        SipTransaction georgeCancelTransaction = georgeCall.waitForCancel(50 * 1000);
        SipTransaction henriqueCancelTransaction = henriqueCall.waitForCancel(50 * 1000);
        SipTransaction aliceCancelTransaction = aliceCall.waitForCancel(50 * 1000);
        assertNotNull(georgeCancelTransaction);
        assertNotNull(aliceCancelTransaction);
        assertNotNull(henriqueCancelTransaction);
        georgeCall.respondToCancel(georgeCancelTransaction, 200, "OK - George", 600);
        aliceCall.respondToCancel(aliceCancelTransaction, 200, "OK - Alice", 600);
        henriqueCall.respondToCancel(henriqueCancelTransaction, 200, "OK - Henrique", 600);

//        int liveCalls = MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
//        int liveCallsArraySize = MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
//        //There will be the initial call from Bob and the new call to Fotini
//        logger.info("&&&& LiveCalls: "+liveCalls);
//        logger.info("&&&& LiveCallsArraySize: "+liveCallsArraySize);
//        assertTrue(liveCalls == 2);
//        assertTrue(liveCallsArraySize == 2);


        //Now Fotini should receive a call
        assertTrue(aliceCall.waitForIncomingCall(30 * 1000));
        assertTrue(aliceCall.sendIncomingCallResponse(100, "Trying-Alice", 600));
        assertTrue(aliceCall.sendIncomingCallResponse(180, "Ringing-Alice", 600));
        String receivedBody = new String(aliceCall.getLastReceivedRequest().getRawContent());
        assertTrue(aliceCall.sendIncomingCallResponse(Response.OK, "OK-Alice", 3600, receivedBody, "application", "sdp", null, null));
        assertTrue(aliceCall.waitForAck(5000));

        bobCall.listenForDisconnect();

        assertTrue(MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken) == 2);
        assertTrue(MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(), adminAccountSid, adminAuthToken) == 2);

        Thread.sleep(2000);

        // hangup.

        assertTrue(aliceCall.disconnect());

        assertTrue(bobCall.waitForDisconnect(50 * 1000));
        assertTrue(bobCall.respondToDisconnect());

        Thread.sleep(10000);

        logger.info("About to check the Requests");
        List<LoggedRequest> requests = findAll(getRequestedFor(urlPathMatching("/1111")));
        assertTrue(requests.size() == 1);
        //        requests.get(0).g;
        String requestBody = new URL(requests.get(0).getAbsoluteUrl()).getQuery();// .getQuery();// .getBodyAsString();
        List<String> params = Arrays.asList(requestBody.split("&"));
        String callSid = "";
        for (String param : params) {
            if (param.contains("CallSid")) {
                callSid = param.split("=")[1];
            }
        }
        JsonObject cdr = RestcommCallsTool.getInstance().getCall(deploymentUrl.toString(), adminAccountSid, adminAuthToken, callSid);
        JsonObject jsonObj = cdr.getAsJsonObject();
        assertTrue(jsonObj.get("status").getAsString().equalsIgnoreCase("completed"));
        assertTrue(MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken) == 0);
        assertTrue(MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(), adminAccountSid, adminAuthToken) == 0);

        logger.info("About to check the StatusCallback Requests");
        requests = findAll(getRequestedFor(urlPathMatching("/status.*")));
        Map<String, String> requestMap = getRequestMap(requests);
        assertEquals(13, requests.size());
    }

    private Map<String, String> getRequestMap(final List<LoggedRequest> requestList) {
        Map<String, String> resultMap = new HashMap<String, String>();
        for(LoggedRequest request: requestList) {
            String[] tokens = request.getUrl().split("&");
            String to = null;
            String callStatus = null;
            for (String token: tokens) {
                if (token.contains("To")) {
                    to = token;
                }
                if (token.contains("SequenceNumber")) {
                    to = to+token;
                }
                if (token.contains("CallStatus")) {
                    callStatus = token;
                }
            }
            resultMap.put(to,callStatus);
        }
        return resultMap;
    }

    @Deployment(name = "DialAction", managed = true, testable = false)
    public static WebArchive createWebArchiveNoGw() {
        logger.info("Packaging Test App");
        reconfigurePorts();

        Map<String,String> replacements = new HashMap();
        //replace mediaport 2727
        replacements.put("2727", String.valueOf(mediaPort));
        replacements.put("8080", String.valueOf(restcommHTTPPort));
        replacements.put("8090", String.valueOf(mockPort));
        replacements.put("5080", String.valueOf(restcommPort));
        replacements.put("5070", String.valueOf(georgePort));
        replacements.put("5090", String.valueOf(bobPort));
        replacements.put("5091", String.valueOf(alicePort));
        replacements.put("5092", String.valueOf(henriquePort));

        List<String> resources = new ArrayList();
        return WebArchiveUtil.createWebArchiveNoGw("restcomm.xml",
                "restcomm.script_dialStatusCallbackTest",resources, replacements);
    }

}
