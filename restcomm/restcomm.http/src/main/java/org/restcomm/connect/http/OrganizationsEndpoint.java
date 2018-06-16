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
import com.sun.jersey.core.header.LinkHeader;
import com.sun.jersey.spi.resource.Singleton;
import com.thoughtworks.xstream.XStream;
import org.apache.commons.configuration.Configuration;
import org.joda.time.DateTime;
import org.restcomm.connect.commons.annotations.concurrency.ThreadSafe;
import org.restcomm.connect.commons.dao.Sid;
import org.restcomm.connect.core.service.api.ClientPasswordHashingService;
import org.restcomm.connect.core.service.api.ProfileService;
import org.restcomm.connect.dao.ClientsDao;
import org.restcomm.connect.dao.DaoManager;
import org.restcomm.connect.dao.entities.Client;
import org.restcomm.connect.dao.entities.Organization;
import org.restcomm.connect.dao.entities.OrganizationList;
import org.restcomm.connect.dao.entities.Profile;
import org.restcomm.connect.dao.entities.RestCommResponse;
import org.restcomm.connect.dns.DnsProvisioningManager;
import org.restcomm.connect.dns.DnsProvisioningManagerProvider;
import org.restcomm.connect.http.converter.ClientConverter;
import org.restcomm.connect.http.converter.ClientListConverter;
import org.restcomm.connect.http.converter.OrganizationConverter;
import org.restcomm.connect.http.converter.OrganizationListConverter;
import org.restcomm.connect.http.converter.RestCommResponseConverter;
import org.restcomm.connect.http.security.ContextUtil;
import org.restcomm.connect.identity.UserIdentityContext;

import javax.annotation.PostConstruct;
import javax.annotation.security.RolesAllowed;
import javax.servlet.ServletContext;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON_TYPE;
import static javax.ws.rs.core.MediaType.APPLICATION_XML;
import static javax.ws.rs.core.MediaType.APPLICATION_XML_TYPE;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static javax.ws.rs.core.Response.Status.CONFLICT;
import static javax.ws.rs.core.Response.Status.FORBIDDEN;
import static javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;
import static javax.ws.rs.core.Response.Status.NOT_FOUND;
import static javax.ws.rs.core.Response.ok;
import static javax.ws.rs.core.Response.status;
import static org.restcomm.connect.http.ProfileEndpoint.PROFILE_REL_TYPE;
import static org.restcomm.connect.http.ProfileEndpoint.TITLE_PARAM;
import static org.restcomm.connect.http.security.AccountPrincipal.ADMIN_ROLE;
import static org.restcomm.connect.http.security.AccountPrincipal.SUPER_ADMIN_ROLE;

/**
 * @author maria.farooq@telestax.com (Maria Farooq)
 */
@Path("/Organizations")
@ThreadSafe
@RolesAllowed(SUPER_ADMIN_ROLE)
@Singleton
public class OrganizationsEndpoint extends AbstractEndpoint {
    @Context
    private ServletContext context;
    private DnsProvisioningManager dnsProvisioningManager;
    private Gson gson;
    private XStream xstream;
    private final String MSG_EMPTY_DOMAIN_NAME = "domain name can not be empty. Please, choose a valid name and try again.";
    private final String MSG_INVALID_DOMAIN_NAME_PATTERN= "Total Length of domain_name can be upto 255 Characters. It can contain only letters, number and hyphen - sign.. Please, choose a valid name and try again.";
    private final String MSG_DOMAIN_NAME_NOT_AVAILABLE = "This domain name is not available. Please, choose a different name and try again.";
    private final String SUB_DOMAIN_NAME_VALIDATION_PATTERN="[A-Za-z0-9\\-]{1,255}";
    private Pattern pattern;
    private ProfileService profileService;
    private ClientPasswordHashingService clientPasswordHashingService;

    private ClientsDao clientsDao;

    private OrganizationListConverter listConverter;

