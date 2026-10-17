package com.sketcher.library;

import com.sketcher.config.LibraryRef;
import com.sketcher.db.dao.ImageDao;
import com.sketcher.model.ImageRecord;
import com.sketcher.model.ImportCandidate;
import com.sketcher.model.ImportStatus;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Drives import staging and the final batch commit (PLAN.md 4.1).
 * Native-decode formats only: png/jpg/jpeg/gif/bmp.
 */
public class ImportService {
    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("png", "jpg", "jpeg", "gif", "bmp");

    private final LibraryRef library;
    private final Connection connection;
    private final HashService hashService;
    private final ThumbnailCache thumbnailCache;

    public ImportService(LibraryRef library, Connection connection, HashService hashService,
                          ThumbnailCache thumbnailCache) {
        this.library = library;
        this.connection = connection;
        this.hashService = hashService;
        this.thumbnailCache = thumbnailCache;
    }

    public static boolean isSupported(Path file) {
        return SUPPORTED_EXTENSIONS.contains(extensionOf(file));
    }

    /** Pure probe result; computed off the FX thread, applied to the candidate on it. */
    public record ProbeOutcome(ImportStatus status, String reason, String hash, int width, int height,
                                long byteSize) {
    }

    /** Runs on the DB executor thread (needs imageDao); never touches the candidate's FX properties. */
    public ProbeOutcome probe(Path file, ImageDao imageDao) {
        String ext = extensionOf(file);
        if (!SUPPORTED_EXTENSIONS.contains(ext)) {
            return new ProbeOutcome(ImportStatus.UNREADABLE, "Unsupported format: ." + ext, null, 0, 0, 0);
        }
        try {
            int[] dims = readDimensions(file);
            long size = Files.size(file);
            String hash = hashService.hashFile(file);
            if (imageDao.existsByHash(hash)) {
                return new ProbeOutcome(ImportStatus.ALREADY_IN_LIBRARY, "Already in library", hash, dims[0], dims[1], size);
            }
            return new ProbeOutcome(ImportStatus.NEW, null, hash, dims[0], dims[1], size);
        } catch (IOException e) {
            return new ProbeOutcome(ImportStatus.UNREADABLE, "Could not read file: " + e.getMessage(), null, 0, 0, 0);
        }
    }

    /** Marks later duplicates of the same hash within one staging batch. */
    public void markBatchDuplicates(List<ImportCandidate> candidates) {
        Set<String> seen = new HashSet<>();
        for (ImportCandidate c : candidates) {
            if (c.getStatus() != ImportStatus.NEW || c.getHash() == null) continue;
            if (!seen.add(c.getHash())) {
                c.setStatus(ImportStatus.DUPLICATE_IN_BATCH, "Duplicate within this batch");
            }
        }
    }

    public record ImportResult(int imported, int taggedExisting, int skipped, int failed, boolean cancelled) {
    }

    /**
     * Commits a batch in one transaction. Copies/moves are performed as the
     * transaction proceeds; on any failure, or if cancelled mid-batch, every
     * file copied so far is removed and the DB transaction is rolled back, so
     * a cancelled or failed import leaves no trace (PLAN.md 4.1, 7).
     */
    public ImportResult importBatch(List<ImportCandidate> candidates, Set<Integer> batchTagIds, boolean move,
                                     ImageDao imageDao, java.util.concurrent.atomic.AtomicBoolean cancelFlag,
                                     java.util.function.IntConsumer onProgress) {
        List<Path> copiedFiles = new ArrayList<>();
        int imported = 0, taggedExisting = 0, skipped = 0, failed = 0;
        try {
            connection.setAutoCommit(false);
            for (int i = 0; i < candidates.size(); i++) {
                if (cancelFlag != null && cancelFlag.get()) {
                    rollback(copiedFiles);
                    return new ImportResult(0, 0, 0, 0, true);
                }
                ImportCandidate candidate = candidates.get(i);
                onProgress.accept(i);
                ImportStatus status = candidate.getStatus();
                if (status == ImportStatus.UNREADABLE || status == ImportStatus.DUPLICATE_IN_BATCH
                        || !candidate.isIncluded()) {
                    skipped++;
                    continue;
                }
                if (status == ImportStatus.ALREADY_IN_LIBRARY) {
                    ImageRecord existing = imageDao.findByHash(candidate.getHash()).orElse(null);
                    if (existing == null) {
                        skipped++;
                        continue;
                    }
                    try {
                        if (existing.isDeleted()) {
                            restoreFromRecycle(existing, imageDao);
                        }
                        imageDao.addTags(existing.id(), allTags(batchTagIds, candidate));
                        taggedExisting++;
                    } catch (IOException e) {
                        failed++;
                    }
                    continue;
                }
                try {
                    Path target = library.imagePath(candidate.getHash(), extensionOf(candidate.sourcePath()));
                    Files.createDirectories(target.getParent());
                    Files.copy(candidate.sourcePath(), target, StandardCopyOption.REPLACE_EXISTING);
                    copiedFiles.add(target);

                    String verifyHash = hashService.hashFile(target);
                    if (!verifyHash.equals(candidate.getHash())) {
                        throw new IOException("Hash mismatch after copy for " + candidate.fileName());
                    }

                    thumbnailCache.ensureThumbnail(library, target, candidate.getHash());

                    int imageId = imageDao.insert(candidate.getHash(), extensionOf(candidate.sourcePath()),
                            candidate.fileName(), candidate.getWidth(), candidate.getHeight(),
                            candidate.getByteSize(), System.currentTimeMillis(),
                            candidate.sourcePath().toAbsolutePath().toString());
                    imageDao.addTags(imageId, allTags(batchTagIds, candidate));

                    if (move) {
                        Files.deleteIfExists(candidate.sourcePath());
                    }
                    imported++;
                } catch (IOException e) {
                    failed++;
                }
            }
            connection.commit();
        } catch (SQLException | RuntimeException e) {
            rollback(copiedFiles);
            throw new RuntimeException("Import batch failed", e);
        } finally {
            try {
                connection.setAutoCommit(true);
            } catch (SQLException ignored) {
            }
        }
        return new ImportResult(imported, taggedExisting, skipped, failed, false);
    }

    /** Re-imports a recycled image in place: moves its file back out of recycle/ and clears deleted_at. */
    private void restoreFromRecycle(ImageRecord existing, ImageDao imageDao) throws IOException {
        Path recycled = library.recyclePath(existing.hash(), existing.ext());
        Path target = library.imagePath(existing.hash(), existing.ext());
        if (Files.isRegularFile(recycled)) {
            Files.createDirectories(target.getParent());
            Files.move(recycled, target, StandardCopyOption.REPLACE_EXISTING);
        }
        imageDao.restore(existing.id());
    }

    private void rollback(List<Path> copiedFiles) {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
        }
        for (Path p : copiedFiles) {
            try {
                Files.deleteIfExists(p);
            } catch (IOException ignored) {
            }
        }
    }

    private static Set<Integer> allTags(Set<Integer> batchTagIds, ImportCandidate candidate) {
        Set<Integer> all = new HashSet<>(batchTagIds);
        all.addAll(candidate.tagOverrides());
        return all;
    }

    private static String extensionOf(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static int[] readDimensions(Path file) throws IOException {
        try (ImageInputStream in = ImageIO.createImageInputStream(file.toFile())) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                throw new IOException("No reader for " + file);
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in);
                return new int[]{reader.getWidth(0), reader.getHeight(0)};
            } finally {
                reader.dispose();
            }
        }
    }
}
