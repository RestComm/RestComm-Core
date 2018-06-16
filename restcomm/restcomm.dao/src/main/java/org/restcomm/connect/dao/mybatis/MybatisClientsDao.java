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
package org.restcomm.connect.dao.mybatis;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.joda.time.DateTime;
import org.restcomm.connect.commons.annotations.concurrency.NotThreadSafe;
import org.restcomm.connect.commons.configuration.RestcommConfiguration;
import org.restcomm.connect.commons.dao.Sid;
import org.restcomm.connect.dao.ClientsDao;
import org.restcomm.connect.dao.entities.Client;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.restcomm.connect.dao.DaoUtils.readDateTime;
import static org.restcomm.connect.dao.DaoUtils.readInteger;
import static org.restcomm.connect.dao.DaoUtils.readSid;
import static org.restcomm.connect.dao.DaoUtils.readString;
import static org.restcomm.connect.dao.DaoUtils.readUri;
import static org.restcomm.connect.dao.DaoUtils.writeDateTime;
import static org.restcomm.connect.dao.DaoUtils.writeSid;
import static org.restcomm.connect.dao.DaoUtils.writeUri;

/**
 * @author quintana.thomas@gmail.com (Thomas Quintana)
 * @author maria-farooq@live.com (Maria Farooq)
 */
@NotThreadSafe
public final class MybatisClientsDao implements ClientsDao {
    private static final String namespace = "org.mobicents.servlet.sip.restcomm.dao.ClientsDao.";
    private final SqlSessionFactory sessions;

    public MybatisClientsDao(final SqlSessionFactory sessions) {
        super();
        this.sessions = sessions;
    }

    @Override
    public void addClient(final Client client) {
        final SqlSession session = sessions.openSession();
        try {
            session.insert(namespace + "addClient", toMap(client));
            session.commit();
        } finally {
            session.close();
        }
    }

    @Override
    public Client getClient(final Sid sid) {
        return getClient(namespace + "getClient", sid.toString());
    }

    @Override
    public Client getClient(final String login, Sid organizationSid) {
        final Map<String, Object> map = new HashMap<String, Object>();
        map.put("login", login);
        map.put("organization_sid", writeSid(organizationSid));
        return getClient(namespace + "getClientByLogin", map);
    }

    private Client getClient(final String selector, final Object parameter) {
        final SqlSession session = sessions.openSession();
        try {
            final Map<String, Object> result = session.selectOne(selector, parameter);
            if (result != null) {
                return toClient(result);
            } else {
                return null;
            }
        } finally {
            session.close();
        }
    }

    @Override
    public List<Client> getClients(final Sid accountSid) {
        final SqlSession session = sessions.openSession();
        try {
            final List<Map<String, Object>> results = session.selectList(namespace + "getClients", accountSid.toString());
            final List<Client> clients = new ArrayList<Client>();
            if (results != null && !results.isEmpty()) {
                for (final Map<String, Object> result : results) {
                    clients.add(toClient(result));
                }
            }
            return clients;
        } finally {
            session.close();
        }
    }

    @Override
    public List<Client> getAllClients() {
        final SqlSession session = sessions.openSession();
        try {
            final List<Map<String, Object>> results = session.selectList(namespace + "getAllClients");
            final List<Client> clients = new ArrayList<Client>();
            if (results != null && !results.isEmpty()) {
                for (final Map<String, Object> result : results) {
                    clients.add(toClient(result));
                }
            }
            return clients;
        } finally {
            session.close();
        }
    }

    @Override
    public List<Client> getClientsByOrg(final Sid organizationSid) {
        final SqlSession session = sessions.openSession();
        try {
            final List<Map<String, Object>> results = session.selectList(namespace + "getAllClientsByOrg", organizationSid.toString());
            final List<Client> clients = new ArrayList<Client>();
            if (results != null && !results.isEmpty()) {
                for (final Map<String, Object> result : results) {
                    clients.add(toClient(result));
                }
            }
            return clients;
        } finally {
            session.close();
        }
    }

    @Override
    public void removeClient(final Sid sid) {
        removeClients(namespace + "removeClient", sid);
    }

    @Override
    public void removeClients(final Sid accountSid) {
        removeClients(namespace + "removeClients", accountSid);
    }

    private void removeClients(final String selector, final Sid sid) {
        final SqlSession session = sessions.openSession();
        try {
            session.delete(selector, sid.toString());
            session.commit();
        } finally {
            session.close();
        }
    }

    @Override
    public void updateClient(final Client client) {
        final SqlSession session = sessions.openSession();
        try {
            session.update(namespace + "updateClient", toMap(client));
            session.commit();
        } finally {
            session.close();
        }
    }

    private Client toClient(final Map<String, Object> map) {
        final Sid sid = readSid(map.get("sid"));
        final DateTime dateCreated = readDateTime(map.get("date_created"));
        final DateTime dateUpdated = readDateTime(map.get("date_updated"));
        final Sid accountSid = readSid(map.get("account_sid"));
        final String apiVersion = readString(map.get("api_version"));
        final String friendlyName = readString(map.get("friendly_name"));
        final String login = readString(map.get("login"));
        final String password = readString(map.get("password"));
        final int status = readInteger(map.get("status"));
        final URI voiceUrl = readUri(map.get("voice_url"));
        final String voiceMethod = readString(map.get("voice_method"));
        final URI voiceFallbackUrl = readUri(map.get("voice_fallback_url"));
        final String voiceFallbackMethod = readString(map.get("voice_fallback_method"));
        final Sid voiceApplicationSid = readSid(map.get("voice_application_sid"));
        final URI uri = readUri(map.get("uri"));
        final String pushClientIdentity = readString(map.get("push_client_identity"));
        String passwordAlgorithm = RestcommConfiguration.getInstance().getMain().getClearTextPasswordAlgorithm();
        if (map.containsKey("password_algorithm")) {
            passwordAlgorithm = readString(map.get("password_algorithm"));
        }
        return new Client(sid, dateCreated, dateUpdated, accountSid, apiVersion, friendlyName, login, password, status,
                voiceUrl, voiceMethod, voiceFallbackUrl, voiceFallbackMethod, voiceApplicationSid, uri, pushClientIdentity, passwordAlgorithm);
    }



    private Map<String, Object> toMap(final Client client) {
        final Map<String, Object> map = new HashMap<String, Object>();
        map.put("sid", writeSid(client.getSid()));
        map.put("date_created", writeDateTime(client.getDateCreated()));
        map.put("date_updated", writeDateTime(client.getDateUpdated()));
        map.put("account_sid", writeSid(client.getAccountSid()));
        map.put("api_version", client.getApiVersion());
        map.put("friendly_name", client.getFriendlyName());
        map.put("login", client.getLogin());
        map.put("password", client.getPassword());
        map.put("status", client.getStatus());
        map.put("voice_url", writeUri(client.getVoiceUrl()));
        map.put("voice_method", client.getVoiceMethod());
        map.put("voice_fallback_url", writeUri(client.getVoiceFallbackUrl()));
        map.put("voice_fallback_method", client.getVoiceFallbackMethod());
        map.put("voice_application_sid", writeSid(client.getVoiceApplicationSid()));
        map.put("uri", writeUri(client.getUri()));
        map.put("push_client_identity", client.getPushClientIdentity());
        map.put("password_algorithm", client.getPasswordAlgorithm());
        return map;
    }
}
