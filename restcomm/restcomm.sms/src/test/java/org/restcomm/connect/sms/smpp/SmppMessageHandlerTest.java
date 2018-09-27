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
package org.restcomm.connect.sms.smpp;

import akka.actor.Actor;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.japi.Creator;
import akka.testkit.JavaTestKit;
import akka.testkit.TestKit;
import com.cloudhopper.smpp.PduAsyncResponse;
import com.cloudhopper.smpp.impl.DefaultPduAsyncResponse;
import com.cloudhopper.smpp.pdu.PduRequest;
import com.cloudhopper.smpp.pdu.SubmitSmResp;
import org.apache.commons.configuration.Configuration;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.restcomm.connect.commons.dao.Sid;
import org.restcomm.connect.core.service.api.NumberSelectorService;
import org.restcomm.connect.dao.DaoManager;
import org.restcomm.connect.dao.SmsMessagesDao;
import org.restcomm.connect.dao.entities.IncomingPhoneNumber;
import org.restcomm.connect.dao.entities.SmsMessage;
import org.restcomm.connect.monitoringservice.MonitoringService;
import org.restcomm.connect.sms.smpp.dlr.spi.DLRPayload;

import javax.servlet.ServletContext;
import javax.servlet.sip.SipFactory;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import org.restcomm.connect.sms.SmsService;
import org.restcomm.connect.sms.api.SmsStatusUpdated;
import scala.concurrent.duration.FiniteDuration;

/**
 * @author Henrique Rosa (henrique.rosa@telestax.com)
 */
public class SmppMessageHandlerTest {

    private static ActorSystem system;

    @BeforeClass
    public static void beforeAll() {
        system = ActorSystem.create();
    }

    @AfterClass
    public static void afterAll() {
        system.shutdown();
    }

    @Test
    public void testRemover() {
        List<IncomingPhoneNumber> numbers = new ArrayList();
        IncomingPhoneNumber.Builder builder = IncomingPhoneNumber.builder();

        builder.setPhoneNumber("123.");
        numbers.add(builder.build());
        RegexRemover.removeRegexes(numbers);
        assertTrue(numbers.isEmpty());

        builder.setPhoneNumber("123*");
        numbers.add(builder.build());
        RegexRemover.removeRegexes(numbers);
        assertTrue(numbers.isEmpty());

        builder.setPhoneNumber("^123");
        numbers.add(builder.build());
        RegexRemover.removeRegexes(numbers);
        assertTrue(numbers.isEmpty());

        builder.setPhoneNumber(".");
        numbers.add(builder.build());
        RegexRemover.removeRegexes(numbers);
        assertTrue(numbers.isEmpty());

        builder.setPhoneNumber("[5]");
        numbers.add(builder.build());
        RegexRemover.removeRegexes(numbers);
        assertTrue(numbers.isEmpty());

        builder.setPhoneNumber("1|2");
        numbers.add(builder.build());
        RegexRemover.removeRegexes(numbers);
        assertTrue(numbers.isEmpty());

        builder.setPhoneNumber("\\d");
        numbers.add(builder.build());
        RegexRemover.removeRegexes(numbers);
        assertTrue(numbers.isEmpty());

        builder.setPhoneNumber("+1234");
        numbers.add(builder.build());
        builder.setPhoneNumber("+1234*");
        numbers.add(builder.build());
        builder.setPhoneNumber("+1234.*");
        numbers.add(builder.build());
        builder.setPhoneNumber("9887");
        numbers.add(builder.build());
        builder.setPhoneNumber("+");
        numbers.add(builder.build());
        RegexRemover.removeRegexes(numbers);
        assertEquals(3, numbers.size());
    }

