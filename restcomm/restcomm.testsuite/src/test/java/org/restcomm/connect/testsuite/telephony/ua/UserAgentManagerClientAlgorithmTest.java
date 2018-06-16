/*
 *  TeleStax, Open Source Cloud Communications
 *  Copyright 2011-2018, Telestax Inc and individual contributors
 *  by the @authors tag.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  under the terms of the GNU Affero General Public License as
 *  published by the Free Software Foundation; either version 3 of
 *  the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>
 */
package org.restcomm.connect.testsuite.telephony.ua;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.cafesip.sipunit.Credential;
import org.cafesip.sipunit.SipPhone;
import org.cafesip.sipunit.SipRequest;
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
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.restcomm.connect.commons.Version;
import org.restcomm.connect.commons.annotations.FeatureAltTests;
import org.restcomm.connect.commons.annotations.ParallelClassTests;
import org.restcomm.connect.commons.annotations.UnstableTests;
import org.restcomm.connect.commons.annotations.WithInMinsTests;
import org.restcomm.connect.testsuite.NetworkPortAssigner;
import org.restcomm.connect.testsuite.WebArchiveUtil;
import org.restcomm.connect.testsuite.http.RestcommOrganizationsTool;
import org.restcomm.connect.testsuite.tools.MonitoringServiceTool;

import javax.servlet.sip.SipServletResponse;
import javax.sip.InvalidArgumentException;
import javax.sip.RequestEvent;
import javax.sip.address.SipURI;
import javax.sip.header.Header;
import javax.sip.message.Response;
import java.net.URL;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
//import org.restcomm.connect.telephony.Version;

/**
 * @author quintana.thomas@gmail.com (Thomas Quintana)
 * @author gvagenas@gmail.com (George Vagenas)
 */

