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
package org.restcomm.connect.http.converter;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import org.apache.commons.configuration.Configuration;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.restcomm.connect.commons.Version;
import org.restcomm.connect.core.service.RestcommConnectServiceProvider;
import org.restcomm.connect.telephony.api.CallInfo;
import org.restcomm.connect.telephony.api.MonitoringServiceResponse;

import java.lang.reflect.Type;
import java.util.Iterator;
import java.util.Map;

/**
 * @author <a href="mailto:gvagenas@gmail.com">gvagenas</a>
 *
 */
public class MonitoringServiceConverter extends AbstractConverter implements JsonSerializer<MonitoringServiceResponse>{

    private String dateTimeNow;

    public MonitoringServiceConverter(Configuration configuration) {
        super(configuration);
        DateTime now = DateTime.now();
        DateTimeFormatter fmt = DateTimeFormat.forPattern("EEE, dd MMM yyyy kk:mm:ss");
        dateTimeNow = fmt.print(now);

    }

    @Override
    public boolean canConvert(final Class klass) {
        return MonitoringServiceResponse.class.equals(klass);
    }

    @Override
    public JsonElement serialize(MonitoringServiceResponse monitoringServiceResponse, Type typeOfSrc, JsonSerializationContext context) {
        final Map<String, Integer> countersMap = monitoringServiceResponse.getCountersMap();
        final Map<String, Double> durationMap = monitoringServiceResponse.getDurationMap();
        JsonObject result = new JsonObject();
        JsonObject metrics = new JsonObject();
        JsonArray callsArray = new JsonArray();

        //First add InstanceId and Version details
        result.addProperty("DateTime", dateTimeNow);
        result.addProperty("InstanceId", monitoringServiceResponse.getInstanceId().getId().toString());
        result.addProperty("Version", Version.getVersion());
        result.addProperty("Revision", Version.getRevision());

        Iterator<String> counterIterator = countersMap.keySet().iterator();
        while (counterIterator.hasNext()) {
            String counter = counterIterator.next();
            metrics.addProperty(counter, countersMap.get(counter));
        }
        Iterator<String> durationIterator = durationMap.keySet().iterator();
        while (durationIterator.hasNext()) {
            String durationKey = durationIterator.next();
            metrics.addProperty(durationKey, durationMap.get(durationKey));
        }
        result.add("Metrics", metrics);

        if (monitoringServiceResponse.isWithCallDetailsList()) {
            if (monitoringServiceResponse.getCallDetailsList() != null && monitoringServiceResponse.getCallDetailsList().size() > 0) {
                for (CallInfo callInfo : monitoringServiceResponse.getCallDetailsList()) {
                    callsArray.add(context.serialize(callInfo));
                }
            }
            result.add("LiveCallDetails", callsArray);
        } else {
            result.addProperty("LiveCallDetails", RestcommConnectServiceProvider.getInstance().uriUtils().resolve(monitoringServiceResponse.getCallDetailsUrl(), monitoringServiceResponse.getAccountSid()).toString());
        }
        return result;
    }

    @Override
    public void marshal(Object object, HierarchicalStreamWriter writer, MarshallingContext context) {
        final MonitoringServiceResponse monitoringServiceResponse = (MonitoringServiceResponse) object;
        final Map<String, Double> durationMap = monitoringServiceResponse.getDurationMap();
        final Map<String, Integer> countersMap = monitoringServiceResponse.getCountersMap();
        Iterator<String> counterIterator = countersMap.keySet().iterator();
        Iterator<String> durationIterator = durationMap.keySet().iterator();

        writer.startNode("DateTime");
        writer.setValue(dateTimeNow);
        writer.endNode();

        writer.startNode("InstanceId");
        writer.setValue(monitoringServiceResponse.getInstanceId().getId().toString());
        writer.endNode();

        writer.startNode("Version");
        writer.setValue(Version.getVersion());
        writer.endNode();

        writer.startNode("Revision");
        writer.setValue(Version.getRevision());
        writer.endNode();

        writer.startNode("Metrics");
        while (counterIterator.hasNext()) {
            String counter = counterIterator.next();
            writer.startNode(counter);
            writer.setValue(String.valueOf(countersMap.get(counter)));
            writer.endNode();
        }
        while (durationIterator.hasNext()) {
            String durationKey = durationIterator.next();
            writer.startNode(durationKey);
            writer.setValue(String.valueOf(durationMap.get(durationKey)));
            writer.endNode();
        }
        writer.endNode();

        if (monitoringServiceResponse.isWithCallDetailsList()) {
            if (monitoringServiceResponse.getCallDetailsList() != null && monitoringServiceResponse.getCallDetailsList().size() > 0) {
                writer.startNode("LiveCallDetails");

                for (final CallInfo callInfo : monitoringServiceResponse.getCallDetailsList()) {
                    context.convertAnother(callInfo);
                }
                writer.endNode();
            } else {
                writer.startNode("LiveCallDetails");
                writer.setValue(RestcommConnectServiceProvider.getInstance().uriUtils().resolve(monitoringServiceResponse.getCallDetailsUrl(), monitoringServiceResponse.getAccountSid()).toString());
                writer.endNode();
            }
        }
    }
}
