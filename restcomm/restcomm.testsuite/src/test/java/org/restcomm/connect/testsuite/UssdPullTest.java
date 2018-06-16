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
package org.restcomm.connect.testsuite;

import static org.cafesip.sipunit.SipAssert.assertLastOperationSuccess;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.sip.DialogState;
import javax.sip.RequestEvent;
import javax.sip.SipException;
import javax.sip.address.Address;
import javax.sip.header.ContentTypeHeader;
import javax.sip.message.Request;
import javax.sip.message.Response;

import org.apache.log4j.Logger;
import org.cafesip.sipunit.SipCall;
import org.cafesip.sipunit.SipPhone;
import org.cafesip.sipunit.SipRequest;
import org.cafesip.sipunit.SipStack;
import org.cafesip.sipunit.SipTransaction;
import org.jboss.arquillian.container.mss.extension.SipStackTool;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.FixMethodOrder;
import org.junit.runners.MethodSorters;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.restcomm.connect.commons.Version;
import org.restcomm.connect.commons.annotations.ParallelClassTests;
import org.restcomm.connect.commons.annotations.UnstableTests;
import org.restcomm.connect.commons.annotations.WithInMinsTests;

/**
 * @author <a href="mailto:gvagenas@gmail.com">gvagenas</a>
 * @modified <a href="mailto:fernando.mendioroz@telestax.com"> Fernando Mendioroz </a>
 *
 */
