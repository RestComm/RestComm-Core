/*
 * TeleStax, Open Source Cloud Communications
 * Copyright 2011-2013, Telestax Inc and individual contributors
 * by the @authors tag.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.restcomm.connect.monitoringservice;

import akka.actor.ActorRef;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import org.restcomm.connect.commons.configuration.RestcommConfiguration;
import org.restcomm.connect.commons.dao.Sid;
import org.restcomm.connect.commons.faulttolerance.RestcommUntypedActor;
import org.restcomm.connect.commons.patterns.Observing;
import org.restcomm.connect.commons.patterns.StopObserving;
import org.restcomm.connect.dao.DaoManager;
import org.restcomm.connect.dao.entities.InstanceId;
import org.restcomm.connect.mgcp.stats.MgcpConnectionAdded;
import org.restcomm.connect.mgcp.stats.MgcpConnectionDeleted;
import org.restcomm.connect.mgcp.stats.MgcpEndpointAdded;
import org.restcomm.connect.mgcp.stats.MgcpEndpointDeleted;
import org.restcomm.connect.telephony.api.CallInfo;
import org.restcomm.connect.telephony.api.CallResponse;
import org.restcomm.connect.telephony.api.CallStateChanged;
import org.restcomm.connect.telephony.api.GetCall;
import org.restcomm.connect.telephony.api.GetCallInfo;
import org.restcomm.connect.telephony.api.GetLiveCalls;
import org.restcomm.connect.telephony.api.GetStatistics;
import org.restcomm.connect.telephony.api.MonitoringServiceResponse;
import org.restcomm.connect.telephony.api.TextMessage;
import org.restcomm.connect.telephony.api.UserRegistration;

import javax.servlet.sip.ServletParseException;
import javax.sip.header.ContactHeader;
import java.net.URI;
import java.net.URISyntaxException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author <a href="mailto:gvagenas@gmail.com">gvagenas</a>
 */
public class MonitoringService extends RestcommUntypedActor {

    private final LoggingAdapter logger = Logging.getLogger(getContext().system(), this);
    private DaoManager daoManager;

    private final Map<String, ActorRef> callMap;
    private final Map<String, ActorRef> callLocationMap;
    private final Map<String,CallInfo> callDetailsMap;
    private final Map<String,CallInfo> incomingCallDetailsMap;
    private final Map<String,CallInfo> outgoingCallDetailsMap;
    private final Map<String, CallStateChanged.State> callStateMap;
    private final Map<String, String> registeredUsers;
    private final AtomicInteger callsUpToNow;
    private final AtomicInteger incomingCallsUpToNow;
    private final AtomicInteger outgoingCallsUpToNow;
    private final AtomicInteger completedCalls;
    private final AtomicInteger failedCalls;
    private final AtomicInteger busyCalls;
    private final AtomicInteger canceledCalls;
    private final AtomicInteger noAnswerCalls;
    private final AtomicInteger notFoundCalls;
    private final AtomicInteger textInboundToApp;
    private final AtomicInteger textInboundToClient;
    private final AtomicInteger textInboundToProxyOut;
    private final AtomicInteger textOutbound;
    private final AtomicInteger textNotFound;
    private final AtomicInteger maxConcurrentCalls;
    private final AtomicInteger maxConcurrentIncomingCalls;
    private final AtomicInteger maxConcurrentOutgoingCalls;

    private final AtomicInteger mgcpEndpointsBridge;
    private final AtomicInteger mgcpEndpointsIvr;
    private final AtomicInteger mgcpEndpointsConference;
    private final AtomicInteger mgcpEndpointsPacketRelay;
    private final Map<String, String> mgcpEndpointMap;
    private final Map<String, String> mgcpConnectionMap;



    private InstanceId instanceId;


