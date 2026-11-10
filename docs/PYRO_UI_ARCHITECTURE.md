# Pyro UI — Minecraft-aware UI framework

Platform: Java 21, Minecraft 1.21.1, NeoForge. `:pyro-ui` is an independent Minecraft-aware mod/library with modId `pyro_ui`. Pyro VFX consumes its public UI API, never owns common text, icons, images, docking or rendering.

## Ownership

- `PyroScreen` wraps Minecraft `Screen`, maintains scale, translates input, owns `UiDockManager` and focus.
- `UiElement`, `UiControls`, `UiContext`, `UiTheme` and `UiDockManager` implement the retained tree, input, styling, layout and docking.
- `UiDockLayout` owns the one-workspace rectangular zone geometry, independent
  of the tab and widget trees. `UiDockManager` owns window contents, preview
  transactions, pointer routing and floating windows.
- `MinecraftUiRenderer` draws geometry, images, icons and text via Minecraft `GuiGraphics`, `RenderType` and `MultiBufferSource` with scissor clipping.
- `MinecraftWorldFrame` copies the already-rendered world entirely on GPU and
  owns a Minecraft-managed runtime texture consumed by the docked game panel.
- `UiIcons` owns the **original Pixelarticons only**, under `assets/pyro_ui/textures/gui/icons/pixelarticons.png`.
- `UiFontService` uses **only vanilla Minecraft FontSet** (`Minecraft.getInstance().font`) for font roles, measurement and draw. Headings use native bold styling. No FreeType, custom font atlas, SDF shaders, alternate fonts or Lucide.

Any Minecraft mod can use `new PyroScreen()` and add a `UiElement` composition without Pyro VFX.

## Docking model v2 — one planar workspace

The old recursive `Split(a,b)` tree is removed from the active layout. It
made resizing a pane implicitly resize entire nested subtrees. The replacement
stores each dock zone as a normalized, axis-aligned rectangle in the **same**
work area; each zone holds any number of tabs. Different zones may share only
some edges (T-junctions). Only geometrically coincident and connected boundary
segments can resize as a group; other columns and rows remain unchanged.

Pointer placement has three scopes:

- **Matching shared boundaries:** a one-to-one seam whose endpoints coincide
  offers exactly one centered insertion region, regardless of approach side.
  Both adjacent panels retreat equally when space permits; if one cannot fit
  half the inserted pane, the remaining space is borrowed from the other.
  Different panel widths are fine as long as their shared border matches.
  The full adjoining RIGHT/LEFT or BOTTOM/TOP local drop sectors are replaced
  by that same centered insertion; there is no 120px detection cutoff for
  these sectors and no separate single-panel insertion on a matched seam.
- **Unequal/T-junction boundaries:** docking splits only the approached panel,
  rather than moving every panel connected along the same line. A divider
  *resize* may still move multiple panels where both sides tile the entire
  same connected boundary. Partially matching or malformed boundaries are
  never treated as two-sided dividers.
- **Local:** move near a zone edge or into its body to divide *that one zone*
  left/right/top/bottom, except when its selected side is fully shared with
  one matching neighbor. The center of the body does not merge tabs.
- **Tabs:** hover a specific existing tab to insert the dragged tab immediately
  **after it**, or use the empty header/last 26px to append. The 26px drop target
  is an interaction hotspot, **not permanent blank visual space**: tab drawing
  and clipping have equal 4px left/right insets. Tab buttons themselves are
  valid insertion targets while dragging, without clicking or closing them.
  During merge hover, the new tab itself is previewed in the tab strip, while
  the existing panel content remains visible. Tabs have a 112px preferred
  minimum, scroll horizontally with the mouse wheel over the header when
  overflowing, and do not display navigation arrows. Wheel scrolling never
  switches the active tab until the user clicks a visible tab. Tabs scroll
  smoothly by 28 logical pixels per wheel notch, remain full sized, and may be
  partially clipped at either edge of the available tab strip.
- **Outer:** move to the work area's far left/right/top/bottom edge to reserve a
  new full-height/full-width band across all neighboring zones. Outer-bottom
  takes precedence over the bottom of a local zone only at the outer work edge.

