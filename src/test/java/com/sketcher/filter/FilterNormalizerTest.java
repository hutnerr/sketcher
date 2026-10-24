package com.sketcher.filter;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FilterNormalizerTest {
    private static final Filter A = new Filter.TagPredicate(1, true);
    private static final Filter B = new Filter.TagPredicate(2, true);
    private static final Filter C = new Filter.TagPredicate(3, true);
    private static final Filter FAV = new Filter.FavoritePredicate();

    @Test
    void nestingIsKeptAsIs() {
        Filter filter = new Filter.Or(List.of(A, new Filter.And(List.of(B, C))));
        assertEquals(filter, FilterNormalizer.normalize(filter));
    }

    /** The old tag-row bar folded left-to-right: ((A AND B) OR C) AND favorites. */
    @Test
    void sameKindNestingIsFlattened() {
        Filter legacy = new Filter.And(List.of(
                new Filter.And(List.of(new Filter.Or(List.of(new Filter.And(List.of(A, B)), C)))), FAV));
        assertEquals(new Filter.And(List.of(new Filter.Or(List.of(new Filter.And(List.of(A, B)), C)), FAV)),
                FilterNormalizer.normalize(legacy));
    }

    @Test
    void notIsPushedDownOntoLeaves() {
        Filter filter = new Filter.Not(new Filter.Or(List.of(A, new Filter.Not(B))));
        assertEquals(new Filter.And(List.of(new Filter.Not(A), B)), FilterNormalizer.normalize(filter));
    }

    @Test
    void allDropsOutOfAndAndAbsorbsOr() {
        assertEquals(A, FilterNormalizer.normalize(new Filter.And(List.of(new Filter.All(), A))));
        assertEquals(new Filter.All(), FilterNormalizer.normalize(new Filter.Or(List.of(new Filter.All(), A))));
    }
}
