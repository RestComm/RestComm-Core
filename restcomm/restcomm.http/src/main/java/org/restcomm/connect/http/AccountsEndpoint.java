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
import com.sun.jersey.core.util.MultivaluedMapImpl;
import com.sun.jersey.spi.resource.Singleton;
import com.thoughtworks.xstream.XStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.PostConstruct;
import javax.annotation.security.RolesAllowed;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.servlet.ServletContext;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import static javax.ws.rs.core.MediaType.APPLICATION_FORM_URLENCODED;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON_TYPE;
import static javax.ws.rs.core.MediaType.APPLICATION_XML;
import static javax.ws.rs.core.MediaType.APPLICATION_XML_TYPE;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import static javax.ws.rs.core.Response.Status.BAD_REQUEST;
import static javax.ws.rs.core.Response.Status.CONFLICT;
import static javax.ws.rs.core.Response.Status.NOT_FOUND;
import static javax.ws.rs.core.Response.Status.PRECONDITION_FAILED;
import static javax.ws.rs.core.Response.ok;
import static javax.ws.rs.core.Response.status;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;
import org.apache.commons.configuration.Configuration;
import org.apache.shiro.crypto.hash.Md5Hash;
import org.joda.time.DateTime;
import org.restcomm.connect.commons.annotations.concurrency.ThreadSafe;
import org.restcomm.connect.commons.configuration.RestcommConfiguration;
import org.restcomm.connect.commons.configuration.sets.RcmlserverConfigurationSet;
import org.restcomm.connect.commons.dao.Sid;
import org.restcomm.connect.commons.util.ClientLoginConstrains;
import org.restcomm.connect.core.service.api.ProfileService;
import org.restcomm.connect.dao.ClientsDao;
import org.restcomm.connect.dao.DaoManager;
import org.restcomm.connect.dao.IncomingPhoneNumbersDao;
import org.restcomm.connect.dao.ProfileAssociationsDao;
import org.restcomm.connect.dao.entities.Account;
import org.restcomm.connect.dao.entities.AccountList;
import org.restcomm.connect.dao.entities.Client;
import org.restcomm.connect.dao.entities.IncomingPhoneNumber;
import org.restcomm.connect.dao.entities.Organization;
import org.restcomm.connect.dao.entities.Profile;
import org.restcomm.connect.dao.entities.RestCommResponse;
import org.restcomm.connect.extension.api.ApiRequest;
import org.restcomm.connect.extension.controller.ExtensionController;
import static org.restcomm.connect.http.ProfileEndpoint.PROFILE_REL_TYPE;
import static org.restcomm.connect.http.ProfileEndpoint.TITLE_PARAM;
import org.restcomm.connect.http.client.rcmlserver.RcmlserverApi;
import org.restcomm.connect.http.client.rcmlserver.RcmlserverNotifications;
import org.restcomm.connect.http.converter.AccountConverter;
import org.restcomm.connect.http.converter.AccountListConverter;
import org.restcomm.connect.http.converter.RestCommResponseConverter;
import org.restcomm.connect.http.exceptionmappers.CustomReasonPhraseType;
import org.restcomm.connect.http.exceptions.InsufficientPermission;
import org.restcomm.connect.http.exceptions.PasswordTooWeak;
import static org.restcomm.connect.http.security.AccountPrincipal.SUPER_ADMIN_ROLE;
import org.restcomm.connect.http.security.ContextUtil;
import org.restcomm.connect.http.security.PermissionEvaluator.SecuredType;
import org.restcomm.connect.identity.UserIdentityContext;
import org.restcomm.connect.identity.passwords.PasswordValidator;
import org.restcomm.connect.identity.passwords.PasswordValidatorFactory;
import org.restcomm.connect.provisioning.number.api.PhoneNumberProvisioningManager;
import org.restcomm.connect.provisioning.number.api.PhoneNumberProvisioningManagerProvider;

/**
 * @author quintana.thomas@gmail.com (Thomas Quintana)
 * @author maria-farooq@live.com (Maria Farooq)
 */
@Path("/Accounts")
@ThreadSafe
@Singleton
public class AccountsEndpoint extends AbstractEndpoint {
    private Configuration runtimeConfiguration;
    private Configuration rootConfiguration; // top-level configuration element
    private Gson gson;
    private XStream xstream;
    private ClientsDao clientDao;
    private ProfileAssociationsDao profileAssociationsDao;
    private ProfileService profileService;




    public AccountsEndpoint() {
        super();
    }

    public AccountsEndpoint(ServletContext context) {
        super(context);
    }

