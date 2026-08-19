package net.filebot.web;

import java.util.Map;

public interface OMDbApi {

	Object request(Map<String, Object> parameters) throws Exception;
}
