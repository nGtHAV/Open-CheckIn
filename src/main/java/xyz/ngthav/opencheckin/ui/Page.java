package xyz.ngthav.opencheckin.ui;

import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import xyz.ngthav.opencheckin.app.AppState;

/**
 * Base class for the four room-scoped pages. Every page shows the shared "create a room to get
 * started" empty state when there is no active room, and its own content otherwise — so no page
 * ever NPEs on a null room. {@link #refresh()} rebuilds the content, and is called
 * on navigation and whenever the active room changes.
 */
public abstract class Page {

    protected final AppState app;
    /** Wires the empty-state call-to-action to the same "New room…" flow the sidebar uses. */
    protected final Runnable onCreateRoom;
    private final StackPane root = new StackPane();

    protected Page(AppState app, Runnable onCreateRoom) {
        this.app = app;
        this.onCreateRoom = onCreateRoom;
        root.getStyleClass().add("page");
    }

    public Node getView() {
        return root;
    }

    /** Rebuilds the page: empty state when there is no room, real content otherwise. */
    public final void refresh() {
        if (app.hasRoom()) {
            root.getChildren().setAll(buildContent());
        } else {
            root.getChildren().setAll(EmptyState.node(onCreateRoom));
        }
    }

    /** Builds the page content; only called when there IS an active room (guarded above). */
    protected abstract Node buildContent();

    public abstract String title();
}
