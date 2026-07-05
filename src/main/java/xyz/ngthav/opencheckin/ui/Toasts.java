package xyz.ngthav.opencheckin.ui;

import javafx.geometry.Pos;
import javafx.stage.Window;
import javafx.util.Duration;
import org.controlsfx.control.Notifications;
import xyz.ngthav.opencheckin.checkin.CheckInService;

/**
 * Transient corner toasts for the "already checked in / wrong room / checked in" flashes
 * (ControlsFX Notifications). Best-effort: never lets a toast failure bubble up
 * into the scan flow. Call on the FX thread.
 */
public final class Toasts {

    private Toasts() {
    }

    public static void flash(String message, CheckInService.Flash kind, Window owner) {
        try {
            Notifications n = Notifications.create()
                    .text(message)
                    .hideAfter(Duration.seconds(2.5))
                    .position(Pos.BOTTOM_RIGHT);
            if (owner != null) {
                n.owner(owner);
            }
            switch (kind) {
                case SUCCESS -> n.showConfirm();
                case WARN -> n.showWarning();
                case INFO -> n.showInformation();
            }
        } catch (RuntimeException ignored) {
            // A toast is cosmetic; never disrupt the scan flow if it can't be shown.
        }
    }

    public static void flash(String message, CheckInService.Flash kind) {
        flash(message, kind, null);
    }
}
