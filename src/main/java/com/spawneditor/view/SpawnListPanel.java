package com.spawneditor.view;

import com.spawneditor.io.NpcIndex;
import com.spawneditor.io.ObjectIndex;
import com.spawneditor.io.DynamicNpcStore;
import com.spawneditor.model.ActionType;
import com.spawneditor.model.SpawnEntry;
import com.spawneditor.model.SpawnProject;
import com.spawneditor.model.DynamicNpcEntry;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public class SpawnListPanel extends JPanel {
    private final JTextField tfSearch = new JTextField(18);
    private final JButton btnSearch = new JButton("Search");
    private final JLabel lbSearchHelp = new JLabel(" name or id contains, all categories");
    private final JTabbedPane tabs = new JTabbedPane();
    private final JList<String> listSearch = new JList<>();
    private final JList<String> listObjects = new JList<>();
    private final JList<String> listNpcs = new JList<>();
    private final JList<String> listDynamic = new JList<>();
    private final DefaultListModel<String> modelSearch = new DefaultListModel<>();
    private final DefaultListModel<String> modelObjects = new DefaultListModel<>();
    private final DefaultListModel<String> modelNpcs = new DefaultListModel<>();
    private final DefaultListModel<String> modelDynamic = new DefaultListModel<>();
    private final List<SearchHit> rowsSearch = new ArrayList<>();
    private final List<SpawnEntry> rowsObjects = new ArrayList<>();
    private final List<SpawnEntry> rowsNpcs = new ArrayList<>();
    private final List<DynamicNpcEntry> rowsDynamic = new ArrayList<>();
    private Consumer<SpawnEntry> onSelect;
    private Consumer<DynamicNpcEntry> onSelectDyn;
    private SpawnProject project;
    private final ObjectIndex objectIndex;
    private final NpcIndex npcIndex;
    private final DynamicNpcStore dynStore;
    private final Timer searchTimer = new Timer(200, e -> runSearch());

    public SpawnListPanel(SpawnProject project, ObjectIndex objectIndex, NpcIndex npcIndex, DynamicNpcStore dynStore) {
        this.project = project;
        this.objectIndex = objectIndex;
        this.npcIndex = npcIndex;
        this.dynStore = dynStore;
        setLayout(new BorderLayout());
        JPanel searchBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
        searchBar.add(new JLabel("Search:"));
        searchBar.add(tfSearch);
        searchBar.add(btnSearch);
        lbSearchHelp.setForeground(new Color(120, 120, 120));
        searchBar.add(lbSearchHelp);
        add(searchBar, BorderLayout.NORTH);
        listSearch.setModel(modelSearch);
        listObjects.setModel(modelObjects);
        listNpcs.setModel(modelNpcs);
        listDynamic.setModel(modelDynamic);
        listSearch.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        listObjects.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        listNpcs.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        listDynamic.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tabs.addTab("Search", new JScrollPane(listSearch));
        tabs.addTab("Objects", new JScrollPane(listObjects));
        tabs.addTab("NPCs", new JScrollPane(listNpcs));
        tabs.addTab("Dynamic NPCs", new JScrollPane(listDynamic));
        add(tabs, BorderLayout.CENTER);
        listObjects.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int i = listObjects.getSelectedIndex();
                if (i >= 0 && i < rowsObjects.size() && onSelect != null) onSelect.accept(rowsObjects.get(i));
            }
        });
        listNpcs.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int i = listNpcs.getSelectedIndex();
                if (i >= 0 && i < rowsNpcs.size() && onSelect != null) onSelect.accept(rowsNpcs.get(i));
            }
        });
        listDynamic.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int i = listDynamic.getSelectedIndex();
                if (i >= 0 && i < rowsDynamic.size() && onSelectDyn != null) onSelectDyn.accept(rowsDynamic.get(i));
            }
        });
        listSearch.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                int i = listSearch.getSelectedIndex();
                if (i >= 0 && i < rowsSearch.size()) {
                    SearchHit hit = rowsSearch.get(i);
                    if (hit.kind == SearchKind.DYNAMIC && onSelectDyn != null) {
                        onSelectDyn.accept(hit.dynamic);
                    } else if (hit.kind != SearchKind.DYNAMIC && onSelect != null) {
                        onSelect.accept(hit.entry);
                    }
                }
            }
        });
        btnSearch.addActionListener(e -> runSearch());
        tfSearch.addActionListener(e -> runSearch());
        searchTimer.setRepeats(false);
        tfSearch.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                restartDebounce();
            }
            @Override
            public void removeUpdate(DocumentEvent e) {
                restartDebounce();
            }
            @Override
            public void changedUpdate(DocumentEvent e) {
                restartDebounce();
            }
            private void restartDebounce() {
                searchTimer.restart();
            }
        });
        refresh();
    }

    public void setOnSelect(Consumer<SpawnEntry> c) {
        this.onSelect = c;
    }

    public void setOnSelectDynamic(Consumer<DynamicNpcEntry> c) {
        this.onSelectDyn = c;
    }

    public void refresh() {
        modelObjects.clear(); rowsObjects.clear();
        modelNpcs.clear(); rowsNpcs.clear();
        for (SpawnEntry e : project.getEntries()) {
            if (e.tile == null) continue;
            if (e.action == ActionType.SPAWN_NPC) {
                String name = (e.id != null) ? npcIndex.nameFor(e.id) : "null";
                modelNpcs.addElement(fmt(e.id, name, e));
                rowsNpcs.add(e);
            } else if (e.action == ActionType.SPAWN_OBJECT || e.action == ActionType.SPAWN_OVER_OBJECT) {
                String name = (e.id != null) ? objectIndex.nameFor(e.id) : "null";
                modelObjects.addElement(fmt(e.id, name, e));
                rowsObjects.add(e);
            }
        }
        refreshDynamic();
    }

    public void refreshDynamic() {
        modelDynamic.clear(); rowsDynamic.clear();
        if (dynStore == null) return;
        for (DynamicNpcEntry d : dynStore.getEntries()) {
            String name = npcIndex.nameFor(d.id);
            modelDynamic.addElement(d.id + " - " + name + "  @ " + d.x + "," + d.y + "," + d.z);
            rowsDynamic.add(d);
        }
    }

    public void selectEntry(SpawnEntry entry) {
        if (entry == null) return;
        if (entry.action == ActionType.SPAWN_NPC) {
            tabs.setSelectedIndex(2);
            int idx = indexOf(rowsNpcs, entry);
            if (idx >= 0) {
                listNpcs.setSelectedIndex(idx);
                listNpcs.ensureIndexIsVisible(idx);
            }
        } else {
            tabs.setSelectedIndex(1);
            int idx = indexOf(rowsObjects, entry);
            if (idx >= 0) {
                listObjects.setSelectedIndex(idx);
                listObjects.ensureIndexIsVisible(idx);
            }
        }
    }

    public void selectDynamic(DynamicNpcEntry entry) {
        if (entry == null) return;
        tabs.setSelectedIndex(3);
        int idx = rowsDynamic.indexOf(entry);
        if (idx >= 0) {
            listDynamic.setSelectedIndex(idx);
            listDynamic.ensureIndexIsVisible(idx);
        } else {
            for (int i = 0; i < rowsDynamic.size(); i++) {
                DynamicNpcEntry d = rowsDynamic.get(i);
                if (d.id == entry.id && d.x == entry.x && d.y == entry.y && d.z == entry.z) {
                    listDynamic.setSelectedIndex(i);
                    listDynamic.ensureIndexIsVisible(i);
                    break;
                }
            }
        }
    }

    public ActionType getCurrentTabDefaultAction() {
        int idx = tabs.getSelectedIndex();
        if (idx == 2) return ActionType.SPAWN_NPC;
        if (idx == 3) return ActionType.DYNAMIC_NPC;
        return ActionType.SPAWN_OBJECT;
    }

    private static int indexOf(List<SpawnEntry> list, SpawnEntry target) {
        for (int i = 0; i < list.size(); i++) {
            SpawnEntry e = list.get(i);
            if (e.action == target.action && e.tile.equals(target.tile)) return i;
        }
        return -1;
    }

    private static String fmt(Integer id, String name, SpawnEntry e) {
        String idStr = (id == null) ? "null" : String.valueOf(id);
        return idStr + " - " + name + "  @ " + e.tile.x + "," + e.tile.y + "," + e.tile.z;
    }

    private enum SearchKind { OBJECT, NPC, DYNAMIC }

    private static class SearchHit {
        final SearchKind kind;
        final SpawnEntry entry;
        final DynamicNpcEntry dynamic;
        SearchHit(SearchKind kind, SpawnEntry entry, DynamicNpcEntry dynamic) {
            this.kind = kind;
            this.entry = entry;
            this.dynamic = dynamic;
        }
    }

    private void runSearch() {
        String qRaw = tfSearch.getText();
        if (qRaw == null) qRaw = "";
        String q = qRaw.trim().toLowerCase(Locale.ROOT);
        String qDigits = extractDigits(qRaw);
        modelSearch.clear();
        rowsSearch.clear();
        if (q.isEmpty() && qDigits.isEmpty()) {
            tabs.setSelectedIndex(0);
            return;
        }
        for (SpawnEntry e : project.getEntries()) {
            if (e.tile == null) continue;
            if (e.action == ActionType.SPAWN_OBJECT || e.action == ActionType.SPAWN_OVER_OBJECT) {
                String name = (e.id != null) ? objectIndex.nameFor(e.id) : "null";
                String idStr = (e.id != null) ? String.valueOf(e.id) : "";
                if (matches(name, idStr, q, qDigits)) {
                    modelSearch.addElement("[object] " + fmt(e.id, name, e));
                    rowsSearch.add(new SearchHit(SearchKind.OBJECT, e, null));
                }
            }
        }
        for (SpawnEntry e : project.getEntries()) {
            if (e.tile == null) continue;
            if (e.action == ActionType.SPAWN_NPC) {
                String name = (e.id != null) ? npcIndex.nameFor(e.id) : "null";
                String idStr = (e.id != null) ? String.valueOf(e.id) : "";
                if (matches(name, idStr, q, qDigits)) {
                    modelSearch.addElement("[npc] " + fmt(e.id, name, e));
                    rowsSearch.add(new SearchHit(SearchKind.NPC, e, null));
                }
            }
        }
        if (dynStore != null) {
            for (DynamicNpcEntry d : dynStore.getEntries()) {
                String name = npcIndex.nameFor(d.id);
                String idStr = String.valueOf(d.id);
                if (matches(name, idStr, q, qDigits)) {
                    modelSearch.addElement("[dynamic] " + d.id + " - " + name + "  @ " + d.x + "," + d.y + "," + d.z);
                    rowsSearch.add(new SearchHit(SearchKind.DYNAMIC, null, d));
                }
            }
        }
        tabs.setSelectedIndex(0);
        if (modelSearch.getSize() > 0) {
            listSearch.setSelectedIndex(0);
            listSearch.ensureIndexIsVisible(0);
        }
    }

    private static boolean matches(String name, String idStr, String q, String qDigits) {
        boolean nameMatch = !q.isEmpty() && name.toLowerCase(Locale.ROOT).contains(q);
        boolean idMatch = !qDigits.isEmpty() && idStr.contains(qDigits);
        return nameMatch || idMatch;
    }

    private static String extractDigits(String s) {
        if (s == null || s.isEmpty()) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch >= '0' && ch <= '9') sb.append(ch);
        }
        return sb.toString();
    }
}
