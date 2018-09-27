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

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.joda.time.DateTime;
import org.restcomm.connect.commons.annotations.concurrency.ThreadSafe;
import org.restcomm.connect.commons.dao.Sid;
import org.restcomm.connect.commons.dao.MessageError;
import org.restcomm.connect.dao.SmsMessagesDao;
import org.restcomm.connect.dao.entities.SmsMessage;
import org.restcomm.connect.dao.entities.SmsMessageFilter;

import java.math.BigDecimal;
import java.net.URI;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Currency;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.restcomm.connect.dao.DaoUtils.readBigDecimal;
import static org.restcomm.connect.dao.DaoUtils.readInteger;
import static org.restcomm.connect.dao.DaoUtils.readCurrency;
import static org.restcomm.connect.dao.DaoUtils.readDateTime;
import static org.restcomm.connect.dao.DaoUtils.readSid;
import static org.restcomm.connect.dao.DaoUtils.readString;
import static org.restcomm.connect.dao.DaoUtils.readUri;
import static org.restcomm.connect.dao.DaoUtils.writeBigDecimal;
import static org.restcomm.connect.dao.DaoUtils.writeCurrency;
import static org.restcomm.connect.dao.DaoUtils.writeDateTime;
import static org.restcomm.connect.dao.DaoUtils.writeSid;
import static org.restcomm.connect.dao.DaoUtils.writeUri;

/**
 * @author quintana.thomas@gmail.com (Thomas Quintana)
 * @author mariafarooq
 */
@ThreadSafe
public final class MybatisSmsMessagesDao implements SmsMessagesDao {
    private static final String namespace = "org.mobicents.servlet.sip.restcomm.dao.SmsMessagesDao.";
    private final SqlSessionFactory sessions;

    private static final String STATUS_CALLBACK_COL = "status_callback";
    private static final String STATUS_CALLBACK_METHOD_COL = "status_callback_method";

    public MybatisSmsMessagesDao(final SqlSessionFactory sessions) {
        super();
        this.sessions = sessions;
    }

    @Override
    public void addSmsMessage(final SmsMessage smsMessage) {
        final SqlSession session = sessions.openSession();
        try {
            session.insert(namespace + "addSmsMessage", toMap(smsMessage));
            session.commit();
        } finally {
            session.close();
        }
    }

    @Override
    public SmsMessage getSmsMessage(final Sid sid) {
        final SqlSession session = sessions.openSession();
        try {
            final Map<String, Object> result = session.selectOne(namespace + "getSmsMessage", sid.toString());
            if (result != null) {
                return toSmsMessage(result);
            } else {
                return null;
            }
        } finally {
            session.close();
        }
    }

    /* (non-Javadoc)
     * @see org.restcomm.connect.dao.SmsMessagesDao#getSmsMessageBySmppMessageId(java.lang.String)
     */
    @Override
    public SmsMessage getSmsMessageBySmppMessageId(final String smppMessageId) {
        final SqlSession session = sessions.openSession();
        try {
            final Map<String, Object> result = session.selectOne(namespace + "getSmsMessageBySmppMessageId", smppMessageId);
            if (result != null) {
                return toSmsMessage(result);
            } else {
                return null;
            }
        } finally {
            session.close();
        }
    }

    @Override
    public List<SmsMessage> getSmsMessages(final Sid accountSid) {
        final SqlSession session = sessions.openSession();
        try {
            final List<Map<String, Object>> results = session.selectList(namespace + "getSmsMessages", accountSid.toString());
            final List<SmsMessage> smsMessages = new ArrayList<SmsMessage>();
            if (results != null && !results.isEmpty()) {
                for (final Map<String, Object> result : results) {
                    smsMessages.add(toSmsMessage(result));
                }
            }
            return smsMessages;
        } finally {
            session.close();
        }
    }

    @Override
    public List<SmsMessage> getSmsMessages(SmsMessageFilter filter) {

        final SqlSession session = sessions.openSession();

        try {
            final List<Map<String, Object>> results = session.selectList(namespace + "getSmsMessagesByUsingFilters",
                    filter);
            final List<SmsMessage> cdrs = new ArrayList<SmsMessage>();

            if (results != null && !results.isEmpty()) {
                for (final Map<String, Object> result : results) {
                    cdrs.add(toSmsMessage(result));
                }
            }
            return cdrs;
        } finally {
            session.close();
        }
    }

    @Override
    public Integer getTotalSmsMessage(SmsMessageFilter filter) {
        final SqlSession session = sessions.openSession();
        try {
            final Integer total = session.selectOne(namespace + "getTotalSmsMessageByUsingFilters", filter);
            return total;
        } finally {
            session.close();
        }
    }

    @Override
    public void removeSmsMessage(final Sid sid) {
        deleteSmsMessage(namespace + "removeSmsMessage", sid);
    }

    @Override
    public void removeSmsMessages(final Sid accountSid) {
        deleteSmsMessage(namespace + "removeSmsMessages", accountSid);
    }

