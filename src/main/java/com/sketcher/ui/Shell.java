package com.sketcher.ui;

import com.sketcher.ui.common.Refreshable;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/** The app's navigation shell: a sidebar plus a center area that swaps between views. */
public class Shell extends BorderPane {
    private final Nav nav = new Nav();
    private final StackPane content = new StackPane();
    private final Map<String, Supplier<Region>> factories = new LinkedHashMap<>();
    private final Map<String, Region> cache = new LinkedHashMap<>();

    public Shell() {
        getStyleClass().add("shell");
        setLeft(nav);
        setCenter(content);
    }

    public void registerView(String key, String label, Supplier<Region> factory) {
        factories.put(key, factory);
        nav.addItem(key, label, this::show);
    }

    /** Same as registerView, but pinned below the spacer at the bottom of the sidebar (e.g. Settings, grouped with Help). */
    public void registerFooterView(String key, String label, Supplier<Region> factory) {
        factories.put(key, factory);
        nav.addFooterItem(key, label, this::show);
    }

    /** Registers a view reachable only by calling show() directly (e.g. from a button elsewhere), with no sidebar entry. */
    public void registerHiddenView(String key, Supplier<Region> factory) {
        factories.put(key, factory);
    }

    /** Adds an action button pinned to the bottom of the sidebar (e.g. Help) that doesn't swap the center content. */
    public void registerFooterAction(String label, Runnable action) {
        nav.addFooterItem(label, action);
    }

    public void show(String key) {
        Region view = cache.computeIfAbsent(key, k -> factories.get(k).get());
        content.getChildren().setAll(view);
        nav.select(key);
        if (view instanceof Refreshable refreshable) {
            refreshable.onShown();
        }
    }
}
