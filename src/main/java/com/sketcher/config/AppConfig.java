package com.sketcher.config;

import java.util.ArrayList;
import java.util.List;

public class AppConfig {
    public String libraryRoot;
    public List<String> recentLibraries = new ArrayList<>();
    public double windowWidth = 1360;
    public double windowHeight = 880;
    /** "dark" (default) or "light" — see com.sketcher.ui.common.Theme (PLAN.md 4.9). */
    public String theme = "dark";
    /** Leave files already in the library, or repeated within the batch, out of the import staging table. */
    public boolean skipDuplicatesOnImport = false;

    /** Puts every setting back to a fresh install's value, in place, so anyone holding this object sees the reset. */
    public void resetToDefaults() {
        AppConfig defaults = new AppConfig();
        libraryRoot = defaults.libraryRoot;
        recentLibraries = defaults.recentLibraries;
        windowWidth = defaults.windowWidth;
        windowHeight = defaults.windowHeight;
        theme = defaults.theme;
        skipDuplicatesOnImport = defaults.skipDuplicatesOnImport;
    }

    public void noteLibraryUsed(String path) {
        libraryRoot = path;
        recentLibraries.remove(path);
        recentLibraries.add(0, path);
        while (recentLibraries.size() > 8) {
            recentLibraries.remove(recentLibraries.size() - 1);
        }
    }
}
