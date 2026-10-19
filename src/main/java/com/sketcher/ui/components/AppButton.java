package com.sketcher.ui.components;

import javafx.scene.control.Button;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;

/**
 * The app's one button. Every button picks a {@link Variant} (how much visual
 * weight it carries) and optionally {@link #small()} / icon-only sizing, so
 * the same action looks the same on every page. Plain {@code new Button()}
 * still renders as SECONDARY via the base .button rule in style.css — this
 * class just makes the intent explicit at the call site.
 */
public class AppButton extends Button {

    public enum Variant {
        /** The one main action on a surface (Start, Import, Save). */
        PRIMARY("btn-primary"),
        /** Default bordered button. */
        SECONDARY("btn-secondary"),
        /** Borderless, for dense toolbars and low-priority actions. */
        GHOST("btn-ghost"),
        /** Destructive actions. */
        DANGER("btn-danger");

        final String styleClass;

        Variant(String styleClass) {
            this.styleClass = styleClass;
        }
    }

    public AppButton(String text, Variant variant) {
        super(text);
        styleAs(this, variant);
    }

    public AppButton(String text, Variant variant, Runnable onAction) {
        this(text, variant);
        if (onAction != null) setOnAction(e -> onAction.run());
    }

    public static AppButton primary(String text, Runnable onAction) {
        return new AppButton(text, Variant.PRIMARY, onAction);
    }

    public static AppButton secondary(String text, Runnable onAction) {
        return new AppButton(text, Variant.SECONDARY, onAction);
    }

    public static AppButton ghost(String text, Runnable onAction) {
        return new AppButton(text, Variant.GHOST, onAction);
    }

    public static AppButton danger(String text, Runnable onAction) {
        return new AppButton(text, Variant.DANGER, onAction);
    }

    /** A square, glyph-only ghost button (✕, ↑, ↓…); the tooltip carries the label. */
    public static AppButton icon(String glyph, String tooltip, Runnable onAction) {
        AppButton button = new AppButton(glyph, Variant.GHOST, onAction);
        button.getStyleClass().add("btn-icon");
        if (tooltip != null) button.setTooltip(new Tooltip(tooltip));
        return button;
    }

    /** A toggle styled to match: bordered when off, accent-tinted when on. */
    public static ToggleButton toggle(String text, Variant variant) {
        ToggleButton button = new ToggleButton(text);
        styleAs(button, variant);
        return button;
    }

    /** Compact sizing for dense toolbars. Returns this for chaining. */
    public AppButton small() {
        getStyleClass().add("btn-sm");
        return this;
    }

    /** Stretches to fill whatever width the parent offers. Returns this for chaining. */
    public AppButton fill() {
        setMaxWidth(Double.MAX_VALUE);
        return this;
    }

    public static <B extends ButtonBase> B small(B button) {
        button.getStyleClass().add("btn-sm");
        return button;
    }

    private static void styleAs(ButtonBase button, Variant variant) {
        button.getStyleClass().addAll("btn", variant.styleClass);
        // Never squeeze a button below its label — a crowded row should wrap
        // or scroll, not turn "Start practice" into "Start p…".
        button.setMinWidth(USE_PREF_SIZE);
    }
}
