/*
 * TeleStax, Open Source Cloud Communications
 * Copyright 2011-2015, Telestax Inc and individual contributors
 * by the @authors tag.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.restcomm.connect.application;

import akka.actor.Actor;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.actor.UntypedActorFactory;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.thoughtworks.xstream.XStream;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.io.FileUtils;
import org.apache.ibatis.exceptions.TooManyResultsException;
import org.joda.time.DateTime;
import org.restcomm.connect.commons.dao.Sid;
import org.restcomm.connect.commons.util.StringUtils;
import org.restcomm.connect.dao.AccountsDao;
import org.restcomm.connect.dao.ApplicationsDao;
import org.restcomm.connect.dao.ClientsDao;
import org.restcomm.connect.dao.DaoManager;
import org.restcomm.connect.dao.IncomingPhoneNumbersDao;
import org.restcomm.connect.dao.NotificationsDao;
import org.restcomm.connect.dao.entities.Account;
import org.restcomm.connect.dao.entities.Application;
import org.restcomm.connect.dao.entities.Client;
import org.restcomm.connect.dao.entities.IncomingPhoneNumber;
import org.restcomm.connect.dao.entities.Notification;
import org.restcomm.connect.email.EmailService;
import org.restcomm.connect.email.api.EmailRequest;
import org.restcomm.connect.email.api.Mail;

import javax.servlet.ServletContext;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.restcomm.connect.dao.entities.IncomingPhoneNumberFilter;

/**
 * This class was designed to be used with exclusivity by {@link RvdProjectsMigrator}, once that
 * Restcomm should not interact with RVD's workspace as a typical operation given that the migration process forms a very
 * specific scenario.
 *
 * @author guilherme.jansen@telestax.com
 */
public class RvdProjectsMigrationHelper {

    private static final String CONTEXT_NAME_RVD = "visual-designer.war";
    private static final String WORKSPACE_DIRECTORY_NAME = "workspace";
    private static final String PROTO_DIRECTORY_PREFIX = "_proto";
    private static final String USERS_DIRECTORY_NAME = "@users";
    private static final Pattern RVD_PROJECT_URL = Pattern.compile("^\\/visual-designer.*\\/(.*)\\/controller$");
    private static final String ACCOUNT_NOTIFICATIONS_SID = "ACae6e420f425248d6a26948c17a9e2acf";
    private static final String EMBEDDED_DIRECTORY_NAME = "workspace-migration";

    private boolean embeddedMigration = false; // If visual-designer context is not found, search for internal structure

    private Configuration configuration;
    private String workspacePath;
    private String workspaceBackupPath;
    private StateHeader currentStateHeader;
    private Application currentApplication;
    private final ApplicationsDao applicationDao;
    private final AccountsDao accountsDao;
    private final IncomingPhoneNumbersDao didsDao;
    private final ClientsDao clientsDao;
    private final NotificationsDao notificationsDao;
    private List<IncomingPhoneNumber> dids;
    private List<Client> clients;
    private ActorRef emailService;
    private ActorSystem system;

    public RvdProjectsMigrationHelper(ServletContext servletContext, Configuration configuration) throws Exception {
        defineWorkspacePath(servletContext);
        this.configuration = configuration;
        final DaoManager storage = (DaoManager) servletContext.getAttribute(DaoManager.class.getName());
        this.applicationDao = storage.getApplicationsDao();
        this.accountsDao = storage.getAccountsDao();
        this.didsDao = storage.getIncomingPhoneNumbersDao();
        this.clientsDao = storage.getClientsDao();
        this.notificationsDao = storage.getNotificationsDao();
        system = (ActorSystem) servletContext.getAttribute(ActorSystem.class.getName());
    }

