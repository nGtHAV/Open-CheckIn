package xyz.ngthav.opencheckin.model;

import java.time.LocalDateTime;

/**
 * A check-in room. Owns its own members, settings and attendance log.
 *
 * <p>{@code checkin}/{@code checkout} are wall-clock times stored as {@code "HH:mm"}.
 * A {@code null} (or blank) {@code checkout} means <b>checkout is disabled</b> for this
 * room — the field doubles as the enable flag.
 *
 * <p>{@code id == 0} denotes a room that has not been persisted yet.
 */
public record Room(
        long id,
        String name,
        String checkin,
        String checkout,
        boolean manualConfirmation,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    /** Checkout is enabled only when a checkout time is present (a locked design decision). */
    public boolean checkoutEnabled() {
        return checkout != null && !checkout.isBlank();
    }
}
