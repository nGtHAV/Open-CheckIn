package xyz.ngthav.opencheckin.ui.scanner;

import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Screen;
import javafx.stage.Stage;
import xyz.ngthav.opencheckin.app.AppState;
import xyz.ngthav.opencheckin.camera.CameraFrameListener;
import xyz.ngthav.opencheckin.camera.CameraService;
import xyz.ngthav.opencheckin.checkin.Action;
import xyz.ngthav.opencheckin.checkin.CheckInService;
import xyz.ngthav.opencheckin.model.Member;
import xyz.ngthav.opencheckin.qr.QrDecoder;
import xyz.ngthav.opencheckin.ui.MemberCard;
import xyz.ngthav.opencheckin.ui.Toasts;
import xyz.ngthav.opencheckin.util.Images;

/**
 * The scanner child window: two columns — the member card (left) and the live camera
 * with a QR alignment helper (right). Owns the one camera; closing the window releases it.
 * Scans flow through the shared {@link CheckInService}, so the dashboard reacts to what the
 * scanner sees. A simulate-scan seam feeds raw payloads with no webcam.
 */
public final class ScannerWindow {

    private final AppState app;
    private final CheckInService checkIn;
    private final CameraService camera = new CameraService();
    private final Stage stage = new Stage();

    private final StackPane leftHolder = new StackPane();
    private final StackPane cameraArea = new StackPane();
    private final ImageView cameraView = new ImageView();
    private final AlignmentOverlay overlay = new AlignmentOverlay();
    private final ComboBox<String> sourceSelector = new ComboBox<>();

    private CheckInService.Listener checkInListener;
    /** Guards against the source ComboBox's onAction re-entering startCamera when we set it in code. */
    private boolean suppressSourceEvent;

    public ScannerWindow(AppState app, Screen targetMonitor, String initialCamera) {
        this.app = app;
        this.checkIn = app.checkIn();
        buildUi();
        wireCamera();
        wireCheckInListener();
        positionAndShow(targetMonitor);
        startCamera(initialCamera);
    }

