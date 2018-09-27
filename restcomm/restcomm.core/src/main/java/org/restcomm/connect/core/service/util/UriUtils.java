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

import org.apache.log4j.Logger;
import org.restcomm.connect.commons.HttpConnector;
import org.restcomm.connect.commons.HttpConnectorList;
import org.restcomm.connect.commons.annotations.concurrency.ThreadSafe;
import org.restcomm.connect.commons.configuration.RestcommConfiguration;
import org.restcomm.connect.commons.dao.Sid;
import org.restcomm.connect.commons.util.DNSUtils;
import org.restcomm.connect.commons.util.JBossConnectorDiscover;
import org.restcomm.connect.commons.util.TomcatConnectorDiscover;
import org.restcomm.connect.dao.DaoManager;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Iterator;
import java.util.List;

/**
 * Utility class to manipulate URI.
 *
 * @author Henrique Rosa
 */
@ThreadSafe
public class UriUtils {

    private Logger logger = Logger.getLogger(UriUtils.class);
    private HttpConnector selectedConnector = null;
    private DaoManager daoManager;
    private JBossConnectorDiscover jBossConnectorDiscover;
    private boolean useHostnameToResolve;

    /**
     * Default constructor.
     */
    public UriUtils(final DaoManager daoManager) {
        this.daoManager = daoManager;
        jBossConnectorDiscover = new JBossConnectorDiscover();
        useHostnameToResolve = RestcommConfiguration.getInstance().getMain().isUseHostnameToResolveRelativeUrls();
    }

    public UriUtils(final DaoManager daoManager, final JBossConnectorDiscover jBossConnectorDiscover, boolean useHostnameToResolve) {
        this.daoManager = daoManager;
        this.jBossConnectorDiscover = jBossConnectorDiscover;
        this.useHostnameToResolve = useHostnameToResolve;
    }

    /**
     * Resolves a relative URI.
     *
     * @param base The base of the URI
     * @param uri The relative URI.
     * @return The absolute URI
     */
    public URI resolveWithBase (final URI base, final URI uri) {
        if (base.equals(uri)) {
            return uri;
        } else if (!uri.isAbsolute()) {
            return base.resolve(uri);
        } else {
            return uri;
        }
    }

    /**
     * Will query different JMX MBeans for a list of runtime connectors.
     *
     * JBoss discovery will be used first, then Tomcat.
     *
     *
     * @return the list of connectors found
     */
    private HttpConnectorList getHttpConnectors() throws Exception {
        logger.info("Searching HTTP connectors.");
        HttpConnectorList httpConnectorList = null;
        //find Jboss first as is typical setup
        httpConnectorList = jBossConnectorDiscover.findConnectors();
        if (httpConnectorList == null || httpConnectorList.getConnectors().isEmpty()) {
            //if not found try tomcat
            httpConnectorList = new TomcatConnectorDiscover().findConnectors();
        }
        return httpConnectorList;
    }

    /**
     *
     * Use to resolve a relative URI
     * using the instance hostname as base
     *
     * @param uri
     * @return
     */
    public URI resolve(final URI uri) {
        return resolve(uri, null);
    }

    /**
     * Use to resolve a relative URI
     * using the domain name of the Organization
     * that the provided AccountSid belongs
     *
     * @param uri The relative URI
     * @param accountSid The accountSid to get the Org domain
     * @return The absolute URI
     */
    public URI resolve(final URI uri, final Sid accountSid) {
        getHttpConnector();

        String restcommAddress = null;

        if (accountSid != null && daoManager != null) {
            Sid organizationSid = daoManager.getAccountsDao().getAccount(accountSid).getOrganizationSid();
            restcommAddress = daoManager.getOrganizationsDao().getOrganization(organizationSid).getDomainName();
        }

        if (restcommAddress == null || restcommAddress.isEmpty()) {
            if (useHostnameToResolve) {
                restcommAddress = RestcommConfiguration.getInstance().getMain().getHostname();
                if (restcommAddress == null || restcommAddress.isEmpty()) {
                    try {
                        InetAddress addr = DNSUtils.getByName(selectedConnector.getAddress());
                        restcommAddress = addr.getCanonicalHostName();
                    } catch (UnknownHostException e) {
                        logger.error("Unable to resolveWithBase: " + selectedConnector + " to hostname: " + e);
                        restcommAddress = selectedConnector.getAddress();
                    }
                }
            } else {
                restcommAddress = selectedConnector.getAddress();
            }
        }

        String base = selectedConnector.getScheme() + "://" + restcommAddress + ":" + selectedConnector.getPort();
        try {
            return resolveWithBase(new URI(base), uri);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Badly formed URI: " + base, e);
        }
    }

    /**
     * For historical reasons this method never returns null, and instead
     * throws a RuntimeException.
     *
     * TODO review all clients of this method to check for null instead of
     * throwing RuntimeException
     *
     * @return The selected connector with Secure preference.
     * @throws RuntimeException if no connector is found for some reason
     */
    public HttpConnector getHttpConnector() throws RuntimeException {
        if (selectedConnector == null) {
            try {

                HttpConnectorList httpConnectorList = getHttpConnectors();

                if (httpConnectorList != null && !httpConnectorList.getConnectors().isEmpty()) {
                    List<HttpConnector> connectors = httpConnectorList.getConnectors();
                    Iterator<HttpConnector> iterator = connectors.iterator();
                    while (iterator.hasNext()) {
                        HttpConnector connector = iterator.next();
                        //take secure conns with preference
                        if (connector.isSecure()) {
                            selectedConnector = connector;
                        }
                    }
                    if (selectedConnector == null) {
                        //if not secure,take the first one
                        selectedConnector = connectors.get(0);
                    }
                }

                if (selectedConnector == null) {
                    //pervent logic to go further
                    throw new RuntimeException("No HttpConnector found");
                }
            } catch (Exception e) {
                throw new RuntimeException("Exception during HTTP Connectors discovery: ", e);
            }
        }
        return selectedConnector;
    }
}
