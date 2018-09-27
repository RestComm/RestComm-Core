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

import java.io.InputStream;
import java.net.URI;
import java.util.List;

import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;

import org.junit.After;
import static org.junit.Assert.*;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import org.restcomm.connect.dao.IncomingPhoneNumbersDao;
import org.restcomm.connect.dao.entities.IncomingPhoneNumber;
import org.restcomm.connect.dao.entities.IncomingPhoneNumberFilter;
import org.restcomm.connect.commons.dao.Sid;
import org.restcomm.connect.dao.common.Sorting;
import org.restcomm.connect.dao.entities.SearchFilterMode;

/**
 * @author quintana.thomas@gmail.com (Thomas Quintana)
 */
public class IncomingPhoneNumbersDaoTest {
    private static MybatisDaoManager manager;

    public IncomingPhoneNumbersDaoTest() {
        super();
    }

    @Before
    public void before() {
        final InputStream data = getClass().getResourceAsStream("/mybatis.xml");
        final SqlSessionFactoryBuilder builder = new SqlSessionFactoryBuilder();
        final SqlSessionFactory factory = builder.build(data);
        manager = new MybatisDaoManager();
        manager.start(factory);
    }

    @After
    public void after() {
        manager.shutdown();
    }

    @Test
    public void createReadUpdateDelete() {
        final Sid sid = Sid.generate(Sid.Type.PHONE_NUMBER);
        Sid account = Sid.generate(Sid.Type.ACCOUNT);
        Sid application = Sid.generate(Sid.Type.APPLICATION);
        URI url = URI.create("http://127.0.0.1:8080/restcomm/demos/hello-world.xml");
        String method = "GET";
        final IncomingPhoneNumber.Builder builder = IncomingPhoneNumber.builder();
        builder.setSid(sid);
        builder.setFriendlyName("Incoming Phone Number Test");
        builder.setAccountSid(account);
        builder.setPhoneNumber("+12223334444");
        builder.setApiVersion("2012-04-24");
        builder.setHasVoiceCallerIdLookup(false);
        builder.setVoiceUrl(url);
        builder.setCost("0.50");
        builder.setVoiceMethod(method);
        builder.setVoiceFallbackUrl(url);
        builder.setVoiceFallbackMethod(method);
        builder.setStatusCallback(url);
        builder.setStatusCallbackMethod(method);
        builder.setVoiceApplicationSid(application);
        builder.setSmsUrl(url);
        builder.setSmsMethod(method);
        builder.setSmsFallbackUrl(url);
        builder.setSmsFallbackMethod(method);
        builder.setSmsApplicationSid(application);
        builder.setUri(url);
        builder.setOrganizationSid(Sid.generate(Sid.Type.ORGANIZATION));
        IncomingPhoneNumber number = builder.build();
        final IncomingPhoneNumbersDao numbers = manager.getIncomingPhoneNumbersDao();
        // Create a new incoming phone number in the data store.
        numbers.addIncomingPhoneNumber(number);
        // Read the incoming phone number from the data store.
        IncomingPhoneNumber result = numbers.getIncomingPhoneNumber(sid);
        // Validate the results.
        assertTrue(result.getSid().equals(number.getSid()));
        assertTrue(result.getFriendlyName().equals(number.getFriendlyName()));
        assertTrue(result.getAccountSid().equals(number.getAccountSid()));
        assertTrue(result.getPhoneNumber().equals(number.getPhoneNumber()));
        assertTrue(result.getApiVersion().equals(number.getApiVersion()));
        assertFalse(result.hasVoiceCallerIdLookup());
        assertTrue(result.getVoiceUrl().equals(number.getVoiceUrl()));
        assertTrue(result.getVoiceMethod().equals(number.getVoiceMethod()));
        assertTrue(result.getVoiceFallbackUrl().equals(number.getVoiceFallbackUrl()));
        assertTrue(result.getVoiceFallbackMethod().equals(number.getVoiceFallbackMethod()));
        assertTrue(result.getStatusCallback().equals(number.getStatusCallback()));
        assertTrue(result.getStatusCallbackMethod().equals(number.getStatusCallbackMethod()));
        assertTrue(result.getVoiceApplicationSid().equals(number.getVoiceApplicationSid()));
        assertTrue(result.getSmsUrl().equals(number.getSmsUrl()));
        assertTrue(result.getSmsMethod().equals(number.getSmsMethod()));
        assertTrue(result.getSmsFallbackUrl().equals(number.getSmsFallbackUrl()));
        assertTrue(result.getSmsFallbackMethod().equals(number.getSmsFallbackMethod()));
        assertTrue(result.getSmsApplicationSid().equals(number.getSmsApplicationSid()));
        assertTrue(result.getUri().equals(number.getUri()));
        // Update the incoming phone number.
        application = Sid.generate(Sid.Type.APPLICATION);
        url = URI.create("http://127.0.0.1:8080/restcomm/demos/world-hello.xml");
        method = "POST";
        number.setFriendlyName("Test Application");
        number.setHasVoiceCallerIdLookup(true);
        number.setVoiceUrl(url);
        number.setVoiceMethod(method);
        number.setVoiceFallbackUrl(url);
        number.setVoiceFallbackMethod(method);
        number.setStatusCallback(url);
        number.setStatusCallbackMethod(method);
        number.setVoiceApplicationSid(application);
        number.setSmsUrl(url);
        number.setCost("0.50");
        number.setSmsMethod(method);
        number.setSmsFallbackUrl(url);
        number.setSmsFallbackMethod(method);
        number.setSmsApplicationSid(application);
        Sid newOrganizationSid = Sid.generate(Sid.Type.ORGANIZATION);
        number.setOrganizationSid(newOrganizationSid);
        numbers.updateIncomingPhoneNumber(number);
        // Read the updated application from the data store.
        result = numbers.getIncomingPhoneNumber(sid);
        // Validate the results.
        assertTrue(result.getSid().equals(number.getSid()));
        assertTrue(result.getFriendlyName().equals(number.getFriendlyName()));
        assertTrue(result.getAccountSid().equals(number.getAccountSid()));
        assertTrue(result.getPhoneNumber().equals(number.getPhoneNumber()));
        assertTrue(result.getApiVersion().equals(number.getApiVersion()));
        assertTrue(result.hasVoiceCallerIdLookup());
        assertTrue(result.getVoiceUrl().equals(number.getVoiceUrl()));
        assertTrue(result.getVoiceMethod().equals(number.getVoiceMethod()));
        assertTrue(result.getVoiceFallbackUrl().equals(number.getVoiceFallbackUrl()));
        assertTrue(result.getVoiceFallbackMethod().equals(number.getVoiceFallbackMethod()));
        assertTrue(result.getStatusCallback().equals(number.getStatusCallback()));
        assertTrue(result.getStatusCallbackMethod().equals(number.getStatusCallbackMethod()));
        assertTrue(result.getVoiceApplicationSid().equals(number.getVoiceApplicationSid()));
        assertTrue(result.getSmsUrl().equals(number.getSmsUrl()));
        assertTrue(result.getSmsMethod().equals(number.getSmsMethod()));
        assertTrue(result.getSmsFallbackUrl().equals(number.getSmsFallbackUrl()));
        assertTrue(result.getSmsFallbackMethod().equals(number.getSmsFallbackMethod()));
        assertTrue(result.getSmsApplicationSid().equals(number.getSmsApplicationSid()));
        assertTrue(result.getUri().equals(number.getUri()));
        assertEquals(newOrganizationSid.toString(), result.getOrganizationSid().toString());
        // Delete the incoming phone number.
        numbers.removeIncomingPhoneNumber(sid);
        // Validate that the incoming phone number was removed.
        assertNull(numbers.getIncomingPhoneNumber(sid));
    }

