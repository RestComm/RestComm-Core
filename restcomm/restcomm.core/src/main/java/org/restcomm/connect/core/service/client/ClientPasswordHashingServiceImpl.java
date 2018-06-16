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

package org.restcomm.connect.core.service.client;

import org.restcomm.connect.commons.configuration.RestcommConfiguration;
import org.restcomm.connect.core.service.api.ClientPasswordHashingService;
import org.restcomm.connect.dao.DaoManager;
import org.restcomm.connect.dao.entities.Client;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ClientPasswordHashingServiceImpl implements ClientPasswordHashingService {
    private final DaoManager daoManager;

    public ClientPasswordHashingServiceImpl (DaoManager daoManager) {
        this.daoManager = daoManager;
    }

    @Override
    public Map<String, String> hashClientPassword (List<Client> clients, String domainName) {
        Map<String, String> migratedClients = new HashMap<>();
        for (Client client: clients) {
            String newPasswordAlgorithm = hashClientPassword(client, domainName);
            if (newPasswordAlgorithm != null) {
                migratedClients.put(client.getSid().toString(), newPasswordAlgorithm);
            }
        }
        return migratedClients;
    }

    @Override
    public String hashClientPassword (Client client, String domainName) {
        if (client.getPasswordAlgorithm().equalsIgnoreCase(RestcommConfiguration.getInstance().getMain().getClearTextPasswordAlgorithm())) {
            client = client.setPassword(client.getLogin(), client.getPassword(), domainName);
            daoManager.getClientsDao().updateClient(client);
            return client.getPasswordAlgorithm();
        }
        return null;
    }
}