    @Test
    public void testOnReceiveDlrPayload() throws ParseException {
        new JavaTestKit(system) {
            {
                // given
                final ServletContext servletContext = mock(ServletContext.class);
                final DaoManager daoManager = mock(DaoManager.class);

                final DLRPayload dlrPayload = new DLRPayload();
                dlrPayload.setId("12345");
                dlrPayload.setStat(SmsMessage.Status.UNDELIVERED);

                final SmsMessagesDao smsMessagesDao = mock(SmsMessagesDao.class);
                final SmsMessage message = SmsMessage.builder().setSid(Sid.generate(Sid.Type.SMS_MESSAGE)).setSmppMessageId(dlrPayload.getId()).setStatus(SmsMessage.Status.SENT).build();

                when(servletContext.getAttribute(DaoManager.class.getName())).thenReturn(daoManager);
                when(servletContext.getAttribute(Configuration.class.getName())).thenReturn(mock(Configuration.class));
                when(servletContext.getAttribute(SipFactory.class.getName())).thenReturn(mock(SipFactory.class));
                when(servletContext.getAttribute(MonitoringService.class.getName())).thenReturn(mock(ActorRef.class));
                when(servletContext.getAttribute(NumberSelectorService.class.getName())).thenReturn(mock(NumberSelectorService.class));
                when(servletContext.getAttribute(SmsService.class.getName())).thenReturn(getRef());
                when(daoManager.getSmsMessagesDao()).thenReturn(smsMessagesDao);
                when(smsMessagesDao.getSmsMessageBySmppMessageId(dlrPayload.getId())).thenReturn(message);

                final ActorRef messageHandler = system.actorOf(Props.apply(new Creator<Actor>() {
                    @Override
                    public Actor create() throws Exception {
                        return new SmppMessageHandler(servletContext);
                    }
                }));

                // when
                messageHandler.tell(dlrPayload, getRef());

                // then
                verify(smsMessagesDao, timeout(100)).getSmsMessageBySmppMessageId(dlrPayload.getId());

                SmsStatusUpdated expectMsgClass = expectMsgClass(FiniteDuration.create(100, TimeUnit.MILLISECONDS),
                        SmsStatusUpdated.class);
                SmsMessage msg = (SmsMessage) expectMsgClass.getInfo().attributes().get("record");
                assertEquals(SmsMessage.Status.UNDELIVERED, msg.getStatus());
            }
        };
    }

    @Test
    public void testOnReceiveDlrPayloadForUnknownSms() throws ParseException {
        new JavaTestKit(system) {
            {
                // given
                final ServletContext servletContext = mock(ServletContext.class);
                final DaoManager daoManager = mock(DaoManager.class);
                final SmsMessagesDao smsMessagesDao = mock(SmsMessagesDao.class);

                final DLRPayload dlrPayload = new DLRPayload();
                dlrPayload.setId("12345");

                when(servletContext.getAttribute(DaoManager.class.getName())).thenReturn(daoManager);
                when(servletContext.getAttribute(Configuration.class.getName())).thenReturn(mock(Configuration.class));
                when(servletContext.getAttribute(SipFactory.class.getName())).thenReturn(mock(SipFactory.class));
                when(servletContext.getAttribute(MonitoringService.class.getName())).thenReturn(mock(ActorRef.class));
                when(servletContext.getAttribute(NumberSelectorService.class.getName())).thenReturn(mock(NumberSelectorService.class));
                when(servletContext.getAttribute(SmsService.class.getName())).thenReturn(getRef());
                when(daoManager.getSmsMessagesDao()).thenReturn(smsMessagesDao);
                when(smsMessagesDao.getSmsMessageBySmppMessageId(dlrPayload.getId())).thenReturn(null);

                final ActorRef messageHandler = system.actorOf(Props.apply(new Creator<Actor>() {
                    @Override
                    public Actor create() throws Exception {
                        return new SmppMessageHandler(servletContext);
                    }
                }));

                // when
                messageHandler.tell(dlrPayload, getRef());

                // then
                verify(smsMessagesDao, timeout(100)).getSmsMessageBySmppMessageId(dlrPayload.getId());
                expectNoMsg();
            }
        };
    }

