package org.restcomm.connect.interpreter.rcml;

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

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.actor.UntypedActorFactory;
import akka.testkit.JavaTestKit;
import gov.nist.javax.sdp.fields.Email;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.InputStream;
import java.util.List;

import static org.junit.Assert.assertTrue;
import static org.restcomm.connect.interpreter.rcml.Verbs.dial;
import static org.restcomm.connect.interpreter.rcml.Verbs.gather;
import static org.restcomm.connect.interpreter.rcml.Verbs.pause;
import static org.restcomm.connect.interpreter.rcml.Verbs.play;
import static org.restcomm.connect.interpreter.rcml.Verbs.record;
import static org.restcomm.connect.interpreter.rcml.Verbs.say;
import static org.restcomm.connect.interpreter.rcml.Verbs.email;
import static org.restcomm.connect.interpreter.rcml.Verbs.sms;

/**
 * @author quintana.thomas@gmail.com (Thomas Quintana)
 */
public final class ParserTest {
    private static ActorSystem system;

    public ParserTest() {
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

    private ActorRef parser(final InputStream input) {
        return system.actorOf(new Props(new UntypedActorFactory() {
            private static final long serialVersionUID = 1L;

            @Override
            public UntypedActor create() throws Exception {
                return new Parser(input, null, null);
            }
        }));
    }

    private ActorRef parser(final String input) {
        return system.actorOf(new Props(new UntypedActorFactory() {
            private static final long serialVersionUID = 1L;

            @Override
            public UntypedActor create() throws Exception {
                return new Parser(input, null);
            }
        }));
    }

    @Test
    public void testParser() {
        final InputStream input = getClass().getResourceAsStream("/rcml.xml");
        new JavaTestKit(system) {
            {
                final ActorRef observer = getRef();
                // Create a new parser.
                final GetNextVerb next = new GetNextVerb();
                final ActorRef parser = parser(input);
                // Start consuming verbs until the end of the document.
                parser.tell(next, observer);
                Tag verb = expectMsgClass(Tag.class);
               
                /////////create depending tag type
                
                assertTrue(record.equals(verb.name()));
                assertTrue(verb.attribute("action").value().equals("https://127.0.0.1:8080/restcomm/demos/hello-world.jsp"));
                assertTrue(verb.attribute("method").value().equals("GET"));
                assertTrue(verb.attribute("maxLength").value().equals("60"));
                assertTrue(verb.attribute("timeout").value().equals("5"));
                assertTrue(verb.attribute("finishOnKey").value().equals("#"));
                assertTrue(verb.attribute("transcribe").value().equals("true"));
                assertTrue(verb.attribute("transcribeCallback").value().equals("transcribe.jsp"));
                assertTrue(verb.attribute("playBeep").value().equals("false"));
                parser.tell(next, observer);
                verb = expectMsgClass(Tag.class);
                
                assertTrue(gather.equals(verb.name()));
                assertTrue(verb.attribute("timeout").value().equals("30"));
                assertTrue(verb.attribute("finishOnKey").value().equals("#"));
                assertTrue(verb.hasChildren());
                final List<Tag> children = verb.children();
                Tag child = children.get(0);
                
                assertTrue(say.equals(child.name()));
                assertTrue(child.attribute("voice").value().equals("man"));
                assertTrue(child.attribute("language").value().equals("en"));
                assertTrue(child.attribute("loop").value().equals("1"));
                assertTrue(child.text().equals("Hello World!"));
                child = children.get(1);
                
                assertTrue(play.equals(child.name()));
                assertTrue(child.attribute("loop").value().equals("1"));
                assertTrue(child.text().equals("https://127.0.0.1:8080/restcomm/audio/hello-world.wav"));
                child = children.get(2);
                
                assertTrue(pause.equals(child.name()));
                assertTrue(child.attribute("length").value().equals("1"));
                parser.tell(next, observer);
                verb = expectMsgClass(Tag.class);
                
                assertTrue(email.equals(verb.name()));
                assertTrue(verb.attribute("from").value().equals("carlos@gmail.com"));
                assertTrue(verb.attribute("to").value().equals("carlos@hotmail.com"));
                assertTrue(verb.attribute("cc").value().equals("someone2@localhost.com, test@localhost.com, test3@localhost.com"));
                assertTrue(verb.attribute("subject").value().equals("This is the subject"));
                assertTrue(verb.text().equals("This is the body!!"));      
                parser.tell(next, observer);
                verb = expectMsgClass(Tag.class);
                
                assertTrue(sms.equals(verb.name()));
                assertTrue(verb.attribute("from").value().equals("+555789211"));
                assertTrue(verb.attribute("to").value().equals("+555789211"));
                assertTrue(verb.attribute("statusCallback").value().equals("transcribe.jsp"));
                parser.tell(next, observer);
                expectMsgClass(End.class);
                
                
                
                
            }
        };
    }

    @Test
    // Test for SIP Noun Parsing for Issue
    // https://bitbucket.org/telestax/telscale-restcomm/issue/132/implement-twilio-sip-out
    public void testParserDialSIP() {
        final InputStream input = getClass().getResourceAsStream("/rcml-sip.xml");
        new JavaTestKit(system) {
            {
                final ActorRef observer = getRef();
                // Create a new parser.
                final GetNextVerb next = new GetNextVerb();
                final ActorRef parser = parser(input);
                // Start consuming verbs until the end of the document.
                parser.tell(next, observer);
                Tag verb = expectMsgClass(Tag.class);
                assertTrue(dial.equals(verb.name()));
                assertTrue(verb.attribute("record").value().equals("true"));
                assertTrue(verb.attribute("timeout").value().equals("10"));
                assertTrue(verb.attribute("hangupOnStar").value().equals("true"));
                assertTrue(verb.attribute("callerId").value().equals("bob"));
                assertTrue(verb.attribute("method").value().equals("POST"));
                assertTrue(verb.attribute("action").value().equals("/handle_post_dial"));
                final List<Tag> children = verb.children();
                Tag child = children.get(0);
                assertTrue(Nouns.SIP.equals(child.name()));
                assertTrue(child.attribute("username").value().equals("admin"));
                assertTrue(child.attribute("password").value().equals("1234"));
                assertTrue(child.attribute("method").value().equals("POST"));
                assertTrue(child.attribute("url").value().equals("/handle_screening_on_answer"));
                assertTrue(child.text().equals("sip:kate@example.com?customheader=foo"));
                parser.tell(next, observer);
                expectMsgClass(End.class);
            }
        };
    }

    
    @Test
    // Test for SIP Noun Parsing for Issue
    // https://bitbucket.org/telestax/telscale-restcomm/issue/132/implement-twilio-sip-out
    public void testParserDialSIPText() {
        final String rcmlContent = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + "<Response>\n" + "<Dial\n"
                + " record=\"true\"\n" + " timeout=\"10\"\n" + " hangupOnStar=\"true\"\n" + " callerId=\"bob\"\n"
                + " method=\"POST\"\n" + " action=\"/handle_post_dial\">\n" +

                "<Sip \n" + " username=\"admin\"\n" + " password=\"1234\"\n" + " method=\"POST\"\n"
                + " url=\"/handle_screening_on_answer\">\n" + "sip:kate@example.com?customheader=foo&myotherheader=bar\n"
                + "</Sip>\n" + "</Dial>\n" + "</Response>";
        // final InputStream input = getClass().getResourceAsStream("/rcml-sip.xml");
        new JavaTestKit(system) {
            {
                final ActorRef observer = getRef();
                // Create a new parser.
                final GetNextVerb next = new GetNextVerb();
                System.out.println(rcmlContent);
                final ActorRef parser = parser(rcmlContent);
                // Start consuming verbs until the end of the document.
                parser.tell(next, observer);
                Tag verb = expectMsgClass(Tag.class);
                assertTrue(dial.equals(verb.name()));
                assertTrue(verb.attribute("record").value().equals("true"));
                assertTrue(verb.attribute("timeout").value().equals("10"));
                assertTrue(verb.attribute("hangupOnStar").value().equals("true"));
                assertTrue(verb.attribute("callerId").value().equals("bob"));
                assertTrue(verb.attribute("method").value().equals("POST"));
                assertTrue(verb.attribute("action").value().equals("/handle_post_dial"));
                final List<Tag> children = verb.children();
                Tag child = children.get(0);
                assertTrue(Nouns.SIP.equals(child.name()));
                assertTrue(child.attribute("username").value().equals("admin"));
                assertTrue(child.attribute("password").value().equals("1234"));
                assertTrue(child.attribute("method").value().equals("POST"));
                assertTrue(child.attribute("url").value().equals("/handle_screening_on_answer"));
                assertTrue(child.text().equals("sip:kate@example.com?customheader=foo&myotherheader=bar"));
                parser.tell(next, observer);
                expectMsgClass(End.class);
            }
        };
    }
}