    public OrganizationsEndpoint() {
        super();
    }


    @PostConstruct
    void init() {
        configuration = (Configuration) context.getAttribute(Configuration.class.getName());
        super.init(configuration.subset("runtime-settings"));

        registerConverters();

        final DaoManager storage = (DaoManager) context.getAttribute(DaoManager.class.getName());
        clientsDao = storage.getClientsDao();

        // Make sure there is an authenticated account present when this endpoint is used
        // get manager from context or create it if it does not exist
        try {
            dnsProvisioningManager = new DnsProvisioningManagerProvider(configuration.subset("runtime-settings"), context).get();
        } catch(Exception e) {
            logger.error("Unable to get dnsProvisioningManager", e);
        }
        pattern = Pattern.compile(SUB_DOMAIN_NAME_VALIDATION_PATTERN);
        profileService = (ProfileService)context.getAttribute(ProfileService.class.getName());
        clientPasswordHashingService = (ClientPasswordHashingService) context.getAttribute(ClientPasswordHashingService.class.getName());
    }

    private void registerConverters(){
        final OrganizationConverter converter = new OrganizationConverter(configuration);
        listConverter = new OrganizationListConverter(configuration);

        final ClientConverter clientConverter = new ClientConverter(configuration);
        final ClientListConverter clientListConverter = new ClientListConverter(configuration);

        final GsonBuilder builder = new GsonBuilder();
        builder.serializeNulls();
        builder.registerTypeAdapter(Organization.class, converter);
        builder.registerTypeAdapter(Client.class, converter);
        builder.setPrettyPrinting();
        gson = builder.create();
        xstream = new XStream();
        xstream.alias("RestcommResponse", RestCommResponse.class);
        xstream.registerConverter(converter);
        xstream.registerConverter(listConverter);
        xstream.registerConverter(clientConverter);
        xstream.registerConverter(clientListConverter);
        xstream.registerConverter(new RestCommResponseConverter(configuration));
    }

    /**
     * @param organizationSid
     * @param responseType
     * @return
     */
    protected Response getOrganization(final String organizationSid,
            final MediaType responseType,
             UriInfo info,
             UserIdentityContext userIdentityContext) {
        //First check if the account has the required permissions in general, this way we can fail fast and avoid expensive DAO operations
        permissionEvaluator.checkPermission("RestComm:Read:Organizations",
                userIdentityContext);
        Organization organization = null;

        if (!Sid.pattern.matcher(organizationSid).matches()) {
            return status(BAD_REQUEST).build();
        } else {
            try {
                //if account is not super admin then allow to read only affiliated organization
                if (!permissionEvaluator.isSuperAdmin(userIdentityContext)) {
                    if (userIdentityContext.getEffectiveAccount().getOrganizationSid().equals(new Sid(organizationSid))) {
                        organization = organizationsDao.getOrganization(new Sid(organizationSid));
                    } else {
                        return status(FORBIDDEN).build();
                    }
                } else {
                    organization = organizationsDao.getOrganization(new Sid(organizationSid));
                }
            } catch (Exception e) {
                return status(NOT_FOUND).build();
            }
        }

        if (organization == null) {
            return status(NOT_FOUND).build();
        } else {
            Response.ResponseBuilder ok = Response.ok();
            Profile associatedProfile = profileService.retrieveEffectiveProfileByOrganizationSid(new Sid(organizationSid));
            if (associatedProfile != null) {
                LinkHeader profileLink = composeLink(new Sid(associatedProfile.getSid()), info);
                ok.header(ProfileEndpoint.LINK_HEADER, profileLink.toString());
            }
            if (APPLICATION_XML_TYPE.equals(responseType)) {
                final RestCommResponse response = new RestCommResponse(organization);
                return ok.type(APPLICATION_XML).entity(xstream.toXML(response)).build();
            } else if (APPLICATION_JSON_TYPE.equals(responseType)) {
                return ok.type(APPLICATION_JSON).entity(gson.toJson(organization)).build();
            } else {
                return null;
            }
        }
    }

