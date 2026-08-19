package net.filebot.web;

import static net.filebot.util.JsonUtilities.*;
import static org.junit.Assert.*;

import java.net.URL;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.junit.Test;

public class TMDbClientTest {

	@Test
	public void searchUsesAdapterAndMapsMovie() throws Exception {
		FixtureApi api = new FixtureApi();
		api.response = readJson("{\"results\":[{\"id\":16320,\"title\":\"Serenity\",\"original_title\":\"Serenity\",\"release_date\":\"2005-09-30\"}]}");
		TMDbClient client = new TMDbClient(api, false);

		List<Movie> result = client.searchMovie("Serenity", Locale.ENGLISH);

		assertEquals("search/movie", api.resource);
		assertEquals("Serenity", api.parameters.get("query"));
		assertEquals(Locale.ENGLISH, api.locale);
		assertEquals("Serenity", result.get(0).getName());
		assertEquals(2005, result.get(0).getYear());
		assertEquals(16320, result.get(0).getTmdbId());
	}

	@Test
	public void adultSearchSetsExplicitFlag() throws Exception {
		FixtureApi api = new FixtureApi();
		api.response = readJson("{\"results\":[]}");
		TMDbClient client = new TMDbClient(api, true);

		client.searchMovie("Example", Locale.ROOT);

		assertEquals(Boolean.TRUE, api.parameters.get("include_adult"));
	}

	static class FixtureApi implements TMDbApi {

		Object response;
		String resource;
		Map<String, Object> parameters;
		Locale locale;
		final List<String> resources = new ArrayList<String>();
		final Map<String, Object> responses = new LinkedHashMap<String, Object>();

		@Override
		public Object request(String resource, Map<String, Object> parameters, Locale locale) {
			this.resource = resource;
			this.parameters = parameters;
			this.locale = locale;
			this.resources.add(resource);
			return responses.containsKey(resource) ? responses.get(resource) : response;
		}

		@Override
		public URL resolveImage(String path) {
			try {
				return path == null ? null : new URL("https://images.example" + path);
			} catch (Exception e) {
				throw new IllegalArgumentException(e);
			}
		}
	}
}
