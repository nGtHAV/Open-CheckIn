package xyz.ngthav.opencheckin.db;

import xyz.ngthav.opencheckin.model.Attendance;
import xyz.ngthav.opencheckin.util.Dates;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Thin SQL for the {@code attendance} table. One row per scan; {@code created_at} is the scan
 * time (which may be edited on the Status page). Dates are matched with SQLite's {@code date()}
 * on the ISO-8601 text.
 */
public final class AttendanceDao {

    private final Database db;

    public AttendanceDao(Database db) {
        this.db = db;
    }

    /** Records a scan at an explicit time (used for Status edits that insert rows). */
    public Attendance insert(long roomId, long memberId, LocalDateTime createdAt) {
        String sql = "INSERT INTO attendance (room_id, member_id, created_at) VALUES (?, ?, ?)";
        try (PreparedStatement ps = db.connection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, roomId);
            ps.setLong(2, memberId);
            ps.setString(3, Dates.toIso(createdAt));
            ps.executeUpdate();
            long id;
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                id = keys.getLong(1);
            }
            return new Attendance(id, roomId, memberId, createdAt);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to insert attendance", e);
        }
    }

    /** Records a scan at "now". */
    public Attendance insert(long roomId, long memberId) {
        return insert(roomId, memberId, Dates.now());
    }

    public List<Attendance> findByRoomAndDate(long roomId, LocalDate date) {
        return query("SELECT * FROM attendance WHERE room_id = ? AND date(created_at) = ? "
                        + "ORDER BY created_at ASC",
                ps -> {
                    ps.setLong(1, roomId);
                    ps.setString(2, date.toString());
                });
    }

    public int countDistinctMembersOn(long roomId, LocalDate date) {
        String sql = "SELECT COUNT(DISTINCT member_id) FROM attendance WHERE room_id = ? AND date(created_at) = ?";
        try (PreparedStatement ps = db.connection().prepareStatement(sql)) {
            ps.setLong(1, roomId);
            ps.setString(2, date.toString());
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to count attendance for room " + roomId, e);
        }
    }

    /** Latest scans first — feeds the "recently checked in" list. */
    public List<Attendance> recent(long roomId, int limit) {
        return query("SELECT * FROM attendance WHERE room_id = ? ORDER BY created_at DESC LIMIT ?",
                ps -> {
                    ps.setLong(1, roomId);
                    ps.setInt(2, limit);
                });
    }

    /** Latest scans on a given day, newest first (default scope for the recent list is today). */
    public List<Attendance> recentOn(long roomId, LocalDate date, int limit) {
        return query("SELECT * FROM attendance WHERE room_id = ? AND date(created_at) = ? "
                        + "ORDER BY created_at DESC LIMIT ?",
                ps -> {
                    ps.setLong(1, roomId);
                    ps.setString(2, date.toString());
                    ps.setInt(3, limit);
                });
    }

    /** A member's scans for a given day, oldest first — input to the scan decision and Status edits. */
    public List<Attendance> findForMemberOnDate(long roomId, long memberId, LocalDate date) {
        return query("SELECT * FROM attendance WHERE room_id = ? AND member_id = ? AND date(created_at) = ? "
                        + "ORDER BY created_at ASC",
                ps -> {
                    ps.setLong(1, roomId);
                    ps.setLong(2, memberId);
                    ps.setString(3, date.toString());
                });
    }

    /** Rewrites a scan's timestamp (Status page time editing). */
    public void updateCreatedAt(long attendanceId, LocalDateTime newTimestamp) {
        String sql = "UPDATE attendance SET created_at = ? WHERE id = ?";
        try (PreparedStatement ps = db.connection().prepareStatement(sql)) {
            ps.setString(1, Dates.toIso(newTimestamp));
            ps.setLong(2, attendanceId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to update attendance " + attendanceId, e);
        }
    }

    // ----- helpers -----

    private interface Binder {
        void bind(PreparedStatement ps) throws SQLException;
    }

    private List<Attendance> query(String sql, Binder binder) {
        List<Attendance> rows = new ArrayList<>();
        try (PreparedStatement ps = db.connection().prepareStatement(sql)) {
            binder.bind(ps);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rows.add(map(rs));
                }
            }
            return rows;
        } catch (SQLException e) {
            throw new IllegalStateException("Attendance query failed: " + sql, e);
        }
    }

    private static Attendance map(ResultSet rs) throws SQLException {
        return new Attendance(
                rs.getLong("id"),
                rs.getLong("room_id"),
                rs.getLong("member_id"),
                Dates.fromIso(rs.getString("created_at")));
    }
}