    @PostConstruct
    void init() {
        rootConfiguration = (Configuration) context.getAttribute(Configuration.class.getName());
        runtimeConfiguration = rootConfiguration.subset("runtime-settings");
        super.init(runtimeConfiguration);
        final DaoManager storage = (DaoManager) context.getAttribute(DaoManager.class.getName());
        clientDao = storage.getClientsDao();
        profileAssociationsDao = storage.getProfileAssociationsDao();
        profileService = (ProfileService)context.getAttribute(ProfileService.class.getName());
        final AccountConverter converter = new AccountConverter(runtimeConfiguration);
        final GsonBuilder builder = new GsonBuilder();
        builder.registerTypeAdapter(Account.class, converter);
        builder.setPrettyPrinting();
        gson = builder.create();
        xstream = new XStream();
        xstream.alias("RestcommResponse", RestCommResponse.class);
        xstream.registerConverter(converter);
        xstream.registerConverter(new AccountListConverter(runtimeConfiguration));
        xstream.registerConverter(new RestCommResponseConverter(runtimeConfiguration));
        // Make sure there is an authenticated account present when this endpoint is used
    }

    private Account createFrom(final Sid accountSid,
            final MultivaluedMap<String, String> data,
            Account parent,
            UserIdentityContext userIdentityContext) throws PasswordTooWeak {
        validate(data);

        final DateTime now = DateTime.now();
        final String emailAddress = (data.getFirst("EmailAddress")).toLowerCase();

        // Issue 108: https://bitbucket.org/telestax/telscale-restcomm/issue/108/account-sid-could-be-a-hash-of-the
        final Sid sid = Sid.generate(Sid.Type.ACCOUNT, emailAddress);
        Sid organizationSid=null;

        String friendlyName = emailAddress;
        if (data.containsKey("FriendlyName")) {
            friendlyName = data.getFirst("FriendlyName");
        }
        final Account.Type type = Account.Type.FULL;
        Account.Status status = Account.Status.ACTIVE;
        if (data.containsKey("Status")) {
            status = Account.Status.getValueOf(data.getFirst("Status").toLowerCase());
        }
        if (data.containsKey("OrganizationSid")) {
            Sid orgSid = new Sid(data.getFirst("OrganizationSid"));
            // user can add account in same organization
            if(!orgSid.equals(parent.getOrganizationSid())){
                //only super admin can add account in organizations other than it belongs to
                permissionEvaluator.allowOnlySuperAdmin(userIdentityContext);
                if(organizationsDao.getOrganization(orgSid) == null){
                    throw new IllegalArgumentException("provided OrganizationSid does not exist");
                }
                organizationSid = orgSid;
            }
        }
        organizationSid = organizationSid != null ? organizationSid : parent.getOrganizationSid();
        final String password = data.getFirst("Password");
        PasswordValidator validator = PasswordValidatorFactory.createDefault();
        if (!validator.isStrongEnough(password))
            throw new PasswordTooWeak();
        final String authToken = new Md5Hash(password).toString();
        final String role = data.getFirst("Role");
        final StringBuilder buffer = new StringBuilder();
        buffer.append("/").append(getApiVersion(null)).append("/Accounts/").append(sid.toString());
        final URI uri = URI.create(buffer.toString());
        return new Account(sid, now, now, emailAddress, friendlyName, accountSid, type, status, authToken, role, uri, organizationSid);
    }

    protected Response getAccount(final String accountSid, final MediaType responseType,
            UriInfo info,
            UserIdentityContext userIdentityContext) {
        //First check if the account has the required permissions in general, this way we can fail fast and avoid expensive DAO operations
        Account account = null;
        permissionEvaluator.checkPermission("RestComm:Read:Accounts",userIdentityContext);
        if (Sid.pattern.matcher(accountSid).matches()) {
            try {
                account = accountsDao.getAccount(new Sid(accountSid));
            } catch (Exception e) {
                return status(NOT_FOUND).build();
            }
        } else {
            try {
                account = accountsDao.getAccount(accountSid);
            } catch (Exception e) {
                return status(NOT_FOUND).build();
            }
        }

        permissionEvaluator.secure(account, "RestComm:Read:Accounts", SecuredType.SECURED_ACCOUNT,userIdentityContext );

        if (account == null) {
            return status(NOT_FOUND).build();
        } else {
            Response.ResponseBuilder ok = Response.ok();
            Profile associatedProfile = profileService.retrieveEffectiveProfileByAccountSid(account.getSid());
            if (associatedProfile != null) {
                LinkHeader profileLink = composeLink(new Sid(associatedProfile.getSid()), info);
                ok.header(ProfileEndpoint.LINK_HEADER, profileLink.toString());
            }
            if (APPLICATION_XML_TYPE.equals(responseType)) {
                final RestCommResponse response = new RestCommResponse(account);
                return ok.type(APPLICATION_XML).entity(xstream.toXML(response)).build();
            } else if (APPLICATION_JSON_TYPE.equals(responseType)) {
                return ok.type(APPLICATION_JSON).entity(gson.toJson(account)).build();
            } else {
                return null;
            }
        }
    }

