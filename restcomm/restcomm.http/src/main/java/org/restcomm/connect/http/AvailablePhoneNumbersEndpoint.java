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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.jersey.spi.resource.Singleton;
import com.thoughtworks.xstream.XStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import javax.annotation.PostConstruct;
import javax.servlet.ServletContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import static javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;
import static javax.ws.rs.core.Response.ok;
import static javax.ws.rs.core.Response.status;
import org.apache.commons.configuration.Configuration;
import org.restcomm.connect.commons.annotations.concurrency.ThreadSafe;
import org.restcomm.connect.commons.loader.ObjectInstantiationException;
import org.restcomm.connect.dao.entities.AvailablePhoneNumber;
import org.restcomm.connect.dao.entities.AvailablePhoneNumberList;
import org.restcomm.connect.dao.entities.RestCommResponse;
import org.restcomm.connect.http.converter.AvailablePhoneNumberConverter;
import org.restcomm.connect.http.converter.AvailablePhoneNumberListConverter;
import org.restcomm.connect.http.converter.RestCommResponseConverter;
import org.restcomm.connect.identity.UserIdentityContext;
import org.restcomm.connect.provisioning.number.api.PhoneNumber;
import org.restcomm.connect.provisioning.number.api.PhoneNumberProvisioningManager;
import org.restcomm.connect.provisioning.number.api.PhoneNumberProvisioningManagerProvider;
import org.restcomm.connect.provisioning.number.api.PhoneNumberSearchFilters;

/**
 * @author quintana.thomas@gmail.com (Thomas Quintana)
 * @author jean.deruelle@telestax.com
 */
@ThreadSafe
@Singleton
public class AvailablePhoneNumbersEndpoint extends AbstractEndpoint {
    @Context
    private ServletContext context;
    private PhoneNumberProvisioningManager phoneNumberProvisioningManager;
    private XStream xstream;
    private Gson gson;




    public AvailablePhoneNumbersEndpoint() {
        super();
    }

    @PostConstruct
    public void init() throws ObjectInstantiationException {
        configuration = (Configuration) context.getAttribute(Configuration.class.getName());
        super.init(configuration.subset("runtime-settings"));
        /*
        phoneNumberProvisioningManager = (PhoneNumberProvisioningManager) context.getAttribute("PhoneNumberProvisioningManager");
        if(phoneNumberProvisioningManager == null) {
            final String phoneNumberProvisioningManagerClass = configuration.getString("phone-number-provisioning[@class]");
            Configuration phoneNumberProvisioningConfiguration = configuration.subset("phone-number-provisioning");
            Configuration telestaxProxyConfiguration = configuration.subset("runtime-settings").subset("telestax-proxy");

            phoneNumberProvisioningManager = (PhoneNumberProvisioningManager) new ObjectFactory(getClass().getClassLoader())
                    .getObjectInstance(phoneNumberProvisioningManagerClass);
            ContainerConfiguration containerConfiguration = new ContainerConfiguration(getOutboundInterfaces());
            phoneNumberProvisioningManager.init(phoneNumberProvisioningConfiguration, telestaxProxyConfiguration, containerConfiguration);
            context.setAttribute("phoneNumberProvisioningManager", phoneNumberProvisioningManager);
        }
        */
        // get manager from context or create it if it does not exist
        phoneNumberProvisioningManager = new PhoneNumberProvisioningManagerProvider(configuration, context).get();


        xstream = new XStream();
        xstream.alias("RestcommResponse", RestCommResponse.class);
        xstream.registerConverter(new AvailablePhoneNumberConverter(configuration));
        xstream.registerConverter(new AvailablePhoneNumberListConverter(configuration));
        xstream.registerConverter(new RestCommResponseConverter(configuration));
        final GsonBuilder builder = new GsonBuilder();
        builder.setPrettyPrinting();
//        builder.serializeNulls();
        gson = builder.create();
    }

