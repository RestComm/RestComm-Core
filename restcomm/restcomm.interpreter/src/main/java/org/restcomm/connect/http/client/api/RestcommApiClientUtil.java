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
package org.restcomm.connect.http.client.api;

import java.nio.charset.Charset;

import org.apache.commons.codec.binary.Base64;
import org.apache.http.Header;
import org.apache.http.HttpHeaders;
import org.apache.http.message.BasicHeader;
import org.restcomm.connect.commons.dao.Sid;
import org.restcomm.connect.dao.DaoManager;
import org.restcomm.connect.dao.entities.Account;

/**
 * @author maria.farooq
 */
public final class RestcommApiClientUtil {

    public static String getAuthenticationHeader(Sid sid, DaoManager storage) {
        Account requestingAccount = storage.getAccountsDao().getAccount(sid);

        String authenticationHeader = null;
        if(requestingAccount != null) {
            String auth = requestingAccount.getSid() + ":" + requestingAccount.getAuthToken();
            byte[] encodedAuth = Base64.encodeBase64(auth.getBytes(Charset.forName("ISO-8859-1")));
            authenticationHeader = "Basic " + new String(encodedAuth);
        }
        return authenticationHeader;
    }

    public static Header[] getBasicHeaders(Sid sid, DaoManager storage){
        Header authHeader = new BasicHeader(HttpHeaders.AUTHORIZATION, RestcommApiClientUtil.getAuthenticationHeader(sid, storage));
        Header contentTypeHeader = new BasicHeader("Content-type", "application/x-www-form-urlencoded");
        Header acceptHeader = new BasicHeader("Accept", "application/json");
        Header[] headers = {
                authHeader
                , contentTypeHeader
                , acceptHeader
            };
        return headers;
    }
}
