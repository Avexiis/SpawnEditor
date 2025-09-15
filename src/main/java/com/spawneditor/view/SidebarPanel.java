package com.spawneditor.view;

import com.spawneditor.model.*;
import com.spawneditor.util.NumberField;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class SidebarPanel extends JPanel {
    private final JComboBox<ActionType> cbAction = new JComboBox<>(ActionType.values());
    private final JLabel lbTile = new JLabel("x=?, y=?, z=?");
    private final NumberField tfX = new NumberField(6);
    private final NumberField tfY = new NumberField(6);
    private final NumberField tfZ = new NumberField(2);
    private final NumberField tfId = new NumberField(8);
    private final NumberField tfType = new NumberField(3);
    private final NumberField tfRot  = new NumberField(2);
    private final NumberField tfWalk = new NumberField(3);
    private final JCheckBox chkAgg = new JCheckBox("Aggressive");
    private final JComboBox<Direction> cbDir = new JComboBox<>(Direction.values());
    private final JLabel lbDynId = new JLabel("");
    private final JLabel lbDynFlags = new JLabel("");
    private final JButton btnSave = new JButton("Save / Update");
    private final JButton btnDelete = new JButton("Delete");
    private Tile currentTile;
    private SpawnEntry currentEntry;
    private DynamicNpcEntry currentDynEntry;
    private boolean suppressEvents = false;
    private boolean dirty = false;
    private Consumer<SpawnEntry> onSaveJson;
    private TriConsumer<DynamicNpcEntry, Integer, Tile> onEditDyn;
    private BiConsumer<Integer, Tile> onCreateDyn;
    private Consumer<SpawnEntry> onDeleteJson;
    private Consumer<DynamicNpcEntry> onDeleteDyn;
    private Consumer<Tile> onDeleteAtTile;

    public SidebarPanel(com.spawneditor.io.ObjectIndex objIdx, com.spawneditor.io.NpcIndex npcIdx) {
        setLayout(new BorderLayout());
        JPanel form = new JPanel();
        form.setBorder(new EmptyBorder(10, 10, 10, 10));
        form.setLayout(new BoxLayout(form, BoxLayout.Y_AXIS));
        JPanel tileRow = row();
        tileRow.add(new JLabel("Tile: "));
        tileRow.add(lbTile);
        form.add(tileRow);
        JPanel xyz = row();
        xyz.add(new JLabel("x"));
        xyz.add(tfX);
        xyz.add(Box.createHorizontalStrut(8));
        xyz.add(new JLabel("y"));
        xyz.add(tfY);
        xyz.add(Box.createHorizontalStrut(8));
        xyz.add(new JLabel("z"));
        xyz.add(tfZ);
        form.add(xyz);
        JPanel dynInfo = new JPanel(new GridLayout(0, 1));
        dynInfo.setBorder(new EmptyBorder(6, 4, 6, 4));
        dynInfo.add(lbDynId);
        dynInfo.add(lbDynFlags);
        form.add(dynInfo);
        JPanel actionRow = row();
        actionRow.add(new JLabel("Action"));
        actionRow.add(cbAction);
        form.add(actionRow);
        JPanel idRow = row();
        idRow.add(new JLabel("ID"));
        idRow.add(tfId);
        form.add(idRow);
        JPanel objRow = row();
        objRow.add(new JLabel("Type"));
        tfType.setInt(10);
        objRow.add(tfType);
        objRow.add(Box.createHorizontalStrut(8));
        objRow.add(new JLabel("Rot (-3..3)"));
        tfRot.setInt(0);
        objRow.add(tfRot);
        form.add(objRow);
        JPanel npcRow1 = row();
        npcRow1.add(new JLabel("Walk"));
        tfWalk.setInt(0);
        npcRow1.add(tfWalk);
        npcRow1.add(Box.createHorizontalStrut(8));
        npcRow1.add(chkAgg);
        form.add(npcRow1);
        JPanel npcRow2 = row();
        npcRow2.add(new JLabel("Direction"));
        npcRow2.add(cbDir);
        form.add(npcRow2);
        JPanel buttons = row();
        buttons.add(btnSave);
        buttons.add(Box.createHorizontalStrut(8));
        buttons.add(btnDelete);
        form.add(Box.createVerticalStrut(10));
        form.add(buttons);
        add(form, BorderLayout.NORTH);
        add(buildLegend(), BorderLayout.SOUTH);
        setSaveEnabled(false);
        setDeleteEnabled(false);
        addDirtyListeners();
        btnSave.addActionListener(e -> requestSave());
        btnDelete.addActionListener(e -> {
            if (currentDynEntry != null && onDeleteDyn != null) {
                onDeleteDyn.accept(currentDynEntry);
            } else if (currentEntry != null && onDeleteJson != null) {
                onDeleteJson.accept(currentEntry);
            } else if (onDeleteAtTile != null && currentTile != null) {
                onDeleteAtTile.accept(currentTile);
            }
        });
        cbAction.addActionListener(e -> {
            if (!suppressEvents && currentDynEntry == null) {
                dirty = true;
                updateFieldVisibility();
            }
        });
        updateFieldVisibility();
    }

    private JPanel row() {
        return new JPanel(new FlowLayout(FlowLayout.LEFT));
    }

    private void addDirtyListeners() {
        DocumentListener d = new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                if (!suppressEvents) dirty = true;
            }
            @Override
            public void removeUpdate(DocumentEvent e) {
                if (!suppressEvents) dirty = true;
            }
            @Override
            public void changedUpdate(DocumentEvent e) {
                if (!suppressEvents) dirty = true;
            }
        };
        tfX.getDocument().addDocumentListener(d);
        tfY.getDocument().addDocumentListener(d);
        tfZ.getDocument().addDocumentListener(d);
        tfId.getDocument().addDocumentListener(d);
        tfType.getDocument().addDocumentListener(d);
        tfRot.getDocument().addDocumentListener(d);
        tfWalk.getDocument().addDocumentListener(d);
        chkAgg.addActionListener(e -> { if (!suppressEvents) dirty = true; });
        cbDir.addActionListener(e -> { if (!suppressEvents) dirty = true; });
    }

    private void updateFieldVisibility() {
        ActionType a = (ActionType) cbAction.getSelectedItem();
        boolean isDynamicCreate = (currentDynEntry == null && a == ActionType.DYNAMIC_NPC);
        boolean isDynamicEdit = (currentDynEntry != null);
        boolean needsId = isDynamicCreate || isDynamicEdit || a != ActionType.DELETE_OBJECT;
        tfId.setEnabled(needsId);
        boolean isObj = !isDynamicCreate && !isDynamicEdit && (a == ActionType.SPAWN_OBJECT || a == ActionType.SPAWN_OVER_OBJECT);
        tfType.setEnabled(isObj);
        tfRot.setEnabled(isObj);
        boolean isNpc = !isDynamicCreate && !isDynamicEdit && (a == ActionType.SPAWN_NPC);
        tfWalk.setEnabled(isNpc);
        chkAgg.setEnabled(isNpc);
        cbDir.setEnabled(isNpc);
        tfX.setEnabled(true); tfY.setEnabled(true); tfZ.setEnabled(true);
    }

    private JComponent buildLegend() {
        JPanel p = new JPanel(new GridLayout(0, 1));
        p.setBorder(new EmptyBorder(8, 12, 8, 12));
        p.add(legendRow(new Color(0xC800FFFF), "Object Spawn")); //cyan
        p.add(legendRow(new Color(0xC800BFFF), "Object Spawn-Over")); //light blue
        p.add(legendRow(new Color(0xC8FF0000), "Object Delete")); //red
        p.add(legendRow(new Color(0xC8FFFF00), "NPC Spawn")); //yellow
        p.add(legendRow(new Color(0xDCFF00FF), "Dynamic NPC Spawn")); //magenta
        return p;
    }

    private JComponent legendRow(Color c, String label) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 2));
        JLabel swatch = new JLabel("  ");
        swatch.setOpaque(true);
        swatch.setBackground(c);
        swatch.setPreferredSize(new Dimension(18, 14));
        row.add(swatch);
        row.add(new JLabel(label));
        return row;
    }

    public void setOnSaveJson(Consumer<SpawnEntry> c) {
        this.onSaveJson = c;
    }

    public void setOnEditDynamic(TriConsumer<DynamicNpcEntry, Integer, Tile> c) {
        this.onEditDyn = c;
    }

    public void setOnCreateDynamic(BiConsumer<Integer, Tile> c) {
        this.onCreateDyn = c;
    }

    public void setOnDeleteJson(Consumer<SpawnEntry> c) {
        this.onDeleteJson = c;
    }

    public void setOnDeleteDynamic(Consumer<DynamicNpcEntry> c) {
        this.onDeleteDyn = c;
    }

    public void setOnDeleteAtTile(Consumer<Tile> c) {
        this.onDeleteAtTile = c;
    }

    public void setTile(Tile t) {
        this.currentTile = t;
        suppressEvents = true;
        try {
            if (t != null) {
                lbTile.setText("x=" + t.x + ", y=" + t.y + ", z=" + t.z);
                tfX.setInt(t.x);
                tfY.setInt(t.y);
                tfZ.setInt(t.z);
            }
        } finally {
            suppressEvents = false;
        }
        setSaveEnabled(t != null);
    }

    public void setEntry(SpawnEntry e) {
        suppressEvents = true;
        try {
            currentDynEntry = null;
            this.currentEntry = e;
            cbAction.setEnabled(true);
            if (e == null) { clearFields(); return; }
            cbAction.setSelectedItem(e.action);
            tfId.setInt(e.id);
            tfType.setInt(e.type != null ? e.type : 10);
            tfRot.setInt(e.rotation != null ? e.rotation : 0);
            tfWalk.setInt(e.walkRadius != null ? e.walkRadius : 0);
            chkAgg.setSelected(e.aggressive != null && e.aggressive);
            cbDir.setSelectedItem(e.direction != null ? e.direction : Direction.NORTH);
            if (e.tile != null) setTile(e.tile);
            updateFieldVisibility();
            lbDynId.setText("");
            lbDynFlags.setText("");
        } finally {
            suppressEvents = false;
        }
        setDeleteEnabled(e != null);
        markClean();
    }

    public void setDynamicEntry(DynamicNpcEntry d) {
        suppressEvents = true;
        try {
            this.currentDynEntry = d;
            this.currentEntry = null;
            cbAction.setSelectedItem(ActionType.DYNAMIC_NPC);
            cbAction.setEnabled(false);
            tfId.setEnabled(true);
            tfType.setEnabled(false);
            tfRot.setEnabled(false);
            tfWalk.setEnabled(false);
            chkAgg.setEnabled(false);
            cbDir.setEnabled(false);
            setTile(new Tile(d.x, d.y, d.z));
            tfId.setInt(d.id);
            lbDynId.setText("Dynamic NPC ID: " + d.id);
            lbDynFlags.setText("(line " + d.lineIndex + ")");
        } finally {
            suppressEvents = false;
        }
        setDeleteEnabled(d != null);
        markClean();
    }

    public void clearFields() {
        suppressEvents = true;
        try {
            currentDynEntry = null;
            currentEntry = null;
            cbAction.setEnabled(true);
            if (currentTile == null) setTile(new Tile(0, 0, 0));
            cbAction.setSelectedItem(ActionType.SPAWN_OBJECT);
            tfId.setInt(null);
            tfType.setInt(10);
            tfRot.setInt(0);
            tfWalk.setInt(0);
            chkAgg.setSelected(false);
            cbDir.setSelectedItem(Direction.NORTH);
            updateFieldVisibility();
            lbDynId.setText("");
            lbDynFlags.setText("");
        } finally {
            suppressEvents = false;
        }
        setDeleteEnabled(false);
        markClean();
    }

    public void prepareForNewAtTile(Tile t, ActionType defaultAction) {
        suppressEvents = true;
        try {
            currentDynEntry = null;
            currentEntry = null;
            cbAction.setEnabled(true);
            setTile(t);
            cbAction.setSelectedItem(defaultAction);
            tfId.setInt(null);
            tfType.setInt(10);
            tfRot.setInt(0);
            tfWalk.setInt(0);
            chkAgg.setSelected(false);
            cbDir.setSelectedItem(Direction.NORTH);
            updateFieldVisibility();
            lbDynId.setText("");
            lbDynFlags.setText("");
        } finally {
            suppressEvents = false;
        }
        setDeleteEnabled(false);
        markClean();
    }

    public void requestSave() {
        if (!hasDirty() || currentTile == null) return;
        if (currentDynEntry != null) {
            Integer id = tfId.getInt(currentDynEntry.id);
            Tile newT = new Tile(tfX.getInt(currentDynEntry.x), tfY.getInt(currentDynEntry.y), tfZ.getInt(currentDynEntry.z));
            if (onEditDyn != null) onEditDyn.accept(currentDynEntry, id, newT);
            return;
        }
        ActionType a = (ActionType) cbAction.getSelectedItem();
        if (a == ActionType.DYNAMIC_NPC) {
            Integer id = tfId.getInt(null);
            if (id != null && onCreateDyn != null) {
                Tile t = new Tile(tfX.getInt(0), tfY.getInt(0), tfZ.getInt(0));
                onCreateDyn.accept(id, t);
            }
            return;
        }
        SpawnEntry out = buildEntryFromFields();
        if (onSaveJson != null && out != null) onSaveJson.accept(out);
    }

    public void setDeleteEnabled(boolean enabled) {
        btnDelete.setEnabled(enabled);
    }

    public void setSaveEnabled(boolean enabled) {
        btnSave.setEnabled(enabled);
    }

    public boolean hasDirty() {
        return dirty;
    }

    public void markClean() {
        dirty = false;
    }

    public Tile getCurrentTile() {
        return currentTile;
    }

    private static int normalizeRotSigned(int raw) {
        return raw % 4;
    }

    private SpawnEntry buildEntryFromFields() {
        Tile t = new Tile(tfX.getInt(0), tfY.getInt(0), tfZ.getInt(0));
        ActionType a = (ActionType) cbAction.getSelectedItem();
        SpawnEntry e = new SpawnEntry();
        e.action = a;
        e.tile = t;
        if (a == ActionType.SPAWN_OBJECT || a == ActionType.SPAWN_OVER_OBJECT) {
            e.id = tfId.getInt(0);
            e.type = tfType.getInt(10);
            e.rotation = normalizeRotSigned(tfRot.getInt(0));
        } else if (a == ActionType.SPAWN_NPC) {
            e.id = tfId.getInt(0);
            e.walkRadius = tfWalk.getInt(0);
            e.aggressive = chkAgg.isSelected();
            e.direction = (Direction) cbDir.getSelectedItem();
        }
        return e;
    }

    @FunctionalInterface
    public interface TriConsumer<A,B,C> {
        void accept(A a, B b, C c);
    }
}
