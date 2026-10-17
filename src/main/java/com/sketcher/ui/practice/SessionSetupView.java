package com.sketcher.ui.practice;

import com.sketcher.filter.Filter;
import com.sketcher.filter.FilterSummary;
import com.sketcher.library.LibraryService;
import com.sketcher.model.OrderMode;
import com.sketcher.model.Playlist;
import com.sketcher.model.Segment;
import com.sketcher.model.SessionSpec;
import com.sketcher.model.SourceKind;
import com.sketcher.model.TickBehavior;
import com.sketcher.ui.common.AsyncTask;
import com.sketcher.ui.common.Dialogs;
import com.sketcher.ui.gallery.FilterBar;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Builds a SessionSpec and hands it off to start a session (PLAN.md 4.4,
 * 4.5). A single configured segment is the fast path; "+ Add this segment
 * to queue" turns it into a multi-segment queue (PLAN.md Phase 6, the
 * "5 female, 5 male, 5 dog, ramping timer" example) by appending the current
 * form to a list and letting the form be reconfigured for the next segment.
 */
public class SessionSetupView extends VBox {
    private final LibraryService library;
    private final FilterBar filterBar;
    private final List<Segment> queuedSegments = new ArrayList<>();
    private final VBox queueListBox = new VBox(4);
    private final Label queueHint = new Label(
            "Starting/saving now uses this queue, not the form above — add the current segment to it first if it should be included too.");
    private List<Playlist> playlistsCache = List.of();

    private final RadioButton sourceFilterRadio = new RadioButton("Filter");
    private final RadioButton sourcePlaylistRadio = new RadioButton("Playlist");
    private final VBox filterSection;
    private final HBox playlistSection;
    private final ChoiceBox<String> playlistChoice = new ChoiceBox<>();

    private final ChoiceBox<String> countChoice = new ChoiceBox<>();
    private final ChoiceBox<String> intervalChoice = new ChoiceBox<>();
    private final HBox intervalRow;
    private final RadioButton randomOrder = new RadioButton("Random");
    private final RadioButton sequentialOrder = new RadioButton("Sequential");
    private final RadioButton tickAdvance = new RadioButton("Advance immediately");
    private final RadioButton tickBreak = new RadioButton("Break for");
    private final RadioButton tickWait = new RadioButton("Wait for click");
    private final TextField breakSecondsField = new TextField("10");
    private final HBox tickRow;

    private final CheckBox rampBox = new CheckBox("Ramp timer to");
    private final TextField rampToField = new TextField("10");
    private final HBox rampRow;

    private final CheckBox memoryModeBox = new CheckBox("Memory drawing mode");
    private final TextField memoryShowField = new TextField("30");
    private final TextField memoryHideField = new TextField("10");
    private final VBox memoryFieldsRow;

    private Integer pendingPreselectPlaylistId;
    private final Integer editingPresetId;

    private static final List<String> COUNT_OPTIONS = List.of("Unlimited", "5", "10", "20", "50", "Custom…");
    private static final List<String> INTERVAL_OPTIONS = List.of(
            "Untimed", "10s", "30s", "60s", "2m", "3m", "5m", "10m", "Custom…");

    public SessionSetupView(LibraryService library, Consumer<SessionSpec> onStart) {
        this(library, onStart, null, null, null);
    }

    /** Pre-selects Playlist as the source and that playlist in the dropdown (e.g. "Start practice" from the Playlists page). */
    public SessionSetupView(LibraryService library, Consumer<SessionSpec> onStart, Integer preselectPlaylistId) {
        this(library, onStart, preselectPlaylistId, null, null);
    }

    /** Pre-fills the whole form from an existing preset and swaps "Save as preset…" for "Update preset" (Home page's "Edit" action). */
    public SessionSetupView(LibraryService library, Consumer<SessionSpec> onStart, SessionSpec editingSpec, int editingPresetId) {
        this(library, onStart, null, editingSpec, editingPresetId);
    }

