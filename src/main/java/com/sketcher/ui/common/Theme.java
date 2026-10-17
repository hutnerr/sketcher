package com.sketcher.ui.common;

import javafx.scene.Scene;
import javafx.scene.control.DialogPane;

/**
 * Applies the light/dark stylesheet pair (PLAN.md 4.9) to a Scene. Dark is
 * a color-only overlay on top of the base stylesheet — layout, padding, and
 * font rules live once in style.css, and style-dark.css only repaints them.
 *
 * Alert/Dialog/TextInputDialog/ChoiceDialog each open their own top-level
 * window with their own Scene, so they never automatically inherit the main
 * window's stylesheets. {@link #style(DialogPane)} must be called on every
 * one of those so they stay visually consistent with the rest of the app.
 */
public final class Theme {
    public static final String LIGHT = "light";
    public static final String DARK = "dark";

    private static final String BASE_CSS = "/style.css";
    private static final String DARK_CSS = "/style-dark.css";

    private static String current = LIGHT;

    private Theme() {
    }

    public static void apply(Scene scene, String theme) {
        current = theme;
        scene.getStylesheets().setAll(stylesheets(theme));
    }

    /** Styles a dialog/alert's pane to match the app's current theme (see class doc). */
    public static void style(DialogPane pane) {
        pane.getStylesheets().setAll(stylesheets(current));
    }

    private static java.util.List<String> stylesheets(String theme) {
        java.util.List<String> sheets = new java.util.ArrayList<>();
        var base = Theme.class.getResource(BASE_CSS);
        if (base != null) sheets.add(base.toExternalForm());
        if (DARK.equals(theme)) {
            var dark = Theme.class.getResource(DARK_CSS);
            if (dark != null) sheets.add(dark.toExternalForm());
        }
        return sheets;
    }
}
