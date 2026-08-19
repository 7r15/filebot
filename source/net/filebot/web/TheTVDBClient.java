package net.filebot.web;

import static java.util.Arrays.*;
import static java.util.stream.Collectors.*;
import static net.filebot.Logging.*;
import static net.filebot.Settings.*;
import static net.filebot.util.JsonUtilities.*;
import static net.filebot.web.EpisodeUtilities.*;

import java.net.URI;
import java.net.URL;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import javax.swing.Icon;

import net.filebot.Cache;
import net.filebot.ResourceManager;

public class TheTVDBClient extends AbstractEpisodeListProvider implements ArtworkProvider {

	private static final Locale DEFAULT_LOCALE = Locale.ENGLISH;

	private final TheTVDBApi api;

	public TheTVDBClient(String apiKey) {
		this(new TheTVDBV4Api(apiKey, getApiKey("thetvdb.pin")));
	}

	TheTVDBClient(TheTVDBApi api) {
		this.api = api;
	}

	@Override
	public String getIdentifier() {
		return "TheTVDB";
	}

	@Override
	public Icon getIcon() {
		return ResourceManager.getIcon("search.thetvdb");
	}

	@Override
	public boolean hasSeasonSupport() {
		return true;
	}

	@Override
	protected List<SearchResult> fetchSearchResult(String query, Locale locale) throws Exception {
		return parseSearchResults(api.searchSeries(query, locale, Cache.ONE_DAY));
	}

	private List<SearchResult> parseSearchResults(Object response) {
		return streamJsonObjects(response, "data").filter(it -> "series".equalsIgnoreCase(getString(it, "type"))).map(it -> {
			Integer id = integer(first(getString(it, "tvdb_id"), getString(it, "id")));
			String name = first(getString(it, "name_translated"), getString(it, "name"), getString(it, "title"));
			String[] aliases = strings(getArray(it, "aliases"), "name");

			if (id == null || name == null || name.startsWith("**") || name.endsWith("**")) {
				debug.warning(String.format("Ignore invalid series: %s [%s]", name, id));
				return null;
			}

			return new SearchResult(id, name, aliases);
		}).filter(Objects::nonNull).collect(toList());
	}

	@Override
	public TheTVDBSeriesInfo getSeriesInfo(int id, Locale language) throws Exception {
		return getSeriesInfo(new SearchResult(id), language);
	}

	@Override
	public TheTVDBSeriesInfo getSeriesInfo(SearchResult series, Locale locale) throws Exception {
		Map<?, ?> data = getMap(api.getSeries(series.getId(), locale, Cache.ONE_WEEK), "data");

		TheTVDBSeriesInfo info = new TheTVDBSeriesInfo(this, locale, series.getId());
		Set<String> aliases = new LinkedHashSet<String>(asList(series.getAliasNames()));
		aliases.addAll(asList(strings(getArray(data, "aliases"), "name")));
		info.setAliasNames(aliases.toArray(new String[aliases.size()]));

		String language = TheTVDBV4Api.getLanguageCode(locale);
		Map<?, ?> translations = getMap(data, "translations");
		info.setName(first(translation(translations, "nameTranslations", language, "name"), getString(data, "name")));
		info.setCertification(firstString(getArray(data, "contentRatings"), "name"));
		info.setNetwork(first(getString(getMap(data, "originalNetwork"), "name"), getString(getMap(data, "latestNetwork"), "name")));
		info.setStatus(getString(getMap(data, "status"), "name"));
		info.setRuntime(getInteger(data, "averageRuntime"));
		info.setGenres(streamJsonObjects(data, "genres").map(it -> getString(it, "name")).filter(Objects::nonNull).collect(toList()));
		info.setStartDate(getStringValue(data, "firstAired", SimpleDate::parse));

		info.setImdbId(findRemoteId(data, "imdb"));
		info.setOverview(first(translation(translations, "overviewTranslations", language, "overview"), getString(data, "overview")));
		info.setAirsDayOfWeek(getAirsDay(getMap(data, "airsDays")));
		info.setAirsTime(getString(data, "airsTime"));
		info.setBannerUrl(getStringValue(data, "image", this::resolveImage));
		info.setLastUpdated(parseTimestamp(getString(data, "lastUpdated")));
		return info;
	}

