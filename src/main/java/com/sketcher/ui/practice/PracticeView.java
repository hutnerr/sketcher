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
import com.sketcher.ui.tags.TagChip;
import com.sketcher.ui.tags.TagPicker;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.ToggleButton;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
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
    private final Label hudSegment = new Label();
    private final FlowPane hudTags = new FlowPane(6, 6);
    private final CheckBox showTagsBox = new CheckBox("Show tags");
    private final CheckBox keepTransformsBox = new CheckBox("Keep flip/grayscale across images");
    private final ToggleButton pauseButton = new ToggleButton("Pause");
    private final ToggleButton favoriteButton = new ToggleButton("★ Favorite");
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

        setCenter(viewport);
        setTop(buildTopBar());
        setBottom(buildHud());

        showTagsBox.setSelected(true);
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

    private HBox buildTopBar() {
        Button exitButton = new Button("Exit session");
        exitButton.setOnAction(e -> confirmExit());
        Button prevButton = new Button("< Prev");
        prevButton.setOnAction(e -> engine.previous());
        Button nextButton = new Button("Next >");
        nextButton.setOnAction(e -> advance());
        Button flipHButton = new Button("Flip H");
        flipHButton.setOnAction(e -> { flipH = !flipH; applyFlip(); });
        Button flipVButton = new Button("Flip V");
        flipVButton.setOnAction(e -> { flipV = !flipV; applyFlip(); });
        ToggleButton grayscaleButton = new ToggleButton("Grayscale");
        grayscaleButton.setOnAction(e -> { grayscaleOn = grayscaleButton.isSelected(); applyGrayscale(); });
        Button zoomFitButton = new Button("Fit");
        zoomFitButton.setOnAction(e -> resetZoomPan());
        Button tagButton = new Button("Tag…");
        tagButton.setOnAction(e -> tagCurrent());

        HBox bar = new HBox(10, exitButton, sep(), pauseButton, prevButton, nextButton, sep(),
                flipHButton, flipVButton, grayscaleButton, zoomFitButton, sep(),
                favoriteButton, tagButton, sep(), keepTransformsBox, showTagsBox);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(14));
        return bar;
    }

    private Separator sep() {
        return new Separator(Orientation.VERTICAL);
    }

    /** Same "next" semantics as the Right-arrow shortcut: dismiss a waiting phase before actually advancing. */
    private void advance() {
        if (awaitingManualAdvance()) engine.advanceClick();
        else engine.next();
    }

    private VBox buildHud() {
        overlayLabel.getStyleClass().add("overlay-label");
        hudTags.setPadding(new Insets(6, 0, 0, 0));
        HBox statusRow = new HBox(20, hudPosition, hudSegment, hudTime);
        statusRow.setAlignment(Pos.CENTER_LEFT);
        VBox hud = new VBox(8, overlayLabel, statusRow, hudTags);
        hud.setPadding(new Insets(14));
        hud.getStyleClass().add("hud");
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
                    hudPosition.setText((position + 1) + " / " + total);
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