    public MonitoringService(final DaoManager daoManager) {
        this.daoManager = daoManager;
        callMap = new ConcurrentHashMap<String, ActorRef>();
        callLocationMap = new ConcurrentHashMap<String, ActorRef>();
        callDetailsMap = new ConcurrentHashMap<String, CallInfo>();
        incomingCallDetailsMap = new ConcurrentHashMap<String, CallInfo>();
        outgoingCallDetailsMap = new ConcurrentHashMap<String, CallInfo>();
        callStateMap = new ConcurrentHashMap<String, CallStateChanged.State>();
        registeredUsers = new ConcurrentHashMap<String, String>();
        callsUpToNow = new AtomicInteger();
        incomingCallsUpToNow = new AtomicInteger();
        outgoingCallsUpToNow = new AtomicInteger();
        completedCalls = new AtomicInteger();
        failedCalls = new AtomicInteger();
        busyCalls = new AtomicInteger();
        canceledCalls = new AtomicInteger();
        noAnswerCalls = new AtomicInteger();
        notFoundCalls = new AtomicInteger();
        textInboundToApp = new AtomicInteger();
        textInboundToClient = new AtomicInteger();
        textInboundToProxyOut = new AtomicInteger();
        textOutbound = new AtomicInteger();
        textNotFound = new AtomicInteger();
        maxConcurrentCalls = new AtomicInteger(0);
        maxConcurrentIncomingCalls = new AtomicInteger(0);
        maxConcurrentOutgoingCalls = new AtomicInteger(0);

        mgcpEndpointsBridge = new AtomicInteger(0);
        mgcpEndpointsIvr = new AtomicInteger(0);
        mgcpEndpointsConference = new AtomicInteger(0);
        mgcpEndpointsPacketRelay = new AtomicInteger(0);

        mgcpEndpointMap = new ConcurrentHashMap<String, String>();
        mgcpConnectionMap = new ConcurrentHashMap<String, String>();

        if(logger.isInfoEnabled()){
            logger.info("Monitoring Service started");
        }
    }

    @Override
    public void onReceive(Object message) throws Exception {
        final Class<?> klass = message.getClass();
        final ActorRef self = self();
        final ActorRef sender = sender();
        if(logger.isInfoEnabled()){
            logger.info("MonitoringService, path: \""+self.path()+"\" Processing Message: \"" + klass.getName() + "\" sender : "+ sender.getClass()+"\" self is terminated: "+self.isTerminated()+"\"");
        }

        if (InstanceId.class.equals(klass)) {
            onGotInstanceId((InstanceId) message, self, sender);
        } else if (Observing.class.equals(klass)) {
            onStartObserve((Observing) message, self, sender);
        } else if (StopObserving.class.equals(klass)) {
            if(logger.isInfoEnabled()){
                logger.info("Received stop observing");
            }
            onStopObserving((StopObserving) message, self, sender);
        } else if (CallResponse.class.equals(klass)) {
            onCallResponse((CallResponse<CallInfo>)message, self, sender);
        } else if (CallStateChanged.class.equals(klass)) {
            onCallStateChanged((CallStateChanged)message, self, sender);
        } else if (GetStatistics.class.equals(klass)) {
            onGetStatistics((GetStatistics) message, self, sender);
        } else if (GetLiveCalls.class.equals(klass)) {
            onGetLiveCalls((GetLiveCalls)message, self, sender);
        } else if (UserRegistration.class.equals(klass)) {
            onUserRegistration((UserRegistration)message, self, sender);
        } else if (TextMessage.class.equals(klass)) {
            onTextMessage((TextMessage) message, self, sender);
        } else if (GetCall.class.equals(klass)) {
            if (message != null) {
                onGetCall(message, self, sender);
            } else {
                if (logger.isDebugEnabled()) {
                    logger.debug("MonitoringService onGetCall, message is null, sender: "+sender.path());
                }
            }
        } else if (MgcpConnectionAdded.class.equals(klass)) {
            MgcpConnectionAdded mgcpConnectionAdded = (MgcpConnectionAdded)message;
            mgcpConnectionMap.put(mgcpConnectionAdded.getConnId(), mgcpConnectionAdded.getEndpointId());
        } else if (MgcpConnectionDeleted.class.equals(klass)) {
            MgcpConnectionDeleted mgcpConnectionDeleted = (MgcpConnectionDeleted)message;
            if (mgcpConnectionDeleted.getConnId() != null) {
                mgcpConnectionMap.remove(mgcpConnectionDeleted.getConnId());
            } else {
                mgcpConnectionMap.values().removeAll(Collections.singleton(mgcpConnectionDeleted.getEndpoint()));
            }
        } else if (MgcpEndpointAdded.class.equals(klass)) {
            MgcpEndpointAdded mgcpEndpointAdded = (MgcpEndpointAdded)message;
            mgcpEndpointMap.put(mgcpEndpointAdded.getConnId(), mgcpEndpointAdded.getEndpoint());
            logger.info("MonitoringService: Added endpoint: "+mgcpEndpointAdded.getEndpoint());
            String endpoint = mgcpEndpointAdded.getEndpoint();
            if (endpoint.contains("ivr")) {
                mgcpEndpointsIvr.incrementAndGet();
            } else if (endpoint.contains("conf")) {
                mgcpEndpointsConference.incrementAndGet();
            } else if (endpoint.contains("bridge")) {
                mgcpEndpointsBridge.incrementAndGet();
            } else if (endpoint.contains("relay")) {
                mgcpEndpointsPacketRelay.incrementAndGet();
            }
        } else if (MgcpEndpointDeleted.class.equals(klass)) {
            MgcpEndpointDeleted mgcpEndpointDeleted = (MgcpEndpointDeleted)message;
            mgcpEndpointMap.values().removeAll(Collections.singleton(mgcpEndpointDeleted.getEndpoint()));
            logger.info("MonitoringService: Deleted endpoint: "+mgcpEndpointDeleted.getEndpoint());
            String endpoint = mgcpEndpointDeleted.getEndpoint();
            if (endpoint.contains("ivr")) {
                mgcpEndpointsIvr.decrementAndGet();
            } else if (endpoint.contains("conf")) {
                mgcpEndpointsConference.decrementAndGet();
            } else if (endpoint.contains("bridge")) {
                mgcpEndpointsBridge.decrementAndGet();
            } else if (endpoint.contains("relay")) {
                mgcpEndpointsPacketRelay.decrementAndGet();
            }
        }
    }

