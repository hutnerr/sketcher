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

    record NameContainsPredicate(String text) implements Filter {
    }

    record ImportedBetweenPredicate(long fromMillis, long toMillis) implements Filter {
    }

    /** Matches images not seen within the last durationMillis, or never seen. */
    record NotSeenSincePredicate(long durationMillis) implements Filter {
    }

    record UntaggedPredicate() implements Filter {
    }

    record And(List<Filter> children) implements Filter {
    }

    record Or(List<Filter> children) implements Filter {
    }

    record Not(Filter child) implements Filter {
    }
}
