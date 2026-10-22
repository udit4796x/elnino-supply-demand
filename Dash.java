import javax.swing.*;
import java.awt.*;
import java.util.Locale;

public class Dash {

    // ---------- Colours (same palette as the main window) ----------
    static final Color LINE = new Color(200, 205, 192);
    static final Color TEXT = new Color(33, 46, 58);
    static final Color MUTED = new Color(104, 114, 118);
    static final Color INK = new Color(33, 46, 58);
    static final Color WHEAT = new Color(214, 164, 52);
    static final Color GOOD = new Color(63, 125, 78);
    static final Color BAD = new Color(181, 67, 47);
    static final Color TEAL = new Color(58, 128, 138);

    static Font sans(int style, int size) {
        return new Font(Fonts.SANS, style, size);
    }

    static Font serif(int style, int size) {
        return new Font(Fonts.SERIF, style, size);
    }

    static void smooth(Graphics2D g2) {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    }

    // ---------- Number formatting ----------

    static String fmt(long n) {
        return String.format(Locale.US, "%,d", n);
    }

    static String pct(double v) {
        return String.format(Locale.US, "%.1f%%", v);
    }

    // 12500 -> 12.5k, 3000000 -> 3M
    static String compact(double v) {
        String s;
        if (v >= 1_000_000) s = String.format(Locale.US, "%.1fM", v / 1_000_000.0);
        else if (v >= 1_000) s = String.format(Locale.US, "%.1fk", v / 1_000.0);
        else s = String.format(Locale.US, "%.0f", v);
        return s.replace(".0M", "M").replace(".0k", "k");
    }

    // Rounds a step size to 1, 2, 5 or 10 times a power of ten (for chart gridlines)
    static double niceStep(double raw) {
        double exp = Math.pow(10, Math.floor(Math.log10(raw)));
        double f = raw / exp;
        double nf = f <= 1 ? 1 : f <= 2 ? 2 : f <= 5 ? 5 : 10;
        return nf * exp;
    }

    // ---------- Text helpers ----------

    static String ellipsize(FontMetrics fm, String s, int maxW) {
        if (fm.stringWidth(s) <= maxW) return s;
        String dots = "...";
        while (s.length() > 1 && fm.stringWidth(s + dots) > maxW) {
            s = s.substring(0, s.length() - 1);
        }
        return s + dots;
    }

    // Returns a font small enough for the text to fit in maxW
    static Font fit(Graphics2D g2, String s, int maxW, boolean serifFace, int style, int size) {
        Font f = serifFace ? serif(style, size) : sans(style, size);
        while (size > 10 && g2.getFontMetrics(f).stringWidth(s) > maxW) {
            size--;
            f = serifFace ? serif(style, size) : sans(style, size);
        }
        return f;
    }