    private SessionSetupView(LibraryService library, Consumer<SessionSpec> onStart, Integer preselectPlaylistId,
                              SessionSpec editingSpec, Integer editingPresetId) {
        this.library = library;
        this.pendingPreselectPlaylistId = preselectPlaylistId;
        this.editingPresetId = editingPresetId;
        getStyleClass().add("session-setup-view");
        setSpacing(22);
        setPadding(new Insets(24));

        Label heading = new Label(editingPresetId != null ? "Edit preset" : "Practice");
        heading.getStyleClass().add("home-title");

        filterBar = new FilterBar(library, false);

        ToggleGroup sourceGroup = new ToggleGroup();
        sourceFilterRadio.setToggleGroup(sourceGroup);
        sourcePlaylistRadio.setToggleGroup(sourceGroup);
        sourceFilterRadio.setSelected(preselectPlaylistId == null);
        sourcePlaylistRadio.setSelected(preselectPlaylistId != null);
        sourceFilterRadio.setOnAction(e -> updateSourceVisibility());
        sourcePlaylistRadio.setOnAction(e -> updateSourceVisibility());
        Button refreshPlaylistsButton = new Button("Refresh");
        refreshPlaylistsButton.setOnAction(e -> loadPlaylists());

        filterSection = new VBox(6, filterBar);
        playlistSection = new HBox(10, new Label("Playlist:"), playlistChoice, refreshPlaylistsButton);
        playlistSection.setAlignment(Pos.CENTER_LEFT);

        countChoice.getItems().addAll(COUNT_OPTIONS);
        countChoice.getSelectionModel().select("Unlimited");
        intervalChoice.getItems().addAll(INTERVAL_OPTIONS);
        intervalChoice.getSelectionModel().select("Untimed");
        intervalChoice.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> updateTimingAvailability());

        ToggleGroup orderGroup = new ToggleGroup();
        randomOrder.setToggleGroup(orderGroup);
        sequentialOrder.setToggleGroup(orderGroup);
        randomOrder.setSelected(true);

        ToggleGroup tickGroup = new ToggleGroup();
        tickAdvance.setToggleGroup(tickGroup);
        tickBreak.setToggleGroup(tickGroup);
        tickWait.setToggleGroup(tickGroup);
        tickAdvance.setSelected(true);
        breakSecondsField.setPrefWidth(50);

        rampToField.setPrefWidth(50);
        rampBox.setOnAction(e -> updateTimingAvailability());

        memoryShowField.setPrefWidth(50);
        memoryHideField.setPrefWidth(50);
        memoryModeBox.setOnAction(e -> updateTimingAvailability());

        HBox sourceRow = new HBox(14, sourceFilterRadio, sourcePlaylistRadio);
        sourceRow.setAlignment(Pos.CENTER_LEFT);
        HBox countRow = new HBox(10, new Label("Image count:"), countChoice);
        countRow.setAlignment(Pos.CENTER_LEFT);
        intervalRow = new HBox(10, new Label("Interval:"), intervalChoice);
        intervalRow.setAlignment(Pos.CENTER_LEFT);
        HBox orderRow = new HBox(14, new Label("Order:"), randomOrder, sequentialOrder);
        orderRow.setAlignment(Pos.CENTER_LEFT);
        tickRow = new HBox(10, new Label("When timer ends:"), tickAdvance, tickBreak, breakSecondsField,
                new Label("sec"), tickWait);
        tickRow.setAlignment(Pos.CENTER_LEFT);
        rampRow = new HBox(10, rampBox, rampToField, new Label("sec/image (from the interval above)"));
        rampRow.setAlignment(Pos.CENTER_LEFT);
        HBox memoryTimesRow = new HBox(8, new Label("Show for"), memoryShowField, new Label("sec, then hide for"),
                memoryHideField, new Label("sec"));
        memoryTimesRow.setAlignment(Pos.CENTER_LEFT);
        Label memoryHint = new Label(
                "While hidden, draw from memory — it reveals again when you click, press Space, or →.");
        memoryHint.getStyleClass().add("hint-label");
        memoryHint.setWrapText(true);
        memoryFieldsRow = new VBox(6, memoryTimesRow, memoryHint);
        VBox memoryRow = new VBox(8, memoryModeBox, memoryFieldsRow);

