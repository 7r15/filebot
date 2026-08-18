package net.filebot.web;

import static java.util.Arrays.*;
import static java.util.Collections.*;
import static java.util.stream.Collectors.*;
import static net.filebot.Logging.*;
import static net.filebot.util.FileUtilities.*;
import static net.filebot.util.JsonUtilities.*;
import static net.filebot.web.OpenSubtitlesHasher.*;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Level;

import javax.swing.Icon;

import net.filebot.Cache;
import net.filebot.Cache.TypedCache;
import net.filebot.CacheType;
import net.filebot.ResourceManager;
import net.filebot.media.MediaDetection;

/**
 * SubtitleClient for OpenSubtitles.
 */
public class OpenSubtitlesClient implements SubtitleProvider, VideoHashSubtitleService, MovieIdentificationService {

	private final OpenSubtitlesRestApi restApi;

	public OpenSubtitlesClient(String apiKey, String applicationName, String version) {
		this.restApi = new OpenSubtitlesRestApi(apiKey, String.format("%s v%s", applicationName, version));
	}

	@Override
	public String getIdentifier() {
		return "OpenSubtitles";
	}

	@Override
	public Icon getIcon() {
		return ResourceManager.getIcon("search.opensubtitles");
	}

	@Override
	public URI getLink() {
		return URI.create("https://www.opensubtitles.com");
	}

	public synchronized void setUser(String username, String password) {
		restApi.setUser(username, password);
	}

	public boolean isAnonymous() {
		return restApi.isAnonymous();
	}

	@Override
	public List<SubtitleSearchResult> search(String query) throws Exception {
		return searchIMDB(query);
	}

	@Override
	public List<Movie> searchMovie(String query, Locale locale) throws Exception {
		return new ArrayList<Movie>(restApi.searchFeatures(query));
	}

	@Override
	public synchronized List<SubtitleSearchResult> guess(String tag) throws Exception {
		return getSearchCache("tag").computeIfAbsent(tag, it -> restApi.searchFeatures(it.toString()));
	}

	public synchronized List<SubtitleSearchResult> searchIMDB(String query) throws Exception {
		return getSearchCache("query").computeIfAbsent(query, it -> restApi.searchFeatures(it.toString()));
	}

	public synchronized List<SubtitleDescriptor> getSubtitleList(SubtitleSearchRequest query) throws Exception {
		return new ArrayList<SubtitleDescriptor>(restApi.searchSubtitles(query));
	}

	public List<SubtitleDescriptor> getSubtitleList(SubtitleSearchResult searchResult, Locale locale) throws Exception {
		return getSubtitleList(searchResult, -1, -1, locale);
	}

	@Override
	public List<SubtitleDescriptor> getSubtitleList(SubtitleSearchResult searchResult, int[][] episodeFilter, Locale locale) throws Exception {
		// no filter
		if (episodeFilter == null || episodeFilter.length == 0) {
			return getSubtitleList(searchResult, -1, -1, locale);
		}

		int[] seasons = stream(episodeFilter).mapToInt(ii -> ii[0]).filter(i -> i >= 0).sorted().distinct().toArray();
		int[] episodes = stream(episodeFilter).mapToInt(ii -> ii[1]).filter(i -> i >= 0).sorted().distinct().toArray();

		// no filter
		if (seasons.length == 0 && episodes.length == 0) {
			return getSubtitleList(searchResult, -1, -1, locale);
		}

		// episode filter
		if (seasons.length == 1 && episodes.length == 1) {
			return getSubtitleList(searchResult, seasons[0], episodes[0], locale);
		}

		// season filter
		if (seasons.length > 0 && episodes.length == 0) {
			return stream(seasons).boxed().flatMap(s -> {
				try {
					return getSubtitleList(searchResult, s, -1, locale).stream();
				} catch (Exception e) {
					throw new RuntimeException(String.format("Failed to retrieve subtitle list for season: %s S%02d [%s]", searchResult, s, locale), e);
				}
			}).distinct().collect(toList());
		}

		// multi-episode filter
		return stream(episodeFilter).flatMap(ii -> {
			try {
				return getSubtitleList(searchResult, ii[0], ii[1], locale).stream();
			} catch (Exception e) {
				throw new RuntimeException(String.format("Failed to retrieve subtitle list for episode: %s %s [%s]", searchResult, asList(ii), locale), e);
			}
		}).distinct().collect(toList());
	}

