package com.sketcher.ui.gallery;

import com.sketcher.filter.Filter;
import com.sketcher.library.LibraryService;
import com.sketcher.model.Tag;
import com.sketcher.ui.tags.TagPicker;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The shared filter builder (PLAN.md 1.4, 4.2): favorites/untagged toggles,
 * name search, "not seen since", and a row-per-tag builder with per-row
 * AND/OR and a NOT toggle (the "NOT nude" case). Rows combine left-to-right
 * with no parenthesized groups — the deliberately simple baseline; nested
 * groups are the "advanced mode" the plan calls out as a later extra.
 */
public class FilterBar extends VBox {
    private final LibraryService library;
    private final VBox rowsBox = new VBox(4);
    private final List<TagRow> rows = new ArrayList<>();
    private final CheckBox favoritesOnly = new CheckBox("Favorites only");
    private final CheckBox untaggedOnly = new CheckBox("Untagged only");
    private final TextField nameSearch = new TextField();
    private final ChoiceBox<String> notSeenChoice = new ChoiceBox<>();
    private Consumer<Filter> onChange = f -> {
    };

    private static final List<Integer> NOT_SEEN_DAYS = List.of(0, 7, 14, 30, 60, 90);

    public FilterBar(LibraryService library) {
        this.library = library;
        getStyleClass().add("filter-bar");
        setSpacing(8);
        setPadding(new Insets(12));

        nameSearch.setPromptText("Search name…");
        nameSearch.textProperty().addListener((o, a, b) -> fireChange());
        favoritesOnly.setOnAction(e -> fireChange());
        untaggedOnly.setOnAction(e -> fireChange());

        notSeenChoice.getItems().add("Not seen: any time");
        for (int d : NOT_SEEN_DAYS) {
            if (d > 0) notSeenChoice.getItems().add("Not seen in " + d + " days");
        }
        notSeenChoice.getSelectionModel().select(0);
        notSeenChoice.getSelectionModel().selectedIndexProperty().addListener((o, a, b) -> fireChange());

        Button addTagRow = new Button("+ Tag filter");
        addTagRow.setOnAction(e -> addRow());

        HBox topRow = new HBox(10, favoritesOnly, untaggedOnly, nameSearch, notSeenChoice, addTagRow);
        topRow.setSpacing(10);

        getChildren().addAll(topRow, rowsBox);
    }

    public void setOnChange(Consumer<Filter> onChange) {
        this.onChange = onChange;
    }

    private void addRow() {
        TagPicker.pickOne(library).ifPresent(tag -> {
            TagRow row = new TagRow(tag, rows.isEmpty());
            rows.add(row);
            rowsBox.getChildren().add(row);
            fireChange();
        });
    }

    private void removeRow(TagRow row) {
        rows.remove(row);
        rowsBox.getChildren().remove(row);
        if (!rows.isEmpty()) {
            rows.get(0).combinatorBox.setVisible(false);
            rows.get(0).combinatorBox.setManaged(false);
        }
        fireChange();
    }

    private void fireChange() {
        onChange.accept(buildFilter());
    }

    public Filter buildFilter() {
        Filter tagFold = null;
        for (TagRow row : rows) {
            Filter f = new Filter.TagPredicate(row.tag.id(), row.includeDescendants.isSelected());
            if (row.exclude.isSelected()) f = new Filter.Not(f);
            if (tagFold == null) {
                tagFold = f;
            } else if ("OR".equals(row.combinatorBox.getValue())) {
                tagFold = new Filter.Or(List.of(tagFold, f));
            } else {
                tagFold = new Filter.And(List.of(tagFold, f));
            }
        }

        List<Filter> extras = new ArrayList<>();
        if (tagFold != null) extras.add(tagFold);
        if (favoritesOnly.isSelected()) extras.add(new Filter.FavoritePredicate());
        if (untaggedOnly.isSelected()) extras.add(new Filter.UntaggedPredicate());
        if (!nameSearch.getText().isBlank()) extras.add(new Filter.NameContainsPredicate(nameSearch.getText().trim()));
        int notSeenIndex = notSeenChoice.getSelectionModel().getSelectedIndex();
        if (notSeenIndex > 0) {
            long days = NOT_SEEN_DAYS.get(notSeenIndex);
            extras.add(new Filter.NotSeenSincePredicate(days * 24L * 60 * 60 * 1000));
        }

        if (extras.isEmpty()) return new Filter.All();
        if (extras.size() == 1) return extras.get(0);
        return new Filter.And(extras);
    }

    private class TagRow extends HBox {
        final Tag tag;
        final ChoiceBox<String> combinatorBox = new ChoiceBox<>();
        final CheckBox includeDescendants = new CheckBox("+sub-tags");
        final ToggleButton exclude = new ToggleButton("NOT");

        TagRow(Tag tag, boolean first) {
            this.tag = tag;
            setSpacing(8);
            combinatorBox.getItems().addAll("AND", "OR");
            combinatorBox.getSelectionModel().select("AND");
            combinatorBox.setVisible(!first);
            combinatorBox.setManaged(!first);
            combinatorBox.getSelectionModel().selectedIndexProperty().addListener((o, a, b) -> fireChange());

            Label label = new Label(library.tags().fullPath(tag.id()));
            label.setPrefWidth(200);

            includeDescendants.setSelected(true);
            includeDescendants.setOnAction(e -> fireChange());
            exclude.setOnAction(e -> fireChange());

            Button removeButton = new Button("x");
            removeButton.setOnAction(e -> removeRow(this));

            getChildren().addAll(combinatorBox, label, includeDescendants, exclude, removeButton);
        }
    }
}
