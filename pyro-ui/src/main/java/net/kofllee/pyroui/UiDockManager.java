package net.kofllee.pyroui;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Independent rectangular docking zones in one workspace. Zone rectangles may
 * form T-junctions; tabs are unlimited within each zone. PyroScreen converts
 * device inputs into these logical coordinates.
 */
public final class UiDockManager {
    // Window minimum is only large enough for a draggable title/close button.
    // Panel contents can clip/scroll instead of restricting the workspace.
    public static final int MIN_WIDTH = 160, MIN_HEIGHT = 36;

    public static final class Window {
        public final String id, title;
        public UiElement content;
        public int x = 120, y = 100, width = 300, height = 260;
        long openedAt = System.nanoTime();
        public int minDockWidth = MIN_WIDTH, minDockHeight = MIN_HEIGHT;
        public boolean open = true;
        private boolean transparentContent;
        private boolean minecraftViewport;
        private boolean accentedHeader;
        Window(String id, String title, UiElement content) {
            this.id = id; this.title = title; this.content = content;
        }
        public Window at(int x, int y) { this.x = x; this.y = y; return this; }
        public Window size(int w, int h) { width = Math.max(MIN_WIDTH, w); height = Math.max(MIN_HEIGHT, h); return this; }
        public Window dockMinimum(int w, int h) {
            minDockWidth = Math.max(0, w);
            minDockHeight = Math.max(0, h);
            return this;
        }
        public Window transparentContent() { transparentContent = true; return this; }
        /** A normal resizable dockable Minecraft viewport with real game input. */
        public Window minecraftViewport() {
            minecraftViewport=true;
            transparentContent=false;
            return this;
        }
        public Window accentedHeader() { accentedHeader = true; return this; }
    }

    private static final class Tabs {
        final String id;
        final List<Window> windows = new ArrayList<>();
        UiRect rect = new UiRect(0, 0, 0, 0);
        int selected;
        int tabScrollPx;
        boolean manualTabScroll;
        long selectedAt;
        Tabs(String id,Window... entries) {
            this.id=id;
            for (var w : entries) if (w != null) windows.add(w);
        }
        Window active() { return windows.isEmpty() ? null : windows.get(UiRect.clamp(selected, 0, windows.size() - 1)); }
    }
    private record DockHit(String target, UiDockLayout.Side side,
                           UiDockLayout.Edge boundary,boolean preferBefore,
                           int tabInsertionIndex) {
        DockHit(String target,UiDockLayout.Side side) {
            this(target,side,null,false,-1);
        }
        DockHit(String target,UiDockLayout.Side side,
                UiDockLayout.Edge boundary,boolean preferBefore) {
            this(target,side,boundary,preferBefore,-1);
        }
    }
    private record HeaderHit(Window window, Tabs group, boolean close) {}

    private final UiTheme theme;
    private final UiContext ctx;
    private final Map<String, Window> windows = new LinkedHashMap<>();
    private final List<Window> floating = new ArrayList<>();
    private record Closing(Window window, UiRect bounds, long at) {}
    private final List<Closing> closing = new ArrayList<>();
    private UiDockLayout workspace = new UiDockLayout();
    private final Map<String,Tabs> groups = new LinkedHashMap<>();
    private int nextZoneId;
    private int width, height;
    private Window selectedWindow, draggingWindow, resizingWindow, pendingTab;
    private double downX, downY, dragOffsetX, dragOffsetY;
    private int resizeW, resizeH;
    private UiDockLayout.Edge resizingEdge;
    private UiDockLayout resizeStart;
    private DockHit dockPreview;
    private DockPlan previewPlan;
    private long previewStartedAt;
    private long dockHoverStartedAt;
    private boolean dockingIntentArmed;
    private long lastDragTime, lastFastDragTime;
    private double lastDragX,lastDragY;
    private Window hoveredFloating;
    private String hoveredZoneId;
    private record DockPlan(UiDockLayout geometry, Map<String,Tabs> groups,
                            DockHit hit,String newZone) {}
    private java.util.function.LongSupplier clock = System::nanoTime;
    void clockForTesting(java.util.function.LongSupplier source) {clock=source;}
    private static final int TOP_BAR = 26;
    // The shared boundary is the primary docking target, including T-junctions.
    private static final int OUTER_HOTSPOT = 24;
    private static final double LOCAL_EDGE_FRACTION = .31;
    private static final int BORDER_GRAB = 9;
    private static final int EDGE_DROP_REACH = 32;
    private static final int DRAG_THRESHOLD = 6;
    private static final long DOCK_DWELL_NANOS = 340_000_000L;
    private static final long DRAG_SETTLE_NANOS = 150_000_000L;
    private static final double FAST_DRAG_SPEED = 660.0;
    private UiElement lastHover;
    private long hoveredSince;
    private Runnable layoutChanged = () -> {};

    public UiDockManager(UiTheme theme) {
        this.theme = theme;
        ctx = new UiContext(theme);
    }
    public UiContext context() { return ctx; }
    public UiDockManager onLayoutChanged(Runnable callback) {
        layoutChanged = callback == null ? () -> {} : callback; return this;
    }
    public Window window(String id, String title, UiElement content) {
        if (windows.containsKey(id)) throw new IllegalArgumentException("Duplicate window id: " + id);
        Window w = new Window(id, title, content);
        windows.put(id, w);
        return w;
    }
    public Window find(String id) { return windows.get(id); }

    /** Generic starting layout for independent consumers; no Pyro VFX panel names. */
    public void singlePanelLayout() {
        floating.clear();
        closing.clear();
        for (Window window : windows.values()) window.open = true;
        workspace.clear();groups.clear();nextZoneId=0;
        addZone(new UiDockLayout.Box(0,0,1,1),windows.values().toArray(Window[]::new));
        selectedWindow = windows.values().stream().findFirst().orElse(null);
        relayout();
    }
    private Tabs addZone(UiDockLayout.Box rect,Window... entries) {
        String id="zone"+nextZoneId++;
        Tabs tabs=new Tabs(id,entries);
        groups.put(id,tabs);
        workspace.put(id,rect);
        return tabs;
    }
    public boolean visible(String id) { Window w = windows.get(id); return w != null && w.open; }
    public boolean floating(String id) { Window w = find(id); return w != null && w.open && floating.contains(w); }
    public boolean hasMinecraftViewport() {
        return windows.values().stream().anyMatch(w->w.open&&w.minecraftViewport);
    }
    /** Body rectangle of the currently visible Minecraft viewport, in UI
     * logical coordinates. Used to embed vanilla inventory/chat screens.
     */
    public UiRect minecraftViewportBody() {
        for(int i=floating.size()-1;i>=0;i--) {
            Window w=floating.get(i);
            if(w.open&&w.minecraftViewport)return floatingBody(w);
        }
        for(Tabs group:tabs()) {
            Window active=group.active();
            if(active!=null&&active.open&&active.minecraftViewport) {
                UiRect r=group.rect;
                return new UiRect(r.x()+1,r.y()+theme.titleHeight+3,
                        Math.max(0,r.w()-2),Math.max(0,r.h()-theme.titleHeight-4));
            }
        }
        return null;
    }
    public boolean minecraftViewportAt(double x,double y) {
        if(ctx.popupVisible()||draggingWindow!=null||resizingWindow!=null||
                resizingEdge!=null||pendingTab!=null||ctx.capture!=null)return false;
        for(int i=floating.size()-1;i>=0;i--) {
            Window w=floating.get(i);
            UiRect r=floatingBounds(w);
            if(!w.open || !r.contains(x,y))continue;
            return w.minecraftViewport && floatingBody(w).contains(x,y);
        }
        // A divider has priority over world focus, just as pointerDown and
        // cursor() do. Never display resize but launch FPS mouse capture.
        if(workspace.edgeAt(x,y,workArea(),BORDER_GRAB)!=null)return false;
        for(Tabs group:tabs()) {
            if(!group.rect.contains(x,y))continue;
            Window active=group.active();
            return active!=null && active.open && active.minecraftViewport &&
                    y>=group.rect.y()+theme.titleHeight+3;
        }
        return false;
    }
    public UiRect area(String id) {
        Window w = find(id);
        if (w == null || !w.open) return null;
        if (floating.contains(w)) return floatingBounds(w);
        for (Tabs t : tabs()) if (t.windows.contains(w)) return t.rect;
        return null;
    }
    /** Exact proposed rectangle, before any state mutation. Used by UI probes. */
    public UiRect previewArea() {
        if(previewPlan==null)return null;
        return previewPlan.geometry.rect(previewPlan.newZone()==null
                ? previewPlan.hit.target():previewPlan.newZone(),workArea());
    }
    UiRect projectedArea(String windowId) {
        if(previewPlan==null)return area(windowId);
        Window window=find(windowId);
        for(Tabs group:previewPlan.groups.values())
            if(group.windows.contains(window))
                return previewPlan.geometry.rect(group.id,workArea());
        return null;
    }

