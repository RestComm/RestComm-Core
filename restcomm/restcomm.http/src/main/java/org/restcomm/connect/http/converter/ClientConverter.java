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
import org.restcomm.connect.commons.annotations.concurrency.ThreadSafe;
import org.restcomm.connect.dao.entities.Client;

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
public class ClientConverter extends AbstractConverter implements JsonSerializer<Client> {
    public ClientConverter(final Configuration configuration) {
        super(configuration);
    }

    @SuppressWarnings("rawtypes")
    @Override
    public boolean canConvert(final Class klass) {
        return Client.class.equals(klass);
    }

    @Override
    public void marshal(final Object object, final HierarchicalStreamWriter writer, final MarshallingContext context) {
        final Client client = (Client) object;
        writer.startNode("Client");
        writeSid(client.getSid(), writer);
        writeDateCreated(client.getDateCreated(), writer);
        writeDateUpdated(client.getDateUpdated(), writer);
        writeAccountSid(client.getAccountSid(), writer);
        writeApiVersion(client.getApiVersion(), writer);
        writeFriendlyName(client.getFriendlyName(), writer);
        writeLogin(client.getLogin(), writer);
        writePassword(client.getPassword(), writer);
        writePasswordAlgorithm(client.getPasswordAlgorithm(), writer);
        writeStatus(client.getStatus().toString(), writer);
        writeVoiceUrl(client.getVoiceUrl(), writer);
        writeVoiceMethod(client.getVoiceMethod(), writer);
        writeVoiceFallbackUrl(client.getVoiceFallbackUrl(), writer);
        writeVoiceFallbackMethod(client.getVoiceFallbackMethod(), writer);
        writeVoiceApplicationSid(client.getVoiceApplicationSid(), writer);
        writeUri(client.getUri(), writer);
        writePushClientIdentity(client.getPushClientIdentity(), writer);
        writer.endNode();
    }

    @Override
    public JsonElement serialize(final Client client, final Type type, final JsonSerializationContext context) {
        final JsonObject object = new JsonObject();
        writeSid(client.getSid(), object);
        writeDateCreated(client.getDateCreated(), object);
        writeDateUpdated(client.getDateUpdated(), object);
        writeAccountSid(client.getAccountSid(), object);
        writeApiVersion(client.getApiVersion(), object);
        writeFriendlyName(client.getFriendlyName(), object);
        writeLogin(client.getLogin(), object);
        writePassword(client.getPassword(), object);
        writePasswordAlgorithm(client.getPasswordAlgorithm(), object);
        writeStatus(client.getStatus().toString(), object);
        writeVoiceUrl(client.getVoiceUrl(), object);
        writeVoiceMethod(client.getVoiceMethod(), object);
        writeVoiceFallbackUrl(client.getVoiceFallbackUrl(), object);
        writeVoiceFallbackMethod(client.getVoiceFallbackMethod(), object);
        writeVoiceApplicationSid(client.getVoiceApplicationSid(), object);
        writeUri(client.getUri(), object);
        writePushClientIdentity(client.getPushClientIdentity(), object);
        return object;
    }

    protected void writeLogin(final String login, final HierarchicalStreamWriter writer) {
        writer.startNode("Login");
        writer.setValue(login);
        writer.endNode();
    }

    protected void writeLogin(final String login, final JsonObject object) {
        object.addProperty("login", login);
    }

    protected void writePassword(final String password, final HierarchicalStreamWriter writer) {
        writer.startNode("Password");
        writer.setValue(password);
        writer.endNode();
    }

    protected void writePasswordAlgorithm(final String password_algorithm, final HierarchicalStreamWriter writer) {
        writer.startNode("Password_Algorithm");
        writer.setValue(password_algorithm);
        writer.endNode();
    }

    protected void writePassword(final String password, final JsonObject object) {
        object.addProperty("password", password);
    }

    protected void writePasswordAlgorithm(final String password_algorithm, final JsonObject object) {
        object.addProperty("password_algorithm", password_algorithm);
    }
}
