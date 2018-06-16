/*
 *  TeleStax, Open Source Cloud Communications
 *  Copyright 2011-2018, Telestax Inc and individual contributors
 *  by the @authors tag.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  under the terms of the GNU Affero General Public License as
 *  published by the Free Software Foundation; either version 3 of
 *  the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>
 */

package org.restcomm.connect.core.service.api;

import org.restcomm.connect.dao.entities.Client;

import java.util.List;
import java.util.Map;

public interface ClientPasswordHashingService {

    /**
     * Will hash the password of all clients in the provided List of Clients
     * @param clients
     * @return will return the List of Clients with hashed password
     */
    Map<String, String> hashClientPassword(List<Client> clients, String domainName);

    /**
     * Will hash the password of the provided client
     * @param client
     * @return will return the Client with hashed password
     */
    String hashClientPassword(Client client, String domainName);
}
