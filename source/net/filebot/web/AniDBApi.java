package net.filebot.web;

import org.w3c.dom.Document;

public interface AniDBApi {

	Document getAnime(int id) throws Exception;

	byte[] getAnimeTitles() throws Exception;
}
