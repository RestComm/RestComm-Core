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
package org.restcomm.connect.sms;

import java.util.ArrayList;
import java.util.List;
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.restcomm.connect.dao.entities.SmsMessage;

public class SmsStatusNotifier {

    private static final String MSG_STATUS_PARAM = "MessageStatus";
    private static final String MSG_ID_PARAM = "MessageSid";
    private static final String ERROR_CODE_PARAM = "ErrorCode";
    private static final String ACCOUT_SID_PARAM = "AccountSid";
    private static final String FROM_PARAM = "From";
    private static final String TO_PARAM = "To";
    private static final String BODY_PARAM = "Body";

    static List<NameValuePair> populateReqParams(SmsMessage message) {
        List<NameValuePair> parameters = new ArrayList<>();
        parameters.add(new BasicNameValuePair(FROM_PARAM, message.getSender()));
        parameters.add(new BasicNameValuePair(TO_PARAM, message.getRecipient()));
        parameters.add(new BasicNameValuePair(BODY_PARAM, message.getBody()));
        parameters.add(new BasicNameValuePair(ACCOUT_SID_PARAM, message.getAccountSid().toString()));
        if (message.getError() != null ) {
            parameters.add(new BasicNameValuePair(ERROR_CODE_PARAM, message.getError().toString()));
        }
        parameters.add(new BasicNameValuePair(MSG_ID_PARAM, message.getSid().toString()));
        parameters.add(new BasicNameValuePair(MSG_STATUS_PARAM, message.getStatus().toString()));
        parameters.add(new BasicNameValuePair(BODY_PARAM, message.getBody()));
        return parameters;
    }

}
