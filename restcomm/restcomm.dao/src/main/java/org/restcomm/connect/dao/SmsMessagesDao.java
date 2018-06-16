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
package org.restcomm.connect.dao;

import org.restcomm.connect.commons.dao.Sid;
import org.restcomm.connect.dao.entities.SmsMessage;
import org.restcomm.connect.dao.entities.SmsMessageFilter;

import java.text.ParseException;
import java.util.List;

/**
 * @author quintana.thomas@gmail.com (Thomas Quintana)
 */
public interface SmsMessagesDao {
    void addSmsMessage(SmsMessage smsMessage);

    SmsMessage getSmsMessage(Sid sid);

    /**
     * @param smppMessageId
     * @return
     */
    SmsMessage getSmsMessageBySmppMessageId(String smppMessageId);

    List<SmsMessage> getSmsMessages(final Sid accountSid);

    void removeSmsMessage(Sid sid);

    void removeSmsMessages(Sid accountSid);

    void updateSmsMessage(SmsMessage smsMessage);

    int getSmsMessagesPerAccountLastPerMinute(String accountSid) throws ParseException;

    // Support for filtering of message list result, Issue 1395
    Integer getTotalSmsMessage(SmsMessageFilter filter);
    List<SmsMessage> getSmsMessages(SmsMessageFilter filter);

    List<SmsMessage> findBySmppMessageId(String smppMessageId);
}
