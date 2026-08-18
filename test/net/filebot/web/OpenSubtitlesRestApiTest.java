package net.filebot.web;

import static net.filebot.util.JsonUtilities.*;
import static org.junit.Assert.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import net.filebot.web.OpenSubtitlesSubtitleDescriptor.Property;

public class OpenSubtitlesRestApiTest {

	private final OpenSubtitlesRestApi api = new OpenSubtitlesRestApi("test-key", "FileBot test", "https://example.com/api/v1/");

	@Test
	public void translatesLegacyHashQuery() {
		SubtitleSearchRequest query = SubtitleSearchRequest.forVideoHash("2bba5c34b007153b", 717565952, "en");
		Map<String, Object> parameters = api.getSearchParameters(query);

		assertEquals("2bba5c34b007153b", parameters.get("moviehash"));
		assertEquals("en", parameters.get("languages"));
		assertFalse(parameters.containsKey("moviebytesize"));
	}

	@Test
	public void translatesLegacyEpisodeQuery() {
		SubtitleSearchRequest query = SubtitleSearchRequest.forImdbId(105946, 1, 2, "en");
		Map<String, Object> parameters = api.getSearchParameters(query);

		assertEquals("105946", parameters.get("imdb_id"));
		assertEquals("1", parameters.get("season_number"));
		assertEquals("2", parameters.get("episode_number"));
	}

	@Test
	public void mapsSubtitleSearchResponse() {
		String json = "{\"data\":[{\"id\":\"9001\",\"type\":\"subtitle\",\"attributes\":{" +
				"\"subtitle_id\":\"8001\",\"language\":\"en\",\"download_count\":42,\"ratings\":8.5," +
				"\"from_trusted\":true,\"hearing_impaired\":false,\"hd\":true,\"moviehash_match\":true," +
				"\"release\":\"Example.Show.S01E02\",\"files\":[{\"file_id\":7001,\"file_name\":\"Example.Show.S01E02.srt\"}]," +
				"\"feature_details\":{\"movie_name\":\"Example Show\",\"year\":2024,\"imdb_id\":105946,\"feature_type\":\"Episode\",\"season_number\":1,\"episode_number\":2}}}]}";

		SubtitleSearchRequest query = SubtitleSearchRequest.forVideoHash("2bba5c34b007153b", 717565952, "en");
		List<OpenSubtitlesSubtitleDescriptor> subtitles = api.parseSubtitles(readJson(json), query);

		assertEquals(1, subtitles.size());
		OpenSubtitlesSubtitleDescriptor subtitle = subtitles.get(0);
		assertEquals("8001", subtitle.getProperty(Property.IDSubtitle));
		assertEquals("7001", subtitle.getProperty(Property.IDSubtitleFile));
		assertEquals("Example.Show.S01E02.srt", subtitle.getPath());
		assertEquals("English", subtitle.getLanguageName());
		assertEquals("srt", subtitle.getType());
		assertEquals("moviehash", subtitle.getProperty(Property.MatchedBy));
		assertEquals("Example Show", subtitle.getProperty(Property.MovieName));
		assertEquals("1", subtitle.getProperty(Property.SeriesSeason));
		assertEquals("2", subtitle.getProperty(Property.SeriesEpisode));

		List<SubtitleSearchResult> features = api.parseFeatures(readJson(json));
		assertEquals(1, features.size());
		assertTrue(features.get(0).isSeries());
		assertEquals("Example Show", features.get(0).getName());
	}

	@Test
	public void logsInAndDownloadsSubtitle() throws Exception {
		HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
		AtomicReference<String> loginBody = new AtomicReference<String>();
		AtomicReference<String> downloadBody = new AtomicReference<String>();

		server.createContext("/api/v1/login", exchange -> {
			loginBody.set(readBody(exchange));
			assertEquals("test-key", exchange.getRequestHeaders().getFirst("Api-Key"));
			respond(exchange, "{\"token\":\"test-token\"}", "application/json");
		});
		server.createContext("/api/v1/download", exchange -> {
			downloadBody.set(readBody(exchange));
			assertEquals("Bearer test-token", exchange.getRequestHeaders().getFirst("Authorization"));
			String link = "http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort() + "/subtitle";
			respond(exchange, "{\"link\":\"" + link + "\",\"remaining\":9}", "application/json");
		});
		server.createContext("/subtitle", exchange -> respond(exchange, "subtitle contents", "text/plain"));
		server.start();

		try {
			String endpoint = "http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort() + "/api/v1/";
			OpenSubtitlesRestApi localApi = new OpenSubtitlesRestApi("test-key", "FileBot test", endpoint);
			localApi.setUser("tester", "secret");

			ByteBuffer subtitle = localApi.download("7001");
			assertEquals("subtitle contents", StandardCharsets.UTF_8.decode(subtitle).toString());
			assertTrue(loginBody.get().contains("\"username\":\"tester\""));
			assertTrue(downloadBody.get().contains("\"file_id\":\"7001\""));
		} finally {
			server.stop(0);
		}
	}

	private static String readBody(HttpExchange exchange) throws IOException {
		try (InputStream input = exchange.getRequestBody(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
			byte[] buffer = new byte[1024];
			for (int count; (count = input.read(buffer)) >= 0;) {
				output.write(buffer, 0, count);
			}
			return new String(output.toByteArray(), StandardCharsets.UTF_8);
		}
	}

	private static void respond(HttpExchange exchange, String body, String contentType) throws IOException {
		byte[] data = body.getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().set("Content-Type", contentType);
		exchange.sendResponseHeaders(200, data.length);
		exchange.getResponseBody().write(data);
		exchange.close();
	}
}
