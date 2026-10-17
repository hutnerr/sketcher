package com.sketcher.practice;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sketcher.filter.Filter;
import com.sketcher.filter.FilterCodec;
import com.sketcher.model.OrderMode;
import com.sketcher.model.Segment;
import com.sketcher.model.SessionSpec;
import com.sketcher.model.SourceKind;
import com.sketcher.model.TickBehavior;

import java.util.ArrayList;
import java.util.List;

/**
 * JSON (de)serialization for a whole SessionSpec, used to remember the most
 * recently run session for the home screen's "Quick start" card (PLAN.md
 * 4.6) — independent of whether that session was launched from a saved
 * preset.
 */
public class SessionSpecCodec {
    private static final Gson GSON = new Gson();

    public static String encode(SessionSpec spec) {
        JsonObject root = new JsonObject();
        JsonArray segments = new JsonArray();
        for (Segment seg : spec.segments()) {
            segments.add(encodeSegment(seg));
        }
        root.add("segments", segments);
        return GSON.toJson(root);
    }

    public static SessionSpec decode(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        List<Segment> segments = new ArrayList<>();
        for (var el : root.getAsJsonArray("segments")) {
            segments.add(decodeSegment(el.getAsJsonObject()));
        }
        return new SessionSpec(segments, null);
    }

    private static JsonObject encodeSegment(Segment seg) {
        JsonObject o = new JsonObject();
        o.addProperty("sourceKind", seg.sourceKind().name());
        if (seg.sourceKind() == SourceKind.FILTER) {
            o.addProperty("filter", FilterCodec.encode(seg.filter()));
        }
        if (seg.playlistId() != null) o.addProperty("playlistId", seg.playlistId());
        if (seg.itemCount() != null) o.addProperty("itemCount", seg.itemCount());
        o.addProperty("orderMode", seg.orderMode().name());
        o.addProperty("secondsPerImage", seg.secondsPerImage());
        o.addProperty("tickBehavior", seg.tickBehavior().name());
        o.addProperty("breakSeconds", seg.breakSeconds());
        if (seg.memoryShowSeconds() != null) o.addProperty("memoryShowSeconds", seg.memoryShowSeconds());
        if (seg.memoryHideSeconds() != null) o.addProperty("memoryHideSeconds", seg.memoryHideSeconds());
        if (seg.rampToSeconds() != null) o.addProperty("rampToSeconds", seg.rampToSeconds());
        return o;
    }

    private static Segment decodeSegment(JsonObject o) {
        SourceKind sourceKind = SourceKind.valueOf(o.get("sourceKind").getAsString());
        Filter filter = o.has("filter") ? FilterCodec.decode(o.get("filter").getAsString()) : null;
        return new Segment(sourceKind, filter,
                o.has("playlistId") ? o.get("playlistId").getAsInt() : null,
                o.has("itemCount") ? o.get("itemCount").getAsInt() : null,
                OrderMode.valueOf(o.get("orderMode").getAsString()),
                o.get("secondsPerImage").getAsInt(),
                TickBehavior.valueOf(o.get("tickBehavior").getAsString()),
                o.get("breakSeconds").getAsInt(),
                o.has("memoryShowSeconds") ? o.get("memoryShowSeconds").getAsInt() : null,
                o.has("memoryHideSeconds") ? o.get("memoryHideSeconds").getAsInt() : null,
                o.has("rampToSeconds") ? o.get("rampToSeconds").getAsInt() : null);
    }
}