    /** Establish a default workspace by registration order, not editor-specific IDs. */
    public void defaultLayout() {
        floating.clear();
        closing.clear();
        workspace.clear();groups.clear();nextZoneId=0;
        for (Window w : windows.values()) {
            w.open = true;
            w.openedAt = System.nanoTime();
        }
        List<Window> primary = windows.values().stream()
                .filter(window -> !window.id.equals("settings")).toList();
        if (primary.size() >= 5) {
            // One planar workspace: the lower row spans independent portions
            // of the three-zone upper row. No nested split ownership exists.
            addZone(new UiDockLayout.Box(0,0,.20,.82),primary.get(0));
            addZone(new UiDockLayout.Box(.20,0,.72,.82),primary.get(1));
            addZone(new UiDockLayout.Box(.72,0,1,.82),primary.get(2));
            addZone(new UiDockLayout.Box(0,.82,.70,1),primary.get(3));
            addZone(new UiDockLayout.Box(.70,.82,1,1),
                    primary.subList(4,primary.size()).toArray(Window[]::new));
        } else {
            addZone(new UiDockLayout.Box(0,0,1,1),primary.toArray(Window[]::new));
        }
        Window settings = find("settings");
        if (settings != null) {
            settings.at(Math.max(16, width - 320), 75).size(290, 258);
            // Keep the editor workspace unobstructed on first opening.
            // Settings remains a real independent floating window, opened
            // through Layout > Show UI Settings.
            settings.open = false;
        }
        selectedWindow = primary.isEmpty() ? null : primary.get(Math.min(1, primary.size() - 1));
        relayout();
    }

    public void resize(int width, int height) {
        this.width = Math.max(1, width);
        this.height = Math.max(1, height);
        ctx.frameWidth = this.width; ctx.frameHeight = this.height;
        for (Window w : floating) clampFloating(w);
        relayout();
    }
    private UiRect workArea() {
        return new UiRect(5, TOP_BAR + 5,
                Math.max(0, width - 10), Math.max(0, height - TOP_BAR - 10));
    }
    private void relayout() {
        assignLayout(workspace,groups);
        layoutContents(groups);
    }
    private void assignLayout(UiDockLayout geometry,Map<String,Tabs> tabsById) {
        UiRect area=workArea();
        for(Tabs tabs:tabsById.values()) {
            UiRect rect=geometry.rect(tabs.id,area);
            tabs.rect=rect==null?new UiRect(0,0,0,0):rect;
        }
    }
    private void layoutContents(Map<String,Tabs> tabsById) {
        for (Window w : floating) {
            if (w.open) w.content.layout(floatingBody(w), ctx);
        }
        for (Tabs t : tabsById.values()) {
            for (Window w : t.windows) {
                w.content.layout(new UiRect(t.rect.x() + 1, t.rect.y() + theme.titleHeight + 1,
                        Math.max(0, t.rect.w() - 2), Math.max(0, t.rect.h() - theme.titleHeight - 2)), ctx);
            }
        }
    }
    private void clampFloating(Window w) {
        // Fully visible floating windows at any scale: previously the x/y clamp
        // kept only 70px of the window onscreen, hiding controls at high zoom.
        int maxW=Math.max(1,width-4);
        int maxH=Math.max(1,height-TOP_BAR-4);
        w.width=UiRect.clamp(w.width,Math.min(MIN_WIDTH,maxW),maxW);
        w.height=UiRect.clamp(w.height,Math.min(MIN_HEIGHT,maxH),maxH);
        w.x=UiRect.clamp(w.x,2,Math.max(2,width-w.width-2));
        w.y=UiRect.clamp(w.y,TOP_BAR+2,
                Math.max(TOP_BAR+2,height-w.height-2));
    }
    private UiRect floatingBounds(Window w) { return new UiRect(w.x, w.y, w.width, w.height); }
    private UiRect floatingBody(Window w) {
        return new UiRect(w.x + 1, w.y + theme.titleHeight + 1,
                Math.max(0, w.width - 2), Math.max(0, w.height - theme.titleHeight - 2));
    }

    public void draw(UiCanvas c, double mx, double my) {
        if (draggingWindow != null || resizingWindow != null || resizingEdge != null
                || ctx.capture != null) {
            // Mouse drag events are authoritative, including when dev tests
            // synthesize pointer movement; the OS pointer may be elsewhere.
            mx = ctx.mouseX; my = ctx.mouseY;
        } else { ctx.mouseX = mx; ctx.mouseY = my; }
        ctx.measure = c::measure;
        ctx.lowDetail = draggingWindow != null || resizingWindow != null
                || resizingEdge != null || ctx.capture instanceof UiControls.Curve;
        determineHoverOwner();
        // Paint the entire desktop before any window. Gaps, rounded corners,
        // temporary drag holes and closed zones must NEVER reveal Minecraft.
        c.fill(0,0,width,height,UiTheme.BG);
        c.fill(0,TOP_BAR,width,5,UiTheme.BG);
        c.fill(0,Math.max(TOP_BAR,height-5),width,5,UiTheme.BG);
        c.fill(0,TOP_BAR,5,Math.max(0,height-TOP_BAR),UiTheme.BG);
        c.fill(Math.max(0,width-5),TOP_BAR,5,Math.max(0,height-TOP_BAR),UiTheme.BG);
        drawMenuBar(c);
        if(draggingWindow!=null)updateDockHover(mx,my);
        if(previewPlan!=null) {
            // The existing panels really reflow into the proposed geometry.
            // Only the new target zone is a single-color insertion preview.
            assignLayout(previewPlan.geometry,previewPlan.groups);
            float transition=theme.animations ? UiTheme.smooth(UiTheme.clamp01(
                    (clock.getAsLong()-previewStartedAt)/135_000_000f)) : 1f;
            for(Tabs group:previewPlan.groups.values()) {
                Tabs previous=groups.get(group.id);
                if(previous!=null)
                    group.rect=between(previous.rect,group.rect,transition);
                else if(group.id.equals(previewPlan.newZone))
                    group.rect=between(collapsedPreview(group.rect,previewPlan.hit.side()),
                            group.rect,transition);
            }
            layoutContents(previewPlan.groups);
            for(Tabs group:previewPlan.groups.values()) {
                if(group.id.equals(previewPlan.newZone))
                    drawInsertionPreview(c,group.rect);
                else drawTabs(c,group,previewPlan.newZone()==null &&
                        group.id.equals(previewPlan.hit.target()));
            }
            relayout();
        } else for(Tabs group:tabs())drawTabs(c,group);
        for (Window w : floating) if (w.open) {
            // A held drop target must remain legible even when the dragged
            // floating window overlaps its monochrome insertion rectangle.
            boolean draggingOverPreview=w==draggingWindow && previewPlan!=null;
            if(draggingOverPreview)c.pushLayer(.32f,0);
            drawFloating(c,w);
            if(draggingOverPreview)c.popLayer();
        }
        for (int i=closing.size()-1;i>=0;i--) {
            Closing entry = closing.get(i);
            float t = theme.progress(entry.at, 150);
            if (t >= 1f) { closing.remove(i); continue; }
            float fade = 1f - UiTheme.smooth(t);
            c.pushLayer(fade,Math.round(-4*t));
            drawFloatingContents(c,entry.window,entry.bounds);
            c.popLayer();
        }
        ctx.drawPopup(c);
        if (!ctx.popupVisible()) drawTooltip(c);
    }
    /**
     * Paint may visit many panels but the mouse belongs to only the topmost
     * visible one. Otherwise controls hidden behind a floating window still
     * draw their hover tint because all widgets read the same ctx.mouseX/Y.
     */
    private void determineHoverOwner() {
        hoveredFloating=null;
        hoveredZoneId=null;
        if(ctx.popupVisible() || draggingWindow!=null)return;
        for(int i=floating.size()-1;i>=0;i--) {
            Window w=floating.get(i);
            if(w.open && floatingBounds(w).contains(ctx.mouseX,ctx.mouseY)) {
                hoveredFloating=w;
                return;
            }
        }
        for(Tabs group:tabs()) {
            if(group.rect.contains(ctx.mouseX,ctx.mouseY)) {
                hoveredZoneId=group.id;
                return;
            }
        }
    }
    private UiElement hitElement(double x, double y) {
        for (int i = floating.size() - 1; i >= 0; i--) {
            Window w = floating.get(i);
            if (w.open && floatingBody(w).contains(x, y)) return w.content.hit(x, y);
            if (w.open && floatingBounds(w).contains(x, y)) return null;
        }
        for (Tabs t : tabs()) if (t.rect.contains(x, y)) {
            Window w = t.active();
            return w == null ? null : w.content.hit(x, y);
        }
        return null;
    }
    private void drawTooltip(UiCanvas c) {
        if (draggingWindow != null || resizingWindow != null ||
                resizingEdge != null || ctx.capture != null) return;
        UiElement hit = hitElement(ctx.mouseX,ctx.mouseY);
        if (hit != lastHover) { lastHover = hit; hoveredSince = System.nanoTime(); }
        if (hit == null || hit.tooltip() == null) return;
        long elapsed = System.nanoTime() - hoveredSince;
        if (elapsed < 550_000_000L) return;
        float fraction = theme.animations ? UiTheme.out(
                UiTheme.clamp01((elapsed-550_000_000L)/140_000_000f)) : 1f;
        String tip = hit.tooltip();
        int tw = Math.min(290,c.measure(tip)+24),th=25;
        int x = UiRect.clamp((int)ctx.mouseX+14,2,Math.max(2,width-tw-2));
        int y = UiRect.clamp((int)ctx.mouseY+17,TOP_BAR+2,
                Math.max(TOP_BAR+2,height-th-2));
        UiRect box = new UiRect(x,y,tw,th);
        c.pushLayer(fraction,Math.round((1f-fraction)*4));
        c.rounded(new UiRect(x+3,y+4,tw,th),UiTheme.R_PANEL,0xAA000000);
        c.rounded(box,UiTheme.R_PANEL,UiTheme.ELEVATED);
        c.roundedBorder(box,UiTheme.R_PANEL,0xFF4A4A4A);
        c.textClipped(tip,x+12,y+(th-ctx.fontHeight)/2,tw-22,UiTheme.TEXT);
        c.popLayer();
    }