	@Override
	protected SeriesData fetchSeriesData(SearchResult series, SortOrder sortOrder, Locale locale) throws Exception {
		SeriesInfo info = getSeriesInfo(series, locale);
		info.setOrder(sortOrder.name());

		if (info.getName() == null && !locale.equals(DEFAULT_LOCALE)) {
			return fetchSeriesData(series, sortOrder, DEFAULT_LOCALE);
		}

		String seasonType = getSeasonType(sortOrder);
		List<Episode> episodes = new ArrayList<Episode>();
		List<Episode> specials = new ArrayList<Episode>();

		for (int page = 0; page < 1000; page++) {
			Object response;
			try {
				response = api.getSeriesEpisodes(series.getId(), seasonType, locale, page, Cache.ONE_DAY);
			} catch (Exception e) {
				if (!locale.equals(DEFAULT_LOCALE)) {
					return fetchSeriesData(series, sortOrder, DEFAULT_LOCALE);
				}
				throw e;
			}

			Map<?, ?> data = getMap(response, "data");
			Map<?, ?>[] batch = getMapArray(data, "episodes");
			for (Map<?, ?> item : batch) {
				Integer id = getInteger(item, "id");
				String episodeName = getString(item, "name");
				Integer absoluteNumber = getInteger(item, "absoluteNumber");
				SimpleDate airdate = getStringValue(item, "aired", SimpleDate::parse);
				Integer episodeNumber = getInteger(item, "number");
				Integer seasonNumber = getInteger(item, "seasonNumber");

				if (sortOrder == SortOrder.Absolute && absoluteNumber != null && absoluteNumber > 0) {
					seasonNumber = null;
					episodeNumber = absoluteNumber;
				} else if (sortOrder == SortOrder.AbsoluteAirdate && airdate != null) {
					seasonNumber = null;
					episodeNumber = airdate.getYear() * 1_00_00 + airdate.getMonth() * 1_00 + airdate.getDay();
				}

				if (seasonNumber == null || seasonNumber > 0) {
					episodes.add(new Episode(info.getName(), seasonNumber, episodeNumber, episodeName, absoluteNumber, null, airdate, id, new SeriesInfo(info)));
				} else {
					specials.add(new Episode(info.getName(), null, null, episodeName, absoluteNumber, episodeNumber, airdate, id, new SeriesInfo(info)));
				}
			}

			if (batch.length == 0 || getMap(response, "links").get("next") == null) {
				break;
			}
		}

		episodes.sort(episodeComparator());
		episodes.addAll(specials);
		return new SeriesData(info, episodes);
	}

	public SearchResult lookupByID(int id, Locale locale) throws Exception {
		if (id <= 0) {
			throw new IllegalArgumentException("Illegal TheTVDB ID: " + id);
		}
		SeriesInfo info = getSeriesInfo(new SearchResult(id), locale);
		return new SearchResult(id, info.getName(), info.getAliasNames());
	}

	public SearchResult lookupByIMDbID(int imdbid, Locale locale) throws Exception {
		if (imdbid <= 0) {
			throw new IllegalArgumentException("Illegal IMDb ID: " + imdbid);
		}
		List<SearchResult> result = parseSearchResults(api.searchSeriesByRemoteId(String.format("tt%07d", imdbid), locale, Cache.ONE_MONTH));
		return result.isEmpty() ? null : result.get(0);
	}

	@Override
	public URI getEpisodeListLink(SearchResult searchResult) {
		return URI.create("https://thetvdb.com/series/" + searchResult.getId());
	}

	@Override
	public List<Artwork> getArtwork(int id, String category, Locale locale) throws Exception {
		Map<Integer, String> types = new LinkedHashMap<Integer, String>();
		for (Map<?, ?> type : getMapArray(api.getArtworkTypes(Cache.ONE_MONTH), "data")) {
			types.put(getInteger(type, "id"), first(getString(type, "slug"), getString(type, "name")));
		}

		Map<?, ?> data = getMap(api.getSeries(id, locale, Cache.ONE_MONTH), "data");
		return streamJsonObjects(data, "artworks").map(it -> {
			String type = types.get(getInteger(it, "type"));
			if (!matchesArtworkCategory(category, type)) {
				return null;
			}

			URL url = getStringValue(it, "image", this::resolveImage);
			Integer width = getInteger(it, "width");
			Integer height = getInteger(it, "height");
			String resolution = width == null || height == null ? null : width + "x" + height;
			Locale language = locale(getString(it, "language"));
			Double score = getDecimal(it, "score");
			return url == null ? null : new Artwork(Stream.of(category, type, resolution), url, language, score);
		}).filter(Objects::nonNull).sorted(Artwork.RATING_ORDER).collect(toList());
	}

	protected URL resolveImage(String path) {
		if (path == null || path.isEmpty()) {
			return null;
		}
		try {
			if (path.startsWith("http://") || path.startsWith("https://")) {
				return new URL(path);
			}
			return new URL("https://artworks.thetvdb.com" + (path.startsWith("/") ? path : "/" + path));
		} catch (Exception e) {
			throw new IllegalArgumentException(path, e);
		}
	}

