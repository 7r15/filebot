package net.filebot.web;

import static java.nio.charset.StandardCharsets.*;
import static net.filebot.web.WebRequest.*;

import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import net.filebot.Cache;
import net.filebot.CacheType;

public class AcoustIDV2Api implements AcoustIDApi {

	private static final FloodLimit REQUEST_LIMIT = new FloodLimit(3, 1, TimeUnit.SECONDS);
	private static final String DEFAULT_ENDPOINT = "https://api.acoustid.org/v2/lookup";

	private final String apiKey;
	private final URL endpoint;

	public AcoustIDV2Api(String apiKey) {
		this(apiKey, System.getProperty("net.filebot.AcoustIDApi.url", DEFAULT_ENDPOINT));
	}

	AcoustIDV2Api(String apiKey, String endpoint) {
		try {
			this.apiKey = apiKey == null ? "" : apiKey.trim();
			this.endpoint = new URL(endpoint);
		} catch (Exception e) {
			throw new IllegalArgumentException("Invalid AcoustID endpoint", e);
		}
	}

	@Override
	public String lookup(int duration, String fingerprint) throws Exception {
		if (apiKey.isEmpty()) {
			throw new IllegalStateException("AcoustID credentials are not configured");
		}

		Map<String, String> lookup = new LinkedHashMap<String, String>();
		lookup.put("duration", String.valueOf(duration));
		lookup.put("fingerprint", fingerprint);

		String cacheName = DEFAULT_ENDPOINT.equals(endpoint.toString()) ? "AcoustID" : "AcoustID_" + Integer.toHexString(endpoint.toString().hashCode());
		return (String) Cache.getCache(cacheName, CacheType.Monthly).computeIfAbsent(lookup.toString(), key -> {
			REQUEST_LIMIT.acquirePermit();

			Map<String, String> parameters = new LinkedHashMap<String, String>();
			parameters.put("client", apiKey);
			parameters.put("meta", "recordings releases releasegroups tracks compress");
			parameters.putAll(lookup);

			Map<String, String> headers = new LinkedHashMap<String, String>();
			headers.put("Content-Encoding", "gzip");
			headers.put("Accept-Encoding", "gzip");
			return UTF_8.decode(post(endpoint, parameters, headers)).toString();
		});
	}
}
