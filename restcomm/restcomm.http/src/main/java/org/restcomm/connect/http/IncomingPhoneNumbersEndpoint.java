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
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.NumberParseException.ErrorType;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;
import com.sun.jersey.spi.container.ResourceFilters;
import com.sun.jersey.spi.resource.Singleton;
import com.thoughtworks.xstream.XStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.PostConstruct;
import javax.annotation.security.RolesAllowed;
import javax.servlet.ServletContext;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON_TYPE;
import static javax.ws.rs.core.MediaType.APPLICATION_XML;
import static javax.ws.rs.core.MediaType.APPLICATION_XML_TYPE;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static javax.ws.rs.core.Response.Status.FORBIDDEN;
import static javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;
import static javax.ws.rs.core.Response.Status.NOT_FOUND;
import static javax.ws.rs.core.Response.Status.OK;
import static javax.ws.rs.core.Response.noContent;
import static javax.ws.rs.core.Response.ok;
import static javax.ws.rs.core.Response.status;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;
import org.apache.commons.configuration.Configuration;
import org.joda.time.DateTime;
import org.restcomm.connect.commons.annotations.concurrency.ThreadSafe;
import org.restcomm.connect.commons.dao.Sid;
import org.restcomm.connect.commons.loader.ObjectInstantiationException;
import org.restcomm.connect.dao.DaoManager;
import org.restcomm.connect.dao.IncomingPhoneNumbersDao;
import org.restcomm.connect.dao.OrganizationsDao;
import org.restcomm.connect.dao.entities.Account;
import org.restcomm.connect.dao.entities.IncomingPhoneNumber;
import org.restcomm.connect.dao.entities.IncomingPhoneNumberFilter;
import org.restcomm.connect.dao.entities.IncomingPhoneNumberList;
import org.restcomm.connect.dao.entities.Organization;
import org.restcomm.connect.dao.entities.RestCommResponse;
import org.restcomm.connect.dao.entities.SearchFilterMode;
import org.restcomm.connect.extension.api.ApiRequest;
import org.restcomm.connect.http.converter.AvailableCountriesConverter;
import org.restcomm.connect.http.converter.AvailableCountriesList;
import org.restcomm.connect.http.converter.IncomingPhoneNumberConverter;
import org.restcomm.connect.http.converter.IncomingPhoneNumberListConverter;
import org.restcomm.connect.http.converter.RestCommResponseConverter;
import org.restcomm.connect.http.filters.ExtensionFilter;
import static org.restcomm.connect.http.security.AccountPrincipal.SUPER_ADMIN_ROLE;
import org.restcomm.connect.http.security.ContextUtil;
import org.restcomm.connect.http.security.PermissionEvaluator.SecuredType;
import org.restcomm.connect.identity.UserIdentityContext;
import org.restcomm.connect.provisioning.number.api.PhoneNumberParameters;
import org.restcomm.connect.provisioning.number.api.PhoneNumberProvisioningManager;
import org.restcomm.connect.provisioning.number.api.PhoneNumberProvisioningManagerProvider;
import org.restcomm.connect.provisioning.number.api.PhoneNumberType;

/**
 * @author quintana.thomas@gmail.com (Thomas Quintana)
 * @author gvagenas@gmail.com
 * @author jean.deruelle@telestax.com
 * @author maria.farooq@telestax.com
 */
@Path("/Accounts/{accountSid}/IncomingPhoneNumbers")
@ThreadSafe
@Singleton
public class IncomingPhoneNumbersEndpoint extends AbstractEndpoint {
    @Context
    private ServletContext context;
    private PhoneNumberProvisioningManager phoneNumberProvisioningManager;
    private IncomingPhoneNumberListConverter listConverter;
    PhoneNumberParameters phoneNumberParameters;
    String callbackPort = "";
    private IncomingPhoneNumbersDao dao;
    private OrganizationsDao organizationsDao;
    private XStream xstream;
    private Gson gson;




    public IncomingPhoneNumbersEndpoint() {
        super();
    }

    @PostConstruct
    public void init() throws ObjectInstantiationException {
        final DaoManager storage = (DaoManager) context.getAttribute(DaoManager.class.getName());
        configuration = (Configuration) context.getAttribute(Configuration.class.getName());
        super.init(configuration.subset("runtime-settings"));
        dao = storage.getIncomingPhoneNumbersDao();
        accountsDao = storage.getAccountsDao();
        organizationsDao = storage.getOrganizationsDao();

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

        Configuration callbackUrlsConfiguration = configuration.subset("phone-number-provisioning").subset("callback-urls");
        String voiceUrl = callbackUrlsConfiguration.getString("voice[@url]");
        if(voiceUrl != null && !voiceUrl.trim().isEmpty()) {
            String[] voiceUrlArr = voiceUrl.split(":");
            if(voiceUrlArr != null && voiceUrlArr.length==2)
                callbackPort = voiceUrlArr[1];
        }
        phoneNumberParameters = new PhoneNumberParameters(
                voiceUrl,
                callbackUrlsConfiguration.getString("voice[@method]"), false,
                callbackUrlsConfiguration.getString("sms[@url]"),
                callbackUrlsConfiguration.getString("sms[@method]"),
                callbackUrlsConfiguration.getString("fax[@url]"),
                callbackUrlsConfiguration.getString("fax[@method]"),
                callbackUrlsConfiguration.getString("ussd[@url]"),
                callbackUrlsConfiguration.getString("ussd[@method]"));

        final IncomingPhoneNumberConverter converter = new IncomingPhoneNumberConverter(configuration);
        listConverter = new IncomingPhoneNumberListConverter(configuration);
        final GsonBuilder builder = new GsonBuilder();
        builder.serializeNulls();
        builder.registerTypeAdapter(IncomingPhoneNumber.class, converter);
        builder.registerTypeAdapter(IncomingPhoneNumberList.class, listConverter);
        builder.setPrettyPrinting();
        gson = builder.create();
        xstream = new XStream();
        xstream.alias("RestcommResponse", RestCommResponse.class);
        xstream.registerConverter(converter);
        xstream.registerConverter(listConverter);
        xstream.registerConverter(new AvailableCountriesConverter(configuration));
        xstream.registerConverter(new RestCommResponseConverter(configuration));

    }