    @Test
    public void applicationFriendlyNameReturned() {
        final IncomingPhoneNumbersDao dao = manager.getIncomingPhoneNumbersDao();
        IncomingPhoneNumberFilter.Builder filterBuilder = IncomingPhoneNumberFilter.Builder.builder();
        filterBuilder.byAccountSid("ACae6e420f425248d6a26948c17a9e2acf");
        filterBuilder.sortedByPhoneNumber(Sorting.Direction.ASC);
        filterBuilder.limited(50, 0);
        List<IncomingPhoneNumber> phoneNumbers = dao.getIncomingPhoneNumbersByFilter(filterBuilder.build());
        Assert.assertEquals("Only a single phone number expected",1, phoneNumbers.size());
        IncomingPhoneNumber number = phoneNumbers.get(0);
        Assert.assertEquals("app0", number.getVoiceApplicationName());
        Assert.assertEquals("app1", number.getSmsApplicationName());
        Assert.assertEquals("app2", number.getUssdApplicationName());
    }

    @Test
    public void getByPhoneNumber() {
        final Sid sid = Sid.generate(Sid.Type.PHONE_NUMBER);
        Sid account = Sid.generate(Sid.Type.ACCOUNT);
        Sid application = Sid.generate(Sid.Type.APPLICATION);
        URI url = URI.create("http://127.0.0.1:8080/restcomm/demos/hello-world.xml");
        String method = "GET";
        final IncomingPhoneNumber.Builder builder = IncomingPhoneNumber.builder();
        builder.setSid(sid);
        builder.setFriendlyName("Incoming Phone Number Test");
        builder.setAccountSid(account);
        builder.setPhoneNumber("+12223334444");
        builder.setApiVersion("2012-04-24");
        builder.setHasVoiceCallerIdLookup(false);
        builder.setVoiceUrl(url);
        builder.setVoiceMethod(method);
        builder.setVoiceFallbackUrl(url);
        builder.setVoiceFallbackMethod(method);
        builder.setStatusCallback(url);
        builder.setStatusCallbackMethod(method);
        builder.setVoiceApplicationSid(application);
        builder.setSmsUrl(url);
        builder.setSmsMethod(method);
        builder.setSmsFallbackUrl(url);
        builder.setSmsFallbackMethod(method);
        builder.setSmsApplicationSid(application);
        builder.setUri(url);
        builder.setOrganizationSid(Sid.generate(Sid.Type.ORGANIZATION));
        IncomingPhoneNumber number = builder.build();
        final IncomingPhoneNumbersDao numbers = manager.getIncomingPhoneNumbersDao();
        // Create a new incoming phone number in the data store.
        numbers.addIncomingPhoneNumber(number);
        // Read the incoming phone number from the data store.
        IncomingPhoneNumberFilter.Builder filterBuilder = IncomingPhoneNumberFilter.Builder.builder();
        filterBuilder.byPhoneNumber("+12223334444");
        filterBuilder.byAccountSid(account.toString());
        filterBuilder.sortedByfriendly(Sorting.Direction.ASC);
        List<IncomingPhoneNumber> incomingPhoneNumbers = numbers.getIncomingPhoneNumbersByFilter(filterBuilder.build());
        assertNotNull (incomingPhoneNumbers);
        assertEquals (1, incomingPhoneNumbers.size());
        IncomingPhoneNumber result = incomingPhoneNumbers.get(0);
        assertEquals(number.getSid(), result.getSid());

        //use wildcard mode now
        filterBuilder = IncomingPhoneNumberFilter.Builder.builder();
        filterBuilder.byPhoneNumber("2223334444");
        filterBuilder.byAccountSid(account.toString());
        filterBuilder.usingMode(SearchFilterMode.WILDCARD_MATCH);
        filterBuilder.sortedByPhoneNumber(Sorting.Direction.DESC);
        incomingPhoneNumbers = numbers.getIncomingPhoneNumbersByFilter(filterBuilder.build());
        assertNotNull (incomingPhoneNumbers);
        assertEquals (1, incomingPhoneNumbers.size());

        // Delete the incoming phone number.
        numbers.removeIncomingPhoneNumber(sid);
        // Validate that the incoming phone number was removed.
        assertNull(numbers.getIncomingPhoneNumber(sid) );
    }

