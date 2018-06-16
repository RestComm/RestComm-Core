package org.restcomm.connect.testsuite.smpp;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.URL;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;

import javax.sip.address.SipURI;
import javax.sip.message.Request;
import javax.sip.message.Response;

import org.apache.log4j.Logger;
import org.cafesip.sipunit.Credential;
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
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.restcomm.connect.commons.Version;
import org.restcomm.connect.commons.annotations.BrokenTests;
import org.restcomm.connect.commons.annotations.FeatureAltTests;
import org.restcomm.connect.commons.annotations.FeatureExpTests;
import org.restcomm.connect.commons.annotations.SequentialClassTests;
import org.restcomm.connect.commons.annotations.WithInSecsTests;
import org.restcomm.connect.dao.entities.SmsMessage;
import org.restcomm.connect.sms.smpp.SmppInboundMessageEntity;
import org.restcomm.connect.testsuite.sms.SmsEndpointTool;

import com.cloudhopper.commons.charset.Charset;
import com.cloudhopper.commons.charset.CharsetUtil;
import com.cloudhopper.smpp.type.SmppChannelException;
import com.cloudhopper.smpp.type.SmppInvalidArgumentException;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * @author quintana.thomas@gmail.com (Thomas Quintana)
 * @author gvagenas@gmail.com (George Vagenas)
 */
