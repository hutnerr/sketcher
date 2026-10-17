package com.sketcher.ui.settings;

import com.sketcher.library.LibraryService;
import com.sketcher.model.ImageRecord;
import com.sketcher.ui.common.AsyncTask;
import com.sketcher.ui.common.Dialogs;
import com.sketcher.ui.common.Refreshable;
import com.sketcher.ui.gallery.GalleryActions;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Lists soft-deleted images with their deletion date (PLAN.md 4.7): restore
 * puts an image back wherever the gallery/practice queries will find it
 * again, permanent delete and "empty bin" are irreversible. Auto-purge after
 * N days (PLAN.md 4.9) runs on library open, in SketcherApp, not here.
 */
public class RecycleBinView extends VBox implements Refreshable {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());

    private final LibraryService library;
    private final VBox listBox = new VBox(8);
    private final Button emptyBinButton = new Button("Empty bin");
    private final Label countLabel = new Label();

    public RecycleBinView(LibraryService library) {
        this.library = library;
        getStyleClass().add("settings-view");
        setSpacing(16);
        setPadding(new Insets(24));

        Label heading = new Label("Recycle bin");
        heading.getStyleClass().add("home-title");
        emptyBinButton.setOnAction(e -> emptyBin());

        HBox headerRow = new HBox(16, countLabel, emptyBinButton);
        headerRow.setAlignment(Pos.CENTER_LEFT);

        ScrollPane scroll = new ScrollPane(listBox);
        scroll.setFitToWidth(true);
        VBox.setVgrow(scroll, javafx.scene.layout.Priority.ALWAYS);

        getChildren().addAll(heading, headerRow, scroll);
        refresh();
    }

    @Override
    public void onShown() {
        refresh();
    }

    private void refresh() {
        AsyncTask.runDb(
                () -> library.images().listDeleted(),
                this::render,
                err -> Dialogs.error("Could not load recycle bin", err.getMessage()));
    }

    private void render(List<ImageRecord> deleted) {
        countLabel.setText(deleted.size() + " deleted image(s)");
        emptyBinButton.setDisable(deleted.isEmpty());
        listBox.getChildren().clear();
        for (ImageRecord record : deleted) {
            listBox.getChildren().add(buildRow(record));
        }
        if (deleted.isEmpty()) {
            Label empty = new Label("Nothing in the recycle bin.");
            empty.getStyleClass().add("hint-label");
            listBox.getChildren().add(empty);
        }
    }

    private HBox buildRow(ImageRecord record) {
        ImageView thumb = new ImageView();
        thumb.setFitWidth(64);
        thumb.setFitHeight(64);
        thumb.setPreserveRatio(true);
        Image image = library.thumbnails().load(library.ref(), record.hash());
        if (image != null) thumb.setImage(image);

        Label name = new Label(record.originalName() != null ? record.originalName() : record.hash());
        String deletedAt = record.deletedAt() == null ? "" : DATE_FORMAT.format(Instant.ofEpochMilli(record.deletedAt()));
        Label deletedLabel = new Label("Deleted " + deletedAt);
        deletedLabel.getStyleClass().add("hint-label");
        VBox info = new VBox(2, name, deletedLabel);

        Button restoreButton = new Button("Restore");
        restoreButton.setOnAction(e -> restore(record));
        Button deleteButton = new Button("Delete permanently");
        deleteButton.setOnAction(e -> permanentlyDelete(record));

        HBox row = new HBox(12, thumb, info, restoreButton, deleteButton);
        row.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(info, javafx.scene.layout.Priority.ALWAYS);
        return row;
    }

    private void restore(ImageRecord record) {
        if (!Dialogs.tryRun(() -> GalleryActions.restoreFromRecycle(library, record), "Could not restore image")) return;
        refresh();
    }

    private void permanentlyDelete(ImageRecord record) {
        if (!Dialogs.confirm("Delete permanently",
                "Permanently delete \"" + record.originalName() + "\"? This cannot be undone.")) {
            return;
        }
        if (!Dialogs.tryRun(() -> GalleryActions.permanentlyDelete(library, record), "Could not permanently delete image")) {
            return;
        }
        refresh();
    }

    private void emptyBin() {
        if (!Dialogs.confirm("Empty recycle bin", "Permanently delete everything in the recycle bin? This cannot be undone.")) {
            return;
        }
        for (ImageRecord record : library.images().listDeleted()) {
            if (!Dialogs.tryRun(() -> GalleryActions.permanentlyDelete(library, record), "Could not empty the recycle bin")) {
                break;
            }
        }
        refresh();
    }
}
