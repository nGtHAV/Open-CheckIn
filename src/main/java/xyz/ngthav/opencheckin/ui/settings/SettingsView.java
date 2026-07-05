package xyz.ngthav.opencheckin.ui.settings;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import xyz.ngthav.opencheckin.app.AppState;
import xyz.ngthav.opencheckin.checkin.CheckInService;
import xyz.ngthav.opencheckin.model.Room;
import xyz.ngthav.opencheckin.ui.Page;
import xyz.ngthav.opencheckin.ui.Toasts;
import xyz.ngthav.opencheckin.util.Dates;

/**
 * The Settings page: per-room check-in time, checkout enable + time (greyed out when
 * disabled, stored NULL), and manual-confirmation. Saving updates the room (refreshing
 * {@code updated_at}) and re-applies everywhere via the {@code currentRoom} listener — the
 * dashboard mode, Status columns and scan logic all pick up the change.
 */
public final class SettingsView extends Page {

    public SettingsView(AppState app, Runnable onCreateRoom) {
        super(app, onCreateRoom);
    }

    @Override
    public String title() {
        return "Settings";
    }

    @Override
    protected Node buildContent() {
        final Room room = app.currentRoom();

        Label pageTitle = new Label("Settings");
        pageTitle.getStyleClass().add("page-title");

        Label roomName = new Label(room.name());
        roomName.getStyleClass().add("card-title");

        TextField checkinField = new TextField(room.checkin() == null ? "" : room.checkin());
        checkinField.setPromptText("HH:mm");

        CheckBox checkoutEnabled = new CheckBox("Enable check-out");
        checkoutEnabled.setSelected(room.checkoutEnabled());

        TextField checkoutField = new TextField(room.checkout() == null ? "" : room.checkout());
        checkoutField.setPromptText("HH:mm");
        checkoutField.setDisable(!room.checkoutEnabled());
        checkoutEnabled.selectedProperty().addListener((obs, was, now) -> checkoutField.setDisable(!now));

        CheckBox manualConfirmation = new CheckBox("Require manual confirmation for each scan");
        manualConfirmation.setSelected(room.manualConfirmation());

        Label error = new Label();
        error.getStyleClass().add("form-error");
        error.setVisible(false);

        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        grid.addRow(0, new Label("Check-in time"), checkinField);
        grid.add(checkoutEnabled, 1, 1);
        grid.addRow(2, new Label("Check-out time"), checkoutField);
        grid.add(manualConfirmation, 1, 3);

        Button save = new Button("Save settings");
        save.getStyleClass().add("primary-button");
        save.setOnAction(e -> {
            String problem = validate(checkinField.getText(), checkoutEnabled.isSelected(), checkoutField.getText());
            if (problem != null) {
                error.setText(problem);
                error.setVisible(true);
                return;
            }
            error.setVisible(false);
            String checkin = blankToNull(checkinField.getText());
            String checkout = checkoutEnabled.isSelected() ? blankToNull(checkoutField.getText()) : null;
            // Name is not edited here; keep it. This refreshes updated_at and re-selects the room,
            // which re-renders the current page and updates the dashboard mode everywhere.
            app.updateCurrentRoom(room.name(), checkin, checkout, manualConfirmation.isSelected());
            Window owner = save.getScene() == null ? null : save.getScene().getWindow();
            Toasts.flash("Settings saved", CheckInService.Flash.SUCCESS, owner);
        });

        HBox actions = new HBox(save);
        actions.setAlignment(Pos.CENTER_LEFT);

        VBox card = new VBox(16, roomName, grid, error, actions);
        card.getStyleClass().add("card");
        card.setMaxWidth(520);

        return new VBox(20, pageTitle, card);
    }

    static String validate(String checkin, boolean checkoutEnabled, String checkout) {
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
