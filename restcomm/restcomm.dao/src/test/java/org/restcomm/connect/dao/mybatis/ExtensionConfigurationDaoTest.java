package org.restcomm.connect.dao.mybatis;

import java.io.File;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.joda.time.DateTime;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.restcomm.connect.commons.annotations.UnstableTests;
import org.restcomm.connect.commons.dao.Sid;
import org.restcomm.connect.extension.api.ConfigurationException;
import org.restcomm.connect.extension.api.ExtensionConfiguration;

import java.io.InputStream;
import java.nio.file.Files;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import java.util.List;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import org.junit.Rule;
import org.junit.rules.TestName;

/**
 * Created by gvagenas on 21/10/2016.
 */
@Category(UnstableTests.class)
@Ignore
public class ExtensionConfigurationDaoTest {
    private static MybatisDaoManager manager;
    private MybatisExtensionsConfigurationDao extensionsConfigurationDao;

    @Rule
    public TestName name = new TestName();

    private String validJsonObject = "{\n" +
            "  \"project\": \"Restcomm-Connect\",\n" +
            "  \"url\": \"http://restcomm.com\",\n" +
            "  \"open-source\": true,\n" +
            "  \"scm\": {\n" +
            "    \"url\": \"https://github.com/RestComm/Restcomm-Connect/\",\n" +
            "    \"issues\": \"https://github.com/RestComm/Restcomm-Connect/issues\"\n" +
            "  },\n" +
            "  \"ci\": {\n" +
            "      \"name\": \"Jenkins\",\n" +
            "      \"url\": \"https://mobicents.ci.cloudbees.com/view/RestComm/job/RestComm/\"\n" +
            "  },\n" +
            "  \"active\": \"true\"\n" +
            "}";

    private String invalidJsonObject = "{\n" +
            "  project: Restcomm-Connect,\n" +
            "  \"url\": \"http://restcomm.com\",\n" +
            "  \"open-source\": true,\n" +
            "  \"scm\": {\n" +
            "    \"url\": \"https://github.com/RestComm/Restcomm-Connect/\",\n" +
            "    \"issues\": \"https://github.com/RestComm/Restcomm-Connect/issues\"\n" +
            "  }\n" +
            "  \"ci\": [\n" +
            "    {\n" +
            "      \"name\": \"Jenkins\",\n" +
            "      \"url\": \"https://mobicents.ci.cloudbees.com/view/RestComm/job/RestComm/\"\n" +
            "    }\n" +
            "  \"active\": \"true\"\n" +
            "}";

    private String validXmlDoc = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<project>\n" +
            "  <name>Restcomm-Connect</name>\n" +
            "  <url>http://restcomm.com</url>\n" +
            "  <open-source>true</open-source>\n" +
            "  <scm>\n" +
            "    <url>https://github.com/RestComm/Restcomm-Connect/</url>\n" +
            "    <issues>https://github.com/RestComm/Restcomm-Connect/issues</issues>\n" +
            "  </scm>\n" +
            "  <ci>\n" +
            "    <name>Jenkins</name>\n" +
            "    <url>https://mobicents.ci.cloudbees.com/view/RestComm/job/RestComm/</url>\n" +
            "  </ci>\n" +
            "</project>";

    private String invalidXmlDoc = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<project>\n" +
            "  <name>Restcomm-Connect</name>\n" +
            "  <url>http://restcomm.com</url>\n" +
            "  <open-source>true</open-source>\n" +
            "  <scm>\n" +
            "    <url>https://github.com/RestComm/Restcomm-Connect/</url>\n" +
            "    <issues>https://github.com/RestComm/Restcomm-Connect/issues</issues>\n" +
            "  </scm>\n" +
            "  <ci>\n" +
            "    <name>Jenkins</name>\n" +
            "    <url>https://mobicents.ci.cloudbees.com/view/RestComm/job/RestComm/</url>\n" +
            "  </ci>";

