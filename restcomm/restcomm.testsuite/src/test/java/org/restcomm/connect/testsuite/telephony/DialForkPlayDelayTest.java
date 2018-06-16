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
 * Tests for the Dial Forking timeout, this test class is being tested with 2 seconds audio file, that can make RC in
 * PLAYING state for 2 seconds.
 * Created by xhoaluu on 03/29/18.
 */
@RunWith(Arquillian.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@Category(SequentialClassTests.class)
public class DialForkPlayDelayTest {

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
    private String dialTimeoutRcmlWithPlay;
    private String rcmlPlayAndDialReturn;

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

        dialTimeoutRcmlWithPlay = "<Response><Dial timeout=\"1\" action=\"http://127.0.0.1:" + mockPort+ "/test\"><Client>alice</Client></Dial></Response>";
        rcmlPlayAndDialReturn = "<Response><Play>" + deploymentUrl.toString() + "/audio/demo-prompt.wav</Play><Dial><Client>bob</Client></Dial></Response>";

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
    @Category(FeatureAltTests.class)
    public void testDialTimeoutWithPlay() throws ParseException, InterruptedException, MalformedURLException {
        stubFor(get(urlPathEqualTo("/1111"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(dialTimeoutRcmlWithPlay)));

        stubFor(post(urlEqualTo("/test"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(rcmlPlayAndDialReturn)));

        SipURI Alice_uri = aliceSipStack.getAddressFactory().createSipURI(null, restcommContact);
        assertTrue(alicePhone.register(Alice_uri, "alice", "1234", aliceContact, 3600, 3600));

        SipURI Bob_uri = bobSipStack.getAddressFactory().createSipURI(null, restcommContact);
        assertTrue(bobPhone.register(Bob_uri, "bob", "1234", bobContact, 3600, 3600));

        // Prepare Alice's phone to receive call
        SipCall aliceCall = alicePhone.createSipCall();
        aliceCall.listenForIncomingCall();

        // Create outgoing call with first phone
        final SipCall henriqueCall = henriquePhone.createSipCall();
        henriqueCall.initiateOutgoingCall(henriqueContact, "sip:1111@" + restcommContact, null, body, "application", "sdp", null, null);
        assertLastOperationSuccess(henriqueCall);
        assertTrue(henriqueCall.waitOutgoingCallResponse(5 * 1000));
        final int response = henriqueCall.getLastReceivedResponse().getStatusCode();
        assertTrue(response == Response.TRYING || response == Response.RINGING);

        if (response == Response.TRYING) {
            assertTrue(henriqueCall.waitOutgoingCallResponse(5 * 1000));
            assertEquals(Response.RINGING, henriqueCall.getLastReceivedResponse().getStatusCode());
        }

        assertTrue(henriqueCall.waitOutgoingCallResponse(5 * 1000));
        assertEquals(Response.OK, henriqueCall.getLastReceivedResponse().getStatusCode());
        assertTrue(henriqueCall.sendInviteOkAck());

        assertTrue(aliceCall.waitForIncomingCall(5000));
        String receivedBody = new String(aliceCall.getLastReceivedRequest().getRawContent());
        assertTrue(aliceCall.sendIncomingCallResponse(Response.TEMPORARILY_UNAVAILABLE, "Alice-TemporaryUnavailable", 3600, receivedBody, "application", "sdp",
                null, null));
        assertTrue(aliceCall.waitForAck(5000));

        // Prepare Bob's phone to receive call
        SipCall bobCall = bobPhone.createSipCall();
        bobCall.listenForIncomingCall();

        assertTrue(bobCall.waitForIncomingCall(5000));
        assertTrue(bobCall.sendIncomingCallResponse(Response.TRYING, "Bob-Trying", 3600));
        assertTrue(bobCall.sendIncomingCallResponse(Response.RINGING, "Bob-Ringing", 3600));
        assertTrue(bobCall.sendIncomingCallResponse(Response.OK, "Bob-OK", 3600, receivedBody, "application", "sdp",
                null, null));
        assertTrue(bobCall.waitForAck(5000));

        Thread.sleep(2000);
        henriqueCall.listenForDisconnect();

        assertTrue(bobCall.disconnect());
        Thread.sleep(500);
        assertTrue(henriqueCall.waitForDisconnect(5000));
        assertTrue(henriqueCall.respondToDisconnect());

        assertTrue(alicePhone.unregister(aliceContact, 3600));
        assertTrue(bobPhone.unregister(aliceContact, 3600));

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
    public void testDialBusyTimeoutWithPlay() throws ParseException, InterruptedException, MalformedURLException {
        stubFor(get(urlPathEqualTo("/1111"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(dialTimeoutRcmlWithPlay)));

        stubFor(post(urlEqualTo("/test"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(rcmlPlayAndDialReturn)));

        SipURI Alice_uri = aliceSipStack.getAddressFactory().createSipURI(null, restcommContact);
        assertTrue(alicePhone.register(Alice_uri, "alice", "1234", aliceContact, 3600, 3600));

        SipURI Bob_uri = bobSipStack.getAddressFactory().createSipURI(null, restcommContact);
        assertTrue(bobPhone.register(Bob_uri, "bob", "1234", bobContact, 3600, 3600));

        // Prepare Alice's phone to receive call
        SipCall aliceCall = alicePhone.createSipCall();
        aliceCall.listenForIncomingCall();

        // Create outgoing call with first phone
        final SipCall henriqueCall = henriquePhone.createSipCall();
        henriqueCall.initiateOutgoingCall(henriqueContact, "sip:1111@" + restcommContact, null, body, "application", "sdp", null, null);
        assertLastOperationSuccess(henriqueCall);
        assertTrue(henriqueCall.waitOutgoingCallResponse(5 * 1000));
        final int response = henriqueCall.getLastReceivedResponse().getStatusCode();
        assertTrue(response == Response.TRYING || response == Response.RINGING);

        if (response == Response.TRYING) {
            assertTrue(henriqueCall.waitOutgoingCallResponse(5 * 1000));
            assertEquals(Response.RINGING, henriqueCall.getLastReceivedResponse().getStatusCode());
        }

        assertTrue(henriqueCall.waitOutgoingCallResponse(5 * 1000));
        assertEquals(Response.OK, henriqueCall.getLastReceivedResponse().getStatusCode());
        assertTrue(henriqueCall.sendInviteOkAck());

        assertTrue(aliceCall.waitForIncomingCall(5000));
        String receivedBody = new String(aliceCall.getLastReceivedRequest().getRawContent());
        assertTrue(aliceCall.sendIncomingCallResponse(Response.BUSY_HERE, "Alice-BUSY-HERE", 3600, receivedBody, "application", "sdp",
                null, null));
        assertTrue(aliceCall.waitForAck(5000));

        // Prepare Bob's phone to receive call
        SipCall bobCall = bobPhone.createSipCall();
        bobCall.listenForIncomingCall();

        assertTrue(bobCall.waitForIncomingCall(5000));
        assertTrue(bobCall.sendIncomingCallResponse(Response.TRYING, "Bob-Trying", 3600));
        assertTrue(bobCall.sendIncomingCallResponse(Response.RINGING, "Bob-Ringing", 3600));
        assertTrue(bobCall.sendIncomingCallResponse(Response.OK, "Bob-OK", 3600, receivedBody, "application", "sdp",
                null, null));
        assertTrue(bobCall.waitForAck(5000));

        Thread.sleep(2000);
        henriqueCall.listenForDisconnect();

        assertTrue(bobCall.disconnect());
        Thread.sleep(500);
        assertTrue(henriqueCall.waitForDisconnect(5000));
        assertTrue(henriqueCall.respondToDisconnect());

        assertTrue(alicePhone.unregister(aliceContact, 3600));
        assertTrue(bobPhone.unregister(aliceContact, 3600));

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
        return WebArchiveUtil.createWebArchiveNoGw("restcomm_dialForkPlayDelayTest.xml",
                "restcomm.script_dialForkPlayDelayTest",resources, replacements);
    }
}
