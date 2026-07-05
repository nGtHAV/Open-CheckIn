package xyz.ngthav.opencheckin.ui;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/**
 * A calm placeholder for pages that land in later milestones. Keeps the shell navigable while
 * those pages are built.
 */
public final class Placeholder {

    private Placeholder() {
    }

    public static Node node(String title, String note) {
        Label heading = new Label(title);
        heading.getStyleClass().add("page-title");
        Label body = new Label(note);
        body.getStyleClass().add("empty-subtitle");
        body.setWrapText(true);

        VBox box = new VBox(10, heading, body);
        box.setAlignment(Pos.CENTER);
        box.getStyleClass().add("empty-state");
        return box;
    }
}
