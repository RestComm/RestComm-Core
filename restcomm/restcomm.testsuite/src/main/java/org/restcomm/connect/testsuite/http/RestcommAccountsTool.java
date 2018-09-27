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

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import java.net.URI;
import java.util.logging.Logger;

/**
 * @author <a href="mailto:gvagenas@gmail.com">gvagenas</a>
 * @author <a href="mailto:lyhungthinh@gmail.com">Thinh Ly</a>
 */
public class RestcommAccountsTool {

    private static Logger logger = Logger.getLogger(RestcommAccountsTool.class.getName());

    private static RestcommAccountsTool instance;
    private static String accountsUrl;

    private RestcommAccountsTool() {

    }

    public static RestcommAccountsTool getInstance() {
        if (instance == null) {
            instance = new RestcommAccountsTool();
        }

        return instance;
    }

    private String getAccountsUrl(String deploymentUrl) {
        return getAccountsUrl(deploymentUrl, false);
    }

    private String getAccountsUrl(String deploymentUrl, Boolean xml) {
//        if (accountsUrl == null) {
        if (deploymentUrl.endsWith("/")) {
            deploymentUrl = deploymentUrl.substring(0, deploymentUrl.length() - 1);
        }
        if (xml) {
            accountsUrl = deploymentUrl + "/2012-04-24/Accounts";
        } else {
            accountsUrl = deploymentUrl + "/2012-04-24/Accounts.json";
        }
//        }

        return accountsUrl;
    }

    public void removeAccount(String deploymentUrl, String adminUsername, String adminAuthToken, String accountSid) {
        Client jerseyClient = Client.create();
        jerseyClient.addFilter(new HTTPBasicAuthFilter(adminUsername, adminAuthToken));

        String url = getAccountsUrl(deploymentUrl, true) + "/" + accountSid;

        WebResource webResource = jerseyClient.resource(url);
        webResource.accept(MediaType.APPLICATION_JSON).delete();
    }

    public JsonObject updateAccount(String deploymentUrl, String adminUsername, String adminAuthToken, String accountSid, String friendlyName, String password, String authToken, String role, String status) {
        JsonParser parser = new JsonParser();
        JsonObject jsonResponse = null;
        try {
            ClientResponse clientResponse = updateAccountResponse(deploymentUrl, adminUsername, adminAuthToken, accountSid, friendlyName, password, authToken, role, status);
            jsonResponse = parser.parse(clientResponse.getEntity(String.class)).getAsJsonObject();
        } catch (Exception e) {
            logger.info("Exception: " + e);
        }
        return jsonResponse;
    }

    public ClientResponse updateAccountResponse(String deploymentUrl, String adminUsername, String adminAuthToken, String accountSid, String friendlyName, String password, String authToken, String role, String status) {
        Client jerseyClient = Client.create();
        jerseyClient.addFilter(new HTTPBasicAuthFilter(adminUsername, adminAuthToken));

        String url = getAccountsUrl(deploymentUrl, false) + "/" + accountSid;

        WebResource webResource = jerseyClient.resource(url);

        // FriendlyName, status, password and auth_token are currently updated in AccountsEndpoint. Role remains to be added
        MultivaluedMap<String, String> params = new MultivaluedMapImpl();
        if (friendlyName != null) {
            params.add("FriendlyName", friendlyName);
        }
        if (password != null) {
            params.add("Password", password);
        }
        if (authToken != null) {
            params.add("Auth_Token", authToken);
        }
        if (role != null) {
            params.add("Role", role);
        }
        if (status != null) {
            params.add("Status", status);
        }

        ClientResponse response = webResource.accept(MediaType.APPLICATION_JSON).post(ClientResponse.class, params);
        return response;
    }

    public JsonObject migrateAccount(String deploymentUrl, String adminUsername, String adminAuthToken,
            String accountSid, String newOrganizationSid) {
        JsonParser parser = new JsonParser();
        JsonObject jsonResponse = null;

        Client jerseyClient = Client.create();
        jerseyClient.addFilter(new HTTPBasicAuthFilter(adminUsername, adminAuthToken));

        String url = getAccountsUrl(deploymentUrl, false) + "/migrate/" + accountSid;

        WebResource webResource = jerseyClient.resource(url);

        // FriendlyName, status, password and auth_token are currently updated in AccountsEndpoint. Role remains to be added
        MultivaluedMap<String, String> params = new MultivaluedMapImpl();
        if (newOrganizationSid != null) {
            params.add("Organization", newOrganizationSid);
        }

        ClientResponse response = webResource.accept(MediaType.APPLICATION_JSON).post(ClientResponse.class, params);
        if (response.getStatus() == 200) {
            jsonResponse = parser.parse(response.getEntity(String.class)).getAsJsonObject();
        }

        return jsonResponse;
    }

    public JsonObject createAccount(String deploymentUrl, String adminUsername, String adminAuthToken, String emailAddress,
            String password) {
        return createAccount(deploymentUrl, adminUsername, adminAuthToken, emailAddress, password, null);
    }

    public JsonObject createAccount(String deploymentUrl, String adminUsername, String adminAuthToken, String emailAddress,
            String password, String friendlyName) {

        JsonParser parser = new JsonParser();
        JsonObject jsonResponse = null;
        try {
            ClientResponse clientResponse = createAccountResponse(deploymentUrl, adminUsername, adminAuthToken, emailAddress, password, friendlyName, null);
            jsonResponse = parser.parse(clientResponse.getEntity(String.class)).getAsJsonObject();
        } catch (Exception e) {
            logger.info("Exception: " + e);
        }
        return jsonResponse;
    }