    private void drawMenuBar(UiCanvas c) {
        c.fill(0, 0, width, TOP_BAR, UiTheme.BG);
        c.gradientHorizontal(new UiRect(0, 0, Math.min(260, width), TOP_BAR),
                0xFF24130F, UiTheme.BG);
        c.fill(0, TOP_BAR - 1, width, 1, 0xFF1F1F1F);
        c.icon("fire",8,6,15,theme.accent);
        c.textHeading("PYRO", 26, 9, UiTheme.HIGHLIGHT);
        boolean compact=width<650;
        if(!compact) c.textMeta("UI",86,9,UiTheme.SECONDARY);
        drawMenu(c,"Window",compact?100:175,compact?55:66);
        drawMenu(c,"Layout",compact?160:245,compact?55:62);

        int scaleStart = compact ? Math.max(215,width-239) : Math.max(320,width-239);
        c.textMeta("Scale", scaleStart, 9, UiTheme.SECONDARY);
        c.text(String.format(java.util.Locale.ROOT,"%d%%",theme.scalePercent),
                scaleStart + 44, 8, UiTheme.TEXT);
        drawTopAction(c,scaleStart+90,"minus");
        drawTopAction(c,scaleStart+117,"plus");
        UiRect settings = new UiRect(scaleStart + 146, 3, 88, TOP_BAR - 6);
        c.rounded(settings, UiTheme.R_CONTROL,
                settings.contains(ctx.mouseX,ctx.mouseY) ? UiTheme.HOVER : UiTheme.ELEVATED);
        c.icon("settings-cog",settings.x()+4,settings.y()+3,15,UiTheme.SECONDARY);
        c.textClipped("Settings",settings.x()+21,
                settings.y()+(settings.h()-ctx.fontHeight)/2,
                Math.max(0,settings.w()-24),UiTheme.TEXT);
    }
    private void drawTopAction(UiCanvas c, int x, String symbol) {
        UiRect r = new UiRect(x, 3, 22, TOP_BAR - 6);
        boolean over = r.contains(ctx.mouseX, ctx.mouseY);
        c.rounded(r, UiTheme.R_CONTROL, over ? UiTheme.HOVER : UiTheme.ELEVATED);
        c.icon(symbol,x+3,r.y()+(r.h()-16)/2,16,UiTheme.TEXT);
    }
    private void drawMenu(UiCanvas c, String label, int x, int w) {
        UiRect area = new UiRect(x, 3, w, TOP_BAR - 6);
        if (area.contains(ctx.mouseX, ctx.mouseY))
            c.rounded(area, UiTheme.R_CONTROL, UiTheme.HOVER);
        c.text(label, x + 9, 9, UiTheme.SECONDARY);
    }
    private void drawInsertionPreview(UiCanvas canvas,UiRect rect) {
        if(rect.w()<=0||rect.h()<=0)return;
        canvas.fill(rect,UiTheme.mix(UiTheme.PANEL,theme.accent,.19f));
        canvas.border(rect,UiTheme.alpha(theme.accent,.88f));
    }
    private static UiRect between(UiRect a,UiRect b,float amount) {
        return new UiRect(Math.round(a.x()+(b.x()-a.x())*amount),
                Math.round(a.y()+(b.y()-a.y())*amount),
                Math.max(0,Math.round(a.w()+(b.w()-a.w())*amount)),
                Math.max(0,Math.round(a.h()+(b.h()-a.h())*amount)));
    }
    private static UiRect collapsedPreview(UiRect r,UiDockLayout.Side side) {
        return switch(side) {
            case LEFT,OUTER_LEFT -> new UiRect(r.x()+r.w(),r.y(),0,r.h());
            case RIGHT,OUTER_RIGHT -> new UiRect(r.x(),r.y(),0,r.h());
            case TOP,OUTER_TOP -> new UiRect(r.x(),r.y()+r.h(),r.w(),0);
            case BOTTOM,OUTER_BOTTOM -> new UiRect(r.x(),r.y(),r.w(),0);
            default -> r;
        };
    }
    private int headerShade(Window active) {
        if (active == null) return UiTheme.ELEVATED;
        if (active.transparentContent) return UiTheme.mix(UiTheme.ELEVATED, theme.accent, .17f);
        if (active.accentedHeader) return UiTheme.mix(UiTheme.ELEVATED, theme.contextAccent, .14f);
        return UiTheme.ELEVATED;
    }
    private record TabStrip(int width,int visible,boolean overflow) {}
    private static final int TAB_DROP_SLOT = 26;
    private static final int TAB_EDGE_INSET = 4;
    /** The tab viewport has equal left/right padding. The drop slot is a
     * temporary hit target during dragging, not permanent blank space.
     */
    static UiRect tabStripViewport(UiRect panel,int titleHeight) {
        return new UiRect(panel.x()+TAB_EDGE_INSET,panel.y()+2,
                Math.max(0,panel.w()-TAB_EDGE_INSET*2),
                Math.max(0,titleHeight+1));
    }
    private TabStrip tabStrip(Tabs group) {
        int count=group.windows.size();
        int available=Math.max(1,tabStripViewport(group.rect,theme.titleHeight).w());
        // Tabs keep their own width and scroll UNDER a clipped viewport. A tab
        // may be partially visible at either edge, as in ordinary desktop UI.
        // Width follows actual rendered title metrics. Never abbreviate
        // individual tab titles just because there are several tabs.
        int per=Math.max(112,group.windows.stream().mapToInt(
                w->ctx.measureHeading.applyAsInt(w.title)+48).max().orElse(112));
        int visible=Math.max(1,Math.min(count,(available+per+1)/(per+2)));
        boolean overflow=count*(per+2)-2>available;
        return new TabStrip(per,visible,overflow);
    }
    private int tabScrollLimit(Tabs tabs,TabStrip strip) {
        int available=Math.max(1,tabStripViewport(tabs.rect,theme.titleHeight).w());
        return Math.max(0,tabs.windows.size()*(strip.width()+2)-2-available);
    }
    /** A header accepts a dragged tab directly after the tab under the pointer,
     * or at the end when targeting the empty header space.
     */
    private boolean freeTabHeaderSpace(Tabs group,double x,double y) {
        UiRect r=group.rect;
        if(y<r.y()+3 || y>=r.y()+theme.titleHeight+1 ||
                x<r.x()+4 || x>=r.x()+r.w()-4)return false;
        TabStrip strip=tabStrip(group);
        int occupiedRight=r.x()+TAB_EDGE_INSET+
                group.windows.size()*(strip.width()+2)-2-group.tabScrollPx;
        int availableRight=r.x()+r.w()-TAB_EDGE_INSET;
        boolean blankAfterTabs=x>=occupiedRight && x<availableRight;
        // Only while dragging: the final 26px is an insertion target.
        // Do not reserve it in the normal tab layout or clip.
        boolean reservedSlot=x>=availableRight-TAB_DROP_SLOT;
        return blankAfterTabs || reservedSlot;
    }
    private int tabInsertionIndex(Tabs group,double x,double y) {
        UiRect r=group.rect;
        if(y<r.y()+3 || y>=r.y()+theme.titleHeight+1)return -1;
        HeaderHit hovered=headerAt(group,x);
        if(hovered!=null)return group.windows.indexOf(hovered.window)+1;
        return freeTabHeaderSpace(group,x,y)?group.windows.size():-1;
    }
    private void ensureActiveTabVisible(Tabs tabs,TabStrip strip) {
        int available=Math.max(1,tabStripViewport(tabs.rect,theme.titleHeight).w());
        tabs.tabScrollPx=UiRect.clamp(tabs.tabScrollPx,0,tabScrollLimit(tabs,strip));
        if(tabs.manualTabScroll)return;
        int start=tabs.selected*(strip.width()+2);
        int end=start+strip.width();
        if(start<tabs.tabScrollPx)tabs.tabScrollPx=start;
        if(end>tabs.tabScrollPx+available)tabs.tabScrollPx=end-available;
        tabs.tabScrollPx=UiRect.clamp(tabs.tabScrollPx,0,tabScrollLimit(tabs,strip));
    }
    private void drawTabs(UiCanvas c, Tabs tabs) {
        drawTabs(c,tabs,false);
    }
    private void drawTabs(UiCanvas c, Tabs tabs,boolean tabMergePreview) {
        UiRect r = tabs.rect;
        if (r.w() <= 0 || r.h() <= 0) return;
        double oldMouseX=ctx.mouseX,oldMouseY=ctx.mouseY;
        if(!tabs.id.equals(hoveredZoneId)) {
            ctx.mouseX=-100000;
            ctx.mouseY=-100000;
        }
        try {
        Window active = tabMergePreview && groups.containsKey(tabs.id)
                ? groups.get(tabs.id).active() : tabs.active();
        boolean preview = active != null && active.transparentContent;
        // Preview is the untouched Minecraft framebuffer. Never shade it.
        if (!preview) {
            c.fill(r,UiTheme.BG);
            c.rounded(r,UiTheme.R_PANEL,UiTheme.PANEL);
        } else {
            c.roundedBorder(r,UiTheme.R_PANEL,UiTheme.DIVIDER);
        }
        UiRect header = new UiRect(r.x()+1, r.y()+1, Math.max(0,r.w()-2),
                Math.min(theme.titleHeight+2,Math.max(0,r.h()-2)));
        if (active != null && active.accentedHeader)
            c.gradientHorizontal(header, headerShade(active), UiTheme.ELEVATED);
        else c.rounded(header, UiTheme.R_CONTROL, UiTheme.ELEVATED);

        TabStrip strip=tabStrip(tabs);
        if(tabMergePreview) {
            int inserted=tabs.windows.indexOf(draggingWindow);
            int visibleWidth=tabStripViewport(r,theme.titleHeight).w();
            int begin=Math.max(0,inserted)*(strip.width()+2);
            int end=begin+strip.width();
            if(begin<tabs.tabScrollPx)tabs.tabScrollPx=begin;
            if(end>tabs.tabScrollPx+visibleWidth)tabs.tabScrollPx=end-visibleWidth;
            tabs.tabScrollPx=UiRect.clamp(tabs.tabScrollPx,0,tabScrollLimit(tabs,strip));
        }
        else ensureActiveTabVisible(tabs,strip);
        int tabWidth=strip.width();
        UiRect viewport=tabStripViewport(r,theme.titleHeight);
        int maxX=viewport.x()+viewport.w();
        int first=Math.min(tabs.windows.size(),tabs.tabScrollPx/(tabWidth+2));
        int x=viewport.x()+first*(tabWidth+2)-tabs.tabScrollPx;
        c.clip(viewport);
        for (int i = first; i < tabs.windows.size() && x<maxX; i++) {
            Window w = tabs.windows.get(i);
            boolean inserted = tabMergePreview && w==draggingWindow;
            boolean selected = !inserted && w == active;
            UiRect tab = new UiRect(x, r.y()+3,tabWidth,
                    Math.max(0,theme.titleHeight-3));
            if (tab.w() <= 0) break;
            boolean over = tab.contains(ctx.mouseX,ctx.mouseY);
            if (inserted) {
                c.rounded(tab,UiTheme.R_CONTROL,UiTheme.mix(UiTheme.ELEVATED,theme.accent,.32f));
                c.border(tab,UiTheme.alpha(theme.accent,.88f));
            } else if (selected) c.rounded(tab,UiTheme.R_CONTROL,
                    UiTheme.mix(UiTheme.ELEVATED, UiTheme.PANEL, .66f));
            else if (over) c.rounded(tab,UiTheme.R_CONTROL, UiTheme.HOVER);
            int titleColor = inserted || selected ? UiTheme.TEXT : UiTheme.SECONDARY;
            c.textHeading(w.title,x+11,r.y()+8,titleColor);
            if(!inserted)c.icon("close",x+tab.w()-24,
                    r.y()+(theme.titleHeight-15)/2+2,15,
                    over ? UiTheme.TEXT : UiTheme.QUIET);
            if (selected) {
                float reveal = theme.fade(tabs.selectedAt, 175);
                c.rounded(new UiRect(x+8, r.y()+theme.titleHeight,
                        Math.max(0,tab.w()-16),2),1,
                        UiTheme.alpha(selectedWindow == w ? theme.accent : 0xFF555555,reveal));
            }
            x += tabWidth+2;
        }
        c.unclip();
        if (active != null) {
            UiRect body = new UiRect(r.x()+1, r.y()+theme.titleHeight+3,
                    Math.max(0,r.w()-2),Math.max(0,r.h()-theme.titleHeight-4));
            if(active.minecraftViewport)c.minecraftFrame(body);
            c.clip(body);
            if(tabMergePreview)active.content.layout(new UiRect(r.x()+1,
                    r.y()+theme.titleHeight+1,Math.max(0,r.w()-2),
                    Math.max(0,r.h()-theme.titleHeight-2)),ctx);
            active.content.paint(c,ctx);
            c.unclip();
        }
        } finally {
            ctx.mouseX=oldMouseX;
            ctx.mouseY=oldMouseY;
        }
    }
    private void drawFloating(UiCanvas c, Window w) {
        UiRect r = floatingBounds(w);
        if (r.w() <= 0 || r.h() <= 0) return;
        double oldMouseX=ctx.mouseX,oldMouseY=ctx.mouseY;
        if(w!=hoveredFloating) {
            ctx.mouseX=-100000;
            ctx.mouseY=-100000;
        }
        try {
        float appear = theme.out(theme.progress(w.openedAt, 165));
        c.pushLayer(appear, Math.round((1f-appear)*5));
        drawFloatingContents(c,w,r);
        c.popLayer();
        } finally {
            ctx.mouseX=oldMouseX;
            ctx.mouseY=oldMouseY;
        }
    }
    private void drawFloatingContents(UiCanvas c, Window w, UiRect r) {
        c.rounded(new UiRect(r.x()+7,r.y()+10,r.w(),r.h()), UiTheme.R_FLOAT,0x8C000000);
        c.rounded(new UiRect(r.x()+3,r.y()+5,r.w(),r.h()),UiTheme.R_FLOAT,0x48000000);
        if (!w.transparentContent) c.rounded(r,UiTheme.R_FLOAT,UiTheme.ELEVATED);
        c.roundedBorder(r,UiTheme.R_FLOAT,0xFF494949);
        UiRect header = new UiRect(r.x()+2,r.y()+2,Math.max(0,r.w()-4),theme.titleHeight+4);
        c.gradientHorizontal(header,
                UiTheme.mix(UiTheme.ELEVATED,theme.accent,.17f), UiTheme.ELEVATED);
        c.rounded(new UiRect(r.x()+8,r.y()+10,3,9),1,theme.accent);
        c.textHeadingClipped(w.title,r.x()+19,r.y()+9,Math.max(0,r.w()-61),UiTheme.TEXT);
        c.icon("close",r.x()+r.w()-29,
                r.y()+(theme.titleHeight-16)/2+3,16,UiTheme.SECONDARY);
        UiRect content = floatingBody(w);
        if(w.minecraftViewport)c.minecraftFrame(content);
        else if (!w.transparentContent) c.rounded(content,UiTheme.R_PANEL,UiTheme.PANEL);
        c.clip(content);
        w.content.paint(c,ctx);
        c.unclip();
        c.icon("expand",r.x()+r.w()-17,
                r.y()+r.h()-17,15,UiTheme.SECONDARY);
    }