    // Account removal disabled as per https://github.com/RestComm/Restcomm-Connect/issues/1270
    /*
    protected Response deleteAccount(final String operatedSid) {
        //First check if the account has the required permissions in general, this way we can fail fast and avoid expensive DAO operations
        checkPermission("RestComm:Delete:Accounts");
        // what if effectiveAccount is null ?? - no need to check since we checkAuthenticatedAccount() in AccountsEndoint.init()
        final Sid accountSid = userIdentityContext.getEffectiveAccount().getSid();
        final Sid sidToBeRemoved = new Sid(operatedSid);

        Account removedAccount = accountsDao.getAccount(sidToBeRemoved);
        secure(removedAccount, "RestComm:Delete:Accounts", SecuredType.SECURED_ACCOUNT);
        // Prevent removal of Administrator account
        if (operatedSid.equalsIgnoreCase(accountSid.toString()))
            return status(BAD_REQUEST).build();

        if (accountsDao.getAccount(sidToBeRemoved) == null)
            return status(NOT_FOUND).build();

        // the whole tree of sub-accounts has to be removed as well
        List<String> removedAccounts = accountsDao.getSubAccountSidsRecursive(sidToBeRemoved);
        if (removedAccounts != null && !removedAccounts.isEmpty()) {
            int i = removedAccounts.size(); // is is the count of accounts left to process
            while (i > 0) {
                i --;
                String removedSid = removedAccounts.get(i);
                try {
                    removeSingleAccount(removedSid);
                } catch (Exception e) {
                    // if anything bad happens, log the error and continue removing the rest of the accounts.
                    logger.error("Failed removing (child) account '" + removedSid + "'");
                }
            }
        }
        // remove the parent account too
        removeSingleAccount(operatedSid);

        return ok().build();
    }*/

    /*
    protected Response deleteAccount(final String operatedSid) {
        //First check if the account has the required permissions in general, this way we can fail fast and avoid expensive DAO operations
        checkPermission("RestComm:Delete:Accounts");
        // what if effectiveAccount is null ?? - no need to check since we checkAuthenticatedAccount() in AccountsEndoint.init()
        final Sid accountSid = userIdentityContext.getEffectiveAccount().getSid();
        final Sid sidToBeRemoved = new Sid(operatedSid);

        Account removedAccount = accountsDao.getAccount(sidToBeRemoved);
        secure(removedAccount, "RestComm:Delete:Accounts", SecuredType.SECURED_ACCOUNT);
        // Prevent removal of Administrator account
        if (operatedSid.equalsIgnoreCase(accountSid.toString()))
            return status(BAD_REQUEST).build();

        if (accountsDao.getAccount(sidToBeRemoved) == null)
            return status(NOT_FOUND).build();

        accountsDao.removeAccount(sidToBeRemoved);

        // Remove its SIP client account
        clientDao.removeClients(sidToBeRemoved);

        return ok().build();
    }
    */
    /**
     * Removes all dependent resources of an account. Some resources like
     * CDRs are excluded.
     *
     * @param sid
     */
    private void removeAccoundDependencies(Sid sid) {
        logger.debug("removing accoutn dependencies");
        DaoManager daoManager = (DaoManager) context.getAttribute(DaoManager.class.getName());
        // remove dependency entities first and dependent entities last. Also, do safer operation first (as a secondary rule)
        daoManager.getAnnouncementsDao().removeAnnouncements(sid);
        daoManager.getNotificationsDao().removeNotifications(sid);
        daoManager.getShortCodesDao().removeShortCodes(sid);
        daoManager.getOutgoingCallerIdsDao().removeOutgoingCallerIds(sid);
        daoManager.getTranscriptionsDao().removeTranscriptions(sid);
        daoManager.getRecordingsDao().removeRecordings(sid);
        daoManager.getApplicationsDao().removeApplications(sid);
        removeIncomingPhoneNumbers(sid,daoManager.getIncomingPhoneNumbersDao());
        daoManager.getClientsDao().removeClients(sid);
        profileAssociationsDao.deleteProfileAssociationByTargetSid(sid.toString());
    }

