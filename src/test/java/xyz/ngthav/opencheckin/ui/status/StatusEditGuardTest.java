package xyz.ngthav.opencheckin.ui.status;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Guards the Status time-edit validation. In particular, a blank check-out field
 * must keep the existing check-out so a check-in can never be pushed past it — the fix for the
 * "check-in/check-out swap" defect.
 */
class StatusEditGuardTest {

    // ----- effectiveCheckOut: blank field keeps the existing check-out (sticky) -----

    @Test
    void effectiveCheckOut_blankFieldKeepsExisting() {
        assertEquals("17:00", StatusEditDialog.effectiveCheckOut(true, "", "17:00"));
        assertEquals("17:00", StatusEditDialog.effectiveCheckOut(true, "   ", "17:00"));
    }

    @Test
    void effectiveCheckOut_typedValueWins() {
        assertEquals("18:30", StatusEditDialog.effectiveCheckOut(true, "18:30", "17:00"));
    }

    @Test
    void effectiveCheckOut_disabledIsAlwaysNull() {
        assertNull(StatusEditDialog.effectiveCheckOut(false, "18:30", "17:00"));
    }

    // ----- validate: check-out must be >= check-in -----

    @Test
    void rejectsCheckInAfterEffectiveCheckOut() {
        // The swap scenario: existing check-out 17:00, user tries check-in 18:00 (blank check-out
        // -> effective 17:00). Must be rejected rather than silently swapping the times.
        String effectiveOut = StatusEditDialog.effectiveCheckOut(true, "", "17:00");
        assertNotNull(StatusEditDialog.validate("18:00", true, effectiveOut));
    }

    @Test
    void acceptsWellOrderedTimes() {
        assertNull(StatusEditDialog.validate("18:00", true, "19:00"));
        assertNull(StatusEditDialog.validate("09:00", true, "17:00"));
        assertNull(StatusEditDialog.validate("09:00", true, "09:00")); // equal is allowed
    }

    @Test
    void rejectsInvalidTimeAndCheckOutWithoutCheckIn() {
        assertNotNull(StatusEditDialog.validate("99:99", true, "18:00"));
        assertNotNull(StatusEditDialog.validate(null, true, "18:00"));
    }
}
