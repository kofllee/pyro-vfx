package net.kofllee.pyroui;

import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Minecraft-native quality lab: one vanilla font and the original Pixelarticons.
 * Labels show measured ink bounds, not misleading 24x24 atlas-cell dimensions.
 * Accessible in the dev client via /pyrovfx typography or runClientUiGallery.
 */
public final class PyroUiTypographyGalleryScreen extends Screen {
    private static final int W=1240,H=740;
    private static final int BG=0xFF101012,SURFACE=0xFF232327;
    private static final int TEXT=0xFFEEEEF0,MUTED=0xFFACABB0,ACCENT=0xFFFF7044;
    private static final List<String> NAMES=List.copyOf(UiIcons.all().keySet());
    private final UiFontService fonts=new UiFontService();
    private int textScale=100, iconSize=20;
    private boolean boundsVisible;
    private float frameScale=1,offsetX,offsetY;

    public PyroUiTypographyGalleryScreen() {
        super(Component.literal("Pyro UI - Vanilla Font & Pixelarticons Lab"));
    }
    public int textScale() {return textScale;}
    public int iconSize() {return iconSize;}
    public boolean boundsVisible() {return boundsVisible;}

    private void hint(MinecraftUiRenderer canvas,String value,int x,int y) {
        canvas.textMeta(value,x,y,MUTED);
    }
    private void button(MinecraftUiRenderer canvas,String value,int x,int y,int width) {
        canvas.rounded(new UiRect(x,y,width,31),2,SURFACE);
        canvas.textHeading(value,x+8,y+6,TEXT);
    }
    private void icon(MinecraftUiRenderer canvas,String name,int x,int y,int size,int color) {
        var sprite=UiIcons.sprite(name);
        if(sprite==null)return;
        if(boundsVisible) {
            canvas.border(new UiRect(x,y,size,size),0xFF494951);
            canvas.border(sprite.destination(x,y,size),0xFFB38446);
        }
        canvas.icon(name,x,y,size,color);
    }

    @Override public void render(GuiGraphics graphics,int mouseX,int mouseY,float tick) {
        frameScale=Math.min(width/(float)W,height/(float)H);
        offsetX=(width-W*frameScale)*.5f;
        offsetY=(height-H*frameScale)*.5f;
        fonts.sizes(Math.max(8,Math.round(13*textScale/100f)),
                Math.max(8,Math.round(15*textScale/100f)),
                Math.max(7,Math.round(11*textScale/100f)));
        graphics.fill(0,0,width,height,BG);
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(offsetX,offsetY,0);
            graphics.pose().scale(frameScale,frameScale,1);
            try(var c=new MinecraftUiRenderer(graphics,frameScale,fonts)) {
                c.fill(0,0,W,H,BG);
                c.fill(0,0,W,3,ACCENT);
                c.textHeading("PYRO UI  /  VANILLA TYPOGRAPHY + ORIGINAL PIXELARTICONS",
                        29,23,TEXT);
                hint(c,"Minecraft FontSet only  ·  22 Pixelarticons SVGs  ·  native 24px grid  ·  measured ink bounds",
                        29,54);
                hint(c,"FONT",866,23);
                button(c,"-",948,20,31);
                button(c,textScale+"%",986,20,69);
                button(c,"+",1063,20,31);
                hint(c,"ICON",1110,23);
                button(c,"-",1151,20,30);
                button(c,"+",1186,20,28);
                c.fill(28,88,1183,1,0xFF35353B);

                c.textHeading("VANILLA MINECRAFT FONT",29,107,TEXT);
                hint(c,"Heading (bold Mojangles)",29,135);
                c.textHeading("Pyrotechnica · Пиротехника  Аа Бб Жж 012345",29,159,TEXT);
                hint(c,"Body (native Minecraft FontSet)",29,190);
                c.text("Exploration, redstone, pressure / Дым, пламя, редстоун",29,214,0xFFD3D2D7);
                hint(c,"Meta: I l 1 | O 0 · № ™ + = { } [ ]",29,242);

                c.fill(28,277,1183,1,0xFF35353B);
                c.textHeading("ORIGINAL PIXELARTICONS  /  ALL 22",29,293,TEXT);
                hint(c,"Dimensions indicate actual visible ink in the 24×24 SVG viewBox",29,317);

                for(int i=0;i<NAMES.size();i++) {
                    String name=NAMES.get(i);
                    var sprite=UiIcons.sprite(name);
                    int x=29+(i/11)*383,y=347+(i%11)*31;
                    c.rounded(new UiRect(x,y,370,28),2,SURFACE);
                    icon(c,name,x+7,y+3,iconSize,ACCENT);
                    c.text(name,x+46,y+6,TEXT);
                    hint(c,sprite.width()+" × "+sprite.height()+" ink",x+215,y+7);
                }

                c.rounded(new UiRect(814,107,395,160),2,SURFACE);
                c.textHeading("SOURCE SIZE ≠ VISIBLE SIZE",829,123,TEXT);
                hint(c,"SVG viewBox: always 24×24",829,151);
                hint(c,"Visible ink: 8×14 ... 24×24 (varies by shape)",829,173);
                hint(c,"Scale by actual ink bounds; preserve aspect ratio",829,195);
                hint(c,"Original art only · GL_NEAREST · no new icon packs",829,217);
                hint(c,"B: SHOW/HIDE INK BOUNDS",829,244);

                c.rounded(new UiRect(814,287,395,399),2,SURFACE);
                c.textHeading("LIVE ICON SCALE COMPARISON",829,301,TEXT);
                hint(c,"Each slot centers measured ink, never stretches it",829,325);
                int[] sizes={12,16,20,24,32};
                String[] samples={"close","check","chevron-right","settings-cog","eye","fire"};
                for(int row=0;row<sizes.length;row++) {
                    int sz=sizes[row],yy=366+row*59;
                    hint(c,sz+" px",829,yy+5);
                    for(int col=0;col<samples.length;col++) {
                        int x=892+col*49;
                        icon(c,samples[col],x,yy,sz,ACCENT);
                    }
                }

                c.fill(28,705,1183,1,0xFF35353B);
                hint(c,"PIXEL ART: original 24px source | tight UV | correct aspect | nearest filter",
                        30,717);
                hint(c,"ESC TO CLOSE",1102,717);
            }
        } finally {
            graphics.pose().popPose();
        }
    }

    @Override public boolean mouseClicked(double mouseX,double mouseY,int button) {
        if(button!=0)return super.mouseClicked(mouseX,mouseY,button);
        double x=(mouseX-offsetX)/frameScale,y=(mouseY-offsetY)/frameScale;
        if(y>=20&&y<54) {
            if(x>=948&&x<980){textScale=Math.max(75,textScale-25);return true;}
            if(x>=1063&&x<1096){textScale=Math.min(175,textScale+25);return true;}
            if(x>=1151&&x<1183){iconSize=Math.max(12,iconSize-2);return true;}
            if(x>=1186&&x<1215){iconSize=Math.min(24,iconSize+2);return true;}
        }
        if(x>=814&&x<1209&&y>=238&&y<267){boundsVisible=!boundsVisible;return true;}
        return super.mouseClicked(mouseX,mouseY,button);
    }
    @Override public boolean keyPressed(int key,int scanCode,int modifiers) {
        if(key==GLFW.GLFW_KEY_B){boundsVisible=!boundsVisible;return true;}
        return super.keyPressed(key,scanCode,modifiers);
    }
    @Override public boolean isPauseScreen(){return false;}
}
