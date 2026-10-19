package com.sketcher.ui.components;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * One labeled line of a form: a fixed-width label column on the left and the
 * control(s) on the right, with an optional muted hint underneath the
 * controls. The fixed label width is what lines every form in the app up on
 * the same vertical edge.
 */
public class FormRow extends HBox {
    private static final double LABEL_WIDTH = 190;

    private final HBox controls = new HBox(12);
    private final VBox right = new VBox(6, controls);

    public FormRow(String label, Node... controlNodes) {
        getStyleClass().add("form-row");
        setSpacing(16);
        setAlignment(Pos.TOP_LEFT);

        Label labelNode = new Label(label);
        labelNode.getStyleClass().add("form-label");
        labelNode.setMinWidth(LABEL_WIDTH);
        labelNode.setPrefWidth(LABEL_WIDTH);
        labelNode.setWrapText(true);
        // Nudge the label down so its baseline sits level with a 44px control beside it.
        labelNode.setPadding(new Insets(10, 0, 0, 0));

        controls.setAlignment(Pos.CENTER_LEFT);
        controls.getChildren().addAll(controlNodes);
        HBox.setHgrow(right, javafx.scene.layout.Priority.ALWAYS);
        getChildren().addAll(labelNode, right);
    }

    public FormRow hint(String text) {
        Label hint = new Label(text);
        hint.getStyleClass().add("hint-label");
        hint.setWrapText(true);
        right.getChildren().add(hint);
        return this;
    }

    /** Adds a full-width node under the controls (e.g. a nested filter builder). */
    public FormRow below(Node node) {
        right.getChildren().add(node);
        return this;
    }

    /** Shows/hides the whole row and collapses its space when hidden. */
    public void setShown(boolean shown) {
        setVisible(shown);
        setManaged(shown);
    }
}
