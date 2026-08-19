package net.filebot.web;

import static java.nio.charset.StandardCharsets.*;
import static org.junit.Assert.*;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

public class TheTVDBV4ApiTest {

	@Test
	public void loginUsesOptionalPinAndBearerToken() throws Exception {
		AtomicInteger logins = new AtomicInteger();
		AtomicReference<String> loginBody = new AtomicReference<String>();
		AtomicReference<String> authorization = new AtomicReference<String>();
		AtomicReference<String> requestPath = new AtomicReference<String>();

		HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
		server.createContext("/v4/login", exchange -> {
			logins.incrementAndGet();
			loginBody.set(read(exchange.getRequestBody()));
			respond(exchange, "{\"data\":{\"token\":\"fixture-token\"}}");
		});
		server.createContext("/v4/search", exchange -> {
			authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
			requestPath.set(exchange.getRequestURI().toString());
			respond(exchange, "{\"data\":[]}");
		});
		server.start();

		String endpoint = "http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort() + "/v4/";
		try {
			TheTVDBV4Api api = new TheTVDBV4Api("fixture-key", "fixture-pin", endpoint);
			api.searchSeries("Fixture " + System.nanoTime(), Locale.GERMAN, Duration.ZERO);
			api.searchSeries("Second Fixture " + System.nanoTime(), Locale.GERMAN, Duration.ZERO);

			assertEquals(1, logins.get());
			assertTrue(loginBody.get().contains("fixture-key"));
			assertTrue(loginBody.get().contains("fixture-pin"));
			assertEquals("Bearer fixture-token", authorization.get());
			assertTrue(requestPath.get().contains("type=series"));
			assertTrue(requestPath.get().contains("language=deu"));
		} finally {
			server.stop(0);
		}
	}

	@Test
	public void languageCodesUseV4ThreeLetterFormat() {
		assertEquals("eng", TheTVDBV4Api.getLanguageCode(Locale.ENGLISH));
		assertEquals("deu", TheTVDBV4Api.getLanguageCode(Locale.GERMAN));
		assertEquals("eng", TheTVDBV4Api.getLanguageCode(Locale.ROOT));
	}

	private static void respond(HttpExchange exchange, String response) throws IOException {
		byte[] data = response.getBytes(UTF_8);
		exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
		exchange.sendResponseHeaders(200, data.length);
		exchange.getResponseBody().write(data);
		exchange.close();
	}

	private static String read(InputStream input) throws IOException {
		byte[] buffer = new byte[2048];
		int length = input.read(buffer);
		return length < 0 ? "" : new String(buffer, 0, length, UTF_8);
	}
}
