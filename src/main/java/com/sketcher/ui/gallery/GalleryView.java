package com.sketcher.ui.gallery;

import com.sketcher.filter.Filter;
import com.sketcher.library.LibraryService;
import com.sketcher.model.ImageRecord;
import com.sketcher.model.OrderMode;
import com.sketcher.ui.common.AsyncTask;
import com.sketcher.ui.common.Dialogs;
import com.sketcher.ui.common.Refreshable;
import com.sketcher.ui.components.AppButton;
import com.sketcher.ui.components.Card;
import com.sketcher.ui.components.EmptyState;
import com.sketcher.ui.components.ImageTile;
import com.sketcher.ui.components.Page;
import com.sketcher.ui.components.SegmentedControl;
import com.sketcher.ui.components.Toolbar;
import com.sketcher.ui.tags.TagPicker;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextInputDialog;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The primary library-management surface (PLAN.md 4.2): grid/list views over
 * a live filter, favoriting, multi-select bulk actions, and a detail
 * overlay. The grid virtualizes by row (PLAN.md 7/Phase 8: stay smooth at
 * 10k+ images) — a ListView whose cells are each one row of tiles, so only
 * the rows actually on screen ever have tile nodes built, instead of a
 * TilePane holding every image's tile at once.
 */
public class GalleryView extends StackPane implements Refreshable {
    private static final int TILE_SIZE = 210;
    private static final int TILE_GAP = 14;

    private final LibraryService library;
    private final FilterBar filterBar;
    private final ListView<List<ImageRecord>> gridListView = new ListView<>();
    private final ObservableList<List<ImageRecord>> gridRows = FXCollections.observableArrayList();
    private final TableView<ImageRecord> table = new TableView<>();
    private final StackPane contentStack = new StackPane();
    private final Label selectionLabel = new Label();
    private final Page page = new Page("Gallery");
    private final ImageDetailView detailView;

    private final ObservableList<ImageRecord> results = FXCollections.observableArrayList();
    private final Set<Integer> gridSelection = new LinkedHashSet<>();
    private final Map<Integer, ImageTile> renderedTiles = new HashMap<>();
    private Integer lastClickedIndex;
    private boolean gridMode = true;
    private Filter currentFilter = new Filter.All();

    public GalleryView(LibraryService library) {
        this.library = library;
        getStyleClass().add("gallery-view");

        filterBar = new FilterBar(library);
        filterBar.setOnChange(f -> {
            currentFilter = f;
            runQuery();
        });

        SegmentedControl<Boolean> modeChoice = new SegmentedControl<Boolean>()
                .option(true, "Grid")
                .option(false, "List")
                .onChange(this::setMode);
        page.setActions(modeChoice);

        selectionLabel.getStyleClass().add("muted-label");
        Toolbar bulkBar = new Toolbar().plain()
                .left(selectionLabel)
                .right(AppButton.ghost("Edit tags", this::bulkEditTags).small(),
                        AppButton.ghost("Favorite", this::bulkToggleFavorite).small(),
                        AppButton.ghost("Add to playlist", this::bulkAddToPlaylist).small(),
                        Toolbar.divider(),
                        AppButton.danger("Delete", this::bulkDelete).small());
        updateSelectionLabel();

        gridListView.getStyleClass().add("gallery-grid-list");
        gridListView.setItems(gridRows);
        gridListView.setFixedCellSize(TILE_SIZE + TILE_GAP);
        gridListView.setCellFactory(lv -> new GridRowCell());
        gridListView.setFocusTraversable(true);
        gridListView.widthProperty().addListener((o, a, b) -> rebuildTiles());
        gridListView.setOnKeyPressed(e -> {
            if ((e.isControlDown() || e.isMetaDown()) && e.getCode() == javafx.scene.input.KeyCode.A) {
                gridSelection.clear();
                for (ImageRecord r : results) gridSelection.add(r.id());
                rebuildTiles();
                updateSelectionLabel();
                e.consume();
            }
        });

        buildTableColumns();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.getSelectionModel().setSelectionMode(javafx.scene.control.SelectionMode.MULTIPLE);
        table.setRowFactory(tv -> {
            javafx.scene.control.TableRow<ImageRecord> row = new javafx.scene.control.TableRow<>();
            row.setOnMouseClicked(e -> {
                if (e.getClickCount() == 2 && !row.isEmpty()) {
                    openDetail(results.indexOf(row.getItem()));
                }
            });
            return row;
        });

        gridListView.setPlaceholder(new EmptyState("No images match",
                "Try loosening the filter, or import some images to get started."));
        table.setPlaceholder(new EmptyState("No images match", null));
        table.getSelectionModel().getSelectedItems().addListener(
                (javafx.collections.ListChangeListener<ImageRecord>) c -> updateSelectionLabel());
        contentStack.getChildren().add(gridListView);

        VBox center = new VBox(16, new Card().add(filterBar), bulkBar, contentStack);
        VBox.setVgrow(contentStack, Priority.ALWAYS);
        page.setBody(center);

        detailView = new ImageDetailView(library, this::closeDetail, this::runQuery);

        getChildren().addAll(page, detailView);
        detailView.setVisible(false);
        detailView.setManaged(false);

        runQuery();
    }

