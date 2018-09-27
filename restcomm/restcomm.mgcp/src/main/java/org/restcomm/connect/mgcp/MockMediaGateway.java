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
package org.restcomm.connect.mgcp;

import akka.actor.Actor;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.actor.ReceiveTimeout;
import akka.actor.UntypedActor;
import akka.actor.UntypedActorContext;
import akka.actor.UntypedActorFactory;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import jain.protocol.ip.mgcp.JainMgcpCommandEvent;
import jain.protocol.ip.mgcp.JainMgcpResponseEvent;
import jain.protocol.ip.mgcp.message.CreateConnectionResponse;
import jain.protocol.ip.mgcp.message.DeleteConnection;
import jain.protocol.ip.mgcp.message.DeleteConnectionResponse;
import jain.protocol.ip.mgcp.message.ModifyConnection;
import jain.protocol.ip.mgcp.message.ModifyConnectionResponse;
import jain.protocol.ip.mgcp.message.NotificationRequest;
import jain.protocol.ip.mgcp.message.NotificationRequestResponse;
import jain.protocol.ip.mgcp.message.Notify;
import jain.protocol.ip.mgcp.message.parms.ConnectionDescriptor;
import jain.protocol.ip.mgcp.message.parms.ConnectionIdentifier;
import jain.protocol.ip.mgcp.message.parms.EndpointIdentifier;
import jain.protocol.ip.mgcp.message.parms.EventName;
import jain.protocol.ip.mgcp.message.parms.NotifiedEntity;
import jain.protocol.ip.mgcp.message.parms.ReturnCode;
import jain.protocol.ip.mgcp.pkg.MgcpEvent;
import org.joda.time.DateTime;
import org.mobicents.protocols.mgcp.jain.pkg.AUMgcpEvent;
import org.mobicents.protocols.mgcp.jain.pkg.AUPackage;
import org.restcomm.connect.commons.faulttolerance.RestcommUntypedActor;
import org.restcomm.connect.commons.util.RevolvingCounter;
import org.restcomm.connect.commons.util.WavUtils;
import org.restcomm.connect.mgcp.stats.MgcpConnectionAdded;
import org.restcomm.connect.mgcp.stats.MgcpConnectionDeleted;
import org.restcomm.connect.mgcp.stats.MgcpEndpointAdded;
import org.restcomm.connect.mgcp.stats.MgcpEndpointDeleted;
import scala.concurrent.duration.Duration;

import javax.sdp.SdpFactory;
import javax.sdp.SdpParseException;
import javax.sdp.SessionDescription;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * @author quintana.thomas@gmail.com (Thomas Quintana)
 */
public class MockMediaGateway extends RestcommUntypedActor {
    protected final LoggingAdapter logger = Logging.getLogger(getContext().system(), this);

    // Session description for the mock media gateway.
    private static final String sdp = "v=0\n" + "o=- 1362546170756 1 IN IP4 192.168.1.100\n" + "s=Mobicents Media Server\n"
            + "c=IN IP4 192.168.1.100\n" + "t=0 0\n" + "m=audio 63044 RTP/AVP 97 8 0 101\n" + "a=rtpmap:97 l16/8000\n"
            + "a=rtpmap:8 pcma/8000\n" + "a=rtpmap:0 pcmu/8000\n" + "a=rtpmap:101 telephone-event/8000\n" + "a=fmtp:101 0-15\n";

    // MediaGateway connection information.
    private String name;
    private InetAddress localIp;
    private int localPort;
    private InetAddress remoteIp;
    private int remotePort;
    // Used for NAT traversal.
    private boolean useNat;
    private InetAddress externalIp;
    // Used to detect dead media gateways.
    private long timeout;
    // Call agent.
    private NotifiedEntity agent;
    // Media gateway domain name.
    private String domain;
    // Runtime stuff.
    private RevolvingCounter requestIdPool;
    private RevolvingCounter sessionIdPool;
    protected RevolvingCounter transactionIdPool;
    protected RevolvingCounter connectionIdPool;
    private RevolvingCounter endpointIdPool;

    private Map<String, String> connEndpointMap;

    private ActorRef monitoringService;

    protected File recordingFile = null;
    private int recordingMaxLength;

    private DateTime startTime;
    private DateTime endTime;

    private ActorSystem system;
    private NotificationRequest recordingRqnt;
    private ActorRef recordingRqntSender;

    private boolean recording = false;

