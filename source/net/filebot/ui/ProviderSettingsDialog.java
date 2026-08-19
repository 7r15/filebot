package net.filebot.ui;

import static net.filebot.Settings.*;

import java.awt.Dimension;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.SwingWorker;

import net.filebot.Settings.ApiKeySource;
import net.filebot.web.ProviderConnectionTester;
import net.miginfocom.swing.MigLayout;

public class ProviderSettingsDialog extends JDialog {

	private static final Provider[] PROVIDERS = { new Provider("themoviedb.token", "TMDB read access token", true), new Provider("themoviedb", "TMDB API key (legacy)", true), new Provider("thetvdb", "TheTVDB v4", true), new Provider("thetvdb.pin", "TheTVDB subscriber PIN", false), new Provider("opensubtitles", "OpenSubtitles", true), new Provider("omdb", "OMDb", true), new Provider("fanart.tv", "Fanart.tv", true), new Provider("acoustid", "AcoustID", false), new Provider("anidb", "AniDB client", false), new Provider("anidb.version", "AniDB client version", false) };

	private final List<ProviderRow> rows = new ArrayList<ProviderRow>();

	public ProviderSettingsDialog(Window owner) {
		super(owner, "Provider Settings", ModalityType.APPLICATION_MODAL);
		setDefaultCloseOperation(DISPOSE_ON_CLOSE);

		JPanel content = new JPanel(new MigLayout("fillx, insets dialog, wrap 4", "[right][grow,fill][pref!][pref!]", "[]8[]"));
		content.add(new JLabel("<html>Configure API credentials for metadata and subtitle services.<br>Java properties and environment variables override saved values. Changes take effect after restart.</html>"), "span, growx, wrap 12px");
		content.add(new JLabel("Provider"));
		content.add(new JLabel("Credential"));
		content.add(new JLabel("Source"));
		content.add(new JLabel("Status"));

		for (Provider provider : PROVIDERS) {
			ProviderRow row = new ProviderRow(provider);
			rows.add(row);
			content.add(new JLabel(provider.name));
			content.add(row.field, "wmin 240px");
			content.add(row.source, "wmin 100px");
			content.add(row.test);
		}

		JCheckBox show = new JCheckBox("Show credentials");
		show.addActionListener(evt -> rows.forEach(row -> row.field.setEchoChar(show.isSelected() ? (char) 0 : row.echoChar)));
		content.add(show, "span, skip 1, wrap 12px");

		JButton save = new JButton("Save");
		save.addActionListener(evt -> save());
		JButton cancel = new JButton("Cancel");
		cancel.addActionListener(evt -> dispose());
		content.add(save, "span 3, align right, split 2");
		content.add(cancel);

		setContentPane(content);
		getRootPane().setDefaultButton(save);
		pack();
		setMinimumSize(new Dimension(650, getPreferredSize().height));
		setLocationRelativeTo(owner);
	}

	private void save() {
		boolean changed = false;
		for (ProviderRow row : rows) {
			String value = new String(row.field.getPassword()).trim();
			if (!value.equals(getUserApiKey(row.provider.id))) {
				setUserApiKey(row.provider.id, value);
				changed = true;
			}
		}
		dispose();
		if (changed) {
			JOptionPane.showMessageDialog(getOwner(), "Provider changes take effect after restart.", "Provider Settings", JOptionPane.INFORMATION_MESSAGE);
		}
	}

	public static void show(Window owner) {
		new ProviderSettingsDialog(owner).setVisible(true);
	}

	private static class ProviderRow {

		private final Provider provider;
		private final JPasswordField field;
		private final JLabel source;
		private final JButton test;
		private final char echoChar;

		private ProviderRow(Provider provider) {
			this.provider = provider;
			this.field = new JPasswordField(getUserApiKey(provider.id));
			this.echoChar = field.getEchoChar();
			this.source = new JLabel(getApiKeySource(provider.id).toString());
			this.test = new JButton(provider.testable ? "Test" : "Saved only");
			this.test.setEnabled(provider.testable);
			this.test.addActionListener(evt -> testConnection());
		}

		private void testConnection() {
			ApiKeySource currentSource = getApiKeySource(provider.id);
			String value = currentSource == ApiKeySource.JAVA_PROPERTY || currentSource == ApiKeySource.ENVIRONMENT ? getApiKey(provider.id) : new String(field.getPassword()).trim();
			test.setEnabled(false);
			test.setText("Testing...");

			new SwingWorker<Boolean, Void>() {
				@Override
				protected Boolean doInBackground() {
					try {
						ProviderConnectionTester.test(provider.id, value);
						return true;
					} catch (Exception e) {
						return false;
					}
				}

				@Override
				protected void done() {
					try {
						test.setText(get() ? "Connected" : "Connection failed");
					} catch (Exception e) {
						test.setText("Connection failed");
					}
					test.setEnabled(true);
				}
			}.execute();
		}
	}

	private static class Provider {

		private final String id;
		private final String name;
		private final boolean testable;

		private Provider(String id, String name, boolean testable) {
			this.id = id;
			this.name = name;
			this.testable = testable;
		}
	}
}
