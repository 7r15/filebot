package net.filebot.web;

import static java.nio.charset.StandardCharsets.*;
import static java.util.Collections.*;
import static net.filebot.Settings.*;
import static net.filebot.util.JsonUtilities.*;
import static net.filebot.web.WebRequest.*;

import java.net.URL;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ProviderConnectionTester {

	public static void test(String provider, String apiKey) {
		if (apiKey == null || apiKey.trim().isEmpty()) {
			throw new IllegalArgumentException("Enter a credential first");
		}

		try {
			switch (provider) {
			case "themoviedb.token":
				testTheMovieDBToken(apiKey.trim());
				break;
			case "themoviedb":
				testTheMovieDB(apiKey.trim());
				break;
			case "thetvdb":
				testTheTVDB(apiKey.trim());
				break;
			case "opensubtitles":
				testOpenSubtitles(apiKey.trim());
				break;
			case "omdb":
				testOMDb(apiKey.trim());
				break;
			case "fanart.tv":
				testFanartTV(apiKey.trim());
				break;
			default:
				throw new IllegalArgumentException("Connection testing is not available for this provider");
			}
		} catch (IllegalArgumentException e) {
			throw e;
		} catch (Exception e) {
			throw new IllegalStateException("Connection failed or credential was rejected");
		}
	}

	private static void testTheMovieDB(String apiKey) throws Exception {
		Object response = get(endpoint("themoviedb", "https://api.themoviedb.org/3/configuration") + "?api_key=" + encode(apiKey), emptyMap());
		if (getMap(response, "images").isEmpty()) {
			throw new IllegalStateException();
		}
	}

	private static void testTheMovieDBToken(String accessToken) throws Exception {
		Object response = get(endpoint("themoviedb", "https://api.themoviedb.org/3/configuration"), singletonMap("Authorization", "Bearer " + accessToken));
		if (getMap(response, "images").isEmpty()) {
			throw new IllegalStateException();
		}
	}

	private static void testTheTVDB(String apiKey) throws Exception {
		Map<String, String> credentials = new LinkedHashMap<String, String>();
		credentials.put("apikey", apiKey);
		String pin = getApiKey("thetvdb.pin");
		if (!pin.isEmpty()) {
			credentials.put("pin", pin);
		}
		ByteBuffer response = post(new URL(endpoint("thetvdb", "https://api4.thetvdb.com/v4/login")), json(credentials, false).getBytes(UTF_8), "application/json", singletonMap("Accept", "application/json"));
		if (getString(getMap(readJson(UTF_8.decode(response)), "data"), "token") == null) {
			throw new IllegalStateException();
		}
	}

	private static void testOpenSubtitles(String apiKey) throws Exception {
		String endpoint = endpoint("opensubtitles", "https://api.opensubtitles.com/api/v1/");
		OpenSubtitlesRestApi service = new OpenSubtitlesRestApi(apiKey, "FileBot settings", endpoint);
		if (service.getLanguages().isEmpty()) {
			throw new IllegalStateException();
		}
	}

	private static void testOMDb(String apiKey) throws Exception {
		Object response = get(endpoint("omdb", "https://www.omdbapi.com/") + "?i=tt1375666&apikey=" + encode(apiKey), emptyMap());
		if (!"tt1375666".equals(getString(response, "imdbID"))) {
			throw new IllegalStateException();
		}
	}

	private static void testFanartTV(String apiKey) throws Exception {
		Object response = get(endpoint("fanart.tv", "https://webservice.fanart.tv/v3/movies/27205") + "?api_key=" + encode(apiKey), emptyMap());
		if (asMap(response).isEmpty() || getString(response, "error message") != null) {
			throw new IllegalStateException();
		}
	}

	private static Object get(String url, Map<String, String> headers) throws Exception {
		ByteBuffer response = fetch(new URL(url), 0, null, headers, null);
		return readJson(UTF_8.decode(response));
	}

	private static String endpoint(String provider, String defaultValue) {
		return System.getProperty("net.filebot.provider.test." + provider + ".url", defaultValue);
	}

	private static String encode(String value) throws Exception {
		return URLEncoder.encode(value, UTF_8.name());
	}

	private ProviderConnectionTester() {
		throw new UnsupportedOperationException();
	}
}
