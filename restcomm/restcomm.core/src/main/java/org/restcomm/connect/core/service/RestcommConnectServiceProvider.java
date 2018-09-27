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
package org.restcomm.connect.core.service;

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
import org.restcomm.connect.core.service.util.UriUtils;
import org.restcomm.connect.dao.DaoManager;
import scala.concurrent.ExecutionContext;

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
    private UriUtils uriUtils;

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
        ExecutionContext ec = (ExecutionContext) ctx.getAttribute(ExecutionContext.class.getName());

        this.uriUtils = new UriUtils(daoManager);
        ctx.setAttribute(UriUtils.class.getName(), uriUtils);

        this.recordingService = new RecordingsServiceImpl(daoManager.getRecordingsDao(), s3AccessTool, ec, uriUtils);
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

    /**
     *
     * @return
     */
    public UriUtils uriUtils() { return uriUtils; }

    //Used for unit testing - not elegant way though
    public void setUriUtils(UriUtils uriUtils) {
        this.uriUtils = uriUtils;
    }

}
