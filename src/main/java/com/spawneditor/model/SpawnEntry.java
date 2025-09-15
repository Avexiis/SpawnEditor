package com.spawneditor.model;

public class SpawnEntry {
    public ActionType action;
    public Tile tile;
    public Integer id;
    public Integer type;
    public Integer rotation;
    public Integer walkRadius;
    public Boolean aggressive;
    public Direction direction;

    public boolean matchesTile(Tile t) {
        return t != null && tile != null && tile.equals(t);
    }

    public SpawnEntry copy() {
        SpawnEntry e = new SpawnEntry();
        e.action = this.action;
        e.tile = new Tile(tile.x, tile.y, tile.z);
        e.id = this.id;
        e.type = this.type;
        e.rotation = this.rotation;
        e.walkRadius = this.walkRadius;
        e.aggressive = this.aggressive;
        e.direction = this.direction;
        return e;
    }
}
