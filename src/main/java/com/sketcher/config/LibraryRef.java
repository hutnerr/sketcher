package com.sketcher.config;

import java.nio.file.Path;

/**
 * Resolves the fixed subpaths inside a chosen library root
 * (see PLAN.md 1.1 for the on-disk layout).
 */
public class LibraryRef {
    private final Path root;

    public LibraryRef(Path root) {
        this.root = root;
    }

    public static LibraryRef of(String path) {
        return new LibraryRef(Path.of(path));
    }

    public Path root() {
        return root;
    }

    public Path dbFile() {
        return root.resolve("sketcher.db");
    }

    public Path imagesDir() {
        return root.resolve("images");
    }

    public Path thumbsDir() {
        return root.resolve("thumbs");
    }

    public Path recycleDir() {
        return root.resolve("recycle");
    }

    public Path exportsDir() {
        return root.resolve("exports");
    }

    /** Content-addressed path for an original: images/ab/cd/<hash>.<ext> */
    public Path imagePath(String hash, String ext) {
        return fanout(imagesDir(), hash).resolve(hash + "." + ext);
    }

    /** Content-addressed path for a cached thumbnail: thumbs/ab/cd/<hash>.jpg */
    public Path thumbPath(String hash) {
        return fanout(thumbsDir(), hash).resolve(hash + ".jpg");
    }

    public Path recyclePath(String hash, String ext) {
        return recycleDir().resolve(hash + "." + ext);
    }

    private static Path fanout(Path base, String hash) {
        return base.resolve(hash.substring(0, 2)).resolve(hash.substring(2, 4));
    }

    public void ensureLayout() throws java.io.IOException {
        java.nio.file.Files.createDirectories(root);
        java.nio.file.Files.createDirectories(imagesDir());
        java.nio.file.Files.createDirectories(thumbsDir());
        java.nio.file.Files.createDirectories(recycleDir());
        java.nio.file.Files.createDirectories(exportsDir());
    }
}
