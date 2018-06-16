package org.restcomm.connect.testsuite.http;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.UniformInterfaceException;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.filter.HTTPBasicAuthFilter;
import com.sun.jersey.core.util.MultivaluedMapImpl;
import java.util.logging.Logger;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;

/**
 * @author maria farooq
 */
public class RestcommOrganizationsTool {

    private static Logger logger = Logger.getLogger(RestcommOrganizationsTool.class.getName());

    private static RestcommOrganizationsTool instance;
    private static String organizationsUrl;

    private RestcommOrganizationsTool() {

    }

    public static RestcommOrganizationsTool getInstance() {
        if (instance == null) {
            instance = new RestcommOrganizationsTool();
        }

        return instance;
    }

    private String getOrganizationsUrl(String deploymentUrl) {
        return getOrganizationsUrl(deploymentUrl, false);
    }

    private String getOrganizationsUrl(String deploymentUrl, Boolean xml) {
        if (deploymentUrl.endsWith("/")) {
            deploymentUrl = deploymentUrl.substring(0, deploymentUrl.length() - 1);
        }
        if (xml) {
            organizationsUrl = deploymentUrl + "/2012-04-24/Organizations";
        } else {
            organizationsUrl = deploymentUrl + "/2012-04-24/Organizations.json";
        }
        return organizationsUrl;
    }

    public JsonObject getOrganization(String deploymentUrl, String adminUsername, String adminAuthToken, String organizationSid)
            throws UniformInterfaceException {
        Client jerseyClient = Client.create();
        jerseyClient.addFilter(new HTTPBasicAuthFilter(adminUsername, adminAuthToken));

        WebResource webResource = jerseyClient.resource(getOrganizationsUrl(deploymentUrl));

        String response = webResource.path(organizationSid).get(String.class);
        JsonParser parser = new JsonParser();
        JsonObject jsonResponse = parser.parse(response).getAsJsonObject();

        return jsonResponse;
    }

    public JsonArray getOrganizationList(String deploymentUrl, String adminUsername, String adminAuthToken, String status)
            throws UniformInterfaceException {
        Client jerseyClient = Client.create();
        jerseyClient.addFilter(new HTTPBasicAuthFilter(adminUsername, adminAuthToken));

        WebResource webResource = jerseyClient.resource(getOrganizationsUrl(deploymentUrl));
        String response;
        if (status != null) {
            MultivaluedMap<String, String> params = new MultivaluedMapImpl();
            params.add("Status", String.valueOf(status));

            response = webResource.queryParams(params).accept(MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML)
                    .get(String.class);
        } else {
            response = webResource.accept(MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML).get(String.class);
        }
        JsonParser parser = new JsonParser();
        JsonArray jsonArray = null;
        try {
            JsonElement jsonElement = parser.parse(response);
            if (jsonElement.isJsonArray()) {
                jsonArray = jsonElement.getAsJsonArray();
            } else {
                logger.info("JsonElement: " + jsonElement.toString());
            }
        } catch (Exception e) {
            logger.info("Exception during JSON response parsing, exception: " + e);
            logger.info("JSON response: " + response);
        }

        return jsonArray;
    }

    public ClientResponse getOrganizationResponse(String deploymentUrl, String username, String authtoken, String organizationSid) {
        Client jerseyClient = Client.create();
        jerseyClient.addFilter(new HTTPBasicAuthFilter(username, authtoken));
        WebResource webResource = jerseyClient.resource(getOrganizationsUrl(deploymentUrl));
        ClientResponse response = webResource.path(organizationSid).get(ClientResponse.class);
        return response;
    }

    public ClientResponse getOrganizationsResponse(String deploymentUrl, String username, String authtoken) {
        Client jerseyClient = Client.create();
        jerseyClient.addFilter(new HTTPBasicAuthFilter(username, authtoken));
        WebResource webResource = jerseyClient.resource(getOrganizationsUrl(deploymentUrl));
        ClientResponse response = webResource.get(ClientResponse.class);
        return response;
    }

    public JsonObject createOrganization(String deploymentUrl, String username, String adminAuthToken, String domainName) {

        JsonParser parser = new JsonParser();
        JsonObject jsonResponse = null;
        try {
            ClientResponse clientResponse = createOrganizationResponse(deploymentUrl, username, adminAuthToken, domainName);
            jsonResponse = parser.parse(clientResponse.getEntity(String.class)).getAsJsonObject();
        } catch (Exception e) {
            logger.info("Exception: " + e);
        }
        return jsonResponse;
    }

    public ClientResponse createOrganizationResponse(String deploymentUrl, String operatorUsername, String operatorAuthtoken, String domainName) {
        Client jerseyClient = Client.create();
        jerseyClient.addFilter(new HTTPBasicAuthFilter(operatorUsername, operatorAuthtoken));

        String url = getOrganizationsUrl(deploymentUrl) + "/" + domainName;

        WebResource webResource = jerseyClient.resource(url);
        MultivaluedMap<String, String> params = new MultivaluedMapImpl();
        ClientResponse response = webResource.accept(MediaType.APPLICATION_JSON).put(ClientResponse.class, params);
        return response;
    }

    public JsonObject migrateClientsOfOrganization(String deploymentUrl, String operatorUsername, String operatorAuthToken, String organizationSid) {
        Client jerseyClient = Client.create();
        jerseyClient.addFilter(new HTTPBasicAuthFilter(operatorUsername, operatorAuthToken));

        WebResource webResource = jerseyClient.resource(getOrganizationsUrl(deploymentUrl));

        WebResource migrateWebResource = webResource.path(organizationSid).path("Migrate");

        String response = migrateWebResource.put(String.class);
        JsonParser parser = new JsonParser();
        JsonObject jsonResponse = parser.parse(response).getAsJsonObject();

        return jsonResponse;
    }
}
