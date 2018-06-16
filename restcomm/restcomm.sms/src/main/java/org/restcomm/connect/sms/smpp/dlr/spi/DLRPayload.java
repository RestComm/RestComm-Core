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

package org.restcomm.connect.sms.smpp.dlr.spi;

import org.joda.time.DateTime;
import org.restcomm.connect.dao.entities.SmsMessage;
import org.restcomm.connect.commons.dao.MessageError;


public class DLRPayload {
    private String id;
    private String sub;
    private String dlvrd;
    private DateTime submitDate;
    private DateTime doneDate;
    private SmsMessage.Status stat;
    private MessageError err;
    private String text;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSub() {
        return sub;
    }

    public void setSub(String sub) {
        this.sub = sub;
    }

    public String getDlvrd() {
        return dlvrd;
    }

    public void setDlvrd(String dlvrd) {
        this.dlvrd = dlvrd;
    }

    public DateTime getSubmitDate() {
        return submitDate;
    }

    public void setSubmitDate(DateTime submitDate) {
        this.submitDate = submitDate;
    }

    public DateTime getDoneDate() {
        return doneDate;
    }

    public void setDoneDate(DateTime doneDate) {
        this.doneDate = doneDate;
    }

    public SmsMessage.Status getStat() {
        return stat;
    }

    public void setStat(SmsMessage.Status stat) {
        this.stat = stat;
    }

    public MessageError getErr() {
        return err;
    }

    public void setErr(MessageError err) {
        this.err = err;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    @Override
    public String toString() {
        return "DLRPayload{" + "id=" + id + ", sub=" + sub + ", dlvrd=" + dlvrd + ", submitDate=" + submitDate + ", doneDate=" + doneDate + ", stat=" + stat + ", err=" + err + ", text=" + text + '}';
    }

}
