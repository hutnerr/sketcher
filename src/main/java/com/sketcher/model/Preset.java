package com.sketcher.model;

/** A saved, named SessionSpec (PLAN.md 4.5); its segments live in the `segment` table. */
public record Preset(int id, String name, long createdAt, Long lastUsedAt, int useCount, boolean pinned) {
}
