package xyz.ngthav.opencheckin.ui.dashboard;

import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.util.StringConverter;
import xyz.ngthav.opencheckin.app.AppState;
import xyz.ngthav.opencheckin.card.CardExporter;
import xyz.ngthav.opencheckin.card.CardPrinter;
import xyz.ngthav.opencheckin.card.CardRenderer;
import xyz.ngthav.opencheckin.model.Member;
import xyz.ngthav.opencheckin.model.Room;

import java.util.List;
import java.util.Locale;

/**
 * The QR-card studio shared by both dashboards: a member selector with type-to-filter search,
 * a credit-card preview, and Save-to-PDF / Print
 * buttons. Built fresh per dashboard render, so it always reflects the active room.
 */
final class CardStudio {

    private final Room room;
    private final List<Member> allMembers;
    private final ComboBox<Member> selector = new ComboBox<>();
    private final StackPane previewHolder = new StackPane();
    private final Button saveButton = new Button("Save to PDF");
    private final Button printButton = new Button("Print");
    private final VBox root;

    CardStudio(AppState app) {
        this.room = app.currentRoom();
        this.allMembers = app.members().findByRoom(room.id());
        this.root = build();
    }

    Region getView() {
        return root;
    }

    private VBox build() {
        Label title = new Label("Member card");
        title.getStyleClass().add("card-title");

        TextField search = new TextField();
        search.setPromptText("Search members…");
        search.textProperty().addListener((obs, was, now) -> applyFilter(now));

        selector.setItems(FXCollections.observableArrayList(allMembers));
        selector.setMaxWidth(Double.MAX_VALUE);
        selector.setPromptText(allMembers.isEmpty() ? "No members yet" : "Select a member");
        selector.setConverter(new StringConverter<>() {
            @Override
            public String toString(Member m) {
                return m == null ? "" : m.name();
            }

            @Override
            public Member fromString(String s) {
                return null;
            }
        });
        selector.getSelectionModel().selectedItemProperty().addListener((obs, was, now) -> updatePreview(now));

        previewHolder.getStyleClass().add("card-preview");
        previewHolder.setMinHeight(CardRenderer.CARD_HEIGHT_PX + 16);

        saveButton.getStyleClass().add("secondary-button");
        printButton.getStyleClass().add("secondary-button");
        saveButton.setOnAction(e -> {
            Member m = selector.getValue();
            if (m != null) {
                CardExporter.saveToPdf(m, room, windowOf(saveButton));
            }
        });
        printButton.setOnAction(e -> {
            Member m = selector.getValue();
            if (m != null) {
                CardPrinter.print(m, room, windowOf(printButton));
            }
        });
        HBox buttons = new HBox(10, saveButton, printButton);
        buttons.setAlignment(Pos.CENTER_LEFT);

        if (!allMembers.isEmpty()) {
            selector.getSelectionModel().selectFirst();
        } else {
            updatePreview(null);
        }

        VBox box = new VBox(12, title, search, selector, previewHolder, buttons);
        box.getStyleClass().add("card");
        return box;
    }

    private void applyFilter(String text) {
        String q = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
        List<Member> filtered = allMembers.stream()
                .filter(m -> q.isEmpty()
                        || m.name().toLowerCase(Locale.ROOT).contains(q)
                        || (m.description() != null && m.description().toLowerCase(Locale.ROOT).contains(q)))
                .toList();
        Member current = selector.getValue();
        selector.setItems(FXCollections.observableArrayList(filtered));
        if (current != null && filtered.contains(current)) {
            selector.getSelectionModel().select(current);
        } else if (filtered.size() == 1) {
            selector.getSelectionModel().selectFirst();
        } else {
            selector.getSelectionModel().clearSelection();
            updatePreview(null);
        }
    }

    private void updatePreview(Member member) {
        boolean has = member != null;
        saveButton.setDisable(!has);
        printButton.setDisable(!has);
        Node content = has
                ? CardRenderer.render(member, room)
                : placeholder();
        previewHolder.getChildren().setAll(content);
    }

    private static Node placeholder() {
        Label label = new Label("Select a member to preview their card");
        label.getStyleClass().add("empty-subtitle");
        return label;
    }

    private static Window windowOf(Node node) {
        return node.getScene() == null ? null : node.getScene().getWindow();
    }
}
