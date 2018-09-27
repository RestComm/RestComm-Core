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
package org.restcomm.connect.dao.mybatis;

import static org.junit.Assert.*;

import java.io.InputStream;
import java.net.URI;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.joda.time.DateTime;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.restcomm.connect.dao.NotificationsDao;
import org.restcomm.connect.dao.common.Sorting;
import org.restcomm.connect.dao.entities.Notification;
import org.restcomm.connect.commons.configuration.RestcommConfiguration;
import org.restcomm.connect.commons.dao.Sid;
import org.restcomm.connect.dao.entities.NotificationFilter;

/**
 * @author quintana.thomas@gmail.com (Thomas Quintana)
 */
public final class NotificationsDaoTest {
    private static MybatisDaoManager manager;

    protected static Sid instanceId = Sid.generate(Sid.Type.INSTANCE);

    public NotificationsDaoTest() {
        super();
    }

    @Before
    public void before() {
        final InputStream data = getClass().getResourceAsStream("/mybatis.xml");
        final SqlSessionFactoryBuilder builder = new SqlSessionFactoryBuilder();
        final SqlSessionFactory factory = builder.build(data);
        manager = new MybatisDaoManager();
        manager.start(factory);

        XMLConfiguration xmlConfiguration = new XMLConfiguration();
        xmlConfiguration.setDelimiterParsingDisabled(true);
        xmlConfiguration.setAttributeSplittingDisabled(true);
        try {
			xmlConfiguration.load("restcomm.xml");
	        RestcommConfiguration.createOnce(xmlConfiguration);
	        RestcommConfiguration.getInstance().getMain().setInstanceId(instanceId.toString());
		} catch (ConfigurationException e) {
			e.printStackTrace();
		}
    }

    @After
    public void after() {
        manager.shutdown();
    }

    @Test
    public void createReadDelete() {
        final Sid sid = Sid.generate(Sid.Type.NOTIFICATION);
        final Sid account = Sid.generate(Sid.Type.ACCOUNT);
        final Sid call = Sid.generate(Sid.Type.CALL);
        final URI url = URI.create("http://127.0.0.1:8080/restcomm/demos/hello-world.xml");
        final String method = "GET";
        final Notification.Builder builder = Notification.builder();
        builder.setSid(sid);
        builder.setAccountSid(account);
        builder.setCallSid(call);
        builder.setApiVersion("2012-04-24");
        builder.setLog(0);
        builder.setErrorCode(11100);
        builder.setMoreInfo(url);
        builder.setMessageText("hello world!");
        builder.setMessageDate(DateTime.now());
        builder.setRequestUrl(url);
        builder.setRequestMethod(method);
        builder.setRequestVariables("hello world!");
        builder.setResponseHeaders("hello world!");
        builder.setResponseBody("hello world!");
        builder.setUri(url);
        Notification notification = builder.build();
        final NotificationsDao notifications = manager.getNotificationsDao();
        // Create a new notification in the data store.
        notifications.addNotification(notification);
        // Read the notification from the data store.
        Notification result = notifications.getNotification(sid);
        // Verify the results.
        assertTrue(result.getSid().equals(notification.getSid()));
        assertTrue(result.getAccountSid().equals(notification.getAccountSid()));
        assertTrue(result.getCallSid().equals(notification.getCallSid()));
        assertTrue(result.getApiVersion().equals(notification.getApiVersion()));
        assertTrue(result.getLog() == notification.getLog());
        assertTrue(result.getErrorCode().equals(notification.getErrorCode()));
        assertTrue(result.getMoreInfo().equals(notification.getMoreInfo()));
        assertTrue(result.getMessageText().equals(notification.getMessageText()));
        assertTrue(result.getMessageDate().equals(notification.getMessageDate()));
        assertTrue(result.getRequestUrl().equals(notification.getRequestUrl()));
        assertTrue(result.getRequestMethod().equals(notification.getRequestMethod()));
        assertTrue(result.getRequestVariables().equals(notification.getRequestVariables()));
        assertTrue(result.getResponseHeaders().equals(notification.getResponseHeaders()));
        assertTrue(result.getResponseBody().equals(notification.getResponseBody()));
        assertTrue(result.getUri().equals(notification.getUri()));
        // Delete the notification.
        notifications.removeNotification(sid);
        // Validate that the notification was removed.
        assertTrue(notifications.getNotification(sid) == null);
    }