    @Override
    public void onShown() {
        runQuery();
    }

    private void setMode(boolean grid) {
        this.gridMode = grid;
        contentStack.getChildren().setAll(grid ? gridListView : table);
        updateSelectionLabel();
    }

    private void updateSelectionLabel() {
        int n = selectedRecords().size();
        selectionLabel.setText(n == 0 ? "Double-click an image to open it"
                : n + " selected");
    }

    private void runQuery() {
        AsyncTask.runDb(
                () -> library.images().query(currentFilter, OrderMode.SEQUENTIAL, null),
                list -> {
                    results.setAll(list);
                    gridSelection.clear();
                    table.setItems(results);
                    rebuildTiles();
                    updateSelectionLabel();
                    page.setSubtitle(results.size() + (results.size() == 1 ? " image" : " images"));
                },
                err -> Dialogs.error("Could not load images", err.getMessage()));
    }

    /** Re-chunks the flat result list into rows sized to the current viewport width. */
    private void rebuildTiles() {
        int columns = Math.max(1, (int) ((gridListView.getWidth() - 16) / (TILE_SIZE + TILE_GAP)));
        List<List<ImageRecord>> rows = new ArrayList<>();
        for (int i = 0; i < results.size(); i += columns) {
            rows.add(new ArrayList<>(results.subList(i, Math.min(i + columns, results.size()))));
        }
        gridRows.setAll(rows);
    }

    /** One row of tiles; reused by the ListView as it scrolls, so only on-screen rows ever build tile nodes. */
    private class GridRowCell extends ListCell<List<ImageRecord>> {
        private final HBox box = new HBox(TILE_GAP);

        GridRowCell() {
            box.setPadding(new Insets(0, 0, TILE_GAP, 0));
            setGraphic(box);
            setText(null);
        }

        @Override
        protected void updateItem(List<ImageRecord> row, boolean empty) {
            super.updateItem(row, empty);
            for (javafx.scene.Node child : box.getChildren()) {
                if (child instanceof ImageTile tile && tile.getUserData() instanceof Integer id) {
                    renderedTiles.remove(id, tile);
                }
            }
            box.getChildren().clear();
            if (!empty && row != null) {
                for (ImageRecord record : row) box.getChildren().add(buildTile(record));
            }
        }
    }

