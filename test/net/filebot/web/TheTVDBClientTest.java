package net.filebot.web;

import static net.filebot.util.JsonUtilities.*;
import static org.junit.Assert.*;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

import org.junit.Test;

public class TheTVDBClientTest {

	private final FixtureApi api = new FixtureApi();
	private final TheTVDBClient client = new TheTVDBClient(api);

	@Test
	public void searchMapsV4SeriesResults() throws Exception {
		List<SearchResult> results = client.fetchSearchResult("Fixture Show", Locale.GERMAN);
		assertEquals(1, results.size());
		assertEquals(991001, results.get(0).getId());
		assertEquals("Fixture Show", results.get(0).getName());
		assertArrayEquals(new String[] { "Fixture Alias" }, results.get(0).getAliasNames());
		assertEquals("Fixture Show", api.query);
		assertEquals(Locale.GERMAN, api.locale);
	}

	@Test
	public void seriesMetadataMapsV4ExtendedRecord() throws Exception {
		TheTVDBSeriesInfo info = client.getSeriesInfo(new SearchResult(991001, "Fixture Show", new String[] { "Search Alias" }), Locale.ENGLISH);
		assertEquals("Fixture Show", info.getName());
		assertEquals("TV-14", info.getCertification());
		assertEquals("Fixture Network", info.getNetwork());
		assertEquals("Continuing", info.getStatus());
		assertEquals(Integer.valueOf(45), info.getRuntime());
		assertEquals("Drama", info.getGenres().get(0));
		assertEquals("2020-01-02", info.getStartDate().toString());
		assertEquals("tt1234567", info.getImdbId());
		assertEquals("Fixture overview", info.getOverview());
		assertEquals("Friday", info.getAirsDayOfWeek());
		assertEquals("20:00", info.getAirsTime());
		assertEquals("https://artworks.thetvdb.com/banners/fixture.jpg", info.getBannerUrl().toString());
		assertTrue(info.getAliasNames().contains("Search Alias"));
		assertTrue(info.getAliasNames().contains("Extended Alias"));

		TheTVDBSeriesInfo german = client.getSeriesInfo(991001, Locale.GERMAN);
		assertEquals("Beispielserie", german.getName());
		assertEquals("Deutsche Beschreibung", german.getOverview());
	}

	@Test
	public void episodeOrdersMapWithoutLeakingIntoCallers() throws Exception {
		List<Episode> episodes = client.fetchSeriesData(new SearchResult(991001), SortOrder.DVD, Locale.ENGLISH).getEpisodeList();
		assertEquals("dvd", api.seasonType);
		assertEquals(2, episodes.size());

		Episode first = episodes.get(0);
		assertEquals("Fixture Show", first.getSeriesName());
		assertEquals(Integer.valueOf(1), first.getSeason());
		assertEquals(Integer.valueOf(1), first.getEpisode());
		assertEquals(Integer.valueOf(7), first.getAbsolute());
		assertEquals("DVD Premiere", first.getTitle());

		Episode special = episodes.get(1);
		assertNull(special.getSeason());
		assertNull(special.getEpisode());
		assertEquals(Integer.valueOf(1), special.getSpecial());
	}

	@Test
	public void remoteLookupArtworkAndCreditsMapV4Records() throws Exception {
		SearchResult result = client.lookupByIMDbID(1234567, Locale.ENGLISH);
		assertEquals(991001, result.getId());
		assertEquals("tt1234567", api.remoteId);

		Artwork artwork = client.getArtwork(991001, "fanart", Locale.ENGLISH).get(0);
		assertEquals("https://artworks.thetvdb.com/banners/background.jpg", artwork.getUrl().toString());
		assertTrue(artwork.matches("fanart", "1920x1080"));

		Person actor = client.getActors(991001, Locale.ENGLISH).get(0);
		assertEquals("Example Performer", actor.getName());
		assertEquals("Example Character", actor.getCharacter());
		assertEquals(Person.ACTOR, actor.getJob());

		EpisodeInfo episode = client.getEpisodeInfo(992001, Locale.ENGLISH);
		assertEquals(Integer.valueOf(991001), episode.getSeriesId());
		assertEquals("Episode overview", episode.getOverview());
		assertEquals("Example Director", episode.getDirectors().get(0));
	}

