#!/usr/bin/env python3
"""Genera landing/assets/og-image.png (1200x630) a partir del héroe de la web.

Lee la versión de api/version.json para pintar el badge, rasteriza el
murciélago del SVG inline de index.html con cairosvg y compone todo con PIL.
Uso:  python3 tools/make-og-image.py   (requiere: pip install pillow cairosvg)
"""
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "assets"
FONT = Path("/Users/andyechc/PersonalProjects/batmonitor/android/app/src/main/res/font/mplus1p_extrabold.ttf")

W, H = 1200, 630
BG = (8, 8, 8)
WHITE = (255, 255, 255)
GREEN = (46, 230, 166)
GRAY = (142, 142, 147)


def main() -> None:
    from PIL import Image, ImageDraw, ImageFont

    version = json.loads((ROOT / "api" / "version.json").read_text())["versionName"]

    html = (ROOT / "index.html").read_text()
    m = re.search(r'<symbol id="bat-mark" viewBox="0 0 1024 1024">(.*?)</symbol>', html, re.S)
    if not m:
        sys.exit("no se encontró el símbolo #bat-mark en index.html")
    bat_svg = (
        '<svg xmlns="http://www.w3.org/2000/svg" width="1024" height="1024" viewBox="0 0 1024 1024">'
        + m.group(1)
        + "</svg>"
    )
    bat_path = Path("/tmp/bat-og.svg")
    bat_path.write_text(bat_svg)
    bat_png = Path("/tmp/bat-og.png")
    import cairosvg

    cairosvg.svg2png(
        url=str(bat_path), write_to=str(bat_png), output_width=420, output_height=420
    )
    try:
        from PIL import Image as _Image

        bat = _Image.open(bat_png).convert("RGBA")
    finally:
        bat_path.unlink(missing_ok=True)
        bat_png.unlink(missing_ok=True)

    img = Image.new("RGB", (W, H), BG)
    img.paste(bat, (80, 105), bat)

    d = ImageDraw.Draw(img)
    title_font = ImageFont.truetype(str(FONT), 104)
    sub_font = ImageFont.truetype(str(FONT), 42)
    pill_font = ImageFont.truetype(str(FONT), 36)

    tx = 580
    d.text((tx, 175), "BatMonitor", font=title_font, fill=WHITE)
    d.text((tx, 305), "Tu BMS por Bluetooth.", font=sub_font, fill=GRAY)
    d.text((tx, 360), "Sin cuenta.", font=sub_font, fill=GRAY)

    label = f"v{version}"
    lb = d.textbbox((0, 0), label, font=pill_font)
    tw, th = lb[2] - lb[0], lb[3] - lb[1]
    px, py, pad_x, pad_y = tx, 445, 26, 18
    d.rounded_rectangle(
        [px, py, px + tw + pad_x * 2, py + th + pad_y * 2],
        radius=30,
        outline=GREEN,
        width=3,
    )
    d.text((px + pad_x, py + pad_y - 2), label, font=pill_font, fill=GREEN)

    out = ASSETS / "og-image.png"
    img.save(out)
    print(f"OK {out} {img.size}")


if __name__ == "__main__":
    main()
