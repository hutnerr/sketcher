package com.sketcher.filter;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FilterCodecTest {

    @Test
    void roundTripsSimpleTagPredicate() {
        Filter original = new Filter.TagPredicate(5, true);
        Filter decoded = FilterCodec.decode(FilterCodec.encode(original));
        assertEquals(original, decoded);
    }

    @Test
    void roundTripsNotNudeCase() {
        Filter original = new Filter.And(List.of(
                new Filter.TagPredicate(1, true),
                new Filter.Not(new Filter.TagPredicate(2, true))
        ));
        Filter decoded = FilterCodec.decode(FilterCodec.encode(original));
        assertEquals(original, decoded);
    }

    @Test
    void roundTripsNestedOrGroup() {
        Filter original = new Filter.Or(List.of(
                new Filter.FavoritePredicate(),
                new Filter.UntaggedPredicate(),
                new Filter.NameContainsPredicate("ref")
        ));
        Filter decoded = FilterCodec.decode(FilterCodec.encode(original));
        assertEquals(original, decoded);
    }
}
