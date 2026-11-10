package net.kofllee.pyroui;

import java.util.EnumMap;
import java.util.Map;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Drop-in Minecraft screen for a Pyro UI tree. This is part of Pyro UI itself:
 * any NeoForge mod can create it without Pyro VFX on the classpath.
 *
 * Add a single panel with add(element), or use panels() to build a dock workspace.
 */
public class PyroScreen extends Screen {
    protected final UiTheme theme;
    protected final UiDockManager ui;
    protected int lastLogicalWidth, lastLogicalHeight, lastScalePercent;
    private final Map<UiDockManager.Cursor, Long> cursors = new EnumMap<>(UiDockManager.Cursor.class);
    private UiDockManager.Cursor lastCursor;
    private final UiFontService fontService = new UiFontService();
    private final MinecraftWorldFrame worldFrame = new MinecraftWorldFrame();
    private boolean gameplayCaptured;
    private Screen nestedGameScreen;
    private boolean nestedEscapeHeld;
    private boolean suspendedForVanillaScreen;

    public PyroScreen() { this(Component.literal("Pyro UI")); }
    public PyroScreen(Component title) { this(title, new UiTheme()); }
    public PyroScreen(Component title, UiTheme theme) {
        super(title);
        this.theme = theme;
        this.ui = new UiDockManager(theme);
    }

    public UiTheme uiTheme() { return theme; }
    public UiDockManager panels() { return ui; }
    public UiFontService fonts() { return fontService; }
    /** True while the actual Minecraft viewport owns movement/camera controls. */
    public boolean gameplayCaptured() {return gameplayCaptured;}
    public boolean nestedGameScreenOpen() {return nestedGameScreen!=null;}
    /** Display vanilla GUIs inside the Minecraft viewport instead of replacing
     * the whole editor screen. All their coordinates are viewport-relative.
     */
    public boolean openNestedGameScreen(Screen next) {
        UiRect body=ui.minecraftViewportBody();
        if(next==null || body==null || body.w()<1 || body.h()<1)return false;
        releaseGameplay();
        if(nestedGameScreen!=null)nestedGameScreen.removed();
        nestedGameScreen=next;
        next.init(Minecraft.getInstance(),body.w(),body.h());
        return true;
    }
    public void closeNestedGameScreen() {
        if(nestedGameScreen==null)return;
        Screen old=nestedGameScreen;
        nestedGameScreen=null;
        old.removed();
        ui.context().setFocus(null);
    }
    /** A nested inventory is not Minecraft.screen. Its vanilla onClose()
     * would pop a GUI layer belonging to the OUTER editor. Close the menu
     * through the player instead, then dismiss only the embedded screen.
     */
    private void closeNestedFromEscape() {
        Screen nested=nestedGameScreen;
        if(nested==null)return;
        if(nested instanceof AbstractContainerScreen<?> &&
                Minecraft.getInstance().player!=null)
            Minecraft.getInstance().player.closeContainer();
        if(nestedGameScreen==nested)closeNestedGameScreen();
    }
    private UiRect nestedGameBounds() {return ui.minecraftViewportBody();}
    private double nestedX(double x) {
        UiRect r=nestedGameBounds();
        return x/getScaleFactor()-(r==null?0:r.x());
    }
    private double nestedY(double y) {
        UiRect r=nestedGameBounds();
        return y/getScaleFactor()-(r==null?0:r.y());
    }
    /** Keep the workspace alive across vanilla inventory/chat/settings screens. */
    public void suspendForVanillaScreen() {suspendedForVanillaScreen=true;}
    public void resumeFromVanillaScreen() {suspendedForVanillaScreen=false;}
    /** A request to leave gameplay must return to the editor, not fullscreen world. */
    public void exitGameplayFocus() {releaseGameplay();}

