package com.sketcher.filter;

import java.util.ArrayList;
import java.util.List;

/**
 * Rewrites a Filter into the shape the condition builder draws: every Not
 * sits directly on a leaf (De Morgan), and no And/Or has a child of its own
 * kind. The result means exactly the same thing; it's just a tree whose
 * levels alternate and/or, so each level is one bracketed group of rows.
 */
public final class FilterNormalizer {

    private FilterNormalizer() {
    }

    public static Filter normalize(Filter filter) {
        return simplify(negationNormal(filter, false));
    }

    private static Filter negationNormal(Filter f, boolean negate) {
        return switch (f) {
            case Filter.Not(Filter child) -> negationNormal(child, !negate);
            case Filter.And(List<Filter> children) -> {
                List<Filter> mapped = children.stream().map(c -> negationNormal(c, negate)).toList();
                yield negate ? new Filter.Or(mapped) : new Filter.And(mapped);
            }
            case Filter.Or(List<Filter> children) -> {
                List<Filter> mapped = children.stream().map(c -> negationNormal(c, negate)).toList();
                yield negate ? new Filter.And(mapped) : new Filter.Or(mapped);
            }
            default -> negate ? new Filter.Not(f) : f;
        };
    }

    /** Flattens And-in-And / Or-in-Or and unwraps single children; All drops out of an And and absorbs an Or. */
    private static Filter simplify(Filter f) {
        boolean isAnd = f instanceof Filter.And;
        List<Filter> children = switch (f) {
            case Filter.And(List<Filter> c) -> c;
            case Filter.Or(List<Filter> c) -> c;
            default -> null;
        };
        if (children == null) return f;

        List<Filter> flat = new ArrayList<>();
        for (Filter child : children) {
            Filter s = simplify(child);
            if (s instanceof Filter.All) {
                if (isAnd) continue;
                return new Filter.All();
            }
            if (isAnd && s instanceof Filter.And(List<Filter> grandchildren)) flat.addAll(grandchildren);
            else if (!isAnd && s instanceof Filter.Or(List<Filter> grandchildren)) flat.addAll(grandchildren);
            else flat.add(s);
        }
        if (flat.isEmpty()) return new Filter.All();
        if (flat.size() == 1) return flat.get(0);
        return isAnd ? new Filter.And(flat) : new Filter.Or(flat);
    }
}
