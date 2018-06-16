/*
 * TeleStax, Open Source Cloud Communications
 * Copyright 2011-2015, Telestax Inc and individual contributors
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

import static org.junit.Assert.*;

import com.sun.jersey.api.client.UniformInterfaceException;
import com.google.gson.JsonElement;
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
import org.junit.experimental.categories.Category;
import org.restcomm.connect.commons.annotations.BrokenTests;
import org.restcomm.connect.commons.annotations.UnstableTests;

/**
 * @author <a href="mailto:abdulazizali@acm.org">abdulazizali77</a>
 */

@RunWith(Arquillian.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class UsageRecordsTest {
    private static Logger logger = Logger.getLogger(UsageRecordsTest.class);

    private static final String version = Version.getVersion();
    private static final String revision = Version.getRevision();

    @ArquillianResource
    URL deploymentUrl;

    private String adminAccountSid = "ACae6e420f425248d6a26948c17a9e2acf";
    private String adminAuthToken = "77f8c12cc7b8f8423e5c38b035249166";

    @Test(expected = UniformInterfaceException.class)
    public void getUsageRecordsList() {
        // NB: currently unimplemented
        JsonElement firstPage = RestcommUsageRecordsTool.getInstance().getUsageRecords(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, "", "", true);
        logger.info(firstPage);
    }

    @Test(expected = UniformInterfaceException.class)
    public void getUsageRecordsListCategoryCalls() {
        JsonElement firstPage = RestcommUsageRecordsTool.getInstance().getUsageRecordsDaily(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, "calls", true);
        logger.info(firstPage);
    }

    @Test(expected = UniformInterfaceException.class)
    public void getUsageRecordsListCategorySms() {
        JsonElement firstPage = RestcommUsageRecordsTool.getInstance().getUsageRecordsDaily(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, "sms", true);
        logger.info(firstPage);
    }

    @Test
    @Category(BrokenTests.class)
    public void getUsageRecordsDaily() {
        JsonElement response = RestcommUsageRecordsTool.getInstance().getUsageRecordsDaily(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, "", true);

        assertNotNull(response);
        // 12 days
        assertTrue(response.getAsJsonArray().size() == 12);
        // TODO: write factory method to arbitrarily compare expectation and output
        JsonObject firstRecord = response.getAsJsonArray().get(0).getAsJsonObject();
        String category = firstRecord.get("category").getAsString();
        String description = firstRecord.get("description").getAsString();
        String startDate = firstRecord.get("start_date").getAsString();
        String endDate = firstRecord.get("end_date").getAsString();
        String count = firstRecord.get("count").getAsString();
        String usage = firstRecord.get("usage").getAsString();
        String price = firstRecord.get("price").getAsString();
        String count_unit = firstRecord.get("count_unit").getAsString();
        String usage_unit = firstRecord.get("usage_unit").getAsString();
        String price_unit = firstRecord.get("price_unit").getAsString();
        String uri = firstRecord.get("uri").getAsString();

        assertTrue(category.equals("calls"));
        assertTrue(description.equals("Total calls"));
        assertTrue(startDate.equals("2016-01-01"));
        assertTrue(endDate.equals("2016-01-01"));
        assertTrue(count.equals("3"));// 3 calls per day
        assertTrue(usage.equals("1"));// 20 seconds each
        assertTrue(price.equals("15.0"));// TODO: check if price per usage or count?
        assertTrue(count_unit.equals("calls"));
        assertTrue(usage_unit.equals("minutes"));
        assertTrue(price_unit.equals("USD"));
        //TODO: potential problem with order of query params
        assertTrue(uri.equals("/2012-04-24/Accounts/ACae6e420f425248d6a26948c17a9e2acf/Usage/Records/Daily.json?Category=Calls&EndDate=2016-01-01&StartDate=2016-01-01"));

        //TODO: test other categories
    }

    @Test
    @Category(BrokenTests.class)
    public void getUsageRecordsMonthly() {
        JsonElement response = RestcommUsageRecordsTool.getInstance().getUsageRecordsMonthly(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, "", true);
        // 4 months
        assertTrue(response.getAsJsonArray().size() == 4);
        // TODO: write factory method to arbitrarily compare expectation and output
        JsonObject firstRecord = response.getAsJsonArray().get(0).getAsJsonObject();
        String category = firstRecord.get("category").getAsString();
        String description = firstRecord.get("description").getAsString();
        String startDate = firstRecord.get("start_date").getAsString();
        String endDate = firstRecord.get("end_date").getAsString();
        String count = firstRecord.get("count").getAsString();
        String usage = firstRecord.get("usage").getAsString();
        String price = firstRecord.get("price").getAsString();
        String count_unit = firstRecord.get("count_unit").getAsString();
        String usage_unit = firstRecord.get("usage_unit").getAsString();
        String price_unit = firstRecord.get("price_unit").getAsString();
        String uri = firstRecord.get("uri").getAsString();

        assertTrue(category.equals("calls"));
        assertTrue(description.equals("Total Calls"));
        assertTrue(startDate.equals("2016-01-01"));
        assertTrue(endDate.equals("2016-01-31"));
        assertTrue(count.equals("9"));
        assertTrue(usage.equals("3"));// 3minutes
        assertTrue(price.equals("45.0"));
        assertTrue(count_unit.equals("calls"));
        assertTrue(usage_unit.equals("minutes"));
        assertTrue(price_unit.equals("USD"));
        assertTrue(uri.equals("/2012-04-24/Accounts/ACae6e420f425248d6a26948c17a9e2acf/Usage/Records/Monthly.json?Category=Calls&EndDate=2016-01-01&StartDate=2016-01-31"));

        //TODO: test other categories
    }

    @Test
    @Category(BrokenTests.class)
    public void getUsageRecordsYearly() {
        JsonElement response = RestcommUsageRecordsTool.getInstance().getUsageRecordsYearly(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, "", true);
        // 2 years
        assertTrue(response.getAsJsonArray().size() == 2);

        JsonObject firstRecord = response.getAsJsonArray().get(0).getAsJsonObject();
        String category = firstRecord.get("category").getAsString();
        String description = firstRecord.get("description").getAsString();
        String startDate = firstRecord.get("start_date").getAsString();
        String endDate = firstRecord.get("end_date").getAsString();
        String count = firstRecord.get("count").getAsString();
        String usage = firstRecord.get("usage").getAsString();
        String price = firstRecord.get("price").getAsString();
        String count_unit = firstRecord.get("count_unit").getAsString();
        String usage_unit = firstRecord.get("usage_unit").getAsString();
        String price_unit = firstRecord.get("price_unit").getAsString();
        String uri = firstRecord.get("uri").getAsString();

        assertTrue(category.equals("calls"));
        assertTrue(description.equals("Total Calls"));
        assertTrue(startDate.equals("2016-01-01"));
        assertTrue(endDate.equals("2016-12-31"));
        assertTrue(count.equals("18"));
        assertTrue(usage.equals("6"));// 6 minutes
        assertTrue(price.equals("90.0"));
        assertTrue(count_unit.equals("calls"));
        assertTrue(usage_unit.equals("minutes"));
        assertTrue(price_unit.equals("USD"));
        assertTrue(uri.equals("/2012-04-24/Accounts/ACae6e420f425248d6a26948c17a9e2acf/Usage/Records/Yearly.json?Category=Calls&EndDate=2016-01-01&StartDate=2016-12-31"));

        //TODO: test other categories
    }

    @Test
    @Category(BrokenTests.class)
    public void getUsageRecordsAlltime() {
        JsonElement response = RestcommUsageRecordsTool.getInstance().getUsageRecordsAllTime(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, "", true);

        assertTrue(response.getAsJsonArray().size() == 1);

        JsonObject firstRecord = response.getAsJsonArray().get(0).getAsJsonObject();
        String category = firstRecord.get("category").getAsString();
        String description = firstRecord.get("description").getAsString();
        String startDate = firstRecord.get("start_date").getAsString();
        String endDate = firstRecord.get("end_date").getAsString();
        String count = firstRecord.get("count").getAsString();
        String usage = firstRecord.get("usage").getAsString();
        String price = firstRecord.get("price").getAsString();
        String count_unit = firstRecord.get("count_unit").getAsString();
        String usage_unit = firstRecord.get("usage_unit").getAsString();
        String price_unit = firstRecord.get("price_unit").getAsString();
        String uri = firstRecord.get("uri").getAsString();

        assertTrue(category.equals("calls"));
        assertTrue(description.equals("Total Calls"));
        assertTrue(startDate.equals("2016-01-01"));
        assertTrue(endDate.equals("2017-12-31"));
        assertTrue(count.equals("36"));
        assertTrue(usage.equals("360"));
        assertTrue(price.equals("180.0"));
        assertTrue(count_unit.equals("calls"));
        assertTrue(usage_unit.equals("minutes"));
        assertTrue(price_unit.equals("USD"));
        //FIXME:start date should be start of epoch?
        assertTrue(uri.equals("/2012-04-24/Accounts/ACae6e420f425248d6a26948c17a9e2acf/Usage/Records/AllTime.json?Category=Calls&EndDate=2017-02-03&StartDate=2016-01-01"));

        //TODO: test other categories
    }

    @Test
    public void getUsageRecordsToday() {
        JsonElement firstPage = RestcommUsageRecordsTool.getInstance().getUsageRecordsToday(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, "", true);
        logger.info(firstPage);
    }

    @Test
    public void getUsageRecordsYesterday() {
        JsonElement firstPage = RestcommUsageRecordsTool.getInstance().getUsageRecordsYesterday(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, "", true);
        logger.info(firstPage);
    }

    @Test
    public void getUsageRecordsThisMonth() {
        JsonElement firstPage = RestcommUsageRecordsTool.getInstance().getUsageRecordsThisMonth(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, "", true);
        logger.info(firstPage);
    }

    @Test
    public void getUsageRecordsLastMonth() {
        JsonElement firstPage = RestcommUsageRecordsTool.getInstance().getUsageRecordsLastMonth(deploymentUrl.toString(),
                adminAccountSid, adminAuthToken, "", true);
        logger.info(firstPage);
    }

    @Deployment(name = "UsageRecordsTest", managed = true, testable = false)
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
        // archive.delete("/WEB-INF/data/hsql/restcomm.properties");
        archive.addAsWebInfResource("sip.xml");
        archive.addAsWebInfResource("web.xml");
        archive.addAsWebInfResource("restcomm.xml", "conf/restcomm.xml");
        archive.addAsWebInfResource("restcomm_with_Data_UsageRecords.script", "data/hsql/restcomm.script");
        // archive.addAsWebInfResource("restcomm.properties", "data/hsql/restcomm.properties");
        return archive;
    }
}