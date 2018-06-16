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

import java.math.BigDecimal;
import java.net.URI;
import java.util.Currency;

import org.joda.time.DateTime;

import org.restcomm.connect.commons.annotations.concurrency.Immutable;
import org.restcomm.connect.commons.annotations.concurrency.NotThreadSafe;
import org.restcomm.connect.commons.dao.Sid;
import org.restcomm.connect.commons.dao.MessageError;
import org.restcomm.connect.commons.stream.StreamEvent;

/**
 * @author quintana.thomas@gmail.com (Thomas Quintana)
 * @author mariafarooq
 */
@Immutable
public class SmsMessage implements StreamEvent {
    public static final int MAX_SIZE = 160;
    private final Sid sid;
    private final DateTime dateCreated;
    private final DateTime dateUpdated;
    private final DateTime dateSent;
    private final Sid accountSid;
    private final String sender;
    private final String recipient;
    private final String body;
    private final Status status;
    private final Direction direction;
    private final BigDecimal price;
    private final Currency priceUnit;

    private final String apiVersion;
    private final URI uri;
    private final String smppMessageId;
    private final MessageError error;

    public SmsMessage(final Sid sid, final DateTime dateCreated, final DateTime dateUpdated, final DateTime dateSent,
            final Sid accountSid, final String sender, final String recipient, final String body, final Status status,
            final Direction direction, final BigDecimal price, final Currency priceUnit, final String apiVersion, final URI uri,
            final String smppMessageId, final MessageError error) {
        super();
        this.sid = sid;
        this.dateCreated = dateCreated;
        this.dateUpdated = dateUpdated;
        this.dateSent = dateSent;
        this.accountSid = accountSid;
        this.sender = sender;
        this.recipient = recipient;
        this.body = body;
        this.status = status;
        this.direction = direction;
        this.price = price;
        this.priceUnit = priceUnit;
        this.apiVersion = apiVersion;
        this.uri = uri;
        this.smppMessageId = smppMessageId;
        this.error = error;
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

    public DateTime getDateSent() {
        return dateSent;
    }

    public Sid getAccountSid() {
        return accountSid;
    }

    public String getSender() {
        return sender;
    }

    public String getRecipient() {
        return recipient;
    }

    public String getBody() {
        return body;
    }

    public Status getStatus() {
        return status;
    }

    public Direction getDirection() {
        return direction;
    }

    public BigDecimal getPrice() {
        return (price == null) ? new BigDecimal("0.0") : price;
    }

    public Currency getPriceUnit() {
        return (priceUnit == null) ? Currency.getInstance("USD") : priceUnit;
    }

    public String getApiVersion() {
        return apiVersion;
    }

    public URI getUri() {
        return uri;
    }

    public String getSmppMessageId() {
        return smppMessageId;
    }

    public MessageError getError() {
        return error;
    }

    @NotThreadSafe
    public static final class Builder {
        private Sid sid;
        private DateTime dateSent;
        private Sid accountSid;
        private String sender;
        private String recipient;
        private String body;
        private Status status;
        private Direction direction;
        private BigDecimal price;
        private Currency priceUnit;
        private String apiVersion;
        private URI uri;
        private DateTime dateCreated;
        private DateTime dateUpdated;
        private String smppMessageId;
        private MessageError error;

        private Builder() {
            super();
        }

        public SmsMessage build() {
            if (dateCreated == null) {
                dateCreated = DateTime.now();
            }
            if (dateUpdated == null) {
                dateUpdated = dateCreated;
            }
            return new SmsMessage(sid, dateCreated, dateUpdated, dateSent, accountSid, sender, recipient, body, status, direction, price,
                    priceUnit, apiVersion, uri, smppMessageId, error);
        }

        public Builder setSid(final Sid sid) {
            this.sid = sid;
            return this;
        }

        public Builder copyMessage(SmsMessage msg) {
            this.accountSid = msg.accountSid;
            this.sid = msg.sid;
            this.dateCreated = msg.dateCreated;
            this.dateUpdated = msg.dateUpdated;
            this.dateSent = msg.dateSent;
            this.accountSid = msg.accountSid;
            this.sender = msg.sender;
            this.recipient = msg.recipient;
            this.body = msg.body;
            this.status = msg.status;
            this.direction = msg.direction;
            this.price = msg.price;
            this.priceUnit = msg.priceUnit;
            this.apiVersion = msg.apiVersion;
            this.uri = msg.uri;
            this.smppMessageId = msg.smppMessageId;
            this.error = msg.error;
            return this;
        }

        public Builder setDateSent(final DateTime dateSent) {
            this.dateSent = dateSent;
            return this;
        }

        public Builder setAccountSid(final Sid accountSid) {
            this.accountSid = accountSid;
            return this;
        }

        public Builder setSender(final String sender) {
            this.sender = sender;
            return this;
        }

        public Builder setRecipient(final String recipient) {
            this.recipient = recipient;
            return this;
        }

        public Builder setBody(final String body) {
            this.body = body;
            return this;
        }

        public Builder setStatus(final Status status) {
            this.status = status;
            return this;
        }

        public Builder setDirection(final Direction direction) {
            this.direction = direction;
            return this;
        }

        public Builder setPrice(final BigDecimal price) {
            this.price = price;
            return this;
        }

        public Builder setPriceUnit(Currency priceUnit) {
            this.priceUnit = priceUnit;
            return this;
        }

        public Builder setApiVersion(final String apiVersion) {
            this.apiVersion = apiVersion;
            return this;
        }

        public Builder setUri(final URI uri) {
            this.uri = uri;
            return this;
        }

        public Builder setDateCreated(DateTime dateCreated) {
            this.dateCreated = dateCreated;
            return this;
        }

        public Builder setDateUpdated(DateTime dateUpdated) {
            this.dateUpdated = dateUpdated;
            return this;
        }

        public Builder setSmppMessageId(String smppMessageId) {
            this.smppMessageId = smppMessageId;
            return this;
        }

        public Builder setError(MessageError error) {
            this.error = error;
            return this;
        }
    }

    public enum Direction {
        INBOUND("inbound"), OUTBOUND_API("outbound-api"), OUTBOUND_CALL("outbound-call"), OUTBOUND_REPLY("outbound-reply");

        private final String text;

        private Direction(final String text) {
            this.text = text;
        }

        public static Direction getDirectionValue(final String text) {
            final Direction[] values = values();
            for (final Direction value : values) {
                if (value.toString().equals(text)) {
                    return value;
                }
            }
            throw new IllegalArgumentException(text + " is not a valid direction.");
        }

        @Override
        public String toString() {
            return text;
        }
    }

    public enum Status {
        QUEUED("queued"), SENDING("sending"), SENT("sent"), FAILED("failed"), RECEIVED("received"), DELIVERED("delivered"), UNDELIVERED("undelivered");

        private final String text;

        private Status(final String text) {
            this.text = text;
        }

        public static Status getStatusValue(final String text) {
            final Status[] values = values();
            for (final Status value : values) {
                if (value.toString().equals(text)) {
                    return value;
                }
            }
            throw new IllegalArgumentException(text + " is not a valid status.");
        }

        @Override
        public String toString() {
            return text;
        }
    }
}
