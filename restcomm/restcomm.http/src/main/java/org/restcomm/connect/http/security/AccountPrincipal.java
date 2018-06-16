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

import java.security.Principal;
import java.util.Set;
import org.restcomm.connect.dao.entities.Account;
import org.restcomm.connect.identity.UserIdentityContext;

public class AccountPrincipal implements Principal {
    public static final String SUPER_ADMIN_ROLE = "SuperAdmin";
    public static final String ADMIN_ROLE = "Administrator";

    UserIdentityContext identityContext;

    public AccountPrincipal(UserIdentityContext identityContext) {
        this.identityContext = identityContext;
    }

    public boolean isSuperAdmin() {
        //SuperAdmin Account is the one the is
        //1. Has no parent, this is the top account
        //2. Is ACTIVE
        return (identityContext.getEffectiveAccount() != null
                && identityContext.getEffectiveAccount().getParentSid() == null)
                && (identityContext.getEffectiveAccount().getStatus().equals(Account.Status.ACTIVE));
    }

    public Set<String> getRole() {
        Set<String> roles = identityContext.getEffectiveAccountRoles();
        if (isSuperAdmin()) {
            roles.add(SUPER_ADMIN_ROLE);
        }
        return roles;
    }

    @Override
    public String getName() {
        if (identityContext.getEffectiveAccount() != null) {
            return identityContext.getAccountKey().getAccount().getSid().toString();
        } else {
            return null;
        }
    }

    public UserIdentityContext getIdentityContext() {
        return identityContext;
    }
}
