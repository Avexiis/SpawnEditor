package com.spawneditor.io;

import com.google.gson.*;
import com.spawneditor.model.ActionType;
import com.spawneditor.model.Direction;
import com.spawneditor.model.SpawnEntry;
import com.spawneditor.model.SpawnProject;
import com.spawneditor.model.Tile;
import com.spawneditor.util.Ui;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class JsonStore {
    private final String path;
    private final List<String> allLines = new ArrayList<>();
    private int arrayStartLine = -1;
    private int arrayEndLine = -1;

    private static class Key {
        final ActionType action; final int x,y,z;
        Key(ActionType a, int x, int y, int z) {
            this.action=a; this.x=x; this.y=y; this.z=z;
        }
        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Key)) return false;
            Key k=(Key)o; return action==k.action && x==k.x && y==k.y && z==k.z;
        }
        @Override
        public int hashCode() {
            return Objects.hash(action,x,y,z);
        }
    }

    private static class Meta {
        int startLine, endLine;
        boolean trailingComma;
        String indent;
        Key key;
    }

    private final List<Meta> metas = new ArrayList<>();
    private final Map<Key, Integer> indexByKey = new HashMap<>();

    public JsonStore(String path) {
        this.path = path;
    }

    public void loadInto(SpawnProject project) {
        project.clear();
        allLines.clear();
        metas.clear();
        indexByKey.clear();
        arrayStartLine=-1; arrayEndLine=-1;
        File f = new File(path);
        if (!f.exists()) return;
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
            String ln; while ((ln = br.readLine()) != null) allLines.add(ln);
        } catch (Exception e) {
            Ui.error("Failed to read JSON: " + e.getMessage());
            return;
        }
        try (Reader reader = new StringReader(String.join("\n", allLines))) {
            JsonElement el = JsonParser.parseReader(reader);
            if (el == null || !el.isJsonArray()) {
                Ui.warn("Invalid JSON root (expected array): " + path);
                return;
            }
            JsonArray arr = el.getAsJsonArray();
            for (JsonElement e : arr) {
                if (!e.isJsonObject()) continue;
                SpawnEntry entry = parseEntry(e.getAsJsonObject());
                if (entry != null) project.getEntries().add(entry);
            }
        } catch (Exception ex) {
            Ui.error("Failed to parse JSON: " + ex.getMessage());
            return;
        }
        findArrayBracketLines();
        if (arrayStartLine < 0 || arrayEndLine < 0 || arrayEndLine <= arrayStartLine) return;
        int i = arrayStartLine + 1;
        while (i < arrayEndLine) {
            String line = allLines.get(i);
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("//")) { i++; continue; }
            int bracePos = line.indexOf('{');
            if (bracePos < 0) { i++; continue; }
            int start = i;
            int open = 0;
            boolean found = false;
            int j = i;
            while (j < arrayEndLine) {
                String L = allLines.get(j);
                for (int k = 0; k < L.length(); k++) {
                    char c = L.charAt(k);
                    if (c == '{') open++;
                    else if (c == '}') {
                        open--;
                        if (open == 0) { found = true; }
                    }
                }
                if (found) break;
                j++;
            }
            if (!found) break;
            int end = j;
            boolean trailingComma = lineHasTrailingComma(end);
            String objText = String.join("\n", allLines.subList(start, end + 1));
            JsonObject obj;
            try { obj = JsonParser.parseString(objText).getAsJsonObject(); }
            catch (Exception ignore) { obj = null; }
            if (obj != null) {
                SpawnEntry e = parseEntry(obj);
                if (e != null) {
                    Key key = new Key(e.action, e.tile.x, e.tile.y, e.tile.z);
                    Meta m = new Meta();
                    m.startLine = start;
                    m.endLine = end;
                    m.trailingComma = trailingComma;
                    m.indent = leadingWhitespace(allLines.get(start));
                    m.key = key;
                    metas.add(m);
                    indexByKey.put(key, metas.size() - 1);
                }
            }
            i = end + 1;
        }
    }

    private SpawnEntry parseEntry(JsonObject o) {
        String actionStr = getString(o, "action", null);
        if (actionStr == null) return null;
        ActionType action = ActionType.fromWire(actionStr);
        if (action == null) return null;
        JsonObject t = o.getAsJsonObject("tile");
        if (t == null) return null;
        Integer x = getInt(t, "x", null);
        Integer y = getInt(t, "y", null);
        Integer z = getInt(t, "z", null);
        if (x == null || y == null || z == null) return null;
        SpawnEntry entry = new SpawnEntry();
        entry.action = action;
        entry.tile = new Tile(x, y, z);
        switch (action) {
            case SPAWN_OBJECT:
            case SPAWN_OVER_OBJECT:
                entry.id = getInt(o, "id", null);
                entry.type = getInt(o, "type", 10);
                Integer rawRot = getInt(o, "rotation", 0);
                entry.rotation = rawRot != null ? normalizeRotSigned(rawRot) : 0;
                break;
            case SPAWN_NPC:
                entry.id = getInt(o, "id", null);
                entry.walkRadius = getInt(o, "walk_radius", 0);
                entry.aggressive = getBool(o, "aggressive", false);
                String d = getString(o, "direction", "NORTH");
                entry.direction = Direction.safeValueOf(d);
                break;
            case DELETE_OBJECT:
                break;
        }
        return entry;
    }

    private void findArrayBracketLines() {
        for (int i = 0; i < allLines.size(); i++) {
            if (allLines.get(i).indexOf('[') >= 0) { arrayStartLine = i; break; }
        }
        for (int i = allLines.size() - 1; i >= 0; i--) {
            if (allLines.get(i).indexOf(']') >= 0) { arrayEndLine = i; break; }
        }
    }

    private boolean lineHasTrailingComma(int lineIndex) {
        String s = allLines.get(lineIndex).trim();
        if (s.endsWith(",")) return true;
        int next = nextNonEmptyLine(lineIndex + 1);
        if (next >= 0) {
            String t = allLines.get(next).trim();
            if (t.startsWith(",")) return true;
        }
        return false;
    }

    private int nextNonEmptyLine(int from) {
        for (int i = from; i < allLines.size(); i++) {
            String t = allLines.get(i).trim();
            if (!t.isEmpty()) return i;
        }
        return -1;
    }

    private static String leadingWhitespace(String s) {
        int i = 0; while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
        return s.substring(0, i);
    }

    public synchronized void upsertOne(SpawnEntry e) {
        if (arrayStartLine < 0 || arrayEndLine < 0) {
            Ui.error("spawns.json not loaded/indexed; cannot edit in-place.");
            return;
        }
        Key key = new Key(e.action, e.tile.x, e.tile.y, e.tile.z);
        Integer idx = indexByKey.get(key);
        if (idx != null) {
            replaceObjectLines(idx, renderObject(e, metas.get(idx).indent, metas.get(idx).trailingComma));
        } else {
            appendObject(e);
        }
    }

    public synchronized void deleteOne(SpawnEntry e) {
        if (arrayStartLine < 0 || arrayEndLine < 0) {
            deleteViaRewrite(e);
            return;
        }
        Key key = new Key(e.action, e.tile.x, e.tile.y, e.tile.z);
        Integer idx = indexByKey.get(key);
        if (idx != null) {
            Meta m = metas.get(idx);
            int start = m.startLine;
            int end   = m.endLine;
            Integer commaLineIndex = null;
            boolean commaOnEndLine = allLines.get(end).trim().endsWith(",");
            if (m.trailingComma && !commaOnEndLine) {
                int ci = nextNonEmptyLine(end + 1);
                if (ci >= 0) {
                    String t = allLines.get(ci).trim();
                    if (t.equals(",")) commaLineIndex = ci;
                    else if (t.startsWith(",")) commaLineIndex = ci;
                }
            }
            int removed = (end - start + 1);
            for (int i = 0; i < removed; i++) allLines.remove(start);
            if (commaLineIndex != null) {
                commaLineIndex -= removed;
                String s = allLines.get(commaLineIndex);
                String t = s.trim();
                if (t.equals(",")) {
                    allLines.remove(commaLineIndex);
                    removed += 1;
                } else {
                    int pos = s.indexOf(',');
                    if (pos >= 0) {
                        allLines.set(commaLineIndex, s.substring(0, pos) + s.substring(pos + 1));
                    }
                }
            }
            metas.remove((int) idx);
            rebuildIndexMapAndShiftFrom(start, -removed);
            writeAll();
            return;
        }
        deleteViaRewrite(e);
    }

    private void deleteViaRewrite(SpawnEntry target) {
        try {
            JsonElement root = JsonParser.parseString(String.join("\n", allLines));
            if (root == null || !root.isJsonArray()) {
                Ui.warn("spawns.json root is not an array; cannot delete in-place. Skipping.");
                return;
            }
            JsonArray arr = root.getAsJsonArray();
            JsonArray out = new JsonArray();
            boolean removedOne = false;
            for (JsonElement el : arr) {
                boolean match = !removedOne && elementMatches(el, target);
                if (match) {
                    removedOne = true;
                    continue;
                }
                out.add(el);
            }
            if (!removedOne) {
                Ui.warn("Delete: entry not found in spawns.json (action + tile).");
                return;
            }
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            try (Writer w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(path), StandardCharsets.UTF_8))) {
                gson.toJson(out, w);
            }
            loadInto(new com.spawneditor.model.SpawnProject());
        } catch (Exception ex) {
            Ui.error("Failed to delete via rewrite: " + ex.getMessage());
        }
    }

    private static boolean elementMatches(JsonElement el, SpawnEntry target) {
        if (el == null || !el.isJsonObject()) return false;
        JsonObject o = el.getAsJsonObject();
        String act = getString(o, "action", null);
        if (act == null || ActionType.fromWire(act) != target.action) return false;
        JsonObject t = o.getAsJsonObject("tile");
        if (t == null) return false;
        Integer x = getInt(t, "x", null);
        Integer y = getInt(t, "y", null);
        Integer z = getInt(t, "z", null);
        return x != null && y != null && z != null && x == target.tile.x && y == target.tile.y && z == target.tile.z;
    }

    private void replaceObjectLines(int metaIndex, List<String> newLines) {
        Meta m = metas.get(metaIndex);
        int oldCount = m.endLine - m.startLine + 1;
        int delta = newLines.size() - oldCount;
        for (int i = 0; i < oldCount; i++) allLines.remove(m.startLine);
        allLines.addAll(m.startLine, newLines);
        m.endLine = m.startLine + newLines.size() - 1;
        shiftAfter(m.startLine + newLines.size(), delta, metaIndex + 1);
        writeAll();
    }

    private void appendObject(SpawnEntry e) {
        if (!metas.isEmpty()) {
            Meta last = metas.get(metas.size() - 1);
            if (!last.trailingComma) {
                String s = allLines.get(last.endLine);
                if (s.trim().endsWith("}")) {
                    allLines.set(last.endLine, s + ",");
                    last.trailingComma = true;
                }
            }
        }
        String indent = metas.isEmpty() ? (leadingWhitespace(allLines.get(arrayStartLine)) + "  ") : metas.get(0).indent;
        List<String> objLines = renderObject(e, indent, false);
        int insertAt = arrayEndLine;
        allLines.addAll(insertAt, objLines);
        int added = objLines.size();
        Meta m = new Meta();
        m.startLine = insertAt;
        m.endLine = insertAt + objLines.size() - 1;
        m.trailingComma = false;
        m.indent = indent;
        m.key = new Key(e.action, e.tile.x, e.tile.y, e.tile.z);
        metas.add(m);
        arrayEndLine += added;
        rebuildIndexMap();
        writeAll();
    }

    private void shiftAfter(int startLineExclusive, int delta, int metaStartIndex) {
        if (delta == 0) return;
        for (int i = metaStartIndex; i < metas.size(); i++) {
            Meta mm = metas.get(i);
            if (mm.startLine >= startLineExclusive) {
                mm.startLine += delta;
                mm.endLine += delta;
            }
        }
        arrayEndLine += delta;
    }

    private void rebuildIndexMapAndShiftFrom(int fromLine, int deltaLines) {
        if (deltaLines != 0) {
            for (Meta mm : metas) {
                if (mm.startLine >= fromLine) {
                    mm.startLine += deltaLines;
                    mm.endLine += deltaLines;
                }
            }
            arrayEndLine += deltaLines;
        }
        rebuildIndexMap();
    }

    private void rebuildIndexMap() {
        indexByKey.clear();
        for (int i = 0; i < metas.size(); i++) {
            indexByKey.put(metas.get(i).key, i);
        }
    }

    private List<String> renderObject(SpawnEntry e, String indent, boolean trailingComma) {
        List<String> out = new ArrayList<>();
        String p1 = indent;
        String p2 = indent + "  ";
        String p3 = indent + "    ";
        out.add(p1 + "{");
        out.add(p2 + "\"action\": \"" + e.action.wire + "\",");
        out.add(p2 + "\"tile\": {");
        out.add(p3 + "\"x\": " + e.tile.x + ",");
        out.add(p3 + "\"y\": " + e.tile.y + ",");
        out.add(p3 + "\"z\": " + e.tile.z);
        out.add(p2 + "}" + (needsMoreFields(e) ? "," : ""));
        switch (e.action) {
            case SPAWN_OBJECT:
            case SPAWN_OVER_OBJECT: {
                out.add(p2 + "\"id\": " + n(e.id) + ",");
                out.add(p2 + "\"type\": " + (e.type != null ? e.type : 10) + ",");
                int rot = e.rotation != null ? normalizeRotSigned(e.rotation) : 0;
                out.add(p2 + "\"rotation\": " + rot);
                break;
            }
            case SPAWN_NPC: {
                out.add(p2 + "\"id\": " + n(e.id) + ",");
                out.add(p2 + "\"walk_radius\": " + (e.walkRadius != null ? e.walkRadius : 0) + ",");
                out.add(p2 + "\"aggressive\": " + (e.aggressive != null ? e.aggressive : false) + ",");
                String dir = (e.direction != null ? e.direction.name() : "NORTH");
                out.add(p2 + "\"direction\": \"" + dir + "\"");
                break;
            }
            case DELETE_OBJECT:
                break;
        }
        out.add(p1 + "}" + (trailingComma ? "," : ""));
        return out;
    }

    private static boolean needsMoreFields(SpawnEntry e) {
        return e.action == ActionType.SPAWN_OBJECT || e.action == ActionType.SPAWN_OVER_OBJECT || e.action == ActionType.SPAWN_NPC;
    }

    private static String n(Integer v) {
        return v == null ? "0" : String.valueOf(v);
    }

    private void writeAll() {
        try (BufferedWriter bw = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(path), StandardCharsets.UTF_8))) {
            for (String ln : allLines) {
                bw.write(ln); bw.newLine();
            }
        } catch (Exception ex) {
            Ui.error("Failed to save JSON: " + ex.getMessage());
        }
    }

    public void save(SpawnProject project) {
        try (Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(path), StandardCharsets.UTF_8))) {
            JsonArray arr = new JsonArray();
            for (SpawnEntry e : project.getEntries()) {
                JsonObject o = new JsonObject();
                o.addProperty("action", e.action.wire);
                JsonObject t = new JsonObject();
                t.addProperty("x", e.tile.x);
                t.addProperty("y", e.tile.y);
                t.addProperty("z", e.tile.z);
                o.add("tile", t);
                switch (e.action) {
                    case SPAWN_OBJECT:
                    case SPAWN_OVER_OBJECT:
                        o.addProperty("id", e.id);
                        o.addProperty("type", e.type != null ? e.type : 10);
                        int rot = e.rotation != null ? normalizeRotSigned(e.rotation) : 0;
                        o.addProperty("rotation", rot);
                        break;
                    case SPAWN_NPC:
                        o.addProperty("id", e.id);
                        o.addProperty("walk_radius", e.walkRadius != null ? e.walkRadius : 0);
                        o.addProperty("aggressive", e.aggressive != null ? e.aggressive : false);
                        o.addProperty("direction", e.direction != null ? e.direction.name() : "NORTH");
                        break;
                    case DELETE_OBJECT:
                        break;
                }
                arr.add(o);
            }
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            gson.toJson(arr, writer);
            writer.flush();
        } catch (Exception ex) {
            Ui.error("Failed to save JSON: " + ex.getMessage());
        }
    }

    private static int normalizeRotSigned(int raw) {
        return raw % 4;
    }

    private static Integer getInt(JsonObject o, String k, Integer def) {
        if (o == null || !o.has(k)) return def;
        try {
            return o.get(k).getAsInt();
        } catch (Exception e) {
            return def;
        }
    }
    private static String getString(JsonObject o, String k, String def) {
        if (o == null || !o.has(k)) return def;
        try {
            return o.get(k).getAsString();
        } catch (Exception e) {
            return def;
        }
    }
    private static boolean getBool(JsonObject o, String k, boolean def) {
        if (o == null || !o.has(k)) return def;
        try {
            return o.get(k).getAsBoolean();
        } catch (Exception e) {
            return def;
        }
    }
}
