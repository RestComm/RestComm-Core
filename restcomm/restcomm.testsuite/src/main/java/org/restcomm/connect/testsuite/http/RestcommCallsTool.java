package org.restcomm.connect.testsuite.http;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.UniformInterfaceException;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.api.client.filter.HTTPBasicAuthFilter;
import com.sun.jersey.core.util.MultivaluedMapImpl;
import com.thoughtworks.xstream.XStream;
import java.util.Map;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import org.apache.log4j.Logger;
import org.restcomm.connect.dao.entities.CallDetailRecordList;

/**
 * @author <a href="mailto:gvagenas@gmail.com">gvagenas</a>
 */
public class RestcommCallsTool {

    private static RestcommCallsTool instance;
    private static String accountsUrl;
    private static Logger logger = Logger.getLogger(RestcommCallsTool.class);

    private RestcommCallsTool() {
    }

    public static RestcommCallsTool getInstance() {
        if (instance == null) {
            instance = new RestcommCallsTool();
        }

        return instance;
    }

    private String getAccountsUrl(String deploymentUrl, String username, Boolean json) {
        if (deploymentUrl.endsWith("/")) {
            deploymentUrl = deploymentUrl.substring(0, deploymentUrl.length() - 1);
        }

        accountsUrl = deploymentUrl + "/2012-04-24/Accounts/" + username + "/Calls" + ((json) ? ".json" : "");

        return accountsUrl;
    }

    private String getRecordingsUrl(String deploymentUrl, String username, Boolean json) {
        if (accountsUrl == null) {
            if (deploymentUrl.endsWith("/")) {
                deploymentUrl = deploymentUrl.substring(0, deploymentUrl.length() - 1);
            }

            accountsUrl = deploymentUrl + "/2012-04-24/Accounts/" + username + "/Recordings.json";
        }

        return accountsUrl;
    }

    private String getCallRecordingsUrl(String deploymentUrl, String username, String callSid, Boolean json) {
        if (deploymentUrl.endsWith("/")) {
            deploymentUrl = deploymentUrl.substring(0, deploymentUrl.length() - 1);
        }

        String url = deploymentUrl + "/2012-04-24/Accounts/" + username + "/Calls/" + callSid + "/Recordings" + ((json) ? ".json" : "");
        return url;
    }

    private String getGateWayUrl(String deploymentUrl, String username) {
        if (deploymentUrl.endsWith("/")) {
            deploymentUrl = deploymentUrl.substring(0, deploymentUrl.length() - 1);
        }

        String url = deploymentUrl + "/2012-04-24/Accounts/" + username + "/Management/Gateways";
        return url;
    }

    public JsonArray getRecordings(String deploymentUrl, String username, String authToken) {
        Client jerseyClient = Client.create();
        jerseyClient.addFilter(new HTTPBasicAuthFilter(username, authToken));

        String url = getRecordingsUrl(deploymentUrl, username, true);

        WebResource webResource = jerseyClient.resource(url);

        String response = null;
        response = webResource.accept(MediaType.APPLICATION_JSON).get(String.class);
//        response = response.replaceAll("\\[", "").replaceAll("]", "").trim();
        JsonArray jsonArray = null;
        try {
            JsonParser parser = new JsonParser();
            JsonObject jsonObject = parser.parse(response).getAsJsonObject();
            jsonArray = jsonObject.get("recordings").getAsJsonArray();
        } catch (Exception e) {
            logger.info("Exception during getRecordings for url: " + url + " exception: " + e);
            logger.info("Response object: " + response);
        }
        return jsonArray;
    }

    public JsonObject getCalls(String deploymentUrl, String username, String authToken) {
        return (JsonObject) getCalls(deploymentUrl, username, authToken, null, null, null, true);
    }

    public JsonObject getCalls(String deploymentUrl, String username, String authToken, Integer page, Integer pageSize, String sortingParameters,
            Boolean json) throws UniformInterfaceException {

        Client jerseyClient = Client.create();
        jerseyClient.addFilter(new HTTPBasicAuthFilter(username, authToken));

        String url = getAccountsUrl(deploymentUrl, username, json);

        WebResource webResource = jerseyClient.resource(url);

        String response = null;

        MultivaluedMap<String, String> params = new MultivaluedMapImpl();

        if (sortingParameters != null) {
            params.add("SortBy", sortingParameters);
        }

        if (page != null || pageSize != null) {
            if (page != null) {
                params.add("Page", String.valueOf(page));
            }
            if (pageSize != null) {
                params.add("PageSize", String.valueOf(pageSize));
            }
        }

        if (!params.isEmpty()) {
            response = webResource.queryParams(params).accept(MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML)
                    .get(String.class);
        } else {
            response = webResource.accept(MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML).get(String.class);
        }

        JsonParser parser = new JsonParser();

        if (json) {
            JsonObject jsonObject = null;
            try {
                JsonElement jsonElement = parser.parse(response);
                if (jsonElement.isJsonObject()) {
                    jsonObject = jsonElement.getAsJsonObject();
                } else {
                    logger.info("JsonElement: " + jsonElement.toString());
                }
            } catch (Exception e) {
                logger.info("Exception during JSON response parsing, exception: " + e);
                logger.info("JSON response: " + response);
            }
            return jsonObject;
        } else {
            XStream xstream = new XStream();
            xstream.alias("cdrlist", CallDetailRecordList.class);
            JsonObject jsonObject = parser.parse(xstream.toXML(response)).getAsJsonObject();
            return jsonObject;
        }

    }

