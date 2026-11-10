package net.kofllee.pyroui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UiScrollBoundsTest {
    private static UiElement populated(int count) {
        UiElement root=UiElement.column(7,10).scroll();
        for(int n=0;n<count;n++)
            root.add(new UiControls.Label("Inspector property "+n).height(33));
        return root;
    }

    @Test void scrollExtentMustNotShrinkWhenTheUserScrolls() {
        UiElement root=populated(12);
        UiContext ctx=new UiContext(new UiTheme());
        UiRect bounds=new UiRect(20,30,200,105);
        root.layout(bounds,ctx);
        int total=root.contentHeight;
        assertEquals(12*33+11*7,total);
        int maximum=total-(bounds.h()-20);

        for(int n=0;n<45;n++)assertTrue(root.wheel(ctx,65,70,-1));
        assertEquals(total,root.contentHeight,"Content extent cannot depend on scrollY");
        assertEquals(maximum,root.scrollY,"At the bottom every row is reachable");
        UiElement last=root.children.get(root.children.size()-1);
        assertEquals(bounds.y()+bounds.h()-10,
                last.bounds().y()+last.bounds().h(),
                "The final control must be fully visible above bottom padding");
        for(int n=0;n<8;n++)root.wheel(ctx,65,70,-1);
        assertEquals(maximum,root.scrollY,"Repeated wheel ticks cannot scroll past the bottom");
    }

    @Test void resizingThePanelRecomputesScrollRangeAndKeepsLastItemVisible() {
        UiElement root=populated(17);
        UiContext ctx=new UiContext(new UiTheme());
        UiRect large=new UiRect(5,40,260,170);
        root.layout(large,ctx);
        for(int n=0;n<60;n++)root.wheel(ctx,30,100,-1);
        assertEquals(root.contentHeight-(large.h()-20),root.scrollY);

        UiRect small=new UiRect(5,40,160,69);
        root.layout(small,ctx);
        for(int n=0;n<30;n++)root.wheel(ctx,30,60,-1);
        assertEquals(root.contentHeight-(small.h()-20),root.scrollY);
        UiElement last=root.children.get(root.children.size()-1);
        assertEquals(small.y()+small.h()-10,last.bounds().y()+last.bounds().h());

        root.layout(new UiRect(5,40,160,1200),ctx);
        assertEquals(0,root.scrollY,"Fully visible content should reset scroll");
        assertFalse(root.wheel(ctx,30,60,-1));
    }

    @Test void innerScrollerConsumesScrollAndOuterListKeepsItsOffset() {
        UiElement inner=populated(9);
        UiElement outer=UiElement.column(5,8).scroll();
        outer.add(new UiControls.Label("Header").height(24));
        outer.add(inner.height(85));
        outer.add(new UiControls.Label("Footer").height(28));
        UiContext ctx=new UiContext(new UiTheme());
        outer.layout(new UiRect(0,0,200,150),ctx);
        int innerX=inner.bounds().x()+20;
        int innerY=inner.bounds().y()+20;
        assertTrue(outer.wheel(ctx,innerX,innerY,-1));
        assertTrue(inner.scrollY>0);
        assertEquals(0,outer.scrollY);
    }
}
