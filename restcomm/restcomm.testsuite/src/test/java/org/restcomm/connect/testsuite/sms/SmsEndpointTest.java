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
import javax.sip.message.Request;

import org.apache.log4j.Logger;
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
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.restcomm.connect.commons.Version;
import org.restcomm.connect.commons.annotations.FeatureAltTests;
import org.restcomm.connect.commons.annotations.ParallelClassTests;
import org.restcomm.connect.commons.annotations.UnstableTests;
import org.restcomm.connect.commons.annotations.WithInSecsTests;
import org.restcomm.connect.testsuite.NetworkPortAssigner;
import org.restcomm.connect.testsuite.WebArchiveUtil;

import com.google.gson.JsonObject;

import gov.nist.javax.sip.header.SIPHeader;

/**
 * @author <a href="mailto:gvagenas@gmail.com">gvagenas</a>
 *
 */
@RunWith(Arquillian.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@Category(value={WithInSecsTests.class, ParallelClassTests.class})
public class SmsEndpointTest {
    private static Logger logger = Logger.getLogger(SmsEndpointTest.class);
    private static final String version = Version.getVersion();

    @ArquillianResource
    private Deployer deployer;
    @ArquillianResource
    URL deploymentUrl;

    private static int mediaPort = NetworkPortAssigner.retrieveNextPortByFile();

    private static SipStackTool tool1;
    private SipStack bobSipStack;
    private SipPhone bobPhone;
    private static String bobPort = String.valueOf(NetworkPortAssigner.retrieveNextPortByFile());
    private String bobContact = "sip:1213@127.0.0.1:" + bobPort;

    private static SipStackTool tool2;
    private SipStack aliceSipStack;
    private SipPhone alicePhone;
    private static String alicePort = String.valueOf(NetworkPortAssigner.retrieveNextPortByFile());
    private String aliceContact = "sip:alice@127.0.0.1:" + alicePort;

    private static SipStackTool tool3;
    private SipStack aliceSipStackOrg2;
    private SipPhone alicePhoneOrg2;
    private static String alicePort2 = String.valueOf(NetworkPortAssigner.retrieveNextPortByFile());
    private String aliceContactOrg2 = "sip:alice@org2.restcomm.com:" + alicePort2;

    private String adminAccountSid = "ACae6e420f425248d6a26948c17a9e2acf";
    private String adminAuthToken = "77f8c12cc7b8f8423e5c38b035249166";

    private String adminAccountSidOrg2 = "ACae6e420f425248d6a26948c17a9e2acg";

    private static int restcommPort = 5080;
    private static int restcommHTTPPort = 8080;
    private static String restcommContact = "127.0.0.1:" + restcommPort;

    @BeforeClass
    public static void beforeClass() throws Exception {
        tool1 = new SipStackTool("SmsTest1");
        tool2 = new SipStackTool("SmsTest2");
        tool3 = new SipStackTool("SmsTest3");
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

        aliceSipStackOrg2 = tool3.initializeSipStack(SipStack.PROTOCOL_UDP, "127.0.0.1", alicePort2, restcommContact);
        alicePhoneOrg2 = aliceSipStackOrg2.createSipPhone("127.0.0.1", SipStack.PROTOCOL_UDP, restcommPort, aliceContactOrg2);
    }

    @After
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
        if (alicePhoneOrg2 != null) {
        	alicePhoneOrg2.dispose();
        }
        if (aliceSipStackOrg2 != null) {
        	aliceSipStackOrg2.dispose();
        }
    }

    @Test
    public void sendSmsTest() {
        SipCall bobCall = bobPhone.createSipCall();
        bobCall.listenForMessage();

        String from = "+15126002188";
        String to = "1213";
        String body = "Hello Bob!";

        JsonObject callResult = SmsEndpointTool.getInstance().createSms(deploymentUrl.toString(), adminAccountSid,
                adminAuthToken, from, to, body, null);
        assertNotNull(callResult);

//        bobPhone.setLoopback(true);
        assertTrue(bobCall.waitForMessage(10000));
        Request messageRequest = bobCall.getLastReceivedMessageRequest();
        assertTrue(bobCall.sendMessageResponse(202, "Accepted", 3600));
        String messageReceived = new String(messageRequest.getRawContent());
        assertTrue(messageReceived.equals(body));
    }

    @Test
    @Category(value={FeatureAltTests.class})
    public void sendSmsTestToClientAlice() throws ParseException {

        SipURI uri = aliceSipStack.getAddressFactory().createSipURI(null, restcommContact);
        assertTrue(alicePhone.register(uri, "alice", "1234", aliceContact, 3600, 3600));

        // Prepare second phone to receive call
        SipCall aliceCall = alicePhone.createSipCall();
        aliceCall.listenForMessage();

        String from = "+15126002188";
        String to = "client:alice";
        String body = "Hello Alice!";

        JsonObject callResult = SmsEndpointTool.getInstance().createSms(deploymentUrl.toString(), adminAccountSid,
                adminAuthToken, from, to, body, null);
        assertNotNull(callResult);

//        bobPhone.setLoopback(true);
        assertTrue(aliceCall.waitForMessage(10000));
        Request messageRequest = aliceCall.getLastReceivedMessageRequest();
        assertTrue(aliceCall.sendMessageResponse(202, "Accepted", 3600));
        String messageReceived = new String(messageRequest.getRawContent());
        assertTrue(messageReceived.equals(body));
    }

    /**
     * Try to send sms to alice:
     * alice exist in both org1 and org2
     * make sure only proper alice gets the msg (the one that exists in source account's organization)
     * @throws ParseException
     */
    @Test
    @Category(value={FeatureAltTests.class, UnstableTests.class})
    public void sendSmsTestToClientExistingInDifferentOrganizations() throws ParseException {
    	// Prepare alice org2 phone to receive call
        SipURI uri = aliceSipStackOrg2.getAddressFactory().createSipURI(null, restcommContact);
        assertTrue(alicePhoneOrg2.register(uri, "alice", "1234", "sip:alice@127.0.0.1:" + alicePort2, 3600, 3600));
        SipCall aliceCallOrg2 = alicePhoneOrg2.createSipCall();
        aliceCallOrg2.listenForMessage();

        // Prepare alice org1 phone to receive call
        SipURI urialiceSipStack = aliceSipStack.getAddressFactory().createSipURI(null, restcommContact);
        assertTrue(alicePhone.register(urialiceSipStack, "alice", "1234", aliceContact, 3600, 3600));
    	SipCall aliceCall = alicePhone.createSipCall();
    	aliceCall.listenForMessage();

        String from = "+15126002188";
        String to = "client:alice";
        String body = "Hello Alice!";

        //send msg from org2 account
        JsonObject msgResult = SmsEndpointTool.getInstance().createSms(deploymentUrl.toString(), adminAccountSidOrg2,
                adminAuthToken, from, to, body, null);
        assertNotNull(msgResult);

        assertTrue(aliceCallOrg2.waitForMessage(10000));
        Request messageRequest = aliceCallOrg2.getLastReceivedMessageRequest();
        assertTrue(aliceCallOrg2.sendMessageResponse(202, "Accepted", 3600));
        String messageReceived = new String(messageRequest.getRawContent());
        assertTrue(messageReceived.equals(body));

        assertTrue(!aliceCall.waitForMessage(10000));
    }

    @Test
    public void sendSmsTestToAlice() throws ParseException {

        SipURI uri = aliceSipStack.getAddressFactory().createSipURI(null, restcommContact);
        assertTrue(alicePhone.register(uri, "alice", "1234", aliceContact, 3600, 3600));

        // Prepare second phone to receive call
        SipCall aliceCall = alicePhone.createSipCall();
        aliceCall.listenForMessage();

        String from = "+15126002188";
        String to = "alice";
        String body = "Hello Alice!";

        JsonObject callResult = SmsEndpointTool.getInstance().createSms(deploymentUrl.toString(), adminAccountSid,
                adminAuthToken, from, to, body, null);
        assertNotNull(callResult);

//        bobPhone.setLoopback(true);
        assertTrue(aliceCall.waitForMessage(10000));
        Request messageRequest = aliceCall.getLastReceivedMessageRequest();
        assertTrue(aliceCall.sendMessageResponse(202, "Accepted", 3600));
        String messageReceived = new String(messageRequest.getRawContent());
        assertTrue(messageReceived.equals(body));
    }

    @Test
    @Category(value={FeatureAltTests.class})
    public void sendSmsTestGSMEncoding() {
        SipCall bobCall = bobPhone.createSipCall();
        bobCall.listenForMessage();

        String from = "+15126002188";
        String to = "1213";
        String body = "Hello Bob!";
        String encoding = "GSM";

        JsonObject callResult = SmsEndpointTool.getInstance().createSms(deploymentUrl.toString(), adminAccountSid,
                adminAuthToken, from, to, body, null, encoding);
        assertNotNull(callResult);

        assertTrue(bobCall.waitForMessage(10000));
        Request messageRequest = bobCall.getLastReceivedMessageRequest();
        assertTrue(bobCall.sendMessageResponse(202, "Accepted", 3600));
        String messageReceived = new String(messageRequest.getRawContent());
        assertTrue(messageReceived.equals(body));
    }

    @Test
    @Category(value={FeatureAltTests.class})
    public void sendSmsTestUCS2Encoding() {
        SipCall bobCall = bobPhone.createSipCall();
        bobCall.listenForMessage();

        String from = "+15126002188";
        String to = "1213";
        String body = " ̰Heo llb!Bo ͤb!";
        String encoding = "UCS-2";

        JsonObject callResult = SmsEndpointTool.getInstance().createSms(deploymentUrl.toString(), adminAccountSid,
                adminAuthToken, from, to, body, null, encoding);
        assertNotNull(callResult);

        assertTrue(bobCall.waitForMessage(10000));
        Request messageRequest = bobCall.getLastReceivedMessageRequest();
        assertTrue(bobCall.sendMessageResponse(202, "Accepted", 3600));
        String messageReceived = new String(messageRequest.getRawContent());
        System.out.println("Body: " + body);
        System.out.println("messageReceived: " + messageReceived);
        assertTrue(messageReceived.equals(body));
    }

    @Test
    @Category(value={FeatureAltTests.class})
    public void sendSmsTestWithCustomHeaders() {
        String myFirstHeaderName = "X-Custom-Header-1";
        String myFirstHeaderValue = "X Custom Header Value 1";

        String mySecondHeaderName = "X-Custom-Header-2";
        String mySecondHeaderValue = "X Custom Header Value 2";

        SipCall bobCall = bobPhone.createSipCall();
        bobCall.listenForMessage();

        String from = "+15126002188";
        String to = "1213";
        String body = "Hello Bob! This time I sent you the message and some additional headers.";
        HashMap<String, String> additionalHeaders = new HashMap<String, String>();
        additionalHeaders.put(myFirstHeaderName, myFirstHeaderValue);
        additionalHeaders.put(mySecondHeaderName, mySecondHeaderValue);

        JsonObject callResult = SmsEndpointTool.getInstance().createSms(deploymentUrl.toString(), adminAccountSid,
                adminAuthToken, from, to, body, additionalHeaders);
        assertNotNull(callResult);

        assertTrue(bobCall.waitForMessage(10000));
        Request messageRequest = bobCall.getLastReceivedMessageRequest();
        assertTrue(bobCall.sendMessageResponse(202, "Accepted", 3600));
        String messageReceived = new String(messageRequest.getRawContent());
        assertTrue(messageReceived.equals(body));

        SIPHeader myFirstHeader = (SIPHeader)messageRequest.getHeader(myFirstHeaderName);
        assertTrue(myFirstHeader != null);
        assertTrue(myFirstHeader.getValue().equalsIgnoreCase(myFirstHeaderValue));

        SIPHeader mySecondHeader = (SIPHeader)messageRequest.getHeader(mySecondHeaderName);
        assertTrue(mySecondHeader != null);
        assertTrue(mySecondHeader.getHeaderValue().equalsIgnoreCase(mySecondHeaderValue));

    }

    @Deployment(name = "LiveCallModificationTest", managed = true, testable = false)
    public static WebArchive createWebArchiveNoGw() {
        logger.info("Packaging Test App");
        reconfigurePorts();

        Map<String, String> webInfResources = new HashMap();
        webInfResources.put("restcomm_for_SMSEndpointTest.xml", "conf/restcomm.xml");
        webInfResources.put("restcomm.script_dialTest", "data/hsql/restcomm.script");
        //webInfResources.put("restcomm.script_SmsTest", "data/hsql/restcomm.script");
        webInfResources.put("akka_application.conf", "classes/application.conf");
        webInfResources.put("sip.xml", "sip.xml");
        webInfResources.put("web.xml", "web.xml");

        Map<String, String> replacements = new HashMap();
        //replace mediaport 2727
        replacements.put("2727", String.valueOf(mediaPort));
        replacements.put("8080", String.valueOf(restcommHTTPPort));
        replacements.put("5080", String.valueOf(restcommPort));
        replacements.put("5090", String.valueOf(bobPort));
        replacements.put("5091", String.valueOf(alicePort));
        replacements.put("5092", String.valueOf(alicePort2));


        List<String> resources = new ArrayList(Arrays.asList(
        ));
        return WebArchiveUtil.createWebArchiveNoGw(webInfResources,
                resources,
                replacements);
    }
}
