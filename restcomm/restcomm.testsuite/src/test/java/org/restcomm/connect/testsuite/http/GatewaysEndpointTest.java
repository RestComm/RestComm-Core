package org.restcomm.connect.testsuite.http;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.URL;
import java.text.ParseException;

import org.apache.http.client.ClientProtocolException;
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

import com.google.gson.JsonObject;
import org.restcomm.connect.commons.Version;

/**
 * 
 * @author <a href="mailto:gvagenas@gmail.com">gvagenas</a>
 */

@RunWith(Arquillian.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class GatewaysEndpointTest {

    private static final String version = Version.getVersion();

    @ArquillianResource
    private Deployer deployer;
    @ArquillianResource
    URL deploymentUrl;

    private String gwName = "MyGateway";
    private String gwUsername = "myusername";
    private String gwPassword = "mypassword";
    private String gwProxy = "127.0.0.1:5090";
    private String gwRegister = "true";
    private String gwTTL = "3600";
    
    @Before
    public void before() throws Exception {
    }

    @After
    public void after() throws Exception {
    }

    //Restcomm will try to register the Gateway but this will fail, we ignore that for now.
    @Test
    public void createGatewayTest() throws ClientProtocolException, IOException, ParseException, InterruptedException { 
        JsonObject gateway = CreateGatewaysTool.getInstance().createGateway(deploymentUrl.toString(), gwName, gwUsername , gwPassword, gwProxy, gwRegister, gwTTL);
        assertNotNull(gateway);
        assertNotNull(gateway.get("sid").getAsString());
        assertTrue(gwName.equalsIgnoreCase(gateway.get("friendly_name").getAsString()));
        assertTrue(gwUsername.equalsIgnoreCase(gateway.get("user_name").getAsString()));
        assertTrue(gwPassword.equalsIgnoreCase(gateway.get("password").getAsString()));
        
        String sid = gateway.get("sid").getAsString();
        CreateGatewaysTool.getInstance().deleteGateway(deploymentUrl.toString(), sid);
        
    }

  //Restcomm will try to register the Gateway but this will fail, we ignore that for now.
    @Test
    public void updateGatewayTest() throws ClientProtocolException, IOException, ParseException, InterruptedException {        
        JsonObject gateway = CreateGatewaysTool.getInstance().createGateway(deploymentUrl.toString(), gwName, gwUsername , gwPassword, gwProxy, gwRegister, gwTTL);
        assertNotNull(gateway);
        assertNotNull(gateway.get("sid").getAsString());
        assertTrue(gwName.equalsIgnoreCase(gateway.get("friendly_name").getAsString()));
        assertTrue(gwUsername.equalsIgnoreCase(gateway.get("user_name").getAsString()));
        assertTrue(gwPassword.equalsIgnoreCase(gateway.get("password").getAsString()));
        
        String sid = gateway.get("sid").getAsString();
        String newName = "MyNewGatewayName";
        
        JsonObject updatedGateway = CreateGatewaysTool.getInstance().updateGateway(deploymentUrl.toString(), sid, newName, null, null, null, null, null);
        assertNotNull(updatedGateway);
        assertTrue(newName.equalsIgnoreCase(updatedGateway.get("friendly_name").getAsString()));
        assertTrue(gwUsername.equalsIgnoreCase(updatedGateway.get("user_name").getAsString()));
        assertTrue(gwPassword.equalsIgnoreCase(updatedGateway.get("password").getAsString()));
        
        String newSid = updatedGateway.get("sid").getAsString();
        assertTrue(sid.equals(newSid));
        
        CreateGatewaysTool.getInstance().deleteGateway(deploymentUrl.toString(), sid);
        
    }

    @Deployment(name = "ClientsEndpointTest", managed = true, testable = false)
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
        archive.addAsWebInfResource("restcomm.script", "data/hsql/restcomm.script");
        return archive;
    }
}
