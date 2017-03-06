package org.restcomm.connect.testsuite.telephony;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import gov.nist.javax.sip.header.ContentType;
import org.apache.log4j.Logger;
import org.cafesip.sipunit.SipCall;
import org.cafesip.sipunit.SipPhone;
import org.cafesip.sipunit.SipRequest;
import org.cafesip.sipunit.SipStack;
import org.cafesip.sipunit.SipTransaction;
import org.jboss.arquillian.container.mss.extension.SipStackTool;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.resolver.api.maven.archive.ShrinkWrapMaven;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.restcomm.connect.commons.Version;
import org.restcomm.connect.testsuite.http.RestcommCallsTool;

import javax.sip.Dialog;
import javax.sip.SipException;
import javax.sip.address.SipURI;
import javax.sip.header.FromHeader;
import javax.sip.message.Request;
import javax.sip.message.Response;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.findAll;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.cafesip.sipunit.SipAssert.assertLastOperationSuccess;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Test for Dial verb. Will test Dial Conference, Dial URI, Dial Client, Dial Number and Dial Fork
 *
 * @author <a href="mailto:gvagenas@gmail.com">gvagenas</a>
 * @author jean.deruelle@telestax.com
 */
@RunWith(Arquillian.class)
public class TestDialVerbPartTwo {
    private final static Logger logger = Logger.getLogger(TestDialVerbPartTwo.class.getName());

    private static final String version = Version.getVersion();
    private static final byte[] bytes = new byte[]{118, 61, 48, 13, 10, 111, 61, 117, 115, 101, 114, 49, 32, 53, 51, 54, 53,
            53, 55, 54, 53, 32, 50, 51, 53, 51, 54, 56, 55, 54, 51, 55, 32, 73, 78, 32, 73, 80, 52, 32, 49, 50, 55, 46, 48, 46,
            48, 46, 49, 13, 10, 115, 61, 45, 13, 10, 99, 61, 73, 78, 32, 73, 80, 52, 32, 49, 50, 55, 46, 48, 46, 48, 46, 49,
            13, 10, 116, 61, 48, 32, 48, 13, 10, 109, 61, 97, 117, 100, 105, 111, 32, 54, 48, 48, 48, 32, 82, 84, 80, 47, 65,
            86, 80, 32, 48, 13, 10, 97, 61, 114, 116, 112, 109, 97, 112, 58, 48, 32, 80, 67, 77, 85, 47, 56, 48, 48, 48, 13, 10};
    private static final String body = new String(bytes);

    @ArquillianResource
    URL deploymentUrl;

    private String adminAccountSid = "ACae6e420f425248d6a26948c17a9e2acf";
    private String adminAuthToken = "77f8c12cc7b8f8423e5c38b035249166";

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(8090); // No-args constructor defaults to port 8080

    private static SipStackTool tool1;
    private static SipStackTool tool2;
    private static SipStackTool tool3;

    // Bob is a simple SIP Client. Will not register with Restcomm
    private SipStack bobSipStack;
    private SipPhone bobPhone;
    private String bobContact = "sip:bob@127.0.0.1:5090";

    // Alice is a Restcomm Client with VoiceURL. This Restcomm Client can register with Restcomm and whatever will dial the RCML
    // of the VoiceURL will be executed.
    private SipStack aliceSipStack;
    private SipPhone alicePhone;
    private String aliceContact = "sip:alice@127.0.0.1:5091";

    // George is a simple SIP Client. Will not register with Restcomm
    private SipStack georgeSipStack;
    private SipPhone georgePhone;
    private String georgeContact = "sip:+131313@127.0.0.1:5070";

    private String dialRestcomm = "sip:1111@127.0.0.1:5080";
    private String dialRestcommWithStatusCallback = "sip:7777@127.0.0.1:5080";

    @BeforeClass
    public static void beforeClass() throws Exception {
        tool1 = new SipStackTool("DialTest2Tool1");
        tool2 = new SipStackTool("DialTest2Tool2");
        tool3 = new SipStackTool("DialTest2Tool3");
    }

    @Before
    public void before() throws Exception {
        bobSipStack = tool1.initializeSipStack(SipStack.PROTOCOL_UDP, "127.0.0.1", "5090", "127.0.0.1:5080");
        bobPhone = bobSipStack.createSipPhone("127.0.0.1", SipStack.PROTOCOL_UDP, 5080, bobContact);

        aliceSipStack = tool2.initializeSipStack(SipStack.PROTOCOL_UDP, "127.0.0.1", "5091", "127.0.0.1:5080");
        alicePhone = aliceSipStack.createSipPhone("127.0.0.1", SipStack.PROTOCOL_UDP, 5080, aliceContact);

        georgeSipStack = tool3.initializeSipStack(SipStack.PROTOCOL_UDP, "127.0.0.1", "5070", "127.0.0.1:5080");
        georgePhone = georgeSipStack.createSipPhone("127.0.0.1", SipStack.PROTOCOL_UDP, 5080, georgeContact);
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

        if (georgePhone != null) {
            georgePhone.dispose();
        }
        if (georgeSipStack != null) {
            georgeSipStack.dispose();
        }
        Thread.sleep(3000);
        wireMockRule.resetRequests();
        Thread.sleep(2000);
    }


