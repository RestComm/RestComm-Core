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

import com.fasterxml.jackson.databind.JsonNode;
import com.github.fge.jackson.JsonLoader;
import com.github.fge.jsonschema.core.report.ProcessingReport;
import com.github.fge.jsonschema.main.JsonSchema;
import com.github.fge.jsonschema.main.JsonSchemaFactory;
import com.sun.jersey.core.header.LinkHeader;
import com.sun.jersey.core.header.LinkHeader.LinkHeaderBuilder;
import com.sun.jersey.spi.resource.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import javax.annotation.PostConstruct;
import javax.annotation.security.PermitAll;
import javax.annotation.security.RolesAllowed;
import javax.servlet.ServletContext;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.GenericEntity;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;
import static javax.ws.rs.core.Response.status;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;
import org.restcomm.connect.commons.annotations.concurrency.ThreadSafe;
import org.restcomm.connect.commons.dao.Sid;
import org.restcomm.connect.core.service.api.ProfileService;
import org.restcomm.connect.dao.AccountsDao;
import org.restcomm.connect.dao.DaoManager;
import org.restcomm.connect.dao.OrganizationsDao;
import org.restcomm.connect.dao.ProfileAssociationsDao;
import org.restcomm.connect.dao.ProfilesDao;
import org.restcomm.connect.dao.entities.Account;
import org.restcomm.connect.dao.entities.Organization;
import org.restcomm.connect.dao.entities.Profile;
import static org.restcomm.connect.dao.entities.Profile.DEFAULT_PROFILE_SID;
import org.restcomm.connect.dao.entities.ProfileAssociation;
import org.restcomm.connect.http.exceptionmappers.CustomReasonPhraseType;
import org.restcomm.connect.http.security.AccountPrincipal;
import static org.restcomm.connect.http.security.AccountPrincipal.SUPER_ADMIN_ROLE;

@javax.ws.rs.Path("/Profiles")
@ThreadSafe
@RolesAllowed(SUPER_ADMIN_ROLE)
@Singleton
public class ProfileEndpoint {

    protected Logger logger = Logger.getLogger(ProfileEndpoint.class);

    public static final String PROFILE_CONTENT_TYPE = "application/instance+json";
    public static final String PROFILE_SCHEMA_CONTENT_TYPE = "application/schema+json";
    public static final String PROFILE_REL_TYPE = "related";
    public static final String SCHEMA_REL_TYPE = "schema";
    public static final String DESCRIBED_REL_TYPE = "describedby";
    public static final String LINK_HEADER = "Link";
    public static final String PROFILE_ENCODING = "UTF-8";
    public static final String TITLE_PARAM = "title";

    public static final String ACCOUNTS_PREFIX = "AC";
    public static final String ORGANIZATIONS_PREFIX = "OR";

    @Context
    protected ServletContext context;

    private Configuration runtimeConfiguration;
    private Configuration rootConfiguration; // top-level configuration element
    private ProfilesDao profilesDao;
    private ProfileAssociationsDao profileAssociationsDao;
    private AccountsDao accountsDao;
    private OrganizationsDao organizationsDao;
    protected ProfileService profileService;
    private JsonNode schemaJson;
    private JsonSchema profileSchema;



    public ProfileEndpoint() {
        super();
    }

    @PostConstruct
    void init() {
        rootConfiguration = (Configuration) context.getAttribute(Configuration.class.getName());
        runtimeConfiguration = rootConfiguration.subset("runtime-settings");
        final DaoManager storage = (DaoManager) context.getAttribute(DaoManager.class.getName());
        profileService = (ProfileService)context.getAttribute(ProfileService.class.getName());
        profileAssociationsDao = storage.getProfileAssociationsDao();
        this.accountsDao = storage.getAccountsDao();
        this.organizationsDao = storage.getOrganizationsDao();
        profilesDao = ((DaoManager) context.getAttribute(DaoManager.class.getName())).getProfilesDao();
        try {
            schemaJson = JsonLoader.fromResource("/org/restcomm/connect/http/schemas/rc-profile-schema.json");
            final JsonSchemaFactory factory = JsonSchemaFactory.byDefault();
            profileSchema = factory.getJsonSchema(schemaJson);
        } catch (Exception e) {
            logger.error("Error starting Profile endpoint.", e);
        }
    }

    class ProfileExt {

        Profile profile;
        String uri;

        public ProfileExt(Profile profile, String uri) {
            this.profile = profile;
            this.uri = uri;
        }

        public String getUri() {
            return uri;
        }

        public Date getDateCreated() {
            return profile.getDateCreated();
        }

        public Date getDateUpdated() {
            return profile.getDateUpdated();
        }

        public String getSid() {
            return profile.getSid();
        }

    }