    private void defineWorkspacePath(ServletContext servletContext) throws Exception {
        // Obtain RVD context root path
        String contextRootPath = servletContext.getRealPath("/");
        String contextPathRvd = contextRootPath + "../" + CONTEXT_NAME_RVD + "/";

        // Check RVD context to try embedded mode if necessary
        File rvd = new File(contextPathRvd);
        if (rvd.exists()) {
            // Load RVD configuration and check workspace path
            FileInputStream input = new FileInputStream(contextPathRvd + "WEB-INF/rvd.xml");
            XStream xstream = new XStream();
            xstream.alias("rvd", RvdConfig.class);
            RvdConfig rvdConfig = (RvdConfig) xstream.fromXML(input);
            // Define workspace location
            String workspaceBasePath = contextPathRvd + WORKSPACE_DIRECTORY_NAME;
            if (rvdConfig.getWorkspaceLocation() != null && !"".equals(rvdConfig.getWorkspaceLocation())) {
                if (rvdConfig.getWorkspaceLocation().startsWith("/"))
                    workspaceBasePath = rvdConfig.getWorkspaceLocation(); // this is an absolute path
                else
                    workspaceBasePath = contextPathRvd + rvdConfig.getWorkspaceLocation(); // this is a relative path hooked
                                                                                           // under RVD context
            }
            this.workspacePath = workspaceBasePath;
            // Define workspace backup location
            String workspaceBackupBasePath = contextPathRvd;
            if (rvdConfig.getWorkspaceBackupLocation() != null && !"".equals(rvdConfig.getWorkspaceBackupLocation())) {
                if (rvdConfig.getWorkspaceBackupLocation().startsWith("/"))
                    workspaceBackupBasePath = rvdConfig.getWorkspaceBackupLocation(); // this is an absolute path
                else
                    workspaceBackupBasePath = contextPathRvd + rvdConfig.getWorkspaceBackupLocation(); // this is a relative
                                                                                                       // path hooked under RVD
                                                                                                       // context
            }
            this.workspaceBackupPath = workspaceBackupBasePath;
        } else {
            // Try to set embedded migration. For testing only
            String dir = contextRootPath + EMBEDDED_DIRECTORY_NAME + File.separator + WORKSPACE_DIRECTORY_NAME + File.separator;
            File embedded = new File(dir);
            if (embedded.exists()) {
                this.workspacePath = dir;
                this.workspaceBackupPath = contextRootPath;
                this.embeddedMigration = true;
            } else {
                throw new Exception("Error while searching for the workspace location. Aborting migration.");
            }
        }

    }

    public void backupWorkspace() throws RvdProjectsMigrationException {
        try {
            File workspace = new File(this.workspacePath);
            File workspaceBackup = new File(this.workspaceBackupPath + File.separator + "workspaceBackup-"
                    + DateTime.now().getMillis());
            FileUtils.copyDirectoryToDirectory(workspace, workspaceBackup);
        } catch (IOException e) {
            throw new RvdProjectsMigrationException("[ERROR-CODE:13] Error while creating backup for RVD workspace", 13);
        }
    }

    public List<String> listProjects() throws RvdProjectsMigrationException {
        List<String> items = new ArrayList<String>();
        File workspaceDir = new File(workspacePath);
        if (workspaceDir.exists()) {
            File[] entries = workspaceDir.listFiles(new FileFilter() {
                @Override
                public boolean accept(File anyfile) {
                    if (anyfile.isDirectory() && !anyfile.getName().startsWith(PROTO_DIRECTORY_PREFIX)
                            && !anyfile.getName().equals(USERS_DIRECTORY_NAME))
                        return true;
                    return false;
                }
            });
            Arrays.sort(entries, new Comparator<File>() {
                @Override
                public int compare(File f1, File f2) {
                    File statefile1 = new File(f1.getAbsolutePath() + File.separator + "state");
                    File statefile2 = new File(f2.getAbsolutePath() + File.separator + "state");
                    if (statefile1.exists() && statefile2.exists())
                        return Long.valueOf(statefile2.lastModified()).compareTo(statefile1.lastModified());
                    else
                        return Long.valueOf(f2.lastModified()).compareTo(f1.lastModified());
                }
            });
            for (File entry : entries) {
                items.add(entry.getName());
            }
        } else {
            throw new RvdProjectsMigrationException("[ERROR-CODE:1] Error while loading the list of projects from workspace", 1);
        }
        return items;
    }

