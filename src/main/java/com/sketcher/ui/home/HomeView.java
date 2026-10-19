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
import com.sketcher.ui.components.EmptyState;
import com.sketcher.ui.components.Page;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.FlowPane;

import java.util.List;
import java.util.function.Consumer;

/**
 * The launcher screen (PLAN.md 4.6): one-click preset cards (pinned first,
 * then most-recently-used), a "Quick start" card for the last-used settings,
 * a small library stat line, and secondary links. Deliberately a launcher,
 * not a dashboard.
 */
public class HomeView extends Page implements Refreshable {
    private static final String LAST_SESSION_SPEC_KEY = "last_session_spec";

    private final LibraryService library;
    private final Consumer<SessionSpec> onStartSession;
    private final Consumer<Integer> onEditPreset;
    private final FlowPane cardsPane = new FlowPane(20, 20);

    public HomeView(LibraryService library, Consumer<SessionSpec> onStartSession, Consumer<Integer> onEditPreset) {
        super("Home");
        this.library = library;
        this.onStartSession = onStartSession;
        this.onEditPreset = onEditPreset;
        getStyleClass().add("home-view");

        ScrollPane scroll = new ScrollPane(cardsPane);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("page-scroll");
        setBody(scroll);

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
        setSubtitle(data.imageCount() + " images  ·  " + data.tagCount() + " tags  ·  "
                + data.seenThisWeek() + " practiced this week");

        cardsPane.getChildren().clear();
        if (data.lastSessionJson() != null) {
            cardsPane.getChildren().add(buildQuickStartCard(data.lastSessionJson()));
        }
        for (Preset preset : data.presets()) {
            cardsPane.getChildren().add(buildPresetCard(preset));
        }
        if (data.presets().isEmpty() && data.lastSessionJson() == null) {
            EmptyState empty = new EmptyState("No presets yet", data.imageCount() == 0
                    ? "Import some images, then start a session from Practice to get going."
                    : "Start a session from Practice, then save it as a preset for one-click practice here.");
            empty.prefWidthProperty().bind(cardsPane.widthProperty());
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
        PresetCard card = new PresetCard("Quick start", summarize(spec) + "\nYour last used settings", false,
                () -> onStartSession.accept(spec), null, null, null);
        card.accent();
        return card;
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
