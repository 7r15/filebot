package net.filebot.web;

import static java.util.Collections.*;
import static net.filebot.CachedResource.*;
import static net.filebot.util.JsonUtilities.*;
import static net.filebot.web.WebRequest.*;

import java.io.FileNotFoundException;
import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import net.filebot.Cache;
import net.filebot.CacheType;

public class TMDbV3Api implements TMDbApi {

	private static final FloodLimit REQUEST_LIMIT = new FloodLimit(35, 10, TimeUnit.SECONDS);
	private static final String DEFAULT_ENDPOINT = "https://api.themoviedb.org/3/";

	private final String apiKey;
	private final String accessToken;
	private final String endpoint;

	public TMDbV3Api(String apiKey, String accessToken) {
		this(apiKey, accessToken, System.getProperty("net.filebot.TMDbApi.url", DEFAULT_ENDPOINT));
	}

	TMDbV3Api(String apiKey, String accessToken, String endpoint) {
		this.apiKey = apiKey == null ? "" : apiKey.trim();
		this.accessToken = accessToken == null ? "" : accessToken.trim();
		this.endpoint = endpoint.endsWith("/") ? endpoint : endpoint + '/';
	}

	@Override
	public Object request(String resource, Map<String, Object> parameters, Locale locale) throws Exception {
		Map<String, Object> query = new LinkedHashMap<String, Object>(parameters);
		String language = getLanguageCode(locale);
		if (language != null) {
			query.put("language", language);
		}
		if (accessToken.isEmpty()) {
			if (apiKey.isEmpty()) {
				throw new IllegalStateException("TMDB credentials are not configured");
			}
			query.put("api_key", apiKey);
		}

		String cacheKey = parameters.isEmpty() ? resource : resource + '?' + encodeParameters(parameters, true);
		String cacheName = language == null ? "TheMovieDB" : "TheMovieDB_" + language;
		if (!DEFAULT_ENDPOINT.equals(endpoint)) {
			cacheName += '_' + Integer.toHexString(endpoint.hashCode());
		}
		URL url = new URL(endpoint + resource + (query.isEmpty() ? "" : '?' + encodeParameters(query, true)));
		Map<String, String> headers = accessToken.isEmpty() ? emptyMap() : singletonMap("Authorization", "Bearer " + accessToken);

		Cache cache = Cache.getCache(cacheName, CacheType.Monthly);
		Object json = cache.json(cacheKey, key -> url).fetch(withPermit(fetchIfModified(() -> headers), r -> REQUEST_LIMIT.acquirePermit())).expire(Cache.ONE_WEEK).get();
		if (asMap(json).isEmpty()) {
			throw new FileNotFoundException(String.format("TMDB resource is empty: %s", resource));
		}
		return json;
	}

	@Override
	public URL resolveImage(String path) {
		if (path == null || path.isEmpty()) {
			return null;
		}
		try {
			String mirror = (String) Cache.getCache("TheMovieDB", CacheType.Monthly).computeIfAbsent("configuration.secure_base_url", key -> {
				return getString(getMap(request("configuration", emptyMap(), Locale.ROOT), "images"), "secure_base_url");
			});
			return new URL(mirror + "original" + path);
		} catch (Exception e) {
			throw new IllegalArgumentException(path, e);
		}
	}

	static String getLanguageCode(Locale locale) {
		String language = locale.getLanguage();
		switch (language) {
		case "iw":
			return "he-IL";
		case "in":
			return "id-ID";
		case "":
			return null;
		default:
			String country = locale.getCountry();
			return country.isEmpty() ? language : language + '-' + country;
		}
	}
}
