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
package org.restcomm.connect.telephony.api;

import org.restcomm.connect.commons.annotations.concurrency.Immutable;
import org.restcomm.connect.commons.dao.Sid;
import org.restcomm.connect.commons.telephony.CreateCallType;
import org.restcomm.connect.dao.entities.MediaAttributes;
import org.restcomm.connect.extension.api.IExtensionCreateCallRequest;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author quintana.thomas@gmail.com (Thomas Quintana)
 * @author jean.deruelle@telestax.com
 * @author gvagenas@telestax.com
 */
@Immutable
public final class CreateCall implements IExtensionCreateCallRequest{

    private final String from;
    private final String to;
    private String username;
    private String password;
    private final boolean isFromApi;
    private final int timeout;
    private final CreateCallType callType;
    private final Sid accountId;
    private boolean createCDR = true;
    private final Sid parentCallSid;
    private final URI statusCallbackUrl;
    private final String statusCallbackMethod;
    private final List<String> statusCallbackEvent;
    private String outboundProxy;
    private Map<String,ArrayList<String>> outboundProxyHeaders;
    private boolean allowed = true;
    private final MediaAttributes mediaAttributes;
    private String customHeaders;

    //Used for IExtensionCreateCallRequest
    public CreateCall(final String from, final String to, final String username, final String password,
                      final boolean isFromApi, final int timeout, final CreateCallType type, final Sid accountId, final Sid parentCallSid,
                      final URI statusCallbackUrl, final String statusCallbackMethod, final List<String> statusCallbackEvent) {
        this(from, to, username, password, isFromApi, timeout, type, accountId, parentCallSid, statusCallbackUrl, statusCallbackMethod,
                statusCallbackEvent, "", null, new MediaAttributes(), null);
    }

    //Used to create CreateCall objects (CallsEndpoint, UssdPushEndpoint, VI)
    public CreateCall(final String from, final String to, final String username, final String password,
                      final boolean isFromApi, final int timeout, final CreateCallType type, final Sid accountId, final Sid parentCallSid,
                      final URI statusCallbackUrl, final String statusCallbackMethod, final List<String> statusCallbackEvent, final String customHeaders) {
        this(from, to, username, password, isFromApi, timeout, type, accountId, parentCallSid, statusCallbackUrl, statusCallbackMethod,
                statusCallbackEvent, "", null, new MediaAttributes(), customHeaders);
    }

    //Used to create CreateCall objects with MediaAttributes (VI)
    public CreateCall(final String from, final String to, final String username, final String password,
                      final boolean isFromApi, final int timeout, final CreateCallType type, final Sid accountId, final Sid parentCallSid,
                      final URI statusCallbackUrl, final String statusCallbackMethod, final List<String> statusCallbackEvent, final MediaAttributes mediaAttributes, final String customHeaders) {
        this(from, to, username, password, isFromApi, timeout, type, accountId, parentCallSid, statusCallbackUrl, statusCallbackMethod,
                statusCallbackEvent, "", null, mediaAttributes, customHeaders);
    }

    public CreateCall(final String from, final String to, final String username, final String password,
                      final boolean isFromApi, final int timeout, final CreateCallType type, final Sid accountId, final Sid parentCallSid,
                      final URI statusCallbackUrl, final String statusCallbackMethod, final List<String> statusCallbackEvent,
                      final String outboundProxy, final Map<String,ArrayList<String>> outboundProxyHeaders, final MediaAttributes mediaAttributes, final String customHeaders) {
        super();
        this.from = from;
        this.to = to;
        this.username = username;
        this.password = password;
        this.isFromApi = isFromApi;
        this.timeout = timeout;
        this.callType = type;
        this.accountId = accountId;
        this.parentCallSid = parentCallSid;
        this.statusCallbackUrl = statusCallbackUrl;
        this.statusCallbackMethod = statusCallbackMethod;
        this.statusCallbackEvent = statusCallbackEvent;
        this.outboundProxy = outboundProxy;
        this.outboundProxyHeaders = outboundProxyHeaders;
        this.mediaAttributes = mediaAttributes;
        this.customHeaders = customHeaders;
    }

    public String from() {
        return from;
    }

    public String to() {
        return to;
    }

    public int timeout() {
        return timeout;
    }

    public CreateCallType type() {
        return callType;
    }

    public Sid accountId() {
        return accountId;
    }