        Button addSegmentButton = new Button("+ Add this segment to queue");
        addSegmentButton.setOnAction(e -> addSegmentToQueue());
        queueListBox.setVisible(false);
        queueListBox.setManaged(false);
        queueHint.getStyleClass().add("hint-label");
        queueHint.setWrapText(true);
        queueHint.setVisible(false);
        queueHint.setManaged(false);

        Button startButton = new Button("Start practice");
        startButton.getStyleClass().add("primary-button");
        startButton.setOnAction(e -> start(onStart));
        Button secondaryButton;
        if (editingPresetId != null) {
            secondaryButton = new Button("Update preset");
            secondaryButton.setOnAction(e -> updatePreset(editingPresetId));
        } else {
            secondaryButton = new Button("Save as preset…");
            secondaryButton.setOnAction(e -> saveAsPreset());
        }

        VBox sourceSection = new VBox(10, sourceRow, filterSection, playlistSection);
        VBox imagesSection = new VBox(10, countRow, orderRow);
        VBox timingSection = new VBox(12, intervalRow, tickRow, rampRow, memoryRow);
        VBox queueSection = new VBox(10, addSegmentButton, queueHint, queueListBox);
        HBox actionRow = new HBox(10, startButton, secondaryButton);
        actionRow.setAlignment(Pos.CENTER_LEFT);

        getChildren().addAll(heading,
                section("Source", sourceSection),
                section("Images", imagesSection),
                section("Timing", timingSection),
                section("Multi-segment queue (optional)", queueSection),
                actionRow);

        if (editingSpec != null && !editingSpec.segments().isEmpty()) {
            applyEditingSpec(editingSpec);
        }

