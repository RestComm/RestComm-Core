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

package org.restcomm.connect.interpreter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.List;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.http.NameValuePair;
import org.joda.time.DateTime;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.internal.stubbing.answers.ClonesArguments;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.restcomm.connect.commons.cache.DiskCacheRequest;
import org.restcomm.connect.commons.cache.DiskCacheResponse;
import org.restcomm.connect.commons.configuration.RestcommConfiguration;
import org.restcomm.connect.commons.dao.Sid;
import org.restcomm.connect.commons.fsm.FiniteStateMachine;
import org.restcomm.connect.commons.fsm.State;
import org.restcomm.connect.commons.patterns.Observe;
import org.restcomm.connect.commons.telephony.CreateCallType;
import org.restcomm.connect.core.service.RestcommConnectServiceProvider;
import org.restcomm.connect.core.service.util.UriUtils;
import org.restcomm.connect.dao.CallDetailRecordsDao;
import org.restcomm.connect.dao.DaoManager;
import org.restcomm.connect.http.client.DownloaderResponse;
import org.restcomm.connect.http.client.HttpRequestDescriptor;
import org.restcomm.connect.http.client.HttpResponseDescriptor;
import org.restcomm.connect.interpreter.rcml.MockedActor;
import org.restcomm.connect.interpreter.rcml.domain.GatherAttributes;
import org.restcomm.connect.mscontrol.api.messages.Collect;
import org.restcomm.connect.mscontrol.api.messages.CollectedResult;
import org.restcomm.connect.mscontrol.api.messages.MediaGroupResponse;
import org.restcomm.connect.mscontrol.api.messages.Play;
import org.restcomm.connect.telephony.api.CallInfo;
import org.restcomm.connect.telephony.api.CallResponse;
import org.restcomm.connect.telephony.api.CallStateChanged;
import org.restcomm.connect.telephony.api.GetCallInfo;
import org.restcomm.connect.telephony.api.Hangup;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;

import akka.actor.Actor;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.actor.UntypedActorFactory;
import akka.testkit.JavaTestKit;
import akka.testkit.TestActorRef;
import org.junit.Assert;
import org.restcomm.connect.dao.entities.MediaAttributes;

/**
 * Created by gdubina on 6/24/17.
 */
public class GatherSpeechTest {

    private static UriUtils uriUtils;

    private static ActorSystem system;

    private Configuration configuration;

    private URI requestUri = URI.create("http://127.0.0.1/gather.xml");
    private URI playUri = URI.create("http://127.0.0.1/play.wav");
    private URI actionCallbackUri = URI.create("http://127.0.0.1/gather-action.xml");
    private URI partialCallbackUri = URI.create("http://127.0.0.1/gather-partial.xml");

    private String endRcml = "<Response><Hangup/></Response>";
    private String playRcml = "<Response><Play>" + playUri + "</Play></Response>";
    private String gatherRcmlPartial = "<Response><Gather " +
            GatherAttributes.ATTRIBUTE_INPUT + "=\"speech\" " +
            GatherAttributes.ATTRIBUTE_ACTION + "=\"" + actionCallbackUri + "\" " +
            GatherAttributes.ATTRIBUTE_PARTIAL_RESULT_CALLBACK + "=\"" + partialCallbackUri + "\" " +
            GatherAttributes.ATTRIBUTE_NUM_DIGITS + "=\"1\" " +
            GatherAttributes.ATTRIBUTE_TIME_OUT + "=\"60\">" +
            "</Gather></Response>";
    private String gatherRcmlNoPartial = "<Response><Gather " +
            GatherAttributes.ATTRIBUTE_INPUT + "=\"speech\" " +
            GatherAttributes.ATTRIBUTE_ACTION + "=\"" + actionCallbackUri + "\" " +
            GatherAttributes.ATTRIBUTE_NUM_DIGITS + "=\"1\" " +
            GatherAttributes.ATTRIBUTE_TIME_OUT + "=\"60\">" +
            "</Gather></Response>";
    private String gatherEmpty = "<Response><Gather " +
            GatherAttributes.ATTRIBUTE_PARTIAL_RESULT_CALLBACK + "=\"" + partialCallbackUri + "\">" +
            "</Gather></Response>";
    private String gatherRcmlWithHints = "<Response><Gather " +
            GatherAttributes.ATTRIBUTE_INPUT + "=\"speech\" " +
            GatherAttributes.ATTRIBUTE_ACTION + "=\"" + actionCallbackUri + "\" " +
            GatherAttributes.ATTRIBUTE_NUM_DIGITS + "=\"1\" " +
            GatherAttributes.ATTRIBUTE_TIME_OUT + "=\"60\" " +
            GatherAttributes.ATTRIBUTE_HINTS + "=\"" + "Telestax’s RestcommONE platform is changing the way real-time communications are developed and delivered. " +
            "It is fast becoming the platform of choice for rapidly building enterprise class real-time messaging, voice and video applications. " +
            "Our platform is scalable, highly available and the only WebRTC platform that supports cloud, on premise and hybrid deployment configurations.\">" +
            "</Gather></Response>";

