package com.sketcher.ui.settings;

import com.sketcher.ui.common.AsyncTask;
import com.sketcher.ui.practice.PracticeView;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.awt.Desktop;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Getting-started text, the tag/filter semantics, and the shortcut table
 * (PLAN.md 4.9). The shortcut table is read straight from
 * PracticeView.SHORTCUTS so the two can never drift apart.
 */
public class HelpView extends ScrollPane {

    public HelpView() {
        getStyleClass().add("help-view");
        getStyleClass().add("page-scroll");
        setFitToWidth(true);

        VBox content = new VBox(28);
        content.setPadding(new Insets(16, 20, 20, 20));
        content.getChildren().addAll(
                section("Getting started",
                        "Sketcher keeps a library of images in a folder you choose, tagged however you like. "
                                + "Import some images from the Import screen, tag them from the Gallery or Tags "
                                + "screen, then start a practice session — either ad hoc from the Practice screen, "
                                + "or with one click from a saved preset on the Home screen."),
                section("Tags and filters",
                        "Tags form a hierarchy: selecting a tag also matches its sub-tags by default (uncheck "
                                + "\"Include sub-tags\" to match only that exact tag). Filter rows combine top to "
                                + "bottom with And or Or, and each row can be inverted with its Exclude toggle — for "
                                + "example, a tag filter on \"figure\" combined with an excluded \"nude\" shows "
                                + "clothed figure references only."),
                buildShortcutList(),
                buildReferenceLinks());

        setContent(content);
    }

    /**
     * "Links to online alternatives with images supplied already" (README
     * want-list) — useful on day one when the library is still empty. URLs
     * are placeholders until real reference sites are picked.
     */
    private VBox buildReferenceLinks() {
        VBox box = section("Need images to start with?",
                "These will point at real reference-image sites later — for now they're placeholders.");
        for (String label : List.of("Reference site placeholder #1", "Reference site placeholder #2")) {
            box.getChildren().add(hyperlinkTo(label, "https://example.com/placeholder-reference"));
        }
        return box;
    }

    private Hyperlink hyperlinkTo(String label, String url) {
        Hyperlink link = new Hyperlink(label);
        link.setOnAction(e -> openInBrowser(url));
        return link;
    }

    /** Desktop.browse() blocks until the OS hands off to a browser — run it off the FX thread so it can't freeze the UI. */
    private void openInBrowser(String url) {
        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) return;
        AsyncTask.runIo(() -> {
            try {
                Desktop.getDesktop().browse(new URI(url));
            } catch (Exception ignored) {
                // best-effort: no system browser available, nothing useful to recover
            }
            return null;
        }, ignored -> {
        }, ignored -> {
        });
    }

    private VBox section(String heading, String body) {
        Label headingLabel = new Label(heading);
        headingLabel.getStyleClass().add("section-title");
        VBox box = new VBox(8, headingLabel);
        if (body != null) {
            Label bodyLabel = new Label(body);
            bodyLabel.getStyleClass().add("body-text");
            bodyLabel.setWrapText(true);
            box.getChildren().add(bodyLabel);
        }
        return box;
    }

    /** Keys rendered as keycaps in a two-column grid, sorted by key so the list reads the same every time. */
    private VBox buildShortcutList() {
        GridPane grid = new GridPane();
        grid.setHgap(20);
        grid.setVgap(10);
        int row = 0;
        for (Map.Entry<String, String> entry : new TreeMap<>(PracticeView.SHORTCUTS).entrySet()) {
            HBox keys = new HBox(6);
            keys.setAlignment(Pos.CENTER_LEFT);
            for (String key : entry.getKey().split(" / ")) {
                Label cap = new Label(key);
                cap.getStyleClass().add("kbd");
                keys.getChildren().add(cap);
            }
            keys.setMinWidth(140);
            Label action = new Label(entry.getValue());
            action.getStyleClass().add("body-text");
            grid.addRow(row++, keys, action);
        }
        VBox box = section("Keyboard shortcuts (during practice)", null);
        box.setSpacing(14);
        box.getChildren().add(grid);
        return box;
    }
}
