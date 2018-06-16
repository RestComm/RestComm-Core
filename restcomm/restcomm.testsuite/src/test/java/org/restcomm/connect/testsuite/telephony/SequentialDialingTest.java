package org.restcomm.connect.testsuite.telephony;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.google.gson.JsonObject;
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

import javax.sip.message.Response;
import java.net.URL;
import java.text.ParseException;
import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.cafesip.sipunit.SipAssert.assertLastOperationSuccess;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.experimental.categories.Category;
import org.restcomm.connect.commons.annotations.FeatureAltTests;
import org.restcomm.connect.commons.annotations.SequentialClassTests;
import org.restcomm.connect.commons.annotations.WithInMinsTests;
import org.restcomm.connect.testsuite.tools.MonitoringServiceTool;

/**
 * Created by gvagenas on 22/02/2017.
 */
@RunWith(Arquillian.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@Category(SequentialClassTests.class)
public class SequentialDialingTest {

	private final static Logger logger = Logger.getLogger(SequentialDialingTest.class.getName());

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
    private static SipStackTool tool4;

	// Bob is a simple SIP Client. Will not register with Restcomm
	private SipStack bobSipStack;
	private SipPhone bobPhone;
	private String bobContact = "sip:bob@127.0.0.1:5090";

	// Alice is a Restcomm Client with VoiceURL. This Restcomm Client can register with Restcomm and whatever will dial the RCML
	// of the VoiceURL will be executed.
	private SipStack aliceSipStack;
	private SipPhone alicePhone;
	private String aliceContact = "sip:alice@127.0.0.1:5091";

	private SipStack carolSipStack;
	private SipPhone carolPhone;
	private String carolContact = "sip:carol@127.0.0.1:5092";

	private SipStack daveSipStack;
	private SipPhone davePhone;
	private String daveContact = "sip:dave@127.0.0.1:5093";

	private String dialRestcomm = "sip:1111@127.0.0.1:5080";

	@BeforeClass
	public static void beforeClass() throws Exception {
		tool1 = new SipStackTool("SequentialDialingTool1");
		tool2 = new SipStackTool("SequentialDialingTool12");
		tool3 = new SipStackTool("SequentialDialingTool13");
		tool4 = new SipStackTool("SequentialDialingTool14");
	}

	@Before
	public void before() throws Exception {
		bobSipStack = tool1.initializeSipStack(SipStack.PROTOCOL_UDP, "127.0.0.1", "5090", "127.0.0.1:5080");
		bobPhone = bobSipStack.createSipPhone("127.0.0.1", SipStack.PROTOCOL_UDP, 5080, bobContact);

		aliceSipStack = tool2.initializeSipStack(SipStack.PROTOCOL_UDP, "127.0.0.1", "5091", "127.0.0.1:5080");
		alicePhone = aliceSipStack.createSipPhone("127.0.0.1", SipStack.PROTOCOL_UDP, 5080, aliceContact);

		carolSipStack = tool3.initializeSipStack(SipStack.PROTOCOL_UDP, "127.0.0.1", "5092", "127.0.0.1:5080");
		carolPhone = carolSipStack.createSipPhone("127.0.0.1", SipStack.PROTOCOL_UDP, 5080, carolContact);

		daveSipStack = tool4.initializeSipStack(SipStack.PROTOCOL_UDP, "127.0.0.1", "5093", "127.0.0.1:5080");
		davePhone = daveSipStack.createSipPhone("127.0.0.1", SipStack.PROTOCOL_UDP, 5080, daveContact);
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

		if (carolSipStack != null) {
			carolSipStack.dispose();
		}
		if (carolPhone != null) {
			carolPhone.dispose();
		}


		if (daveSipStack != null) {
			daveSipStack.dispose();
		}
		if (davePhone != null) {
			davePhone.dispose();
		}
		Thread.sleep(3000);
		wireMockRule.resetRequests();
		Thread.sleep(2000);
	}


	private String sequentialDialingRcml = "<Response><Dial timeout=\"3\"><Uri>"+aliceContact+"</Uri></Dial><Dial timeout=\"3\"><Uri>" + carolContact + "</Uri></Dial><Dial timeout=\"3\"><Uri>" + daveContact + "</Uri></Dial></Response>";
	@Test
	public void testSequentialDialing() throws ParseException, InterruptedException {
		stubFor(get(urlPathEqualTo("/1111"))
				.willReturn(aResponse()
						.withStatus(200)
						.withHeader("Content-Type", "text/xml")
						.withBody(sequentialDialingRcml)));

		final SipCall aliceCall = alicePhone.createSipCall();
		aliceCall.listenForIncomingCall();

		final SipCall bobCall = bobPhone.createSipCall();

		final SipCall carolCall = carolPhone.createSipCall();
		carolCall.listenForIncomingCall();

		final SipCall daveCall = davePhone.createSipCall();
		daveCall.listenForIncomingCall();

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

		assertTrue(aliceCall.waitForIncomingCall(5000));
		assertTrue(aliceCall.sendIncomingCallResponse(100, "Trying-Alice", 600));
		assertTrue(aliceCall.sendIncomingCallResponse(180, "Ringing-Alice", 600));
		aliceCall.listenForCancel();
		SipTransaction aliceCancelTransaction = aliceCall.waitForCancel(5000);
		assertNotNull(aliceCancelTransaction);
		aliceCall.respondToCancel(aliceCancelTransaction, 200, "OK-2-Cancel-Alice", 3600);

		assertTrue(carolCall.waitForIncomingCall(5000));
		assertTrue(carolCall.sendIncomingCallResponse(100, "Trying-Carol", 600));
		assertTrue(carolCall.sendIncomingCallResponse(180, "Ringing-Carol", 600));
		carolCall.listenForCancel();
		SipTransaction carolCancelTransaction = carolCall.waitForCancel(5000);
		assertNotNull(carolCancelTransaction);
		carolCall.respondToCancel(carolCancelTransaction, 200, "OK-2-Cancel-Carol", 3600);

		assertTrue(daveCall.waitForIncomingCall(5000));
		assertTrue(daveCall.sendIncomingCallResponse(100, "Trying-Dave", 600));
		assertTrue(daveCall.sendIncomingCallResponse(180, "Ringing-Dave", 600));
		daveCall.listenForCancel();
		SipTransaction daveCancelTransaction = daveCall.waitForCancel(5000);
		assertNotNull(daveCancelTransaction);
		daveCall.respondToCancel(daveCancelTransaction, 200, "OK-2-Cancel-Dave", 3600);

		Thread.sleep(1000);

		bobCall.disconnect();

		Thread.sleep(1000);
		JsonObject metrics = MonitoringServiceTool.getInstance().getMetrics(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
		assertNotNull(metrics);
		int liveCalls = metrics.getAsJsonObject("Metrics").get("LiveCalls").getAsInt();
		int liveCallsArraySize = metrics.getAsJsonArray("LiveCallDetails").size();
		assertEquals(0 ,liveCalls);
		assertEquals(0,liveCallsArraySize);

		Map<String, Integer> mgcpResources = MonitoringServiceTool.getInstance().getMgcpResources(metrics);
		int mgcpEndpoints = mgcpResources.get("MgcpEndpoints");
		int mgcpConnections = mgcpResources.get("MgcpConnections");

		assertEquals(0, mgcpEndpoints);
		assertEquals(0, mgcpConnections);
	}

	@Test
	public void testSequentialDialingAliceAnswerBobDisconnects() throws ParseException, InterruptedException {
		stubFor(get(urlPathEqualTo("/1111"))
				.willReturn(aResponse()
						.withStatus(200)
						.withHeader("Content-Type", "text/xml")
						.withBody(sequentialDialingRcml)));

		final SipCall aliceCall = alicePhone.createSipCall();
		aliceCall.listenForIncomingCall();

		final SipCall bobCall = bobPhone.createSipCall();

		final SipCall carolCall = carolPhone.createSipCall();
		carolCall.listenForIncomingCall();

		final SipCall daveCall = davePhone.createSipCall();
		daveCall.listenForIncomingCall();

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

		assertTrue(aliceCall.waitForIncomingCall(5000));
		assertTrue(aliceCall.sendIncomingCallResponse(100, "Trying-Alice", 600));
		assertTrue(aliceCall.sendIncomingCallResponse(180, "Ringing-Alice", 600));
		String receivedBody = new String(aliceCall.getLastReceivedRequest().getRawContent());
		assertTrue(aliceCall.sendIncomingCallResponse(Response.OK, "Alice-OK", 3600, receivedBody, "application", "sdp",
				null, null));
		assertTrue(aliceCall.waitForAck(5000));

		Thread.sleep(3000);
		aliceCall.listenForDisconnect();

		assertTrue(bobCall.disconnect());

		assertTrue(aliceCall.waitForDisconnect(5000));
		assertTrue(aliceCall.respondToDisconnect());

		assertTrue(!carolCall.waitForIncomingCall(5000));

		assertTrue(!daveCall.waitForIncomingCall(5000));

		Thread.sleep(1000);

		bobCall.disconnect();

		Thread.sleep(1000);
		JsonObject metrics = MonitoringServiceTool.getInstance().getMetrics(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
		assertNotNull(metrics);
		int liveCalls = metrics.getAsJsonObject("Metrics").get("LiveCalls").getAsInt();
		int liveCallsArraySize = metrics.getAsJsonArray("LiveCallDetails").size();
		assertEquals(0 ,liveCalls);
		assertEquals(0,liveCallsArraySize);

		Map<String, Integer> mgcpResources = MonitoringServiceTool.getInstance().getMgcpResources(metrics);
		int mgcpEndpoints = mgcpResources.get("MgcpEndpoints");
		int mgcpConnections = mgcpResources.get("MgcpConnections");

		assertEquals(0, mgcpEndpoints);
		assertEquals(0, mgcpConnections);
	}

	@Test
	public void testSequentialDialingCarolAnswerBobDisconnects() throws ParseException, InterruptedException {
		stubFor(get(urlPathEqualTo("/1111"))
				.willReturn(aResponse()
						.withStatus(200)
						.withHeader("Content-Type", "text/xml")
						.withBody(sequentialDialingRcml)));

		final SipCall aliceCall = alicePhone.createSipCall();
		aliceCall.listenForIncomingCall();

		final SipCall bobCall = bobPhone.createSipCall();

		final SipCall carolCall = carolPhone.createSipCall();
		carolCall.listenForIncomingCall();

		final SipCall daveCall = davePhone.createSipCall();
		daveCall.listenForIncomingCall();

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

		assertTrue(aliceCall.waitForIncomingCall(5000));
		assertTrue(aliceCall.sendIncomingCallResponse(100, "Trying-Alice", 600));
		assertTrue(aliceCall.sendIncomingCallResponse(180, "Ringing-Alice", 600));
		aliceCall.listenForCancel();
		SipTransaction aliceCancelTransaction = aliceCall.waitForCancel(5000);
		assertNotNull(aliceCancelTransaction);
		aliceCall.respondToCancel(aliceCancelTransaction, 200, "OK-2-Cancel-Alice", 3600);

		assertTrue(carolCall.waitForIncomingCall(5000));
		assertTrue(carolCall.sendIncomingCallResponse(100, "Trying-Carol", 600));
		assertTrue(carolCall.sendIncomingCallResponse(180, "Ringing-Carol", 600));
		String receivedBody = new String(carolCall.getLastReceivedRequest().getRawContent());
		assertTrue(carolCall.sendIncomingCallResponse(Response.OK, "Carol-OK", 3600, receivedBody, "application", "sdp",
				null, null));
		assertTrue(carolCall.waitForAck(5000));

		Thread.sleep(3000);
		carolCall.listenForDisconnect();

		assertTrue(bobCall.disconnect());

		assertTrue(carolCall.waitForDisconnect(5000));
		assertTrue(carolCall.respondToDisconnect());

		assertTrue(!daveCall.waitForIncomingCall(5000));

		Thread.sleep(1000);

		bobCall.disconnect();

		Thread.sleep(1000);
		JsonObject metrics = MonitoringServiceTool.getInstance().getMetrics(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
		assertNotNull(metrics);
		int liveCalls = metrics.getAsJsonObject("Metrics").get("LiveCalls").getAsInt();
		int liveCallsArraySize = metrics.getAsJsonArray("LiveCallDetails").size();
		assertEquals(0 ,liveCalls);
		assertEquals(0,liveCallsArraySize);

		Map<String, Integer> mgcpResources = MonitoringServiceTool.getInstance().getMgcpResources(metrics);
		int mgcpEndpoints = mgcpResources.get("MgcpEndpoints");
		int mgcpConnections = mgcpResources.get("MgcpConnections");

		assertEquals(0, mgcpEndpoints);
		assertEquals(0, mgcpConnections);
	}

	@Test
	public void testSequentialDialingAliceAnswerAliceDisconnects() throws ParseException, InterruptedException {
		stubFor(get(urlPathEqualTo("/1111"))
				.willReturn(aResponse()
						.withStatus(200)
						.withHeader("Content-Type", "text/xml")
						.withBody(sequentialDialingRcml)));

		final SipCall aliceCall = alicePhone.createSipCall();
		aliceCall.listenForIncomingCall();

		final SipCall bobCall = bobPhone.createSipCall();

		final SipCall carolCall = carolPhone.createSipCall();
		carolCall.listenForIncomingCall();

		final SipCall daveCall = davePhone.createSipCall();
		daveCall.listenForIncomingCall();

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

		assertTrue(aliceCall.waitForIncomingCall(5000));
		assertTrue(aliceCall.sendIncomingCallResponse(100, "Trying-Alice", 600));
		assertTrue(aliceCall.sendIncomingCallResponse(180, "Ringing-Alice", 600));
		String receivedBody = new String(aliceCall.getLastReceivedRequest().getRawContent());
		assertTrue(aliceCall.sendIncomingCallResponse(Response.OK, "Alice-OK", 3600, receivedBody, "application", "sdp",
				null, null));
		assertTrue(aliceCall.waitForAck(5000));

		Thread.sleep(3000);
		bobCall.listenForDisconnect();

		assertTrue(aliceCall.disconnect());

		assertTrue(bobCall.waitForDisconnect(5000));
		assertTrue(bobCall.respondToDisconnect());

		assertTrue(!carolCall.waitForIncomingCall(5000));

		assertTrue(!daveCall.waitForIncomingCall(5000));

		Thread.sleep(1000);

		bobCall.disconnect();

		Thread.sleep(1000);
		JsonObject metrics = MonitoringServiceTool.getInstance().getMetrics(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
		assertNotNull(metrics);
		int liveCalls = metrics.getAsJsonObject("Metrics").get("LiveCalls").getAsInt();
		int liveCallsArraySize = metrics.getAsJsonArray("LiveCallDetails").size();
		assertEquals(0 ,liveCalls);
		assertEquals(0,liveCallsArraySize);

		Map<String, Integer> mgcpResources = MonitoringServiceTool.getInstance().getMgcpResources(metrics);
		int mgcpEndpoints = mgcpResources.get("MgcpEndpoints");
		int mgcpConnections = mgcpResources.get("MgcpConnections");

		assertEquals(0, mgcpEndpoints);
		assertEquals(0, mgcpConnections);
	}

	@Test
	public void testSequentialDialingCarolAnswerCarolDisconnects() throws ParseException, InterruptedException {
		stubFor(get(urlPathEqualTo("/1111"))
				.willReturn(aResponse()
						.withStatus(200)
						.withHeader("Content-Type", "text/xml")
						.withBody(sequentialDialingRcml)));

		final SipCall aliceCall = alicePhone.createSipCall();
		aliceCall.listenForIncomingCall();

		final SipCall bobCall = bobPhone.createSipCall();

		final SipCall carolCall = carolPhone.createSipCall();
		carolCall.listenForIncomingCall();

		final SipCall daveCall = davePhone.createSipCall();
		daveCall.listenForIncomingCall();

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

		assertTrue(aliceCall.waitForIncomingCall(5000));
		assertTrue(aliceCall.sendIncomingCallResponse(100, "Trying-Alice", 600));
		assertTrue(aliceCall.sendIncomingCallResponse(180, "Ringing-Alice", 600));
		aliceCall.listenForCancel();
		SipTransaction aliceCancelTransaction = aliceCall.waitForCancel(5000);
		assertNotNull(aliceCancelTransaction);
		aliceCall.respondToCancel(aliceCancelTransaction, 200, "OK-2-Cancel-Alice", 3600);

		assertTrue(carolCall.waitForIncomingCall(5000));
		assertTrue(carolCall.sendIncomingCallResponse(100, "Trying-Carol", 600));
		assertTrue(carolCall.sendIncomingCallResponse(180, "Ringing-Carol", 600));
		String receivedBody = new String(carolCall.getLastReceivedRequest().getRawContent());
		assertTrue(carolCall.sendIncomingCallResponse(Response.OK, "Carol-OK", 3600, receivedBody, "application", "sdp",
				null, null));
		assertTrue(carolCall.waitForAck(5000));

		Thread.sleep(3000);
		bobCall.listenForDisconnect();

		assertTrue(carolCall.disconnect());

		assertTrue(bobCall.waitForDisconnect(5000));
		assertTrue(bobCall.respondToDisconnect());

		assertTrue(!daveCall.waitForIncomingCall(5000));

		Thread.sleep(1000);

		bobCall.disconnect();

		Thread.sleep(1000);
		JsonObject metrics = MonitoringServiceTool.getInstance().getMetrics(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
		assertNotNull(metrics);
		int liveCalls = metrics.getAsJsonObject("Metrics").get("LiveCalls").getAsInt();
		int liveCallsArraySize = metrics.getAsJsonArray("LiveCallDetails").size();
		assertEquals(0 ,liveCalls);
		assertEquals(0,liveCallsArraySize);

		Map<String, Integer> mgcpResources = MonitoringServiceTool.getInstance().getMgcpResources(metrics);
		int mgcpEndpoints = mgcpResources.get("MgcpEndpoints");
		int mgcpConnections = mgcpResources.get("MgcpConnections");

		assertEquals(0, mgcpEndpoints);
		assertEquals(0, mgcpConnections);
	}

	private String sequentialDialingRcml1 = "<Response><Dial action=\"http://127.0.0.1:8090/action1\" timeout=\"3\"><Uri>" + aliceContact + "</Uri></Dial></Response>";
	private String action1 = "<Response><Dial action=\"http://127.0.0.1:8090/action2\" timeout=\"3\"><Uri>" + carolContact + "</Uri></Dial></Response>";
	private String action2 = "<Response><Dial timeout=\"3\"><Uri>" + daveContact + "</Uri></Dial></Response>";
	@Test
	@Category(FeatureAltTests.class)
	public void testSequentialDialingWithDialAction() throws ParseException, InterruptedException {
		stubFor(get(urlPathEqualTo("/1111"))
				.willReturn(aResponse()
						.withStatus(200)
						.withHeader("Content-Type", "text/xml")
						.withBody(sequentialDialingRcml1)));

		stubFor(post(urlPathEqualTo("/action1"))
				.willReturn(aResponse()
						.withStatus(200)
						.withHeader("Content-Type", "text/xml")
						.withBody(action1)));

		stubFor(post(urlPathEqualTo("/action2"))
				.willReturn(aResponse()
						.withStatus(200)
						.withHeader("Content-Type", "text/xml")
						.withBody(action2)));

		final SipCall aliceCall = alicePhone.createSipCall();
		aliceCall.listenForIncomingCall();

		final SipCall bobCall = bobPhone.createSipCall();

		final SipCall carolCall = carolPhone.createSipCall();
		carolCall.listenForIncomingCall();

		final SipCall daveCall = davePhone.createSipCall();
		daveCall.listenForIncomingCall();

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

		assertTrue(aliceCall.waitForIncomingCall(5000));
		assertTrue(aliceCall.sendIncomingCallResponse(100, "Trying-Alice", 600));
		assertTrue(aliceCall.sendIncomingCallResponse(180, "Ringing-Alice", 600));
		aliceCall.listenForCancel();
		SipTransaction aliceCancelTransaction = aliceCall.waitForCancel(5000);
		assertNotNull(aliceCancelTransaction);
		aliceCall.respondToCancel(aliceCancelTransaction, 200, "OK-2-Cancel-Alice", 3600);

		assertTrue(carolCall.waitForIncomingCall(5000));
		assertTrue(carolCall.sendIncomingCallResponse(100, "Trying-Carol", 600));
		assertTrue(carolCall.sendIncomingCallResponse(180, "Ringing-Carol", 600));
		carolCall.listenForCancel();
		SipTransaction carolCancelTransaction = carolCall.waitForCancel(5000);
		assertNotNull(carolCancelTransaction);
		carolCall.respondToCancel(carolCancelTransaction, 200, "OK-2-Cancel-Carol", 3600);

		assertTrue(daveCall.waitForIncomingCall(5000));
		assertTrue(daveCall.sendIncomingCallResponse(100, "Trying-Dave", 600));
		assertTrue(daveCall.sendIncomingCallResponse(180, "Ringing-Dave", 600));
		daveCall.listenForCancel();
		SipTransaction daveCancelTransaction = daveCall.waitForCancel(5000);
		assertNotNull(daveCancelTransaction);
		daveCall.respondToCancel(daveCancelTransaction, 200, "OK-2-Cancel-Dave", 3600);

		Thread.sleep(1000);

		bobCall.disconnect();

		Thread.sleep(1000);
		JsonObject metrics = MonitoringServiceTool.getInstance().getMetrics(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
		assertNotNull(metrics);
		int liveCalls = metrics.getAsJsonObject("Metrics").get("LiveCalls").getAsInt();
		int liveCallsArraySize = metrics.getAsJsonArray("LiveCallDetails").size();
		assertEquals(0 ,liveCalls);
		assertEquals(0,liveCallsArraySize);

		Map<String, Integer> mgcpResources = MonitoringServiceTool.getInstance().getMgcpResources(metrics);
		int mgcpEndpoints = mgcpResources.get("MgcpEndpoints");
		int mgcpConnections = mgcpResources.get("MgcpConnections");

		assertEquals(0, mgcpEndpoints);
		assertEquals(0, mgcpConnections);
	}

	@Deployment(name = "SequentialDialingTest", managed = true, testable = false)
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
		archive.addAsWebInfResource("restcomm.script_dialTest_new", "data/hsql/restcomm.script");
		logger.info("Packaged Test App");
		return archive;
	}
}
