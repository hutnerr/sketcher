package com.sketcher.library;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sketcher.db.dao.TagDao;
import com.sketcher.filter.Filter;
import com.sketcher.filter.FilterCodec;
import com.sketcher.model.ImageRecord;
import com.sketcher.model.OrderMode;
import com.sketcher.model.Playlist;
import com.sketcher.model.Preset;
import com.sketcher.model.Segment;
import com.sketcher.model.SessionSpec;
import com.sketcher.model.SourceKind;
import com.sketcher.model.Tag;
import com.sketcher.model.TickBehavior;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Reads an archive written by ArchiveExporter and merges it into an open
 * library (PLAN.md 4.8). Images already present by hash are skipped but
 * gain the archive's tags and favorite flag; tags are matched by full path
 * and created as needed; name-colliding presets and playlists are suffixed
 * rather than overwritten. Thumbnails are always regenerated locally from
 * the extracted original rather than trusted from the archive, so whether
 * the archive bundled them only affects its file size, never correctness.
 */
public class ArchiveImporter {
    private static final Gson GSON = new Gson();

    public record Summary(int newImages, int alreadyPresentImages, int newTags, int totalPlaylists, int totalPresets) {
    }

    public record Result(int newImages, int taggedExisting, int newTagsCreated, int playlistsImported,
                          int presetsImported) {
    }

    public static Summary dryRun(LibraryService library, Path zipPath) throws IOException {
        JsonObject manifest = readManifest(zipPath);
        JsonArray images = manifest.getAsJsonArray("images");
        int newImages = 0;
        for (JsonElement el : images) {
            if (!library.images().existsByHash(el.getAsJsonObject().get("hash").getAsString())) newImages++;
        }

        int newTags = 0;
        for (JsonElement el : manifest.getAsJsonArray("tags")) {
            if (resolveExistingTagPath(library.tags(), el.getAsString()) == null) newTags++;
        }

        return new Summary(newImages, images.size() - newImages, newTags,
                manifest.getAsJsonArray("playlists").size(), manifest.getAsJsonArray("presets").size());
    }

    public static Result apply(LibraryService library, Path zipPath) throws IOException {
        JsonObject manifest = readManifest(zipPath);
        TagDao tagDao = library.tags();
        Map<String, Integer> tagPathToId = new HashMap<>();
        int[] tagsCreated = {0};

        for (JsonElement el : manifest.getAsJsonArray("tags")) {
            resolveOrCreateTagPath(tagDao, el.getAsString(), tagPathToId, tagsCreated);
        }

        Map<String, Integer> hashToImageId = new HashMap<>();
        int newImages = 0;
        int taggedExisting = 0;

        try (ZipFile zip = new ZipFile(zipPath.toFile())) {
            for (JsonElement el : manifest.getAsJsonArray("images")) {
                JsonObject img = el.getAsJsonObject();
                String hash = img.get("hash").getAsString();
                String ext = img.get("ext").getAsString();

                List<Integer> tagIds = new ArrayList<>();
                for (JsonElement tp : img.getAsJsonArray("tagPaths")) {
                    tagIds.add(resolveOrCreateTagPath(tagDao, tp.getAsString(), tagPathToId, tagsCreated));
                }
                boolean favorite = img.has("favorite") && img.get("favorite").getAsBoolean();

                ImageRecord existing = library.images().findByHash(hash).orElse(null);
                int imageId;
                if (existing != null) {
                    imageId = existing.id();
                    if (existing.isDeleted()) {
                        library.images().restore(imageId);
                    }
                    library.images().addTags(imageId, tagIds);
                    if (favorite && !existing.favorite()) library.images().setFavorite(imageId, true);
                    taggedExisting++;
                } else {
                    imageId = importNewImage(library, zip, img, hash, ext, tagIds, favorite);
                    newImages++;
                }
                hashToImageId.put(hash, imageId);
            }
        }

        Map<String, Integer> playlistIdByOriginalName = new HashMap<>();
        List<String> existingPlaylistNames = new ArrayList<>(library.playlists().listAll().stream().map(Playlist::name).toList());
        for (JsonElement el : manifest.getAsJsonArray("playlists")) {
            JsonObject pl = el.getAsJsonObject();
            String originalName = pl.get("name").getAsString();
            String name = uniqueName(originalName, existingPlaylistNames);
            existingPlaylistNames.add(name);
            int playlistId = library.playlists().create(name);
            playlistIdByOriginalName.put(originalName, playlistId);
            for (JsonElement h : pl.getAsJsonArray("imageHashes")) {
                Integer imageId = hashToImageId.computeIfAbsent(h.getAsString(),
                        hash -> library.images().findByHash(hash).map(ImageRecord::id).orElse(null));
                if (imageId != null) library.playlists().addItem(playlistId, imageId);
            }
        }

        List<String> existingPresetNames = new ArrayList<>(library.presets().listAll().stream().map(Preset::name).toList());
        int presetsImported = 0;
        for (JsonElement el : manifest.getAsJsonArray("presets")) {
            JsonObject presetJson = el.getAsJsonObject();
            String name = uniqueName(presetJson.get("name").getAsString(), existingPresetNames);
            existingPresetNames.add(name);

            List<Segment> segments = new ArrayList<>();
            for (JsonElement segEl : presetJson.getAsJsonArray("segments")) {
                segments.add(decodeSegment(segEl.getAsJsonObject(), tagDao, tagPathToId, tagsCreated,
                        playlistIdByOriginalName));
            }
            int presetId = library.presets().create(name, new SessionSpec(segments, null));
            if (presetJson.has("pinned") && presetJson.get("pinned").getAsBoolean()) {
                library.presets().setPinned(presetId, true);
            }
            presetsImported++;
        }

        return new Result(newImages, taggedExisting, tagsCreated[0], playlistIdByOriginalName.size(), presetsImported);
    }

