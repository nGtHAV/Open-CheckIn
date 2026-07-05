package xyz.ngthav.opencheckin.checkin;

import xyz.ngthav.opencheckin.model.Attendance;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Derives a member's check-in / check-out for a single day from their raw scan rows.
 *
 * <p>Check-in = earliest scan ({@code MIN(created_at)}); check-out = latest scan
 * ({@code MAX(created_at)}), but only meaningful when there are <b>two or more</b> rows — a
 * lone scan is a check-in with no check-out yet. Pure and unit-tested.
 *
 * @param checkIn  earliest scan of the day, or {@code null} when there were no scans
 * @param checkOut latest scan of the day, or {@code null} when there are fewer than two scans
 */
public record AttendanceSummary(LocalDateTime checkIn, LocalDateTime checkOut) {

    /** Rows may arrive in any order; MIN/MAX are computed here rather than relying on the caller. */
    public static AttendanceSummary summarize(List<Attendance> rowsForDay) {
        if (rowsForDay == null || rowsForDay.isEmpty()) {
            return new AttendanceSummary(null, null);
        }
        LocalDateTime min = null;
        LocalDateTime max = null;
        for (Attendance a : rowsForDay) {
            LocalDateTime t = a.createdAt();
            if (min == null || t.isBefore(min)) {
                min = t;
            }
            if (max == null || t.isAfter(max)) {
                max = t;
            }
        }
        // A single scan is a check-in only; MAX becomes a real check-out at 2+ rows.
        LocalDateTime checkOut = rowsForDay.size() >= 2 ? max : null;
        return new AttendanceSummary(min, checkOut);
    }

    public boolean hasCheckIn() {
        return checkIn != null;
    }

    public boolean hasCheckOut() {
        return checkOut != null;
    }
}
