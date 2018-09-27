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

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import org.restcomm.connect.commons.annotations.concurrency.Immutable;
import org.restcomm.connect.dao.common.Sorting;

/**
 * @author <a href="mailto:gvagenas@gmail.com">gvagenas</a>
 */

@Immutable
public class CallDetailRecordFilter {
    private final String accountSid;
    private final List<String> accountSidSet; // if not-null we need the cdrs that belong to several accounts
    private final String recipient;
    private final String sender;
    private final String status;
    private final Date startTime;  // to initialize it pass string arguments with  yyyy-MM-dd format
    private final Date endTime;
    private final String parentCallSid;
    private final String conferenceSid;
    private final Integer limit;
    private final Integer offset;
    private final String instanceid;
    private final Sorting.Direction sortByDate;
    private final Sorting.Direction sortByFrom;
    private final Sorting.Direction sortByTo;
    private final Sorting.Direction sortByDirection;
    private final Sorting.Direction sortByStatus;
    private final Sorting.Direction sortByDuration;
    private final Sorting.Direction sortByPrice;

    public CallDetailRecordFilter(String accountSid, List<String> accountSidSet, String recipient, String sender, String status, String startTime, String endTime,
                                  String parentCallSid, String conferenceSid, Integer limit, Integer offset) throws ParseException {
        this(accountSid, accountSidSet, recipient,sender,status,startTime,endTime,parentCallSid, conferenceSid, limit, offset, null, null,
                null, null, null, null, null, null);
    }

    public CallDetailRecordFilter(String accountSid, List<String> accountSidSet, String recipient, String sender, String status, String startTime, String endTime,
                                  String parentCallSid, String conferenceSid, Integer limit, Integer offset, String instanceId, Sorting.Direction sortByDate,
                                  Sorting.Direction sortByFrom, Sorting.Direction sortByTo, Sorting.Direction sortByDirection, Sorting.Direction sortByStatus, Sorting.Direction sortByDuration,
                                  Sorting.Direction sortByPrice) throws ParseException {
        this.accountSid = accountSid;
        this.accountSidSet = accountSidSet;

        // The LIKE keyword uses '%' to match any (including 0) number of characters, and '_' to match exactly one character
        // Add here the '%' keyword so +15126002188 will be the same as 15126002188 and 6002188
        if (recipient != null)
            recipient = "%".concat(recipient);
        if (sender != null)
            sender = "%".concat(sender);

        this.recipient = recipient;
        this.sender = sender;
        this.status = status;
        this.parentCallSid = parentCallSid;
        this.conferenceSid = conferenceSid;
        this.limit = limit;
        this.offset = offset;
        if (startTime != null) {
            SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd");
            Date date = parser.parse(startTime);
            this.startTime = date;
        } else
            this.startTime = null;

        if (endTime != null) {
            SimpleDateFormat parser = new SimpleDateFormat("yyyy-MM-dd");
            Date date = parser.parse(endTime);
            this.endTime = date;
        } else {
            this.endTime = null;
        }
        if (instanceId != null && !instanceId.isEmpty()) {
            this.instanceid = instanceId;
        } else {
            this.instanceid = null;
        }

        this.sortByDate = sortByDate;
        this.sortByFrom = sortByFrom;
        this.sortByTo = sortByTo;
        this.sortByDirection = sortByDirection;
        this.sortByStatus = sortByStatus;
        this.sortByDuration = sortByDuration;
        this.sortByPrice = sortByPrice;
    }

    public String getSid() {
        return accountSid;
    }

    public List<String> getAccountSidSet() {
        return accountSidSet;
    }

    public String getRecipient() {
        return recipient;
    }

    public String getSender() {
        return sender;
    }

    public String getStatus() {
        return status;
    }

    public Date getStartTime() {
        return startTime;
    }

    public Date getEndTime() {
        return endTime;
    }

    public String getParentCallSid() {
        return parentCallSid;
    }

    public String getConferenceSid() {
        return conferenceSid;
    }

    public Integer getLimit() {
        return limit;
    }

    public Integer getOffset() {
        return offset;
    }

