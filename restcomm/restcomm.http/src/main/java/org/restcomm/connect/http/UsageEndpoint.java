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
package org.restcomm.connect.http;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.sun.jersey.spi.resource.Singleton;
import com.thoughtworks.xstream.XStream;
import java.util.List;
import java.util.regex.Pattern;
import javax.annotation.PostConstruct;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import static javax.ws.rs.core.MediaType.*;
import javax.ws.rs.core.Response;
import static javax.ws.rs.core.Response.Status.NOT_FOUND;
import static javax.ws.rs.core.Response.ok;
import static javax.ws.rs.core.Response.status;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.core.UriInfo;
import org.apache.commons.configuration.Configuration;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.restcomm.connect.commons.annotations.concurrency.ThreadSafe;
import org.restcomm.connect.commons.dao.Sid;
import org.restcomm.connect.dao.DaoManager;
import org.restcomm.connect.dao.UsageDao;
import org.restcomm.connect.dao.entities.RestCommResponse;
import org.restcomm.connect.dao.entities.Usage;
import org.restcomm.connect.dao.entities.UsageList;
import org.restcomm.connect.http.converter.RestCommResponseConverter;
import org.restcomm.connect.http.converter.UsageConverter;
import org.restcomm.connect.http.converter.UsageListConverter;
import org.restcomm.connect.http.security.ContextUtil;
import org.restcomm.connect.identity.UserIdentityContext;

/**
 * @author charles.roufay@telestax.com (Charles Roufay)
 * @author brainslog@gmail.com (Alexandre Mendonca)
 */
@Path("/Accounts/{accountSid}/Usage/Records")
@ThreadSafe
@Singleton
public class UsageEndpoint extends AbstractEndpoint {
  @Context
  protected ServletContext context;
  protected Configuration configuration;
  protected UsageDao dao;
  protected Gson gson;
  protected XStream xstream;


    @Context
    private HttpServletRequest servletRequest;




  public UsageEndpoint() {
    super();
  }

  public static final Pattern relativePattern = Pattern.compile("(\\+|\\-)(\\d+)day[s]");

  @PostConstruct
  public void init() {
    final DaoManager storage = (DaoManager) context.getAttribute(DaoManager.class.getName());
    configuration = (Configuration) context.getAttribute(Configuration.class.getName());
    configuration = configuration.subset("runtime-settings");
    super.init(configuration);
    dao = storage.getUsageDao();
    final UsageConverter converter = new UsageConverter(configuration);
    final GsonBuilder builder = new GsonBuilder();
    builder.registerTypeAdapter(Usage.class, converter);
    builder.setPrettyPrinting();
    gson = builder.disableHtmlEscaping().create();
    xstream = new XStream();
    xstream.alias("RestcommResponse", RestCommResponse.class);
    xstream.registerConverter(converter);
    xstream.registerConverter(new UsageListConverter(configuration));
    xstream.registerConverter(new RestCommResponseConverter(configuration));
  }

  protected Response getUsage(final String accountSid,
          final String subresource,
          UriInfo info,
          final MediaType responseType,
          UserIdentityContext userIdentityContext) {
    permissionEvaluator.secure(accountsDao.getAccount(accountSid),
            "RestComm:Read:Usage",
            userIdentityContext);

    String categoryStr = info.getQueryParameters().getFirst("Category");
    String startDateStr = info.getQueryParameters().getFirst("StartDate");
    String endDateStr = info.getQueryParameters().getFirst("EndDate");
    //pass in reqUri without query params
    String reqUri = servletRequest.getServletPath() + "/" + info.getPath(false);

    Usage.Category category = categoryStr != null ? Usage.Category.valueOf(categoryStr) : null;
    DateTime startDate = new DateTime(0).withTimeAtStartOfDay();
    if (startDateStr != null) {
      try {
        startDate = DateTimeFormat.forPattern("yyyyy-MM-dd").parseDateTime(startDateStr);
      }
      catch (IllegalArgumentException iae) {
        // TODO: Support relative
      }
    }
    DateTime endDate = new DateTime();
    if (endDateStr != null) {
      try {
        endDate = DateTimeFormat.forPattern("yyyyy-MM-dd").parseDateTime(endDateStr);
      }
      catch (IllegalArgumentException iae) {
        // TODO: Support relative
      }
    }

    final List<Usage> usage;
    if (subresource.toLowerCase().equals("daily")) {
      usage = dao.getUsageDaily(new Sid(accountSid), category, startDate, endDate, reqUri);
    }
    else if (subresource.toLowerCase().equals("monthly")) {
      usage = dao.getUsageMonthly(new Sid(accountSid), category, startDate, endDate, reqUri);
    }
    else if (subresource.toLowerCase().equals("yearly")) {
      usage = dao.getUsageYearly(new Sid(accountSid), category, startDate, endDate, reqUri);
    }
    else if (subresource.toLowerCase().equals("alltime")) {
      usage = dao.getUsageAllTime(new Sid(accountSid), category, startDate, endDate, reqUri);
    } else if (subresource.toLowerCase().equals("today")) {
      usage = dao.getUsageAllTime(new Sid(accountSid), category, DateTime.now(), DateTime.now(), reqUri);
    }
    else if (subresource.toLowerCase().equals("yesterday")) {
      usage = dao.getUsageAllTime(new Sid(accountSid), category, DateTime.now().minusDays(1), DateTime.now().minusDays(1), reqUri);
    }
    else if (subresource.toLowerCase().equals("thismonth")) {
      usage = dao.getUsageAllTime(new Sid(accountSid), category, DateTime.now().dayOfMonth().withMinimumValue(), DateTime.now().dayOfMonth().withMaximumValue(), reqUri);
    }
    else if (subresource.toLowerCase().equals("lastmonth")) {
      usage = dao.getUsageAllTime(new Sid(accountSid), category, DateTime.now().minusMonths(1).dayOfMonth().withMinimumValue(), DateTime.now().minusMonths(1).dayOfMonth().withMaximumValue(), reqUri);
    }
    else {
      usage = dao.getUsageAllTime(new Sid(accountSid), category, startDate, endDate, reqUri);
    }

    if (usage == null) {
      return status(NOT_FOUND).build();
    } else {
      if (APPLICATION_JSON_TYPE.equals(responseType)) {
        return ok(gson.toJson(usage), APPLICATION_JSON).build();
      } else if (APPLICATION_XML_TYPE.equals(responseType)) {
        final RestCommResponse response = new RestCommResponse(new UsageList(usage));
        return ok(xstream.toXML(response), APPLICATION_XML).build();
      } else {
        return null;
      }
    }
  }

    @Path("/{subresource}")
    @GET
    @Produces({MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public Response getUsageAsXml(@PathParam("accountSid") final String accountSid,
            @PathParam("subresource") final String subresource,
            @Context UriInfo info,
            @HeaderParam("Accept") String accept,
            @Context SecurityContext sec) {
      return getUsage(accountSid,
              subresource,
              info, retrieveMediaType(accept),
              ContextUtil.convert(sec));
    }
}