    private void onGetCall(Object message, ActorRef self, ActorRef sender) throws ServletParseException {
        GetCall getCall = (GetCall)message;
        String location = getCall.getIdentifier();
        if (logger.isDebugEnabled()) {
            logger.debug("MonitoringService onGetCall, location: "+location);
        }
        if (location != null) {
            ActorRef call = callLocationMap.get(location);
            if(call == null && location.indexOf("@") != -1 && location.indexOf(":") != -1) {
                // required in case the Contact Header of the INVITE doesn't contain any user part
                // as it is the case for Restcomm SDKs
                if (logger.isDebugEnabled()) {
                    logger.debug("MonitoringService onGetCall Another try on removing the user part from " + location);
                }
                int indexOfAt = location.indexOf("@");
                int indexOfColumn = location.indexOf(":");
                String newLocation = location.substring(0, indexOfColumn+1).concat(location.substring(indexOfAt+1));
                call = callLocationMap.get(newLocation);
                if (logger.isDebugEnabled()) {
                    logger.debug("MonitoringService onGetCall call " + call + " found for new Location " + newLocation);
                }
            }
            if (call != null) {
                sender.tell(call, sender());
            } else {
                if (logger.isDebugEnabled()) {
                    logger.debug("MonitoringService onGetCall, Call is null");
                }
            }
        } else {
            if (logger.isDebugEnabled()) {
                logger.debug("MonitoringService onGetCall, GetCall identifier location is null");
            }
        }
    }

    /**
     * @param message
     * @param self
     * @param sender
     */
    private void onTextMessage(TextMessage message, ActorRef self, ActorRef sender) {
        TextMessage.SmsState state = message.getState();
        if (state.equals(TextMessage.SmsState.INBOUND_TO_APP)) {
            textInboundToApp.incrementAndGet();
        } else if (state.equals(TextMessage.SmsState.INBOUND_TO_CLIENT)) {
            textInboundToClient.incrementAndGet();
        } else if (state.equals(TextMessage.SmsState.INBOUND_TO_PROXY_OUT)) {
            textInboundToProxyOut.incrementAndGet();
        } else if (state.equals(TextMessage.SmsState.OUTBOUND)) {
            textOutbound.incrementAndGet();
        } else if (state.equals(TextMessage.SmsState.NOT_FOUND)) {
            textNotFound.incrementAndGet();
        }
    }

    private void onGotInstanceId(InstanceId instanceId, ActorRef self, ActorRef sender) {
        this.instanceId = instanceId;
    }

