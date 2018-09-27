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
import org.apache.commons.configuration.Configuration;
import org.restcomm.connect.commons.amazonS3.RecordingSecurityLevel;
import org.restcomm.connect.commons.amazonS3.S3AccessTool;
import org.restcomm.connect.commons.annotations.concurrency.ThreadSafe;
import org.restcomm.connect.commons.configuration.RestcommConfiguration;
import org.restcomm.connect.commons.dao.Sid;
import org.restcomm.connect.core.service.api.RecordingService;
import org.restcomm.connect.dao.AccountsDao;
import org.restcomm.connect.dao.DaoManager;
import org.restcomm.connect.dao.RecordingsDao;
import org.restcomm.connect.dao.common.Sorting;
import org.restcomm.connect.dao.entities.Account;
import org.restcomm.connect.dao.entities.Recording;
import org.restcomm.connect.dao.entities.RecordingFilter;
import org.restcomm.connect.dao.entities.RecordingList;
import org.restcomm.connect.dao.entities.RestCommResponse;
import org.restcomm.connect.http.converter.RecordingConverter;
import org.restcomm.connect.http.converter.RecordingListConverter;
import org.restcomm.connect.http.converter.RestCommResponseConverter;
import org.restcomm.connect.http.security.ContextUtil;
import org.restcomm.connect.http.security.PermissionEvaluator.SecuredType;
import org.restcomm.connect.identity.UserIdentityContext;

import javax.annotation.PostConstruct;
import javax.servlet.ServletContext;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;
import java.io.File;
import java.net.URI;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON_TYPE;
import static javax.ws.rs.core.MediaType.APPLICATION_XML;
import static javax.ws.rs.core.MediaType.APPLICATION_XML_TYPE;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static javax.ws.rs.core.Response.Status.NOT_FOUND;
import static javax.ws.rs.core.Response.ok;
import static javax.ws.rs.core.Response.status;
import static javax.ws.rs.core.Response.temporaryRedirect;

/**
 * @author quintana.thomas@gmail.com (Thomas Quintana)
 */
@Path("/Accounts/{accountSid}/Recordings")
@ThreadSafe
@Singleton
public class RecordingsEndpoint extends AbstractEndpoint {
    private static final String SORTING_URL_PARAM_DATE_CREATED = "DateCreated";
    private static final String SORTING_URL_PARAM_DURATION = "Duration";
    private static final String SORTING_URL_PARAM_CALLSID = "CallSid";

    @Context
    private ServletContext context;
    private Configuration configuration;
    private AccountsDao accountsDao;
    private RecordingsDao dao;
    private Gson gson;
    private XStream xstream;
    private S3AccessTool s3AccessTool;
    private RecordingSecurityLevel securityLevel = RecordingSecurityLevel.SECURE;
    private RecordingListConverter listConverter;
    private String instanceId;
    private RecordingService recordingService;

    public RecordingsEndpoint() {
        super();
    }

    @PostConstruct
    public void init() {
        final DaoManager storage = (DaoManager) context.getAttribute(DaoManager.class.getName());
        configuration = (Configuration) context.getAttribute(Configuration.class.getName());
        Configuration amazonS3Configuration = configuration.subset("amazon-s3");
        configuration = configuration.subset("runtime-settings");
        super.init(configuration);
        recordingService = (RecordingService) context.getAttribute(RecordingService.class.getName());
        this.accountsDao = storage.getAccountsDao();
        dao = storage.getRecordingsDao();
        final RecordingConverter converter = new RecordingConverter(configuration);
        listConverter = new RecordingListConverter(configuration);
        final GsonBuilder builder = new GsonBuilder();
        builder.registerTypeAdapter(Recording.class, converter);
        builder.registerTypeAdapter(RecordingList.class, listConverter);
        builder.setPrettyPrinting();
        gson = builder.create();
        xstream = new XStream();
        xstream.alias("RestcommResponse", RestCommResponse.class);
        xstream.registerConverter(converter);
        xstream.registerConverter(new RecordingListConverter(configuration));
        xstream.registerConverter(new RestCommResponseConverter(configuration));
        if(!amazonS3Configuration.isEmpty()) { // Do not fail with NPE is amazonS3Configuration is not present for older install
            boolean amazonS3Enabled = amazonS3Configuration.getBoolean("enabled");
            if (amazonS3Enabled) {
                final String accessKey = amazonS3Configuration.getString("access-key");
                final String securityKey = amazonS3Configuration.getString("security-key");
                final String bucketName = amazonS3Configuration.getString("bucket-name");
                final String bucketFolder = amazonS3Configuration.getString("folder");
                final boolean reducedRedundancy = amazonS3Configuration.getBoolean("reduced-redundancy");
                final int minutesToRetainPublicUrl = amazonS3Configuration.getInt("minutes-to-retain-public-url", 10);
                final boolean removeOriginalFile = amazonS3Configuration.getBoolean("remove-original-file");
                final String bucketRegion = amazonS3Configuration.getString("bucket-region");
                final boolean testing = amazonS3Configuration.getBoolean("testing",false);
                final String testingUrl = amazonS3Configuration.getString("testing-url",null);
                s3AccessTool = new S3AccessTool(accessKey, securityKey, bucketName, bucketFolder, reducedRedundancy, minutesToRetainPublicUrl, removeOriginalFile,bucketRegion, testing, testingUrl);

                securityLevel = RecordingSecurityLevel.valueOf(amazonS3Configuration.getString("security-level", "secure").toUpperCase());
                converter.setSecurityLevel(securityLevel);
            }
        }

        xstream.registerConverter(listConverter);

        instanceId = RestcommConfiguration.getInstance().getMain().getInstanceId();
    }

