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

import org.restcomm.connect.dao.common.Sorting;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * @author <a href="mailto:n.congvu@gmail.com">vunguyen</a>
 */

public class RecordingFilter {

    private final String accountSid;
    private final List<String> accountSidSet; // if not-null we need the cdrs that belong to several accounts
    private final Date startTime;  // to initialize it pass string arguments with  yyyy-MM-dd format
    private final Date endTime;
    private final String callSid;
    private final Integer limit;
    private final Integer offset;
    private final String instanceid;
    private final Sorting.Direction sortByDate;
    private final Sorting.Direction sortByDuration;
    private final Sorting.Direction sortByCallSid;

    public RecordingFilter(String accountSid, List<String> accountSidSet, String startTime, String endTime,
                                  String callSid, Integer limit, Integer offset) throws ParseException {
        this(accountSid, accountSidSet, startTime,endTime, callSid, limit,offset, null, null, null, null);
    }

    public RecordingFilter(String accountSid, List<String> accountSidSet, String startTime, String endTime,
                                  String callSid, Integer limit, Integer offset, String instanceId, Sorting.Direction sortByDate,
                           Sorting.Direction sortByDuration, Sorting.Direction sortByCallSid) throws ParseException {
        this.accountSid = accountSid;
        this.accountSidSet = accountSidSet;

        this.callSid = callSid;
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
        this.sortByDuration = sortByDuration;
        this.sortByCallSid = sortByCallSid;
    }

    public String getSid() {
        return accountSid;
    }

    public List<String> getAccountSidSet() {
        return accountSidSet;
    }

    public String getCallSid() {
        return callSid;
    }

    public Date getStartTime() {
        return startTime;
    }

    public Date getEndTime() {
        return endTime;
    }

    public Integer getLimit() {
        return limit;
    }

    public Integer getOffset() {
        return offset;
    }

    public String getInstanceid() { return instanceid; }

    public Sorting.Direction getSortByDate() { return sortByDate; }
    public Sorting.Direction getSortByDuration() { return sortByDuration; }
    public Sorting.Direction getSortByCallSid() { return sortByCallSid; }

    public static final class Builder {
        private String accountSid = null;
        private List<String> accountSidSet = null;
        private String startTime = null;
        private String endTime = null;
        private String callSid = null;
        private String instanceid = null;
        private Sorting.Direction sortByDate = null;
        private Sorting.Direction sortByDuration = null;
        private Sorting.Direction sortByCallSid = null;
        private Integer limit = null;
        private Integer offset = null;

        public static RecordingFilter.Builder builder() {
            return new RecordingFilter.Builder();
        }

        public RecordingFilter build() throws ParseException {
            return new RecordingFilter(accountSid,
                    accountSidSet,
                    startTime,
                    endTime,
                    callSid,
                    limit,
                    offset,
                    instanceid,
                    sortByDate,
                    sortByDuration,
                    sortByCallSid);
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
        public Builder byStartTime(String startTime) {
            this.startTime = startTime;
            return this;
        }
        public Builder byEndTime(String endTime) {
            this.endTime = endTime;
            return this;
        }
        public Builder byCallSid(String callSid) {
            this.callSid = callSid;
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
        public Builder sortedByDuration(Sorting.Direction sortDirection) {
            this.sortByDuration = sortDirection;
            return this;
        }
        public Builder sortedByCallSid(Sorting.Direction sortDirection) {
            this.sortByCallSid = sortDirection;
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