A docked tab immediately becomes a real floating window on drag and follows
the pointer. There is **no sticky docking while moving**. Initial docking
intent now requires roughly **340 ms inside a drop region**, not a motionless
cursor. Fast motion (over about 660 logical px/s) cancels the preview, and
the window must settle for at least 150 ms before it can arm again.
With a valid drop preview the floating window is rendered at 32% opacity,
making the target and panels behind it visible. Horizontal top/bottom splits
start at roughly 34% of the target panel height (subject to minimum sizes);
insertion between two panels sharing a horizontal boundary requests 28% of
the workspace height, borrowing room from either neighbor when necessary.
Slow movement along a line retains docking intent; crossing to another side
changes the proposed placement. Leaving the workspace or releasing the window
resets the intent.

The zones are proportional, not thin pixel-strip targets: the outer 31%
of each panel provides broad left/right/top/bottom local sectors. A 32px reach
around actual shared borders takes priority
over the sector fallback. A 24 logical-pixel strip around the absolute
workspace edge performs full-width/full-height insertion. Local splits can use
more than 45% of the original zone if both
panes still satisfy their actual minimum sizes. General-purpose floating and
docked windows default to a compact 160×36 logical-pixel minimum; consumers
can request larger constraints explicitly using `dockMinimum`.

When the Minecraft viewport has input focus, its key bindings are dispatched
through the user's active vanilla key mappings. Sneak and sprint need a
separate ToggleKeyMapping bridge because they override KeyMapping.isDown().
The six GLFW modifier keys are also reconciled with actual keyboard state
each client tick. Inventory/chat and other in-world GUI Screens are rendered
as nested screens clipped to the Minecraft panel's body; Escape first closes
the nested GUI without closing the Pyro editor. Opening and closing
them does not replace or discard the Pyro UI workspace.

Only after the initial hover and motion settling, `UiDockManager` constructs a speculative
layout with the proposed placement. Other zones visibly animate into position,
new split zones get a **single-color preview** (tab merges use an in-header
ghost tab instead), and the dragged floating
window becomes slightly translucent so the preview remains legible. Mouse
release commits precisely the projected geometry, not an arbitrary new shape.
No popup target icons or redundant drawing adapters are used.

Workspace coordinates are normalized, so a window resize recomputes pixel
rectangles without permanently changing user geometry. Save format
`dock.version=2` stores individual zone rectangles, tab membership/active
tabs and floating bounds. The loader migrates legacy v1 Split-tree saves
directly into zone rectangles. `UiDockLayout.coversWorkspace()` and the
geometry tests reject overlaps and holes. Orphaned zones are merged into
rectangular neighbors where possible; otherwise an empty dock target
is retained rather than creating an impossible L-shaped panel.

Tab strips provide scroll arrows when many tabs cannot fit. The main editor
and generic Minecraft mods use the same public `UiDockManager` implementation.

### Minecraft viewport and gameplay focus

`Window.minecraftViewport()` makes the actual client world a dockable,
resizable panel, not a transparent workspace background. The entire Pyro UI
desktop paints opaque before drawing the live world image **inside the game
panel only**. Gaps, corners, resizing and animation frames reveal no Minecraft
world outside the panel.

The viewport's GPU-local texture is center-cropped to match the panel aspect
ratio without distorting pixels. The underlying GameRenderer still renders its
native full-screen perspective: this implementation does **not** claim a
second independent camera or corrected projection/FOV for arbitrary viewport
dimensions.

Clicking the game viewport activates exclusive game focus. Pyro UI's
client-only Minecraft/KeyMapping mixin bridge handles native gameplay keys
(movement, jumping, attack, item use, continuous mining and hotbar keys)
even though a Minecraft `Screen` remains open. GLFW cursor capture retains
the editor, while the vanilla `MouseHandler.turnPlayer` consumes **raw
physical mouse deltas**, including NeoForge turn-event hooks, scoping, invert
Y and cinematic camera smoothing. No Pyro UI sensitivity reimplementation
remains. `Esc` releases
game focus back to the editor; vanilla inventory/chat screens temporarily
replace Pyro UI and restore the saved workspace on close.

Mouse clicks invoke Minecraft's native `startAttack`, `startUseItem` and
`pickBlock` on the input event itself rather than waiting for the next
20 Hz client tick. Continuous mining and item-use states still use Minecraft
key mappings. The mouse action bridge preserves native NeoForge click hooks.

