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

package org.restcomm.connect.core.service.api;

import java.util.Set;

import org.restcomm.connect.commons.dao.Sid;
import org.restcomm.connect.core.service.number.api.NumberSelectionResult;
import org.restcomm.connect.core.service.number.api.SearchModifier;
import org.restcomm.connect.dao.entities.IncomingPhoneNumber;



public interface NumberSelectorService {
    /**
     * @param phone
     * @param sourceOrganizationSid
     * @param destinationOrganizationSid
     * @return
     */
    IncomingPhoneNumber searchNumber(String phone,
            Sid sourceOrganizationSid, Sid destinationOrganizationSid);

    /**
     * @param phone
     * @param sourceOrganizationSid
     * @param destinationOrganizationSid
     * @param modifiers
     * @return
     */
    IncomingPhoneNumber searchNumber(String phone,
            Sid sourceOrganizationSid, Sid destinationOrganizationSid, Set<SearchModifier> modifiers);

    /**
     * The main logic is: -Find a perfect match in DB using different formats.
     * -If not matched, use available Regexes in the organization. -If not
     * matched, try with the special * match.
     *
     * @param phone
     * @param sourceOrganizationSid
     * @param destinationOrganizationSid
     * @return
     */
    NumberSelectionResult searchNumberWithResult(String phone,
            Sid sourceOrganizationSid, Sid destinationOrganizationSid);

    /**
     * The main logic is: -Find a perfect match in DB using different formats.
     * -If not matched, use available Regexes in the organization. -If not
     * matched, try with the special * match.
     *
     * @param phone
     * @param sourceOrganizationSid
     * @param destinationOrganizationSid
     * @param modifiers
     * @return
     */
    NumberSelectionResult searchNumberWithResult(String phone,
            Sid sourceOrganizationSid, Sid destinationOrganizationSid, Set<SearchModifier> modifiers);

    /**
     *
     * @param result whether the call should be rejected depending on results
     * found
     * @param srcOrg
     * @param destOrg
     * @return
     */
    boolean isFailedCall(NumberSelectionResult result, Sid srcOrg, Sid destOrg);
}
