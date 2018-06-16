/*
 * TeleStax, Open Source Cloud Communications
 * Copyright 2011-2018, Telestax Inc and individual contributors
 * by the @authors tag.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.restcomm.connect.application;

import javax.servlet.ServletContext;

import org.restcomm.connect.commons.amazonS3.S3AccessTool;
import org.restcomm.connect.core.service.api.ClientPasswordHashingService;
import org.restcomm.connect.core.service.api.NumberSelectorService;
import org.restcomm.connect.core.service.api.ProfileService;
import org.restcomm.connect.core.service.api.RecordingService;
import org.restcomm.connect.core.service.client.ClientPasswordHashingServiceImpl;
import org.restcomm.connect.core.service.number.NumberSelectorServiceImpl;
import org.restcomm.connect.core.service.profile.ProfileServiceImpl;
import org.restcomm.connect.core.service.recording.RecordingsServiceImpl;
import org.restcomm.connect.dao.DaoManager;

/**
 * @author guilherme.jansen@telestax.com
 * @author Maria
 */
public class RestcommConnectServiceProvider {
    private static RestcommConnectServiceProvider instance = null;

    // core services
    private NumberSelectorService numberSelector;
    private ProfileService profileService;
    private ClientPasswordHashingService clientPasswordHashingService;
    private RecordingService recordingService;

    public static RestcommConnectServiceProvider getInstance() {
        if (instance == null) {
            instance = new RestcommConnectServiceProvider();
        }
        return instance;
    }

    /**
     * @param ctx
     */
    public void startServices(ServletContext ctx) {
        DaoManager daoManager = (DaoManager) ctx.getAttribute(DaoManager.class.getName());
        // core services initialization
        this.numberSelector = new NumberSelectorServiceImpl(daoManager.getIncomingPhoneNumbersDao());
        ctx.setAttribute(NumberSelectorService.class.getName(), numberSelector);
        this.profileService = new ProfileServiceImpl(daoManager);
        ctx.setAttribute(ProfileService.class.getName(), profileService);
        this.clientPasswordHashingService = new ClientPasswordHashingServiceImpl(daoManager);
        ctx.setAttribute(ClientPasswordHashingService.class.getName(), clientPasswordHashingService);

        S3AccessTool s3AccessTool = (S3AccessTool) ctx.getAttribute(S3AccessTool.class.getName());

        this.recordingService = new RecordingsServiceImpl(daoManager.getRecordingsDao(), s3AccessTool);
        ctx.setAttribute(RecordingService.class.getName(), recordingService);
    }

    /**
     * @return
     */
    public NumberSelectorService provideNumberSelectorService() {
        return numberSelector;
    }

    /**
     * @return
     */
    public ProfileService provideProfileService() {
        return profileService;
    }

    /**
     * @return
     */
    public ClientPasswordHashingService clientPasswordHashingService() { return clientPasswordHashingService; }

    /**
     * @return
     */
    public RecordingService recordingService() { return recordingService; }

}
