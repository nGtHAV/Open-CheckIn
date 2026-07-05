package xyz.ngthav.opencheckin.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Date / time helpers. Storage format is ISO-8601 local text ({@link LocalDateTime#toString()}),
 * which sorts chronologically — that keeps the Status queries trivial. Clock times
 * (room check-in / check-out) are {@code "HH:mm"}.
 */
public final class Dates {

    /** Wall-clock display / storage format for room times. */
    public static final DateTimeFormatter HHMM = DateTimeFormatter.ofPattern("HH:mm");
    /** Filename date stamp (e.g. {@code 20260705}) for member pictures. */
    public static final DateTimeFormatter FILE_DATE = DateTimeFormatter.ofPattern("yyyyMMdd");

    private Dates() {
    }

    public static LocalDateTime now() {
        return LocalDateTime.now();
    }

    public static LocalDate today() {
        return LocalDate.now();
    }

    // ----- ISO-8601 storage (created_at / updated_at / attendance.created_at) -----

    public static String toIso(LocalDateTime dt) {
        return dt.toString();
    }

    public static LocalDateTime fromIso(String iso) {
        return LocalDateTime.parse(iso);
    }

    // ----- "HH:mm" clock times (room.checkin / room.checkout) -----

    /** Formats a timestamp's time-of-day as {@code "HH:mm"} (used in the Status table). */
    public static String formatTime(LocalDateTime dt) {
        return dt == null ? "" : dt.format(HHMM);
    }

    /** True when {@code text} is a valid {@code "HH:mm"} clock time. Blank is invalid. */
    public static boolean isValidHmm(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        try {
            LocalTime.parse(text.trim(), HHMM);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    /**
     * Combines a calendar date with a {@code "HH:mm"} clock time into a timestamp — used when
     * the Status page rewrites an attendance row's {@code created_at}.
     */
    public static LocalDateTime combine(LocalDate date, String hhmm) {
        return LocalDateTime.of(date, LocalTime.parse(hhmm.trim(), HHMM));
    }
}
