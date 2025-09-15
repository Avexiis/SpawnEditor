package com.spawneditor.io;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.*;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;

public final class PathsConfig {
    private PathsConfig() {}
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static File configFile() {
        File dir = jarDirectorySafe();
        return new File(dir, "spawneditor.config.json");
    }

    private static File jarDirectorySafe() {
        try {
            File self = new File(PathsConfig.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            File dir = self.isFile() ? self.getParentFile() : self;
            return (dir != null ? dir : new File("."));
        } catch (URISyntaxException e) {
            return new File(".");
        }
    }

    public static final class Model {
        public String spawnsJson;
        public String mapImage;
        public String objectsJson;
        public String npcsJson;
        public String dynamicNpcsTxt;
        public Integer rxMin;
        public Integer rxMax;
        public Integer ryMin;
        public Integer ryMax;
    }

    public static Model loadOrNull() {
        File f = configFile();
        if (!f.exists()) return null;
        try (Reader r = new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8)) {
            return GSON.fromJson(r, Model.class);
        } catch (Exception ignored) {
            return null;
        }
    }

    public static void saveFromCurrentPaths() {
        Model m = new Model();
        m.spawnsJson = Paths.SPAWNS_JSON;
        m.mapImage = Paths.MAP_IMAGE;
        m.objectsJson = Paths.OBJECTS_JSON;
        m.npcsJson = Paths.NPCS_JSON;
        m.dynamicNpcsTxt = Paths.DYNAMIC_NPCS_TXT;
        m.rxMin = Paths.RX_MIN;
        m.rxMax = Paths.RX_MAX;
        m.ryMin = Paths.RY_MIN;
        m.ryMax = Paths.RY_MAX;
        File f = configFile();
        try (Writer w = new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8)) {
            GSON.toJson(m, w);
        } catch (Exception ignored) {
        }
    }

    public static void applyToPaths(Model m) {
        if (m == null) return;
        if (m.spawnsJson != null) Paths.SPAWNS_JSON = m.spawnsJson;
        if (m.mapImage != null) Paths.MAP_IMAGE = m.mapImage;
        if (m.objectsJson != null) Paths.OBJECTS_JSON = m.objectsJson;
        if (m.npcsJson != null) Paths.NPCS_JSON = m.npcsJson;
        if (m.dynamicNpcsTxt != null) Paths.DYNAMIC_NPCS_TXT = m.dynamicNpcsTxt;
        if (m.rxMin != null) Paths.RX_MIN = m.rxMin;
        if (m.rxMax != null) Paths.RX_MAX = m.rxMax;
        if (m.ryMin != null) Paths.RY_MIN = m.ryMin;
        if (m.ryMax != null) Paths.RY_MAX = m.ryMax;
    }
}
