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
package org.restcomm.connect.telephony.api.util;

import org.junit.Test;
import org.mockito.Mockito;
import org.restcomm.connect.commons.dao.Sid;
import org.restcomm.connect.dao.AccountsDao;
import org.restcomm.connect.dao.ClientsDao;
import org.restcomm.connect.dao.DaoManager;
import org.restcomm.connect.dao.entities.Account;
import org.restcomm.connect.dao.entities.Client;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

public class CallControlHelperTest {

    public CallControlHelperTest() {
    }

    @Test
    public void testPermitted() {
        String auth = "Digest username=\"alice\",realm=\"127.0.0.1\",nonce=\"64353235316666342d306434392d343\",uri=\"sip:127.0.0.1:5080\",response=\"31927ed6bc4b0c3796fd2240f8315fc7\",cnonce=\"416ba85980000000\",nc=00000001,qop=auth,algorithm=MD5";
        String method = "REGISTER";
        Sid organization = Sid.generate(Sid.Type.ORGANIZATION);
        Sid accountSid = Sid.generate(Sid.Type.ACCOUNT);
        Account.Builder builder = Account.builder();
        builder.setSid(accountSid);
        builder.setStatus(Account.Status.ACTIVE);
        Account account = builder.build();
        Client.Builder builder2 = Client.builder();
        builder2.setSid(Sid.generate(Sid.Type.CLIENT));
        builder2.setAccountSid(accountSid);
        //use no algorithm to skip conf mocking
        builder2.setPassword("alice", "2ea216cdda59f04ef9ba21fead1bca10", "127.0.0.1", "");
        builder2.setPasswordAlgorithm("MD5");
        builder2.setStatus(Client.ENABLED);
        Client client = builder2.build();

        DaoManager manager = Mockito.mock(DaoManager.class);
        ClientsDao clients = Mockito.mock(ClientsDao.class);
        AccountsDao accounts = Mockito.mock(AccountsDao.class);
        when(manager.getClientsDao()).thenReturn(clients);
        when(clients.getClient("alice", organization)).thenReturn(client);
        when(manager.getAccountsDao()).thenReturn(accounts);
        when(accounts.getAccount(accountSid)).thenReturn(account);

        boolean permitted = CallControlHelper.permitted(auth, method, manager, organization);
        assertTrue(permitted);
    }

    @Test
    public void testPermittedNoAlgorithm() {
        String auth = "Digest username=\"alice\",realm=\"127.0.0.1\",nonce=\"64353235316666342d306434392d343\",uri=\"sip:127.0.0.1:5080\",response=\"31927ed6bc4b0c3796fd2240f8315fc7\",cnonce=\"416ba85980000000\",nc=00000001,qop=auth";
        String method = "REGISTER";
        Sid organization = Sid.generate(Sid.Type.ORGANIZATION);
        Sid accountSid = Sid.generate(Sid.Type.ACCOUNT);
        Account.Builder builder = Account.builder();
        builder.setSid(accountSid);
        builder.setStatus(Account.Status.ACTIVE);
        Account account = builder.build();
        Client.Builder builder2 = Client.builder();
        builder2.setSid(Sid.generate(Sid.Type.CLIENT));
        builder2.setAccountSid(accountSid);
        //use no algorithm to skip conf mocking
        builder2.setPassword("alice", "2ea216cdda59f04ef9ba21fead1bca10", "127.0.0.1", "");
        builder2.setPasswordAlgorithm("MD5");
        builder2.setStatus(Client.ENABLED);
        Client client = builder2.build();

        DaoManager manager = Mockito.mock(DaoManager.class);
        ClientsDao clients = Mockito.mock(ClientsDao.class);
        AccountsDao accounts = Mockito.mock(AccountsDao.class);
        when(manager.getClientsDao()).thenReturn(clients);
        when(clients.getClient("alice", organization)).thenReturn(client);
        when(manager.getAccountsDao()).thenReturn(accounts);
        when(accounts.getAccount(accountSid)).thenReturn(account);

        boolean permitted = CallControlHelper.permitted(auth, method, manager, organization);
        assertTrue(permitted);
    }

}
