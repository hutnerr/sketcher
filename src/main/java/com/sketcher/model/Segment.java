package com.sketcher.model;

import com.sketcher.filter.Filter;

/**
 * One slice of a practice session (PLAN.md 1.3). itemCount null means
 * unlimited; memoryShowSeconds null means memory-drawing mode is off;
 * rampToSeconds null means the timer is flat across the segment, otherwise
 * secondsPerImage is the starting value and rampToSeconds is the ending
 * value, interpolated linearly across the segment's images (PLAN.md Phase 6
 * "ramping timer").
 */
public record Segment(
        SourceKind sourceKind,
        Filter filter,
        Integer playlistId,
        Integer itemCount,
        OrderMode orderMode,
        int secondsPerImage,
        TickBehavior tickBehavior,
        int breakSeconds,
        Integer memoryShowSeconds,
        Integer memoryHideSeconds,
        Integer rampToSeconds
) {
    public static Segment filterSegment(Filter filter, Integer itemCount, OrderMode orderMode,
                                         int secondsPerImage, TickBehavior tickBehavior, int breakSeconds) {
        return new Segment(SourceKind.FILTER, filter, null, itemCount, orderMode,
                secondsPerImage, tickBehavior, breakSeconds, null, null, null);
    }

    public static Segment playlistSegment(int playlistId, Integer itemCount, OrderMode orderMode,
                                           int secondsPerImage, TickBehavior tickBehavior, int breakSeconds) {
        return new Segment(SourceKind.PLAYLIST, null, playlistId, itemCount, orderMode,
                secondsPerImage, tickBehavior, breakSeconds, null, null, null);
    }

    public Segment withMemoryMode(Integer showSeconds, Integer hideSeconds) {
        return new Segment(sourceKind, filter, playlistId, itemCount, orderMode, secondsPerImage,
                tickBehavior, breakSeconds, showSeconds, hideSeconds, rampToSeconds);
    }

    public Segment withRampToSeconds(Integer rampToSeconds) {
        return new Segment(sourceKind, filter, playlistId, itemCount, orderMode, secondsPerImage,
                tickBehavior, breakSeconds, memoryShowSeconds, memoryHideSeconds, rampToSeconds);
    }

    public boolean isTimed() {
        return secondsPerImage > 0;
    }

    public boolean isMemoryMode() {
        return memoryShowSeconds != null;
    }

    public boolean isRamped() {
        return rampToSeconds != null && isTimed();
    }
}
