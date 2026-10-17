package com.sketcher.practice;

import com.sketcher.db.Database;
import com.sketcher.db.dao.ImageDao;
import com.sketcher.filter.Filter;
import com.sketcher.library.LibraryService;
import com.sketcher.model.OrderMode;
import com.sketcher.model.Segment;
import com.sketcher.model.SessionSpec;
import com.sketcher.model.TickBehavior;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SessionBuilderTest {

    @Test
    void segmentShrinksWhenFilterMatchesFewerImagesThanRequestedCount(@TempDir Path tempDir) {
        try (LibraryService library = new LibraryService(tempDir.resolve("lib").toString())) {
            ImageDao imageDao = library.images();
            for (int i = 0; i < 3; i++) {
                imageDao.insert("hash" + i, "png", "img" + i + ".png", 10, 10, 100,
                        System.currentTimeMillis(), null);
            }

            Segment segment = Segment.filterSegment(new Filter.All(), 5, OrderMode.SEQUENTIAL,
                    0, TickBehavior.ADVANCE_IMMEDIATELY, 0);
            List<ResolvedSegment> resolved = new SessionBuilder().resolve(SessionSpec.single(segment), library);

            assertEquals(1, resolved.size());
            assertEquals(3, resolved.get(0).imageIds().size());
        }
    }
}
