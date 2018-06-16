package org.restcomm.connect.testsuite.telephony;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import gov.nist.javax.sip.message.MessageExt;
import org.apache.log4j.Logger;
import org.cafesip.sipunit.SipCall;
import org.cafesip.sipunit.SipPhone;
import org.cafesip.sipunit.SipStack;
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
import org.restcomm.connect.testsuite.telephony.security.DigestServerAuthenticationMethod;

import javax.sip.address.SipURI;
import javax.sip.header.Header;
import javax.sip.header.ProxyAuthenticateHeader;
import javax.sip.header.ProxyAuthorizationHeader;
import javax.sip.message.Request;
import javax.sip.message.Response;
import java.net.URL;
import java.text.ParseException;
import java.util.ArrayList;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
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
import org.restcomm.connect.commons.annotations.UnstableTests;
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
public class TestDialVerbPartThree {
    private final static Logger logger = Logger.getLogger(TestDialVerbPartThree.class.getName());

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

    private static int mediaPort = NetworkPortAssigner.retrieveNextPortByFile();

    private static int mockPort = NetworkPortAssigner.retrieveNextPortByFile();
    @Rule
    public WireMockRule wireMockRule = new WireMockRule(mockPort); // No-args constructor defaults to port 8080
    private final String recordWithActionRcml = "<Response><Record action=\"http://127.0.0.1:" + mockPort + "/recordAction\" method=\"GET\" finishOnKey=\"#\" maxLength=\"10\" playBeep=\"true\"/></Response>";
    private final String recordWithActionRcmNullFinishOnKey = "<Response><Record action=\"http://127.0.0.1:" + mockPort + "/recordAction\" method=\"GET\" finishOnKey=\"-1\" maxLength=\"10\" playBeep=\"true\"/></Response>";
    private final String dialSipTagScreeningRcml = "<Response>\n" +
            "\t<Dial timeLimit=\"10\" timeout=\"10\" action=\"http://127.0.0.1:" + restcommHTTPPort + "/restcomm/sip-dial-url-screening-test.jsp\" method=\"GET\">\n" +
            "\t  <Sip url=\"http://127.0.0.1:" + mockPort + "/screening\" method=\"GET\">sip:alice@127.0.0.1:" + alicePort + "?mycustomheader=foo&myotherheader=bar</Sip>\n" +
            "\t</Dial>\n" +
            "</Response>";
    private final String dialSipDialScreeningRcml = "<Response>\n" +
            "\t<Dial timeLimit=\"10\" timeout=\"10\" action=\"http://127.0.0.1:" + mockPort + "/action\" method=\"GET\">\n" +
            "\t  <Sip url=\"http://127.0.0.1:" + mockPort + "/screening\" method=\"GET\">sip:alice@127.0.0.1:" + alicePort + "?mycustomheader=foo&myotherheader=bar</Sip>\n" +
            "\t</Dial>\n" +
            "</Response>";

    private static SipStackTool tool1;
    private static SipStackTool tool2;
//    private static SipStackTool tool3;
//    private static SipStackTool tool4;

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
    private final String actionUrlRcml = "<Dial timeout=\"50\"><Uri>sip:alice@127.0.0.1:" + alicePort + "</Uri></Dial>";
    private final String dialSipRcml = "<Response><Dial timeLimit=\"10\" timeout=\"10\"><Sip>sip:alice@127.0.0.1:" + alicePort + "?mycustomheader=foo&myotherheader=bar</Sip></Dial></Response>";
    private final String dialSipAuthRcml = "<Response><Dial timeLimit=\"10\" timeout=\"10\"><Sip username=\"alice\" password=\"1234\">sip:alice@127.0.0.1:" + alicePort + "?mycustomheader=foo&myotherheader=bar</Sip></Dial></Response>";


    private static int restcommPort = 5080;
    private static int restcommHTTPPort = 8080;
    private static String restcommContact = "127.0.0.1:" + restcommPort;
    private static String dialRestcomm = "sip:1111@" + restcommContact;


    @BeforeClass
    public static void beforeClass() throws Exception {
        tool1 = new SipStackTool("DialTest3Tool1");
        tool2 = new SipStackTool("DialTest3Tool2");
    }

