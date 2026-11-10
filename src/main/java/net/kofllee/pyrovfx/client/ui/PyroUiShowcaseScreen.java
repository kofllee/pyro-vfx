package net.kofllee.pyrovfx.client.ui;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.kofllee.pyroui.UiCanvas;
import net.kofllee.pyroui.UiControls;
import net.kofllee.pyroui.UiDockManager;
import net.kofllee.pyroui.UiElement;
import net.kofllee.pyroui.UiRect;
import net.kofllee.pyroui.UiTheme;
import net.kofllee.pyroui.MinecraftUiRenderer;
import net.kofllee.pyroui.PyroScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Interactive proof-of-UI, deliberately not a JSON effects editor yet. */
public final class PyroUiShowcaseScreen extends PyroScreen {
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm:ss");
    private final Map<String, DemoEmitter> objects = new LinkedHashMap<>();
    private final ArrayList<Message> messages = new ArrayList<>();
    private final Path layoutPath;
    private UiElement inspector, consoleLines, settingsPanel;
    private UiControls.Tree hierarchyTree;
    private UiControls.Curve curvePreview;
    private UiControls.Dropdown accentControl, curveControl;
    private UiControls.TextField nameControl;
    private UiControls.Label scaleReadout;
    private UiControls.Slider scaleSlider;
    private UiControls.NumberField scaleNumber;
    private String selected = "effect/smoke/main";
    private boolean initialized;
    private boolean inspectorPropertiesOpen = true;
    private boolean inspectorAppearanceOpen = true;

    private static final class DemoEmitter {
        String name;
        boolean enabled = true, collision;
        double spawnRate = 24, intensity = .68;
        int shape = 0, color = UiTheme.ACCENT;
        DemoEmitter(String name) { this.name = name; }
    }
    private record Message(String time, String severity, String text) {}

    public PyroUiShowcaseScreen() {
        super(Component.literal("Pyro UI — Ignition Showcase"));
        layoutPath = Minecraft.getInstance().gameDirectory.toPath().resolve("config/pyro-ui-layout.properties");
    }
    @Override protected void init() {
        super.init();
        updateSize();
        if (initialized) return;
        initialized = true;
        objects.put("effect", new DemoEmitter("Demo Effect"));
        objects.put("effect/smoke", new DemoEmitter("Smoke"));
        objects.put("effect/smoke/main", new DemoEmitter("Main Emitter"));
        objects.put("effect/smoke/secondary", new DemoEmitter("Secondary Emitter"));
        objects.put("effect/sparks", new DemoEmitter("Sparks"));
        objects.put("effect/flash", new DemoEmitter("Flash"));
        objects.get("effect/smoke/main").color = 0xFF99968F;
        objects.get("effect/sparks").color = 0xFFFFC660;
        objects.get("effect/flash").color = 0xFFFFF1BC;

        UiElement hierarchy = UiElement.column(10, 9);
        hierarchy.add(UiElement.row(4,0).height(28)
                .add(new UiControls.Label("Effect layers").heading().grow(1))
                .add(new UiControls.Label("6").meta().color(UiTheme.SECONDARY).width(14)));
        hierarchyTree = new UiControls.Tree(List.of(
                new UiControls.TreeEntry("effect", "Demo Effect", 0, true),
                new UiControls.TreeEntry("effect/smoke", "Smoke", 1, true),
                new UiControls.TreeEntry("effect/smoke/main", "Main Emitter", 2, false),
                new UiControls.TreeEntry("effect/smoke/secondary", "Secondary Emitter", 2, false),
                new UiControls.TreeEntry("effect/sparks", "Sparks", 1, false),
                new UiControls.TreeEntry("effect/flash", "Flash", 1, false)
        ), selected, this::select);
        hierarchy.add(hierarchyTree.grow(1));

        inspector = UiElement.column(UiTheme.SPACE_GROUP, 9).scroll();
        rebuildInspector();

        UiElement preview = UiElement.column(0,0);
        preview.add(new WorldPreview().grow(1));

        UiElement curve = UiElement.column(10, 10);
        UiControls.Curve curveWidget = new UiControls.Curve(() -> {});
        curvePreview = curveWidget;
        curve.add(UiElement.row(6, 0).height(30)
                .add(new UiControls.Label("Intensity over lifetime").heading().grow(1))
                .add(curveControl = new UiControls.Dropdown(List.of("Linear", "Step", "Catmull", "Bezier"),
                        0, index -> {
                    curveWidget.interpolation(UiControls.Interpolation.values()[index]);
                    log("Info", "Curve interpolation: " + curveWidget.interpolation());
                })));
        curveControl.width(108);
        curve.add(curveWidget.grow(1));

        UiElement console = UiElement.column(10, 9);
        console.add(UiElement.row(4, 0)
                .add(new UiControls.Label("Session events").heading().grow(1))
                .add(new UiControls.Button("Clear", this::clearMessages).width(57))
                .height(28));
        consoleLines = UiElement.column(3, 2).scroll().grow(1);
        console.add(consoleLines);

        settingsPanel = UiElement.column(UiTheme.SPACE_GROUP, 10).scroll();
        ui.window("hierarchy", "Hierarchy", hierarchy);
        ui.window("preview", "Minecraft", preview).minecraftViewport();
        ui.window("inspector", "Inspector", inspector).accentedHeader();
        ui.window("curve", "Curve Editor", curve);
        ui.window("console", "Console", console);
        ui.window("settings", "UI Settings", settingsPanel).size(292, 287);

        rebuildSettings();
        ui.onLayoutChanged(this::saveLayout);
        ui.defaultLayout();
        if (!ui.load(layoutPath)) log("Info", "Default workspace loaded");
        else log("Info", "Saved workspace restored");
        updateSize();
    }

