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
package org.restcomm.connect.telephony.api.util;

import static javax.servlet.sip.SipServletResponse.SC_PROXY_AUTHENTICATION_REQUIRED;
import static org.restcomm.connect.commons.util.HexadecimalUtils.toHex;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.servlet.sip.SipServletRequest;
import javax.servlet.sip.SipServletResponse;
import javax.servlet.sip.SipURI;

import org.apache.log4j.Logger;
import org.restcomm.connect.dao.ClientsDao;
import org.restcomm.connect.dao.DaoManager;
import org.restcomm.connect.dao.entities.Account;
import org.restcomm.connect.dao.entities.Client;
import org.restcomm.connect.commons.configuration.RestcommConfiguration;
import org.restcomm.connect.commons.dao.Sid;
import org.restcomm.connect.commons.util.DigestAuthentication;

/**
 *
 * Helper class for managing SIP interactions
 *
 *
 * @author ivelin.ivanov@teletax.com
 *
 */
public class CallControlHelper {
    private static Logger logger = Logger.getLogger(CallControlHelper.class);

    public static boolean permitted(final String authorization, final String method, DaoManager daoManager, final Sid organizationSid) {
        final Map<String, String> map = authHeaderToMap(authorization);
        final String user = map.get("username");
        String authHeaderAlgorithm = map.get("algorithm");
        final String realm = map.get("realm");
        final String uri = map.get("uri");
        final String nonce = map.get("nonce");
        final String nc = map.get("nc");
        final String cnonce = map.get("cnonce");
        final String qop = map.get("qop");
        final String response = map.get("response");
        final ClientsDao clients = daoManager.getClientsDao();
        final Client client = clients.getClient(user, organizationSid);

        if (client != null && daoManager.getAccountsDao().getAccount(client.getAccountSid()).getStatus() != Account.Status.ACTIVE) {
            return false;
        }

        if (client != null && Client.ENABLED == client.getStatus()) {
            final String password = client.getPassword();
            final String clientPasswordAlgorithm = client.getPasswordAlgorithm();
            if (authHeaderAlgorithm == null) {
                authHeaderAlgorithm = clientPasswordAlgorithm;
            }
            final String result = DigestAuthentication.response(authHeaderAlgorithm, user, realm, password,clientPasswordAlgorithm, nonce, nc, cnonce,
                    method, uri, null, qop);
            if (logger.isDebugEnabled()) {
                String msg = String.format("Provided response [%s], generated response [%s]", response, result);
                logger.debug(msg);
            }
            return result.equals(response);
        } else {
            if (logger.isDebugEnabled()) {
                String msg = String.format("Authorization check failed, [if(client!=null) evaluates %s] or [if(client.getStatus()==Client.ENABLED) evaluates %s]", client!=null, client != null ? Client.ENABLED == client.getStatus() : "client is null");
                logger.debug(msg);
            }
            return false;
        }
    }

    /**
     * Check if a client is authenticated. If so, return true. Otherwise request authentication and return false;
     * @param request
     * @param storage
     * @param organizationSid
     * @return
     * @throws IOException
     */
    public static boolean checkAuthentication(SipServletRequest request, DaoManager storage, Sid organizationSid) throws IOException {
        // Make sure we force clients to authenticate.
        final String authorization = request.getHeader("Proxy-Authorization");
        final String method = request.getMethod();
        if (authorization == null || !CallControlHelper.permitted(authorization, method, storage, organizationSid)) {
            if (logger.isDebugEnabled()) {
                String msg = String.format("Either authorization header is null [if(authorization==null) evaluates %s], or CallControlHelper.permitted() method failed, will send \"407 Proxy Authentication required\"", authorization==null);
                logger.debug(msg);
            }
            authenticate(request, storage.getOrganizationsDao().getOrganization(organizationSid).getDomainName());
            return false;
        } else {
            return true;
        }
    }

    static void authenticate(final SipServletRequest request, String realm) throws IOException {
        final SipServletResponse response = request.createResponse(SC_PROXY_AUTHENTICATION_REQUIRED);
        final String nonce = nonce();
        String algorithm = RestcommConfiguration.getInstance().getMain().getClientAlgorithm();
        String qop = RestcommConfiguration.getInstance().getMain().getClientQOP();
        final String header = header(nonce, realm, "Digest", algorithm, qop);
        response.addHeader("Proxy-Authenticate", header);
        response.send();
    }

    private static Map<String, String> authHeaderToMap(final String header) {
        final Map<String, String> map = new HashMap<String, String>();
        final int endOfScheme = header.indexOf(" ");
        map.put("scheme", header.substring(0, endOfScheme).trim());
        final String[] tokens = header.substring(endOfScheme + 1).split(",");
        for (final String token : tokens) {
            final String[] values = token.trim().split("=",2); //Issue #935, split only for first occurrence of "="
            map.put(values[0].toLowerCase(), values[1].replace("\"", ""));
        }

        return map;
    }

    static String nonce() {
        final byte[] uuid = UUID.randomUUID().toString().getBytes();
        final char[] hex = toHex(uuid);
        return new String(hex).substring(0, 31);
    }

    static String header(final String nonce, final String realm, final String scheme, String algo, String qop) {
        final StringBuilder buffer = new StringBuilder();
        buffer.append(scheme).append(" ");
        if(!algo.isEmpty()){
            //NB: we dont support "algorithm-sess" yet
            buffer.append("algorithm=\"").append(algo).append("\", ");
            buffer.append("qop=\"").append(qop).append("\", ");
        }
        buffer.append("realm=\"").append(realm).append("\", ");
        buffer.append("nonce=\"").append(nonce).append("\"");
        return buffer.toString();
    }

    /**
     *
     * Extracts the User SIP identity from a request header
     *
     * @param request
     * @param useTo Whether or not to use the To field in the SIP header
     * @return
     */
    public static String getUserSipId(final SipServletRequest request, boolean useTo) {
        final SipURI toUri;
        final String toUser;
        if (useTo) {
            toUri = (SipURI) request.getTo().getURI();
            toUser = toUri.getUser();
        } else {
            toUri = (SipURI) request.getRequestURI();
            toUser = toUri.getUser();
        }
        return toUser;
    }

}
