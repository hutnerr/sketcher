package com.sketcher.db;

import com.sketcher.db.dao.ImageDao;
import com.sketcher.db.dao.TagDao;
import com.sketcher.filter.Filter;
import com.sketcher.model.ImageRecord;
import com.sketcher.model.OrderMode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises FilterCompiler through real SQLite queries, especially the NOT
 * and descendant-tag-expansion cases the plan calls out as the ones that
 * must be correct (PLAN.md 1.4, 6).
 */
class FilterQueryIntegrationTest {
    private Database database;
    private ImageDao imageDao;
    private TagDao tagDao;

    private int animalTag, dogTag, huskyTag, nudeTag;
    private int img1, img2, img3, img4, img5;

    @BeforeEach
    void setUp(@TempDir Path tempDir) {
        database = new Database(tempDir.resolve("test.db"));
        imageDao = new ImageDao(database.connection());
        tagDao = new TagDao(database.connection());

        animalTag = tagDao.create("animal", null);
        dogTag = tagDao.create("dog", animalTag);
        huskyTag = tagDao.create("husky", dogTag);
        nudeTag = tagDao.create("nude", null);

        img1 = insertImage("h1");
        img2 = insertImage("h2");
        img3 = insertImage("h3");
        img4 = insertImage("h4");
        img5 = insertImage("h5");

        imageDao.addTags(img1, Set.of(dogTag));
        imageDao.addTags(img2, Set.of(huskyTag));
        imageDao.addTags(img3, Set.of(nudeTag));
        // img4 stays untagged
        imageDao.addTags(img5, Set.of(dogTag, nudeTag));
    }

    private int insertImage(String hash) {
        return imageDao.insert(hash, "png", hash + ".png", 100, 100, 1000,
                System.currentTimeMillis(), null);
    }

    @Test
    void tagPredicateWithDescendantsMatchesWholeSubtree() {
        List<ImageRecord> results = imageDao.query(
                new Filter.TagPredicate(animalTag, true), OrderMode.SEQUENTIAL, null);
        assertEquals(Set.of(img1, img2, img5), ids(results));
    }

    @Test
    void tagPredicateWithoutDescendantsIsExact() {
        List<ImageRecord> results = imageDao.query(
                new Filter.TagPredicate(dogTag, false), OrderMode.SEQUENTIAL, null);
        assertEquals(Set.of(img1, img5), ids(results));
    }

    @Test
    void notNudeExcludesTaggedImages() {
        Filter filter = new Filter.And(List.of(
                new Filter.TagPredicate(dogTag, true),
                new Filter.Not(new Filter.TagPredicate(nudeTag, false))
        ));
        List<ImageRecord> results = imageDao.query(filter, OrderMode.SEQUENTIAL, null);
        assertEquals(Set.of(img1, img2), ids(results));
    }

    @Test
    void untaggedPredicateFindsOnlyUntaggedImages() {
        List<ImageRecord> results = imageDao.query(new Filter.UntaggedPredicate(), OrderMode.SEQUENTIAL, null);
        assertEquals(Set.of(img4), ids(results));
    }

    @Test
    void softDeletedImagesAreAlwaysExcluded() {
        imageDao.softDelete(img1, System.currentTimeMillis());
        List<ImageRecord> results = imageDao.query(
                new Filter.TagPredicate(dogTag, true), OrderMode.SEQUENTIAL, null);
        assertTrue(ids(results).contains(img5));
        assertTrue(!ids(results).contains(img1));
    }

    private static Set<Integer> ids(List<ImageRecord> records) {
        return records.stream().map(ImageRecord::id).collect(Collectors.toSet());
    }
}
