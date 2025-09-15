package com.spawneditor.io;

import com.google.gson.*;
import com.spawneditor.util.Ui;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class ObjectIndex {
    private final String path;
    private Map<Integer, String> idToName = new LinkedHashMap<>();

    public ObjectIndex(String path) {
        this.path = path;
    }

    public void load() {
        idToName.clear();
        File f = new File(path);
        if (!f.exists()) {
            Ui.warn("Object list not found: " + path);
            return;
        }
        try {
            char first = firstNonWhitespaceChar(f);
            if (first == '[' || first == '{' || path.toLowerCase().endsWith(".json")) {
                loadJson(f);
            } else {
                loadTxt(f);
            }
        } catch (Exception e) {
            Ui.error("Failed to load object list: " + e.getMessage());
        }
    }

    private void loadTxt(File f) throws IOException {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                int dash = line.indexOf(" - ");
                if (dash < 0) continue;
                String idStr = line.substring(0, dash).trim();
                String name = line.substring(dash + 3).trim();
                try {
                    int id = Integer.parseInt(idStr);
                    idToName.put(id, name);
                } catch (NumberFormatException ignore) {}
            }
        }
    }

    private void loadJson(File f) throws IOException {
        try (Reader r = new BufferedReader(new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
            JsonElement root = JsonParser.parseReader(r);
            if (!root.isJsonArray()) {
                Ui.warn("Objects JSON root is not an array: " + path);
                return;
            }
            JsonArray arr = root.getAsJsonArray();
            for (JsonElement el : arr) {
                if (!el.isJsonObject()) continue;
                JsonObject o = el.getAsJsonObject();
                if (!o.has("id")) continue;
                int id = safeInt(o.get("id"), -1);
                String name = safeString(o.get("name"), "");
                if (id >= 0) idToName.put(id, name != null ? name : "");
            }
        }
    }

    private static int safeInt(JsonElement el, int def) {
        try {
            return el == null ? def : el.getAsInt();
        } catch (Exception e) {
            return def;
        }
    }

    private static String safeString(JsonElement el, String def) {
        try {
            return el == null ? def : el.getAsString();
        } catch (Exception e) {
            return def;
        }
    }

    private static char firstNonWhitespaceChar(File f) throws IOException {
        try (InputStream in = new FileInputStream(f)) {
            int b;
            while ((b = in.read()) != -1) {
                char c = (char) b;
                if (!Character.isWhitespace(c)) return c;
            }
            return '\0';
        }
    }

    public String nameFor(int id) {
        String n = idToName.get(id);
        return n != null ? n : Integer.toString(id);
    }

    public Map<Integer, String> asMap() {
        return Collections.unmodifiableMap(idToName);
    }
}
