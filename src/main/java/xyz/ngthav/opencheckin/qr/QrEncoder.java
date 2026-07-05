package xyz.ngthav.opencheckin.qr;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.EnumMap;
import java.util.Map;

/**
 * Text → QR {@link BufferedImage}. Error correction {@code M} survives print
 * smudging; a small quiet-zone margin keeps scanners happy.
 */
public final class QrEncoder {

    private static final Map<EncodeHintType, Object> HINTS = new EnumMap<>(EncodeHintType.class);

    static {
        HINTS.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
        HINTS.put(EncodeHintType.MARGIN, 1);
        HINTS.put(EncodeHintType.CHARACTER_SET, "UTF-8");
    }

    private QrEncoder() {
    }

    public static BufferedImage encode(String text, int size) {
        try {
            BitMatrix matrix = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, HINTS);
            return MatrixToImageWriter.toBufferedImage(matrix);
        } catch (WriterException e) {
            throw new IllegalArgumentException("Could not encode QR for: " + text, e);
        }
    }

    public static BufferedImage encode(QrPayload payload, int size) {
        return encode(payload.format(), size);
    }

    /** PNG bytes for embedding (e.g. into a PDF or a data URL). */
    public static byte[] toPngBytes(BufferedImage image) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write QR PNG bytes", e);
        }
    }
}
