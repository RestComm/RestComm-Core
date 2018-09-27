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
package org.restcomm.connect.dao.common;

import java.util.HashMap;
import java.util.Map;

/**
 * Class to assist with Sorting facilities of the API
 */
public class Sorting {
    public static final String SORT_BY_KEY = "sort-by";
    public static final String SORT_DIRECTION_KEY = "sort-direction";

    /**
     * Sorting direction for various API Queries
     */
    public enum Direction {
        ASC,
        DESC,
    }

    /**
     * Parse the sorting part of the URI and return sortBy and sortDirection counterparts
     * @param url  String representing the sorting part of the URI, which is of the form SortBy=<field>:<direction>', for example: '?SortBy=date_created:asc'
     * @return a map containing sortBy and sortDirection
     * @throws Exception if there is a parsing error
     */
    public static Map<String, String> parseUrl(String url) throws Exception {
        final String[] values = url.split(":", 2);
        HashMap<String, String> sortParameters = new HashMap<String, String>();
        sortParameters.put(SORT_BY_KEY, values[0]);
        if (values.length > 1) {
            sortParameters.put(SORT_DIRECTION_KEY, values[1]);
            if (sortParameters.get(SORT_BY_KEY).isEmpty()) {
                throw new Exception("Error parsing the SortBy parameter: missing field to sort by");
            }
            if (!sortParameters.get(SORT_DIRECTION_KEY).equalsIgnoreCase(Direction.ASC.name()) && !sortParameters.get(SORT_DIRECTION_KEY).equalsIgnoreCase(Direction.DESC.name())) {
                throw new Exception("Error parsing the SortBy parameter: sort direction needs to be either " + Direction.ASC + " or " + Direction.DESC);
            }
        }
        else if (values.length == 1) {
            // Default to ascending if only the sorting field has been passed without direction
            sortParameters.put(SORT_DIRECTION_KEY, Direction.ASC.name());
        }
        return sortParameters;
    }

}
