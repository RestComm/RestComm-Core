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
package org.restcomm.connect.http.security;

import org.apache.commons.lang.NotImplementedException;
import org.apache.log4j.Logger;
import org.apache.shiro.authz.Permission;
import org.apache.shiro.authz.SimpleRole;
import org.apache.shiro.authz.permission.WildcardPermissionResolver;
import org.restcomm.connect.commons.dao.Sid;
import org.restcomm.connect.dao.AccountsDao;
import org.restcomm.connect.dao.DaoManager;
import org.restcomm.connect.dao.OrganizationsDao;
import org.restcomm.connect.dao.entities.Account;
import org.restcomm.connect.dao.entities.Organization;
import org.restcomm.connect.dao.exceptions.AccountHierarchyDepthCrossed;
import org.restcomm.connect.http.exceptions.AuthorizationException;
import org.restcomm.connect.http.exceptions.InsufficientPermission;
import org.restcomm.connect.http.exceptions.OperatedAccountMissing;
import org.restcomm.connect.identity.AuthOutcome;
import org.restcomm.connect.identity.IdentityContext;
import org.restcomm.connect.identity.UserIdentityContext;
import org.restcomm.connect.identity.shiro.RestcommRoles;

import javax.servlet.ServletContext;
import java.util.Set;
import static org.restcomm.connect.http.security.AccountPrincipal.ADMIN_ROLE;


/**
 * PermissionEvaluator.
 *
 * How to use it:
 * - use checkAuthenticatedAccount() method to check that a user (any user) is authenticated.
 * - use checkAuthenticatedAccount(permission) method to check that an authenticated user has the required permission according to his roles
 * - use checkAuthenticatedAccount(account,permission) method to check that besides permission a user also has ownership over an account
 *
 * @author orestis.tsakiridis@telestax.com (Orestis Tsakiridis)
 */
public class PermissionEvaluator {

    // types of secured resources used to apply different policies to applications, numbers etc.
    public enum SecuredType {
        SECURED_APP,
        SECURED_ACCOUNT, SECURED_STANDARD
    }

    private Logger logger = Logger.getLogger(PermissionEvaluator.class);

    private AccountsDao accountsDao;
    private OrganizationsDao organizationsDao;
    private IdentityContext identityContext;

    private ServletContext context;


    public PermissionEvaluator() {
    }

    public PermissionEvaluator(ServletContext context) {
        this.context = context;
        final DaoManager storage = (DaoManager) context.getAttribute(DaoManager.class.getName());
        this.accountsDao = storage.getAccountsDao();
        this.organizationsDao = storage.getOrganizationsDao();
        this.identityContext = (IdentityContext) context.getAttribute(IdentityContext.class.getName());
    }

    /**
     * Checks if the effective account is a super account (top level account)
     * @param userIdentityContext
     * @return
     */
    public boolean isSuperAdmin(UserIdentityContext userIdentityContext) {
        //SuperAdmin Account is the one the is
        //1. Has no parent, this is the top account
        //2. Is ACTIVE
        return (userIdentityContext.getEffectiveAccount().getParentSid() == null)
                && (userIdentityContext.getEffectiveAccount().getStatus().equals(Account.Status.ACTIVE));
    }

    /**
     * Checks if the operated account is a direct child of effective account
     * @param operatedAccount
     * @return
     */
    public boolean isDirectChildOfAccount(final Account effectiveAccount,
            final Account operatedAccount) {
        return operatedAccount.getParentSid().equals(effectiveAccount.getSid());
    }

    /**
     * Checks if the effective account is a super account (top level account)
     *
     * @param userIdentityContext
     */
    public void allowOnlySuperAdmin(UserIdentityContext userIdentityContext) {
        if (!isSuperAdmin(userIdentityContext)) {
            throw new InsufficientPermission();
        }
    }

