package net.filebot.util.ui;

import static net.filebot.Logging.*;

import java.awt.Color;
import java.awt.Window;
import java.util.Locale;
import java.util.logging.Level;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.plaf.BorderUIResource;
import javax.swing.plaf.ColorUIResource;

import net.filebot.Settings;

public final class Theme {

	public enum Appearance {
		DEFAULT("Default"), DARK("Dark");

		private final String displayName;

		Appearance(String displayName) {
			this.displayName = displayName;
		}

		@Override
		public String toString() {
			return displayName;
		}

		public static Appearance parse(String value) {
			try {
				return valueOf(value.toUpperCase(Locale.ROOT));
			} catch (Exception e) {
				return DEFAULT;
			}
		}
	}

	private static final String APPEARANCE_KEY = "appearance";
	private static final String[] DARK_VALUE_KEYS = { "control", "info", "nimbusBase", "nimbusBlueGrey", "nimbusBorder", "nimbusDisabledText", "nimbusFocus", "nimbusLightBackground", "nimbusSelectedText", "nimbusSelectionBackground", "text", "Table.gridColor", "Table.alternateRowColor", "Separator.shadow", "Separator.highlight", "TitledBorder.border" };

	public static Appearance getAppearance() {
		String value = Settings.forPackage(Theme.class).get(APPEARANCE_KEY, Appearance.DEFAULT.name());
		return Appearance.parse(value);
	}

	public static boolean isDark() {
		return getAppearance() == Appearance.DARK;
	}

	public static void initialize() {
		apply(getAppearance(), false);
	}

	public static void setAppearance(Appearance appearance) {
		Settings.forPackage(Theme.class).put(APPEARANCE_KEY, appearance.name());
		apply(appearance, true);
	}

	static void apply(Appearance appearance, boolean refreshWindows) {
		try {
			clearDarkPalette();

			if (appearance == Appearance.DARK) {
				UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel");
				installDarkPalette();
			} else if (Settings.isPortableApp()) {
				UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel");
			} else {
				UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
			}

			installMissingColors();

			if (refreshWindows) {
				for (Window window : Window.getWindows()) {
					SwingUtilities.updateComponentTreeUI(window);
					window.repaint();
				}
			}
		} catch (Exception e) {
			debug.log(Level.SEVERE, "Failed to apply " + appearance + " appearance", e);
		}
	}

	private static void installDarkPalette() {
		putColor("control", 0x35383C);
		putColor("info", 0x35383C);
		putColor("nimbusBase", 0x121E31);
		putColor("nimbusBlueGrey", 0x2D3035);
		putColor("nimbusBorder", 0x555A60);
		putColor("nimbusDisabledText", 0x8A8F96);
		putColor("nimbusFocus", 0x5B9BD5);
		putColor("nimbusLightBackground", 0x2B2D30);
		putColor("nimbusSelectedText", 0xFFFFFF);
		putColor("nimbusSelectionBackground", 0x315F8C);
		putColor("text", 0xE6E6E6);
		putColor("Table.gridColor", 0x4A4E53);
		putColor("Table.alternateRowColor", 0x303338);
		putColor("Separator.shadow", 0x1F2124);
		putColor("Separator.highlight", 0x555A60);
		UIManager.put("TitledBorder.border", new BorderUIResource.LineBorderUIResource(new Color(0x555A60), 1));
	}

	private static void installMissingColors() {
		putColorIfMissing("Separator.shadow", 0xB4B4B4);
		putColorIfMissing("Separator.highlight", 0xACACAC);
	}

	private static void clearDarkPalette() {
		for (String key : DARK_VALUE_KEYS) {
			UIManager.put(key, null);
		}
	}

	private static void putColor(String key, int rgb) {
		UIManager.put(key, new ColorUIResource(new Color(rgb)));
	}

	private static void putColorIfMissing(String key, int rgb) {
		if (UIManager.getColor(key) == null) {
			putColor(key, rgb);
		}
	}

	private Theme() {
		throw new UnsupportedOperationException();
	}
}
