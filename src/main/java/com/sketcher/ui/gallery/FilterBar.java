package com.sketcher.ui.gallery;

import com.sketcher.filter.Filter;
import com.sketcher.library.LibraryService;
import com.sketcher.model.Tag;
import com.sketcher.ui.components.AppButton;
import com.sketcher.ui.components.SegmentedControl;
import com.sketcher.ui.tags.TagPicker;
import javafx.geometry.Pos;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
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
    private final VBox rowsBox = new VBox(8);
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
        setSpacing(12);

        nameSearch.setPromptText("Search by filename…");
        nameSearch.setPrefWidth(280);
        nameSearch.setMinWidth(160);
        favoritesOnly.setMinWidth(USE_PREF_SIZE);
        notSeenChoice.setMinWidth(USE_PREF_SIZE);
        nameSearch.textProperty().addListener((o, a, b) -> fireChange());
        favoritesOnly.setOnAction(e -> fireChange());

        notSeenChoice.getItems().add("Not seen: any time");
        for (int d : NOT_SEEN_DAYS) {
            if (d > 0) notSeenChoice.getItems().add("Not seen in " + d + " days");
        }
        notSeenChoice.getSelectionModel().select(0);
        notSeenChoice.getSelectionModel().selectedIndexProperty().addListener((o, a, b) -> fireChange());

        AppButton addTagRow = AppButton.secondary("+ Tag filter", this::addRow);

        // Wraps onto a second line instead of forcing the page wider in a narrow window.
        FlowPane topRow = new FlowPane(12, 10);
        topRow.setAlignment(Pos.CENTER_LEFT);
        if (showNameSearch) topRow.getChildren().add(nameSearch);
        topRow.getChildren().addAll(notSeenChoice, favoritesOnly, addTagRow);

        rowsBox.setVisible(false);
        rowsBox.setManaged(false);
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
            updateRowsVisibility();
            fireChange();
        });
    }

    private void addRow(Tag tag, boolean includeDescendants, boolean notFlag, String combinator) {
        TagRow row = new TagRow(tag, rows.isEmpty());
        row.includeDescendants.setSelected(includeDescendants);
        row.exclude.setSelected(notFlag);
        if (combinator != null) row.combinator.setValue(combinator);
        rows.add(row);
        rowsBox.getChildren().add(row);
        updateRowsVisibility();
    }

    private void updateRowsVisibility() {
        rowsBox.setVisible(!rows.isEmpty());
        rowsBox.setManaged(!rows.isEmpty());
    }

    private void removeRow(TagRow row) {
        rows.remove(row);
        rowsBox.getChildren().remove(row);
        if (!rows.isEmpty()) {
            rows.get(0).combinator.setVisible(false);
            rows.get(0).combinator.setManaged(false);
            rows.get(0).leadLabel.setVisible(true);
            rows.get(0).leadLabel.setManaged(true);
        }
        updateRowsVisibility();
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
            } else if ("OR".equals(row.combinator.getValue())) {
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

    /**
     * One tag term: "Where / AND / OR", the tag, include-sub-tags, an
     * Include/Exclude switch (the NOT toggle), and remove. Sits on a tinted
     * rounded strip so each term reads as one unit.
     */
    private class TagRow extends HBox {
        final Tag tag;
        final SegmentedControl<String> combinator = new SegmentedControl<String>()
                .option("AND", "And")
                .option("OR", "Or");
        final Label leadLabel = new Label("Where");
        final CheckBox includeDescendants = new CheckBox("Include sub-tags");
        final ToggleButton exclude = AppButton.small(AppButton.toggle("Exclude", AppButton.Variant.SECONDARY));

        TagRow(Tag tag, boolean first) {
            this.tag = tag;
            getStyleClass().add("filter-row");
            setSpacing(14);
            setAlignment(Pos.CENTER_LEFT);
            combinator.setVisible(!first);
            combinator.setManaged(!first);
            combinator.onChange(v -> fireChange());
            leadLabel.getStyleClass().add("muted-label");
            leadLabel.setMinWidth(USE_PREF_SIZE);
            leadLabel.setVisible(first);
            leadLabel.setManaged(first);

            Label label = new Label(library.tags().fullPath(tag.id()));
            label.getStyleClass().add("filter-row-tag");
            label.setMinWidth(120);
            HBox.setHgrow(label, Priority.ALWAYS);
            label.setMaxWidth(Double.MAX_VALUE);

            includeDescendants.setSelected(true);
            includeDescendants.setMinWidth(USE_PREF_SIZE);
            includeDescendants.setOnAction(e -> fireChange());
            exclude.setOnAction(e -> fireChange());

            getChildren().addAll(leadLabel, combinator, label, includeDescendants, exclude,
                    AppButton.icon("✕", "Remove this filter", () -> removeRow(this)));
        }
    }
}