    public Response getProfiles(UriInfo info) {
        try {
            List<Profile> allProfiles = profilesDao.getAllProfiles();
            List<ProfileExt> extProfiles = new ArrayList(allProfiles.size());
            for (Profile pAux : allProfiles) {
                URI pURI = info.getBaseUriBuilder().path(this.getClass()).path(pAux.getSid()).build();
                extProfiles.add(new ProfileExt(pAux, pURI.toString()));
            }
            GenericEntity<List<ProfileExt>> entity = new GenericEntity<List<ProfileExt>>(extProfiles) {
            };
            return Response.ok(entity, MediaType.APPLICATION_JSON).build();
        } catch (SQLException ex) {
            logger.debug("getting profiles", ex);
            return Response.serverError().entity(ex.getMessage()).build();
        }
    }

    public Response unlinkProfile(String profileSidStr, HttpHeaders headers) {
        checkProfileExists(profileSidStr);
        List<String> requestHeader = checkLinkHeader(headers);
        LinkHeader link = LinkHeader.valueOf(requestHeader.get(0));
        checkRelType(link);
        String targetSid = retrieveSid(link.getUri());
        checkTargetSid(new Sid(targetSid));
        profileAssociationsDao.deleteProfileAssociationByTargetSid(targetSid, profileSidStr);
        return Response.ok().build();
    }

    private String retrieveSid(URI uri) {
        Path paths = Paths.get(uri.getPath());
        return paths.getName(paths.getNameCount() - 1).toString();
    }

    private void checkRelType(LinkHeader link) {
        if (!link.getRel().contains(PROFILE_REL_TYPE)) {
            logger.debug("Only related rel type supported");
            CustomReasonPhraseType stat = new CustomReasonPhraseType(Response.Status.BAD_REQUEST, "Only related rel type supported");
            throw new WebApplicationException(status(stat).build());
        }
    }

    private List<String> checkLinkHeader(HttpHeaders headers) {
        List<String> requestHeader = headers.getRequestHeader(LINK_HEADER);
        if (requestHeader.size() != 1) {
            logger.debug("Only one Link supported");
            CustomReasonPhraseType stat = new CustomReasonPhraseType(Response.Status.BAD_REQUEST, "Only one Link supported");
            throw new WebApplicationException(status(stat).build());
        }
        return requestHeader;
    }

    public Response linkProfile(String profileSidStr, HttpHeaders headers, UriInfo uriInfo) {
        checkProfileExists(profileSidStr);
        List<String> requestHeader = checkLinkHeader(headers);
        LinkHeader link = LinkHeader.valueOf(requestHeader.get(0));
        checkRelType(link);
        String targetSidStr = retrieveSid(link.getUri());
        Sid targetSid = new Sid(targetSidStr);
        checkTargetSid(targetSid);
        Sid profileSid = new Sid(profileSidStr);
        ProfileAssociation assoc = new ProfileAssociation(profileSid, targetSid, new Date(), new Date());
        //remove previous link if any
        profileAssociationsDao.deleteProfileAssociationByTargetSid(targetSidStr);
        profileAssociationsDao.addProfileAssociation(assoc);
        return Response.ok().build();
    }

    public Response deleteProfile(String profileSid) {
        checkProfileExists(profileSid);
        checkDefaultProfile(profileSid);
        profilesDao.deleteProfile(profileSid);
        profileAssociationsDao.deleteProfileAssociationByProfileSid(profileSid);
        return Response.ok().build();
    }

    private void checkDefaultProfile(String profileSid) {
        if (profileSid.equals(DEFAULT_PROFILE_SID)) {
            logger.debug("Modififying default profile is forbidden");
            CustomReasonPhraseType stat = new CustomReasonPhraseType(Response.Status.FORBIDDEN, "Modififying default profile is forbidden");
            throw new WebApplicationException(status(stat).build());
        }
    }

    private Profile checkProfileExists(String profileSid) {
        try {
            Profile profile = profilesDao.getProfile(profileSid);
            if (profile != null) {
                return profile;
            } else {
                if (logger.isDebugEnabled()) {
                    logger.debug("Profile not found:" + profileSid);
                }
                CustomReasonPhraseType stat = new CustomReasonPhraseType(Response.Status.NOT_FOUND, "Profile not found");
                throw new WebApplicationException(status(stat).build());
            }
        } catch (SQLException ex) {
            logger.debug("SQL issue getting profile.", ex);
            CustomReasonPhraseType stat = new CustomReasonPhraseType(Response.Status.INTERNAL_SERVER_ERROR, "SQL issue getting profile.");
            throw new WebApplicationException(status(stat).build());
        }
    }