@RunWith(Arquillian.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@Category(value={WithInMinsTests.class, ParallelClassTests.class})
public final class UserAgentManagerClientAlgorithmTest {
    private static Logger logger = Logger.getLogger(UserAgentManagerClientAlgorithmTest.class.getName());
    private static final String version = Version.getVersion();

    @ArquillianResource
    private Deployer deployer;
    @ArquillianResource
    URL deploymentUrl;

    private String adminAccountSid = "ACae6e420f425248d6a26948c17a9e2acf";
    private String adminAuthToken = "77f8c12cc7b8f8423e5c38b035249166";

    private static int mediaPort = NetworkPortAssigner.retrieveNextPortByFile();

    private static SipStackTool tool1;
    private SipStack sipStack;
    private SipPhone phone;
    private static String alicePort = String.valueOf(NetworkPortAssigner.retrieveNextPortByFile());
    private String aliceContact = "sip:alice@127.0.0.1:" + alicePort + ";transport=udp";

    private static SipStackTool tool2;
    private SipStack sipStack2;
    private SipPhone phone2;
    private static String bobPort = String.valueOf(NetworkPortAssigner.retrieveNextPortByFile());
    private String bobContact = "sip:bob@127.0.0.1:" + bobPort;

    private static SipStackTool tool3;
    private SipStack sipStack3;
    private SipPhone phone3;
    private static String alicePort3 = String.valueOf(NetworkPortAssigner.retrieveNextPortByFile());
    private String aliceContact3 = "sip:alice@127.0.0.1:" + alicePort3 + ";transport=udp;rc-id=7616";

    private static SipStackTool tool4;
    private SipStack sipStack4;
    private SipPhone phone4;
    private static String alicePort4 = String.valueOf(NetworkPortAssigner.retrieveNextPortByFile());
    private String aliceContact4 = "sip:alice@127.0.0.1:" + alicePort4;


    private static SipStackTool tool5;
    private SipStack sipStack5;
    private SipPhone phone5;
    private static String mariaPort5 = String.valueOf(NetworkPortAssigner.retrieveNextPortByFile());
    private String mariaContact5 = "sip:maria.test%40telestax.com@127.0.0.1:" + mariaPort5;

    private static SipStackTool tool6;
    private SipStack sipStack6;
    private SipPhone phone6;
    private static String alicePort6 = String.valueOf(NetworkPortAssigner.retrieveNextPortByFile());
    private String aliceContact6 = "sip:alice@testdomain2.restcomm.com:" + alicePort6;

    private static SipStackTool tool7;
    private SipStack sipStack7;
    private SipPhone phone7;
    private static String alicePort7 = String.valueOf(NetworkPortAssigner.retrieveNextPortByFile());
    private String aliceContact7 = "sip:alice@127.0.0.1:" + alicePort7;

    private static SipStackTool tool8;
    private SipStack sipStack8;
    private SipPhone closedPhone;
    private static String closedPort = String.valueOf(NetworkPortAssigner.retrieveNextPortByFile());
    private String closedContact = "sip:closed@127.0.0.1:" + closedPort;

    private static SipStackTool tool9;
    private SipStack sipStack9;
    private SipPhone suspendedPhone;
    private static String suspendedPort = String.valueOf(NetworkPortAssigner.retrieveNextPortByFile());
    private String suspendedContact = "sip:suspended@127.0.0.1:" + suspendedPort;

    private static SipStackTool tool10;
    private SipStack sipStack10;
    private SipPhone org1AlicePhone;
    private static String org1AlicePort = String.valueOf(NetworkPortAssigner.retrieveNextPortByFile());
    private String org1AliceContact = "sip:alice@org1.restcomm.com:" + suspendedPort;


    private static int restcommPort = 5080;
    private static int restcommHTTPPort = 8080;
    private static String restcommContact = "127.0.0.1:" + restcommPort;

    public UserAgentManagerClientAlgorithmTest () {
        super();
    }

    @BeforeClass
    public static void beforeClass() throws Exception {
        tool1 = new SipStackTool("UserAgentTest1");
        tool2 = new SipStackTool("UserAgentTest2");
        tool3 = new SipStackTool("UserAgentTest3");
        tool4 = new SipStackTool("UserAgentTest4");
        tool5 = new SipStackTool("UserAgentTest5");
        tool6 = new SipStackTool("UserAgentTest6");
        tool7 = new SipStackTool("UserAgentTest7");
        tool8 = new SipStackTool("UserAgentTest8");
        tool9 = new SipStackTool("UserAgentTest9");
        tool10 = new SipStackTool("UserAgentTest10");
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
        sipStack = tool1.initializeSipStack(SipStack.PROTOCOL_UDP, "127.0.0.1", alicePort, restcommContact);
        phone = sipStack.createSipPhone("127.0.0.1", SipStack.PROTOCOL_UDP, restcommPort, aliceContact);

        sipStack2 = tool2.initializeSipStack(SipStack.PROTOCOL_UDP, "127.0.0.1", bobPort, restcommContact);
        phone2 = sipStack2.createSipPhone("127.0.0.1", SipStack.PROTOCOL_UDP, restcommPort, bobContact);

        sipStack3 = tool3.initializeSipStack(SipStack.PROTOCOL_UDP, "127.0.0.1", alicePort3, restcommContact);
        phone3 = sipStack3.createSipPhone("127.0.0.1", SipStack.PROTOCOL_UDP, restcommPort, aliceContact3);

        sipStack4 = tool4.initializeSipStack(SipStack.PROTOCOL_TCP, "127.0.0.1", alicePort4, restcommContact);
        phone4 = sipStack4.createSipPhone("127.0.0.1", SipStack.PROTOCOL_TCP, restcommPort, aliceContact4);

        sipStack5 = tool5.initializeSipStack(SipStack.PROTOCOL_TCP, "127.0.0.1", mariaPort5, restcommContact);
        phone5 = sipStack5.createSipPhone("127.0.0.1", SipStack.PROTOCOL_TCP, restcommPort, mariaContact5);

        sipStack6 = tool6.initializeSipStack(SipStack.PROTOCOL_TCP, "127.0.0.1", alicePort6, restcommContact);
        phone6 = sipStack6.createSipPhone("127.0.0.1", SipStack.PROTOCOL_TCP, restcommPort, aliceContact6);

        sipStack7 = tool7.initializeSipStack(SipStack.PROTOCOL_TCP, "127.0.0.1", alicePort7, restcommContact);
        phone7 = sipStack7.createSipPhone("127.0.0.1", SipStack.PROTOCOL_TCP, restcommPort, aliceContact7);

        sipStack8 = tool8.initializeSipStack(SipStack.PROTOCOL_UDP, "127.0.0.1", closedPort, restcommContact);
        closedPhone = sipStack8.createSipPhone("127.0.0.1", SipStack.PROTOCOL_UDP, restcommPort, closedContact);

        sipStack9 = tool9.initializeSipStack(SipStack.PROTOCOL_UDP, "127.0.0.1", suspendedPort, restcommContact);
        suspendedPhone = sipStack9.createSipPhone("127.0.0.1", SipStack.PROTOCOL_UDP, restcommPort, suspendedContact);

        sipStack10 = tool10.initializeSipStack(SipStack.PROTOCOL_UDP, "127.0.0.1", org1AlicePort, restcommContact);
        org1AlicePhone = sipStack10.createSipPhone("127.0.0.1", SipStack.PROTOCOL_UDP, restcommPort, org1AliceContact);
    }

    @After
    public void after() throws Exception {
        if (phone != null) {
            phone.dispose();
        }
        if (sipStack != null) {
            sipStack.dispose();
        }
        if (phone2 != null) {
            phone2.dispose();
        }
        if (sipStack2 != null) {
            sipStack2.dispose();
        }
        if (phone3 != null) {
            phone3.dispose();
        }
        if (sipStack3 != null) {
            sipStack3.dispose();
        }
        if (phone4 != null) {
            phone4.dispose();
        }
        if (sipStack4 != null) {
            sipStack4.dispose();
        }

        if (phone5 != null) {
            phone5.dispose();
        }
        if (sipStack5 != null) {
            sipStack5.dispose();
        }

        if (phone6 != null) {
            phone6.dispose();
        }
        if (sipStack6 != null) {
            sipStack6.dispose();
        }

        if (phone7 != null) {
            phone7.dispose();
        }
        if (sipStack7 != null) {
            sipStack7.dispose();
        }

        if (closedPhone != null) {
            closedPhone.dispose();
        }
        if (sipStack8 != null) {
            sipStack8.dispose();
        }

        if (suspendedPhone != null) {
            suspendedPhone.dispose();
        }
        if (sipStack9 != null) {
            sipStack9.dispose();
        }

        if (org1AlicePhone != null) {
            org1AlicePhone.dispose();
        }
        if (sipStack10 != null) {
            sipStack10.dispose();
        }

    }

    @Test
    public void registerUserAgent() throws Exception {
        SipURI uri = sipStack.getAddressFactory().createSipURI(null, restcommContact);
        Credential c = new Credential("127.0.0.1","alice", "1234");
        phone.addUpdateCredential(c);
        assertTrue(phone.register(uri, "alice", "1234", "sip:127.0.0.1:" + alicePort, 3600, 3600));
        Thread.sleep(500);
        assertEquals(1, MonitoringServiceTool.getInstance().getRegisteredUsers(deploymentUrl.toString(),adminAccountSid, adminAuthToken));
        assertTrue(phone.unregister("sip:127.0.0.1:" + alicePort, 0));
        Thread.sleep(500);
        assertEquals(0, MonitoringServiceTool.getInstance().getRegisteredUsers(deploymentUrl.toString(),adminAccountSid, adminAuthToken));
    }

    @Test
    public void registerClosedClient() throws Exception {
        SipURI uri = sipStack8.getAddressFactory().createSipURI(null, restcommContact);
        Credential c = new Credential("127.0.0.1","closed", "1234");
        closedPhone.addUpdateCredential(c);
        assertFalse(closedPhone.register(uri, "closed", "1234", "sip:127.0.0.1:" + closedPort, 3600, 3600));
        Thread.sleep(500);
        assertEquals(Response.FORBIDDEN, closedPhone.getReturnCode());
        assertEquals(0, MonitoringServiceTool.getInstance().getRegisteredUsers(deploymentUrl.toString(),adminAccountSid, adminAuthToken));
//        assertTrue(closedPhone.unregister("sip:127.0.0.1:" + closedPort, 0));
//        Thread.sleep(500);
//        assertEquals(0, MonitoringServiceTool.getInstance().getRegisteredUsers(deploymentUrl.toString(),adminAccountSid, adminAuthToken));
    }

    @Test
    public void registerSuspendedClient() throws Exception {
        SipURI uri = sipStack9.getAddressFactory().createSipURI(null, restcommContact);
        Credential c = new Credential("127.0.0.1","suspended", "1234");
        suspendedPhone.addUpdateCredential(c);
        assertFalse(suspendedPhone.register(uri, "suspended", "1234", "sip:127.0.0.1:" + suspendedPort, 3600, 3600));
        Thread.sleep(500);
        assertEquals(Response.FORBIDDEN, suspendedPhone.getReturnCode());
        assertEquals(0, MonitoringServiceTool.getInstance().getRegisteredUsers(deploymentUrl.toString(),adminAccountSid, adminAuthToken));
//        assertTrue(suspendedPhone.unregister("sip:127.0.0.1:" + closedPort, 0));
//        Thread.sleep(500);
//        assertEquals(0, MonitoringServiceTool.getInstance().getRegisteredUsers(deploymentUrl.toString(),adminAccountSid, adminAuthToken));
    }

    @Test
    public void registerUserAgentWithTransport() throws Exception {
        SipURI uri = sipStack.getAddressFactory().createSipURI(null, restcommContact);
        Credential c = new Credential("127.0.0.1","alice", "1234");
        phone.addUpdateCredential(c);
        assertTrue(phone.register(uri, "alice", "1234", aliceContact, 3600, 3600));
        Thread.sleep(500);
        assertEquals(1, MonitoringServiceTool.getInstance().getRegisteredUsers(deploymentUrl.toString(),adminAccountSid, adminAuthToken));
        assertTrue(phone.unregister(aliceContact, 0));
        Thread.sleep(500);
        assertEquals(0, MonitoringServiceTool.getInstance().getRegisteredUsers(deploymentUrl.toString(),adminAccountSid, adminAuthToken));
    }

    @Test
    public void registerUserAgentWithSecureTransport() throws Exception {
        SipURI uri = sipStack4.getAddressFactory().createSipURI(null, restcommContact);
        uri.setSecure(true);
        Credential c = new Credential("127.0.0.1","alice", "1234");
        phone4.addUpdateCredential(c);
        assertTrue(phone4.register(uri, "alice", "1234", aliceContact4, 3600, 3600));
        Thread.sleep(500);
        assertEquals(1, MonitoringServiceTool.getInstance().getRegisteredUsers(deploymentUrl.toString(),adminAccountSid, adminAuthToken));
        assertTrue(phone4.unregister(aliceContact4, 0));
        Thread.sleep(500);
        assertEquals(0, MonitoringServiceTool.getInstance().getRegisteredUsers(deploymentUrl.toString(),adminAccountSid, adminAuthToken));
    }

    @Test
    public void registerUserAgentWithReRegister() throws Exception {
//        deployer.deploy("UserAgentTest");
        SipURI uri = sipStack.getAddressFactory().createSipURI(null, restcommContact);
        Credential c = new Credential("127.0.0.1","alice", "1234");
        phone.addUpdateCredential(c);
        assertTrue(phone.register(uri, "alice", "1234", "sip:127.0.0.1:" + alicePort, 3600, 3600));
        Thread.sleep(500);
        assertEquals(1, MonitoringServiceTool.getInstance().getRegisteredUsers(deploymentUrl.toString(),adminAccountSid, adminAuthToken));
        assertTrue(phone.register(uri, "alice", "1234", "sip:127.0.0.1:" + alicePort, 3600, 3600));
        Thread.sleep(500);
        assertEquals(1, MonitoringServiceTool.getInstance().getRegisteredUsers(deploymentUrl.toString(),adminAccountSid, adminAuthToken));
        assertTrue(phone.unregister("sip:127.0.0.1:" + alicePort, 0));
        Thread.sleep(500);
        assertEquals(0, MonitoringServiceTool.getInstance().getRegisteredUsers(deploymentUrl.toString(),adminAccountSid, adminAuthToken));
    }

    @Test
    public void registerUserAgentWithOptionsPing() throws ParseException, InterruptedException {
//        deployer.deploy("UserAgentTest");
        // Register the phone so we can get OPTIONS pings from RestComm.
        SipURI uri = sipStack.getAddressFactory().createSipURI(null, restcommContact);
        Credential c = new Credential("127.0.0.1","alice", "1234");
        phone.addUpdateCredential(c);
        assertTrue(phone.register(uri, "alice", "1234", aliceContact, 3600, 3600));
        Thread.sleep(500);
        assertEquals(1, MonitoringServiceTool.getInstance().getRegisteredUsers(deploymentUrl.toString(),adminAccountSid, adminAuthToken));
        Thread.sleep(15000);
        // Clean up (Unregister).

        assertTrue(phone.getLastReceivedOptionsRequest() != null);

        assertTrue(phone.unregister(aliceContact, 0));
    }

    @Test
    public void registerUserAgentWithExtraParamsAndOptionsPing() throws ParseException, InterruptedException {
//        deployer.deploy("UserAgentTest");
        // Register the phone so we can get OPTIONS pings from RestComm.
        SipURI uri = sipStack.getAddressFactory().createSipURI(null, restcommContact);
        Credential c = new Credential("127.0.0.1","alice", "1234");
        phone.addUpdateCredential(c);
        assertTrue(phone.register(uri, "alice", "1234", "sip:alice@127.0.0.1:" + alicePort + ";transport=udp;rc-id=7616", 3600, 3600));
        Thread.sleep(500);
        assertEquals(1, MonitoringServiceTool.getInstance().getRegisteredUsers(deploymentUrl.toString(),adminAccountSid, adminAuthToken));

        Thread.sleep(15000);

        assertTrue(phone.getLastReceivedOptionsRequest() != null);

        // Clean up (Unregister).
        assertTrue(phone.unregister(aliceContact, 0));
    }

    @Test
    public void registerUserAgentWithExceptionOnOptionsPing() throws ParseException, InterruptedException {
//        deployer.deploy("UserAgentTest");
        // Register the phone so we can get OPTIONS pings from RestComm.
        SipURI uri = sipStack.getAddressFactory().createSipURI(null, restcommContact);
        Credential c = new Credential("127.0.0.1","alice", "1234");
        phone.addUpdateCredential(c);

        phone.setAutoResponseOptionsRequests(false);

        assertTrue(phone.register(uri, "alice", "1234", aliceContact, 3600, 3600));
        Thread.sleep(500);
        assertEquals(1, MonitoringServiceTool.getInstance().getRegisteredUsers(deploymentUrl.toString(),adminAccountSid, adminAuthToken));

        Thread.sleep(50000);
        assertEquals(0, MonitoringServiceTool.getInstance().getRegisteredUsers(deploymentUrl.toString(),adminAccountSid, adminAuthToken));

        phone.setAutoResponseOptionsRequests(true);
    }

    @Test
    public void registerUserAgentWith503ErrorResponse() throws ParseException, InterruptedException, InvalidArgumentException {
//        deployer.deploy("UserAgentTest");
        // Register the phone so we can get OPTIONS pings from RestComm.
        SipURI uri = sipStack2.getAddressFactory().createSipURI(null, restcommContact);
        Credential c = new Credential("127.0.0.1","bob", "1234");
        phone2.addUpdateCredential(c);

        assertTrue(phone2.register(uri, "bob", "1234", bobContact, 3600, 3600));

        Thread.sleep(500);
        assertEquals(1, MonitoringServiceTool.getInstance().getRegisteredUsers(deploymentUrl.toString(),adminAccountSid, adminAuthToken));

        phone2.setAutoResponseOptionsRequests(false);
        phone2.setErrorRespondToOptions(SipServletResponse.SC_SERVICE_UNAVAILABLE);

        phone2.listenRequestMessage();
        RequestEvent request = phone2.waitRequest(10000);
        assertEquals(request.getRequest().getMethod(), SipRequest.OPTIONS);

        ArrayList<Header> additionalHeader = new ArrayList<Header>();
        Header reason = sipStack2.getHeaderFactory().createReasonHeader("udp", 503, "Destination not available");
        additionalHeader.add(reason);
        phone2.sendReply(request, 503, "Service unavailable", null, null, 3600, additionalHeader, null, null);

        Thread.sleep(50000);
        assertTrue(MonitoringServiceTool.getInstance().getRegisteredUsers(deploymentUrl.toString(),adminAccountSid, adminAuthToken)==0);
        phone2.setAutoResponseOptionsRequests(true);
    }

    @Test
    public void registerUserAgentWithExtraParamsAnd503ToOptionsPing() throws ParseException, InterruptedException, InvalidArgumentException {
//        deployer.deploy("UserAgentTest");
        // Register the phone so we can get OPTIONS pings from RestComm.
        SipURI uri = sipStack3.getAddressFactory().createSipURI(null, restcommContact);
        Credential c = new Credential("127.0.0.1","alice", "1234");
        phone3.addUpdateCredential(c);

        phone3.setAutoResponseOptionsRequests(false);
        phone3.setErrorRespondToOptions(SipServletResponse.SC_SERVICE_UNAVAILABLE);

        assertTrue(phone3.register(uri, "alice", "1234", aliceContact3, 3600, 3600));
        Thread.sleep(500);
        assertEquals(1,MonitoringServiceTool.getInstance().getRegisteredUsers(deploymentUrl.toString(),adminAccountSid, adminAuthToken));

        Thread.sleep(50000);
        assertEquals(0, MonitoringServiceTool.getInstance().getRegisteredUsers(deploymentUrl.toString(),adminAccountSid, adminAuthToken));

        phone3.setAutoResponseOptionsRequests(true);
    }

    @Test
    public void registerUserAgentWithExtraParamsAnd503ToOptionsPingNoTransport() throws ParseException, InterruptedException, InvalidArgumentException {
//        deployer.deploy("UserAgentTest");
        // Register the phone so we can get OPTIONS pings from RestComm.
        SipURI uri = sipStack3.getAddressFactory().createSipURI(null, restcommContact);
        Credential c = new Credential("127.0.0.1","alice", "1234");
        phone3.addUpdateCredential(c);

        phone3.setAutoResponseOptionsRequests(false);
        phone3.setErrorRespondToOptions(SipServletResponse.SC_SERVICE_UNAVAILABLE);

        assertTrue(phone3.register(uri, "alice", "1234", aliceContact3, 3600, 3600));
        Thread.sleep(500);
        assertEquals(1,MonitoringServiceTool.getInstance().getRegisteredUsers(deploymentUrl.toString(),adminAccountSid, adminAuthToken));

        Thread.sleep(50000);
        assertEquals(0, MonitoringServiceTool.getInstance().getRegisteredUsers(deploymentUrl.toString(),adminAccountSid, adminAuthToken));

        phone3.setAutoResponseOptionsRequests(true);
    }

    @Test
    public void registerUserAgentWithExtraParamsAnd408ToOptionsPing() throws ParseException, InterruptedException, InvalidArgumentException {
//        deployer.deploy("UserAgentTest");
        // Register the phone so we can get OPTIONS pings from RestComm.
        SipURI uri = sipStack3.getAddressFactory().createSipURI(null, restcommContact);
        Credential c = new Credential("127.0.0.1","alice", "1234");
        phone3.addUpdateCredential(c);

        phone3.setAutoResponseOptionsRequests(false);

        assertTrue(phone3.register(uri, "alice", "1234", aliceContact3, 3600, 3600));
        Thread.sleep(500);
        assertEquals(1, MonitoringServiceTool.getInstance().getRegisteredUsers(deploymentUrl.toString(),adminAccountSid, adminAuthToken));

        Thread.sleep(50000);
        assertEquals(0, MonitoringServiceTool.getInstance().getRegisteredUsers(deploymentUrl.toString(),adminAccountSid, adminAuthToken));

        phone3.setAutoResponseOptionsRequests(true);
    }

    @Test
    public void registerUserAgentWith408ErrorResponse() throws ParseException, InterruptedException, InvalidArgumentException {
//        deployer.deploy("UserAgentTest");
        // Register the phone so we can get OPTIONS pings from RestComm.
        SipURI uri = sipStack2.getAddressFactory().createSipURI(null, restcommContact);
        Credential c = new Credential("127.0.0.1","bob", "1234");
        phone2.addUpdateCredential(c);

        phone2.setAutoResponseOptionsRequests(false);

        assertTrue(phone2.register(uri, "bob", "1234", bobContact, 3600, 3600));
        Thread.sleep(2000);
        assertEquals(1, MonitoringServiceTool.getInstance().getRegisteredUsers(deploymentUrl.toString(),adminAccountSid, adminAuthToken));

        Thread.sleep(50000);
        assertEquals(0, MonitoringServiceTool.getInstance().getRegisteredUsers(deploymentUrl.toString(),adminAccountSid, adminAuthToken));

        phone2.setAutoResponseOptionsRequests(true);
    }

    @Test @Ignore @Category(UnstableTests.class)
    public void registerUserAgentWith486BusyErrorResponse() throws ParseException, InterruptedException, InvalidArgumentException {
//        deployer.deploy("UserAgentTest");
        // Register the phone so we can get OPTIONS pings from RestComm.
        SipURI uri = sipStack2.getAddressFactory().createSipURI(null, restcommContact);
        Credential c = new Credential("127.0.0.1","bob", "1234");
        phone2.addUpdateCredential(c);

        assertTrue(phone2.register(uri, "bob", "1234", bobContact, 3600, 3600));

        phone2.setAutoResponseOptionsRequests(false);
        phone2.setErrorRespondToOptions(SipServletResponse.SC_BUSY_HERE);

        Thread.sleep(2000);
        assertEquals(1, MonitoringServiceTool.getInstance().getRegisteredUsers(deploymentUrl.toString(),adminAccountSid, adminAuthToken));

        Thread.sleep(50000);
        assertEquals(0, MonitoringServiceTool.getInstance().getRegisteredUsers(deploymentUrl.toString(),adminAccountSid, adminAuthToken));
        phone2.setAutoResponseOptionsRequests(true);
    }


    /**
     * registerUserAgentWithAtTheRateSignInLogin
     * we should be able to register and remove registration on non-response to options
     * @throws ParseException
     * @throws InterruptedException
     * @throws InvalidArgumentException
     */
    @Test
    public void registerUserAgentWithAtTheRateSignInLogin() throws ParseException, InterruptedException, InvalidArgumentException {
        SipURI uri = sipStack5.getAddressFactory().createSipURI(null, restcommContact);
        Credential c = new Credential("127.0.0.1","maria.test@telestax.com", "1234");
        phone5.addUpdateCredential(c);

        phone5.setAutoResponseOptionsRequests(false);

        assertTrue(phone5.register(uri, "maria.test@telestax.com", "1234", mariaContact5, 3600, 3600));
        Thread.sleep(2000);

        //user should be registered successfully
        assertEquals(1, MonitoringServiceTool.getInstance().getRegisteredUsers(deploymentUrl.toString(),adminAccountSid, adminAuthToken));

        Thread.sleep(50000);
        assertEquals(0, MonitoringServiceTool.getInstance().getRegisteredUsers(deploymentUrl.toString(),adminAccountSid, adminAuthToken));
        phone5.setAutoResponseOptionsRequests(true);
    }

    /**
     * registerMultipleUsersWithSameLoginUnderDifferentOrganizations
     * https://github.com/RestComm/Restcomm-Connect/issues/2106
     * @throws ParseException
     * @throws InterruptedException
     * @throws InvalidArgumentException
     */
    @Test
    public void registerMultipleUsersWithSameLoginUnderDifferentOrganizations() throws ParseException, InterruptedException, InvalidArgumentException {

        //register alice of organization (testdomain2.restcomm.com)
        SipURI uri = sipStack6.getAddressFactory().createSipURI(null, restcommContact);
        Credential c = new Credential("testdomain2.restcomm.com","alice", "1234");
        phone6.addUpdateCredential(c);

        phone6.setAutoResponseOptionsRequests(false);

        assertTrue(phone6.register(uri, "alice", "1234", aliceContact6, 3600, 3600));
        Thread.sleep(500);
        //alice should be registered successfully
        assertEquals(1, MonitoringServiceTool.getInstance().getRegisteredUsers(deploymentUrl.toString(),adminAccountSid, adminAuthToken));

        //register another alice of organization (127.0.0.1)
        uri = sipStack7.getAddressFactory().createSipURI(null, restcommContact);
        c = new Credential("127.0.0.1","alice", "1234");
        phone7.addUpdateCredential(c);

        phone7.setAutoResponseOptionsRequests(false);

        assertTrue(phone7.register(uri, "alice", "1234", aliceContact7, 3600, 3600));
        Thread.sleep(500);

        //both users should be registered successfully
        int totalRegistrations = MonitoringServiceTool.getInstance().getRegisteredUsers(deploymentUrl.toString(),adminAccountSid, adminAuthToken);
        logger.info("Totatl Registrations: "+totalRegistrations);
        assertEquals(2, totalRegistrations);

        Thread.sleep(50000);
        assertEquals(0, MonitoringServiceTool.getInstance().getRegisteredUsers(deploymentUrl.toString(),adminAccountSid, adminAuthToken));

        phone6.setAutoResponseOptionsRequests(true);
        phone7.setAutoResponseOptionsRequests(true);
    }

    @Test @Category(FeatureAltTests.class)
    public void migrateClientsPasswordAndRegister() throws ParseException, InterruptedException, InvalidArgumentException {

        Gson gson = new Gson();
        String org1 = "OR11111111111111111111111111111111";

        //register alice of organization (org1.restcomm.com)
        SipURI uri = sipStack10.getAddressFactory().createSipURI(null, restcommContact);
        Credential c = new Credential("org1.restcomm.com","alice", "1234");
        org1AlicePhone.addUpdateCredential(c);

        assertTrue(org1AlicePhone.register(uri, "alice", "1234", org1AliceContact, 3600, 3600));
        Thread.sleep(500);
        //alice should be registered successfully
        assertEquals(1, MonitoringServiceTool.getInstance().getRegisteredUsers(deploymentUrl.toString(),adminAccountSid, adminAuthToken));

        Thread.sleep(500);

        assertTrue(org1AlicePhone.unregister(org1AliceContact, 3600));
        Thread.sleep(500);
        //alice should be registered successfully
        assertEquals(0, MonitoringServiceTool.getInstance().getRegisteredUsers(deploymentUrl.toString(),adminAccountSid, adminAuthToken));

        //Hash client's passwords of Org1
        JsonObject clientsMigratedJsonObj = RestcommOrganizationsTool.getInstance().migrateClientsOfOrganization(deploymentUrl.toString(), adminAccountSid, adminAuthToken, org1);
        assertNotNull(clientsMigratedJsonObj);

        Map<String,String> migratedClients = new HashMap<>();
        migratedClients = (Map<String,String>) gson.fromJson(clientsMigratedJsonObj, migratedClients.getClass());

        assertNotNull(migratedClients);
        assertEquals(4, migratedClients.size());

        assertTrue(org1AlicePhone.register(uri, "alice", "1234", org1AliceContact, 3600, 3600));
        Thread.sleep(500);
        //alice should be registered successfully
        assertEquals(1, MonitoringServiceTool.getInstance().getRegisteredUsers(deploymentUrl.toString(),adminAccountSid, adminAuthToken));

        Thread.sleep(500);

        assertTrue(org1AlicePhone.unregister(org1AliceContact, 3600));
        Thread.sleep(500);
        //alice should be registered successfully
        assertEquals(0, MonitoringServiceTool.getInstance().getRegisteredUsers(deploymentUrl.toString(),adminAccountSid, adminAuthToken));
    }

    @Deployment(name = "UserAgentTest", managed = true, testable = false)
    public static WebArchive createWebArchiveNoGw() {
        logger.info("Packaging Test App");
        reconfigurePorts();

        Map<String,String> replacements = new HashMap();
        //replace mediaport 2727
        replacements.put("2727", String.valueOf(mediaPort));
        replacements.put("8080", String.valueOf(restcommHTTPPort));
        replacements.put("5080", String.valueOf(restcommPort));
        replacements.put("5070", String.valueOf(alicePort));
        replacements.put("5071", String.valueOf(alicePort3));
        replacements.put("5072", String.valueOf(alicePort4));
        replacements.put("5073", String.valueOf(mariaPort5));
        replacements.put("5074", String.valueOf(alicePort6));
        replacements.put("5075", String.valueOf(alicePort7));

        replacements.put("5090", String.valueOf(bobPort));


        return WebArchiveUtil.createWebArchiveNoGw("restcomm_UserAgentManagerTest.xml",
                "restcomm.script_UserAgentTestClientAlgorithm",
                replacements);
    }
}
