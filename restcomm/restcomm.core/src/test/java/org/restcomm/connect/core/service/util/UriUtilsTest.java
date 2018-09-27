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

package org.restcomm.connect.core.service.util;

import org.apache.commons.configuration.XMLConfiguration;
import org.junit.Assert;
import org.junit.Test;
import org.restcomm.connect.commons.HttpConnector;
import org.restcomm.connect.commons.HttpConnectorList;
import org.restcomm.connect.commons.configuration.RestcommConfiguration;
import org.restcomm.connect.commons.dao.Sid;
import org.restcomm.connect.commons.util.JBossConnectorDiscover;
import org.restcomm.connect.dao.AccountsDao;
import org.restcomm.connect.dao.DaoManager;
import org.restcomm.connect.dao.OrganizationsDao;
import org.restcomm.connect.dao.entities.Account;
import org.restcomm.connect.dao.entities.Organization;

import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.MBeanException;
import javax.management.MalformedObjectNameException;
import javax.management.ReflectionException;
import java.net.URI;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Arrays;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class UriUtilsTest {

    DaoManager daoManager = mock(DaoManager.class);
    AccountsDao accountsDao = mock(AccountsDao.class);
    OrganizationsDao organizationsDao = mock(OrganizationsDao.class);

    Account account = mock(Account.class);
    Organization organization = mock(Organization.class);

    JBossConnectorDiscover jBossConnectorDiscover = mock(JBossConnectorDiscover.class);
    UriUtils uriUtils = new UriUtils(daoManager, jBossConnectorDiscover, false);

    Sid orgSid = new Sid("ORafbe225ad37541eba518a74248f0ac4c");

    @Test
    public void testResolveWithBase() {
        URI relativeUri = URI.create("/restcomm/recording.wav");
        URI baseUri = URI.create("http://127.0.0.1:8080");

        Assert.assertEquals("http://127.0.0.1:8080/restcomm/recording.wav",
                uriUtils.resolveWithBase(baseUri, relativeUri).toString());
    }

    @Test
    public void testResolveRelativeUrlWithAccount() throws MalformedObjectNameException, UnknownHostException, InstanceNotFoundException, AttributeNotFoundException, MBeanException, ReflectionException {

        String domainName = "amazing.restcomm.com";

        HttpConnector httpConnector = new HttpConnector("http","127.0.0.1", 8080, false);

        HttpConnectorList httpConnectorList = new HttpConnectorList(Arrays.asList(new HttpConnector[]{httpConnector}));

        when(jBossConnectorDiscover.findConnectors()).thenReturn(httpConnectorList);
        when(daoManager.getAccountsDao()).thenReturn(accountsDao);
        when(daoManager.getOrganizationsDao()).thenReturn(organizationsDao);
        when(accountsDao.getAccount(any(Sid.class))).thenReturn(account);
        when(account.getOrganizationSid()).thenReturn(orgSid);
        when(organizationsDao.getOrganization(orgSid)).thenReturn(organization);
        when(organization.getDomainName()).thenReturn(domainName);

        URI relativeUri = URI.create("/restcomm/recording.wav");

        Assert.assertEquals("http://amazing.restcomm.com:8080/restcomm/recording.wav",
                uriUtils.resolve(relativeUri, Sid.generate(Sid.Type.ACCOUNT)).toString());
    }

    @Test
    public void testResolveRelativeUrl() throws Exception {

        URL url = this.getClass().getResource("/restcomm.xml");
        XMLConfiguration xml = new XMLConfiguration();
        xml.setDelimiterParsingDisabled(true);
        xml.setAttributeSplittingDisabled(true);
        xml.load(url);
        RestcommConfiguration conf = new RestcommConfiguration(xml);

        String domainName = "amazing.restcomm.com";

        HttpConnector httpConnector = new HttpConnector("http","127.0.0.1", 8080, false);

        HttpConnectorList httpConnectorList = new HttpConnectorList(Arrays.asList(new HttpConnector[]{httpConnector}));

        when(jBossConnectorDiscover.findConnectors()).thenReturn(httpConnectorList);

        URI relativeUri = URI.create("/restcomm/recording.wav");

        Assert.assertEquals("http://127.0.0.1:8080/restcomm/recording.wav",
                uriUtils.resolve(relativeUri).toString());

    }
}
