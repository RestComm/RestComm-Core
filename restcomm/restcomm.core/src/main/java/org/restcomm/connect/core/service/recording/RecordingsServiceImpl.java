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

package org.restcomm.connect.core.service.recording;

import org.apache.log4j.Logger;
import org.restcomm.connect.commons.amazonS3.S3AccessTool;
import org.restcomm.connect.commons.configuration.RestcommConfiguration;
import org.restcomm.connect.commons.dao.Sid;
import org.restcomm.connect.core.service.api.RecordingService;
import org.restcomm.connect.dao.RecordingsDao;
import org.restcomm.connect.dao.entities.Recording;

import java.io.File;
import java.net.URI;

public class RecordingsServiceImpl implements RecordingService {

    private static Logger logger = Logger.getLogger(RecordingsServiceImpl.class);

    private final RecordingsDao recordingsDao;
    private final S3AccessTool s3AccessTool;
    private String recordingsPath;

    public RecordingsServiceImpl (RecordingsDao recordingsDao, S3AccessTool s3AccessTool) {
        this.recordingsDao = recordingsDao;
        this.s3AccessTool = s3AccessTool;
        this.recordingsPath = RestcommConfiguration.getInstance().getMain().getRecordingPath();
    }

    //Used for unit testing
    public RecordingsServiceImpl (RecordingsDao recordingsDao, S3AccessTool s3AccessTool, String recordingsPath) {
        this.recordingsDao = recordingsDao;
        this.s3AccessTool = s3AccessTool;
        this.recordingsPath = recordingsPath;
    }

    @Override
    public void removeRecording (Sid recordingSid) {
        Recording recording = recordingsDao.getRecording(recordingSid);

        boolean isStoredAtS3 = recording.getS3Uri() != null;

        if (isStoredAtS3) {
            if (s3AccessTool != null) {
                s3AccessTool.removeS3Uri(recording.getS3Uri());
            }
        } else {
            if (!recordingsPath.endsWith("/"))
                recordingsPath += "/";
            URI recordingsUri = URI.create(recordingsPath+recordingSid+".wav");
            File fileToDelete = new File(recordingsUri);

            if (fileToDelete.exists()) {
                fileToDelete.delete();
            }
        }

        recordingsDao.removeRecording(recordingSid);
    }

}
