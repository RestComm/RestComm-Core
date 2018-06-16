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

package org.restcomm.connect.sms.smpp;

import com.cloudhopper.smpp.SmppConstants;
import static com.cloudhopper.smpp.SmppConstants.STATUS_DELIVERYFAILURE;
import static com.cloudhopper.smpp.SmppConstants.STATUS_THROTTLED;
import java.util.HashMap;
import java.util.Map;
import org.apache.log4j.Logger;

public class ErrorCodeMapper {
    private static final Logger logger = Logger.getLogger(ErrorCodeMapper.class);
    private static final Map<Integer, org.restcomm.connect.commons.dao.MessageError> errorMap;

    static {
        errorMap = new HashMap<>();
        errorMap.put(STATUS_THROTTLED, org.restcomm.connect.commons.dao.MessageError.LANDLINE_OR_UNREACHABLE_CARRIER);
        errorMap.put(STATUS_DELIVERYFAILURE, org.restcomm.connect.commons.dao.MessageError.LANDLINE_OR_UNREACHABLE_CARRIER);
    }

    public static org.restcomm.connect.commons.dao.MessageError parseRestcommErrorCode(int errCode) {
        org.restcomm.connect.commons.dao.MessageError error = null;
        if (SmppConstants.STATUS_OK == errCode) {
            //set to null so no error is shown
            error = null;
        } else if (errorMap.containsKey(errCode)) {
            error = errorMap.get(errCode);
        } else {
            //if error is not in mapping table, set it to unknown
            error = org.restcomm.connect.commons.dao.MessageError.UNKNOWN_ERROR;
        }
        if (logger.isDebugEnabled()) {
            logger.debug("Mapped to: " + error);
        }
        return error;
    }
}
