package com.sketcher.config;

import java.util.ArrayList;
import java.util.List;

public class AppConfig {
    public String libraryRoot;
    public List<String> recentLibraries = new ArrayList<>();
    public double windowWidth = 1100;
    public double windowHeight = 720;
    /** "dark" (default) or "light" — see com.sketcher.ui.common.Theme (PLAN.md 4.9). */
    public String theme = "dark";

    public void noteLibraryUsed(String path) {
        libraryRoot = path;
        recentLibraries.remove(path);
        recentLibraries.add(0, path);
        while (recentLibraries.size() > 8) {
            recentLibraries.remove(recentLibraries.size() - 1);
        }
    }
}
