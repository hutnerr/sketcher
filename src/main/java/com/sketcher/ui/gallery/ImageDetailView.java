package com.sketcher.ui.gallery;

import com.sketcher.library.LibraryService;
import com.sketcher.model.ImageRecord;
import com.sketcher.model.Tag;
import com.sketcher.ui.common.Dialogs;
import com.sketcher.ui.tags.TagChip;
import com.sketcher.ui.tags.TagPicker;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/** Full-size image view with metadata, favorite/tag editing, and arrow-key navigation (PLAN.md 4.2). */
public class ImageDetailView extends BorderPane {
    private final LibraryService library;
    private final Runnable onClose;
    private final Runnable onLibraryChanged;

    private List<ImageRecord> context;
    private int index;

    private final ImageView imageView = new ImageView();
    private final Label nameLabel = new Label();
    private final Label metaLabel = new Label();
    private final ToggleButton favoriteButton = new ToggleButton("★ Favorite");
    private final FlowPane tagsPane = new FlowPane(6, 6);
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
        imageView.fitWidthProperty().bind(imageArea.widthProperty().subtract(20));
        imageView.fitHeightProperty().bind(imageArea.heightProperty().subtract(20));
        setCenter(imageArea);

        Button prevButton = new Button("< Prev");
        prevButton.setOnAction(e -> move(-1));
        Button nextButton = new Button("Next >");
        nextButton.setOnAction(e -> move(1));
        Button closeButton = new Button("Close");
        closeButton.setOnAction(e -> onClose.run());
        favoriteButton.setOnAction(e -> toggleFavorite());
        Button addTagsButton = new Button("Add tags…");
        addTagsButton.setOnAction(e -> addTags());
        Button deleteButton = new Button("Delete");
        deleteButton.getStyleClass().add("danger-button");
        deleteButton.setOnAction(e -> delete());

        HBox topBar = new HBox(10, closeButton, positionLabel, prevButton, nextButton,
                favoriteButton, addTagsButton, deleteButton);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setPadding(new Insets(14));
        setTop(topBar);

        nameLabel.getStyleClass().add("detail-name");
        tagsPane.setPadding(new Insets(6, 0, 0, 0));
        VBox sidebar = new VBox(10, nameLabel, metaLabel, new Label("Tags:"), tagsPane);
        sidebar.setPadding(new Insets(16));
        sidebar.setPrefWidth(270);
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
        metaLabel.setText(record.width() + " x " + record.height() + "  ·  " + humanSize(record.byteSize())
                + "\nImported " + formatDate(record.importedAt())
                + (record.lastSeenAt() != null ? "\nLast seen " + formatDate(record.lastSeenAt()) : "\nNever practiced")
                + "\nSeen " + record.seenCount() + " time(s)");
        favoriteButton.setSelected(record.favorite());
        positionLabel.setText((index + 1) + " / " + context.size());

        tagsPane.getChildren().clear();
        for (int tagId : record.tagIds()) {
            library.tags().get(tagId).ifPresent(tag -> tagsPane.getChildren().add(
                    new TagChip(library.tags().fullPath(tag.id()), () -> removeTag(record, tag))));
        }
    }

    private void toggleFavorite() {
        ImageRecord record = context.get(index);
        boolean newValue = !record.favorite();
        library.images().setFavorite(record.id(), newValue);
        favoriteButton.setSelected(newValue);
        onLibraryChanged.run();
    }

    private void addTags() {
        ImageRecord record = context.get(index);
        TagPicker.pickMany(library, new java.util.HashSet<>(record.tagIds())).ifPresent(tagIds -> {
            library.images().addTags(record.id(), tagIds);
            onLibraryChanged.run();
        });
    }

    private void removeTag(ImageRecord record, Tag tag) {
        library.images().removeTag(record.id(), tag.id());
        onLibraryChanged.run();
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
