/*
 * TeleStax, Open Source Cloud Communications
 * Copyright 2011-2017, Telestax Inc and individual contributors
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

package org.restcomm.connect.interpreter.mediagroup;

import akka.actor.Actor;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.actor.ReceiveTimeout;
import akka.actor.UntypedActorFactory;
import akka.testkit.JavaTestKit;
import akka.testkit.TestActorRef;
import com.google.common.base.Function;
import junit.runner.Version;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.XMLConfiguration;
import org.joda.time.DateTime;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;
import org.restcomm.connect.commons.HttpConnector;
import org.restcomm.connect.commons.cache.DiskCacheRequest;
import org.restcomm.connect.commons.cache.DiskCacheResponse;
import org.restcomm.connect.commons.configuration.RestcommConfiguration;
import org.restcomm.connect.commons.dao.Sid;
import org.restcomm.connect.commons.fsm.State;
import org.restcomm.connect.commons.patterns.Observe;
import org.restcomm.connect.commons.telephony.CreateCallType;
import org.restcomm.connect.core.service.RestcommConnectServiceProvider;
import org.restcomm.connect.core.service.util.UriUtils;
import org.restcomm.connect.dao.CallDetailRecordsDao;
import org.restcomm.connect.dao.DaoManager;
import org.restcomm.connect.dao.IncomingPhoneNumbersDao;
import org.restcomm.connect.dao.entities.MediaAttributes;
import org.restcomm.connect.http.client.DownloaderResponse;
import org.restcomm.connect.http.client.HttpRequestDescriptor;
import org.restcomm.connect.http.client.HttpResponseDescriptor;
import org.restcomm.connect.interpreter.Fork;
import org.restcomm.connect.interpreter.StartInterpreter;
import org.restcomm.connect.interpreter.VoiceInterpreter;
import org.restcomm.connect.interpreter.VoiceInterpreterParams;
import org.restcomm.connect.interpreter.rcml.MockedActor;
import org.restcomm.connect.mscontrol.api.messages.MediaGroupResponse;
import org.restcomm.connect.mscontrol.api.messages.Play;
import org.restcomm.connect.mscontrol.api.messages.StopMediaGroup;
import org.restcomm.connect.telephony.api.CallInfo;
import org.restcomm.connect.telephony.api.CallResponse;
import org.restcomm.connect.telephony.api.CallStateChanged;
import org.restcomm.connect.telephony.api.GetCallInfo;
import org.restcomm.connect.telephony.api.Hangup;
import scala.concurrent.ExecutionContext;

import javax.servlet.ServletContext;
import java.net.URI;

/**
 * @author guilherme.jansen@telestax.com
 */

public class StopMediaGroupTest {

    private static ActorSystem system;
    private Configuration configuration;
    private MockedActor mockedCallManager;

    private URI requestUri = URI.create("http://127.0.0.1/gather.xml");
    private String dialRcmlNoAction = "<Response><Dial><Client>bob</Client><Client>alice</Client></Dial></Response>";
    private String dialRcmlAction = "<Response><Dial action=\"http://127.0.0.1:8989\"><Client>bob</Client><Client>alice</Client></Dial></Response>";
    private URI playUri = URI.create("http://127.0.0.1/play.wav");


    public StopMediaGroupTest() {
        super();
    }

    @BeforeClass
    public static void before() throws Exception {
        system = ActorSystem.create();
    }

    @AfterClass
    public static void after() throws Exception {
        system.shutdown();
    }

    @Before
    public void init() {
        String restcommXmlPath = this.getClass().getResource("/restcomm.xml").getFile();
        try {
            configuration = getConfiguration(restcommXmlPath);
            RestcommConfiguration.createOnce(configuration);
        } catch (ConfigurationException e) {
            throw new RuntimeException();
        }
    }

    private Configuration getConfiguration(String path) throws ConfigurationException {
        XMLConfiguration xmlConfiguration = new XMLConfiguration();
        xmlConfiguration.setDelimiterParsingDisabled(true);
        xmlConfiguration.setAttributeSplittingDisabled(true);
        xmlConfiguration.load(path);
        return xmlConfiguration;
    }

    private HttpResponseDescriptor getOkRcml(URI uri, String rcml) {
        HttpResponseDescriptor.Builder builder = HttpResponseDescriptor.builder();
        builder.setURI(uri);
        builder.setStatusCode(200);
        builder.setStatusDescription("OK");
        builder.setContent(rcml);
        builder.setContentLength(rcml.length());
        builder.setContentType("text/xml");
        return builder.build();
    }

