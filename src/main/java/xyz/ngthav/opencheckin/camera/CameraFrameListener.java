package xyz.ngthav.opencheckin.camera;

import java.awt.image.BufferedImage;

/**
 * Receives camera frames on the capture background thread (never the FX thread). Two listeners
 * typically attach: the live-preview ImageView and the QR decoder.
 */
@FunctionalInterface
public interface CameraFrameListener {
    void onFrame(BufferedImage frame);
}