    @Test
    public void getByPhoneNumberUAndOrg() {
        final Sid sid = Sid.generate(Sid.Type.PHONE_NUMBER);
        Sid account = Sid.generate(Sid.Type.ACCOUNT);
        Sid org1 = Sid.generate(Sid.Type.ORGANIZATION);
        final Sid sid2 = Sid.generate(Sid.Type.PHONE_NUMBER);
        Sid account2 = Sid.generate(Sid.Type.ACCOUNT);
        Sid org2 = Sid.generate(Sid.Type.ORGANIZATION);

        Sid application = Sid.generate(Sid.Type.APPLICATION);
        URI url = URI.create("http://127.0.0.1:8080/restcomm/demos/hello-world.xml");
        String method = "GET";
        final IncomingPhoneNumber.Builder builder = IncomingPhoneNumber.builder();
        builder.setSid(sid);
        builder.setFriendlyName("Incoming Phone Number Test");
        builder.setAccountSid(account);
        builder.setPhoneNumber("+12223334444");
        builder.setApiVersion("2012-04-24");
        builder.setHasVoiceCallerIdLookup(false);
        builder.setVoiceUrl(url);
        builder.setVoiceMethod(method);
        builder.setVoiceFallbackUrl(url);
        builder.setVoiceFallbackMethod(method);
        builder.setStatusCallback(url);
        builder.setStatusCallbackMethod(method);
        builder.setVoiceApplicationSid(application);
        builder.setSmsUrl(url);
        builder.setSmsMethod(method);
        builder.setSmsFallbackUrl(url);
        builder.setSmsFallbackMethod(method);
        builder.setSmsApplicationSid(application);
        builder.setUri(url);
        builder.setOrganizationSid(org1);
        builder.setPureSip(Boolean.FALSE);
        IncomingPhoneNumber number = builder.build();
        final IncomingPhoneNumber.Builder builder2 = IncomingPhoneNumber.builder();
        builder2.setSid(sid2);
        builder2.setFriendlyName("Incoming Phone Number Test");
        builder2.setAccountSid(account2);
        builder2.setPhoneNumber("+12223334444");
        builder2.setApiVersion("2012-04-24");
        builder2.setHasVoiceCallerIdLookup(false);
        builder2.setVoiceUrl(url);
        builder2.setVoiceMethod(method);
        builder2.setVoiceFallbackUrl(url);
        builder2.setVoiceFallbackMethod(method);
        builder2.setStatusCallback(url);
        builder2.setStatusCallbackMethod(method);
        builder2.setVoiceApplicationSid(application);
        builder2.setSmsUrl(url);
        builder2.setSmsMethod(method);
        builder2.setSmsFallbackUrl(url);
        builder2.setSmsFallbackMethod(method);
        builder2.setSmsApplicationSid(application);
        builder2.setUri(url);
        builder2.setOrganizationSid(org2);
        builder2.setPureSip(Boolean.FALSE);

        IncomingPhoneNumber number2 = builder2.build();
        final IncomingPhoneNumbersDao numbers = manager.getIncomingPhoneNumbersDao();
        // Create a new incoming phone number in the data store.
        numbers.addIncomingPhoneNumber(number2);
        numbers.addIncomingPhoneNumber(number);


        // Read the incoming phone number from the data store.
        IncomingPhoneNumberFilter.Builder filterBuilder = IncomingPhoneNumberFilter.Builder.builder();
        filterBuilder.byPhoneNumber("+12223334444");
        filterBuilder.byOrgSid(org2.toString());
        filterBuilder.byPureSIP(Boolean.FALSE);
        IncomingPhoneNumberFilter numFilter = filterBuilder.build();
        List<IncomingPhoneNumber> incomingPhoneNumbers = numbers.getIncomingPhoneNumbersByFilter(numFilter);
        assertNotNull(incomingPhoneNumbers);
        assertEquals(1, incomingPhoneNumbers.size());
        assertEquals(Integer.valueOf(1), numbers.getTotalIncomingPhoneNumbers(numFilter));
    }

