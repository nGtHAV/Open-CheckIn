package xyz.ngthav.opencheckin.ui.member;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import xyz.ngthav.opencheckin.app.AppState;
import xyz.ngthav.opencheckin.card.CardExporter;
import xyz.ngthav.opencheckin.card.CardPrinter;
import xyz.ngthav.opencheckin.model.Member;
import xyz.ngthav.opencheckin.model.Room;
import xyz.ngthav.opencheckin.ui.Page;
import xyz.ngthav.opencheckin.util.Dates;
import xyz.ngthav.opencheckin.util.Files;
import xyz.ngthav.opencheckin.util.Images;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The Member page: a search box + "＋ Add member" on one line, and a table of the
 * active room's members (photo, name, description, created-at) with per-row Edit / Save card /
 * Print card actions. Add/Edit copies the chosen picture into {@code data/pictures/<roomId>/}.
 */
public final class MemberView extends Page {

    private static final DateTimeFormatter CREATED_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public MemberView(AppState app, Runnable onCreateRoom) {
        super(app, onCreateRoom);
    }

    @Override
    public String title() {
        return "Members";
    }

    @Override
    protected Node buildContent() {
        final Room room = app.currentRoom();
        final long roomId = room.id();

        Label pageTitle = new Label("Members");
        pageTitle.getStyleClass().add("page-title");

        TextField search = new TextField();
        search.setPromptText("Search by name or description…");
        search.getStyleClass().add("search-box");
        HBox.setHgrow(search, Priority.ALWAYS);

        Button addButton = new Button("＋ Add member");
        addButton.getStyleClass().add("primary-button");

        HBox toolbar = new HBox(12, search, addButton);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        ObservableList<Member> items = FXCollections.observableArrayList();
        TableView<Member> table = new TableView<>(items);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(new Label("No members yet. Add one to get started."));
        VBox.setVgrow(table, Priority.ALWAYS);

        Runnable reload = () -> items.setAll(app.members().search(roomId, search.getText()));
        search.textProperty().addListener((obs, was, now) -> reload.run());

        table.getColumns().add(photoColumn());
        table.getColumns().add(textColumn("Name", Member::name));
        table.getColumns().add(textColumn("Description", m -> m.description() == null ? "" : m.description()));
        table.getColumns().add(textColumn("Created at", m -> m.createdAt().format(CREATED_FMT)));
        table.getColumns().add(actionColumn("Edit", "Edit", member -> {
            Window owner = windowOf(table);
            MemberEditDialog.show(owner, member, roomId).ifPresent(result -> {
                applyEdit(roomId, member, result);
                reload.run();
            });
        }));
        table.getColumns().add(actionColumn("Save card", "Save", member ->
                CardExporter.saveToPdf(member, room, windowOf(table))));
        table.getColumns().add(actionColumn("Print card", "Print", member ->
                CardPrinter.print(member, room, windowOf(table))));

        addButton.setOnAction(e -> {
            Window owner = windowOf(addButton);
            MemberEditDialog.show(owner, null, roomId).ifPresent(result -> {
                applyAdd(roomId, result);
                reload.run();
            });
        });

        reload.run();

        VBox layout = new VBox(20, pageTitle, toolbar, table);
        return layout;
    }

    // ----- add / edit -----

    private void applyAdd(long roomId, MemberEditDialog.Result result) {
        try {
            String uuid = UUID.randomUUID().toString();
            String pictureName = resolvePicture(roomId, uuid, result.name(), null, result.image());
            app.members().insert(roomId, uuid, result.name(), result.description(), pictureName);
        } catch (RuntimeException ex) {
            showError("Could not add member: " + ex.getMessage());
        }
    }

    private void applyEdit(long roomId, Member existing, MemberEditDialog.Result result) {
        try {
            String pictureName = resolvePicture(
                    roomId, existing.uuid(), result.name(), existing.pictureName(), result.image());
            app.members().update(existing.id(), result.name(), result.description(), pictureName);
        } catch (RuntimeException ex) {
            showError("Could not save member: " + ex.getMessage());
        }
    }