    private void select(String id) {
        selected = id;
        DemoEmitter current = objects.get(id);
        theme.contextAccent = (current.color & 0xFFFFFF) == 0x99968F ?
                theme.accent : current.color;
        log("Info", "Selected " + current.name);
        rebuildInspector();
        updateSize();
    }
    private UiElement sliderProperty(String label, double initial, double min,
            double max, double step, java.util.function.DoubleConsumer changed) {
        UiControls.Slider[] slider = new UiControls.Slider[1];
        UiControls.NumberField[] field = new UiControls.NumberField[1];
        slider[0] = new UiControls.Slider(initial, min, max, value -> {
            field[0].setNumber(value);
            changed.accept(value);
        });
        field[0] = new UiControls.NumberField(initial, min, max, step, value -> {
            slider[0].setNumber(value);
            changed.accept(value);
        });
        if ("UI Scale".equals(label)) {
            scaleSlider = slider[0];
            scaleNumber = field[0];
        }
        return UiElement.row(UiTheme.SPACE_ITEM, 1).height(24)
                .add(new UiControls.Label(label).width(76))
                .add(slider[0].grow(1))
                .add(field[0].width(69));
    }
    private UiElement property(String label, UiElement field) {
        return UiElement.row(UiTheme.SPACE_ITEM, 1).height(24)
                .add(new UiControls.Label(label).color(UiTheme.SECONDARY).width(88))
                .add(field.grow(1));
    }
    /** Distinct visual groups: 4px between members, 12px between groups. */
    private UiElement section(String label, boolean open, Runnable toggle, UiElement... items) {
        UiElement group = UiElement.column(4,0);
        group.add(new UiControls.Button(label,toggle).section(open).height(24));
        int height = 24;
        if(open) for(UiElement child : items) {
            group.add(child);
            height += 4 + child.preferredHeight();
        }
        return group.height(height);
    }

