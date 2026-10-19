package com.sketcher.ui.components;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * The scaffold every top-level screen sits in: a header (title, optional
 * subtitle, right-aligned actions) over a body, with the same margins
 * everywhere. Two body modes:
 * <ul>
 *   <li>{@link #setBody(Node)} — the node fills the remaining space (tables,
 *       trees, image grids that scroll themselves).</li>
 *   <li>{@link #setScrollingBody(Node...)} — stacked content in a vertically
 *       scrolling column, capped at a readable width (forms, card lists).</li>
 * </ul>
 */
public class Page extends BorderPane {
    public static final double GUTTER = 40;
    private static final double READABLE_WIDTH = 960;

    private final Label title = new Label();
    private final Label subtitle = new Label();
    private final HBox actions = new HBox(10);

    public Page(String titleText) {
        getStyleClass().add("page");

        title.setText(titleText);
        title.getStyleClass().add("page-title");
        subtitle.getStyleClass().add("page-subtitle");
        subtitle.setWrapText(true);
        setSubtitle(null);

        VBox titles = new VBox(4, title, subtitle);
        actions.setAlignment(Pos.CENTER_RIGHT);
        HBox header = new HBox(16, titles, new Spacer(), actions);
        header.setAlignment(Pos.CENTER_LEFT);
        header.getStyleClass().add("page-header");
        header.setPadding(new Insets(36, GUTTER, 24, GUTTER));
        setTop(header);
    }

    public void setTitle(String text) {
        title.setText(text);
    }

    /** Null or blank hides the subtitle line entirely. */
    public void setSubtitle(String text) {
        boolean show = text != null && !text.isBlank();
        subtitle.setText(show ? text : "");
        subtitle.setVisible(show);
        subtitle.setManaged(show);
    }

    public void setActions(Node... nodes) {
        actions.getChildren().setAll(nodes);
    }

    /** Body that fills the remaining space, inset by the page gutter. */
    public void setBody(Node body) {
        BorderPane.setMargin(body, new Insets(0, GUTTER, GUTTER, GUTTER));
        setCenter(body);
    }

    /** Body as a scrolling column of stacked sections. Returns the column so callers can add to it later. */
    public VBox setScrollingBody(Node... sections) {
        VBox column = new VBox(20, sections);
        column.setMaxWidth(READABLE_WIDTH);
        column.setPadding(new Insets(0, GUTTER, GUTTER, GUTTER));
        ScrollPane scroll = new ScrollPane(column);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("page-scroll");
        setCenter(scroll);
        return column;
    }
}
