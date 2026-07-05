package xyz.ngthav.opencheckin.app;

import javafx.scene.layout.BorderPane;
import xyz.ngthav.opencheckin.ui.Page;
import xyz.ngthav.opencheckin.ui.Sidebar;
import xyz.ngthav.opencheckin.ui.dashboard.DashboardView;
import xyz.ngthav.opencheckin.ui.member.MemberView;
import xyz.ngthav.opencheckin.ui.settings.SettingsView;
import xyz.ngthav.opencheckin.ui.status.StatusView;

/**
 * The window frame: sidebar on the left, swappable content on the right. Wires the
 * pages into the navigator and refreshes the visible page whenever the active room changes (so
 * switching rooms — or dropping to the empty state — re-renders every page correctly).
 */
public final class AppShell {

    private final BorderPane root = new BorderPane();

    public AppShell(AppState app) {
        Navigator navigator = new Navigator();
        Sidebar sidebar = new Sidebar(app, navigator);

        // Empty-state CTA and sidebar "＋" share one create-room flow.
        Runnable onCreateRoom = sidebar::openNewRoomDialog;

        Page dashboard = new DashboardView(app, onCreateRoom);
        Page status = new StatusView(app, onCreateRoom);
        Page member = new MemberView(app, onCreateRoom);
        Page settings = new SettingsView(app, onCreateRoom);

        navigator.register(Navigator.PageId.DASHBOARD, dashboard);
        navigator.register(Navigator.PageId.STATUS, status);
        navigator.register(Navigator.PageId.MEMBER, member);
        navigator.register(Navigator.PageId.SETTING, settings);

        root.getStyleClass().add("app-shell");
        root.setLeft(sidebar.getView());
        root.setCenter(navigator.getContent());

        // Any change to the active room re-renders whatever page is on screen.
        app.currentRoomProperty().addListener((obs, was, now) -> navigator.refreshCurrent());

        navigator.show(Navigator.PageId.DASHBOARD);
    }

    public BorderPane getRoot() {
        return root;
    }
}