    private IncomingPhoneNumber createFrom(final Sid accountSid, final MultivaluedMap<String, String> data, Sid organizationSid) {
        final IncomingPhoneNumber.Builder builder = IncomingPhoneNumber.builder();
        final Sid sid = Sid.generate(Sid.Type.PHONE_NUMBER);
        builder.setSid(sid);
        builder.setAccountSid(accountSid);
        String phoneNumber = data.getFirst("PhoneNumber");
        String cost = data.getFirst("Cost");
        builder.setPhoneNumber(phoneNumber);
        builder.setFriendlyName(getFriendlyName(phoneNumber, data));
        if (data.containsKey("VoiceCapable")) {
            builder.setVoiceCapable(Boolean.parseBoolean(data.getFirst("VoiceCapable")));
        }
        if (data.containsKey("SmsCapable")) {
            builder.setSmsCapable(Boolean.parseBoolean(data.getFirst("SmsCapable")));
        }
        if (data.containsKey("MmsCapable")) {
            builder.setMmsCapable(Boolean.parseBoolean(data.getFirst("MmsCapable")));
        }
        if (data.containsKey("FaxCapable")) {
            builder.setFaxCapable(Boolean.parseBoolean(data.getFirst("FaxCapable")));
        }
        if (data.containsKey("isSIP")) {
            builder.setPureSip(Boolean.parseBoolean(data.getFirst("isSIP")));
        } else {
            builder.setPureSip(false);
        }
        final String apiVersion = getApiVersion(data);
        builder.setApiVersion(apiVersion);
        builder.setVoiceUrl(getUrl("VoiceUrl", data));
        builder.setVoiceMethod(getMethod("VoiceMethod", data));
        builder.setVoiceFallbackUrl(getUrl("VoiceFallbackUrl", data));
        builder.setVoiceFallbackMethod(getMethod("VoiceFallbackMethod", data));
        builder.setStatusCallback(getUrl("StatusCallback", data));
        builder.setStatusCallbackMethod(getMethod("StatusCallbackMethod", data));
        builder.setHasVoiceCallerIdLookup(getHasVoiceCallerIdLookup(data));
        builder.setVoiceApplicationSid(getSid("VoiceApplicationSid", data));
        builder.setSmsUrl(getUrl("SmsUrl", data));
        builder.setSmsMethod(getMethod("SmsMethod", data));
        builder.setSmsFallbackUrl(getUrl("SmsFallbackUrl", data));
        builder.setSmsFallbackMethod(getMethod("SmsFallbackMethod", data));
        builder.setSmsApplicationSid(getSid("SmsApplicationSid", data));

        builder.setUssdUrl(getUrl("UssdUrl", data));
        builder.setUssdMethod(getMethod("UssdMethod", data));
        builder.setUssdFallbackUrl(getUrl("UssdFallbackUrl", data));
        builder.setUssdFallbackMethod(getMethod("UssdFallbackMethod",data));
        builder.setUssdApplicationSid(getSid("UssdApplicationSid",data));

        builder.setReferUrl(getUrl("ReferUrl", data));
        builder.setReferMethod(getMethod("ReferMethod", data));
        builder.setReferApplicationSid(getSid("ReferApplicationSid",data));
        builder.setOrganizationSid(organizationSid);

        final Configuration configuration = this.configuration.subset("runtime-settings");
        final StringBuilder buffer = new StringBuilder();
        buffer.append("/").append(apiVersion).append("/Accounts/").append(accountSid.toString())
        .append("/IncomingPhoneNumbers/").append(sid.toString());
        builder.setUri(URI.create(buffer.toString()));
        return builder.build();
    }

    private String e164(String number) throws NumberParseException {
        final PhoneNumberUtil numbersUtil = PhoneNumberUtil.getInstance();
        if(!number.startsWith("+")) {
            number = "+" + number;
        }
        final PhoneNumber result = numbersUtil.parse(number, "US");
        if (numbersUtil.isValidNumber(result)) {
            return numbersUtil.format(result, PhoneNumberFormat.E164);
        } else {
            throw new NumberParseException(ErrorType.NOT_A_NUMBER, "This is not a valid number");
        }
    }

    private String getFriendlyName(final String phoneNumber, final MultivaluedMap<String, String> data) {
        String friendlyName = phoneNumber;
        if (data.containsKey("FriendlyName")) {
            friendlyName = data.getFirst("FriendlyName");
        }
        return friendlyName;
    }