    public String searchApplicationSid(String projectName) throws RvdProjectsMigrationException {
        try {
            currentApplication = null;
            String applicationSid = null;
            currentApplication = applicationDao.getApplication(projectName);
            if (currentApplication != null) {
                applicationSid = currentApplication.getSid().toString();
            } else if (Sid.pattern.matcher(projectName).matches()) {
                Sid sid = new Sid(projectName);
                currentApplication = applicationDao.getApplication(sid);
                if (currentApplication != null) {
                    applicationSid = currentApplication.getSid().toString();
                }
            }
            return applicationSid;
        } catch ( TooManyResultsException e) {
            /* This happens when a non-upgraded project whose friendly-name exists in several applications is upgraded.
               The old bahaviour was a broken upgrade attempt for the whole workspace.
               Now, the failure should be limited to this project
               */
            throw new RvdProjectsMigrationException("[ERROR-CODE:14] Error while upgrading project '" + projectName + "'. Several applications with such a FriendlyName were found. You have to manually restore this project.", 14);
        }
    }

    public void renameProjectUsingNewConvention(String projectName, String applicationSid) throws RvdProjectsMigrationException {
        try {
            renameProject(projectName, applicationSid);
        } catch (IOException e) {
            throw new RvdProjectsMigrationException("[ERROR-CODE:5] Error while renaming the project '" + projectName
                    + "' to '" + applicationSid + "'" + e.getMessage(), 5);
        }
    }

    public void renameProject(String source, String dest) throws IOException {
        File sourceDir = new File(workspacePath + File.separator + source);
        File destDir = new File(workspacePath + File.separator + dest);
        FileUtils.moveDirectory(sourceDir, destDir);
    }

    public void loadProjectState(String projectName) throws RvdProjectsMigrationException {
        try {
            String pathName = workspacePath + File.separator + projectName + File.separator + "state";
            File file = new File(pathName);
            if (!file.exists()) {
                throw new RvdProjectsMigrationException("File " + file.getPath() + "does not exist");
            }
            String data = FileUtils.readFileToString(file, Charset.forName("UTF-8"));
            JsonParser parser = new JsonParser();
            JsonElement headerElement = parser.parse(data).getAsJsonObject().get("header");
            if (headerElement == null) {
                throw new RvdProjectsMigrationException();
            }
            Gson gson = new Gson();
            currentStateHeader = gson.fromJson(headerElement, StateHeader.class);
        } catch (IOException e) {
            throw new RvdProjectsMigrationException("[ERROR-CODE:6] Error loading state file from project '" + projectName
                    + "' " + e.getMessage(), 6);
        }
    }

    public boolean projectUsesNewNamingConvention(String projectName) {
        return Sid.pattern.matcher(projectName).matches();
    }

    public String createOrUpdateApplicationEntity(String applicationSid, String projectName)
            throws RvdProjectsMigrationException {
        try {
            if(applicationSid != null) {
                // Update application
                currentApplication = currentApplication.setRcmlUrl(URI.create("/visual-designer/services/apps/" + applicationSid
                        + "/controller"));
                applicationDao.updateApplication(currentApplication);
                return applicationSid;
            } else {
                // Create new application
                Account account = accountsDao.getAccount(currentStateHeader.getOwner());
                if (account == null) {
                    throw new RvdProjectsMigrationException("Error locating the owner account for project \"" + projectName
                            + "\"");
                }
                final Application.Builder builder = Application.builder();
                final Sid sid = Sid.generate(Sid.Type.APPLICATION);
                builder.setSid(sid);
                builder.setFriendlyName(projectName);
                builder.setAccountSid(account.getSid());
                builder.setApiVersion(configuration.subset("runtime-settings").getString("api-version"));
                builder.setHasVoiceCallerIdLookup(false);
                String rootUri = configuration.subset("runtime-settings").getString("root-uri");
                rootUri = StringUtils.addSuffixIfNotPresent(rootUri, "/");
                final StringBuilder buffer = new StringBuilder();
                buffer.append(rootUri).append(configuration.subset("runtime-settings").getString("api-version"))
                .append("/Accounts/").append(account.getSid().toString()).append("/Applications/").append(sid.toString());
                builder.setUri(URI.create(buffer.toString()));
                builder.setRcmlUrl(URI.create("/visual-designer/services/apps/" + sid.toString() + "/controller"));
                builder.setKind(Application.Kind.getValueOf(currentStateHeader.getProjectKind()));
                currentApplication = builder.build();
                applicationDao.addApplication(currentApplication);
                return sid.toString();
            }
        } catch (RvdProjectsMigrationException e) {
            String suffix = currentApplication != null ? "with the application '" + currentApplication.getSid().toString()
                    + "' " : "";
            throw new RvdProjectsMigrationException("[ERROR-CODE:7] Error while synchronizing the project '" + projectName
                    + "' " + suffix + e.getMessage(), 7);
        }
    }

