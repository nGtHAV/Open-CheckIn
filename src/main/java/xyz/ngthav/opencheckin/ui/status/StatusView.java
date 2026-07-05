package xyz.ngthav.opencheckin.ui.status;

import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import xyz.ngthav.opencheckin.app.AppState;
import xyz.ngthav.opencheckin.checkin.AttendanceSummary;
import xyz.ngthav.opencheckin.model.Attendance;
import xyz.ngthav.opencheckin.model.Member;
import xyz.ngthav.opencheckin.model.Room;
import xyz.ngthav.opencheckin.ui.Page;
import xyz.ngthav.opencheckin.util.Dates;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * The Status page: a date picker and a per-day table of members with derived
 * check-in (MIN) / check-out (MAX) times. The check-out column is hidden when the room disables
 * checkout. Editing rewrites {@code attendance.created_at} following the row-count rules.
 */
public final class StatusView extends Page {

    private record Row(Member member, LocalDateTime checkIn, LocalDateTime checkOut) {
    }

    private final ObservableList<Row> rows = FXCollections.observableArrayList();
    private DatePicker datePicker;

    public StatusView(AppState app, Runnable onCreateRoom) {
        super(app, onCreateRoom);
    }

    @Override
    public String title() {
        return "Status";
    }

    @Override
    protected Node buildContent() {
        final Room room = app.currentRoom();

        Label pageTitle = new Label("Status");
        pageTitle.getStyleClass().add("page-title");

        datePicker = new DatePicker(Dates.today());
        datePicker.setEditable(false); // value comes only from the calendar popup — never null
        datePicker.valueProperty().addListener((obs, was, now) -> reload(room));
        HBox toolbar = new HBox(12, new Label("Date"), datePicker);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        TableView<Row> table = new TableView<>(rows);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setPlaceholder(new Label("No members in this room yet."));
        VBox.setVgrow(table, Priority.ALWAYS);

        TableColumn<Row, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().member().name()));
        table.getColumns().add(nameCol);

        TableColumn<Row, String> inCol = new TableColumn<>("Check-in");
        inCol.setCellValueFactory(d -> new SimpleStringProperty(Dates.formatTime(d.getValue().checkIn())));
        table.getColumns().add(inCol);

        // Check-out column omitted entirely when checkout is disabled.
        if (room.checkoutEnabled()) {
            TableColumn<Row, String> outCol = new TableColumn<>("Check-out");
            outCol.setCellValueFactory(d -> new SimpleStringProperty(Dates.formatTime(d.getValue().checkOut())));
            table.getColumns().add(outCol);
        }

        TableColumn<Row, Void> editCol = new TableColumn<>("");
        editCol.setSortable(false);
        editCol.setMaxWidth(90);
        editCol.setCellFactory(c -> new TableCell<>() {
            private final Button button = new Button("Edit");

            {
                button.getStyleClass().add("secondary-button");
                button.setOnAction(e -> openEditor(room, getTableView().getItems().get(getIndex())));
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : button);
            }
        });
        table.getColumns().add(editCol);

        reload(room);

        return new VBox(20, pageTitle, toolbar, table);
    }

    private void reload(Room room) {
        LocalDate date = datePicker.getValue();
        if (date == null) {
            return; // defensive: nothing to show without a date
        }
        List<Member> members = app.members().findByRoom(room.id());
        rows.clear();
        for (Member m : members) {
            List<Attendance> dayRows = app.attendance().findForMemberOnDate(room.id(), m.id(), date);
            AttendanceSummary summary = AttendanceSummary.summarize(dayRows);
            rows.add(new Row(m, summary.checkIn(), summary.checkOut()));
        }
    }

    private void openEditor(Room room, Row row) {
        Window owner = datePicker.getScene() == null ? null : datePicker.getScene().getWindow();
        String currentIn = row.checkIn() == null ? null : Dates.formatTime(row.checkIn());
        String currentOut = row.checkOut() == null ? null : Dates.formatTime(row.checkOut());
        StatusEditDialog.show(owner, row.member(), room.checkoutEnabled(), currentIn, currentOut)
                .ifPresent(result -> {
                    applyEdit(room, row.member(), result);
                    reload(room);
                });
    }

    /**
     * Applies the row-count rules. The earliest/latest rows are pinned from the
     * <b>pre-edit</b> set, so moving check-in never reshuffles which row is the check-out (and vice
     * versa) — updating both by identity keeps MIN/MAX pointing where the user intended. The dialog
     * guarantees check-out ≥ check-in, so the edited MIN row stays the earliest.
     */
    private void applyEdit(Room room, Member member, StatusEditDialog.Result result) {
        LocalDate date = datePicker.getValue();
        if (date == null) {
            return;
        }
        long roomId = room.id();
        long memberId = member.id();
        List<Attendance> dayRows = app.attendance().findForMemberOnDate(roomId, memberId, date);

        LocalDateTime newIn = result.checkIn() != null ? Dates.combine(date, result.checkIn()) : null;
        LocalDateTime newOut = (room.checkoutEnabled() && result.checkOut() != null)
                ? Dates.combine(date, result.checkOut()) : null;

        if (dayRows.size() >= 2) {
            // Pin the earliest & latest rows up front, then update each by id.
            Attendance minRow = dayRows.stream().min(Comparator.comparing(Attendance::createdAt)).orElseThrow();
            Attendance maxRow = dayRows.stream().max(Comparator.comparing(Attendance::createdAt)).orElseThrow();
            if (newIn != null) {
                app.attendance().updateCreatedAt(minRow.id(), newIn);
            }
            if (newOut != null) {
                app.attendance().updateCreatedAt(maxRow.id(), newOut);
            }
        } else if (dayRows.size() == 1) {
            Attendance only = dayRows.get(0);
            if (newIn != null) {
                app.attendance().updateCreatedAt(only.id(), newIn); // edit check-in: move that row
            }
            if (newOut != null) {
                app.attendance().insert(roomId, memberId, newOut);  // edit check-out: add a 2nd (later) row
            }
        } else { // no rows yet: insert whatever was provided
            if (newIn != null) {
                app.attendance().insert(roomId, memberId, newIn);
            }
            if (newOut != null) {
                app.attendance().insert(roomId, memberId, newOut);
            }
        }
    }
}
