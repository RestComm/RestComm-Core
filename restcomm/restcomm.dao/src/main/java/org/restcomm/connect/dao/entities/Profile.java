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

import java.util.Calendar;
import java.util.Date;

import org.restcomm.connect.commons.annotations.concurrency.Immutable;
import org.restcomm.connect.commons.annotations.concurrency.NotThreadSafe;

/**
 * @author maria.farooq@telestax.com (Maria Farooq)
 */
@Immutable
public class Profile{
    public static final String DEFAULT_PROFILE_SID = "PRae6e420f425248d6a26948c17a9e2acf";
    private final String sid;
    private final String profileDocument;
    private final Date dateCreated;
    private final Date dateUpdated;

    public Profile(final String sid, final String profileDocument, final Date dateCreated, final Date dateUpdated) {
        super();
        this.sid = sid;
        this.profileDocument = profileDocument;
        this.dateCreated = dateCreated;
        this.dateUpdated = dateUpdated;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getSid() {
        return sid;
    }

    public String getProfileDocument() {
        return profileDocument;
    }

    public Date getDateCreated() {
        return dateCreated;
    }

    public Date getDateUpdated() {
        return dateUpdated;
    }

    public Profile setProfileDocument(String updatedProfileDocument){
        return new Profile(this.sid, updatedProfileDocument, this.dateCreated, this.dateUpdated);
    }

    @NotThreadSafe
    public static final class Builder {
        private String sid;
        private String profileDocument;
        private Date dateCreated;

        private Builder() {
            super();
            sid = null;
            profileDocument = null;
            dateCreated = null;
        }

        public Profile build() {
            return new Profile(sid, profileDocument, dateCreated, Calendar.getInstance().getTime());
        }

        public void setProfileDocument(final String profileDocument) {
            this.profileDocument = profileDocument;
        }

        public void setSid(final String sid) {
            this.sid = sid;
        }

        public void setDateCreated(final Date dateCreated) {
            this.dateCreated = dateCreated;
        }
    }

}
