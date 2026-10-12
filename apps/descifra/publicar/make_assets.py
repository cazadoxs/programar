"""Draws the Descifra store icon (512x512) and feature graphic (1024x500).

Same geometry as app/src/main/res/drawable/ic_launcher_foreground.xml, so the Play listing
icon matches the installed one. Run: python3 make_assets.py
"""
import os
from PIL import Image, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
FONTS = os.path.join(HERE, "..", "app", "src", "main", "res", "font")

NAVY = (27, 42, 65)
NAVY2 = (34, 52, 79)
PAPER = (244, 239, 230)
FOLD = (217, 207, 189)
STAMP = (184, 50, 31)
GREY = (138, 148, 168)

S = 8  # supersampling


def draw_mark(d, ox, oy, scale):
    """Draws the 108-unit adaptive-icon foreground at (ox, oy) with the given scale."""
    def P(x, y):
        return (ox + x * scale, oy + y * scale)

    d.polygon([P(32, 26), P(64, 26), P(78, 40), P(78, 80), P(32, 80)], fill=PAPER)
    d.polygon([P(64, 26), P(78, 40), P(64, 40)], fill=FOLD)
    # ciphered squiggle
    import math
    pts = []
    for i in range(0, 61):
        x = 40 + i * 0.5
        pts.append(P(x, 46 + math.sin(i / 60 * 3 * 2 * math.pi) * 2.2))
    d.line(pts, fill=GREY, width=int(2.4 * scale), joint="curve")
    d.line([P(40, 56), P(70, 56)], fill=NAVY, width=int(2.6 * scale))
    d.line([P(40, 64), P(62, 64)], fill=NAVY, width=int(2.6 * scale))
    cx, cy = P(68, 70)
    r = 13 * scale
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=STAMP)
    r2 = 10 * scale
    d.ellipse([cx - r2, cy - r2, cx + r2, cy + r2], outline=PAPER, width=max(1, int(1.6 * scale)))
    d.line([P(62.5, 70.5), P(66.5, 74.5), P(74, 66)], fill=PAPER, width=int(3 * scale), joint="curve")


def icon(size=512):
    big = size * S
    img = Image.new("RGB", (big, big), NAVY)
    d = ImageDraw.Draw(img)
    d.polygon([(0, big * 78 / 108), (big, big * 50 / 108), (big, big), (0, big)], fill=NAVY2)
    draw_mark(d, 0, 0, big / 108)
    return img.resize((size, size), Image.LANCZOS)


def feature(w=1024, h=500):
    big_w, big_h = w * 2, h * 2
    img = Image.new("RGB", (big_w, big_h), PAPER)
    d = ImageDraw.Draw(img)
    # ruled-paper lines
    for y in range(80, big_h, 56):
        d.line([(0, y), (big_w, y)], fill=(231, 223, 208), width=2)
    # left: mark on navy tile
    tile = 360 * 2
    ox, oy = 90 * 2, (h * 2 - tile) // 2
    d.rounded_rectangle([ox, oy, ox + tile, oy + tile], radius=80, fill=NAVY)
    draw_mark(d, ox, oy, tile / 108)
    try:
        serif = ImageFont.truetype(os.path.join(FONTS, "fraunces.ttf"), 150)
        serif.set_variation_by_axes([72, 650, 0, 0])
        sans = ImageFont.truetype(os.path.join(FONTS, "atkinson_bold.ttf"), 52)
    except Exception:
        serif = sans = ImageFont.load_default()
    tx = ox + tile + 90
    d.text((tx, 250), "Descifra", font=serif, fill=NAVY)
    d.text((tx, 450), "Tus cartas oficiales,", font=sans, fill=NAVY)
    d.text((tx, 515), "en cristiano.", font=sans, fill=STAMP)
    # stamp
    st = ImageDraw.Draw(img)
    f2 = ImageFont.truetype(os.path.join(FONTS, "atkinson_bold.ttf"), 38)
    label = "ESCANEA · ENTIENDE · A TIEMPO"
    wlab = int(st.textlength(label, font=f2))
    st.rounded_rectangle([tx, 620, tx + wlab + 48, 700], radius=10, outline=STAMP, width=5)
    st.text((tx + 24, 635), label, font=f2, fill=STAMP)
    return img.resize((w, h), Image.LANCZOS)


if __name__ == "__main__":
    out = os.path.join(HERE, "assets")
    os.makedirs(out, exist_ok=True)
    icon(512).save(os.path.join(out, "icono-512.png"))
    icon(1024).save(os.path.join(out, "icono-1024.png"))
    feature().save(os.path.join(out, "grafico-funciones-1024x500.png"))
    print("ok")