    private void captureGameplay() {
        if(gameplayCaptured || nestedGameScreen!=null)return;
        Minecraft mc=Minecraft.getInstance();
        if(mc.player==null || mc.level==null)return;
        gameplayCaptured=true;
        ui.context().setFocus(null);
        ui.context().capture=null;
        ui.context().mouseX=-100000;
        ui.context().mouseY=-100000;
        GLFW.glfwSetInputMode(mc.getWindow().getWindow(),GLFW.GLFW_CURSOR,GLFW.GLFW_CURSOR_DISABLED);
        GLFW.glfwSetCursor(mc.getWindow().getWindow(),0L);
        lastCursor=null;
        mc.mouseHandler.setIgnoreFirstMove();
        KeyMapping.releaseAll();
    }
    private void releaseGameplay() {
        if(!gameplayCaptured)return;
        gameplayCaptured=false;
        KeyMapping.releaseAll();
        GLFW.glfwSetInputMode(Minecraft.getInstance().getWindow().getWindow(),
                GLFW.GLFW_CURSOR,GLFW.GLFW_CURSOR_NORMAL);
        GLFW.glfwSetCursor(Minecraft.getInstance().getWindow().getWindow(),0L);
        Minecraft.getInstance().mouseHandler.setIgnoreFirstMove();
        lastCursor=null;
    }
    private void gameKey(KeyMapping binding,boolean pressed,boolean click) {
        ((GameKeyAccess)binding).pyroUi$setGameKey(pressed,click);
    }
    private static boolean gameplayBinding(KeyMapping binding) {
        var context=binding.getKeyConflictContext();
        return context==KeyConflictContext.IN_GAME || context==KeyConflictContext.UNIVERSAL;
    }
    /** The native GUI keyboard path may not dispatch modifier-only keys to
     * a Screen consistently. Synchronize their real GLFW state each tick.
     * This also handles modifiers already held when the viewport is captured.
     */
    public void pollGameplayModifiers() {
        if(!gameplayCaptured)return;
        long window=Minecraft.getInstance().getWindow().getWindow();
        int[] keys={GLFW.GLFW_KEY_LEFT_SHIFT,GLFW.GLFW_KEY_RIGHT_SHIFT,
                GLFW.GLFW_KEY_LEFT_CONTROL,GLFW.GLFW_KEY_RIGHT_CONTROL,
                GLFW.GLFW_KEY_LEFT_ALT,GLFW.GLFW_KEY_RIGHT_ALT};
        for(KeyMapping binding:Minecraft.getInstance().options.keyMappings) {
            if(!gameplayBinding(binding))continue;
            for(int key:keys)if(binding.getKey().equals(InputConstants.getKey(key,0))) {
                gameKey(binding,GLFW.glfwGetKey(window,key)==GLFW.GLFW_PRESS,false);
                break;
            }
        }
    }
    private void gameModifiers(int key,int modifiers,boolean pressed) {
        // GLFW reports the held modifier mask on keyboard events. Reconcile
        // modifier-only vanilla bindings here as well: Screen input handling
        // does not run the normal gameplay KeyMapping dispatch.
        int[] keys={GLFW.GLFW_KEY_LEFT_SHIFT,GLFW.GLFW_KEY_RIGHT_SHIFT,
                GLFW.GLFW_KEY_LEFT_CONTROL,GLFW.GLFW_KEY_RIGHT_CONTROL,
                GLFW.GLFW_KEY_LEFT_ALT,GLFW.GLFW_KEY_RIGHT_ALT};
        int[] masks={GLFW.GLFW_MOD_SHIFT,GLFW.GLFW_MOD_SHIFT,
                GLFW.GLFW_MOD_CONTROL,GLFW.GLFW_MOD_CONTROL,
                GLFW.GLFW_MOD_ALT,GLFW.GLFW_MOD_ALT};
        for(KeyMapping binding:Minecraft.getInstance().options.keyMappings) {
            if(!gameplayBinding(binding))continue;
            for(int i=0;i<keys.length;i++) {
                if(!binding.getKey().equals(InputConstants.getKey(keys[i],0)))continue;
                boolean down=(modifiers&masks[i])!=0;
                if(key==keys[i])down=pressed;
                gameKey(binding,down,false);
                break;
            }
        }
    }
    private void gameKeyboard(int key,int scanCode,boolean pressed) {
        var input=InputConstants.getKey(key,scanCode);
        for(KeyMapping binding:Minecraft.getInstance().options.keyMappings) {
            if(!binding.getKey().equals(input))continue;
            if(gameplayBinding(binding))
                gameKey(binding,pressed,pressed);
        }
    }
    private void gameMouse(int button,boolean pressed) {
        var input=InputConstants.Type.MOUSE.getOrCreate(button);
        Minecraft mc=Minecraft.getInstance();
        for(KeyMapping binding:mc.options.keyMappings) {
            if(!gameplayBinding(binding) || !binding.getKey().equals(input))continue;
            gameKey(binding,pressed,false);
            if(!pressed)continue;
            // Preserve vanilla immediate action semantics AND respect rebinding
            // instead of assuming left=attack, right=use, middle=pick.
            if(binding==mc.options.keyAttack)((GameClickAccess)mc).pyroUi$clickGame(0);
            else if(binding==mc.options.keyUse)((GameClickAccess)mc).pyroUi$clickGame(1);
            else if(binding==mc.options.keyPickItem)((GameClickAccess)mc).pyroUi$clickGame(2);
            else gameKey(binding,true,true);
        }
    }

