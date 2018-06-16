/*
 * TeleStax, Open Source Cloud Communications
 * Copyright 2011-2014, Telestax Inc and individual contributors
 * by the @authors tag.
 *
 * This program is free software: you can redistribute it and/or modify
 * under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation; either version 3 of
 * the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 *
 */

package org.restcomm.connect.testsuite.sms.push;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
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
import org.restcomm.connect.commons.annotations.ParallelClassTests;
import org.restcomm.connect.commons.annotations.UnstableTests;
import org.restcomm.connect.commons.annotations.WithInSecsTests;
import org.restcomm.connect.testsuite.NetworkPortAssigner;
import org.restcomm.connect.testsuite.WebArchiveUtil;
import org.restcomm.connect.testsuite.http.CreateClientsTool;
import org.restcomm.connect.testsuite.sms.SmsEndpointTool;

import com.github.tomakehurst.wiremock.http.RequestListener;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.google.gson.JsonObject;

/**
 * @author oleg.agafonov@telestax.com (Oleg Agafonov)
 */
@RunWith(Arquillian.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@Category(value={WithInSecsTests.class, ParallelClassTests.class})
public class SmsPushNotificationServerTest {

    private final static Logger logger = Logger.getLogger(SmsPushNotificationServerTest.class.getName());

    private static final String CLIENT_PASSWORD = "qwerty1234RT";

    private static int mediaPort = NetworkPortAssigner.retrieveNextPortByFile();

    private static int mockPort = NetworkPortAssigner.retrieveNextPortByFile();

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(mockPort);

    @ArquillianResource
    URL deploymentUrl;

    private static SipStackTool tool1;
    private static SipStackTool tool2;

    private SipStack bobSipStack;
    private SipPhone bobPhone;
    private static String bobPort = String.valueOf(NetworkPortAssigner.retrieveNextPortByFile());
    private String bobContact = "sip:bob@127.0.0.1:" + bobPort;

    private SipStack aliceSipStack;
    private SipPhone alicePhone;
    private static String alicePort = String.valueOf(NetworkPortAssigner.retrieveNextPortByFile());
    private String aliceContact = "sip:alice@127.0.0.1:" + alicePort;

    private static int restcommPort = 5080;
    private static int restcommHTTPPort = 8080;
    private static String restcommContact = "127.0.0.1:" + restcommPort;

    private String adminAccountSid = "ACae6e420f425248d6a26948c17a9e2acf";
    private String adminAuthToken = "77f8c12cc7b8f8423e5c38b035249166";

    @BeforeClass
    public static void beforeClass() throws Exception {
        tool1 = new SipStackTool("SmsPushNotificationServerTest1");
        tool2 = new SipStackTool("SmsPushNotificationServerTest2");
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
        bobSipStack = tool1.initializeSipStack(SipStack.PROTOCOL_UDP, "127.0.0.1", bobPort, restcommContact);
        bobPhone = bobSipStack.createSipPhone("127.0.0.1", SipStack.PROTOCOL_UDP, restcommPort, bobContact);

        aliceSipStack = tool2.initializeSipStack(SipStack.PROTOCOL_UDP, "127.0.0.1", alicePort, restcommContact);
        alicePhone = aliceSipStack.createSipPhone("127.0.0.1", SipStack.PROTOCOL_UDP, restcommPort, aliceContact);
    }

    @After
    @SuppressWarnings("Duplicates")
    public void after() throws Exception {
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

        Thread.sleep(1000);
        wireMockRule.resetRequests();
        Thread.sleep(4000);
    }

    @Test
    @Category(UnstableTests.class)
    public void testB2BUAMessage() throws ParseException, InterruptedException, IOException {
        stubFor(post(urlPathEqualTo("/api/notifications"))
                .withHeader("Content-Type", matching("application/json;.*"))
                .willReturn(aResponse()
                        .withStatus(200)));

        final SipURI uri = aliceSipStack.getAddressFactory().createSipURI(null, restcommContact);

        wireMockRule.addMockServiceRequestListener(new RequestListener() {
            @Override
            public void requestReceived(com.github.tomakehurst.wiremock.http.Request request, com.github.tomakehurst.wiremock.http.Response response) {
                if (request.getAbsoluteUrl().contains("/api/notifications") && response.getStatus() == 200) {
                    assertTrue(alicePhone.register(uri, "alice", "1234", aliceContact, 3600, 3600));
                }
            }
        });

        SipCall aliceCall = alicePhone.createSipCall();
        aliceCall.listenForMessage();

        CreateClientsTool.getInstance().createClient(deploymentUrl.toString(), "bob", CLIENT_PASSWORD, null);

        Credential c = new Credential("127.0.0.1", "bob", CLIENT_PASSWORD);
        bobPhone.addUpdateCredential(c);
        assertTrue(bobPhone.register(uri, "bob", CLIENT_PASSWORD, bobContact, 3600, 3600));

        SipCall bobCall = bobPhone.createSipCall();
        bobCall.initiateOutgoingMessage(bobContact, aliceContact, null, null, null, "Hello, Alice!");
        assertTrue(bobCall.waitForAuthorisation(5000));
        assertTrue(bobCall.waitOutgoingMessageResponse(5000));
        assertTrue(aliceCall.waitForMessage(5000));
        assertTrue(aliceCall.sendMessageResponse(Response.ACCEPTED, "Accepted", 3600, null));

        int response;
        do {
            assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
            response = bobCall.getLastReceivedResponse().getStatusCode();
        } while (response == Response.TRYING);
        assertTrue(response == Response.ACCEPTED);

        verify(postRequestedFor(urlEqualTo("/api/notifications")));
    }

    @Test
    public void testSmsEndpointMessage() throws ParseException {

        stubFor(post(urlPathEqualTo("/api/notifications"))
                .withHeader("Content-Type", matching("application/json;.*"))
                .willReturn(aResponse()
                        .withStatus(200)));

        final SipURI uri = aliceSipStack.getAddressFactory().createSipURI(null, restcommContact);

        wireMockRule.addMockServiceRequestListener(new RequestListener() {
            @Override
            public void requestReceived(com.github.tomakehurst.wiremock.http.Request request, com.github.tomakehurst.wiremock.http.Response response) {
                if (request.getAbsoluteUrl().contains("/api/notifications") && response.getStatus() == 200) {
                    assertTrue(alicePhone.register(uri, "alice", "1234", aliceContact, 3600, 3600));
                }
            }
        });

        SipCall aliceCall = alicePhone.createSipCall();
        aliceCall.listenForMessage();

        String from = "+15126002188";
        String to = "alice";
        String body = "Hello, Alice!";

        JsonObject callResult = SmsEndpointTool.getInstance().createSms(deploymentUrl.toString(), adminAccountSid,
                adminAuthToken, from, to, body, null);
        assertNotNull(callResult);

        assertTrue(aliceCall.waitForMessage(10000));
        Request messageRequest = aliceCall.getLastReceivedMessageRequest();
        assertTrue(aliceCall.sendMessageResponse(202, "Accepted", 3600));
        String messageReceived = new String(messageRequest.getRawContent());
        assertTrue(messageReceived.equals(body));

        verify(postRequestedFor(urlEqualTo("/api/notifications")));
    }

    @SuppressWarnings("Duplicates")
    @Deployment(name = "SmsPushNotificationServerTest", testable = false)
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

        return WebArchiveUtil.createWebArchiveNoGw("restcomm_for_SMSEndpointTest.xml", "restcomm.script_pushNotificationServer", replacements);
    }
}
