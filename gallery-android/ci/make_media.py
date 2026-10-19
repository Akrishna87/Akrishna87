"""Makes the smoke test's photos: camera shots with EXIF camera details, screenshots and a panorama.

Usage: make_media.py <output dir>
"""
import colorsys
import os
import sys

from PIL import Image, ImageDraw

out = sys.argv[1]
for sub in ("camera", "screenshots", "panoramas"):
    os.makedirs(os.path.join(out, sub), exist_ok=True)


def picture(size, hue):
    """A sky-to-ground gradient with a sun and hills, so each photo looks different."""
    w, h = size
    img = Image.new("RGB", size)
    draw = ImageDraw.Draw(img)
    for y in range(h):
        r, g, b = colorsys.hsv_to_rgb((hue + y / h * 0.15) % 1, 0.55, 1 - y / h * 0.45)
        draw.line([(0, y), (w, y)], fill=(int(r * 255), int(g * 255), int(b * 255)))
    draw.ellipse([w * 0.65, h * 0.15, w * 0.65 + h * 0.2, h * 0.35], fill=(255, 236, 170))
    draw.polygon([(0, h), (w * 0.35, h * 0.5), (w * 0.7, h), ], fill=(40, 70, 60))
    draw.polygon([(w * 0.4, h), (w * 0.75, h * 0.6), (w, h * 0.8), (w, h)], fill=(30, 55, 50))
    return img


def exif():
    e = Image.Exif()
    e[0x010F] = "Google"       # Make
    e[0x0110] = "Pixel Smoke"  # Model
    sub = e.get_ifd(0x8769)    # Exif IFD
    sub[0x829D] = 1.8          # FNumber
    sub[0x8827] = 64           # ISO
    sub[0x829A] = 1 / 120      # ExposureTime
    sub[0x920A] = 6.9          # FocalLength
    return e


for i in range(8):
    picture((1600, 1200) if i % 3 else (1200, 1600), i / 8).save(
        os.path.join(out, "camera", f"PXL_smoke_{i + 1:02d}.jpg"), quality=88, exif=exif())
for i in range(2):
    picture((1080, 2400), 0.55 + i / 10).save(os.path.join(out, "screenshots", f"Screenshot_smoke_{i + 1}.png"))
picture((4200, 1200), 0.08).save(os.path.join(out, "panoramas", "PANO_smoke.jpg"), quality=85)
print("made test photos in", out)