    protected Response getIncomingPhoneNumber(final String accountSid,
            final String sid,
            final MediaType responseType,
            UserIdentityContext userIdentityContext) {
        Account operatedAccount = accountsDao.getAccount(accountSid);
        permissionEvaluator.secure(operatedAccount,
                "RestComm:Read:IncomingPhoneNumbers",
                userIdentityContext);
        try{
            final IncomingPhoneNumber incomingPhoneNumber = dao.getIncomingPhoneNumber(new Sid(sid));
            if (incomingPhoneNumber == null) {
                return status(NOT_FOUND).build();
            } else {
                // if the account that the resource belongs to does not existb while the resource does, we're having BAD parameters
                if (operatedAccount == null) {
                    return status(BAD_REQUEST).build();
                }
                permissionEvaluator.secure(operatedAccount,
                        incomingPhoneNumber.getAccountSid(),
                        SecuredType.SECURED_STANDARD,
                        userIdentityContext);
                if (APPLICATION_JSON_TYPE.equals(responseType)) {
                    return ok(gson.toJson(incomingPhoneNumber), APPLICATION_JSON).build();
                } else if (APPLICATION_XML_TYPE.equals(responseType)) {
                    final RestCommResponse response = new RestCommResponse(incomingPhoneNumber);
                    return ok(xstream.toXML(response), APPLICATION_XML).build();
                } else {
                    return null;
                }
            }
        }catch(Exception e){
            logger.error("Exception while performing getIncomingPhoneNumber: ", e);
            return status(INTERNAL_SERVER_ERROR).build();
        }
    }

    protected Response getAvailableCountries(final String accountSid,
            final MediaType responseType,
            UserIdentityContext userIdentityContext) {
        permissionEvaluator.secure(accountsDao.getAccount(accountSid),
                "RestComm:Read:IncomingPhoneNumbers",
                userIdentityContext);
        List<String> countries = phoneNumberProvisioningManager.getAvailableCountries();
        if (countries == null) {
            countries = new ArrayList<String>();
            countries.add("US");
        }
        if (APPLICATION_JSON_TYPE.equals(responseType)) {
            return ok(gson.toJson(countries), APPLICATION_JSON).build();
        } else if (APPLICATION_XML_TYPE.equals(responseType)) {
            final RestCommResponse response = new RestCommResponse(new AvailableCountriesList(countries));
            return ok(xstream.toXML(response), APPLICATION_XML).build();
        } else {
            return null;
        }
    }

    protected Response getIncomingPhoneNumbers(final String accountSid,
            final PhoneNumberType phoneNumberType,
            UriInfo info,
        final MediaType responseType,
        UserIdentityContext userIdentityContext) {
        permissionEvaluator.secure(accountsDao.getAccount(accountSid),
                "RestComm:Read:IncomingPhoneNumbers",
                userIdentityContext);
        try{
            String phoneNumberFilter = info.getQueryParameters().getFirst("PhoneNumber");
            String friendlyNameFilter = info.getQueryParameters().getFirst("FriendlyName");
            String page = info.getQueryParameters().getFirst("Page");
            String reverse = info.getQueryParameters().getFirst("Reverse");
            String pageSize = info.getQueryParameters().getFirst("PageSize");
            String sortBy = info.getQueryParameters().getFirst("SortBy");

            pageSize = (pageSize == null) ? "50" : pageSize;
            page = (page == null) ? "0" : page;
            reverse = (reverse != null && "true".equalsIgnoreCase(reverse)) ? "DESC" : "ASC";
            sortBy = (sortBy != null) ? sortBy : "phone_number";

            int limit = Integer.parseInt(pageSize);
            int pageAsInt = Integer.parseInt(page);
            int offset = (page == "0") ? 0 : (((pageAsInt - 1) * limit) + limit);
            IncomingPhoneNumberFilter.Builder filterBuilder = IncomingPhoneNumberFilter.Builder.builder();
            filterBuilder.byAccountSid(accountSid);
            filterBuilder.byFriendlyName(friendlyNameFilter);
            filterBuilder.byPhoneNumber(phoneNumberFilter);
            filterBuilder.usingMode(SearchFilterMode.WILDCARD_MATCH);
            final int total = dao.getTotalIncomingPhoneNumbers(filterBuilder.build());

            if (pageAsInt > (total / limit)) {
                return status(javax.ws.rs.core.Response.Status.BAD_REQUEST).build();
            }

            filterBuilder.byAccountSid(accountSid);
            filterBuilder.byFriendlyName(friendlyNameFilter);
            filterBuilder.byPhoneNumber(phoneNumberFilter);
            filterBuilder.sortedBy(sortBy, reverse);
            filterBuilder.limited(limit, offset);
            filterBuilder.usingMode(SearchFilterMode.WILDCARD_MATCH);

            final List<IncomingPhoneNumber> incomingPhoneNumbers = dao.getIncomingPhoneNumbersByFilter(filterBuilder.build());

            listConverter.setCount(total);
            listConverter.setPage(pageAsInt);
            listConverter.setPageSize(limit);
            listConverter.setPathUri("/" + getApiVersion(null) + "/" + info.getPath());

            if (APPLICATION_JSON_TYPE.equals(responseType)) {
                return ok(gson.toJson(new IncomingPhoneNumberList(incomingPhoneNumbers)), APPLICATION_JSON).build();
            } else if (APPLICATION_XML_TYPE.equals(responseType)) {
                final RestCommResponse response = new RestCommResponse(new IncomingPhoneNumberList(incomingPhoneNumbers));
                return ok(xstream.toXML(response), APPLICATION_XML).build();
            } else {
                return null;
            }
        }catch(Exception e){
            logger.error("Exception while performing getIncomingPhoneNumbers: ", e);
            return status(INTERNAL_SERVER_ERROR).build();
        }
    }

