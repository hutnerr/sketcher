package com.sketcher.ui.components;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;

/** A small rounded pill for tags, filter terms, and status badges, optionally with a remove "✕". */
public class Chip extends HBox {

    public Chip(String text) {
        this(text, null);
    }

    public Chip(String text, Runnable onRemove) {
        getStyleClass().add("chip");
        setAlignment(Pos.CENTER_LEFT);
        setSpacing(6);
        setMaxWidth(USE_PREF_SIZE);
        Label label = new Label(text);
        label.getStyleClass().add("chip-label");
        getChildren().add(label);
        if (onRemove != null) {
            Button remove = new Button("✕");
            remove.getStyleClass().add("chip-remove");
            remove.setOnAction(e -> onRemove.run());
            getChildren().add(remove);
        }
    }

    /** Neutral gray styling instead of the accent tint. Returns this for chaining. */
    public Chip neutral() {
        getStyleClass().add("chip-neutral");
        return this;
    }
}
