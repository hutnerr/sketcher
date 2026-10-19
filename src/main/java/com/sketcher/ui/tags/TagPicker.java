package com.sketcher.ui.tags;

import com.sketcher.library.LibraryService;
import com.sketcher.model.Tag;
import com.sketcher.ui.common.Dialogs;
import com.sketcher.ui.components.AppButton;
import com.sketcher.ui.components.Card;
import com.sketcher.ui.components.Spacer;
import javafx.geometry.Pos;
import javafx.scene.control.CheckBoxTreeItem;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TextField;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.control.cell.CheckBoxTreeCell;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Reusable tag tree selector: single or multi-select, with optional
 * create-on-the-fly (PLAN.md 4.1 import tagging, 4.3 tag management). Used
 * by import batch tagging, the filter builder, bulk tagging, and per-image
 * tagging — only the filter builder (picking an existing tag to filter by,
 * not managing the vocabulary) disables tag creation. Starts fully expanded,
 * with a button to collapse/expand the whole tree.
 *
 * Multi-select uses a checkbox per row rather than relying on the TreeView's
 * own MULTIPLE selection mode (which needs Ctrl/Shift+click and isn't
 * discoverable — a plain click would just replace the whole selection).
 */
public class TagPicker extends VBox {
    private final LibraryService library;
    private final boolean multiSelect;
    private final boolean allowCreate;
    private final TreeView<Tag> treeView = new TreeView<>();
    private final TextField newTagField = new TextField();
    private final AppButton collapseToggle = AppButton.ghost("Collapse all", null).small();
    private boolean collapsed = false;

    public TagPicker(LibraryService library, boolean multiSelect) {
        this(library, multiSelect, true);
    }

    /** allowCreate is false for the filter builder's tag picker — you're choosing an existing tag to filter by, not managing the tag vocabulary. */
    public TagPicker(LibraryService library, boolean multiSelect, boolean allowCreate) {
        this.library = library;
        this.multiSelect = multiSelect;
        this.allowCreate = allowCreate;
        setSpacing(12);
        setPrefWidth(460);

        treeView.setShowRoot(false);
        if (multiSelect) {
            treeView.setCellFactory(CheckBoxTreeCell.forTreeView(
                    item -> item instanceof CheckBoxTreeItem<Tag> checkItem ? checkItem.selectedProperty() : null,
                    new javafx.util.StringConverter<>() {
                        @Override
                        public String toString(TreeItem<Tag> item) {
                            return item == null || item.getValue() == null ? "" : item.getValue().name();
                        }

                        @Override
                        public TreeItem<Tag> fromString(String string) {
                            throw new UnsupportedOperationException();
                        }
                    }));
        } else {
            treeView.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
            treeView.setCellFactory(tv -> new javafx.scene.control.TreeCell<>() {
                @Override
                protected void updateItem(Tag item, boolean empty) {
                    super.updateItem(item, empty);
                    setText(empty || item == null ? null : item.name());
                }
            });
        }
        treeView.setPrefHeight(380);

        collapseToggle.setOnAction(e -> toggleCollapse());
        javafx.scene.control.Label heading = new javafx.scene.control.Label(
                multiSelect ? "Check every tag that applies" : "Pick a tag");
        heading.getStyleClass().add("muted-label");
        HBox treeToolbar = new HBox(8, heading, new Spacer(), collapseToggle);
        treeToolbar.setAlignment(Pos.CENTER_LEFT);

        getChildren().addAll(treeToolbar, new Card().flush().add(treeView));

        if (allowCreate) {
            newTagField.setPromptText("New tag (added under the selected tag)");
            newTagField.setOnAction(e -> createTagUnderSelection());
            HBox.setHgrow(newTagField, javafx.scene.layout.Priority.ALWAYS);
            HBox addRow = new HBox(8, newTagField, AppButton.secondary("Add", this::createTagUnderSelection));
            getChildren().add(addRow);
        }

        refresh();
    }

    public void refresh() {
        List<Tag> all = library.tags().listAll();
        Map<Integer, List<Tag>> byParent = all.stream()
                .filter(t -> t.parentId() != null)
                .collect(Collectors.groupingBy(Tag::parentId));
        List<Tag> roots = all.stream().filter(t -> t.parentId() == null).toList();

        TreeItem<Tag> hiddenRoot = multiSelect ? new CheckBoxTreeItem<>() : new TreeItem<>();
        for (Tag root : roots) {
            hiddenRoot.getChildren().add(buildNode(root, byParent));
        }
        treeView.setRoot(hiddenRoot);
        collapsed = false;
        collapseToggle.setText("Collapse all");
    }

    private TreeItem<Tag> buildNode(Tag tag, Map<Integer, List<Tag>> byParent) {
        TreeItem<Tag> item;
        if (multiSelect) {
            CheckBoxTreeItem<Tag> checkItem = new CheckBoxTreeItem<>(tag);
            checkItem.setIndependent(true);
            item = checkItem;
        } else {
            item = new TreeItem<>(tag);
        }
        item.setExpanded(true);
        for (Tag child : byParent.getOrDefault(tag.id(), List.of())) {
            item.getChildren().add(buildNode(child, byParent));
        }
        return item;
    }

    /**
     * Collapses/expands every tag's sub-tags, but leaves the root-level tags
     * themselves listed — the hidden tree root (treeView.getRoot(), separate
     * from setShowRoot(false) which only hides its own row) must stay
     * expanded itself, or its children (every root-level tag) disappear too.
     */
    private void toggleCollapse() {
        collapsed = !collapsed;
        collapseToggle.setText(collapsed ? "Expand all" : "Collapse all");
        TreeItem<Tag> root = treeView.getRoot();
        if (root == null) return;
        for (TreeItem<Tag> child : root.getChildren()) {
            setAllExpanded(child, !collapsed);
        }
    }