    protected Response putIncomingPhoneNumber(final String accountSid,
            final MultivaluedMap<String, String> data,
        PhoneNumberType phoneNumberType,
        final MediaType responseType,
        UserIdentityContext userIdentityContext) {
        Account account = accountsDao.getAccount(accountSid);
        permissionEvaluator.secure(account,
                "RestComm:Create:IncomingPhoneNumbers",
                userIdentityContext);
        try {
            validate(data);
        } catch (final NullPointerException exception) {
            return status(BAD_REQUEST).entity(exception.getMessage()).build();
        }
        try{
            String number = data.getFirst("PhoneNumber");
            String isSIP = data.getFirst("isSIP");
            // cater to SIP numbers
            if(isSIP == null) {
                try {
                    number = e164(number);
                } catch (NumberParseException e) {}
            }
            Boolean isSip = Boolean.parseBoolean(isSIP);
            Boolean available = true;
            IncomingPhoneNumberFilter.Builder filterBuilder = IncomingPhoneNumberFilter.Builder.builder();
            filterBuilder.byPhoneNumber(number);
            List<IncomingPhoneNumber> incomingPhoneNumbers = dao.getIncomingPhoneNumbersByFilter(filterBuilder.build());
            /* check if number is occupied by same organization or different.
             * if it is occupied by different organization then we can add it in current.
             * but it has to be pure sip as provider numbers must be unique even across organizations.
             * https://github.com/RestComm/Restcomm-Connect/issues/2073
             */
            if(incomingPhoneNumbers != null && !incomingPhoneNumbers.isEmpty()){
                if(!isSip){
                    //provider numbers must be unique even across organizations.
                    available = false;
                }else{
                    for(IncomingPhoneNumber incomingPhoneNumber : incomingPhoneNumbers){
                        if(incomingPhoneNumber.getOrganizationSid().equals(account.getOrganizationSid())){
                            available = false;
                        }
                    }
                }
            }
            if (available) {
                IncomingPhoneNumber incomingPhoneNumber = createFrom(new Sid(accountSid), data, account.getOrganizationSid());
                String domainName = organizationsDao.getOrganization(account.getOrganizationSid()).getDomainName();
                phoneNumberParameters.setVoiceUrl((callbackPort == null || callbackPort.trim().isEmpty()) ? domainName : domainName+":"+callbackPort);
                phoneNumberParameters.setPhoneNumberType(phoneNumberType);

                org.restcomm.connect.provisioning.number.api.PhoneNumber phoneNumber = convertIncomingPhoneNumbertoPhoneNumber(incomingPhoneNumber);
                boolean hasSuceeded = false;
                boolean allowed = true;
                if(phoneNumberProvisioningManager != null && isSIP == null) {
                    ApiRequest apiRequest = new ApiRequest(accountSid, data, ApiRequest.Type.INCOMINGPHONENUMBER);
                    //Before proceed to buy the DID, check with the extensions if the purchase is allowed or not
                    if (executePreApiAction(apiRequest)) {
                        if(logger.isDebugEnabled())
                            logger.debug("buyNumber " + phoneNumber +" phoneNumberParameters: " + phoneNumberParameters);
                        hasSuceeded = phoneNumberProvisioningManager.buyNumber(phoneNumber, phoneNumberParameters);
                    } else {
                        //Extensions didn't allowed this API action
                        allowed = false;
                        if (logger.isInfoEnabled()) {
                            logger.info("DID purchase is now allowed for this account");
                        }
                    }
                    executePostApiAction(apiRequest);
                    //If Extension blocked the request, return the proper error response
                    if (!allowed) {
                        String msg = "DID purchase is now allowed for this account";
                        String error = "DID_QUOTA_EXCEEDED";
                        return status(FORBIDDEN).entity(buildErrorResponseBody(msg, error, responseType)).build();
                    }
                } else if (isSIP != null) {
                    hasSuceeded = true;
                }
                if(hasSuceeded) {
                    if(phoneNumber.getFriendlyName() != null) {
                        incomingPhoneNumber.setFriendlyName(phoneNumber.getFriendlyName());
                    }
                    if(phoneNumber.getPhoneNumber() != null) {
                        incomingPhoneNumber.setPhoneNumber(phoneNumber.getPhoneNumber());
                    }
                    dao.addIncomingPhoneNumber(incomingPhoneNumber);
                    if (APPLICATION_JSON_TYPE.equals(responseType)) {
                        return ok(gson.toJson(incomingPhoneNumber), APPLICATION_JSON).build();
                    } else if (APPLICATION_XML_TYPE.equals(responseType)) {
                        final RestCommResponse response = new RestCommResponse(incomingPhoneNumber);
                        return ok(xstream.toXML(response), APPLICATION_XML).build();
                    }
                }
            }
            return status(BAD_REQUEST).entity("21452").build();
        }catch(Exception e){
            logger.error("Exception while performing putIncomingPhoneNumber: ", e);
            return status(INTERNAL_SERVER_ERROR).build();
        }
    }

