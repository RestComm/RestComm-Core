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
package org.restcomm.connect.sms;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import javax.servlet.ServletContext;
import javax.servlet.sip.SipApplicationSession;
import javax.servlet.sip.SipFactory;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;
import javax.servlet.sip.SipSession;
import javax.servlet.sip.SipURI;

import akka.actor.ActorSystem;
import org.apache.commons.configuration.Configuration;
import org.joda.time.DateTime;
import org.restcomm.connect.commons.dao.Sid;
import org.restcomm.connect.commons.faulttolerance.RestcommUntypedActor;
import org.restcomm.connect.commons.patterns.Observe;
import org.restcomm.connect.commons.patterns.Observing;
import org.restcomm.connect.commons.patterns.StopObserving;
import org.restcomm.connect.commons.push.PushNotificationServerHelper;
import org.restcomm.connect.dao.ClientsDao;
import org.restcomm.connect.dao.DaoManager;
import org.restcomm.connect.dao.RegistrationsDao;
import org.restcomm.connect.dao.entities.Client;
import org.restcomm.connect.dao.entities.Registration;
import org.restcomm.connect.dao.entities.SmsMessage;
import org.restcomm.connect.sms.api.GetLastSmsRequest;
import org.restcomm.connect.sms.api.SmsSessionAttribute;
import org.restcomm.connect.sms.api.SmsSessionInfo;
import org.restcomm.connect.sms.api.SmsSessionRequest;
import org.restcomm.connect.sms.api.SmsSessionResponse;
import org.restcomm.connect.sms.smpp.SmppClientOpsThread;
import org.restcomm.connect.sms.smpp.SmppInboundMessageEntity;
import org.restcomm.connect.sms.smpp.SmppMessageHandler;
import org.restcomm.connect.sms.smpp.SmppOutboundMessageEntity;
import org.restcomm.connect.telephony.api.TextMessage;
import org.restcomm.smpp.parameter.TlvSet;

import com.cloudhopper.commons.charset.Charset;
import com.cloudhopper.commons.charset.CharsetUtil;
import com.cloudhopper.commons.util.ByteArrayUtil;
import com.cloudhopper.smpp.SmppConstants;
import com.cloudhopper.smpp.tlv.Tlv;
import com.google.common.collect.ImmutableMap;

import akka.actor.ActorRef;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import scala.concurrent.duration.Duration;

/**
 * @author quintana.thomas@gmail.com (Thomas Quintana)
 * @author maria-farooq@live.com (Maria Farooq)
 */
public final class SmsSession extends RestcommUntypedActor {

    private static final String SMS_RECORD = "record";
    // Logger
    private final LoggingAdapter logger = Logging.getLogger(getContext().system(), this);
    // Runtime stuff.
    private final ActorSystem system;
    private final Configuration smsConfiguration;
    private final Configuration configuration;
    private final SipFactory factory;
    private final List<ActorRef> observers;
    private final SipURI transport;
    private final Map<String, Object> attributes;

    // Push notification server
    private final PushNotificationServerHelper pushNotificationServerHelper;

    // Map for custom headers from inbound SIP MESSAGE
    private ConcurrentHashMap<String, String> customRequestHeaderMap = new ConcurrentHashMap<String, String>();
    private TlvSet tlvSet;

    private final DaoManager storage;

    private SmsSessionRequest initial;
    private SmsSessionRequest last;

    private final boolean smppActivated;
    private String externalIP;

    private final ServletContext servletContext;

    private ActorRef smppMessageHandler;

    private final ActorRef monitoringService;

    private final Sid fromOrganizationSid;

