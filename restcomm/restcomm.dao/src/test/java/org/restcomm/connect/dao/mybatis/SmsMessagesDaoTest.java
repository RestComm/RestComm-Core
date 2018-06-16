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

import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.log4j.Logger;
import org.joda.time.DateTime;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.restcomm.connect.commons.dao.MessageError;
import org.restcomm.connect.commons.dao.Sid;
import org.restcomm.connect.dao.SmsMessagesDao;
import org.restcomm.connect.dao.entities.SmsMessage;

import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.text.ParseException;
import java.util.Currency;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.FixMethodOrder;
import org.junit.runners.MethodSorters;

/**
 * @author quintana.thomas@gmail.com (Thomas Quintana)
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public final class SmsMessagesDaoTest {
    private static Logger logger = Logger.getLogger(SmsMessagesDaoTest.class);
    private static MybatisDaoManager manager;

    public SmsMessagesDaoTest() {
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
        final Sid sid = Sid.generate(Sid.Type.SMS_MESSAGE);
        final Sid account = Sid.generate(Sid.Type.ACCOUNT);
        final URI url = URI.create("2012-04-24/Accounts/Acoount/SMS/Messages/unique-id.json");
        final SmsMessage.Builder builder = SmsMessage.builder();
        builder.setSid(sid);
        builder.setAccountSid(account);
        builder.setApiVersion("2012-04-24");
        builder.setRecipient("+12223334444");
        builder.setSender("+17778889999");
        builder.setBody("Hello World!");
        builder.setStatus(SmsMessage.Status.SENDING);
        builder.setDirection(SmsMessage.Direction.INBOUND);
        builder.setPrice(new BigDecimal("0.00"));
        builder.setPriceUnit(Currency.getInstance("USD"));
        builder.setUri(url);
        SmsMessage message = builder.build();
        final SmsMessagesDao messages = manager.getSmsMessagesDao();
        // Create a new sms message in the data store.
        messages.addSmsMessage(message);
        // Read the message from the data store.
        SmsMessage result = messages.getSmsMessage(sid);
        // Validate the results.
        assertTrue(result.getSid().equals(message.getSid()));
        assertTrue(result.getAccountSid().equals(message.getAccountSid()));
        assertTrue(result.getApiVersion().equals(message.getApiVersion()));
        assertTrue(result.getDateSent() == message.getDateSent());
        assertTrue(result.getRecipient().equals(message.getRecipient()));
        assertTrue(result.getSender().equals(message.getSender()));
        assertTrue(result.getBody().equals(message.getBody()));
        assertTrue(result.getStatus() == message.getStatus());
        assertTrue(result.getDirection() == message.getDirection());
        assertTrue(result.getPrice().equals(message.getPrice()));
        assertTrue(result.getPriceUnit().equals(message.getPriceUnit()));
        assertTrue(result.getUri().equals(message.getUri()));
        // Update the message.
        final DateTime now = DateTime.now();
        final SmsMessage.Builder builder2 = SmsMessage.builder();
        builder2.copyMessage(message);
        builder2.setDateSent(now);
        builder2.setStatus(SmsMessage.Status.SENT);
        messages.updateSmsMessage(builder2.build());
        // Read the updated message from the data store.
        result = messages.getSmsMessage(sid);
        // Validate the results.
        assertTrue(result.getDateSent().equals(now));
        assertEquals(SmsMessage.Status.SENT, result.getStatus());
        // Delete the message.
        messages.removeSmsMessage(sid);
        // Validate that the CDR was removed.
        assertTrue(messages.getSmsMessage(sid) == null);
    }

    private SmsMessage createSms() {
        return createSms(Sid.generate(Sid.Type.ACCOUNT), SmsMessage.Direction.OUTBOUND_API, 0, DateTime.now());
    }

    private SmsMessage createSms(Sid account, SmsMessage.Direction direction, int index, DateTime date) {
        return createSms(account, direction, index, date, null);
    }

    private SmsMessage createSms(Sid account, SmsMessage.Direction direction, int index, DateTime date, String smppMessageId) {
        final Sid sid = Sid.generate(Sid.Type.SMS_MESSAGE);
        final URI url = URI.create("2012-04-24/Accounts/Acoount/SMS/Messages/unique-id.json");
        return SmsMessage.builder()
                .setSid(sid)
                .setAccountSid(account)
                .setApiVersion("2012-04-24")
                .setRecipient("+12223334444")
                .setSender("+17778889999")
                .setBody("Hello World - " + index)
                .setStatus(SmsMessage.Status.SENDING)
                .setDirection(direction)
                .setPrice(new BigDecimal("0.00"))
                .setPriceUnit(Currency.getInstance("USD"))
                .setUri(url)
                .setDateCreated(date)
                .setSmppMessageId(smppMessageId)
                .build();
    }

    @Test
    public void aaatestGetSmsMessagesLastMinute() throws InterruptedException, ParseException {
        final SmsMessagesDao messages = manager.getSmsMessagesDao();
        final Sid account = Sid.generate(Sid.Type.ACCOUNT);
        DateTime oneMinuteAgo = DateTime.now().minusSeconds(58);
        for (int i = 0; i < 2; i++) {
            SmsMessage message = createSms(account, SmsMessage.Direction.OUTBOUND_API, i, oneMinuteAgo);
            // Create a new sms message in the data store.
            messages.addSmsMessage(message);
            logger.info("Created message: " + message);
        }
        for (int i = 0; i < 2; i++) {
            SmsMessage message = createSms(account, SmsMessage.Direction.OUTBOUND_CALL, i, oneMinuteAgo);
            // Create a new sms message in the data store.
            messages.addSmsMessage(message);
            logger.info("Created message: " + message);
        }
        for (int i = 0; i < 2; i++) {
            SmsMessage message = createSms(account, SmsMessage.Direction.OUTBOUND_REPLY, i, oneMinuteAgo);
            // Create a new sms message in the data store.
            messages.addSmsMessage(message);
            logger.info("Created message: " + message);
        }
        int lastMessages = messages.getSmsMessagesPerAccountLastPerMinute(account.toString());
        logger.info("SMS Messages last minutes: " + lastMessages);
        assertEquals(6, lastMessages);
        Thread.sleep(5000);
        DateTime oneMinuteLater = DateTime.now();
        for (int i = 0; i < 3; i++) {
            SmsMessage message = createSms(account, SmsMessage.Direction.OUTBOUND_CALL, i, oneMinuteLater);
            // Create a new sms message in the data store.
            messages.addSmsMessage(message);
            logger.info("Created message: " + message);
        }
        lastMessages = messages.getSmsMessagesPerAccountLastPerMinute(account.toString());
        logger.info("SMS Messages last minutes: " + lastMessages);
        assertEquals(3, lastMessages);
        messages.removeSmsMessages(account);
    }

    @Test
    public void testReadDeleteByAccount() {
        final Sid sid = Sid.generate(Sid.Type.SMS_MESSAGE);
        final Sid account = Sid.generate(Sid.Type.ACCOUNT);
        DateTime now = DateTime.now();
        final URI url = URI.create("2012-04-24/Accounts/Acoount/SMS/Messages/unique-id.json");
        final SmsMessage.Builder builder = SmsMessage.builder();
        builder.setSid(sid);
        builder.setAccountSid(account);
        builder.setApiVersion("2012-04-24");
        builder.setDateSent(now);
        builder.setRecipient("+12223334444");
        builder.setSender("+17778889999");
        builder.setBody("Hello World!");
        builder.setStatus(SmsMessage.Status.SENDING);
        builder.setDirection(SmsMessage.Direction.INBOUND);
        builder.setPrice(new BigDecimal("0.00"));
        builder.setPriceUnit(Currency.getInstance("GBP"));
        builder.setUri(url);
        SmsMessage message = builder.build();
        final SmsMessagesDao messages = manager.getSmsMessagesDao();
        // Create a new sms message in the data store.
        messages.addSmsMessage(message);
        // Validate the results.
        assertTrue(messages.getSmsMessages(account).size() == 1);
        // Delete the message.
        messages.removeSmsMessages(account);
        // Validate the results.
        assertTrue(messages.getSmsMessages(account).size() == 0);
    }

    @Test
    public void testUpdateSmsMessageDateSentAndStatusAndGetBySmppMsgId() {
        final DateTime dateSent = DateTime.now();
        final SmsMessage.Status status = SmsMessage.Status.SENT;
        final String smppMessageId = "0000058049";

        // add a new msg
        SmsMessage smsMessage = createSms();
        final SmsMessagesDao messages = manager.getSmsMessagesDao();
        messages.addSmsMessage(smsMessage);

        //set status and dateSent
        final SmsMessage.Builder builder2 = SmsMessage.builder();
        builder2.copyMessage(smsMessage);
        builder2.setStatus(status).setDateSent(dateSent).setSmppMessageId(smppMessageId);
        messages.updateSmsMessage(builder2.build());

        //get SmsMessage By SmppMessageId
        SmsMessage resultantSmsMessage = messages.getSmsMessageBySmppMessageId(smppMessageId);

        //make assertions
        assertEquals(smppMessageId, resultantSmsMessage.getSmppMessageId());
        assertEquals(dateSent, resultantSmsMessage.getDateSent());
        assertEquals(status, resultantSmsMessage.getStatus());
    }

    @Test
    public void testSmsMessageError() {
    	// add a new msg
    	SmsMessage smsMessage = createSms();
    	final SmsMessagesDao messages = manager.getSmsMessagesDao();
        messages.addSmsMessage(smsMessage);

        //update error message
        final SmsMessage.Builder builder2 = SmsMessage.builder();
        builder2.copyMessage(smsMessage);
        builder2.setError(MessageError.QUEUE_OVERFLOW);
        messages.updateSmsMessage(builder2.build());

        //get msg from DB
        SmsMessage resultantSmsMessage = messages.getSmsMessage(smsMessage.getSid());

        //make assertions
        assertEquals(MessageError.QUEUE_OVERFLOW, resultantSmsMessage.getError());
    }

    public void testFindBySmppMessageIdAndDateCreatedGreaterOrEqualThanOrderedByDateCreatedDesc() {
        // given
        final SmsMessagesDao smsMessagesDao = manager.getSmsMessagesDao();
        final Sid accountSid = Sid.generate(Sid.Type.ACCOUNT);
        final String smppMessageId = "12345";

        final DateTime fourDaysAgo = DateTime.now().minusDays(4);
        final SmsMessage smsMessage1 = createSms(accountSid, SmsMessage.Direction.OUTBOUND_API, 0, fourDaysAgo, smppMessageId);
        final SmsMessage smsMessage2 = createSms(accountSid, SmsMessage.Direction.OUTBOUND_API, 1, fourDaysAgo, smppMessageId);

        final DateTime threeDaysAgo = DateTime.now().minusDays(3);
        final SmsMessage smsMessage3 = createSms(accountSid, SmsMessage.Direction.OUTBOUND_API, 2, threeDaysAgo, smppMessageId);
        final SmsMessage smsMessage4 = createSms(accountSid, SmsMessage.Direction.OUTBOUND_API, 3, threeDaysAgo, null);

        final DateTime yesterday = DateTime.now().minusDays(1);
        final SmsMessage smsMessage5 = createSms(accountSid, SmsMessage.Direction.OUTBOUND_API, 4, yesterday, smppMessageId);

        final DateTime today = DateTime.now();
        final SmsMessage smsMessage6 = createSms(accountSid, SmsMessage.Direction.OUTBOUND_API, 5, today, smppMessageId);
        final SmsMessage smsMessage7 = createSms(accountSid, SmsMessage.Direction.OUTBOUND_API, 6, today, null);

        // when
        smsMessagesDao.addSmsMessage(smsMessage1);
        smsMessagesDao.addSmsMessage(smsMessage2);
        smsMessagesDao.addSmsMessage(smsMessage3);
        smsMessagesDao.addSmsMessage(smsMessage4);
        smsMessagesDao.addSmsMessage(smsMessage5);
        smsMessagesDao.addSmsMessage(smsMessage6);
        smsMessagesDao.addSmsMessage(smsMessage7);

        final List<SmsMessage> messages = smsMessagesDao.findBySmppMessageId(smppMessageId);

        // then
        try {
            assertEquals(5, messages.size());
            for (SmsMessage message: messages) {
                assertEquals(smppMessageId, message.getSmppMessageId());
            }
        } finally {
            smsMessagesDao.removeSmsMessages(accountSid);
        }
    }
}