    public int updateIncomingPhoneNumbers(String applicationSid, String projectName) throws RvdProjectsMigrationException {
        try {

            if (dids == null) {
                IncomingPhoneNumberFilter.Builder filterBuilder = IncomingPhoneNumberFilter.Builder.builder();
                dids = didsDao.getIncomingPhoneNumbersByFilter(filterBuilder.build());
            }
        } catch (Exception e) {
            throw new RvdProjectsMigrationException(
                    "[ERROR-CODE:8] Error while loading IncomingPhoneNumbers list for updates with project '" + applicationSid
                            + "' " + e.getMessage(), 8);
        }
        Application.Kind kind = Application.Kind.getValueOf(currentStateHeader.getProjectKind());
        IncomingPhoneNumber did = null;
        int amountUpdated = 0;
        try {
            switch (kind) {
                case SMS:
                    for (int i = 0; i < dids.size(); i++) {
                        did = dids.get(i);
                        if (hasUrlReference(did.getSmsUrl(), currentApplication.getFriendlyName())) {
                            Sid smsApplicationSid = new Sid(applicationSid);
                            IncomingPhoneNumber updateSmsDid = new IncomingPhoneNumber(did.getSid(), did.getDateCreated(),
                                    did.getDateUpdated(), did.getFriendlyName(), did.getAccountSid(), did.getPhoneNumber(),
                                    did.getCost(), did.getApiVersion(), did.hasVoiceCallerIdLookup(), did.getVoiceUrl(),
                                    did.getVoiceMethod(), did.getVoiceFallbackUrl(), did.getVoiceFallbackMethod(),
                                    did.getStatusCallback(), did.getStatusCallbackMethod(), did.getVoiceApplicationSid(), null,
                                    did.getSmsMethod(), did.getSmsFallbackUrl(), did.getSmsFallbackMethod(), smsApplicationSid,
                                    did.getUri(), did.getUssdUrl(), did.getUssdMethod(), did.getUssdFallbackUrl(),
                                    did.getUssdFallbackMethod(), did.getUssdApplicationSid(),
                                    did.getReferUrl(), did.getReferMethod(), did.getReferApplicationSid(),
                                    did.isVoiceCapable(),
                                    did.isSmsCapable(), did.isMmsCapable(), did.isFaxCapable(), did.isPureSip(), did.getOrganizationSid());
                            didsDao.updateIncomingPhoneNumber(updateSmsDid);
                            dids.set(i, updateSmsDid);
                            amountUpdated++;
                        }
                    }
                    break;
                case USSD:
                    for (int i = 0; i < dids.size(); i++) {
                        did = dids.get(i);
                        if (hasUrlReference(did.getUssdUrl(), currentApplication.getFriendlyName())) {
                            Sid ussdApplicationSid = new Sid(applicationSid);
                            IncomingPhoneNumber updateUssdDid = new IncomingPhoneNumber(did.getSid(), did.getDateCreated(),
                                    did.getDateUpdated(), did.getFriendlyName(), did.getAccountSid(), did.getPhoneNumber(),
                                    did.getCost(), did.getApiVersion(), did.hasVoiceCallerIdLookup(), did.getVoiceUrl(),
                                    did.getVoiceMethod(), did.getVoiceFallbackUrl(), did.getVoiceFallbackMethod(),
                                    did.getStatusCallback(), did.getStatusCallbackMethod(), did.getVoiceApplicationSid(),
                                    did.getSmsUrl(), did.getSmsMethod(), did.getSmsFallbackUrl(), did.getSmsFallbackMethod(),
                                    did.getSmsApplicationSid(), did.getUri(), null, did.getUssdMethod(),
                                    did.getUssdFallbackUrl(), did.getUssdFallbackMethod(), ussdApplicationSid,
                                    did.getReferUrl(), did.getReferMethod(), did.getReferApplicationSid(),
                                    did.isVoiceCapable(), did.isSmsCapable(), did.isMmsCapable(), did.isFaxCapable(),
                                    did.isPureSip(), did.getOrganizationSid());
                            didsDao.updateIncomingPhoneNumber(updateUssdDid);
                            dids.set(i, updateUssdDid);
                            amountUpdated++;
                        }
                    }
                    break;
                case VOICE:
                    for (int i = 0; i < dids.size(); i++) {
                        did = dids.get(i);
                        if (hasUrlReference(did.getVoiceUrl(), currentApplication.getFriendlyName())) {
                            Sid voiceApplicationSid = new Sid(applicationSid);
                            IncomingPhoneNumber updateVoiceDid = new IncomingPhoneNumber(did.getSid(), did.getDateCreated(),
                                    did.getDateUpdated(), did.getFriendlyName(), did.getAccountSid(), did.getPhoneNumber(),
                                    did.getCost(), did.getApiVersion(), did.hasVoiceCallerIdLookup(), null,
                                    did.getVoiceMethod(), did.getVoiceFallbackUrl(), did.getVoiceFallbackMethod(),
                                    did.getStatusCallback(), did.getStatusCallbackMethod(), voiceApplicationSid,
                                    did.getSmsUrl(), did.getSmsMethod(), did.getSmsFallbackUrl(), did.getSmsFallbackMethod(),
                                    did.getSmsApplicationSid(), did.getUri(), did.getUssdUrl(), did.getUssdMethod(),
                                    did.getUssdFallbackUrl(), did.getUssdFallbackMethod(), did.getUssdApplicationSid(),
                                    did.getReferUrl(), did.getReferMethod(), did.getReferApplicationSid(),
                                    did.isVoiceCapable(), did.isSmsCapable(), did.isMmsCapable(), did.isFaxCapable(),
                                    did.isPureSip(), did.getOrganizationSid());
                            didsDao.updateIncomingPhoneNumber(updateVoiceDid);
                            dids.set(i, updateVoiceDid);
                            amountUpdated++;
                        }
                    }
                    break;
                default:
                    break;
            }
        } catch (UnsupportedEncodingException e) {
            throw new RvdProjectsMigrationException("[ERROR-CODE:9] Error while updating IncomingPhoneNumber '"
                    + did.getSid().toString() + "' with the Application '" + applicationSid + "' "
                    + e.getMessage(), 9);
        }
        return amountUpdated;
    }