    /**
     * @param userRegistration
     * @param self
     * @param sender
     */
    private void onUserRegistration(UserRegistration userRegistration, ActorRef self, ActorRef sender) {
        if (userRegistration.getRegistered()) {
            try {
                registeredUsers.put(userRegistration.getUserPlusOrganizationsSid(), userRegistration.getAddress());
            } catch (Exception e) {
                if (logger.isDebugEnabled()) {
                    logger.debug("MonitoringService There was an issue during the process of UserRegistration message, "+e);
                }
            }
        } else {
            if (registeredUsers.containsKey(userRegistration.getUserPlusOrganizationsSid())) {
                registeredUsers.remove(userRegistration.getUserPlusOrganizationsSid());
                if (logger.isDebugEnabled()) {
                    String msg = String.format("MonitoringService User %s removed from registered users", userRegistration.getUserPlusOrganizationsSid());
                    logger.debug(msg);
                }
            } else {
                if (logger.isDebugEnabled()) {
                    String msg = String.format("MonitoringService User %s was not removed  because is not in the registered users", userRegistration.getUserPlusOrganizationsSid());
                    logger.debug(msg);
                }
            }
        }
    }

    /**
     * @param message
     * @param self
     * @param sender
     */
    private void onStartObserve(Observing message, ActorRef self, ActorRef sender) {
        String senderPath = sender.path().name();
        sender.tell(new GetCallInfo(), self);
        callMap.put(senderPath, sender);
        callsUpToNow.incrementAndGet();
    }

    /**
     * @param message
     * @param self
     * @param sender
     */
    private void onStopObserving(StopObserving message, ActorRef self, ActorRef sender) throws ServletParseException {
        String senderPath = sender.path().name();
        callMap.remove(senderPath);
        CallInfo callInfo = callDetailsMap.remove(senderPath);
        if (callInfo != null && callInfo.invite() != null) {
            callLocationMap.remove(callInfo.invite().getAddressHeader(ContactHeader.NAME).getURI().toString());
        }
        if (callInfo.direction().equalsIgnoreCase("inbound")) {
            if (logger.isDebugEnabled()) {
                String msg = String.format("MonitoringService Removed inbound call from: %s to: %s, currently liveCalls: %d", callInfo.from(), callInfo.to(),callDetailsMap.size());
                logger.debug(msg);
            }
            incomingCallDetailsMap.remove(senderPath);
        } else {
            if (logger.isDebugEnabled()) {
                String msg = String.format("MonitoringService Removed outbound call from: %s to: %s, currently liveCallS: %d ", callInfo.from(), callInfo.to(),callDetailsMap.size());
                logger.debug(msg);
            }
            outgoingCallDetailsMap.remove(senderPath);
        }
        callStateMap.remove(senderPath);
    }

    /**
     * @param message
     * @param self
     * @param sender
     */
    private void onCallResponse(CallResponse<CallInfo> message, ActorRef self, ActorRef sender) throws ServletParseException {
        String senderPath = sender.path().name();
        CallInfo callInfo = message.get();
        callDetailsMap.put(senderPath, callInfo);
        if (callInfo != null && callInfo.invite() != null) {
            callLocationMap.put(callInfo.invite().getAddressHeader(ContactHeader.NAME).getURI().toString(), sender);
        }
        if (callInfo.direction().equalsIgnoreCase("inbound")) {
            if (logger.isDebugEnabled()) {
                logger.debug("MonitoringService New inbound call from: "+callInfo.from()+"  to: "+callInfo.to());
            }
            incomingCallDetailsMap.put(senderPath, callInfo);
            incomingCallsUpToNow.incrementAndGet();
        } else {
            if (logger.isDebugEnabled()) {
                logger.debug("MonitoringService New outbound call from: "+callInfo.from()+"  to: "+callInfo.to());
            }
            outgoingCallDetailsMap.put(senderPath, callInfo);
            outgoingCallsUpToNow.incrementAndGet();
        }
        //Calculate Maximum concurrent calls
        if (maxConcurrentCalls.get() < callDetailsMap.size()) {
            maxConcurrentCalls.set(callDetailsMap.size());
        }
        if (maxConcurrentIncomingCalls.get() < incomingCallDetailsMap.size()) {
            maxConcurrentIncomingCalls.set(incomingCallDetailsMap.size());
        }
        if (maxConcurrentOutgoingCalls.get() < outgoingCallDetailsMap.size()) {
            maxConcurrentOutgoingCalls.set(outgoingCallDetailsMap.size());
        }
    }

