package net.kofllee.pyroui;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/**
 * Original Pixelarticons (MIT). Native 24x24 SVG viewboxes have different
 * occupied rectangles (e.g. close=14x14, check=16x12, chevron=8x14).
 *
 * Size in icon() is the maximum *visible* glyph extent, not the texture cell.
 * Each glyph uses measured UV bounds, uniform aspect-ratio-preserving scaling,
 * and centered placement; bitmap edges use GL_NEAREST sampling.
 */
public final class UiIcons {
    public record Sprite(UiTextureRegion ink, int width, int height) {
        public Sprite {
            if(ink == null || width < 1 || height < 1) throw new IllegalArgumentException("Invalid icon");
            if(ink.width()!=width || ink.height()!=height)
                throw new IllegalArgumentException("Source rect must match native ink dimensions");
        }
        /** Same visual extent for both horizontal and vertical shapes, without stretching. */
        public UiRect destination(int x,int y,int slot) {
            if(slot<=0)throw new IllegalArgumentException("Icon slot must be positive");
            // One logical pixel of breathing room on each side when possible.
            int max=Math.max(1,slot-2);
            int longest=Math.max(width,height);
            int w=Math.max(1,Math.round(max*width/(float)longest));
            int h=Math.max(1,Math.round(max*height/(float)longest));
            return new UiRect(x+(slot-w)/2,y+(slot-h)/2,w,h);
        }
    }

    public static final ResourceLocation TEXTURE=ResourceLocation.fromNamespaceAndPath(
            "pyro_ui","textures/gui/icons/pixelarticons.png");
    private static final Map<String,Sprite> SPRITES=buildSprites();
    private static final Map<String,UiTextureRegion> REGIONS=buildRegions();

    private UiIcons() {}
    private static Map<String,Sprite> buildSprites() {
        var map=new LinkedHashMap<String,Sprite>();
        int fullW=UiIconBounds.CELL*UiIconBounds.NAMES.length;
        for(int i=0;i<UiIconBounds.NAMES.length;i++) {
            int[] b=UiIconBounds.INK[i];
            int x=b[0],y=b[1],w=b[2],h=b[3];
            if(x<0||y<0||w<=0||h<=0||x+w>UiIconBounds.VIEWBOX||y+h>UiIconBounds.VIEWBOX)
                throw new IllegalStateException("Invalid measured bounds of "+UiIconBounds.NAMES[i]);
            var region=new UiTextureRegion(TEXTURE,fullW,UiIconBounds.CELL,
                    i*UiIconBounds.CELL+UiIconBounds.GUTTER+x,UiIconBounds.GUTTER+y,w,h);
            map.put(UiIconBounds.NAMES[i],new Sprite(region,w,h));
        }
        return java.util.Collections.unmodifiableMap(map);
    }
    private static Map<String,UiTextureRegion> buildRegions() {
        var map=new LinkedHashMap<String,UiTextureRegion>();
        SPRITES.forEach((name,sprite)->map.put(name,sprite.ink()));
        return java.util.Collections.unmodifiableMap(map);
    }
    public static Sprite sprite(String id) {return SPRITES.get(id);}
    public static UiTextureRegion region(String id) {return REGIONS.get(id);}
    public static Map<String,UiTextureRegion> all() {return REGIONS;}
}
