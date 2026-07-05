package xyz.ngthav.opencheckin.camera;

import com.github.sarxos.webcam.Webcam;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Owns exactly one open webcam and streams frames to listeners on a background thread.
 *
 * <p><b>Degrades gracefully.</b> A physical webcam can only be opened by one consumer at a time, so
 * the scanner window is the sole camera owner. With no device present — or a busy/failing one —
 * nothing throws: {@link #list()} returns empty and {@link #start} returns {@code false}. Native
 * errors (missing bridj, {@code UnsatisfiedLinkError}) are caught as {@link Throwable}.
 *
 * <p>All frame delivery happens off the FX thread; listeners must marshal any UI work themselves.
 */
public final class CameraService {

    private static final Dimension VIEW_SIZE = new Dimension(640, 480);
    private static final long FRAME_INTERVAL_MS = 40;

    private final CopyOnWriteArrayList<CameraFrameListener> listeners = new CopyOnWriteArrayList<>();

    private volatile Webcam webcam;
    private volatile boolean streaming;
    private ExecutorService captureExecutor;

    public void addListener(CameraFrameListener listener) {
        listeners.add(listener);
    }

    public void removeListener(CameraFrameListener listener) {
        listeners.remove(listener);
    }

    /** Names of available cameras for the "select camera source" dropdown; empty if none/unavailable. */
    public List<String> list() {
        try {
            return Webcam.getWebcams().stream().map(Webcam::getName).toList();
        } catch (Throwable t) {
            return List.of(); // no camera stack on this machine — that's fine
        }
    }

    public boolean isStreaming() {
        return streaming && webcam != null;
    }

    /**
     * Opens the named camera (or the default when {@code deviceName} is null) and starts streaming.
     * Closes any currently-open device first. Returns {@code false} if no camera could be opened.
     */
    public boolean start(String deviceName) {
        stop();
        Webcam cam = resolve(deviceName);
        if (cam == null) {
            return false;
        }
        try {
            cam.setViewSize(VIEW_SIZE);
            cam.open();
        } catch (Throwable t) {
            return false; // busy / not found / native failure — caller shows the "no camera" state
        }
        webcam = cam;
        streaming = true;
        captureExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "camera-capture");
            thread.setDaemon(true);
            return thread;
        });
        captureExecutor.submit(this::captureLoop);
        return true;
    }

    private void captureLoop() {
        while (streaming) {
            Webcam cam = webcam;
            if (cam == null || !cam.isOpen()) {
                break;
            }
            BufferedImage frame;
            try {
                frame = cam.getImage();
            } catch (Throwable t) {
                frame = null; // transient read failure; try again next tick
            }
            if (frame != null) {
                for (CameraFrameListener l : listeners) {
                    try {
                        l.onFrame(frame);
                    } catch (RuntimeException ignored) {
                        // one bad listener must not kill the capture loop
                    }
                }
            }
            try {
                Thread.sleep(FRAME_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    /** Stops streaming and releases the camera. Safe to call repeatedly (idempotent). */
    public void stop() {
        streaming = false;
        if (captureExecutor != null) {
            captureExecutor.shutdownNow();
            captureExecutor = null;
        }
        Webcam cam = webcam;
        webcam = null;
        if (cam != null) {
            try {
                if (cam.isOpen()) {
                    cam.close();
                }
            } catch (Throwable ignored) {
                // best-effort release
            }
        }
    }

    private static Webcam resolve(String deviceName) {
        try {
            if (deviceName != null) {
                for (Webcam w : Webcam.getWebcams()) {
                    if (w.getName().equals(deviceName)) {
                        return w;
                    }
                }
            }
            return Webcam.getDefault();
        } catch (Throwable t) {
            return null;
        }
    }
}
