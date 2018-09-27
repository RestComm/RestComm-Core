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
 * @author <a href="mailto:n.congvu@gmail.com">vunguyen</a>
 */

@Immutable
public class NotificationFilter {

    private final String accountSid;
    private final List<String> accountSidSet; // if not-null we need the cdrs that belong to several accounts
    private final Date startTime;  // to initialize it pass string arguments with  yyyy-MM-dd format
    private final Date endTime;
    private final String errorCode;
    private final String requestUrl;
    private final String messageText;
    private final Integer limit;
    private final Integer offset;
    private final String instanceid;
    private final Sorting.Direction sortByDate;
    private final Sorting.Direction sortByLog;
    private final Sorting.Direction sortByErrorCode;
    private final Sorting.Direction sortByCallSid;
    private final Sorting.Direction sortByMessageText;


    public NotificationFilter(String accountSid, List<String> accountSidSet, String startTime, String endTime, String errorCode, String requestUrl,
                                  String messageText, Integer limit, Integer offset) throws ParseException {
        this(accountSid, accountSidSet, startTime,endTime, errorCode, requestUrl, messageText, limit,offset,null, null, null, null, null, null);
    }

    public NotificationFilter(String accountSid, List<String> accountSidSet, String startTime, String endTime, String errorCode, String requestUrl,
                                  String messageText, Integer limit, Integer offset, String instanceId, Sorting.Direction sortByDate,
                              Sorting.Direction sortByLog, Sorting.Direction sortByErrorCode, Sorting.Direction sortByCallSid,
                              Sorting.Direction sortByMessageText) throws ParseException {
        this.accountSid = accountSid;
        this.accountSidSet = accountSidSet;

        this.errorCode = errorCode;
        this.requestUrl = requestUrl;
        this.messageText = messageText;
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
        this.sortByLog = sortByLog;
        this.sortByErrorCode = sortByErrorCode;
        this.sortByCallSid = sortByCallSid;
        this.sortByMessageText = sortByMessageText;
    }

    public String getSid() {
        return accountSid;
    }

    public List<String> getAccountSidSet() {
        return accountSidSet;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getRequestUrl() {
        return requestUrl;
    }

    public Date getStartTime() {
        return startTime;
    }

    public Date getEndTime() {
        return endTime;
    }

    public String getMessageText() {
        return messageText;
    }

    public Integer getLimit() {
        return limit;
    }

    public Integer getOffset() {
        return offset;
    }

    public String getInstanceid() { return instanceid; }

    public Sorting.Direction getSortByDate() { return sortByDate; }
    public Sorting.Direction getSortByLog() { return sortByLog; }
    public Sorting.Direction getSortByErrorCode() { return sortByErrorCode; }
    public Sorting.Direction getSortByCallSid() { return sortByCallSid; }
    public Sorting.Direction getSortByMessageText() { return sortByMessageText; }

    public static final class Builder {
        private String accountSid = null;
        private List<String> accountSidSet = null;
        private String startTime = null;
        private String endTime = null;
        private String errorCode = null;
        private String requestUrl = null;
        private String messageText = null;
        private String instanceid = null;
        private Sorting.Direction sortByDate = null;
        private Sorting.Direction sortByLog = null;
        private Sorting.Direction sortByErrorCode = null;
        private Sorting.Direction sortByCallSid = null;
        private Sorting.Direction sortByMessageText = null;
        private Integer limit = null;
        private Integer offset = null;

        public static NotificationFilter.Builder builder() {
            return new NotificationFilter.Builder();
        }

        public NotificationFilter build() throws ParseException {
            return new NotificationFilter(accountSid,
                    accountSidSet,
                    startTime,
                    endTime,
                    errorCode,
                    requestUrl,
                    messageText,
                    limit,
                    offset,
                    instanceid,
                    sortByDate,
                    sortByLog,
                    sortByErrorCode,
                    sortByCallSid,
                    sortByMessageText);
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
        public Builder byErrorCode(String errorCode) {
            this.errorCode = errorCode;
            return this;
        }
        public Builder byRequestUrl(String requestUrl) {
            this.requestUrl = requestUrl;
            return this;
        }
        public Builder byMessageText(String messageText) {
            this.messageText = messageText;
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
        public Builder sortedByLog(Sorting.Direction sortDirection) {
            this.sortByLog = sortDirection;
            return this;
        }
        public Builder sortedByErrorCode(Sorting.Direction sortDirection) {
            this.sortByErrorCode = sortDirection;
            return this;
        }
        public Builder sortedByCallSid(Sorting.Direction sortDirection) {
            this.sortByCallSid = sortDirection;
            return this;
        }
        public Builder sortedByMessageText(Sorting.Direction sortDirection) {
            this.sortByMessageText = sortDirection;
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
