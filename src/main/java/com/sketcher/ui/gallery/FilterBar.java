package com.sketcher.ui.gallery;

import com.sketcher.filter.Filter;
import com.sketcher.library.LibraryService;
import com.sketcher.ui.components.AppButton;
import com.sketcher.ui.filter.ConditionBuilder;
import javafx.geometry.Pos;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.function.Consumer;

/**
 * The shared filter bar (PLAN.md 1.4, 4.2): an optional quick filename
 * search plus the {@link ConditionBuilder}. Used by the Gallery and by the
 * Practice setup's "Images matching" row, so a query built in one reads the
 * same in the other.
 */
public class FilterBar extends VBox {
    private final boolean showNameSearch;
    private final TextField nameSearch = new TextField();
    private final ConditionBuilder conditions;
    private final AppButton clearButton;
    private Consumer<Filter> onChange = f -> {
    };

    public FilterBar(LibraryService library) {
        this(library, true);
    }

    /** showNameSearch is false for the Practice page's copy — a quick filename box isn't useful there (a "File name" condition still is). */
    public FilterBar(LibraryService library, boolean showNameSearch) {
        this.showNameSearch = showNameSearch;
        getStyleClass().add("filter-bar");
        setSpacing(12);

        conditions = new ConditionBuilder(library);
        conditions.setOnChange(this::fireChange);

        nameSearch.setPromptText("Search by filename…");
        nameSearch.setPrefWidth(280);
        nameSearch.setMinWidth(160);
        nameSearch.textProperty().addListener((o, a, b) -> fireChange());

        clearButton = AppButton.ghost("Clear conditions", conditions::clear).small();

        // Wraps onto a second line instead of forcing the page wider in a narrow window.
        FlowPane topRow = new FlowPane(12, 10);
        topRow.setAlignment(Pos.CENTER_LEFT);
        if (showNameSearch) topRow.getChildren().add(nameSearch);
        topRow.getChildren().addAll(
                AppButton.secondary("+ Add condition", conditions::addCondition),
                clearButton);
        updateClearButton();

        getChildren().addAll(topRow, conditions);
    }

    public void setOnChange(Consumer<Filter> onChange) {
        this.onChange = onChange;
    }

    private void fireChange() {
        updateClearButton();
        onChange.accept(buildFilter());
    }

    private void updateClearButton() {
        clearButton.setVisible(!conditions.isEmpty());
        clearButton.setManaged(!conditions.isEmpty());
    }

    /** Shows this filter as rows (e.g. a saved preset being edited). */
    public void loadFilter(Filter filter) {
        conditions.load(filter);
        fireChange();
    }

    public Filter buildFilter() {
        Filter built = conditions.buildFilter();
        String search = showNameSearch ? nameSearch.getText().trim() : "";
        if (search.isEmpty()) return built;
        Filter name = new Filter.NamePredicate(Filter.TextMatch.CONTAINS, search);
        return built instanceof Filter.All ? name : new Filter.And(List.of(name, built));
    }
}
