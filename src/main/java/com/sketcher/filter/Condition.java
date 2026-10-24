package com.sketcher.filter;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The condition builder's vocabulary: one row is "field, operator, value"
 * (e.g. "Times seen · less than · 3 times"), and maps to exactly one Criteria leaf
 * — a predicate, or Not of one. Pure model, no JavaFX, so the mapping in both
 * directions is unit-testable.
 */
public final class Condition {
    private static final long DAY_MILLIS = 24L * 60 * 60 * 1000;

    private Condition() {
    }

    public enum Operator {
        IS("is"), IS_NOT("is not"),
        CONTAINS("contains"), NOT_CONTAINS("does not contain"), STARTS_WITH("starts with"), ENDS_WITH("ends with"),
        IS_EMPTY("is empty"), IS_NOT_EMPTY("is not empty"),
        IS_TRUE("is true"), IS_FALSE("is false"),
        GREATER_THAN("greater than"), AT_LEAST("at least"), LESS_THAN("less than"), AT_MOST("at most"),
        IN_LAST("in the last"), NOT_IN_LAST("not in the last"),
        ON_OR_AFTER("on or after"), BEFORE("before"), BETWEEN("between");

        public final String label;

        Operator(String label) {
            this.label = label;
        }
    }

    /** What kind of editor a row shows after its operator. */
    public enum ValueKind { NONE, TAG, TEXT, INTEGER, DAYS, DATE, DATE_RANGE, FORMAT, ORIENTATION }

    public enum Field {
        TAG("Tag", List.of(Operator.IS, Operator.IS_NOT, Operator.IS_EMPTY, Operator.IS_NOT_EMPTY)),
        NAME("File name", List.of(Operator.CONTAINS, Operator.NOT_CONTAINS, Operator.STARTS_WITH,
                Operator.ENDS_WITH, Operator.IS, Operator.IS_NOT)),
        FAVORITE("Favorite", List.of(Operator.IS_TRUE, Operator.IS_FALSE)),
        ORIENTATION("Orientation", List.of(Operator.IS, Operator.IS_NOT)),
        FORMAT("Format", List.of(Operator.IS, Operator.IS_NOT)),
        IMPORTED("Imported", List.of(Operator.IN_LAST, Operator.NOT_IN_LAST, Operator.ON_OR_AFTER,
                Operator.BEFORE, Operator.BETWEEN)),
        LAST_SEEN("Last seen", List.of(Operator.IN_LAST, Operator.NOT_IN_LAST)),
        TIMES_SEEN("Times seen", List.of(Operator.IS, Operator.IS_NOT,
                Operator.GREATER_THAN, Operator.AT_LEAST, Operator.LESS_THAN, Operator.AT_MOST));

        public final String label;
        public final List<Operator> operators;

        Field(String label, List<Operator> operators) {
            this.label = label;
            this.operators = operators;
        }

        public ValueKind valueKind(Operator op) {
            return switch (op) {
                case IS_EMPTY, IS_NOT_EMPTY, IS_TRUE, IS_FALSE -> ValueKind.NONE;
                case IN_LAST, NOT_IN_LAST -> ValueKind.DAYS;
                case ON_OR_AFTER, BEFORE -> ValueKind.DATE;
                case BETWEEN -> ValueKind.DATE_RANGE;
                default -> switch (this) {
                    case TAG -> ValueKind.TAG;
                    case NAME -> ValueKind.TEXT;
                    case ORIENTATION -> ValueKind.ORIENTATION;
                    case FORMAT -> ValueKind.FORMAT;
                    default -> ValueKind.INTEGER;
                };
            };
        }

        /** Shown after a numeric value, or null. */
        public String unit(Operator op) {
            return switch (valueKind(op)) {
                case DAYS -> "days";
                case INTEGER -> "times";
                default -> null;
            };
        }
    }

    public static final List<String> FORMATS = List.of("png", "jpg", "gif", "bmp");

    /**
     * Everything a row's editor can hold; which parts matter depends on the
     * ValueKind. Numbers, format names, and orientation names travel as text,
     * since that's what the editors hold.
     */
    public record Value(Integer tagId, boolean includeSubTags, String text, LocalDate from, LocalDate to) {
        public static Value empty() {
            return new Value(null, true, "", null, null);
        }

        public static Value tag(int tagId, boolean includeSubTags) {
            return new Value(tagId, includeSubTags, "", null, null);
        }

        public static Value text(String text) {
            return new Value(null, true, text, null, null);
        }