    public Response updateIncomingPhoneNumber(final String accountSid,
            final String sid,
            final MultivaluedMap<String, String> data,
            final MediaType responseType,
            UserIdentityContext userIdentityContext) {
        Account operatedAccount = accountsDao.getAccount(accountSid);
        permissionEvaluator.secure(operatedAccount,
                "RestComm:Modify:IncomingPhoneNumbers",
                userIdentityContext);
        try{
            final IncomingPhoneNumber incomingPhoneNumber = dao.getIncomingPhoneNumber(new Sid(sid));
            if (incomingPhoneNumber == null) {
                return status(NOT_FOUND).build();
            }
            permissionEvaluator.secure(operatedAccount,
                    incomingPhoneNumber.getAccountSid(),
                    SecuredType.SECURED_STANDARD,
                    userIdentityContext);
            boolean updated = true;
            updated = updateNumberAtPhoneNumberProvisioningManager(incomingPhoneNumber, organizationsDao.getOrganization(operatedAccount.getOrganizationSid()));
            if(updated) {
                dao.updateIncomingPhoneNumber(update(incomingPhoneNumber, data));
                if (APPLICATION_JSON_TYPE.equals(responseType)) {
                    return ok(gson.toJson(incomingPhoneNumber), APPLICATION_JSON).build();
                } else if (APPLICATION_XML_TYPE.equals(responseType)) {
                    final RestCommResponse response = new RestCommResponse(incomingPhoneNumber);
                    return ok(xstream.toXML(response), APPLICATION_XML).build();
                } else {
                    return null;
                }
            }
            return status(BAD_REQUEST).entity("21452").build();
        }catch(Exception e){
            logger.error("Exception while performing updateIncomingPhoneNumber: ", e);
            return status(INTERNAL_SERVER_ERROR).build();
        }
    }

    private void validate(final MultivaluedMap<String, String> data) throws RuntimeException {
        if (!data.containsKey("PhoneNumber") && !data.containsKey("AreaCode")) {
            throw new NullPointerException("Phone number can not be null.");
        }
    }

    private IncomingPhoneNumber update(final IncomingPhoneNumber incomingPhoneNumber, final MultivaluedMap<String, String> data) {
        if (data.containsKey("ApiVersion")) {
            incomingPhoneNumber.setApiVersion(getApiVersion(data));
        }
        if (data.containsKey("FriendlyName")) {
            incomingPhoneNumber.setFriendlyName(data.getFirst("FriendlyName"));
        }
        if (data.containsKey("VoiceUrl")) {
            // for all values that qualify as 'empty' populate property with null
            URI uri = getUrl("VoiceUrl", data);
            incomingPhoneNumber.setVoiceUrl(isEmpty(uri.toString()) ? null : uri);
        }
        if (data.containsKey("VoiceMethod")) {
            incomingPhoneNumber.setVoiceMethod(getMethod("VoiceMethod", data));
        }
        if (data.containsKey("VoiceFallbackUrl")) {
            URI uri = getUrl("VoiceFallbackUrl", data);
            incomingPhoneNumber.setVoiceFallbackUrl( isEmpty(uri.toString()) ? null : uri );
        }
        if (data.containsKey("VoiceFallbackMethod")) {
            incomingPhoneNumber.setVoiceFallbackMethod( getMethod("VoiceFallbackMethod", data) );
        }
        if (data.containsKey("StatusCallback")) {
            URI uri = getUrl("StatusCallback", data);
            incomingPhoneNumber.setStatusCallback( isEmpty(uri.toString()) ? null : uri );
        }
        if (data.containsKey("StatusCallbackMethod")) {
            incomingPhoneNumber.setStatusCallbackMethod(getMethod("StatusCallbackMethod", data));
        }
        if (data.containsKey("VoiceCallerIdLookup")) {
            incomingPhoneNumber.setHasVoiceCallerIdLookup(getHasVoiceCallerIdLookup(data));
        }
        if (data.containsKey("VoiceApplicationSid")) {
            if ( org.apache.commons.lang.StringUtils.isEmpty( data.getFirst("VoiceApplicationSid") ) )
                incomingPhoneNumber.setVoiceApplicationSid(null);
            else
                incomingPhoneNumber.setVoiceApplicationSid(getSid("VoiceApplicationSid", data));
        }
        if (data.containsKey("SmsUrl")) {
            URI uri = getUrl("SmsUrl", data);
            incomingPhoneNumber.setSmsUrl( isEmpty(uri.toString()) ? null : uri);
        }
        if (data.containsKey("SmsMethod")) {
            incomingPhoneNumber.setSmsMethod(getMethod("SmsMethod", data));
        }
        if (data.containsKey("SmsFallbackUrl")) {
            URI uri = getUrl("SmsFallbackUrl", data);
            incomingPhoneNumber.setSmsFallbackUrl( isEmpty(uri.toString()) ? null : uri );
        }
        if (data.containsKey("SmsFallbackMethod")) {
            incomingPhoneNumber.setSmsFallbackMethod(getMethod("SmsFallbackMethod", data));
        }
        if (data.containsKey("SmsApplicationSid")) {
            if ( org.apache.commons.lang.StringUtils.isEmpty( data.getFirst("SmsApplicationSid") ) )
                incomingPhoneNumber.setSmsApplicationSid(null);
            else
                incomingPhoneNumber.setSmsApplicationSid(getSid("SmsApplicationSid", data));

        }

        if (data.containsKey("ReferUrl")) {
            URI uri = getUrl("ReferUrl", data);
            incomingPhoneNumber.setReferUrl( isEmpty(uri.toString()) ? null : uri );
        }

        if (data.containsKey("ReferMethod")) {
            incomingPhoneNumber.setReferMethod(getMethod("ReferMethod", data));
        }

        if (data.containsKey("ReferApplicationSid")) {
            if ( org.apache.commons.lang.StringUtils.isEmpty( data.getFirst("ReferApplicationSid") ) )
                incomingPhoneNumber.setReferApplicationSid(null);
            else
                incomingPhoneNumber.setReferApplicationSid(getSid("ReferApplicationSid", data));
        }

        if (data.containsKey("VoiceCapable")) {
            incomingPhoneNumber.setVoiceCapable(Boolean.parseBoolean(data.getFirst("VoiceCapable")));
        }

        if (data.containsKey("VoiceCapable")) {
            incomingPhoneNumber.setVoiceCapable(Boolean.parseBoolean(data.getFirst("VoiceCapable")));
        }

        if (data.containsKey("SmsCapable")) {
            incomingPhoneNumber.setSmsCapable(Boolean.parseBoolean(data.getFirst("SmsCapable")));
        }

        if (data.containsKey("MmsCapable")) {
            incomingPhoneNumber.setMmsCapable(Boolean.parseBoolean(data.getFirst("MmsCapable")));
        }

        if (data.containsKey("FaxCapable")) {
            incomingPhoneNumber.setFaxCapable(Boolean.parseBoolean(data.getFirst("FaxCapable")));
        }

        if (data.containsKey("UssdUrl")) {
            URI uri = getUrl("UssdUrl", data);
            incomingPhoneNumber.setUssdUrl(isEmpty(uri.toString()) ? null : uri);
        }

        if (data.containsKey("UssdMethod")) {
            incomingPhoneNumber.setUssdMethod(getMethod("UssdMethod", data));
        }

        if (data.containsKey("UssdFallbackUrl")) {
            URI uri = getUrl("UssdFallbackUrl", data);
            incomingPhoneNumber.setUssdFallbackUrl(isEmpty(uri.toString()) ? null : uri);
        }

        if (data.containsKey("UssdFallbackMethod")) {
            incomingPhoneNumber.setUssdFallbackMethod(getMethod("UssdFallbackMethod", data));
        }

        if (data.containsKey("UssdApplicationSid")) {
            if (org.apache.commons.lang.StringUtils.isEmpty(data.getFirst("UssdApplicationSid")))
                incomingPhoneNumber.setUssdApplicationSid(null);
            else
                incomingPhoneNumber.setUssdApplicationSid(getSid("UssdApplicationSid", data));
        }

        incomingPhoneNumber.setDateUpdated(DateTime.now());
        return incomingPhoneNumber;
    }