    /**
     * @param message
     * @param self
     * @param sender
     */
    private void onCallStateChanged(CallStateChanged message, ActorRef self, ActorRef sender) {
        String senderPath = sender.path().name();
        if (senderPath != null && message != null && callStateMap != null && callDetailsMap != null) {
            CallStateChanged.State callState = message.state();
            callStateMap.put(senderPath, callState);
            CallInfo callInfo = callDetailsMap.get(senderPath);
            if (callInfo != null) {
                callInfo.setState(callState);
                if (callState.equals(CallStateChanged.State.FAILED)) {
                    failedCalls.incrementAndGet();
                } else if (callState.equals(CallStateChanged.State.COMPLETED)) {
                    completedCalls.incrementAndGet();
                } else if(callState.equals(CallStateChanged.State.BUSY)) {
                    busyCalls.incrementAndGet();
                } else if (callState.equals(CallStateChanged.State.CANCELED)) {
                    canceledCalls.incrementAndGet();
                } else if (callState.equals(CallStateChanged.State.NO_ANSWER)) {
                    noAnswerCalls.incrementAndGet();
                } else if (callState.equals(CallStateChanged.State.NOT_FOUND)) {
                    notFoundCalls.incrementAndGet();
                }
            } else if(logger.isInfoEnabled()){
                logger.info("MonitoringService CallInfo was not in the store for Call: "+senderPath);
            }
        } else {
            logger.error("MonitoringService, SenderPath or storage is null.");
        }
    }

