package org.restcomm.connect.testsuite.telephony;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.findAll;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import javax.sip.address.SipURI;
import javax.sip.message.Response;
import org.apache.log4j.Logger;
import static org.cafesip.sipunit.SipAssert.assertLastOperationSuccess;
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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.restcomm.connect.commons.annotations.FeatureAltTests;
import org.restcomm.connect.commons.annotations.FeatureExpTests;
import org.restcomm.connect.commons.annotations.SequentialClassTests;
import org.restcomm.connect.commons.annotations.UnstableTests;
import org.restcomm.connect.testsuite.NetworkPortAssigner;
import org.restcomm.connect.testsuite.WebArchiveUtil;
import org.restcomm.connect.testsuite.http.RestcommCallsTool;
import org.restcomm.connect.testsuite.tools.MonitoringServiceTool;

/**
 * Tests for the Dial forking
 * Created by gvagenas on 12/19/15.
 */
@RunWith(Arquillian.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@Category(SequentialClassTests.class)
public class DialForkTest {

    private final static Logger logger = Logger.getLogger(CallLifecycleTest.class.getName());

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
    private String dialForkWithActionUrl = "<Response><Dial timeLimit=\"1000\" timeout=\"2\" action=\"http://127.0.0.1:" + mockPort+ "/test\">" +
            "<Number>+131313</Number><Uri>sip:henrique@127.0.0.1:" + henriquePort +"</Uri><Client>alice</Client></Dial></Response>";
    private String rcmlToReturn = "<Response><Dial timeout=\"50\"><Uri>sip:fotini@127.0.0.1:" + fotiniPort + "</Uri></Dial></Response>";
    private String dialForkToNotRegisteredClientDialClientFirst = "<Response><Dial><Client>alice</Client><Sip>sip:henrique@127.0.0.1:" + henriquePort + "</Sip></Dial></Response>";
    private String dialFork = "<Response><Dial><Client>alice</Client><Sip>sip:henrique@127.0.0.1:" + henriquePort + "</Sip><Number>+131313</Number></Dial></Response>";
    private String dialForkToNotRegisteredClientSipFirst = "<Response><Dial><Sip>sip:henrique@127.0.0.1:" + henriquePort + "</Sip><Client>alice</Client></Dial></Response>";
    private String dialForkWithTimeout15 = "<Response><Dial timeout=\"15\"><Client>alice</Client><Sip>sip:henrique@127.0.0.1:" + henriquePort + "</Sip><Number>+131313</Number></Dial></Response>";
    private String dialSequential = "<Response><Dial timeout=\"5\"><Sip>sip:nonexistent@127.0.0.1:5566</Sip></Dial><Dial timeout=\"5\"><Sip>sip:nonexistent2@127.0.0.1:6655</Sip></Dial><Dial><Sip>sip:henrique@127.0.0.1:" + henriquePort + "</Sip></Dial></Response>";
    private String dialForkTwoSipUrisRcml = "<Response><Dial><Sip>sip:fotini@127.0.0.1:" + fotiniPort + "</Sip><Sip>sip:henrique@127.0.0.1:" + henriquePort + "</Sip></Dial></Response>";


    private static SipStackTool tool1;
    private static SipStackTool tool2;
    private static SipStackTool tool3;
    private static SipStackTool tool4;
    private static SipStackTool tool5;

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

    // Fotini is a simple SIP Client. Will not register with Restcomm
    private SipStack fotiniSipStack;
    private SipPhone fotiniPhone;
    private static String fotiniPort = String.valueOf(NetworkPortAssigner.retrieveNextPortByFile());
    private String fotiniContact = "sip:fotini@127.0.0.1:" + fotiniPort;

    private String adminAccountSid = "ACae6e420f425248d6a26948c17a9e2acf";
    private String adminAuthToken = "77f8c12cc7b8f8423e5c38b035249166";

    private static int restcommPort = 5080;
    private static int restcommHTTPPort = 8080;
    private static String restcommContact = "127.0.0.1:" + restcommPort;
    private static String dialAliceRcmlWithPlay;
    private static String dialAliceRcmlWithInvalidPlay;
    private int timeout = 10;
    private String dialForkWithTimeout = "<Response><Dial timeout=\""+timeout+"\"><Client>alice</Client><Sip>sip:henrique@127.0.0.1:" + henriquePort + "</Sip><Number>+131313</Number></Dial></Response>";
    private int initialTotalCallSinceUptime;
    private int initialOutgoingCallsSinceUptime;

    @BeforeClass
    public static void beforeClass() throws Exception {
        tool1 = new SipStackTool("DialFork1");
        tool2 = new SipStackTool("DialFork2");
        tool3 = new SipStackTool("DialFork3");
        tool4 = new SipStackTool("DialFork4");
        tool5 = new SipStackTool("DialFork5");
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

        dialAliceRcmlWithPlay = "<Response><Play>" + deploymentUrl.toString() + "/audio/demo-prompt.wav</Play><Dial><Client>alice</Client></Dial></Response>";
        dialAliceRcmlWithInvalidPlay = "<Response><Play>" + deploymentUrl.toString() + "/audio/demo-prompt13.wav</Play><Dial><Client>alice</Client></Dial></Response>";

        bobSipStack = tool1.initializeSipStack(SipStack.PROTOCOL_UDP, "127.0.0.1", bobPort, restcommContact);
        bobPhone = bobSipStack.createSipPhone("127.0.0.1", SipStack.PROTOCOL_UDP, restcommPort, bobContact);

        aliceSipStack = tool2.initializeSipStack(SipStack.PROTOCOL_UDP, "127.0.0.1", alicePort, restcommContact);
        alicePhone = aliceSipStack.createSipPhone("127.0.0.1", SipStack.PROTOCOL_UDP, restcommPort, aliceContact);

        henriqueSipStack = tool3.initializeSipStack(SipStack.PROTOCOL_UDP, "127.0.0.1", henriquePort, restcommContact);
        henriquePhone = henriqueSipStack.createSipPhone("127.0.0.1", SipStack.PROTOCOL_UDP, restcommPort, henriqueContact);

        georgeSipStack = tool4.initializeSipStack(SipStack.PROTOCOL_UDP, "127.0.0.1", georgePort, restcommContact);
        georgePhone = georgeSipStack.createSipPhone("127.0.0.1", SipStack.PROTOCOL_UDP, restcommPort, georgeContact);

        fotiniSipStack = tool5.initializeSipStack(SipStack.PROTOCOL_UDP, "127.0.0.1", fotiniPort, restcommContact);
        fotiniPhone = fotiniSipStack.createSipPhone("127.0.0.1", SipStack.PROTOCOL_UDP, restcommPort, fotiniContact);
    }

    @After
    public void after() throws Exception {
        if (bobPhone != null) {
            bobPhone.dispose();
        }
        if (bobSipStack != null) {
            bobSipStack.dispose();
        }

        if (alicePhone != null) {
            alicePhone.dispose();
        }
        if (aliceSipStack != null) {
            aliceSipStack.dispose();
        }

        if (henriquePhone != null) {
            henriquePhone.dispose();
        }
        if (henriqueSipStack != null) {
            henriqueSipStack.dispose();
        }

        if (georgePhone != null) {
            georgePhone.dispose();
        }
        if (georgeSipStack != null) {
            georgeSipStack.dispose();
        }

        if (fotiniPhone != null) {
            fotiniPhone.dispose();
        }
        if (fotiniSipStack != null) {
            fotiniSipStack.dispose();
        }
        Thread.sleep(1000);
        wireMockRule.resetRequests();
        Thread.sleep(5000);
    }

    @Test
    public synchronized void testDialForkNoAnswerButHenrique() throws InterruptedException, ParseException, MalformedURLException {
        stubFor(get(urlPathEqualTo("/1111"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(dialFork)));

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
        assertEquals(2, liveCalls);
        assertEquals(2, liveCallsArraySize);

        henriqueCall.listenForDisconnect();

        Thread.sleep(8000);

        // hangup.

        bobCall.disconnect();

        assertTrue(henriqueCall.waitForDisconnect(30 * 1000));

        assertTrue(alicePhone.unregister(aliceContact, 3600));

        Thread.sleep(10 * 1000);

        assertTrue(MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken) == 0);
        assertTrue(MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(), adminAccountSid, adminAuthToken) == 0);

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

        metrics = MonitoringServiceTool.getInstance().getMetrics(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
        Map<String, Integer> mgcpResources = MonitoringServiceTool.getInstance().getMgcpResources(metrics);
        int mgcpEndpoints = mgcpResources.get("MgcpEndpoints");
        int mgcpConnections = mgcpResources.get("MgcpConnections");

        assertEquals(0, mgcpEndpoints);
        assertEquals(0, mgcpConnections);
    }

    private String dialForkFromPstn = "<Response><Dial><Client>alice</Client><Sip>sip:henrique@127.0.0.1:" + henriquePort + "</Sip></Dial></Response>";
    @Test
    public synchronized void testDialForkFromPstnNoAnswerButHenrique() throws InterruptedException, ParseException, MalformedURLException {
        stubFor(get(urlPathEqualTo("/1111"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(dialForkFromPstn)));

        // Register Alice
        SipURI uri = aliceSipStack.getAddressFactory().createSipURI(null, restcommContact);
        assertTrue(alicePhone.register(uri, "alice", "1234", aliceContact, 3600, 3600));

        // Prepare Alice to receive call
        final SipCall aliceCall = alicePhone.createSipCall();
        aliceCall.listenForIncomingCall();

        // Prepare Henrique phone to receive call
        // henriquePhone.setLoopback(true);
        final SipCall henriqueCall = henriquePhone.createSipCall();
        henriqueCall.listenForIncomingCall();

        // Initiate a call using Bob
        final SipCall georgeCall = georgePhone.createSipCall();

        georgeCall.initiateOutgoingCall(georgeContact, "sip:1111@" + restcommContact, null, body, "application", "sdp", null, null);
        assertLastOperationSuccess(georgeCall);

        assertTrue(georgeCall.waitOutgoingCallResponse(5 * 1000));

        final int response = georgeCall.getLastReceivedResponse().getStatusCode();
        assertTrue(response == Response.TRYING || response == Response.RINGING);
        if (response == Response.TRYING) {
            assertTrue(georgeCall.waitOutgoingCallResponse(5 * 1000));
            assertEquals(Response.RINGING, georgeCall.getLastReceivedResponse().getStatusCode());
        }

        assertTrue(georgeCall.waitOutgoingCallResponse(5 * 1000));
        assertEquals(Response.OK, georgeCall.getLastReceivedResponse().getStatusCode());
        georgeCall.sendInviteOkAck();
        assertTrue(!(georgeCall.getLastReceivedResponse().getStatusCode() >= 400));

        aliceCall.listenForCancel();

        assertTrue(aliceCall.waitForIncomingCall(3000));
        assertTrue(aliceCall.sendIncomingCallResponse(Response.TRYING, "Trying-Alice", 3600));
        assertTrue(aliceCall.sendIncomingCallResponse(Response.RINGING, "Ringing-Alice", 3600));

        assertTrue(henriqueCall.waitForIncomingCall(3000));
        assertTrue(henriqueCall.sendIncomingCallResponse(Response.TRYING, "Trying-Henrique", 3600));
        assertTrue(henriqueCall.sendIncomingCallResponse(Response.RINGING, "Ringing-Henrique", 3600));

        String receivedBody = new String(henriqueCall.getLastReceivedRequest().getRawContent());
        assertTrue(henriqueCall.sendIncomingCallResponse(Response.OK, "OK-Henrique", 3600, receivedBody, "application", "sdp",
                null, null));
        assertTrue(henriqueCall.waitForAck(5000));

        SipTransaction aliceCancelTransaction = aliceCall.waitForCancel(30000);
        assertNotNull(aliceCancelTransaction);
        aliceCall.respondToCancel(aliceCancelTransaction, 200, "OK-2-Cancel-Alice", 3600);

//        Wait to cancel the other branches
        Thread.sleep(2000);

        JsonObject metrics = MonitoringServiceTool.getInstance().getMetrics(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        int liveCalls = MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        int liveCallsArraySize = MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        assertEquals(2, liveCalls);
        assertEquals(2, liveCallsArraySize);

        georgeCall.listenForDisconnect();

        Thread.sleep(3000);

        // hangup.

        henriqueCall.disconnect();

        assertTrue(georgeCall.waitForDisconnect(3000));
        georgeCall.respondToDisconnect();

        assertTrue(alicePhone.unregister(aliceContact, 3600));

        Thread.sleep(10 * 1000);

        assertTrue(MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken) == 0);
        assertTrue(MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(), adminAccountSid, adminAuthToken) == 0);

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

        metrics = MonitoringServiceTool.getInstance().getMetrics(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
        Map<String, Integer> mgcpResources = MonitoringServiceTool.getInstance().getMgcpResources(metrics);
        int mgcpEndpoints = mgcpResources.get("MgcpEndpoints");
        int mgcpConnections = mgcpResources.get("MgcpConnections");

        assertEquals(0, mgcpEndpoints);
        assertEquals(0, mgcpConnections);
    }

    //Non regression test for https://telestax.atlassian.net/browse/RESTCOMM-585
    @Test
    public synchronized void testDialForkNoAnswerButFromAliceClient() throws InterruptedException, ParseException, MalformedURLException {
        stubFor(get(urlPathEqualTo("/1111"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(dialFork)));

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
        assertTrue(georgeCall.sendIncomingCallResponse(Response.TRYING, "Trying-George", 600));
        assertTrue(georgeCall.sendIncomingCallResponse(Response.RINGING, "Ringing-George", 600));
        assertTrue(aliceCall.waitForIncomingCall(30 * 1000));
        assertTrue(aliceCall.sendIncomingCallResponse(Response.TRYING, "Trying-Alice", 3600));
        assertTrue(aliceCall.sendIncomingCallResponse(Response.RINGING, "Ringing-Alice", 3600));
        assertTrue(henriqueCall.waitForIncomingCall(30 * 1000));
        assertTrue(henriqueCall.sendIncomingCallResponse(Response.TRYING, "Trying-Henrique", 600));
        assertTrue(henriqueCall.sendIncomingCallResponse(Response.RINGING, "Ringing-Henrique", 600));

        String receivedBody = new String(aliceCall.getLastReceivedRequest().getRawContent());

        assertTrue(aliceCall.sendIncomingCallResponse(Response.OK, "OK-Alice", 3600, receivedBody, "application", "sdp",
                null, null));
        assertTrue(aliceCall.waitForAck(50 * 1000));

        assertTrue(henriqueCall.listenForCancel());
        assertTrue(georgeCall.listenForCancel());

        SipTransaction georgeCancelTransaction = georgeCall.waitForCancel(30 * 1000);
        SipTransaction henriqueCancelTransaction = henriqueCall.waitForCancel(30 * 1000);
        assertNotNull(georgeCancelTransaction);
        assertNotNull(henriqueCancelTransaction);
        henriqueCall.respondToCancel(henriqueCancelTransaction, 200, "OK - Henrique", 600);
        georgeCall.respondToCancel(georgeCancelTransaction, 200, "OK - George", 600);

        Thread.sleep(2000);

        JsonObject metrics = MonitoringServiceTool.getInstance().getMetrics(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
        assertNotNull(metrics);
        int liveCalls = metrics.getAsJsonObject("Metrics").get("LiveCalls").getAsInt();
        logger.info("LiveCalls: "+liveCalls);
        int liveCallsArraySize = metrics.getAsJsonArray("LiveCallDetails").size();
        logger.info("LiveCallsArraySize: "+liveCallsArraySize);
        assertTrue(liveCalls == 2);
        assertEquals(2, liveCallsArraySize);

        aliceCall.listenForDisconnect();

        Thread.sleep(8000);

        // hangup.

        bobCall.disconnect();
        assertTrue(bobCall.waitForAnswer(5000));

        assertTrue(aliceCall.waitForDisconnect(30 * 1000));

        assertTrue(alicePhone.unregister(aliceContact, 3600));

        Thread.sleep(10 * 1000);

        logger.info("About to check the Requests");
        List<LoggedRequest> requests = findAll(getRequestedFor(urlPathMatching("/1111")));
        assertTrue(requests.size() == 1);
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
        String status = jsonObj.get("status").getAsString();
        System.out.println("%%%%Status : "+status);
        assertTrue(jsonObj.get("status").getAsString().equalsIgnoreCase("completed"));
        assertTrue(MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken) == 0);
        assertTrue(MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(), adminAccountSid, adminAuthToken) == 0);

        metrics = MonitoringServiceTool.getInstance().getMetrics(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
        Map<String, Integer> mgcpResources = MonitoringServiceTool.getInstance().getMgcpResources(metrics);
        int mgcpEndpoints = mgcpResources.get("MgcpEndpoints");
        int mgcpConnections = mgcpResources.get("MgcpConnections");

        assertEquals(0, mgcpEndpoints);
        assertEquals(0, mgcpConnections);
    }

    // Non regression test for https://github.com/RestComm/Restcomm-Connect/issues/1972
    @Test
    @Category(FeatureAltTests.class)
    public synchronized void testDialForkToNotRegisteredClientDialSipFirst() throws InterruptedException, ParseException, MalformedURLException {
        stubFor(get(urlPathEqualTo("/1111"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(dialForkToNotRegisteredClientSipFirst)));


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

        assertTrue(henriqueCall.waitForIncomingCall(30 * 1000));
        assertTrue(henriqueCall.sendIncomingCallResponse(Response.TRYING, "Trying-Henrique", 3600));
        assertTrue(henriqueCall.sendIncomingCallResponse(Response.RINGING, "Ringing-Henrique", 3600));


        String receivedBody = new String(henriqueCall.getLastReceivedRequest().getRawContent());
        assertTrue(henriqueCall.sendIncomingCallResponse(Response.OK, "OK-Henrique", 3600, receivedBody, "application", "sdp",
                null, null));
        assertTrue(henriqueCall.waitForAck(50 * 1000));

        //Wait to cancel the other branches
        Thread.sleep(2000);

        JsonObject metrics = MonitoringServiceTool.getInstance().getMetrics(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        int liveCalls = MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        int liveCallsArraySize = MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        assertTrue(liveCalls == 2);
        assertEquals(2, liveCallsArraySize);

        henriqueCall.listenForDisconnect();

        Thread.sleep(8000);

        // hangup.

        bobCall.disconnect();

        assertTrue(henriqueCall.waitForDisconnect(30 * 1000));

        assertTrue(alicePhone.unregister(aliceContact, 3600));

        Thread.sleep(10 * 1000);

        assertTrue(MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken) == 0);
        assertTrue(MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(), adminAccountSid, adminAuthToken) == 0);

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

        metrics = MonitoringServiceTool.getInstance().getMetrics(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
        Map<String, Integer> mgcpResources = MonitoringServiceTool.getInstance().getMgcpResources(metrics);
        int mgcpEndpoints = mgcpResources.get("MgcpEndpoints");
        int mgcpConnections = mgcpResources.get("MgcpConnections");

        assertEquals(0, mgcpEndpoints);
        assertEquals(0, mgcpConnections);
    }

    //Non regression test for https://github.com/RestComm/Restcomm-Connect/issues/1972
    //When Dial Client is first its working fine
    @Test
    @Category(FeatureAltTests.class)
    public synchronized void testDialForkToNotRegisteredClientDialClientFirst() throws InterruptedException, ParseException, MalformedURLException {
        stubFor(get(urlPathEqualTo("/1111"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(dialForkToNotRegisteredClientDialClientFirst)));


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

        assertTrue(henriqueCall.waitForIncomingCall(30 * 1000));
        assertTrue(henriqueCall.sendIncomingCallResponse(Response.TRYING, "Trying-Henrique", 3600));
        assertTrue(henriqueCall.sendIncomingCallResponse(Response.RINGING, "Ringing-Henrique", 3600));


        String receivedBody = new String(henriqueCall.getLastReceivedRequest().getRawContent());
        assertTrue(henriqueCall.sendIncomingCallResponse(Response.OK, "OK-Henrique", 3600, receivedBody, "application", "sdp",
                null, null));
        assertTrue(henriqueCall.waitForAck(50 * 1000));

        //Wait to cancel the other branches
        Thread.sleep(2000);

        JsonObject metrics = MonitoringServiceTool.getInstance().getMetrics(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        int liveCalls = MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        int liveCallsArraySize = MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        assertTrue(liveCalls == 2);
        assertEquals(2, liveCallsArraySize);

        henriqueCall.listenForDisconnect();

        Thread.sleep(8000);

        // hangup.

        bobCall.disconnect();

        assertTrue(henriqueCall.waitForDisconnect(30 * 1000));

        assertTrue(alicePhone.unregister(aliceContact, 3600));

        Thread.sleep(10 * 1000);

        assertTrue(MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken) == 0);
        assertTrue(MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(), adminAccountSid, adminAuthToken) == 0);

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

        metrics = MonitoringServiceTool.getInstance().getMetrics(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
        Map<String, Integer> mgcpResources = MonitoringServiceTool.getInstance().getMgcpResources(metrics);
        int mgcpEndpoints = mgcpResources.get("MgcpEndpoints");
        int mgcpConnections = mgcpResources.get("MgcpConnections");

        assertEquals(0, mgcpEndpoints);
        assertEquals(0, mgcpConnections);
    }

    @Test
    public synchronized void testDialForkWithBusy() throws InterruptedException, ParseException, MalformedURLException {
        stubFor(get(urlPathEqualTo("/1111"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(dialFork)));

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

        assertTrue(aliceCall.waitForIncomingCall(30 * 1000));
        assertTrue(aliceCall.sendIncomingCallResponse(100, "Trying-Alice", 600));

        assertTrue(henriqueCall.waitForIncomingCall(30 * 1000));
        assertTrue(henriqueCall.sendIncomingCallResponse(Response.TRYING, "Trying-Henrique", 3600));
        assertTrue(henriqueCall.sendIncomingCallResponse(Response.RINGING, "Ringing-Henrique", 3600));

        assertTrue(georgeCall.sendIncomingCallResponse(486, "Busy Here-George", 3600));
        assertTrue(georgeCall.waitForAck(50 * 1000));

        assertTrue(aliceCall.sendIncomingCallResponse(486, "Busy Here-Alice", 3600));
        assertTrue(aliceCall.waitForAck(50 * 1000));
        assertTrue(alicePhone.unregister(aliceContact, 3600));

        String receivedBody = new String(henriqueCall.getLastReceivedRequest().getRawContent());
        assertTrue(henriqueCall.sendIncomingCallResponse(Response.OK, "OK-Henrique", 3600, receivedBody, "application", "sdp",
                null, null));
        assertTrue(henriqueCall.waitForAck(50 * 1000));

        Thread.sleep(2000);

        JsonObject metrics = MonitoringServiceTool.getInstance().getMetrics(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        int liveCalls = MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        int liveCallsArraySize = MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        logger.info("&&&&& LiveCalls: "+liveCalls);
        logger.info("&&&&& LiveCallsArraySize: "+liveCallsArraySize);
        assertTrue(liveCalls == 2);
        assertEquals(2, liveCallsArraySize);

        henriqueCall.listenForDisconnect();

        Thread.sleep(8000);

        // hangup.

        bobCall.disconnect();

        assertTrue(henriqueCall.waitForDisconnect(30 * 1000));

        assertTrue(alicePhone.unregister(aliceContact, 3600));

        Thread.sleep(10 * 1000);

        assertTrue(MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken) == 0);
        assertTrue(MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(), adminAccountSid, adminAuthToken) == 0);

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
        logger.info("%%%% CallSID: "+callSid+" Status : "+jsonObj.get("status").getAsString());
        assertTrue(jsonObj.get("status").getAsString().equalsIgnoreCase("completed"));
        assertTrue(MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken) == 0);
        assertTrue(MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(), adminAccountSid, adminAuthToken) == 0);

        metrics = MonitoringServiceTool.getInstance().getMetrics(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
        Map<String, Integer> mgcpResources = MonitoringServiceTool.getInstance().getMgcpResources(metrics);
        int mgcpEndpoints = mgcpResources.get("MgcpEndpoints");
        int mgcpConnections = mgcpResources.get("MgcpConnections");

        assertEquals(0, mgcpEndpoints);
        assertEquals(0, mgcpConnections);
    }

    @Test
    public synchronized void testDialForkWithDecline() throws InterruptedException, ParseException, MalformedURLException {
        stubFor(get(urlPathEqualTo("/1111"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(dialFork)));

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

        assertTrue(aliceCall.waitForIncomingCall(30 * 1000));
        assertTrue(aliceCall.sendIncomingCallResponse(100, "Trying-Alice", 600));

        assertTrue(henriqueCall.waitForIncomingCall(30 * 1000));
        assertTrue(henriqueCall.sendIncomingCallResponse(Response.TRYING, "Trying-Henrique", 3600));
        assertTrue(henriqueCall.sendIncomingCallResponse(Response.RINGING, "Ringing-Henrique", 3600));

        //int DECLINE = 603;
        assertTrue(georgeCall.sendIncomingCallResponse(Response.DECLINE, "Busy Here-George", 3600));
        assertTrue(georgeCall.waitForAck(50 * 1000));

        assertTrue(aliceCall.sendIncomingCallResponse(Response.DECLINE, "Busy Here-Alice", 3600));
        assertTrue(aliceCall.waitForAck(50 * 1000));
        assertTrue(alicePhone.unregister(aliceContact, 3600));

        String receivedBody = new String(henriqueCall.getLastReceivedRequest().getRawContent());
        assertTrue(henriqueCall.sendIncomingCallResponse(Response.OK, "OK-Henrique", 3600, receivedBody, "application", "sdp",
                null, null));
        assertTrue(henriqueCall.waitForAck(50 * 1000));

        int liveCalls = MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        int liveCallsArraySize = MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        assertTrue(liveCalls == 2);
        assertEquals(2, liveCallsArraySize);

        henriqueCall.listenForDisconnect();

        Thread.sleep(8000);

        // hangup.

        bobCall.disconnect();

        assertTrue(henriqueCall.waitForDisconnect(30 * 1000));

        assertTrue(alicePhone.unregister(aliceContact, 3600));

        Thread.sleep(10 * 1000);

        assertTrue(MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken) == 0);
        assertTrue(MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(), adminAccountSid, adminAuthToken) == 0);

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
        logger.info("%%%% CallSID: "+callSid+" Status : "+jsonObj.get("status").getAsString());
        assertTrue(jsonObj.get("status").getAsString().equalsIgnoreCase("completed"));
        assertTrue(MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken) == 0);
        assertTrue(MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(), adminAccountSid, adminAuthToken) == 0);

        JsonObject metrics = MonitoringServiceTool.getInstance().getMetrics(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
        Map<String, Integer> mgcpResources = MonitoringServiceTool.getInstance().getMgcpResources(metrics);
        int mgcpEndpoints = mgcpResources.get("MgcpEndpoints");
        int mgcpConnections = mgcpResources.get("MgcpConnections");

        assertEquals(0, mgcpEndpoints);
        assertEquals(0, mgcpConnections);
    }

    @Test
    public synchronized void testDialForkBobSendsBye() throws InterruptedException, ParseException, MalformedURLException {
        stubFor(get(urlPathEqualTo("/1111"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(dialFork)));

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

        georgeCall.listenForCancel();
        aliceCall.listenForCancel();
        henriqueCall.listenForCancel();

        Thread.sleep(1000);
        bobCall.disconnect();

        SipTransaction georgeCancelTransaction = georgeCall.waitForCancel(30000);
        SipTransaction aliceCancelTransaction = aliceCall.waitForCancel(30000);
        SipTransaction henriqueCancelTransaction = henriqueCall.waitForCancel(30000);

        assertNotNull(georgeCancelTransaction);
        assertNotNull(aliceCancelTransaction);
        assertNotNull(henriqueCancelTransaction);

        georgeCall.respondToCancel(georgeCancelTransaction, 200, "OK-2-Cancel-George", 3600);
        aliceCall.respondToCancel(aliceCancelTransaction, 200, "OK-2-Cancel-Alice", 3600);
        henriqueCall.respondToCancel(henriqueCancelTransaction, 200, "OK-2-Cancel-Henrique", 3600);

        assertTrue(alicePhone.unregister(aliceContact, 3600));

        Thread.sleep(10000);

        JsonObject metrics = MonitoringServiceTool.getInstance().getMetrics(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        int liveCalls = MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        int liveCallsArraySize = MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        logger.info("&&&&& LiveCalls: "+liveCalls);
        logger.info("&&&&& LiveCallsArraySize: "+liveCallsArraySize);
        assertEquals(0, liveCalls);
        assertEquals(0, liveCallsArraySize);

        Thread.sleep(10 * 1000);

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
        logger.info("Status for call: "+callSid+" : "+jsonObj.get("status").getAsString());
        assertTrue(jsonObj.get("status").getAsString().equalsIgnoreCase("completed"));
        assertTrue(MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken) == 0);
        assertTrue(MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(), adminAccountSid, adminAuthToken) == 0);

        metrics = MonitoringServiceTool.getInstance().getMetrics(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
        Map<String, Integer> mgcpResources = MonitoringServiceTool.getInstance().getMgcpResources(metrics);
        int mgcpEndpoints = mgcpResources.get("MgcpEndpoints");
        int mgcpConnections = mgcpResources.get("MgcpConnections");

        assertEquals(0, mgcpEndpoints);
        assertEquals(0, mgcpConnections);
    }


    @Test
    public synchronized void testDialForkNoAnswer() throws InterruptedException, ParseException, MalformedURLException {

        stubFor(get(urlPathEqualTo("/1111"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(dialForkWithTimeout)));

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

        //Prepare Fotini phone to receive a call
        final SipCall fotiniCall = fotiniPhone.createSipCall();
        fotiniCall.listenForIncomingCall();

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

        //No one will answer the call and Bob will receive disconnect
        assertTrue(georgeCall.listenForCancel());
        assertTrue(aliceCall.listenForCancel());
        assertTrue(henriqueCall.listenForCancel());

        assertTrue(bobCall.listenForDisconnect());

        SipTransaction henriqueCancelTransaction = henriqueCall.waitForCancel(50 * 1000);
        assertNotNull(henriqueCancelTransaction);
        henriqueCall.respondToCancel(henriqueCancelTransaction, 200, "OK - Henrique", 600);

        SipTransaction aliceCancelTransaction = aliceCall.waitForCancel(50 * 1000);
        assertNotNull(aliceCancelTransaction);
        aliceCall.respondToCancel(aliceCancelTransaction, 200, "OK - Alice", 600);

        SipTransaction georgeCancelTransaction = georgeCall.waitForCancel(50 * 1000);
        assertNotNull(georgeCancelTransaction);
        georgeCall.respondToCancel(georgeCancelTransaction, 200, "OK - George", 600);


        assertTrue(alicePhone.unregister(aliceContact, 3600));

        assertTrue(bobCall.waitForDisconnect(50 * 1000));
        assertTrue(bobCall.respondToDisconnect());

        Thread.sleep(1000);

        JsonObject metrics = MonitoringServiceTool.getInstance().getMetrics(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        int liveCalls = MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        int liveCallsArraySize = MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        logger.info("&&&&& LiveCalls: "+liveCalls);
        logger.info("&&&&& LiveCallsArraySize: "+liveCallsArraySize);
        assertEquals(0, liveCalls);
        assertEquals(0, liveCallsArraySize);

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
        logger.info("Status for call: "+callSid+" : "+jsonObj.get("status").getAsString());
        int callDuration = jsonObj.get("duration").getAsInt();
        assertEquals(timeout, callDuration, 1.5);
        assertTrue(jsonObj.get("status").getAsString().equalsIgnoreCase("completed"));
        assertTrue(MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken) == 0);
        assertTrue(MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(), adminAccountSid, adminAuthToken) == 0);

        metrics = MonitoringServiceTool.getInstance().getMetrics(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
        Map<String, Integer> mgcpResources = MonitoringServiceTool.getInstance().getMgcpResources(metrics);
        int mgcpEndpoints = mgcpResources.get("MgcpEndpoints");
        int mgcpConnections = mgcpResources.get("MgcpConnections");

        assertEquals(0, mgcpEndpoints);
        assertEquals(0, mgcpConnections);
    }

    String nonValidSDP = "v=0\n" +
            "o=user1 53655765 2353687637 IN IP4 127.0.0.1\n" +
            "s=- NonValidSDP\n" +
            "c=IN IP4 127.0.0.1\n" +
            "t=0 0\n" +
            "m=audio 6000 RTP/AVP 0\n" +
            "a=rtpmap:0 PCMU/8000\n";

    @Test @Ignore //Passes only when run individually. Doesn't pass when run with the rest of the tests
    @Category(FeatureAltTests.class)
    public synchronized void testDialForkWithReInviteBeforeDialForkStarts_CancelCall() throws InterruptedException, ParseException, MalformedURLException {

        stubFor(get(urlPathEqualTo("/1111"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(dialForkWithTimeout)));

        JsonObject metrics = MonitoringServiceTool.getInstance().getMetrics(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        initialTotalCallSinceUptime = metrics.getAsJsonObject("Metrics").get("TotalCallsSinceUptime").getAsInt();
        initialOutgoingCallsSinceUptime = metrics.getAsJsonObject("Metrics").get("OutgoingCallsSinceUptime").getAsInt();

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

        //Prepare Fotini phone to receive a call
        final SipCall fotiniCall = fotiniPhone.createSipCall();
        fotiniCall.listenForIncomingCall();

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

        //Send re-Invite here using non-valid SDP
        SipTransaction sipTransaction = bobCall.sendReinvite(bobContact, "Bob", nonValidSDP, "application", "sdp");
        assertNotNull(sipTransaction);
        assertLastOperationSuccess(bobCall);
        //For a reason, container sends 100 straight after 569 and sipunit never receives 569.
//        assertTrue(bobCall.waitReinviteResponse(sipTransaction, 50 * 1000));
//        assertEquals(569, bobCall.getLastReceivedResponse().getStatusCode());

//        bobCall.listenForDisconnect();
//        assertTrue(bobCall.waitForDisconnect(50 * 1000));

//        assertTrue(georgeCall.waitForIncomingCall(30 * 1000));
//        assertTrue(georgeCall.sendIncomingCallResponse(100, "Trying-George", 600));
//        assertTrue(georgeCall.sendIncomingCallResponse(180, "Ringing-George", 600));
//        assertTrue(aliceCall.waitForIncomingCall(30 * 1000));
//        assertTrue(aliceCall.sendIncomingCallResponse(100, "Trying-Alice", 600));
//        assertTrue(aliceCall.sendIncomingCallResponse(180, "Ringing-Alice", 600));
//        assertTrue(henriqueCall.waitForIncomingCall(30 * 1000));
//        assertTrue(henriqueCall.sendIncomingCallResponse(100, "Trying-Henrique", 600));
//        assertTrue(henriqueCall.sendIncomingCallResponse(Response.RINGING, "Ringing-Henrique", 3600));
//
//        //No one will answer the call and Bob will receive disconnect
//        assertTrue(georgeCall.listenForCancel());
//        assertTrue(aliceCall.listenForCancel());
//        assertTrue(henriqueCall.listenForCancel());
//
//        assertTrue(bobCall.listenForDisconnect());
//
//        SipTransaction henriqueCancelTransaction = henriqueCall.waitForCancel(50 * 1000);
//        assertNotNull(henriqueCancelTransaction);
//        henriqueCall.respondToCancel(henriqueCancelTransaction, 200, "OK - Henrique", 600);
//
//        SipTransaction aliceCancelTransaction = aliceCall.waitForCancel(50 * 1000);
//        assertNotNull(aliceCancelTransaction);
//        aliceCall.respondToCancel(aliceCancelTransaction, 200, "OK - Alice", 600);
//
//        SipTransaction georgeCancelTransaction = georgeCall.waitForCancel(50 * 1000);
//        assertNotNull(georgeCancelTransaction);
//        georgeCall.respondToCancel(georgeCancelTransaction, 200, "OK - George", 600);

        assertTrue(alicePhone.unregister(aliceContact, 3600));

//        assertTrue(bobCall.waitForDisconnect(50 * 1000));
//        assertTrue(bobCall.respondToDisconnect());

        Thread.sleep(1000);

        metrics = MonitoringServiceTool.getInstance().getMetrics(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        int liveCalls = MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        int liveCallsArraySize = MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        int totalCallSinceUptime = metrics.getAsJsonObject("Metrics").get("TotalCallsSinceUptime").getAsInt();
        int outgoingCallsSinceUptime = metrics.getAsJsonObject("Metrics").get("OutgoingCallsSinceUptime").getAsInt();

        logger.info("&&&&& LiveCalls: "+liveCalls);
        logger.info("&&&&& LiveCallsArraySize: "+liveCallsArraySize);
        assertEquals(0, liveCalls);
        assertEquals(0, liveCallsArraySize);

        assertEquals(initialTotalCallSinceUptime+1, totalCallSinceUptime);
        assertEquals(initialOutgoingCallsSinceUptime, outgoingCallsSinceUptime);

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
        logger.info("Status for call: "+callSid+" : "+jsonObj.get("status").getAsString());
        assertTrue(jsonObj.get("status").getAsString().equalsIgnoreCase("failed"));
        assertTrue(MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken) == 0);
        assertTrue(MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(), adminAccountSid, adminAuthToken) == 0);

        metrics = MonitoringServiceTool.getInstance().getMetrics(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
        Map<String, Integer> mgcpResources = MonitoringServiceTool.getInstance().getMgcpResources(metrics);
        int mgcpEndpoints = mgcpResources.get("MgcpEndpoints");
        int mgcpConnections = mgcpResources.get("MgcpConnections");

        assertEquals(0, mgcpEndpoints);
        assertEquals(0, mgcpConnections);
    }

    private String dialClientAlice = "<Response><Dial timeout=\"2\"><Client>alice</Client></Dial></Response>";

    @Test //Passes only when run individually. Doesn't pass when run with the rest of the tests. It not only fails, it messes up the metrics and cause all following tests to fail. so ignoring it unless we fix it
    @Ignore
    @Category(FeatureAltTests.class)
    public synchronized void testDialForkWithReInviteAfterDialStarts_CancelCall() throws InterruptedException, ParseException, MalformedURLException {

        stubFor(get(urlPathEqualTo("/1111"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(dialClientAlice)));

        JsonObject metrics = MonitoringServiceTool.getInstance().getMetrics(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        initialTotalCallSinceUptime = metrics.getAsJsonObject("Metrics").get("TotalCallsSinceUptime").getAsInt();
        initialOutgoingCallsSinceUptime = metrics.getAsJsonObject("Metrics").get("OutgoingCallsSinceUptime").getAsInt();

        // Register Alice
        SipURI uri = aliceSipStack.getAddressFactory().createSipURI(null, restcommContact);
        assertTrue(alicePhone.register(uri, "alice", "1234", aliceContact, 3600, 3600));

        // Prepare Alice to receive call
        final SipCall aliceCall = alicePhone.createSipCall();
        aliceCall.listenForIncomingCall();


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


        assertTrue(aliceCall.waitForIncomingCall(30 * 1000));
        assertTrue(aliceCall.sendIncomingCallResponse(100, "Trying-Alice", 600));
        assertTrue(aliceCall.sendIncomingCallResponse(180, "Ringing-Alice", 600));
        assertTrue(aliceCall.listenForCancel());


        //Send re-Invite here using non-valid SDP
        SipTransaction sipTransaction = bobCall.sendReinvite(bobContact, "Bob", nonValidSDP, "application", "sdp");
        assertNotNull(sipTransaction);
        assertLastOperationSuccess(bobCall);
        //For a reason, container sends 100 straight after 569 and sipunit never receives 569.
//        assertTrue(bobCall.waitReinviteResponse(sipTransaction, 50 * 1000));
//        assertEquals(569, bobCall.getLastReceivedResponse().getStatusCode());

        SipTransaction aliceCancelTransaction = aliceCall.waitForCancel(50 * 1000);

        assertNotNull(aliceCancelTransaction);
        aliceCall.respondToCancel(aliceCancelTransaction, 200, "OK - Alice", 600);

//        bobCall.listenForDisconnect();
//        assertTrue(bobCall.waitForDisconnect(50 * 1000));


        assertTrue(alicePhone.unregister(aliceContact, 3600));

//        assertTrue(bobCall.waitForDisconnect(50 * 1000));
//        assertTrue(bobCall.respondToDisconnect());

        Thread.sleep(1000);

        metrics = MonitoringServiceTool.getInstance().getMetrics(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        int liveCalls = MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        int liveCallsArraySize = MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        int totalCallSinceUptime = metrics.getAsJsonObject("Metrics").get("TotalCallsSinceUptime").getAsInt();
        int outgoingCallsSinceUptime = metrics.getAsJsonObject("Metrics").get("OutgoingCallsSinceUptime").getAsInt();

        logger.info("&&&&& LiveCalls: "+liveCalls);
        logger.info("&&&&& LiveCallsArraySize: "+liveCallsArraySize);
        assertEquals(0, liveCalls);
        assertEquals(0, liveCallsArraySize);

        assertEquals(initialTotalCallSinceUptime+2, totalCallSinceUptime);
        assertEquals(initialOutgoingCallsSinceUptime+1, outgoingCallsSinceUptime);

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
        logger.info("Status for call: "+callSid+" : "+jsonObj.get("status").getAsString());
        assertTrue(jsonObj.get("status").getAsString().equalsIgnoreCase("failed"));
        assertTrue(MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken) == 0);
        assertTrue(MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(), adminAccountSid, adminAuthToken) == 0);

        metrics = MonitoringServiceTool.getInstance().getMetrics(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
        Map<String, Integer> mgcpResources = MonitoringServiceTool.getInstance().getMgcpResources(metrics);
        int mgcpEndpoints = mgcpResources.get("MgcpEndpoints");
        int mgcpConnections = mgcpResources.get("MgcpConnections");

        assertEquals(0, mgcpEndpoints);
        assertEquals(0, mgcpConnections);
    }

    @Test
    @Category(FeatureAltTests.class)
    public synchronized void testDialForkNoAnswerWith183FromAlice() throws InterruptedException, ParseException, MalformedURLException {

        stubFor(get(urlPathEqualTo("/1111"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(dialForkWithTimeout)));

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

        //Prepare Fotini phone to receive a call
        final SipCall fotiniCall = fotiniPhone.createSipCall();
        fotiniCall.listenForIncomingCall();

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
        assertTrue(aliceCall.sendIncomingCallResponse(183, "SessionProgress-Alice", 600));
        assertTrue(henriqueCall.waitForIncomingCall(30 * 1000));
        assertTrue(henriqueCall.sendIncomingCallResponse(100, "Trying-Henrique", 600));
        assertTrue(henriqueCall.sendIncomingCallResponse(Response.RINGING, "Ringing-Henrique", 3600));

        //No one will answer the call and Bob will receive disconnect
        assertTrue(georgeCall.listenForCancel());
        assertTrue(aliceCall.listenForCancel());
        assertTrue(henriqueCall.listenForCancel());

        assertTrue(bobCall.listenForDisconnect());

        SipTransaction henriqueCancelTransaction = henriqueCall.waitForCancel(50 * 1000);
        assertNotNull(henriqueCancelTransaction);
        henriqueCall.respondToCancel(henriqueCancelTransaction, 200, "OK - Henrique", 600);

        SipTransaction aliceCancelTransaction = aliceCall.waitForCancel(50 * 1000);
        assertNotNull(aliceCancelTransaction);
        aliceCall.respondToCancel(aliceCancelTransaction, 200, "OK - Alice", 600);

        SipTransaction georgeCancelTransaction = georgeCall.waitForCancel(50 * 1000);
        assertNotNull(georgeCancelTransaction);
        georgeCall.respondToCancel(georgeCancelTransaction, 200, "OK - George", 600);


        assertTrue(alicePhone.unregister(aliceContact, 3600));

        assertTrue(bobCall.waitForDisconnect(50 * 1000));
        assertTrue(bobCall.respondToDisconnect());

        Thread.sleep(1000);

        JsonObject metrics = MonitoringServiceTool.getInstance().getMetrics(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        int liveCalls = MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        int liveCallsArraySize = MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        logger.info("&&&&& LiveCalls: "+liveCalls);
        logger.info("&&&&& LiveCallsArraySize: "+liveCallsArraySize);
        assertEquals(0, liveCalls);
        assertEquals(0, liveCallsArraySize);

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
        logger.info("Status for call: "+callSid+" : "+jsonObj.get("status").getAsString());
        assertTrue(jsonObj.get("status").getAsString().equalsIgnoreCase("completed"));
        assertEquals(0, MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken));
        assertEquals(0, MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(), adminAccountSid, adminAuthToken));

        metrics = MonitoringServiceTool.getInstance().getMetrics(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
        Map<String, Integer> mgcpResources = MonitoringServiceTool.getInstance().getMgcpResources(metrics);
        int mgcpEndpoints = mgcpResources.get("MgcpEndpoints");
        int mgcpConnections = mgcpResources.get("MgcpConnections");

        assertEquals(0, mgcpEndpoints);
        assertEquals(0, mgcpConnections);
    }

    class AutoAnswer implements Runnable {
        SipCall call;

        public AutoAnswer(SipCall call) {
            this.call = call;
        }



        public void run() {
            try {
                call.waitForIncomingCall(15000);
                call.sendIncomingCallResponse(Response.TRYING, "Trying", 3600);
                call.sendIncomingCallResponse(Response.RINGING, "Ringing", 3600);
                String receivedBody = new String(call.getLastReceivedRequest().getRawContent());
                //simulate answer time
                Thread.sleep(3000);
                call.sendIncomingCallResponse(Response.OK, "OK", 3600, receivedBody, "application", "sdp",
                        null, null);
                call.waitForAck(15000);
                call.listenForDisconnect();
                call.waitForDisconnect(15000);
            } catch (InterruptedException ex) {
                java.util.logging.Logger.getLogger(DialForkTest.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    private void assertNoMGCPResources() {
        JsonObject metrics = MonitoringServiceTool.getInstance().getMetrics(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
        Map<String, Integer> mgcpResources = MonitoringServiceTool.getInstance().getMgcpResources(metrics);
        int mgcpEndpoints = mgcpResources.get("MgcpEndpoints");
        int mgcpConnections = mgcpResources.get("MgcpConnections");

        assertEquals(0, mgcpEndpoints);
        assertEquals(0, mgcpConnections);
    }


    @Test
    @Category({FeatureExpTests.class, UnstableTests.class})
    public synchronized void testDialForkMultipleAnswer() throws InterruptedException, ParseException, MalformedURLException {
        List<AutoAnswer> autoAnswers = new ArrayList<AutoAnswer>();
        stubFor(get(urlPathEqualTo("/1111"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(dialFork)));

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

        assertTrue(bobCall.waitForAnswer(10000));
        bobCall.sendInviteOkAck();
        assertTrue(!(bobCall.getLastReceivedResponse().getStatusCode() >= 400));

        aliceCall.waitForIncomingCall(15000);
        aliceCall.sendIncomingCallResponse(Response.TRYING, "Trying", 3600);
        aliceCall.sendIncomingCallResponse(Response.RINGING, "Ringing", 3600);

        henriqueCall.waitForIncomingCall(15000);
        henriqueCall.sendIncomingCallResponse(Response.TRYING, "Trying", 3600);
        henriqueCall.sendIncomingCallResponse(Response.RINGING, "Ringing", 3600);

        georgeCall.waitForIncomingCall(15000);
        georgeCall.sendIncomingCallResponse(Response.TRYING, "Trying", 3600);
        georgeCall.sendIncomingCallResponse(Response.RINGING, "Ringing", 3600);

        Thread.sleep(3000);
        String receivedBody = new String(aliceCall.getLastReceivedRequest().getRawContent());
        aliceCall.sendIncomingCallResponse(Response.OK, "OK", 3600, receivedBody, "application", "sdp",
                null, null);
        henriqueCall.sendIncomingCallResponse(Response.OK, "OK", 3600, receivedBody, "application", "sdp",
                null, null);
        georgeCall.sendIncomingCallResponse(Response.OK, "OK", 3600, receivedBody, "application", "sdp",
                null, null);

        assertTrue(aliceCall.waitForAck(20000));
        aliceCall.listenForDisconnect();

        assertTrue(henriqueCall.waitForAck(20000));
        henriqueCall.listenForDisconnect();

        assertTrue(georgeCall.waitForAck(20000));
        georgeCall.listenForDisconnect();

        assertTrue(henriqueCall.waitForDisconnect(15000));
        henriqueCall.respondToDisconnect();

        assertTrue(georgeCall.waitForDisconnect(15000));
        georgeCall.respondToDisconnect();


        //TODO assert just one call get establlished, rest are either cancel/bye

        Thread.sleep(5000);

        assertEquals(1, MonitoringServiceTool.getInstance().getLiveIncomingCallStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken));
        JsonObject liveCalls = MonitoringServiceTool.getInstance().getLiveCalls(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        logger.info("&&&&& liveCalls: "+liveCalls);
        JsonArray liveCallDetails = liveCalls.getAsJsonArray("LiveCallDetails");
        assertEquals(1, MonitoringServiceTool.getInstance().getLiveOutgoingCallStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken));
        assertEquals(2,liveCallDetails.size());

        bobCall.disconnect();
        assertTrue(aliceCall.waitForDisconnect(5000));
        aliceCall.respondToDisconnect();

        Thread.sleep(1000);
        assertNoMGCPResources();
    }

    @Test
    @Category(FeatureExpTests.class)
    public synchronized void testDialForkNoAnswerAndNoResponseFromAlice() throws InterruptedException, ParseException, MalformedURLException {

        stubFor(get(urlPathEqualTo("/1111"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(dialForkWithTimeout)));

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

        //Prepare Fotini phone to receive a call
        final SipCall fotiniCall = fotiniPhone.createSipCall();
        fotiniCall.listenForIncomingCall();

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
        aliceCall.disposeNoBye();

        //Alice will send no response at all
//        assertTrue(aliceCall.sendIncomingCallResponse(100, "Trying-Alice", 600));
//        assertTrue(aliceCall.sendIncomingCallResponse(180, "Ringing-Alice", 600));

        assertTrue(henriqueCall.waitForIncomingCall(30 * 1000));
        assertTrue(henriqueCall.sendIncomingCallResponse(100, "Trying-Henrique", 600));
        assertTrue(henriqueCall.sendIncomingCallResponse(Response.RINGING, "Ringing-Henrique", 3600));

        //No one will answer the call and Bob will receive disconnect
        assertTrue(georgeCall.listenForCancel());
        assertTrue(henriqueCall.listenForCancel());

        assertTrue(bobCall.listenForDisconnect());

        SipTransaction henriqueCancelTransaction = henriqueCall.waitForCancel(50 * 1000);
        assertNotNull(henriqueCancelTransaction);
        henriqueCall.respondToCancel(henriqueCancelTransaction, 200, "OK - Henrique", 600);

        SipTransaction georgeCancelTransaction = georgeCall.waitForCancel(50 * 1000);
        assertNotNull(georgeCancelTransaction);
        georgeCall.respondToCancel(georgeCancelTransaction, 200, "OK - George", 600);


        assertTrue(bobCall.waitForDisconnect(50 * 1000));
        assertTrue(bobCall.respondToDisconnect());

        Thread.sleep(1000);

        JsonObject metrics = MonitoringServiceTool.getInstance().getMetrics(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        int liveCalls = MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        int liveCallsArraySize = MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        logger.info("&&&&& LiveCalls: "+liveCalls);
        logger.info("&&&&& LiveCallsArraySize: "+liveCallsArraySize);
        assertEquals(0, liveCalls);
        assertEquals(0, liveCallsArraySize);

        Thread.sleep(60000);

        assertTrue(alicePhone.register(uri, "alice", "1234", aliceContact, 3600, 3600));

        Thread.sleep(5000);

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
        logger.info("Status for call: "+callSid+" : "+jsonObj.get("status").getAsString());
        assertTrue(jsonObj.get("status").getAsString().equalsIgnoreCase("completed"));
        assertTrue(MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken) == 0);
        assertTrue(MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(), adminAccountSid, adminAuthToken) == 0);

        metrics = MonitoringServiceTool.getInstance().getMetrics(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
        Map<String, Integer> mgcpResources = MonitoringServiceTool.getInstance().getMgcpResources(metrics);
        int mgcpEndpoints = mgcpResources.get("MgcpEndpoints");
        int mgcpConnections = mgcpResources.get("MgcpConnections");

        assertEquals(0, mgcpEndpoints);
        assertEquals(0, mgcpConnections);
    }


    @Test
    @Category(FeatureExpTests.class)
    public synchronized void testDialForkNoAnswerWith183() throws InterruptedException, ParseException, MalformedURLException {

        stubFor(get(urlPathEqualTo("/1111"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(dialForkWithTimeout15)));

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

        //Prepare Fotini phone to receive a call
        final SipCall fotiniCall = fotiniPhone.createSipCall();
        fotiniCall.listenForIncomingCall();

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
        assertTrue(georgeCall.sendIncomingCallResponse(183, "Ringing-George", 600));
        assertTrue(aliceCall.waitForIncomingCall(30 * 1000));
        assertTrue(aliceCall.sendIncomingCallResponse(100, "Trying-Alice", 600));
        assertTrue(henriqueCall.waitForIncomingCall(30 * 1000));
        assertTrue(henriqueCall.sendIncomingCallResponse(100, "Trying-Henrique", 600));

        //No one will answer the call and Bob will receive disconnect
        assertTrue(georgeCall.listenForCancel());
        assertTrue(aliceCall.listenForCancel());
        assertTrue(henriqueCall.listenForCancel());

        assertTrue(bobCall.listenForDisconnect());

        SipTransaction georgeCancelTransaction = georgeCall.waitForCancel(50 * 1000);
        SipTransaction henriqueCancelTransaction = henriqueCall.waitForCancel(50 * 1000);
        SipTransaction aliceCancelTransaction = aliceCall.waitForCancel(50 * 1000);
        assertNotNull(georgeCancelTransaction);
        assertNotNull(aliceCancelTransaction);
        assertNotNull(henriqueCancelTransaction);
        georgeCall.respondToCancel(georgeCancelTransaction, 200, "OK - George", 600);
        aliceCall.respondToCancel(aliceCancelTransaction, 200, "OK - Alice", 600);
        henriqueCall.respondToCancel(henriqueCancelTransaction, 200, "OK - Henrique", 600);

        assertTrue(alicePhone.unregister(aliceContact, 3600));

        assertTrue(bobCall.waitForDisconnect(50 * 1000));
        assertTrue(bobCall.respondToDisconnect());

        int liveCalls = MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        int liveCallsArraySize = MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        logger.info("&&&&& LiveCalls: "+liveCalls);
        logger.info("&&&&& LiveCallsArraySize: "+liveCallsArraySize);
        assertEquals(0, liveCalls);
        assertEquals(0, liveCallsArraySize);

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
        logger.info("Status for call: "+callSid+" : "+jsonObj.get("status").getAsString());
        assertTrue(jsonObj.get("status").getAsString().equalsIgnoreCase("completed"));
        assertTrue(MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken) == 0);
        assertTrue(MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(), adminAccountSid, adminAuthToken) == 0);

        JsonObject metrics = MonitoringServiceTool.getInstance().getMetrics(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
        Map<String, Integer> mgcpResources = MonitoringServiceTool.getInstance().getMgcpResources(metrics);
        int mgcpEndpoints = mgcpResources.get("MgcpEndpoints");
        int mgcpConnections = mgcpResources.get("MgcpConnections");

        assertEquals(0, mgcpEndpoints);
        assertEquals(0, mgcpConnections);
    }



    //Non regression test for https://telestax.atlassian.net/browse/RESTCOMM-585
    @Test //TODO Fails when the whole test class runs but Passes when run individually
//    @Category(UnstableTests.class)
    @Category(FeatureAltTests.class)
    public synchronized void testDialForkNoAnswerExecuteRCML_ReturnedFromActionURL() throws InterruptedException, ParseException, MalformedURLException {

        stubFor(get(urlPathEqualTo("/1111"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(dialForkWithActionUrl)));

        stubFor(post(urlEqualTo("/test"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(rcmlToReturn)));

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

        //Prepare Fotini phone to receive a call
        final SipCall fotiniCall = fotiniPhone.createSipCall();
        fotiniCall.listenForIncomingCall();

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
//        assertEquals(2, liveCallsArraySize);

        assertTrue(alicePhone.unregister(aliceContact, 3600));

        //Now Fotini should receive a call
        assertTrue(fotiniCall.waitForIncomingCall(30 * 1000));
        assertTrue(fotiniCall.sendIncomingCallResponse(100, "Trying-Fotini", 600));
        assertTrue(fotiniCall.sendIncomingCallResponse(180, "Ringing-Fotini", 600));
        String receivedBody = new String(fotiniCall.getLastReceivedRequest().getRawContent());
        assertTrue(fotiniCall.sendIncomingCallResponse(Response.OK, "OK-Fotini", 3600, receivedBody, "application", "sdp", null, null));
        assertTrue(fotiniCall.waitForAck(5000));
        fotiniCall.listenForDisconnect();

        assertEquals(2, MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken) );
        assertEquals(2, MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(), adminAccountSid, adminAuthToken) );

        Thread.sleep(2000);

        // hangup.

        assertTrue(bobCall.disconnect());

        assertTrue(fotiniCall.waitForDisconnect(50 * 1000));

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

        JsonObject metrics = MonitoringServiceTool.getInstance().getMetrics(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
        Map<String, Integer> mgcpResources = MonitoringServiceTool.getInstance().getMgcpResources(metrics);
        int mgcpEndpoints = mgcpResources.get("MgcpEndpoints");
        int mgcpConnections = mgcpResources.get("MgcpConnections");

        assertEquals(0, mgcpEndpoints);
        assertEquals(0, mgcpConnections);
    }

    @Test
    public synchronized void testDialForkEveryoneBusyExecuteRCML_ReturnedFromActionURL() throws InterruptedException, ParseException, MalformedURLException {

        stubFor(get(urlPathEqualTo("/1111"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(dialForkWithActionUrl)));

        stubFor(post(urlEqualTo("/test"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(rcmlToReturn)));

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

        //Prepare Fotini phone to receive a call
        final SipCall fotiniCall = fotiniPhone.createSipCall();
        fotiniCall.listenForIncomingCall();

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
        assertTrue(georgeCall.sendIncomingCallResponse(486, "Busy Here-George", 3600));
        assertTrue(georgeCall.waitForAck(50 * 1000));

        assertTrue(aliceCall.waitForIncomingCall(30 * 1000));
        assertTrue(aliceCall.sendIncomingCallResponse(100, "Trying-Alice", 600));
        assertTrue(aliceCall.sendIncomingCallResponse(180, "Ringing-Alice", 600));
        assertTrue(aliceCall.sendIncomingCallResponse(486, "Busy Here-Alice", 3600));
        assertTrue(aliceCall.waitForAck(50 * 1000));

        assertTrue(henriqueCall.waitForIncomingCall(30 * 1000));
        assertTrue(henriqueCall.sendIncomingCallResponse(100, "Trying-Henrique", 600));
        assertTrue(henriqueCall.sendIncomingCallResponse(Response.RINGING, "Ringing-Henrique", 3600));
        assertTrue(henriqueCall.sendIncomingCallResponse(486, "Busy Here-Henrique", 3600));
        assertTrue(henriqueCall.waitForAck(50 * 1000));
        //No one will answer the call and RCML will move to the next verb to call Fotini

//        assertTrue(georgeCall.listenForCancel());
//        assertTrue(aliceCall.listenForCancel());
//        assertTrue(henriqueCall.listenForCancel());
//
//        Thread.sleep(1000);
//
//        SipTransaction georgeCancelTransaction = georgeCall.waitForCancel(50 * 1000);
//        SipTransaction henriqueCancelTransaction = henriqueCall.waitForCancel(50 * 1000);
//        SipTransaction aliceCancelTransaction = aliceCall.waitForCancel(50 * 1000);
//        assertNotNull(georgeCancelTransaction);
//        assertNotNull(aliceCancelTransaction);
//        assertNotNull(henriqueCancelTransaction);
//        georgeCall.respondToCancel(georgeCancelTransaction, 200, "OK - George", 600);
//        aliceCall.respondToCancel(aliceCancelTransaction, 200, "OK - Alice", 600);
//        henriqueCall.respondToCancel(henriqueCancelTransaction, 200, "OK - Henrique", 600);

        assertTrue(alicePhone.unregister(aliceContact, 3600));

//        int liveCalls = MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
//        int liveCallsArraySize = MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
//        //Even though no call answered the dial forking the originated call from Bob should be still live
//        assertTrue(liveCalls == 1);
//        assertTrue(liveCallsArraySize == 1);

        //Now Fotini should receive a call
        assertTrue(fotiniCall.waitForIncomingCall(30 * 1000));
        assertTrue(fotiniCall.sendIncomingCallResponse(100, "Trying-Fotini", 600));
        assertTrue(fotiniCall.sendIncomingCallResponse(180, "Ringing-Fotini", 600));
        String receivedBody = new String(fotiniCall.getLastReceivedRequest().getRawContent());
        assertTrue(fotiniCall.sendIncomingCallResponse(Response.OK, "OK-Fotini", 3600, receivedBody, "application", "sdp", null, null));
        assertTrue(fotiniCall.waitForAck(5000));
        fotiniCall.listenForDisconnect();

        Thread.sleep(1000);

        int liveCalls = MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        int liveCallsArraySize = MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        //Even though no call answered the dial forking the originated call from Bob should be still live
        logger.info("&&&& LiveCalls: "+liveCalls);
        logger.info("&&&& LiveCallsArraySize: "+liveCallsArraySize);
        assertEquals(2, liveCalls);
        assertEquals(2, liveCallsArraySize);

//        assertTrue(MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken) == 2);
//        assertTrue(MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(), adminAccountSid, adminAuthToken) == 2);

        Thread.sleep(2000);

        // hangup.
        assertTrue(bobCall.disconnect());

        assertTrue(fotiniCall.waitForDisconnect(50 * 1000));

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

        JsonObject metrics = MonitoringServiceTool.getInstance().getMetrics(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
        Map<String, Integer> mgcpResources = MonitoringServiceTool.getInstance().getMgcpResources(metrics);
        int mgcpEndpoints = mgcpResources.get("MgcpEndpoints");
        int mgcpConnections = mgcpResources.get("MgcpConnections");

        assertEquals(0, mgcpEndpoints);
        assertEquals(0, mgcpConnections);
    }

    private String dialAliceRcml = "<Response><Dial><Client>alice</Client></Dial></Response>";

    @Test //TODO Fails when the whole test class runs but Passes when run individually
//    @Category(UnstableTests.class)
    public void testDialClientAlice() throws ParseException, InterruptedException, MalformedURLException {
        stubFor(get(urlPathEqualTo("/1111"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(dialAliceRcml)));

        SipURI uri = aliceSipStack.getAddressFactory().createSipURI(null, restcommContact);
        assertTrue(alicePhone.register(uri, "alice", "1234", aliceContact, 3600, 3600));

        // Prepare second phone to receive call
        SipCall aliceCall = alicePhone.createSipCall();
        aliceCall.listenForIncomingCall();

        // Create outgoing call with first phone
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
        assertTrue(bobCall.sendInviteOkAck());

        assertTrue(aliceCall.waitForIncomingCall(5000));
        assertTrue(aliceCall.sendIncomingCallResponse(Response.TRYING, "Alice-Trying", 3600));
        assertTrue(aliceCall.sendIncomingCallResponse(Response.RINGING, "Alice-Ringing", 3600));
        String receivedBody = new String(aliceCall.getLastReceivedRequest().getRawContent());
        assertTrue(aliceCall.sendIncomingCallResponse(Response.OK, "Alice-OK", 3600, receivedBody, "application", "sdp",
                null, null));
        assertTrue(aliceCall.waitForAck(5000));

        assertEquals(2, MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken) );
        assertEquals(2, MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(), adminAccountSid, adminAuthToken) );

        Thread.sleep(3000);
        bobCall.listenForDisconnect();

        assertTrue(aliceCall.disconnect());
        Thread.sleep(500);
        assertTrue(bobCall.waitForDisconnect(5000));
        assertTrue(bobCall.respondToDisconnect());

        assertTrue(alicePhone.unregister(aliceContact, 3600));

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

        JsonObject metrics = MonitoringServiceTool.getInstance().getMetrics(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
        Map<String, Integer> mgcpResources = MonitoringServiceTool.getInstance().getMgcpResources(metrics);
        int mgcpEndpoints = mgcpResources.get("MgcpEndpoints");
        int mgcpConnections = mgcpResources.get("MgcpConnections");

        assertEquals(0, mgcpEndpoints);
        assertEquals(0, mgcpConnections);
    }


    @Test
    @Category(FeatureAltTests.class)
    public void testDialClientAliceWithPlay() throws ParseException, InterruptedException, MalformedURLException {
        stubFor(get(urlPathEqualTo("/1111"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(dialAliceRcmlWithPlay)));

        SipURI uri = aliceSipStack.getAddressFactory().createSipURI(null, restcommContact);
        assertTrue(alicePhone.register(uri, "alice", "1234", aliceContact, 3600, 3600));

        // Prepare second phone to receive call
        SipCall aliceCall = alicePhone.createSipCall();
        aliceCall.listenForIncomingCall();

        // Create outgoing call with first phone
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
        assertTrue(bobCall.sendInviteOkAck());

        assertTrue(aliceCall.waitForIncomingCall(5000));
        assertTrue(aliceCall.sendIncomingCallResponse(Response.TRYING, "Alice-Trying", 3600));
        assertTrue(aliceCall.sendIncomingCallResponse(Response.RINGING, "Alice-Ringing", 3600));
        String receivedBody = new String(aliceCall.getLastReceivedRequest().getRawContent());
        assertTrue(aliceCall.sendIncomingCallResponse(Response.OK, "Alice-OK", 3600, receivedBody, "application", "sdp",
                null, null));
        assertTrue(aliceCall.waitForAck(5000));

        assertEquals(2, MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken) );
        assertEquals(2, MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(), adminAccountSid, adminAuthToken) );

        Thread.sleep(3000);
        bobCall.listenForDisconnect();

        assertTrue(aliceCall.disconnect());
        Thread.sleep(500);
        assertTrue(bobCall.waitForDisconnect(5000));
        assertTrue(bobCall.respondToDisconnect());

        assertTrue(alicePhone.unregister(aliceContact, 3600));

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

        JsonObject metrics = MonitoringServiceTool.getInstance().getMetrics(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
        Map<String, Integer> mgcpResources = MonitoringServiceTool.getInstance().getMgcpResources(metrics);
        int mgcpEndpoints = mgcpResources.get("MgcpEndpoints");
        int mgcpConnections = mgcpResources.get("MgcpConnections");

        assertEquals(0, mgcpEndpoints);
        assertEquals(0, mgcpConnections);
    }


    @Test //Test that Restcomm cleans up calls when an error from MMS happens
    @Category(FeatureExpTests.class)
    public void testDialClientAliceWithInvalidPlayFile() throws ParseException, InterruptedException, MalformedURLException {
        stubFor(get(urlPathEqualTo("/1111"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(dialAliceRcmlWithInvalidPlay)));

        SipURI uri = aliceSipStack.getAddressFactory().createSipURI(null, restcommContact);
        assertTrue(alicePhone.register(uri, "alice", "1234", aliceContact, 3600, 3600));

        // Prepare second phone to receive call
        SipCall aliceCall = alicePhone.createSipCall();
        aliceCall.listenForIncomingCall();

        // Create outgoing call with first phone
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
        assertTrue(bobCall.sendInviteOkAck());

        bobCall.listenForDisconnect();

        assertTrue(bobCall.waitForDisconnect(5000));
        assertTrue(bobCall.respondToDisconnect());

        assertEquals(0, MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken) );
        assertEquals(0, MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(), adminAccountSid, adminAuthToken) );

        assertTrue(alicePhone.unregister(aliceContact, 3600));

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

        JsonObject metrics = MonitoringServiceTool.getInstance().getMetrics(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
        Map<String, Integer> mgcpResources = MonitoringServiceTool.getInstance().getMgcpResources(metrics);
        int mgcpEndpoints = mgcpResources.get("MgcpEndpoints");
        int mgcpConnections = mgcpResources.get("MgcpConnections");

        assertEquals(0, mgcpEndpoints);
        assertEquals(0, mgcpConnections);
    }


    @Test
    @Category(FeatureAltTests.class)
    public synchronized void testDialSequentialFirstCallTimeouts() throws InterruptedException, ParseException, MalformedURLException {
        stubFor(get(urlPathEqualTo("/1111"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(dialSequential)));

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

        Thread.sleep(9000);

        assertTrue(henriqueCall.waitForIncomingCall(30 * 1000));
        assertTrue(henriqueCall.sendIncomingCallResponse(Response.TRYING, "Trying-Henrique", 3600));
        assertTrue(henriqueCall.sendIncomingCallResponse(Response.RINGING, "Ringing-Henrique", 3600));


        String receivedBody = new String(henriqueCall.getLastReceivedRequest().getRawContent());
        assertTrue(henriqueCall.sendIncomingCallResponse(Response.OK, "OK-Henrique", 3600, receivedBody, "application", "sdp",
                null, null));
        assertTrue(henriqueCall.waitForAck(50 * 1000));

        //Wait to cancel the other branches
        Thread.sleep(2000);

        int liveCalls = MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        int liveCallsArraySize = MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        assertEquals(2, liveCalls);
        assertEquals(2, liveCallsArraySize);

        henriqueCall.listenForDisconnect();

        Thread.sleep(8000);

        // hangup.

        bobCall.disconnect();

        assertTrue(henriqueCall.waitForDisconnect(30 * 1000));

        assertTrue(alicePhone.unregister(aliceContact, 3600));

        Thread.sleep(10 * 1000);

        assertTrue(MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken) == 0);
        assertTrue(MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(), adminAccountSid, adminAuthToken) == 0);

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

        JsonObject metrics = MonitoringServiceTool.getInstance().getMetrics(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
        Map<String, Integer> mgcpResources = MonitoringServiceTool.getInstance().getMgcpResources(metrics);
        int mgcpEndpoints = mgcpResources.get("MgcpEndpoints");
        int mgcpConnections = mgcpResources.get("MgcpConnections");

        assertEquals(0, mgcpEndpoints);
        assertEquals(0, mgcpConnections);
    }

    @Test
//    @Category({FeatureExpTests.class, UnstableTests.class})
    @Category(FeatureAltTests.class)
    public synchronized void testDialForkWithServerErrorReponse() throws InterruptedException, ParseException, MalformedURLException {
        stubFor(get(urlPathEqualTo("/1111"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(dialForkTwoSipUrisRcml)));

        //Prepare Fotini to receive call
        final SipCall fotiniCall = fotiniPhone.createSipCall();
        fotiniCall.listenForIncomingCall();

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


        assertTrue(fotiniCall.waitForIncomingCall(30 * 1000));
        assertTrue(fotiniCall.sendIncomingCallResponse(Response.TRYING, "Trying-Fotini", 600));
        assertTrue(fotiniCall.sendIncomingCallResponse(Response.SERVER_INTERNAL_ERROR, "Fotini-Internal-Server-Error", 600));
        assertTrue(fotiniCall.waitForAck(5000));

        assertTrue(henriqueCall.waitForIncomingCall(30 * 1000));
        assertTrue(henriqueCall.sendIncomingCallResponse(Response.TRYING, "Trying-Henrique", 3600));
        assertTrue(henriqueCall.sendIncomingCallResponse(Response.RINGING, "Ringing-Henrique", 3600));

        String receivedBody = new String(henriqueCall.getLastReceivedRequest().getRawContent());
        assertTrue(henriqueCall.sendIncomingCallResponse(Response.OK, "OK-Henrique", 3600, receivedBody, "application", "sdp",
                null, null));
        assertTrue(henriqueCall.waitForAck(50 * 1000));

        int liveCalls = MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        int liveCallsArraySize = MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        logger.info("&&&&& LiveCalls: "+liveCalls);
        logger.info("&&&&& LiveCallsArraySize: "+liveCallsArraySize);
        assertEquals(2, liveCalls);
        assertEquals(2, liveCallsArraySize);

        henriqueCall.listenForDisconnect();

        Thread.sleep(8000);

        // hangup.
        bobCall.disconnect();

        assertTrue(henriqueCall.waitForDisconnect(30 * 1000));
        assertTrue(henriqueCall.respondToDisconnect());

        Thread.sleep(10 * 1000);

        assertTrue(MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken) == 0);
        assertTrue(MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(), adminAccountSid, adminAuthToken) == 0);

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
        logger.info("%%%% CallSID: "+callSid+" Status : "+jsonObj.get("status").getAsString());
        assertTrue(jsonObj.get("status").getAsString().equalsIgnoreCase("completed"));
        assertTrue(MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken) == 0);
        assertTrue(MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(), adminAccountSid, adminAuthToken) == 0);

        JsonObject metrics = MonitoringServiceTool.getInstance().getMetrics(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
        Map<String, Integer> mgcpResources = MonitoringServiceTool.getInstance().getMgcpResources(metrics);
        int mgcpEndpoints = mgcpResources.get("MgcpEndpoints");
        int mgcpConnections = mgcpResources.get("MgcpConnections");

        assertEquals(0, mgcpEndpoints);
        assertEquals(0, mgcpConnections);
    }

    @Deployment(name = "DialForkTest", managed = true, testable = false)
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
        replacements.put("5093", String.valueOf(fotiniPort));

        List<String> resources = new ArrayList(Arrays.asList("hello-play.xml"));
        return WebArchiveUtil.createWebArchiveNoGw("restcomm.xml",
                "restcomm.script_dialForkTest",resources, replacements);
    }
}
