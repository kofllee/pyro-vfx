package net.kofllee.pyroui;

import java.util.List;
import java.util.function.IntConsumer;

/** Per-screen interaction state. No Minecraft imports in the library. */
public final class UiContext {
    public final UiTheme theme;
    public UiElement focus;
    public UiElement capture;
    public double mouseX, mouseY;
    public int frameWidth, frameHeight;
    public java.util.function.ToIntFunction<String> measure = s -> s.length() * 6;
    public java.util.function.ToIntFunction<String> measureHeading = s -> s.length() * 9;
    public java.util.function.Supplier<String> readClipboard = () -> "";
    public java.util.function.Consumer<String> writeClipboard = value -> {};
    public int fontHeight = 15;
    /** Skip nonessential stroke passes while moving/resizing windows. */
    public boolean lowDetail;

    private Menu popup, exitingMenu;
    private long popupDismissedAt;
    public record Menu(UiRect rect, List<String> options, IntConsumer pick, long openedAt) {}

    public UiContext(UiTheme theme) { this.theme = theme; }

    public void menu(UiRect anchor, List<String> options, IntConsumer pick) {
        int itemHeight = 24;
        int width = Math.max(142, anchor.w());
        UiRect rect = new UiRect(anchor.x(), anchor.y() + anchor.h(),
                width, options.size() * itemHeight + 8);
        int x = UiRect.clamp(rect.x(), 2, Math.max(2, frameWidth - rect.w() - 2));
        int y = rect.y() + rect.h() > frameHeight
                ? Math.max(2, anchor.y() - rect.h()) : rect.y();
        popup = new Menu(new UiRect(x,y,rect.w(),rect.h()),List.copyOf(options),
                pick,System.nanoTime());
        exitingMenu = null;
    }
    public boolean popupVisible() { return popup != null; }
    public UiRect popupBounds() { return popup == null ? null : popup.rect(); }
    public void dismissPopup() {
        if (popup != null) {
            exitingMenu = popup;
            popupDismissedAt = System.nanoTime();
            popup = null;
        }
    }
    public void drawPopup(UiCanvas c) {
        if (popup != null) {
            float alpha = theme.out(theme.progress(popup.openedAt, 160));
            paintMenu(c, popup, alpha, Math.round((1-alpha)*-4));
        } else if (exitingMenu != null) {
            float remaining = 1f-theme.fade(popupDismissedAt,120);
            if (remaining <= 0) exitingMenu = null;
            else paintMenu(c,exitingMenu,remaining,Math.round((1-remaining)*-3));
        }
    }
    private void paintMenu(UiCanvas c, Menu menu, float alpha, int offsetY) {
        UiRect box = menu.rect;
        c.pushLayer(alpha,offsetY);
        c.rounded(new UiRect(box.x()+4,box.y()+6,box.w(),box.h()),
                UiTheme.R_FLOAT,0xA0000000);
        c.rounded(box,UiTheme.R_PANEL,UiTheme.ELEVATED);
        c.roundedBorder(box,UiTheme.R_PANEL,0xFF464646);
        int step = 24;
        for (int i=0;i<menu.options.size();i++) {
            UiRect row = new UiRect(box.x()+5,box.y()+4+i*step,box.w()-10,step);
            boolean over = popup != null && row.contains(mouseX,mouseY);
            if (over) c.rounded(row,UiTheme.R_CONTROL,UiTheme.HOVER);
            c.textClipped(menu.options.get(i),row.x()+11,
                    row.y()+(step-fontHeight)/2,row.w()-20,
                    over ? UiTheme.TEXT : UiTheme.SECONDARY);
        }
        c.popLayer();
    }
    public boolean popupClick(double x,double y) {
        if (popup == null) return false;
        Menu old = popup;
        dismissPopup();
        if (old.rect.contains(x,y)) {
            int selected = (int) (y-old.rect.y()-4) / 24;
            if (selected >= 0 && selected < old.options.size())
                old.pick.accept(selected);
        }
        return true;
    }
    public void setFocus(UiElement element) {
        if (focus != element) {
            if (focus != null) focus.blur(this);
            focus = element;
        }
    }
}
