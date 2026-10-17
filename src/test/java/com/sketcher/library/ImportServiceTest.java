package com.sketcher.library;

import com.sketcher.model.ImageRecord;
import com.sketcher.model.ImportCandidate;
import com.sketcher.model.ImportStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the import staging/commit flow against a real temp library and
 * real PNG files (PLAN.md 4.1, 6: "import new / duplicate / move vs copy /
 * cancel rollback").
 */
class ImportServiceTest {

    private Path pngFile(Path dir, String name, int rgb) throws IOException {
        BufferedImage img = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < 10; x++) {
            for (int y = 0; y < 10; y++) img.setRGB(x, y, rgb);
        }
        Path file = dir.resolve(name);
        ImageIO.write(img, "png", file.toFile());
        return file;
    }

    private ImportCandidate stageAndProbe(LibraryService library, Path file) {
        ImportCandidate candidate = new ImportCandidate(file);
        candidate.applyProbe(library.importService().probe(file, library.images()));
        return candidate;
    }

    @Test
    void newImageIsCopiedAndPersisted(@TempDir Path tempDir) throws IOException {
        try (LibraryService library = new LibraryService(tempDir.resolve("lib").toString())) {
            Path source = pngFile(tempDir, "a.png", 0xFF0000);
            ImportCandidate candidate = stageAndProbe(library, source);
            assertEquals(ImportStatus.NEW, candidate.getStatus());

            var result = library.importService().importBatch(List.of(candidate), Set.of(), false,
                    library.images(), null, i -> { });

            assertEquals(1, result.imported());
            ImageRecord record = library.images().findByHash(candidate.getHash()).orElseThrow();
            assertTrue(Files.isRegularFile(library.ref().imagePath(record.hash(), record.ext())));
            assertTrue(Files.isRegularFile(source), "copy must not remove the source file");
        }
    }

    @Test
    void moveDeletesSourceAfterImport(@TempDir Path tempDir) throws IOException {
        try (LibraryService library = new LibraryService(tempDir.resolve("lib").toString())) {
            Path source = pngFile(tempDir, "a.png", 0x00FF00);
            ImportCandidate candidate = stageAndProbe(library, source);

            library.importService().importBatch(List.of(candidate), Set.of(), true,
                    library.images(), null, i -> { });

            assertFalse(Files.exists(source), "move must remove the source file");
        }
    }

    @Test
    void duplicateWithinBatchIsSkipped(@TempDir Path tempDir) throws IOException {
        try (LibraryService library = new LibraryService(tempDir.resolve("lib").toString())) {
            Path a = pngFile(tempDir, "a.png", 0x123456);
            Path b = pngFile(tempDir, "b.png", 0x123456); // identical pixels -> identical hash

            ImportCandidate ca = stageAndProbe(library, a);
            ImportCandidate cb = stageAndProbe(library, b);
            library.importService().markBatchDuplicates(List.of(ca, cb));
            assertEquals(ImportStatus.DUPLICATE_IN_BATCH, cb.getStatus());

            var result = library.importService().importBatch(List.of(ca, cb), Set.of(), false,
                    library.images(), null, i -> { });

            assertEquals(1, result.imported());
            assertEquals(1, result.skipped());
        }
    }

    @Test
    void reimportingAnAlreadyPresentHashTagsTheExistingImageInstead(@TempDir Path tempDir) throws IOException {
        try (LibraryService library = new LibraryService(tempDir.resolve("lib").toString())) {
            Path a = pngFile(tempDir, "a.png", 0xABCDEF);
            ImportCandidate first = stageAndProbe(library, a);
            library.importService().importBatch(List.of(first), Set.of(), false,
                    library.images(), null, i -> { });

            Path aAgain = pngFile(tempDir, "a-again.png", 0xABCDEF);
            ImportCandidate second = stageAndProbe(library, aAgain);
            assertEquals(ImportStatus.ALREADY_IN_LIBRARY, second.getStatus());
            second.includedProperty().set(true);
            int tagId = library.tags().create("study", null);
            second.tagOverrides().add(tagId);

            var result = library.importService().importBatch(List.of(second), Set.of(), false,
                    library.images(), null, i -> { });

            assertEquals(1, result.taggedExisting());
            assertEquals(0, result.imported());
            ImageRecord existing = library.images().findByHash(first.getHash()).orElseThrow();
            assertTrue(existing.tagIds().contains(tagId));
        }
    }

    /**
     * Regression test: a soft-deleted image's hash still occupies the
     * UNIQUE(hash) slot, so re-importing the same bytes must restore it
     * rather than being classified NEW and hitting a constraint violation.
     */
    @Test
    void reimportingARecycledImageRestoresItInstead(@TempDir Path tempDir) throws IOException {
        try (LibraryService library = new LibraryService(tempDir.resolve("lib").toString())) {
            Path a = pngFile(tempDir, "a.png", 0x55AA33);
            ImportCandidate first = stageAndProbe(library, a);
            library.importService().importBatch(List.of(first), Set.of(), false,
                    library.images(), null, i -> { });

            ImageRecord existing = library.images().findByHash(first.getHash()).orElseThrow();
            com.sketcher.ui.gallery.GalleryActions.softDeleteToRecycle(library, existing);
            assertTrue(library.images().findByHash(first.getHash()).orElseThrow().isDeleted());
            assertTrue(Files.isRegularFile(library.ref().recyclePath(existing.hash(), existing.ext())));

            Path aAgain = pngFile(tempDir, "a-again.png", 0x55AA33);
            ImportCandidate second = stageAndProbe(library, aAgain);
            assertEquals(ImportStatus.ALREADY_IN_LIBRARY, second.getStatus(),
                    "a recycled hash must not be reported as NEW");
            second.includedProperty().set(true);

            var result = library.importService().importBatch(List.of(second), Set.of(), false,
                    library.images(), null, i -> { });

            assertEquals(1, result.taggedExisting());
            assertEquals(0, result.failed());
            ImageRecord restored = library.images().findByHash(first.getHash()).orElseThrow();
            assertFalse(restored.isDeleted());
            assertTrue(Files.isRegularFile(library.ref().imagePath(restored.hash(), restored.ext())));
            assertFalse(Files.isRegularFile(library.ref().recyclePath(restored.hash(), restored.ext())));
        }
    }

    @Test
    void cancellingMidBatchLeavesNoTrace(@TempDir Path tempDir) throws IOException {
        try (LibraryService library = new LibraryService(tempDir.resolve("lib").toString())) {
            Path a = pngFile(tempDir, "a.png", 0x999999);
            ImportCandidate candidate = stageAndProbe(library, a);
            AtomicBoolean cancelFlag = new AtomicBoolean(true);

            var result = library.importService().importBatch(List.of(candidate), Set.of(), false,
                    library.images(), cancelFlag, i -> { });

            assertTrue(result.cancelled());
            assertTrue(library.images().findByHash(candidate.getHash()).isEmpty());
        }
    }
}
