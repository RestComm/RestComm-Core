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
package org.restcomm.connect.dao.mybatis;

import java.math.BigDecimal;
import java.net.URI;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Currency;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.restcomm.connect.commons.annotations.concurrency.ThreadSafe;
import org.restcomm.connect.commons.dao.Sid;
import org.restcomm.connect.dao.CallDetailRecordsDao;
import org.restcomm.connect.dao.DaoUtils;
import org.restcomm.connect.dao.entities.CallDetailRecord;
import org.restcomm.connect.dao.entities.CallDetailRecordFilter;

/**
 * @author quintana.thomas@gmail.com (Thomas Quintana)
 */
@ThreadSafe
public final class MybatisCallDetailRecordsDao implements CallDetailRecordsDao {
    private static final String namespace = "org.mobicents.servlet.sip.restcomm.dao.CallDetailRecordsDao.";
    private final SqlSessionFactory sessions;

    public MybatisCallDetailRecordsDao(final SqlSessionFactory sessions) {
        super();
        this.sessions = sessions;
    }

    @Override
    public void addCallDetailRecord(final CallDetailRecord cdr) {
        final SqlSession session = sessions.openSession();
        try {
            session.insert(namespace + "addCallDetailRecord", toMap(cdr));
            session.commit();
        } finally {
            session.close();
        }
    }

    @Override
    public CallDetailRecord getCallDetailRecord(final Sid sid) {
        final SqlSession session = sessions.openSession();
        try {
            final Map<String, Object> result = session.selectOne(namespace + "getCallDetailRecord", sid.toString());
            if (result != null) {
                return toCallDetailRecord(result);
            } else {
                return null;
            }
        } finally {
            session.close();
        }
    }

    // Issue 110
    @Override
    public Integer getTotalCallDetailRecords(CallDetailRecordFilter filter) {
        final SqlSession session = sessions.openSession();
        try {
            final Integer total = session.selectOne(namespace + "getTotalCallDetailRecordByUsingFilters", filter);
            return total;
        } finally {
            session.close();
        }
    }

    @Override
    public Integer getInProgressCallsByClientName(String client) {
        final SqlSession session = sessions.openSession();
        try {
            final Integer total = session.selectOne(namespace + "getInProgressCallsByClientName", client);
            return total;
        } finally {
            session.close();
        }
    }

    @Override
    public Integer getInProgressCallsByAccountSid(String accountSid) {
        final SqlSession session = sessions.openSession();
        try {
            final Integer total = session.selectOne(namespace + "getInProgressCallsByAccountSid", accountSid);
            return total;
        } finally {
            session.close();
        }
    }

    @Override
    public Integer getTotalRunningCallDetailRecordsByConferenceSid(Sid conferenceSid){

        final SqlSession session = sessions.openSession();
        try {
            final Integer total = session.selectOne(namespace + "getTotalRunningCallDetailRecordsByConferenceSid", conferenceSid.toString());
            return total;
        } finally {
            session.close();
        }

    }

    // Issue 153: https://bitbucket.org/telestax/telscale-restcomm/issue/153
    // Issue 110: https://bitbucket.org/telestax/telscale-restcomm/issue/110
    @Override
    public List<CallDetailRecord> getCallDetailRecords(CallDetailRecordFilter filter) {

        final SqlSession session = sessions.openSession();

        try {
            final List<Map<String, Object>> results = session.selectList(namespace + "getCallDetailRecordByUsingFilters",
                    filter);
            final List<CallDetailRecord> cdrs = new ArrayList<CallDetailRecord>();

            if (results != null && !results.isEmpty()) {
                for (final Map<String, Object> result : results) {
                    cdrs.add(toCallDetailRecord(result));
                }
            }
            return cdrs;
        } finally {
            session.close();
        }
    }

    @Override
    public List<CallDetailRecord> getCallDetailRecordsByAccountSid(final Sid accountSid) {
        return getCallDetailRecords(namespace + "getCallDetailRecords", accountSid.toString());
    }

    @Override
    public List<CallDetailRecord> getCallDetailRecordsByRecipient(final String recipient) {
        return getCallDetailRecords(namespace + "getCallDetailRecordsByRecipient", recipient);
    }

    @Override
    public List<CallDetailRecord> getCallDetailRecordsBySender(final String sender) {
        return getCallDetailRecords(namespace + "getCallDetailRecordsBySender", sender);
    }

    @Override
    public List<CallDetailRecord> getCallDetailRecordsByStatus(final String status) {
        return getCallDetailRecords(namespace + "getCallDetailRecordsByStatus", status);
    }

    @Override
    public List<CallDetailRecord> getCallDetailRecordsByStartTime(final DateTime startTime) {
        return getCallDetailRecords(namespace + "getCallDetailRecordsByStartTime", startTime.toDate());
    }

