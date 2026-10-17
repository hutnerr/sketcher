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
 * The shared filter builder (PLAN.md 1.4, 4.2): a favorites toggle, filename
 * search, "not seen since", and a row-per-tag builder with per-row AND/OR
 * and a NOT toggle (the "NOT nude" case). Rows combine left-to-right with
 * no parenthesized groups — the deliberately simple baseline; nested groups
 * are the "advanced mode" the plan calls out as a later extra.
 */
public class FilterBar extends VBox {
    private final LibraryService library;
    private final boolean showNameSearch;
    private final VBox rowsBox = new VBox(4);
    private final List<TagRow> rows = new ArrayList<>();
    private final CheckBox favoritesOnly = new CheckBox("Favorites only");
    private final TextField nameSearch = new TextField();
    private final ChoiceBox<String> notSeenChoice = new ChoiceBox<>();
    private Consumer<Filter> onChange = f -> {
    };

    private static final List<Integer> NOT_SEEN_DAYS = List.of(0, 7, 14, 30, 60, 90);

    public FilterBar(LibraryService library) {
        this(library, true);
    }

    /** showNameSearch is false for the Practice page's copy — filtering a practice segment by filename isn't a meaningful use case there. */
    public FilterBar(LibraryService library, boolean showNameSearch) {
        this.library = library;
        this.showNameSearch = showNameSearch;
        getStyleClass().add("filter-bar");
        setSpacing(8);
        setPadding(new Insets(12));

        nameSearch.setPromptText("Search by filename…");
        nameSearch.textProperty().addListener((o, a, b) -> fireChange());
        favoritesOnly.setOnAction(e -> fireChange());

        notSeenChoice.getItems().add("Not seen: any time");
        for (int d : NOT_SEEN_DAYS) {
            if (d > 0) notSeenChoice.getItems().add("Not seen in " + d + " days");
        }
        notSeenChoice.getSelectionModel().select(0);
        notSeenChoice.getSelectionModel().selectedIndexProperty().addListener((o, a, b) -> fireChange());

        Button addTagRow = new Button("+ Tag filter");
        addTagRow.setOnAction(e -> addRow());

        HBox topRow = new HBox(10, favoritesOnly);
        if (showNameSearch) topRow.getChildren().add(nameSearch);
        topRow.getChildren().addAll(notSeenChoice, addTagRow);

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

    private void addRow(Tag tag, boolean includeDescendants, boolean notFlag, String combinator) {
        TagRow row = new TagRow(tag, rows.isEmpty());
        row.includeDescendants.setSelected(includeDescendants);
        row.exclude.setSelected(notFlag);
        if (combinator != null) row.combinatorBox.getSelectionModel().select(combinator);
        rows.add(row);
        rowsBox.getChildren().add(row);
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

    /**
     * Best-effort inverse of buildFilter() — repopulates the checkboxes/rows
     * to match a Filter built by this same class (e.g. a saved preset being
     * edited). A shape this builder couldn't have produced itself (hand-
     * written, or from an older version) is left unmatched rather than
     * guessed at, so editing never silently fabricates a wrong filter.
     */
    public void loadFilter(Filter filter) {
        if (filter instanceof Filter.All) return;
        List<Filter> conjuncts = filter instanceof Filter.And(List<Filter> children) ? children : List.of(filter);
        for (Filter conjunct : conjuncts) {
            if (conjunct instanceof Filter.FavoritePredicate) {
                favoritesOnly.setSelected(true);
            } else if (conjunct instanceof Filter.NotSeenSincePredicate(long durationMillis)) {
                long days = durationMillis / (24L * 60 * 60 * 1000);
                int idx = NOT_SEEN_DAYS.indexOf((int) days);
                if (idx > 0) notSeenChoice.getSelectionModel().select(idx);
            } else if (showNameSearch && conjunct instanceof Filter.NameContainsPredicate(String text)) {
                nameSearch.setText(text);
            } else if (conjunct instanceof Filter.UntaggedPredicate || conjunct instanceof Filter.NameContainsPredicate) {
                // no longer exposed in this builder's UI — dropped rather than silently kept
            } else {
                loadTagFold(conjunct);
            }
        }
    }

    private void loadTagFold(Filter node) {
        List<TagRowSpec> specs = new ArrayList<>();
        if (!unfoldTagFold(node, specs)) return;
        for (TagRowSpec spec : specs) {
            library.tags().get(spec.tagId()).ifPresent(tag ->
                    addRow(tag, spec.includeDescendants(), spec.notFlag(), spec.combinator()));
        }
    }

    private record TagRowSpec(int tagId, boolean includeDescendants, boolean notFlag, String combinator) {
    }

    private boolean unfoldTagFold(Filter node, List<TagRowSpec> out) {
        if (node instanceof Filter.And(List<Filter> children) && children.size() == 2) {
            return unfoldTagFold(children.get(0), out) && appendTagLeaf(children.get(1), "AND", out);
        }
        if (node instanceof Filter.Or(List<Filter> children) && children.size() == 2) {
            return unfoldTagFold(children.get(0), out) && appendTagLeaf(children.get(1), "OR", out);
        }
        return appendTagLeaf(node, null, out);
    }

    private boolean appendTagLeaf(Filter node, String combinator, List<TagRowSpec> out) {
        boolean notFlag = node instanceof Filter.Not;
        Filter inner = node instanceof Filter.Not(Filter child) ? child : node;
        if (inner instanceof Filter.TagPredicate(int tagId, boolean includeDescendants)) {
            out.add(new TagRowSpec(tagId, includeDescendants, notFlag, combinator));
            return true;
        }
        return false;
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