    @Test
    public void testByAccount() {
        final Sid sid = Sid.generate(Sid.Type.NOTIFICATION);
        final Sid account = Sid.generate(Sid.Type.ACCOUNT);
        final Sid call = Sid.generate(Sid.Type.CALL);
        final URI url = URI.create("http://127.0.0.1:8080/restcomm/demos/hello-world.xml");
        final String method = "GET";
        final Notification.Builder builder = Notification.builder();
        builder.setSid(sid);
        builder.setAccountSid(account);
        builder.setCallSid(call);
        builder.setApiVersion("2012-04-24");
        builder.setLog(0);
        builder.setErrorCode(11100);
        builder.setMoreInfo(url);
        builder.setMessageText("hello world!");
        builder.setMessageDate(DateTime.now());
        builder.setRequestUrl(url);
        builder.setRequestMethod(method);
        builder.setRequestVariables("hello world!");
        builder.setResponseHeaders("hello world!");
        builder.setResponseBody("hello world!");
        builder.setUri(url);
        Notification notification = builder.build();
        final NotificationsDao notifications = manager.getNotificationsDao();
        // Create a new notification in the data store.
        notifications.addNotification(notification);
        // Read the notification from the data store.
        assertTrue(notifications.getNotifications(account).size() == 1);
        // Delete the notification.
        notifications.removeNotifications(account);
        // Validate that the notification was removed.
        assertTrue(notifications.getNotifications(account).size() == 0);
    }

    @Test
    public void testByCall() {
        final Sid sid = Sid.generate(Sid.Type.NOTIFICATION);
        final Sid account = Sid.generate(Sid.Type.ACCOUNT);
        final Sid call = Sid.generate(Sid.Type.CALL);
        final URI url = URI.create("http://127.0.0.1:8080/restcomm/demos/hello-world.xml");
        final String method = "GET";
        final Notification.Builder builder = Notification.builder();
        builder.setSid(sid);
        builder.setAccountSid(account);
        builder.setCallSid(call);
        builder.setApiVersion("2012-04-24");
        builder.setLog(0);
        builder.setErrorCode(11100);
        builder.setMoreInfo(url);
        builder.setMessageText("hello world!");
        builder.setMessageDate(DateTime.now());
        builder.setRequestUrl(url);
        builder.setRequestMethod(method);
        builder.setRequestVariables("hello world!");
        builder.setResponseHeaders("hello world!");
        builder.setResponseBody("hello world!");
        builder.setUri(url);
        Notification notification = builder.build();
        final NotificationsDao notifications = manager.getNotificationsDao();
        // Create a new notification in the data store.
        notifications.addNotification(notification);
        // Read the notification from the data store.
        assertTrue(notifications.getNotificationsByCall(call).size() == 1);
        // Delete the notification.
        notifications.removeNotificationsByCall(call);
        // Validate that the notification was removed.
        assertTrue(notifications.getNotificationsByCall(call).size() == 0);
    }

