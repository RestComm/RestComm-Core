/*
 * TeleStax, Open Source Cloud Communications
 * Copyright 2011-2015, Telestax Inc and individual contributors
 * by the @authors tag.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.restcomm.connect.testsuite.http;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import junit.framework.Assert;
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
import org.restcomm.connect.commons.annotations.FeatureAltTests;
import org.restcomm.connect.commons.annotations.UnstableTests;
import wiremock.org.apache.http.client.ClientProtocolException;

/**
 * The aim of this scenario is to ensure that an account can manage its own information and the information from its sub
 * accounts. Accounts and Applications endpoints are tested separately due its particularities.
 *
 * @author guilherme.jansen@telestax.com
 */
@RunWith(Arquillian.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class MultitenancyAllowAccessApiTest {

    private final static Logger logger = Logger.getLogger(MultitenancyAllowAccessApiTest.class);
    private static final String version = Version.getVersion();

    @ArquillianResource
    private Deployer deployer;
    @ArquillianResource
    URL deploymentUrl;

    private final static String adminAccountSid = "ACae6e420f425248d6a26948c17a9e2acf";
    private final static String primaryUsername = "primary@company.com";
    private final static String primaryAccountSid = "AC1ec5d14c34a421c7697e1ce5d4eac782";
    private final static String subaccountaAccountSid = "AC5fa870789e95b7867a0fc0b85c5805e9";
    private final static String subaccountbAccountSid = "AC5fa870789e95b7867a0fc0b85c5805e8";
    private final static String primaryApplicationSid = "APcf92fc10ab384116bfe3df19ef741c7a";
    private final static String accountsPassword = "RestComm";

    private final static int httpOk = 200;
    private final static int httpDeleteOk = 204;

    private final static String apiPath = "2012-04-24/Accounts/";
    private final static String jsonExtension = ".json";

    private enum Endpoint {

        INCOMING_PHONE_NUMBERS("IncomingPhoneNumbers", true, false, true, true, new HashMap<String,String>(){{ put("PhoneNumber","1111"); put("AreaCode","100"); }}, "PNff22dc8d1cdf4d449d666ac09f0bb110", "PN9f9cf955aeb94ebb9d2e09cead5683a4"),
        CALLS("Calls", true, false, false, false, null, "CA9aa1b61e9b864477a820d5c1c9d9bb7d", "CAc6a057e16aa74cb0923c538725ffcf01"),
        SMS_MESSAGES("SMS/Messages", true, false, false, false, null, "SMa272937700b3461bb5d68a3569c61bf1", "SMa272937700b3461bb5d68a3569c61bf2"),
        CLIENTS("Clients", true, true, true, true, new HashMap<String,String>(){{ put("Login","test"); put("Password","Restcomm12"); }}, "CLe95ba029114147c9a9aa42becd0518c0", "CL9bfcb54ead2b44e6bae03f337967a249"),
        OUTGOING_CALLER_IDS("OutgoingCallerIds", true, true, false, true, new HashMap<String,String>(){{ put("PhoneNumber","1111"); }}, "PNfa413fdbf3944932b37bef4bd661c7f7", "PN5a33fa8232d84578af023b1e81e30f67"),
        RECORDINGS("Recordings", true, false, false, false, null, "REacaffdf107da4dc3926e37bddfff44ed", "REacaffdf107da4dc3926e37bddfff44ee"),
        TRANSCRIPTIONS("Transcriptions", true, false, false, true, null, "TRacaffdf107da4dc3926e37bddfff44ee", "TRacaffdf107da4dc3926e37bddfff44ed"),
        NOTIFICATIONS("Notifications", true, false, false, false, null, "NO8927433ce9514b70ac0a76cd36601b9e", "NO8927433ce9514b70ac0a76cd36601b9d");

        String name;
        boolean get;
        boolean postList;
        boolean postElement;
        boolean delete;
        HashMap<String, String> postParams;
        String elementSameAccount;
        String elementSubaccount;

        Endpoint(String name, boolean get, boolean postList, boolean postElement, boolean delete, HashMap<String,String> postParams, String elementSameAccount, String elementSubaccount){
            this.name = name;
            this.get = get;
            this.postList = postList;
            this.postElement = postElement;
            this.delete = delete;
            this.postParams = postParams;
            this.elementSameAccount = elementSameAccount;
            this.elementSubaccount = elementSubaccount;
        }

    }

    @Test
    public void getListSameAccount() throws ClientProtocolException, IOException {
        String baseUrl = deploymentUrl.toString() + apiPath + primaryAccountSid + "/";
        for (Endpoint endpoint : Endpoint.values()) {
            if (endpoint.get) {
                String url = baseUrl + endpoint.name + jsonExtension;
                int statusCode = RestcommMultitenancyTool.getInstance().get(url, primaryUsername, accountsPassword);
                assertTrue(statusCode == httpOk);
                logger.info("Tested endpoint " + endpoint.name + " (getListSameAccount). Status code = " + statusCode);
            }
        }
    }

    @Test
    @Category(FeatureAltTests.class)
    public void getListSubaccount() throws ClientProtocolException, IOException {
        String baseUrl = deploymentUrl.toString() + apiPath + subaccountaAccountSid + "/";
        for (Endpoint endpoint : Endpoint.values()) {
            if (endpoint.get) {
                String url = baseUrl + endpoint.name + jsonExtension;
                int statusCode = RestcommMultitenancyTool.getInstance().get(url, primaryUsername, accountsPassword);
                assertTrue(statusCode == httpOk);
                logger.info("Tested endpoint " + endpoint.name + " (getListSubaccount). Status code = " + statusCode);
            }
        }
    }

    @Test
    @Category(UnstableTests.class)
    public void postListSameAccount() throws ClientProtocolException, IOException {
        String baseUrl = deploymentUrl.toString() + apiPath + primaryAccountSid + "/";
        for (Endpoint endpoint : Endpoint.values()) {
            if (endpoint.postList) {
                String url = baseUrl + endpoint.name + jsonExtension;
                int statusCode = RestcommMultitenancyTool.getInstance().post(url, primaryUsername, accountsPassword,
                        endpoint.postParams);
                Assert.assertEquals(httpOk, statusCode);
                logger.info("Tested endpoint " + endpoint.name + " (postListSameAccount). Status code = " + statusCode);
            }
        }
    }

    @Test
    @Category(FeatureAltTests.class)
    public void postListSubaccount() throws ClientProtocolException, IOException {
        Endpoint endpoints[] = modifyPostParameters("2");
        String baseUrl = deploymentUrl.toString() + apiPath + subaccountaAccountSid + "/";
        for (Endpoint endpoint : endpoints) {
            if (endpoint.postList) {
                String url = baseUrl + endpoint.name + jsonExtension;
                int statusCode = RestcommMultitenancyTool.getInstance().post(url, primaryUsername, accountsPassword,
                        endpoint.postParams);
                Assert.assertEquals(httpOk, statusCode);
                logger.info("Tested endpoint " + endpoint.name + " (postListSubaccount). Status code = " + statusCode);
            }
        }
    }

    @Test
    @Category(UnstableTests.class)
    /**
     * using aaa prefix to ensure order and prevent sideeffects
     */
    public void aaagetElementSameAccount() throws ClientProtocolException, IOException {
        String baseUrl = deploymentUrl.toString() + apiPath + primaryAccountSid + "/";
        for (Endpoint endpoint : Endpoint.values()) {
            if (endpoint.get) {
                String url = baseUrl + endpoint.name + "/" + endpoint.elementSameAccount + jsonExtension;
                int statusCode = RestcommMultitenancyTool.getInstance().get(url, primaryUsername, accountsPassword);
                Assert.assertEquals(httpOk, statusCode);
                logger.info("Tested endpoint " + endpoint.name + " (getElementSameAccount). Status code = " + statusCode);
            }
        }
    }

    @Test
    @Category({UnstableTests.class, FeatureAltTests.class})
    public void aaagetElementSubaccount() throws ClientProtocolException, IOException {
        String baseUrl = deploymentUrl.toString() + apiPath + subaccountaAccountSid + "/";
        for (Endpoint endpoint : Endpoint.values()) {
            if (endpoint.get) {
                String url = baseUrl + endpoint.name + "/" + endpoint.elementSubaccount + jsonExtension;
                int statusCode = RestcommMultitenancyTool.getInstance().get(url, primaryUsername, accountsPassword);
                assertTrue(statusCode == httpOk);
                logger.info("Tested endpoint " + endpoint.name + " (getElementSubaccount). Status code = " + statusCode);
            }
        }
    }

    @Test
    @Category(UnstableTests.class)
    /**
     * using aaa prefix to ensure order and prevent sideeffects
     */
    public void aaapostElementSameAccount() throws ClientProtocolException, IOException {
        Endpoint endpoints[] = modifyPostParameters("3");
        String baseUrl = deploymentUrl.toString() + apiPath + primaryAccountSid + "/";
        for (Endpoint endpoint : endpoints) {
            if (endpoint.postElement) {
                String url = baseUrl + endpoint.name + "/" + endpoint.elementSameAccount;
                int statusCode = RestcommMultitenancyTool.getInstance().post(url, primaryUsername, accountsPassword,
                        endpoint.postParams);
                Assert.assertEquals(httpOk, statusCode);
                logger.info("Tested endpoint " + endpoint.name + " (postElementSameAccount). Status code = " + statusCode);
            }
        }
    }

    @Test
    @Category(FeatureAltTests.class)
    /**
     * using aaa prefix to ensure order and prevent sideeffects
     */
    public void aaapostElementSubaccount() throws ClientProtocolException, IOException {
        Endpoint endpoints[] = modifyPostParameters("4");
        String baseUrl = deploymentUrl.toString() + apiPath + subaccountaAccountSid + "/";
        for (Endpoint endpoint : endpoints) {
            if (endpoint.postElement) {
                String url = baseUrl + endpoint.name + "/" + endpoint.elementSubaccount + jsonExtension;
                int statusCode = RestcommMultitenancyTool.getInstance().post(url, primaryUsername, accountsPassword,
                        endpoint.postParams);
                Assert.assertEquals(httpOk, statusCode);
                logger.info("Tested endpoint " + endpoint.name + " (postElementSubaccount). Status code = " + statusCode);
            }
        }
    }

    @Test
    public void deleteElementSameAccount() throws ClientProtocolException, IOException {
        String baseUrl = deploymentUrl.toString() + apiPath + primaryAccountSid + "/";
        for (Endpoint endpoint : Endpoint.values()) {
            if (endpoint.delete) {
                String url = baseUrl + endpoint.name + "/" + endpoint.elementSameAccount;
                int statusCode = RestcommMultitenancyTool.getInstance().delete(url, primaryUsername, accountsPassword);
                assertTrue(statusCode == httpDeleteOk || statusCode == httpOk);
                logger.info("Tested endpoint " + endpoint.name + " (deleteElementSameAccount). Status code = " + statusCode);
            }
        }
    }

    @Test
    @Category(FeatureAltTests.class)
    public void deleteElementSubaccount() throws ClientProtocolException, IOException {
        String baseUrl = deploymentUrl.toString() + apiPath + subaccountaAccountSid + "/";
        for (Endpoint endpoint : Endpoint.values()) {
            if (endpoint.delete) {
                String url = baseUrl + endpoint.name + "/" + endpoint.elementSubaccount;
                int statusCode = RestcommMultitenancyTool.getInstance().delete(url, primaryUsername, accountsPassword);
                assertTrue(statusCode == httpDeleteOk || statusCode == httpOk);
                logger.info("Tested endpoint " + endpoint.name + " (deleteElementSubaccount). Status code = " + statusCode);
            }
        }
    }

    @Test
    /**
     * using aaa prefix to ensure order and prevent sideeffects
     */
    public void aaaaccountsApi() throws ClientProtocolException, IOException {
        // Same account
        String baseUrl = deploymentUrl.toString() + apiPath.substring(0, apiPath.length()-1);
        int statusCode = RestcommMultitenancyTool.getInstance().get(baseUrl + jsonExtension, primaryUsername, accountsPassword);
        Assert.assertEquals(httpOk, statusCode);
        statusCode = RestcommMultitenancyTool.getInstance().post(baseUrl + jsonExtension, primaryUsername, accountsPassword, new HashMap<String,String>(){{ put("EmailAddress","test@test.com"); put("Password","RestComm12");}});
        Assert.assertEquals(httpOk, statusCode);

        // Sub account
        baseUrl = deploymentUrl.toString() + apiPath.substring(0, apiPath.length()-1);
        statusCode = RestcommMultitenancyTool.getInstance().get(baseUrl + jsonExtension + "/" + subaccountbAccountSid, primaryUsername, accountsPassword);
        Assert.assertEquals(httpOk, statusCode);
        statusCode = RestcommMultitenancyTool.getInstance().post(baseUrl + "/" + subaccountbAccountSid, primaryUsername, accountsPassword, new HashMap<String,String>(){{ put("EmailAddress","test2@test.com"); put("Password","RestComm12");}});
        Assert.assertEquals(httpOk, statusCode);
        Map<String,String> updateParams = new HashMap<String,String>();
        updateParams.put("Status", "closed");
        statusCode = RestcommMultitenancyTool.getInstance().update(baseUrl + jsonExtension + "/" + subaccountbAccountSid, primaryUsername, accountsPassword,updateParams);
        assertTrue(statusCode == httpDeleteOk || statusCode == httpOk);
    }

    @Test
    public void applicationsApi() throws ClientProtocolException, IOException {
        // access to account's own applications
        String baseUrl = deploymentUrl.toString() + apiPath + primaryAccountSid + "/Applications";
        int statusCode = RestcommMultitenancyTool.getInstance().get(baseUrl + jsonExtension, primaryUsername, accountsPassword);
        assertTrue(statusCode == httpOk);
        statusCode = RestcommMultitenancyTool.getInstance().get(baseUrl + "/" + primaryApplicationSid + jsonExtension, primaryUsername, accountsPassword);
        assertTrue(statusCode == httpOk);
        statusCode = RestcommMultitenancyTool.getInstance().post(baseUrl + jsonExtension, primaryUsername, accountsPassword, new HashMap<String,String>(){{ put("FriendlyName","TEST"); }});
        assertTrue(statusCode == httpOk);
        statusCode = RestcommMultitenancyTool.getInstance().post(baseUrl + "/" + primaryApplicationSid + jsonExtension, primaryUsername, accountsPassword, new HashMap<String,String>(){{ put("FriendlyName","TEST123"); }});
        assertTrue(statusCode == httpOk);
        // access to sub-account applications
        // list
        statusCode = RestcommMultitenancyTool.getInstance().get(baseUrl + jsonExtension, adminAccountSid, accountsPassword);
        Assert.assertEquals(httpOk, statusCode);
        // get single
        statusCode = RestcommMultitenancyTool.getInstance().get(baseUrl + "/" + primaryApplicationSid + jsonExtension, adminAccountSid, accountsPassword);
        assertTrue(statusCode == httpOk);
        // create
        statusCode = RestcommMultitenancyTool.getInstance().post(baseUrl + jsonExtension, adminAccountSid, accountsPassword, new HashMap<String,String>(){{ put("FriendlyName","TEST"); }});
        assertTrue(statusCode == httpOk);
        // update
        statusCode = RestcommMultitenancyTool.getInstance().post(baseUrl + "/" + primaryApplicationSid + jsonExtension, adminAccountSid, accountsPassword, new HashMap<String,String>(){{ put("FriendlyName","TEST123"); }});
        assertTrue(statusCode == httpOk);

    }

    private Endpoint[] modifyPostParameters(String suffix) {
        Endpoint endpoints[] = Endpoint.values();
        for (Endpoint endpoint : endpoints) {
            if (endpoint.postElement || endpoint.postList) {
                for (String k : endpoint.postParams.keySet()) {
                    endpoint.postParams.put(k, endpoint.postParams.get(k) + suffix);
                }
            }
        }
        return endpoints;
    }

    @Deployment(name = "MultitenancyAllowAccessApiTest", managed = true, testable = false)
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
        archive.addAsWebInfResource("restcomm.script_multitenancyTest", "data/hsql/restcomm.script");
        return archive;
    }

}
