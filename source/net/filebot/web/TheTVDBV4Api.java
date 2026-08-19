package net.filebot.web;

import static java.nio.charset.StandardCharsets.*;
import static java.util.Collections.*;
import static net.filebot.CachedResource.fetchIfModified;
import static net.filebot.util.JsonUtilities.*;
import static net.filebot.web.WebRequest.*;

import java.io.FileNotFoundException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import net.filebot.Cache;
import net.filebot.CacheType;

public class TheTVDBV4Api implements TheTVDBApi {

	private final String apiKey;
	private final String pin;
	private final String endpoint;
	private final Object tokenLock = new Object();

	private String token;
	private Instant tokenExpiration;

	public TheTVDBV4Api(String apiKey, String pin) {
		this(apiKey, pin, System.getProperty("net.filebot.TheTVDBApi.url", "https://api4.thetvdb.com/v4/"));
	}

	TheTVDBV4Api(String apiKey, String pin, String endpoint) {
		this.apiKey = apiKey == null ? "" : apiKey.trim();
		this.pin = pin == null ? "" : pin.trim();
		this.endpoint = endpoint.endsWith("/") ? endpoint : endpoint + "/";
	}

	@Override
	public Object searchSeries(String query, Locale locale, Duration expirationTime) throws Exception {
		Map<String, Object> parameters = new LinkedHashMap<String, Object>();
		parameters.put("query", query);
		parameters.put("type", "series");
		parameters.put("language", getLanguageCode(locale));
		return request("search?" + encodeParameters(parameters, true), locale, expirationTime);
	}

	@Override
	public Object searchSeriesByRemoteId(String remoteId, Locale locale, Duration expirationTime) throws Exception {
		Map<String, Object> parameters = new LinkedHashMap<String, Object>();
		parameters.put("remote_id", remoteId);
		parameters.put("type", "series");
		return request("search?" + encodeParameters(parameters, true), locale, expirationTime);
	}

	@Override
	public Object getSeries(int id, Locale locale, Duration expirationTime) throws Exception {
		return request("series/" + id + "/extended?meta=translations", locale, expirationTime);
	}

	@Override
	public Object getSeriesEpisodes(int id, String seasonType, Locale locale, int page, Duration expirationTime) throws Exception {
		String language = getLanguageCode(locale);
		try {
			return request("series/" + id + "/episodes/" + seasonType + "/" + language + "?page=" + page, locale, expirationTime);
		} catch (FileNotFoundException e) {
			return request("series/" + id + "/episodes/" + seasonType + "?page=" + page, Locale.ROOT, expirationTime);
		}
	}

	@Override
	public Object getEpisode(int id, Locale locale, Duration expirationTime) throws Exception {
		return request("episodes/" + id + "/extended?meta=translations", locale, expirationTime);
	}

	@Override
	public Object getLanguages(Duration expirationTime) throws Exception {
		return request("languages", Locale.ROOT, expirationTime);
	}

	@Override
	public Object getArtworkTypes(Duration expirationTime) throws Exception {
		return request("artwork/types", Locale.ROOT, expirationTime);
	}

	protected Object request(String path, Locale locale, Duration expirationTime) throws Exception {
		Cache cache = Cache.getCache(locale == null || locale == Locale.ROOT ? "TheTVDBV4" : "TheTVDBV4_" + locale.getLanguage(), CacheType.Monthly);
		return cache.json(path, this::getEndpoint).fetch(fetchIfModified(this::getRequestHeaders)).expire(expirationTime).get();
	}

	private URL getEndpoint(String path) throws Exception {
		return new URL(endpoint + path);
	}

	private Map<String, String> getRequestHeaders() {
		Map<String, String> headers = new LinkedHashMap<String, String>();
		headers.put("Accept", "application/json");
		headers.put("Authorization", "Bearer " + getAuthorizationToken());
		return headers;
	}

	private String getAuthorizationToken() {
		synchronized (tokenLock) {
			if (token == null || tokenExpiration == null || Instant.now().isAfter(tokenExpiration)) {
				if (apiKey.isEmpty()) {
					throw new IllegalStateException("TheTVDB API key is not configured");
				}

				try {
					Map<String, String> credentials = new LinkedHashMap<String, String>();
					credentials.put("apikey", apiKey);
					if (!pin.isEmpty()) {
						credentials.put("pin", pin);
					}

					ByteBuffer response = post(getEndpoint("login"), json(credentials, false).getBytes(UTF_8), "application/json", singletonMap("Accept", "application/json"));
					token = getString(getMap(readJson(UTF_8.decode(response)), "data"), "token");
					if (token == null) {
						throw new IllegalStateException("TheTVDB login did not return an authentication token");
					}
					tokenExpiration = Instant.now().plus(Duration.ofDays(29));
				} catch (Exception e) {
					throw new IllegalStateException("Failed to authenticate with TheTVDB", e);
				}
			}
			return token;
		}
	}

	static String getLanguageCode(Locale locale) {
		if (locale == null || locale == Locale.ROOT || locale.getLanguage().isEmpty()) {
			return "eng";
		}

		try {
			return locale.getISO3Language().toLowerCase(Locale.ROOT);
		} catch (Exception e) {
			return "eng";
		}
	}
}