	private static class FixtureApi implements TheTVDBApi {

		private String query;
		private String remoteId;
		private String seasonType;
		private Locale locale;

		@Override
		public Object searchSeries(String query, Locale locale, Duration expirationTime) {
			this.query = query;
			this.locale = locale;
			return json("{\"data\":[{\"type\":\"series\",\"tvdb_id\":\"991001\",\"name_translated\":\"Fixture Show\",\"aliases\":[\"Fixture Alias\"]},{\"type\":\"movie\",\"tvdb_id\":\"1\",\"name\":\"Ignore Me\"}]}");
		}

		@Override
		public Object searchSeriesByRemoteId(String remoteId, Locale locale, Duration expirationTime) {
			this.remoteId = remoteId;
			return json("{\"data\":[{\"type\":\"series\",\"tvdb_id\":\"991001\",\"name\":\"Fixture Show\"}]}");
		}

		@Override
		public Object getSeries(int id, Locale locale, Duration expirationTime) {
			return json("{\"data\":{\"id\":991001,\"name\":\"Fixture Show\",\"aliases\":[{\"name\":\"Extended Alias\"}],\"averageRuntime\":45,\"firstAired\":\"2020-01-02\",\"overview\":\"Fixture overview\",\"airsTime\":\"20:00\",\"airsDays\":{\"friday\":true},\"image\":\"/banners/fixture.jpg\",\"lastUpdated\":\"2024-01-02T03:04:05Z\",\"status\":{\"name\":\"Continuing\"},\"originalNetwork\":{\"name\":\"Fixture Network\"},\"genres\":[{\"name\":\"Drama\"}],\"contentRatings\":[{\"name\":\"TV-14\"}],\"remoteIds\":[{\"id\":\"tt1234567\",\"sourceName\":\"IMDB\"}],\"translations\":{\"nameTranslations\":[{\"language\":\"deu\",\"name\":\"Beispielserie\"}],\"overviewTranslations\":[{\"language\":\"deu\",\"overview\":\"Deutsche Beschreibung\"}]},\"artworks\":[{\"type\":3,\"image\":\"/banners/background.jpg\",\"width\":1920,\"height\":1080,\"language\":\"eng\",\"score\":9.5}],\"characters\":[{\"peopleType\":\"Actor\",\"personName\":\"Example Performer\",\"name\":\"Example Character\",\"sort\":1,\"personImgURL\":\"/people/example.jpg\"}]}}");
		}

		@Override
		public Object getSeriesEpisodes(int id, String seasonType, Locale locale, int page, Duration expirationTime) {
			this.seasonType = seasonType;
			return json("{\"data\":{\"episodes\":[{\"id\":992001,\"name\":\"DVD Premiere\",\"seasonNumber\":1,\"number\":1,\"absoluteNumber\":7,\"aired\":\"2020-01-02\"},{\"id\":992000,\"name\":\"Special\",\"seasonNumber\":0,\"number\":1}]},\"links\":{\"next\":null}}");
		}

		@Override
		public Object getEpisode(int id, Locale locale, Duration expirationTime) {
			return json("{\"data\":{\"id\":992001,\"seriesId\":991001,\"overview\":\"Episode overview\",\"characters\":[{\"peopleType\":\"Director\",\"personName\":\"Example Director\",\"sort\":1}]}}");
		}

		@Override
		public Object getLanguages(Duration expirationTime) {
			return json("{\"data\":[{\"id\":\"eng\",\"shortCode\":\"en\"}]}");
		}

		@Override
		public Object getArtworkTypes(Duration expirationTime) {
			return json("{\"data\":[{\"id\":3,\"name\":\"Series Background\",\"slug\":\"series-background\"}]}");
		}

		private static Object json(String value) {
			return readJson(value);
		}
	}
}
