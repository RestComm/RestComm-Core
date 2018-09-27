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

import org.restcomm.connect.commons.dao.Sid;
import org.restcomm.connect.dao.entities.MediaAttributes;

import java.net.URI;

public interface RecordingService {

    /**
     * Upload recording file to Amazon S3
     * @param recordingSid
     * @param mediaType
     * @return
     */
    URI storeRecording(Sid recordingSid, MediaAttributes.MediaType mediaType);

    /**
     * Prepare Recording URL to store in the Recording.fileUrl property.
     * This will be used later to access the recording file
     * @param apiVersion
     * @param accountSid
     * @param recordingSid
     * @param mediaType
     * @return
     */
    URI prepareFileUrl (String apiVersion, String accountSid, String recordingSid, MediaAttributes.MediaType mediaType);

    /**
     *Remove recording file from Amazon S3
     * @param recordingSid
     */
    void removeRecording(Sid recordingSid);

}
