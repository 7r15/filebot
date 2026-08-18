package net.filebot.util.ui;

import static org.junit.Assert.*;

import java.awt.Color;

import javax.swing.UIManager;

import org.junit.After;
import org.junit.Test;

import net.filebot.util.ui.Theme.Appearance;

public class ThemeTest {

	@After
	public void restoreDefaultAppearance() {
		Theme.apply(Appearance.DEFAULT, false);
	}

	@Test
	public void parseAppearance() {
		assertEquals(Appearance.DEFAULT, Appearance.parse(null));
		assertEquals(Appearance.DEFAULT, Appearance.parse("unknown"));
		assertEquals(Appearance.DARK, Appearance.parse("dark"));
	}

	@Test
	public void applyDarkAppearance() {
		Theme.apply(Appearance.DARK, false);

		assertEquals("Nimbus", UIManager.getLookAndFeel().getName());
		assertNotNull(UIManager.getColor("Panel.background"));
		assertNotNull(UIManager.getColor("Label.foreground"));
		assertNotNull(UIManager.getColor("Separator.shadow"));
		assertNotNull(UIManager.getColor("Separator.highlight"));
		assertEquals(new Color(0x2B2D30), UIManager.getColor("nimbusLightBackground"));
		assertEquals(new Color(0xE6E6E6), UIManager.getColor("text"));
		assertEquals(new Color(0x315F8C), UIManager.getColor("nimbusSelectionBackground"));
	}
}
