package net.kofllee.pyroui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Geometry invariants of the planar (non-binary-split) docking workspace. */
class UiDockLayoutTest {
    private static UiDockLayout threeColumns() {
        var layout=new UiDockLayout();
        layout.put("a",new UiDockLayout.Box(0,0,.30,1));
        layout.put("b",new UiDockLayout.Box(.30,0,.67,1));
        layout.put("c",new UiDockLayout.Box(.67,0,1,1));
        assertTrue(layout.coversWorkspace());
        return layout;
    }
    @Test void localBottomSplitAffectsOnlyTheSelectedColumn() {
        var layout=threeColumns();
        var a=layout.get("a").copy();
        var c=layout.get("c").copy();
        assertTrue(layout.insert("new","b",UiDockLayout.Side.BOTTOM,.32));
        assertTrue(layout.coversWorkspace());
        assertEquals(a.x0,layout.get("a").x0);
        assertEquals(a.y1,layout.get("a").y1);
        assertEquals(c.x1,layout.get("c").x1);
        assertEquals(c.y0,layout.get("c").y0);
        assertEquals(layout.get("b").x0,layout.get("new").x0);
        assertEquals(layout.get("b").x1,layout.get("new").x1);
        assertEquals(layout.get("b").y1,layout.get("new").y0);
        assertTrue(layout.get("new").height()<1);
    }
    @Test void outerBottomCreatesOneFullWidthStripAndPushesEveryOtherZoneUp() {
        var layout=threeColumns();
        assertTrue(layout.insert("bottom","a",UiDockLayout.Side.OUTER_BOTTOM,.23));
        assertTrue(layout.coversWorkspace());
        assertEquals(0,layout.get("bottom").x0);
        assertEquals(1,layout.get("bottom").x1);
        assertEquals(1,layout.get("bottom").y1);
        for(String id:new String[]{"a","b","c"})
            assertEquals(layout.get("bottom").y0,layout.get(id).y1,1e-8);
    }
    @Test void independentColumnBoundaryDoesNotResizeUnrelatedColumn() {
        var layout=threeColumns();
        UiRect workspace=new UiRect(5,31,1200,740);
        var edge=layout.edgeAt(5+360,31+300,workspace,5);
        assertNotNull(edge);
        assertTrue(edge.vertical());
        assertEquals(java.util.List.of("a"),edge.before());
        assertEquals(java.util.List.of("b"),edge.after());
        UiDockLayout.Box c=layout.get("c").copy();
        assertEquals(.07,layout.resize(edge,.07,.10,.10),1e-8);
        assertTrue(layout.coversWorkspace());
        assertEquals(c.x0,layout.get("c").x0);
        assertEquals(c.x1,layout.get("c").x1);
        assertEquals(.37,layout.get("a").x1,1e-8);
        assertEquals(.37,layout.get("b").x0,1e-8);
    }
    @Test void sharedEdgeMayResizeMultipleAlignedNeighbors() {
        var layout=new UiDockLayout();
        layout.put("tall",new UiDockLayout.Box(0,0,.4,1));
        layout.put("upper",new UiDockLayout.Box(.4,0,1,.5));
        layout.put("lower",new UiDockLayout.Box(.4,.5,1,1));
        UiRect area=new UiRect(0,0,1000,800);
        var edge=layout.edgeAt(400,200,area,5);
        assertNotNull(edge);
        assertTrue(edge.before().contains("tall"));
        assertEquals(2,edge.after().size());
        layout.resize(edge,.1,.12,.12);
        assertTrue(layout.coversWorkspace());
        assertEquals(.5,layout.get("tall").x1,1e-8);
        assertEquals(.5,layout.get("upper").x0,1e-8);
        assertEquals(.5,layout.get("lower").x0,1e-8);
    }
    @Test void symmetricDockingOnlyAppliesToMatchingOneToOneSeams() {
        var layout=new UiDockLayout();
        layout.put("left",new UiDockLayout.Box(0,0,.4,1));
        layout.put("upper",new UiDockLayout.Box(.4,0,1,.5));
        layout.put("lower",new UiDockLayout.Box(.4,.5,1,1));
        UiRect area=new UiRect(0,0,1000,800);
        var junction=layout.edgeAt(400,160,area,5);
        assertNotNull(junction);
        assertFalse(layout.symmetricDropEdge(junction),
                "One tall window next to two short ones is a T-junction, not a matched pair");
        var full=threeColumns();
        assertTrue(full.symmetricDropEdge(full.edgeAt(300,160,area,5)),
                "Different widths are fine when both windows share the full same-length seam");
    }
    @Test void fullSharedSideHasOneDockTargetFromBothPanelsAndTJunctionKeepsLocalSides() {
        var columns=threeColumns();
        var rightOfA=columns.matchingEdgeForZoneSide("a",UiDockLayout.Side.RIGHT);
        var leftOfB=columns.matchingEdgeForZoneSide("b",UiDockLayout.Side.LEFT);
        assertNotNull(rightOfA);
        assertEquals(rightOfA,leftOfB,"Both local side sectors resolve to one shared edge");
        assertEquals(java.util.List.of("a"),rightOfA.before());
        assertEquals(java.util.List.of("b"),rightOfA.after());

        var stacked=new UiDockLayout();
        stacked.put("top",new UiDockLayout.Box(0,0,1,.45));
        stacked.put("bottom",new UiDockLayout.Box(0,.45,1,1));
        assertEquals(stacked.matchingEdgeForZoneSide("top",UiDockLayout.Side.BOTTOM),
                stacked.matchingEdgeForZoneSide("bottom",UiDockLayout.Side.TOP));

        var junction=new UiDockLayout();
        junction.put("tall",new UiDockLayout.Box(0,0,.4,1));
        junction.put("upper",new UiDockLayout.Box(.4,0,1,.5));
        junction.put("lower",new UiDockLayout.Box(.4,.5,1,1));
        assertNull(junction.matchingEdgeForZoneSide("tall",UiDockLayout.Side.RIGHT));
        assertNull(junction.matchingEdgeForZoneSide("upper",UiDockLayout.Side.LEFT));
    }
    @Test void insertingAtThreeWaySeamRespectsEveryAdjacentZone() {
        var layout=new UiDockLayout();
        layout.put("left",new UiDockLayout.Box(0,0,.40,1));
        layout.put("upper",new UiDockLayout.Box(.40,0,1,.5));
        layout.put("lower",new UiDockLayout.Box(.40,.5,1,1));
        var edge=layout.edgeAt(400,160,new UiRect(0,0,1000,800),27);
        assertNotNull(edge);
        assertEquals(1,edge.before().size());
        assertEquals(2,edge.after().size());
        assertTrue(layout.insertOnEdge("between",edge,false,.16,.20,.27));
        assertTrue(layout.coversWorkspace());
        UiDockLayout.Box region=layout.get("between");
        assertEquals(0,region.y0,1e-8);
        assertEquals(1,region.y1,1e-8);
        assertEquals(.32,region.x0,1e-8);
        assertEquals(.48,region.x1,1e-8);
        assertEquals(.48,layout.get("upper").x0,1e-8);
        assertEquals(.48,layout.get("lower").x0,1e-8);
        assertEquals(.32,layout.get("left").x1,1e-8);
    }
    @Test void insertingAlongSharedEdgeBorrowsSpaceFromOppositeSideWhenNeeded() {
        var layout=threeColumns();
        var edge=layout.edgeAt(300,300,new UiRect(0,0,1000,800),27);
        assertNotNull(edge);
        assertTrue(layout.insertOnEdge("new",edge,true,.17,.09,.20));
        assertTrue(layout.coversWorkspace());
        assertEquals(.215,layout.get("a").x1,1e-8);
        assertEquals(.385,layout.get("b").x0,1e-8);
        assertEquals(.215,layout.get("new").x0,1e-8);
        assertEquals(.385,layout.get("new").x1,1e-8);
        assertEquals(.67,layout.get("c").x0,1e-8);
    }
    @Test void edgeInsertionDoesNotCombineOnlyPointTouchingSegments() {
        var layout=new UiDockLayout();
        layout.put("a",new UiDockLayout.Box(0,0,.4,.5));
        layout.put("b",new UiDockLayout.Box(.4,0,1,.5));
        layout.put("c",new UiDockLayout.Box(0,.5,.4,1));
        layout.put("d",new UiDockLayout.Box(.4,.5,1,1));
        var edge=layout.edgeAt(400,160,new UiRect(0,0,1000,800),22);
        assertNotNull(edge);
        assertEquals(java.util.List.of("a"),edge.before());
        assertEquals(java.util.List.of("b"),edge.after());
        assertTrue(layout.insertOnEdge("topSeam",edge,true,.12,.25,.25));
        assertTrue(layout.coversWorkspace());
        assertEquals(.4,layout.get("c").x1,1e-8);
        assertEquals(.4,layout.get("d").x0,1e-8);
        assertEquals(.5,layout.get("topSeam").y1,1e-8);
    }
    @Test void touchingButSeparateEdgesDoNotCreateOneGlobalSplitter() {
        var layout=new UiDockLayout();
        layout.put("a",new UiDockLayout.Box(0,0,.4,.5));
        layout.put("b",new UiDockLayout.Box(.4,0,1,.5));
        layout.put("c",new UiDockLayout.Box(0,.5,.4,1));
        layout.put("d",new UiDockLayout.Box(.4,.5,1,1));
        var edge=layout.edgeAt(400,190,new UiRect(0,0,1000,800),5);
        assertNotNull(edge);
        assertEquals(java.util.List.of("a"),edge.before());
        assertEquals(java.util.List.of("b"),edge.after());
        layout.resize(edge,.06,.10,.10);
        assertTrue(layout.coversWorkspace());
        assertEquals(.4,layout.get("c").x1,1e-8);
        assertEquals(.4,layout.get("d").x0,1e-8);
    }
    @Test void noRectangularOverlapOrHolesAfterRepeatedDocking() {
        var layout=threeColumns();
        assertTrue(layout.insert("d","b",UiDockLayout.Side.BOTTOM,.28));
        assertTrue(layout.insert("e","d",UiDockLayout.Side.RIGHT,.42));
        assertTrue(layout.insert("f","e",UiDockLayout.Side.OUTER_LEFT,.18));
        assertTrue(layout.insert("g","f",UiDockLayout.Side.OUTER_BOTTOM,.19));
        assertTrue(layout.coversWorkspace());
        assertTrue(layout.remove("g"));
        assertTrue(layout.coversWorkspace());
    }
    @Test void pixelProjectionUsesGapsOnlyAlongSharedInternalEdges() {
        var layout=threeColumns();
        var workspace=new UiRect(5,31,1200,700);
        var a=layout.rect("a",workspace);
        var b=layout.rect("b",workspace);
        assertEquals(6,b.x()-(a.x()+a.w()));
        assertEquals(workspace.x(),a.x());
        assertEquals(workspace.y(),a.y());
        assertEquals(workspace.y()+workspace.h(),a.y()+a.h());
    }
}
