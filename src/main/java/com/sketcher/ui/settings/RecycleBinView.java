package com.sketcher.ui.settings;

import com.sketcher.library.LibraryService;
import com.sketcher.model.ImageRecord;
import com.sketcher.ui.common.AsyncTask;
import com.sketcher.ui.common.Dialogs;
import com.sketcher.ui.common.Refreshable;
import com.sketcher.ui.components.AppButton;
import com.sketcher.ui.components.Card;
import com.sketcher.ui.components.EmptyState;
import com.sketcher.ui.components.Page;
import com.sketcher.ui.gallery.GalleryActions;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
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
public class RecycleBinView extends Page implements Refreshable {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(ZoneId.systemDefault());

    private final LibraryService library;
    private final AppButton emptyBinButton = AppButton.danger("Empty bin", this::emptyBin);
    private final VBox listBox;

    public RecycleBinView(LibraryService library) {
        super("Recycle bin");
        this.library = library;
        getStyleClass().add("settings-view");
        setActions(emptyBinButton);
        listBox = setScrollingBody();
        listBox.setSpacing(10);
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
        setSubtitle(deleted.size() + (deleted.size() == 1 ? " deleted image" : " deleted images"));
        emptyBinButton.setDisable(deleted.isEmpty());
        listBox.getChildren().clear();
        for (ImageRecord record : deleted) {
            listBox.getChildren().add(buildRow(record));
        }
        if (deleted.isEmpty()) {
            listBox.getChildren().add(new EmptyState("The recycle bin is empty",
                    "Images you delete from the Gallery land here first, so they can be restored."));
        }
    }

    private Card buildRow(ImageRecord record) {
        ImageView thumb = new ImageView();
        thumb.setFitWidth(72);
        thumb.setFitHeight(72);
        thumb.setPreserveRatio(true);
        Image image = library.thumbnails().load(library.ref(), record.hash());
        if (image != null) thumb.setImage(image);

        Label name = new Label(record.originalName() != null ? record.originalName() : record.hash());
        String deletedAt = record.deletedAt() == null ? "" : DATE_FORMAT.format(Instant.ofEpochMilli(record.deletedAt()));
        Label deletedLabel = new Label("Deleted " + deletedAt);
        deletedLabel.getStyleClass().add("hint-label");
        VBox info = new VBox(4, name, deletedLabel);

        HBox row = new HBox(16, thumb, info,
                AppButton.secondary("Restore", () -> restore(record)).small(),
                AppButton.danger("Delete permanently", () -> permanentlyDelete(record)).small());
        row.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(info, javafx.scene.layout.Priority.ALWAYS);
        Card card = new Card().add(row);
        card.setPadding(new javafx.geometry.Insets(12, 16, 12, 12));
        return card;
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