    private static int importNewImage(LibraryService library, ZipFile zip, JsonObject img, String hash, String ext,
                                        List<Integer> tagIds, boolean favorite) throws IOException {
        Path target = library.ref().imagePath(hash, ext);
        Files.createDirectories(target.getParent());
        ZipEntry entry = zip.getEntry("images/" + hash + "." + ext);
        if (entry == null) {
            throw new IOException("Archive is missing the file for image " + hash + "." + ext);
        }
        try (InputStream in = zip.getInputStream(entry)) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }

        int imageId = library.images().insert(hash, ext,
                img.has("originalName") ? img.get("originalName").getAsString() : null,
                img.get("width").getAsInt(), img.get("height").getAsInt(),
                img.get("byteSize").getAsLong(), img.get("importedAt").getAsLong(), null);
        library.images().addTags(imageId, tagIds);
        if (favorite) library.images().setFavorite(imageId, true);

        if (img.has("viewEvents")) {
            long maxSeenAt = 0;
            int count = 0;
            for (JsonElement ve : img.getAsJsonArray("viewEvents")) {
                JsonObject veo = ve.getAsJsonObject();
                long seenAt = veo.get("seenAt").getAsLong();
                library.images().insertRawViewEvent(imageId, seenAt, veo.get("shownMs").getAsLong());
                maxSeenAt = Math.max(maxSeenAt, seenAt);
                count++;
            }
            if (count > 0) library.images().setSeenStats(imageId, maxSeenAt, count);
        } else {
            Long lastSeenAt = img.has("lastSeenAt") ? img.get("lastSeenAt").getAsLong() : null;
            int seenCount = img.has("seenCount") ? img.get("seenCount").getAsInt() : 0;
            if (lastSeenAt != null || seenCount > 0) library.images().setSeenStats(imageId, lastSeenAt, seenCount);
        }

