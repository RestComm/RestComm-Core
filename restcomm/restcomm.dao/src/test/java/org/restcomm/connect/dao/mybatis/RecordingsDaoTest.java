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

import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.log4j.Logger;
import org.joda.time.DateTime;
import org.junit.After;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.restcomm.connect.dao.RecordingsDao;
import org.restcomm.connect.dao.common.Sorting;
import org.restcomm.connect.dao.entities.Recording;
import org.restcomm.connect.dao.entities.RecordingFilter;

import java.io.InputStream;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * @author quintana.thomas@gmail.com (Thomas Quintana)
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public final class RecordingsDaoTest {
    private static Logger logger = Logger.getLogger(RecordingsDaoTest.class);
    private static MybatisDaoManager manager;

    public RecordingsDaoTest() {
        super();
    }

    @Before
    public void before() {
        final InputStream data = getClass().getResourceAsStream("/mybatis.xml");
        final SqlSessionFactoryBuilder builder = new SqlSessionFactoryBuilder();
        final SqlSessionFactory factory = builder.build(data);
        manager = new MybatisDaoManager();
        manager.start(factory);
    }

    @After
    public void after() {
        manager.shutdown();
    }

    // TODO: need to add unit tests to exercise creation, query, update, removal

    @Test
    public void filterWithDateSorting() throws ParseException {
        RecordingsDao dao = manager.getRecordingsDao();
        RecordingFilter.Builder builder = new RecordingFilter.Builder();
        List<String> accountSidSet = new ArrayList<String>();
        accountSidSet.add("ACe4462e9eb95b4cf8ae17001ab2f520af");
        builder.byAccountSidSet(accountSidSet);
        builder.sortedByDate(Sorting.Direction.ASC);
        RecordingFilter filter = builder.build();
        List<Recording> recordings = dao.getRecordings(filter);
        assertEquals(9, recordings.size());
        final DateTime min = DateTime.parse("2017-04-05T12:57:35.535");
        final DateTime max = DateTime.parse("2017-04-13T12:57:48.535");
        assertEquals(0, min.compareTo(recordings.get(0).getDateCreated()));
        assertEquals(0, max.compareTo(recordings.get(recordings.size() - 1).getDateCreated()));

        builder.sortedByDate(Sorting.Direction.DESC);
        filter = builder.build();
        recordings = dao.getRecordings(filter);
        assertEquals(0, max.compareTo(recordings.get(0).getDateCreated()));
        assertEquals(0, min.compareTo(recordings.get(recordings.size() - 1).getDateCreated()));
    }

    @Test
    public void filterWithDurationSorting() throws ParseException {
        RecordingsDao dao = manager.getRecordingsDao();
        RecordingFilter.Builder builder = new RecordingFilter.Builder();
        List<String> accountSidSet = new ArrayList<String>();
        accountSidSet.add("ACe4462e9eb95b4cf8ae17001ab2f520af");
        builder.byAccountSidSet(accountSidSet);
        builder.sortedByDuration(Sorting.Direction.ASC);
        RecordingFilter filter = builder.build();
        List<Recording> recordings = dao.getRecordings(filter);
        assertEquals(9, recordings.size());
        assertEquals("3.0", recordings.get(0).getDuration().toString());
        assertEquals("52.0", recordings.get(recordings.size() - 1).getDuration().toString());

        builder.sortedByDuration(Sorting.Direction.DESC);
        filter = builder.build();
        recordings = dao.getRecordings(filter);
        assertEquals("52.0", recordings.get(0).getDuration().toString());
        assertEquals("3.0", recordings.get(recordings.size() - 1).getDuration().toString());
    }

    @Test
    public void filterWithCallSidSorting() throws ParseException {
        RecordingsDao dao = manager.getRecordingsDao();
        RecordingFilter.Builder builder = new RecordingFilter.Builder();
        List<String> accountSidSet = new ArrayList<String>();
        accountSidSet.add("ACe4462e9eb95b4cf8ae17001ab2f520af");
        builder.byAccountSidSet(accountSidSet);
        builder.sortedByCallSid(Sorting.Direction.ASC);
        RecordingFilter filter = builder.build();
        List<Recording> recordings = dao.getRecordings(filter);
        assertEquals(9, recordings.size());
        assertEquals("CA2d0f6354e75e46b3ac76f534129ff511", recordings.get(0).getCallSid().toString());
        assertEquals("CA2d9f6354e75e46b3ac76f534129ff511", recordings.get(recordings.size() - 1).getCallSid().toString());

        builder.sortedByCallSid(Sorting.Direction.DESC);
        filter = builder.build();
        recordings = dao.getRecordings(filter);
        assertEquals("CA2d9f6354e75e46b3ac76f534129ff511", recordings.get(0).getCallSid().toString());
        assertEquals("CA2d0f6354e75e46b3ac76f534129ff511", recordings.get(recordings.size() - 1).getCallSid().toString());
    }
}