	public List<String> getLanguages() throws Exception {
		return streamJsonObjects(api.getLanguages(Cache.ONE_MONTH), "data").map(it -> first(getString(it, "shortCode"), getString(it, "id"))).filter(Objects::nonNull).collect(toList());
	}

	public List<Person> getActors(int seriesId, Locale locale) throws Exception {
		Map<?, ?> data = getMap(api.getSeries(seriesId, locale, Cache.ONE_MONTH), "data");
		return streamJsonObjects(data, "characters").filter(it -> isActor(getString(it, "peopleType"))).map(this::person).filter(Objects::nonNull).sorted(Person.CREDIT_ORDER).collect(toList());
	}

	public EpisodeInfo getEpisodeInfo(int id, Locale locale) throws Exception {
		Map<?, ?> data = getMap(api.getEpisode(id, locale, Cache.ONE_MONTH), "data");
		Integer seriesId = getInteger(data, "seriesId");
		String overview = getString(data, "overview");
		List<Person> people = streamJsonObjects(data, "characters").map(this::person).filter(Objects::nonNull).collect(toList());
		return new EpisodeInfo(this, locale, seriesId, id, people, overview, null, null);
	}

	private Person person(Map<?, ?> item) {
		String type = getString(item, "peopleType");
		String name = first(getString(item, "personName"), getString(item, "name"));
		String character = isActor(type) ? getString(item, "name") : null;
		String job = getJob(type);
		Integer order = getInteger(item, "sort");
		URL image = getStringValue(item, "personImgURL", this::resolveImage);
		return name == null ? null : new Person(name, character, job, null, order, image);
	}

	private static String getSeasonType(SortOrder order) {
		switch (order) {
		case DVD:
			return "dvd";
		case Absolute:
			return "absolute";
		default:
			return "official";
		}
	}

	private static boolean matchesArtworkCategory(String category, String type) {
		if (type == null) {
			return false;
		}
		String value = type.toLowerCase(Locale.ROOT);
		if ("fanart".equalsIgnoreCase(category)) {
			return value.contains("background") || value.contains("fanart");
		}
		return value.contains(category.toLowerCase(Locale.ROOT));
	}

	private static String findRemoteId(Object node, String source) {
		for (Map<?, ?> remote : getMapArray(node, "remoteIds")) {
			String sourceName = getString(remote, "sourceName");
			String id = getString(remote, "id");
			if (sourceName != null && sourceName.toLowerCase(Locale.ROOT).contains(source) || "imdb".equals(source) && id != null && id.startsWith("tt")) {
				return id;
			}
		}
		return null;
	}

	private static String getAirsDay(Map<?, ?> days) {
		for (String day : new String[] { "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday" }) {
			if (Boolean.TRUE.equals(days.get(day))) {
				return Character.toUpperCase(day.charAt(0)) + day.substring(1);
			}
		}
		return null;
	}

	private static String[] strings(Object[] values, String objectKey) {
		return stream(values).map(it -> it instanceof Map ? getString(it, objectKey) : it == null ? null : it.toString()).filter(Objects::nonNull).toArray(String[]::new);
	}

	private static String firstString(Object[] values, String key) {
		return stream(values).map(it -> getString(it, key)).filter(Objects::nonNull).findFirst().orElse(null);
	}

	private static String translation(Object node, String section, String language, String valueKey) {
		return streamJsonObjects(node, section).filter(it -> language.equalsIgnoreCase(getString(it, "language"))).map(it -> getString(it, valueKey)).filter(Objects::nonNull).findFirst().orElse(null);
	}

	private static String first(String... values) {
		return stream(values).filter(Objects::nonNull).filter(it -> !it.isEmpty()).findFirst().orElse(null);
	}

	private static Integer integer(String value) {
		try {
			return value == null ? null : Integer.valueOf(value);
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static Long parseTimestamp(String value) {
		try {
			return value == null ? null : Long.valueOf(value);
		} catch (NumberFormatException e) {
			try {
				return Instant.parse(value).getEpochSecond();
			} catch (Exception ignored) {
				return null;
			}
		}
	}

	private static Locale locale(String language) {
		return language == null || language.isEmpty() ? null : new Locale(language);
	}

	private static boolean isActor(String type) {
		return type != null && (type.equalsIgnoreCase("Actor") || type.equalsIgnoreCase("Guest Star"));
	}

	private static String getJob(String type) {
		if (type == null) {
			return null;
		}
		if (type.equalsIgnoreCase("Director")) {
			return Person.DIRECTOR;
		}
		if (type.equalsIgnoreCase("Writer")) {
			return Person.WRITER;
		}
		if (type.equalsIgnoreCase("Guest Star")) {
			return Person.GUEST_STAR;
		}
		return Person.ACTOR;
	}
}