    public String username() {
        return username;
    }
    public String setUsername() {
        return username;
    }

    public String password() {
        return password;
    }

    public String setPassword() {
        return password;
    }

    public boolean isCreateCDR() {
        return createCDR;
    }

    public void setCreateCDR(boolean createCDR) {
        this.createCDR = createCDR;
    }

    public Sid parentCallSid() {
        return parentCallSid;
    }

    public URI statusCallback() { return statusCallbackUrl; }

    public String statusCallbackMethod() { return statusCallbackMethod; }

    public List<String> statusCallbackEvent() { return statusCallbackEvent; }

    /**
     * IExtensionCreateCallRequest
     * @return the outboundProxy
     */
    public String getOutboundProxy() {
        return outboundProxy;
    }

    /**
     * IExtensionCreateCallRequest
     * @param outboundProxy the outboundProxy to set
     */
    public void setOutboundProxy(String outboundProxy) {
        this.outboundProxy = outboundProxy;
    }

    /**
     * IExtensionCreateCallRequest
     * the outboundProxyUsername is a facade to the username field
     * @return the outboundProxyUsername
     */
    public String getOutboundProxyUsername() {
        return username;
    }

    /**
     * IExtensionCreateCallRequest
     * the outboundProxyUsername is a facade to the username field
     * @param outboundProxyUsername the outboundProxyUsername to set
     */
    public void setOutboundProxyUsername(String outboundProxyUsername) {
        this.username = outboundProxyUsername;
    }

    /**
     * IExtensionCreateCallRequest
     * the outboundProxyPassword is a facade to the password field
     * @return the outboundProxyPassword
     */
    public String getOutboundProxyPassword() {
        return password;
    }

    /**
     * IExtensionCreateCallRequest
     * the outboundProxyPassword is a facade to the password field
     * @param outboundProxyPassword the outboundProxyPassword to set
     */
    public void setOutboundProxyPassword(String outboundProxyPassword) {
        this.password = outboundProxyPassword;
    }

    /**
     * IExtensionCreateCallRequest
     * @return the outboundProxyHeaders
     */
    public Map<String,ArrayList<String>> getOutboundProxyHeaders() {
        return outboundProxyHeaders;
    }

    /**
     * IExtensionCreateCallRequest
     * @param outboundProxyHeaders the outboundProxyHeaders to set
     */
    public void setOutboundProxyHeaders(Map<String,ArrayList<String>> outboundProxyHeaders) {
        this.outboundProxyHeaders = outboundProxyHeaders;
    }

    /**
     * IExtensionCreateCallRequest
     * @return from address
     */
    public String getFrom() {
        return from;
    }

    /**
     * IExtensionCreateCallRequest
     * @return to address
     */
    public String getTo() {
        return to;
    }

    /**
     * IExtensionCreateCallRequest
     * @return accountId
     */
    public Sid getAccountId() {
        return accountId;
    }

    /**
     * IExtensionCreateCallRequest
     * @return boolean fromApi
     */
    @Override
    public boolean isFromApi() {
        return isFromApi;
    }

    /**
     * IExtensionCreateCallRequest
     * @return boolean is child call
     */
    @Override
    public boolean isParentCallSidExists() {
        return parentCallSid != null;
    }

    /**
     * IExtensionCreateCallRequest
     * @return the CreateCallType
     */
    @Override
    public CreateCallType getType() {
        return callType;
    }

    /**
     * IExtensionCreateCallRequest
     * @return the CreateCallType
     */
    @Override
    public String getRequestURI() {
        return this.to;
    }

    /**
     * IExtensionRequest
     * @return accountSid
     */
    @Override
    public String getAccountSid() {
        return this.accountId.toString();
    }

    /**
     * IExtensionRequest
     * @return is allowed
     */
    @Override
    public boolean isAllowed() {
        return this.allowed;
    }

    /**
     * IExtensionRequest
     * @param allowed
     */
    @Override
    public void setAllowed(boolean allowed) {
        this.allowed = allowed;
    }

    @Override
    public String toString() {
        return "From: "+from+", To: "+to+", Type: "+callType.name()+", AccountId: "+accountId+", isFromApi: "+isFromApi+", parentCallSidExists: "+isParentCallSidExists();
    }

    public MediaAttributes mediaAttributes() {
        return mediaAttributes;
    }

    public String getCustomHeaders () {
        return customHeaders;
    }
}
