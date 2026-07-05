package xyz.ngthav.opencheckin.app;

import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import xyz.ngthav.opencheckin.ui.Page;

import java.util.EnumMap;
import java.util.Map;

/**
 * Swaps the active page into the content area. Each page keeps a stable root node
 * whose children are rebuilt on {@link Page#refresh()}, so switching rooms just refreshes the
 * page currently on screen.
 */
public final class Navigator {

    public enum PageId {
        DASHBOARD, STATUS, MEMBER, SETTING
    }

    private final StackPane content = new StackPane();
    private final Map<PageId, Page> pages = new EnumMap<>(PageId.class);
    private PageId current;

    public Region getContent() {
        return content;
    }

    public void register(PageId id, Page page) {
        pages.put(id, page);
    }

    public void show(PageId id) {
        Page page = pages.get(id);
        if (page == null) {
            return;
        }
        current = id;
        page.refresh();
        content.getChildren().setAll(page.getView());
    }

    public PageId current() {
        return current;
    }

    /** Re-renders the page on screen — called when the active room (or its settings) changes. */
    public void refreshCurrent() {
        if (current != null) {
            pages.get(current).refresh();
        }
    }
}
