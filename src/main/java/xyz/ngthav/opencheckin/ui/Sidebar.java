package xyz.ngthav.opencheckin.ui;

import javafx.collections.ListChangeListener;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.util.StringConverter;
import org.kordamp.ikonli.javafx.FontIcon;
import xyz.ngthav.opencheckin.app.AppState;
import xyz.ngthav.opencheckin.app.Navigator;
import xyz.ngthav.opencheckin.db.RoomDao;
import xyz.ngthav.opencheckin.model.Room;
import xyz.ngthav.opencheckin.ui.room.RoomDialog;

import java.util.Optional;

/**
 * Left sidebar: the "Open-CheckIn" wordmark, the room selector (with create + delete), and the
 * four nav items. Selecting a room makes it active and refreshes every page;
 * deleting is FK-guarded and blocks with a friendly error when the room is still referenced.
 */
public final class Sidebar {

    private final AppState app;
    private final Navigator navigator;
    private final VBox root = new VBox();
    private final ComboBox<Room> roomSelector = new ComboBox<>();

    /** Guards the two-way sync between the selector and {@code AppState.currentRoom}. */
    private boolean syncing = false;

    public Sidebar(AppState app, Navigator navigator) {
        this.app = app;
        this.navigator = navigator;
        build();
        wireRoomSelection();
    }

    public Node getView() {
        return root;
    }

    /** Opens the create-room dialog; shared by the sidebar "＋" and the empty-state CTA. */
    public void openNewRoomDialog() {
        Window owner = root.getScene() == null ? null : root.getScene().getWindow();
        Optional<RoomDialog.Result> result = RoomDialog.show(owner);
        result.ifPresent(r -> app.createRoom(r.name(), r.checkin(), r.checkout(), r.manualConfirmation()));
    }

    private void build() {
        root.getStyleClass().add("sidebar");

        Label wordmark = new Label("Open-CheckIn");
        wordmark.getStyleClass().add("wordmark");

        // --- room selector row: dropdown + new + delete ---
        roomSelector.setItems(app.rooms());
        roomSelector.setConverter(new StringConverter<>() {
            @Override
            public String toString(Room room) {
                return room == null ? "" : room.name();
            }

            @Override
            public Room fromString(String s) {
                return null;
            }
        });
        roomSelector.setPromptText("No rooms yet");
        roomSelector.getStyleClass().add("room-selector");
        HBox.setHgrow(roomSelector, Priority.ALWAYS);
        roomSelector.setMaxWidth(Double.MAX_VALUE);

        Button newRoom = new Button();
        newRoom.setGraphic(iconOrText("mdi2p-plus", "＋"));
        newRoom.getStyleClass().add("icon-button");
        newRoom.setTooltip(new Tooltip("New room…"));
        newRoom.setOnAction(e -> openNewRoomDialog());

        Button deleteRoom = new Button();
        deleteRoom.setGraphic(iconOrText("mdi2d-delete", "🗑"));
        deleteRoom.getStyleClass().addAll("icon-button", "danger-icon-button");
        deleteRoom.setTooltip(new Tooltip("Delete room"));
        deleteRoom.setOnAction(e -> deleteCurrentRoom());
        deleteRoom.disableProperty().bind(app.currentRoomProperty().isNull());

        HBox roomRow = new HBox(6, roomSelector, newRoom, deleteRoom);
        roomRow.setAlignment(Pos.CENTER_LEFT);
        roomRow.getStyleClass().add("room-row");

        // --- nav items ---
        ToggleGroup navGroup = new ToggleGroup();
        VBox nav = new VBox(4,
                navItem(navGroup, "Dashboard", "mdi2v-view-dashboard", Navigator.PageId.DASHBOARD, true),
                navItem(navGroup, "Status", "mdi2c-calendar-check", Navigator.PageId.STATUS, false),
                navItem(navGroup, "Member", "mdi2a-account-multiple", Navigator.PageId.MEMBER, false),
                navItem(navGroup, "Setting", "mdi2c-cog", Navigator.PageId.SETTING, false));
        nav.getStyleClass().add("nav-list");

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        root.getChildren().addAll(wordmark, roomRow, nav, spacer);
    }

    private ToggleButton navItem(ToggleGroup group, String text, String iconLiteral,
                                 Navigator.PageId page, boolean selected) {
        ToggleButton item = new ToggleButton(text);
        item.setGraphic(iconOrText(iconLiteral, ""));
        item.setToggleGroup(group);
        item.setSelected(selected);
        item.setMaxWidth(Double.MAX_VALUE);
        item.setAlignment(Pos.CENTER_LEFT);
        item.getStyleClass().add("nav-item");
        item.setOnAction(e -> {
            item.setSelected(true); // never allow deselecting the active page
            navigator.show(page);
        });
        return item;
    }

    private void wireRoomSelection() {
        roomSelector.valueProperty().addListener((obs, was, now) -> {
            if (syncing || now == null) {
                return;
            }
            if (!now.equals(app.currentRoom())) {
                app.selectRoom(now);
            }
        });
        app.currentRoomProperty().addListener((obs, was, now) -> syncSelector(now));
        // The list is replaced on every reload; re-point the selector at the current room afterward.
        app.rooms().addListener((ListChangeListener<Room>) c -> syncSelector(app.currentRoom()));
        syncSelector(app.currentRoom());
    }

    private void syncSelector(Room room) {
        syncing = true;
        roomSelector.getSelectionModel().select(room);
        syncing = false;
    }

    private void deleteCurrentRoom() {
        Room room = app.currentRoom();
        if (room == null) {
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete room \"" + room.name() + "\"?", ButtonType.OK, ButtonType.CANCEL);
        confirm.setHeaderText(null);
        Optional<ButtonType> choice = confirm.showAndWait();
        if (choice.isEmpty() || choice.get() != ButtonType.OK) {
            return;
        }
        // FK guard: refuse (with a clear message) while members/attendance still reference the room.
        RoomDao.References refs = app.roomReferences(room);
        if (refs.total() > 0) {
            Alert error = new Alert(Alert.AlertType.ERROR,
                    "Can't delete \"" + room.name() + "\": it still has " + refs.members()
                            + " members and " + refs.attendance() + " attendance records. Remove them first.");
            error.setHeaderText(null);
            error.showAndWait();
            return;
        }
        try {
            app.deleteRoom(room);
        } catch (RuntimeException ex) {
            Alert error = new Alert(Alert.AlertType.ERROR, "Could not delete room: " + ex.getMessage());
            error.setHeaderText(null);
            error.showAndWait();
        }
    }

    /**
     * Builds an Ikonli icon, degrading to a text glyph if the literal is unknown on this classpath
     * (a bad literal otherwise throws and takes the window down).
     */
    private static Node iconOrText(String iconLiteral, String fallback) {
        try {
            FontIcon icon = new FontIcon(iconLiteral);
            icon.getStyleClass().add("nav-icon");
            return icon;
        } catch (RuntimeException e) {
            return new Label(fallback);
        }
    }
}
