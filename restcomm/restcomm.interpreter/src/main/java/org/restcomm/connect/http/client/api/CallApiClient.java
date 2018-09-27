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
package org.restcomm.connect.http.client.api;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.ReceiveTimeout;
import akka.actor.UntypedActor;
import akka.actor.UntypedActorFactory;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import org.apache.http.Header;
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.restcomm.connect.commons.dao.Sid;
import org.restcomm.connect.commons.faulttolerance.RestcommUntypedActor;
import org.restcomm.connect.core.service.RestcommConnectServiceProvider;
import org.restcomm.connect.dao.DaoManager;
import org.restcomm.connect.dao.entities.CallDetailRecord;
import org.restcomm.connect.http.asyncclient.HttpAsycClientHelper;
import org.restcomm.connect.http.client.CallApiResponse;
import org.restcomm.connect.http.client.DownloaderResponse;
import org.restcomm.connect.http.client.HttpRequestDescriptor;
import org.restcomm.connect.telephony.api.Hangup;
import scala.concurrent.duration.Duration;

import java.net.URI;
import java.net.URISyntaxException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

/**
 * @author mariafarooq
 * Disposable call api client to be used for one request and get destroyed after that.
 *
 */
public class CallApiClient extends RestcommUntypedActor {

    private final LoggingAdapter logger = Logging.getLogger(getContext().system(), this);

    private final DaoManager storage;
    private final Sid callSid;
    private CallDetailRecord callDetailRecord;
    private ActorRef requestee;

    private ActorRef httpAsycClientHelper;

    /**
     * @param callSid
     * @param storage
     */
    public CallApiClient(final Sid callSid, final DaoManager storage) {
        super();
        this.callSid = callSid;
        this.storage = storage;
        //actor will only live in memory for one hour
        context().setReceiveTimeout(Duration.create(3600, TimeUnit.SECONDS));
    }
    @Override
    public void onReceive(Object message) throws Exception {
        final Class<?> klass = message.getClass();
        final ActorRef sender = sender();
        ActorRef self = self();

        if (logger.isInfoEnabled()) {
            logger.info(" ********** CallApiClient " + self().path() + " Sender: " + sender);
            logger.info(" ********** CallApiClient " + self().path() + " Processing Message: " + klass.getName());
        }
        if (Hangup.class.equals(klass)) {
            onHangup((Hangup) message, self, sender);
        } else if (DownloaderResponse.class.equals(klass)) {
            onDownloaderResponse(message, self, sender);
        } else if (message instanceof ReceiveTimeout) {
            onDownloaderResponse(new DownloaderResponse(new Exception("Call Api stayed active too long")), self, sender);
            getContext().stop(self());
        }
    }

    private void onDownloaderResponse(Object message, ActorRef self, ActorRef sender) {
        final DownloaderResponse response = (DownloaderResponse) message;
        if (logger.isInfoEnabled()) {
            logger.info("Call api response succeeded " + response.succeeded());
            logger.info("Call api response: " + response);
        }
        requestee = requestee == null ? sender : requestee;
        CallApiResponse apiResponse;
        if (response.succeeded()) {
            apiResponse = new CallApiResponse(response.get());
        } else {
            apiResponse = new CallApiResponse(response.cause(), response.error());
        }
        requestee.tell(apiResponse, self);
    }

    protected void onHangup(Hangup message, ActorRef self, ActorRef sender) throws URISyntaxException, ParseException {
        requestee = sender;
        callDetailRecord = message.getCallDetailRecord() == null ? getCallDetailRecord() : message.getCallDetailRecord();
        if(callDetailRecord == null){
            logger.error("could not retrieve cdr by provided Sid");
            onDownloaderResponse(new DownloaderResponse(new IllegalArgumentException("could not retrieve cdr by provided Sid")), self, sender);
        } else {
            try {
                URI uri = new URI("/restcomm"+callDetailRecord.getUri());
                uri = RestcommConnectServiceProvider.getInstance().uriUtils().resolve(uri, callDetailRecord.getAccountSid());
                if (logger.isInfoEnabled())
                    logger.info("call api uri is: "+uri);

                Header[] headers = RestcommApiClientUtil.getBasicHeaders(message.getRequestingAccountSid(), storage);

                ArrayList<NameValuePair> postParameters = new ArrayList<NameValuePair>();
                postParameters.add(new BasicNameValuePair("Status", "Completed"));

                httpAsycClientHelper = httpAsycClientHelper();
                HttpRequestDescriptor httpRequestDescriptor =new HttpRequestDescriptor(uri, "POST", postParameters, -1, headers);
                httpAsycClientHelper.tell(httpRequestDescriptor, self);
            } catch (Exception e) {
                logger.error("Exception while trying to terminate call via api {} ", e);
                onDownloaderResponse(new DownloaderResponse(e), self, sender);
            }
        }
    }

    private CallDetailRecord getCallDetailRecord() {
        if(callSid != null){
            return storage.getCallDetailRecordsDao().getCallDetailRecord(callSid);
        }
        return null;
    }

    private ActorRef httpAsycClientHelper(){
        final Props props = new Props(new UntypedActorFactory() {
            private static final long serialVersionUID = 1L;

            @Override
            public UntypedActor create() throws Exception {
                return new HttpAsycClientHelper();
            }
        });
        return getContext().actorOf(props);

    }

    @Override
    public void postStop () {
        if (logger.isInfoEnabled()) {
            logger.info("CallApiClient at post stop");
        }
        if(httpAsycClientHelper != null && !httpAsycClientHelper.isTerminated())
            getContext().stop(httpAsycClientHelper);
        getContext().stop(self());
        super.postStop();
    }
}