    public Response deleteIncomingPhoneNumber(final String accountSid,
            final String sid,
            UserIdentityContext userIdentityContext) {
            Account operatedAccount = accountsDao.getAccount(accountSid);
            permissionEvaluator.secure(operatedAccount,
                    "RestComm:Delete:IncomingPhoneNumbers",
                    userIdentityContext);
        try{
            final IncomingPhoneNumber incomingPhoneNumber = dao.getIncomingPhoneNumber(new Sid(sid));
            if (incomingPhoneNumber == null) {
                return status(NOT_FOUND).build();
            }
            permissionEvaluator.secure(operatedAccount,
                    incomingPhoneNumber.getAccountSid(),
                    SecuredType.SECURED_STANDARD,
                    userIdentityContext);
            if(phoneNumberProvisioningManager != null && (incomingPhoneNumber.isPureSip() == null || !incomingPhoneNumber.isPureSip())) {
                phoneNumberProvisioningManager.cancelNumber(convertIncomingPhoneNumbertoPhoneNumber(incomingPhoneNumber));
            }
            dao.removeIncomingPhoneNumber(new Sid(sid));
            return noContent().build();
        }catch(Exception e){
            logger.error("Exception while performing deleteIncomingPhoneNumber: ", e);
            return status(INTERNAL_SERVER_ERROR).build();
        }
    }

    /*
    @SuppressWarnings("unchecked")
    private List<SipURI> getOutboundInterfaces() {
        final List<SipURI> uris = (List<SipURI>) context.getAttribute(SipServlet.OUTBOUND_INTERFACES);
        return uris;
    }
    */

    public static org.restcomm.connect.provisioning.number.api.PhoneNumber convertIncomingPhoneNumbertoPhoneNumber(IncomingPhoneNumber incomingPhoneNumber) {
        return new org.restcomm.connect.provisioning.number.api.PhoneNumber(
                incomingPhoneNumber.getFriendlyName(),
                incomingPhoneNumber.getPhoneNumber(),
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                incomingPhoneNumber.isVoiceCapable(),
                incomingPhoneNumber.isSmsCapable(),
                incomingPhoneNumber.isMmsCapable(),
                incomingPhoneNumber.isFaxCapable(),
                false);
    }

