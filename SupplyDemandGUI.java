import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.plaf.FontUIResource;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public class SupplyDemandGUI {

    // ---------- Colours ----------
    // Grain-and-atlas palette: paper ground, ink text, wheat for the main action
    private static final Color BG = new Color(236, 238, 230);          // paper
    private static final Color LINE = new Color(200, 205, 192);        // thin rules
    private static final Color TEXT = new Color(33, 46, 58);           // ink
    private static final Color MUTED = new Color(104, 114, 118);
    private static final Color INK = new Color(33, 46, 58);
    private static final Color PRIMARY = new Color(214, 164, 52);      // wheat
    private static final Color PRIMARY_HOVER = new Color(196, 146, 38);
    private static final Color SELECT = new Color(226, 231, 212);
    private static final Color GOOD = new Color(63, 125, 78);
    private static final Color BAD = new Color(181, 67, 47);
    private static final Color NORMAL_ACCENT = new Color(58, 128, 138);

    private static Font font(int style, int size) {
        return new Font(Fonts.SANS, style, size);
    }

    private static Font serif(int style, int size) {
        return new Font(Fonts.SERIF, style, size);
    }

    private static String fmt(int n) {
        return String.format(Locale.US, "%,d", n);
    }

    private static String pct(double v) {
        return String.format(Locale.US, "%.1f%%", v);
    }

    // Applies one font to every standard Swing component
    private static void setGlobalFont(Font f) {
        Enumeration<Object> keys = UIManager.getDefaults().keys();
        while (keys.hasMoreElements()) {
            Object key = keys.nextElement();
            Object value = UIManager.get(key);
            if (value instanceof FontUIResource) {
                UIManager.put(key, new FontUIResource(f));
            }
        }
    }

    // ---------- Custom look-and-feel pieces ----------

    // A panel with rounded corners (used for cards)
    static class RoundedPanel extends JPanel {
        private final int radius;
        private final Color fill;
        private final Color border;

        RoundedPanel(int radius, Color fill, Color border) {
            this.radius = radius;
            this.fill = fill;
            this.border = border;
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(fill);
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);
            if (border != null) {
                g2.setColor(border);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, radius, radius);
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    // Dark bar at the top with a wheat rule under it
    static class HeaderPanel extends JPanel {
        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setColor(INK);
            g2.fillRect(0, 0, getWidth(), getHeight());
            g2.setColor(PRIMARY);
            g2.fillRect(0, getHeight() - 3, getWidth(), 3);
            g2.dispose();
        }
    }

    // A flat, rounded button
    static class RoundedButton extends JButton {
        private final Color fill, hover, border, textColor;

        RoundedButton(String text, Color fill, Color hover, Color border, Color textColor, int fontSize) {
            super(text);
            this.fill = fill;
            this.hover = hover;
            this.border = border;
            this.textColor = textColor;
            setContentAreaFilled(false);
            setFocusPainted(false);
            setBorderPainted(false);
            setOpaque(false);
            setFont(font(Font.BOLD, fontSize));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            ButtonModel m = getModel();
            Color bg = m.isPressed() ? hover.darker() : (m.isRollover() ? hover : fill);
            g2.setColor(bg);
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 6, 6);
            if (border != null) {
                g2.setColor(border);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 6, 6);
            }

            g2.setFont(getFont());
            FontMetrics fm = g2.getFontMetrics();
            int x = (getWidth() - fm.stringWidth(getText())) / 2;
            int y = (getHeight() - fm.getHeight()) / 2 + fm.getAscent();
            g2.setColor(isEnabled() ? textColor : new Color(160, 165, 175));
            g2.drawString(getText(), x, y);
            g2.dispose();
        }
    }

    // One figure: plain label, big serif number, thin rule on the left
    static class StatCard extends JPanel {
        private final JLabel value = new JLabel("-");

        StatCard(String title, boolean first, int size) {
            setOpaque(false);
            setLayout(new GridLayout(2, 1, 0, 0));
            setBorder(BorderFactory.createCompoundBorder(
                    first ? new EmptyBorder(0, 0, 0, 0)
                          : BorderFactory.createMatteBorder(0, 1, 0, 0, LINE),
                    new EmptyBorder(2, first ? 0 : 14, 2, 6)));
            JLabel t = new JLabel(title);
            t.setFont(font(Font.PLAIN, 12));
            t.setForeground(MUTED);
            value.setFont(serif(Font.BOLD, size));
            value.setForeground(TEXT);
            add(t);
            add(value);
        }

        void set(String text, Color color) {
            value.setText(text);
            value.setForeground(color);
        }
    }

    static class CellRenderer extends DefaultTableCellRenderer {
        CellRenderer(int align) {
            setHorizontalAlignment(align);
        }

        @Override
        public Component getTableCellRendererComponent(JTable t, Object v, boolean sel,
                                                       boolean focus, int r, int c) {
            super.getTableCellRendererComponent(t, v, sel, false, r, c);
            setBorder(new EmptyBorder(0, 12, 0, 12));
            setFont(font(Font.PLAIN, 13));
            return this;
        }
    }

    static class HeaderRenderer extends DefaultTableCellRenderer {
        HeaderRenderer(int align) {
            setHorizontalAlignment(align);
            setOpaque(true);
        }

        @Override
        public Component getTableCellRendererComponent(JTable t, Object v, boolean sel,
                                                       boolean focus, int r, int c) {
            super.getTableCellRendererComponent(t, v, false, false, r, c);
            setBackground(new Color(246, 247, 241));
            setForeground(MUTED);
            setFont(font(Font.BOLD, 12));
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, LINE),
                    new EmptyBorder(0, 12, 0, 12)));
            return this;
        }
    }

    // Gives a table the clean modern look. Columns from rightFrom onwards are right-aligned.
    private static void styleTable(JTable table, int rightFrom) {
        table.setRowHeight(34);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setFillsViewportHeight(true);
        table.setFont(font(Font.PLAIN, 13));
        table.setForeground(TEXT);
        table.setBackground(Color.WHITE);
        table.setSelectionBackground(SELECT);
        table.setSelectionForeground(TEXT);
        table.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);

        JTableHeader header = table.getTableHeader();
        header.setReorderingAllowed(false);
        header.setPreferredSize(new Dimension(10, 34));

        for (int i = 0; i < table.getColumnCount(); i++) {
            int align = (i >= rightFrom) ? SwingConstants.RIGHT : SwingConstants.LEFT;
            table.getColumnModel().getColumn(i).setCellRenderer(new CellRenderer(align));
            table.getColumnModel().getColumn(i).setHeaderRenderer(new HeaderRenderer(align));
        }
    }

    private static JScrollPane styledScroll(JTable table) {
        JScrollPane sp = new JScrollPane(table);
        sp.setBorder(BorderFactory.createLineBorder(LINE));
        sp.getViewport().setBackground(Color.WHITE);
        return sp;
    }

    private static JLabel cardTitle(String text) {
        JLabel l = new JLabel(text);
        l.setFont(serif(Font.BOLD, 17));
        l.setForeground(TEXT);
        return l;
    }

    // A flat section: a thin rule on top and a serif heading, no card behind it
    private static JPanel section(int vgap) {
        JPanel p = new JPanel(new BorderLayout(0, vgap));
        p.setOpaque(false);
        p.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, LINE),
                new EmptyBorder(14, 0, 14, 0)));
        return p;
    }

    private static RoundedButton secondaryButton(String text) {
        RoundedButton b = new RoundedButton(text, Color.WHITE, new Color(244, 245, 238),
                new Color(190, 196, 182), TEXT, 13);
        b.setPreferredSize(new Dimension(80, 36));
        return b;
    }

    // ---------- Helpers for the table and dialog ----------

    // Makes sure a cell you are still typing in is saved before we read it
    private static void stopEditing(JTable table) {
        if (table.isEditing()) {
            table.getCellEditor().stopCellEditing();
        }
    }

    private static String cell(DefaultTableModel model, int row, int col) {
        Object o = model.getValueAt(row, col);
        return o == null ? "" : o.toString().trim();
    }

    private static void loadExample(DefaultTableModel model) {
        model.setRowCount(0);
        model.addRow(new Object[]{"Punjab", "10000", "4000"});
        model.addRow(new Object[]{"Haryana", "8000", "5000"});
        model.addRow(new Object[]{"Rajasthan", "3000", "7000"});
        model.addRow(new Object[]{"Maharashtra", "2000", "6000"});
    }

    // Opens the "Add Region" form. Returns {name, supply, demand} or null if cancelled.
    private static String[] showAddDialog(JFrame frame, DefaultComboBoxModel<String> nameModel,
                                          Set<String> used) {
        // Separate copy of the names, so the form does not disturb the table's dropdown
        DefaultComboBoxModel<String> copy = new DefaultComboBoxModel<>();
        for (int i = 0; i < nameModel.getSize(); i++) {
            copy.addElement(nameModel.getElementAt(i));
        }

        JComboBox<String> nameBox = new JComboBox<>(copy);
        nameBox.setEditable(true);
        nameBox.setSelectedItem("");
        JTextField nameField = (JTextField) nameBox.getEditor().getEditorComponent();

        JTextField latField = new JTextField();
        JTextField lonField = new JTextField();
        JTextField supplyField = new JTextField("0");
        JTextField demandField = new JTextField("0");
        JLabel hint = new JLabel(" ");
        hint.setFont(font(Font.ITALIC, 12));
        hint.setForeground(MUTED);

        // Known place: position is filled in. Unknown place: you enter latitude and longitude.
        boolean[] wasKnown = {false};
        Runnable refresh = () -> {
            String known = IndiaStates.canonicalName(nameField.getText());
            if (known != null) {
                double[] p = IndiaStates.getPosition(known);
                lonField.setText(String.valueOf(p[0]));
                latField.setText(String.valueOf(p[1]));
                lonField.setEditable(false);
                latField.setEditable(false);
                wasKnown[0] = true;
                hint.setText("Known place: position filled in automatically.");
            } else {
                if (wasKnown[0]) {
                    latField.setText("");
                    lonField.setText("");
                }
                wasKnown[0] = false;
                latField.setEditable(true);
                lonField.setEditable(true);
                hint.setText(nameField.getText().trim().isEmpty()
                        ? "Pick a state, or type your own place name."
                        : "New place: enter its latitude and longitude.");
            }
        };
        nameField.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e) { refresh.run(); }
            public void removeUpdate(DocumentEvent e) { refresh.run(); }
            public void changedUpdate(DocumentEvent e) { refresh.run(); }
        });
        refresh.run();

        JPanel fields = new JPanel(new GridLayout(0, 2, 8, 8));
        fields.add(new JLabel("State / place name"));
        fields.add(nameBox);
        fields.add(new JLabel("Latitude (e.g. 19.07)"));
        fields.add(latField);
        fields.add(new JLabel("Longitude (e.g. 72.88)"));
        fields.add(lonField);
        fields.add(new JLabel("Supply"));
        fields.add(supplyField);
        fields.add(new JLabel("Demand"));
        fields.add(demandField);

        JPanel wrapper = new JPanel(new BorderLayout(5, 8));
        wrapper.add(hint, BorderLayout.NORTH);
        wrapper.add(fields, BorderLayout.CENTER);
        wrapper.setPreferredSize(new Dimension(440, 220));

        // Keep asking until the input is valid, or the user cancels
        while (true) {
            int result = JOptionPane.showConfirmDialog(frame, wrapper, "Add Region",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (result != JOptionPane.OK_OPTION) return null;

            String typed = nameField.getText().trim();
            if (typed.isEmpty()) {
                JOptionPane.showMessageDialog(frame, "Please pick or type a name.");
                continue;
            }

            String known = IndiaStates.canonicalName(typed);
            String name = (known != null) ? known : typed;
            if (used.contains(name)) {
                JOptionPane.showMessageDialog(frame, name + " is already in the table.");
                continue;
            }

            int supply = 0, demand = 0;
            try {
                supply = Integer.parseInt(supplyField.getText().trim());
                demand = Integer.parseInt(demandField.getText().trim());
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(frame,
                        "Supply and Demand must be whole numbers (no commas or letters).");
                continue;
            }
            if (supply < 0 || demand < 0) {
                JOptionPane.showMessageDialog(frame, "Supply and Demand cannot be negative.");
                continue;
            }

            if (known == null) {
                double lat, lon;
                try {
                    lat = Double.parseDouble(latField.getText().trim());
                    lon = Double.parseDouble(lonField.getText().trim());
                } catch (NumberFormatException ex) {
                    JOptionPane.showMessageDialog(frame,
                            "Please enter the latitude and longitude as numbers, e.g. 19.07 and 72.88.");
                    continue;
                }
                if (lat < 6 || lat > 38 || lon < 68 || lon > 98) {
                    JOptionPane.showMessageDialog(frame,
                            "The place must be inside the map of India:\n"
                            + "latitude 6 to 38, longitude 68 to 98.");
                    continue;
                }
                IndiaStates.addCustom(name, lon, lat);
                nameModel.addElement(name);
            }

            return new String[]{name, String.valueOf(supply), String.valueOf(demand)};
        }
    }

    // ---------- Calculating one scenario ----------

    static class ScenarioResult {
        List<Region> regions = new ArrayList<>();
        List<Transfer> transfers = new ArrayList<>();
        int totalSupply, totalDemand, unmet;
        double pct;
    }

    // Copies the regions, lowers every supply by dropPercent, then matches surplus to deficit
    private static ScenarioResult compute(List<Region> base, int dropPercent) {
        ScenarioResult res = new ScenarioResult();
        for (Region b : base) {
            int supply = (int) ((long) b.supply * (100 - dropPercent) / 100);
            res.regions.add(new Region(b.name, supply, b.demand));
        }
        res.transfers = Matcher.getTransfers(res.regions);

        Map<String, Integer> received = new HashMap<>();
        for (Transfer t : res.transfers) {
            received.merge(t.to, t.amount, Integer::sum);
        }
        for (Region r : res.regions) {
            res.totalSupply += r.supply;
            res.totalDemand += r.demand;
            int bal = r.getBalance();
            if (bal < 0) {
                res.unmet += Math.max(0, -bal - received.getOrDefault(r.name, 0));
            }
        }
        res.pct = res.totalDemand > 0 ? res.unmet * 100.0 / res.totalDemand : 0;
        return res;
    }

    // ---------- One scenario panel: totals, map and best matches ----------

    static class ScenarioPanel extends JPanel {
        private final StatCard supplyCard = new StatCard("Supply", true, 18);
        private final StatCard demandCard = new StatCard("Demand", false, 18);
        private final StatCard unmetCard = new StatCard("Unmet", false, 18);
        private final StatCard shortageCard = new StatCard("Shortage", false, 18);;
        private final MapPanel map = new MapPanel();
        private final DefaultTableModel transferModel =
                new DefaultTableModel(new String[]{"From", "To", "Units"}, 0) {
                    @Override
                    public boolean isCellEditable(int row, int col) {
                        return false;
                    }
                };
        private final JLabel transferCount = new JLabel(" ");
        private final JLabel subtitle = new JLabel(" ");
        private JPanel statsRow;
        private JPanel matchesPanel;

        ScenarioPanel(String titleText, Color accent, String sub) {
            setOpaque(false);
            setLayout(new BorderLayout(0, 10));
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(3, 0, 0, 0, accent),
                    new EmptyBorder(12, 0, 0, 0)));

            // Title row
            JLabel title = new JLabel(titleText);
            title.setFont(serif(Font.BOLD, 20));
                        title.setForeground(TEXT);
            subtitle.setFont(font(Font.PLAIN, 12));
            subtitle.setForeground(MUTED);
            subtitle.setText(sub);

            JPanel head = new JPanel(new BorderLayout());
            head.setOpaque(false);
            head.add(title, BorderLayout.WEST);
            head.add(subtitle, BorderLayout.EAST);

            // Four totals
            JPanel stats = new JPanel(new GridLayout(1, 4, 0, 0));
            stats.setOpaque(false);
            stats.add(supplyCard);
            stats.add(demandCard);
            stats.add(unmetCard);
            stats.add(shortageCard);

            JPanel north = new JPanel(new BorderLayout(0, 10));
            north.setOpaque(false);
            north.add(head, BorderLayout.NORTH);
            north.add(stats, BorderLayout.CENTER);
            statsRow = stats;

            // Map
            map.setBorder(BorderFactory.createLineBorder(new Color(150, 168, 172)));

            // Best matches table
            JTable transferTable = new JTable(transferModel);
            styleTable(transferTable, 2);
            transferTable.setRowHeight(28);
            transferTable.setFocusable(false);

            JLabel matchTitle = new JLabel("Best matches");
            matchTitle.setFont(serif(Font.BOLD, 15));
            matchTitle.setForeground(TEXT);
            transferCount.setFont(font(Font.PLAIN, 12));
            transferCount.setForeground(MUTED);

            JPanel matchHead = new JPanel(new BorderLayout());
            matchHead.setOpaque(false);
            matchHead.add(matchTitle, BorderLayout.WEST);
            matchHead.add(transferCount, BorderLayout.EAST);

            JPanel south = new JPanel(new BorderLayout(0, 6));
            south.setOpaque(false);
            south.setPreferredSize(new Dimension(10, 160));
            south.add(matchHead, BorderLayout.NORTH);
            south.add(styledScroll(transferTable), BorderLayout.CENTER);

            add(north, BorderLayout.NORTH);
            add(map, BorderLayout.CENTER);
            add(south, BorderLayout.SOUTH);
            matchesPanel = south;
        }

        MapPanel getMap() {
            return map;
        }

        void setDetailsVisible(boolean show) {
            statsRow.setVisible(show);
            matchesPanel.setVisible(show);
            revalidate();
            repaint();
        }

        void setSubtitle(String s) {
            subtitle.setText(s);
        }

        void showResult(ScenarioResult r) {
            Color status = r.unmet > 0 ? BAD : GOOD;
            supplyCard.set(fmt(r.totalSupply), TEXT);
            demandCard.set(fmt(r.totalDemand), TEXT);
            unmetCard.set(fmt(r.unmet), status);
            shortageCard.set(pct(r.pct), status);

            transferModel.setRowCount(0);
            for (Transfer t : r.transfers) {
                transferModel.addRow(new Object[]{t.from, t.to, fmt(t.amount)});
            }
            transferCount.setText(r.transfers.isEmpty() ? "No transfers needed"
                    : r.transfers.size() + (r.transfers.size() == 1 ? " transfer" : " transfers"));

            map.setData(r.regions, r.transfers);
        }

        void clear() {
            supplyCard.set("-", TEXT);
            demandCard.set("-", TEXT);
            unmetCard.set("-", TEXT);
            shortageCard.set("-", TEXT);
            transferModel.setRowCount(0);
            transferCount.setText(" ");
            map.setData(new ArrayList<>(), new ArrayList<>());
        }
    }

    // ---------- The window ----------

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ex) {
            // keep the default look if this fails
        }
        setGlobalFont(font(Font.PLAIN, 13));
        SwingUtilities.invokeLater(SupplyDemandGUI::createAndShow);
    }

    private static void createAndShow() {

        JFrame frame = new JFrame("El Niño Supply-Demand Matcher");
        frame.setSize(1400, 800);
        frame.setMinimumSize(new Dimension(1280, 700));
        frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);

        Container root = frame.getContentPane();
        root.setBackground(BG);
        root.setLayout(new BorderLayout());

        // ----- Header -----
        HeaderPanel header = new HeaderPanel();
        header.setLayout(new BorderLayout());
        header.setBorder(new EmptyBorder(16, 28, 19, 28));

        JPanel titles = new JPanel(new GridLayout(2, 1, 0, 2));
        titles.setOpaque(false);
        JLabel title = new JLabel("El Niño Supply-Demand Matcher");
        title.setFont(serif(Font.BOLD, 24));
        title.setForeground(new Color(244, 241, 230));
        JLabel subtitle = new JLabel("Match surplus grain regions with deficit regions across India");
        subtitle.setFont(font(Font.PLAIN, 13));
        subtitle.setForeground(new Color(176, 190, 198));
        titles.add(title);
        titles.add(subtitle);
        header.add(titles, BorderLayout.WEST);

        JLabel modeLabel = new JLabel("Normal year vs El Niño year");
        modeLabel.setFont(serif(Font.ITALIC, 15));
        modeLabel.setForeground(new Color(232, 196, 100));
        JPanel badgeWrap = new JPanel(new GridBagLayout());
        badgeWrap.setOpaque(false);
        RoundedButton detailsButton = new RoundedButton("Hide details", new Color(48, 64, 78),
                new Color(66, 86, 104), new Color(110, 128, 142), new Color(244, 241, 230), 13);
        detailsButton.setPreferredSize(new Dimension(130, 34));
        badgeWrap.add(modeLabel);
        badgeWrap.add(Box.createHorizontalStrut(20));
        badgeWrap.add(detailsButton);
        RoundedButton viewButton = new RoundedButton("Compare view", PRIMARY,
                PRIMARY_HOVER, null, INK, 13);
        viewButton.setPreferredSize(new Dimension(150, 34));
        badgeWrap.add(Box.createHorizontalStrut(10));
        badgeWrap.add(viewButton);
        header.add(badgeWrap, BorderLayout.EAST);

        root.add(header, BorderLayout.NORTH);

        // ----- The two scenario panels -----
        ScenarioPanel normalPanel = new ScenarioPanel("Normal year", NORMAL_ACCENT,
                "Supply exactly as entered");
        ScenarioPanel elNinoPanel = new ScenarioPanel("El Niño year", BAD,
                "Supply reduced by 15%");
        // normalPanel.getMap().linkWith(elNinoPanel.getMap());   // zoom and move together

        // ----- Impact strip (El Niño compared with normal) -----
        StatCard impactSupply = new StatCard("Supply lost", true, 20);
        StatCard impactUnmet = new StatCard("Extra unmet demand", false, 20);
        StatCard impactShortage = new StatCard("Shortage", false, 20);

        JLabel impactTitle = cardTitle("El Niño impact");
        JLabel impactSub = new JLabel("compared with the normal year");
        impactSub.setFont(font(Font.PLAIN, 12));
        impactSub.setForeground(MUTED);
        JPanel impactLabels = new JPanel(new GridLayout(2, 1, 0, 0));
        impactLabels.setOpaque(false);
        impactLabels.setBorder(new EmptyBorder(0, 0, 0, 24));
        impactLabels.add(impactTitle);
        impactLabels.add(impactSub);

        JPanel impactStats = new JPanel(new GridLayout(1, 3, 0, 0));
        impactStats.setOpaque(false);
        impactStats.add(impactSupply);
        impactStats.add(impactUnmet);
        impactStats.add(impactShortage);

        RoundedPanel strip = new RoundedPanel(8, Color.WHITE, LINE);
        strip.setLayout(new BorderLayout());
        strip.setBorder(new EmptyBorder(10, 18, 10, 18));
        strip.add(impactLabels, BorderLayout.WEST);
        strip.add(impactStats, BorderLayout.CENTER);

        // ----- Left column: El Niño slider -----
        JLabel dropText = new JLabel("How much supply falls in an El Niño year");
        dropText.setForeground(MUTED);
        JLabel dropValue = new JLabel("15%");
        dropValue.setFont(font(Font.BOLD, 14));
        dropValue.setForeground(TEXT);
        JPanel dropRow = new JPanel(new BorderLayout());
        dropRow.setOpaque(false);
        dropRow.add(dropText, BorderLayout.WEST);
        dropRow.add(dropValue, BorderLayout.EAST);

        JSlider slider = new JSlider(0, 100, 15);
        slider.setOpaque(false);

        JPanel dropBody = new JPanel(new GridLayout(2, 1, 0, 4));
        dropBody.setOpaque(false);
        dropBody.add(dropRow);
        dropBody.add(slider);

        JPanel dropSection = section(8);
        dropSection.add(cardTitle("El Niño scenario"), BorderLayout.NORTH);
        dropSection.add(dropBody, BorderLayout.CENTER);

        // ----- Left column: Regions table -----
        DefaultComboBoxModel<String> nameModel = new DefaultComboBoxModel<>(IndiaStates.NAMES);

        DefaultTableModel model = new DefaultTableModel(new String[]{"State", "Supply", "Demand"}, 0);
        JTable table = new JTable(model);
        styleTable(table, 1);
        table.getColumnModel().getColumn(0).setPreferredWidth(170);
        table.getColumnModel().getColumn(0)
                .setCellEditor(new DefaultCellEditor(new JComboBox<>(nameModel)));
        loadExample(model);

        RoundedButton addButton = secondaryButton("+ Add");
        RoundedButton removeButton = secondaryButton("Remove");
        RoundedButton exampleButton = secondaryButton("Example");
        RoundedButton clearButton = new RoundedButton("Clear", Color.WHITE,
                new Color(250, 238, 232), new Color(190, 196, 182), BAD, 13);
        clearButton.setPreferredSize(new Dimension(80, 36));

        JPanel buttonRow = new JPanel(new GridLayout(1, 4, 8, 0));
        buttonRow.setOpaque(false);
        buttonRow.add(addButton);
        buttonRow.add(removeButton);
        buttonRow.add(exampleButton);
        buttonRow.add(clearButton);

        JPanel regionsCard = section(10);
        regionsCard.add(cardTitle("Regions"), BorderLayout.NORTH);
        regionsCard.add(styledScroll(table), BorderLayout.CENTER);
        regionsCard.add(buttonRow, BorderLayout.SOUTH);

        // ----- Main button -----
        RoundedButton matchButton = new RoundedButton("Compare Normal vs El Niño", PRIMARY,
                PRIMARY_HOVER, null, INK, 15);
        matchButton.setPreferredSize(new Dimension(10, 52));

        // ----- Left column: collapsible sidebar -----
        final int SIDEBAR_OPEN = 410;
        final int SIDEBAR_CLOSED = 40;

        JPanel leftContent = new JPanel(new BorderLayout(0, 14));
        leftContent.setOpaque(false);
        leftContent.add(dropSection, BorderLayout.NORTH);
        leftContent.add(regionsCard, BorderLayout.CENTER);
        leftContent.add(matchButton, BorderLayout.SOUTH);

        JLabel sideTitle = new JLabel("Inputs");
        sideTitle.setFont(serif(Font.BOLD, 20));
        sideTitle.setForeground(TEXT);

        RoundedButton toggleButton = new RoundedButton("\u2039", Color.WHITE,
                new Color(244, 245, 238), new Color(190, 196, 182), TEXT, 20);
        toggleButton.setPreferredSize(new Dimension(36, 36));
        toggleButton.setToolTipText("Hide the inputs panel");

        JPanel sideHeader = new JPanel(new BorderLayout());
        sideHeader.setOpaque(false);
        sideHeader.setBorder(new EmptyBorder(0, 0, 10, 0));
        sideHeader.add(sideTitle, BorderLayout.WEST);
        sideHeader.add(toggleButton, BorderLayout.EAST);

        JPanel left = new JPanel(new BorderLayout());
        left.setOpaque(false);
        left.setPreferredSize(new Dimension(SIDEBAR_OPEN, 0));
        left.add(sideHeader, BorderLayout.NORTH);
        left.add(leftContent, BorderLayout.CENTER);

        boolean[] open = {true};
        javax.swing.Timer[] slide = {null};

        Runnable toggle = () -> {
            if (slide[0] != null && slide[0].isRunning()) return;

            final boolean opening = !open[0];
            final int from = left.getPreferredSize().width;
            final int to = opening ? SIDEBAR_OPEN : SIDEBAR_CLOSED;

            // When closing, hide the content first and then shrink the panel
            if (!opening) {
                leftContent.setVisible(false);
                sideTitle.setVisible(false);
            }
            toggleButton.setText(opening ? "\u2039" : "\u203A");
            toggleButton.setToolTipText(opening ? "Hide the inputs panel" : "Show the inputs panel");

            final int steps = 10;
            int[] step = {0};
            slide[0] = new javax.swing.Timer(15, ev -> {
                step[0]++;
                double t = step[0] / (double) steps;
                double eased = 1 - Math.pow(1 - t, 3);
                int w = (int) Math.round(from + (to - from) * eased);
                left.setPreferredSize(new Dimension(w, 0));
                left.revalidate();
                left.repaint();

                if (step[0] >= steps) {
                    ((javax.swing.Timer) ev.getSource()).stop();
                    left.setPreferredSize(new Dimension(to, 0));
                    if (opening) {
                        leftContent.setVisible(true);
                        sideTitle.setVisible(true);
                    }
                    open[0] = opening;
                    left.revalidate();
                    left.repaint();
                }
            });
            slide[0].start();
        };
        toggleButton.addActionListener(e -> toggle.run());
        // ----- Right column: impact strip + the two scenarios side by side -----
        JPanel scenarios = new JPanel(new GridLayout(1, 2, 18, 0));
        scenarios.setOpaque(false);
        scenarios.add(normalPanel);
        scenarios.add(elNinoPanel);

        JPanel right = new JPanel(new BorderLayout(0, 14));
        right.setOpaque(false);
        right.add(strip, BorderLayout.NORTH);
        right.add(scenarios, BorderLayout.CENTER);

        // ----- Put it all together -----
        JPanel body = new JPanel(new BorderLayout(18, 0));
        body.setOpaque(false);
        body.setBorder(new EmptyBorder(18, 18, 18, 18));
        body.add(left, BorderLayout.WEST);
        body.add(right, BorderLayout.CENTER);
        // ----- Two views: Dashboard and Compare -----
        CardLayout cards = new CardLayout();
        JPanel pages = new JPanel(cards);
        pages.setOpaque(false);
        DashboardPanel dashboard = new DashboardPanel();
        pages.add(dashboard, "dashboard");
        pages.add(body, "compare");
        root.add(pages, BorderLayout.CENTER);

        boolean[] onDashboard = {true};
        cards.show(pages, "dashboard");
        detailsButton.setVisible(false);
        viewButton.addActionListener(e -> {
            onDashboard[0] = !onDashboard[0];
            cards.show(pages, onDashboard[0] ? "dashboard" : "compare");
            viewButton.setText(onDashboard[0] ? "Compare view" : "Dashboard");
            detailsButton.setVisible(!onDashboard[0]);
        });

        boolean[] detailsShown = {true};
        detailsButton.addActionListener(e -> {
            detailsShown[0] = !detailsShown[0];
            strip.setVisible(detailsShown[0]);
            normalPanel.setDetailsVisible(detailsShown[0]);
            elNinoPanel.setDetailsVisible(detailsShown[0]);
            detailsButton.setText(detailsShown[0] ? "Hide details" : "Show details");
            right.revalidate();
            right.repaint();
        });

        // ----- Calculation: runs both scenarios from the same table -----
        boolean[] hasResult = {false};

        Consumer<Boolean> calculate = loud -> {
            stopEditing(table);

            if (model.getRowCount() < 2) {
                if (loud) JOptionPane.showMessageDialog(frame, "Please add at least 2 regions.");
                return;
            }

            try {
                List<Region> base = new ArrayList<>();
                Set<String> seen = new HashSet<>();

                for (int i = 0; i < model.getRowCount(); i++) {
                    String name = cell(model, i, 0);
                    if (!seen.add(name)) {
                        if (loud) JOptionPane.showMessageDialog(frame,
                                name + " appears twice. Each place can be listed only once.");
                        return;
                    }
                    int supply = Integer.parseInt(cell(model, i, 1));
                    int demand = Integer.parseInt(cell(model, i, 2));
                    if (supply < 0 || demand < 0) {
                        if (loud) JOptionPane.showMessageDialog(frame,
                                "Supply and Demand cannot be negative (" + name + ").");
                        return;
                    }
                    base.add(new Region(name, supply, demand));
                }

                ScenarioResult normal = compute(base, 0);
                ScenarioResult elNino = compute(base, slider.getValue());

                normalPanel.showResult(normal);
                elNinoPanel.showResult(elNino);

                int lost = normal.totalSupply - elNino.totalSupply;
                int extra = elNino.unmet - normal.unmet;
                impactSupply.set(lost > 0 ? "-" + fmt(lost) : "0", TEXT);
                impactUnmet.set(extra > 0 ? "+" + fmt(extra) : "0", extra > 0 ? BAD : GOOD);
                impactShortage.set(pct(normal.pct) + " to " + pct(elNino.pct),
                        elNino.pct > normal.pct ? BAD : GOOD);

                hasResult[0] = true;
                dashboard.update(normal.regions, normal.transfers,
                        elNino.regions, elNino.transfers, slider.getValue());

            } catch (NumberFormatException ex) {
                if (loud) JOptionPane.showMessageDialog(frame,
                        "Supply and Demand must be whole numbers (no commas or letters).");
            }
        };

        // ----- Button actions -----

        matchButton.addActionListener(e -> calculate.accept(true));

        // Fill everything with the example straight away, so the dashboard is never empty
        calculate.accept(false);

        // After the first comparison, moving the slider updates both panels live
        slider.addChangeListener(e -> {
            dropValue.setText(slider.getValue() + "%");
            elNinoPanel.setSubtitle("Supply reduced by " + slider.getValue() + "%");
            if (hasResult[0]) calculate.accept(false);
        });

        // Add Region: opens the form so you choose the place yourself
        addButton.addActionListener(e -> {
            stopEditing(table);
            Set<String> used = new HashSet<>();
            for (int i = 0; i < model.getRowCount(); i++) {
                used.add(cell(model, i, 0));
            }
            String[] row = showAddDialog(frame, nameModel, used);
            if (row != null) {
                model.addRow(new Object[]{row[0], row[1], row[2]});
            }
        });

        removeButton.addActionListener(e -> {
            stopEditing(table);
            int[] rows = table.getSelectedRows();
            for (int i = rows.length - 1; i >= 0; i--) {
                model.removeRow(rows[i]);
            }
        });

        exampleButton.addActionListener(e -> {
            stopEditing(table);
            loadExample(model);
        });

        clearButton.addActionListener(e -> {
            stopEditing(table);
            model.setRowCount(0);
            normalPanel.clear();
            elNinoPanel.clear();
            impactSupply.set("-", TEXT);
            impactUnmet.set("-", TEXT);
            impactShortage.set("-", TEXT);
            hasResult[0] = false;
            dashboard.clear();
        });

        frame.setVisible(true);
    }
}