    /**
     * @param message
     * @param self
     * @param sender
     */
    private void onGetStatistics (GetStatistics message, ActorRef self, ActorRef sender) throws ParseException {

        List<CallInfo> callDetailsList = new ArrayList<CallInfo>(callDetailsMap.values());
        Map<String, Integer> countersMap = new HashMap<String, Integer>();
        Map<String, Double> durationMap = new HashMap<String, Double>();

        final AtomicInteger liveIncomingCalls = new AtomicInteger();
        final AtomicInteger liveOutgoingCalls = new AtomicInteger();

        countersMap.put(MonitoringMetrics.COUNTERS_MAP_TOTAL_CALLS_SINCE_UPTIME,callsUpToNow.get());
        countersMap.put(MonitoringMetrics.COUNTERS_MAP_INCOMING_CALLS_SINCE_UPTIME, incomingCallsUpToNow.get());
        countersMap.put(MonitoringMetrics.COUNTERS_MAP_OUTGOING_CALL_SINCE_UPTIME, outgoingCallsUpToNow.get());
        countersMap.put(MonitoringMetrics.COUNTERS_MAP_REGISTERED_USERS, registeredUsers.size());
        countersMap.put(MonitoringMetrics.COUNTERS_MAP_LIVE_CALLS, callDetailsMap.size());
        countersMap.put(MonitoringMetrics.COUNTERS_MAP_MAXIMUM_CONCURRENT_CALLS, maxConcurrentCalls.get());
        countersMap.put(MonitoringMetrics.COUNTERS_MAP_MAXIMUM_CONCURRENT_INCOMING_CALLS, maxConcurrentIncomingCalls.get());
        countersMap.put(MonitoringMetrics.COUNTERS_MAP_MAXIMUM_CONCURRENT_OUTGOING_CALLS, maxConcurrentOutgoingCalls.get());

        Double averageCallDurationLast24Hours = null;
        Double averageCallDurationLastHour = null;

        try {
            averageCallDurationLast24Hours = daoManager.getCallDetailRecordsDao().getAverageCallDurationLast24Hours(instanceId.getId());
            averageCallDurationLastHour = daoManager.getCallDetailRecordsDao().getAverageCallDurationLastHour(instanceId.getId());
        } catch (Exception e) {
            if (logger.isDebugEnabled()) {
                logger.debug("MonitoringService Exception during the query for AVG Call Duration: "+e.getStackTrace());
            }
        }

        if (averageCallDurationLast24Hours == null) {
            averageCallDurationLast24Hours = 0.0;
        }

        if (averageCallDurationLastHour == null) {
            averageCallDurationLastHour = 0.0;
        }

        durationMap.put(MonitoringMetrics.DURATION_MAP_AVERAGE_CALL_DURATION_IN_SECONDS_LAST_24_HOURS, averageCallDurationLast24Hours);
        durationMap.put(MonitoringMetrics.DURATION_MAP_AVERAGE_CALL_DURATION_IN_SECONDS_LAST_HOUR, averageCallDurationLastHour);

        for (CallInfo callInfo : callDetailsList) {
            if (callInfo.direction().equalsIgnoreCase("inbound")) {
                liveIncomingCalls.incrementAndGet();
            } else if (callInfo.direction().contains("outbound")) {
                liveOutgoingCalls.incrementAndGet();
            }
        }
        countersMap.put(MonitoringMetrics.COUNTERS_MAP_LIVE_INCOMING_CALLS, liveIncomingCalls.get());
        countersMap.put(MonitoringMetrics.COUNTERS_MAP_LIVE_OUTGOING_CALLS, liveOutgoingCalls.get());

        countersMap.put(MonitoringMetrics.COUNTERS_MAP_COMPLETED_CALLS, completedCalls.get());
        countersMap.put(MonitoringMetrics.COUNTERS_MAP_NO_ANSWER_CALLS, noAnswerCalls.get());
        countersMap.put(MonitoringMetrics.COUNTERS_MAP_BUSY_CALLS, busyCalls.get());
        countersMap.put(MonitoringMetrics.COUNTERS_MAP_FAILED_CALLS, failedCalls.get());
        countersMap.put(MonitoringMetrics.COUNTERS_MAP_NOT_FOUND_CALLS, notFoundCalls.get());
        countersMap.put(MonitoringMetrics.COUNTERS_MAP_CANCELED_CALLS, canceledCalls.get());
        countersMap.put(MonitoringMetrics.COUNTERS_MAP_TEXT_MESSAGE_INBOUND_TO_APP, textInboundToApp.get());
        countersMap.put(MonitoringMetrics.COUNTERS_MAP_TEXT_MESSAGE_INBOUND_TO_CLIENT, textInboundToClient.get());
        countersMap.put(MonitoringMetrics.COUNTERS_MAP_TEXT_MESSAGE_INBOUND_TO_PROXY_OUT, textInboundToProxyOut.get());
        countersMap.put(MonitoringMetrics.COUNTERS_MAP_TEXT_MESSAGE_NOT_FOUND, textNotFound.get());
        countersMap.put(MonitoringMetrics.COUNTERS_MAP_TEXT_MESSAGE_OUTBOUND, textOutbound.get());

        if (message.isWithMgcpStats()) {
            countersMap.put(MonitoringMetrics.COUNTERS_MAP_MGCP_CONNECTIONS, mgcpConnectionMap.size());
            countersMap.put(MonitoringMetrics.COUNTERS_MAP_MGCP_ENDPOINTS, mgcpEndpointMap.size());
            if (mgcpEndpointMap.size()>0) {
                countersMap.put(MonitoringMetrics.COUNTERS_MAP_MGCP_ENDPOINTS_BRIDGE, mgcpEndpointsBridge.get());
                countersMap.put(MonitoringMetrics.COUNTERS_MAP_MGCP_ENDPOINTS_IVR, mgcpEndpointsIvr.get());
                countersMap.put(MonitoringMetrics.COUNTERS_MAP_MGCP_ENDPOINTS_CONFERENCE, mgcpEndpointsConference.get());
                countersMap.put(MonitoringMetrics.COUNTERS_MAP_MGCP_ENDPOINTS_PACKETRELAY, mgcpEndpointsPacketRelay.get());
            }
        }

        MonitoringServiceResponse callInfoList = null;
        if (message.isWithLiveCallDetails()) {
            callInfoList = new MonitoringServiceResponse(instanceId, callDetailsList, countersMap, durationMap, true, null, new Sid(message.getAccountSid()));
        } else {
            URI callDetailsUri = null;
            try {
                callDetailsUri = new URI(String.format("/restcomm/%s/Accounts/%s/Supervisor.json/livecalls", RestcommConfiguration.getInstance().getMain().getApiVersion(), message.getAccountSid()));
            } catch (URISyntaxException e) {
                logger.error("MonitoringService Problem while trying to create the LiveCalls detail URI");
            }
            callInfoList = new MonitoringServiceResponse(instanceId, null, countersMap, durationMap, false, callDetailsUri, new Sid(message.getAccountSid()));
        }
        sender.tell(callInfoList, self);
    }

    /**
     * @param message
     * @param self
     * @param sender
     */
    private void onGetLiveCalls (GetLiveCalls message, ActorRef self, ActorRef sender) throws ParseException {
        List<CallInfo> callDetailsList = new ArrayList<CallInfo>(callDetailsMap.values());
        sender.tell(new LiveCallsDetails(callDetailsList), self());
    }

    @Override
    public void postStop() {
        if(logger.isInfoEnabled()){
            logger.info("Monitoring Service at postStop()");
        }
        super.postStop();
    }
}
