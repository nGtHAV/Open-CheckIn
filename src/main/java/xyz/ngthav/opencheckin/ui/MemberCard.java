package xyz.ngthav.opencheckin.ui;

import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import xyz.ngthav.opencheckin.model.Member;
import xyz.ngthav.opencheckin.model.Room;
import xyz.ngthav.opencheckin.util.Files;

/**
 * A person card (photo, name, description) used by the scanner's left column and the manual
 * dashboard confirmation panel. Not the QR card — that's {@code CardRenderer}.
 */
public final class MemberCard {

    private static final int PHOTO_HEIGHT = 480;
    private static final int PHOTO_WIDTH = 360;

    private MemberCard() {
    }

    /** @param actionLabel e.g. "Checking in" / "Checking out"; null/blank to omit. */
    public static Region node(Member member, Room room, String actionLabel) {
        Label name = new Label(member.name());
        name.getStyleClass().add("member-card-name");

        Label description = new Label(member.description() == null ? "" : member.description());
        description.getStyleClass().add("member-card-desc");
        description.setWrapText(true);

        VBox box = new VBox(12, photo(member, room), name, description);
        box.setAlignment(Pos.TOP_CENTER);
        box.getStyleClass().add("member-card");

        if (actionLabel != null && !actionLabel.isBlank()) {
            Label action = new Label(actionLabel);
            action.getStyleClass().add("member-card-action");
            box.getChildren().add(1, action);
        }
        return box;
    }

    /** The idle placeholder before the first scan. */
    public static Region waiting(String message) {
        Label label = new Label(message);
        label.getStyleClass().add("member-card-waiting");
        label.setWrapText(true);
        VBox box = new VBox(label);
        box.setAlignment(Pos.CENTER);
        box.getStyleClass().add("member-card");
        return box;
    }

    private static Region photo(Member member, Room room) {
        long roomId = room == null ? member.roomId() : room.id();
        if (member.hasPicture()) {
            try {
                Image img = new Image(
                        Files.picturePath(roomId, member.pictureName()).toUri().toString(),
                        PHOTO_WIDTH, PHOTO_HEIGHT, false, true);
                if (!img.isError()) {
                    ImageView view = new ImageView(img);
                    view.setFitWidth(PHOTO_WIDTH);
                    view.setFitHeight(PHOTO_HEIGHT);
                    Rectangle clip = new Rectangle(PHOTO_WIDTH, PHOTO_HEIGHT);
                    clip.setArcWidth(24);
                    clip.setArcHeight(24);
                    view.setClip(clip);
                    return new StackPane(view);
                }
            } catch (RuntimeException ignored) {
                // fall through to initials
            }
        }
        Label initials = new Label(initialsOf(member.name()));
        initials.getStyleClass().add("member-card-initials");
        StackPane tile = new StackPane(initials);
        tile.getStyleClass().add("member-card-photo-placeholder");
        tile.setPrefSize(PHOTO_WIDTH, PHOTO_HEIGHT);
        tile.setMinSize(PHOTO_WIDTH, PHOTO_HEIGHT);
        tile.setMaxSize(PHOTO_WIDTH, PHOTO_HEIGHT);
        return tile;
    }

    private static String initialsOf(String name) {
        if (name == null || name.isBlank()) {
            return "?";
        }
        String[] parts = name.trim().split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length && sb.length() < 2; i++) {
            if (!parts[i].isEmpty()) {
                sb.append(Character.toUpperCase(parts[i].charAt(0)));
            }
        }
        return sb.toString();
    }
}
