package net.kofllee.pyroui;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import java.io.IOException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * A GPU-local snapshot of the already-rendered Minecraft frame.
 *
 * Pyro UI owns this texture; no framebuffer pixels are ever read into Java.
 * The world is sampled only by its docked viewport, never through transparent
 * holes in the workspace. The captured source is one frame from the native
 * GameRenderer, not a painted/mock preview.
 *
 * The source is center-cropped to the viewport's aspect ratio (no stretching).
 * Capturing the native frame does not change Minecraft's camera FOV; a fully
 * independent world-camera render backend could be added later.
 */
public final class MinecraftWorldFrame implements AutoCloseable {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath("pyro_ui","runtime/world_frame");
    private Texture backing;
    private int width,height;

    public ResourceLocation texture() {return TEXTURE;}
    public int width() {return width;}
    public int height() {return height;}
    public boolean ready() {return backing!=null && width>0 && height>0;}

    public void capture() {
        RenderSystem.assertOnRenderThread();
        Minecraft mc=Minecraft.getInstance();
        if(mc.level==null)return;
        RenderTarget source=mc.getMainRenderTarget();
        int w=source.width,h=source.height;
        if(w<=0||h<=0)return;
        if(backing==null) {
            backing=new Texture();
            mc.getTextureManager().register(TEXTURE,backing);
        }
        if(width!=w || height!=h) {
            width=w;height=h;
            int previous=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            GlStateManager._bindTexture(backing.getId());
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA8,w,h,0,
                    GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,(java.nio.ByteBuffer)null);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S,org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T,org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE);
            GlStateManager._bindTexture(previous);
        }
        int previousTexture=GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int previousRead=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        try {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,source.frameBufferId);
            GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
            GlStateManager._bindTexture(backing.getId());
            GL11.glCopyTexSubImage2D(GL11.GL_TEXTURE_2D,0,0,0,0,0,w,h);
        } finally {
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,previousRead);
            GlStateManager._bindTexture(previousTexture);
        }
    }

    @Override public void close() {
        if(backing!=null) {
            Minecraft.getInstance().getTextureManager().release(TEXTURE);
            backing=null;
        }
        width=0;height=0;
    }

    private static final class Texture extends AbstractTexture {
        Texture() {id=GlStateManager._genTexture();}
        @Override public void load(ResourceManager ignored) throws IOException {
            // Runtime GPU content is refreshed every rendered frame.
        }
        @Override public void close() {releaseId();}
    }
}