    /**
     * Removes incoming phone numbers that belong to an account from the database.
     * For provided numbers the provider is also contacted to get them released.
     *
     * @param accountSid
     * @param dao
     */
    private void removeIncomingPhoneNumbers(Sid accountSid, IncomingPhoneNumbersDao dao) {
        List<IncomingPhoneNumber> numbers = dao.getIncomingPhoneNumbers(accountSid);
        if (numbers != null && numbers.size() > 0) {
            // manager is retrieved in a lazy way. If any number needs it, it will be first retrieved
            // from the servlet context. If not there it will be created, stored in context and returned.
            boolean managerQueried = false;
            PhoneNumberProvisioningManager manager = null;
            for (IncomingPhoneNumber number : numbers) {
                // if this is not just a SIP number try to release it by contacting the provider
                if (number.isPureSip() == null || !number.isPureSip()) {
                    if ( ! managerQueried )
                        manager = new PhoneNumberProvisioningManagerProvider(rootConfiguration,context).get(); // try to retrieve/build manager only once
                    if (manager != null) {
                        try {
                            if  (! manager.cancelNumber(IncomingPhoneNumbersEndpoint.convertIncomingPhoneNumbertoPhoneNumber(number)) ) {
                                logger.error("Number cancelation failed for provided number '" + number.getPhoneNumber()+"'. Number entity " + number.getSid() + " will stay in database");
                            } else {
                                dao.removeIncomingPhoneNumber(number.getSid());
                            }
                        } catch (Exception e) {
                            logger.error("Number cancelation failed for provided number '" + number.getPhoneNumber()+"'",e);
                        }
                    }
                    else
                        logger.error("Number cancelation failed for provided number '" + number.getPhoneNumber()+"'. Provisioning Manager was null. "+"Number entity " + number.getSid() + " will stay in database");
                } else {
                    // pureSIP numbers only to be removed from database. No need to contact provider
                    dao.removeIncomingPhoneNumber(number.getSid());
                }
            }
        }
    }



    protected Response getAccounts(final UriInfo info, final MediaType responseType,
            UserIdentityContext userIdentityContext) {
        //First check if the account has the required permissions in general, this way we can fail fast and avoid expensive DAO operations
        permissionEvaluator.checkPermission("RestComm:Read:Accounts",userIdentityContext);
        final Account account = userIdentityContext.getEffectiveAccount();
        if (account == null) {
            return status(NOT_FOUND).build();
        } else {
            final List<Account> accounts = new ArrayList<Account>();

            if (info == null) {
                accounts.addAll(accountsDao.getChildAccounts(account.getSid()));
            } else {
                String organizationSid = info.getQueryParameters().getFirst("OrganizationSid");
                String domainName = info.getQueryParameters().getFirst("DomainName");

                if(organizationSid != null && !(organizationSid.trim().isEmpty())){
                    permissionEvaluator.allowOnlySuperAdmin(userIdentityContext);
                    accounts.addAll(accountsDao.getAccountsByOrganization(new Sid(organizationSid)));
                } else if(domainName != null && !(domainName.trim().isEmpty())){
                    permissionEvaluator.allowOnlySuperAdmin(userIdentityContext);
                    Organization organization = organizationsDao.getOrganizationByDomainName(domainName);
                    if(organization == null){
                        return status(NOT_FOUND).build();
                    }
                    accounts.addAll(accountsDao.getAccountsByOrganization(organization.getSid()));
                } else {
                    accounts.addAll(accountsDao.getChildAccounts(account.getSid()));
                }
            }

            if (APPLICATION_XML_TYPE.equals(responseType)) {
                final RestCommResponse response = new RestCommResponse(new AccountList(accounts));
                return ok(xstream.toXML(response), APPLICATION_XML).build();
            } else if (APPLICATION_JSON_TYPE.equals(responseType)) {
                return ok(gson.toJson(accounts), APPLICATION_JSON).build();
            } else {
                return null;
            }
        }
    }

