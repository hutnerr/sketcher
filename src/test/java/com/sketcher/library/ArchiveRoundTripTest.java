package com.sketcher.library;

import com.sketcher.filter.Filter;
import com.sketcher.model.ImageRecord;
import com.sketcher.model.OrderMode;
import com.sketcher.model.Playlist;
import com.sketcher.model.Preset;
import com.sketcher.model.Segment;
import com.sketcher.model.SessionSpec;
import com.sketcher.model.SourceKind;
import com.sketcher.model.Tag;
import com.sketcher.model.TickBehavior;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises PLAN.md 4.8's "export -> import into an empty library produces
 * an equivalent DB" scenario end to end, including the Phase 6 session
 * features (a playlist segment and a ramped timer) a preset might carry.
 */
class ArchiveRoundTripTest {

    @Test
    void exportingAndImportingReproducesImagesTagsPlaylistsAndPresets(@TempDir Path tempDir) throws IOException {
        try (LibraryService source = new LibraryService(tempDir.resolve("source").toString())) {
            int dogTag = source.tags().create("animal", null);
            int huskyTag = source.tags().create("dog", dogTag);

            int imageA = source.images().insert("hashA", "png", "a.png", 100, 100, 1000,
                    System.currentTimeMillis(), null);
            source.images().addTags(imageA, List.of(huskyTag));
            source.images().setFavorite(imageA, true);
            Files.createDirectories(source.ref().imagePath("hashA", "png").getParent());
            Files.writeString(source.ref().imagePath("hashA", "png"), "fake-png-bytes-a");

            int imageB = source.images().insert("hashB", "png", "b.png", 100, 100, 1000,
                    System.currentTimeMillis(), null);
            Files.createDirectories(source.ref().imagePath("hashB", "png").getParent());
            Files.writeString(source.ref().imagePath("hashB", "png"), "fake-png-bytes-b");

            int playlistId = source.playlists().create("Warm-up set");
            source.playlists().addItem(playlistId, imageB);
            source.playlists().addItem(playlistId, imageA);

            Segment filterSegment = Segment.filterSegment(
                    new Filter.Not(new Filter.TagPredicate(huskyTag, true)), 5, OrderMode.SEQUENTIAL,
                    30, TickBehavior.ADVANCE_IMMEDIATELY, 0).withRampToSeconds(10);
            Segment playlistSegment = Segment.playlistSegment(playlistId, null, OrderMode.SEQUENTIAL,
                    0, TickBehavior.ADVANCE_IMMEDIATELY, 0);
            source.presets().create("Queue A", new SessionSpec(List.of(filterSegment, playlistSegment), null));

            Path archive = tempDir.resolve("export.zip");
            ArchiveExporter.export(source, archive, new ArchiveExporter.Options(false, false));

            try (LibraryService dest = new LibraryService(tempDir.resolve("dest").toString())) {
                ArchiveImporter.Summary summary = ArchiveImporter.dryRun(dest, archive);
                assertEquals(2, summary.newImages());
                assertEquals(0, summary.alreadyPresentImages());
                assertEquals(2, summary.newTags());
                assertEquals(1, summary.totalPlaylists());
                assertEquals(1, summary.totalPresets());

                ArchiveImporter.Result result = ArchiveImporter.apply(dest, archive);
                assertEquals(2, result.newImages());
                assertEquals(0, result.taggedExisting());
                assertEquals(2, result.newTagsCreated());
                assertEquals(1, result.playlistsImported());
                assertEquals(1, result.presetsImported());

                assertEquals(2, dest.images().count(new Filter.All()));
                ImageRecord restoredA = dest.images().findByHash("hashA").orElseThrow();
                assertTrue(restoredA.favorite());
                assertTrue(Files.isRegularFile(dest.ref().imagePath("hashA", "png")));

                List<Tag> destTags = dest.tags().listAll();
                Optional<Tag> destHusky = destTags.stream().filter(t -> t.name().equals("dog")).findFirst();
                assertTrue(destHusky.isPresent());
                assertEquals("animal / dog", dest.tags().fullPath(destHusky.get().id()));
                assertTrue(restoredA.tagIds().contains(destHusky.get().id()));

                Playlist destPlaylist = dest.playlists().listAll().get(0);
                assertEquals("Warm-up set", destPlaylist.name());
                List<Integer> destPlaylistImageIds = dest.playlists().listImageIds(destPlaylist.id(), false);
                assertEquals(2, destPlaylistImageIds.size());
                assertEquals("hashB", dest.images().findById(destPlaylistImageIds.get(0)).orElseThrow().hash());
                assertEquals("hashA", dest.images().findById(destPlaylistImageIds.get(1)).orElseThrow().hash());

                Preset destPreset = dest.presets().listAll().get(0);
                assertEquals("Queue A", destPreset.name());
                SessionSpec destSpec = dest.presets().loadSpec(destPreset.id());
                assertEquals(2, destSpec.segments().size());
                Segment destFilterSegment = destSpec.segments().get(0);
                assertEquals(SourceKind.FILTER, destFilterSegment.sourceKind());
                assertEquals(10, destFilterSegment.rampToSeconds());
                assertEquals(new Filter.Not(new Filter.TagPredicate(destHusky.get().id(), true)), destFilterSegment.filter());
                Segment destPlaylistSegment = destSpec.segments().get(1);
                assertEquals(SourceKind.PLAYLIST, destPlaylistSegment.sourceKind());
                assertEquals(destPlaylist.id(), destPlaylistSegment.playlistId());
            }
        }
    }
}
