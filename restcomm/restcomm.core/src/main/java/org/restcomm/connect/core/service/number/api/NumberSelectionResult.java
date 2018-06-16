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
package org.restcomm.connect.core.service.number.api;

import org.restcomm.connect.dao.entities.IncomingPhoneNumber;

public class NumberSelectionResult {

    IncomingPhoneNumber number;
    Boolean organizationFiltered = false;
    ResultType type;

    public NumberSelectionResult(IncomingPhoneNumber number, Boolean organizationFiltered, ResultType type) {
        this.number = number;
        this.organizationFiltered = organizationFiltered;
        this.type = type;
    }

    public IncomingPhoneNumber getNumber() {
        return number;
    }

    public Boolean getOrganizationFiltered() {
        return organizationFiltered;
    }

    public void setOrganizationFiltered(Boolean organizationFiltered) {
        this.organizationFiltered = organizationFiltered;
    }

    public ResultType getType() {
        return type;
    }

    public void setType(ResultType type) {
        this.type = type;
    }


}