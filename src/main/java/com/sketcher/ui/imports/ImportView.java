package com.sketcher.ui.imports;

import com.sketcher.library.ImportService;
import com.sketcher.library.LibraryService;
import com.sketcher.model.ImportCandidate;
import com.sketcher.ui.common.AsyncTask;
import com.sketcher.ui.common.Dialogs;
import com.sketcher.ui.common.Refreshable;
import com.sketcher.ui.tags.TagPicker;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
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
public class ImportView extends BorderPane implements Refreshable {
    private final LibraryService library;
    private final StagingTable stagingTable = new StagingTable();
    private final Label batchTagsLabel = new Label("(none)");
    private final Label summaryLabel = new Label("0 file(s) staged");
    private final RadioButton copyRadio = new RadioButton("Copy");
    private final RadioButton moveRadio = new RadioButton("Move");
    private Set<Integer> batchTagIds = new HashSet<>();

    public ImportView(LibraryService library) {
        this.library = library;
        getStyleClass().add("import-view");
        setPadding(new Insets(16));

        CheckBox includeSubfolders = new CheckBox("Include subfolders");

        Button addFilesButton = new Button("Add files…");
        addFilesButton.setOnAction(e -> addFiles());
        Button addFolderButton = new Button("Add folder…");
        addFolderButton.setOnAction(e -> addFolder(includeSubfolders.isSelected()));
        Button clearButton = new Button("Clear staged");
        clearButton.setOnAction(e -> stagingTable.candidates().clear());

        HBox pickRow = new HBox(10, addFilesButton, addFolderButton, includeSubfolders, clearButton);
        pickRow.setAlignment(Pos.CENTER_LEFT);

        ToggleGroup copyMoveGroup = new ToggleGroup();
        copyRadio.setToggleGroup(copyMoveGroup);
        moveRadio.setToggleGroup(copyMoveGroup);
        copyRadio.setSelected(true);

        Button batchTagsButton = new Button("Batch tags…");
        batchTagsButton.setOnAction(e -> chooseBatchTags());

        Button confirmButton = new Button("Import");
        confirmButton.getStyleClass().add("primary-button");
        confirmButton.setOnAction(e -> confirmImport());

        HBox optionsRow = new HBox(14, new Label("Tags for whole batch:"), batchTagsLabel, batchTagsButton,
                new Label("  "), copyRadio, moveRadio, new Label("  "), confirmButton);
        optionsRow.setAlignment(Pos.CENTER_LEFT);

        VBox top = new VBox(10, pickRow, optionsRow, summaryLabel);
        top.setPadding(new Insets(0, 0, 14, 0));
        setTop(top);
        setCenter(stagingTable);

        stagingTable.candidates().addListener((javafx.collections.ListChangeListener<ImportCandidate>) c ->
                summaryLabel.setText(stagingTable.candidates().size() + " file(s) staged"));
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
            batchTagsLabel.setText(ids.isEmpty() ? "(none)"
                    : ids.stream().map(library.tags()::fullPath).collect(Collectors.joining(", ")));
        });
    }

    private void confirmImport() {
        List<ImportCandidate> snapshot = List.copyOf(stagingTable.candidates());
        if (snapshot.isEmpty()) {
            Dialogs.error("Nothing to import", "Add some files or a folder first.");
            return;
        }
        boolean move = moveRadio.isSelected();

        ProgressBar progressBar = new ProgressBar(0);
        progressBar.setPrefWidth(300);
        Label progressLabel = new Label("Starting…");
        Alert progressAlert = new Alert(Alert.AlertType.NONE);
        progressAlert.setTitle("Importing");
        progressAlert.getDialogPane().setContent(new VBox(8, progressLabel, progressBar));
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
