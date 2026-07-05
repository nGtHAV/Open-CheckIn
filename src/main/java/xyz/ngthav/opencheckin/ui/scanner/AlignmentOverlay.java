package xyz.ngthav.opencheckin.ui.scanner;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;

/**
 * A centered reticle / bracket frame drawn on top of the live camera, telling the user where to
 * hold the QR card. Mouse-transparent so it never intercepts clicks; redraws itself
 * as the camera view resizes.
 */
public final class AlignmentOverlay extends Region {

    private final Canvas canvas = new Canvas();

    public AlignmentOverlay() {
        getChildren().add(canvas);
        setMouseTransparent(true);
    }

    @Override
    protected void layoutChildren() {
        double w = getWidth();
        double h = getHeight();
        canvas.setWidth(w);
        canvas.setHeight(h);
        draw(w, h);
    }

    private void draw(double w, double h) {
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.clearRect(0, 0, w, h);
        if (w <= 0 || h <= 0) {
            return;
        }
        // A centered square target, ~60% of the smaller dimension.
        double side = Math.min(w, h) * 0.6;
        double x = (w - side) / 2;
        double y = (h - side) / 2;
        double corner = side * 0.18;

        g.setStroke(Color.web("#5BD1A6")); // bright mint — glanceable from a distance
        g.setLineWidth(4);

        // top-left
        g.strokeLine(x, y, x + corner, y);
        g.strokeLine(x, y, x, y + corner);
        // top-right
        g.strokeLine(x + side - corner, y, x + side, y);
        g.strokeLine(x + side, y, x + side, y + corner);
        // bottom-left
        g.strokeLine(x, y + side - corner, x, y + side);
        g.strokeLine(x, y + side, x + corner, y + side);
        // bottom-right
        g.strokeLine(x + side - corner, y + side, x + side, y + side);
        g.strokeLine(x + side, y + side - corner, x + side, y + side);
    }
}
