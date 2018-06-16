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

import java.util.List;

import javax.sip.message.Response;

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
import org.junit.Ignore;
import org.junit.Test;
import org.junit.FixMethodOrder;
import org.junit.runners.MethodSorters;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.restcomm.connect.commons.Version;
import org.restcomm.connect.commons.annotations.SequentialClassTests;
//import org.restcomm.connect.telephony.Version;

/**
 * @author quintana.thomas@gmail.com (Thomas Quintana)
 */
@Ignore
@RunWith(Arquillian.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@Category(value={SequentialClassTests.class})
public final class CallTest {
    private static final String version = Version.getVersion();
    private static final byte[] bytes = new byte[] { 118, 61, 48, 13, 10, 111, 61, 117, 115, 101, 114, 49, 32, 53, 51, 54, 53,
            53, 55, 54, 53, 32, 50, 51, 53, 51, 54, 56, 55, 54, 51, 55, 32, 73, 78, 32, 73, 80, 52, 32, 49, 50, 55, 46, 48, 46,
            48, 46, 49, 13, 10, 115, 61, 45, 13, 10, 99, 61, 73, 78, 32, 73, 80, 52, 32, 49, 50, 55, 46, 48, 46, 48, 46, 49,
            13, 10, 116, 61, 48, 32, 48, 13, 10, 109, 61, 97, 117, 100, 105, 111, 32, 54, 48, 48, 48, 32, 82, 84, 80, 47, 65,
            86, 80, 32, 48, 13, 10, 97, 61, 114, 116, 112, 109, 97, 112, 58, 48, 32, 80, 67, 77, 85, 47, 56, 48, 48, 48, 13, 10 };
    private static final String body = new String(bytes);

    @ArquillianResource
    private Deployer deployer;

    private static SipStackTool tool;
    private SipStack receiver;
    private SipPhone phone;
    private SipPhone phone2;

    public CallTest() {
        super();
    }

    @BeforeClass
    public static void beforeClass() throws Exception {
        tool = new SipStackTool("CallTest");
    }

    @Before
    public void before() throws Exception {
        receiver = tool.initializeSipStack(SipStack.PROTOCOL_UDP, "127.0.0.1", "5070", "127.0.0.1:5080");
        phone = receiver.createSipPhone("127.0.0.1", SipStack.PROTOCOL_UDP, 5080, "sip:+17778889999@127.0.0.1:5070");
        phone2 = receiver.createSipPhone("127.0.0.1", SipStack.PROTOCOL_UDP, 5080, "sip:+17778889998@127.0.0.1:5070");
    }

    @After
    public void after() throws Exception {
        if (phone != null) {
            phone.dispose();
        }
        if (phone2 != null) {
            phone2.dispose();
        }
        if (receiver != null) {
            receiver.dispose();
        }
        deployer.undeploy("CallTest");
    }

    @Ignore
    @Test
    public synchronized void testInboundRedirectAndSms() {
        deployer.deploy("CallTest");
        phone.setLoopback(true);
        final SipCall call = phone.createSipCall();
        call.initiateOutgoingCall("sip:+17778889999@127.0.0.1:5070", "sip:+12223334444@127.0.0.1:5080", null, body,
                "application", "sdp", null, null);
        assertLastOperationSuccess(call);
        assertTrue(call.waitOutgoingCallResponse(5 * 1000));
        final int response = call.getLastReceivedResponse().getStatusCode();
        assertTrue(response == Response.TRYING || response == Response.RINGING);
        if (response == Response.TRYING) {
            assertTrue(call.waitOutgoingCallResponse(5 * 1000));
            assertEquals(Response.RINGING, call.getLastReceivedResponse().getStatusCode());
        }
        assertTrue(call.waitOutgoingCallResponse(5 * 1000));
        assertEquals(Response.OK, call.getLastReceivedResponse().getStatusCode());
        call.sendInviteOkAck();
        // Wait for an sms.
        final SipCall receiver = phone.createSipCall();
        phone.listenRequestMessage();
        assertTrue(receiver.waitForMessage(10 * 1000));
        receiver.sendMessageResponse(202, "Accepted", -1);
        final List<String> messages = receiver.getAllReceivedMessagesContent();
        assertTrue(messages.size() > 0);
        assertTrue(messages.get(0).equals("Hello World!"));
        // HangUp the call.
        assertTrue(!(call.getLastReceivedResponse().getStatusCode() >= 400));
        assertTrue(call.waitForDisconnect(5 * 1000));
        try {
            Thread.sleep(10 * 1000);
        } catch (final InterruptedException exception) {
            exception.printStackTrace();
        }
    }

    @Ignore
    @Test
    public synchronized void testPauseRejectBusy() {
        deployer.deploy("CallTest");
        phone.setLoopback(true);
        final SipCall call = phone.createSipCall();
        call.initiateOutgoingCall("sip:+17778889999@127.0.0.1:5070", "sip:+12223334445@127.0.0.1:5080", null, body,
                "application", "sdp", null, null);
        assertLastOperationSuccess(call);
        assertTrue(call.waitOutgoingCallResponse(5 * 1000));
        final int response = call.getLastReceivedResponse().getStatusCode();
        assertTrue(response == Response.TRYING || response == Response.RINGING);
        if (response == Response.TRYING) {
            assertTrue(call.waitOutgoingCallResponse(5 * 1000));
            assertEquals(Response.RINGING, call.getLastReceivedResponse().getStatusCode());
        }
        assertTrue(call.waitOutgoingCallResponse(5 * 1000));
        assertEquals(Response.BUSY_HERE, call.getLastReceivedResponse().getStatusCode());
    }

    @Ignore
    @Test
    public synchronized void testPauseRejectRejected() throws InterruptedException {
        deployer.deploy("CallTest");
        phone.setLoopback(true);
        final SipCall call = phone.createSipCall();
        call.initiateOutgoingCall("sip:+17778889999@127.0.0.1:5070", "sip:+12223334446@127.0.0.1:5080", null, body,
                "application", "sdp", null, null);
        assertLastOperationSuccess(call);
        assertTrue(call.waitOutgoingCallResponse(5 * 1000));
        final int response = call.getLastReceivedResponse().getStatusCode();
        assertTrue(response == Response.TRYING || response == Response.RINGING);
        if (response == Response.TRYING) {
            assertTrue(call.waitOutgoingCallResponse(5 * 1000));
            assertEquals(Response.RINGING, call.getLastReceivedResponse().getStatusCode());
        }
        assertTrue(call.waitOutgoingCallResponse(5 * 1000));
        assertEquals(Response.OK, call.getLastReceivedResponse().getStatusCode());
        call.sendInviteOkAck();
        assertTrue(!(call.getLastReceivedResponse().getStatusCode() >= 400));
        // Wait for the media to play and the call to hangup.
        assertTrue(call.waitForDisconnect(10 * 1000));
        try {
            Thread.sleep(10 * 1000);
        } catch (final InterruptedException exception) {
            exception.printStackTrace();
        }
    }

    @Ignore
    @Test
    public synchronized void testPlaySay() throws InterruptedException {
        deployer.deploy("CallTest");
        phone.setLoopback(true);
        final SipCall call = phone.createSipCall();
        call.initiateOutgoingCall("sip:+17778889999@127.0.0.1:5070", "sip:+12223334447@127.0.0.1:5080", null, body,
                "application", "sdp", null, null);
        assertLastOperationSuccess(call);
        assertTrue(call.waitOutgoingCallResponse(5 * 1000));
        final int response = call.getLastReceivedResponse().getStatusCode();
        assertTrue(response == Response.TRYING || response == Response.RINGING);
        if (response == Response.TRYING) {
            assertTrue(call.waitOutgoingCallResponse(5 * 1000));
            assertEquals(Response.RINGING, call.getLastReceivedResponse().getStatusCode());
        }
        assertTrue(call.waitOutgoingCallResponse(5 * 1000));
        assertEquals(Response.OK, call.getLastReceivedResponse().getStatusCode());
        call.sendInviteOkAck();
        assertTrue(!(call.getLastReceivedResponse().getStatusCode() >= 400));
        // Wait for the media to play and the call to hangup.
        assertTrue(call.waitForDisconnect(10 * 1000));
        try {
            Thread.sleep(10 * 1000);
        } catch (final InterruptedException exception) {
            exception.printStackTrace();
        }
    }

    @Ignore
    @Test
    public synchronized void testRecord() throws InterruptedException {
        deployer.deploy("CallTest");
        phone.setLoopback(true);
        final SipCall call = phone.createSipCall();
        call.initiateOutgoingCall("sip:+17778889999@127.0.0.1:5070", "sip:+12223334448@127.0.0.1:5080", null, body,
                "application", "sdp", null, null);
        assertLastOperationSuccess(call);
        assertTrue(call.waitOutgoingCallResponse(5 * 1000));
        final int response = call.getLastReceivedResponse().getStatusCode();
        assertTrue(response == Response.TRYING || response == Response.RINGING);
        if (response == Response.TRYING) {
            assertTrue(call.waitOutgoingCallResponse(5 * 1000));
            assertEquals(Response.RINGING, call.getLastReceivedResponse().getStatusCode());
        }
        assertTrue(call.waitOutgoingCallResponse(5 * 1000));
        assertEquals(Response.OK, call.getLastReceivedResponse().getStatusCode());
        call.sendInviteOkAck();
        assertTrue(!(call.getLastReceivedResponse().getStatusCode() >= 400));
        // Wait for the media to play and the call to hangup.
        assertTrue(call.waitForDisconnect(60 * 1000));
        try {
            Thread.sleep(10 * 1000);
        } catch (final InterruptedException exception) {
            exception.printStackTrace();
        }
    }

    @Ignore
    @Test
    public synchronized void testFax() throws InterruptedException {
        deployer.deploy("CallTest");
        phone.setLoopback(true);
        final SipCall call = phone.createSipCall();
        call.initiateOutgoingCall("sip:+17778889999@127.0.0.1:5070", "sip:+12223334449@127.0.0.1:5080", null, body,
                "application", "sdp", null, null);
        assertLastOperationSuccess(call);
        assertTrue(call.waitOutgoingCallResponse(5 * 1000));
        final int response = call.getLastReceivedResponse().getStatusCode();
        assertTrue(response == Response.TRYING || response == Response.RINGING);
        if (response == Response.TRYING) {
            assertTrue(call.waitOutgoingCallResponse(5 * 1000));
            assertEquals(Response.RINGING, call.getLastReceivedResponse().getStatusCode());
        }
        assertTrue(call.waitOutgoingCallResponse(5 * 1000));
        assertEquals(Response.OK, call.getLastReceivedResponse().getStatusCode());
        call.sendInviteOkAck();
        assertTrue(!(call.getLastReceivedResponse().getStatusCode() >= 400));
        // Wait for the media to play and the call to hangup.
        assertTrue(call.waitForDisconnect(10 * 1000));
        try {
            Thread.sleep(10 * 1000);
        } catch (final InterruptedException exception) {
            exception.printStackTrace();
        }
    }

    @Ignore
    @Test
    public synchronized void testGather() throws InterruptedException {
        deployer.deploy("CallTest");
        phone.setLoopback(true);
        final SipCall call = phone.createSipCall();
        call.initiateOutgoingCall("sip:+17778889999@127.0.0.1:5070", "sip:+12223334450@127.0.0.1:5080", null, body,
                "application", "sdp", null, null);
        assertLastOperationSuccess(call);
        assertTrue(call.waitOutgoingCallResponse(5 * 1000));
        final int response = call.getLastReceivedResponse().getStatusCode();
        assertTrue(response == Response.TRYING || response == Response.RINGING);
        if (response == Response.TRYING) {
            assertTrue(call.waitOutgoingCallResponse(5 * 1000));
            assertEquals(Response.RINGING, call.getLastReceivedResponse().getStatusCode());
        }
        assertTrue(call.waitOutgoingCallResponse(5 * 1000));
        assertEquals(Response.OK, call.getLastReceivedResponse().getStatusCode());
        call.sendInviteOkAck();
        assertTrue(!(call.getLastReceivedResponse().getStatusCode() >= 400));
        // Wait for the media to play and the call to hangup.
        assertTrue(call.waitForDisconnect(10 * 1000));
        try {
            Thread.sleep(10 * 1000);
        } catch (final InterruptedException exception) {
            exception.printStackTrace();
        }
    }

    @Ignore
    @Test
    public synchronized void testGatherAndFollowAction() throws InterruptedException {
        deployer.deploy("CallTest");
        phone.setLoopback(true);
        final SipCall call = phone.createSipCall();
        call.initiateOutgoingCall("sip:+17778889999@127.0.0.1:5070", "sip:+12223334453@127.0.0.1:5080", null, body,
                "application", "sdp", null, null);
        assertLastOperationSuccess(call);
        assertTrue(call.waitOutgoingCallResponse(5 * 1000));
        final int response = call.getLastReceivedResponse().getStatusCode();
        assertTrue(response == Response.TRYING || response == Response.RINGING);
        if (response == Response.TRYING) {
            assertTrue(call.waitOutgoingCallResponse(5 * 1000));
            assertEquals(Response.RINGING, call.getLastReceivedResponse().getStatusCode());
        }
        assertTrue(call.waitOutgoingCallResponse(5 * 1000));
        assertEquals(Response.OK, call.getLastReceivedResponse().getStatusCode());
        call.sendInviteOkAck();
        assertTrue(!(call.getLastReceivedResponse().getStatusCode() >= 400));
        // Wait for the media to play and the call to hangup.
        assertTrue(call.waitForDisconnect(10 * 1000));
        try {
            Thread.sleep(10 * 1000);
        } catch (final InterruptedException exception) {
            exception.printStackTrace();
        }
    }

    @Ignore
    @Test
    public synchronized void testDialConference() throws InterruptedException {
        deployer.deploy("CallTest");
        phone.setLoopback(true);
        final SipCall call = phone.createSipCall();
        call.initiateOutgoingCall("sip:+17778889999@127.0.0.1:5070", "sip:+12223334451@127.0.0.1:5080", null, body,
                "application", "sdp", null, null);
        assertLastOperationSuccess(call);
        assertTrue(call.waitOutgoingCallResponse(5 * 1000));
        final int response = call.getLastReceivedResponse().getStatusCode();
        assertTrue(response == Response.TRYING || response == Response.RINGING);
        if (response == Response.TRYING) {
            assertTrue(call.waitOutgoingCallResponse(5 * 1000));
            assertEquals(Response.RINGING, call.getLastReceivedResponse().getStatusCode());
        }
        assertTrue(call.waitOutgoingCallResponse(5 * 1000));
        assertEquals(Response.OK, call.getLastReceivedResponse().getStatusCode());
        call.sendInviteOkAck();
        assertTrue(!(call.getLastReceivedResponse().getStatusCode() >= 400));
        // Wait for the media to play and the call to hangup.
        assertTrue(call.waitForDisconnect(30 * 1000));
        try {
            Thread.sleep(10 * 1000);
        } catch (final InterruptedException exception) {
            exception.printStackTrace();
        }
    }

    @Ignore
    @Test
    public synchronized void testDialFork() throws InterruptedException {
        deployer.deploy("CallTest");
        phone.setLoopback(true);
        final SipCall call = phone.createSipCall();
        call.initiateOutgoingCall("sip:+17778889999@127.0.0.1:5070", "sip:+12223334452@127.0.0.1:5080", null, body,
                "application", "sdp", null, null);
        assertLastOperationSuccess(call);
        assertTrue(call.waitOutgoingCallResponse(5 * 1000));
        final int response = call.getLastReceivedResponse().getStatusCode();
        assertTrue(response == Response.TRYING || response == Response.RINGING);
        if (response == Response.TRYING) {
            assertTrue(call.waitOutgoingCallResponse(5 * 1000));
            assertEquals(Response.RINGING, call.getLastReceivedResponse().getStatusCode());
        }
        assertTrue(call.waitOutgoingCallResponse(5 * 1000));
        assertEquals(Response.OK, call.getLastReceivedResponse().getStatusCode());
        call.sendInviteOkAck();
        assertTrue(!(call.getLastReceivedResponse().getStatusCode() >= 400));
        // hangup.
        assertTrue(call.waitForDisconnect(120 * 1000));
        try {
            Thread.sleep(10 * 1000);
        } catch (final InterruptedException exception) {
            exception.printStackTrace();
        }
    }

    @Deployment(name = "CallTest", managed = false, testable = false)
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
        archive.addAsWebInfResource("restcomm.xml", "conf/restcomm.xml");
        archive.addAsWebInfResource("restcomm.script", "data/hsql/restcomm.script");
        archive.addAsWebResource("redirect-sms-entry.xml");
        archive.addAsWebResource("redirect-sms-sms.xml");
        archive.addAsWebResource("pause-reject-busy-entry.xml");
        archive.addAsWebResource("pause-reject-rejected-entry.xml");
        archive.addAsWebResource("play-say-entry.xml");
        archive.addAsWebResource("record-entry.xml");
        archive.addAsWebResource("fax-entry.xml");
        archive.addAsWebResource("fax.pdf");
        archive.addAsWebResource("gather-entry.xml");
        archive.addAsWebResource("dial-conference-entry.xml");
        archive.addAsWebResource("dial-fork-entry.xml");
        archive.addAsWebResource("gather-action-entry.xml");
        archive.addAsWebResource("gather-action-finish.xml");
        return archive;
    }
}
