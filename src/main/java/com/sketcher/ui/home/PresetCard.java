package com.sketcher.ui.home;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * One launcher card on the home screen (PLAN.md 4.6): a saved preset or the
 * "Quick start" shortcut. Starting is an explicit button rather than a
 * whole-card click handler, so it never fights with the pin/delete buttons
 * for the same mouse click.
 */
public class PresetCard extends VBox {

    public PresetCard(String name, String summary, boolean pinned, Runnable onStart,
                       Runnable onTogglePin, Runnable onDelete) {
        getStyleClass().add("preset-card");
        setPrefWidth(220);
        setSpacing(8);
        setPadding(new Insets(16));

        Label nameLabel = new Label(name);
        nameLabel.getStyleClass().add("preset-card-name");
        nameLabel.setWrapText(true);

        Label summaryLabel = new Label(summary);
        summaryLabel.getStyleClass().add("preset-card-summary");
        summaryLabel.setWrapText(true);
        VBox.setVgrow(summaryLabel, Priority.ALWAYS);

        Button startButton = new Button("Start practice");
        startButton.getStyleClass().add("primary-button");
        startButton.setOnAction(e -> onStart.run());
        startButton.setMaxWidth(Double.MAX_VALUE);

        getChildren().addAll(nameLabel, summaryLabel, startButton);

        if (onTogglePin != null || onDelete != null) {
            HBox actions = new HBox(6);
            actions.setAlignment(Pos.CENTER_LEFT);
            if (onTogglePin != null) {
                ToggleButton pinButton = new ToggleButton(pinned ? "Unpin" : "Pin");
                pinButton.setSelected(pinned);
                pinButton.setOnAction(e -> onTogglePin.run());
                actions.getChildren().add(pinButton);
            }
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            actions.getChildren().add(spacer);
            if (onDelete != null) {
                Button deleteButton = new Button("Delete");
                deleteButton.getStyleClass().add("danger-button");
                deleteButton.setOnAction(e -> onDelete.run());
                actions.getChildren().add(deleteButton);
            }
            getChildren().add(actions);
        }
    }
}
