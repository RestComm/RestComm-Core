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
package org.restcomm.connect.dao.entities;

import java.net.URI;

import org.joda.time.DateTime;
import org.restcomm.connect.commons.annotations.concurrency.Immutable;
import org.restcomm.connect.commons.dao.Sid;

/**
 * Represent a user Account
 *
 * @author quintana.thomas@gmail.com (Thomas Quintana)
 * @author maria-farooq@live.com (Maria Farooq)
 */
@Immutable
public final class Account {
    private final Sid sid;
    private final DateTime dateCreated;
    private final DateTime dateUpdated;
    private final String emailAddress;
    private final String friendlyName;
    private final Sid parentSid;
    private final Type type;
    private final Status status;
    private final String authToken;
    private final String role;
    private final URI uri;
    private final Sid organizationSid;

    public Account(final Sid sid, final DateTime dateCreated, final DateTime dateUpdated, final String emailAddress,
                   final String friendlyName, final Sid parentSid, final Type type, final Status status, final String authToken,
                   final String role, final URI uri, final Sid organizationSid) {
        super();
        this.sid = sid;
        this.dateCreated = dateCreated;
        this.dateUpdated = dateUpdated;
        this.emailAddress = emailAddress;
        this.friendlyName = friendlyName;
        this.parentSid = parentSid;
        this.type = type;
        this.status = status;
        this.authToken = authToken;
        this.role = role;
        this.uri = uri;
        this.organizationSid = organizationSid;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Sid getSid() {
        return sid;
    }

    public DateTime getDateCreated() {
        return dateCreated;
    }

    public DateTime getDateUpdated() {
        return dateUpdated;
    }

    public String getEmailAddress() {
        return emailAddress;
    }

    public String getFriendlyName() {
        return friendlyName;
    }

    public Sid getParentSid() {
        return parentSid;
    }

    public Type getType() {
        return type;
    }

    public Status getStatus() {
        return status;
    }

    public String getAuthToken() {
        return authToken;
    }

    public String getRole() {
        return role;
    }

    public URI getUri() {
        return uri;
    }

    public Sid getOrganizationSid() {
        return organizationSid;
    }

    public Account setEmailAddress(final String emailAddress) {
        return new Account(sid, dateCreated, DateTime.now(), emailAddress, friendlyName, parentSid, type, status, authToken,
                role, uri, organizationSid);
    }

    public Account setFriendlyName(final String friendlyName) {
        return new Account(sid, dateCreated, DateTime.now(), emailAddress, friendlyName, parentSid, type, status, authToken,
                role, uri, organizationSid);
    }

    public Account setType(final Type type) {
        return new Account(sid, dateCreated, DateTime.now(), emailAddress, friendlyName, parentSid, type, status, authToken,
                role, uri, organizationSid);
    }

    public Account setStatus(final Status status) {
        return new Account(sid, dateCreated, DateTime.now(), emailAddress, friendlyName, parentSid, type, status, authToken,
                role, uri, organizationSid);
    }

    public Account setAuthToken(final String authToken) {
        return new Account(sid, dateCreated, DateTime.now(), emailAddress, friendlyName, parentSid, type, status, authToken,
                role, uri, organizationSid);
    }

    public Account setRole(final String role) {
        return new Account(sid, dateCreated, DateTime.now(), emailAddress, friendlyName, parentSid, type, status, authToken,
                role, uri, organizationSid);
    }

    public Account setOrganizationSid(final Sid organizationSid) {
        return new Account(sid, dateCreated, DateTime.now(), emailAddress, friendlyName, parentSid, type, status, authToken,
                role, uri, organizationSid);
    }

    public enum Status {
        ACTIVE("active"), CLOSED("closed"), SUSPENDED("suspended"), INACTIVE("inactive"), UNINITIALIZED("uninitialized");

        private final String text;

        private Status(final String text) {
            this.text = text;
        }

        public static Status getValueOf(final String text) {
            Status[] values = values();
            for (final Status value : values) {
                if (value.toString().equals(text)) {
                    return value;
                }
            }
            throw new IllegalArgumentException(text + " is not a valid account status.");
        }

        @Override
        public String toString() {
            return text;
        }
    };

    public enum Type {
        FULL("Full"), TRIAL("Trial");

        private final String text;

        private Type(final String text) {
            this.text = text;
        }

        public static Type getValueOf(final String text) {
            Type[] values = values();
            for (final Type value : values) {
                if (value.text.equals(text)) {
                    return value;
                }
            }
            throw new IllegalArgumentException(text + " is not a valid account type.");
        }

        @Override
        public String toString() {
            return text;
        }
    };

    public static final class Builder {
        private Sid sid;
        private String emailAddress;
        private String friendlyName;
        private Sid parentSid;
        private Type type;
        private Status status;
        private String authToken;
        private String role;
        private URI uri;
        private Sid organizationSid;

        private Builder() {
            super();
        }

        public Builder copy(Account account) {
            sid = account.getSid();
            parentSid = account.getParentSid();
            organizationSid = account.getOrganizationSid();
            type = account.getType();
            uri = account.getUri();
            authToken = account.getAuthToken();
            emailAddress = account.getEmailAddress();
            friendlyName = account.getFriendlyName();
            role = account.getRole();
            status = account.getStatus();
            return this;
        }

        public Account build() {
            final DateTime now = DateTime.now();
            return new Account(sid, now, now, emailAddress, friendlyName, parentSid, type, status, authToken, role, uri, organizationSid);
        }

        public void setSid(final Sid sid) {
            this.sid = sid;
        }

        public void setEmailAddress(final String emailAddress) {
            this.emailAddress = emailAddress;
        }

        public void setFriendlyName(final String friendlyName) {
            this.friendlyName = friendlyName;
        }

        public void setParentSid(final Sid parentSid) {
            this.parentSid = parentSid;
        }

        public void setOrganizationSid(final Sid organizationSid) {
            this.organizationSid = organizationSid;
        }

        public void setType(final Type type) {
            this.type = type;
        }

        public void setStatus(final Status status) {
            this.status = status;
        }

        public void setAuthToken(final String authToken) {
            this.authToken = authToken;
        }

        public void setRole(final String role) {
            this.role = role;
        }

        public void setUri(final URI uri) {
            this.uri = uri;
        }
    }
}
