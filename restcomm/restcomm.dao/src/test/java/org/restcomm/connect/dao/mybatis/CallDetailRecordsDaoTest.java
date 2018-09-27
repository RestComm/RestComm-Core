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
package org.restcomm.connect.dao.mybatis;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

import java.io.FileInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;

import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.joda.time.DateTime;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.restcomm.connect.commons.dao.Sid;
import org.restcomm.connect.dao.CallDetailRecordsDao;
import org.restcomm.connect.dao.entities.CallDetailRecord;
import org.restcomm.connect.dao.entities.CallDetailRecordFilter;

import junit.framework.Assert;
import static org.junit.Assert.assertNull;
import org.junit.FixMethodOrder;
import org.junit.Rule;
import org.junit.rules.TestName;
import org.junit.runners.MethodSorters;
import org.restcomm.connect.dao.common.Sorting;

/**
 * @author quintana.thomas@gmail.com (Thomas Quintana)
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class CallDetailRecordsDaoTest extends DaoTest {
    @Rule public TestName name = new TestName();

    private static MybatisDaoManager manager;

    public CallDetailRecordsDaoTest() {
        super();
    }

    @Before
    public void before() throws Exception {
        //use testmethod name to further ensure uniqueness of tmp dir
        sandboxRoot = createTempDir("cdrTest" + name.getMethodName());
        String mybatisFilesPath = getClass().getResource("/callDetailRecordsDao").getFile();
        setupSandbox(mybatisFilesPath, sandboxRoot);

        String mybatisXmlPath = sandboxRoot.getPath() + "/mybatis_updated.xml";
        final InputStream data = new FileInputStream(mybatisXmlPath);
        final SqlSessionFactoryBuilder builder = new SqlSessionFactoryBuilder();
        final SqlSessionFactory factory = builder.build(data);
        manager = new MybatisDaoManager();
        manager.start(factory);
    }

    @After
    public void after() {
        manager.shutdown();
        removeTempDir(sandboxRoot.getAbsolutePath());
    }

    @Test
    public void createReadUpdateDelete() {
        final Sid sid = Sid.generate(Sid.Type.CALL);
        final Sid account = Sid.generate(Sid.Type.ACCOUNT);
        final Sid parent = Sid.generate(Sid.Type.CALL);
        final Sid phone = Sid.generate(Sid.Type.PHONE_NUMBER);
        final URI url = URI.create("http://127.0.0.1:8080/restcomm/demos/hello-world.xml");
        final CallDetailRecord.Builder builder = CallDetailRecord.builder();
        builder.setSid(sid);
        builder.setInstanceId(instanceId.toString());
        builder.setParentCallSid(parent);
        builder.setDateCreated(DateTime.now());
        builder.setAccountSid(account);
        builder.setTo("+12223334444");
        builder.setFrom("+17778889999");
        builder.setPhoneNumberSid(phone);
        builder.setStatus("queued");
        builder.setStartTime(DateTime.now());
        builder.setEndTime(DateTime.now());
        builder.setDuration(1);
        builder.setPrice(new BigDecimal("0.00"));
        builder.setPriceUnit(Currency.getInstance("USD"));
        builder.setDirection("outbound-api");
        builder.setApiVersion("2012-04-24");
        builder.setCallerName("Alice");
        builder.setUri(url);
        CallDetailRecord cdr = builder.build();
        final CallDetailRecordsDao cdrs = manager.getCallDetailRecordsDao();
        // Create a new CDR in the data store.
        cdrs.addCallDetailRecord(cdr);
        // Read the CDR from the data store.
        CallDetailRecord result = cdrs.getCallDetailRecord(sid);
        // Validate the results.
        assertTrue(result.getSid().equals(cdr.getSid()));
        assertTrue(result.getParentCallSid().equals(cdr.getParentCallSid()));
        assertTrue(result.getDateCreated().equals(cdr.getDateCreated()));
        assertTrue(result.getAccountSid().equals(cdr.getAccountSid()));
        assertTrue(result.getTo().equals(cdr.getTo()));
        assertTrue(result.getFrom().equals(cdr.getFrom()));
        assertTrue(result.getPhoneNumberSid().equals(cdr.getPhoneNumberSid()));
        assertTrue(result.getStatus().equals(cdr.getStatus()));
        assertTrue(result.getStartTime().equals(cdr.getStartTime()));
        assertTrue(result.getEndTime().equals(cdr.getEndTime()));
        assertTrue(result.getDuration().equals(cdr.getDuration()));
        assertTrue(result.getPrice().equals(cdr.getPrice()));
        assertTrue(result.getPriceUnit().equals(cdr.getPriceUnit()));
        assertTrue(result.getDirection().equals(cdr.getDirection()));
        assertTrue(result.getApiVersion().equals(cdr.getApiVersion()));
        assertTrue(result.getCallerName().equals(cdr.getCallerName()));
        assertTrue(result.getUri().equals(cdr.getUri()));
        // Update the CDR.
        cdr = cdr.setDuration(2);
        cdr = cdr.setPrice(new BigDecimal("1.00"));
        cdr = cdr.setStatus("in-progress");
        cdrs.updateCallDetailRecord(cdr);
        // Read the updated CDR from the data store.
        result = cdrs.getCallDetailRecord(sid);
        // Validate the results.
        assertTrue(result.getStatus().equals(cdr.getStatus()));
        assertTrue(result.getDuration().equals(cdr.getDuration()));
        assertTrue(result.getPrice().equals(cdr.getPrice()));
        assertTrue(result.getPriceUnit().equals(cdr.getPriceUnit()));
        // Delete the CDR.
        cdrs.removeCallDetailRecord(sid);
        // Validate that the CDR was removed.
        assertNull(cdrs.getCallDetailRecord(sid));
    }

    @Test
    public void testReadDeleteByAccount() {
        final Sid sid = Sid.generate(Sid.Type.CALL);
        final String instanceId = Sid.generate(Sid.Type.INSTANCE).toString();
        final Sid account = Sid.generate(Sid.Type.ACCOUNT);
        final Sid parent = Sid.generate(Sid.Type.CALL);
        final Sid phone = Sid.generate(Sid.Type.PHONE_NUMBER);
        final URI url = URI.create("http://127.0.0.1:8080/restcomm/demos/hello-world.xml");
        final CallDetailRecord.Builder builder = CallDetailRecord.builder();
        builder.setSid(sid);
        builder.setInstanceId(instanceId);
        builder.setParentCallSid(parent);
        builder.setDateCreated(DateTime.now());
        builder.setAccountSid(account);
        builder.setTo("+12223334444");
        builder.setFrom("+17778889999");
        builder.setPhoneNumberSid(phone);
        builder.setStatus("queued");
        builder.setStartTime(DateTime.now());
        builder.setEndTime(DateTime.now());
        builder.setDuration(1);
        builder.setPrice(new BigDecimal("0.00"));
        builder.setPriceUnit(Currency.getInstance("JPY"));
        builder.setDirection("outbound-api");
        builder.setApiVersion("2012-04-24");
        builder.setCallerName("Alice");
        builder.setUri(url);
        CallDetailRecord cdr = builder.build();
        final CallDetailRecordsDao cdrs = manager.getCallDetailRecordsDao();
        // Create a new CDR in the data store.
        cdrs.addCallDetailRecord(cdr);
        // Validate the results.
        assertEquals(1, cdrs.getCallDetailRecordsByAccountSid(account).size());
        // Delete the CDR.
        cdrs.removeCallDetailRecords(account);
        // Validate that the CDRs were removed.
        assertEquals(0, cdrs.getCallDetailRecordsByAccountSid(account).size());
    }

    public void testReadByRecipient() {
        final Sid sid = Sid.generate(Sid.Type.CALL);
        final String instanceId = Sid.generate(Sid.Type.INSTANCE).toString();
        final Sid account = Sid.generate(Sid.Type.ACCOUNT);
        final Sid parent = Sid.generate(Sid.Type.CALL);
        final Sid phone = Sid.generate(Sid.Type.PHONE_NUMBER);
        final URI url = URI.create("http://127.0.0.1:8080/restcomm/demos/hello-world.xml");
        final CallDetailRecord.Builder builder = CallDetailRecord.builder();
        builder.setSid(sid);
        builder.setInstanceId(instanceId);
        builder.setParentCallSid(parent);
        builder.setDateCreated(DateTime.now());
        builder.setAccountSid(account);
        builder.setTo("+12223334444");
        builder.setFrom("+17778889999");
        builder.setPhoneNumberSid(phone);
        builder.setStatus("queued");
        builder.setStartTime(DateTime.now());
        builder.setEndTime(DateTime.now());
        builder.setDuration(1);
        builder.setPrice(new BigDecimal("0.00"));
        builder.setPriceUnit(Currency.getInstance("EUR"));
        builder.setDirection("outbound-api");
        builder.setApiVersion("2012-04-24");
        builder.setCallerName("Alice");
        builder.setUri(url);
        CallDetailRecord cdr = builder.build();
        final CallDetailRecordsDao cdrs = manager.getCallDetailRecordsDao();
        // Create a new CDR in the data store.
        cdrs.addCallDetailRecord(cdr);
        // Validate the results.
        assertEquals(1, cdrs.getCallDetailRecordsByRecipient("+12223334444").size());
        // Delete the CDR.
        cdrs.removeCallDetailRecord(sid);
        // Validate that the CDRs were removed.
        assertNull(cdrs.getCallDetailRecord(sid) == null);
    }

    public void testReadBySender() {
        final Sid sid = Sid.generate(Sid.Type.CALL);
        final String instanceId = Sid.generate(Sid.Type.INSTANCE).toString();
        final Sid account = Sid.generate(Sid.Type.ACCOUNT);
        final Sid parent = Sid.generate(Sid.Type.CALL);
        final Sid phone = Sid.generate(Sid.Type.PHONE_NUMBER);
        final URI url = URI.create("http://127.0.0.1:8080/restcomm/demos/hello-world.xml");
        final CallDetailRecord.Builder builder = CallDetailRecord.builder();
        builder.setSid(sid);
        builder.setInstanceId(instanceId);
        builder.setParentCallSid(parent);
        builder.setDateCreated(DateTime.now());
        builder.setAccountSid(account);
        builder.setTo("+12223334444");
        builder.setFrom("+17778889999");
        builder.setPhoneNumberSid(phone);
        builder.setStatus("queued");
        builder.setStartTime(DateTime.now());
        builder.setEndTime(DateTime.now());
        builder.setDuration(1);
        builder.setPrice(new BigDecimal("0.00"));
        builder.setPriceUnit(Currency.getInstance("USD"));
        builder.setDirection("outbound-api");
        builder.setApiVersion("2012-04-24");
        builder.setCallerName("Alice");
        builder.setUri(url);
        CallDetailRecord cdr = builder.build();
        final CallDetailRecordsDao cdrs = manager.getCallDetailRecordsDao();
        // Create a new CDR in the data store.
        cdrs.addCallDetailRecord(cdr);
        // Validate the results.
        assertEquals(1, cdrs.getCallDetailRecordsByRecipient("+17778889999").size());
        // Delete the CDR.
        cdrs.removeCallDetailRecord(sid);
        // Validate that the CDRs were removed.
        assertNull(cdrs.getCallDetailRecord(sid));
    }

    public void testReadByStatus() {
        final Sid sid = Sid.generate(Sid.Type.CALL);
        final String instanceId = Sid.generate(Sid.Type.INSTANCE).toString();
        final Sid account = Sid.generate(Sid.Type.ACCOUNT);
        final Sid parent = Sid.generate(Sid.Type.CALL);
        final Sid phone = Sid.generate(Sid.Type.PHONE_NUMBER);
        final URI url = URI.create("http://127.0.0.1:8080/restcomm/demos/hello-world.xml");
        final CallDetailRecord.Builder builder = CallDetailRecord.builder();
        builder.setSid(sid);
        builder.setInstanceId(instanceId);
        builder.setParentCallSid(parent);
        builder.setDateCreated(DateTime.now());
        builder.setAccountSid(account);
        builder.setTo("+12223334444");
        builder.setFrom("+17778889999");
        builder.setPhoneNumberSid(phone);
        builder.setStatus("queued");
        builder.setStartTime(DateTime.now());
        builder.setEndTime(DateTime.now());
        builder.setDuration(1);
        builder.setPrice(new BigDecimal("0.00"));
        builder.setPriceUnit(Currency.getInstance("CZK"));
        builder.setDirection("outbound-api");
        builder.setApiVersion("2012-04-24");
        builder.setCallerName("Alice");
        builder.setUri(url);
        CallDetailRecord cdr = builder.build();
        final CallDetailRecordsDao cdrs = manager.getCallDetailRecordsDao();
        // Create a new CDR in the data store.
        cdrs.addCallDetailRecord(cdr);
        // Validate the results.
        assertEquals(1, cdrs.getCallDetailRecordsByStatus("queued").size());
        // Delete the CDR.
        cdrs.removeCallDetailRecord(sid);
        // Validate that the CDRs were removed.
        assertNull(cdrs.getCallDetailRecord(sid));
    }

    @Test
    public void testReadByStartTime() {
        final Sid sid = Sid.generate(Sid.Type.CALL);
        final String instanceId = Sid.generate(Sid.Type.INSTANCE).toString();
        final Sid account = Sid.generate(Sid.Type.ACCOUNT);
        final Sid parent = Sid.generate(Sid.Type.CALL);
        final Sid phone = Sid.generate(Sid.Type.PHONE_NUMBER);
        final URI url = URI.create("http://127.0.0.1:8080/restcomm/demos/hello-world.xml");
        final CallDetailRecord.Builder builder = CallDetailRecord.builder();
        builder.setSid(sid);
        builder.setInstanceId(instanceId);
        builder.setParentCallSid(parent);
        builder.setDateCreated(DateTime.now());
        builder.setAccountSid(account);
        builder.setTo("+12223334444");
        builder.setFrom("+17778889999");
        builder.setPhoneNumberSid(phone);
        builder.setStatus("queued");
        final DateTime now = DateTime.now();
        builder.setStartTime(now);
        builder.setEndTime(now);
        builder.setDuration(1);
        builder.setPrice(new BigDecimal("0.00"));
        builder.setPriceUnit(Currency.getInstance("AUD"));
        builder.setDirection("outbound-api");
        builder.setApiVersion("2012-04-24");
        builder.setCallerName("Alice");
        builder.setUri(url);
        CallDetailRecord cdr = builder.build();
        final CallDetailRecordsDao cdrs = manager.getCallDetailRecordsDao();
        // Create a new CDR in the data store.
        cdrs.addCallDetailRecord(cdr);
        // Validate the results.
        assertEquals(1, cdrs.getCallDetailRecordsByStartTime(now).size());
        // Delete the CDR.
        cdrs.removeCallDetailRecord(sid);
        // Validate that the CDRs were removed.
        assertNull(cdrs.getCallDetailRecord(sid));
    }

    @Test
    public void testReadByEndTime() {
        final Sid sid = Sid.generate(Sid.Type.CALL);
        final String instanceId = Sid.generate(Sid.Type.INSTANCE).toString();
        final Sid account = Sid.generate(Sid.Type.ACCOUNT);
        final Sid parent = Sid.generate(Sid.Type.CALL);
        final Sid phone = Sid.generate(Sid.Type.PHONE_NUMBER);
        final URI url = URI.create("http://127.0.0.1:8080/restcomm/demos/hello-world.xml");
        final CallDetailRecord.Builder builder = CallDetailRecord.builder();
        builder.setSid(sid);
        builder.setInstanceId(instanceId);
        builder.setParentCallSid(parent);
        builder.setDateCreated(DateTime.now());
        builder.setAccountSid(account);
        builder.setTo("+12223334444");
        builder.setFrom("+17778889999");
        builder.setPhoneNumberSid(phone);
        builder.setStatus("queued");
        final DateTime now = DateTime.now();
        builder.setStartTime(now);
        builder.setEndTime(now);
        builder.setDuration(1);
        builder.setPrice(new BigDecimal("0.00"));
        builder.setPriceUnit(Currency.getInstance("AUD"));
        builder.setDirection("outbound-api");
        builder.setApiVersion("2012-04-24");
        builder.setCallerName("Alice");
        builder.setUri(url);
        builder.setMsId("msId");
        CallDetailRecord cdr = builder.build();
        final CallDetailRecordsDao cdrs = manager.getCallDetailRecordsDao();
        int beforeAdding = cdrs.getCallDetailRecordsByEndTime(now).size();
        // Create a new CDR in the data store.
        cdrs.addCallDetailRecord(cdr);
        // Validate the results, including the ones matching in the script(10)
        assertEquals(beforeAdding + 1, cdrs.getCallDetailRecordsByEndTime(now).size());
        // Delete the CDR.
        cdrs.removeCallDetailRecord(sid);
        // Validate that the CDRs were removed.
        assertNull(cdrs.getCallDetailRecord(sid));
    }

    public void testReadByParentCall() {
        final Sid sid = Sid.generate(Sid.Type.CALL);
        final String instanceId = Sid.generate(Sid.Type.INSTANCE).toString();
        final Sid account = Sid.generate(Sid.Type.ACCOUNT);
        final Sid parent = Sid.generate(Sid.Type.CALL);
        final Sid phone = Sid.generate(Sid.Type.PHONE_NUMBER);
        final URI url = URI.create("http://127.0.0.1:8080/restcomm/demos/hello-world.xml");
        final CallDetailRecord.Builder builder = CallDetailRecord.builder();
        builder.setSid(sid);
        builder.setInstanceId(instanceId);
        builder.setParentCallSid(parent);
        builder.setDateCreated(DateTime.now());
        builder.setAccountSid(account);
        builder.setTo("+12223334444");
        builder.setFrom("+17778889999");
        builder.setPhoneNumberSid(phone);
        builder.setStatus("queued");
        builder.setStartTime(DateTime.now());
        builder.setEndTime(DateTime.now());
        builder.setDuration(1);
        builder.setPrice(new BigDecimal("0.00"));
        builder.setPriceUnit(Currency.getInstance("GBP"));
        builder.setDirection("outbound-api");
        builder.setApiVersion("2012-04-24");
        builder.setCallerName("Alice");
        builder.setUri(url);
        CallDetailRecord cdr = builder.build();
        final CallDetailRecordsDao cdrs = manager.getCallDetailRecordsDao();
        // Create a new CDR in the data store.
        cdrs.addCallDetailRecord(cdr);
        // Validate the results.
        assertEquals(1, cdrs.getCallDetailRecordsByParentCall(parent).size());
        // Delete the CDR.
        cdrs.removeCallDetailRecord(sid);
        // Validate that the CDRs were removed.
        assertNull(cdrs.getCallDetailRecord(sid));
    }

    @Test
    public void retrieveAccountCdrsRecursively() throws ParseException {
        CallDetailRecordsDao dao = manager.getCallDetailRecordsDao();

        /*
            Sample data summary

                100 calls in total
                12 calls belong to AC00000000000000000000000000000000
                5 calls belong to AC11111111111111111111111111111111
                8 calls belong to AC22222222222222222222222222222222
         */

        // read from a single account but using the 'accountSidSet' interface
        List<String> accountSidSet = new ArrayList<String>();
        accountSidSet.add("AC00000000000000000000000000000000");
        CallDetailRecordFilter filter = new CallDetailRecordFilter(null, accountSidSet, null, null, null, null, null, null, null, null, null);
        Assert.assertEquals(12, dao.getTotalCallDetailRecords(filter).intValue());
        // read cdrs of three accounts
        accountSidSet.add("AC00000000000000000000000000000000");
        accountSidSet.add("AC11111111111111111111111111111111");
        accountSidSet.add("AC22222222222222222222222222222222");
        Assert.assertEquals(25, dao.getTotalCallDetailRecords(filter).intValue());
        // pass an empty accountSid set
        accountSidSet.clear();
        Assert.assertEquals(0, dao.getTotalCallDetailRecords(filter).intValue());
        // if both an accountSid and a accountSid set are passed, only accountSidSet is taken into account
        filter = new CallDetailRecordFilter("ACae6e420f425248d6a26948c17a9e2acf", accountSidSet, null, null, null, null, null, null, null, null, null);
        accountSidSet.add("AC00000000000000000000000000000000");
        accountSidSet.add("AC11111111111111111111111111111111");
        accountSidSet.add("AC22222222222222222222222222222222");
        Assert.assertEquals(25, dao.getTotalCallDetailRecords(filter).intValue());
        // if no (null) accountSidSet is passed the method still works
        filter = new CallDetailRecordFilter("AC00000000000000000000000000000000", null, null, null, null, null, null, null, null, null, null);
        Assert.assertEquals(12, dao.getTotalCallDetailRecords(filter).intValue());
    }

    @Test
    public void filterWithDateSorting() throws ParseException {
        CallDetailRecordsDao dao = manager.getCallDetailRecordsDao();
        CallDetailRecordFilter.Builder builder = new CallDetailRecordFilter.Builder();
        List<String> accountSidSet = new ArrayList<String>();
        accountSidSet.add("AC00000000000000000000000000000000");
        builder.byAccountSidSet(accountSidSet);
        builder.sortedByDate(Sorting.Direction.ASC);
        CallDetailRecordFilter filter = builder.build();
        List<CallDetailRecord> callDetailRecords = dao.getCallDetailRecords(filter);
        assertEquals(12, callDetailRecords.size());
        final DateTime min = DateTime.parse("2013-07-30T15:08:21.228");
        final DateTime max = DateTime.parse("2013-09-10T14:03:36.496");
        assertEquals(min.compareTo(callDetailRecords.get(0).getDateCreated()), 0);
        assertEquals(max.compareTo(callDetailRecords.get(11).getDateCreated()), 0);

        builder.sortedByDate(Sorting.Direction.DESC);
        filter = builder.build();
        callDetailRecords = dao.getCallDetailRecords(filter);
        assertEquals(max.compareTo(callDetailRecords.get(0).getDateCreated()), 0);
        assertEquals(min.compareTo(callDetailRecords.get(11).getDateCreated()), 0);
    }

    @Test
    public void filterWithFromSorting() throws ParseException {
        CallDetailRecordsDao dao = manager.getCallDetailRecordsDao();
        CallDetailRecordFilter.Builder builder = new CallDetailRecordFilter.Builder();
        List<String> accountSidSet = new ArrayList<String>();
        accountSidSet.add("AC00000000000000000000000000000000");
        builder.byAccountSidSet(accountSidSet);
        builder.sortedByFrom(Sorting.Direction.ASC);
        CallDetailRecordFilter filter = builder.build();
        List<CallDetailRecord> callDetailRecords = dao.getCallDetailRecords(filter);
        assertEquals(12, callDetailRecords.size());
        assertEquals("+1011420534008567", callDetailRecords.get(0).getFrom());
        assertEquals("Anonymous", callDetailRecords.get(11).getFrom());

        builder.sortedByFrom(Sorting.Direction.DESC);
        filter = builder.build();
        callDetailRecords = dao.getCallDetailRecords(filter);
        assertEquals("Anonymous", callDetailRecords.get(0).getFrom());
        assertEquals("+1011420534008567", callDetailRecords.get(11).getFrom());
    }

    @Test
    public void filterWithToSorting() throws ParseException {
        CallDetailRecordsDao dao = manager.getCallDetailRecordsDao();
        CallDetailRecordFilter.Builder builder = new CallDetailRecordFilter.Builder();
        List<String> accountSidSet = new ArrayList<String>();
        accountSidSet.add("AC00000000000000000000000000000000");
        builder.byAccountSidSet(accountSidSet);
        builder.sortedByTo(Sorting.Direction.ASC);
        CallDetailRecordFilter filter = builder.build();
        List<CallDetailRecord> callDetailRecords = dao.getCallDetailRecords(filter);
        assertEquals(12, callDetailRecords.size());
        assertEquals("+13052406432", callDetailRecords.get(0).getTo());
        assertEquals("+17863580884", callDetailRecords.get(11).getTo());

        builder.sortedByTo(Sorting.Direction.DESC);
        filter = builder.build();
        callDetailRecords = dao.getCallDetailRecords(filter);
        assertEquals("+17863580884", callDetailRecords.get(0).getTo());
        assertEquals("+13052406432", callDetailRecords.get(11).getTo());
    }

    @Test
    public void filterWithDirectionSorting() throws ParseException {
        CallDetailRecordsDao dao = manager.getCallDetailRecordsDao();
        CallDetailRecordFilter.Builder builder = new CallDetailRecordFilter.Builder();
        List<String> accountSidSet = new ArrayList<String>();
        accountSidSet.add("AC00000000000000000000000000000000");
        builder.byAccountSidSet(accountSidSet);
        builder.sortedByDirection(Sorting.Direction.ASC);
        CallDetailRecordFilter filter = builder.build();
        List<CallDetailRecord> callDetailRecords = dao.getCallDetailRecords(filter);
        assertEquals(12, callDetailRecords.size());
        assertEquals("inbound", callDetailRecords.get(0).getDirection());
        assertEquals("outbound", callDetailRecords.get(11).getDirection());

        builder.sortedByDirection(Sorting.Direction.DESC);
        filter = builder.build();
        callDetailRecords = dao.getCallDetailRecords(filter);
        assertEquals("outbound", callDetailRecords.get(0).getDirection());
        assertEquals("inbound", callDetailRecords.get(11).getDirection());
    }

    @Test
    public void filterWithStatusSorting() throws ParseException {
        CallDetailRecordsDao dao = manager.getCallDetailRecordsDao();
        CallDetailRecordFilter.Builder builder = new CallDetailRecordFilter.Builder();
        List<String> accountSidSet = new ArrayList<String>();
        accountSidSet.add("AC00000000000000000000000000000000");
        builder.byAccountSidSet(accountSidSet);
        builder.sortedByStatus(Sorting.Direction.ASC);
        CallDetailRecordFilter filter = builder.build();
        List<CallDetailRecord> callDetailRecords = dao.getCallDetailRecords(filter);
        assertEquals(12, callDetailRecords.size());
        assertEquals("completed", callDetailRecords.get(0).getStatus());
        assertEquals("in-progress", callDetailRecords.get(11).getStatus());

        builder.sortedByStatus(Sorting.Direction.DESC);
        filter = builder.build();
        callDetailRecords = dao.getCallDetailRecords(filter);
        assertEquals("in-progress", callDetailRecords.get(0).getStatus());
        assertEquals("completed", callDetailRecords.get(11).getStatus());
    }

    @Test
    public void filterWithDurationSorting() throws ParseException {
        CallDetailRecordsDao dao = manager.getCallDetailRecordsDao();
        CallDetailRecordFilter.Builder builder = new CallDetailRecordFilter.Builder();
        List<String> accountSidSet = new ArrayList<String>();
        accountSidSet.add("AC00000000000000000000000000000000");
        builder.byAccountSidSet(accountSidSet);
        builder.sortedByDuration(Sorting.Direction.ASC);
        CallDetailRecordFilter filter = builder.build();
        List<CallDetailRecord> callDetailRecords = dao.getCallDetailRecords(filter);
        assertEquals(12, callDetailRecords.size());
        assertEquals("1", callDetailRecords.get(0).getDuration().toString());
        assertEquals("44", callDetailRecords.get(11).getDuration().toString());

        builder.sortedByDuration(Sorting.Direction.DESC);
        filter = builder.build();
        callDetailRecords = dao.getCallDetailRecords(filter);
        assertEquals("44", callDetailRecords.get(0).getDuration().toString());
        assertEquals("1", callDetailRecords.get(11).getDuration().toString());
    }

    @Test
    public void filterWithPriceSorting() throws ParseException {
        CallDetailRecordsDao dao = manager.getCallDetailRecordsDao();
        CallDetailRecordFilter.Builder builder = new CallDetailRecordFilter.Builder();
        List<String> accountSidSet = new ArrayList<String>();
        accountSidSet.add("AC00000000000000000000000000000000");
        builder.byAccountSidSet(accountSidSet);
        builder.sortedByPrice(Sorting.Direction.ASC);
        CallDetailRecordFilter filter = builder.build();
        List<CallDetailRecord> callDetailRecords = dao.getCallDetailRecords(filter);
        assertEquals(12, callDetailRecords.size());
        assertEquals("0.00", callDetailRecords.get(0).getPrice().toString());
        assertEquals("120.00", callDetailRecords.get(11).getPrice().toString());

        builder.sortedByPrice(Sorting.Direction.DESC);
        filter = builder.build();
        callDetailRecords = dao.getCallDetailRecords(filter);
        assertEquals("120.00", callDetailRecords.get(0).getPrice().toString());
        assertEquals("0.00", callDetailRecords.get(11).getPrice().toString());
    }
}
