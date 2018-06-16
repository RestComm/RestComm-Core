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
package org.restcomm.connect.interpreter;

import javax.servlet.sip.Address;
import javax.servlet.sip.ServletParseException;
import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipURI;
import org.apache.log4j.Logger;
import org.restcomm.connect.commons.dao.Sid;
import org.restcomm.connect.dao.entities.Organization;
import org.restcomm.connect.dao.OrganizationsDao;

public class SIPOrganizationUtil {

    private static Logger logger = Logger.getLogger(SIPOrganizationUtil.class);

    public static Sid getOrganizationSidBySipURIHost(OrganizationsDao orgDao, final SipURI sipURI) {
        if (logger.isDebugEnabled()) {
            logger.debug(String.format("getOrganizationSidBySipURIHost sipURI = %s", sipURI));
        }
        final String organizationDomainName = sipURI.getHost();
        Organization organization = orgDao.getOrganizationByDomainName(organizationDomainName);
        if (logger.isDebugEnabled()) {
            logger.debug(String.format("Org found = %s", organization));
        }
        return organization == null ? null : organization.getSid();
    }

    public static Sid searchOrganizationBySIPRequest(OrganizationsDao orgDao, SipServletRequest request) {
        //first try with requetURI
        Sid destinationOrganizationSid = getOrganizationSidBySipURIHost(orgDao,
                (SipURI) request.getRequestURI());
        if (destinationOrganizationSid == null) {
            // try to get destinationOrganizationSid from toUri
            destinationOrganizationSid = getOrganizationSidBySipURIHost(orgDao, (SipURI) request.getTo().getURI());
        }
        if (destinationOrganizationSid == null) {
            // try to get destinationOrganizationSid from Refer-To
            Address referAddress;
            try {
                referAddress = request.getAddressHeader("Refer-To");
                if(referAddress != null){
                    destinationOrganizationSid = getOrganizationSidBySipURIHost(orgDao, (SipURI) referAddress.getURI());
                }
            } catch (ServletParseException e) {
                logger.error(e);
            }
        }
        return destinationOrganizationSid;
    }
}