    /**
     * @param info
     * @param responseType
     * @return
     */
    protected Response getOrganizations(UriInfo info, final MediaType responseType) {
        List<Organization> organizations = null;

        String status = info.getQueryParameters().getFirst("Status");

        if(status != null && Organization.Status.getValueOf(status.toLowerCase()) != null){
            organizations = organizationsDao.getOrganizationsByStatus(Organization.Status.getValueOf(status.toLowerCase()));
        }else{
            organizations = organizationsDao.getAllOrganizations();
        }
        if (organizations == null || organizations.isEmpty()) {
            return status(NOT_FOUND).build();
        }
        if (APPLICATION_XML_TYPE.equals(responseType)) {
            final RestCommResponse response = new RestCommResponse(new OrganizationList(organizations));
            return ok(xstream.toXML(response), APPLICATION_XML).build();
        } else if (APPLICATION_JSON_TYPE.equals(responseType)) {
            return ok(gson.toJson(organizations), APPLICATION_JSON).build();
        } else {
            return null;
        }
    }

    /**
     * putOrganization create new organization
     * @param domainName
     * @param info
     * @param responseType
     * @return
     */
    protected Response putOrganization(String domainName, final UriInfo info,
            MediaType responseType) {
        if(domainName == null){
            return status(BAD_REQUEST).entity(MSG_EMPTY_DOMAIN_NAME ).build();
        }else{

            //Character verification
            if(!pattern.matcher(domainName).matches()){
                return status(BAD_REQUEST).entity(MSG_INVALID_DOMAIN_NAME_PATTERN).build();
            }
            Organization organization;
            if(dnsProvisioningManager == null) {
                //Check if domain_name does not already taken inside restcomm by an organization.
                organization = organizationsDao.getOrganizationByDomainName(domainName);
                if(organization != null){
                    return status(CONFLICT)
                            .entity(MSG_DOMAIN_NAME_NOT_AVAILABLE)
                            .build();
                }
                logger.warn("No DNS provisioning Manager is configured, restcomm will not make any queries to DNS server.");
                organization = new Organization(Sid.generate(Sid.Type.ORGANIZATION), domainName, DateTime.now(), DateTime.now(), Organization.Status.ACTIVE);
                organizationsDao.addOrganization(organization);
            }else {
                //for example hosted zone id of domain restcomm.com or others. if not provided then default will be used as per configuration
                String hostedZoneId = info.getQueryParameters().getFirst("HostedZoneId");
                //Check if domain_name does not already taken inside restcomm by an organization.
                String completeDomainName = dnsProvisioningManager.getCompleteDomainName(domainName, hostedZoneId);
                organization = organizationsDao.getOrganizationByDomainName(completeDomainName);
                if(organization != null){
                    return status(CONFLICT)
                            .entity(MSG_DOMAIN_NAME_NOT_AVAILABLE)
                            .build();
                }
                //check if domain name already exists on dns side or not
                if(dnsProvisioningManager.doesResourceRecordAlreadyExists(domainName, hostedZoneId)){
                    return status(CONFLICT)
                            .entity(MSG_DOMAIN_NAME_NOT_AVAILABLE)
                            .build();
                }
                if(!dnsProvisioningManager.createResourceRecord(domainName, hostedZoneId)){
                    logger.error("could not create resource record on dns server");
                    return status(INTERNAL_SERVER_ERROR).build();
                }else{
                    organization = new Organization(Sid.generate(Sid.Type.ORGANIZATION), completeDomainName, DateTime.now(), DateTime.now(), Organization.Status.ACTIVE);
                    organizationsDao.addOrganization(organization);
                }
            }

            if (APPLICATION_XML_TYPE.equals(responseType)) {
                final RestCommResponse response = new RestCommResponse(organization);
                return ok(xstream.toXML(response), APPLICATION_XML).build();
            } else if (APPLICATION_JSON_TYPE.equals(responseType)) {
                return ok(gson.toJson(organization), APPLICATION_JSON).build();
            } else {
                return null;
            }
        }
    }

