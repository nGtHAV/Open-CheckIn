package xyz.ngthav.opencheckin.ui.dashboard;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import xyz.ngthav.opencheckin.app.AppState;
import xyz.ngthav.opencheckin.checkin.Action;
import xyz.ngthav.opencheckin.checkin.CheckInService;
import xyz.ngthav.opencheckin.model.Attendance;
import xyz.ngthav.opencheckin.model.Member;
import xyz.ngthav.opencheckin.model.Room;
import xyz.ngthav.opencheckin.ui.MemberCard;
import xyz.ngthav.opencheckin.ui.Page;
import xyz.ngthav.opencheckin.util.Dates;

/**
 * The Dashboard page (manual and automatic modes). Renders the layout for the active
 * room's mode and stays live by observing the shared {@link CheckInService}: the counter and
 * recent list update on each recorded scan, and the manual confirmation panel shows pending scans
 * with Accept/Reject. The single active listener is swapped on every rebuild (no accumulation).
 */
public final class DashboardView extends Page {

    private static final int RECENT_LIMIT = 12;

    // Rebuilt each render; the active listener closes over whichever of these the mode uses.
    private CheckInService.Listener activeListener;
    private Label counterValue;
    private VBox recentBox;
    private StackPane confirmationHolder;
    private Button acceptButton;
    private Button rejectButton;

    public DashboardView(AppState app, Runnable onCreateRoom) {
        super(app, onCreateRoom);
    }

    @Override
    public String title() {
        return "Dashboard";
    }

    @Override
    protected Node buildContent() {
        // Drop the previous render's subscription before building a fresh one.
        if (activeListener != null) {
            app.checkIn().removeListener(activeListener);
            activeListener = null;
        }
        Room room = app.currentRoom();
        return room.manualConfirmation() ? buildManual(room) : buildAuto(room);
    }

    // ---------------- Automatic ----------------

    private Node buildAuto(Room room) {
        VBox left = new VBox(20, counterCard(), recentCard(), SpawnScannerBox.node(app));
        left.setMinWidth(380);
        left.setPrefWidth(380);

        Region studio = new CardStudio(app).getView();
        HBox.setHgrow(studio, Priority.ALWAYS);

        activeListener = new CheckInService.Listener() {
            @Override
            public void onRecorded(Member member, Action action) {
                Platform.runLater(() -> {
                    refreshCounter();
                    refreshRecent();
                });
            }
        };
        app.checkIn().addListener(activeListener);

        return twoColumns(left, studio);
    }

    // ---------------- Manual ----------------

    private Node buildManual(Room room) {
        VBox left = new VBox(20, counterCard(), new CardStudio(app).getView(), SpawnScannerBox.node(app));
        left.setMinWidth(400);
        left.setPrefWidth(400);

        Region confirmation = confirmationPanel();
        HBox.setHgrow(confirmation, Priority.ALWAYS);

        activeListener = new CheckInService.Listener() {
            @Override
            public void onPending(Member member, Action action) {
                Platform.runLater(() -> showPending(member, action));
            }

            @Override
            public void onRecorded(Member member, Action action) {
                Platform.runLater(DashboardView.this::refreshCounter);
            }

            @Override
            public void onCleared() {
                Platform.runLater(DashboardView.this::showIdleConfirmation);
            }
        };
        app.checkIn().addListener(activeListener);

        // Reflect any scan already pending when the page (re)opens.
        app.checkIn().pending().ifPresentOrElse(
                p -> showPending(p.member(), p.action()),
                this::showIdleConfirmation);

        return twoColumns(left, confirmation);
    }

    // ---------------- shared pieces ----------------

    private Node twoColumns(Region left, Region right) {
        HBox row = new HBox(24, left, right);
        row.setFillHeight(true);
        VBox.setVgrow(row, Priority.ALWAYS);

        Label pageTitle = new Label("Dashboard");
        pageTitle.getStyleClass().add("page-title");

        VBox page = new VBox(20, pageTitle, row);
        VBox.setVgrow(row, Priority.ALWAYS);
        return page;
    }

    private Region counterCard() {
        Label title = new Label("Total check-in today");
        title.getStyleClass().add("card-title");
        counterValue = new Label();
        counterValue.getStyleClass().add("counter-value");
        refreshCounter();
        VBox box = new VBox(8, title, counterValue);
        box.getStyleClass().add("card");
        return box;
    }

    private Region recentCard() {
        Label title = new Label("Recently checked in");
        title.getStyleClass().add("card-title");
        recentBox = new VBox(6);
        refreshRecent();
        VBox box = new VBox(12, title, recentBox);
        box.getStyleClass().add("card");
        return box;
    }

    private Region confirmationPanel() {
        Label title = new Label("Confirm scan");
        title.getStyleClass().add("card-title");

        confirmationHolder = new StackPane();
        confirmationHolder.setMinHeight(240);
        VBox.setVgrow(confirmationHolder, Priority.ALWAYS);

        rejectButton = new Button("Reject");
        rejectButton.getStyleClass().add("danger-button");
        rejectButton.setOnAction(e -> app.checkIn().rejectPending());

        acceptButton = new Button("Accept");
        acceptButton.getStyleClass().add("primary-button");
        acceptButton.setOnAction(e -> app.checkIn().acceptPending());

        HBox buttons = new HBox(12, rejectButton, acceptButton);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        VBox box = new VBox(16, title, confirmationHolder, buttons);
        box.getStyleClass().add("card");
        HBox.setHgrow(box, Priority.ALWAYS);
        return box;
    }

    // ---------------- live updates ----------------

    private void refreshCounter() {
        if (counterValue != null) {
            counterValue.setText(app.checkedInToday() + " / " + app.totalMembers());
        }
    }

    private void refreshRecent() {
        if (recentBox == null) {
            return;
        }
        recentBox.getChildren().clear();
        var recent = app.recentToday(RECENT_LIMIT);
        if (recent.isEmpty()) {
            Label empty = new Label("No check-ins yet today.");
            empty.getStyleClass().add("empty-subtitle");
            recentBox.getChildren().add(empty);
            return;
        }
        for (Attendance a : recent) {
            String name = app.members().findById(a.memberId()).map(Member::name).orElse("Unknown");
            Label nameLabel = new Label(name);
            nameLabel.getStyleClass().add("recent-name");
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            Label time = new Label(Dates.formatTime(a.createdAt()));
            time.getStyleClass().add("recent-time");
            HBox row = new HBox(nameLabel, spacer, time);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("recent-row");
            recentBox.getChildren().add(row);
        }
    }

    private void showPending(Member member, Action action) {
        if (confirmationHolder == null) {
            return;
        }
        String label = action == Action.CHECK_OUT ? "Checking out" : "Checking in";
        confirmationHolder.getChildren().setAll(MemberCard.node(member, app.currentRoom(), label));
        acceptButton.setDisable(false);
        rejectButton.setDisable(false);
    }

    private void showIdleConfirmation() {
        if (confirmationHolder == null) {
            return;
        }
        confirmationHolder.getChildren().setAll(MemberCard.waiting("Waiting for the next scan…"));
        acceptButton.setDisable(true);
        rejectButton.setDisable(true);
    }
}