    private String updatedJsonObject = "{\n" +
            "  \"project\": \"Restcomm-Connect\",\n" +
            "  \"url\": \"http://restcomm.com\",\n" +
            "  \"open-source\": true,\n" +
            "  \"scm\": {\n" +
            "    \"url\": \"https://github.com/RestComm/Restcomm-Connect/\",\n" +
            "    \"issues\": \"https://github.com/RestComm/Restcomm-Connect/issues\"\n" +
            "  },\n" +
            "  \"ci\": {\n" +
            "      \"name\": \"Jenkins\",\n" +
            "      \"url\": \"https://mobicents.ci.cloudbees.com/view/RestComm/job/RestComm/\"\n" +
            "  },\n" +
            "  \"active\": \"false\"\n" +
            "}";

    private String updatedXmlDoc = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<project>\n" +
            "  <name>Restcomm-Connect</name>\n" +
            "  <url>http://restcomm.com</url>\n" +
            "  <open-source>true</open-source>\n" +
            "  <scm>\n" +
            "    <url>https://github.com/RestComm/Restcomm-Connect/</url>\n" +
            "    <issues>https://github.com/RestComm/Restcomm-Connect/issues</issues>\n" +
            "  </scm>\n" +
            "  <ci>\n" +
            "    <name>Jenkins</name>\n" +
            "    <url>https://mobicents.ci.cloudbees.com/view/RestComm/job/RestComm/</url>\n" +
            "  </ci>\n" +
            "  <active>true</active>\n" +
            "</project>";

    @Before
    public void before() throws Exception{
        //use a different data dir for each test case to provide isolation
        Properties properties = new Properties();
        File srcFile = new File("./target/test-classes/data/restcomm.script");
        File theDir = new File("./target/test-classes/data" + name.getMethodName());
        theDir.mkdir();
        File destFile = new File("./target/test-classes/data" + name.getMethodName() + "/restcomm.script");

        Files.copy(srcFile.toPath(),
                destFile.toPath(), REPLACE_EXISTING);
        properties.setProperty("data", name.getMethodName());

        final InputStream data = getClass().getResourceAsStream("/mybatis_pertest.xml");
        final SqlSessionFactoryBuilder builder = new SqlSessionFactoryBuilder();
        final SqlSessionFactory factory = builder.build(data,properties);
        manager = new MybatisDaoManager();
        manager.start(factory);
        extensionsConfigurationDao = (MybatisExtensionsConfigurationDao) manager.getExtensionsConfigurationDao();
    }

    @After
    public void after() {
        manager.shutdown();
    }

    @Test
    public void testValidJsonDoc() {
        ExtensionConfiguration validExtensionConfiguration = new ExtensionConfiguration(Sid.generate(Sid.Type.EXTENSION_CONFIGURATION),
                "testExt", true, validJsonObject, ExtensionConfiguration.configurationType.JSON, DateTime.now());
        assertEquals(true, extensionsConfigurationDao.validate(validExtensionConfiguration));
    }

    @Test
    public void testInvalidJsonDoc() {
        ExtensionConfiguration invalidExtensionConfiguration = new ExtensionConfiguration(Sid.generate(Sid.Type.EXTENSION_CONFIGURATION),
                "testExt", true, invalidJsonObject, ExtensionConfiguration.configurationType.JSON, DateTime.now());
        assertEquals(false, extensionsConfigurationDao.validate(invalidExtensionConfiguration));
    }

    @Test
    public void testValidXmlDoc() {
        ExtensionConfiguration validExtensionConfiguration = new ExtensionConfiguration(Sid.generate(Sid.Type.EXTENSION_CONFIGURATION),
                "testExt", true, validXmlDoc, ExtensionConfiguration.configurationType.XML, DateTime.now());
        assertEquals(true, extensionsConfigurationDao.validate(validExtensionConfiguration));
    }

    @Test
    public void testInvalidXmlDoc() {
        ExtensionConfiguration invalidExtensionConfiguration = new ExtensionConfiguration(Sid.generate(Sid.Type.EXTENSION_CONFIGURATION),
                "testExt", true, invalidXmlDoc, ExtensionConfiguration.configurationType.XML, DateTime.now());
        assertEquals(false, extensionsConfigurationDao.validate(invalidExtensionConfiguration));
    }

