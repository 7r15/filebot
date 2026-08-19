package net.filebot.web;

import static java.nio.charset.StandardCharsets.*;
import static java.util.Collections.*;
import static org.junit.Assert.*;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

public class OMDbV1ApiTest {

	@Test
	public void requestIncludesEncodedParametersAndCredential() throws Exception {
		AtomicReference<String> query = new AtomicReference<String>();
		HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
		server.createContext("/", exchange -> respond(exchange, query));
		server.start();

		try {
			String endpoint = "http://" + server.getAddress().getHostString() + ':' + server.getAddress().getPort() + '/';
			new OMDbV1Api("application-key", endpoint).request(singletonMap("s", "Example Movie"));

			assertTrue(query.get().contains("s=Example+Movie"));
			assertTrue(query.get().contains("apikey=application-key"));
		} finally {
			server.stop(0);
		}
	}

	@Test
	public void apiKeyIsRequired() throws Exception {
		try {
			new OMDbV1Api("", "http://127.0.0.1/").request(singletonMap("s", "Example"));
			fail("Expected missing credential error");
		} catch (IllegalStateException e) {
			assertEquals("OMDb credentials are not configured", e.getMessage());
		}
	}

	private static void respond(HttpExchange exchange, AtomicReference<String> query) throws IOException {
		query.set(exchange.getRequestURI().getRawQuery());
		byte[] data = "{\"Response\":\"True\"}".getBytes(UTF_8);
		exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
		exchange.sendResponseHeaders(200, data.length);
		exchange.getResponseBody().write(data);
		exchange.close();
	}
}
