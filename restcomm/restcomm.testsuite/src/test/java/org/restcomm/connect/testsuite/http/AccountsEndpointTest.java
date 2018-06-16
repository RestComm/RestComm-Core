package org.restcomm.connect.testsuite.http;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.net.URL;

import javax.sip.address.SipURI;
import javax.ws.rs.core.MultivaluedMap;

import org.apache.log4j.Logger;
import org.apache.shiro.crypto.hash.Md5Hash;
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
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.FixMethodOrder;
import org.junit.runners.MethodSorters;
import org.junit.runner.RunWith;
import org.restcomm.connect.commons.Version;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.UniformInterfaceException;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.core.util.MultivaluedMapImpl;
import org.junit.FixMethodOrder;
import org.junit.experimental.categories.Category;
import org.junit.runners.MethodSorters;
import org.restcomm.connect.commons.annotations.FeatureAltTests;
import org.restcomm.connect.commons.annotations.FeatureExpTests;
import org.restcomm.connect.commons.annotations.UnstableTests;

/**
 * @author <a href="mailto:gvagenas@gmail.com">gvagenas</a>
 * @author <a href="mailto:jean.deruelle@telestax.com">Jean Deruelle</a>
 * @author <a href="mailto:lyhungthinh@gmail.com">Thinh Ly</a>
 */

