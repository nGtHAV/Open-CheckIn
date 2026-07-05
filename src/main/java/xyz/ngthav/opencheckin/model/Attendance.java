package xyz.ngthav.opencheckin.model;

import java.time.LocalDateTime;

/**
 * A single scan event. One row per scan — check-in vs check-out is <b>derived</b> from scan
 * order, never stored (a locked design decision). {@code createdAt} is the moment of the
 * scan; there is deliberately no {@code updatedAt} (a scan is a point-in-time event).
 */
public record Attendance(
        long id,
        long roomId,
        long memberId,
        LocalDateTime createdAt
) {
}