    private void rebuildInspector() {
        if (inspector == null) return;
        inspector.clear();
        DemoEmitter model=objects.get(selected);
        if(model==null) return;
        inspector.add(new SelectedHeader(model).height(58));
        inspector.add(section("Emitter", inspectorPropertiesOpen,
                () -> {inspectorPropertiesOpen=!inspectorPropertiesOpen;rebuildInspector();updateSize();},
                new UiControls.Toggle("Enabled",model.enabled,v -> {
                    model.enabled=v; log("Info",model.name+" enabled = "+v);
                }).height(24),
                property("Name", nameControl=new UiControls.TextField(model.name,v -> {
                    model.name=v;
                    if(hierarchyTree!=null) hierarchyTree.rename(selected,v);
                })),
                property("Spawn Rate",new UiControls.NumberField(model.spawnRate,0,1000,1,v -> {
                    model.spawnRate=v;log("Info","Spawn Rate = "+Math.round(v));
                }).tooltip("Drag or type a number")),
                sliderProperty("Intensity",model.intensity,0,1,.01,v -> model.intensity=v),
                property("Shape",new UiControls.Dropdown(
                    List.of("Point","Sphere","Box","Cone","Ring"),model.shape,v -> {
                        model.shape=v;log("Info","Spawn Shape changed");
                    }))));
        inspector.add(section("Appearance",inspectorAppearanceOpen,
                () -> {inspectorAppearanceOpen=!inspectorAppearanceOpen;rebuildInspector();updateSize();},
                property("Color",new UiControls.Swatch(model.color,v -> {
                    model.color=v;log("Info","Color updated");
                })),
                new UiControls.Check("Collision",model.collision,v -> {
                    model.collision=v;log("Info","Collision = "+v);
                }).height(24)));
        inspector.add(UiElement.row(6,0).height(24)
                .add(new UiControls.Button("Apply",() ->
                    log("Info","Applied demo state to "+model.name))
                    .primary().tooltip("Apply demonstration settings").grow(1))
                .add(new UiControls.Button("Reset",() -> {
                    objects.put(selected,new DemoEmitter(model.name));
                    log("Warning","Reset "+model.name+" to demo defaults");
                    rebuildInspector();updateSize();
                }).grow(1)));
    }

    private void rebuildSettings() {
        settingsPanel.clear();
        UiElement sizing=UiElement.column(4,0);
        sizing.add(new UiControls.Label("Interface").heading().height(21));
        sizing.add(sliderProperty("UI Scale",theme.scalePercent,
                UiTheme.SCALE_MIN,UiTheme.SCALE_MAX,1,v -> {
                    theme.scalePercent=UiRect.clamp((int)Math.round(v),
                            UiTheme.SCALE_MIN,UiTheme.SCALE_MAX);
                    if(scaleReadout!=null)scaleReadout.set("Current: "+theme.scalePercent+"%");
                }));
        scaleReadout=new UiControls.Label("Current: "+theme.scalePercent+"%").meta();
        sizing.add(scaleReadout.height(15));
        settingsPanel.add(sizing.height(21+4+24+4+15));

        UiElement appearance=UiElement.column(4,0);
        appearance.add(new UiControls.Label("Appearance").heading().height(21));
        appearance.add(property("Accent",accentControl=new UiControls.Dropdown(
                List.of("Pyro orange","Copper","Cyan","Violet"),0,i -> {
                    theme.accent=switch(i){
                        case 1 -> 0xFFE3996F;
                        case 2 -> 0xFF70D1D2;
                        case 3 -> 0xFFAD90E8;
                        default -> UiTheme.ACCENT;
                    };
                    DemoEmitter current=objects.get(selected);
                    if(current!=null && (current.color&0xFFFFFF)==0x99968F)
                        theme.contextAccent=theme.accent;
                    log("Info","Theme accent changed");
                })));
        appearance.add(new UiControls.Toggle("Microanimations",theme.animations,v -> {
            theme.animations=v;log("Info","Microanimations = "+v);
        }).height(24));
        settingsPanel.add(appearance.height(21+4+24+4+24));

        UiElement actions=UiElement.column(4,0);
        actions.add(new UiControls.Button("Reset workspace",() -> {
            ui.defaultLayout();saveLayout();log("Info","Workspace layout reset");
        }).tooltip("Restore the original docking arrangement").height(24));
        actions.add(new UiControls.Button("Reset appearance",() -> {
            theme.reset();rebuildSettings();updateSize();
            log("Info","Ignition theme restored");
        }).height(24));
        settingsPanel.add(actions.height(24+4+24));
    }

