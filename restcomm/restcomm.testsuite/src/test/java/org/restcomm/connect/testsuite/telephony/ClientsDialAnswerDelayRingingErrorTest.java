package org.restcomm.connect.testsuite.telephony;

import static org.cafesip.sipunit.SipAssert.assertLastOperationSuccess;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.net.URL;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

import javax.sip.Dialog;
import javax.sip.address.SipURI;
import javax.sip.header.UserAgentHeader;
import javax.sip.message.Response;

import org.cafesip.sipunit.Credential;
import org.cafesip.sipunit.SipCall;
import org.cafesip.sipunit.SipPhone;
import org.cafesip.sipunit.SipRequest;
import org.cafesip.sipunit.SipStack;
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
import org.restcomm.connect.commons.annotations.SequentialClassTests;
import org.restcomm.connect.testsuite.http.CreateClientsTool;

import com.github.tomakehurst.wiremock.junit.WireMockRule;

/**
 * Test for clients with or without VoiceURL (Bitbucket issue 115). Clients without VoiceURL can dial anything.
 *
 * @author <a href="mailto:gvagenas@gmail.com">gvagenas</a>
 */
@RunWith(Arquillian.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@Category(value={SequentialClassTests.class})
public class ClientsDialAnswerDelayRingingErrorTest {

    private static final String version = Version.getVersion();

    private static final byte[] bytes = new byte[] { 118, 61, 48, 13, 10, 111, 61, 117, 115, 101, 114, 49, 32, 53, 51, 54, 53,
        53, 55, 54, 53, 32, 50, 51, 53, 51, 54, 56, 55, 54, 51, 55, 32, 73, 78, 32, 73, 80, 52, 32, 49, 50, 55, 46, 48, 46,
        48, 46, 49, 13, 10, 115, 61, 45, 13, 10, 99, 61, 73, 78, 32, 73, 80, 52, 32, 49, 50, 55, 46, 48, 46, 48, 46, 49,
        13, 10, 116, 61, 48, 32, 48, 13, 10, 109, 61, 97, 117, 100, 105, 111, 32, 54, 48, 48, 48, 32, 82, 84, 80, 47, 65,
        86, 80, 32, 48, 13, 10, 97, 61, 114, 116, 112, 109, 97, 112, 58, 48, 32, 80, 67, 77, 85, 47, 56, 48, 48, 48, 13, 10 };
    private static final String body = new String(bytes);

    private static final String webRtcBody = "v=0\n" +
            "o=- 655836341935372758 2 IN IP4 24.217.142.20\n" +
            "s=Restcomm B2BUA\n" +
            "t=0 0\n" +
            "a=group:BUNDLE audio video\n" +
            "a=msid-semantic:WMS ARDAMS\n" +
            "m=audio 9 UDP/TLS/RTP/SAVPF 111 103 9 102 0 8 106 105 13 126\n" +
            "c=IN IP4 24.217.142.20\n" +
            "a=rtcp:9 IN IP4 0.0.0.0\n" +
            "a=candidate:704553097 1 udp 2122260223 192.168.1.3 60475 typ host generation 0 ufrag PFs2 network-id 3 network-cost 10\n" +
            "a=candidate:2158047068 1 udp 1686052607 24.217.142.20 60475 typ srflx raddr 192.168.1.3 rport 60475 generation 0 ufrag PFs2 network-id 3 network-cost 10\n" +
            "a=candidate:152961445 1 udp 41885695 50.97.253.79 55954 typ relay raddr 24.217.142.20 rport 60475 generation 0 ufrag PFs2 network-id 3 network-cost 10\n" +
            "a=candidate:152961445 1 udp 41886207 50.97.253.79 49214 typ relay raddr 24.217.142.20 rport 60475 generation 0 ufrag PFs2 network-id 3 network-cost 10\n" +
            "a=candidate:1201536341 1 udp 25108735 50.97.253.79 63824 typ relay raddr 24.217.142.20 rport 54517 generation 0 ufrag PFs2 network-id 3 network-cost 10\n" +
            "a=candidate:1201536341 1 udp 25108223 50.97.253.79 50246 typ relay raddr 24.217.142.20 rport 33927 generation 0 ufrag PFs2 network-id 3 network-cost 10\n" +
            "a=ice-ufrag:PFs2\n" +
            "a=ice-pwd:MZHsJBkJ+vdWaNvb4Z7iCejt\n" +
            "a=fingerprint:sha-256 56:B1:98:06:06:EA:B1:BB:31:51:28:35:C7:8C:45:11:CD:3A:0D:28:C1:10:5B:D5:99:EA:21:46:8E:ED:08:89\n" +
            "a=setup:actpass\n" +
            "a=mid:audio\n" +
            "a=extmap:1 urn:ietf:params:rtp-hdrext:ssrc-audio-level\n" +
            "a=extmap:3 http://www.webrtc.org/experiments/rtp-hdrext/abs-send-time\n" +
            "a=sendrecv\n" +
            "a=rtcp-mux\n" +
            "a=rtpmap:111 opus/48000/2\n" +
            "a=rtcp-fb:111 transport-cc\n" +
            "a=fmtp:111 minptime=10;useinbandfec=1\n" +
            "a=rtpmap:103 ISAC/16000\n" +
            "a=rtpmap:9 G722/8000\n" +
            "a=rtpmap:102 ILBC/8000\n" +
            "a=rtpmap:0 PCMU/8000\n" +
            "a=rtpmap:8 PCMA/8000\n" +
            "a=rtpmap:106 CN/32000\n" +
            "a=rtpmap:105 CN/16000\n" +
            "a=rtpmap:13 CN/8000\n" +
            "a=rtpmap:126 telephone-event/8000\n" +
            "a=ssrc:743432057 cname:USPxCkO2V7yoEmg/\n" +
            "a=ssrc:743432057 msid:ARDAMS ARDAMSa0\n" +
            "a=ssrc:743432057 mslabel:ARDAMS\n" +
            "a=ssrc:743432057 label:ARDAMSa0\n" +
            "m=video 9 UDP/TLS/RTP/SAVPF 100 101 116 117 121 96 97 98 99\n" +
            "c=IN IP4 24.217.142.20\n" +
            "a=rtcp:9 IN IP4 0.0.0.0\n" +
            "a=candidate:704553097 1 udp 2122260223 192.168.1.3 40839 typ host generation 0 ufrag PFs2 network-id 3 network-cost 10\n" +
            "a=candidate:2158047068 1 udp 1686052607 24.217.142.20 40839 typ srflx raddr 192.168.1.3 rport 40839 generation 0 ufrag PFs2 network-id 3 network-cost 10\n" +
            "a=candidate:152961445 1 udp 41886207 50.97.253.79 53691 typ relay raddr 24.217.142.20 rport 40839 generation 0 ufrag PFs2 network-id 3 network-cost 10\n" +
            "a=candidate:152961445 1 udp 41885695 50.97.253.79 63503 typ relay raddr 24.217.142.20 rport 40839 generation 0 ufrag PFs2 network-id 3 network-cost 10\n" +
            "a=candidate:1201536341 1 udp 25108735 50.97.253.79 56359 typ relay raddr 24.217.142.20 rport 33643 generation 0 ufrag PFs2 network-id 3 network-cost 10\n" +
            "a=candidate:1201536341 1 udp 25108223 50.97.253.79 54107 typ relay raddr 24.217.142.20 rport 44827 generation 0 ufrag PFs2 network-id 3 network-cost 10\n" +
            "a=ice-ufrag:PFs2\n" +
            "a=ice-pwd:MZHsJBkJ+vdWaNvb4Z7iCejt\n" +
            "a=fingerprint:sha-256 56:B1:98:06:06:EA:B1:BB:31:51:28:35:C7:8C:45:11:CD:3A:0D:28:C1:10:5B:D5:99:EA:21:46:8E:ED:08:89\n" +
            "a=setup:actpass\n" +
            "a=mid:video\n" +
            "a=extmap:2 urn:ietf:params:rtp-hdrext:toffset\n" +
            "a=extmap:3 http://www.webrtc.org/experiments/rtp-hdrext/abs-send-time\n" +
            "a=extmap:4 urn:3gpp:video-orientation\n" +
            "a=extmap:6 http://www.webrtc.org/experiments/rtp-hdrext/playout-delay\n" +
            "a=sendrecv\n" +
            "a=rtcp-mux\n" +
            "a=rtcp-rsize\n" +
            "a=rtpmap:100 VP8/90000\n" +
            "a=rtcp-fb:100 ccm fir\n" +
            "a=rtcp-fb:100 nack\n" +
            "a=rtcp-fb:100 nack pli\n" +
            "a=rtcp-fb:100 goog-remb\n" +
            "a=rtcp-fb:100 transport-cc\n" +
            "a=rtpmap:101 VP9/90000\n" +
            "a=rtcp-fb:101 ccm fir\n" +
            "a=rtcp-fb:101 nack\n" +
            "a=rtcp-fb:101 nack pli\n" +
            "a=rtcp-fb:101 goog-remb\n" +
            "a=rtcp-fb:101 transport-cc\n" +
            "a=rtpmap:116 red/90000\n" +
            "a=rtpmap:117 ulpfec/90000\n" +
            "a=rtpmap:121 H264/90000\n" +
            "a=rtcp-fb:121 ccm fir\n" +
            "a=rtcp-fb:121 nack\n" +
            "a=rtcp-fb:121 nack pli\n" +
            "a=rtcp-fb:121 goog-remb\n" +
            "a=rtcp-fb:121 transport-cc\n" +
            "a=rtpmap:96 rtx/90000\n" +
            "a=fmtp:96 apt=100\n" +
            "a=rtpmap:97 rtx/90000\n" +
            "a=fmtp:97 apt=101\n" +
            "a=rtpmap:98 rtx/90000\n" +
            "a=fmtp:98 apt=116\n" +
            "a=rtpmap:99 rtx/90000\n" +
            "a=fmtp:99 apt=121\n" +
            "a=ssrc-group:FID 2457543170 3154322644\n" +
            "a=ssrc:2457543170 cname:USPxCkO2V7yoEmg/\n" +
            "a=ssrc:2457543170 msid:ARDAMS ARDAMSv0\n" +
            "a=ssrc:2457543170 mslabel:ARDAMS\n" +
            "a=ssrc:2457543170 label:ARDAMSv0\n" +
            "a=ssrc:3154322644 cname:USPxCkO2V7yoEmg/\n" +
            "a=ssrc:3154322644 msid:ARDAMS ARDAMSv0\n" +
            "a=ssrc:3154322644 mslabel:ARDAMS\n" +
            "a=ssrc:3154322644 label:ARDAMSv0";

    @ArquillianResource
    private Deployer deployer;
    @ArquillianResource
    URL deploymentUrl;

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(8090); // No-args constructor defaults to port 8080

    private static SipStackTool tool1;
    private static SipStackTool tool2;
    private static SipStackTool tool3;
    private static SipStackTool tool4;
    private static SipStackTool tool5;
    private static SipStackTool tool6;
    private static SipStackTool tool7;
    private static SipStackTool tool8;
    private static SipStackTool tool9;

    private String pstnNumber = "+151261006100";

    private String clientPassword = "qwerty1234RT";

    // Maria is a Restcomm Client **without** VoiceURL. This Restcomm Client can dial anything.
    private SipStack mariaSipStack;
    private SipPhone mariaPhone;
    private String mariaContact = "sip:maria@127.0.0.1:5092";
    private String mariaRestcommClientSid;

    // Dimitris is a Restcomm Client **without** VoiceURL. This Restcomm Client can dial anything.
    private SipStack dimitriSipStack;
    private SipPhone dimitriPhone;
    private String dimitriContact = "sip:dimitri@127.0.0.1:5093";
    private String dimitriRestcommClientSid;

    // Alice is a Restcomm Client with VoiceURL. This Restcomm Client can register with Restcomm and whatever will dial the RCML
    // of the VoiceURL will be executed.
    private SipStack aliceSipStack;
    private SipPhone alicePhone;
    private String aliceContact = "sip:alice@127.0.0.1:5091";

    private SipStack aliceSipStack2;
    private SipPhone alicePhone2;
    private String aliceContact2 = "sip:alice@127.0.0.1:5094";

    // George is a simple SIP Client. Will not register with Restcomm
    private SipStack georgeSipStack;
    private SipPhone georgePhone;
    private String georgeContact = "sip:"+pstnNumber+"@127.0.0.1:5070";

    private SipStack clientWithAppSipStack;
    private SipPhone clientWithAppPhone;
    private String clientWithAppContact = "sip:clientWithApp@127.0.0.1:5095";
    private String clientWithAppClientSid;

    private SipStack fotiniSipStackTcp;
    private SipPhone fotiniPhoneTcp;
    private String fotiniContactTcp = "sip:fotini@127.0.0.1:5096";
    private String fotiniClientSid;

    private SipStack bobSipStackTcp;
    private SipPhone bobPhoneTcp;
    private String bobContactTcp = "sip:bob@127.0.0.1:5097";

    private SipStack leftySipStack;
    private SipPhone leftyPhone;
    private String leftyContact = "sip:lefty@127.0.0.1:5098";
    private String leftyRestcommClientSid;

    private String adminAccountSid = "ACae6e420f425248d6a26948c17a9e2acf";
    private String adminAuthToken = "77f8c12cc7b8f8423e5c38b035249166";

    @BeforeClass
    public static void beforeClass() throws Exception {
        tool1 = new SipStackTool("ClientsDialAnswerDelayTest1");
        tool2 = new SipStackTool("ClientsDialAnswerDelayTest2");
        tool3 = new SipStackTool("ClientsDialAnswerDelayTest3");
        tool4 = new SipStackTool("ClientsDialAnswerDelayTest4");
        tool5 = new SipStackTool("ClientsDialAnswerDelayTest5");
        tool6 = new SipStackTool("ClientsDialAnswerDelayTest6");
        tool7 = new SipStackTool("ClientsDialAnswerDelayTest7");
        tool8 = new SipStackTool("ClientsDialAnswerDelayTest8");
        tool9 = new SipStackTool("ClientsDialAnswerDelayTest9");
    }

    @Before
    public void before() throws Exception {

        aliceSipStack = tool1.initializeSipStack(SipStack.PROTOCOL_UDP, "127.0.0.1", "5091", "127.0.0.1:5080");
        alicePhone = aliceSipStack.createSipPhone("127.0.0.1", SipStack.PROTOCOL_UDP, 5080, aliceContact);

        aliceSipStack2 = tool5.initializeSipStack(SipStack.PROTOCOL_UDP, "127.0.0.1", "5094", "127.0.0.1:5080");
        alicePhone2 = aliceSipStack2.createSipPhone("127.0.0.1", SipStack.PROTOCOL_UDP, 5080, aliceContact2);

        mariaSipStack = tool2.initializeSipStack(SipStack.PROTOCOL_UDP, "127.0.0.1", "5092", "127.0.0.1:5080");
        mariaPhone = mariaSipStack.createSipPhone("127.0.0.1", SipStack.PROTOCOL_UDP, 5080, mariaContact);

        dimitriSipStack = tool3.initializeSipStack(SipStack.PROTOCOL_UDP, "127.0.0.1", "5093", "127.0.0.1:5080");
        dimitriPhone = dimitriSipStack.createSipPhone("127.0.0.1", SipStack.PROTOCOL_UDP, 5080, dimitriContact);

        georgeSipStack = tool4.initializeSipStack(SipStack.PROTOCOL_UDP, "127.0.0.1", "5070", "127.0.0.1:5080");
        georgePhone = georgeSipStack.createSipPhone("127.0.0.1", SipStack.PROTOCOL_UDP, 5080, georgeContact);

        clientWithAppSipStack = tool6.initializeSipStack(SipStack.PROTOCOL_UDP, "127.0.0.1", "5095", "127.0.0.1:5080");
        clientWithAppPhone = clientWithAppSipStack.createSipPhone("127.0.0.1", SipStack.PROTOCOL_UDP, 5080, clientWithAppContact);

        mariaRestcommClientSid = CreateClientsTool.getInstance().createClient(deploymentUrl.toString(), "maria", clientPassword, null);
        dimitriRestcommClientSid = CreateClientsTool.getInstance().createClient(deploymentUrl.toString(), "dimitri", clientPassword, null);
        clientWithAppClientSid = CreateClientsTool.getInstance().createClient(deploymentUrl.toString(), "clientWithApp", clientPassword, "http://127.0.0.1:8090/1111");

        fotiniSipStackTcp = tool7.initializeSipStack(SipStack.PROTOCOL_TCP, "127.0.0.1", "5096", "127.0.0.1:5080");
        fotiniPhoneTcp = fotiniSipStackTcp.createSipPhone("127.0.0.1", SipStack.PROTOCOL_TCP, 5080, fotiniContactTcp);
        fotiniClientSid = CreateClientsTool.getInstance().createClient(deploymentUrl.toString(), "fotini", clientPassword, null);

        bobSipStackTcp = tool8.initializeSipStack(SipStack.PROTOCOL_TCP, "127.0.0.1", "5097", "127.0.0.1:5080");
        bobPhoneTcp = bobSipStackTcp.createSipPhone("127.0.0.1", SipStack.PROTOCOL_TCP, 5080, bobContactTcp);

        leftySipStack = tool9.initializeSipStack(SipStack.PROTOCOL_UDP, "127.0.0.1", "5098", "127.0.0.1:5080");
        leftyPhone = leftySipStack.createSipPhone("127.0.0.1", SipStack.PROTOCOL_UDP, 5080, leftyContact);
    }

    @After
    public void after() throws Exception {
        if (mariaPhone != null) {
            mariaPhone.dispose();
        }
        if (mariaSipStack != null) {
            mariaSipStack.dispose();
        }

        if (aliceSipStack != null) {
            aliceSipStack.dispose();
        }
        if (alicePhone != null) {
            alicePhone.dispose();
        }

        if (dimitriSipStack != null) {
            dimitriSipStack.dispose();
        }
        if (dimitriPhone != null) {
            dimitriPhone.dispose();
        }

        if (georgePhone != null) {
            georgePhone.dispose();
        }
        if (georgeSipStack != null) {
            georgeSipStack.dispose();
        }

        if (clientWithAppPhone != null) {
            clientWithAppPhone.dispose();
        }
        if (clientWithAppSipStack != null) {
            clientWithAppSipStack.dispose();
        }
        if (fotiniSipStackTcp != null) {
            fotiniSipStackTcp.dispose();
        }
        if (fotiniPhoneTcp != null) {
            fotiniPhoneTcp.dispose();
        }
        if (bobSipStackTcp != null) {
            bobSipStackTcp.dispose();
        }
        if (bobPhoneTcp != null) {
            bobPhoneTcp.dispose();
        }
        Thread.sleep(3000);
        wireMockRule.resetRequests();
        Thread.sleep(3000);
    }

    @Test //Non regression test for issue https://github.com/RestComm/Restcomm-Connect/issues/1042 - Support WebRTC clients to dial out through MediaServer
    public void testClientDialOutPstnSimulateWebRTCClient() throws ParseException, InterruptedException {

        assertNotNull(mariaRestcommClientSid);
        assertNotNull(dimitriRestcommClientSid);

        SipURI uri = mariaSipStack.getAddressFactory().createSipURI(null, "127.0.0.1:5080");
        assertTrue(mariaPhone.register(uri, "maria", clientPassword, mariaContact, 14400, 3600));
        Thread.sleep(3000);

        Credential c = new Credential("127.0.0.1", "maria", clientPassword);
        mariaPhone.addUpdateCredential(c);

        final SipCall georgeCall = georgePhone.createSipCall();
        georgeCall.listenForIncomingCall();


        Thread.sleep(1000);

        //Change UserAgent header to "sipunit" so CallManager
        ArrayList<String> replaceHeaders = new ArrayList<String>();
        List<String> userAgentList = new ArrayList<String>();
        userAgentList.add("wss-sipunit");
        UserAgentHeader userAgentHeader = mariaSipStack.getHeaderFactory().createUserAgentHeader(userAgentList);
        replaceHeaders.add(userAgentHeader.toString());

        // Maria initiates a call to Dimitri
        final SipCall mariaCall = mariaPhone.createSipCall();
        mariaCall.initiateOutgoingCall(mariaContact, "sip:"+pstnNumber+"@127.0.0.1:5080", null, body, "application", "sdp", null, replaceHeaders);
        assertLastOperationSuccess(mariaCall);
        assertTrue(mariaCall.waitForAuthorisation(3000));

        assertTrue(mariaCall.waitOutgoingCallResponse(5 * 1000));
        int responseMaria = mariaCall.getLastReceivedResponse().getStatusCode();
        assertTrue(responseMaria == Response.TRYING || responseMaria == Response.RINGING);

        Dialog mariaDialog = null;

        if (responseMaria == Response.TRYING) {
            assertTrue(mariaCall.waitOutgoingCallResponse(5 * 1000));
            assertEquals(Response.RINGING, mariaCall.getLastReceivedResponse().getStatusCode());
            mariaDialog = mariaCall.getDialog();
            assertNotNull(mariaDialog);
        }

        assertTrue(georgeCall.waitForIncomingCall(5 * 1000));
        assertTrue(georgeCall.sendIncomingCallResponse(Response.RINGING, "RINGING-George", 3600));

        SipRequest lastReceivedRequest = georgeCall.getLastReceivedRequest();
        String receivedBody = new String(lastReceivedRequest.getRawContent());
        assertTrue(georgeCall.sendIncomingCallResponse(Response.OK, "OK-George", 3600, receivedBody, "application", "sdp", null,
                null));

        assertTrue(mariaCall.waitOutgoingCallResponse(5 * 1000));
        assertEquals(Response.OK, mariaCall.getLastReceivedResponse().getStatusCode());
        assertTrue(mariaCall.sendInviteOkAck());

        //        For a reason the ACK will never reach Restcomm. This is only when working with the sipUnit
        //        assertTrue(georgeCall.waitForAck(5 * 1000));

        Thread.sleep(3000);
        georgeCall.listenForDisconnect();
        assertTrue(mariaCall.disconnect());

        //        assertTrue(georgeCall.waitForDisconnect(5 * 1000));
        //        assertTrue(georgeCall.respondToDisconnect());
    }

    @Deployment(name = "ClientsDialAnswerDelayTest", managed = true, testable = false)
    public static WebArchive createWebArchiveNoGw() {
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
        archive.addAsWebInfResource("restcomm-delay-ringing-error.xml", "conf/restcomm.xml");
        archive.addAsWebInfResource("restcomm.script_dialTest", "data/hsql/restcomm.script");
        archive.addAsWebResource("dial-conference-entry.xml");
        archive.addAsWebResource("dial-fork-entry.xml");
        archive.addAsWebResource("dial-uri-entry.xml");
        archive.addAsWebResource("dial-client-entry.xml");
        archive.addAsWebResource("dial-number-entry.xml");
        return archive;
    }

}
