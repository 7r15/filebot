package net.filebot.web;

import static java.nio.charset.StandardCharsets.*;
import static org.junit.Assert.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.GZIPInputStream;

import org.junit.Test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

public class AcoustIDV2ApiTest {

	@Test
	public void applicationKeyIsRequired() throws Exception {
		try {
			new AcoustIDV2Api("", "http://127.0.0.1/lookup").lookup(357, "fixture-fingerprint");
			fail("Expected missing credential error");
		} catch (IllegalStateException e) {
			assertEquals("AcoustID credentials are not configured", e.getMessage());
		}
	}

	@Test
	public void lookupUsesCompressedPostWithoutQueryCredentials() throws Exception {
		AtomicReference<String> method = new AtomicReference<String>();
		AtomicReference<String> query = new AtomicReference<String>();
		AtomicReference<String> encoding = new AtomicReference<String>();
		AtomicReference<String> body = new AtomicReference<String>();
		HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
		server.createContext("/lookup", exchange -> respond(exchange, method, query, encoding, body));
		server.start();

		try {
			String endpoint = "http://" + server.getAddress().getHostString() + ':' + server.getAddress().getPort() + "/lookup";
			String response = new AcoustIDV2Api("application-key", endpoint).lookup(357, "fixture-fingerprint");

			assertEquals("POST", method.get());
			assertNull(query.get());
			assertEquals("gzip", encoding.get());
			assertTrue(body.get().contains("client=application-key"));
			assertTrue(body.get().contains("duration=357"));
			assertTrue(body.get().contains("fingerprint=fixture-fingerprint"));
			assertTrue(body.get().contains("meta=recordings+releases+releasegroups+tracks+compress"));
			assertEquals("{\"status\":\"ok\",\"results\":[]}", response);
		} finally {
			server.stop(0);
		}
	}

	private static void respond(HttpExchange exchange, AtomicReference<String> method, AtomicReference<String> query, AtomicReference<String> encoding, AtomicReference<String> body) throws IOException {
		method.set(exchange.getRequestMethod());
		query.set(exchange.getRequestURI().getRawQuery());
		encoding.set(exchange.getRequestHeaders().getFirst("Content-Encoding"));
		body.set(read(new GZIPInputStream(exchange.getRequestBody())));

		byte[] data = "{\"status\":\"ok\",\"results\":[]}".getBytes(UTF_8);
		exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
		exchange.sendResponseHeaders(200, data.length);
		exchange.getResponseBody().write(data);
		exchange.close();
	}

	private static String read(InputStream input) throws IOException {
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		byte[] buffer = new byte[1024];
		for (int length; (length = input.read(buffer)) >= 0;) {
			output.write(buffer, 0, length);
		}
		return new String(output.toByteArray(), UTF_8);
	}
}