    private void log(String type, String text) {
        if (messages.size() >= 150) messages.remove(0);
        messages.add(new Message(LocalTime.now().format(CLOCK), type, text));
        refreshConsole();
    }
    private void clearMessages() {
        messages.clear();
        refreshConsole();
    }
    private void refreshConsole() {
        if (consoleLines == null) return;
        consoleLines.clear();
        for (Message message : messages) {
            int severityColor = switch (message.severity()) {
                case "Error" -> UiTheme.ERROR;
                case "Warning" -> UiTheme.WARNING;
                default -> UiTheme.SECONDARY;
            };
            consoleLines.add(UiElement.row(6, 2).height(27)
                    .add(new UiControls.Label(message.severity()).color(severityColor).width(61)
                            .tooltip(message.time()))
                    .add(new UiControls.Label(message.text()).color(UiTheme.TEXT).grow(1)
                            .tooltip(message.time()+" · "+message.text())));
        }
        if (initialized) ui.resize(lastLogicalWidth, lastLogicalHeight);
    }
    private void saveLayout() {
        // Automated visual probes must never overwrite the user's real
        // dock layout with synthetic testing moves / temporary window sizes.
        if (Boolean.getBoolean("pyrovfx.ui.capture")) return;
        try { ui.save(layoutPath); }
        catch (IOException error) { log("Error", "Cannot save layout: " + error.getMessage()); }
    }

    /** Contextual hero uses the selected emitter's swatch, not a generic banner. */
    private final class SelectedHeader extends UiElement {
        private final DemoEmitter emitter;
        SelectedHeader(DemoEmitter emitter) { this.emitter = emitter; }
        @Override protected void drawSelf(UiCanvas c, net.kofllee.pyroui.UiContext ctx) {
            int left = UiTheme.mix(UiTheme.ELEVATED,theme.contextAccent,.24f);
            c.rounded(bounds,UiTheme.R_PANEL,UiTheme.ELEVATED);
            c.gradientHorizontal(new UiRect(bounds.x()+2,bounds.y()+2,
                    Math.max(0,bounds.w()-4),Math.max(0,bounds.h()-4)),left,UiTheme.PANEL);
            c.rounded(new UiRect(bounds.x()+10,bounds.y()+11,3,
                    Math.max(0,bounds.h()-22)),1,theme.accent);
            c.textMeta("SELECTED EMITTER",bounds.x()+21,bounds.y()+12,UiTheme.SECONDARY);
            c.textHeadingClipped(emitter.name,bounds.x()+21,bounds.y()+29,
                    Math.max(0,bounds.w()-64),UiTheme.TEXT);
            UiRect swatch = new UiRect(bounds.x()+bounds.w()-28,
                    bounds.y()+bounds.h()-29,14,14);
            c.rounded(swatch,UiTheme.R_CONTROL,emitter.color);
            c.roundedBorder(swatch,UiTheme.R_CONTROL,0x88505050);
        }
    }

    /** Real Minecraft framebuffer and gameplay controls are provided by Pyro UI.
     * This element merely defines the available dock-body content region. */
    private static final class WorldPreview extends UiElement {
        // The viewport is drawn by the shared Pyro UI renderer, not the mod.
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (theme.scalePercent != lastScalePercent) {
            if (scaleSlider != null) scaleSlider.setNumber(theme.scalePercent);
            if (scaleNumber != null && ui.context().focus != scaleNumber)
                scaleNumber.setNumber(theme.scalePercent);
            if (scaleReadout != null) scaleReadout.set("Current: " + theme.scalePercent + "%");
        }
        super.render(graphics, mouseX, mouseY, partialTick);
    }
    @Override public void onClose() {
        saveLayout();
        super.onClose();
    }
    @Override public void removed() {
        saveLayout();
        super.removed();
    }
    // Package scope: allows development-only visual probes to drive the actual
    // Screen without exporting any synthetic input system in the UI library.
    UiDockManager docking() { return ui; }
    String selectedDemoName() { return objects.get(selected).name; }
    UiControls.Interpolation curveMode() { return curvePreview.interpolation(); }
    UiRect accentControlBounds() { return accentControl.bounds(); }
    UiRect scaleNumberBounds() { return scaleNumber.bounds(); }
    UiRect nameControlBounds() { return nameControl.bounds(); }
    UiRect curveControlBounds() { return curveControl.bounds(); }
    UiTheme theme() { return theme; }

    @Override public boolean isPauseScreen() { return false; }
}
