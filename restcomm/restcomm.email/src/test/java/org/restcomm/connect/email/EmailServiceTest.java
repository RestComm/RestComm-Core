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
package org.restcomm.connect.email;

import akka.actor.Actor;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.actor.UntypedActorFactory;
import akka.testkit.JavaTestKit;

import java.io.IOException;
import java.net.URL;
import java.util.concurrent.TimeUnit;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.XMLConfiguration;
import org.junit.After;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;
import org.restcomm.connect.email.api.EmailRequest;
import org.restcomm.connect.email.api.EmailResponse;
import org.restcomm.connect.email.api.Mail;
import scala.concurrent.duration.FiniteDuration;

import com.icegreen.greenmail.util.GreenMail;
import com.icegreen.greenmail.util.ServerSetupTest;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;


/**
 * @author liblefty@gmail.com (Lefteris Banos)
 */
public final class EmailServiceTest {
    private ActorSystem system;
    private ActorRef emailService;
    private GreenMail mailServer;

    public EmailServiceTest() {
        super();
    }

    @Before
    public void before() throws Exception {
        mailServer = new GreenMail(ServerSetupTest.SMTP);
        mailServer.start();
        mailServer.setUser("hascode@localhost", "hascode", "abcdef123");
        system = ActorSystem.create();
        final URL input = getClass().getResource("/emailServiceTest.xml");
        final XMLConfiguration configuration = new XMLConfiguration(input);
        emailService = emailService(configuration);
    }

    @After
    public void after() throws Exception {
        system.shutdown();
        mailServer.stop();
    }

    private ActorRef emailService(final Configuration configuration) {
        return system.actorOf(new Props(new UntypedActorFactory() {
            private static final long serialVersionUID = 1L;

            @Override
            public Actor create() throws Exception {
                return new EmailService(configuration);
            }
        }));
    }

    @Test
    public void testSendEmail() {
        new JavaTestKit(system) {
            {
                final ActorRef observer = getRef();

                // Send the email.
                final Mail emailMsg = new Mail("hascode@localhost", "someone@localhost.com","Testing Email Service" ,"This is the subject of the email service testing", "someone2@localhost.com, test@localhost.com, test3@localhost.com", "someone3@localhost.com, test2@localhost.com");
                emailService.tell(new EmailRequest(emailMsg), observer);

                final EmailResponse response = expectMsgClass(FiniteDuration.create(60, TimeUnit.SECONDS), EmailResponse.class);
                assertTrue(response.succeeded());

                // fetch messages from server
                MimeMessage[] messages = mailServer.getReceivedMessages();
                assertNotNull(messages);
                assertEquals(6, messages.length);
                MimeMessage m = messages[0];
                try {
                assertEquals(emailMsg.subject(), m.getSubject());
                assertTrue(String.valueOf(m.getContent()).contains(emailMsg.body()));
                assertEquals(emailMsg.from(), m.getFrom()[0].toString());
                assertEquals(emailMsg.contentType(), "text/plain");
                } catch(Exception e){
                    assertTrue(false);
            }
            }
        };
    }

    @Test
    public void testSendHTMLEmail() throws IOException, MessagingException {
        new JavaTestKit(system) {
            {
                final ActorRef observer = getRef();

                // Send the email.
                final Mail emailMsg = new Mail("hascode@localhost", "someone@localhost.com","Testing Email Service" ,"This is the subject of the email service testing", "someone2@localhost.com, test@localhost.com, test3@localhost.com", "someone3@localhost.com, test2@localhost.com", null, null, "text/html");
                emailService.tell(new EmailRequest(emailMsg), observer);

                final EmailResponse response = expectMsgClass(FiniteDuration.create(60, TimeUnit.SECONDS), EmailResponse.class);
                assertTrue(response.succeeded());

                // fetch messages from server
                MimeMessage[] messages = mailServer.getReceivedMessages();
                assertNotNull(messages);
                assertEquals(6, messages.length);
                MimeMessage m = messages[0];
                assertEquals(emailMsg.subject(), m.getSubject());
                assertTrue(String.valueOf(m.getContent()).contains(emailMsg.body()));
                assertEquals(emailMsg.from(), m.getFrom()[0].toString());
                assertEquals(emailMsg.contentType(), "text/html");
            }
        };
    }
}


