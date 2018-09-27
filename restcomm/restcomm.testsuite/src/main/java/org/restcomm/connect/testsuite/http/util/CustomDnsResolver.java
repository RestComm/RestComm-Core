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

package org.restcomm.connect.testsuite.http.util;


import org.apache.log4j.Logger;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;

/**
 * @author gvagenas@gmail.com
 */

public class CustomDnsResolver implements sun.net.spi.nameservice.NameService {
    //Based on https://stackoverflow.com/a/43870031

    private static Logger logger = Logger.getLogger(CustomDnsResolver.class);
    private static final String LOCALHOST = "127.0.0.1";
    private static Map<String, String> domainIpMap;

    @Override
    public InetAddress[] lookupAllHostAddr (String host) throws UnknownHostException {
        if (domainIpMap != null) {
            if (domainIpMap.containsKey(host)) {
                final byte[] arrayOfByte = sun.net.util.IPAddressUtil.textToNumericFormatV4(domainIpMap.get(host));
                final InetAddress address = InetAddress.getByAddress(host, arrayOfByte);
                return new InetAddress[]{address};
            } else {
                throw new UnknownHostException();
            }
        } else {
            final byte[] arrayOfByte = sun.net.util.IPAddressUtil.textToNumericFormatV4(LOCALHOST);
            final InetAddress address = InetAddress.getByAddress(host, arrayOfByte);
            return new InetAddress[]{address};
        }
    }

    @Override
    public String getHostByAddr (byte[] bytes) throws UnknownHostException {
        throw new UnknownHostException();
    }


    public static void setNameService (Map<String, String> domainIpMapProvided) {
        try {
            List<sun.net.spi.nameservice.NameService> nameServices =
                    (List<sun.net.spi.nameservice.NameService>)
                            org.apache.commons.lang3.reflect.FieldUtils.readStaticField(InetAddress.class, "nameServices", true);
            if (domainIpMap != null) {
                domainIpMap = domainIpMapProvided;
            }
            nameServices.add(new CustomDnsResolver());
        } catch (IllegalAccessException e) {
            logger.error("Error during setNameService: ",e);
        }
    }

}