    private void buildUi() {
        showWaiting();
        leftHolder.getStyleClass().add("scanner-left");
        leftHolder.setPrefWidth(360);
        leftHolder.setMinWidth(320);

        cameraView.setPreserveRatio(true);
        cameraView.fitWidthProperty().bind(cameraArea.widthProperty());
        cameraView.fitHeightProperty().bind(cameraArea.heightProperty());
        cameraArea.getStyleClass().add("scanner-camera");
        cameraArea.getChildren().setAll(cameraView, overlay);

        sourceSelector.setPromptText("Camera source");
        sourceSelector.setOnAction(e -> {
            if (suppressSourceEvent) {
                return; // programmatic selection, not a user pick
            }
            String choice = sourceSelector.getValue();
            if (choice != null) {
                startCamera(choice);
            }
        });

        // Dev seam: paste a {"roomId":..,"memberUUID":".."} payload and feed it straight in.
        TextField simulateField = new TextField();
        simulateField.setPromptText("Simulate scan: paste QR payload…");
        HBox.setHgrow(simulateField, Priority.ALWAYS);
        Button simulateButton = new Button("Simulate");
        Runnable simulate = () -> {
            String payload = simulateField.getText();
            if (payload != null && !payload.isBlank()) {
                checkIn.onDecoded(payload.trim());
                simulateField.clear();
            }
        };
        simulateButton.setOnAction(e -> simulate.run());
        simulateField.setOnAction(e -> simulate.run());
        HBox simulateRow = new HBox(8, simulateField, simulateButton);
        simulateRow.setAlignment(Pos.CENTER_LEFT);
        simulateRow.getStyleClass().add("scanner-devseam");

        HBox controls = new HBox(12, new Label("Camera"), sourceSelector);
        controls.setAlignment(Pos.CENTER_LEFT);

        VBox rightColumn = new VBox(12, cameraArea, controls, simulateRow);
        rightColumn.getStyleClass().add("scanner-right");
        VBox.setVgrow(cameraArea, Priority.ALWAYS);
        HBox.setHgrow(rightColumn, Priority.ALWAYS);

        HBox root = new HBox(leftHolder, rightColumn);
        root.getStyleClass().add("scanner-root");

        Scene scene = new Scene(root, 960, 600);
        var css = getClass().getResource("/xyz/ngthav/opencheckin/css/app.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
        stage.setScene(scene);
        stage.setTitle("Open-CheckIn — Scanner");
    }

    private void wireCamera() {
        // Preview: convert on the capture thread, push the FX Image on the FX thread.
        CameraFrameListener preview = frame -> {
            var image = Images.toFxImage(frame);
            Platform.runLater(() -> cameraView.setImage(image));
        };
        // Decode: purely off the FX thread; a hit forwards the payload (onDecoded re-hops to FX).
        CameraFrameListener decode = frame -> QrDecoder.decode(frame).ifPresent(checkIn::onDecoded);
        camera.addListener(preview);
        camera.addListener(decode);
    }

    private void wireCheckInListener() {
        checkInListener = new CheckInService.Listener() {
            @Override
            public void onRecorded(Member member, Action action) {
                Platform.runLater(() -> showMember(member, label(action)));
            }

            @Override
            public void onPending(Member member, Action action) {
                Platform.runLater(() -> showMember(member, label(action)));
            }

            @Override
            public void onCleared() {
                Platform.runLater(ScannerWindow.this::showWaiting);
            }

            @Override
            public void onFlash(String message, CheckInService.Flash kind) {
                Platform.runLater(() -> Toasts.flash(message, kind, stage));
            }
        };
        checkIn.addListener(checkInListener);
    }

    private void positionAndShow(Screen targetMonitor) {
        if (targetMonitor != null) {
            Rectangle2D bounds = targetMonitor.getBounds();
            stage.setX(bounds.getMinX());
            stage.setY(bounds.getMinY());
            stage.setWidth(bounds.getWidth());
            stage.setHeight(bounds.getHeight());
            stage.setMaximized(true); // fullscreen-feel on the chosen monitor
        }
        stage.setOnCloseRequest(e -> dispose());
        stage.show();
    }

    private void startCamera(String deviceName) {
        // Repopulate the source list (cheap, and reflects hot-plugged devices). Suppress the
        // ComboBox's onAction while we set items/value in code, or it re-enters startCamera and
        // opens the device twice (open/close/open) on launch.
        var devices = camera.list();
        suppressSourceEvent = true;
        try {
            if (!sourceSelector.getItems().equals(devices)) {
                sourceSelector.getItems().setAll(devices);
            }
            if (deviceName != null && devices.contains(deviceName)) {
                sourceSelector.setValue(deviceName);
            }
        } finally {
            suppressSourceEvent = false;
        }
        boolean started = camera.start(deviceName);
        if (started) {
            cameraArea.getChildren().setAll(cameraView, overlay);
        } else {
            showNoCamera();
        }
    }

    private void showNoCamera() {
        Label label = new Label(camera.list().isEmpty()
                ? "No camera detected.\nUse the simulate field below to test scans."
                : "Could not open the camera (busy or unavailable).");
        label.getStyleClass().add("scanner-nocamera");
        label.setWrapText(true);
        label.setAlignment(Pos.CENTER);
        cameraArea.getChildren().setAll(label);
    }

    private void showMember(Member member, String actionLabel) {
        leftHolder.getChildren().setAll(MemberCard.node(member, app.currentRoom(), actionLabel));
    }

    private void showWaiting() {
        leftHolder.getChildren().setAll(MemberCard.waiting("Waiting for a scan…"));
    }

    /** Releases the camera and unsubscribes. */
    private void dispose() {
        camera.stop();
        if (checkInListener != null) {
            checkIn.removeListener(checkInListener);
            checkInListener = null;
        }
    }

    private static String label(Action action) {
        return switch (action) {
            case CHECK_IN -> "Checking in";
            case CHECK_OUT -> "Checking out";
            case IGNORE -> "";
        };
    }
}