    // Draws text that wraps onto several lines. Returns the y of the next free line.
    static int drawWrapped(Graphics2D g2, String text, int x, int y, int maxW) {
        FontMetrics fm = g2.getFontMetrics();
        int lineH = fm.getHeight();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String test = line.length() == 0 ? word : line + " " + word;
            if (fm.stringWidth(test) > maxW && line.length() > 0) {
                g2.drawString(line.toString(), x, y);
                y += lineH;
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(test);
            }
        }
        if (line.length() > 0) {
            g2.drawString(line.toString(), x, y);
            y += lineH;
        }
        return y;
    }

    // ---------- Building blocks ----------

    // White rounded card
    static class Card extends JPanel {
        Card() {
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            smooth(g2);
            g2.setColor(Color.WHITE);
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
            g2.setColor(LINE);
            g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 8, 8);
            g2.dispose();
            super.paintComponent(g);
        }
    }

    // Lets the dashboard scroll up and down on small screens, but always fill the width
    static class Content extends JPanel implements Scrollable {
        Content() {
            setOpaque(false);
        }

        public Dimension getPreferredScrollableViewportSize() {
            return getPreferredSize();
        }

        public int getScrollableUnitIncrement(Rectangle r, int orientation, int direction) {
            return 24;
        }

        public int getScrollableBlockIncrement(Rectangle r, int orientation, int direction) {
            return 160;
        }

        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        public boolean getScrollableTracksViewportHeight() {
            Container p = getParent();
            return p instanceof JViewport && p.getHeight() > getPreferredSize().height;
        }
    }

    // Small Normal / El Nino switch above the map
    static class Chip extends JToggleButton {
        Chip(String text) {
            super(text);
            setContentAreaFilled(false);
            setFocusPainted(false);
            setBorderPainted(false);
            setOpaque(false);
            setFont(sans(Font.BOLD, 12));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setPreferredSize(new Dimension(86, 28));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            smooth(g2);
            if (isSelected()) {
                g2.setColor(INK);
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 6, 6);
            } else {
                g2.setColor(Color.WHITE);
                g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 6, 6);
                g2.setColor(LINE);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 6, 6);
            }
            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            g2.setColor(isSelected() ? Color.WHITE : TEXT);
            g2.drawString(getText(), (getWidth() - fm.stringWidth(getText())) / 2,
                    (getHeight() - fm.getHeight()) / 2 + fm.getAscent());
            g2.dispose();
        }
    }

    // One key number: small title, big figure, small note
    static class Kpi extends Card {
        private final String title;
        private String value = "-";
        private String sub = " ";
        private Color color = TEXT;

        Kpi(String title) {
            this.title = title;
            setPreferredSize(new Dimension(10, 104));
        }

        void set(String value, Color color, String sub) {
            this.value = value;
            this.color = color;
            this.sub = sub;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            smooth(g2);
            int maxW = getWidth() - 36;

            g2.setFont(sans(Font.PLAIN, 12));
            g2.setColor(MUTED);
            g2.drawString(title, 18, 26);

            g2.setFont(fit(g2, value, maxW, true, Font.BOLD, 30));
            g2.setColor(color);
            g2.drawString(value, 18, 64);

            g2.setFont(fit(g2, sub, maxW, false, Font.PLAIN, 12));
            g2.setColor(MUTED);
            g2.drawString(sub, 18, 90);
            g2.dispose();
        }
    }

    // The "How it works" strip: five numbered steps
    static class Steps extends Card {
        private static final String[] TITLES = {
            "Enter the data", "Find each balance", "Sort the regions",
            "Match surplus to deficit", "Repeat for El Niño"};
        private static final String[] TEXTS = {
            "Type the grain supply and demand of each region.",
            "Balance = supply - demand. Positive means surplus, negative means deficit.",
            "HashMaps split surplus and deficit regions. Each list is sorted, largest first.",
            "The biggest surplus feeds the biggest deficit until the need is met or the grain runs out.",
            "Cut every supply by the chosen %, then match again and compare the two years."};

        Steps() {
            setPreferredSize(new Dimension(10, 168));
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            smooth(g2);

            g2.setFont(sans(Font.BOLD, 11));
            g2.setColor(MUTED);
            g2.drawString("HOW IT WORKS", 20, 26);
            int capW = g2.getFontMetrics().stringWidth("HOW IT WORKS");
            g2.setFont(sans(Font.PLAIN, 12));
            g2.drawString("Grain moves from regions with a surplus to regions that fall short.",
                    20 + capW + 16, 26);

            int n = TITLES.length;
            int pad = 20;
            int colW = (getWidth() - 2 * pad) / n;
            for (int i = 0; i < n; i++) {
                int x = pad + i * colW;
                if (i > 0) {
                    g2.setColor(LINE);
                    g2.drawLine(x - 10, 42, x - 10, getHeight() - 16);
                }

                g2.setColor(INK);
                g2.fillOval(x, 42, 28, 28);
                g2.setColor(Color.WHITE);
                g2.setFont(sans(Font.BOLD, 14));
                FontMetrics fm = g2.getFontMetrics();
                String num = String.valueOf(i + 1);
                g2.drawString(num, x + (28 - fm.stringWidth(num)) / 2,
                        42 + (28 - fm.getHeight()) / 2 + fm.getAscent());

                g2.setColor(TEXT);
                g2.setFont(fit(g2, TITLES[i], colW - 52, false, Font.BOLD, 14));
                g2.drawString(TITLES[i], x + 38, 61);

                g2.setColor(MUTED);
                g2.setFont(sans(Font.PLAIN, 12));
                drawWrapped(g2, TEXTS[i], x, 94, colW - 22);
            }
            g2.dispose();
        }
    }
}