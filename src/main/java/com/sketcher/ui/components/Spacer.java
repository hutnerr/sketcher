package com.sketcher.ui.components;

import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** An empty region that soaks up spare space in an HBox or VBox, pushing its siblings apart. */
public class Spacer extends Region {
    public Spacer() {
        HBox.setHgrow(this, Priority.ALWAYS);
        VBox.setVgrow(this, Priority.ALWAYS);
    }
}
