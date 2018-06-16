/*
 * TeleStax, Open Source Cloud Communications
 * Copyright 2011-2013, Telestax Inc and individual contributors
 * by the @authors tag.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.restcomm.connect.http;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.jersey.spi.resource.Singleton;
import com.thoughtworks.xstream.XStream;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import javax.annotation.PostConstruct;
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
import static javax.ws.rs.core.Response.Status.*;
import static javax.ws.rs.core.Response.ok;
import static javax.ws.rs.core.Response.status;
import javax.ws.rs.core.SecurityContext;
import org.apache.commons.configuration.Configuration;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.log4j.Logger;
import org.joda.time.DateTime;
import org.restcomm.connect.commons.annotations.concurrency.ThreadSafe;
import org.restcomm.connect.commons.dao.Sid;
import org.restcomm.connect.commons.util.StringUtils;
import org.restcomm.connect.dao.AccountsDao;
import org.restcomm.connect.dao.DaoManager;
import org.restcomm.connect.dao.GeolocationDao;
import org.restcomm.connect.dao.entities.Account;
import org.restcomm.connect.dao.entities.Geolocation;
import org.restcomm.connect.dao.entities.Geolocation.GeolocationType;
import org.restcomm.connect.dao.entities.GeolocationList;
import org.restcomm.connect.dao.entities.RestCommResponse;
import org.restcomm.connect.http.converter.ClientListConverter;
import org.restcomm.connect.http.converter.GeolocationConverter;
import org.restcomm.connect.http.converter.GeolocationListConverter;
import org.restcomm.connect.http.converter.RestCommResponseConverter;
import org.restcomm.connect.http.security.ContextUtil;
import org.restcomm.connect.http.security.PermissionEvaluator.SecuredType;
import org.restcomm.connect.identity.UserIdentityContext;
/**
 * @author <a href="mailto:fernando.mendioroz@telestax.com"> Fernando Mendioroz </a>
 *
 */
@Path("/Accounts/{accountSid}/Geolocation")
@ThreadSafe
@Singleton
public class GeolocationEndpoint extends AbstractEndpoint {

    @Context
    protected ServletContext context;
    protected Configuration configuration;
    protected GeolocationDao dao;
    protected Gson gson;
    protected XStream xstream;
    protected AccountsDao accountsDao;
    private static final Logger logger = Logger.getLogger(GeolocationEndpoint.class);
    private static final String ImmediateGT = Geolocation.GeolocationType.Immediate.toString();
    private static final String NotificationGT = Geolocation.GeolocationType.Notification.toString();
    private String cause;
    private String rStatus;




    private static enum responseStatus {
        Queued("queued"), Sent("sent"), Processing("processing"), Successful("successful"), PartiallySuccessful(
            "partially-successful"), LastKnown("last-known"), Failed("failed"), Unauthorized("unauthorized"), Rejected(
            "rejected");

        private final String rs;

        private responseStatus(final String rs) {
            this.rs = rs;
        }

        @Override
        public String toString() {
            return rs;
        }
    }

    public GeolocationEndpoint() {
        super();
    }

    @PostConstruct
    public void init() {
        final DaoManager storage = (DaoManager) context.getAttribute(DaoManager.class.getName());
        dao = storage.getGeolocationDao();
        accountsDao = storage.getAccountsDao();
        configuration = (Configuration) context.getAttribute(Configuration.class.getName());
        configuration = configuration.subset("runtime-settings");
        super.init(configuration);
        final GeolocationConverter converter = new GeolocationConverter(configuration);
        final GsonBuilder builder = new GsonBuilder();
        builder.registerTypeAdapter(Geolocation.class, converter);
        builder.setPrettyPrinting();
        gson = builder.create();
        xstream = new XStream();
        xstream.alias("RestcommResponse", RestCommResponse.class);
        xstream.registerConverter(converter);
        xstream.registerConverter(new ClientListConverter(configuration));
        xstream.registerConverter(new GeolocationListConverter(configuration));
        xstream.registerConverter(new RestCommResponseConverter(configuration));
    }

    protected Response getGeolocation(final String accountSid,
            final String sid,
            final MediaType responseType,
            UserIdentityContext userIdentityContext) {
        Account account;
        try {
            account = accountsDao.getAccount(accountSid);
            permissionEvaluator.secure(account,
                    "RestComm:Read:Geolocation",
                    userIdentityContext);
        } catch (final Exception exception) {
            return status(UNAUTHORIZED).build();
        }
        // final Geolocation geolocation = dao.getGeolocation(new Sid(sid));
        Geolocation geolocation = null;
        if (Sid.pattern.matcher(sid).matches()) {
            geolocation = dao.getGeolocation(new Sid(sid));
        }
        if (geolocation == null) {
            return status(NOT_FOUND).build();
        } else {
            try {
                permissionEvaluator.secure(account,
                        geolocation.getAccountSid(),
                        SecuredType.SECURED_APP,
                        userIdentityContext);
            } catch (final Exception exception) {
                return status(UNAUTHORIZED).build();
            }
            if (APPLICATION_XML_TYPE.equals(responseType)) {
                final RestCommResponse response = new RestCommResponse(geolocation);
                return ok(xstream.toXML(response), APPLICATION_XML).build();
            } else if (APPLICATION_JSON_TYPE.equals(responseType)) {
                return ok(gson.toJson(geolocation), APPLICATION_JSON).build();
            } else {
                return null;
            }
        }
    }