        updateSourceVisibility();
        updateTimingAvailability();
        loadPlaylists();
    }

    /**
     * Pre-fills the form from an existing preset: every segment but the last
     * goes straight into the queue (same place it'd land if you were
     * building it fresh one segment at a time), and the last segment's
     * values populate the live form so it's still the one being edited.
     */
    private void applyEditingSpec(SessionSpec editingSpec) {
        List<Segment> segments = editingSpec.segments();
        queuedSegments.addAll(segments.subList(0, segments.size() - 1));
        renderQueueList();

        Segment last = segments.get(segments.size() - 1);
        if (last.sourceKind() == SourceKind.PLAYLIST) {
            sourcePlaylistRadio.setSelected(true);
            pendingPreselectPlaylistId = last.playlistId();
        } else {
            sourceFilterRadio.setSelected(true);
            filterBar.loadFilter(last.filter());
        }

        selectChoice(countChoice, last.itemCount() == null ? "Unlimited" : String.valueOf(last.itemCount()));
        if (last.orderMode() == OrderMode.SEQUENTIAL) sequentialOrder.setSelected(true);
        else randomOrder.setSelected(true);

        if (last.isMemoryMode()) {
            memoryModeBox.setSelected(true);
            memoryShowField.setText(String.valueOf(last.memoryShowSeconds()));
            memoryHideField.setText(String.valueOf(last.memoryHideSeconds()));
        } else {
            selectChoice(intervalChoice, intervalLabelFor(last.secondsPerImage()));
            switch (last.tickBehavior()) {
                case ADVANCE_IMMEDIATELY -> tickAdvance.setSelected(true);
                case BREAK -> tickBreak.setSelected(true);
                case WAIT_FOR_CLICK -> tickWait.setSelected(true);
            }
            breakSecondsField.setText(String.valueOf(last.breakSeconds()));
            if (last.isRamped()) {
                rampBox.setSelected(true);
                rampToField.setText(String.valueOf(last.rampToSeconds()));
            }
        }
    }

    private void selectChoice(ChoiceBox<String> box, String value) {
        box.getSelectionModel().select(box.getItems().contains(value) ? value : "Custom…");
    }

    private String intervalLabelFor(int seconds) {
        return switch (seconds) {
            case 0 -> "Untimed";
            case 10 -> "10s";
            case 30 -> "30s";
            case 60 -> "60s";
            case 120 -> "2m";
            case 180 -> "3m";
            case 300 -> "5m";
            case 600 -> "10m";
            default -> "Custom…";
        };
    }

    /**
     * Either the queue, or the live form — never both. Once you've queued at
     * least one segment, the queue IS the session being built (you used this
     * same form to build each one); silently tacking the form's current,
     * maybe-half-reconfigured state on as one more segment was the confusing
     * part. With nothing queued, the form itself is the single-segment fast
     * path, unchanged from before.
     */
    private List<Segment> resolveSegments() {
        if (!queuedSegments.isEmpty()) {
            return new ArrayList<>(queuedSegments);
        }
        Segment current = buildSegment();
        return current == null ? List.of() : List.of(current);
    }

    /** Replaces this preset's segments in place instead of creating a new one (Home page's "Edit" action). */
    private void updatePreset(int presetId) {
        List<Segment> segments = resolveSegments();
        if (segments.isEmpty()) {
            Dialogs.error("Nothing to save", "Fill in this segment's fields, or add at least one to the queue.");
            return;
        }
        AsyncTask.runDb(() -> {
            library.presets().update(presetId, new SessionSpec(segments, presetId));
            return null;
        }, ignored -> Dialogs.info("Preset updated", "Your changes have been saved."),
                err -> Dialogs.error("Could not update preset", err.getMessage()));
    }

    private VBox section(String title, Node content) {
        Label label = new Label(title);
        label.getStyleClass().add("help-heading");
        VBox box = new VBox(12, label, content);
        box.getStyleClass().add("form-section");
        return box;
    }

    private void updateSourceVisibility() {
        boolean playlist = sourcePlaylistRadio.isSelected();
        filterSection.setVisible(!playlist);
        filterSection.setManaged(!playlist);
        playlistSection.setVisible(playlist);
        playlistSection.setManaged(playlist);
    }

    /**
     * Hides (not just disables) whichever timing rows don't apply right now,
     * instead of leaving dead, disabled controls sitting on screen — "When
     * timer ends" and "Ramp timer" are meaningless while Untimed is picked
     * (the default), and the whole interval/tick/ramp cluster is meaningless
     * once Memory drawing mode takes over timing.
     */
    private void updateTimingAvailability() {
        boolean memoryMode = memoryModeBox.isSelected();
        boolean timed = !"Untimed".equals(intervalChoice.getValue());

        memoryFieldsRow.setVisible(memoryMode);
        memoryFieldsRow.setManaged(memoryMode);

        intervalRow.setVisible(!memoryMode);
        intervalRow.setManaged(!memoryMode);

        boolean showTickRow = !memoryMode && timed;
        tickRow.setVisible(showTickRow);
        tickRow.setManaged(showTickRow);

        boolean showRampRow = !memoryMode && timed;
        rampRow.setVisible(showRampRow);
        rampRow.setManaged(showRampRow);

        boolean ramping = rampBox.isSelected() && showRampRow;
        rampToField.setVisible(ramping);
        rampToField.setManaged(ramping);
    }

    private void loadPlaylists() {
        AsyncTask.runDb(
                () -> library.playlists().listAll(),
                playlists -> {
                    playlistsCache = playlists;
                    String previous = playlistChoice.getValue();
                    playlistChoice.getItems().setAll(playlists.stream().map(Playlist::name).toList());
                    if (pendingPreselectPlaylistId != null) {
                        int wanted = pendingPreselectPlaylistId;
                        pendingPreselectPlaylistId = null;
                        playlists.stream().filter(p -> p.id() == wanted).findFirst()
                                .ifPresent(p -> playlistChoice.getSelectionModel().select(p.name()));
                    } else if (previous != null && playlistChoice.getItems().contains(previous)) {
                        playlistChoice.getSelectionModel().select(previous);
                    } else if (!playlistChoice.getItems().isEmpty()) {
                        playlistChoice.getSelectionModel().select(0);
                    }
                },
                err -> Dialogs.error("Could not load playlists", err.getMessage()));
    }

    private Integer selectedPlaylistId() {
        String name = playlistChoice.getValue();
        if (name == null) return null;
        return playlistsCache.stream().filter(p -> p.name().equals(name)).map(Playlist::id).findFirst().orElse(null);
    }

    /** Builds the segment from the current form state, or null if a required field is incomplete. */
    private Segment buildSegment() {
        Integer itemCount = parseCount(countChoice.getValue());
        if (itemCount == null && countChoice.getValue().equals("Custom…")) return null;
        OrderMode order = randomOrder.isSelected() ? OrderMode.RANDOM : OrderMode.SEQUENTIAL;

        boolean memoryMode = memoryModeBox.isSelected();
        int seconds;
        TickBehavior tickBehavior;
        int breakSeconds;
        Integer rampTo = null;

        if (memoryMode) {
            seconds = 0;
            tickBehavior = TickBehavior.ADVANCE_IMMEDIATELY;
            breakSeconds = 0;
        } else {
            seconds = parseInterval(intervalChoice.getValue());
            if (seconds < 0) return null;
            tickBehavior = tickAdvance.isSelected() ? TickBehavior.ADVANCE_IMMEDIATELY
                    : tickWait.isSelected() ? TickBehavior.WAIT_FOR_CLICK : TickBehavior.BREAK;
            breakSeconds = parseIntOr(breakSecondsField.getText(), 10);
            if (rampBox.isSelected() && seconds > 0) {
                rampTo = parseIntOrNull(rampToField.getText());
                if (rampTo == null || rampTo < 0) return null;
            }
        }

        Segment segment;
        if (sourcePlaylistRadio.isSelected()) {
            Integer playlistId = selectedPlaylistId();
            if (playlistId == null) return null;
            segment = Segment.playlistSegment(playlistId, itemCount, order, seconds, tickBehavior, breakSeconds);
        } else {
            Filter filter = filterBar.buildFilter();
            segment = Segment.filterSegment(filter, itemCount, order, seconds, tickBehavior, breakSeconds);
        }
        if (rampTo != null) segment = segment.withRampToSeconds(rampTo);
        if (memoryMode) {
            Integer showSeconds = parseIntOrNull(memoryShowField.getText());
            Integer hideSeconds = parseIntOrNull(memoryHideField.getText());
            if (showSeconds == null || hideSeconds == null || showSeconds <= 0 || hideSeconds <= 0) return null;
            segment = segment.withMemoryMode(showSeconds, hideSeconds);
        }
        return segment;
    }

    private void addSegmentToQueue() {
        Segment segment = buildSegment();
        if (segment == null) {
            Dialogs.error("Incomplete segment", "Fill in this segment's fields before adding it to the queue.");
            return;
        }
        queuedSegments.add(segment);
        renderQueueList();
    }

    private void renderQueueList() {
        queueListBox.getChildren().clear();
        for (int i = 0; i < queuedSegments.size(); i++) {
            queueListBox.getChildren().add(buildQueueRow(i));
        }
        boolean hasItems = !queuedSegments.isEmpty();
        queueListBox.setVisible(hasItems);
        queueListBox.setManaged(hasItems);
        queueHint.setVisible(hasItems);
        queueHint.setManaged(hasItems);
    }

    private HBox buildQueueRow(int index) {
        Segment segment = queuedSegments.get(index);
        Label summary = new Label((index + 1) + ". " + summarizeSegment(segment));
        Button upButton = new Button("↑");
        upButton.setDisable(index == 0);
        upButton.setOnAction(e -> {
            queuedSegments.add(index - 1, queuedSegments.remove(index));
            renderQueueList();
        });
        Button downButton = new Button("↓");
        downButton.setDisable(index == queuedSegments.size() - 1);
        downButton.setOnAction(e -> {
            queuedSegments.add(index + 1, queuedSegments.remove(index));
            renderQueueList();
        });
        Button removeButton = new Button("x");
        removeButton.setOnAction(e -> {
            queuedSegments.remove(index);
            renderQueueList();
        });
        HBox row = new HBox(8, summary, upButton, downButton, removeButton);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private String summarizeSegment(Segment segment) {
        String source = segment.sourceKind() == SourceKind.FILTER
                ? FilterSummary.describe(segment.filter(), id -> library.tags().get(id).map(t -> t.name()).orElse("?"))
                : playlistName(segment.playlistId());
        String count = segment.itemCount() == null ? "unlimited" : segment.itemCount() + " images";
        String timing;
        if (segment.isMemoryMode()) {
            timing = "memory " + segment.memoryShowSeconds() + "s/" + segment.memoryHideSeconds() + "s";
        } else if (segment.isRamped()) {
            timing = segment.secondsPerImage() + "s→" + segment.rampToSeconds() + "s/image";
        } else {
            timing = segment.isTimed() ? segment.secondsPerImage() + "s/image" : "untimed";
        }
        return source + " · " + count + " · " + timing;
    }

    private String playlistName(Integer playlistId) {
        if (playlistId == null) return "playlist";
        return playlistsCache.stream().filter(p -> p.id() == playlistId).map(Playlist::name).findFirst()
                .orElse("playlist #" + playlistId);
    }

    private void start(Consumer<SessionSpec> onStart) {
        List<Segment> segments = resolveSegments();
        if (segments.isEmpty()) {
            Dialogs.error("Nothing to start", "Fill in this segment's fields, or add at least one to the queue.");
            return;
        }

        AsyncTask.runDb(
                () -> countMatches(segments),
                total -> {
                    if (total == 0) {
                        Dialogs.error("No matching images", "None of these segments match any images in your library.");
                        return;
                    }
                    onStart.accept(new SessionSpec(segments, null));
                },
                err -> Dialogs.error("Could not start session", err.getMessage()));
    }

    private int countMatches(List<Segment> segments) {
        int total = 0;
        for (Segment seg : segments) {
            total += seg.sourceKind() == SourceKind.FILTER
                    ? library.images().count(seg.filter())
                    : library.playlists().listImageIds(seg.playlistId(), false).size();
        }
        return total;
    }

    /** "Create from the setup screen" (PLAN.md 4.5) — saves without starting a session. */
    private void saveAsPreset() {
        List<Segment> segments = resolveSegments();
        if (segments.isEmpty()) {
            Dialogs.error("Nothing to save", "Fill in this segment's fields, or add at least one to the queue.");
            return;
        }
        Dialogs.prompt("Save preset", "Preset name", "My preset").ifPresent(name ->
                AsyncTask.runDb(
                        () -> library.presets().create(name, new SessionSpec(segments, null)),
                        id -> Dialogs.info("Preset saved", "\"" + name + "\" is ready on the home screen."),
                        err -> Dialogs.error("Could not save preset", err.getMessage())));
    }

    private Integer parseCount(String value) {
        if (value.equals("Unlimited")) return null;
        if (value.equals("Custom…")) {
            return Dialogs.prompt("Custom count", "Number of images", "20")
                    .map(this::parseIntOrNull).orElse(null);
        }
        return Integer.parseInt(value);
    }

    private int parseInterval(String value) {
        return switch (value) {
            case "Untimed" -> 0;
            case "10s" -> 10;
            case "30s" -> 30;
            case "60s" -> 60;
            case "2m" -> 120;
            case "3m" -> 180;
            case "5m" -> 300;
            case "10m" -> 600;
            case "Custom…" -> Dialogs.prompt("Custom interval", "Seconds per image", "45")
                    .map(this::parseIntOrNull).orElse(-1);
            default -> 0;
        };
    }

    private Integer parseIntOrNull(String s) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private int parseIntOr(String s, int fallback) {
        Integer v = parseIntOrNull(s);
        return v == null ? fallback : v;
    }
}
