package com.sketcher.ui.practice;

import com.sketcher.library.LibraryService;
import com.sketcher.model.ImageRecord;
import com.sketcher.model.Segment;
import com.sketcher.model.SessionSpec;
import com.sketcher.practice.ResolvedSegment;
import com.sketcher.practice.SessionBuilder;
import com.sketcher.practice.SessionEngine;
import com.sketcher.practice.SessionSpecCodec;
import com.sketcher.ui.common.AsyncTask;
import com.sketcher.ui.common.Dialogs;
import com.sketcher.ui.components.AppButton;
import com.sketcher.ui.components.Toolbar;
import com.sketcher.ui.tags.TagChip;
import com.sketcher.ui.tags.TagPicker;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

import java.util.List;
import java.util.Map;

/**
 * Runs a practice session: image display with zoom/pan/flip/grayscale, a
 * HUD, timer/tick-behavior UI, inline tag/favorite editing, and the memory-
 * drawing show/hide/reveal cycle (PLAN.md 4.4).
 */
public class PracticeView extends BorderPane {
    /** Shared source of truth for the shortcut table shown in the HUD footer. */
    public static final Map<String, String> SHORTCUTS = Map.ofEntries(
            Map.entry("Space", "pause / resume"), Map.entry("Left", "previous"), Map.entry("Right", "next"),
            Map.entry("Esc", "exit session"), Map.entry("F", "favorite current image"),
            Map.entry("G", "grayscale toggle"), Map.entry("H / V", "flip horizontal / vertical"),
            Map.entry("+ / - / 0", "zoom in / out / fit"), Map.entry("T", "tag current image"));

    private static final String LAST_SESSION_SPEC_KEY = "last_session_spec";

    private final LibraryService library;
    private final SessionSpec spec;
    private final SessionEngine engine;
    private final Runnable onExit;
    private final Timeline timeline;

    private final ImageView imageView = new ImageView();
    private final Group imageGroup = new Group(imageView);
    private final StackPane viewport = new StackPane(imageGroup);
    private final ColorAdjust grayscale = new ColorAdjust();

    private final Label hudTime = new Label();
    private final Label hudPosition = new Label();
    private final FlowPane hudTags = new FlowPane(8, 8);
    private final CheckBox showTagsBox = new CheckBox("Show tags");
    private final CheckBox keepTransformsBox = new CheckBox("Keep flip/grayscale");
    private final ToggleButton pauseButton = AppButton.small(AppButton.toggle("Pause", AppButton.Variant.SECONDARY));
    private final ToggleButton favoriteButton = AppButton.small(AppButton.toggle("★ Favorite", AppButton.Variant.GHOST));
    private final Label overlayLabel = new Label();

    private boolean flipH, flipV, grayscaleOn;
    private double zoom = 1.0;
    private double dragStartX, dragStartY, groupStartX, groupStartY;
    private ImageRecord currentRecord;

    public PracticeView(LibraryService library, SessionSpec spec, Runnable onExit) {
        this.library = library;
        this.spec = spec;
        this.onExit = onExit;
        getStyleClass().add("practice-view");

        List<ResolvedSegment> resolved = new SessionBuilder().resolve(spec, library);
        this.engine = new SessionEngine(library, resolved, spec.presetId());

        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);
        viewport.getStyleClass().add("image-area");
        Rectangle clip = new Rectangle();
        clip.widthProperty().bind(viewport.widthProperty());
        clip.heightProperty().bind(viewport.heightProperty());
        viewport.setClip(clip);
        imageView.fitWidthProperty().bind(viewport.widthProperty());
        imageView.fitHeightProperty().bind(viewport.heightProperty());

        viewport.setOnScroll(e -> {
            // Touchpad gestures fire a flood of deltaY=0 phase/inertia events
            // alongside the real ±N ticks; treating 0 as "scroll down" let
            // that noise outvote real scroll-up attempts. Ignore it outright.
            if (e.getDeltaY() != 0) {
                setZoom(zoom * (e.getDeltaY() > 0 ? 1.1 : 0.9));
            }
            e.consume();
        });
        viewport.setOnMousePressed(e -> {
            dragStartX = e.getSceneX();
            dragStartY = e.getSceneY();
            groupStartX = imageGroup.getTranslateX();
            groupStartY = imageGroup.getTranslateY();
        });
        viewport.setOnMouseDragged(e -> {
            imageGroup.setTranslateX(groupStartX + (e.getSceneX() - dragStartX));
            imageGroup.setTranslateY(groupStartY + (e.getSceneY() - dragStartY));
        });
        viewport.setOnMouseClicked(e -> {
            if (awaitingManualAdvance()) engine.advanceClick();
        });

