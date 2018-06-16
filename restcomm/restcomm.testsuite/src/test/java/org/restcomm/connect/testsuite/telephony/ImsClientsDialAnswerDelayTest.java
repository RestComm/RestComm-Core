package org.restcomm.connect.testsuite.telephony;

import static org.cafesip.sipunit.SipAssert.assertLastOperationSuccess;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.net.URL;
import java.sql.SQLException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.sip.Dialog;
import javax.sip.InvalidArgumentException;
import javax.sip.RequestEvent;
import javax.sip.SipException;
import javax.sip.address.Address;
import javax.sip.address.SipURI;
import javax.sip.address.URI;
import javax.sip.header.ContactHeader;
import javax.sip.header.Header;
import javax.sip.header.ProxyAuthenticateHeader;
import javax.sip.header.RouteHeader;
import javax.sip.header.ToHeader;
import javax.sip.header.UserAgentHeader;
import javax.sip.header.WWWAuthenticateHeader;
import javax.sip.message.Request;
import javax.sip.message.Response;

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
import org.restcomm.connect.testsuite.http.RestcommCallsTool;
import org.restcomm.connect.testsuite.telephony.security.DigestServerAuthenticationMethod;
import org.restcomm.connect.testsuite.tools.MonitoringServiceTool;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.google.gson.JsonObject;
import java.util.Arrays;
import org.junit.experimental.categories.Category;
import org.restcomm.connect.commons.annotations.FeatureAltTests;
import org.restcomm.connect.commons.annotations.ParallelClassTests;
import org.restcomm.connect.testsuite.NetworkPortAssigner;
import org.restcomm.connect.commons.annotations.UnstableTests;
import org.restcomm.connect.testsuite.WebArchiveUtil;

/**
 * Test for clients with or without VoiceURL (Bitbucket issue 115). Clients
 * without VoiceURL can dial anything.
 *
 * @author <a href="mailto:gvagenas@gmail.com">gvagenas</a>
 */
