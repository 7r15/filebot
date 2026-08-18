package net.filebot.ui;

import static net.filebot.Settings.*;
import static net.filebot.util.ui.SwingUI.*;

import javax.swing.Action;
import javax.swing.ButtonGroup;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JRadioButtonMenuItem;

import net.filebot.util.ui.Theme;
import net.filebot.util.ui.Theme.Appearance;

public class FileBotMenuBar {

	public static JMenuBar create() {
		JMenuBar menuBar = new JMenuBar();
		menuBar.add(createAppearance());
		menuBar.add(createHelp());
		return menuBar;
	}

	private static JMenu createAppearance() {
		JMenu appearance = new JMenu("Appearance");
		ButtonGroup group = new ButtonGroup();

		for (Appearance value : Appearance.values()) {
			JRadioButtonMenuItem item = new JRadioButtonMenuItem(value.toString(), Theme.getAppearance() == value);
			item.addActionListener(evt -> Theme.setAppearance(value));
			group.add(item);
			appearance.add(item);
		}

		return appearance;
	}

	public static JMenu createHelp() {
		JMenu help = new JMenu("Help");

		help.add(createLink("Getting Started", getApplicationProperty("link.intro")));
		help.add(createLink("FAQ", getApplicationProperty("link.faq")));
		help.add(createLink("Forums", getApplicationProperty("link.forums")));
		help.add(createLink("Discord Channel", getApplicationProperty("link.channel")));

		help.addSeparator();

		if (isMacSandbox()) {
			help.add(createLink("Report Bugs", getApplicationProperty("link.help.mas")));
			help.add(createLink("Request Help", getApplicationProperty("link.help.mas")));
		} else {
			help.add(createLink("Report Bugs", getApplicationProperty("link.bugs")));
			help.add(createLink("Request Help", getApplicationProperty("link.help")));
		}

		help.addSeparator();

		help.add(createLink("Contact us on Twitter", getApplicationProperty("link.twitter")));
		help.add(createLink("Contact us on Facebook", getApplicationProperty("link.facebook")));

		return help;
	}

	private static Action createLink(final String title, final String uri) {
		return newAction(title, null, evt -> openURI(uri));
	}

}