    public GatherSpeechTest() {
        super();
    }

    @BeforeClass
    public static void before() throws Exception {
        uriUtils = Mockito.mock(UriUtils.class);
        RestcommConnectServiceProvider.getInstance().setUriUtils(uriUtils);

        Mockito.when(uriUtils.resolveWithBase(Mockito.any(URI.class), Mockito.any(URI.class))).thenAnswer(new Answer() {
            public Object answer (InvocationOnMock invocation) {
                return invocation.getArguments()[1];
            }});

        Mockito.when(uriUtils.resolve(Mockito.any(URI.class), Mockito.any(Sid.class))).thenAnswer(new Answer() {
            public Object answer (InvocationOnMock invocation) {
                return invocation.getArguments()[0];
            }});

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

    private HttpResponseDescriptor getOk(URI uri) {
        HttpResponseDescriptor.Builder builder = HttpResponseDescriptor.builder();
        builder.setURI(uri);
        builder.setStatusCode(200);
        builder.setStatusDescription("OK");
        builder.setContentLength(0);
        return builder.build();
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

    private TestActorRef<VoiceInterpreter> createVoiceInterpreter(final ActorRef observer) {
        //dao
        final CallDetailRecordsDao recordsDao = mock(CallDetailRecordsDao.class);
        when(recordsDao.getCallDetailRecord(any(Sid.class))).thenReturn(null);

        final DaoManager storage = mock(DaoManager.class);
        when(storage.getCallDetailRecordsDao()).thenReturn(recordsDao);

        //actors
        final ActorRef downloader = new MockedActor("downloader")
                .add(DiskCacheRequest.class, new DiskCacheRequestProperty(playUri), new DiskCacheResponse(playUri))
                .asRef(system);

        final ActorRef callManager = new MockedActor("callManager").asRef(system);

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
                return new VoiceInterpreter(builder.build()) {
                    @Override
                    protected ActorRef downloader() {
                        return observer;
                    }

                    @Override
                    protected ActorRef httpAsycClientHelper(){
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
                };
            }
        });
        return TestActorRef.create(system, props, "VoiceInterpreter" + System.currentTimeMillis());//system.actorOf(props);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testPartialHangupScenario() throws Exception {
        new JavaTestKit(system) {
            {
                final ActorRef observer = getRef();
                final ActorRef interpreter = createVoiceInterpreter(observer);
                interpreter.tell(new StartInterpreter(observer), observer);

                expectMsgClass(GetCallInfo.class);
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

                //wait for rcml downloading
                HttpRequestDescriptor callback = expectMsgClass(HttpRequestDescriptor.class);
                assertEquals(callback.getUri(), requestUri);

                interpreter.tell(new DownloaderResponse(getOkRcml(requestUri, gatherRcmlPartial)), observer);

                expectMsgClass(Collect.class);

                //generate partial response1
                interpreter.tell(new MediaGroupResponse(new CollectedResult("1", true, true)), observer);

                callback = expectMsgClass(HttpRequestDescriptor.class);
                assertEquals(callback.getUri(), partialCallbackUri);
                assertEquals(findParam(callback.getParameters(), "UnstableSpeechResult").getValue(), "1");

                interpreter.tell(new DownloaderResponse(getOkRcml(partialCallbackUri, "")), observer);

                //generate partial response2
                interpreter.tell(new MediaGroupResponse(new CollectedResult("12", true, true)), observer);

                callback = expectMsgClass(HttpRequestDescriptor.class);
                assertEquals(callback.getUri(), partialCallbackUri);
                assertEquals(findParam(callback.getParameters(), "UnstableSpeechResult").getValue(), "12");

                //generate final response
                interpreter.tell(new MediaGroupResponse(new CollectedResult("Hello. World.", true, false)), observer);

                callback = expectMsgClass(HttpRequestDescriptor.class);
                assertEquals(actionCallbackUri, callback.getUri());
                assertEquals(findParam(callback.getParameters(), "SpeechResult").getValue(), "Hello. World.");

                interpreter.tell(new DownloaderResponse(getOkRcml(actionCallbackUri, endRcml)), observer);

                expectMsgClass(Hangup.class);
            }
        };
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testFinalResultAndHangupScenario() throws Exception {
        new JavaTestKit(system) {
            {
                final ActorRef observer = getRef();
                final ActorRef interpreter = createVoiceInterpreter(observer);
                interpreter.tell(new StartInterpreter(observer), observer);

                expectMsgClass(GetCallInfo.class);
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

                //wait for rcml downloading
                HttpRequestDescriptor callback = expectMsgClass(HttpRequestDescriptor.class);
                assertEquals(callback.getUri(), requestUri);

                interpreter.tell(new DownloaderResponse(getOkRcml(requestUri, gatherRcmlNoPartial)), observer);

                expectMsgClass(Collect.class);

                //generate final response
                interpreter.tell(new MediaGroupResponse(new CollectedResult("Hello. World.", true, false)), observer);

                callback = expectMsgClass(HttpRequestDescriptor.class);
                assertEquals(callback.getUri(), actionCallbackUri);
                assertEquals(findParam(callback.getParameters(), "SpeechResult").getValue(), "Hello. World.");

                interpreter.tell(new DownloaderResponse(getOkRcml(actionCallbackUri, endRcml)), observer);

                expectMsgClass(Hangup.class);
            }
        };
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testPartialAndPlayScenario() throws Exception {
        new JavaTestKit(system) {
            {
                final ActorRef observer = getRef();
                final ActorRef interpreter = createVoiceInterpreter(observer);
                interpreter.tell(new StartInterpreter(observer), observer);

                expectMsgClass(GetCallInfo.class);
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

                //wait for rcml downloading
                HttpRequestDescriptor callback = expectMsgClass(HttpRequestDescriptor.class);
                assertEquals(callback.getUri(), requestUri);

                interpreter.tell(new DownloaderResponse(getOkRcml(requestUri, gatherRcmlPartial)), observer);

                expectMsgClass(Collect.class);

                //generate partial response1
                interpreter.tell(new MediaGroupResponse(new CollectedResult("1", true, true)), observer);

                callback = expectMsgClass(HttpRequestDescriptor.class);
                assertEquals(callback.getUri(), partialCallbackUri);
                assertEquals(findParam(callback.getParameters(), "UnstableSpeechResult").getValue(), "1");

                interpreter.tell(new DownloaderResponse(getOk(partialCallbackUri)), observer);

                //generate partial response2
                interpreter.tell(new MediaGroupResponse(new CollectedResult("12", true, true)), observer);

                callback = expectMsgClass(HttpRequestDescriptor.class);
                assertEquals(callback.getUri(), partialCallbackUri);
                assertEquals(findParam(callback.getParameters(), "UnstableSpeechResult").getValue(), "12");

                //generate final response
                interpreter.tell(new MediaGroupResponse(new CollectedResult("Hello. World.", true, false)), observer);

                callback = expectMsgClass(HttpRequestDescriptor.class);
                assertEquals(callback.getUri(), actionCallbackUri);
                assertEquals(findParam(callback.getParameters(), "SpeechResult").getValue(), "Hello. World.");

                interpreter.tell(new DownloaderResponse(getOkRcml(actionCallbackUri, playRcml)), observer);

                //wait for new tag: Play
                DiskCacheRequest diskCacheRequest = expectMsgClass(DiskCacheRequest.class);

                interpreter.tell(new DiskCacheResponse(diskCacheRequest.uri()), observer);

                expectMsgClass(Play.class);

                //simulate play is finished
                interpreter.tell(new MediaGroupResponse(new CollectedResult("", false, false)), observer);

                expectMsgClass(Hangup.class);
            }
        };
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testFinalResultAndPlayScenario() throws Exception {
        new JavaTestKit(system) {
            {
                final ActorRef observer = getRef();
                final ActorRef interpreter = createVoiceInterpreter(observer);
                interpreter.tell(new StartInterpreter(observer), observer);

                expectMsgClass(GetCallInfo.class);
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

                //wait for rcml downloading
                HttpRequestDescriptor callback = expectMsgClass(HttpRequestDescriptor.class);
                assertEquals(callback.getUri(), requestUri);

                interpreter.tell(new DownloaderResponse(getOkRcml(requestUri, gatherRcmlNoPartial)), observer);

                expectMsgClass(Collect.class);

                //generate final response
                interpreter.tell(new MediaGroupResponse(new CollectedResult("Hello. World.", true, false)), observer);

                callback = expectMsgClass(HttpRequestDescriptor.class);
                assertEquals(callback.getUri(), actionCallbackUri);
                assertEquals(findParam(callback.getParameters(), "SpeechResult").getValue(), "Hello. World.");

                interpreter.tell(new DownloaderResponse(getOkRcml(actionCallbackUri, playRcml)), observer);

                //wait for new tag: Play
                DiskCacheRequest diskCacheRequest = expectMsgClass(DiskCacheRequest.class);

                interpreter.tell(new DiskCacheResponse(diskCacheRequest.uri()), observer);

                expectMsgClass(Play.class);

                //simulate play is finished
                interpreter.tell(new MediaGroupResponse(new CollectedResult("", false, false)), observer);

                expectMsgClass(Hangup.class);
            }
        };
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testValidateDefaultAttributeValues() throws Exception {
        new JavaTestKit(system) {
            {
                final ActorRef observer = getRef();
                final ActorRef interpreter = createVoiceInterpreter(observer);
                interpreter.tell(new StartInterpreter(observer), observer);

                expectMsgClass(GetCallInfo.class);
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

                //wait for rcml downloading
                HttpRequestDescriptor callback = expectMsgClass(HttpRequestDescriptor.class);
                assertEquals(callback.getUri(), requestUri);

                interpreter.tell(new DownloaderResponse(getOkRcml(requestUri, gatherEmpty)), observer);
                Collect collect = expectMsgClass(Collect.class);
                assertEquals(Collect.Type.DTMF, collect.type());
                assertEquals("en-US", collect.lang());
                assertEquals("#", collect.endInputKey());
                assertSame(5, collect.timeout());
                assertEquals("google", collect.getDriver());
            }
        };
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testOnMediaGroupResponseEmptyCollectedResult() throws Exception {
        new JavaTestKit(system) {
            {
                final ActorRef observer = getRef();
                final TestActorRef<VoiceInterpreter> interpreterRef = createVoiceInterpreter(observer);
                VoiceInterpreter interpreter = interpreterRef.underlyingActor();
                interpreter.fsm = spy(new FiniteStateMachine(interpreter.gathering, interpreter.transitions));
                doNothing().when(interpreter.fsm).transition(any(), eq(interpreter.finishGathering));
                interpreter.collectedDigits = new StringBuffer();
                interpreterRef.tell(new MediaGroupResponse(new CollectedResult("", false, false)), observer);
                assertSame(0, interpreter.collectedDigits.length());
            }
        };
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testIgnoreRcmlFromPartialCallback() throws Exception {
        new JavaTestKit(system) {
            {
                final ActorRef observer = getRef();
                final TestActorRef<VoiceInterpreter> interpreterRef = createVoiceInterpreter(observer);
                VoiceInterpreter interpreter = interpreterRef.underlyingActor();
                interpreter.fsm = spy(new FiniteStateMachine(interpreter.gathering, interpreter.transitions));
                interpreterRef.tell(new DownloaderResponse(getOk(partialCallbackUri)), observer);
                verify(interpreter.fsm, never()).transition(any(), any(State.class));
            }
        };
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testDtmfFromMediaServer() throws Exception {
        new JavaTestKit(system) {
            {
                final ActorRef observer = getRef();
                final TestActorRef<VoiceInterpreter> interpreterRef = createVoiceInterpreter(observer);
                VoiceInterpreter interpreter = interpreterRef.underlyingActor();
                interpreter.fsm = spy(new FiniteStateMachine(interpreter.gathering, interpreter.transitions));
                doNothing().when(interpreter.fsm).transition(any(), eq(interpreter.finishGathering));
                interpreter.collectedDigits = new StringBuffer();
                interpreter.finishOnKey="#";
                interpreterRef.tell(new MediaGroupResponse(new CollectedResult("5", false, false)), observer);
                assertEquals("5", interpreter.collectedDigits.toString());
            }
        };
    }

    @Test
    public void testHintsLimits() {
        new JavaTestKit(system) {
            {
                final ActorRef observer = getRef();
                final ActorRef interpreter = createVoiceInterpreter(observer);
                interpreter.tell(new StartInterpreter(observer), observer);

                expectMsgClass(GetCallInfo.class);
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

                //wait for rcml downloading
                HttpRequestDescriptor callback = expectMsgClass(HttpRequestDescriptor.class);
                assertEquals(callback.getUri(), requestUri);

                interpreter.tell(new DownloaderResponse(getOkRcml(requestUri, gatherRcmlWithHints)), observer);

                expectMsgClass(Hangup.class);
            }
        };
    }

    @Test
    public void testStartInputKeys() {
        final String inputKeys = "1234";
        final String GATHER_WITH_START_INPUT = "<Response><Gather " +
            GatherAttributes.ATTRIBUTE_PARTIAL_RESULT_CALLBACK + "=\"" + partialCallbackUri + "\" " +
            GatherAttributes.ATTRIBUTE_TIME_OUT + "=\"60\" " +
            GatherAttributes.ATTRIBUTE_START_INPUT_KEY + "=\"" + inputKeys + "\" " +
            ">" +
            "</Gather></Response>";
        new JavaTestKit(system) {
            {
                final ActorRef observer = getRef();
                final ActorRef interpreter = createVoiceInterpreter(observer);
                interpreter.tell(new StartInterpreter(observer), observer);

                expectMsgClass(GetCallInfo.class);
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

                //wait for rcml downloading
                HttpRequestDescriptor callback = expectMsgClass(HttpRequestDescriptor.class);
                assertEquals(callback.getUri(), requestUri);

                interpreter.tell(new DownloaderResponse(getOkRcml(requestUri, GATHER_WITH_START_INPUT)), observer);

                Collect collectCmd = expectMsgClass(Collect.class);
                Assert.assertTrue( collectCmd.hasStartInputKey());
                assertEquals(inputKeys, collectCmd.startInputKey());
            }
        };
    }

    private NameValuePair findParam(final List<NameValuePair> params, final String key) {
        return Iterables.find(params, new Predicate<NameValuePair>() {
            public boolean apply(NameValuePair p) {
                return key.equals(p.getName());
            }
        });
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
