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

package org.restcomm.connect.http.client.rcmlserver.resolver;

import junit.framework.Assert;
import org.junit.Test;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * @author otsakir@gmail.com - Orestis Tsakiridis
 */
public class RcmlserverResolverTest {
    @Test
    public void testResolving() throws URISyntaxException {
        RcmlserverResolver resolver = RcmlserverResolver.getInstance("http://rvdserver.org","/visual-designer/services/", true);
        URI uri = resolver.resolveRelative(new URI("/visual-designer/services/apps/AP5f58b0baf6e14001b6eec02295fab05a/controller"));
        // relative urls to actual RVD applications should get prefixed
        Assert.assertEquals("http://rvdserver.org/visual-designer/services/apps/AP5f58b0baf6e14001b6eec02295fab05a/controller", uri.toString());
        // absolute urls should not be touched
        uri = resolver.resolveRelative(new URI("http://externalserver/AP5f58b0baf6e14001b6eec02295fab05a/controller"));
        Assert.assertEquals("http://externalserver/AP5f58b0baf6e14001b6eec02295fab05a/controller", uri.toString());
        // relative urls pointing to other (non-rvd) apps
        uri = resolver.resolveRelative(new URI("/restcomm/demos/hellp-play.xml"));
        Assert.assertEquals("/restcomm/demos/hellp-play.xml", uri.toString());
        // make sure that RVD path can vary. Assume it's  '/new-rvd' now
        resolver = RcmlserverResolver.getInstance("http://rvdserver.org","/new-rvd/services/", true);
        uri = resolver.resolveRelative(new URI("/new-rvd/myapp.xml"));
        Assert.assertEquals("http://rvdserver.org/new-rvd/myapp.xml", uri.toString());
        // if rcmlserver.baseUrl is null, no resolving should occur
        resolver = RcmlserverResolver.getInstance(null,"/visual-designer/services", true);
        uri = resolver.resolveRelative(new URI("/visual-designer/services/apps/xxxx"));
        Assert.assertEquals("/visual-designer/services/apps/xxxx", uri.toString());
        // if rcmlserver.apiPath is null or empty, no resolving should occur
        resolver = RcmlserverResolver.getInstance("http://rvdotsakir.org","", true);
        uri = resolver.resolveRelative(new URI("/visual-designer/services/apps/xxxx"));
        Assert.assertEquals("/visual-designer/services/apps/xxxx", uri.toString());
        // all nulls
        resolver = RcmlserverResolver.getInstance(null,null, true);
        uri = resolver.resolveRelative(new URI("/visual-designer/services/apps/xxxx"));
        Assert.assertEquals("/visual-designer/services/apps/xxxx", uri.toString());
    }
}
