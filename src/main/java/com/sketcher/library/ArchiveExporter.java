package com.sketcher.library;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sketcher.db.dao.TagDao;
import com.sketcher.filter.Filter;
import com.sketcher.model.ImageRecord;
import com.sketcher.model.OrderMode;
import com.sketcher.model.Playlist;
import com.sketcher.model.Preset;
import com.sketcher.model.Segment;
import com.sketcher.model.SessionSpec;
import com.sketcher.model.SourceKind;
import com.sketcher.model.Tag;
import com.sketcher.model.ViewEvent;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Writes a portable .zip archive of a library (PLAN.md 4.8): manifest.json
 * plus the original image files, named by hash. Tags, playlists, and preset
 * segments are serialized by full path/name rather than numeric id, so the
 * archive can be re-imported into a different library without id
 * collisions (PLAN.md: "tags are matched by full path", "name-colliding
 * presets and playlists are suffixed"). Recycled images are excluded, since
 * the archive is a snapshot of the active library, not the recycle bin.
 */
public class ArchiveExporter {
    private static final int SCHEMA_VERSION = 1;
    private static final Gson GSON = new Gson();

    public record Options(boolean includeHistory, boolean includeThumbnails) {
    }

    public static void export(LibraryService library, Path zipPath, Options options) throws IOException {
        JsonObject manifest = buildManifest(library, options);
        List<ImageRecord> images = library.images().query(new Filter.All(), OrderMode.SEQUENTIAL, null);

        Files.createDirectories(zipPath.getParent());
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(zipPath))) {
            writeBytesEntry(zip, "manifest.json", GSON.toJson(manifest).getBytes(StandardCharsets.UTF_8));

            for (ImageRecord image : images) {
                Path original = library.ref().imagePath(image.hash(), image.ext());
                if (Files.isRegularFile(original)) {
                    writeFileEntry(zip, "images/" + image.hash() + "." + image.ext(), original);
                }
                if (options.includeThumbnails()) {
                    Path thumb = library.ref().thumbPath(image.hash());
                    if (Files.isRegularFile(thumb)) {
                        writeFileEntry(zip, "thumbs/" + image.hash() + ".jpg", thumb);
                    }
                }
            }
        }
    }

    private static JsonObject buildManifest(LibraryService library, Options options) {
        JsonObject root = new JsonObject();
        root.addProperty("schemaVersion", SCHEMA_VERSION);
        root.addProperty("exportedAt", System.currentTimeMillis());

        TagDao tagDao = library.tags();
        JsonArray tagPaths = new JsonArray();
        for (Tag tag : tagDao.listAll()) {
            tagPaths.add(tagDao.fullPath(tag.id()));
        }
        root.add("tags", tagPaths);

        JsonArray imagesJson = new JsonArray();
        for (ImageRecord image : library.images().query(new Filter.All(), OrderMode.SEQUENTIAL, null)) {
            imagesJson.add(encodeImage(image, tagDao, library, options));
        }
        root.add("images", imagesJson);

        JsonArray playlistsJson = new JsonArray();
        for (Playlist playlist : library.playlists().listAll()) {
            playlistsJson.add(encodePlaylist(playlist, library));
        }
        root.add("playlists", playlistsJson);

        JsonArray presetsJson = new JsonArray();
        for (Preset preset : library.presets().listAll()) {
            presetsJson.add(encodePreset(preset, library, tagDao));
        }
        root.add("presets", presetsJson);

        return root;
    }

    private static JsonObject encodeImage(ImageRecord image, TagDao tagDao, LibraryService library, Options options) {
        JsonObject o = new JsonObject();
        o.addProperty("hash", image.hash());
        o.addProperty("ext", image.ext());
        if (image.originalName() != null) o.addProperty("originalName", image.originalName());
        o.addProperty("width", image.width());
        o.addProperty("height", image.height());
        o.addProperty("byteSize", image.byteSize());
        o.addProperty("importedAt", image.importedAt());
        o.addProperty("favorite", image.favorite());

        JsonArray tagPaths = new JsonArray();
        for (int tagId : image.tagIds()) {
            tagPaths.add(tagDao.fullPath(tagId));
        }
        o.add("tagPaths", tagPaths);

        if (options.includeHistory()) {
            JsonArray events = new JsonArray();
            for (ViewEvent event : library.images().listViewEvents(image.id())) {
                JsonObject e = new JsonObject();
                e.addProperty("seenAt", event.seenAt());
                e.addProperty("shownMs", event.shownMs());
                events.add(e);
            }
            o.add("viewEvents", events);
        } else {
            if (image.lastSeenAt() != null) o.addProperty("lastSeenAt", image.lastSeenAt());
            o.addProperty("seenCount", image.seenCount());
        }
        return o;
    }

    private static JsonObject encodePlaylist(Playlist playlist, LibraryService library) {
        JsonObject o = new JsonObject();
        o.addProperty("name", playlist.name());
        JsonArray hashes = new JsonArray();
        for (int imageId : library.playlists().listImageIds(playlist.id(), false)) {
            library.images().findById(imageId).ifPresent(img -> hashes.add(img.hash()));
        }
        o.add("imageHashes", hashes);
        return o;
    }

    private static JsonObject encodePreset(Preset preset, LibraryService library, TagDao tagDao) {
        JsonObject o = new JsonObject();
        o.addProperty("name", preset.name());
        o.addProperty("pinned", preset.pinned());
        JsonArray segments = new JsonArray();
        SessionSpec spec = library.presets().loadSpec(preset.id());
        for (Segment segment : spec.segments()) {
            segments.add(encodeSegment(segment, library, tagDao));
        }
        o.add("segments", segments);
        return o;
    }

    private static JsonObject encodeSegment(Segment segment, LibraryService library, TagDao tagDao) {
        JsonObject o = new JsonObject();
        o.addProperty("sourceKind", segment.sourceKind().name());
        if (segment.sourceKind() == SourceKind.FILTER) {
            o.add("filter", encodePortableFilter(segment.filter(), tagDao));
        } else {
            library.playlists().listAll().stream()
                    .filter(p -> p.id() == segment.playlistId())
                    .findFirst()
                    .ifPresent(p -> o.addProperty("playlistName", p.name()));
        }
        if (segment.itemCount() != null) o.addProperty("itemCount", segment.itemCount());
        o.addProperty("orderMode", segment.orderMode().name());
        o.addProperty("secondsPerImage", segment.secondsPerImage());
        o.addProperty("tickBehavior", segment.tickBehavior().name());
        o.addProperty("breakSeconds", segment.breakSeconds());
        if (segment.memoryShowSeconds() != null) o.addProperty("memoryShowSeconds", segment.memoryShowSeconds());
        if (segment.memoryHideSeconds() != null) o.addProperty("memoryHideSeconds", segment.memoryHideSeconds());
        if (segment.rampToSeconds() != null) o.addProperty("rampToSeconds", segment.rampToSeconds());
        return o;
    }

    /** Same shape as FilterCodec, but a TagPredicate carries the tag's full path instead of its local numeric id. */
    private static JsonObject encodePortableFilter(Filter filter, TagDao tagDao) {
        JsonObject o = new JsonObject();
        switch (filter) {
            case Filter.All ignored -> o.addProperty("type", "all");
            case Filter.FavoritePredicate ignored -> o.addProperty("type", "favorite");
            case Filter.UntaggedPredicate ignored -> o.addProperty("type", "untagged");
            case Filter.NameContainsPredicate p -> {
                o.addProperty("type", "nameContains");
                o.addProperty("text", p.text());
            }
            case Filter.ImportedBetweenPredicate p -> {
                o.addProperty("type", "importedBetween");
                o.addProperty("from", p.fromMillis());
                o.addProperty("to", p.toMillis());
            }
            case Filter.NotSeenSincePredicate p -> {
                o.addProperty("type", "notSeenSince");
                o.addProperty("durationMillis", p.durationMillis());
            }
            case Filter.TagPredicate p -> {
                o.addProperty("type", "tag");
                o.addProperty("tagPath", tagDao.fullPath(p.tagId()));
                o.addProperty("includeDescendants", p.includeDescendants());
            }
            case Filter.Not n -> {
                o.addProperty("type", "not");
                o.add("child", encodePortableFilter(n.child(), tagDao));
            }
            case Filter.And a -> {
                o.addProperty("type", "and");
                o.add("children", encodePortableFilterArray(a.children(), tagDao));
            }
            case Filter.Or orFilter -> {
                o.addProperty("type", "or");
                o.add("children", encodePortableFilterArray(orFilter.children(), tagDao));
            }
        }
        return o;
    }

    private static JsonArray encodePortableFilterArray(List<Filter> filters, TagDao tagDao) {
        JsonArray array = new JsonArray();
        for (Filter f : filters) array.add(encodePortableFilter(f, tagDao));
        return array;
    }

    private static void writeBytesEntry(ZipOutputStream zip, String name, byte[] content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(content);
        zip.closeEntry();
    }

    private static void writeFileEntry(ZipOutputStream zip, String name, Path file) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        Files.copy(file, zip);
        zip.closeEntry();
    }
}
