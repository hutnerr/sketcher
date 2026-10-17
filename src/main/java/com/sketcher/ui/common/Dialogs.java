package com.sketcher.ui.common;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.TextInputDialog;

import java.util.Optional;

public final class Dialogs {
    private Dialogs() {
    }

    public static void error(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message);
        alert.setTitle(title);
        alert.setHeaderText(null);
        Theme.style(alert.getDialogPane());
        alert.showAndWait();
    }

    public static void info(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message);
        alert.setTitle(title);
        alert.setHeaderText(null);
        Theme.style(alert.getDialogPane());
        alert.showAndWait();
    }

    public static boolean confirm(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.YES, ButtonType.NO);
        alert.setTitle(title);
        alert.setHeaderText(null);
        Theme.style(alert.getDialogPane());
        return alert.showAndWait().orElse(ButtonType.NO) == ButtonType.YES;
    }

    public static Optional<String> prompt(String title, String label, String defaultValue) {
        TextInputDialog dialog = new TextInputDialog(defaultValue == null ? "" : defaultValue);
        dialog.setTitle(title);
        dialog.setHeaderText(null);
        dialog.setContentText(label);
        Theme.style(dialog.getDialogPane());
        return dialog.showAndWait().map(String::trim).filter(s -> !s.isEmpty());
    }

    /**
     * Runs a synchronous, file-system-touching action (e.g. a recycle-bin
     * move) directly on the FX thread, showing an error dialog instead of
     * crashing the UI thread if it throws. Returns whether it succeeded, so
     * callers can skip their own follow-up (e.g. a refresh) on failure.
     */
    public static boolean tryRun(Runnable action, String errorTitle) {
        try {
            action.run();
            return true;
        } catch (RuntimeException e) {
            error(errorTitle, e.getMessage());
            return false;
        }
    }
}