    public boolean pointerDown(double x, double y, int button) {
        ctx.mouseX = x; ctx.mouseY = y;
        if (ctx.popupClick(x, y)) return true;
        if (y < TOP_BAR) {
            if (button == 0) ctx.setFocus(null);
            int windowMenuX=width<650?100:175;
            int layoutMenuX=width<650?160:245;
            if (button == 0 && x >= windowMenuX && x < windowMenuX+(width<650?55:66)) {
                var closed = windows.values().stream().filter(w -> !w.open).toList();
                if (closed.isEmpty()) ctx.menu(new UiRect(windowMenuX, 1, 154, TOP_BAR-2),
                        List.of("All panels visible"), i -> {});
                else ctx.menu(new UiRect(windowMenuX, 1, 170, TOP_BAR-2),
                        closed.stream().map(w -> "Open " + w.title).toList(),
                        index -> reopen(closed.get(index).id));
            } else if (button == 0 && x >= layoutMenuX && x < layoutMenuX+(width<650?55:62)) {
                ctx.menu(new UiRect(layoutMenuX, 1, 158, TOP_BAR-2),
                        List.of("Restore default layout", "Show UI Settings", "Close floating panels"),
                        index -> {
                            if (index == 0) { defaultLayout(); changed(); }
                            if (index == 1) reopen("settings");
                            if (index == 2) {
                                for (Window w : List.copyOf(floating)) if (!w.id.equals("settings")) close(w.id);
                            }
                        });
            } else if (button == 0) {
                int start = width<650 ? Math.max(215,width-239) : Math.max(320,width-239);
                if (x >= start+90 && x < start+112)
                    theme.scalePercent = Math.max(UiTheme.SCALE_MIN, theme.scalePercent - UiTheme.SCALE_STEP);
                else if (x >= start+117 && x < start+139)
                    theme.scalePercent = Math.min(UiTheme.SCALE_MAX, theme.scalePercent + UiTheme.SCALE_STEP);
                else if (x >= start+146 && x < start+234)
                    reopen("settings");
            }
            return true;
        }
        downX = x; downY = y;
        for (int i = floating.size() - 1; i >= 0; i--) {
            Window w = floating.get(i);
            UiRect rect = floatingBounds(w);
            if (!w.open || !rect.contains(x, y)) continue;
            floating.remove(i); floating.add(w);
            selectedWindow = w;
            if (button == 0 && (y < rect.y() + theme.titleHeight
                    || x >= rect.x() + rect.w() - 13 && y >= rect.y() + rect.h() - 13))
                ctx.setFocus(null);
            if (button == 0 && x > rect.x() + rect.w() - 24 && y < rect.y() + theme.titleHeight) {
                close(w.id); return true;
            }
            if (button == 0 && x >= rect.x() + rect.w() - 13 && y >= rect.y() + rect.h() - 13) {
                resizingWindow = w; resizeW = w.width; resizeH = w.height; return true;
            }
            if (button == 0 && y < rect.y() + theme.titleHeight) {
                pendingTab = w;
                return true;
            }
            return dispatchTo(w.content, x, y, button);
        }
        if (button == 0) {
            UiDockLayout.Edge edge=workspace.edgeAt(x,y,workArea(),BORDER_GRAB);
            if (edge != null) {
                ctx.setFocus(null);
                resizingEdge=edge;
                resizeStart=workspace.copy();
                return true;
            }
        }
        for (Tabs t : tabs()) {
            if (!t.rect.contains(x, y)) continue;
            if (y < t.rect.y() + theme.titleHeight) {
                TabStrip strip=tabStrip(t);
                HeaderHit header = headerAt(t, x);
                if (header != null && button == 0) {
                    ctx.setFocus(null);
                    if (header.close) close(header.window.id);
                    else {
                        if (t.selected != t.windows.indexOf(header.window))
                            t.selectedAt = System.nanoTime();
                        t.selected = t.windows.indexOf(header.window);
                        t.manualTabScroll=false;
                        selectedWindow = header.window;
                        pendingTab = header.window;
                    }
                }
                relayout(); return true;
            }
            Window active = t.active();
            if (active != null) { selectedWindow = active; return dispatchTo(active.content, x, y, button); }
            return true;
        }
        ctx.setFocus(null);
        return true;
    }
    private boolean dispatchTo(UiElement tree, double x, double y, int button) {
        UiElement hit = tree.hit(x, y);
        if (hit == null) {
            ctx.setFocus(null);
            return true; // the window surface still consumes the click
        }
        if (!hit.focusable()) ctx.setFocus(null);
        if (hit.onPointerDown(ctx, x, y, button)) ctx.capture = hit;
        return true;
    }
    private HeaderHit headerAt(Tabs t, double x) {
        TabStrip strip=tabStrip(t);
        int per=strip.width();
        UiRect viewport=tabStripViewport(t.rect,theme.titleHeight);
        if(x<viewport.x() || x>=viewport.x()+viewport.w())return null;
        int within=(int)Math.floor(x-viewport.x()+t.tabScrollPx);
        if(within<0)return null;
        int index=within/(per+2);
        if(index>=t.windows.size() || within%(per+2)>=per)return null;
        Window w = t.windows.get(index);
        boolean close=within%(per+2)>=per-23;
        return new HeaderHit(w, t, close);
    }
    public boolean pointerMove(double x, double y) {
        ctx.mouseX = x; ctx.mouseY = y;
        if (ctx.capture != null) { ctx.capture.onPointerMove(ctx, x, y); return true; }
        if (pendingTab != null && Math.hypot(x - downX, y - downY) > DRAG_THRESHOLD) {
            Window w = pendingTab;
            pendingTab = null;
            if(floating.contains(w)) {
                dragOffsetX=downX-w.x;
                dragOffsetY=downY-w.y;
            } else {
                // Undock at drag START, not at drop. The complete floating
                // window follows the mouse from this very frame onward.
                dragOffsetX=Math.min(80,Math.max(26,w.width/4));
                dragOffsetY=Math.min(14,theme.titleHeight/2);
                w.at((int)Math.round(x-dragOffsetX),(int)Math.round(y-dragOffsetY));
                undock(w);
            }
            draggingWindow = w;
            lastDragX=x;
            lastDragY=y;
            lastDragTime=clock.getAsLong();
            lastFastDragTime=0;
            clearDockHover();
        }
        if (draggingWindow != null) {
            observeDragMotion(x,y);
            draggingWindow.x = (int)Math.round(x - dragOffsetX);
            draggingWindow.y = (int)Math.round(y - dragOffsetY);
            draggingWindow.content.layout(floatingBody(draggingWindow), ctx);
            updateDockHover(x, y);
            return true;
        }
        if (resizingWindow != null) {
            resizingWindow.width = Math.max(MIN_WIDTH, resizeW + (int) (x - downX));
            resizingWindow.height = Math.max(MIN_HEIGHT, resizeH + (int) (y - downY));
            clampFloating(resizingWindow);
            resizingWindow.content.layout(floatingBody(resizingWindow),ctx);
            return true;
        }
        if (resizingEdge != null) {
            UiRect r=workArea();
            double delta=(resizingEdge.vertical()?x-downX:y-downY)/
                    Math.max(1,resizingEdge.vertical()?r.w():r.h());
            workspace=resizeStart.copy();
            // Resolve every zone's minimum in the affected boundary group.
            double before=0,after=0;
            for(String id:resizingEdge.before())before=Math.max(before,
                    minZoneSpan(id,resizingEdge.vertical()));
            for(String id:resizingEdge.after())after=Math.max(after,
                    minZoneSpan(id,resizingEdge.vertical()));
            workspace.resize(resizingEdge,delta,before,after);
            relayout(); return true;
        }
        return false;
    }
    public boolean pointerUp(double x, double y, int button) {
        if (ctx.capture != null) {
            ctx.capture.onPointerUp(ctx, x, y, button);
            ctx.capture = null; return true;
        }
        boolean modified = false;
        if (draggingWindow != null) {
            DockPlan shownPreview=previewPlan;
            updateDockHover(x, y);
            // A mouse release cannot create and commit a new docking plan in
            // the same event. Only a preview already activated beforehand
            // may be committed; otherwise the free window stays free.
            if(shownPreview!=null && shownPreview==previewPlan)
                commitPreview(draggingWindow);
            else clampFloating(draggingWindow);
            modified = true;
        } else if (resizingWindow != null || resizingEdge != null) modified = true;
        draggingWindow = null; resizingWindow = null; resizingEdge = null;
        resizeStart=null;
        pendingTab = null; dockPreview = null;
        previewPlan=null;
        previewStartedAt=0;
        dockHoverStartedAt=0;
        dockingIntentArmed=false;
        lastDragTime=0;
        lastFastDragTime=0;
        if (modified) { relayout(); changed(); }
        return true;
    }
    public boolean wheel(double x, double y, double amount) {
        if (ctx.popupVisible()) return true;
        for (int i = floating.size() - 1; i >= 0; i--) {
            Window w = floating.get(i);
            if (floatingBounds(w).contains(x, y)) return w.content.wheel(ctx, x, y, amount);
        }
        for (Tabs t : tabs()) {
            if (t.rect.contains(x, y)) {
                if(y<t.rect.y()+theme.titleHeight+2) {
                    TabStrip strip=tabStrip(t);
                    if(strip.overflow() && amount!=0) {
                        // Pixel scrolling: a wheel notch moves 28 logical px,
                        // and partial tabs naturally remain visible at edges.
                        int delta=Math.max(1,(int)Math.round(Math.abs(amount)*28));
                        t.tabScrollPx=UiRect.clamp(t.tabScrollPx+(amount<0?delta:-delta),
                                0,tabScrollLimit(t,strip));
                        t.manualTabScroll=true;
                    }
                    return true;
                }
                Window w = t.active();
                return w != null && w.content.wheel(ctx, x, y, amount);
            }
        }
        return false;
    }
    public boolean key(int key, int modifiers) {
        if (key == 258) {
            List<UiElement> candidates = new ArrayList<>();
            if (selectedWindow != null && selectedWindow.open) {
                selectedWindow.content.collectFocusable(candidates);
            } else {
                for (Tabs tabs : tabs()) {
                    Window current = tabs.active();
                    if (current != null) current.content.collectFocusable(candidates);
                }
            }
            if (candidates.isEmpty()) {
                for (Tabs tabs : tabs()) {
                    Window active = tabs.active();
                    if (active != null) active.content.collectFocusable(candidates);
                }
                for (Window floatingWindow : floating)
                    if (floatingWindow.open) floatingWindow.content.collectFocusable(candidates);
            }
            if (!candidates.isEmpty()) {
                int old = candidates.indexOf(ctx.focus);
                int delta = (modifiers & 1) != 0 ? -1 : 1;
                int next = Math.floorMod(old + delta, candidates.size());
                ctx.setFocus(candidates.get(next));
                return true;
            }
        }
        if (ctx.focus != null && ctx.focus.onKey(ctx, key, modifiers)) return true;
        if (key == 256 && ctx.popupVisible()) { ctx.dismissPopup(); return true; }
        if ((modifiers & 2) != 0) {
            if (key == 83) { changed(); return true; } // Ctrl-S
            if (key == 87 && selectedWindow != null) { close(selectedWindow.id); return true; } // Ctrl-W
            if (key == 82 && (modifiers & 1) != 0) { defaultLayout(); changed(); return true; }
        }
        return false;
    }
    public enum Cursor { ARROW, MOVE, RESIZE_HORIZONTAL, RESIZE_VERTICAL, RESIZE_DIAGONAL, TEXT, CROSSHAIR }
    public Cursor cursor(double x, double y) {
        if (resizingWindow != null) return Cursor.RESIZE_DIAGONAL;
        if (resizingEdge != null) return resizingEdge.vertical()?Cursor.RESIZE_HORIZONTAL:Cursor.RESIZE_VERTICAL;
        if (draggingWindow != null || pendingTab != null) return Cursor.MOVE;
        for (int i = floating.size() - 1; i >= 0; i--) {
            Window w = floating.get(i);
            if (!w.open || !floatingBounds(w).contains(x, y)) continue;
            if (x >= w.x + w.width - 13 && y >= w.y + w.height - 13) return Cursor.RESIZE_DIAGONAL;
            if (y < w.y + theme.titleHeight) return Cursor.MOVE;
            UiElement top = w.content.hit(x, y);
            if (top instanceof UiControls.TextField) return Cursor.TEXT;
            if (top instanceof UiControls.Curve) return Cursor.CROSSHAIR;
            return Cursor.ARROW; // never consult a hidden splitter behind a floating panel
        }
        UiDockLayout.Edge edge=workspace.edgeAt(x,y,workArea(),BORDER_GRAB);
        if(edge!=null)return edge.vertical()?Cursor.RESIZE_HORIZONTAL:Cursor.RESIZE_VERTICAL;
        UiElement item = hitElement(x, y);
        if (item instanceof UiControls.TextField) return Cursor.TEXT;
        if (item instanceof UiControls.Curve) return Cursor.CROSSHAIR;
        return Cursor.ARROW;
    }
    public boolean typed(char character) {
        return ctx.focus != null && ctx.focus.onChar(ctx, character);
    }

