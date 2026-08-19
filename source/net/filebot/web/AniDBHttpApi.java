package net.filebot.web;

import static net.filebot.CachedResource.*;
import static net.filebot.web.WebRequest.*;

import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.w3c.dom.Document;

import net.filebot.Cache;
import net.filebot.CacheType;

public class AniDBHttpApi implements AniDBApi {

	private static final FloodLimit REQUEST_LIMIT = new FloodLimit(1, 2, TimeUnit.SECONDS);
	private static final String DEFAULT_ENDPOINT = "http://api.anidb.net:9001/httpapi";
	private static final String DEFAULT_TITLES_ENDPOINT = "http://anidb.net/api/anime-titles.dat.gz";

	private final String client;
	private final int clientVersion;
	private final String endpoint;
	private final String titlesEndpoint;

	public AniDBHttpApi(String client, int clientVersion) {
		this(client, clientVersion, System.getProperty("net.filebot.AniDBApi.url", DEFAULT_ENDPOINT), System.getProperty("net.filebot.AniDBApi.titles.url", DEFAULT_TITLES_ENDPOINT));
	}

	AniDBHttpApi(String client, int clientVersion, String endpoint, String titlesEndpoint) {
		this.client = client == null ? "" : client.trim().toLowerCase(Locale.ROOT);
		this.clientVersion = clientVersion;
		this.endpoint = endpoint;
		this.titlesEndpoint = titlesEndpoint;
	}

	@Override
	public Document getAnime(int id) throws Exception {
		validateClient();

		Map<String, Object> query = new LinkedHashMap<String, Object>();
		query.put("request", "anime");
		query.put("client", client);
		query.put("clientver", clientVersion);
		query.put("protover", 1);
		query.put("aid", id);

		URL url = new URL(endpoint + (endpoint.contains("?") ? '&' : '?') + encodeParameters(query, true));
		String cacheName = DEFAULT_ENDPOINT.equals(endpoint) ? "AniDB" : "AniDB_" + Integer.toHexString(endpoint.hashCode());
		Cache cache = Cache.getCache(cacheName, CacheType.Monthly);
		return cache.xml(id, key -> url).fetch(withPermit(fetchIfModified(), r -> REQUEST_LIMIT.acquirePermit())).expire(Cache.ONE_WEEK).get();
	}

	@Override
	public byte[] getAnimeTitles() throws Exception {
		String cacheName = DEFAULT_TITLES_ENDPOINT.equals(titlesEndpoint) ? "AniDB_root" : "AniDB_root_" + Integer.toHexString(titlesEndpoint.hashCode());
		return Cache.getCache(cacheName, CacheType.Weekly).bytes("anime-titles.dat.gz", key -> new URL(titlesEndpoint)).get();
	}

	private void validateClient() {
		if (client.isEmpty() || clientVersion <= 0) {
			throw new IllegalStateException("AniDB client registration is not configured");
		}
	}
}