    @Test
    public void testByLogLevel() {
        final Sid sid = Sid.generate(Sid.Type.NOTIFICATION);
        final Sid account = Sid.generate(Sid.Type.ACCOUNT);
        final Sid call = Sid.generate(Sid.Type.CALL);
        final URI url = URI.create("http://127.0.0.1:8080/restcomm/demos/hello-world.xml");
        final String method = "GET";
        final Notification.Builder builder = Notification.builder();
        builder.setSid(sid);
        builder.setAccountSid(account);
        builder.setCallSid(call);
        builder.setApiVersion("2012-04-24");
        builder.setLog(0);
        builder.setErrorCode(11100);
        builder.setMoreInfo(url);
        builder.setMessageText("hello world!");
        builder.setMessageDate(DateTime.now());
        builder.setRequestUrl(url);
        builder.setRequestMethod(method);
        builder.setRequestVariables("hello world!");
        builder.setResponseHeaders("hello world!");
        builder.setResponseBody("hello world!");
        builder.setUri(url);
        Notification notification = builder.build();
        final NotificationsDao notifications = manager.getNotificationsDao();
        // Create a new notification in the data store.
        notifications.addNotification(notification);
        // Read the notification from the data store (remember that there are already notifications in the test DB, so we expect 4 existing already with log level 0 plus this one)
        assertTrue(notifications.getNotificationsByLogLevel(0).size() == 5);
        // Delete the notification.
        notifications.removeNotification(sid);
        // Validate that the notification was removed.
        assertTrue(notifications.getNotification(sid) == null);
    }

    @Test
    public void testByMessageDate() {
        final Sid sid = Sid.generate(Sid.Type.NOTIFICATION);
        final Sid account = Sid.generate(Sid.Type.ACCOUNT);
        final Sid call = Sid.generate(Sid.Type.CALL);
        final URI url = URI.create("http://127.0.0.1:8080/restcomm/demos/hello-world.xml");
        final String method = "GET";
        final Notification.Builder builder = Notification.builder();
        builder.setSid(sid);
        builder.setAccountSid(account);
        builder.setCallSid(call);
        builder.setApiVersion("2012-04-24");
        builder.setLog(0);
        builder.setErrorCode(11100);
        builder.setMoreInfo(url);
        builder.setMessageText("hello world!");
        final DateTime now = DateTime.now();
        builder.setMessageDate(now);
        builder.setRequestUrl(url);
        builder.setRequestMethod(method);
        builder.setRequestVariables("hello world!");
        builder.setResponseHeaders("hello world!");
        builder.setResponseBody("hello world!");
        builder.setUri(url);
        Notification notification = builder.build();
        final NotificationsDao notifications = manager.getNotificationsDao();
        // Create a new notification in the data store.
        notifications.addNotification(notification);
        // Read the notification from the data store.
        assertTrue(notifications.getNotificationsByMessageDate(now).size() == 1);
        // Delete the notification.
        notifications.removeNotification(sid);
        // Validate that the notification was removed.
        assertTrue(notifications.getNotification(sid) == null);
    }

    @Test
    public void filterWithDateSorting() throws ParseException {
        NotificationsDao dao = manager.getNotificationsDao();
        NotificationFilter.Builder builder = new NotificationFilter.Builder();
        List<String> accountSidSet = new ArrayList<String>();
        accountSidSet.add("ACae6e420f425248d6a26948c17a9e2acf");
        builder.byAccountSidSet(accountSidSet);
        builder.sortedByDate(Sorting.Direction.ASC);
        NotificationFilter filter = builder.build();
        List<Notification> notifications = dao.getNotifications(filter);
        assertEquals(10, notifications.size());
        final DateTime min = DateTime.parse("2013-08-30T16:28:33.403");
        final DateTime max = DateTime.parse("2013-09-02T16:28:33.403");
        assertEquals(0, min.compareTo(notifications.get(0).getDateCreated()));
        assertEquals(0, max.compareTo(notifications.get(notifications.size() - 1).getDateCreated()));

        builder.sortedByDate(Sorting.Direction.DESC);
        filter = builder.build();
        notifications = dao.getNotifications(filter);
        assertEquals(0, max.compareTo(notifications.get(0).getDateCreated()));
        assertEquals(0, min.compareTo(notifications.get(notifications.size() - 1).getDateCreated()));
    }

