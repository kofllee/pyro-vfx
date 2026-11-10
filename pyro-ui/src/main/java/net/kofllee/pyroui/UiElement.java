package net.kofllee.pyroui;

import java.util.ArrayList;
import java.util.List;

/**
 * Minimal retained-mode element tree. Host supplies mouse/keyboard events and
 * one Canvas; layout is resolved when a window's bounds change.
 */
public class UiElement {
    public enum Direction { COLUMN, ROW, LEAF }

    protected UiRect bounds = new UiRect(0, 0, 0, 0);
    protected final List<UiElement> children = new ArrayList<>();
    protected Direction direction = Direction.LEAF;
    protected int preferredWidth = 120, preferredHeight = 24, flex = 0;
    protected int padding = 0, gap = 0, scrollY = 0, contentHeight = 0;
    protected boolean scrollable, visible = true, enabled = true;
    protected String tooltip;
    protected UiContext context;

    public static UiElement column(int gap, int padding) {
        UiElement e = new UiElement();
        e.direction = Direction.COLUMN;
        e.gap = gap;
        e.padding = padding;
        return e;
    }
    public static UiElement row(int gap, int padding) {
        UiElement e = new UiElement();
        e.direction = Direction.ROW;
        e.gap = gap;
        e.padding = padding;
        return e;
    }
    public UiElement add(UiElement child) {
        children.add(child);
        return this;
    }
    public UiElement clear() { children.clear(); scrollY = 0; return this; }
    public UiElement height(int value) { preferredHeight = value; return this; }
    public UiElement width(int value) { preferredWidth = value; return this; }
    public UiElement grow(int value) { flex = Math.max(0, value); return this; }
    public UiElement scroll() { scrollable = true; return this; }
    public UiElement enabled(boolean value) { enabled = value; return this; }
    public UiElement visible(boolean value) { visible = value; return this; }
    public UiElement tooltip(String value) { tooltip = value; return this; }
    public String tooltip() { return tooltip; }
    public UiRect bounds() { return bounds; }
    public void collectFocusable(List<UiElement> out) {
        collectFocusable(out, bounds);
    }
    private void collectFocusable(List<UiElement> out, UiRect visibleClip) {
        if (!visible || !enabled) return;
        UiRect intersection = bounds.intersect(visibleClip);
        if (intersection.w() <= 0 || intersection.h() <= 0) return;
        if (focusable()) out.add(this);
        for (UiElement child : children) child.collectFocusable(out, intersection);
    }
    public int preferredHeight() { return preferredHeight; }
    public boolean focusable() { return false; }
    public void blur(UiContext ctx) {}
    public void onPointerMove(UiContext ctx, double x, double y) {}
    public void onPointerUp(UiContext ctx, double x, double y, int button) {}
    public boolean onPointerDown(UiContext ctx, double x, double y, int button) { return false; }
    public boolean onKey(UiContext ctx, int key, int modifiers) { return false; }
    public boolean onChar(UiContext ctx, char c) { return false; }
    public boolean onScroll(UiContext ctx, double amount) { return false; }
    protected void drawSelf(UiCanvas c, UiContext ctx) {}