    public Response updateProfile(String profileSid, InputStream body, UriInfo info) {
        checkProfileExists(profileSid);
        checkDefaultProfile(profileSid);
        try {
            String profileStr = IOUtils.toString(body, Charset.forName(PROFILE_ENCODING));
            final JsonNode profileJson = JsonLoader.fromString(profileStr);
            ProcessingReport report = profileSchema.validate(profileJson);
            if (report.isSuccess()) {
                Profile profile = new Profile(profileSid, profileStr, new Date(), new Date());
                profilesDao.updateProfile(profile);
                Profile updatedProfile = profilesDao.getProfile(profileSid);
                return getProfileBuilder(updatedProfile, info).build();
            } else {
                return Response.status(Response.Status.BAD_REQUEST).entity(report.toString()).build();
            }
        } catch (Exception ex) {
            logger.debug("updating profiles", ex);
            return Response.serverError().entity(ex.getMessage()).build();
        }
    }

    public LinkHeader composeSchemaLink(UriInfo info) throws MalformedURLException {
        URI build = info.getBaseUriBuilder().path(this.getClass()).path("/schemas/rc-profile-schema.json").build();
        return LinkHeader.uri(build).rel(DESCRIBED_REL_TYPE).build();
    }

    /**
     *
     * @param sid
     * @return first two chars in sid
     */
    private String extractSidPrefix(Sid sid) {
        return sid.toString().substring(0, 2);

    }

    public LinkHeader composeLink(Sid targetSid, UriInfo info) throws MalformedURLException {
        String sid = targetSid.toString();
        URI uri = null;
        LinkHeaderBuilder link = null;
        switch (extractSidPrefix(targetSid)) {
            case ACCOUNTS_PREFIX:
                uri = info.getBaseUriBuilder().path(AccountsEndpoint.class).path(sid).build();
                link = LinkHeader.uri(uri).parameter(TITLE_PARAM, "Accounts");
                break;
            case ORGANIZATIONS_PREFIX:
                uri = info.getBaseUriBuilder().path(AccountsEndpoint.class).path(sid).build();
                link = LinkHeader.uri(uri).parameter(TITLE_PARAM, "Organizations");
                break;
            default:
        }
        if (link != null) {
            return link.rel(PROFILE_REL_TYPE).build();
        } else {
            return null;
        }
    }

    public void checkTargetSid(Sid sid) {
        switch (extractSidPrefix(sid)) {
            case ACCOUNTS_PREFIX:
                Account acc = accountsDao.getAccount(sid);
                if (acc == null) {
                    CustomReasonPhraseType stat = new CustomReasonPhraseType(Response.Status.NOT_FOUND, "Account not found");
                    throw new WebApplicationException(status(stat).build());

                }
                break;
            case ORGANIZATIONS_PREFIX:
                Organization org = organizationsDao.getOrganization(sid);
                if (org == null) {
                    CustomReasonPhraseType stat = new CustomReasonPhraseType(Response.Status.NOT_FOUND, "Organization not found");
                    throw new WebApplicationException(status(stat).build());

                }
                break;
            default:
                CustomReasonPhraseType stat = new CustomReasonPhraseType(Response.Status.NOT_FOUND, "Link not supported");
                throw new WebApplicationException(status(stat).build());

        }
    }

    public ResponseBuilder getProfileBuilder(Profile profile , UriInfo info) {
        try {
            Response.ResponseBuilder ok = Response.ok(profile.getProfileDocument());
            List<ProfileAssociation> profileAssociationsByProfileSid = profileAssociationsDao.getProfileAssociationsByProfileSid(profile.getSid());
            for (ProfileAssociation assoc : profileAssociationsByProfileSid) {
                LinkHeader composeLink = composeLink(assoc.getTargetSid(), info);
                ok.header(LINK_HEADER, composeLink.toString());
            }
            ok.header(LINK_HEADER, composeSchemaLink(info));
            String profileStr = profile.getProfileDocument();// IOUtils.toString(profile.getProfileDocument(), PROFILE_ENCODING);
            ok.entity(profileStr);
            ok.lastModified(profile.getDateUpdated());
            ok.type(PROFILE_CONTENT_TYPE);
            return ok;
        } catch (Exception ex) {
            logger.debug("getting profile", ex);
            return Response.serverError().entity(ex.getMessage());
        }
    }

    private void checkProfileAccess(String profileSid, SecurityContext secCtx) {
        AccountPrincipal userPrincipal = (AccountPrincipal) secCtx.getUserPrincipal();
        if (!userPrincipal.isSuperAdmin()) {
            Sid accountSid = userPrincipal.getIdentityContext().getAccountKey().getAccount().getSid();
            Profile effectiveProfile = profileService.retrieveEffectiveProfileByAccountSid(accountSid);
            if (!effectiveProfile.getSid().equals(profileSid)) {
                CustomReasonPhraseType stat = new CustomReasonPhraseType(Response.Status.FORBIDDEN, "Profile not linked");
                throw new WebApplicationException(status(stat).build());
            }
        }
    }

