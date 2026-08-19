package net.filebot.web;

import java.time.Duration;
import java.util.Locale;

public interface TheTVDBApi {

	Object searchSeries(String query, Locale locale, Duration expirationTime) throws Exception;

	Object searchSeriesByRemoteId(String remoteId, Locale locale, Duration expirationTime) throws Exception;

	Object getSeries(int id, Locale locale, Duration expirationTime) throws Exception;

	Object getSeriesEpisodes(int id, String seasonType, Locale locale, int page, Duration expirationTime) throws Exception;

	Object getEpisode(int id, Locale locale, Duration expirationTime) throws Exception;

	Object getLanguages(Duration expirationTime) throws Exception;

	Object getArtworkTypes(Duration expirationTime) throws Exception;
}
