package com.sketcher.ui.tags;

import com.sketcher.ui.components.Chip;

/** A tag rendered as a {@link Chip}, optionally with a remove button. */
public class TagChip extends Chip {

    public TagChip(String text) {
        this(text, null);
    }

    public TagChip(String text, Runnable onRemove) {
        super(text, onRemove);
        getStyleClass().add("tag-chip");
    }
}
