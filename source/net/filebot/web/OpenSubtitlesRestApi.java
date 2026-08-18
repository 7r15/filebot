package net.filebot.web;

import static java.nio.charset.StandardCharsets.*;
import static java.util.Collections.*;
import static java.util.stream.Collectors.*;
import static net.filebot.util.FileUtilities.*;
import static net.filebot.util.JsonUtilities.*;
import static net.filebot.web.WebRequest.*;

import java.net.URL;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import net.filebot.web.OpenSubtitlesSubtitleDescriptor.Property;

public class OpenSubtitlesRestApi {

	private final String apiKey;
	private final String userAgent;
	private final String endpoint;

	private String username = "";
	private String password = "";
	private String token;
	private Instant tokenExpiration;

	public OpenSubtitlesRestApi(String apiKey, String userAgent) {
		this(apiKey, userAgent, System.getProperty("net.filebot.OpenSubtitlesRestApi.url", "https://api.opensubtitles.com/api/v1/"));
	}

	OpenSubtitlesRestApi(String apiKey, String userAgent, String endpoint) {
		this.apiKey = apiKey == null ? "" : apiKey.trim();
		this.userAgent = userAgent;
		this.endpoint = endpoint.endsWith("/") ? endpoint : endpoint + "/";
	}

	public synchronized void setUser(String username, String password) {
		logout();
		this.username = username == null ? "" : username;
		this.password = password == null ? "" : password;
	}

	public synchronized boolean isAnonymous() {
		return username.isEmpty() || password.isEmpty();
	}

	public synchronized void login() throws Exception {
		if (isAnonymous()) {
			throw new IllegalStateException("OpenSubtitles login is required for downloads");
		}

		if (token == null || tokenExpiration == null || Instant.now().isAfter(tokenExpiration)) {
			Map<String, String> credentials = new LinkedHashMap<String, String>();
			credentials.put("username", username);
			credentials.put("password", password);

			Object response = requestJson("login", credentials, true);
			token = string(response, "token");
			if (token == null) {
				throw new IllegalStateException("OpenSubtitles login did not return an authentication token");
			}
			tokenExpiration = Instant.now().plus(Duration.ofHours(23));
		}
	}

	public synchronized void logout() {
		token = null;
		tokenExpiration = null;
	}

	public List<OpenSubtitlesSubtitleDescriptor> searchSubtitles(Map<String, ?> query) throws Exception {
		Map<String, Object> parameters = getSearchParameters(query);
		Object response = requestJson("subtitles?" + encodeParameters(parameters, true));
		return parseSubtitles(response, query);
	}

	public List<SubtitleSearchResult> searchFeatures(String query) throws Exception {
		Object response = requestJson("subtitles?" + encodeParameters(singletonMap("query", query), true));
		return parseFeatures(response);
	}

	List<SubtitleSearchResult> parseFeatures(Object response) {
		Map<Integer, SubtitleSearchResult> features = new LinkedHashMap<Integer, SubtitleSearchResult>();
		for (Map<?, ?> item : getMapArray(response, "data")) {
			Map<?, ?> details = getMap(getMap(item, "attributes"), "feature_details");
			Integer imdbId = integer(details, "imdb_id");
			String name = first(string(details, "movie_name"), string(details, "title"));
			if (imdbId != null && imdbId > 0 && name != null) {
				Integer year = integer(details, "year");
				String kind = getFeatureKind(string(details, "feature_type"));
				features.putIfAbsent(imdbId, new SubtitleSearchResult(imdbId, name, year == null ? -1 : year, kind, -1));
			}
		}

		return new ArrayList<SubtitleSearchResult>(features.values());
	}

	public ByteBuffer download(String fileId) throws Exception {
		login();

		Object response = requestJson("download", singletonMap("file_id", fileId), true);
		String link = string(response, "link");
		if (link == null) {
			throw new IllegalStateException("OpenSubtitles download did not return a link");
		}

		return fetch(new URL(link));
	}

	public Map<?, ?> getUserInfo() throws Exception {
		login();
		return asMap(requestJson("infos/user", true));
	}

	public Map<String, String> getLanguages() throws Exception {
		Object response = requestJson("infos/languages");
		return streamJsonObjects(response, "data").filter(it -> string(it, "language_code") != null && string(it, "language_name") != null).collect(toMap(it -> string(it, "language_code"), it -> string(it, "language_name"), (a, b) -> a, LinkedHashMap::new));
	}

	Map<String, Object> getSearchParameters(Map<String, ?> query) {
		Map<String, Object> parameters = new LinkedHashMap<String, Object>();
		copy(query, parameters, "moviehash", "moviehash");
		copy(query, parameters, "imdb_id", "imdb_id");
		copy(query, parameters, "query", "query");
		copy(query, parameters, "season_number", "season_number");
		copy(query, parameters, "episode_number", "episode_number");
		copy(query, parameters, "languages", "languages");
		return parameters;
	}

