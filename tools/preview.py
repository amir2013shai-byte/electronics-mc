#!/usr/bin/env python3
"""Монтаж превью ресурспака: иконки всех компонентов во всех состояниях."""
import os, json
from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RP = os.path.join(ROOT, "build", "resourcepack", "assets", "electronics", "textures", "item")
SUFFIX = ["", "_dim", "_on", "_burnt", "_rev", "_closed"]
comps = json.load(open(os.path.join(ROOT, "shared", "components.json"), encoding="utf-8"))["components"]
cell, pad = 32, 4
cols = 6
rows = len(comps)
W = cols * (cell + pad) + pad + 150
H = rows * (cell + pad) + pad
img = Image.new("RGBA", (W, H), (28, 32, 34, 255))
d = ImageDraw.Draw(img)
for r, c in enumerate(comps):
    d.text((6, r * (cell + pad) + pad + 8), c["id"][:24], fill=(220, 226, 230, 255))
    n = int(c.get("states", 1))
    for col in range(cols):
        suf = SUFFIX[col] if col < n else ""
        name = f"{c['id']}{suf}_top.png"
        p = os.path.join(RP, name)
        x = pad + 150 + col * (cell + pad)
        y = r * (cell + pad) + pad
        d.rectangle([x, y, x + cell, y + cell], fill=(45, 50, 54, 255))
        if os.path.isfile(p):
            t = Image.open(p).convert("RGBA")
            side_p = os.path.join(RP, f"{c['id']}{suf}_side.png")
            out = Image.new("RGBA", (cell, cell), (0, 0, 0, 0))
            out.paste(t.resize((cell, cell // 2), Image.NEAREST), (0, 0), t.resize((cell, cell // 2), Image.NEAREST))
            if os.path.isfile(side_p):
                st = Image.open(side_p).convert("RGBA")
                out.paste(st.resize((cell, cell // 2), Image.NEAREST), (0, cell // 2),
                          st.resize((cell, cell // 2), Image.NEAREST))
            img.alpha_composite(out, (x, y))
img.save(os.path.join(ROOT, "build", "preview.png"))
print("saved", os.path.join(ROOT, "build", "preview.png"), img.size)
