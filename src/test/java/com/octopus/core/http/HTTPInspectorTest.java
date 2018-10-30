package com.octopus.core.http;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.Header;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

import java.net.Proxy;
import java.net.URL;

import static org.junit.Assert.*;

public class HTTPInspectorTest {
    private ClientAndServer mockServer;

    @Before
    public void startMockServer() {
        mockServer = ClientAndServer.startClientAndServer(7088);

        mockServer.when(
                HttpRequest
                        .request()
                        .withMethod("GET")
                        .withPath("/path/to/file.txt")

        ).respond(
                HttpResponse
                        .response()
                        .withStatusCode(200)
                        .withHeaders(
                                Header.header("Content-Length", 2),
                                Header.header("Accept-Ranges", "bytes")
                        )
                        .withBody("OK")
        );

        mockServer.when(
                HttpRequest
                        .request()
                        .withMethod("GET")
                        .withPath("/redirect/path/to/file")

        ).respond(
                HttpResponse
                        .response()
                        .withStatusCode(301)
                        .withHeader("Location", "http://localhost:7088/path/to/file1")
        );

        mockServer.when(
                HttpRequest
                        .request()
                        .withMethod("GET")
                        .withPath("/path/to/file1")

        ).respond(
                HttpResponse
                        .response()
                        .withStatusCode(301)
                        .withHeader("Location", "http://localhost:7088/path/to/file.txt")
        );
    }

    @After
    public void stopMockServer() {
        mockServer.stop();
    }

    @Test
    public void shouldFindURLWhenNotRedirect() throws Exception {
        HTTPInspector httpInspector = new HTTPInspector(
                new URL("http://localhost:7088/path/to/file.txt"),
                Proxy.NO_PROXY,
                5000,
                5);


        URL finalURL = httpInspector.findRedirectedFinalURL(new URL("http://localhost:7088/path/to/file.txt"), 5);
        assertEquals("http://localhost:7088/path/to/file.txt", finalURL.toString());
    }

    @Test
    public void shouldFindURLWhenRedirect() throws Exception {
        HTTPInspector httpInspector = new HTTPInspector(
                new URL("http://localhost:7088/redirect/path/to/file"),
                Proxy.NO_PROXY,
                5000,
                5);


        URL finalURL = httpInspector.findRedirectedFinalURL(new URL("http://localhost:7088/redirect/path/to/file"), 5);
        assertEquals("http://localhost:7088/path/to/file.txt", finalURL.toString());
    }

    @Test(expected = RedirectLimitException.class)
    public void shouldNotFindURLWhenRedirectLimitExceeded() throws Exception {
        HTTPInspector httpInspector = new HTTPInspector(
                new URL("http://localhost:7088/redirect/path/to/file"),
                Proxy.NO_PROXY,
                5000,
                5);


        URL finalURL = httpInspector.findRedirectedFinalURL(new URL("http://localhost:7088/redirect/path/to/file"), 1);
        assertEquals("http://localhost:7088/path/to/file.txt", finalURL.toString());
    }

    @Test
    public void shouldRetrieveFields() throws Exception {
        HTTPInspector httpInspector = new HTTPInspector(
                new URL("http://localhost:7088/redirect/path/to/file"),
                Proxy.NO_PROXY,
                5000,
                5);


        httpInspector.inspect();
        assertEquals(0, httpInspector.getLastModified());
        assertEquals(0, httpInspector.getExpiration());
        assertEquals(2, httpInspector.getContentLength());
        assertNull(httpInspector.getContentType());
        assertEquals(200, httpInspector.getResponseCode());
        assertTrue(httpInspector.isAcceptingRanges());
        assertEquals("http://localhost:7088/redirect/path/to/file", httpInspector.getOriginalUrl().toString());
        assertEquals("http://localhost:7088/path/to/file.txt", httpInspector.getFinalURL().toString());
        assertEquals("file.txt", httpInspector.getFileName());
        assertEquals(Proxy.NO_PROXY, httpInspector.getProxy());
        assertEquals(5, httpInspector.getMaxRedirects());
        assertEquals(5000, httpInspector.getTimeout());
    }
}
