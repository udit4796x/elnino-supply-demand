import javax.swing.*;
import java.awt.*;

public class BarChart extends JPanel {

    private String[] labels = new String[0];
    private double[][] values = new double[0][0];   // values[series][group]
    private Color[] colors = new Color[0];
    private String[] names = new String[0];
    private String emptyMessage = "";
    private final boolean exactNumbers;

    public BarChart(boolean exactNumbers) {
        this.exactNumbers = exactNumbers;
        setOpaque(false);
    }

    public void setData(String[] labels, double[][] values, Color[] colors,
                        String[] names, String emptyMessage) {
        this.labels = labels;
        this.values = values;
        this.colors = colors;
        this.names = names;
        this.emptyMessage = emptyMessage;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        Dash.smooth(g2);
        int w = getWidth(), h = getHeight();

        // Legend (top row)
        g2.setFont(Dash.sans(Font.PLAIN, 12));
        FontMetrics fm = g2.getFontMetrics();
        int lx = 2;
        for (int s = 0; s < names.length; s++) {
            g2.setColor(colors[s]);
            g2.fillRoundRect(lx, 4, 11, 11, 3, 3);
            g2.setColor(Dash.TEXT);
            g2.drawString(names[s], lx + 16, 14);
            lx += 16 + fm.stringWidth(names[s]) + 16;
        }

        // Nothing to show
        if (labels.length == 0) {
            g2.setFont(Dash.sans(Font.PLAIN, 13));
            g2.setColor(Dash.MUTED);
            Dash.drawWrapped(g2, emptyMessage, 8, h / 2 - 8, w - 16);
            g2.dispose();
            return;
        }

        int left = 46, right = 8, top = 34, bottom = 30;
        int plotW = w - left - right;
        int plotH = h - top - bottom;
        if (plotW < 40 || plotH < 40) {
            g2.dispose();
            return;
        }

        // Scale
        double max = 0;
        for (double[] row : values) {
            for (double v : row) max = Math.max(max, v);
        }
        if (max <= 0) max = 1;
        double step = Dash.niceStep(max / 4.0);
        double axisMax = Math.ceil(max / step) * step;
        if (max > axisMax * 0.92) axisMax += step;   // room for the number above the tallest bar
        int ticks = (int) Math.round(axisMax / step);

        // Gridlines and the numbers on the left
        g2.setFont(Dash.sans(Font.PLAIN, 11));
        fm = g2.getFontMetrics();
        for (int i = 0; i <= ticks; i++) {
            double v = i * step;
            int y = top + plotH - (int) Math.round(v / axisMax * plotH);
            g2.setColor(i == 0 ? new Color(150, 158, 160) : new Color(226, 229, 220));
            g2.drawLine(left, y, left + plotW, y);
            String t = Dash.compact(v);
            g2.setColor(Dash.MUTED);
            g2.drawString(t, left - 6 - fm.stringWidth(t), y + 4);
        }

        // Bars
        int groups = labels.length;
        int series = values.length;
        double groupW = plotW / (double) groups;
        int barW = (int) Math.max(6, Math.min(46, groupW * 0.72 / series));
        int total = barW * series;

        Font valueFont = Dash.sans(Font.BOLD, 10);
        FontMetrics vfm = g2.getFontMetrics(valueFont);
        Font labelFont = Dash.sans(Font.PLAIN, 12);
        FontMetrics lfm = g2.getFontMetrics(labelFont);

        for (int gi = 0; gi < groups; gi++) {
            double gx = left + gi * groupW;
            int startX = (int) (gx + (groupW - total) / 2);

            for (int s = 0; s < series; s++) {
                double v = values[s][gi];
                int bh = (int) Math.round(v / axisMax * plotH);
                int x = startX + s * barW;
                int y = top + plotH - bh;

                g2.setColor(colors[s]);
                if (bh > 0) g2.fillRect(x, y, barW - 1, bh);

                String t = exactNumbers ? Dash.fmt(Math.round(v)) : Dash.compact(v);
                if (vfm.stringWidth(t) <= barW) {
                    g2.setFont(valueFont);
                    g2.setColor(Dash.TEXT);
                    g2.drawString(t, x + (barW - 1 - vfm.stringWidth(t)) / 2, y - 4);
                }
            }

            // Region name under the group
            g2.setFont(labelFont);
            String name = Dash.ellipsize(lfm, labels[gi], (int) groupW - 4);
            g2.setColor(Dash.TEXT);
            g2.drawString(name, (int) (gx + (groupW - lfm.stringWidth(name)) / 2), top + plotH + 19);
        }
        g2.dispose();
    }
}