Hover visuals belong only to the topmost panel beneath the cursor. When
painting panels behind a floating window or a popup, Pyro UI masks their
hover coordinates for that paint pass, preventing occluded buttons from
appearing hovered. It restores the original pointer state afterward. During
captured Minecraft gameplay, Pyro UI masks all panel hover and tooltip tests
by passing offscreen pointer coordinates. The Minecraft capture hit test
and splitter cursor share the same border priority, so a resize gesture
cannot accidentally capture first-person input.

Both rendering and input belong to independent Pyro UI, with no Pyro VFX
classes referenced. GPU frame copies avoid Java-side framebuffer readback.

### Scrolling in small panels

`UiElement` calculates its content extent without subtracting the existing
scroll offset. Layout, the visible scrollbar thumb and wheel clamping all
use the same inner viewport dimension (after padding), preventing the thumb
from reaching the bottom while trailing controls remain inaccessible. Tested
with long Inspector controls, nested scrollers and resizing into a short
floating window.

## Original Pixelarticons size audit

All 22 SVGs declare a **24×24 viewBox**, but the drawn ink footprints differ (close 14×14, check 16×12, chevron-right 8×14, settings-cog 24×24). The previous 12×12 atlas stored whole viewboxes; treating every cell as the same actual visible image caused inconsistent perceived sizing.

`tools/audit_pixelarticons.py` audits actual pixels. `tools/build_pixelarticons.py` renders original SVGs into a native 24px source atlas, each in a 26×26 cell with 1px gutter, and generates `UiIconBounds.java` with measured alpha bounds. The resulting atlas is **572×26**. `UiIcons.region` returns the tight ink UVs. `UiIcons.Sprite.destination` centers the drawing and scales it **uniformly**, preserving width/height ratio and leaving 1px slot margin. `MinecraftUiRenderer.icon` uses GL_NEAREST.

Pixelarticons SVG originals and MIT attribution remain in `third_party/pixelarticons/`. Python Pillow + resvg_py are asset-build tools only, never runtime dependencies. High-resolution textures for unrelated UI elements may still use the normal filtered image renderer.

## Vanilla font and cleanup

`UiFontService` offers `BODY`, `HEADING` and `META` roles and shared logical sizes. `measure()` and `draw()` use the same native `Minecraft.font` and role styling; drawing is `GuiGraphics.drawString`. Pixelated appearance at some fractional zoom factors is inherent to the deliberately chosen vanilla font.

Custom FreeType/SDF/raster font pipelines, dynamic and prebuilt atlases, Inter, Lato, JetBrains Mono, custom font shaders, Lucide and high-density alternate Pixelarticons are **removed**, not dormant fallbacks. The core Minecraft renderer, input and docking functionality remains owned by Pyro UI.

## Developer gallery and validation

Open the Minecraft gallery via `/pyrovfx typography` or `.\gradlew.bat runClientUiGallery`. It displays Mojangles samples, names/actual ink dimensions for all 22 original icons, several icon sizes, +/- text/icon size controls and a `B` key toggle for visible ink bounds. The VFX editor is opened by `/pyrovfx editor` or `.\gradlew.bat runClientUiShowcase`.

Build commands:

```powershell
.\gradlew.bat :pyro-ui:test build verifyReleaseJar --no-daemon
.\gradlew.bat runClientUiGallery -PuiGalleryCapture=true -PuiGalleryAutoExit=true -PuiGuiScale=2
.\gradlew.bat runClientUiShowcase -PuiCapture=true -PuiAutoExit=true
```

`UiTexturesTest` checks actual PNG pixel bounds against the generated Java metadata for every icon, plus centered aspect-preserving destination sizes. The release verifier rejects obsolete font/shader/new-icon assets. Actual Minecraft screenshot and interaction probe results are in `run/pyro-ui-verification/`.

`UiDockLayoutTest` verifies local vs workspace-wide insertion, shared-edge
resizing, independent borders, round-trip planar coverage and pixel gutter
projection. `UiDockManagerTest` verifies exact preview/commit parity, cancel
to floating, unlimited tabs/overflow and v2 serialization. The development
probe captures live local and outer bottom preview states.

No separate process or app is used: Pyro UI runs within Minecraft. Pyro VFX alone owns its editor-specific panels, VFX state, preview and commands.
