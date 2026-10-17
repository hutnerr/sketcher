package com.sketcher.ui.settings;

import com.sketcher.config.AppConfig;
import com.sketcher.config.ConfigStore;
import com.sketcher.library.ArchiveExporter;
import com.sketcher.library.ArchiveImporter;
import com.sketcher.library.LibraryService;
import com.sketcher.ui.common.AsyncTask;
import com.sketcher.ui.common.Dialogs;
import com.sketcher.ui.common.Theme;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;

/** Theme, library location, and archive export/import (PLAN.md 4.9, 4.8). */
public class SettingsView extends VBox {

    public SettingsView(LibraryService library, ConfigStore configStore, AppConfig config, Stage stage) {
        getStyleClass().add("settings-view");
        setSpacing(20);
        setPadding(new Insets(24));

        Label heading = new Label("Settings");
        heading.getStyleClass().add("home-title");

        getChildren().addAll(heading,
                section("Theme", buildThemeSection(configStore, config, stage)),
                section("Library", buildLibrarySection(library, configStore, config, stage)),
                section("Backup", buildArchiveSection(library, stage)));
    }

    private VBox section(String title, VBox content) {
        Label label = new Label(title);
        label.getStyleClass().add("help-heading");
        VBox box = new VBox(12, label, content);
        box.getStyleClass().add("form-section");
        return box;
    }

    private VBox buildThemeSection(ConfigStore configStore, AppConfig config, Stage stage) {
        ChoiceBox<String> themeChoice = new ChoiceBox<>();
        themeChoice.getItems().addAll("Dark", "Light");
        themeChoice.getSelectionModel().select(Theme.LIGHT.equals(config.theme) ? "Light" : "Dark");
        themeChoice.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> {
            config.theme = "Light".equals(b) ? Theme.LIGHT : Theme.DARK;
            configStore.save(config);
            if (stage.getScene() != null) Theme.apply(stage.getScene(), config.theme);
        });

        return new VBox(themeChoice);
    }

    private VBox buildLibrarySection(LibraryService library, ConfigStore configStore, AppConfig config, Stage stage) {
        Label pathLabel = new Label(library.root().toString());
        pathLabel.setWrapText(true);

        Button revealButton = new Button("Reveal in file manager");
        revealButton.setOnAction(e -> reveal(library.root()));
        Button changeButton = new Button("Change library…");
        changeButton.setOnAction(e -> changeLibrary(stage, configStore, config));
        HBox libraryActions = new HBox(10, revealButton, changeButton);

        return new VBox(8, pathLabel, libraryActions);
    }

    private VBox buildArchiveSection(LibraryService library, Stage stage) {
        Label hint = new Label("Move this library to another computer, or merge another Sketcher library into this one.");
        hint.setWrapText(true);

        Button exportButton = new Button("Export library…");
        exportButton.setOnAction(e -> exportLibrary(library, stage));
        Button importButton = new Button("Import archive…");
        importButton.setOnAction(e -> importArchive(library, stage));
        HBox actions = new HBox(10, exportButton, importButton);

        return new VBox(6, hint, actions);
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
