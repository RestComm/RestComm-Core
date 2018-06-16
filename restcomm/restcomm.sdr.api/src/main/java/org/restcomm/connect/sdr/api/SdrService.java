/*
 * TeleStax, Open Source Cloud Communications
 * Copyright 2017, Telestax Inc and individual contributors
 * by the @authors tag.
 *
 * This program is free software: you can redistribute it andor modify
 * under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation; either version 3 of
 * the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but OUT ANY WARRANTY; out even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 *  along  this program.  If not, see <http:www.gnu.orglicenses>
 */

package org.restcomm.connect.sdr.api;

import akka.actor.ActorRef;
import org.restcomm.connect.commons.faulttolerance.RestcommUntypedActor;
import org.restcomm.connect.commons.stream.StreamEvent;
import org.restcomm.connect.dao.entities.Recording;
import org.restcomm.connect.dao.entities.SmsMessage;
import org.restcomm.connect.telephony.api.CallInfoStreamEvent;
import org.restcomm.connect.telephony.api.events.UssdStreamEvent;

/**
 * @author oleg.agafonov@telestax.com (Oleg Agafonov)
 */
public abstract class SdrService extends RestcommUntypedActor {

    @Override
    public void onReceive(Object message) throws Exception {
        ActorRef self = self();
        ActorRef sender = sender();
        if (message instanceof StartSdrService) {
            getContext().system().eventStream().subscribe(self, StreamEvent.class);
            onStartSdrService((StartSdrService) message, self, sender);
        } else if (message instanceof CallInfoStreamEvent) {
            onCallInfoStreamEvent((CallInfoStreamEvent) message, self, sender);
        } else if (message instanceof UssdStreamEvent) {
            onUssdStreamEvent((UssdStreamEvent) message, self, sender);
        } else if (message instanceof SmsMessage) {
            onSmsMessage((SmsMessage) message, self, sender);
        } else if (message instanceof Recording) {
            onRecording((Recording)message, self, sender);
        }
    }

    protected abstract void onStartSdrService(StartSdrService message, ActorRef self, ActorRef sender);

    protected abstract void onCallInfoStreamEvent(CallInfoStreamEvent message, ActorRef self, ActorRef sender);

    protected abstract void onSmsMessage(SmsMessage message, ActorRef self, ActorRef sender);

    protected abstract void onUssdStreamEvent(UssdStreamEvent message, ActorRef self, ActorRef sender);

    protected abstract void onRecording(Recording recording, ActorRef self, ActorRef sender);
}