    protected Response getRecording(final String accountSid,
            final String sid,
            final MediaType responseType,
            UserIdentityContext userIdentityContext) {
        Account operatedAccount = accountsDao.getAccount(accountSid);
        permissionEvaluator.secure(operatedAccount,
                "RestComm:Read:Recordings",
                userIdentityContext);
        final Recording recording = dao.getRecording(new Sid(sid));
        if (recording == null) {
            return status(NOT_FOUND).build();
        } else {
            permissionEvaluator.secure(operatedAccount,
                    recording.getAccountSid(),
                    SecuredType.SECURED_STANDARD,
                    userIdentityContext);
            if (APPLICATION_JSON_TYPE.equals(responseType)) {
                return ok(gson.toJson(recording), APPLICATION_JSON).build();
            } else if (APPLICATION_XML_TYPE.equals(responseType)) {
                final RestCommResponse response = new RestCommResponse(recording);
                return ok(xstream.toXML(response), APPLICATION_XML).build();
            } else {
                return null;
            }
        }
    }

    protected Response getRecordings(final String accountSid,
            UriInfo info,
            final MediaType responseType,
            UserIdentityContext userIdentityContext) {
        permissionEvaluator.secure(accountsDao.getAccount(accountSid),
                "RestComm:Read:Recordings",
                userIdentityContext);

        boolean localInstanceOnly = true;
        try {
            String localOnly = info.getQueryParameters().getFirst("localOnly");
            if (localOnly != null && localOnly.equalsIgnoreCase("false"))
                localInstanceOnly = false;
        } catch (Exception e) {
        }

        // shall we include sub-accounts cdrs in our query ?
        boolean querySubAccounts = false; // be default we don't
        String querySubAccountsParam = info.getQueryParameters().getFirst("SubAccounts");
        if (querySubAccountsParam != null && querySubAccountsParam.equalsIgnoreCase("true"))
            querySubAccounts = true;

        String pageSize = info.getQueryParameters().getFirst("PageSize");
        String page = info.getQueryParameters().getFirst("Page");
        String startTime = info.getQueryParameters().getFirst("StartTime");
        String endTime = info.getQueryParameters().getFirst("EndTime");
        String callSid = info.getQueryParameters().getFirst("CallSid");
        String sortParameters = info.getQueryParameters().getFirst("SortBy");

        RecordingFilter.Builder filterBuilder = RecordingFilter.Builder.builder();

        String sortBy = null;
        String sortDirection = null;

        if (sortParameters != null && !sortParameters.isEmpty()) {
            try {
                Map<String, String> sortMap = Sorting.parseUrl(sortParameters);
                sortBy = sortMap.get(Sorting.SORT_BY_KEY);
                sortDirection = sortMap.get(Sorting.SORT_DIRECTION_KEY);
            }
            catch (Exception e) {
                return status(BAD_REQUEST).entity(buildErrorResponseBody(e.getMessage(), responseType)).build();
            }
        }

        if (sortBy != null) {
            if (sortBy.equals(SORTING_URL_PARAM_DATE_CREATED)) {
                if (sortDirection != null) {
                    if (sortDirection.equalsIgnoreCase(Sorting.Direction.ASC.name())) {
                        filterBuilder.sortedByDate(Sorting.Direction.ASC);
                    } else {
                        filterBuilder.sortedByDate(Sorting.Direction.DESC);
                    }
                }
            }
            if (sortBy.equals(SORTING_URL_PARAM_DURATION)) {
                if (sortDirection != null) {
                    if (sortDirection.equalsIgnoreCase(Sorting.Direction.ASC.name())) {
                        filterBuilder.sortedByDuration(Sorting.Direction.ASC);
                    } else {
                        filterBuilder.sortedByDuration(Sorting.Direction.DESC);
                    }
                }
            }
            if (sortBy.equals(SORTING_URL_PARAM_CALLSID)) {
                if (sortDirection != null) {
                    if (sortDirection.equalsIgnoreCase(Sorting.Direction.ASC.name())) {
                        filterBuilder.sortedByCallSid(Sorting.Direction.ASC);
                    } else {
                        filterBuilder.sortedByCallSid(Sorting.Direction.DESC);
                    }
                }
            }
        }

        // TODO: Test that we are not imposing different ordering, compared to what we did before this enhancement, when the client is not asking for any type of sorting

        if (pageSize == null) {
            pageSize = "50";
        }

        if (page == null) {
            page = "0";
        }

        int limit = Integer.parseInt(pageSize);
        int offset = (page.equals("0")) ? 0 : (((Integer.parseInt(page) - 1) * Integer.parseInt(pageSize)) + Integer
                .parseInt(pageSize));

        // Shall we query cdrs of sub-accounts too ?
        // if we do, we need to find the sub-accounts involved first
        List<String> ownerAccounts = null;
        if (querySubAccounts) {
            ownerAccounts = new ArrayList<String>();
            ownerAccounts.add(accountSid); // we will also return parent account cdrs
            ownerAccounts.addAll(accountsDao.getSubAccountSidsRecursive(new Sid(accountSid)));
        }

/*        RecordingFilter filterForTotal;

        try {

            if (localInstanceOnly) {
                filterForTotal = new RecordingFilter(accountSid, ownerAccounts, startTime, endTime, callSid,
                        null, null);
            } else {
                filterForTotal = new RecordingFilter(accountSid, ownerAccounts, startTime, endTime, callSid,
                        null, null, instanceId);
            }
        } catch (ParseException e) {
            return status(BAD_REQUEST).build();
        }

        final int total = dao.getTotalRecording(filterForTotal);*/

        filterBuilder.byAccountSid(accountSid)
                .byAccountSidSet(ownerAccounts)
                .byStartTime(startTime)
                .byEndTime(endTime)
                .byCallSid(callSid)
                .limited(limit, offset);
        if (!localInstanceOnly) {
            filterBuilder.byInstanceId(instanceId);
        }

        RecordingFilter filter;
        try {
            filter = filterBuilder.build();
        } catch (ParseException e) {
            return status(BAD_REQUEST).build();
        }
        final int total = dao.getTotalRecording(filter);

        if (Integer.parseInt(page) > (total / limit)) {
            return status(BAD_REQUEST).build();
        }

/*
        RecordingFilter filter;

        try {
            if (localInstanceOnly) {
                filter = new RecordingFilter(accountSid, ownerAccounts, startTime, endTime, callSid,
                        limit, offset);
            } else {
                filter = new RecordingFilter(accountSid, ownerAccounts, startTime, endTime, callSid,
                        limit, offset, instanceId);
            }
        } catch (ParseException e) {
            return status(BAD_REQUEST).build();
        }
*/

        final List<Recording> cdrs = dao.getRecordings(filter);

        listConverter.setCount(total);
        listConverter.setPage(Integer.parseInt(page));
        listConverter.setPageSize(Integer.parseInt(pageSize));
        listConverter.setPathUri(info.getRequestUri().getPath());

        if (APPLICATION_XML_TYPE.equals(responseType)) {
            final RestCommResponse response = new RestCommResponse(new RecordingList(cdrs));
            return ok(xstream.toXML(response), APPLICATION_XML).build();
        } else if (APPLICATION_JSON_TYPE.equals(responseType)) {
            return ok(gson.toJson(new RecordingList(cdrs)), APPLICATION_JSON).build();
        } else {
            return null;
        }
    }

