import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DashboardPanel extends JPanel {

    // One region's numbers for both years
    private static class Row {
        String name;
        int demand, supplyN, supplyE, shortE, unmetN, unmetE;
    }

    private final List<Row> rows = new ArrayList<>();
    private boolean hasData = false;
    private int dropPercent = 0;
    private int supplyN, supplyE, demandTotal, unmetN, unmetE, worstUnmet;
    private double pctN, pctE;
    private String worstName = "";

    private List<Region> normalRegions = new ArrayList<>();
    private List<Transfer> normalTransfers = new ArrayList<>();
    private List<Region> elRegions = new ArrayList<>();
    private List<Transfer> elTransfers = new ArrayList<>();

    private final Banner banner = new Banner();
    private final Dash.Kpi kpiLost = new Dash.Kpi("Grain supply lost in El Niño");
    private final Dash.Kpi kpiUnmet = new Dash.Kpi("Demand left unmet");
    private final Dash.Kpi kpiShortage = new Dash.Kpi("Shortage");
    private final Dash.Kpi kpiWorst = new Dash.Kpi("Most affected region");
    private final BarChart supplyChart = new BarChart(false);
    private final BarChart gapChart = new BarChart(true);
    private final JLabel supplySub = new JLabel(" ");
    private final MapPanel map = new MapPanel();
    private final Dash.Chip normalChip = new Dash.Chip("Normal");
    private final Dash.Chip elChip = new Dash.Chip("El Niño");

    public DashboardPanel() {
        setOpaque(false);
        setLayout(new BorderLayout());

        // Row of key numbers
        JPanel kpis = new JPanel(new GridLayout(1, 4, 14, 0));
        kpis.setOpaque(false);
        kpis.add(kpiLost);
        kpis.add(kpiUnmet);
        kpis.add(kpiShortage);
        kpis.add(kpiWorst);

        // Normal / El Nino switch for the map
        ButtonGroup group = new ButtonGroup();
        group.add(normalChip);
        group.add(elChip);
        elChip.setSelected(true);
        normalChip.addActionListener(e -> refreshMap());
        elChip.addActionListener(e -> refreshMap());
        JPanel chips = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        chips.setOpaque(false);
        chips.add(normalChip);
        chips.add(elChip);

        map.setBorder(BorderFactory.createLineBorder(new Color(150, 168, 172)));

        // Three visuals side by side
        JPanel charts = new JPanel(new GridLayout(1, 3, 14, 0));
        charts.setOpaque(false);
        charts.setPreferredSize(new Dimension(10, 420));
        charts.add(chartCard("Supply vs demand by region", supplySub, null, supplyChart));
        charts.add(chartCard("Shortfall before and after matching",
                new JLabel("El Niño year: how much the transfers close the gap"), null, gapChart));
        charts.add(chartCard("Where the grain moves",
                new JLabel("Blue arrows go from surplus to deficit regions"), chips, map));

        // Everything stacked, inside a scroll area for small screens
        Dash.Content content = new Dash.Content();
        content.setLayout(new GridBagLayout());
        content.setBorder(new EmptyBorder(18, 18, 18, 18));

        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(0, 0, 14, 0);
        c.gridy = 0;
        content.add(banner, c);
        c.gridy = 1;
        content.add(new Dash.Steps(), c);
        c.gridy = 2;
        content.add(kpis, c);
        c.gridy = 3;
        c.weighty = 1;
        c.fill = GridBagConstraints.BOTH;
        c.insets = new Insets(0, 0, 0, 0);
        content.add(charts, c);

        JScrollPane scroll = new JScrollPane(content,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.getVerticalScrollBar().setUnitIncrement(24);
        add(scroll, BorderLayout.CENTER);

        clear();
    }

    // A white card with a title, a short explanation, and the chart inside
    private Dash.Card chartCard(String title, JLabel sub, JComponent headerRight, JComponent body) {
        Dash.Card card = new Dash.Card();
        card.setLayout(new BorderLayout(0, 8));
        card.setBorder(new EmptyBorder(14, 16, 14, 16));

        JLabel t = new JLabel(title);
        t.setFont(Dash.serif(Font.BOLD, 17));
        t.setForeground(Dash.TEXT);
        sub.setFont(Dash.sans(Font.PLAIN, 12));
        sub.setForeground(Dash.MUTED);

        JPanel texts = new JPanel(new GridLayout(2, 1, 0, 0));
        texts.setOpaque(false);
        texts.add(t);
        texts.add(sub);

        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        head.add(texts, BorderLayout.CENTER);
        if (headerRight != null) head.add(headerRight, BorderLayout.EAST);

        card.add(head, BorderLayout.NORTH);
        card.add(body, BorderLayout.CENTER);
        return card;
    }

    private void refreshMap() {
        if (!hasData) {
            map.setData(new ArrayList<>(), new ArrayList<>());
        } else if (elChip.isSelected()) {
            map.setData(elRegions, elTransfers);
        } else {
            map.setData(normalRegions, normalTransfers);
        }
    }

    private static Map<String, Integer> received(List<Transfer> transfers) {
        Map<String, Integer> m = new HashMap<>();
        for (Transfer t : transfers) {
            m.merge(t.to, t.amount, Integer::sum);
        }
        return m;
    }

    private static int unmetOf(Region r, Map<String, Integer> rec) {
        int bal = r.getBalance();
        return bal < 0 ? Math.max(0, -bal - rec.getOrDefault(r.name, 0)) : 0;
    }

    // Called by the main window after every calculation
    public void update(List<Region> nRegions, List<Transfer> nTransfers,
                       List<Region> eRegions, List<Transfer> eTransfers, int drop) {
        normalRegions = nRegions;
        normalTransfers = nTransfers;
        elRegions = eRegions;
        elTransfers = eTransfers;
        dropPercent = drop;

        Map<String, Integer> recN = received(nTransfers);
        Map<String, Integer> recE = received(eTransfers);

        rows.clear();
        supplyN = 0;
        supplyE = 0;
        demandTotal = 0;
        unmetN = 0;
        unmetE = 0;
        worstUnmet = 0;
        worstName = "";

        for (int i = 0; i < nRegions.size() && i < eRegions.size(); i++) {
            Region n = nRegions.get(i);
            Region el = eRegions.get(i);
            Row r = new Row();
            r.name = n.name;
            r.demand = n.demand;
            r.supplyN = n.supply;
            r.supplyE = el.supply;
            r.shortE = Math.max(0, el.demand - el.supply);
            r.unmetN = unmetOf(n, recN);
            r.unmetE = unmetOf(el, recE);
            rows.add(r);

            supplyN += r.supplyN;
            supplyE += r.supplyE;
            demandTotal += r.demand;
            unmetN += r.unmetN;
            unmetE += r.unmetE;
            if (r.unmetE > worstUnmet) {
                worstUnmet = r.unmetE;
                worstName = r.name;
            }
        }
        pctN = demandTotal > 0 ? unmetN * 100.0 / demandTotal : 0;
        pctE = demandTotal > 0 ? unmetE * 100.0 / demandTotal : 0;
        hasData = true;

        // Key numbers
        int lost = supplyN - supplyE;
        kpiLost.set(lost > 0 ? "-" + Dash.fmt(lost) : "0", Dash.TEXT,
                Dash.fmt(supplyN) + " in a normal year, " + Dash.fmt(supplyE) + " in El Niño");
        kpiUnmet.set(Dash.fmt(unmetE), unmetE > 0 ? Dash.BAD : Dash.GOOD,
                "Normal year: " + Dash.fmt(unmetN));
        kpiShortage.set(Dash.pct(pctE), unmetE > 0 ? Dash.BAD : Dash.GOOD,
                "Normal year: " + Dash.pct(pctN));
        kpiWorst.set(worstUnmet > 0 ? worstName : "None", worstUnmet > 0 ? Dash.BAD : Dash.GOOD,
                worstUnmet > 0 ? Dash.fmt(worstUnmet) + " units short" : "Every region's need is met");

        // Most affected regions first
        List<Row> sorted = new ArrayList<>(rows);
        sorted.sort((a, b) -> {
            if (a.unmetE != b.unmetE) return Integer.compare(b.unmetE, a.unmetE);
            return Integer.compare(a.supplyE - a.demand, b.supplyE - b.demand);
        });

        // Chart 1: demand, normal supply, El Nino supply
        int n1 = Math.min(8, sorted.size());
        String[] l1 = new String[n1];
        double[][] v1 = new double[3][n1];
        for (int i = 0; i < n1; i++) {
            Row r = sorted.get(i);
            l1[i] = r.name;
            v1[0][i] = r.demand;
            v1[1][i] = r.supplyN;
            v1[2][i] = r.supplyE;
        }
        supplyChart.setData(l1, v1, new Color[]{Dash.WHEAT, Dash.TEAL, Dash.BAD},
                new String[]{"Demand", "Supply (normal)", "Supply (El Niño)"}, "");
        supplySub.setText(sorted.size() > 8
                ? "Showing the 8 most affected of " + sorted.size() + " regions"
                : "Gold = need. A bar shorter than gold is a shortfall.");

        // Chart 2: shortfall before matching vs still missing after matching (El Nino year)
        List<Row> deficit = new ArrayList<>();
        for (Row r : sorted) {
            if (r.shortE > 0) deficit.add(r);
        }
        int n2 = Math.min(8, deficit.size());
        String[] l2 = new String[n2];
        double[][] v2 = new double[2][n2];
        for (int i = 0; i < n2; i++) {
            Row r = deficit.get(i);
            l2[i] = r.name;
            v2[0][i] = r.shortE;
            v2[1][i] = r.unmetE;
        }
        gapChart.setData(l2, v2, new Color[]{Dash.WHEAT, Dash.BAD},
                new String[]{"Shortfall", "Still missing after matching"},
                "No region falls short in the El Niño year. Every need is met.");

        banner.repaint();
        refreshMap();
    }

    public void clear() {
        hasData = false;
        rows.clear();
        kpiLost.set("-", Dash.TEXT, " ");
        kpiUnmet.set("-", Dash.TEXT, " ");
        kpiShortage.set("-", Dash.TEXT, " ");
        kpiWorst.set("-", Dash.TEXT, " ");

        String msg = "Add at least two regions in the Compare view and press Compare.";
        supplyChart.setData(new String[0], new double[0][0], new Color[0], new String[0], msg);
        gapChart.setData(new String[0], new double[0][0], new Color[0], new String[0], msg);
        supplySub.setText(" ");
        banner.repaint();
        refreshMap();
    }

    // The headline at the top: the result in one plain sentence
    private class Banner extends Dash.Card {
        Banner() {
            setPreferredSize(new Dimension(10, 108));
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            Dash.smooth(g2);

            Color accent = !hasData ? Dash.MUTED : (unmetE > 0 ? Dash.BAD : Dash.GOOD);
            g2.setColor(accent);
            g2.fillRect(0, 12, 6, getHeight() - 24);

            int x = 28;
            int maxW = getWidth() - x - 24;
            g2.setFont(Dash.sans(Font.BOLD, 11));
            g2.setColor(Dash.MUTED);
            g2.drawString("THE RESULT AT A GLANCE", x, 28);

            String head, sub;
            if (!hasData) {
                head = "Add your regions, then press Compare to see the result";
                sub = "This tool moves surplus grain to regions that fall short, and compares a normal year with an El Niño year.";
            } else {
                if (dropPercent == 0) {
                    head = "With no supply drop, both years look the same";
                } else if (unmetE == 0) {
                    head = "Even with a " + dropPercent + "% fall in supply, every region's demand is met";
                } else if (unmetE > unmetN) {
                    head = "A " + dropPercent + "% fall in grain supply leaves " + Dash.pct(pctE) + " of demand unmet";
                } else {
                    head = Dash.pct(pctE) + " of demand stays unmet";
                }
                sub = "Normal year: " + Dash.pct(pctN) + " unmet   \u2022   El Niño year: " + Dash.pct(pctE) + " unmet";
                if (worstUnmet > 0) {
                    sub += "   \u2022   Most affected: " + worstName + " (" + Dash.fmt(worstUnmet) + " units short)";
                }
            }

            g2.setFont(Dash.fit(g2, head, maxW, true, Font.BOLD, 26));
            g2.setColor(Dash.TEXT);
            g2.drawString(head, x, 64);
            g2.setFont(Dash.fit(g2, sub, maxW, false, Font.PLAIN, 14));
            g2.setColor(Dash.MUTED);
            g2.drawString(sub, x, 90);
            g2.dispose();
        }
    }
}