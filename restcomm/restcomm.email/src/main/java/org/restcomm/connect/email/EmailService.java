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

import akka.actor.ActorRef;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import org.apache.commons.configuration.Configuration;
import org.restcomm.connect.commons.faulttolerance.RestcommUntypedActor;
import org.restcomm.connect.commons.patterns.Observe;
import org.restcomm.connect.commons.patterns.Observing;
import org.restcomm.connect.commons.patterns.StopObserving;
import org.restcomm.connect.email.api.EmailRequest;
import org.restcomm.connect.email.api.EmailResponse;
import org.restcomm.connect.email.api.Mail;

import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.NoSuchProviderException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;


/**
 * @author liblefty@gmail.com (Lefteris Banos)
 */
public class EmailService extends RestcommUntypedActor {

    final LoggingAdapter logger = Logging.getLogger(getContext().system(), this);
    private final List<ActorRef> observers;
    private Configuration configuration;
    private Session session;
    private String host;
    private String port;
    private String user;
    private String password;
    private Transport transport;
    private final Properties properties = System.getProperties();
    private boolean isSslEnabled = false;
    public EmailService(final Configuration config) {
        this.observers = new ArrayList<ActorRef>();
        configuration = config;
        this.host = configuration.getString("host");
        this.port = configuration.getString("port");
        this.user = configuration.getString("user");
        this.password = configuration.getString("password");
        if ((user != null && !user.isEmpty()) || (password != null && !password.isEmpty()) ||
                (port != null && !port.isEmpty()) || (host != null && !host.isEmpty()) ){
        //allow smtp over ssl if port is set to 465
        if ("465".equals(port)){
            this.isSslEnabled = true;
           useSSLSmtp();
        }else{
       //TLS uses port 587
        properties.setProperty("mail.smtp.host", host);
        properties.setProperty("mail.smtp.user", user);
        properties.setProperty("mail.smtp.password", password);
        properties.setProperty("mail.smtp.port", port);
        }
        properties.setProperty("mail.smtp.starttls.enable", "true");
        properties.setProperty("mail.transport.protocol", "smtps");
        // properties.setProperty("mail.smtp.ssl.enable", "true");
        properties.setProperty("mail.smtp.auth", "true");
        session = Session.getInstance(properties,
                new javax.mail.Authenticator() {
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(user, password);
                    }
                });
        }
    }

    private void useSSLSmtp() {
            properties.put("mail.transport.protocol", "smtps");
            properties.put("mail.smtps.ssl.enable", "true");
            properties.put("mail.smtps.host", host);
            properties.put("mail.smtps.user", user);
            properties.put("mail.smtps.password", password);
            properties.put("mail.smtps.port", port);
            properties.put("mail.smtps.auth", "true");
            session = Session.getDefaultInstance(properties);
            try {
                this.transport = session.getTransport();
            } catch (NoSuchProviderException ex) {
               logger.error(EmailService.class.getName() ,  ex);
            }
    }
    private void observe(final Object message) {
        final ActorRef self = self();
        final Observe request = (Observe) message;
        final ActorRef observer = request.observer();
        if (observer != null) {
            observers.add(observer);
            observer.tell(new Observing(self), self);
        }
    }

    private void stopObserving(final Object message) {
        final StopObserving request = (StopObserving) message;
        final ActorRef observer = request.observer();
        if (observer != null) {
            observers.remove(observer);
        }
    }

    @Override
    public void onReceive(final Object message) throws Exception {
        final Class<?> klass = message.getClass();
        final ActorRef self = self();
        final ActorRef sender = sender();

        if (Observe.class.equals(klass)) {
            observe(message);
        } else if (StopObserving.class.equals(klass)) {
            stopObserving(message);
        }else if (EmailRequest.class.equals(klass)) {
            EmailRequest request = (EmailRequest)message;
            if(isSslEnabled){
               sender.tell(sendEmailSsL(request.getObject()), self);
            }else{
            sender.tell(send(request.getObject()), self);
            }
        }
    }

    EmailResponse sendEmailSsL (final Mail mail) {
        try {
            InternetAddress from;
            if (mail.from() != null || !mail.from().equalsIgnoreCase("")) {
                from = new InternetAddress(mail.from());
            } else {
                from = new InternetAddress(user);
            }
            final InternetAddress to = new InternetAddress(mail.to());
            final MimeMessage email = new MimeMessage(session);
            email.setFrom(from);
            email.addRecipient(Message.RecipientType.TO, to);
            email.setSubject(mail.subject());
            email.setContent(mail.body(), mail.contentType());
            email.addRecipients(Message.RecipientType.CC, InternetAddress.parse(mail.cc(), false));
            email.addRecipients(Message.RecipientType.BCC,InternetAddress.parse(mail.bcc(),false));
            //Transport.send(email);
            transport.connect (host, Integer.parseInt(port), user, password);
            transport.sendMessage(email, email.getRecipients(Message.RecipientType.TO));
            return new EmailResponse(mail);
        } catch (final MessagingException exception) {
            logger.error(exception.getMessage(), exception);
            return new EmailResponse(exception,exception.getMessage());
        }
    }

     EmailResponse send(final Mail mail) {
        try {
            InternetAddress from;
            if (mail.from() != null || !mail.from().equalsIgnoreCase("")) {
                from = new InternetAddress(mail.from());
            } else {
                from = new InternetAddress(user);
            }
            final InternetAddress to = new InternetAddress(mail.to());
            final MimeMessage email = new MimeMessage(session);
            email.setFrom(from);
            email.addRecipient(Message.RecipientType.TO, to);
            email.setSubject(mail.subject());
            email.setContent(mail.body(), mail.contentType());
            email.addRecipients(Message.RecipientType.CC, InternetAddress.parse(mail.cc(), false));
            email.addRecipients(Message.RecipientType.BCC,InternetAddress.parse(mail.bcc(),false));
            Transport.send(email);
            return new EmailResponse(mail);
        } catch (final MessagingException exception) {
            logger.error(exception.getMessage(), exception);
            return new EmailResponse(exception,exception.getMessage());
        }
    }
}