    /**
     * Resolves the picture filename, copying/renaming files on disk as needed:
     * a newly chosen image is copied to {@code yyyyMMdd-<name>.png} (today), and on a name change
     * with no new image the existing file is renamed. Duplicate names on the same day get a short
     * uuid suffix. Returns the stored filename, or {@code null} when there is no picture.
     */
    private String resolvePicture(long roomId, String uuid, String name,
                                  String existingPictureName, File chosenImage) {
        if (chosenImage != null) {
            String fileName = Files.pictureFileName(Dates.today(), name);
            Path dest = Files.picturePath(roomId, fileName);
            if (java.nio.file.Files.exists(dest) && !fileName.equals(existingPictureName)) {
                fileName = Files.pictureFileName(Dates.today(), name, uuid.substring(0, 4));
                dest = Files.picturePath(roomId, fileName);
            }
            Images.copyAsPng(chosenImage.toPath(), dest);
            deleteIfObsolete(roomId, existingPictureName, fileName);
            return fileName;
        }
        // No new image: keep the existing one, but re-normalise its name on a rename.
        if (existingPictureName == null) {
            return null;
        }
        String target = Files.pictureFileName(Dates.today(), name);
        if (target.equals(existingPictureName)) {
            return existingPictureName;
        }
        Path from = Files.picturePath(roomId, existingPictureName);
        if (!java.nio.file.Files.exists(from)) {
            return existingPictureName; // file already gone — leave the stored name as-is
        }
        Path to = Files.picturePath(roomId, target);
        if (java.nio.file.Files.exists(to)) {
            target = Files.pictureFileName(Dates.today(), name, uuid.substring(0, 4));
            to = Files.picturePath(roomId, target);
        }
        try {
            java.nio.file.Files.move(from, to, StandardCopyOption.REPLACE_EXISTING);
            return target;
        } catch (IOException ex) {
            return existingPictureName; // rename failed — keep pointing at the old file
        }
    }

    private void deleteIfObsolete(long roomId, String oldName, String newName) {
        if (oldName == null || oldName.equals(newName)) {
            return;
        }
        try {
            java.nio.file.Files.deleteIfExists(Files.picturePath(roomId, oldName));
        } catch (IOException ignored) {
            // an orphaned old picture is harmless; ignore
        }
    }

    // ----- table columns -----

    private static TableColumn<Member, String> textColumn(String header, java.util.function.Function<Member, String> getter) {
        TableColumn<Member, String> col = new TableColumn<>(header);
        col.setCellValueFactory(data -> new SimpleStringProperty(getter.apply(data.getValue())));
        return col;
    }

    private static TableColumn<Member, Void> photoColumn() {
        TableColumn<Member, Void> col = new TableColumn<>("Photo");
        col.setSortable(false);
        col.setMaxWidth(70);
        col.setCellFactory(c -> new TableCell<>() {
            private final ImageView view = new ImageView();

            {
                view.setFitWidth(34);
                view.setFitHeight(34);
                view.setPreserveRatio(true);
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                    return;
                }
                Member m = getTableView().getItems().get(getIndex());
                if (m.hasPicture()) {
                    try {
                        // A missing file doesn't throw — Image just enters an error state — so
                        // check isError() explicitly before using it.
                        Image img = new Image(
                                Files.picturePath(m.roomId(), m.pictureName()).toUri().toString(),
                                34, 34, true, true);
                        if (!img.isError()) {
                            view.setImage(img);
                            setGraphic(view);
                            return;
                        }
                    } catch (RuntimeException ignored) {
                        // malformed URI — fall through to the dash
                    }
                }
                setGraphic(new Label("—"));
            }
        });
        return col;
    }

    private static TableColumn<Member, Void> actionColumn(String header, String label, Consumer<Member> onClick) {
        TableColumn<Member, Void> col = new TableColumn<>(header);
        col.setSortable(false);
        col.setCellFactory(c -> new TableCell<>() {
            private final Button button = new Button(label);

            {
                button.getStyleClass().add("secondary-button");
                button.setOnAction(e -> onClick.accept(getTableView().getItems().get(getIndex())));
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : button);
            }
        });
        return col;
    }

    // ----- helpers -----

    private static Window windowOf(Node node) {
        return node.getScene() == null ? null : node.getScene().getWindow();
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message);
        alert.setHeaderText(null);
        alert.showAndWait();
    }
}
