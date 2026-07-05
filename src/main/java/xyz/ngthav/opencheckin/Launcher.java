package xyz.ngthav.opencheckin;

import javafx.application.Application;

/**
 * Non-{@link Application} entry point. A packaged / {@code java -jar} build must launch
 * through a class that is NOT an {@code Application} subclass, otherwise the JVM reports
 * "JavaFX runtime components are missing". Keep this as the main class.
 */
public class Launcher {
    public static void main(String[] args) {
        Application.launch(OpenCheckInApp.class, args);
    }
}