    @Test
    public void testOnReceivePduAsyncResponseWithExistingSmppMessageCorrelation() {
        new JavaTestKit(system) {
            {
                // given
                final ServletContext servletContext = mock(ServletContext.class);
                final DaoManager daoManager = mock(DaoManager.class);

                when(servletContext.getAttribute(DaoManager.class.getName())).thenReturn(daoManager);
                when(servletContext.getAttribute(Configuration.class.getName())).thenReturn(mock(Configuration.class));
                when(servletContext.getAttribute(SipFactory.class.getName())).thenReturn(mock(SipFactory.class));
                when(servletContext.getAttribute(MonitoringService.class.getName())).thenReturn(mock(ActorRef.class));
                when(servletContext.getAttribute(NumberSelectorService.class.getName())).thenReturn(mock(NumberSelectorService.class));
                when(servletContext.getAttribute(SmsService.class.getName())).thenReturn(getRef());
                final String smppMessageId = "12345";
                final SmsMessagesDao smsMessagesDao = mock(SmsMessagesDao.class);
                final SmsMessage existingSmsMessage = SmsMessage.builder().setSid(Sid.generate(Sid.Type.SMS_MESSAGE)).setSmppMessageId(smppMessageId).setStatus(SmsMessage.Status.QUEUED).build();
                final SmsMessage smsMessage = SmsMessage.builder().setSid(Sid.generate(Sid.Type.SMS_MESSAGE)).setSmppMessageId(null).setStatus(SmsMessage.Status.SENDING).build();
                final SubmitSmResp submitSmResp = mock(SubmitSmResp.class);
                final PduRequest pduRequest = mock(PduRequest.class);
                final PduAsyncResponse pduResponse = mock(DefaultPduAsyncResponse.class);

                when(pduResponse.getRequest()).thenReturn(pduRequest);
                when(pduResponse.getResponse()).thenReturn(submitSmResp);
                when(pduRequest.getReferenceObject()).thenReturn(smsMessage.getSid());
                when(submitSmResp.getMessageId()).thenReturn(smppMessageId);
                when(submitSmResp.getCommandStatus()).thenReturn(0);

                when(daoManager.getSmsMessagesDao()).thenReturn(smsMessagesDao);
                when(smsMessagesDao.getSmsMessageBySmppMessageId(smppMessageId)).thenReturn(existingSmsMessage);
                when(smsMessagesDao.getSmsMessage(smsMessage.getSid())).thenReturn(smsMessage);

                final SmsMessage smsMessageWithSmppMessageId1 = SmsMessage.builder().setSid(Sid.generate(Sid.Type.SMS_MESSAGE)).setSmppMessageId(smppMessageId).setStatus(SmsMessage.Status.SENT).build();
                final SmsMessage smsMessageWithSmppMessageId2 = SmsMessage.builder().setSid(Sid.generate(Sid.Type.SMS_MESSAGE)).setSmppMessageId(smppMessageId).setStatus(SmsMessage.Status.SENT).build();
                final SmsMessage smsMessageWithSmppMessageId3 = SmsMessage.builder().setSid(Sid.generate(Sid.Type.SMS_MESSAGE)).setSmppMessageId(smppMessageId).setStatus(SmsMessage.Status.SENT).build();
                final List<SmsMessage> messages = Arrays.asList(smsMessageWithSmppMessageId1, smsMessageWithSmppMessageId2, smsMessageWithSmppMessageId3);

                when(smsMessagesDao.findBySmppMessageId(smppMessageId)).thenReturn(messages);

                final ActorRef messageHandler = system.actorOf(Props.apply(new Creator<Actor>() {
                    @Override
                    public Actor create() throws Exception {
                        return new SmppMessageHandler(servletContext);
                    }
                }));

                // when
                messageHandler.tell(pduResponse, getRef());

                // then
                final ArgumentCaptor<SmsMessage> smsCaptor = ArgumentCaptor.forClass(SmsMessage.class);
                SmsStatusUpdated expectMsgClass = expectMsgClass(FiniteDuration.create(100, TimeUnit.MILLISECONDS),
                        SmsStatusUpdated.class);
                SmsMessage msg = (SmsMessage) expectMsgClass.getInfo().attributes().get("record");
                assertEquals(SmsMessage.Status.SENT, msg.getStatus());

            }
        };
    }

}