    public Response getProfile(String profileSid, UriInfo info, SecurityContext secCtx) {
        Profile profile = checkProfileExists(profileSid);
        checkProfileAccess(profileSid, secCtx);
        return getProfileBuilder(profile, info).build();
    }

    public Response createProfile(InputStream body, UriInfo info) {

        Response response;
        try {
            Sid profileSid = Sid.generate(Sid.Type.PROFILE);
            String profileStr = IOUtils.toString(body, Charset.forName(PROFILE_ENCODING));
            final JsonNode profileJson = JsonLoader.fromString(profileStr);
            ProcessingReport report = profileSchema.validate(profileJson);
            if (report.isSuccess()) {
                Profile profile = new Profile(profileSid.toString(), profileStr, new Date(), new Date());
                profilesDao.addProfile(profile);
                URI location = info.getBaseUriBuilder().path(this.getClass()).path(profileSid.toString()).build();
                Profile createdProfile = profilesDao.getProfile(profileSid.toString());
                response = getProfileBuilder(createdProfile, info).status(Status.CREATED).location(location).build();
            } else {
                response = Response.status(Response.Status.BAD_REQUEST).entity(report.toString()).build();
            }
        } catch (Exception ex) {
            logger.debug("creating profile", ex);
            return Response.serverError().entity(ex.getMessage()).build();
        }
        return response;
    }

    public Response getSchema(String schemaId) {
        try {
            JsonNode schema = JsonLoader.fromResource("/org/restcomm/connect/http/schemas/" + schemaId);
            return Response.ok(schema.toString(), PROFILE_SCHEMA_CONTENT_TYPE).build();
        } catch (IOException ex) {
            logger.debug("getting schema", ex);
            return Response.status(Status.NOT_FOUND).build();
        }
    }

    private static final String OVERRIDE_HDR = "X-HTTP-Method-Override";

    @GET
    @Produces(APPLICATION_JSON)
    public Response getProfilesAsJson(@Context UriInfo info) {
        return getProfiles(info);
    }

    @POST
    @Consumes({PROFILE_CONTENT_TYPE, APPLICATION_JSON})
    @Produces({PROFILE_CONTENT_TYPE, APPLICATION_JSON})
    public Response createProfileAsJson(InputStream body, @Context UriInfo info) {
        return createProfile(body, info);
    }

    @javax.ws.rs.Path("/{profileSid}")
    @GET
    @Produces({PROFILE_CONTENT_TYPE, MediaType.APPLICATION_JSON})
    @PermitAll
    public Response getProfileAsJson(@PathParam("profileSid") final String profileSid,
            @Context UriInfo info, @Context SecurityContext secCtx) {
        return getProfile(profileSid, info, secCtx);
    }

    @javax.ws.rs.Path("/{profileSid}")
    @PUT
    @Consumes({PROFILE_CONTENT_TYPE, MediaType.APPLICATION_JSON})
    @Produces({PROFILE_CONTENT_TYPE, MediaType.APPLICATION_JSON})
    public Response updateProfileAsJson(@PathParam("profileSid") final String profileSid,
            InputStream body, @Context UriInfo info,
            @Context HttpHeaders headers) {
        if (headers.getRequestHeader(OVERRIDE_HDR) != null
                && headers.getRequestHeader(OVERRIDE_HDR).size() > 0) {
            String overrideHdr = headers.getRequestHeader(OVERRIDE_HDR).get(0);
            switch (overrideHdr) {
                case "LINK":
                    return linkProfile(profileSid, headers, info);
                case "UNLINK":
                    return unlinkProfile(profileSid, headers);

            }
        }
        return updateProfile(profileSid, body, info);
    }

    @javax.ws.rs.Path("/{profileSid}")
    @DELETE
    public Response deleteProfileAsJson(@PathParam("profileSid") final String profileSid) {
        return deleteProfile(profileSid);
    }

    @javax.ws.rs.Path("/{profileSid}")
    @LINK
    @Produces(APPLICATION_JSON)
    public Response linkProfileAsJson(@PathParam("profileSid") final String profileSid,
            @Context HttpHeaders headers, @Context UriInfo info
    ) {
        return linkProfile(profileSid, headers, info);
    }

    @javax.ws.rs.Path("/{profileSid}")
    @UNLINK
    @Produces(APPLICATION_JSON)
    public Response unlinkProfileAsJson(@PathParam("profileSid") final String profileSid,
            @Context HttpHeaders headers) {
        return unlinkProfile(profileSid, headers);
    }

    @javax.ws.rs.Path("/schemas/{schemaId}")
    @GET
    @Produces({PROFILE_SCHEMA_CONTENT_TYPE, MediaType.APPLICATION_JSON})
    @PermitAll
    public Response getProfileSchemaAsJson(@PathParam("schemaId") final String schemaId) {
        return getSchema(schemaId);
    }
}
