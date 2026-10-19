package com.sketcher.ui.gallery;

import com.sketcher.library.LibraryService;
import com.sketcher.model.ImageRecord;
import com.sketcher.model.Tag;
import com.sketcher.ui.common.Dialogs;
import com.sketcher.ui.components.AppButton;
import com.sketcher.ui.components.Toolbar;
import com.sketcher.ui.tags.TagChip;
import com.sketcher.ui.tags.TagPicker;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Full-size image view with metadata, favorite/tag editing, and arrow-key navigation (PLAN.md 4.2). */
public class ImageDetailView extends BorderPane {
    private final LibraryService library;
    private final Runnable onClose;
    private final Runnable onLibraryChanged;

    private List<ImageRecord> context;
    private int index;

    private final ImageView imageView = new ImageView();
    private final Label nameLabel = new Label();
    private final GridPane metaGrid = new GridPane();
    private final ToggleButton favoriteButton = AppButton.small(AppButton.toggle("★ Favorite", AppButton.Variant.GHOST));
    private final FlowPane tagsPane = new FlowPane(8, 8);
    private final Label positionLabel = new Label();

    public ImageDetailView(LibraryService library, Runnable onClose, Runnable onLibraryChanged) {
        this.library = library;
        this.onClose = onClose;
        this.onLibraryChanged = onLibraryChanged;
        getStyleClass().add("image-detail-view");

        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);
        StackPane imageArea = new StackPane(imageView);
        imageArea.getStyleClass().add("image-area");
        imageArea.setMinSize(0, 0);
        imageView.fitWidthProperty().bind(imageArea.widthProperty().subtract(48));
        imageView.fitHeightProperty().bind(imageArea.heightProperty().subtract(48));
        setCenter(imageArea);

        favoriteButton.setOnAction(e -> toggleFavorite());
        positionLabel.getStyleClass().add("muted-label");
        Toolbar topBar = new Toolbar()
                .left(AppButton.ghost("← Back", onClose).small())
                .center(AppButton.icon("‹", "Previous (←)", () -> move(-1)), positionLabel,
                        AppButton.icon("›", "Next (→)", () -> move(1)))
                .right(favoriteButton, AppButton.ghost("Edit tags…", this::editTags).small(),
                        Toolbar.divider(), AppButton.danger("Delete", this::delete).small());
        setTop(topBar);

        nameLabel.getStyleClass().add("detail-name");
        nameLabel.setWrapText(true);
        metaGrid.setHgap(16);
        javafx.scene.layout.ColumnConstraints keyColumn = new javafx.scene.layout.ColumnConstraints();
        keyColumn.setMinWidth(javafx.scene.layout.Region.USE_PREF_SIZE);
        javafx.scene.layout.ColumnConstraints valueColumn = new javafx.scene.layout.ColumnConstraints();
        valueColumn.setHgrow(javafx.scene.layout.Priority.ALWAYS);
        valueColumn.setMinWidth(0);
        metaGrid.getColumnConstraints().addAll(keyColumn, valueColumn);
        metaGrid.setVgap(8);
        Label tagsHeading = new Label("Tags");
        tagsHeading.getStyleClass().add("section-title");
        VBox sidebar = new VBox(20, nameLabel, metaGrid, tagsHeading, tagsPane);
        VBox.setMargin(tagsHeading, new Insets(8, 0, -8, 0));
        sidebar.getStyleClass().add("detail-sidebar");
        sidebar.setPadding(new Insets(28));
        sidebar.setPrefWidth(340);
        sidebar.setMinWidth(340);
        sidebar.setMaxWidth(340);
        tagsPane.setPrefWrapLength(280);
        setRight(sidebar);