        // Status messages (Paused, Break, Memorize…) float over the image as a
        // pill rather than living in the HUD, so they're seen at a glance.
        overlayLabel.getStyleClass().add("stage-pill");
        overlayLabel.setMouseTransparent(true);
        overlayLabel.visibleProperty().bind(overlayLabel.textProperty().isNotEmpty());
        StackPane stage = new StackPane(viewport, overlayLabel);
        // The image's natural size must never become the stage's minimum size —
        // if it loads before the first layout pass, fitWidth/fitHeight would
        // otherwise lock onto it and push the toolbar/HUD off-screen.
        viewport.setMinSize(0, 0);
        stage.setMinSize(0, 0);
        StackPane.setAlignment(overlayLabel, Pos.TOP_CENTER);
        StackPane.setMargin(overlayLabel, new Insets(24));
        setCenter(stage);
        setTop(buildTopBar());
        setBottom(buildHud());

        showTagsBox.setSelected(true);
        showTagsBox.setOnAction(e -> {
            if (currentRecord != null) renderTags(currentRecord);
        });
        keepTransformsBox.setSelected(true);
        pauseButton.setOnAction(e -> togglePause());
        favoriteButton.setOnAction(e -> toggleFavorite());

        engine.setListener(new SessionEngine.Listener() {
            @Override
            public void onImageChanged(int imageId, Segment segment, int position, int total) {
                showImage(imageId, segment, position, total);
            }

            @Override
            public void onPhaseChanged(SessionEngine.Phase phase, int secondsRemaining) {
                updatePhaseUi(phase, secondsRemaining);
            }

            @Override
            public void onSessionFinished() {
                finishSession();
            }
        });

        timeline = new Timeline(new KeyFrame(Duration.seconds(1), e -> engine.tick()));
        timeline.setCycleCount(Animation.INDEFINITE);

        setFocusTraversable(true);
        setOnKeyPressed(this::handleKey);

