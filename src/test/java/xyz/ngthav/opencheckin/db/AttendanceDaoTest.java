package xyz.ngthav.opencheckin.db;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import xyz.ngthav.opencheckin.model.Attendance;
import xyz.ngthav.opencheckin.model.Member;
import xyz.ngthav.opencheckin.model.Room;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Confirms the date-scoped attendance queries — which rely on SQLite's {@code date()} parsing the
 * ISO-8601 {@code 'T'} timestamp text ({@link LocalDateTime#toString()}). These queries drive the
 * today-counter, the Status page and the scan decision, so this is load-bearing.
 */
class AttendanceDaoTest {

    private Database db;
    private AttendanceDao dao;
    private long roomId;
    private long memberId;

    @BeforeEach
    void setUp() {
        db = Database.inMemory();
        RoomDao roomDao = new RoomDao(db);
        MemberDao memberDao = new MemberDao(db);
        Room room = roomDao.insert("Room A", "09:00", "17:00", false);
        Member member = memberDao.insert(room.id(), UUID.randomUUID().toString(), "John Doe", null, null);
        roomId = room.id();
        memberId = member.id();
        dao = new AttendanceDao(db);
    }

    @AfterEach
    void tearDown() {
        db.close();
    }

    @Test
    void findsRowsByDateAcrossIsoTSeparator() {
        LocalDate day = LocalDate.of(2026, 7, 5);
        dao.insert(roomId, memberId, LocalDateTime.of(day, java.time.LocalTime.of(9, 0)));
        dao.insert(roomId, memberId, LocalDateTime.of(day, java.time.LocalTime.of(17, 30, 45)));
        // a row on a different day must NOT match
        dao.insert(roomId, memberId, LocalDateTime.of(2026, 7, 6, 9, 0));

        List<Attendance> rows = dao.findByRoomAndDate(roomId, day);
        assertEquals(2, rows.size());
        assertEquals(1, dao.countDistinctMembersOn(roomId, day));
        assertEquals(2, dao.findForMemberOnDate(roomId, memberId, day).size());
        assertTrue(dao.findByRoomAndDate(roomId, LocalDate.of(2026, 7, 7)).isEmpty());
    }

    @Test
    void editingByPinnedRowIdsShiftsDayLaterWithoutSwapping() {
        // Repro of the reviewed "swap" defect: day = [09:00, 17:00]; move the whole day later to
        // check-in 18:00 / check-out 19:00. Updating the pinned MIN and MAX rows by id (as the
        // Status page now does) must yield check-in 18:00 / check-out 19:00 — not a stale 17:00.
        LocalDate day = LocalDate.of(2026, 7, 5);
        var early = dao.insert(roomId, memberId, LocalDateTime.of(day, java.time.LocalTime.of(9, 0)));
        var late = dao.insert(roomId, memberId, LocalDateTime.of(day, java.time.LocalTime.of(17, 0)));

        dao.updateCreatedAt(early.id(), LocalDateTime.of(day, java.time.LocalTime.of(18, 0)));
        dao.updateCreatedAt(late.id(), LocalDateTime.of(day, java.time.LocalTime.of(19, 0)));

        var summary = xyz.ngthav.opencheckin.checkin.AttendanceSummary.summarize(
                dao.findForMemberOnDate(roomId, memberId, day));
        assertEquals(LocalDateTime.of(day, java.time.LocalTime.of(18, 0)), summary.checkIn());
        assertEquals(LocalDateTime.of(day, java.time.LocalTime.of(19, 0)), summary.checkOut());
    }

    @Test
    void updateCreatedAtRewritesTheTimestamp() {
        LocalDate day = LocalDate.of(2026, 7, 5);
        Attendance row = dao.insert(roomId, memberId, LocalDateTime.of(day, java.time.LocalTime.of(9, 0)));
        dao.updateCreatedAt(row.id(), LocalDateTime.of(day, java.time.LocalTime.of(10, 15)));

        List<Attendance> rows = dao.findForMemberOnDate(roomId, memberId, day);
        assertEquals(1, rows.size());
        assertEquals(LocalDateTime.of(day, java.time.LocalTime.of(10, 15)), rows.get(0).createdAt());
    }
}
