package org.restcomm.connect.testsuite.telephony;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.apache.log4j.Logger;
import org.cafesip.sipunit.SipCall;
import org.cafesip.sipunit.SipPhone;
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
import org.junit.runner.RunWith;
import org.restcomm.connect.commons.Version;
import org.restcomm.connect.commons.annotations.FeatureAltTests;
import org.restcomm.connect.testsuite.tools.MonitoringServiceTool;

import javax.sip.message.Response;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;

import static org.cafesip.sipunit.SipAssert.assertLastOperationSuccess;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.experimental.categories.Category;
import org.restcomm.connect.commons.annotations.SequentialClassTests;
import org.restcomm.connect.commons.annotations.WithInMinsTests;

/**
 * Created by gvagenas on 26/06/2017.
 */
@RunWith(Arquillian.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@Category({FeatureAltTests.class, SequentialClassTests.class})
public class RestcommActingAsProxyTest {

    private final static Logger logger = Logger.getLogger(RestcommActingAsProxyTest.class.getName());

    @Deployment(name = "RestcommActingAsProxyTest", managed = true, testable = false)
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
        archive.delete("/WEB-INF/classes/application.conf");
        archive.addAsWebInfResource("sip.xml");
        archive.addAsWebInfResource("web.xml");
        archive.addAsWebInfResource("restcomm_acting_as_proxy.xml", "conf/restcomm.xml");
        archive.addAsWebInfResource("restcomm.script_acting_as_proxy", "data/hsql/restcomm.script");
        archive.addAsWebInfResource("akka_application.conf", "classes/application.conf");
        logger.info("Packaged Test App");
        return archive;
    }

    private static final String version = Version.getVersion();
    private static final byte[] bytes = new byte[] { 118, 61, 48, 13, 10, 111, 61, 117, 115, 101, 114, 49, 32, 53, 51, 54, 53,
            53, 55, 54, 53, 32, 50, 51, 53, 51, 54, 56, 55, 54, 51, 55, 32, 73, 78, 32, 73, 80, 52, 32, 49, 50, 55, 46, 48, 46,
            48, 46, 49, 13, 10, 115, 61, 45, 13, 10, 99, 61, 73, 78, 32, 73, 80, 52, 32, 49, 50, 55, 46, 48, 46, 48, 46, 49,
            13, 10, 116, 61, 48, 32, 48, 13, 10, 109, 61, 97, 117, 100, 105, 111, 32, 54, 48, 48, 48, 32, 82, 84, 80, 47, 65,
            86, 80, 32, 48, 13, 10, 97, 61, 114, 116, 112, 109, 97, 112, 58, 48, 32, 80, 67, 77, 85, 47, 56, 48, 48, 48, 13, 10 };
    private static final String body = new String(bytes);

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
//    private static SipStackTool tool5;

    // Bob is a simple SIP Client. Will not register with Restcomm
    private SipStack bobSipStack;
    private SipPhone bobPhone;
    private String bobContact = "sip:bob@127.0.0.1:5090";

    // Alice is a Restcomm Client with VoiceURL. This Restcomm Client can register with Restcomm and whatever will dial the RCML
    // of the VoiceURL will be executed.
    private SipStack otherRestcommSipStack;
    private SipPhone otherRestcommPhone;
    private String otherRestcommContact = "sip:+15126008888@127.0.0.1:5091";

    // Henrique is a simple SIP Client. Will not register with Restcomm
    private SipStack henriqueSipStack;
    private SipPhone henriquePhone;
    private String henriqueContact = "sip:henrique@127.0.0.1:5092";

    // George is a simple SIP Client. Will not register with Restcomm
    private SipStack georgeSipStack;
    private SipPhone georgePhone;
    private String georgeContact = "sip:+15126008888@127.0.0.1:5070";

    // subaccountclient is a simple SIP Client. Will register with Restcomm
//    private SipStack subAccountClientSipStack;
//    private SipPhone subAccountClientPhone;
//    private String subAccountClientContact = "sip:subaccountclient@127.0.0.1:5093";

    private String adminAccountSid = "ACae6e420f425248d6a26948c17a9e2acf";
    private String adminAuthToken = "77f8c12cc7b8f8423e5c38b035249166";

//    private String subAccountSid = "ACae6e420f425248d6a26948c17a9e2acg";
//    private String subAuthToken = "77f8c12cc7b8f8423e5c38b035249166";

    @BeforeClass
    public static void beforeClass() throws Exception {
        tool1 = new SipStackTool("RestcommActingAsProxyTest1");
        tool2 = new SipStackTool("RestcommActingAsProxyTest2");
        tool3 = new SipStackTool("RestcommActingAsProxyTest3");
        tool4 = new SipStackTool("RestcommActingAsProxyTest4");
//        tool5 = new SipStackTool("DialActionTest5");
    }

    @Before
    public void before() throws Exception {
        bobSipStack = tool1.initializeSipStack(SipStack.PROTOCOL_UDP, "127.0.0.1", "5090", "127.0.0.1:5080");
        bobPhone = bobSipStack.createSipPhone("127.0.0.1", SipStack.PROTOCOL_UDP, 5080, bobContact);

        otherRestcommSipStack = tool2.initializeSipStack(SipStack.PROTOCOL_UDP, "127.0.0.1", "5091", "127.0.0.1:5080");
        otherRestcommPhone = otherRestcommSipStack.createSipPhone("127.0.0.1", SipStack.PROTOCOL_UDP, 5080, otherRestcommContact);

        henriqueSipStack = tool3.initializeSipStack(SipStack.PROTOCOL_UDP, "127.0.0.1", "5092", "127.0.0.1:5080");
        henriquePhone = henriqueSipStack.createSipPhone("127.0.0.1", SipStack.PROTOCOL_UDP, 5080, henriqueContact);
//
        georgeSipStack = tool4.initializeSipStack(SipStack.PROTOCOL_UDP, "127.0.0.1", "5070", "127.0.0.1:5080");
        georgePhone = georgeSipStack.createSipPhone("127.0.0.1", SipStack.PROTOCOL_UDP, 5080, georgeContact);
//
//        subAccountClientSipStack = tool5.initializeSipStack(SipStack.PROTOCOL_UDP, "127.0.0.1", "5093", "127.0.0.1:5080");
//        subAccountClientPhone = subAccountClientSipStack.createSipPhone("127.0.0.1", SipStack.PROTOCOL_UDP, 5080, subAccountClientContact);
    }

    @After
    public void after() throws Exception {
        if (bobPhone != null) {
            bobPhone.dispose();
        }
        if (bobSipStack != null) {
            bobSipStack.dispose();
        }

        if (otherRestcommSipStack != null) {
            otherRestcommSipStack.dispose();
        }
        if (otherRestcommPhone != null) {
            otherRestcommPhone.dispose();
        }

        if (henriqueSipStack != null) {
            henriqueSipStack.dispose();
        }
        if (henriquePhone != null) {
            henriquePhone.dispose();
        }

//        if (georgePhone != null) {
//            georgePhone.dispose();
//        }
//        if (georgeSipStack != null) {
//            georgeSipStack.dispose();
//        }
//
//        if (subAccountClientPhone != null) {
//            subAccountClientPhone.dispose();
//        }
//        if (subAccountClientSipStack != null) {
//            subAccountClientSipStack.dispose();
//        }
        Thread.sleep(1000);
        wireMockRule.resetRequests();
        Thread.sleep(4000);
    }


    @Test
    public void testDialNumberToProxyFromClient() throws ParseException, InterruptedException, MalformedURLException {
        SipCall otherRestcommCall = otherRestcommPhone.createSipCall();
        otherRestcommCall.listenForIncomingCall();

        // Create outgoing call with first phone
        final SipCall bobCall = bobPhone.createSipCall();
        bobCall.initiateOutgoingCall(bobContact, "sip:+15126008888@127.0.0.1:5080", null, body, "application", "sdp", null, null);
        assertLastOperationSuccess(bobCall);

        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        int response = bobCall.getLastReceivedResponse().getStatusCode();
        while (response != Response.OK) {
            assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
            response = bobCall.getLastReceivedResponse().getStatusCode();
        }

        assertTrue(bobCall.sendInviteOkAck());

        assertTrue(otherRestcommCall.waitForIncomingCall(10000));
        assertTrue(otherRestcommCall.sendIncomingCallResponse(Response.TRYING, "George-Trying", 3600));
        assertTrue(otherRestcommCall.sendIncomingCallResponse(Response.RINGING, "George-Ringing", 3600));
        String receivedBody = new String(otherRestcommCall.getLastReceivedRequest().getRawContent());
        assertTrue(otherRestcommCall.sendIncomingCallResponse(Response.OK, "George-OK", 3600, receivedBody, "application", "sdp",
                null, null));
        assertTrue(otherRestcommCall.waitForAck(50000));

        assertEquals(2, MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(),adminAccountSid, adminAuthToken));
        assertEquals(1, MonitoringServiceTool.getInstance().getLiveIncomingCallStatistics(deploymentUrl.toString(),adminAccountSid, adminAuthToken));
        assertEquals(1, MonitoringServiceTool.getInstance().getLiveOutgoingCallStatistics(deploymentUrl.toString(),adminAccountSid, adminAuthToken));
        assertEquals(2, MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(),adminAccountSid, adminAuthToken));

        Thread.sleep(1000);

        otherRestcommCall.listenForDisconnect();
        assertTrue(bobCall.disconnect());
        assertTrue(otherRestcommCall.waitForDisconnect(5000));
        otherRestcommCall.respondToDisconnect();

        Thread.sleep(10000);

