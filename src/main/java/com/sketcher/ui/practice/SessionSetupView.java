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
import com.sketcher.ui.components.AppButton;
import com.sketcher.ui.components.Card;
import com.sketcher.ui.components.FormRow;
import com.sketcher.ui.components.NumberField;
import com.sketcher.ui.components.Page;
import com.sketcher.ui.components.SegmentedControl;
import com.sketcher.ui.gallery.FilterBar;
import javafx.geometry.Pos;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
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
public class SessionSetupView extends Page {
    private final LibraryService library;
    private final FilterBar filterBar;
    private final List<Segment> queuedSegments = new ArrayList<>();
    private final VBox queueListBox = new VBox(8);
    private final Label queueHint = new Label(
            "Starting/saving now uses this queue, not the form above — add the current segment to it first if it should be included too.");
    private List<Playlist> playlistsCache = List.of();

    private final SegmentedControl<SourceKind> sourceChoice = new SegmentedControl<SourceKind>()
            .option(SourceKind.FILTER, "Filter")
            .option(SourceKind.PLAYLIST, "Playlist");
    private final FormRow filterRow;
    private final FormRow playlistRow;
    private final ChoiceBox<String> playlistChoice = new ChoiceBox<>();

    private final ChoiceBox<String> countChoice = new ChoiceBox<>();
    private final ChoiceBox<String> intervalChoice = new ChoiceBox<>();
    private final FormRow intervalRow;
    private final SegmentedControl<OrderMode> orderChoice = new SegmentedControl<OrderMode>()
            .option(OrderMode.RANDOM, "Random")
            .option(OrderMode.SEQUENTIAL, "Sequential");
    private final SegmentedControl<TickBehavior> tickChoice = new SegmentedControl<TickBehavior>()
            .option(TickBehavior.ADVANCE_IMMEDIATELY, "Advance")
            .option(TickBehavior.BREAK, "Take a break")
            .option(TickBehavior.WAIT_FOR_CLICK, "Wait for click");
    private final TextField breakSecondsField = new NumberField(10);
    private final HBox breakFields;
    private final FormRow tickRow;

    private final CheckBox rampBox = new CheckBox("Ramp to");
    private final TextField rampToField = new NumberField(10);
    private final HBox rampFields;
    private final FormRow rampRow;

    private final SegmentedControl<Boolean> modeChoice = new SegmentedControl<Boolean>()
            .option(false, "Standard")
            .option(true, "Memory drawing");
    private final TextField memoryShowField = new NumberField(30);
    private final TextField memoryHideField = new NumberField(10);
    private final FormRow memoryRow;

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
        super(editingPresetId != null ? "Edit preset" : "Practice");
        this.library = library;
        this.pendingPreselectPlaylistId = preselectPlaylistId;
        this.editingPresetId = editingPresetId;
        getStyleClass().add("session-setup-view");
        setSubtitle("Choose what to draw and how long you get with each image.");

        filterBar = new FilterBar(library, false);

        sourceChoice.setValue(preselectPlaylistId == null ? SourceKind.FILTER : SourceKind.PLAYLIST);
        sourceChoice.onChange(v -> updateSourceVisibility());
        filterRow = new FormRow("Images matching").below(filterBar);
        playlistChoice.setMinWidth(240);
        playlistRow = new FormRow("Playlist", playlistChoice,
                AppButton.ghost("Refresh", this::loadPlaylists).small());

