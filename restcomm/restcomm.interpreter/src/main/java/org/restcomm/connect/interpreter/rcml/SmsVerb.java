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
package org.restcomm.connect.interpreter.rcml;

import java.net.URI;
import org.restcomm.connect.dao.entities.SmsMessage;

public class SmsVerb {

    public static String ACTION_ATT = "action";
    public static String TO_ATT = "to";
    public static String FROM_ATT = "from";
    public static String METHOD_ATT = "method";

    public static void populateAttributes(Tag verb, SmsMessage.Builder builder) {
        if (verb.hasAttribute(SmsVerb.ACTION_ATT)) {
            final URI callback = URI.create(verb.attribute(SmsVerb.ACTION_ATT).value());
            builder.setStatusCallback(callback);
            if (verb.hasAttribute(SmsVerb.METHOD_ATT)){
                builder.setStatusCallbackMethod(verb.attribute(SmsVerb.METHOD_ATT).value());
            }
        }
    }

}
