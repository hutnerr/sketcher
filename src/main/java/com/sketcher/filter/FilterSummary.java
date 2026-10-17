package com.sketcher.filter;

import java.util.function.IntFunction;
import java.util.stream.Collectors;

/** Renders a Filter as a short human-readable string for filter-bar chips and HUDs. */
public class FilterSummary {

    public static String describe(Filter filter, IntFunction<String> tagName) {
        return switch (filter) {
            case Filter.All ignored -> "all images";
            case Filter.FavoritePredicate ignored -> "favorites";
            case Filter.UntaggedPredicate ignored -> "untagged";
            case Filter.NameContainsPredicate p -> "name contains \"" + p.text() + "\"";
            case Filter.ImportedBetweenPredicate p -> "imported in range";
            case Filter.NotSeenSincePredicate p -> "not seen recently";
            case Filter.TagPredicate p -> "tag:" + tagName.apply(p.tagId()) + (p.includeDescendants() ? "+" : "");
            case Filter.Not n -> "NOT " + describe(n.child(), tagName);
            case Filter.And a -> a.children().stream()
                    .map(c -> describe(c, tagName))
                    .collect(Collectors.joining(" AND "));
            case Filter.Or o -> o.children().stream()
                    .map(c -> describe(c, tagName))
                    .collect(Collectors.joining(" OR "));
        };
    }
}
