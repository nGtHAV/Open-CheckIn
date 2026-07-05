package xyz.ngthav.opencheckin.ui;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/**
 * The first-run empty state shown on every page when no room exists yet: a single centered
 * call-to-action that opens the "New room…" dialog.
 */
final class EmptyState {

    private EmptyState() {
    }

    static Node node(Runnable onCreateRoom) {
        Label heading = new Label("No room yet");
        heading.getStyleClass().add("empty-heading");

        Label subtitle = new Label("Create a room to get started.");
        subtitle.getStyleClass().add("empty-subtitle");

        Button create = new Button("＋ New room…");
        create.getStyleClass().add("primary-button");
        create.setOnAction(e -> onCreateRoom.run());

        VBox box = new VBox(12, heading, subtitle, create);
        box.setAlignment(Pos.CENTER);
        box.getStyleClass().add("empty-state");
        return box;
    }
}
