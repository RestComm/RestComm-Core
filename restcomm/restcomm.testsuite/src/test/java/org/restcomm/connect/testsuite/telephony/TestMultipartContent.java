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
import static org.junit.Assert.assertTrue;

import java.net.URL;

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
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.FixMethodOrder;
import org.junit.runners.MethodSorters;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.restcomm.connect.commons.Version;
import org.restcomm.connect.commons.annotations.SequentialClassTests;
import org.restcomm.connect.commons.annotations.WithInMinsTests;

/**
 * Test for Multipart Content type. UFone Issue 31380.
 * RESTCOMM-330 https://telestax.atlassian.net/browse/RESTCOMM-330
 *
 * @author <a href="mailto:gvagenas@gmail.com">gvagenas</a>
 *
 */
@RunWith(Arquillian.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@Category(SequentialClassTests.class)
public class TestMultipartContent {

    private static Logger logger = Logger.getLogger(TestMultipartContent.class);
    private static final String version = Version.getVersion();

    private static final String multipartBody = "--uniqueBoundary \n"
            + "Content-Type: application/sdp \n"
            + "Content-Disposition: session;handling=required \n"
            + "\n"
            + "v=0\n"
            + "o=CiscoSystemsSIP-GW-UserAgent 7968 329 IN IP4 172.16.30.13\n"
            + "s=SIP Call\n"
            + "c=IN IP4 172.16.30.13\n"
            + "t=0 0\n"
            + "m=audio 30086 RTP/AVP 0 18 101\n"
            + "c=IN IP4 172.16.30.13\n"
            + "a=rtpmap:0 PCMU/8000\n"
            + "a=rtpmap:18 G729/8000\n"
            + "a=fmtp:18 annexb=no\n"
            + "a=rtpmap:101 telephone-event/8000\n"
            + "a=fmtp:101 0-16\n"
            + "\n"
            +"--uniqueBoundary\n"
            +"Content-Type: application/x-q931\n"
            +"Content-Disposition: signal;handling=optional\n"
            +"Content-Length: 39\n"
            +"\n"
            +"�L�������l!�3335500057p�1330\n"
            +"\n"
            +"--uniqueBoundary\n"
            +"Content-Type: application/gtd\n"
            +"Content-Disposition: signal;handling=optional\n"
            +"\n"
            +"IAM,\n"
            +"PRN,isdn*,,NET5*,\n"
            +"USI,rate,c,s,c,1\n"
            +"USI,lay1,alaw\n"
            +"TMR,00\n"
            +"CPN,02,,1,1330\n"
            +"CGN,04,,1,y,4,3335500057\n"
            +"CPC,09 \n"
            +"FCI,,,,,,,y, \n"
            +"GCI,f2ea4c5e9fd211e3ae4b5057a8ed5300\n"
            +"\n"
            +"--uniqueBoundary-- \n";

    @ArquillianResource
    private Deployer deployer;
    @ArquillianResource
    URL deploymentUrl;


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

    // Henrique is a simple SIP Client. Will not register with Restcomm
    private SipStack henriqueSipStack;
    private SipPhone henriquePhone;
    private String henriqueContact = "sip:henrique@127.0.0.1:5092";

    // George is a simple SIP Client. Will not register with Restcomm
    private SipStack georgeSipStack;
    private SipPhone georgePhone;
    private String georgeContact = "sip:+131313@127.0.0.1:5070";

    private String dialClientWithActionUrl = "sip:+12223334455@127.0.0.1:5080";
    private String dialConf = "sip:+12223334451@127.0.0.1:5080";

    private String adminAccountSid = "ACae6e420f425248d6a26948c17a9e2acf";
    private String adminAuthToken = "77f8c12cc7b8f8423e5c38b035249166";

    @BeforeClass
    public static void beforeClass() throws Exception {
        tool1 = new SipStackTool("DialActionTest1");
        tool2 = new SipStackTool("DialActionTest2");
        tool3 = new SipStackTool("DialActionTest3");
        tool4 = new SipStackTool("DialActionTest4");
    }

    @Before
    public void before() throws Exception {
        bobSipStack = tool1.initializeSipStack(SipStack.PROTOCOL_UDP, "127.0.0.1", "5090", "127.0.0.1:5080");
        bobPhone = bobSipStack.createSipPhone("127.0.0.1", SipStack.PROTOCOL_UDP, 5080, bobContact);

        aliceSipStack = tool2.initializeSipStack(SipStack.PROTOCOL_UDP, "127.0.0.1", "5091", "127.0.0.1:5080");
        alicePhone = aliceSipStack.createSipPhone("127.0.0.1", SipStack.PROTOCOL_UDP, 5080, aliceContact);

        henriqueSipStack = tool3.initializeSipStack(SipStack.PROTOCOL_UDP, "127.0.0.1", "5092", "127.0.0.1:5080");
        henriquePhone = henriqueSipStack.createSipPhone("127.0.0.1", SipStack.PROTOCOL_UDP, 5080, henriqueContact);

        georgeSipStack = tool4.initializeSipStack(SipStack.PROTOCOL_UDP, "127.0.0.1", "5070", "127.0.0.1:5080");
        georgePhone = georgeSipStack.createSipPhone("127.0.0.1", SipStack.PROTOCOL_UDP, 5080, georgeContact);
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

        if (henriqueSipStack != null) {
            henriqueSipStack.dispose();
        }
        if (henriquePhone != null) {
            henriquePhone.dispose();
        }

        if (georgePhone != null) {
            georgePhone.dispose();
        }
        if (georgeSipStack != null) {
            georgeSipStack.dispose();
        }
    }

    @Test
    public synchronized void testDialConference() throws InterruptedException {

        Credential c = new Credential("127.0.0.1", "bob", "1234");
        bobPhone.addUpdateCredential(c);

        final SipCall bobCall = bobPhone.createSipCall();
        bobCall.initiateOutgoingCall(bobContact, dialConf, null, multipartBody, "multipart", "mixed;boundary=uniqueBoundary", null, null);
        assertLastOperationSuccess(bobCall);
        assertTrue(bobCall.waitForAuthorisation(5000));
        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        int responseBob = bobCall.getLastReceivedResponse().getStatusCode();
        assertTrue(responseBob == Response.TRYING || responseBob == Response.RINGING);

        if (responseBob == Response.TRYING) {
            assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
            assertEquals(Response.RINGING, bobCall.getLastReceivedResponse().getStatusCode());
        }

        assertTrue(bobCall.waitOutgoingCallResponse(5 * 1000));
        assertEquals(Response.OK, bobCall.getLastReceivedResponse().getStatusCode());
        bobCall.sendInviteOkAck();
        assertTrue(!(bobCall.getLastReceivedResponse().getStatusCode() >= 400));

        // George calls to the conference
        final SipCall georgeCall = georgePhone.createSipCall();
        georgeCall.initiateOutgoingCall(georgeContact, dialConf, null, multipartBody, "multipart", "mixed;boundary=uniqueBoundary", null, null);
        assertLastOperationSuccess(georgeCall);
        assertTrue(georgeCall.waitOutgoingCallResponse(5 * 1000));
        int responseGeorge = georgeCall.getLastReceivedResponse().getStatusCode();
        assertTrue(responseGeorge == Response.TRYING || responseGeorge == Response.RINGING);

        if (responseGeorge == Response.TRYING) {
            assertTrue(georgeCall.waitOutgoingCallResponse(5 * 1000));
            assertEquals(Response.RINGING, georgeCall.getLastReceivedResponse().getStatusCode());
        }

        assertTrue(georgeCall.waitOutgoingCallResponse(5 * 1000));
        assertEquals(Response.OK, georgeCall.getLastReceivedResponse().getStatusCode());
        georgeCall.sendInviteOkAck();
        assertTrue(!(georgeCall.getLastReceivedResponse().getStatusCode() >= 400));

        // Wait for the media to play and the call to hangup.
        bobCall.listenForDisconnect();
        georgeCall.listenForDisconnect();

        // Start a new thread for george to wait disconnect
        new Thread(new Runnable() {
            @Override
            public void run() {
                assertTrue(georgeCall.waitForDisconnect(30 * 1000));
            }
        }).start();

        // Start a new thread for bob to wait disconnect
        new Thread(new Runnable() {
            @Override
            public void run() {
                assertTrue(bobCall.waitForDisconnect(30 * 1000));
            }
        }).start();

        // assertTrue(bobCall.waitForDisconnect(30 * 1000));

        try {
            Thread.sleep(10 * 1000);
        } catch (final InterruptedException exception) {
            exception.printStackTrace();
        }
    }
    @Deployment(name = "TestMultipartContent", managed = true, testable = false)
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
        archive.addAsWebInfResource("restcomm.script_dialTest", "data/hsql/restcomm.script");
        archive.addAsWebResource("dial-conference-entry.xml");
        archive.addPackage("org.restcomm.connect.telephony.RestResources");
        logger.info("Packaged Test App");
        return archive;
    }


}
