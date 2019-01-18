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

import org.joda.time.DateTime;
import org.restcomm.connect.commons.annotations.concurrency.Immutable;
import org.restcomm.connect.commons.dao.Sid;

import java.io.Serializable;
import java.math.BigDecimal;
import java.net.URI;
import java.util.Currency;

/**
 * @author brainslog@gmail.com (Alexandre Mendonca)
 */
@Immutable
public final class Usage implements Serializable {
  private static final long serialVersionUID = 1L;

  private final Category category;
  private final String description;
  private final Sid accountSid;
  private final DateTime startDate;
  private final DateTime endDate;
  private final Long usage;
  private final String usageUnit;
  private final Long count;
  private final String countUnit;
  private final BigDecimal price;
  private final Currency priceUnit;
  private final URI uri;

  public Usage(final Category category, final String description, final Sid accountSid, final DateTime startDate, final DateTime endDate,
               final Long usage, final String usageUnit, final Long count, final String countUnit, final BigDecimal price,
               final Currency priceUnit, final URI uri) {
    super();
    this.category = category;
    this.description = description;
    this.accountSid = accountSid;
    this.startDate = startDate;
    this.endDate = endDate;
    this.usage = usage;
    this.usageUnit = usageUnit;
    this.count = count;
    this.countUnit = countUnit;
    this.price = price;
    this.priceUnit = priceUnit;
    this.uri = uri;
  }

  public Category getCategory() {
    return category;
  }

  public String getDescription() {
    return description;
  }

  public Sid getAccountSid() {
    return accountSid;
  }

  public DateTime getStartDate() {
    return startDate;
  }

  public DateTime getEndDate() {
    return endDate;
  }

  public Long getUsage() {
    return usage;
  }

  public String getUsageUnit() {
    return usageUnit;
  }

  public Long getCount() {
    return count;
  }

  public String getCountUnit() {
    return countUnit;
  }

  public BigDecimal getPrice() {
    return price;
  }

  public Currency getPriceUnit() {
    return priceUnit;
  }

  public URI getUri() {
    return uri;
  }

  public enum Category {
    CALLS("calls"), CALLS_INBOUND("calls-inbound"), CALLS_INBOUND_LOCAL("calls-inbound-local"),
    CALLS_INBOUND_TOLLFREE("calls-inbound-tollfree"), CALLS_OUTBOUND("calls-outbound"),
    CALLS_CLIENT("calls-client"), CALLS_SIP("calls-sip"), SMS("sms"), SMS_INBOUND("sms-inbound"),
    SMS_INBOUND_SHORTCODE("sms-inbound-shortcode"), SMS_INBOUND_LONGCODE("sms-inbound-longcode"),
    SMS_OUTBOUND("sms-outbound"), SMS_OUTBOUND_SHORTCODE("sms-outbound-shortcode"),
    SMS_OUTBOUND_LONGCODE("sms-outbound-longcode"), PHONENUMBERS("phonenumbers"),
    PHONENUMBERS_TOLLFREE("phonenumbers-tollfree"), PHONENUMBERS_LOCAL("phonenumbers-local"),
    SHORTCODES("shortcodes"), SHORTCODES_VANITY("shortcodes-vanity"),
    SHORTCODES_RANDOM("shortcodes-random"), SHORTCODES_CUSTOMEROWNED("shortcodes-customerowned"),
    CALLERIDLOOKUPS("calleridlookups"), RECORDINGS("recordings"), TRANSCRIPTIONS("transcriptions"),
    RECORDINGSTORAGE("recordingstorage"), TOTALPRICE("totalprice");

    private final String text;

    Category(final String text) {
      this.text = text;
    }

    public static Category getCategoryValue(final String text) {
      final Category[] values = values();
      for (final Category value : values) {
        if (value.toString().equals(text)) {
          return value;
        }
      }
      throw new IllegalArgumentException(text + " is not a valid category.");
    }

    @Override
    public String toString() {
      return text;
    }
  }
}
