package com.sketcher.library;

import com.sketcher.config.LibraryRef;
import javafx.scene.image.Image;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Generates and serves cached thumbnails. Disk cache lives under
 * <library>/thumbs (disposable, regenerable); a small in-memory LRU on top
 * avoids re-decoding the same thumbnail during fast gallery scrolling
 * (PLAN.md 7, thumbnail/decode performance risk).
 */
public class ThumbnailCache {
    private static final int THUMB_MAX_DIM = 320;
    private static final int MEMORY_CACHE_CAPACITY = 500;

    private final Map<String, Image> memoryCache = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Image> eldest) {
            return size() > MEMORY_CACHE_CAPACITY;
        }
    };

    /** Generates the on-disk thumbnail for a newly imported image, if not already present. */
    public void ensureThumbnail(LibraryRef lib, Path originalFile, String hash) throws IOException {
        Path thumbPath = lib.thumbPath(hash);
        if (Files.isRegularFile(thumbPath)) return;
        Files.createDirectories(thumbPath.getParent());

        BufferedImage source = ImageIO.read(originalFile.toFile());
        if (source == null) {
            throw new IOException("Could not decode image for thumbnailing: " + originalFile);
        }
        double scale = Math.min(1.0, (double) THUMB_MAX_DIM / Math.max(source.getWidth(), source.getHeight()));
        int w = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int h = Math.max(1, (int) Math.round(source.getHeight() * scale));

        BufferedImage scaled = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(source, 0, 0, w, h, null);
        g.dispose();

        ImageIO.write(scaled, "jpg", thumbPath.toFile());
    }

    public synchronized Image load(LibraryRef lib, String hash) {
        return memoryCache.computeIfAbsent(hash, h -> {
            Path thumbPath = lib.thumbPath(h);
            if (!Files.isRegularFile(thumbPath)) return null;
            return new Image(thumbPath.toUri().toString(), THUMB_MAX_DIM, THUMB_MAX_DIM, true, true, true);
        });
    }

    public void evict(String hash) {
        synchronized (this) {
            memoryCache.remove(hash);
        }
    }
}
