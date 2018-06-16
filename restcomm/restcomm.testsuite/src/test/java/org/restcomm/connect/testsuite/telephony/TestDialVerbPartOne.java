package org.restcomm.connect.testsuite.telephony;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.apache.log4j.Logger;
import org.cafesip.sipunit.SipCall;
import org.cafesip.sipunit.SipPhone;
import org.cafesip.sipunit.SipResponse;
import org.cafesip.sipunit.SipStack;
import org.cafesip.sipunit.SipTransaction;
import org.jboss.arquillian.container.mss.extension.SipStackTool;
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
import org.junit.runner.RunWith;
import org.restcomm.connect.commons.Version;
import org.restcomm.connect.commons.annotations.FeatureAltTests;
import org.restcomm.connect.commons.annotations.FeatureExpTests;
import org.restcomm.connect.testsuite.http.RestcommCallsTool;

import javax.sip.address.SipURI;
import javax.sip.message.Response;
import java.net.URL;
import java.text.ParseException;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import java.util.HashMap;
import java.util.Map;
import static org.cafesip.sipunit.SipAssert.assertLastOperationSuccess;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.experimental.categories.Category;
import org.restcomm.connect.commons.annotations.ParallelClassTests;
import org.restcomm.connect.commons.annotations.WithInMinsTests;
import org.restcomm.connect.testsuite.NetworkPortAssigner;
import org.restcomm.connect.testsuite.WebArchiveUtil;

/**
 * Test for Dial verb. Will test Dial Conference, Dial URI, Dial Client, Dial Number and Dial Fork
 *
 * @author <a href="mailto:gvagenas@gmail.com">gvagenas</a>
 * @author jean.deruelle@telestax.com
 */
