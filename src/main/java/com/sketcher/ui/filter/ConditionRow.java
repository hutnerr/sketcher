package com.sketcher.ui.filter;

import com.sketcher.filter.Condition;
import com.sketcher.filter.Condition.Field;
import com.sketcher.filter.Condition.Operator;
import com.sketcher.filter.Condition.ValueKind;
import com.sketcher.filter.Filter;
import com.sketcher.library.LibraryService;
import com.sketcher.ui.components.AppButton;
import com.sketcher.ui.components.NumberField;
import com.sketcher.ui.tags.TagPicker;
import javafx.geometry.Pos;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.util.StringConverter;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;

/**
 * One "field · operator · value" row of the condition builder, with its
 * and / or / remove buttons. The value editor is rebuilt only when the
 * operator's {@link ValueKind} changes, so switching "at least" to "at most"
 * keeps the number you typed.
 */
class ConditionRow extends HBox {
    interface Actions {
        void and(ConditionRow row);

        void or(ConditionRow row);

        void remove(ConditionRow row);
    }

    private final LibraryService library;
    private final Runnable onChange;
    private final ChoiceBox<Field> fieldChoice = choiceBox(f -> f.label);
    private final ChoiceBox<Operator> operatorChoice = choiceBox(o -> o.label);
    private final HBox valueBox = new HBox(8);
    private ValueKind currentKind;

    // Whichever of these the current ValueKind uses; the rest are null.
    private Integer tagId;
    private AppButton tagButton;
    private CheckBox subTags;
    private TextField textInput;
    private ChoiceBox<String> choice;
    private DatePicker fromPicker;
    private DatePicker toPicker;

    /** Set while load() fills the editors, so one load fires one change instead of several half-built ones. */
    private boolean loading;

    ConditionRow(LibraryService library, Runnable onChange, Actions actions) {
        this.library = library;
        this.onChange = onChange;
        getStyleClass().add("condition-row");
        setSpacing(10);
        setAlignment(Pos.CENTER_LEFT);

        valueBox.setAlignment(Pos.CENTER_LEFT);

        fieldChoice.getItems().setAll(Field.values());
        fieldChoice.setValue(Field.TAG);
        operatorChoice.getItems().setAll(Field.TAG.operators);
        operatorChoice.setValue(Field.TAG.operators.get(0));
        fieldChoice.valueProperty().addListener((o, was, field) -> {
            if (field == null) return;
            operatorChoice.getItems().setAll(field.operators);
            operatorChoice.setValue(field.operators.get(0));
            // A new field means a new kind of value even when the editor type matches (a tag isn't a filename).
            rebuildValueEditor(true);
            fire();
        });
        operatorChoice.valueProperty().addListener((o, was, op) -> {
            if (op == null) return;
            rebuildValueEditor(false);
            fire();
        });
        rebuildValueEditor(true);

        // Field, operator, and value wrap onto a second line in narrow spots (the Practice form's
        // right-hand column) instead of forcing the page into a horizontal scroll.
        FlowPane controls = new FlowPane(10, 8, fieldChoice, operatorChoice, valueBox);
        controls.setAlignment(Pos.CENTER_LEFT);
        controls.setPrefWrapLength(900);
        controls.setMinWidth(0);
        HBox.setHgrow(controls, Priority.ALWAYS);

        getChildren().addAll(controls,
                AppButton.ghost("and", () -> actions.and(this)).small(),
                AppButton.ghost("or", () -> actions.or(this)).small(),
                AppButton.icon("✕", "Remove this condition", () -> actions.remove(this)));
    }

    void focus() {
        fieldChoice.requestFocus();
    }

    Optional<Filter> buildFilter() {
        return Condition.build(fieldChoice.getValue(), operatorChoice.getValue(), value());
    }

    void load(Condition.Spec spec) {
        loading = true;
        try {
            fieldChoice.setValue(spec.field());
            operatorChoice.setValue(spec.operator());
            rebuildValueEditor(true);
            Condition.Value v = spec.value();
            if (v.tagId() != null) setTag(v.tagId());
            if (subTags != null) subTags.setSelected(v.includeSubTags());
            if (textInput != null) textInput.setText(v.text());
            if (choice != null && v.text() != null && !v.text().isEmpty()) choice.setValue(v.text());
            if (fromPicker != null) fromPicker.setValue(v.from());
            if (toPicker != null) toPicker.setValue(v.to());
        } finally {
            loading = false;
        }
    }

