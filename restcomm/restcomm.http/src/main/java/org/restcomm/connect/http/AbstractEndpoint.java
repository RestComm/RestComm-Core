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

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber.PhoneNumber;
import java.math.BigInteger;
import java.net.URI;
import java.util.List;
import javax.servlet.ServletContext;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import org.apache.commons.configuration.Configuration;
import org.apache.log4j.Logger;
import org.joda.time.DateTime;
import org.restcomm.connect.commons.annotations.concurrency.NotThreadSafe;
import org.restcomm.connect.commons.dao.Sid;
import org.restcomm.connect.commons.util.StringUtils;
import org.restcomm.connect.dao.AccountsDao;
import org.restcomm.connect.dao.DaoManager;
import org.restcomm.connect.dao.OrganizationsDao;
import org.restcomm.connect.extension.api.ApiRequest;
import org.restcomm.connect.extension.api.ExtensionType;
import org.restcomm.connect.extension.api.RestcommExtensionGeneric;
import org.restcomm.connect.extension.controller.ExtensionController;
import org.restcomm.connect.http.security.PermissionEvaluator;

/**
 * @author quintana.thomas@gmail.com (Thomas Quintana)
 * @author jean.deruelle@telestax.com
 */
@NotThreadSafe
public abstract class AbstractEndpoint {
    protected Logger logger = Logger.getLogger(AbstractEndpoint.class);

    private String defaultApiVersion;
    protected Configuration configuration;
    protected String baseRecordingsPath;
    //List of extensions for RestAPI
    protected List<RestcommExtensionGeneric> extensions;
    @Context
    protected ServletContext context;
    protected AccountsDao accountsDao;
    protected OrganizationsDao organizationsDao;
    protected PermissionEvaluator permissionEvaluator;

    public AbstractEndpoint() {
        super();
    }

    public AbstractEndpoint(ServletContext context) {
        this.context = context;
    }

    protected MediaType retrieveMediaType(String accept) {
        if (accept.contains("json")) {
             return MediaType.APPLICATION_JSON_TYPE;
        }
        return MediaType.APPLICATION_XML_TYPE;
    }

    protected void init(final Configuration configuration) {
        final String path = configuration.getString("recordings-path");
        baseRecordingsPath = StringUtils.addSuffixIfNotPresent(path, "/");
        defaultApiVersion = configuration.getString("api-version");
        extensions = ExtensionController.getInstance().getExtensions(ExtensionType.RestApi);
        final DaoManager storage = (DaoManager) context.getAttribute(DaoManager.class.getName());
        this.accountsDao = storage.getAccountsDao();
        this.organizationsDao = storage.getOrganizationsDao();
        if (logger.isInfoEnabled()) {
            if (extensions != null) {
                logger.info("RestAPI extensions: "+(extensions != null ? extensions.size() : "0"));
            }
        }
        permissionEvaluator = new PermissionEvaluator(context);
    }

    protected String getApiVersion(final MultivaluedMap<String, String> data) {
        String apiVersion = defaultApiVersion;
        if (data != null && data.containsKey("ApiVersion")) {
            apiVersion = data.getFirst("ApiVersion");
        }
        return apiVersion;
    }

    protected PhoneNumber getPhoneNumber(final MultivaluedMap<String, String> data) {
        final PhoneNumberUtil phoneNumberUtil = PhoneNumberUtil.getInstance();
        PhoneNumber phoneNumber = null;
        try {
            phoneNumber = phoneNumberUtil.parse(data.getFirst("PhoneNumber"), "US");
        } catch (final NumberParseException ignored) {
        }
        return phoneNumber;
    }

    protected String getMethod(final String name, final MultivaluedMap<String, String> data) {
        String method = "POST";
        if (data.containsKey(name)) {
            method = data.getFirst(name);
        }
        return method;
    }

    protected Sid getSid(final String name, final MultivaluedMap<String, String> data) {
        Sid sid = null;
        if (data.containsKey(name)) {
            sid = new Sid(data.getFirst(name));
        }
        return sid;
    }

    protected URI getUrl(final String name, final MultivaluedMap<String, String> data) {
        URI uri = null;
        if (data.containsKey(name)) {
            uri = URI.create(data.getFirst(name));
        }
        return uri;
    }

    protected Integer getInteger(final String name, final MultivaluedMap<String, String> data) {
        Integer integer = null;
        if (data.containsKey(name)) {
            integer = new Integer(data.getFirst(name));
        }
        return integer;
    }

    protected BigInteger getBigInteger(final String name, final MultivaluedMap<String, String> data) {
        BigInteger bigInteger = null;
        if (data.containsKey(name)) {
            bigInteger = new BigInteger(data.getFirst(name));
        }
        return bigInteger;
    }

    protected Long getLong(final String name, final MultivaluedMap<String, String> data) {
        Long l = null;
        if (data.containsKey(name)) {
            l = new Long(data.getFirst(name));
        }
        return l;
    }

    protected DateTime getDateTime(final String name, final MultivaluedMap<String, String> data) {
        DateTime dateTime = null;
        if (data.containsKey(name)) {
            dateTime = new DateTime(data.getFirst(name));
        }
        return dateTime;
    }

    protected Boolean getBoolean(final String name, final MultivaluedMap<String, String> data) {
        Boolean b = null;
        if (data.containsKey(name)) {
            b = new Boolean(data.getFirst(name));
        }
        return b;
    }

    protected boolean getHasVoiceCallerIdLookup(final MultivaluedMap<String, String> data) {
        boolean hasVoiceCallerIdLookup = false;
        if (data.containsKey("VoiceCallerIdLookup")) {
            final String value = data.getFirst("VoiceCallerIdLookup");
            if ("true".equalsIgnoreCase(value)) {
                return true;
            }
        }
        return hasVoiceCallerIdLookup;
    }

    // A general purpose method to test incoming parameters for meaningful data
    protected boolean isEmpty(Object value) {
        if (value == null)
            return true;
        if ( value.equals("") )
            return true;
        return false;
    }

    // Quick'n'dirty error response building
    String buildErrorResponseBody(String message, MediaType type) {
        if (!type.equals(MediaType.APPLICATION_XML_TYPE)) { // fallback to JSON if not XML
            return "{\"message\":\""+message+"\"}";
        } else {
            return "<RestcommResponse><Message>" + message + "</Message></RestcommResponse>";
        }
    }

    String buildErrorResponseBody(String message, String error, MediaType type) {
        if (!type.equals(MediaType.APPLICATION_XML_TYPE)) { // fallback to JSON if not XML
            return "{\"message\":\""+message+"\",\n\"error\":\""+error+"\"}";
        } else {
            return "<RestcommResponse><Message>" + message + "</Message><Error>"+ error +"</Error></RestcommResponse>";
        }
    }

    protected boolean executePreApiAction(final ApiRequest apiRequest) {
        ExtensionController ec = ExtensionController.getInstance();
        return ec.executePreApiAction(apiRequest, extensions).isAllowed();
    }

    protected boolean executePostApiAction(final ApiRequest apiRequest) {
        ExtensionController ec = ExtensionController.getInstance();
        return ec.executePostApiAction(apiRequest, extensions).isAllowed();
    }
}
