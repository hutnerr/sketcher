package com.sketcher.filter;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Locale;
import java.util.function.IntFunction;
import java.util.stream.Collectors;

/** Renders a Filter as a short human-readable string for filter-bar chips and HUDs. */
public class FilterSummary {
    private static final long DAY_MILLIS = 24L * 60 * 60 * 1000;

    public static String describe(Filter filter, IntFunction<String> tagName) {
        return switch (filter) {
            case Filter.All ignored -> "all images";
            case Filter.FavoritePredicate ignored -> "favorites";
            case Filter.UntaggedPredicate ignored -> "untagged";
            case Filter.NamePredicate p -> "name " + switch (p.match()) {
                case CONTAINS -> "contains";
                case STARTS_WITH -> "starts with";
                case ENDS_WITH -> "ends with";
                case EQUALS -> "is";
            } + " \"" + p.text() + "\"";
            case Filter.ImportedBetweenPredicate p -> describeImportedBetween(p);
            case Filter.ImportedWithinPredicate p -> "imported in last " + days(p.durationMillis()) + "d";
            case Filter.NotSeenSincePredicate p -> "not seen in " + days(p.durationMillis()) + "d";
            case Filter.SeenCountPredicate p -> describeSeenCount(p);
            case Filter.FormatPredicate p -> p.format().toUpperCase(Locale.ROOT);
            case Filter.OrientationPredicate p -> p.orientation().name().toLowerCase(Locale.ROOT);
            case Filter.TagPredicate p -> "tag:" + tagName.apply(p.tagId()) + (p.includeDescendants() ? "+" : "");
            case Filter.Not n -> "NOT " + describe(n.child(), tagName);
            case Filter.And a -> a.children().stream()
                    .map(c -> group(c, tagName))
                    .collect(Collectors.joining(" AND "));
            case Filter.Or o -> o.children().stream()
                    .map(c -> group(c, tagName))
                    .collect(Collectors.joining(" OR "));
        };
    }

    /** Parenthesizes nested AND/OR so "A AND (B OR C)" doesn't read as "A AND B OR C". */
    private static String group(Filter child, IntFunction<String> tagName) {
        String text = describe(child, tagName);
        return child instanceof Filter.And || child instanceof Filter.Or ? "(" + text + ")" : text;
    }

    private static String describeSeenCount(Filter.SeenCountPredicate p) {
        String op = switch (p.comparison()) {
            case LT -> "<";
            case LE -> "≤";
            case EQ -> "=";
            case GE -> "≥";
            case GT -> ">";
        };
        return "seen " + op + " " + p.count() + "×";
    }

    private static String describeImportedBetween(Filter.ImportedBetweenPredicate p) {
        boolean openStart = p.fromMillis() <= 0;
        boolean openEnd = p.toMillis() == Long.MAX_VALUE;
        if (openStart && openEnd) return "imported any time";
        if (openEnd) return "imported since " + date(p.fromMillis());
        if (openStart) return "imported before " + date(p.toMillis() + 1);
        return "imported " + date(p.fromMillis()) + "–" + date(p.toMillis());
    }

    private static long days(long millis) {
        return millis / DAY_MILLIS;
    }

    private static LocalDate date(long millis) {
        return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate();
    }
}
