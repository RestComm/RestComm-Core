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

package org.restcomm.connect.http;

import akka.actor.ActorRef;
import org.apache.log4j.Logger;
import org.restcomm.connect.dao.CallDetailRecordsDao;
import org.restcomm.connect.dao.entities.CallDetailRecord;
import org.restcomm.connect.mscontrol.api.messages.Mute;
import org.restcomm.connect.mscontrol.api.messages.Unmute;
import org.restcomm.connect.telephony.api.CallInfo;

public class CallsUtil {
    protected static Logger logger = Logger.getLogger(CallsUtil.class);

    /**
     * @param mute - true if we want to mute the call, false otherwise.
     * @param callInfo - CallInfo
     * @param call - ActorRef for the call
     * @param cdr - CallDetailRecord of given call to update mute status in db
     * @param dao - CallDetailRecordsDao for calls to update mute status in db
     */
    public static void muteUnmuteCall(Boolean mute, CallInfo callInfo, ActorRef call, CallDetailRecord cdr, CallDetailRecordsDao dao){
        if(callInfo.state().name().equalsIgnoreCase("IN_PROGRESS") || callInfo.state().name().equalsIgnoreCase("in-progress")){
            if(mute){
                if(!callInfo.isMuted()){
                    call.tell(new Mute(), null);
                }else{
                    if(logger.isInfoEnabled())
                        logger.info("Call is already muted.");
                }
            }else{
                if(callInfo.isMuted()){
                    call.tell(new Unmute(), null);
                }else{
                    if(logger.isInfoEnabled())
                        logger.info("Call is not muted.");
                }
            }
            cdr = cdr.setMuted(mute);
            dao.updateCallDetailRecord(cdr);
        }else{
            // Do Nothing. We can only mute/unMute in progress calls
        }
    }
}
