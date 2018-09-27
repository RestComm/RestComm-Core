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
package org.restcomm.connect.telephony.api;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.restcomm.connect.commons.dao.Sid;
import org.restcomm.connect.dao.entities.InstanceId;

/**
 * @author <a href="mailto:gvagenas@gmail.com">gvagenas</a>
 *
 */
public class MonitoringServiceResponse {
    private final InstanceId instanceId;
    private final List<CallInfo> callDetailsList;
    private final Map<String, Integer> countersMap;
    private final Map<String, Double> durationMap;
    private final boolean withCallDetailsList;
    private final URI callDetailsUrl;
    private final Sid accountSid;

    public MonitoringServiceResponse(final InstanceId instanceId, final List<CallInfo> callDetailsList,
                                     final Map<String, Integer> countersMap, final Map<String, Double> durationMap,
                                     final boolean withCallDetailsList, final URI callDetailsUrl, final Sid accountSid) {
        super();
        this.instanceId = instanceId;
        this.callDetailsList = callDetailsList;
        this.countersMap = countersMap;
        this.durationMap = durationMap;
        this.withCallDetailsList = withCallDetailsList;
        this.callDetailsUrl = callDetailsUrl;
        this.accountSid = accountSid;
    }

    public List<CallInfo> getCallDetailsList() {
        return callDetailsList;
    }

    public Map<String, Integer> getCountersMap() {
        return countersMap;
    }

    public InstanceId getInstanceId() {
        return instanceId;
    }

    public Map<String, Double> getDurationMap() {
        return durationMap;
    }

    public URI getCallDetailsUrl () {
        return callDetailsUrl;
    }

    public boolean isWithCallDetailsList () {
        return withCallDetailsList;
    }

    public Sid getAccountSid () { return accountSid; }
}
