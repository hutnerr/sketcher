package com.sketcher;

import com.sketcher.config.AppConfig;
import com.sketcher.config.ConfigStore;
import com.sketcher.filter.Filter;
import com.sketcher.library.LibraryService;
import com.sketcher.model.SessionSpec;
import com.sketcher.ui.Shell;
import com.sketcher.ui.common.AsyncTask;
import com.sketcher.ui.common.Dialogs;
import com.sketcher.ui.common.Theme;
import com.sketcher.ui.gallery.GalleryActions;
import com.sketcher.ui.gallery.GalleryView;
import com.sketcher.ui.home.HomeView;
import com.sketcher.ui.imports.ImportView;
import com.sketcher.ui.playlist.PlaylistView;
import com.sketcher.ui.practice.PracticeContainerView;
import com.sketcher.ui.settings.HelpView;
import com.sketcher.ui.settings.SettingsView;
import com.sketcher.ui.tags.TagManagerView;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;

/** App entry point: owns config, the chosen library, and the navigation shell (PLAN.md 3). */
public class SketcherApp extends Application {
    private final ConfigStore configStore = new ConfigStore();
    private AppConfig config;
    private LibraryService library;

    @Override
    public void start(Stage stage) {
        stage.setTitle("Sketcher");
        config = configStore.load();

        String libraryRoot = resolveLibraryRoot(stage);
        if (libraryRoot == null) {
            return;
        }
        config.noteLibraryUsed(libraryRoot);
        configStore.save(config);
        library = new LibraryService(libraryRoot);
        boolean freshLibrary = library.images().count(new Filter.All()) == 0 && library.tags().listAll().isEmpty();

        Shell shell = new Shell();
        PracticeContainerView practiceContainerView = new PracticeContainerView(library);
        Consumer<SessionSpec> startSession = spec -> {
            practiceContainerView.startSpec(spec);
            shell.show("practice");
        };
        Consumer<Integer> startPracticeWithPlaylist = playlistId -> {
            practiceContainerView.showSetupWithPlaylist(playlistId);
            shell.show("practice");
        };
        Consumer<Integer> editPreset = presetId -> {
            practiceContainerView.showSetupForEdit(presetId, library.presets().loadSpec(presetId));
            shell.show("practice");
        };
        HomeView homeView = new HomeView(library, startSession, editPreset);

        shell.registerView("home", "Home", () -> homeView);
        shell.registerView("gallery", "Gallery", () -> new GalleryView(library));
        shell.registerView("import", "Import", () -> new ImportView(library));
        shell.registerView("tags", "Tags", () -> new TagManagerView(library));
        shell.registerView("playlists", "Playlists", () -> new PlaylistView(library, startPracticeWithPlaylist));
        shell.registerView("practice", "Practice", () -> practiceContainerView);
        shell.registerFooterView("settings", "Settings",
                () -> new SettingsView(library, configStore, config, stage));
        shell.registerFooterAction("Help", () -> showHelp(stage));
        shell.show("home");

        purgeExpiredRecycleItems(library);

        Scene scene = new Scene(shell, config.windowWidth, config.windowHeight);
        Theme.apply(scene, config.theme);

        stage.setScene(scene);
        stage.setOnCloseRequest(e -> shutdown(stage, scene));
        stage.show();

        if (freshLibrary) {
            runFirstRunFlow(shell);
        }
    }

    /** Permanently removes recycled images past the configured auto-purge age, if any (PLAN.md 4.9), off the FX thread. */
    private void purgeExpiredRecycleItems(LibraryService library) {
        AsyncTask.runDb(() -> {
            int days = library.settings().get("recycle_auto_purge_days").map(Integer::parseInt).orElse(0);
            if (days > 0) {
                GalleryActions.purgeExpired(library, System.currentTimeMillis() - days * 24L * 60 * 60 * 1000);
            }
            return null;
        }, ignored -> {
        }, err -> {
        });
    }

    /** Help is a popup, not a sidebar page, so it stays reachable from any screen via the "Help" footer button. */
    private void showHelp(Stage stage) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Help");
        dialog.initOwner(stage);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        dialog.getDialogPane().setContent(new HelpView());
        dialog.getDialogPane().setPrefSize(720, 780);
        Theme.style(dialog.getDialogPane());
        dialog.showAndWait();
    }

    /** No library yet → point at Import (PLAN.md 4.6). */
    private void runFirstRunFlow(Shell shell) {
        Dialogs.info("Welcome to Sketcher", "This library is empty. Head to Import to add your first images.");
        shell.show("import");
    }

    private String resolveLibraryRoot(Stage stage) {
        if (config.libraryRoot != null && Files.isDirectory(Path.of(config.libraryRoot))) {
            return config.libraryRoot;
        }
        Dialogs.info("Welcome to Sketcher",
                "Choose a folder where Sketcher will keep your image library (originals, tags, and settings).");
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Choose a library folder");
        File chosen = chooser.showDialog(stage);
        if (chosen == null) {
            return null;
        }
        return chosen.getAbsolutePath();
    }

    private void shutdown(Stage stage, Scene scene) {
        config.windowWidth = scene.getWidth();
        config.windowHeight = scene.getHeight();
        configStore.save(config);
        if (library != null) library.close();
        AsyncTask.shutdown();
    }
}