        public static Value dates(LocalDate from, LocalDate to) {
            return new Value(null, true, "", from, to);
        }
    }

    public record Spec(Field field, Operator operator, Value value) {
    }

    /** The leaf this row means, or empty while the row's value is still incomplete. */
    public static Optional<Filter> build(Field field, Operator op, Value v) {
        ValueKind kind = field.valueKind(op);
        String text = v.text() == null ? "" : v.text().trim();
        if (kind == ValueKind.TAG && v.tagId() == null) return Optional.empty();
        if ((kind == ValueKind.DATE || kind == ValueKind.DATE_RANGE) && v.from() == null) return Optional.empty();
        if (kind == ValueKind.DATE_RANGE && v.to() == null) return Optional.empty();
        if ((kind == ValueKind.TEXT || kind == ValueKind.INTEGER || kind == ValueKind.DAYS || kind == ValueKind.FORMAT || kind == ValueKind.ORIENTATION)
                && text.isEmpty()) return Optional.empty();

        try {
            Filter f = switch (field) {
                case TAG -> switch (op) {
                    case IS_EMPTY -> new Filter.UntaggedPredicate();
                    case IS_NOT_EMPTY -> new Filter.Not(new Filter.UntaggedPredicate());
                    case IS_NOT -> new Filter.Not(new Filter.TagPredicate(v.tagId(), v.includeSubTags()));
                    default -> new Filter.TagPredicate(v.tagId(), v.includeSubTags());
                };
                case NAME -> switch (op) {
                    case NOT_CONTAINS -> new Filter.Not(new Filter.NamePredicate(Filter.TextMatch.CONTAINS, text));
                    case STARTS_WITH -> new Filter.NamePredicate(Filter.TextMatch.STARTS_WITH, text);
                    case ENDS_WITH -> new Filter.NamePredicate(Filter.TextMatch.ENDS_WITH, text);
                    case IS -> new Filter.NamePredicate(Filter.TextMatch.EQUALS, text);
                    case IS_NOT -> new Filter.Not(new Filter.NamePredicate(Filter.TextMatch.EQUALS, text));
                    default -> new Filter.NamePredicate(Filter.TextMatch.CONTAINS, text);
                };
                case FAVORITE -> op == Operator.IS_FALSE
                        ? new Filter.Not(new Filter.FavoritePredicate()) : new Filter.FavoritePredicate();
                case ORIENTATION -> negateIf(op == Operator.IS_NOT,
                        new Filter.OrientationPredicate(Filter.Orientation.valueOf(text)));
                case FORMAT -> negateIf(op == Operator.IS_NOT, new Filter.FormatPredicate(text));
                case TIMES_SEEN -> seenCount(op, Long.parseLong(text));
                case IMPORTED -> switch (op) {
                    case IN_LAST -> new Filter.ImportedWithinPredicate(Long.parseLong(text) * DAY_MILLIS);
                    case NOT_IN_LAST -> new Filter.Not(new Filter.ImportedWithinPredicate(Long.parseLong(text) * DAY_MILLIS));
                    case ON_OR_AFTER -> new Filter.ImportedBetweenPredicate(startOf(v.from()), Long.MAX_VALUE);
                    case BEFORE -> new Filter.ImportedBetweenPredicate(0, startOf(v.from()) - 1);
                    default -> {
                        LocalDate a = v.from().isAfter(v.to()) ? v.to() : v.from();
                        LocalDate b = v.from().isAfter(v.to()) ? v.from() : v.to();
                        yield new Filter.ImportedBetweenPredicate(startOf(a), startOf(b.plusDays(1)) - 1);
                    }
                };
                case LAST_SEEN -> {
                    Filter notSeen = new Filter.NotSeenSincePredicate(Long.parseLong(text) * DAY_MILLIS);
                    yield op == Operator.IN_LAST ? new Filter.Not(notSeen) : notSeen;
                }
            };
            return Optional.of(f);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** The row that means this leaf, or empty if no row can express it exactly. */
    public static Optional<Spec> parse(Filter leaf) {
        boolean negated = leaf instanceof Filter.Not;
        Filter inner = leaf instanceof Filter.Not(Filter child) ? child : leaf;
        Spec spec = switch (inner) {
            case Filter.TagPredicate(int tagId, boolean includeDescendants) ->
                    new Spec(Field.TAG, negated ? Operator.IS_NOT : Operator.IS, Value.tag(tagId, includeDescendants));
            case Filter.UntaggedPredicate ignored ->
                    new Spec(Field.TAG, negated ? Operator.IS_NOT_EMPTY : Operator.IS_EMPTY, Value.empty());
            case Filter.FavoritePredicate ignored ->
                    new Spec(Field.FAVORITE, negated ? Operator.IS_FALSE : Operator.IS_TRUE, Value.empty());
            case Filter.NamePredicate(Filter.TextMatch match, String text) -> {
                Operator op = switch (match) {
                    case CONTAINS -> negated ? Operator.NOT_CONTAINS : Operator.CONTAINS;
                    case EQUALS -> negated ? Operator.IS_NOT : Operator.IS;
                    case STARTS_WITH -> negated ? null : Operator.STARTS_WITH;
                    case ENDS_WITH -> negated ? null : Operator.ENDS_WITH;
                };
                yield op == null ? null : new Spec(Field.NAME, op, Value.text(text));
            }
            case Filter.OrientationPredicate(Filter.Orientation orientation) ->
                    new Spec(Field.ORIENTATION, negated ? Operator.IS_NOT : Operator.IS, Value.text(orientation.name()));
            case Filter.FormatPredicate(String format) ->
                    new Spec(Field.FORMAT, negated ? Operator.IS_NOT : Operator.IS, Value.text(format));
            case Filter.SeenCountPredicate p -> parseSeenCount(p, negated);
            case Filter.ImportedWithinPredicate(long durationMillis) ->
                    durationMillis % DAY_MILLIS != 0 ? null
                            : new Spec(Field.IMPORTED, negated ? Operator.NOT_IN_LAST : Operator.IN_LAST,
                            Value.text(String.valueOf(durationMillis / DAY_MILLIS)));
            case Filter.NotSeenSincePredicate(long durationMillis) ->
                    durationMillis % DAY_MILLIS != 0 ? null
                            : new Spec(Field.LAST_SEEN, negated ? Operator.IN_LAST : Operator.NOT_IN_LAST,
                            Value.text(String.valueOf(durationMillis / DAY_MILLIS)));
            case Filter.ImportedBetweenPredicate(long from, long to) -> {
                if (negated) yield null;
                if (to == Long.MAX_VALUE) yield new Spec(Field.IMPORTED, Operator.ON_OR_AFTER, Value.dates(dateOf(from), null));
                if (from <= 0) yield new Spec(Field.IMPORTED, Operator.BEFORE, Value.dates(dateOf(to + 1), null));
                yield new Spec(Field.IMPORTED, Operator.BETWEEN, Value.dates(dateOf(from), dateOf(to)));
            }
            default -> null;
        };
        return Optional.ofNullable(spec);
    }

    private static Spec parseSeenCount(Filter.SeenCountPredicate p, boolean negated) {
        // seen_count is NOT NULL, so NOT (x < n) is exactly x >= n.
        Operator op = switch (p.comparison()) {
            case LT -> negated ? Operator.AT_LEAST : Operator.LESS_THAN;
            case LE -> negated ? Operator.GREATER_THAN : Operator.AT_MOST;
            case GE -> negated ? Operator.LESS_THAN : Operator.AT_LEAST;
            case GT -> negated ? Operator.AT_MOST : Operator.GREATER_THAN;
            case EQ -> negated ? Operator.IS_NOT : Operator.IS;
        };
        return new Spec(Field.TIMES_SEEN, op, Value.text(String.valueOf(p.count())));
    }

    private static Filter seenCount(Operator op, long count) {
        return switch (op) {
            case IS_NOT -> new Filter.Not(new Filter.SeenCountPredicate(Filter.Comparison.EQ, count));
            case GREATER_THAN -> new Filter.SeenCountPredicate(Filter.Comparison.GT, count);
            case AT_LEAST -> new Filter.SeenCountPredicate(Filter.Comparison.GE, count);
            case LESS_THAN -> new Filter.SeenCountPredicate(Filter.Comparison.LT, count);
            case AT_MOST -> new Filter.SeenCountPredicate(Filter.Comparison.LE, count);
            default -> new Filter.SeenCountPredicate(Filter.Comparison.EQ, count);
        };
    }

    private static Filter negateIf(boolean negate, Filter f) {
        return negate ? new Filter.Not(f) : f;
    }

    private static long startOf(LocalDate date) {
        return date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private static LocalDate dateOf(long millis) {
        return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate();
    }

    public static String orientationLabel(String name) {
        return name.charAt(0) + name.substring(1).toLowerCase(Locale.ROOT);
    }
}
