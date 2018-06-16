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
package org.restcomm.connect.mscontrol.api.messages;

import org.restcomm.connect.commons.annotations.concurrency.Immutable;

import java.net.URI;
import java.util.List;

/**
 * @author quintana.thomas@gmail.com (Thomas Quintana)
 */
@Immutable
public final class Collect {

    public enum Type {
        DTMF, SPEECH, DTMF_SPEECH;

        public static Type parseOrDefault(String name, Type defaultValue){
            try {
                return "DTMF SPEECH".equalsIgnoreCase(name) ? DTMF_SPEECH : Type.valueOf(name.toUpperCase());
            } catch (Exception e) {
                return defaultValue;
            }
        }
    }

    private final Type type;
    private final List<URI> prompts;
    private final String pattern;
    private final int timeout;
    private final String endInputKey;
    private final String startInputKey;
    private final int numberOfDigits;
    private final String lang;
    private final String hints;
    private final String driver;
    private final boolean partialResult;

    public Collect(String driver,final Type type, final List<URI> prompts, final String pattern, final int timeout, final String endInputKey,
            final String startInputKey, final int numberOfDigits, final String lang, final String hints, final boolean partialResult) {
        super();
        this.driver = driver;
        this.type = type;
        this.prompts = prompts;
        this.pattern = pattern;
        this.timeout = timeout;
        this.endInputKey = endInputKey;
        this.startInputKey = startInputKey;
        this.numberOfDigits = numberOfDigits;
        this.lang = lang;
        this.hints = hints;
        this.partialResult = partialResult;
    }

    public String getDriver() {
        return driver;
    }

    public Type type() {
        return type;
    }

    public String lang() {
        return lang;
    }

    public List<URI> prompts() {
        return prompts;
    }

    public boolean hasPrompts() {
        return (prompts != null && !prompts.isEmpty());
    }

    public String pattern() {
        return pattern;
    }

    public boolean hasPattern() {
        return (pattern != null && !pattern.isEmpty());
    }

    public int timeout() {
        return timeout;
    }

    public String endInputKey() {
        return endInputKey;
    }

    public boolean hasEndInputKey() {
        return (endInputKey != null && !endInputKey.isEmpty());
    }

    public String startInputKey() { return startInputKey; }

    public boolean hasStartInputKey() {return (startInputKey != null && !startInputKey.isEmpty()); }

    public int numberOfDigits() {
        return numberOfDigits;
    }

    public String getHints() {
        return hints;
    }

    public boolean needPartialResult() {
        return partialResult;
    }
}
