package org.restcomm.connect.testsuite.http;

import com.google.gson.JsonObject;
import com.sun.jersey.core.util.MultivaluedMapImpl;
import org.apache.log4j.Logger;
import org.jboss.arquillian.container.test.api.Deployer;
import org.jboss.arquillian.container.test.api.Deployment;
import org.jboss.arquillian.junit.Arquillian;
import org.jboss.arquillian.test.api.ArquillianResource;
import org.jboss.shrinkwrap.api.ShrinkWrap;
import org.jboss.shrinkwrap.api.spec.WebArchive;
import org.jboss.shrinkwrap.resolver.api.maven.Maven;
import org.junit.Test;
import org.junit.FixMethodOrder;
import org.junit.runners.MethodSorters;
import org.junit.runner.RunWith;
import org.restcomm.connect.commons.Version;

import javax.ws.rs.core.MultivaluedMap;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * Created by gvagenas on 27/10/2016.
 */
@RunWith(Arquillian.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class ExtensionsConfigurationTest {
    private final static Logger logger = Logger.getLogger(ExtensionsConfigurationTest.class.getName());
    private static final String version = Version.getVersion();

    private String adminAccountSid = "ACae6e420f425248d6a26948c17a9e2acf";
    private String adminAuthToken = "77f8c12cc7b8f8423e5c38b035249166";

    @ArquillianResource
    private Deployer deployer;
    @ArquillianResource
    URL deploymentUrl;

    private String jsonConfiguration = "{\n" +
            "  \"extension_name\": \"testExtension\",\n" +
            "  \"option1\": \"true\",\n" +
            "  \"option2\": \"20\",\n" +
            "  \"option3\": \"false\",\n" +
            "  \"setOfOptions\": {\n" +
            "    \"subOption1\": \"true\",\n" +
            "    \"subOption2\": \"50\"\n" +
            "  },\n" +
            "  \"enabed\": \"true\"\n" +
            "}";

    private String updatedJsonConfiguration = "{\n" +
            "  \"extension_name\": \"testExtension\",\n" +
            "  \"option1\": \"false\",\n" +
            "  \"option2\": \"40\",\n" +
            "  \"option3\": \"true\",\n" +
            "  \"setOfOptions\": {\n" +
            "    \"subOption1\": \"false\",\n" +
            "    \"subOption2\": \"100\"\n" +
            "  },\n" +
            "  \"enabed\": \"true\"\n" +
            "}";

    @Test
    public void testCreateAndUpdateJsonConfiguration() throws UnsupportedEncodingException {
        String extensionName = "testExtension";
        MultivaluedMap<String, String> configurationParams = new MultivaluedMapImpl();
        configurationParams.add("ExtensionName", extensionName);
        configurationParams.add("ConfigurationData", jsonConfiguration);

        JsonObject response = RestcommExtensionsConfigurationTool.getInstance().postConfiguration(deploymentUrl.toString(), adminAccountSid,
                adminAuthToken, configurationParams);
        assertNotNull(response);

        JsonObject extension = RestcommExtensionsConfigurationTool.getInstance().getConfiguration(deploymentUrl.toString(), adminAccountSid,
                adminAuthToken, extensionName);
        assertNotNull(extension);
        String receivedConf = extension.get("configuration").getAsString();
        assertEquals(jsonConfiguration, receivedConf);

        String extensionSid = extension.get("sid").getAsString();

        MultivaluedMap<String, String> updatedConfigurationParams = new MultivaluedMapImpl();
        updatedConfigurationParams.add("ExtensionName", extensionName);
        updatedConfigurationParams.add("ConfigurationData", updatedJsonConfiguration);

        JsonObject updatedExtension = RestcommExtensionsConfigurationTool.getInstance().updateConfiguration(deploymentUrl.toString(), adminAccountSid,
                adminAuthToken, extensionSid, updatedConfigurationParams);
        assertNotNull(updatedExtension);
        String updatedReceveivedConf = updatedExtension.get("configuration").getAsString();
        assertEquals(updatedJsonConfiguration, updatedReceveivedConf);
    }

    @Test
    public void testCreateJsonConfigurationAndGetBySid() throws UnsupportedEncodingException {
        String extensionName = "testExtension2";
        MultivaluedMap<String, String> configurationParams = new MultivaluedMapImpl();
        configurationParams.add("ExtensionName", extensionName);
        configurationParams.add("ConfigurationData", jsonConfiguration);

        JsonObject response = RestcommExtensionsConfigurationTool.getInstance().postConfiguration(deploymentUrl.toString(), adminAccountSid,
                adminAuthToken, configurationParams);
        assertNotNull(response);

        JsonObject extension = RestcommExtensionsConfigurationTool.getInstance().getConfiguration(deploymentUrl.toString(), adminAccountSid,
                adminAuthToken, extensionName);
        assertNotNull(extension);
        String receivedConf = extension.get("configuration").getAsString();
        assertEquals(jsonConfiguration, receivedConf);

        String extensionSid = extension.get("sid").getAsString();

        JsonObject extensionBySid = RestcommExtensionsConfigurationTool.getInstance().getConfiguration(deploymentUrl.toString(), adminAccountSid,
                adminAuthToken, extensionSid);
        assertNotNull(extensionBySid);
        assertEquals(jsonConfiguration, extensionBySid.get("configuration").getAsString());
    }

    @Deployment(name = "ExtensionsConfigurationTest", managed = true, testable = false)
    public static WebArchive createWebArchiveNoGw() {
        logger.info("Packaging Test App");
        logger.info("version");
        WebArchive archive = ShrinkWrap.create(WebArchive.class, "restcomm.war");
        final WebArchive restcommArchive = Maven.resolver()
                .resolve("org.restcomm:restcomm-connect.application:war:" + version).withoutTransitivity()
                .asSingle(WebArchive.class);
        archive = archive.merge(restcommArchive);
        archive.delete("/WEB-INF/conf/restcomm.xml");
        archive.addAsWebInfResource("restcomm.xml", "conf/restcomm.xml");
        archive.delete("/WEB-INF/data/hsql/restcomm.script");
        archive.addAsWebInfResource("restcomm.script", "data/hsql/restcomm.script");
        logger.info("Packaged Test App");
        return archive;
    }
}