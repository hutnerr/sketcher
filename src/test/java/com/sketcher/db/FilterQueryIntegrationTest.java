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

    @Test
    void conditionPredicatesCompileToWorkingSql() {
        long now = System.currentTimeMillis();
        long day = 24L * 60 * 60 * 1000;
        int portrait = imageDao.insert("p1", "jpeg", "Pose_50%_ref.JPEG", 800, 1200, 3 * 1024 * 1024, now, null);
        int landscape = imageDao.insert("l1", "png", "landscape.png", 1200, 800, 500 * 1024, now - 40 * day, null);
        int nearSquare = imageDao.insert("s1", "bmp", "scan.bmp", 1000, 990, 1000, now - 40 * day, null);

        assertEquals(Set.of(portrait), ids(query(new Filter.OrientationPredicate(Filter.Orientation.PORTRAIT))));
        assertEquals(Set.of(landscape), ids(query(new Filter.OrientationPredicate(Filter.Orientation.LANDSCAPE))));
        assertTrue(ids(query(new Filter.OrientationPredicate(Filter.Orientation.SQUARE))).contains(nearSquare));
        assertEquals(Set.of(portrait), ids(query(new Filter.FormatPredicate("jpg"))));
        imageDao.setSeenStats(landscape, now, 5);
        assertEquals(Set.of(landscape), ids(query(new Filter.SeenCountPredicate(Filter.Comparison.GE, 3))));
        assertEquals(Set.of(landscape, nearSquare), ids(query(new Filter.Not(
                new Filter.ImportedWithinPredicate(30 * day)))));
        // % and _ are literal in a filename search, and matching ignores case.
        assertEquals(Set.of(portrait), ids(query(new Filter.NamePredicate(Filter.TextMatch.CONTAINS, "50%_"))));
        assertEquals(Set.of(portrait), ids(query(new Filter.NamePredicate(Filter.TextMatch.STARTS_WITH, "pose"))));
        assertEquals(Set.of(), ids(query(new Filter.NamePredicate(Filter.TextMatch.CONTAINS, "50%x"))));
    }

    private List<ImageRecord> query(Filter filter) {
        return imageDao.query(filter, OrderMode.SEQUENTIAL, null);
    }

    private static Set<Integer> ids(List<ImageRecord> records) {
        return records.stream().map(ImageRecord::id).collect(Collectors.toSet());
    }
}
