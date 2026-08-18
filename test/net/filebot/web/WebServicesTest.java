package net.filebot.web;

import static org.junit.Assert.*;

import org.junit.Test;

import net.filebot.WebServices;

public class WebServicesTest {

	@Test
	public void tmdbTvIsDefaultEpisodeProvider() {
		EpisodeListProvider[] providers = WebServices.getEpisodeListProviders();

		assertTrue(providers.length > 0);
		assertSame(WebServices.TheMovieDB_TV, providers[0]);
	}
}