    protected Response getAvailablePhoneNumbers(final String accountSid,
            final String isoCountryCode,
            PhoneNumberSearchFilters listFilters,
            String filterPattern,
            final MediaType responseType,
            UserIdentityContext userIdentityContext) {
        permissionEvaluator.secure(accountsDao.getAccount(accountSid),
                "RestComm:Read:AvailablePhoneNumbers", userIdentityContext);
        String searchPattern = "";
        if (filterPattern != null && !filterPattern.isEmpty()) {
            for(int i = 0; i < filterPattern.length(); i ++) {
                char c = filterPattern.charAt(i);
                boolean isDigit = (c >= '0' && c <= '9');
                boolean isStar = c == '*';
                if(!isDigit && !isStar) {
                    searchPattern = searchPattern.concat(getNumber(c));
                } else if (isStar) {
                    searchPattern = searchPattern.concat("\\d");
                } else {
                    searchPattern = searchPattern.concat(Character.toString(c));
                }
            }
            // completing the pattern to match any substring of the number
            searchPattern = "((" + searchPattern + ")+).*";
            Pattern pattern = Pattern.compile(searchPattern);
            listFilters.setFilterPattern(pattern);
        }

        final List<PhoneNumber> phoneNumbers = phoneNumberProvisioningManager.searchForNumbers(isoCountryCode, listFilters);
        List<AvailablePhoneNumber> availablePhoneNumbers = toAvailablePhoneNumbers(phoneNumbers);
        if (MediaType.APPLICATION_XML_TYPE.equals(responseType)) {
            return ok(xstream.toXML(new RestCommResponse(new AvailablePhoneNumberList(availablePhoneNumbers))),
                    MediaType.APPLICATION_XML).build();
        } else if (MediaType.APPLICATION_JSON_TYPE.equals(responseType)) {
            return ok(gson.toJson(phoneNumbers), MediaType.APPLICATION_JSON).build();
        }
        return status(INTERNAL_SERVER_ERROR).build();
        // } else {
        // return status(BAD_REQUEST).build();
        // }
    }

    // TODO refactor this to see if we can have a single object instead of copying things
    private List<AvailablePhoneNumber> toAvailablePhoneNumbers(List<PhoneNumber> phoneNumbers) {
        final List<AvailablePhoneNumber> availablePhoneNumbers = new ArrayList<AvailablePhoneNumber>();
        for (PhoneNumber phoneNumber : phoneNumbers) {
            final AvailablePhoneNumber number = new AvailablePhoneNumber(phoneNumber.getFriendlyName(),
                    phoneNumber.getPhoneNumber(), phoneNumber.getLata(), phoneNumber.getRateCenter(),
                    phoneNumber.getLatitude(), phoneNumber.getLongitude(), phoneNumber.getRegion(),
                    phoneNumber.getPostalCode(), phoneNumber.getIsoCountry(), phoneNumber.getCost(), phoneNumber.isVoiceCapable(),
                    phoneNumber.isSmsCapable(), phoneNumber.isMmsCapable(), phoneNumber.isFaxCapable());
            availablePhoneNumbers.add(number);
        }
        return availablePhoneNumbers;
    }

    public static String getNumber(char letter) {
        if (letter == 'A' || letter == 'B' || letter == 'C' || letter == 'a' || letter == 'b' || letter == 'c') {
            return "1";
        } else if (letter == 'D' || letter == 'E' || letter == 'F' || letter == 'd' || letter == 'e' || letter == 'f') {
            return "2";
        } else if (letter == 'G' || letter == 'H' || letter == 'I' || letter == 'g' || letter == 'h' || letter == 'i') {
            return "3";
        } else if (letter == 'J' || letter == 'K' || letter == 'L' || letter == 'j' || letter == 'k' || letter == 'l') {
            return "4";
        } else if (letter == 'M' || letter == 'N' || letter == 'O' || letter == 'm' || letter == 'n' || letter == 'o') {
            return "5";
        } else if (letter == 'P' || letter == 'Q' || letter == 'R' || letter == 'S' || letter == 'p' || letter == 'q' || letter == 'r' || letter == 's') {
            return "6";
        } else if (letter == 'T' || letter == 'U' || letter == 'V' || letter == 't' || letter == 'u' || letter == 'v') {
            return "7";
        } else if (letter == 'W' || letter == 'X' || letter == 'Y' || letter == 'Z' || letter == 'w' || letter == 'x' || letter == 'y' || letter == 'z') {
            return "9";
        }
        return "0";
    }

    /*
    @SuppressWarnings("unchecked")
    private List<SipURI> getOutboundInterfaces() {
        final List<SipURI> uris = (List<SipURI>) context.getAttribute(SipServlet.OUTBOUND_INTERFACES);
        return uris;
    }
    */
}
