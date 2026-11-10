package net.kofllee.pyroui;

import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UiTexturesTest {
    private static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath("pyro_ui","textures/gui/icons/pixelarticons.png");

    @Test void originalIconAtlasHasTightMeasuredUvRegions() {
        assertEquals(22,UiIcons.all().size());
        assertEquals(ID,UiIcons.TEXTURE);
        assertEquals(14,UiIcons.sprite("close").width());
        assertEquals(14,UiIcons.sprite("close").height());
        assertEquals(16,UiIcons.sprite("check").width());
        assertEquals(12,UiIcons.sprite("check").height());
        assertEquals(8,UiIcons.sprite("chevron-right").width());
        assertEquals(14,UiIcons.sprite("chevron-right").height());
        assertEquals(24,UiIcons.sprite("settings-cog").width());
        assertEquals(24,UiIcons.sprite("settings-cog").height());
        assertNull(UiIcons.region("unknown"));
        for(var sprite:UiIcons.all().values()) {
            assertEquals(ID,sprite.texture());
            assertEquals(UiIconBounds.CELL*22,sprite.textureWidth());
            assertEquals(UiIconBounds.CELL,sprite.textureHeight());
            assertTrue(sprite.u()>=0 && sprite.v()>=0);
        }
    }

    @Test void generatedAtlasMatchesActualVisiblePixelsForEveryIcon() throws Exception {
        try(var resource=UiIcons.class.getResourceAsStream(
                "/assets/pyro_ui/textures/gui/icons/pixelarticons.png")) {
            assertNotNull(resource,"Original icon atlas must be packed into Pyro UI JAR");
            BufferedImage atlas=ImageIO.read(resource);
            assertEquals(572,atlas.getWidth());
            assertEquals(26,atlas.getHeight());
            for(int i=0;i<UiIconBounds.NAMES.length;i++) {
                String name=UiIconBounds.NAMES[i];
                int[] box=UiIconBounds.INK[i];
                int x0=24,y0=24,x1=-1,y1=-1;
                for(int y=0;y<24;y++) for(int x=0;x<24;x++) {
                    int argb=atlas.getRGB(i*26+1+x,1+y);
                    if((argb>>>24)!=0) {
                        assertEquals(0xFFFFFF,argb&0xFFFFFF,
                                name+" must be a white alpha mask for tinting");
                        x0=Math.min(x0,x);y0=Math.min(y0,y);
                        x1=Math.max(x1,x);y1=Math.max(y1,y);
                    }
                }
                assertEquals(box[0],x0,name+" x");
                assertEquals(box[1],y0,name+" y");
                assertEquals(box[2],x1-x0+1,name+" width");
                assertEquals(box[3],y1-y0+1,name+" height");
                assertEquals(i*26+1+x0,UiIcons.region(name).u(),name+" u");
                assertEquals(1+y0,UiIcons.region(name).v(),name+" v");
                assertEquals(box[2],UiIcons.region(name).width(),name+" sprite width");
                assertEquals(box[3],UiIcons.region(name).height(),name+" sprite height");
            }
        }
    }

    @Test void destinationFitsTheSlotAndPreservesEveryIconAspect() {
        for(var sprite:UiIcons.all().entrySet()) {
            UiIcons.Sprite s=UiIcons.sprite(sprite.getKey());
            for(int size:new int[]{12,16,20,24,32}) {
                UiRect r=s.destination(100,200,size);
                assertTrue(r.x()>=100 && r.y()>=200);
                assertTrue(r.x()+r.w()<=100+size && r.y()+r.h()<=200+size);
                // Allow a one-device-pixel rounding error without erroneously
                // penalizing skinny sprites such as the 16x2 minus sign.
                assertTrue(Math.abs(r.w()*s.height()-r.h()*s.width())<=
                                Math.max(s.width(),s.height()),
                        "Unexpected aspect distortion of "+sprite.getKey()+" at "+size+"px");
            }
        }
        var check=UiIcons.sprite("check").destination(0,0,20);
        assertTrue(check.w()>check.h(),"Check must stay wider than tall");
        var chevron=UiIcons.sprite("chevron-right").destination(0,0,20);
        assertTrue(chevron.h()>chevron.w(),"Right chevron must stay taller than wide");
    }

    @Test void sourceRegionsCannotLeaveAtlasOrOverflow() {
        assertThrows(IllegalArgumentException.class,()->new UiTextureRegion(ID,64,64,50,0,20,10));
        assertThrows(IllegalArgumentException.class,()->new UiTextureRegion(ID,64,64,0,-1,10,10));
        var a=UiTextureRegion.full(ID,64,64);
        var b=a.subRegion(12,8,20,15).subRegion(2,3,5,4);
        assertEquals(14,b.u());
        assertEquals(11,b.v());
        assertEquals(5,b.width());
        assertThrows(IllegalArgumentException.class,()->a.subRegion(60,60,8,8));
    }

    @Test void nineSliceProducesExactlyNineOrderedSourceAndDestinationRegions() {
        record Blit(UiTextureRegion src,UiRect dst,int tint){}
        class Recorder implements UiCanvas {
            final List<Blit> blits=new ArrayList<>();
            @Override public void fill(int x,int y,int w,int h,int c){}
            @Override public void text(String s,int x,int y,int color){}
            @Override public int measure(String s){return s.length();}
            @Override public void clip(UiRect r){}
            @Override public void unclip(){}
            @Override public void image(UiTextureRegion src,UiRect dst,int tint){
                blits.add(new Blit(src,dst,tint));
            }
        }
        Recorder canvas=new Recorder();
        UiTextureRegion texture=UiTextureRegion.full(ID,16,16);
        canvas.nineSlice(texture,new UiRect(20,30,60,40),4,4,4,4,0xFF4488FF);
        assertEquals(9,canvas.blits.size());
        assertEquals(new UiRect(20,30,4,4),canvas.blits.get(0).dst());
        assertEquals(new UiRect(24,30,52,4),canvas.blits.get(1).dst());
        assertEquals(new UiRect(76,66,4,4),canvas.blits.get(8).dst());
        assertEquals(4,canvas.blits.get(0).src().width());
        assertEquals(8,canvas.blits.get(4).src().height());
        assertEquals(0xFF4488FF,canvas.blits.get(8).tint());
        int total=canvas.blits.stream().mapToInt(b->b.dst().w()*b.dst().h()).sum();
        assertEquals(60*40,total);
    }
}
