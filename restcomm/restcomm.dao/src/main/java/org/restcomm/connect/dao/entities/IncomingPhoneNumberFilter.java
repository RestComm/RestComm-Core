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

import org.restcomm.connect.commons.annotations.concurrency.Immutable;
import org.restcomm.connect.dao.common.Sorting;

/**
 * @author <a href="mailto:jean.deruelle@telestax.com">Jean Deruelle</a>
 */
@Immutable
public class IncomingPhoneNumberFilter {

    private final String accountSid;
    private final String friendlyName;
    private final String phoneNumber;
    private final Sorting.Direction sortByNumber;
    private final Sorting.Direction sortByFriendly;
    private final Integer limit;
    private final Integer offset;
    private final String orgSid;
    private final Boolean pureSIP;
    private SearchFilterMode filterMode;

    private IncomingPhoneNumberFilter(String accountSid, String friendlyName, String phoneNumber,
            Sorting.Direction sortByNumber,Sorting.Direction sortByFriendly, Integer limit, Integer offset, String orgSid, Boolean pureSIP, SearchFilterMode filterMode) {
        this.accountSid = accountSid;
        this.friendlyName = friendlyName;
        this.phoneNumber = phoneNumber;
        this.sortByNumber = sortByNumber;
        this.sortByFriendly = sortByFriendly;
        this.limit = limit;
        this.offset = offset;
        this.orgSid = orgSid;
        this.pureSIP = pureSIP;
        this.filterMode = filterMode;
    }

    public IncomingPhoneNumberFilter(String accountSid, String friendlyName, String phoneNumber) {
        this.accountSid = accountSid;
        this.friendlyName = friendlyName;
        this.phoneNumber = phoneNumber;
        this.sortByNumber = null;
        this.sortByFriendly = null;
        this.offset = null;
        this.limit = null;
        this.orgSid = null;
        this.pureSIP = null;
        this.filterMode = SearchFilterMode.PERFECT_MATCH;
    }

    public String getAccountSid() {
        return accountSid;
    }

    /**
     * @return the friendlyName
     */
    public String getFriendlyName() {
        return friendlyName;
    }

    /**
     * @return the phoneNumber
     */
    public String getPhoneNumber() {
        return phoneNumber;
    }

    public Sorting.Direction getSortByNumber() {
        return sortByNumber;
    }

    public Sorting.Direction getSortByFriendly() {
        return sortByFriendly;
    }

    /**
     * @return the limit
     */
    public Integer getLimit() {
        return limit;
    }

    /**
     * @return the offset
     */
    public Integer getOffset() {
        return offset;
    }

    public String getOrgSid() {
        return orgSid;
    }

    public Boolean getPureSIP() {
        return pureSIP;
    }

    public SearchFilterMode getFilterMode() {
        return filterMode;
    }

    @Override
    public String toString() {
        return "IncomingPhoneNumberFilter{" + "accountSid=" + accountSid + ", friendlyName=" + friendlyName + ", phoneNumber=" + phoneNumber + ", sortByNumber=" + sortByNumber + ", limit=" + limit + ", offset=" + offset + ", orgSid=" + orgSid + ", pureSIP=" + pureSIP + '}';
    }

    public static final class Builder {

        private String accountSid = null;
        private String friendlyName = null;
        private String phoneNumber = null;
        private Sorting.Direction sortByPhoneNumber = null;
        private Sorting.Direction sortByFriendly = null;
        private Integer limit = null;
        private Integer offset = null;
        private String orgSid = null;
        private Boolean pureSIP = null;
        private SearchFilterMode filterMode = SearchFilterMode.PERFECT_MATCH;

        public static IncomingPhoneNumberFilter.Builder builder() {
            return new IncomingPhoneNumberFilter.Builder();
        }

        /**
         * Prepare fields with SQL wildcards
         *
         * @param value
         * @return
         */
        private String convertIntoSQLWildcard(String value) {
            String wildcarded = value;
            // The LIKE keyword uses '%' to match any (including 0) number of characters, and '_' to match exactly one character
            // Add here the '%' keyword so +15126002188 will be the same as 15126002188 and 6002188
            if (wildcarded != null) {
                wildcarded = "%" + wildcarded + "%";
            }
            return wildcarded;
        }

        public IncomingPhoneNumberFilter build() {
            if (filterMode.equals(SearchFilterMode.WILDCARD_MATCH)) {
                phoneNumber = convertIntoSQLWildcard(phoneNumber);
                friendlyName = convertIntoSQLWildcard(friendlyName);
            }

            return new IncomingPhoneNumberFilter(accountSid,
                    friendlyName,
                    phoneNumber,
                    sortByPhoneNumber,
                    sortByFriendly,
                    limit,
                    offset,
                    orgSid,
                    pureSIP,
                    filterMode);
        }

        public Builder byAccountSid(String accountSid) {
            this.accountSid = accountSid;
            return this;
        }

        public Builder byPureSIP(Boolean pureSIP) {
            this.pureSIP = pureSIP;
            return this;
        }

        public Builder byFriendlyName(String friendlyName) {
            this.friendlyName = friendlyName;
            return this;
        }

        public Builder byPhoneNumber(String phoneNumber) {
            this.phoneNumber = phoneNumber;
            return this;
        }

        public Builder sortedByPhoneNumber(Sorting.Direction sortDirection) {
            this.sortByPhoneNumber = sortDirection;
            return this;
        }

        public Builder sortedByfriendly(Sorting.Direction sortDirection) {
            this.sortByFriendly = sortDirection;
            return this;
        }

        public Builder usingMode(SearchFilterMode mode) {
            this.filterMode = mode;
            return this;
        }

        public Builder limited(Integer limit, Integer offset) {
            this.limit = limit;
            this.offset = offset;
            return this;
        }

        public Builder byOrgSid(String orgSid) {
            this.orgSid = orgSid;
            return this;
        }
    }

}
