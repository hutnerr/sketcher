package com.sketcher.ui.filter;

import com.sketcher.filter.Condition;
import com.sketcher.filter.Filter;
import com.sketcher.filter.FilterNormalizer;
import com.sketcher.filter.FilterSummary;
import com.sketcher.library.LibraryService;
import com.sketcher.ui.components.Chip;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * ServiceNow-style condition builder (PLAN.md 1.4 "advanced mode"): rows of
 * field · operator · value, arranged as a tree of and/or groups.
 *
 * A row's "and"/"or" button adds a sibling when its group already uses that
 * connector; otherwise it brackets that row and the new one into a nested
 * group. So in "x or y", pressing "and" on y gives "x or (y and z)" — the
 * new condition binds to the row you pressed it on, and the bracket shows
 * it. Removing rows collapses any group left with a single child.
 *
 * Rows whose value isn't filled in yet are ignored, so the filter never
 * flickers to "nothing matches" while you're still typing.
 */
public class ConditionBuilder extends VBox {
    private final LibraryService library;
    private Group root = new Group(false);
    private final VBox treeBox = new VBox();
    private final HBox rawBox = new HBox(8);
    /** A loaded filter with a leaf no row can express; kept whole (AND'd in) rather than silently changed. */
    private Filter rawFilter;
    private Runnable onChange = () -> {
    };

    /** One and/or level. Children are ConditionRows or nested Groups, never a Group with the same connector. */
    private static final class Group {
        boolean or;
        final List<Object> children = new ArrayList<>();

        Group(boolean or) {
            this.or = or;
        }
    }

    public ConditionBuilder(LibraryService library) {
        this.library = library;
        getStyleClass().add("condition-builder");
        setSpacing(10);
        rawBox.setAlignment(Pos.CENTER_LEFT);
        getChildren().addAll(rawBox, treeBox);
        render();
    }

    public void setOnChange(Runnable onChange) {
        this.onChange = onChange;
    }

    public boolean isEmpty() {
        return root.children.isEmpty() && rawFilter == null;
    }

    /** "+ Add condition": narrows the whole query, so an existing top-level OR gets bracketed first. */
    public void addCondition() {
        ConditionRow row = newRow();
        if (root.or && root.children.size() > 1) {
            Group previous = new Group(true);
            previous.children.addAll(root.children);
            root.children.clear();
            root.children.add(previous);
        }
        if (root.children.size() <= 1) root.or = false;
        root.children.add(row);
        render();
        row.focus();
        // The filter itself is unchanged (a blank row is ignored), but listeners may track emptiness.
        onChange.run();
    }

    public void clear() {
        root = new Group(false);
        setRaw(null);
        render();
        onChange.run();
    }

    /** Replaces the rows with ones that mean exactly this filter. */
    public void load(Filter filter) {
        root = new Group(false);
        setRaw(null);
        Filter normalized = FilterNormalizer.normalize(filter);
        if (!(normalized instanceof Filter.All)) {
            Object node = toNode(normalized);
            if (node == null) {
                setRaw(filter);
            } else if (node instanceof Group group) {
                root = group;
            } else {
                root.children.add(node);
            }
        }
        render();
    }

    /** A Group or ConditionRow for this normalized filter, or null if some leaf has no row form. */
    private Object toNode(Filter f) {
        List<Filter> children = switch (f) {
            case Filter.And(List<Filter> c) -> c;
            case Filter.Or(List<Filter> c) -> c;
            default -> null;
        };
        if (children == null) {
            Optional<Condition.Spec> spec = Condition.parse(f);
            if (spec.isEmpty()) return null;
            ConditionRow row = newRow();
            row.load(spec.get());
            return row;
        }
        Group group = new Group(f instanceof Filter.Or);
        for (Filter child : children) {
            Object node = toNode(child);
            if (node == null) return null;
            group.children.add(node);
        }
        return group;
    }

    public Filter buildFilter() {
        Filter built = build(root).orElse(new Filter.All());
        if (rawFilter == null) return built;
        return built instanceof Filter.All ? rawFilter : new Filter.And(List.of(rawFilter, built));
    }

    private static Optional<Filter> build(Object node) {
        if (node instanceof ConditionRow row) return row.buildFilter();
        Group group = (Group) node;
        List<Filter> parts = new ArrayList<>();
        for (Object child : group.children) build(child).ifPresent(parts::add);
        if (parts.isEmpty()) return Optional.empty();
        if (parts.size() == 1) return Optional.of(parts.get(0));
        return Optional.of(group.or ? new Filter.Or(parts) : new Filter.And(parts));
    }

