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

import com.sun.jersey.spi.container.ResourceFilters;
import com.sun.jersey.spi.resource.Singleton;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import static javax.ws.rs.core.Response.*;
import static javax.ws.rs.core.Response.Status.*;
import javax.ws.rs.core.SecurityContext;
import org.restcomm.connect.commons.annotations.concurrency.ThreadSafe;
import org.restcomm.connect.http.filters.ExtensionFilter;
import org.restcomm.connect.http.security.ContextUtil;
import org.restcomm.connect.provisioning.number.api.PhoneNumberSearchFilters;
import org.restcomm.connect.provisioning.number.api.PhoneNumberType;

/**
 * @author <a href="mailto:gvagenas@gmail.com">gvagenas</a>
 * @author <a href="mailto:jean.deruelle@telestax.com">Jean Deruelle</a>
 */
@Path("/Accounts/{accountSid}/AvailablePhoneNumbers/{IsoCountryCode}/Local")
@ThreadSafe
@Singleton
public class AvailablePhoneNumbersXmlEndpoint extends AvailablePhoneNumbersEndpoint {
    public AvailablePhoneNumbersXmlEndpoint() {
        super();
    }

    @GET
    @ResourceFilters({ ExtensionFilter.class })
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public Response getAvailablePhoneNumber(@PathParam("accountSid") final String accountSid,
            @PathParam("IsoCountryCode") final String isoCountryCode, @QueryParam("AreaCode") String areaCode,
            @QueryParam("Contains") String filterPattern, @QueryParam("SmsEnabled") String smsEnabled,
            @QueryParam("MmsEnabled") String mmsEnabled, @QueryParam("VoiceEnabled") String voiceEnabled,
            @QueryParam("FaxEnabled") String faxEnabled, @QueryParam("UssdEnabled") String ussdEnabled,
            @QueryParam("NearNumber") String nearNumber,
            @QueryParam("NearLatLong") String nearLatLong, @QueryParam("Distance") String distance,
            @QueryParam("InPostalCode") String inPostalCode, @QueryParam("InRegion") String inRegion,
            @QueryParam("InRateCenter") String inRateCenter, @QueryParam("InLata") String inLata,
            @QueryParam("RangeSize") String rangeSize,
            @QueryParam("RangeIndex") String rangeIndex,
            @HeaderParam("Accept") String accept,
            @Context SecurityContext sec) {
        if (isoCountryCode != null && !isoCountryCode.isEmpty()) {
            int rangeSizeInt = -1;
            if (rangeSize != null && !rangeSize.isEmpty()) {
                rangeSizeInt = Integer.parseInt(rangeSize);
            }
            int rangeIndexInt = -1;
            if (rangeIndex != null && !rangeIndex.isEmpty()) {
                rangeIndexInt = Integer.parseInt(rangeIndex);
            }
            Boolean smsEnabledBool = null;
            if (smsEnabled != null && !smsEnabled.isEmpty()) {
                smsEnabledBool = Boolean.valueOf(smsEnabled);
            }
            Boolean mmsEnabledBool = null;
            if (mmsEnabled != null && !mmsEnabled.isEmpty()) {
                mmsEnabledBool = Boolean.valueOf(mmsEnabled);
            }
            Boolean voiceEnabledBool = null;
            if (voiceEnabled != null && !voiceEnabled.isEmpty()) {
                voiceEnabledBool = Boolean.valueOf(voiceEnabled);
            }
            Boolean faxEnabledBool = null;
            if (faxEnabled != null && !faxEnabled.isEmpty()) {
                faxEnabledBool = Boolean.valueOf(faxEnabled);
            }
            Boolean ussdEnabledBool = null;
            if (ussdEnabled != null && !ussdEnabled.isEmpty()) {
                ussdEnabledBool = Boolean.valueOf(ussdEnabled);
            }
            PhoneNumberType phoneNumberType = PhoneNumberType.Local;
            if(!isoCountryCode.equalsIgnoreCase("US")) {
                phoneNumberType = PhoneNumberType.Global;
            }
            PhoneNumberSearchFilters listFilters = new PhoneNumberSearchFilters(areaCode, null, smsEnabledBool,
                    mmsEnabledBool, voiceEnabledBool, faxEnabledBool, ussdEnabledBool, nearNumber, nearLatLong, distance, inPostalCode, inRegion,
                    inRateCenter, inLata, rangeSizeInt, rangeIndexInt, phoneNumberType);
            return getAvailablePhoneNumbers(accountSid, isoCountryCode,
                    listFilters, filterPattern, retrieveMediaType(accept),
                    ContextUtil.convert(sec));
        } else {
            return status(BAD_REQUEST).build();
        }
    }
}
