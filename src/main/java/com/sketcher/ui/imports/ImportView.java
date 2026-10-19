package com.sketcher.ui.imports;

import com.sketcher.library.ImportService;
import com.sketcher.library.LibraryService;
import com.sketcher.model.ImportCandidate;
import com.sketcher.ui.common.AsyncTask;
import com.sketcher.ui.common.Dialogs;
import com.sketcher.ui.common.Refreshable;
import com.sketcher.ui.components.AppButton;
import com.sketcher.ui.components.Card;
import com.sketcher.ui.components.FormRow;
import com.sketcher.ui.components.Page;
import com.sketcher.ui.components.SegmentedControl;
import com.sketcher.ui.components.Spacer;
import com.sketcher.ui.tags.TagPicker;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Pick files/folders, review them in the staging table, pick batch tags and
 * copy-vs-move, then commit (PLAN.md 4.1).
 */
public class ImportView extends Page implements Refreshable {
    private final LibraryService library;
    private final StagingTable stagingTable = new StagingTable();
    private final Label batchTagsLabel = new Label("None");
    private final SegmentedControl<Boolean> moveChoice = new SegmentedControl<Boolean>()
            .option(false, "Copy files")
            .option(true, "Move files");
    private Set<Integer> batchTagIds = new HashSet<>();

    public ImportView(LibraryService library) {
        super("Import");
        this.library = library;
        getStyleClass().add("import-view");
        setSubtitle("No files staged");

        CheckBox includeSubfolders = new CheckBox("Include subfolders");
        includeSubfolders.setMinWidth(USE_PREF_SIZE);
        AppButton confirmButton = AppButton.primary("Import", this::confirmImport);
        setActions(confirmButton);

        HBox pickRow = new HBox(10,
                AppButton.secondary("Add files…", this::addFiles),
                AppButton.secondary("Add folder…", () -> addFolder(includeSubfolders.isSelected())),
                includeSubfolders, new Spacer(),
                AppButton.ghost("Clear staged", () -> stagingTable.candidates().clear()));
        pickRow.setAlignment(Pos.CENTER_LEFT);

        batchTagsLabel.getStyleClass().add("muted-label");
        batchTagsLabel.setWrapText(true);
        Card optionsCard = new Card().add(
                pickRow,
                new FormRow("Tags for whole batch", batchTagsLabel,
                        AppButton.secondary("Choose tags…", this::chooseBatchTags).small()),
                new FormRow("Into the library", moveChoice)
                        .hint("Move removes the originals from their current folder after importing."));

        VBox.setVgrow(stagingTable, Priority.ALWAYS);
        stagingTable.setPlaceholder(new com.sketcher.ui.components.EmptyState("Nothing staged yet",
                "Add files or a whole folder to review them here before they're imported."));
        VBox body = new VBox(20, optionsCard, stagingTable);
        setBody(body);

        stagingTable.candidates().addListener((javafx.collections.ListChangeListener<ImportCandidate>) c -> {
            int n = stagingTable.candidates().size();
            setSubtitle(n == 0 ? "No files staged" : n + (n == 1 ? " file staged" : " files staged"));
        });
    }

    @Override
    public void onShown() {
    }

    private void addFiles() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose images to import");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                "Images", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp"));
        List<java.io.File> files = chooser.showOpenMultipleDialog(getScene().getWindow());
        if (files == null) return;
        stageFiles(files.stream().map(java.io.File::toPath));
    }

    private void addFolder(boolean includeSubfolders) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Choose a folder to import");
        java.io.File dir = chooser.showDialog(getScene().getWindow());
        if (dir == null) return;
        try (Stream<Path> stream = includeSubfolders ? Files.walk(dir.toPath()) : Files.list(dir.toPath())) {
            List<Path> files = stream.filter(Files::isRegularFile)
                    .sorted(Comparator.naturalOrder())
                    .toList();
            stageFiles(files.stream());
        } catch (IOException e) {
            Dialogs.error("Could not read folder", e.getMessage());
        }
    }

    private void stageFiles(Stream<Path> files) {
        List<ImportCandidate> newCandidates = files
                .filter(ImportService::isSupported)
                .map(ImportCandidate::new)
                .toList();
        stagingTable.candidates().addAll(newCandidates);
        for (ImportCandidate candidate : newCandidates) {
            AsyncTask.runDb(
                    () -> library.importService().probe(candidate.sourcePath(), library.images()),
                    outcome -> {
                        candidate.applyProbe(outcome);
                        library.importService().markBatchDuplicates(stagingTable.candidates());
                        stagingTable.refresh();
                    },
                    err -> candidate.setStatus(com.sketcher.model.ImportStatus.UNREADABLE, err.getMessage()));
        }
    }

    private void chooseBatchTags() {
        TagPicker.pickMany(library).ifPresent(ids -> {
            batchTagIds = ids;
            batchTagsLabel.setText(ids.isEmpty() ? "None"
                    : ids.stream().map(library.tags()::fullPath).collect(Collectors.joining(", ")));
        });
    }

    private void confirmImport() {
        List<ImportCandidate> snapshot = List.copyOf(stagingTable.candidates());
        if (snapshot.isEmpty()) {
            Dialogs.error("Nothing to import", "Add some files or a folder first.");
            return;
        }
        boolean move = moveChoice.getValue();

        ProgressBar progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(380);
        Label progressLabel = new Label("Starting…");
        Alert progressAlert = new Alert(Alert.AlertType.NONE);
        progressAlert.setTitle("Importing");
        progressAlert.getDialogPane().setContent(new VBox(12, progressLabel, progressBar));
        progressAlert.getButtonTypes().add(ButtonType.CANCEL);
        com.sketcher.ui.common.Theme.style(progressAlert.getDialogPane());
        AtomicBoolean cancelFlag = new AtomicBoolean(false);
        progressAlert.setOnCloseRequest(e -> cancelFlag.set(true));
        progressAlert.getDialogPane().lookupButton(ButtonType.CANCEL).addEventFilter(
                javafx.event.ActionEvent.ACTION, e -> cancelFlag.set(true));
        progressAlert.show();

        AsyncTask.runDb(
                () -> library.importService().importBatch(snapshot, batchTagIds, move, library.images(),
                        cancelFlag, i -> Platform.runLater(() -> {
                            progressBar.setProgress((i + 1.0) / snapshot.size());
                            progressLabel.setText("Processing " + (i + 1) + " / " + snapshot.size());
                        })),
                result -> {
                    progressAlert.setResult(ButtonType.CLOSE);
                    progressAlert.close();
                    if (result.cancelled()) {
                        Dialogs.info("Import cancelled", "No changes were made.");
                        return;
                    }
                    stagingTable.candidates().removeIf(ImportCandidate::isIncluded);
                    Dialogs.info("Import complete", result.imported() + " imported, "
                            + result.taggedExisting() + " already-present tagged, "
                            + result.skipped() + " skipped, " + result.failed() + " failed.");
                },
                err -> {
                    progressAlert.close();
                    Dialogs.error("Import failed", err.getMessage());
                });
    }
}