    /**
     * Hash password for clients of the given organization
     * @param organizationSid
     * @param info
     * @param responseType
     * @return Response with List<Client> for the Clients that hashed the password
     */
    protected Response migrateClientsOrganization(final String organizationSid, UriInfo info, MediaType responseType, UserIdentityContext userIdentityContext) {

        //First check if the account has the required permissions in general, this way we can fail fast and avoid expensive DAO operations
        permissionEvaluator.checkPermission("RestComm:Read:Organizations", userIdentityContext);
        Organization organization = null;

        if (!Sid.pattern.matcher(organizationSid).matches()) {
            return status(BAD_REQUEST).build();
        } else {
            try {
                if (!permissionEvaluator.isSuperAdmin(userIdentityContext)) {
                    return status(FORBIDDEN).build();
                } else {
                    organization = organizationsDao.getOrganization(new Sid(organizationSid));
                }
            } catch (Exception e) {
                return status(NOT_FOUND).build();
            }
        }

        if (organization == null) {
            return status(NOT_FOUND).build();
        } else {
            Response.ResponseBuilder ok = Response.ok();

            List<Client> clients = clientsDao.getClientsByOrg(organization.getSid());
            Map<String, String> migratedClients = clientPasswordHashingService.hashClientPassword(clients, organization.getDomainName());


            if (APPLICATION_XML_TYPE.equals(responseType)) {
                final RestCommResponse response = new RestCommResponse(migratedClients);
                return ok.type(APPLICATION_XML).entity(xstream.toXML(response)).build();
            } else if (APPLICATION_JSON_TYPE.equals(responseType)) {
                return ok.type(APPLICATION_JSON).entity(gson.toJson(migratedClients)).build();
            } else {
                return null;
            }
        }
    }

    public LinkHeader composeLink(Sid targetSid, UriInfo info) {
        String sid = targetSid.toString();
        URI uri = info.getBaseUriBuilder().path(ProfileEndpoint.class).path(sid).build();
        LinkHeader.LinkHeaderBuilder link = LinkHeader.uri(uri).parameter(TITLE_PARAM, "Profiles");
        return link.rel(PROFILE_REL_TYPE).build();
    }

    @Path("/{organizationSid}")
    @GET
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    @RolesAllowed({SUPER_ADMIN_ROLE, ADMIN_ROLE})
    public Response getOrganizationAsXml(@PathParam("organizationSid") final String organizationSid,
            @Context UriInfo info,
            @HeaderParam("Accept") String accept,
            @Context SecurityContext sec) {
        return getOrganization(organizationSid,
                retrieveMediaType(accept),
                info,
                ContextUtil.convert(sec));
    }

    @GET
    @RolesAllowed(SUPER_ADMIN_ROLE)
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public Response getOrganizations(@Context UriInfo info,
            @HeaderParam("Accept") String accept) {
        return getOrganizations(info, retrieveMediaType(accept));
    }

    @Path("/{domainName}")
    @PUT
    @RolesAllowed(SUPER_ADMIN_ROLE)
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public Response putOrganizationPut(@PathParam("domainName") final String domainName,
            @Context UriInfo info,
            @HeaderParam("Accept") String accept) {
        return putOrganization(domainName, info, retrieveMediaType(accept));
    }

    @Path("/{organizationSid}/Migrate")
    @PUT
    @RolesAllowed(SUPER_ADMIN_ROLE)
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public Response migrateClientsOrganizationPut(@PathParam("organizationSid") final String organizationSid,
                                           @Context UriInfo info,
                                           @HeaderParam("Accept") String accept, @Context SecurityContext sec) {
        return migrateClientsOrganization(organizationSid, info, retrieveMediaType(accept), ContextUtil.convert(sec));
    }
}
