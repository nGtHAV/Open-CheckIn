package xyz.ngthav.opencheckin.card;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.control.Label;
import xyz.ngthav.opencheckin.model.Member;
import xyz.ngthav.opencheckin.model.Room;
import xyz.ngthav.opencheckin.qr.QrEncoder;
import xyz.ngthav.opencheckin.qr.QrPayload;
import xyz.ngthav.opencheckin.util.Images;

/**
 * Builds the single portrait card node that feeds the on-screen preview, the PDF and the printer
 * alike — so all three look identical. It's deliberately simple: a big QR code centred on the card
 * with the member's name underneath. Styling is inline and the node has an explicit size, so it
 * renders faithfully even when snapshotted off-scene.
 */
public final class CardRenderer {

    // Physical card dimensions (portrait) — used to size the PDF page and compute print scale.
    public static final double CARD_WIDTH_MM = 53.98;
    public static final double CARD_HEIGHT_MM = 85.6;

    // On-screen pixel size, holding the same portrait aspect ratio (53.98 / 85.6 ≈ 0.631).
    public static final double CARD_WIDTH_PX = 290;
    public static final double CARD_HEIGHT_PX = 460;

    private static final int QR_PX = 230;

    private CardRenderer() {
    }

    /** Builds a fresh card node for the given member. A new node each call keeps callers independent. */
    public static Region render(Member member, Room room) {
        Label roomName = new Label(room == null ? "" : room.name());
        roomName.setStyle("-fx-font-size: 13px; -fx-font-weight: bold; -fx-text-fill: #3B6EA5;");
        roomName.setWrapText(true);
        roomName.setMaxWidth(CARD_WIDTH_PX - 32);
        roomName.setAlignment(Pos.CENTER);

        ImageView qr = buildQr(member, room);

        Label name = new Label(member.name());
        name.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #1F2430;");
        name.setWrapText(true);
        name.setMaxWidth(CARD_WIDTH_PX - 32);
        name.setAlignment(Pos.CENTER);

        VBox qrAndName = new VBox(14, qr, name);
        qrAndName.setAlignment(Pos.CENTER);
        VBox.setVgrow(qrAndName, Priority.ALWAYS);

        Label wordmark = new Label("Open-CheckIn");
        wordmark.setStyle("-fx-font-size: 9px; -fx-text-fill: #9AA1AC;");

        VBox content = new VBox(12, roomName, qrAndName, wordmark);
        content.setAlignment(Pos.CENTER);
        content.setPadding(new Insets(16));
        content.setFillWidth(true);

        StackPane card = new StackPane(content);
        card.setStyle(
                "-fx-background-color: white;"
                        + "-fx-background-radius: 14;"
                        + "-fx-border-color: #E7E9EE;"
                        + "-fx-border-radius: 14;"
                        + "-fx-border-width: 1;");
        card.setPrefSize(CARD_WIDTH_PX, CARD_HEIGHT_PX);
        card.setMinSize(CARD_WIDTH_PX, CARD_HEIGHT_PX);
        card.setMaxSize(CARD_WIDTH_PX, CARD_HEIGHT_PX);
        return card;
    }

    private static ImageView buildQr(Member member, Room room) {
        long roomId = room == null ? member.roomId() : room.id();
        QrPayload payload = new QrPayload(roomId, member.uuid());
        Image qrImage = Images.toFxImage(QrEncoder.encode(payload, 460));
        ImageView view = new ImageView(qrImage);
        view.setFitWidth(QR_PX);
        view.setFitHeight(QR_PX);
        view.setPreserveRatio(true);
        view.setSmooth(true);
        return view;
    }
}
