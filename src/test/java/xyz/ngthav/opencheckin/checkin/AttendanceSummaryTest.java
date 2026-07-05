package xyz.ngthav.opencheckin.checkin;

import org.junit.jupiter.api.Test;
import xyz.ngthav.opencheckin.model.Attendance;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Proves the Status page's MIN/MAX check-in/out inference. */
class AttendanceSummaryTest {

    private static Attendance at(LocalDateTime t) {
        return new Attendance(0, 1, 1, t);
    }

    @Test
    void noRows_bothBlank() {
        AttendanceSummary s = AttendanceSummary.summarize(List.of());
        assertFalse(s.hasCheckIn());
        assertFalse(s.hasCheckOut());
        assertNull(s.checkIn());
        assertNull(s.checkOut());
    }

    @Test
    void oneRow_checkInOnly() {
        LocalDateTime t = LocalDateTime.of(2026, 7, 5, 9, 15);
        AttendanceSummary s = AttendanceSummary.summarize(List.of(at(t)));
        assertTrue(s.hasCheckIn());
        assertEquals(t, s.checkIn());
        assertFalse(s.hasCheckOut()); // a lone scan is a check-in with no check-out yet
        assertNull(s.checkOut());
    }

    @Test
    void twoRows_minIsCheckInMaxIsCheckOut_regardlessOfOrder() {
        LocalDateTime early = LocalDateTime.of(2026, 7, 5, 9, 0);
        LocalDateTime late = LocalDateTime.of(2026, 7, 5, 17, 30);
        // deliberately unordered
        AttendanceSummary s = AttendanceSummary.summarize(List.of(at(late), at(early)));
        assertEquals(early, s.checkIn());
        assertEquals(late, s.checkOut());
    }

    @Test
    void manyRows_useExtremes() {
        LocalDateTime a = LocalDateTime.of(2026, 7, 5, 8, 45);
        LocalDateTime b = LocalDateTime.of(2026, 7, 5, 12, 0);
        LocalDateTime c = LocalDateTime.of(2026, 7, 5, 18, 5);
        AttendanceSummary s = AttendanceSummary.summarize(List.of(at(b), at(c), at(a)));
        assertEquals(a, s.checkIn());
        assertEquals(c, s.checkOut());
    }
}
