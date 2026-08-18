package net.filebot;

import static org.junit.Assert.*;

import org.junit.Test;

public class SettingsTest {

	@Test
	public void apiKeySystemPropertyOverridesApplicationConfiguration() {
		String property = "net.filebot.apikey.opensubtitles";
		String previous = System.getProperty(property);

		try {
			System.setProperty(property, " test-key ");
			assertEquals("test-key", Settings.getApiKey("opensubtitles"));
		} finally {
			if (previous == null) {
				System.clearProperty(property);
			} else {
				System.setProperty(property, previous);
			}
		}
	}

	@Test
	public void missingApiKeyIsEmpty() {
		String property = "net.filebot.apikey.opensubtitles";
		String previous = System.getProperty(property);

		try {
			System.clearProperty(property);
			assertNotNull(Settings.getApiKey("opensubtitles"));
		} finally {
			if (previous != null) {
				System.setProperty(property, previous);
			}
		}
	}
}