    @Test
    public void getRegexes() {
        final Sid sid = Sid.generate(Sid.Type.PHONE_NUMBER);
        Sid account = Sid.generate(Sid.Type.ACCOUNT);
        Sid org1 = Sid.generate(Sid.Type.ORGANIZATION);
        final Sid sid2 = Sid.generate(Sid.Type.PHONE_NUMBER);
        Sid account2 = Sid.generate(Sid.Type.ACCOUNT);
        Sid org2 = Sid.generate(Sid.Type.ORGANIZATION);

        Sid application = Sid.generate(Sid.Type.APPLICATION);
        URI url = URI.create("http://127.0.0.1:8080/restcomm/demos/hello-world.xml");
        String method = "GET";
        final IncomingPhoneNumber.Builder builder = IncomingPhoneNumber.builder();
        builder.setSid(sid);
        builder.setFriendlyName("Incoming Phone Number Test");
        builder.setAccountSid(account);
        builder.setPhoneNumber("+12.*");
        builder.setApiVersion("2012-04-24");
        builder.setHasVoiceCallerIdLookup(false);
        builder.setVoiceUrl(url);
        builder.setVoiceMethod(method);
        builder.setVoiceFallbackUrl(url);
        builder.setVoiceFallbackMethod(method);
        builder.setStatusCallback(url);
        builder.setStatusCallbackMethod(method);
        builder.setVoiceApplicationSid(application);
        builder.setSmsUrl(url);
        builder.setSmsMethod(method);
        builder.setSmsFallbackUrl(url);
        builder.setSmsFallbackMethod(method);
        builder.setSmsApplicationSid(application);
        builder.setUri(url);
        builder.setOrganizationSid(org1);
        builder.setPureSip(Boolean.TRUE);
        IncomingPhoneNumber number = builder.build();
        final IncomingPhoneNumber.Builder builder2 = IncomingPhoneNumber.builder();
        builder2.setSid(sid2);
        builder2.setFriendlyName("Incoming Phone Number Test");
        builder2.setAccountSid(account2);
        builder2.setPhoneNumber("+12223334444");
        builder2.setApiVersion("2012-04-24");
        builder2.setHasVoiceCallerIdLookup(false);
        builder2.setVoiceUrl(url);
        builder2.setVoiceMethod(method);
        builder2.setVoiceFallbackUrl(url);
        builder2.setVoiceFallbackMethod(method);
        builder2.setStatusCallback(url);
        builder2.setStatusCallbackMethod(method);
        builder2.setVoiceApplicationSid(application);
        builder2.setSmsUrl(url);
        builder2.setSmsMethod(method);
        builder2.setSmsFallbackUrl(url);
        builder2.setSmsFallbackMethod(method);
        builder2.setSmsApplicationSid(application);
        builder2.setUri(url);
        builder2.setOrganizationSid(org2);
        builder2.setPureSip(Boolean.FALSE);

        IncomingPhoneNumber number2 = builder2.build();
        final IncomingPhoneNumbersDao numbers = manager.getIncomingPhoneNumbersDao();
        // Create a new incoming phone number in the data store.
        numbers.addIncomingPhoneNumber(number2);
        numbers.addIncomingPhoneNumber(number);


        // Read the incoming phone number from the data store.
        IncomingPhoneNumberFilter.Builder filterBuilder = IncomingPhoneNumberFilter.Builder.builder();
        filterBuilder.byOrgSid(org1.toString());
        filterBuilder.byPureSIP(Boolean.TRUE);
        filterBuilder.sortedByfriendly(Sorting.Direction.DESC);
        IncomingPhoneNumberFilter numFilter = filterBuilder.build();
        List<IncomingPhoneNumber> incomingPhoneNumbers = numbers.getIncomingPhoneNumbersRegex(numFilter);
        assertNotNull(incomingPhoneNumbers);
        assertEquals(1, incomingPhoneNumbers.size());
    }