    protected Response putAccount(final MultivaluedMap<String, String> data,
            final MediaType responseType,UserIdentityContext userIdentityContext) {
        //First check if the account has the required permissions in general, this way we can fail fast and avoid expensive DAO operations
        permissionEvaluator.checkPermission("RestComm:Create:Accounts",userIdentityContext);
        // check account level depth. If we're already at third level no sub-accounts are allowed to be created
        List<String> accountLineage = userIdentityContext.getEffectiveAccountLineage();
        if (accountLineage.size() >= 2) {
            // there are already 2+1=3 account levels. Sub-accounts at 4th level are not allowed
            return status(BAD_REQUEST).entity(buildErrorResponseBody("This account is not allowed to have sub-accounts",responseType)).type(responseType).build();
        }

        // what if effectiveAccount is null ?? - no need to check since we checkAuthenticatedAccount() in AccountsEndoint.init()
        final Sid sid = userIdentityContext.getEffectiveAccount().getSid();

        ExtensionController ec = ExtensionController.getInstance();
        ApiRequest apiRequest = new ApiRequest(sid.toString(), data, ApiRequest.Type.CREATE_SUBACCOUNT);

        if (executePreApiAction(apiRequest)) {
            final Account parent = accountsDao.getAccount(sid);
            Account account = null;
            try {
                account = createFrom(sid, data, parent,userIdentityContext);
            } catch (IllegalArgumentException  illegalArgumentException) {
                return status(BAD_REQUEST).entity(illegalArgumentException.getMessage()).build();
            }catch (final NullPointerException exception) {
                return status(BAD_REQUEST).entity(exception.getMessage()).build();
            } catch (PasswordTooWeak passwordTooWeak) {
                return status(BAD_REQUEST).entity(buildErrorResponseBody("Password too weak",responseType)).type(responseType).build();
            }

            // If Account already exists don't add it again
        /*
            Account creation rules:
            - either be Administrator or have the following permission: RestComm:Create:Accounts
            - only Administrators can choose a role for newly created accounts. Normal users will create accounts with the same role as their own.
         */
            if (accountsDao.getAccount(account.getSid()) == null && !account.getEmailAddress().equalsIgnoreCase("administrator@company.com")) {
                if (parent.getStatus().equals(Account.Status.ACTIVE) &&
                        permissionEvaluator.isSecuredByPermission("RestComm:Create:Accounts",userIdentityContext)) {
                    if (!permissionEvaluator.hasAccountRole(permissionEvaluator.getAdministratorRole(),userIdentityContext) || !data.containsKey("Role")) {
                        account = account.setRole(parent.getRole());
                    }
                    accountsDao.addAccount(account);

                    // Create default SIP client data
                    MultivaluedMap<String, String> clientData = new MultivaluedMapImpl();
                    String username = data.getFirst("EmailAddress").split("@")[0];
                    clientData.add("Login", username);
                    clientData.add("Password", data.getFirst("Password"));
                    clientData.add("FriendlyName", account.getFriendlyName());
                    clientData.add("AccountSid", account.getSid().toString());
                    Client client = clientDao.getClient(clientData.getFirst("Login"), account.getOrganizationSid());
                    if (client == null) {
                        client = createClientFrom(account.getSid(), clientData);
                        clientDao.addClient(client);
                    }
                } else {
                    throw new InsufficientPermission();
                }
            } else {
                return status(CONFLICT).entity("The email address used for the new account is already in use.").build();
            }

            executePostApiAction(apiRequest);

            if (APPLICATION_JSON_TYPE.equals(responseType)) {
                return ok(gson.toJson(account), APPLICATION_JSON).build();
            } else if (APPLICATION_XML_TYPE.equals(responseType)) {
                final RestCommResponse response = new RestCommResponse(account);
                return ok(xstream.toXML(response), APPLICATION_XML).build();
            } else {
                return null;
            }
        } else {
            if (logger.isDebugEnabled()) {
                final String errMsg = "Creation of sub-accounts is not Allowed";
                logger.debug(errMsg);
            }
            executePostApiAction(apiRequest);
            String errMsg = "Creation of sub-accounts is not Allowed";
            return status(Response.Status.FORBIDDEN).entity(errMsg).build();
        }
    }

    private Client createClientFrom(final Sid accountSid, final MultivaluedMap<String, String> data) {
        final Client.Builder builder = Client.builder();
        final Sid sid = Sid.generate(Sid.Type.CLIENT);

        // TODO: need to encrypt this password because it's same with Account
        // password.
        // Don't implement now. Opened another issue for it.
        // String password = new Md5Hash(data.getFirst("Password")).toString();
        String password = data.getFirst("Password");

        builder.setSid(sid);
        builder.setAccountSid(accountSid);
        builder.setApiVersion(getApiVersion(data));
        builder.setLogin(data.getFirst("Login"));
        builder.setPassword(data.getFirst("Login"), password, organizationsDao.getOrganization(accountsDao.getAccount(accountSid).getOrganizationSid()).getDomainName(), "");
        builder.setFriendlyName(data.getFirst("FriendlyName"));
        builder.setStatus(Client.ENABLED);
        final StringBuilder buffer = new StringBuilder();
        buffer.append("/").append(getApiVersion(data)).append("/Accounts/").append(accountSid.toString())
                .append("/Clients/").append(sid.toString());
        builder.setUri(URI.create(buffer.toString()));
        return builder.build();
    }

    /**
     * Fills an account entity object with values supplied from an http request
     *
     * @param account
     * @param data
     * @return a new instance with given account,and overriden fields from data
     */
    private Account prepareAccountForUpdate(final Account account,
            final MultivaluedMap<String, String> data,
            UserIdentityContext userIdentityContext) {
        Account.Builder accBuilder = Account.builder();
        //copy full incoming account, and let override happen
        //in a separate instance later
        accBuilder.copy(account);


        if (data.containsKey("Status")) {
            Account.Status newStatus = Account.Status.getValueOf(data.getFirst("Status").toLowerCase());
            accBuilder.setStatus(newStatus);
        }
        if (data.containsKey("FriendlyName")) {
            accBuilder.setFriendlyName(data.getFirst("FriendlyName"));
        }
        if (data.containsKey("Password")) {
            // if this is a reset-password operation, we also need to set the account status to active
            if (account.getStatus() == Account.Status.UNINITIALIZED) {
                accBuilder.setStatus(Account.Status.ACTIVE);
            }

            String password = data.getFirst("Password");
            PasswordValidator validator = PasswordValidatorFactory.createDefault();
            if (!validator.isStrongEnough(password)) {
                CustomReasonPhraseType stat = new CustomReasonPhraseType(Response.Status.BAD_REQUEST, "Password too weak");
                throw new WebApplicationException(status(stat).build());
            }
            final String hash = new Md5Hash(data.getFirst("Password")).toString();
            accBuilder.setAuthToken(hash);
        }

        if (data.containsKey("Role")) {
            // Only allow role change for administrators. Multitenancy checks will take care of restricting the modification scope to sub-accounts.
            if (userIdentityContext.getEffectiveAccountRoles().contains(permissionEvaluator.getAdministratorRole())) {
                accBuilder.setRole(data.getFirst("Role"));
            } else {
                CustomReasonPhraseType stat = new CustomReasonPhraseType(Response.Status.FORBIDDEN, "Only Administrator allowed");
                throw new WebApplicationException(status(stat).build());
            }
        }

        return accBuilder.build();
    }

