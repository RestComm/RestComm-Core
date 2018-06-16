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
package org.restcomm.connect.dao;

import org.restcomm.connect.commons.dao.Sid;
import org.restcomm.connect.dao.entities.MediaAttributes;
import org.restcomm.connect.dao.entities.Recording;
import org.restcomm.connect.dao.entities.RecordingFilter;

import java.util.List;

/**
 * @author quintana.thomas@gmail.com (Thomas Quintana)
 */
public interface RecordingsDao {
    void addRecording(Recording recording, MediaAttributes.MediaType mediaType);

    Recording getRecording(Sid sid);

    // otsakir: is this really needed?
    Recording getRecordingByCall(Sid callSid);

    List<Recording> getRecordingsByCall(Sid callSid);

    List<Recording> getRecordings(Sid accountSid);

    void removeRecording(Sid sid);

    void removeRecordings(Sid accountSid);

    // Support for filtering of recording list result, Issue 1395
    Integer getTotalRecording(RecordingFilter filter);
    List<Recording> getRecordings(RecordingFilter filter);

    void updateRecording(Recording recording);
}
