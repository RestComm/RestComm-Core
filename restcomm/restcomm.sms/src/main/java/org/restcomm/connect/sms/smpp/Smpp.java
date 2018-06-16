package org.restcomm.connect.sms.smpp;

import org.apache.log4j.Logger;

import com.cloudhopper.commons.charset.Charset;
import com.cloudhopper.commons.charset.CharsetUtil;
import com.cloudhopper.smpp.SmppBindType;
import com.cloudhopper.smpp.impl.DefaultSmppSession;
import com.cloudhopper.smpp.type.Address;

/**
 *
 * @author amit bhayani
 *
 */
public class Smpp {

    private static final Logger logger = Logger.getLogger(Smpp.class);

    private static final String DEFAULT_SMPP_INBOUND_ENCODING = "MODIFIED-UTF8";
    private static final String DEFAULT_SMPP_OUTBOUND_ENCODING = "GSM";
    private String name;
    private String systemId;
    private String peerIp;
    private int peerPort;
    private SmppBindType smppBindType;
    private String password;
    private String systemType;
    private byte interfaceVersion;

    private Address address;

    private long connectTimeout;

    private int windowSize;

    private long windowWaitTimeout;
    // if > 0, then activated
    private long requestExpiryTimeout;

    private long windowMonitorInterval;
    private boolean countersEnabled;

    private boolean logBytes;

    private long enquireLinkDelay;

    private Charset inboundCharacterEncoding;
    private Charset outboundCharacterEncoding;
    private boolean messagePayloadFlag;
    private boolean autoDetectDcsFlag;

    // not used as of today, but later we can allow users to stop each SMPP
    private boolean started = true;

    private transient DefaultSmppSession defaultSmppSession = null;