    /**
     * getCall from same account
     *
     * @param deploymentUrl
     * @param username
     * @param authToken
     * @param sid
     * @return
     */
    public JsonObject getCall(String deploymentUrl, String username, String authToken, String sid) {
        return getCall(deploymentUrl, username, authToken, username, sid);
    }

    /**
     * getCall from another account's resource
     * https://github.com/RestComm/Restcomm-Connect/issues/1939
     *
     * @param deploymentUrl
     * @param username
     * @param authToken
     * @param resourceAccountSid
     * @param sid
     * @return
     */
    public JsonObject getCall(String deploymentUrl, String username, String authToken, String resourceAccountSid, String sid) {

        Client jerseyClient = Client.create();
        jerseyClient.addFilter(new HTTPBasicAuthFilter(username, authToken));

        String url = getAccountsUrl(deploymentUrl, resourceAccountSid, false);

        WebResource webResource = jerseyClient.resource(url);

        String response = null;

        webResource = webResource.path(String.valueOf(sid) + ".json");
        logger.info("The URI to sent: " + webResource.getURI());

        response = webResource.accept(MediaType.APPLICATION_JSON, MediaType.APPLICATION_XML)
                .get(String.class);

        JsonParser parser = new JsonParser();
        JsonObject jsonObject = parser.parse(response).getAsJsonObject();

        return jsonObject;

    }

    public JsonObject getCallsUsingFilter(String deploymentUrl, String username, String authToken, Map<String, String> filters) {

        Client jerseyClient = Client.create();
        jerseyClient.addFilter(new HTTPBasicAuthFilter(username, authToken));

        String url = getAccountsUrl(deploymentUrl, username, true);

        WebResource webResource = jerseyClient.resource(url);

        MultivaluedMap<String, String> params = new MultivaluedMapImpl();

        for (String filterName : filters.keySet()) {
            String filterData = filters.get(filterName);
            params.add(filterName, filterData);
        }
        webResource = webResource.queryParams(params);
        String response = webResource.accept(MediaType.APPLICATION_JSON).get(String.class);
        JsonParser parser = new JsonParser();
        JsonObject jsonObject = parser.parse(response).getAsJsonObject();

        return jsonObject;
    }

    public JsonElement createCall(String deploymentUrl, String username, String authToken, String from, String to, String rcmlUrl) {
        return createCall(deploymentUrl, username, authToken, from, to, rcmlUrl, null, null, null, null);
    }

    public JsonElement createCall(String deploymentUrl, String username, String authToken, String from, String to, String rcmlUrl, String timeout) {
        return createCall(deploymentUrl, username, authToken, from, to, rcmlUrl, null, null, null, timeout);
    }

    public JsonElement createCall(String deploymentUrl, String username, String authToken, String from, String to, String rcmlUrl,
            final String statusCallback, final String statusCallbackMethod, final String statusCallbackEvent) {
        return createCall(deploymentUrl, username, authToken, from, to, rcmlUrl, statusCallback, statusCallbackMethod, statusCallbackEvent, null);
    }

    public JsonElement createCall(String deploymentUrl, String username, String authToken, String from, String to, String rcmlUrl,
            final String statusCallback, final String statusCallbackMethod, final String statusCallbackEvent, final String timeout) {

        Client jerseyClient = Client.create();
        jerseyClient.addFilter(new HTTPBasicAuthFilter(username, authToken));

        String url = getAccountsUrl(deploymentUrl, username, true);

        WebResource webResource = jerseyClient.resource(url);

        MultivaluedMap<String, String> params = new MultivaluedMapImpl();
        params.add("From", from);
        params.add("To", to);
        params.add("Url", rcmlUrl);

        if (statusCallback != null) {
            params.add("StatusCallback", statusCallback);
        }
        if (statusCallbackMethod != null) {
            params.add("StatusCallbackMethod", statusCallbackMethod);
        }
        if (statusCallbackEvent != null) {
            params.add("StatusCallbackEvent", statusCallbackEvent);
        }

        if (timeout != null) {
            params.add("Timeout", timeout);
        }

        // webResource = webResource.queryParams(params);
        String response = webResource.accept(MediaType.APPLICATION_JSON).post(String.class, params);
        JsonParser parser = new JsonParser();
        if (response.startsWith("[")) {
            return parser.parse(response).getAsJsonArray();
        } else {
            return parser.parse(response).getAsJsonObject();
        }
    }

