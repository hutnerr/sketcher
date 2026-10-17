package com.sketcher.practice;

import com.sketcher.library.LibraryService;
import com.sketcher.model.ImageRecord;
import com.sketcher.model.OrderMode;
import com.sketcher.model.Segment;
import com.sketcher.model.SessionSpec;
import com.sketcher.model.SourceKind;

import java.util.ArrayList;
import java.util.List;

/**
 * Resolves a SessionSpec's segments into concrete, ordered image-id lists.
 * If a segment's count exceeds what its source can supply, the segment is
 * shrunk rather than padded by repeating images (PLAN.md 8, open question 4).
 */
public class SessionBuilder {

    public List<ResolvedSegment> resolve(SessionSpec spec, LibraryService library) {
        List<ResolvedSegment> resolved = new ArrayList<>();
        for (Segment segment : spec.segments()) {
            List<Integer> ids;
            if (segment.sourceKind() == SourceKind.PLAYLIST) {
                ids = library.playlists().listImageIds(segment.playlistId(), segment.orderMode() == OrderMode.RANDOM);
            } else {
                ids = library.images().query(segment.filter(), segment.orderMode(), segment.itemCount())
                        .stream().map(ImageRecord::id).toList();
            }
            if (segment.itemCount() != null && ids.size() > segment.itemCount()) {
                ids = ids.subList(0, segment.itemCount());
            }
            resolved.add(new ResolvedSegment(segment, ids));
        }
        return resolved;
    }
}
