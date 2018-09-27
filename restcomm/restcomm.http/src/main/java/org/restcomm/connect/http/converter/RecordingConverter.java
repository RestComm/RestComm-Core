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
package org.restcomm.connect.http.converter;

import java.lang.reflect.Type;

import org.apache.commons.configuration.Configuration;
import org.restcomm.connect.commons.amazonS3.RecordingSecurityLevel;
import org.restcomm.connect.commons.annotations.concurrency.ThreadSafe;
import org.restcomm.connect.core.service.RestcommConnectServiceProvider;
import org.restcomm.connect.dao.entities.Recording;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;

/**
 * @author quintana.thomas@gmail.com (Thomas Quintana)
 */
@ThreadSafe
public final class RecordingConverter extends AbstractConverter implements JsonSerializer<Recording> {

    private RecordingSecurityLevel securityLevel = RecordingSecurityLevel.SECURE;

    public RecordingConverter(final Configuration configuration) {
        super(configuration);
    }

    public void setSecurityLevel(final RecordingSecurityLevel securityLevel) {
        this.securityLevel = securityLevel;
    }

    @SuppressWarnings("rawtypes")
    @Override
    public boolean canConvert(final Class klass) {
        return Recording.class.equals(klass);
    }

    @Override
    public void marshal(final Object object, final HierarchicalStreamWriter writer, final MarshallingContext context) {
        final Recording recording = (Recording) object;
        writer.startNode("Recording");
        writeSid(recording.getSid(), writer);
        writeDateCreated(recording.getDateCreated(), writer);
        writeDateUpdated(recording.getDateUpdated(), writer);
        writeAccountSid(recording.getAccountSid(), writer);
        writeCallSid(recording.getCallSid(), writer);
        writeDuration(recording.getDuration(), writer);
        writeApiVersion(recording.getApiVersion(), writer);
        writeUri(recording.getUri(), writer);
        writer.startNode("FileUri");
        //Previously MybatisRecordingsDao used to prepare absolute URL for fileUri. Now we do it in the converter
        String fileUri = RestcommConnectServiceProvider.getInstance().uriUtils().resolve(recording.getFileUri(), recording.getAccountSid()).toString();
        writer.setValue(fileUri);
        writer.endNode();
        if (securityLevel.equals(RecordingSecurityLevel.NONE) && recording.getS3Uri() != null) {
            writer.startNode("S3Uri");
            writer.setValue(recording.getS3Uri().toString());
            writer.endNode();
        }
        writer.endNode();
    }

    @Override
    public JsonElement serialize(final Recording recording, final Type type, final JsonSerializationContext context) {
        final JsonObject object = new JsonObject();
        writeSid(recording.getSid(), object);
        writeDateCreated(recording.getDateCreated(), object);
        writeDateUpdated(recording.getDateUpdated(), object);
        writeAccountSid(recording.getAccountSid(), object);
        writeCallSid(recording.getCallSid(), object);
        writeDuration(recording.getDuration(), object);
        writeApiVersion(recording.getApiVersion(), object);
        writeUri(recording.getUri(), object);
        if (recording.getFileUri() != null) {
            //Previously MybatisRecordingsDao used to prepare absolute URL for fileUri. Now we do it in the converter
            String fileUri = RestcommConnectServiceProvider.getInstance().uriUtils().resolve(recording.getFileUri(), recording.getAccountSid()).toString();
            object.addProperty("file_uri", fileUri);
        }
        if (securityLevel.equals(RecordingSecurityLevel.NONE) && recording.getS3Uri() != null) {
            object.addProperty("s3_uri", recording.getS3Uri().toString());
        }
        return object;
    }
}