    @Test
    public void testStoreAndRetrieveConfigurationByNameJson() throws ConfigurationException {
        String extName = "testExt";
        ExtensionConfiguration validExtensionConfiguration = new ExtensionConfiguration(Sid.generate(Sid.Type.EXTENSION_CONFIGURATION),
                extName, true, validJsonObject, ExtensionConfiguration.configurationType.JSON, DateTime.now());
        extensionsConfigurationDao.addConfiguration(validExtensionConfiguration);
        ExtensionConfiguration retrievedConf = extensionsConfigurationDao.getConfigurationByName(extName);
        assertNotNull(retrievedConf);
        assertEquals(retrievedConf.getConfigurationData().toString(), validExtensionConfiguration.getConfigurationData().toString());
        extensionsConfigurationDao.deleteConfigurationByName(extName);
    }

    @Test
    public void testStoreAndRetrieveConfigurationBySidJson() throws ConfigurationException {
        Sid sid = Sid.generate(Sid.Type.EXTENSION_CONFIGURATION);
        ExtensionConfiguration validExtensionConfiguration = new ExtensionConfiguration(sid,
                "testExt", true, validJsonObject, ExtensionConfiguration.configurationType.JSON, DateTime.now());
        extensionsConfigurationDao.addConfiguration(validExtensionConfiguration);
        ExtensionConfiguration retrievedConf = extensionsConfigurationDao.getConfigurationBySid(sid);
        assertNotNull(retrievedConf);
        assertEquals(retrievedConf.getConfigurationData().toString(), validExtensionConfiguration.getConfigurationData().toString());
        extensionsConfigurationDao.deleteConfigurationBySid(sid);
    }

    @Test
    public void testStoreUpdateAndRetrieveConfigurationByNameJson() throws ConfigurationException {
        String extName = "testExt";
        ExtensionConfiguration validExtensionConfiguration = new ExtensionConfiguration(Sid.generate(Sid.Type.EXTENSION_CONFIGURATION),
                extName, true, validJsonObject, ExtensionConfiguration.configurationType.JSON, DateTime.now());
        extensionsConfigurationDao.addConfiguration(validExtensionConfiguration);
        ExtensionConfiguration retrievedConf = extensionsConfigurationDao.getConfigurationByName(extName);
        assertNotNull(retrievedConf);
        assertEquals(retrievedConf.getConfigurationData().toString(), validExtensionConfiguration.getConfigurationData().toString());
        validExtensionConfiguration.setConfigurationData(updatedJsonObject, ExtensionConfiguration.configurationType.JSON);
        extensionsConfigurationDao.updateConfiguration(validExtensionConfiguration);
        retrievedConf = extensionsConfigurationDao.getConfigurationByName(extName);
        assertEquals(updatedJsonObject, retrievedConf.getConfigurationData());
        extensionsConfigurationDao.deleteConfigurationByName(extName);
    }

    @Test
    public void testStoreUpdateAndRetrieveConfigurationBySidJson() throws ConfigurationException {
        Sid sid = Sid.generate(Sid.Type.EXTENSION_CONFIGURATION);
        ExtensionConfiguration validExtensionConfiguration = new ExtensionConfiguration(sid,
                "testExt", true, validJsonObject, ExtensionConfiguration.configurationType.JSON, DateTime.now());
        extensionsConfigurationDao.addConfiguration(validExtensionConfiguration);
        ExtensionConfiguration retrievedConf = extensionsConfigurationDao.getConfigurationBySid(sid);
        assertNotNull(retrievedConf);
        assertEquals(retrievedConf.getConfigurationData().toString(), validExtensionConfiguration.getConfigurationData().toString());
        validExtensionConfiguration.setConfigurationData(updatedJsonObject, ExtensionConfiguration.configurationType.JSON);
        extensionsConfigurationDao.updateConfiguration(validExtensionConfiguration);
        retrievedConf = extensionsConfigurationDao.getConfigurationBySid(sid);
        assertEquals(updatedJsonObject, retrievedConf.getConfigurationData());
        extensionsConfigurationDao.deleteConfigurationBySid(sid);
    }

