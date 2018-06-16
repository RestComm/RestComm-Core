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

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;

import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.joda.time.DateTime;
import org.junit.After;
import org.junit.Before;
import static org.junit.Assert.*;
import org.junit.Test;
import org.restcomm.connect.dao.AccountsDao;
import org.restcomm.connect.dao.ClientsDao;
import org.restcomm.connect.dao.entities.Account;
import org.restcomm.connect.dao.entities.Client;
import org.restcomm.connect.commons.dao.Sid;

/**
 * @author quintana.thomas@gmail.com (Thomas Quintana)
 * @author maria farooq
 */
public final class ClientsDaoTest extends DaoTest {
    private static MybatisDaoManager manager;
    private final String realm = "org1.restcomm.com";

    public ClientsDaoTest() {
        super();
    }

    @Before
    public void before() throws IOException {

        sandboxRoot = createTempDir("accountsTest");
        String mybatisFilesPath = getClass().getResource("/accountsDao").getFile();
        setupSandbox(mybatisFilesPath, sandboxRoot);
    	
        final InputStream data = getClass().getResourceAsStream("/mybatis.xml");
        final SqlSessionFactoryBuilder builder = new SqlSessionFactoryBuilder();
        final SqlSessionFactory factory = builder.build(data);
        manager = new MybatisDaoManager();
        manager.start(factory);
    }

    @After
    public void after() {
        manager.shutdown();
    }

    @Test
    public void createReadUpdateDelete() {
        final Sid account = Sid.generate(Sid.Type.ACCOUNT);
        final Sid sid = Sid.generate(Sid.Type.CLIENT);
        Sid application = Sid.generate(Sid.Type.APPLICATION);
        URI url = URI.create("http://127.0.0.1:8080/restcomm/demos/hello-world.xml");
        String method = "GET";
        final Client.Builder builder = Client.builder();
        builder.setSid(sid);
        builder.setAccountSid(account);
        builder.setApiVersion("2012-04-24");
        builder.setFriendlyName("Alice");
        builder.setLogin("alice");
        builder.setPassword("alice", "1234", realm, "");
        builder.setStatus(Client.ENABLED);
        builder.setVoiceUrl(url);
        builder.setVoiceMethod(method);
        builder.setVoiceFallbackUrl(url);
        builder.setVoiceFallbackMethod(method);
        builder.setVoiceApplicationSid(application);
        builder.setUri(url);
        builder.setPushClientIdentity(sid.toString());
        Client client = builder.build();
        final ClientsDao clients = manager.getClientsDao();
        // Create a new client in the data store.
        clients.addClient(client);
        // Read the client from the data store.
        Client result = clients.getClient(sid);
        // Validate the results.
        assertTrue(result.getSid().equals(client.getSid()));
        assertTrue(result.getAccountSid().equals(client.getAccountSid()));
        assertTrue(result.getApiVersion().equals(client.getApiVersion()));
        assertTrue(result.getFriendlyName().equals(client.getFriendlyName()));
        assertTrue(result.getLogin().equals(client.getLogin()));
        assertTrue(result.getPassword().equals(client.getPassword()));
        assertTrue(result.getStatus() == client.getStatus());
        assertTrue(result.getVoiceUrl().equals(client.getVoiceUrl()));
        assertTrue(result.getVoiceMethod().equals(client.getVoiceMethod()));
        assertTrue(result.getVoiceFallbackUrl().equals(client.getVoiceFallbackUrl()));
        assertTrue(result.getVoiceFallbackMethod().equals(client.getVoiceFallbackMethod()));
        assertTrue(result.getVoiceApplicationSid().equals(client.getVoiceApplicationSid()));
        assertTrue(result.getUri().equals(client.getUri()));
        assertTrue(result.getPushClientIdentity().equals(client.getPushClientIdentity()));
        // Update the client.
        application = Sid.generate(Sid.Type.APPLICATION);
        url = URI.create("http://127.0.0.1:8080/restcomm/demos/world-hello.xml");
        method = "POST";
        String newClientIdentity = "newClientIdentity";
        client = client.setFriendlyName("Bob");
        client = client.setPassword(client.getLogin(), "4321", realm);
        client = client.setStatus(Client.DISABLED);
        client = client.setVoiceApplicationSid(application);
        client = client.setVoiceUrl(url);
        client = client.setVoiceMethod(method);
        client = client.setVoiceFallbackUrl(url);
        client = client.setVoiceFallbackMethod(method);
        client = client.setPushClientIdentity(newClientIdentity);
        clients.updateClient(client);
        // Read the updated client from the data store.
        result = clients.getClient(sid);
        // Validate the results.
        assertTrue(result.getFriendlyName().equals(client.getFriendlyName()));
        assertTrue(result.getLogin().equals(client.getLogin()));
        assertTrue(result.getPassword().equals(client.getPassword()));
        assertTrue(result.getStatus() == client.getStatus());
        assertTrue(result.getVoiceUrl().equals(client.getVoiceUrl()));
        assertTrue(result.getVoiceMethod().equals(client.getVoiceMethod()));
        assertTrue(result.getVoiceFallbackUrl().equals(client.getVoiceFallbackUrl()));
        assertTrue(result.getVoiceFallbackMethod().equals(client.getVoiceFallbackMethod()));
        assertTrue(result.getVoiceApplicationSid().equals(client.getVoiceApplicationSid()));
        assertTrue(result.getPushClientIdentity().equals(newClientIdentity));
        // Delete the client.
        clients.removeClient(sid);
        // Validate that the client was removed.
        assertTrue(clients.getClient(sid) == null);
    }