    private Tabs groupContaining(Window window) {
        for(Tabs t:tabs())if(t.windows.contains(window))return t;
        return null;
    }
    private double minZoneSpan(String id,boolean horizontal) {
        Tabs tabs=groups.get(id);
        if(tabs==null)return 0;
        int min=horizontal?MIN_WIDTH:MIN_HEIGHT;
        for(Window w:tabs.windows)
            min=Math.max(min,horizontal?w.minDockWidth:w.minDockHeight);
        return (min+UiDockLayout.GAP)/(double)Math.max(1,
                horizontal?workArea().w():workArea().h());
    }
    /** True only when an actual, feasible placement is shown in the workspace. */
    public boolean indicatorsVisible() {
        return draggingWindow!=null && previewPlan!=null;
    }
    String hoverTargetId() {
        if(previewPlan==null)return null;
        Tabs target=groups.get(previewPlan.hit.target());
        return target==null||target.active()==null?null:target.active().id;
    }
    private void updateDockHover(double x, double y) {
        if(draggingWindow==null || !workArea().contains(x,y)) {
            clearDockHover();
            return;
        }
        long now=clock.getAsLong();
        if(lastFastDragTime!=0 && now-lastFastDragTime<DRAG_SETTLE_NANOS) {
            // High-speed travel never activates a dock. Wait for the movement
            // to settle, even if the cursor passes over a valid shared edge.
            previewPlan=null;
            return;
        }
        UiRect area=workArea();
        // Workspace edges win over zone edges. A pointer near the *absolute*
        // bottom docks across the entire width; over one zone's bottom
        // edge it docks below that zone only.
        UiDockLayout.Side side=null;
        Tabs target=null;
        DockHit next=null;
        // The tab strip wins over nearby shared edges. Hovering an existing
        // tab inserts AFTER it; hovering empty header space appends.
        for(Tabs t:tabs()) {
            int insert=tabInsertionIndex(t,x,y);
            if(insert<0)continue;
            next=new DockHit(t.id,UiDockLayout.Side.CENTER,null,false,insert);
            break;
        }
        // Below the header, one continuous seam owns both nearby local
        // sectors: approaching from either pane gives the SAME centered plan.
        if(next==null) {
            UiDockLayout.Edge shared=workspace.matchingDropEdgeAt(
                    x,y,area,EDGE_DROP_REACH);
            if(shared!=null)
                next=new DockHit(null,shared.vertical()
                        ?UiDockLayout.Side.RIGHT:UiDockLayout.Side.BOTTOM,
                        shared,false);
        }
        if(next==null && y>=area.y()+area.h()-OUTER_HOTSPOT)side=UiDockLayout.Side.OUTER_BOTTOM;
        else if(next==null && y<area.y()+OUTER_HOTSPOT)side=UiDockLayout.Side.OUTER_TOP;
        else if(next==null && x<area.x()+OUTER_HOTSPOT)side=UiDockLayout.Side.OUTER_LEFT;
        else if(next==null && x>=area.x()+area.w()-OUTER_HOTSPOT)side=UiDockLayout.Side.OUTER_RIGHT;
        if(side==null && next==null) {
            // Shared panel edges are the primary drop targets. A single
            // straight edge can border multiple zones at a T-junction.
            UiDockLayout.Edge boundary=workspace.edgeAt(x,y,area,EDGE_DROP_REACH);
            if(boundary!=null) {
                double line=(boundary.vertical()?area.x()+boundary.position()*area.w():
                        area.y()+boundary.position()*area.h());
                boolean before=boundary.vertical()?x<=line:y<=line;
                UiDockLayout.Side approach=boundary.vertical()
                        ?(before?UiDockLayout.Side.RIGHT:UiDockLayout.Side.LEFT)
                        :(before?UiDockLayout.Side.BOTTOM:UiDockLayout.Side.TOP);
                if(workspace.symmetricDropEdge(boundary)) {
                    // One seam, one zone: both windows move away from its
                    // center, regardless of which side the pointer entered.
                    next=new DockHit(null,boundary.vertical()
                            ?UiDockLayout.Side.RIGHT:UiDockLayout.Side.BOTTOM,
                            boundary,false);
                } else {
                    // At a T-junction the two sides have different spans.
                    // Anchor the insertion to the panel actually approached
                    // instead of resizing every pane along the whole seam.
                    String local=zoneAlongEdge(boundary,before,x,y,area);
                    if(local!=null)next=new DockHit(local,approach);
                }
            }
        }
        if(side==null && next==null) {
            for(Tabs t:tabs())if(t.rect.contains(x,y)) {target=t;break;}
            if(target==null) {
                // Internal gutters between dock panels are also drop targets;
                // users should not have to search for a one-pixel strip.
                double best=13;
                for(Tabs t:tabs()) {
                    UiRect r=t.rect;
                    double dx=Math.max(0,Math.max(r.x()-x,x-(r.x()+r.w())));
                    double dy=Math.max(0,Math.max(r.y()-y,y-(r.y()+r.h())));
                    double distance=Math.hypot(dx,dy);
                    if(distance<best) {best=distance;target=t;}
                }
            }
            if(target==null) {clearDockHover();return;}
            UiRect r=target.rect;
            side=localDropSide(r,x,y);
            // A completely shared edge has no independent LEFT/RIGHT (or
            // TOP/BOTTOM) local insertion sectors. Convert the entire side
            // sector to the same centered, two-window insertion instead of
            // falling back once the pointer is >120px from the seam.
            UiDockLayout.Edge shared=workspace.matchingEdgeForZoneSide(target.id,side);
            if(shared!=null)
                next=new DockHit(null,shared.vertical()
                        ?UiDockLayout.Side.RIGHT:UiDockLayout.Side.BOTTOM,
                        shared,false);
        }
        if(next==null)next=new DockHit(target==null?null:target.id,side);
        if(!next.equals(dockPreview)) {
            dockPreview=next;
            dockHoverStartedAt=now;
            previewPlan=null;
            previewStartedAt=0;
        }
        // A continuous hover over a *region* matters, not a stationary pixel.
        // Once the user deliberately activated docking, moving into another
        // region updates its preview immediately (still requires mouse-up).
        if(previewPlan==null && (dockingIntentArmed ||
                now-dockHoverStartedAt>=DOCK_DWELL_NANOS)) {
            previewPlan=planDrop(draggingWindow,next);
            if(previewPlan!=null) {
                dockingIntentArmed=true;
                previewStartedAt=now;
            }
        }
    }
    private String zoneAlongEdge(UiDockLayout.Edge edge,boolean before,
                                 double x,double y,UiRect area) {
        double cross=edge.vertical()
                ?(y-area.y())/Math.max(1,area.h())
                :(x-area.x())/Math.max(1,area.w());
        for(String id:before?edge.before():edge.after()) {
            UiDockLayout.Box box=workspace.get(id);
            if(box==null)continue;
            double start=edge.vertical()?box.y0:box.x0;
            double end=edge.vertical()?box.y1:box.x1;
            if(cross>=start-1e-7 && cross<=end+1e-7)return id;
        }
        return null;
    }
    private void observeDragMotion(double x,double y) {
        long now=clock.getAsLong();
        if(lastDragTime!=0 && now>lastDragTime) {
            double distance=Math.hypot(x-lastDragX,y-lastDragY);
            double velocity=distance*1_000_000_000.0/
                    Math.max(16_000_000L,now-lastDragTime);
            if(distance>=5 && velocity>FAST_DRAG_SPEED) {
                previewPlan=null;
                dockPreview=null;
                previewStartedAt=0;
                dockHoverStartedAt=now;
                dockingIntentArmed=false;
                lastFastDragTime=now;
            }
        }
        lastDragX=x;
        lastDragY=y;
        lastDragTime=now;
    }
    /**
     * Broad, proportional drop sectors, independent of the number of physical
     * pixels between a window's edge and its neighbors. Unlike nearest-edge
     * probing, the lower/right quarters remain easy to acquire on a large zone.
     */
    static UiDockLayout.Side localDropSide(UiRect r,double x,double y) {
        double u=(x-r.x())/Math.max(1,r.w());
        double v=(y-r.y())/Math.max(1,r.h());
        double l=u,rr=1-u,t=v,b=1-v;
        double best=Math.min(Math.min(l,rr),Math.min(t,b));
        // Merging belongs exclusively to the empty tab strip, never the
        // center of a panel body. A central drop defaults to a local top split.
        if(best>=LOCAL_EDGE_FRACTION)return UiDockLayout.Side.TOP;
        if(best==l)return UiDockLayout.Side.LEFT;
        if(best==rr)return UiDockLayout.Side.RIGHT;
        if(best==t)return UiDockLayout.Side.TOP;
        return UiDockLayout.Side.BOTTOM;
    }
    private void clearDockHover() {
        dockPreview=null;
        previewPlan=null;
        previewStartedAt=0;
        dockHoverStartedAt=0;
        dockingIntentArmed=false;
    }
    private void undock(Window w) {
        floating.remove(w);
        remove(w);
        w.open = true;
        floating.add(w);
        relayout();
    }
    /** A top/bottom split must create a usable panel, not just the minimum
     * 36px header height. Left/right splits keep their existing sizing.
     */
    static double defaultSplitFraction(boolean splitLeftRight,int required,int span) {
        double minimum=(required+UiDockLayout.GAP)/(double)Math.max(1,span);
        return splitLeftRight?Math.max(.06,minimum):Math.max(.34,minimum);
    }
    private DockPlan planDrop(Window w,DockHit hit) {
        UiDockLayout candidate=workspace.copy();
        Map<String,Tabs> simulated=new LinkedHashMap<>();
        groups.forEach((id,group)->{
            Tabs clone=new Tabs(id,group.windows.toArray(Window[]::new));
            clone.selected=group.selected;clone.selectedAt=group.selectedAt;
            clone.tabScrollPx=group.tabScrollPx;
            clone.manualTabScroll=group.manualTabScroll;
            simulated.put(id,clone);
        });
        for(Tabs t:new ArrayList<>(simulated.values())) {
            if(!t.windows.remove(w))continue;
            t.selected=Math.min(t.selected,Math.max(0,t.windows.size()-1));
            if(t.windows.isEmpty() && candidate.remove(t.id))simulated.remove(t.id);
            break;
        }
        if(hit.side()==UiDockLayout.Side.CENTER) {
            Tabs target=simulated.get(hit.target());
            if(target==null)return null;
            int index=UiRect.clamp(hit.tabInsertionIndex(),0,target.windows.size());
            target.windows.add(index,w);target.selected=index;
            target.selectedAt=System.nanoTime();
            return new DockPlan(candidate,simulated,hit,null);
        }
        String newId="zone"+nextZoneId;
        if(hit.boundary()!=null) {
            UiDockLayout.Edge edge=hit.boundary();
            boolean vertical=edge.vertical();
            int span=vertical?workArea().w():workArea().h();
            int needed=vertical?w.minDockWidth:w.minDockHeight;
            double requested=(needed+UiDockLayout.GAP)/(double)Math.max(1,span);
            if(!vertical)requested=Math.max(.28,requested);
            double beforeCapacity=Double.POSITIVE_INFINITY;
            double afterCapacity=Double.POSITIVE_INFINITY;
            for(String key:edge.before()) {
                UiDockLayout.Box box=candidate.get(key);
                if(box==null)return null;
                beforeCapacity=Math.min(beforeCapacity,Math.max(0,
                        (vertical?box.width():box.height())-minZoneSpan(key,vertical)));
            }
            for(String key:edge.after()) {
                UiDockLayout.Box box=candidate.get(key);
                if(box==null)return null;
                afterCapacity=Math.min(afterCapacity,Math.max(0,
                        (vertical?box.width():box.height())-minZoneSpan(key,vertical)));
            }
            if(!candidate.insertOnEdge(newId,edge,hit.preferBefore(),requested,
                    beforeCapacity,afterCapacity))return null;
        } else {
        boolean horizontal=hit.side()==UiDockLayout.Side.LEFT ||
                hit.side()==UiDockLayout.Side.RIGHT ||
                hit.side()==UiDockLayout.Side.OUTER_LEFT ||
                hit.side()==UiDockLayout.Side.OUTER_RIGHT;
        UiRect insertionSpace=hit.side().name().startsWith("OUTER_")
                ? workArea() : candidate.rect(hit.target(),workArea());
        if(insertionSpace==null)return null;
        int span=horizontal?insertionSpace.w():insertionSpace.h();
        int required=horizontal?w.minDockWidth:w.minDockHeight;
        double fraction=defaultSplitFraction(horizontal,required,span);
        if(fraction>.85)return null;
        if(!candidate.insert(newId,hit.target(),hit.side(),fraction))return null;
        }
        if(!candidate.coversWorkspace())return null;
        simulated.put(newId,new Tabs(newId,w));
        // Reject plans which cannot fit the new window or make any existing
        // previously-usable panel fall below its declared dock minimum.
        UiRect dropped=candidate.rect(newId,workArea());
        if(dropped==null || dropped.w()<w.minDockWidth || dropped.h()<w.minDockHeight)
            return null;
        for(Tabs group:simulated.values()) {
            UiRect before=workspace.rect(group.id,workArea());
            UiRect after=candidate.rect(group.id,workArea());
            if(before==null||after==null)continue;
            for(Window member:group.windows) {
                if((before.w()>=member.minDockWidth && after.w()<member.minDockWidth) ||
                        (before.h()>=member.minDockHeight && after.h()<member.minDockHeight))
                    return null;
            }
        }
        return new DockPlan(candidate,simulated,hit,newId);
    }
    private void commitPreview(Window window) {
        DockPlan plan=previewPlan;
        if(plan==null)return;
        workspace=plan.geometry;
        groups.clear();groups.putAll(plan.groups);
        if(plan.newZone()!=null)nextZoneId++;
        floating.remove(window);
        window.open=true;
        selectedWindow=window;
    }
    private void remove(Window w) {
        for(Tabs t:new ArrayList<>(groups.values())) {
            int index=t.windows.indexOf(w);
            if(index<0)continue;
            t.windows.remove(index);
            t.selected=UiRect.clamp(t.selected>=index?t.selected-1:t.selected,
                    0,Math.max(0,t.windows.size()-1));
            if(t.windows.isEmpty() && workspace.remove(t.id))groups.remove(t.id);
            break;
        }
    }
    private List<Tabs> tabs() {
        return new ArrayList<>(groups.values());
    }
    public void close(String id) {
        Window w = find(id);
        if (w == null || !w.open) return;
        ctx.setFocus(null);
        w.open = false;
        if (floating.contains(w) && theme.animations)
            closing.add(new Closing(w,floatingBounds(w),System.nanoTime()));
        floating.remove(w);
        remove(w);
        if (selectedWindow == w) selectedWindow = null;
        relayout();
        changed();
    }
    public void reopen(String id) {
        Window w = find(id);
        if (w == null) return;
        if (w.open) {
            if (floating.remove(w)) {
                floating.add(w);
                selectedWindow = w;
                changed();
            } else {
                for (Tabs group : tabs()) if (group.windows.contains(w)) {
                    group.selected = group.windows.indexOf(w);
                    group.selectedAt = System.nanoTime();
                    selectedWindow = w;
                    relayout();
                    break;
                }
            }
            return;
        }
        w.open = true;
        closing.removeIf(entry -> entry.window == w);
        w.openedAt = System.nanoTime();
        w.x = Math.max(0, Math.min(width - 100, w.x));
        w.y = Math.max(28, Math.min(height - 32, w.y));
        floating.add(w);
        selectedWindow = w;
        clampFloating(w);
        relayout();
        changed();
    }

