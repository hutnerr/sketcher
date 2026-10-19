package com.sketcher.ui.components;

import javafx.geometry.Pos;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;

/** A compact, centered text field that only accepts digits — for seconds and counts inline in a sentence. */
public class NumberField extends TextField {

    public NumberField(int initial) {
        super(String.valueOf(initial));
        getStyleClass().add("number-field");
        setAlignment(Pos.CENTER);
        setPrefColumnCount(3);
        setPrefWidth(76);
        setMaxWidth(USE_PREF_SIZE);
        setTextFormatter(new TextFormatter<String>(change ->
                change.getControlNewText().matches("\\d{0,5}") ? change : null));
    }
}