//        logger.info("About to check the Requests");
//        List<LoggedRequest> requests = findAll(getRequestedFor(urlPathMatching("/1111")));
//        assertTrue(requests.size() == 1);
//        //        requests.get(0).g;
//        String requestBody = new URL(requests.get(0).getAbsoluteUrl()).getQuery();// .getQuery();// .getBodyAsString();
//        List<String> params = Arrays.asList(requestBody.split("&"));
//        String callSid = "";
//        for (String param : params) {
//            if (param.contains("CallSid")) {
//                callSid = param.split("=")[1];
//            }
//        }
//        JsonObject cdr = RestcommCallsTool.getInstance().getCall(deploymentUrl.toString(), adminAccountSid, adminAuthToken, callSid);
//        JsonObject jsonObj = cdr.getAsJsonObject();
//        String status = jsonObj.get("status").getAsString();
//        logger.info("Status: "+status);
//        assertTrue(status.equalsIgnoreCase("completed"));
        assertTrue(MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(),adminAccountSid, adminAuthToken)==0);
        assertTrue(MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(),adminAccountSid, adminAuthToken)==0);
    }

    @Test
    public void testDialNumberToProxyFromNonClient() throws ParseException, InterruptedException, MalformedURLException {
        SipCall georgeCall = georgePhone.createSipCall();
        georgeCall.listenForIncomingCall();

        // Create outgoing call with first phone
        final SipCall henriqueCall = henriquePhone.createSipCall();
        henriqueCall.initiateOutgoingCall(henriqueContact, "sip:+15126008888@127.0.0.1:5080", null, body, "application", "sdp", null, null);
        assertLastOperationSuccess(henriqueCall);

        assertTrue(henriqueCall.waitOutgoingCallResponse(5 * 1000));
        int response = henriqueCall.getLastReceivedResponse().getStatusCode();
        while (response != Response.OK) {
            assertTrue(henriqueCall.waitOutgoingCallResponse(5 * 1000));
            response = henriqueCall.getLastReceivedResponse().getStatusCode();
        }

        assertTrue(henriqueCall.sendInviteOkAck());

        assertTrue(georgeCall.waitForIncomingCall(10000));
        assertTrue(georgeCall.sendIncomingCallResponse(Response.TRYING, "George-Trying", 3600));
        assertTrue(georgeCall.sendIncomingCallResponse(Response.RINGING, "George-Ringing", 3600));
        String receivedBody = new String(georgeCall.getLastReceivedRequest().getRawContent());
        assertTrue(georgeCall.sendIncomingCallResponse(Response.OK, "George-OK", 3600, receivedBody, "application", "sdp",
                null, null));
        assertTrue(georgeCall.waitForAck(50000));

        assertEquals(2, MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(),adminAccountSid, adminAuthToken));
        assertEquals(1, MonitoringServiceTool.getInstance().getLiveIncomingCallStatistics(deploymentUrl.toString(),adminAccountSid, adminAuthToken));
        assertEquals(1, MonitoringServiceTool.getInstance().getLiveOutgoingCallStatistics(deploymentUrl.toString(),adminAccountSid, adminAuthToken));
        assertEquals(2, MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(),adminAccountSid, adminAuthToken));

        Thread.sleep(1000);

        georgeCall.listenForDisconnect();
        assertTrue(henriqueCall.disconnect());
        assertTrue(georgeCall.waitForDisconnect(5000));
        georgeCall.respondToDisconnect();

        Thread.sleep(10000);

//        logger.info("About to check the Requests");
//        List<LoggedRequest> requests = findAll(getRequestedFor(urlPathMatching("/1111")));
//        assertTrue(requests.size() == 1);
//        //        requests.get(0).g;
//        String requestBody = new URL(requests.get(0).getAbsoluteUrl()).getQuery();// .getQuery();// .getBodyAsString();
//        List<String> params = Arrays.asList(requestBody.split("&"));
//        String callSid = "";
//        for (String param : params) {
//            if (param.contains("CallSid")) {
//                callSid = param.split("=")[1];
//            }
//        }
//        JsonObject cdr = RestcommCallsTool.getInstance().getCall(deploymentUrl.toString(), adminAccountSid, adminAuthToken, callSid);
//        JsonObject jsonObj = cdr.getAsJsonObject();
//        String status = jsonObj.get("status").getAsString();
//        logger.info("Status: "+status);
//        assertTrue(status.equalsIgnoreCase("completed"));
        assertTrue(MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(),adminAccountSid, adminAuthToken)==0);
        assertTrue(MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(),adminAccountSid, adminAuthToken)==0);
    }

}