        countChoice.getItems().addAll(COUNT_OPTIONS);
        countChoice.getSelectionModel().select("Unlimited");
        intervalChoice.getItems().addAll(INTERVAL_OPTIONS);
        intervalChoice.getSelectionModel().select("Untimed");
        intervalChoice.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> updateTimingAvailability());
        countChoice.setMinWidth(160);
        intervalChoice.setMinWidth(160);

        tickChoice.onChange(v -> updateTimingAvailability());
        breakFields = new HBox(10, breakSecondsField, unit("sec"));
        breakFields.setAlignment(Pos.CENTER_LEFT);

        rampBox.setOnAction(e -> updateTimingAvailability());
        rampFields = new HBox(10, rampToField, unit("sec/image"));
        rampFields.setAlignment(Pos.CENTER_LEFT);

        modeChoice.onChange(v -> updateTimingAvailability());
        HBox memoryTimes = new HBox(10, unit("Show for"), memoryShowField, unit("sec, then hide for"),
                memoryHideField, unit("sec"));
        memoryTimes.setAlignment(Pos.CENTER_LEFT);

        intervalRow = new FormRow("Time per image", intervalChoice);
        tickRow = new FormRow("When time is up", tickChoice, breakFields);
        rampRow = new FormRow("Ramp timer", rampBox, rampFields)
                .hint("Gradually changes the time per image from the interval above to this value.");
        memoryRow = new FormRow("Memory timing", memoryTimes)
                .hint("While hidden, draw from memory — it reveals again when you click, press Space, or →.");

        queueListBox.setVisible(false);
        queueListBox.setManaged(false);
        queueHint.getStyleClass().add("hint-label");
        queueHint.setWrapText(true);
        queueHint.setVisible(false);
        queueHint.setManaged(false);

        AppButton startButton = AppButton.primary("Start practice", () -> start(onStart));
        AppButton secondaryButton = editingPresetId != null
                ? AppButton.secondary("Update preset", () -> updatePreset(editingPresetId))
                : AppButton.secondary("Save as preset…", this::saveAsPreset);
        setActions(secondaryButton, startButton);

        Card queueCard = new Card("Segment queue",
                "Optional. Chain several differently-configured segments into one session.")
                .headerActions(AppButton.secondary("+ Add this segment", this::addSegmentToQueue).small());
        queueCard.add(queueHint, queueListBox);

        setScrollingBody(
                new Card("Source").add(new FormRow("Draw from", sourceChoice), filterRow, playlistRow),
                new Card("Images").add(new FormRow("Image count", countChoice), new FormRow("Order", orderChoice)),
                new Card("Timing").add(new FormRow("Mode", modeChoice), intervalRow, tickRow, rampRow, memoryRow),
                queueCard);

        if (editingSpec != null && !editingSpec.segments().isEmpty()) {
            applyEditingSpec(editingSpec);
        }

        updateSourceVisibility();
        updateTimingAvailability();
        loadPlaylists();
    }

    private static Label unit(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("muted-label");
        label.setMinWidth(Label.USE_PREF_SIZE);
        return label;
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
        sourceChoice.setValue(last.sourceKind());
        if (last.sourceKind() == SourceKind.PLAYLIST) {
            pendingPreselectPlaylistId = last.playlistId();
        } else {
            filterBar.loadFilter(last.filter());
        }

        selectChoice(countChoice, last.itemCount() == null ? "Unlimited" : String.valueOf(last.itemCount()));
        orderChoice.setValue(last.orderMode());

        if (last.isMemoryMode()) {
            modeChoice.setValue(true);
            memoryShowField.setText(String.valueOf(last.memoryShowSeconds()));
            memoryHideField.setText(String.valueOf(last.memoryHideSeconds()));
        } else {
            selectChoice(intervalChoice, intervalLabelFor(last.secondsPerImage()));
            tickChoice.setValue(last.tickBehavior());
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

    private void updateSourceVisibility() {
        boolean playlist = sourceChoice.getValue() == SourceKind.PLAYLIST;
        filterRow.setShown(!playlist);
        playlistRow.setShown(playlist);
    }

    /**
     * Hides (not just disables) whichever timing rows don't apply right now,
     * instead of leaving dead, disabled controls sitting on screen — "When
     * timer ends" and "Ramp timer" are meaningless while Untimed is picked
     * (the default), and the whole interval/tick/ramp cluster is meaningless
     * once Memory drawing mode takes over timing.
     */
    private void updateTimingAvailability() {
        boolean memoryMode = modeChoice.getValue();
        boolean timed = !"Untimed".equals(intervalChoice.getValue());

        memoryRow.setShown(memoryMode);
        intervalRow.setShown(!memoryMode);

        boolean showTickRow = !memoryMode && timed;
        tickRow.setShown(showTickRow);
        boolean breaking = tickChoice.getValue() == TickBehavior.BREAK;
        breakFields.setVisible(breaking);
        breakFields.setManaged(breaking);

        boolean showRampRow = !memoryMode && timed;
        rampRow.setShown(showRampRow);

        boolean ramping = rampBox.isSelected() && showRampRow;
        rampFields.setVisible(ramping);
        rampFields.setManaged(ramping);
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
        OrderMode order = orderChoice.getValue();

        boolean memoryMode = modeChoice.getValue();
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
            tickBehavior = tickChoice.getValue();
            breakSeconds = parseIntOr(breakSecondsField.getText(), 10);
            if (rampBox.isSelected() && seconds > 0) {
                rampTo = parseIntOrNull(rampToField.getText());
                if (rampTo == null || rampTo < 0) return null;
            }
        }

        Segment segment;
        if (sourceChoice.getValue() == SourceKind.PLAYLIST) {
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
        Label number = new Label(String.valueOf(index + 1));
        number.getStyleClass().add("queue-index");
        Label summary = new Label(summarizeSegment(segment));
        summary.setWrapText(true);
        summary.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(summary, Priority.ALWAYS);

        AppButton upButton = AppButton.icon("↑", "Move up", () -> {
            queuedSegments.add(index - 1, queuedSegments.remove(index));
            renderQueueList();
        });
        upButton.setDisable(index == 0);
        AppButton downButton = AppButton.icon("↓", "Move down", () -> {
            queuedSegments.add(index + 1, queuedSegments.remove(index));
            renderQueueList();
        });
        downButton.setDisable(index == queuedSegments.size() - 1);
        AppButton removeButton = AppButton.icon("✕", "Remove", () -> {
            queuedSegments.remove(index);
            renderQueueList();
        });

        HBox row = new HBox(12, number, summary, upButton, downButton, removeButton);
        row.getStyleClass().add("filter-row");
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
