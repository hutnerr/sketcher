package com.sketcher.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ConfigStoreTest {

    @Test
    void freshConfigDefaultsToDarkTheme(@TempDir Path tempDir) {
        ConfigStore store = new ConfigStore(tempDir.resolve("config.json"));

        AppConfig config = store.load();

        assertEquals("dark", config.theme);
    }

    /**
     * Gson's default allocator bypasses AppConfig's constructor, so a field
     * added after some users already had a config.json on disk (like
     * "theme") comes back null from a plain Gson parse instead of picking up
     * its source-level default. ConfigStore.load() must patch that back in.
     */
    @Test
    void loadingAnOlderConfigFileMissingNewerFieldsFillsInDefaults(@TempDir Path tempDir) throws IOException {
        Path configFile = tempDir.resolve("config.json");
        Files.writeString(configFile, "{\"libraryRoot\":\"/some/library\",\"windowWidth\":1200,\"windowHeight\":800}");
        ConfigStore store = new ConfigStore(configFile);

        AppConfig config = store.load();

        assertEquals("/some/library", config.libraryRoot);
        assertEquals("dark", config.theme);
        assertNotNull(config.recentLibraries);
    }
}
