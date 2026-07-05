package xyz.ngthav.opencheckin.checkin;

/**
 * The outcome of the pure {@code decide()} function: an {@link Action} plus a human-readable
 * {@code reason} that drives the on-screen flash.
 */
public record Decision(Action action, String reason) {

    public static Decision checkIn() {
        return new Decision(Action.CHECK_IN, "Checking in");
    }

    public static Decision checkOut() {
        return new Decision(Action.CHECK_OUT, "Checking out");
    }

    public static Decision ignore(String reason) {
        return new Decision(Action.IGNORE, reason);
    }
}
