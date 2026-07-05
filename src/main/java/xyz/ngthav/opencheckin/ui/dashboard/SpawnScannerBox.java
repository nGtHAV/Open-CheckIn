package xyz.ngthav.opencheckin.ui.dashboard;

import javafx.collections.FXCollections;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Screen;
import javafx.stage.Window;
import javafx.util.StringConverter;
import xyz.ngthav.opencheckin.app.AppState;
import xyz.ngthav.opencheckin.camera.CameraService;
import xyz.ngthav.opencheckin.ui.scanner.ScannerWindow;

import java.util.ArrayList;
import java.util.List;

/**
 * Dashboard box to spawn the scanner window: a camera-source dropdown, a
 * target (a monitor <i>other</i> than the main window's, or a floating window), and an "Open
 * scanner" button.
 */
final class SpawnScannerBox {

    /** A scanner target: a specific monitor, or {@code null} screen for a floating window. */
    private record Target(String label, Screen screen) {
        @Override
        public String toString() {
            return label;
        }
    }

    private SpawnScannerBox() {
    }

    static Region node(AppState app) {
        Label title = new Label("Scanner window");
        title.getStyleClass().add("card-title");

        ComboBox<String> cameraSource = new ComboBox<>();
        cameraSource.setPromptText("Default camera");
        cameraSource.setMaxWidth(Double.MAX_VALUE);
        // Listing does not open the device, so a throwaway service is safe here.
        cameraSource.getItems().setAll(new CameraService().list());
        cameraSource.setOnShowing(e -> {
            var devices = new CameraService().list();
            if (!cameraSource.getItems().equals(devices)) {
                cameraSource.getItems().setAll(devices);
            }
        });

        ComboBox<Target> target = new ComboBox<>();
        target.setMaxWidth(Double.MAX_VALUE);
        target.setConverter(new StringConverter<>() {
            @Override
            public String toString(Target t) {
                return t == null ? "" : t.label();
            }

            @Override
            public Target fromString(String s) {
                return null;
            }
        });
        target.getItems().setAll(new Target("Floating window", null));
        target.getSelectionModel().selectFirst();
        // Enumerate monitors lazily — exclude the one the main window sits on.
        target.setOnShowing(e -> refreshTargets(target, cameraSource.getScene() == null ? null
                : cameraSource.getScene().getWindow()));

        Button open = new Button("Open scanner");
        open.getStyleClass().add("primary-button");
        open.setMaxWidth(Double.MAX_VALUE);
        open.setOnAction(e -> {
            Target chosen = target.getValue();
            Screen screen = chosen == null ? null : chosen.screen();
            new ScannerWindow(app, screen, cameraSource.getValue());
        });

        VBox box = new VBox(10, title,
                new Label("Camera source"), cameraSource,
                new Label("Target"), target,
                open);
        box.getStyleClass().add("card");
        return box;
    }

    private static void refreshTargets(ComboBox<Target> target, Window mainWindow) {
        List<Target> options = new ArrayList<>();
        options.add(new Target("Floating window", null));
        List<Screen> exclude = mainWindow == null ? List.of()
                : Screen.getScreensForRectangle(mainWindow.getX(), mainWindow.getY(),
                mainWindow.getWidth(), mainWindow.getHeight());
        var screens = Screen.getScreens();
        for (int i = 0; i < screens.size(); i++) {
            Screen s = screens.get(i);
            if (exclude.contains(s)) {
                continue; // don't offer the monitor the main window is already on
            }
            var b = s.getBounds();
            options.add(new Target(
                    "Monitor " + (i + 1) + "  (" + (int) b.getWidth() + "×" + (int) b.getHeight() + ")", s));
        }
        Target selected = target.getValue();
        target.setItems(FXCollections.observableArrayList(options));
        if (selected != null && options.stream().anyMatch(o -> o.label().equals(selected.label()))) {
            target.getSelectionModel().select(
                    options.stream().filter(o -> o.label().equals(selected.label())).findFirst().orElse(options.get(0)));
        } else {
            target.getSelectionModel().selectFirst();
        }
    }
}
