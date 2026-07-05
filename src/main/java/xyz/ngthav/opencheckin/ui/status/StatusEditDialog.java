package xyz.ngthav.opencheckin.ui.status;

import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.stage.Window;
import xyz.ngthav.opencheckin.model.Member;
import xyz.ngthav.opencheckin.util.Dates;

import java.util.Optional;

/**
 * Edits a member's check-in / check-out <b>times</b> for the selected date. The
 * check-out editor only appears when the room has checkout enabled, and check-out must be ≥
 * check-in. Returns the entered {@code "HH:mm"} strings (blank → null); the caller applies the
 * row-count rules.
 */
public final class StatusEditDialog {

    public record Result(String checkIn, String checkOut) {
    }

    private StatusEditDialog() {
    }

    public static Optional<Result> show(Window owner, Member member, boolean checkoutEnabled,
                                        String currentCheckIn, String currentCheckOut) {
        Dialog<Result> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle("Edit times");
        dialog.setHeaderText("Edit times for " + member.name());

        ButtonType saveType = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);

        TextField checkInField = new TextField(currentCheckIn == null ? "" : currentCheckIn);
        checkInField.setPromptText("HH:mm");

        TextField checkOutField = new TextField(currentCheckOut == null ? "" : currentCheckOut);
        checkOutField.setPromptText("HH:mm");

        Label error = new Label();
        error.getStyleClass().add("form-error");
        error.setVisible(false);

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        grid.setPadding(new Insets(8, 4, 4, 4));
        grid.addRow(0, new Label("Check-in"), checkInField);
        if (checkoutEnabled) {
            grid.addRow(1, new Label("Check-out"), checkOutField);
        }
        grid.add(error, 1, 2);
        dialog.getDialogPane().setContent(grid);

        dialog.getDialogPane().lookupButton(saveType).addEventFilter(ActionEvent.ACTION, evt -> {
            // Validate against the EFFECTIVE check-out (a blank field keeps any existing check-out),
            // so a check-in can never be pushed past the day's check-out.
            String effectiveOut = effectiveCheckOut(checkoutEnabled, checkOutField.getText(), currentCheckOut);
            String problem = validate(checkInField.getText(), checkoutEnabled, effectiveOut);
            if (problem != null) {
                error.setText(problem);
                error.setVisible(true);
                evt.consume();
            }
        });

        dialog.setResultConverter(button -> {
            if (button != saveType) {
                return null;
            }
            String checkIn = blankToNull(checkInField.getText());
            String checkOut = effectiveCheckOut(checkoutEnabled, checkOutField.getText(), currentCheckOut);
            return new Result(checkIn, checkOut);
        });

        return dialog.showAndWait();
    }

    /** Returns an error message, or {@code null} when valid. */
    static String validate(String checkIn, boolean checkoutEnabled, String checkOut) {
        boolean hasIn = checkIn != null && !checkIn.isBlank();
        boolean hasOut = checkOut != null && !checkOut.isBlank();
        if (hasIn && !Dates.isValidHmm(checkIn)) {
            return "Check-in must be HH:mm.";
        }
        if (checkoutEnabled && hasOut && !Dates.isValidHmm(checkOut)) {
            return "Check-out must be HH:mm.";
        }
        if (hasOut && !hasIn) {
            return "Set a check-in time before a check-out time.";
        }
        if (hasIn && hasOut && checkOut.trim().compareTo(checkIn.trim()) < 0) {
            // "HH:mm" text compares chronologically, so a lexical compare is a valid time compare.
            return "Check-out must be at or after check-in.";
        }
        return null;
    }

    /**
     * The check-out to actually use: the typed value, or — when the field is left blank — the
     * existing check-out. This keeps an already-recorded check-out from being silently dropped,
     * so the ≥ guard always has something to compare against.
     */
    static String effectiveCheckOut(boolean checkoutEnabled, String field, String currentCheckOut) {
        if (!checkoutEnabled) {
            return null;
        }
        String typed = blankToNull(field);
        return typed != null ? typed : currentCheckOut;
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