    protected Response getGeolocations(final String accountSid,
            final MediaType responseType,
            UserIdentityContext userIdentityContext) {
        Account account;
        try {
            account = accountsDao.getAccount(accountSid);
            permissionEvaluator.secure(account,
                    "RestComm:Read:Geolocation",
                    SecuredType.SECURED_APP,
                    userIdentityContext);
        } catch (final Exception exception) {
            return status(UNAUTHORIZED).build();
        }
        final List<Geolocation> geolocations = dao.getGeolocations(new Sid(accountSid));
        if (APPLICATION_XML_TYPE.equals(responseType)) {
            final RestCommResponse response = new RestCommResponse(new GeolocationList(geolocations));
            return ok(xstream.toXML(response), APPLICATION_XML).build();
        } else if (APPLICATION_JSON_TYPE.equals(responseType)) {
            return ok(gson.toJson(geolocations), APPLICATION_JSON).build();
        } else {
            return null;
        }
    }

    protected Response deleteGeolocation(final String accountSid,
            final String sid,
            UserIdentityContext userIdentityContext) {
        Account account;
        try {
            account = accountsDao.getAccount(accountSid);
            permissionEvaluator.secure(account,
                    "RestComm:Delete:Geolocation",
                    userIdentityContext);
            Geolocation geolocation = dao.getGeolocation(new Sid(sid));
            if (geolocation != null) {
                permissionEvaluator.secure(account,
                        geolocation.getAccountSid(),
                        SecuredType.SECURED_APP,
                        userIdentityContext
                );
            }
        } catch (final Exception exception) {
            return status(UNAUTHORIZED).build();
        }
        dao.removeGeolocation(new Sid(sid));
        return ok().build();
    }

