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
package org.restcomm.connect.dao.entities;

import java.net.URI;

import org.joda.time.DateTime;

import org.restcomm.connect.commons.annotations.concurrency.Immutable;
import org.restcomm.connect.commons.annotations.concurrency.NotThreadSafe;
import org.restcomm.connect.commons.configuration.RestcommConfiguration;
import org.restcomm.connect.commons.dao.Sid;
import org.restcomm.connect.commons.util.DigestAuthentication;

/**
 * @author quintana.thomas@gmail.com (Thomas Quintana)
 */
@Immutable
public final class Client {
    public static final int DISABLED = 0;
    public static final int ENABLED = 1;

    private final Sid sid;
    private final DateTime dateCreated;
    private final DateTime dateUpdated;
    private final Sid accountSid;
    private final String apiVersion;
    private final String friendlyName;
    private final String login;
    private final String password;
    private final Integer status;
    private final URI voiceUrl;
    private final String voiceMethod;
    private final URI voiceFallbackUrl;
    private final String voiceFallbackMethod;
    private final Sid voiceApplicationSid;
    private final URI uri;
    private final String pushClientIdentity;
    private final String passwordAlgorithm;

    public Client(final Sid sid, final DateTime dateCreated, final DateTime dateUpdated, final Sid accountSid,
            final String apiVersion, final String friendlyName, final String login, final String password,
            final Integer status, final URI voiceUrl, final String voiceMethod, final URI voiceFallbackUrl,
            String voiceFallbackMethod, final Sid voiceApplicationSid, final URI uri, final String pushClientIdentity, final String passwordAlgorithm) {
        super();
        this.sid = sid;
        this.dateCreated = dateCreated;
        this.dateUpdated = dateUpdated;
        this.accountSid = accountSid;
        this.apiVersion = apiVersion;
        this.friendlyName = friendlyName;
        this.login = login;
        this.password = password;
        this.status = status;
        this.voiceUrl = voiceUrl;
        this.voiceMethod = voiceMethod;
        this.voiceFallbackUrl = voiceFallbackUrl;
        this.voiceFallbackMethod = voiceFallbackMethod;
        this.voiceApplicationSid = voiceApplicationSid;
        this.uri = uri;
        this.pushClientIdentity = pushClientIdentity;
        this.passwordAlgorithm = passwordAlgorithm;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Sid getSid() {
        return sid;
    }

    public DateTime getDateCreated() {
        return dateCreated;
    }

    public DateTime getDateUpdated() {
        return dateUpdated;
    }

    public Sid getAccountSid() {
        return accountSid;
    }

    public String getApiVersion() {
        return apiVersion;
    }

    public String getFriendlyName() {
        return friendlyName;
    }

    public String getLogin() {
        return login;
    }

    public String getPassword() {
        return password;
    }

    public Integer getStatus() {
        return status;
    }

    public URI getVoiceUrl() {
        return voiceUrl;
    }

    public String getVoiceMethod() {
        return voiceMethod;
    }

    public URI getVoiceFallbackUrl() {
        return voiceFallbackUrl;
    }

    public String getVoiceFallbackMethod() {
        return voiceFallbackMethod;
    }

    public Sid getVoiceApplicationSid() {
        return voiceApplicationSid;
    }

    public URI getUri() {
        return uri;
    }

    public String getPushClientIdentity() {
        return pushClientIdentity;
    }

    public String getPasswordAlgorithm() { return passwordAlgorithm; }

    public Client setFriendlyName(final String friendlyName) {
        return new Client(sid, dateCreated, DateTime.now(), accountSid, apiVersion, friendlyName, login, password, status,
                voiceUrl, voiceMethod, voiceFallbackUrl, voiceFallbackMethod, voiceApplicationSid, uri, pushClientIdentity, passwordAlgorithm);
    }

    /**
     * @param login
     * @param password
     * @param realm
     * @return
     */
    public Client setPassword(final String login, final String password, final String realm) {
        return new Client(sid, dateCreated, DateTime.now(), accountSid, apiVersion, friendlyName, login, DigestAuthentication.HA1(login, realm, password), status,
                voiceUrl, voiceMethod, voiceFallbackUrl, voiceFallbackMethod, voiceApplicationSid, uri, pushClientIdentity, RestcommConfiguration.getInstance().getMain().getClientAlgorithm());
    }

    public Client setStatus(final int status) {
        return new Client(sid, dateCreated, DateTime.now(), accountSid, apiVersion, friendlyName, login, password, status,
                voiceUrl, voiceMethod, voiceFallbackUrl, voiceFallbackMethod, voiceApplicationSid, uri, pushClientIdentity, passwordAlgorithm);
    }

    public Client setVoiceUrl(final URI voiceUrl) {
        return new Client(sid, dateCreated, DateTime.now(), accountSid, apiVersion, friendlyName, login, password, status,
                voiceUrl, voiceMethod, voiceFallbackUrl, voiceFallbackMethod, voiceApplicationSid, uri, pushClientIdentity, passwordAlgorithm);
    }

    public Client setVoiceMethod(final String voiceMethod) {
        return new Client(sid, dateCreated, DateTime.now(), accountSid, apiVersion, friendlyName, login, password, status,
                voiceUrl, voiceMethod, voiceFallbackUrl, voiceFallbackMethod, voiceApplicationSid, uri, pushClientIdentity, passwordAlgorithm);
    }

    public Client setVoiceFallbackUrl(final URI voiceFallbackUrl) {
        return new Client(sid, dateCreated, DateTime.now(), accountSid, apiVersion, friendlyName, login, password, status,
                voiceUrl, voiceMethod, voiceFallbackUrl, voiceFallbackMethod, voiceApplicationSid, uri, pushClientIdentity, passwordAlgorithm);
    }

    public Client setVoiceFallbackMethod(final String voiceFallbackMethod) {
        return new Client(sid, dateCreated, DateTime.now(), accountSid, apiVersion, friendlyName, login, password, status,
                voiceUrl, voiceMethod, voiceFallbackUrl, voiceFallbackMethod, voiceApplicationSid, uri, pushClientIdentity, passwordAlgorithm);
    }

    public Client setVoiceApplicationSid(final Sid voiceApplicationSid) {
        return new Client(sid, dateCreated, DateTime.now(), accountSid, apiVersion, friendlyName, login, password, status,
                voiceUrl, voiceMethod, voiceFallbackUrl, voiceFallbackMethod, voiceApplicationSid, uri, pushClientIdentity, passwordAlgorithm);
    }

    public Client setPushClientIdentity(final String pushClientIdentity) {
        return new Client(sid, dateCreated, DateTime.now(), accountSid, apiVersion, friendlyName, login, password, status,
                voiceUrl, voiceMethod, voiceFallbackUrl, voiceFallbackMethod, voiceApplicationSid, uri, pushClientIdentity, passwordAlgorithm);
    }

    public Client setPasswordAlgorithm(final String passwordAlgorithm) {
        return new Client(sid, dateCreated, DateTime.now(), accountSid, apiVersion, friendlyName, login, password, status,
                voiceUrl, voiceMethod, voiceFallbackUrl, voiceFallbackMethod, voiceApplicationSid, uri, pushClientIdentity, passwordAlgorithm);
    }

    @NotThreadSafe
    public static final class Builder {
        private Sid sid;
        private Sid accountSid;
        private String apiVersion;
        private String friendlyName;
        private String login;
        private String password;
        private int status;
        private URI voiceUrl;
        private String voiceMethod;
        private URI voiceFallbackUrl;
        private String voiceFallbackMethod;
        private Sid voiceApplicationSid;
        private URI uri;
        private String pushClientIdentity;
        private String passwordAlgorithm;

        private Builder() {
            super();
        }

        public Client build() {
            final DateTime now = DateTime.now();
            return new Client(sid, now, now, accountSid, apiVersion, friendlyName, login, password, status, voiceUrl,
                    voiceMethod, voiceFallbackUrl, voiceFallbackMethod, voiceApplicationSid, uri, pushClientIdentity, passwordAlgorithm);
        }

        public void setSid(final Sid sid) {
            this.sid = sid;
        }

        public void setAccountSid(final Sid accountSid) {
            this.accountSid = accountSid;
        }

        public void setApiVersion(final String apiVersion) {
            this.apiVersion = apiVersion;
        }

        public void setFriendlyName(final String friendlyName) {
            this.friendlyName = friendlyName;
        }

        public void setLogin(final String login) {
            this.login = login;
        }

        /**
         * @param login
         * @param password
         * @param realm
         * @param algorithm TODO
         */
        public void setPassword(final String login, final String password, final String realm, String algorithm) {
            if(!algorithm.isEmpty()) {
                this.password = DigestAuthentication.HA1(login, realm, password);
            }else {
                this.password = password;
            }
        }

        public void setStatus(final int status) {
            this.status = status;
        }

        public void setVoiceUrl(final URI voiceUrl) {
            this.voiceUrl = voiceUrl;
        }

        public void setVoiceMethod(final String voiceMethod) {
            this.voiceMethod = voiceMethod;
        }

        public void setVoiceFallbackUrl(final URI voiceFallbackUrl) {
            this.voiceFallbackUrl = voiceFallbackUrl;
        }

        public void setVoiceFallbackMethod(final String voiceFallbackMethod) {
            this.voiceFallbackMethod = voiceFallbackMethod;
        }

        public void setVoiceApplicationSid(final Sid voiceApplicationSid) {
            this.voiceApplicationSid = voiceApplicationSid;
        }

        public void setUri(final URI uri) {
            this.uri = uri;
        }

        public void setPushClientIdentity(String pushClientIdentity) {
            this.pushClientIdentity = pushClientIdentity;
        }

        public void setPasswordAlgorithm(final String passwordAlgorithm) { this.passwordAlgorithm = passwordAlgorithm; }
    }
}
