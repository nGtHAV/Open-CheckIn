package xyz.ngthav.opencheckin;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import xyz.ngthav.opencheckin.app.AppShell;
import xyz.ngthav.opencheckin.app.AppState;
import xyz.ngthav.opencheckin.db.Database;
import xyz.ngthav.opencheckin.util.Files;

import java.util.Objects;

/**
 * The JavaFX {@link Application} for Open-CheckIn (replaces the scaffold's {@code HelloApplication}).
 * Launched via {@link Launcher} for packaged builds. Opens the SQLite database
 * (creating {@code data/} + schema on first run), builds the shell, and closes the database on exit.
 */
public final class OpenCheckInApp extends Application {

    private Database database;

    @Override
    public void start(Stage stage) {
        database = Database.open(Files.dbPath());
        AppState app = new AppState(database);

        AppShell shell = new AppShell(app);
        Scene scene = new Scene(shell.getRoot(), 1180, 760);
        scene.getStylesheets().add(
                Objects.requireNonNull(getClass().getResource("/xyz/ngthav/opencheckin/css/app.css"),
                        "app.css not found on classpath").toExternalForm());

        stage.setTitle("Open-CheckIn");
        stage.setScene(scene);
        stage.setMinWidth(960);
        stage.setMinHeight(640);
        stage.show();
    }

    @Override
    public void stop() {
        if (database != null) {
            database.close();
        }
    }
}