    /** Simple one-pane usage for mods that do not need a custom docking layout. */
    public PyroScreen add(UiElement element) {
        if (ui.find("main") == null) ui.window("main", "Panel", element);
        else ui.find("main").content = element;
        ui.singlePanelLayout();
        updateSize();
        return this;
    }

    @Override protected void init() {
        super.init();
        updateSize();
        if(nestedGameScreen!=null) {
            UiRect body=nestedGameBounds();
            if(body!=null)nestedGameScreen.resize(Minecraft.getInstance(),body.w(),body.h());
        }
    }
    @Override public void tick() {
        super.tick();
        if(nestedGameScreen!=null)nestedGameScreen.tick();
    }

    /** GUI display size is distinct from framebuffer pixels. One factor for geometry and input. */
    protected float getScaleFactor() {
        float fit = Math.max(.2f, Math.min(width / 950f, height / 535f));
        return fit * .82f * theme.scalePercent / 100f;
    }

    protected void updateSize() {
        float factor = getScaleFactor();
        lastScalePercent = theme.scalePercent;
        lastLogicalWidth = Math.max(1, (int) Math.ceil(width / factor));
        lastLogicalHeight = Math.max(1, (int) Math.ceil(height / factor));
        ui.resize(lastLogicalWidth, lastLogicalHeight);
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        float factor = getScaleFactor();
        if (theme.scalePercent != lastScalePercent
                || lastLogicalWidth != (int) Math.ceil(width / factor)
                || lastLogicalHeight != (int) Math.ceil(height / factor)) {
            updateSize();
        }
        if(ui.hasMinecraftViewport())worldFrame.capture();
        MinecraftUiRenderer canvas = new MinecraftUiRenderer(graphics, factor, fontService)
                .worldFrame(worldFrame);
        ui.context().fontHeight = canvas.fontHeight();
        ui.context().measureHeading = canvas::measureHeading;
        ui.context().readClipboard = () -> Minecraft.getInstance().keyboardHandler.getClipboard();
        ui.context().writeClipboard = value -> Minecraft.getInstance().keyboardHandler.setClipboard(value);
        if(!gameplayCaptured)updateCursor(mouseX / factor, mouseY / factor);
        graphics.pose().pushPose();
        try {
            graphics.pose().scale(factor, factor, 1f);
            // A captured GLFW cursor has synthetic/centered coordinates.
            // Never send these into the editor's hover/tooltip hit tests.
            ui.draw(canvas, gameplayCaptured?-100000:mouseX/factor,
                    gameplayCaptured?-100000:mouseY/factor);
        } finally {
            canvas.finish();
            graphics.pose().popPose();
        }
        renderNestedGameScreen(graphics,mouseX,mouseY,partialTick);
    }
    private void renderNestedGameScreen(GuiGraphics graphics,int mouseX,int mouseY,float partialTick) {
        if(nestedGameScreen==null)return;
        UiRect r=nestedGameBounds();
        if(r==null)return;
        float factor=getScaleFactor();
        if(nestedGameScreen.width!=r.w() || nestedGameScreen.height!=r.h())
            nestedGameScreen.resize(Minecraft.getInstance(),r.w(),r.h());
        graphics.pose().pushPose();
        try {
            graphics.pose().scale(factor,factor,1f);
            graphics.pose().translate(r.x(),r.y(),100f);
            graphics.enableScissor(0,0,r.w(),r.h());
            nestedGameScreen.render(graphics,
                    (int)(mouseX/factor-r.x()),(int)(mouseY/factor-r.y()),partialTick);
            graphics.disableScissor();
        } finally {
            graphics.pose().popPose();
        }
    }

    private void updateCursor(double x, double y) {
        UiDockManager.Cursor current = ui.cursor(x, y);
        if (current == lastCursor) return;
        long cursor = cursors.computeIfAbsent(current, value ->
                GLFW.glfwCreateStandardCursor(switch (value) {
                    case TEXT -> GLFW.GLFW_IBEAM_CURSOR;
                    case RESIZE_HORIZONTAL -> GLFW.GLFW_HRESIZE_CURSOR;
                    case RESIZE_VERTICAL -> GLFW.GLFW_VRESIZE_CURSOR;
                    case RESIZE_DIAGONAL -> GLFW.GLFW_RESIZE_NWSE_CURSOR;
                    case MOVE -> GLFW.GLFW_HAND_CURSOR;
                    case CROSSHAIR -> GLFW.GLFW_CROSSHAIR_CURSOR;
                    default -> GLFW.GLFW_ARROW_CURSOR;
                }));
        if (cursor != 0L) GLFW.glfwSetCursor(Minecraft.getInstance().getWindow().getWindow(), cursor);
        lastCursor = current;
    }

