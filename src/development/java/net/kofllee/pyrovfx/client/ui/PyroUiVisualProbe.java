package net.kofllee.pyrovfx.client.ui;

import com.mojang.blaze3d.platform.NativeImage;
import java.nio.file.Files;
import java.nio.file.Path;
import net.kofllee.pyrovfx.PyroVfx;
import net.kofllee.pyroui.UiRect;
import net.kofllee.pyroui.UiControls;
import net.kofllee.pyroui.UiIcons;
import net.kofllee.pyroui.UiTextureRegion;
import net.kofllee.pyroui.UiFontService;
import net.kofllee.pyrovfx.vfx.curve.VfxCurveDefinition;
import net.kofllee.pyrovfx.vfx.curve.VfxCurvePoint;
import net.kofllee.pyrovfx.vfx.type.VfxCurveType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Actual Minecraft framebuffer smoke proof. Never included in production JAR. */
@EventBusSubscriber(modid = PyroVfx.MOD_ID, value = Dist.CLIENT)
public final class PyroUiVisualProbe {
    private static int ticks;
    private static boolean finished;
    private static int captures;
    private static String failure;
    private static int originalGuiScale = -1;
    private static UiRect localDropPreview,globalDropPreview,sharedLinePreview;
    private PyroUiVisualProbe() {}

