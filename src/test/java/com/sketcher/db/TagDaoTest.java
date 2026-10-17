package com.sketcher.db;

import com.sketcher.db.dao.TagDao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TagDaoTest {
    private TagDao tagDao;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        Database database = new Database(tempDir.resolve("test.db"));
        tagDao = new TagDao(database.connection());
    }

    @Test
    void reparentingATagUnderItsOwnDescendantIsRejected() {
        int animal = tagDao.create("animal", null);
        int dog = tagDao.create("dog", animal);
        int husky = tagDao.create("husky", dog);

        assertThrows(IllegalStateException.class, () -> tagDao.reparent(animal, husky));
    }

    @Test
    void reparentingATagUnderItselfIsRejected() {
        int animal = tagDao.create("animal", null);
        assertThrows(IllegalStateException.class, () -> tagDao.reparent(animal, animal));
    }

    @Test
    void duplicateSiblingNamesAreRejected() {
        int animal = tagDao.create("animal", null);
        tagDao.create("dog", animal);
        assertThrows(IllegalStateException.class, () -> tagDao.create("dog", animal));
    }

    @Test
    void deletingATagReassignsChildrenToItsParent() {
        int animal = tagDao.create("animal", null);
        int dog = tagDao.create("dog", animal);
        int husky = tagDao.create("husky", dog);

        tagDao.deleteReassignChildren(dog);

        assertEquals(animal, tagDao.get(husky).orElseThrow().parentId());
    }
}
