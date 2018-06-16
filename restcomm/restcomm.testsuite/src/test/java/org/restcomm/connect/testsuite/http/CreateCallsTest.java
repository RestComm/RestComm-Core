package org.restcomm.connect.testsuite.http;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.net.URL;
import java.text.ParseException;

import javax.sip.address.SipURI;
import javax.sip.header.FromHeader;
import javax.sip.message.Response;

import gov.nist.javax.sip.header.HeaderExt;
import org.apache.log4j.Logger;
import org.cafesip.sipunit.Credential;
import org.cafesip.sipunit.SipCall;
import org.cafesip.sipunit.SipPhone;
import org.cafesip.sipunit.SipRequest;
import org.cafesip.sipunit.SipStack;
import org.cafesip.sipunit.SipTransaction;
import org.jboss.arquillian.container.mss.extension.SipStackTool;
import org.jboss.arquillian.container.test.api.Deployer;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
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
import org.restcomm.connect.commons.annotations.FeatureExpTests;
import org.restcomm.connect.commons.annotations.UnstableTests;
import org.restcomm.connect.testsuite.tools.MonitoringServiceTool;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * @author <a href="mailto:gvagenas@gmail.com">gvagenas</a>
 */
@RunWith(Arquillian.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class CreateCallsTest {

    private final static Logger logger = Logger.getLogger(CreateCallsTest.class.getName());

    private static final String version = Version.getVersion();

    @ArquillianResource
    private Deployer deployer;
    @ArquillianResource
    URL deploymentUrl;

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(8090); // No-args constructor defaults to port 8080

    private String adminAccountSid = "ACae6e420f425248d6a26948c17a9e2acf";
    private String adminAuthToken = "77f8c12cc7b8f8423e5c38b035249166";

    private static SipStackTool tool1;
    private static SipStackTool tool2;
    private static SipStackTool tool3;
    private static SipStackTool tool4;

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

    private SipStack alice2SipStack;
    private SipPhone alice2Phone;
    private String alice2Contact = "sip:alice@127.0.0.1:5092";

    @BeforeClass
    public static void beforeClass() throws Exception {
        tool1 = new SipStackTool("CreateCalls1");
        tool2 = new SipStackTool("CreateCalls2");
        tool3 = new SipStackTool("CreateCalls3");
        tool4 = new SipStackTool("CreateCalls4");
    }

    @Before
    public void before() throws Exception {
        bobSipStack = tool1.initializeSipStack(SipStack.PROTOCOL_UDP, "127.0.0.1", "5090", "127.0.0.1:5080");
        bobPhone = bobSipStack.createSipPhone("127.0.0.1", SipStack.PROTOCOL_UDP, 5080, bobContact);

        georgeSipStack = tool2.initializeSipStack(SipStack.PROTOCOL_UDP, "127.0.0.1", "5070", "127.0.0.1:5080");
        georgePhone = georgeSipStack.createSipPhone("127.0.0.1", SipStack.PROTOCOL_UDP, 5080, georgeContact);

        aliceSipStack = tool3.initializeSipStack(SipStack.PROTOCOL_UDP, "127.0.0.1", "5091", "127.0.0.1:5080");
        alicePhone = aliceSipStack.createSipPhone("127.0.0.1", SipStack.PROTOCOL_UDP, 5080, aliceContact);

        alice2SipStack = tool4.initializeSipStack(SipStack.PROTOCOL_UDP, "127.0.0.1", "5092", "127.0.0.1:5080");
        alice2Phone = alice2SipStack.createSipPhone("127.0.0.1", SipStack.PROTOCOL_UDP, 5080, alice2Contact);
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

        if (alicePhone != null) {
            alicePhone.dispose();
        }
        if (aliceSipStack != null) {
            aliceSipStack.dispose();
        }

        if (alice2Phone != null) {
            alice2Phone.dispose();
        }
        if (alice2SipStack != null) {
            alice2SipStack.dispose();
        }

        Thread.sleep(1000);
    }

    @Test
//    @Category(UnstableTests.class)
    // Create a call to a SIP URI. Non-regression test for issue https://bitbucket.org/telestax/telscale-restcomm/issue/175
    // Use Calls Rest API to dial Bob (SIP URI sip:bob@127.0.0.1:5090) and connect him to the RCML app dial-number-entry.xml.
    // This RCML will dial +131313 which George's phone is listening (use the dial-number-entry.xml as a side effect to verify
    // that the call created successfully)
    public void createCallSipUriTest() throws InterruptedException {

        SipCall bobCall = bobPhone.createSipCall();
        bobCall.listenForIncomingCall();

        SipCall georgeCall = georgePhone.createSipCall();
        georgeCall.listenForIncomingCall();

        String from = "+15126002188";
        String to = bobContact;
        String rcmlUrl = deploymentUrl.toString() + "/dial-number-entry.xml";

        JsonElement callResult = RestcommCallsTool.getInstance().createCall(deploymentUrl.toString(), adminAccountSid,
                adminAuthToken, from, to, rcmlUrl);
        assertNotNull(callResult);

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

        Thread.sleep(3000);

        bobCall.listenForDisconnect();

        assertTrue(georgeCall.disconnect());
        assertTrue(georgeCall.waitForAck(5000));

        assertTrue(bobCall.waitForDisconnect(5000));
        assertTrue(bobCall.respondToDisconnect());

        Thread.sleep(3000);

        JsonObject metrics = MonitoringServiceTool.getInstance().getMetrics(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
        assertNotNull(metrics);
        int liveCalls = metrics.getAsJsonObject("Metrics").get("LiveCalls").getAsInt();
        logger.info("LiveCalls: "+liveCalls);
        int liveCallsArraySize = metrics.getAsJsonArray("LiveCallDetails").size();
        logger.info("LiveCallsArraySize: "+liveCallsArraySize);
        assertEquals(0, liveCalls);
        assertEquals(0, liveCallsArraySize);
        int maxConcurrentCalls = metrics.getAsJsonObject("Metrics").get("MaximumConcurrentCalls").getAsInt();
        int maxConcurrentIncomingCalls = metrics.getAsJsonObject("Metrics").get("MaximumConcurrentIncomingCalls").getAsInt();
        int maxConcurrentOutgoingCalls = metrics.getAsJsonObject("Metrics").get("MaximumConcurrentOutgoingCalls").getAsInt();
        assertEquals(2, maxConcurrentCalls);
        assertEquals(0,maxConcurrentIncomingCalls);
        assertEquals(2,maxConcurrentOutgoingCalls);
    }

    @Test @Category(FeatureAltTests.class)
    public void createCallSipUriWithCustomHeadersTest() throws InterruptedException {

        SipCall bobCall = bobPhone.createSipCall();
        bobCall.listenForIncomingCall();

        SipCall georgeCall = georgePhone.createSipCall();
        georgeCall.listenForIncomingCall();

        String from = "+15126002188";
        String to = bobContact+"?X-Custom-Header1=1234&X-Custom-Header2=4321";
        String rcmlUrl = deploymentUrl.toString() + "/dial-number-entry.xml";

        JsonElement callResult = RestcommCallsTool.getInstance().createCall(deploymentUrl.toString(), adminAccountSid,
                adminAuthToken, from, to, rcmlUrl);
        assertNotNull(callResult);

        assertTrue(bobCall.waitForIncomingCall(5000));

        //Check custom headers
        SipRequest invite = bobCall.getLastReceivedRequest();
        assertEquals(SipRequest.INVITE, invite.getRequestEvent().getRequest().getMethod());

        String customHeader1 = ((HeaderExt) invite.getRequestEvent().getRequest().getHeader("X-Custom-Header1")).getValue();
        String customHeader2 = ((HeaderExt) invite.getRequestEvent().getRequest().getHeader("X-Custom-Header2")).getValue();
        assertEquals("1234", customHeader1);
        assertEquals("4321", customHeader2);

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

        Thread.sleep(3000);

        bobCall.listenForDisconnect();

        assertTrue(georgeCall.disconnect());
        assertTrue(georgeCall.waitForAck(5000));

        assertTrue(bobCall.waitForDisconnect(5000));
        assertTrue(bobCall.respondToDisconnect());

        Thread.sleep(3000);

        JsonObject metrics = MonitoringServiceTool.getInstance().getMetrics(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
        assertNotNull(metrics);
        int liveCalls = metrics.getAsJsonObject("Metrics").get("LiveCalls").getAsInt();
        logger.info("LiveCalls: "+liveCalls);
        int liveCallsArraySize = metrics.getAsJsonArray("LiveCallDetails").size();
        logger.info("LiveCallsArraySize: "+liveCallsArraySize);
        assertEquals(0, liveCalls);
        assertEquals(0, liveCallsArraySize);
        int maxConcurrentCalls = metrics.getAsJsonObject("Metrics").get("MaximumConcurrentCalls").getAsInt();
        int maxConcurrentIncomingCalls = metrics.getAsJsonObject("Metrics").get("MaximumConcurrentIncomingCalls").getAsInt();
        int maxConcurrentOutgoingCalls = metrics.getAsJsonObject("Metrics").get("MaximumConcurrentOutgoingCalls").getAsInt();
        assertEquals(2, maxConcurrentCalls);
        assertEquals(0,maxConcurrentIncomingCalls);
        assertEquals(2,maxConcurrentOutgoingCalls);
    }

    @Test
//    @Category({FeatureAltTests.class, UnstableTests.class})
    @Category(FeatureAltTests.class)
    // Create a call to a SIP URI. Non-regression test for issue https://github.com/Mobicents/RestComm/issues/150
    // Use Calls Rest API to dial Bob (SIP URI sip:bob@127.0.0.1:5090) and connect him to the RCML app dial-number-entry.xml.
    // This RCML will dial +131313 which George's phone is listening (use the dial-number-entry.xml as a side effect to verify
    // that the call created successfully)
    public void createCallSipUriAllowFromModificationTest() throws InterruptedException {

        SipCall bobCall = bobPhone.createSipCall();
        bobCall.listenForIncomingCall();

        SipCall georgeCall = georgePhone.createSipCall();
        georgeCall.listenForIncomingCall();

        String from = "sip:+15126002188@mobicents.org";
        String to = bobContact;
        String rcmlUrl = deploymentUrl.toString() + "/dial-number-entry.xml";

        JsonElement callResult = RestcommCallsTool.getInstance().createCall(deploymentUrl.toString(), adminAccountSid,
                adminAuthToken, from, to, rcmlUrl);
        assertNotNull(callResult);

        assertTrue(bobCall.waitForIncomingCall(5000));
        FromHeader fromHeader = (FromHeader) bobCall.getLastReceivedRequest().getRequestEvent().getRequest().getHeader(FromHeader.NAME);
        assertNotNull(fromHeader);
//        System.out.println(fromHeader);
        assertEquals(from, fromHeader.getAddress().getURI().toString().trim());
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

        Thread.sleep(3000);

        bobCall.listenForDisconnect();

        assertTrue(georgeCall.disconnect());
        assertTrue(georgeCall.waitForAck(5000));

        assertTrue(bobCall.waitForDisconnect(5000));
        assertTrue(bobCall.respondToDisconnect());

        Thread.sleep(2000);

        JsonObject metrics = MonitoringServiceTool.getInstance().getMetrics(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
        assertNotNull(metrics);
        int liveCalls = metrics.getAsJsonObject("Metrics").get("LiveCalls").getAsInt();
        logger.info("LiveCalls: "+liveCalls);
        int liveCallsArraySize = metrics.getAsJsonArray("LiveCallDetails").size();
        logger.info("LiveCallsArraySize: "+liveCallsArraySize);
        assertEquals(0, liveCalls);
        assertEquals(0, liveCallsArraySize);
        int maxConcurrentCalls = metrics.getAsJsonObject("Metrics").get("MaximumConcurrentCalls").getAsInt();
        int maxConcurrentIncomingCalls = metrics.getAsJsonObject("Metrics").get("MaximumConcurrentIncomingCalls").getAsInt();
        int maxConcurrentOutgoingCalls = metrics.getAsJsonObject("Metrics").get("MaximumConcurrentOutgoingCalls").getAsInt();
        assertEquals(2, maxConcurrentCalls);
        assertEquals(0,maxConcurrentIncomingCalls);
        assertEquals(2,maxConcurrentOutgoingCalls);
    }

    @Test
//    @Category(UnstableTests.class)
    // Create a call to a Restcomm Client. Non-regression test for issue
    // https://bitbucket.org/telestax/telscale-restcomm/issue/175
    // Use Calls Rest API to dial Alice Restcomm client and connect him to the RCML app dial-number-entry.xml.
    // This RCML will dial +131313 which George's phone is listening (use the dial-number-entry.xml as a side effect to verify
    // that the call created successfully)
    public void createCallClientTest() throws InterruptedException, ParseException {

        SipCall georgeCall = georgePhone.createSipCall();
        georgeCall.listenForIncomingCall();

        // Register Alice Restcomm client
        SipURI uri = aliceSipStack.getAddressFactory().createSipURI(null, "127.0.0.1:5080");
        assertTrue(alicePhone.register(uri, "alice", "1234", aliceContact, 3600, 3600));

        SipCall aliceCall = alicePhone.createSipCall();
        aliceCall.listenForIncomingCall();

        String from = "+15126002188";
        String to = "client:alice";
        String rcmlUrl = deploymentUrl.toString() + "/dial-number-entry.xml";

        JsonElement callResult = RestcommCallsTool.getInstance().createCall(deploymentUrl.toString(), adminAccountSid,
                adminAuthToken, from, to, rcmlUrl);
        assertNotNull(callResult);

        assertTrue(aliceCall.waitForIncomingCall(5000));
        String receivedBody = new String(aliceCall.getLastReceivedRequest().getRawContent());
        assertTrue(aliceCall.sendIncomingCallResponse(Response.RINGING, "Ringing-Alice", 3600));
        assertTrue(aliceCall.sendIncomingCallResponse(Response.OK, "OK-Alice", 3600, receivedBody, "application", "sdp", null,
                null));

        // Restcomm now should execute RCML that will create a call to +131313 (george's phone)

        assertTrue(georgeCall.waitForIncomingCall(5000));
        receivedBody = new String(georgeCall.getLastReceivedRequest().getRawContent());
        assertTrue(georgeCall.sendIncomingCallResponse(Response.RINGING, "Ringing-George", 3600));
        assertTrue(georgeCall.sendIncomingCallResponse(Response.OK, "OK-George", 3600, receivedBody, "application", "sdp",
                null, null));

        Thread.sleep(3000);

        aliceCall.listenForDisconnect();

        assertTrue(georgeCall.disconnect());
        assertTrue(georgeCall.waitForAck(5000));

        assertTrue(aliceCall.waitForDisconnect(5000));
        assertTrue(aliceCall.respondToDisconnect());

        Thread.sleep(2000);

        JsonObject metrics = MonitoringServiceTool.getInstance().getMetrics(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
        assertNotNull(metrics);
        int liveCalls = metrics.getAsJsonObject("Metrics").get("LiveCalls").getAsInt();
        logger.info("LiveCalls: "+liveCalls);
        int liveCallsArraySize = metrics.getAsJsonArray("LiveCallDetails").size();
        logger.info("LiveCallsArraySize: "+liveCallsArraySize);
        assertEquals(0, liveCalls);
        assertEquals(0, liveCallsArraySize);
        int maxConcurrentCalls = metrics.getAsJsonObject("Metrics").get("MaximumConcurrentCalls").getAsInt();
        int maxConcurrentIncomingCalls = metrics.getAsJsonObject("Metrics").get("MaximumConcurrentIncomingCalls").getAsInt();
        int maxConcurrentOutgoingCalls = metrics.getAsJsonObject("Metrics").get("MaximumConcurrentOutgoingCalls").getAsInt();
        assertEquals(2, maxConcurrentCalls);
        assertEquals(0,maxConcurrentIncomingCalls);
        assertEquals(2,maxConcurrentOutgoingCalls);
    }

    @Test @Category(FeatureAltTests.class)
    public void createCallClientWithCustomHeadersTest() throws InterruptedException, ParseException {

        SipCall georgeCall = georgePhone.createSipCall();
        georgeCall.listenForIncomingCall();

        // Register Alice Restcomm client
        SipURI uri = aliceSipStack.getAddressFactory().createSipURI(null, "127.0.0.1:5080");
        assertTrue(alicePhone.register(uri, "alice", "1234", aliceContact, 3600, 3600));
        Credential c = new Credential("127.0.0.1", "alice", "1234");
        alicePhone.addUpdateCredential(c);

        SipCall aliceCall = alicePhone.createSipCall();
        aliceCall.listenForIncomingCall();

        String from = "+15126002188";
        String to = "client:alice?X-Custom-Header1=1234&X-Custom-Header2=4321";
        String rcmlUrl = deploymentUrl.toString() + "dial-number-entry.xml";

        JsonElement callResult = RestcommCallsTool.getInstance().createCall(deploymentUrl.toString(), adminAccountSid,
                adminAuthToken, from, to, rcmlUrl);
        assertNotNull(callResult);

        assertTrue(aliceCall.waitForIncomingCall(5000));

        SipRequest invite = aliceCall.getLastReceivedRequest();
        assertEquals(SipRequest.INVITE, invite.getRequestEvent().getRequest().getMethod());

        String customHeader1 = ((HeaderExt) invite.getRequestEvent().getRequest().getHeader("X-Custom-Header1")).getValue();
        String customHeader2 = ((HeaderExt) invite.getRequestEvent().getRequest().getHeader("X-Custom-Header2")).getValue();
        assertEquals("1234", customHeader1);
        assertEquals("4321", customHeader2);

        String receivedBody = new String(aliceCall.getLastReceivedRequest().getRawContent());
        assertTrue(aliceCall.sendIncomingCallResponse(Response.RINGING, "Ringing-Alice", 3600));
        assertTrue(aliceCall.sendIncomingCallResponse(Response.OK, "OK-Alice", 3600, receivedBody, "application", "sdp", null,
                null));

        // Restcomm now should execute RCML that will create a call to +131313 (george's phone)

        assertTrue(georgeCall.waitForIncomingCall(5000));
        receivedBody = new String(georgeCall.getLastReceivedRequest().getRawContent());
        assertTrue(georgeCall.sendIncomingCallResponse(Response.RINGING, "Ringing-George", 3600));
        assertTrue(georgeCall.sendIncomingCallResponse(Response.OK, "OK-George", 3600, receivedBody, "application", "sdp",
                null, null));

        Thread.sleep(3000);

        aliceCall.listenForDisconnect();

        assertTrue(georgeCall.disconnect());
        assertTrue(georgeCall.waitForAck(5000));

        assertTrue(aliceCall.waitForDisconnect(5000));
        assertTrue(aliceCall.respondToDisconnect());

        Thread.sleep(2000);

        JsonObject metrics = MonitoringServiceTool.getInstance().getMetrics(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
        assertNotNull(metrics);
        int liveCalls = metrics.getAsJsonObject("Metrics").get("LiveCalls").getAsInt();
        logger.info("LiveCalls: "+liveCalls);
        int liveCallsArraySize = metrics.getAsJsonArray("LiveCallDetails").size();
        logger.info("LiveCallsArraySize: "+liveCallsArraySize);
        assertEquals(0, liveCalls);
        assertEquals(0, liveCallsArraySize);
        int maxConcurrentCalls = metrics.getAsJsonObject("Metrics").get("MaximumConcurrentCalls").getAsInt();
        int maxConcurrentIncomingCalls = metrics.getAsJsonObject("Metrics").get("MaximumConcurrentIncomingCalls").getAsInt();
        int maxConcurrentOutgoingCalls = metrics.getAsJsonObject("Metrics").get("MaximumConcurrentOutgoingCalls").getAsInt();
        assertEquals(2, maxConcurrentCalls);
        assertEquals(0,maxConcurrentIncomingCalls);
        assertEquals(2,maxConcurrentOutgoingCalls);
    }

    @Test
    @Category({FeatureExpTests.class})
    // Create a call to a Restcomm Client for wrong RCML url
    public void createCallClientTestWrongRcmlUrl() throws InterruptedException, ParseException {

        SipCall georgeCall = georgePhone.createSipCall();
        georgeCall.listenForIncomingCall();

        // Register Alice Restcomm client
        SipURI uri = aliceSipStack.getAddressFactory().createSipURI(null, "127.0.0.1:5080");
        assertTrue(alicePhone.register(uri, "alice", "1234", aliceContact, 3600, 3600));

        SipCall aliceCall = alicePhone.createSipCall();
        aliceCall.listenForIncomingCall();

        String from = "+15126002188";
        String to = "client:alice";
        String rcmlUrl = "/restcomm/dial-number-entry.xml";

        JsonElement callResult = RestcommCallsTool.getInstance().createCall(deploymentUrl.toString(), adminAccountSid,
                adminAuthToken, from, to, rcmlUrl);
        assertNotNull(callResult);

        assertTrue(aliceCall.waitForIncomingCall(5000));
        String receivedBody = new String(aliceCall.getLastReceivedRequest().getRawContent());
        assertTrue(aliceCall.sendIncomingCallResponse(Response.RINGING, "Ringing-Alice", 3600));
        assertTrue(aliceCall.sendIncomingCallResponse(Response.OK, "OK-Alice", 3600, receivedBody, "application", "sdp", null,
                null));


        aliceCall.listenForDisconnect();
        assertTrue(aliceCall.waitForDisconnect(5000));
        assertTrue(aliceCall.respondToDisconnect());

        Thread.sleep(2000);

        JsonObject metrics = MonitoringServiceTool.getInstance().getMetrics(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
        assertNotNull(metrics);
        int liveCalls = metrics.getAsJsonObject("Metrics").get("LiveCalls").getAsInt();
        logger.info("LiveCalls: "+liveCalls);
        int liveCallsArraySize = metrics.getAsJsonArray("LiveCallDetails").size();
        logger.info("LiveCallsArraySize: "+liveCallsArraySize);
        assertEquals(0, liveCalls);
        assertEquals(0, liveCallsArraySize);
        int maxConcurrentCalls = metrics.getAsJsonObject("Metrics").get("MaximumConcurrentCalls").getAsInt();
        int maxConcurrentIncomingCalls = metrics.getAsJsonObject("Metrics").get("MaximumConcurrentIncomingCalls").getAsInt();
        int maxConcurrentOutgoingCalls = metrics.getAsJsonObject("Metrics").get("MaximumConcurrentOutgoingCalls").getAsInt();
        assertEquals(2, maxConcurrentCalls);
        assertEquals(0,maxConcurrentIncomingCalls);
        assertEquals(2,maxConcurrentOutgoingCalls);
    }

    @Test
    //Create call to client with multiple registrations. Client Alice has two registrations (2 locations) and both should ring
    public void createCallClientTestWithMultipleRegistrations() throws InterruptedException, ParseException {

        SipCall georgeCall = georgePhone.createSipCall();
        georgeCall.listenForIncomingCall();

        // Register Alice Restcomm client
        SipURI uri = aliceSipStack.getAddressFactory().createSipURI(null, "127.0.0.1:5080");
        assertTrue(alicePhone.register(uri, "alice", "1234", aliceContact, 3600, 3600));
        assertTrue(alice2Phone.register(uri, "alice", "1234", alice2Contact, 3600, 3600));

        SipCall aliceCall = alicePhone.createSipCall();
        aliceCall.listenForIncomingCall();
        SipCall alice2Call = alice2Phone.createSipCall();
        alice2Call.listenForIncomingCall();

        String from = "+15126002188";
        String to = "client:alice";
        String rcmlUrl = deploymentUrl.toString() + "/dial-number-entry.xml";

        JsonArray callResult = (JsonArray) RestcommCallsTool.getInstance().createCall(deploymentUrl.toString(), adminAccountSid,
                adminAuthToken, from, to, rcmlUrl);
        assertNotNull(callResult);

        assertTrue(alice2Call.waitForIncomingCall(5000));
        assertTrue(aliceCall.waitForIncomingCall(5000));

        String receivedBody = new String(aliceCall.getLastReceivedRequest().getRawContent());

        assertTrue(aliceCall.sendIncomingCallResponse(Response.RINGING, "Ringing-Alice", 3600));
        assertTrue(aliceCall.sendIncomingCallResponse(Response.OK, "OK-Alice", 3600, receivedBody, "application", "sdp", null,
                null));

        assertTrue(alice2Call.sendIncomingCallResponse(Response.BUSY_HERE, "Busy-Here-Alice-2", 3600));


        // Restcomm now should execute RCML that will create a call to +131313 (george's phone)

        assertTrue(georgeCall.waitForIncomingCall(5000));
        receivedBody = new String(georgeCall.getLastReceivedRequest().getRawContent());
        assertTrue(georgeCall.sendIncomingCallResponse(Response.RINGING, "Ringing-George", 3600));
        assertTrue(georgeCall.sendIncomingCallResponse(Response.OK, "OK-George", 3600, receivedBody, "application", "sdp",
                null, null));

        Thread.sleep(3000);

        aliceCall.listenForDisconnect();

        assertTrue(georgeCall.disconnect());
        assertTrue(georgeCall.waitForAck(5000));

        assertTrue(aliceCall.waitForDisconnect(5000));
        assertTrue(aliceCall.respondToDisconnect());

        Thread.sleep(3000);

        JsonObject metrics = MonitoringServiceTool.getInstance().getMetrics(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
        assertNotNull(metrics);
        int liveCalls = metrics.getAsJsonObject("Metrics").get("LiveCalls").getAsInt();
        logger.info("LiveCalls: "+liveCalls);
        int liveCallsArraySize = metrics.getAsJsonArray("LiveCallDetails").size();
        logger.info("LiveCallsArraySize: "+liveCallsArraySize);
        assertEquals(0, liveCalls);
        assertEquals(0, liveCallsArraySize);
        int maxConcurrentCalls = metrics.getAsJsonObject("Metrics").get("MaximumConcurrentCalls").getAsInt();
        int maxConcurrentIncomingCalls = metrics.getAsJsonObject("Metrics").get("MaximumConcurrentIncomingCalls").getAsInt();
        int maxConcurrentOutgoingCalls = metrics.getAsJsonObject("Metrics").get("MaximumConcurrentOutgoingCalls").getAsInt();
        assertEquals(2, maxConcurrentCalls);
        assertEquals(0,maxConcurrentIncomingCalls);
        assertEquals(2,maxConcurrentOutgoingCalls);
    }

    @Test
    // Create a call to a Number. Non-regression test for issue https://bitbucket.org/telestax/telscale-restcomm/issue/175
    // Use Calls Rest API to dial Number +131313 which is George's phone and connect him to the RCML app dial-client-entry.xml.
    // This RCML will dial Alice Restcomm client (use the dial-number-entry.xml as a side effect to verify that the call created
    // successfully)
    public void createCallNumberTest() throws InterruptedException, ParseException {

        SipCall georgeCall = georgePhone.createSipCall();
        georgeCall.listenForIncomingCall();

        // Register Alice Restcomm client
        SipURI uri = aliceSipStack.getAddressFactory().createSipURI(null, "127.0.0.1:5080");
        assertTrue(alicePhone.register(uri, "alice", "1234", aliceContact, 3600, 3600));

        SipCall aliceCall = alicePhone.createSipCall();
        aliceCall.listenForIncomingCall();

        String from = "+15126002188";
        String to = "+131313";
        String rcmlUrl = deploymentUrl.toString() + "/dial-client-entry.xml";

        JsonElement callResult = RestcommCallsTool.getInstance().createCall(deploymentUrl.toString(), adminAccountSid,
                adminAuthToken, from, to, rcmlUrl);
        assertNotNull(callResult);
        logger.info("CallResult: "+callResult);

        assertTrue(georgeCall.waitForIncomingCall(5000));
        String receivedBody = new String(georgeCall.getLastReceivedRequest().getRawContent());
        assertTrue(georgeCall.sendIncomingCallResponse(Response.RINGING, "Ringing-George", 3600));
        assertTrue(georgeCall.sendIncomingCallResponse(Response.OK, "OK-George", 3600, receivedBody, "application", "sdp",
                null, null));

        // Restcomm now should execute RCML that will create a call to Alice Restcomm client
        assertTrue(aliceCall.waitForIncomingCall(5000));
        receivedBody = new String(aliceCall.getLastReceivedRequest().getRawContent());
        assertTrue(aliceCall.sendIncomingCallResponse(Response.RINGING, "Ringing-Alice", 3600));
        assertTrue(aliceCall.sendIncomingCallResponse(Response.OK, "OK-Alice", 3600, receivedBody, "application", "sdp", null,
                null));

        Thread.sleep(3000);

        aliceCall.listenForDisconnect();

        assertTrue(georgeCall.disconnect());
        assertTrue(georgeCall.waitForAck(5000));

        assertTrue(aliceCall.waitForDisconnect(5000));
        assertTrue(aliceCall.respondToDisconnect());

        Thread.sleep(2000);

        JsonObject metrics = MonitoringServiceTool.getInstance().getMetrics(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
        assertNotNull(metrics);
        int liveCalls = metrics.getAsJsonObject("Metrics").get("LiveCalls").getAsInt();
        logger.info("LiveCalls: "+liveCalls);
        int liveCallsArraySize = metrics.getAsJsonArray("LiveCallDetails").size();
        logger.info("LiveCallsArraySize: "+liveCallsArraySize);
        assertEquals(0, liveCalls);
        assertEquals(0, liveCallsArraySize);
    }

    @Test @Category(FeatureAltTests.class)
    public void createCallNumberWithCustomHeadersTest() throws InterruptedException, ParseException {

        SipCall georgeCall = georgePhone.createSipCall();
        georgeCall.listenForIncomingCall();

        // Register Alice Restcomm client
        SipURI uri = aliceSipStack.getAddressFactory().createSipURI(null, "127.0.0.1:5080");
        assertTrue(alicePhone.register(uri, "alice", "1234", aliceContact, 3600, 3600));

        SipCall aliceCall = alicePhone.createSipCall();
        aliceCall.listenForIncomingCall();

        String from = "+15126002188";
        String to = "+131313?X-Custom-Header1=1234&X-Custom-Header2=4321";
        String rcmlUrl = deploymentUrl.toString() + "/dial-client-entry.xml";

        JsonElement callResult = RestcommCallsTool.getInstance().createCall(deploymentUrl.toString(), adminAccountSid,
                adminAuthToken, from, to, rcmlUrl);
        assertNotNull(callResult);

        assertTrue(georgeCall.waitForIncomingCall(5000));

        //Check custom headers
        SipRequest invite = georgeCall.getLastReceivedRequest();
        assertEquals(SipRequest.INVITE, invite.getRequestEvent().getRequest().getMethod());

        String customHeader1 = ((HeaderExt) invite.getRequestEvent().getRequest().getHeader("X-Custom-Header1")).getValue();
        String customHeader2 = ((HeaderExt) invite.getRequestEvent().getRequest().getHeader("X-Custom-Header2")).getValue();
        assertEquals("1234", customHeader1);
        assertEquals("4321", customHeader2);

        String receivedBody = new String(georgeCall.getLastReceivedRequest().getRawContent());
        assertTrue(georgeCall.sendIncomingCallResponse(Response.RINGING, "Ringing-George", 3600));
        assertTrue(georgeCall.sendIncomingCallResponse(Response.OK, "OK-George", 3600, receivedBody, "application", "sdp",
                null, null));

        // Restcomm now should execute RCML that will create a call to Alice Restcomm client
        assertTrue(aliceCall.waitForIncomingCall(5000));
        receivedBody = new String(aliceCall.getLastReceivedRequest().getRawContent());
        assertTrue(aliceCall.sendIncomingCallResponse(Response.RINGING, "Ringing-Alice", 3600));
        assertTrue(aliceCall.sendIncomingCallResponse(Response.OK, "OK-Alice", 3600, receivedBody, "application", "sdp", null,
                null));

        Thread.sleep(3000);

        aliceCall.listenForDisconnect();

        assertTrue(georgeCall.disconnect());
        assertTrue(georgeCall.waitForAck(5000));

        assertTrue(aliceCall.waitForDisconnect(5000));
        assertTrue(aliceCall.respondToDisconnect());

        Thread.sleep(2000);

        JsonObject metrics = MonitoringServiceTool.getInstance().getMetrics(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
        assertNotNull(metrics);
        int liveCalls = metrics.getAsJsonObject("Metrics").get("LiveCalls").getAsInt();
        logger.info("LiveCalls: "+liveCalls);
        int liveCallsArraySize = metrics.getAsJsonArray("LiveCallDetails").size();
        logger.info("LiveCallsArraySize: "+liveCallsArraySize);
        assertEquals(0, liveCalls);
        assertEquals(0, liveCallsArraySize);
    }

    String dialClient = "<Response><Dial timeLimit=\"10\" timeout=\"10\"><Client>alice</Client></Dial></Response>";


    /*  Calls REST API will create call to sipUnit client 'george' but there will be no answer within the timeout interval (10sec)
        Restcomm should cancel the call and cleanup
     */
    @Test
    @Category({FeatureExpTests.class})
    public void createCallNumberTestNoAnswer() throws InterruptedException, ParseException {

        stubFor(post(urlPathEqualTo("/1111"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(dialClient)));

        SipCall georgeCall = georgePhone.createSipCall();
        georgeCall.listenForIncomingCall();

        // Register Alice Restcomm client
        SipURI uri = aliceSipStack.getAddressFactory().createSipURI(null, "127.0.0.1:5080");
        assertTrue(alicePhone.register(uri, "alice", "1234", aliceContact, 3600, 3600));

        SipCall aliceCall = alicePhone.createSipCall();
        aliceCall.listenForIncomingCall();

        String from = "+15126002188";
        String to = "+131313";
        String rcmlUrl = "http://127.0.0.1:8090/1111";
        String timeout = "10";

        JsonElement callResult = RestcommCallsTool.getInstance().createCall(deploymentUrl.toString(), adminAccountSid,
                adminAuthToken, from, to, rcmlUrl, timeout);
        assertNotNull(callResult);

        assertTrue(georgeCall.waitForIncomingCall(5000));
        String receivedBody = new String(georgeCall.getLastReceivedRequest().getRawContent());
        assertTrue(georgeCall.sendIncomingCallResponse(Response.RINGING, "Ringing-George", 3600));

        assertTrue(georgeCall.sendIncomingCallResponse(Response.RINGING, "Ringing-George", 3600));

        Thread.sleep(15000);


        georgeCall.listenForCancel();

        SipTransaction cancelTransaction = georgeCall.waitForCancel(100 * 1000);
        assertNotNull(cancelTransaction);
        assertTrue(georgeCall.respondToCancel(cancelTransaction, Response.OK, "George-OK-2-Cancel", 3600));

        Thread.sleep(3000);

        JsonObject metrics = MonitoringServiceTool.getInstance().getMetrics(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
        assertNotNull(metrics);
        int liveCalls = metrics.getAsJsonObject("Metrics").get("LiveCalls").getAsInt();
        logger.info("LiveCalls: "+liveCalls);
        int liveCallsArraySize = metrics.getAsJsonArray("LiveCallDetails").size();
        logger.info("LiveCallsArraySize: "+liveCallsArraySize);
        assertEquals(0, liveCalls);
        assertEquals(0, liveCallsArraySize);
    }

    /*  Calls REST API will create call to sipUnit client 'george' which will answer, next will create call to client 'alice'
        but there will be no answer within the timeout interval (10sec) and Restcomm should cancel the call to alice and disconnect 'george' call
    */
    @Test
//    @Category({FeatureExpTests.class, UnstableTests.class})
    @Category(FeatureExpTests.class)
    public void createCallNumberTestNoAnswerOnRcml() throws InterruptedException, ParseException {

        stubFor(post(urlPathEqualTo("/1111"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(dialClient)));

        SipCall georgeCall = georgePhone.createSipCall();
        georgeCall.listenForIncomingCall();

        // Register Alice Restcomm client
        SipURI uri = aliceSipStack.getAddressFactory().createSipURI(null, "127.0.0.1:5080");
        assertTrue(alicePhone.register(uri, "alice", "1234", aliceContact, 3600, 3600));

        SipCall aliceCall = alicePhone.createSipCall();
        aliceCall.listenForIncomingCall();

        String from = "+15126002188";
        String to = "+131313";
        String rcmlUrl = "http://127.0.0.1:8090/1111";
        String timeout = "10";

        JsonElement callResult = RestcommCallsTool.getInstance().createCall(deploymentUrl.toString(), adminAccountSid,
                adminAuthToken, from, to, rcmlUrl, timeout);
        assertNotNull(callResult);

        assertTrue(georgeCall.waitForIncomingCall(5000));
        String receivedBody = new String(georgeCall.getLastReceivedRequest().getRawContent());
        assertTrue(georgeCall.sendIncomingCallResponse(Response.RINGING, "Ringing-George", 3600));
        assertTrue(georgeCall.sendIncomingCallResponse(Response.OK, "OK-George", 3600, receivedBody, "application", "sdp",
                null, null));

        // Restcomm now should execute RCML that will create a call to Alice Restcomm client
        assertTrue(aliceCall.waitForIncomingCall(5000));
        receivedBody = new String(aliceCall.getLastReceivedRequest().getRawContent());
        assertTrue(aliceCall.sendIncomingCallResponse(Response.RINGING, "Ringing-Alice", 3600));

        aliceCall.listenForCancel();
        georgeCall.listenForDisconnect();

        SipTransaction cancelTransaction = aliceCall.waitForCancel(100 * 1000);
        assertNotNull(cancelTransaction);
        assertTrue(aliceCall.respondToCancel(cancelTransaction, Response.OK, "Alice-OK-2-Cancel", 3600));

        assertTrue(georgeCall.waitForDisconnect(5000));
        assertTrue(georgeCall.respondToDisconnect());

        Thread.sleep(2000);

        JsonObject metrics = MonitoringServiceTool.getInstance().getMetrics(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
        assertNotNull(metrics);
        int liveCalls = metrics.getAsJsonObject("Metrics").get("LiveCalls").getAsInt();
        logger.info("LiveCalls: "+liveCalls);
        int liveCallsArraySize = metrics.getAsJsonArray("LiveCallDetails").size();
        logger.info("LiveCallsArraySize: "+liveCallsArraySize);
        assertEquals(0, liveCalls);
        assertEquals(0, liveCallsArraySize);
    }

    /*  Calls REST API will create call to sipUnit client 'george' which will answer, next will create call to client 'alice'
	but the response will be 486 Busy here and Restcomm should disconnect 'george' call
*/
    @Test
//    @Category({FeatureExpTests.class, UnstableTests.class})
    @Category(FeatureExpTests.class)
    public void createCallNumberTestBusyOnRcml () throws InterruptedException, ParseException {

        stubFor(post(urlPathEqualTo("/1111"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(dialClient)));

        SipCall georgeCall = georgePhone.createSipCall();
        georgeCall.listenForIncomingCall();

        // Register Alice Restcomm client
        SipURI uri = aliceSipStack.getAddressFactory().createSipURI(null, "127.0.0.1:5080");
        assertTrue(alicePhone.register(uri, "alice", "1234", aliceContact, 3600, 3600));

        SipCall aliceCall = alicePhone.createSipCall();
        aliceCall.listenForIncomingCall();

        String from = "+15126002188";
        String to = "+131313";
        String rcmlUrl = "http://127.0.0.1:8090/1111";
        String timeout = "10";

        JsonElement callResult = RestcommCallsTool.getInstance().createCall(deploymentUrl.toString(), adminAccountSid,
                adminAuthToken, from, to, rcmlUrl, timeout);
        assertNotNull(callResult);

        assertTrue(georgeCall.waitForIncomingCall(5000));
        String receivedBody = new String(georgeCall.getLastReceivedRequest().getRawContent());
        assertTrue(georgeCall.sendIncomingCallResponse(Response.RINGING, "Ringing-George", 3600));
        assertTrue(georgeCall.sendIncomingCallResponse(Response.OK, "OK-George", 3600, receivedBody, "application", "sdp",
                null, null));

        // Restcomm now should execute RCML that will create a call to Alice Restcomm client
        assertTrue(aliceCall.waitForIncomingCall(5000));
        receivedBody = new String(aliceCall.getLastReceivedRequest().getRawContent());
        assertTrue(aliceCall.sendIncomingCallResponse(Response.BUSY_HERE, "Busy-Here-Alice", 3600));
        assertTrue(aliceCall.waitForAck(5000));

        georgeCall.listenForDisconnect();

        assertTrue(georgeCall.waitForDisconnect(5000));
        assertTrue(georgeCall.respondToDisconnect());

        Thread.sleep(2000);

        JsonObject metrics = MonitoringServiceTool.getInstance().getMetrics(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
        assertNotNull(metrics);
        int liveCalls = metrics.getAsJsonObject("Metrics").get("LiveCalls").getAsInt();
        logger.info("LiveCalls: "+liveCalls);
        int liveCallsArraySize = metrics.getAsJsonArray("LiveCallDetails").size();
        logger.info("LiveCallsArraySize: "+liveCallsArraySize);
        assertEquals(0, liveCalls);
        assertEquals(0, liveCallsArraySize);
    }



    @Test
    @Category({FeatureExpTests.class})
    public void createCallNumberTestWith500ErrorResponse() throws InterruptedException, ParseException {

        SipCall georgeCall = georgePhone.createSipCall();
        georgeCall.listenForIncomingCall();

        // Register Alice Restcomm client
        SipURI uri = aliceSipStack.getAddressFactory().createSipURI(null, "127.0.0.1:5080");
        assertTrue(alicePhone.register(uri, "alice", "1234", aliceContact, 3600, 3600));

        SipCall aliceCall = alicePhone.createSipCall();
        aliceCall.listenForIncomingCall();

        String from = "+15126002188";
        String to = "+131313";
        String rcmlUrl = deploymentUrl.toString() + "/dial-client-entry.xml";

        JsonElement callResult = RestcommCallsTool.getInstance().createCall(deploymentUrl.toString(), adminAccountSid,
                adminAuthToken, from, to, rcmlUrl);
        assertNotNull(callResult);

        assertTrue(georgeCall.waitForIncomingCall(5000));
        String receivedBody = new String(georgeCall.getLastReceivedRequest().getRawContent());
        assertTrue(georgeCall.sendIncomingCallResponse(Response.RINGING, "Ringing-George", 3600));
        assertTrue(georgeCall.sendIncomingCallResponse(Response.SERVER_INTERNAL_ERROR, "Service Unavailable", 3600));

        assertTrue(georgeCall.waitForAck(5000));

        Thread.sleep(2000);

        JsonObject metrics = MonitoringServiceTool.getInstance().getMetrics(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
        assertNotNull(metrics);
        int liveCalls = metrics.getAsJsonObject("Metrics").get("LiveCalls").getAsInt();
        logger.info("LiveCalls: "+liveCalls);
        int liveCallsArraySize = metrics.getAsJsonArray("LiveCallDetails").size();
        logger.info("LiveCallsArraySize: "+liveCallsArraySize);
        assertEquals(0, liveCalls);
        assertEquals(0, liveCallsArraySize);
    }

    String dialClientWithRecord = "<Response><Dial record=\"true\"><Client>alice</Client></Dial></Response>";


    /*  Calls REST API will create call to sipUnit client 'george' but there will be no answer within the timeout interval (10sec)
        Restcomm should cancel the call and cleanup
     */
    @Test
    public void createCallNumberWithRecord() throws InterruptedException, ParseException {

        stubFor(post(urlPathEqualTo("/1111"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(dialClientWithRecord)));

        SipCall georgeCall = georgePhone.createSipCall();
        georgeCall.listenForIncomingCall();

        // Register Alice Restcomm client
        SipURI uri = aliceSipStack.getAddressFactory().createSipURI(null, "127.0.0.1:5080");
        assertTrue(alicePhone.register(uri, "alice", "1234", aliceContact, 3600, 3600));

        SipCall aliceCall = alicePhone.createSipCall();
        aliceCall.listenForIncomingCall();

        String from = "+15126002188";
        String to = "+131313";
        String rcmlUrl = "http://127.0.0.1:8090/1111";

        JsonElement callResult = RestcommCallsTool.getInstance().createCall(deploymentUrl.toString(), adminAccountSid,
                adminAuthToken, from, to, rcmlUrl);
        assertNotNull(callResult);

        assertTrue(georgeCall.waitForIncomingCall(5000));
        String receivedBody = new String(georgeCall.getLastReceivedRequest().getRawContent());
        assertTrue(georgeCall.sendIncomingCallResponse(Response.RINGING, "Ringing-George", 3600));
        assertTrue(georgeCall.sendIncomingCallResponse(Response.OK, "OK-George", 3600, receivedBody, "application", "sdp",
                null, null));

        String callSid = georgeCall.getLastReceivedRequest().getMessage().getHeader("X-RestComm-CallSid").toString().split(":")[1].trim();

        // Restcomm now should execute RCML that will create a call to Alice Restcomm client
        assertTrue(aliceCall.waitForIncomingCall(5000));
        receivedBody = new String(aliceCall.getLastReceivedRequest().getRawContent());
        assertTrue(aliceCall.sendIncomingCallResponse(Response.RINGING, "Ringing-Alice", 3600));
        assertTrue(aliceCall.sendIncomingCallResponse(Response.OK, "OK-Alice", 3600, receivedBody, "application", "sdp", null,
                null));

        Thread.sleep(3000);

        aliceCall.listenForDisconnect();

        assertTrue(georgeCall.disconnect());
        assertTrue(georgeCall.waitForAck(5000));

        assertTrue(aliceCall.waitForDisconnect(5000));
        assertTrue(aliceCall.respondToDisconnect());

        Thread.sleep(2000);

        JsonObject metrics = MonitoringServiceTool.getInstance().getMetrics(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
        assertNotNull(metrics);
        int liveCalls = metrics.getAsJsonObject("Metrics").get("LiveCalls").getAsInt();
        logger.info("LiveCalls: "+liveCalls);
        int liveCallsArraySize = metrics.getAsJsonArray("LiveCallDetails").size();
        logger.info("LiveCallsArraySize: "+liveCallsArraySize);
        assertEquals(0, liveCalls);
        assertEquals(0, liveCallsArraySize);

        //Check recording
        JsonArray recording = RestcommCallsTool.getInstance().getCallRecordings(deploymentUrl.toString(),adminAccountSid,adminAuthToken,callSid);
        assertNotNull(recording);
        assertEquals(1, recording.size());
        double duration = recording.get(0).getAsJsonObject().get("duration").getAsDouble();
        assertEquals(3.0, duration, 1.0);
    }

    @Deployment(name = "CreateCallsTest", managed = true, testable = false)
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