    protected Response getRecordingsByCall(final String accountSid,
            final String callSid,
            final MediaType responseType,
            UserIdentityContext userIdentityContext) {
        permissionEvaluator.secure(accountsDao.getAccount(accountSid),
                "RestComm:Read:Recordings",
                userIdentityContext);

        final List<Recording> recordings = dao.getRecordingsByCall(new Sid(callSid));
        if (APPLICATION_JSON_TYPE.equals(responseType)) {
            return ok(gson.toJson(recordings), APPLICATION_JSON).build();
        } else if (APPLICATION_XML_TYPE.equals(responseType)) {
            final RestCommResponse response = new RestCommResponse(new RecordingList(recordings));
            return ok(xstream.toXML(response), APPLICATION_XML).build();
        } else {
            return null;
        }
    }

    protected Response getRecordingFile (String accountSid, String sid) {
        Account operatedAccount = accountsDao.getAccount(accountSid);
//        secure(operatedAccount, "RestComm:Read:Recordings");

        final Recording recording = dao.getRecording(new Sid(sid));
        if (recording == null) {
            if (logger.isInfoEnabled()) {
                logger.info("Recording with SID: "+sid+", was not found");
            }
            return status(NOT_FOUND).build();
        } else {
//            secure(operatedAccount, recording.getAccountSid(), SecuredType.SECURED_STANDARD);
            URI recordingUri = null;
            try {
                if (recording.getS3Uri() != null) {
                    String fileExtension = recording.getS3Uri().toString().endsWith("wav") ? ".wav" : ".mp4";
                    recordingUri = s3AccessTool.getPublicUrl(recording.getSid() + fileExtension);
                    if (securityLevel.equals(RecordingSecurityLevel.REDIRECT)) {
                        return temporaryRedirect(recordingUri).build();
                    } else {
                        String contentType = recordingUri.toURL().openConnection().getContentType();
                        if (contentType == null || contentType.isEmpty()) {
                            if (fileExtension.equals(".wav")) {
                                contentType = "audio/x-wav";
                            } else {
                                contentType = "video/mp4";
                            }
                        }
                        //Fetch recording and serve it from here
                        return ok(recordingUri.toURL().openStream(), contentType).build();
                    }
                } else {
                    String path = configuration.getString("recordings-path");
                    if (!path.endsWith("/")) {
                        path += "/";
                    }
                    String fileExtension = ".wav";
                    if (recording.getFileUri() != null) {
                        fileExtension = recording.getFileUri().toString().endsWith("wav") ? ".wav" : ".mp4";
                    }
                    path += sid.toString() + fileExtension;

                    File recordingFile = new File(URI.create(path));
                    if (recordingFile.exists()) {
                        //Fetch recording and serve it from here
                        String contentType;
                        if (fileExtension.equals(".wav")) {
                            contentType = "audio/x-wav";
                        } else {
                            contentType = "video/mp4";
                        }
                        return ok(recordingFile, contentType).build();
                    } else {
                        return status(NOT_FOUND).build();
                    }
                }
            } catch (Exception e) {
                if (logger.isInfoEnabled()) {
                    logger.info("Problem during preparation of Recording file link, ", e);
                }
            }
        }
        return status(Response.Status.NOT_FOUND).build();
    }

