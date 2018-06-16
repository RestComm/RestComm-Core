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

import java.net.URISyntaxException;
import java.util.HashSet;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.XMLConfiguration;
import org.restcomm.connect.dao.AccountsDao;
import org.restcomm.connect.dao.DaoManager;
import org.restcomm.connect.dao.entities.Account;
import org.mockito.Mockito;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Set;
import static org.apache.shiro.web.filter.mgt.DefaultFilter.roles;

import static org.mockito.Mockito.when;
import org.restcomm.connect.dao.ClientsDao;
import org.restcomm.connect.dao.OrganizationsDao;
import static org.restcomm.connect.http.security.AccountPrincipal.ADMIN_ROLE;
import org.restcomm.connect.http.security.PermissionEvaluator;
import org.restcomm.connect.identity.IdentityContext;
import org.restcomm.connect.identity.UserIdentityContext;

/**
 * Base class for unit testing restcomm endpoints by mocking the following dependent components:
 *
 *  - Configuration
 *  - DaoManager and individual daos
 *  - ServletContext
 *  - HttpServletRequest
 *
 * Extend this class and further customize it to test other endpoints. See sample AccountsEndpointMockedTest
 * to get an idea how this works.
 *
 * @author orestis.tsakiridis@telestax.com - Orestis Tsakiridis
 */
public class EndpointMockedTest {

    Configuration conf;
    ServletContext servletContext;
    List<Account> accounts;
    AccountsDao accountsDao;
    DaoManager daoManager;
    HttpServletRequest request;
    OrganizationsDao orgDao;
    ClientsDao clientsDao;
    UserIdentityContext userIdentityContext;


    void init() throws URISyntaxException {
        String restcommXmlPath = AccountsEndpointMockedTest.class.getResource("/restcomm.xml").getFile();
        try {
            conf = getConfiguration(restcommXmlPath, "/restcomm", "http://localhost:8080");
        } catch (ConfigurationException e) {
            throw new RuntimeException();
        }
        // create ServletContext mock
        servletContext = Mockito.mock(ServletContext.class);
        daoManager = Mockito.mock(DaoManager.class);
        accountsDao = Mockito.mock(AccountsDao.class);
        orgDao= Mockito.mock(OrganizationsDao.class);
        clientsDao= Mockito.mock(ClientsDao.class);
        userIdentityContext = Mockito.mock(UserIdentityContext.class);

        Set<String> roles = new HashSet();
        roles.add(ADMIN_ROLE);
        when (userIdentityContext.getEffectiveAccountRoles()).thenReturn(roles);

        when(servletContext.getAttribute(Configuration.class.getName())).thenReturn(conf);
        when(daoManager.getAccountsDao()).thenReturn(accountsDao);
        when(daoManager.getOrganizationsDao()).thenReturn(orgDao);
        when(daoManager.getClientsDao()).thenReturn(clientsDao);
        when(servletContext.getAttribute(DaoManager.class.getName())).thenReturn(daoManager);
        when(servletContext.getAttribute(IdentityContext.class.getName())).thenReturn(new IdentityContext(conf));
        // createt request mock
        request = Mockito.mock(HttpServletRequest.class);
        when(request.getHeader("Authorization")).thenReturn("Basic YWRtaW5pc3RyYXRvckBjb21wYW55LmNvbTo3N2Y4YzEyY2M3YjhmODQyM2U1YzM4YjAzNTI0OTE2Ng==");
    }

    private Configuration getConfiguration(String path, String homeDirectory, String rootUri) throws ConfigurationException {
        Configuration xml = null;

        XMLConfiguration xmlConfiguration = new XMLConfiguration();
        xmlConfiguration.setDelimiterParsingDisabled(true);
        xmlConfiguration.setAttributeSplittingDisabled(true);
        xmlConfiguration.load(path);
        xml = xmlConfiguration;
        xml.setProperty("runtime-settings.home-directory", homeDirectory);
        xml.setProperty("runtime-settings.root-uri", rootUri);
        return xml;
    }

}