@RunWith(Arquillian.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class AccountsEndpointTest extends EndpointTest {
    private final static Logger logger = Logger.getLogger(AccountsEndpointTest.class.getName());

    private static final String version = Version.getVersion();

    @ArquillianResource
    private Deployer deployer;
    @ArquillianResource
    URL deploymentUrl;

    private String adminUsername = "administrator@company.com";
    private String adminFriendlyName = "Default Administrator Account";
    private String adminAccountSid = "ACae6e420f425248d6a26948c17a9e2acf";
    private String adminAuthToken = "77f8c12cc7b8f8423e5c38b035249166";
    private String adminPassword = "RestComm";

    private String childUsername = "child@company.com";
    private String childSid = "AC574d775522c96f9aacacc5ca60c8c74f";
    private String childAuthToken ="77f8c12cc7b8f8423e5c38b035249166";

    private String createdUsernanme = "created@company.com";
    private String createdUsernanme2 = "created2@company.com";
    private String createdUsernanme3 = "created3@company.com";
    private String createdAccountSid = "AC5ee3b351401804c2d064a33f762146fb";
    private String createdPassword = "RestComm12";
    private String createdAuthToken = "28f96b0fea1f9e33646f42026abdf305";

    private String updatedUsername = "updated@company.com";
    private String updatedAccountSid = "AC6b53c6ffa9fa7c4682dbcf4dec73012f";

    private String userEmailAddress = "gvagenas@restcomm.org";
    private String userPassword = "1234";

    private String unprivilegedSid = "AC00000000000000000000000000000000";
    private String unprivilegedUsername = "unprivileged@company.com";
    private String unprivilegedAuthToken = "77f8c12cc7b8f8423e5c38b035249166";

    private String guestSid = "AC11111111111111111111111111111111";
    private String guestUsername = "guest@company.com";
    private String guestAuthToken = "77f8c12cc7b8f8423e5c38b035249166";

    private String removedSid = "AC22222222222222222222222222222222";
    private String removedUsername = "removed@company.com";
    private String removedAuthToken = "77f8c12cc7b8f8423e5c38b035249166";

    private String updatedRoleUsername = "updatedRole@company.com";
    private String updatedRoleAccountSid = "AC33333333333333333333333333333333";
    private String updateRoleAuthToken = "77f8c12cc7b8f8423e5c38b035249166";
    private String nonSubAccountSid = "AC44444444444444444444444444444444";

    private String commonAuthToken = "77f8c12cc7b8f8423e5c38b035249166";

    private String organizationSid1 = "ORafbe225ad37541eba518a74248f0ac4c";
    private String organizationSid2 = "ORafbe225ad37541eba518a74248f0ac4d";
    private String organizationSid3 = "ORafbe225ad37541eba518a74248f0ac4e";

    private String organization1DomainName = "127.0.0.1";
    private String organization2DomainName = "org1.restcomm.com";
    private String organization3DomainName = "org2.restcomm.com";


    static SipStackTool tool1;

    SipStack thinhSipStack;
    SipPhone thinhPhone;
    //String thinhContact = "sip:lyhungthinh@127.0.0.1:5090";

    @BeforeClass
    public static void beforeClass() {
        tool1 = new SipStackTool("AccountsEndpointTest");
    }

    public void startSipStack(String thinhContact) throws Exception {
        thinhSipStack = tool1.initializeSipStack(SipStack.PROTOCOL_UDP, "127.0.0.1", "5090", "127.0.0.1:5080");
        thinhPhone = thinhSipStack.createSipPhone("127.0.0.1", SipStack.PROTOCOL_UDP, 5080, thinhContact);
    }

    @After
    public void stopSipStack() throws InterruptedException {
        if (thinhPhone != null) {
            thinhPhone.dispose();
        }
        if (thinhSipStack != null && thinhSipStack.getSipProvider().getListeningPoints().length>0) {
            thinhSipStack.dispose();
        }
        Thread.sleep(1000);
    }

    @Test
    public void testGetAccount() {
        // Get Account using admin email address and user email address
        JsonObject adminAccount = RestcommAccountsTool.getInstance().getAccount(deploymentUrl.toString(), adminUsername,
                adminAuthToken, adminUsername);
        assertTrue(adminAccount.get("sid").getAsString().equals(adminAccountSid));
    }

    @Test
    @Category(FeatureExpTests.class)
    public void testGetAccountAccess(){
        // check non-existent user receives a 401
        ClientResponse response = RestcommAccountsTool.getInstance().getAccountResponse(deploymentUrl.toString(), "nonexisting@company.com", "badpassword", adminAccountSid);
        assertEquals("Non-existing user should get a 401", 401, response.getStatus());
        // check InsufficientPerimssion errors- 403. Try to get administrator account with unprivileged accoutn creds
        response = RestcommAccountsTool.getInstance().getAccountResponse(deploymentUrl.toString(), unprivilegedUsername, unprivilegedAuthToken, adminAccountSid);
        assertEquals("Unpriveleged access to account did not return 403", 403, response.getStatus());
    }

    @Test
    @Category(FeatureExpTests.class)
    public void testGetAccountByFriendlyName() {
        // Try to get Account using admin friendly name and user email address
        int code = 0;
        try {
            JsonObject adminAccount = RestcommAccountsTool.getInstance().getAccount(deploymentUrl.toString(),
                    adminFriendlyName, adminAuthToken, adminUsername);
        } catch (UniformInterfaceException e) {
            code = e.getResponse().getStatus();
        }
        // Logins using friendly name are not allowed anymore
        assertTrue(code == 401);
    }

    @Test
    public void testCreateAccount() {
        JsonObject createAccountResponse = RestcommAccountsTool.getInstance().createAccount(deploymentUrl.toString(),
                adminUsername, adminAuthToken, createdUsernanme, createdPassword);
        JsonObject getAccountResponse = RestcommAccountsTool.getInstance().getAccount(deploymentUrl.toString(), adminUsername,
                adminAuthToken, createdUsernanme);
        assertTrue(getAccountResponse.get("sid").getAsString().equals(createdAccountSid));
        assertEquals(createdAuthToken, getAccountResponse.get("auth_token").getAsString());
        logger.info("createAccountResponse: "+createAccountResponse);
        assertTrue(createAccountResponse.get("sid").getAsString().equals(createdAccountSid));
        assertEquals(createdAuthToken, createAccountResponse.get("auth_token").getAsString());
    }

    @Test @Category(FeatureExpTests.class)
    public void testCreateAccountWithInvalidChars() {

        String username1 = "my?account@company.com";
        String username2 = "my@account@company.com";
        String username3 = "my=account@company.com";
        String password = "RestCom1233@";

        JsonObject createAccountResponse = RestcommAccountsTool.getInstance().createAccount(deploymentUrl.toString(),
                adminUsername, adminAuthToken, username1, password);
        assertNull(createAccountResponse);

        createAccountResponse = RestcommAccountsTool.getInstance().createAccount(deploymentUrl.toString(),
                adminUsername, adminAuthToken, username2, password);
        assertNull(createAccountResponse);

        createAccountResponse = RestcommAccountsTool.getInstance().createAccount(deploymentUrl.toString(),
                adminUsername, adminAuthToken, username3, password);
        assertNull(createAccountResponse);
    }

    @Test
    @Category(UnstableTests.class)
    public void testCreateAccountWithJapaneseChars() {
        String friendlyName = "NTTアドバンステクノロジ";
        String userName = "japaneseChars@company.com";
        JsonObject createAccountResponse = RestcommAccountsTool.getInstance().createAccount(deploymentUrl.toString(),
                adminUsername, adminAuthToken, userName, createdPassword, friendlyName);
        String createdsid = createAccountResponse.get("sid").getAsString();
        JsonObject getAccountResponse = RestcommAccountsTool.getInstance().getAccount(deploymentUrl.toString(), adminUsername,
                adminAuthToken, createdsid);
        assertEquals(getAccountResponse.get("auth_token").getAsString(), createAccountResponse.get("auth_token").getAsString());
        assertEquals(getAccountResponse.get("friendly_name").getAsString(), createAccountResponse.get("friendly_name").getAsString());
    }

    @Test
    public void testUpdateAccount() {
        //update friendlyname and role, no password, no status update!!
        JsonObject updateAccountResponse = RestcommAccountsTool.getInstance().updateAccount(deploymentUrl.toString(),
                adminUsername, adminAuthToken, updatedAccountSid, "updated2", null, null, "Developer", null );
        JsonObject getAccountResponse = RestcommAccountsTool.getInstance().getAccount(deploymentUrl.toString(),
                adminUsername, adminAuthToken, updatedUsername);

        assertEquals("FriendlyName field is not updated",  "updated2", updateAccountResponse.get("friendly_name").getAsString());
        assertEquals("Status field should remain uninitialized", "uninitialized", updateAccountResponse.get("status").getAsString());
        assertEquals("Role field is not updated", "Developer", updateAccountResponse.get("role").getAsString());

        assertEquals("FriendlyName field is not updated",  "updated2", getAccountResponse.get("friendly_name").getAsString());
        assertEquals("Status field should remain uninitialized", "uninitialized", getAccountResponse.get("status").getAsString());
        assertEquals("Role field is not updated", "Developer", updateAccountResponse.get("role").getAsString());

        // role update test revert it back to Administrator
        //update friendlyname, role, and authtoken, no status update!!
        updateAccountResponse = RestcommAccountsTool.getInstance().updateAccount(deploymentUrl.toString(),
                adminUsername, adminAuthToken, updatedAccountSid, "updated3", "Restcomm2", "Restcomm2", "Administrator", null );
         getAccountResponse = RestcommAccountsTool.getInstance().getAccount(deploymentUrl.toString(),
                adminUsername, adminAuthToken, updatedUsername);

         assertEquals("FriendlyName field is not updated",  "updated3", updateAccountResponse.get("friendly_name").getAsString());
         assertEquals("AuthToken field is not updated", new Md5Hash("Restcomm2").toString(), updateAccountResponse.get("auth_token").getAsString());
         assertEquals("Changing password must activate account", "active", updateAccountResponse.get("status").getAsString());
         assertEquals("Role field is not updated", "Administrator", updateAccountResponse.get("role").getAsString());

        assertEquals("FriendlyName field is not updated",  "updated3", getAccountResponse.get("friendly_name").getAsString());
        assertEquals("AuthToken field is not updated", new Md5Hash("Restcomm2").toString(), getAccountResponse.get("auth_token").getAsString());
        assertEquals("Changing password must activate account", "active",getAccountResponse.get("status").getAsString());
        assertEquals("Role field is not updated", "Administrator", getAccountResponse.get("role").getAsString());
        // role update test revert it back to Administrator
    }

    @Test
    public void testUpdateAuthTokenIgnored() {
        // try to update directly the AuthToken
        JsonObject updateAccountResponse = RestcommAccountsTool.getInstance().updateAccount(deploymentUrl.toString(),
                adminUsername, adminAuthToken, childSid, null, null, "RestComm12", null, null );
        // make sure the AuthToken has not changed
        assertEquals("77f8c12cc7b8f8423e5c38b035249166", updateAccountResponse.get("auth_token").getAsString());
    }

    @Test
    @Category(FeatureAltTests.class)
    public void testUpdateAccountStatusAllCapital() {
        JsonObject updateAccountResponse = RestcommAccountsTool.getInstance().updateAccount(deploymentUrl.toString(),
                adminUsername, adminAuthToken, updatedAccountSid, null, null, null, null, "ACTIVE" );
        JsonObject getAccountResponse = RestcommAccountsTool.getInstance().getAccount(deploymentUrl.toString(),
                adminUsername, adminAuthToken, updatedUsername);

        assertEquals("Status field is not updated", "active", updateAccountResponse.get("status").getAsString());
        assertEquals("Status field is not updated", "active", getAccountResponse.get("status").getAsString());

        // role update test revert it back to Administrator
        updateAccountResponse = RestcommAccountsTool.getInstance().updateAccount(deploymentUrl.toString(),
                adminUsername, adminAuthToken, updatedAccountSid, "updated3", "Restcomm2", null, "Administrator", null );
        getAccountResponse = RestcommAccountsTool.getInstance().getAccount(deploymentUrl.toString(),
                adminUsername, adminAuthToken, updatedUsername);

        assertEquals("FriendlyName field is not updated",  "updated3", updateAccountResponse.get("friendly_name").getAsString());
        assertEquals("AuthToken field is not updated", new Md5Hash("Restcomm2").toString(), updateAccountResponse.get("auth_token").getAsString());
        assertEquals("Role field is not updated", "Administrator", updateAccountResponse.get("role").getAsString());

        assertEquals("FriendlyName field is not updated",  "updated3", getAccountResponse.get("friendly_name").getAsString());
        assertEquals("AuthToken field is not updated", new Md5Hash("Restcomm2").toString(), getAccountResponse.get("auth_token").getAsString());
        assertEquals("Role field is not updated", "Administrator", getAccountResponse.get("role").getAsString());
    }

    @Test
    public void testUpdateAccountByEmail() {
        JsonObject updateAccountResponse = RestcommAccountsTool.getInstance().updateAccount(deploymentUrl.toString(),
                adminUsername, adminAuthToken, updatedUsername, "updated2", "Restcomm2", null, "Developer", null );
        JsonObject getAccountResponse = RestcommAccountsTool.getInstance().getAccount(deploymentUrl.toString(),
                adminUsername, adminAuthToken, updatedUsername);

        assertEquals("FriendlyName field is not updated",  "updated2", updateAccountResponse.get("friendly_name").getAsString());
        assertEquals("AuthToken field is not updated", new Md5Hash("Restcomm2").toString(), updateAccountResponse.get("auth_token").getAsString());
        assertEquals("Role field is not updated", "Developer", updateAccountResponse.get("role").getAsString());

        assertEquals("FriendlyName field is not updated",  "updated2", getAccountResponse.get("friendly_name").getAsString());
        assertEquals("AuthToken field is not updated", new Md5Hash("Restcomm2").toString(), getAccountResponse.get("auth_token").getAsString());
        assertEquals("Role field is not updated", "Developer", updateAccountResponse.get("role").getAsString());

        // role update test revert it back to Administrator
        updateAccountResponse = RestcommAccountsTool.getInstance().updateAccount(deploymentUrl.toString(),
                adminUsername, adminAuthToken, updatedAccountSid, "updated3", "Restcomm2", null, "Administrator", null );
        getAccountResponse = RestcommAccountsTool.getInstance().getAccount(deploymentUrl.toString(),
                adminUsername, adminAuthToken, updatedUsername);

        assertEquals("FriendlyName field is not updated",  "updated3", updateAccountResponse.get("friendly_name").getAsString());
        assertEquals("AuthToken field is not updated", new Md5Hash("Restcomm2").toString(), updateAccountResponse.get("auth_token").getAsString());
        assertEquals("Role field is not updated", "Administrator", updateAccountResponse.get("role").getAsString());

        assertEquals("FriendlyName field is not updated",  "updated3", getAccountResponse.get("friendly_name").getAsString());
        assertEquals("AuthToken field is not updated", new Md5Hash("Restcomm2").toString(), getAccountResponse.get("auth_token").getAsString());
        assertEquals("Role field is not updated", "Administrator", getAccountResponse.get("role").getAsString());
    }

    // special account-update policy when updating roles
    @Test
    @Category(FeatureExpTests.class)
    public void testUpdateAccountRoleAccessControl() {
        // non-admins should not be able to change their role
        ClientResponse response = RestcommAccountsTool.getInstance().updateAccountResponse(deploymentUrl.toString(),
                updatedRoleUsername, updateRoleAuthToken, updatedRoleAccountSid, null, null, null, "Administrator", null );
        assertEquals("Should return a 403 when non-admin tries to update role", 403, response.getStatus() );
        // admin, should be able to change their sub-account role
        response = RestcommAccountsTool.getInstance().updateAccountResponse(deploymentUrl.toString(),
                adminUsername, adminAuthToken, updatedRoleAccountSid, null, null, null, "Administrator", null );
        assertEquals("Should return a 200 when admin tries to update role of sub-account", 200, response.getStatus() );
        // admin, should not be able to change role of other account that is not their sub-account
        response = RestcommAccountsTool.getInstance().updateAccountResponse(deploymentUrl.toString(),
                adminUsername, adminAuthToken, nonSubAccountSid, null, null, null, "Administrator", null );
        assertEquals("Should get a 403 when admin tries to modify role of non-sub-accounts", 403, response.getStatus() );

    }

    @Test
    @Category(FeatureExpTests.class)
    public void testCreateAccountAccess(){
        // 'unprivilaged should not be able to create accounts and receive a 403
        ClientResponse response = RestcommAccountsTool.getInstance().createAccountResponse(deploymentUrl.toString(),
                unprivilegedUsername, unprivilegedAuthToken, "notcreated@company.com", "not-created-password");
        assertEquals("403 not returned", 403, response.getStatus());
    }

    @Test
    @Category(FeatureExpTests.class)
    public void testCreateAdministratorAccountFails() {
        JsonObject createAccountResponse = RestcommAccountsTool.getInstance().createAccount(deploymentUrl.toString(),
                adminUsername, adminAuthToken, "administrator@company.com", "RestComm12");
        assertNull(createAccountResponse);
    }

    @Test
    @Category(FeatureExpTests.class)
    public void testCreateAccountTwiceFails() {
        ClientResponse createResponse1 = RestcommAccountsTool.getInstance().createAccountResponse(deploymentUrl.toString(), adminUsername, adminAuthToken,
                "twice@company.com", "RestComm12");
        assertEquals("Account twice@company.com could not be created even once", 200, createResponse1.getStatus());
        ClientResponse createResponse2 = RestcommAccountsTool.getInstance().createAccountResponse(deploymentUrl.toString(), adminUsername, adminAuthToken,
                "twice@company.com", "RestComm12");
        assertEquals("Did not retrieve a conflict HTTP status (409) while creating accounts with same email address", 409, createResponse2.getStatus());
    }

    /**
     * When creating a new account and the client with the same name is already there
     * TODO [...]
     *
     * @throws Exception
     */
    @Test
    @Category(UnstableTests.class)
    public void testCreateAccountCheckClientExisted() throws Exception {
        try {
            String thinhContact = "sip:lyhungthinh@127.0.0.1:5090";
            startSipStack(thinhContact);

            String subAccountPassword = "mynewpassword12";
            String subAccountEmail = "lyhungthinh@gmail.com";

            JsonObject subAccountResponse = RestcommAccountsTool.getInstance().createAccount(deploymentUrl.toString(),
                    adminUsername, adminAuthToken, subAccountEmail, subAccountPassword);

            JsonObject clientOfAccount = CreateClientsTool.getInstance().getClientOfAccount(deploymentUrl.toString(),
                    subAccountResponse, adminUsername, adminPassword);
            assertNotNull(clientOfAccount);

            CreateClientsTool.getInstance().updateClientVoiceUrl(deploymentUrl.toString(), subAccountResponse,
                    clientOfAccount.get("sid").getAsString(), deploymentUrl.toString() + "/demos/welcome.xml",
                    adminUsername, adminPassword);

            JsonObject clientOfAccountUpdated = CreateClientsTool.getInstance().getClientOfAccount(deploymentUrl.toString(),
                    subAccountResponse, adminUsername, adminPassword);
            System.out.println(clientOfAccountUpdated);

            // Use the new client to register with Restcomm
            SipURI reqUri = thinhSipStack.getAddressFactory().createSipURI(null, "127.0.0.1:5080");

            assertTrue(thinhPhone.register(reqUri, "lyhungthinh", subAccountPassword, thinhContact, 1800, 1800));
            assertTrue(thinhPhone.unregister(thinhContact, 0));

            //RestcommAccountsTool.getInstance().removeAccount(deploymentUrl.toString(), adminUsername, adminAuthToken,
            //        subAccountResponse.get("sid").getAsString());
        } finally {
            stopSipStack();
        }
    }

    @Test
    @Category(FeatureExpTests.class)
    public void testCreateAccountFourthLevelFails() {
        ClientResponse response = RestcommAccountsTool.getInstance().createAccountResponse(deploymentUrl.toString(), "grandchild@company.com", commonAuthToken, "fourthlevelAccount@company.com", "RestComm12");
        assertEquals(400, response.getStatus());
        //Assert.assertTrue(response.getEntity(String.class).contains(""))
    }

    @Test
    @Category(UnstableTests.class)
    public void testUpdateAccountCheckClient() throws Exception {
        try {
            String thinhContact = "sip:lyhungthinh2@127.0.0.1:5090";
            startSipStack(thinhContact);

            String subAccountPassword = "mynewpassword12";
            String subAccountEmail = "lyhungthinh2@gmail.com";
            String subAccountNewPassword = "latestpassword12";
            String subAccountNewAuthToken = "81eb94645c485fc39eedc5d46522e5ba";
            JsonObject subAccountResponse;

            SipURI reqUri = thinhSipStack.getAddressFactory().createSipURI(null, "127.0.0.1:5080");

            subAccountResponse = RestcommAccountsTool.getInstance().createAccount(deploymentUrl.toString(), adminUsername,
                    adminAuthToken, subAccountEmail, subAccountPassword);
            assertNotNull(subAccountResponse);
            JsonObject clientOfAccount = CreateClientsTool.getInstance().getClientOfAccount(deploymentUrl.toString(),
                    subAccountResponse, adminUsername, adminPassword);
            assertNotNull(clientOfAccount);
            // Use the new client to register with Restcomm
            assertTrue(thinhPhone.register(reqUri, "lyhungthinh2", subAccountPassword, thinhContact, 1800, 1800));
            assertTrue(thinhPhone.unregister(thinhContact, 0));

            subAccountResponse = RestcommAccountsTool.getInstance().updateAccount(deploymentUrl.toString(), adminUsername,
                    adminAuthToken,subAccountResponse.get("sid").getAsString(), null, subAccountNewPassword, null, null, null);
            assertTrue(subAccountResponse.get("auth_token").getAsString().equals(subAccountNewAuthToken));
            assertTrue(thinhPhone.register(reqUri, "lyhungthinh2", subAccountNewPassword, thinhContact, 1800, 1800));
            assertTrue(thinhPhone.unregister(thinhContact, 0));

            clientOfAccount = CreateClientsTool.getInstance().getClientOfAccount(deploymentUrl.toString(),
                    subAccountResponse, adminUsername, adminPassword);
            assertTrue(clientOfAccount.get("password").getAsString().equals(subAccountNewPassword));

            //RestcommAccountsTool.getInstance().removeAccount(deploymentUrl.toString(), adminUsername, adminAuthToken,
            //        subAccountResponse.get("sid").getAsString());
        } finally {
            stopSipStack();
        }
    }

    @Test
    @Category(UnstableTests.class)
    public void testCloseAccountCheckClient() throws Exception {
        try {
            String thinhContact = "sip:lyhungthinh3@127.0.0.1:5090";
            startSipStack(thinhContact);

            String subAccountPassword = "mynewpassword12";
            String subAccountEmail = "lyhungthinh3@gmail.com";
            JsonObject subAccountResponse;

            SipURI reqUri = thinhSipStack.getAddressFactory().createSipURI(null, "127.0.0.1:5080");

            subAccountResponse = RestcommAccountsTool.getInstance().createAccount(deploymentUrl.toString(), adminUsername,
                    adminAuthToken, subAccountEmail, subAccountPassword);
            assertNotNull(subAccountResponse);
            JsonObject clientOfAccount = CreateClientsTool.getInstance().getClientOfAccount(deploymentUrl.toString(),
                    subAccountResponse, adminUsername, adminPassword);
            assertNotNull(clientOfAccount);
            assertTrue(thinhPhone.register(reqUri, "lyhungthinh3", subAccountPassword, thinhContact, 1800, 1800));
            assertTrue(thinhPhone.unregister(thinhContact, 0));
            // close the account
            Client jersey = getClient(adminUsername, adminPassword);
            WebResource resource = jersey.resource( getResourceUrl("/2012-04-24/Accounts.json/"+subAccountEmail) );
            MultivaluedMap<String,String> params = new MultivaluedMapImpl();
            params.add("Status","closed");
            ClientResponse response = resource.put(ClientResponse.class,params);
            assertEquals(200, response.getStatus());
            //RestcommAccountsTool.getInstance().removeAccount(deploymentUrl.toString(), adminUsername, adminAuthToken,
            //        subAccountResponse.get("sid").getAsString());
            // and make sure the client is gone and can't register
            JsonObject clientOfAccount2 = CreateClientsTool.getInstance().getClientOfAccount(deploymentUrl.toString(),
                    subAccountResponse, adminUsername, adminPassword);
            assertTrue(clientOfAccount2 == null);
            assertFalse(thinhPhone.register(reqUri, "lyhungthinh3", subAccountPassword, thinhContact, 1800, 1800));
        } finally {
            stopSipStack();
        }
    }

    @Test
    public void testCloseAccountNested() {
        String topLevelSid = "AC12300000000000000000000000000000";
        String removed1Sid = "AC12300000000000000000000000000001";
        String removed11Sid = "AC12300000000000000000000000000011";

        Client jersey = getClient("removed-top@company.com", commonAuthToken);

        WebResource resource = jersey.resource( getResourceUrl("/2012-04-24/Accounts.json/"+removed1Sid) );
        MultivaluedMap<String,String> params = new MultivaluedMapImpl();
        params.add("Status","closed");
        ClientResponse response = resource.put(ClientResponse.class,params);
        // the closed account should be available
        assertEquals(200, response.getStatus());
        JsonParser parser = new JsonParser();
        JsonObject jsonObject = parser.parse(response.getEntity(String.class)).getAsJsonObject();
        // make sure the account status is set to closed
        assertEquals("closed", jsonObject.get("status").getAsString());
        // assert removed accounts children are closed too
        resource = jersey.resource( getResourceUrl("/2012-04-24/Accounts.json/"+removed11Sid) );
        response = resource.get(ClientResponse.class);
        assertEquals(200, response.getStatus());
        parser = new JsonParser();
        jsonObject = parser.parse(response.getEntity(String.class)).getAsJsonObject();
        assertEquals("closed", jsonObject.get("status").getAsString());
        // assert the applications of the account are removed
        resource = jersey.resource( getResourceUrl("/2012-04-24/Accounts/"+removed1Sid+"/Applications/AP00000000000000000000000000000001.json" ) );
        assertEquals(404, resource.get(ClientResponse.class).getStatus());
        // assert IncomingPhoneNumbers of the account are removed
        resource = jersey.resource( getResourceUrl("/2012-04-24/Accounts/"+removed1Sid+"/IncomingPhoneNumbers/PN00000000000000000000000000000001.json" ) );
        assertEquals(404, resource.get(ClientResponse.class).getStatus());
        // assert notification are removed
        resource = jersey.resource( getResourceUrl("/2012-04-24/Accounts/"+removed1Sid+"/Notifications/NO00000000000000000000000000000001.json" ) );
        assertEquals(404, resource.get(ClientResponse.class).getStatus());
        // assert recordings are removed
        resource = jersey.resource( getResourceUrl("/2012-04-24/Accounts/"+removed1Sid+"/Recordings/RE00000000000000000000000000000001.json" ) );
        assertEquals(404, resource.get(ClientResponse.class).getStatus());
        // assert transcriptions are removed
        resource = jersey.resource( getResourceUrl("/2012-04-24/Accounts/"+removed1Sid+"/Transcriptions/TR00000000000000000000000000000001.json" ) );
        assertEquals(404, resource.get(ClientResponse.class).getStatus());
        // assert outgoing caller ids are removed
        resource = jersey.resource( getResourceUrl("/2012-04-24/Accounts/"+removed1Sid+"/OutgoingCallerIds/PN00000000000000000000000000000001.json" ) );
        assertEquals(404, resource.get(ClientResponse.class).getStatus());
        // assert clients are removed
        resource = jersey.resource( getResourceUrl("/2012-04-24/Accounts/"+removed1Sid+"/Clients/CL00000000000000000000000000000001.json" ) );
        assertEquals(404, resource.get(ClientResponse.class).getStatus());
        // we won't test Announcements removal . There is no retrieval method yet.
    }

    @Test
    public void testGetAccounts() throws InterruptedException {
        // Get Account using admin email address and user email address
        JsonObject account1 = RestcommAccountsTool.getInstance().getAccount(deploymentUrl.toString(), adminUsername,
                adminAuthToken, childUsername);
        // Get Account using admin account sid and user sid
        JsonObject account2 = RestcommAccountsTool.getInstance().getAccount(deploymentUrl.toString(), adminAccountSid,
                adminAuthToken, account1.get("sid").getAsString());

        assertTrue(account1.toString().equals(account2.toString()));
    }

    @Test
    @Category(FeatureExpTests.class)
    public void testGetAccountsAccess() {
        ClientResponse response = RestcommAccountsTool.getInstance().getAccountsResponse(deploymentUrl.toString(), guestUsername, guestAuthToken);
        assertEquals("Guest account should get get a 403 when retrieving accounts", 403, response.getStatus());
    }

    @Ignore // ignored since account removal is disables as of #1270
    @Test
    public void testRemoveAccountAccess(){
        ClientResponse response = RestcommAccountsTool.getInstance().removeAccountResponse(deploymentUrl.toString(), unprivilegedUsername, unprivilegedAuthToken, removedSid + ".json" );
        assertEquals("Unprivileged account should receive a 403 while removing an account", 403, response.getStatus());
        response = RestcommAccountsTool.getInstance().removeAccountResponse(deploymentUrl.toString(), adminUsername, adminAuthToken, removedSid + ".json");
        assertEquals("Administrator should receive a 200 OK when removing an account", 200, response.getStatus());
    }

    /**
     * Test access without administrator type of access. Instead a common role and permissions is used.
     */
    @Test
    public void testAccountAcccessUsingRoles() {
        ClientResponse response = RestcommAccountsTool.getInstance().getAccountResponse(deploymentUrl.toString(),unprivilegedUsername, unprivilegedAuthToken, unprivilegedSid);
        assertEquals(200,response.getStatus());
    }

    @Test
    public void testPasswordStrengthForAccountCreate() {
        ClientResponse response = RestcommAccountsTool.getInstance().createAccountResponse(deploymentUrl.toString(),
                adminUsername, adminAuthToken, "weak@company.com", "1234");
        assertEquals("Weak password account creation should fail with 400", 400, response.getStatus());
        Assert.assertTrue("Response error message should contain 'weak' term", response.getEntity(String.class).toLowerCase().contains("weak"));
        response = RestcommAccountsTool.getInstance().createAccountResponse(deploymentUrl.toString(),
                adminUsername, adminAuthToken, "weak@company.com", "1234asdf!@#");
        assertEquals(200, response.getStatus());
    }

    @Test
    public void testPasswordStrengthForAccountUpdate() {
        // updating an account with weak password should fail
        ClientResponse response = RestcommAccountsTool.getInstance().updateAccountResponse(deploymentUrl.toString(), adminUsername, adminAuthToken, "updated-weak@company.com", null, "1234", null, null, null );
        assertEquals(400, response.getStatus());
        Assert.assertTrue("Response should contain 'weak' term", response.getEntity(String.class).toLowerCase().contains("weak"));
        // updating an account with strong password should succeed
        response = RestcommAccountsTool.getInstance().updateAccountResponse(deploymentUrl.toString(), adminUsername, adminAuthToken, "updated-weak@company.com", null, "RestComm12", null, null, null );
        assertEquals(200, response.getStatus());
    }

    @Test
    @Category(FeatureExpTests.class)
    public void createAccountInSpecificOrganizationPermissionTest() {
    	// child should not be able to create account in specified org that it does not belong to
    	ClientResponse clientResponse = RestcommAccountsTool.getInstance().createAccountResponse(deploymentUrl.toString(),
                childUsername, childAuthToken, createdUsernanme, createdPassword, null, organizationSid2);
    	assertEquals(403, clientResponse.getStatus());

    }

    @Test
    public void createAccountInSpecificOrganizationRoleTest() {
    	// child should be able to create account in specified org that it belong to
    	ClientResponse clientResponse = RestcommAccountsTool.getInstance().createAccountResponse(deploymentUrl.toString(),
                childUsername, childAuthToken, createdUsernanme2, createdPassword, null, organizationSid1);
    	assertEquals(200, clientResponse.getStatus());

    	//super admin should be able to create account in specified org
    	clientResponse = RestcommAccountsTool.getInstance().createAccountResponse(deploymentUrl.toString(),
                adminUsername, adminAuthToken, createdUsernanme3, createdPassword, null, organizationSid2);
    	assertEquals(200, clientResponse.getStatus());

    }

    @Test
    @Category(FeatureExpTests.class)
    public void createAccountInSpecificOrganizationInvalidRequestTest() {

    	//super admin tries to create account with invalid organization Sid
    	ClientResponse clientResponse = RestcommAccountsTool.getInstance().createAccountResponse(deploymentUrl.toString(),
                adminUsername, adminAuthToken, createdUsernanme3, createdPassword, null, "blabla");
    	assertEquals(400, clientResponse.getStatus());

    	//super admin tries to create account with organization Sid that does not exists
    	clientResponse = RestcommAccountsTool.getInstance().createAccountResponse(deploymentUrl.toString(),
                adminUsername, adminAuthToken, createdUsernanme3, createdPassword, null, "ORafbe225ad37541eba518a74248f01234");
    	assertEquals(400, clientResponse.getStatus());
    }

    @Test
    @Category({UnstableTests.class, FeatureAltTests.class})
    //Using aaa prefix to enforce order, so this can be executed under the assumptions
    //of initial db script, rather than depending on execution order of diff
    //test cases
    public void aaatestGetAccountsOfASpecificOrganization() {
    	//getAccounts without any parameters
        ClientResponse response = RestcommAccountsTool.getInstance().getAccountsResponse(deploymentUrl.toString(), adminUsername, adminAuthToken);
        if(logger.isDebugEnabled())
        	logger.debug("getAccounts without filter Response: "+response);
        assertEquals(200, response.getStatus());

        //getAccounts with null Filter
        JsonArray accountsArray = RestcommAccountsTool.getInstance().getAccountsWithFilterResponse(deploymentUrl.toString(), adminUsername, adminAuthToken, null, null);
        if(logger.isDebugEnabled())
        	logger.debug("getAccounts With null Filter Response: "+accountsArray);
        assertEquals(8, accountsArray.size());

        //getAccounts with organizationSid Filter
        accountsArray = RestcommAccountsTool.getInstance().getAccountsWithFilterResponse(deploymentUrl.toString(), adminUsername, adminAuthToken, organizationSid1, null);
        if(logger.isDebugEnabled())
        	logger.debug("getAccounts With organizationSid Filter Response: "+accountsArray);
        assertEquals(24, accountsArray.size());

        //getAccounts with organizationSid Filter
        accountsArray = RestcommAccountsTool.getInstance().getAccountsWithFilterResponse(deploymentUrl.toString(), adminUsername, adminAuthToken, organizationSid2, null);
        if(logger.isDebugEnabled())
        	logger.debug("getAccounts With organizationSid Filter Response: "+accountsArray);
        //should be 1 account that belongs to this org
        assertEquals(1, accountsArray.size());

        //getAccounts with organizationSid Filter
        accountsArray = RestcommAccountsTool.getInstance().getAccountsWithFilterResponse(deploymentUrl.toString(), adminUsername, adminAuthToken, organizationSid3, null);
        if(logger.isDebugEnabled())
        	logger.debug("getAccounts With organizationSid Filter Response: "+accountsArray);
        //should be 0, as no account belongs to this org
        assertEquals(0, accountsArray.size());

        //getAccounts with domainName Filter
        accountsArray = RestcommAccountsTool.getInstance().getAccountsWithFilterResponse(deploymentUrl.toString(), adminUsername, adminAuthToken, null, organization2DomainName);
        if(logger.isDebugEnabled())
        	logger.debug("getAccounts With domainName Filter Response: "+accountsArray);
        //should be 1 account
        assertEquals(1, accountsArray.size());
    }

    @Test
    public void testUpdateAccountStatusSuspended() {
        final String sidMaster = "ACbdf00000000000000000000000000000";
        final String sidChild1 = "ACbdf00000000000000000000000000001";
        final String sidChild2 = "ACbdf00000000000000000000000000002";
        final String sidGrandchild1 = "ACbdf00000000000000000000000000011";
        final String sidGrandchild2 = "ACbdf00000000000000000000000000012";
        //final String sidGreatGrandchild1 = "ACbdf00000000000000000000000000111";
        //final String sidGreatGrandchild2 = "ACbdf00000000000000000000000000112";

        //change master account status to suspended
        JsonObject updateAccountResponse = RestcommAccountsTool.getInstance().updateAccount(deploymentUrl.toString(),
                adminUsername, adminAuthToken, sidMaster, null, null, null, null, "suspended" );

        //collect data from account tree to check status replication
        JsonObject getAccountResponse1 = RestcommAccountsTool.getInstance().getAccount(deploymentUrl.toString(),
                adminUsername, adminAuthToken, sidChild1);
        JsonObject getAccountResponse2 = RestcommAccountsTool.getInstance().getAccount(deploymentUrl.toString(),
                adminUsername, adminAuthToken, sidChild2);
        JsonObject getAccountResponse3 = RestcommAccountsTool.getInstance().getAccount(deploymentUrl.toString(),
                adminUsername, adminAuthToken, sidGrandchild1);
        JsonObject getAccountResponse4 = RestcommAccountsTool.getInstance().getAccount(deploymentUrl.toString(),
                adminUsername, adminAuthToken, sidGrandchild2);
        //JsonObject getAccountResponse5 = RestcommAccountsTool.getInstance().getAccount(deploymentUrl.toString(),
        //        adminUsername, adminAuthToken, sidGreatGrandchild1);
        //JsonObject getAccountResponse6 = RestcommAccountsTool.getInstance().getAccount(deploymentUrl.toString(),
        //        adminUsername, adminAuthToken, sidGreatGrandchild2);

        //check master account status
        assertEquals("Master account status is not updated",  "suspended", updateAccountResponse.get("status").getAsString());

        //check account tree status
        assertEquals("Child 1 account status is not updated",  "suspended", getAccountResponse1.get("status").getAsString());
        assertEquals("Child 2 account status is not updated",  "suspended", getAccountResponse2.get("status").getAsString());
        assertEquals("Grandchild 1 account status is not updated",  "suspended", getAccountResponse3.get("status").getAsString());
        assertEquals("Grandchild 2 account status is not updated",  "suspended", getAccountResponse4.get("status").getAsString());
        //assertEquals("Great-grandchild 1 account status is not updated",  "suspended", getAccountResponse5.get("status").getAsString());
        //assertEquals("Great-grandchild 2 account status is not updated",  "suspended", getAccountResponse6.get("status").getAsString());

        //revert master account status to active
        updateAccountResponse = RestcommAccountsTool.getInstance().updateAccount(deploymentUrl.toString(),
                adminUsername, adminAuthToken, sidMaster, null, null, null, null, "active" );

        //collect data from account tree to check status replication
        getAccountResponse1 = RestcommAccountsTool.getInstance().getAccount(deploymentUrl.toString(),
                adminUsername, adminAuthToken, sidChild1);
        getAccountResponse2 = RestcommAccountsTool.getInstance().getAccount(deploymentUrl.toString(),
                adminUsername, adminAuthToken, sidChild2);
        getAccountResponse3 = RestcommAccountsTool.getInstance().getAccount(deploymentUrl.toString(),
                adminUsername, adminAuthToken, sidGrandchild1);
        getAccountResponse4 = RestcommAccountsTool.getInstance().getAccount(deploymentUrl.toString(),
                adminUsername, adminAuthToken, sidGrandchild2);
        //getAccountResponse5 = RestcommAccountsTool.getInstance().getAccount(deploymentUrl.toString(),
        //        adminUsername, adminAuthToken, sidGreatGrandchild1);
        //getAccountResponse6 = RestcommAccountsTool.getInstance().getAccount(deploymentUrl.toString(),
        //        adminUsername, adminAuthToken, sidGreatGrandchild2);

        //check master account status
        assertEquals("Master account status is not updated",  "active", updateAccountResponse.get("status").getAsString());

        //check account tree status
        assertEquals("Child 1 account status is not updated",  "active", getAccountResponse1.get("status").getAsString());
        assertEquals("Child 2 account status is not updated",  "active", getAccountResponse2.get("status").getAsString());
        assertEquals("Grandchild 1 account status is not updated",  "active", getAccountResponse3.get("status").getAsString());
        assertEquals("Grandchild 2 account status is not updated",  "active", getAccountResponse4.get("status").getAsString());
        //assertEquals("Great-grandchild 1 account status is not updated",  "active", getAccountResponse5.get("status").getAsString());
        //assertEquals("Great-grandchild 2 account status is not updated",  "active", getAccountResponse6.get("status").getAsString());
    }

    @Deployment(name = "ClientsEndpointTest", managed = true, testable = false)
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
