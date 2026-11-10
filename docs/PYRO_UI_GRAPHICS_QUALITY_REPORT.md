# Pyro UI — audit of original icon geometry (2026-11-08)

## Findings

All 22 original Pixelarticons SVGs have a 24×24 viewBox, but their *actual ink* sizes differ. The old atlas did contain 12×12 cells, but only a portion of each cell was occupied. This is why uniformly scaling the cell did not produce consistent visible icon sizes.

Historical measurements of the old 12px cells compared with the current source SVGs (the old raster set is no longer shipped):

| Icon | 12px old ink | 24px native ink |
|---|---:|---:|
| close | 7×7 | 14×14 |
| check | 8×6 | 16×12 |
| checkbox | 10×10 | 20×20 |
| chevron-right | 4×7 | 8×14 |
| settings-cog | 12×12 | 24×24 |
| eye | 12×8 | 24×16 |
| play | 5×9 | 10×18 |
| fire | 9×10 | 18×20 |

Source audit: `tools/audit_pixelarticons.py`. The atlas build is reproducible using `py tools/build_pixelarticons.py` (Pillow + resvg_py, asset-generation only).

## Current implementation

- Only the original 22 Pixelarticons are included, with native 24px pixel geometry, one 572×26 PNG atlas, 1px gutters and generated ink metadata `UiIconBounds.java`.
- `UiIcons` supplies tight UVs and a centered target rectangle. Visible ink keeps its original width-to-height ratio, with maximum extent normalized to the requested icon size and a 1px margin.
- `MinecraftUiRenderer.icon` samples using nearest-neighbor, not bilinear filtering. The separate high-density Pixelarticons and Lucide variations were removed.
- Typography uses only the vanilla Minecraft FontSet; `UiFontService` measures/draws Minecraft's same font with native bold headings. Custom FreeType, SDF, TTF families and prebuilt atlas pathways were deleted.
- The independent `:pyro-ui` library still owns resources, GuiGraphics integration, docking and input. The VFX editor consumes it without a private renderer.

## Validation

`UiTexturesTest` checks every pixel in the generated atlas, asserting the actual alpha bounds agree with generated metadata for all 22 icons. It checks icon destination bounds at multiple requested sizes and invariant aspect ratios. `verifyReleaseJar` verifies original icon assets and excludes legacy extra fonts/icons/shaders.

The live Minecraft test screen at `/pyrovfx typography` (or `.\gradlew.bat runClientUiGallery`) displays all 22 icons with actual ink dimensions, size comparisons, the vanilla font, and a bounding-box visualization toggled with B. Capture outputs are under `run/pyro-ui-verification/`.

### Final verification

- Source/asset audit: **22/22** SVG alpha masks and visible dimensions match the rebuilt atlas.
- `:pyro-ui:test build verifyReleaseJar`: successful; no old custom font assets, SDF shaders, alternate typefaces or Lucide packs in the Pyro UI release.
- Minecraft gallery 1280×720 at GUI Scale 2: screenshot, all interactions and native icon geometry checks passed.
- Minecraft gallery 1600×1000 at GUI Scale 4: screenshot and resource-pack reload passed.
- Existing editor Showcase at 1280×720: **21/21** interaction/visual acceptance stages passed after updating the old 12×12 atlas assertion.
- GameTest dedicated-server run: **104/104** required tests passed.

Client snapshots are generated locally under the ignored `run/pyro-ui-verification/` directory. Screenshots are excluded from Git; only the reproducible probes, tests and source asset audit are versioned.

There is no claim that the new SDF pipeline remains active: **the user requested vanilla fonts**, and the custom font engine was deliberately removed. Fractional zoom may still magnify vanilla Mojangles pixels, as expected.

## Vanilla bold pixel-phase artifact (2026-11)

At wide game resolutions, tab headings such as `Hierarchy` could show a disconnected pixel stripe on the stem of `h`. The font was still vanilla; the problem was the compounded **fractional UI scale × heading scale × Minecraft GUI scale**. Vanilla bold adds a horizontally offset bitmap pass, so fractional physical scaling/placement can change which framebuffer pixels each pass occupies.

The corrected `UiFontService` now snaps the bold-heading origin to the framebuffer pixel grid and quantizes the heading's effective bitmap magnification to a whole number of *physical pixels per source font pixel*. `MinecraftUiRenderer` supplies the current full UI-to-framebuffer scale for consistent measurement and drawing; body and meta text still use their original sizes. The font remains vanilla, and `withBold(true)` is retained.

Regression checks include `UiFontPixelSnapTest`, real 1600×1000 comparison frames (`bold-before-1600` and `bold-snapped-1600`), and a 1920×1080 pixel-locked capture (`bold-pixel-locked-1920`). Pixel-locking can slightly change the apparent heading size when crossing a GUI-scale boundary; preserving strictly pixel-perfect bitmap glyphs and arbitrary continuously variable scale simultaneously is not possible without a different font rendering method.