    @Test
    public void removeByAccountSid() {
        final Sid sid = Sid.generate(Sid.Type.PHONE_NUMBER);
        Sid account = Sid.generate(Sid.Type.ACCOUNT);
        Sid application = Sid.generate(Sid.Type.APPLICATION);
        URI url = URI.create("http://127.0.0.1:8080/restcomm/demos/hello-world.xml");
        String method = "GET";
        final IncomingPhoneNumber.Builder builder = IncomingPhoneNumber.builder();
        builder.setSid(sid);
        builder.setFriendlyName("Incoming Phone Number Test");
        builder.setAccountSid(account);
        builder.setPhoneNumber("+12223334444");
        builder.setApiVersion("2012-04-24");
        builder.setHasVoiceCallerIdLookup(false);
        builder.setVoiceUrl(url);
        builder.setVoiceMethod(method);
        builder.setVoiceFallbackUrl(url);
        builder.setVoiceFallbackMethod(method);
        builder.setStatusCallback(url);
        builder.setStatusCallbackMethod(method);
        builder.setVoiceApplicationSid(application);
        builder.setSmsUrl(url);
        builder.setSmsMethod(method);
        builder.setSmsFallbackUrl(url);
        builder.setSmsFallbackMethod(method);
        builder.setSmsApplicationSid(application);
        builder.setUri(url);
        builder.setOrganizationSid(Sid.generate(Sid.Type.ORGANIZATION));
        IncomingPhoneNumber number = builder.build();
        final IncomingPhoneNumbersDao numbers = manager.getIncomingPhoneNumbersDao();
        // Create a new incoming phone number in the data store.
        numbers.addIncomingPhoneNumber(number);
        assertEquals(1, numbers.getIncomingPhoneNumbers(account).size());
        // Delete the incoming phone number.
        numbers.removeIncomingPhoneNumbers(account);
        assertTrue(numbers.getIncomingPhoneNumbers(account).isEmpty());
    }
}