@RunWith(Arquillian.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@Category(value={WithInMinsTests.class, ParallelClassTests.class})
public class UssdPullTest {

    private final static Logger logger = Logger.getLogger(UssdPullTest.class.getName());
    private static final String version = Version.getVersion();

    private static int mediaPort = NetworkPortAssigner.retrieveNextPortByFile();

    private static SipStackTool tool1;

    private SipStack bobSipStack;
    private SipPhone bobPhone;
    private static String bobPort = String.valueOf(NetworkPortAssigner.retrieveNextPortByFile());
    private String bobContact = "sip:bob@127.0.0.1:" + bobPort;

    private static int restcommPort = 5080;
    private static int restcommHTTPPort = 8080;
    private static String restcommContact = "127.0.0.1:" + restcommPort;
    private static String ussdPullDid = "sip:5544@" + restcommContact;
    private static String ussdPullShortCodeDialDid = "sip:*777#@" + restcommContact;
    private static String ussdPullFastDialDid = "sip:*777*3#@" + restcommContact;
    private static String ussdPullWithCollectDID = "sip:5555@" + restcommContact;
    private static String ussdPullMessageLengthExceeds = "sip:5566@" + restcommContact;
    private static String ussdPullDidNoHttpMethod = "sip:5577@" + restcommContact;
    private static String ussdPullDidClosedAccount = "sip:5578@" + restcommContact;
    private static String ussdPullDidSuspendedAccount = "sip:5579@" + restcommContact;

    @BeforeClass
    public static void beforeClass() throws Exception {
        tool1 = new SipStackTool("UssdPullTest");
    }

    public static void reconfigurePorts() {
        if (System.getProperty("arquillian_sip_port") != null) {
            restcommPort = Integer.valueOf(System.getProperty("arquillian_sip_port"));
            restcommContact = "127.0.0.1:" + restcommPort;
            ussdPullDid = "sip:5544@" + restcommContact;
            ussdPullShortCodeDialDid = "sip:*777#@" + restcommContact;
            ussdPullFastDialDid = "sip:*777*3#@" + restcommContact;
            ussdPullWithCollectDID = "sip:5555@" + restcommContact;
            ussdPullMessageLengthExceeds = "sip:5566@" + restcommContact;
            ussdPullDidNoHttpMethod = "sip:5577@" + restcommContact;
        }
        if (System.getProperty("arquillian_http_port") != null) {
            restcommHTTPPort = Integer.valueOf(System.getProperty("arquillian_http_port"));
        }
    }

    @Before
    public void before() throws Exception {
        bobSipStack = tool1.initializeSipStack(SipStack.PROTOCOL_UDP, "127.0.0.1", bobPort, restcommContact);
        bobPhone = bobSipStack.createSipPhone("127.0.0.1", SipStack.PROTOCOL_UDP, restcommPort, bobContact);
    }

    @After
    public void after() throws Exception {
        if (bobPhone != null) {
            bobPhone.dispose();
            bobPhone = null;
        }
        if (bobSipStack != null) {
            bobSipStack.dispose();
            bobSipStack = null;
        }
    }

    @Test
    @Category(UnstableTests.class)
    public void testUssdPull() {
        final SipCall bobCall = bobPhone.createSipCall();
        bobCall.initiateOutgoingCall(bobContact, ussdPullDid, null, UssdPullTestMessages.ussdClientRequestBody, "application", "vnd.3gpp.ussd+xml", null, null);
        assertLastOperationSuccess(bobCall);

        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        int responseBob = bobCall.getLastReceivedResponse().getStatusCode();
        if (responseBob == Response.TRYING) {
            assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
            assertTrue(bobCall.getLastReceivedResponse().getStatusCode() == Response.RINGING);
        } else {
            assertTrue(bobCall.getLastReceivedResponse().getStatusCode() == Response.RINGING);
        }

        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        assertEquals(Response.OK, bobCall.getLastReceivedResponse().getStatusCode());
        assertTrue(bobCall.sendInviteOkAck());

        assertEquals(DialogState._CONFIRMED, bobCall.getDialog().getState().getValue());

        assertTrue(bobCall.listenForDisconnect());

        assertTrue(bobCall.waitForDisconnect(30 * 1000));
        bobCall.respondToDisconnect();
        SipRequest bye = bobCall.getLastReceivedRequest();
        String receivedUssdPayload = new String(bye.getRawContent());
        assertTrue(receivedUssdPayload.equalsIgnoreCase(UssdPullTestMessages.ussdRestcommResponse.trim()));
        bobCall.dispose();
    }

    @Test
    public void testUssdPullNoHttpMethod() {
        final SipCall bobCall = bobPhone.createSipCall();
        bobCall.initiateOutgoingCall(bobContact, ussdPullDidNoHttpMethod, null, UssdPullTestMessages.ussdClientRequestBody, "application", "vnd.3gpp.ussd+xml", null, null);
        assertLastOperationSuccess(bobCall);

        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        int responseBob = bobCall.getLastReceivedResponse().getStatusCode();
        if (responseBob == Response.TRYING) {
            assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
            assertTrue(bobCall.getLastReceivedResponse().getStatusCode() == Response.RINGING);
        } else {
            assertTrue(bobCall.getLastReceivedResponse().getStatusCode() == Response.RINGING);
        }

        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        assertEquals(Response.OK, bobCall.getLastReceivedResponse().getStatusCode());
        assertTrue(bobCall.sendInviteOkAck());

        assertTrue(bobCall.getDialog().getState().getValue() == DialogState._CONFIRMED);

        assertTrue(bobCall.listenForDisconnect());

        assertTrue(bobCall.waitForDisconnect(30 * 1000));
        bobCall.respondToDisconnect();
        SipRequest bye = bobCall.getLastReceivedRequest();
        String receivedUssdPayload = new String(bye.getRawContent());
        assertTrue(receivedUssdPayload.equalsIgnoreCase(UssdPullTestMessages.ussdRestcommResponse.trim()));
        bobCall.dispose();
    }

    @Test //USSD Pull to *777#
    public void testUssdPullShortCodeDial() {
        final SipCall bobCall = bobPhone.createSipCall();
        bobCall.initiateOutgoingCall(bobContact, ussdPullShortCodeDialDid, null, UssdPullTestMessages.ussdClientRequestBody, "application", "vnd.3gpp.ussd+xml", null, null);
        assertLastOperationSuccess(bobCall);

        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        int responseBob = bobCall.getLastReceivedResponse().getStatusCode();
        if (responseBob == Response.TRYING) {
            assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
            assertTrue(bobCall.getLastReceivedResponse().getStatusCode() == Response.RINGING);
        } else {
            assertTrue(bobCall.getLastReceivedResponse().getStatusCode() == Response.RINGING);
        }

        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        assertEquals(Response.OK, bobCall.getLastReceivedResponse().getStatusCode());
        assertTrue(bobCall.sendInviteOkAck());

        assertTrue(bobCall.getDialog().getState().getValue() == DialogState._CONFIRMED);

        assertTrue(bobCall.listenForDisconnect());

        assertTrue(bobCall.waitForDisconnect(30 * 1000));
        bobCall.respondToDisconnect();
        SipRequest bye = bobCall.getLastReceivedRequest();
        String receivedUssdPayload = new String(bye.getRawContent());
        assertTrue(receivedUssdPayload.equalsIgnoreCase(UssdPullTestMessages.ussdRestcommResponse.trim()));
        bobCall.dispose();
    }

    @Test //USSD Pull to *777*...#
    @Category(UnstableTests.class)
    public void testUssdPullFastDial() {
        final SipCall bobCall = bobPhone.createSipCall();
        bobCall.initiateOutgoingCall(bobContact, ussdPullFastDialDid, null, UssdPullTestMessages.ussdClientFastDialRequestBody, "application", "vnd.3gpp.ussd+xml", null, null);
        assertLastOperationSuccess(bobCall);

        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        int responseBob = bobCall.getLastReceivedResponse().getStatusCode();
        if (responseBob == Response.TRYING) {
            assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
            assertTrue(bobCall.getLastReceivedResponse().getStatusCode() == Response.RINGING);
        } else {
            assertTrue(bobCall.getLastReceivedResponse().getStatusCode() == Response.RINGING);
        }

        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        assertEquals(Response.OK, bobCall.getLastReceivedResponse().getStatusCode());
        assertTrue(bobCall.sendInviteOkAck());

        assertTrue(bobCall.getDialog().getState().getValue() == DialogState._CONFIRMED);

        assertTrue(bobCall.listenForDisconnect());

        assertTrue(bobCall.waitForDisconnect(30 * 1000));
        bobCall.respondToDisconnect();
        SipRequest bye = bobCall.getLastReceivedRequest();
        String receivedUssdPayload = new String(bye.getRawContent());
        assertTrue(receivedUssdPayload.equalsIgnoreCase(UssdPullTestMessages.ussdRestcommShortDialResponse.trim()));
        bobCall.dispose();
    }

    @Test
    public void testUssdPullWithCollect() throws InterruptedException, SipException, ParseException {
        final SipCall bobCall = bobPhone.createSipCall();
        bobCall.initiateOutgoingCall(bobContact, ussdPullWithCollectDID, null, UssdPullTestMessages.ussdClientRequestBodyForCollect, "application", "vnd.3gpp.ussd+xml", null, null);
        assertLastOperationSuccess(bobCall);

        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        int responseBob = bobCall.getLastReceivedResponse().getStatusCode();
        assertTrue(responseBob == Response.TRYING || responseBob == Response.RINGING);

        if (responseBob == Response.TRYING) {
            assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
            assertEquals(Response.RINGING, bobCall.getLastReceivedResponse().getStatusCode());
        }

        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        assertEquals(Response.OK, bobCall.getLastReceivedResponse().getStatusCode());
        assertTrue(bobCall.sendInviteOkAck());

        assertTrue(bobCall.getDialog().getState().getValue() == DialogState._CONFIRMED);
        String toTag = bobCall.getDialog().getLocalTag();
        Address bobAddress = bobPhone.getAddress();

        assertTrue(bobPhone.listenRequestMessage());
        RequestEvent requestEvent = bobPhone.waitRequest(30 * 1000);

        assertNotNull(requestEvent);
        assertTrue(requestEvent.getRequest().getMethod().equalsIgnoreCase("INFO"));
        bobPhone.sendReply(requestEvent, 200, "OK", toTag, bobAddress, 0);

        String receivedUssdPayload = new String(requestEvent.getRequest().getRawContent());
        System.out.println("receivedUssdPayload: \n" + receivedUssdPayload);
        System.out.println("UssdPullTestMessages.ussdRestcommResponseWithCollect: \n" + UssdPullTestMessages.ussdRestcommResponseWithCollect);
        assertTrue(receivedUssdPayload.equals(UssdPullTestMessages.ussdRestcommResponseWithCollect.trim()));

        Request infoResponse = requestEvent.getDialog().createRequest(Request.INFO);
        ContentTypeHeader contentTypeHeader = bobCall.getHeaderFactory().createContentTypeHeader("application", "vnd.3gpp.ussd+xml");
        infoResponse.setContent(UssdPullTestMessages.ussdClientResponseBodyToCollect.getBytes(), contentTypeHeader);

        bobPhone.sendRequestWithTransaction(infoResponse, false, requestEvent.getDialog());

        assertTrue(bobCall.listenForDisconnect());
        assertTrue(bobCall.waitForDisconnect(30 * 1000));
        bobCall.respondToDisconnect();
        SipRequest bye = bobCall.getLastReceivedRequest();
        receivedUssdPayload = new String(bye.getRawContent());
        assertTrue(receivedUssdPayload.equalsIgnoreCase(UssdPullTestMessages.ussdRestcommResponse.trim()));
        bobCall.dispose();
    }

    @Test
    @Ignore
    public void testUssdPullWithCollectFromRVD() throws InterruptedException, SipException, ParseException {
        final SipCall bobCall = bobPhone.createSipCall();
        bobCall.initiateOutgoingCall(bobContact, "sip:*888#@" + restcommContact, null, UssdPullTestMessages.ussdClientRequestBodyForCollect, "application", "vnd.3gpp.ussd+xml", null, null);
        assertLastOperationSuccess(bobCall);

        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        int responseBob = bobCall.getLastReceivedResponse().getStatusCode();
        assertTrue(responseBob == Response.TRYING || responseBob == Response.RINGING);

        if (responseBob == Response.TRYING) {
            assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
            assertEquals(Response.RINGING, bobCall.getLastReceivedResponse().getStatusCode());
        }

        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        assertEquals(Response.OK, bobCall.getLastReceivedResponse().getStatusCode());
        assertTrue(bobCall.sendInviteOkAck());

        assertTrue(bobCall.getDialog().getState().getValue() == DialogState._CONFIRMED);
        String toTag = bobCall.getDialog().getLocalTag();
        Address bobAddress = bobPhone.getAddress();

        assertTrue(bobPhone.listenRequestMessage());
        RequestEvent requestEvent = bobPhone.waitRequest(30 * 1000);

        assertNotNull(requestEvent);
        assertTrue(requestEvent.getRequest().getMethod().equalsIgnoreCase("INFO"));
        bobPhone.sendReply(requestEvent, 200, "OK", toTag, bobAddress, 0);

        String receivedUssdPayload = new String(requestEvent.getRequest().getRawContent());
        System.out.println("receivedUssdPayload: \n" + receivedUssdPayload);
        System.out.println("UssdPullTestMessages.ussdRestcommResponseWithCollect: \n" + UssdPullTestMessages.ussdRestcommResponseWithCollect);
        assertTrue(receivedUssdPayload.equals(UssdPullTestMessages.ussdRestcommResponseWithCollect.trim()));

        Request infoResponse = requestEvent.getDialog().createRequest(Request.INFO);
        ContentTypeHeader contentTypeHeader = bobCall.getHeaderFactory().createContentTypeHeader("application", "vnd.3gpp.ussd+xml");
        infoResponse.setContent(UssdPullTestMessages.ussdClientResponseBodyToCollect.getBytes(), contentTypeHeader);

        bobPhone.sendRequestWithTransaction(infoResponse, false, requestEvent.getDialog());

        assertTrue(bobCall.listenForDisconnect());
        assertTrue(bobCall.waitForDisconnect(30 * 1000));
        bobCall.respondToDisconnect();
        SipRequest bye = bobCall.getLastReceivedRequest();
        receivedUssdPayload = new String(bye.getRawContent());
        assertTrue(receivedUssdPayload.equalsIgnoreCase(UssdPullTestMessages.ussdRestcommResponse.trim()));
        bobCall.dispose();
    }

    @Test
    public void testUssdPullWithCollect_DisconnectFromUser() throws InterruptedException, SipException, ParseException {
        final SipCall bobCall = bobPhone.createSipCall();
        bobCall.initiateOutgoingCall(bobContact, ussdPullWithCollectDID, null, UssdPullTestMessages.ussdClientRequestBodyForCollect, "application", "vnd.3gpp.ussd+xml", null, null);
        assertLastOperationSuccess(bobCall);

        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        int responseBob = bobCall.getLastReceivedResponse().getStatusCode();
        assertTrue(responseBob == Response.TRYING || responseBob == Response.RINGING);

        if (responseBob == Response.TRYING) {
            assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
            assertEquals(Response.RINGING, bobCall.getLastReceivedResponse().getStatusCode());
        }

        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        assertEquals(Response.OK, bobCall.getLastReceivedResponse().getStatusCode());
        assertTrue(bobCall.sendInviteOkAck());

        assertTrue(bobCall.getDialog().getState().getValue() == DialogState._CONFIRMED);
        String toTag = bobCall.getDialog().getLocalTag();
        Address bobAddress = bobPhone.getAddress();

        assertTrue(bobPhone.listenRequestMessage());
        RequestEvent requestEvent = bobPhone.waitRequest(30 * 1000);

        assertNotNull(requestEvent);
        assertTrue(requestEvent.getRequest().getMethod().equalsIgnoreCase("INFO"));
        bobPhone.sendReply(requestEvent, 200, "OK", toTag, bobAddress, 0);

        String receivedUssdPayload = new String(requestEvent.getRequest().getRawContent());
        System.out.println("receivedUssdPayload: \n" + receivedUssdPayload);
        System.out.println("UssdPullTestMessages.ussdRestcommResponseWithCollect: \n" + UssdPullTestMessages.ussdRestcommResponseWithCollect);
        assertTrue(receivedUssdPayload.equals(UssdPullTestMessages.ussdRestcommResponseWithCollect.trim()));

        bobCall.disconnect();
        bobCall.waitForAnswer(10000);
        assertTrue(bobCall.getLastReceivedResponse().getStatusCode() == 200);

    }

    @Test
    @Ignore
    public void testUssdPullWithCollect_CancelFromUser() throws InterruptedException, SipException, ParseException {
        final SipCall bobCall = bobPhone.createSipCall();
        bobCall.initiateOutgoingCall(bobContact, ussdPullWithCollectDID, null, UssdPullTestMessages.ussdClientRequestBodyForCollect, "application", "vnd.3gpp.ussd+xml", null, null);
        assertLastOperationSuccess(bobCall);
        bobCall.waitOutgoingCallResponse(1000);
//        Thread.sleep(1000);

        SipTransaction cancelTransaction = bobCall.sendCancel();
        assertNotNull(cancelTransaction);

        bobCall.waitForCancel(5 * 1000);
        int lastResponseCode = bobCall.getLastReceivedResponse().getStatusCode();
        if (lastResponseCode != Response.REQUEST_TERMINATED) {
            bobCall.waitOutgoingCallResponse(1000);
        }
        assertEquals(Response.REQUEST_TERMINATED, bobCall.getLastReceivedResponse().getStatusCode());

        Request ackRequest = cancelTransaction.getServerTransaction().getDialog().createRequest(Request.ACK);
        ContentTypeHeader contentTypeHeader = bobCall.getHeaderFactory().createContentTypeHeader("application", "vnd.3gpp.ussd+xml");
        ackRequest.setContent(null, contentTypeHeader);

        assertNotNull(bobPhone.sendRequestWithTransaction(ackRequest, false, cancelTransaction.getServerTransaction().getDialog()));
//
//        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
//        int responseBob = bobCall.getLastReceivedResponse().getStatusCode();
//        assertTrue(responseBob == Response.TRYING || responseBob == Response.RINGING);
//
//        if (responseBob == Response.TRYING || responseBob == Response.RINGING) {
//            SipTransaction cancelTransaction = bobCall.sendCancel();
//            assertNotNull(cancelTransaction);
//
//            bobCall.waitForAnswer(5 * 1000);
//            assertEquals(Response.REQUEST_TERMINATED, bobCall.getLastReceivedResponse().getStatusCode());
//
//            Request ackRequest = cancelTransaction.getServerTransaction().getDialog().createRequest(Request.ACK);
//            ContentTypeHeader contentTypeHeader = bobCall.getHeaderFactory().createContentTypeHeader("application", "vnd.3gpp.ussd+xml");
//            ackRequest.setContent(null, contentTypeHeader);
//
//            assertNotNull(bobPhone.sendRequestWithTransaction(ackRequest, false, cancelTransaction.getServerTransaction().getDialog()));
//        }

    }

    @Test
    @Category(UnstableTests.class)
    public void testUssdMessageLengthExceeds() {
        final SipCall bobCall = bobPhone.createSipCall();
        bobCall.initiateOutgoingCall(bobContact, ussdPullMessageLengthExceeds, null, UssdPullTestMessages.ussdClientRequestBodyForMessageLengthExceeds, "application", "vnd.3gpp.ussd+xml", null, null);
        assertLastOperationSuccess(bobCall);

        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        int responseBob = bobCall.getLastReceivedResponse().getStatusCode();
        assertTrue(responseBob == Response.TRYING || responseBob == Response.RINGING);

        if (responseBob == Response.TRYING) {
            assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
            assertEquals(Response.RINGING, bobCall.getLastReceivedResponse().getStatusCode());
        }

        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        assertEquals(Response.OK, bobCall.getLastReceivedResponse().getStatusCode());
        assertTrue(bobCall.sendInviteOkAck());

        assertTrue(bobCall.getDialog().getState().getValue() == DialogState._CONFIRMED);

        assertTrue(bobCall.listenForDisconnect());

        assertTrue(bobCall.waitForDisconnect(30 * 1000));
        bobCall.respondToDisconnect();
        SipRequest bye = bobCall.getLastReceivedRequest();
        String receivedUssdPayload = new String(bye.getRawContent());
        assertTrue(receivedUssdPayload.equalsIgnoreCase(UssdPullTestMessages.ussdRestcommResponseForMessageLengthExceeds.trim()));
        bobCall.dispose();
    }

    @Test
    public void testUssdPullClosedAccount() {
        final SipCall bobCall = bobPhone.createSipCall();
        bobCall.initiateOutgoingCall(bobContact, ussdPullDidClosedAccount, null, UssdPullTestMessages.ussdClientRequestBody, "application", "vnd.3gpp.ussd+xml", null, null);
        assertLastOperationSuccess(bobCall);

        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        int responseBob = bobCall.getLastReceivedResponse().getStatusCode();
        if (responseBob == Response.TRYING) {
            assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
            assertEquals(Response.FORBIDDEN, bobCall.getLastReceivedResponse().getStatusCode());
        } else {
            assertEquals(Response.FORBIDDEN, bobCall.getLastReceivedResponse().getStatusCode());
        }
        bobCall.dispose();
    }

    @Test
    public void testUssdPullSuspendedAccount() {
        final SipCall bobCall = bobPhone.createSipCall();
        bobCall.initiateOutgoingCall(bobContact, ussdPullDidSuspendedAccount, null, UssdPullTestMessages.ussdClientRequestBody, "application", "vnd.3gpp.ussd+xml", null, null);
        assertLastOperationSuccess(bobCall);

        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        int responseBob = bobCall.getLastReceivedResponse().getStatusCode();
        if (responseBob == Response.TRYING) {
            assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
            assertEquals(Response.FORBIDDEN, bobCall.getLastReceivedResponse().getStatusCode());
        } else {
            assertEquals(Response.FORBIDDEN, bobCall.getLastReceivedResponse().getStatusCode());
        }
        bobCall.dispose();
    }

    @Deployment(name = "UssdPullTest", managed = true, testable = false)
    public static WebArchive createWebArchiveNoGw() {
        logger.info("Packaging Test App");
        reconfigurePorts();

        Map<String,String> webInfResources = new HashMap();
        webInfResources.put("restcomm.xml", "conf/restcomm.xml");
        webInfResources.put("org/restcomm/connect/ussd/restcomm.script_ussdPullTest", "data/hsql/restcomm.script");
        webInfResources.put("akka_application.conf", "classes/application.conf");
        webInfResources.put("sip.xml", "/sip.xml");
        webInfResources.put("web.xml", "web.xml");

        Map<String, String> replacements = new HashMap();
        //replace mediaport 2727
        replacements.put("2727", String.valueOf(mediaPort));
        replacements.put("8080", String.valueOf(restcommHTTPPort));
        replacements.put("5080", String.valueOf(restcommPort));
        replacements.put("5090", String.valueOf(bobPort));

        List<String> resources = new ArrayList(Arrays.asList(
                "org/restcomm/connect/ussd/ussd-rcml.xml",
                "org/restcomm/connect/ussd/ussd-rcml-collect.xml",
                "org/restcomm/connect/ussd/ussd-rcml-character-limit-exceed.xml",
                "org/restcomm/connect/ussd/ussd-rcml-shortdial.xml"
        ));

        return WebArchiveUtil.createWebArchiveNoGw(webInfResources,
                resources,
                replacements);
    }

}
