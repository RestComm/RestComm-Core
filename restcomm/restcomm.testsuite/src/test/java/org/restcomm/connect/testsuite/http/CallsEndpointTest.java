package org.restcomm.connect.testsuite.http;

import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static junit.framework.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import com.google.gson.JsonElement;
import com.sun.jersey.api.client.UniformInterfaceException;
import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.Deployer;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.joda.time.DateTime;
import org.junit.Assert;
import org.junit.Test;
import org.junit.FixMethodOrder;
import org.junit.runners.MethodSorters;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.restcomm.connect.commons.Version;
import org.restcomm.connect.commons.annotations.FeatureAltTests;

/**
 * @author <a href="mailto:gvagenas@gmail.com">gvagenas</a>
 * @author <a href="mailto:jean.deruelle@telestax.com">Jean Deruelle</a>
 */

@RunWith(Arquillian.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class CallsEndpointTest {
    private final static Logger logger = Logger.getLogger(CallsEndpointTest.class.getName());

    private static final String version = Version.getVersion();

    @ArquillianResource
    private Deployer deployer;
    @ArquillianResource
    URL deploymentUrl;

    private String adminAccountSid = "ACae6e420f425248d6a26948c17a9e2acf";
    private String adminAuthToken = "77f8c12cc7b8f8423e5c38b035249166";
    private String topLevelAccountSid = "AC00000000000000000000000000000000";
    private String subAccount1 =  "AC10000000000000000000000000000000";
    private String subAccount2 =  "AC20000000000000000000000000000000";
    private String subAccount11 = "AC11000000000000000000000000000000";
    private String subAccount111 = "AC11100000000000000000000000000000";
    private String commonAuthToken = "77f8c12cc7b8f8423e5c38b035249166";

    @Test
    public void getCallsList() {
        JsonObject firstPage = RestcommCallsTool.getInstance().getCalls(deploymentUrl.toString(), adminAccountSid,
                adminAuthToken);
        int totalSize = firstPage.get("total").getAsInt();
        JsonArray firstPageCallsArray = firstPage.get("calls").getAsJsonArray();
        int firstPageCallsArraySize = firstPageCallsArray.size();
        assertEquals(firstPageCallsArraySize, 50);
        assertEquals(firstPage.get("start").getAsInt(), 0);
        assertEquals(firstPage.get("end").getAsInt(), 49);

        JsonObject secondPage = (JsonObject) RestcommCallsTool.getInstance().getCalls(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, 2, null, null, true);
        JsonArray secondPageCallsArray = secondPage.get("calls").getAsJsonArray();
        assertEquals(secondPageCallsArray.size(), 50);
        assertEquals(secondPage.get("start").getAsInt(), 100);
        assertEquals(secondPage.get("end").getAsInt(), 149);

        JsonObject lastPage = (JsonObject) RestcommCallsTool.getInstance().getCalls(deploymentUrl.toString(), adminAccountSid,
                adminAuthToken, firstPage.get("num_pages").getAsInt(), null, null, true);
        JsonArray lastPageCallsArray = lastPage.get("calls").getAsJsonArray();
        assertEquals(lastPageCallsArray.get(lastPageCallsArray.size() - 1).getAsJsonObject().get("sid").getAsString(),
                "CA22ac5fd16a954c339f33519bdfe4af78");
        assertEquals(lastPageCallsArray.size(), 48);
        assertEquals(lastPage.get("start").getAsInt(), 400);
        assertEquals(lastPage.get("end").getAsInt(), 448);

        assertEquals(totalSize, 448);
    }

    @Test
    public void getCallsListUsingSorting() {
        // Provide both sort field and direction
        // Provide ascending sorting and verify that the first row is indeed the earliest one
        JsonObject response = RestcommCallsTool.getInstance().getCalls(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, 0, 10, "DateCreated:asc", true);
        // Remember there is a discrepancy between the sort parameters and the result attribute in the .json response. For example DateCreated:asc, means
        // to sort based of DateCreated field, but in the response the field is called 'date_created', not DateCreated. This only happens only for .json; in
        // .xml the naming seems to be respected.
        // Notice that we are removing the timezone part from the end of the string, because CI potentially uses different timezone that messes the test up
        assertEquals("Fri, 5 Jul 2013 22:15:53", ((JsonObject)response.get("calls").getAsJsonArray().get(0)).get("date_created").getAsString().replaceFirst("\\s*\\+.*", ""));

        // Provide only sort field; all fields default to ascending
        response = RestcommCallsTool.getInstance().getCalls(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, 0, 10, "DateCreated", true);
        assertEquals("Fri, 5 Jul 2013 22:15:53", ((JsonObject)response.get("calls").getAsJsonArray().get(0)).get("date_created").getAsString().replaceFirst("\\s*\\+.*", ""));

        response = RestcommCallsTool.getInstance().getCalls(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, 0, 10, "DateCreated:desc", true);
        assertEquals("Tue, 31 May 2016 16:20:22", ((JsonObject)response.get("calls").getAsJsonArray().get(0)).get("date_created").getAsString().replaceFirst("\\s*\\+.*", ""));

        try {
            // provide only direction, should cause an exception
            RestcommCallsTool.getInstance().getCalls(deploymentUrl.toString(),
                    adminAccountSid, adminAuthToken, 0, 10, ":asc", true);
        }
        catch (UniformInterfaceException e) {
            assertEquals(BAD_REQUEST.getStatusCode(), e.getResponse().getStatus());
        }

        try {
            // provide sort field and direction, but direction is invalid (neither of asc or desc)
            RestcommCallsTool.getInstance().getCalls(deploymentUrl.toString(),
                    adminAccountSid, adminAuthToken, 0, 10, "DateCreated:invalid", true);
        }
        catch (UniformInterfaceException e) {
            assertEquals(BAD_REQUEST.getStatusCode(), e.getResponse().getStatus());
        }

        // From
        response = RestcommCallsTool.getInstance().getCalls(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, 0, 10, "From:asc", true);
        assertEquals("+101133679364376", ((JsonObject)response.get("calls").getAsJsonArray().get(0)).get("from").getAsString());

        response = RestcommCallsTool.getInstance().getCalls(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, 0, 10, "From:desc", true);
        assertEquals("thomas", ((JsonObject)response.get("calls").getAsJsonArray().get(0)).get("from").getAsString());

        // To
        response = RestcommCallsTool.getInstance().getCalls(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, 0, 10, "To:asc", true);
        assertEquals("+13052406432", ((JsonObject)response.get("calls").getAsJsonArray().get(0)).get("to").getAsString());

        response = RestcommCallsTool.getInstance().getCalls(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, 0, 10, "To:desc", true);
        assertEquals("15126002188", ((JsonObject)response.get("calls").getAsJsonArray().get(0)).get("to").getAsString());

        // Direction
        response = RestcommCallsTool.getInstance().getCalls(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, 0, 10, "Direction:asc", true);
        assertEquals("inbound", ((JsonObject)response.get("calls").getAsJsonArray().get(0)).get("direction").getAsString());

        response = RestcommCallsTool.getInstance().getCalls(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, 0, 10, "Direction:desc", true);
        assertEquals("outbound", ((JsonObject)response.get("calls").getAsJsonArray().get(0)).get("direction").getAsString());

        // Status
        response = RestcommCallsTool.getInstance().getCalls(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, 0, 10, "Status:asc", true);
        assertEquals("completed", ((JsonObject)response.get("calls").getAsJsonArray().get(0)).get("status").getAsString());

        response = RestcommCallsTool.getInstance().getCalls(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, 0, 10, "Status:desc", true);
        assertEquals("ringing", ((JsonObject)response.get("calls").getAsJsonArray().get(0)).get("status").getAsString());

        // TODO: need to improve Duration and Price assertions because right now there are so many NULL values in duration that are messing this up. We should
        // probably add non null values in all duration and price fields to make these work better
        // Duration
        response = RestcommCallsTool.getInstance().getCalls(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, 0, 10, "Duration:asc", true);
        assertEquals(null, ((JsonObject)response.get("calls").getAsJsonArray().get(0)).get("duration"));

        response = RestcommCallsTool.getInstance().getCalls(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, 0, 10, "Duration:desc", true);
        assertEquals(null, ((JsonObject)response.get("calls").getAsJsonArray().get(0)).get("duration"));

        // Price
        response = RestcommCallsTool.getInstance().getCalls(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, 0, 10, "Price:asc", true);
        assertEquals(null, ((JsonObject)response.get("calls").getAsJsonArray().get(0)).get("price"));

        response = RestcommCallsTool.getInstance().getCalls(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, 0, 10, "Price:desc", true);
        assertEquals(null, ((JsonObject)response.get("calls").getAsJsonArray().get(0)).get("price\""));
    }

    @Test
    @Category(FeatureAltTests.class)
    public void getCallsListUsingPageSize() {
        JsonObject firstPage = (JsonObject) RestcommCallsTool.getInstance().getCalls(deploymentUrl.toString(), adminAccountSid,
                adminAuthToken, null, 100, null, true);
        int totalSize = firstPage.get("total").getAsInt();
        JsonArray firstPageCallsArray = firstPage.get("calls").getAsJsonArray();
        int firstPageCallsArraySize = firstPageCallsArray.size();
        assertEquals(firstPageCallsArraySize, 100);
        assertEquals(firstPage.get("start").getAsInt(), 0);
        assertEquals(firstPage.get("end").getAsInt(), 99);

        JsonObject secondPage = (JsonObject) RestcommCallsTool.getInstance().getCalls(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, 2, 100, null, true);
        JsonArray secondPageCallsArray = secondPage.get("calls").getAsJsonArray();
        assertEquals(secondPageCallsArray.size(), 100);
        assertEquals(secondPage.get("start").getAsInt(), 200);
        assertEquals(secondPage.get("end").getAsInt(), 299);

        JsonObject lastPage = (JsonObject) RestcommCallsTool.getInstance().getCalls(deploymentUrl.toString(), adminAccountSid,
                adminAuthToken, firstPage.get("num_pages").getAsInt(), 100, null, true);
        JsonArray lastPageCallsArray = lastPage.get("calls").getAsJsonArray();
        assertEquals("CA22ac5fd16a954c339f33519bdfe4af78",lastPageCallsArray.get(lastPageCallsArray.size() - 1).getAsJsonObject().get("sid").getAsString());
        assertEquals(lastPageCallsArray.size(), 48);
        assertEquals(lastPage.get("start").getAsInt(), 400);
        assertEquals(lastPage.get("end").getAsInt(), 448);

        assertEquals(totalSize, 448);
    }

    @Test
    @Category(FeatureAltTests.class)
    public void getCallsListFilteredByStatus() {
        Map<String, String> filters = new HashMap<String, String>();
        filters.put("Status", "in-progress");

        JsonObject allCallsObject = RestcommCallsTool.getInstance().getCalls(deploymentUrl.toString(), adminAccountSid,
                adminAuthToken);

        JsonObject filteredCallsByStatusObject = RestcommCallsTool.getInstance().getCallsUsingFilter(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, filters);

        assertEquals(filteredCallsByStatusObject.get("calls").getAsJsonArray().size(), 50);
        assertEquals(allCallsObject.get("start").getAsInt(), 0);
        assertEquals(allCallsObject.get("end").getAsInt(), 49);
        assertEquals(filteredCallsByStatusObject.get("start").getAsInt(), 0);
        assertEquals(filteredCallsByStatusObject.get("end").getAsInt(), 49);
        assertEquals(allCallsObject.get("calls").getAsJsonArray().size(), filteredCallsByStatusObject.get("calls")
                .getAsJsonArray().size());
    }

    @Test
    @Category(FeatureAltTests.class)
    public void getCallsListFilteredBySender() {
        Map<String, String> filters = new HashMap<String, String>();
        filters.put("From", "3021097%");
        JsonObject allCalls = RestcommCallsTool.getInstance().getCalls(deploymentUrl.toString(), adminAccountSid,
                adminAuthToken);

        JsonObject filteredCallsBySender = RestcommCallsTool.getInstance().getCallsUsingFilter(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, filters);

        assertEquals(filteredCallsBySender.get("calls").getAsJsonArray().size(), 50);
        assertEquals(allCalls.get("start").getAsInt(), 0);
        assertEquals(allCalls.get("end").getAsInt(), 49);
        assertEquals(filteredCallsBySender.get("start").getAsInt(), 0);
        assertEquals(filteredCallsBySender.get("end").getAsInt(), 49);
        assertEquals(allCalls.get("calls").getAsJsonArray().size(), filteredCallsBySender.get("calls").getAsJsonArray().size());
    }

    @Test
    @Category(FeatureAltTests.class)
    public void getCallsListFilteredByRecipient() {
        Map<String, String> filters = new HashMap<String, String>();
        filters.put("To", "1512600%");
        JsonObject allCalls = RestcommCallsTool.getInstance().getCalls(deploymentUrl.toString(), adminAccountSid,
                adminAuthToken);

        JsonObject filteredCallsByRecipient = RestcommCallsTool.getInstance().getCallsUsingFilter(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, filters);

        assertEquals(filteredCallsByRecipient.get("calls").getAsJsonArray().size(), 50);
        assertEquals(allCalls.get("calls").getAsJsonArray().size(), filteredCallsByRecipient.get("calls").getAsJsonArray()
                .size());
    }

    @Test
    @Category(FeatureAltTests.class)
    public void getCallsListFilteredByStartTime() {
        Map<String, String> filters = new HashMap<String, String>();
        filters.put("StartTime", "2013-08-23 14:30:07.820000000");
        JsonObject allCalls = RestcommCallsTool.getInstance().getCalls(deploymentUrl.toString(), adminAccountSid,
                adminAuthToken);

        JsonObject filteredCallsByStartTime = RestcommCallsTool.getInstance().getCallsUsingFilter(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, filters);

        assertTrue(filteredCallsByStartTime.get("calls").getAsJsonArray().size() > 0);
        assertEquals(allCalls.get("calls").getAsJsonArray().size(), filteredCallsByStartTime.get("calls").getAsJsonArray()
                .size());
    }

    @Test
    @Category(FeatureAltTests.class)
    public void getCallsListFilteredByParentCallSid() {
        Map<String, String> filters = new HashMap<String, String>();
        filters.put("ParentCallSid", "CA01a09068a1f348269b6670ef599a6e57");

        JsonObject filteredCallsByParentCallSid = RestcommCallsTool.getInstance().getCallsUsingFilter(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, filters);

        assertEquals(filteredCallsByParentCallSid.get("calls").getAsJsonArray().size(), 0);
    }

    @Test
    @Category(FeatureAltTests.class)
    public void getCallsListFilteredUsingMultipleFilters() {
        Map<String, String> filters = new HashMap<String, String>();
        filters.put("StartTime", "2013-08-23 14:30:07.820000000");
        filters.put("To", "1512600%");
        filters.put("From", "3021097%");
        filters.put("Status", "in-progress");

        JsonObject allCalls = RestcommCallsTool.getInstance().getCalls(deploymentUrl.toString(), adminAccountSid,
                adminAuthToken);

        JsonObject filteredCallsUsingMultipleFilters = RestcommCallsTool.getInstance().getCallsUsingFilter(
                deploymentUrl.toString(), adminAccountSid, adminAuthToken, filters);

        assertTrue(filteredCallsUsingMultipleFilters.get("calls").getAsJsonArray().size() > 0);
        assertTrue(allCalls.get("calls").getAsJsonArray().size() > filteredCallsUsingMultipleFilters.get("calls")
                .getAsJsonArray().size());
    }

    @Test
    @Category(FeatureAltTests.class)
    public void getCallsListIncludingSubAccounts() {
        Map<String, String> filters = new HashMap<String, String>();
        filters.put("SubAccounts", "true");

        // retrieve top-level account cdrs
        JsonObject calls = RestcommCallsTool.getInstance().getCallsUsingFilter(
                deploymentUrl.toString(), topLevelAccountSid, commonAuthToken, filters);
        assertEquals(50,calls.get("calls").getAsJsonArray().size()); // that's because of the default page size
        int totalSize = calls.get("total").getAsInt();
        assertEquals(54, calls.get("total").getAsInt());
        // retrieve second page too
        filters.put("Page", "1");
        calls = RestcommCallsTool.getInstance().getCallsUsingFilter(
                deploymentUrl.toString(), topLevelAccountSid, commonAuthToken, filters);
        assertEquals(4, calls.get("calls").getAsJsonArray().size()); // 50 + 4 = 54
        filters.remove("Page"); // no page for the rest of the cases
        // retrieve first level child account cdrs
        calls = RestcommCallsTool.getInstance().getCallsUsingFilter(
                deploymentUrl.toString(), subAccount1, commonAuthToken, filters);
        assertEquals(33,calls.get("calls").getAsJsonArray().size());
        // retrieve first level child (with no grandchildren) account cdrs
        calls = RestcommCallsTool.getInstance().getCallsUsingFilter(
                deploymentUrl.toString(), subAccount2, commonAuthToken, filters);
        assertEquals(19,calls.get("calls").getAsJsonArray().size());
        // retrieve second level child cdrs
        calls = RestcommCallsTool.getInstance().getCallsUsingFilter(
                deploymentUrl.toString(), subAccount11, commonAuthToken, filters);
        assertEquals(18,calls.get("calls").getAsJsonArray().size());
        // retrieve third level child cdrs
        calls = RestcommCallsTool.getInstance().getCallsUsingFilter(
                deploymentUrl.toString(), subAccount111, commonAuthToken, filters);
        assertEquals(11,calls.get("calls").getAsJsonArray().size());
        // retrieve all cdrs of administrator@company.com but using the SubAccount filter
        calls = RestcommCallsTool.getInstance().getCallsUsingFilter(
                deploymentUrl.toString(), adminAccountSid, adminAuthToken, filters);
        assertEquals(50,calls.get("calls").getAsJsonArray().size());
    }

    @Test
    public void getCallRecordingsList() {
        String callWithRecordingsSid = "CAfe9ce46f104f4beeb10c83a5dad2be66";
        JsonArray callRecordings = RestcommCallsTool.getInstance().getCallRecordings(deploymentUrl.toString(), adminAccountSid,
                adminAuthToken, callWithRecordingsSid );
        assertEquals("Call recordings size() should be 1", 1, callRecordings.size());

        String callWithoutRecordings = "CAfd82074503754b80aa8555199dfcc703";
        callRecordings = RestcommCallsTool.getInstance().getCallRecordings(deploymentUrl.toString(), adminAccountSid,
                adminAuthToken, callWithoutRecordings );
        assertEquals("Call recordings size() should be 0", 0, callRecordings.size());
    }

    @Deployment(name = "ClientsEndpointTest", managed = true, testable = false)
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
        archive.addAsWebInfResource("restcomm_with_Data.script", "data/hsql/restcomm.script");
        logger.info("Packaged Test App");
        return archive;
    }

}
