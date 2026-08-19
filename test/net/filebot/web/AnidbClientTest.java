package net.filebot.web;

import static java.nio.charset.StandardCharsets.*;
import static net.filebot.web.WebRequest.*;
import static org.junit.Assert.*;

import java.util.List;
import java.util.Locale;

import org.junit.Test;
import org.w3c.dom.Document;

public class AnidbClientTest {

	private static final String TITLES = "# fixture\n69|1|x-jat|One Piece\n69|4|en|One Piece\n69|2|en|One &amp; Piece\n1539|1|x-jat|Monster\n1539|4|en|Monster\n";
	private static final String ANIME = "<anime id=\"1539\"><type>TV Series</type><startdate>2004-04-07</startdate><titles><title type=\"main\" lang=\"x-jat\">Monster</title><title type=\"official\" lang=\"en\">Monster</title><title type=\"official\" lang=\"ja\">MONSTER</title></titles><ratings><permanent count=\"1000\">8.8</permanent></ratings><categories><category weight=\"600\"><name>Thriller</name></category><category weight=\"300\"><name>Minor</name></category></categories><episodes><episode id=\"17843\"><epno type=\"1\">1</epno><airdate>2004-04-07</airdate><title lang=\"en\">Herr Dr. Tenma</title><title lang=\"ja\">Dr. Tenma</title></episode><episode id=\"17844\"><epno type=\"2\">S1</epno><airdate>2004-04-08</airdate><title lang=\"en\">Interview</title></episode></episodes></anime>";

	@Test
	public void titleDumpBuildsLocalSearchIndex() throws Exception {
		AnidbClient client = new AnidbClient(new FixtureApi());

		SearchResult[] titles = client.getAnimeTitles();
		List<SearchResult> results = client.search("one piece", Locale.ENGLISH);

		assertEquals(2, titles.length);
		assertEquals(69, results.get(0).getId());
		assertEquals("One Piece", results.get(0).getName());
		assertArrayEquals(new String[] { "One & Piece" }, results.get(0).getAliasNames());
	}

	@Test
	public void animeFixtureMapsSeriesAndEpisodes() throws Exception {
		AnidbClient client = new AnidbClient(new FixtureApi());

		AbstractEpisodeListProvider.SeriesData data = client.fetchSeriesData(new SearchResult(1539, "Monster"), SortOrder.Absolute, Locale.ENGLISH);
		List<Episode> episodes = data.getEpisodeList();

		assertEquals("Monster", data.getSeriesInfo().getName());
		assertEquals("2004-04-07", data.getSeriesInfo().getStartDate().toString());
		assertEquals("Thriller", data.getSeriesInfo().getGenres().get(0));
		assertEquals(2, episodes.size());
		assertEquals("Herr Dr. Tenma", episodes.get(0).getTitle());
		assertEquals(Integer.valueOf(1), episodes.get(0).getAbsolute());
		assertEquals("Interview", episodes.get(1).getTitle());
		assertEquals(Integer.valueOf(1), episodes.get(1).getSpecial());
	}

	private static class FixtureApi implements AniDBApi {

		private final Document anime;

		FixtureApi() throws Exception {
			anime = getDocument(ANIME);
		}

		@Override
		public Document getAnime(int id) {
			return anime;
		}

		@Override
		public byte[] getAnimeTitles() {
			return TITLES.getBytes(UTF_8);
		}
	}
}
