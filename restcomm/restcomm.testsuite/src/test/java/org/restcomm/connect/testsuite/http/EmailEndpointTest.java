package org.restcomm.connect.testsuite.http;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.GreenMailUtil;
import com.icegreen.greenmail.util.ServerSetupTest;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.filter.HTTPBasicAuthFilter;
import com.sun.jersey.core.util.MultivaluedMapImpl;
import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.Deployer;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.FixMethodOrder;
import org.junit.runners.MethodSorters;
import org.junit.runner.RunWith;
import org.restcomm.connect.commons.Version;
import org.restcomm.connect.email.api.Mail;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import java.io.IOException;
import java.net.URL;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Created by gvagenas on 1/12/16.
 */
@RunWith(Arquillian.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class EmailEndpointTest {

    private final static Logger logger = Logger.getLogger(CallsEndpointTest.class.getName());

    private static final String version = Version.getVersion();

    @ArquillianResource
    private Deployer deployer;
    @ArquillianResource
    URL deploymentUrl;

    private GreenMail mailServer;

    private String adminAccountSid = "ACae6e420f425248d6a26948c17a9e2acf";
    private String adminAuthToken = "77f8c12cc7b8f8423e5c38b035249166";


    @Before
    public void before() throws Exception {
        mailServer = new GreenMail(ServerSetupTest.SMTP);
        mailServer.setUser("hascode@localhost", "hascode", "abcdef123");
        mailServer.start();
        logger.info("MailServer started");
        Thread.sleep(2000);
    }

    @After
    public void after() {
        mailServer.reset();
        mailServer.stop();
    }

    @Test
    public void sendEmailTest() throws MessagingException, IOException {

        final Mail emailMsg = new Mail("hascode@localhost", "someone@localhost.com","Testing Email Service" ,"This is the subject of the email service testing", "someone2@localhost.com, test@localhost.com, test3@localhost.com", "someone3@localhost.com, test2@localhost.com");

        Client jerseyClient = Client.create();
        jerseyClient.addFilter(new HTTPBasicAuthFilter(adminAccountSid, adminAuthToken));

        String url = deploymentUrl + "2012-04-24/Accounts/" + adminAccountSid + "/Email/Messages.json";

        WebResource webResource = jerseyClient.resource(url);

        final String subject = "Test Email Subject";
        final String body = "Test body";

        MultivaluedMap<String, String> params = new MultivaluedMapImpl();
        params.add("From", "restcomm@company.com");
        params.add("To", "user@company.com");
        params.add("Subject", subject );
        params.add("Body", body);

        // webResource = webResource.queryParams(params);
        String response = webResource.accept(MediaType.APPLICATION_JSON).post(String.class, params);
        assertNotNull(response);
        logger.info("Response: "+response);
        assertTrue(mailServer.waitForIncomingEmail(1));

        Message[] messages = mailServer.getReceivedMessages();
        assertEquals(1, messages.length);

        assertEquals(subject, messages[0].getSubject());
        assertEquals(body, GreenMailUtil.getBody(messages[0]).trim());
    }

    @Deployment(name = "EmailEndpointTest", managed = true, testable = false)
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
        archive.addAsWebInfResource("restcomm-EmailEndpoint.xml", "conf/restcomm.xml");
        archive.addAsWebInfResource("restcomm_with_Data.script", "data/hsql/restcomm.script");
        logger.info("Packaged Test App");
        return archive;
    }
}
