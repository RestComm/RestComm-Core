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
import javax.sip.address.Address;
import javax.sip.address.SipURI;
import javax.sip.address.URI;
import javax.sip.header.ContactHeader;
import javax.sip.header.Header;
import javax.sip.header.ProxyAuthenticateHeader;
import javax.sip.header.RouteHeader;
import javax.sip.header.ToHeader;
import javax.sip.header.UserAgentHeader;
import javax.sip.message.Request;
import javax.sip.message.Response;

import org.apache.log4j.Logger;
import org.cafesip.sipunit.Credential;
import org.cafesip.sipunit.SipCall;
import org.cafesip.sipunit.SipPhone;
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
import org.junit.runner.RunWith;
import org.restcomm.connect.commons.Version;
import org.restcomm.connect.testsuite.http.RestcommCallsTool;
import org.restcomm.connect.testsuite.telephony.security.DigestServerAuthenticationMethod;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.google.gson.JsonObject;
import org.junit.experimental.categories.Category;
import org.restcomm.connect.commons.annotations.FeatureAltTests;
import org.restcomm.connect.commons.annotations.SequentialClassTests;
import org.restcomm.connect.commons.annotations.UnstableTests;

/**
 * Test for clients with or without VoiceURL (Bitbucket issue 115). Clients without VoiceURL can dial anything.
 *
 * @author <a href="mailto:gvagenas@gmail.com">gvagenas</a>
 */
