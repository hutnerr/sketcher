package com.sketcher.ui.home;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * One launcher card on the home screen (PLAN.md 4.6): a saved preset or the
 * "Quick start" shortcut. Starting is an explicit button rather than a
 * whole-card click handler, so it never fights with the pin/delete buttons
 * for the same mouse click.
 */
public class PresetCard extends VBox {
    private static final double CARD_WIDTH = 260;
    private static final double CARD_HEIGHT = 190;

    public PresetCard(String name, String summary, boolean pinned, Runnable onStart,
                       Runnable onTogglePin, Runnable onEdit, Runnable onDelete) {
        getStyleClass().add("preset-card");
        setPrefWidth(CARD_WIDTH);
        setMinWidth(CARD_WIDTH);
        // Fixed, not content-derived, so every card in the row reports the same
        // preferred size to the FlowPane — names/summaries of different lengths
        // would otherwise make cards different heights. The summary label's
        // vgrow then soaks up whatever slack that leaves, pinning the button(s)
        // to the bottom edge consistently.
        setPrefHeight(CARD_HEIGHT);
        setMinHeight(CARD_HEIGHT);
        setSpacing(8);
        setPadding(new Insets(16));

        Label nameLabel = new Label(name);
        nameLabel.getStyleClass().add("preset-card-name");
        nameLabel.setWrapText(true);

        Label summaryLabel = new Label(summary);
        summaryLabel.getStyleClass().add("preset-card-summary");
        summaryLabel.setWrapText(true);
        // A Label's own maxHeight defaults to its preferred height, which caps
        // vgrow at nothing to actually stretch into — without this, the VBox's
        // leftover space (from forcing a uniform card height above) goes
        // unclaimed at the bottom instead of pushing the button(s) down to it.
        summaryLabel.setMaxHeight(Double.MAX_VALUE);
        VBox.setVgrow(summaryLabel, Priority.ALWAYS);

        Button startButton = new Button("Start practice");
        startButton.getStyleClass().add("primary-button");
        startButton.setOnAction(e -> onStart.run());
        startButton.setMaxWidth(Double.MAX_VALUE);

        getChildren().addAll(nameLabel, summaryLabel, startButton);

        if (onTogglePin != null || onEdit != null || onDelete != null) {
            HBox actions = new HBox(6);
            actions.setAlignment(Pos.CENTER_LEFT);
            if (onTogglePin != null) {
                ToggleButton pinButton = new ToggleButton(pinned ? "Unpin" : "Pin");
                pinButton.setSelected(pinned);
                pinButton.setOnAction(e -> onTogglePin.run());
                spanWidth(pinButton);
                actions.getChildren().add(pinButton);
            }
            if (onEdit != null) {
                Button editButton = new Button("Edit");
                editButton.setOnAction(e -> onEdit.run());
                spanWidth(editButton);
                actions.getChildren().add(editButton);
            }
            if (onDelete != null) {
                Button deleteButton = new Button("Delete");
                deleteButton.getStyleClass().add("danger-button");
                deleteButton.setOnAction(e -> onDelete.run());
                spanWidth(deleteButton);
                actions.getChildren().add(deleteButton);
            }
            getChildren().add(actions);
        }
    }

    /** Makes a button in the actions HBox claim an even share of the row instead of just its own text width. */
    private void spanWidth(javafx.scene.layout.Region button) {
        button.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(button, Priority.ALWAYS);
    }
}