    @Override
    public List<CallDetailRecord> getCallDetailRecordsByEndTime(final DateTime endTime) {
        return getCallDetailRecords(namespace + "getCallDetailRecordsByEndTime", endTime.toDate());
    }

    @Override
    public List<CallDetailRecord> getCallDetailRecordsByStarTimeAndEndTime(final DateTime endTime) {
        return getCallDetailRecords(namespace + "getCallDetailRecordsByStarTimeAndEndTime", endTime.toDate());
    }

    @Override
    public List<CallDetailRecord> getCallDetailRecordsByParentCall(final Sid parentCallSid) {
        return getCallDetailRecords(namespace + "getCallDetailRecordsByParentCall", parentCallSid.toString());
    }

    @Override
    public List<CallDetailRecord> getCallDetailRecordsByConferenceSid(final Sid conferenceSid) {
        return getCallDetailRecords(namespace + "getCallDetailRecordsByConferenceSid", conferenceSid.toString());
    }

    @Override
    public List<CallDetailRecord> getRunningCallDetailRecordsByConferenceSid(final Sid conferenceSid) {
        return getCallDetailRecords(namespace + "getRunningCallDetailRecordsByConferenceSid", conferenceSid.toString());
    }

    @Override
    public List<CallDetailRecord> getCallDetailRecordsByInstanceId(final Sid instanceId) {
        return getCallDetailRecords(namespace + "getCallDetailRecordsByInstanceId", instanceId.toString());
    }

    @Override
    public List<CallDetailRecord> getInCompleteCallDetailRecordsByInstanceId(Sid instanceId) {
        return getCallDetailRecords(namespace + "getInCompleteCallDetailRecordsByInstanceId", instanceId.toString());
    }

    @Override
    public List<CallDetailRecord> getCallDetailRecordsByMsId(String msId) {
        return getCallDetailRecords(namespace + "getCallDetailRecordsByMsId", msId);
    }

    @Override
    public Double getAverageCallDurationLast24Hours(Sid instanceId) throws ParseException {
        DateTime now = DateTime.now();
        DateTimeFormatter fmt = DateTimeFormat.forPattern("yyyy-MM-dd");
        SimpleDateFormat formatter= new SimpleDateFormat("yyyy-MM-dd");

        Date today = formatter.parse(now.toString(fmt));

        Map<String, Object> params = new HashMap<String, Object>();
        params.put("instanceid", instanceId.toString());
        params.put("startTime", today);

        final SqlSession session = sessions.openSession();
        try {
            final Double total = session.selectOne(namespace + "getAverageCallDurationLast24Hours", params);
            return total;
        } finally {
            session.close();
        }
    }

    @Override
    public Double getAverageCallDurationLastHour(Sid instanceId) throws ParseException {
        SimpleDateFormat formatter= new SimpleDateFormat("yyyy-MM-dd HH:00:00");
        String hour = formatter.format(Calendar.getInstance().getTime());
        Date lastHour = formatter.parse(hour);

        Map<String, Object> params = new HashMap<String, Object>();
        params.put("instanceid", instanceId.toString());
        params.put("startTime", lastHour);

        final SqlSession session = sessions.openSession();
        try {
            final Double total = session.selectOne(namespace + "getAverageCallDurationLastHour", params);
            return total;
        } finally {
            session.close();
        }
    }

    private List<CallDetailRecord> getCallDetailRecords(final String selector, Object input) {
        final SqlSession session = sessions.openSession();
        try {
            final List<Map<String, Object>> results = session.selectList(selector, input);
            final List<CallDetailRecord> cdrs = new ArrayList<CallDetailRecord>();
            if (results != null && !results.isEmpty()) {
                for (final Map<String, Object> result : results) {
                    cdrs.add(toCallDetailRecord(result));
                }
            }
            return cdrs;
        } finally {
            session.close();
        }
    }

    @Override
    public void removeCallDetailRecord(final Sid sid) {
        removeCallDetailRecords(namespace + "removeCallDetailRecord", sid);
    }

    @Override
    public void removeCallDetailRecords(final Sid accountSid) {
        removeCallDetailRecords(namespace + "removeCallDetailRecords", accountSid);
    }

    private void removeCallDetailRecords(final String selector, final Sid sid) {
        final SqlSession session = sessions.openSession();
        try {
            session.delete(selector, sid.toString());
            session.commit();
        } finally {
            session.close();
        }
    }

    @Override
    public void updateCallDetailRecord(final CallDetailRecord cdr) {
        final SqlSession session = sessions.openSession();
        try {
            session.update(namespace + "updateCallDetailRecord", toMap(cdr));
            session.commit();
        } finally {
            session.close();
        }
    }

    @Override
    public void updateInCompleteCallDetailRecordsToCompletedByInstanceId(Sid instanceId) {
        final SqlSession session = sessions.openSession();
        try {
            session.update(namespace + "updateInCompleteCallDetailRecordsToCompletedByInstanceId", instanceId.toString());
            session.commit();
        } finally {
            session.close();
        }
    }

