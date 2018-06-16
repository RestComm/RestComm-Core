/*
 * TeleStax, Open Source Cloud Communications
 * Copyright 2011-2013, Telestax Inc and individual contributors
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

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Locale;

import javax.ws.rs.core.MultivaluedMap;

import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.apache.http.client.ClientProtocolException;
import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.Deployer;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.FixMethodOrder;
import org.junit.runners.MethodSorters;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.restcomm.connect.dao.entities.Geolocation;
import org.restcomm.connect.commons.Version;
import org.restcomm.connect.commons.annotations.FeatureAltTests;
import org.restcomm.connect.commons.annotations.FeatureExpTests;
import org.restcomm.connect.commons.dao.Sid;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.jersey.core.util.MultivaluedMapImpl;

/**
 * @author <a href="mailto:fernando.mendioroz@telestax.com"> Fernando Mendioroz </a>
 *
 */
@RunWith(Arquillian.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class GeolocationEndpointTest {

    private static final Logger logger = Logger.getLogger(GeolocationEndpointTest.class);
    private static final String version = Version.getVersion();
    private static final String ImmediateGT = Geolocation.GeolocationType.Immediate.toString();
    private static final String NotificationGT = Geolocation.GeolocationType.Notification.toString();

    @ArquillianResource
    private Deployer deployer;
    @ArquillianResource
    URL deploymentUrl;

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(8090); // No-args constructor defaults to port 8080

    private String adminUsername = "administrator@company.com";
    private String adminAccountSid = "ACae6e420f425248d6a26948c17a9e2acf";
    private String adminAuthToken = "77f8c12cc7b8f8423e5c38b035249166";

    @After
    public void after() throws InterruptedException {
        wireMockRule.resetRequests();
        Thread.sleep(1000);
    }

    String gmlcResponse = "mcc=598,mnc=1,lac=320,cellid=521,aol=0,vlrNumber=598001,latitude=35.349781,longitude=87.754320,civicAddress=Avenue2501";

    @Test
    public void testCreateAndGetImmediateGeolocation()
        throws ParseException, IllegalArgumentException, ClientProtocolException, IOException {

        String msisdn = "5989738292";

        //This is for POST requests
        stubFor(post(urlPathEqualTo("/restcomm/gmlc/rest"))
                .withQueryParam("msisdn", equalTo(msisdn))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(gmlcResponse)));

        //This is for GET requests - REMOVE if not needed
        stubFor(get(urlPathEqualTo("/restcomm/gmlc/rest"))
                .withQueryParam("msisdn", equalTo(msisdn))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "text/xml")
                        .withBody(gmlcResponse)));

        // Define Immediate Geolocation attributes for this method
        String deviceIdentifier;

        // Test create Immediate type of Geolocation via POST (only mandatory parameters)
        // Parameter values Assignment
        MultivaluedMap<String, String> geolocationParams = new MultivaluedMapImpl();
        geolocationParams.add("DeviceIdentifier", deviceIdentifier = msisdn);
        geolocationParams.add("StatusCallback", "http://192.1.0.19:8080/ACae6e420f425248d6a26948c17a9e2acf");
        // HTTP POST Geolocation creation with given parameters values and those returned via GMLC stub
        JsonObject geolocationJson = RestcommGeolocationsTool.getInstance().createImmediateGeolocation(deploymentUrl.toString(),
            adminAccountSid, adminUsername, adminAuthToken, geolocationParams);
        Sid geolocationSid = new Sid(geolocationJson.get("sid").getAsString());

        // Test asserts via GET to a single Geolocation
        geolocationJson = RestcommGeolocationsTool.getInstance().getImmediateGeolocation(deploymentUrl.toString(),
            adminUsername, adminAuthToken, adminAccountSid, geolocationSid.toString());

        SimpleDateFormat df = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss Z", Locale.US);
        assertTrue(df.parse(geolocationJson.get("date_created").getAsString()) != null);
        assertTrue(df.parse(geolocationJson.get("date_updated").getAsString()) != null);
        assertTrue(df.parse(geolocationJson.get("date_executed").getAsString()) != null);
        assertTrue(geolocationJson.get("account_sid").getAsString().equals(adminAccountSid));
        assertTrue(geolocationJson.get("device_identifier").getAsString().equals(deviceIdentifier));
        assertTrue(geolocationJson.get("geolocation_type").getAsString().equals(ImmediateGT));
        assertTrue(geolocationJson.get("response_status") == null);
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("cell_id").getAsString().equals("521"));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("location_area_code").getAsString().equals("320"));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("mobile_country_code").getAsString().equals("598"));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("mobile_network_code").getAsString().equals("1"));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("network_entity_address").getAsString().equals("598001"));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("location_age").getAsString().equals("0"));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("device_latitude").getAsString().equals("35.349781"));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("device_longitude").getAsString().equals("87.754320"));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("accuracy") == null);
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("internet_address") == null);
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("physical_address") == null);
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("formatted_address").getAsString().equals("Avenue2501"));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("location_timestamp") == null);
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("event_geofence_latitude") == null);
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("event_geofence_longitude") == null);
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("radius") == null);
        assertTrue(geolocationJson.get("geolocation_positioning_type") == null);
        assertTrue(geolocationJson.get("last_geolocation_response") == null);
        assertTrue(geolocationJson.get("cause") == null);
        assertTrue(geolocationJson.get("api_version").getAsString().equals("2012-04-24"));

        // Test asserts via GET to a Geolocation list
        JsonArray immediateGeolocationsListJson = RestcommGeolocationsTool.getInstance()
            .getGeolocations(deploymentUrl.toString(), adminUsername, adminAuthToken, adminAccountSid);
        geolocationJson = immediateGeolocationsListJson.get(0).getAsJsonObject();
        assertTrue(df.parse(geolocationJson.get("date_created").getAsString()) != null);
        assertTrue(df.parse(geolocationJson.get("date_updated").getAsString()) != null);
        assertTrue(df.parse(geolocationJson.get("date_executed").getAsString()) != null);
        assertTrue(geolocationJson.get("account_sid").getAsString().equals(adminAccountSid));
        assertTrue(geolocationJson.get("device_identifier").getAsString().equals(deviceIdentifier));
        assertTrue(geolocationJson.get("geolocation_type").getAsString().equals(ImmediateGT));
        assertTrue(geolocationJson.get("response_status") == null);
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("cell_id").getAsString().equals("521"));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("location_area_code").getAsString().equals("320"));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("mobile_country_code").getAsString().equals("598"));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("mobile_network_code").getAsString().equals("1"));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("network_entity_address").getAsString().equals("598001"));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("location_age").getAsString().equals("0"));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("device_latitude").getAsString().equals("35.349781"));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("device_longitude").getAsString().equals("87.754320"));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("accuracy") == null);
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("internet_address") == null);
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("physical_address") == null);
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("formatted_address").getAsString().equals("Avenue2501"));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("location_timestamp") == null);
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("event_geofence_latitude") == null);
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("event_geofence_longitude") == null);
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("radius") == null);
        assertTrue(geolocationJson.get("geolocation_positioning_type") == null);
        assertTrue(geolocationJson.get("last_geolocation_response") == null);
        assertTrue(geolocationJson.get("cause") == null);
        assertTrue(geolocationJson.get("api_version").getAsString().equals("2012-04-24"));

        // Remove created Geolocation via HTTP DELETE
        RestcommGeolocationsTool.getInstance().deleteImmediateGeolocation(deploymentUrl.toString(), adminUsername,
            adminAuthToken, adminAccountSid, geolocationSid.toString());

    }

    @Test
    @Category(FeatureAltTests.class)
    public void testCreateNotApiCompliantImmediateGeolocation()
        throws ParseException, IllegalArgumentException, ClientProtocolException, IOException {

        String msisdn = "5989738292";

        //This is for POST requests
        stubFor(post(urlPathEqualTo("/restcomm/gmlc/rest"))
            .withQueryParam("msisdn", equalTo(msisdn))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "text/xml")
                .withBody(gmlcResponse)));

        //This is for GET requests - REMOVE if not needed
        stubFor(get(urlPathEqualTo("/restcomm/gmlc/rest"))
            .withQueryParam("msisdn", equalTo(msisdn))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "text/xml")
                .withBody(gmlcResponse)));

        // Define Immediate Geolocation attributes for this method
        String deviceIdentifier;

        // Test create Immediate type of Geolocation via POST with one missing mandatory parameter
        // Parameter values Assignment, StatusCallback missing
        MultivaluedMap<String, String> geolocationParams = new MultivaluedMapImpl();
        geolocationParams.add("DeviceIdentifier", deviceIdentifier = msisdn);
        Sid rejectedGeolocationSid = null;
        // HTTP POST Geolocation creation with given parameters values
        try {
            JsonObject missingParamGeolocationJson = RestcommGeolocationsTool.getInstance().createImmediateGeolocation(
                deploymentUrl.toString(), adminAccountSid, adminUsername, adminAuthToken, geolocationParams);
            rejectedGeolocationSid = new Sid(missingParamGeolocationJson.get("sid").getAsString());
            JsonObject rejectedGeolocationJson = RestcommGeolocationsTool.getInstance().getImmediateGeolocation(
                deploymentUrl.toString(), adminUsername, adminAuthToken, adminAccountSid,
                rejectedGeolocationSid.toString());
            assertTrue(rejectedGeolocationJson == null);
        } catch (Exception exception) {
            // Checking Test asserts via HTTP GET (no record found as POST returned a response status of 400 Bad Request)
            assertTrue(rejectedGeolocationSid == null);
            logger.info("Exception during HTTP POST: " + exception.getMessage());
        }

        // Test create Immediate type of Geolocation via POST with one prohibited parameter
        @SuppressWarnings("unused")
        String eventGeofenceLatitude = null;
        geolocationParams.add("EventGeofenceLatitude", eventGeofenceLatitude = "45.426280"); // "EventGeofenceLatitude"
        // applicable only for Notification
        // type of Geolocation
        // HTTP POST Geolocation creation with given parameters values
        try {
            JsonObject prohibitedParamGeolocationJson = RestcommGeolocationsTool.getInstance().createImmediateGeolocation(
                deploymentUrl.toString(), adminAccountSid, adminUsername, adminAuthToken, geolocationParams);
            rejectedGeolocationSid = new Sid(prohibitedParamGeolocationJson.get("sid").getAsString());
            JsonObject rejectedGeolocationJson = RestcommGeolocationsTool.getInstance().getImmediateGeolocation(
                deploymentUrl.toString(), adminUsername, adminAuthToken, adminAccountSid,
                rejectedGeolocationSid.toString());
            assertTrue(rejectedGeolocationJson == null);
        } catch (Exception exception) {
            // Checking Test asserts via HTTP GET (no record found as POST is rejected)
            assertTrue(rejectedGeolocationSid == null);
            logger.info("Exception during HTTP POST: " + exception.getMessage());
        }

    }

    @Test
    public void testUpdateImmediateGeolocation()
        throws ParseException, IllegalArgumentException, ClientProtocolException, IOException {

        String msisdn = "5989738292";

        //This is for POST requests
        stubFor(post(urlPathEqualTo("/restcomm/gmlc/rest"))
            .withQueryParam("msisdn", equalTo(msisdn))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "text/xml")
                .withBody(gmlcResponse)));

        //This is for GET requests - REMOVE if not needed
        stubFor(get(urlPathEqualTo("/restcomm/gmlc/rest"))
            .withQueryParam("msisdn", equalTo(msisdn))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "text/xml")
                .withBody(gmlcResponse)));

        // Define Geolocation attributes
        String deviceIdentifier, responseStatus, cellId, locationAreaCode, mobileCountryCode, mobileNetworkCode,
            networkEntityAddress, ageOfLocationInfo, deviceLatitude, deviceLongitude, accuracy, internetAddress,
            physicalAddress, formattedAddress, locationTimestamp, geolocationPositioningType, lastGeolocationResponse;

        // Create Immediate type of Geolocation via POST
        // Parameter values Assignment
        MultivaluedMap<String, String> geolocationParams = new MultivaluedMapImpl();
        geolocationParams.add("DeviceIdentifier", deviceIdentifier = msisdn);
        geolocationParams.add("StatusCallback", "http://192.1.0.19:8080/ACae6e420f425248d6a26948c17a9e2acf");
        // HTTP POST Geolocation creation with given parameters values and those returned via GMLC stub
        JsonObject geolocationJson = RestcommGeolocationsTool.getInstance().createImmediateGeolocation(deploymentUrl.toString(),
            adminAccountSid, adminUsername, adminAuthToken, geolocationParams);
        Sid geolocationSid = new Sid(geolocationJson.get("sid").getAsString());

        // Define new values to the application attributes (POST test)
        MultivaluedMap<String, String> geolocationParamsUpdate = new MultivaluedMapImpl();
        geolocationParamsUpdate.add("DeviceIdentifier", deviceIdentifier = msisdn);
        geolocationParamsUpdate.add("DesiredAccuracy", "Low");
        geolocationParamsUpdate.add("StatusCallback", "http://192.1.0.19:8080/ACae6e420f425248d6a26948c17a9e2acf");
        geolocationParamsUpdate.add("ResponseStatus", responseStatus = "successfull");
        geolocationParamsUpdate.add("CellId", cellId = "12345");
        geolocationParamsUpdate.add("LocationAreaCode", locationAreaCode = "321");
        geolocationParamsUpdate.add("MobileCountryCode", mobileCountryCode = "749");
        geolocationParamsUpdate.add("MobileNetworkCode", mobileNetworkCode = "01");
        geolocationParamsUpdate.add("NetworkEntityAddress", networkEntityAddress = "5980042343201");
        geolocationParamsUpdate.add("LocationAge", ageOfLocationInfo = "0");
        geolocationParamsUpdate.add("DeviceLatitude", deviceLatitude = "-34.908134");
        geolocationParamsUpdate.add("DeviceLongitude", deviceLongitude = "-55.908134");
        geolocationParamsUpdate.add("Accuracy", accuracy = "75");
        geolocationParamsUpdate.add("InternetAddress", internetAddress = "2001:0:9d38:6ab8:30a5:1c9d:58c6:5898");
        geolocationParamsUpdate.add("PhysicalAddress", physicalAddress = "D8-97-BA-19-02-D8");
        geolocationParamsUpdate.add("FormattedAddress", formattedAddress = "Avenida Brasil 2681, 11500, Montevideo, Uruguay");
        geolocationParamsUpdate.add("LocationTimestamp", locationTimestamp = "2016-04-17T20:28:40.690-03:00");
        geolocationParamsUpdate.add("Radius", "200");
        geolocationParamsUpdate.add("GeolocationPositioningType", geolocationPositioningType = "Network");
        geolocationParamsUpdate.add("LastGeolocationResponse", lastGeolocationResponse = "false");
        geolocationParamsUpdate.add("Cause", "Not API Compliant");
        // Update Geolocation via POST
        RestcommGeolocationsTool.getInstance().updateImmediateGeolocation(deploymentUrl.toString(), adminUsername,
            adminAuthToken, adminAccountSid, geolocationSid.toString(), geolocationParamsUpdate, false);

        // Test asserts via GET to a single Geolocation
        geolocationJson = RestcommGeolocationsTool.getInstance().getImmediateGeolocation(deploymentUrl.toString(),
            adminUsername, adminAuthToken, adminAccountSid, geolocationSid.toString());

        DateTimeFormatter dtf = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
        DateTime dateTime = dtf.parseDateTime(locationTimestamp);
        SimpleDateFormat df = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss Z", Locale.US);
        locationTimestamp = df.format(dateTime.toDate());
        assertTrue(df.parse(geolocationJson.get("date_created").getAsString()) != null);
        assertTrue(df.parse(geolocationJson.get("date_updated").getAsString()) != null);
        assertTrue(df.parse(geolocationJson.get("date_executed").getAsString()) != null);
        assertTrue(geolocationJson.get("account_sid").getAsString().equals(adminAccountSid));
        assertTrue(geolocationJson.get("device_identifier").getAsString().equals(deviceIdentifier));
        assertTrue(geolocationJson.get("geolocation_type").getAsString().equalsIgnoreCase(ImmediateGT));
        assertTrue(geolocationJson.get("response_status").getAsString().equals(responseStatus));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("cell_id").getAsString().equals(cellId));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("location_area_code").getAsString()
            .equals(locationAreaCode));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("mobile_country_code").getAsString()
            .equals(mobileCountryCode));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("mobile_network_code").getAsString()
            .equals(mobileNetworkCode));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("network_entity_address").getAsString()
            .equals(networkEntityAddress));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("location_age").getAsString()
            .equals(ageOfLocationInfo));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("device_latitude").getAsString()
            .equals(deviceLatitude));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("device_longitude").getAsString()
            .equals(deviceLongitude));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("accuracy").getAsString().equals(accuracy));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("internet_address").getAsString()
            .equals(internetAddress));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("physical_address").getAsString()
            .equals(physicalAddress));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("formatted_address").getAsString()
            .equals(formattedAddress));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("location_timestamp").getAsString()
            .equals(locationTimestamp));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("event_geofence_latitude") == null);
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("event_geofence_longitude") == null);
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("radius") == null);
        assertTrue(geolocationJson.get("geolocation_positioning_type").getAsString().equals(geolocationPositioningType));
        assertTrue(geolocationJson.get("last_geolocation_response").getAsString().equals(lastGeolocationResponse));
        assertTrue(geolocationJson.get("cause") == null);
        assertTrue(geolocationJson.get("api_version").getAsString().equals("2012-04-24"));

        // Define new values for the Geolocation attributes (PUT test)
        geolocationParamsUpdate = new MultivaluedMapImpl();
        geolocationParamsUpdate.add("DesiredAccuracy", "Average");
        geolocationParamsUpdate.add("StatusCallback", "http://192.1.2.19:8080/ACae6e420f425248d6a26948c17a9e2acf");
        geolocationParamsUpdate.add("ResponseStatus", responseStatus = "successfull");
        geolocationParamsUpdate.add("CellId", cellId = "55777");
        geolocationParamsUpdate.add("LocationAreaCode", locationAreaCode = "707");
        geolocationParamsUpdate.add("MobileCountryCode", mobileCountryCode = "748");
        geolocationParamsUpdate.add("MobileNetworkCode", mobileNetworkCode = "03");
        geolocationParamsUpdate.add("NetworkEntityAddress", networkEntityAddress = "598003245701");
        geolocationParamsUpdate.add("LocationAge", ageOfLocationInfo = "1");
        geolocationParamsUpdate.add("DeviceLatitude", deviceLatitude = "43 38 19.39");
        geolocationParamsUpdate.add("DeviceLongitude", deviceLongitude = "-170 21 10.02");
        geolocationParamsUpdate.add("Accuracy", accuracy = "25");
        geolocationParamsUpdate.add("InternetAddress", internetAddress = "180.7.2.141");
        geolocationParamsUpdate.add("PhysicalAddress", physicalAddress = "A8-77-CA-29-32-D1");
        geolocationParamsUpdate.add("FormattedAddress", formattedAddress = "Avenida Italia 2681, 11100, Montevideo, Uruguay");
        geolocationParamsUpdate.add("LocationTimestamp", locationTimestamp = "2016-04-17T20:31:27.790-08:00");
        geolocationParamsUpdate.add("Radius", "100");
        geolocationParamsUpdate.add("GeolocationPositioningType", geolocationPositioningType = "Network");
        geolocationParamsUpdate.add("LastGeolocationResponse", lastGeolocationResponse = "true");
        geolocationParamsUpdate.add("Cause", "API Not Compliant");
        // Update Geolocation via PUT
        RestcommGeolocationsTool.getInstance().updateImmediateGeolocation(deploymentUrl.toString(), adminUsername,
            adminAuthToken, adminAccountSid, geolocationSid.toString(), geolocationParamsUpdate, true);

        // Test asserts via GET to a single Geolocation
        geolocationJson = RestcommGeolocationsTool.getInstance().getImmediateGeolocation(deploymentUrl.toString(),
            adminUsername, adminAuthToken, adminAccountSid, geolocationSid.toString());

        dateTime = dtf.parseDateTime(locationTimestamp);
        locationTimestamp = df.format(dateTime.toDate());
        assertTrue(df.parse(geolocationJson.get("date_created").getAsString()) != null);
        assertTrue(df.parse(geolocationJson.get("date_updated").getAsString()) != null);
        assertTrue(df.parse(geolocationJson.get("date_executed").getAsString()) != null);
        assertTrue(geolocationJson.get("account_sid").getAsString().equals(adminAccountSid));
        assertTrue(geolocationJson.get("device_identifier").getAsString().equals(deviceIdentifier));
        assertTrue(geolocationJson.get("geolocation_type").getAsString().equals(ImmediateGT));
        assertTrue(geolocationJson.get("response_status").getAsString().equals(responseStatus));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("cell_id").getAsString().equals(cellId));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("location_area_code").getAsString()
            .equals(locationAreaCode));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("mobile_country_code").getAsString()
            .equals(mobileCountryCode));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("mobile_network_code").getAsString()
            .equals(mobileNetworkCode));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("network_entity_address").getAsString()
            .equals(networkEntityAddress));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("location_age").getAsString()
            .equals(ageOfLocationInfo));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("device_latitude").getAsString()
            .equals(deviceLatitude));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("device_longitude").getAsString()
            .equals(deviceLongitude));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("accuracy").getAsString().equals(accuracy));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("internet_address").getAsString()
            .equals(internetAddress));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("physical_address").getAsString()
            .equals(physicalAddress));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("formatted_address").getAsString()
            .equals(formattedAddress));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("location_timestamp").getAsString()
            .equals(locationTimestamp));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("event_geofence_latitude") == null);
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("event_geofence_longitude") == null);
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("radius") == null);
        assertTrue(geolocationJson.get("geolocation_positioning_type").getAsString().equals(geolocationPositioningType));
        assertTrue(geolocationJson.get("last_geolocation_response").getAsString().equals(lastGeolocationResponse));
        assertTrue(geolocationJson.get("cause") == null);
        assertTrue(geolocationJson.get("api_version").getAsString().equals("2012-04-24"));

        // Remove created & updated Geolocation via HTTP DELETE
        RestcommGeolocationsTool.getInstance().deleteImmediateGeolocation(deploymentUrl.toString(), adminUsername,
            adminAuthToken, adminAccountSid, geolocationSid.toString());

    }

    @Test
    @Category(FeatureAltTests.class)
    public void testNotApiCompliantUpdateImmediateGeolocation()
        throws ParseException, IllegalArgumentException, ClientProtocolException, IOException {

        String msisdn = "5989738292";

        //This is for POST requests
        stubFor(post(urlPathEqualTo("/restcomm/gmlc/rest"))
            .withQueryParam("msisdn", equalTo(msisdn))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "text/xml")
                .withBody(gmlcResponse)));

        //This is for GET requests - REMOVE if not needed
        stubFor(get(urlPathEqualTo("/restcomm/gmlc/rest"))
            .withQueryParam("msisdn", equalTo(msisdn))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "text/xml")
                .withBody(gmlcResponse)));

        // Define Geolocation attributes
        String deviceIdentifier, responseStatus, cellId, locationAreaCode, mobileCountryCode, mobileNetworkCode,
            networkEntityAddress, ageOfLocationInfo, deviceLatitude, deviceLongitude, accuracy, internetAddress,
            physicalAddress, formattedAddress, locationTimestamp = "2016-04-17T20:28:40.690-03:00", geolocationPositioningType, lastGeolocationResponse;

        // Create Immediate type of Geolocation via POST
        // Parameter values Assignment
        MultivaluedMap<String, String> geolocationParams = new MultivaluedMapImpl();
        geolocationParams.add("DeviceIdentifier", deviceIdentifier = msisdn);
        geolocationParams.add("StatusCallback", "http://192.1.0.19:8080/ACae6e420f425248d6a26948c17a9e2acf");
        // HTTP POST Geolocation creation with given parameters values and those returned via GMLC stub
        JsonObject geolocationJson = RestcommGeolocationsTool.getInstance().createImmediateGeolocation(deploymentUrl.toString(),
            adminAccountSid, adminUsername, adminAuthToken, geolocationParams);
        Sid geolocationSid = new Sid(geolocationJson.get("sid").getAsString());

        DateTimeFormatter dtf = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
        DateTime dateTime = dtf.parseDateTime(locationTimestamp);
        SimpleDateFormat df = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss Z", Locale.US);
        locationTimestamp = df.format(dateTime.toDate());

        // Define malformed values for the Geolocation attributes (PUT test to fail)
        MultivaluedMap<String, String> geolocationParamsUpdate = new MultivaluedMapImpl();
        geolocationParamsUpdate.add("DeviceLatitude", deviceLatitude = "North 72.908134"); // WGS84 not compliant
        geolocationParamsUpdate.add("DeviceLongitude", deviceLongitude = "170.908134");
        // Update failed Geolocation via PUT
        RestcommGeolocationsTool.getInstance().updateImmediateGeolocation(deploymentUrl.toString(), adminUsername,
            adminAuthToken, adminAccountSid, geolocationSid.toString(), geolocationParamsUpdate, true);
        // Test asserts via GET to a single Geolocation
        geolocationJson = RestcommGeolocationsTool.getInstance().getImmediateGeolocation(deploymentUrl.toString(),
            adminUsername, adminAuthToken, adminAccountSid, geolocationSid.toString());
        assertTrue(df.parse(geolocationJson.get("date_created").getAsString()) != null);
        assertTrue(df.parse(geolocationJson.get("date_updated").getAsString()) != null);
        assertTrue(df.parse(geolocationJson.get("date_executed").getAsString()) != null);
        assertTrue(geolocationJson.get("account_sid").getAsString().equals(adminAccountSid));
        assertTrue(geolocationJson.get("source") == null);
        assertTrue(geolocationJson.get("device_identifier").getAsString().equals(deviceIdentifier));
        assertTrue(geolocationJson.get("geolocation_type").getAsString().equals(ImmediateGT));
        assertTrue(geolocationJson.get("response_status").getAsString().equalsIgnoreCase("failed"));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("cell_id") == null);
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("location_area_code") == null);
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("mobile_country_code") == null);
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("mobile_network_code") == null);
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("network_entity_address") == null);
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("location_age") == null);
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("device_latitude") == null);
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("device_longitude") == null);
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("accuracy") == null);
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("internet_address") == null);
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("physical_address") == null);
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("formatted_address") == null);
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("location_timestamp") == null);
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("event_geofence_latitude") == null);
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("event_geofence_longitude") == null);
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("radius") == null);
        assertTrue(geolocationJson.get("geolocation_positioning_type") == null);
        assertTrue(geolocationJson.get("last_geolocation_response") == null);
        assertTrue(geolocationJson.get("cause") != null);
        assertTrue(geolocationJson.get("api_version").getAsString().equals("2012-04-24"));

        // Define new values for the Geolocation attributes (PUT test)
        geolocationParamsUpdate = new MultivaluedMapImpl();
        geolocationParamsUpdate.add("DesiredAccuracy", "High");
        geolocationParamsUpdate.add("StatusCallback", "http://192.1.2.19:8080/ACae6e420f425248d6a26948c17a9e2acf");
        geolocationParamsUpdate.add("ResponseStatus", responseStatus = "successfull");
        geolocationParamsUpdate.add("CellId", cellId = "34580");
        geolocationParamsUpdate.add("LocationAreaCode", locationAreaCode = "709");
        geolocationParamsUpdate.add("MobileCountryCode", mobileCountryCode = "748");
        geolocationParamsUpdate.add("MobileNetworkCode", mobileNetworkCode = "01");
        geolocationParamsUpdate.add("NetworkEntityAddress", networkEntityAddress = "598003245702");
        geolocationParamsUpdate.add("LocationAge", ageOfLocationInfo = "1");
        geolocationParamsUpdate.add("DeviceLatitude", deviceLatitude = "S43\u00b038'19.39''");
        geolocationParamsUpdate.add("DeviceLongitude", deviceLongitude = "E169\u00b028'49.07''");
        geolocationParamsUpdate.add("Accuracy", accuracy = "25");
        geolocationParamsUpdate.add("InternetAddress", internetAddress = "180.7.2.141");
        geolocationParamsUpdate.add("PhysicalAddress", physicalAddress = "A8-77-CA-29-32-D1");
        geolocationParamsUpdate.add("FormattedAddress", formattedAddress = "Avenida Italia 2681, 11100, Montevideo, Uruguay");
        geolocationParamsUpdate.add("LocationTimestamp", locationTimestamp = "2016-04-17T20:31:28.388-05:00");
        geolocationParamsUpdate.add("Radius", "5");
        geolocationParamsUpdate.add("GeolocationPositioningType", geolocationPositioningType = "GPS");
        geolocationParamsUpdate.add("LastGeolocationResponse", lastGeolocationResponse = "true");
        geolocationParamsUpdate.add("Cause", "API Not Compliant");
        // Update Geolocation via PUT
        // previous failed location is composed again with new proper geolocation data values
        RestcommGeolocationsTool.getInstance().updateImmediateGeolocation(deploymentUrl.toString(), adminUsername,
            adminAuthToken, adminAccountSid, geolocationSid.toString(), geolocationParamsUpdate, true);

        // Test asserts via GET to a single Geolocation
        geolocationJson = RestcommGeolocationsTool.getInstance().getImmediateGeolocation(deploymentUrl.toString(),
            adminUsername, adminAuthToken, adminAccountSid, geolocationSid.toString());

        dateTime = dtf.parseDateTime(locationTimestamp);
        locationTimestamp = df.format(dateTime.toDate());
        assertTrue(df.parse(geolocationJson.get("date_created").getAsString()) != null);
        assertTrue(df.parse(geolocationJson.get("date_updated").getAsString()) != null);
        assertTrue(df.parse(geolocationJson.get("date_executed").getAsString()) != null);
        assertTrue(geolocationJson.get("account_sid").getAsString().equals(adminAccountSid));
        assertTrue(geolocationJson.get("device_identifier").getAsString().equals(deviceIdentifier));
        assertTrue(geolocationJson.get("geolocation_type").getAsString().equals(ImmediateGT));
        assertTrue(geolocationJson.get("response_status").getAsString().equals(responseStatus));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("cell_id").getAsString().equals(cellId));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("location_area_code").getAsString()
            .equals(locationAreaCode));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("mobile_country_code").getAsString()
            .equals(mobileCountryCode));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("mobile_network_code").getAsString()
            .equals(mobileNetworkCode));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("network_entity_address").getAsString()
            .equals(networkEntityAddress));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("location_age").getAsString()
            .equals(ageOfLocationInfo));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("device_latitude").getAsString()
            .equals(deviceLatitude));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("device_longitude").getAsString()
            .equals(deviceLongitude));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("accuracy").getAsString().equals(accuracy));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("internet_address").getAsString()
            .equals(internetAddress));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("physical_address").getAsString()
            .equals(physicalAddress));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("formatted_address").getAsString()
            .equals(formattedAddress));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("location_timestamp").getAsString()
            .equals(locationTimestamp));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("event_geofence_latitude") == null);
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("event_geofence_longitude") == null);
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("radius") == null);
        assertTrue(geolocationJson.get("geolocation_positioning_type").getAsString().equals(geolocationPositioningType));
        assertTrue(geolocationJson.get("last_geolocation_response").getAsString().equals(lastGeolocationResponse));
        assertTrue(geolocationJson.get("cause") == null);
        assertTrue(geolocationJson.get("api_version").getAsString().equals("2012-04-24"));

        // Remove created & updated Geolocation via HTTP DELETE
        RestcommGeolocationsTool.getInstance().deleteImmediateGeolocation(deploymentUrl.toString(), adminUsername,
            adminAuthToken, adminAccountSid, geolocationSid.toString());

    }

    @Test
    public void testDeleteImmediateGeolocation() throws IllegalArgumentException, ClientProtocolException, IOException {

        String msisdn = "5989738292";

        //This is for POST requests
        stubFor(post(urlPathEqualTo("/restcomm/gmlc/rest"))
            .withQueryParam("msisdn", equalTo(msisdn))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "text/xml")
                .withBody(gmlcResponse)));

        //This is for GET requests - REMOVE if not needed
        stubFor(get(urlPathEqualTo("/restcomm/gmlc/rest"))
            .withQueryParam("msisdn", equalTo(msisdn))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "text/xml")
                .withBody(gmlcResponse)));

        // Define Immediate Geolocation attributes for this method
        String deviceIdentifier;

        // Create Immediate type of Geolocation via POST
        // Parameter values Assignment
        MultivaluedMap<String, String> geolocationParams = new MultivaluedMapImpl();
        geolocationParams.add("DeviceIdentifier", deviceIdentifier = msisdn);
        geolocationParams.add("DesiredAccuracy", "High");
        geolocationParams.add("StatusCallback", "http://192.1.0.19:8080/ACae6e420f425248d6a26948c17a9e2acf");
        geolocationParams.add("ResponseStatus", "successfull");
        geolocationParams.add("Accuracy", "5");
        geolocationParams.add("InternetAddress", "194.87.1.127");
        geolocationParams.add("PhysicalAddress", "D8-97-BA-19-02-D8");
        geolocationParams.add("LocationTimestamp", "2016-04-15");
        geolocationParams.add("Radius", "200");
        geolocationParams.add("GeolocationPositioningType", "GPS");
        geolocationParams.add("LastGeolocationResponse", "true");
        geolocationParams.add("Cause", "Not API Compliant");
        // HTTP POST Geolocation creation with given parameters values and those returned via GMLC stub
        JsonObject geolocationJson = RestcommGeolocationsTool.getInstance().createImmediateGeolocation(deploymentUrl.toString(),
            adminAccountSid, adminUsername, adminAuthToken, geolocationParams);
        Sid geolocationSid = new Sid(geolocationJson.get("sid").getAsString());

        // Remove created Geolocation via HTTP DELETE
        RestcommGeolocationsTool.getInstance().deleteImmediateGeolocation(deploymentUrl.toString(), adminUsername,
            adminAuthToken, adminAccountSid, geolocationSid.toString());
        // Remove checking Test asserts via HTTP GET
        geolocationJson = RestcommGeolocationsTool.getInstance().getImmediateGeolocation(deploymentUrl.toString(),
            adminUsername, adminAuthToken, adminAccountSid, geolocationSid.toString());

        assertTrue(geolocationJson == null);
    }

    @Test
    public void testCreateAndGetNotificationGeolocation()
        throws ParseException, IllegalArgumentException, ClientProtocolException, IOException {

        String msisdn = "5989738292";

        //This is for POST requests
        stubFor(post(urlPathEqualTo("/restcomm/gmlc/rest"))
            .withQueryParam("msisdn", equalTo(msisdn))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "text/xml")
                .withBody(gmlcResponse)));

        //This is for GET requests - REMOVE if not needed
        stubFor(get(urlPathEqualTo("/restcomm/gmlc/rest"))
            .withQueryParam("msisdn", equalTo(msisdn))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "text/xml")
                .withBody(gmlcResponse)));

        // Define Geolocation attributes for this test method
        String deviceIdentifier, eventGeofenceLatitude, eventGeofenceLongitude;

        // Test create Notification type of Geolocation via POST (only mandatory parameters)
        // Parameter values Assignment
        MultivaluedMap<String, String> geolocationParams = new MultivaluedMapImpl();
        geolocationParams.add("DeviceIdentifier", deviceIdentifier = msisdn);
        geolocationParams.add("EventGeofenceLatitude", eventGeofenceLatitude = "45.426280");
        geolocationParams.add("EventGeofenceLongitude", eventGeofenceLongitude = "-80.566560");
        geolocationParams.add("GeofenceRange", "300");
        geolocationParams.add("GeofenceEvent", "in");
        geolocationParams.add("StatusCallback", "http://192.1.0.19:8080/ACae6e420f425248d6a26948c17a9e2acf");
        // HTTP POST Geolocation creation with given parameters values and those returned via GMLC stub
        JsonObject geolocationJson = RestcommGeolocationsTool.getInstance().createNotificationGeolocation(
            deploymentUrl.toString(), adminAccountSid, adminUsername, adminAuthToken, geolocationParams);
        Sid geolocationSid = new Sid(geolocationJson.get("sid").getAsString());

        // Test asserts via GET to a single Geolocation
        geolocationJson = RestcommGeolocationsTool.getInstance().getNotificationGeolocation(deploymentUrl.toString(),
            adminUsername, adminAuthToken, adminAccountSid, geolocationSid.toString());

        SimpleDateFormat df = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss Z", Locale.US);
        assertTrue(df.parse(geolocationJson.get("date_created").getAsString()) != null);
        assertTrue(df.parse(geolocationJson.get("date_updated").getAsString()) != null);
        assertTrue(df.parse(geolocationJson.get("date_executed").getAsString()) != null);
        assertTrue(geolocationJson.get("account_sid").getAsString().equals(adminAccountSid));
        assertTrue(geolocationJson.get("device_identifier").getAsString().equals(deviceIdentifier));
        assertTrue(geolocationJson.get("geolocation_type").getAsString().equals(NotificationGT));
        assertTrue(geolocationJson.get("response_status") == null);
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("cell_id").getAsString().equals("521"));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("location_area_code").getAsString().equals("320"));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("mobile_country_code").getAsString().equals("598"));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("mobile_network_code").getAsString().equals("1"));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("network_entity_address").getAsString().equals("598001"));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("location_age").getAsString().equals("0"));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("device_latitude").getAsString().equals("35.349781"));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("device_longitude").getAsString().equals("87.754320"));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("accuracy") == null);
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("internet_address") == null);
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("physical_address") == null);
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("formatted_address").getAsString().equals("Avenue2501"));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("location_timestamp") == null);
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("event_geofence_latitude").getAsString()
            .equals(eventGeofenceLatitude));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("event_geofence_longitude").getAsString()
            .equals(eventGeofenceLongitude));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("radius") == null);
        assertTrue(geolocationJson.get("geolocation_positioning_type") == null);
        assertTrue(geolocationJson.get("last_geolocation_response") == null);
        assertTrue(geolocationJson.get("cause") == null);
        assertTrue(geolocationJson.get("api_version").getAsString().equals("2012-04-24"));

        // Test asserts via GET to a Geolocation list
        JsonArray notificationGeolocationsListJson = RestcommGeolocationsTool.getInstance()
            .getGeolocations(deploymentUrl.toString(), adminUsername, adminAuthToken, adminAccountSid);
        geolocationJson = notificationGeolocationsListJson.get(0).getAsJsonObject();
        assertTrue(df.parse(geolocationJson.get("date_created").getAsString()) != null);
        assertTrue(df.parse(geolocationJson.get("date_updated").getAsString()) != null);
        assertTrue(df.parse(geolocationJson.get("date_executed").getAsString()) != null);
        assertTrue(geolocationJson.get("account_sid").getAsString().equals(adminAccountSid));
        assertTrue(geolocationJson.get("source") == null);
        assertTrue(geolocationJson.get("device_identifier").getAsString().equals(deviceIdentifier));
        assertTrue(geolocationJson.get("geolocation_type").getAsString().equals(NotificationGT));
        assertTrue(geolocationJson.get("response_status") == null);
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("cell_id").getAsString().equals("521"));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("location_area_code").getAsString().equals("320"));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("mobile_country_code").getAsString().equals("598"));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("mobile_network_code").getAsString().equals("1"));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("network_entity_address").getAsString().equals("598001"));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("location_age").getAsString().equals("0"));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("device_latitude").getAsString().equals("35.349781"));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("device_longitude").getAsString().equals("87.754320"));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("accuracy") == null);
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("internet_address") == null);
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("physical_address") == null);
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("formatted_address").getAsString().equals("Avenue2501"));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("location_timestamp") == null);
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("event_geofence_latitude").getAsString()
            .equals(eventGeofenceLatitude));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("event_geofence_longitude").getAsString()
            .equals(eventGeofenceLongitude));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("radius") == null);
        assertTrue(geolocationJson.get("geolocation_positioning_type") == null);
        assertTrue(geolocationJson.get("last_geolocation_response") == null);
        assertTrue(geolocationJson.get("cause") == null);
        assertTrue(geolocationJson.get("api_version").getAsString().equals("2012-04-24"));

        // Remove created Geolocation via HTTP DELETE
        RestcommGeolocationsTool.getInstance().deleteNotificationGeolocation(deploymentUrl.toString(), adminUsername,
            adminAuthToken, adminAccountSid, geolocationSid.toString());

    }

    @Test
    @Category(FeatureAltTests.class)
    public void testCreateNotApiCompliantNotificationGeolocation()
        throws ParseException, IllegalArgumentException, ClientProtocolException, IOException {

        String msisdn = "5989738292";

        //This is for POST requests
        stubFor(post(urlPathEqualTo("/restcomm/gmlc/rest"))
            .withQueryParam("msisdn", equalTo(msisdn))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "text/xml")
                .withBody(gmlcResponse)));

        //This is for GET requests - REMOVE if not needed
        stubFor(get(urlPathEqualTo("/restcomm/gmlc/rest"))
            .withQueryParam("msisdn", equalTo(msisdn))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "text/xml")
                .withBody(gmlcResponse)));

        // Define Geolocation attributes for this test method
        String deviceIdentifier, eventGeofenceLatitude, eventGeofenceLongitude;

        // Test create Notification type of Geolocation via POST with one missing mandatory parameter
        // Parameter values Assignment, GeofenceEvent missing
        MultivaluedMap<String, String> geolocationNewParams = new MultivaluedMapImpl();
        geolocationNewParams.add("DeviceIdentifier", deviceIdentifier = msisdn);
        geolocationNewParams.add("EventGeofenceLatitude", eventGeofenceLatitude = "-43.426280");
        geolocationNewParams.add("EventGeofenceLongitude", eventGeofenceLongitude = "170.566560");
        geolocationNewParams.add("GeofenceRange", "200");
        geolocationNewParams.add("StatusCallback", "http://192.1.0.19:8080/ACae6e420f425248d6a26948c17a9e2acf");
        Sid rejectedGeolocationSid = null;
        // HTTP POST Geolocation creation with given parameters values
        try {
            JsonObject missingParamGeolocationJson = RestcommGeolocationsTool.getInstance().createNotificationGeolocation(
                deploymentUrl.toString(), adminAccountSid, adminUsername, adminAuthToken, geolocationNewParams);
            rejectedGeolocationSid = new Sid(missingParamGeolocationJson.get("sid").getAsString());
            JsonObject rejectedGeolocationJson = RestcommGeolocationsTool.getInstance().getNotificationGeolocation(
                deploymentUrl.toString(), adminUsername, adminAuthToken, adminAccountSid,
                rejectedGeolocationSid.toString());
            assertTrue(rejectedGeolocationJson == null);
        } catch (Exception exception) {
            // Checking Test asserts via HTTP GET (no record found as POST returned a response status of 400 Bad Request)
            assertTrue(rejectedGeolocationSid == null);
            logger.info("Exception during HTTP POST: " + exception.getMessage());
        }

    }

    @Test
    public void testUpdateNotificationGeolocation()
        throws ParseException, IllegalArgumentException, ClientProtocolException, IOException {

        String msisdn = "5989738292";

        //This is for POST requests
        stubFor(post(urlPathEqualTo("/restcomm/gmlc/rest"))
            .withQueryParam("msisdn", equalTo(msisdn))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "text/xml")
                .withBody(gmlcResponse)));

        //This is for GET requests - REMOVE if not needed
        stubFor(get(urlPathEqualTo("/restcomm/gmlc/rest"))
            .withQueryParam("msisdn", equalTo(msisdn))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "text/xml")
                .withBody(gmlcResponse)));

        // Define Notification Geolocation attributes
        String deviceIdentifier, responseStatus, cellId, locationAreaCode, mobileCountryCode, mobileNetworkCode,
            networkEntityAddress, ageOfLocationInfo, deviceLatitude, deviceLongitude, accuracy, internetAddress,
            physicalAddress, formattedAddress, locationTimestamp, eventGeofenceLatitude, eventGeofenceLongitude, radius,
            geolocationPositioningType, lastGeolocationResponse;

        // Create Notification type of Geolocation via POST
        // Parameter values Assignment
        MultivaluedMap<String, String> geolocationParams = new MultivaluedMapImpl();
        geolocationParams.add("DeviceIdentifier", deviceIdentifier = msisdn);
        geolocationParams.add("EventGeofenceLatitude", "-33.426280");
        geolocationParams.add("EventGeofenceLongitude", "-70.566560");
        geolocationParams.add("GeofenceRange", "300");
        geolocationParams.add("GeofenceEvent", "in");
        geolocationParams.add("StatusCallback", "http://192.1.0.19:8080/ACae6e420f425248d6a26948c17a9e2acf");
        // HTTP POST Geolocation creation with given parameters values and those returned via GMLC stub
        JsonObject geolocationJson = RestcommGeolocationsTool.getInstance().createNotificationGeolocation(
            deploymentUrl.toString(), adminAccountSid, adminUsername, adminAuthToken, geolocationParams);
        Sid geolocationSid = new Sid(geolocationJson.get("sid").getAsString());

        // Define new values to the application attributes (POST test)
        MultivaluedMap<String, String> geolocationParamsUpdate = new MultivaluedMapImpl();
        geolocationParamsUpdate.add("DeviceIdentifier", deviceIdentifier = msisdn);
        geolocationParamsUpdate.add("EventGeofenceLatitude", eventGeofenceLatitude = "34\u00b038'19.39''N");
        geolocationParamsUpdate.add("EventGeofenceLongitude", eventGeofenceLongitude = "55\u00b028'59.33''E");
        geolocationParamsUpdate.add("GeofenceRange", "200");
        geolocationParamsUpdate.add("GeofenceEvent", "in-out");
        geolocationParamsUpdate.add("DesiredAccuracy", "High");
        geolocationParamsUpdate.add("StatusCallback", "http://192.1.1.19:8080/ACae6e420f425248d6a26948c17a9e2acf");
        geolocationParamsUpdate.add("ResponseStatus", responseStatus = "successfull");
        geolocationParamsUpdate.add("CellId", cellId = "12345");
        geolocationParamsUpdate.add("LocationAreaCode", locationAreaCode = "321");
        geolocationParamsUpdate.add("MobileCountryCode", mobileCountryCode = "749");
        geolocationParamsUpdate.add("MobileNetworkCode", mobileNetworkCode = "01");
        geolocationParamsUpdate.add("NetworkEntityAddress", networkEntityAddress = "5980042343201");
        geolocationParamsUpdate.add("LocationAge", ageOfLocationInfo = "0");
        geolocationParamsUpdate.add("DeviceLatitude", deviceLatitude = "34.908134");
        geolocationParamsUpdate.add("DeviceLongitude", deviceLongitude = "-55.087134");
        geolocationParamsUpdate.add("Accuracy", accuracy = "75");
        geolocationParamsUpdate.add("InternetAddress", internetAddress = "2001:0:9d38:6ab8:30a5:1c9d:58c6:5898");
        geolocationParamsUpdate.add("PhysicalAddress", physicalAddress = "D8-97-BA-19-02-D8");
        geolocationParamsUpdate.add("FormattedAddress", formattedAddress = "Avenida Brasil 2681, 11500, Montevideo, Uruguay");
        geolocationParamsUpdate.add("LocationTimestamp", locationTimestamp = "2016-04-17T20:28:40.690-03:00");
        geolocationParamsUpdate.add("Radius", radius = "200");
        geolocationParamsUpdate.add("GeolocationPositioningType", geolocationPositioningType = "Network");
        geolocationParamsUpdate.add("LastGeolocationResponse", lastGeolocationResponse = "false");
        geolocationParamsUpdate.add("Cause", "Not API Compliant");
        // Update Geolocation via POST
        RestcommGeolocationsTool.getInstance().updateNotificationGeolocation(deploymentUrl.toString(), adminUsername,
            adminAuthToken, adminAccountSid, geolocationSid.toString(), geolocationParamsUpdate, false);

        // Test asserts via GET to a single Geolocation
        geolocationJson = RestcommGeolocationsTool.getInstance().getNotificationGeolocation(deploymentUrl.toString(),
            adminUsername, adminAuthToken, adminAccountSid, geolocationSid.toString());

        DateTimeFormatter dtf = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
        DateTime dateTime = dtf.parseDateTime(locationTimestamp);
        SimpleDateFormat df = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss Z", Locale.US);
        locationTimestamp = df.format(dateTime.toDate());
        assertTrue(df.parse(geolocationJson.get("date_created").getAsString()) != null);
        assertTrue(df.parse(geolocationJson.get("date_updated").getAsString()) != null);
        assertTrue(df.parse(geolocationJson.get("date_executed").getAsString()) != null);
        assertTrue(geolocationJson.get("account_sid").getAsString().equals(adminAccountSid));
        assertTrue(geolocationJson.get("device_identifier").getAsString().equals(deviceIdentifier));
        assertTrue(geolocationJson.get("geolocation_type").getAsString().equals(NotificationGT));
        assertTrue(geolocationJson.get("response_status").getAsString().equals(responseStatus));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("cell_id").getAsString().equals(cellId));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("location_area_code").getAsString()
            .equals(locationAreaCode));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("mobile_country_code").getAsString()
            .equals(mobileCountryCode));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("mobile_network_code").getAsString()
            .equals(mobileNetworkCode));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("network_entity_address").getAsString()
            .equals(networkEntityAddress));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("location_age").getAsString()
            .equals(ageOfLocationInfo));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("device_latitude").getAsString()
            .equals(deviceLatitude));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("device_longitude").getAsString()
            .equals(deviceLongitude));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("accuracy").getAsString().equals(accuracy));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("internet_address").getAsString()
            .equals(internetAddress));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("physical_address").getAsString()
            .equals(physicalAddress));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("formatted_address").getAsString()
            .equals(formattedAddress));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("location_timestamp").getAsString()
            .equals(locationTimestamp));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("event_geofence_latitude").getAsString()
            .equals(eventGeofenceLatitude));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("event_geofence_longitude").getAsString()
            .equals(eventGeofenceLongitude));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("radius").getAsString().equals(radius));
        assertTrue(geolocationJson.get("geolocation_positioning_type").getAsString().equals(geolocationPositioningType));
        assertTrue(geolocationJson.get("last_geolocation_response").getAsString().equals(lastGeolocationResponse));
        assertTrue(geolocationJson.get("cause") == null);
        assertTrue(geolocationJson.get("api_version").getAsString().equals("2012-04-24"));

        // Define new values for the Geolocation attributes (PUT test)
        geolocationParamsUpdate = new MultivaluedMapImpl();
        geolocationParamsUpdate.add("EventGeofenceLatitude", eventGeofenceLatitude = "N172 42 62.80");
        geolocationParamsUpdate.add("EventGeofenceLongitude", eventGeofenceLongitude = "W170 56 65.60");
        geolocationParamsUpdate.add("GeofenceRange", "50");
        geolocationParamsUpdate.add("GeofenceEvent", "in");
        geolocationParamsUpdate.add("DesiredAccuracy", "Average");
        geolocationParamsUpdate.add("StatusCallback", "http://192.1.2.19:8080/ACae6e420f425248d6a26948c17a9e2acf");
        geolocationParamsUpdate.add("ResponseStatus", responseStatus = "partially-successfull");
        geolocationParamsUpdate.add("CellId", cellId = "55777");
        geolocationParamsUpdate.add("LocationAreaCode", locationAreaCode = "707");
        geolocationParamsUpdate.add("MobileCountryCode", mobileCountryCode = "748");
        geolocationParamsUpdate.add("MobileNetworkCode", mobileNetworkCode = "03");
        geolocationParamsUpdate.add("NetworkEntityAddress", networkEntityAddress = "598003245701");
        geolocationParamsUpdate.add("LocationAge", ageOfLocationInfo = "1");
        geolocationParamsUpdate.add("DeviceLatitude", deviceLatitude = "172.908134");
        geolocationParamsUpdate.add("DeviceLongitude", deviceLongitude = "170.908134");
        geolocationParamsUpdate.add("Accuracy", accuracy = "25");
        geolocationParamsUpdate.add("InternetAddress", internetAddress = "180.7.2.141");
        geolocationParamsUpdate.add("PhysicalAddress", physicalAddress = "A8-77-CA-29-32-D1");
        geolocationParamsUpdate.add("FormattedAddress", formattedAddress = "Avenida Italia 2681, 11100, Montevideo, Uruguay");
        geolocationParamsUpdate.add("LocationTimestamp", locationTimestamp = "2016-04-17T20:28:42.771-03:00");
        geolocationParamsUpdate.add("Radius", radius = "100");
        geolocationParamsUpdate.add("GeolocationPositioningType", geolocationPositioningType = "GPS");
        geolocationParamsUpdate.add("LastGeolocationResponse", lastGeolocationResponse = "true");
        geolocationParamsUpdate.add("Cause", "API Not Compliant");
        // Update Geolocation via PUT
        RestcommGeolocationsTool.getInstance().updateNotificationGeolocation(deploymentUrl.toString(), adminUsername,
            adminAuthToken, adminAccountSid, geolocationSid.toString(), geolocationParamsUpdate, true);

        // Test asserts via GET to a single Geolocation
        geolocationJson = RestcommGeolocationsTool.getInstance().getNotificationGeolocation(deploymentUrl.toString(),
            adminUsername, adminAuthToken, adminAccountSid, geolocationSid.toString());

        dateTime = dtf.parseDateTime(locationTimestamp);
        locationTimestamp = df.format(dateTime.toDate());
        assertTrue(df.parse(geolocationJson.get("date_created").getAsString()) != null);
        assertTrue(df.parse(geolocationJson.get("date_updated").getAsString()) != null);
        assertTrue(df.parse(geolocationJson.get("date_executed").getAsString()) != null);
        assertTrue(geolocationJson.get("account_sid").getAsString().equals(adminAccountSid));
        assertTrue(geolocationJson.get("device_identifier").getAsString().equals(deviceIdentifier));
        assertTrue(geolocationJson.get("geolocation_type").getAsString().equals(NotificationGT));
        assertTrue(geolocationJson.get("response_status").getAsString().equals(responseStatus));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("cell_id").getAsString().equals(cellId));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("location_area_code").getAsString()
            .equals(locationAreaCode));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("mobile_country_code").getAsString()
            .equals(mobileCountryCode));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("mobile_network_code").getAsString()
            .equals(mobileNetworkCode));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("network_entity_address").getAsString()
            .equals(networkEntityAddress));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("location_age").getAsString()
            .equals(ageOfLocationInfo));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("device_latitude").getAsString()
            .equals(deviceLatitude));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("device_longitude").getAsString()
            .equals(deviceLongitude));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("accuracy").getAsString().equals(accuracy));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("internet_address").getAsString()
            .equals(internetAddress));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("physical_address").getAsString()
            .equals(physicalAddress));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("formatted_address").getAsString()
            .equals(formattedAddress));

        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("location_timestamp").getAsString()
            .equals(locationTimestamp));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("event_geofence_latitude").getAsString()
            .equals(eventGeofenceLatitude));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("event_geofence_longitude").getAsString()
            .equals(eventGeofenceLongitude));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("radius").getAsString().equals(radius));
        assertTrue(geolocationJson.get("geolocation_positioning_type").getAsString().equals(geolocationPositioningType));
        assertTrue(geolocationJson.get("last_geolocation_response").getAsString().equals(lastGeolocationResponse));
        assertTrue(geolocationJson.get("cause") == null);
        assertTrue(geolocationJson.get("api_version").getAsString().equals("2012-04-24"));

        // Remove created & updated Geolocation via HTTP DELETE
        RestcommGeolocationsTool.getInstance().deleteNotificationGeolocation(deploymentUrl.toString(), adminUsername,
            adminAuthToken, adminAccountSid, geolocationSid.toString());

    }

    @Test
    @Category(FeatureAltTests.class)
    public void testNotApiCompliantUpdateNotificationGeolocation()
        throws ParseException, IllegalArgumentException, ClientProtocolException, IOException {

        String msisdn = "5989738292";

        //This is for POST requests
        stubFor(post(urlPathEqualTo("/restcomm/gmlc/rest"))
            .withQueryParam("msisdn", equalTo(msisdn))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "text/xml")
                .withBody(gmlcResponse)));

        //This is for GET requests - REMOVE if not needed
        stubFor(get(urlPathEqualTo("/restcomm/gmlc/rest"))
            .withQueryParam("msisdn", equalTo(msisdn))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "text/xml")
                .withBody(gmlcResponse)));

        // Define Notification Geolocation attributes
        String deviceIdentifier, responseStatus, cellId, locationAreaCode, mobileCountryCode, mobileNetworkCode,
            networkEntityAddress, ageOfLocationInfo, deviceLatitude, deviceLongitude, accuracy, internetAddress,
            physicalAddress, formattedAddress, locationTimestamp = "2016-04-17T20:28:40.690-03:00", eventGeofenceLatitude,
            eventGeofenceLongitude, radius, geolocationPositioningType, lastGeolocationResponse;

        // Create Notification type of Geolocation via POST
        // Parameter values Assignment
        MultivaluedMap<String, String> geolocationParams = new MultivaluedMapImpl();
        geolocationParams.add("DeviceIdentifier", deviceIdentifier = msisdn);
        geolocationParams.add("EventGeofenceLatitude", "-33.426280");
        geolocationParams.add("EventGeofenceLongitude", "-70.566560");
        geolocationParams.add("GeofenceRange", "300");
        geolocationParams.add("GeofenceEvent", "in");
        geolocationParams.add("StatusCallback", "http://192.1.0.19:8080/ACae6e420f425248d6a26948c17a9e2acf");
        // HTTP POST Geolocation creation with given parameters values and those returned via GMLC stub
        JsonObject geolocationJson = RestcommGeolocationsTool.getInstance().createNotificationGeolocation(
            deploymentUrl.toString(), adminAccountSid, adminUsername, adminAuthToken, geolocationParams);
        Sid geolocationSid = new Sid(geolocationJson.get("sid").getAsString());

        DateTimeFormatter dtf = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
        DateTime dateTime = dtf.parseDateTime(locationTimestamp);
        SimpleDateFormat df = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss Z", Locale.US);
        locationTimestamp = df.format(dateTime.toDate());


        // Define malformed values for the Geolocation attributes (PUT test to fail)
        MultivaluedMap<String, String> geolocationParamsUpdate = new MultivaluedMapImpl();
        geolocationParamsUpdate.add("DeviceLatitude", deviceLatitude = "72.908134");
        geolocationParamsUpdate.add("DeviceLongitude", deviceLongitude = "South 170.908134"); // WGS84 not compliant
        // Update failed Geolocation via PUT
        RestcommGeolocationsTool.getInstance().updateNotificationGeolocation(deploymentUrl.toString(), adminUsername,
            adminAuthToken, adminAccountSid, geolocationSid.toString(), geolocationParamsUpdate, true);
        // Test asserts via GET to a single Geolocation
        geolocationJson = RestcommGeolocationsTool.getInstance().getNotificationGeolocation(deploymentUrl.toString(),
            adminUsername, adminAuthToken, adminAccountSid, geolocationSid.toString());

        assertTrue(df.parse(geolocationJson.get("date_created").getAsString()) != null);
        assertTrue(df.parse(geolocationJson.get("date_updated").getAsString()) != null);
        assertTrue(df.parse(geolocationJson.get("date_executed").getAsString()) != null);
        assertTrue(geolocationJson.get("account_sid").getAsString().equals(adminAccountSid));
        assertTrue(geolocationJson.get("source") == null);
        assertTrue(geolocationJson.get("device_identifier").getAsString().equals(deviceIdentifier));
        assertTrue(geolocationJson.get("geolocation_type").getAsString().equals(NotificationGT));
        assertTrue(geolocationJson.get("response_status").getAsString().equalsIgnoreCase("failed"));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("cell_id") == null);
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("location_area_code") == null);
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("mobile_country_code") == null);
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("mobile_network_code") == null);
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("network_entity_address") == null);
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("location_age") == null);
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("device_latitude") == null);
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("device_longitude") == null);
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("accuracy") == null);
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("internet_address") == null);
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("physical_address") == null);
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("formatted_address") == null);
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("location_timestamp") == null);
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("event_geofence_latitude") == null);
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("event_geofence_longitude") == null);
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("radius") == null);
        assertTrue(geolocationJson.get("geolocation_positioning_type") == null);
        assertTrue(geolocationJson.get("last_geolocation_response") == null);
        assertTrue(geolocationJson.get("cause") != null);
        assertTrue(geolocationJson.get("api_version").getAsString().equals("2012-04-24"));

        // Define new values for the Geolocation attributes (PUT test)
        geolocationParamsUpdate = new MultivaluedMapImpl();
        geolocationParamsUpdate.add("EventGeofenceLatitude", eventGeofenceLatitude = "172 42 62.80N");
        geolocationParamsUpdate.add("EventGeofenceLongitude", eventGeofenceLongitude = "170 56 65.60E");
        geolocationParamsUpdate.add("GeofenceRange", "50");
        geolocationParamsUpdate.add("GeofenceEvent", "out");
        geolocationParamsUpdate.add("DesiredAccuracy", "High");
        geolocationParamsUpdate.add("StatusCallback", "http://192.1.2.19:8080/ACae6e420f425248d6a26948c17a9e2acf");
        geolocationParamsUpdate.add("ResponseStatus", responseStatus = "successfull");
        geolocationParamsUpdate.add("CellId", cellId = "34580");
        geolocationParamsUpdate.add("LocationAreaCode", locationAreaCode = "709");
        geolocationParamsUpdate.add("MobileCountryCode", mobileCountryCode = "747");
        geolocationParamsUpdate.add("MobileNetworkCode", mobileNetworkCode = "05");
        geolocationParamsUpdate.add("NetworkEntityAddress", networkEntityAddress = "598003245703");
        geolocationParamsUpdate.add("LocationAge", ageOfLocationInfo = "0");
        geolocationParamsUpdate.add("DeviceLatitude", deviceLatitude = "172\u00b038'19.39''N");
        geolocationParamsUpdate.add("DeviceLongitude", deviceLongitude = "169\u00b028'44.07''E");
        geolocationParamsUpdate.add("Accuracy", accuracy = "25");
        geolocationParamsUpdate.add("InternetAddress", internetAddress = "180.7.2.141");
        geolocationParamsUpdate.add("PhysicalAddress", physicalAddress = "A8-77-CA-29-32-D1");
        geolocationParamsUpdate.add("FormattedAddress", formattedAddress = "Avenida Brasil 2681, 11300, Montevideo, Uruguay");
        geolocationParamsUpdate.add("LocationTimestamp", locationTimestamp = "2016-04-17T20:32:29.488-07:00");
        geolocationParamsUpdate.add("Radius", radius = "5");
        geolocationParamsUpdate.add("GeolocationPositioningType", geolocationPositioningType = "GPS");
        geolocationParamsUpdate.add("LastGeolocationResponse", lastGeolocationResponse = "true");
        geolocationParamsUpdate.add("Cause", "API Not Compliant");
        // Update Geolocation via PUT
        // previous failed location is composed again with new proper geolocation data values
        RestcommGeolocationsTool.getInstance().updateNotificationGeolocation(deploymentUrl.toString(), adminUsername,
            adminAuthToken, adminAccountSid, geolocationSid.toString(), geolocationParamsUpdate, true);

        // Test asserts via GET to a single Geolocation
        geolocationJson = RestcommGeolocationsTool.getInstance().getNotificationGeolocation(deploymentUrl.toString(),
            adminUsername, adminAuthToken, adminAccountSid, geolocationSid.toString());

        dateTime = dtf.parseDateTime(locationTimestamp);
        locationTimestamp = df.format(dateTime.toDate());
        assertTrue(df.parse(geolocationJson.get("date_created").getAsString()) != null);
        assertTrue(df.parse(geolocationJson.get("date_updated").getAsString()) != null);
        assertTrue(df.parse(geolocationJson.get("date_executed").getAsString()) != null);
        assertTrue(geolocationJson.get("account_sid").getAsString().equals(adminAccountSid));
        assertTrue(geolocationJson.get("device_identifier").getAsString().equals(deviceIdentifier));
        assertTrue(geolocationJson.get("geolocation_type").getAsString().equals(NotificationGT));
        assertTrue(geolocationJson.get("response_status").getAsString().equals(responseStatus));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("cell_id").getAsString().equals(cellId));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("location_area_code").getAsString()
            .equals(locationAreaCode));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("mobile_country_code").getAsString()
            .equals(mobileCountryCode));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("mobile_network_code").getAsString()
            .equals(mobileNetworkCode));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("network_entity_address").getAsString()
            .equals(networkEntityAddress));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("location_age").getAsString()
            .equals(ageOfLocationInfo));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("device_latitude").getAsString()
            .equals(deviceLatitude));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("device_longitude").getAsString()
            .equals(deviceLongitude));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("accuracy").getAsString().equals(accuracy));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("internet_address").getAsString()
            .equals(internetAddress));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("physical_address").getAsString()
            .equals(physicalAddress));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("formatted_address").getAsString()
            .equals(formattedAddress));

        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("location_timestamp").getAsString()
            .equals(locationTimestamp));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("event_geofence_latitude").getAsString()
            .equals(eventGeofenceLatitude));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("event_geofence_longitude").getAsString()
            .equals(eventGeofenceLongitude));
        assertTrue(geolocationJson.get("geolocation_data").getAsJsonObject().get("radius").getAsString().equals(radius));
        assertTrue(geolocationJson.get("geolocation_positioning_type").getAsString().equals(geolocationPositioningType));
        assertTrue(geolocationJson.get("last_geolocation_response").getAsString().equals(lastGeolocationResponse));
        assertTrue(geolocationJson.get("cause") == null);
        assertTrue(geolocationJson.get("api_version").getAsString().equals("2012-04-24"));

        // Remove created & updated Geolocation via HTTP DELETE
        RestcommGeolocationsTool.getInstance().deleteNotificationGeolocation(deploymentUrl.toString(), adminUsername,
            adminAuthToken, adminAccountSid, geolocationSid.toString());

    }

    @Test
    public void testDeleteNotificationGeolocation() throws IllegalArgumentException, ClientProtocolException, IOException {

        String msisdn = "5989738292";

        //This is for POST requests
        stubFor(post(urlPathEqualTo("/restcomm/gmlc/rest"))
            .withQueryParam("msisdn", equalTo(msisdn))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "text/xml")
                .withBody(gmlcResponse)));

        //This is for GET requests - REMOVE if not needed
        stubFor(get(urlPathEqualTo("/restcomm/gmlc/rest"))
            .withQueryParam("msisdn", equalTo(msisdn))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "text/xml")
                .withBody(gmlcResponse)));

        String deviceIdentifier;

        // Create Notification type of Geolocation via POST
        // Parameter values Assignment
        MultivaluedMap<String, String> geolocationParams = new MultivaluedMapImpl();
        geolocationParams.add("DeviceIdentifier", deviceIdentifier = msisdn);
        geolocationParams.add("EventGeofenceLatitude", "-33.426280");
        geolocationParams.add("EventGeofenceLongitude", "-70.566560");
        geolocationParams.add("GeofenceRange", "300");
        geolocationParams.add("GeofenceEvent", "in");
        geolocationParams.add("DesiredAccuracy", "High");
        geolocationParams.add("StatusCallback", "http://192.1.0.19:8080/ACae6e420f425248d6a26948c17a9e2acf");
        geolocationParams.add("ResponseStatus", "successfull");
        geolocationParams.add("Accuracy", "5");
        geolocationParams.add("InternetAddress", "194.87.1.127");
        geolocationParams.add("PhysicalAddress", "D8-97-BA-19-02-D8");
        geolocationParams.add("LocationTimestamp", "2016-04-17T20:28:40.690-03:00");
        geolocationParams.add("Radius", "200");
        geolocationParams.add("GeolocationPositioningType", "GPS");
        geolocationParams.add("LastGeolocationResponse", "true");
        geolocationParams.add("Cause", "Not API Compliant");
        // HTTP POST Geolocation creation with given parameters values and those returned via GMLC stub
        JsonObject geolocationJson = RestcommGeolocationsTool.getInstance().createNotificationGeolocation(
            deploymentUrl.toString(), adminAccountSid, adminUsername, adminAuthToken, geolocationParams);
        Sid geolocationSid = new Sid(geolocationJson.get("sid").getAsString());

        // Remove created Geolocation via HTTP DELETE
        RestcommGeolocationsTool.getInstance().deleteNotificationGeolocation(deploymentUrl.toString(), adminUsername,
            adminAuthToken, adminAccountSid, geolocationSid.toString());
        // Remove checking Test asserts via HTTP GET
        geolocationJson = RestcommGeolocationsTool.getInstance().getNotificationGeolocation(deploymentUrl.toString(),
            adminUsername, adminAuthToken, adminAccountSid, geolocationSid.toString());

        assertTrue(geolocationJson == null);
    }

    @Deployment(name = "GeolocationsEndpointTest", managed = true, testable = false)
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
        archive.addAsWebInfResource("restcomm.script", "data/hsql/restcomm.script");
        logger.info("Packaged Test App");
        return archive;
    }

}
