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

import akka.actor.ActorRef;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import com.cloudhopper.smpp.SmppBindType;
import com.cloudhopper.smpp.impl.DefaultSmppClient;
import com.cloudhopper.smpp.type.Address;
import org.apache.commons.configuration.Configuration;
import org.restcomm.connect.commons.faulttolerance.RestcommUntypedActor;
import org.restcomm.connect.dao.DaoManager;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.sip.SipFactory;
import javax.servlet.sip.SipURI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;

import static javax.servlet.sip.SipServlet.OUTBOUND_INTERFACES;

/**
 *
 * @author amit bhayani
 * @author gvagenas@telestax.com
 *
 */
public final class SmppService extends RestcommUntypedActor {
    private final LoggingAdapter logger = Logging.getLogger(getContext().system(), this);

    private final ActorRef smppMessageHandler;
    private final Configuration configuration;
    private boolean authenticateUsers = true;
    private final ServletConfig servletConfig;
    private final SipFactory sipFactory;
    private final DaoManager storage;
    private final ServletContext servletContext;
    private static String smppActivated;

    static final int ERROR_NOTIFICATION = 0;
    static final int WARNING_NOTIFICATION = 1;
    private static String smppSourceAddressMap;
    private static String smppDestinationAddressMap;
    private static String smppTonNpiValue;

    private ThreadPoolExecutor executor;
    private ScheduledThreadPoolExecutor monitorExecutor;

    private DefaultSmppClient clientBootstrap = null;

    private SmppClientOpsThread smppClientOpsThread = null;

    private ArrayList<Smpp> smppList = new ArrayList<Smpp>();

    public SmppService(final Configuration configuration, final SipFactory factory,
                       final DaoManager storage, final ServletContext servletContext, final ActorRef smppMessageHandler) {

        super();
        this.smppMessageHandler = smppMessageHandler;
        this.configuration = configuration;
        final Configuration runtime = configuration.subset("runtime-settings");
        this.authenticateUsers = runtime.getBoolean("authenticate");
        this.servletConfig = (ServletConfig) configuration.getProperty(ServletConfig.class.getName());
        this.sipFactory = factory;
        this.storage = storage;
        this.servletContext = servletContext;

        Configuration config = this.configuration.subset("smpp");
        smppActivated = config.getString("[@activateSmppConnection]");

        //get smpp address map from restcomm.xml file
        this.smppSourceAddressMap = config.getString("connections.connection[@sourceAddressMap]");
        this.smppDestinationAddressMap = config.getString("connections.connection[@destinationAddressMap]");
        this.smppTonNpiValue = config.getString("connections.connection[@tonNpiValue]");

        this.initializeSmppConnections();
    }


    public static String getSmppTonNpiValue(){
        return smppTonNpiValue;
    }

    @Override
    public void onReceive(Object message) throws Exception {}


