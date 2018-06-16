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

package org.restcomm.connect.commons.configuration.sets;

import org.restcomm.connect.commons.common.http.SslMode;

import java.net.InetSocketAddress;
import java.util.Map;

/**
 * @author orestis.tsakiridis@telestax.com - Orestis Tsakiridis
 */
public interface MainConfigurationSet {
    SslMode getSslMode();

    int getResponseTimeout();

    Integer getDefaultHttpConnectionRequestTimeout();

    Integer getDefaultHttpMaxConns();

    Integer getDefaultHttpMaxConnsPerRoute();

    Integer getDefaultHttpTTL();

    Map<InetSocketAddress,Integer> getDefaultHttpRoutes();

    boolean isUseHostnameToResolveRelativeUrls();

    String getHostname();

    boolean getBypassLbForClients();

    void setInstanceId(String instanceId);

    String getInstanceId();

    String getApiVersion();

    int getRecordingMaxDelay ();

    long getConferenceTimeout();

    void setConferenceTimeout(long conferenceTimeout);

    String getClientAlgorithm();

    String getClientQOP();

    String getClearTextPasswordAlgorithm();

    String getRecordingPath();
}
