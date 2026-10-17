package com.sketcher.model;

import java.util.List;

/** An ordered list of segments; the unit presets save and sessions run. */
public record SessionSpec(List<Segment> segments, Integer presetId) {
    public static SessionSpec single(Segment segment) {
        return new SessionSpec(List.of(segment), null);
    }
}