    private void initializeSmppConnections() {
        Configuration smppConfiguration = this.configuration.subset("smpp");

        List<Object> smppConnections = smppConfiguration.getList("connections.connection.name");

        int smppConnecsSize = smppConnections.size();
        if (smppConnecsSize == 0) {
            logger.warning("No SMPP Connections defined!");
            return;
        }

        for (int count = 0; count < smppConnecsSize; count++) {
            String name = smppConfiguration.getString("connections.connection(" + count + ").name");
            String systemId = smppConfiguration.getString("connections.connection(" + count + ").systemid");
            String peerIp = smppConfiguration.getString("connections.connection(" + count + ").peerip");
            int peerPort = smppConfiguration.getInt("connections.connection(" + count + ").peerport");
            SmppBindType bindtype = SmppBindType.valueOf(smppConfiguration.getString("connections.connection(" + count
                    + ").bindtype"));

            if (bindtype == null) {
                logger.warning("Bindtype for SMPP name=" + name + " is not specified. Using default TRANSCEIVER");
            }

            String password = smppConfiguration.getString("connections.connection(" + count + ").password");
            String systemType = smppConfiguration.getString("connections.connection(" + count + ").systemtype");

            byte interfaceVersion = smppConfiguration.getByte("connections.connection(" + count + ").interfaceversion");

            byte ton = smppConfiguration.getByte("connections.connection(" + count + ").ton");
            byte npi = smppConfiguration.getByte("connections.connection(" + count + ").npi");
            String range = smppConfiguration.getString("connections.connection(" + count + ").range");

            Address address = null;
            if (ton != -1 && npi != -1 && range != null) {
                address = new Address(ton, npi, range);
            }

            int windowSize = smppConfiguration.getInt("connections.connection(" + count + ").windowsize");

            long windowWaitTimeout = smppConfiguration.getLong("connections.connection(" + count + ").windowwaittimeout");

            long connectTimeout = smppConfiguration.getLong("connections.connection(" + count + ").connecttimeout");
            long requestExpiryTimeout = smppConfiguration.getLong("connections.connection(" + count + ").requestexpirytimeout");
            long windowMonitorInterval = smppConfiguration.getLong("connections.connection(" + count
                    + ").windowmonitorinterval");
            boolean logBytes = smppConfiguration.getBoolean("connections.connection(" + count + ").logbytes");
            boolean countersEnabled = smppConfiguration.getBoolean("connections.connection(" + count + ").countersenabled");

            long enquireLinkDelay = smppConfiguration.getLong("connections.connection(" + count + ").enquirelinkdelay");
            String inboundCharacterEncoding = smppConfiguration.getString("connections.connection(" + count + ").inboundencoding");
            String outboundCharacterEncoding = smppConfiguration.getString("connections.connection(" + count + ").outboundencoding");
            boolean messagePayloadFlag = smppConfiguration.getBoolean("connections.connection(" + count + ").messagepayloadflag");
            boolean autoDetectDcsFlag = smppConfiguration.getBoolean("connections.connection(" + count + ").autodetectdcsflag");

            Smpp smpp = new Smpp(name, systemId, peerIp, peerPort, bindtype, password, systemType, interfaceVersion, address,
                    connectTimeout, windowSize, windowWaitTimeout, requestExpiryTimeout, windowMonitorInterval,
                    countersEnabled, logBytes, enquireLinkDelay, inboundCharacterEncoding, outboundCharacterEncoding, messagePayloadFlag, autoDetectDcsFlag);

            this.smppList.add(smpp);

            if(logger.isInfoEnabled()) {
                logger.info("creating new SMPP connection " + smpp);
            }

        }

        // for monitoring thread use, it's preferable to create your own
        // instance of an executor and cast it to a ThreadPoolExecutor from
        // Executors.newCachedThreadPool() this permits exposing thinks like
        // executor.getActiveCount() via JMX possible no point renaming the
        // threads in a factory since underlying Netty framework does not easily
        // allow you to customize your thread names
        this.executor = (ThreadPoolExecutor) Executors.newCachedThreadPool();

        // to enable automatic expiration of requests, a second scheduled
        // executor is required which is what a monitor task will be executed
        // with - this is probably a thread pool that can be shared with between
        // all client bootstraps
        this.monitorExecutor = (ScheduledThreadPoolExecutor) Executors.newScheduledThreadPool(1, new ThreadFactory() {
            private AtomicInteger sequence = new AtomicInteger(0);

            @Override
            public Thread newThread(Runnable r) {
                Thread t = new Thread(r);
                t.setName("SmppServer-SessionWindowMonitorPool-" + sequence.getAndIncrement());
                return t;
            }
        });

        // a single instance of a client bootstrap can technically be shared
        // between any sessions that are created (a session can go to any
        // different number of SMSCs) - each session created under a client
        // bootstrap will use the executor and monitorExecutor set in its
        // constructor - just be *very* careful with the "expectedSessions"
        // value to make sure it matches the actual number of total concurrent
        // open sessions you plan on handling - the underlying netty library
        // used for NIO sockets essentially uses this value as the max number of
        // threads it will ever use, despite the "max pool size", etc. set on
        // the executor passed in here

        // Setting expected session to be 25. May be this should be
        // configurable?
        this.clientBootstrap = new DefaultSmppClient(this.executor, 25, monitorExecutor);

        this.smppClientOpsThread = new SmppClientOpsThread(this.clientBootstrap, outboundInterface("udp").getPort(), smppMessageHandler);

        (new Thread(this.smppClientOpsThread)).start();

        for(Smpp smpp : this.smppList){
            this.smppClientOpsThread.scheduleConnect(smpp);
        }

        if(logger.isInfoEnabled()) {
            logger.info("SMPP Service started");
        }
    }

    private SipURI outboundInterface(String transport) {
        SipURI result = null;
        @SuppressWarnings("unchecked")
        final List<SipURI> uris = (List<SipURI>) this.servletContext.getAttribute(OUTBOUND_INTERFACES);
        for (final SipURI uri : uris) {
            final String interfaceTransport = uri.getTransportParam();
            if (transport.equalsIgnoreCase(interfaceTransport)) {
                result = uri;
            }
        }
        return result;
    }

}
