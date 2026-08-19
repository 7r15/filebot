package net.filebot.web;

import static java.nio.charset.StandardCharsets.*;
import static org.junit.Assert.*;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

public class AniDBHttpApiTest {

	@Test
	public void animeRequestIncludesRegisteredClientParameters() throws Exception {
		AtomicReference<String> query = new AtomicReference<String>();
		HttpServer server = server(query);
		try {
			String base = "http://" + server.getAddress().getHostString() + ':' + server.getAddress().getPort();
			AniDBHttpApi api = new AniDBHttpApi("FixtureClient", 6, base + "/httpapi", base + "/titles");

			assertEquals("Monster", api.getAnime(1539).getElementsByTagName("title").item(0).getTextContent());
			assertTrue(query.get().contains("request=anime"));
			assertTrue(query.get().contains("client=fixtureclient"));
			assertTrue(query.get().contains("clientver=6"));
			assertTrue(query.get().contains("protover=1"));
			assertTrue(query.get().contains("aid=1539"));
			assertEquals("69|1|x-jat|One Piece", new String(api.getAnimeTitles(), UTF_8));
		} finally {
			server.stop(0);
		}
	}

	@Test
	public void registeredClientIsRequired() throws Exception {
		try {
			new AniDBHttpApi("", 0, "http://127.0.0.1/httpapi", "http://127.0.0.1/titles").getAnime(1);
			fail("Expected missing client registration error");
		} catch (IllegalStateException e) {
			assertEquals("AniDB client registration is not configured", e.getMessage());
		}
	}

	private static HttpServer server(AtomicReference<String> query) throws IOException {
		HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
		server.createContext("/httpapi", exchange -> {
			query.set(exchange.getRequestURI().getRawQuery());
			respond(exchange, "<anime id=\"1539\"><title>Monster</title></anime>");
		});
		server.createContext("/titles", exchange -> respond(exchange, "69|1|x-jat|One Piece"));
		server.start();
		return server;
	}

	private static void respond(HttpExchange exchange, String response) throws IOException {
		byte[] data = response.getBytes(UTF_8);
		exchange.sendResponseHeaders(200, data.length);
		exchange.getResponseBody().write(data);
		exchange.close();
	}
}
