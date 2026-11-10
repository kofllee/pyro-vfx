# Pixelarticons assets used by Pyro UI

Design source: [Pixelarticons](https://github.com/halfmage/pixelarticons), © Gerrit Halfmann, licensed under MIT. The complete license is in `LICENSE` and a copy ships in `META-INF/licenses/pixelarticons-MIT.txt`.

- `source/`: unchanged selected SVG shapes from the upstream 24px icon set.
- `../../pyro-ui/src/main/resources/assets/pyro_ui/textures/gui/icons/pixelarticons.png`: generated tintable 572×26 atlas (22 original 24px icons, 1px gutters).
- `../../pyro-ui/src/main/java/net/kofllee/pyroui/UiIconBounds.java`: generated visible-ink metadata.

The atlas and ink bounds are generated together using `python tools/build_pixelarticons.py` (Pillow + resvg_py, asset-generation only). Verify with `python tools/audit_pixelarticons.py`.

The order is defined by `NAMES` in `tools/build_pixelarticons.py` and `UiIconBounds.NAMES`. Both must match `UiIcons`.

**Rendering contract:** `MinecraftUiRenderer` draws icon sprites as tinted texture quads with nearest filtering. Text uses vanilla Minecraft's font separately. `UiIcons` uses each glyph's actual ink bounds to preserve centered, correct aspect ratios.

For a new icon: add its original SVG, update the source icon list and regenerate the atlas/metadata. Run `:pyro-ui:test` and `verifyReleaseJar` before publishing.
