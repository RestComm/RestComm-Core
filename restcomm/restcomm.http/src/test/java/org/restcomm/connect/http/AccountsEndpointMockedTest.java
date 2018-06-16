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

package org.restcomm.connect.http;

import com.sun.jersey.core.util.MultivaluedMapImpl;
import java.net.URI;
import java.net.URISyntaxException;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import org.apache.commons.configuration.ConfigurationException;
import org.joda.time.DateTime;
import static org.junit.Assert.assertEquals;
import org.junit.Test;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.*;
import org.restcomm.connect.commons.dao.Sid;
import org.restcomm.connect.dao.entities.Account;
import org.restcomm.connect.dao.entities.Organization;

/**
 * A sample unit test for accounts endpoint. It illustrates the use of the EndpointMockedTest.
 *
 * @author orestis.tsakiridis@telestax.com - Orestis Tsakiridis
 */
public class AccountsEndpointMockedTest extends EndpointMockedTest {
    @Test
    public void endpointInitializedAndBasicAuthorizationWork() throws ConfigurationException, URISyntaxException {
        init(); // setup default mocking values
        AccountsEndpoint endpoint = new AccountsEndpoint(servletContext);
        endpoint.init();
    }

    @Test
    public void statusUpdate() throws ConfigurationException, URISyntaxException {
        init(); // setup default mocking values
        Account account = new Account(new Sid("AC00000000000000000000000000000000"),new DateTime(),new DateTime(),"administrator@company.com","Administrator",new Sid("AC00000000000000000000000000000001"),Account.Type.TRIAL,Account.Status.ACTIVE,"77f8c12cc7b8f8423e5c38b035249166","Administrator",new URI("/uri"), Sid.generate(Sid.Type.ORGANIZATION));
        Organization organization = new Organization(account.getOrganizationSid(), "domainName", null, null, Organization.Status.ACTIVE);
        when(accountsDao.getAccount(any(Sid.class))).thenReturn(account);
        when(accountsDao.getAccount(any(String.class))).thenReturn(account);
        when(accountsDao.getAccountToAuthenticate(any(String.class))).thenReturn(account);
        when(orgDao.getOrganization(any(Sid.class))).thenReturn(organization);
        when (userIdentityContext.getEffectiveAccount()).thenReturn(account);

        AccountsEndpoint endpoint = new AccountsEndpoint(servletContext);
        endpoint.init();
        MultivaluedMapImpl multivaluedMapImpl = new MultivaluedMapImpl();
        multivaluedMapImpl.putSingle("Status", "active");
        Response updateAccount = endpoint.updateAccount(account.getSid().toString(),
                multivaluedMapImpl,  MediaType.APPLICATION_JSON_TYPE,
                userIdentityContext);
        assertEquals(200, updateAccount.getStatus());
    }

}