    public void layout(UiRect r, UiContext ctx) {
        bounds = r;
        context = ctx;
        if (direction == Direction.LEAF || children.isEmpty()) return;
        int n = 0, fixed = 0, weight = 0;
        for (var child : children) if (child.visible) {
            n++;
            if (child.flex == 0) fixed += direction == Direction.COLUMN ? child.preferredHeight : child.preferredWidth;
            else weight += child.flex;
        }
        int main = (direction == Direction.COLUMN ? r.h() : r.w()) - 2 * padding;
        int cross = Math.max(0, (direction == Direction.COLUMN ? r.w() : r.h()) - 2 * padding);
        int availableCells = Math.max(0, main - Math.max(0, n - 1) * gap);
        int remaining = Math.max(0, availableCells - fixed);
        // In narrow rows fixed-width labels and fields used to paint outside
        // their assigned panel. Share the deficit instead of overlapping.
        boolean squeeze = direction == Direction.ROW && fixed > availableCells;
        int leftFixed = fixed, fixedSpace = availableCells;
        int start = (direction == Direction.COLUMN ? r.y() : r.x()) + padding;
        int running = start - (scrollable ? scrollY : 0);
        int leftWeight = weight, leftFree = remaining;
        for (var child : children) {
            if (!child.visible) continue;
            int size;
            if (child.flex > 0) {
                size = leftWeight == 0 ? 0 : (int) ((long) leftFree * child.flex / leftWeight);
                leftFree -= size;
                leftWeight -= child.flex;
            } else {
                int preferred = direction == Direction.COLUMN ? child.preferredHeight : child.preferredWidth;
                size = squeeze && leftFixed > 0 ? (int) ((long) fixedSpace * preferred / leftFixed) : preferred;
                if (squeeze) {
                    leftFixed -= preferred;
                    fixedSpace -= size;
                }
            }
            size = Math.max(0, size);
            UiRect next = direction == Direction.COLUMN
                    ? new UiRect(r.x() + padding, running, cross, size)
                    : new UiRect(running, r.y() + padding, size, cross);
            child.layout(next, ctx);
            running += size + gap;
        }
        // 'running' contains the negative scroll offset. Never feed that
        // offset back into the content extent or the maximum shrinks with
        // every wheel tick and the scrollbar reaches the bottom too early.
        contentHeight = Math.max(0,running - start + (scrollable ? scrollY : 0)
                - (n == 0 ? 0 : gap));
        if (scrollable) {
            int maxScroll = Math.max(0, contentHeight - main);
            if (scrollY > maxScroll) {
                scrollY = maxScroll;
                layout(r, ctx);
            }
        }
    }

    public final void paint(UiCanvas c, UiContext ctx) {
        if (!visible || bounds.w() == 0 || bounds.h() == 0) return;
        // Every container clips its children; scrolling is only a layout/input feature.
        // This prevents Inspector controls from painting over Curve Editor.
        c.clip(bounds);
        drawSelf(c, ctx);
        if (direction != Direction.LEAF) {
            for (var child : children) child.paint(c, ctx);
        }
        if (scrollable && bounds.w() > 12 && bounds.h() > 30) {
            int viewport = scrollViewport();
            int overflow = contentHeight - viewport;
            if (overflow > 1) {
                int available = bounds.h()-12;
                int thumb = UiRect.clamp((int)((long)available*viewport/
                        Math.max(1,contentHeight)),20,available);
                int top = bounds.y()+6 +
                        (int)((long)(available-thumb)*scrollY/Math.max(1,overflow));
                c.rounded(new UiRect(bounds.x()+bounds.w()-5,bounds.y()+6,3,available),
                        2,0xFF272727);
                c.rounded(new UiRect(bounds.x()+bounds.w()-5,top,3,thumb),
                        2,UiTheme.HOVER);
            }
        }
        c.unclip();
    }

    /** Returns the deepest actionable element whose clipped area owns the point. */
    public UiElement hit(double x, double y) {
        if (!visible || !enabled || !bounds.contains(x, y)) return null;
        for (int i = children.size() - 1; i >= 0; i--) {
            UiElement target = children.get(i).hit(x, y);
            if (target != null) return target;
        }
        return direction == Direction.LEAF ? this : null;
    }

    public boolean wheel(UiContext ctx, double x, double y, double amount) {
        if (!visible || !bounds.contains(x, y)) return false;
        for (int i = children.size() - 1; i >= 0; i--) {
            if (children.get(i).wheel(ctx, x, y, amount)) return true;
        }
        if (onScroll(ctx, amount)) return true;
        if (scrollable) {
            int maxScroll=Math.max(0,contentHeight-scrollViewport());
            if(maxScroll>0) {
                int updated=UiRect.clamp(scrollY-(int)Math.round(amount*18),0,maxScroll);
                if(updated!=scrollY) {
                    scrollY=updated;
                    layout(bounds,ctx);
                }
                return true;
            }
        }
        return false;
    }
    private int scrollViewport() {
        int span=direction==Direction.ROW?bounds.w():bounds.h();
        return Math.max(1,span-2*padding);
    }
}