    /**
     * Builds one tile. Selection changes update style classes on the already-
     * rendered tiles directly (see updateSelectionStyles) rather than going
     * through rebuildTiles()/gridRows.setAll() — replacing the clicked tile's
     * Node mid-gesture made JavaFX lose track of double-clicks, since its
     * click-count tracking keys off the Node a click lands on, not just
     * screen position.
     */
    private javafx.scene.Node buildTile(ImageRecord record) {
        ImageTile tile = new ImageTile(library.thumbnails().load(library.ref(), record.hash()), TILE_SIZE)
                .favorite(record.favorite());
        tile.setUserData(record.id());
        tile.setSelected(gridSelection.contains(record.id()));
        renderedTiles.put(record.id(), tile);

        // MOUSE_PRESSED instead of MOUSE_CLICKED: a CLICKED event only
        // synthesizes if the release lands back on the exact same node as
        // the press, which trackpad jitter (or a cell being recycled by the
        // ListView mid-gesture) can silently break, making clicks/double-
        // clicks feel like they sometimes just don't register. PRESSED
        // fires immediately and unconditionally, and still carries an
        // accurate getClickCount().
        tile.setOnMousePressed(e -> {
            gridListView.requestFocus();
            if (e.getButton() != MouseButton.PRIMARY) return;
            int index = results.indexOf(record);
            if (e.getClickCount() == 2) {
                openDetail(index);
                e.consume();
                return;
            }
            Set<Integer> previouslySelected = new LinkedHashSet<>(gridSelection);
            if (e.isShiftDown() && lastClickedIndex != null) {
                int from = Math.min(lastClickedIndex, index);
                int to = Math.max(lastClickedIndex, index);
                for (int i = from; i <= to; i++) gridSelection.add(results.get(i).id());
            } else if (e.isControlDown() || e.isMetaDown()) {
                if (!gridSelection.add(record.id())) gridSelection.remove(record.id());
            } else {
                gridSelection.clear();
                gridSelection.add(record.id());
            }
            lastClickedIndex = index;
            updateSelectionStyles(previouslySelected);
            updateSelectionLabel();
            e.consume();
        });

        return tile;
    }

    /** Toggles the "selected" style class on whichever currently-rendered tiles changed, without rebuilding any Node. */
    private void updateSelectionStyles(Set<Integer> previouslySelected) {
        Set<Integer> changed = new LinkedHashSet<>(previouslySelected);
        changed.addAll(gridSelection);
        for (int id : changed) {
            ImageTile tile = renderedTiles.get(id);
            if (tile != null) tile.setSelected(gridSelection.contains(id));
        }
    }

    private void openDetail(int index) {
        // Visible/managed first: ImageDetailView.open() ends with
        // requestFocus(), which JavaFX silently drops on a node that isn't
        // part of the visible scene graph yet — do it the other order and
        // focus (and so Esc-to-close, arrow-key navigation, etc.) never
        // actually lands on the detail view.
        detailView.setVisible(true);
        detailView.setManaged(true);
        detailView.open(List.copyOf(results), index);
    }

    private void closeDetail() {
        detailView.setVisible(false);
        detailView.setManaged(false);
    }

    private List<ImageRecord> selectedRecords() {
        if (gridMode) {
            return results.stream().filter(r -> gridSelection.contains(r.id())).toList();
        }
        return List.copyOf(table.getSelectionModel().getSelectedItems());
    }

    /**
     * Opens the tag picker pre-checked with the tags common to every selected
     * image, then applies the diff to all of them: newly-checked tags are
     * added, newly-unchecked ones are removed.
     */
    private void bulkEditTags() {
        List<ImageRecord> selected = selectedRecords();
        if (selected.isEmpty()) return;
        Set<Integer> common = selected.stream()
                .map(r -> (Set<Integer>) new LinkedHashSet<>(r.tagIds()))
                .reduce((a, b) -> {
                    a.retainAll(b);
                    return a;
                })
                .orElseGet(LinkedHashSet::new);
        TagPicker.pickMany(library, common).ifPresent(after -> {
            Set<Integer> toAdd = new LinkedHashSet<>(after);
            toAdd.removeAll(common);
            Set<Integer> toRemove = new LinkedHashSet<>(common);
            toRemove.removeAll(after);
            for (ImageRecord r : selected) {
                if (!toAdd.isEmpty()) library.images().addTags(r.id(), toAdd);
                for (int tagId : toRemove) library.images().removeTag(r.id(), tagId);
            }
            runQuery();
        });
    }

