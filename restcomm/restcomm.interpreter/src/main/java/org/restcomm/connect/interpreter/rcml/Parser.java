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
package org.restcomm.connect.interpreter.rcml;

import akka.actor.ActorRef;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.restcomm.connect.commons.faulttolerance.RestcommUntypedActor;
import org.restcomm.connect.interpreter.rcml.domain.GatherAttributes;

import javax.naming.LimitExceededException;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.regex.Matcher; //added
import java.util.regex.Pattern; //added


import static javax.xml.stream.XMLStreamConstants.*;

/**
 * @author quintana.thomas@gmail.com (Thomas Quintana)
 */
public final class Parser extends RestcommUntypedActor {
    private static Logger logger = Logger.getLogger(Parser.class);
    private Tag document;
    private Iterator<Tag> iterator;
    private String xml;
    private ActorRef sender;

    private Tag current;

    public Parser(final InputStream input, final String xml, final ActorRef sender) throws IOException {
        this(new InputStreamReader(input), xml, sender);
    }

    public Parser(final Reader reader, final String xml, final ActorRef sender) throws IOException {
        super();
        if(logger.isDebugEnabled()){
            logger.debug("About to create new Parser for xml: "+xml);
        }
        this.xml = xml;
        this.sender = sender;
        final XMLInputFactory inputs = XMLInputFactory.newInstance();
        inputs.setProperty("javax.xml.stream.isCoalescing", true);
        XMLStreamReader stream = null;
        try {
            stream = inputs.createXMLStreamReader(reader);
            document = parse(stream);
            if (document == null) {
                throw new IOException("There was an error parsing the RCML.");
            }
            iterator = document.iterator();
        } catch (final XMLStreamException exception) {
            if(logger.isInfoEnabled()) {
                logger.info("There was an error parsing the RCML for xml: "+xml+" excpetion: ", exception);
            }
            sender.tell(new ParserFailed(exception,xml), null);
        } finally {
            if (stream != null) {
                try {
                    stream.close();
                } catch (final XMLStreamException nested) {
                    throw new IOException(nested);
                }
            }
        }
    }

    public Parser(final String xml, final ActorRef sender) throws IOException {
        this(new StringReader(xml.trim().replaceAll("&([^;]+(?!(?:\\w|;)))", "&amp;$1")), xml, sender);
    }

    private void end(final Stack<Tag.Builder> builders, final XMLStreamReader stream) {
        if (builders.size() > 1) {
            final Tag.Builder builder = builders.pop();
            final Tag tag = builder.build();
            builders.peek().addChild(tag);
        }
    }

    private void start(final Stack<Tag.Builder> builders, final XMLStreamReader stream) {
        final Tag.Builder builder = Tag.builder();
        // Read the next tag.
        builder.setName(stream.getLocalName());
        // Read the attributes.
        final int limit = stream.getAttributeCount();
        for (int index = 0; index < limit; index++) {
            final String name = stream.getAttributeLocalName(index);
            final String value = stream.getAttributeValue(index).trim();
            final Attribute attribute = new Attribute(name, value);
            builder.addAttribute(attribute);
        }
        builders.push(builder);
    }

    private Tag next() throws LimitExceededException{
	boolean AttributteValidator; // new variable
        if (iterator != null) {
            while (iterator.hasNext()) {
                final Tag tag = iterator.next();
                if (Verbs.isVerb(tag)) {
				
					AttributteValidator=validation(tag); // add new validation, invoke to class
					
                    if (current != null && current.hasChildren()) {
                        final List<Tag> children = current.children();
                        if (children.contains(tag)) {
                            continue;
                        }
                    }
                    
                    if (tag.name().equals(Verbs.gather) && tag.hasAttribute(GatherAttributes.ATTRIBUTE_HINTS) && !StringUtils.isEmpty(tag.attribute(GatherAttributes.ATTRIBUTE_HINTS).value())) {
                        String hotWords = tag.attribute(GatherAttributes.ATTRIBUTE_HINTS).value();
                        List<String> hintList = Arrays.asList(hotWords.split(","));
                        if (hintList.size() > 50) {
                            throw new LimitExceededException("HotWords limit exceeded. There are more than 50 phrases");
                        }
                        for (String hint : hintList) {
                            if (hint.length() > 100) {
                                throw new LimitExceededException("HotWords limit exceeded. Hint with more than 100 characters found");
                            }
                        }
                    }
                    current = tag;
                    return current;
                }
            }
        } else {
            if(logger.isInfoEnabled()){
                logger.info("iterator is null");
            }
        }
        return null;
    }

	///////////////////////////////////////////// new class -- validate
    private boolean validation(Tag tag) {
    	boolean ValidationPass=true;
    	AttributeValidator AttValidator=new Validator();
    	
    	switch (tag.name()) {
    	case "Record":
        	ValidationPass=AttValidator.RecordValidator(tag);
    		break;
    	case "Dial":
    		ValidationPass=AttValidator.DialValidator(tag);
    		break;
    	case "Email":
    		ValidationPass=AttValidator.EmailValidator(tag);
    		break;
    	case "Pause":
    		ValidationPass=AttValidator.PauseValidator(tag);
    		break;
    	case "Sms":
    		ValidationPass=AttValidator.SmsAndFaxValidator(tag);
    		break;
    	case "Fax":
    		ValidationPass=AttValidator.SmsAndFaxValidator(tag);
    		break;
    	case "Gather":
    		ValidationPass=AttValidator.GatherValidator(tag);
    		break;
    	case "Play":
    		ValidationPass=AttValidator.PlayAndSayValidator(tag);
    		break;
    	case "Say":
    		ValidationPass=AttValidator.PlayAndSayValidator(tag);
    		break;
    		
    	}

    	return ValidationPass;
    	
    }
///////////////// end new class
	
    private Tag parse(final XMLStreamReader stream) throws IOException, XMLStreamException {
        final Stack<Tag.Builder> builders = new Stack<Tag.Builder>();
        while (stream.hasNext()) {
            switch (stream.next()) {
                case START_ELEMENT: {
                    start(builders, stream);
                    continue;
                }
                case CHARACTERS: {
                    text(builders, stream);
                    continue;
                }
                case END_ELEMENT: {
                    end(builders, stream);
                    continue;
                }
                case END_DOCUMENT: {
                    if (!builders.isEmpty()) {
                        return builders.pop().build();
                    }
                }
            }
        }
        return null;
    }

    @Override
    public void onReceive(final Object message) throws Exception {
        final Class<?> klass = message.getClass();
        final ActorRef self = self();
        final ActorRef sender = sender();
        if (GetNextVerb.class.equals(klass)) {
            try {
                final Tag verb = next();
                if (verb != null) {
                    sender.tell(verb, self);
                    if (logger.isDebugEnabled()) {
                        logger.debug("Parser, next verb: " + verb.toString());
                    }
                } else {
                    final End end = new End();
                    sender.tell(end, sender);
                    if (logger.isDebugEnabled()) {
                        logger.debug("Parser, next verb: " + end.toString());
                    }
                }
            } catch (LimitExceededException e) {
                logger.warn(e.getMessage());
                sender.tell(new ParserFailed(e, xml), null);
            }
        }
    }

    private void text(final Stack<Tag.Builder> builders, final XMLStreamReader stream) {
        if (!stream.isWhiteSpace()) {
            // Read the text.
            final Tag.Builder builder = builders.peek();
            final String text = stream.getText().trim();
            builder.setText(text);
        }
    }
}