    public Response putGeolocation(final String accountSid,
            final MultivaluedMap<String, String> data,
            GeolocationType geolocationType,
            final MediaType responseType,
            UserIdentityContext userIdentityContext) {
        Account account;
        try {
            account = accountsDao.getAccount(accountSid);
            permissionEvaluator.secure(account,
                    "RestComm:Create:Geolocation",
                    SecuredType.SECURED_APP,
                    userIdentityContext);
        } catch (final Exception exception) {
            return status(UNAUTHORIZED).build();
        }

        try {
            validate(data, geolocationType);
        } catch (final NullPointerException nullPointerException) {
            // API compliance check regarding missing mandatory parameters
            return status(BAD_REQUEST).entity(nullPointerException.getMessage()).build();
        } catch (final IllegalArgumentException illegalArgumentException) {
            // API compliance check regarding malformed parameters
            cause = illegalArgumentException.getMessage();
            rStatus = responseStatus.Failed.toString();
        } catch (final UnsupportedOperationException unsupportedOperationException) {
            // API compliance check regarding parameters not allowed for Immediate type of Geolocation
            return status(BAD_REQUEST).entity(unsupportedOperationException.getMessage()).build();
        }

        /*********************************************/
        /*** Query GMLC for Location Data, stage 1 ***/
        /*********************************************/
        try {
            String targetMSISDN = data.getFirst("DeviceIdentifier");
            Configuration gmlcConf = configuration.subset("gmlc");
            String gmlcURI = gmlcConf.getString("gmlc-uri");
            // Authorization for further stage of the project
            String gmlcUser = gmlcConf.getString("gmlc-user");
            String gmlcPassword = gmlcConf.getString("gmlc-password");
            // Credentials credentials = new UsernamePasswordCredentials(gmlcUser, gmlcPassword);
            URL url = new URL(gmlcURI + targetMSISDN);
            HttpClient client = HttpClientBuilder.create().build();
            HttpGet request = new HttpGet(String.valueOf(url));
            // Authorization for further stage of the project
            request.addHeader("User-Agent", gmlcUser);
            request.addHeader("User-Password", gmlcPassword);
            HttpResponse response = client.execute(request);
            final HttpEntity entity = response.getEntity();
            if (entity != null) {
                InputStream stream = entity.getContent();
                try {
                    BufferedReader br = new BufferedReader(
                        new InputStreamReader(stream));
                    String gmlcResponse = null;
                    while (null != (gmlcResponse = br.readLine())) {
                        List<String> items = Arrays.asList(gmlcResponse.split("\\s*,\\s*"));
                        if (logger.isInfoEnabled()) {
                            logger.info("Data retrieved from GMLC: " + items.toString());
                        }
                        for (String item : items) {
                            for (int i = 0; i < items.size(); i++) {
                                if (item.contains("mcc")) {
                                    String token = item.substring(item.lastIndexOf("=") + 1);
                                    data.putSingle("MobileCountryCode", token);
                                }
                                if (item.contains("mnc")) {
                                    String token = item.substring(item.lastIndexOf("=") + 1);
                                    data.putSingle("MobileNetworkCode", token);
                                }
                                if (item.contains("lac")) {
                                    String token = item.substring(item.lastIndexOf("=") + 1);
                                    data.putSingle("LocationAreaCode", token);
                                }
                                if (item.contains("cellid")) {
                                    String token = item.substring(item.lastIndexOf("=") + 1);
                                    data.putSingle("CellId", token);
                                }
                                if (item.contains("aol")) {
                                    String token = item.substring(item.lastIndexOf("=") + 1);
                                    data.putSingle("LocationAge", token);
                                }
                                if (item.contains("vlrNumber")) {
                                    String token = item.substring(item.lastIndexOf("=") + 1);
                                    data.putSingle("NetworkEntityAddress", token);
                                }
                                if (item.contains("latitude")) {
                                    String token = item.substring(item.lastIndexOf("=") + 1);
                                    data.putSingle("DeviceLatitude", token);
                                }
                                if (item.contains("longitude")) {
                                    String token = item.substring(item.lastIndexOf("=") + 1);
                                    data.putSingle("DeviceLongitude", token);
                                }
                                if (item.contains("civicAddress")) {
                                    String token = item.substring(item.lastIndexOf("=") + 1);
                                    data.putSingle("FormattedAddress", token);
                                }
                            }
                        }
                        if (gmlcURI != null && gmlcResponse != null) {
                            // For debugging/logging purposes only
                            if (logger.isDebugEnabled()) {
                                logger.debug("Geolocation data of " + targetMSISDN + " retrieved from GMCL at: " + gmlcURI);
                                logger.debug("MCC (Mobile Country Code) = " + getInteger("MobileCountryCode", data));
                                logger.debug("MNC (Mobile Network Code) = " + data.getFirst("MobileNetworkCode"));
                                logger.debug("LAC (Location Area Code) = " + data.getFirst("LocationAreaCode"));
                                logger.debug("CI (Cell ID) = " + data.getFirst("CellId"));
                                logger.debug("AOL (Age of Location) = " + getInteger("LocationAge", data));
                                logger.debug("NNN (Network Node Number/Address) = " + +getLong("NetworkEntityAddress", data));
                                logger.debug("Devide Latitude = " + data.getFirst("DeviceLatitude"));
                                logger.debug("Devide Longitude = " + data.getFirst("DeviceLongitude"));
                                logger.debug("Civic Address = " + data.getFirst("FormattedAddress"));
                            }
                        }
                    }
                } finally {
                    stream.close();
                }
            }

        } catch (Exception ex) {
            if (logger.isInfoEnabled()) {
                logger.info("Problem while trying to retrieve data from GMLC");
            }
            return status(INTERNAL_SERVER_ERROR).entity(ex.getMessage()).build();
        }

        Geolocation geolocation = createFrom(new Sid(accountSid), data, geolocationType);

        if (geolocation.getResponseStatus() != null
            && geolocation.getResponseStatus().equals(responseStatus.Rejected.toString())) {
            if (APPLICATION_XML_TYPE.equals(responseType)) {
                final RestCommResponse response = new RestCommResponse(geolocation);
                return ok(xstream.toXML(response), APPLICATION_XML).build();
            } else if (APPLICATION_JSON_TYPE.equals(responseType)) {
                return ok(gson.toJson(geolocation), APPLICATION_JSON).build();
            } else {
                return null;
            }
        } else {

            dao.addGeolocation(geolocation);

            if (APPLICATION_XML_TYPE.equals(responseType)) {
                final RestCommResponse response = new RestCommResponse(geolocation);
                return ok(xstream.toXML(response), APPLICATION_XML).build();
            } else if (APPLICATION_JSON_TYPE.equals(responseType)) {
                return ok(gson.toJson(geolocation), APPLICATION_JSON).build();
            } else {
                return null;
            }
        }
    }

