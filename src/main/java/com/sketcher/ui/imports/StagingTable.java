package com.sketcher.ui.imports;

import com.sketcher.model.ImportCandidate;
import com.sketcher.model.ImportStatus;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import com.sketcher.ui.components.AppButton;
import javafx.scene.control.CheckBox;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

/** The import review table: thumbnail, filename, dimensions, size, status, remove (PLAN.md 4.1). */
public class StagingTable extends TableView<ImportCandidate> {
    private final ObservableList<ImportCandidate> candidates = FXCollections.observableArrayList();

    public StagingTable() {
        setItems(candidates);
        setFixedCellSize(68);
        setColumnResizePolicy(CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        TableColumn<ImportCandidate, ImportCandidate> includeCol = new TableColumn<>("Import");
        includeCol.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue()));
        includeCol.setCellFactory(col -> new TableCell<>() {
            private final CheckBox checkBox = new CheckBox();

            @Override
            protected void updateItem(ImportCandidate item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }
                checkBox.setDisable(!item.isIncludable());
                checkBox.selectedProperty().unbindBidirectional(item.includedProperty());
                checkBox.selectedProperty().bindBidirectional(item.includedProperty());
                setGraphic(checkBox);
            }
        });
        includeCol.setPrefWidth(76);
        includeCol.setSortable(false);

        TableColumn<ImportCandidate, ImportCandidate> thumbCol = new TableColumn<>("");
        thumbCol.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue()));
        thumbCol.setCellFactory(col -> new TableCell<>() {
            private final ImageView imageView = new ImageView();

            @Override
            protected void updateItem(ImportCandidate item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }
                imageView.setFitWidth(56);
                imageView.setFitHeight(56);
                imageView.setPreserveRatio(true);
                try {
                    imageView.setImage(new Image(item.sourcePath().toUri().toString(), 56, 56, true, true, true));
                } catch (Exception ignored) {
                    imageView.setImage(null);
                }
                setGraphic(imageView);
            }
        });
        thumbCol.setPrefWidth(76);
        thumbCol.setSortable(false);

        TableColumn<ImportCandidate, String> nameCol = new TableColumn<>("File");
        nameCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().fileName()));
        nameCol.setPrefWidth(260);

        TableColumn<ImportCandidate, String> dimCol = new TableColumn<>("Dimensions");
        dimCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().dimensionsText()));

        TableColumn<ImportCandidate, String> sizeCol = new TableColumn<>("Size");
        sizeCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(humanSize(cd.getValue().getByteSize())));

        TableColumn<ImportCandidate, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(cd -> {
            ImportCandidate c = cd.getValue();
            String text = statusLabel(c.getStatus());
            String reason = c.reasonProperty().get();
            return new ReadOnlyStringWrapper(reason == null || reason.isBlank() ? text : text + " (" + reason + ")");
        });
        statusCol.setPrefWidth(260);

        TableColumn<ImportCandidate, ImportCandidate> removeCol = new TableColumn<>("");
        removeCol.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue()));
        removeCol.setCellFactory(col -> new TableCell<>() {
            private final AppButton removeButton = AppButton.icon("✕", "Remove from batch", null);

            @Override
            protected void updateItem(ImportCandidate item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }
                removeButton.setOnAction(e -> candidates.remove(item));
                setGraphic(removeButton);
            }
        });
        removeCol.setSortable(false);

        getColumns().addAll(java.util.List.of(includeCol, thumbCol, nameCol, dimCol, sizeCol, statusCol, removeCol));
    }

    public ObservableList<ImportCandidate> candidates() {
        return candidates;
    }

    private static String statusLabel(ImportStatus status) {
        return switch (status) {
            case NEW -> "New";
            case ALREADY_IN_LIBRARY -> "Already in library";
            case DUPLICATE_IN_BATCH -> "Duplicate in batch";
            case UNREADABLE -> "Unreadable";
        };
    }

    private static String humanSize(long bytes) {
        if (bytes <= 0) return "";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024));
    }
}