    private boolean hasUrlReference(URI url, String projectName) throws UnsupportedEncodingException {
        if (url != null && !url.toString().isEmpty()) {
            Matcher m = RVD_PROJECT_URL.matcher(url.toString());
            if (m.find()) {
                String result = m.group(1);
                result = URLDecoder.decode(result, "UTF-8");
                return projectName.equals(result);
            }
        }
        return false;
    }

    public int updateClients(String applicationSid, String projectName) throws RvdProjectsMigrationException {
        try {
            if (clients == null) {
                clients = clientsDao.getAllClients();
            }

        } catch (Exception e) {
            throw new RvdProjectsMigrationException(
                    "[ERROR-CODE:10] Error while loading the Clients list for updates with project '" + applicationSid + "'. "
                            + e.getMessage(), 10);
        }
        Application.Kind kind = Application.Kind.getValueOf(currentStateHeader.getProjectKind());
        Client client = null;
        int amountUpdated = 0;
        try {
            if (kind == Application.Kind.VOICE) {
                for (int i = 0; i < clients.size(); i++) {
                    client = clients.get(i);
                    if (hasUrlReference(client.getVoiceUrl(), currentApplication.getFriendlyName())) {
                        Sid voiceApplicationSid = new Sid(applicationSid);
                        client = client.setVoiceApplicationSid(voiceApplicationSid);
                        client = client.setVoiceUrl(null);
                        clientsDao.updateClient(client);
                        clients.set(i, client);
                        amountUpdated++;
                    }
                }
            }
        } catch (Exception e) {
            throw new RvdProjectsMigrationException("[ERROR-CODE:11] Error while updating Client '"
                    + client.getSid().toString() + "' with the Application '" + applicationSid + "' "
                    + e.getMessage(), 11);
        }
        return amountUpdated;
    }

