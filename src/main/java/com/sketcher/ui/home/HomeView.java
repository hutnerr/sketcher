package com.sketcher.ui.home;

import com.sketcher.filter.Filter;
import com.sketcher.filter.FilterSummary;
import com.sketcher.library.LibraryService;
import com.sketcher.model.Preset;
import com.sketcher.model.Segment;
import com.sketcher.model.SessionSpec;
import com.sketcher.model.SourceKind;
import com.sketcher.practice.SessionSpecCodec;
import com.sketcher.ui.common.AsyncTask;
import com.sketcher.ui.common.Dialogs;
import com.sketcher.ui.common.Refreshable;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.function.Consumer;

/**
 * The launcher screen (PLAN.md 4.6): one-click preset cards (pinned first,
 * then most-recently-used), a "Quick start" card for the last-used settings,
 * a small library stat line, and secondary links. Deliberately a launcher,
 * not a dashboard.
 */
public class HomeView extends VBox implements Refreshable {
    private static final String LAST_SESSION_SPEC_KEY = "last_session_spec";

    private final LibraryService library;
    private final Consumer<SessionSpec> onStartSession;
    private final Consumer<Integer> onEditPreset;
    private final FlowPane cardsPane = new FlowPane(12, 12);
    private final Label statsLabel = new Label();

    public HomeView(LibraryService library, Consumer<SessionSpec> onStartSession, Consumer<Integer> onEditPreset) {
        this.library = library;
        this.onStartSession = onStartSession;
        this.onEditPreset = onEditPreset;
        getStyleClass().add("home-view");
        setSpacing(16);
        setPadding(new Insets(24));

        Label title = new Label("Sketcher");
        title.getStyleClass().add("home-title");
        statsLabel.getStyleClass().add("hint-label");

        getChildren().addAll(title, statsLabel, cardsPane);
        VBox.setVgrow(cardsPane, javafx.scene.layout.Priority.ALWAYS);

        refresh();
    }

    @Override
    public void onShown() {
        refresh();
    }

    private record HomeData(int imageCount, int tagCount, int seenThisWeek, List<Preset> presets,
                             String lastSessionJson) {
    }

    private void refresh() {
        AsyncTask.runDb(
                () -> new HomeData(
                        library.images().count(new Filter.All()),
                        library.tags().listAll().size(),
                        library.images().countSeenSince(System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000),
                        library.presets().listAll(),
                        library.settings().get(LAST_SESSION_SPEC_KEY).orElse(null)),
                this::render,
                err -> Dialogs.error("Could not load home screen", err.getMessage()));
    }

    private void render(HomeData data) {
        statsLabel.setText(data.imageCount() + " image(s)  ·  " + data.tagCount() + " tag(s)  ·  "
                + data.seenThisWeek() + " practiced this week");

        cardsPane.getChildren().clear();
        if (data.lastSessionJson() != null) {
            cardsPane.getChildren().add(buildQuickStartCard(data.lastSessionJson()));
        }
        for (Preset preset : data.presets()) {
            cardsPane.getChildren().add(buildPresetCard(preset));
        }
        if (data.presets().isEmpty() && data.lastSessionJson() == null) {
            Label empty = new Label(data.imageCount() == 0
                    ? "Import some images, then start a session from Practice to get going."
                    : "Start a session from Practice, then save it as a preset for one-click practice here.");
            empty.getStyleClass().add("hint-label");
            empty.setWrapText(true);
            cardsPane.getChildren().add(empty);
        }
    }

    private PresetCard buildPresetCard(Preset preset) {
        SessionSpec spec = library.presets().loadSpec(preset.id());
        return new PresetCard(preset.name(), summarize(spec), preset.pinned(),
                () -> onStartSession.accept(spec),
                () -> {
                    library.presets().setPinned(preset.id(), !preset.pinned());
                    refresh();
                },
                () -> onEditPreset.accept(preset.id()),
                () -> {
                    if (Dialogs.confirm("Delete preset", "Delete \"" + preset.name() + "\"?")) {
                        library.presets().delete(preset.id());
                        refresh();
                    }
                });
    }

    private PresetCard buildQuickStartCard(String lastSessionJson) {
        SessionSpec spec = SessionSpecCodec.decode(lastSessionJson);
        return new PresetCard("Quick start", summarize(spec) + "  (last used settings)", false,
                () -> onStartSession.accept(spec), null, null, null);
    }

    private String summarize(SessionSpec spec) {
        if (spec.segments().size() != 1) {
            return spec.segments().size() + " segments";
        }
        Segment segment = spec.segments().get(0);
        String source = segment.sourceKind() == SourceKind.FILTER
                ? FilterSummary.describe(segment.filter(), id -> library.tags().get(id).map(t -> t.name()).orElse("?"))
                : "playlist";
        String count = segment.itemCount() == null ? "unlimited" : segment.itemCount() + " images";
        String timing = segment.isTimed() ? segment.secondsPerImage() + "s/image" : "untimed";
        return source + " · " + count + " · " + timing;
    }
}
