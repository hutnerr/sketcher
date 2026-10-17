package com.sketcher.db;

import com.sketcher.db.dao.PresetDao;
import com.sketcher.filter.Filter;
import com.sketcher.model.OrderMode;
import com.sketcher.model.Preset;
import com.sketcher.model.Segment;
import com.sketcher.model.SessionSpec;
import com.sketcher.model.TickBehavior;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PresetDaoTest {
    private PresetDao presetDao;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        Database database = new Database(tempDir.resolve("test.db"));
        presetDao = new PresetDao(database.connection());
    }

    private Segment sampleSegment() {
        return Segment.filterSegment(new Filter.FavoritePredicate(), 10, OrderMode.RANDOM, 30,
                TickBehavior.BREAK, 5);
    }

    @Test
    void savedPresetRoundTripsItsSegment() {
        int id = presetDao.create("Warm-up", SessionSpec.single(sampleSegment()));

        SessionSpec loaded = presetDao.loadSpec(id);

        assertEquals(1, loaded.segments().size());
        Segment segment = loaded.segments().get(0);
        assertEquals(new Filter.FavoritePredicate(), segment.filter());
        assertEquals(10, segment.itemCount());
        assertEquals(OrderMode.RANDOM, segment.orderMode());
        assertEquals(30, segment.secondsPerImage());
        assertEquals(TickBehavior.BREAK, segment.tickBehavior());
        assertEquals(5, segment.breakSeconds());
    }

    @Test
    void duplicatePresetNameIsRejected() {
        presetDao.create("Warm-up", SessionSpec.single(sampleSegment()));
        assertThrows(IllegalStateException.class,
                () -> presetDao.create("Warm-up", SessionSpec.single(sampleSegment())));
    }

    @Test
    void pinnedPresetsSortBeforeUnpinnedRegardlessOfRecency() {
        int older = presetDao.create("Older", SessionSpec.single(sampleSegment()));
        int newer = presetDao.create("Newer", SessionSpec.single(sampleSegment()));
        presetDao.recordUsed(older);
        presetDao.recordUsed(newer);
        presetDao.setPinned(older, true);

        List<Preset> all = presetDao.listAll();

        assertEquals(older, all.get(0).id());
        assertEquals(newer, all.get(1).id());
    }

    @Test
    void recordUsedBumpsUseCountAndLastUsedAt() {
        int id = presetDao.create("Warm-up", SessionSpec.single(sampleSegment()));
        assertTrue(presetDao.get(id).orElseThrow().lastUsedAt() == null);

        presetDao.recordUsed(id);
        presetDao.recordUsed(id);

        Preset preset = presetDao.get(id).orElseThrow();
        assertEquals(2, preset.useCount());
        assertTrue(preset.lastUsedAt() != null);
    }

    @Test
    void deletingAPresetRemovesItsSegments() {
        int id = presetDao.create("Warm-up", SessionSpec.single(sampleSegment()));
        presetDao.delete(id);
        assertEquals(0, presetDao.loadSpec(id).segments().size());
    }
}
