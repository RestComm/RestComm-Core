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

import junit.framework.Assert;
import wiremock.org.apache.http.client.ClientProtocolException;

/**
 * The aim of this scenario is to ensure that an account can not manage information from parent accounts or from accounts in the
 * same level. Accounts and Applications endpoints are tested separately due its particularities.
 * 
 * @author guilherme.jansen@telestax.com
 */
@RunWith(Arquillian.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@Category(FeatureExpTests.class)
public class MultitenancyDenyAccessApiTest {

    private final static Logger logger = Logger.getLogger(MultitenancyDenyAccessApiTest.class);
    private static final String version = Version.getVersion();

    @ArquillianResource
    private Deployer deployer;
    @ArquillianResource
    URL deploymentUrl;

    private final static String primaryUsername = "primary@company.com";
    private final static String adminAccountSid = "ACae6e420f425248d6a26948c17a9e2acf";
    private final static String secondaryAccountSid = "AC97c556cd186f58a08520fe5391c54f36";
    private final static String adminApplicationSid = "APf9edf166b5ed4fe58174135df3807601";
    private final static String secondaryApplicationSid = "AP908a3a52b8a548cc9db97f1a1dd9d23b";
    private final static String accountsPassword = "RestComm";

    private final static int httpUnauthorized = 403;

    private final static String apiPath = "2012-04-24/Accounts/";
    private final static String jsonExtension = ".json";

    private enum Endpoint {

        INCOMING_PHONE_NUMBERS("IncomingPhoneNumbers", true, true, true, true, new HashMap<String,String>(){{ put("PhoneNumber","1111"); put("AreaCode","100"); }}, "PNff22dc8d1cdf4d449d666ac09f0bb110", "PN9f9cf955aeb94ebb9d2e09cead5683a4"),
        CALLS("Calls", true, true, false, false, new HashMap<String,String>(){{ put("From","bob"); put("To","alice"); put("Url","/restcomm/url"); }}, "CA9aa1b61e9b864477a820d5c1c9d9bb7d", "CAc6a057e16aa74cb0923c538725ffcf01"), 
        SMS_MESSAGES("SMS/Messages", true, true, false, false, new HashMap<String,String>(){{ put("From","bob"); put("To","alice"); put("Body","Hi"); }}, "SMa272937700b3461bb5d68a3569c61bf1", "SMa272937700b3461bb5d68a3569c61bf2"), 
        CLIENTS("Clients", true, true, true, true, new HashMap<String,String>(){{ put("Login","test"); put("Password","1234"); }}, "CLe95ba029114147c9a9aa42becd0518c0", "CL9bfcb54ead2b44e6bae03f337967a249"), 
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
    public void getListParentAccount() throws ClientProtocolException, IOException {
        String baseUrl = deploymentUrl.toString() + apiPath + adminAccountSid + "/";
        for (Endpoint endpoint : Endpoint.values()) {
            if (endpoint.get) {
                String url = baseUrl + endpoint.name + jsonExtension;
                int statusCode = RestcommMultitenancyTool.getInstance().get(url, primaryUsername, accountsPassword);
                assertTrue(statusCode == httpUnauthorized);
                logger.info("Tested endpoint " + endpoint.name + " (getListParentAccount). Status code = " + statusCode);
            }
        }
    }

    @Test
    public void getListSameLevelAccount() throws ClientProtocolException, IOException {
        String baseUrl = deploymentUrl.toString() + apiPath + secondaryAccountSid + "/";
        for (Endpoint endpoint : Endpoint.values()) {
            if (endpoint.get) {
                String url = baseUrl + endpoint.name + jsonExtension;
                int statusCode = RestcommMultitenancyTool.getInstance().get(url, primaryUsername, accountsPassword);
                assertTrue(statusCode == httpUnauthorized);
                logger.info("Tested endpoint " + endpoint.name + " (getListSameLevelAccount). Status code = " + statusCode);
            }
        }
    }
    
    @Test
    public void postListParentAccount() throws ClientProtocolException, IOException {
        String baseUrl = deploymentUrl.toString() + apiPath + adminAccountSid + "/";
        for (Endpoint endpoint : Endpoint.values()) {
            if (endpoint.postList) {
                String url = baseUrl + endpoint.name + jsonExtension;
                int statusCode = RestcommMultitenancyTool.getInstance().post(url, primaryUsername, accountsPassword,
                        endpoint.postParams);
                assertTrue(statusCode == httpUnauthorized);
                logger.info("Tested endpoint " + endpoint.name + " (postListParentAccount). Status code = " + statusCode);
            }
        }
    }
    
    @Test
    public void postListSameLevelAccount() throws ClientProtocolException, IOException {
        String baseUrl = deploymentUrl.toString() + apiPath + secondaryAccountSid + "/";
        for (Endpoint endpoint : Endpoint.values()) {
            if (endpoint.postList) {
                String url = baseUrl + endpoint.name + jsonExtension;
                int statusCode = RestcommMultitenancyTool.getInstance().post(url, primaryUsername, accountsPassword,
                        endpoint.postParams);
                assertTrue(statusCode == httpUnauthorized);
                logger.info("Tested endpoint " + endpoint.name + " (postListSameLevelAccount). Status code = " + statusCode);
            }
        }
    }
    
    @Test
    public void getElementParentAccount() throws ClientProtocolException, IOException {
        String baseUrl = deploymentUrl.toString() + apiPath + adminAccountSid + "/";
        for (Endpoint endpoint : Endpoint.values()) {
            if (endpoint.get) {
                String url = baseUrl + endpoint.name + "/" + endpoint.elementSameAccount + jsonExtension;
                int statusCode = RestcommMultitenancyTool.getInstance().get(url, primaryUsername, accountsPassword);
                assertTrue(statusCode == httpUnauthorized);
                logger.info("Tested endpoint " + endpoint.name + " (getElementParentAccount). Status code = " + statusCode);
            }
        }
    }
    
    @Test
    public void getElementSameLevelAccount() throws ClientProtocolException, IOException {
        String baseUrl = deploymentUrl.toString() + apiPath + secondaryAccountSid + "/";
        for (Endpoint endpoint : Endpoint.values()) {
            if (endpoint.get) {
                String url = baseUrl + endpoint.name + "/" + endpoint.elementSubaccount + jsonExtension;
                int statusCode = RestcommMultitenancyTool.getInstance().get(url, primaryUsername, accountsPassword);
                assertTrue(statusCode == httpUnauthorized);
                logger.info("Tested endpoint " + endpoint.name + " (getElementSameLevelAccount). Status code = " + statusCode);
            }
        }
    }
    
    @Test
    public void postElementParentAccount() throws ClientProtocolException, IOException {
        String baseUrl = deploymentUrl.toString() + apiPath + adminAccountSid + "/";
        for (Endpoint endpoint : Endpoint.values()) {
            if (endpoint.postElement) {
                String url = baseUrl + endpoint.name + "/" + endpoint.elementSameAccount;
                int statusCode = RestcommMultitenancyTool.getInstance().post(url, primaryUsername, accountsPassword,
                        endpoint.postParams);
                assertTrue(statusCode == httpUnauthorized);
                logger.info("Tested endpoint " + endpoint.name + " (postElementParentAccount). Status code = " + statusCode);
            }
        }
    }
    
    @Test
    public void postElementSameLevelAccount() throws ClientProtocolException, IOException {
        String baseUrl = deploymentUrl.toString() + apiPath + secondaryAccountSid + "/";
        for (Endpoint endpoint : Endpoint.values()) {
            if (endpoint.postElement) {
                String url = baseUrl + endpoint.name + "/" + endpoint.elementSubaccount + jsonExtension;
                int statusCode = RestcommMultitenancyTool.getInstance().post(url, primaryUsername, accountsPassword,
                        endpoint.postParams);
                assertTrue(statusCode == httpUnauthorized);
                logger.info("Tested endpoint " + endpoint.name + " (postElementSameLevelAccount). Status code = " + statusCode);
            }
        }
    }
    
    @Test
    public void deleteElementParentAccount() throws ClientProtocolException, IOException {
        String baseUrl = deploymentUrl.toString() + apiPath + adminAccountSid + "/";
        for (Endpoint endpoint : Endpoint.values()) {
            if (endpoint.delete) {
                String url = baseUrl + endpoint.name + "/" + endpoint.elementSameAccount;
                int statusCode = RestcommMultitenancyTool.getInstance().delete(url, primaryUsername, accountsPassword);
                assertTrue(statusCode == httpUnauthorized);
                logger.info("Tested endpoint " + endpoint.name + " (deleteElementParentAccount). Status code = " + statusCode);
            }
        }
    }
    
    @Test
    public void deleteElementSameLevelAccount() throws ClientProtocolException, IOException {
        String baseUrl = deploymentUrl.toString() + apiPath + secondaryAccountSid + "/";
        for (Endpoint endpoint : Endpoint.values()) {
            if (endpoint.delete) {
                String url = baseUrl + endpoint.name + "/" + endpoint.elementSubaccount;
                int statusCode = RestcommMultitenancyTool.getInstance().delete(url, primaryUsername, accountsPassword);
                assertTrue(statusCode == httpUnauthorized);
                logger.info("Tested endpoint " + endpoint.name + " (deleteElementSameLevelAccount). Status code = " + statusCode);
            }
        }
    }

    @Test
    public void accountsApi() throws ClientProtocolException, IOException {
        // Parent account
        String baseUrl = deploymentUrl.toString() + apiPath.substring(0, apiPath.length()-1);
        int statusCode = RestcommMultitenancyTool.getInstance().get(baseUrl + jsonExtension + "/" + adminAccountSid, primaryUsername, accountsPassword);
        assertTrue(statusCode == httpUnauthorized);
        statusCode = RestcommMultitenancyTool.getInstance().post(baseUrl + jsonExtension + "/" + adminAccountSid, primaryUsername, accountsPassword, new HashMap<String,String>(){{ put("EmailAddress","test@test.com"); put("Password","RestComm");}});
        assertTrue(statusCode == httpUnauthorized);
        Map<String,String> updateParams = new HashMap<String,String>();
        updateParams.put("Status", "closed");
        statusCode = RestcommMultitenancyTool.getInstance().update(baseUrl + jsonExtension + "/" + adminAccountSid, primaryUsername, accountsPassword, updateParams);
        Assert.assertEquals(httpUnauthorized, statusCode);
        
        // Same level account
        statusCode = RestcommMultitenancyTool.getInstance().get(baseUrl + jsonExtension + "/" + secondaryAccountSid, primaryUsername, accountsPassword);
        assertTrue(statusCode == httpUnauthorized);
        statusCode = RestcommMultitenancyTool.getInstance().post(baseUrl + jsonExtension+ "/" + secondaryAccountSid, primaryUsername, accountsPassword, new HashMap<String,String>(){{ put("EmailAddress","test@test.com"); put("Password","RestComm");}});
        assertTrue(statusCode == httpUnauthorized);
        updateParams = new HashMap<String,String>();
        updateParams.put("Status", "closed");
        statusCode = RestcommMultitenancyTool.getInstance().update(baseUrl + jsonExtension + "/" + secondaryAccountSid, primaryUsername, accountsPassword,updateParams);
        assertTrue(statusCode == httpUnauthorized);
    }

    @Test
    public void applicationsApi() throws ClientProtocolException, IOException {
        // Parent account
        String baseUrl = deploymentUrl.toString() + apiPath + adminAccountSid + "/Applications";
        int statusCode = RestcommMultitenancyTool.getInstance().get(baseUrl + jsonExtension, primaryUsername, accountsPassword);
        assertTrue(statusCode == httpUnauthorized);
        statusCode = RestcommMultitenancyTool.getInstance().post(baseUrl + jsonExtension, primaryUsername, accountsPassword, new HashMap<String,String>(){{ put("FriendlyName","TESTABC"); }});
        assertTrue(statusCode == httpUnauthorized);
        statusCode = RestcommMultitenancyTool.getInstance().post(baseUrl + "/" + adminApplicationSid + jsonExtension, primaryUsername, accountsPassword, new HashMap<String,String>(){{ put("FriendlyName","TESTABC"); }});
        assertTrue(statusCode == httpUnauthorized);
        Map<String,String> updateParams = new HashMap<String,String>();
        updateParams.put("Status", "closed");
        statusCode = RestcommMultitenancyTool.getInstance().update(baseUrl + "/" + adminApplicationSid + jsonExtension, primaryUsername, accountsPassword, updateParams );
        assertTrue(statusCode == httpUnauthorized);
        
        // Same level account
        baseUrl = deploymentUrl.toString() + apiPath + secondaryAccountSid + "/Applications";
        statusCode = RestcommMultitenancyTool.getInstance().get(baseUrl + jsonExtension, primaryUsername, accountsPassword);
        assertTrue(statusCode == httpUnauthorized);
        statusCode = RestcommMultitenancyTool.getInstance().post(baseUrl + jsonExtension, primaryUsername, accountsPassword, new HashMap<String,String>(){{ put("FriendlyName","TESTABC"); }});
        assertTrue(statusCode == httpUnauthorized);
        statusCode = RestcommMultitenancyTool.getInstance().post(baseUrl + "/" + secondaryApplicationSid + jsonExtension, primaryUsername, accountsPassword, new HashMap<String,String>(){{ put("FriendlyName","TESTABC"); }});
        assertTrue(statusCode == httpUnauthorized);
        updateParams = new HashMap<String,String>();
        updateParams.put("Status", "closed");
        statusCode = RestcommMultitenancyTool.getInstance().update(baseUrl + "/" + secondaryApplicationSid + jsonExtension, primaryUsername, accountsPassword, updateParams);
        assertTrue(statusCode == httpUnauthorized);
    }

    @Deployment(name = "MultitenancyDenyAccessApiTest", managed = true, testable = false)
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
