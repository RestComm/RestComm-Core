package org.restcomm.connect.testsuite.http;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;


import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.Deployer;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.junit.Before;
import org.junit.Test;
import org.junit.FixMethodOrder;
import org.junit.runners.MethodSorters;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.restcomm.connect.commons.Version;
import org.restcomm.connect.commons.annotations.BrokenTests;
import org.restcomm.connect.commons.annotations.FeatureAltTests;
import org.restcomm.connect.commons.annotations.FeatureExpTests;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResourceLinkHeaders;
import com.sun.jersey.core.header.LinkHeader;

/**
 * @author maria
 */

@RunWith(Arquillian.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class ProfilesEndpointTest extends EndpointTest {
    private final static Logger logger = Logger.getLogger(ProfilesEndpointTest.class.getName());

    private static final String version = Version.getVersion();

    @ArquillianResource
    private Deployer deployer;
    @ArquillianResource
    URL deploymentUrl;

    private static final String SUPER_ADMIN_ACCOUNT_SID = "ACae6e420f425248d6a26948c17a9e2acf";
    private static final String ADMIN_ACCOUNT_SID = "AC574d775522c96f9aacacc5ca60c8c74g";
    private static final String DEVELOPER_ACCOUNT_SID = "AC574d775522c96f9aacacc5ca60c8c74f";
    private static final String AUTH_TOKEN = "77f8c12cc7b8f8423e5c38b035249166";

    private static final String DEFAULT_PROFILE_SID = "PRae6e420f425248d6a26948c17a9e2acf";
    private static final String SECONDARY_PROFILE_SID = "PRae6e420f425248d6a26948c17a9e2acg";
    private static final String UNKNOWN_PROFILE_SID = "PRafbe225ad37541eba518a74248f0ac4d";
    private static final String ORGANIZATION_SID = "ORafbe225ad37541eba518a74248f0ac4c";
    private static final String UNKNOWN_ACCOUNT_SID = "AC1111225ad37541eba518a74248f0ac4d";
    private static final String UNKNOWN_ORGANIZATION_SID = "OR1111225ad37541eba518a74248f0ac4d";

    private static final String PROFILE_DOCUMENT="{ \"featureEnablement\": { \"DIDPurchase\": { \"allowedCountries\": [\"US\", \"CA\"] },  \"outboundPSTN\": { }, \"inboundPSTN\": { }, \"outboundSMS\": { }, \"inboundSMS\": { } }, \"sessionThrottling\": { \"PSTNCallsPerTime\": { \"events\" : 300, \"time\" : 30, \"timeUnit\" : \"days\" } } }";
    private static final String UPDATE_PROFILE_DOCUMENT="{ \"featureEnablement\": { \"DIDPurchase\": { \"allowedCountries\": [\"PK\", \"CA\"] },  \"outboundPSTN\": { }, \"inboundPSTN\": { }, \"outboundSMS\": { }, \"inboundSMS\": { } }, \"sessionThrottling\": { \"PSTNCallsPerTime\": { \"events\" : 300, \"time\" : 30, \"timeUnit\" : \"days\" } } }";
    private static final String INVALID_PROFILE_DOCUMENT="{ \"featureEnablement\": { \"DIDPurchase\": { \"allowedCountries\": [\"PKeuietue\", \"CA\"] },  \"outboundPSTN\": { }, \"inboundPSTN\": { }, \"outboundSMS\": { }, \"inboundSMS\": { } }, \"sessionThrottling\": { \"PSTNCallsPerTime\": { \"events\" : 300, \"time\" : 30, \"timeUnit\" : \"days\" } } }";

    @Before
    public void before() {
    }

    /**
     * this test will try to Read single profile
     */
    @Test
    public void getProfile(){
    	ClientResponse clientResponse = RestcommProfilesTool.getInstance().getProfileResponse(deploymentUrl.toString(), SUPER_ADMIN_ACCOUNT_SID, AUTH_TOKEN, DEFAULT_PROFILE_SID);
        String responseBody = clientResponse.getEntity(String.class);
    	logger.info("profile: "+responseBody);
    	assertEquals(200, clientResponse.getStatus());

    	JsonObject jsonResponse = new JsonParser().parse(responseBody).getAsJsonObject();
    	assertNotNull(jsonResponse);
    }

    /**
     * getProfileList
     */
    @Test
    public void getProfileList() {
    	ClientResponse clientResponse = RestcommProfilesTool.getInstance().getProfileListClientResponse(deploymentUrl.toString(), SUPER_ADMIN_ACCOUNT_SID, AUTH_TOKEN);
        String responseBody = clientResponse.getEntity(String.class);
    	logger.info("profile list: "+responseBody);
    	assertNotNull(clientResponse);
    	assertEquals(200, clientResponse.getStatus());

    	JsonArray jsonArray = new JsonParser().parse(responseBody).getAsJsonArray();
    	assertNotNull(jsonArray);
    	//as we have one default and one secondary profile and based on other tests creating profiles, we may have more
    	assertTrue(jsonArray.size()>=2);
    }

    /**
     * this test will try to Read single profile with an unknown profile Sid
     */
    @Test
    @Category(FeatureExpTests.class)
    public void getProfileUnknownSid(){
    	ClientResponse clientResponse = RestcommProfilesTool.getInstance().getProfileResponse(deploymentUrl.toString(), SUPER_ADMIN_ACCOUNT_SID, AUTH_TOKEN, UNKNOWN_PROFILE_SID);
    	assertNotNull(clientResponse);
    	assertEquals(404, clientResponse.getStatus());
    }

    /**
     * super admin can read any profile
     * others can read profile applicatble only to them
     */
    @Test
    @Category(FeatureExpTests.class)
    public void getProfilePermissionTest(){
    	// super admin account gets associated profile - should be able to get it
    	ClientResponse clientResponse = RestcommProfilesTool.getInstance().getProfileResponse(deploymentUrl.toString(), SUPER_ADMIN_ACCOUNT_SID, AUTH_TOKEN, DEFAULT_PROFILE_SID);
    	assertNotNull(clientResponse);
    	assertEquals(200, clientResponse.getStatus());
    	// super admin account gets not associated profile - should be able to get it
    	clientResponse = RestcommProfilesTool.getInstance().getProfileResponse(deploymentUrl.toString(), SUPER_ADMIN_ACCOUNT_SID, AUTH_TOKEN, SECONDARY_PROFILE_SID);
    	assertNotNull(clientResponse);
    	assertEquals(200, clientResponse.getStatus());
    	// admin account gets associated profile - should be able to get it
    	clientResponse = RestcommProfilesTool.getInstance().getProfileResponse(deploymentUrl.toString(), ADMIN_ACCOUNT_SID, AUTH_TOKEN, SECONDARY_PROFILE_SID);
    	assertNotNull(clientResponse);
    	assertEquals(200, clientResponse.getStatus());
    	// admin account gets not associated profile - should not be able to get it
    	clientResponse = RestcommProfilesTool.getInstance().getProfileResponse(deploymentUrl.toString(), ADMIN_ACCOUNT_SID, AUTH_TOKEN, DEFAULT_PROFILE_SID);
    	assertNotNull(clientResponse);
    	assertEquals(403, clientResponse.getStatus());
    	// dev account gets associated profile that is not explicitly assign to it but associated by inheritance - should be able to get it
    	clientResponse = RestcommProfilesTool.getInstance().getProfileResponse(deploymentUrl.toString(), DEVELOPER_ACCOUNT_SID, AUTH_TOKEN, DEFAULT_PROFILE_SID);
    	assertNotNull(clientResponse);
    	assertEquals(200, clientResponse.getStatus());
    	// dev account gets un associated profile - should not be able to get it
    	clientResponse = RestcommProfilesTool.getInstance().getProfileResponse(deploymentUrl.toString(), DEVELOPER_ACCOUNT_SID, AUTH_TOKEN, SECONDARY_PROFILE_SID);
    	assertNotNull(clientResponse);
    	assertEquals(403, clientResponse.getStatus());

    }

    /**
     * Create, Read And Update Profile Test
     */
    @Test
    public void createReadUpdateDeleteProfileTest() throws IOException, URISyntaxException{
    	/*
		 * create a profile
		 */
    	ClientResponse clientResponse = RestcommProfilesTool.getInstance().createProfileResponse(deploymentUrl.toString(), SUPER_ADMIN_ACCOUNT_SID, AUTH_TOKEN, PROFILE_DOCUMENT);
    	assertEquals(201, clientResponse.getStatus());
    	assertEquals(PROFILE_DOCUMENT, clientResponse.getEntity(String.class));

    	//extract profileSid of newly created profile Sid.
    	URI location = clientResponse.getLocation();
    	assertNotNull(location);
    	String profileLocation  = location.toString();
    	String[] profileUriElements = profileLocation.split("/");
    	assertNotNull(profileUriElements);
    	String newlyCreatedProfileSid = profileUriElements[profileUriElements.length-1];

        /**
         * link default profile to dev account
         */
        HttpResponse response = RestcommProfilesTool.getInstance().linkProfile(deploymentUrl.toString(), SUPER_ADMIN_ACCOUNT_SID, AUTH_TOKEN, newlyCreatedProfileSid, SUPER_ADMIN_ACCOUNT_SID, RestcommProfilesTool.AssociatedResourceType.ACCOUNT);
        logger.info("HttpResponse: "+response);
        assertEquals(200, response.getStatusLine().getStatusCode());

    	/*
		 * read newly created profile
		 */
    	clientResponse = RestcommProfilesTool.getInstance().getProfileResponse(deploymentUrl.toString(), SUPER_ADMIN_ACCOUNT_SID, AUTH_TOKEN, newlyCreatedProfileSid);
    	assertEquals(200, clientResponse.getStatus());
    	assertEquals(PROFILE_DOCUMENT, clientResponse.getEntity(String.class));

    	/*
		 * update the profile
		 */
    	clientResponse = RestcommProfilesTool.getInstance().updateProfileResponse(deploymentUrl.toString(), SUPER_ADMIN_ACCOUNT_SID, AUTH_TOKEN, newlyCreatedProfileSid, UPDATE_PROFILE_DOCUMENT);
    	assertEquals(200, clientResponse.getStatus());
    	assertEquals(UPDATE_PROFILE_DOCUMENT, clientResponse.getEntity(String.class));

        /*
		 * unlink a profile from an account
		 */
    	response = RestcommProfilesTool.getInstance().unLinkProfileWithOverride(deploymentUrl.toString(), SUPER_ADMIN_ACCOUNT_SID, AUTH_TOKEN, newlyCreatedProfileSid, SUPER_ADMIN_ACCOUNT_SID, RestcommProfilesTool.AssociatedResourceType.ACCOUNT);
    	logger.info("HttpResponse: "+response);
    	assertEquals(200, response.getStatusLine().getStatusCode());

    	/*
		 * delete the profile
		 */
    	clientResponse = RestcommProfilesTool.getInstance().deleteProfileResponse(deploymentUrl.toString(), SUPER_ADMIN_ACCOUNT_SID, AUTH_TOKEN, newlyCreatedProfileSid);
    	assertEquals(200, clientResponse.getStatus());

    	/*
		 * read again
		 */
    	clientResponse = RestcommProfilesTool.getInstance().getProfileResponse(deploymentUrl.toString(), SUPER_ADMIN_ACCOUNT_SID, AUTH_TOKEN, newlyCreatedProfileSid);
    	assertEquals(404, clientResponse.getStatus());

    }

    /**
     * createProfileTestInvalidSchema
     */
    @Test
    @Category(FeatureExpTests.class)
    public void createProfileTestInvalidSchema(){
    	/*
		 * create a profile with invalid schema
		 */
    	ClientResponse clientResponse = RestcommProfilesTool.getInstance().createProfileResponse(deploymentUrl.toString(), SUPER_ADMIN_ACCOUNT_SID, AUTH_TOKEN, INVALID_PROFILE_DOCUMENT);
    	assertEquals(400, clientResponse.getStatus());
    }

    /**
     * updateProfileUnknownSidTest
     */
    @Test
    @Category(FeatureExpTests.class)
    public void updateProfileUnknownSidTest(){
    	/*
		 * update a profile with unknown sid
		 */
    	ClientResponse clientResponse = RestcommProfilesTool.getInstance().updateProfileResponse(deploymentUrl.toString(), SUPER_ADMIN_ACCOUNT_SID, AUTH_TOKEN, UNKNOWN_PROFILE_SID, UPDATE_PROFILE_DOCUMENT);
    	assertEquals(404, clientResponse.getStatus());
    }

    /**
     * updateProfilePermissionTest
     */
    @Test
    @Category(FeatureExpTests.class)
    public void updateProfilePermissionTest(){
    	/*
		 * update a profile from admin account
		 */
    	ClientResponse clientResponse = RestcommProfilesTool.getInstance().updateProfileResponse(deploymentUrl.toString(), ADMIN_ACCOUNT_SID, AUTH_TOKEN, DEFAULT_PROFILE_SID, UPDATE_PROFILE_DOCUMENT);
    	assertEquals(403, clientResponse.getStatus());
    	/*
		 * update a profile from dev account
		 */
    	clientResponse = RestcommProfilesTool.getInstance().updateProfileResponse(deploymentUrl.toString(), DEVELOPER_ACCOUNT_SID, AUTH_TOKEN, DEFAULT_PROFILE_SID, UPDATE_PROFILE_DOCUMENT);
    	assertEquals(403, clientResponse.getStatus());
    }

    /**
     * updateDefaultProfileTest
     * updating default profile is not allowed
     */
    @Test
    @Category(FeatureExpTests.class)
    public void updateDefaultProfileTest(){
    	/*
		 * update default profile
		 */
    	ClientResponse clientResponse = RestcommProfilesTool.getInstance().updateProfileResponse(deploymentUrl.toString(), SUPER_ADMIN_ACCOUNT_SID, AUTH_TOKEN, DEFAULT_PROFILE_SID, UPDATE_PROFILE_DOCUMENT);
    	assertEquals(403, clientResponse.getStatus());
    }

    /**
     * deleteDefaultProfileTest
     * deleteing default profile is not allowed
     */
    @Test
    @Category(FeatureExpTests.class)
    public void deleteDefaultProfileTest(){
    	/*
		 * delete default profile
		 */
    	ClientResponse clientResponse = RestcommProfilesTool.getInstance().deleteProfileResponse(deploymentUrl.toString(), SUPER_ADMIN_ACCOUNT_SID, AUTH_TOKEN, DEFAULT_PROFILE_SID);
    	assertEquals(403, clientResponse.getStatus());
    }

    /**
     * deleteProfilePermissionTest
     */
    @Test
    @Category(FeatureExpTests.class)
    public void deleteProfilePermissionTest(){
    	/*
		 * delete a profile from admin account
		 */
    	ClientResponse clientResponse = RestcommProfilesTool.getInstance().deleteProfileResponse(deploymentUrl.toString(), ADMIN_ACCOUNT_SID, AUTH_TOKEN, DEFAULT_PROFILE_SID);
    	assertEquals(403, clientResponse.getStatus());
    	/*
		 * delete a profile from Dev account
		 */
    	clientResponse = RestcommProfilesTool.getInstance().deleteProfileResponse(deploymentUrl.toString(), DEVELOPER_ACCOUNT_SID, AUTH_TOKEN, DEFAULT_PROFILE_SID);
    	assertEquals(403, clientResponse.getStatus());
    }

    /**
     * deleteProfileUnknownSidTest
     */
    @Test
    @Category(FeatureExpTests.class)
    public void deleteProfileUnknownSidTest(){
    	/*
		 * delete a profile with unknown sid
		 */
    	ClientResponse clientResponse = RestcommProfilesTool.getInstance().deleteProfileResponse(deploymentUrl.toString(), SUPER_ADMIN_ACCOUNT_SID, AUTH_TOKEN, UNKNOWN_PROFILE_SID);
    	assertEquals(404, clientResponse.getStatus());
    }

    @Test
    @Category({FeatureExpTests.class, BrokenTests.class})
    public void createExceedingProfileTest(){
        String longProfile = new String(new char[10000001]);
    	//admin tries to create profile
	    ClientResponse  clientResponse = RestcommProfilesTool.getInstance().createProfileResponse(deploymentUrl.toString(), ADMIN_ACCOUNT_SID, AUTH_TOKEN, longProfile);
    	assertEquals(413, clientResponse.getStatus());
    }

    @Test
    @Category(FeatureExpTests.class)
    public void createProfilePermissionTest(){
    	//admin tries to create profile
    	ClientResponse  clientResponse = RestcommProfilesTool.getInstance().createProfileResponse(deploymentUrl.toString(), ADMIN_ACCOUNT_SID, AUTH_TOKEN, PROFILE_DOCUMENT);
    	assertEquals(403, clientResponse.getStatus());
    	//developer tries to create profile
    	clientResponse = RestcommProfilesTool.getInstance().createProfileResponse(deploymentUrl.toString(), DEVELOPER_ACCOUNT_SID, AUTH_TOKEN, PROFILE_DOCUMENT);
    	assertEquals(403, clientResponse.getStatus());
    }

    /**
     * link/unlink a give Profile To an Account
     * @throws IOException
     * @throws ClientProtocolException
     * @throws URISyntaxException
     */
    @Test
    public void linkUnLinkProfileToAccount() throws ClientProtocolException, IOException, URISyntaxException{
		/*
		 * link a profile to an account
		 */
    	HttpResponse response = RestcommProfilesTool.getInstance().linkProfileWithOverride(deploymentUrl.toString(), SUPER_ADMIN_ACCOUNT_SID, AUTH_TOKEN, SECONDARY_PROFILE_SID, SUPER_ADMIN_ACCOUNT_SID, RestcommProfilesTool.AssociatedResourceType.ACCOUNT);
    	logger.info("HttpResponse: "+response);
    	assertEquals(200, response.getStatusLine().getStatusCode());

    	/*
    	 * Get associated profile
    	 * from Accounts endpoint:
    	 * to verify association establishment.
    	 */
    	ClientResponse accountEndopintResponse = RestcommAccountsTool.getInstance().getAccountResponse(deploymentUrl.toString(), SUPER_ADMIN_ACCOUNT_SID, AUTH_TOKEN, SUPER_ADMIN_ACCOUNT_SID);
    	WebResourceLinkHeaders linkHeaders = accountEndopintResponse.getLinks();
    	logger.info("accountEndopintResponse WebResourceLinkHeaders: "+linkHeaders);
    	assertNotNull(linkHeaders);
    	LinkHeader linkHeader = linkHeaders.getLink(RestcommProfilesTool.PROFILE_REL_TYPE);
    	logger.info("accountEndopintResponse WebResourceLinkHeaders linkHeader: "+linkHeader);
    	assertNotNull(linkHeader);
    	assertTrue(linkHeader.getUri().toString().contains(SECONDARY_PROFILE_SID));

    	/*
		 * unlink a profile from an account
		 */
    	response = RestcommProfilesTool.getInstance().unLinkProfileWithOverride(deploymentUrl.toString(), SUPER_ADMIN_ACCOUNT_SID, AUTH_TOKEN, SECONDARY_PROFILE_SID, SUPER_ADMIN_ACCOUNT_SID, RestcommProfilesTool.AssociatedResourceType.ACCOUNT);
    	logger.info("HttpResponse: "+response);
    	assertEquals(200, response.getStatusLine().getStatusCode());

    	/*
    	 * Get associated profile
    	 * from Accounts endpoint:
    	 * to verify association removal
    	 */
    	accountEndopintResponse = RestcommAccountsTool.getInstance().getAccountResponse(deploymentUrl.toString(), SUPER_ADMIN_ACCOUNT_SID, AUTH_TOKEN, SUPER_ADMIN_ACCOUNT_SID);
    	linkHeaders = accountEndopintResponse.getLinks();
    	logger.info("accountEndopintResponse WebResourceLinkHeaders: "+linkHeaders);
    	linkHeader = linkHeaders.getLink(RestcommProfilesTool.PROFILE_REL_TYPE);
    	logger.info("accountEndopintResponse WebResourceLinkHeaders linkHeader: "+linkHeader);
    	assertNotNull(linkHeader);
    	assertTrue(linkHeader.getUri().toString().contains(DEFAULT_PROFILE_SID));
    }

    /**
     * link/unlink a give Profile To an Organization
     * @throws IOException
     * @throws ClientProtocolException
     * @throws URISyntaxException
     */
    @Test
    public void linkUnLinkProfileToOrganization() throws ClientProtocolException, IOException, URISyntaxException{
    	/*
		 * link a profile to an organizations
		 */
    	HttpResponse response = RestcommProfilesTool.getInstance().linkProfile(deploymentUrl.toString(), SUPER_ADMIN_ACCOUNT_SID, AUTH_TOKEN, SECONDARY_PROFILE_SID, ORGANIZATION_SID, RestcommProfilesTool.AssociatedResourceType.ORGANIZATION);
    	logger.info("HttpResponse: "+response);
    	assertEquals(200, response.getStatusLine().getStatusCode());

    	/*
    	 * Get associated profile
    	 * from Organizations endpoint:
    	 * to verify association establishment.
    	 */
    	ClientResponse orgEndopintResponse = RestcommOrganizationsTool.getInstance().getOrganizationResponse(deploymentUrl.toString(), SUPER_ADMIN_ACCOUNT_SID, AUTH_TOKEN, ORGANIZATION_SID);
    	WebResourceLinkHeaders linkHeaders = orgEndopintResponse.getLinks();
    	logger.info("accountEndopintResponse WebResourceLinkHeaders: "+linkHeaders);
    	assertNotNull(linkHeaders);
    	LinkHeader linkHeader = linkHeaders.getLink(RestcommProfilesTool.PROFILE_REL_TYPE);
    	logger.info("accountEndopintResponse WebResourceLinkHeaders linkHeader: "+linkHeader);
    	assertNotNull(linkHeader);
    	assertTrue(linkHeader.getUri().toString().contains(SECONDARY_PROFILE_SID));

    	/*
		 * unlink a profile from an organization
		 */
    	response = RestcommProfilesTool.getInstance().unLinkProfile(deploymentUrl.toString(), SUPER_ADMIN_ACCOUNT_SID, AUTH_TOKEN, SECONDARY_PROFILE_SID, ORGANIZATION_SID, RestcommProfilesTool.AssociatedResourceType.ORGANIZATION);
    	logger.info("HttpResponse: "+response);
    	assertEquals(200, response.getStatusLine().getStatusCode());

    	/*
    	 * Get associated profile
    	 * from Organizations endpoint:
    	 * to verify association removal.
    	 */
    	orgEndopintResponse = RestcommOrganizationsTool.getInstance().getOrganizationResponse(deploymentUrl.toString(), SUPER_ADMIN_ACCOUNT_SID, AUTH_TOKEN, ORGANIZATION_SID);
    	linkHeaders = orgEndopintResponse.getLinks();
    	logger.info("orgEndopintResponse WebResourceLinkHeaders: "+linkHeaders);
    	linkHeader = linkHeaders.getLink(RestcommProfilesTool.PROFILE_REL_TYPE);
    	logger.info("orgEndopintResponse WebResourceLinkHeaders linkHeader: "+linkHeader);
    	assertNotNull(linkHeader);
    	assertTrue(linkHeader.getUri().toString().contains(DEFAULT_PROFILE_SID));
    }


    /**
     * @throws ClientProtocolException
     * @throws IOException
     * @throws URISyntaxException
     */
    @Test
    @Category(FeatureExpTests.class)
    public void linkUnLinkProfilePermissionTest() throws ClientProtocolException, IOException, URISyntaxException{
    	/*
		 * link a profile by admin account
		 */
    	HttpResponse response = RestcommProfilesTool.getInstance().linkProfile(deploymentUrl.toString(), ADMIN_ACCOUNT_SID, AUTH_TOKEN, DEFAULT_PROFILE_SID, ORGANIZATION_SID, RestcommProfilesTool.AssociatedResourceType.ORGANIZATION);
    	assertEquals(403, response.getStatusLine().getStatusCode());
    	/*
		 * unlink a profile by dev account
		 */
    	response = RestcommProfilesTool.getInstance().linkProfile(deploymentUrl.toString(), DEVELOPER_ACCOUNT_SID, AUTH_TOKEN, DEFAULT_PROFILE_SID, ORGANIZATION_SID, RestcommProfilesTool.AssociatedResourceType.ORGANIZATION);
    	assertEquals(403, response.getStatusLine().getStatusCode());
    }

    /**
     * @throws ClientProtocolException
     * @throws IOException
     * @throws URISyntaxException
     */
    @Test
    @Category(FeatureExpTests.class)
    public void linkUnLinkProfileUnknownProfileSidTest() throws ClientProtocolException, IOException, URISyntaxException{
    	/*
		 * link a profile with unknown profile sid
		 */
    	HttpResponse response = RestcommProfilesTool.getInstance().linkProfile(deploymentUrl.toString(), SUPER_ADMIN_ACCOUNT_SID, AUTH_TOKEN, UNKNOWN_PROFILE_SID, ORGANIZATION_SID, RestcommProfilesTool.AssociatedResourceType.ORGANIZATION);
    	assertEquals(404, response.getStatusLine().getStatusCode());
    	/*
		 * unlink a profile with unknown profile sid
		 */
    	response = RestcommProfilesTool.getInstance().linkProfile(deploymentUrl.toString(), SUPER_ADMIN_ACCOUNT_SID, AUTH_TOKEN, UNKNOWN_PROFILE_SID, ORGANIZATION_SID, RestcommProfilesTool.AssociatedResourceType.ORGANIZATION);
    	assertEquals(404, response.getStatusLine().getStatusCode());
    }

    /**
     * @throws ClientProtocolException
     * @throws IOException
     * @throws URISyntaxException
     */
    @Test
    @Category(FeatureExpTests.class)
    public void linkUnLinkProfileUnknownAccountSidTest() throws ClientProtocolException, IOException, URISyntaxException{
    	/*
		 * link a profile with unknown account sid
		 */
    	HttpResponse response = RestcommProfilesTool.getInstance().linkProfile(deploymentUrl.toString(), SUPER_ADMIN_ACCOUNT_SID, AUTH_TOKEN, DEFAULT_PROFILE_SID, UNKNOWN_ACCOUNT_SID, RestcommProfilesTool.AssociatedResourceType.ACCOUNT);
    	assertEquals(404, response.getStatusLine().getStatusCode());
    	/*
		 * unlink a profile with unknown account sid
		 */
    	response = RestcommProfilesTool.getInstance().linkProfile(deploymentUrl.toString(), SUPER_ADMIN_ACCOUNT_SID, AUTH_TOKEN, DEFAULT_PROFILE_SID, UNKNOWN_ACCOUNT_SID, RestcommProfilesTool.AssociatedResourceType.ACCOUNT);
    	assertEquals(404, response.getStatusLine().getStatusCode());
    }

    /**
     * @throws ClientProtocolException
     * @throws IOException
     * @throws URISyntaxException
     */
    @Test
    @Category(FeatureExpTests.class)
    public void linkUnLinkProfileUnknownOrganizationSidTest() throws ClientProtocolException, IOException, URISyntaxException{
    	/*
		 * link a profile with unknown organization sid
		 */
    	HttpResponse response = RestcommProfilesTool.getInstance().linkProfile(deploymentUrl.toString(), SUPER_ADMIN_ACCOUNT_SID, AUTH_TOKEN, DEFAULT_PROFILE_SID, UNKNOWN_ORGANIZATION_SID, RestcommProfilesTool.AssociatedResourceType.ORGANIZATION);
    	assertEquals(404, response.getStatusLine().getStatusCode());
    	/*
		 * unlink a profile with unknown organization sid
		 */
    	response = RestcommProfilesTool.getInstance().linkProfile(deploymentUrl.toString(), SUPER_ADMIN_ACCOUNT_SID, AUTH_TOKEN, DEFAULT_PROFILE_SID, UNKNOWN_ORGANIZATION_SID, RestcommProfilesTool.AssociatedResourceType.ORGANIZATION);
    	assertEquals(404, response.getStatusLine().getStatusCode());
    }

    /**
     * test removal of association onnce we delete a profile
     * @throws URISyntaxException
     * @throws IOException
     * @throws ClientProtocolException
     */
    @Test
    @Category(FeatureAltTests.class)
    public void testRemovalOfAssociationOnDeleteProfile() throws ClientProtocolException, IOException, URISyntaxException{
    	/*
		 * create a profile
		 */
    	ClientResponse clientResponse = RestcommProfilesTool.getInstance().createProfileResponse(deploymentUrl.toString(), SUPER_ADMIN_ACCOUNT_SID, AUTH_TOKEN, PROFILE_DOCUMENT);
    	assertEquals(201, clientResponse.getStatus());
    	assertEquals(PROFILE_DOCUMENT, clientResponse.getEntity(String.class));

    	//extract profileSid of newly created profile Sid.
    	URI location = clientResponse.getLocation();
    	assertNotNull(location);
    	String profileLocation  = location.toString();
    	String[] profileUriElements = profileLocation.split("/");
    	assertNotNull(profileUriElements);
    	String newlyCreatedProfileSid = profileUriElements[profileUriElements.length-1];

    	/*
		 * link this profile to an account
		 */
    	HttpResponse response = RestcommProfilesTool.getInstance().linkProfile(deploymentUrl.toString(), SUPER_ADMIN_ACCOUNT_SID, AUTH_TOKEN, newlyCreatedProfileSid, ADMIN_ACCOUNT_SID, RestcommProfilesTool.AssociatedResourceType.ACCOUNT);
    	assertEquals(200, response.getStatusLine().getStatusCode());


    	/*
		 * delete the profile
		 */
    	clientResponse = RestcommProfilesTool.getInstance().deleteProfileResponse(deploymentUrl.toString(), SUPER_ADMIN_ACCOUNT_SID, AUTH_TOKEN, newlyCreatedProfileSid);
    	assertEquals(200, clientResponse.getStatus());

    	/*
    	 * Get associated profile
    	 * from Accounts endpoint:
    	 * to verify association was removed.
    	 */
    	ClientResponse accountEndopintResponse = RestcommAccountsTool.getInstance().getAccountResponse(deploymentUrl.toString(), SUPER_ADMIN_ACCOUNT_SID, AUTH_TOKEN, ADMIN_ACCOUNT_SID);
    	WebResourceLinkHeaders linkHeaders = accountEndopintResponse.getLinks();
    	LinkHeader linkHeader = linkHeaders.getLink(RestcommProfilesTool.PROFILE_REL_TYPE);
    	logger.info("linkHeader after deleteing profile: "+linkHeader);
    	assertNotNull(linkHeader);
    	assertTrue(linkHeader.getUri().toString().contains(DEFAULT_PROFILE_SID));
    }

    @Test
    public void getProfileSchemaTest() throws Exception {
        ClientResponse clientResponse = RestcommProfilesTool.getInstance().getProfileSchema(deploymentUrl.toString(), SUPER_ADMIN_ACCOUNT_SID, AUTH_TOKEN);
    	assertEquals(200, clientResponse.getStatus());
    	String str = clientResponse.getEntity(String.class);
    	assertNotNull(str);
    	JsonObject jsonResponse = new JsonParser().parse(str).getAsJsonObject();
    	assertNotNull(jsonResponse);
    }

    /**
	 * link a Profile To an Account which is already linked to a different profile
	 * @throws IOException
	 * @throws ClientProtocolException
	 * @throws URISyntaxException
	 */
	@Test
	@Category(FeatureAltTests.class)
	public void linkAccountToNewProfile() throws ClientProtocolException, IOException, URISyntaxException{
		/**
		 * link default profile to dev account
		 */
		HttpResponse response = RestcommProfilesTool.getInstance().linkProfile(deploymentUrl.toString(), SUPER_ADMIN_ACCOUNT_SID, AUTH_TOKEN, DEFAULT_PROFILE_SID, DEVELOPER_ACCOUNT_SID, RestcommProfilesTool.AssociatedResourceType.ACCOUNT);
		logger.info("HttpResponse: "+response);
		assertEquals(200, response.getStatusLine().getStatusCode());

		/**
		 * Get associated profile
		 * from Accounts endpoint:
		 * to verify association establishment.
		 */
		ClientResponse accountEndopintResponse = RestcommAccountsTool.getInstance().getAccountResponse(deploymentUrl.toString(), SUPER_ADMIN_ACCOUNT_SID, AUTH_TOKEN, DEVELOPER_ACCOUNT_SID);
		WebResourceLinkHeaders linkHeaders = accountEndopintResponse.getLinks();
		logger.info("accountEndopintResponse WebResourceLinkHeaders: "+linkHeaders);
		assertNotNull(linkHeaders);
		LinkHeader linkHeader = linkHeaders.getLink(RestcommProfilesTool.PROFILE_REL_TYPE);
		logger.info("accountEndopintResponse WebResourceLinkHeaders linkHeader: "+linkHeader);
		assertNotNull(linkHeader);
		assertTrue(linkHeader.getUri().toString().contains(DEFAULT_PROFILE_SID));

		/**
		 * Create a new profile
		 */
		ClientResponse clientResponse = RestcommProfilesTool.getInstance().createProfileResponse(deploymentUrl.toString(), SUPER_ADMIN_ACCOUNT_SID, AUTH_TOKEN, PROFILE_DOCUMENT);
    	assertEquals(201, clientResponse.getStatus());
    	assertEquals(PROFILE_DOCUMENT, clientResponse.getEntity(String.class));

    	//extract profileSid of newly created profile Sid.
    	URI location = clientResponse.getLocation();
    	assertNotNull(location);
    	String profileLocation  = location.toString();
    	String[] profileUriElements = profileLocation.split("/");
    	assertNotNull(profileUriElements);
    	String newlyCreatedProfileSid = profileUriElements[profileUriElements.length-1];

    	/**
		 * link newlyCreated profile to dev account.
		 */
    	response = RestcommProfilesTool.getInstance().linkProfile(deploymentUrl.toString(), SUPER_ADMIN_ACCOUNT_SID, AUTH_TOKEN, newlyCreatedProfileSid, DEVELOPER_ACCOUNT_SID, RestcommProfilesTool.AssociatedResourceType.ACCOUNT);
		logger.info("HttpResponse: "+response);
		assertEquals(200, response.getStatusLine().getStatusCode());

		/**
		 * Get associated profile
		 * from Accounts endpoint:
		 * to verify new association is establishment.
		 */
		accountEndopintResponse = RestcommAccountsTool.getInstance().getAccountResponse(deploymentUrl.toString(), SUPER_ADMIN_ACCOUNT_SID, AUTH_TOKEN, DEVELOPER_ACCOUNT_SID);
		linkHeaders = accountEndopintResponse.getLinks();
		logger.info("accountEndopintResponse WebResourceLinkHeaders: "+linkHeaders);
		assertNotNull(linkHeaders);
		linkHeader = linkHeaders.getLink(RestcommProfilesTool.PROFILE_REL_TYPE);
		logger.info("accountEndopintResponse WebResourceLinkHeaders linkHeader: "+linkHeader);
		assertNotNull(linkHeader);
		assertTrue(linkHeader.getUri().toString().contains(newlyCreatedProfileSid));
	}

    /**
	 * link a Profile To an Account which is already linked to the same profile.
	 * @throws IOException
	 * @throws ClientProtocolException
	 * @throws URISyntaxException
	 */
	@Test
	@Category(FeatureAltTests.class)
	public void linkAccountAgainToSameProfile() throws ClientProtocolException, IOException, URISyntaxException{
		/**
		 * link default profile to admin account
		 */
		HttpResponse response = RestcommProfilesTool.getInstance().linkProfile(deploymentUrl.toString(), SUPER_ADMIN_ACCOUNT_SID, AUTH_TOKEN, DEFAULT_PROFILE_SID, ADMIN_ACCOUNT_SID, RestcommProfilesTool.AssociatedResourceType.ACCOUNT);
		logger.info("HttpResponse: "+response);
		assertEquals(200, response.getStatusLine().getStatusCode());

		/**
		 * Get associated profile
		 * from Accounts endpoint:
		 * to verify association establishment.
		 */
		ClientResponse accountEndopintResponse = RestcommAccountsTool.getInstance().getAccountResponse(deploymentUrl.toString(), SUPER_ADMIN_ACCOUNT_SID, AUTH_TOKEN, ADMIN_ACCOUNT_SID);
		WebResourceLinkHeaders linkHeaders = accountEndopintResponse.getLinks();
		logger.info("accountEndopintResponse WebResourceLinkHeaders: "+linkHeaders);
		assertNotNull(linkHeaders);
		LinkHeader linkHeader = linkHeaders.getLink(RestcommProfilesTool.PROFILE_REL_TYPE);
		logger.info("accountEndopintResponse WebResourceLinkHeaders linkHeader: "+linkHeader);
		assertNotNull(linkHeader);
		assertTrue(linkHeader.getUri().toString().contains(DEFAULT_PROFILE_SID));

    	/**
		 * link default profile to admin account again.
		 */
    	response = RestcommProfilesTool.getInstance().linkProfile(deploymentUrl.toString(), SUPER_ADMIN_ACCOUNT_SID, AUTH_TOKEN, DEFAULT_PROFILE_SID, ADMIN_ACCOUNT_SID, RestcommProfilesTool.AssociatedResourceType.ACCOUNT);
		logger.info("HttpResponse: "+response);
		assertEquals(200, response.getStatusLine().getStatusCode());

		/**
		 * Get associated profile
		 * from Accounts endpoint:
		 * to verify association is still established.
		 */
		accountEndopintResponse = RestcommAccountsTool.getInstance().getAccountResponse(deploymentUrl.toString(), SUPER_ADMIN_ACCOUNT_SID, AUTH_TOKEN, ADMIN_ACCOUNT_SID);
		linkHeaders = accountEndopintResponse.getLinks();
		logger.info("accountEndopintResponse WebResourceLinkHeaders: "+linkHeaders);
		assertNotNull(linkHeaders);
		linkHeader = linkHeaders.getLink(RestcommProfilesTool.PROFILE_REL_TYPE);
		logger.info("accountEndopintResponse WebResourceLinkHeaders linkHeader: "+linkHeader);
		assertNotNull(linkHeader);
		assertTrue(linkHeader.getUri().toString().contains(DEFAULT_PROFILE_SID));
	}

	@Deployment(name = "ProfilesEndpointTest", managed = true, testable = false)
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
