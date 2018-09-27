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
package org.restcomm.connect.telephony;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.actor.UntypedActorFactory;
import org.apache.commons.configuration.Configuration;
import org.apache.log4j.Logger;
import org.restcomm.connect.dao.DaoManager;
import org.restcomm.connect.mscontrol.api.MediaServerControllerFactory;
import org.restcomm.connect.sms.SmsService;
import org.restcomm.connect.telephony.api.util.B2BUAHelper;
import org.restcomm.connect.ussd.telephony.UssdCallManager;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.sip.SipApplicationSession;
import javax.servlet.sip.SipFactory;
import javax.servlet.sip.SipServlet;
import javax.servlet.sip.SipServletContextEvent;
import javax.servlet.sip.SipServletListener;
import javax.servlet.sip.SipServletMessage;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;
import javax.servlet.sip.SipSession;
import javax.sip.message.Request;
import javax.sip.message.Response;
import java.io.IOException;
import javax.servlet.sip.SipApplicationSessionEvent;
import javax.servlet.sip.SipApplicationSessionListener;

/**
 * @author quintana.thomas@gmail.com (Thomas Quintana)
 * @author <a href="mailto:gvagenas@gmail.com">gvagenas</a>
 */
public final class CallManagerProxy extends SipServlet implements SipServletListener, SipApplicationSessionListener {
    private static final long serialVersionUID = 1L;

    private static final Logger logger = Logger.getLogger(CallManagerProxy.class);

    private boolean sendTryingForInitalRequests = false;

    private ActorSystem system;
    private ActorRef manager;
    private ActorRef ussdManager;
    private ServletContext context;

    private Configuration configuration;

    public CallManagerProxy() {
        super();
    }

    @Override
    public void destroy() {
        if (system != null) {
            system.shutdown();
            system.awaitTermination();
        }
    }

    @Override
    protected void doRequest(final SipServletRequest request) throws ServletException, IOException {
        if (isUssdMessage(request)) {
            ussdManager.tell(request, null);
        } else {
            if (request.isInitial() && sendTryingForInitalRequests) {
                SipServletResponse resp = request.createResponse(Response.TRYING);
                resp.send();
            }
            manager.tell(request, null);
        }
    }

    @Override
    protected void doResponse(final SipServletResponse response) throws ServletException, IOException {
        if (response.getMethod().equals(Request.BYE) && response.getStatus() >= 200) {
            SipSession sipSession = response.getSession();
            SipApplicationSession sipApplicationSession = response.getApplicationSession();
            if (sipSession.isValid()) {
                sipSession.setInvalidateWhenReady(true);
            }
            if (sipApplicationSession.isValid()) {
                sipApplicationSession.setInvalidateWhenReady(true);
            }
            return;
        }
        if (isUssdMessage(response)) {
            ussdManager.tell(response, null);
        } else {
            manager.tell(response, null);
        }
    }

    @Override
    public void init(final ServletConfig config) throws ServletException {
        super.init(config);
    }

    private ActorRef manager(final Configuration configuration, final ServletContext context,
            final MediaServerControllerFactory msControllerfactory, final ActorRef conferences, final ActorRef bridges,
            final ActorRef sms, final SipFactory factory, final DaoManager storage) {
        final Props props = new Props(new UntypedActorFactory() {
            private static final long serialVersionUID = 1L;
            @Override
            public UntypedActor create() throws Exception {
                return new CallManager(configuration, context, msControllerfactory, conferences, bridges, sms, factory, storage);
            }
        });
        return system.actorOf(props);
    }

    private ActorRef ussdManager(final Configuration configuration, final ServletContext context, final SipFactory factory, final DaoManager storage) {

        final Props props = new Props(new UntypedActorFactory() {
            private static final long serialVersionUID = 1L;
            @Override
            public UntypedActor create() throws Exception {
                return new UssdCallManager(configuration, context, factory, storage);
            }
        });
        return system.actorOf(props);
    }

    private ActorRef conferences(final MediaServerControllerFactory factory, final DaoManager storage) {

        final Props props = new Props(new UntypedActorFactory() {
            private static final long serialVersionUID = 1L;
            @Override
            public UntypedActor create() throws Exception {
                return new ConferenceCenter(factory, storage);
            }
        });
        return system.actorOf(props);
    }

    private ActorRef bridges(final MediaServerControllerFactory factory) {
        final Props props = new Props(new UntypedActorFactory() {
            private static final long serialVersionUID = 1L;
            @Override
            public UntypedActor create() throws Exception {
                return new BridgeManager(factory);
            }
        });
        return system.actorOf(props);
    }

    private boolean isUssdMessage(SipServletMessage message) {
        Boolean isUssd = false;
        String contentType = null;
        try {
            contentType = message.getContentType();
        } catch (Exception e) {
        }
        if (contentType != null) {
            isUssd = contentType.equalsIgnoreCase("application/vnd.3gpp.ussd+xml");
        } else {
            if (message.getApplicationSession().getAttribute("UssdCall") != null) {
                isUssd = true;
            }
        }
        return isUssd;
    }

    @Override
    public void servletInitialized(SipServletContextEvent event) {
        if (event.getSipServlet().getClass().equals(CallManagerProxy.class)) {
            if(logger.isInfoEnabled()) {
                logger.info("CallManagerProxy sip servlet initialized. Will proceed to create CallManager and UssdManager");
            }
            context = event.getServletContext();
            configuration = (Configuration) context.getAttribute(Configuration.class.getName());
            sendTryingForInitalRequests = Boolean.parseBoolean(configuration.subset("runtime-settings").getString("send-trying-for-initial-requests", "false"));
            system = (ActorSystem) context.getAttribute(ActorSystem.class.getName());
            final DaoManager storage = (DaoManager) context.getAttribute(DaoManager.class.getName());
            final MediaServerControllerFactory mscontrolFactory = (MediaServerControllerFactory) context
                    .getAttribute(MediaServerControllerFactory.class.getName());
            // Create the call manager.
            final SipFactory factory = (SipFactory) context.getAttribute(SIP_FACTORY);
            final ActorRef conferences = conferences(mscontrolFactory, storage);
            final ActorRef bridges = bridges(mscontrolFactory);
            final ActorRef sms = (ActorRef) context.getAttribute(SmsService.class.getName());
            manager = manager(configuration, context, mscontrolFactory, conferences, bridges, sms, factory, storage);
            ussdManager = ussdManager(configuration, context, factory, storage);
            context.setAttribute(CallManager.class.getName(), manager);
            context.setAttribute(UssdCallManager.class.getName(), ussdManager);
        }
    }

    @Override
    public void sessionCreated(SipApplicationSessionEvent sase) {

    }

    @Override
    public void sessionDestroyed(SipApplicationSessionEvent sase) {

    }

    /**
     * extension time added on expiration.
     */
    private static final int EXPIRATION_GRACE_PERIOD = 1;

    @Override
    public void sessionExpired(SipApplicationSessionEvent sase) {
        //check attribute signlling Akka actor completed timeout processing
        //if att is present, let the sesion just expire
        logger.debug("Session expired");
        if (sase.getApplicationSession().getAttribute(B2BUAHelper.B2BUA_CALL) != null && sase.getApplicationSession().getAttribute(CallManager.TIMEOUT_ATT) == null) {
            logger.debug("Session expired still not processed");
            //extend expiration a bit,to let sessions be properly disconnected
            sase.getApplicationSession().setExpires(EXPIRATION_GRACE_PERIOD);
            manager.tell(sase, manager);
        }

    }

    @Override
    public void sessionReadyToInvalidate(SipApplicationSessionEvent sase) {

    }
}
