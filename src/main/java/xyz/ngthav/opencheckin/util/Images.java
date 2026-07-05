package xyz.ngthav.opencheckin.util;

import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Node;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.scene.transform.Scale;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;

/**
 * BufferedImage &lt;-&gt; JavaFX Image bridging (via {@code SwingFXUtils}) plus snapshot helpers
 * for the card renderer.
 */
public final class Images {

    private Images() {
    }

    public static Image toFxImage(BufferedImage bi) {
        return SwingFXUtils.toFXImage(bi, null);
    }

    public static BufferedImage fromFxImage(Image img) {
        return SwingFXUtils.fromFXImage(img, null);
    }

    /** Reads any image file supported by ImageIO. Returns {@code null} if it cannot be decoded. */
    public static BufferedImage read(Path file) {
        try {
            return ImageIO.read(file.toFile());
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read image: " + file, e);
        }
    }

    public static void writePng(BufferedImage bi, Path dest) {
        try {
            Files.ensureDir(dest.getParent());
            ImageIO.write(bi, "png", dest.toFile());
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write PNG: " + dest, e);
        }
    }

    /** Reads {@code source} (any format) and re-encodes it as a PNG at {@code dest}. */
    public static void copyAsPng(Path source, Path dest) {
        BufferedImage bi = read(source);
        if (bi == null) {
            throw new IllegalArgumentException("Unsupported image file: " + source);
        }
        writePng(bi, dest);
    }

    /**
     * Snapshots a JavaFX node at a resolution multiplier so the exported card stays crisp
     * (render at ~300 DPI by passing an appropriate {@code scale}). Must run on
     * the FX thread.
     */
    public static WritableImage snapshotAtScale(Node node, double scale) {
        SnapshotParameters params = new SnapshotParameters();
        params.setTransform(new Scale(scale, scale));
        // White fill so a card's rounded-corner transparency doesn't export as black.
        params.setFill(Color.WHITE);
        return node.snapshot(params, null);
    }
}
