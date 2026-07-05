package xyz.ngthav.opencheckin.checkin;

import org.junit.jupiter.api.Test;
import xyz.ngthav.opencheckin.model.Attendance;
import xyz.ngthav.opencheckin.model.Room;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Proves the pure {@code decide()} against the check-in/out truth table. {@code decide()} depends only on
 * the room's checkout setting and the number of the member's rows today, so the attendance rows
 * here are placeholders.
 */
class CheckInServiceDecideTest {

    private static Room roomWithCheckout(boolean checkoutEnabled) {
        LocalDateTime now = LocalDateTime.now();
        return new Room(1, "Room A", "09:00", checkoutEnabled ? "17:00" : null, false, now, now);
    }

    private static List<Attendance> rows(int count) {
        LocalDateTime base = LocalDateTime.of(2026, 7, 5, 9, 0);
        Attendance[] arr = new Attendance[count];
        for (int i = 0; i < count; i++) {
            arr[i] = new Attendance(i + 1, 1, 1, base.plusHours(i));
        }
        return List.of(arr);
    }

    // ---- checkout disabled: every scan is a check-in; a second scan is a no-op ----

    @Test
    void checkoutDisabled_noRows_checksIn() {
        Decision d = CheckInService.decide(roomWithCheckout(false), rows(0));
        assertEquals(Action.CHECK_IN, d.action());
    }

    @Test
    void checkoutDisabled_oneRow_ignoresAlreadyIn() {
        Decision d = CheckInService.decide(roomWithCheckout(false), rows(1));
        assertEquals(Action.IGNORE, d.action());
        assertEquals("Already checked in", d.reason());
    }

    @Test
    void checkoutDisabled_twoRows_ignores() {
        Decision d = CheckInService.decide(roomWithCheckout(false), rows(2));
        assertEquals(Action.IGNORE, d.action());
    }

    // ---- checkout enabled: first in, second out, rest ignored ----

    @Test
    void checkoutEnabled_noRows_checksIn() {
        Decision d = CheckInService.decide(roomWithCheckout(true), rows(0));
        assertEquals(Action.CHECK_IN, d.action());
    }

    @Test
    void checkoutEnabled_oneRow_checksOut() {
        Decision d = CheckInService.decide(roomWithCheckout(true), rows(1));
        assertEquals(Action.CHECK_OUT, d.action());
    }

    @Test
    void checkoutEnabled_twoRows_ignoresAlreadyOut() {
        Decision d = CheckInService.decide(roomWithCheckout(true), rows(2));
        assertEquals(Action.IGNORE, d.action());
        assertEquals("Already checked out", d.reason());
    }

    @Test
    void checkoutEnabled_threeRows_ignores() {
        Decision d = CheckInService.decide(roomWithCheckout(true), rows(3));
        assertEquals(Action.IGNORE, d.action());
    }

    @Test
    void nullRowsTreatedAsNone() {
        assertEquals(Action.CHECK_IN, CheckInService.decide(roomWithCheckout(true), null).action());
        assertEquals(Action.CHECK_IN, CheckInService.decide(roomWithCheckout(false), null).action());
    }

    @Test
    void blankCheckoutCountsAsDisabled() {
        LocalDateTime now = LocalDateTime.now();
        Room blankCheckout = new Room(1, "Room", "09:00", "  ", false, now, now);
        // one row + checkout disabled => already checked in
        assertEquals(Action.IGNORE, CheckInService.decide(blankCheckout, rows(1)).action());
    }
}
