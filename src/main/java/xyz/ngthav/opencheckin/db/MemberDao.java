package xyz.ngthav.opencheckin.db;

import xyz.ngthav.opencheckin.model.Member;
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
 * Thin SQL for the {@code member} table. {@code created_at}/{@code updated_at} stamped on insert;
 * only {@code updated_at} refreshed on update.
 */
public final class MemberDao {

    private final Database db;

    public MemberDao(Database db) {
        this.db = db;
    }

    public Member insert(long roomId, String uuid, String name, String description, String pictureName) {
        LocalDateTime now = Dates.now();
        String sql = "INSERT INTO member (uuid, name, description, picture_name, room_id, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = db.connection().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, uuid);
            ps.setString(2, name);
            ps.setString(3, description);
            ps.setString(4, pictureName);
            ps.setLong(5, roomId);
            ps.setString(6, Dates.toIso(now));
            ps.setString(7, Dates.toIso(now));
            ps.executeUpdate();
            long id;
            try (ResultSet keys = ps.getGeneratedKeys()) {
                keys.next();
                id = keys.getLong(1);
            }
            return new Member(id, uuid, name, description, pictureName, roomId, now, now);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to insert member", e);
        }
    }

    /** Updates editable fields and refreshes {@code updated_at}. */
    public void update(long id, String name, String description, String pictureName) {
        String sql = "UPDATE member SET name = ?, description = ?, picture_name = ?, updated_at = ? WHERE id = ?";
        try (PreparedStatement ps = db.connection().prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setString(2, description);
            ps.setString(3, pictureName);
            ps.setString(4, Dates.toIso(Dates.now()));
            ps.setLong(5, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to update member " + id, e);
        }
    }

    public List<Member> findByRoom(long roomId) {
        return query("SELECT * FROM member WHERE room_id = ? ORDER BY name COLLATE NOCASE ASC",
                ps -> ps.setLong(1, roomId));
    }

    /** Case-insensitive filter on name or description within a room. */
    public List<Member> search(long roomId, String text) {
        String like = "%" + (text == null ? "" : text.trim()) + "%";
        return query("SELECT * FROM member WHERE room_id = ? "
                        + "AND (name LIKE ? OR IFNULL(description, '') LIKE ?) "
                        + "ORDER BY name COLLATE NOCASE ASC",
                ps -> {
                    ps.setLong(1, roomId);
                    ps.setString(2, like);
                    ps.setString(3, like);
                });
    }

    public Optional<Member> findByUuid(long roomId, String uuid) {
        List<Member> found = query("SELECT * FROM member WHERE room_id = ? AND uuid = ?",
                ps -> {
                    ps.setLong(1, roomId);
                    ps.setString(2, uuid);
                });
        return found.isEmpty() ? Optional.empty() : Optional.of(found.get(0));
    }

    public Optional<Member> findById(long id) {
        List<Member> found = query("SELECT * FROM member WHERE id = ?", ps -> ps.setLong(1, id));
        return found.isEmpty() ? Optional.empty() : Optional.of(found.get(0));
    }

    // ----- helpers -----

    private interface Binder {
        void bind(PreparedStatement ps) throws SQLException;
    }

    private List<Member> query(String sql, Binder binder) {
        List<Member> members = new ArrayList<>();
        try (PreparedStatement ps = db.connection().prepareStatement(sql)) {
            binder.bind(ps);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    members.add(map(rs));
                }
            }
            return members;
        } catch (SQLException e) {
            throw new IllegalStateException("Member query failed: " + sql, e);
        }
    }

    private static Member map(ResultSet rs) throws SQLException {
        return new Member(
                rs.getLong("id"),
                rs.getString("uuid"),
                rs.getString("name"),
                rs.getString("description"),
                rs.getString("picture_name"),
                rs.getLong("room_id"),
                Dates.fromIso(rs.getString("created_at")),
                Dates.fromIso(rs.getString("updated_at")));
    }
}