	public synchronized List<SubtitleDescriptor> getSubtitleList(SubtitleSearchResult searchResult, int season, int episode, Locale locale) throws Exception {
		SubtitleSearchRequest query = SubtitleSearchRequest.forImdbId(searchResult.getImdbId(), season, episode, getLanguageFilter(locale));

		return getSubtitleList(query);
	}

	@Override
	public Map<File, List<SubtitleDescriptor>> getSubtitleList(File[] files, Locale locale) throws Exception {
		Map<File, List<SubtitleDescriptor>> results = new HashMap<File, List<SubtitleDescriptor>>(files.length);
		Set<File> remainingFiles = new HashSet<File>(asList(files));

		// lookup subtitles by hash
		if (remainingFiles.size() > 0) {
			results.putAll(getSubtitleListByHash(remainingFiles.toArray(new File[0]), locale));
		}

		// remove files for which subtitles have already been found
		results.forEach((k, v) -> {
			if (v.size() > 0) {
				remainingFiles.remove(k);
			}
		});

		return results;
	}

	protected Map<File, List<SubtitleDescriptor>> getSubtitleList(File[] files, Function<File, SubtitleSearchRequest> queryMapper) throws Exception {
		Map<File, List<SubtitleDescriptor>> results = new HashMap<File, List<SubtitleDescriptor>>(files.length);

		// dispatch query for all hashes
		for (File f : files) {
			SubtitleSearchRequest query = queryMapper.apply(f);
			if (query != null) {
				results.put(f, getSubtitleList(query));
			} else {
				results.put(f, emptyList());
			}
		}

		return results;
	}

	public Map<File, List<SubtitleDescriptor>> getSubtitleListByHash(File[] files, Locale locale) throws Exception {
		return getSubtitleList(files, f -> {
			if (f.length() > HASH_CHUNK_SIZE) {
				try {
					String hash = computeHash(f);
					return SubtitleSearchRequest.forVideoHash(hash, f.length(), getLanguageFilter(locale));
				} catch (Exception e) {
					debug.log(Level.SEVERE, "Failed to compute hash", e);
				}
			} else {
				// debug dummy files, e.g. { "hash":"ca8395374fad4b83", "size":639511378 }
				try {
					Map<?, ?> json = asMap(readJson(readTextFile(f)));
					if (json != null) {
						return SubtitleSearchRequest.forVideoHash(json.get("hash").toString(), Long.parseLong(json.get("size").toString()), getLanguageFilter(locale));
					}
				} catch (Exception e) {
					debug.finest("Ignore sample file: " + f);
				}
			}
			return null;
		});
	}

	public Map<File, List<SubtitleDescriptor>> getSubtitleListByTag(File[] files, Locale locale) throws Exception {
		return getSubtitleList(files, f -> {
			String tag = getNameWithoutExtension(f.getName());
			return SubtitleSearchRequest.forName(tag, getLanguageFilter(locale));
		});
	}

	@Override
	public synchronized CheckResult checkSubtitle(File videoFile, File subtitleFile) throws Exception {
		throw new UnsupportedOperationException("OpenSubtitles REST upload checks are not available");
	}

	@Override
	public synchronized void uploadSubtitle(Object identity, Locale locale, File[] videoFile, File[] subtitleFile) throws Exception {
		throw new UnsupportedOperationException("OpenSubtitles REST uploads are not available");
	}

	@Override
	public synchronized Movie getMovieDescriptor(Movie id, Locale locale) throws Exception {
		if (id.getImdbId() <= 0) {
			throw new IllegalArgumentException("Illegal IMDbID ID: " + id.getImdbId());
		}

		List<OpenSubtitlesSubtitleDescriptor> subtitles = restApi.searchSubtitles(SubtitleSearchRequest.forImdbId(id.getImdbId(), -1, -1));
		return subtitles.isEmpty() ? null : getMovie(subtitles.get(0));
	}

	public Movie getMovieDescriptor(File movieFile, Locale locale) throws Exception {
		return getMovieDescriptors(singleton(movieFile), locale).get(movieFile);
	}

	public synchronized Map<File, Movie> getMovieDescriptors(Collection<File> movieFiles, Locale locale) throws Exception {
		// create result array
		Map<File, Movie> results = new HashMap<File, Movie>();

		for (File f : movieFiles) {
			if (f.length() > HASH_CHUNK_SIZE) {
				String hash = computeHash(f);

				List<OpenSubtitlesSubtitleDescriptor> subtitles = restApi.searchSubtitles(SubtitleSearchRequest.forVideoHash(hash, f.length()));
				Movie match = subtitles.isEmpty() ? null : getMovie(subtitles.get(0));

				results.put(f, match);
			}
		}

		return results;
	}