    @Override public boolean mouseClicked(double x, double y, int button) {
        if(nestedGameScreen!=null) {
            UiRect r=nestedGameBounds();
            if(r!=null && r.contains(x/getScaleFactor(),y/getScaleFactor()))
                nestedGameScreen.mouseClicked(nestedX(x),nestedY(y),button);
            return true;
        }
        if(gameplayCaptured) {
            gameMouse(button,true);
            return true;
        }
        double logicalX=x/getScaleFactor(),logicalY=y/getScaleFactor();
        // Exactly the same topmost-body/divider hit test determines whether
        // the click starts gameplay or a UI interaction. Do not first start a
        // splitter drag and then unexpectedly capture the Minecraft mouse.
        if(button==0 && ui.minecraftViewportAt(logicalX,logicalY)) {
            captureGameplay();
            return true;
        }
        return ui.pointerDown(logicalX,logicalY,button);
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        if(nestedGameScreen!=null) {
            nestedGameScreen.mouseReleased(nestedX(x),nestedY(y),button);
            return true;
        }
        if(gameplayCaptured) {
            gameMouse(button,false);
            return true;
        }
        return ui.pointerUp(x / getScaleFactor(), y / getScaleFactor(), button);
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if(nestedGameScreen!=null) {
            nestedGameScreen.mouseDragged(nestedX(x),nestedY(y),button,
                    dx/getScaleFactor(),dy/getScaleFactor());
            return true;
        }
        if(gameplayCaptured)return true;
        return ui.pointerMove(x / getScaleFactor(), y / getScaleFactor());
    }
    @Override public void mouseMoved(double x,double y) {
        // MouseHandler owns physical mouse deltas, sensitivity, cinematic
        // smoothing, scoped sensitivity and NeoForge turn hooks. Never
        // implement a second GUI-coordinate camera turn here.
        if(!gameplayCaptured)super.mouseMoved(x,y);
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if(nestedGameScreen!=null) {
            nestedGameScreen.mouseScrolled(nestedX(x),nestedY(y),horizontal,vertical);
            return true;
        }
        if(gameplayCaptured) {
            Minecraft mc=Minecraft.getInstance();
            if(mc.player!=null)mc.player.getInventory().swapPaint(vertical);
            return true;
        }
        return ui.wheel(x / getScaleFactor(), y / getScaleFactor(), vertical);
    }
    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        if(key==GLFW.GLFW_KEY_ESCAPE && nestedEscapeHeld)return true;
        if(nestedGameScreen!=null) {
            if(key==GLFW.GLFW_KEY_ESCAPE) {
                // Consume the entire physical ESC press (including repeat)
                // so it cannot also close the outer editor.
                nestedEscapeHeld=true;
                closeNestedFromEscape();
                return true;
            }
            nestedGameScreen.keyPressed(key,scanCode,modifiers);
            return true;
        }
        if(gameplayCaptured) {
            if(key==GLFW.GLFW_KEY_ESCAPE) {releaseGameplay();return true;}
            gameModifiers(key,modifiers,true);
            gameKeyboard(key,scanCode,true);
            return true;
        }
        return ui.key(key, modifiers) || super.keyPressed(key, scanCode, modifiers);
    }
    @Override public boolean keyReleased(int key,int scanCode,int modifiers) {
        if(key==GLFW.GLFW_KEY_ESCAPE && nestedEscapeHeld) {
            nestedEscapeHeld=false;
            return true;
        }
        if(nestedGameScreen!=null) {
            nestedGameScreen.keyReleased(key,scanCode,modifiers);
            return true;
        }
        if(gameplayCaptured) {
            gameModifiers(key,modifiers,false);
            gameKeyboard(key,scanCode,false);
            return true;
        }
        return super.keyReleased(key,scanCode,modifiers);
    }
    @Override public boolean charTyped(char value, int modifiers) {
        if(nestedGameScreen!=null)return nestedGameScreen.charTyped(value,modifiers);
        if(gameplayCaptured)return true;
        return ui.typed(value) || super.charTyped(value, modifiers);
    }

    @Override public void removed() {
        closeNestedGameScreen();
        releaseGameplay();
        if(suspendedForVanillaScreen) {
            // setScreen(next) calls removed() on the current Screen. Destroying
            // the world frame here would make the resumed editor appear empty.
            super.removed();
            return;
        }
        worldFrame.close();
        ui.context().setFocus(null);
        ui.context().capture = null;
        GLFW.glfwSetCursor(Minecraft.getInstance().getWindow().getWindow(), 0L);
        for (long cursor : cursors.values()) if (cursor != 0L) GLFW.glfwDestroyCursor(cursor);
        cursors.clear();
        lastCursor = null;
        super.removed();
    }
    @Override public boolean isPauseScreen() { return false; }
}
