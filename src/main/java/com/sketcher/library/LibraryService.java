package com.sketcher.library;

import com.sketcher.config.LibraryRef;
import com.sketcher.db.Database;
import com.sketcher.db.dao.ImageDao;
import com.sketcher.db.dao.PlaylistDao;
import com.sketcher.db.dao.PresetDao;
import com.sketcher.db.dao.SettingDao;
import com.sketcher.db.dao.TagDao;
import com.sketcher.db.dao.ViewDao;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Opens one library root and owns everything needed to work with it: the DB
 * connection, DAOs, and the import/thumbnail services. Passed around the UI
 * as a single context object rather than wiring each dependency separately.
 */
public class LibraryService implements AutoCloseable {
    private final LibraryRef ref;
    private final Database database;
    private final ImageDao imageDao;
    private final TagDao tagDao;
    private final PlaylistDao playlistDao;
    private final ViewDao viewDao;
    private final PresetDao presetDao;
    private final SettingDao settingDao;
    private final ThumbnailCache thumbnailCache;
    private final ImportService importService;

    public LibraryService(String libraryRootPath) {
        this.ref = LibraryRef.of(libraryRootPath);
        try {
            ref.ensureLayout();
        } catch (IOException e) {
            throw new RuntimeException("Could not create library layout at " + libraryRootPath, e);
        }
        this.database = new Database(ref.dbFile());
        this.imageDao = new ImageDao(database.connection());
        this.tagDao = new TagDao(database.connection());
        this.playlistDao = new PlaylistDao(database.connection());
        this.viewDao = new ViewDao(database.connection());
        this.presetDao = new PresetDao(database.connection());
        this.settingDao = new SettingDao(database.connection());
        this.thumbnailCache = new ThumbnailCache();
        this.importService = new ImportService(ref, database.connection(), new HashService(), thumbnailCache);
    }

    public LibraryRef ref() {
        return ref;
    }

    public ImageDao images() {
        return imageDao;
    }

    public TagDao tags() {
        return tagDao;
    }

    public PlaylistDao playlists() {
        return playlistDao;
    }

    public ViewDao views() {
        return viewDao;
    }

    public PresetDao presets() {
        return presetDao;
    }

    public SettingDao settings() {
        return settingDao;
    }

    public ThumbnailCache thumbnails() {
        return thumbnailCache;
    }

    public ImportService importService() {
        return importService;
    }

    public Path root() {
        return ref.root();
    }

    @Override
    public void close() {
        database.close();
    }
}