    @Test
    public void testStoreAndRetrieveConfigurationByNameXml() throws ConfigurationException {
        String extName = "testExt";
        ExtensionConfiguration validExtensionConfiguration = new ExtensionConfiguration(Sid.generate(Sid.Type.EXTENSION_CONFIGURATION),
                extName, true, validXmlDoc, ExtensionConfiguration.configurationType.XML, DateTime.now());
        extensionsConfigurationDao.addConfiguration(validExtensionConfiguration);
        ExtensionConfiguration retrievedConf = extensionsConfigurationDao.getConfigurationByName(extName);
        assertNotNull(retrievedConf);
        assertEquals(retrievedConf.getConfigurationData().toString(), validExtensionConfiguration.getConfigurationData().toString());
        extensionsConfigurationDao.deleteConfigurationByName(extName);
    }

    @Test
    public void testStoreAndRetrieveConfigurationBySidXml() throws ConfigurationException {
        Sid sid = Sid.generate(Sid.Type.EXTENSION_CONFIGURATION);
        ExtensionConfiguration validExtensionConfiguration = new ExtensionConfiguration(sid,
                "testExt", true, validXmlDoc, ExtensionConfiguration.configurationType.XML, DateTime.now());
        extensionsConfigurationDao.addConfiguration(validExtensionConfiguration);
        ExtensionConfiguration retrievedConf = extensionsConfigurationDao.getConfigurationBySid(sid);
        assertNotNull(retrievedConf);
        assertEquals(retrievedConf.getConfigurationData().toString(), validExtensionConfiguration.getConfigurationData().toString());
        extensionsConfigurationDao.deleteConfigurationBySid(sid);
    }

    @Test
    public void testStoreUpdateAndRetrieveConfigurationByNameXml() throws ConfigurationException {
        String extName = "testExt";
        ExtensionConfiguration validExtensionConfiguration = new ExtensionConfiguration(Sid.generate(Sid.Type.EXTENSION_CONFIGURATION),
                extName, true, validXmlDoc, ExtensionConfiguration.configurationType.XML, DateTime.now());
        extensionsConfigurationDao.addConfiguration(validExtensionConfiguration);
        ExtensionConfiguration retrievedConf = extensionsConfigurationDao.getConfigurationByName(extName);
        assertNotNull(retrievedConf);
        assertEquals(retrievedConf.getConfigurationData().toString(), validExtensionConfiguration.getConfigurationData().toString());
        validExtensionConfiguration.setConfigurationData(updatedXmlDoc, ExtensionConfiguration.configurationType.XML);
        extensionsConfigurationDao.updateConfiguration(validExtensionConfiguration);
        retrievedConf = extensionsConfigurationDao.getConfigurationByName(extName);
        assertEquals(updatedXmlDoc, retrievedConf.getConfigurationData());
        extensionsConfigurationDao.deleteConfigurationByName(extName);
    }

    @Test
    public void testStoreUpdateAndRetrieveConfigurationBySidXml() throws ConfigurationException {
        Sid sid = Sid.generate(Sid.Type.EXTENSION_CONFIGURATION);
        ExtensionConfiguration validExtensionConfiguration = new ExtensionConfiguration(sid,
                "testExt", true, validXmlDoc, ExtensionConfiguration.configurationType.XML, DateTime.now());
        extensionsConfigurationDao.addConfiguration(validExtensionConfiguration);
        ExtensionConfiguration retrievedConf = extensionsConfigurationDao.getConfigurationBySid(sid);
        assertNotNull(retrievedConf);
        assertEquals(retrievedConf.getConfigurationData().toString(), validExtensionConfiguration.getConfigurationData().toString());
        validExtensionConfiguration.setConfigurationData(updatedXmlDoc, ExtensionConfiguration.configurationType.XML);
        extensionsConfigurationDao.updateConfiguration(validExtensionConfiguration);
        retrievedConf = extensionsConfigurationDao.getConfigurationBySid(sid);
        assertEquals(updatedXmlDoc, retrievedConf.getConfigurationData());
        extensionsConfigurationDao.deleteConfigurationBySid(sid);
    }