@RunWith(Arquillian.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@Category(ParallelClassTests.class)
public class TestDialVerbPartOne {
    private final static Logger logger = Logger.getLogger(TestDialVerbPartOne.class.getName());

    private static final String version = Version.getVersion();
    private static final byte[] bytes = new byte[]{118, 61, 48, 13, 10, 111, 61, 117, 115, 101, 114, 49, 32, 53, 51, 54, 53,
            53, 55, 54, 53, 32, 50, 51, 53, 51, 54, 56, 55, 54, 51, 55, 32, 73, 78, 32, 73, 80, 52, 32, 49, 50, 55, 46, 48, 46,
            48, 46, 49, 13, 10, 115, 61, 45, 13, 10, 99, 61, 73, 78, 32, 73, 80, 52, 32, 49, 50, 55, 46, 48, 46, 48, 46, 49,
            13, 10, 116, 61, 48, 32, 48, 13, 10, 109, 61, 97, 117, 100, 105, 111, 32, 54, 48, 48, 48, 32, 82, 84, 80, 47, 65,
            86, 80, 32, 48, 13, 10, 97, 61, 114, 116, 112, 109, 97, 112, 58, 48, 32, 80, 67, 77, 85, 47, 56, 48, 48, 48, 13, 10};
    private static final String body = new String(bytes);
    private static final String sdpForHold = "v=0\n" +
            "o=bob-jitsi.org 0 2 IN IP4 192.168.1.190\n" +
            "s=-\n" +
            "c=IN IP4 192.168.1.190\n" +
            "t=0 0\n" +
            "m=audio 5000 RTP/AVP 0 8 3 101\n" +
            "a=rtpmap:0 PCMU/8000\n" +
            "a=rtpmap:8 PCMA/8000\n" +
            "a=rtpmap:3 GSM/8000\n" +
            "a=rtpmap:101 telephone-event/8000\n" +
            "a=extmap:1 urn:ietf:params:rtp-hdrext:csrc-audio-level\n" +
            "a=extmap:2 urn:ietf:params:rtp-hdrext:ssrc-audio-level\n" +
            "a=rtcp-xr:voip-metrics\n" +
            "m=video 5004 RTP/AVP 96 99\n" +
            "a=recvonly\n" +
            "a=rtpmap:96 H264/90000\n" +
            "a=fmtp:96 profile-level-id=4DE01f;packetization-mode=1\n" +
            "a=imageattr:96 send * recv [x=[0-1440],y=[0-900]]\n" +
            "a=rtpmap:99 H264/90000\n" +
            "a=fmtp:99 profile-level-id=4DE01f\n" +
            "a=imageattr:99 send * recv [x=[0-1440],y=[0-900]]\n";

    @ArquillianResource
    URL deploymentUrl;

    private String adminAccountSid = "ACae6e420f425248d6a26948c17a9e2acf";
    private String adminAuthToken = "77f8c12cc7b8f8423e5c38b035249166";

    private static int mediaPort = NetworkPortAssigner.retrieveNextPortByFile();

    private static int mockPort = NetworkPortAssigner.retrieveNextPortByFile();
    @Rule
    public WireMockRule wireMockRule = new WireMockRule(mockPort);
    private String dialClientRcmlWithScreeningRelative = "<Response><Dial timeLimit=\"10\" timeout=\"10\"><Client url=\"http://127.0.0.1:" + mockPort + "/screening\" method=\"GET\">alice</Client></Dial></Response>";
    private String dialClientRcmlWithScreening = "<Response><Dial timeLimit=\"10\" timeout=\"10\"><Client url=\"http://127.0.0.1:" + mockPort + "/screening\" method=\"GET\">alice</Client></Dial></Response>";


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

    private static int restcommPort = 5080;
    private static int restcommHTTPPort = 8080;
    private static String restcommContact = "127.0.0.1:" + restcommPort;
    private static String dialRestcomm = "sip:1111@" + restcommContact;
    private static String notFoundDialNumber = "sip:+12223334457@" + restcommContact;

    @BeforeClass
    public static void beforeClass() throws Exception {
        tool1 = new SipStackTool("DialTest1Tool1");
        tool2 = new SipStackTool("DialTest1Tool2");
        tool3 = new SipStackTool("DialTest1Tool3");
        tool4 = new SipStackTool("DialTest1Tool4");
    }

    public static void reconfigurePorts() {
        if (System.getProperty("arquillian_sip_port") != null) {
            restcommPort = Integer.valueOf(System.getProperty("arquillian_sip_port"));
            restcommContact = "127.0.0.1:" + restcommPort;
            dialRestcomm = "sip:1111@" + restcommContact;
            notFoundDialNumber = "sip:+12223334457@" + restcommContact;
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

        georgeSipStack = tool3.initializeSipStack(SipStack.PROTOCOL_UDP, "127.0.0.1", georgePort, restcommContact);
        georgePhone = georgeSipStack.createSipPhone("127.0.0.1", SipStack.PROTOCOL_UDP, restcommPort, georgeContact);

        fotiniSipStack = tool4.initializeSipStack(SipStack.PROTOCOL_UDP, "127.0.0.1", fotiniPort, restcommContact);
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

    private String dialConfernceRcml = "<Response><Dial timeLimit=\"50\"><Conference>test</Conference></Dial></Response>";
    @Test
    public synchronized void testDialConference() throws InterruptedException {
        stubFor(get(urlPathEqualTo("/1111"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(dialConfernceRcml)));

        final SipCall bobCall = bobPhone.createSipCall();
        bobCall.initiateOutgoingCall(bobContact, dialRestcomm, null, body, "application", "sdp", null, null);
        assertLastOperationSuccess(bobCall);
        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        int responseBob = bobCall.getLastReceivedResponse().getStatusCode();
        assertTrue(responseBob == Response.TRYING || responseBob == Response.RINGING);

        if (responseBob == Response.TRYING) {
            assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
            assertEquals(Response.RINGING, bobCall.getLastReceivedResponse().getStatusCode());
        }

        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        assertEquals(Response.OK, bobCall.getLastReceivedResponse().getStatusCode());
        bobCall.sendInviteOkAck();
        assertTrue(!(bobCall.getLastReceivedResponse().getStatusCode() >= 400));

        // George calls to the conference
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

        // Wait for the media to play and the call to hangup.
        bobCall.listenForDisconnect();
        georgeCall.listenForDisconnect();

        // Start a new thread for george to wait disconnect
        new Thread(new Runnable() {
            @Override
            public void run() {
                assertTrue(georgeCall.waitForDisconnect(30 * 1000));
            }
        }).start();

        // Start a new thread for bob to wait disconnect
        new Thread(new Runnable() {
            @Override
            public void run() {
                assertTrue(bobCall.waitForDisconnect(30 * 1000));
            }
        }).start();
    }

    private String dialConfernceRcmlWithTimeLimit = "<Response><Dial timeLimit=\"50\"><Conference>test</Conference></Dial></Response>";
    @Test
    public synchronized void testDialConferenceOnlyOneClientWithTimeLimit() throws InterruptedException {
        stubFor(get(urlPathEqualTo("/1111"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(dialConfernceRcmlWithTimeLimit)));

        final SipCall bobCall = bobPhone.createSipCall();
        bobCall.initiateOutgoingCall(bobContact, dialRestcomm, null, body, "application", "sdp", null, null);
        assertLastOperationSuccess(bobCall);
        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        int responseBob = bobCall.getLastReceivedResponse().getStatusCode();
        assertTrue(responseBob == Response.TRYING || responseBob == Response.RINGING);

        if (responseBob == Response.TRYING) {
            assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
            assertEquals(Response.RINGING, bobCall.getLastReceivedResponse().getStatusCode());
        }

        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        assertEquals(Response.OK, bobCall.getLastReceivedResponse().getStatusCode());
        bobCall.sendInviteOkAck();
        assertTrue(!(bobCall.getLastReceivedResponse().getStatusCode() >= 400));

        // Wait for the media to play and the call to hangup.
        bobCall.listenForDisconnect();
        assertTrue(bobCall.waitForDisconnect(60 * 1000));
    }

    private String dialConfernceRcmlWithTimeLimitSmsAfterConf = "<Response><Dial timeLimit=\"50\"><Conference>test</Conference></Dial><Sms>Conference time limit reached</Sms></Response>";
    @Test
    @Category(FeatureAltTests.class)
    public synchronized void testDialConferenceOnlyOneClientWithTimeLimitSmsAfterConf() throws InterruptedException {
        stubFor(get(urlPathEqualTo("/1111"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(dialConfernceRcmlWithTimeLimitSmsAfterConf)));

        final SipCall bobCall = bobPhone.createSipCall();
        bobCall.initiateOutgoingCall(bobContact, dialRestcomm, null, body, "application", "sdp", null, null);
        assertLastOperationSuccess(bobCall);
        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        int responseBob = bobCall.getLastReceivedResponse().getStatusCode();
        assertTrue(responseBob == Response.TRYING || responseBob == Response.RINGING);

        if (responseBob == Response.TRYING) {
            assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
            assertEquals(Response.RINGING, bobCall.getLastReceivedResponse().getStatusCode());
        }

        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        assertEquals(Response.OK, bobCall.getLastReceivedResponse().getStatusCode());
        bobCall.sendInviteOkAck();
        assertTrue(!(bobCall.getLastReceivedResponse().getStatusCode() >= 400));

        // Wait for the media to play and the call to hangup.
        bobCall.listenForDisconnect();
        assertTrue(bobCall.waitForDisconnect(60 * 1000));

        bobCall.listenForMessage();
        assertTrue(bobCall.waitForMessage(60000));
        assertTrue(bobCall.sendMessageResponse(Response.ACCEPTED,"BobCall Msg Accepted", 3600));
        String messageReceived = new String(bobCall.getLastReceivedMessageRequest().getRawContent());
        assertEquals("Conference time limit reached", messageReceived);
    }

    private String dialConfernceRcmlWithoutTimeLimit = "<Response><Dial><Conference>test</Conference></Dial></Response>";
    @Test
    @Category(FeatureAltTests.class)
    public synchronized void testDialConferenceOnlyOneClientWithoutTimeLimit() throws InterruptedException {
        stubFor(get(urlPathEqualTo("/1111"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(dialConfernceRcmlWithoutTimeLimit)));

        final SipCall bobCall = bobPhone.createSipCall();
        bobCall.initiateOutgoingCall(bobContact, dialRestcomm, null, body, "application", "sdp", null, null);
        assertLastOperationSuccess(bobCall);
        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        int responseBob = bobCall.getLastReceivedResponse().getStatusCode();
        assertTrue(responseBob == Response.TRYING || responseBob == Response.RINGING);

        if (responseBob == Response.TRYING) {
            assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
            assertEquals(Response.RINGING, bobCall.getLastReceivedResponse().getStatusCode());
        }

        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        assertEquals(Response.OK, bobCall.getLastReceivedResponse().getStatusCode());
        bobCall.sendInviteOkAck();
        assertTrue(!(bobCall.getLastReceivedResponse().getStatusCode() >= 400));

        Thread.sleep(1000);
        bobCall.disconnect();

        Thread.sleep(10000);
    }

    @Test
    public synchronized void testDialConferenceConcurrentCalls() throws InterruptedException {
        stubFor(get(urlPathEqualTo("/1111"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(dialConfernceRcml)));

        final SipCall bobCall = bobPhone.createSipCall();
        final SipCall georgeCall = georgePhone.createSipCall();

        bobCall.initiateOutgoingCall(bobContact, dialRestcomm, null, body, "application", "sdp", null, null);
        georgeCall.initiateOutgoingCall(georgeContact, dialRestcomm, null, body, "application", "sdp", null, null);


        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        assertTrue(georgeCall.waitOutgoingCallResponse(5 * 1000));


        int responseBob = bobCall.getLastReceivedResponse().getStatusCode();
        assertTrue(responseBob == Response.TRYING || responseBob == Response.RINGING);

        if (responseBob == Response.TRYING) {
            assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
            assertEquals(Response.RINGING, bobCall.getLastReceivedResponse().getStatusCode());
        }

        int responseGeorge = georgeCall.getLastReceivedResponse().getStatusCode();
        assertTrue(responseGeorge == Response.TRYING || responseGeorge == Response.RINGING);

        if (responseGeorge == Response.TRYING) {
            assertTrue(georgeCall.waitOutgoingCallResponse(5 * 1000));
            assertEquals(Response.RINGING, georgeCall.getLastReceivedResponse().getStatusCode());
        }

        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        assertEquals(Response.OK, bobCall.getLastReceivedResponse().getStatusCode());

        assertTrue(georgeCall.waitOutgoingCallResponse(5 * 1000));
        assertEquals(Response.OK, georgeCall.getLastReceivedResponse().getStatusCode());

        bobCall.sendInviteOkAck();
        georgeCall.sendInviteOkAck();

        // Wait for the media to play and the call to hangup.
        bobCall.listenForDisconnect();
        georgeCall.listenForDisconnect();

        Thread.sleep(5000);

        // Start a new thread for george to wait disconnect
        new Thread(new Runnable() {
            @Override
            public void run() {
                assertTrue(georgeCall.waitForDisconnect(30 * 1000));
            }
        }).start();

        // Start a new thread for bob to wait disconnect
        new Thread(new Runnable() {
            @Override
            public void run() {
                assertTrue(bobCall.waitForDisconnect(30 * 1000));
            }
        }).start();
    }

    private String dialConfernceRcmlWithPlay = "<Response><Play>/restcomm/audio/demo-prompt.wav</Play><Dial timeLimit=\"50\"><Conference>test</Conference></Dial></Response>";
    @Test
    @Category(FeatureAltTests.class)
    public synchronized void testDialConferenceWithPlay() throws InterruptedException {
        stubFor(get(urlPathEqualTo("/1111"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(dialConfernceRcml)));

        final SipCall bobCall = bobPhone.createSipCall();
        bobCall.initiateOutgoingCall(bobContact, dialRestcomm, null, body, "application", "sdp", null, null);
        assertLastOperationSuccess(bobCall);
        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        int responseBob = bobCall.getLastReceivedResponse().getStatusCode();
        assertTrue(responseBob == Response.TRYING || responseBob == Response.RINGING);

        if (responseBob == Response.TRYING) {
            assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
            assertEquals(Response.RINGING, bobCall.getLastReceivedResponse().getStatusCode());
        }

        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        assertEquals(Response.OK, bobCall.getLastReceivedResponse().getStatusCode());
        bobCall.sendInviteOkAck();
        assertTrue(!(bobCall.getLastReceivedResponse().getStatusCode() >= 400));

        // George calls to the conference
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

        // Wait for the media to play and the call to hangup.
        bobCall.listenForDisconnect();
        georgeCall.listenForDisconnect();

        // Start a new thread for george to wait disconnect
        new Thread(new Runnable() {
            @Override
            public void run() {
                assertTrue(georgeCall.waitForDisconnect(30 * 1000));
            }
        }).start();

        // Start a new thread for bob to wait disconnect
        new Thread(new Runnable() {
            @Override
            public void run() {
                assertTrue(bobCall.waitForDisconnect(30 * 1000));
            }
        }).start();
    }

    @Test
    @Category(FeatureAltTests.class)
    public synchronized void testDialConferenceWithPlayInDialogInviteHold() throws InterruptedException {
        stubFor(get(urlPathEqualTo("/1111"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(dialConfernceRcml)));

        final SipCall bobCall = bobPhone.createSipCall();
        bobCall.initiateOutgoingCall(bobContact, dialRestcomm, null, body, "application", "sdp", null, null);
        assertLastOperationSuccess(bobCall);
        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        int responseBob = bobCall.getLastReceivedResponse().getStatusCode();
        assertTrue(responseBob == Response.TRYING || responseBob == Response.RINGING);

        if (responseBob == Response.TRYING) {
            assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
            assertEquals(Response.RINGING, bobCall.getLastReceivedResponse().getStatusCode());
        }

        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        assertEquals(Response.OK, bobCall.getLastReceivedResponse().getStatusCode());
        bobCall.sendInviteOkAck();
        assertTrue(!(bobCall.getLastReceivedResponse().getStatusCode() >= 400));

        // George calls to the conference
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

        //sendReinvite(String newContact, String displayName, String body, String contentType, String contentSubType)
        SipTransaction reInviteTrans = bobCall.sendReinvite(bobContact, "Hold", sdpForHold, "application", "sdp");
        assertTrue(bobCall.waitReinviteResponse(reInviteTrans, 5000));
        bobCall.sendReinviteOkAck(reInviteTrans);

        // Wait for the media to play and the call to hangup.
        bobCall.listenForDisconnect();
        georgeCall.listenForDisconnect();

        // Start a new thread for george to wait disconnect
        new Thread(new Runnable() {
            @Override
            public void run() {
                assertTrue(georgeCall.waitForDisconnect(30 * 1000));
            }
        }).start();

        // Start a new thread for bob to wait disconnect
        new Thread(new Runnable() {
            @Override
            public void run() {
                assertTrue(bobCall.waitForDisconnect(30 * 1000));
            }
        }).start();
    }

    @Test
    @Category(FeatureAltTests.class)
    public synchronized void testDialConferenceWithContactHeaderPortNull() throws InterruptedException {
        stubFor(get(urlPathEqualTo("/1111"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(dialConfernceRcml)));

        final SipCall fotiniCall = fotiniPhone.createSipCall();
        fotiniCall.initiateOutgoingCall(fotiniContact, dialRestcomm, null, body, "application", "sdp", null, null);
        assertLastOperationSuccess(fotiniCall);
        assertTrue(fotiniCall.waitOutgoingCallResponse(5 * 1000));
        int responseFotini = fotiniCall.getLastReceivedResponse().getStatusCode();
        assertTrue(responseFotini == Response.TRYING || responseFotini == Response.RINGING);

        if (responseFotini == Response.TRYING) {
            assertTrue(fotiniCall.waitOutgoingCallResponse(5 * 1000));
            assertEquals(Response.RINGING, fotiniCall.getLastReceivedResponse().getStatusCode());
        }

        assertTrue(fotiniCall.waitOutgoingCallResponse(5 * 1000));
        assertEquals(Response.OK, fotiniCall.getLastReceivedResponse().getStatusCode());
        fotiniCall.sendInviteOkAck();
        assertTrue(!(fotiniCall.getLastReceivedResponse().getStatusCode() >= 400));

        // George calls to the conference
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

        // Wait for the media to play and the call to hangup.
        fotiniCall.listenForDisconnect();
        georgeCall.listenForDisconnect();

        // Start a new thread for george to wait disconnect
        new Thread(new Runnable() {
            @Override
            public void run() {
                assertTrue(georgeCall.waitForDisconnect(30 * 1000));
            }
        }).start();

        // Start a new thread for bob to wait disconnect
        new Thread(new Runnable() {
            @Override
            public void run() {
                assertTrue(fotiniCall.waitForDisconnect(30 * 1000));
            }
        }).start();

    }

    @Test
    // Non regression test for
    // https://bitbucket.org/telestax/telscale-restcomm/issue/113/when-restcomm-cannot-find-an-app-url-it
    @Category(FeatureExpTests.class)
    public synchronized void testDialApplicationInvalidURL() throws InterruptedException, ParseException {

        // Phone2 register as alice
        SipURI uri = aliceSipStack.getAddressFactory().createSipURI(null, restcommContact);
        assertTrue(alicePhone.register(uri, "alice", "1234", aliceContact, 3600, 3600));

        // Prepare second phone to receive call
        SipCall aliceCall = alicePhone.createSipCall();
        aliceCall.listenForIncomingCall();

        // Create outgoing call with first phone
        final SipCall bobCall = bobPhone.createSipCall();
        bobCall.initiateOutgoingCall(bobContact, notFoundDialNumber, null, body, "application", "sdp", null, null);
        assertLastOperationSuccess(bobCall);

        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        final int response = bobCall.getLastReceivedResponse().getStatusCode();

        assertTrue(response == Response.TRYING || response == Response.RINGING);

        if (response == Response.TRYING) {
            assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
            assertEquals(Response.RINGING, bobCall.getLastReceivedResponse().getStatusCode());
        }
        // wait for 404 Not Found
        assertTrue(bobCall.waitOutgoingCallResponse(10000));
        SipResponse lastResponse = bobCall.getLastReceivedResponse();
        assertEquals(500, lastResponse.getStatusCode());
    }

    private String dialUriRcml = "<Response><Dial timeLimit=\"100000\" timeout=\"1000000\"><Uri>sip:alice@127.0.0.1:" + alicePort + "</Uri></Dial><Hangup/></Response>";
    @Test
    public synchronized void testDialUriAliceHangup() throws InterruptedException, ParseException {
        stubFor(get(urlPathEqualTo("/1111"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(dialUriRcml)));

        // Phone2 register as alice
        SipURI uri = aliceSipStack.getAddressFactory().createSipURI(null, restcommContact);
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
        String inboundCallSid = bobCall.getLastReceivedResponse().getMessage().getHeader("X-RestComm-CallSid").toString().split(":")[1].trim();

        bobCall.sendInviteOkAck();
        assertTrue(!(bobCall.getLastReceivedResponse().getStatusCode() >= 400));

        assertTrue(aliceCall.waitForIncomingCall(30 * 1000));
        String outboundCallSid = aliceCall.getLastReceivedRequest().getMessage().getHeader("X-RestComm-CallSid").toString().split(":")[1].trim();
        assertTrue(aliceCall.sendIncomingCallResponse(Response.RINGING, "Ringing-Alice", 3600));
        String receivedBody = new String(aliceCall.getLastReceivedRequest().getRawContent());
        assertTrue(aliceCall.sendIncomingCallResponse(Response.OK, "OK-Alice", 3600, receivedBody, "application", "sdp", null,
                null));
        assertTrue(aliceCall.waitForAck(50 * 1000));

        Thread.sleep(3000);

        // hangup.
        aliceCall.disconnect();

        bobCall.listenForDisconnect();
        assertTrue(bobCall.waitForDisconnect(50 * 1000));
        assertTrue(bobCall.respondToDisconnect());

        JsonObject inboundCallCdr = RestcommCallsTool.getInstance().getCall(deploymentUrl.toString(), adminAccountSid, adminAuthToken, inboundCallSid);
        JsonObject outboundCallCdr = RestcommCallsTool.getInstance().getCall(deploymentUrl.toString(), adminAccountSid, adminAuthToken, outboundCallSid);
        assertNotNull(inboundCallCdr);
        assertNotNull(outboundCallCdr);

        int inboundCdrDuration = inboundCallCdr.get("duration").getAsInt();
        int outboundCdrDuration = outboundCallCdr.get("duration").getAsInt();
        int outboundCdrRinging = outboundCallCdr.get("ring_duration").getAsInt();
        assertTrue(inboundCdrDuration==3);
        assertTrue(outboundCdrDuration==3);
        assertTrue(outboundCdrRinging==0);
    }

    @Test
    public synchronized void testDialUriBobHangup() throws InterruptedException, ParseException {
        stubFor(get(urlPathEqualTo("/1111"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(dialUriRcml)));

        int initialCdrSize = 0;

        //Check CDR
        JsonObject cdrs = RestcommCallsTool.getInstance().getCalls("http://127.0.0.1:" +restcommHTTPPort + "/restcomm", adminAccountSid, adminAuthToken);
        if (cdrs != null) {
            initialCdrSize = cdrs.get("calls").getAsJsonArray().size();
        }

        // Phone2 register as alice
        SipURI uri = aliceSipStack.getAddressFactory().createSipURI(null, restcommContact);
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
        //Ringing time 5 sec
        Thread.sleep(5000);
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

        Thread.sleep(3000);

        //Check CDR
        cdrs = RestcommCallsTool.getInstance().getCalls("http://127.0.0.1:" + restcommHTTPPort + "/restcomm", adminAccountSid, adminAuthToken);
        assertNotNull(cdrs);
        JsonArray cdrsArray = cdrs.get("calls").getAsJsonArray();
        System.out.println("cdrsArray.size(): " + cdrsArray.size());
        assertTrue((cdrsArray.size() - initialCdrSize) == 2);
    }

    @Test
    @Category(FeatureAltTests.class)
    public synchronized void testDialUriBobHangupCheckCDRs() throws InterruptedException, ParseException {
        stubFor(get(urlPathEqualTo("/1111"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(dialUriRcml)));

        // Phone2 register as alice
        SipURI uri = aliceSipStack.getAddressFactory().createSipURI(null, restcommContact);
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
//        X-RestComm-CallSid: CA5c2a775f2ca24003a04b7f7b90e6fabb
        String inboundCallSid = bobCall.getLastReceivedResponse().getMessage().getHeader("X-RestComm-CallSid").toString().split(":")[1].trim();

        bobCall.sendInviteOkAck();
        assertTrue(!(bobCall.getLastReceivedResponse().getStatusCode() >= 400));

        assertTrue(aliceCall.waitForIncomingCall(30 * 1000));
        //X-RestComm-CallSid: CAedaccc8f598b4093b1fc33431e1c9ac9
        String outboundCallSid = aliceCall.getLastReceivedRequest().getMessage().getHeader("X-RestComm-CallSid").toString().split(":")[1].trim();
        assertTrue(aliceCall.sendIncomingCallResponse(Response.RINGING, "Ringing-Alice", 3600));
        //Ringing time 5 sec
        Thread.sleep(5000);
        String receivedBody = new String(aliceCall.getLastReceivedRequest().getRawContent());
        assertTrue(aliceCall.sendIncomingCallResponse(Response.OK, "OK-Alice", 3600, receivedBody, "application", "sdp", null,
                null));
        assertTrue(aliceCall.waitForAck(50 * 1000));

        Thread.sleep(3000);
        aliceCall.listenForDisconnect();
        // hangup.
        bobCall.disconnect();
        assertTrue(aliceCall.waitForDisconnect(30 * 1000));
        assertTrue(aliceCall.respondToDisconnect());

        Thread.sleep(6000);

        //Check CDR
        JsonObject inboundCallCdr = RestcommCallsTool.getInstance().getCall(deploymentUrl.toString(), adminAccountSid, adminAuthToken, inboundCallSid);
        JsonObject outboundCallCdr = RestcommCallsTool.getInstance().getCall(deploymentUrl.toString(), adminAccountSid, adminAuthToken, outboundCallSid);
        assertNotNull(inboundCallCdr);
        assertNotNull(outboundCallCdr);

        int inboundCdrDuration = inboundCallCdr.get("duration").getAsInt();
        int outboundCdrDuration = outboundCallCdr.get("duration").getAsInt();
        int outboundCdrRinging = outboundCallCdr.get("ring_duration").getAsInt();
        assertTrue(inboundCdrDuration==8);
        assertTrue(outboundCdrDuration==3);
        assertTrue(outboundCdrRinging==5);
    }

    private String dialClientRcml = "<Response><Dial timeLimit=\"10\" timeout=\"10\"><Client>alice</Client></Dial></Response>";
    @Test
    public synchronized void testDialClientAlice() throws InterruptedException, ParseException {
        stubFor(get(urlPathEqualTo("/1111"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(dialClientRcml)));

        // Phone2 register as alice
        SipURI uri = aliceSipStack.getAddressFactory().createSipURI(null, restcommContact);
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

    @Test
    @Category(FeatureExpTests.class)
    public synchronized void testDialClientAliceNoSDP() throws InterruptedException, ParseException {
        stubFor(get(urlPathEqualTo("/1111"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(dialClientRcml)));

        // Phone2 register as alice
        SipURI uri = aliceSipStack.getAddressFactory().createSipURI(null, restcommContact);
        assertTrue(alicePhone.register(uri, "alice", "1234", aliceContact, 3600, 3600));

        // Prepare second phone to receive call
        SipCall aliceCall = alicePhone.createSipCall();
        aliceCall.listenForIncomingCall();

        // Create outgoing call with first phone
        final SipCall bobCall = bobPhone.createSipCall();
        bobCall.initiateOutgoingCall(bobContact, dialRestcomm, null);

        assertLastOperationSuccess(bobCall);
        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        final int response = bobCall.getLastReceivedResponse().getStatusCode();
        assertTrue(response == Response.BAD_REQUEST);
    }

    @Test
    @Category(FeatureExpTests.class)
    public synchronized void testDialClientAliceNullSDP() throws InterruptedException, ParseException {
        stubFor(get(urlPathEqualTo("/1111"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(dialClientRcml)));

        // Phone2 register as alice
        SipURI uri = aliceSipStack.getAddressFactory().createSipURI(null, restcommContact);
        assertTrue(alicePhone.register(uri, "alice", "1234", aliceContact, 3600, 3600));

        // Prepare second phone to receive call
        SipCall aliceCall = alicePhone.createSipCall();
        aliceCall.listenForIncomingCall();

        // Create outgoing call with first phone
        final SipCall bobCall = bobPhone.createSipCall();
        bobCall.initiateOutgoingCall(bobContact, dialRestcomm, null, null, "application", "sdp", null, null);
        assertLastOperationSuccess(bobCall);
        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        final int response = bobCall.getLastReceivedResponse().getStatusCode();
        assertTrue(response == Response.BAD_REQUEST);
    }

    final String screeningResponse = "<Response></Response>";
    @Test
    @Category(FeatureAltTests.class)
    public synchronized void testDialClientAliceWithScreeningAbsoluteURL() throws InterruptedException, ParseException {

        stubFor(get(urlPathEqualTo("/screening"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(screeningResponse)));

        stubFor(get(urlPathEqualTo("/1111"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(dialClientRcmlWithScreening)));


        // Phone2 register as alice
        SipURI uri = aliceSipStack.getAddressFactory().createSipURI(null, restcommContact);
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

    private String screeningRcml = "<Response><Say>Hi bob. Someone wants to talk to you</Say></Response>";
    @Test
    @Category(FeatureAltTests.class)
    public synchronized void testDialClientAliceWithScreeningRelativeURL() throws InterruptedException, ParseException {

        stubFor(get(urlPathEqualTo("/1111"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(dialClientRcmlWithScreeningRelative)));

        stubFor(get(urlPathEqualTo("/screening"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(screeningRcml)));

        // Phone2 register as alice
        SipURI uri = aliceSipStack.getAddressFactory().createSipURI(null, restcommContact);
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
        aliceCall.listenForDisconnect();
        bobCall.disconnect();

        assertTrue(aliceCall.waitForDisconnect(30 * 1000));
        assertTrue(aliceCall.respondToDisconnect());
    }



    @Deployment(name = "TestDialVerbPartOne", managed = true, testable = false)
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

        return WebArchiveUtil.createWebArchiveNoGw("restcomm.xml", "restcomm.script_dialTest_new", replacements);
    }

}
