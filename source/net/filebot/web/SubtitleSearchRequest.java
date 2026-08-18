package net.filebot.web;

import static net.filebot.util.StringUtilities.*;

import java.util.LinkedHashMap;

public final class SubtitleSearchRequest extends LinkedHashMap<String, Object> {

	private SubtitleSearchRequest(String... languageCodes) {
		put("languages", join(languageCodes, ","));
	}

	public static SubtitleSearchRequest forVideoHash(String videoHash, long videoSize, String... languageCodes) {
		SubtitleSearchRequest request = new SubtitleSearchRequest(languageCodes);
		request.put("moviehash", videoHash);
		request.put("moviebytesize", Long.toString(videoSize));
		return request;
	}

	public static SubtitleSearchRequest forName(String name, String... languageCodes) {
		SubtitleSearchRequest request = new SubtitleSearchRequest(languageCodes);
		request.put("query", name);
		return request;
	}

	public static SubtitleSearchRequest forImdbId(int imdbId, int season, int episode, String... languageCodes) {
		SubtitleSearchRequest request = new SubtitleSearchRequest(languageCodes);
		request.put("imdb_id", Integer.toString(imdbId));
		if (season >= 0) {
			request.put("season_number", Integer.toString(season));
		}
		if (episode >= 0) {
			request.put("episode_number", Integer.toString(episode));
		}
		return request;
	}
}
