package xyz.ngthav.opencheckin.app;

import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import xyz.ngthav.opencheckin.checkin.CheckInService;
import xyz.ngthav.opencheckin.db.AttendanceDao;
import xyz.ngthav.opencheckin.db.Database;
import xyz.ngthav.opencheckin.db.MemberDao;
import xyz.ngthav.opencheckin.db.RoomDao;
import xyz.ngthav.opencheckin.model.Attendance;
import xyz.ngthav.opencheckin.model.Room;
import xyz.ngthav.opencheckin.util.Dates;

import java.util.List;
import java.util.Optional;

/**
 * The one piece of shared application state: the open database, the DAOs, the list of rooms, and
 * the currently-active room. Every page is room-scoped and observes {@link #currentRoomProperty()};
 * when it fires {@code null} the pages show the "create a room to get started" empty state
 * All DAO access flows through here so the whole app talks to one connection.
 */
public final class AppState {

    private final Database db;
    private final RoomDao roomDao;
    private final MemberDao memberDao;
    private final AttendanceDao attendanceDao;
    /** Shared scan brain — the dashboards and the scanner window all drive/observe this one. */
    private final CheckInService checkIn;

    private final ObservableList<Room> rooms = FXCollections.observableArrayList();
    /** Nullable — {@code null} means "no active room" (first-run empty state). */
    private final ObjectProperty<Room> currentRoom = new SimpleObjectProperty<>(this, "currentRoom", null);

    public AppState(Database db) {
        this.db = db;
        this.roomDao = new RoomDao(db);
        this.memberDao = new MemberDao(db);
        this.attendanceDao = new AttendanceDao(db);
        this.checkIn = new CheckInService(this);
        reloadRooms();
        // On startup, auto-select the most recently used room if any exist.
        roomDao.findMostRecentlyUsed().ifPresent(currentRoom::set);
    }

    public CheckInService checkIn() {
        return checkIn;
    }

    // ----- dashboard convenience queries (guarded against a null room) -----

    /** Total members in the active room. */
    public int totalMembers() {
        Room room = currentRoom.get();
        return room == null ? 0 : memberDao.findByRoom(room.id()).size();
    }

    /** Distinct members with at least one scan today (the "checked in today" numerator). */
    public int checkedInToday() {
        Room room = currentRoom.get();
        return room == null ? 0 : attendanceDao.countDistinctMembersOn(room.id(), Dates.today());
    }

    /** Latest scans today, newest first — feeds the "recently checked in" list. */
    public List<Attendance> recentToday(int limit) {
        Room room = currentRoom.get();
        return room == null ? List.of() : attendanceDao.recentOn(room.id(), Dates.today(), limit);
    }

    // ----- DAOs / db -----

    public RoomDao rooms_() {
        return roomDao;
    }

    public MemberDao members() {
        return memberDao;
    }

    public AttendanceDao attendance() {
        return attendanceDao;
    }

    public Database db() {
        return db;
    }

    // ----- room list & selection -----

    public ObservableList<Room> rooms() {
        return rooms;
    }

    public ReadOnlyObjectProperty<Room> currentRoomProperty() {
        return currentRoom;
    }

    public Room currentRoom() {
        return currentRoom.get();
    }

    public boolean hasRoom() {
        return currentRoom.get() != null;
    }

    /** Reloads the room list from the database, keeping the selection pointed at a fresh instance. */
    public void reloadRooms() {
        rooms.setAll(roomDao.findAll());
        Room selected = currentRoom.get();
        if (selected != null) {
            Optional<Room> fresh = roomDao.findById(selected.id());
            currentRoom.set(fresh.orElse(rooms.isEmpty() ? null : rooms.get(0)));
        }
    }

    public void selectRoom(Room room) {
        currentRoom.set(room);
    }

    /** Creates, persists and selects a new room (from the room dialog). */
    public Room createRoom(String name, String checkin, String checkout, boolean manualConfirmation) {
        Room saved = roomDao.insert(name, checkin, checkout, manualConfirmation);
        reloadRooms();
        currentRoom.set(saved);
        return saved;
    }

    /** Persists Settings edits to the current room and re-selects the refreshed instance. */
    public Room updateCurrentRoom(String name, String checkin, String checkout, boolean manualConfirmation) {
        Room current = currentRoom.get();
        if (current == null) {
            throw new IllegalStateException("No active room to update");
        }
        roomDao.update(current.id(), name, checkin, checkout, manualConfirmation);
        reloadRooms();
        Room refreshed = roomDao.findById(current.id()).orElse(null);
        currentRoom.set(refreshed);
        return refreshed;
    }

    /** Reference breakdown for the room-delete guard. */
    public RoomDao.References roomReferences(Room room) {
        return roomDao.countReferences(room.id());
    }

    /**
     * Deletes a room (throws {@link IllegalStateException} if still referenced), then reselects
     * another room, or drops to the empty state when none remain.
     */
    public void deleteRoom(Room room) {
        roomDao.delete(room.id());
        boolean deletingCurrent = currentRoom.get() != null && currentRoom.get().id() == room.id();
        reloadRooms();
        if (deletingCurrent) {
            currentRoom.set(rooms.isEmpty() ? null : rooms.get(0));
        }
    }
}