    /**
     * Grants access by permission. If the effective account has a role that resolves
     * to the specified permission (accoording to mappings of restcomm.xml) access is granted.
     * Administrator is granted access regardless of permissions.
     *
     * @param permission - e.g. 'RestComm:Create:Accounts'
     * @param userIdentityContext
     */
    public void checkPermission(final String permission,
            UserIdentityContext userIdentityContext) {
        //checkAuthenticatedAccount(); // ok there is a valid authenticated account
        if ( checkPermission(permission, userIdentityContext.getEffectiveAccountRoles()) != AuthOutcome.OK )
            throw new InsufficientPermission();
    }

    // boolean overloaded form of checkAuthenticatedAccount(permission)
    public boolean isSecuredByPermission(final String permission,
            UserIdentityContext userIdentityContext) {
        try {
            checkPermission(permission,userIdentityContext);
            return true;
        } catch (AuthorizationException e) {
            return false;
        }
    }

    /**
     * Personalized type of grant. Besides checking 'permission' the effective account should have some sort of
     * ownership over the operatedAccount. The exact type of ownership is defined in secureAccount()
     *
     * @param operatedAccount
     * @param permission
     * @param userIdentityContext
     * @throws AuthorizationException
     */
    public void secure(final Account operatedAccount,
            final String permission,
            UserIdentityContext userIdentityContext) throws AuthorizationException {
        secure(operatedAccount, permission, SecuredType.SECURED_STANDARD,
                userIdentityContext);
    }

    /**
     * @param operatedAccount
     * @param permission
     * @param type
     * @param userIdentityContext
     * @throws AuthorizationException
     */
    public void secure(final Account operatedAccount, final String permission, SecuredType type,
            UserIdentityContext userIdentityContext) throws AuthorizationException {
        checkPermission(permission,userIdentityContext); // check an authenticated account allowed to do "permission" is available
        checkOrganization(operatedAccount,userIdentityContext); // check if valid organization is attached with this account.
        if (operatedAccount == null) {
            // if operatedAccount is NULL, we'll probably return a 404. But let's handle that in a central place.
            throw new OperatedAccountMissing();
        }
        if (type == SecuredType.SECURED_STANDARD) {
            if (secureLevelControl(operatedAccount, null,userIdentityContext) != AuthOutcome.OK )
                throw new InsufficientPermission();
        } else
        if (type == SecuredType.SECURED_APP) {
            if (secureLevelControlApplications(operatedAccount,null,userIdentityContext) != AuthOutcome.OK)
                throw new InsufficientPermission();
        } else
        if (type == SecuredType.SECURED_ACCOUNT) {
            if (secureLevelControlAccounts(operatedAccount,userIdentityContext) != AuthOutcome.OK)
                throw new InsufficientPermission();
        }
    }

    /**
     * @param account
     * @throws IllegalStateException
     */
    private void checkOrganization(Account account,
            UserIdentityContext userIdentityContext) throws IllegalStateException {
        Sid organizationSid = account.getOrganizationSid();
        if(organizationSid == null){
            String errorMsg = "there is no organization assosiate with this account: "+account.getSid();
            logger.error(errorMsg);
            throw new IllegalStateException(errorMsg);
        }
        Organization organization = organizationsDao.getOrganization(organizationSid);
        if(organization == null || organization.getDomainName() == null || organization.getDomainName().trim().isEmpty()){
            String errorMsg = "Invalid or Null Organization: "+organization +" for account: "+account.getSid();
            logger.error(errorMsg);
            throw new IllegalStateException(errorMsg);
        }
    }

