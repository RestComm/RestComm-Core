package org.restcomm.connect.testsuite.http;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import javax.ws.rs.core.MediaType;

import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.Deployer;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.junit.Test;
import org.junit.FixMethodOrder;
import org.junit.runners.MethodSorters;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.restcomm.connect.commons.Version;
import org.restcomm.connect.commons.annotations.FeatureExpTests;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.filter.HTTPBasicAuthFilter;

@RunWith(Arquillian.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class RestcommAPIEndpointSecurityTest {
    private final static Logger logger = Logger.getLogger(RestcommAPIEndpointSecurityTest.class.getName());

    private static final String version = Version.getVersion();

    @ArquillianResource
    private Deployer deployer;
    @ArquillianResource
    URL deploymentUrl;

    private static final String SUPER_ADMIN_ACCOUNT_SID = "ACae6e420f425248d6a26948c17a9e2acf";
    private static final String CLOSED_ACCOUNT_SID="ACA3000000000000000000000000000000";
    private static final String SUSPENDED_ACCOUNT_SID="ACA4000000000000000000000000000000";
    private static final String AUTH_TOKEN = "77f8c12cc7b8f8423e5c38b035249166";
    private static final String RESOURCE_SID = "PRae6e420f425248d6a26948c17a9e2acf";
    private static final String GENERIC_ENDPOINT = "/2012-04-24/";
    private static final String PROFILE_ENDPOINT = "/2012-04-24/Profiles";
    private static final String ACCOUNT_ENDPOINT = "/2012-04-24/Accounts";
    private static final String ORGANIZATION_ENDPOINT = "/2012-04-24/Organizations";
    private static final String CLIENTS_ENDPOINT = "/2012-04-24/Accounts/"+SUPER_ADMIN_ACCOUNT_SID+"/Clients";
    private static final String RECORDINGS_ENDPOINT_FILE_PATH = "/2012-04-24/Accounts/"+SUPER_ADMIN_ACCOUNT_SID+"/Recordings/REc267d9cdcd0f4623a54abedf8e3d8835";
    private static final String RECORDINGS_ENDPOINT = "/2012-04-24/Accounts/"+SUPER_ADMIN_ACCOUNT_SID+"/Recordings";
    private static final String RECORDINGS_ENDPOINT_FILE_FULL_PATH = "http://127.0.0.1:8080/restcomm/2012-04-24/Accounts/"+SUPER_ADMIN_ACCOUNT_SID+"/Recordings/REc267d9cdcd0f4623a54abedf8e3d8835";

    /**
     * this test will try to access generic EP Without Authentication or invalid token
     */
    @Test
    public void genericSecurityTest(){
    	assertEquals(401, performUnautherizedRequest(deploymentUrl.toString()+GENERIC_ENDPOINT));
    	assertEquals(401, performRequestWithInvalidToken(deploymentUrl.toString()+GENERIC_ENDPOINT));
    	assertEquals(403, performApiRequest(deploymentUrl.toString()+GENERIC_ENDPOINT, CLOSED_ACCOUNT_SID, AUTH_TOKEN));
    	assertEquals(403, performApiRequest(deploymentUrl.toString()+GENERIC_ENDPOINT, SUSPENDED_ACCOUNT_SID, AUTH_TOKEN));
    }
    /**
     * this test will try to access org EP Without Authentication or invalid token
     */
    @Test
    @Category(FeatureExpTests.class)
    public void organizationSecurityTest(){
    	assertEquals(401, performUnautherizedRequest(deploymentUrl.toString()+ORGANIZATION_ENDPOINT+"/"+RESOURCE_SID));
    	assertEquals(401, performUnautherizedRequest(deploymentUrl.toString()+ORGANIZATION_ENDPOINT));
    	assertEquals(401, performRequestWithInvalidToken(deploymentUrl.toString()+ORGANIZATION_ENDPOINT));
    	assertEquals(403, performApiRequest(deploymentUrl.toString()+ORGANIZATION_ENDPOINT, CLOSED_ACCOUNT_SID, AUTH_TOKEN));
    	assertEquals(403, performApiRequest(deploymentUrl.toString()+ORGANIZATION_ENDPOINT, SUSPENDED_ACCOUNT_SID, AUTH_TOKEN));
    }

    /**
     * this test will try to access acc EP Without Authentication or invalid token
     */
    @Test
    @Category(FeatureExpTests.class)
    public void accountSecurityTest(){
    	assertEquals(401, performUnautherizedRequest(deploymentUrl.toString()+ACCOUNT_ENDPOINT+"/"+RESOURCE_SID));
    	assertEquals(401, performUnautherizedRequest(deploymentUrl.toString()+ACCOUNT_ENDPOINT));
    	assertEquals(401, performRequestWithInvalidToken(deploymentUrl.toString()+ACCOUNT_ENDPOINT));
    	assertEquals(403, performApiRequest(deploymentUrl.toString()+ACCOUNT_ENDPOINT, CLOSED_ACCOUNT_SID, AUTH_TOKEN));
    	assertEquals(403, performApiRequest(deploymentUrl.toString()+ACCOUNT_ENDPOINT, SUSPENDED_ACCOUNT_SID, AUTH_TOKEN));
    }
    

    /**
     * this test will try to access profile EP Without Authentication or invalid token
     */
    @Test
    @Category(FeatureExpTests.class)
    public void profileSecurityTest(){
    	assertEquals(401, performUnautherizedRequest(deploymentUrl.toString()+PROFILE_ENDPOINT+"/"+RESOURCE_SID));
    	assertEquals(401, performUnautherizedRequest(deploymentUrl.toString()+PROFILE_ENDPOINT));
    	assertEquals(401, performRequestWithInvalidToken(deploymentUrl.toString()+PROFILE_ENDPOINT));
    	assertEquals(403, performApiRequest(deploymentUrl.toString()+PROFILE_ENDPOINT, CLOSED_ACCOUNT_SID, AUTH_TOKEN));
    	assertEquals(403, performApiRequest(deploymentUrl.toString()+PROFILE_ENDPOINT, SUSPENDED_ACCOUNT_SID, AUTH_TOKEN));
    }
    
    /**
     * this test will try to access client EP Without Authentication or invalid token
     */
    @Test
    @Category(FeatureExpTests.class)
    public void clientSecurityTest(){
    	assertEquals(401, performUnautherizedRequest(deploymentUrl.toString()+CLIENTS_ENDPOINT+"/"+RESOURCE_SID));
    	assertEquals(401, performUnautherizedRequest(deploymentUrl.toString()+CLIENTS_ENDPOINT));
    	assertEquals(401, performRequestWithInvalidToken(deploymentUrl.toString()+CLIENTS_ENDPOINT));
    	assertEquals(403, performApiRequest(deploymentUrl.toString()+CLIENTS_ENDPOINT, CLOSED_ACCOUNT_SID, AUTH_TOKEN));
    	assertEquals(403, performApiRequest(deploymentUrl.toString()+CLIENTS_ENDPOINT, SUSPENDED_ACCOUNT_SID, AUTH_TOKEN));
    }

    /**
     * this test will try to access recording endpoint
     * we allow audio video file to be accessed, but recording list and single resource description is protected
     * we will need to change this test if https://telestax.atlassian.net/browse/RESTCOMM-1736 is implemented
     * @throws IOException 
     */
    @Test
    public void recordingSecurityTest() throws IOException{
    	//recording list is protected
    	assertEquals(401, performUnautherizedRequest(deploymentUrl.toString()+RECORDINGS_ENDPOINT));
    	assertEquals(401, performRequestWithInvalidToken(deploymentUrl.toString()+RECORDINGS_ENDPOINT));
    	assertEquals(403, performApiRequest(deploymentUrl.toString()+RECORDINGS_ENDPOINT, CLOSED_ACCOUNT_SID, AUTH_TOKEN));
    	assertEquals(403, performApiRequest(deploymentUrl.toString()+RECORDINGS_ENDPOINT, SUSPENDED_ACCOUNT_SID, AUTH_TOKEN));

    	//recording resource is protected    	
    	assertEquals(401, performUnautherizedRequest(deploymentUrl.toString()+RECORDINGS_ENDPOINT_FILE_PATH));
    	assertEquals(401, performRequestWithInvalidToken(deploymentUrl.toString()+RECORDINGS_ENDPOINT_FILE_PATH));
    	assertEquals(403, performApiRequest(deploymentUrl.toString()+RECORDINGS_ENDPOINT_FILE_PATH, CLOSED_ACCOUNT_SID, AUTH_TOKEN));
    	assertEquals(403, performApiRequest(deploymentUrl.toString()+RECORDINGS_ENDPOINT_FILE_PATH, SUSPENDED_ACCOUNT_SID, AUTH_TOKEN));
    	
    	//recording audio file is not protected, we consider 404 equivalent to 200 as that means we already bypassed 401 and 403 and the fact that file does not exists
    	assertEquals(404, performUnautherizedRequest(deploymentUrl.toString()+RECORDINGS_ENDPOINT_FILE_PATH+".wav"));
    	assertEquals(404, performRequestWithInvalidToken(deploymentUrl.toString()+RECORDINGS_ENDPOINT_FILE_PATH+".wav"));
    	assertEquals(404, performApiRequest(deploymentUrl.toString()+RECORDINGS_ENDPOINT_FILE_PATH+".wav", CLOSED_ACCOUNT_SID, AUTH_TOKEN));
    	assertEquals(404, performApiRequest(deploymentUrl.toString()+RECORDINGS_ENDPOINT_FILE_PATH+".wav", SUSPENDED_ACCOUNT_SID, AUTH_TOKEN));

    	//recording video file is not protected, we consider 404 equivalent to 200 as that means we already bypassed 401 and 403 and the fact that file does not exists
    	assertEquals(404, performUnautherizedRequest(deploymentUrl.toString()+RECORDINGS_ENDPOINT_FILE_PATH+".mp4"));
    	assertEquals(404, performRequestWithInvalidToken(deploymentUrl.toString()+RECORDINGS_ENDPOINT_FILE_PATH+".mp4"));
    	assertEquals(404, performApiRequest(deploymentUrl.toString()+RECORDINGS_ENDPOINT_FILE_PATH+".mp4", CLOSED_ACCOUNT_SID, AUTH_TOKEN));
    	assertEquals(404, performApiRequest(deploymentUrl.toString()+RECORDINGS_ENDPOINT_FILE_PATH+".mp4", SUSPENDED_ACCOUNT_SID, AUTH_TOKEN));

    	//access by simple http url connection
		URL url = new URL(RECORDINGS_ENDPOINT_FILE_FULL_PATH+".wav");
		HttpURLConnection connection = (HttpURLConnection)url.openConnection();
		connection.setRequestMethod("GET");
		connection.connect();
		assertEquals(404, connection.getResponseCode());
    }

    /**
     * perform api Request with account
     * 
     * @param endpointUrl
     * @return
     */
    private int performApiRequest(String endpointUrl, String account, String auth){
    	Client jerseyClient = Client.create();
        jerseyClient.addFilter(new HTTPBasicAuthFilter(CLOSED_ACCOUNT_SID, AUTH_TOKEN));
        WebResource webResource = jerseyClient.resource(endpointUrl);
        ClientResponse clientResponse = webResource.accept(MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML).get(ClientResponse.class);
        return clientResponse.getStatus();
    }

    /**
     * performUnautherizedRequest with no authentication
     * 
     * @param endpointUrl
     * @return
     */
    private int performUnautherizedRequest(String endpointUrl){
    	Client jerseyClient = Client.create();
		WebResource webResource = jerseyClient.resource(endpointUrl);
		ClientResponse clientResponse = webResource.accept(MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML).get(ClientResponse.class);
		return clientResponse.getStatus();
    }

    /**
     * perform request with Invalid Token
     * @param endpointUrl
     * @return
     */
    private int performRequestWithInvalidToken(String endpointUrl){
    	Client jerseyClient = Client.create();
    	jerseyClient.addFilter(new HTTPBasicAuthFilter(SUPER_ADMIN_ACCOUNT_SID, "wrongauthtoken"));
		WebResource webResource = jerseyClient.resource(endpointUrl);
		ClientResponse clientResponse = webResource.accept(MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML).get(ClientResponse.class);
		return clientResponse.getStatus();
    }

    @Deployment(name = "RestcommAPIEndpointSecurityTest", managed = true, testable = false)
    public static WebArchive createWebArchiveNoGw() {
        logger.info("Packaging Test App");
        logger.info("version");
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
        archive.addAsWebInfResource("restcomm.script_accounts_test", "data/hsql/restcomm.script");
        logger.info("Packaged Test App");
        return archive;
    }
}