    private CallDetailRecord toCallDetailRecord(final Map<String, Object> map) {
        final String msId = DaoUtils.readString(map.get("ms_id"));
        final Sid sid = DaoUtils.readSid(map.get("sid"));
        final String instanceId = DaoUtils.readString(map.get("instanceid"));
        final Sid parentCallSid = DaoUtils.readSid(map.get("parent_call_sid"));
        final Sid conferenceSid = DaoUtils.readSid(map.get("conference_sid"));
        final DateTime dateCreated = DaoUtils.readDateTime(map.get("date_created"));
        final DateTime dateUpdated = DaoUtils.readDateTime(map.get("date_updated"));
        final Sid accountSid = DaoUtils.readSid(map.get("account_sid"));
        final String to = DaoUtils.readString(map.get("recipient"));
        final String from = DaoUtils.readString(map.get("sender"));
        final Sid phoneNumberSid = DaoUtils.readSid(map.get("phone_number_sid"));
        final String status = DaoUtils.readString(map.get("status"));
        final DateTime startTime = DaoUtils.readDateTime(map.get("start_time"));
        final DateTime endTime = DaoUtils.readDateTime(map.get("end_time"));
        final Integer duration = DaoUtils.readInteger(map.get("duration"));
        final Integer ringDuration = DaoUtils.readInteger(map.get("ring_duration"));
        final BigDecimal price = DaoUtils.readBigDecimal(map.get("price"));
        final Currency priceUnit = DaoUtils.readCurrency(map.get("price_unit"));
        final String direction = DaoUtils.readString(map.get("direction"));
        final String answeredBy = DaoUtils.readString(map.get("answered_by"));
        final String apiVersion = DaoUtils.readString(map.get("api_version"));
        final String forwardedFrom = DaoUtils.readString(map.get("forwarded_from"));
        final String callerName = DaoUtils.readString(map.get("caller_name"));
        final URI uri = DaoUtils.readUri(map.get("uri"));
        final String callPath = DaoUtils.readString(map.get("call_path"));
        final Boolean muted = DaoUtils.readBoolean(map.get("muted"));
        final Boolean startConferenceOnEnter = DaoUtils.readBoolean(map.get("start_conference_on_enter"));
        final Boolean endConferenceOnExit = DaoUtils.readBoolean(map.get("end_conference_on_exit"));
        final Boolean onHold = DaoUtils.readBoolean(map.get("on_hold"));
        return new CallDetailRecord(sid, instanceId, parentCallSid, conferenceSid, dateCreated, dateUpdated, accountSid, to, from, phoneNumberSid, status,
                startTime, endTime, duration, price, priceUnit, direction, answeredBy, apiVersion, forwardedFrom, callerName,
                uri, callPath, ringDuration, muted, startConferenceOnEnter, endConferenceOnExit, onHold, msId);
    }

    private Map<String, Object> toMap(final CallDetailRecord cdr) {
        final Map<String, Object> map = new HashMap<String, Object>();
        map.put("sid", DaoUtils.writeSid(cdr.getSid()));
        map.put("instanceid", cdr.getInstanceId());
        map.put("parent_call_sid", DaoUtils.writeSid(cdr.getParentCallSid()));
        map.put("conference_sid", DaoUtils.writeSid(cdr.getConferenceSid()));
        map.put("date_created", DaoUtils.writeDateTime(cdr.getDateCreated()));
        map.put("date_updated", DaoUtils.writeDateTime(cdr.getDateUpdated()));
        map.put("account_sid", DaoUtils.writeSid(cdr.getAccountSid()));
        map.put("to", cdr.getTo());
        map.put("from", cdr.getFrom());
        map.put("phone_number_sid", DaoUtils.writeSid(cdr.getPhoneNumberSid()));
        map.put("status", cdr.getStatus());
        map.put("start_time", DaoUtils.writeDateTime(cdr.getStartTime()));
        map.put("end_time", DaoUtils.writeDateTime(cdr.getEndTime()));
        map.put("duration", cdr.getDuration());
        map.put("ring_duration", cdr.getRingDuration());
        map.put("price", DaoUtils.writeBigDecimal(cdr.getPrice()));
        map.put("direction", cdr.getDirection());
        map.put("answered_by", cdr.getAnsweredBy());
        map.put("api_version", cdr.getApiVersion());
        map.put("forwarded_from", cdr.getForwardedFrom());
        map.put("caller_name", cdr.getCallerName());
        map.put("uri", DaoUtils.writeUri(cdr.getUri()));
        map.put("call_path", cdr.getCallPath());
        map.put("muted", cdr.isMuted());
        map.put("start_conference_on_enter", cdr.isStartConferenceOnEnter());
        map.put("end_conference_on_exit", cdr.isEndConferenceOnExit());
        map.put("on_hold", cdr.isOnHold());
        map.put("ms_id", cdr.getMsId());
        return map;
    }
}
