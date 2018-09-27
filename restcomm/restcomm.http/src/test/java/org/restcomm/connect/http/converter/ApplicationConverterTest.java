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

package org.restcomm.connect.http.converter;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.thoughtworks.xstream.XStream;
import junit.framework.Assert;
import org.joda.time.DateTime;
import org.junit.Test;
import org.restcomm.connect.commons.dao.Sid;
import org.restcomm.connect.dao.entities.Application;
import org.restcomm.connect.dao.entities.ApplicationNumberSummary;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author otsakir@gmail.com - Orestis Tsakiridis
 */
public class ApplicationConverterTest {

    @Test
    public void testNestedNumbersProperty() throws URISyntaxException {
        // Initialize Json and XML converters
        final ApplicationConverter applicationConverter = new ApplicationConverter(null);
        final GsonBuilder builder = new GsonBuilder();
        builder.registerTypeAdapter(Application.class, applicationConverter);
        // convert camelCaseFieldNames to camel_case_field_names convention. This will only affect ApplicationNumberSummary
        builder.setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES);
        builder.setPrettyPrinting();
        Gson gson = builder.create();
        ApplicationNumberSummaryConverter numberConverter = new ApplicationNumberSummaryConverter();
        XStream xstream = new XStream();
        //xstream.alias("RestcommResponse", RestCommResponse.class);
        xstream.registerConverter(applicationConverter);
        xstream.registerConverter(numberConverter);
        xstream.registerConverter(new ApplicationListConverter(null));
        xstream.alias("Number",ApplicationNumberSummary.class);
        //xstream.alias("Numbers", );
        //xstream.registerConverter(new RestCommResponseConverter(configuration));

        Application app = new Application( new Sid("AP73926e7113fa4d95981aa96b76eca854"), new DateTime(), new DateTime(), "test app", new Sid("ACae6e420f425248d6a26948c17a9e2acf"), "2012-04-24", false, new URI("/2012-04-24/Accounts/ACae6e420f425248d6a26948c17a9e2acf/Applications/AP73926e7113fa4d95981aa96b76eca854"),new URI("/visual-designer/services/apps/AP73926e7113fa4d95981aa96b76eca854/controller"), Application.Kind.VOICE);
        List<ApplicationNumberSummary> numbers = new ArrayList<ApplicationNumberSummary>();
        ApplicationNumberSummary numberSummary = new ApplicationNumberSummary(
          "PN00000000000000000000000000000001",
                "1234",
                "+1234",
                "AP73926e7113fa4d95981aa96b76eca854", null,null,null
        );
        numbers.add(numberSummary);
        numberSummary = new ApplicationNumberSummary(
                "PN00000000000000000000000000000002",
                "1234",
                "+1234",
                "AP73926e7113fa4d95981aa96b76eca854", null,null,null
        );
        numbers.add(numberSummary);
        app.setNumbers(numbers);

        List<Application> apps = new ArrayList<Application>();
        apps.add(app);
        // test json results
        String responseJson = gson.toJson(apps);
        Assert.assertTrue(responseJson.contains("\"numbers\": ["));
        Assert.assertTrue(responseJson.contains("\"phone_number\": \"+1234\""));
        // test xml results
        String responseXML = xstream.toXML(app);
        responseXML = responseXML.replaceAll("[ \n]","");
        Assert.assertTrue(responseXML.contains("<Numbers><Number>"));
        Assert.assertTrue(responseXML.contains("<PhoneNumber>+1234</PhoneNumber>"));
    }
}