    @Test
    public void readByUser() {
    	AccountsDao dao = manager.getAccountsDao();
        Sid accountSid = Sid.generate(Sid.Type.ACCOUNT);
        final Sid org = Sid.generate(Sid.Type.ORGANIZATION);
        try {
			dao.addAccount(new Account(accountSid, new DateTime(), new DateTime(), "test@telestax.com", "Top Level Account", new Sid("AC00000000000000000000000000000000"),Account.Type.FULL,Account.Status.ACTIVE,"77f8c12cc7b8f8423e5c38b035249166","Administrator",new URI("/2012-04-24/Accounts/AC00000000000000000000000000000000"), org));
		} catch (IllegalArgumentException e) {
			e.printStackTrace();
		} catch (URISyntaxException e) {
			e.printStackTrace();
		}

        final Sid sid = Sid.generate(Sid.Type.CLIENT);
        Sid application = Sid.generate(Sid.Type.APPLICATION);
        URI url = URI.create("hello-world.xml");
        String method = "GET";
        final Client.Builder builder = Client.builder();
        builder.setSid(sid);
        builder.setAccountSid(accountSid);
        builder.setApiVersion("2012-04-24");
        builder.setFriendlyName("Tom");
        builder.setLogin("tom");
        builder.setPassword("tom", "1234", realm, "");
        builder.setStatus(Client.ENABLED);
        builder.setVoiceUrl(url);
        builder.setVoiceMethod(method);
        builder.setVoiceFallbackUrl(url);
        builder.setVoiceFallbackMethod(method);
        builder.setVoiceApplicationSid(application);
        builder.setUri(url);
        final Client client = builder.build();
        final ClientsDao clients = manager.getClientsDao();
        // Create a new client in the data store.
        clients.addClient(client);
        // Read the client from the data store using the user name.
        final Client result = clients.getClient("tom", org);
        // Validate the result.
        assertTrue(result.getSid().equals(client.getSid()));
        assertTrue(result.getAccountSid().equals(client.getAccountSid()));
        assertTrue(result.getApiVersion().equals(client.getApiVersion()));
        assertTrue(result.getFriendlyName().equals(client.getFriendlyName()));
        assertTrue(result.getLogin().equals(client.getLogin()));
        assertTrue(result.getPassword().equals(client.getPassword()));
        assertTrue(result.getStatus() == client.getStatus());
        assertTrue(result.getVoiceUrl().equals(client.getVoiceUrl()));
        assertTrue(result.getVoiceMethod().equals(client.getVoiceMethod()));
        assertTrue(result.getVoiceFallbackUrl().equals(client.getVoiceFallbackUrl()));
        assertTrue(result.getVoiceFallbackMethod().equals(client.getVoiceFallbackMethod()));
        assertTrue(result.getVoiceApplicationSid().equals(client.getVoiceApplicationSid()));
        assertTrue(result.getUri().equals(client.getUri()));
        // Delete the client.
        clients.removeClient(sid);
        // Validate that the client was removed.
        assertTrue(clients.getClient(sid) == null);
    }

    @Test
    public void readDeleteByAccountSid() {
        final Sid account = Sid.generate(Sid.Type.ACCOUNT);
        final Sid sid = Sid.generate(Sid.Type.CLIENT);
        Sid application = Sid.generate(Sid.Type.APPLICATION);
        URI url = URI.create("hello-world.xml");
        String method = "GET";
        final Client.Builder builder = Client.builder();
        builder.setSid(sid);
        builder.setAccountSid(account);
        builder.setApiVersion("2012-04-24");
        builder.setFriendlyName("Tom");
        builder.setLogin("tom");
        builder.setPassword("tom", "1234", realm, "");
        builder.setStatus(Client.ENABLED);
        builder.setVoiceUrl(url);
        builder.setVoiceMethod(method);
        builder.setVoiceFallbackUrl(url);
        builder.setVoiceFallbackMethod(method);
        builder.setVoiceApplicationSid(application);
        builder.setUri(url);
        final Client client = builder.build();
        final ClientsDao clients = manager.getClientsDao();
        // Create a new client in the data store.
        clients.addClient(client);
        // Get all the clients for a specific account.
        assertTrue(clients.getClients(account).size() == 1);
        // Remove all the clients for a specific account.
        clients.removeClients(account);
        // Validate that the clients were removed.
        assertTrue(clients.getClients(account).size() == 0);
    }
}
