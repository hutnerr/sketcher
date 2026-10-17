package com.sketcher.ui;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The app's left-hand navigation sidebar: one toggle button per top-level
 * section, plus footer actions (e.g. Help) pinned to the bottom below a
 * growing spacer.
 */
public class Nav extends VBox {
    private final ToggleGroup group = new ToggleGroup();
    private final Map<String, ToggleButton> buttons = new LinkedHashMap<>();
    private final Region spacer = new Region();

    public Nav() {
        getStyleClass().add("nav");
        setSpacing(3);
        setPadding(new Insets(16, 10, 16, 10));

        Label title = new Label("Sketcher");
        title.getStyleClass().add("nav-title");
        getChildren().add(title);

        VBox.setVgrow(spacer, Priority.ALWAYS);
        getChildren().add(spacer);
    }

    public void addItem(String key, String label, Consumer<String> onSelect) {
        ToggleButton button = new ToggleButton(label);
        button.setToggleGroup(group);
        button.setMaxWidth(Double.MAX_VALUE);
        button.getStyleClass().add("nav-item");
        button.setOnAction(e -> onSelect.accept(key));
        buttons.put(key, button);
        getChildren().add(getChildren().indexOf(spacer), button);
    }

    /** Adds a page, same as addItem, but pinned below the spacer (e.g. Settings, grouped with Help). */
    public void addFooterItem(String key, String label, Consumer<String> onSelect) {
        ToggleButton button = new ToggleButton(label);
        button.setToggleGroup(group);
        button.setMaxWidth(Double.MAX_VALUE);
        button.getStyleClass().add("nav-item");
        button.setOnAction(e -> onSelect.accept(key));
        buttons.put(key, button);
        getChildren().add(button);
    }

    /** Adds a plain (non-toggling) action pinned below the spacer, e.g. a Help button. */
    public void addFooterItem(String label, Runnable onSelect) {
        Button button = new Button(label);
        button.setMaxWidth(Double.MAX_VALUE);
        button.getStyleClass().addAll("nav-item", "nav-footer-item");
        button.setOnAction(e -> onSelect.run());
        getChildren().add(button);
    }

    public void select(String key) {
        ToggleButton button = buttons.get(key);
        if (button != null) {
            group.selectToggle(button);
        }
    }
}
