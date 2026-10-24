package com.sketcher.ui.settings;

import com.sketcher.config.AppConfig;
import com.sketcher.config.ConfigStore;
import com.sketcher.filter.Filter;
import com.sketcher.library.ArchiveExporter;
import com.sketcher.library.ArchiveImporter;
import com.sketcher.library.LibraryService;
import com.sketcher.ui.common.AsyncTask;
import com.sketcher.ui.common.Dialogs;
import com.sketcher.ui.common.Theme;
import com.sketcher.ui.components.AppButton;
import com.sketcher.ui.components.Card;
import com.sketcher.ui.components.FormRow;
import com.sketcher.ui.components.Page;
import com.sketcher.ui.components.SegmentedControl;
import javafx.application.Platform;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;

/** Theme, library location, and archive export/import (PLAN.md 4.9, 4.8). */
public class SettingsView extends Page {

    public SettingsView(LibraryService library, ConfigStore configStore, AppConfig config, Stage stage) {
        super("Settings");
        getStyleClass().add("settings-view");
        setSubtitle("Appearance, importing, library location, backups, and deleting the library.");

        setScrollingBody(
                buildThemeSection(configStore, config, stage),
                buildImportSection(configStore, config),
                buildLibrarySection(library, configStore, config, stage),
                buildArchiveSection(library, stage),
                buildDangerSection(library, configStore, config));
    }

    private Card buildThemeSection(ConfigStore configStore, AppConfig config, Stage stage) {
        SegmentedControl<String> themeChoice = new SegmentedControl<String>()
                .option(Theme.LIGHT, "Light")
                .option(Theme.DARK, "Dark");
        themeChoice.setValue(Theme.LIGHT.equals(config.theme) ? Theme.LIGHT : Theme.DARK);
        themeChoice.onChange(theme -> {
            config.theme = theme;
            configStore.save(config);
            if (stage.getScene() != null) Theme.apply(stage.getScene(), config.theme);
        });

        return new Card("Appearance").add(new FormRow("Theme", themeChoice));
    }

    private Card buildImportSection(ConfigStore configStore, AppConfig config) {
        CheckBox skipDuplicates = new CheckBox("Skip duplicates when staging");
        skipDuplicates.setSelected(config.skipDuplicatesOnImport);
        skipDuplicates.setOnAction(e -> {
            config.skipDuplicatesOnImport = skipDuplicates.isSelected();
            configStore.save(config);
        });

        return new Card("Import").add(new FormRow("Duplicates", skipDuplicates)
                .hint("Files already in the library, or repeated within one batch, are left out of the staging list."));
    }

    private Card buildLibrarySection(LibraryService library, ConfigStore configStore, AppConfig config, Stage stage) {
        Label pathLabel = new Label(library.root().toString());
        pathLabel.setWrapText(true);
        pathLabel.getStyleClass().add("muted-label");

        return new Card("Library", "Where Sketcher keeps your originals, tags, and settings.").add(
                new FormRow("Location", pathLabel),
                new FormRow("",
                        AppButton.secondary("Reveal in file manager", () -> reveal(library.root())),
                        AppButton.secondary("Change library…", () -> changeLibrary(stage, configStore, config))));
    }

    private Card buildArchiveSection(LibraryService library, Stage stage) {
        return new Card("Backup",
                "Move this library to another computer, or merge another Sketcher library into this one.").add(
                new FormRow("Archive",
                        AppButton.secondary("Export library…", () -> exportLibrary(library, stage)),
                        AppButton.secondary("Import archive…", () -> importArchive(library, stage))));
    }

    private Card buildDangerSection(LibraryService library, ConfigStore configStore, AppConfig config) {
        return new Card("Delete library",
                "Permanently deletes every image, tag, playlist, preset, and practice history in this library, "
                        + "and resets all settings. Sketcher then closes; open it again to choose or create a library.")
                .add(new FormRow("", AppButton.danger("Delete library…", () -> deleteLibrary(library, configStore, config))));
    }

