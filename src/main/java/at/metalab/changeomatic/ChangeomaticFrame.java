package at.metalab.changeomatic;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.GraphicsEnvironment;
import java.awt.GridLayout;
import java.awt.font.TextAttribute;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

public class ChangeomaticFrame extends javax.swing.JFrame {

	private static final long serialVersionUID = 1L;

	private final JLabel euro5;
	private final JLabel euro10;
	private final JLabel euro20;
	private final JLabel euro50;
	private final JLabel euro100;
	private final JLabel euro200;

	private final JLabel hint;
	
	private final JPanel notes;

	private JLabel createNoteLabel(String amount) {
		JLabel note = new JLabel();
		note.setFont(fontNoteInhibited);
		note.setText(amount);
		note.setHorizontalAlignment(SwingConstants.CENTER);
		note.setForeground(Color.RED);

		return note;
	}

	private static Font loadFont(String resource) {
		try {
			return Font.createFont(Font.TRUETYPE_FONT, Thread.currentThread()
					.getContextClassLoader().getResourceAsStream(resource));
		} catch (IOException | FontFormatException e) {
			// Handle exception
			e.printStackTrace();
			return null;
		}
	}

	private Font font;

	private Font fontNoteAccepted;

	private Font fontNoteInhibited;

	public ChangeomaticFrame() {
		setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
		setTitle("change-o-matic");
		setCursor(new java.awt.Cursor(java.awt.Cursor.DEFAULT_CURSOR));

		font = loadFont("alphbeta.ttf");

		fontNoteAccepted = font.deriveFont(80f);

		{
			Map attributes = fontNoteAccepted.getAttributes();
			attributes.put(TextAttribute.STRIKETHROUGH,
					TextAttribute.STRIKETHROUGH_ON);
			fontNoteInhibited = fontNoteAccepted.deriveFont(attributes);
		}

		JPanel main = new JPanel(new GridLayout(3, 0));
		main.setBackground(Color.BLACK);

		{
			JLabel logo = new JLabel();
			logo.setHorizontalAlignment(SwingConstants.CENTER);
			logo.setForeground(Color.WHITE);
			logo.setFont(font.deriveFont(110f));
			logo.setText("change-o-matic".toUpperCase());

			main.add(logo);
		}

		euro5 = createNoteLabel("5");
		euro10 = createNoteLabel("10");
		euro20 = createNoteLabel("20");
		euro50 = createNoteLabel("50");
		euro100 = createNoteLabel("100");
		euro200 = createNoteLabel("200");

		notes = new JPanel(new GridLayout(1, 0));
		notes.setBackground(Color.BLACK);

		notes.add(euro5);
		notes.add(euro10);
		notes.add(euro20);
		notes.add(euro50);
		notes.add(euro100);
		notes.add(euro200);

		main.add(notes);

		hint = new JLabel();
		hint.setForeground(Color.YELLOW);
		hint.setFont(font.deriveFont(80f));
		hint.setHorizontalAlignment(SwingConstants.CENTER);
		main.add(hint);

		add(main);
		setSize(GraphicsEnvironment.getLocalGraphicsEnvironment()
				.getMaximumWindowBounds().getSize());
		setLocationRelativeTo(null);
	}

	private void updateHint(String strHint) {
		SwingUtilities.invokeLater(new Runnable() {

			@Override
			public void run() {
				hint.setText("-" + strHint + "-");
				hint.repaint();
			}
		});
	}

	public void hintOhNo() {
		updateHint("OUT OF ORDER");
	}
	
	public void hintSorry() {
		updateHint("SORRY. CAN'T DO.");
	}
	
	public void hintInsertNote() {
		notes.setVisible(true);
		updateHint("INSERT NOTE");
	}

	public void hintPleaseWait() {
		notes.setVisible(false);
		updateHint("PLEASE WAIT");
	}

	public void hintDispensing() {
		updateHint("DISPENSING");
	}

	public void updateInhibits(List<Integer> inhibitedChannels) {
		SwingUtilities.invokeLater(new Runnable() {

			@Override
			public void run() {
				synchronized (ChangeomaticFrame.this) {
					updateInhibit(euro5, 1, inhibitedChannels);
					updateInhibit(euro10, 2, inhibitedChannels);
					updateInhibit(euro20, 3, inhibitedChannels);
					updateInhibit(euro50, 4, inhibitedChannels);
					updateInhibit(euro100, 5, inhibitedChannels);
					updateInhibit(euro200, 6, inhibitedChannels);
				}
			}
		});
	}

	private void updateInhibit(JLabel note, int channel,
			List<Integer> inhibitedChannels) {
		if (inhibitedChannels.contains(channel)) {
			note.setForeground(Color.RED);
			note.setFont(fontNoteInhibited);
		} else {
			note.setForeground(Color.GREEN);
			note.setFont(fontNoteAccepted);
		}
		note.repaint();
	}

}
