import javax.swing.*;
import java.awt.*;
import java.awt.geom.*;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MapPanel extends JPanel {

    private static final String MAP_FILE = "india-soi.geojson";
    private static final double LON_STRETCH = 0.92; // keeps India from looking too wide

    // Atlas look: pale sea, wheat-coloured land, ink for lines and text
    private static final Color GREEN = new Color(63, 125, 78);
    private static final Color TEAL = new Color(58, 128, 138);
    private static final Color RED = new Color(181, 67, 47);
    private static final Color GRAY = new Color(150, 156, 156);
    private static final Color BLUE = new Color(33, 70, 110);      // transfer arrows
    private static final Color TEXT = new Color(33, 46, 58);
    private static final Color MUTED = new Color(104, 114, 118);
    private static final Color SEA = new Color(214, 227, 230);
    private static final Color GRID = new Color(255, 255, 255, 150);
    private static final Color LAND_FILL = new Color(241, 233, 208);
    private static final Color LAND_LINE = new Color(143, 130, 98);
    private static final Color PILL_LINE = new Color(190, 196, 182);
    private static final String SANS = Fonts.SANS;

    private static final String[] LEGEND_NAMES =
            {"Surplus region", "Deficit fully covered", "Deficit still short", "Grain transfer"};
    private static final Color[] LEGEND_COLORS = {GREEN, TEAL, RED, BLUE};

    // ---- The map file is read only once and shared by every MapPanel ----
    private static boolean sLoaded = false;
    private static Path2D.Double sPath = null;
    private static String sError = null;
    private static double sMinLon = 68, sMaxLon = 98, sMinLat = 6, sMaxLat = 37.5;

    private List<Region> regions = new ArrayList<>();
    private List<Transfer> transfers = new ArrayList<>();

    private Path2D.Double mapPath = null;
    private String loadError = null;
    private double minLon = 68, maxLon = 98, minLat = 6, maxLat = 37.5;

    private Shape cachedShape = null;
    private int cachedW = -1, cachedH = -1;
    private double cachedZoom = -1, cachedPanX = Double.NaN, cachedPanY = Double.NaN;

    // View state: zoom 1 = whole India fits the panel
    private static final double MIN_ZOOM = 1.0, MAX_ZOOM = 12.0;
    private double zoom = 1.0;
    private double panX = 0, panY = 0;   // screen-pixel offset after zooming
    private int dragX, dragY;

    // The other map (so both maps zoom and move together)
    private MapPanel partner = null;

    public MapPanel() {
        setOpaque(true);
        setBackground(SEA);
        ensureLoaded();
        mapPath = sPath;
        loadError = sError;
        minLon = sMinLon;
        maxLon = sMaxLon;
        minLat = sMinLat;
        maxLat = sMaxLat;
        setupZoomAndPan();
    }

    // Makes this map and another map share the same zoom and position
    public void linkWith(MapPanel other) {
        this.partner = other;
        other.partner = this;
    }

    private void syncPartner() {
        if (partner != null) partner.applyView(zoom, panX, panY);
    }

    private void applyView(double z, double px, double py) {
        zoom = z;
        panX = px;
        panY = py;
        repaint();
    }

    // Mouse wheel = zoom at the cursor, drag = move the map, double-click = reset
    private void setupZoomAndPan() {
        addMouseWheelListener(e -> {
            double factor = Math.pow(1.15, -e.getPreciseWheelRotation());
            zoomAt(e.getX(), e.getY(), factor);
        });

        java.awt.event.MouseAdapter drag = new java.awt.event.MouseAdapter() {
            @Override public void mousePressed(java.awt.event.MouseEvent e) {
                dragX = e.getX();
                dragY = e.getY();
                setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
            }
            @Override public void mouseDragged(java.awt.event.MouseEvent e) {
                panX += e.getX() - dragX;
                panY += e.getY() - dragY;
                dragX = e.getX();
                dragY = e.getY();
                clampPan();
                repaint();
                syncPartner();
            }
            @Override public void mouseReleased(java.awt.event.MouseEvent e) {
                setCursor(Cursor.getDefaultCursor());
            }
            @Override public void mouseClicked(java.awt.event.MouseEvent e) {
                if (plusBox().contains(e.getPoint())) { zoomIn(); return; }
                if (minusBox().contains(e.getPoint())) { zoomOut(); return; }
                if (resetBox().contains(e.getPoint())) { resetView(); return; }
                if (e.getClickCount() == 2) resetView();
            }
        };
        addMouseListener(drag);
        addMouseMotionListener(drag);
    }

    public void zoomIn()  { zoomAt(getWidth() / 2.0, getHeight() / 2.0, 1.4); }
    public void zoomOut() { zoomAt(getWidth() / 2.0, getHeight() / 2.0, 1 / 1.4); }

    public void resetView() {
        zoom = 1.0;
        panX = 0;
        panY = 0;
        repaint();
        syncPartner();
    }

    // Zooms so the map point under (cx, cy) stays under the cursor
    private void zoomAt(double cx, double cy, double factor) {
        double newZoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom * factor));
        double k = newZoom / zoom;
        double mx = getWidth() / 2.0, my = getHeight() / 2.0;
        panX = (cx - mx) - k * (cx - mx - panX);
        panY = (cy - my) - k * (cy - my - panY);
        zoom = newZoom;
        clampPan();
        repaint();
        syncPartner();
    }

    // Stops the map being dragged completely out of view
    private void clampPan() {
        double limX = getWidth() * 0.5 * zoom;
        double limY = getHeight() * 0.5 * zoom;
        panX = Math.max(-limX, Math.min(limX, panX));
        panY = Math.max(-limY, Math.min(limY, panY));
        if (zoom <= 1.0001) { panX = 0; panY = 0; }
    }

    // Called by the GUI after matching
    public void setData(List<Region> regions, List<Transfer> transfers) {
        this.regions = regions;
        this.transfers = transfers;
        repaint();
    }

    // ---------- Loading the GeoJSON file (once) ----------

    private static synchronized void ensureLoaded() {
        if (sLoaded) return;
        sLoaded = true;

        try {
            byte[] data;
            java.io.InputStream in = MapPanel.class.getResourceAsStream("/" + MAP_FILE);
            if (in != null) {
                try (java.io.InputStream is = in) {
                    data = is.readAllBytes();
                }
            } else {
                File f = new File(MAP_FILE);
                if (!f.exists()) {
                    sError = "Map file not found.\nPut " + MAP_FILE + " in this folder:\n"
                            + new File(".").getAbsolutePath();
                    return;
                }
                data = Files.readAllBytes(f.toPath());
            }
            String text = new String(data, StandardCharsets.UTF_8);
            Path2D.Double path = new Path2D.Double();

            int from = 0;
            while (true) {
                int idx = text.indexOf("\"coordinates\"", from);
                if (idx < 0) break;
                int start = text.indexOf('[', idx);
                if (start < 0) break;
                int[] pos = {start};
                Object tree = parseArray(text, pos);
                collectRings(tree, path);
                from = pos[0];
            }

            Rectangle2D b = path.getBounds2D();
            if (b.getWidth() > 0 && b.getHeight() > 0) {
                sPath = path;
                sMinLon = b.getMinX();
                sMaxLon = b.getMaxX();
                sMinLat = b.getMinY();
                sMaxLat = b.getMaxY();
            } else {
                sError = "Map file was read, but no shapes were found in it.";
            }
        } catch (Exception | OutOfMemoryError e) {
            sError = "Could not read the map file:\n" + e;
        }
    }

    private static int skipWs(String s, int p) {
        while (p < s.length() && Character.isWhitespace(s.charAt(p))) p++;
        return p;
    }

    // Reads a nested JSON array. Returns a List (of lists) or a double[] {lon, lat}
    private static Object parseArray(String s, int[] pos) {
        int p = skipWs(s, pos[0] + 1);

        if (s.charAt(p) == ']') {
            pos[0] = p + 1;
            return new ArrayList<Object>();
        }

        if (s.charAt(p) == '[') {
            List<Object> list = new ArrayList<>();
            while (true) {
                p = skipWs(s, p);
                pos[0] = p;
                list.add(parseArray(s, pos));
                p = skipWs(s, pos[0]);
                char c = s.charAt(p++);
                if (c == ']') break;
            }
            pos[0] = p;
            return list;
        }

        double[] vals = new double[2];
        int n = 0;
        while (true) {
            p = skipWs(s, p);
            int q = p;
            while (q < s.length() && "+-.0123456789eE".indexOf(s.charAt(q)) >= 0) q++;
            double v = Double.parseDouble(s.substring(p, q));
            if (n < 2) vals[n++] = v;
            p = skipWs(s, q);
            char c = s.charAt(p++);
            if (c == ']') break;
        }
        pos[0] = p;
        return vals;
    }

    // Finds every ring of points and adds it to the path
    private static void collectRings(Object o, Path2D.Double path) {
        if (!(o instanceof List)) return;
        List<?> list = (List<?>) o;
        if (list.isEmpty()) return;
        if (list.get(0) instanceof double[]) {
            addRing(list, path);
        } else {
            for (Object child : list) collectRings(child, path);
        }
    }

    // Adds one ring, skipping points that are very close together (keeps drawing fast)
    private static void addRing(List<?> ring, Path2D.Double path) {
        double minDist2 = 0.03 * 0.03;
        Path2D.Double tmp = new Path2D.Double();
        boolean started = false;
        double lastX = 0, lastY = 0;
        int kept = 0;

        for (int i = 0; i < ring.size(); i++) {
            double[] pt = (double[]) ring.get(i);
            double x = pt[0], y = pt[1];
            boolean isLast = (i == ring.size() - 1);

            if (!started) {
                tmp.moveTo(x, y);
                started = true;
                lastX = x;
                lastY = y;
                kept++;
            } else {
                double dx = x - lastX, dy = y - lastY;
                if (dx * dx + dy * dy >= minDist2 && !isLast) {
                    tmp.lineTo(x, y);
                    lastX = x;
                    lastY = y;
                    kept++;
                }
            }
        }
        if (kept >= 3) {
            tmp.closePath();
            path.append(tmp, false);
        }
    }

    // ---------- Converting map coordinates to screen ----------

    private double scale() {
        double w = (maxLon - minLon) * LON_STRETCH;
        double h = maxLat - minLat;
        return Math.min(Math.max(1, getWidth() - 40) / w, Math.max(1, getHeight() - 40) / h);
    }

    private Point2D.Double toPoint(double lon, double lat) {
        double s = scale();
        double mapW = (maxLon - minLon) * LON_STRETCH * s;
        double mapH = (maxLat - minLat) * s;
        double offX = (getWidth() - mapW) / 2.0;
        double offY = (getHeight() - mapH) / 2.0;
        double x = offX + (lon - minLon) * LON_STRETCH * s;
        double y = offY + (maxLat - lat) * s;
        double mx = getWidth() / 2.0, my = getHeight() / 2.0;
        return new Point2D.Double(mx + (x - mx) * zoom + panX,
                                  my + (y - my) * zoom + panY);
    }

    private Point2D.Double regionPoint(String name) {
        double[] p = IndiaStates.getPosition(name);
        if (p == null) return null;
        return toPoint(p[0], p[1]);
    }

    private Shape getScreenShape() {
        if (cachedShape == null || cachedW != getWidth() || cachedH != getHeight()
                || cachedZoom != zoom || cachedPanX != panX || cachedPanY != panY) {
            double s = scale() * zoom;
            Point2D.Double origin = toPoint(minLon, maxLat);
            AffineTransform tx = new AffineTransform();
            tx.translate(origin.x, origin.y);
            tx.scale(s * LON_STRETCH, -s);
            tx.translate(-minLon, -maxLat);
            cachedShape = tx.createTransformedShape(mapPath);
            cachedW = getWidth();
            cachedH = getHeight();
            cachedZoom = zoom;
            cachedPanX = panX;
            cachedPanY = panY;
        }
        return cachedShape;
    }

    // ---------- Drawing ----------

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        // 0. Faint lat/long grid, like an atlas plate
        if (mapPath != null) {
            g2.setColor(GRID);
            g2.setStroke(new BasicStroke(1f));
            for (int lon = 60; lon <= 110; lon += 5) {
                Point2D.Double a = toPoint(lon, 0), b = toPoint(lon, 45);
                g2.draw(new Line2D.Double(a.x, a.y, b.x, b.y));
            }
            for (int lat = 0; lat <= 45; lat += 5) {
                Point2D.Double a = toPoint(55, lat), b = toPoint(115, lat);
                g2.draw(new Line2D.Double(a.x, a.y, b.x, b.y));
            }
        }

        // 1. The map
        if (mapPath != null) {
            Shape shape = getScreenShape();
            g2.setColor(LAND_FILL);
            g2.fill(shape);
            g2.setColor(LAND_LINE);
            g2.setStroke(new BasicStroke(1f));
            g2.draw(shape);
        } else if (loadError != null) {
            g2.setFont(new Font(SANS, Font.BOLD, 13));
            g2.setColor(RED);
            int y = 30;
            for (String line : loadError.split("\n")) {
                g2.drawString(line, 14, y);
                y += 18;
            }
        }

        // How much each deficit region received
        Map<String, Integer> received = new HashMap<>();
        for (Transfer t : transfers) {
            received.merge(t.to, t.amount, Integer::sum);
        }

        // 2. Arrows (their amount labels are placed later, once names are placed)
        List<Object[]> amountLabels = new ArrayList<>();
        for (int i = 0; i < transfers.size(); i++) {
            Transfer t = transfers.get(i);
            Point2D.Double a = regionPoint(t.from);
            Point2D.Double b = regionPoint(t.to);
            if (a != null && b != null) {
                drawArrow(g2, a, b, t.amount, i, amountLabels);
            }
        }

        // 3. Region dots
        for (Region r : regions) {
            Point2D.Double p = regionPoint(r.name);
            if (p == null) continue;

            Color c = statusColor(r, received);
            g2.setColor(c);
            g2.fill(new Ellipse2D.Double(p.x - 8, p.y - 8, 16, 16));
            g2.setColor(Color.WHITE);
            g2.setStroke(new BasicStroke(2.5f));
            g2.draw(new Ellipse2D.Double(p.x - 8, p.y - 8, 16, 16));
        }

        // Things labels must not cover: the dots, the legend and the zoom buttons
        List<Rectangle> placed = new ArrayList<>();
        for (Region r : regions) {
            Point2D.Double p = regionPoint(r.name);
            if (p != null) placed.add(new Rectangle((int) p.x - 10, (int) p.y - 10, 20, 20));
        }
        placed.add(legendBox());
        placed.add(plusBox());
        placed.add(minusBox());
        placed.add(resetBox());

        // 4. Region name pills: each one picks a free spot
        Font nameFont = new Font(SANS, Font.BOLD, 12);
        Font amtFont = new Font(SANS, Font.PLAIN, 12);
        FontMetrics fmN = g2.getFontMetrics(nameFont);
        FontMetrics fmA = g2.getFontMetrics(amtFont);
        for (Region r : regions) {
            Point2D.Double p = regionPoint(r.name);
            if (p == null) continue;

            int bal = r.getBalance();
            String amt = (bal > 0 ? "+" : "") + String.format(Locale.US, "%,d", bal);
            Color amtColor = bal > 0 ? GREEN : (bal < 0 ? RED : MUTED);

            int nameW = fmN.stringWidth(r.name);
            int amtW = fmA.stringWidth(amt);
            int w = nameW + 8 + amtW + 16;
            int h = fmN.getHeight() + 6;

            Rectangle box = placeNamePill(p, w, h, placed);
            placed.add(box);

            g2.setColor(new Color(255, 255, 255, 238));
            g2.fillRoundRect(box.x, box.y, box.width, box.height, 5, 5);
            g2.setColor(PILL_LINE);
            g2.setStroke(new BasicStroke(1f));
            g2.drawRoundRect(box.x, box.y, box.width, box.height, 5, 5);

            int base = box.y + 3 + fmN.getAscent();
            g2.setFont(nameFont);
            g2.setColor(TEXT);
            g2.drawString(r.name, box.x + 8, base);
            g2.setFont(amtFont);
            g2.setColor(amtColor);
            g2.drawString(amt, box.x + 8 + nameW + 8, base);
        }

        // 5. Amount pills on the arrows: slide along the arrow until a free spot is found
        g2.setFont(new Font(SANS, Font.BOLD, 11));
        FontMetrics fm = g2.getFontMetrics();
        double[] ts = {0.5, 0.4, 0.6, 0.3, 0.7, 0.22, 0.78, 0.15, 0.85};
        for (Object[] l : amountLabels) {
            double[] c = (double[]) l[0];   // start, control and end point of the curve
            String text = (String) l[1];
            int w = fm.stringWidth(text) + 14;
            int h = fm.getHeight() + 4;

            Rectangle best = null;
            for (double t : ts) {
                double mx = (1 - t) * (1 - t) * c[0] + 2 * t * (1 - t) * c[2] + t * t * c[4];
                double my = (1 - t) * (1 - t) * c[1] + 2 * t * (1 - t) * c[3] + t * t * c[5];
                                Rectangle box = new Rectangle((int) mx - w / 2, (int) my - h / 2, w, h);
                if (best == null) best = box;
                if (!overlapsAny(box, placed)) {
                    best = box;
                    break;
                }
            }
            placed.add(best);

            g2.setColor(BLUE);
            g2.fillRoundRect(best.x, best.y, best.width, best.height, 5, 5);
            g2.setColor(Color.WHITE);
            g2.drawString(text, best.x + 7, best.y + 2 + fm.getAscent());
        }

        drawLegend(g2);
        drawZoomControls(g2);

        // 6. Hint before the first match
        if (regions.isEmpty() && mapPath != null) {
            String msg = "Press Compare to draw the grain flow";
            g2.setFont(new Font(SANS, Font.PLAIN, 13));
            fm = g2.getFontMetrics();
            int w = fm.stringWidth(msg) + 32;
            int h = 34;
            int x = (getWidth() - w) / 2;
            int y = 10;
            g2.setColor(new Color(255, 255, 255, 220));
            g2.fillRoundRect(x, y, w, h, 5, 5);
            g2.setColor(MUTED);
            g2.drawString(msg, x + 16, y + (h - fm.getHeight()) / 2 + fm.getAscent());
        }
    }

    private static boolean overlapsAny(Rectangle box, List<Rectangle> others) {
        for (Rectangle o : others) {
            if (box.intersects(o)) return true;
        }
        return false;
    }

    // Tries the right and left of the dot, then moves up and down, until the pill is free
    private Rectangle placeNamePill(Point2D.Double p, int w, int h, List<Rectangle> placed) {
        boolean preferLeft = p.x > getWidth() * 0.6;
        int[] sides = preferLeft ? new int[]{-1, 1} : new int[]{1, -1};
        int step = h + 2;
        int[] dys = {0, -step, step, -2 * step, 2 * step, -3 * step, 3 * step};

        Rectangle fallback = null;
        for (int dy : dys) {
            for (int side : sides) {
                int x = side > 0 ? (int) p.x + 14 : (int) p.x - 14 - w;
                x = Math.max(4, Math.min(x, getWidth() - w - 4));
                Rectangle box = new Rectangle(x, (int) p.y - h / 2 + dy, w, h);
                if (fallback == null) fallback = box;
                if (!overlapsAny(box, placed)) return box;
            }
        }
        return fallback;
    }

    private Color statusColor(Region r, Map<String, Integer> received) {
        int bal = r.getBalance();
        if (bal > 0) return GREEN;
        if (bal == 0) return GRAY;
        int stillShort = -bal - received.getOrDefault(r.name, 0);
        return stillShort > 0 ? RED : TEAL;
    }

    private void drawArrow(Graphics2D g2, Point2D.Double a, Point2D.Double b,
                           int amount, int index, List<Object[]> labels) {
        double dx = b.x - a.x, dy = b.y - a.y;
        double len = Math.hypot(dx, dy);
        if (len < 1) return;

        // Curve the arrow sideways; alternate sides so arrows don't overlap
        double nx = -dy / len, ny = dx / len;
        double sign = (index % 2 == 0) ? 1 : -1;
        double bend = sign * (0.15 + 0.08 * (index / 2)) * len;
        double cx = (a.x + b.x) / 2 + nx * bend;
        double cy = (a.y + b.y) / 2 + ny * bend;

        // Directions at the start and end of the curve
        double d0x = cx - a.x, d0y = cy - a.y;
        double l0 = Math.hypot(d0x, d0y);
        d0x /= l0; d0y /= l0;
        double d1x = b.x - cx, d1y = b.y - cy;
        double l1 = Math.hypot(d1x, d1y);
        d1x /= l1; d1y /= l1;

        // Start and end a little away from the dots
        double sx = a.x + d0x * 12, sy = a.y + d0y * 12;
        double ex = b.x - d1x * 12, ey = b.y - d1y * 12;

        g2.setColor(new Color(BLUE.getRed(), BLUE.getGreen(), BLUE.getBlue(), 225));
        g2.setStroke(new BasicStroke(2.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g2.draw(new QuadCurve2D.Double(sx, sy, cx, cy, ex, ey));

        // Arrow head
        double angle = Math.atan2(d1y, d1x);
        double headLen = 13;
        double a1 = angle + Math.toRadians(150);
        double a2 = angle - Math.toRadians(150);
        Path2D.Double head = new Path2D.Double();
        head.moveTo(ex, ey);
        head.lineTo(ex + Math.cos(a1) * headLen, ey + Math.sin(a1) * headLen);
        head.lineTo(ex + Math.cos(a2) * headLen, ey + Math.sin(a2) * headLen);
        head.closePath();
        g2.fill(head);

        // Remember the curve so the amount label can be placed later
        labels.add(new Object[]{new double[]{sx, sy, cx, cy, ex, ey},
                String.format(Locale.US, "%,d", amount)});
    }

    // ---------- Legend ----------

    private Rectangle legendBox() {
        FontMetrics fm = getFontMetrics(new Font(SANS, Font.PLAIN, 11));
        int maxW = 0;
        for (String n : LEGEND_NAMES) maxW = Math.max(maxW, fm.stringWidth(n));
        int w = 12 * 2 + 20 + maxW;
        int h = 10 * 2 + 19 * LEGEND_NAMES.length - 5;
        return new Rectangle(12, getHeight() - h - 12, w, h);
    }

    private void drawLegend(Graphics2D g2) {
        Rectangle b = legendBox();
        g2.setFont(new Font(SANS, Font.PLAIN, 11));

        g2.setColor(new Color(255, 255, 255, 238));
        g2.fillRoundRect(b.x, b.y, b.width, b.height, 5, 5);
        g2.setStroke(new BasicStroke(1f));
        g2.setColor(PILL_LINE);
        g2.drawRoundRect(b.x, b.y, b.width, b.height, 5, 5);

        for (int i = 0; i < LEGEND_NAMES.length; i++) {
            int cy = b.y + 10 + i * 19 + 7;
            g2.setColor(LEGEND_COLORS[i]);
            if (i < 3) {
                g2.fillOval(b.x + 12, cy - 6, 12, 12);
            } else {
                g2.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.drawLine(b.x + 12, cy, b.x + 24, cy);
            }
            g2.setColor(TEXT);
            g2.drawString(LEGEND_NAMES[i], b.x + 12 + 20, cy + 4);
        }
    }

    // ---------- Zoom buttons (top-right of the map) ----------

    private Rectangle plusBox()  { return new Rectangle(getWidth() - 46, 10, 32, 32); }
    private Rectangle minusBox() { return new Rectangle(getWidth() - 46, 46, 32, 32); }
    private Rectangle resetBox() { return new Rectangle(getWidth() - 46, 82, 32, 32); }

    private void drawZoomControls(Graphics2D g2) {
        String[] labels = {"+", "\u2212", "Fit"};
        Rectangle[] boxes = {plusBox(), minusBox(), resetBox()};
        g2.setFont(new Font(SANS, Font.BOLD, 18));
        FontMetrics fm = g2.getFontMetrics();
        for (int i = 0; i < 3; i++) {
            Rectangle b = boxes[i];
            g2.setColor(new Color(255, 255, 255, 240));
            g2.fillRoundRect(b.x, b.y, b.width, b.height, 5, 5);
            g2.setColor(PILL_LINE);
            g2.setStroke(new BasicStroke(1f));
            g2.drawRoundRect(b.x, b.y, b.width, b.height, 5, 5);
            g2.setColor(TEXT);
            g2.drawString(labels[i], b.x + (b.width - fm.stringWidth(labels[i])) / 2,
                    b.y + (b.height - fm.getHeight()) / 2 + fm.getAscent());
        }
        g2.setFont(new Font(SANS, Font.PLAIN, 11));
        g2.setColor(MUTED);
        String hint = "Scroll to zoom \u2022 drag to move";
        g2.drawString(hint, getWidth() - g2.getFontMetrics().stringWidth(hint) - 14, getHeight() - 12);
    }
}