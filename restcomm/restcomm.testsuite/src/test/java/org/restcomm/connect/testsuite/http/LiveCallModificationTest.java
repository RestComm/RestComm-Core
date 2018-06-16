package org.restcomm.connect.testsuite.http;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.cafesip.sipunit.SipAssert.assertLastOperationSuccess;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.net.URL;
import java.util.Map;

import javax.sip.address.SipURI;
import javax.sip.message.Response;

import org.apache.log4j.Logger;
import org.cafesip.sipunit.Credential;
import org.cafesip.sipunit.SipCall;
import org.cafesip.sipunit.SipPhone;
import org.cafesip.sipunit.SipStack;
import org.cafesip.sipunit.SipTransaction;
import org.jboss.arquillian.container.mss.extension.SipStackTool;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.FixMethodOrder;
import org.junit.runners.MethodSorters;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.restcomm.connect.commons.Version;
import org.restcomm.connect.commons.annotations.BrokenTests;
import org.restcomm.connect.commons.annotations.UnstableTests;
import org.restcomm.connect.testsuite.tools.MonitoringServiceTool;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * @author <a href="mailto:gvagenas@gmail.com">gvagenas</a>
 */
@RunWith(Arquillian.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@Category(UnstableTests.class)
public class LiveCallModificationTest {

    private final static Logger logger = Logger.getLogger(CreateCallsTest.class.getName());
    private static final String version = Version.getVersion();

    private static final byte[] bytes = new byte[] { 118, 61, 48, 13, 10, 111, 61, 117, 115, 101, 114, 49, 32, 53, 51, 54, 53,
            53, 55, 54, 53, 32, 50, 51, 53, 51, 54, 56, 55, 54, 51, 55, 32, 73, 78, 32, 73, 80, 52, 32, 49, 50, 55, 46, 48, 46,
            48, 46, 49, 13, 10, 115, 61, 45, 13, 10, 99, 61, 73, 78, 32, 73, 80, 52, 32, 49, 50, 55, 46, 48, 46, 48, 46, 49,
            13, 10, 116, 61, 48, 32, 48, 13, 10, 109, 61, 97, 117, 100, 105, 111, 32, 54, 48, 48, 48, 32, 82, 84, 80, 47, 65,
            86, 80, 32, 48, 13, 10, 97, 61, 114, 116, 112, 109, 97, 112, 58, 48, 32, 80, 67, 77, 85, 47, 56, 48, 48, 48, 13, 10 };
    private static final String body = new String(bytes);

    @ArquillianResource
    URL deploymentUrl;

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(8090); // No-args constructor defaults to port 8080

    private String adminAccountSid = "ACae6e420f425248d6a26948c17a9e2acf";
    private String adminAuthToken = "77f8c12cc7b8f8423e5c38b035249166";

    private static SipStackTool tool1;
    private static SipStackTool tool2;
    private static SipStackTool tool3;

    private SipStack bobSipStack;
    private SipPhone bobPhone;
    private String bobContact = "sip:bob@127.0.0.1:5090";

    private SipStack georgeSipStack;
    private SipPhone georgePhone;
    private String georgeContact = "sip:+131313@127.0.0.1:5070";

    // Alice is a Restcomm Client with VoiceURL. This Restcomm Client can register with Restcomm and whatever will dial the RCML
    // of the VoiceURL will be executed.
    private SipStack aliceSipStack;
    private SipPhone alicePhone;
    private String aliceContact = "sip:alice@127.0.0.1:5091";

    private String dialRestcomm = "sip:1111@127.0.0.1:5080";

    @BeforeClass
    public static void beforeClass() throws Exception {
        tool1 = new SipStackTool("LiveCallModification1");
        tool2 = new SipStackTool("LiveCallModification2");
        tool3 = new SipStackTool("LiveCallModification3");
    }

    @Before
    public void before() throws Exception {
        bobSipStack = tool1.initializeSipStack(SipStack.PROTOCOL_UDP, "127.0.0.1", "5090", "127.0.0.1:5080");
        bobPhone = bobSipStack.createSipPhone("127.0.0.1", SipStack.PROTOCOL_UDP, 5080, bobContact);

        georgeSipStack = tool2.initializeSipStack(SipStack.PROTOCOL_UDP, "127.0.0.1", "5070", "127.0.0.1:5080");
        georgePhone = georgeSipStack.createSipPhone("127.0.0.1", SipStack.PROTOCOL_UDP, 5080, georgeContact);

        aliceSipStack = tool3.initializeSipStack(SipStack.PROTOCOL_UDP, "127.0.0.1", "5091", "127.0.0.1:5080");
        alicePhone = aliceSipStack.createSipPhone("127.0.0.1", SipStack.PROTOCOL_UDP, 5080, aliceContact);
    }

    @After
    public void after() throws Exception {
        if (bobPhone != null) {
            bobPhone.dispose();
        }
        if (bobSipStack != null) {
            bobSipStack.dispose();
        }

        if (georgePhone != null) {
            georgePhone.dispose();
        }
        if (georgeSipStack != null) {
            georgeSipStack.dispose();
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

    private int getConferencesSize() {
        JsonObject conferences = RestcommConferenceTool.getInstance().getConferences(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
        JsonArray conferenceArray = conferences.getAsJsonArray("conferences");
        return conferenceArray.size();
    }

    private int getParticipantsSize(final String name) {
        JsonObject conferences = RestcommConferenceTool.getInstance().getConferences(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        JsonArray conferenceArray = conferences.getAsJsonArray("conferences");
        String confSid = null;
        for (int i = 0; i < conferenceArray.size(); i++) {
            JsonObject confObj = conferenceArray.get(i).getAsJsonObject();
            String confName = confObj.get("friendly_name").getAsString();
            if (confName.equalsIgnoreCase(name)) {
                confSid = confObj.get("sid").getAsString();
                break;
            }
        }
        //        confSid = conferenceArray.get(conferenceArray.size()-1).getAsJsonObject().get("sid").getAsString();
        JsonObject participants = RestcommConferenceParticipantsTool.getInstance().getParticipants(deploymentUrl.toString(), adminAccountSid, adminAuthToken, confSid);
        JsonArray participantsArray = participants.getAsJsonArray("calls");
        return participantsArray.size();
    }

    @Test
    // Terminate a call in-progress using the Live Call Modification API. Non-regression test for issue:
    // https://bitbucket.org/telestax/telscale-restcomm/issue/139
    public void terminateInProgressCall() throws Exception {

        SipCall bobCall = bobPhone.createSipCall();
        bobCall.listenForIncomingCall();

        SipCall georgeCall = georgePhone.createSipCall();
        georgeCall.listenForIncomingCall();

        String from = "+15126002188";
        String to = bobContact;
        String rcmlUrl = deploymentUrl.toString() + "/dial-number-entry.xml";

        JsonObject callResult = (JsonObject) RestcommCallsTool.getInstance().createCall(deploymentUrl.toString(), adminAccountSid,
                adminAuthToken, from, to, rcmlUrl);
        assertNotNull(callResult);
        String callSid = callResult.get("sid").getAsString();

        assertTrue(bobCall.waitForIncomingCall(5000));
        String receivedBody = new String(bobCall.getLastReceivedRequest().getRawContent());
        assertTrue(bobCall.sendIncomingCallResponse(Response.RINGING, "Ringing-Bob", 3600));
        assertTrue(bobCall
                .sendIncomingCallResponse(Response.OK, "OK-Bob", 3600, receivedBody, "application", "sdp", null, null));

        // Restcomm now should execute RCML that will create a call to +131313 (george's phone)

        assertTrue(georgeCall.waitForIncomingCall(5000));
        receivedBody = new String(georgeCall.getLastReceivedRequest().getRawContent());
        assertTrue(georgeCall.sendIncomingCallResponse(Response.RINGING, "Ringing-George", 3600));
        assertTrue(georgeCall.sendIncomingCallResponse(Response.OK, "OK-George", 3600, receivedBody, "application", "sdp",
                null, null));

        Thread.sleep(1000);

        bobCall.listenForDisconnect();
        georgeCall.listenForDisconnect();

        callResult = RestcommCallsTool.getInstance().modifyCall(deploymentUrl.toString(), adminAccountSid, adminAuthToken,
                callSid, "completed", null);

        assertTrue(georgeCall.waitForDisconnect(5000));
        assertTrue(georgeCall.respondToDisconnect());

        assertTrue(bobCall.waitForDisconnect(5000));
        assertTrue(bobCall.respondToDisconnect());

        georgeCall.dispose();
        bobCall.dispose();

        JsonObject metrics = MonitoringServiceTool.getInstance().getMetrics(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
        assertNotNull(metrics);
        int liveCalls = metrics.getAsJsonObject("Metrics").get("LiveCalls").getAsInt();
        logger.info("LiveCalls: "+liveCalls);
        int liveCallsArraySize = metrics.getAsJsonArray("LiveCallDetails").size();
        logger.info("LiveCallsArraySize: "+liveCallsArraySize);
        assertEquals(0,liveCalls);
        assertEquals(0, liveCallsArraySize);

        Map<String, Integer> mgcpResources = MonitoringServiceTool.getInstance().getMgcpResources(metrics);
        int mgcpEndpoints = mgcpResources.get("MgcpEndpoints");
        int mgcpConnections = mgcpResources.get("MgcpConnections");

        assertEquals(0, mgcpEndpoints);
        assertEquals(0, mgcpConnections);
    }

    private final String confRoom3 = "confRoom3";
    private String dialConfernceRcml3 = "<Response><Dial><Conference>"+confRoom3+"</Conference></Dial></Response>";
    @Test
    public synchronized void testDialConferenceTerminateWithLCM() throws Exception {
        stubFor(get(urlPathEqualTo("/1111"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(dialConfernceRcml3)));

        final SipCall georgeCall = georgePhone.createSipCall();
        georgeCall.initiateOutgoingCall(georgeContact, dialRestcomm, null, body, "application", "sdp", null, null);
        assertLastOperationSuccess(georgeCall);
        assertTrue(georgeCall.waitOutgoingCallResponse(5 * 1000));
        int responseGeorge = georgeCall.getLastReceivedResponse().getStatusCode();
        assertTrue(responseGeorge == Response.TRYING || responseGeorge == Response.RINGING);

        if (responseGeorge == Response.TRYING) {
            assertTrue(georgeCall.waitOutgoingCallResponse(5 * 1000));
            assertEquals(Response.RINGING, georgeCall.getLastReceivedResponse().getStatusCode());
        }

        assertTrue(georgeCall.waitOutgoingCallResponse(5 * 1000));
        assertEquals(Response.OK, georgeCall.getLastReceivedResponse().getStatusCode());
        georgeCall.sendInviteOkAck();
        assertTrue(!(georgeCall.getLastReceivedResponse().getStatusCode() >= 400));
        String callSid = georgeCall.getLastReceivedResponse().getMessage().getHeader("X-RestComm-CallSid").toString().split(":")[1].trim();

        Thread.sleep(3000);

        int liveCalls = MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        int liveCallsArraySize = MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        logger.info("&&&&& LiveCalls: "+liveCalls);
        logger.info("&&&&& LiveCallsArraySize: "+liveCallsArraySize);
        assertEquals(1,liveCalls);
        assertEquals(1, liveCallsArraySize);
        assertTrue(getConferencesSize()>=1);
        int numOfParticipants = getParticipantsSize(confRoom3);
        logger.info("Number of participants: "+numOfParticipants);
        assertTrue(numOfParticipants==1);

        JsonObject metrics = MonitoringServiceTool.getInstance().getMetrics(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
        assertNotNull(metrics);
        Map<String, Integer> mgcpResources = MonitoringServiceTool.getInstance().getMgcpResources(metrics);
        int mgcpEndpoints = mgcpResources.get("MgcpEndpoints");
        int mgcpConnections = mgcpResources.get("MgcpConnections");

        assertTrue(mgcpEndpoints>0);
        assertTrue(mgcpConnections>0);

        Thread.sleep(3000);

        georgeCall.listenForDisconnect();

        JsonObject callResult = RestcommCallsTool.getInstance().modifyCall(deploymentUrl.toString(), adminAccountSid, adminAuthToken,
                callSid, "completed", null);

        assertTrue(georgeCall.waitForDisconnect(5000));
        assertTrue(georgeCall.respondToDisconnect());


        Thread.sleep(5000);
        liveCalls = MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        liveCallsArraySize = MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        logger.info("&&&&& LiveCalls: "+liveCalls);
        logger.info("&&&&& LiveCallsArraySize: "+liveCallsArraySize);
        assertEquals(0,liveCalls);
        assertEquals(0, liveCallsArraySize);
        assertTrue(getConferencesSize()>=1);
        int confRoom2Participants = getParticipantsSize(confRoom3);
        logger.info("&&&&& ConfRoom2Participants: "+confRoom2Participants);
        assertTrue(confRoom2Participants==0);

        metrics = MonitoringServiceTool.getInstance().getMetrics(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
        mgcpResources = MonitoringServiceTool.getInstance().getMgcpResources(metrics);
        mgcpEndpoints = mgcpResources.get("MgcpEndpoints");
        mgcpConnections = mgcpResources.get("MgcpConnections");

        assertEquals(0, mgcpEndpoints);
        assertEquals(0, mgcpConnections);
    }

    @Test
    // Terminate a call in-progress using the Live Call Modification API. Non-regression test for issue:
    // https://bitbucket.org/telestax/telscale-restcomm/issue/139
    public void terminateInProgressCallAlreadyTerminated() throws Exception {

        SipCall bobCall = bobPhone.createSipCall();
        bobCall.listenForIncomingCall();

        SipCall georgeCall = georgePhone.createSipCall();
        georgeCall.listenForIncomingCall();

        String from = "+15126002188";
        String to = bobContact;
        String rcmlUrl = deploymentUrl.toString() + "/dial-number-entry.xml";

        JsonObject callResult = (JsonObject) RestcommCallsTool.getInstance().createCall(deploymentUrl.toString(), adminAccountSid,
                adminAuthToken, from, to, rcmlUrl);
        assertNotNull(callResult);
        String callSid = callResult.get("sid").getAsString();

        assertTrue(bobCall.waitForIncomingCall(5000));
        String receivedBody = new String(bobCall.getLastReceivedRequest().getRawContent());
        assertTrue(bobCall.sendIncomingCallResponse(Response.RINGING, "Ringing-Bob", 3600));
        assertTrue(bobCall
                .sendIncomingCallResponse(Response.OK, "OK-Bob", 3600, receivedBody, "application", "sdp", null, null));

        // Restcomm now should execute RCML that will create a call to +131313 (george's phone)

        assertTrue(georgeCall.waitForIncomingCall(5000));
        receivedBody = new String(georgeCall.getLastReceivedRequest().getRawContent());
        assertTrue(georgeCall.sendIncomingCallResponse(Response.RINGING, "Ringing-George", 3600));
        assertTrue(georgeCall.sendIncomingCallResponse(Response.OK, "OK-George", 3600, receivedBody, "application", "sdp",
                null, null));

        Thread.sleep(1000);

        bobCall.listenForDisconnect();
        georgeCall.listenForDisconnect();

        callResult = RestcommCallsTool.getInstance().modifyCall(deploymentUrl.toString(), adminAccountSid, adminAuthToken,
                callSid, "completed", null);

        assertTrue(georgeCall.waitForDisconnect(5000));
        assertTrue(georgeCall.respondToDisconnect());

        assertTrue(bobCall.waitForDisconnect(5000));
        assertTrue(bobCall.respondToDisconnect());

        Thread.sleep(1000);

        callResult = RestcommCallsTool.getInstance().modifyCall(deploymentUrl.toString(), adminAccountSid, adminAuthToken,
                callSid, "completed", null);

        assertNotNull(callResult);

        georgeCall.dispose();
        bobCall.dispose();

        JsonObject metrics = MonitoringServiceTool.getInstance().getMetrics(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
        assertNotNull(metrics);
        int liveCalls = metrics.getAsJsonObject("Metrics").get("LiveCalls").getAsInt();
        logger.info("LiveCalls: "+liveCalls);
        int liveCallsArraySize = metrics.getAsJsonArray("LiveCallDetails").size();
        logger.info("LiveCallsArraySize: "+liveCallsArraySize);
        assertEquals(0,liveCalls);
        assertEquals(0, liveCallsArraySize);

        Map<String, Integer> mgcpResources = MonitoringServiceTool.getInstance().getMgcpResources(metrics);
        int mgcpEndpoints = mgcpResources.get("MgcpEndpoints");
        int mgcpConnections = mgcpResources.get("MgcpConnections");

        assertEquals(0, mgcpEndpoints);
        assertEquals(0, mgcpConnections);
    }

    @Test
    // Terminate a call that is ringing using the Live Call Modification API. Non-regression test for issue:
    // https://bitbucket.org/telestax/telscale-restcomm/issue/139
    public void terminateRingingCall() throws Exception {

        SipCall bobCall = bobPhone.createSipCall();
        bobCall.listenForIncomingCall();

        SipCall georgeCall = georgePhone.createSipCall();
        georgeCall.listenForIncomingCall();

        String from = "+15126002188";
        String to = bobContact;
        String rcmlUrl = deploymentUrl.toString() + "/dial-number-entry-lcm.xml";

        JsonObject callResult = (JsonObject) RestcommCallsTool.getInstance().createCall(deploymentUrl.toString(), adminAccountSid,
                adminAuthToken, from, to, rcmlUrl);
        assertNotNull(callResult);
        String callSid = callResult.get("sid").getAsString();

        assertTrue(bobCall.waitForIncomingCall(5000));
        String receivedBody = new String(bobCall.getLastReceivedRequest().getRawContent());
        assertTrue(bobCall.sendIncomingCallResponse(Response.RINGING, "Ringing-Bob1", 3600));

        Thread.sleep(1000);

        bobCall.listenForDisconnect();

        callResult = RestcommCallsTool.getInstance().modifyCall(deploymentUrl.toString(), adminAccountSid, adminAuthToken,
                callSid, "canceled", null);

        SipTransaction transaction = bobCall.waitForCancel(5000);
        assertNotNull(transaction);
        bobCall.respondToCancel(transaction, 200, "OK-2-Cancel-Bob", 3600);

        georgeCall.dispose();
        bobCall.dispose();

        JsonObject metrics = MonitoringServiceTool.getInstance().getMetrics(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
        assertNotNull(metrics);
        int liveCalls = metrics.getAsJsonObject("Metrics").get("LiveCalls").getAsInt();
        logger.info("LiveCalls: "+liveCalls);
        int liveCallsArraySize = metrics.getAsJsonArray("LiveCallDetails").size();
        logger.info("LiveCallsArraySize: "+liveCallsArraySize);
        assertEquals(0,liveCalls);
        assertEquals(0, liveCallsArraySize);

        Map<String, Integer> mgcpResources = MonitoringServiceTool.getInstance().getMgcpResources(metrics);
        int mgcpEndpoints = mgcpResources.get("MgcpEndpoints");
        int mgcpConnections = mgcpResources.get("MgcpConnections");

        assertEquals(0, mgcpEndpoints);
        assertEquals(0, mgcpConnections);
    }

    @Test
    // Redirect a call to a different URL using the Live Call Modification API. Non-regression test for issue:
    // https://bitbucket.org/telestax/telscale-restcomm/issue/139
    // TODO: This test is expected to fail because of issue https://bitbucket.org/telestax/telscale-restcomm/issue/192
    public void redirectCall() throws Exception {

        SipCall bobCall = bobPhone.createSipCall();
        bobCall.listenForIncomingCall();

        SipCall georgeCall = georgePhone.createSipCall();
        georgeCall.listenForIncomingCall();

        // Register Alice Restcomm client
        SipURI uri = aliceSipStack.getAddressFactory().createSipURI(null, "127.0.0.1:5080");
        assertTrue(alicePhone.register(uri, "alice", "1234", aliceContact, 3600, 3600));

        SipCall aliceCall = alicePhone.createSipCall();
        aliceCall.listenForIncomingCall();

        String from = "+15126002188";
        String to = bobContact;
        String rcmlUrl = deploymentUrl.toString() + "/dial-number-entry.xml";

        JsonObject callResult = (JsonObject) RestcommCallsTool.getInstance().createCall(deploymentUrl.toString(), adminAccountSid,
                adminAuthToken, from, to, rcmlUrl);
        assertNotNull(callResult);
        String callSid = callResult.get("sid").getAsString();

        assertTrue(bobCall.waitForIncomingCall(5000));
        String receivedBody = new String(bobCall.getLastReceivedRequest().getRawContent());
        assertTrue(bobCall.sendIncomingCallResponse(Response.RINGING, "Ringing-Bob", 3600));
        assertTrue(bobCall
                .sendIncomingCallResponse(Response.OK, "OK-Bob", 3600, receivedBody, "application", "sdp", null, null));

        assertTrue(bobCall.waitForAck(5000));

        // Restcomm now should execute RCML that will create a call to +131313 (george's phone)

        assertTrue(georgeCall.waitForIncomingCall(5000));
        receivedBody = new String(georgeCall.getLastReceivedRequest().getRawContent());
        assertTrue(georgeCall.sendIncomingCallResponse(Response.RINGING, "Ringing-George", 3600));
        assertTrue(georgeCall.sendIncomingCallResponse(Response.OK, "OK-George", 3600, receivedBody, "application", "sdp",
                null, null));

        assertTrue(georgeCall.waitForAck(5000));

        Thread.sleep(10000);
        System.out.println("\n ******************** \nAbout to redirect the call\n ********************\n");
        rcmlUrl = deploymentUrl.toString() + "/dial-client-entry.xml";

        callResult = RestcommCallsTool.getInstance().modifyCall(deploymentUrl.toString(), adminAccountSid, adminAuthToken,
                callSid, null, rcmlUrl);

        georgeCall.listenForDisconnect();
        assertTrue(georgeCall.waitForDisconnect(10000));
        assertTrue(georgeCall.respondToDisconnect());

        // Restcomm now should execute the new RCML and create a call to Alice Restcomm client
        // TODO: This test is expected to fail because of issue https://bitbucket.org/telestax/telscale-restcomm/issue/192
        assertTrue(aliceCall.waitForIncomingCall(5000));
        receivedBody = new String(aliceCall.getLastReceivedRequest().getRawContent());
        assertTrue(aliceCall.sendIncomingCallResponse(Response.RINGING, "Ringing-Alice", 3600));
        assertTrue(aliceCall.sendIncomingCallResponse(Response.OK, "OK-Alice", 3600, receivedBody, "application", "sdp", null,
                null));

        Thread.sleep(3000);

        aliceCall.listenForDisconnect();
        bobCall.listenForDisconnect();

        assertTrue(aliceCall.disconnect());
        assertTrue(aliceCall.waitForAck(5000));

        assertTrue(bobCall.waitForDisconnect(5000));
        assertTrue(bobCall.respondToDisconnect());


        JsonObject metrics = MonitoringServiceTool.getInstance().getMetrics(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
        assertNotNull(metrics);
        int liveCalls = metrics.getAsJsonObject("Metrics").get("LiveCalls").getAsInt();
        logger.info("LiveCalls: "+liveCalls);
        int liveCallsArraySize = metrics.getAsJsonArray("LiveCallDetails").size();
        logger.info("LiveCallsArraySize: "+liveCallsArraySize);
        assertEquals(0,liveCalls);
        assertEquals(0, liveCallsArraySize);

        Map<String, Integer> mgcpResources = MonitoringServiceTool.getInstance().getMgcpResources(metrics);
        int mgcpEndpoints = mgcpResources.get("MgcpEndpoints");
        int mgcpConnections = mgcpResources.get("MgcpConnections");

        assertEquals(0, mgcpEndpoints);
        assertEquals(0, mgcpConnections);

    }

    String dialConference = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<Response>\n" +
            "\t<Dial timeout=\"10\">\n" +
            "\t  <Conference muted=\"true\" startConferenceOnEnter=\"false\" beep=\"false\">Conf1234</Conference>\n" +
            "\t</Dial>\n" +
            "</Response>";
    @Test
    // Redirect a call to a different URL using the Live Call Modification API. Non-regression test for issue:
    // https://bitbucket.org/telestax/telscale-restcomm/issue/139
    // TODO: This test is expected to fail because of issue https://bitbucket.org/telestax/telscale-restcomm/issue/192
    public void redirectCallInConferenceMuted() throws Exception {
        stubFor(get(urlPathEqualTo("/1111"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(dialConference)));

        // Register Alice Restcomm client
        SipURI uri = aliceSipStack.getAddressFactory().createSipURI(null, "127.0.0.1:5080");
        assertTrue(alicePhone.register(uri, "alice", "1234", aliceContact, 3600, 3600));

        SipCall aliceCall = alicePhone.createSipCall();
        aliceCall.listenForIncomingCall();

        Credential bobCredential = new Credential("127.0.0.1", "bob", "1234");
        bobPhone.addUpdateCredential(bobCredential);

        final SipCall bobCall = bobPhone.createSipCall();
        bobCall.initiateOutgoingCall(bobContact, "sip:1111@127.0.0.1:5080", null, body, "application", "sdp", null, null);
        assertTrue(bobCall.waitForAuthorisation(5000));
        assertLastOperationSuccess(bobCall);
        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        final int response = bobCall.getLastReceivedResponse().getStatusCode();
        assertTrue(response == Response.TRYING || response == Response.RINGING);
        logger.info("Last response: "+response);

        if (response == Response.TRYING) {
            assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
            assertEquals(Response.RINGING, bobCall.getLastReceivedResponse().getStatusCode());
            logger.info("Last response: "+bobCall.getLastReceivedResponse().getStatusCode());
        }

        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        assertEquals(Response.OK, bobCall.getLastReceivedResponse().getStatusCode());
        String callSid = bobCall.getLastReceivedResponse().getMessage().getHeader("X-RestComm-CallSid").toString().split(":")[1].trim();
        assertTrue(bobCall.sendInviteOkAck());

//
//
//        String from = "+15126002188";
//        String to = bobContact;
//        String rcmlUrl = "http://127.0.0.1:8090/1111";
//
//        JsonObject callResult = (JsonObject) RestcommCallsTool.getInstance().createCall(deploymentUrl.toString(), adminAccountSid,
//                adminAuthToken, from, to, rcmlUrl);
//        assertNotNull(callResult);
//        String callSid = callResult.get("sid").getAsString();
//
//        assertTrue(bobCall.waitForIncomingCall(5000));
//        String receivedBody = new String(bobCall.getLastReceivedRequest().getRawContent());
//        assertTrue(bobCall.sendIncomingCallResponse(Response.RINGING, "Ringing-Bob", 3600));
//        assertTrue(bobCall
//                .sendIncomingCallResponse(Response.OK, "OK-Bob", 3600, receivedBody, "application", "sdp", null, null));
//
//        assertTrue(bobCall.waitForAck(5000));
//
//        // Restcomm now should execute RCML that will create a call to +131313 (george's phone)
//
//        assertTrue(georgeCall.waitForIncomingCall(5000));
//        receivedBody = new String(georgeCall.getLastReceivedRequest().getRawContent());
//        assertTrue(georgeCall.sendIncomingCallResponse(Response.RINGING, "Ringing-George", 3600));
//        assertTrue(georgeCall.sendIncomingCallResponse(Response.OK, "OK-George", 3600, receivedBody, "application", "sdp",
//                null, null));
//
//        assertTrue(georgeCall.waitForAck(5000));

        Thread.sleep(10000);
        System.out.println("\n ******************** \nAbout to redirect the call\n ********************\n");
        String rcmlUrl = deploymentUrl.toString() + "/dial-client-entry.xml";

        JsonObject callResult = RestcommCallsTool.getInstance().modifyCall(deploymentUrl.toString(), adminAccountSid, adminAuthToken,
                callSid, null, rcmlUrl);

//        georgeCall.listenForDisconnect();
//        assertTrue(georgeCall.waitForDisconnect(10000));
//        assertTrue(georgeCall.respondToDisconnect());

        // Restcomm now should execute the new RCML and create a call to Alice Restcomm client
        // TODO: This test is expected to fail because of issue https://bitbucket.org/telestax/telscale-restcomm/issue/192
        assertTrue(aliceCall.waitForIncomingCall(5000));
        String receivedBody = new String(aliceCall.getLastReceivedRequest().getRawContent());
        assertTrue(aliceCall.sendIncomingCallResponse(Response.RINGING, "Ringing-Alice", 3600));
        assertTrue(aliceCall.sendIncomingCallResponse(Response.OK, "OK-Alice", 3600, receivedBody, "application", "sdp", null,
                null));

        Thread.sleep(3000);

        aliceCall.listenForDisconnect();
        bobCall.listenForDisconnect();

        assertTrue(aliceCall.disconnect());
        assertTrue(aliceCall.waitForAck(5000));

        assertTrue(bobCall.waitForDisconnect(5000));
        assertTrue(bobCall.respondToDisconnect());

        JsonObject metrics = MonitoringServiceTool.getInstance().getMetrics(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
        assertNotNull(metrics);
        int liveCalls = metrics.getAsJsonObject("Metrics").get("LiveCalls").getAsInt();
        logger.info("LiveCalls: "+liveCalls);
        int liveCallsArraySize = metrics.getAsJsonArray("LiveCallDetails").size();
        logger.info("LiveCallsArraySize: "+liveCallsArraySize);
        assertEquals(0,liveCalls);
        assertEquals(0, liveCallsArraySize);

        Map<String, Integer> mgcpResources = MonitoringServiceTool.getInstance().getMgcpResources(metrics);
        int mgcpEndpoints = mgcpResources.get("MgcpEndpoints");
        int mgcpConnections = mgcpResources.get("MgcpConnections");

        assertEquals(0, mgcpEndpoints);
        assertEquals(0, mgcpConnections);

    }


    private String dialAlice = "<Response><Dial><Client>alice</Client></Dial></Response>";
    private String confUnHold = "<Response>\n" +
            "        <Dial>\n" +
            "                <Conference startConferenceOnEnter=\"true\">HoldConf1234</Conference>\n" +
            "        </Dial>\n" +
            "</Response>";
    private String confHold = "<Response>\n" +
            "\t<Dial>\n" +
            "\t\t<Conference startConferenceOnEnter=\"false\" waitUrl=\"/restcomm/music/rock/nickleus_-_original_guitar_song_200907251723.wav\">HoldConf1234</Conference>\n" +
            "\t</Dial>\n" +
            "</Response>";
    @Test
    @Category(BrokenTests.class)
    @Ignore
    public void holdCall() throws Exception {
        stubFor(get(urlPathEqualTo("/1111"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(dialAlice)));

        stubFor(post(urlPathEqualTo("/hold"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(confHold)));

        stubFor(post(urlPathEqualTo("/unhold"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(confUnHold)));

        Credential c = new Credential("127.0.0.1", "bob", "1234");
        bobPhone.addUpdateCredential(c);

        SipURI uri = aliceSipStack.getAddressFactory().createSipURI(null, "127.0.0.1:5080");
        assertTrue(alicePhone.register(uri, "alice", "1234", aliceContact, 3600, 3600));

        SipCall aliceCall = alicePhone.createSipCall();
        aliceCall.listenForIncomingCall();

        final SipCall bobCall = bobPhone.createSipCall();
        bobCall.initiateOutgoingCall(bobContact, "sip:1111@127.0.0.1:5080", null, body, "application", "sdp", null, null);
        assertTrue(bobCall.waitForAuthorisation(5000));
        assertLastOperationSuccess(bobCall);
        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        final int response = bobCall.getLastReceivedResponse().getStatusCode();
        assertTrue(response == Response.TRYING || response == Response.RINGING);
        logger.info("Last response: "+response);

        if (response == Response.TRYING) {
            assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
            assertEquals(Response.RINGING, bobCall.getLastReceivedResponse().getStatusCode());
            logger.info("Last response: "+bobCall.getLastReceivedResponse().getStatusCode());
        }

        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        assertEquals(Response.OK, bobCall.getLastReceivedResponse().getStatusCode());
        String callSid = bobCall.getLastReceivedResponse().getMessage().getHeader("X-RestComm-CallSid").toString().split(":")[1].trim();
        assertTrue(bobCall.sendInviteOkAck());

        assertTrue(aliceCall.waitForIncomingCall(5000));
        String receivedBody = new String(aliceCall.getLastReceivedRequest().getRawContent());
        assertTrue(aliceCall.sendIncomingCallResponse(Response.RINGING, "Ringing-Alice", 3600));
        assertTrue(aliceCall.sendIncomingCallResponse(Response.OK, "OK-Alice", 3600, receivedBody,
                "application", "sdp", null, null));


        Thread.sleep(10000);
        System.out.println("\n ******************** \nAbout to put call on hold\n ********************\n");
        String rcmlUrl = "http://127.0.0.1:8090/hold";

        JsonObject callResult = RestcommCallsTool.getInstance().modifyCall(deploymentUrl.toString(), adminAccountSid, adminAuthToken,
                callSid, null, rcmlUrl, true);

        Thread.sleep(10000);

        JsonObject confObject = RestcommConferenceTool.getInstance().getConferences(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        assertNotNull(confObject);
        JsonArray confArray = confObject.getAsJsonArray("conferences");
        assertNotNull(confArray);
        String confSid = confArray.get(0).getAsJsonObject().get("sid").getAsString();
        assertNotNull(confSid);
        JsonObject partObject = RestcommConferenceParticipantsTool.getInstance().getParticipants(deploymentUrl.toString(), adminAccountSid, adminAuthToken, confSid);
        assertNotNull(partObject);
        JsonArray callsArray = partObject.getAsJsonArray("calls");
        int size = callsArray.size();
        assertEquals(2, size);

        Thread.sleep(10000);
        System.out.println("\n ******************** \nAbout to unhold calls\n ********************\n");
        rcmlUrl = "http://127.0.0.1:8090/unhold";

        callResult = RestcommCallsTool.getInstance().modifyCall(deploymentUrl.toString(), adminAccountSid, adminAuthToken,
                callSid, null, rcmlUrl, true);

        Thread.sleep(2000);

        partObject = RestcommConferenceParticipantsTool.getInstance().getParticipants(deploymentUrl.toString(), adminAccountSid, adminAuthToken, confSid);
        assertNotNull(partObject);
        callsArray = partObject.getAsJsonArray("calls");
        size = callsArray.size();
        assertEquals(2, size);

        Thread.sleep(2000);

        bobCall.disconnect();

        aliceCall.disconnect();

        Thread.sleep(1000);

        partObject = RestcommConferenceParticipantsTool.getInstance().getParticipants(deploymentUrl.toString(), adminAccountSid, adminAuthToken, confSid);
        assertNotNull(partObject);
        callsArray = partObject.getAsJsonArray("calls");
        size = callsArray.size();
        assertEquals(0, size);

        JsonObject metrics = MonitoringServiceTool.getInstance().getMetrics(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
        assertNotNull(metrics);
        int liveCalls = metrics.getAsJsonObject("Metrics").get("LiveCalls").getAsInt();
        logger.info("LiveCalls: "+liveCalls);
        int liveCallsArraySize = metrics.getAsJsonArray("LiveCallDetails").size();
        logger.info("LiveCallsArraySize: "+liveCallsArraySize);
        assertEquals(0,liveCalls);
        assertEquals(0, liveCallsArraySize);

        Map<String, Integer> mgcpResources = MonitoringServiceTool.getInstance().getMgcpResources(metrics);
        int mgcpEndpoints = mgcpResources.get("MgcpEndpoints");
        int mgcpConnections = mgcpResources.get("MgcpConnections");

        assertEquals(0, mgcpEndpoints);
        assertEquals(0, mgcpConnections);
    }

    @Test
    // Redirect a call to a different URL using the Live Call Modification API. Non-regression test for issue:
    // https://bitbucket.org/telestax/telscale-restcomm/issue/139
    public void redirectCallInvalidCallSid() throws Exception {

        SipCall bobCall = bobPhone.createSipCall();
        bobCall.listenForIncomingCall();

        SipCall georgeCall = georgePhone.createSipCall();
        georgeCall.listenForIncomingCall();

        // Register Alice Restcomm client
        SipURI uri = aliceSipStack.getAddressFactory().createSipURI(null, "127.0.0.1:5080");
        assertTrue(alicePhone.register(uri, "alice", "1234", aliceContact, 3600, 3600));

        SipCall aliceCall = alicePhone.createSipCall();
        aliceCall.listenForIncomingCall();

        String from = "+15126002188";
        String to = bobContact;
        String rcmlUrl = deploymentUrl.toString() + "/dial-number-entry.xml";

        JsonObject callResult = (JsonObject) RestcommCallsTool.getInstance().createCall(deploymentUrl.toString(), adminAccountSid,
                adminAuthToken, from, to, rcmlUrl);
        assertNotNull(callResult);
        String callSid = callResult.get("sid").getAsString();

        assertTrue(bobCall.waitForIncomingCall(5000));
        String receivedBody = new String(bobCall.getLastReceivedRequest().getRawContent());
        assertTrue(bobCall.sendIncomingCallResponse(Response.RINGING, "Ringing-Bob", 3600));
        assertTrue(bobCall
                .sendIncomingCallResponse(Response.OK, "OK-Bob", 3600, receivedBody, "application", "sdp", null, null));

        assertTrue(bobCall.waitForAck(5000));

        // Restcomm now should execute RCML that will create a call to +131313 (george's phone)

        assertTrue(georgeCall.waitForIncomingCall(5000));
        receivedBody = new String(georgeCall.getLastReceivedRequest().getRawContent());
        assertTrue(georgeCall.sendIncomingCallResponse(Response.RINGING, "Ringing-George", 3600));
        assertTrue(georgeCall.sendIncomingCallResponse(Response.OK, "OK-George", 3600, receivedBody, "application", "sdp",
                null, null));

        assertTrue(georgeCall.waitForAck(5000));

        Thread.sleep(10000);
        System.out.println("\n ******************** \nAbout to redirect the call\n ********************\n");
        rcmlUrl = deploymentUrl.toString() + "/dial-client-entry.xml";

        //String invalidCallSid = Sid.generate(Sid.Type.CALL).toString();
        // restcomm-connect/1907
        String invalidCallSid = "IDaaaaaaaaaaabbbbbbbbbbbcccccccddd-CAaaaaaabbbbbcccccdddddeeeeeffffgg";

        callResult = RestcommCallsTool.getInstance().modifyCall(deploymentUrl.toString(), adminAccountSid, adminAuthToken,
                invalidCallSid, null, rcmlUrl);

        assertNotNull(callResult);
        String exc = callResult.get("Exception").getAsString();
        assertTrue(callResult.get("Exception").getAsString().equals("406"));

        georgeCall.listenForDisconnect();
        assertTrue(bobCall.disconnect());

        assertTrue(georgeCall.waitForDisconnect(10000));
        assertTrue(georgeCall.respondToDisconnect());

        JsonObject metrics = MonitoringServiceTool.getInstance().getMetrics(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
        assertNotNull(metrics);
        int liveCalls = metrics.getAsJsonObject("Metrics").get("LiveCalls").getAsInt();
        logger.info("LiveCalls: "+liveCalls);
        int liveCallsArraySize = metrics.getAsJsonArray("LiveCallDetails").size();
        logger.info("LiveCallsArraySize: "+liveCallsArraySize);
        assertEquals(0,liveCalls);
        assertEquals(0, liveCallsArraySize);

        Map<String, Integer> mgcpResources = MonitoringServiceTool.getInstance().getMgcpResources(metrics);
        int mgcpEndpoints = mgcpResources.get("MgcpEndpoints");
        int mgcpConnections = mgcpResources.get("MgcpConnections");

        assertEquals(0, mgcpEndpoints);
        assertEquals(0, mgcpConnections);
    }

    private String dialFork = "<Response><Dial record=\"true\" timeout=\"150\" action=\"/completed\"><Client>alice</Client><Number>+131313</Number></Dial></Response>";
    @Test
    public void testTerminateDialForkCallWhileRinging_LCM_to_dial_branches() throws Exception {
        stubFor(get(urlPathEqualTo("/1111"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(dialFork)));

        stubFor(post(urlPathEqualTo("/completed"))
                .willReturn(aResponse()
                        .withStatus(200)));

        // Register Alice
        SipURI uri = aliceSipStack.getAddressFactory().createSipURI(null, "127.0.0.1:5080");
        assertTrue(alicePhone.register(uri, "alice", "1234", aliceContact, 3600, 3600));

        // Prepare Alice to receive call
        final SipCall aliceCall = alicePhone.createSipCall();
        aliceCall.listenForIncomingCall();

        // Prepare George phone to receive call
        final SipCall georgeCall = georgePhone.createSipCall();
        georgeCall.listenForIncomingCall();

        Credential c = new Credential("127.0.0.1", "bob", "1234");
        bobPhone.addUpdateCredential(c);

        // Initiate a call using Bob
        final SipCall bobCall = bobPhone.createSipCall();

        bobCall.initiateOutgoingCall(bobContact, "sip:1111@127.0.0.1:5080", null, body, "application", "sdp", null, null);
        assertLastOperationSuccess(bobCall);
        assertTrue(bobCall.waitForAuthorisation(5000));
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
        String callSid = bobCall.getLastReceivedResponse().getMessage().getHeader("X-RestComm-CallSid").toString().split(":")[1].trim();
        assertTrue(!(bobCall.getLastReceivedResponse().getStatusCode() >= 400));

        assertTrue(georgeCall.waitForIncomingCall(30 * 1000));
        assertTrue(georgeCall.sendIncomingCallResponse(Response.TRYING, "Trying-George", 3600));
        assertTrue(georgeCall.sendIncomingCallResponse(Response.RINGING, "Ringing-George", 3600));
        String georgeCallSid = georgeCall.getLastReceivedRequest().getMessage().getHeader("X-RestComm-CallSid").toString().split(":")[1].trim();

        assertTrue(aliceCall.waitForIncomingCall(30 * 1000));
        assertTrue(aliceCall.sendIncomingCallResponse(Response.TRYING, "Trying-Alice", 3600));
        assertTrue(aliceCall.sendIncomingCallResponse(Response.RINGING, "Ringing-Alice", 3600));
        String aliceCallSid = aliceCall.getLastReceivedRequest().getMessage().getHeader("X-RestComm-CallSid").toString().split(":")[1].trim();

        georgeCall.listenForCancel();
        aliceCall.listenForCancel();

        //LCM request to terminate RINGING calls
        JsonObject callResult = RestcommCallsTool.getInstance().modifyCall(deploymentUrl.toString(), adminAccountSid, adminAuthToken, aliceCallSid, "canceled", null);
        assertNotNull(callResult);
        callResult = RestcommCallsTool.getInstance().modifyCall(deploymentUrl.toString(), adminAccountSid, adminAuthToken, georgeCallSid, "canceled", null);
        assertNotNull(callResult);

        SipTransaction georgeCancelTransaction = georgeCall.waitForCancel(30000);
        SipTransaction aliceCancelTransaction = aliceCall.waitForCancel(30000);
        assertNotNull(aliceCancelTransaction);
        assertNotNull(georgeCancelTransaction);

        georgeCall.respondToCancel(georgeCancelTransaction, 200, "OK-2-Cancel-George", 3600);
        aliceCall.respondToCancel(aliceCancelTransaction, 200, "OK-2-Cancel-Alice", 3600);

        bobCall.disconnect();

        //Wait to cancel the other branches
        Thread.sleep(3000);

        JsonObject metrics = MonitoringServiceTool.getInstance().getMetrics(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
        assertNotNull(metrics);
        int liveCalls = metrics.getAsJsonObject("Metrics").get("LiveCalls").getAsInt();
        logger.info("LiveCalls: "+liveCalls);
        int liveCallsArraySize = metrics.getAsJsonArray("LiveCallDetails").size();
        logger.info("LiveCallsArraySize: "+liveCallsArraySize);
        assertEquals(0,liveCalls);
        assertEquals(0, liveCallsArraySize);

        Map<String, Integer> mgcpResources = MonitoringServiceTool.getInstance().getMgcpResources(metrics);
        int mgcpEndpoints = mgcpResources.get("MgcpEndpoints");
        int mgcpConnections = mgcpResources.get("MgcpConnections");

        assertEquals(0, mgcpEndpoints);
        assertEquals(0, mgcpConnections);
    }

    @Test
    public void testTerminateDialForkCallWhileRinging_LCM_to_initial_call() throws Exception {
        stubFor(get(urlPathEqualTo("/1111"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(dialFork)));

        stubFor(post(urlPathEqualTo("/completed"))
                .willReturn(aResponse()
                        .withStatus(200)));

        // Register Alice
        SipURI uri = aliceSipStack.getAddressFactory().createSipURI(null, "127.0.0.1:5080");
        assertTrue(alicePhone.register(uri, "alice", "1234", aliceContact, 3600, 3600));

        // Prepare Alice to receive call
        final SipCall aliceCall = alicePhone.createSipCall();
        aliceCall.listenForIncomingCall();

        // Prepare George phone to receive call
        final SipCall georgeCall = georgePhone.createSipCall();
        georgeCall.listenForIncomingCall();

        Credential c = new Credential("127.0.0.1", "bob", "1234");
        bobPhone.addUpdateCredential(c);

        // Initiate a call using Bob
        final SipCall bobCall = bobPhone.createSipCall();

        bobCall.initiateOutgoingCall(bobContact, "sip:1111@127.0.0.1:5080", null, body, "application", "sdp", null, null);
        assertLastOperationSuccess(bobCall);
        assertTrue(bobCall.waitForAuthorisation(5000));
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
        String callSid = bobCall.getLastReceivedResponse().getMessage().getHeader("X-RestComm-CallSid").toString().split(":")[1].trim();
        assertTrue(!(bobCall.getLastReceivedResponse().getStatusCode() >= 400));

        assertTrue(georgeCall.waitForIncomingCall(30 * 1000));
        assertTrue(georgeCall.sendIncomingCallResponse(Response.TRYING, "Trying-George", 3600));
        assertTrue(georgeCall.sendIncomingCallResponse(Response.RINGING, "Ringing-George", 3600));
        String georgeCallSid = georgeCall.getLastReceivedRequest().getMessage().getHeader("X-RestComm-CallSid").toString().split(":")[1].trim();

        assertTrue(aliceCall.waitForIncomingCall(30 * 1000));
        assertTrue(aliceCall.sendIncomingCallResponse(Response.TRYING, "Trying-Alice", 3600));
        assertTrue(aliceCall.sendIncomingCallResponse(Response.RINGING, "Ringing-Alice", 3600));
        String aliceCallSid = aliceCall.getLastReceivedRequest().getMessage().getHeader("X-RestComm-CallSid").toString().split(":")[1].trim();

        georgeCall.listenForCancel();
        aliceCall.listenForCancel();

        //LCM request to terminate the initial call which is in-progress
        JsonObject callResult = RestcommCallsTool.getInstance().modifyCall(deploymentUrl.toString(), adminAccountSid, adminAuthToken,
                callSid, "completed", null);
        assertNotNull(callResult);

        SipTransaction georgeCancelTransaction = georgeCall.waitForCancel(30000);
        SipTransaction aliceCancelTransaction = aliceCall.waitForCancel(30000);
        assertNotNull(aliceCancelTransaction);
        assertNotNull(georgeCancelTransaction);

        georgeCall.respondToCancel(georgeCancelTransaction, 200, "OK-2-Cancel-George", 3600);
        aliceCall.respondToCancel(aliceCancelTransaction, 200, "OK-2-Cancel-Alice", 3600);

        //Wait to cancel the other branches
        Thread.sleep(3000);

        JsonObject metrics = MonitoringServiceTool.getInstance().getMetrics(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
        assertNotNull(metrics);
        int liveCalls = metrics.getAsJsonObject("Metrics").get("LiveCalls").getAsInt();
        logger.info("LiveCalls: "+liveCalls);
        int liveCallsArraySize = metrics.getAsJsonArray("LiveCallDetails").size();
        logger.info("LiveCallsArraySize: "+liveCallsArraySize);
        assertEquals(0,liveCalls);
        assertEquals(0, liveCallsArraySize);

        Map<String, Integer> mgcpResources = MonitoringServiceTool.getInstance().getMgcpResources(metrics);
        int mgcpEndpoints = mgcpResources.get("MgcpEndpoints");
        int mgcpConnections = mgcpResources.get("MgcpConnections");

        assertEquals(0, mgcpEndpoints);
        assertEquals(0, mgcpConnections);
    }

    private String hangupRcml = "<Response><Hangup></Hangup></Response>";

    @Test
    public void testTerminateDialForkCallWhileRinging_LCM_to_move_initial_call_to_hangup_rcml() throws Exception {
        stubFor(get(urlPathEqualTo("/1111"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(dialFork)));

        stubFor(post(urlPathEqualTo("/completed"))
                .willReturn(aResponse()
                        .withStatus(200)));

        stubFor(post(urlPathEqualTo("/hangupRcml"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(hangupRcml)));

        // Register Alice
        SipURI uri = aliceSipStack.getAddressFactory().createSipURI(null, "127.0.0.1:5080");
        assertTrue(alicePhone.register(uri, "alice", "1234", aliceContact, 3600, 3600));

        // Prepare Alice to receive call
        final SipCall aliceCall = alicePhone.createSipCall();
        aliceCall.listenForIncomingCall();

        // Prepare George phone to receive call
        final SipCall georgeCall = georgePhone.createSipCall();
        georgeCall.listenForIncomingCall();

        Credential c = new Credential("127.0.0.1", "bob", "1234");
        bobPhone.addUpdateCredential(c);

        // Initiate a call using Bob
        final SipCall bobCall = bobPhone.createSipCall();

        bobCall.initiateOutgoingCall(bobContact, "sip:1111@127.0.0.1:5080", null, body, "application", "sdp", null, null);
        assertLastOperationSuccess(bobCall);
        assertTrue(bobCall.waitForAuthorisation(5000));
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
        String callSid = bobCall.getLastReceivedResponse().getMessage().getHeader("X-RestComm-CallSid").toString().split(":")[1].trim();
        assertTrue(!(bobCall.getLastReceivedResponse().getStatusCode() >= 400));

        assertTrue(georgeCall.waitForIncomingCall(30 * 1000));
        assertTrue(georgeCall.sendIncomingCallResponse(Response.TRYING, "Trying-George", 3600));
        assertTrue(georgeCall.sendIncomingCallResponse(Response.RINGING, "Ringing-George", 3600));
        String georgeCallSid = georgeCall.getLastReceivedRequest().getMessage().getHeader("X-RestComm-CallSid").toString().split(":")[1].trim();

        assertTrue(aliceCall.waitForIncomingCall(30 * 1000));
        assertTrue(aliceCall.sendIncomingCallResponse(Response.TRYING, "Trying-Alice", 3600));
        assertTrue(aliceCall.sendIncomingCallResponse(Response.RINGING, "Ringing-Alice", 3600));
        String aliceCallSid = aliceCall.getLastReceivedRequest().getMessage().getHeader("X-RestComm-CallSid").toString().split(":")[1].trim();

        georgeCall.listenForCancel();
        aliceCall.listenForCancel();

        //LCM request to move initial call to Hangup RCML
        String hangupUrl = "http://127.0.0.1:8090/hangupRcml";
        JsonObject callResult = RestcommCallsTool.getInstance().modifyCall(deploymentUrl.toString(), adminAccountSid, adminAuthToken,
                callSid, null, hangupUrl);
        assertNotNull(callResult);

        SipTransaction georgeCancelTransaction = georgeCall.waitForCancel(30000);
        SipTransaction aliceCancelTransaction = aliceCall.waitForCancel(30000);
        assertNotNull(aliceCancelTransaction);
        assertNotNull(georgeCancelTransaction);

        georgeCall.respondToCancel(georgeCancelTransaction, 200, "OK-2-Cancel-George", 3600);
        aliceCall.respondToCancel(aliceCancelTransaction, 200, "OK-2-Cancel-Alice", 3600);

        //Wait to cancel the other branches
        Thread.sleep(3000);

        JsonObject metrics = MonitoringServiceTool.getInstance().getMetrics(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
        assertNotNull(metrics);
        int liveCalls = metrics.getAsJsonObject("Metrics").get("LiveCalls").getAsInt();
        logger.info("LiveCalls: "+liveCalls);
        int liveCallsArraySize = metrics.getAsJsonArray("LiveCallDetails").size();
        logger.info("LiveCallsArraySize: "+liveCallsArraySize);
        assertEquals(0,liveCalls);
        assertEquals(0, liveCallsArraySize);

        Map<String, Integer> mgcpResources = MonitoringServiceTool.getInstance().getMgcpResources(metrics);
        int mgcpEndpoints = mgcpResources.get("MgcpEndpoints");
        int mgcpConnections = mgcpResources.get("MgcpConnections");

        assertEquals(0, mgcpEndpoints);
        assertEquals(0, mgcpConnections);
    }

    @Deployment(name = "LiveCallModificationTest", managed = true, testable = false)
    public static WebArchive createWebArchiveNoGw() {
        logger.info("Packaging Test App");
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
        archive.addAsWebInfResource("restcomm.xml", "conf/restcomm.xml");
        archive.addAsWebInfResource("restcomm.script_dialTest", "data/hsql/restcomm.script");
        archive.addAsWebResource("dial-number-entry.xml");
        archive.addAsWebResource("dial-client-entry.xml");
        archive.addAsWebResource("hello-play.xml");
        logger.info("Packaged Test App");
        return archive;
    }

}
