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

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.concurrent.TimeUnit;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.XMLConfiguration;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.restcomm.connect.commons.configuration.RestcommConfiguration;
import org.restcomm.connect.commons.dao.Sid;
import org.restcomm.connect.dao.CallDetailRecordsDao;
import org.restcomm.connect.dao.DaoManager;
import org.restcomm.connect.dao.entities.CallDetailRecord;
import org.restcomm.connect.http.client.CallApiResponse;
import org.restcomm.connect.telephony.api.Hangup;

import com.github.tomakehurst.wiremock.junit.WireMockRule;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.actor.ReceiveTimeout;
import akka.actor.UntypedActor;
import akka.actor.UntypedActorFactory;
import akka.testkit.JavaTestKit;
import scala.concurrent.duration.FiniteDuration;

/**
 * @author maria.farooq
 */
public final class ApiClientTests {

    private ActorSystem system;
    
    private static int MOCK_PORT = 8099;
    private static final Sid TEST_CALL_SID = new Sid("ID8deb35fc5121429fa96635aebe3976d2-CA6d61e3877f3c47828a26efc498a9e8f9");
    private static final String TEST_CALL_URI = "/2012-04-24/Accounts/ACae6e420f425248d6a26948c17a9e2acf/Calls/ID8deb35fc5121429fa96635aebe3976d2-CA6d61e3877f3c47828a26efc498a9e8f9";

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(wireMockConfig().bindAddress("127.0.0.1").port(MOCK_PORT));    

    public ApiClientTests() {
        super();
    }

    @Before
    public void before() throws Exception {
        URL url = this.getClass().getResource("/restcomm.xml");
        Configuration xml = new XMLConfiguration(url);
        RestcommConfiguration.createOnce(xml);
        system = ActorSystem.create();
    }

    @After
    public void after() throws Exception {
        system.shutdown();
        wireMockRule.resetRequests();
    }

    private Props CallApiClientProps(final DaoManager storage){
    	final Props props =  new Props(new UntypedActorFactory() {
            private static final long serialVersionUID = 1L;

            @Override
            public UntypedActor create() throws Exception {
                return new CallApiClient(null, storage);
            }
        });
        return props;
    }

    @Test
    public void testCallApiTimeoutTermination() throws URISyntaxException, IOException, InterruptedException {
        stubFor(get(urlMatching("/testGet")).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("expectedBody")));        
        new JavaTestKit(system) {
            {
                final ActorRef observer = getRef();
                DaoManager daoManager = mock(DaoManager.class);
                CallDetailRecord.Builder cdrBuilder = CallDetailRecord.builder();
                cdrBuilder.setSid(TEST_CALL_SID);
                cdrBuilder.setUri(new URI(TEST_CALL_URI));
                CallDetailRecord cdr = cdrBuilder.build();
                CallDetailRecordsDao cdrDao = mock(CallDetailRecordsDao.class);
                when(daoManager.getCallDetailRecordsDao()).thenReturn(cdrDao);
                when(cdrDao.getCallDetailRecord(any(Sid.class))).thenReturn(cdr);
                
                ActorRef callApiClient = system.actorOf(CallApiClientProps(daoManager));
                callApiClient.tell(new ReceiveTimeout() {}, observer);
                final FiniteDuration timeout = FiniteDuration.create(15, TimeUnit.SECONDS);
                final CallApiResponse response = expectMsgClass(timeout, CallApiResponse.class);
                assertFalse(response.succeeded());
                Thread.sleep(1000);
                assertTrue(callApiClient.isTerminated());
            }
        };
    }

    @Test
    public void testCallApiClientFailedResponse() throws URISyntaxException, IOException, InterruptedException {
        stubFor(get(urlMatching("/testGet")).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("expectedBody")));        
        new JavaTestKit(system) {
            {
                final ActorRef observer = getRef();
                DaoManager daoManager = mock(DaoManager.class);
                CallDetailRecord.Builder cdrBuilder = CallDetailRecord.builder();
                cdrBuilder.setSid(TEST_CALL_SID);
                cdrBuilder.setUri(new URI(TEST_CALL_URI));
                CallDetailRecord cdr = cdrBuilder.build();
                CallDetailRecordsDao cdrDao = mock(CallDetailRecordsDao.class);
                when(daoManager.getCallDetailRecordsDao()).thenReturn(cdrDao);
                when(cdrDao.getCallDetailRecord(any(Sid.class))).thenReturn(cdr);
                
                ActorRef callApiClient = system.actorOf(CallApiClientProps(daoManager));
                callApiClient.tell(new Hangup("test", new Sid("ACae6e420f425248d6a26948c17a9e2acf"), null), observer);
                final FiniteDuration timeout = FiniteDuration.create(15, TimeUnit.SECONDS);
                final CallApiResponse response = expectMsgClass(timeout, CallApiResponse.class);
                assertFalse(response.succeeded());
            }
        };
    }
}