@RunWith(Arquillian.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@Category(value={SequentialClassTests.class, WithInSecsTests.class})
public class SmppTest {

	private final static Logger logger = Logger.getLogger(SmppTest.class);
	private static final String version = Version.getVersion();

    private static String to = "7777";
    private static String toPureSipProviderNumber = "7007";
    private static String from = "9999";
    private static String msgBody = "か~!@#$%^&*()-=\u263a\u00a1\u00a2\u00a3\u00a4\u00a5Message from SMPP Server to Restcomm";
    private static String msgBodyResp = "か~!@#$%^&*()-=\u263a\u00a1\u00a2\u00a3\u00a4\u00a5Response from Restcomm to SMPP server";
    private static String msgBodyRespUCS2 = "か~!@#$%^&*()-=\u263a\u00a1\u00a2\u00a3\u00a4\u00a5Response from Restcomm to SMPP server";

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(8090); // No-args constructor defaults to port 8080

	@ArquillianResource
	private Deployer deployer;

    @ArquillianResource
    URL deploymentUrl;
	private static MockSmppServer mockSmppServer;

	private static SipStackTool tool2;
	private SipStack aliceSipStack;
	private SipPhone alicePhone;
	private String aliceContact = "sip:alice@127.0.0.1:5092";

	private static SipStackTool tool3;
	private SipStack bobSipStack;
	private SipPhone bobPhone;
	private String bobContact = "sip:bob@127.0.0.1:5093";

	private static SipStackTool tool5;
	private SipStack mariaSipStack;
	private SipPhone mariaPhone;
	private String mariaContact = "sip:maria@org2.restcomm.com";

	private static SipStackTool tool6;
	private SipStack shoaibSipStack;
	private SipPhone shoaibPhone;
	private String shoaibContact = "sip:shoaib@org2.restcomm.com";

	private static SipStackTool tool4;
	private SipStack mariaOrg3SipStack;
	private SipPhone mariaOrg3Phone;
	private String mariaOrg3Contact = "sip:maria@org3.restcomm.com";

	private String adminAccountSid = "ACae6e420f425248d6a26948c17a9e2acf";
	private String adminAuthToken = "77f8c12cc7b8f8423e5c38b035249166";

	@BeforeClass
	public static void prepare() throws SmppChannelException, InterruptedException {
		tool2 = new SipStackTool("SmppTest2");
		tool3 = new SipStackTool("SmppTest3");
		tool4 = new SipStackTool("SmppTest4");
		tool5 = new SipStackTool("SmppTest5");
		tool6 = new SipStackTool("SmppTest6");

		mockSmppServer = new MockSmppServer();
		logger.info("Will wait for the SMPP link to be established");
		do {
			Thread.sleep(1000);
		} while (!mockSmppServer.isLinkEstablished());
		logger.info("SMPP link is now established");
	}

	@Before
	public void before() throws Exception {

		aliceSipStack = tool2.initializeSipStack(SipStack.PROTOCOL_UDP, "127.0.0.1", "5092", "127.0.0.1:5080");
		alicePhone = aliceSipStack.createSipPhone("127.0.0.1", SipStack.PROTOCOL_UDP, 5080, aliceContact);

		bobSipStack = tool3.initializeSipStack(SipStack.PROTOCOL_UDP, "127.0.0.1", "5093", "127.0.0.1:5080");
		bobPhone = bobSipStack.createSipPhone("127.0.0.1", SipStack.PROTOCOL_UDP, 5080, bobContact);

		mariaSipStack = tool5.initializeSipStack(SipStack.PROTOCOL_UDP, "127.0.0.1", "5095", "127.0.0.1:5080");
		mariaPhone = mariaSipStack.createSipPhone("127.0.0.1", SipStack.PROTOCOL_UDP, 5080, mariaContact);

		shoaibSipStack = tool6.initializeSipStack(SipStack.PROTOCOL_UDP, "127.0.0.1", "5096", "127.0.0.1:5080");
		shoaibPhone = shoaibSipStack.createSipPhone("127.0.0.1", SipStack.PROTOCOL_UDP, 5080, shoaibContact);

		mariaOrg3SipStack = tool4.initializeSipStack(SipStack.PROTOCOL_UDP, "127.0.0.1", "5094", "127.0.0.1:5080");
		mariaOrg3Phone = mariaOrg3SipStack.createSipPhone("127.0.0.1", SipStack.PROTOCOL_UDP, 5080, mariaOrg3Contact);

		mockSmppServer.cleanup();
		Thread.sleep(5000);
	}

	@AfterClass
	public static void cleanup() {
            if (mockSmppServer!= null) {
		mockSmppServer.stop();
            }
	}

	@After
	public void after() throws InterruptedException {
		if (bobPhone != null) {
			bobPhone.dispose();
		}
		if (bobSipStack != null) {
			bobSipStack.dispose();
		}

		if (alicePhone != null) {
			alicePhone.dispose();
		}
		if (aliceSipStack != null) {
			aliceSipStack.dispose();
		}
		if (shoaibPhone != null) {
			shoaibPhone.dispose();
		}
		if (shoaibSipStack != null) {
			shoaibSipStack.dispose();
		}

		if (mariaPhone != null) {
			mariaPhone.dispose();
		}
		if (mariaSipStack != null) {
			mariaSipStack.dispose();
		}

		if (mariaOrg3Phone != null) {
			mariaOrg3Phone.dispose();
		}
		if (mariaOrg3SipStack != null) {
			mariaOrg3SipStack.dispose();
		}
		Thread.sleep(2000);
        wireMockRule.resetRequests();
        Thread.sleep(2000);
	}

    @Test
    public void testSendMessageToRestcommUTF8() throws SmppInvalidArgumentException, IOException, InterruptedException {
        testSendMessageToRestcomm(msgBody, msgBodyResp, CharsetUtil.CHARSET_UTF_8);
    }

    @Test
    public void testSendMessageToRestcommUCS2() throws SmppInvalidArgumentException, IOException, InterruptedException {
        testSendMessageToRestcomm(msgBody, msgBodyRespUCS2, CharsetUtil.CHARSET_UCS_2);
    }

    public void testSendMessageToRestcomm(String msgBodySend, String msgBodyResp, Charset charset) throws SmppInvalidArgumentException, IOException, InterruptedException {

        String smsEchoRcml = "<Response><Sms to=\"" + from + "\" from=\"" + to + "\">" + msgBodyResp + "</Sms></Response>";
        stubFor(get(urlPathEqualTo("/smsApp")).willReturn(aResponse()
                .withStatus(200).withHeader("Content-Type", "text/xml")
                .withBody(smsEchoRcml)));

        mockSmppServer.sendSmppMessageToRestcomm(msgBodySend, to, from,
                charset);
        Thread.sleep(2000);
        assertTrue(mockSmppServer.isMessageSent());
        Thread.sleep(2000);
        assertTrue(mockSmppServer.isMessageReceived());
        SmppInboundMessageEntity inboundMessageEntity = mockSmppServer.getSmppInboundMessageEntity();
        assertNotNull(inboundMessageEntity);

        logger.info("msgBodyResp: " + msgBodyResp);
        logger.info("getSmppContent: " + inboundMessageEntity.getSmppContent());

        assertTrue(inboundMessageEntity.getSmppTo().equals(from));
        assertTrue(inboundMessageEntity.getSmppFrom().equals(to));
        assertTrue(inboundMessageEntity.getSmppContent().equals(msgBodyResp));
    }

    private String smsEchoRcmlPureSipProviderNumber = "<Response><Sms to=\""+from+"\" from=\""+toPureSipProviderNumber+"\">"+msgBodyResp+"</Sms></Response>";
	@Test //https://telestax.atlassian.net/browse/RESTCOMM-1428, https://telestax.atlassian.net/browse/POSTMORTEM-13
	public void testSendSMPPMessageToRestcommPureSipProviderNumber () throws SmppInvalidArgumentException, IOException, InterruptedException {

        stubFor(get(urlPathEqualTo("/smsApp"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(smsEchoRcmlPureSipProviderNumber)));

		mockSmppServer.sendSmppMessageToRestcomm(msgBody,toPureSipProviderNumber,from,CharsetUtil.CHARSET_GSM);
        Thread.sleep(2000);
        assertTrue(mockSmppServer.isMessageSent());
		Thread.sleep(2000);
		assertTrue(mockSmppServer.isMessageReceived());
		SmppInboundMessageEntity inboundMessageEntity = mockSmppServer.getSmppInboundMessageEntity();
		assertNotNull(inboundMessageEntity);
		assertTrue(inboundMessageEntity.getSmppTo().equals(from));
		assertTrue(inboundMessageEntity.getSmppFrom().equals(toPureSipProviderNumber));
		assertTrue(inboundMessageEntity.getSmppContent().equals(msgBodyResp));
	}
	
	@Test
	public void testSendSMPPMessageAndGetDeliveryReceipt () throws SmppInvalidArgumentException, IOException, InterruptedException, ParseException {

        stubFor(get(urlPathEqualTo("/smsApp"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(smsEchoRcmlPureSipProviderNumber)));

        final String from = "alice";
        final String to = "9999"; // pstn (not a RC number)
        // Send out SMS using SMPP
        final String body="Test Message from Alice. "+System.currentTimeMillis();
		SipURI uri = aliceSipStack.getAddressFactory().createSipURI(null, "127.0.0.1:5080");
		assertTrue(alicePhone.register(uri, from, "1234", aliceContact, 3600, 3600));
		Credential aliceCred = new Credential("127.0.0.1",from,"1234");
		alicePhone.addUpdateCredential(aliceCred);


		SipCall aliceCall = alicePhone.createSipCall();
		aliceCall.initiateOutgoingMessage("sip:9999@127.0.0.1:5080", null, body);
		aliceCall.waitForAuthorisation(8000);
		Thread.sleep(5000);
        SmppInboundMessageEntity inboundMessageEntity = mockSmppServer.getSmppInboundMessageEntity();
		assertNotNull(inboundMessageEntity);
		assertTrue(inboundMessageEntity.getSmppTo().equals(to));
		assertTrue(inboundMessageEntity.getSmppFrom().equals(from));
		assertTrue(inboundMessageEntity.getSmppContent().equals(body));
		final String smppMessageId = mockSmppServer.getSmppMessageId();
		
		// Verify SMS CDR
		Map<String, String> filters = new HashMap<String, String>();
        filters.put("From", from);
        filters.put("To", to);
        filters.put("Body", body);
		JsonObject smsCdrResult = SmsEndpointTool.getInstance().getSmsMessageListUsingFilter(deploymentUrl.toString(), adminAccountSid, adminAuthToken, filters);
        assertNotNull(smsCdrResult);
        JsonElement msgs = smsCdrResult.get("messages");
        JsonObject smsCDR = msgs.getAsJsonArray().get(0).getAsJsonObject();
        assertNotNull(smsCDR);
        final String sid = smsCDR.get("sid").getAsString();
        String status = smsCDR.get("status").getAsString();
        String actualFrom = smsCDR.get("from").getAsString();
        String actualTo = smsCDR.get("to").getAsString();
        assertEquals(SmsMessage.Status.SENT.toString(), status);
        assertEquals("alice", actualFrom);
        assertEquals("9999", actualTo);
        
        // Ask SMPP mock server to Send DLR to RC
		mockSmppServer.sendSmppDeliveryMessageToRestcomm(smppMessageId, MockSmppServer.SmppDeliveryStatus.DELIVRD);
        Thread.sleep(5000);
        
        // ReCheck CDR to make sure we get updated status
        smsCDR = SmsEndpointTool.getInstance().getSmsMessage(deploymentUrl.toString(), adminAccountSid, adminAuthToken, sid);
        assertNotNull(smsCdrResult);
        status = smsCDR.get("status").getAsString();
        assertEquals(SmsMessage.Status.DELIVERED.toString(), status);
	}
	
	@Test
	@Category(value={FeatureExpTests.class})
	public void testSendSMPPMessageViaAPIAndGetDeliveryReceipt () throws SmppInvalidArgumentException, IOException, InterruptedException, ParseException {

        stubFor(get(urlPathEqualTo("/smsApp"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(smsEchoRcmlPureSipProviderNumber)));
        
        final String from = "alice";
        final String to = "9999"; // pstn (not a RC number)
        // Send out SMS using SMPP via rest api
        final String body="Test Message from Alice. "+System.currentTimeMillis();
        SmsEndpointTool.getInstance().createSms(deploymentUrl.toString(), adminAccountSid, adminAuthToken, "alice", "9999", body, null);
        Thread.sleep(5000);
        SmppInboundMessageEntity inboundMessageEntity = mockSmppServer.getSmppInboundMessageEntity();
		assertNotNull(inboundMessageEntity);
		assertTrue(inboundMessageEntity.getSmppTo().equals(to));
		assertTrue(inboundMessageEntity.getSmppFrom().equals(from));
		assertTrue(inboundMessageEntity.getSmppContent().equals(body));
		final String smppMessageId = mockSmppServer.getSmppMessageId();
		
		// Verify SMS CDR
		Map<String, String> filters = new HashMap<String, String>();
        filters.put("From", from);
        filters.put("To", to);
        filters.put("Body", body);
		JsonObject smsCdrResult = SmsEndpointTool.getInstance().getSmsMessageListUsingFilter(deploymentUrl.toString(), adminAccountSid, adminAuthToken, filters);
        assertNotNull(smsCdrResult);
        JsonElement msgs = smsCdrResult.get("messages");
        JsonObject smsCDR = msgs.getAsJsonArray().get(0).getAsJsonObject();
        assertNotNull(smsCDR);
        final String sid = smsCDR.get("sid").getAsString();
        String status = smsCDR.get("status").getAsString();
        String actualFrom = smsCDR.get("from").getAsString();
        String actualTo = smsCDR.get("to").getAsString();
        assertEquals(SmsMessage.Status.SENT.toString(), status);
        assertEquals("alice", actualFrom);
        assertEquals("9999", actualTo);
        
        // Ask SMPP mock server to Send DLR to RC
		mockSmppServer.sendSmppDeliveryMessageToRestcomm(smppMessageId, MockSmppServer.SmppDeliveryStatus.DELIVRD);
        Thread.sleep(5000);
        
        // ReCheck CDR to make sure we get updated status
        smsCDR = SmsEndpointTool.getInstance().getSmsMessage(deploymentUrl.toString(), adminAccountSid, adminAuthToken, sid);
        assertNotNull(smsCdrResult);
        status = smsCDR.get("status").getAsString();
        assertEquals(SmsMessage.Status.DELIVERED.toString(), status);
	}

    private String smsEchoRcmlUCS2 = "<Response><Sms to=\""+from+"\" from=\""+to+"\">"+msgBodyRespUCS2+"</Sms></Response>";
	@Test
    @Category(value={FeatureAltTests.class, BrokenTests.class})
	public void testSendMessageToRestcommUCS2_2 () throws SmppInvalidArgumentException, IOException, InterruptedException {

        stubFor(get(urlPathEqualTo("/smsApp"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(smsEchoRcmlUCS2)));

		mockSmppServer.sendSmppMessageToRestcomm(msgBody,to,from,CharsetUtil.CHARSET_UCS_2);
        Thread.sleep(2000);
        assertTrue(mockSmppServer.isMessageSent());
		Thread.sleep(8000);
		assertTrue(mockSmppServer.isMessageReceived());
		SmppInboundMessageEntity inboundMessageEntity = mockSmppServer.getSmppInboundMessageEntity();
		assertNotNull(inboundMessageEntity);
		assertTrue(inboundMessageEntity.getSmppTo().equals(from));
		assertTrue(inboundMessageEntity.getSmppFrom().equals(to));
		logger.info("msgBodyResp: " + msgBodyRespUCS2);
		logger.info("getSmppContent: " + inboundMessageEntity.getSmppContent());
		assertTrue(inboundMessageEntity.getSmppContent().equals(msgBodyRespUCS2));
	}

	@Test
        @Category(value={FeatureAltTests.class})
	public void testClientSentToOtherClient () throws ParseException {

		SipURI uri = aliceSipStack.getAddressFactory().createSipURI(null, "127.0.0.1:5080");
		assertTrue(alicePhone.register(uri, "alice", "1234", aliceContact, 3600, 3600));
		Credential aliceCred = new Credential("127.0.0.1","alice","1234");
		alicePhone.addUpdateCredential(aliceCred);

		assertTrue(bobPhone.register(uri,"bob","1234",bobContact, 3600, 3600));
		Credential bobCread = new Credential("127.0.0.1","bob","1234");
		bobPhone.addUpdateCredential(bobCread);

		SipCall bobCall = bobPhone.createSipCall();
		bobCall.listenForMessage();

		SipCall aliceCall = alicePhone.createSipCall();
		assertTrue(aliceCall.initiateOutgoingMessage("sip:bob@127.0.0.1:5080", null, "Test Message from Alice"));
		assertTrue(aliceCall.waitForAuthorisation(5000));
		assertTrue(aliceCall.waitOutgoingMessageResponse(5000));

		assertTrue(bobCall.waitForMessage(5000));
		Request msgReceived = bobCall.getLastReceivedMessageRequest();
		assertTrue(new String(msgReceived.getRawContent()).equals("Test Message from Alice"));
	}

	@Test
	public void testClientSentOutUsingSMPP () throws ParseException, InterruptedException {

		SipURI uri = aliceSipStack.getAddressFactory().createSipURI(null, "127.0.0.1:5080");
		assertTrue(alicePhone.register(uri, "alice", "1234", aliceContact, 3600, 3600));
		Credential aliceCred = new Credential("127.0.0.1","alice","1234");
		alicePhone.addUpdateCredential(aliceCred);

		assertTrue(bobPhone.register(uri,"bob","1234",bobContact, 3600, 3600));
		Credential bobCread = new Credential("127.0.0.1","bob","1234");
		bobPhone.addUpdateCredential(bobCread);


		SipCall aliceCall = alicePhone.createSipCall();
		aliceCall.initiateOutgoingMessage("sip:9999@127.0.0.1:5080", null, "Test Message from Alice");
		aliceCall.waitForAuthorisation(8000);
		Thread.sleep(5000);
		assertTrue(mockSmppServer.isMessageReceived());
		SmppInboundMessageEntity inboundMessageEntity = mockSmppServer.getSmppInboundMessageEntity();
		assertNotNull(inboundMessageEntity);
		assertTrue(inboundMessageEntity.getSmppTo().equals("9999"));
		assertTrue(inboundMessageEntity.getSmppFrom().equals("alice"));
		assertTrue(inboundMessageEntity.getSmppContent().equals("Test Message from Alice"));
    }

    @Test
    @Ignore
    public void testClientSentOutUsingSMPPDeliveryReceipt() throws ParseException, InterruptedException {
        final String msg = "Test Message from Alice with Delivery Receipt";
        SipURI uri = aliceSipStack.getAddressFactory().createSipURI(null, "127.0.0.1:5080");
        assertTrue(alicePhone.register(uri, "alice", "1234", aliceContact, 3600, 3600));
        Credential aliceCred = new Credential("127.0.0.1", "alice", "1234");
        alicePhone.addUpdateCredential(aliceCred);

        SipCall aliceCall = alicePhone.createSipCall();
        aliceCall.initiateOutgoingMessage("sip:9999@127.0.0.1:5080", null, msg);
        aliceCall.waitForAuthorisation(8000);
        Thread.sleep(5000);
        assertTrue(mockSmppServer.isMessageReceived());
        SmppInboundMessageEntity inboundMessageEntity = mockSmppServer.getSmppInboundMessageEntity();
        assertNotNull(inboundMessageEntity);
        assertTrue(inboundMessageEntity.getSmppTo().equals("9999"));
        assertTrue(inboundMessageEntity.getSmppFrom().equals("alice"));
        assertTrue(inboundMessageEntity.getSmppContent().equals(msg));
        assertTrue(inboundMessageEntity.getIsDeliveryReceipt());
    }

	@Test
        @Category(value={FeatureExpTests.class})
	public void testClientSentToOtherClientDifferentOrganization () throws ParseException {

		SipURI uri = mariaSipStack.getAddressFactory().createSipURI(null, "127.0.0.1:5080");
		assertTrue(mariaPhone.register(uri, "maria", "qwerty1234RT", "sip:maria@127.0.0.1:5095", 3600, 3600));
		Credential mariaCred = new Credential("org2.restcomm.com","maria","qwerty1234RT");
		mariaPhone.addUpdateCredential(mariaCred);

		assertTrue(mariaOrg3Phone.register(uri,"maria","1234","sip:maria@127.0.0.1:5094", 3600, 3600));
		Credential mariaOrg3Cread = new Credential("org3.restcomm.com","maria","1234");
		mariaOrg3Phone.addUpdateCredential(mariaOrg3Cread);

		SipCall mariaOrg3Call = mariaOrg3Phone.createSipCall();
		mariaOrg3Call.listenForMessage();

		SipCall mariaCall = mariaPhone.createSipCall();
		assertTrue(mariaCall.initiateOutgoingMessage(mariaOrg3Contact, null, "Test Message from maria"));
		assertTrue(mariaCall.waitForAuthorisation(5000));
		assertTrue(mariaCall.waitOutgoingMessageResponse(5000));

		int responseMariaCall = mariaCall.getLastReceivedResponse().getStatusCode();
        logger.info("responseMariaCall: "+responseMariaCall);
        assertEquals(Response.NOT_FOUND, responseMariaCall);

	}

	@Test
	public void testClientSentToOtherClientSameOrganization () throws ParseException {

		SipURI uri = mariaSipStack.getAddressFactory().createSipURI(null, "127.0.0.1:5080");
		assertTrue(mariaPhone.register(uri, "maria", "qwerty1234RT", "sip:maria@127.0.0.1:5095", 3600, 3600));
		Credential mariaCred = new Credential("org2.restcomm.com","maria","qwerty1234RT");
		mariaPhone.addUpdateCredential(mariaCred);

		assertTrue(shoaibPhone.register(uri,"shoaib","qwerty1234RT","sip:shoaib@127.0.0.1:5096", 3600, 3600));
		Credential shoaibCread = new Credential("org2.restcomm.com","shoaib","qwerty1234RT");
		shoaibPhone.addUpdateCredential(shoaibCread);

		SipCall shoaibCall = shoaibPhone.createSipCall();
		shoaibCall.listenForMessage();

		SipCall mariaCall = mariaPhone.createSipCall();
		assertTrue(mariaCall.initiateOutgoingMessage(shoaibContact, null, "Test Message from maria"));
		assertTrue(mariaCall.waitForAuthorisation(5000));
		assertTrue(mariaCall.waitOutgoingMessageResponse(5000));

		assertTrue(shoaibCall.waitForMessage(5000));
		Request msgReceived = shoaibCall.getLastReceivedMessageRequest();
		assertTrue(new String(msgReceived.getRawContent()).equals("Test Message from maria"));
	}
	
	@Test
	@Category(value={FeatureExpTests.class})
	public void testSendSMPPMessageWithFailedStatus () throws SmppInvalidArgumentException, IOException, InterruptedException, ParseException {

        stubFor(get(urlPathEqualTo("/smsApp"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(smsEchoRcmlPureSipProviderNumber)));
        
        //set submit_sm_resp to Failed
        mockSmppServer.setSendFailureOnSubmitSmResponse(true);

        final String from = "alice";
        final String to = "9999"; // pstn (not a RC number)
        // Send out SMS using SMPP via rest api
        final String body="Test Message from Alice. "+System.currentTimeMillis();
        SmsEndpointTool.getInstance().createSms(deploymentUrl.toString(), adminAccountSid, adminAuthToken, "alice", "9999", body, null);
        Thread.sleep(5000);
        SmppInboundMessageEntity inboundMessageEntity = mockSmppServer.getSmppInboundMessageEntity();
		assertNotNull(inboundMessageEntity);
		assertTrue(inboundMessageEntity.getSmppTo().equals(to));
		assertTrue(inboundMessageEntity.getSmppFrom().equals(from));
		assertTrue(inboundMessageEntity.getSmppContent().equals(body));
		final String smppMessageId = mockSmppServer.getSmppMessageId();
		
		// Verify SMS CDR
		Map<String, String> filters = new HashMap<String, String>();
        filters.put("From", from);
        filters.put("To", to);
        filters.put("Body", body);
		JsonObject smsCdrResult = SmsEndpointTool.getInstance().getSmsMessageListUsingFilter(deploymentUrl.toString(), adminAccountSid, adminAuthToken, filters);
        assertNotNull(smsCdrResult);
        JsonElement msgs = smsCdrResult.get("messages");
        JsonObject smsCDR = msgs.getAsJsonArray().get(0).getAsJsonObject();
        assertNotNull(smsCDR);
        final String sid = smsCDR.get("sid").getAsString();
        String status = smsCDR.get("status").getAsString();
        String actualFrom = smsCDR.get("from").getAsString();
        String actualTo = smsCDR.get("to").getAsString();
        assertEquals(SmsMessage.Status.FAILED.toString(), status);
        assertEquals("alice", actualFrom);
        assertEquals("9999", actualTo);
        
        //set submit_sm_resp to Pass
        mockSmppServer.setSendFailureOnSubmitSmResponse(false);
	}

	@Deployment(name = "SmppTests", managed = true, testable = false)
	public static WebArchive createWebArchive() {
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
		archive.addAsWebInfResource("restcomm-smpp.xml", "conf/restcomm.xml");
	    archive.addAsWebInfResource("restcomm.script-smpp", "data/hsql/restcomm.script");
		return archive;
	}
}
