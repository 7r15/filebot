package net.filebot.web;

import static java.nio.charset.StandardCharsets.*;
import static net.filebot.CachedResource.*;
import static net.filebot.util.JsonUtilities.*;
import static net.filebot.web.WebRequest.*;

import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import net.filebot.Cache;
import net.filebot.CacheType;

public class OMDbV1Api implements OMDbApi {

	private static final FloodLimit REQUEST_LIMIT = new FloodLimit(2, 1, TimeUnit.SECONDS);
	private static final String DEFAULT_ENDPOINT = "https://www.omdbapi.com/";

	private final String apiKey;
	private final String endpoint;
	private final boolean cacheEnabled;

	public OMDbV1Api(String apiKey) {
		this(apiKey, System.getProperty("net.filebot.OMDbApi.url", DEFAULT_ENDPOINT), true);
	}

	OMDbV1Api(String apiKey, String endpoint) {
		this(apiKey, endpoint, true);
	}

	OMDbV1Api(String apiKey, String endpoint, boolean cacheEnabled) {
		this.apiKey = apiKey == null ? "" : apiKey.trim();
		this.endpoint = endpoint.endsWith("/") ? endpoint : endpoint + '/';
		this.cacheEnabled = cacheEnabled;
	}

	@Override
	public Object request(Map<String, Object> parameters) throws Exception {
		if (apiKey.isEmpty()) {
			throw new IllegalStateException("OMDb credentials are not configured");
		}

		String cacheKey = encodeParameters(parameters, true);
		Map<String, Object> query = new LinkedHashMap<String, Object>(parameters);
		query.put("apikey", apiKey);
		URL url = new URL(endpoint + '?' + encodeParameters(query, true));
		if (!cacheEnabled) {
			REQUEST_LIMIT.acquirePermit();
			return readJson(UTF_8.decode(fetch(url)));
		}

		String cacheName = DEFAULT_ENDPOINT.equals(endpoint) ? "OMDb" : "OMDb_" + Integer.toHexString(endpoint.hashCode());
		Cache cache = Cache.getCache(cacheName, CacheType.Monthly);
		return cache.json(cacheKey, key -> url).fetch(withPermit(fetchIfModified(), r -> REQUEST_LIMIT.acquirePermit())).expire(Cache.ONE_WEEK).get();
	}
}
