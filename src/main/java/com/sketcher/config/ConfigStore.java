package com.sketcher.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loads and saves the app's own configuration (library location, window
 * geometry). Lives outside the library root since it must be readable before
 * any library is chosen.
 */
public class ConfigStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path configFile;

    public ConfigStore() {
        this(configDir().resolve("config.json"));
    }

    /** Exposed for tests, so they can point at a temp file instead of the real per-OS config dir. */
    public ConfigStore(Path configFile) {
        this.configFile = configFile;
    }

    private static Path configDir() {
        String os = System.getProperty("os.name", "").toLowerCase();
        String home = System.getProperty("user.home");
        if (os.contains("win")) {
            String appData = System.getenv("APPDATA");
            Path base = appData != null ? Path.of(appData) : Path.of(home, "AppData", "Roaming");
            return base.resolve("sketcher");
        } else if (os.contains("mac")) {
            return Path.of(home, "Library", "Application Support", "sketcher");
        } else {
            String xdg = System.getenv("XDG_CONFIG_HOME");
            Path base = xdg != null ? Path.of(xdg) : Path.of(home, ".config");
            return base.resolve("sketcher");
        }
    }

    public AppConfig load() {
        if (!Files.isRegularFile(configFile)) {
            return new AppConfig();
        }
        try (Reader reader = Files.newBufferedReader(configFile, StandardCharsets.UTF_8)) {
            AppConfig config = GSON.fromJson(reader, AppConfig.class);
            return config != null ? patchMissingFields(config) : new AppConfig();
        } catch (IOException e) {
            return new AppConfig();
        }
    }

    /**
     * Gson instantiates via an unsafe allocator, not AppConfig's constructor,
     * so a field absent from an older config.json (e.g. "theme", added after
     * some configs were already saved) comes back null instead of picking up
     * its source-level default. Patch those back in here, once, on load.
     */
    private static AppConfig patchMissingFields(AppConfig config) {
        AppConfig defaults = new AppConfig();
        if (config.theme == null) config.theme = defaults.theme;
        if (config.recentLibraries == null) config.recentLibraries = defaults.recentLibraries;
        return config;
    }

    public void save(AppConfig config) {
        try {
            Files.createDirectories(configFile.getParent());
            try (Writer writer = Files.newBufferedWriter(configFile, StandardCharsets.UTF_8)) {
                GSON.toJson(config, writer);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to save config", e);
        }
    }
}
