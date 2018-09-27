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

import akka.dispatch.Futures;
import org.apache.log4j.Logger;
import org.restcomm.connect.commons.amazonS3.S3AccessTool;
import org.restcomm.connect.commons.configuration.RestcommConfiguration;
import org.restcomm.connect.commons.dao.Sid;
import org.restcomm.connect.core.service.api.RecordingService;
import org.restcomm.connect.core.service.util.UriUtils;
import org.restcomm.connect.dao.RecordingsDao;
import org.restcomm.connect.dao.entities.MediaAttributes;
import org.restcomm.connect.dao.entities.Recording;
import scala.concurrent.ExecutionContext;
import scala.concurrent.Future;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.concurrent.Callable;

public class RecordingsServiceImpl implements RecordingService {

    private static Logger logger = Logger.getLogger(RecordingsServiceImpl.class);

    private final RecordingsDao recordingsDao;
    private final S3AccessTool s3AccessTool;
    private String recordingsPath;
    private final ExecutionContext ec;
    private final UriUtils uriUtils;

    public RecordingsServiceImpl (RecordingsDao recordingsDao, S3AccessTool s3AccessTool, ExecutionContext ec, UriUtils uriUtils) {
        this.recordingsDao = recordingsDao;
        this.s3AccessTool = s3AccessTool;
        this.recordingsPath = RestcommConfiguration.getInstance().getMain().getRecordingPath();
        this.ec = ec;
        this.uriUtils = uriUtils;
    }

    //Used for unit testing
    public RecordingsServiceImpl (RecordingsDao recordingsDao, String recordingsPath, S3AccessTool s3AccessTool,  ExecutionContext ec, UriUtils uriUtils) {
        this.recordingsDao = recordingsDao;
        this.s3AccessTool = s3AccessTool;
        this.recordingsPath = recordingsPath;
        this.ec = ec;
        this.uriUtils = uriUtils;
    }

    @Override
    public URI storeRecording (final Sid recordingSid, MediaAttributes.MediaType mediaType) {
        URI s3Uri = null;
        final String fileExtension = mediaType.equals(MediaAttributes.MediaType.AUDIO_ONLY) ? ".wav" : ".mp4";
        Recording recording = recordingsDao.getRecording(recordingSid);
        if (s3AccessTool != null && ec != null) {
            s3Uri = s3AccessTool.getS3Uri(recordingsPath+"/"+recordingSid+fileExtension);
            Future<Boolean> f = Futures.future(new Callable<Boolean>() {
                @Override
                public Boolean call () throws Exception {
                    return s3AccessTool.uploadFile(recordingsPath+"/"+recordingSid+fileExtension);
                }
            }, ec);
        }

        return s3Uri;
    }

    @Override
    public URI prepareFileUrl(String apiVersion, String accountSid, String recordingSid, MediaAttributes.MediaType mediaType) {
        final String fileExtension = mediaType.equals(MediaAttributes.MediaType.AUDIO_ONLY) ? ".wav" : ".mp4";
        String fileUrl = String.format("/restcomm/%s/Accounts/%s/Recordings/%s",apiVersion,accountSid,recordingSid);
        URI uriToResolve = null;
        try {
            //For local stored recordings, add .wav/.mp4 suffix to the URI
            uriToResolve = new URI(fileUrl+fileExtension);
        } catch (URISyntaxException e) {
            logger.error(e);
        }
        return uriUtils.resolve(uriToResolve, new Sid(accountSid));
    }

    @Override
    public void removeRecording (Sid recordingSid) {
        Recording recording = recordingsDao.getRecording(recordingSid);

        boolean isStoredAtS3 = recording.getS3Uri() != null;

        if (isStoredAtS3) {
            if (s3AccessTool != null) {
                s3AccessTool.removeS3Uri(recordingSid.toString());
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