    /**
     * @param operatedAccount
     * @param resourceAccountSid
     * @param type
     * @param userIdentityContext
     * @throws AuthorizationException
     */
    public void secure(final Account operatedAccount, final Sid resourceAccountSid, SecuredType type,
            UserIdentityContext userIdentityContext) throws AuthorizationException {
        if (operatedAccount == null) {
            // if operatedAccount is NULL, we'll probably return a 404. But let's handle that in a central place.
            throw new OperatedAccountMissing();
        }
        String resourceAccountSidString = resourceAccountSid == null ? null : resourceAccountSid.toString();
        if (type == SecuredType.SECURED_APP) {
            if (secureLevelControlApplications(operatedAccount, resourceAccountSidString,userIdentityContext) != AuthOutcome.OK)
                throw new InsufficientPermission();
        } else
        if (type == SecuredType.SECURED_STANDARD){
            if (secureLevelControl(operatedAccount, resourceAccountSidString,userIdentityContext) != AuthOutcome.OK)
                throw new InsufficientPermission();
        } else
        if (type == SecuredType.SECURED_ACCOUNT)
            throw new IllegalStateException("Account security is not supported when using sub-resources");
        else {
            throw new NotImplementedException();
        }
    }

//    protected void secure(final Account operatedAccount, final Sid resourceAccountSid, final String permission) throws AuthorizationException {
//        secure(operatedAccount, resourceAccountSid, permission, SecuredType.SECURED_STANDARD);
//    }
//
//    protected void secure(final Account operatedAccount, final Sid resourceAccountSid, final String permission, final SecuredType type ) {
//        secure(operatedAccount, resourceAccountSid, type);
//        checkPermission(permission); // check an authbenticated account allowed to do "permission" is available
//    }

    /**
     * Checks is the effective account has the specified role. Only role values contained in the Restcomm Account
     * are take into account.
     *
     * @param role
     * @return true if the role exists in the Account. Otherwise it returns false.
     */
    public boolean hasAccountRole(final String role,UserIdentityContext userIdentityContext) {
        if (userIdentityContext.getEffectiveAccount() != null) {
            return userIdentityContext.getEffectiveAccountRoles().contains(role);
        }
        return false;
    }

    /**
     * Low level permission checking. roleNames are checked for neededPermissionString permission using permission
     * mappings contained in restcomm.xml. The permission mappings are stored in RestcommRoles.
     *
     * Note: Administrator is granted access with eyes closed

     * @param neededPermissionString
     * @param roleNames
     * @return
     */
    private AuthOutcome checkPermission(String neededPermissionString, Set<String> roleNames) {
        // if this is an administrator ask no more questions
        if ( roleNames.contains(getAdministratorRole()))
            return AuthOutcome.OK;

        // normalize the permission string
        //neededPermissionString = "domain:" + neededPermissionString;

        WildcardPermissionResolver resolver = new WildcardPermissionResolver();
        Permission neededPermission = resolver.resolvePermission(neededPermissionString);

        // check the neededPermission against all roles of the user
        RestcommRoles restcommRoles = identityContext.getRestcommRoles();
        for (String roleName: roleNames) {
            SimpleRole simpleRole = restcommRoles.getRole(roleName);
            if ( simpleRole == null) {
                return AuthOutcome.FAILED;
            }
            else {
                Set<Permission> permissions = simpleRole.getPermissions();
                // check the permissions one by one
                for (Permission permission: permissions) {
                    if (permission.implies(neededPermission)) {
                        if (logger.isDebugEnabled()) {
                            logger.debug("Granted access by permission " + permission.toString());
                        }
                        return AuthOutcome.OK;
                    }
                }
                if (logger.isDebugEnabled()) {
                    logger.debug("Role " + roleName + " does not allow " + neededPermissionString);
                }
            }
        }
        return AuthOutcome.FAILED;
    }

    /**
     * Applies the following access control rule:

     * If no sub-resources are involved (resourceAccountSid is null):
     *  - If operatingAccount is the same or an ancestor of operatedAccount, access is granted
     * If there are sub-resources involved:
     *  - If operatingAccount is the same or an ancestor of operatedAccount AND resource belongs to operatedAccount access is granted

     * @param operatedAccount the account specified in the URL
     * @param resourceAccountSid the account SID property of the operated resource e.g. the accountSid of a DID.
     *
     */
    private AuthOutcome secureLevelControl(Account operatedAccount, String resourceAccountSid,
            UserIdentityContext userIdentityContext) {
        Account operatingAccount = userIdentityContext.getEffectiveAccount();
        String operatingAccountSid = null;
        if (operatingAccount != null)
            operatingAccountSid = operatingAccount.getSid().toString();
        String operatedAccountSid = null;
        if (operatedAccount != null)
            operatedAccountSid = operatedAccount.getSid().toString();
        // in case we're dealing with resources, we first make sure that they are accessed under their owner account.
        if (resourceAccountSid != null)
            if (! resourceAccountSid.equals(operatedAccountSid))
                return AuthOutcome.FAILED;
        // check parent/ancestor relationship between operatingAccount and operatedAccount
        if ( operatingAccountSid.equals(operatedAccountSid) )
            return AuthOutcome.OK;
        if ( operatedAccount.getParentSid() != null ) {
            if (operatedAccount.getParentSid().toString().equals(operatingAccountSid))
                return AuthOutcome.OK;
            else if (accountsDao.getAccountLineage(operatedAccount).contains(operatingAccountSid))
                return AuthOutcome.OK;
        }
        return AuthOutcome.FAILED;
    }

