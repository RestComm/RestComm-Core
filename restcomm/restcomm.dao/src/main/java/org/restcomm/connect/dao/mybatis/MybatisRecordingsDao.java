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
package org.restcomm.connect.dao.mybatis;

import akka.dispatch.Futures;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.joda.time.DateTime;
import org.restcomm.connect.commons.amazonS3.S3AccessTool;
import org.restcomm.connect.commons.annotations.concurrency.ThreadSafe;
import org.restcomm.connect.commons.dao.Sid;
import org.restcomm.connect.commons.util.UriUtils;
import org.restcomm.connect.dao.DaoUtils;
import org.restcomm.connect.dao.RecordingsDao;
import org.restcomm.connect.dao.entities.MediaAttributes;
import org.restcomm.connect.dao.entities.Recording;
import org.restcomm.connect.dao.entities.RecordingFilter;
import scala.concurrent.ExecutionContext;
import scala.concurrent.Future;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

/**
 * @author quintana.thomas@gmail.com (Thomas Quintana)
 * @author maria-farooq@live.com (Maria Farooq)
 */
@ThreadSafe
public final class MybatisRecordingsDao implements RecordingsDao {
    private static final String namespace = "org.mobicents.servlet.sip.restcomm.dao.RecordingsDao.";
    private final SqlSessionFactory sessions;
    private S3AccessTool s3AccessTool;
    private String recordingPath;
    private ExecutionContext ec;

    public MybatisRecordingsDao(final SqlSessionFactory sessions) {
        super();
        this.sessions = sessions;
    }

    public MybatisRecordingsDao(final SqlSessionFactory sessions, final S3AccessTool s3AccessTool, final String recordingPath, final ExecutionContext ec) {
        super();
        this.sessions = sessions;
        this.s3AccessTool = s3AccessTool;
        this.recordingPath = recordingPath;
        this.ec = ec;
    }

    @Override
    public void addRecording(Recording recording, MediaAttributes.MediaType mediaType) {
        final String fileExtension = mediaType.equals(MediaAttributes.MediaType.AUDIO_ONLY) ? ".wav" : ".mp4";
        if (s3AccessTool != null && ec != null) {
            final String recordingSid = recording.getSid().toString();
            URI s3Uri = s3AccessTool.getS3Uri(recordingPath+"/"+recordingSid+fileExtension);
            //s3AccessTool.uploadFile(recordingPath+"/"+recording.getSid().toString()+fileExtension);
            if (s3Uri != null) {
                recording = recording.setS3Uri(s3Uri);
            }
            Future<Boolean> f = Futures.future(new Callable<Boolean>() {
                @Override
                public Boolean call () throws Exception {
                    return s3AccessTool.uploadFile(recordingPath+"/"+recordingSid+fileExtension);
                }
            }, ec);
        }
        String fileUrl = String.format("/restcomm/%s/Accounts/%s/Recordings/%s",recording.getApiVersion(),recording.getAccountSid(),recording.getSid());
        recording = recording.updateFileUri(generateLocalFileUri(fileUrl, fileExtension));
        final SqlSession session = sessions.openSession();
        try {
            session.insert(namespace + "addRecording", toMap(recording));
            session.commit();
        } finally {
            session.close();
        }
    }

    public URI generateLocalFileUri(String recordingRelativeUri, String fileExtension) {
        URI uriToResolve = null;
        try {
            //For local stored recordings, add .wav/.mp4 suffix to the URI
            uriToResolve = new URI(recordingRelativeUri+fileExtension);
        } catch (URISyntaxException e) {}
        return UriUtils.resolve(uriToResolve);
    }

    @Override
    public Recording getRecording(final Sid sid) {
        return getRecording(namespace + "getRecording", sid);
    }

    @Override
    public Recording getRecordingByCall(final Sid callSid) {
        return getRecording(namespace + "getRecordingByCall", callSid);
    }

    @Override
    public List<Recording> getRecordingsByCall(Sid callSid) {
        final SqlSession session = sessions.openSession();
        try {
            final List<Map<String, Object>> results = session.selectList(namespace + "getRecordingsByCall", callSid.toString());
            final List<Recording> recordings = new ArrayList<Recording>();
            if (results != null && !results.isEmpty()) {
                for (final Map<String, Object> result : results) {
                    recordings.add(toRecording(result));
                }
            }
            return recordings;
        } finally {
            session.close();
        }
    }

    private Recording getRecording(final String selector, final Sid sid) {
        final SqlSession session = sessions.openSession();
        try {
            final Map<String, Object> result = session.selectOne(selector, sid.toString());
            if (result != null) {
                return toRecording(result);
            } else {
                return null;
            }
        } finally {
            session.close();
        }
    }

    @Override
    public List<Recording> getRecordings(final Sid accountSid) {
        final SqlSession session = sessions.openSession();
        try {
            final List<Map<String, Object>> results = session.selectList(namespace + "getRecordings", accountSid.toString());
            final List<Recording> recordings = new ArrayList<Recording>();
            if (results != null && !results.isEmpty()) {
                for (final Map<String, Object> result : results) {
                    recordings.add(toRecording(result));
                }
            }
            return recordings;
        } finally {
            session.close();
        }
    }

    @Override
    public List<Recording> getRecordings(RecordingFilter filter) {

        final SqlSession session = sessions.openSession();

        try {
            final List<Map<String, Object>> results = session.selectList(namespace + "getRecordingsByUsingFilters",
                    filter);
            final List<Recording> cdrs = new ArrayList<Recording>();

            if (results != null && !results.isEmpty()) {
                for (final Map<String, Object> result : results) {
                    cdrs.add(toRecording(result));
                }
            }
            return cdrs;
        } finally {
            session.close();
        }
    }

