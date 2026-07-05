package xyz.ngthav.opencheckin.ui.room;

import javafx.geometry.Insets;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.stage.Window;
import xyz.ngthav.opencheckin.util.Dates;

import java.util.Optional;

/**
 * Modal dialog to create a room. Captures the same fields as Settings so a room is usable the
 * moment it is created: name, check-in time, optional checkout (time greyed out
 * unless enabled), and manual-confirmation. The checkout time is stored as {@code null} when
 * the checkbox is unticked — {@code null} doubles as the "checkout disabled" flag.
 */
public final class RoomDialog {

    /** The captured values; {@code checkout} is {@code null} when checkout is disabled. */
    public record Result(String name, String checkin, String checkout, boolean manualConfirmation) {
    }

    private RoomDialog() {
    }

    public static Optional<Result> show(Window owner) {
        Dialog<Result> dialog = new Dialog<>();
        dialog.initOwner(owner);
        dialog.setTitle("New room");
        dialog.setHeaderText("Create a room");

        ButtonType saveType = new ButtonType("Create", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);

        TextField nameField = new TextField();
        nameField.setPromptText("Room name");

        TextField checkinField = new TextField();
        checkinField.setPromptText("HH:mm  (e.g. 09:00)");

        CheckBox checkoutEnabled = new CheckBox("Enable check-out");
        TextField checkoutField = new TextField();
        checkoutField.setPromptText("HH:mm  (e.g. 17:00)");
        checkoutField.setDisable(true);
        checkoutEnabled.selectedProperty().addListener((obs, was, now) -> checkoutField.setDisable(!now));

        CheckBox manualConfirmation = new CheckBox("Require manual confirmation for each scan");

        Label error = new Label();
        error.getStyleClass().add("form-error");
        error.setVisible(false);

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        grid.setPadding(new Insets(8, 4, 4, 4));
        grid.addRow(0, new Label("Name"), nameField);
        grid.addRow(1, new Label("Check-in time"), checkinField);
        grid.add(checkoutEnabled, 1, 2);
        grid.addRow(3, new Label("Check-out time"), checkoutField);
        grid.add(manualConfirmation, 1, 4);
        grid.add(error, 1, 5);
        dialog.getDialogPane().setContent(grid);

        dialog.getDialogPane().lookupButton(saveType).addEventFilter(javafx.event.ActionEvent.ACTION, evt -> {
            String problem = validate(nameField.getText(), checkinField.getText(),
                    checkoutEnabled.isSelected(), checkoutField.getText());
            if (problem != null) {
                error.setText(problem);
                error.setVisible(true);
                evt.consume(); // keep the dialog open
            }
        });

        dialog.setResultConverter(button -> {
            if (button != saveType) {
                return null;
            }
            String checkin = blankToNull(checkinField.getText());
            String checkout = checkoutEnabled.isSelected() ? blankToNull(checkoutField.getText()) : null;
            return new Result(nameField.getText().trim(), checkin, checkout, manualConfirmation.isSelected());
        });

        return Optional.ofNullable(dialog.showAndWait().orElse(null));
    }

    /** Returns an error message, or {@code null} when the input is valid. */
    static String validate(String name, String checkin, boolean checkoutEnabled, String checkout) {
        if (name == null || name.isBlank()) {
            return "Name is required.";
        }
        if (checkin != null && !checkin.isBlank() && !Dates.isValidHmm(checkin)) {
            return "Check-in time must be HH:mm.";
        }
        if (checkoutEnabled && (checkout == null || checkout.isBlank() || !Dates.isValidHmm(checkout))) {
            return "Check-out time must be HH:mm when check-out is enabled.";
        }
        return null;
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }
}