    @SubscribeEvent public static void tick(ClientTickEvent.Post event) {
        if (!Boolean.getBoolean("pyrovfx.ui.capture") || finished) return;
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof PyroUiShowcaseScreen screen) || mc.level == null) return;
        try {
            ticks++;
            if (ticks == 1) {
                originalGuiScale = mc.options.guiScale().get();
                int requested = Integer.getInteger("pyrovfx.ui.guiScale",0);
                if (requested > 0 && requested != originalGuiScale) {
                    mc.options.guiScale().set(requested);
                    mc.resizeDisplay();
                }
                screen.docking().defaultLayout();
            }
            switch (ticks) {
                case 15 -> {
                    verifyCurveParity();
                    verifyMinecraftFont();
                    verifyPixelIcons(mc);
                    verifyOriginalPixelArtAtlas(mc);
                }
                case 25 -> {
                    grab(mc, "01-default");
                    // Capture original source-aligned Pixelarticons and vanilla FontSet.
                }
                case 26 -> {
                    var ui = screen.docking();
                    UiRect r = ui.area("inspector");
                    if (r == null) throw new IllegalStateException("Inspector not docked");
                    ui.pointerDown(r.x() + 35, r.y() + 10, 0);
                    ui.pointerMove(470, 6);
                    ui.pointerUp(470, 6, 0);
                    if (!ui.floating("inspector")) throw new IllegalStateException("Undock failed");
                }

                case 27 -> {
                    var ui = screen.docking();
                    UiRect inspector = ui.area("inspector");
                    ui.pointerDown(inspector.x() + 24, inspector.y() + 8, 0);
                    ui.pointerMove(470, 8);
                    ui.pointerUp(470, 8, 0);
                    if (!ui.floating("inspector"))
                        throw new IllegalStateException("Outside-workspace floating drag failed");
                }
                case 49 -> grab(mc, "02-inspector-floating");
                case 50 -> {
                    var ui = screen.docking();
                    UiRect r = ui.area("inspector");
                    if (r == null) throw new IllegalStateException("Floating Inspector disappeared");
                    ui.pointerDown(r.x() + 32, r.y() + 9, 0);
                    UiRect hierarchy = ui.area("hierarchy");
                    if (hierarchy == null) throw new IllegalStateException("Hierarchy disappeared");
                    int x = hierarchy.x() + hierarchy.w() / 2;
                    int y = hierarchy.y() + hierarchy.h() / 2;
                    ui.pointerMove(x, y);
                    if (ui.indicatorsVisible() || ui.previewArea()!=null)
                        throw new IllegalStateException("Docking must not activate during free dragging");
                }
                case 53 -> {
                    if (screen.docking().indicatorsVisible())
                        throw new IllegalStateException("Dock preview appeared before stationary dwell");
                    grab(mc,"03-floating-without-dock");
                }
                case 65 -> {
                    var ui = screen.docking();
                    UiRect target = ui.area("hierarchy");
                    int x=target.x()+target.w()/2, y=target.y()+target.h()/2;
                    ui.pointerMove(x,y);
                    if (!ui.indicatorsVisible())
                        throw new IllegalStateException("Stationary dock hover did not activate preview");
                    UiRect desired=ui.previewArea();
                    grab(mc,"04-dock-hover");
                    ui.pointerUp(x,y,0);
                    if (ui.floating("inspector")) throw new IllegalStateException("Deliberate docking failed");
                    if(!desired.equals(ui.area("inspector")))
                        throw new IllegalStateException("Committed geometry differs from drawn preview");
                }
                case 75 -> grab(mc, "05-inspector-docked");
                case 76 -> {
                    var ui = screen.docking();
                    ui.reopen("settings");
                    UiRect settings = ui.area("settings");
                    // Hit the real Accent dropdown, then select its Cyan item.
                    // Also verifies input routing through a floating window.
                    UiRect accent = screen.accentControlBounds();
                    grab(mc,"06-settings-pre-click");
                    int clickX = accent.x() + accent.w()/2;
                    int clickY = accent.y() + accent.h()/2;
                    ui.pointerDown(clickX, clickY, 0);
                    ui.pointerUp(clickX, clickY, 0);
                    if (!ui.context().popupVisible()) throw new IllegalStateException("Accent dropdown did not open bounds="+accent+" settings="+settings+" cursor="+clickX+","+clickY+" focus="+ui.context().focus);
                    UiRect menu=ui.context().popupBounds();
                    int pickX=menu.x()+20,pickY=menu.y()+4+2*24+12;
                    ui.pointerDown(pickX,pickY,0);
                    ui.pointerUp(pickX,pickY,0);
                    if (screen.theme().accent != 0xFF70D1D2)
                        throw new IllegalStateException("Accent selection did not update theme");
                    screen.theme().animations = false;
                }
                case 100 -> grab(mc, "06-alt-theme");
                case 101 -> screen.docking().context().menu(new UiRect(300, 110, 152, 20),
                        java.util.List.of("Select emitter", "Duplicate emitter", "Reset property"), i -> {});
                case 125 -> grab(mc, "07-context-menu");
                case 126 -> {
                    screen.docking().context().dismissPopup();
                    screen.docking().defaultLayout();
                }
                case 127 -> {
                    var ui = screen.docking();
                    UiRect r = screen.nameControlBounds();
                    int x = r.x() + Math.min(45,r.w()/2), y = r.y() + r.h()/2;
                    ui.pointerDown(x, y, 0);
                    ui.pointerUp(x, y, 0);
                    ui.key(65, 2); // Ctrl+A
                    for (char letter : "Smoke".toCharArray()) ui.typed(letter);
                    if (!screen.selectedDemoName().equals("Smoke"))
                        throw new AssertionError("Name editor input failed: " + screen.selectedDemoName());
                    ui.key(268, 1); // Shift+Home: visible keyboard selection
                }
                case 148 -> grab(mc, "08-text-selection");
                case 149 -> {
                    var ui = screen.docking();
                    ui.key(65, 2);
                    for (char letter : "Main Emitter".toCharArray()) ui.typed(letter);
                    ui.context().setFocus(null);
                }
                case 150 -> {
                    grab(mc, "09-reset-layout");
                    // Capture at the larger default, then explicitly validate
                    // the absolute 80/140% keyboard/toolbar acceptance levels.
                    screen.theme().scalePercent = 100;
                }
                case 151 -> {
                    var ui = screen.docking();
                    int x = Math.max(320,ui.context().frameWidth-239)+99;
                    for (int i=0; i<2; i++) {
                        ui.pointerDown(x, 9, 0);
                        ui.pointerUp(x, 9, 0);
                    }
                    if (screen.theme().scalePercent != 90)
                        throw new AssertionError("Top bar minus control did not reduce scale");
                }
                case 175 -> grab(mc, "10-scale-90");
                case 176 -> {
                    var ui = screen.docking();
                    int x = Math.max(320,ui.context().frameWidth-239)+126;
                    for (int i=0; i<10; i++) {
                        ui.pointerDown(x,9,0);ui.pointerUp(x,9,0);
                    }
                    if (screen.theme().scalePercent != 140)
                        throw new AssertionError("Top bar plus control did not increase scale");
                }
                case 200 -> grab(mc, "11-scale-140");
                case 201 -> {
                    screen.theme().scalePercent = 95;
                    screen.docking().reopen("settings");
                }
                // GUI Scale 4 can render more slowly than the game tick;
                // leave several ticks for the requested state to reach the framebuffer.
                case 208 -> grab(mc, "11a-ui-scale-95-settings");
                case 209 -> screen.theme().scalePercent = 40;
                case 215 -> grab(mc, "11b-ui-scale-40");
                case 216 -> {
                    screen.theme().scalePercent = 100;
                    screen.docking().close("settings");
                    chooseCurve(screen, 1);
                }
                case 225 -> grab(mc, "12-curve-step");
                case 226 -> chooseCurve(screen, 2);
                case 250 -> grab(mc, "13-curve-catmull");
                case 251 -> chooseCurve(screen, 3);
                case 275 -> grab(mc, "14-curve-bezier");
                case 277 -> {
                    var ui = screen.docking();
                    ui.reopen("settings");
                    UiRect box = ui.area("settings");
                    ui.pointerDown(box.x() + 35, box.y() + 8, 0);
                    ui.pointerMove(495, 17);
                    ui.pointerUp(495, 17, 0);
                    if (!ui.floating("settings")) throw new AssertionError("Free floating window unexpectedly docked");
                    UiRect moving = ui.area("settings");
                    int x = moving.x() + moving.w() - 5;
                    int y = moving.y() + moving.h() - 5;
                    ui.pointerDown(x, y, 0);
                    ui.pointerMove(x + 70, y + 35);
                    ui.pointerUp(x + 70, y + 35, 0);
                    UiRect resized = ui.area("settings");
                    if (resized.w() <= moving.w() || resized.h() <= moving.h())
                        throw new AssertionError("Floating resize did not apply");
                }
                case 300 -> grab(mc, "15-floating-resized");
                case 301 -> {
                    var ui = screen.docking();
                    ui.close("settings");
                    UiRect preview = ui.area("preview");
                    int x = preview.x() + Math.max(25, preview.w() / 3);
                    int y = preview.y() + preview.h() + 1;
                    ui.pointerDown(x, y, 0);
                    ui.pointerMove(x, y - 55);
                    ui.pointerUp(x, y - 55, 0);
                    if (ui.area("preview").h() == preview.h())
                        throw new AssertionError("Dock split resize did not apply");
                }
                case 324 -> grab(mc, "16-split-resized");
                case 326 -> {
                    var ui=screen.docking();
                    ui.reopen("settings");
                    UiRect field=screen.scaleNumberBounds();
                    int x=field.x()+Math.max(25,field.w()/2),y=field.y()+field.h()/2;
                    ui.pointerDown(x,y,0);ui.pointerUp(x,y,0);
                    ui.key(65,2); // Ctrl+A
                    for(char ch : "217".toCharArray()) ui.typed(ch);
                    ui.key(257,0);
                    if(screen.theme().scalePercent!=217)
                        throw new AssertionError("Scale field should accept 1% increments: "+
                                screen.theme().scalePercent);
                }
                case 343 -> {
                    UiRect box=screen.docking().area("settings");
                    if(box.x()<0 || box.y()<0 ||
                            box.x()+box.w()>screen.docking().context().frameWidth ||
                            box.y()+box.h()>screen.docking().context().frameHeight)
                        throw new AssertionError("Floating settings escaped the screen at 217%: "+box);
                    grab(mc,"17-scale-217");
                }
                case 344 -> {
                    screen.theme().scalePercent=100;
                    screen.docking().close("settings");
                }
                case 345 -> {
                    screen.theme().scalePercent=95;
                    screen.docking().reopen("settings");
                }
                case 347 -> grab(mc,"18-settings-scale-95");
                case 348 -> screen.docking().close("settings");
                case 349 -> {
                    var ui=screen.docking();
                    ui.reopen("settings");
                    UiRect source=ui.area("settings"),target=ui.area("preview");
                    ui.pointerDown(source.x()+25,source.y()+8,0);
                    ui.pointerMove(target.x()+target.w()/2,target.y()+target.h()-65);
                    if(ui.previewArea()!=null)
                        throw new AssertionError("Local zone must not snap immediately");
                    if(!ui.floating("settings"))
                        throw new AssertionError("Settings should remain floating during free drag");
                }
                case 355 -> grab(mc,"19-local-bottom-free-drag");
                case 365 -> {
                    var ui=screen.docking();
                    UiRect target=ui.area("preview");
                    ui.pointerMove(target.x()+target.w()/2,target.y()+target.h()-65);
                    localDropPreview=ui.previewArea();
                    if(localDropPreview==null || localDropPreview.w()!=target.w())
                        throw new AssertionError("Delayed local-bottom preview is not column-sized");
                    grab(mc,"20-local-bottom-preview");
                }
                case 366 -> {
                    var ui=screen.docking();
                    int x=localDropPreview.x()+localDropPreview.w()/2;
                    int y=ui.area("preview").y()+ui.area("preview").h()-65;
                    ui.pointerUp(x,y,0);
                    if(!localDropPreview.equals(ui.area("settings")))
                        throw new AssertionError("Local drop does not match its preview");
                    UiRect docked=ui.area("settings");
                    ui.pointerDown(docked.x()+23,docked.y()+8,0);
                    int bottom=ui.context().frameHeight-13;
                    ui.pointerMove(ui.context().frameWidth/2,bottom);
                    if(ui.previewArea()!=null)
                        throw new AssertionError("Outer bottom should also require stationary dwell");
                }
                case 383 -> {
                    var ui=screen.docking();
                    ui.pointerMove(ui.context().frameWidth/2,ui.context().frameHeight-13);
                    globalDropPreview=ui.previewArea();
                    if(globalDropPreview==null || globalDropPreview.w()<ui.context().frameWidth-14)
                        throw new AssertionError("Delayed outer-bottom preview should span workspace");
                    grab(mc,"21-global-bottom-preview");
                }
                case 384 -> {
                    var ui=screen.docking();
                    ui.pointerUp(ui.context().frameWidth/2,ui.context().frameHeight-13,0);
                    if(!globalDropPreview.equals(ui.area("settings")))
                        throw new AssertionError("Full-width drop does not match its preview");
                }
                case 388 -> {
                    var ui=screen.docking();
                    ui.defaultLayout();
                    UiRect game=ui.area("preview");
                    if(game==null)throw new AssertionError("Missing docked Minecraft viewport");
                    UiRect adjacent=ui.area("inspector");
                    int dividerX=(game.x()+game.w()+adjacent.x())/2;
                    int dividerY=game.y()+game.h()/2;
                    if(ui.minecraftViewportAt(dividerX-2,dividerY))
                        throw new AssertionError("Resize cursor and Minecraft capture overlap");
                    if(ui.cursor(dividerX-2,dividerY)!=
                            net.kofllee.pyroui.UiDockManager.Cursor.RESIZE_HORIZONTAL)
                        throw new AssertionError("Adjacent Minecraft edge is not a resize handle");
                    double factor=screen.width/(double)ui.context().frameWidth;
                    double x=(game.x()+game.w()/2)*factor;
                    double y=(game.y()+game.h()/2)*factor;
                    if(!screen.mouseClicked(x,y,0) || !screen.gameplayCaptured())
                        throw new AssertionError("Minecraft viewport did not capture input on click");
                    if(mc.screen!=screen)
                        throw new AssertionError("Game focus replaced the editor screen");
                }
                case 389 -> {
                    screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_W,0,0);
                    if(!mc.options.keyUp.isDown())
                        throw new AssertionError("Game key mapping was not activated");
                    screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT,0,
                            org.lwjgl.glfw.GLFW.GLFW_MOD_SHIFT);
                    if(!mc.options.keyShift.isDown())
                        throw new AssertionError("Shift/sneak mapping was not activated: bound="+
                                mc.options.keyShift.getKey()+", input="+
                                com.mojang.blaze3d.platform.InputConstants.getKey(
                                        org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT,0)+", context="+
                                mc.options.keyShift.getKeyConflictContext()+", screen="+mc.screen);
                    screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL,0,
                            org.lwjgl.glfw.GLFW.GLFW_MOD_SHIFT|
                                    org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL);
                    if(!mc.options.keySprint.isDown())
                        throw new AssertionError("Control/sprint mapping was not activated");
                    screen.keyReleased(org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT,0,
                            org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL);
                    if(mc.options.keyShift.isDown())
                        throw new AssertionError("Shift/sneak stuck after key release");
                    if(!mc.options.keySprint.isDown())
                        throw new AssertionError("Ctrl/sprint dropped when Shift was released");
                    screen.keyReleased(org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_CONTROL,0,0);
                    if(mc.options.keySprint.isDown())
                        throw new AssertionError("Ctrl/sprint stuck after key release");
                }
                case 394 -> {
                    if(mc.player==null || mc.player.input.forwardImpulse<=0)
                        throw new AssertionError("Movement input is blocked while editor is open");
                    if(screen.docking().context().mouseX>-9999 ||
                            screen.docking().context().mouseY>-9999)
                        throw new AssertionError("Captured game cursor still hovers editor controls");
                    screen.keyReleased(org.lwjgl.glfw.GLFW.GLFW_KEY_W,0,
                            org.lwjgl.glfw.GLFW.GLFW_MOD_SHIFT|
                                    org.lwjgl.glfw.GLFW.GLFW_MOD_CONTROL);
                    if(mc.options.keyUp.isDown())
                        throw new AssertionError("Released movement key remained down");
                }
                case 395 -> {
                    float yaw=mc.player.getYRot(),pitch=mc.player.getXRot();
                    simulateNativeMouseDelta(mc,45,17);
                    if(mc.player.getYRot()==yaw && mc.player.getXRot()==pitch)
                        throw new AssertionError("Raw MouseHandler motion did not turn the vanilla player");
                    // Mouse use should happen in the mouse event, not 50ms
                    // later at the tail of Minecraft's gameplay tick.
                    java.lang.reflect.Field delay=Minecraft.class.getDeclaredField("rightClickDelay");
                    delay.setAccessible(true);
                    screen.mouseClicked(350,317,1);
                    if(delay.getInt(mc)<=0)
                        throw new AssertionError("Right click was deferred instead of immediately invoking vanilla use");
                    screen.mouseReleased(350,317,1);
                    screen.mouseClicked(350,317,0);
                    if(!mc.options.keyAttack.isDown())
                        throw new AssertionError("Mouse attack did not reach vanilla KeyMapping");
                    screen.mouseReleased(350,317,0);
                    if(mc.options.keyAttack.isDown())
                        throw new AssertionError("Mouse attack remained held after release");
                }
                case 400 -> {
                    grab(mc,"22-minecraft-gameplay-focus");
                    if(!screen.keyPressed(org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE,0,0))
                        throw new AssertionError("Escape did not release game focus");
                    if(screen.gameplayCaptured() || mc.screen!=screen)
                        throw new AssertionError("Escape should restore editor focus without closing UI");
                }
                case 402 -> {
                    var ui=screen.docking();
                    UiRect game=ui.area("preview");
                    double factor=screen.width/(double)ui.context().frameWidth;
                    screen.mouseClicked((game.x()+game.w()/2)*factor,
                            (game.y()+game.h()/2)*factor,0);
                    if(!screen.gameplayCaptured())
                        throw new AssertionError("Minecraft viewport could not recapture game focus");
                    mc.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player));
                    if(mc.screen!=screen || !screen.nestedGameScreenOpen())
                        throw new AssertionError("Inventory should render inside Minecraft panel, not replace Pyro UI");
                    // Test the actual Minecraft keyboard dispatch, not a
                    // direct PyroScreen.keyPressed() unit simulation.
                    long window=mc.getWindow().getWindow();
                    mc.keyboardHandler.keyPress(window,org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE,
                            0,org.lwjgl.glfw.GLFW.GLFW_PRESS,0);
                    mc.keyboardHandler.keyPress(window,org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE,
                            0,org.lwjgl.glfw.GLFW.GLFW_REPEAT,0);
                    if(mc.screen!=screen)
                        throw new AssertionError("Escape reached outer editor after closing inventory");
                    mc.keyboardHandler.keyPress(window,org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE,
                            0,org.lwjgl.glfw.GLFW.GLFW_RELEASE,0);
                    if(mc.screen!=screen || screen.gameplayCaptured() || screen.nestedGameScreenOpen())
                        throw new AssertionError("Escape must close ONLY embedded inventory, retaining editor");
                }
                case 409 -> {
                    var ui=screen.docking();
                    UiRect game=ui.area("preview");
                    ui.pointerDown(game.x()+36,game.y()+8,0);
                    ui.pointerMove(game.x()+210,game.y()+90);
                    if(!ui.floating("preview"))
                        throw new AssertionError("Minecraft must detach into a floating window during drag");
                    ui.pointerUp(game.x()+210,game.y()+90,0);
                    UiRect floated=ui.area("preview");
                    if(!ui.minecraftViewportAt(floated.x()+45,floated.y()+55))
                        throw new AssertionError("Floating Minecraft lost its game viewport interaction surface");
                }
                case 416 -> grab(mc,"23-minecraft-floating-window");
                case 425 -> {
                    var ui=screen.docking();
                    ui.defaultLayout();
                    ui.reopen("settings");
                    UiRect source=ui.area("settings");
                    UiRect center=ui.area("preview"),right=ui.area("inspector");
                    int boundary=(center.x()+center.w()+right.x())/2;
                    int y=center.y()+center.h()/2;
                    ui.pointerDown(source.x()+26,source.y()+8,0);
                    ui.pointerMove(boundary+9,y);
                    if(ui.previewArea()!=null)
                        throw new AssertionError("Shared-edge docking should have a short hover delay");
                }
                case 446 -> {
                    var ui=screen.docking();
                    UiRect center=ui.area("preview"),right=ui.area("inspector");
                    int boundary=(center.x()+center.w()+right.x())/2;
                    int y=center.y()+center.h()/2;
                    ui.pointerMove(boundary+9,y);
                    sharedLinePreview=ui.previewArea();
                    if(sharedLinePreview==null || sharedLinePreview.h()<center.h()-4)
                        throw new AssertionError("Could not dock along the full shared border");
                    grab(mc,"24-shared-boundary-docking");
                }
                case 447 -> {
                    var ui=screen.docking();
                    UiRect center=ui.area("preview"),right=ui.area("inspector");
                    int boundary=(center.x()+center.w()+right.x())/2;
                    int y=center.y()+center.h()/2;
                    ui.pointerUp(boundary+9,y,0);
                    if(!sharedLinePreview.equals(ui.area("settings")))
                        throw new AssertionError("Shared-border drop differs from its preview");
                }
                case 465 -> {
                    Path report = directory(mc).resolve("verification.txt");
                    Files.writeString(report,
                            "Minecraft frame capture: " + captures + "/27\n"
                            + "Floating drag outside workspace: passed\n"
                            + "Inspector undocking: passed\n"
                            + "Floating-first drag, delayed docking and exact-drop rectangle: passed\n"
                            + "Local bottom and full-width outer bottom previews: passed\n"
                            + "Theme switch: passed\n"
                            + "Context menu: captured\n"
                            + "Layout reset: captured\n" + "Text input and selection: passed\n"
                            + "Top bar Scale minus/plus controls (90 and 140): passed\n"
                            + "95% floating-settings and 40% screenshots: captured\n"
                            + "Four VFX curve modes parity (404 samples): passed\n"
                            + "Step, Catmull, Bezier dropdown screenshots: captured\n"
                            + "Floating resize, split resize: passed\n"
                            + "Minecraft font metrics and Pyro UI sprite atlas: verified\n"
                            + "Exact numeric scale editing to 217%: passed\n"
                            + "Minecraft focus, WASD, raw camera look, immediate mouse use/attack and Escape: passed\n"
                            + "Vanilla inventory opens and returns to editor: passed\n"
                            + "Minecraft remains a live floating, interactive window: passed\n"
                            + "Full shared-line docking and exact preview geometry: passed\n"
                            + "resolution=" + mc.getWindow().getWidth() + "x" + mc.getWindow().getHeight() + "\n"
                            + "guiScale=" + mc.getWindow().getGuiScale() + "\n");
                    restoreGuiScale(mc);
                    finished = true;
                    if (Boolean.getBoolean("pyrovfx.ui.autoExit")) mc.stop();
                }
                default -> {}
            }
        } catch (Throwable error) {
            failure = error.toString();
            finished = true;
            try { Files.writeString(directory(mc).resolve("failure.txt"), failure); }
            catch (Exception ignored) {}
            restoreGuiScale(mc);
            if (Boolean.getBoolean("pyrovfx.ui.autoExit")) mc.stop();
            throw new RuntimeException("Pyro UI visual probe failed", error);
        }
    }
    /** Test harness only: inject raw GLFW-style deltas into Minecraft's own
     * mouse accumulator. This exercises the real turnPlayer and NeoForge
     * sensitivity path, not a second camera formula in PyroScreen. */
    private static void simulateNativeMouseDelta(Minecraft mc,double dx,double dy)
            throws ReflectiveOperationException {
        Class<?> type=net.minecraft.client.MouseHandler.class;
        java.lang.reflect.Field fieldX=type.getDeclaredField("accumulatedDX");
        java.lang.reflect.Field fieldY=type.getDeclaredField("accumulatedDY");
        fieldX.setAccessible(true);
        fieldY.setAccessible(true);
        fieldX.setDouble(mc.mouseHandler,dx);
        fieldY.setDouble(mc.mouseHandler,dy);
        mc.mouseHandler.handleAccumulatedMovement();
    }
    private static void restoreGuiScale(Minecraft mc) {
        if (originalGuiScale > 0 && mc.options.guiScale().get() != originalGuiScale) {
            mc.options.guiScale().set(originalGuiScale);
            mc.resizeDisplay();
        }
    }
    private static void chooseCurve(PyroUiShowcaseScreen screen, int index) {
        var ui = screen.docking();
        UiRect r = ui.area("curve");
        if (r == null) throw new IllegalStateException("Curve Editor missing");
        UiRect field = screen.curveControlBounds();
        int x = field.x() + field.w()/2;
        int y = field.y() + field.h()/2;
        ui.pointerDown(x, y, 0);
        ui.pointerUp(x, y, 0);
        UiRect menu = ui.context().popupBounds();
        if (menu == null) throw new AssertionError("Interpolation dropdown not opened");
        ui.pointerDown(menu.x()+20,menu.y()+4+index*24+12,0);
        ui.pointerUp(menu.x()+20,menu.y()+4+index*24+12,0);
        if (ui.context().popupVisible()) throw new AssertionError("Interpolation dropdown not dismissed");
        if (screen.curveMode() != UiControls.Interpolation.values()[index])
            throw new AssertionError("Wrong curve mode after dropdown selection: " + screen.curveMode());
    }
    private static void verifyOriginalPixelArtAtlas(Minecraft mc) throws Exception {
        var atlas=UiIcons.TEXTURE;
        try(var stream=mc.getResourceManager().getResource(atlas)
                .orElseThrow(() -> new AssertionError("Missing Pyro UI sprite atlas "+atlas)).open();
                NativeImage image=NativeImage.read(stream)) {
            if(image.getWidth()!=22*26 || image.getHeight()!=26)
                throw new AssertionError("Pyro UI atlas must be 22 cells of 26px (native 24px + gutters)");
            for(int cell=0;cell<22;cell++) {
                int painted=0;
                for(int y=0;y<24;y++)for(int x=0;x<24;x++) {
                    int alpha=(image.getPixelRGBA(cell*26+1+x,y+1)>>>24)&255;
                    if(alpha!=0 && alpha!=255)
                        throw new AssertionError("Pixel icon atlas has blurred alpha");
                    if(alpha==255)painted++;
                }
                if(painted<3)throw new AssertionError("Atlas icon cell "+cell+" empty");
            }
        }
        for(String name:java.util.List.of("close","check","chevron-down",
                "settings-cog","move","fire","expand","plus","minus")) {
            if(UiIcons.region(name)==null)
                throw new AssertionError("Missing Pyro UI atlas icon "+name);
        }
    }
    private static void verifyMinecraftFont() {
        int pixels=Minecraft.getInstance().font.width(
                net.minecraft.network.chat.Component.literal("Mojangles"));
        if(pixels<30 || pixels>95)
            throw new AssertionError("Vanilla Minecraft font metrics unavailable: "+pixels);
    }
    private static void verifyPixelIcons(Minecraft mc) {
        // Actual occupied ink differs for every glyph. The atlas cell is a
        // source viewBox, not the rendered/visible icon size.
        var regions=new java.util.HashSet<Integer>();
        for(String name:java.util.List.of("close","check","checkbox",
                "chevron-down","chevron-right","chevron-up","plus","minus",
                "settings-cog","sliders","folder","folder-plus","pencil",
                "refresh","move","eye","play","pause","expand","collapse",
                "menu","fire")) {
            UiTextureRegion region=UiIcons.region(name);
            if(region==null || region.width()<1 || region.width()>24
                    || region.height()<1 || region.height()>24
                    || region.textureWidth()!=572 || region.textureHeight()!=26
                    || !regions.add(region.u()))
                throw new AssertionError("Invalid/shared icon atlas region: "+name);
        }
        if(regions.size()!=22)throw new AssertionError("22 icons required");
    }
    private static void verifyCurveParity() {
        var points = java.util.List.of(new UiControls.Point(0, 0.05),
                new UiControls.Point(.18, .82), new UiControls.Point(.55, .38),
                new UiControls.Point(1, 1));
        var runtimePoints = points.stream().map(p -> new VfxCurvePoint(p.time(), p.value())).toList();
        var widget = new UiControls.Curve(() -> {});
        widget.setPoints(points);
        for (VfxCurveType type : VfxCurveType.values()) {
            widget.interpolation(UiControls.Interpolation.valueOf(type.name()));
            var runtime = new VfxCurveDefinition(type, runtimePoints);
            for (int i=0; i<=100; i++) {
                double t=i/100.0;
                if (Math.abs(widget.sample(t)-runtime.sample(t)) > 1e-10)
                    throw new AssertionError("Curve mismatch " + type + " at " + t);
            }
        }
    }
    private static Path directory(Minecraft mc) throws java.io.IOException {
        Path dir = mc.gameDirectory.toPath().resolve("pyro-ui-verification")
                .resolve(System.getProperty("pyrovfx.ui.capture.tag", "after-1280"));
        Files.createDirectories(dir);
        return dir;
    }
    private static void grab(Minecraft mc, String name) throws java.io.IOException {
        // Grab the previous *completed* framebuffer, matching VfxBenchHarness.
        try (NativeImage image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            image.writeToFile(directory(mc).resolve(name + ".png"));
        }
        captures++;
    }
}
