package com.sketcher.ui.playlist;

import com.sketcher.library.LibraryService;
import com.sketcher.model.ImageRecord;
import com.sketcher.model.Playlist;
import com.sketcher.ui.common.AsyncTask;
import com.sketcher.ui.common.Dialogs;
import com.sketcher.ui.common.Refreshable;
import com.sketcher.ui.gallery.ImageDetailView;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.function.Consumer;

/**
 * Create/rename/delete playlists and manage the images in each one. Images
 * are added to a playlist from the Gallery's "Add to playlist" action;
 * double-clicking one here opens the same detail view the Gallery uses
 * (favorite, edit tags, delete, browse with arrow keys), and "Start
 * practice" sends you to the Practice page with this playlist already
 * selected as the source, so timing/order/count can still be chosen there.
 */
public class PlaylistView extends StackPane implements Refreshable {
    private static final int TILE_SIZE = 190;
    private static final int TILE_GAP = 10;

    private final LibraryService library;
    private final Consumer<Integer> onStartPractice;
    private final ListView<Playlist> playlistList = new ListView<>();
    private final Label itemsHeading = new Label();
    private final FlowPane itemsPane = new FlowPane(TILE_GAP, TILE_GAP);
    private final Button startButton = new Button("Start practice");
    private final ImageDetailView detailView;

    private Playlist currentPlaylist;
    private List<ImageRecord> currentRecords = List.of();

