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

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.joda.time.DateTime;
import org.restcomm.connect.commons.annotations.concurrency.ThreadSafe;
import org.restcomm.connect.commons.dao.Sid;
import org.restcomm.connect.dao.DaoUtils;
import org.restcomm.connect.dao.RecordingsDao;
import org.restcomm.connect.dao.entities.Recording;
import org.restcomm.connect.dao.entities.RecordingFilter;

import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author quintana.thomas@gmail.com (Thomas Quintana)
 * @author maria-farooq@live.com (Maria Farooq)
 */
@ThreadSafe
public final class MybatisRecordingsDao implements RecordingsDao {
    private static final String namespace = "org.mobicents.servlet.sip.restcomm.dao.RecordingsDao.";
    private final SqlSessionFactory sessions;

    public MybatisRecordingsDao(final SqlSessionFactory sessions) {
        super();
        this.sessions = sessions;
    }

    @Override
    public void addRecording(Recording recording) {
        final SqlSession session = sessions.openSession();
        try {
            session.insert(namespace + "addRecording", toMap(recording));
            session.commit();
        } finally {
            session.close();
        }
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
            fileUri = String.format("/restcomm/%s/Accounts/%s/Recordings/%s.wav",apiVersion,accountSid,sid);
        }

        // fileUri: http://192.168.1.190:8080/restcomm/2012-04-24/Accounts/ACae6e420f425248d6a26948c17a9e2acf/Recordings/RE4c9c09908b60402c8c0a77e24313f27d.wav
        // s3Uri: https://gvagrestcomm.s3.amazonaws.com/RE4c9c09908b60402c8c0a77e24313f27d.wav
        // old S3URI: https://s3.amazonaws.com/restcomm-as-a-service/logs/RE7ddbd5b441574e4ab786a1fddf33eb47.wav?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Date=20170209T103950Z&X-Amz-SignedHeaders=host&X-Amz-Expires=604800&X-Amz-Credential=AKIAIRG5NINXKJAJM5DA%2F20170209%2Fus-east-1%2Fs3%2Faws4_request&X-Amz-Signature=b3da2acc17ee9c6aca4cd151e154d94f530670850f0fcade2422f85d1c7cc992
        String s3Uri = (String) map.get("s3_uri");
        recording = new Recording(sid, dateCreated, dateUpdated, accountSid, callSid, duration, apiVersion, uri, DaoUtils.readUri(fileUri), DaoUtils.readUri(s3Uri));
        return recording;
    }


}