    /**
     * update SIP client of the corresponding Account.Password and FriendlyName fields are synched.
     */
    private void updateLinkedClient(Account account, MultivaluedMap<String, String> data) {
        logger.debug("checking linked client");
        String email = account.getEmailAddress();
        if (email != null && !email.equals("")) {
            logger.debug("account email is valid");
            String username = email.split("@")[0];
            Client client = clientDao.getClient(username, account.getOrganizationSid());
            if (client != null) {
                logger.debug("client found");
                // TODO: need to encrypt this password because it's
                // same with Account password.
                // Don't implement now. Opened another issue for it.
                if (data.containsKey("Password")) {
                    // Md5Hash(data.getFirst("Password")).toString();
                    logger.debug("password changed");
                    String password = data.getFirst("Password");
                    client = client.setPassword(client.getLogin(), password, organizationsDao.getOrganization(account.getOrganizationSid()).getDomainName());
                }

                if (data.containsKey("FriendlyName")) {
                    logger.debug("friendlyname changed");
                    client = client.setFriendlyName(data.getFirst("FriendlyName"));
                }
                logger.debug("updating linked client");
                clientDao.updateClient(client);
            }
        }
    }

    protected Response updateAccount(final String identifier, final MultivaluedMap<String, String> data,
            final MediaType responseType,
            UserIdentityContext userIdentityContext) {
        // First check if the account has the required permissions in general, this way we can fail fast and avoid expensive DAO
        // operations
        permissionEvaluator.checkPermission("RestComm:Modify:Accounts",userIdentityContext);
        Account account = getOperatingAccount(identifier);

        if (account == null) {
            return status(NOT_FOUND).build();
        } else {
            // since the operated account exists, first thing to do is make sure we have access
            permissionEvaluator.secure(account, "RestComm:Modify:Accounts", SecuredType.SECURED_ACCOUNT,
                    userIdentityContext);

            // if the account is already CLOSED, no updates are allowed
            if (account.getStatus() == Account.Status.CLOSED) {
                // If the account is CLOSED, no updates are allowed. Return a BAD_REQUEST status code.
                CustomReasonPhraseType stat = new CustomReasonPhraseType(Response.Status.BAD_REQUEST, "Account is closed");
                throw new WebApplicationException(status(stat).build());
            }

            Account modifiedAccount;
            modifiedAccount = prepareAccountForUpdate(account, data, userIdentityContext);

            // we are modifying status
            if (modifiedAccount.getStatus() != null &&
                    account.getStatus() != modifiedAccount.getStatus()) {
                switchAccountStatusTree(modifiedAccount,userIdentityContext);
            }

            //update client only if friendlyname or password was changed
            if (data.containsKey("Password") ||
                data.containsKey("FriendlyName") )  {
                updateLinkedClient(account, data);
            }
            accountsDao.updateAccount(modifiedAccount);


            if (APPLICATION_JSON_TYPE.equals(responseType)) {
                return ok(gson.toJson(modifiedAccount), APPLICATION_JSON).build();
            } else if (APPLICATION_XML_TYPE.equals(responseType)) {
                final RestCommResponse response = new RestCommResponse(modifiedAccount);
                return ok(xstream.toXML(response), APPLICATION_XML).build();
            } else {
                return null;
            }
        }
    }

    private Account getOperatingAccount (String identifier) {
        Sid sid = null;
        Account account = null;
        try {
            sid = new Sid(identifier);
            account = accountsDao.getAccount(sid);
        } catch (Exception e) {
            if (logger.isDebugEnabled()) {
                logger.debug("Exception trying to get account using SID. Seems we have email as identifier");
            }
        }
        if (account == null) {
            account = accountsDao.getAccount(identifier);
        }
        return account;
    }

    private Organization getOrganization(final MultivaluedMap<String, String> data) {
        Organization organization = null;
        String organizationId = null;

        if (data.containsKey("Organization")) {
            organizationId = data.getFirst("Organization");
        } else {
            return null;
        }

        if (Sid.pattern.matcher(organizationId).matches()) {
            //Attempt to get Organization by SID
            organization = organizationsDao.getOrganization(new Sid(organizationId));
            return organization;
        } else {
            //Attempt to get Organization by domain name
            organization = organizationsDao.getOrganizationByDomainName(organizationId);
            return organization;
        }
    }

