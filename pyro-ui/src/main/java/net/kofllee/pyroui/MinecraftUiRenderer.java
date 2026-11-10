package net.kofllee.pyroui;

import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/**
 * Native Minecraft 1.21.1 UI renderer. Uses GuiGraphics' MultiBufferSource:
 * adjacent compatible quads share a RenderType/buffer, and flush boundaries
 * follow scissor changes rather than individual controls or pixels.
 *
 * The caller applies one logical-UI -> Minecraft-GUI pose scale for the frame.
 * GUI scissor coordinates are converted just once at this boundary.
 */
public final class MinecraftUiRenderer implements UiCanvas, AutoCloseable {
    private final GuiGraphics graphics;
    private final float uiScale;
    private final UiFontService fonts;
    private MinecraftWorldFrame worldFrame;
    private final Deque<UiRect> clips = new ArrayDeque<>();
    private final Deque<Layer> layers = new ArrayDeque<>();
    private float opacity = 1f;
    private int offsetY;
    private int submittedQuads;
    private int flushes;
    private enum Pass { NONE, GEOMETRY, TEXTURE, FONT }
    private Pass pass = Pass.NONE;
    private ResourceLocation activeTexture;
    private record Layer(float opacity, int offsetY) {}

    public MinecraftUiRenderer(GuiGraphics graphics, float uiScale) {
        this(graphics, uiScale, new UiFontService());
    }
    public MinecraftUiRenderer(GuiGraphics graphics, float uiScale, UiFontService fonts) {
        if (graphics == null || fonts == null || !(uiScale > 0))
            throw new IllegalArgumentException("Invalid UI render context");
        this.graphics = graphics;
        this.uiScale = uiScale;
        this.fonts = fonts;
        this.fonts.deviceScale(uiScale *
                (float)net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScale());
    }
    public UiFontService fonts() { return fonts; }
    public MinecraftUiRenderer worldFrame(MinecraftWorldFrame source) {
        worldFrame=source;
        return this;
    }
    public int fontHeight() { return fonts.lineHeight(UiFontService.Role.BODY); }
    public int submittedQuads() { return submittedQuads; }
    public int flushCount() { return flushes; }
    private void flushPending() {
        if(pass!=Pass.NONE) {
            graphics.flush();
            flushes++;
            pass=Pass.NONE;
            activeTexture=null;
        }
    }
    private void geometryPass() {
        if(pass!=Pass.GEOMETRY) {
            flushPending();
            pass=Pass.GEOMETRY;
        }
    }
    private void texturePass(ResourceLocation texture) {
        if(pass!=Pass.TEXTURE || !texture.equals(activeTexture)) {
            flushPending();
            pass=Pass.TEXTURE;
            activeTexture=texture;
        }
    }
    private void fontPass() {
        if(pass!=Pass.FONT) {
            flushPending();
            pass=Pass.FONT;
        }
    }

