package com.spawneditor.view;

import com.spawneditor.io.Paths;
import com.spawneditor.model.ActionType;
import com.spawneditor.model.SpawnEntry;
import com.spawneditor.model.SpawnProject;
import com.spawneditor.model.Tile;
import com.spawneditor.model.DynamicNpcEntry;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class MapPanel extends JComponent {
    private static final int CANVAS_PAD = 400;
    private final BufferedImage map;
    private final SpawnProject project;
    private double zoom = 1.0;
    private boolean showGrid = true;
    private final Map<ActionType, Boolean> typeVisible = new EnumMap<>(ActionType.class);
    private Consumer<Tile> onTileClicked;
    private int cols;
    private int rows;
    private double regionW;
    private double regionH;
    private Point dragStartView;
    private Point dragStartMouse;
    private Tile hoverTile;
    private Tile selectedTile;
    private Supplier<List<DynamicNpcEntry>> dynamicSupplier;
    private int currentPlane = 0;
    private final boolean[] ghostPlane = {false, true, true, true};
    private final Rectangle overlayBounds = new Rectangle();
    private final Rectangle[] btnPlane = { new Rectangle(), new Rectangle(), new Rectangle(), new Rectangle() };
    private final Rectangle[] chkGhost = { new Rectangle(), new Rectangle(), new Rectangle(), new Rectangle() };
    private static final int UI_PAD = 5; //margin from viewport edges
    private static final int UI_H = 44; //overlay height (short)
    private static final int SEG_W_MIN = 72; //min width of the Z button
    private static final int SEG_H = 24; // button height
    private static final int SLOT_GAP = 8; // inner gap between button and checkbox label
    private static final int SLOT_PAD_X = 10;
    private int heldDX = 0;
    private int heldDY = 0;
    private final RepeatMover repeatMover = new RepeatMover();

    public MapPanel(BufferedImage map, SpawnProject project) {
        this.map = map;
        this.project = project;
        setOpaque(true);
        setBackground(Color.black);
        setDoubleBuffered(true);
        setFocusable(true);
        for (ActionType t : ActionType.values()) typeVisible.put(t, true);
        if (map == null) {
            cols = 1;
            rows = 1;
            regionW = regionH = 512;
            setPreferredSize(new Dimension(1024 + 2 * CANVAS_PAD, 1024 + 2 * CANVAS_PAD));
        } else {
            cols = Paths.RX_MAX - Paths.RX_MIN + 1;
            rows = Paths.RY_MAX - Paths.RY_MIN + 1;
            regionW = map.getWidth() / (double) cols;
            regionH = map.getHeight() / (double) rows;
            setPreferredSize(new Dimension((int) (map.getWidth() * zoom) + 2 * CANVAS_PAD, (int) (map.getHeight() * zoom) + 2 * CANVAS_PAD));
        }
        addComponentListener(new ComponentAdapter() {
            @Override
            public void componentShown(ComponentEvent e)  {
                updateOverlayLayout();
            }
            @Override
            public void componentResized(ComponentEvent e){
                updateOverlayLayout();
            }
        });
        addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (handleOverlayClick(e.getPoint())) return;
                Tile t = toTile(e.getX(), e.getY());
                selectedTile = t;
                repaint();
                if (onTileClicked != null) onTileClicked.accept(t);
            }
            @Override
            public void mousePressed(MouseEvent e) {
                requestFocusInWindow();
                if (overlayBounds.contains(e.getPoint())) return;
                JViewport vp = getViewport();
                if (vp != null) {
                    dragStartView = vp.getViewPosition();
                    dragStartMouse = SwingUtilities.convertPoint(MapPanel.this, e.getPoint(), vp);
                    setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
                }
            }
            @Override
            public void mouseReleased(MouseEvent e) {
                setCursor(Cursor.getDefaultCursor());
                dragStartView = null;
                dragStartMouse = null;
            }
        });
        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseDragged(MouseEvent e) {
                if (overlayBounds.contains(e.getPoint())) return;
                JViewport vp = getViewport();
                if (vp == null || dragStartView == null || dragStartMouse == null) return;
                Point now = SwingUtilities.convertPoint(MapPanel.this, e.getPoint(), vp);
                Point target = new Point(dragStartView.x - (now.x - dragStartMouse.x), dragStartView.y - (now.y - dragStartMouse.y));
                clampAndSetViewPosition(vp, target);
            }
            @Override
            public void mouseMoved(MouseEvent e) {
                if (overlayBounds.contains(e.getPoint())) {
                    setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                } else {
                    setCursor(Cursor.getDefaultCursor());
                    hoverTile = toTile(e.getX(), e.getY());
                    repaint();
                }
            }
        });
        addMouseWheelListener(e -> {
            if (overlayBounds.contains(e.getPoint())) return;
            double factor = (e.getPreciseWheelRotation() < 0) ? 1.15 : 1.0 / 1.15;
            Point p = e.getPoint();
            setZoomAtPoint(zoom * factor, p.x, p.y);
            e.consume();
        });
        installArrowKeyBindings();
    }
    @Override
    public void addNotify() {
        super.addNotify();
        JViewport vp = getViewport();
        if (vp != null) {
            vp.setScrollMode(JViewport.SIMPLE_SCROLL_MODE);
        }
        updateOverlayLayout();
        SwingUtilities.invokeLater(this::disableScrollPaneArrowKeys);
        SwingUtilities.invokeLater(this::requestFocusInWindow);
    }

    private void disableScrollPaneArrowKeys() {
        JScrollPane sp = (JScrollPane) SwingUtilities.getAncestorOfClass(JScrollPane.class, this);
        if (sp == null) return;
        InputMap imAncestor = sp.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
        if (imAncestor != null) {
            for (String k : new String[]{"LEFT","RIGHT","UP","DOWN","KP_LEFT","KP_RIGHT","KP_UP","KP_DOWN"}) {
                imAncestor.put(KeyStroke.getKeyStroke(k), "none");
                imAncestor.put(KeyStroke.getKeyStroke("shift " + k), "none");
                imAncestor.put(KeyStroke.getKeyStroke("ctrl " + k), "none");
            }
        }
        JViewport vp = sp.getViewport();
        if (vp != null) {
            InputMap imV = vp.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT);
            if (imV != null) {
                for (String k : new String[]{"LEFT","RIGHT","UP","DOWN","KP_LEFT","KP_RIGHT","KP_UP","KP_DOWN"}) {
                    imV.put(KeyStroke.getKeyStroke(k), "none");
                    imV.put(KeyStroke.getKeyStroke("shift " + k), "none");
                    imV.put(KeyStroke.getKeyStroke("ctrl " + k), "none");
                }
            }
        }
    }

    private Font overlayFont() {
        return getFont().deriveFont(Font.PLAIN, 12f);
    }

    private void updateOverlayLayout() {
        JViewport vp = getViewport();
        if (vp == null) return;
        Rectangle vr = vp.getViewRect();
        int obW = Math.max(320, vr.width - UI_PAD * 2);
        int obH = UI_H;
        int ox = vr.x + UI_PAD;
        int oy = vr.y + vr.height - obH - UI_PAD;
        overlayBounds.setBounds(ox, oy, obW, obH);
        int slotW = overlayBounds.width / 4;
        int slotPadY = (obH - SEG_H) / 2;
        FontMetrics fm = getFontMetrics(overlayFont());
        final int box = 14;
        final int slotRightPad = SLOT_PAD_X;
        for (int i = 0; i < 4; i++) {
            int sx = ox + i * slotW;
            String btnText = "Select Plane: " + i;
            String chkText = "Show Plane: " + i;
            int btnTextW = fm.stringWidth(btnText);
            int chkTextW = fm.stringWidth(chkText);
            int minChkHitW = box + 6 + chkTextW + 6;
            int maxBtnW = Math.max(0, slotW - SLOT_PAD_X - SLOT_GAP - minChkHitW - slotRightPad);
            int desiredBtnW = btnTextW + 16;
            int bw = Math.max(SEG_W_MIN, Math.min(desiredBtnW, maxBtnW));
            int bx = sx + SLOT_PAD_X;
            int by = oy + slotPadY;
            btnPlane[i].setBounds(bx, by, bw, SEG_H);
            int chkX = bx + bw + SLOT_GAP;
            int chkW = Math.max(minChkHitW, sx + slotW - chkX - slotRightPad);
            chkGhost[i].setBounds(chkX, oy, chkW, obH);
        }
        repaint();
    }

    private boolean handleOverlayClick(Point p) {
        if (!overlayBounds.contains(p)) return false;
        for (int i = 0; i < 4; i++) {
            if (btnPlane[i].contains(p)) {
                setPlane(i);
                return true;
            }
        }
        for (int i = 0; i < 4; i++) {
            if (chkGhost[i].contains(p)) {
                if (i == currentPlane) {
                    ghostPlane[i] = false;
                } else {
                    ghostPlane[i] = !ghostPlane[i];
                }
                repaint();
                return true;
            }
        }
        return true;
    }

    public void setDynamicSupplier(Supplier<List<DynamicNpcEntry>> supplier) {
        this.dynamicSupplier = supplier;
        repaint();
    }

    public void setOnTileClicked(Consumer<Tile> c) {
        this.onTileClicked = c;
    }

    public void setZoom(double z) {
        setZoomCentered(z);
    }

    public void setShowGrid(boolean v) {
        this.showGrid = v;
        repaint();
    }

    public void setTypeVisibility(Map<ActionType, Boolean> map) {
        typeVisible.clear();
        typeVisible.putAll(map);
        repaint();
    }

    public void focusTile(Tile t, Double targetZoom) {
        if (t == null) return;
        if (targetZoom != null) setZoomCentered(targetZoom);
        Rectangle r = tileToRect(t);
        Rectangle viewRect = scaleRect(r, zoom);
        viewRect.translate(CANVAS_PAD, CANVAS_PAD);
        centerViewOn(viewRect);
        selectedTile = t;
        setPlane(Math.max(0, Math.min(3, t.z)));
        repaint();
    }

    public void setPlane(int z) {
        int nz = Math.max(0, Math.min(3, z));
        if (nz != currentPlane) {
            currentPlane = nz;
            ghostPlane[nz] = false;
        }
        repaint();
    }

    public int getPlane() {
        return currentPlane;
    }

    private Tile toTile(int px, int py) {
        double x = (px - CANVAS_PAD) / zoom;
        double y = (py - CANVAS_PAD) / zoom;
        int col = (int) Math.floor(x / regionW);
        int rowTop = (int) Math.floor(y / regionH);
        col = Math.max(0, Math.min(cols - 1, col));
        rowTop = Math.max(0, Math.min(rows - 1, rowTop));
        double ox = x - col * regionW;
        double oyTop = y - rowTop * regionH;
        int tx = (int) Math.floor(ox / (regionW / Paths.REGION_TILE_SIZE));
        int tyTop = (int) Math.floor(oyTop / (regionH / Paths.REGION_TILE_SIZE));
        int rowFromBottom = (rows - 1) - rowTop;
        int tyFromBottom = 63 - tyTop;
        int worldX = (Paths.RX_MIN + col) * Paths.REGION_TILE_SIZE + tx;
        int worldY = (Paths.RY_MIN + rowFromBottom) * Paths.REGION_TILE_SIZE + tyFromBottom;
        return new Tile(worldX, worldY, currentPlane);
    }

    private Rectangle tileToRect(Tile t) {
        int col = (t.x / Paths.REGION_TILE_SIZE) - Paths.RX_MIN;
        int rowFromBottom = (t.y / Paths.REGION_TILE_SIZE) - Paths.RY_MIN;
        int rowTop = (rows - 1) - rowFromBottom;
        int oxTiles = t.x % Paths.REGION_TILE_SIZE;
        int oyTilesFromBottom = t.y % Paths.REGION_TILE_SIZE;
        int oyTilesTop = 63 - oyTilesFromBottom;
        double px = (col * regionW) + oxTiles * (regionW / Paths.REGION_TILE_SIZE);
        double py = (rowTop * regionH) + oyTilesTop * (regionH / Paths.REGION_TILE_SIZE);
        double pw = (regionW / Paths.REGION_TILE_SIZE);
        double ph = (regionH / Paths.REGION_TILE_SIZE);
        return new Rectangle((int) Math.round(px), (int) Math.round(py), (int) Math.ceil(pw), (int) Math.ceil(ph));
    }

    @Override
    public Dimension getPreferredSize() {
        if (map != null) {
            return new Dimension((int) (map.getWidth() * zoom) + 2 * CANVAS_PAD, (int) (map.getHeight() * zoom) + 2 * CANVAS_PAD);
        }
        return new Dimension(1024 + 2 * CANVAS_PAD, 1024 + 2 * CANVAS_PAD);
    }

    private void setZoomAtPoint(double newZoom, int anchorX, int anchorY) {
        newZoom = Math.max(0.2, Math.min(16.0, newZoom));
        JViewport vp = getViewport();
        if (vp == null || map == null) {
            zoom = newZoom;
            revalidate();
            repaint();
            return;
        }
        Rectangle view = vp.getViewRect();
        int dx = anchorX - view.x;
        int dy = anchorY - view.y;
        double worldX = (anchorX - CANVAS_PAD) / zoom;
        double worldY = (anchorY - CANVAS_PAD) / zoom;
        zoom = newZoom;
        Dimension newPref = getPreferredSize();
        setPreferredSize(newPref);
        vp.setViewSize(newPref);
        int newAnchorPx = CANVAS_PAD + (int) Math.round(worldX * zoom);
        int newAnchorPy = CANVAS_PAD + (int) Math.round(worldY * zoom);
        Point newPos = new Point(newAnchorPx - dx, newAnchorPy - dy);
        clampAndSetViewPosition(vp, newPos);
        revalidate();
        repaint();
        updateOverlayLayout();
    }

    private void setZoomCentered(double newZoom) {
        newZoom = Math.max(0.2, Math.min(16.0, newZoom));
        JViewport vp = getViewport();
        if (vp == null || map == null) {
            zoom = newZoom;
            revalidate();
            repaint();
            return;
        }
        Rectangle view = vp.getViewRect();
        Point centerView = new Point(view.x + view.width / 2, view.y + view.height / 2);
        double anchorX = (centerView.x - CANVAS_PAD) / zoom;
        double anchorY = (centerView.y - CANVAS_PAD) / zoom;
        zoom = newZoom;
        Dimension newPref = getPreferredSize();
        setPreferredSize(newPref);
        vp.setViewSize(newPref);
        int newCenterX = CANVAS_PAD + (int) Math.round(anchorX * zoom);
        int newCenterY = CANVAS_PAD + (int) Math.round(anchorY * zoom);
        Point newPos = new Point(newCenterX - view.width / 2, newCenterY - view.height / 2);
        clampAndSetViewPosition(vp, newPos);
        revalidate();
        repaint();
        updateOverlayLayout();
    }

    private Rectangle scaleRect(Rectangle r, double factor) {
        return new Rectangle((int) Math.round(r.x * factor), (int) Math.round(r.y * factor),
                (int) Math.round(r.width * factor), (int) Math.round(r.height * factor)
        );
    }

    private void centerViewOn(Rectangle viewTargetDeviceSpace) {
        JViewport vp = getViewport();
        if (vp == null) return;
        Rectangle view = vp.getViewRect();
        Point p = new Point(viewTargetDeviceSpace.x + viewTargetDeviceSpace.width / 2 - view.width / 2,
                viewTargetDeviceSpace.y + viewTargetDeviceSpace.height / 2 - view.height / 2
        );
        clampAndSetViewPosition(vp, p);
        updateOverlayLayout();
    }

    private void clampAndSetViewPosition(JViewport vp, Point p) {
        Dimension size = getPreferredSize();
        Rectangle view = vp.getViewRect();
        int maxX = Math.max(0, size.width - view.width);
        int maxY = Math.max(0, size.height - view.height);
        p.x = Math.max(0, Math.min(p.x, maxX));
        p.y = Math.max(0, Math.min(p.y, maxY));
        vp.setViewPosition(p);
    }

    private JViewport getViewport() {
        return (JViewport) SwingUtilities.getAncestorOfClass(JViewport.class, this);
    }

    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(48, 48, 48));
        g.fillRect(0, 0, getWidth(), getHeight());
        AffineTransform old = g.getTransform();
        g.translate(CANVAS_PAD, CANVAS_PAD);
        g.scale(zoom, zoom);
        if (map != null) g.drawImage(map, 0, 0, null);
        if (showGrid) {
            g.setColor(new Color(0x19FFFFFF, true));
            for (int c = 0; c <= cols; c++) {
                int x = (int) Math.round(c * regionW);
                g.drawLine(x, 0, x, (int) Math.round(rows * regionH));
            }
            for (int r = 0; r <= rows; r++) {
                int y = (int) Math.round(r * regionH);
                g.drawLine(0, y, (int) Math.round(cols * regionW), y);
            }
        }
        drawOverlays(g);
        float screenPx = (float) (1.0 / Math.max(zoom, 0.0001));
        Stroke onePxStroke = new BasicStroke(screenPx);
        if (hoverTile != null) {
            Rectangle r = tileToRect(hoverTile);
            g.setColor(new Color(0x3CFFFFFF, true));
            g.fillRect(r.x, r.y, r.width, r.height);
            g.setColor(new Color(0xB4FFFFFF, true));
            Stroke oldStroke = g.getStroke();
            g.setStroke(onePxStroke);
            g.drawRect(r.x, r.y, r.width, r.height);
            g.setStroke(oldStroke);
        }
        if (selectedTile != null) {
            Rectangle r = tileToRect(selectedTile);
            g.setColor(new Color(0x5A03FC13, true));
            g.fillRect(r.x, r.y, r.width, r.height);
            g.setColor(new Color(0xDC03FC13, true));
            Stroke oldStroke = g.getStroke();
            g.setStroke(onePxStroke);
            g.drawRect(r.x, r.y, r.width, r.height);
            g.setStroke(oldStroke);
        }
        g.setTransform(old);
        paintOverlay(g);
        g.dispose();
    }

    private void drawOverlays(Graphics2D g) {
        final Color cObjV = new Color(0xC800FFFF, true); // cyan
        final Color cOverV = new Color(0xC800BFFF, true); // light blue
        final Color cDelV = new Color(0xC8FF0000, true); // red
        final Color cNpcV = new Color(0xC8FFFF00, true); // yellow
        final Color cDynV = new Color(0xDCFF00FF, true); // magenta
        final Color cObjG = halfAlpha(cObjV);
        final Color cOverG = halfAlpha(cOverV);
        final Color cDelG = halfAlpha(cDelV);
        final Color cNpcG = halfAlpha(cNpcV);
        final Color cDynG = halfAlpha(cDynV);
        for (SpawnEntry e : project.getEntries()) {
            if (e.tile == null) continue;
            if (!typeVisible.getOrDefault(e.action, true)) continue;
            int z = clampZ(e.tile.z);
            boolean onActive = (z == currentPlane);
            boolean ghost = !onActive && ghostPlane[z];
            if (!onActive && !ghost) continue;
            Rectangle r = tileToRect(e.tile);
            switch (e.action) {
                case SPAWN_OBJECT -> g.setColor(ghost ? cObjG  : cObjV);
                case SPAWN_OVER_OBJECT -> g.setColor(ghost ? cOverG : cOverV);
                case SPAWN_NPC -> g.setColor(ghost ? cNpcG : cNpcV);
                case DELETE_OBJECT -> g.setColor(ghost ? cDelG : cDelV);
                default -> {
                    continue;
                }
            }
            g.fillRect(r.x, r.y, r.width, r.height);
        }
        if (dynamicSupplier != null && typeVisible.getOrDefault(ActionType.DYNAMIC_NPC, true)) {
            List<DynamicNpcEntry> list = dynamicSupplier.get();
            if (list != null) {
                for (DynamicNpcEntry d : list) {
                    int z = clampZ(d.z);
                    boolean onActive = (z == currentPlane);
                    boolean ghost = !onActive && ghostPlane[z];
                    if (!onActive && !ghost) continue;
                    Rectangle r = tileToRect(new Tile(d.x, d.y, d.z));
                    g.setColor(ghost ? cDynG : cDynV);
                    g.fillRect(r.x, r.y, r.width, r.height);
                }
            }
        }
    }

    private static int clampZ(int z) {
        return (z < 0) ? 0 : (z > 3 ? 3 : z);
    }

    private static Color halfAlpha(Color c) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), 128);
    }

    private void paintOverlay(Graphics2D g) {
        updateOverlayLayout();
        g.setColor(new Color(30, 30, 30, 220));
        g.fillRoundRect(overlayBounds.x, overlayBounds.y, overlayBounds.width, overlayBounds.height, 10, 10);
        g.setColor(new Color(70, 70, 70, 220));
        g.drawRoundRect(overlayBounds.x, overlayBounds.y, overlayBounds.width, overlayBounds.height, 10, 10);
        g.setColor(new Color(80, 80, 80, 180));
        int slotW = overlayBounds.width / 4;
        for (int i = 1; i < 4; i++) {
            int x = overlayBounds.x + i * slotW;
            g.drawLine(x, overlayBounds.y + 6, x, overlayBounds.y + overlayBounds.height - 6);
        }
        g.setFont(overlayFont());
        for (int i = 0; i < 4; i++) {
            Rectangle rBtn = btnPlane[i];
            boolean sel = (i == currentPlane);
            g.setColor(sel ? new Color(80, 140, 255) : new Color(55, 55, 55));
            g.fillRoundRect(rBtn.x, rBtn.y, rBtn.width, rBtn.height, 8, 8);
            g.setColor(sel ? new Color(20, 40, 100) : new Color(90, 90, 90));
            g.drawRoundRect(rBtn.x, rBtn.y, rBtn.width, rBtn.height, 8, 8);
            g.setColor(Color.WHITE);
            drawCentered(g, "Select Plane: " + i, rBtn);
            Rectangle rHit = chkGhost[i];
            int box = 14;
            int by = rBtn.y + (rBtn.height - box) / 2;
            int bx = rHit.x;
            boolean enabled = (i != currentPlane);
            boolean checked = ghostPlane[i];
            g.setColor(enabled ? new Color(210, 210, 210) : new Color(140, 140, 140));
            g.drawRect(bx, by, box, box);
            if (checked && enabled) {
                g.drawLine(bx + 3, by + 7, bx + 6, by + 10);
                g.drawLine(bx + 6, by + 10, bx + 11, by + 4);
            }
            g.setColor(new Color(220, 220, 220));
            g.drawString("Show Plane: " + i, bx + box + 6, by + box - 2);
        }
    }

    private static void drawCentered(Graphics2D g, String text, Rectangle r) {
        FontMetrics fm = g.getFontMetrics();
        int tx = r.x + (r.width - fm.stringWidth(text)) / 2;
        int ty = r.y + (r.height + fm.getAscent() - fm.getDescent()) / 2;
        g.drawString(text, tx, ty);
    }

    private void installArrowKeyBindings() {
        int cond = JComponent.WHEN_IN_FOCUSED_WINDOW;
        InputMap im = getInputMap(cond);
        ActionMap am = getActionMap();
        Map<String, int[]> dirs = new HashMap<>();
        dirs.put("LEFT", new int[]{-1, 0});
        dirs.put("RIGHT", new int[]{ 1, 0});
        dirs.put("UP", new int[]{ 0, 1});
        dirs.put("DOWN", new int[]{ 0,-1});
        dirs.put("KP_LEFT", new int[]{-1, 0});
        dirs.put("KP_RIGHT", new int[]{ 1, 0});
        dirs.put("KP_UP", new int[]{ 0, 1});
        dirs.put("KP_DOWN", new int[]{ 0,-1});
        for (Map.Entry<String,int[]> e : dirs.entrySet()) {
            String name = e.getKey();
            int[] v = e.getValue();
            String pressKey = "press_" + name;
            String releaseKey = "release_" + name;
            im.put(KeyStroke.getKeyStroke(name), pressKey);
            im.put(KeyStroke.getKeyStroke("released " + name), releaseKey);
            am.put(pressKey, new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent ev) {
                    onArrowPressed(v[0], v[1]);
                }
            });
            am.put(releaseKey, new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent ev) {
                    onArrowReleased(v[0], v[1]);
                }
            });
        }
    }

    private void onArrowPressed(int dx, int dy) {
        heldDX = clampStep(heldDX + dx);
        heldDY = clampStep(heldDY + dy);
        if (heldDX == 0 && heldDY == 0) {
            repeatMover.stop();
        } else {
            ensureSelectionExists();
            repeatMover.start();
        }
    }

    private void onArrowReleased(int dx, int dy) {
        heldDX = clampStep(heldDX - dx);
        heldDY = clampStep(heldDY - dy);
        if (heldDX == 0 && heldDY == 0) {
            repeatMover.stop();
        } else {
            repeatMover.bumpVectorChange();
        }
    }

    private static int clampStep(int v) {
        return v < 0 ? -1 : (v > 0 ? 1 : 0);
    }

    private void ensureSelectionExists() {
        if (selectedTile != null) return;
        if (hoverTile != null) {
            selectedTile = new Tile(hoverTile.x, hoverTile.y, currentPlane);
        } else {
            JViewport vp = getViewport();
            if (vp != null) {
                Rectangle vr = vp.getViewRect();
                selectedTile = toTile(vr.x + vr.width / 2, vr.y + vr.height / 2);
            } else {
                selectedTile = new Tile(Paths.RX_MIN * Paths.REGION_TILE_SIZE, Paths.RY_MIN * Paths.REGION_TILE_SIZE, currentPlane);
            }
        }
        selectedTile.z = currentPlane;
        repaint();
        if (onTileClicked != null) onTileClicked.accept(selectedTile);
    }

    private void moveSelectionBy(int dxTiles, int dyTiles) {
        if (selectedTile == null) return;
        int nx = selectedTile.x + dxTiles;
        int ny = selectedTile.y + dyTiles;
        int minX = Paths.RX_MIN * Paths.REGION_TILE_SIZE;
        int maxX = (Paths.RX_MAX + 1) * Paths.REGION_TILE_SIZE - 1;
        int minY = Paths.RY_MIN * Paths.REGION_TILE_SIZE;
        int maxY = (Paths.RY_MAX + 1) * Paths.REGION_TILE_SIZE - 1;
        if (nx < minX) nx = minX;
        if (nx > maxX) nx = maxX;
        if (ny < minY) ny = minY;
        if (ny > maxY) ny = maxY;
        if (nx == selectedTile.x && ny == selectedTile.y) return;
        selectedTile = new Tile(nx, ny, currentPlane);
        ensureSelectionVisible();
        repaint();
        if (onTileClicked != null) onTileClicked.accept(selectedTile);
    }

    private void ensureSelectionVisible() {
        JViewport vp = getViewport();
        if (vp == null || selectedTile == null) return;
        Rectangle tileDevice = scaleRect(tileToRect(selectedTile), zoom);
        tileDevice.translate(CANVAS_PAD, CANVAS_PAD);
        Rectangle view = vp.getViewRect();
        int newX = view.x;
        int newY = view.y;
        if (tileDevice.x < view.x) newX = tileDevice.x;
        else if (tileDevice.x + tileDevice.width > view.x + view.width) newX = tileDevice.x + tileDevice.width - view.width;
        if (tileDevice.y < view.y) newY = tileDevice.y;
        else if (tileDevice.y + tileDevice.height > view.y + view.height) newY = tileDevice.y + tileDevice.height - view.height;
        if (newX != view.x || newY != view.y) {
            clampAndSetViewPosition(vp, new Point(newX, newY));
            updateOverlayLayout();
        }
    }

    private final class RepeatMover implements ActionListener {
        private static final int INITIAL_DELAY_MS = 220;
        private static final int BASE_INTERVAL_MS = 90;
        private static final int FAST_INTERVAL_MS = 35;
        private static final int ACCEL_AFTER_STEPS = 8;
        private final Timer timer = new Timer(BASE_INTERVAL_MS, this);
        private int stepCount = 0;
        private boolean running = false;
        private boolean toggleAxis = false;

        RepeatMover() {
            timer.setRepeats(true);
        }

        void start() {
            if (!running) {
                running = true;
                stepCount = 0;
                toggleAxis = false;
                immediateStep();
                timer.setInitialDelay(INITIAL_DELAY_MS);
                timer.setDelay(BASE_INTERVAL_MS);
                timer.restart();
            } else {
                if (!timer.isRunning()) {
                    timer.setInitialDelay(0);
                    timer.setDelay(currentInterval());
                    timer.start();
                }
            }
        }

        void stop() {
            timer.stop();
            running = false;
        }

        void bumpVectorChange() {
            if (running && timer.isRunning()) {
                timer.setDelay(currentInterval());
            }
        }

        @Override
        public void actionPerformed(ActionEvent e) {
            if (heldDX == 0 && heldDY == 0) {
                stop();
                return;
            }
            stepOnce();
            stepCount++;
            timer.setDelay(currentInterval());
        }

        private void immediateStep() {
            if (heldDX == 0 && heldDY == 0) return;
            stepOnce();
            stepCount++;
        }

        private int currentInterval() {
            return (stepCount >= ACCEL_AFTER_STEPS) ? FAST_INTERVAL_MS : BASE_INTERVAL_MS;
        }

        private void stepOnce() {
            int dx = heldDX;
            int dy = heldDY;
            if (dx != 0 && dy != 0) {
                if (toggleAxis) {
                    dx = 0;
                } else {
                    dy = 0;
                }
                toggleAxis = !toggleAxis;
            }
            moveSelectionBy(dx, dy);
        }
    }
}
