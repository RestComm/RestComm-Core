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
package org.restcomm.connect.testsuite.sms;

import gov.nist.javax.sip.header.SIPHeader;

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
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.FixMethodOrder;
import org.junit.runners.MethodSorters;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.restcomm.connect.commons.Version;
import org.restcomm.connect.commons.annotations.FeatureAltTests;
import org.restcomm.connect.commons.annotations.ParallelClassTests;
import org.restcomm.connect.commons.annotations.UnstableTests;
import org.restcomm.connect.commons.annotations.WithInSecsTests;
import org.restcomm.connect.testsuite.NetworkPortAssigner;
import org.restcomm.connect.testsuite.WebArchiveUtil;

import javax.sip.address.SipURI;
import javax.sip.header.Header;
import javax.sip.message.Request;
import javax.sip.message.Response;

import java.net.URL;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static javax.servlet.sip.SipServletResponse.SC_FORBIDDEN;
import static org.cafesip.sipunit.SipAssert.assertLastOperationSuccess;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author <a href="mailto:gvagenas@gmail.com">gvagenas</a>
 *
 */
@RunWith(Arquillian.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@Category(value={WithInSecsTests.class, ParallelClassTests.class})
public class SmsTest {

    private final static Logger logger = Logger.getLogger(SmsTest.class);
    private static final String version = Version.getVersion();

    private static final byte[] bytes = new byte[]{118, 61, 48, 13, 10, 111, 61, 117, 115, 101, 114, 49, 32, 53, 51, 54, 53,
        53, 55, 54, 53, 32, 50, 51, 53, 51, 54, 56, 55, 54, 51, 55, 32, 73, 78, 32, 73, 80, 52, 32, 49, 50, 55, 46, 48, 46,
        48, 46, 49, 13, 10, 115, 61, 45, 13, 10, 99, 61, 73, 78, 32, 73, 80, 52, 32, 49, 50, 55, 46, 48, 46, 48, 46, 49,
        13, 10, 116, 61, 48, 32, 48, 13, 10, 109, 61, 97, 117, 100, 105, 111, 32, 54, 48, 48, 48, 32, 82, 84, 80, 47, 65,
        86, 80, 32, 48, 13, 10, 97, 61, 114, 116, 112, 109, 97, 112, 58, 48, 32, 80, 67, 77, 85, 47, 56, 48, 48, 48, 13, 10};
    private static final String body = new String(bytes);

    private static int mediaPort = NetworkPortAssigner.retrieveNextPortByFile();

    @ArquillianResource
    private Deployer deployer;
    @ArquillianResource
    URL deploymentUrl;

    private static SipStackTool tool1;
    private static SipStackTool tool2;
    private static SipStackTool tool3;
    private static SipStackTool tool4;
    private static SipStackTool tool5;
    private static SipStackTool tool6;
    private static SipStackTool tool7;
    private static SipStackTool tool8;
    private static SipStackTool tool9;
    private static SipStackTool tool10;


    private SipStack bobSipStack;
    private SipPhone bobPhone;
    private static String bobPort = String.valueOf(NetworkPortAssigner.retrieveNextPortByFile());
    private String bobContact = "sip:bob@127.0.0.1:" + bobPort;

    private SipStack aliceSipStack;
    private SipPhone alicePhone;
    private static String alicePort = String.valueOf(NetworkPortAssigner.retrieveNextPortByFile());
    private String aliceContact = "sip:alice@127.0.0.1:" + alicePort;

    private SipStack georgeSipStack;
    private SipPhone georgePhone;
    private static String georgePort = String.valueOf(NetworkPortAssigner.retrieveNextPortByFile());
    private String georgeContact = "sip:+131313@127.0.0.1:" + georgePort;

    private SipStack fotiniSipStack;
    private SipPhone fotiniPhone;
    private static String fotiniPort = String.valueOf(NetworkPortAssigner.retrieveNextPortByFile());
    private String fotiniContact = "sip:fotini@127.0.0.1:" + fotiniPort;

    private SipStack aliceSipStackOrg2;
    private SipPhone alicePhoneOrg2;
    private static String alicePort2 = String.valueOf(NetworkPortAssigner.retrieveNextPortByFile());
    private String aliceContactOrg2 = "sip:alice@org2.restcomm.com";

    private SipStack bobSipStackOrg2;
    private SipPhone bobPhoneOrg2;
    private static String bobPort2 = String.valueOf(NetworkPortAssigner.retrieveNextPortByFile());
    private String bobContactOrg2 = "sip:bob@org2.restcomm.com";

    private SipStack georgeSipStackOrg2;
    private SipPhone georgePhoneOrg2;
    private static String georgePort2 = String.valueOf(NetworkPortAssigner.retrieveNextPortByFile());
    private String georgeContactOrg2 = "sip:george@org2.restcomm.com";

    private SipStack fotiniSipStackOrg2;
    private SipPhone fotiniPhoneOrg2;
    private static String fotiniPort2 = String.valueOf(NetworkPortAssigner.retrieveNextPortByFile());
    private String fotiniContactOrg2 = "sip:fotini@org2.restcomm.com";

    private SipStack closedSipStack;
    private SipPhone closedPhone;
    private static String closedPort = String.valueOf(NetworkPortAssigner.retrieveNextPortByFile());
    private String closedContact = "sip:closed@127.0.0.1:" + closedPort;

    private SipStack suspendedSipStack;
    private SipPhone suspendedPhone;
    private static String suspendedPort = String.valueOf(NetworkPortAssigner.retrieveNextPortByFile());
    private String suspendedContact = "sip:suspended@127.0.0.1:" + suspendedPort;

    private static int restcommPort = 5080;
    private static int restcommHTTPPort = 8080;
    private static String restcommContact = "127.0.0.1:" + restcommPort;
    private static String dialSendSMS = "sip:+12223334444@" + restcommContact;
    private static String dialSendSMS2 = "sip:+12223334445@" + restcommContact;
    private static String dialSendSMS2_Greek = "sip:+12223334447@" + restcommContact;
    private static String dialSendSMS2_Greek_Huge = "sip:+12223334448@" + restcommContact;
    private static String dialSendSMS3 = "sip:+12223334446@" + restcommContact;
    private static String dialSendSMSwithCustomHeaders = "sip:+12223334449@" + restcommContact;
    private static String dialSendSMS2Org2 = "sip:+12223334445@org2.restcomm.com";

    private String greekHugeMessage = "Καλημερα Καλημερα Καλημερα Καλημερα Καλημερα Καλημερα Καλημερα Καλημερα Καλημερα Καλημερα "
            + "Καλημερα Καλημερα Καλημερα Καλημερα Καλημερα Καλημερα Καλημερα Καλημερα Καλημερα Καλημερα Καλημερα Καλημερα Καλημερα "
            + "Καλημερα Καλημερα Καλημερα Καλημερα Καλημερα Καλημερα Καλημερα Καλημερα Καλημερα Καλημερα Καλημερα Καλημερα Καλημερα "
            + "Καλημερα Καλημερα Καλημερα Καλημερα Καλημερα Καλημερα Καλημερα Καλημερα Καλημερα Καλημερα Καλημερα Καλημερα Καλημερα "
            + "Καλημερα Καλημερα Καλημερα";

    @BeforeClass
    public static void beforeClass() throws Exception {
        tool1 = new SipStackTool("SmsTest1");
        tool2 = new SipStackTool("SmsTest2");
        tool3 = new SipStackTool("SmsTest3");
        tool4 = new SipStackTool("SmsTest4");
        tool5 = new SipStackTool("SmsTest5");
        tool6 = new SipStackTool("SmsTest6");
        tool7 = new SipStackTool("SmsTest7");
        tool8 = new SipStackTool("SmsTest8");
        tool9 = new SipStackTool("SmsTest9");
        tool10 = new SipStackTool("SmsTest10");
    }

    public static void reconfigurePorts() {
        if (System.getProperty("arquillian_sip_port") != null) {
            restcommPort = Integer.valueOf(System.getProperty("arquillian_sip_port"));
            restcommContact = "127.0.0.1:" + restcommPort;
            dialSendSMS = "sip:+12223334444@" + restcommContact;
            dialSendSMS2 = "sip:+12223334445@" + restcommContact;
            dialSendSMS2_Greek = "sip:+12223334447@" + restcommContact;
            dialSendSMS2_Greek_Huge = "sip:+12223334448@" + restcommContact;
            dialSendSMS3 = "sip:+12223334446@" + restcommContact;
            dialSendSMSwithCustomHeaders = "sip:+12223334449@" + restcommContact;
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

        georgeSipStack = tool3.initializeSipStack(SipStack.PROTOCOL_UDP, "127.0.0.1", georgePort, restcommContact);
        georgePhone = georgeSipStack.createSipPhone("127.0.0.1", SipStack.PROTOCOL_UDP, restcommPort, georgeContact);

        fotiniSipStack = tool4.initializeSipStack(SipStack.PROTOCOL_UDP, "127.0.0.1", fotiniPort, restcommContact);
        fotiniPhone = fotiniSipStack.createSipPhone("127.0.0.1", SipStack.PROTOCOL_UDP, restcommPort, fotiniContact);

        aliceSipStackOrg2 = tool5.initializeSipStack(SipStack.PROTOCOL_UDP, "127.0.0.1", alicePort2, restcommContact);
        alicePhoneOrg2 = aliceSipStackOrg2.createSipPhone("127.0.0.1", SipStack.PROTOCOL_UDP, restcommPort, aliceContactOrg2);

        bobSipStackOrg2 = tool6.initializeSipStack(SipStack.PROTOCOL_UDP, "127.0.0.1", bobPort2, restcommContact);
        bobPhoneOrg2 = bobSipStackOrg2.createSipPhone("127.0.0.1", SipStack.PROTOCOL_UDP, restcommPort, bobContactOrg2);

        georgeSipStackOrg2 = tool7.initializeSipStack(SipStack.PROTOCOL_UDP, "127.0.0.1", georgePort2, restcommContact);
        georgePhoneOrg2 = georgeSipStackOrg2.createSipPhone("127.0.0.1", SipStack.PROTOCOL_UDP, restcommPort, georgeContactOrg2);

        fotiniSipStackOrg2 = tool8.initializeSipStack(SipStack.PROTOCOL_UDP, "127.0.0.1", fotiniPort2, restcommContact);
        fotiniPhoneOrg2 = fotiniSipStackOrg2.createSipPhone("127.0.0.1", SipStack.PROTOCOL_UDP, restcommPort, fotiniContactOrg2);

        closedSipStack = tool9.initializeSipStack(SipStack.PROTOCOL_UDP, "127.0.0.1", closedPort, restcommContact);
        closedPhone = closedSipStack.createSipPhone("127.0.0.1", SipStack.PROTOCOL_UDP, restcommPort, closedContact);

        suspendedSipStack = tool10.initializeSipStack(SipStack.PROTOCOL_UDP, "127.0.0.1", suspendedPort, restcommContact);
        suspendedPhone = suspendedSipStack.createSipPhone("127.0.0.1", SipStack.PROTOCOL_UDP, restcommPort, suspendedContact);
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

        if (georgeSipStack != null) {
            georgeSipStack.dispose();
        }
        if (georgePhone != null) {
            georgePhone.dispose();
        }

        if (fotiniSipStack != null) {
            fotiniSipStack.dispose();
        }
        if (fotiniPhone != null) {
            fotiniPhone.dispose();
        }

        if (georgeSipStackOrg2 != null) {
            georgeSipStackOrg2.dispose();
        }
        if (georgePhoneOrg2 != null) {
            georgePhoneOrg2.dispose();
        }

        if (fotiniSipStackOrg2 != null) {
            fotiniSipStackOrg2.dispose();
        }
        if (fotiniPhoneOrg2 != null) {
            fotiniPhoneOrg2.dispose();
        }
        if (bobPhoneOrg2 != null) {
            bobPhoneOrg2.dispose();
        }
        if (bobSipStackOrg2 != null) {
            bobSipStackOrg2.dispose();
        }

        if (aliceSipStackOrg2 != null) {
            aliceSipStackOrg2.dispose();
        }
        if (alicePhoneOrg2 != null) {
            alicePhoneOrg2.dispose();
        }

        if (closedPhone != null) {
            closedPhone.dispose();
        }
        if (closedSipStack != null) {
            closedSipStack.dispose();
        }

        if (suspendedPhone != null) {
            suspendedPhone.dispose();
        }
        if (suspendedSipStack != null) {
            suspendedSipStack.dispose();
        }
        Thread.sleep(1000);
    }

    @Test
    public void testAliceActsAsSMSGatewayAndReceivesSMS() throws ParseException {
        // Phone2 register as alice
        SipURI uri = aliceSipStack.getAddressFactory().createSipURI(null, restcommContact);
        assertTrue(alicePhone.register(uri, "alice", "1234", aliceContact, 3600, 3600));

        // Prepare second phone to receive call
        SipCall aliceCall = alicePhone.createSipCall();
        aliceCall.listenForMessage();

        // Create outgoing call with first phone
        final SipCall bobCall = bobPhone.createSipCall();
        bobCall.initiateOutgoingCall(bobContact, dialSendSMS, null, body, "application", "sdp", null, null);
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
        bobCall.listenForDisconnect();

        assertTrue(aliceCall.waitForMessage(5 * 1000));
        String msgReceived = new String(aliceCall.getLastReceivedMessageRequest().getRawContent());
        assertTrue("Hello World!".equals(msgReceived));
        aliceCall.sendMessageResponse(200, "OK-From-Alice", 3600);

        assertTrue(bobCall.waitForDisconnect(40 * 1000));
        assertTrue(bobCall.respondToDisconnect());

        try {
            Thread.sleep(10 * 1000);
        } catch (final InterruptedException exception) {
            exception.printStackTrace();
        }
    }

    @Test
    public void TestIncomingSmsSendToClientAlice() throws ParseException, InterruptedException {
        SipURI uri = aliceSipStack.getAddressFactory().createSipURI(null, restcommContact);
        assertTrue(alicePhone.register(uri, "alice", "1234", aliceContact, 3600, 3600));

        // Prepare second phone to receive call
        SipCall aliceCall = alicePhone.createSipCall();
        aliceCall.listenForMessage();

        // Create outgoing call with first phone
        final SipCall bobCall = bobPhone.createSipCall();
        bobCall.initiateOutgoingMessage(dialSendSMS2, null, "Hello from Bob!");
        assertLastOperationSuccess(bobCall);
        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        final int response = bobCall.getLastReceivedResponse().getStatusCode();
        assertTrue(response == Response.ACCEPTED);

        //Restcomm receives the SMS message from Bob, matches the DID with an RCML application, and executes it.
        //The new RCML application sends an SMS to Alice with body "Hello World!"
        assertTrue(aliceCall.waitForMessage(5 * 1000));
        String msgReceived = new String(aliceCall.getLastReceivedMessageRequest().getRawContent());
        assertTrue("Hello World!".equals(msgReceived));
        aliceCall.sendMessageResponse(200, "OK-From-Alice", 3600);
    }

    @Test
    public void TestIncomingSmsSendToClientAliceOfOrganization2() throws ParseException, InterruptedException {
        SipURI uri = aliceSipStackOrg2.getAddressFactory().createSipURI(null, restcommContact);
        assertTrue(alicePhoneOrg2.register(uri, "alice", "1234", "sip:alice@127.0.0.1:" + alicePort2, 3600, 3600));

        // Prepare second phone to receive call
        SipCall aliceCallOrg2 = alicePhoneOrg2.createSipCall();
        aliceCallOrg2.listenForMessage();

        // Create outgoing call with first phone
        final SipCall bobCallOrg2 = bobPhoneOrg2.createSipCall();
        bobCallOrg2.initiateOutgoingMessage(dialSendSMS2Org2, null, "Hello from Bob!");
        assertLastOperationSuccess(bobCallOrg2);
        assertTrue(bobCallOrg2.waitOutgoingCallResponse(5 * 1000));
        final int response = bobCallOrg2.getLastReceivedResponse().getStatusCode();
        assertTrue(response == Response.ACCEPTED);

        //Restcomm receives the SMS message from Bob, matches the DID with an RCML application, and executes it.
        //The new RCML application sends an SMS to Alice with body "Hello World!"
        assertTrue(aliceCallOrg2.waitForMessage(5 * 1000));
        String msgReceived = new String(aliceCallOrg2.getLastReceivedMessageRequest().getRawContent());
        assertTrue("Hello World!".equals(msgReceived));
        aliceCallOrg2.sendMessageResponse(200, "OK-From-Alice", 3600);
    }

    @Test
    @Category(value={FeatureAltTests.class})
    public void TestIncomingSmsSendToClientAliceGreekHugeMessage() throws ParseException, InterruptedException {
        SipURI uri = aliceSipStack.getAddressFactory().createSipURI(null, restcommContact);
        assertTrue(alicePhone.register(uri, "alice", "1234", aliceContact, 3600, 3600));

        // Prepare second phone to receive call
        SipCall aliceCall = alicePhone.createSipCall();
        aliceCall.listenForMessage();

        // Create outgoing call with first phone
        final SipCall bobCall = bobPhone.createSipCall();
        bobCall.initiateOutgoingMessage(dialSendSMS2_Greek_Huge, null, greekHugeMessage);
        assertLastOperationSuccess(bobCall);
        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        final int response = bobCall.getLastReceivedResponse().getStatusCode();
        assertTrue(response == Response.ACCEPTED);

        //Restcomm receives the SMS message from Bob, matches the DID with an RCML application, and executes it.
        //The new RCML application sends an SMS to Alice with body "Hello World!"
        assertTrue(aliceCall.waitForMessage(5 * 1000));
        String msgReceived = new String(aliceCall.getLastReceivedMessageRequest().getRawContent());
        assertTrue(greekHugeMessage.equals(msgReceived));
        aliceCall.sendMessageResponse(200, "OK-From-Alice", 3600);
    }

    @Test
    @Category(value={FeatureAltTests.class})
    public void TestIncomingSmsSendToClientAliceGreek() throws ParseException, InterruptedException {
        SipURI uri = aliceSipStack.getAddressFactory().createSipURI(null, restcommContact);
        assertTrue(alicePhone.register(uri, "alice", "1234", aliceContact, 3600, 3600));

        // Prepare second phone to receive call
        SipCall aliceCall = alicePhone.createSipCall();
        aliceCall.listenForMessage();

        // Create outgoing call with first phone
        final SipCall bobCall = bobPhone.createSipCall();
        bobCall.initiateOutgoingMessage(dialSendSMS2_Greek, null, "Καλώς τον Γιώργο!");
        assertLastOperationSuccess(bobCall);
        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        final int response = bobCall.getLastReceivedResponse().getStatusCode();
        assertTrue(response == Response.ACCEPTED);

        //Restcomm receives the SMS message from Bob, matches the DID with an RCML application, and executes it.
        //The new RCML application sends an SMS to Alice with body "Hello World!"
        assertTrue(aliceCall.waitForMessage(5 * 1000));
        String msgReceived = new String(aliceCall.getLastReceivedMessageRequest().getRawContent());
        assertTrue("Καλώς τον Γιώργο!".equals(msgReceived));
        aliceCall.sendMessageResponse(200, "OK-From-Alice", 3600);
    }

    @Test
    public void TestIncomingSmsSendToNumber1313() throws ParseException, InterruptedException {
        SipURI uri = aliceSipStack.getAddressFactory().createSipURI(null, restcommContact);
        assertTrue(alicePhone.register(uri, "alice", "1234", "sip:1313@127.0.0.1:" + alicePort, 3600, 3600));

        // Prepare second phone to receive call
        SipCall aliceCall = alicePhone.createSipCall();
        aliceCall.listenForMessage();

        // Create outgoing call with first phone
        final SipCall bobCall = bobPhone.createSipCall();
        bobCall.initiateOutgoingMessage(dialSendSMS3, null, "Hello from Bob!");
        assertLastOperationSuccess(bobCall);
        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        final int response = bobCall.getLastReceivedResponse().getStatusCode();
        assertTrue(response == Response.ACCEPTED);

        //Restcomm receives the SMS message from Bob, matches the DID with an RCML application, and executes it.
        //The new RCML application sends an SMS to Alice with body "Hello World!"
        assertTrue(aliceCall.waitForMessage(5 * 1000));
        String msgReceived = new String(aliceCall.getLastReceivedMessageRequest().getRawContent());
        assertTrue("Hello World!".equals(msgReceived));
        aliceCall.sendMessageResponse(200, "OK-From-Alice", 3600);
    }

    @Test
    @Category(value={FeatureAltTests.class})
    public void TestIncomingSmsSendToNumber1313WithCustomHeaders() throws ParseException, InterruptedException {
        String myFirstHeaderName = "X-Custom-Header-1";
        String myFirstHeaderValue = "X Custom Header Value 1";

        String mySecondHeaderName = "X-Custom-Header-2";
        String mySecondHeaderValue = "X Custom Header Value 2";

        SipURI uri = aliceSipStack.getAddressFactory().createSipURI(null, restcommContact);
        assertTrue(alicePhone.register(uri, "alice", "1234", "sip:1313@127.0.0.1:" + alicePort, 3600, 3600));

        // Prepare second phone to receive call
        SipCall aliceCall = alicePhone.createSipCall();
        aliceCall.listenForMessage();

        // Create outgoing call with first phone
        final SipCall bobCall = bobPhone.createSipCall();

        ArrayList<Header> additionalHeaders = new ArrayList<Header>();
        Header header1 = bobSipStack.getHeaderFactory().createHeader(myFirstHeaderName, myFirstHeaderValue);
        Header header2 = bobSipStack.getHeaderFactory().createHeader(mySecondHeaderName, mySecondHeaderValue);
        additionalHeaders.add(header1);
        additionalHeaders.add(header2);

        bobCall.initiateOutgoingMessage(bobContact, dialSendSMSwithCustomHeaders, null, additionalHeaders, null, "Hello from Bob!");
        assertLastOperationSuccess(bobCall);
        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        final int response = bobCall.getLastReceivedResponse().getStatusCode();
        assertTrue(response == Response.ACCEPTED);

        //Restcomm receives the SMS message from Bob, matches the DID with an RCML application, and executes it.
        //The new RCML application sends an SMS to Alice with body "Hello World!"
        assertTrue(aliceCall.waitForMessage(5 * 1000));
        Request receivedRequest = aliceCall.getLastReceivedMessageRequest();
        String msgReceived = new String(receivedRequest.getRawContent());
        assertTrue("Hello World!".equals(msgReceived));

        SIPHeader myFirstHeader = (SIPHeader) receivedRequest.getHeader(myFirstHeaderName);
        assertTrue(myFirstHeader != null);
        assertTrue(myFirstHeader.getValue().equalsIgnoreCase(myFirstHeaderValue));

        SIPHeader mySecondHeader = (SIPHeader) receivedRequest.getHeader(mySecondHeaderName);
        assertTrue(mySecondHeader != null);
        assertTrue(mySecondHeader.getHeaderValue().equalsIgnoreCase(mySecondHeaderValue));

        aliceCall.sendMessageResponse(200, "OK-From-Alice", 3600);
    }

    @Test
    @Category(UnstableTests.class)
    public void testP2PSendSMS_GeorgeClient_ToFotiniClient() throws ParseException {
        SipURI uri = aliceSipStack.getAddressFactory().createSipURI(null, restcommContact);
        //Register George phone
        assertTrue(alicePhone.register(uri, "alice", "1234", aliceContact, 3600, 3600));
        Credential aliceCredentials = new Credential("127.0.0.1", "alice", "1234");
        alicePhone.addUpdateCredential(aliceCredentials);

        //Register Fotini phone
        assertTrue(fotiniPhone.register(uri, "fotini", "1234", fotiniContact, 3600, 3600));
        Credential fotiniCredentials = new Credential("127.0.0.1", "fotini", "1234");
        fotiniPhone.addUpdateCredential(fotiniCredentials);

        //Prepare Fotini to receive message
        SipCall fotiniCall = fotiniPhone.createSipCall();
        fotiniCall.listenForMessage();

        //Prepare George to send message
        SipCall aliceCall = alicePhone.createSipCall();
        aliceCall.initiateOutgoingMessage(aliceContact, "sip:fotini@" + restcommContact, null, null, null, greekHugeMessage);
        assertLastOperationSuccess(aliceCall);
        aliceCall.waitForAuthorisation(30 * 1000);
        assertTrue(aliceCall.waitOutgoingMessageResponse(3000));
        assertEquals(Response.TRYING, aliceCall.getLastReceivedResponse().getStatusCode());

        assertTrue(fotiniCall.waitForMessage(30 * 1000));
        assertTrue(fotiniCall.sendMessageResponse(200, "OK-Fotini-Mesasge-Receieved", 1800));
        assertTrue(aliceCall.waitOutgoingMessageResponse(3000));
        assertTrue(aliceCall.getLastReceivedResponse().getStatusCode() == Response.OK);
        List<String> msgsFromGeorge = fotiniCall.getAllReceivedMessagesContent();

        assertTrue(msgsFromGeorge.size() > 0);
        assertTrue(msgsFromGeorge.get(0).equals(greekHugeMessage));
    }

    @Test
    @Category(UnstableTests.class)
    public void testP2PSendSMS_GeorgeClient_ToFotiniClientOrg2() throws ParseException {
        SipURI uri = georgeSipStackOrg2.getAddressFactory().createSipURI(null, restcommContact);
        //Register George phone
        assertTrue(georgePhoneOrg2.register(uri, "george", "1234", "sip:george@127.0.0.1:" + georgePort2, 3600, 3600));
        Credential georgeCredentialsOrg2 = new Credential("org2.restcomm.com", "george", "1234");
        georgePhoneOrg2.addUpdateCredential(georgeCredentialsOrg2);

        //Register Fotini phone
        assertTrue(fotiniPhoneOrg2.register(uri, "fotini", "1234", "sip:fotini@127.0.0.1:" + fotiniPort2, 3600, 3600));
        Credential fotiniCredentials = new Credential("org2.restcomm.com", "fotini", "1234");
        fotiniPhoneOrg2.addUpdateCredential(fotiniCredentials);

        //Prepare Fotini to receive message
        SipCall fotiniCallOrg2 = fotiniPhoneOrg2.createSipCall();
        fotiniCallOrg2.listenForMessage();

        //Prepare George to send message
        SipCall georgeCallOrg2 = georgePhoneOrg2.createSipCall();
        georgeCallOrg2.initiateOutgoingMessage(georgeContactOrg2, fotiniContactOrg2, null, null, null, greekHugeMessage);
        assertLastOperationSuccess(georgeCallOrg2);
        georgeCallOrg2.waitForAuthorisation(30 * 1000);
        assertTrue(georgeCallOrg2.waitOutgoingMessageResponse(3000));
        assertTrue(georgeCallOrg2.getLastReceivedResponse().getStatusCode() == Response.TRYING);

        assertTrue(fotiniCallOrg2.waitForMessage(30 * 1000));
        assertTrue(fotiniCallOrg2.sendMessageResponse(200, "OK-Fotini-Mesasge-Receieved", 1800));
        assertTrue(georgeCallOrg2.waitOutgoingMessageResponse(3000));
        assertTrue(georgeCallOrg2.getLastReceivedResponse().getStatusCode() == Response.OK);
        List<String> msgsFromGeorge = fotiniCallOrg2.getAllReceivedMessagesContent();

        assertTrue(msgsFromGeorge.size() > 0);
        assertTrue(msgsFromGeorge.get(0).equals(greekHugeMessage));
    }

    @Test
    @Category(value={FeatureAltTests.class})
    public void testP2PSendSMS_GeorgeClient_ToFotiniClient_EmptyContent() throws ParseException {
        SipURI uri = georgeSipStack.getAddressFactory().createSipURI(null, restcommContact);
        //Register George phone
        assertTrue(georgePhone.register(uri, "george", "1234", georgeContact, 3600, 3600));
        Credential georgeCredentials = new Credential("127.0.0.1", "george", "1234");
        georgePhone.addUpdateCredential(georgeCredentials);

        //Register Fotini phone
        assertTrue(fotiniPhone.register(uri, "fotini", "1234", "sip:fotini@" + restcommContact, 3600, 3600));
        Credential fotiniCredentials = new Credential("127.0.0.1", "fotini", "1234");
        fotiniPhone.addUpdateCredential(fotiniCredentials);

        //Prepare Fotini to receive message
        SipCall fotiniCall = fotiniPhone.createSipCall();
        fotiniCall.listenForMessage();

        //Prepare George to send message
        SipCall georgeCall = georgePhone.createSipCall();
        georgeCall.initiateOutgoingMessage(georgeContact, fotiniContact, null, null, null, null);
        assertLastOperationSuccess(georgeCall);
        assertTrue(georgeCall.waitOutgoingMessageResponse(5000));
        assertTrue(georgeCall.getLastReceivedResponse().getStatusCode() == Response.NOT_ACCEPTABLE);
    }

    @Test
    public void TestIncomingSmsSendToNumberOfClosedAccount() throws ParseException, InterruptedException {
        // Create outgoing call with first phone
        final SipCall bobCall = bobPhone.createSipCall();
        bobCall.initiateOutgoingMessage("sip:2222@" + restcommContact, null, "Hello from Bob!");
        assertLastOperationSuccess(bobCall);
        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        final int response = bobCall.getLastReceivedResponse().getStatusCode();
        assertEquals(SC_FORBIDDEN, response);
    }

    @Test
    public void TestIncomingSmsSendToNumberOfSuspendedAccount() throws ParseException, InterruptedException {
        // Create outgoing call with first phone
        final SipCall bobCall = bobPhone.createSipCall();
        bobCall.initiateOutgoingMessage("sip:3333@" + restcommContact, null, "Hello from Bob!");
        assertLastOperationSuccess(bobCall);
        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        final int response = bobCall.getLastReceivedResponse().getStatusCode();
        assertEquals(SC_FORBIDDEN, response);
    }

    @Deployment(name = "SmsTest", managed = true, testable = false)
    public static WebArchive createWebArchiveNoGw() {
        logger.info("Packaging Test App");
        reconfigurePorts();

        Map<String, String> webInfResources = new HashMap();
        webInfResources.put("restcomm_SmsTest.xml", "conf/restcomm.xml");
        webInfResources.put("restcomm.script_SmsTest", "data/hsql/restcomm.script");
        webInfResources.put("sip.xml", "sip.xml");
        webInfResources.put("web_for_SmsTest.xml", "web.xml");
        webInfResources.put("akka_application.conf", "classes/application.conf");

        Map<String, String> replacements = new HashMap();
        //replace mediaport 2727
        replacements.put("2727", String.valueOf(mediaPort));
        replacements.put("8080", String.valueOf(restcommHTTPPort));
        replacements.put("5080", String.valueOf(restcommPort));
        replacements.put("5090", String.valueOf(bobPort));
        replacements.put("5091", String.valueOf(alicePort));
        replacements.put("5092", String.valueOf(georgePort));
        replacements.put("5093", String.valueOf(fotiniPort));

        replacements.put("5094", String.valueOf(alicePort2));
        replacements.put("5095", String.valueOf(bobPort2));
        replacements.put("5096", String.valueOf(georgePort2));
        replacements.put("5097", String.valueOf(fotiniPort2));


        List<String> resources = new ArrayList(Arrays.asList(
                "send-sms-test.xml",
                "send-sms-test-greek.xml",
                "send-sms-test-greek_huge.xml",
                "send-sms-test2.xml",
                "dial-client-entry.xml"
        ));
        return WebArchiveUtil.createWebArchiveNoGw(webInfResources,
                resources,
                replacements);
    }
}
