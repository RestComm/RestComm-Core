package org.restcomm.connect.testsuite.sms;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.cafesip.sipunit.SipAssert.assertLastOperationSuccess;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.net.URL;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.sip.address.SipURI;
import javax.sip.header.Header;

import org.cafesip.sipunit.Credential;
import org.cafesip.sipunit.SipCall;
import org.cafesip.sipunit.SipPhone;
import org.cafesip.sipunit.SipStack;
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
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
//import org.restcomm.connect.sms.Version;
import org.restcomm.connect.commons.Version;
import org.restcomm.connect.commons.annotations.FeatureAltTests;
import org.restcomm.connect.commons.annotations.FeatureExpTests;
import org.restcomm.connect.commons.annotations.ParallelClassTests;
import org.restcomm.connect.commons.annotations.WithInSecsTests;
import org.restcomm.connect.testsuite.NetworkPortAssigner;
import org.restcomm.connect.testsuite.WebArchiveUtil;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * @author quintana.thomas@gmail.com (Thomas Quintana)
 * @author gvagenas@gmail.com (George Vagenas)
 */
@RunWith(Arquillian.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@Category(value={WithInSecsTests.class, ParallelClassTests.class})
public final class SmsSessionTest {
    private static final String version = Version.getVersion();

    @ArquillianResource
    private Deployer deployer;
    @ArquillianResource
    URL deploymentUrl;

    private static int mediaPort = NetworkPortAssigner.retrieveNextPortByFile();

    private static int mockPort = NetworkPortAssigner.retrieveNextPortByFile();
    @Rule
    public WireMockRule wireMockRule = new WireMockRule(mockPort);

    private static SipStackTool tool;
    private SipStack receiver;
    private SipPhone phone;
    private static String phonePort = String.valueOf(NetworkPortAssigner.retrieveNextPortByFile());
    private String phoneContact = "sip:+17778889999@127.0.0.1:" + phonePort;

    private static SipStackTool tool2;
    private SipStack alice;
    private SipPhone alicePhone;
    private static String alicePort = String.valueOf(NetworkPortAssigner.retrieveNextPortByFile());
    private String aliceContact = "sip:alice@127.0.0.1:" + alicePort;

    private String adminAccountSid = "ACae6e420f425248d6a26948c17a9e2acf";
    private String adminAuthToken = "77f8c12cc7b8f8423e5c38b035249166";

    private static int restcommPort = 5080;
    private static int restcommHTTPPort = 8080;
    private static String restcommContact = "127.0.0.1:" + restcommPort;

    public SmsSessionTest() {
        super();
    }

    @BeforeClass
    public static void beforeClass() throws Exception {
        tool = new SipStackTool("SmsSessionTest");
        tool2 = new SipStackTool("SmsSessionTest2");
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
        receiver = tool.initializeSipStack(SipStack.PROTOCOL_UDP, "127.0.0.1", phonePort, restcommContact);
        phone = receiver.createSipPhone("127.0.0.1", SipStack.PROTOCOL_UDP, restcommPort, phoneContact);

        alice = tool2.initializeSipStack(SipStack.PROTOCOL_UDP, "127.0.0.1", alicePort, restcommContact);
        alicePhone = alice.createSipPhone("127.0.0.1", SipStack.PROTOCOL_UDP, restcommPort, aliceContact);
    }

    @After
    public void after() throws Exception {
        if (phone != null) {
            phone.dispose();
        }
        if (receiver != null) {
            receiver.dispose();
        }

        if (alicePhone != null) {
            alicePhone.dispose();
        }
        if (alice != null) {
            alice.dispose();
        }
        Thread.sleep(1000);
    }

    @Test
    public void testSendSmsRedirectReceiveSms() throws ParseException {
        // Send restcomm an sms.
        final String proxy = phone.getStackAddress() + ":" + restcommPort + ";lr/udp";
        final String to = "sip:+12223334450@" + restcommContact;
        final String body = "Hello, waiting your response!";
        final SipCall call = phone.createSipCall();
        call.initiateOutgoingMessage(to, proxy, body);
        assertLastOperationSuccess(call);
        // Wait for a response sms.
        phone.setLoopback(true);
        phone.listenRequestMessage();
        assertTrue(call.waitForMessage(60 * 1000));
        call.sendMessageResponse(202, "Accepted", -1);
        final List<String> messages = call.getAllReceivedMessagesContent();
        assertTrue(messages.size() > 0);
        assertTrue(messages.get(0).equals("Hello World!"));
    }

    @Test
    @Category(value={FeatureAltTests.class})
    public void testSendSmsRedirectReceiveSms2() throws ParseException {
        // Send restcomm an sms.
        final String proxy = phone.getStackAddress() + ":" + restcommPort + ";lr/udp";
        final String to = "sip:2001@" + restcommContact;
        final String body = "Hello, waiting your response!";
        final SipCall call = phone.createSipCall();
        call.initiateOutgoingMessage(to, proxy, body);
        assertLastOperationSuccess(call);
        // Wait for a response sms.
        phone.setLoopback(true);
        phone.listenRequestMessage();
        assertTrue(call.waitForMessage(60 * 1000));
        call.sendMessageResponse(202, "Accepted", -1);
        final List<String> messages = call.getAllReceivedMessagesContent();
        assertTrue(messages.size() > 0);
        assertTrue(messages.get(0).equals("Hello World!"));
    }

    @Test
    @Category(value={FeatureAltTests.class})
    public void testSendSmsRedirectReceiveSms3() throws ParseException {
        // Send restcomm an sms.
        final String proxy = phone.getStackAddress() + ":" + restcommPort + ";lr/udp";
        final String to = "sip:2001@" + restcommContact;
        final String body = "Hello, waiting your response!";
        final SipCall call = phone.createSipCall();
        call.initiateOutgoingMessage(to, proxy, body);
        assertLastOperationSuccess(call);
        // Wait for a response sms.
        phone.setLoopback(true);
        phone.listenRequestMessage();
        assertTrue(call.waitForMessage(60 * 1000));
        call.sendMessageResponse(202, "Accepted", -1);
        final List<String> messages = call.getAllReceivedMessagesContent();
        assertTrue(messages.size() > 0);
        assertTrue(messages.get(0).equals("Hello World!"));
    }

    @Test
    public void testAliceEchoTest() throws ParseException {

        SipURI uri = alice.getAddressFactory().createSipURI(null, restcommContact);
        assertTrue(alicePhone.register(uri, "alice", "1234", aliceContact, 3600, 3600));

        Credential credential = new Credential("127.0.0.1", "alice", "1234");
        alicePhone.addUpdateCredential(credential);

        // Prepare Alice to receive call
        final SipCall aliceCall = alicePhone.createSipCall();
        aliceCall.listenForMessage();

//        // Send restcomm an sms.
        final String proxy = phone.getStackAddress() + ":" + restcommPort + ";lr/udp";
        final String to = "sip:2002@" + restcommContact;
        final String body = "Hello, waiting your response!";
        final SipCall call = phone.createSipCall();
        call.initiateOutgoingMessage(to, proxy, body);

        // Wait for a response sms.
        alicePhone.setLoopback(true);
        aliceCall.listenForMessage();
        assertTrue(aliceCall.waitForMessage(5000));
        assertTrue(aliceCall.sendMessageResponse(202, "Alice-Accepted", -1));
        String messageBody = new String(aliceCall.getLastReceivedMessageRequest().getRawContent());
        assertTrue(messageBody.equals("Hello World!"));
        aliceCall.initiateOutgoingMessage(phoneContact, null, "Its great to hear from you!");
        assertTrue(aliceCall.waitForAuthorisation(5000));

        call.listenForMessage();
        assertTrue(call.waitForMessage(6 * 1000));
        call.sendMessageResponse(202, "Accepted", -1);
        final List<String> messages = call.getAllReceivedMessagesContent();
        assertTrue(messages.size() > 0);
        assertTrue(messages.get(0).equals("Its great to hear from you!"));
    }

    private String smsRcml = "<Response><Sms to=\"alice\" from=\"restcomm\">Hello World!</Sms></Response>";
    @Test
    @Category(value={FeatureAltTests.class})
    public void testSmsWithCustomHeaders() throws ParseException {
        stubFor(get(urlPathEqualTo("/rcml"))
                .withQueryParam("SipHeader_X-MyCustom-Header1", containing("Value1"))
                .withQueryParam("SipHeader_X-MyCustom-Header2", containing("Value2"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/xml")
                    .withBody(smsRcml)));

        SipURI uri = alice.getAddressFactory().createSipURI(null, restcommContact);
        assertTrue(alicePhone.register(uri, "alice", "1234", aliceContact, 3600, 3600));

        Credential credential = new Credential("127.0.0.1", "alice", "1234");
        alicePhone.addUpdateCredential(credential);

        // Prepare Alice to receive call
        final SipCall aliceCall = alicePhone.createSipCall();
        aliceCall.listenForMessage();

//        // Send restcomm an sms.
        final String proxy = phone.getStackAddress() + ":" + restcommPort + ";lr/udp";
        final String to = "sip:2003@" + restcommContact;
        final String body = "Hello, waiting your response!";
        final SipCall call = phone.createSipCall();
        ArrayList<Header> additionalHeaders = new ArrayList<Header>();
        additionalHeaders.add(phone.getParent().getHeaderFactory().createHeader("X-MyCustom-Header1", "Value1"));
        additionalHeaders.add(phone.getParent().getHeaderFactory().createHeader("X-MyCustom-Header2", "Value2"));
        call.initiateOutgoingMessage(phoneContact, to, proxy, additionalHeaders, null, body);
//        call.initiateOutgoingMessage(to, proxy, body);

        // Wait for a response sms.
        alicePhone.setLoopback(true);
        aliceCall.listenForMessage();
        assertTrue(aliceCall.waitForMessage(5000));
        assertTrue(aliceCall.sendMessageResponse(202, "Alice-Accepted", -1));
        String messageBody = new String(aliceCall.getLastReceivedMessageRequest().getRawContent());
        assertTrue(messageBody.equals("Hello World!"));
        aliceCall.initiateOutgoingMessage(phoneContact, null, "Its great to hear from you!");
        assertTrue(aliceCall.waitForAuthorisation(5000));

        call.listenForMessage();
        assertTrue(call.waitForMessage(6 * 1000));
        call.sendMessageResponse(202, "Accepted", -1);
        final List<String> messages = call.getAllReceivedMessagesContent();
        assertTrue(messages.size() > 0);
        assertTrue(messages.get(0).equals("Its great to hear from you!"));
    }

    @Test
    @Category(value={FeatureExpTests.class})
    public void sendMessageUsingValidContentType() throws ParseException, InterruptedException {
        final String proxy = phone.getStackAddress() + ":" + restcommPort + ";lr/udp";
        final String to = "sip:+12223334450@" + restcommContact;
        final String body = "VALID-CONTENT-TYPE";
        final SipCall call = phone.createSipCall();
        gov.nist.javax.sip.header.ContentType header = new gov.nist.javax.sip.header.ContentType();
        header.setContentType("text");
        header.setContentSubType("plain;charset=UTF-8");
        ArrayList<Header> replaceHeaders = new ArrayList<Header>();
        replaceHeaders.add(header);
        call.initiateOutgoingMessage(null, to, proxy, new ArrayList<Header>(), replaceHeaders, body);
        assertLastOperationSuccess(call);
        Thread.sleep(1000);
        // Verify if message was properly registered
        JsonArray array = SmsEndpointTool.getInstance().getSmsList(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        assertNotNull(array);
        boolean found = false;
        for (int i = 0; i < array.size(); i++) {
            if (((JsonObject) array.get(i)).get("body").getAsString().equals(body)) {
                found = true;
                break;
            }
        }
        assertTrue(found);
    }

    @Test
    @Category(value={FeatureExpTests.class})
    public void sendMessageUsingInvalidContentType() throws ParseException {
        final String proxy = phone.getStackAddress() + ":" + restcommPort + ";lr/udp";
        final String to = "sip:+12223334450@" + restcommContact;
        final String body = "INVALID-CONTENT-TYPE-COMPOSING";
        final SipCall call = phone.createSipCall();
        gov.nist.javax.sip.header.ContentType header = new gov.nist.javax.sip.header.ContentType();
        header.setContentType("application");
        header.setContentSubType("im-iscomposing+xml");
        ArrayList<Header> replaceHeaders = new ArrayList<Header>();
        replaceHeaders.add(header);
        call.initiateOutgoingMessage(null, to, proxy, new ArrayList<Header>(), replaceHeaders, body);
        assertLastOperationSuccess(call);
        assertTrue(call.waitOutgoingMessageResponse(5000));
        assertTrue(call.getLastReceivedResponse().getStatusCode() == 406);
        // Verify if message was properly discarded
        JsonArray array = SmsEndpointTool.getInstance().getSmsList(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        boolean found = false;
        for (int i = 0; i < array.size(); i++) {
            if (((JsonObject) array.get(i)).get("body").getAsString().equals(body)) {
                found = true;
                break;
            }
        }
        assertFalse(found);
    }

    @Test
    @Category(value={FeatureExpTests.class})
    public void sendMessageUsingInvalidContentType2() throws ParseException {
        final String proxy = phone.getStackAddress() + ":" + restcommPort + ";lr/udp";
        final String to = "sip:+12223334450@" + restcommContact;
        final String body = "INVALID-CONTENT-TYPE-HTML";
        final SipCall call = phone.createSipCall();
        gov.nist.javax.sip.header.ContentType header = new gov.nist.javax.sip.header.ContentType();
        header.setContentType("text");
        header.setContentSubType("html;charset=UTF-8");
        ArrayList<Header> replaceHeaders = new ArrayList<Header>();
        replaceHeaders.add(header);
        call.initiateOutgoingMessage(null, to, proxy, new ArrayList<Header>(), replaceHeaders, body);
        assertLastOperationSuccess(call);
        assertTrue(call.waitOutgoingMessageResponse(5000));
        assertTrue(call.getLastReceivedResponse().getStatusCode() == 406);
        // Verify if message was properly discarded
        JsonArray array = SmsEndpointTool.getInstance().getSmsList(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        boolean found = false;
        for (int i = 0; i < array.size(); i++) {
            if (((JsonObject) array.get(i)).get("body").getAsString().equals(body)) {
                found = true;
                break;
            }
        }
        assertFalse(found);
    }

    @Deployment(name = "SmsSessionTest", managed = true, testable = false)
    public static WebArchive createWebArchive() {
        reconfigurePorts();

        Map<String, String> webInfResources = new HashMap();
        webInfResources.put("restcomm_SmsTest.xml", "conf/restcomm.xml");
        webInfResources.put("restcomm.script_SmsTest", "data/hsql/restcomm.script");
        webInfResources.put("sip.xml", "sip.xml");
        webInfResources.put("web.xml", "web.xml");
        webInfResources.put("akka_application.conf", "classes/application.conf");

        Map<String, String> replacements = new HashMap();
        //replace mediaport 2727
        replacements.put("2727", String.valueOf(mediaPort));
        replacements.put("8080", String.valueOf(restcommHTTPPort));
        replacements.put("8090", String.valueOf(mockPort));
        replacements.put("5080", String.valueOf(restcommPort));
        replacements.put("5091", String.valueOf(phonePort));
        replacements.put("5092", String.valueOf(alicePort));


        List<String> resources = new ArrayList(Arrays.asList(
                            "entry.xml",
                            "sms.xml",
                            "sms_to_alice.xml"
        ));
        return WebArchiveUtil.createWebArchiveNoGw(webInfResources,
                resources,
                replacements);
    }
}
