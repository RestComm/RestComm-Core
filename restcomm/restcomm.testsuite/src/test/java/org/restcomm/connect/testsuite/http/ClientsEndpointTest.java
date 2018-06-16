package org.restcomm.connect.testsuite.http;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.URL;
import java.text.ParseException;

import javax.sip.address.SipURI;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;

import org.apache.http.client.ClientProtocolException;
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
import org.restcomm.connect.commons.annotations.FeatureAltTests;
import org.restcomm.connect.commons.annotations.FeatureExpTests;
import org.restcomm.connect.commons.annotations.UnstableTests;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.filter.HTTPBasicAuthFilter;
import com.sun.jersey.core.util.MultivaluedMapImpl;

/**
 *
 * @author <a href="mailto:gvagenas@gmail.com">gvagenas</a>
 */

@RunWith(Arquillian.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class ClientsEndpointTest {

    private static final String version = Version.getVersion();

    @ArquillianResource
    private Deployer deployer;
    @ArquillianResource
    URL deploymentUrl;

    private static SipStackTool tool1;
    private SipStack bobSipStack;
    private SipPhone bobPhone;
    private String bobContact = "sip:bob@127.0.0.1:5090";

    String superadminAccountSid = "ACae6e420f425248d6a26948c17a9e2acf";
    //developer is account in Organization - default.restcomm.com
    String developerUsername = "developer@company.com";
    String developeerAuthToken = "77f8c12cc7b8f8423e5c38b035249166";
    String developerAccountSid = "AC11111111111111111111111111111111";
    String removedClientSid = "CLb8838febabef4970a10dda1680506815";

    //developerOrg2 is account in Organization - org2.restcomm.com
    String developerOrg2Username = "developer2@org2.com";
    String developeerOrg2AuthToken = "77f8c12cc7b8f8423e5c38b035249166";
    String developerOrg2AccountSid = "AC11111111111111111111111111111112";

    @BeforeClass
    public static void beforeClass() throws Exception {
        tool1 = new SipStackTool("ClientsEndpointTest");
    }

    @Before
    public void before() throws Exception {
        bobSipStack = tool1.initializeSipStack(SipStack.PROTOCOL_UDP, "127.0.0.1", "5090", "127.0.0.1:5080");
        bobPhone = bobSipStack.createSipPhone("127.0.0.1", SipStack.PROTOCOL_UDP, 5080, bobContact);
    }

    @After
    public void after() throws Exception {
        if (bobPhone != null) {
            bobPhone.dispose();
        }
        if (bobSipStack != null) {
            bobSipStack.dispose();
        }
    }

    // Issue 109: https://bitbucket.org/telestax/telscale-restcomm/issue/109
    @Test
    public void createClientTest() throws ClientProtocolException, IOException, ParseException, InterruptedException {

        SipURI reqUri = bobSipStack.getAddressFactory().createSipURI(null, "127.0.0.1:5080");

        String clientSID = CreateClientsTool.getInstance().createClient(deploymentUrl.toString(), "bob", "RestComm1234",
                deploymentUrl.toString() + "/demos/welcome.xml");
        assertNotNull(clientSID);

        Thread.sleep(3000);

        String clientSID2 = CreateClientsTool.getInstance().createClient(deploymentUrl.toString(), "bob", "RestComm1234",
                deploymentUrl.toString() + "/demos/welcome.xml");
        assertNotNull(clientSID2);

        Thread.sleep(3000);

        assertTrue(clientSID.equalsIgnoreCase(clientSID2));
        assertTrue(bobPhone.register(reqUri, "bob", "RestComm1234", bobContact, 1800, 1800));
        bobContact = "sip:mobile@127.0.0.1:5090";
        assertTrue(bobPhone.register(reqUri, "bob", "RestComm1234", bobContact, 1800, 1800));
        assertTrue(bobPhone.unregister(bobContact, 0));
        
        //update password and test register again
        Client jersey = getClient(superadminAccountSid, developeerAuthToken);
        WebResource resource = jersey.resource( getResourceUrl("/2012-04-24/Accounts/" + superadminAccountSid + "/Clients/" + clientSID2 ) );
        MultivaluedMap<String, String> params = new MultivaluedMapImpl();
        params.add("Password","RestComm1234RestComm1234");
        ClientResponse response = resource.accept(MediaType.APPLICATION_JSON).put(ClientResponse.class, params);
        assertEquals(200, response.getStatus());

        assertTrue(bobPhone.register(reqUri, "bob", "RestComm1234RestComm1234", bobContact, 1800, 1800));
        assertTrue(bobPhone.unregister(bobContact, 0));
    }

    @Test@Category(FeatureAltTests.class)
    public void createClientTestNoVoiceUrl() throws ClientProtocolException, IOException, ParseException, InterruptedException {

        SipURI reqUri = bobSipStack.getAddressFactory().createSipURI(null, "127.0.0.1:5080");

        String clientSID = CreateClientsTool.getInstance().createClient(deploymentUrl.toString(), "bob", "RestComm1234", null);
        assertNotNull(clientSID);

        Thread.sleep(3000);

        String clientSID2 = CreateClientsTool.getInstance().createClient(deploymentUrl.toString(), "bob", "RestComm1234", null);
        assertNotNull(clientSID2);

        Thread.sleep(3000);

        assertTrue(clientSID.equalsIgnoreCase(clientSID2));
        assertTrue(bobPhone.register(reqUri, "bob", "RestComm1234", bobContact, 1800, 1800));
        bobContact = "sip:mobile@127.0.0.1:5090";
        assertTrue(bobPhone.register(reqUri, "bob", "RestComm1234", bobContact, 1800, 1800));
        assertTrue(bobPhone.unregister(bobContact, 0));
        
    }

    @Test
    public void clientRemovalBehaviour() {
        // A developer account should be able to remove his own client
        Client jersey = getClient(developerUsername, developeerAuthToken);
        WebResource resource = jersey.resource( getResourceUrl("/2012-04-24/Accounts/" + developerAccountSid + "/Clients/" + removedClientSid ) );
        ClientResponse response = resource.delete(ClientResponse.class);
        assertEquals("Developer account could not remove his client", 200, response.getStatus());
        // re-removing the client should return a 404 (not a 200)
        response = resource.delete(ClientResponse.class);
        assertEquals("Removing a non-existing client did not return 404", 404, response.getStatus());
    }

    @Test@Category(FeatureExpTests.class)
    public void createClientWithWeakPasswordShouldFail() throws IOException {
        Client jersey = getClient(developerUsername, developeerAuthToken);
        WebResource resource = jersey.resource( getResourceUrl("/2012-04-24/Accounts/" + developerAccountSid + "/Clients.json" ) );
        MultivaluedMap<String, String> params = new MultivaluedMapImpl();
        params.add("Login","weakClient");
        params.add("Password","1234"); // this is a very weak password
        ClientResponse response = resource.accept(MediaType.APPLICATION_JSON).post(ClientResponse.class, params);
        assertEquals(400, response.getStatus());
        assertTrue("Response should contain 'weak' term", response.getEntity(String.class).toLowerCase().contains("weak"));
    }

    @Test@Category(FeatureExpTests.class)
    public void updateClientWithWeakPasswordShouldFail() {
        String updateClientSid = "CL00000000000000000000000000000001";
        Client jersey = getClient(developerUsername, developeerAuthToken);
        WebResource resource = jersey.resource( getResourceUrl("/2012-04-24/Accounts/" + developerAccountSid + "/Clients/" + updateClientSid ) );
        MultivaluedMap<String, String> params = new MultivaluedMapImpl();
        params.add("Password","1234"); // this is a very weak password
        ClientResponse response = resource.accept(MediaType.APPLICATION_JSON).put(ClientResponse.class, params);
        assertEquals(400, response.getStatus());
        assertTrue("Response should contain 'weak' term", response.getEntity(String.class).toLowerCase().contains("weak"));
    }

    /**
     * createClientTestWithInvalidCharacters
     * https://github.com/RestComm/Restcomm-Connect/issues/1979
     * @throws ClientProtocolException
     * @throws IOException
     * @throws ParseException
     * @throws InterruptedException
     */
    @Test@Category(FeatureExpTests.class)
    public void createClientTestWithInvalidCharacters() throws ClientProtocolException, IOException, ParseException, InterruptedException {
    	Client jersey = getClient(developerUsername, developeerAuthToken);
        WebResource resource = jersey.resource( getResourceUrl("/2012-04-24/Accounts/" + developerAccountSid + "/Clients.json" ) );
        MultivaluedMap<String, String> params = new MultivaluedMapImpl();
        params.add("Login","maria.test@telestax.com"); // login contains @ sign
        params.add("Password","RestComm1234!");
        ClientResponse response = resource.accept(MediaType.APPLICATION_JSON).post(ClientResponse.class, params);
        assertEquals(400, response.getStatus());
        assertTrue("Response should contain 'invalid' term", response.getEntity(String.class).toLowerCase().contains("invalid"));

        params = new MultivaluedMapImpl();
        params.add("Login","maria.test?"); // login contains @ sign
        params.add("Password","RestComm1234!");
        response = resource.accept(MediaType.APPLICATION_JSON).post(ClientResponse.class, params);
        assertEquals(400, response.getStatus());
        assertTrue("Response should contain 'invalid' term", response.getEntity(String.class).toLowerCase().contains("invalid"));

        params = new MultivaluedMapImpl();
        params.add("Login","maria.test="); // login contains @ sign
        params.add("Password","RestComm1234!");
        response = resource.accept(MediaType.APPLICATION_JSON).post(ClientResponse.class, params);
        assertEquals(400, response.getStatus());
        assertTrue("Response should contain 'invalid' term", response.getEntity(String.class).toLowerCase().contains("invalid"));
    }

    /**
     * addSameClientNameInDifferentOrganizations
     * https://github.com/RestComm/Restcomm-Connect/issues/2106
     * We should be able to add same client in different organizations
     *
     */
    @Test
    public void addSameClientNameInDifferentOrganizations() {
    	/*
    	 * Add client maria in Organization - default.restcomm.com
    	 */
    	Client jersey = getClient(developerUsername, developeerAuthToken);
        WebResource resource = jersey.resource( getResourceUrl("/2012-04-24/Accounts/" + developerAccountSid + "/Clients.json" ) );
        MultivaluedMap<String, String> params = new MultivaluedMapImpl();
        params.add("Login","maria");
        params.add("Password","RestComm1234!");
        ClientResponse response = resource.accept(MediaType.APPLICATION_JSON).post(ClientResponse.class, params);
        assertEquals(200, response.getStatus());

        //try to add same client again in same organization - should not be allowed
        /*jersey = getClient(developerUsername, developeerAuthToken);
        resource = jersey.resource( getResourceUrl("/2012-04-24/Accounts/" + developerAccountSid + "/Clients.json" ) );
        params = new MultivaluedMapImpl();
        params.add("Login","maria");
        params.add("Password","RestComm1234!");
        response = resource.accept(MediaType.APPLICATION_JSON).post(ClientResponse.class, params);
        assertEquals(409, response.getStatus());*/

        /*
    	 * Add client maria in Organization - org2.restcomm.com
    	 */
        jersey = getClient(developerOrg2AccountSid, developeerOrg2AuthToken);
        resource = jersey.resource( getResourceUrl("/2012-04-24/Accounts/" + developerOrg2AccountSid + "/Clients.json" ) );
        params = new MultivaluedMapImpl();
        params.add("Login","maria");
        params.add("Password","RestComm1234!");
        response = resource.accept(MediaType.APPLICATION_JSON).post(ClientResponse.class, params);
        assertEquals(200, response.getStatus());

    }

    @Test@Category({FeatureAltTests.class, UnstableTests.class})
    public void createClientTestWithIsPushEnabled() throws IOException, ParseException, InterruptedException {
        Client jersey = getClient(developerUsername, developeerAuthToken);
        WebResource resource = jersey.resource( getResourceUrl("/2012-04-24/Accounts/" + developerAccountSid + "/Clients.json" ) );
        MultivaluedMap<String, String> params = new MultivaluedMapImpl();
        params.add("Login","bob"); // login contains @ sign
        params.add("Password","RestComm1234!");
        params.add("IsPushEnabled", "true");
        ClientResponse response = resource.accept(MediaType.APPLICATION_JSON).post(ClientResponse.class, params);
        assertEquals(200, response.getStatus());
        assertTrue("Response should contain 'push_client_identity'", response.getEntity(String.class).contains("push_client_identity"));
    }

    @Test@Category(FeatureAltTests.class)
    public void updateClientTestWithIsPushEnabled() throws IOException, ParseException, InterruptedException {
        String sid = CreateClientsTool.getInstance().createClient(deploymentUrl.toString(), developerAccountSid, developeerAuthToken, "agafox", "RestComm1234", null);
        // add push_client_identity
        Client jersey = getClient(developerUsername, developeerAuthToken);
        WebResource resource = jersey.resource(getResourceUrl("/2012-04-24/Accounts/" + developerAccountSid + "/Clients/" + sid + ".json" ));
        MultivaluedMap<String, String> params = new MultivaluedMapImpl();
        params.putSingle("IsPushEnabled", "true");
        ClientResponse response = resource.accept(MediaType.APPLICATION_JSON).put(ClientResponse.class, params);
        assertEquals(200, response.getStatus());
        assertTrue("Response should contain 'push_client_identity'", response.getEntity(String.class).contains("push_client_identity"));
        // remove push_client_identity
        resource = jersey.resource(getResourceUrl("/2012-04-24/Accounts/" + developerAccountSid + "/Clients/" + sid + ".json"));
        params.putSingle("IsPushEnabled", "false");
        response = resource.accept(MediaType.APPLICATION_JSON).put(ClientResponse.class, params);
        assertEquals(200, response.getStatus());
        assertFalse("Response shouldn't contain 'push_client_identity'", response.getEntity(String.class).contains("push_client_identity"));
    }

    protected String getResourceUrl(String suffix) {
        String urlString = deploymentUrl.toString();
        if ( urlString.endsWith("/") )
            urlString = urlString.substring(0,urlString.length()-1);

        if ( suffix != null && !suffix.isEmpty()) {
            if (!suffix.startsWith("/"))
                suffix = "/" + suffix;
            return urlString + suffix;
        } else
            return urlString;

    }

    protected Client getClient(String username, String password) {
        Client jersey = Client.create();
        jersey.addFilter(new HTTPBasicAuthFilter(username, password));
        return jersey;
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
        archive.addAsWebInfResource("restcomm.script_clients_test", "data/hsql/restcomm.script");
        return archive;
    }
}