        try {
            library.thumbnails().ensureThumbnail(library.ref(), target, hash);
        } catch (IOException ignored) {
            // best-effort: a missing thumbnail just regenerates the next time it's viewed
        }
        return imageId;
    }

    private static Segment decodeSegment(JsonObject o, TagDao tagDao, Map<String, Integer> tagPathToId,
                                          int[] tagsCreated, Map<String, Integer> playlistIdByOriginalName) {
        SourceKind sourceKind = SourceKind.valueOf(o.get("sourceKind").getAsString());
        Integer itemCount = o.has("itemCount") ? o.get("itemCount").getAsInt() : null;
        OrderMode orderMode = OrderMode.valueOf(o.get("orderMode").getAsString());
        int secondsPerImage = o.get("secondsPerImage").getAsInt();
        TickBehavior tickBehavior = TickBehavior.valueOf(o.get("tickBehavior").getAsString());
        int breakSeconds = o.get("breakSeconds").getAsInt();

        Segment segment;
        if (sourceKind == SourceKind.FILTER) {
            Filter filter = decodePortableFilter(o.getAsJsonObject("filter"), tagDao, tagPathToId, tagsCreated);
            segment = Segment.filterSegment(filter, itemCount, orderMode, secondsPerImage, tickBehavior, breakSeconds);
        } else {
            String playlistName = o.has("playlistName") ? o.get("playlistName").getAsString() : null;
            Integer playlistId = playlistName != null ? playlistIdByOriginalName.get(playlistName) : null;
            segment = playlistId != null
                    ? Segment.playlistSegment(playlistId, itemCount, orderMode, secondsPerImage, tickBehavior, breakSeconds)
                    : Segment.filterSegment(new Filter.All(), itemCount, orderMode, secondsPerImage, tickBehavior, breakSeconds);
        }
        if (o.has("rampToSeconds")) segment = segment.withRampToSeconds(o.get("rampToSeconds").getAsInt());
        if (o.has("memoryShowSeconds") && o.has("memoryHideSeconds")) {
            segment = segment.withMemoryMode(o.get("memoryShowSeconds").getAsInt(), o.get("memoryHideSeconds").getAsInt());
        }
        return segment;
    }

    /** Same shape as FilterCodec, but a TagPredicate's path is resolved/created in the destination library. */
    private static Filter decodePortableFilter(JsonObject o, TagDao tagDao, Map<String, Integer> tagPathToId,
                                                 int[] tagsCreated) {
        return FilterCodec.fromJson(o, tag ->
                resolveOrCreateTagPath(tagDao, tag.get("tagPath").getAsString(), tagPathToId, tagsCreated));
    }

    /** Walks a " / "-joined tag path without creating anything; null if any segment is missing. */
    private static Integer resolveExistingTagPath(TagDao tagDao, String path) {
        Integer parentId = null;
        for (String part : path.split(" / ")) {
            Tag match = tagDao.listChildren(parentId).stream().filter(t -> t.name().equals(part)).findFirst().orElse(null);
            if (match == null) return null;
            parentId = match.id();
        }
        return parentId;
    }

    /** Walks a " / "-joined tag path, creating any missing segment, caching every prefix it resolves. */
    private static int resolveOrCreateTagPath(TagDao tagDao, String path, Map<String, Integer> cache, int[] created) {
        Integer cached = cache.get(path);
        if (cached != null) return cached;

        Integer parentId = null;
        StringBuilder builtPath = new StringBuilder();
        for (String part : path.split(" / ")) {
            if (builtPath.length() > 0) builtPath.append(" / ");
            builtPath.append(part);
            String key = builtPath.toString();
            Integer id = cache.get(key);
            if (id == null) {
                final Integer searchParentId = parentId;
                Tag match = tagDao.listChildren(parentId).stream()
                        .filter(t -> t.name().equals(part)).findFirst().orElse(null);
                if (match != null) {
                    id = match.id();
                } else {
                    id = tagDao.create(part, searchParentId);
                    created[0]++;
                }
                cache.put(key, id);
            }
            parentId = id;
        }
        return parentId;
    }

    private static String uniqueName(String base, List<String> existing) {
        if (!existing.contains(base)) return base;
        int n = 2;
        while (existing.contains(base + " (" + n + ")")) n++;
        return base + " (" + n + ")";
    }

    private static JsonObject readManifest(Path zipPath) throws IOException {
        try (ZipFile zip = new ZipFile(zipPath.toFile())) {
            ZipEntry entry = zip.getEntry("manifest.json");
            if (entry == null) throw new IOException("Not a Sketcher archive: missing manifest.json");
            try (InputStream in = zip.getInputStream(entry)) {
                return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
            }
        }
    }
}
