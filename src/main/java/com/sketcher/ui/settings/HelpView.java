package com.sketcher.ui.settings;

import com.sketcher.ui.common.AsyncTask;
import com.sketcher.ui.practice.PracticeView;
import javafx.geometry.Insets;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.VBox;

import java.awt.Desktop;
import java.net.URI;
import java.util.List;
import java.util.Map;

/**
 * Getting-started text, the tag/filter semantics, and the shortcut table
 * (PLAN.md 4.9). The shortcut table is read straight from
 * PracticeView.SHORTCUTS so the two can never drift apart.
 */
public class HelpView extends ScrollPane {

    public HelpView() {
        getStyleClass().add("help-view");
        setFitToWidth(true);

        VBox content = new VBox(18);
        content.setPadding(new Insets(24));
        content.getChildren().addAll(
                section("Getting started",
                        "Sketcher keeps a library of images in a folder you choose, tagged however you like. "
                                + "Import some images from the Import screen, tag them from the Gallery or Tags "
                                + "screen, then start a practice session — either ad hoc from the Practice screen, "
                                + "or with one click from a saved preset on the Home screen."),
                section("Tags and filters",
                        "Tags form a hierarchy: selecting a tag also matches its sub-tags by default (toggle "
                                + "\"+sub-tags\" off to match only that exact tag). Filter rows combine left to "
                                + "right with AND or OR, and each row can be inverted with the NOT toggle — for "
                                + "example, a tag filter on \"figure\" combined with NOT \"nude\" shows clothed "
                                + "figure references only."),
                section("Keyboard shortcuts (during practice)", null),
                buildShortcutTable(),
                buildReferenceLinks());

        setContent(content);
    }

    /**
     * "Links to online alternatives with images supplied already" (README
     * want-list) — useful on day one when the library is still empty. URLs
     * are placeholders until real reference sites are picked.
     */
    private VBox buildReferenceLinks() {
        Label heading = new Label("Need images to start with?");
        heading.getStyleClass().add("help-heading");
        Label body = new Label("These will point at real reference-image sites later — for now they're placeholders.");
        body.setWrapText(true);

        VBox box = new VBox(6, heading, body);
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
        headingLabel.getStyleClass().add("help-heading");
        VBox box = new VBox(6, headingLabel);
        if (body != null) {
            Label bodyLabel = new Label(body);
            bodyLabel.setWrapText(true);
            box.getChildren().add(bodyLabel);
        }
        return box;
    }

    private TableView<Map.Entry<String, String>> buildShortcutTable() {
        TableView<Map.Entry<String, String>> table = new TableView<>();
        table.setItems(javafx.collections.FXCollections.observableArrayList(PracticeView.SHORTCUTS.entrySet()));
        table.setMaxHeight(PracticeView.SHORTCUTS.size() * 32 + 30);

        TableColumn<Map.Entry<String, String>, String> keyCol = new TableColumn<>("Key");
        keyCol.setCellValueFactory(cd -> new javafx.beans.property.ReadOnlyStringWrapper(cd.getValue().getKey()));
        keyCol.setPrefWidth(140);

        TableColumn<Map.Entry<String, String>, String> actionCol = new TableColumn<>("Action");
        actionCol.setCellValueFactory(cd -> new javafx.beans.property.ReadOnlyStringWrapper(cd.getValue().getValue()));
        actionCol.setPrefWidth(300);

        table.getColumns().addAll(List.of(keyCol, actionCol));
        return table;
    }
}
