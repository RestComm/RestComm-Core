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
package org.restcomm.connect.testsuite.http;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.net.URL;
import java.text.ParseException;

import javax.sip.RequestEvent;
import javax.sip.ResponseEvent;
import javax.sip.SipException;
import javax.sip.address.Address;
import javax.sip.header.ContentTypeHeader;
import javax.sip.message.Request;
import javax.sip.message.Response;

import org.apache.log4j.Logger;
import org.cafesip.sipunit.SipCall;
import org.cafesip.sipunit.SipPhone;
import org.cafesip.sipunit.SipStack;
import org.cafesip.sipunit.SipTransaction;
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

import com.google.gson.JsonObject;
import org.restcomm.connect.commons.Version;
import org.restcomm.connect.commons.annotations.FeatureAltTests;

/**
 * @author <a href="mailto:gvagenas@gmail.com">gvagenas</a>
 *
 */
@RunWith(Arquillian.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class UssdPushTest {

    private final static Logger logger = Logger.getLogger(CreateCallsTest.class.getName());

    private static final String version = Version.getVersion();

    @ArquillianResource
    private Deployer deployer;
    @ArquillianResource
    URL deploymentUrl;

    private String adminAccountSid = "ACae6e420f425248d6a26948c17a9e2acf";
    private String adminAuthToken = "77f8c12cc7b8f8423e5c38b035249166";

    private String ussdContentSubType = "vnd.3gpp.ussd+xml";
    
    private static SipStackTool tool1;
    private static SipStackTool tool2;

    private SipStack bobSipStack;
    private SipPhone bobPhone;
    private String bobContact = "sip:bob@127.0.0.1:5090";

    private SipStack georgeSipStack;
    private SipPhone georgePhone;
    private String georgeContact = "sip:+131313@127.0.0.1:5070";

    @BeforeClass
    public static void beforeClass() throws Exception {
        tool1 = new SipStackTool("CreateCalls1");
        tool2 = new SipStackTool("CreaeCalls2");
    }

    @Before
    public void before() throws Exception {
        bobSipStack = tool1.initializeSipStack(SipStack.PROTOCOL_UDP, "127.0.0.1", "5090", "127.0.0.1:5080");
        bobPhone = bobSipStack.createSipPhone("127.0.0.1", SipStack.PROTOCOL_UDP, 5080, bobContact);

        georgeSipStack = tool2.initializeSipStack(SipStack.PROTOCOL_UDP, "127.0.0.1", "5070", "127.0.0.1:5080");
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

        if (georgePhone != null) {
            georgePhone.dispose();
        }
        if (georgeSipStack != null) {
            georgeSipStack.dispose();
        }
    }
    
    @Test
    public void createUssdPushTestNotifyOnly() throws InterruptedException, SipException, ParseException {

        SipCall bobCall = bobPhone.createSipCall();
        bobCall.listenForIncomingCall();

        SipCall georgeCall = georgePhone.createSipCall();
        georgeCall.listenForIncomingCall();

        String from = "+15126002188";
        String to = "bob";
        String rcmlUrl = deploymentUrl.toString() + "/ussd-rcml.xml";

        JsonObject callResult = RestcommUssdPushTool.getInstance().createUssdPush(deploymentUrl.toString(), adminAccountSid,
                adminAuthToken, from, to, rcmlUrl);
        assertNotNull(callResult);

        assertTrue(bobCall.waitForIncomingCall(5000));
        String receivedBody = new String(bobCall.getLastReceivedRequest().getRawContent());
        assertTrue(bobCall.sendIncomingCallResponse(Response.RINGING, "Ringing-Bob", 3600));
        assertTrue(bobCall
                .sendIncomingCallResponse(Response.OK, "OK-Bob", 3600, null, "application", ussdContentSubType, null, null));

        assertTrue(receivedBody.trim().equals(UssdPushTestMessages.ussdPushNotifyOnlyMessage));

        bobCall.waitForAck(5000);
        
        Request infoRequest = bobCall.getDialog().createRequest(Request.INFO);
        
        ContentTypeHeader contentTypeHeader = bobCall.getHeaderFactory().createContentTypeHeader("application", "vnd.3gpp.ussd+xml");
        infoRequest.setContent(UssdPushTestMessages.ussdPushNotifyOnlyClientResponse.getBytes(), contentTypeHeader);

        SipTransaction sipTransaction = bobPhone.sendRequestWithTransaction(infoRequest, false, bobCall.getDialog());
        assertNotNull(sipTransaction);
        ResponseEvent response = (ResponseEvent) bobPhone.waitResponse(sipTransaction, 5000);
        assertNotNull(response);
        assertTrue(response.getResponse().getStatusCode() == Response.OK);
        
        Thread.sleep(3000);

        bobCall.listenForDisconnect();
        assertTrue(bobCall.waitForDisconnect(5000));        
        assertTrue(bobCall.respondToDisconnect());

        receivedBody = new String(bobCall.getLastReceivedRequest().getRawContent());
        assertTrue(receivedBody.trim().equals(UssdPushTestMessages.ussdPushNotifyOnlyFinalResponse));
    }
    
    @Test
    @Category(FeatureAltTests.class)
    public void createUssdPushTestNotifyOnlyFromIsRestcomm() throws InterruptedException, SipException, ParseException {

        SipCall bobCall = bobPhone.createSipCall();
        bobCall.listenForIncomingCall();

        SipCall georgeCall = georgePhone.createSipCall();
        georgeCall.listenForIncomingCall();

        String from = "Restcomm";
        String to = "bob";
        String rcmlUrl = deploymentUrl.toString() + "/ussd-rcml.xml";

        JsonObject callResult = RestcommUssdPushTool.getInstance().createUssdPush(deploymentUrl.toString(), adminAccountSid,
                adminAuthToken, from, to, rcmlUrl);
        assertNotNull(callResult);

        assertTrue(bobCall.waitForIncomingCall(5000));
        String receivedBody = new String(bobCall.getLastReceivedRequest().getRawContent());
        assertTrue(bobCall.sendIncomingCallResponse(Response.RINGING, "Ringing-Bob", 3600));
        assertTrue(bobCall
                .sendIncomingCallResponse(Response.OK, "OK-Bob", 3600, null, "application", ussdContentSubType, null, null));

        assertTrue(receivedBody.trim().equals(UssdPushTestMessages.ussdPushNotifyOnlyMessage));

        bobCall.waitForAck(5000);
        
        Request infoRequest = bobCall.getDialog().createRequest(Request.INFO);
        
        ContentTypeHeader contentTypeHeader = bobCall.getHeaderFactory().createContentTypeHeader("application", "vnd.3gpp.ussd+xml");
        infoRequest.setContent(UssdPushTestMessages.ussdPushNotifyOnlyClientResponse.getBytes(), contentTypeHeader);

        SipTransaction sipTransaction = bobPhone.sendRequestWithTransaction(infoRequest, false, bobCall.getDialog());
        assertNotNull(sipTransaction);
        ResponseEvent response = (ResponseEvent) bobPhone.waitResponse(sipTransaction, 5000);
        assertNotNull(response);
        assertTrue(response.getResponse().getStatusCode() == Response.OK);
        
        Thread.sleep(3000);

        bobCall.listenForDisconnect();
        assertTrue(bobCall.waitForDisconnect(5000));        
        assertTrue(bobCall.respondToDisconnect());

        receivedBody = new String(bobCall.getLastReceivedRequest().getRawContent());
        assertTrue(receivedBody.trim().equals(UssdPushTestMessages.ussdPushNotifyOnlyFinalResponse));
    }
    
    @Test
    public void createUssdPushTestCollect() throws InterruptedException, SipException, ParseException {

        SipCall bobCall = bobPhone.createSipCall();
        bobCall.listenForIncomingCall();

        SipCall georgeCall = georgePhone.createSipCall();
        georgeCall.listenForIncomingCall();

        String from = "+15126002188";
        String to = "bob";
        String rcmlUrl = deploymentUrl.toString() + "/ussd-rcml-collect.xml";

        JsonObject callResult = RestcommUssdPushTool.getInstance().createUssdPush(deploymentUrl.toString(), adminAccountSid,
                adminAuthToken, from, to, rcmlUrl);
        assertNotNull(callResult);

        //Wait for the USSD Push Tree Message 
        assertTrue(bobCall.waitForIncomingCall(5000));
        String receivedBody = new String(bobCall.getLastReceivedRequest().getRawContent());
        assertTrue(bobCall.sendIncomingCallResponse(Response.RINGING, "Ringing-Bob", 3600));
        assertTrue(bobCall
                .sendIncomingCallResponse(Response.OK, "OK-Bob", 3600, null, "application", ussdContentSubType, null, null));
        
        assertTrue(receivedBody.equals(UssdPushTestMessages.ussdPushCollectMessage));

        bobCall.waitForAck(5000);
        String toTag = bobCall.getDialog().getLocalTag();
        Address bobAddress = bobPhone.getAddress();
        
        //Prepare and send USSD Response "1" for the tree menu
        Request infoRequest = bobCall.getDialog().createRequest(Request.INFO);
        
        ContentTypeHeader contentTypeHeader = bobCall.getHeaderFactory().createContentTypeHeader("application", "vnd.3gpp.ussd+xml");
        infoRequest.setContent(UssdPushTestMessages.ussdPushCollectClientResponse.getBytes(), contentTypeHeader);

        SipTransaction sipTransaction = bobPhone.sendRequestWithTransaction(infoRequest, false, bobCall.getDialog());
        assertNotNull(sipTransaction);
        ResponseEvent response = (ResponseEvent) bobPhone.waitResponse(sipTransaction, 5000);
        assertNotNull(response);
        assertTrue(response.getResponse().getStatusCode() == Response.OK);
        
        assertTrue(bobPhone.listenRequestMessage());
        RequestEvent requestEvent = bobPhone.waitRequest(30*1000);
        
        assertNotNull(requestEvent);  
        assertTrue(requestEvent.getRequest().getMethod().equalsIgnoreCase("INFO"));
        bobPhone.sendReply(requestEvent, 200, "OK", toTag, bobAddress, 0);
        

        String receivedUssdPayload = new String(requestEvent.getRequest().getRawContent());
        assertTrue(receivedUssdPayload.equals(UssdPushTestMessages.ussdPushNotifyOnlyMessage));

        //Prepare and send final "OK" USSD message to end the communication
        Request infoRequestFinal = bobCall.getDialog().createRequest(Request.INFO);
        
        infoRequestFinal.setContent(UssdPushTestMessages.ussdPushNotifyOnlyClientResponse.getBytes(), contentTypeHeader);

        SipTransaction sipTransactionFinal = bobPhone.sendRequestWithTransaction(infoRequestFinal, false, bobCall.getDialog());
        assertNotNull(sipTransactionFinal);
        ResponseEvent responseFinal = (ResponseEvent) bobPhone.waitResponse(sipTransactionFinal, 5000);
        assertNotNull(responseFinal);
        assertTrue(responseFinal.getResponse().getStatusCode() == Response.OK);
        
//        Thread.sleep(3000);

        bobCall.listenForDisconnect();
        assertTrue(bobCall.waitForDisconnect(5000));        
        assertTrue(bobCall.respondToDisconnect());

        receivedBody = new String(bobCall.getLastReceivedRequest().getRawContent());
        assertTrue(receivedBody.trim().equals(UssdPushTestMessages.ussdPushNotifyOnlyFinalResponse));
    }    
    
    @Deployment(name = "UssdPushTest", managed = true, testable = false)
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
        archive.addAsWebInfResource("org/restcomm/connect/ussd/restcomm_conf_ussd_push.xml", "conf/restcomm.xml");
        archive.addAsWebInfResource("org/restcomm/connect/ussd/restcomm.script_ussdPullTest", "data/hsql/restcomm.script");
        archive.addAsWebResource("org/restcomm/connect/ussd/ussd-rcml.xml");
        archive.addAsWebResource("org/restcomm/connect/ussd/ussd-rcml-collect.xml");
        logger.info("Packaged Test App");
        return archive;
    }
    
}
