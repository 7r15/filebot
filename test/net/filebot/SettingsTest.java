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

	@Test
	public void savedApiKeyIsResolved() {
		String name = "settings-test-provider";
		String property = Settings.getApiKeyPropertyName(name);
		String previousProperty = System.getProperty(property);
		String previousUserValue = Settings.getUserApiKey(name);

		try {
			System.clearProperty(property);
			Settings.setUserApiKey(name, " saved-key ");
			assertEquals(Settings.ApiKeySource.USER, Settings.getApiKeySource(name));
			assertEquals("saved-key", Settings.getApiKey(name));

			System.setProperty(property, "property-key");
			assertEquals(Settings.ApiKeySource.JAVA_PROPERTY, Settings.getApiKeySource(name));
			assertEquals("property-key", Settings.getApiKey(name));
		} finally {
			Settings.setUserApiKey(name, previousUserValue);
			if (previousProperty == null) {
				System.clearProperty(property);
			} else {
				System.setProperty(property, previousProperty);
			}
		}
	}
}
