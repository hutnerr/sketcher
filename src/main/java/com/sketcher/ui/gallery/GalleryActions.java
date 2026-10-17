package com.sketcher.ui.gallery;

import com.sketcher.library.LibraryService;
import com.sketcher.model.ImageRecord;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/** Shared delete/restore behavior for the gallery grid/list, the detail view, and the recycle bin (PLAN.md 4.7). */
public final class GalleryActions {
    private GalleryActions() {
    }

    /** Soft-deletes an image and moves its original file into recycle/. Metadata is retained so this is lossless. */
    public static void softDeleteToRecycle(LibraryService library, ImageRecord record) {
        Path source = library.ref().imagePath(record.hash(), record.ext());
        Path target = library.ref().recyclePath(record.hash(), record.ext());
        try {
            if (Files.isRegularFile(source)) {
                Files.createDirectories(target.getParent());
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        library.images().softDelete(record.id(), System.currentTimeMillis());
        library.thumbnails().evict(record.hash());
    }

    /** Moves a recycled image's original back out of recycle/ and clears its deleted_at. */
    public static void restoreFromRecycle(LibraryService library, ImageRecord record) {
        Path source = library.ref().recyclePath(record.hash(), record.ext());
        Path target = library.ref().imagePath(record.hash(), record.ext());
        try {
            if (Files.isRegularFile(source)) {
                Files.createDirectories(target.getParent());
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        library.images().restore(record.id());
    }

    /** Permanently removes a recycled image's file, cached thumbnail, and DB row. Cannot be undone. */
    public static void permanentlyDelete(LibraryService library, ImageRecord record) {
        try {
            Files.deleteIfExists(library.ref().recyclePath(record.hash(), record.ext()));
            Files.deleteIfExists(library.ref().thumbPath(record.hash()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        library.images().permanentlyDelete(record.id());
        library.thumbnails().evict(record.hash());
    }

    /** Permanently removes every recycled image older than the cutoff (PLAN.md 4.9 recycle-bin auto-purge). */
    public static void purgeExpired(LibraryService library, long cutoffDeletedAtMillis) {
        List<ImageRecord> deleted = library.images().listDeleted();
        for (ImageRecord record : deleted) {
            if (record.deletedAt() != null && record.deletedAt() < cutoffDeletedAtMillis) {
                permanentlyDelete(library, record);
            }
        }
    }
}
