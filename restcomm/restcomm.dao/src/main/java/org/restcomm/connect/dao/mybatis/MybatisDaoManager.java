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

import org.apache.commons.configuration.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.restcomm.connect.commons.annotations.concurrency.ThreadSafe;
import org.restcomm.connect.dao.AccountsDao;
import org.restcomm.connect.dao.AnnouncementsDao;
import org.restcomm.connect.dao.ApplicationsDao;
import org.restcomm.connect.dao.AvailablePhoneNumbersDao;
import org.restcomm.connect.dao.CallDetailRecordsDao;
import org.restcomm.connect.dao.ClientsDao;
import org.restcomm.connect.dao.ConferenceDetailRecordsDao;
import org.restcomm.connect.dao.DaoManager;
import org.restcomm.connect.dao.ExtensionsConfigurationDao;
import org.restcomm.connect.dao.GatewaysDao;
import org.restcomm.connect.dao.GeolocationDao;
import org.restcomm.connect.dao.HttpCookiesDao;
import org.restcomm.connect.dao.IncomingPhoneNumbersDao;
import org.restcomm.connect.dao.InstanceIdDao;
import org.restcomm.connect.dao.MediaResourceBrokerDao;
import org.restcomm.connect.dao.MediaServersDao;
import org.restcomm.connect.dao.NotificationsDao;
import org.restcomm.connect.dao.OrganizationsDao;
import org.restcomm.connect.dao.OutgoingCallerIdsDao;
import org.restcomm.connect.dao.ProfileAssociationsDao;
import org.restcomm.connect.dao.ProfilesDao;
import org.restcomm.connect.dao.RecordingsDao;
import org.restcomm.connect.dao.RegistrationsDao;
import org.restcomm.connect.dao.ShortCodesDao;
import org.restcomm.connect.dao.SmsMessagesDao;
import org.restcomm.connect.dao.TranscriptionsDao;
import org.restcomm.connect.dao.UsageDao;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.Reader;
import java.util.Properties;

/**
 * @author quintana.thomas@gmail.com (Thomas Quintana)
 * @author maria-farooq@live.com (Maria Farooq)
 */
@ThreadSafe
public final class MybatisDaoManager implements DaoManager {
    private Configuration configuration;
    private Configuration runtimeConfiguration;
    private AccountsDao accountsDao;
    private ApplicationsDao applicationsDao;
    private AvailablePhoneNumbersDao availablePhoneNumbersDao;
    private CallDetailRecordsDao callDetailRecordsDao;
    private ConferenceDetailRecordsDao conferenceDetailRecordsDao;
    private ClientsDao clientsDao;
    private HttpCookiesDao httpCookiesDao;
    private IncomingPhoneNumbersDao incomingPhoneNumbersDao;
    private NotificationsDao notificationsDao;
    private OutgoingCallerIdsDao outgoingCallerIdsDao;
    private RegistrationsDao presenceRecordsDao;
    private RecordingsDao recordingsDao;
    private ShortCodesDao shortCodesDao;
    private SmsMessagesDao smsMessagesDao;
    private UsageDao usageDao;
    private TranscriptionsDao transcriptionsDao;
    private GatewaysDao gatewaysDao;
    private AnnouncementsDao announcementsDao;
    private InstanceIdDao instanceIdDao;
    private MediaServersDao mediaServersDao;
    private MediaResourceBrokerDao mediaResourceBrokerDao;
    private ExtensionsConfigurationDao extensionsConfigurationDao;
    private GeolocationDao geolocationDao;
    private ProfileAssociationsDao profileAssociationsDao;
    private OrganizationsDao organizationsDao;
    private ProfilesDao profilesDao;


    public MybatisDaoManager() {
        super();
    }

    @Override
    public void configure(final Configuration configuration, Configuration daoManagerConfiguration) {
        this.configuration = daoManagerConfiguration.subset("dao-manager");
        this.runtimeConfiguration = configuration.subset("runtime-settings");
    }

    @Override
    public AccountsDao getAccountsDao() {
        return accountsDao;
    }

    @Override
    public ApplicationsDao getApplicationsDao() {
        return applicationsDao;
    }

    @Override
    public AnnouncementsDao getAnnouncementsDao() {
        return announcementsDao;
    }

    @Override
    public AvailablePhoneNumbersDao getAvailablePhoneNumbersDao() {
        return availablePhoneNumbersDao;
    }

    @Override
    public CallDetailRecordsDao getCallDetailRecordsDao() {
        return callDetailRecordsDao;
    }

    @Override
    public ConferenceDetailRecordsDao getConferenceDetailRecordsDao() {
        return conferenceDetailRecordsDao;
    }

    @Override
    public ClientsDao getClientsDao() {
        return clientsDao;
    }

    @Override
    public HttpCookiesDao getHttpCookiesDao() {
        return httpCookiesDao;
    }