    private void validate(final MultivaluedMap<String, String> data, Geolocation.GeolocationType glType)
        throws RuntimeException {

        // ** Validation of Geolocation POST requests with valid type **/
        if (!glType.toString().equals(ImmediateGT) && !glType.toString().equals(NotificationGT)) {
            throw new NullPointerException("Geolocation Type can not be null, but either \"Immediate\" or \"Notification\".");
        }

        // *** DeviceIdentifier can not be null ***/
        if (!data.containsKey("DeviceIdentifier")) {
            throw new NullPointerException("DeviceIdentifier value can not be null");
        }

        // *** StatusCallback can not be null ***/
        if (!data.containsKey("StatusCallback")) {
            throw new NullPointerException("StatusCallback value can not be null");
        }

        // *** DesiredAccuracy must be API compliant: High, Average or Low***/
        if (data.containsKey("DesiredAccuracy")) {
            String desiredAccuracy = data.getFirst("DesiredAccuracy");
            if (!desiredAccuracy.equalsIgnoreCase("High") && !desiredAccuracy.equalsIgnoreCase("Average")
                && !desiredAccuracy.equalsIgnoreCase("Low")) {
                throw new IllegalArgumentException("DesiredAccuracy value not API compliant");
            }
        }

        // *** DeviceLatitude must be API compliant***/
        if (data.containsKey("DeviceLatitude")) {
            String deviceLat = data.getFirst("DeviceLatitude");
            Boolean devLatWGS84 = validateGeoCoordinatesFormat(deviceLat);
            if (!devLatWGS84) {
                throw new IllegalArgumentException("DeviceLatitude not API compliant");
            }
        }

        // *** DeviceLongitude must be API compliant***/
        if (data.containsKey("DeviceLongitude")) {
            String deviceLong = data.getFirst("DeviceLongitude");
            Boolean devLongWGS84 = validateGeoCoordinatesFormat(deviceLong);
            if (!devLongWGS84) {
                throw new IllegalArgumentException("DeviceLongitude not API compliant");
            }
        }

        // *** GeofenceEvent must belong to Notification type of Geolocation, not null and API compliant: in, out or in-out***/
        if (!data.containsKey("GeofenceEvent") && glType.toString().equals(NotificationGT)) {
            throw new NullPointerException("GeofenceEvent value con not be null for Notification type of Geolocation");
        } else if (data.containsKey("GeofenceEvent") && !glType.toString().equals(NotificationGT)) {
            throw new UnsupportedOperationException("GeofenceEvent only applies for Notification type of Geolocation");
        } else if (data.containsKey("GeofenceEvent") && glType.toString().equals(NotificationGT)) {
            String geofenceEvent = data.getFirst("GeofenceEvent");
            if (!geofenceEvent.equalsIgnoreCase("in") && !geofenceEvent.equalsIgnoreCase("out")
                && !geofenceEvent.equalsIgnoreCase("in-out")) {
                throw new IllegalArgumentException("GeofenceEvent value not API compliant");
            }
        }

        // *** EventGeofenceLatitude must belong to Notification type of Geolocation, not null and API compliant ***/
        if (!data.containsKey("EventGeofenceLatitude") && glType.toString().equals(NotificationGT)) {
            throw new NullPointerException("EventGeofenceLatitude value con not be null for Notification type of Geolocation");
        } else if (data.containsKey("EventGeofenceLatitude") && !glType.toString().equals(NotificationGT)) {
            throw new UnsupportedOperationException("EventGeofenceLatitude only applies for Notification type of Geolocation");
        } else if (data.containsKey("EventGeofenceLatitude") && glType.toString().equals(NotificationGT)) {
            String eventGeofenceLat = data.getFirst("EventGeofenceLatitude");
            Boolean eventGeofenceLongWGS84 = validateGeoCoordinatesFormat(eventGeofenceLat);
            if (!eventGeofenceLongWGS84) {
                throw new IllegalArgumentException("EventGeofenceLatitude format not API compliant");
            }
        }

        // *** EventGeofenceLongitude must belong to Notification type of Geolocation and must be API compliant ***/
        if (!data.containsKey("EventGeofenceLongitude") && glType.toString().equals(NotificationGT)) {
            throw new NullPointerException("EventGeofenceLongitude value con not be null for Notification type of Geolocation");
        } else if (data.containsKey("EventGeofenceLongitude") && !glType.toString().equals(NotificationGT)) {
            throw new UnsupportedOperationException("EventGeofenceLongitude only applies for Notification type of Geolocation");
        } else if (data.containsKey("EventGeofenceLongitude") && glType.toString().equals(NotificationGT)) {
            String eventGeofenceLong = data.getFirst("EventGeofenceLongitude");
            Boolean eventGeofenceLongWGS84 = validateGeoCoordinatesFormat(eventGeofenceLong);
            if (!eventGeofenceLongWGS84) {
                throw new IllegalArgumentException("EventGeofenceLongitude format not API compliant");
            }
        }

        // *** GeofenceRange can not be null in Notification type of Geolocation***/
        if (!data.containsKey("GeofenceRange") && glType.toString().equals(NotificationGT)) {
            throw new NullPointerException("GeofenceRange value con not be null for Notification type of Geolocation");
        } else if (data.containsKey("GeofenceRange") && !glType.toString().equals(NotificationGT)) {
            throw new UnsupportedOperationException("GeofenceRange only applies for Notification type of Geolocation");
        }

        // *** LocationTimestamp must be API compliant: DateTime format only***/
        try {
            if (data.containsKey("LocationTimestamp")) {
                @SuppressWarnings("unused")
                DateTime locationTimestamp = getDateTime("LocationTimestamp", data);
            }
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("LocationTimestamp value is not API compliant");
        }
    }

    private Geolocation createFrom(final Sid accountSid, final MultivaluedMap<String, String> data,
                                   Geolocation.GeolocationType glType) {

        if (rStatus != null && rStatus.equals(responseStatus.Failed.toString())) {
            Geolocation gl = buildFailedGeolocation(accountSid, data, glType);
            return gl;
        } else {
            Geolocation gl = buildGeolocation(accountSid, data, glType);
            return gl;
        }
    }

