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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.net.URL;
import java.text.ParseException;
import java.util.List;

import javax.sip.address.SipURI;

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
import org.restcomm.connect.testsuite.http.CreateClientsTool;

/**
 * Client SIP MESSAGE Test.
 *
 * @author <a href="mailto:gvagenas@gmail.com">gvagenas</a>
 */
@RunWith(Arquillian.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@Category(value={SequentialClassTests.class})
public class ClientMessageTest {

    private static final String version = Version.getVersion();

    private static final byte[] bytes = new byte[] { 118, 61, 48, 13, 10, 111, 61, 117, 115, 101, 114, 49, 32, 53, 51, 54, 53,
        53, 55, 54, 53, 32, 50, 51, 53, 51, 54, 56, 55, 54, 51, 55, 32, 73, 78, 32, 73, 80, 52, 32, 49, 50, 55, 46, 48, 46,
        48, 46, 49, 13, 10, 115, 61, 45, 13, 10, 99, 61, 73, 78, 32, 73, 80, 52, 32, 49, 50, 55, 46, 48, 46, 48, 46, 49,
        13, 10, 116, 61, 48, 32, 48, 13, 10, 109, 61, 97, 117, 100, 105, 111, 32, 54, 48, 48, 48, 32, 82, 84, 80, 47, 65,
        86, 80, 32, 48, 13, 10, 97, 61, 114, 116, 112, 109, 97, 112, 58, 48, 32, 80, 67, 77, 85, 47, 56, 48, 48, 48, 13, 10 };
    private static final String body = new String(bytes);

    private String messageBody = "Hello from George";

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
    private String mariaRestcommContact = "sip:maria@127.0.0.1:5080";

    // Alice is a Restcomm Client with VoiceURL. This Restcomm Client can register with Restcomm and whatever will dial the RCML
    // of the VoiceURL will be executed.
    private SipStack georgeSipStack;
    private SipPhone georgePhone;
    private String georgeContact = "sip:george@127.0.0.1:5091";
    private String georgeRestcommClientSid;

    private String adminAccountSid = "ACae6e420f425248d6a26948c17a9e2acf";
    private String adminAuthToken = "77f8c12cc7b8f8423e5c38b035249166";

    @BeforeClass
    public static void beforeClass() throws Exception {
        tool1 = new SipStackTool("RegisterClientTest1");
        tool2 = new SipStackTool("RegisterClientTest2");
    }

    @Before
    public void before() throws Exception {

        georgeSipStack = tool2.initializeSipStack(SipStack.PROTOCOL_UDP, "127.0.0.1", "5091", "127.0.0.1:5080");
        georgePhone = georgeSipStack.createSipPhone("127.0.0.1", SipStack.PROTOCOL_UDP, 5080, georgeContact);

        mariaSipStack = tool1.initializeSipStack(SipStack.PROTOCOL_UDP, "127.0.0.1", "5092", "127.0.0.1:5080");
        mariaPhone = mariaSipStack.createSipPhone("127.0.0.1", SipStack.PROTOCOL_UDP, 5080, mariaContact);

        mariaRestcommClientSid = CreateClientsTool.getInstance().createClient(deploymentUrl.toString(), "maria", "1234qwerT", null);
        georgeRestcommClientSid = CreateClientsTool.getInstance().createClient(deploymentUrl.toString(), "george", "1234qwerT", null);
        Thread.sleep(500);
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
    }

    @Test
    public void testRegisterClients() throws ParseException, InterruptedException {

        assertNotNull(mariaRestcommClientSid);
        assertNotNull(georgeRestcommClientSid);

        SipURI uri = georgeSipStack.getAddressFactory().createSipURI(null, "127.0.0.1:5080");

        assertTrue(georgePhone.register(uri, "george", "1234qwerT", georgeContact, 3600, 3600));
        assertTrue(mariaPhone.register(uri, "maria", "1234qwerT", mariaContact, 3600, 3600));

        Thread.sleep(1000);

        assertTrue(georgePhone.unregister(georgeContact, 0));
        assertTrue(mariaPhone.unregister(mariaContact, 0));
    }

    @Test
    public void testGeorgeSendMessageTolMaria() throws ParseException, InterruptedException {

        assertNotNull(mariaRestcommClientSid);
        assertNotNull(georgeRestcommClientSid);

        SipURI uri = mariaSipStack.getAddressFactory().createSipURI(null, "127.0.0.1:5080");

        assertTrue(georgePhone.register(uri, "george", "1234qwerT", georgeContact, 3600, 3600));
        Thread.sleep(3000);
        assertTrue(mariaPhone.register(uri, "maria", "1234qwerT", mariaContact, 3600, 3600));
        Thread.sleep(3000);


        Credential c = new Credential("127.0.0.1", "george", "1234qwerT");
        georgePhone.addUpdateCredential(c);

        Credential c2 = new Credential("127.0.0.1", "maria", "1234qwerT");
        mariaPhone.addUpdateCredential(c2);


        Thread.sleep(1000);

        final SipCall mariaCall_1 = mariaPhone.createSipCall();
        mariaCall_1.listenForMessage();

        // Alice initiates a call to Maria
        final SipCall georgeCall = georgePhone.createSipCall();
        georgeCall.initiateOutgoingMessage(mariaRestcommContact, null, messageBody);
        assertLastOperationSuccess(georgeCall);
        assertTrue(georgeCall.waitForAuthorisation(3000));

        assertTrue(mariaCall_1.waitForMessage(3000));
        assertTrue(mariaCall_1.sendMessageResponse(200, "OK-Maria-Mesasge-Receieved", 1800));
        List<String> msgsFromGeorge = mariaCall_1.getAllReceivedMessagesContent();

        assertTrue(msgsFromGeorge.size()>0);
        assertTrue(msgsFromGeorge.get(0).equals(messageBody));

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
