package com.sketcher.ui.components;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * A raised surface that groups related content — form sections, list panes,
 * launcher tiles. An optional header carries a title, a muted description,
 * and right-aligned header actions; everything else goes in via
 * {@link #add(Node...)} and stacks with uniform spacing.
 */
public class Card extends VBox {
    private HBox headerActions;

    public Card() {
        getStyleClass().add("card");
        setSpacing(16);
        setPadding(new Insets(24));
    }

    public Card(String title) {
        this(title, null);
    }

    public Card(String title, String description) {
        this();
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("card-title");
        VBox titles = new VBox(4, titleLabel);
        if (description != null) {
            Label descriptionLabel = new Label(description);
            descriptionLabel.getStyleClass().add("card-description");
            descriptionLabel.setWrapText(true);
            titles.getChildren().add(descriptionLabel);
        }
        headerActions = new HBox(8);
        headerActions.setAlignment(Pos.CENTER_RIGHT);
        HBox header = new HBox(12, titles, new Spacer(), headerActions);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("card-header");
        getChildren().add(header);
    }

    public Card add(Node... nodes) {
        getChildren().addAll(nodes);
        return this;
    }

    /** Puts nodes on the right side of the title row. Only for cards built with a title. */
    public Card headerActions(Node... nodes) {
        if (headerActions == null) throw new IllegalStateException("Card has no header");
        headerActions.getChildren().setAll(nodes);
        return this;
    }

    /** Hover lift + pointer cursor, for cards that act as launchers. */
    public Card interactive() {
        getStyleClass().add("card-interactive");
        return this;
    }

    /** Accent-tinted surface, for the one card on a screen that should stand out. */
    public Card accent() {
        getStyleClass().add("card-accent");
        return this;
    }

    /** Zero inner padding, for cards wrapping a list/table/tree that draws its own rows edge to edge. */
    public Card flush() {
        setPadding(Insets.EMPTY);
        getStyleClass().add("card-flush");
        return this;
    }
}