    protected Response deleteRecording(final String accountSid,
                                    final String sid,
                                    UserIdentityContext userIdentityContext) {
        Account operatedAccount = accountsDao.getAccount(accountSid);
        permissionEvaluator.secure(operatedAccount,
                "RestComm:Delete:Recordings",
                userIdentityContext);
        final Recording recording = dao.getRecording(new Sid(sid));
        if (recording == null) {
            return status(NOT_FOUND).build();
        } else {
            permissionEvaluator.secure(operatedAccount,
                    recording.getAccountSid(),
                    SecuredType.SECURED_STANDARD,
                    userIdentityContext);
            recordingService.removeRecording(recording.getSid());
            return Response.status(Response.Status.OK).build();
        }
    }

    @Path("/{sid}.wav")
    @GET
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public Response getRecordingAsWav(@PathParam("accountSid") final String accountSid,
            @PathParam("sid") final String sid) {
        return getRecordingFile(accountSid, sid);
    }

    @Path("/{sid}.mp4")
    @GET
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public Response getRecordingAsMp4(@PathParam("accountSid") final String accountSid, @PathParam("sid") final String sid) {
        return getRecordingFile(accountSid, sid);
    }

    @Path("/{sid}")
    @GET
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public Response getRecordingAsXml(@PathParam("accountSid") final String accountSid,
            @PathParam("sid") final String sid,
            @HeaderParam("Accept") String accept,
            @Context SecurityContext sec) {
        return getRecording(accountSid,
                sid,
                retrieveMediaType(accept),
                ContextUtil.convert(sec));
    }

    @Path("/{sid}")
    @DELETE
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public Response deleteRecording(@PathParam("accountSid") final String accountSid,
                                      @PathParam("sid") final String sid,
                                      @Context SecurityContext sec) {
        return deleteRecording(accountSid,
                sid,
                ContextUtil.convert(sec));
    }

    @GET
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public Response getRecordings(@PathParam("accountSid") final String accountSid,
            @Context UriInfo info,
            @HeaderParam("Accept") String accept,
            @Context SecurityContext sec) {
        return getRecordings(accountSid,
                info,
                retrieveMediaType(accept),
                ContextUtil.convert(sec));
    }

}
