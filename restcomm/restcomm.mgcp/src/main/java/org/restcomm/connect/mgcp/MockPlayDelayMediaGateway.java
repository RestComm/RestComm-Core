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
package org.restcomm.connect.mgcp;


import akka.actor.ActorRef;
import jain.protocol.ip.mgcp.message.NotificationRequest;
import jain.protocol.ip.mgcp.message.Notify;
import jain.protocol.ip.mgcp.message.parms.EventName;
import jain.protocol.ip.mgcp.pkg.MgcpEvent;
import org.mobicents.protocols.mgcp.jain.pkg.AUMgcpEvent;
import org.mobicents.protocols.mgcp.jain.pkg.AUPackage;

/**
 * @author hoan.h.luu@telestax.com
 */
public class MockPlayDelayMediaGateway extends MockMediaGateway {
    @Override
    protected void notify(Object message, ActorRef sender) {
        final ActorRef self = self();
        final NotificationRequest request = (NotificationRequest) message;

        MgcpEvent event = null;
        if (!request.getSignalRequests()[0].getEventIdentifier().getParms().equalsIgnoreCase("sg=pa") &&
                (request.getSignalRequests()[0].getEventIdentifier().getName().equalsIgnoreCase("es") ||
                        request.getSignalRequests()[0].getEventIdentifier().getName().equalsIgnoreCase("pr"))) {
            //Looks like this is either an RQNT AU/ES or
            //recording max length reached and we got the original recording RQNT
            event = AUMgcpEvent.auoc.withParm("AU/pr ri=file://" + recordingFile.toPath() + " rc=100 dc=1");
        } else {
            event = AUMgcpEvent.auoc.withParm("rc=100 dc=1");
            if(!request.getSignalRequests()[0].getEventIdentifier().getParms().contains("ringing.wav")) {
                try {
                    Thread.sleep(2000);
                } catch (Exception e){
                    logger.error(e.toString());
                }
            }
        }

        final EventName[] events = {new EventName(AUPackage.AU, event)};

        final Notify notify = new Notify(this, request.getEndpointIdentifier(), request.getRequestIdentifier(), events);
        notify.setTransactionHandle((int) transactionIdPool.get());
        System.out.println(notify.toString());
        sender.tell(notify, self);
    }
}