    public void storeWorkspaceStatus(boolean migrationSucceeded) throws RvdProjectsMigrationException {
        String pathName = workspacePath + File.separator + ".version";
        File file = new File(pathName);
        Gson gson = new Gson();
        String version = org.restcomm.connect.commons.Version.getVersion();
        Version ws = new Version(migrationSucceeded, version);
        String data = gson.toJson(ws);
        try {
            FileUtils.writeStringToFile(file, data, "UTF-8");
        } catch (IOException e) {
            throw new RvdProjectsMigrationException("[ERROR-CODE:12] Error creating file in storage: " + file + e.getMessage(),
                    12);
        }
    }

    public boolean isMigrationExecuted() {
        String pathName = workspacePath + File.separator + ".version";
        File file = new File(pathName);
        if (!file.exists()) {
            return false;
        }
        String data;
        try {
            data = FileUtils.readFileToString(file, Charset.forName("UTF-8"));
        } catch (IOException e) {
            return false;
        }
        JsonParser parser = new JsonParser();
        JsonElement element = parser.parse(data).getAsJsonObject();
        if (element != null) {
            Gson gson = new Gson();
            Version workspaceVersion = gson.fromJson(element, Version.class);
            String restcommVersion = org.restcomm.connect.commons.Version.getVersion();
            if (!workspaceVersion.getVersion().equals(restcommVersion) && !workspaceVersion.getStatus()) {
                return false;
            } else {
                return true;
            }
        }
        return false;
    }

    public void addNotification(String message, boolean error, Integer errorCode) throws URISyntaxException {
        Notification.Builder builder = Notification.builder();
        Sid sid = Sid.generate(Sid.Type.NOTIFICATION);
        builder.setSid(sid);
        builder.setAccountSid(new Sid(ACCOUNT_NOTIFICATIONS_SID));
        builder.setApiVersion(configuration.subset("runtime-settings").getString("api-version"));
        builder.setLog(error ? new Integer(1) : new Integer(0));
        builder.setErrorCode(errorCode);
        builder.setMoreInfo(new URI("http://docs.telestax.com/rvd-workspace-upgrade"));
        builder.setMessageText(message);
        builder.setMessageDate(DateTime.now());
        builder.setRequestUrl(new URI(""));
        builder.setRequestMethod("");
        builder.setRequestVariables("");
        StringBuilder buffer = new StringBuilder();
        buffer.append("/").append(configuration.subset("runtime-settings").getString("api-version")).append("/Accounts/");
        buffer.append(ACCOUNT_NOTIFICATIONS_SID).append("/Notifications/");
        buffer.append(sid.toString());
        final URI uri = URI.create(buffer.toString());
        builder.setUri(uri);

        Notification notification = builder.build();
        notificationsDao.addNotification(notification);
    }