    public MockMediaGateway () {
        super();
        system = context().system();
        connEndpointMap = new ConcurrentHashMap<String, String>();
    }

    private ActorRef getConnection (final Object message) {
        final CreateConnection request = (CreateConnection) message;
        final MediaSession session = request.session();
        final ActorRef gateway = self();
        final Props props = new Props(new UntypedActorFactory() {
            private static final long serialVersionUID = 1L;

            @Override
            public UntypedActor create () throws Exception {
                return new MockConnection(gateway, session, agent, timeout);
            }
        });
        ActorRef connection = system.actorOf(props);
        if (logger.isInfoEnabled()) {
            String msg = String.format("MockMediaGateway, Added new Connection, path: %s", connection.path());
            logger.info(msg);
        }
        return connection;
    }

    private ActorRef getBridgeEndpoint (final Object message) {
        final CreateBridgeEndpoint request = (CreateBridgeEndpoint) message;
        final ActorRef gateway = self();
        final MediaSession session = request.session();
        final Props props = new Props(new UntypedActorFactory() {
            private static final long serialVersionUID = 1L;

            @Override
            public Actor create () throws Exception {
                return new BridgeEndpoint(gateway, session, agent, domain, timeout);
            }
        });
        ActorRef bridgeEndpoint = system.actorOf(props);
        if (logger.isInfoEnabled()) {
            String msg = String.format("MockMediaGateway, Added Bridge endpoint, path: %s", bridgeEndpoint.path());
            logger.info(msg);
        }
        return bridgeEndpoint;
    }

    private ActorRef getConferenceEndpoint (final Object message) {
        final ActorRef gateway = self();
        final CreateConferenceEndpoint request = (CreateConferenceEndpoint) message;
        final MediaSession session = request.session();
        final String endpointName = request.endpointName();

        Props props = null;

        props = new Props(new UntypedActorFactory() {
            private static final long serialVersionUID = 1L;

            @Override
            public UntypedActor create () throws Exception {
                return new ConferenceEndpoint(gateway, session, agent, domain, timeout, endpointName);
            }
        });
        ActorRef conferenceEndpoint = system.actorOf(props);
        if (logger.isInfoEnabled()) {
            String msg = String.format("MockMediaGateway, Added Conference endpoint, path: %s", conferenceEndpoint.path());
            logger.info(msg);
        }
        return conferenceEndpoint;
    }

    private MediaGatewayInfo getInfo (final Object message) {
        return new MediaGatewayInfo(name, remoteIp, remotePort, useNat, externalIp);
    }

    private ActorRef getIvrEndpoint (final Object message) {
        final ActorRef gateway = self();
        final CreateIvrEndpoint request = (CreateIvrEndpoint) message;
        final MediaSession session = request.session();
        final String endpointName = request.endpointName();

        final Props props = new Props(new UntypedActorFactory() {
            private static final long serialVersionUID = 1L;

            @Override
            public UntypedActor create () throws Exception {
                return new IvrEndpoint(gateway, session, agent, domain, timeout, endpointName);
            }
        });
        ActorRef ivrEndpoint = system.actorOf(props);
        if (logger.isInfoEnabled()) {
            String msg = String.format("MockMediaGateway, Added Ivr endpoint, path: %s", ivrEndpoint.path());
            logger.info(msg);
        }
        return ivrEndpoint;
    }

    private ActorRef getLink (final Object message) {
        final CreateLink request = (CreateLink) message;
        final ActorRef gateway = self();
        final MediaSession session = request.session();
        final Props props = new Props(new UntypedActorFactory() {
            private static final long serialVersionUID = 1L;

            @Override
            public UntypedActor create () throws Exception {
                return new Link(gateway, session, agent, timeout);
            }
        });
        ActorRef link = system.actorOf(props);
        if (logger.isInfoEnabled()) {
            String msg = String.format("MockMediaGateway, Added new Link, path: %s", link.path());
            logger.info(msg);
        }
        return link;
    }

    private ActorRef getPacketRelayEndpoint (final Object message) {
        final ActorRef gateway = self();
        final CreatePacketRelayEndpoint request = (CreatePacketRelayEndpoint) message;
        final MediaSession session = request.session();
        final Props props = new Props(new UntypedActorFactory() {
            private static final long serialVersionUID = 1L;

            @Override
            public UntypedActor create () throws Exception {
                return new PacketRelayEndpoint(gateway, session, agent, domain, timeout);
            }
        });
        ActorRef packetRelayEndpoint = system.actorOf(props);
        if (logger.isInfoEnabled()) {
            String msg = String.format("MockMediaGateway, Added PacketRelay endpoint, path: %s", packetRelayEndpoint.path());
            logger.info(msg);
        }
        return packetRelayEndpoint;
    }

