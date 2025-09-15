package com.spawneditor.model;

public enum ActionType {
    SPAWN_OBJECT("spawn_object"),
    SPAWN_OVER_OBJECT("spawn_over_object"),
    SPAWN_NPC("spawn_npc"),
    DELETE_OBJECT("delete_object"),
    DYNAMIC_NPC("dynamic_npc");

    public final String wire;

    ActionType(String wire) {
        this.wire = wire;
    }

    public static ActionType fromWire(String w) {
        for (ActionType a : ActionType.values()) {
            if (a.wire.equalsIgnoreCase(w)) {
                return a;
            }
        }
        return null;
    }
}