    private void changed() { layoutChanged.run(); }
    public void save(Path path) throws IOException {
        Properties p = new Properties();
        p.setProperty("dock.version","2");
        p.setProperty("zone.count",Integer.toString(groups.size()));
        int zone=0;
        for(Tabs t:tabs()) {
            UiDockLayout.Box b=workspace.get(t.id);
            if(b==null)continue;
            String key="zone."+zone++;
            p.setProperty(key+".rect",b.x0+","+b.y0+","+b.x1+","+b.y1);
            p.setProperty(key+".tabs",String.join(",",t.windows.stream().map(w->w.id).toList()));
            p.setProperty(key+".active",t.active()==null?"":t.active().id);
        }
        p.setProperty("float.count", String.valueOf(floating.size()));
        for (int i = 0; i < floating.size(); i++) {
            Window w = floating.get(i);
            p.setProperty("float." + i, w.id + "," + w.x + "," + w.y + "," + w.width + "," + w.height);
        }
        Files.createDirectories(path.toAbsolutePath().getParent());
        try (OutputStream stream = Files.newOutputStream(path)) { p.store(stream, "Pyro UI rectangular docking layout v2"); }
    }
    public boolean load(Path path) {
        if (!Files.isRegularFile(path)) return false;
        Properties p = new Properties();
        try (InputStream stream = Files.newInputStream(path)) {
            p.load(stream);
            var seen = new java.util.HashSet<String>();
            UiDockLayout parsed=new UiDockLayout();
            Map<String,Tabs> loaded=new LinkedHashMap<>();
            if("2".equals(p.getProperty("dock.version"))) {
                int count=Integer.parseInt(p.getProperty("zone.count","0"));
                if(count<1||count>64)throw new IllegalArgumentException("Invalid zone count");
                for(int i=0;i<count;i++) {
                    String key="zone."+i;
                    String[] coords=p.getProperty(key+".rect","").split(",");
                    if(coords.length!=4)throw new IllegalArgumentException("Incomplete rectangle");
                    UiDockLayout.Box box=new UiDockLayout.Box(
                            Double.parseDouble(coords[0]),Double.parseDouble(coords[1]),
                            Double.parseDouble(coords[2]),Double.parseDouble(coords[3]));
                    if(!Double.isFinite(box.x0)||!Double.isFinite(box.y0)||
                            !Double.isFinite(box.x1)||!Double.isFinite(box.y1))
                        throw new IllegalArgumentException("Invalid zone coordinates");
                    String id="zone"+i;
                    Tabs tabs=new Tabs(id);
                    for(String name:p.getProperty(key+".tabs","").split(",")) {
                        Window w=find(name);
                        if(w!=null&&seen.add(name))tabs.windows.add(w);
                    }
                    String active=p.getProperty(key+".active","");
                    for(int j=0;j<tabs.windows.size();j++)
                        if(tabs.windows.get(j).id.equals(active))tabs.selected=j;
                    parsed.put(id,box);loaded.put(id,tabs);
                }
            } else {
                // One-time migration of the old nested split-tree properties.
                // Convert ratios to planar rectangles without keeping a Split
                // dependency or changing saved window identities.
                if(!readLegacy(p,"dock",new UiDockLayout.Box(0,0,1,1),
                        parsed,loaded,seen,0))return false;
            }
            if(!parsed.coversWorkspace())throw new IllegalArgumentException("Overlapping or incomplete zones");
            floating.clear();
            int count = UiRect.clamp(Integer.parseInt(p.getProperty("float.count", "0")), 0, windows.size());
            for (int i = 0; i < count; i++) {
                String[] parts = p.getProperty("float." + i, "").split(",");
                if (parts.length != 5 || !seen.add(parts[0])) continue;
                Window w = find(parts[0]);
                if (w == null) continue;
                w.x = Integer.parseInt(parts[1]);
                w.y = Integer.parseInt(parts[2]);
                w.width = Integer.parseInt(parts[3]);
                w.height = Integer.parseInt(parts[4]);
                clampFloating(w);
                floating.add(w);
            }
            workspace=parsed;
            groups.clear();groups.putAll(loaded);
            nextZoneId=groups.keySet().stream()
                    .filter(id->id.startsWith("zone"))
                    .mapToInt(id->{
                        try{return Integer.parseInt(id.substring(4))+1;}
                        catch(NumberFormatException ignored){return 0;}
                    }).max().orElse(0);
            for (Window w : windows.values()) w.open = seen.contains(w.id);
            selectedWindow=tabs().stream().map(Tabs::active).filter(java.util.Objects::nonNull)
                    .findFirst().orElse(null);
            relayout(); return true;
        } catch (Exception invalid) {
            defaultLayout(); return false;
        }
    }
    private boolean readLegacy(Properties p,String key,UiDockLayout.Box area,
                               UiDockLayout geometry,Map<String,Tabs> restored,
                               java.util.Set<String> seen,int depth) {
        if(depth>20)return false;
        String kind = p.getProperty(key + ".kind", "");
        if ("tabs".equals(kind)) {
            String id="zone"+restored.size();
            Tabs tabs = new Tabs(id);
            for (String member : p.getProperty(key + ".members", "").split(",")) {
                Window w = find(member);
                if (w != null && seen.add(member)) tabs.windows.add(w);
            }
            String active = p.getProperty(key + ".active", "");
            for (int i = 0; i < tabs.windows.size(); i++) {
                if (tabs.windows.get(i).id.equals(active)) tabs.selected = i;
            }
            geometry.put(tabs.id,area);restored.put(tabs.id,tabs);
            return true;
        }
        if (!"horizontal".equals(kind) && !"vertical".equals(kind)) return false;
        double fraction = Double.parseDouble(p.getProperty(key + ".ratio", "0.5"));
        if (!Double.isFinite(fraction)) fraction = .5;
        fraction=Math.max(.1,Math.min(.9,fraction));
        UiDockLayout.Box a=area.copy(),b=area.copy();
        if(kind.equals("horizontal")) {
            double x=area.x0+area.width()*fraction;
            a.x1=x;b.x0=x;
        } else {
            double y=area.y0+area.height()*fraction;
            a.y1=y;b.y0=y;
        }
        return readLegacy(p,key+".a",a,geometry,restored,seen,depth+1) &&
                readLegacy(p,key+".b",b,geometry,restored,seen,depth+1);
    }
}