    private MediaSession getSession () {
        return new MediaSession((int) sessionIdPool.get());
    }

    private void powerOff (final Object message) {
        // Make sure we don't leave anything behind.
        name = null;
        localIp = null;
        localPort = 0;
        remoteIp = null;
        remotePort = 0;
        useNat = false;
        externalIp = null;
        timeout = 0;
        agent = null;
        domain = null;
        requestIdPool = null;
        sessionIdPool = null;
        transactionIdPool = null;
    }

    private void powerOn (final Object message) {
        final PowerOnMediaGateway request = (PowerOnMediaGateway) message;
        name = request.getName();
        localIp = request.getLocalIp();
        localPort = request.getLocalPort();
        remoteIp = request.getRemoteIp();
        remotePort = request.getRemotePort();
        useNat = request.useNat();
        externalIp = request.getExternalIp();
        timeout = request.getTimeout();
        agent = new NotifiedEntity("restcomm", localIp.getHostAddress(), localPort);
        domain = new StringBuilder().append(remoteIp.getHostAddress()).append(":").append(remotePort).toString();
        connectionIdPool = new RevolvingCounter(1, Integer.MAX_VALUE);
        endpointIdPool = new RevolvingCounter(1, Integer.MAX_VALUE);
        requestIdPool = new RevolvingCounter(1, Integer.MAX_VALUE);
        sessionIdPool = new RevolvingCounter(1, Integer.MAX_VALUE);
        transactionIdPool = new RevolvingCounter(1, Integer.MAX_VALUE);
        monitoringService = request.getMonitoringService();
    }

    @Override
    public void onReceive (final Object message) throws Exception {
        final UntypedActorContext context = getContext();
        final Class<?> klass = message.getClass();
        final ActorRef self = self();
        final ActorRef sender = sender();
        if (PowerOnMediaGateway.class.equals(klass)) {
            powerOn(message);
        } else if (PowerOffMediaGateway.class.equals(klass)) {
            powerOff(message);
        } else if (GetMediaGatewayInfo.class.equals(klass)) {
            sender.tell(new MediaGatewayResponse<MediaGatewayInfo>(getInfo(message)), sender);
        } else if (CreateConnection.class.equals(klass)) {
            sender.tell(new MediaGatewayResponse<ActorRef>(getConnection(message)), self);
        } else if (CreateLink.class.equals(klass)) {
            sender.tell(new MediaGatewayResponse<ActorRef>(getLink(message)), self);
        } else if (CreateMediaSession.class.equals(klass)) {
            sender.tell(new MediaGatewayResponse<MediaSession>(getSession()), self);
        } else if (CreateBridgeEndpoint.class.equals(klass)) {
            final ActorRef endpoint = getBridgeEndpoint(message);
            sender.tell(new MediaGatewayResponse<ActorRef>(endpoint), self);
        } else if (CreatePacketRelayEndpoint.class.equals(klass)) {
            final ActorRef endpoint = getPacketRelayEndpoint(message);
            sender.tell(new MediaGatewayResponse<ActorRef>(endpoint), self);
        } else if (CreateIvrEndpoint.class.equals(klass)) {
            final ActorRef endpoint = getIvrEndpoint(message);
            sender.tell(new MediaGatewayResponse<ActorRef>(endpoint), self);
        } else if (CreateConferenceEndpoint.class.equals(klass)) {
            final ActorRef endpoint = getConferenceEndpoint(message);
            sender.tell(new MediaGatewayResponse<ActorRef>(endpoint), self);
        } else if (DestroyConnection.class.equals(klass)) {
            final DestroyConnection request = (DestroyConnection) message;
            if (logger.isInfoEnabled()) {
                String msg = String.format("MockMediaGateway, Connection destroyed, path %s", request.connection().path());
                logger.info(msg);
            }
            context.stop(request.connection());
        } else if (DestroyLink.class.equals(klass)) {
            final DestroyLink request = (DestroyLink) message;
            if (logger.isInfoEnabled()) {
                String msg = String.format("MockMediaGateway, Link destroyed, path %s", request.link().path());
                logger.info(msg);
            }
            context.stop(request.link());
        } else if (DestroyEndpoint.class.equals(klass)) {
            final DestroyEndpoint request = (DestroyEndpoint) message;
            if (logger.isInfoEnabled()) {
                String msg = String.format("MockMediaGateway, Endpoint destroyed, path %s", request.endpoint().path());
                logger.info(msg);
            }
            context.stop(request.endpoint());
        } else if (message instanceof JainMgcpCommandEvent) {
            send(message, sender);
        } else if (message instanceof JainMgcpResponseEvent) {
            send(message);
        } else if (message instanceof ReceiveTimeout) {
            onReceiveTimeout(message);
        }
    }

