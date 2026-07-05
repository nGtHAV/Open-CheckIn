package xyz.ngthav.opencheckin.card;

import javafx.print.PrinterJob;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.layout.Region;
import javafx.stage.Window;
import xyz.ngthav.opencheckin.model.Member;
import xyz.ngthav.opencheckin.model.Room;

/**
 * Card → native print via JavaFX {@link PrinterJob}. Shows the OS print dialog,
 * prints the single card node, and ends the job. Must be called on the FX thread.
 */
public final class CardPrinter {

    private CardPrinter() {
    }

    /** Returns {@code true} if a job was created and printed, {@code false} if the user cancelled. */
    public static boolean print(Member member, Room room, Window owner) {
        Region card = CardRenderer.render(member, room);
        // Attach off-screen so the node is laid out before printing.
        new Scene(new Group(card));
        card.applyCss();
        card.layout();

        PrinterJob job = PrinterJob.createPrinterJob();
        if (job == null) {
            return false; // no printers available
        }
        if (!job.showPrintDialog(owner)) {
            return false; // cancelled
        }
        boolean printed = job.printPage(card);
        if (printed) {
            job.endJob();
        }
        return printed;
    }
}