    public String getInstanceid() { return instanceid; }

    public Sorting.Direction getSortByDate() { return sortByDate; }
    public Sorting.Direction getSortByFrom() { return sortByFrom; }
    public Sorting.Direction getSortByTo() { return sortByTo; }
    public Sorting.Direction getSortByDirection() { return sortByDirection; }
    public Sorting.Direction getSortByStatus() { return sortByStatus; }
    public Sorting.Direction getSortByDuration() { return sortByDuration; }
    public Sorting.Direction getSortByPrice() { return sortByPrice; }


    public static final class Builder {
        private String accountSid = null;
        private List<String> accountSidSet = null;
        private String recipient = null;
        private String sender = null;
        private String status = null;
        private String startTime = null;
        private String endTime = null;
        private String parentCallSid = null;
        private String conferenceSid = null;
        private String instanceid = null;
        private Sorting.Direction sortByDate = null;
        private Sorting.Direction sortByFrom = null;
        private Sorting.Direction sortByTo = null;
        private Sorting.Direction sortByDirection = null;
        private Sorting.Direction sortByStatus = null;
        private Sorting.Direction sortByDuration = null;
        private Sorting.Direction sortByPrice = null;

        private Integer limit = null;
        private Integer offset = null;

        public static CallDetailRecordFilter.Builder builder() {
            return new CallDetailRecordFilter.Builder();
        }


        public CallDetailRecordFilter build() throws ParseException {
            return new CallDetailRecordFilter(accountSid,
                    accountSidSet,
                    recipient,
                    sender,
                    status,
                    startTime,
                    endTime,
                    parentCallSid,
                    conferenceSid,
                    limit,
                    offset,
                    instanceid,
                    sortByDate,
                    sortByFrom,
                    sortByTo,
                    sortByDirection,
                    sortByStatus,
                    sortByDuration,
                    sortByPrice);
        }

        // Filters
        public Builder byAccountSid(String accountSid) {
            this.accountSid = accountSid;
            return this;
        }
        public Builder byAccountSidSet(List<String> accountSidSet) {
            this.accountSidSet = accountSidSet;
            return this;
        }
        public Builder byRecipient(String recipient) {
            this.recipient = recipient;
            return this;
        }
        public Builder bySender(String sender) {
            this.sender = sender;
            return this;
        }
        public Builder byStatus(String status) {
            this.status = status;
            return this;
        }
        public Builder byStartTime(String startTime) {
            this.startTime = startTime;
            return this;
        }
        public Builder byEndTime(String endTime) {
            this.endTime = endTime;
            return this;
        }
        public Builder byParentCallSid(String parentCallSid) {
            this.parentCallSid = parentCallSid;
            return this;
        }
        public Builder byConferenceSid(String conferenceSid) {
            this.conferenceSid = conferenceSid;
            return this;
        }
        public Builder byInstanceId(String instanceid) {
            this.instanceid = instanceid;
            return this;
        }

        // Sorters
        public Builder sortedByDate(Sorting.Direction sortDirection) {
            this.sortByDate = sortDirection;
            return this;
        }
        public Builder sortedByFrom(Sorting.Direction sortDirection) {
            this.sortByFrom = sortDirection;
            return this;
        }
        public Builder sortedByTo(Sorting.Direction sortDirection) {
            this.sortByTo = sortDirection;
            return this;
        }
        public Builder sortedByDirection(Sorting.Direction sortDirection) {
            this.sortByDirection = sortDirection;
            return this;
        }
        public Builder sortedByStatus(Sorting.Direction sortDirection) {
            this.sortByStatus = sortDirection;
            return this;
        }
        public Builder sortedByDuration(Sorting.Direction sortDirection) {
            this.sortByDuration = sortDirection;
            return this;
        }
        public Builder sortedByPrice(Sorting.Direction sortDirection) {
            this.sortByPrice = sortDirection;
            return this;
        }


        // Paging
        public Builder limited(Integer limit, Integer offset) {
            this.limit = limit;
            this.offset = offset;
            return this;
        }
    }
}
