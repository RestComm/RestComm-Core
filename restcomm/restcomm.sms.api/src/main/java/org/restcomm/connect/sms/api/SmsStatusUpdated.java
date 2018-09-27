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


package org.restcomm.connect.sms.api;

import org.restcomm.connect.commons.annotations.concurrency.Immutable;

/**
 * This event is triggered when a message status has been updated from the network.
 *
 * @author
 */
@Immutable
public final class SmsStatusUpdated {
    private final SmsSessionInfo info;

    public SmsStatusUpdated(SmsSessionInfo info) {
        this.info = info;
    }

    public SmsSessionInfo getInfo() {
        return info;
    }

}