    private Geolocation buildGeolocation(final Sid accountSid, final MultivaluedMap<String, String> data,
                                         Geolocation.GeolocationType glType) {
        final Geolocation.Builder builder = Geolocation.builder();
        final Sid sid = Sid.generate(Sid.Type.GEOLOCATION);
        String geoloctype = glType.toString();
        builder.setSid(sid);
        DateTime currentDateTime = DateTime.now();
        builder.setDateUpdated(currentDateTime);
        builder.setAccountSid(accountSid);
        builder.setSource(data.getFirst("Source"));
        builder.setDeviceIdentifier(data.getFirst("DeviceIdentifier"));
        builder.setGeolocationType(glType);
        builder.setResponseStatus(data.getFirst("ResponseStatus"));
        builder.setCause(data.getFirst("Cause"));
        builder.setCellId(data.getFirst("CellId"));
        builder.setLocationAreaCode(data.getFirst("LocationAreaCode"));
        builder.setMobileCountryCode(getInteger("MobileCountryCode", data));
        builder.setMobileNetworkCode(data.getFirst("MobileNetworkCode"));
        builder.setNetworkEntityAddress(getLong("NetworkEntityAddress", data));
        builder.setAgeOfLocationInfo(getInteger("LocationAge", data));
        builder.setDeviceLatitude(data.getFirst("DeviceLatitude"));
        builder.setDeviceLongitude(data.getFirst("DeviceLongitude"));
        builder.setAccuracy(getLong("Accuracy", data));
        builder.setPhysicalAddress(data.getFirst("PhysicalAddress"));
        builder.setInternetAddress(data.getFirst("InternetAddress"));
        builder.setFormattedAddress(data.getFirst("FormattedAddress"));
        builder.setLocationTimestamp(getDateTime("LocationTimestamp", data));
        builder.setEventGeofenceLatitude(data.getFirst("EventGeofenceLatitude"));
        builder.setEventGeofenceLongitude(data.getFirst("EventGeofenceLongitude"));
        builder.setRadius(getLong("Radius", data));
        builder.setGeolocationPositioningType(data.getFirst("GeolocationPositioningType"));
        builder.setLastGeolocationResponse(data.getFirst("LastGeolocationResponse"));
        builder.setApiVersion(getApiVersion(data));
        String rootUri = configuration.getString("root-uri");
        rootUri = StringUtils.addSuffixIfNotPresent(rootUri, "/");
        final StringBuilder buffer = new StringBuilder();
        buffer.append(rootUri).append(getApiVersion(data)).append("/Accounts/").append(accountSid.toString())
            .append("/Geolocation/" + geoloctype + "/").append(sid.toString());
        builder.setUri(URI.create(buffer.toString()));
        return builder.build();
    }

    private Geolocation buildFailedGeolocation(final Sid accountSid, final MultivaluedMap<String, String> data,
                                               Geolocation.GeolocationType glType) {
        final Geolocation.Builder builder = Geolocation.builder();
        final Sid sid = Sid.generate(Sid.Type.GEOLOCATION);
        String geoloctype = glType.toString();
        builder.setSid(sid);
        DateTime currentDateTime = DateTime.now();
        builder.setDateUpdated(currentDateTime);
        builder.setAccountSid(accountSid);
        builder.setSource(data.getFirst("Source"));
        builder.setDeviceIdentifier(data.getFirst("DeviceIdentifier"));
        builder.setResponseStatus(rStatus);
        builder.setGeolocationType(glType);
        builder.setCause(cause);
        builder.setApiVersion(getApiVersion(data));
        String rootUri = configuration.getString("root-uri");
        rootUri = StringUtils.addSuffixIfNotPresent(rootUri, "/");
        final StringBuilder buffer = new StringBuilder();
        buffer.append(rootUri).append(getApiVersion(data)).append("/Accounts/").append(accountSid.toString())
            .append("/Geolocation/" + geoloctype + "/").append(sid.toString());
        builder.setUri(URI.create(buffer.toString()));
        return builder.build();
    }

    protected Response updateGeolocation(final String accountSid,
            final String sid,
            final MultivaluedMap<String, String> data,
            final MediaType responseType,
            UserIdentityContext userIdentityContext) {
        Account account;
        try {
            account = accountsDao.getAccount(accountSid);
            permissionEvaluator.secure(account,
                    "RestComm:Modify:Geolocation",
                    userIdentityContext);
        } catch (final Exception exception) {
            return status(UNAUTHORIZED).build();
        }
        Geolocation geolocation = dao.getGeolocation(new Sid(sid));
        if (geolocation == null) {
            return status(NOT_FOUND).build();
        } else {
            try {
                permissionEvaluator.secure(account,
                        geolocation.getAccountSid(),
                        SecuredType.SECURED_APP,
                        userIdentityContext);
            } catch (final NullPointerException exception) {
                return status(BAD_REQUEST).entity(exception.getMessage()).build();
            } catch (final Exception exception) {
                return status(UNAUTHORIZED).build();
            }

            geolocation = update(geolocation, data);
            dao.updateGeolocation(geolocation);
            if (APPLICATION_XML_TYPE.equals(responseType)) {
                final RestCommResponse response = new RestCommResponse(geolocation);
                return ok(xstream.toXML(response), APPLICATION_XML).build();
            } else if (APPLICATION_JSON_TYPE.equals(responseType)) {
                return ok(gson.toJson(geolocation), APPLICATION_JSON).build();
            } else {
                return null;
            }
        }
    }

