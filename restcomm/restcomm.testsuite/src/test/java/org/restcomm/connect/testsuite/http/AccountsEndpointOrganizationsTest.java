/*
 *  TeleStax, Open Source Cloud Communications
 *  Copyright 2011-2018, Telestax Inc and individual contributors
 *  by the @authors tag.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  under the terms of the GNU Affero General Public License as
 *  published by the Free Software Foundation; either version 3 of
 *  the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>
 */

package org.restcomm.connect.testsuite.http;

import com.google.gson.JsonObject;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.filter.HTTPBasicAuthFilter;
import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.Deployer;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.restcomm.connect.commons.Version;
import org.restcomm.connect.testsuite.http.util.CustomDnsResolver;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author <a href="mailto:gvagenas@gmail.com">gvagenas</a>
 */

@RunWith(Arquillian.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class AccountsEndpointOrganizationsTest extends EndpointTest {
    private final static Logger logger = Logger.getLogger(AccountsEndpointOrganizationsTest.class.getName());

    private static final String version = Version.getVersion();

    @ArquillianResource
    private Deployer deployer;
    @ArquillianResource
    URL deploymentUrl;

    private String org1Username = "administrator@org1.restcomm.com";
    private String adminFriendlyName = "Default Administrator Account";
    private String org1AccountSid = "ACae6e420f425248d6a26948c17a9e2acg";
    private String org1AuthToken = "77f8c12cc7b8f8423e5c38b035249166";

    private String org2Username = "administrator@org2.restcomm.com";
    private String org2AccountSid = "ACae6e420f425248d6a26948c17a9e2ach";
    private String org2AuthToken = "77f8c12cc7b8f8423e5c38b035249166";

    private String org3Username = "administrator@org3.restcomm.com";
    private String org3AccountSid = "ACae6e420f425248d6a26948c17a9e2acl";
    private String org3AuthToken = "77f8c12cc7b8f8423e5c38b035249166";

    @BeforeClass
    public static void beforeClass() {
        Map<String, String> domainIpMap = new HashMap<>();
        domainIpMap.put("org1.restcomm.com", "127.0.0.1");
        domainIpMap.put("org2.restcomm.com", "127.0.0.1");
        domainIpMap.put("org3.restcomm.com", "127.0.0.1");
        CustomDnsResolver.setNameService(domainIpMap);
    }

    @Test
    public void testGetAccount() {
        // Get Account using admin email address and user email address
        String url = "http://org1.restcomm.com:8080/restcomm";
        JsonObject adminAccount = RestcommAccountsTool.getInstance().getAccount(url, org1Username,
                org1AuthToken, org1Username);
        assertTrue(adminAccount.get("sid").getAsString().equals(org1AccountSid));
    }

    @Test
    public void testGetAccountOrg2() {
        // Get Account using admin email address and user email address
        String org2Url = "http://org2.restcomm.com:8080/restcomm";
        JsonObject org2account = RestcommAccountsTool.getInstance().getAccount(org2Url, org2Username,
                org2AuthToken, org2Username);
        assertTrue(org2account.get("sid").getAsString().equals(org2AccountSid));
    }

    @Test
    public void testGetAccountOrg3() {
//        when(new InetSocketAddress(any(String.class),8080)).thenReturn(new InetSocketAddress("127.0.0.1",8080));
        // Get Account using admin email address and user email address
        String org3Url = "http://org3.restcomm.com:8080/restcomm";
        JsonObject org3account = RestcommAccountsTool.getInstance().getAccount(org3Url, org3Username,
                org3AuthToken, org3Username);
        assertTrue(org3account.get("sid").getAsString().equals(org3AccountSid));
    }

    @Test @Ignore //TODO temporarely disable organization domain validation - https://telestax.atlassian.net/browse/BS-408
    public void testGetAccountOrg3WithOrg2Url() {
        // Get Account using admin email address and user email address
        String org2Url = "http://org2.restcomm.com:8080/restcomm";
        ClientResponse accountResponse = RestcommAccountsTool.getInstance().getAccountResponse(org2Url, org3Username,
                org3AuthToken, org3Username);
        assertEquals(403, accountResponse.getClientResponseStatus().getStatusCode());
    }

    @Test
    public void testRemoveClientOfOrg2UsingOrg3Credentials() {
        String org2Url = "http://org2.restcomm.com:8080/restcomm";
        String clientOfORg2 = "CLae6e420f425248d6a26948c17a9e2ach";

        Client jerseyClient = Client.create();
        jerseyClient.addFilter(new HTTPBasicAuthFilter(org2Username, org3AuthToken));
        WebResource resource = jerseyClient.resource(org2Url).path("/2012-04-24/Accounts/" + org3AccountSid+ "/Clients/" + clientOfORg2);
        ClientResponse response = resource.delete(ClientResponse.class);

        //This request will fail because Client is trying to access resources of ORG2 using credentials of ORG3
        //and security filter will drop this request because ORG3.domainName != Request.host
        assertEquals(403, response.getStatus());
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
        archive.addAsWebInfResource("restcomm.script-accounts_organizations", "data/hsql/restcomm.script");
        logger.info("Packaged Test App");
        return archive;
    }

}
