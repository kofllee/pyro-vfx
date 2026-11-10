"""Audit original Pixelarticons SVG ink bounds against the shipped atlas.
Run: py tools/audit_pixelarticons.py
No downloads. Requires Pillow and resvg_py (asset-build tools only).
"""
from pathlib import Path
import io
from PIL import Image
import resvg_py

ROOT=Path(__file__).resolve().parents[1]
SOURCE=ROOT/"third_party/pixelarticons/source"
ATLAS=ROOT/"pyro-ui/src/main/resources/assets/pyro_ui/textures/gui/icons/pixelarticons.png"
NAMES=[
    "close","check","checkbox","chevron-down","chevron-right","chevron-up",
    "plus","minus","settings-cog","sliders","folder","folder-plus","pencil",
    "refresh","move","eye","play","pause","expand","collapse","menu","fire"
]

def main():
    atlas=Image.open(ATLAS).convert("RGBA")
    assert atlas.size==(26*len(NAMES),26),f"Unexpected native atlas dimensions: {atlas.size}"
    print("new atlas dimensions",atlas.size)
    for i,name in enumerate(NAMES):
        svg=(SOURCE/(name+".svg")).read_text(encoding="utf-8")
        native=Image.open(io.BytesIO(resvg_py.svg_to_bytes(
            svg_string=svg,width=24,height=24))).convert("RGBA")
        cell=atlas.crop((26*i+1,1,26*i+25,25))
        actual=cell.getchannel("A").getbbox()
        source=native.getchannel("A").getbbox()
        assert actual==source,f"{name}: atlas ink {actual} differs from SVG {source}"
        assert cell.getchannel("A").tobytes()==native.getchannel("A").tobytes()
        assert all(
            cell.getpixel((x,y))[:3]==(255,255,255)
            for y in range(24) for x in range(24)
            if cell.getpixel((x,y))[3]>0
        ),name
        print(f"{name:16s} original 24px ink={source!s:18s} PASS")
    print(f"22/22 original Pixelarticons ink bounds and tintable white coverage verified")
if __name__=="__main__":
    main()
