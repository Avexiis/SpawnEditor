package com.spawneditor.controller;

import com.spawneditor.App;
import com.spawneditor.io.DynamicNpcStore;
import com.spawneditor.io.JsonStore;
import com.spawneditor.io.NpcIndex;
import com.spawneditor.io.ObjectIndex;
import com.spawneditor.io.Paths;
import com.spawneditor.model.ActionType;
import com.spawneditor.model.SpawnEntry;
import com.spawneditor.model.SpawnProject;
import com.spawneditor.model.Tile;
import com.spawneditor.model.DynamicNpcEntry;
import com.spawneditor.util.Ui;
import com.spawneditor.util.Images;
import com.spawneditor.view.MapPanel;
import com.spawneditor.view.PathsDialog;
import com.spawneditor.view.SidebarPanel;
import com.spawneditor.view.SpawnListPanel;
import com.spawneditor.view.ToolbarPanel;
import com.spawneditor.rpc.DiscordPresence;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class EditorController {
    private final SpawnProject project;
    private final DiscordPresence presence;
    private ObjectIndex objectIndex;
    private NpcIndex npcIndex;
    private DynamicNpcStore dynStore;
    private JsonStore jsonStore;
    private JFrame frame;
    private MapPanel mapPanel;
    private SidebarPanel sidebar;
    private ToolbarPanel toolbar;
    private SpawnListPanel spawnList;
    private BufferedImage appIcon = null;

    public EditorController(SpawnProject project, ObjectIndex objectIndex, NpcIndex npcIndex, DynamicNpcStore dynStore, DiscordPresence presence) {
        this.project = project;
        this.objectIndex = objectIndex;
        this.npcIndex = npcIndex;
        this.dynStore = dynStore;
        this.presence = presence;
    }

    public void show() {
        frame = new JFrame("Spawn Editor by Xeon");
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.addWindowFocusListener(new java.awt.event.WindowFocusListener() {
            @Override
            public void windowGainedFocus(java.awt.event.WindowEvent e) {
                if (presence != null) presence.setActive(true);
            }
            @Override
            public void windowLostFocus(java.awt.event.WindowEvent e) {
                if (presence != null) presence.setActive(false);
            }
        });
        frame.setLayout(new BorderLayout());
        try (InputStream in = App.class.getResourceAsStream("icon.png")) {
            if (in == null) {
                System.err.println("Icon resource not found next to App class: /com/spawneditor/icon.png");
            } else {
                appIcon = ImageIO.read(in);
            }
        } catch (Throwable t) {
            System.err.println("Failed to load icon resource: " + t.getMessage());
        }
        if (appIcon != null) {
            try {
                int[] sizes = new int[] {16, 20, 24, 32, 48, 64, 128, 256, 512};
                List<Image> icons = new ArrayList<>(sizes.length);
                for (int sz : sizes) {
                    Image scaled = Images.scale(appIcon, sz, sz);
                    if (scaled != null) icons.add(scaled);
                }
                if (!icons.isEmpty()) {
                    frame.setIconImages(icons);
                }
            } catch (Throwable ignore) {
            }
        }
        buildUi();
        frame.setSize(1600, 900);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private void buildUi() {
        if (frame.getContentPane().getComponentCount() > 0) {
            frame.getContentPane().removeAll();
        }
        try {
            mapPanel = new MapPanel(ImageIO.read(new File(Paths.MAP_IMAGE)), project);
        } catch (Exception e) {
            Ui.error("Could not load map image: " + Paths.MAP_IMAGE + "\n" + e.getMessage());
            mapPanel = new MapPanel(null, project);
        }
        jsonStore = new JsonStore(Paths.SPAWNS_JSON);
        refreshAll(null);
        sidebar = new SidebarPanel(objectIndex, npcIndex);
        toolbar = new ToolbarPanel();
        spawnList = new SpawnListPanel(project, objectIndex, npcIndex, dynStore);
        int leftWidth = 320;
        spawnList.setMinimumSize(new Dimension(leftWidth, 0));
        spawnList.setPreferredSize(new Dimension(leftWidth, 0));
        spawnList.setMaximumSize(new Dimension(leftWidth, Integer.MAX_VALUE));
        mapPanel.setDynamicSupplier(() -> dynStore.getEntries());
        mapPanel.setOnTileClicked(tile -> handleTileSelection(tile, true));
        spawnList.setOnSelect(entry -> {
            if (entry != null) handleTileSelection(entry.tile, false);
        });
        spawnList.setOnSelectDynamic(dyn -> {
            if (dyn != null) handleTileSelection(dyn.toTile(), false);
        });
        sidebar.setOnSaveJson(entry -> {
            if (entry != null) {
                jsonStore.upsertOne(entry);
                refreshAll(entry.tile);
                sidebar.markClean();
            }
        });
        sidebar.setOnEditDynamic((dyn, newId, newTile) -> {
            if (dyn != null && newTile != null) {
                dynStore.updateEntry(dyn, newId, newTile);
                refreshAll(newTile);
                sidebar.markClean();
            }
        });
        sidebar.setOnCreateDynamic((id, tile) -> {
            if (tile != null) {
                DynamicNpcEntry created = dynStore.appendEntry(id, tile); //Do not remove
                refreshAll(tile);
                sidebar.markClean();
                DynamicNpcEntry match = dynStore.findBestForClick(tile);
                if (match != null) {
                    spawnList.selectDynamic(match);
                    sidebar.setDynamicEntry(match);
                }
                mapPanel.focusTile(tile, null);
            }
        });
        sidebar.setOnDeleteJson(entry -> {
            if (entry != null) {
                Tile t = entry.tile;
                jsonStore.deleteOne(entry);
                refreshAll(t);
                sidebar.clearFields();
                selectContextAt(t, true);
            }
        });
        sidebar.setOnDeleteDynamic(dyn -> {
            if (dyn != null) {
                Tile t = dyn.toTile();
                dynStore.deleteEntry(dyn);
                refreshAll(t);
                sidebar.clearFields();
                selectContextAt(t, true);
            }
        });
        sidebar.setOnDeleteAtTile(tile -> {
            if (tile != null) {
                Optional<SpawnEntry> json = project.findBestForClick(tile);
                if (json.isPresent()) {
                    jsonStore.deleteOne(json.get());
                    refreshAll(tile);
                    sidebar.clearFields();
                    selectContextAt(tile, true);
                    return;
                }
                DynamicNpcEntry dyn = dynStore.findBestForClick(tile);
                if (dyn != null) {
                    dynStore.deleteEntry(dyn);
                    refreshAll(tile);
                    sidebar.clearFields();
                    selectContextAt(tile, true);
                }
            }
        });
        toolbar.onZoomChanged.addListener(mapPanel::setZoom);
        toolbar.onToggleGrid.addListener(mapPanel::setShowGrid);
        toolbar.onToggleTypes.addListener(mapPanel::setTypeVisibility);
        toolbar.onJumpToTile.addListener(tile -> { if (tile != null) handleTileSelection(tile, false); });
        toolbar.setOnRefresh(e -> refreshAll(sidebar != null ? sidebar.getCurrentTile() : null));
        toolbar.setOnSaveAs(e -> {
            File f = com.spawneditor.util.Ui.saveJson(frame, "Save spawns.json as");
            if (f != null) {
                Paths.SPAWNS_JSON = f.getAbsolutePath();
                jsonStore.save(project);
                refreshAll(null);
            }
        });
        toolbar.setOnPaths(e -> {
            PathsDialog dlg = new PathsDialog(frame);
            dlg.setVisible(true);
            if (dlg.isAccepted()) {
                objectIndex = new ObjectIndex(Paths.OBJECTS_JSON);
                npcIndex = new NpcIndex(Paths.NPCS_JSON);
                dynStore = new DynamicNpcStore(Paths.DYNAMIC_NPCS_TXT, npcIndex::nameFor);
                jsonStore = new JsonStore(Paths.SPAWNS_JSON);
                refreshAll(null);
                buildUi();
                frame.revalidate();
                frame.repaint();
            }
        });
        frame.add(toolbar, BorderLayout.NORTH);
        frame.add(new JScrollPane(mapPanel), BorderLayout.CENTER);
        frame.add(sidebar, BorderLayout.EAST);
        frame.add(spawnList, BorderLayout.WEST);
    }

    private void refreshAll(Tile focusTile) {
        objectIndex.load();
        npcIndex.load();
        dynStore.setNameLookup(npcIndex::nameFor);
        dynStore.load();
        project.clear();
        jsonStore.loadInto(project);
        if (spawnList != null) spawnList.refresh();
        if (mapPanel != null) mapPanel.repaint();
        if (focusTile != null) selectContextAt(focusTile, true);
    }

    private void selectContextAt(Tile tile, boolean prepareNewIfNone) {
        if (tile == null) return;
        sidebar.setTile(tile);
        Optional<SpawnEntry> match = project.findBestForClick(tile);
        if (match.isPresent()) {
            SpawnEntry entry = match.get();
            sidebar.setEntry(entry);
            spawnList.selectEntry(entry);
            sidebar.setDeleteEnabled(true);
            sidebar.setSaveEnabled(true);
            mapPanel.focusTile(entry.tile, null);
            return;
        }
        DynamicNpcEntry dyn = dynStore.findBestForClick(tile);
        if (dyn != null) {
            sidebar.setDynamicEntry(dyn);
            spawnList.selectDynamic(dyn);
            sidebar.setDeleteEnabled(true);
            sidebar.setSaveEnabled(true);
            mapPanel.focusTile(dyn.toTile(), null);
            return;
        }
        if (prepareNewIfNone) {
            ActionType def = spawnList.getCurrentTabDefaultAction();
            sidebar.prepareForNewAtTile(tile, def);
        }
        sidebar.setDeleteEnabled(false);
        sidebar.setSaveEnabled(true);
        mapPanel.focusTile(tile, null);
    }

    private void commitPendingIfAny() {
        if (sidebar != null && sidebar.hasDirty() && sidebar.getCurrentTile() != null) {
            sidebar.requestSave();
        }
    }

    private void handleTileSelection(Tile tile, boolean prepareNewIfNone) {
        if (tile == null) return;
        commitPendingIfAny();
        selectContextAt(tile, prepareNewIfNone);
        if (presence != null) presence.setTile(tile);
    }
}