    public static void reconfigurePorts() {
        if (System.getProperty("arquillian_sip_port") != null) {
            restcommPort = Integer.valueOf(System.getProperty("arquillian_sip_port"));
            restcommContact = "127.0.0.1:" + restcommPort;
            dialRestcomm = "sip:1111@" + restcommContact;
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
        Thread.sleep(3000);
        wireMockRule.resetRequests();
        Thread.sleep(2000);
    }


//Non regression test for https://github.com/Mobicents/RestComm/issues/612
    @Test
    @Category({UnstableTests.class, FeatureAltTests.class})
    public synchronized void testRecord_ExecuteRCML_ReturnedFromActionURL() throws InterruptedException, ParseException {

        stubFor(get(urlPathEqualTo("/1111"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(recordWithActionRcml)));

        stubFor(get(urlPathEqualTo("/recordAction"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(actionUrlRcml)));

        //Prepare Fotini phone to receive a call
        final SipCall aliceCall = alicePhone.createSipCall();
        aliceCall.listenForIncomingCall();

        // Initiate a call using Bob
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

        //At this point bob leaves a voicemail

        //Now Fotini should receive a call
        assertTrue(aliceCall.waitForIncomingCall(30 * 1000));
        assertTrue(aliceCall.sendIncomingCallResponse(100, "Trying-Fotini", 600));
        assertTrue(aliceCall.sendIncomingCallResponse(180, "Ringing-Fotini", 600));
        String receivedBody = new String(aliceCall.getLastReceivedRequest().getRawContent());
        assertTrue(aliceCall.sendIncomingCallResponse(Response.OK, "OK-Fotini", 3600, receivedBody, "application", "sdp", null, null));
        assertTrue(aliceCall.waitForAck(5000));
        aliceCall.listenForDisconnect();

        Thread.sleep(2000);

        // hangup.

        assertTrue(bobCall.disconnect());

        assertTrue(aliceCall.waitForDisconnect(50 * 1000));
        assertTrue(aliceCall.respondToDisconnect());
    }

    //Non regression test for https://github.com/Mobicents/RestComm/issues/612
    @Test
    @Category({UnstableTests.class, FeatureAltTests.class})
    public synchronized void testRecord_ExecuteRCML_ReturnedFromActionURLWithNullFinishOnKey() throws InterruptedException, ParseException {

        stubFor(get(urlPathEqualTo("/1111"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(recordWithActionRcmNullFinishOnKey)));

        stubFor(get(urlPathEqualTo("/recordAction"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(actionUrlRcml)));

        //Prepare Fotini phone to receive a call
        final SipCall aliceCall = alicePhone.createSipCall();
        aliceCall.listenForIncomingCall();

        // Initiate a call using Bob
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

        //At this point bob leaves a voicemail

        //Now Fotini should receive a call
        assertTrue(aliceCall.waitForIncomingCall(30 * 1000));
        assertTrue(aliceCall.sendIncomingCallResponse(100, "Trying-Fotini", 600));
        assertTrue(aliceCall.sendIncomingCallResponse(180, "Ringing-Fotini", 600));
        String receivedBody = new String(aliceCall.getLastReceivedRequest().getRawContent());
        assertTrue(aliceCall.sendIncomingCallResponse(Response.OK, "OK-Fotini", 3600, receivedBody, "application", "sdp", null, null));
        assertTrue(aliceCall.waitForAck(5000));
        aliceCall.listenForDisconnect();

        Thread.sleep(2000);

        // hangup.

        assertTrue(bobCall.disconnect());

        assertTrue(aliceCall.waitForDisconnect(50 * 1000));
        assertTrue(aliceCall.respondToDisconnect());
    }

    private String playRcml = "<Play>/restcomm/audio/demo-prompt.wav</Play>";
    //Non regression test for https://telestax.atlassian.net/browse/RESTCOMM-585
    @Test
    @Category(FeatureAltTests.class)
    public synchronized void testDialWithCustomHeaders() throws InterruptedException, ParseException {
        //Received request: GET /rcml?CallSid=CA154c8c93d7eb439989a6ea42915b6c1b&AccountSid=ACae6e420f425248d6a26948c17a9e2acf&From=bob&To=%2B17778&
        //CallStatus=ringing&ApiVersion=2012-04-24&Direction=inbound&CallerName&ForwardedFrom&SipHeader_X-MyCustom-Header1=Value1&SipHeader_X-MyCustom-Header2=Value2 HTTP/1.1
        stubFor(get(urlPathEqualTo("/1111"))
                .withQueryParam("SipHeader_X-MyCustom-Header1", containing("Value1"))
                .withQueryParam("SipHeader_X-MyCustom-Header2", containing("Value2"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(playRcml)));

        ArrayList<String> additionalHeaders = new ArrayList<String>();
        additionalHeaders.add(bobPhone.getParent().getHeaderFactory().createHeader("X-MyCustom-Header1", "Value1").toString());
        additionalHeaders.add(bobPhone.getParent().getHeaderFactory().createHeader("X-MyCustom-Header2", "Value2").toString());

        // Initiate a call using Bob
        final SipCall bobCall = bobPhone.createSipCall();

        bobCall.initiateOutgoingCall(bobContact, dialRestcomm, null, body, "application", "sdp", additionalHeaders, null);
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

        Thread.sleep(1000);

        assertTrue(bobCall.waitForDisconnect(5 * 1000));
        assertTrue(bobCall.respondToDisconnect());
    }

    @Test
    @Category(UnstableTests.class)
// Non regression test for https://bitbucket.org/telestax/telscale-restcomm/issue/132/implement-twilio-sip-out
    public synchronized void testDialSip() throws InterruptedException, ParseException {
        stubFor(get(urlPathEqualTo("/1111"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(dialSipRcml)));

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
        MessageExt invite = (MessageExt) aliceCall.getLastReceivedRequest().getMessage();
        assertNotNull(invite);
        assertEquals(Request.INVITE, invite.getCSeqHeader().getMethod());
        Header mycustomheader = invite.getHeader("X-mycustomheader");
        Header myotherheader = invite.getHeader("X-myotherheader");
        assertNotNull(mycustomheader);
        assertNotNull(myotherheader);

        assertTrue(aliceCall.sendIncomingCallResponse(Response.RINGING, "Ringing-Alice", 3600));
        String receivedBody = new String(aliceCall.getLastReceivedRequest().getRawContent());
        assertTrue(aliceCall.sendIncomingCallResponse(Response.OK, "OK-Alice", 3600, receivedBody, "application", "sdp", null,
                null));
        assertTrue(aliceCall.waitForAck(50 * 1000));

        Thread.sleep(3000);

        // hangup.
        bobCall.disconnect();

        aliceCall.disconnect();
        assertTrue(aliceCall.waitForDisconnect(30 * 1000));
    }


//    @Ignore
    @Test
// Non regression test for https://bitbucket.org/telestax/telscale-restcomm/issue/132/implement-twilio-sip-out
// in auth manner
    public synchronized void testDialSipAuth() throws InterruptedException, ParseException {
        stubFor(get(urlPathEqualTo("/1111"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(dialSipAuthRcml)));

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
        MessageExt invite = (MessageExt) aliceCall.getLastReceivedRequest().getMessage();
        assertNotNull(invite);
        assertEquals(Request.INVITE, invite.getCSeqHeader().getMethod());
        Header mycustomheader = invite.getHeader("X-mycustomheader");
        Header myotherheader = invite.getHeader("X-myotherheader");
        assertNotNull(mycustomheader);
        assertNotNull(myotherheader);

        DigestServerAuthenticationMethod dsam = new DigestServerAuthenticationMethod();
        dsam.initialize(); // it should read values from file, now all static

        ProxyAuthenticateHeader proxyAuthenticate = aliceSipStack.getHeaderFactory().createProxyAuthenticateHeader(
                dsam.getScheme());
        proxyAuthenticate.setParameter("realm", dsam.getRealm(null));
        proxyAuthenticate.setParameter("nonce", dsam.generateNonce());
        // proxyAuthenticateImpl.setParameter("domain",authenticationMethod.getDomain());
        proxyAuthenticate.setParameter("opaque", "");

        proxyAuthenticate.setParameter("algorithm", dsam.getAlgorithm());
        ArrayList<Header> headers = new ArrayList<Header>();
        headers.add(proxyAuthenticate);
        assertTrue(aliceCall.sendIncomingCallResponse(Response.PROXY_AUTHENTICATION_REQUIRED, "Non authorized", 3600, headers,
                null, null));

        assertTrue(aliceCall.waitForIncomingCall(30 * 1000));
        invite = (MessageExt) aliceCall.getLastReceivedRequest().getMessage();
        assertNotNull(invite.getHeader(ProxyAuthorizationHeader.NAME));

        ProxyAuthorizationHeader proxyAuthorization = (ProxyAuthorizationHeader) invite
                .getHeader(ProxyAuthorizationHeader.NAME);

        boolean res = dsam.doAuthenticate("alice", "1234", proxyAuthorization, (Request) invite);
        assertTrue(res);

        assertTrue(aliceCall.sendIncomingCallResponse(Response.RINGING, "Ringing-Alice", 3600));
        String receivedBody = new String(aliceCall.getLastReceivedRequest().getRawContent());
        assertTrue(aliceCall.sendIncomingCallResponse(Response.OK, "OK-Alice", 3600, receivedBody, "application", "sdp", null,
                null));
        assertTrue(aliceCall.waitForAck(50 * 1000));
        aliceCall.listenForDisconnect();

        Thread.sleep(3000);

        // hangup.
        bobCall.disconnect();

        assertTrue(aliceCall.waitForDisconnect(30 * 1000));
    }

    private String screeningRcml = "<Response><Hangup/></Response>";
    @Test
// Non regression test for https://bitbucket.org/telestax/telscale-restcomm/issue/132/implement-twilio-sip-out
// with URL screening
    @Category(FeatureAltTests.class)
    public synchronized void testDialSipTagScreening() throws InterruptedException, ParseException {
        stubFor(get(urlPathEqualTo("/1111"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(dialSipTagScreeningRcml)));

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
        MessageExt invite = (MessageExt) aliceCall.getLastReceivedRequest().getMessage();
        assertNotNull(invite);
        assertEquals(Request.INVITE, invite.getCSeqHeader().getMethod());
        Header mycustomheader = invite.getHeader("X-mycustomheader");
        Header myotherheader = invite.getHeader("X-myotherheader");
        assertNotNull(mycustomheader);
        assertNotNull(myotherheader);

        assertTrue(aliceCall.sendIncomingCallResponse(Response.RINGING, "Ringing-Alice", 3600));
        String receivedBody = new String(aliceCall.getLastReceivedRequest().getRawContent());
        ArrayList<String> headers = new ArrayList<String>();
        Header customHeader = aliceSipStack.getHeaderFactory().createHeader("X-mycustomheader", "customValue");
        Header otherHeader = aliceSipStack.getHeaderFactory().createHeader("X-myothereader", "customOtherValue");
        headers.add(customHeader.toString());
        headers.add(otherHeader.toString());
        assertTrue(aliceCall.sendIncomingCallResponse(Response.OK, "OK-Alice", 3600, receivedBody, "application", "sdp",
                headers, null));
        assertTrue(aliceCall.waitForAck(50 * 1000));

        Thread.sleep(3000);

        // hangup.
        bobCall.disconnect();

        aliceCall.disconnect();
        assertTrue(aliceCall.waitForDisconnect(30 * 1000));
    }


    private String sipDialUrlActionRcml = "<Response><Hangup/></Response>";
    @Test
// Non regression test for https://bitbucket.org/telestax/telscale-restcomm/issue/132/implement-twilio-sip-out
// with Dial Action screening
    @Category(FeatureAltTests.class)
    public synchronized void testDialSipDialTagScreening() throws InterruptedException, ParseException {
        stubFor(get(urlPathEqualTo("/1111"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(dialSipDialScreeningRcml)));

        stubFor(get(urlPathEqualTo("/screening"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(screeningRcml)));

        stubFor(get(urlPathEqualTo("/action"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(sipDialUrlActionRcml)));

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

        bobCall.listenForDisconnect();

        assertTrue(aliceCall.waitForIncomingCall(30 * 1000));
        MessageExt invite = (MessageExt) aliceCall.getLastReceivedRequest().getMessage();
        assertNotNull(invite);
        assertEquals(Request.INVITE, invite.getCSeqHeader().getMethod());
        Header mycustomheader = invite.getHeader("X-mycustomheader");
        Header myotherheader = invite.getHeader("X-myotherheader");
        assertNotNull(mycustomheader);
        assertNotNull(myotherheader);

        String receivedBody = new String(aliceCall.getLastReceivedRequest().getRawContent());
        ArrayList<String> headers = new ArrayList<String>();
        Header customHeader = aliceSipStack.getHeaderFactory().createHeader("X-mycustomheader", "customValue");
        Header otherHeader = aliceSipStack.getHeaderFactory().createHeader("X-myothereader", "customOtherValue");
        headers.add(customHeader.toString());
        headers.add(otherHeader.toString());
        assertTrue(aliceCall.sendIncomingCallResponse(Response.NOT_FOUND, "Not-Found", 3600, receivedBody, "application",
                "sdp", headers, null));
        assertTrue(aliceCall.waitForAck(50 * 1000));

        assertTrue(bobCall.waitForDisconnect(5000));
        assertTrue(bobCall.respondToDisconnect());

    }

    @Test
// Non regression test for https://bitbucket.org/telestax/telscale-restcomm/issue/132/implement-twilio-sip-out
// with Dial Action screening
    @Category(FeatureAltTests.class)
    public synchronized void testDialSipDialTagScreening180Decline() throws InterruptedException, ParseException {
        stubFor(get(urlPathEqualTo("/1111"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(dialSipDialScreeningRcml)));

        stubFor(get(urlPathEqualTo("/screening"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(screeningRcml)));

        stubFor(get(urlPathEqualTo("/action"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(sipDialUrlActionRcml)));

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

        bobCall.listenForDisconnect();

        assertTrue(aliceCall.waitForIncomingCall(30 * 1000));
        MessageExt invite = (MessageExt) aliceCall.getLastReceivedRequest().getMessage();
        assertNotNull(invite);
        assertEquals(Request.INVITE, invite.getCSeqHeader().getMethod());
        Header mycustomheader = invite.getHeader("X-mycustomheader");
        Header myotherheader = invite.getHeader("X-myotherheader");
        assertNotNull(mycustomheader);
        assertNotNull(myotherheader);

        String receivedBody = new String(aliceCall.getLastReceivedRequest().getRawContent());

        assertTrue(aliceCall.sendIncomingCallResponse(Response.RINGING, "Ringing", 3600, receivedBody, "application", "sdp",
                null, null));

        ArrayList<String> headers = new ArrayList<String>();
        Header customHeader = aliceSipStack.getHeaderFactory().createHeader("X-mycustomheader", "customValue");
        Header otherHeader = aliceSipStack.getHeaderFactory().createHeader("X-myothereader", "customOtherValue");
        headers.add(customHeader.toString());
        headers.add(otherHeader.toString());
        assertTrue(aliceCall.sendIncomingCallResponse(Response.DECLINE, "Declined", 3600, receivedBody, "application", "sdp",
                headers, null));
        assertTrue(aliceCall.waitForAck(50 * 1000));

        assertTrue(bobCall.waitForDisconnect(5000));
        assertTrue(bobCall.respondToDisconnect());
    }

    private String dialClientRcml = "<Response><Dial timeLimit=\"10\" timeout=\"10\"><Client>alice</Client></Dial></Response>";
    @Test //For github issue #600, At the DB the IncomingPhoneNumber is '1111' and we dial '+1111', Restcomm should find this number even with the '+'
    @Category(FeatureAltTests.class)
    public synchronized void testDialClientAliceWithPlusSign() throws InterruptedException, ParseException {
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
        bobCall.initiateOutgoingCall(bobContact, "sip:+1111@" + restcommContact + "", null, body, "application", "sdp", null, null);
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

    @Test //For github issue #600, At the DB the IncomingPhoneNumber is '+2222' and we dial '2222', Restcomm should find this number even without the '+'
    @Category({FeatureAltTests.class, UnstableTests.class})
    public synchronized void testDialClientAliceWithoutPlusSign() throws InterruptedException, ParseException {
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
        bobCall.initiateOutgoingCall(bobContact, "sip:2222@" + restcommContact + "", null, body, "application", "sdp", null, null);
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

    @Deployment(name = "TestDialVerbPartThree", managed = true, testable = false)
    public static WebArchive createWebArchiveNoGw() {
        logger.info("Packaging Test App");
        reconfigurePorts();

        Map<String,String> replacements = new HashMap();
        //replace mediaport 2727
        replacements.put("2727", String.valueOf(mediaPort));
        replacements.put("8080", String.valueOf(restcommHTTPPort));
        replacements.put("8090", String.valueOf(mockPort));
        replacements.put("5080", String.valueOf(restcommPort));
        replacements.put("5090", String.valueOf(bobPort));
        replacements.put("5091", String.valueOf(alicePort));

        return WebArchiveUtil.createWebArchiveNoGw("restcomm.xml", "restcomm.script_dialTest_new", replacements);
    }

}
