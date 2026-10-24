package com.sketcher.filter;

import com.sketcher.filter.Condition.Field;
import com.sketcher.filter.Condition.Operator;
import com.sketcher.filter.Condition.Value;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConditionTest {

    /** Every operator of every field, built then parsed, must come back as the same row. */
    @Test
    void everyRowRoundTrips() {
        for (Field field : Field.values()) {
            for (Operator op : field.operators) {
                Value value = sampleValue(field, op);
                Filter leaf = Condition.build(field, op, value).orElseThrow(() -> new AssertionError(field + " " + op));
                Condition.Spec spec = Condition.parse(leaf).orElseThrow(() -> new AssertionError(field + " " + op));
                assertEquals(field, spec.field(), field + " " + op);
                assertEquals(op, spec.operator(), field + " " + op);
                assertEquals(leaf, Condition.build(spec.field(), spec.operator(), spec.value()).orElseThrow());
            }
        }
    }

    @Test
    void incompleteRowsBuildNothing() {
        assertEquals(Optional.empty(), Condition.build(Field.TAG, Operator.IS, Value.empty()));
        assertEquals(Optional.empty(), Condition.build(Field.TIMES_SEEN, Operator.AT_LEAST, Value.text("")));
        assertEquals(Optional.empty(), Condition.build(Field.IMPORTED, Operator.BETWEEN,
                Value.dates(LocalDate.of(2026, 1, 1), null)));
    }

    @Test
    void negatedComparisonsFlip() {
        Filter notLess = new Filter.Not(new Filter.SeenCountPredicate(Filter.Comparison.LT, 10));
        assertEquals(Operator.AT_LEAST, Condition.parse(notLess).orElseThrow().operator());
    }

    @Test
    void unexpressibleLeavesDoNotParse() {
        assertTrue(Condition.parse(new Filter.Not(new Filter.NamePredicate(Filter.TextMatch.STARTS_WITH, "x"))).isEmpty());
    }

    private static Value sampleValue(Field field, Operator op) {
        return switch (field.valueKind(op)) {
            case NONE -> Value.empty();
            case TAG -> Value.tag(7, false);
            case TEXT -> Value.text("hand");
            case INTEGER, DAYS -> Value.text("12");
            case DATE -> Value.dates(LocalDate.of(2026, 3, 4), null);
            case DATE_RANGE -> Value.dates(LocalDate.of(2026, 3, 4), LocalDate.of(2026, 5, 6));
            case FORMAT -> Value.text("gif");
            case ORIENTATION -> Value.text("LANDSCAPE");
        };
    }
}