    private void setAllExpanded(TreeItem<Tag> node, boolean expanded) {
        if (node == null) return;
        node.setExpanded(expanded);
        for (TreeItem<Tag> child : node.getChildren()) {
            setAllExpanded(child, expanded);
        }
    }

    private void createTagUnderSelection() {
        String name = newTagField.getText() == null ? "" : newTagField.getText().trim();
        if (name.isEmpty()) return;
        TreeItem<Tag> selected = treeView.getSelectionModel().getSelectedItem();
        Integer parentId = selected == null || selected.getValue() == null ? null : selected.getValue().id();
        try {
            int newId = library.tags().create(name, parentId);
            newTagField.clear();
            refresh();
            selectTagIds(Set.of(newId));
        } catch (IllegalStateException ex) {
            Dialogs.error("Could not create tag", ex.getMessage());
        }
    }

    public Set<Integer> getSelectedTagIds() {
        if (multiSelect) {
            Set<Integer> ids = new HashSet<>();
            collectChecked(treeView.getRoot(), ids);
            return ids;
        }
        Set<Integer> ids = new HashSet<>();
        for (TreeItem<Tag> item : treeView.getSelectionModel().getSelectedItems()) {
            if (item != null && item.getValue() != null) ids.add(item.getValue().id());
        }
        return ids;
    }

    private void collectChecked(TreeItem<Tag> node, Set<Integer> ids) {
        if (node == null) return;
        if (node instanceof CheckBoxTreeItem<Tag> checkItem && checkItem.isSelected() && node.getValue() != null) {
            ids.add(node.getValue().id());
        }
        for (TreeItem<Tag> child : node.getChildren()) {
            collectChecked(child, ids);
        }
    }

    public void selectTagIds(Set<Integer> tagIds) {
        List<TreeItem<Tag>> toExpand = new ArrayList<>();
        if (multiSelect) {
            applyCheckedRecursive(treeView.getRoot(), tagIds, toExpand);
        } else {
            treeView.getSelectionModel().clearSelection();
            selectRecursive(treeView.getRoot(), tagIds, toExpand);
        }
        for (TreeItem<Tag> item : toExpand) {
            TreeItem<Tag> parent = item.getParent();
            while (parent != null) {
                parent.setExpanded(true);
                parent = parent.getParent();
            }
        }
    }

    private void applyCheckedRecursive(TreeItem<Tag> node, Set<Integer> tagIds, List<TreeItem<Tag>> toExpand) {
        if (node == null) return;
        if (node instanceof CheckBoxTreeItem<Tag> checkItem && node.getValue() != null) {
            boolean shouldBeChecked = tagIds.contains(node.getValue().id());
            checkItem.setSelected(shouldBeChecked);
            if (shouldBeChecked) toExpand.add(node);
        }
        for (TreeItem<Tag> child : node.getChildren()) {
            applyCheckedRecursive(child, tagIds, toExpand);
        }
    }

    private void selectRecursive(TreeItem<Tag> node, Set<Integer> tagIds, List<TreeItem<Tag>> toExpand) {
        if (node == null) return;
        if (node.getValue() != null && tagIds.contains(node.getValue().id())) {
            treeView.getSelectionModel().select(node);
            toExpand.add(node);
        }
        for (TreeItem<Tag> child : node.getChildren()) {
            selectRecursive(child, tagIds, toExpand);
        }
    }

    /** Opens a modal single-tag picker; used by the filter builder to choose one tag per row (no on-the-fly tag creation — you're picking an existing tag to filter by). */
    public static java.util.Optional<Tag> pickOne(LibraryService library) {
        TagPicker picker = new TagPicker(library, false, false);
        javafx.scene.control.Dialog<Tag> dialog = new javafx.scene.control.Dialog<>();
        dialog.setTitle("Choose a tag");
        dialog.getDialogPane().getButtonTypes().addAll(
                javafx.scene.control.ButtonType.OK, javafx.scene.control.ButtonType.CANCEL);
        dialog.getDialogPane().setContent(picker);
        com.sketcher.ui.common.Theme.style(dialog.getDialogPane());
        dialog.setResultConverter(buttonType -> {
            if (buttonType != javafx.scene.control.ButtonType.OK) return null;
            Set<Integer> selected = picker.getSelectedTagIds();
            if (selected.isEmpty()) return null;
            return library.tags().get(selected.iterator().next()).orElse(null);
        });
        return dialog.showAndWait();
    }

    /** Opens a modal multi-tag picker (checkbox per tag) with nothing pre-checked; used by bulk/batch tagging. */
    public static java.util.Optional<Set<Integer>> pickMany(LibraryService library) {
        return pickMany(library, Set.of());
    }

    /** Opens a modal multi-tag picker with {@code preselected} already checked; used for a single image's tags. */
    public static java.util.Optional<Set<Integer>> pickMany(LibraryService library, Set<Integer> preselected) {
        TagPicker picker = new TagPicker(library, true);
        picker.selectTagIds(preselected);
        javafx.scene.control.Dialog<Set<Integer>> dialog = new javafx.scene.control.Dialog<>();
        dialog.setTitle("Choose tags");
        dialog.getDialogPane().getButtonTypes().addAll(
                javafx.scene.control.ButtonType.OK, javafx.scene.control.ButtonType.CANCEL);
        dialog.getDialogPane().setContent(picker);
        com.sketcher.ui.common.Theme.style(dialog.getDialogPane());
        dialog.setResultConverter(buttonType ->
                buttonType == javafx.scene.control.ButtonType.OK ? picker.getSelectedTagIds() : null);
        return dialog.showAndWait();
    }
}