    protected Response migrateAccountOrganization(final String identifier,
            final MultivaluedMap<String, String> data,
                                              final MediaType responseType,
                                              UserIdentityContext userIdentityContext) {

        Organization organization = getOrganization(data);
        //Validation 2 - Check if data contains Organization (either SID or domain name)
        if (organization == null) {
            return status(PRECONDITION_FAILED).entity("Missing Organization SID or Domain Name").build();
        }

        Account operatingAccount = getOperatingAccount(identifier);

        //Validation 3 - Operating Account shouldn't be null;
        if (operatingAccount == null) {
            return status(NOT_FOUND).build();
        }

        //Validation 4 - Only direct child of super admin account can be migrated to a new organization
        if (!permissionEvaluator.isDirectChildOfAccount(userIdentityContext.getEffectiveAccount(), operatingAccount)) {
            return status(BAD_REQUEST).build();
        }

        //Validation 5 - Check if Account already in the requested Organization
        if (operatingAccount.getOrganizationSid().equals(organization.getSid())) {
            return status(BAD_REQUEST).entity("Account already in the requested Organization").build();
        }

        //Update Account for the new Organization
        Account modifiedAccount = operatingAccount.setOrganizationSid(organization.getSid());
        accountsDao.updateAccount(modifiedAccount);

        if (logger.isDebugEnabled()) {
            String msg = String.format("Parent Account %s migrated to Organization %s", modifiedAccount.getSid(), organization.getSid());
            logger.debug(msg);
        }

        //Update Child accounts and their numbers
        List<Account> childAccounts = accountsDao.getChildAccounts(operatingAccount.getSid());
        for (Account child : childAccounts) {
            if (!child.getOrganizationSid().equals(organization.getSid())) {
                Account modifiedChildAccount = child.setOrganizationSid(organization.getSid());
                accountsDao.updateAccount(modifiedChildAccount);
                if (logger.isDebugEnabled()) {
                    String msg = String.format("Child Account %s from Parent Account %s, migrated to Organization %s", modifiedChildAccount.getSid(), modifiedAccount.getSid(), organization.getSid());
                    logger.debug(msg);
                }
            }
        }

        if (APPLICATION_JSON_TYPE.equals(responseType)) {
            return ok(gson.toJson(modifiedAccount), APPLICATION_JSON).build();
        } else if (APPLICATION_XML_TYPE.equals(responseType)) {
            final RestCommResponse response = new RestCommResponse(modifiedAccount);
            return ok(xstream.toXML(response), APPLICATION_XML).build();
        } else {
            return null;
        }
    }

    private void sendRVDStatusNotification(Account updatedAccount,
            UserIdentityContext userIdentityContext) {
        logger.debug("sendRVDStatusNotification");
        // set rcmlserverApi in case we need to also notify the application sever (RVD)
        RestcommConfiguration rcommConfiguration = RestcommConfiguration.getInstance();
        RcmlserverConfigurationSet config = rcommConfiguration.getRcmlserver();
        if (config != null && config.getNotify()) {
            logger.debug("notification enabled");
            // first send account removal notification to RVD now that the applications of the account still exist
            RcmlserverApi rcmlServerApi = new RcmlserverApi(rcommConfiguration.getMain(), rcommConfiguration.getRcmlserver());
            RcmlserverNotifications notifications = new RcmlserverNotifications();
            notifications.add(rcmlServerApi.buildAccountStatusNotification(updatedAccount));
            Account notifier = userIdentityContext.getEffectiveAccount();
            rcmlServerApi.transmitNotifications(notifications, notifier.getSid().toString(), notifier.getAuthToken());
        }
    }


    /**
     * Switches an account status at dao level.
     *
     * If status is CLSOED, Removes all resources belonging to an account.
     *
     * If rcmlServerApi is not null it will
     * also send account-removal notifications to the rcmlserver
     *
     * @param account
     */
    private void switchAccountStatus(Account account, Account.Status status,
            UserIdentityContext userIdentityContext) {
        if (logger.isDebugEnabled()) {
            logger.debug("Switching status for account:" + account.getSid() + ",status:" + status);
        }
        switch (status) {
            case CLOSED:
                sendRVDStatusNotification(account,userIdentityContext);
                // then proceed to dependency removal
                removeAccoundDependencies(account.getSid());
                break;
            default:
                break;

        }
        // finally, set and persist account status
        account = account.setStatus(status);
        accountsDao.updateAccount(account);
    }

