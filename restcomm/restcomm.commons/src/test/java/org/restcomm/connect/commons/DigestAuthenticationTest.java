/*
 *  TeleStax, Open Source Cloud Communications
 *  Copyright 2011-2018, Telestax Inc and individual contributors
 *  by the @authors tag.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  under the terms of the GNU Affero General Public License as
 *  published by the Free Software Foundation; either version 3 of
 *  the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>
 */

package org.restcomm.connect.commons;

import org.junit.Test;
import org.restcomm.connect.commons.dao.Sid;
import org.restcomm.connect.commons.util.DigestAuthentication;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DigestAuthenticationTest {

    private String client = "alice0000000";
    private String domain = "org0000000.restcomm.com";
    private String password = "1234";
    private String proxyAuthHeader = "Digest username=\"alice0000000\",realm=\"org0000000.restcomm.com\",cnonce=\"6b8b4567\",nc=00000001,qop=auth,uri=\"sip:172.31.45.30:5080\",nonce=\"61343361383534392d633237372d343\",response=\"bc322276e42a123c53c2ed6f53d5e7c7\",algorithm=MD5";

    @Test
    public void testAuth(){
        String hashedPass = DigestAuthentication.HA1(client, domain, password, "MD5");

        assertEquals("9b11a2924d0881aca84f9db97f834d99", hashedPass);

        assertTrue(permitted(proxyAuthHeader, "INVITE", hashedPass));

    }

    static boolean permitted(final String authorization, final String method, String clientPassword) {
        final Map<String, String> map = authHeaderToMap(authorization);
        String user = map.get("username");
        final String algorithm = map.get("algorithm");
        final String realm = map.get("realm");
        final String uri = map.get("uri");
        final String nonce = map.get("nonce");
        final String nc = map.get("nc");
        final String cnonce = map.get("cnonce");
        final String qop = map.get("qop");
        final String response = map.get("response");
        final String password = clientPassword;
        final String result = DigestAuthentication.response(algorithm, user, realm, password, "MD5", nonce, nc, cnonce,
                method, uri, null, qop);
        return result.equals(response);
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

}
