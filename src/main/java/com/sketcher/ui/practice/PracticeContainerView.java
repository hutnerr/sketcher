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

    @Override
    public void onShown() {
        if (getChildren().isEmpty() || !(getChildren().get(0) instanceof PracticeView)) {
            showSetup();
        }
    }

    /** Skips setup and starts running a SessionSpec directly (PLAN.md 4.6: "one click" from Home). */
    public void startSpec(SessionSpec spec) {
        getChildren().setAll(new PracticeView(library, spec, this::showSetup));
    }

    private void showSetup() {
        getChildren().setAll(new SessionSetupView(library, this::startSpec));
    }
}