    /**
     * Switches status of account along with all its children (the whole tree).
     *
     * @param parentAccount
     */
    private void switchAccountStatusTree(Account parentAccount,
            UserIdentityContext userIdentityContext) {
        logger.debug("Status transition requested");
        // transition child accounts
        List<String> subAccountsToSwitch = accountsDao.getSubAccountSidsRecursive(parentAccount.getSid());
        if (subAccountsToSwitch != null && !subAccountsToSwitch.isEmpty()) {
            int i = subAccountsToSwitch.size(); // is is the count of accounts left to process
            // we iterate backwards to handle child accounts first, parent accounts next
            while (i > 0) {
                i --;
                String removedSid = subAccountsToSwitch.get(i);
                try {
                    Account subAccount = accountsDao.getAccount(new Sid(removedSid));
                    switchAccountStatus(subAccount, parentAccount.getStatus(), userIdentityContext);
                } catch (Exception e) {
                    // if anything bad happens, log the error and continue removing the rest of the accounts.
                    logger.error("Failed switching status (child) account '" + removedSid + "'");
                }
            }
        }
        // switch parent account too
        switchAccountStatus(parentAccount, parentAccount.getStatus(), userIdentityContext);
    }

    private void validate(final MultivaluedMap<String, String> data) throws NullPointerException {
        if (!data.containsKey("EmailAddress")) {
            throw new NullPointerException("Email address can not be null.");
        } else if (!data.containsKey("Password")) {
            throw new NullPointerException("Password can not be null.");
        }

        String emailAddress = data.getFirst("EmailAddress");
        try {
            InternetAddress emailAddr = new InternetAddress(emailAddress);
            emailAddr.validate();
        } catch (AddressException ex) {
            String msg = String.format("Provided email address %s is not valid",emailAddress);
            if (logger.isDebugEnabled()) {
                logger.debug(msg);
            }
            throw new IllegalArgumentException(msg);
        }

        String clientLogin = data.getFirst("EmailAddress").split("@")[0];
        if (!ClientLoginConstrains.isValidClientLogin(clientLogin)) {
            String msg = String.format("Login %s contains invalid character(s) ",clientLogin);
            if (logger.isDebugEnabled()) {
                logger.debug(msg);
            }
            throw new IllegalArgumentException(msg);
        }
    }

    public LinkHeader composeLink(Sid targetSid, UriInfo info) {
        String sid = targetSid.toString();
        URI uri = info.getBaseUriBuilder().path(ProfileEndpoint.class).path(sid).build();
        LinkHeader.LinkHeaderBuilder link = LinkHeader.uri(uri).parameter(TITLE_PARAM, "Profiles");
        return link.rel(PROFILE_REL_TYPE).build();
    }

    @Path("/{accountSid}")
    @GET
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public Response getAccountAsXml(@PathParam("accountSid") final String accountSid,
            @Context UriInfo info,
            @HeaderParam("Accept") String accept,
            @Context SecurityContext sec) {
        return getAccount(accountSid, retrieveMediaType(accept), info, ContextUtil.convert(sec));
    }

    @GET
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public Response getAccounts(@Context UriInfo info,
            @HeaderParam("Accept") String accept,
            @Context SecurityContext sec) {
        return getAccounts(info, retrieveMediaType(accept), ContextUtil.convert(sec));
    }

    @Consumes(APPLICATION_FORM_URLENCODED)
    @POST
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public Response putAccount(final MultivaluedMap<String, String> data,
            @HeaderParam("Accept") String accept,
            @Context SecurityContext sec) {
        return putAccount(data, retrieveMediaType(accept), ContextUtil.convert(sec));
    }

    //The {accountSid} could be the email address of the account we need to update. Later we check if this is SID or EMAIL
    @Path("/{accountSid}")
    @Consumes(APPLICATION_FORM_URLENCODED)
    @POST
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public Response updateAccountAsXmlPost(@PathParam("accountSid") final String accountSid,
            final MultivaluedMap<String, String> data,
            @HeaderParam("Accept") String accept,
            @Context SecurityContext sec) {
        return updateAccount(accountSid, data, retrieveMediaType(accept), ContextUtil.convert(sec));
    }

    //The {accountSid} could be the email address of the account we need to update. Later we check if this is SID or EMAIL
    @Path("/{accountSid}")
    @Consumes(APPLICATION_FORM_URLENCODED)
    @PUT
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public Response updateAccountAsXmlPut(@PathParam("accountSid") final String accountSid,
            final MultivaluedMap<String, String> data,
            @HeaderParam("Accept") String accept,
            @Context SecurityContext sec) {
        return updateAccount(accountSid, data, retrieveMediaType(accept), ContextUtil.convert(sec));
    }

    @Path("/migrate/{accountSid}")
    @Consumes(APPLICATION_FORM_URLENCODED)
    @POST
    @RolesAllowed(SUPER_ADMIN_ROLE)
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public Response migrateAccount(@PathParam("accountSid") final String accountSid,
            final MultivaluedMap<String, String> data,
            @HeaderParam("Accept") String accept,
            @Context SecurityContext sec) {
        return migrateAccountOrganization(accountSid, data, retrieveMediaType(accept), ContextUtil.convert(sec));
    }

}
