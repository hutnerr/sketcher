package com.sketcher.model;

import java.util.List;

/** A library image's metadata row. Named ImageRecord to avoid clashing with javafx.scene.image.Image. */
public record ImageRecord(
        int id,
        String hash,
        String ext,
        String originalName,
        int width,
        int height,
        long byteSize,
        long importedAt,
        String sourcePath,
        boolean favorite,
        Long lastSeenAt,
        int seenCount,
        Long deletedAt,
        List<Integer> tagIds
) {
    public boolean isDeleted() {
        return deletedAt != null;
    }
}