    private TestActorRef<Actor> createVoiceInterpreter(final ActorRef observer, final String ... fsmStateBypass) {
        System.out.println("JUnit version is: " + Version.id());

        DaoManager storage = Mockito.mock(DaoManager.class);
        UriUtils uriUtils = Mockito.mock(UriUtils.class);
        RestcommConnectServiceProvider.getInstance().setUriUtils(uriUtils);

        Mockito.when(uriUtils.resolve(Mockito.any(URI.class), Mockito.any(Sid.class))).thenReturn(URI.create("http://127.0.0.1"));
        //dao
        final CallDetailRecordsDao recordsDao = Mockito.mock(CallDetailRecordsDao.class);
        Mockito.when(recordsDao.getCallDetailRecord(Mockito.any(Sid.class))).thenReturn(null);


        IncomingPhoneNumbersDao incomingPhoneNumbersDao = Mockito.mock(IncomingPhoneNumbersDao.class);
        Mockito.when(storage.getCallDetailRecordsDao()).thenReturn(recordsDao);

        //actors
        final ActorRef downloader = new MockedActor("downloader")
                .add(DiskCacheRequest.class, new DiskCacheRequestProperty(playUri), new DiskCacheResponse(playUri))
                .asRef(system);

        mockedCallManager = new MockedActor("callManager");
        final ActorRef callManager = mockedCallManager.asRef(system);

        final VoiceInterpreterParams.Builder builder = new VoiceInterpreterParams.Builder();
        builder.setConfiguration(configuration);
        builder.setStorage(storage);
        builder.setCallManager(callManager);
        builder.setAccount(new Sid("ACae6e420f425248d6a26948c17a9e2acf"));
        builder.setVersion("2012-04-24");
        builder.setUrl(requestUri);
        builder.setMethod("GET");
        builder.setAsImsUa(false);
        final Props props = new Props(new UntypedActorFactory() {
            private static final long serialVersionUID = 1L;

            @Override
            public Actor create() throws Exception {
                return new VoiceInterpreter(builder.build()){

                    @Override
                    protected ActorRef downloader() {
                        return observer;
                    }

                    @Override
                    protected ActorRef cache(String path, String uri) {
                        return downloader;
                    }

                    @Override
                    public ActorRef getCache() {
                        return downloader;
                    }

                    @Override
                    protected boolean is(State state) {
                        //bypass fsm state so the test logic can be reduced
                        String id = state.getId();
                        for(int i = 0; i < fsmStateBypass.length; i++){
                            if(id.equals(fsmStateBypass[i])){
                                return true;
                            }
                        }
                        //if there is no state to bypass, stick to formal verification
                        return super.is(state);
                    }
                };
            }
        });
        return TestActorRef.create(system, props, "VoiceInterpreter" + System.currentTimeMillis());
    }

    @Test
    public void testForkingBusyStop() throws Exception {
        new JavaTestKit(system){
            {
                //creating and starting vi
                final ActorRef observer = getRef();
                final ActorRef interpreter = createVoiceInterpreter(observer, "forking");
                interpreter.tell(new StartInterpreter(observer), observer);
                expectMsgClass(GetCallInfo.class);

                //notifying vi that the last/only call answered with busy
                final CallStateChanged callStateChanged = new CallStateChanged(CallStateChanged.State.BUSY);
                interpreter.tell(callStateChanged, null);

                //check if StopMediaGroup was requested to media actors
                //this request originally continues through the actors Call > CallController > MediaGroup
                //reaching media server and stopping whats currently playing. But at this test, both call and
                //vi actors are using the same observer, so we receive StopMediaGroup directly
                expectMsgClass(StopMediaGroup.class);

                //emulate the response that comes from media server, through media group, reaching vi
                interpreter.tell(new MediaGroupResponse<String>("stopped"), observer);

                //check if vi stays still and DO NOT ask for next verb
                expectNoMsg();
            }
        };
    }

    @Test
    public void testForkingBusyStopNextVerb() throws Exception {
        new JavaTestKit(system){
            {
                //creating and starting vi
                final ActorRef observer = getRef();
                final ActorRef interpreter = createVoiceInterpreter(observer);
                interpreter.tell(new StartInterpreter(observer), observer);
                expectMsgClass(GetCallInfo.class);

                //creating new call
                interpreter.tell(new CallResponse(new CallInfo(
                        new Sid("ACae6e420f425248d6a26948c17a9e2acf"),
                        new Sid("ACae6e420f425248d6a26948c17a9e2acf"),
                        CallStateChanged.State.IN_PROGRESS,
                        CreateCallType.SIP,
                        "inbound",
                        new DateTime(),
                        null,
                        "test", "test",
                        "testTo",
                        null,
                        null,
                        false,
                        false,
                        false,
                        new DateTime(),
                        new MediaAttributes())), observer);
                expectMsgClass(Observe.class);
                expectMsgClass(HttpRequestDescriptor.class);
                interpreter.tell(new DownloaderResponse(getOkRcml(requestUri, dialRcmlNoAction)), observer);
                expectNoMsg();

                //force forking state at FSM
                interpreter.tell(new Fork(), observer);
                expectMsgClass(Play.class);

                //notifying vi that the last/only call answered as busy
                final CallStateChanged callStateChanged = new CallStateChanged(CallStateChanged.State.BUSY);
                interpreter.tell(callStateChanged, null);

                //check if StopMediaGroup was requested to media actors
                //this request originally continues through the actors Call > CallController > MediaGroup
                //reaching media server and stopping whats currently playing. But at this test, both call and
                //vi actors are using the same observer, so we receive StopMediaGroup directly
                expectMsgClass(StopMediaGroup.class);

                //emulate the response that comes from media server, through media group, reaching vi
                interpreter.tell(new MediaGroupResponse<String>("stopped"), observer);

                //check if vi asked for next verb, reaching End tag and consequently hangs up the call
                expectMsgClass(Hangup.class);
            }
        };
    }