    private Geolocation update(final Geolocation geolocation, final MultivaluedMap<String, String> data) {

        Geolocation updatedGeolocation = geolocation;
        // *** Set of parameters with provided data for Geolocation update***//
        if (data.containsKey("Source")) {
            updatedGeolocation = updatedGeolocation.setSource(data.getFirst("Source"));

        }

        if (data.containsKey("DeviceIdentifier")) {
            updatedGeolocation = updatedGeolocation.setDeviceIdentifier(data.getFirst("DeviceIdentifier"));
        }

        if (data.containsKey("ResponseStatus")) {
            updatedGeolocation = updatedGeolocation.setResponseStatus(data.getFirst("ResponseStatus"));
            updatedGeolocation = updatedGeolocation.setDateUpdated(DateTime.now());
            if (data.containsKey("Cause") && (updatedGeolocation.getResponseStatus().equals(responseStatus.Rejected.toString())
                || updatedGeolocation.getResponseStatus().equals(responseStatus.Unauthorized.toString())
                || updatedGeolocation.getResponseStatus().equals(responseStatus.Failed.toString()))) {
                updatedGeolocation = updatedGeolocation.setCause(data.getFirst("Cause"));
                // cause is set to null if responseStatus is not rejected, failed or unauthorized
            }
            if (!updatedGeolocation.getResponseStatus().equals(responseStatus.Rejected.toString())
                || !updatedGeolocation.getResponseStatus().equals(responseStatus.Unauthorized.toString())
                || !updatedGeolocation.getResponseStatus().equals(responseStatus.Failed.toString())) {
                updatedGeolocation = updatedGeolocation.setCause(null);
                // cause is set to null if responseStatus is not rejected, failed or unauthorized
            }
        }

        if (updatedGeolocation.getResponseStatus() != null
            && (!updatedGeolocation.getResponseStatus().equals(responseStatus.Unauthorized.toString())
            || !updatedGeolocation.getResponseStatus().equals(responseStatus.Failed.toString()))) {
            updatedGeolocation = updatedGeolocation.setCause(null);
            // "Cause" is set to null if "ResponseStatus" is not null and is neither "rejected", "unauthorized" nor "failed"
        }

        if (data.containsKey("CellId")) {
            updatedGeolocation = updatedGeolocation.setCellId(data.getFirst("CellId"));
        }

        if (data.containsKey("LocationAreaCode")) {
            updatedGeolocation = updatedGeolocation.setLocationAreaCode(data.getFirst("LocationAreaCode"));
        }

        if (data.containsKey("MobileCountryCode")) {
            updatedGeolocation = updatedGeolocation.setMobileCountryCode(getInteger("MobileCountryCode", data));
        }

        if (data.containsKey("MobileNetworkCode")) {
            updatedGeolocation = updatedGeolocation.setMobileNetworkCode(data.getFirst("MobileNetworkCode"));
        }

        if (data.containsKey("NetworkEntityAddress")) {
            updatedGeolocation = updatedGeolocation.setNetworkEntityAddress(getLong("NetworkEntityAddress", data));
        }

        if (data.containsKey("LocationAge")) {
            updatedGeolocation = updatedGeolocation.setAgeOfLocationInfo(getInteger("LocationAge", data));
        }

        if (data.containsKey("DeviceLatitude")) {
            String deviceLat = data.getFirst("DeviceLatitude");
            Boolean deviceLatWGS84 = validateGeoCoordinatesFormat(deviceLat);
            if (!deviceLatWGS84) {
                return buildFailedGeolocationUpdate(geolocation, data, geolocation.getGeolocationType(),
                    responseStatus.Failed.toString(), "DeviceLatitude format not API compliant");
            } else {
                updatedGeolocation = updatedGeolocation.setDeviceLatitude(deviceLat);
            }
        }

        if (data.containsKey("DeviceLongitude")) {
            updatedGeolocation = updatedGeolocation.setDeviceLongitude(data.getFirst("DeviceLongitude"));
            String deviceLong = data.getFirst("DeviceLongitude");
            Boolean deviceLongGS84 = validateGeoCoordinatesFormat(deviceLong);
            if (!deviceLongGS84) {
                return buildFailedGeolocationUpdate(geolocation, data, geolocation.getGeolocationType(),
                    responseStatus.Failed.toString(), "DeviceLongitude format not API compliant");
            } else {
                updatedGeolocation = updatedGeolocation.setDeviceLongitude(deviceLong);
            }
        }

        if (data.containsKey("Accuracy")) {
            updatedGeolocation = updatedGeolocation.setAccuracy(getLong("Accuracy", data));
        }

        if (data.containsKey("PhysicalAddress")) {
            updatedGeolocation = updatedGeolocation.setPhysicalAddress(data.getFirst("PhysicalAddress"));
        }

        if (data.containsKey("InternetAddress")) {
            updatedGeolocation = updatedGeolocation.setInternetAddress(data.getFirst("InternetAddress"));
        }

        if (data.containsKey("FormattedAddress")) {
            updatedGeolocation = updatedGeolocation.setFormattedAddress(data.getFirst("FormattedAddress"));
        }

        if (data.containsKey("LocationTimestamp")) {
            updatedGeolocation = updatedGeolocation.setLocationTimestamp(getDateTime("LocationTimestamp", data));
        }

        if (data.containsKey("EventGeofenceLatitude") && geolocation.getGeolocationType().toString().equals(NotificationGT)) {
            String eventGeofenceLat = data.getFirst("EventGeofenceLatitude");
            Boolean eventGeofenceLatWGS84 = validateGeoCoordinatesFormat(eventGeofenceLat);
            if (!eventGeofenceLatWGS84) {
                return buildFailedGeolocationUpdate(geolocation, data, geolocation.getGeolocationType(),
                    responseStatus.Failed.toString(), "EventGeofenceLatitude format not API compliant");
            } else {
                updatedGeolocation = updatedGeolocation.setEventGeofenceLatitude(eventGeofenceLat);
            }
        }

        if (data.containsKey("EventGeofenceLongitude") && geolocation.getGeolocationType().toString().equals(NotificationGT)) {
            String eventGeofenceLong = data.getFirst("EventGeofenceLongitude");
            Boolean eventGeofenceLongWGS84 = validateGeoCoordinatesFormat(eventGeofenceLong);
            if (!eventGeofenceLongWGS84) {
                return buildFailedGeolocationUpdate(geolocation, data, geolocation.getGeolocationType(),
                    responseStatus.Failed.toString(), "EventGeofenceLongitude format not API compliant");
            } else {
                updatedGeolocation = updatedGeolocation.setEventGeofenceLongitude(eventGeofenceLong);
            }
        }

        if (data.containsKey("Radius") && geolocation.getGeolocationType().toString().equals(NotificationGT)) {
            updatedGeolocation = updatedGeolocation.setRadius(getLong("Radius", data));
        }

        if (data.containsKey("GeolocationPositioningType")) {
            updatedGeolocation = updatedGeolocation.setGeolocationPositioningType(data.getFirst("GeolocationPositioningType"));
        }

        if (data.containsKey("LastGeolocationResponse")) {
            updatedGeolocation = updatedGeolocation.setLastGeolocationResponse(data.getFirst("LastGeolocationResponse"));
        }

        DateTime thisDateTime = DateTime.now();
        updatedGeolocation = updatedGeolocation.setDateUpdated(thisDateTime);
        return updatedGeolocation;
    }

