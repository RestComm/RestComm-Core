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

package org.restcomm.connect.testsuite.telephony;

import static org.cafesip.sipunit.SipAssert.assertLastOperationSuccess;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.URL;
import java.text.ParseException;

import javax.sip.*;
import javax.sip.address.SipURI;
import javax.sip.message.Request;
import javax.sip.message.Response;

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
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.FixMethodOrder;
import org.junit.runners.MethodSorters;
import org.junit.runner.RunWith;
import org.restcomm.connect.commons.Version;
import org.restcomm.connect.commons.annotations.ParallelClassTests;
import org.restcomm.connect.testsuite.http.CreateClientsTool;
import org.restcomm.connect.testsuite.http.RestcommCallsTool;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.experimental.categories.Category;
import org.restcomm.connect.commons.annotations.UnstableTests;
import org.restcomm.connect.testsuite.tools.MonitoringServiceTool;

/**
 * Client registration Test.
 * Maria client is using two sip clients to register
 *
 * @author <a href="mailto:gvagenas@gmail.com">gvagenas</a>
 */
@RunWith(Arquillian.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@Category(ParallelClassTests.class)
public class RegisterClientTest {

    private static final String version = Version.getVersion();

    private static final byte[] bytes = new byte[]{118, 61, 48, 13, 10, 111, 61, 117, 115, 101, 114, 49, 32, 53, 51, 54, 53,
            53, 55, 54, 53, 32, 50, 51, 53, 51, 54, 56, 55, 54, 51, 55, 32, 73, 78, 32, 73, 80, 52, 32, 49, 50, 55, 46, 48, 46,
            48, 46, 49, 13, 10, 115, 61, 45, 13, 10, 99, 61, 73, 78, 32, 73, 80, 52, 32, 49, 50, 55, 46, 48, 46, 48, 46, 49,
            13, 10, 116, 61, 48, 32, 48, 13, 10, 109, 61, 97, 117, 100, 105, 111, 32, 54, 48, 48, 48, 32, 82, 84, 80, 47, 65,
            86, 80, 32, 48, 13, 10, 97, 61, 114, 116, 112, 109, 97, 112, 58, 48, 32, 80, 67, 77, 85, 47, 56, 48, 48, 48, 13, 10};
    private static final String body = new String(bytes);

    @ArquillianResource
    private Deployer deployer;
    @ArquillianResource
    URL deploymentUrl;

    private static SipStackTool tool1;
    private static SipStackTool tool2;
    private static SipStackTool tool3;

    // Maria is a Restcomm Client **without** VoiceURL. This Restcomm Client can dial anything.
    private SipStack mariaSipStack;
    private SipPhone mariaPhone;
    private String mariaContact = "sip:maria@127.0.0.1:5092";
    private String mariaRestcommClientSid;

    // Dimitris is a Restcomm Client **without** VoiceURL. This Restcomm Client can dial anything.
    private SipStack mariaSipStack2;
    private SipPhone mariaPhone2;
    private String mariaContact2 = "sip:maria@127.0.0.1:5093";

    private String mariaRestcommContact = "sip:maria@127.0.0.1:5080";

    // Alice is a Restcomm Client with VoiceURL. This Restcomm Client can register with Restcomm and whatever will dial the RCML
    // of the VoiceURL will be executed.
    private SipStack georgeSipStack;
    private SipPhone georgePhone;
    private String georgeContact = "sip:george@127.0.0.1:5091";
    private String georgeRestcommClientSid;

    private String adminAccountSid = "ACae6e420f425248d6a26948c17a9e2acf";
    private String adminAuthToken = "77f8c12cc7b8f8423e5c38b035249166";

    private String strongPassword = "Qwert12345";

    @BeforeClass
    public static void beforeClass() throws Exception {
        tool1 = new SipStackTool("RegisterClientTest1");
        tool2 = new SipStackTool("RegisterClientTest2");
        tool3 = new SipStackTool("RegisterClientTest3");
    }

    @Before
    public void before() throws Exception {

        georgeSipStack = tool2.initializeSipStack(SipStack.PROTOCOL_UDP, "127.0.0.1", "5091", "127.0.0.1:5080");
        georgePhone = georgeSipStack.createSipPhone("127.0.0.1", SipStack.PROTOCOL_UDP, 5080, georgeContact);

        mariaSipStack = tool1.initializeSipStack(SipStack.PROTOCOL_UDP, "127.0.0.1", "5092", "127.0.0.1:5080");
        mariaPhone = mariaSipStack.createSipPhone("127.0.0.1", SipStack.PROTOCOL_UDP, 5080, mariaContact);

        mariaSipStack2 = tool3.initializeSipStack(SipStack.PROTOCOL_UDP, "127.0.0.1", "5093", "127.0.0.1:5080");
        mariaPhone2 = mariaSipStack2.createSipPhone("127.0.0.1", SipStack.PROTOCOL_UDP, 5080, mariaContact2);

        mariaRestcommClientSid = CreateClientsTool.getInstance().createClient(deploymentUrl.toString(), "maria", strongPassword, null);
        georgeRestcommClientSid = CreateClientsTool.getInstance().createClient(deploymentUrl.toString(), "george", strongPassword, null);

    }

    @After
    public void after() throws Exception {
        if (mariaPhone != null) {
            mariaPhone.dispose();
        }
        if (mariaSipStack != null) {
            mariaSipStack.dispose();
        }

        if (georgeSipStack != null) {
            georgeSipStack.dispose();
        }
        if (georgePhone != null) {
            georgePhone.dispose();
        }

        if (mariaSipStack2 != null) {
            mariaSipStack2.dispose();
        }
        if (mariaPhone2 != null) {
            mariaPhone2.dispose();
        }

        Thread.sleep(2000);
    }

    @Test
    @Category(value={UnstableTests.class})
    public void testRegisterClients() throws ParseException, InterruptedException {

        assertNotNull(mariaRestcommClientSid);
        assertNotNull(georgeRestcommClientSid);

        SipURI uri = georgeSipStack.getAddressFactory().createSipURI(null, "127.0.0.1:5080");

        assertTrue(georgePhone.register(uri, "george", strongPassword, georgeContact, 3600, 3600));
        assertTrue(mariaPhone.register(uri, "maria", strongPassword, mariaContact, 3600, 3600));
        assertTrue(mariaPhone2.register(uri, "maria", strongPassword, mariaContact2, 3600, 3600));

        Thread.sleep(1000);

        assertTrue(georgePhone.unregister(georgeContact, 0));
        assertTrue(mariaPhone.unregister(mariaContact, 0));
        assertTrue(mariaPhone2.unregister(mariaContact2, 0));
    }

    @Test
    @Category(value={UnstableTests.class})
    public void testRegisterClientAndRemoveItAfterNoResponseToOptions() throws ParseException, InterruptedException, SipException, InvalidArgumentException, IOException {
        assertNotNull(georgeRestcommClientSid);
        SipURI uri = georgeSipStack.getAddressFactory().createSipURI(null, "127.0.0.1:5080");
        assertTrue(georgePhone.register(uri, "george", strongPassword, georgeContact, 3600, 3600));

        Credential c = new Credential("127.0.0.1", "george", strongPassword);
        georgePhone.addUpdateCredential(c);

        assertTrue(MonitoringServiceTool.getInstance().getRegisteredUsers(deploymentUrl.toString(),adminAccountSid,adminAuthToken)==1);

        Thread.sleep(1000);
        georgePhone.listenRequestMessage();
        RequestEvent reqEvent = georgePhone.waitRequest(65000);
        assertTrue(reqEvent != null);
        assertTrue(reqEvent.getRequest().getMethod().equals(Request.OPTIONS));

        Thread.sleep(40000);

//        assertEquals(0, MonitoringServiceTool.getInstance().getRegisteredUsers(deploymentUrl.toString(),adminAccountSid,adminAuthToken));

    }


    @Test
    @Category(value={UnstableTests.class})
    public void testGeorgeCallMaria() throws ParseException, InterruptedException {

        assertNotNull(mariaRestcommClientSid);
        assertNotNull(georgeRestcommClientSid);

        SipURI uri = mariaSipStack.getAddressFactory().createSipURI(null, "127.0.0.1:5080");

        assertTrue(georgePhone.register(uri, "george", strongPassword, georgeContact, 3600, 3600));
        Thread.sleep(3000);
        assertTrue(mariaPhone.register(uri, "maria", strongPassword, mariaContact, 3600, 3600));
        Thread.sleep(3000);
        assertTrue(mariaPhone2.register(uri, "maria", strongPassword, mariaContact2, 3600, 3600));
        Thread.sleep(3000);

        Credential c = new Credential("127.0.0.1", "george", strongPassword);
        georgePhone.addUpdateCredential(c);

        Credential c2 = new Credential("127.0.0.1", "maria", strongPassword);
        mariaPhone.addUpdateCredential(c2);

        Credential c3 = new Credential("127.0.0.1", "maria", strongPassword);
        mariaPhone2.addUpdateCredential(c3);

        Thread.sleep(1000);

        final SipCall mariaCall_1 = mariaPhone.createSipCall();
        mariaCall_1.listenForIncomingCall();

        final SipCall mariaCall_2 = mariaPhone2.createSipCall();
        mariaCall_2.listenForIncomingCall();

        // Alice initiates a call to Maria
        final SipCall georgeCall = georgePhone.createSipCall();
        georgeCall.initiateOutgoingCall(georgeContact, mariaRestcommContact, null, body, "application", "sdp", null, null);
        assertLastOperationSuccess(georgeCall);
        assertTrue(georgeCall.waitForAuthorisation(3000));

        //According to issue 106: https://telestax.atlassian.net/browse/RESTCOMM-106
        //Restcomm will only use the last REGISTER address
        //Last REGISTRATION was from Maria-2
        assertTrue(mariaCall_2.waitForIncomingCall(3000));
        assertTrue(mariaCall_2.sendIncomingCallResponse(100, "Trying-Maria-2", 1800));
        assertTrue(mariaCall_2.sendIncomingCallResponse(180, "Ringing-Maria-2", 1800));
        String receivedBody = new String(mariaCall_2.getLastReceivedRequest().getRawContent());
        assertTrue(mariaCall_2.sendIncomingCallResponse(Response.OK, "OK-Maria-2", 3600, receivedBody, "application", "sdp", null,
                null));

        assertTrue(!mariaCall_1.waitForIncomingCall(3000));


        assertTrue(georgeCall.waitOutgoingCallResponse(5 * 1000));
        int georgeResponse = georgeCall.getLastReceivedResponse().getStatusCode();
        assertTrue(georgeResponse == Response.TRYING || georgeResponse == Response.RINGING);

        Dialog georgeDialog = null;

        if (georgeResponse == Response.TRYING) {
            assertTrue(georgeCall.waitOutgoingCallResponse(5 * 1000));
            assertEquals(Response.RINGING, georgeCall.getLastReceivedResponse().getStatusCode());
            georgeDialog = georgeCall.getDialog();
        }

        assertTrue(georgeCall.waitOutgoingCallResponse(5 * 1000));
        assertEquals(Response.OK, georgeCall.getLastReceivedResponse().getStatusCode());
        assertTrue(georgeCall.sendInviteOkAck());
        assertTrue(georgeCall.getDialog().equals(georgeDialog));
        assertTrue(georgeCall.getDialog().equals(georgeDialog));

        assertTrue(!(georgeCall.getLastReceivedResponse().getStatusCode() >= 400));

//        Thread.sleep(3000);
//        assertTrue(georgeCall.disconnect());
//
//        assertTrue(mariaCall_2.waitForDisconnect(5 * 1000));
//        assertTrue(mariaCall_2.respondToDisconnect());

        //Check CDR
        JsonObject cdrs = RestcommCallsTool.getInstance().getCalls(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
        assertNotNull(cdrs);
        JsonArray cdrsArray = cdrs.get("calls").getAsJsonArray();
        assertTrue(cdrsArray.size() == 1);

    }

    @Deployment(name = "RegisterClientTest", managed = true, testable = false)
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
        archive.addAsWebInfResource("sip.xml");
        archive.addAsWebInfResource("web.xml");
        archive.addAsWebInfResource("restcomm.xml", "conf/restcomm.xml");
        archive.addAsWebInfResource("restcomm.script_dialTest", "data/hsql/restcomm.script");
        archive.addAsWebResource("dial-conference-entry.xml");
        archive.addAsWebResource("dial-fork-entry.xml");
        archive.addAsWebResource("dial-uri-entry.xml");
        archive.addAsWebResource("dial-client-entry.xml");
        archive.addAsWebResource("dial-number-entry.xml");
        return archive;
    }
}