    /**
     * @param targetAccountSid
     * @param data
     * @param responseType
     * @return
     */
    protected Response migrateIncomingPhoneNumbers(String targetAccountSid,
            MultivaluedMap<String, String> data,
            MediaType responseType,
            UserIdentityContext userIdentityContext) {
        Account effectiveAccount = userIdentityContext.getEffectiveAccount();
        permissionEvaluator.secure(effectiveAccount,
                "RestComm:Modify:IncomingPhoneNumbers",
                 userIdentityContext);
        try{
            Account targetAccount = accountsDao.getAccount(targetAccountSid);
            // this is to avoid if mistakenly provided super admin account as targetAccountSid
            // if this check is not in place and someone mistakenly provided super admin
            // then all accounts and sub account in platform will be impacted
            if(targetAccount == null){
                return status(NOT_FOUND).entity("Account not found").build();
            }
            if(targetAccount.getParentSid() == null){
                return status(BAD_REQUEST).entity("Super Admin account numbers can not be migrated. Please provide a valid account sid").build();
            }else{
                String organizationSidStr = data.getFirst("OrganizationSid");
                if(organizationSidStr == null){
                    return status(BAD_REQUEST).entity("OrganizationSid cannot be null").build();
                }
                Sid organizationSid = null;
                try{
                    organizationSid = new Sid(organizationSidStr);
                }catch(IllegalArgumentException iae){
                    return status(BAD_REQUEST).entity("OrganizationSid is not valid").build();
                }
                Organization destinationOrganization = organizationsDao.getOrganization(organizationSid);
                if(destinationOrganization == null){
                    return status(NOT_FOUND).entity("Destination organization not found").build();
                }
                migrateNumbersFromAccountTree(targetAccount, destinationOrganization);
                return status(OK).build();
            }
        }catch(Exception e){
            logger.error("Exception while performing migrateIncomingPhoneNumbers: ", e);
            return status(INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * @param targetAccount
     * @param destinationOrganization
     */
    private void migrateNumbersFromAccountTree(Account targetAccount, Organization destinationOrganization) {
        List<String> subAccountsToClose = accountsDao.getSubAccountSidsRecursive(targetAccount.getSid());
        if (subAccountsToClose != null && !subAccountsToClose.isEmpty()) {
            int i = subAccountsToClose.size(); // is is the count of accounts left to process
            // we iterate backwards to handle child account numbers first, parent account's numbers next
            while (i > 0) {
                i --;
                String migrateSid = subAccountsToClose.get(i);
                try {
                    Account subAccount = accountsDao.getAccount(new Sid(migrateSid));
                    migrateSingleAccountNumbers(subAccount, destinationOrganization);
                } catch (Exception e) {
                    // if anything bad happens, log the error and continue migrating the rest of the accounts numbers.
                    logger.error("Failed migrating (child) account's numbers: account sid '" + migrateSid + "'");
                }
            }
        }
        // migrate parent account numbers too
        migrateSingleAccountNumbers(targetAccount, destinationOrganization);

    }

    /**
     * @param account
     * @param destinationOrganization
     */
    private void migrateSingleAccountNumbers(Account account, Organization destinationOrganization) {
        List<IncomingPhoneNumber> incomingPhoneNumbers = dao.getIncomingPhoneNumbers(account.getSid());
        if(incomingPhoneNumbers != null) {
            for (int i=0; i<incomingPhoneNumbers.size(); i++){
                IncomingPhoneNumber incomingPhoneNumber = incomingPhoneNumbers.get(i);
                incomingPhoneNumber.setOrganizationSid(destinationOrganization.getSid());
                //update organization in db
                dao.updateIncomingPhoneNumber(incomingPhoneNumber);
                //update number at provider's end
                if(!updateNumberAtPhoneNumberProvisioningManager(incomingPhoneNumber, destinationOrganization)){
                    //if number could not be updated at provider's end, log the error and keep moving to next number.
                    logger.error(String.format("could not update number %s at number provider %s ", incomingPhoneNumber.getPhoneNumber(), phoneNumberProvisioningManager));
                }
            }
        }
    }

    /**
     * @param incomingPhoneNumber
     * @param organization
     * @return
     */
    private boolean updateNumberAtPhoneNumberProvisioningManager(IncomingPhoneNumber incomingPhoneNumber, Organization organization){
        if(phoneNumberProvisioningManager != null && (incomingPhoneNumber.isPureSip() == null || !incomingPhoneNumber.isPureSip())) {
            String domainName = organization.getDomainName();
            phoneNumberParameters.setVoiceUrl((callbackPort == null || callbackPort.trim().isEmpty()) ? domainName : domainName+":"+callbackPort);
            if(logger.isDebugEnabled())
                logger.debug("updateNumber " + incomingPhoneNumber +" phoneNumberParameters: " + phoneNumberParameters);
            return phoneNumberProvisioningManager.updateNumber(convertIncomingPhoneNumbertoPhoneNumber(incomingPhoneNumber), phoneNumberParameters);
        }
        return true;
    }

    @Path("/{sid}")
    @DELETE
    public Response deleteIncomingPhoneNumberAsXml(@PathParam("accountSid") final String accountSid,
            @PathParam("sid") final String sid,
            @Context SecurityContext sec) {
        return deleteIncomingPhoneNumber(accountSid,
                sid,
                ContextUtil.convert(sec));
    }

    @Path("/{sid}")
    @GET
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public Response getIncomingPhoneNumberAsXml(@PathParam("accountSid") final String accountSid,
            @PathParam("sid") final String sid,
            @HeaderParam("Accept") String accept,
            @Context SecurityContext sec) {
        MediaType acceptType = retrieveMediaType(accept);
        return getIncomingPhoneNumber(accountSid,
                sid,
                acceptType,
                ContextUtil.convert(sec));
    }


    @Path("/AvailableCountries")
    @GET
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public Response getAvailableCountriesAsXml(@PathParam("accountSid") final String accountSid,
            @HeaderParam("Accept") String accept,
            @Context SecurityContext sec) {
        MediaType acceptType = retrieveMediaType(accept);
        return getAvailableCountries(accountSid,
                acceptType,
                ContextUtil.convert(sec));
    }

    @GET
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public Response getIncomingPhoneNumbers(@PathParam("accountSid") final String accountSid,
            @Context UriInfo info,
            @HeaderParam("Accept") String accept,
            @Context SecurityContext sec) {
        MediaType acceptType = retrieveMediaType(accept);
        return getIncomingPhoneNumbers(accountSid,
                PhoneNumberType.Global,
                info,
                acceptType,
                ContextUtil.convert(sec));
    }

    @POST
    @ResourceFilters({ ExtensionFilter.class })
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public Response putIncomingPhoneNumber(@PathParam("accountSid") final String accountSid,
            final MultivaluedMap<String, String> data,
            @HeaderParam("Accept") String accept,
            @Context SecurityContext sec) {
        MediaType acceptType = retrieveMediaType(accept);
        return putIncomingPhoneNumber(accountSid,
                data,
                PhoneNumberType.Global,
                acceptType,
                ContextUtil.convert(sec));
    }

    @Path("/{sid}")
    @PUT
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public Response updateIncomingPhoneNumberAsXml(@PathParam("accountSid") final String accountSid,
            @PathParam("sid") final String sid,
            final MultivaluedMap<String, String> data,
            @HeaderParam("Accept") String accept,
            @Context SecurityContext sec) {
        MediaType acceptType = retrieveMediaType(accept);
        return updateIncomingPhoneNumber(accountSid,
                sid,
                data,
                acceptType,
                ContextUtil.convert(sec));
    }

    @Path("/{sid}")
    @POST
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public Response updateIncomingPhoneNumberAsXmlPost(@PathParam("accountSid") final String accountSid,
            @PathParam("sid") final String sid,
            final MultivaluedMap<String, String> data,
            @HeaderParam("Accept") String accept,
            @Context SecurityContext sec) {
        MediaType acceptType = retrieveMediaType(accept);
        return updateIncomingPhoneNumber(accountSid,
                sid,
                data,
                acceptType,
                ContextUtil.convert(sec));
    }

    // Local Numbers

    @Path("/Local")
    @GET
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public Response getIncomingLocalPhoneNumbersAsXml(@PathParam("accountSid") final String accountSid,
            @Context UriInfo info,
            @HeaderParam("Accept") String accept,
            @Context SecurityContext sec) {
        MediaType acceptType = retrieveMediaType(accept);
        return getIncomingPhoneNumbers(accountSid,
                PhoneNumberType.Local,
                info,
                acceptType,
                ContextUtil.convert(sec));
    }

    @Path("/Local")
    @POST
    @ResourceFilters({ ExtensionFilter.class })
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public Response putIncomingLocalPhoneNumberAsXml(@PathParam("accountSid") final String accountSid,
            final MultivaluedMap<String, String> data,
            @HeaderParam("Accept") String accept,
            @Context SecurityContext sec) {
        MediaType acceptType = retrieveMediaType(accept);
        return putIncomingPhoneNumber(accountSid,
                data,
                PhoneNumberType.Local,
                acceptType,
                ContextUtil.convert(sec));
    }

    // Toll Free Numbers

    @Path("/TollFree")
    @GET
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public Response getIncomingTollFreePhoneNumbersAsXml(@PathParam("accountSid") final String accountSid,
            @Context UriInfo info,
            @HeaderParam("Accept") String accept,
            @Context SecurityContext sec) {
        MediaType acceptType = retrieveMediaType(accept);
        return getIncomingPhoneNumbers(accountSid,
                PhoneNumberType.TollFree,
                info,
                acceptType,
                ContextUtil.convert(sec));
    }

    @Path("/TollFree")
    @POST
    @ResourceFilters({ ExtensionFilter.class })
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public Response putIncomingTollFreePhoneNumberAsXml(@PathParam("accountSid") final String accountSid,
            final MultivaluedMap<String, String> data,
            @HeaderParam("Accept") String accept,
            @Context SecurityContext sec) {
        MediaType acceptType = retrieveMediaType(accept);
        return putIncomingPhoneNumber(accountSid,
                data,
                PhoneNumberType.TollFree,
                acceptType,
                ContextUtil.convert(sec));
    }

    // Mobile Numbers

    @Path("/Mobile")
    @GET
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public Response getIncomingMobilePhoneNumbersAsXml(@PathParam("accountSid") final String accountSid,
            @Context UriInfo info,
            @HeaderParam("Accept") String accept,
            @Context SecurityContext sec) {
        MediaType acceptType = retrieveMediaType(accept);
        return getIncomingPhoneNumbers(accountSid,
                PhoneNumberType.Mobile,
                info,
                acceptType,
                ContextUtil.convert(sec));
    }

    @Path("/Mobile")
    @POST
    @ResourceFilters({ ExtensionFilter.class })
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public Response putIncomingMobilePhoneNumberAsXml(@PathParam("accountSid") final String accountSid,
            final MultivaluedMap<String, String> data,
            @HeaderParam("Accept") String accept,
            @Context SecurityContext sec) {
        MediaType acceptType = retrieveMediaType(accept);
        return putIncomingPhoneNumber(accountSid, data,
                PhoneNumberType.Mobile,
                acceptType,
                ContextUtil.convert(sec));
    }


    @Path("/migrate")
    @POST
    @RolesAllowed(SUPER_ADMIN_ROLE)
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public Response migrateIncomingPhoneNumbersAsXml(@PathParam("accountSid") final String accountSid,
            final MultivaluedMap<String, String> data,
            @HeaderParam("Accept") String accept,
            @Context SecurityContext sec) {
        MediaType acceptType = retrieveMediaType(accept);
        return migrateIncomingPhoneNumbers(accountSid, data,
                acceptType,
                ContextUtil.convert(sec));
    }
}
