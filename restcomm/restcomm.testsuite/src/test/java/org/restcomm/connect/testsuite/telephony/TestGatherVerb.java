package org.restcomm.connect.testsuite.telephony;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import gov.nist.javax.sip.header.ContentType;
import org.apache.log4j.Logger;
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
import org.junit.Rule;
import org.junit.Test;
import org.junit.FixMethodOrder;
import org.junit.runners.MethodSorters;
import org.junit.runner.RunWith;
import org.restcomm.connect.commons.Version;

import javax.sip.Dialog;
import javax.sip.SipException;
import javax.sip.message.Request;
import javax.sip.message.Response;
import java.net.URL;
import java.text.ParseException;
import java.util.List;

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
import org.junit.experimental.categories.Category;
import org.restcomm.connect.commons.annotations.FeatureAltTests;
import org.restcomm.connect.commons.annotations.SequentialClassTests;
import org.restcomm.connect.commons.annotations.WithInMinsTests;

/**
 * Test for Dial verb. Will test Dial Conference, Dial URI, Dial Client, Dial Number and Dial Fork
 *
 * @author <a href="mailto:hoan.h.luu@telestax.com">Hoan HL</a>
 */
@RunWith(Arquillian.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@Category(SequentialClassTests.class)
public class TestGatherVerb {
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

    private String dtmfWithSipInfo = "<Response><Gather action=\"http://127.0.0.1:8090/dtmfAction\" method=\"GET\" numDigits=\"3\"></Gather></Response>";
    private String hangupActionRcml = "<Response><Hangup /></Response>";
    @Test //Test case for github issue 2415
    public synchronized void testDtmfWithSipInfo() throws InterruptedException, ParseException {
        stubFor(get(urlPathEqualTo("/1111"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(dtmfWithSipInfo)));

        stubFor(get(urlPathEqualTo("/dtmfAction"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(hangupActionRcml)));

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

        Thread.sleep(3000);

        Dialog dialog = bobCall.getDialog();
        String infoBody =
                "Signal=3\r\n" +
                "Duration=28\r\n";
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

        SipTransaction infoTransaction = bobPhone.sendRequestWithTransaction(info, false, dialog);
        assertNotNull(infoTransaction);

        bobCall.listenForDisconnect();
        assertTrue(bobCall.waitForDisconnect(15000));
        assertTrue(bobCall.respondToDisconnect());

        List<LoggedRequest> recordActionRequests = findAll(getRequestedFor(urlPathMatching("/dtmfAction.*")));
        assertTrue(recordActionRequests.size()==1);
        logger.info(recordActionRequests.get(0).toString().contains("Digits=3"));
    }

    @Test //Test case for github issue 2415
    @Category(FeatureAltTests.class)
    public synchronized void testDtmfWithSipInfoWith3Number() throws InterruptedException, ParseException {
        stubFor(get(urlPathEqualTo("/1111"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(dtmfWithSipInfo)));

        stubFor(get(urlPathEqualTo("/dtmfAction"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(hangupActionRcml)));

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

        Thread.sleep(1000);

        Dialog dialog = bobCall.getDialog();
        String infoBody =
                "Signal=1\r\n" +
                "Duration=28\r\n";
        String infoBody2 =
                "Signal=2\r\n" +
                "Duration=28\r\n";
        String infoBody3 =
                "Signal=3\r\n" +
                "Duration=28\r\n";
        Request info = null;
        Request info2 = null;
        Request info3 = null;

        try {
            info = dialog.createRequest(Request.INFO);
            info2 = dialog.createRequest(Request.INFO);
            info3 = dialog.createRequest(Request.INFO);
        } catch (SipException e) {
            e.printStackTrace();
        }
        ContentType contentType = new ContentType();
        contentType.setContentType("application");
        contentType.setContentSubType("dtmf-relay");
        info.setContent(infoBody.getBytes(), contentType);
        info2.setContent(infoBody2.getBytes(), contentType);
        info3.setContent(infoBody3.getBytes(), contentType);

        SipTransaction infoTransaction = bobPhone.sendRequestWithTransaction(info, false, dialog);
        assertNotNull(infoTransaction);
        Thread.sleep(1000);
        SipTransaction infoTransaction2 = bobPhone.sendRequestWithTransaction(info2, false, dialog);
        assertNotNull(infoTransaction2);
        Thread.sleep(1000);
        SipTransaction infoTransaction3 = bobPhone.sendRequestWithTransaction(info3, false, dialog);
        assertNotNull(infoTransaction3);

        bobCall.listenForDisconnect();
        assertTrue(bobCall.waitForDisconnect(15000));
        assertTrue(bobCall.respondToDisconnect());

        List<LoggedRequest> recordActionRequests = findAll(getRequestedFor(urlPathMatching("/dtmfAction.*")));
        assertTrue(recordActionRequests.size()==1);
        logger.info(recordActionRequests.get(0).toString().contains("Digits=123"));
    }

    @Deployment(name = "TestDialVerbPartTwo", managed = true, testable = false)
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
        archive.addAsWebInfResource("restcomm_gather_test.xml", "conf/restcomm.xml");
        archive.addAsWebInfResource("restcomm.script_dialTest_new", "data/hsql/restcomm.script");
        archive.addAsWebResource("hello-play.xml");
        logger.info("Packaged Test App");
        return archive;
    }
}
