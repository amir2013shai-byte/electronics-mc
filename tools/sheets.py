#!/usr/bin/env python3
"""Постраничные листы текстур: build/preview.png → release/textures/sheet-0N.png.

preview.png (52 строки по 36 px) на 4× не читается целиком, поэтому делим на
4 листа по 13 строк и тянем NEAREST-масштабом: пиксель-арт 32×32 становится
128×128, видны цветовые кольца и «глазки» LED. Запуск: python3 tools/sheets.py
"""
import os
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "build", "preview.png")
OUT = os.path.join(ROOT, "release", "textures")
SCALE, ROWS_PER_SHEET, CELL = 4, 13, 36


def main():
    if not os.path.isfile(SRC):
        raise SystemExit("нет build/preview.png — сначала: python3 tools/preview.py")
    im = Image.open(SRC).convert("RGBA")
    rows = im.height // CELL
    os.makedirs(OUT, exist_ok=True)
    n = 0
    for start in range(0, rows, ROWS_PER_SHEET):
        end = min(start + ROWS_PER_SHEET, rows)
        crop = im.crop((0, start * CELL, im.width, end * CELL))
        crop = crop.resize((crop.width * SCALE, crop.height * SCALE), Image.NEAREST)
        canvas = Image.new("RGBA", (crop.width, crop.height + 34), (18, 20, 22, 255))
        canvas.alpha_composite(crop, (0, 34))
        from PIL import ImageDraw, ImageFont
        font = None
        for cand in ("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
                     "/usr/share/fonts/dejavu/DejaVuSans.ttf",
                     "/System/Library/Fonts/Menlo.ttc"):
            if os.path.isfile(cand):
                font = ImageFont.truetype(cand, 20)
                break
        label = (f"electronics: rows {start + 1}-{end} of {rows}"
                 f"  (columns: off, dim, on, burnt, rev, closed)")
        if font is None:   # без кириллицы в растровом шрифте PIL — метка латиницей
            pass
        d = ImageDraw.Draw(canvas)
        d.text((10, 8), label, fill=(215, 222, 228, 255), font=font)
        n += 1
        path = os.path.join(OUT, f"sheet-{n:02d}.png")
        canvas.save(path)
        print(f"{os.path.relpath(path, ROOT)}  {canvas.width}×{canvas.height}")
    print(f"листов: {n}")


if __name__ == "__main__":
    main()