    private void deleteSmsMessage(final String selector, final Sid sid) {
        final SqlSession session = sessions.openSession();
        try {
            session.delete(selector, sid.toString());
            session.commit();
        } finally {
            session.close();
        }
    }

    public void updateSmsMessage(final SmsMessage smsMessage) {
        final SqlSession session = sessions.openSession();
        try {
            session.update(namespace + "updateSmsMessage", toMap(smsMessage));
            session.commit();
        } finally {
            session.close();
        }
    }

    @Override
    public int getSmsMessagesPerAccountLastPerMinute(String accountSid) throws ParseException {
        SimpleDateFormat formatter= new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String date = formatter.format(DateTime.now().minusSeconds(60).toDate());

        Map<String, Object> params = new HashMap<String, Object>();
        params.put("start_time", date);
        params.put("account_sid", accountSid);

        final SqlSession session = sessions.openSession();
        try {
            final int total = session.selectOne(namespace + "getSmsMessagesPerAccountLastPerMinute", params);
            return total;
        } finally {
            session.close();
        }
    }

    @Override
    public List<SmsMessage> findBySmppMessageId(String smppMessageId) {
        final Map<String, Object> parameters = new HashMap<>(2);
        parameters.put("smppMessageId", smppMessageId);
        final SqlSession session = this.sessions.openSession();

        try {
            final List<Map<String, Object>> results = session.selectList(namespace + "findBySmppMessageId", parameters);
            final List<SmsMessage> messages = new ArrayList<>(results.size());

            for (Map<String, Object> result : results) {
                messages.add(toSmsMessage(result));
            }
            return messages;
        } finally {
            session.close();
        }
    }

    private Map<String, Object> toMap(final SmsMessage smsMessage) {
        final Map<String, Object> map = new HashMap<String, Object>();
        map.put("sid", writeSid(smsMessage.getSid()));
        map.put("date_created", writeDateTime(smsMessage.getDateCreated()));
        map.put("date_updated", writeDateTime(smsMessage.getDateUpdated()));
        map.put("date_sent", writeDateTime(smsMessage.getDateSent()));
        map.put("account_sid", writeSid(smsMessage.getAccountSid()));
        map.put("sender", smsMessage.getSender());
        map.put("recipient", smsMessage.getRecipient());
        map.put("body", smsMessage.getBody());
        map.put("status", smsMessage.getStatus().toString());
        map.put("direction", smsMessage.getDirection().toString());
        map.put("price", writeBigDecimal(smsMessage.getPrice()));
        map.put("price_unit", writeCurrency(smsMessage.getPriceUnit()));
        map.put("api_version", smsMessage.getApiVersion());
        map.put("uri", writeUri(smsMessage.getUri()));
        map.put("smpp_message_id", smsMessage.getSmppMessageId());
        map.put(STATUS_CALLBACK_COL, writeUri(smsMessage.getStatusCallback()));
        map.put(STATUS_CALLBACK_METHOD_COL, smsMessage.getStatusCallbackMethod());
        MessageError error = smsMessage.getError();
        if(error != null) {
            map.put("error_code", smsMessage.getError().getErrorCode());
        }
        return map;
    }

    private SmsMessage toSmsMessage(final Map<String, Object> map) {
        final Sid sid = readSid(map.get("sid"));
        final DateTime dateCreated = readDateTime(map.get("date_created"));
        final DateTime dateUpdated = readDateTime(map.get("date_updated"));
        final DateTime dateSent = readDateTime(map.get("date_sent"));
        final Sid accountSid = readSid(map.get("account_sid"));
        final String sender = readString(map.get("sender"));
        final String recipient = readString(map.get("recipient"));
        final String body = readString(map.get("body"));
        final SmsMessage.Status status = SmsMessage.Status.getStatusValue(readString(map.get("status")));
        final SmsMessage.Direction direction = SmsMessage.Direction.getDirectionValue(readString(map.get("direction")));
        final BigDecimal price = readBigDecimal(map.get("price"));
        final Currency priceUnit = readCurrency(map.get("price_unit"));
        final String apiVersion = readString(map.get("api_version"));
        final URI uri = readUri(map.get("uri"));
        final String smppMessageId = readString(map.get("smpp_message_id"));
        final URI statusCallback = readUri(map.get(STATUS_CALLBACK_COL));
        final String statusCallbackMethod = readString(map.get(STATUS_CALLBACK_METHOD_COL));
        final Integer errorCode = readInteger(map.get("error_code"));
        MessageError error = null;
        if(errorCode != null) {
            error = MessageError.getErrorValue(errorCode);
        }
        return new SmsMessage(sid, dateCreated, dateUpdated, dateSent, accountSid,
                sender, recipient, body, status, direction,
                price, priceUnit, apiVersion, uri, smppMessageId, error,
                statusCallback, statusCallbackMethod);
    }
}
