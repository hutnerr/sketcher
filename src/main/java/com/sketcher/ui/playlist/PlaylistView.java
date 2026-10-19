package com.sketcher.ui.playlist;

import com.sketcher.library.LibraryService;
import com.sketcher.model.ImageRecord;
import com.sketcher.model.Playlist;
import com.sketcher.ui.common.AsyncTask;
import com.sketcher.ui.common.Dialogs;
import com.sketcher.ui.common.Refreshable;
import com.sketcher.ui.components.AppButton;
import com.sketcher.ui.components.Card;
import com.sketcher.ui.components.EmptyState;
import com.sketcher.ui.components.ImageTile;
import com.sketcher.ui.components.Page;
import com.sketcher.ui.components.Spacer;
import com.sketcher.ui.gallery.ImageDetailView;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
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
    private static final int TILE_SIZE = 200;
    private static final int TILE_GAP = 14;

    private final LibraryService library;
    private final Consumer<Integer> onStartPractice;
    private final ListView<Playlist> playlistList = new ListView<>();
    private final Label itemsHeading = new Label();
    private final Label itemsCount = new Label();
    private final FlowPane itemsPane = new FlowPane(TILE_GAP, TILE_GAP);
    private final AppButton startButton = AppButton.primary("Start practice", this::startPractice);
    private final AppButton renameButton = AppButton.ghost("Rename", this::renamePlaylist).small();
    private final AppButton deleteButton = AppButton.danger("Delete", this::deletePlaylist).small();
    private final ImageDetailView detailView;

    private Playlist currentPlaylist;
    private List<ImageRecord> currentRecords = List.of();

    public PlaylistView(LibraryService library, Consumer<Integer> onStartPractice) {
        this.library = library;
        this.onStartPractice = onStartPractice;
        getStyleClass().add("playlist-view");

        Page page = new Page("Playlists");
        page.setSubtitle("Hand-picked sets of images. Add images from the Gallery's \"Add to playlist\" action.");
        page.setActions(AppButton.secondary("New playlist…", this::createPlaylist));

        playlistList.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Playlist item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.name());
            }
        });
        playlistList.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> loadItems(b));
        playlistList.setPlaceholder(new EmptyState("No playlists", null));
        VBox.setVgrow(playlistList, Priority.ALWAYS);
        Card listCard = new Card().flush().add(playlistList);
        listCard.setPrefWidth(260);
        listCard.setMinWidth(260);

        itemsHeading.getStyleClass().add("card-title");
        itemsHeading.setMinWidth(140);
        itemsCount.getStyleClass().add("card-description");
        startButton.setDisable(true);
        HBox itemsTop = new HBox(8, new VBox(2, itemsHeading, itemsCount), new Spacer(),
                renameButton, deleteButton, startButton);
        itemsTop.setAlignment(Pos.CENTER_LEFT);
        itemsTop.setPadding(new Insets(20, 24, 4, 24));

        itemsPane.setPadding(new Insets(16, 24, 24, 24));
        ScrollPane itemsScroll = new ScrollPane(itemsPane);
        itemsScroll.setFitToWidth(true);
        itemsScroll.getStyleClass().add("page-scroll");
        VBox.setVgrow(itemsScroll, Priority.ALWAYS);
        Card itemsCard = new Card().flush().add(itemsTop, itemsScroll);
        itemsCard.setSpacing(0);
        HBox.setHgrow(itemsCard, Priority.ALWAYS);

        HBox body = new HBox(20, listCard, itemsCard);
        page.setBody(body);

        detailView = new ImageDetailView(library, this::closeDetail, () -> loadItems(currentPlaylist));

        getChildren().addAll(page, detailView);
        detailView.setVisible(false);
        detailView.setManaged(false);

        refresh();
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
        boolean hasPlaylist = playlist != null;
        renameButton.setDisable(!hasPlaylist);
        deleteButton.setDisable(!hasPlaylist);
        if (!hasPlaylist) {
            itemsHeading.setText("No playlist selected");
            itemsCount.setText("");
            startButton.setDisable(true);
            itemsPane.getChildren().setAll(emptyState("No playlists yet",
                    "Create one to start collecting images for a focused practice session."));
            return;
        }
        itemsHeading.setText(playlist.name());
        itemsCount.setText("");
        AsyncTask.runDb(
                () -> {
                    List<Integer> ids = library.playlists().listImageIds(playlist.id(), false);
                    return ids.stream().map(id -> library.images().findById(id).orElse(null))
                            .filter(r -> r != null && !r.isDeleted()).toList();
                },
                records -> {
                    currentRecords = records;
                    itemsCount.setText(records.size() + (records.size() == 1 ? " image" : " images"));
                    startButton.setDisable(records.isEmpty());
                    itemsPane.getChildren().clear();
                    if (records.isEmpty()) {
                        itemsPane.getChildren().add(emptyState("This playlist is empty",
                                "Select images in the Gallery and use \"Add to playlist\"."));
                        return;
                    }
                    for (int i = 0; i < records.size(); i++) {
                        itemsPane.getChildren().add(buildTile(playlist, records.get(i), i));
                    }
                },
                err -> Dialogs.error("Could not load playlist images", err.getMessage()));
    }

    /** EmptyState sized to span the whole FlowPane so it centers instead of hugging the left edge. */
    private EmptyState emptyState(String title, String message) {
        EmptyState empty = new EmptyState(title, message);
        empty.prefWidthProperty().bind(itemsPane.widthProperty().subtract(48));
        return empty;
    }

    private Node buildTile(Playlist playlist, ImageRecord record, int index) {
        Button removeButton = new Button("✕");
        removeButton.getStyleClass().add("tile-remove-button");
        removeButton.setOnAction(e -> {
            library.playlists().removeItem(playlist.id(), record.id());
            loadItems(playlist);
        });

        removeButton.setTooltip(new javafx.scene.control.Tooltip("Remove from playlist"));

        ImageTile tile = new ImageTile(library.thumbnails().load(library.ref(), record.hash()), TILE_SIZE)
                .favorite(record.favorite())
                .overlay(removeButton, Pos.TOP_LEFT);
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
