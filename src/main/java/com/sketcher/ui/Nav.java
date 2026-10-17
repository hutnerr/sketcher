package com.sketcher.ui;

import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.VBox;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

/** The app's left-hand navigation sidebar: one toggle button per top-level section. */
public class Nav extends VBox {
    private final ToggleGroup group = new ToggleGroup();
    private final Map<String, ToggleButton> buttons = new LinkedHashMap<>();

    public Nav() {
        getStyleClass().add("nav");
        setSpacing(3);
        setPadding(new Insets(16, 10, 16, 10));

        Label title = new Label("Sketcher");
        title.getStyleClass().add("nav-title");
        getChildren().add(title);
    }

    public void addItem(String key, String label, Consumer<String> onSelect) {
        ToggleButton button = new ToggleButton(label);
        button.setToggleGroup(group);
        button.setMaxWidth(Double.MAX_VALUE);
        button.getStyleClass().add("nav-item");
        button.setOnAction(e -> onSelect.accept(key));
        buttons.put(key, button);
        getChildren().add(button);
    }

    public void select(String key) {
        ToggleButton button = buttons.get(key);
        if (button != null) {
            group.selectToggle(button);
        }
    }
}