    /**
     * @param deploymentUrl
     * @param username
     * @param authToken
     * @param callSid
     * @param mute
     * @return
     * @throws Exception
     */
    public JsonObject modifyCall(String deploymentUrl, String username, String authToken, String callSid, Boolean mute) throws Exception {
        return modifyCall(deploymentUrl, username, authToken, callSid, null, null, false, mute);
    }

    /**
     * @param deploymentUrl
     * @param username
     * @param authToken
     * @param callSid
     * @param status
     * @param rcmlUrl
     * @return
     * @throws Exception
     */
    public JsonObject modifyCall(String deploymentUrl, String username, String authToken, String callSid, String status,
            String rcmlUrl) throws Exception {
        return modifyCall(deploymentUrl, username, authToken, callSid.trim(), status, rcmlUrl, false, null);
    }

    public JsonObject modifyCall(String deploymentUrl, String username, String authToken, String callSid, String status,
            String rcmlUrl, boolean moveConnectedLeg) throws Exception {
        return modifyCall(deploymentUrl, username, authToken, callSid, status, rcmlUrl, moveConnectedLeg, null);
    }

    /**
     * @param deploymentUrl
     * @param username
     * @param authToken
     * @param callSid
     * @param status
     * @param rcmlUrl
     * @param moveConnectedLeg
     * @param mute
     * @return
     * @throws Exception
     */
    public JsonObject modifyCall(String deploymentUrl, String username, String authToken, String callSid, String status,
            String rcmlUrl, boolean moveConnectedLeg, Boolean mute) throws Exception {

        Client jerseyClient = Client.create();
        jerseyClient.addFilter(new HTTPBasicAuthFilter(username, authToken));

        String url = getAccountsUrl(deploymentUrl, username, true);

        WebResource webResource = jerseyClient.resource(url);

        MultivaluedMap<String, String> params = new MultivaluedMapImpl();
        if (status != null && rcmlUrl != null) {
            throw new Exception(
                    "You can either redirect a call using the \"url\" attribute or terminate it using the \"status\" attribute!");
        }
        if (status != null) {
            params.add("Status", status);
        }
        if (rcmlUrl != null) {
            params.add("Url", rcmlUrl);
        }
        if (moveConnectedLeg) {
            params.add("MoveConnectedCallLeg", "true");
        }
        if (mute != null) {
            if (mute) {
                params.add("Mute", "true");
            } else {
                params.add("Mute", "false");
            }
        }

        JsonObject jsonObject = null;

        try {
            String response = webResource.path(callSid).accept(MediaType.APPLICATION_JSON).post(String.class, params);
            JsonParser parser = new JsonParser();
            jsonObject = parser.parse(response).getAsJsonObject();
        } catch (Exception e) {
            logger.error("Exception : ", e);
            UniformInterfaceException exception = (UniformInterfaceException) e;
            jsonObject = new JsonObject();
            jsonObject.addProperty("Exception", exception.getResponse().getStatus());
        }
        return jsonObject;
    }

    public JsonArray getCallRecordings(String deploymentUrl, String username, String authToken, String callWithRecordingsSid) {
        Client jerseyClient = Client.create();
        jerseyClient.addFilter(new HTTPBasicAuthFilter(username, authToken));

        String url = getCallRecordingsUrl(deploymentUrl, username, callWithRecordingsSid, true);

        WebResource webResource = jerseyClient.resource(url);

        String response = webResource.accept(MediaType.APPLICATION_JSON).get(String.class);
        JsonParser parser = new JsonParser();
        JsonArray jsonArray = parser.parse(response).getAsJsonArray();

        return jsonArray;
    }

    public String setGateWay(String deploymentUrl, String username, String authToken, String friend, String uName, String password, String proxy,
            boolean register, String ttl) {

        Client jerseyClient = Client.create();
        jerseyClient.addFilter(new HTTPBasicAuthFilter(username, authToken));

        String url = getGateWayUrl(deploymentUrl, username);

        WebResource webResource = jerseyClient.resource(url);

        MultivaluedMap<String, String> params = new MultivaluedMapImpl();
        params.add("Register", String.valueOf(register));
        if (friend != null) {
            params.add("FriendlyName", friend);
        }
        if (uName != null) {
            params.add("UserName", uName);
        }
        if (password != null) {
            params.add("Password", password);
        }
        if (proxy != null) {
            params.add("Proxy", proxy);
        }
        if (ttl != null) {
            params.add("TTL", ttl);
        }

        String response = null;
        try {
            response = webResource.accept(MediaType.APPLICATION_JSON).post(String.class, params);
        } catch (Exception e) {
            logger.error("Exception : ", e);
            UniformInterfaceException exception = (UniformInterfaceException) e;
        }
        return response;
    }
}
