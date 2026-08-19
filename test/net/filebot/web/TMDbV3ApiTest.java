package net.filebot.web;

import static java.nio.charset.StandardCharsets.*;
import static java.util.Collections.*;
import static org.junit.Assert.*;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

public class TMDbV3ApiTest {

	@Test
	public void bearerTokenUsesAuthorizationHeader() throws Exception {
		AtomicReference<String> authorization = new AtomicReference<String>();
		AtomicReference<String> query = new AtomicReference<String>();
		HttpServer server = server(authorization, query);
		try {
			String endpoint = "http://" + server.getAddress().getHostString() + ':' + server.getAddress().getPort() + '/';
			new TMDbV3Api("legacy-key", "read-token", endpoint).request("bearer-test", singletonMap("query", "Example Title"), Locale.US);

			assertEquals("Bearer read-token", authorization.get());
			assertTrue(query.get().contains("query=Example+Title"));
			assertTrue(query.get().contains("language=en-US"));
			assertFalse(query.get().contains("api_key"));
			assertFalse(query.get().contains("read-token"));
		} finally {
			server.stop(0);
		}
	}

	@Test
	public void apiKeyRemainsSupported() throws Exception {
		AtomicReference<String> authorization = new AtomicReference<String>();
		AtomicReference<String> query = new AtomicReference<String>();
		HttpServer server = server(authorization, query);
		try {
			String endpoint = "http://" + server.getAddress().getHostString() + ':' + server.getAddress().getPort() + '/';
			new TMDbV3Api("legacy-key", "", endpoint).request("key-test", emptyMap(), Locale.ROOT);

			assertNull(authorization.get());
			assertEquals("api_key=legacy-key", query.get());
		} finally {
			server.stop(0);
		}
	}

	@Test
	public void languageCodesMatchProviderFormat() {
		assertEquals("en-US", TMDbV3Api.getLanguageCode(Locale.US));
		assertEquals("he-IL", TMDbV3Api.getLanguageCode(Locale.forLanguageTag("he-IL")));
		assertEquals("id-ID", TMDbV3Api.getLanguageCode(Locale.forLanguageTag("id-ID")));
		assertNull(TMDbV3Api.getLanguageCode(Locale.ROOT));
	}

	private static HttpServer server(AtomicReference<String> authorization, AtomicReference<String> query) throws IOException {
		HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
		server.createContext("/", exchange -> respond(exchange, authorization, query));
		server.start();
		return server;
	}

	private static void respond(HttpExchange exchange, AtomicReference<String> authorization, AtomicReference<String> query) throws IOException {
		authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
		query.set(exchange.getRequestURI().getRawQuery());
		byte[] data = "{\"status\":\"ok\"}".getBytes(UTF_8);
		exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
		exchange.sendResponseHeaders(200, data.length);
		exchange.getResponseBody().write(data);
		exchange.close();
	}
}