    private int alpha(int color) {
        int a = Math.max(0, Math.min(255, Math.round(((color >>> 24) & 255) * opacity)));
        return (a << 24) | (color & 0xFFFFFF);
    }
    private static int channel(int color, int shift) { return (color >>> shift) & 255; }
    private static void put(VertexConsumer consumer, Matrix4f transform,
                            float x, float y, int color) {
        consumer.addVertex(transform, x, y, 0)
                .setColor(channel(color,16),channel(color,8),
                          channel(color,0),channel(color,24));
    }
    private void quad(float x0, float y0, float x1, float y1,
                      int topLeft, int bottomLeft, int bottomRight, int topRight) {
        if (opacity <= 0.001f) return;
        geometryPass();
        VertexConsumer b = graphics.bufferSource().getBuffer(RenderType.gui());
        Matrix4f m = graphics.pose().last().pose();
        put(b,m,x0,y0,alpha(topLeft));
        put(b,m,x0,y1,alpha(bottomLeft));
        put(b,m,x1,y1,alpha(bottomRight));
        put(b,m,x1,y0,alpha(topRight));
        submittedQuads++;
    }
    @Override public void fill(int x,int y,int w,int h,int color) {
        if (w<=0 || h<=0 || (color>>>24)==0) return;
        quad(x,y,x+w,y+h,color,color,color,color);
    }
    @Override public void gradient(UiRect r,int top,int bottom) {
        if (r.w()<=0 || r.h()<=0) return;
        quad(r.x(),r.y(),r.x()+r.w(),r.y()+r.h(),
             top,bottom,bottom,top);
    }
    @Override public void gradientHorizontal(UiRect r,int left,int right) {
        if (r.w()<=0 || r.h()<=0) return;
        quad(r.x(),r.y(),r.x()+r.w(),r.y()+r.h(),
             left,left,right,right);
    }
    @Override public void gradientStops(UiRect r,int... stops) {
        if (stops.length==0 || r.h()<=0) return;
        if (stops.length==1) { fill(r,stops[0]); return; }
        for(int i=0;i<stops.length-1;i++) {
            int y0=r.y()+Math.round(r.h()*i/(float)(stops.length-1));
            int y1=r.y()+Math.round(r.h()*(i+1)/(float)(stops.length-1));
            if(y1>y0) quad(r.x(),y0,r.x()+r.w(),y1,
                    stops[i],stops[i+1],stops[i+1],stops[i]);
        }
    }
    @Override public void rounded(UiRect r,int radius,int color) {
        if(r.w()<=0 || r.h()<=0)return;
        int rad=Math.max(0,Math.min(radius,Math.min(r.w(),r.h())/2));
        if(rad==0) {fill(r,color);return;}
        if(rad==1) {
            fill(r.x()+1,r.y(),r.w()-2,1,color);
            fill(r.x(),r.y()+1,r.w(),r.h()-2,color);
            fill(r.x()+1,r.y()+r.h()-1,r.w()-2,1,color);
            return;
        }
        fill(r.x(),r.y()+rad,r.w(),r.h()-rad*2,color);
        // At most 2*radius strips, regardless of panel length/width.
        for(int row=0;row<rad;row++) {
            double d=rad-row-.5;
            int inset=(int)Math.ceil(rad-Math.sqrt(Math.max(0,rad*rad-d*d)));
            fill(r.x()+inset,r.y()+row,r.w()-inset*2,1,color);
            fill(r.x()+inset,r.y()+r.h()-row-1,r.w()-inset*2,1,color);
        }
    }
    @Override public void roundedBorder(UiRect r,int radius,int color) {
        if(r.w()<2 || r.h()<2)return;
        int rad=Math.max(0,Math.min(radius,Math.min(r.w(),r.h())/2));
        if(rad==0) { border(r,color); return; }
        if(rad==1) {
            fill(r.x()+1,r.y(),r.w()-2,1,color);
            fill(r.x()+1,r.y()+r.h()-1,r.w()-2,1,color);
            fill(r.x(),r.y()+1,1,r.h()-2,color);
            fill(r.x()+r.w()-1,r.y()+1,1,r.h()-2,color);
            return;
        }
        line(r.x()+rad,r.y()+.5,r.x()+r.w()-rad,r.y()+.5,color,1);
        line(r.x()+rad,r.y()+r.h()-.5,r.x()+r.w()-rad,r.y()+r.h()-.5,color,1);
        line(r.x()+.5,r.y()+rad,r.x()+.5,r.y()+r.h()-rad,color,1);
        line(r.x()+r.w()-.5,r.y()+rad,r.x()+r.w()-.5,r.y()+r.h()-rad,color,1);
        final int steps=Math.max(3,rad*2);
        for(int corner=0;corner<4;corner++) {
            double cx=r.x()+(corner==0||corner==3 ? rad : r.w()-rad);
            double cy=r.y()+(corner<2 ? rad : r.h()-rad);
            double base=Math.PI/2*corner+Math.PI;
            for(int i=0;i<steps;i++) {
                double a=base+i*Math.PI/(2*steps);
                double b=base+(i+1)*Math.PI/(2*steps);
                line(cx+Math.cos(a)*(rad-.5),cy+Math.sin(a)*(rad-.5),
                     cx+Math.cos(b)*(rad-.5),cy+Math.sin(b)*(rad-.5),color,1);
            }
        }
    }
    @Override public void line(double x0,double y0,double x1,double y1,int color,float thickness) {
        double dx=x1-x0,dy=y1-y0, length=Math.hypot(dx,dy);
        if(length<1e-6) { fill((int)x0,(int)y0,1,1,color);return; }
        double nx=-dy/length * Math.max(.5,thickness)/2;
        double ny=dx/length * Math.max(.5,thickness)/2;
        geometryPass();
        VertexConsumer b=graphics.bufferSource().getBuffer(RenderType.gui());
        Matrix4f m=graphics.pose().last().pose();
        int c=alpha(color);
        put(b,m,(float)(x0+nx),(float)(y0+ny),c);
        put(b,m,(float)(x0-nx),(float)(y0-ny),c);
        put(b,m,(float)(x1-nx),(float)(y1-ny),c);
        put(b,m,(float)(x1+nx),(float)(y1+ny),c);
        submittedQuads++;
    }
    @Override public void image(UiTextureRegion src,UiRect dst,int tint) {
        imageInternal(src,dst,tint,true);
    }
    @Override public void minecraftFrame(UiRect dst) {
        fill(dst,UiTheme.BG);
        if(worldFrame==null || !worldFrame.ready() || dst.w()<1 || dst.h()<1)return;
        // The framebuffer is GL-bottom-left-oriented; flip V explicitly.
        // Crop instead of stretching when a dock zone changes aspect ratio.
        float sourceAspect=worldFrame.width()/(float)worldFrame.height();
        float targetAspect=dst.w()/(float)dst.h();
        float u0=0f,u1=1f,v0=1f,v1=0f;
        if(targetAspect>sourceAspect) {
            float visible=sourceAspect/targetAspect;
            v0=(1f+visible)*.5f;
            v1=(1f-visible)*.5f;
        } else {
            float visible=targetAspect/sourceAspect;
            u0=(1f-visible)*.5f;
            u1=(1f+visible)*.5f;
        }
        texturePass(worldFrame.texture());
        VertexConsumer consumer=graphics.bufferSource().getBuffer(
                UiRenderTypes.smoothTexture(worldFrame.texture()));
        Matrix4f pose=graphics.pose().last().pose();
        int color=alpha(0xFFFFFFFF);
        consumer.addVertex(pose,dst.x(),dst.y(),0)
                .setColor(color).setUv(u0,v0).setUv2(240,240);
        consumer.addVertex(pose,dst.x(),dst.y()+dst.h(),0)
                .setColor(color).setUv(u0,v1).setUv2(240,240);
        consumer.addVertex(pose,dst.x()+dst.w(),dst.y()+dst.h(),0)
                .setColor(color).setUv(u1,v1).setUv2(240,240);
        consumer.addVertex(pose,dst.x()+dst.w(),dst.y(),0)
                .setColor(color).setUv(u1,v0).setUv2(240,240);
        submittedQuads++;
    }
    private void imageInternal(UiTextureRegion src,UiRect dst,int tint,boolean smooth) {
        if(src==null || dst.w()<=0 || dst.h()<=0 || opacity<=.001f) return;
        int color=alpha(tint);
        if((color>>>24)==0)return;
        // 1.21.1 has no RenderType.guiTextured. Use native texture/UV/lightmap
        // with GL_LINEAR for truecolor imagery; legacy pixel art keeps nearest.
        texturePass(src.texture());
        VertexConsumer consumer=graphics.bufferSource().getBuffer(
                smooth ? UiRenderTypes.smoothTexture(src.texture()) : UiRenderTypes.pixelTexture(src.texture()));
        Matrix4f pose=graphics.pose().last().pose();
        consumer.addVertex(pose,dst.x(),dst.y(),0)
                .setColor(color).setUv(src.u0(),src.v0()).setUv2(240, 240);
        consumer.addVertex(pose,dst.x(),dst.y()+dst.h(),0)
                .setColor(color).setUv(src.u0(),src.v1()).setUv2(240, 240);
        consumer.addVertex(pose,dst.x()+dst.w(),dst.y()+dst.h(),0)
                .setColor(color).setUv(src.u1(),src.v1()).setUv2(240, 240);
        consumer.addVertex(pose,dst.x()+dst.w(),dst.y(),0)
                .setColor(color).setUv(src.u1(),src.v0()).setUv2(240, 240);
        submittedQuads++;
    }
    @Override public void icon(String id,int x,int y,int size,int color) {
        if(size<=0)return;
        UiIcons.Sprite sprite=UiIcons.sprite(id);
        if(sprite!=null)
            imageInternal(sprite.ink(),sprite.destination(x,y,size),color,false);
    }
    @Override public void text(String value,int x,int y,int color) {
        fontPass();
        fonts.draw(graphics,value,x,y,alpha(color),UiFontService.Role.BODY);
    }
    @Override public void textHeading(String value,int x,int y,int color) {
        fontPass();
        fonts.draw(graphics,value,x,y,alpha(color),UiFontService.Role.HEADING);
    }
    @Override public void textMeta(String value,int x,int y,int color) {
        fontPass();
        fonts.draw(graphics,value,x,y,alpha(color),UiFontService.Role.META);
    }
    @Override public int measure(String text) {return fonts.measure(text,UiFontService.Role.BODY);}
    @Override public int measureHeading(String text) {return fonts.measure(text,UiFontService.Role.HEADING);}
    @Override public int measureMeta(String text) {return fonts.measure(text,UiFontService.Role.META);}
    @Override public void pushLayer(float amount,int translateY) {
        layers.push(new Layer(opacity,offsetY));
        opacity*=Math.max(0,Math.min(1,amount));
        offsetY+=translateY;
        graphics.pose().pushPose();
        graphics.pose().translate(0,translateY,0);
    }
    @Override public void popLayer() {
        if(layers.isEmpty())throw new IllegalStateException("Layer stack underflow");
        Layer previous=layers.pop();
        opacity=previous.opacity();
        offsetY=previous.offsetY();
        graphics.pose().popPose();
    }
    @Override public void clip(UiRect area) {
        UiRect shifted=new UiRect(area.x(),area.y()+offsetY,area.w(),area.h());
        UiRect effective=clips.isEmpty()?shifted:clips.peek().intersect(shifted);
        clips.push(effective);
        flushPending();
        graphics.enableScissor(Math.round(effective.x()*uiScale),
                Math.round(effective.y()*uiScale),
                Math.round((effective.x()+effective.w())*uiScale),
                Math.round((effective.y()+effective.h())*uiScale));
    }
    @Override public void unclip() {
        if(clips.isEmpty())throw new IllegalStateException("Scissor stack underflow");
        flushPending();
        clips.pop();
        graphics.disableScissor();
    }
    public void finish() {
        while(!clips.isEmpty())unclip();
        while(!layers.isEmpty())popLayer();
        flushPending();
    }
    @Override public void close() {finish();}
}
