package com.sketcher.ui.tags;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;

/** A small rounded label used to display a tag, optionally with a remove button. */
public class TagChip extends HBox {

    public TagChip(String text) {
        this(text, null);
    }

    public TagChip(String text, Runnable onRemove) {
        getStyleClass().add("tag-chip");
        setSpacing(5);
        setPadding(new Insets(3, 8, 3, 8));
        Label label = new Label(text);
        getChildren().add(label);
        if (onRemove != null) {
            Button remove = new Button("x");
            remove.getStyleClass().add("tag-chip-remove");
            remove.setOnAction(e -> onRemove.run());
            getChildren().add(remove);
        }
    }
}
