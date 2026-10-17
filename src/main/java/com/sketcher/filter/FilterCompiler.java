package com.sketcher.filter;

import java.util.ArrayList;
import java.util.List;

/**
 * Compiles a Filter AST into a SQL WHERE clause (for "FROM image i …") plus
 * ordered bind parameters. Always ANDs in "i.deleted_at IS NULL" so recycled
 * images can never leak into a gallery or practice query (PLAN.md 4.7).
 */
public class FilterCompiler {

    public record Compiled(String whereSql, List<Object> params) {
    }

    public static Compiled compile(Filter filter) {
        List<Object> params = new ArrayList<>();
        String body = compileNode(filter, params);
        String where = "(" + body + ") AND i.deleted_at IS NULL";
        return new Compiled(where, params);
    }

    private static String compileNode(Filter filter, List<Object> params) {
        return switch (filter) {
            case Filter.All ignored -> "1=1";

            case Filter.FavoritePredicate ignored -> "i.favorite = 1";

            case Filter.UntaggedPredicate ignored ->
                    "NOT EXISTS (SELECT 1 FROM image_tag it WHERE it.image_id = i.id)";

            case Filter.NameContainsPredicate p -> {
                params.add("%" + p.text() + "%");
                yield "i.original_name LIKE ?";
            }

            case Filter.ImportedBetweenPredicate p -> {
                params.add(p.fromMillis());
                params.add(p.toMillis());
                yield "i.imported_at BETWEEN ? AND ?";
            }

            case Filter.NotSeenSincePredicate p -> {
                long cutoff = System.currentTimeMillis() - p.durationMillis();
                params.add(cutoff);
                yield "(i.last_seen_at IS NULL OR i.last_seen_at < ?)";
            }

            case Filter.TagPredicate p -> {
                if (!p.includeDescendants()) {
                    params.add(p.tagId());
                    yield "EXISTS (SELECT 1 FROM image_tag it WHERE it.image_id = i.id AND it.tag_id = ?)";
                }
                params.add(p.tagId());
                yield """
                        EXISTS (
                            WITH RECURSIVE sub(id) AS (
                                SELECT ?
                                UNION ALL
                                SELECT t.id FROM tag t JOIN sub ON t.parent_id = sub.id
                            )
                            SELECT 1 FROM image_tag it WHERE it.image_id = i.id AND it.tag_id IN (SELECT id FROM sub)
                        )""";
            }

            case Filter.Not n -> "NOT (" + compileNode(n.child(), params) + ")";

            case Filter.And a -> combine(a.children(), " AND ", params);

            case Filter.Or o -> combine(o.children(), " OR ", params);
        };
    }

    private static String combine(List<Filter> children, String joiner, List<Object> params) {
        if (children.isEmpty()) {
            return "1=1";
        }
        List<String> parts = new ArrayList<>();
        for (Filter child : children) {
            parts.add("(" + compileNode(child, params) + ")");
        }
        return String.join(joiner, parts);
    }
}