	@Override
	public URI getSubtitleListLink(SubtitleSearchResult searchResult, Locale locale) {
		return URI.create(String.format("https://www.opensubtitles.com/en/search/sublanguageid-%s/imdbid-%d", getSubLanguageID(locale), searchResult.getImdbId()));
	}

	public synchronized Locale detectLanguage(byte[] data) throws Exception {
		throw new UnsupportedOperationException("OpenSubtitles REST language detection is not available");
	}

	public synchronized void login() throws Exception {
		restApi.login();
	}

	public synchronized void logout() {
		restApi.logout();
	}

	public synchronized Map<?, ?> getServerInfo() throws Exception {
		return restApi.getUserInfo();
	}

	public Map<?, ?> getDownloadLimits() throws Exception {
		return getMap(getServerInfo(), "data");
	}

	private Movie getMovie(OpenSubtitlesSubtitleDescriptor subtitle) {
		try {
			String name = subtitle.getProperty(OpenSubtitlesSubtitleDescriptor.Property.MovieName);
			int year = Integer.parseInt(subtitle.getProperty(OpenSubtitlesSubtitleDescriptor.Property.MovieYear));
			int imdbId = Integer.parseInt(subtitle.getProperty(OpenSubtitlesSubtitleDescriptor.Property.IDMovieImdb));
			return new Movie(name, year, imdbId);
		} catch (Exception e) {
			return null;
		}
	}

	/**
	 * SubLanguageID by English language name
	 */
	protected synchronized Map<String, String> getSubLanguageMap() throws Exception {
		Map<String, String> subLanguageMap = new HashMap<String, String>();

		// try to get language map from cache
		Cache cache = Cache.getCache(getName() + "_languages", CacheType.Persistent);
		Map<?, ?> m = (Map<?, ?>) cache.computeIfAbsent("subLanguageMap", k -> restApi.getLanguages());

		// add additional language aliases for improved compatibility
		Map<String, Locale> additionalLanguageMappings = MediaDetection.releaseInfo.getLanguageMap(Locale.ENGLISH);

		m.forEach((k, v) -> {
			// map id by name
			String subLanguageID = k.toString().toLowerCase();
			String languageCode = v.toString().toLowerCase();

			subLanguageMap.put(languageCode, subLanguageID);
			subLanguageMap.put(subLanguageID, subLanguageID); // add reverse mapping as well for improved compatibility

			// add additional language aliases for improved compatibility
			for (String key : new String[] { subLanguageID, languageCode }) {
				Locale locale = additionalLanguageMappings.get(key);
				if (locale != null) {
					for (String identifier : asList(locale.getLanguage(), locale.getISO3Language(), locale.getDisplayLanguage(Locale.ENGLISH))) {
						if (identifier != null && identifier.length() > 0 && !subLanguageMap.containsKey(identifier.toLowerCase())) {
							subLanguageMap.put(identifier.toLowerCase(), subLanguageID);
						}
					}
				}
			}
		});

		return subLanguageMap;
	}

	protected String getSubLanguageID(Locale locale) {
		if (locale == null || locale.equals(Locale.ROOT)) {
			return "all";
		}

		// some special handling
		switch (locale.toString()) {
		case "pt_BR":
			return "pt-br"; // Brazilian Portuguese
		case "zh_CN":
			return "zh-cn"; // Chinese (Simplified)
		case "zh_TW":
			return "zh-tw"; // Chinese (Traditional)
		case "iw_IL":
			return "he"; // Hebrew
		}

		Map<String, String> languageMap;
		try {
			languageMap = getSubLanguageMap();
		} catch (Exception e) {
			throw new IllegalStateException("Failed to retrieve subtitle language map", e);
		}

		String subLanguageID = languageMap.get(locale.getLanguage());
		if (subLanguageID == null) {
			throw new IllegalArgumentException("SubLanguageID not found: " + locale);
		}

		return subLanguageID;
	}

	protected String[] getLanguageFilter(Locale locale) {
		return locale == null || locale.getLanguage().isEmpty() ? new String[0] : new String[] { getSubLanguageID(locale) };
	}

	public Cache getCache(String section) {
		return Cache.getCache(getName() + "_" + section, CacheType.Daily);
	}

	protected TypedCache<List<SubtitleSearchResult>> getSearchCache(String method) {
		return getCache("search_" + method).castList(SubtitleSearchResult.class);
	}

}
