package com.spawneditor.model;

public enum Direction {
    NORTH, NORTH_EAST, EAST, SOUTH_EAST, SOUTH, SOUTH_WEST, WEST, NORTH_WEST, NONE;

    public static Direction safeValueOf(String s) {
        if (s == null) return NORTH;
        String k = s.trim().toUpperCase().replace("-", "").replace("_", "").replace(" ", "");

        return switch (k) {
            case "N", "NORTH" -> NORTH;
            case "NE", "NORTHEAST" -> NORTH_EAST;
            case "E", "EAST" -> EAST;
            case "SE", "SOUTHEAST" -> SOUTH_EAST;
            case "S", "SOUTH" -> SOUTH;
            case "SW", "SOUTHWEST" -> SOUTH_WEST;
            case "W", "WEST" -> WEST;
            case "NW", "NORTHWEST" -> NORTH_WEST;
            case "NONE", "STATIONARY" -> NONE;
            default -> NORTH;
        };
    }
}
