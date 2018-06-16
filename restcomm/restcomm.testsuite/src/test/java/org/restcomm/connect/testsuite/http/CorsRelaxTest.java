/*
 * TeleStax, Open Source Cloud Communications
 * Copyright 2011-2014, Telestax Inc and individual contributors
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

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import junit.framework.Assert;
import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.Deployer;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.FixMethodOrder;
import org.junit.runners.MethodSorters;
import org.junit.runner.RunWith;
import org.restcomm.connect.commons.Version;

import javax.ws.rs.core.MultivaluedMap;
import java.net.URL;

/**
 * Tests whether CORS restrictions are relaxed when <rcmlserver/> configuration contains an absolute baseurl.
 * Currently it only tests Accounts endpoint where CORS-relax logic has been applied
 *
 * @author otsakir@gmail.com - Orestis Tsakiridis
 * 
 * It was written for the case when restcomm should accept CORS requests and return the appropriate headers.
 * Initially that would happen if restcomm would be in a different domain from RVD. 
 * Later, we decided to put everything (restcomm and RVD) behind the same domain.
 * So, relaxing CORS restrictions is not really needed
 */
@RunWith(Arquillian.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@Ignore
public class CorsRelaxTest extends EndpointTest {
    private final static Logger logger = Logger.getLogger(CorsRelaxTest.class.getName());

    private static final String version = Version.getVersion();

    @ArquillianResource
    private Deployer deployer;
    @ArquillianResource
    URL deploymentUrl;

    String accountSid = "ACae6e420f425248d6a26948c17a9e2acf";
    String accountToken = "77f8c12cc7b8f8423e5c38b035249166";

    // Tests cors headers existence when retrieving an account
    @Test
    public void corsHeadersAreReturnedForAccount() {
        // Bypass jersey restriction for "Origin" header. By default it can't be added to a WebResource object.
        System.setProperty("sun.net.http.allowRestrictedHeaders", "true");

        Client jersey = Client.create();
        // make a preflight OPTIONS request using an origin present in restcomm.xml/rcmlserver i.e. http://testing.restcomm.com
        WebResource resource = jersey.resource(getResourceUrl("/2012-04-24/Accounts.json/ACae6e420f425248d6a26948c17a9e2acf"));
        ClientResponse response = resource.header("Origin", "http://testing.restcomm.com").options(ClientResponse.class);
        Assert.assertEquals(200, response.getStatus());
        MultivaluedMap<String,String> headers = response.getHeaders();
        String originHeader = headers.getFirst("Access-Control-Allow-Origin");
        Assert.assertEquals("http://testing.restcomm.com",originHeader);
        // make a preflight OPTIONS request using an origin NOT present in restcomm.xml/rcmlserver i.e. http://otherhost.restcomm.com
        WebResource resource2 = jersey.resource(getResourceUrl("/restcomm/2012-04-24/Accounts.json/ACae6e420f425248d6a26948c17a9e2acf"));
        ClientResponse response2 = resource2.header("Origin", "http://otherhost.restcomm.com").options(ClientResponse.class);
        originHeader = response2.getHeaders().getFirst("Access-Control-Allow-Origin");
        Assert.assertEquals(200, response2.getStatus());
        Assert.assertNull(originHeader);

    }

    @Deployment(name = "CorsRelaxTest", managed = true, testable = false)
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
        archive.addAsWebInfResource("restcomm-corsRelax.xml", "conf/restcomm.xml");
        archive.addAsWebInfResource("restcomm.script", "data/hsql/restcomm.script");
        logger.info("Packaged Test App");
        return archive;
    }
}
