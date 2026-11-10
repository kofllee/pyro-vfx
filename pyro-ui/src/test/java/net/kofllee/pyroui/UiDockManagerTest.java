package net.kofllee.pyroui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class UiDockManagerTest {
    @TempDir Path temp;

    private static UiDockManager manager(int w, int h) {
        UiDockManager ui = new UiDockManager(new UiTheme());
        for (String name : new String[]{"hierarchy", "preview", "inspector", "curve", "console", "settings"}) {
            ui.window(name, name, UiElement.column(4, 4).add(new UiControls.Label(name)));
        }
        ui.find("hierarchy").dockMinimum(125, 140);
        ui.find("preview").dockMinimum(230, 170);
        ui.find("inspector").dockMinimum(180, 170);
        ui.find("curve").dockMinimum(220, 140);
        ui.find("console").dockMinimum(165, 140);
        ui.resize(w, h);
        ui.defaultLayout();
        return ui;
    }
    private static void dwell(UiDockManager ui,AtomicLong clock,int x,int y) {
        clock.addAndGet(700_000_000L);
        ui.pointerMove(x,y);
    }

    private static final class LayoutCounter extends UiElement {
        int calls;
        @Override public void layout(UiRect bounds,UiContext context) {
            calls++;
            super.layout(bounds,context);
        }
    }

    @Test void everyWidgetIconStaysSquareAndInsideItsControl() {
        class IconCanvas implements UiCanvas {
            UiRect control;
            int icons;
            @Override public void fill(int x,int y,int w,int h,int color) {}
            @Override public void text(String value,int x,int y,int color) {}
            @Override public int measure(String value) { return value.length()*6; }
            @Override public void clip(UiRect r) {}
            @Override public void unclip() {}
            @Override public void icon(String id,int x,int y,int side,int color) {
                icons++;
                assertTrue(side>0,"No zero-size icon: "+id);
                assertTrue(x>=control.x() && y>=control.y(),
                        "Icon "+id+" starts outside its control "+control);
                assertTrue(x+side<=control.x()+control.w() &&
                        y+side<=control.y()+control.h(),
                        "Icon "+id+" overflows its control "+control+
                        " (square slot "+side+" at "+x+","+y+")");
            }
        }
        IconCanvas canvas=new IconCanvas();
        UiContext context=new UiContext(new UiTheme());
        UiElement[] elements=new UiElement[]{
                new UiControls.IconButton("close",()->{}),
                new UiControls.Button("Emitter",()->{}).section(true),
                new UiControls.Check("Collision",true,v->{}),
                new UiControls.NumberField(95,40,250,1,v->{}),
                new UiControls.Dropdown(java.util.List.of("Point","Box"),0,v->{}),
                new UiControls.Swatch(0xFFFF6432,v->{})
        };
        int[][] sizes={{22,22},{140,24},{130,24},{80,24},{100,24},{95,24}};
        for(int i=0;i<elements.length;i++) {
            UiRect box=new UiRect(7,8,sizes[i][0],sizes[i][1]);
            elements[i].layout(box,context);
            canvas.control=box;
            int before=canvas.icons;
            elements[i].paint(canvas,context);
            assertTrue(canvas.icons>before,"Icon did not render within "+elements[i].getClass());
        }
        // Compressed controls should omit icons rather than paint outside.
        UiElement tiny=new UiControls.Dropdown(java.util.List.of("A"),0,v->{});
        UiRect cramped=new UiRect(0,0,17,13);
        tiny.layout(cramped,context);
        canvas.control=cramped;
        int before=canvas.icons;
        tiny.paint(canvas,context);
        assertEquals(before,canvas.icons);
    }

    @Test void onePixelCornerNotchHasNoPartialCoverageOrSteppedRadius() {
        class PixelCanvas implements UiCanvas {
            final int[][] pixels = new int[9][9];
            @Override public void fill(int x,int y,int w,int h,int argb) {
                for(int row=y;row<y+h;row++) for(int col=x;col<x+w;col++)
                    if(row>=0 && row<9 && col>=0 && col<9) pixels[row][col]=argb;
            }
            @Override public void text(String value,int x,int y,int color) {}
            @Override public int measure(String value) { return value.length(); }
            @Override public void clip(UiRect r) {}
            @Override public void unclip() {}
        }
        assertEquals(1,UiTheme.R_CONTROL);
        assertEquals(1,UiTheme.R_PANEL);
        assertEquals(1,UiTheme.R_FLOAT);
        UiRect bounds = new UiRect(0,0,9,9);
        for(boolean border : new boolean[]{false,true}) {
            PixelCanvas canvas=new PixelCanvas();
            if(border) canvas.roundedBorder(bounds,UiTheme.R_PANEL,0xFFFFFFFF);
            else canvas.rounded(bounds,UiTheme.R_PANEL,0xFFFFFFFF);
            for(int y : new int[]{0,8}) for(int x:new int[]{0,8})
                assertEquals(0,canvas.pixels[y][x],"Only corner pixel is missing");
            for(int y:new int[]{0,8}) for(int x:new int[]{1,7})
                assertEquals(0xFFFFFFFF,canvas.pixels[y][x]);
            for(int y:new int[]{1,7}) for(int x:new int[]{0,8})
                assertEquals(0xFFFFFFFF,canvas.pixels[y][x]);
            assertEquals(border ? 0 : 0xFFFFFFFF,canvas.pixels[4][4]);
        }
    }

    @Test void movingFloatingWindowMustNotRelayoutAllDockedWindows() {
        UiDockManager ui=manager(1400,800);
        LayoutCounter other=new LayoutCounter();
        ui.find("hierarchy").content=other;
        ui.resize(1400,800);
        ui.reopen("settings");
        UiRect r=ui.area("settings");
        assertTrue(ui.pointerDown(r.x()+30,r.y()+9,0));
        int original=other.calls;
        for(int i=0;i<120;i++) ui.pointerMove(340+i,180+i%20);
        assertEquals(original,other.calls,
                "Mouse movement must not re-layout unrelated docked windows");
        ui.pointerUp(460,190,0);
        assertTrue(other.calls <= original + 1,
                "A single post-drop re-layout is acceptable for persistence");
    }

    @Test void curveDrawCostDropsDuringDraggingWithoutChangingInterpolation() {
        UiControls.Curve curve=new UiControls.Curve(()->{});
        UiContext context=new UiContext(new UiTheme());
        curve.layout(new UiRect(0,0,860,170),context);
        class CountingCanvas implements UiCanvas {
            int fills;
            @Override public void fill(int x,int y,int w,int h,int argb) { fills++; }
            @Override public void text(String str,int x,int y,int color) {}
            @Override public int measure(String str) {return str.length()*7;}
            @Override public void clip(UiRect r) {}
            @Override public void unclip() {}
        }
        CountingCanvas regular=new CountingCanvas(),drag=new CountingCanvas();
        curve.paint(regular,context);
        context.lowDetail=true;
        curve.paint(drag,context);
        assertTrue(drag.fills < regular.fills/2,
                "Drag mode should submit substantially fewer curve primitives");
        assertEquals(curve.sample(.37),curve.sample(.37),1e-10);
    }

    @Test void intraGroupSpacingIsSmallerThanInterGroupAndRowsDoNotOverlap() {
        assertTrue(UiTheme.SPACE_INLINE < UiTheme.SPACE_ITEM);
        assertTrue(UiTheme.SPACE_ITEM < UiTheme.SPACE_GROUP);
        UiElement root=UiElement.column(UiTheme.SPACE_GROUP,0);
        UiElement first=UiElement.column(4,0)
                .add(new UiControls.Label("Title").height(19))
                .add(new UiControls.Button("Action",()->{}).height(24)).height(47);
        UiElement second=UiElement.column(4,0)
                .add(new UiControls.Label("Other").height(19))
                .add(new UiControls.Button("Action",()->{}).height(24)).height(47);
        root.add(first).add(second);
        root.layout(new UiRect(0,0,220,130),new UiContext(new UiTheme()));
        int within=first.children.get(1).bounds().y() -
                (first.children.get(0).bounds().y()+first.children.get(0).bounds().h());
        int between=second.bounds().y()-(first.bounds().y()+first.bounds().h());
        assertTrue(within<between,"Spacing between groups must exceed spacing within each group");
        assertEquals(4,within);
        assertEquals(UiTheme.SPACE_GROUP,between);
        UiElement cramped=UiElement.row(5,2)
                .add(new UiControls.Label("Lengthy property").width(105))
                .add(new UiControls.NumberField(24,0,100,1,v->{}).width(80));
        cramped.layout(new UiRect(0,0,110,25),new UiContext(new UiTheme()));
        UiRect a=cramped.children.get(0).bounds(),b=cramped.children.get(1).bounds();
        assertTrue(a.x()+a.w()+5<=b.x(),"Squeezed row must not overlap its controls");
        assertTrue(b.x()+b.w()<=110,"Inputs must remain inside the row");
    }

    @Test void floatingWindowsNeverEscapeViewportAtExtremeZoom() {
        UiDockManager ui=manager(220,155);
        ui.reopen("settings");
        ui.resize(220,155);
        UiRect rect=ui.area("settings");
        assertTrue(rect.x()>=0 && rect.y()>=0);
        assertTrue(rect.x()+rect.w()<=220 && rect.y()+rect.h()<=155,
                "All floating controls must remain reachable even at high scale");
    }

    @Test void defaultLayoutKeepsEveryWindowReachable() {
        UiDockManager ui = manager(1280, 720);
        for (String name : new String[]{"hierarchy", "preview", "inspector", "curve", "console"}) {
            assertTrue(ui.visible(name));
            assertNotNull(ui.area(name));
            assertTrue(ui.area(name).w() > 0);
            assertTrue(ui.area(name).h() > 0);
        }
        assertFalse(ui.visible("settings"), "Settings can be opened without blocking the initial workspace");
        assertFalse(ui.floating("inspector"));
    }
    @Test void workspaceGapsAreOpaqueAndMinecraftViewportHasItsOwnBody() {
        UiDockManager ui=manager(1280,720);
        ui.find("preview").minecraftViewport();
        class Canvas implements UiCanvas {
            final java.util.List<UiRect> paints=new ArrayList<>();
            UiRect game;
            @Override public void fill(int x,int y,int w,int h,int color) {
                if(color==UiTheme.BG)paints.add(new UiRect(x,y,w,h));
            }
            @Override public void minecraftFrame(UiRect target) {game=target;}
            @Override public void text(String str,int x,int y,int color) {}
            @Override public int measure(String str) {return str.length()*7;}
            @Override public void clip(UiRect r) {}
            @Override public void unclip() {}
        }
        Canvas c=new Canvas();
        ui.draw(c,0,0);
        assertFalse(c.paints.isEmpty());
        assertEquals(new UiRect(0,0,1280,720),c.paints.get(0),
                "No gap or corner is allowed to expose the live Minecraft framebuffer");
        assertNotNull(c.game);
        assertTrue(c.game.w()>100 && c.game.h()>100);
        assertEquals(ui.area("preview").x()+1,c.game.x());
    }

    @Test void dragFloatingToTabbedPreviewAndUndockInspector() {
        UiDockManager ui = manager(1400, 800);
        AtomicLong time=new AtomicLong(100_000_000L);
        ui.clockForTesting(time::get);
        ui.reopen("settings");
        UiRect settings = ui.area("settings");
        assertTrue(ui.pointerDown(settings.x() + 30, settings.y() + 10, 0));
        UiRect preview = ui.area("preview");
        int cx = preview.x() + preview.w() - 14;
        int cy = preview.y() + 9;
        ui.pointerMove(cx,cy);
        assertFalse(ui.indicatorsVisible(),"Normal floating movement must not attach immediately");
        dwell(ui,time,cx,cy);
        assertTrue(ui.indicatorsVisible(),"Preview appears after a stationary dwell");
        assertEquals("preview",ui.hoverTargetId());
        ui.pointerUp(cx,cy,0);
        assertFalse(ui.floating("settings"),"Empty header space adds an ordinary tab");
        assertEquals(ui.area("settings"),ui.area("preview"));

        UiRect inspector = ui.area("inspector");
        ui.pointerDown(inspector.x() + 20, inspector.y() + 8, 0);
        ui.pointerMove(480, 8);
        ui.pointerUp(480, 8, 0);
        assertTrue(ui.floating("inspector"), "Dragging tab outside all dock regions undocks");
    }

    @Test void livePreviewImmediatelyFollowsTargetWithoutGlobalMarkers() {
        UiDockManager ui = manager(1400,800);
        AtomicLong time=new AtomicLong(100_000_000L);
        ui.clockForTesting(time::get);
        ui.reopen("settings");
        UiRect origin=ui.area("settings");
        UiRect preview=ui.area("preview");
        UiRect inspector=ui.area("inspector");
        int px=preview.x()+preview.w()-14,py=preview.y()+9;
        ui.pointerDown(origin.x()+30,origin.y()+9,0);
        ui.pointerMove(px,py);
        assertFalse(ui.indicatorsVisible());
        dwell(ui,time,px,py);
        assertEquals("preview",ui.hoverTargetId());
        assertTrue(ui.indicatorsVisible());
        int ix=inspector.x()+inspector.w()-14, iy=inspector.y()+9;
        ui.pointerMove(ix,iy);
        assertTrue(ui.indicatorsVisible(),
                "Once docking intent is armed, moving to another zone changes the preview immediately");
        assertEquals("inspector",ui.hoverTargetId());
        ui.pointerUp(ix,iy,0);
        assertFalse(ui.floating("settings"));
        assertEquals(ui.area("settings"),ui.area("inspector"));
    }

    @Test void mouseReleaseCannotDockWithoutAnAlreadyShownPreview() {
        UiDockManager ui=manager(1400,800);
        AtomicLong time=new AtomicLong(100_000_000L);
        ui.clockForTesting(time::get);
        ui.reopen("settings");
        UiRect source=ui.area("settings"), target=ui.area("preview");
        int x=target.x()+target.w()/2,y=target.y()+target.h()/2;
        ui.pointerDown(source.x()+30,source.y()+8,0);
        ui.pointerMove(x,y);
        time.addAndGet(900_000_000L);
        // No intermediate render or pointer move: never snap on release.
        ui.pointerUp(x,y,0);
        assertTrue(ui.floating("settings"));
    }

    @Test void dockingIntentTracksRegionsRatherThanMotionlessMousePixels() {
        UiDockManager ui=manager(1400,800);
        AtomicLong time=new AtomicLong(100_000_000L);
        ui.clockForTesting(time::get);
        ui.reopen("settings");
        UiRect s=ui.area("settings"), target=ui.area("preview");
        int x=target.x()+target.w()-14,y=target.y()+9;
        ui.pointerDown(s.x()+24,s.y()+9,0);
        ui.pointerMove(x-8,y);
        time.addAndGet(185_000_000L);
        ui.pointerMove(x+4,y);
        assertFalse(ui.indicatorsVisible());
        time.addAndGet(185_000_000L);
        ui.pointerMove(x-4,y);
        assertTrue(ui.indicatorsVisible(),"Continuous movement within one zone must not reset its timer");
        assertEquals(target,ui.previewArea());
        int bottom=target.y()+target.h()*3/4;
        ui.pointerMove(target.x()+target.w()/2,bottom);
        UiRect newPreview=ui.previewArea();
        assertNotNull(newPreview,"Armed docking immediately follows the new local sector");
        assertTrue(newPreview.y()>target.y());
        ui.pointerUp(target.x()+target.w()/2,bottom,0);
        assertEquals(newPreview,ui.area("settings"));
    }

    @Test void aSecondDragMustNotInheritDockIntentFromPreviousDrag() {
        UiDockManager ui=manager(1400,800);
        AtomicLong clock=new AtomicLong(50_000_000L);
        ui.clockForTesting(clock::get);
        ui.reopen("settings");
        UiRect start=ui.area("settings"),target=ui.area("preview");
        int x=target.x()+target.w()-14,y=target.y()+9;
        ui.pointerDown(start.x()+22,start.y()+9,0);
        ui.pointerMove(x,y);
        dwell(ui,clock,x,y);
        assertTrue(ui.indicatorsVisible());
        ui.pointerUp(x,y,0);
        assertFalse(ui.floating("settings"));

        UiRect docked=ui.area("settings");
        // Settings occupies the second tab after the original Preview tab.
        ui.pointerDown(docked.x()+190,docked.y()+8,0);
        ui.pointerMove(x+50,y+40);
        assertTrue(ui.floating("settings"));
        assertFalse(ui.indicatorsVisible(),"Dock intent must reset after the last drag");
    }

    @Test void narrowPanelCanStillBeLocallySplitWhenMinimumNeedsOver45Percent() {
        UiDockManager ui=manager(1700,800);
        AtomicLong clock=new AtomicLong(50_000_000L);
        ui.clockForTesting(clock::get);
        ui.reopen("settings");
        UiRect original=ui.area("settings"),hierarchy=ui.area("hierarchy");
        int x=hierarchy.x()+25,y=hierarchy.y()+hierarchy.h()/2;
        ui.pointerDown(original.x()+30,original.y()+8,0);
        ui.pointerMove(x,y);
        dwell(ui,clock,x,y);
        UiRect proposed=ui.previewArea();
        assertNotNull(proposed,"Minimum-compatible split should not be rejected by a fixed 45% fraction cap");
        assertEquals(hierarchy.x(),proposed.x());
        assertTrue(proposed.w()>=ui.find("settings").minDockWidth);
    }

    @Test void dropSectorsHaveBroadTopBottomLeftRightAndCenterAreas() {
        UiRect rect=new UiRect(100,60,680,460);
        assertEquals(UiDockLayout.Side.LEFT,UiDockManager.localDropSide(rect,190,290));
        assertEquals(UiDockLayout.Side.RIGHT,UiDockManager.localDropSide(rect,700,290));
        assertEquals(UiDockLayout.Side.TOP,UiDockManager.localDropSide(rect,440,135));
        assertEquals(UiDockLayout.Side.BOTTOM,UiDockManager.localDropSide(rect,440,445));
        assertEquals(UiDockLayout.Side.TOP,UiDockManager.localDropSide(rect,440,290));
    }

    @Test void hoveringExistingTabInsertsImmediatelyAfterIt() throws Exception {
        UiDockManager ui=manager(1400,800);
        AtomicLong time=new AtomicLong(100_000_000L);
        ui.clockForTesting(time::get);
        for(String id:new String[]{"floatingA","floatingB"}) {
            ui.window(id,id,UiElement.column(2,2).add(new UiControls.Label(id)));
            ui.find(id).open=false;
            ui.reopen(id);
            UiRect source=ui.area(id),target=ui.area("preview");
            int x=target.x()+25,y=target.y()+9;
            ui.pointerDown(source.x()+25,source.y()+8,0);
            ui.pointerMove(x,y);
            dwell(ui,time,x,y);
            assertNotNull(ui.previewArea(),"Dragging onto the existing tab must show a preview");
            ui.pointerUp(x,y,0);
        }
        Path file=temp.resolve("insert-after-tab.properties");
        ui.save(file);
        var props=new java.util.Properties();
        try(var input=java.nio.file.Files.newInputStream(file)){props.load(input);}
        assertTrue(props.values().stream().anyMatch(v->
                v.equals("preview,floatingB,floatingA")),
                "A tab hovered after preview must be inserted before the formerly second tab");
    }
    @Test void horizontalDockUsesSubstantialDefaultHeight() {
        assertEquals(.34,UiDockManager.defaultSplitFraction(false,36,600),1e-8);
        assertEquals(.34,UiDockManager.defaultSplitFraction(false,36,250),1e-8);
        assertTrue(UiDockManager.defaultSplitFraction(false,170,450)>.34);
        assertTrue(UiDockManager.defaultSplitFraction(true,160,600)<.34,
                "Left/right splitting must keep its old minimum-based sizing");
    }
    @Test void draggedWindowBecomesTranslucentOnlyWhenDockPreviewIsActive() {
        UiDockManager ui=manager(1400,800);
        AtomicLong time=new AtomicLong(100_000_000L);
        ui.clockForTesting(time::get);
        ui.reopen("settings");
        UiRect source=ui.area("settings"),target=ui.area("preview");
        int x=target.x()+25,y=target.y()+9;
        ui.pointerDown(source.x()+25,source.y()+8,0);
        ui.pointerMove(x,y);
        dwell(ui,time,x,y);
        assertNotNull(ui.previewArea());
        java.util.concurrent.atomic.AtomicBoolean translucent=new java.util.concurrent.atomic.AtomicBoolean();
        UiCanvas canvas=new UiCanvas() {
            @Override public void fill(int x,int y,int w,int h,int color) {}
            @Override public void text(String value,int x,int y,int color) {}
            @Override public int measure(String value) {return value.length()*6;}
            @Override public void clip(UiRect rect) {}
            @Override public void unclip() {}
            @Override public void pushLayer(float opacity,int offsetY) {
                if(opacity>0.25f && opacity<0.50f)translucent.set(true);
            }
        };
        ui.draw(canvas,x,y);
        assertTrue(translucent.get(),
                "Floating window must be translucent so the proposed dock remains visible behind it");
    }
    @Test void sharedDockLineOffersDropOnEitherSideIncludingTJunctions() {
        UiDockManager ui=manager(1400,800);
        AtomicLong time=new AtomicLong(100_000_000L);
        ui.clockForTesting(time::get);
        ui.reopen("settings");
        UiRect source=ui.area("settings");
        UiRect preview=ui.area("preview");
        UiRect hierarchy=ui.area("hierarchy");
        int line=(hierarchy.x()+hierarchy.w()+preview.x())/2;
        int y=preview.y()+preview.h()/2;
        ui.pointerDown(source.x()+25,source.y()+9,0);
        ui.pointerMove(line-10,y);
        dwell(ui,time,line-10,y);
        UiRect before=ui.previewArea();
        assertNotNull(before,"The shared divider is a docking target, not a dead gap");
        ui.pointerMove(line+10,y);
        UiRect after=ui.previewArea();
        assertNotNull(after,"Approaching the same divider from the other side also works");
        assertEquals(before,after,
                "A matching shared seam must produce one centered drop zone");
        ui.pointerUp(line+10,y,0);
        assertEquals(after,ui.area("settings"),"Drop matches the shared-edge preview");
    }
    @Test void minecraftInspectorCommonSeamAlwaysInsertsBetweenBothPanels() {
        UiDockManager ui=manager(1800,800);
        ui.defaultLayout();
        AtomicLong time=new AtomicLong(100_000_000L);
        ui.clockForTesting(time::get);
        ui.reopen("settings");
        UiRect source=ui.area("settings");
        UiRect mc=ui.area("preview"),inspector=ui.area("inspector");
        assertEquals(mc.y(),inspector.y());
        assertEquals(mc.y()+mc.h(),inspector.y()+inspector.h());
        int seam=(mc.x()+mc.w()+inspector.x())/2;
        int y=mc.y()+mc.h()/2;
        ui.pointerDown(source.x()+25,source.y()+9,0);
        // These locations are outside the old 120px matching-edge hotspot,
        // but still inside the two formerly separate RIGHT/LEFT local zones.
        ui.pointerMove(seam-200,y);
        dwell(ui,time,seam-200,y);
        UiRect fromMinecraft=ui.previewArea();
        assertNotNull(fromMinecraft,"Full seam must replace Minecraft's local RIGHT zone");
        assertNotEquals(mc,ui.projectedArea("preview"));
        assertNotEquals(inspector,ui.projectedArea("inspector"));
        assertEquals(seam,(fromMinecraft.x()+fromMinecraft.x()+fromMinecraft.w())/2,2,
                "Insertion must be centered on the shared seam");
        ui.pointerMove(seam+145,y);
        UiRect fromInspector=ui.previewArea();
        assertEquals(fromMinecraft,fromInspector,
                "Inspector's local LEFT zone must resolve to the SAME central insertion");
        ui.pointerUp(seam+145,y,0);
        assertEquals(fromMinecraft,ui.area("settings"));
    }
    @Test void unequalLengthSharedEdgesDockLocallyWithoutMovingOtherPanels() {
        UiDockManager ui=manager(1400,800);
        AtomicLong time=new AtomicLong(100_000_000L);
        ui.clockForTesting(time::get);
        ui.reopen("settings");
        UiRect source=ui.area("settings"),preview=ui.area("preview");
        UiRect curve=ui.area("curve"),console=ui.area("console");
        UiRect inspector=ui.area("inspector");
        int x=preview.x()+preview.w()/2;
        int seam=(preview.y()+preview.h()+curve.y())/2;
        ui.pointerDown(source.x()+25,source.y()+8,0);
        ui.pointerMove(x,seam-10);
        dwell(ui,time,x,seam-10);
        UiRect topDrop=ui.previewArea();
        assertNotNull(topDrop,"Top side of a T-junction should still dock");
        assertEquals(preview.x(),topDrop.x());
        assertEquals(preview.w(),topDrop.w());
        assertEquals(curve,ui.projectedArea("curve"));
        assertEquals(console,ui.projectedArea("console"));
        assertEquals(inspector,ui.projectedArea("inspector"));
        ui.pointerMove(x,seam+10);
        UiRect bottomDrop=ui.previewArea();
        assertNotNull(bottomDrop,"Bottom side should dock in its own panel");
        assertEquals(curve.x(),bottomDrop.x());
        assertEquals(curve.w(),bottomDrop.w());
        assertEquals(preview,ui.projectedArea("preview"));
        assertEquals(console,ui.projectedArea("console"));
        ui.pointerUp(x,seam+10,0);
        assertEquals(bottomDrop,ui.area("settings"),
                "Committed local geometry should exactly match the preview");
    }

    @Test void defaultWindowsCanShrinkToCompactHeaderSizes() {
        UiDockManager ui=new UiDockManager(new UiTheme());
        for(int i=0;i<5;i++)
            ui.window("panel"+i,"Panel "+i,
                    UiElement.column(3,3).add(new UiControls.Label("Content")));
        var small=ui.find("panel0");
        small.size(1,1);
        assertEquals(UiDockManager.MIN_WIDTH,small.width);
        assertEquals(UiDockManager.MIN_HEIGHT,small.height);
        assertEquals(UiDockManager.MIN_WIDTH,small.minDockWidth);
        assertEquals(UiDockManager.MIN_HEIGHT,small.minDockHeight);
        ui.resize(1280,720);
        ui.defaultLayout();
        UiRect left=ui.area("panel0"),middle=ui.area("panel1");
        int separator=(left.x()+left.w()+middle.x())/2;
        int y=left.y()+left.h()/2;
        ui.pointerDown(separator,y,0);
        ui.pointerMove(separator-600,y);
        ui.pointerUp(separator-600,y,0);
        UiRect reduced=ui.area("panel0");
        assertTrue(reduced.w()>=UiDockManager.MIN_WIDTH);
        assertTrue(reduced.w()<=UiDockManager.MIN_WIDTH+16,
                "The dock splitter should reach the compact minimum, not a large panel-specific limit");
    }

    @Test void fastMotionCancelsDockingUntilTheWindowSettles() {
        UiDockManager ui=manager(1400,800);
        AtomicLong time=new AtomicLong(100_000_000L);
        ui.clockForTesting(time::get);
        ui.reopen("settings");
        UiRect source=ui.area("settings"),target=ui.area("preview");
        int x=target.x()+target.w()/2,y=target.y()+target.h()/2;
        ui.pointerDown(source.x()+25,source.y()+8,0);
        ui.pointerMove(x,y);
        dwell(ui,time,x,y);
        assertTrue(ui.indicatorsVisible());
        time.addAndGet(16_000_000L);
        ui.pointerMove(x+110,y);
        assertFalse(ui.indicatorsVisible(),"Fast dragging should dismiss the active preview");
        time.addAndGet(180_000_000L);
        ui.pointerMove(x+110,y);
        assertFalse(ui.indicatorsVisible(),"An active gesture should not instantly reattach");
        time.addAndGet(355_000_000L);
        ui.pointerMove(x+110,y);
        assertTrue(ui.indicatorsVisible(),"Preview can reappear after settling in one region");
    }

    @Test void gameplayCaptureAndResizeHandleNeverCompeteForSameClick() {
        UiDockManager ui=manager(1400,800);
        ui.find("preview").minecraftViewport();
        UiRect game=ui.area("preview");
        int insideX=game.x()+game.w()/2,insideY=game.y()+game.h()/2;
        assertTrue(ui.minecraftViewportAt(insideX,insideY));
        assertEquals(UiDockManager.Cursor.ARROW,ui.cursor(insideX,insideY));
        UiRect right=ui.area("inspector");
        int split=(game.x()+game.w()+right.x())/2;
        int y=game.y()+game.h()/2;
        assertFalse(ui.minecraftViewportAt(split-2,y),
                "The same resize line cannot also capture FPS mouse input");
        assertEquals(UiDockManager.Cursor.RESIZE_HORIZONTAL,ui.cursor(split-2,y));
        ui.reopen("settings");
        ui.find("settings").at(insideX-75,insideY-45).size(210,150);
        ui.resize(1400,800);
        assertFalse(ui.minecraftViewportAt(insideX,insideY),
                "An overlay must block game input to the world behind it");
    }

    @Test void overlappedElementsDoNotPaintHoverBehindFloatingWindowsOrMenus() {
        UiDockManager ui=manager(1400,800);
        class HoverProbe extends UiElement {
            boolean hovered;
            @Override protected void drawSelf(UiCanvas c,UiContext ctx) {
                hovered=bounds.contains(ctx.mouseX,ctx.mouseY);
            }
        }
        class BareCanvas implements UiCanvas {
            @Override public void fill(int x,int y,int w,int h,int argb) {}
            @Override public void text(String s,int x,int y,int color) {}
            @Override public int measure(String s) {return s.length()*7;}
            @Override public void clip(UiRect r) {}
            @Override public void unclip() {}
        }
        HoverProbe background=new HoverProbe(),above=new HoverProbe();
        ui.find("preview").content=background;
        ui.reopen("settings");
        ui.find("settings").content=above;
        UiRect pv=ui.area("preview");
        ui.find("settings").at(pv.x()+40,pv.y()+80).size(250,200);
        ui.resize(1400,800);
        UiRect top=ui.area("settings");
        int x=top.x()+45,y=top.y()+60;
        ui.draw(new BareCanvas(),x,y);
        assertTrue(above.hovered,"Topmost floating content must receive hover");
        assertFalse(background.hovered,"Covered dock content must NOT show hover");

        ui.context().menu(new UiRect(x-20,y-26,120,20),
                java.util.List.of("Menu item"),index->{});
        ui.draw(new BareCanvas(),x,y);
        assertFalse(above.hovered,"Popup menu must block hover through to floating controls");
        assertFalse(background.hovered);
    }

    @Test void freeDragOutsideWorkspaceRemainsFloating() {
        UiDockManager ui=manager(1400,800);
        ui.reopen("settings");
        UiRect origin=ui.area("settings");
        ui.pointerDown(origin.x()+25,origin.y()+9,0);
        ui.pointerMove(440,18);
        assertFalse(ui.indicatorsVisible());
        assertNull(ui.previewArea());
        ui.pointerUp(440,18,0);
        assertTrue(ui.floating("settings"),"Outside workspace never docks");
    }

    @Test void saveAndRestoreCloseReopenAndClampToNewResolution() throws Exception {
        UiDockManager ui = manager(1400, 800);
        ui.close("console");
        assertFalse(ui.visible("console"));
        ui.reopen("console");
        ui.reopen("settings");
        assertTrue(ui.floating("console"));
        Path config = temp.resolve("settings/docking.properties");
        ui.save(config);

        UiDockManager fresh = manager(820, 480);
        assertTrue(fresh.load(config));
        assertTrue(fresh.floating("console"));
        assertTrue(fresh.floating("settings"));
        assertNotNull(fresh.area("inspector"));
        assertTrue(fresh.area("console").x() < 820);
        assertTrue(fresh.area("console").y() < 480);
    }

    @Test void invalidSavedLayoutDoesNotDestroyDefault() throws Exception {
        UiDockManager ui = manager(900, 600);
        Path path = temp.resolve("broken.properties");
        java.nio.file.Files.writeString(path, "dock.kind=horizontal\ndock.ratio=nan\n");
        assertFalse(ui.load(path));
        assertTrue(ui.visible("hierarchy"));
        assertTrue(ui.visible("preview"));
    }

    @Test void draggingSplitKeepsPositivePanelSizesAtExtremePositions() {
        UiDockManager ui = manager(1280, 720);
        UiRect hierarchy = ui.area("hierarchy");
        ui.pointerDown(hierarchy.x() + hierarchy.w() + 2, hierarchy.y() + 100, 0);
        ui.pointerMove(-900, hierarchy.y() + 100);
        ui.pointerUp(-900, hierarchy.y() + 100, 0);
        for (String name : new String[]{"hierarchy", "preview", "inspector", "curve", "console"}) {
            UiRect rect = ui.area(name);
            assertNotNull(rect);
            assertTrue(rect.w() >= 0);
            assertTrue(rect.h() >= 0);
        }
    }

    @Test void menuReceivesTopmostClicks() {
        UiDockManager ui = manager(1280, 720);
        ui.close("console");
        assertFalse(ui.visible("console"));
        ui.pointerDown(190, 10, 0);
        assertTrue(ui.context().popupVisible());
        ui.pointerDown(190, 40, 0);
        assertTrue(ui.visible("console"));
        assertTrue(ui.floating("console"));
    }

    @Test void curvePointsActuallyChangeWhenDragged() {
        UiTheme theme = new UiTheme();
        UiContext ctx = new UiContext(theme);
        AtomicInteger updates = new AtomicInteger();
        UiControls.Curve curve = new UiControls.Curve(updates::incrementAndGet);
        curve.layout(new UiRect(0, 0, 300, 180), ctx);
        UiControls.Point first = curve.points().get(0);
        assertTrue(curve.onPointerDown(ctx, 29, 143, 0));
        curve.onPointerMove(ctx, 30, 25);
        curve.onPointerUp(ctx, 30, 25, 0);
        assertNotEquals(first.value(), curve.points().get(0).value());
        assertTrue(updates.get() >= 1);
    }

    @Test void dockedPanelsNeverOverlapAtSeveralViewportSizes() {
        String[] ids = {"hierarchy", "preview", "inspector", "curve", "console"};
        for (int[] size : new int[][] {{1280,720}, {950,535}, {700,430}, {460,280}}) {
            UiDockManager ui = manager(size[0], size[1]);
            for (int i=0; i<ids.length; i++) for (int j=i+1; j<ids.length; j++) {
                UiRect a=ui.area(ids[i]), b=ui.area(ids[j]), intersection=a.intersect(b);
                assertEquals(0, intersection.w()*intersection.h(),
                        ids[i] + " overlaps " + ids[j] + " at " + size[0] + "x" + size[1]);
            }
        }
    }

    @Test void sliderAndNumberFieldRemainSynchronized() {
        UiControls.Slider[] slider = new UiControls.Slider[1];
        UiControls.NumberField[] number = new UiControls.NumberField[1];
        slider[0] = new UiControls.Slider(.5, 0, 1, v -> number[0].setNumber(v));
        number[0] = new UiControls.NumberField(.5, 0, 1, .01, v -> slider[0].setNumber(v));
        UiContext ctx = new UiContext(new UiTheme());
        slider[0].layout(new UiRect(0,0,100,24), ctx);
        slider[0].onPointerDown(ctx, 80, 12, 0);
        slider[0].onPointerUp(ctx, 80, 12, 0);
        assertEquals(slider[0].numberValue(), number[0].numberValue(), 1e-10);
        number[0].setText("0.25");
        number[0].layout(new UiRect(0,0,110,24), ctx);
        number[0].onPointerDown(ctx, 95, 12, 0);
        number[0].onKey(ctx, 65, 2);
        number[0].onChar(ctx, '0');
        number[0].onChar(ctx, '.');
        number[0].onChar(ctx, '9');
        assertEquals(.9, slider[0].numberValue(), 1e-10);
    }

    @Test void centerPlacementIsARealTabAndNotAHiddenDropMarker() {
        UiDockManager ui = manager(1400, 800);
        AtomicLong time=new AtomicLong(100_000_000L);
        ui.clockForTesting(time::get);
        ui.reopen("settings");
        UiRect start = ui.area("settings");
        UiRect preview = ui.area("preview");
        int x = preview.x() + preview.w() - 14, y = preview.y() + 9;
        ui.pointerDown(start.x() + 22, start.y() + 8, 0);
        ui.pointerMove(x, y);
        dwell(ui,time,x,y);
        assertTrue(ui.indicatorsVisible());
        assertEquals(preview,ui.previewArea());
        ui.pointerUp(x, y, 0);
        assertFalse(ui.floating("settings"));
        assertEquals(ui.area("settings"),ui.area("preview"));
    }

    @Test void panelBodyCannotMergeTabsButEmptyHeaderCan() {
        UiDockManager ui=manager(1400,800);
        AtomicLong time=new AtomicLong(100_000_000L);
        ui.clockForTesting(time::get);
        ui.reopen("settings");
        UiRect source=ui.area("settings"),preview=ui.area("preview");
        int bodyX=preview.x()+preview.w()/2;
        int bodyY=preview.y()+preview.h()/2;
        ui.pointerDown(source.x()+25,source.y()+8,0);
        ui.pointerMove(bodyX,bodyY);
        dwell(ui,time,bodyX,bodyY);
        assertNotNull(ui.previewArea());
        assertNotEquals(preview,ui.previewArea(),
                "The center of the panel body must create a split, never merge tabs");
        int tabX=preview.x()+preview.w()-14,tabY=preview.y()+9;
        ui.pointerMove(tabX,tabY);
        assertEquals(preview,ui.previewArea(),
                "Only the empty header drop slot offers tab merging");
        ui.pointerUp(tabX,tabY,0);
        assertEquals(ui.area("settings"),ui.area("preview"));
    }

    @Test void bottomOfOneZoneInsertsOnlyWithinThatColumnAndPreviewIsExact() {
        UiDockManager ui=manager(1400,800);
        AtomicLong time=new AtomicLong(100_000_000L);
        ui.clockForTesting(time::get);
        ui.reopen("settings");
        UiRect source=ui.area("settings"), target=ui.area("preview");
        UiRect left=ui.area("hierarchy"),right=ui.area("inspector");
        // The shared line at the bottom belongs to an edge-wide placement.
        // Stay inside this zone's lower sector to request its local split.
        int x=target.x()+target.w()/2,y=target.y()+target.h()-65;
        ui.pointerDown(source.x()+26,source.y()+9,0);
        ui.pointerMove(x,y);
        dwell(ui,time,x,y);
        UiRect proposed=ui.previewArea();
        assertNotNull(proposed);
        assertEquals(target.x(),proposed.x());
        assertEquals(target.w(),proposed.w());
        assertTrue(proposed.y()>target.y());
        assertEquals(left,ui.projectedArea("hierarchy"));
        assertEquals(right,ui.projectedArea("inspector"));
        assertEquals(target,ui.area("preview"),"No live model mutation before release");
        ui.pointerUp(x,y,0);
        assertEquals(proposed,ui.area("settings"),"Commit must exactly match preview geometry");
        assertFalse(ui.floating("settings"));
    }

    @Test void absoluteBottomInsertsAcrossTheEntireWorkspace() {
        UiDockManager ui=manager(1400,800);
        AtomicLong time=new AtomicLong(100_000_000L);
        ui.clockForTesting(time::get);
        ui.reopen("settings");
        UiRect source=ui.area("settings");
        ui.pointerDown(source.x()+28,source.y()+9,0);
        ui.pointerMove(700,790);
        dwell(ui,time,700,790);
        UiRect proposed=ui.previewArea();
        assertNotNull(proposed);
        assertEquals(5,proposed.x());
        assertEquals(1390,proposed.w());
        assertTrue(proposed.h()>=200,
                "Outer horizontal docking should reserve a substantial starting height");
        assertEquals(795,proposed.y()+proposed.h(),
                "The new bottom dock must remain attached to the workspace bottom");
        assertTrue(ui.projectedArea("hierarchy").h()<ui.area("hierarchy").h());
        ui.pointerUp(700,790,0);
        assertEquals(proposed,ui.area("settings"));
    }

    @Test void dockedTabUndocksImmediatelyAndFollowsCursorBeforeDeliberateDrop() {
        UiDockManager ui=manager(1400,800);
        AtomicLong time=new AtomicLong(100_000_000L);
        ui.clockForTesting(time::get);
        UiRect original=ui.area("hierarchy");
        UiRect target=ui.area("inspector");
        int hx=original.x()+30,hy=original.y()+8;
        ui.pointerDown(hx,hy,0);
        int x=target.x()+target.w()/2,y=target.y()+target.h()-13;
        ui.pointerMove(x,y);
        assertTrue(ui.floating("hierarchy"),"Tab must detach on the first drag move");
        assertNotEquals(original,ui.area("hierarchy"),"Detached tab has floating bounds");
        assertFalse(ui.indicatorsVisible(),"Floating first, docking only after dwell");
        UiRect floatingStart=ui.area("hierarchy");
        ui.pointerMove(x+15,y);
        assertEquals(floatingStart.x()+15,ui.area("hierarchy").x(),
                "Floating window must track mouse before docking");
        ui.pointerMove(x,y);
        dwell(ui,time,x,y);
        UiRect proposed=ui.previewArea();
        assertNotNull(proposed);
        ui.pointerUp(x,y,0);
        assertEquals(proposed,ui.area("hierarchy"));
        assertFalse(ui.floating("hierarchy"));
    }

    @Test void shrunkenFloatingInspectorCanScrollToEveryLastControl() {
        UiDockManager ui=manager(1400,800);
        UiElement longInspector=UiElement.column(7,9).scroll();
        for(int n=0;n<22;n++)
            longInspector.add(new UiControls.Label("Property "+n).height(26));
        ui.find("inspector").content=longInspector;
        ui.resize(1400,800);
        UiRect docked=ui.area("inspector");
        ui.pointerDown(docked.x()+32,docked.y()+8,0);
        ui.pointerMove(460,130);
        assertTrue(ui.floating("inspector"),"Inspector must detach during dragging");
        ui.pointerUp(460,130,0);
        ui.find("inspector").size(230,155);
        ui.resize(1400,800);
        UiRect floated=ui.area("inspector");
        for(int n=0;n<100;n++)
            ui.wheel(floated.x()+50,floated.y()+ui.context().theme.titleHeight+32,-1);
        UiElement last=longInspector.children.get(longInspector.children.size()-1);
        assertEquals(longInspector.contentHeight-
                (longInspector.bounds().h()-2*longInspector.padding),longInspector.scrollY);
        assertEquals(longInspector.bounds().y()+longInspector.bounds().h()-longInspector.padding,
                last.bounds().y()+last.bounds().h(),
                "The final Inspector control must be reachable even in a compact floating panel");
    }

    @Test void zonesCanHoldArbitrarilyManyTabsAndRoundtripV2() throws Exception {
        UiDockManager ui=manager(1400,800);
        AtomicLong time=new AtomicLong(100_000_000L);
        ui.clockForTesting(time::get);
        for(int i=0;i<9;i++)ui.window("extra"+i,"Extra "+i,
                UiElement.column(2,2).add(new UiControls.Label("Item "+i)));
        // Start each extra as floating via the existing open/reopen semantics.
        for(int i=0;i<9;i++) {
            String id="extra"+i;
            ui.find(id).open=false;
            ui.reopen(id);
            UiRect start=ui.area(id), target=ui.area("preview");
            ui.pointerDown(start.x()+27,start.y()+7,0);
            int tabX=target.x()+target.w()-14,tabY=target.y()+9;
            ui.pointerMove(tabX,tabY);
            dwell(ui,time,tabX,tabY);
            ui.pointerUp(tabX,tabY,0);
            assertEquals(ui.area("preview"),ui.area(id));
        }
        UiRect preview=ui.area("preview");
        ui.pointerDown(preview.x()+preview.w()-62,preview.y()+8,0);
        ui.pointerUp(preview.x()+preview.w()-62,preview.y()+8,0);
        Path saved=temp.resolve("rectangles.properties");
        ui.save(saved);
        var properties=new java.util.Properties();
        try(var input=java.nio.file.Files.newInputStream(saved)){properties.load(input);}
        assertEquals("2",properties.getProperty("dock.version"));
        assertTrue(properties.values().stream().anyMatch(v -> v.toString().contains("extra8")));
        assertFalse(properties.values().stream().anyMatch(v -> v.equals("extra8")),
                "Tab-scroll arrow should reveal/select earlier hidden tabs");
        UiDockManager restored=manager(1400,800);
        for(int i=0;i<9;i++)restored.window("extra"+i,"Extra "+i,
                UiElement.column(2,2).add(new UiControls.Label("Item "+i)));
        assertTrue(restored.load(saved));
        for(int i=0;i<9;i++)assertEquals(restored.area("preview"),restored.area("extra"+i));
    }

    @Test void tabStripHasIdenticalLeftAndRightInsetsEvenWhenOverflowing() {
        for(int width:new int[]{180,365,560,1200}) {
            UiRect panel=new UiRect(7,26,width,200);
            UiRect viewport=UiDockManager.tabStripViewport(panel,30);
            assertEquals(4,viewport.x()-panel.x(),"Left inset");
            assertEquals(4,panel.x()+panel.w()-viewport.x()-viewport.w(),
                    "Right inset must not reserve a permanent 26px insertion slot");
        }
    }
    @Test void mouseWheelScrollsOverflowingHeaderWithoutSelectingTabs() throws Exception {
        UiDockManager ui=new UiDockManager(new UiTheme());
        for(int i=0;i<9;i++)
            ui.window("tab"+i,"Long Tab "+i,
                    UiElement.column(2,2).add(new UiControls.Label("Body "+i)));
        ui.resize(560,380);
        ui.singlePanelLayout();
        UiRect header=ui.area("tab0");
        int x=header.x()+25,y=header.y()+9;
        assertTrue(ui.wheel(x,y,-1),"Header wheel should be consumed");
        Path before=temp.resolve("before-wheel-click.properties");
        ui.save(before);
        var state=new java.util.Properties();
        try(var in=java.nio.file.Files.newInputStream(before)){state.load(in);}
        assertEquals("tab0",state.getProperty("zone.0.active"),
                "Scrolling must not change the selected content");
        ui.pointerDown(x,y,0);
        ui.pointerUp(x,y,0);
        Path after=temp.resolve("after-wheel-click.properties");
        ui.save(after);
        try(var in=java.nio.file.Files.newInputStream(after)){state.load(in);}
        assertEquals("tab0",state.getProperty("zone.0.active"),
                "One small wheel step should only partially shift the first tab");
        for(int i=0;i<4;i++)ui.wheel(x,y,-1);
        ui.pointerDown(x,y,0);
        ui.pointerUp(x,y,0);
        Path back=temp.resolve("after-reverse-wheel.properties");
        ui.save(back);
        try(var in=java.nio.file.Files.newInputStream(back)){state.load(in);}
        assertEquals("tab1",state.getProperty("zone.0.active"),
                "Several small wheel steps should move the next tab under the pointer");
        for(int i=0;i<5;i++)ui.wheel(x,y,1);
        ui.pointerDown(x,y,0);
        ui.pointerUp(x,y,0);
        ui.save(back);
        try(var in=java.nio.file.Files.newInputStream(back)){state.load(in);}
        assertEquals("tab0",state.getProperty("zone.0.active"));
    }

    @Test void textSelectionClipboardAndCaretUseRealMeasuredWidths() {
        UiContext ctx = new UiContext(new UiTheme());
        ctx.measure = text -> text.chars().map(ch -> ch == 'W' ? 12 : 4).sum();
        String[] clipboard = {""};
        ctx.writeClipboard = v -> clipboard[0] = v;
        ctx.readClipboard = () -> clipboard[0];
        UiControls.TextField field = new UiControls.TextField("Wide Window", unused -> {});
        field.layout(new UiRect(0, 0, 180, 24), ctx);
        field.onPointerDown(ctx, 11, 10, 0);
        field.onPointerMove(ctx, 75, 10);
        field.onPointerUp(ctx, 75, 10, 0);
        assertTrue(field.hasSelection());
        field.onKey(ctx, 67, 2);
        assertFalse(clipboard[0].isEmpty());
        field.onKey(ctx, 88, 2);
        String afterCut = field.value();
        field.onKey(ctx, 86, 2);
        assertNotEquals(afterCut, field.value());
        field.onKey(ctx, 65, 2);
        assertEquals(0, field.selectionStart());
        assertEquals(field.value().length(), field.selectionEnd());
        field.onKey(ctx, 268, 0);
        assertFalse(field.hasSelection());
        assertEquals(0, field.caret());
        field.onKey(ctx, 269, 0);
        assertEquals(field.value().length(), field.caret());
    }

    @Test void allFourCurveModesMatchRuntimeInterpolationSemantics() {
        UiControls.Curve curve = new UiControls.Curve(() -> {});
        curve.setPoints(java.util.List.of(new UiControls.Point(0, 0),
                new UiControls.Point(.5, 1), new UiControls.Point(1, 0)));
        curve.interpolation(UiControls.Interpolation.LINEAR);
        assertEquals(.5, curve.sample(.25), 1e-10);
        curve.interpolation(UiControls.Interpolation.STEP);
        assertEquals(0, curve.sample(.25), 1e-10);
        curve.interpolation(UiControls.Interpolation.BEZIER);
        assertEquals(.5, curve.sample(.25), 1e-10);
        curve.interpolation(UiControls.Interpolation.CATMULL);
        assertEquals(.5625, curve.sample(.25), 1e-10);
    }

    @Test void rowLayoutNeverPushesFixedFieldsOutsideNarrowPanel() {
        UiElement label = new UiControls.Label("Spawn Rate").width(92);
        UiElement field = new UiControls.NumberField(24, 0, 100, 1, v -> {}).width(67);
        UiElement row = UiElement.row(5, 2).add(label).add(field);
        row.layout(new UiRect(10, 10, 100, 27), new UiContext(new UiTheme()));
        assertTrue(field.bounds().x() + field.bounds().w() <= 110);
        assertTrue(label.bounds().x() + label.bounds().w() <= field.bounds().x());
    }

    @Test void minimumDockWidthsArePanelAwareAndDoNotDistortSavedRatio() {
        UiDockManager ui = manager(950, 535);
        int originalHierarchy = ui.area("hierarchy").w();
        ui.resize(679, 381); // roughly a 140% UI scale workspace
        assertTrue(ui.area("hierarchy").w() >= 125);
        assertTrue(ui.area("inspector").w() >= 180);
        assertTrue(ui.area("preview").w() >= 230);
        ui.resize(950, 535);
        assertEquals(originalHierarchy, ui.area("hierarchy").w(),
                "Shrinking/expanding a viewport must not permanently modify user split ratio");
    }

    @Test void floatingResizeAndSplitResizeRespectBounds() {
        UiDockManager ui = manager(1280, 720);
        ui.reopen("settings");
        UiRect initial = ui.area("settings");
        int x = initial.x() + initial.w() - 4;
        int y = initial.y() + initial.h() - 4;
        ui.pointerDown(x, y, 0);
        ui.pointerMove(x + 60, y + 35);
        ui.pointerUp(x + 60, y + 35, 0);
        UiRect grown = ui.area("settings");
        assertTrue(grown.w() > initial.w(), "Floating resize should grow width");
        assertTrue(grown.h() > initial.h(), "Floating resize should grow height");
        assertEquals(UiDockManager.Cursor.RESIZE_DIAGONAL,
                ui.cursor(grown.x() + grown.w() - 6, grown.y() + grown.h() - 6));

        UiRect preview = ui.area("preview");
        int splitY = preview.y() + preview.h() + 1;
        int splitX = preview.x() + Math.max(30, preview.w() / 3);
        ui.pointerDown(splitX, splitY, 0);
        ui.pointerMove(splitX, splitY - 55);
        ui.pointerUp(splitX, splitY - 55, 0);
        assertNotEquals(preview.h(), ui.area("preview").h());
        UiRect curve = ui.area("curve");
        assertEquals(0, curve.intersect(ui.area("preview")).w()
                * curve.intersect(ui.area("preview")).h());
    }

    @Test void tabSwitchBlursHiddenInputsAndTabFindsVisibleControls() {
        UiDockManager ui = manager(950, 535);
        UiControls.TextField field = new UiControls.TextField("hello", ignored -> {});
        ui.find("inspector").content = UiElement.column(0, 2).add(field.height(24));
        ui.resize(950, 535);
        UiRect inspector = ui.area("inspector");
        int x = inspector.x() + 40, y = inspector.y() + 29;
        ui.pointerDown(x, y, 0);
        ui.pointerUp(x, y, 0);
        assertSame(field, ui.context().focus);
        UiRect hierarchy = ui.area("hierarchy");
        ui.pointerDown(hierarchy.x() + 15, hierarchy.y() + 7, 0);
        ui.pointerUp(hierarchy.x() + 15, hierarchy.y() + 7, 0);
        assertNull(ui.context().focus, "Clicking another panel header should end hidden input focus");
        assertTrue(ui.key(258, 0), "Tab should traverse accessible controls across visible panels");
        assertSame(field, ui.context().focus);
    }

    @Test void reopeningAnAlreadyVisiblePanelRaisesOrActivatesIt() throws Exception {
        UiDockManager ui = manager(950, 535);
        ui.reopen("settings");
        ui.close("console");
        ui.reopen("console");
        ui.reopen("settings"); // must raise, not silently ignore
        Path path = temp.resolve("raised.properties");
        ui.save(path);
        var saved = new java.util.Properties();
        try (var in = java.nio.file.Files.newInputStream(path)) { saved.load(in); }
        int count = Integer.parseInt(saved.getProperty("float.count"));
        assertTrue(saved.getProperty("float." + (count-1)).startsWith("settings,"));
        assertTrue(ui.visible("settings"));
    }

    @Test void cursorRoutingRespectsWindowZOrderAndResizers() {
        UiDockManager ui = manager(1280, 720);
        UiRect side = ui.area("hierarchy");
        assertEquals(UiDockManager.Cursor.RESIZE_HORIZONTAL,
                ui.cursor(side.x() + side.w() + 1, side.y() + 80));
        ui.reopen("settings");
        UiRect floating = ui.area("settings");
        assertEquals(UiDockManager.Cursor.MOVE,
                ui.cursor(floating.x() + 20, floating.y() + 6));
        assertEquals(UiDockManager.Cursor.RESIZE_DIAGONAL,
                ui.cursor(floating.x() + floating.w() - 5, floating.y() + floating.h() - 5));
        assertEquals(UiDockManager.Cursor.ARROW,
                ui.cursor(floating.x() + 40, floating.y() + 90));
    }

    @Test void focusAndWidgetCallbacksWork() {
        UiContext ctx = new UiContext(new UiTheme());
        ArrayList<String> typed = new ArrayList<>();
        UiControls.TextField field = new UiControls.TextField("old", typed::add);
        field.layout(new UiRect(0, 0, 160, 24), ctx);
        field.onPointerDown(ctx, 150, 15, 0);
        assertSame(field, ctx.focus);
        assertTrue(field.onChar(ctx, '!'));
        assertEquals("old!", field.value());
        assertEquals("old!", typed.getLast());

        AtomicInteger clicks = new AtomicInteger();
        UiControls.Button b = new UiControls.Button("Apply", clicks::incrementAndGet);
        b.layout(new UiRect(0, 0, 70, 24), ctx);
        assertTrue(b.onPointerDown(ctx, 20, 12, 0));
        b.onPointerUp(ctx, 20, 12, 0);
        assertEquals(1, clicks.get());
    }
}