    @Test
    public void filterWithLogSorting() throws ParseException {
        NotificationsDao dao = manager.getNotificationsDao();
        NotificationFilter.Builder builder = new NotificationFilter.Builder();
        List<String> accountSidSet = new ArrayList<String>();
        accountSidSet.add("ACae6e420f425248d6a26948c17a9e2acf");
        builder.byAccountSidSet(accountSidSet);
        builder.sortedByLog(Sorting.Direction.ASC);
        NotificationFilter filter = builder.build();
        List<Notification> notifications = dao.getNotifications(filter);
        assertEquals(10, notifications.size());
        assertEquals("0", notifications.get(0).getLog().toString());
        assertEquals("1", notifications.get(notifications.size() - 1).getLog().toString());

        builder.sortedByLog(Sorting.Direction.DESC);
        filter = builder.build();
        notifications = dao.getNotifications(filter);
        assertEquals("1", notifications.get(0).getLog().toString());
        assertEquals("0", notifications.get(notifications.size() - 1).getLog().toString());
    }

    @Test
    public void filterWithErrorCodeSorting() throws ParseException {
        NotificationsDao dao = manager.getNotificationsDao();
        NotificationFilter.Builder builder = new NotificationFilter.Builder();
        List<String> accountSidSet = new ArrayList<String>();
        accountSidSet.add("ACae6e420f425248d6a26948c17a9e2acf");
        builder.byAccountSidSet(accountSidSet);
        builder.sortedByErrorCode(Sorting.Direction.ASC);
        NotificationFilter filter = builder.build();
        List<Notification> notifications = dao.getNotifications(filter);
        assertEquals(10, notifications.size());
        assertEquals("0", notifications.get(0).getErrorCode().toString());
        assertEquals("100", notifications.get(notifications.size() - 1).getErrorCode().toString());

        builder.sortedByErrorCode(Sorting.Direction.DESC);
        filter = builder.build();
        notifications = dao.getNotifications(filter);
        assertEquals("100", notifications.get(0).getErrorCode().toString());
        assertEquals("0", notifications.get(notifications.size() - 1).getErrorCode().toString());
    }

    @Test
    public void filterWithCallSidSorting() throws ParseException {
        NotificationsDao dao = manager.getNotificationsDao();
        NotificationFilter.Builder builder = new NotificationFilter.Builder();
        List<String> accountSidSet = new ArrayList<String>();
        accountSidSet.add("ACae6e420f425248d6a26948c17a9e2acf");
        builder.byAccountSidSet(accountSidSet);
        builder.sortedByCallSid(Sorting.Direction.ASC);
        NotificationFilter filter = builder.build();
        List<Notification> notifications = dao.getNotifications(filter);
        assertEquals(10, notifications.size());
        assertEquals("CA5EB00000000000000000000000000002", notifications.get(0).getCallSid().toString());
        assertEquals("CA5EB00000000000000000000000000009", notifications.get(notifications.size() - 1).getCallSid().toString());

        builder.sortedByCallSid(Sorting.Direction.DESC);
        filter = builder.build();
        notifications = dao.getNotifications(filter);
        assertEquals("CA5EB00000000000000000000000000009", notifications.get(0).getCallSid().toString());
        assertEquals("CA5EB00000000000000000000000000002", notifications.get(notifications.size() - 1).getCallSid().toString());
    }

    @Test
    public void filterWithMessageTextSorting() throws ParseException {
        NotificationsDao dao = manager.getNotificationsDao();
        NotificationFilter.Builder builder = new NotificationFilter.Builder();
        List<String> accountSidSet = new ArrayList<String>();
        accountSidSet.add("ACae6e420f425248d6a26948c17a9e2acf");
        builder.byAccountSidSet(accountSidSet);
        builder.sortedByMessageText(Sorting.Direction.ASC);
        NotificationFilter filter = builder.build();
        List<Notification> notifications = dao.getNotifications(filter);
        assertEquals(10, notifications.size());
        assertEquals("Another fictitious message for testing", notifications.get(0).getMessageText());
        assertEquals("Workspace migration skipped in 2016-12-28 21:12:25.758", notifications.get(notifications.size() - 1).getMessageText());

        builder.sortedByMessageText(Sorting.Direction.DESC);
        filter = builder.build();
        notifications = dao.getNotifications(filter);
        assertEquals("Workspace migration skipped in 2016-12-28 21:12:25.758", notifications.get(0).getMessageText());
        assertEquals("Another fictitious message for testing", notifications.get(notifications.size() - 1).getMessageText());
    }
}