    /**
     * Two steps on purpose: a summary of what goes, then typing DELETE. The
     * deletion runs as the last job on the DB thread, so nothing still queued
     * there can touch the database after it's closed and removed.
     */
    private void deleteLibrary(LibraryService library, ConfigStore configStore, AppConfig config) {
        AsyncTask.runDb(() -> library.images().count(new Filter.All()), imageCount -> {
            Path root = library.root();
            boolean confirmed = Dialogs.confirm("Delete library",
                    "This permanently deletes " + imageCount + (imageCount == 1 ? " image" : " images")
                            + " and everything else in the library at\n" + root
                            + "\n\nIt does not go to the recycle bin and cannot be undone. Exports in its exports folder are kept."
                            + "\n\nAll settings are reset too.");
            if (!confirmed) return;
            boolean typed = Dialogs.prompt("Delete library", "Type DELETE to confirm", "")
                    .map("DELETE"::equals).orElse(false);
            if (!typed) return;

            AsyncTask.runDb(() -> {
                library.close();
                try {
                    LibraryService.deleteLibraryFiles(library.ref());
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                return null;
            }, ignored -> {
                config.resetToDefaults();
                configStore.save(config);
                Dialogs.info("Library deleted", "Sketcher will close now. Open it again to choose or create a library.");
                Platform.exit();
            }, err -> {
                // The library is already closed, so carrying on would hit a dead connection; reset and exit anyway.
                config.resetToDefaults();
                configStore.save(config);
                Dialogs.error("Could not delete everything",
                        "Some files could not be removed: " + err.getMessage()
                                + "\n\nSettings were reset. Sketcher will close; you can delete the folder by hand:\n" + root);
                Platform.exit();
            });
        }, err -> Dialogs.error("Could not read library", err.getMessage()));
    }

    private void exportLibrary(LibraryService library, Stage stage) {
        boolean includeHistory = Dialogs.confirm("Export options", "Include practice history (view events)?");
        boolean includeThumbnails = Dialogs.confirm("Export options",
                "Include thumbnails? They're regenerable on import, so this only saves re-decoding time.");

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export library archive");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Sketcher archive", "*.zip"));
        chooser.setInitialFileName("sketcher-export.zip");
        File file = chooser.showSaveDialog(stage);
        if (file == null) return;
        Path zipPath = file.toPath();

        AsyncTask.runDb(() -> {
            try {
                ArchiveExporter.export(library, zipPath, new ArchiveExporter.Options(includeHistory, includeThumbnails));
                return null;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }, ignored -> Dialogs.info("Export complete", "Library exported to\n" + zipPath),
                err -> Dialogs.error("Export failed", err.getMessage()));
    }

    private void importArchive(LibraryService library, Stage stage) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Import library archive");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Sketcher archive", "*.zip"));
        File file = chooser.showOpenDialog(stage);
        if (file == null) return;
        Path zipPath = file.toPath();

        AsyncTask.runDb(() -> {
            try {
                return ArchiveImporter.dryRun(library, zipPath);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }, summary -> {
            String message = summary.newImages() + " new image(s), " + summary.alreadyPresentImages()
                    + " already present, " + summary.newTags() + " new tag(s), " + summary.totalPlaylists()
                    + " playlist(s), " + summary.totalPresets() + " preset(s).\n\nImport now?";
            if (!Dialogs.confirm("Import archive", message)) return;
            applyImport(library, zipPath);
        }, err -> Dialogs.error("Could not read archive", err.getMessage()));
    }

    private void applyImport(LibraryService library, Path zipPath) {
        AsyncTask.runDb(() -> {
            try {
                return ArchiveImporter.apply(library, zipPath);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }, result -> Dialogs.info("Import complete",
                result.newImages() + " new image(s) imported, " + result.taggedExisting()
                        + " existing image(s) tagged, " + result.newTagsCreated() + " new tag(s), "
                        + result.playlistsImported() + " playlist(s), " + result.presetsImported() + " preset(s)."),
                err -> Dialogs.error("Import failed", err.getMessage()));
    }

    /**
     * Desktop.open() shells out to the system file manager and blocks until
     * it returns, which on some Linux file managers can take seconds (or
     * hang) — run it off the FX thread so it can't freeze the UI.
     */
    private void reveal(Path root) {
        if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
            Dialogs.info("Library folder", root.toString());
            return;
        }
        AsyncTask.runIo(() -> {
            try {
                Desktop.getDesktop().open(root.toFile());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            return null;
        }, ignored -> {
        }, err -> Dialogs.error("Could not open file manager", root + "\n\n" + err.getMessage()));
    }

    private void changeLibrary(Stage stage, ConfigStore configStore, AppConfig config) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Choose a library folder");
        var chosen = chooser.showDialog(stage);
        if (chosen == null) return;
        config.noteLibraryUsed(chosen.getAbsolutePath());
        configStore.save(config);
        Dialogs.info("Library changed", "Restart Sketcher to open \"" + chosen.getAbsolutePath() + "\".");
    }
}
