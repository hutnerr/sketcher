package com.sketcher.ui.imports;

import com.sketcher.config.AppConfig;
import com.sketcher.config.ConfigStore;
import com.sketcher.library.ImportService;
import com.sketcher.library.LibraryService;
import com.sketcher.model.ImportCandidate;
import com.sketcher.model.ImportStatus;
import com.sketcher.ui.common.AsyncTask;
import com.sketcher.ui.common.Dialogs;
import com.sketcher.ui.common.Refreshable;
import com.sketcher.ui.components.AppButton;
import com.sketcher.ui.components.Card;
import com.sketcher.ui.components.FormRow;
import com.sketcher.ui.components.Page;
import com.sketcher.ui.components.SegmentedControl;
import com.sketcher.ui.components.Spacer;
import com.sketcher.ui.components.Toolbar;
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
    private final ConfigStore configStore;
    private final AppConfig config;
    private final StagingTable stagingTable = new StagingTable();
    private final Label batchTagsLabel = new Label("None");
    private final SegmentedControl<Boolean> moveChoice = new SegmentedControl<Boolean>()
            .option(false, "Copy files")
            .option(true, "Move files");
    private Set<Integer> batchTagIds = new HashSet<>();
    private final Label selectionLabel = new Label();
    private final AppButton checkSelected = AppButton.ghost("Check", () -> stagingTable.setIncluded(selectedRows(), true)).small();
    private final AppButton uncheckSelected = AppButton.ghost("Uncheck", () -> stagingTable.setIncluded(selectedRows(), false)).small();
    private final AppButton importSelected = AppButton.secondary("Import selected", this::importSelected).small();
    private final Toolbar selectionBar;
    private final CheckBox skipDuplicates = new CheckBox("Skip duplicates");
    private int skippedDuplicates = 0;

    public ImportView(LibraryService library, ConfigStore configStore, AppConfig config) {
        super("Import");
        this.library = library;
        this.configStore = configStore;
        this.config = config;
        getStyleClass().add("import-view");
        setSubtitle("No files staged");

        CheckBox includeSubfolders = new CheckBox("Include subfolders");
        includeSubfolders.setMinWidth(USE_PREF_SIZE);
        skipDuplicates.setMinWidth(USE_PREF_SIZE);
        skipDuplicates.setTooltip(new javafx.scene.control.Tooltip(
                "Leave out files already in the library or repeated within this batch."));
        skipDuplicates.setSelected(config.skipDuplicatesOnImport);
        skipDuplicates.selectedProperty().addListener((obs, was, on) -> {
            if (config.skipDuplicatesOnImport != on) {
                config.skipDuplicatesOnImport = on;
                configStore.save(config);
            }
            if (on) dropDuplicates();
        });
        AppButton confirmButton = AppButton.primary("Import checked", () -> confirmImport(
                stagingTable.candidates().stream().filter(ImportCandidate::isIncluded).toList()));
        setActions(confirmButton);

        HBox pickRow = new HBox(10,
                AppButton.secondary("Add files…", this::addFiles),
                AppButton.secondary("Add folder…", () -> addFolder(includeSubfolders.isSelected())),
                includeSubfolders, skipDuplicates, new Spacer(),
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
        selectionLabel.getStyleClass().add("muted-label");
        selectionBar = new Toolbar().plain()
                .left(selectionLabel)
                .right(checkSelected, uncheckSelected, Toolbar.divider(), importSelected);
        VBox body = new VBox(12, optionsCard, selectionBar, stagingTable);
        VBox.setMargin(selectionBar, new javafx.geometry.Insets(8, 0, 0, 0));
        setBody(body);

        stagingTable.candidates().addListener((javafx.collections.ListChangeListener<ImportCandidate>) c -> {
            if (stagingTable.candidates().isEmpty()) skippedDuplicates = 0;
            updateSubtitle();
            updateSelectionBar();
        });
        stagingTable.getSelectionModel().getSelectedItems().addListener(
                (javafx.collections.ListChangeListener<ImportCandidate>) c -> updateSelectionBar());
        updateSelectionBar();
    }

    private List<ImportCandidate> selectedRows() {
        return List.copyOf(stagingTable.getSelectionModel().getSelectedItems());
    }

    /**
     * The bar is how a big staged folder gets imported a slice at a time:
     * select a run of rows (Shift/Ctrl-click), then check them, or import
     * just those. It stays visible while anything is staged so the hint is
     * there before you've discovered multi-select.
     */
    private void updateSelectionBar() {
        boolean staged = !stagingTable.candidates().isEmpty();
        selectionBar.setVisible(staged);
        selectionBar.setManaged(staged);
        int selected = stagingTable.getSelectionModel().getSelectedItems().size();
        long checked = stagingTable.candidates().stream().filter(ImportCandidate::isIncluded).count();
        selectionLabel.setText(selected == 0
                ? checked + " checked · Shift- or Ctrl-click rows to select several, Space checks them"
                : selected + " selected · " + checked + " checked");
        checkSelected.setDisable(selected == 0);
        uncheckSelected.setDisable(selected == 0);
        importSelected.setDisable(selected == 0);
    }

    private void updateSubtitle() {
        int n = stagingTable.candidates().size();
        String text = n == 0 ? "No files staged" : n + (n == 1 ? " file staged" : " files staged");
        if (skippedDuplicates > 0) text += " · " + skippedDuplicates + " duplicate" + (skippedDuplicates == 1 ? "" : "s") + " skipped";
        setSubtitle(text);
    }

    private static boolean isDuplicate(ImportCandidate c) {
        return c.getStatus() == ImportStatus.ALREADY_IN_LIBRARY || c.getStatus() == ImportStatus.DUPLICATE_IN_BATCH;
    }

    /** Duplicates are only known once a probe has hashed the file, so they're dropped as probes complete. */
    private void dropDuplicates() {
        int before = stagingTable.candidates().size();
        stagingTable.candidates().removeIf(ImportView::isDuplicate);
        skippedDuplicates += before - stagingTable.candidates().size();
        updateSubtitle();
    }

    @Override
    public void onShown() {
        // The same preference can be changed from Settings while this page is cached.
        skipDuplicates.setSelected(config.skipDuplicatesOnImport);
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
                        if (skipDuplicates.isSelected()) dropDuplicates();
                        stagingTable.refresh();
                    },
                    err -> candidate.setStatus(com.sketcher.model.ImportStatus.UNREADABLE, err.getMessage()));
        }
    }

    private void chooseBatchTags() {
        TagPicker.pickMany(library, batchTagIds).ifPresent(ids -> {
            batchTagIds = ids;
            batchTagsLabel.setText(ids.isEmpty() ? "None"
                    : ids.stream().map(library.tags()::fullPath).collect(Collectors.joining(", ")));
        });
    }

    /** Imports the selected rows whether or not they were checked — checking them first so the table agrees. */
    private void importSelected() {
        List<ImportCandidate> rows = selectedRows().stream().filter(ImportCandidate::isIncludable).toList();
        stagingTable.setIncluded(rows, true);
        confirmImport(rows);
    }

    /**
     * Imports exactly these rows. Afterwards they leave the staging table
     * unless they failed — failures stay checked, with the reason in their
     * status, so the next import retries them. Everything else staged is
     * untouched for a later pass.
     */
    private void confirmImport(List<ImportCandidate> rows) {
        List<ImportCandidate> snapshot = List.copyOf(rows);
        if (snapshot.isEmpty()) {
            Dialogs.error("Nothing to import", stagingTable.candidates().isEmpty()
                    ? "Add some files or a folder first."
                    : "Check the files to import, or select some rows and use Import selected.");
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
                    result.failures().forEach(ImportCandidate::markFailed);
                    Set<ImportCandidate> done = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
                    for (ImportCandidate c : snapshot) {
                        if (!result.failures().containsKey(c)) done.add(c);
                    }
                    stagingTable.candidates().removeIf(done::contains);
                    int remaining = stagingTable.candidates().size();
                    String message = result.imported() + " imported, "
                            + result.taggedExisting() + " already-present tagged, "
                            + result.skipped() + " skipped, " + result.failed() + " failed.";
                    if (result.failed() > 0) message += "\n\nFailed files are still staged with the reason in their status — import again to retry.";
                    if (remaining > 0) message += "\n\n" + remaining + (remaining == 1 ? " file is" : " files are") + " still staged.";
                    Dialogs.info("Import complete", message);
                },
                err -> {
                    progressAlert.close();
                    Dialogs.error("Import failed", err.getMessage());
                });
    }
}