    @Override
    public Integer getTotalRecording(RecordingFilter filter) {
        final SqlSession session = sessions.openSession();
        try {
            final Integer total = session.selectOne(namespace + "getTotalRecordingByUsingFilters", filter);
            return total;
        } finally {
            session.close();
        }
    }

    @Override
    public void removeRecording(final Sid sid) {
        removeRecording(namespace + "removeRecording", sid);
    }

    @Override
    public void removeRecordings(final Sid accountSid) {
        removeRecording(namespace + "removeRecordings", accountSid);
    }

    private void removeRecording(final String selector, final Sid sid) {
        final SqlSession session = sessions.openSession();
        try {
            session.delete(selector, sid.toString());
            session.commit();
        } finally {
            session.close();
        }
    }

    @Override
    public void updateRecording(final Recording recording) {
        final SqlSession session = sessions.openSession();
        try {
            session.update(namespace + "updateRecording", toMap(recording));
            session.commit();
        } finally {
            session.close();
        }
    }

    private Map<String, Object> toMap(final Recording recording) {
        final Map<String, Object> map = new HashMap<String, Object>();
        map.put("sid", DaoUtils.writeSid(recording.getSid()));
        map.put("date_created", DaoUtils.writeDateTime(recording.getDateCreated()));
        map.put("date_updated", DaoUtils.writeDateTime(recording.getDateUpdated()));
        map.put("account_sid", DaoUtils.writeSid(recording.getAccountSid()));
        map.put("call_sid", DaoUtils.writeSid(recording.getCallSid()));
        map.put("duration", recording.getDuration());
        map.put("api_version", recording.getApiVersion());
        map.put("uri", DaoUtils.writeUri(recording.getUri()));
        map.put("file_uri", DaoUtils.writeUri(recording.getFileUri()));
        if (recording.getS3Uri() != null) {
            map.put("s3_uri", DaoUtils.writeUri(recording.getS3Uri()));
        } else {
            map.put("s3_uri", null);
        }
        return map;
    }

    private Recording toRecording(final Map<String, Object> map) {
        Recording recording = null;
        boolean update = false;
        final Sid sid = DaoUtils.readSid(map.get("sid"));
        final DateTime dateCreated = DaoUtils.readDateTime(map.get("date_created"));
        DateTime dateUpdated = DaoUtils.readDateTime(map.get("date_updated"));
        final Sid accountSid = DaoUtils.readSid(map.get("account_sid"));
        final Sid callSid = DaoUtils.readSid(map.get("call_sid"));
        final Double duration = DaoUtils.readDouble(map.get("duration"));
        final String apiVersion = DaoUtils.readString(map.get("api_version"));
        final URI uri = DaoUtils.readUri(map.get("uri"));
        //For backward compatibility. For old an database that we upgraded to the latest schema, the file_uri will be null so we need
        //to create the file_uri on the fly
        String fileUri = (String) map.get("file_uri");
        if (fileUri == null || fileUri.isEmpty()) {
            String file = String.format("/restcomm/%s/Accounts/%s/Recordings/%s",apiVersion,accountSid,sid);
            fileUri = generateLocalFileUri(file, ".wav").toString();
        }

        // fileUri: http://192.168.1.190:8080/restcomm/2012-04-24/Accounts/ACae6e420f425248d6a26948c17a9e2acf/Recordings/RE4c9c09908b60402c8c0a77e24313f27d.wav
        // s3Uri: https://gvagrestcomm.s3.amazonaws.com/RE4c9c09908b60402c8c0a77e24313f27d.wav
        // old S3URI: https://s3.amazonaws.com/restcomm-as-a-service/logs/RE7ddbd5b441574e4ab786a1fddf33eb47.wav?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Date=20170209T103950Z&X-Amz-SignedHeaders=host&X-Amz-Expires=604800&X-Amz-Credential=AKIAIRG5NINXKJAJM5DA%2F20170209%2Fus-east-1%2Fs3%2Faws4_request&X-Amz-Signature=b3da2acc17ee9c6aca4cd151e154d94f530670850f0fcade2422f85d1c7cc992
        String s3Uri = (String) map.get("s3_uri");
        if (fileUri.contains("s3.amazonaws.com") && s3AccessTool != null) {
            update = true;
            dateUpdated = DateTime.now();
            String tempUri = fileUri;
            String file = String.format("/restcomm/%s/Accounts/%s/Recordings/%s",apiVersion,accountSid,sid);
            String fileExtension = null;
            URI oldS3Uri = null;
            try {
                oldS3Uri = new URI(tempUri);
            } catch (URISyntaxException e) {
                e.printStackTrace();
            }
            if (oldS3Uri != null) {
                String tempS3Uri = oldS3Uri.getPath().replaceFirst("/","").replaceAll("/",",");
                String bucketName = tempS3Uri.split(",")[0].trim();
                String folder = tempS3Uri.split(",")[1].trim();
                String filename = tempS3Uri.split(",")[2].trim();
                fileExtension = filename.contains(".wav") ? ".wav" : ".mp4";
                StringBuffer bucket = new StringBuffer();
                bucket.append(bucketName);
                if (folder != null && !folder.isEmpty())
                    bucket.append("/").append(folder);
                s3Uri =  s3AccessTool.getS3client().getUrl(bucket.toString(), filename).toString();
            }
            if (fileExtension == null) {
                // assuming as WAV since previous attempt to obtain fileExtension failed
                fileExtension = ".wav";
            }
            fileUri = generateLocalFileUri(file, fileExtension).toString();
        }
        recording = new Recording(sid, dateCreated, dateUpdated, accountSid, callSid, duration, apiVersion, uri, DaoUtils.readUri(fileUri), DaoUtils.readUri(s3Uri));
        if (update) {
            updateRecording(recording);
        }
        return recording;
    }


}
