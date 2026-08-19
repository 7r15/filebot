package net.filebot.web;

import static java.nio.charset.StandardCharsets.*;
import static org.junit.Assert.*;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.Test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

public class ProviderConnectionTesterTest {

	@Test
	public void supportedProviderChecksAcceptValidResponses() throws Exception {
		Map<String, String> requests = new LinkedHashMap<String, String>();
		HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
		server.createContext("/tmdb", exchange -> respond(exchange, requests, "tmdb", "{\"images\":{\"base_url\":\"https://example.test/\"}}"));
		server.createContext("/tvdb", exchange -> respond(exchange, requests, "tvdb", "{\"data\":{\"token\":\"token\"}}"));
		server.createContext("/opensubtitles/infos/languages", exchange -> respond(exchange, requests, "opensubtitles", "{\"data\":[{\"language_code\":\"en\",\"language_name\":\"English\"}]}"));
		server.createContext("/omdb", exchange -> respond(exchange, requests, "omdb", "{\"imdbID\":\"tt1375666\"}"));
		server.createContext("/fanart", exchange -> respond(exchange, requests, "fanart.tv", "{\"name\":\"The Matrix\"}"));
		server.start();

		String base = "http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort();
		try {
			setEndpoint("themoviedb", base + "/tmdb");
			setEndpoint("thetvdb", base + "/tvdb");
			setEndpoint("opensubtitles", base + "/opensubtitles/");
			setEndpoint("omdb", base + "/omdb");
			setEndpoint("fanart.tv", base + "/fanart");

			ProviderConnectionTester.test("themoviedb", "tmdb-key");
			ProviderConnectionTester.test("thetvdb", "tvdb-key");
			ProviderConnectionTester.test("opensubtitles", "subtitles-key");
			ProviderConnectionTester.test("omdb", "omdb-key");
			ProviderConnectionTester.test("fanart.tv", "fanart-key");

			assertTrue(requests.get("tmdb").contains("api_key=tmdb-key"));
			assertTrue(requests.get("tvdb").contains("tvdb-key"));
			assertEquals("subtitles-key", requests.get("opensubtitles"));
			assertTrue(requests.get("omdb").contains("apikey=omdb-key"));
			assertTrue(requests.get("fanart.tv").contains("api_key=fanart-key"));
		} finally {
			server.stop(0);
			clearEndpoint("themoviedb");
			clearEndpoint("thetvdb");
			clearEndpoint("opensubtitles");
			clearEndpoint("omdb");
			clearEndpoint("fanart.tv");
		}
	}

	@Test
	public void credentialsAreRequired() {
		try {
			ProviderConnectionTester.test("themoviedb", " ");
			fail("Expected missing credential error");
		} catch (IllegalArgumentException e) {
			assertEquals("Enter an API key first", e.getMessage());
		}
	}

	private static void setEndpoint(String provider, String endpoint) {
		System.setProperty("net.filebot.provider.test." + provider + ".url", endpoint);
	}

	private static void clearEndpoint(String provider) {
		System.clearProperty("net.filebot.provider.test." + provider + ".url");
	}

	private static void respond(HttpExchange exchange, Map<String, String> requests, String provider, String response) throws IOException {
		String request = exchange.getRequestURI().getRawQuery();
		if ("tvdb".equals(provider)) {
			request = read(exchange.getRequestBody());
		} else if ("opensubtitles".equals(provider)) {
			request = exchange.getRequestHeaders().getFirst("Api-Key");
		}
		requests.put(provider, request);

		byte[] data = response.getBytes(UTF_8);
		exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
		exchange.sendResponseHeaders(200, data.length);
		exchange.getResponseBody().write(data);
		exchange.close();
	}

	private static String read(InputStream input) throws IOException {
		byte[] buffer = new byte[1024];
		int length = input.read(buffer);
		return length < 0 ? "" : new String(buffer, 0, length, UTF_8);
	}
}