    @Test
    public void testForkingTimeoutStop() throws Exception {
        new JavaTestKit(system){
            {
                //creating and starting vi
                final ActorRef observer = getRef();
                final ActorRef interpreter = createVoiceInterpreter(observer);
                interpreter.tell(new StartInterpreter(observer), observer);
                expectMsgClass(GetCallInfo.class);

                //creating new call
                interpreter.tell(new CallResponse(new CallInfo(
                        new Sid("ACae6e420f425248d6a26948c17a9e2acf"),
                        new Sid("ACae6e420f425248d6a26948c17a9e2acf"),
                        CallStateChanged.State.IN_PROGRESS,
                        CreateCallType.SIP,
                        "inbound",
                        new DateTime(),
                        null,
                        "test", "test",
                        "testTo",
                        null,
                        null,
                        false,
                        false,
                        false,
                        new DateTime(),
                        new MediaAttributes())), observer);
                expectMsgClass(Observe.class);
                expectMsgClass(HttpRequestDescriptor.class);
                interpreter.tell(new DownloaderResponse(getOkRcml(requestUri, dialRcmlAction)), observer);
                expectNoMsg();

                //force forking state at FSM
                interpreter.tell(new Fork(), observer);
                expectMsgClass(Play.class);

                //notifying vi that the last/only call reached timeout
                ReceiveTimeout timeout = new ReceiveTimeout(){};
                interpreter.tell(timeout, observer);

                //check if StopMediaGroup was requested to media actors
                //this request originally continues through the actors Call > CallController > MediaGroup
                //reaching media server and stopping whats currently playing. But at this test, both call and
                //vi actors are using the same observer, so we receive StopMediaGroup directly
                expectMsgClass(StopMediaGroup.class);

                //emulate the response that comes from media server, through media group, reaching vi
                interpreter.tell(new MediaGroupResponse<String>("stopped"), observer);

                //check if vi stays still and DO NOT ask for next verb
                expectNoMsg();
            }
        };
    }

    @Test
    public void testForkingTimeoutStopNextVerb() throws Exception {
        new JavaTestKit(system){
            {
                //creating and starting vi
                final ActorRef observer = getRef();
                final ActorRef interpreter = createVoiceInterpreter(observer);
                interpreter.tell(new StartInterpreter(observer), observer);
                expectMsgClass(GetCallInfo.class);

                //creating new call
                interpreter.tell(new CallResponse(new CallInfo(
                        new Sid("ACae6e420f425248d6a26948c17a9e2acf"),
                        new Sid("ACae6e420f425248d6a26948c17a9e2acf"),
                        CallStateChanged.State.IN_PROGRESS,
                        CreateCallType.SIP,
                        "inbound",
                        new DateTime(),
                        null,
                        "test", "test",
                        "testTo",
                        null,
                        null,
                        false,
                        false,
                        false,
                        new DateTime(),
                        new MediaAttributes())), observer);
                expectMsgClass(Observe.class);
                expectMsgClass(HttpRequestDescriptor.class);
                interpreter.tell(new DownloaderResponse(getOkRcml(requestUri, dialRcmlNoAction)), observer);
                expectNoMsg();

                //force forking state at FSM
                interpreter.tell(new Fork(), observer);
                expectMsgClass(Play.class);

                //notifying vi that the last/only call reached timeout
                ReceiveTimeout timeout = new ReceiveTimeout(){};
                interpreter.tell(timeout, observer);

                //check if StopMediaGroup was requested to media actors
                //this request originally continues through the actors Call > CallController > MediaGroup
                //reaching media server and stopping whats currently playing. But at this test, both call and
                //vi actors are using the same observer, so we receive StopMediaGroup directly
                expectMsgClass(StopMediaGroup.class);

                //emulate the response that comes from media server, through media group, reaching vi
                interpreter.tell(new MediaGroupResponse<String>("stopped"), observer);

                //check if vi asked for next verb, reaching End tag and consequently hangs up the call
                expectMsgClass(Hangup.class);
            }
        };
    }

    public static class DiskCacheRequestProperty extends MockedActor.SimplePropertyPredicate<DiskCacheRequest, URI> {

        static Function<DiskCacheRequest, URI> extractor = new Function<DiskCacheRequest, URI>() {
            @Override
            public URI apply(DiskCacheRequest diskCacheRequest) {
                return diskCacheRequest.uri();
            }
        };

        public DiskCacheRequestProperty(URI value) {
            super(value, extractor);
        }
    }

}
