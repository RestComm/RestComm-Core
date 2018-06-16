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

package org.restcomm.connect.testsuite.telephony.push;

import com.github.tomakehurst.wiremock.http.RequestListener;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.apache.log4j.Logger;
import org.cafesip.sipunit.Credential;
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
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.FixMethodOrder;
import org.junit.runners.MethodSorters;
import org.junit.runner.RunWith;
import org.restcomm.connect.commons.Version;
import org.restcomm.connect.testsuite.http.CreateClientsTool;

import javax.sip.address.SipURI;
import javax.sip.message.Response;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.ParseException;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.cafesip.sipunit.SipAssert.assertLastOperationSuccess;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.experimental.categories.Category;
import org.restcomm.connect.commons.annotations.UnstableTests;

/**
 * @author oleg.agafonov@telestax.com (Oleg Agafonov)
 */
@RunWith(Arquillian.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class CallPushNotificationServerTest {

    private final static Logger logger = Logger.getLogger(CallPushNotificationServerTest.class.getName());

    private static final String BODY = new String(new byte[]{
            118, 61, 48, 13, 10, 111, 61, 117, 115, 101, 114, 49, 32, 53, 51, 54, 53,
            53, 55, 54, 53, 32, 50, 51, 53, 51, 54, 56, 55, 54, 51, 55, 32, 73, 78, 32, 73, 80, 52, 32, 49, 50, 55, 46, 48, 46,
            48, 46, 49, 13, 10, 115, 61, 45, 13, 10, 99, 61, 73, 78, 32, 73, 80, 52, 32, 49, 50, 55, 46, 48, 46, 48, 46, 49,
            13, 10, 116, 61, 48, 32, 48, 13, 10, 109, 61, 97, 117, 100, 105, 111, 32, 54, 48, 48, 48, 32, 82, 84, 80, 47, 65,
            86, 80, 32, 48, 13, 10, 97, 61, 114, 116, 112, 109, 97, 112, 58, 48, 32, 80, 67, 77, 85, 47, 56, 48, 48, 48, 13, 10
    });

    private static final String CLIENT_PASSWORD = "qwerty1234RT";


    @Rule
    public WireMockRule wireMockRule = new WireMockRule(8090);

    @ArquillianResource
    URL deploymentUrl;

    private static SipStackTool tool1;
    private static SipStackTool tool2;

    private SipStack bobSipStack;
    private SipPhone bobPhone;
    private String bobContact = "sip:bob@127.0.0.1:5090";

    private SipStack aliceSipStack;
    private SipPhone alicePhone;
    private String aliceContact = "sip:alice@127.0.0.1:5091";

    @BeforeClass
    public static void beforeClass() throws Exception {
        tool1 = new SipStackTool("CallPushNotificationServerTest1");
        tool2 = new SipStackTool("CallPushNotificationServerTest2");
    }

    @Before
    public void before() throws Exception {
        bobSipStack = tool1.initializeSipStack(SipStack.PROTOCOL_UDP, "127.0.0.1", "5090", "127.0.0.1:5080");
        bobPhone = bobSipStack.createSipPhone("127.0.0.1", SipStack.PROTOCOL_UDP, 5080, bobContact);

        aliceSipStack = tool2.initializeSipStack(SipStack.PROTOCOL_UDP, "127.0.0.1", "5091", "127.0.0.1:5080");
        alicePhone = aliceSipStack.createSipPhone("127.0.0.1", SipStack.PROTOCOL_UDP, 5080, aliceContact);
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

        if (aliceSipStack != null) {
            aliceSipStack.dispose();
        }
        if (alicePhone != null) {
            alicePhone.dispose();
        }

        Thread.sleep(1000);
        wireMockRule.resetRequests();
        Thread.sleep(4000);
    }

    @AfterClass
    public static void afterClass() {
        System.gc();
        System.out.println("System.gc() run");
    }

    private String dialAliceRcml = "<Response><Dial><Client>alice</Client></Dial></Response>";

    @Test
    @Category(UnstableTests.class)
    public void testRcmlCall() throws ParseException, InterruptedException, MalformedURLException {

        stubFor(get(urlPathEqualTo("/1111"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(dialAliceRcml)));

        stubFor(post(urlPathEqualTo("/api/notifications"))
                .withHeader("Content-Type", matching("application/json;.*"))
                .willReturn(aResponse()
                        .withStatus(200)));

        final SipURI uri = aliceSipStack.getAddressFactory().createSipURI(null, "127.0.0.1:5080");

        wireMockRule.addMockServiceRequestListener(new RequestListener() {
            @Override
            public void requestReceived(com.github.tomakehurst.wiremock.http.Request request, com.github.tomakehurst.wiremock.http.Response response) {
                if (request.getAbsoluteUrl().contains("/api/notifications") && response.getStatus() == 200) {
                    assertTrue(alicePhone.register(uri, "alice", "1234", aliceContact, 3600, 3600));
                }
            }
        });

        SipCall aliceCall = alicePhone.createSipCall();
        aliceCall.listenForIncomingCall();

        final SipCall bobCall = bobPhone.createSipCall();
        bobCall.initiateOutgoingCall(bobContact, "sip:1111@127.0.0.1:5080", null, BODY, "application", "sdp", null, null);
        assertLastOperationSuccess(bobCall);
        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        final int response = bobCall.getLastReceivedResponse().getStatusCode();
        assertTrue(response == Response.TRYING || response == Response.RINGING);
        logger.info("Last response: " + response);

        if (response == Response.TRYING) {
            assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
            assertEquals(Response.RINGING, bobCall.getLastReceivedResponse().getStatusCode());
            logger.info("Last response: " + bobCall.getLastReceivedResponse().getStatusCode());
        }

        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        assertEquals(Response.OK, bobCall.getLastReceivedResponse().getStatusCode());
        assertTrue(bobCall.sendInviteOkAck());

        assertTrue(aliceCall.waitForIncomingCall(5000));
        assertTrue(aliceCall.sendIncomingCallResponse(Response.TRYING, "Alice-Trying", 3600));
        assertTrue(aliceCall.sendIncomingCallResponse(Response.RINGING, "Alice-Ringing", 3600));
        String receivedBody = new String(aliceCall.getLastReceivedRequest().getRawContent());
        assertTrue(aliceCall.sendIncomingCallResponse(Response.OK, "Alice-OK", 3600, receivedBody, "application", "sdp",
                null, null));
        assertTrue(aliceCall.waitForAck(5000));

        Thread.sleep(4000);
        bobCall.listenForDisconnect();

        assertTrue(aliceCall.disconnect());
        Thread.sleep(500);
        assertTrue(bobCall.waitForDisconnect(5000));
        assertTrue(bobCall.respondToDisconnect());

        verify(postRequestedFor(urlEqualTo("/api/notifications")));
    }

    @Test
    public void testB2BUACall() throws ParseException, InterruptedException, IOException {
        stubFor(post(urlPathEqualTo("/api/notifications"))
                .withHeader("Content-Type", matching("application/json;.*"))
                .willReturn(aResponse()
                        .withStatus(200)));

        final SipURI uri = aliceSipStack.getAddressFactory().createSipURI(null, "127.0.0.1:5080");

        wireMockRule.addMockServiceRequestListener(new RequestListener() {
            @Override
            public void requestReceived(com.github.tomakehurst.wiremock.http.Request request, com.github.tomakehurst.wiremock.http.Response response) {
                if (request.getAbsoluteUrl().contains("/api/notifications") && response.getStatus() == 200) {
                    assertTrue(alicePhone.register(uri, "alice", "1234", aliceContact, 3600, 3600));
                }
            }
        });

        SipCall aliceCall = alicePhone.createSipCall();
        aliceCall.listenForIncomingCall();

        CreateClientsTool.getInstance().createClient(deploymentUrl.toString(), "bob", CLIENT_PASSWORD, null);

        Credential c = new Credential("127.0.0.1", "bob", CLIENT_PASSWORD);
        bobPhone.addUpdateCredential(c);
        assertTrue(bobPhone.register(uri, "bob", CLIENT_PASSWORD, bobContact, 3600, 3600));

        final SipCall bobCall = bobPhone.createSipCall();
        bobCall.initiateOutgoingCall(bobContact, aliceContact, null, BODY, "application", "sdp", null, null);
        assertLastOperationSuccess(bobCall);
        assertTrue(bobCall.waitForAuthorisation(3000));

        assertTrue(aliceCall.waitForIncomingCall(5000));
        assertTrue(aliceCall.sendIncomingCallResponse(Response.TRYING, "Alice-Trying", 3600));
        assertTrue(aliceCall.sendIncomingCallResponse(Response.RINGING, "Alice-Ringing", 3600));
        String receivedBody = new String(aliceCall.getLastReceivedRequest().getRawContent());
        assertTrue(aliceCall.sendIncomingCallResponse(Response.OK, "Alice-OK", 3600, receivedBody, "application", "sdp",
                null, null));

        int response;
        do {
            assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
            response = bobCall.getLastReceivedResponse().getStatusCode();
            assertTrue(response == Response.TRYING || response == Response.RINGING);
            logger.info("Last response: " + response);
        } while (response == Response.TRYING);
        assertEquals(Response.RINGING, response);
        logger.info("Last response: " + response);

        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        assertEquals(Response.OK, bobCall.getLastReceivedResponse().getStatusCode());
        assertTrue(bobCall.sendInviteOkAck());
        assertTrue(aliceCall.waitForAck(5000));

        Thread.sleep(4000);
        bobCall.listenForDisconnect();

        assertTrue(aliceCall.disconnect());
        Thread.sleep(500);
        assertTrue(bobCall.waitForDisconnect(5000));
        assertTrue(bobCall.respondToDisconnect());

        verify(postRequestedFor(urlEqualTo("/api/notifications")));
    }

    @SuppressWarnings("Duplicates")
    @Deployment(name = "CallPushNotificationServerTest", testable = false)
    public static WebArchive createWebArchiveNoGw() {
        logger.info("Packaging Test App");
        WebArchive archive = ShrinkWrap.create(WebArchive.class, "restcomm.war");
        final WebArchive restcommArchive = Maven.resolver()
                .resolve("org.restcomm:restcomm-connect.application:war:" + Version.getVersion()).withoutTransitivity()
                .asSingle(WebArchive.class);
        archive = archive.merge(restcommArchive);
        archive.delete("/WEB-INF/sip.xml");
archive.delete("/WEB-INF/web.xml");
        archive.delete("/WEB-INF/conf/restcomm.xml");
        archive.delete("/WEB-INF/data/hsql/restcomm.script");
        archive.delete("/WEB-INF/classes/application.conf");
        archive.addAsWebInfResource("sip.xml");
        archive.addAsWebInfResource("web.xml");
        archive.addAsWebInfResource("restcomm_pushNotificationServer.xml", "conf/restcomm.xml");
        archive.addAsWebInfResource("restcomm.script_pushNotificationServer", "data/hsql/restcomm.script");
        archive.addAsWebInfResource("akka_application.conf", "classes/application.conf");
        archive.addAsWebResource("dial-client-entry_wActionUrl.xml");
        logger.info("Packaged Test App");
        return archive;
    }
}
