package com.sketcher.ui.imports;

import com.sketcher.model.ImportCandidate;
import com.sketcher.model.ImportStatus;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.Observable;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import com.sketcher.ui.components.AppButton;
import javafx.scene.control.CheckBox;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;

import java.util.List;

/**
 * The import review table: thumbnail, filename, dimensions, size, status,
 * remove (PLAN.md 4.1). The Import column's header checkbox checks or
 * unchecks every row that can be imported, Shift-clicking a checkbox sets
 * the whole range since the last one clicked, and Space toggles the
 * checkboxes of all selected rows — together with the Import page's selection bar, that
 * makes it practical to stage a large folder and import it a slice at a time.
 */
public class StagingTable extends TableView<ImportCandidate> {
    /** The extractor makes a checkbox or status change fire a list update, so the header and listeners stay in sync. */
    private final ObservableList<ImportCandidate> candidates = FXCollections.observableArrayList(
            c -> new Observable[]{c.includedProperty(), c.statusProperty(), c.reasonProperty()});
    private final CheckBox headerCheckBox = new CheckBox();
    private boolean syncingHeader;
    /** Row of the last checkbox clicked; a Shift-click extends from here, like a file manager's range select. */
    private int checkAnchor = -1;

    public StagingTable() {
        setItems(candidates);
        setFixedCellSize(68);
        setColumnResizePolicy(CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        TableColumn<ImportCandidate, ImportCandidate> includeCol = new TableColumn<>("");
        headerCheckBox.setOnAction(e -> {
            if (syncingHeader) return;
            setIncluded(candidates, !allIncludableChecked());
        });
        headerCheckBox.setTooltip(new javafx.scene.control.Tooltip("Check or uncheck every file"));
        includeCol.setGraphic(headerCheckBox);
        includeCol.setCellValueFactory(cd -> new ReadOnlyObjectWrapper<>(cd.getValue()));
        includeCol.setCellFactory(col -> new TableCell<>() {
            private final CheckBox checkBox = new CheckBox();

            {
                // MOUSE_CLICKED arrives after the checkbox has toggled, so isSelected() is the state to spread.
                checkBox.addEventHandler(MouseEvent.MOUSE_CLICKED, e -> {
                    int index = getIndex();
                    if (e.isShiftDown() && checkAnchor >= 0 && checkAnchor < getItems().size() && checkAnchor != index) {
                        int from = Math.min(checkAnchor, index), to = Math.max(checkAnchor, index);
                        setIncluded(List.copyOf(getItems().subList(from, to + 1)), checkBox.isSelected());
                    }
                    checkAnchor = index;
                });
            }
            /** Cells are recycled across rows — unbind from the previous row, or one checkbox ends up driving many. */
            private ImportCandidate bound;

            @Override
            protected void updateItem(ImportCandidate item, boolean empty) {
                super.updateItem(item, empty);
                if (bound != null) {
                    checkBox.selectedProperty().unbindBidirectional(bound.includedProperty());
                    bound = null;
                }
                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }
                checkBox.setDisable(!item.isIncludable());
                checkBox.selectedProperty().bindBidirectional(item.includedProperty());
                bound = item;
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

        candidates.addListener((ListChangeListener<ImportCandidate>) c -> syncHeader());
        syncHeader();

        addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() != KeyCode.SPACE || getSelectionModel().isEmpty()) return;
            List<ImportCandidate> selected = List.copyOf(getSelectionModel().getSelectedItems());
            boolean anyUnchecked = selected.stream().anyMatch(c -> c.isIncludable() && !c.isIncluded());
            setIncluded(selected, anyUnchecked);
            e.consume();
        });
    }

    /** Checks or unchecks these rows; rows that can't be imported (unreadable, duplicate) are left alone. */
    public void setIncluded(List<ImportCandidate> rows, boolean included) {
        for (ImportCandidate c : rows) {
            if (c.isIncludable()) c.includedProperty().set(included);
        }
    }

    private boolean allIncludableChecked() {
        return candidates.stream().filter(ImportCandidate::isIncludable).allMatch(ImportCandidate::isIncluded);
    }

    /** Header shows checked when every importable row is, unchecked when none is, and a dash when it's mixed. */
    private void syncHeader() {
        long includable = candidates.stream().filter(ImportCandidate::isIncludable).count();
        long checked = candidates.stream().filter(c -> c.isIncludable() && c.isIncluded()).count();
        syncingHeader = true;
        headerCheckBox.setDisable(includable == 0);
        headerCheckBox.setIndeterminate(checked > 0 && checked < includable);
        headerCheckBox.setSelected(includable > 0 && checked == includable);
        syncingHeader = false;
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
