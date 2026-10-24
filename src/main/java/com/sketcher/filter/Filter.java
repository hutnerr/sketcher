package com.sketcher.filter;

import java.util.List;

/**
 * Typed filter AST (PLAN.md 1.4). Compiled to SQL by FilterCompiler, and
 * serialized to JSON by FilterCodec for storage in presets/segments.
 */
public sealed interface Filter {

    record All() implements Filter {
    }

    record TagPredicate(int tagId, boolean includeDescendants) implements Filter {
    }

    record FavoritePredicate() implements Filter {
    }

    enum TextMatch { CONTAINS, STARTS_WITH, ENDS_WITH, EQUALS }

    /** Case-insensitive match on the image's original filename. */
    record NamePredicate(TextMatch match, String text) implements Filter {
    }

    record ImportedBetweenPredicate(long fromMillis, long toMillis) implements Filter {
    }

    /** Matches images imported within the last durationMillis — relative, so a saved preset keeps meaning "recent". */
    record ImportedWithinPredicate(long durationMillis) implements Filter {
    }

    /** Matches images not seen within the last durationMillis, or never seen. */
    record NotSeenSincePredicate(long durationMillis) implements Filter {
    }

    record UntaggedPredicate() implements Filter {
    }

    enum Comparison { LT, LE, EQ, GE, GT }

    /** Compares how many times the image has been shown in practice. */
    record SeenCountPredicate(Comparison comparison, long count) implements Filter {
    }

    /** Lower-case extension without the dot; "jpg" also matches "jpeg". */
    record FormatPredicate(String format) implements Filter {
    }

    enum Orientation { PORTRAIT, LANDSCAPE, SQUARE }

    /** SQUARE allows a 2% difference between sides, so a 1000x990 scan still counts; the other two exclude that band. */
    record OrientationPredicate(Orientation orientation) implements Filter {
    }

    record And(List<Filter> children) implements Filter {
    }

    record Or(List<Filter> children) implements Filter {
    }

    record Not(Filter child) implements Filter {
    }
}
