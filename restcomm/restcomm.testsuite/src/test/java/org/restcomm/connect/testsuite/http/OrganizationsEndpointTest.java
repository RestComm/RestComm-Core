package org.restcomm.connect.testsuite.http;

import static org.junit.Assert.assertTrue;

import java.net.URL;

import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.Deployer;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.FixMethodOrder;
import org.junit.runners.MethodSorters;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.restcomm.connect.commons.Version;
import org.restcomm.connect.commons.annotations.FeatureExpTests;
import org.restcomm.connect.commons.annotations.UnstableTests;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.jersey.api.client.ClientResponse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * @author maria
 */

@RunWith(Arquillian.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class OrganizationsEndpointTest extends EndpointTest {
    private final static Logger logger = Logger.getLogger(OrganizationsEndpointTest.class.getName());

    private static final String version = Version.getVersion();

    @ArquillianResource
    private Deployer deployer;
    @ArquillianResource
    URL deploymentUrl;

    //super admin account
    private String superAdminAccountSid = "ACae6e420f425248d6a26948c17a9e2acf";
    private String superAdminAuthToken = "77f8c12cc7b8f8423e5c38b035249166";

    //admin account
    private String adminAccountSid = "AC574d775522c96f9aacacc5ca60c8c74g";
    private String adminAuthToken = "77f8c12cc7b8f8423e5c38b035249166";

    //developer account
    private String devAccountSid = "AC574d775522c96f9aacacc5ca60c8c74f";
    private String devAuthToken = "77f8c12cc7b8f8423e5c38b035249166";

    private final String org1 = "ORafbe225ad37541eba518a74248f0ac4c";
    private final String org2 = "ORafbe225ad37541eba518a74248f0ac4d";

    @BeforeClass
    public static void beforeClass() {
    }

    /**
     * SuperAdmin is allowed to read any organization
     * this test will try to Read single organization and read list
     */
    @Test
    @Category(UnstableTests.class)
    public void getOrganizationFromSuperAdminAccount(){
    	JsonObject organizationJsonObject = RestcommOrganizationsTool.getInstance().getOrganization(deploymentUrl.toString(), superAdminAccountSid, superAdminAuthToken, org1);
    	assertTrue(organizationJsonObject!=null);
    	logger.info("organization: "+organizationJsonObject);

    	// only superadmin can read an org that does not affiliate with its account
    	organizationJsonObject = null;
    	organizationJsonObject = RestcommOrganizationsTool.getInstance().getOrganization(deploymentUrl.toString(), superAdminAccountSid, superAdminAuthToken, org2);
    	assertTrue(organizationJsonObject!=null);

    	//only superadmin can read the whole list of organizations
    	JsonArray jsonArray = null;
    	jsonArray = RestcommOrganizationsTool.getInstance().getOrganizationList(deploymentUrl.toString(), superAdminAccountSid, superAdminAuthToken, null);
    	logger.info("organization list: "+jsonArray);
    	assertTrue(jsonArray!=null);
    	assertTrue(jsonArray.size() == 3);
    }

    /**
     * Administrators can read only affiliated organization
     * this test will try to Read single organization and read list
     */
    @Test
    public void getOrganizationFromAdministratorAccount(){
    	ClientResponse clientResponse = RestcommOrganizationsTool.getInstance().getOrganizationResponse(deploymentUrl.toString(), adminAccountSid, adminAuthToken, org1);
    	assertNotNull(clientResponse);
    	logger.info("organization: "+clientResponse);
    	assertEquals(200 , clientResponse.getStatus());

    	// only superadmin can read an org that does not affiliate with its account
    	clientResponse = null;
    	clientResponse = RestcommOrganizationsTool.getInstance().getOrganizationResponse(deploymentUrl.toString(), adminAccountSid, adminAuthToken, org2);
    	assertTrue(clientResponse!=null);
    	logger.info("organization: "+clientResponse);
    	assertEquals(403, 403);

    	//only superadmin can read the whole list of organizations
    	clientResponse = null;
    	clientResponse = RestcommOrganizationsTool.getInstance().getOrganizationsResponse(deploymentUrl.toString(), adminAccountSid, adminAuthToken);
    	logger.info("organization list: "+clientResponse);
    	assertTrue(clientResponse!=null);
    	assertEquals(403, clientResponse.getStatus());

    }
    /**
     * Developers can not read organization
     * this test will try to Read single organization and read list
     */
    @Test
    @Category(FeatureExpTests.class)
    public void getOrganizationFromDeveloperAccount(){
    	ClientResponse clientResponse = RestcommOrganizationsTool.getInstance().getOrganizationResponse(deploymentUrl.toString(), devAccountSid, devAuthToken, org1);
    	assertTrue(clientResponse!=null);
    	logger.info("organization: "+clientResponse);
    	assertTrue(clientResponse.getStatus() == 403);

    	// only superadmin can read an org that does not affiliate with its account
    	clientResponse = null;
    	clientResponse = RestcommOrganizationsTool.getInstance().getOrganizationResponse(deploymentUrl.toString(), devAccountSid, devAuthToken, org2);
    	assertTrue(clientResponse!=null);
    	logger.info("organization: "+clientResponse);
    	assertTrue(clientResponse.getStatus() == 403);

    	//only superadmin can read the whole list of organizations
    	clientResponse = null;
    	clientResponse = RestcommOrganizationsTool.getInstance().getOrganizationsResponse(deploymentUrl.toString(), devAccountSid, devAuthToken);
    	logger.info("organization list: "+clientResponse);
    	assertTrue(clientResponse!=null);
    	assertTrue(clientResponse.getStatus() == 403);

    }

    /**
     * getOrganizationListByStatus
     */
    @Test
    @Category({UnstableTests.class})
    public void getOrganizationListByStatus(){
    	JsonArray jsonArray = null;
    	jsonArray = RestcommOrganizationsTool.getInstance().getOrganizationList(deploymentUrl.toString(), superAdminAccountSid, superAdminAuthToken, "active");
    	logger.info("organization list: "+jsonArray);
    	assertTrue(jsonArray!=null);
    	assertTrue(jsonArray.size() == 2);
    	jsonArray = null;
    	jsonArray = RestcommOrganizationsTool.getInstance().getOrganizationList(deploymentUrl.toString(), superAdminAccountSid, superAdminAuthToken, "closed");
    	logger.info("organization list: "+jsonArray);
    	assertTrue(jsonArray!=null);
    	assertTrue(jsonArray.size() == 1);
    }

    /**
     * createOrganizationTest
     * only super admin can create a new organization
     */
    @Test
    public void createOrganizationTest(){
    	//super admin tries to create org
    	ClientResponse clientResponse = RestcommOrganizationsTool.getInstance().createOrganizationResponse(deploymentUrl.toString(), superAdminAccountSid, superAdminAuthToken, "newdomain");
    	logger.info("clientResponse: "+clientResponse);
    	assertTrue(clientResponse.getStatus() == 200);

    	// create an org that domain name already exists
    	//super admin tries to create org that already exists
    	clientResponse = RestcommOrganizationsTool.getInstance().createOrganizationResponse(deploymentUrl.toString(), superAdminAccountSid, superAdminAuthToken, "newdomain");
    	logger.info("clientResponse: "+clientResponse);
    	assertTrue(clientResponse.getStatus() == 409);
    	//admin tries to create org
    	clientResponse = RestcommOrganizationsTool.getInstance().createOrganizationResponse(deploymentUrl.toString(), adminAccountSid, adminAuthToken, "newdomain");
    	logger.info("clientResponse: "+clientResponse);
    	assertTrue(clientResponse.getStatus() == 403);
    	//developer tries to create org
    	clientResponse = RestcommOrganizationsTool.getInstance().createOrganizationResponse(deploymentUrl.toString(), devAccountSid, devAuthToken, "newdomain");
    	logger.info("clientResponse: "+clientResponse);
    	assertTrue(clientResponse.getStatus() == 403);
    }

    @Deployment(name = "OrganizationsEndpointTest", managed = true, testable = false)
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