    private Condition.Value value() {
        String text = textInput != null ? textInput.getText() : choice != null ? choice.getValue() : "";
        return new Condition.Value(tagId, subTags == null || subTags.isSelected(), text,
                fromPicker == null ? null : fromPicker.getValue(), toPicker == null ? null : toPicker.getValue());
    }

    private void fire() {
        if (!loading) onChange.run();
    }

    private void rebuildValueEditor(boolean force) {
        Field field = fieldChoice.getValue();
        ValueKind kind = field.valueKind(operatorChoice.getValue());
        if (!force && kind == currentKind) {
            updateUnit();
            return;
        }
        currentKind = kind;
        tagId = null;
        tagButton = null;
        subTags = null;
        textInput = null;
        choice = null;
        fromPicker = null;
        toPicker = null;
        valueBox.getChildren().clear();

        switch (kind) {
            case NONE -> {
            }
            case TAG -> {
                tagButton = AppButton.secondary("Choose tag…", this::pickTag).small();
                subTags = new CheckBox("incl. sub-tags");
                subTags.setSelected(true);
                subTags.setMinWidth(USE_PREF_SIZE);
                subTags.setOnAction(e -> fire());
                valueBox.getChildren().addAll(tagButton, subTags);
            }
            case TEXT -> {
                textInput = new TextField();
                textInput.setPromptText("text");
                textInput.setPrefWidth(200);
                textInput.textProperty().addListener((o, a, b) -> fire());
                valueBox.getChildren().add(textInput);
            }
            case INTEGER, DAYS -> {
                textInput = new NumberField(kind == ValueKind.DAYS ? 30 : 0);
                if (kind == ValueKind.INTEGER) textInput.setText("");
                textInput.textProperty().addListener((o, a, b) -> fire());
                valueBox.getChildren().addAll(textInput, unitLabel());
            }
            case DATE, DATE_RANGE -> {
                fromPicker = datePicker();
                valueBox.getChildren().add(fromPicker);
                if (kind == ValueKind.DATE_RANGE) {
                    toPicker = datePicker();
                    valueBox.getChildren().addAll(muted("and"), toPicker);
                }
            }
            case FORMAT -> {
                choice = choiceBox(s -> s.toUpperCase(Locale.ROOT));
                choice.getItems().setAll(Condition.FORMATS);
                choice.setValue(Condition.FORMATS.get(0));
                choice.valueProperty().addListener((o, a, b) -> fire());
                valueBox.getChildren().add(choice);
            }
            case ORIENTATION -> {
                choice = choiceBox(Condition::orientationLabel);
                choice.getItems().setAll(List.of(Filter.Orientation.values()).stream().map(Enum::name).toList());
                choice.setValue(Filter.Orientation.PORTRAIT.name());
                choice.valueProperty().addListener((o, a, b) -> fire());
                valueBox.getChildren().add(choice);
            }
        }
    }

    private Label unitLabel() {
        Label unit = muted(fieldChoice.getValue().unit(operatorChoice.getValue()));
        unit.getStyleClass().add("condition-unit");
        return unit;
    }

    private void updateUnit() {
        String unit = fieldChoice.getValue().unit(operatorChoice.getValue());
        valueBox.getChildren().stream()
                .filter(n -> n.getStyleClass().contains("condition-unit"))
                .forEach(n -> ((Label) n).setText(unit));
    }

    private void pickTag() {
        TagPicker.pickOne(library).ifPresent(tag -> {
            setTag(tag.id());
            fire();
        });
    }

    private void setTag(int id) {
        tagId = id;
        if (tagButton != null) tagButton.setText(library.tags().fullPath(id));
    }

    private DatePicker datePicker() {
        DatePicker picker = new DatePicker();
        picker.setPrefWidth(150);
        picker.setPromptText("date");
        picker.valueProperty().addListener((o, a, b) -> fire());
        return picker;
    }

    private static Label muted(String text) {
        Label label = new Label(text == null ? "" : text);
        label.getStyleClass().add("muted-label");
        label.setMinWidth(USE_PREF_SIZE);
        return label;
    }

    private static <T> ChoiceBox<T> choiceBox(Function<T, String> label) {
        ChoiceBox<T> box = new ChoiceBox<>();
        box.setMinWidth(USE_PREF_SIZE);
        box.setConverter(new StringConverter<>() {
            @Override
            public String toString(T value) {
                return value == null ? "" : label.apply(value);
            }

            @Override
            public T fromString(String s) {
                return null;
            }
        });
        return box;
    }
}
