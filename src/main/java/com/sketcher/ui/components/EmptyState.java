package com.sketcher.ui.components;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/** The centered "nothing here yet" message shown in place of an empty list, grid, or table. */
public class EmptyState extends VBox {

    public EmptyState(String title, String message, Node... actions) {
        getStyleClass().add("empty-state");
        setAlignment(Pos.CENTER);
        setSpacing(8);
        setPadding(new Insets(48, 24, 48, 24));

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("empty-state-title");
        getChildren().add(titleLabel);
        if (message != null) {
            Label messageLabel = new Label(message);
            messageLabel.getStyleClass().add("empty-state-message");
            messageLabel.setWrapText(true);
            messageLabel.setMaxWidth(440);
            messageLabel.setAlignment(Pos.CENTER);
            messageLabel.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
            getChildren().add(messageLabel);
        }
        if (actions.length > 0) {
            VBox.setMargin(actions[0], new Insets(8, 0, 0, 0));
            getChildren().addAll(actions);
        }
    }
}
