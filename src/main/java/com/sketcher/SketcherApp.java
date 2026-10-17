package com.sketcher;

import com.sketcher.config.AppConfig;
import com.sketcher.config.ConfigStore;
import com.sketcher.filter.Filter;
import com.sketcher.library.LibraryService;
import com.sketcher.library.StarterTagSets;
import com.sketcher.ui.Shell;
import com.sketcher.ui.common.AsyncTask;
import com.sketcher.ui.common.Dialogs;
import com.sketcher.ui.common.Theme;
import com.sketcher.ui.gallery.GalleryActions;
import com.sketcher.ui.gallery.GalleryView;
import com.sketcher.ui.home.HomeView;
import com.sketcher.ui.imports.ImportView;
import com.sketcher.ui.practice.PracticeContainerView;
import com.sketcher.ui.settings.HelpView;
import com.sketcher.ui.settings.RecycleBinView;
import com.sketcher.ui.settings.SettingsView;
import com.sketcher.ui.tags.TagManagerView;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.ChoiceDialog;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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
        HomeView homeView = new HomeView(library,
                spec -> {
                    practiceContainerView.startSpec(spec);
                    shell.show("practice");
                },
                shell::show);

        shell.registerView("home", "Home", () -> homeView);
        shell.registerView("gallery", "Gallery", () -> new GalleryView(library));
        shell.registerView("import", "Import", () -> new ImportView(library));
        shell.registerView("tags", "Tags", () -> new TagManagerView(library));
        shell.registerView("practice", "Practice", () -> practiceContainerView);
        shell.registerView("settings", "Settings",
                () -> new SettingsView(library, configStore, config, stage, shell::show));
        shell.registerHiddenView("recyclebin", () -> new RecycleBinView(library));
        shell.registerView("help", "Help", () -> new HelpView());
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

    /** No library yet → offer a starter tag set, then point at Import (PLAN.md 4.6). */
    private void runFirstRunFlow(Shell shell) {
        if (Dialogs.confirm("Welcome to Sketcher",
                "This library is empty. Import a starter tag set (figure study, animals, or environments) "
                        + "to get going? You can always do this later from Tags.")) {
            List<String> keys = List.copyOf(StarterTagSets.AVAILABLE.keySet());
            ChoiceDialog<String> dialog = new ChoiceDialog<>(keys.get(0), keys);
            dialog.setTitle("Import starter tag set");
            dialog.setHeaderText(null);
            dialog.setContentText("Choose a tag set:");
            dialog.showAndWait().ifPresent(key -> StarterTagSets.apply(key, library.tags()));
        }
        Dialogs.info("Next step", "Head to Import to add your first images.");
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