    public PlaylistView(LibraryService library, Consumer<Integer> onStartPractice) {
        this.library = library;
        this.onStartPractice = onStartPractice;
        getStyleClass().add("playlist-view");

        BorderPane mainPane = new BorderPane();

        Button newButton = new Button("New playlist…");
        newButton.setOnAction(e -> createPlaylist());
        Button renameButton = new Button("Rename…");
        renameButton.setOnAction(e -> renamePlaylist());
        Button deleteButton = new Button("Delete");
        deleteButton.getStyleClass().add("danger-button");
        deleteButton.setOnAction(e -> deletePlaylist());
        VBox leftToolbar = new VBox(8, newButton, renameButton, deleteButton);
        leftToolbar.setPadding(new Insets(16, 16, 12, 16));

        playlistList.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Playlist item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.name());
            }
        });
        playlistList.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> loadItems(b));
        playlistList.setPrefWidth(240);
        VBox.setVgrow(playlistList, Priority.ALWAYS);

        VBox left = new VBox(leftToolbar, playlistList);
        mainPane.setLeft(left);

        itemsHeading.getStyleClass().add("home-title");
        startButton.getStyleClass().add("primary-button");
        startButton.setDisable(true);
        startButton.setOnAction(e -> startPractice());
        HBox itemsTop = new HBox(16, itemsHeading, spacer(), startButton);
        itemsTop.setAlignment(Pos.CENTER_LEFT);
        itemsTop.setPadding(new Insets(20, 20, 0, 20));

        itemsPane.setPadding(new Insets(20));
        ScrollPane itemsScroll = new ScrollPane(itemsPane);
        itemsScroll.setFitToWidth(true);
        VBox.setVgrow(itemsScroll, Priority.ALWAYS);

        VBox center = new VBox(12, itemsTop, itemsScroll);
        mainPane.setCenter(center);

        detailView = new ImageDetailView(library, this::closeDetail, () -> loadItems(currentPlaylist));

        getChildren().addAll(mainPane, detailView);
        detailView.setVisible(false);
        detailView.setManaged(false);

        refresh();
    }

    private Region spacer() {
        Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        return r;
    }

    @Override
    public void onShown() {
        refresh();
    }

    private void refresh() {
        Playlist previouslySelected = playlistList.getSelectionModel().getSelectedItem();
        AsyncTask.runDb(
                () -> library.playlists().listAll(),
                list -> {
                    playlistList.getItems().setAll(list);
                    Playlist toSelect = previouslySelected == null ? null : list.stream()
                            .filter(p -> p.id() == previouslySelected.id()).findFirst().orElse(null);
                    if (toSelect != null) {
                        playlistList.getSelectionModel().select(toSelect);
                    } else if (!list.isEmpty()) {
                        playlistList.getSelectionModel().select(0);
                    } else {
                        loadItems(null);
                    }
                },
                err -> Dialogs.error("Could not load playlists", err.getMessage()));
    }

    private void loadItems(Playlist playlist) {
        currentPlaylist = playlist;
        closeDetail();
        if (playlist == null) {
            itemsHeading.setText("");
            startButton.setDisable(true);
            itemsPane.getChildren().clear();
            Label empty = new Label("No playlists yet. Create one to start collecting images.");
            empty.getStyleClass().add("hint-label");
            itemsPane.getChildren().add(empty);
            return;
        }
        itemsHeading.setText(playlist.name());
        AsyncTask.runDb(
                () -> {
                    List<Integer> ids = library.playlists().listImageIds(playlist.id(), false);
                    return ids.stream().map(id -> library.images().findById(id).orElse(null))
                            .filter(r -> r != null && !r.isDeleted()).toList();
                },
                records -> {
                    currentRecords = records;
                    itemsHeading.setText(playlist.name() + "  ·  " + records.size()
                            + (records.size() == 1 ? " image" : " images"));
                    startButton.setDisable(records.isEmpty());
                    itemsPane.getChildren().clear();
                    if (records.isEmpty()) {
                        Label empty = new Label(
                                "No images in this playlist yet. Add some from the Gallery's \"Add to playlist\" action.");
                        empty.getStyleClass().add("hint-label");
                        empty.setWrapText(true);
                        itemsPane.getChildren().add(empty);
                        return;
                    }
                    for (int i = 0; i < records.size(); i++) {
                        itemsPane.getChildren().add(buildTile(playlist, records.get(i), i));
                    }
                },
                err -> Dialogs.error("Could not load playlist images", err.getMessage()));
    }

    private Node buildTile(Playlist playlist, ImageRecord record, int index) {
        ImageView imageView = new ImageView(library.thumbnails().load(library.ref(), record.hash()));
        imageView.setFitWidth(TILE_SIZE - 12);
        imageView.setFitHeight(TILE_SIZE - 12);
        imageView.setPreserveRatio(true);

        Button removeButton = new Button("✕");
        removeButton.getStyleClass().add("tile-remove-button");
        removeButton.setOnAction(e -> {
            library.playlists().removeItem(playlist.id(), record.id());
            loadItems(playlist);
        });

        StackPane tile = new StackPane(imageView, removeButton);
        StackPane.setAlignment(removeButton, Pos.TOP_RIGHT);
        StackPane.setMargin(removeButton, new Insets(8));
        tile.getStyleClass().add("gallery-tile");
        tile.setPrefSize(TILE_SIZE, TILE_SIZE);
        tile.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) openDetail(index);
        });
        return tile;
    }

    private void openDetail(int index) {
        // Visible/managed first — see GalleryView.openDetail() for why order matters here.
        detailView.setVisible(true);
        detailView.setManaged(true);
        detailView.open(List.copyOf(currentRecords), index);
    }

    private void closeDetail() {
        detailView.setVisible(false);
        detailView.setManaged(false);
    }

    private void startPractice() {
        if (currentPlaylist == null || currentRecords.isEmpty()) return;
        onStartPractice.accept(currentPlaylist.id());
    }

    private void createPlaylist() {
        Dialogs.prompt("New playlist", "Name", "").ifPresent(name -> {
            library.playlists().create(name);
            refresh();
        });
    }

    private void renamePlaylist() {
        Playlist selected = playlistList.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        Dialogs.prompt("Rename playlist", "New name", selected.name()).ifPresent(name -> {
            library.playlists().rename(selected.id(), name);
            refresh();
        });
    }

    private void deletePlaylist() {
        Playlist selected = playlistList.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        if (!Dialogs.confirm("Delete playlist", "Delete \"" + selected.name() + "\"? This won't delete its images.")) return;
        library.playlists().delete(selected.id());
        refresh();
    }
}
