package com.spawneditor.model;

public class DynamicNpcEntry {
    public int id;
    public int x, y, z;
    public Integer mapAreaNameHash;
    public Boolean canBeAttackFromOutOfArea;
    public int lineIndex;
    public String rawLine;

    public Tile toTile() {
        return new Tile(x, y, z);
    }

    @Override
    public String toString() {
        String extra = "";
        if (mapAreaNameHash != null && mapAreaNameHash != -1) {
            extra = "  hash=" + mapAreaNameHash + "  outOfArea=" + canBeAttackFromOutOfArea;
        }
        return id + " @ " + x + "," + y + "," + z + extra;
    }
}
