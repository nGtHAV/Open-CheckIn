package xyz.ngthav.opencheckin.card;

import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Region;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import xyz.ngthav.opencheckin.model.Member;
import xyz.ngthav.opencheckin.model.Room;
import xyz.ngthav.opencheckin.util.Files;
import xyz.ngthav.opencheckin.util.Images;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;

/**
 * Card → PDF, sized to the physical card (PDFBox 3.x). The node is snapshotted at
 * ~300 DPI so the QR and text stay crisp, then drawn to fill a CR80-sized page.
 *
 * <p>Must be called on the FX thread (it snapshots a live node).
 */
public final class CardExporter {

    private static final double EXPORT_DPI = 300;
    /** points per millimetre (72 pt / 25.4 mm). */
    private static final double PT_PER_MM = 72.0 / 25.4;

    private CardExporter() {
    }

    /** Shows a Save dialog defaulting to the member's name, then writes the PDF. No-op if cancelled. */
    public static void saveToPdf(Member member, Room room, Window owner) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Save card as PDF");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF", "*.pdf"));
        chooser.setInitialFileName(Files.sanitizeFilename(member.name()) + "-card.pdf");
        java.io.File file = chooser.showSaveDialog(owner);
        if (file != null) {
            writePdf(member, room, file.toPath());
        }
    }

    /** Renders the member's card and writes it to {@code dest} as a single-page CR80 PDF. */
    public static void writePdf(Member member, Room room, Path dest) {
        Region card = CardRenderer.render(member, room);
        // Attach to an off-screen scene so layout/inline styling are applied before snapshot.
        new Scene(new Group(card));
        card.applyCss();
        card.layout();

        double scale = (CardRenderer.CARD_WIDTH_MM / 25.4 * EXPORT_DPI) / CardRenderer.CARD_WIDTH_PX;
        WritableImage fxImage = Images.snapshotAtScale(card, scale);
        BufferedImage buffered = Images.fromFxImage(fxImage);

        float pageWidth = (float) (CardRenderer.CARD_WIDTH_MM * PT_PER_MM);
        float pageHeight = (float) (CardRenderer.CARD_HEIGHT_MM * PT_PER_MM);

        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(new PDRectangle(pageWidth, pageHeight));
            doc.addPage(page);
            PDImageXObject image = LosslessFactory.createFromImage(doc, buffered);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.drawImage(image, 0, 0, pageWidth, pageHeight);
            }
            doc.save(dest.toFile());
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write card PDF: " + dest, e);
        }
    }
}
