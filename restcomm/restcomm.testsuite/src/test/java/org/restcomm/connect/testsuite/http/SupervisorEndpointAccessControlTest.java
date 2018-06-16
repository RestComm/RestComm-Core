/*
 * TeleStax, Open Source Cloud Communications
 * Copyright 2011-2016, Telestax Inc and individual contributors
 * by the @authors tag.
 *
 * This program is free software: you can redistribute it and/or modify
 * under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation; either version 3 of
 * the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 *
 */

package org.restcomm.connect.testsuite.http;

import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.junit.Assert;
import org.junit.Test;
import org.junit.FixMethodOrder;
import org.junit.runners.MethodSorters;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.restcomm.connect.commons.annotations.FeatureAltTests;
import org.restcomm.connect.commons.annotations.FeatureExpTests;
import org.restcomm.connect.testsuite.tools.MonitoringServiceTool;

import com.google.gson.JsonObject;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.filter.HTTPBasicAuthFilter;

/**
 * SupervisorEndpointAccessControlTest Supervisor metrices should be accessible to only Super Admin role.
 *
 * @author Maria Farooq <dam dam nak nak>
 */
@RunWith(Arquillian.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class SupervisorEndpointAccessControlTest extends EndpointTest {
    protected final static Logger logger = Logger.getLogger(SupervisorEndpointAccessControlTest.class);

    private String superAdminAccountSid = "ACae6e420f425248d6a26948c17a9e2acf";
    private String superAdminAuthToken = "77f8c12cc7b8f8423e5c38b035249166";

    static String adminAccountSid = "AC22222222222222222222222222222222";
    static String userUsername = "user@company.com";
    static String adminAccountAuthToken = "77f8c12cc7b8f8423e5c38b035249166";

    static String developerSid = "AC11111111111111111111111111111111";
    static String developerUsername = "developer@company.com";
    static String developerAuthToken = "77f8c12cc7b8f8423e5c38b035249166";

    private String superAdmin2AccountSid = "AC00000000000000000000000000000000";
    private String superAdmin2AuthToken = "77f8c12cc7b8f8423e5c38b035249166";

    /**
     * Super Admin should be able to access supervisor metrics
     */
    @Test
    public void testSuperAdminPermissionTest() {
    	JsonObject metrics = MonitoringServiceTool.getInstance().getMetrics(deploymentUrl.toString(), superAdminAccountSid, superAdminAuthToken);
        Assert.assertNotNull(metrics);
    }


    /**
     *  Super Admins (but not ancestors) SHOULD be able to access supervisor metrics
     */
    @Test
    @Category(FeatureAltTests.class)
    public void testSuperAdmin2PermissionTest() {
        JsonObject metrics = MonitoringServiceTool.getInstance().getMetrics(deploymentUrl.toString(), superAdmin2AccountSid, superAdmin2AuthToken);
        Assert.assertNotNull(metrics);
    }

    /**
     *  Admin should NOT be able to access supervisor metrics
     */
    @Test
    @Category(FeatureExpTests.class)
    public void testAdminPermissionTest() {
    	Client jerseyClient = Client.create();
        jerseyClient.addFilter(new HTTPBasicAuthFilter(adminAccountSid, adminAccountAuthToken));
        String url = MonitoringServiceTool.getInstance().getAccountsUrl(deploymentUrl.toString(), adminAccountSid);
        WebResource webResource = jerseyClient.resource(url+"/metrics");
        ClientResponse response = webResource.get(ClientResponse.class);
        Assert.assertEquals(403, response.getStatus());
    }

    /**
     *  Developer should NOT be able to access supervisor metrics
     */
    @Test
    @Category(FeatureExpTests.class)
    public void testDeveloperPermissionTest() {
    	Client jerseyClient = Client.create();
        jerseyClient.addFilter(new HTTPBasicAuthFilter(developerSid, developerAuthToken));
        String url = MonitoringServiceTool.getInstance().getAccountsUrl(deploymentUrl.toString(), developerSid);
        WebResource webResource = jerseyClient.resource(url+"/metrics");
        ClientResponse response = webResource.get(ClientResponse.class);
        Assert.assertEquals(403, response.getStatus());
    }

    @Deployment(name = "RoleSensitiveTest", managed = true, testable = false)
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
        archive.addAsWebInfResource("restcomm_roles_permissions.xml", "conf/restcomm.xml");
        archive.addAsWebInfResource("restcomm.script_supervisor_permission_test", "data/hsql/restcomm.script");
        logger.info("Packaged Test App");
        return archive;
    }
}
