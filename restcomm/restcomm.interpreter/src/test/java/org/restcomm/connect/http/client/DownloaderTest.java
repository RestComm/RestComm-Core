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
package org.restcomm.connect.http.client;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.testkit.JavaTestKit;
import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import com.github.tomakehurst.wiremock.junit.WireMockRule;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.concurrent.TimeUnit;
import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.http.conn.ConnectionPoolTimeoutException;

import org.junit.After;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.restcomm.connect.commons.configuration.RestcommConfiguration;

import scala.concurrent.duration.FiniteDuration;

/**
 * @author quintana.thomas@gmail.com (Thomas Quintana)
 */
public final class DownloaderTest {

    private ActorSystem system;
    private ActorRef downloader;

    private static int MOCK_PORT = 8099;
    //use localhost instead of 127.0.0.1 to match the route rule
    private static String PATH = "http://localhost:" + MOCK_PORT + "/";

    @Rule
    public WireMockRule wireMockRule = new WireMockRule(wireMockConfig().bindAddress("127.0.0.1").port(MOCK_PORT));

    public DownloaderTest() {
        super();
    }

    @Before
    public void before() throws Exception {
        URL url = this.getClass().getResource("/restcomm_downloader.xml");
        Configuration xml = new XMLConfiguration(url);
        RestcommConfiguration.createOnce(xml);
        system = ActorSystem.create();
        downloader = system.actorOf(new Props(Downloader.class));
    }

    @After
    public void after() throws Exception {
        system.shutdown();
        wireMockRule.resetRequests();
    }

    @Test
    public void testGet() throws URISyntaxException, IOException {
        stubFor(get(urlMatching("/testGet")).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("expectedBody")));
        new JavaTestKit(system) {
            {
                final ActorRef observer = getRef();
                final URI uri = URI.create(PATH + "testGet");
                final String method = "GET";
                final HttpRequestDescriptor request = new HttpRequestDescriptor(uri, method);
                downloader.tell(request, observer);
                final FiniteDuration timeout = FiniteDuration.create(30, TimeUnit.SECONDS);
                final DownloaderResponse response = expectMsgClass(timeout, DownloaderResponse.class);
                assertTrue(response.succeeded());
                final HttpResponseDescriptor descriptor = response.get();
                System.out.println("Result: " + descriptor.getContentAsString());
                assertTrue(descriptor.getContentAsString().contains("expectedBody"));
            }
        };
    }

    @Test
    @Ignore
    public void testPost() throws URISyntaxException, IOException {
        stubFor(post(urlMatching("/testPost")).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("expectedBody")));
        new JavaTestKit(system) {
            {
                final ActorRef observer = getRef();
                final URI uri = URI.create(PATH + "testPost");
                final String method = "POST";
                final HttpRequestDescriptor request = new HttpRequestDescriptor(uri, method);
                downloader.tell(request, observer);
                final FiniteDuration timeout = FiniteDuration.create(30, TimeUnit.SECONDS);
                final DownloaderResponse response = expectMsgClass(timeout, DownloaderResponse.class);
                assertTrue(response.succeeded());
                final HttpResponseDescriptor descriptor = response.get();
                assertTrue(descriptor.getContentAsString().contains("expectedBody"));
            }
        };
    }

    @Test
    public void testNotFound() throws URISyntaxException, IOException {
        stubFor(get(urlMatching("/testNotFound")).willReturn(aResponse()
                .withStatus(404)
                .withHeader("Content-Type", "application/json")
                .withBody("{}")));
        new JavaTestKit(system) {
            {
                final ActorRef observer = getRef();
                final URI uri = URI.create(PATH + "testNotFound");
                final String method = "GET";
                final HttpRequestDescriptor request = new HttpRequestDescriptor(uri, method);
                downloader.tell(request, observer);
                final FiniteDuration timeout = FiniteDuration.create(30, TimeUnit.SECONDS);
                final DownloaderResponse response = expectMsgClass(timeout, DownloaderResponse.class);
                assertTrue(response.succeeded());
                final HttpResponseDescriptor descriptor = response.get();
                assertEquals(404, descriptor.getStatusCode());
            }
        };
    }


    /**
     * configuration comes from restcomm.xml file
     *
     * @throws Exception
     */
    @Test()
    public void testDownloaderWithRouteconfiguration() throws Exception {
        stubFor(get(urlMatching("/testDownloaderWithRouteconfiguration")).willReturn(aResponse()
                .withFixedDelay(5000 * 2)//delay will cause read timeout to happen
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("{}")));
        new JavaTestKit(system) {
            {
                int connsPerRoute = 5;
                final URI uri = URI.create(PATH + "testDownloaderWithRouteconfiguration");
                final String method = "GET";
                final HttpRequestDescriptor request = new HttpRequestDescriptor(uri, method);
                final ActorRef observer = getRef();

                for (int i =0; i < connsPerRoute; i ++)
                {
                    downloader = system.actorOf(new Props(Downloader.class));
                    downloader.tell(request, observer);
                }
                Thread.sleep(1000);
                downloader = system.actorOf(new Props(Downloader.class));
                downloader.tell(request, observer);
                final FiniteDuration timeout = FiniteDuration.create(30, TimeUnit.SECONDS);
                final DownloaderResponse response = expectMsgClass(timeout, DownloaderResponse.class);
                assertFalse(response.succeeded());
                //JavaTestKit dont allow to throw exception and use "expected"
                assertEquals(ConnectionPoolTimeoutException.class, response.cause().getClass());
            }
        };
    }
}
