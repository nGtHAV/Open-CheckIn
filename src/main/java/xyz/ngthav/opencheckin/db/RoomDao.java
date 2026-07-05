package xyz.ngthav.opencheckin.db;

import xyz.ngthav.opencheckin.model.Room;
import xyz.ngthav.opencheckin.util.Dates;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Thin, boring SQL for the {@code room} table. All access is via {@link PreparedStatement}.
 * Timestamp policy lives here so it can't drift: {@code created_at} and {@code updated_at} are
 * both stamped on insert; only {@code updated_at} is refreshed on update.
 */
public final class RoomDao {

    /** Reference breakdown used to build the room-delete guard message. */
    public record References(int members, int attendance) {
        public int total() {
            return members + attendance;
        }
    }

    private final Database db;

    public RoomDao(Database db) {
        this.db = db;
    }

    public Room insert(String name, String checkin, String checkout, boolean manualConfirmation) {
        LocalDateTime now = Dates.now();
        String sql = "INSERT INTO room (name, checkin, checkout, manual_confirmation, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = db.connection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.setString(2, checkin);
            ps.setString(3, checkout);
            ps.setInt(4, manualConfirmation ? 1 : 0);
            ps.setString(5, Dates.toIso(now));
            ps.setString(6, Dates.toIso(now));
            ps.executeUpdate();
            long id;
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                id = keys.getLong(1);
            }
            return new Room(id, name, checkin, checkout, manualConfirmation, now, now);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to insert room", e);
        }
    }

    /** Updates the room's editable fields and refreshes {@code updated_at}. */
    public void update(long id, String name, String checkin, String checkout, boolean manualConfirmation) {
        String sql = "UPDATE room SET name = ?, checkin = ?, checkout = ?, manual_confirmation = ?, "
                + "updated_at = ? WHERE id = ?";
        try (PreparedStatement ps = db.connection().prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setString(2, checkin);
            ps.setString(3, checkout);
            ps.setInt(4, manualConfirmation ? 1 : 0);
            ps.setString(5, Dates.toIso(Dates.now()));
            ps.setLong(6, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to update room " + id, e);
        }
    }

    public List<Room> findAll() {
        String sql = "SELECT * FROM room ORDER BY name COLLATE NOCASE ASC";
        List<Room> rooms = new ArrayList<>();
        try (PreparedStatement ps = db.connection().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                rooms.add(map(rs));
            }
            return rooms;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to list rooms", e);
        }
    }

    public Optional<Room> findById(long id) {
        String sql = "SELECT * FROM room WHERE id = ?";
        try (PreparedStatement ps = db.connection().prepareStatement(sql)) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to find room " + id, e);
        }
    }

    /** The most recently created/updated room — used to auto-select on startup. */
    public Optional<Room> findMostRecentlyUsed() {
        String sql = "SELECT * FROM room ORDER BY updated_at DESC LIMIT 1";
        try (PreparedStatement ps = db.connection().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? Optional.of(map(rs)) : Optional.empty();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to find most recent room", e);
        }
    }

    /** Counts member + attendance rows that reference this room (the FK-guard pre-check). */
    public References countReferences(long roomId) {
        int members = count("SELECT COUNT(*) FROM member WHERE room_id = ?", roomId);
        int attendance = count("SELECT COUNT(*) FROM attendance WHERE room_id = ?", roomId);
        return new References(members, attendance);
    }

    private int count(String sql, long roomId) {
        try (PreparedStatement ps = db.connection().prepareStatement(sql)) {
            ps.setLong(1, roomId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to count references for room " + roomId, e);
        }
    }

    /**
     * Deletes a room only when nothing references it. Refuses (throws) when members or attendance
     * still point at it — the UI shows a friendly blocking error instead. Foreign
     * keys are the backstop if this pre-check is ever bypassed.
     */
    public void delete(long roomId) {
        References refs = countReferences(roomId);
        if (refs.total() > 0) {
            throw new IllegalStateException(
                    "Room " + roomId + " still has " + refs.members() + " members and "
                            + refs.attendance() + " attendance records");
        }
        try (PreparedStatement ps = db.connection().prepareStatement("DELETE FROM room WHERE id = ?")) {
            ps.setLong(1, roomId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to delete room " + roomId, e);
        }
    }

    private static Room map(ResultSet rs) throws SQLException {
        return new Room(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("checkin"),
                rs.getString("checkout"),
                rs.getInt("manual_confirmation") != 0,
                Dates.fromIso(rs.getString("created_at")),
                Dates.fromIso(rs.getString("updated_at")));
    }
}
