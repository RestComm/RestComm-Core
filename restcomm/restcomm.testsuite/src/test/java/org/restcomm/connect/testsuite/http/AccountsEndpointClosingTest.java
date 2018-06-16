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

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.core.util.MultivaluedMapImpl;
import junit.framework.Assert;

import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.Deployer;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.FixMethodOrder;
import org.junit.runners.MethodSorters;
import org.junit.runner.RunWith;
import org.restcomm.connect.commons.Version;

import javax.ws.rs.core.MultivaluedMap;

import java.net.URL;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.containing;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import org.junit.Ignore;

/**
 * @author otsakir@gmail.com - Orestis Tsakiridis
 */
@RunWith(Arquillian.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class AccountsEndpointClosingTest extends EndpointTest {
    private final static Logger logger = Logger.getLogger(AccountsEndpointClosingTest.class.getName());

    private static final String version = Version.getVersion();

    @ArquillianResource
    private Deployer deployer;
    @ArquillianResource
    URL deploymentUrl;

    String toplevelSid = "ACA0000000000000000000000000000000";
    String toplevelKey = "77f8c12cc7b8f8423e5c38b035249166";

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(8090);

    @Before
    public void before() {
        stubFor(post(urlMatching("/restcomm-rvd/services/notifications")).willReturn(aResponse().withStatus(200)));
    }

    // verify that acount-removal notifications are sent to the application server (RVD)
    @Test
    public void removeAccountAndSendNotifications() throws InterruptedException {

        String closedParentSid = "ACA1000000000000000000000000000000";
        Client jersey = getClient(toplevelSid, toplevelKey);
        WebResource resource = jersey.resource( getResourceUrl("/2012-04-24/Accounts.json/" + closedParentSid) );
        MultivaluedMap<String,String> params = new MultivaluedMapImpl();
        params.add("Status","closed");
        ClientResponse response = resource.put(ClientResponse.class,params);
        Assert.assertEquals(200, response.getStatus());
        // wait until all asynchronous request have been sent to RVD
        verify(6, postRequestedFor(urlMatching("/restcomm-rvd/services/notifications"))
                .withHeader("Content-Type", containing("application/json"))
                //.withRequestBody(equalToJson("[{\"type\":\"accountClosed\",\"accountSid\":\"ACA1000000000000000000000000000005\"},{\"type\":\"accountClosed\",\"accountSid\":\"ACA1000000000000000000000000000004\"},{\"type\":\"accountClosed\",\"accountSid\":\"ACA1000000000000000000000000000003\"},{\"type\":\"accountClosed\",\"accountSid\":\"ACA1000000000000000000000000000002\"},{\"type\":\"accountClosed\",\"accountSid\":\"ACA1000000000000000000000000000001\"},{\"type\":\"accountClosed\",\"accountSid\":\"ACA1000000000000000000000000000000\"}]")
                );
    }

    // verify that DID provider (nexmo) was contacted to cancel the numbers when the account is removed
    @Test
    public void removeAccountAndReleaseProvidedNumbers() {
        stubFor(post(urlMatching("/nexmo/number/cancel/.*/.*/US/12223334444"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("")));
        stubFor(post(urlMatching("/nexmo/number/cancel/.*/.*/US/12223334445"))
                .willReturn(aResponse()
                        .withStatus(404)
                        .withHeader("Content-Type", "application/json")
                        .withBody("")));

        String closedParentSid = "ACA2000000000000000000000000000000";
        Client jersey = getClient(toplevelSid, toplevelKey);
        WebResource resource = jersey.resource( getResourceUrl("/2012-04-24/Accounts.json/" + closedParentSid) );
        MultivaluedMap<String,String> params = new MultivaluedMapImpl();
        params.add("Status","closed");
        ClientResponse response = resource.put(ClientResponse.class,params);
        Assert.assertEquals(200, response.getStatus());
        // make sure the request reached nexmo
        verify(postRequestedFor(urlMatching("/nexmo/number/cancel/.*/.*/US/12223334444")));
        verify(postRequestedFor(urlMatching("/nexmo/number/cancel/.*/.*/US/12223334445")));
        // confirm that numbers that were not successfully released, are still in the database
        resource = jersey.resource( getResourceUrl("/2012-04-24/Accounts/" + closedParentSid + "/IncomingPhoneNumbers/PH00000000000000000000000000000002.json") );
        response = resource.get(ClientResponse.class);
        Assert.assertEquals(200, response.getStatus());
    }

    @Deployment(name = "AccountsEndpointClosingTest", managed = true, testable = false)
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
        archive.addAsWebInfResource("restcomm-accountRemoval.xml", "conf/restcomm.xml");
        archive.addAsWebInfResource("restcomm.script_account_removal_test", "data/hsql/restcomm.script");
        logger.info("Packaged Test App");
        return archive;
    }
}
