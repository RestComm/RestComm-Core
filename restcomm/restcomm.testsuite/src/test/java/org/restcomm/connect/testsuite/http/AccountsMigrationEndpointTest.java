package org.restcomm.connect.testsuite.http;

import com.google.gson.JsonObject;
import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.junit.Test;
import org.junit.FixMethodOrder;
import org.junit.runners.MethodSorters;
import org.junit.runner.RunWith;
import org.restcomm.connect.commons.Version;

import java.net.URL;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(Arquillian.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class AccountsMigrationEndpointTest {
    final static Logger logger = Logger.getLogger(AccountsMigrationEndpointTest.class);
    private static final String version = Version.getVersion();

    @ArquillianResource
    URL deploymentUrl;

    private String adminUsername = "administrator@company.com";
    private String adminFriendlyName = "Default Administrator Account";
    private String adminAccountSid = "ACae6e420f425248d6a26948c17a9e2acf";
    private String adminAuthToken = "77f8c12cc7b8f8423e5c38b035249166";
    private String adminPassword = "RestComm";

    private String parentUsername = "child@company.com";
    private String parentSid = "AC574d775522c96f9aacacc5ca60c8c74f";
    private String parentAuthToken ="77f8c12cc7b8f8423e5c38b035249166";

    @Test
    public void testMigrateParentAndChildAccount() {
        String childSid1 = "AC55555555555555555555555555555555";

        String originalOrganizationSid = "ORafbe225ad37541eba518a74248f0ac4c";
        String newOrganizationSid = "ORafbe225ad37541eba518a74248f0ac4d";

        JsonObject currentParentAccout = RestcommAccountsTool.getInstance().getAccount(deploymentUrl.toString(), adminUsername, adminAuthToken, parentSid);
        assertNotNull(currentParentAccout);
        String currentOrg = currentParentAccout.get("organization").getAsString();
        assertEquals(originalOrganizationSid, currentOrg);

        JsonObject currentChildAccout = RestcommAccountsTool.getInstance().getAccount(deploymentUrl.toString(), adminUsername, adminAuthToken, childSid1);
        assertNotNull(currentChildAccout);
        String currentChildOrg = currentChildAccout.get("organization").getAsString();
        assertEquals(originalOrganizationSid, currentChildOrg);

        //Migrate Parent Account and child accounts
        RestcommAccountsTool.getInstance().migrateAccount(deploymentUrl.toString(), adminUsername,
                adminAuthToken, parentSid, newOrganizationSid);

        JsonObject migratedParentAccount = RestcommAccountsTool.getInstance().getAccount(deploymentUrl.toString(), adminUsername, adminAuthToken, parentSid);
        assertNotNull(migratedParentAccount);
        String parentNewOrg = migratedParentAccount.getAsJsonObject().get("organization").getAsString();
        assertEquals(newOrganizationSid, parentNewOrg);
        assertTrue(!currentOrg.equalsIgnoreCase(parentNewOrg));

        JsonObject childAccount1 = RestcommAccountsTool.getInstance().getAccount(deploymentUrl.toString(), adminUsername, adminAuthToken, childSid1);
        assertNotNull(childAccount1);
        String childNewOrg = childAccount1.get("organization").getAsString();
        assertEquals(newOrganizationSid, childNewOrg);

        //Migrate Parent Account and child accounts
        assertNotNull(RestcommAccountsTool.getInstance().migrateAccount(deploymentUrl.toString(), adminUsername,
                adminAuthToken, parentSid, originalOrganizationSid));
    }


    @Test
    public void testAttemptToMigrateAchildAccountShouldFail() {
        String childSid1 = "AC55555555555555555555555555555555";

        String originalOrganizationSid = "ORafbe225ad37541eba518a74248f0ac4c";
        String newOrganizationSid = "ORafbe225ad37541eba518a74248f0ac4d";

        JsonObject currentChildAccout = RestcommAccountsTool.getInstance().getAccount(deploymentUrl.toString(), adminUsername, adminAuthToken, childSid1);
        assertNotNull(currentChildAccout);
        String currentChildOrg = currentChildAccout.get("organization").getAsString();
        assertEquals(originalOrganizationSid, currentChildOrg);

        //Migrate Parent Account and child accounts
        JsonObject response = RestcommAccountsTool.getInstance().migrateAccount(deploymentUrl.toString(), adminUsername,
                adminAuthToken, childSid1, newOrganizationSid);

        assertNull(response);

        JsonObject childAccount1 = RestcommAccountsTool.getInstance().getAccount(deploymentUrl.toString(), adminUsername, adminAuthToken, childSid1);
        assertNotNull(childAccount1);
        String childNewOrg = childAccount1.get("organization").getAsString();
        assertEquals(originalOrganizationSid, childNewOrg);
    }

    @Test
    public void testAttemptToMigrateAccountByNonSuperAdminShouldFail() {
        String childSid1 = "AC55555555555555555555555555555555";

        String originalOrganizationSid = "ORafbe225ad37541eba518a74248f0ac4c";
        String newOrganizationSid = "ORafbe225ad37541eba518a74248f0ac4d";

        JsonObject currentParentAccout = RestcommAccountsTool.getInstance().getAccount(deploymentUrl.toString(), adminUsername, adminAuthToken, parentSid);
        assertNotNull(currentParentAccout);
        String currentOrg = currentParentAccout.get("organization").getAsString();
        assertEquals(originalOrganizationSid, currentOrg);

        JsonObject currentChildAccout = RestcommAccountsTool.getInstance().getAccount(deploymentUrl.toString(), adminUsername, adminAuthToken, childSid1);
        assertNotNull(currentChildAccout);
        String currentChildOrg = currentChildAccout.get("organization").getAsString();
        assertEquals(originalOrganizationSid, currentChildOrg);

        //Migrate Parent Account and child accounts
        JsonObject response = RestcommAccountsTool.getInstance().migrateAccount(deploymentUrl.toString(), parentUsername,
                parentAuthToken, parentSid, newOrganizationSid);

        assertNull(response);

        assertEquals(originalOrganizationSid, RestcommAccountsTool.getInstance().
                getAccount(deploymentUrl.toString(), adminUsername, adminAuthToken, parentSid).get("organization").getAsString());

    }

    @Deployment(name = "AccountsMigrationEndpointTest", managed = true, testable = false)
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
        archive.addAsWebInfResource("restcomm.script_accounts_migration_test", "data/hsql/restcomm.script");
        logger.info("Packaged Test App");
        return archive;
    }

}
