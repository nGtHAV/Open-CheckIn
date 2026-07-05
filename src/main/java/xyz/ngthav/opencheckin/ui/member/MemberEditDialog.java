package xyz.ngthav.opencheckin.ui.member;

import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import xyz.ngthav.opencheckin.model.Member;
import xyz.ngthav.opencheckin.util.Files;

import java.io.File;
import java.util.Optional;

/**
 * Add / Edit member dialog. Same fields both ways: a file-chooser picture (locked
 * decision: file chooser only — no in-app webcam capture), a required name, and a description.
 *
 * <p>The dialog only collects input; copying the picture into {@code data/pictures/<roomId>/} and
 * writing the row happen at the call site ({@link MemberView}).
 */
public final class MemberEditDialog {

    /** Collected input; {@code image} is {@code null} when no new picture was chosen. */
    public record Result(String name, String description, File image) {
    }

    private MemberEditDialog() {
    }

    /** @param existing the member being edited, or {@code null} to add a new one. */
    public static Optional<Result> show(Window owner, Member existing, long roomId) {
        Dialog<Result> dialog = new Dialog<>();
        dialog.initOwner(owner);
        boolean editing = existing != null;
        dialog.setTitle(editing ? "Edit member" : "Add member");
        dialog.setHeaderText(editing ? "Edit member" : "Add a new member");

        ButtonType saveType = new ButtonType(editing ? "Save" : "Add", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);

        TextField nameField = new TextField(editing ? existing.name() : "");
        nameField.setPromptText("Full name");

        TextArea descField = new TextArea(editing && existing.description() != null ? existing.description() : "");
        descField.setPromptText("Description (optional)");
        descField.setPrefRowCount(3);
        descField.setWrapText(true);

        ImageView preview = new ImageView();
        preview.setFitWidth(96);
        preview.setFitHeight(96);
        preview.setPreserveRatio(true);
        if (editing && existing.hasPicture()) {
            try {
                // A missing file yields an errored Image rather than throwing; guard on isError().
                Image existingImage = new Image(
                        Files.picturePath(roomId, existing.pictureName()).toUri().toString(),
                        96, 96, true, true);
                if (!existingImage.isError()) {
                    preview.setImage(existingImage);
                }
            } catch (RuntimeException ignored) {
                // malformed URI — leave the preview empty
            }
        }

        // Holder for the chosen file (mutated from the chooser callback).
        final File[] chosen = {null};
        Label chosenLabel = new Label(editing && existing.hasPicture() ? existing.pictureName() : "No image chosen");
        chosenLabel.getStyleClass().add("empty-subtitle");

        javafx.scene.control.Button chooseButton = new javafx.scene.control.Button("Choose image…");
        chooseButton.setOnAction(e -> {
            FileChooser fc = new FileChooser();
            fc.setTitle("Choose member picture");
            fc.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp"));
            File file = fc.showOpenDialog(owner);
            if (file != null) {
                chosen[0] = file;
                chosenLabel.setText(file.getName());
                try {
                    preview.setImage(new Image(file.toURI().toString(), 96, 96, true, true));
                } catch (RuntimeException ignored) {
                    // ignore preview failure; the copy step re-reads the file anyway
                }
            }
        });

        VBox pictureBox = new VBox(8, preview, chooseButton, chosenLabel);
        pictureBox.setAlignment(Pos.CENTER);

        GridPane fields = new GridPane();
        fields.setHgap(12);
        fields.setVgap(10);
        fields.addRow(0, new Label("Name"), nameField);
        fields.addRow(1, new Label("Description"), descField);

        Label error = new Label();
        error.getStyleClass().add("form-error");
        error.setVisible(false);

        VBox rightSide = new VBox(10, fields, error);

        HBox content = new HBox(20, pictureBox, rightSide);
        content.setPadding(new Insets(8, 4, 4, 4));
        dialog.getDialogPane().setContent(content);

        dialog.getDialogPane().lookupButton(saveType).addEventFilter(ActionEvent.ACTION, evt -> {
            if (nameField.getText() == null || nameField.getText().isBlank()) {
                error.setText("Name is required.");
                error.setVisible(true);
                evt.consume();
            }
        });

        dialog.setResultConverter(button -> {
            if (button != saveType) {
                return null;
            }
            String desc = descField.getText() == null || descField.getText().isBlank() ? null : descField.getText().trim();
            return new Result(nameField.getText().trim(), desc, chosen[0]);
        });

        return dialog.showAndWait();
    }
}