    private Geolocation buildFailedGeolocationUpdate(Geolocation geolocation, final MultivaluedMap<String, String> data,
                                                     Geolocation.GeolocationType glType, String responseStatus, String wrongUpdateCause) {
        final Sid accountSid = geolocation.getAccountSid();
        final Sid sid = geolocation.getSid();
        final Geolocation.Builder builder = Geolocation.builder();
        String geoloctype = glType.toString();
        DateTime currentDateTime = DateTime.now();
        builder.setSid(sid);
        builder.setDateUpdated(currentDateTime);
        builder.setAccountSid(accountSid);
        builder.setResponseStatus(responseStatus);
        builder.setCause(wrongUpdateCause);
        builder.setSource(data.getFirst("Source"));
        builder.setDeviceIdentifier(data.getFirst("DeviceIdentifier"));
        builder.setGeolocationType(glType);
        builder.setApiVersion(getApiVersion(data));
        String rootUri = configuration.getString("root-uri");
        rootUri = StringUtils.addSuffixIfNotPresent(rootUri, "/");
        final StringBuilder buffer = new StringBuilder();
        buffer.append(rootUri).append(getApiVersion(data)).append("/Accounts/").append(accountSid.toString())
            .append("/Geolocation/" + geoloctype + "/").append(sid.toString());
        builder.setUri(URI.create(buffer.toString()));
        return builder.build();
    }

    private boolean validateGeoCoordinatesFormat(String coordinates) {

        String degrees = "\\u00b0";
        String minutes = "'";
        Boolean WGS84_validation;
        Boolean pattern1 = coordinates.matches("[NWSE]{1}\\d{1,3}\\s\\d{1,2}\\s\\d{1,2}\\.\\d{1,2}$");
        Boolean pattern2 = coordinates.matches("\\d{1,3}\\s\\d{1,2}\\s\\d{1,2}\\.\\d{1,2}[NWSE]{1}$");
        Boolean pattern3 = coordinates.matches("\\d{1,3}[" + degrees + "]\\d{1,3}[" + minutes + "]\\d{1,2}\\.\\d{1,2}["
            + minutes + "][" + minutes + "][NWSE]{1}$");
        Boolean pattern4 = coordinates.matches("[NWSE]{1}\\d{1,3}[" + degrees + "]\\d{1,3}[" + minutes + "]\\d{1,2}\\.\\d{1,2}["
            + minutes + "][" + minutes + "]$");
        Boolean pattern5 = coordinates.matches("\\d{1,3}\\s\\d{1,2}\\s\\d{1,2}\\.\\d{1,2}$");
        Boolean pattern6 = coordinates.matches("-?\\d{1,3}\\s\\d{1,2}\\s\\d{1,2}\\.\\d{1,2}$");
        Boolean pattern7 = coordinates.matches("-?\\d+(\\.\\d+)?");

        if (pattern1 || pattern2 || pattern3 || pattern4 || pattern5 || pattern6 || pattern7) {
            WGS84_validation = true;
            return WGS84_validation;
        } else {
            WGS84_validation = false;
            return WGS84_validation;
        }
    }

