package com.sketcher.ui.tags;

import com.sketcher.library.LibraryService;
import com.sketcher.model.Tag;
import com.sketcher.ui.common.Dialogs;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
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
 * Reusable tag tree selector: single or multi-select, with create-on-the-fly
 * (PLAN.md 4.1 import tagging, 4.3 tag management). Used by import batch
 * tagging, the gallery filter bar, bulk tagging, and the tag manager.
 *
 * Multi-select uses a checkbox per row rather than relying on the TreeView's
 * own MULTIPLE selection mode (which needs Ctrl/Shift+click and isn't
 * discoverable — a plain click would just replace the whole selection).
 */
public class TagPicker extends VBox {
    private final LibraryService library;
    private final boolean multiSelect;
    private final TreeView<Tag> treeView = new TreeView<>();
    private final TextField newTagField = new TextField();

    public TagPicker(LibraryService library, boolean multiSelect) {
        this.library = library;
        this.multiSelect = multiSelect;
        setSpacing(6);

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
        treeView.setPrefHeight(260);

        newTagField.setPromptText("New tag name");
        Button addButton = new Button("Add");
        addButton.setOnAction(e -> createTagUnderSelection());
        HBox addRow = new HBox(6, newTagField, addButton);
        addRow.setPadding(new Insets(4, 0, 0, 0));

        getChildren().addAll(treeView, addRow);
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
        for (Tag child : byParent.getOrDefault(tag.id(), List.of())) {
            item.getChildren().add(buildNode(child, byParent));
        }
        return item;
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

    /** Opens a modal single-tag picker; used by the filter builder to choose one tag per row. */
    public static java.util.Optional<Tag> pickOne(LibraryService library) {
        TagPicker picker = new TagPicker(library, false);
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
