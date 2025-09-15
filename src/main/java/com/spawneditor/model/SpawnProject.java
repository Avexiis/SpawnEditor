package com.spawneditor.model;

import java.util.*;
import java.util.stream.Collectors;

public class SpawnProject {
    private final List<SpawnEntry> entries = new ArrayList<>();

    public List<SpawnEntry> getEntries() { return entries; }
    public void clear() { entries.clear(); }

    public Optional<SpawnEntry> findFirstAt(Tile t) {
        if (t == null) return Optional.empty();
        for (SpawnEntry e : entries) if (e.matchesTile(t)) return Optional.of(e);
        return Optional.empty();
    }

    public Optional<SpawnEntry> findFirstAtXY(Tile t) {
        if (t == null) return Optional.empty();
        for (SpawnEntry e : entries) {
            if (e.tile != null && e.tile.x == t.x && e.tile.y == t.y) {
                return Optional.of(e);
            }
        }
        return Optional.empty();
    }

    public Optional<SpawnEntry> findBestForClick(Tile t) {
        Optional<SpawnEntry> exact = findFirstAt(t);
        if (exact.isPresent()) return exact;
        return findFirstAtXY(t);
    }

    public void upsert(SpawnEntry entry) {
        for (int i = 0; i < entries.size(); i++) {
            SpawnEntry e = entries.get(i);
            if (e.action == entry.action && e.matchesTile(entry.tile)) {
                entries.set(i, entry.copy());
                return;
            }
        }
        entries.add(entry.copy());
    }

    public void remove(SpawnEntry entry) {
        entries.removeIf(e -> e.action == entry.action && e.matchesTile(entry.tile));
    }

    public List<SpawnEntry> entriesAtPlane(int z) {
        return entries.stream().filter(e -> e.tile != null && e.tile.z == z).collect(Collectors.toList());
    }
}
