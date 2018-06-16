package org.restcomm.connect.testsuite.telephony;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.google.gson.JsonObject;
import org.apache.log4j.Logger;
import org.cafesip.sipunit.Credential;
import org.cafesip.sipunit.SipCall;
import org.cafesip.sipunit.SipPhone;
import org.cafesip.sipunit.SipStack;
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
import org.junit.Rule;
import org.junit.Test;
import org.junit.FixMethodOrder;
import org.junit.runners.MethodSorters;
import org.junit.runner.RunWith;
import org.restcomm.connect.commons.Version;
import org.restcomm.connect.monitoringservice.MonitoringMetrics;
import org.restcomm.connect.testsuite.tools.MonitoringServiceTool;

import javax.sip.message.Response;
import java.net.URL;
import java.text.ParseException;

import static org.cafesip.sipunit.SipAssert.assertLastOperationSuccess;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.experimental.categories.Category;
import org.restcomm.connect.commons.annotations.SequentialClassTests;
import org.restcomm.connect.commons.annotations.WithInMinsTests;

/**
 * Created by gvagenas on 6/15/16.
 */
@RunWith(Arquillian.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@Category(SequentialClassTests.class)
public class TestMgcpOperations {

    private final static Logger logger = Logger.getLogger(TestDialVerbPartOne.class.getName());

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
    private static SipStackTool tool4;

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

    // Fotini is a simple SIP Client. Will not register with Restcomm
    private SipStack fotiniSipStack;
    private SipPhone fotiniPhone;
    private String fotiniContact = "sip:fotini@127.0.0.1";

    private String dialRestcomm = "sip:1111@127.0.0.1:5080";
    private String notFoundDialNumber = "sip:+12223334457@127.0.0.1:5080";

    @BeforeClass
    public static void beforeClass() throws Exception {
        tool1 = new SipStackTool("DialTest1Tool1");
        tool2 = new SipStackTool("DialTest1Tool2");
        tool3 = new SipStackTool("DialTest1Tool3");
        tool4 = new SipStackTool("DialTest1Tool4");
    }

    @Before
    public void before() throws Exception {
        bobSipStack = tool1.initializeSipStack(SipStack.PROTOCOL_UDP, "127.0.0.1", "5090", "127.0.0.1:5080");
        bobPhone = bobSipStack.createSipPhone("127.0.0.1", SipStack.PROTOCOL_UDP, 5080, bobContact);

        aliceSipStack = tool2.initializeSipStack(SipStack.PROTOCOL_UDP, "127.0.0.1", "5091", "127.0.0.1:5080");
        alicePhone = aliceSipStack.createSipPhone("127.0.0.1", SipStack.PROTOCOL_UDP, 5080, aliceContact);

        georgeSipStack = tool3.initializeSipStack(SipStack.PROTOCOL_UDP, "127.0.0.1", "5070", "127.0.0.1:5080");
        georgePhone = georgeSipStack.createSipPhone("127.0.0.1", SipStack.PROTOCOL_UDP, 5080, georgeContact);

        fotiniSipStack = tool4.initializeSipStack(SipStack.PROTOCOL_UDP, "127.0.0.1", "5060", "127.0.0.1:5080");
        fotiniPhone = fotiniSipStack.createSipPhone("127.0.0.1", SipStack.PROTOCOL_UDP, 5080, fotiniContact);
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

        if (fotiniPhone != null) {
            fotiniPhone.dispose();
        }
        if (fotiniSipStack != null) {
            fotiniSipStack.dispose();
        }
        Thread.sleep(3000);
        wireMockRule.resetRequests();
        Thread.sleep(2000);
    }


    @Test
    public synchronized void testDialHelloPlay() throws InterruptedException, ParseException {

        Credential c = new Credential("127.0.0.1", "bob", "1234");
        bobPhone.addUpdateCredential(c);

        final SipCall bobCall = bobPhone.createSipCall();
        bobCall.listenForDisconnect();
        bobCall.initiateOutgoingCall(bobContact, "sip:1234@127.0.0.1:5080", null, body, "application", "sdp", null, null);
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
        assertTrue(!(bobCall.getLastReceivedResponse().getStatusCode() >= 400));

        Thread.sleep(500);

        JsonObject metrics = MonitoringServiceTool.getInstance().getMetrics(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
        assertNotNull(metrics);
        int mgcpEndpoints = metrics.getAsJsonObject("Metrics").get("MgcpEndpoints").getAsInt();
        int mgcpConnections = metrics.getAsJsonObject("Metrics").get("MgcpConnections").getAsInt();
        int liveCalls = metrics.getAsJsonObject("Metrics").get("LiveCalls").getAsInt();

        logger.info("MgcpEndpoints: "+mgcpEndpoints);
        logger.info("MgcpConnections: "+mgcpConnections);
        logger.info("LiveCalls: "+liveCalls);
//        int liveCallsArraySize = metrics.getAsJsonArray("LiveCallDetails").size();
//        logger.info("LiveCallsArraySize: "+liveCallsArraySize);
//        assertTrue(liveCalls==0);
//        assertTrue(liveCallsArraySize==0);
//        int maxConcurrentCalls = metrics.getAsJsonObject("Metrics").get("MaximumConcurrentCalls").getAsInt();
//        int maxConcurrentIncomingCalls = metrics.getAsJsonObject("Metrics").get("MaximumConcurrentIncomingCalls").getAsInt();
//        int maxConcurrentOutgoingCalls = metrics.getAsJsonObject("Metrics").get("MaximumConcurrentIncomingCalls").getAsInt();
//        assertTrue(maxConcurrentCalls==0);
//        assertTrue(maxConcurrentIncomingCalls==0);
//        assertTrue(maxConcurrentOutgoingCalls==0);



        assertTrue(bobCall.waitForDisconnect(10000));

        Thread.sleep(1000);

        metrics = MonitoringServiceTool.getInstance().getMetrics(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
        assertNotNull(metrics);
        mgcpEndpoints = metrics.getAsJsonObject("Metrics").get("MgcpEndpoints").getAsInt();
        mgcpConnections = metrics.getAsJsonObject("Metrics").get("MgcpConnections").getAsInt();
        liveCalls = metrics.getAsJsonObject("Metrics").get("LiveCalls").getAsInt();

        logger.info("MgcpEndpoints at the end: "+mgcpEndpoints);
        logger.info("MgcpConnections at the end: "+mgcpConnections);
        logger.info("Live calls at the end: "+liveCalls);


        int ivrEndpoints;
        int confEndpoints;
        int bridgeEndpoints;
        int packetRelayEndpoints;
        if (mgcpEndpoints > 0) {
            bridgeEndpoints = metrics.getAsJsonObject("Metrics").get(MonitoringMetrics.COUNTERS_MAP_MGCP_ENDPOINTS_BRIDGE).getAsInt();
            ivrEndpoints = metrics.getAsJsonObject("Metrics").get(MonitoringMetrics.COUNTERS_MAP_MGCP_ENDPOINTS_IVR).getAsInt();
            confEndpoints = metrics.getAsJsonObject("Metrics").get(MonitoringMetrics.COUNTERS_MAP_MGCP_ENDPOINTS_CONFERENCE).getAsInt();
            packetRelayEndpoints = metrics.getAsJsonObject("Metrics").get(MonitoringMetrics.COUNTERS_MAP_MGCP_ENDPOINTS_PACKETRELAY).getAsInt();
            logger.info("IVR Endpoints: "+ivrEndpoints);
            logger.info("Bridge Endpoints: "+bridgeEndpoints);
            logger.info("Conference Endpoints: "+confEndpoints);
            logger.info("PacketRelay Endpoints: "+packetRelayEndpoints);
        }
        assertEquals(0, liveCalls);
        assertEquals(0, mgcpEndpoints);
        assertEquals(0, mgcpConnections);
    }


    @Deployment(name = "TestMgcpOperations", managed = true, testable = false)
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
        archive.addAsWebInfResource("restcomm.script_mgcpoperations", "data/hsql/restcomm.script");
        logger.info("Packaged Test App");
        return archive;
    }
}
