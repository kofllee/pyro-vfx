package net.kofllee.pyroui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.joml.Matrix4f;

/**
 * Vanilla Minecraft font only. Render and measure use exactly the same
 * Minecraft FontSet and role scale, without custom raster/SDF providers.
 */
public final class UiFontService {
    public enum Role { BODY, HEADING, META }
    private int bodySize=13,headingSize=13,metaSize=11;
    private float deviceScale=1f;

    /**
     * Current logical-UI -> framebuffer scale. GuiGraphics itself is in GUI
     * units, so the actual physical scale also includes Minecraft GUI scale.
     * The renderer sets this before layout/measurement on every frame.
     */
    public void deviceScale(float value) {
        if(!(value>0f) || !Float.isFinite(value))
            throw new IllegalArgumentException("Invalid physical UI scale");
        deviceScale=value;
    }

    public void sizes(int body,int heading,int meta) {
        if(body<=0||heading<=0||meta<=0)throw new IllegalArgumentException("Font size");
        bodySize=body;headingSize=heading;metaSize=meta;
    }
    private int size(Role role) {
        return switch(role) {
            case BODY->bodySize;
            case HEADING->headingSize;
            case META->metaSize;
        };
    }
    private Font font() {return Minecraft.getInstance().font;}
    private Component component(String value,Role role) {
        var c=Component.literal(value==null?"":value);
        return role==Role.HEADING?c.copy().withStyle(s->s.withBold(true)):c;
    }
    private float scale(Role role) {
        float requested=size(role)/(float)font().lineHeight;
        // Vanilla bold emits two bitmap glyph passes, with the second one
        // slightly horizontally offset. When one *source bitmap pixel* spans
        // a fractional number of framebuffer pixels, those passes sample
        // different pixel phases and create detached strokes on wide glyphs.
        // Pixel-lock headings only; body/meta retain their original sizes.
        return role==Role.HEADING
                ? quantizedHeadingScale(requested,deviceScale) : requested;
    }
    static float quantizedHeadingScale(float requested,float deviceScale) {
        int pixels=Math.max(1,Math.round(requested*deviceScale));
        return pixels/deviceScale;
    }
    /**
     * Align a vanilla bitmap heading's origin to whole framebuffer pixels.
     * The origin adjustment is at most half one physical pixel; glyph scale
     * and advances are quantized together by scale(Role.HEADING) so clipping
     * and measurement agree with the final drawn glyph positions.
     *
     * The Minecraft bold style draws an additional displaced copy of each
     * glyph; a fractional starting phase makes the two bitmap passes appear
     * uneven, particularly on straight stems such as h/H at large GUI sizes.
     */
    static float snapDevicePixel(float value, float guiScale) {
        return Math.round(value * guiScale) / guiScale;
    }
    private static float[] snappedOrigin(Matrix4f pose,float x,float y,float guiScale) {
        // Only the axis-aligned case supports a translation-only pixel snap.
        if(Math.abs(pose.m01())>0.0001f || Math.abs(pose.m10())>0.0001f
                || Math.abs(pose.m00())<0.0001f || Math.abs(pose.m11())<0.0001f)
            return new float[]{x,y};
        float screenX=pose.m00()*x+pose.m30();
        float screenY=pose.m11()*y+pose.m31();
        float dx=(snapDevicePixel(screenX,guiScale)-screenX)/pose.m00();
        float dy=(snapDevicePixel(screenY,guiScale)-screenY)/pose.m11();
        return new float[]{x+dx,y+dy};
    }
    public int measure(String value,Role role) {
        return Math.round(font().width(component(value,role))*scale(role));
    }
    public int lineHeight(Role role) {return size(role);}
    public void draw(GuiGraphics graphics,String value,int x,int y,int argb,Role role) {
        if(value==null||value.isEmpty()||(argb>>>24)==0)return;
        float scale=scale(role);
        float drawX=x,drawY=y;
        if(role==Role.HEADING) {
            float guiScale=(float)Minecraft.getInstance().getWindow().getGuiScale();
            float[] aligned=snappedOrigin(graphics.pose().last().pose(),x,y,guiScale);
            drawX=aligned[0];
            drawY=aligned[1];
        }
        graphics.pose().pushPose();
        try {
            graphics.pose().translate(drawX,drawY,0);
            graphics.pose().scale(scale,scale,1);
            graphics.drawString(font(),component(value,role),0,0,argb,false);
        } finally {
            graphics.pose().popPose();
        }
    }
}