    @Test
    public void testStoreAndGetAllConfiguration() throws ConfigurationException {
        Sid sid1 = Sid.generate(Sid.Type.EXTENSION_CONFIGURATION);
        Sid sid2 = Sid.generate(Sid.Type.EXTENSION_CONFIGURATION);
        ExtensionConfiguration validExtensionConfigurationXml = new ExtensionConfiguration(sid1,
                "testExtXml", true, validXmlDoc, ExtensionConfiguration.configurationType.XML, DateTime.now());
        ExtensionConfiguration validExtensionConfigurationJson = new ExtensionConfiguration(sid2,
                "testExtJson", true, validJsonObject, ExtensionConfiguration.configurationType.JSON, DateTime.now());

        extensionsConfigurationDao.addConfiguration(validExtensionConfigurationXml);
        extensionsConfigurationDao.addConfiguration(validExtensionConfigurationJson);

        List<ExtensionConfiguration> confs = extensionsConfigurationDao.getAllConfiguration();

        assertEquals(2, confs.size());

        extensionsConfigurationDao.deleteConfigurationBySid(sid1);
        extensionsConfigurationDao.deleteConfigurationBySid(sid2);

        confs = extensionsConfigurationDao.getAllConfiguration();
        assertEquals(0, confs.size());
    }

    @Test
    public void testStoreUpdateAndRetrieveConfigurationByNameCheckVersionJson() throws ConfigurationException {
        String extName = "testExt";
        ExtensionConfiguration originalExtensionConfiguration = new ExtensionConfiguration(Sid.generate(Sid.Type.EXTENSION_CONFIGURATION),
                extName, true, validJsonObject, ExtensionConfiguration.configurationType.JSON, DateTime.now());
        extensionsConfigurationDao.addConfiguration(originalExtensionConfiguration);

        ExtensionConfiguration retrievedConf = extensionsConfigurationDao.getConfigurationByName(extName);
        assertNotNull(retrievedConf);
        assertEquals(retrievedConf.getConfigurationData().toString(), originalExtensionConfiguration.getConfigurationData().toString());

        DateTime originalDateUpdated = retrievedConf.getDateUpdated();

        boolean isUpdated = extensionsConfigurationDao.isLatestVersionByName(extName, originalDateUpdated);
        assertEquals(isUpdated , false);

        originalExtensionConfiguration.setConfigurationData(updatedJsonObject, ExtensionConfiguration.configurationType.JSON);
        extensionsConfigurationDao.updateConfiguration(originalExtensionConfiguration);

        isUpdated = extensionsConfigurationDao.isLatestVersionByName(extName, originalDateUpdated);
        assertEquals(isUpdated , true);

        extensionsConfigurationDao.deleteConfigurationByName(extName);
    }

    @Test
    public void testStoreUpdateAndRetrieveConfigurationBySidCheckVersionJson() throws ConfigurationException {
        String extName = "testExt";
        Sid sid = Sid.generate(Sid.Type.EXTENSION_CONFIGURATION);
        ExtensionConfiguration originalExtensionConfiguration = new ExtensionConfiguration(sid,
                extName, true, validJsonObject, ExtensionConfiguration.configurationType.JSON, DateTime.now());
        extensionsConfigurationDao.addConfiguration(originalExtensionConfiguration);

        ExtensionConfiguration retrievedConf = extensionsConfigurationDao.getConfigurationBySid(sid);
        assertNotNull(retrievedConf);
        assertEquals(retrievedConf.getConfigurationData().toString(), originalExtensionConfiguration.getConfigurationData().toString());

        DateTime originalDateUpdated = retrievedConf.getDateUpdated();

        boolean isUpdated = extensionsConfigurationDao.isLatestVersionBySid(sid, originalDateUpdated);
        assertEquals(isUpdated , false);

        originalExtensionConfiguration.setConfigurationData(updatedJsonObject, ExtensionConfiguration.configurationType.JSON);
        extensionsConfigurationDao.updateConfiguration(originalExtensionConfiguration);

        isUpdated = extensionsConfigurationDao.isLatestVersionBySid(sid, originalDateUpdated);
        assertEquals(isUpdated , true);

        extensionsConfigurationDao.deleteConfigurationBySid(sid);
    }
}