@RunWith(Arquillian.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@Category(value={FeatureAltTests.class, ParallelClassTests.class})
public class ImsClientsDialAnswerDelayTest {

    private static final String version = Version.getVersion();

    private static Logger logger = Logger.getLogger(ImsClientsDialAnswerDelayTest.class);

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

    private String pstnNumber = "+151261006100";

    // Maria is a Restcomm Client **without** VoiceURL. This Restcomm Client can dial anything.
    private SipStack augustSipStack;
    private SipPhone augustPhone;
    private static String augustPort = String.valueOf(NetworkPortAssigner.retrieveNextPortByFile());
    private String augustContact = "sip:august@127.0.0.1:" + augustPort;
    private boolean isAugustRegistered = false;

    private SipStack juliusSipStack;
    private SipPhone juliusPhone;
    private static String juliusPort = String.valueOf(NetworkPortAssigner.retrieveNextPortByFile());
    private String juliusContact = "sip:julius@127.0.0.1:" + juliusPort;
    private boolean isJuliusRegistered = false;

    private SipStack imsSipStack;
    private SipPhone imsAugustPhone;
    private SipPhone imsJuliusPhone;
    private static String imsPort = String.valueOf(NetworkPortAssigner.retrieveNextPortByFile());
    private String imsContact = "sip:127.0.0.1:" + imsPort;

    private SipPhone pstnPhone;
    private String pstnContact = "sip:" + pstnNumber + "@127.0.0.1:" + imsPort;

    private String adminAccountSid = "AC27f2dd02ab51ba5d5a9ff7fc5537a09a";
    private String adminAuthToken = "77f8c12cc7b8f8423e5c38b035249166";

    private static int restcommPort = 5080;
    private static int restcommHTTPPort = 8080;
    private static String restcommContact = "127.0.0.1:" + restcommPort;

    @BeforeClass
    public static void beforeClass() throws Exception {
        tool1 = new SipStackTool("ImsClientsDialTest1");
        tool2 = new SipStackTool("ImsClientsDialTest2");
        tool3 = new SipStackTool("ImsClientsDialTest3");

        Class.forName("org.hsqldb.jdbc.JDBCDriver");
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

        imsSipStack = tool1.initializeSipStack(SipStack.PROTOCOL_UDP, "127.0.0.1", imsPort, restcommContact);

        augustSipStack = tool2.initializeSipStack(SipStack.PROTOCOL_UDP, "127.0.0.1", augustPort, restcommContact);
        augustPhone = augustSipStack.createSipPhone("127.0.0.1", SipStack.PROTOCOL_UDP, restcommPort, augustContact);
        imsAugustPhone = imsSipStack.createSipPhone("127.0.0.1", SipStack.PROTOCOL_UDP, restcommPort, augustContact);
        imsAugustPhone.setLoopback(true);
        imsAugustPhone.setPassThroughRegisterRequests(true);

        juliusSipStack = tool3.initializeSipStack(SipStack.PROTOCOL_UDP, "127.0.0.1", juliusPort, restcommContact);
        juliusPhone = juliusSipStack.createSipPhone("127.0.0.1", SipStack.PROTOCOL_UDP, restcommPort, juliusContact);
        imsJuliusPhone = imsSipStack.createSipPhone("127.0.0.1", SipStack.PROTOCOL_UDP, restcommPort, juliusContact);
        imsJuliusPhone.setLoopback(true);
        imsJuliusPhone.setPassThroughRegisterRequests(true);

        pstnPhone = imsSipStack.createSipPhone("127.0.0.1", SipStack.PROTOCOL_UDP, restcommPort, pstnContact);
        pstnPhone.setPassThroughRegisterRequests(true);

        if (isAugustRegistered) {
            unregisterAugust();
        }

        if (isJuliusRegistered) {
            unregisterJulius();
        }

    }

    @After
    public void after() throws Exception {
        if (augustPhone != null) {
            augustPhone.dispose();
        }
        if (augustSipStack != null) {
            augustSipStack.dispose();
        }

        if (imsSipStack != null) {
            imsSipStack.dispose();
        }
        if (imsAugustPhone != null) {
            imsAugustPhone.dispose();
        }
        if (imsJuliusPhone != null) {
            imsJuliusPhone.dispose();
        }

        Thread.sleep(3000);
        wireMockRule.resetRequests();
        Thread.sleep(3000);
    }

    @Test
    public void testRegisterClients() throws ParseException, InterruptedException, SQLException {
        logger.info("testRegisterClients");
        SipURI uri = augustSipStack.getAddressFactory().createSipURI(null, restcommContact);

        ExecutorService executorService = Executors.newSingleThreadExecutor();
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                imsAugustPhone.listenRequestMessage();
                RequestEvent requestEvent = imsAugustPhone.waitRequest(10000);
                assertNotNull(requestEvent);
                try {
                    Response response = imsSipStack.getMessageFactory().createResponse(401, requestEvent.getRequest());
                    WWWAuthenticateHeader wwwAuthenticateHeader = imsSipStack.getHeaderFactory().createWWWAuthenticateHeader("Digest realm=\"ims.tp.pl\",\n"
                            + "   nonce=\"b7c9036dbf357f7683f054aea940e9703dc8f84c1108\",\n"
                            + "   opaque=\"ALU:QbkRBthOEgEQAkgVEwwHRAIBHgkdHwQCQ1lFRkZWDhMyIXBqLCs0Zj06ZTwhdHpgZmI_\",\n"
                            + "   algorithm=MD5,\n"
                            + "   qop=\"auth\"");
                    response.setHeader(wwwAuthenticateHeader);
                    ContactHeader contactHeader = augustSipStack.getHeaderFactory().createContactHeader();
                    contactHeader.setAddress(augustSipStack.getAddressFactory().createAddress(imsContact));
                    response.addHeader(contactHeader);
                    imsAugustPhone.sendReply(requestEvent, response);
                    requestEvent = imsAugustPhone.waitRequest(10000);
                    response = imsSipStack.getMessageFactory().createResponse(200, requestEvent.getRequest());
                    contactHeader = augustSipStack.getHeaderFactory().createContactHeader();
                    contactHeader.setExpires(600);
                    contactHeader.setAddress(augustSipStack.getAddressFactory().createAddress(imsContact));
                    response.addHeader(contactHeader);
                    imsAugustPhone.sendReply(requestEvent, response);
                } catch (ParseException e) {
                    e.printStackTrace();
                    fail(e.getMessage());
                } catch (InvalidArgumentException e) {
                    e.printStackTrace();
                    fail(e.getMessage());
                }

            }
        });

        assertTrue(augustPhone.register(uri, "august", "1234", augustContact, 3600, 3600));
        isAugustRegistered = true;
        augustPhone.createSipCall().listenForIncomingCall();

        Thread.sleep(1000);

        executorService.execute(new Runnable() {
            @Override
            public void run() {
                imsAugustPhone.listenRequestMessage();
                RequestEvent requestEvent = imsAugustPhone.waitRequest(10000);
                assertNotNull(requestEvent);
                try {
                    Response response = imsSipStack.getMessageFactory().createResponse(200, requestEvent.getRequest());
                    ContactHeader contactHeader = augustSipStack.getHeaderFactory().createContactHeader();
                    contactHeader.setExpires(0);
                    contactHeader.setAddress(augustSipStack.getAddressFactory().createAddress(imsContact));
                    response.addHeader(contactHeader);
                    imsAugustPhone.sendReply(requestEvent, response);
                } catch (ParseException e) {
                    e.printStackTrace();
                    fail(e.getMessage());
                } catch (InvalidArgumentException e) {
                    e.printStackTrace();
                    fail(e.getMessage());
                }

            }
        });
        assertTrue(augustPhone.unregister(augustContact, 0));
        isAugustRegistered = false;
    }

    @Test
    public void testWebRTCClientOutgoingAdisconnect() throws ParseException, InterruptedException, SQLException {

        logger.info("testWebRTCClientOutgoingAdisconnect");
        registerAugust();

        SipCall pstnCall = pstnPhone.createSipCall();
        final SipCall augustCall = augustPhone.createSipCall();
        initiateAugust(pstnCall, pstnContact, augustCall);

        assertTrue(pstnCall.waitForIncomingCall(5 * 1000));
        assertTrue(pstnCall.sendIncomingCallResponse(Response.RINGING, "RINGING-Pstn", 3600));

        Thread.sleep(500);

        SipRequest lastReceivedRequest = pstnCall.getLastReceivedRequest();
        String receivedBody = new String(lastReceivedRequest.getRawContent());
        assertTrue(pstnCall.sendIncomingCallResponse(Response.OK, "OK-Pstn", 3600, receivedBody, "application", "sdp", null,
                null));

        int responseAugust = augustCall.getLastReceivedResponse().getStatusCode();
        while (responseAugust != Response.OK) {
            assertTrue(augustCall.waitOutgoingCallResponse(5 * 1000));
            responseAugust = augustCall.getLastReceivedResponse().getStatusCode();
        }
        assertTrue(augustCall.sendInviteOkAck());

        Thread.sleep(1000);

        int liveCalls = MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        int liveCallsArraySize = MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        assertTrue(liveCalls == 2);
        assertTrue(liveCallsArraySize == 2);

        Map<String, String> filters = new HashMap<String, String>();
        JsonObject filteredCallsByStatusObject = RestcommCallsTool.getInstance().getCallsUsingFilter(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, filters);
        assertEquals(2, filteredCallsByStatusObject.get("calls").getAsJsonArray().size());

        pstnCall.listenForDisconnect();
        assertTrue(augustCall.disconnect());

        assertTrue(pstnCall.waitForDisconnect(5 * 1000));
        assertTrue(pstnCall.respondToDisconnect());

        Thread.sleep(1000);

        filteredCallsByStatusObject = RestcommCallsTool.getInstance().getCallsUsingFilter(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, filters);
        assertEquals(0, filteredCallsByStatusObject.get("calls").getAsJsonArray().size());

        unregisterAugust();
    }

    @Test
    public void testWebRTCClientOutgoingAHold() throws SipException, ParseException, InterruptedException, InvalidArgumentException {

        logger.info("testWebRTCClientOutgoingAHold");
        registerAugust();

        SipCall pstnCall = pstnPhone.createSipCall();
        final SipCall augustCall = augustPhone.createSipCall();
        initiateAugust(pstnCall, pstnContact, augustCall);

        assertTrue(pstnCall.waitForIncomingCall(5 * 1000));
        assertTrue(pstnCall.sendIncomingCallResponse(Response.RINGING, "RINGING-Pstn", 3600));

        Thread.sleep(500);

        SipRequest lastReceivedRequest = pstnCall.getLastReceivedRequest();
        String receivedBody = new String(lastReceivedRequest.getRawContent());
        assertTrue(pstnCall.sendIncomingCallResponse(Response.OK, "OK-Pstn", 3600, receivedBody, "application", "sdp", null,
                null));

        int responseAugust = augustCall.getLastReceivedResponse().getStatusCode();
        while (responseAugust != Response.OK) {
            assertTrue(augustCall.waitOutgoingCallResponse(5 * 1000));
            responseAugust = augustCall.getLastReceivedResponse().getStatusCode();
        }
        assertTrue(augustCall.sendInviteOkAck());

        Thread.sleep(1000);

        int liveCalls = MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        int liveCallsArraySize = MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        assertTrue(liveCalls == 2);
        assertTrue(liveCallsArraySize == 2);

        Map<String, String> filters = new HashMap<String, String>();
        JsonObject filteredCallsByStatusObject = RestcommCallsTool.getInstance().getCallsUsingFilter(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, filters);
        assertEquals(2, filteredCallsByStatusObject.get("calls").getAsJsonArray().size());

        //HOLD - start
        SipTransaction augustReinviteTx = augustCall.sendReinvite(augustContact, augustContact, body + "a=sendonly", "application", "sdp");
        assertTrue(augustCall.waitReinviteResponse(augustReinviteTx, 5 * 1000));
        augustCall.sendReinviteOkAck(augustReinviteTx);

        assertTrue(pstnCall.waitForMessage(5 * 1000));
        lastReceivedRequest = pstnCall.getLastReceivedRequest();
        receivedBody = new String(lastReceivedRequest.getRawContent());
        assertTrue(receivedBody.equals("action=onHold"));
        SipTransaction pstnMessageTx = pstnCall.getLastTransaction();
        Request pstnMessage = pstnMessageTx.getServerTransaction().getRequest();
        Response pstnMessageAccepted = imsSipStack.getMessageFactory().createResponse(Response.ACCEPTED, pstnMessage);
        pstnMessageTx.getServerTransaction().sendResponse(pstnMessageAccepted);

        augustReinviteTx = augustCall.sendReinvite(augustContact, augustContact, body + "a=sendrecv", "application", "sdp");
        assertTrue(augustCall.waitReinviteResponse(augustReinviteTx, 5 * 1000));
        augustCall.sendReinviteOkAck(augustReinviteTx);

        assertTrue(pstnCall.waitForMessage(5 * 1000));
        lastReceivedRequest = pstnCall.getLastReceivedRequest();
        receivedBody = new String(lastReceivedRequest.getRawContent());
        assertTrue(receivedBody.equals("action=offHold"));
        pstnMessageTx = pstnCall.getLastTransaction();
        pstnMessage = pstnMessageTx.getServerTransaction().getRequest();
        pstnMessageAccepted = imsSipStack.getMessageFactory().createResponse(Response.ACCEPTED, pstnMessage);
        pstnMessageTx.getServerTransaction().sendResponse(pstnMessageAccepted);
        //HOLD - end

        Thread.sleep(1000);

        pstnCall.listenForDisconnect();
        assertTrue(augustCall.disconnect());

        assertTrue(pstnCall.waitForDisconnect(5 * 1000));
        assertTrue(pstnCall.respondToDisconnect());
        Thread.sleep(1000);

        filteredCallsByStatusObject = RestcommCallsTool.getInstance().getCallsUsingFilter(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, filters);
        assertEquals(0, filteredCallsByStatusObject.get("calls").getAsJsonArray().size());

        unregisterAugust();
    }

    @Test
    @Category({UnstableTests.class})
    public void testWebRTCClientOutgoingBHold() throws SipException, ParseException, InterruptedException, InvalidArgumentException {

        logger.info("testWebRTCClientOutgoingBHold");
        registerAugust();

        SipCall pstnCall = pstnPhone.createSipCall();
        final SipCall augustCall = augustPhone.createSipCall();
        initiateAugust(pstnCall, pstnContact, augustCall);

        assertTrue(pstnCall.waitForIncomingCall(5 * 1000));
        assertTrue(pstnCall.sendIncomingCallResponse(Response.RINGING, "RINGING-Pstn", 3600));

        Thread.sleep(500);

        SipRequest lastReceivedRequest = pstnCall.getLastReceivedRequest();
        String receivedBody = new String(lastReceivedRequest.getRawContent());
        assertTrue(pstnCall.sendIncomingCallResponse(Response.OK, "OK-Pstn", 3600, receivedBody, "application", "sdp", null,
                null));

        int responseAugust = Response.RINGING;
        while (responseAugust != Response.OK) {
            assertTrue(augustCall.waitOutgoingCallResponse(5 * 1000));
            responseAugust = augustCall.getLastReceivedResponse().getStatusCode();
        }
        assertTrue(augustCall.sendInviteOkAck());

        Thread.sleep(1000);

        int liveCalls = MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        int liveCallsArraySize = MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        assertTrue(liveCalls == 2);
        assertTrue(liveCallsArraySize == 2);

        Map<String, String> filters = new HashMap<String, String>();
        JsonObject filteredCallsByStatusObject = RestcommCallsTool.getInstance().getCallsUsingFilter(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, filters);
        assertEquals(2, filteredCallsByStatusObject.get("calls").getAsJsonArray().size());

        Thread.sleep(1000);

        //HOLD - start
        augustCall.listenForMessage();

        SipTransaction pstnReinviteTx = pstnCall.sendReinvite(pstnContact, pstnContact, body + "a=sendonly", "application", "sdp");
        assertTrue(pstnCall.waitReinviteResponse(pstnReinviteTx, 5 * 1000));
        pstnCall.sendReinviteOkAck(pstnReinviteTx);

        assertTrue(augustCall.waitForMessage(5 * 1000));
        lastReceivedRequest = augustCall.getLastReceivedRequest();
        receivedBody = new String(lastReceivedRequest.getRawContent());
        assertTrue(receivedBody.equals("action=onHold"));
        SipTransaction augustMessageTx = augustCall.getLastTransaction();
        Request augustMessage = augustMessageTx.getServerTransaction().getRequest();
        Response augustMessageAccepted = augustSipStack.getMessageFactory().createResponse(Response.ACCEPTED, augustMessage);
        augustMessageTx.getServerTransaction().sendResponse(augustMessageAccepted);

        pstnReinviteTx = pstnCall.sendReinvite(pstnContact, pstnContact, body + "a=sendrecv", "application", "sdp");
        assertTrue(pstnCall.waitReinviteResponse(pstnReinviteTx, 5 * 1000));
        pstnCall.sendReinviteOkAck(pstnReinviteTx);

        assertTrue(augustCall.waitForMessage(5 * 1000));
        lastReceivedRequest = augustCall.getLastReceivedRequest();
        receivedBody = new String(lastReceivedRequest.getRawContent());
        assertTrue(receivedBody.equals("action=offHold"));
        augustMessageTx = augustCall.getLastTransaction();
        augustMessage = augustMessageTx.getServerTransaction().getRequest();
        augustMessageAccepted = augustSipStack.getMessageFactory().createResponse(Response.ACCEPTED, augustMessage);
        augustMessageTx.getServerTransaction().sendResponse(augustMessageAccepted);
        //HOLD - end

        Thread.sleep(1000);
        augustCall.listenForDisconnect();
        assertTrue(pstnCall.disconnect());

        assertTrue(augustCall.waitForDisconnect(5 * 1000));
        assertTrue(augustCall.respondToDisconnect());
        Thread.sleep(1000);

        filteredCallsByStatusObject = RestcommCallsTool.getInstance().getCallsUsingFilter(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, filters);
        assertEquals(0, filteredCallsByStatusObject.get("calls").getAsJsonArray().size());

        unregisterAugust();
    }

    @Test
    @Category(UnstableTests.class)
    public void testWebRTCClientIncomingADisconnect() throws InterruptedException, ParseException {
        logger.info("testWebRTCClientIncomingADisconnect");

        registerAugust();

        SipCall augustCall = augustPhone.createSipCall();
        SipCall pstnCall = pstnPhone.createSipCall();
        initiatePstn(pstnCall, augustCall);

        assertTrue(augustCall.waitForIncomingCall(30 * 1000));
        assertTrue(augustCall.sendIncomingCallResponse(Response.RINGING, "Ringing-August", 3600));
        String receivedBody = new String(augustCall.getLastReceivedRequest().getRawContent());
        assertTrue(augustCall.sendIncomingCallResponse(Response.OK, "OK-August", 3600, receivedBody, "application", "sdp", null,
                null));
        assertTrue(augustCall.waitForAck(50 * 1000));

        int responsePstn = Response.RINGING;
        while (responsePstn != Response.OK) {
            assertTrue(pstnCall.waitOutgoingCallResponse(5 * 1000));
            responsePstn = pstnCall.getLastReceivedResponse().getStatusCode();
        }
        assertTrue(pstnCall.sendInviteOkAck());

        Thread.sleep(1000);

        int liveCalls = MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        int liveCallsArraySize = MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        assertTrue(liveCalls == 2);
        assertTrue(liveCallsArraySize == 2);

        Map<String, String> filters = new HashMap<String, String>();
        JsonObject filteredCallsByStatusObject = RestcommCallsTool.getInstance().getCallsUsingFilter(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, filters);
        assertEquals(2, filteredCallsByStatusObject.get("calls").getAsJsonArray().size());

        Thread.sleep(1000);

        // hangup.
        augustCall.listenForDisconnect();
        pstnCall.disconnect();
        assertTrue(augustCall.waitForDisconnect(30 * 1000));
        assertTrue(augustCall.respondToDisconnect());
        Thread.sleep(1000);

        filteredCallsByStatusObject = RestcommCallsTool.getInstance().getCallsUsingFilter(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, filters);
        assertEquals(0, filteredCallsByStatusObject.get("calls").getAsJsonArray().size());

        unregisterAugust();
    }

    @Test
    @Category(UnstableTests.class)
    public void testWebRTCClientIncomingBusy() throws InterruptedException, ParseException {
        logger.info("testWebRTCClientIncomingBusy");

        registerAugust();

        SipCall augustCall = augustPhone.createSipCall();
        SipCall pstnCall = pstnPhone.createSipCall();
        initiatePstn(pstnCall, augustCall);

        assertTrue(augustCall.waitForIncomingCall(30 * 1000));
        assertTrue(augustCall.sendIncomingCallResponse(Response.BUSY_HERE, "Busy-August", 3600));
        assertTrue(augustCall.waitForAck(50 * 1000));

        int responsePstn = Response.RINGING;
        while (responsePstn != Response.BUSY_HERE) {
            assertTrue(pstnCall.waitOutgoingCallResponse(5 * 1000));
            responsePstn = pstnCall.getLastReceivedResponse().getStatusCode();
        }
        Thread.sleep(1000);

        Map<String, String> filters = new HashMap<String, String>();
        JsonObject filteredCallsByStatusObject = RestcommCallsTool.getInstance().getCallsUsingFilter(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, filters);
        assertEquals(0, filteredCallsByStatusObject.get("calls").getAsJsonArray().size());

        unregisterAugust();
    }

    @Test
    @Category(UnstableTests.class)
    public void testWebRTCClientIncomingFail() throws InterruptedException, ParseException {
        logger.info("testWebRTCClientIncomingFail");

        registerAugust();

        SipCall augustCall = augustPhone.createSipCall();
        SipCall pstnCall = pstnPhone.createSipCall();
        initiatePstn(pstnCall, augustCall);

        assertTrue(augustCall.waitForIncomingCall(30 * 1000));
        assertTrue(augustCall.sendIncomingCallResponse(Response.SERVICE_UNAVAILABLE, "ServiceUnavailable-August", 3600));
        assertTrue(augustCall.waitForAck(50 * 1000));

        int responsePstn = Response.RINGING;
        while (responsePstn != Response.SERVICE_UNAVAILABLE) {
            assertTrue(pstnCall.waitOutgoingCallResponse(5 * 1000));
            responsePstn = pstnCall.getLastReceivedResponse().getStatusCode();
        }
        Thread.sleep(1000);

        Map<String, String> filters = new HashMap<String, String>();
        JsonObject filteredCallsByStatusObject = RestcommCallsTool.getInstance().getCallsUsingFilter(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, filters);
        assertEquals(0, filteredCallsByStatusObject.get("calls").getAsJsonArray().size());

        unregisterAugust();
    }

    @Test
    @Category(UnstableTests.class)
    public void testWebRTCClientIncomingAHold() throws SipException, InterruptedException, ParseException, InvalidArgumentException {
        logger.info("testWebRTCClientIncomingAHold");

        registerAugust();

        SipCall augustCall = augustPhone.createSipCall();
        SipCall pstnCall = pstnPhone.createSipCall();
        initiatePstn(pstnCall, augustCall);

        assertTrue(augustCall.waitForIncomingCall(30 * 1000));
        assertTrue(augustCall.sendIncomingCallResponse(Response.RINGING, "Ringing-August", 3600));
        String receivedBody = new String(augustCall.getLastReceivedRequest().getRawContent());
        assertTrue(augustCall.sendIncomingCallResponse(Response.OK, "OK-August", 3600, receivedBody, "application", "sdp", null,
                null));
        assertTrue(augustCall.waitForAck(50 * 1000));

        int responsePstn = Response.RINGING;
        while (responsePstn != Response.OK) {
            assertTrue(pstnCall.waitOutgoingCallResponse(5 * 1000));
            responsePstn = pstnCall.getLastReceivedResponse().getStatusCode();
        }
        assertTrue(pstnCall.sendInviteOkAck());

        Thread.sleep(1000);

        int liveCalls = MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        int liveCallsArraySize = MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        assertTrue(liveCalls == 2);
        assertTrue(liveCallsArraySize == 2);

        Map<String, String> filters = new HashMap<String, String>();
        JsonObject filteredCallsByStatusObject = RestcommCallsTool.getInstance().getCallsUsingFilter(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, filters);
        assertEquals(2, filteredCallsByStatusObject.get("calls").getAsJsonArray().size());

        Thread.sleep(1000);

        //HOLD - start
        SipTransaction pstnReinviteTx = pstnCall.sendReinvite(pstnContact, pstnContact, body + "a=sendonly", "application", "sdp");
        assertTrue(pstnCall.waitReinviteResponse(pstnReinviteTx, 5 * 1000));
        pstnCall.sendReinviteOkAck(pstnReinviteTx);

        assertTrue(augustCall.waitForMessage(5 * 1000));
        SipRequest lastReceivedRequest = augustCall.getLastReceivedRequest();
        receivedBody = new String(lastReceivedRequest.getRawContent());
        assertTrue(receivedBody.equals("action=onHold"));
        SipTransaction augustMessageTx = augustCall.getLastTransaction();
        Request augustMessage = augustMessageTx.getServerTransaction().getRequest();
        Response augustMessageAccepted = augustSipStack.getMessageFactory().createResponse(Response.ACCEPTED, augustMessage);
        augustMessageTx.getServerTransaction().sendResponse(augustMessageAccepted);

        pstnReinviteTx = pstnCall.sendReinvite(pstnContact, pstnContact, body + "a=sendrecv", "application", "sdp");
        assertTrue(pstnCall.waitReinviteResponse(pstnReinviteTx, 5 * 1000));
        pstnCall.sendReinviteOkAck(pstnReinviteTx);

        assertTrue(augustCall.waitForMessage(5 * 1000));
        lastReceivedRequest = augustCall.getLastReceivedRequest();
        receivedBody = new String(lastReceivedRequest.getRawContent());
        assertTrue(receivedBody.equals("action=offHold"));
        augustMessageTx = augustCall.getLastTransaction();
        augustMessage = augustMessageTx.getServerTransaction().getRequest();
        augustMessageAccepted = augustSipStack.getMessageFactory().createResponse(Response.ACCEPTED, augustMessage);
        augustMessageTx.getServerTransaction().sendResponse(augustMessageAccepted);
        //HOLD - end

        // hangup.
        augustCall.disconnect();

        pstnCall.listenForDisconnect();
        assertTrue(pstnCall.waitForDisconnect(30 * 1000));
        assertTrue(pstnCall.respondToDisconnect());
        Thread.sleep(1000);

        filteredCallsByStatusObject = RestcommCallsTool.getInstance().getCallsUsingFilter(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, filters);
        assertEquals(0, filteredCallsByStatusObject.get("calls").getAsJsonArray().size());

        unregisterAugust();
    }

    @Test
    @Category(UnstableTests.class)
    public void testWebRTCClientIncomingBHold() throws SipException, InvalidArgumentException, InterruptedException, ParseException {
        logger.info("testWebRTCClientIncomingBHold");

        registerAugust();

        SipCall augustCall = augustPhone.createSipCall();
        SipCall pstnCall = pstnPhone.createSipCall();
        initiatePstn(pstnCall, augustCall);

        assertTrue(augustCall.waitForIncomingCall(30 * 1000));
        assertTrue(augustCall.sendIncomingCallResponse(Response.RINGING, "Ringing-August", 3600));
        String receivedBody = new String(augustCall.getLastReceivedRequest().getRawContent());
        assertTrue(augustCall.sendIncomingCallResponse(Response.OK, "OK-August", 3600, receivedBody, "application", "sdp", null,
                null));
        assertTrue(augustCall.waitForAck(50 * 1000));

        int responsePstn = Response.RINGING;
        while (responsePstn != Response.OK) {
            assertTrue(pstnCall.waitOutgoingCallResponse(5 * 1000));
            responsePstn = pstnCall.getLastReceivedResponse().getStatusCode();
        }
        assertTrue(pstnCall.sendInviteOkAck());

        Thread.sleep(1000);

        int liveCalls = MonitoringServiceTool.getInstance().getStatistics(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        int liveCallsArraySize = MonitoringServiceTool.getInstance().getLiveCallsArraySize(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        assertTrue(liveCalls == 2);
        assertTrue(liveCallsArraySize == 2);

        Map<String, String> filters = new HashMap<String, String>();
        JsonObject filteredCallsByStatusObject = RestcommCallsTool.getInstance().getCallsUsingFilter(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, filters);
        assertEquals(2, filteredCallsByStatusObject.get("calls").getAsJsonArray().size());

        Thread.sleep(1000);

        //HOLD - start
        pstnCall.listenForMessage();

        SipTransaction augustReinviteTx = augustCall.sendReinvite(augustContact, augustContact, body + "a=sendonly", "application", "sdp");
        assertTrue(augustCall.waitReinviteResponse(augustReinviteTx, 5 * 1000));
        augustCall.sendReinviteOkAck(augustReinviteTx);

        assertTrue(pstnCall.waitForMessage(5 * 1000));
        SipRequest lastReceivedRequest = pstnCall.getLastReceivedRequest();
        receivedBody = new String(lastReceivedRequest.getRawContent());
        assertTrue(receivedBody.equals("action=onHold"));
        SipTransaction pstnMessageTx = pstnCall.getLastTransaction();
        Request pstnMessage = pstnMessageTx.getServerTransaction().getRequest();
        Response pstnMessageAccepted = imsSipStack.getMessageFactory().createResponse(Response.ACCEPTED, pstnMessage);
        pstnMessageTx.getServerTransaction().sendResponse(pstnMessageAccepted);

        augustReinviteTx = augustCall.sendReinvite(augustContact, augustContact, body + "a=sendrecv", "application", "sdp");
        assertTrue(augustCall.waitReinviteResponse(augustReinviteTx, 5 * 1000));
        augustCall.sendReinviteOkAck(augustReinviteTx);

        assertTrue(pstnCall.waitForMessage(5 * 1000));
        lastReceivedRequest = pstnCall.getLastReceivedRequest();
        receivedBody = new String(lastReceivedRequest.getRawContent());
        assertTrue(receivedBody.equals("action=offHold"));
        pstnMessageTx = pstnCall.getLastTransaction();
        pstnMessage = pstnMessageTx.getServerTransaction().getRequest();
        pstnMessageAccepted = imsSipStack.getMessageFactory().createResponse(Response.ACCEPTED, pstnMessage);
        pstnMessageTx.getServerTransaction().sendResponse(pstnMessageAccepted);
        //HOLD - end

        // hangup.
        pstnCall.disconnect();

        augustCall.listenForDisconnect();
        assertTrue(augustCall.waitForDisconnect(30 * 1000));
        assertTrue(augustCall.respondToDisconnect());
        Thread.sleep(1000);

        filteredCallsByStatusObject = RestcommCallsTool.getInstance().getCallsUsingFilter(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, filters);
        assertEquals(0, filteredCallsByStatusObject.get("calls").getAsJsonArray().size());

        unregisterAugust();
    }

    @Test
    @Category(UnstableTests.class)
    public void testWebRTCClientIncomingRequestTimeout() throws InterruptedException, ParseException {
        logger.info("testWebRTCClientIncomingRequestTimeout");

        registerAugust();

        SipCall augustCall = augustPhone.createSipCall();
        SipCall pstnCall = pstnPhone.createSipCall();
        initiatePstn(pstnCall, augustCall);

        assertTrue(augustCall.waitForIncomingCall(5 * 1000));
        assertTrue(augustCall.sendIncomingCallResponse(Response.RINGING, "RINGING-august", 3600));
        assertTrue(augustCall.listenForCancel());

        Thread.sleep(30000);

        SipTransaction augustCancelTransaction = augustCall.waitForCancel(5 * 1000);
        assertTrue(augustCancelTransaction != null);
        augustCall.respondToCancel(augustCancelTransaction, 200, "OK-august", 3600);

        assertTrue(pstnCall.waitOutgoingCallResponse(5 * 1000));
        assertEquals(Response.REQUEST_TIMEOUT, pstnCall.getLastReceivedResponse().getStatusCode());

        Map<String, String> filters = new HashMap<String, String>();
        JsonObject filteredCallsByStatusObject = RestcommCallsTool.getInstance().getCallsUsingFilter(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, filters);
        assertEquals(0, filteredCallsByStatusObject.get("calls").getAsJsonArray().size());

        unregisterAugust();
    }

    @Test //Non regression test for issue https://github.com/RestComm/Restcomm-Connect/issues/1042 - Support WebRTC clients to dial out through MediaServer
    @Category({UnstableTests.class})
    public void testWebRTCClientOutgoingBusy() throws ParseException, InterruptedException {

        logger.info("testWebRTCClientOutgoingBusy");
        registerAugust();

        SipCall pstnCall = pstnPhone.createSipCall();
        final SipCall augustCall = augustPhone.createSipCall();
        initiateAugust(pstnCall, pstnContact, augustCall);

        assertTrue(pstnCall.waitForIncomingCall(5 * 1000));
        assertTrue(pstnCall.sendIncomingCallResponse(Response.BUSY_HERE, "Busy-Pstn", 3600));

        int responseAugust = Response.RINGING;
        while (responseAugust != Response.BUSY_HERE) {
            assertTrue(augustCall.waitOutgoingCallResponse(5 * 1000));
            responseAugust = augustCall.getLastReceivedResponse().getStatusCode();
        }

        Thread.sleep(1000);

        Map<String, String> filters = new HashMap<String, String>();
        JsonObject filteredCallsByStatusObject = RestcommCallsTool.getInstance().getCallsUsingFilter(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, filters);
        assertEquals(0, filteredCallsByStatusObject.get("calls").getAsJsonArray().size());

        unregisterAugust();
    }

    @Test //Non regression test for issue https://github.com/RestComm/Restcomm-Connect/issues/1042 - Support WebRTC clients to dial out through MediaServer
    @Category({UnstableTests.class})
    public void testWebRTCClientOutgoingFail() throws ParseException, InterruptedException {

        logger.info("testWebRTCClientOutgoingFail");
        registerAugust();

        SipCall pstnCall = pstnPhone.createSipCall();
        final SipCall augustCall = augustPhone.createSipCall();
        initiateAugust(pstnCall, pstnContact, augustCall);

        assertTrue(pstnCall.waitForIncomingCall(5 * 1000));
        assertTrue(pstnCall.sendIncomingCallResponse(Response.SERVICE_UNAVAILABLE, "ServiceUnavailable-Pstn", 3600));

        int responseAugust = Response.RINGING;
        while (responseAugust != Response.SERVICE_UNAVAILABLE) {
            assertTrue(augustCall.waitOutgoingCallResponse(5 * 1000));
            responseAugust = augustCall.getLastReceivedResponse().getStatusCode();
        }
        Thread.sleep(1000);

        Map<String, String> filters = new HashMap<String, String>();
        JsonObject filteredCallsByStatusObject = RestcommCallsTool.getInstance().getCallsUsingFilter(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, filters);
        assertEquals(0, filteredCallsByStatusObject.get("calls").getAsJsonArray().size());

        unregisterAugust();
    }

    @Test //Non regression test for issue https://github.com/RestComm/Restcomm-Connect/issues/1042 - Support WebRTC clients to dial out through MediaServer
    @Category({UnstableTests.class})
    public void testUnregisteredWebRTCClientOutgoing() throws ParseException, InterruptedException {
        logger.info("testUnregisteredWebRTCClientOutgoing");

        //Change UserAgent header to "sipunit" so CallManager
        ArrayList<String> replaceHeaders = new ArrayList<String>();
        List<String> userAgentList = new ArrayList<String>();
        userAgentList.add("wss-sipunit");
        UserAgentHeader userAgentHeader = augustSipStack.getHeaderFactory().createUserAgentHeader(userAgentList);
        replaceHeaders.add(userAgentHeader.toString());

        // August initiates a call to pstn
        final SipCall augustCall = augustPhone.createSipCall();
        URI uri1 = augustSipStack.getAddressFactory().createURI("sip:" + restcommContact);
        SipURI sipURI = (SipURI) uri1;
        sipURI.setLrParam();
        Address address = augustSipStack.getAddressFactory().createAddress(uri1);

        RouteHeader routeHeader = augustSipStack.getHeaderFactory().createRouteHeader(address);
        replaceHeaders.add(routeHeader.toString());
        Header user = augustSipStack.getHeaderFactory().createHeader("X-RestComm-Ims-User", "myUser");
        Header pass = augustSipStack.getHeaderFactory().createHeader("X-RestComm-Ims-Password", "myPass");
        replaceHeaders.add(user.toString());
        replaceHeaders.add(pass.toString());
        augustCall.initiateOutgoingCall(augustContact, "sip:" + pstnNumber + "@127.0.0.1:5060", null, body, "application", "sdp", null, replaceHeaders);
        assertLastOperationSuccess(augustCall);

        assertTrue(augustCall.waitOutgoingCallResponse(5 * 1000));
        int responseAugust = augustCall.getLastReceivedResponse().getStatusCode();
        assertTrue(responseAugust == Response.NOT_FOUND);

        Map<String, String> filters = new HashMap<String, String>();
        JsonObject filteredCallsByStatusObject = RestcommCallsTool.getInstance().getCallsUsingFilter(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, filters);
        assertEquals(0, filteredCallsByStatusObject.get("calls").getAsJsonArray().size());

    }

    @Test
    @Category({UnstableTests.class})
    public void testUnregisteredWebRTCClientIncoming() throws InterruptedException, ParseException {
        logger.info("testUnregisteredWebRTCClientIncoming");

        // Prepare august phone to receive call
        SipCall augustCall = augustPhone.createSipCall();
        augustCall.listenForIncomingCall();

        // Create outgoing call with pstn phone
        final SipCall pstnCall = pstnPhone.createSipCall();
        pstnCall.initiateOutgoingCall(pstnContact, augustContact, null, body, "application", "sdp", null, null);
        assertLastOperationSuccess(pstnCall);
        assertTrue(pstnCall.waitOutgoingCallResponse(5 * 1000));
        final int response = pstnCall.getLastReceivedResponse().getStatusCode();
        assertTrue(response == Response.NOT_FOUND);

        Map<String, String> filters = new HashMap<String, String>();
        JsonObject filteredCallsByStatusObject = RestcommCallsTool.getInstance().getCallsUsingFilter(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, filters);
        assertEquals(0, filteredCallsByStatusObject.get("calls").getAsJsonArray().size());
    }

    @Test //Non regression test for issue https://github.com/RestComm/Restcomm-Connect/issues/1042 - Support WebRTC clients to dial out through MediaServer
    @Category({UnstableTests.class})
    public void testWebRTCClientOutgoingRequestTimeout() throws ParseException, InterruptedException {

        logger.info("testWebRTCClientOutgoingRequestTimeout");
        registerAugust();

        SipCall pstnCall = pstnPhone.createSipCall();
        final SipCall augustCall = augustPhone.createSipCall();
        initiateAugust(pstnCall, pstnContact, augustCall);

        assertTrue(pstnCall.waitForIncomingCall(5 * 1000));
        assertTrue(pstnCall.sendIncomingCallResponse(Response.RINGING, "RINGING-pstn", 3600));
        assertTrue(pstnCall.listenForCancel());

        Thread.sleep(30000);

        SipTransaction pstnCancelTransaction = pstnCall.waitForCancel(5 * 1000);
        assertTrue(pstnCancelTransaction != null);
        pstnCall.respondToCancel(pstnCancelTransaction, 200, "OK-pstn", 3600);

        assertTrue(augustCall.waitOutgoingCallResponse(5 * 1000));
        assertEquals(Response.REQUEST_TIMEOUT, augustCall.getLastReceivedResponse().getStatusCode());

        Map<String, String> filters = new HashMap<String, String>();
        JsonObject filteredCallsByStatusObject = RestcommCallsTool.getInstance().getCallsUsingFilter(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, filters);
        assertEquals(0, filteredCallsByStatusObject.get("calls").getAsJsonArray().size());

        unregisterAugust();
    }

    private void unregisterAugust() throws InterruptedException {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                imsAugustPhone.listenRequestMessage();
                RequestEvent requestEvent = imsAugustPhone.waitRequest(10000);
                assertNotNull(requestEvent);
                try {
                    Response response = imsSipStack.getMessageFactory().createResponse(200, requestEvent.getRequest());
                    ContactHeader contactHeader = augustSipStack.getHeaderFactory().createContactHeader();
                    contactHeader.setExpires(0);
                    contactHeader.setAddress(augustSipStack.getAddressFactory().createAddress(imsContact));
                    response.addHeader(contactHeader);
                    imsAugustPhone.sendReply(requestEvent, response);
                } catch (ParseException e) {
                    e.printStackTrace();
                    fail(e.getMessage());
                } catch (InvalidArgumentException e) {
                    e.printStackTrace();
                    fail(e.getMessage());
                }

            }
        });

        assertTrue(augustPhone.unregister(augustContact, 3600));
        isAugustRegistered = false;
        Thread.sleep(1000);
    }

    private void unregisterJulius() throws InterruptedException {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                imsJuliusPhone.listenRequestMessage();
                RequestEvent requestEvent = imsJuliusPhone.waitRequest(10000);
                assertNotNull(requestEvent);
                try {
                    Response response = imsSipStack.getMessageFactory().createResponse(200, requestEvent.getRequest());
                    ContactHeader contactHeader = juliusSipStack.getHeaderFactory().createContactHeader();
                    contactHeader.setExpires(0);
                    contactHeader.setAddress(juliusSipStack.getAddressFactory().createAddress(imsContact));
                    response.addHeader(contactHeader);
                    imsJuliusPhone.sendReply(requestEvent, response);
                } catch (ParseException e) {
                    e.printStackTrace();
                    fail(e.getMessage());
                } catch (InvalidArgumentException e) {
                    e.printStackTrace();
                    fail(e.getMessage());
                }

            }
        });

        assertTrue(juliusPhone.unregister(juliusContact, 3600));
        isJuliusRegistered = false;
        Thread.sleep(1000);
    }

    private void registerAugust() throws ParseException, InterruptedException {
        SipURI uri = augustSipStack.getAddressFactory().createSipURI(null, restcommContact);

        ExecutorService executorService = Executors.newSingleThreadExecutor();
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                imsAugustPhone.listenRequestMessage();
                RequestEvent requestEvent = imsAugustPhone.waitRequest(10000);
                assertNotNull(requestEvent);
                try {
                    Response response = imsSipStack.getMessageFactory().createResponse(200, requestEvent.getRequest());
                    ContactHeader contactHeader = augustSipStack.getHeaderFactory().createContactHeader();
                    contactHeader.setExpires(14400);
                    contactHeader.setAddress(augustSipStack.getAddressFactory().createAddress(imsContact));
                    response.addHeader(contactHeader);
                    imsAugustPhone.sendReply(requestEvent, response);
                } catch (ParseException e) {
                    e.printStackTrace();
                    fail(e.getMessage());
                } catch (InvalidArgumentException e) {
                    e.printStackTrace();
                    fail(e.getMessage());
                }

            }
        });

        assertTrue(augustPhone.register(uri, "august", "1234", augustContact, 14400, 3600));
        isAugustRegistered = true;
        Thread.sleep(1000);

        Credential c = new Credential("127.0.0.1", "august", "1234");
        augustPhone.addUpdateCredential(c);
    }

    private void initiateAugust(SipCall toCall, String toUri, SipCall augustCall) throws ParseException, InterruptedException {
        toCall.listenForIncomingCall();

        Thread.sleep(1000);

        //Change UserAgent header to "sipunit" so CallManager
        ArrayList<String> replaceHeaders = new ArrayList<String>();
        List<String> userAgentList = new ArrayList<String>();
        userAgentList.add("wss-sipunit");
        UserAgentHeader userAgentHeader = augustSipStack.getHeaderFactory().createUserAgentHeader(userAgentList);
        replaceHeaders.add(userAgentHeader.toString());

        // August initiates a call to pstn
        URI uri1 = augustSipStack.getAddressFactory().createURI("sip:" + restcommContact);
        SipURI sipURI = (SipURI) uri1;
        sipURI.setLrParam();
        Address address = augustSipStack.getAddressFactory().createAddress(uri1);

        RouteHeader routeHeader = augustSipStack.getHeaderFactory().createRouteHeader(address);
        replaceHeaders.add(routeHeader.toString());
        Header user = augustSipStack.getHeaderFactory().createHeader("X-RestComm-Ims-User", "myUser");
        Header pass = augustSipStack.getHeaderFactory().createHeader("X-RestComm-Ims-Password", "myPass");
        replaceHeaders.add(user.toString());
        replaceHeaders.add(pass.toString());
        augustCall.initiateOutgoingCall(augustContact, toUri, null, body, "application", "sdp", null, replaceHeaders);
        assertLastOperationSuccess(augustCall);

        assertTrue(augustCall.waitOutgoingCallResponse(5 * 1000));
        int responseAugust = augustCall.getLastReceivedResponse().getStatusCode();
        assertTrue(responseAugust == Response.TRYING || responseAugust == Response.RINGING);

        Dialog augustDialog = null;

        if (responseAugust == Response.TRYING) {
            assertTrue(augustCall.waitOutgoingCallResponse(5 * 1000));
            assertEquals(Response.RINGING, augustCall.getLastReceivedResponse().getStatusCode());
            augustDialog = augustCall.getDialog();
            assertNotNull(augustDialog);
        }

        assertTrue(toCall.waitForIncomingCall(5 * 1000));

        DigestServerAuthenticationMethod dsam = new DigestServerAuthenticationMethod();
        dsam.initialize(); // it should read values from file, now all static

        ProxyAuthenticateHeader proxyAuthenticate = augustSipStack.getHeaderFactory().createProxyAuthenticateHeader(
                dsam.getScheme());
        proxyAuthenticate.setParameter("realm", dsam.getRealm(null));
        proxyAuthenticate.setParameter("nonce", dsam.generateNonce());
        // proxyAuthenticateImpl.setParameter("domain",authenticationMethod.getDomain());
        proxyAuthenticate.setParameter("opaque", "");

        proxyAuthenticate.setParameter("algorithm", dsam.getAlgorithm());
        ArrayList<Header> headers = new ArrayList<Header>();
        headers.add(proxyAuthenticate);
        assertTrue(toCall.sendIncomingCallResponse(Response.PROXY_AUTHENTICATION_REQUIRED, "Non authorized", 3600, headers,
                null, null));

    }

    private void initiatePstn(SipCall pstnCall, SipCall augustCall) throws ParseException, InterruptedException {
        // Prepare august phone to receive call

        augustCall.listenForIncomingCall();

        // Create outgoing call with pstn phone
        ArrayList<String> replaceHeaders = new ArrayList<String>();
        URI uri1 = augustSipStack.getAddressFactory().createURI(augustContact);
        SipURI sipURI = (SipURI) uri1;
        sipURI.setLrParam();
        Address address = augustSipStack.getAddressFactory().createAddress(uri1);
        ToHeader toHeader = augustSipStack.getHeaderFactory().createToHeader(address, null);
        replaceHeaders.add(toHeader.toString());

        pstnCall.initiateOutgoingCall(pstnContact, "sip:" + restcommContact, null, body, "application", "sdp", null, replaceHeaders);
        assertLastOperationSuccess(pstnCall);
        assertTrue(pstnCall.waitOutgoingCallResponse(5 * 1000));
        int responsePstn = pstnCall.getLastReceivedResponse().getStatusCode();
        assertTrue(responsePstn == Response.TRYING || responsePstn == Response.RINGING);
    }

    @Deployment(name = "ImsClientsDialTest", managed = true, testable = false)
    public static WebArchive createWebArchiveNoGw() {
        logger.info("Packaging Test App");
        reconfigurePorts();

        Map<String, String> webInfResources = new HashMap();
        webInfResources.put("restcomm-ims-delay.xml", "conf/restcomm.xml");
        webInfResources.put("restcomm.script_imsDialTest", "data/hsql/restcomm.script");
        webInfResources.put("sip-ims.xml", "/sip.xml");
        webInfResources.put("web.xml", "web.xml");
        webInfResources.put("akka_application.conf", "classes/application.conf");

        Map<String, String> replacements = new HashMap();
        //replace mediaport 2727
        replacements.put("2727", String.valueOf(mediaPort));
        replacements.put("8080", String.valueOf(restcommHTTPPort));
        replacements.put("8090", String.valueOf(mockPort));
        replacements.put("5080", String.valueOf(restcommPort));
        replacements.put("5092", String.valueOf(augustPort));
        replacements.put("5094", String.valueOf(juliusPort));
        replacements.put("5060", String.valueOf(imsPort));
        replacements.put("9999", String.valueOf(imsPort));


        List<String> resources = new ArrayList(Arrays.asList(
                "dial-conference-entry.xml",
                "dial-fork-entry.xml",
                "dial-number-entry.xml",
                "dial-client-entry.xml",
                "dial-uri-entry.xml"
        ));
        return WebArchiveUtil.createWebArchiveNoGw(
                webInfResources, resources,
                replacements);
    }
}