    public Smpp(String name, String systemId, String peerIp, int peerPort, SmppBindType smppBindType, String password,
            String systemType, byte interfaceVersion, Address address, long connectTimeout, int windowSize,
            long windowWaitTimeout, long requestExpiryTimeout, long windowMonitorInterval, boolean countersEnabled,
            boolean logBytes, long enquireLinkDelay, String inboundCharacterEncoding, String outboundCharacterEncoding, boolean messagePayloadFlag, boolean autoDetectDcsFlag) {
        super();
        this.name = name;
        this.systemId = systemId;
        this.peerIp = peerIp;
        this.peerPort = peerPort;
        this.smppBindType = smppBindType;
        this.password = password;
        this.systemType = systemType;
        this.interfaceVersion = interfaceVersion;
        this.address = address;
        this.connectTimeout = connectTimeout;
        this.windowSize = windowSize;
        this.windowWaitTimeout = windowWaitTimeout;
        this.requestExpiryTimeout = requestExpiryTimeout;
        this.windowMonitorInterval = windowMonitorInterval;
        this.countersEnabled = countersEnabled;
        this.logBytes = logBytes;
        this.enquireLinkDelay = enquireLinkDelay;
        try {
            this.inboundCharacterEncoding = CharsetUtil.map(inboundCharacterEncoding);
        } catch (Exception e) {
            this.inboundCharacterEncoding = CharsetUtil.map(DEFAULT_SMPP_INBOUND_ENCODING);
            if(logger.isInfoEnabled()) {
                logger.info("Charset " + inboundCharacterEncoding + " does not exist. Inbound encoding is set to default " + DEFAULT_SMPP_INBOUND_ENCODING + "\n" + e.getMessage());
            }
        }
        try {
            this.outboundCharacterEncoding = CharsetUtil.map(outboundCharacterEncoding);
        } catch (Exception e) {
            this.outboundCharacterEncoding = CharsetUtil.map(DEFAULT_SMPP_OUTBOUND_ENCODING);
            if(logger.isInfoEnabled()) {
                logger.info("Charset " + outboundCharacterEncoding+ " does not exist. Outbound encoding is set to default " + DEFAULT_SMPP_OUTBOUND_ENCODING + "\n" + e.getMessage());
            }
        }
        this.messagePayloadFlag = messagePayloadFlag;
        this.autoDetectDcsFlag = autoDetectDcsFlag;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSystemId() {
        return systemId;
    }

    public void setSystemId(String systemId) {
        this.systemId = systemId;
    }

    public String getPeerIp() {
        return peerIp;
    }

    public void setPeerIp(String peerIp) {
        this.peerIp = peerIp;
    }

    public int getPeerPort() {
        return peerPort;
    }

    public void setPeerPort(int peerPort) {
        this.peerPort = peerPort;
    }

    public SmppBindType getSmppBindType() {
        return smppBindType;
    }

    public void setSmppBindType(SmppBindType smppBindType) {
        this.smppBindType = smppBindType;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getSystemType() {
        return systemType;
    }

    public void setSystemType(String systemType) {
        this.systemType = systemType;
    }

    public byte getInterfaceVersion() {
        return interfaceVersion;
    }

    public void setInterfaceVersion(byte interfaceVersion) {
        this.interfaceVersion = interfaceVersion;
    }

    public Address getAddress() {
        return address;
    }

    public void setAddress(Address address) {
        this.address = address;
    }

    public long getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(long connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public int getWindowSize() {
        return windowSize;
    }

    public void setWindowSize(int windowSize) {
        this.windowSize = windowSize;
    }

    public long getWindowWaitTimeout() {
        return windowWaitTimeout;
    }

    public void setWindowWaitTimeout(long windowWaitTimeout) {
        this.windowWaitTimeout = windowWaitTimeout;
    }

    public long getRequestExpiryTimeout() {
        return requestExpiryTimeout;
    }

    public void setRequestExpiryTimeout(long requestExpiryTimeout) {
        this.requestExpiryTimeout = requestExpiryTimeout;
    }

    public long getWindowMonitorInterval() {
        return windowMonitorInterval;
    }

    public void setWindowMonitorInterval(long windowMonitorInterval) {
        this.windowMonitorInterval = windowMonitorInterval;
    }

    public boolean isCountersEnabled() {
        return countersEnabled;
    }

    public void setCountersEnabled(boolean countersEnabled) {
        this.countersEnabled = countersEnabled;
    }

    public boolean isLogBytes() {
        return logBytes;
    }

    public void setLogBytes(boolean logBytes) {
        this.logBytes = logBytes;
    }

    public long getEnquireLinkDelay() {
        return enquireLinkDelay;
    }

    public void setEnquireLinkDelay(long enquireLinkDelay) {
        this.enquireLinkDelay = enquireLinkDelay;
    }

    public Charset getInboundDefaultEncoding() {
        return inboundCharacterEncoding;
    }
    public Charset getOutboundDefaultEncoding() {
        return outboundCharacterEncoding;
    }

    public boolean getMessagePayloadFlag() {
        return messagePayloadFlag;
    }

    public boolean getAutoDetectDcsFlag() {
        return autoDetectDcsFlag;
    }

    public boolean isStarted() {
        return started;
    }

    public void setStarted(boolean started) {
        this.started = started;
        if (this.started == false) {
            this.defaultSmppSession.close(5000);
        }
    }

    public DefaultSmppSession getSmppSession() {
        return defaultSmppSession;
    }

    public void setSmppSession(DefaultSmppSession smppSession) {
        this.defaultSmppSession = smppSession;
    }

    @Override
    public String toString() {
        return "Smpp [name=" + name + ", systemId=" + systemId + ", peerIp=" + peerIp + ", peerPort=" + peerPort
                + ", smppBindType=" + smppBindType + ", password=" + password + ", systemType=" + systemType
                + ", interfaceVersion=" + interfaceVersion + ", address=" + address + ", connectTimeout=" + connectTimeout
                + ", windowSize=" + windowSize + ", windowWaitTimeout=" + windowWaitTimeout + ", requestExpiryTimeout="
                + requestExpiryTimeout + ", windowMonitorInterval=" + windowMonitorInterval + ", countersEnabled="
                + countersEnabled + ", logBytes=" + logBytes + ", enquireLinkDelay=" + enquireLinkDelay
                + ", inboundCharacterEncoding=" + inboundCharacterEncoding.toString()
                + ", outboundCharacterEncoding=" + outboundCharacterEncoding.toString()
                + "]";
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((name == null) ? 0 : name.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        Smpp other = (Smpp) obj;
        if (name == null) {
            if (other.name != null)
                return false;
        } else if (!name.equals(other.name))
            return false;
        return true;
    }
}
