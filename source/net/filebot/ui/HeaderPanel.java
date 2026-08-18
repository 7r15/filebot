package net.filebot.ui;

import static javax.swing.BorderFactory.*;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.UIManager;

import net.filebot.util.ui.GradientStyle;
import net.filebot.util.ui.notification.SeparatorBorder;
import net.filebot.util.ui.notification.SeparatorBorder.Position;

public class HeaderPanel extends JComponent {

	private JLabel titleLabel = new JLabel();

	private float[] gradientFractions = { 0.0f, 0.5f, 1.0f };
	private Color[] gradientColors;

	public HeaderPanel() {
		setLayout(new BorderLayout());
		JPanel centerPanel = new JPanel(new BorderLayout());
		centerPanel.setOpaque(false);

		titleLabel.setHorizontalAlignment(SwingConstants.CENTER);
		titleLabel.setVerticalAlignment(SwingConstants.CENTER);
		titleLabel.setOpaque(false);
		titleLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 24));

		centerPanel.setBorder(createEmptyBorder());
		centerPanel.add(titleLabel, BorderLayout.CENTER);

		add(centerPanel, BorderLayout.CENTER);

		updateColors();
	}

	@Override
	public void updateUI() {
		super.updateUI();
		if (titleLabel != null) {
			updateColors();
		}
	}

	private void updateColors() {
		Color background = UIManager.getColor("Panel.background");
		Color foreground = UIManager.getColor("Label.foreground");
		Color shadow = UIManager.getColor("Separator.shadow");
		Color highlight = UIManager.getColor("Separator.highlight");

		setBackground(background);
		titleLabel.setForeground(foreground);
		gradientColors = new Color[] { background.brighter(), background, background.darker() };
		setBorder(new SeparatorBorder(1, shadow, highlight, GradientStyle.LEFT_TO_RIGHT, Position.BOTTOM));
	}

	public void setTitle(String title) {
		titleLabel.setText(title);
	}

	public JLabel getTitleLabel() {
		return titleLabel;
	}

	@Override
	protected void paintComponent(Graphics g) {
		Graphics2D g2d = (Graphics2D) g;

		LinearGradientPaint paint = new LinearGradientPaint(0, 0, getWidth(), 0, gradientFractions, gradientColors);

		g2d.setPaint(paint);
		g2d.fill(getBounds());
	}

}
