package net.filebot.web;

import static net.filebot.util.JsonUtilities.*;
import static org.junit.Assert.*;

import java.util.List;
import java.util.Locale;

import org.junit.Test;

public class TMDbTVClientTest {

	@Test
	public void searchUsesSharedAdapter() throws Exception {
		TMDbClientTest.FixtureApi api = new TMDbClientTest.FixtureApi();
		api.response = readJson("{\"results\":[{\"id\":3137,\"name\":\"Babylon 5\",\"original_name\":\"Babylon 5\"}]}");
		TMDbTVClient client = new TMDbTVClient(new TMDbClient(api, false));

		List<SearchResult> results = client.searchTV("babylon 5", -1, Locale.ENGLISH, false);

		assertEquals("search/tv", api.resource);
		assertEquals("babylon 5", api.parameters.get("query"));
		assertEquals(1, results.size());
		assertEquals("Babylon 5", results.get(0).getName());
		assertEquals(3137, results.get(0).getId());
	}

	@Test
	public void seriesAndEpisodesAreMappedFromFixtures() throws Exception {
		TMDbClientTest.FixtureApi api = new TMDbClientTest.FixtureApi();
		api.responses.put("tv/95", readJson("{\"name\":\"Buffy the Vampire Slayer\",\"original_name\":\"Buffy the Vampire Slayer\",\"status\":\"Ended\",\"original_language\":\"en\",\"first_air_date\":\"1997-03-10\",\"vote_average\":8.1,\"vote_count\":1500,\"episode_run_time\":[42],\"genres\":[{\"name\":\"Drama\"}],\"networks\":[{\"name\":\"The WB\"}],\"seasons\":[{\"season_number\":0},{\"season_number\":1}]}"));
		api.responses.put("tv/95/season/0", readJson("{\"episodes\":[{\"id\":900,\"episode_number\":1,\"season_number\":0,\"name\":\"Unaired Pilot\",\"air_date\":null}]}"));
		api.responses.put("tv/95/season/1", readJson("{\"episodes\":[{\"id\":901,\"episode_number\":1,\"season_number\":1,\"name\":\"Welcome to the Hellmouth\",\"air_date\":\"1997-03-10\"}]}"));
		TMDbTVClient client = new TMDbTVClient(new TMDbClient(api, false));

		AbstractEpisodeListProvider.SeriesData data = client.fetchSeriesData(new SearchResult(95, "Buffy the Vampire Slayer"), SortOrder.Airdate, Locale.ENGLISH);
		List<Episode> episodes = data.getEpisodeList();

		assertEquals(2, episodes.size());
		assertEquals("Welcome to the Hellmouth", episodes.get(0).getTitle());
		assertEquals(Integer.valueOf(1), episodes.get(0).getAbsolute());
		assertEquals("Unaired Pilot", episodes.get(1).getTitle());
		assertEquals(Integer.valueOf(1), episodes.get(1).getSpecial());
		assertEquals("The WB", data.getSeriesInfo().getNetwork());
		assertEquals("1997-03-10", data.getSeriesInfo().getStartDate().toString());
	}
}
