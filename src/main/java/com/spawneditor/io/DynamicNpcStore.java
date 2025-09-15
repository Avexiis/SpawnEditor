package com.spawneditor.io;

import com.spawneditor.model.DynamicNpcEntry;
import com.spawneditor.model.Tile;
import com.spawneditor.util.Ui;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;

public class DynamicNpcStore {
    public interface NameLookup extends IntFunction<String> {}
    private final String path;
    private final List<String> allLines = new ArrayList<>();
    private final List<DynamicNpcEntry> entries = new ArrayList<>();
    private NameLookup nameLookup;
    public DynamicNpcStore(String path) {
        this(path, null);
    }

    public DynamicNpcStore(String path, NameLookup nameLookup) {
        this.path = path;
        this.nameLookup = nameLookup;
    }

    public void setNameLookup(NameLookup lookup) {
        this.nameLookup = lookup;
    }

    public List<DynamicNpcEntry> getEntries() {
        return entries;
    }

    public void load() {
        entries.clear();
        allLines.clear();
        File f = new File(path);
        if (!f.exists()) {
            Ui.warn("Dynamic NPC list not found: " + path);
            return;
        }
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
            String line;
            int idx = 0;
            while ((line = br.readLine()) != null) {
                allLines.add(line);
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("//")) { idx++; continue; }
                int dash = trimmed.indexOf(" - ");
                if (dash < 0) { idx++; continue; }
                try {
                    int npcId = Integer.parseInt(trimmed.substring(0, dash).trim());
                    String rest = trimmed.substring(dash + 3).trim();
                    String[] parts = rest.split("\\s+");
                    if (parts.length != 3) { idx++; continue; }
                    DynamicNpcEntry e = new DynamicNpcEntry();
                    e.id = npcId;
                    e.x = Integer.parseInt(parts[0]);
                    e.y = Integer.parseInt(parts[1]);
                    e.z = Integer.parseInt(parts[2]);
                    e.mapAreaNameHash = -1;
                    e.canBeAttackFromOutOfArea = null;
                    e.lineIndex = idx;
                    e.rawLine = line;
                    entries.add(e);
                } catch (Exception ignore) {}
                idx++;
            }
        } catch (Exception e) {
            Ui.error("Failed to load dynamic NPC list: " + e.getMessage());
        }
    }

    public void updateEntry(DynamicNpcEntry entry, int newId, Tile newTile) {
        if (entry == null) return;
        int dataIdx = entry.lineIndex;
        if (dataIdx < 0 || dataIdx >= allLines.size()) return;
        String newData = buildSpawnLine(newId, newTile.x, newTile.y, newTile.z);
        String comment = editorCommentFor(newId);
        if (dataIdx > 0 && allLines.get(dataIdx - 1).trim().startsWith("//")) {
            allLines.set(dataIdx - 1, comment);
        } else {
            allLines.add(dataIdx, comment);
            dataIdx++;
            shiftEntryLineIndexesFrom(dataIdx, +1);
        }
        allLines.set(dataIdx, newData);
        entry.id = newId;
        entry.x = newTile.x; entry.y = newTile.y; entry.z = newTile.z;
        entry.lineIndex = dataIdx;
        entry.rawLine = newData;
        writeAll();
    }

    public DynamicNpcEntry appendEntry(int id, Tile tile) {
        String comment = editorCommentFor(id);
        String data = buildSpawnLine(id, tile.x, tile.y, tile.z);
        int insertAt = allLines.size();
        allLines.add(comment);
        allLines.add(data);
        DynamicNpcEntry e = new DynamicNpcEntry();
        e.id = id;
        e.x = tile.x; e.y = tile.y; e.z = tile.z;
        e.mapAreaNameHash = -1;
        e.canBeAttackFromOutOfArea = null;
        e.lineIndex = insertAt + 1;
        e.rawLine = data;
        entries.add(e);
        writeAll();
        return e;
    }

    public void deleteEntry(DynamicNpcEntry entry) {
        if (entry == null) return;
        int dataIdx = entry.lineIndex;
        if (dataIdx < 0 || dataIdx >= allLines.size()) return;
        allLines.remove(dataIdx);
        int removed = 1;
        while (dataIdx - 1 >= 0) {
            String prev = allLines.get(dataIdx - 1).trim();
            if (prev.startsWith("//")) {
                allLines.remove(dataIdx - 1);
                removed++;
                dataIdx--;
            } else {
                break;
            }
        }
        int oldDataLine = entry.lineIndex;
        entries.remove(entry);
        for (DynamicNpcEntry e : entries) {
            if (e.lineIndex > oldDataLine) {
                e.lineIndex -= removed;
            }
        }
        writeAll();
    }

    private String editorCommentFor(int id) {
        String name = null;
        try { if (nameLookup != null) name = nameLookup.apply(id); } catch (Exception ignore) {}
        if (name == null) name = "null";
        return "//" + name + " spawned by Xeon's Spawn Editor";
    }

    private static String buildSpawnLine(int id, int x, int y, int z) {
        return id + " - " + x + " " + y + " " + z;
    }

    private void shiftEntryLineIndexesFrom(int startInclusive, int delta) {
        if (delta == 0) return;
        for (DynamicNpcEntry e : entries) {
            if (e.lineIndex >= startInclusive) e.lineIndex += delta;
        }
    }

    private void writeAll() {
        try (BufferedWriter bw = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(path), StandardCharsets.UTF_8))) {
            for (String ln : allLines) {
                bw.write(ln);
                bw.newLine();
            }
        } catch (Exception e) {
            Ui.error("Failed to save dynamic NPC list: " + e.getMessage());
        }
    }

    public DynamicNpcEntry findFirstAt(Tile t) {
        if (t == null) return null;
        for (DynamicNpcEntry e : entries) {
            if (e.x == t.x && e.y == t.y && e.z == t.z) return e;
        }
        return null;
    }
    public DynamicNpcEntry findFirstAtXY(Tile t) {
        if (t == null) return null;
        for (DynamicNpcEntry e : entries) {
            if (e.x == t.x && e.y == t.y) return e;
        }
        return null;
    }
    public DynamicNpcEntry findBestForClick(Tile t) {
        DynamicNpcEntry exact = findFirstAt(t);
        return (exact != null) ? exact : findFirstAtXY(t);
    }
}