    public ClientResponse createAccountResponse(String deploymentUrl, String operatorUsername, String operatorAuthtoken, String emailAddress,
            String password) {
        return createAccountResponse(deploymentUrl, operatorUsername, operatorAuthtoken, emailAddress, password, null, null);
    }

    public ClientResponse createAccountResponse(String deploymentUrl, String operatorUsername, String operatorAuthtoken, String emailAddress,
            String password, String friendlyName, String organizationSid) {
        Client jerseyClient = Client.create();
        jerseyClient.addFilter(new HTTPBasicAuthFilter(operatorUsername, operatorAuthtoken));

        String url = getAccountsUrl(deploymentUrl);

        WebResource webResource = jerseyClient.resource(url);

        MultivaluedMap<String, String> params = new MultivaluedMapImpl();
        params.add("EmailAddress", emailAddress);
        params.add("Password", password);
        params.add("Role", "Administartor");
        if (friendlyName != null) {
            params.add("FriendlyName", friendlyName);
        }
        if (organizationSid != null) {
            params.add("OrganizationSid", organizationSid);
        }

        ClientResponse response = webResource.accept(MediaType.APPLICATION_JSON).post(ClientResponse.class, params);
        return response;
    }

    public JsonObject getAccount(String deploymentUrl, String adminUsername, String adminAuthToken, String username)
            throws UniformInterfaceException {
        Client jerseyClient = Client.create();
        jerseyClient.addFilter(new HTTPBasicAuthFilter(adminUsername, adminAuthToken));

        WebResource webResource = jerseyClient.resource(URI.create(getAccountsUrl(deploymentUrl)));

        String response = webResource.path(username).get(String.class);
        JsonParser parser = new JsonParser();
        JsonObject jsonResponse = parser.parse(response).getAsJsonObject();

        return jsonResponse;
    }

    /**
     *
     * @param deploymentUrl
     * @param username
     * @param authtoken
     * @param accountSid
     * @return an account response so that the invoker can make decisions on the
     * status code etc.
     */
    public ClientResponse getAccountResponse(String deploymentUrl, String username, String authtoken, String accountSid) {

        Client jerseyClient = Client.create();
        jerseyClient.addFilter(new HTTPBasicAuthFilter(username, authtoken));
        WebResource webResource = jerseyClient.resource(getAccountsUrl(deploymentUrl));
        ClientResponse response = webResource.path(accountSid).get(ClientResponse.class);
        return response;
    }

    public ClientResponse getAccountsResponse(String deploymentUrl, String username, String authtoken) {
        Client jerseyClient = Client.create();
        jerseyClient.addFilter(new HTTPBasicAuthFilter(username, authtoken));
        WebResource webResource = jerseyClient.resource(getAccountsUrl(deploymentUrl));
        ClientResponse response = webResource.get(ClientResponse.class);
        return response;
    }

    public ClientResponse removeAccountResponse(String deploymentUrl, String operatingUsername, String operatingAuthToken, String removedAccountSid) {
        Client jerseyClient = Client.create();
        jerseyClient.addFilter(new HTTPBasicAuthFilter(operatingUsername, operatingAuthToken));
        WebResource webResource = jerseyClient.resource(getAccountsUrl(deploymentUrl));
        ClientResponse response = webResource.path(removedAccountSid).delete(ClientResponse.class);
        return response;
    }

    /**
     * @param deploymentUrl
     * @param username
     * @param authtoken
     * @param organizationSid
     * @param domainName
     * @return
     */
    public ClientResponse getAccountsWithFilterClientResponse(String deploymentUrl, String username, String authtoken, String organizationSid, String domainName) {
        WebResource webResource = prepareAccountListWebResource(deploymentUrl, username, authtoken);

        ClientResponse response = webResource.queryParams(prepareAccountListFilter(organizationSid, domainName))
                .accept(MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML)
                .get(ClientResponse.class);
        return response;
    }

    /**
     * @param deploymentUrl
     * @param username
     * @param authtoken
     * @param organizationSid
     * @param domainName
     * @return JsonArray
     */
    public JsonArray getAccountsWithFilterResponse(String deploymentUrl, String username, String authtoken, String organizationSid, String domainName) {
        WebResource webResource = prepareAccountListWebResource(deploymentUrl, username, authtoken);

        String response = webResource.queryParams(prepareAccountListFilter(organizationSid, domainName))
                .accept(MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML)
                .get(String.class);
        JsonElement jsonElement = new JsonParser().parse(response);
        return jsonElement.getAsJsonArray();
    }

    /**
     * @param deploymentUrl
     * @param username
     * @param authtoken
     * @return
     */
    private WebResource prepareAccountListWebResource(String deploymentUrl, String username, String authtoken) {
        Client jerseyClient = Client.create();
        jerseyClient.addFilter(new HTTPBasicAuthFilter(username, authtoken));
        WebResource webResource = jerseyClient.resource(getAccountsUrl(deploymentUrl));
        return webResource;
    }

    /**
     * @param organizationSid
     * @param domainName
     * @return
     */
    private MultivaluedMap<String, String> prepareAccountListFilter(String organizationSid, String domainName) {
        MultivaluedMap<String, String> params = new MultivaluedMapImpl();
        if (organizationSid != null && !(organizationSid.trim().isEmpty())) {
            params.add("OrganizationSid", organizationSid);
        }
        if (domainName != null && !(domainName.trim().isEmpty())) {
            params.add("DomainName", domainName);
        }

        return params;
    }
}