    public boolean isMigrationEnabled() {
        Boolean value = new Boolean(configuration.subset("runtime-settings").getString("rvd-workspace-migration-enabled", "true"));
        return value;
    }

    public void sendEmailNotification(String message, boolean migrationSucceeded) throws RvdProjectsMigrationException {
        String host = configuration.subset("smtp-notify").getString("host");
        String username = configuration.subset("smtp-notify").getString("user");
        String password = configuration.subset("smtp-notify").getString("password");
        String defaultEmailAddress = configuration.subset("smtp-notify").getString("default-email-address");
        if (host == null || username == null || password == null || defaultEmailAddress == null || host.isEmpty()
                || username.isEmpty() || password.isEmpty() || defaultEmailAddress.isEmpty()) {
            throw new RvdProjectsMigrationException("Skipping email notification due to invalid configuration");
        }
        if (emailService == null) {
            emailService = emailService(configuration.subset("smtp-notify"));
        }

        String subject = "Restcomm - RVD Projects migration";
        String body = message;
        if (!migrationSucceeded) {
            body += ". Please, visit http://docs.telestax.com/rvd-workspace-upgrade for more information on how to troubleshoot workspace migration issues.";
        }

        final Mail emailMsg = new Mail(username + "@" + host, defaultEmailAddress, subject, body);
        emailService.tell(new EmailRequest(emailMsg), emailService);

    }

    private ActorRef emailService(final Configuration configuration) {
        final Props props = new Props(new UntypedActorFactory() {
            private static final long serialVersionUID = 1L;

            @Override
            public Actor create() throws Exception {
                return new EmailService(configuration);
            }
        });
        return system.actorOf(props);
    }

    public boolean isEmbeddedMigration() {
        return this.embeddedMigration;
    }

    private class Version {

        boolean status;
        String version;

        public Version() {

        }

        public Version(boolean namingMigrationSucceeded, String versionLastRun) {
            super();
            this.status = namingMigrationSucceeded;
            this.version = versionLastRun;
        }

        public boolean getStatus() {
            return status;
        }

        public String getVersion() {
            return version;
        }

    }

    private class RvdConfig {
        private String workspaceLocation;
        private String workspaceBackupLocation;
        private String sslMode;
        private String restcommBaseUrl;

        public RvdConfig() {
        }

        public RvdConfig(String workspaceLocation, String workspaceBackupLocation, String restcommPublicIp, String sslMode) {
            super();
            this.workspaceLocation = workspaceLocation;
            this.workspaceBackupLocation = workspaceBackupLocation;
            this.sslMode = sslMode;
        }

        public String getWorkspaceLocation() {
            return workspaceLocation;
        }

        public String getWorkspaceBackupLocation() {
            return workspaceBackupLocation;
        }

        public String getSslMode() {
            return sslMode;
        }

        public String getRestcommBaseUrl() {
            return restcommBaseUrl;
        }
    }

    private class StateHeader {
        // application logging settings for this project. If not null logging is enabled.
        // We are using an object instead of a boolean to easily add properties in the future
        public class Logging {
        }

        String projectKind;
        String startNodeName;
        String version;
        String owner; // the Restcomm user id that owns the project or null if it has no owner at all. Added in 7.1.6 release

        // Logging logging; - moved to the separate 'settings' file
        public StateHeader() {
        }

        public StateHeader(String projectKind, String startNodeName, String version) {
            super();
            this.projectKind = projectKind;
            this.startNodeName = startNodeName;
            this.version = version;
        }

        public StateHeader(String projectKind, String startNodeName, String version, String owner) {
            super();
            this.projectKind = projectKind;
            this.startNodeName = startNodeName;
            this.version = version;
            this.owner = owner;
        }

        public String getProjectKind() {
            return projectKind;
        }

        public String getStartNodeName() {
            return startNodeName;
        }

        public String getVersion() {
            return version;
        }

        public String getOwner() {
            return owner;
        }

        public void setOwner(String owner2) {
            this.owner = owner2;
        }

        public void setVersion(String version) {
            this.version = version;
        }
    }
}
