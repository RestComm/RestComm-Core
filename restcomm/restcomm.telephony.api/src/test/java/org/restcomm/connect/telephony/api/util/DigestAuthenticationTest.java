/*
 *  TeleStax, Open Source Cloud Communications
 *  Copyright 2011-2018, Telestax Inc and individual contributors
 *  by the @authors tag.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  under the terms of the GNU Affero General Public License as
 *  published by the Free Software Foundation; either version 3 of
 *  the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>
 */

package org.restcomm.connect.telephony.api.util;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.log4j.Logger;
import org.junit.Test;
import org.restcomm.connect.commons.configuration.RestcommConfiguration;
import org.restcomm.connect.commons.dao.Sid;
import org.restcomm.connect.commons.util.DigestAuthentication;
import org.restcomm.connect.dao.AccountsDao;
import org.restcomm.connect.dao.ClientsDao;
import org.restcomm.connect.dao.DaoManager;
import org.restcomm.connect.dao.entities.Account;
import org.restcomm.connect.dao.entities.Client;

import java.net.URI;
import java.net.URL;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DigestAuthenticationTest {

    private static Logger logger = Logger.getLogger(DigestAuthenticationTest.class);

    private String client = "alice0000000";
    private String domain = "org0000000.restcomm.com";
    private String password = "1234";

    //INSERT INTO "restcomm_organizations" VALUES('ORc241349fa4c0442bb8464bce8bb203e3', 'org0000000.restcomm.com', '2017-04-19 00:00:00.000000000','2017-04-19 00:00:00.000000000', 'active');
    //INSERT INTO "restcomm_clients" VALUES('CL21d7a04619d84015ab1e1feceaa1893b','2017-04-19 00:00:00.000000000','2017-04-19 00:00:00.000000000','ACca5c323916cb43ec99d0c93eb0b824c4','2012-04-24','alice0000000','alice0000000','9b11a2924d0881aca84f9db97f834d99',1,NULL,'POST',NULL,'POST',NULL,'/2012-04-24/Accounts/ACca5c323916cb43ec99d0c93eb0b824c4/Clients/CL21d7a04619d84015ab1e1feceaa1893b',NULL);

    private DaoManager daoManager = mock(DaoManager.class);
    private ClientsDao clientsDao = mock(ClientsDao.class);
    private AccountsDao accountsDao = mock(AccountsDao.class);
    private Account account = mock(Account.class);
    private Sid orgSid = Sid.generate(Sid.Type.ORGANIZATION);

    private String proxyAuthHeader = "Digest username=\"alice0000000\",realm=\"org0000000.restcomm.com\",cnonce=\"6b8b4567\",nc=00000001,qop=auth,uri=\"sip:192.168.1.204:5080\",nonce=\"34316531323833622d313539352d343\",response=\"9b22e97f4715c937cadf167bb9b02cbf\",algorithm=MD5";

    @Test
    public void testAuth(){


        RestcommConfiguration.createOnce(prepareConfiguration());

        String hashedPass = DigestAuthentication.HA1(client, domain, password, "MD5");

        Sid clientSid = Sid.generate(Sid.Type.CLIENT);
        Sid accountSid = Sid.generate(Sid.Type.ACCOUNT);

        Client.Builder builder = Client.builder();
        builder.setSid(clientSid);
        builder.setAccountSid(accountSid);
        builder.setApiVersion("2012-04-24");
        builder.setFriendlyName(client);
        builder.setLogin(client);
        builder.setPassword(client, password, domain, "MD5");
        builder.setStatus(1);
        builder.setPasswordAlgorithm("MD5");
        builder.setUri(URI.create("/2012-04-24/Accounts/ACca5c323916cb43ec99d0c93eb0b824c4/Clients/CL21d7a04619d84015ab1e1feceaa1893b"));

        Client aliceClient = builder.build();

        when(daoManager.getClientsDao()).thenReturn(clientsDao);
        when(clientsDao.getClient(client, orgSid)).thenReturn(aliceClient);
        when(daoManager.getAccountsDao()).thenReturn(accountsDao);
        when(accountsDao.getAccount(accountSid)).thenReturn(account);
        when(account.getStatus()).thenReturn(Account.Status.ACTIVE);

        assertEquals("9b11a2924d0881aca84f9db97f834d99", hashedPass);

        assertTrue(CallControlHelper.permitted(proxyAuthHeader, "INVITE", daoManager, orgSid));
    }

    private Configuration prepareConfiguration() {
        Configuration xml = null;
        try {
            URL restcommXml = this.getClass().getClassLoader().getResource("restcomm.xml");
            XMLConfiguration xmlConfiguration = new XMLConfiguration();
            xmlConfiguration.setDelimiterParsingDisabled(true);
            xmlConfiguration.setAttributeSplittingDisabled(true);
            xmlConfiguration.load(restcommXml);
            xml = xmlConfiguration;
        } catch (Exception e) {
            logger.error("Exception: "+e);
        }
        return xml;
    }

}
