package net.filebot.web;

import static net.filebot.util.JsonUtilities.*;
import static org.junit.Assert.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;

public class OMDbClientTest {

	@Test
	public void searchMapsMoviesAndFiltersOtherTypes() throws Exception {
		FixtureApi api = new FixtureApi("{\"Search\":[{\"Title\":\"Avatar\",\"Year\":\"2009\",\"imdbID\":\"tt0499549\",\"Type\":\"movie\"},{\"Title\":\"Avatar: The Last Airbender\",\"Year\":\"2005\",\"imdbID\":\"tt0417299\",\"Type\":\"series\"}],\"Response\":\"True\"}");
		OMDbClient client = new OMDbClient(api);

		List<Movie> results = client.searchMovie("Avatar 2009", null);

		assertEquals("Avatar", api.parameters.get("s"));
		assertEquals(2009, api.parameters.get("y"));
		assertEquals(1, results.size());
		assertEquals("Avatar", results.get(0).getName());
		assertEquals(2009, results.get(0).getYear());
		assertEquals(499549, results.get(0).getImdbId());
	}

	@Test
	public void descriptorMapsIdentifierResponse() throws Exception {
		FixtureApi api = new FixtureApi("{\"Title\":\"Avatar\",\"Year\":\"2009\",\"imdbID\":\"tt0499549\",\"Type\":\"movie\",\"Response\":\"True\"}");
		OMDbClient client = new OMDbClient(api);

		Movie movie = client.getMovieDescriptor(new Movie(499549), null);

		assertEquals("tt0499549", api.parameters.get("i"));
		assertEquals("Avatar", movie.getName());
		assertEquals(499549, movie.getImdbId());
	}

	@Test
	public void movieInfoMapsMetadataFixture() throws Exception {
		FixtureApi api = new FixtureApi("{\"Title\":\"Avatar\",\"Year\":\"2009\",\"Rated\":\"PG-13\",\"Released\":\"18 Dec 2009\",\"Runtime\":\"162 min\",\"Genre\":\"Action, Adventure\",\"Director\":\"James Cameron\",\"Writer\":\"James Cameron\",\"Actors\":\"Sam Worthington, Zoe Saldana\",\"Plot\":\"A marine visits Pandora.\",\"Language\":\"English, Spanish\",\"Poster\":\"https://images.example/avatar.jpg\",\"imdbRating\":\"7.9\",\"imdbVotes\":\"1,400,000\",\"imdbID\":\"tt0499549\",\"Type\":\"movie\",\"Response\":\"True\"}");
		OMDbClient client = new OMDbClient(api);

		MovieInfo movie = client.getMovieInfo(new Movie(499549));

		assertEquals("Avatar", movie.getName());
		assertEquals("2009-12-18", movie.getReleased().toString());
		assertEquals("PG-13", movie.getCertification());
		assertEquals("James Cameron", movie.getDirector());
		assertEquals("Sam Worthington", movie.getActors().get(0));
		assertEquals("162", movie.getRuntime().toString());
	}

	private static class FixtureApi implements OMDbApi {

		private final Object response;
		private Map<String, Object> parameters = new LinkedHashMap<String, Object>();

		FixtureApi(String response) throws Exception {
			this.response = readJson(response);
		}

		@Override
		public Object request(Map<String, Object> parameters) {
			this.parameters = new LinkedHashMap<String, Object>(parameters);
			return response;
		}
	}
}
