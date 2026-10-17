package com.sketcher.filter;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;

/**
 * JSON (de)serialization for the Filter AST, used to persist segments and
 * presets. Hand-rolled rather than reflective, since Filter is a sealed
 * interface of records and Gson has no first-class support for that.
 * Versioned so a future AST change can still read old presets.
 */
public class FilterCodec {
    private static final int VERSION = 1;
    private static final Gson GSON = new Gson();

    public static String encode(Filter filter) {
        JsonObject root = new JsonObject();
        root.addProperty("version", VERSION);
        root.add("filter", toJson(filter));
        return GSON.toJson(root);
    }

    public static Filter decode(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        return fromJson(root.getAsJsonObject("filter"));
    }

    private static JsonObject toJson(Filter filter) {
        JsonObject o = new JsonObject();
        switch (filter) {
            case Filter.All ignored -> o.addProperty("type", "all");
            case Filter.FavoritePredicate ignored -> o.addProperty("type", "favorite");
            case Filter.UntaggedPredicate ignored -> o.addProperty("type", "untagged");
            case Filter.NameContainsPredicate p -> {
                o.addProperty("type", "nameContains");
                o.addProperty("text", p.text());
            }
            case Filter.ImportedBetweenPredicate p -> {
                o.addProperty("type", "importedBetween");
                o.addProperty("from", p.fromMillis());
                o.addProperty("to", p.toMillis());
            }
            case Filter.NotSeenSincePredicate p -> {
                o.addProperty("type", "notSeenSince");
                o.addProperty("durationMillis", p.durationMillis());
            }
            case Filter.TagPredicate p -> {
                o.addProperty("type", "tag");
                o.addProperty("tagId", p.tagId());
                o.addProperty("includeDescendants", p.includeDescendants());
            }
            case Filter.Not n -> {
                o.addProperty("type", "not");
                o.add("child", toJson(n.child()));
            }
            case Filter.And a -> {
                o.addProperty("type", "and");
                o.add("children", toJsonArray(a.children()));
            }
            case Filter.Or or -> {
                o.addProperty("type", "or");
                o.add("children", toJsonArray(or.children()));
            }
        }
        return o;
    }

    private static JsonArray toJsonArray(List<Filter> filters) {
        JsonArray array = new JsonArray();
        for (Filter f : filters) {
            array.add(toJson(f));
        }
        return array;
    }

    private static Filter fromJson(JsonObject o) {
        String type = o.get("type").getAsString();
        return switch (type) {
            case "all" -> new Filter.All();
            case "favorite" -> new Filter.FavoritePredicate();
            case "untagged" -> new Filter.UntaggedPredicate();
            case "nameContains" -> new Filter.NameContainsPredicate(o.get("text").getAsString());
            case "importedBetween" -> new Filter.ImportedBetweenPredicate(
                    o.get("from").getAsLong(), o.get("to").getAsLong());
            case "notSeenSince" -> new Filter.NotSeenSincePredicate(o.get("durationMillis").getAsLong());
            case "tag" -> new Filter.TagPredicate(
                    o.get("tagId").getAsInt(), o.get("includeDescendants").getAsBoolean());
            case "not" -> new Filter.Not(fromJson(o.getAsJsonObject("child")));
            case "and" -> new Filter.And(fromJsonArray(o.getAsJsonArray("children")));
            case "or" -> new Filter.Or(fromJsonArray(o.getAsJsonArray("children")));
            default -> throw new IllegalArgumentException("Unknown filter type: " + type);
        };
    }

    private static List<Filter> fromJsonArray(JsonArray array) {
        List<Filter> result = new ArrayList<>();
        for (var el : array) {
            result.add(fromJson(el.getAsJsonObject()));
        }
        return result;
    }
}
