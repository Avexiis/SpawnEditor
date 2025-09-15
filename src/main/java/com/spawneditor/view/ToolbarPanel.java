package com.spawneditor.view;

import com.spawneditor.model.ActionType;
import com.spawneditor.model.Tile;
import com.spawneditor.util.NumberField;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.EnumMap;
import java.util.Map;
import java.util.List;
import java.util.function.Consumer;

public class ToolbarPanel extends JToolBar {
    private final JButton btnRefresh = new JButton("Refresh");
    private final JButton btnSaveAs = new JButton("Save As");
    private final JButton btnPaths = new JButton("Paths");
    private final JCheckBox chkGrid = new JCheckBox("Grid", true);
    private final JSlider zoom = new JSlider(20, 1600, 100);
    private final JLabel lbZoom = new JLabel("100%");
    private final Map<ActionType, JCheckBox> typeChecks = new EnumMap<>(ActionType.class);
    private final JLabel lbJump = new JLabel(" Jump x,y,z: ");
    private final NumberField tfX = new NumberField(6);
    private final NumberField tfY = new NumberField(6);
    private final NumberField tfZ = new NumberField(2);
    private final JButton btnJump = new JButton("Jump");
    public final Event<Boolean> onToggleGrid = new Event<>();
    public final Event<Double> onZoomChanged = new Event<>();
    public final Event<Map<ActionType, Boolean>> onToggleTypes = new Event<>();
    public final Event<Tile> onJumpToTile = new Event<>();
    private Consumer<ActionEvent> onRefresh;
    private Consumer<ActionEvent> onSaveAs;
    private Consumer<ActionEvent> onPaths;

    public ToolbarPanel() {
        super("Toolbar", JToolBar.HORIZONTAL);
        setFloatable(false);
        add(btnRefresh);
        add(btnSaveAs);
        add(btnPaths);
        addSeparator();
        add(chkGrid);
        add(new JLabel(" Zoom: "));
        add(zoom);
        add(lbZoom);
        addSeparator();
        add(new JLabel(" Show: "));
        for (ActionType t : ActionType.values()) {
            String label = t == ActionType.DYNAMIC_NPC ? "dynamic_npc" : t.name().toLowerCase();
            JCheckBox cb = new JCheckBox(label, true);
            typeChecks.put(t, cb);
            add(cb);
            cb.addActionListener(e -> emitTypeVisibility());
        }
        addSeparator();
        tfZ.setInt(0);
        JPanel jumpPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        jumpPanel.setOpaque(false);
        jumpPanel.add(lbJump);
        jumpPanel.add(new JLabel("x"));
        jumpPanel.add(tfX);
        jumpPanel.add(new JLabel(" y"));
        jumpPanel.add(tfY);
        jumpPanel.add(new JLabel(" z"));
        jumpPanel.add(tfZ);
        jumpPanel.add(btnJump);
        add(jumpPanel);
        zoom.addChangeListener(e -> {
            double z = zoom.getValue() / 100.0;
            lbZoom.setText((int)(z * 100) + "%");
            onZoomChanged.emit(z);
        });
        chkGrid.addActionListener(e -> onToggleGrid.emit(chkGrid.isSelected()));
        btnJump.addActionListener(e -> {
            Integer x = tfX.getInt(null);
            Integer y = tfY.getInt(null);
            Integer z = tfZ.getInt(0);
            if (x == null || y == null) {
                JOptionPane.showMessageDialog(this, "Enter valid integers for x and y.", "Jump", JOptionPane.WARNING_MESSAGE);
                return;
            }
            onJumpToTile.emit(new Tile(x, y, z != null ? z : 0));
        });
        btnRefresh.addActionListener(e -> {
            if (onRefresh != null) onRefresh.accept(e);
        });
        btnSaveAs.addActionListener(e -> {
            if (onSaveAs != null) onSaveAs.accept(e);
        });
        btnPaths.addActionListener(e -> {
            if (onPaths != null) onPaths.accept(e);
        });
    }

    public void setOnRefresh(Consumer<ActionEvent> c) {
        this.onRefresh = c;
    }

    public void setOnSaveAs(Consumer<ActionEvent> c) {
        this.onSaveAs = c;
    }

    public void setOnPaths(Consumer<ActionEvent> c) {
        this.onPaths = c;
    }

    private void emitTypeVisibility() {
        Map<ActionType, Boolean> m = new EnumMap<>(ActionType.class);
        for (Map.Entry<ActionType, JCheckBox> e : typeChecks.entrySet()) {
            m.put(e.getKey(), e.getValue().isSelected());
        }
        onToggleTypes.emit(m);
    }

    public static class Event<T> {
        private List<Consumer<T>> listeners = new java.util.ArrayList<>();
        public void addListener(Consumer<T> c) { listeners.add(c); }
        public void emit(T value) { for (Consumer<T> c : listeners) c.accept(value); }
    }
}