    /**
     * Uses the security policy applied by secureLevelControl(). See there for details.
     *
     * DEPRECATED security policy:
     *
     * Applies the following access control rules
     *
     * If an application Account Sid is given:
     *  - If operatingAccount is the same as the operated account and application resource belongs to operated account too
     *    acces is granted.
     * If no application Account Sid is given:
     *  - If operatingAccount is the same as the operated account access is granted.
     *
     * NOTE: Parent/ancestor relationships on accounts do not grant access here.
     *
     * @param operatedAccount
     * @param applicationAccountSid
     * @return
     */
    private AuthOutcome secureLevelControlApplications(Account operatedAccount, String applicationAccountSid,
            UserIdentityContext userIdentityContext) {
        /*
        // disabled strict policy that prevented access to sub-account applications

        // operatingAccount and operatedAccount are not null at this point
        Account operatingAccount = userIdentityContext.getEffectiveAccount();
        String operatingAccountSid = operatingAccount.getSid().toString();
        String operatedAccountSid = operatedAccount.getSid().toString();
        if (!operatingAccountSid.equals(String.valueOf(operatedAccountSid))) {
            return AuthOutcome.FAILED;
        } else if (applicationAccountSid != null && !operatingAccountSid.equals(applicationAccountSid)) {
            return AuthOutcome.FAILED;
        }
        return AuthOutcome.OK;
        */

        // use the more liberal default policy that applies to other entities for applications too
        return secureLevelControl(operatedAccount, applicationAccountSid, userIdentityContext);
    }

    /** Applies the following access control rules:
     *
     * If the operating account is an administrator:
     *  - If it is the same or parent/ancestor of the operated account access is granted.
     * If the operating accoutn is NOT an administrator:
     *  - If it is the same as the operated account access is granted.
     *
     * @param operatedAccount
     * @return
     */
    private AuthOutcome secureLevelControlAccounts(Account operatedAccount,
            UserIdentityContext userIdentityContext) throws AccountHierarchyDepthCrossed {
        // operatingAccount and operatedAccount are not null
        Account operatingAccount = userIdentityContext.getEffectiveAccount();
        String operatingAccountSid = operatingAccount.getSid().toString();
        String operatedAccountSid = operatedAccount.getSid().toString();
        if (getAdministratorRole().equals(operatingAccount.getRole())) {
            // administrator can also operate on themselves, direct children, grand-children
            if (operatingAccountSid.equals(operatedAccountSid))
                return AuthOutcome.OK;
            if (operatedAccount.getParentSid() != null ) {
                if (operatedAccount.getParentSid().toString().equals(operatingAccountSid ))
                    return AuthOutcome.OK;
                else if (accountsDao.getAccountLineage(operatedAccount).contains(operatingAccountSid))
                    return AuthOutcome.OK;
            }
            return AuthOutcome.FAILED;
        } else { // non-administrators can only operate directly on themselves
            if ( operatingAccountSid.equals(operatedAccountSid) )
                return AuthOutcome.OK;
            else
                return AuthOutcome.FAILED;
        }
    }

    /**
     * Returns the string literal for the administrator role. This role is granted implicitly access from checkAuthenticatedAccount() method.
     * No need to explicitly apply it at each protected resource
     * .
     * @return the administrator role as string
     */
    public String getAdministratorRole() {
        return ADMIN_ROLE;
    }


}
