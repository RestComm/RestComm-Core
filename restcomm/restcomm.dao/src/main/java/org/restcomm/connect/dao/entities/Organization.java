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

import org.joda.time.DateTime;
import org.restcomm.connect.commons.annotations.concurrency.Immutable;
import org.restcomm.connect.commons.annotations.concurrency.NotThreadSafe;
import org.restcomm.connect.commons.dao.Sid;

/**
 * @author maria-farooq@live.com (Maria Farooq)
 */
@Immutable
public class Organization {
    private final Sid sid;
    private final String domainName;
    private final DateTime dateCreated;
    private final DateTime dateUpdated;
    private final Status status;

    /**
     * @param sid
     * @param domainName - such as customer.restcomm.com
     * @param dateCreated
     * @param dateUpdated
     * @param status
     * @throws IllegalArgumentException if sid or domainName are null/empty
     */
    public Organization(final Sid sid, final String domainName, final DateTime dateCreated, final DateTime dateUpdated, final Status status) throws IllegalArgumentException {
        super();
        if(domainName == null || domainName.trim().isEmpty())
            throw new IllegalArgumentException("Organization domainName can not be empty.");
        if(sid == null)
            throw new IllegalArgumentException("Organization sid can not be empty.");
        this.sid = sid;

        if(domainName.endsWith(".")){
            this.domainName = domainName.substring(0, domainName.lastIndexOf('.'));
        }else{
            this.domainName = domainName;
        }
        this.dateCreated = dateCreated;
        this.dateUpdated = dateUpdated;
        this.status = status;
    }

    public enum Status {
        // max length of a new status should be under 16 characters otherwise schema update is required.
        ACTIVE("active"), CLOSED("closed");

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
            throw new IllegalArgumentException(text + " is not a valid organization status.");
        }

        @Override
        public String toString() {
            return text;
        }
    };

    public static Builder builder() {
        return new Builder();
    }

    public Sid getSid() {
        return sid;
    }

    public String getDomainName() {
        if(domainName != null && domainName.endsWith("."))
            return domainName.substring(0, domainName.lastIndexOf('.'));
        return domainName;
    }

    public DateTime getDateCreated() {
        return dateCreated;
    }

    public DateTime getDateUpdated() {
        return dateUpdated;
    }

    public Status getStatus() {
        return status;
    }

    /**
     * @param domainName
     * @return
     * @throws IllegalArgumentException in case provided domainName is empty or null
     */
    public Organization setDomainName(final String domainName) throws IllegalArgumentException {
        if(domainName == null || domainName.trim().isEmpty())
            throw new IllegalArgumentException("Organization domainName can not be empty.");
        return new Organization(sid, domainName, dateCreated, DateTime.now(), status);
    }

    @NotThreadSafe
    public static final class Builder {
        private Sid sid;
        private String domainName;
        private DateTime dateCreated;
        private DateTime dateUpdated;
        private Status status;

        private Builder() {
            super();
            sid = null;
            domainName = null;
            dateCreated = DateTime.now();
            dateUpdated = DateTime.now();
            status = null;
        }

        public Organization build() {
            return new Organization(sid, domainName, dateCreated, dateUpdated, status);
        }

        public void setSid(final Sid sid) {
            this.sid = sid;
        }

        public void setDomainName(final String domainName) {
            this.domainName = domainName;
        }

        public void setStatus(final Status status) {
            this.status = status;
        }

        @Override
        public String toString() {
            return "Organization.Builder [sid=" + sid + ", domainName=" + domainName + ", dateCreated=" + dateCreated
                    + ", dateUpdated=" + dateUpdated + ", status=" + status + "]";
        }
    }

    @Override
    public String toString() {
        return "Organization [sid=" + sid + ", domainName=" + domainName + ", dateCreated=" + dateCreated
                + ", dateUpdated=" + dateUpdated + ", status=" + status + "]";
    }

}