    /** Favorites all selected images if any aren't favorited yet, otherwise unfavorites all of them. */
    private void bulkToggleFavorite() {
        List<ImageRecord> selected = selectedRecords();
        if (selected.isEmpty()) return;
        boolean favorite = selected.stream().anyMatch(r -> !r.favorite());
        for (ImageRecord r : selected) library.images().setFavorite(r.id(), favorite);
        runQuery();
    }

    private void bulkAddToPlaylist() {
        List<ImageRecord> selected = selectedRecords();
        if (selected.isEmpty()) return;
        List<String> names = library.playlists().listAll().stream().map(p -> p.name()).collect(Collectors.toList());
        List<String> choices = new ArrayList<>(names);
        choices.add("New playlist…");
        javafx.scene.control.ChoiceDialog<String> dialog = new javafx.scene.control.ChoiceDialog<>(
                choices.isEmpty() ? null : choices.get(0), choices);
        dialog.setTitle("Add to playlist");
        dialog.setHeaderText(null);
        dialog.setContentText("Playlist:");
        com.sketcher.ui.common.Theme.style(dialog.getDialogPane());
        dialog.showAndWait().ifPresent(choice -> {
            Integer playlistId;
            if ("New playlist…".equals(choice)) {
                TextInputDialog nameDialog = new TextInputDialog();
                nameDialog.setTitle("New playlist");
                nameDialog.setHeaderText(null);
                nameDialog.setContentText("Name:");
                com.sketcher.ui.common.Theme.style(nameDialog.getDialogPane());
                var nameOpt = nameDialog.showAndWait();
                if (nameOpt.isEmpty() || nameOpt.get().isBlank()) return;
                playlistId = library.playlists().create(nameOpt.get().trim());
            } else {
                playlistId = library.playlists().listAll().stream()
                        .filter(p -> p.name().equals(choice)).findFirst().map(p -> p.id()).orElse(null);
            }
            if (playlistId == null) return;
            for (ImageRecord r : selected) library.playlists().addItem(playlistId, r.id());
        });
    }

    private void bulkDelete() {
        List<ImageRecord> selected = selectedRecords();
        if (selected.isEmpty()) return;
        if (!Dialogs.confirm("Delete images", "Move " + selected.size() + " image(s) to the recycle bin?")) return;
        for (ImageRecord r : selected) {
            if (!Dialogs.tryRun(() -> GalleryActions.softDeleteToRecycle(library, r), "Could not delete an image")) break;
        }
        runQuery();
    }

    private void buildTableColumns() {
        TableColumn<ImageRecord, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().originalName()));
        nameCol.setPrefWidth(260);

        TableColumn<ImageRecord, String> dimCol = new TableColumn<>("Dimensions");
        dimCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(
                cd.getValue().width() + " x " + cd.getValue().height()));

        TableColumn<ImageRecord, String> tagsCol = new TableColumn<>("Tags");
        tagsCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(
                cd.getValue().tagIds().stream().map(library.tags()::fullPath).collect(Collectors.joining(", "))));
        tagsCol.setPrefWidth(280);

        TableColumn<ImageRecord, String> favCol = new TableColumn<>("★");
        favCol.setPrefWidth(56);
        favCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(cd.getValue().favorite() ? "★" : ""));

        TableColumn<ImageRecord, String> importedCol = new TableColumn<>("Imported");
        importedCol.setPrefWidth(170);
        importedCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(formatDate(cd.getValue().importedAt())));

        TableColumn<ImageRecord, String> lastSeenCol = new TableColumn<>("Last seen");
        lastSeenCol.setPrefWidth(170);
        lastSeenCol.setCellValueFactory(cd -> new ReadOnlyStringWrapper(
                cd.getValue().lastSeenAt() == null ? "never" : formatDate(cd.getValue().lastSeenAt())));

        table.getColumns().addAll(List.of(favCol, nameCol, dimCol, tagsCol, importedCol, lastSeenCol));
    }

    private static String formatDate(long epochMillis) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date(epochMillis));
    }
}