@RunWith(Arquillian.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@Category(value={FeatureAltTests.class, SequentialClassTests.class})
public class ImsClientsDialAnswerDelayCancelTest {

    private static final String version = Version.getVersion();

    private static Logger logger = Logger.getLogger(ImsClientsDialAnswerDelayCancelTest.class);

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

    private String pstnNumber = "+151261006100";


    // Maria is a Restcomm Client **without** VoiceURL. This Restcomm Client can dial anything.
    private SipStack augustSipStack;
    private SipPhone augustPhone;
    private String augustContact = "sip:august@127.0.0.1:5092";
    private boolean isAugustRegistered = false;

    private SipStack juliusSipStack;
    private SipPhone juliusPhone;
    private String juliusContact = "sip:julius@127.0.0.1:5094";
    private boolean isJuliusRegistered = false;

    private SipStack imsSipStack;
    private SipPhone imsAugustPhone;
    private SipPhone imsJuliusPhone;
    private String imsContact = "sip:127.0.0.1";

    private SipPhone pstnPhone;
    private String pstnContact = "sip:"+pstnNumber+"@127.0.0.1:5060";

    private String adminAccountSid = "AC27f2dd02ab51ba5d5a9ff7fc5537a09a";
    private String adminAuthToken = "77f8c12cc7b8f8423e5c38b035249166";

    @BeforeClass
    public static void beforeClass() throws Exception {
        tool1 = new SipStackTool("ImsClientsDialTest1");
        tool2 = new SipStackTool("ImsClientsDialTest2");
        tool3 = new SipStackTool("ImsClientsDialTest3");

        Class.forName("org.hsqldb.jdbc.JDBCDriver");
    }

    @Before
    public void before() throws Exception {

    	imsSipStack = tool1.initializeSipStack(SipStack.PROTOCOL_UDP, "127.0.0.1", "5060", "127.0.0.1:5080");

        augustSipStack = tool2.initializeSipStack(SipStack.PROTOCOL_UDP, "127.0.0.1", "5092", "127.0.0.1:5080");
        augustPhone = augustSipStack.createSipPhone("127.0.0.1", SipStack.PROTOCOL_UDP, 5080, augustContact);
        imsAugustPhone = imsSipStack.createSipPhone("127.0.0.1", SipStack.PROTOCOL_UDP, 5080, augustContact);
        imsAugustPhone.setLoopback(true);
        imsAugustPhone.setPassThroughRegisterRequests(true);

        juliusSipStack = tool3.initializeSipStack(SipStack.PROTOCOL_UDP, "127.0.0.1", "5094", "127.0.0.1:5080");
        juliusPhone = juliusSipStack.createSipPhone("127.0.0.1", SipStack.PROTOCOL_UDP, 5080, juliusContact);
        imsJuliusPhone = imsSipStack.createSipPhone("127.0.0.1", SipStack.PROTOCOL_UDP, 5080, juliusContact);
        imsJuliusPhone.setLoopback(true);
        imsJuliusPhone.setPassThroughRegisterRequests(true);

        pstnPhone = imsSipStack.createSipPhone("127.0.0.1", SipStack.PROTOCOL_UDP, 5080, pstnContact);
        pstnPhone.setPassThroughRegisterRequests(true);

        if(isAugustRegistered){
        	unregisterAugust();
        }

        if(isJuliusRegistered){
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
    @Category(UnstableTests.class)
    public void testWebRTCClientOutgoingCancel() throws ParseException, InterruptedException, SQLException {

        logger.info("testWebRTCClientOutgoingCancel");
        registerAugust();

        SipCall pstnCall = pstnPhone.createSipCall();
        final SipCall augustCall = augustPhone.createSipCall();
        initiateAugust(pstnCall,pstnContact,augustCall);

        assertTrue(pstnCall.waitForIncomingCall(5 * 1000));
        assertTrue(pstnCall.sendIncomingCallResponse(Response.RINGING, "RINGING-Pstn", 3600));
        SipTransaction pstnInviteTx = pstnCall.getLastTransaction();
        Request pstnInvite = pstnInviteTx.getServerTransaction().getRequest();

        Thread.sleep(500);

        pstnCall.listenForCancel();

        SipTransaction augustCancelTransaction = augustCall.sendCancel();
        assertTrue(augustCancelTransaction != null);

        SipTransaction pstnCancelTransaction = pstnCall.waitForCancel(5 * 1000);
        assertTrue(pstnCancelTransaction != null);
        pstnCall.respondToCancel(pstnCancelTransaction, 200, "OK-pstn", 3600);

        Response pstnResponseTerminated = imsSipStack.getMessageFactory().createResponse(Response.REQUEST_TERMINATED, pstnInvite);
        try{
            pstnInviteTx.getServerTransaction().sendResponse(pstnResponseTerminated);
        }
        catch(Exception e){
            e.printStackTrace();
        }

        assertTrue(augustCall.waitOutgoingCallResponse(5 * 1000));
        assertEquals(Response.REQUEST_TERMINATED, augustCall.getLastReceivedResponse().getStatusCode());

        Thread.sleep(1000);

        Map<String, String> filters = new HashMap<String, String>();
        JsonObject filteredCallsByStatusObject = RestcommCallsTool.getInstance().getCallsUsingFilter(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, filters);
        assertEquals(0, filteredCallsByStatusObject.get("calls").getAsJsonArray().size());

        unregisterAugust();
    }

    @Test
    @Category(UnstableTests.class)
    public void testWebRTCClientIncomingCancel() throws InterruptedException, ParseException {
        logger.info("testWebRTCClientIncomingCancel");

        registerAugust();


        SipCall augustCall = augustPhone.createSipCall();
        SipCall pstnCall = pstnPhone.createSipCall();
        initiatePstn(pstnCall, augustCall);


        assertTrue(augustCall.waitForIncomingCall(30 * 1000));
        assertTrue(augustCall.sendIncomingCallResponse(Response.RINGING, "Ringing-August", 3600));
        SipTransaction augustInviteTx = augustCall.getLastTransaction();
        Request augustInvite = augustInviteTx.getServerTransaction().getRequest();

        augustCall.listenForCancel();

        SipTransaction pstnCancelTransaction = pstnCall.sendCancel();
        assertTrue(pstnCancelTransaction != null);

        SipTransaction augustCancelTransaction = augustCall.waitForCancel(5 * 1000);
        assertTrue(augustCancelTransaction != null);
        augustCall.respondToCancel(augustCancelTransaction, 200, "OK-pstn", 3600);

        Response augustResponseTerminated = imsSipStack.getMessageFactory().createResponse(Response.REQUEST_TERMINATED, augustInvite);
        try{
            augustInviteTx.getServerTransaction().sendResponse(augustResponseTerminated);
        }
        catch(Exception e){
            e.printStackTrace();
        }

        assertTrue(pstnCall.waitOutgoingCallResponse(5 * 1000));
        assertEquals(Response.REQUEST_TERMINATED, pstnCall.getLastReceivedResponse().getStatusCode());


        Thread.sleep(1000);

        Map<String, String> filters = new HashMap<String, String>();
        JsonObject filteredCallsByStatusObject = RestcommCallsTool.getInstance().getCallsUsingFilter(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, filters);
        assertEquals(0, filteredCallsByStatusObject.get("calls").getAsJsonArray().size());

        unregisterAugust();
    }

    private void unregisterAugust() throws InterruptedException{
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

    private void unregisterJulius() throws InterruptedException{
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

    private void registerAugust() throws ParseException, InterruptedException{
        SipURI uri = augustSipStack.getAddressFactory().createSipURI(null, "127.0.0.1:5080");

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
        URI uri1 = augustSipStack.getAddressFactory().createURI("sip:127.0.0.1:5080");
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
        URI uri1 = augustSipStack.getAddressFactory().createURI("sip:august@127.0.0.1:5080");
        SipURI sipURI = (SipURI) uri1;
        sipURI.setLrParam();
        Address address = augustSipStack.getAddressFactory().createAddress(uri1);
        ToHeader toHeader = augustSipStack.getHeaderFactory().createToHeader(address, null);
        replaceHeaders.add(toHeader.toString());

        pstnCall.initiateOutgoingCall(pstnContact, "sip:127.0.0.1:5080", null, body, "application", "sdp", null, replaceHeaders);
        assertLastOperationSuccess(pstnCall);
        assertTrue(pstnCall.waitOutgoingCallResponse(5 * 1000));
        int responsePstn = pstnCall.getLastReceivedResponse().getStatusCode();
        assertTrue(responsePstn == Response.TRYING || responsePstn == Response.RINGING);

        if (responsePstn == Response.TRYING) {
            assertTrue(pstnCall.waitOutgoingCallResponse(5 * 1000));
            assertEquals(Response.RINGING, pstnCall.getLastReceivedResponse().getStatusCode());
        }
    }

    @Deployment(name = "ImsClientsDialTest", managed = true, testable = false)
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
        archive.addAsWebInfResource("sip-ims.xml", "/sip.xml");
        archive.addAsWebInfResource("restcomm-ims-delay.xml", "conf/restcomm.xml");
        archive.addAsWebInfResource("restcomm.script_imsDialTest", "data/hsql/restcomm.script");
        archive.addAsWebResource("dial-conference-entry.xml");
        archive.addAsWebResource("dial-fork-entry.xml");
        archive.addAsWebResource("dial-uri-entry.xml");
        archive.addAsWebResource("dial-client-entry.xml");
        archive.addAsWebResource("dial-number-entry.xml");
        return archive;
    }
}
