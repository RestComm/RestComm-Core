package org.restcomm.connect.testsuite.http;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.net.URL;

import org.apache.log4j.Logger;
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

import com.google.gson.JsonObject;
import org.restcomm.connect.commons.Version;

/**
 * @author <a href="mailto:gvagenas@gmail.com">gvagenas</a>
 */
@RunWith(Arquillian.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class OutboudProxyCallsTest {

    private final static Logger logger = Logger.getLogger(OutboudProxyCallsTest.class.getName());

    private static final String version = Version.getVersion();

    @ArquillianResource
    private Deployer deployer;
    @ArquillianResource
    URL deploymentUrl;

    private String adminAccountSid = "ACae6e420f425248d6a26948c17a9e2acf";
    private String adminAuthToken = "77f8c12cc7b8f8423e5c38b035249166";

    private static SipStackTool tool1;
    private static SipStackTool tool2;
    private static SipStackTool tool3;

    private SipStack primaryProxySipStack;
    private SipPhone primaryProxyPhone;
    private String primaryProxyContact = "sip:primary@127.0.0.1:5070";

    private SipStack fallbackProxySipStack;
    private SipPhone fallbackProxyPhone;
    private String fallbackProxyContact = "sip:fallback@127.0.0.1:5090";

    // Alice is a Restcomm Client with VoiceURL. This Restcomm Client can register with Restcomm and whatever will dial the RCML
    // of the VoiceURL will be executed.
    private SipStack georgeSipStack;
    private SipPhone georgePhone;
    private String georgeContact = "sip:george@127.0.0.1:5091";

    @BeforeClass
    public static void beforeClass() throws Exception {
        tool1 = new SipStackTool("OutboundProxyCalls1");
        tool2 = new SipStackTool("OutboundProxyCalls2");
        tool3 = new SipStackTool("OutboundProxyCalls3");
    }

    @Before
    public void before() throws Exception {
        primaryProxySipStack = tool1.initializeSipStack(SipStack.PROTOCOL_UDP, "127.0.0.1", "5070", "127.0.0.1:5080");
        primaryProxyPhone = primaryProxySipStack.createSipPhone("127.0.0.1", SipStack.PROTOCOL_UDP, 5080, primaryProxyContact);

        fallbackProxySipStack = tool2.initializeSipStack(SipStack.PROTOCOL_UDP, "127.0.0.1", "5090", "127.0.0.1:5080");
        fallbackProxyPhone = fallbackProxySipStack.createSipPhone("127.0.0.1", SipStack.PROTOCOL_UDP, 5080, fallbackProxyContact);

        georgeSipStack = tool3.initializeSipStack(SipStack.PROTOCOL_UDP, "127.0.0.1", "5091", "127.0.0.1:5080");
        georgePhone = georgeSipStack.createSipPhone("127.0.0.1", SipStack.PROTOCOL_UDP, 5080, georgeContact);
    }

    @After
    public void after() throws Exception {
        if (primaryProxyPhone != null) {
            primaryProxyPhone.dispose();
        }
        if (primaryProxySipStack != null) {
            primaryProxySipStack.dispose();
        }

        if (fallbackProxyPhone != null) {
            fallbackProxyPhone.dispose();
        }
        if (fallbackProxySipStack != null) {
            fallbackProxySipStack.dispose();
        }

        if (georgeSipStack != null) {
            georgeSipStack.dispose();
        }
        if (georgePhone != null) {
            georgePhone.dispose();
        }
    }
    
    @Test
    // Create a call to a SIP URI. Non-regression test for issue https://bitbucket.org/telestax/telscale-restcomm/issue/175
    // Use Calls Rest API to dial Bob (SIP URI sip:bob@127.0.0.1:5090) and connect him to the RCML app dial-number-entry.xml.
    // This RCML will dial +131313 which George's phone is listening (use the dial-number-entry.xml as a side effect to verify
    // that the call created successfully)
    public void createCallAndFallback() throws InterruptedException {

        String primaryProxy = "127.0.0.1:5070";
        String fallbackProxy = "127.0.0.1:5090";
        
        JsonObject activeProxyJsonObject = OutboundProxyTool.getInstance().getActiveProxy(deploymentUrl.toString(), adminAccountSid, adminAuthToken);        
        String activeProxy = activeProxyJsonObject.get("ActiveProxy").getAsString();
        assertTrue(activeProxy.equalsIgnoreCase(primaryProxy));
        
        SipCall primaryProxyCall = primaryProxyPhone.createSipCall();
        primaryProxyCall.listenForIncomingCall();

        SipCall fallbackProxyCall = fallbackProxyPhone.createSipCall();
        fallbackProxyCall.listenForIncomingCall();

        String from = "+15126002188";
        String to = primaryProxyContact;
        String rcmlUrl = deploymentUrl.toString() + ".application-"+version+"/dial-number-entry.xml";

        for (int i = 0; i < 20; i++) {

            JsonObject callResult = (JsonObject) RestcommCallsTool.getInstance().createCall(deploymentUrl.toString(), adminAccountSid,
                    adminAuthToken, from, to, rcmlUrl);
            assertNotNull(callResult);

            assertTrue(primaryProxyCall.waitForIncomingCall(5000));
            assertTrue(primaryProxyCall.sendIncomingCallResponse(480, "Temporarily Unavailable - Primary Proxy", 3600));
            Thread.sleep(2000);
        }
        
        activeProxyJsonObject = OutboundProxyTool.getInstance().getActiveProxy(deploymentUrl.toString(), adminAccountSid, adminAuthToken);        
        activeProxy = activeProxyJsonObject.get("ActiveProxy").getAsString();
        assertTrue(activeProxy.equalsIgnoreCase(fallbackProxy));
        
        to = fallbackProxyContact;

        for (int i = 0; i < 20; i++) {

            JsonObject callResult = (JsonObject) RestcommCallsTool.getInstance().createCall(deploymentUrl.toString(), adminAccountSid,
                    adminAuthToken, from, to, rcmlUrl);
            assertNotNull(callResult);

            assertTrue(fallbackProxyCall.waitForIncomingCall(5000));
            assertTrue(fallbackProxyCall.sendIncomingCallResponse(480, "Temporarily Unavailable - Fallback Proxy", 3600));
            Thread.sleep(2000);
        }
        
        activeProxyJsonObject = OutboundProxyTool.getInstance().getActiveProxy(deploymentUrl.toString(), adminAccountSid, adminAuthToken);        
        activeProxy = activeProxyJsonObject.get("ActiveProxy").getAsString();
        assertTrue(activeProxy.equalsIgnoreCase(primaryProxy));
    }
    

    @Deployment(name = "OutboundCallsTest", managed = true, testable = false)
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
        archive.addAsWebResource("dial-number-entry.xml");
        archive.addAsWebResource("dial-client-entry.xml");
        logger.info("Packaged Test App");
        return archive;
    }
}
