package org.restcomm.connect.testsuite.http;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.cafesip.sipunit.SipAssert.assertLastOperationSuccess;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.net.URL;

import javax.sip.message.Response;

import org.apache.log4j.Logger;
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
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.restcomm.connect.commons.Version;
import org.restcomm.connect.commons.annotations.FeatureAltTests;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * LiveCallModificationMuteUnMuteTest
 * @author mariafarooq
 *
 */
@RunWith(Arquillian.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class LiveCallModificationMuteUnMuteTest {

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

    private SipStack mariaSipStack;
    private SipPhone mariaPhone;
    private String mariaContact = "sip:maria@127.0.0.1:5090";

    private SipStack buttSipStack;
    private SipPhone buttPhone;
    private String buttContact = "sip:+131313@127.0.0.1:5070";

    String dialConference = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<Response>\n" +
            "\t<Dial timeout=\"10\">\n" +
            "\t  <Conference muted=\"true\" startConferenceOnEnter=\"false\" beep=\"false\">Conf1234</Conference>\n" +
            "\t</Dial>\n" +
            "</Response>";
    private final String confRoom2 = "confRoom2";
    private String dialConfernceRcml = "<Response><Dial><Conference>"+confRoom2+"</Conference></Dial></Response>";

    private String dialRestcommConference = "sip:1111@127.0.0.1:5080";

    @BeforeClass
    public static void beforeClass() throws Exception {
        tool1 = new SipStackTool("LiveCallModification1");
        tool2 = new SipStackTool("LiveCallModification2");
    }

    @Before
    public void before() throws Exception {
        mariaSipStack = tool1.initializeSipStack(SipStack.PROTOCOL_UDP, "127.0.0.1", "5090", "127.0.0.1:5080");
        mariaPhone = mariaSipStack.createSipPhone("127.0.0.1", SipStack.PROTOCOL_UDP, 5080, mariaContact);

        buttSipStack = tool2.initializeSipStack(SipStack.PROTOCOL_UDP, "127.0.0.1", "5070", "127.0.0.1:5080");
        buttPhone = buttSipStack.createSipPhone("127.0.0.1", SipStack.PROTOCOL_UDP, 5080, buttContact);
    }

    @After
    public void after() throws Exception {
        if (mariaPhone != null) {
            mariaPhone.dispose();
        }
        if (mariaSipStack != null) {
            mariaSipStack.dispose();
        }

        if (buttPhone != null) {
            buttPhone.dispose();
        }
        if (buttSipStack != null) {
            buttSipStack.dispose();
        }
        Thread.sleep(1000);
        wireMockRule.resetRequests();
        Thread.sleep(4000);
    }

    /**
     * muteUnmuteInProgressConferenceParticipant
     * this test will:
     * 1. start a conference with 2 participants
     * 2. mute one praticipant
     * 3. unmute it
     * @throws Exception
     */
    @Test
    @Category(FeatureAltTests.class)
    public void muteUnmuteInProgressConferenceParticipant() throws Exception {
    	stubFor(get(urlPathEqualTo("/1111"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(dialConfernceRcml)));

    	// maria Dials the conference
        final SipCall mariaCall = mariaPhone.createSipCall();
        mariaCall.initiateOutgoingCall(mariaContact, dialRestcommConference, null, body, "application", "sdp", null, null);
        assertLastOperationSuccess(mariaCall);
        assertTrue(mariaCall.waitOutgoingCallResponse(5 * 1000));
        int responsemaria = mariaCall.getLastReceivedResponse().getStatusCode();
        assertTrue(responsemaria == Response.TRYING || responsemaria == Response.RINGING);

        if (responsemaria == Response.TRYING) {
            assertTrue(mariaCall.waitOutgoingCallResponse(5 * 1000));
            assertEquals(Response.RINGING, mariaCall.getLastReceivedResponse().getStatusCode());
        }

        assertTrue(mariaCall.waitOutgoingCallResponse(5 * 1000));
        assertEquals(Response.OK, mariaCall.getLastReceivedResponse().getStatusCode());
        mariaCall.sendInviteOkAck();
        assertTrue(!(mariaCall.getLastReceivedResponse().getStatusCode() >= 400));

    	// butt Dials the conference
        final SipCall buttCall = buttPhone.createSipCall();
        buttCall.initiateOutgoingCall(buttContact, dialRestcommConference, null, body, "application", "sdp", null, null);
        assertLastOperationSuccess(buttCall);
        assertTrue(buttCall.waitOutgoingCallResponse(5 * 1000));
        int responsebutt = buttCall.getLastReceivedResponse().getStatusCode();
        assertTrue(responsebutt == Response.TRYING || responsebutt == Response.RINGING);

        if (responsebutt == Response.TRYING) {
            assertTrue(buttCall.waitOutgoingCallResponse(5 * 1000));
            assertEquals(Response.RINGING, buttCall.getLastReceivedResponse().getStatusCode());
        }

        assertTrue(buttCall.waitOutgoingCallResponse(5 * 1000));
        assertEquals(Response.OK, buttCall.getLastReceivedResponse().getStatusCode());
        buttCall.sendInviteOkAck();
        assertTrue(!(buttCall.getLastReceivedResponse().getStatusCode() >= 400));

        Thread.sleep(2000);

        JsonObject confObject = RestcommConferenceTool.getInstance().getConferences(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        assertNotNull(confObject);
        JsonArray confArray = confObject.getAsJsonArray("conferences");
        assertNotNull(confArray);
        String conferenceSid = confArray.get(0).getAsJsonObject().get("sid").getAsString();
        assertNotNull(conferenceSid);
        JsonObject partObject = RestcommConferenceParticipantsTool.getInstance().getParticipants(deploymentUrl.toString(), adminAccountSid, adminAuthToken, conferenceSid);
        assertNotNull(partObject);
        JsonArray callsArray = partObject.getAsJsonArray("calls");
        int size = callsArray.size();
        assertEquals(2, size);
        
        logger.info("callsArray: "+callsArray);
        
        //any call sid
        String firstCallSid = callsArray.get(0).getAsJsonObject().get("sid").getAsString();

        //Going to mute : 
        JsonObject muteResponse = RestcommConferenceParticipantsTool.getInstance().modifyCall(deploymentUrl.toString(), adminAccountSid, conferenceSid, adminAuthToken, firstCallSid, true);
        assertNotNull(muteResponse);        
        JsonObject modifiedParticipant = RestcommConferenceParticipantsTool.getInstance().getParticipant(deploymentUrl.toString(), adminAccountSid, conferenceSid, adminAuthToken, firstCallSid);
        assertNotNull(modifiedParticipant);
        Boolean muted = modifiedParticipant.get("muted").getAsBoolean();
        assertTrue(muted);
        
        //Going to unmute : 
        JsonObject unmuteResponse = RestcommConferenceParticipantsTool.getInstance().modifyCall(deploymentUrl.toString(), adminAccountSid, conferenceSid, adminAuthToken, firstCallSid, false);
        assertNotNull(muteResponse);
        modifiedParticipant = RestcommConferenceParticipantsTool.getInstance().getParticipant(deploymentUrl.toString(), adminAccountSid, conferenceSid, adminAuthToken, firstCallSid);
        assertNotNull(modifiedParticipant);
        muted = modifiedParticipant.get("muted").getAsBoolean();
        assertTrue(!muted);
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
