package xyz.ngthav.opencheckin.checkin;

import javafx.application.Platform;
import xyz.ngthav.opencheckin.app.AppState;
import xyz.ngthav.opencheckin.model.Attendance;
import xyz.ngthav.opencheckin.model.Member;
import xyz.ngthav.opencheckin.model.Room;
import xyz.ngthav.opencheckin.qr.QrPayload;
import xyz.ngthav.opencheckin.util.Dates;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The scan-decision brain. One entry point — {@link #onDecoded(String)} — used by
 * both the real camera and the simulate-scan seam, so the whole flow is testable without a
 * webcam.
 *
 * <p>The decision itself, {@link #decide(Room, List)}, is a <b>pure</b> function (no JDBC, no FX)
 * and is unit-tested against the check-in/out truth table. Everything with side effects (resolving the
 * member, writing rows, notifying the UI) lives at the call site in {@link #onDecoded}.
 *
 * <p>Threading: a scan may arrive on the camera background thread; DB access must be
 * serialized and never touched from that thread. So {@code onDecoded} hops to the FX thread and
 * does the lookup, the write and the UI notification there.
 */
public final class CheckInService {

    /** Transient scanner/dashboard feedback kind (drives toast colour). */
    public enum Flash {
        INFO, SUCCESS, WARN
    }

    /** Observer of scan outcomes — implemented by the dashboards and the scanner window. */
    public interface Listener {
        /** A row was written (automatic scan, or a manual confirmation accepted). */
        default void onRecorded(Member member, Action action) {
        }

        /** Manual mode: a scan is resolved and awaiting Accept/Reject. */
        default void onPending(Member member, Action action) {
        }

        /** The pending confirmation was resolved (accepted or rejected). */
        default void onCleared() {
        }

        /** Transient feedback (e.g. "wrong room", "already checked out", "checked in"). */
        default void onFlash(String message, Flash kind) {
        }
    }

    /** A resolved scan awaiting manual Accept/Reject. */
    public record PendingScan(Member member, Action action) {
    }

    /** Same card can be read ~30×/second — ignore an identical payload seen within this window. */
    private static final long DEBOUNCE_NANOS = 3_000_000_000L;

    private final AppState app;
    private final List<Listener> listeners = new ArrayList<>();

    // All mutated only on the FX thread (process()/accept()/reject() run there).
    private String lastPayload;
    private long lastPayloadNanos;
    private PendingScan pending;

    public CheckInService(AppState app) {
        this.app = app;
    }

    public void addListener(Listener listener) {
        listeners.add(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    public boolean hasPending() {
        return pending != null;
    }

    /** The scan currently awaiting Accept/Reject (manual mode), if any. */
    public Optional<PendingScan> pending() {
        return Optional.ofNullable(pending);
    }

    // ================= the pure decision (unit-tested) =================

    /**
     * Decides what a scan means, given the room settings and the member's existing rows for today.
     * Pure: no JDBC, no {@code Platform.runLater}. See the check-in/out truth table.
     */
    public static Decision decide(Room room, List<Attendance> memberRowsToday) {
        int rows = memberRowsToday == null ? 0 : memberRowsToday.size();
        if (!room.checkoutEnabled()) {
            // Checkout disabled: every scan is a check-in; a second scan today is a no-op.
            return rows == 0 ? Decision.checkIn() : Decision.ignore("Already checked in");
        }
        // Checkout enabled: first scan in, second scan out, anything further ignored.
        if (rows == 0) {
            return Decision.checkIn();
        } else if (rows == 1) {
            return Decision.checkOut();
        } else {
            return Decision.ignore("Already checked out");
        }
    }

    // ================= the side-effecting entry point (resolve/commit) =================

    /**
     * The one scan entry point. Safe to call from any thread; the actual lookup/write/notify is
     * marshalled onto the FX thread so all DB access stays serialized.
     */
    public void onDecoded(String rawPayload) {
        if (Platform.isFxApplicationThread()) {
            process(rawPayload);
        } else {
            Platform.runLater(() -> process(rawPayload));
        }
    }

    private void process(String rawPayload) {
        // Manual mode pauses new scans while a confirmation is pending (the default).
        if (pending != null) {
            return;
        }
        long now = System.nanoTime();
        if (rawPayload != null && rawPayload.equals(lastPayload) && (now - lastPayloadNanos) < DEBOUNCE_NANOS) {
            return; // debounce: identical payload seen moments ago
        }
        try {
            Optional<QrPayload> parsed = QrPayload.parse(rawPayload);
            if (parsed.isEmpty()) {
                flash("Unrecognised code", Flash.WARN);
                return;
            }
            Room room = app.currentRoom();
            if (room == null) {
                flash("No active room", Flash.WARN);
                return;
            }
            QrPayload payload = parsed.get();
            if (payload.roomId() != room.id()) {
                flash("Wrong room", Flash.WARN);
                return;
            }
            Optional<Member> memberOpt = app.members().findByUuid(room.id(), payload.memberUUID());
            if (memberOpt.isEmpty()) {
                flash("Unknown member", Flash.WARN);
                return;
            }
            Member member = memberOpt.get();
            List<Attendance> rowsToday =
                    app.attendance().findForMemberOnDate(room.id(), member.id(), Dates.today());
            Decision decision = decide(room, rowsToday);
            switch (decision.action()) {
                case IGNORE -> flash(decision.reason(), Flash.INFO);
                case CHECK_IN, CHECK_OUT -> commit(room, member, decision);
            }
        } finally {
            // Debounce on the raw payload so a card held in frame doesn't re-flash / re-scan.
            lastPayload = rawPayload;
            lastPayloadNanos = now;
        }
    }

    private void commit(Room room, Member member, Decision decision) {
        if (room.manualConfirmation()) {
            // Manual: do NOT write yet — hand the resolved member + intended action to the panel.
            pending = new PendingScan(member, decision.action());
            notifyPending(member, decision.action());
        } else {
            // Automatic: write immediately and flash success.
            app.attendance().insert(room.id(), member.id());
            notifyRecorded(member, decision.action());
            flash(decision.reason(), Flash.SUCCESS);
        }
    }

    // ================= manual confirmation resolution (commit) =================

    /** Accepts the pending manual scan: writes the row, then resumes scanning. FX thread only. */
    public void acceptPending() {
        if (pending == null) {
            return;
        }
        PendingScan p = pending;
        Room room = app.currentRoom();
        if (room != null) {
            app.attendance().insert(room.id(), p.member().id());
            notifyRecorded(p.member(), p.action());
        }
        pending = null;
        notifyCleared();
    }

    /** Rejects the pending manual scan: nothing persists, then resumes scanning. FX thread only. */
    public void rejectPending() {
        if (pending == null) {
            return;
        }
        pending = null;
        notifyCleared();
    }

    // ================= notifications =================

    private void notifyRecorded(Member member, Action action) {
        for (Listener l : listeners) {
            l.onRecorded(member, action);
        }
    }

    private void notifyPending(Member member, Action action) {
        for (Listener l : listeners) {
            l.onPending(member, action);
        }
    }

    private void notifyCleared() {
        for (Listener l : listeners) {
            l.onCleared();
        }
    }

    private void flash(String message, Flash kind) {
        for (Listener l : listeners) {
            l.onFlash(message, kind);
        }
    }
}
