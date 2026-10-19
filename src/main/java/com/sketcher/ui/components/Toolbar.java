package com.sketcher.ui.components;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;

/**
 * A horizontal bar of controls with a left group, an optional centered group,
 * and a right group. {@link #divider()} draws a thin vertical rule between
 * clusters of related tools. Used anywhere a screen needs a row of actions
 * docked above or below its content (gallery, practice, image detail).
 */
public class Toolbar extends HBox {
    private final HBox left = new HBox(8);
    private final HBox center = new HBox(8);
    private final HBox right = new HBox(8);

    public Toolbar() {
        getStyleClass().add("app-toolbar");
        setAlignment(Pos.CENTER_LEFT);
        setSpacing(12);
        setPadding(new Insets(12, 20, 12, 20));
        for (HBox group : new HBox[]{left, center, right}) {
            group.setAlignment(Pos.CENTER_LEFT);
        }
        getChildren().addAll(left, new Spacer(), center, new Spacer(), right);
    }

    public Toolbar left(Node... nodes) {
        left.getChildren().addAll(nodes);
        return this;
    }

    public Toolbar center(Node... nodes) {
        center.getChildren().addAll(nodes);
        return this;
    }

    public Toolbar right(Node... nodes) {
        right.getChildren().addAll(nodes);
        return this;
    }

    /** Transparent variant, for toolbars sitting directly on a page instead of docked to an edge. */
    public Toolbar plain() {
        getStyleClass().add("app-toolbar-plain");
        setPadding(Insets.EMPTY);
        return this;
    }

    public static Region divider() {
        Region rule = new Region();
        rule.getStyleClass().add("toolbar-divider");
        return rule;
    }
}
