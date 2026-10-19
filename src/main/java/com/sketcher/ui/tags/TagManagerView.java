package com.sketcher.ui.tags;

import com.sketcher.db.dao.TagDao;
import com.sketcher.library.LibraryService;
import com.sketcher.model.Tag;
import com.sketcher.ui.common.Dialogs;
import com.sketcher.ui.common.Refreshable;
import com.sketcher.ui.components.AppButton;
import com.sketcher.ui.components.Card;
import com.sketcher.ui.components.EmptyState;
import com.sketcher.ui.components.Page;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/** Tag CRUD, hierarchy management (drag to reparent), and merge (PLAN.md 4.3). */
public class TagManagerView extends Page implements Refreshable {
    private final LibraryService library;
    private final TreeView<Tag> treeView = new TreeView<>();
    private final EmptyState emptyState = new EmptyState("No tags yet",
            "Tags organize your references — e.g. figure › hands. Create one to get started.");

    public TagManagerView(LibraryService library) {
        super("Tags");
        this.library = library;
        getStyleClass().add("tag-manager-view");
        setSubtitle("Drag a tag onto another to re-parent it. Right-click a tag for more actions.");
        setActions(AppButton.primary("New tag", () -> createChild(null)));

        treeView.setShowRoot(false);
        treeView.setCellFactory(tv -> new TagTreeCell());
        // TreeView has no placeholder support, so the empty state sits on top of it instead.
        StackPane treeStack = new StackPane(treeView, emptyState);
        VBox.setVgrow(treeStack, Priority.ALWAYS);
        setBody(new Card().flush().add(treeStack));

        refresh();
    }

    @Override
    public void onShown() {
        refresh();
    }

    private void refresh() {
        List<Tag> all = library.tags().listAll();
        Map<Integer, List<Tag>> byParent = all.stream()
                .filter(t -> t.parentId() != null)
                .collect(Collectors.groupingBy(Tag::parentId));
        List<Tag> roots = all.stream().filter(t -> t.parentId() == null).toList();

        TreeItem<Tag> hiddenRoot = new TreeItem<>();
        for (Tag root : roots) {
            hiddenRoot.getChildren().add(buildNode(root, byParent));
        }
        hiddenRoot.setExpanded(true);
        treeView.setRoot(hiddenRoot);
        emptyState.setVisible(roots.isEmpty());
    }

    private TreeItem<Tag> buildNode(Tag tag, Map<Integer, List<Tag>> byParent) {
        TreeItem<Tag> item = new TreeItem<>(tag);
        item.setExpanded(true);
        for (Tag child : byParent.getOrDefault(tag.id(), List.of())) {
            item.getChildren().add(buildNode(child, byParent));
        }
        return item;
    }

    private void createChild(Tag parent) {
        Dialogs.prompt("New tag", parent == null ? "Tag name" : "Child of \"" + parent.name() + "\"", "")
                .ifPresent(name -> {
                    try {
                        library.tags().create(name, parent == null ? null : parent.id());
                        refresh();
                    } catch (IllegalStateException ex) {
                        Dialogs.error("Could not create tag", ex.getMessage());
                    }
                });
    }

    private void rename(Tag tag) {
        Dialogs.prompt("Rename tag", "New name", tag.name()).ifPresent(name -> {
            try {
                library.tags().rename(tag.id(), name);
                refresh();
            } catch (IllegalStateException ex) {
                Dialogs.error("Could not rename tag", ex.getMessage());
            }
        });
    }

    private void delete(Tag tag) {
        int directCount = library.tags().countDirect(tag.id());
        String message = "Delete \"" + tag.name() + "\"? Its children will move up to its parent."
                + (directCount > 0 ? " " + directCount + " image(s) will lose this tag." : "");
        if (Dialogs.confirm("Delete tag", message)) {
            library.tags().deleteReassignChildren(tag.id());
            refresh();
        }
    }

    private void mergeInto(Tag source) {
        List<Tag> candidates = library.tags().listAll().stream()
                .filter(t -> t.id() != source.id())
                .toList();
        if (candidates.isEmpty()) {
            Dialogs.error("Merge tag", "There are no other tags to merge into.");
            return;
        }
        ChoiceDialog<Tag> dialog = new ChoiceDialog<>(candidates.get(0), candidates);
        dialog.setTitle("Merge tag");
        dialog.setHeaderText(null);
        dialog.setContentText("Merge \"" + source.name() + "\" into:");
        com.sketcher.ui.common.Theme.style(dialog.getDialogPane());
        Optional<Tag> target = dialog.showAndWait();
        target.ifPresent(t -> {
            library.tags().merge(source.id(), t.id());
            refresh();
        });
    }

    private class TagTreeCell extends TreeCell<Tag> {
        TagTreeCell() {
            setOnDragDetected(e -> {
                if (getItem() == null) return;
                Dragboard db = startDragAndDrop(TransferMode.MOVE);
                ClipboardContent content = new ClipboardContent();
                content.putString(String.valueOf(getItem().id()));
                db.setContent(content);
                e.consume();
            });
            setOnDragOver(e -> {
                if (getItem() != null && e.getDragboard().hasString()) {
                    e.acceptTransferModes(TransferMode.MOVE);
                }
                e.consume();
            });
            setOnDragDropped(e -> {
                if (getItem() == null) {
                    e.setDropCompleted(false);
                    e.consume();
                    return;
                }
                try {
                    int draggedId = Integer.parseInt(e.getDragboard().getString());
                    library.tags().reparent(draggedId, getItem().id());
                    refresh();
                    e.setDropCompleted(true);
                } catch (IllegalStateException | NumberFormatException ex) {
                    Dialogs.error("Could not move tag", ex.getMessage());
                    e.setDropCompleted(false);
                }
                e.consume();
            });
        }

        @Override
        protected void updateItem(Tag tag, boolean empty) {
            super.updateItem(tag, empty);
            if (empty || tag == null) {
                setText(null);
                setContextMenu(null);
                return;
            }
            int direct = library.tags().countDirect(tag.id());
            int total = library.tags().countIncludingDescendants(tag.id());
            setText(tag.name() + "  (" + direct + (total != direct ? "/" + total : "") + ")");

            ContextMenu menu = new ContextMenu();
            MenuItem addChild = new MenuItem("New child tag…");
            addChild.setOnAction(e -> createChild(tag));
            MenuItem renameItem = new MenuItem("Rename…");
            renameItem.setOnAction(e -> rename(tag));
            MenuItem deleteItem = new MenuItem("Delete");
            deleteItem.setOnAction(e -> delete(tag));
            MenuItem mergeItem = new MenuItem("Merge into…");
            mergeItem.setOnAction(e -> mergeInto(tag));
            MenuItem moveToRoot = new MenuItem("Move to top level");
            moveToRoot.setOnAction(e -> {
                try {
                    library.tags().reparent(tag.id(), null);
                    refresh();
                } catch (IllegalStateException ex) {
                    Dialogs.error("Could not move tag", ex.getMessage());
                }
            });
            menu.getItems().addAll(addChild, renameItem, moveToRoot, mergeItem, deleteItem);
            setContextMenu(menu);
        }
    }
}