    public SmsSession(final Configuration configuration, final SipFactory factory, final SipURI transport,
                      final DaoManager storage, final ActorRef monitoringService, final ServletContext servletContext, final Sid fromOrganizationSid) {
        super();
        this.system = getContext().system();
        this.configuration = configuration;
        this.smsConfiguration = configuration.subset("sms-aggregator");
        this.factory = factory;
        this.observers = new ArrayList<ActorRef>();
        this.transport = transport;
        this.attributes = new HashMap<String, Object>();
        this.storage = storage;
        this.monitoringService = monitoringService;
        this.servletContext = servletContext;
        this.smppActivated = Boolean.parseBoolean(this.configuration.subset("smpp").getString("[@activateSmppConnection]", "false"));
        if (smppActivated) {
            smppMessageHandler = (ActorRef) servletContext.getAttribute(SmppMessageHandler.class.getName());
        }
        String defaultHost = transport.getHost();
        this.externalIP = this.configuration.subset("runtime-settings").getString("external-ip");
        if (externalIP == null || externalIP.isEmpty() || externalIP.equals(""))
            externalIP = defaultHost;
        this.fromOrganizationSid = fromOrganizationSid;

        this.tlvSet = new TlvSet();
        if(!this.configuration.subset("outbound-sms").isEmpty()) {
            //TODO: handle arbitrary keys instead of just TAG_DEST_NETWORK_ID
            try {
                String valStr = this.configuration.subset("outbound-sms").getString("destination_network_id");
                this.tlvSet.addOptionalParameter(new Tlv(SmppConstants.TAG_DEST_NETWORK_ID,ByteArrayUtil.toByteArray(Integer.parseInt(valStr))));
            } catch (Exception e) {
                logger.error("Error while parsing tlv configuration " + e);
            }
        }

        this.pushNotificationServerHelper = new PushNotificationServerHelper(system, configuration);
    }

    private void inbound(final Object message) throws IOException {
        if (message instanceof SipServletRequest) {
            final SipServletRequest request = (SipServletRequest) message;
            // Handle the SMS.
            SipURI uri = (SipURI) request.getFrom().getURI();
            final String from = uri.getUser();
            uri = (SipURI) request.getTo().getURI();
            final String to = uri.getUser();
            String body = null;
            if (request.getContentLength() > 0) {
                body = new String(request.getRawContent());
            }
            Iterator<String> headerIt = request.getHeaderNames();
            while (headerIt.hasNext()) {
                String headerName = headerIt.next();
                if (headerName.startsWith("X-")) {
                    customRequestHeaderMap.put(headerName, request.getHeader(headerName));
                }
            }
            // Store the last sms event.

            last = new SmsSessionRequest(from, to, body, this.tlvSet, customRequestHeaderMap);
            if (initial == null) {
                initial = last;
            }
            // Notify the observers.
            final ActorRef self = self();
            for (final ActorRef observer : observers) {
                observer.tell(last, self);
            }
        } else if (message instanceof SmppInboundMessageEntity) {
            final SmppInboundMessageEntity request = (SmppInboundMessageEntity) message;

            final SmsSessionRequest.Encoding encoding;
            if(request.getSmppEncoding().equals(CharsetUtil.CHARSET_UCS_2)) {
                encoding = SmsSessionRequest.Encoding.UCS_2;
            } else {
                encoding = SmsSessionRequest.Encoding.GSM;
            }
            // Store the last sms event.

            last = new SmsSessionRequest (request.getSmppFrom(), request.getSmppTo(), request.getSmppContent(), encoding, request.getTlvSet(), null);
            if (initial == null) {
                initial = last;
            }
            // Notify the observers.
            for (final ActorRef observer : observers) {
                observer.tell(last, self());
            }
        }
    }

