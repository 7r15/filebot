package net.filebot.web;

public interface AcoustIDApi {

	String lookup(int duration, String fingerprint) throws Exception;
}
