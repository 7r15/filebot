package net.filebot.web;

import java.net.URL;
import java.util.Locale;
import java.util.Map;

public interface TMDbApi {

	Object request(String resource, Map<String, Object> parameters, Locale locale) throws Exception;

	URL resolveImage(String path);
}
