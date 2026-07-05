package xyz.ngthav.opencheckin.qr;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.LuminanceSource;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;

import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * QR {@link BufferedImage} → decoded text. Runs on the camera background thread,
 * never on the FX thread. A frame with no QR is completely normal — it returns
 * {@link Optional#empty()} rather than logging spam.
 */
public final class QrDecoder {

    private static final Map<DecodeHintType, Object> HINTS = new EnumMap<>(DecodeHintType.class);

    static {
        HINTS.put(DecodeHintType.POSSIBLE_FORMATS, List.of(BarcodeFormat.QR_CODE));
    }

    private QrDecoder() {
    }

    public static Optional<String> decode(BufferedImage frame) {
        if (frame == null) {
            return Optional.empty();
        }
        LuminanceSource source = new BufferedImageLuminanceSource(frame);
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
        // A fresh reader per call keeps this stateless and safe on the capture thread.
        MultiFormatReader reader = new MultiFormatReader();
        try {
            Result result = reader.decode(bitmap, HINTS);
            return Optional.ofNullable(result.getText());
        } catch (NotFoundException e) {
            return Optional.empty(); // no QR in this frame — expected, not an error
        }
    }
}