        if (engine.isEmpty()) {
            Dialogs.error("Nothing to practice", "This filter doesn't match any images.");
            javafx.application.Platform.runLater(onExit);
            return;
        }
        recordSessionStart();
        engine.start();
        timeline.play();
    }

    /** Remembers this spec for Home's "Quick start" and, if it came from a preset, bumps its use stats. */
    private void recordSessionStart() {
        library.settings().set(LAST_SESSION_SPEC_KEY, SessionSpecCodec.encode(spec));
        if (spec.presetId() != null) {
            library.presets().recordUsed(spec.presetId());
        }
    }

    private Toolbar buildTopBar() {
        ToggleButton grayscaleButton = AppButton.small(AppButton.toggle("Grayscale", AppButton.Variant.GHOST));
        grayscaleButton.setOnAction(e -> { grayscaleOn = grayscaleButton.isSelected(); applyGrayscale(); });

        return new Toolbar()
                .left(AppButton.ghost("← Exit", this::confirmExit).small())
                .center(AppButton.icon("‹", "Previous (←)", engine::previous), pauseButton,
                        AppButton.icon("›", "Next (→)", this::advance))
                .right(AppButton.ghost("Flip H", () -> { flipH = !flipH; applyFlip(); }).small(),
                        AppButton.ghost("Flip V", () -> { flipV = !flipV; applyFlip(); }).small(),
                        grayscaleButton,
                        AppButton.ghost("Fit", this::resetZoomPan).small(),
                        Toolbar.divider(),
                        favoriteButton,
                        AppButton.ghost("Tag…", this::tagCurrent).small());
    }

    /** Same "next" semantics as the Right-arrow shortcut: dismiss a waiting phase before actually advancing. */
    private void advance() {
        if (awaitingManualAdvance()) engine.advanceClick();
        else engine.next();
    }

    private HBox buildHud() {
        hudPosition.getStyleClass().add("hud-meta");
        hudTime.getStyleClass().add("hud-timer");
        HBox.setHgrow(hudTags, Priority.ALWAYS);
        hudTags.setAlignment(Pos.CENTER_LEFT);

        keepTransformsBox.setMinWidth(HBox.USE_PREF_SIZE);
        showTagsBox.setMinWidth(HBox.USE_PREF_SIZE);
        HBox options = new HBox(20, keepTransformsBox, showTagsBox);
        options.setAlignment(Pos.CENTER_LEFT);
        options.setMinWidth(HBox.USE_PREF_SIZE);
        VBox left = new VBox(8, hudPosition, hudTags);
        HBox.setHgrow(left, Priority.ALWAYS);

        HBox hud = new HBox(24, left, options, hudTime);
        hud.setAlignment(Pos.CENTER_LEFT);
        hud.setPadding(new Insets(14, 24, 14, 24));
        hud.getStyleClass().addAll("app-toolbar", "bottom", "hud");
        return hud;
    }

    private void showImage(int imageId, Segment segment, int position, int total) {
        AsyncTask.runDb(
                () -> library.images().findById(imageId).orElse(null),
                record -> {
                    currentRecord = record;
                    if (record == null) return;
                    String uri = library.ref().imagePath(record.hash(), record.ext()).toUri().toString();
                    imageView.setImage(new Image(uri, 0, 0, true, true, true));
                    if (!keepTransformsBox.isSelected()) {
                        flipH = false;
                        flipV = false;
                    }
                    resetZoomPan();
                    applyFlip();
                    favoriteButton.setSelected(record.favorite());
                    hudPosition.setText("Image " + (position + 1) + " of " + total);
                    renderTags(record);
                },
                err -> Dialogs.error("Could not load image", err.getMessage()));
    }

    private void renderTags(ImageRecord record) {
        hudTags.getChildren().clear();
        if (!showTagsBox.isSelected()) return;
        for (int tagId : record.tagIds()) {
            library.tags().get(tagId).ifPresent(tag ->
                    hudTags.getChildren().add(new TagChip(library.tags().fullPath(tag.id()))));
        }
    }

    private void updatePhaseUi(SessionEngine.Phase phase, int secondsRemaining) {
        switch (phase) {
            case SHOWING -> {
                imageView.setVisible(true);
                hudTime.setText(secondsRemaining > 0 ? secondsRemaining + "s" : "");
                overlayLabel.setText(engine.isPaused() ? "Paused" : "");
            }
            case MEMORY_HIDDEN -> {
                imageView.setVisible(false);
                hudTime.setText(secondsRemaining + "s");
                overlayLabel.setText("Memorize: hidden for " + secondsRemaining + "s");
            }
            case MEMORY_REVEALED -> {
                imageView.setVisible(true);
                hudTime.setText("");
                overlayLabel.setText("Compare your drawing — click, Space, or -> to continue");
            }
            case BREAK -> overlayLabel.setText("Break: " + secondsRemaining + "s");
            case WAITING_FOR_CLICK -> overlayLabel.setText("Click, press Space, or press -> to continue");
            case FINISHED -> overlayLabel.setText("Session finished");
        }
        pauseButton.setSelected(engine.isPaused());
    }

    private boolean awaitingManualAdvance() {
        return engine.phase() == SessionEngine.Phase.WAITING_FOR_CLICK
                || engine.phase() == SessionEngine.Phase.MEMORY_REVEALED;
    }

    private void handleKey(javafx.scene.input.KeyEvent e) {
        switch (e.getCode()) {
            case SPACE -> {
                if (awaitingManualAdvance()) engine.advanceClick();
                else togglePause();
            }
            case LEFT -> engine.previous();
            case RIGHT -> advance();
            case ESCAPE -> confirmExit();
            case F -> toggleFavorite();
            case G -> { grayscaleOn = !grayscaleOn; applyGrayscale(); }
            case H -> { flipH = !flipH; applyFlip(); }
            case V -> { flipV = !flipV; applyFlip(); }
            case PLUS, ADD, EQUALS -> setZoom(zoom * 1.2);
            case MINUS, SUBTRACT -> setZoom(zoom * 0.8);
            case DIGIT0, NUMPAD0 -> resetZoomPan();
            case T -> tagCurrent();
            default -> {
            }
        }
        e.consume();
    }

    private void togglePause() {
        engine.togglePause();
    }

    private void toggleFavorite() {
        if (currentRecord == null) return;
        boolean newValue = !currentRecord.favorite();
        library.images().setFavorite(currentRecord.id(), newValue);
        favoriteButton.setSelected(newValue);
    }

    private void tagCurrent() {
        if (currentRecord == null) return;
        TagPicker.pickMany(library, new java.util.HashSet<>(currentRecord.tagIds())).ifPresent(tagIds -> {
            library.images().addTags(currentRecord.id(), tagIds);
            library.images().findById(currentRecord.id()).ifPresent(updated -> {
                currentRecord = updated;
                renderTags(updated);
            });
        });
    }

    private void applyFlip() {
        imageView.setScaleX(flipH ? -1 : 1);
        imageView.setScaleY(flipV ? -1 : 1);
    }

    private void applyGrayscale() {
        grayscale.setSaturation(-1);
        imageView.setEffect(grayscaleOn ? grayscale : null);
    }

    private void setZoom(double newZoom) {
        zoom = Math.max(0.2, Math.min(8.0, newZoom));
        imageGroup.setScaleX(zoom);
        imageGroup.setScaleY(zoom);
    }

    private void resetZoomPan() {
        zoom = 1.0;
        imageGroup.setScaleX(1);
        imageGroup.setScaleY(1);
        imageGroup.setTranslateX(0);
        imageGroup.setTranslateY(0);
    }

    private void confirmExit() {
        if (Dialogs.confirm("Exit session", "End this practice session?")) {
            timeline.stop();
            engine.end();
            onExit.run();
        }
    }

    private void finishSession() {
        timeline.stop();
        Dialogs.info("Session complete", "You've gone through all the images in this session.");
        offerSaveAsPreset();
        onExit.run();
    }

    /** "save this as a preset" at the end of a session (PLAN.md 4.5) — skipped if already run from one. */
    private void offerSaveAsPreset() {
        if (spec.presetId() != null) return;
        if (!Dialogs.confirm("Save as preset", "Save these settings as a reusable preset for one-click practice?")) {
            return;
        }
        Dialogs.prompt("Save preset", "Preset name", "My preset").ifPresent(name -> {
            try {
                library.presets().create(name, spec);
            } catch (IllegalStateException e) {
                Dialogs.error("Could not save preset", e.getMessage());
            }
        });
    }
}