    @Path("/Immediate/{sid}")
    @DELETE
    public Response deleteImmediateGeolocationAsXml(@PathParam("accountSid") final String accountSid,
                                                    @PathParam("sid") final String sid,
                                                    @Context SecurityContext sec) {
        return deleteGeolocation(accountSid, sid, ContextUtil.convert(sec));
    }

    @Path("/Immediate/{sid}")
    @GET
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public Response getImmediateGeolocationAsXml(@PathParam("accountSid") final String accountSid,
                                                 @PathParam("sid") final String sid,
                                                 @HeaderParam("Accept") String accept,
                                                 @Context SecurityContext sec) {
        return getGeolocation(accountSid, sid, retrieveMediaType(accept),
                ContextUtil.convert(sec));
    }

    @Path("/Immediate")
    @POST
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public Response putImmediateGeolocationXmlPost(@PathParam("accountSid") final String accountSid,
                                                   final MultivaluedMap<String, String> data,
                                                   @HeaderParam("Accept") String accept,
                                                   @Context SecurityContext sec) {
        return putGeolocation(accountSid,
                data,
                Geolocation.GeolocationType.Immediate,
                retrieveMediaType(accept),
                ContextUtil.convert(sec));
    }

    @Path("/Immediate/{sid}")
    @POST
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public Response putImmediateGeolocationAsXmlPost(@PathParam("accountSid") final String accountSid,
                                                     @PathParam("sid") final String sid,
                                                     final MultivaluedMap<String, String> data,
                                                     @HeaderParam("Accept") String accept,
                                                     @Context SecurityContext sec) {
        return updateGeolocation(accountSid, sid, data, retrieveMediaType(accept),
                ContextUtil.convert(sec));
    }

    @Path("/Immediate/{sid}")
    @PUT
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public Response updateImmediateGeolocationAsXmlPut(@PathParam("accountSid") final String accountSid,
                                                       @PathParam("sid") final String sid,
                                                       final MultivaluedMap<String, String> data,
                                                       @HeaderParam("Accept") String accept,
                                                       @Context SecurityContext sec) {
        return updateGeolocation(accountSid, sid, data, retrieveMediaType(accept),
                ContextUtil.convert(sec));
    }

    /*******************************************/
    // *** Notification type of Geolocation ***//
    /*******************************************/

    @Path("/Notification/{sid}")
    @DELETE
    public Response deleteNotificationGeolocationAsXml(@PathParam("accountSid") final String accountSid,
                                                       @PathParam("sid") final String sid,
                                                       @Context SecurityContext sec) {
        return deleteGeolocation(accountSid, sid,ContextUtil.convert(sec));
    }

    @Path("/Notification/{sid}")
    @GET
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public Response getNotificationGeolocationAsXml(@PathParam("accountSid") final String accountSid,
                                                    @PathParam("sid") final String sid,
                                                    @HeaderParam("Accept") String accept,
                                                    @Context SecurityContext sec) {
        return getGeolocation(accountSid, sid, retrieveMediaType(accept),
                ContextUtil.convert(sec));
    }

    @Path("/Notification")
    @POST
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public Response putNotificationGeolocationXmlPost(@PathParam("accountSid") final String accountSid,
                                                      final MultivaluedMap<String, String> data,
                                                      @HeaderParam("Accept") String accept,
                                                      @Context SecurityContext sec) {
        return putGeolocation(accountSid,
                data,
                Geolocation.GeolocationType.Notification,
                retrieveMediaType(accept),
                ContextUtil.convert(sec));
    }

    @Path("/Notification/{sid}")
    @POST
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public Response putNotificationGeolocationAsXmlPost(@PathParam("accountSid") final String accountSid,
                                                        @PathParam("sid") final String sid,
                                                        final MultivaluedMap<String, String> data,
                                                        @HeaderParam("Accept") String accept,
                                                        @Context SecurityContext sec) {
        return updateGeolocation(accountSid,
                sid,
                data,
                retrieveMediaType(accept),
                ContextUtil.convert(sec));
    }

    @Path("/Notification/{sid}")
    @PUT
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public Response updateNotificationGeolocationAsXmlPut(@PathParam("accountSid") final String accountSid,
                                                          @PathParam("sid") final String sid,
                                                          final MultivaluedMap<String, String> data,
                                                          @HeaderParam("Accept") String accept,
                                                          @Context SecurityContext sec) {
        return updateGeolocation(accountSid,
                sid,
                data,
                retrieveMediaType(accept),
                ContextUtil.convert(sec));
    }

    @GET
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public Response getGeolocationsAsXml(@PathParam("accountSid") final String accountSid,
            @HeaderParam("Accept") String accept,
            @Context SecurityContext sec) {
        return getGeolocations(accountSid,
                retrieveMediaType(accept),
                ContextUtil.convert(sec));
    }

}

