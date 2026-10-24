package com.sketcher.filter;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * JSON (de)serialization for the Filter AST, used to persist segments and
 * presets. Hand-rolled rather than reflective, since Filter is a sealed
 * interface of records and Gson has no first-class support for that.
 * Versioned so a future AST change can still read old presets.
 *
 * The tag hooks let archives reuse the same shape while writing a tag's
 * portable path instead of its library-local id.
 */
public class FilterCodec {
    private static final int VERSION = 1;
    private static final Gson GSON = new Gson();

    private static final BiConsumer<Filter.TagPredicate, JsonObject> TAG_ID_WRITER =
            (p, o) -> o.addProperty("tagId", p.tagId());
    private static final Function<JsonObject, Integer> TAG_ID_READER = o -> o.get("tagId").getAsInt();

    public static String encode(Filter filter) {
        JsonObject root = new JsonObject();
        root.addProperty("version", VERSION);
        root.add("filter", toJson(filter, TAG_ID_WRITER));
        return GSON.toJson(root);
    }

    public static Filter decode(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        return fromJson(root.getAsJsonObject("filter"), TAG_ID_READER);
    }

    /** tagWriter adds the fields that identify the tag; "type" and "includeDescendants" are written here. */
    public static JsonObject toJson(Filter filter, BiConsumer<Filter.TagPredicate, JsonObject> tagWriter) {
        JsonObject o = new JsonObject();
        switch (filter) {
            case Filter.All ignored -> o.addProperty("type", "all");
            case Filter.FavoritePredicate ignored -> o.addProperty("type", "favorite");
            case Filter.UntaggedPredicate ignored -> o.addProperty("type", "untagged");
            case Filter.NamePredicate p -> {
                o.addProperty("type", "name");
                o.addProperty("match", p.match().name());
                o.addProperty("text", p.text());
            }
            case Filter.ImportedBetweenPredicate p -> {
                o.addProperty("type", "importedBetween");
                o.addProperty("from", p.fromMillis());
                o.addProperty("to", p.toMillis());
            }
            case Filter.ImportedWithinPredicate p -> {
                o.addProperty("type", "importedWithin");
                o.addProperty("durationMillis", p.durationMillis());
            }
            case Filter.NotSeenSincePredicate p -> {
                o.addProperty("type", "notSeenSince");
                o.addProperty("durationMillis", p.durationMillis());
            }
            case Filter.SeenCountPredicate p -> {
                o.addProperty("type", "seenCount");
                o.addProperty("comparison", p.comparison().name());
                o.addProperty("count", p.count());
            }
            case Filter.FormatPredicate p -> {
                o.addProperty("type", "format");
                o.addProperty("format", p.format());
            }
            case Filter.OrientationPredicate p -> {
                o.addProperty("type", "orientation");
                o.addProperty("orientation", p.orientation().name());
            }
            case Filter.TagPredicate p -> {
                o.addProperty("type", "tag");
                tagWriter.accept(p, o);
                o.addProperty("includeDescendants", p.includeDescendants());
            }
            case Filter.Not n -> {
                o.addProperty("type", "not");
                o.add("child", toJson(n.child(), tagWriter));
            }
            case Filter.And a -> {
                o.addProperty("type", "and");
                o.add("children", toJsonArray(a.children(), tagWriter));
            }
            case Filter.Or or -> {
                o.addProperty("type", "or");
                o.add("children", toJsonArray(or.children(), tagWriter));
            }
        }
        return o;
    }

    private static JsonArray toJsonArray(List<Filter> filters, BiConsumer<Filter.TagPredicate, JsonObject> tagWriter) {
        JsonArray array = new JsonArray();
        for (Filter f : filters) {
            array.add(toJson(f, tagWriter));
        }
        return array;
    }

    /** tagReader resolves a "tag" object (as written by the matching tagWriter) to a local tag id. */
    public static Filter fromJson(JsonObject o, Function<JsonObject, Integer> tagReader) {
        String type = o.get("type").getAsString();
        return switch (type) {
            case "all" -> new Filter.All();
            case "favorite" -> new Filter.FavoritePredicate();
            case "untagged" -> new Filter.UntaggedPredicate();
            case "name" -> new Filter.NamePredicate(
                    Filter.TextMatch.valueOf(o.get("match").getAsString()), o.get("text").getAsString());
            // Written by builds before NamePredicate generalized it.
            case "nameContains" -> new Filter.NamePredicate(Filter.TextMatch.CONTAINS, o.get("text").getAsString());
            case "importedBetween" -> new Filter.ImportedBetweenPredicate(
                    o.get("from").getAsLong(), o.get("to").getAsLong());
            case "importedWithin" -> new Filter.ImportedWithinPredicate(o.get("durationMillis").getAsLong());
            case "notSeenSince" -> new Filter.NotSeenSincePredicate(o.get("durationMillis").getAsLong());
            case "seenCount" -> new Filter.SeenCountPredicate(
                    Filter.Comparison.valueOf(o.get("comparison").getAsString()), o.get("count").getAsLong());
            case "format" -> new Filter.FormatPredicate(o.get("format").getAsString());
            case "orientation" -> new Filter.OrientationPredicate(
                    Filter.Orientation.valueOf(o.get("orientation").getAsString()));
            case "tag" -> new Filter.TagPredicate(tagReader.apply(o), o.get("includeDescendants").getAsBoolean());
            case "not" -> new Filter.Not(fromJson(o.getAsJsonObject("child"), tagReader));
            case "and" -> new Filter.And(fromJsonArray(o.getAsJsonArray("children"), tagReader));
            case "or" -> new Filter.Or(fromJsonArray(o.getAsJsonArray("children"), tagReader));
            default -> throw new IllegalArgumentException("Unknown filter type: " + type);
        };
    }

    private static List<Filter> fromJsonArray(JsonArray array, Function<JsonObject, Integer> tagReader) {
        List<Filter> result = new ArrayList<>();
        for (var el : array) {
            result.add(fromJson(el.getAsJsonObject(), tagReader));
        }
        return result;
    }
}
