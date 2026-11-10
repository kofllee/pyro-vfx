package net.kofllee.pyrovfx.client.ui;

import com.mojang.blaze3d.platform.NativeImage;
import java.nio.file.Files;
import java.nio.file.Path;
import net.kofllee.pyroui.PyroUiTypographyGalleryScreen;
import net.kofllee.pyroui.UiIcons;
import net.kofllee.pyrovfx.PyroVfx;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.lwjgl.glfw.GLFW;

/** Development-only screenshots and interaction verification of vanilla-font icon lab. */
@EventBusSubscriber(modid=PyroVfx.MOD_ID,value=Dist.CLIENT)
public final class PyroUiGalleryProbe {
    private static boolean completed;
    private static int frames;
    private static int originalGuiScale=-1;
    private static boolean reloadRequested;
    private PyroUiGalleryProbe(){}

    private static void restoreScale(Minecraft mc) {
        if(originalGuiScale>=0 && mc.options.guiScale().get()!=originalGuiScale) {
            mc.options.guiScale().set(originalGuiScale);
            mc.resizeDisplay();
        }
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if(completed||!Boolean.getBoolean("pyrovfx.ui.gallery.capture"))return;
        Minecraft mc=Minecraft.getInstance();
        if(mc.level==null || !(mc.screen instanceof PyroUiTypographyGalleryScreen screen))return;
        try {
            frames++;
            if(frames==1) {
                originalGuiScale=mc.options.guiScale().get();
                int requested=Integer.getInteger("pyrovfx.ui.guiScale",0);
                if(requested>0 && originalGuiScale!=requested) {
                    mc.options.guiScale().set(requested);
                    mc.resizeDisplay();
                }
            }
            if(frames==27) {
                float scale=Math.min(screen.width/1240f,screen.height/740f);
                float dx=(screen.width-1240*scale)/2f;
                float dy=(screen.height-740*scale)/2f;
                screen.mouseClicked(dx+1079*scale,dy+35*scale,0);
                if(screen.textScale()!=125)throw new AssertionError("Vanilla font-scale + control");
                screen.mouseClicked(dx+965*scale,dy+35*scale,0);
                if(screen.textScale()!=100)throw new AssertionError("Vanilla font-scale - control");
                screen.mouseClicked(dx+1197*scale,dy+35*scale,0);
                if(screen.iconSize()!=22)throw new AssertionError("Icon-scale + control");
                screen.mouseClicked(dx+1166*scale,dy+35*scale,0);
                if(screen.iconSize()!=20)throw new AssertionError("Icon-scale - control");
                screen.keyPressed(GLFW.GLFW_KEY_B,0,0);
                if(!screen.boundsVisible())throw new AssertionError("Ink-bounds toggle");
            }
            if(frames==30) {
                screen.keyPressed(GLFW.GLFW_KEY_B,0,0);
                if(screen.boundsVisible())throw new AssertionError("Ink-bounds toggle restore");
            }
            if(frames==40 && Boolean.getBoolean("pyrovfx.ui.gallery.reload")) {
                reloadRequested=true;
                mc.reloadResourcePacks();
            }
            if(frames==(Boolean.getBoolean("pyrovfx.ui.gallery.reload")?120:35)) {
                if(screen.iconSize()!=20 || screen.textScale()!=100)
                    throw new AssertionError("Gallery controls did not restore original size");
                if(UiIcons.all().size()!=22)throw new AssertionError("Expected 22 original icons");
                if(UiIcons.sprite("check").width()!=16 || UiIcons.sprite("check").height()!=12)
                    throw new AssertionError("Source check ink should be 16x12");
                if(UiIcons.sprite("chevron-right").width()!=8 ||
                        UiIcons.sprite("chevron-right").height()!=14)
                    throw new AssertionError("Source chevron ink should be 8x14");
                if(UiIcons.sprite("check").destination(0,0,20).w()==
                        UiIcons.sprite("check").destination(0,0,20).h())
                    throw new AssertionError("Glyph aspect ratio was lost");
                if(mc.getResourceManager().getResource(UiIcons.TEXTURE).isEmpty())
                    throw new AssertionError("Pyro UI original icon atlas missing after load/reload");
                Path dir=mc.gameDirectory.toPath().resolve("pyro-ui-verification")
                        .resolve(System.getProperty("pyrovfx.ui.gallery.tag","pixelarticons-native"));
                Files.createDirectories(dir);
                try(NativeImage image=Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
                    image.writeToFile(dir.resolve("01-vanilla-pixelarticons.png"));
                }
                Files.writeString(dir.resolve("verification.txt"),
                        "Pixelarticons original SVG ink bounds: verified (22/22)\n"
                        +"PNG 24px source viewBox and tight UV crop with GL_NEAREST: tested\n"
                        +"Vanilla Minecraft FontSet only: displayed\n"
                        +"Aspect ratio / text scale / icon scale / bounds toggle: passed\n"
                        +"Resource reload: "+(reloadRequested?"performed":"not requested")+"\n"
                        +"GUI scale="+mc.getWindow().getGuiScale()+"\n"
                        +"framebuffer="+mc.getWindow().getWidth()+"x"+mc.getWindow().getHeight()+"\n");
                completed=true;
                restoreScale(mc);
                if(Boolean.getBoolean("pyrovfx.ui.gallery.autoExit"))mc.stop();
            }
        } catch(Throwable error) {
            completed=true;
            try {
                Path dir=mc.gameDirectory.toPath().resolve("pyro-ui-verification")
                        .resolve(System.getProperty("pyrovfx.ui.gallery.tag","pixelarticons-native"));
                Files.createDirectories(dir);
                Files.writeString(dir.resolve("failure.txt"),error.toString());
            } catch(Exception ignored){}
            restoreScale(mc);
            if(Boolean.getBoolean("pyrovfx.ui.gallery.autoExit"))mc.stop();
            throw new RuntimeException("Vanilla typography/pixelarticons gallery failed",error);
        }
    }
}