	List<OpenSubtitlesSubtitleDescriptor> parseSubtitles(Object response, Map<String, ?> query) {
		List<OpenSubtitlesSubtitleDescriptor> subtitles = new ArrayList<OpenSubtitlesSubtitleDescriptor>();

		for (Map<?, ?> item : getMapArray(response, "data")) {
			Map<?, ?> attributes = getMap(item, "attributes");
			Map<?, ?> details = getMap(attributes, "feature_details");
			Map<?, ?>[] files = getMapArray(attributes, "files");
			if (files.length == 0) {
				continue;
			}

			Map<?, ?> file = files[0];
			String fileId = string(file, "file_id");
			String fileName = first(string(file, "file_name"), string(attributes, "release"));
			if (fileId == null || fileName == null) {
				continue;
			}

			String languageCode = string(attributes, "language");
			EnumMap<Property, String> properties = new EnumMap<Property, String>(Property.class);
			put(properties, Property.IDSubtitle, first(string(attributes, "subtitle_id"), string(item, "id")));
			put(properties, Property.IDSubtitleFile, fileId);
			put(properties, Property.SubFileName, fileName);
			put(properties, Property.SubFormat, first(getExtension(fileName), "srt"));
			put(properties, Property.SubSize, "0");
			put(properties, Property.SubLanguageID, languageCode);
			put(properties, Property.ISO639, languageCode);
			put(properties, Property.LanguageName, getLanguageName(languageCode));
			put(properties, Property.SubDownloadsCnt, string(attributes, "download_count"));
			put(properties, Property.SubRating, string(attributes, "ratings"));
			put(properties, Property.SubAddDate, string(attributes, "upload_date"));
			put(properties, Property.SubAuthorComment, string(attributes, "comments"));
			put(properties, Property.SubFeatured, flag(attributes, "from_trusted"));
			put(properties, Property.SubHearingImpaired, flag(attributes, "hearing_impaired"));
			put(properties, Property.SubHD, flag(attributes, "hd"));
			put(properties, Property.MovieFPS, string(attributes, "fps"));
			put(properties, Property.MovieReleaseName, first(string(attributes, "release"), fileName));
			put(properties, Property.SubtitlesLink, string(attributes, "url"));
			put(properties, Property.MovieName, first(string(details, "movie_name"), string(details, "title")));
			put(properties, Property.MovieYear, string(details, "year"));
			put(properties, Property.IDMovieImdb, string(details, "imdb_id"));
			put(properties, Property.MovieKind, string(details, "feature_type"));
			put(properties, Property.SeriesSeason, string(details, "season_number"));
			put(properties, Property.SeriesEpisode, string(details, "episode_number"));
			put(properties, Property.SeriesIMDBParent, string(details, "parent_imdb_id"));
			put(properties, Property.MovieHash, string(query, "moviehash"));
			put(properties, Property.MovieByteSize, string(query, "moviebytesize"));
			put(properties, Property.MatchedBy, Boolean.TRUE.equals(attributes.get("moviehash_match")) ? "moviehash" : "metadata");
			put(properties, Property.QueryNumber, "0");
			put(properties, Property.SubActualCD, "1");
			put(properties, Property.SubSumCD, first(string(attributes, "nb_cd"), "1"));

			subtitles.add(new OpenSubtitlesSubtitleDescriptor(properties, this, fileId));
		}

		return subtitles;
	}

	protected Object requestJson(String path) throws Exception {
		ByteBuffer response = fetch(new URL(endpoint + path), 0, null, getHeaders(false), null);
		return readJson(UTF_8.decode(response));
	}

	protected Object requestJson(String path, boolean authenticated) throws Exception {
		if (authenticated) {
			login();
		}
		ByteBuffer response = fetch(new URL(endpoint + path), 0, null, getHeaders(authenticated), null);
		return readJson(UTF_8.decode(response));
	}

	protected Object requestJson(String path, Object body, boolean authenticated) throws Exception {
		if (authenticated && !path.equals("login")) {
			login();
		}
		ByteBuffer response = post(new URL(endpoint + path), json(body, false).getBytes(UTF_8), "application/json", getHeaders(authenticated && token != null));
		return readJson(UTF_8.decode(response));
	}

	private Map<String, String> getHeaders(boolean authenticated) {
		if (apiKey.isEmpty()) {
			throw new IllegalStateException("OpenSubtitles API key is not configured; set FILEBOT_APIKEY_OPENSUBTITLES or net.filebot.apikey.opensubtitles");
		}

		Map<String, String> headers = new LinkedHashMap<String, String>();
		headers.put("Accept", "application/json");
		headers.put("Api-Key", apiKey);
		headers.put("User-Agent", userAgent);
		if (authenticated && token != null) {
			headers.put("Authorization", "Bearer " + token);
		}
		return headers;
	}

	private static void copy(Map<String, ?> source, Map<String, Object> target, String sourceKey, String targetKey) {
		Object value = source.get(sourceKey);
		if (value != null && !value.toString().isEmpty()) {
			target.put(targetKey, value);
		}
	}

	private static void put(Map<Property, String> properties, Property key, String value) {
		if (value != null && !value.isEmpty()) {
			properties.put(key, value);
		}
	}

	private static String string(Object node, String key) {
		Object value = asMap(node).get(key);
		return value == null ? null : value.toString();
	}

	private static Integer integer(Object node, String key) {
		try {
			String value = string(node, key);
			return value == null ? null : Integer.valueOf(value);
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static String flag(Object node, String key) {
		return Boolean.TRUE.equals(asMap(node).get(key)) ? "1" : "0";
	}

	private static String first(String... values) {
		for (String value : values) {
			if (value != null && !value.isEmpty()) {
				return value;
			}
		}
		return null;
	}

	private static String getLanguageName(String code) {
		if (code == null || code.isEmpty()) {
			return null;
		}
		Locale locale = Locale.forLanguageTag(code.replace('_', '-'));
		String name = locale.getDisplayLanguage(Locale.ENGLISH);
		return name.isEmpty() ? code : name;
	}

	private static String getFeatureKind(String type) {
		if (type != null && (type.equalsIgnoreCase("episode") || type.equalsIgnoreCase("tvshow") || type.equalsIgnoreCase("series"))) {
			return "tv series";
		}
		return type;
	}
}