    @Override
    public IncomingPhoneNumbersDao getIncomingPhoneNumbersDao() {
        return incomingPhoneNumbersDao;
    }

    @Override
    public NotificationsDao getNotificationsDao() {
        return notificationsDao;
    }

    @Override
    public RegistrationsDao getRegistrationsDao() {
        return presenceRecordsDao;
    }

    @Override
    public OutgoingCallerIdsDao getOutgoingCallerIdsDao() {
        return outgoingCallerIdsDao;
    }

    @Override
    public RecordingsDao getRecordingsDao() {
        return recordingsDao;
    }

    @Override
    public ShortCodesDao getShortCodesDao() {
        return shortCodesDao;
    }

    @Override
    public SmsMessagesDao getSmsMessagesDao() {
        return smsMessagesDao;
    }

    @Override
    public UsageDao getUsageDao() {
        return usageDao;
    }

    @Override
    public TranscriptionsDao getTranscriptionsDao() {
        return transcriptionsDao;
    }

    @Override
    public GatewaysDao getGatewaysDao() {
        return gatewaysDao;
    }

    @Override
    public InstanceIdDao getInstanceIdDao() {
        return instanceIdDao;
    }

    @Override
    public MediaServersDao getMediaServersDao() {
        return mediaServersDao;
    }

    @Override
    public MediaResourceBrokerDao getMediaResourceBrokerDao() {
        return mediaResourceBrokerDao;
    }

    @Override
    public ExtensionsConfigurationDao getExtensionsConfigurationDao() {
        return extensionsConfigurationDao;
    }

    @Override
    public GeolocationDao getGeolocationDao() {
        return geolocationDao;
    }

    @Override
    public ProfileAssociationsDao getProfileAssociationsDao() {
        return profileAssociationsDao;
    }

    @Override
    public OrganizationsDao getOrganizationsDao() {
        return organizationsDao;
    }

    @Override
    public ProfilesDao getProfilesDao() {
        return profilesDao;
    }

    @Override
    public void shutdown() {
        // Nothing to do.
    }

    @Override
    public void start() throws RuntimeException {
        // This must be called before any other MyBatis methods.
        org.apache.ibatis.logging.LogFactory.useSlf4jLogging();
        // Load the configuration file.
        final SqlSessionFactoryBuilder builder = new SqlSessionFactoryBuilder();
        final String path = configuration.getString("configuration-file");
        Reader reader = null;
        try {
            reader = new FileReader(path);
        } catch (final FileNotFoundException exception) {
            throw new RuntimeException(exception);
        }
        final Properties properties = new Properties();
        final String dataFiles = configuration.getString("data-files");
        final String sqlFiles = configuration.getString("sql-files");
        properties.setProperty("data", dataFiles);
        properties.setProperty("sql", sqlFiles);
        final SqlSessionFactory sessions = builder.build(reader, properties);
        start(sessions);
    }

    public void start(final SqlSessionFactory sessions) {
        // Instantiate the DAO objects.
        accountsDao = new MybatisAccountsDao(sessions);
        applicationsDao = new MybatisApplicationsDao(sessions);
        announcementsDao = new MybatisAnnouncementsDao(sessions);
        availablePhoneNumbersDao = new MybatisAvailablePhoneNumbersDao(sessions);
        callDetailRecordsDao = new MybatisCallDetailRecordsDao(sessions);
        conferenceDetailRecordsDao = new MybatisConferenceDetailRecordsDao(sessions);
        clientsDao = new MybatisClientsDao(sessions);
        httpCookiesDao = new MybatisHttpCookiesDao(sessions);
        incomingPhoneNumbersDao = new MybatisIncomingPhoneNumbersDao(sessions);
        notificationsDao = new MybatisNotificationsDao(sessions);
        outgoingCallerIdsDao = new MybatisOutgoingCallerIdsDao(sessions);
        presenceRecordsDao = new MybatisRegistrationsDao(sessions);
        recordingsDao = new MybatisRecordingsDao(sessions);
        shortCodesDao = new MybatisShortCodesDao(sessions);
        smsMessagesDao = new MybatisSmsMessagesDao(sessions);
        usageDao = new MybatisUsageDao(sessions);
        transcriptionsDao = new MybatisTranscriptionsDao(sessions);
        gatewaysDao = new MybatisGatewaysDao(sessions);
        instanceIdDao = new MybatisInstanceIdDao(sessions);
        mediaServersDao = new MybatisMediaServerDao(sessions);
        mediaResourceBrokerDao = new MybatisMediaResourceBrokerDao(sessions);
        extensionsConfigurationDao = new MybatisExtensionsConfigurationDao(sessions);
        geolocationDao = new MybatisGeolocationDao(sessions);
        profileAssociationsDao = new MybatisProfileAssociationsDao(sessions);
        organizationsDao = new MybatisOrganizationDao(sessions);
        profilesDao = new MybatisProfilesDao(sessions);
    }
}
