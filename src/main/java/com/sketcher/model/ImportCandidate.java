package com.sketcher.model;

import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** A row in the import staging table; mutable while the batch is reviewed. */
public class ImportCandidate {
    private final Path sourcePath;
    private final SimpleObjectProperty<ImportStatus> status = new SimpleObjectProperty<>();
    private final SimpleStringProperty reason = new SimpleStringProperty("");
    private final SimpleBooleanProperty included = new SimpleBooleanProperty(true);
    private String hash;
    private int width;
    private int height;
    private long byteSize;
    private final List<Integer> tagOverrides = new ArrayList<>();

    public ImportCandidate(Path sourcePath) {
        this.sourcePath = sourcePath;
        this.status.set(ImportStatus.NEW);
    }

    public Path sourcePath() {
        return sourcePath;
    }

    public String fileName() {
        return sourcePath.getFileName().toString();
    }

    public SimpleObjectProperty<ImportStatus> statusProperty() {
        return status;
    }

    public SimpleStringProperty reasonProperty() {
        return reason;
    }

    public SimpleBooleanProperty includedProperty() {
        return included;
    }

    public boolean isIncluded() {
        return included.get();
    }

    /** Rows can only be included if the status allows it: unreadable/duplicate rows never can. */
    public boolean isIncludable() {
        return getStatus() == ImportStatus.NEW || getStatus() == ImportStatus.ALREADY_IN_LIBRARY;
    }

    public ImportStatus getStatus() {
        return status.get();
    }

    public void setStatus(ImportStatus value, String reasonText) {
        status.set(value);
        reason.set(reasonText == null ? "" : reasonText);
        included.set(value == ImportStatus.NEW);
    }

    /** Applies a completed probe (PLAN.md 4.1 staging); call only on the FX thread. */
    public void applyProbe(com.sketcher.library.ImportService.ProbeOutcome outcome) {
        this.hash = outcome.hash();
        setDimensions(outcome.width(), outcome.height(), outcome.byteSize());
        setStatus(outcome.status(), outcome.reason());
    }

    public String getHash() {
        return hash;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public long getByteSize() {
        return byteSize;
    }

    public void setDimensions(int width, int height, long byteSize) {
        this.width = width;
        this.height = height;
        this.byteSize = byteSize;
    }

    public List<Integer> tagOverrides() {
        return tagOverrides;
    }

    public String dimensionsText() {
        return width > 0 ? width + " x " + height : "";
    }
}