    private void onReceiveTimeout (Object message) {
        getContext().setReceiveTimeout(Duration.Undefined());
        if (recording) {
            if (logger.isInfoEnabled()) {
                logger.info("Max recording length reached");
            }
            stopRecording();
            notify(recordingRqnt, recordingRqntSender);
        }
    }

    private void writeRecording (File srcWaveFile, File outWaveFile, int duration) {
        DateTime start = new DateTime();
        DateTime end;
        long operationDuration;

        AudioInputStream audioInputStream = null;
        AudioInputStream shortenedStream = null;
        try {
            File tempFile = File.createTempFile("tempRecording", ".wav");
            tempFile.deleteOnExit();
            if (outWaveFile.exists()) {
                String msg = String.format("Recording file %s doesn't exist, will create it", outWaveFile);
                logger.warning(msg);
                outWaveFile.createNewFile();
            }
            audioInputStream = AudioSystem.getAudioInputStream(srcWaveFile);

            AudioFileFormat fileFormat = AudioSystem.getAudioFileFormat(srcWaveFile);
            AudioFormat format = fileFormat.getFormat();

            int bytesPerSecond = format.getFrameSize() * (int) format.getFrameRate();

            long framesOfAudioToCopy = duration * (int) format.getFrameRate();
            shortenedStream = new AudioInputStream(audioInputStream, format, framesOfAudioToCopy);

            AudioSystem.write(shortenedStream, fileFormat.getType(), tempFile);

            Files.move(tempFile.toPath(), outWaveFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

            double recordedDuration = WavUtils.getAudioDuration(outWaveFile);
            String msg = String.format("Write to recording file %s completed, duration %6.0f", outWaveFile, recordedDuration);
            logger.info(msg);

        } catch (UnsupportedAudioFileException | IOException e) {
            String msg = String.format("Exception while trying to write to recording file %s for duration of %d, exception %s", outWaveFile, duration, e);
            logger.error(msg);
        } finally {
            if (audioInputStream != null) try {
                audioInputStream.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
            if (shortenedStream != null) try {
                shortenedStream.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
            end = new DateTime();
            operationDuration = (end.getMillis() - start.getMillis());
            String msg = String.format("Write to recording file %s completed, operation duration %d ms", outWaveFile, operationDuration);
            logger.info(msg);
        }

    }

    protected void createConnection (final Object message, final ActorRef sender) {
        final ActorRef self = self();
        final jain.protocol.ip.mgcp.message.CreateConnection crcx = (jain.protocol.ip.mgcp.message.CreateConnection) message;
        System.out.println(crcx.toString());
        // Create a response.
        StringBuilder buffer = new StringBuilder();
        buffer.append(connectionIdPool.get());
        ConnectionIdentifier connId = new ConnectionIdentifier(buffer.toString());
        final ReturnCode code = ReturnCode.Transaction_Executed_Normally;
        final CreateConnectionResponse response = new CreateConnectionResponse(self, code, connId);
        // Create a new end point id if necessary.
        EndpointIdentifier endpointId = crcx.getEndpointIdentifier();
        String endpointName = endpointId.getLocalEndpointName();
        if (endpointName.endsWith("$")) {
            final String[] tokens = endpointName.split("/");
            final String type = tokens[1];
            buffer = new StringBuilder();
            buffer.append("mobicents/").append(type).append("/");
            buffer.append(endpointIdPool.get());
            endpointId = new EndpointIdentifier(buffer.toString(), domain);
        }
        connEndpointMap.put(connId.toString(), endpointId.getLocalEndpointName());
        if (logger.isInfoEnabled()) {
            String msg = String.format("About to add connId %s for endpoint %s", connId.toString(), endpointId.getLocalEndpointName());
            logger.info(msg);
        }
        monitoringService.tell(new MgcpEndpointAdded(connId.toString(), endpointId.getLocalEndpointName()), self());
        monitoringService.tell(new MgcpConnectionAdded(connId.toString(), endpointId.getLocalEndpointName()), self());
        response.setSpecificEndpointIdentifier(endpointId);
        // Create a new secondary end point id if necessary.
        EndpointIdentifier secondaryEndpointId = crcx.getSecondEndpointIdentifier();
        if (secondaryEndpointId != null) {
            buffer = new StringBuilder();
            buffer.append(connectionIdPool.get());
            ConnectionIdentifier secondayConnId = new ConnectionIdentifier(buffer.toString());
            response.setSecondConnectionIdentifier(secondayConnId);
            endpointName = secondaryEndpointId.getLocalEndpointName();
            if (endpointName.endsWith("$")) {
                final String[] tokens = endpointName.split("/");
                final String type = tokens[1];
                buffer = new StringBuilder();
                buffer.append("mobicents/").append(type).append("/");
                buffer.append(endpointIdPool.get());
                secondaryEndpointId = new EndpointIdentifier(buffer.toString(), domain);
            }
            connEndpointMap.put(secondayConnId.toString(), secondaryEndpointId.getLocalEndpointName());
            if (logger.isInfoEnabled()) {
                String msg = String.format("About to add connId %s for secondary endpoint %s associated with endpont %s", secondayConnId.toString(), secondaryEndpointId.getLocalEndpointName(), endpointId.getLocalEndpointName());
                logger.info(msg);
            }
            monitoringService.tell(new MgcpEndpointAdded(connId.toString(), secondaryEndpointId.getLocalEndpointName()), self());
            monitoringService.tell(new MgcpConnectionAdded(secondayConnId.toString(), secondaryEndpointId.getLocalEndpointName()));
            response.setSecondEndpointIdentifier(secondaryEndpointId);
        }
        final ConnectionDescriptor descriptor = new ConnectionDescriptor(sdp);
        response.setLocalConnectionDescriptor(descriptor);
        final int transaction = crcx.getTransactionHandle();
        response.setTransactionHandle(transaction);
        System.out.println(response.toString());
        sender.tell(response, self);
    }

    private void modifyConnection (final Object message, final ActorRef sender) {
        final ActorRef self = self();
        final ModifyConnection mdcx = (ModifyConnection) message;
        System.out.println("MDCX: \n" + mdcx.toString());
        if (logger.isInfoEnabled()) {
            String msg = String.format("Got MDCX for endpoint %s connId %s, mdcx: \n%s", mdcx.getEndpointIdentifier().getLocalEndpointName(), mdcx.getConnectionIdentifier(), mdcx);
            logger.info(msg);
        }
        ReturnCode code;
        SessionDescription sessionDescription = null;
        boolean isNonValidSdp = false;
        if (mdcx.getRemoteConnectionDescriptor() != null) {
            try {
                sessionDescription = SdpFactory.getInstance().createSessionDescription(mdcx.getRemoteConnectionDescriptor().toString());
                isNonValidSdp = sessionDescription.getSessionName().getValue().contains("NonValidSDP");
            } catch (SdpParseException e) {
                logger.error("Error while trying to get SDP from MDCX");
            }
        }
        if (sessionDescription != null && isNonValidSdp) {
            code = ReturnCode.Protocol_Error;
            final ModifyConnectionResponse response = new ModifyConnectionResponse(self, code);
            final int transaction = mdcx.getTransactionHandle();
            response.setTransactionHandle(transaction);
            System.out.println(response.toString());
            sender.tell(response, self);
        } else {
            code = ReturnCode.Transaction_Executed_Normally;
            final ModifyConnectionResponse response = new ModifyConnectionResponse(self, code);
            final ConnectionDescriptor descriptor = new ConnectionDescriptor(sdp);
            response.setLocalConnectionDescriptor(descriptor);
            final int transaction = mdcx.getTransactionHandle();
            response.setTransactionHandle(transaction);
            System.out.println(response.toString());
            sender.tell(response, self);
        }
    }

    private void deleteConnection (final Object message, final ActorRef sender) {
        final ActorRef self = self();
        final DeleteConnection dlcx = (DeleteConnection) message;
        if (dlcx.getConnectionIdentifier() == null) {
            connEndpointMap.values().removeAll(Collections.singleton(dlcx.getEndpointIdentifier().getLocalEndpointName()));
            monitoringService.tell(new MgcpConnectionDeleted(null, dlcx.getEndpointIdentifier().getLocalEndpointName()), self());
            monitoringService.tell(new MgcpEndpointDeleted(dlcx.getEndpointIdentifier().getLocalEndpointName()), self());
            if (logger.isInfoEnabled()) {
                String msg = String.format("Endpoint deleted %s", dlcx.getEndpointIdentifier().getLocalEndpointName());
                logger.info(msg);
            }
        } else {
            connEndpointMap.remove(dlcx.getConnectionIdentifier().toString());
            monitoringService.tell(new MgcpConnectionDeleted(dlcx.getConnectionIdentifier().toString(), null), self());
            //If all connections have been removed for a given Endpoint, we can consider that the endpoint is stopped (this is the RMS behavior)
            //Here check if we have Endpoint ID on the map, and if not, tell MonitoringService that the endpoint stopped.
            if (!connEndpointMap.values().contains(dlcx.getEndpointIdentifier().getLocalEndpointName())) {
                monitoringService.tell(new MgcpEndpointDeleted(dlcx.getEndpointIdentifier().getLocalEndpointName()), self());
                if (logger.isInfoEnabled()) {
                    String msg = String.format("Endpoint deleted %s since because there are no more connections related to this endpoint", dlcx.getEndpointIdentifier().getLocalEndpointName());
                    logger.info(msg);
                }
            }
        }

        System.out.println(dlcx.toString());
        final ReturnCode code = ReturnCode.Transaction_Executed_Normally;
        final DeleteConnectionResponse response = new DeleteConnectionResponse(self, code);
        final int transaction = dlcx.getTransactionHandle();
        response.setTransactionHandle(transaction);
        System.out.println(response.toString());
        sender.tell(response, self);
    }

    protected void notificationResponse (final Object message, final ActorRef sender) {
        final ActorRef self = self();
        final NotificationRequest rqnt = (NotificationRequest) message;
        EventName[] events = rqnt.getSignalRequests();
        //Thread sleep for the maximum recording length to simulate recording from RMS side
        String filename = null;
        boolean failResponse = false;

        if (events != null && events.length > 0 && events[0].getEventIdentifier() != null) {
            if (events[0].getEventIdentifier().getName().equalsIgnoreCase("pr")) {
                recording = true;
                startTime = DateTime.now();
                //Check for the Recording Length Timer parameter if the RQNT is about PlayRecord request
                String[] paramsArray = ((EventName) events[0]).getEventIdentifier().getParms().split(" ");

                for (String param : paramsArray) {
                    if (param.startsWith("rlt")) {
                        recordingMaxLength = Integer.parseInt(param.replace("rlt=", ""));
                    } else if (param.startsWith("ri")) {
                        filename = param.replace("ri=", "");
                    }
                }
                //If maxLength is not set, rlt will be rlt=36000
                if (recordingMaxLength != 36000) {
                    getContext().setReceiveTimeout(Duration.create(recordingMaxLength*10, TimeUnit.MILLISECONDS));
                }
                if (filename != null) {
                    Path path = Paths.get(filename.replaceFirst("file://", ""));
                    recordingFile = new File(filename.replaceFirst("file://", ""));
                    recordingFile.getParentFile().mkdir();
                }
            } else if (events[0].getEventIdentifier().getName().equalsIgnoreCase("es")) {
                String signal = ((EventName) events[0]).getEventIdentifier().getParms().split("=")[1];

                if (signal.equalsIgnoreCase("pr") && recording) {
                    stopRecording();
                }
            } else if (events[0].getEventIdentifier().getName().equalsIgnoreCase("pa")) {
                //If this is a Play Audio request, check that the parameter string ends with WAV
                String[] paramsArray = ((EventName) events[0]).getEventIdentifier().getParms().split(" ");
                for (String param : paramsArray) {
                    if (param.startsWith("an")) {
                        String annoUrl = param.replace("an=", "");
                        if (!annoUrl.toLowerCase().endsWith("wav")) {
                            failResponse = true;
                        }
                    }
                }
            }
        }
        System.out.println(rqnt.toString());
        ReturnCode code = null;
        if (failResponse) {
            code = ReturnCode.Transient_Error;
        } else {
            code = ReturnCode.Transaction_Executed_Normally;
        }
        final JainMgcpResponseEvent response = new NotificationRequestResponse(self, code);
        final int transaction = rqnt.getTransactionHandle();
        response.setTransactionHandle(transaction);

        String msg = String.format("About to send MockMediaGateway response %s", response.toString());
        logger.info(msg);
        sender.tell(response, self);
    }

    private void stopRecording () {
        if (recordingFile != null) {
            recording = false;
            endTime = DateTime.now();
            long recordingDuration = (int) ((endTime.getMillis() - startTime.getMillis()) / 1000);
            try {
                int duration = (int) recordingDuration;
                if (duration > 0) {
                    String msg = String.format("Will write to recording file %s for duration of %d", recordingFile, duration);
                    logger.info(msg);
                    URI waveFileUri = ClassLoader.getSystemResource("FiveMinutes.wav").toURI();
                    File waveFile = new File(waveFileUri);
                    writeRecording(waveFile, recordingFile, duration);
                } else {
                    String msg = String.format("Will not write recording file %s because duration is %d", recordingFile, duration);
                    logger.info(msg);
                }
            } catch (Exception e) {
                String msg = String.format("Exception while trying to create Recording file %s, exception %s", recordingFile, e);
                logger.error(msg);
            }
        }
    }

    protected void notify (final Object message, final ActorRef sender) {
        final ActorRef self = self();
        final NotificationRequest request = (NotificationRequest) message;

        MgcpEvent event = null;
        // Remove case RQNT for stoping media instead of stop recording.
        if (!request.getSignalRequests()[0].getEventIdentifier().getParms().equalsIgnoreCase("sg=pa") &&
                (request.getSignalRequests()[0].getEventIdentifier().getName().equalsIgnoreCase("es") ||
                        request.getSignalRequests()[0].getEventIdentifier().getName().equalsIgnoreCase("pr"))) {
            //Looks like this is either an RQNT AU/ES or
            //recording max length reached and we got the original recording RQNT
            event = AUMgcpEvent.auoc.withParm("AU/pr ri=file://" + recordingFile.toPath() + " rc=100 dc=1");
        } else {
            event = AUMgcpEvent.auoc.withParm("rc=100 dc=1");
        }

        final EventName[] events = {new EventName(AUPackage.AU, event)};

        final Notify notify = new Notify(this, request.getEndpointIdentifier(), request.getRequestIdentifier(), events);
        notify.setTransactionHandle((int) transactionIdPool.get());
        System.out.println(notify.toString());
        sender.tell(notify, self);
    }

    private void respond (final Object message, final ActorRef sender) {
        final Class<?> klass = message.getClass();
        if (jain.protocol.ip.mgcp.message.CreateConnection.class.equals(klass)) {
            createConnection(message, sender);
        } else if (ModifyConnection.class.equals(klass)) {
            modifyConnection(message, sender);
        } else if (DeleteConnection.class.equals(klass)) {
            deleteConnection(message, sender);
        } else if (NotificationRequest.class.equals(klass)) {
            notificationResponse(message, sender);
        }
    }


    private void send (final Object message, final ActorRef sender) {
        final JainMgcpCommandEvent command = (JainMgcpCommandEvent) message;
        final int transactionId = (int) transactionIdPool.get();
        command.setTransactionHandle(transactionId);
        respond(message, sender);

        EventName[] events = null;
        if (message instanceof NotificationRequest) {
            final NotificationRequest rqnt = (NotificationRequest) message;
            events = rqnt.getSignalRequests();
            if (events != null && events[0].getEventIdentifier().getName().equalsIgnoreCase("pr")) {
                recordingRqnt = rqnt;
                recordingRqntSender = sender();
            }
        }

        if (events != null && !events[0].getEventIdentifier().getName().equalsIgnoreCase("pr") &&
                // In real scenario, there is no NTFY for finish ringing immediately.
                !events[0].getEventIdentifier().getParms().contains("ringing.wav")) {
            if (NotificationRequest.class.equals(command.getClass())) {
                final NotificationRequest request = (NotificationRequest) command;
                final String id = Long.toString(requestIdPool.get());
                request.getRequestIdentifier().setRequestIdentifier(id);
                notify(request, sender);
            }
        }
    }

    private void send (final Object message) {
        final JainMgcpResponseEvent response = (JainMgcpResponseEvent) message;
        System.out.println(response.toString());
    }
}