    private ConditionRow newRow() {
        return new ConditionRow(library, () -> onChange.run(), new ConditionRow.Actions() {
            @Override
            public void and(ConditionRow source) {
                insertNextTo(source, false);
            }

            @Override
            public void or(ConditionRow source) {
                insertNextTo(source, true);
            }

            @Override
            public void remove(ConditionRow source) {
                ParentSlot slot = find(root, source);
                if (slot == null) return;
                slot.group.children.remove(slot.index);
                tidy(root);
                render();
                onChange.run();
            }
        });
    }

    private void insertNextTo(ConditionRow source, boolean or) {
        ParentSlot slot = find(root, source);
        if (slot == null) return;
        Group group = slot.group;
        ConditionRow row = newRow();
        if (group.children.size() == 1) group.or = or;
        if (group.or == or) {
            group.children.add(slot.index + 1, row);
        } else {
            Group bracket = new Group(or);
            bracket.children.add(source);
            bracket.children.add(row);
            group.children.set(slot.index, bracket);
        }
        render();
        row.focus();
        onChange.run();
    }

    private record ParentSlot(Group group, int index) {
    }

    private static ParentSlot find(Group group, ConditionRow row) {
        for (int i = 0; i < group.children.size(); i++) {
            Object child = group.children.get(i);
            if (child == row) return new ParentSlot(group, i);
            if (child instanceof Group nested) {
                ParentSlot found = find(nested, row);
                if (found != null) return found;
            }
        }
        return null;
    }

    /** Drops empty groups, unwraps single-child groups, and merges a group into a parent with the same connector. */
    private void tidy(Group group) {
        List<Object> tidied = new ArrayList<>();
        for (Object child : group.children) {
            if (child instanceof Group nested) {
                tidy(nested);
                if (nested.children.isEmpty()) continue;
                if (nested.children.size() == 1) {
                    child = nested.children.get(0);
                } else if (nested.or == group.or) {
                    tidied.addAll(nested.children);
                    continue;
                }
            }
            tidied.add(child);
        }
        group.children.clear();
        group.children.addAll(tidied);
        if (group == root && root.children.size() == 1 && root.children.get(0) instanceof Group only) {
            root = only;
        }
    }

    private void render() {
        treeBox.getChildren().setAll(renderGroup(root));
        boolean hasRows = !root.children.isEmpty();
        treeBox.setVisible(hasRows);
        treeBox.setManaged(hasRows);
        rawBox.setVisible(rawFilter != null);
        rawBox.setManaged(rawFilter != null);
        setVisible(!isEmpty());
        setManaged(!isEmpty());
    }

    /** Each child gets a lead word — "Where" first, then the group's connector — and nested groups get a bracket. */
    private VBox renderGroup(Group group) {
        VBox box = new VBox(6);
        for (int i = 0; i < group.children.size(); i++) {
            Object child = group.children.get(i);
            Label lead = new Label(i == 0 ? "Where" : group.or ? "or" : "and");
            lead.getStyleClass().add("condition-lead");
            Node content;
            if (child instanceof Group nested) {
                VBox nestedBox = renderGroup(nested);
                nestedBox.getStyleClass().add("condition-group");
                content = nestedBox;
                lead.setAlignment(Pos.TOP_LEFT);
                lead.getStyleClass().add("condition-lead-group");
            } else {
                content = (ConditionRow) child;
            }
            HBox.setHgrow(content, Priority.ALWAYS);
            HBox slot = new HBox(0, lead, content);
            slot.setAlignment(child instanceof Group ? Pos.TOP_LEFT : Pos.CENTER_LEFT);
            box.getChildren().add(slot);
        }
        return box;
    }

    private void setRaw(Filter filter) {
        rawFilter = filter;
        rawBox.getChildren().clear();
        if (filter != null) {
            String text = FilterSummary.describe(filter, id -> library.tags().get(id).map(t -> t.name()).orElse("?"));
            rawBox.getChildren().addAll(
                    new Chip(text, () -> {
                        setRaw(null);
                        render();
                        onChange.run();
                    }).neutral(),
                    hint("Saved filter that can't be shown as rows — kept as is."));
        }
    }

    private static Label hint(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("hint-label");
        label.setWrapText(true);
        return label;
    }
}