    private SmsSessionInfo info() {
        final String from = initial.from();
        final String to = initial.to();
        final Map<String, Object> attributes = ImmutableMap.copyOf(this.attributes);
        return new SmsSessionInfo(from, to, attributes);
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

    @Override
    public void onReceive(final Object message) throws Exception {
        final Class<?> klass = message.getClass();
        final ActorRef self = self();
        final ActorRef sender = sender();
        if (Observe.class.equals(klass)) {
            observe(message);
        } else if (StopObserving.class.equals(klass)) {
            stopObserving(message);
        } else if (GetLastSmsRequest.class.equals(klass)) {
            sender.tell(last, self);
        } else if (SmsSessionAttribute.class.equals(klass)) {
            final SmsSessionAttribute attribute = (SmsSessionAttribute) message;
            attributes.put(attribute.name(), attribute.value());
            Object record = attributes.get(SMS_RECORD);
            if (record != null) {
                system.eventStream().publish(record);
            }
        } else if (SmsSessionRequest.class.equals(klass)) {
            outbound(message);
        } else if (message instanceof SipServletRequest) {
            inbound(message);
        } else if (message instanceof SipServletResponse) {
            response(message);
        } else if (message instanceof SmppInboundMessageEntity) {
            inbound(message);
        }
    }

    @Override
    public void postStop() {
        super.postStop();
        Object record = attributes.get(SMS_RECORD);
        if (record != null) {
            system.eventStream().publish(record);
        }
    }

    private void response(final Object message) {
        final SipServletResponse response = (SipServletResponse) message;
        final int status = response.getStatus();
        SmsSessionInfo info = info();
        SmsSessionResponse result = null;
        Object record = info.attributes().get(SMS_RECORD);
        if (SipServletResponse.SC_ACCEPTED == status || SipServletResponse.SC_OK == status) {
            if (record != null) {
                SmsMessage toBeUpdated = ((SmsMessage)record);
                SmsMessage.Builder builder = SmsMessage.builder();
                builder.copyMessage(toBeUpdated);
                builder.setDateSent(DateTime.now());
                builder.setStatus(SmsMessage.Status.SENT);
                this.attributes.put(SMS_RECORD, builder.build());
                info = info();
            }
            result = new SmsSessionResponse(info, true);
        } else {
            if (record != null) {
                SmsMessage toBeUpdated = ((SmsMessage)record);
                SmsMessage.Builder builder = SmsMessage.builder();
                builder.copyMessage(toBeUpdated);
                builder.setStatus(SmsMessage.Status.FAILED);
                this.attributes.put(SMS_RECORD, builder.build());
                info = info();
            }
            result = new SmsSessionResponse(info, false);
        }
        // Notify the observers.
        final ActorRef self = self();
        for (final ActorRef observer : observers) {
            observer.tell(result, self);
        }
    }

    private void outbound(final Object message) {
        last = (SmsSessionRequest) message;
        if (initial == null) {
            initial = last;
        }

        final Charset charset;
        if(logger.isInfoEnabled()) {
            logger.info("SMS encoding:  " + last.encoding() );
        }
        switch(last.encoding()) {
        case GSM:
            charset = CharsetUtil.CHARSET_GSM;
            break;
        case UCS_2:
            charset = CharsetUtil.CHARSET_UCS_2;
            break;
        case UTF_8:
            charset = CharsetUtil.CHARSET_UTF_8;
            break;
        default:
            charset = CharsetUtil.CHARSET_GSM;
        }

        monitoringService.tell(new TextMessage(last.from(), last.to(), TextMessage.SmsState.OUTBOUND), self());
        final ClientsDao clients = storage.getClientsDao();
        String to;
        if (last.to().toLowerCase().startsWith("client")) {
            to = last.to().replaceAll("client:","");
        } else {
            to = last.to();
        }
        final Client toClient = clients.getClient(to, fromOrganizationSid);

        long delay = 0;
        if (toClient == null) {
            //We will send using the SMPP link only if:
            // 1. This SMS is not for a registered client
            // 2, SMPP is activated
            if (smppActivated) {
                if(logger.isInfoEnabled()) {
                    logger.info("Destination is not a local registered client, therefore, sending through SMPP to:  " + last.to() );
                }

                if (sendUsingSmpp(last.from(), last.to(), last.body(), tlvSet, charset))
                    return;
            }
        } else {
            delay = pushNotificationServerHelper.sendPushNotificationIfNeeded(toClient.getPushClientIdentity());
        }
        system.scheduler().scheduleOnce(Duration.create(delay, TimeUnit.MILLISECONDS), new Runnable() {
            @Override
            public void run() {
                sendUsingSip(toClient, (SmsSessionRequest) message);
            }
        }, system.dispatcher());
    }

    private boolean sendUsingSmpp(String from, String to, String body, Charset encoding) {
        return sendUsingSmpp(from, to, body, null, encoding);
    }
    private boolean sendUsingSmpp(String from, String to, String body, TlvSet tlvSet, Charset encoding) {
        if ((SmppClientOpsThread.getSmppSession() != null && SmppClientOpsThread.getSmppSession().isBound()) && smppMessageHandler != null) {
            if(logger.isInfoEnabled()) {
                logger.info("SMPP session is available and connected, outbound message will be forwarded to :  " + to );
                logger.info("Encoding:  " + encoding );
            }

            SmsMessage record = (SmsMessage)this.attributes.get(SMS_RECORD);
            Sid sid = null;
            if(record!=null) {
                sid = record.getSid();
                if(logger.isInfoEnabled()) {
                    logger.info("record sid = "+sid.toString());
                }
            }else{
                logger.error("record is null");
            }
            try {
                final SmppOutboundMessageEntity sms = new SmppOutboundMessageEntity(to, from, body, encoding, tlvSet, sid);
                smppMessageHandler.tell(sms, null);
            }catch (final Exception exception) {
                // Log the exception.
                logger.error("There was an error sending SMS to SMPP endpoint : " + exception);
            }
            return true;
        }
        return false;
    }

    private void sendUsingSip(Client toClient, SmsSessionRequest request) {
        Registration toClientRegistration = null;
        if (toClient != null) {
            final RegistrationsDao registrations = storage.getRegistrationsDao();
            toClientRegistration = registrations.getRegistration(toClient.getLogin(), fromOrganizationSid);
        }

        final SipApplicationSession application = factory.createApplicationSession();
        StringBuilder buffer = new StringBuilder();
        //buffer.append("sip:").append(from).append("@").append(transport.getHost() + ":" + transport.getPort());
        buffer.append("sip:").append(request.from()).append("@").append(externalIP + ":" + transport.getPort());
        final String sender = buffer.toString();
        buffer = new StringBuilder();
        if (toClientRegistration != null) {
            buffer.append(toClientRegistration.getLocation());
        } else {
            final String service = smsConfiguration.getString("outbound-endpoint");
            if (service == null) {
                return;
            }
            buffer.append("sip:");
            final String prefix = smsConfiguration.getString("outbound-prefix");
            if (prefix != null) {
                buffer.append(prefix);
            }
            buffer.append(request.to()).append("@").append(service);
        }
        final String recipient = buffer.toString();

        try {
            application.setAttribute(SmsSession.class.getName(), self());
            if (request.getOrigRequest() != null) {
                application.setAttribute(SipServletRequest.class.getName(), request.getOrigRequest());
            }
            final SipServletRequest sms = factory.createRequest(application, "MESSAGE", sender, recipient);
            final SipURI uri = (SipURI) factory.createURI(recipient);
            sms.pushRoute(uri);
            sms.setRequestURI(uri);
            sms.setContent(request.body(), "text/plain");
            final SipSession session = sms.getSession();
            session.setHandler("SmsService");
            Map<String, String> headers = request.headers();
            if (headers != null) {
                for (Map.Entry<String, String> header : headers.entrySet()) {
                    sms.setHeader(header.getKey(), header.getValue());
                }
            }
            sms.send();
        } catch (final Exception exception) {
            // Notify the observers.
            SmsSessionInfo info = info();
            Object record = info.attributes().get(SMS_RECORD);
            if (record != null) {
                SmsMessage toBeUpdated = ((SmsMessage)record);
                SmsMessage.Builder builder = SmsMessage.builder();
                builder.copyMessage(toBeUpdated);
                builder.setStatus(SmsMessage.Status.FAILED);
                this.attributes.put(SMS_RECORD, builder.build());
                info = info();
            }
            final SmsSessionResponse error = new SmsSessionResponse(info, false);
            for (final ActorRef observer : observers) {
                observer.tell(error, self());
            }
            // Log the exception.
            logger.error(exception.getMessage(), exception);
        }
    }

    private void stopObserving(final Object message) {
        final StopObserving request = (StopObserving) message;
        final ActorRef observer = request.observer();
        if (observer != null) {
            observers.remove(observer);
        }
    }
}
