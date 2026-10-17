package com.sketcher.ui.practice;

import com.sketcher.library.LibraryService;
import com.sketcher.model.SessionSpec;
import com.sketcher.ui.common.Refreshable;
import javafx.scene.layout.StackPane;

/** Swaps between session setup and the running session (PLAN.md 4.4/4.6 fast-path entry point). */
public class PracticeContainerView extends StackPane implements Refreshable {
    private final LibraryService library;

    public PracticeContainerView(LibraryService library) {
        this.library = library;
        showSetup();
    }

    /**
     * Only initializes a blank setup screen the first time this is ever
     * shown. Any other time — including right after showSetupWithPlaylist()
     * or showSetupForEdit() called shell.show("practice") to display what
     * they just built — there's already real content here (a configured
     * SessionSetupView or a running PracticeView), and resetting it on every
     * nav visit would both discard that pre-filled state and wipe an
     * in-progress (not-yet-started) form if the user just clicked away and back.
     */
    @Override
    public void onShown() {
        if (getChildren().isEmpty()) {
            showSetup();
        }
    }

    /** Skips setup and starts running a SessionSpec directly (PLAN.md 4.6: "one click" from Home). */
    public void startSpec(SessionSpec spec) {
        PracticeView view = new PracticeView(library, spec, this::showSetup);
        getChildren().setAll(view);
        // Without this, keyboard focus stays wherever it was (often the nav
        // sidebar, once the old setup form's focused button gets swapped out
        // of the scene) — PracticeView's own arrow/space/etc. shortcuts never
        // receive those key presses, which instead toggle the nav buttons.
        view.requestFocus();
    }

    /** Opens setup with Playlist pre-selected as the source, so timing/order/count can still be chosen. */
    public void showSetupWithPlaylist(int playlistId) {
        getChildren().setAll(new SessionSetupView(library, this::startSpec, playlistId));
    }

    /** Opens setup pre-filled from an existing preset, with "Save as preset…" swapped for "Update preset" (Home page's "Edit" action). */
    public void showSetupForEdit(int presetId, SessionSpec spec) {
        getChildren().setAll(new SessionSetupView(library, this::startSpec, spec, presetId));
    }

    private void showSetup() {
        getChildren().setAll(new SessionSetupView(library, this::startSpec));
    }
}
