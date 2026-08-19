package net.filebot.web;

import static org.junit.Assert.*;

import java.io.File;
import java.util.EnumMap;
import java.util.Map;

import org.junit.Test;

import net.filebot.web.AcoustIDClient.ChromaprintField;

public class AcoustIDClientTest {

	private static final String RESPONSE = "{\"status\":\"ok\",\"results\":[{\"id\":\"track-id\",\"recordings\":[{\"duration\":357,\"title\":\"Thriller\",\"artists\":[{\"name\":\"Michael Jackson\"}],\"releasegroups\":[{\"type\":\"Single\",\"title\":\"Thriller\",\"secondarytypes\":[],\"releases\":[]}]}]}]}";

	@Test
	public void parsesRecordingFixture() throws Exception {
		AudioTrack track = new AcoustIDClient((duration, fingerprint) -> RESPONSE).parseResult(RESPONSE, 357);

		assertEquals("Michael Jackson", track.getArtist());
		assertEquals("Thriller", track.getTitle());
	}

	@Test
	public void fileLookupRequestsFingerprintOnce() throws Exception {
		CountingApi api = new CountingApi();
		AcoustIDClient client = new AcoustIDClient(api) {
			@Override
			public Map<ChromaprintField, String> fpcalc(File file) {
				Map<ChromaprintField, String> values = new EnumMap<ChromaprintField, String>(ChromaprintField.class);
				values.put(ChromaprintField.DURATION, "357");
				values.put(ChromaprintField.FINGERPRINT, "fixture-fingerprint");
				return values;
			}
		};

		Map<File, AudioTrack> result = client.lookup(java.util.Collections.singleton(new File("fixture.mp3")));

		assertEquals(1, api.requests);
		assertEquals(1, result.size());
		assertEquals("Thriller", result.values().iterator().next().getTitle());
	}

	private static class CountingApi implements AcoustIDApi {

		int requests;

		@Override
		public String lookup(int duration, String fingerprint) {
			requests++;
			return RESPONSE;
		}
	}
}