    private String dialClientRcml = "<Response><Dial timeLimit=\"10\" timeout=\"10\"><Client>alice</Client></Dial></Response>";
    //Test for issue RESTCOMM-617
    @Test
    public synchronized void testDialClientAliceToBigDID() throws InterruptedException, ParseException {
        stubFor(get(urlPathEqualTo("/1111"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(dialClientRcml)));

        // Phone2 register as alice
        SipURI uri = aliceSipStack.getAddressFactory().createSipURI(null, "127.0.0.1:5080");
        assertTrue(alicePhone.register(uri, "alice", "1234", aliceContact, 3600, 3600));

        // Prepare second phone to receive call
        SipCall aliceCall = alicePhone.createSipCall();
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
        assertTrue(!(bobCall.getLastReceivedResponse().getStatusCode() >= 400));

        assertTrue(aliceCall.waitForIncomingCall(30 * 1000));
        assertTrue(aliceCall.sendIncomingCallResponse(Response.RINGING, "Ringing-Alice", 3600));
        String receivedBody = new String(aliceCall.getLastReceivedRequest().getRawContent());
        assertTrue(aliceCall.sendIncomingCallResponse(Response.OK, "OK-Alice", 3600, receivedBody, "application", "sdp", null,
                null));
        assertTrue(aliceCall.waitForAck(50 * 1000));

        Thread.sleep(3000);

        // hangup.
        bobCall.disconnect();

        aliceCall.listenForDisconnect();
        assertTrue(aliceCall.waitForDisconnect(30 * 1000));
        assertTrue(aliceCall.respondToDisconnect());
    }

    private String dialClientWithRecordingRcml = "<Response><Dial timeLimit=\"10\" timeout=\"10\" record=\"true\" action=\"http://127.0.0.1:8090/action\" method=\"GET\"><Client>alice</Client></Dial></Response>";
    private String sendSmsActionRcml = "<Response>\n" +
            "\t\t\t<Sms to=\"bob\" from=\"+12223334499\">Hello World!</Sms>\n" +
            "</Response>";
    @Test
    public synchronized void testDialClientAliceWithRecord() throws InterruptedException, ParseException {
        stubFor(get(urlPathEqualTo("/1111"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(dialClientWithRecordingRcml)));

        stubFor(get(urlPathEqualTo("/action"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(sendSmsActionRcml)));

        // Phone2 register as alice
        SipURI uri = aliceSipStack.getAddressFactory().createSipURI(null, "127.0.0.1:5080");
        assertTrue(alicePhone.register(uri, "alice", "1234", aliceContact, 3600, 3600));

        // Prepare second phone to receive call
        SipCall aliceCall = alicePhone.createSipCall();
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
        assertTrue(!(bobCall.getLastReceivedResponse().getStatusCode() >= 400));

        assertTrue(aliceCall.waitForIncomingCall(30 * 1000));
        assertTrue(aliceCall.sendIncomingCallResponse(Response.RINGING, "Ringing-Alice", 3600));
        String receivedBody = new String(aliceCall.getLastReceivedRequest().getRawContent());
        assertTrue(aliceCall.sendIncomingCallResponse(Response.OK, "OK-Alice", 3600, receivedBody, "application", "sdp", null,
                null));
        assertTrue(aliceCall.waitForAck(50 * 1000));

        Thread.sleep(7000);

        // hangup.
        bobCall.disconnect();

        aliceCall.listenForDisconnect();
        assertTrue(aliceCall.waitForDisconnect(30 * 1000));
        assertTrue(aliceCall.respondToDisconnect());

        bobCall.listenForMessage();
        assertTrue(bobCall.waitForMessage(60 * 1000));
        assertTrue(bobCall.sendMessageResponse(200, "OK-Message Received", 3600));
        Request messageReceived = bobCall.getLastReceivedMessageRequest();
        assertTrue(new String(messageReceived.getRawContent()).equalsIgnoreCase("Hello World!"));

        Thread.sleep(5000);

        final String deploymentUrl = "http://127.0.0.1:8080/restcomm/";
        JsonArray recordings = RestcommCallsTool.getInstance().getRecordings(deploymentUrl, adminAccountSid, adminAuthToken);
        assertNotNull(recordings);
        assertTrue("7.0".equalsIgnoreCase(((JsonObject)recordings.get(0)).get("duration").getAsString()));
        assertNotNull(((JsonObject)recordings.get(0)).get("uri").getAsString());
    }

    private String dialClientWithRecordingRcml2 = "<Response><Dial timeLimit=\"10\" timeout=\"10\" record=\"true\" action=\"http://127.0.0.1:8090/action&sticky_numToDial=00306986971731\" method=\"GET\"><Client>alice</Client></Dial></Response>";
    @Test
    public synchronized void testDialClientAliceWithRecord2() throws InterruptedException, ParseException {
        stubFor(get(urlPathEqualTo("/1111"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(dialClientWithRecordingRcml2)));

        stubFor(get(urlPathEqualTo("/action"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(sendSmsActionRcml)));

        // Phone2 register as alice
        SipURI uri = aliceSipStack.getAddressFactory().createSipURI(null, "127.0.0.1:5080");
        assertTrue(alicePhone.register(uri, "alice", "1234", aliceContact, 3600, 3600));

        // Prepare second phone to receive call
        SipCall aliceCall = alicePhone.createSipCall();
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
        assertTrue(!(bobCall.getLastReceivedResponse().getStatusCode() >= 400));

        assertTrue(aliceCall.waitForIncomingCall(30 * 1000));
        assertTrue(aliceCall.sendIncomingCallResponse(Response.RINGING, "Ringing-Alice", 3600));
        String receivedBody = new String(aliceCall.getLastReceivedRequest().getRawContent());
        assertTrue(aliceCall.sendIncomingCallResponse(Response.OK, "OK-Alice", 3600, receivedBody, "application", "sdp", null,
                null));
        assertTrue(aliceCall.waitForAck(50 * 1000));

        Thread.sleep(7000);

        // hangup.
        bobCall.disconnect();

        aliceCall.listenForDisconnect();
        assertTrue(aliceCall.waitForDisconnect(30 * 1000));
        assertTrue(aliceCall.respondToDisconnect());

        Thread.sleep(10000);

        bobCall.listenForMessage();
        assertTrue(bobCall.waitForMessage(60 * 1000));
        assertTrue(bobCall.sendMessageResponse(200, "OK-Message Received", 3600));
        Request messageReceived = bobCall.getLastReceivedMessageRequest();
        assertTrue(new String(messageReceived.getRawContent()).equalsIgnoreCase("Hello World!"));

        Thread.sleep(5000);

        final String deploymentUrl = "http://127.0.0.1:8080/restcomm/";
        JsonArray recordings = RestcommCallsTool.getInstance().getRecordings(deploymentUrl, adminAccountSid, adminAuthToken);
        assertNotNull(recordings);
        assertTrue("7.0".equalsIgnoreCase(((JsonObject)recordings.get(0)).get("duration").getAsString()));
        assertNotNull(((JsonObject)recordings.get(0)).get("uri").getAsString());
    }

    private String dialConferenceWithDialActionRcml = "<Response><Dial action=\"http://127.0.0.1:8090/action\" method=\"GET\"><Conference>test</Conference></Dial></Response>";
    @Test
    public synchronized void testDialConferenceWithDialActionSms() throws InterruptedException, ParseException {
        stubFor(get(urlPathEqualTo("/1111"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(dialConferenceWithDialActionRcml)));

        stubFor(get(urlPathEqualTo("/action"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(sendSmsActionRcml)));

        // Phone2 register as alice
        SipURI uri = aliceSipStack.getAddressFactory().createSipURI(null, "127.0.0.1:5080");
        assertTrue(alicePhone.register(uri, "alice", "1234", aliceContact, 3600, 3600));

        // Prepare second phone to receive call
        SipCall aliceCall = alicePhone.createSipCall();
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
        assertTrue(!(bobCall.getLastReceivedResponse().getStatusCode() >= 400));

        //Now bob is connected to the conference room

        Thread.sleep(7000);

        // hangup.
        bobCall.disconnect();

        bobCall.listenForMessage();
        assertTrue(bobCall.waitForMessage(60 * 1000));
        assertTrue(bobCall.sendMessageResponse(200, "OK-Message Received", 3600));
        Request messageReceived = bobCall.getLastReceivedMessageRequest();
        assertTrue(new String(messageReceived.getRawContent()).equalsIgnoreCase("Hello World!"));
    }

    @Test
    public synchronized void testDialConferenceWithDialActionNoRcml() throws InterruptedException, ParseException {
        stubFor(get(urlPathEqualTo("/1111"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(dialConferenceWithDialActionRcml)));

        stubFor(get(urlPathEqualTo("/action"))
                .willReturn(aResponse()
                        .withStatus(200)));

        // Phone2 register as alice
        SipURI uri = aliceSipStack.getAddressFactory().createSipURI(null, "127.0.0.1:5080");
        assertTrue(alicePhone.register(uri, "alice", "1234", aliceContact, 3600, 3600));

        // Prepare second phone to receive call
        SipCall aliceCall = alicePhone.createSipCall();
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
        assertTrue(!(bobCall.getLastReceivedResponse().getStatusCode() >= 400));

        //Now bob is connected to the conference room

        Thread.sleep(7000);

        // hangup.
        bobCall.disconnect();
        assertTrue(bobCall.waitForAnswer(5000));
    }

    private String dialConferenceNoDialActionRcml = "<Response><Dial><Conference>test</Conference></Dial>" +
            "<Sms to=\"bob\" from=\"+12223334499\">Hello World!</Sms></Response>";
    @Test
    public synchronized void testDialConferenceNoDialActionNoSms() throws InterruptedException, ParseException {
        stubFor(get(urlPathEqualTo("/1111"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(dialConferenceNoDialActionRcml)));

        // Phone2 register as alice
        SipURI uri = aliceSipStack.getAddressFactory().createSipURI(null, "127.0.0.1:5080");
        assertTrue(alicePhone.register(uri, "alice", "1234", aliceContact, 3600, 3600));

        // Prepare second phone to receive call
        SipCall aliceCall = alicePhone.createSipCall();
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
        assertTrue(!(bobCall.getLastReceivedResponse().getStatusCode() >= 400));

        //Now bob is connected to the conference room

        Thread.sleep(7000);

        // hangup.
        bobCall.disconnect();
        assertTrue(bobCall.waitForAnswer(5000));

        bobCall.listenForMessage();
        assertTrue(bobCall.waitForMessage(60 * 1000));
        assertTrue(bobCall.sendMessageResponse(200, "OK-Message Received", 3600));
        Request messageReceived = bobCall.getLastReceivedMessageRequest();
        assertTrue(new String(messageReceived.getRawContent()).equalsIgnoreCase("Hello World!"));
    }

    private String dialConferenceNoDialActionSendSMSRcml = "<Response><Dial><Conference>test</Conference></Dial></Response>";
    @Test
    public synchronized void testDialConferenceNoDialActionSendSms() throws InterruptedException, ParseException {
        stubFor(get(urlPathEqualTo("/1111"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(dialConferenceWithDialActionRcml)));

        stubFor(get(urlPathEqualTo("/action"))
                .willReturn(aResponse()
                        .withStatus(200)));

        // Phone2 register as alice
        SipURI uri = aliceSipStack.getAddressFactory().createSipURI(null, "127.0.0.1:5080");
        assertTrue(alicePhone.register(uri, "alice", "1234", aliceContact, 3600, 3600));

        // Prepare second phone to receive call
        SipCall aliceCall = alicePhone.createSipCall();
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
        assertTrue(!(bobCall.getLastReceivedResponse().getStatusCode() >= 400));

        //Now bob is connected to the conference room

        Thread.sleep(7000);

        // hangup.
        bobCall.disconnect();
        assertTrue(bobCall.waitForAnswer(5000));
    }

    @Test //Test case for issue 320
    public synchronized void testDialClientAliceWithRecordAndStatusCallbackForApp() throws InterruptedException, ParseException {
        stubFor(get(urlPathEqualTo("/1111"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(dialClientWithRecordingRcml)));

        stubFor(get(urlPathEqualTo("/action"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(sendSmsActionRcml)));

        stubFor(get(urlPathMatching("/StatusCallBack.*"))
                .willReturn(aResponse()
                        .withStatus(200)));

        // Phone2 register as alice
        SipURI uri = aliceSipStack.getAddressFactory().createSipURI(null, "127.0.0.1:5080");
        assertTrue(alicePhone.register(uri, "alice", "1234", aliceContact, 3600, 3600));

        // Prepare second phone to receive call
        SipCall aliceCall = alicePhone.createSipCall();
        aliceCall.listenForIncomingCall();

        // Create outgoing call with first phone
        final SipCall bobCall = bobPhone.createSipCall();
        bobCall.initiateOutgoingCall(bobContact, dialRestcommWithStatusCallback, null, body, "application", "sdp", null, null);
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

        assertTrue(aliceCall.waitForIncomingCall(30 * 1000));
        assertTrue(aliceCall.sendIncomingCallResponse(Response.RINGING, "Ringing-Alice", 3600));
        String receivedBody = new String(aliceCall.getLastReceivedRequest().getRawContent());
        assertTrue(aliceCall.sendIncomingCallResponse(Response.OK, "OK-Alice", 3600, receivedBody, "application", "sdp", null,
                null));
        assertTrue(aliceCall.waitForAck(50 * 1000));

        Thread.sleep(7000);

        // hangup.
        bobCall.disconnect();

        aliceCall.listenForDisconnect();
        assertTrue(aliceCall.waitForDisconnect(30 * 1000));
        try {
            Thread.sleep(10 * 1000);
        } catch (final InterruptedException exception) {
            exception.printStackTrace();
        }

        bobCall.listenForMessage();
        assertTrue(bobCall.waitForMessage(60 * 1000));
        assertTrue(bobCall.sendMessageResponse(200, "OK-Message Received", 3600));
        Request messageReceived = bobCall.getLastReceivedMessageRequest();
        assertTrue(new String(messageReceived.getRawContent()).equalsIgnoreCase("Hello World!"));

        Thread.sleep(5000);

        JsonArray recordings = RestcommCallsTool.getInstance().getRecordings(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        assertNotNull(recordings);
        assertTrue("7.0".equalsIgnoreCase(((JsonObject)recordings.get(0)).get("duration").getAsString()));
        assertNotNull(((JsonObject)recordings.get(0)).get("uri").getAsString());

        logger.info("About to check the Status Callback Requests");
        Map<String, String> statusCallbacks = new HashMap<String,String>();

        List<LoggedRequest> requests = findAll(getRequestedFor(urlPathMatching("/StatusCallBack.*")));
        assertTrue(requests.size()==3);

//        for (LoggedRequest loggedRequest : requests) {
//            String queryParam = loggedRequest.getUrl().replaceFirst("/StatusCallBack?", "");
//            String[] params = queryParam.split("&");
//            String callSid = params[0].split("=")[1];
//            String callStatus = params[4].split("=")[1];
//            if (statusCallbacks.containsKey(callSid)) {
//                statusCallbacks.remove(callSid);
//            }
//            statusCallbacks.put(callSid, callStatus);
//        }
//        assertTrue(statusCallbacks.size()==1);
//        Iterator<String> iter = statusCallbacks.keySet().iterator();
//        while (iter.hasNext()) {
//            String key = iter.next();
//            assertTrue(statusCallbacks.get(key).equalsIgnoreCase("completed"));
//        }
    }

    private String dialClientWithActionRcml = "<Response><Dial action=\"http://127.0.0.1:8090/action\"><Client>alice</Client></Dial></Response>";
    private String hangupActionRcml = "<Response><Hangup /></Response>";

    @Test // (customised from testDialClientAliceWithRecordAndStatusCallbackForApp)
    public synchronized void testDialClientAliceWithActionAndStatusCallbackForApp() throws InterruptedException, ParseException {
        stubFor(get(urlPathEqualTo("/1111"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(dialClientWithActionRcml)));

        stubFor(get(urlPathEqualTo("/action"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(hangupActionRcml)));

        stubFor(get(urlPathMatching("/StatusCallBack.*"))
                .willReturn(aResponse()
                        .withStatus(200)));

        // Phone2 register as alice
        SipURI uri = aliceSipStack.getAddressFactory().createSipURI(null, "127.0.0.1:5080");
        assertTrue(alicePhone.register(uri, "alice", "1234", aliceContact, 3600, 3600));

        // Prepare second phone to receive call
        SipCall aliceCall = alicePhone.createSipCall();
        aliceCall.listenForIncomingCall();

        // Create outgoing call with first phone
        final SipCall bobCall = bobPhone.createSipCall();
        bobCall.initiateOutgoingCall(bobContact, dialRestcommWithStatusCallback, null, body, "application", "sdp", null, null);
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

        assertTrue(aliceCall.waitForIncomingCall(3 * 1000));
        assertTrue(aliceCall.sendIncomingCallResponse(Response.RINGING, "Ringing-Alice", 3600));
        String receivedBody = new String(aliceCall.getLastReceivedRequest().getRawContent());
        assertTrue(aliceCall.sendIncomingCallResponse(Response.OK, "OK-Alice", 3600, receivedBody, "application", "sdp", null,
                null));
        assertTrue(aliceCall.waitForAck(5 * 1000));

        Thread.sleep(7000);

        // hangup (must be as alice, the callee, hanging up in order to test the specific issue found)
        aliceCall.disconnect();
        bobCall.listenForDisconnect();
        assertTrue(bobCall.waitForDisconnect(3 * 1000));
        // NOTE: If you have bob hang up first instead of alice, this test will pass
//        bobCall.disconnect();
//        aliceCall.listenForDisconnect();
//        assertTrue(aliceCall.waitForDisconnect(3 * 1000));

        Thread.sleep(5000);

        logger.info("About to check the Status Callback Requests");
        List<LoggedRequest> requests = findAll(getRequestedFor(urlPathMatching("/StatusCallBack.*")));

        for (LoggedRequest loggedRequest : requests) {
            logger.info("Status callback received: " + loggedRequest.getUrl());
        }
        assertTrue(requests.size()==3);
    }    

    @Test //Test case for issue 320
    public synchronized void testDialClientAliceWithRecordAndStatusCallbackForAppForThreeCalls() throws InterruptedException, ParseException, MalformedURLException {
        stubFor(get(urlPathEqualTo("/1111"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(dialClientWithRecordingRcml)));

        stubFor(get(urlPathEqualTo("/action"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(sendSmsActionRcml)));

        stubFor(get(urlPathMatching("/StatusCallBack.*"))
                .willReturn(aResponse()
                        .withStatus(200)));

        // Phone2 register as alice
        SipURI uri = aliceSipStack.getAddressFactory().createSipURI(null, "127.0.0.1:5080");
        assertTrue(alicePhone.register(uri, "alice", "1234", aliceContact, 3600, 3600));

        // Prepare second phone to receive call
        SipCall aliceCall = alicePhone.createSipCall();
        aliceCall.listenForIncomingCall();

        // Create outgoing call with first phone
        final SipCall bobCall = bobPhone.createSipCall();
        bobCall.initiateOutgoingCall(bobContact, dialRestcommWithStatusCallback, null, body, "application", "sdp", null, null);
        assertLastOperationSuccess(bobCall);
        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        int response = bobCall.getLastReceivedResponse().getStatusCode();
        assertTrue(response == Response.TRYING || response == Response.RINGING);

        if (response == Response.TRYING) {
            assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
            assertEquals(Response.RINGING, bobCall.getLastReceivedResponse().getStatusCode());
        }

        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        assertEquals(Response.OK, bobCall.getLastReceivedResponse().getStatusCode());

        bobCall.sendInviteOkAck();
        assertTrue(!(bobCall.getLastReceivedResponse().getStatusCode() >= 400));

        assertTrue(aliceCall.waitForIncomingCall(30 * 1000));
        assertTrue(aliceCall.sendIncomingCallResponse(Response.RINGING, "Ringing-Alice", 3600));
        String receivedBody = new String(aliceCall.getLastReceivedRequest().getRawContent());
        assertTrue(aliceCall.sendIncomingCallResponse(Response.OK, "OK-Alice", 3600, receivedBody, "application", "sdp", null,
                null));
        assertTrue(aliceCall.waitForAck(50 * 1000));

        Thread.sleep(7000);

        // hangup.
        bobCall.disconnect();

        aliceCall.listenForDisconnect();
        assertTrue(aliceCall.waitForDisconnect(30 * 1000));
        try {
            Thread.sleep(10 * 1000);
        } catch (final InterruptedException exception) {
            exception.printStackTrace();
        }

        bobCall.listenForMessage();
        assertTrue(bobCall.waitForMessage(60 * 1000));
        assertTrue(bobCall.sendMessageResponse(200, "OK-Message Received", 3600));
        Request messageReceived = bobCall.getLastReceivedMessageRequest();
        assertTrue(new String(messageReceived.getRawContent()).equalsIgnoreCase("Hello World!"));

        Thread.sleep(5000);

        final String deploymentUrl = "http://127.0.0.1:8080/restcomm/";
        JsonArray recordings = RestcommCallsTool.getInstance().getRecordings(deploymentUrl, adminAccountSid, adminAuthToken);
        assertNotNull(recordings);
        int recordingsSize = recordings.size();
        logger.info("Recording Size: "+recordingsSize);
        assertTrue(recordingsSize >= 1 || recordingsSize <= 3);
        assertTrue("7.0".equalsIgnoreCase(((JsonObject)recordings.get(0)).get("duration").getAsString()));
        assertNotNull(((JsonObject)recordings.get(0)).get("uri").getAsString());

        /*
         * Start the second call
         */

        Thread.sleep(2000);

        bobCall.initiateOutgoingCall(bobContact, dialRestcomm, null, body, "application", "sdp", null, null);
        assertLastOperationSuccess(bobCall);
        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        response = bobCall.getLastReceivedResponse().getStatusCode();
        assertTrue(response == Response.TRYING || response == Response.RINGING);

        if (response == Response.TRYING) {
            assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
            assertEquals(Response.RINGING, bobCall.getLastReceivedResponse().getStatusCode());
        }

        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        assertEquals(Response.OK, bobCall.getLastReceivedResponse().getStatusCode());

        bobCall.sendInviteOkAck();
        assertTrue(!(bobCall.getLastReceivedResponse().getStatusCode() >= 400));

        assertTrue(aliceCall.waitForIncomingCall(30 * 1000));
        assertTrue(aliceCall.sendIncomingCallResponse(Response.RINGING, "Ringing-Alice", 3600));
        receivedBody = new String(aliceCall.getLastReceivedRequest().getRawContent());
        assertTrue(aliceCall.sendIncomingCallResponse(Response.OK, "OK-Alice", 3600, receivedBody, "application", "sdp", null,
                null));
        assertTrue(aliceCall.waitForAck(50 * 1000));

        Thread.sleep(7000);

        // hangup.
        bobCall.disconnect();

        aliceCall.listenForDisconnect();
        assertTrue(aliceCall.waitForDisconnect(30 * 1000));
        try {
            Thread.sleep(10 * 1000);
        } catch (final InterruptedException exception) {
            exception.printStackTrace();
        }

        bobCall.listenForMessage();
        assertTrue(bobCall.waitForMessage(60 * 1000));
        assertTrue(bobCall.sendMessageResponse(200, "OK-Message Received", 3600));
        messageReceived = bobCall.getLastReceivedMessageRequest();
        assertTrue(new String(messageReceived.getRawContent()).equalsIgnoreCase("Hello World!"));

        Thread.sleep(3000);

        recordings = RestcommCallsTool.getInstance().getRecordings(deploymentUrl, adminAccountSid, adminAuthToken);
        assertNotNull(recordings);
        assertTrue(recordings.size() >= 2);
        assertTrue("7.0".equalsIgnoreCase(((JsonObject)recordings.get(1)).get("duration").getAsString()));
        assertNotNull(((JsonObject)recordings.get(1)).get("uri").getAsString());

        /*
         * Start the third call
         */

        Thread.sleep(2000);

        bobCall.initiateOutgoingCall(bobContact, dialRestcomm, null, body, "application", "sdp", null, null);
        assertLastOperationSuccess(bobCall);
        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        response = bobCall.getLastReceivedResponse().getStatusCode();
        assertTrue(response == Response.TRYING || response == Response.RINGING);

        if (response == Response.TRYING) {
            assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
            assertEquals(Response.RINGING, bobCall.getLastReceivedResponse().getStatusCode());
        }

        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        assertEquals(Response.OK, bobCall.getLastReceivedResponse().getStatusCode());

        bobCall.sendInviteOkAck();
        assertTrue(!(bobCall.getLastReceivedResponse().getStatusCode() >= 400));

        assertTrue(aliceCall.waitForIncomingCall(30 * 1000));
        assertTrue(aliceCall.sendIncomingCallResponse(Response.RINGING, "Ringing-Alice", 3600));
        receivedBody = new String(aliceCall.getLastReceivedRequest().getRawContent());
        assertTrue(aliceCall.sendIncomingCallResponse(Response.OK, "OK-Alice", 3600, receivedBody, "application", "sdp", null,
                null));
        assertTrue(aliceCall.waitForAck(50 * 1000));

        Thread.sleep(7000);

        // hangup.
        bobCall.disconnect();
        bobCall.stopListeningForRequests();

        aliceCall.listenForDisconnect();
        assertTrue(aliceCall.waitForDisconnect(30 * 1000));
        try {
            Thread.sleep(10 * 1000);
        } catch (final InterruptedException exception) {
            exception.printStackTrace();
        }

        bobCall.listenForMessage();
        assertTrue(bobCall.waitForMessage(60 * 1000));
        assertTrue(bobCall.sendMessageResponse(200, "OK-Message Received", 3600));
        messageReceived = bobCall.getLastReceivedMessageRequest();
        assertTrue(new String(messageReceived.getRawContent()).equalsIgnoreCase("Hello World!"));

        Thread.sleep(3000);

        recordings = RestcommCallsTool.getInstance().getRecordings(deploymentUrl, adminAccountSid, adminAuthToken);
        assertNotNull(recordings);
        assertTrue(recordings.size() >= 3);
        assertTrue("7.0".equalsIgnoreCase(((JsonObject)recordings.get(2)).get("duration").getAsString()));
        assertNotNull(((JsonObject)recordings.get(2)).get("uri").getAsString());

        logger.info("About to check the Status Callback Requests");
        Map<String, String> statusCallbacks = new HashMap<String,String>();
        List<LoggedRequest> requests = findAll(getRequestedFor(urlPathMatching("/StatusCallBack.*")));
        assertTrue(requests.size() == 3);

//        for (LoggedRequest loggedRequest : requests) {
//            String queryParam = loggedRequest.getUrl().replaceFirst("/StatusCallBack?", "");
//            String[] params = queryParam.split("&");
//            String callSid = params[0].split("=")[1];
//            String callStatus = params[4].split("=")[1];
//            if (statusCallbacks.containsKey(callSid)) {
//                statusCallbacks.remove(callSid);
//            }
//            statusCallbacks.put(callSid, callStatus);
//        }
//        assertTrue(statusCallbacks.size()==3);
//        Iterator<String> iter = statusCallbacks.keySet().iterator();
//        while (iter.hasNext()) {
//            String key = iter.next();
//            assertTrue(statusCallbacks.get(key).equalsIgnoreCase("completed"));
//        }
    }

    private String dialRecordWithActionRcml = "<Response><Record action=\"http://127.0.0.1:8090/recordAction\" method=\"GET\" finishOnKey=\"*\" maxLength=\"10\" playBeep=\"true\"/></Response>";
    @Test //Test case for github issue 859
    public synchronized void testRecordWithActionAndStatusCallbackForAppWithDisconnectFromBob() throws InterruptedException, ParseException {
        stubFor(get(urlPathEqualTo("/1111"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(dialRecordWithActionRcml)));

        stubFor(get(urlPathEqualTo("/recordAction"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(sendSmsActionRcml)));

        stubFor(get(urlPathMatching("/StatusCallBack.*"))
                .willReturn(aResponse()
                        .withStatus(200)));

        // Phone2 register as alice
        SipURI uri = aliceSipStack.getAddressFactory().createSipURI(null, "127.0.0.1:5080");
        assertTrue(alicePhone.register(uri, "alice", "1234", aliceContact, 3600, 3600));

        // Prepare second phone to receive call
        SipCall aliceCall = alicePhone.createSipCall();
        aliceCall.listenForIncomingCall();

        // Create outgoing call with first phone
        final SipCall bobCall = bobPhone.createSipCall();
        bobCall.initiateOutgoingCall(bobContact, dialRestcommWithStatusCallback, null, body, "application", "sdp", null, null);
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

        //Here we have Restcomm voice mail app for 10 sec
        Thread.sleep(9000);

        // hangup.
        bobCall.disconnect();

        bobCall.listenForMessage();
        assertTrue(bobCall.waitForMessage(60 * 1000));
        assertTrue(bobCall.sendMessageResponse(200, "OK-Message Received", 3600));
        Request messageReceived = bobCall.getLastReceivedMessageRequest();
        assertTrue(new String(messageReceived.getRawContent()).equalsIgnoreCase("Hello World!"));

        Thread.sleep(5000);

        JsonArray recordings = RestcommCallsTool.getInstance().getRecordings(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        assertNotNull(recordings);
        double recordingDuration = ((JsonObject)recordings.get(0)).get("duration").getAsDouble();
        assertTrue(recordingDuration <= 9.0 || recordingDuration <= 10.0);
        assertNotNull(((JsonObject)recordings.get(0)).get("uri").getAsString());

        logger.info("About to check the Status Callback Requests");
        Map<String, String> statusCallbacks = new HashMap<String,String>();

        List<LoggedRequest> statusCallbackRequests = findAll(getRequestedFor(urlPathMatching("/StatusCallBack.*")));
        assertTrue(statusCallbackRequests.size()==3);

        List<LoggedRequest> recordActionRequests = findAll(getRequestedFor(urlPathMatching("/recordAction.*")));
        assertTrue(recordActionRequests.size()==1);
    }

    @Test //Test case for github issue 859
    public synchronized void testRecordWithActionAndStatusCallbackForAppWithBobSendsFinishKey() throws InterruptedException, ParseException {
        stubFor(get(urlPathEqualTo("/1111"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(dialRecordWithActionRcml)));

        stubFor(get(urlPathEqualTo("/recordAction"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(sendSmsActionRcml)));

        stubFor(get(urlPathMatching("/StatusCallBack.*"))
                .willReturn(aResponse()
                        .withStatus(200)));

        // Phone2 register as alice
        SipURI uri = aliceSipStack.getAddressFactory().createSipURI(null, "127.0.0.1:5080");
        assertTrue(alicePhone.register(uri, "alice", "1234", aliceContact, 3600, 3600));

        // Prepare second phone to receive call
        SipCall aliceCall = alicePhone.createSipCall();
        aliceCall.listenForIncomingCall();

        // Create outgoing call with first phone
        final SipCall bobCall = bobPhone.createSipCall();
        bobCall.initiateOutgoingCall(bobContact, dialRestcommWithStatusCallback, null, body, "application", "sdp", null, null);
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

        //Here we have Restcomm voice mail app for 10 sec
        Thread.sleep(5000);

        Dialog dialog = bobCall.getDialog();
        String infoBody = "\n" +
                "Signal=*\n" +
                "Duration=28";
        Request info = null;
        try {
            info = dialog.createRequest(Request.INFO);
        } catch (SipException e) {
            e.printStackTrace();
        }
        ContentType contentType = new ContentType();
        contentType.setContentType("application");
        contentType.setContentSubType("dtmf-relay");
        info.setContent(infoBody.getBytes(), contentType);
        String ruri = info.getRequestURI().toString();

        SipTransaction infoTransaction = bobPhone.sendRequestWithTransaction(info, false, dialog);
        assertNotNull(infoTransaction);

        bobCall.listenForDisconnect();
        assertTrue(bobCall.waitForDisconnect(5000));
        assertTrue(bobCall.respondToDisconnect());

        bobCall.listenForMessage();
        assertTrue(bobCall.waitForMessage(60 * 1000));
        assertTrue(bobCall.sendMessageResponse(200, "OK-Message Received", 3600));
        Request messageReceived = bobCall.getLastReceivedMessageRequest();
        assertTrue(new String(messageReceived.getRawContent()).equalsIgnoreCase("Hello World!"));

        Thread.sleep(5000);

        JsonArray recordings = RestcommCallsTool.getInstance().getRecordings(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        assertNotNull(recordings);
        double recordingDuration = ((JsonObject)recordings.get(0)).get("duration").getAsDouble();
        assertTrue(recordingDuration <= 9.0 || recordingDuration <= 10.0);
        assertNotNull(((JsonObject)recordings.get(0)).get("uri").getAsString());

        logger.info("About to check the Status Callback Requests");
        Map<String, String> statusCallbacks = new HashMap<String,String>();

        List<LoggedRequest> statusCallbackRequests = findAll(getRequestedFor(urlPathMatching("/StatusCallBack.*")));
        assertTrue(statusCallbackRequests.size()==3);

        List<LoggedRequest> recordActionRequests = findAll(getRequestedFor(urlPathMatching("/recordAction.*")));
        assertTrue(recordActionRequests.size()==1);
    }

    private String dialNumberRcml = "<Response><Dial callerId=\"+13055872294\"><Number url=\"http://127.0.0.1:8080/restcomm/hello-play.xml\">131313</Number></Dial></Response>";
    @Test
    public synchronized void testDialNumberGeorge() throws InterruptedException, ParseException {
        stubFor(get(urlPathEqualTo("/1111"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(dialNumberRcml)));

        // Prepare George phone to receive call
        georgePhone.setLoopback(true);
        SipCall georgeCall = georgePhone.createSipCall();
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
        assertTrue(!(bobCall.getLastReceivedResponse().getStatusCode() >= 400));

        assertTrue(georgeCall.waitForIncomingCall(30 * 1000));
        assertTrue(georgeCall.sendIncomingCallResponse(Response.RINGING, "Ringing-George", 3600));
        final SipRequest lastRequest = georgeCall.getLastReceivedRequest();
        String receivedBody = new String(lastRequest.getRawContent());
        assertTrue(georgeCall.sendIncomingCallResponse(Response.OK, "OK-George", 3600, receivedBody, "application", "sdp",
                null, null));
        // the number dialed uses a callerId of "+13055872294", which is what George should receive
        String contactHeader = georgeCall.getLastReceivedRequest().getMessage().getHeader("Contact").toString().replaceAll("\r\n","");
        assertTrue(contactHeader.equalsIgnoreCase("Contact: \"+13055872294\" <sip:+13055872294@127.0.0.1:5080>"));
        assertTrue(georgeCall.waitForAck(50 * 1000));

        Thread.sleep(3000);
        georgeCall.listenForDisconnect();
        // hangup.
        bobCall.disconnect();
        assertTrue(!bobCall.callTimeoutOrError());
        assertTrue(georgeCall.waitForDisconnect(30 * 1000));
        assertTrue(georgeCall.respondToDisconnect());
    }

    private String dialNumberRcmlWrongScreeningUrl = "<Response><Dial callerId=\"+13055872294\"><Number url=\"/restcomm/invalid.xml\">131313</Number></Dial></Response>";
    @Test
    public synchronized void testDialNumberGeorgeWithWrongScreeningUrl() throws InterruptedException, ParseException {
        stubFor(get(urlPathEqualTo("/1111"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(dialNumberRcmlWrongScreeningUrl)));

        // Prepare George phone to receive call
        georgePhone.setLoopback(true);
        SipCall georgeCall = georgePhone.createSipCall();
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
        assertTrue(!(bobCall.getLastReceivedResponse().getStatusCode() >= 400));

        assertTrue(georgeCall.waitForIncomingCall(30 * 1000));
        assertTrue(georgeCall.sendIncomingCallResponse(Response.RINGING, "Ringing-George", 3600));
        final SipRequest lastRequest = georgeCall.getLastReceivedRequest();
        String receivedBody = new String(lastRequest.getRawContent());
        assertTrue(georgeCall.sendIncomingCallResponse(Response.OK, "OK-George", 3600, receivedBody, "application", "sdp",
                null, null));
        // the number dialed uses a callerId of "+13055872294", which is what George should receive
        String contactHeader = georgeCall.getLastReceivedRequest().getMessage().getHeader("Contact").toString().replaceAll("\r\n","");
        assertTrue(contactHeader.equalsIgnoreCase("Contact: \"+13055872294\" <sip:+13055872294@127.0.0.1:5080>"));
        assertTrue(georgeCall.waitForAck(50 * 1000));

        //Since the Screening URL is not valid, Restcomm will disconnect call
        bobCall.listenForDisconnect();
        georgeCall.listenForDisconnect();

        assertTrue(bobCall.waitForDisconnect(5000));
        assertTrue(bobCall.respondToDisconnect());

        assertTrue(georgeCall.waitForDisconnect(30 * 1000));
        assertTrue(georgeCall.respondToDisconnect());

//        Thread.sleep(3000);
//        georgeCall.listenForDisconnect();
//        // hangup.
//        bobCall.disconnect();
//        assertTrue(!bobCall.callTimeoutOrError());
//        assertTrue(georgeCall.waitForDisconnect(30 * 1000));
//        assertTrue(georgeCall.respondToDisconnect());
    }

    //Non-regression test for https://github.com/Mobicents/RestComm/issues/505
    @Test
    public synchronized void testDialNumberGeorge_403Forbidden() throws InterruptedException, ParseException {
        stubFor(get(urlPathEqualTo("/1111"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(dialNumberRcml)));

//        SipURI uri = bobSipStack.getAddressFactory().createSipURI(null, "127.0.0.1:5080");
//        assertTrue(bobPhone.register(uri, "bob", "1234", bobContact, 3600, 3600));
//
//        Credential c = new Credential("127.0.0.1", "bob", "1234");
//        bobPhone.addUpdateCredential(c);

        // Prepare George phone to receive call
        georgePhone.setLoopback(true);
        SipCall georgeCall = georgePhone.createSipCall();
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
        assertTrue(!(bobCall.getLastReceivedResponse().getStatusCode() >= 400));

        assertTrue(georgeCall.waitForIncomingCall(30 * 1000));
        assertTrue(georgeCall.sendIncomingCallResponse(Response.FORBIDDEN, "FORBIDDEN-George", 3600));
        assertTrue(georgeCall.waitForAck(50 * 1000));

        bobCall.listenForDisconnect();
        assertTrue(bobCall.waitForDisconnect(5000));
        assertTrue(bobCall.respondToDisconnect());
    }

    //Non-regression test for https://github.com/Mobicents/RestComm/issues/505
    @Test
    public synchronized void testDialNumberGeorge_404_OnBye() throws InterruptedException, ParseException {
        stubFor(get(urlPathEqualTo("/1111"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(dialNumberRcml)));

        // Prepare George phone to receive call
        georgePhone.setLoopback(true);
        SipCall georgeCall = georgePhone.createSipCall();
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
        assertTrue(!(bobCall.getLastReceivedResponse().getStatusCode() >= 400));

        assertTrue(georgeCall.waitForIncomingCall(30 * 1000));
        assertTrue(georgeCall.sendIncomingCallResponse(Response.RINGING, "Ringing-George", 3600));
        String receivedBody = new String(georgeCall.getLastReceivedRequest().getRawContent());
        assertTrue(georgeCall.sendIncomingCallResponse(Response.OK, "OK-George", 3600, receivedBody, "application", "sdp",
                null, null));
        assertTrue(georgeCall.waitForAck(50 * 1000));

        Thread.sleep(3000);
        bobCall.disconnect();

        georgeCall.listenForDisconnect();
        georgeCall.waitForDisconnect(5000);
        georgeCall.respondToDisconnect(404, "Not Here");
        georgeCall.disposeNoBye();
    }

    final String dialNumberNoCallerId = "<Response><Dial><Number url=\"http://127.0.0.1:8080/restcomm/hello-play.xml\">131313</Number></Dial></Response>";
    //Test for Issue 210: https://telestax.atlassian.net/browse/RESTCOMM-210
//Bob callerId should pass to the call created by Dial Number
    @Test
    public synchronized void testDialNumberGeorgePassInitialCallerId() throws InterruptedException, ParseException {
        stubFor(get(urlPathEqualTo("/1111"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(dialNumberNoCallerId)));

        // Prepare George phone to receive call
        georgePhone.setLoopback(true);
        SipCall georgeCall = georgePhone.createSipCall();
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
        assertTrue(!(bobCall.getLastReceivedResponse().getStatusCode() >= 400));

        assertTrue(georgeCall.waitForIncomingCall(30 * 1000));
        SipRequest georgeInvite = georgeCall.getLastReceivedRequest();
        assertTrue(((FromHeader)georgeInvite.getMessage().getHeader("From")).getAddress().getDisplayName().contains("bob"));
        assertTrue(georgeCall.sendIncomingCallResponse(Response.RINGING, "Ringing-George", 3600));
        String receivedBody = new String(georgeCall.getLastReceivedRequest().getRawContent());
        assertTrue(georgeCall.sendIncomingCallResponse(Response.OK, "OK-George", 3600, receivedBody, "application", "sdp",
                null, null));
        assertTrue(georgeCall.waitForAck(50 * 1000));

        Thread.sleep(3000);
        georgeCall.listenForDisconnect();
        // hangup.
        bobCall.disconnect();

        assertTrue(georgeCall.waitForDisconnect(30 * 1000));
        assertTrue(georgeCall.respondToDisconnect());
    }

    private String didRcml = "<Response><Play>/restcomm/audio/demo-prompt.wav</Play></Response>";
    @Test
    public synchronized void testDialNumberWithPlusSign() throws InterruptedException, ParseException {
        stubFor(get(urlPathEqualTo("/12349876543"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(didRcml)));

        // Prepare George phone to receive call
        georgePhone.setLoopback(true);
        SipCall georgeCall = georgePhone.createSipCall();
        georgeCall.listenForIncomingCall();

        // Create outgoing call with first phone
        final SipCall bobCall = bobPhone.createSipCall();
        bobCall.initiateOutgoingCall(bobContact, "sip:+12349876543@127.0.0.1:5080", null, body, "application", "sdp", null, null);
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
        bobCall.listenForDisconnect();

        Thread.sleep(3000);
        assertTrue(bobCall.waitForDisconnect(10000));
        assertTrue(bobCall.respondToDisconnect());
    }

    @Test
    public synchronized void testDialNumberWithOUTPlusSign() throws InterruptedException, ParseException {
        stubFor(get(urlPathEqualTo("/12349876543"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(didRcml)));

        // Prepare George phone to receive call
        georgePhone.setLoopback(true);
        SipCall georgeCall = georgePhone.createSipCall();
        georgeCall.listenForIncomingCall();

        // Create outgoing call with first phone
        final SipCall bobCall = bobPhone.createSipCall();
        bobCall.initiateOutgoingCall(bobContact, "sip:12349876543@127.0.0.1:5080", null, body, "application", "sdp", null, null);
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
        bobCall.listenForDisconnect();

        Thread.sleep(3000);
        assertTrue(bobCall.waitForDisconnect(10000));
        assertTrue(bobCall.respondToDisconnect());
    }

    @Test
    public synchronized void testDialNumberNoCountryAccessCode() throws InterruptedException, ParseException {
        stubFor(get(urlPathEqualTo("/12349876543"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(didRcml)));

        // Prepare George phone to receive call
        georgePhone.setLoopback(true);
        SipCall georgeCall = georgePhone.createSipCall();
        georgeCall.listenForIncomingCall();

        // Create outgoing call with first phone
        final SipCall bobCall = bobPhone.createSipCall();
        bobCall.initiateOutgoingCall(bobContact, "sip:2349876543@127.0.0.1:5080", null, body, "application", "sdp", null, null);
        assertLastOperationSuccess(bobCall);
        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        final int response = bobCall.getLastReceivedResponse().getStatusCode();
        assertTrue(response == Response.NOT_FOUND);
    }

    private String rejectRejectedRcml = "<Response>" +
            "<Reject reason=\"rejected\"/>" +
            "<Dial><Number>131313</Number></Dial>" +
            "</Response>";
    @Test
    public synchronized void testDialNumberRejectRejectedRcml() throws InterruptedException, ParseException {
        stubFor(get(urlPathEqualTo("/1111"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(rejectRejectedRcml)));

        // Prepare George phone to receive call
        georgePhone.setLoopback(true);
        SipCall georgeCall = georgePhone.createSipCall();
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
        assertEquals(Response.DECLINE, bobCall.getLastReceivedResponse().getStatusCode());
    }

    private String rejectBusyRcml = "<Response>" +
            "<Reject reason=\"busy\"/>" +
            "<Dial><Number>131313</Number></Dial>" +
            "</Response>";
    @Test
    public synchronized void testDialNumberRejectBusyRcml() throws InterruptedException, ParseException {
        stubFor(get(urlPathEqualTo("/1111"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(rejectBusyRcml)));

        // Prepare George phone to receive call
        georgePhone.setLoopback(true);
        SipCall georgeCall = georgePhone.createSipCall();
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
        assertEquals(Response.BUSY_HERE, bobCall.getLastReceivedResponse().getStatusCode());
    }

    @Deployment(name = "TestDialVerbPartTwo", managed = true, testable = false)
    public static WebArchive createWebArchiveNoGw() {
        logger.info("Packaging Test App");
        WebArchive archive = ShrinkWrap.create(WebArchive.class, "restcomm.war");
        final WebArchive restcommArchive = ShrinkWrapMaven.resolver()
                .resolve("org.restcomm:restcomm-connect.application:war:" + version).withoutTransitivity()
                .asSingle(WebArchive.class);
        archive = archive.merge(restcommArchive);
        archive.delete("/WEB-INF/sip.xml");
        archive.delete("/WEB-INF/conf/restcomm.xml");
        archive.delete("/WEB-INF/data/hsql/restcomm.script");
        archive.addAsWebInfResource("sip.xml");
        archive.addAsWebInfResource("restcomm.xml", "conf/restcomm.xml");
        archive.addAsWebInfResource("restcomm.script_dialTest_new", "data/hsql/restcomm.script");
        archive.addAsWebResource("hello-play.xml");
        logger.info("Packaged Test App");
        return archive;
    }

}