        setFocusTraversable(true);
        setOnKeyPressed(e -> {
            switch (e.getCode()) {
                case LEFT -> move(-1);
                case RIGHT -> move(1);
                case ESCAPE -> onClose.run();
                case F -> toggleFavorite();
                default -> {
                }
            }
        });
    }

    public void open(List<ImageRecord> context, int startIndex) {
        this.context = context;
        this.index = startIndex;
        render();
        requestFocus();
    }

    private void move(int delta) {
        int newIndex = index + delta;
        if (newIndex < 0 || newIndex >= context.size()) return;
        index = newIndex;
        render();
    }

    private void render() {
        ImageRecord record = context.get(index);
        String uri = library.ref().imagePath(record.hash(), record.ext()).toUri().toString();
        imageView.setImage(new Image(uri, 0, 0, true, true, true));

        nameLabel.setText(record.originalName());
        metaGrid.getChildren().clear();
        addMeta(0, "Size", record.width() + " × " + record.height() + "  ·  " + humanSize(record.byteSize()));
        addMeta(1, "Imported", formatDate(record.importedAt()));
        addMeta(2, "Last seen", record.lastSeenAt() != null ? formatDate(record.lastSeenAt()) : "Never practiced");
        addMeta(3, "Practiced", record.seenCount() + (record.seenCount() == 1 ? " time" : " times"));
        favoriteButton.setSelected(record.favorite());
        positionLabel.setText((index + 1) + " / " + context.size());

        tagsPane.getChildren().clear();
        for (int tagId : record.tagIds()) {
            library.tags().get(tagId).ifPresent(tag -> tagsPane.getChildren().add(
                    new TagChip(library.tags().fullPath(tag.id()), () -> removeTag(record, tag))));
        }
        if (tagsPane.getChildren().isEmpty()) {
            Label none = new Label("No tags yet");
            none.getStyleClass().add("hint-label");
            tagsPane.getChildren().add(none);
        }
    }

    private void addMeta(int row, String key, String value) {
        Label keyLabel = new Label(key);
        keyLabel.getStyleClass().add("meta-key");
        keyLabel.setMinWidth(USE_PREF_SIZE);
        Label valueLabel = new Label(value);
        valueLabel.getStyleClass().add("meta-value");
        valueLabel.setWrapText(true);
        metaGrid.addRow(row, keyLabel, valueLabel);
    }

    private void toggleFavorite() {
        ImageRecord record = context.get(index);
        boolean newValue = !record.favorite();
        library.images().setFavorite(record.id(), newValue);
        favoriteButton.setSelected(newValue);
        onLibraryChanged.run();
    }

    /** Opens the tag picker pre-checked with this image's current tags and applies the diff: adds, then removes. */
    private void editTags() {
        ImageRecord record = context.get(index);
        Set<Integer> before = new HashSet<>(record.tagIds());
        TagPicker.pickMany(library, before).ifPresent(after -> {
            Set<Integer> toAdd = new HashSet<>(after);
            toAdd.removeAll(before);
            Set<Integer> toRemove = new HashSet<>(before);
            toRemove.removeAll(after);
            if (!toAdd.isEmpty()) library.images().addTags(record.id(), toAdd);
            for (int tagId : toRemove) library.images().removeTag(record.id(), tagId);
            refreshCurrent();
            onLibraryChanged.run();
        });
    }

    private void removeTag(ImageRecord record, Tag tag) {
        library.images().removeTag(record.id(), tag.id());
        refreshCurrent();
        onLibraryChanged.run();
    }

    /** record/favorite/tagIds are immutable, so after editing the current image, re-fetch it to re-render accurately. */
    private void refreshCurrent() {
        library.images().findById(context.get(index).id()).ifPresent(updated -> context.set(index, updated));
        render();
    }

    private void delete() {
        if (!Dialogs.confirm("Delete image", "Move this image to the recycle bin?")) return;
        ImageRecord record = context.get(index);
        if (!Dialogs.tryRun(() -> GalleryActions.softDeleteToRecycle(library, record), "Could not delete image")) return;
        onLibraryChanged.run();
        onClose.run();
    }

    private static String humanSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024));
    }

    private static String formatDate(long epochMillis) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(epochMillis));
    }
}
