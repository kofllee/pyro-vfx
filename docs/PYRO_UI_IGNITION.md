# Pyro UI — Ignition / Pixel-native Deep Dark

## Design direction

Spotify-like neutral-black elevation meets Minecraft's native pixel language. Black workspace; #121212 editable surfaces, #181818 panels, #242424 raised controls, #303030 hover, #333333 separators; #FFFFFF primary and #B3B3B3 secondary text; #FF6432 Pyro accent with #FFB067 for secondary heat.

This is a compact technical workspace, not a card-based web dashboard. Preview remains the genuine unobstructed game world without an illustration, tint, label or gradient.

## Pixel grid and type

The **default Minecraft Mojangles** font is used for all editor text through `Minecraft.font` / `GuiGraphics.drawString`, with one role-aware size transform for both measurement and drawing. Headings use Mojangles bold. No custom SDF, FreeType, Inter/Lato/JetBrains Mono or alternate font backend is packaged. Generated screenshot comparisons are intentionally excluded from version control.

**Text and icons now use the appropriate native Minecraft pathways separately.** Fonts use Minecraft FontSet; the original Pixelarticons are **PNG sprites** drawn by `MinecraftUiRenderer` through `RenderType` with nearest sampling. The 22 SVGs all have 24×24 viewboxes, but their actual visible pixels vary from 8×14 to 24×24. `UiIconBounds` stores measured dimensions for tight atlas UVs and correctly centered, aspect-preserving target rectangles. The release contains a single tintable 572×26 atlas at `assets/pyro_ui/textures/gui/icons/pixelarticons.png`. There is no old private-use icon font, Lucide pack or extra HD icon family.

Opaque UI rectangles and gradient boundaries are now aligned to **framebuffer pixel coordinates** after applying the editor's layout transform and the active Minecraft GUI Scale; floating animation offsets are included. This eliminates subpixel seams and irregular edges from separately transformed rect primitives. It does *not* make arbitrary fractional zooms mathematically pixel-perfect: for example, 95% may place one glyph pixel across 1 output pixel and another across 2. Achieving strictly identical physical pixel widths would require quantizing the entire font/UI scale to integer factors, in conflict with the requested 1% UI Scale precision.

## Geometry and density

| Role | Rule |
|---|---|
| Rectangle corners | one GUI pixel removed in each corner |
| Icon button | square hit target; icon centered without glyph baseline |
| Toggle | 29×14 rectangular housing, 9px block knob, no round pill |
| Slider | 2px track, 7px square/1px-corner drag handle |
| Number field | Pixelarticons `move` drag grip + editable number |
| Dropdown | genuine pixel `chevron-down` |
| Tree | real pixel chevrons and simple 3px leaf markers |
| Scale | 40–250%, 1% field precision, 5% quick +/- |
| 100% | 0.82× the previous layout baseline |

For spacing, use **3px for tight inline items, 4–5px between controls within one section, 12px between sections**, plus a 9–10px outer panel inset. Always make grouped items feel closer to one another than to adjacent groups. Inspector Emitter/Appearance, Settings Interface/Appearance/Actions and Console title/log list use this hierarchy.

At large scale and narrow windows, text that cannot fit is clipped to its own hit rect and displayed with ellipsis; contextual tooltips preserve long field values. Floating windows clamp to the full viewport (not merely keeping their title bars visible). The top menu folds into a compact layout when the logical workspace is under 650px wide.

## Motion and behavior

Reuse the existing fade/ease timing for feedback without unnecessary layout reflows. Dock indicators appear after deliberate hover; a floating window becomes translucent over its target. Curve Editor switches to lower-density lines while dragging. UI `Screen` is a demonstration editor rather than a live JSON editor, but its docked Minecraft viewport does forward gameplay input.

## Verification

Generated Minecraft capture sets live under the ignored `run/` directory and are not committed. The programmatic probe checks the native 24px source icons, Minecraft FontSet glyph metrics and scale range; unit tests validate spacing hierarchy and floating window bounds. See `docs/PYRO_UI_ARCHITECTURE.md` for module ownership.
