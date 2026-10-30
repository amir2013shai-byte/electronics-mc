#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Генератор ресурспака (и датапака рецептов) для плагина Electronics.

Источник истины — shared/components.json: из него берутся id компонентов,
количества состояний и индексы «реестра состояний модели», которые использует
и плагин (assets/.../items/<id>.json + item_model + custom_model_data).

Запуск:  python3 tools/gen_pack.py
Результат: build/resourcepack/**, build/resourcepack.zip,
           build/datapack/**,  build/datapack.zip
"""

import json, os, shutil, zipfile, sys
from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SHARED = json.load(open(os.path.join(ROOT, "shared", "components.json"), encoding="utf-8"))
COMPONENTS = SHARED["components"]
STATE_SUFFIX = ["", "_dim", "_on", "_burnt", "_rev", "_closed"]
BUILD = os.path.join(ROOT, "build")
RP = os.path.join(BUILD, "resourcepack")
DP = os.path.join(BUILD, "datapack")
NS = "electronics"


def out(*p):
    path = os.path.join(RP, *p)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    return path


def hex2rgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def shade(c, k, add=0):
    return tuple(max(0, min(255, int(v * k + add))) for v in c)


def mix(a, b, t):
    return tuple(int(a[i] * (1 - t) + b[i] * t) for i in range(3))


# ---------------------------------------------------------------------------- canvases

def hole_grid(d, ox, oy, step, count, dark=(38, 38, 44, 255), ring=(150, 154, 160, 255)):
    """Ряды «дырочек» макетной платы в масштабе x2."""
    for i in range(count[0]):
        for j in range(count[1]):
            cx = ox + i * step[0]
            cy = oy + j * step[1]
            d.rectangle([cx - 2, cy - 2, cx + 1, cy + 1], fill=ring)
            d.rectangle([cx - 1, cy - 1, cx, cy], fill=dark)


def base_canvas(comp):
    """Рисует вид сверху (16x16, масштаб x2) для компонента."""
    pal = [hex2rgb(c) for c in comp.get("palette", ["#888888", "#666666"])]
    shape = comp.get("shape", "box")
    size = comp.get("size", [12, 6, 12])
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    body = pal[0]
    accent = pal[1] if len(pal) > 1 else shade(body, 0.7)
    detail = pal[2] if len(pal) > 2 else shade(body, 1.35)

    def rect(x0, y0, x1, y1, fill, outline=None):
        d.rectangle([x0 * 2, y0 * 2, x1 * 2 + 1, y1 * 2 + 1], fill=fill, outline=outline)

    def grid_fill(c0, c1):
        for x in range(16):
            for y in range(16):
                img.putpixel((x * 2, y * 2), mix(c0, c1, (x + y) / 30.0))
                img.putpixel((x * 2 + 1, y * 2), mix(c0, c1, (x + y) / 30.0))
                img.putpixel((x * 2, y * 2 + 1), mix(c0, c1, (x + y) / 30.0))
                img.putpixel((x * 2 + 1, y * 2 + 1), mix(c0, c1, (x + y) / 30.0))

    if shape == "breadboard":
        grid_fill(shade(body, 0.95), shade(body, 1.06))
        # красная/синяя шины питания
        rect(0, 1, 15, 1, (196, 62, 58, 255))
        rect(0, 2, 15, 2, shade(body, 0.8))
        rect(0, 13, 15, 13, (58, 86, 170, 255))
        rect(0, 14, 15, 14, shade(body, 0.8))
        hole_grid(d, 10, 14, (10, 10), (6, 1), ring=(168, 172, 178, 255))
        hole_grid(d, 10, 44, (10, 10), (6, 1), ring=(168, 172, 178, 255))
        # центральный канал
        rect(0, 7, 15, 8, shade(body, 0.7))
        d.line([0, 15, 32, 15], fill=(28, 30, 34, 255), width=1)
        hole_grid(d, 10, 26, (10, 0), (6, 1), ring=(150, 154, 160, 255))
    elif shape == "plate":
        rect(0, 0, 15, 15, shade(body, 0.8))
        rect(0, 0, 15, 14, body)
        for i in range(0, 16, 3):
            d.line([i * 2, 0, i * 2, 31], fill=shade(body, 0.86) + (255,), width=1)
            d.line([0, i * 2, 31, i * 2], fill=shade(body, 0.86) + (255,), width=1)
        for i in (2, 13):
            for j in (2, 13):
                rect(i, j, i + 1, j + 1, shade(detail, 0.9))
        for i in range(1, 15):
            d.point([i * 2, 8], fill=shade(accent, 1.0) + (255,))
            d.point([i * 2, 22], fill=shade(accent, 1.0) + (255,))
        rect(5, 6, 10, 9, shade(accent, 0.85))
    elif shape == "wire":
        rect(0, 6, 15, 9, body)
        rect(0, 6, 15, 6, shade(body, 1.3))
        rect(0, 9, 15, 9, shade(body, 0.7))
        rect(0, 6, 1, 9, shade(detail, 1.1))
        rect(14, 6, 15, 9, shade(detail, 1.1))
    elif shape == "coil":
        # каркас с витками: медный «блин» и сердечник по центру
        d.ellipse([10, 10, 54, 54], fill=shade(body, 0.85), outline=shade(body, 0.6))
        d.ellipse([16, 16, 48, 48], fill=body)
        for a in range(0, 360, 24):
            import math as _m
            x0 = 32 + 21 * _m.cos(_m.radians(a)); y0 = 32 + 21 * _m.sin(_m.radians(a))
            x1 = 32 + 13 * _m.cos(_m.radians(a)); y1 = 32 + 13 * _m.sin(_m.radians(a))
            d.line([x0, y0, x1, y1], fill=shade(accent, 1.15) + (255,), width=2)
        d.ellipse([24, 24, 40, 40], fill=shade(detail, 0.9), outline=(20, 22, 26, 255))
    elif shape == "magnet":
        # подковообразный: два полюса, между ними зазор
        d.ellipse([6, 6, 58, 58], fill=body, outline=shade(body, 0.7))
        d.ellipse([18, 18, 46, 46], fill=(0, 0, 0, 0))
        rect(2, 2, 6, 13, (200, 60, 55, 255))
        rect(10, 2, 14, 13, (60, 96, 190, 255))
        rect(2, 2, 14, 3, shade(body, 1.25))
        d.text((4, 11), "N", fill=(255, 255, 255, 255))
        d.text((11, 11), "S", fill=(255, 255, 255, 255))
    elif shape == "battery":
        rect(0, 3, 15, 12, body)
        rect(1, 4, 15, 11, shade(body, 1.15))
        for i in range(3):
            rect(2 + i * 5, 4, 2 + i * 5, 11, shade(body, 0.6))
        rect(14, 5, 15, 10, (230, 230, 235, 255))
        d.text((4, 6), "+", fill=(255, 255, 255, 255))
    elif shape == "battery9":
        rect(1, 2, 14, 13, body)
        rect(2, 3, 13, 12, shade(body, 1.2))
        rect(4, 1, 6, 2, (220, 220, 225, 255))
        rect(9, 1, 11, 2, (150, 150, 155, 255))
        rect(5, 6, 10, 9, accent)
    elif shape == "psu":
        rect(0, 2, 15, 13, body)
        rect(1, 3, 14, 12, shade(body, 0.95))
        rect(2, 5, 8, 10, (20, 26, 32, 255))
        d.text((3, 6), "12", fill=(255, 90, 90, 255))
        d.ellipse([19, 12, 27, 20], fill=(40, 44, 50, 255), outline=shade(accent, 1.1))
        d.line([23, 13, 23, 16], fill=shade(accent, 1.2), width=2)
        rect(12, 4, 14, 5, (230, 230, 235, 255))
        rect(12, 11, 14, 12, (200, 40, 40, 255))
    elif shape == "resistor":
        rect(0, 7, 15, 8, shade(detail, 0.85))
        rect(3, 4, 12, 11, body)
        rect(3, 4, 12, 5, shade(body, 1.25))
        bands = comp.get("bands") or []
        if not bands:
            bands = [accent, shade(accent, 0.7), (200, 180, 120, 255)]
        for i, b in enumerate(bands[:4]):
            c = b if isinstance(b, tuple) else hex2rgb(b)
            rect(4 + i * 2, 4, 4 + i * 2, 11, c)
    elif shape in ("cylinder", "electrolytic", "disc", "led"):
        cx, cy = 16, 16
        r = 12 if shape != "led" else 6
        if shape == "led":
            d.ellipse([cx - r * 2, cy - r * 2, cx + r * 2, cy + r * 2], fill=body)
            d.ellipse([cx - r, cy - r, cx + r * 2, cy + r * 2], fill=shade(body, 1.4))
            rect(6, 12, 9, 19, shade(body, 0.6))
            d.line([12, 30, 12, 22], fill=(200, 200, 210, 255), width=2)
            d.line([20, 30, 20, 22], fill=(200, 200, 210, 255), width=2)
        else:
            d.ellipse([4, 4, 28, 28], fill=body, outline=shade(body, 0.7))
            d.ellipse([8, 6, 24, 14], fill=shade(body, 1.35))
            if shape == "electrolytic":
                rect(4, 15, 8, 17, accent)
                rect(22, 6, 27, 10, shade(body, 0.5))
            if shape == "disc":
                d.text((11, 13), "104", fill=shade(detail, 1.0))
    elif shape == "pot":
        d.ellipse([5, 5, 27, 27], fill=body, outline=shade(body, 0.6))
        d.ellipse([10, 10, 22, 22], fill=shade(accent, 1.1))
        d.line([16, 16, 16, 8], fill=(240, 240, 240, 255), width=3)
        for i in range(3):
            rect(2 + i * 5, 14, 3 + i * 5, 15, (210, 210, 215, 255))
    elif shape == "switch":
        rect(1, 4, 14, 12, shade(body, 0.9))
        rect(2, 5, 13, 11, body)
        rect(4, 6, 7, 10, shade(accent, 1.1))
        rect(10, 6, 12, 10, shade(detail, 0.8))
    elif shape == "switch3":
        rect(1, 4, 14, 12, shade(body, 0.9))
        for i in range(3):
            rect(3 + i * 4, 6, 4 + i * 4, 10, (220, 220, 225, 255))
        rect(7, 2, 9, 14, shade(accent, 1.0))
    elif shape == "button":
        d.ellipse([4, 4, 28, 28], fill=shade(body, 0.8))
        d.ellipse([6, 6, 26, 26], fill=body)
        d.ellipse([9, 9, 23, 23], fill=shade(body, 1.35))
        d.rectangle([4, 26, 28, 29], fill=shade(detail, 0.8))
    elif shape == "to92":
        rect(4, 6, 28, 24, body)
        d.ellipse([4, 6, 28, 20], fill=body)
        d.rectangle([4, 8, 28, 14], fill=shade(body, 1.4))
        for i in range(3):
            rect(7 + i * 7, 24, 9 + i * 7, 29, (225, 225, 230, 255))
    elif shape == "chip":
        rect(1, 4, 15, 11, body)
        rect(2, 5, 14, 10, shade(body, 1.15))
        for i in range(4):
            rect(2 + i * 3, 11, 3 + i * 3, 12, (225, 225, 230, 255))
            rect(2 + i * 3, 3, 3 + i * 3, 4, (225, 225, 230, 255))
        d.ellipse([3, 6, 5, 8], fill=shade(accent, 1.2))
        d.text((7, 5), comp.get("logic", "")[:3], fill=shade(accent, 1.3))
    elif shape == "bulb":
        d.ellipse([5, 3, 27, 25], fill=(245, 245, 250, 170), outline=shade(detail, 0.9))
        d.line([13, 20, 16, 10], fill=(230, 190, 90, 255), width=2)
        d.line([19, 20, 16, 10], fill=(230, 190, 90, 255), width=2)
        rect(11, 25, 21, 29, shade(accent, 0.9))
    elif shape == "motor":
        rect(2, 6, 13, 13, body)
        d.rectangle([2, 6, 13, 8], fill=shade(body, 1.3))
        rect(13, 8, 15, 11, (230, 230, 235, 255))
        rect(0, 8, 2, 11, shade(accent, 0.9))
    elif shape == "meter":
        rect(1, 3, 15, 12, shade(body, 0.85))
        d.pieslice([4, 4, 28, 26], 180, 360, fill=shade(body, 1.2))
        d.line([16, 16, 24, 8], fill=(220, 60, 60, 255), width=2)
        d.ellipse([14, 14, 18, 18], fill=shade(detail, 0.8))
    elif shape == "fuse":
        rect(0, 7, 15, 8, (215, 215, 220, 255))
        rect(3, 4, 12, 11, (250, 240, 200, 220))
        rect(4, 7, 11, 8, shade(accent, 1.0))
    elif shape == "ptc":
        # «таблетка» PTC: полимер с углеродом, два боковых вывода, светлая полоска
        rect(3, 5, 12, 10, body)
        rect(4, 6, 11, 9, shade(body, 1.25))
        rect(0, 7, 2, 8, (220, 224, 230, 255))
        rect(13, 7, 15, 8, (220, 224, 230, 255))
        rect(6, 6, 9, 6, accent)
    elif shape == "varistor":
        # диск MOV: синяя «линза», снизу два вывода
        d.ellipse([4, 3, 28, 25], fill=body, outline=shade(body, 0.7))
        d.ellipse([8, 5, 24, 15], fill=shade(body, 1.35))
        rect(6, 11, 9, 12, accent)
        d.line([10, 25, 10, 31], fill=(220, 224, 230, 255), width=2)
        d.line([22, 25, 22, 31], fill=(220, 224, 230, 255), width=2)
    elif shape == "bridge":
        # KBU: квадратный корпус, светящаяся метка «+», четыре ножки
        rect(2, 2, 13, 13, body)
        rect(3, 3, 12, 12, shade(body, 1.35))
        d.ellipse([5, 5, 10, 10], fill=shade(accent, 0.9))
        d.line([6 * 2, 6 * 2, 8 * 2, 6 * 2], fill=(240, 240, 245, 255), width=2)
        d.line([7 * 2, 5 * 2, 7 * 2, 7 * 2], fill=(240, 240, 245, 255), width=2)
        for i in (1, 5, 9, 13):
            rect(i, 13, i + 1, 15, (225, 228, 235, 255))
    elif shape == "to220":
        # TO-220: тёмный корпус, никелированная площадка с отверстием, три ножки
        rect(1, 5, 12, 11, body)
        rect(2, 6, 11, 10, shade(body, 1.3))
        rect(11, 4, 15, 12, shade(accent, 1.0))
        d.ellipse([24, 12, 28, 16], fill=(40, 42, 48, 255))
        for i in (2, 6, 10):
            rect(i, 11, i + 1, 15, (225, 228, 235, 255))
    elif shape == "transformer":
        # Ш-магнитопровод из пластин + две обмотки (первичка светлее вторички)
        rect(1, 3, 14, 12, shade(body, 0.8))
        for i in (1, 4, 7, 10, 13):
            rect(i, 3, i, 12, shade(body, 1.2))
        rect(3, 5, 6, 10, accent)
        rect(9, 5, 12, 10, shade(accent, 0.75))
        d.line([4 * 2, 5 * 2, 4 * 2, 10 * 2], fill=shade(accent, 1.5) + (255,), width=1)
        d.line([11 * 2, 5 * 2, 11 * 2, 10 * 2], fill=shade(accent, 1.25) + (255,), width=1)
        for i in (1, 4, 11, 14):
            rect(i, 12, i + 1, 15, (225, 228, 235, 255))
    elif shape == "paper":
        rect(0, 0, 15, 15, body)
        rect(1, 1, 14, 14, shade(body, 1.05))
        for i in range(3):
            rect(3, 4 + i * 3, 12, 4 + i * 3, shade(accent, 0.8))
        rect(6, 6, 9, 9, shade(detail, 0.9))
    elif shape == "handheld":
        rect(2, 5, 13, 10, body)
        rect(9, 6, 15, 9, shade(detail, 0.9))
        rect(2, 6, 4, 9, shade(accent, 1.1))
    elif shape == "station":
        # корпус стола: столешница, силиконовый коврик, подставка под паяльник
        rect(0, 2, 15, 4, shade(body, 0.7))
        rect(0, 4, 15, 11, body)
        rect(1, 5, 14, 10, shade(body, 1.15))
        rect(3, 6, 11, 9, shade(accent, 0.72))
        rect(4, 7, 10, 8, shade(accent, 0.9))
        rect(12, 3, 15, 4, shade(detail, 1.1))
        d.line([5 * 2, 7 * 2, 9 * 2, 7 * 2], fill=(245, 245, 250, 255), width=2)
        d.line([9 * 2, 7 * 2, 11 * 2, 6 * 2], fill=(255, 150, 60, 255), width=2)
    elif shape == "tool":
        # паяльник: рукоять, корпус, жало с нагревателем
        rect(1, 6, 5, 9, shade(accent, 0.85))
        rect(5, 6, 10, 9, body)
        rect(10, 7, 13, 8, shade(detail, 1.2))
        rect(13, 7, 15, 8, (255, 158, 62, 255))
        d.line([6 * 2, 7 * 2, 9 * 2, 7 * 2], fill=shade(body, 0.6) + (255,), width=1)
    elif shape == "supply":
        # моток припоя / баночка флюса
        rect(2, 5, 13, 11, body)
        rect(3, 6, 12, 10, shade(body, 1.3))
        for i in range(4, 12, 2):
            d.line([i * 2, 6 * 2, i * 2, 10 * 2], fill=shade(accent, 0.8) + (255,), width=1)
        rect(4, 3, 11, 5, shade(detail, 1.05))
        rect(5, 4, 10, 4, shade(detail, 0.8))
    else:  # box
        rect(1, 3, 14, 12, body)
        rect(2, 4, 13, 8, shade(body, 1.3))
        rect(2, 11, 13, 12, shade(body, 0.7))
        rect(6, 6, 9, 9, accent)
    return img


def apply_state(img, comp, state):
    """Меняет канвас под конкретное визуальное состояние."""
    d = ImageDraw.Draw(img)
    kind = comp["kind"]
    glow = tuple(comp.get("glow_color", [255, 255, 255]))
    if state == "on":
        px = img.load()
        for y in range(32):
            for x in range(32):
                r, g, b, a = px[x, y]
                if a <= 30:
                    continue
                k = 0.55 if kind not in ("LED", "LAMP") else 0.8
                nr = int(r * (1 - k) + (glow[0] * 0.55 + 110) * k)
                ng = int(g * (1 - k) + (glow[1] * 0.55 + 110) * k)
                nb = int(b * (1 - k) + (glow[2] * 0.55 + 110) * k)
                img.putpixel((x, y), (min(255, nr), min(255, ng), min(255, nb), a))
        halo = Image.new("RGBA", img.size, (0, 0, 0, 0))
        hd = ImageDraw.Draw(halo)
        hd.ellipse([4, 4, 28, 28], fill=glow + (70 if kind in ("LED", "LAMP") else 30,))
        img.alpha_composite(halo.filter(ImageFilter.GaussianBlur(2)))
    elif state == "dim":
        px = img.load()
        for y in range(32):
            for x in range(32):
                r, g, b, a = px[x, y]
                if a > 30:
                    img.putpixel((x, y), (r * 3 // 5 + 30, g * 3 // 5 + 20, b * 3 // 5 + 10, a))
        d.ellipse([6, 6, 26, 26], outline=glow + (90,), width=2)
    elif state == "burnt":
        px = img.load()
        for y in range(32):
            for x in range(32):
                r, g, b, a = px[x, y]
                if a > 30:
                    img.putpixel((x, y), (int(r * 0.45 + 18), int(g * 0.4 + 14), int(b * 0.4 + 12), a))
        d.line([6, 4, 20, 16], fill=(20, 18, 18, 230), width=2)
        d.line([24, 6, 12, 26], fill=(30, 26, 24, 200), width=2)
        d.line([26, 22, 8, 28], fill=(16, 14, 14, 190), width=1)
    elif state == "reverse":
        d.line([8, 8, 24, 24], fill=(255, 70, 70, 235), width=4)
        d.line([24, 8, 8, 24], fill=(255, 70, 70, 235), width=4)
        d.rectangle([3, 3, 29, 29], outline=(255, 200, 80, 160), width=2)
    elif state == "closed":
        d.rectangle([3, 3, 29, 29], outline=(90, 255, 130, 200), width=2)
        d.line([10, 16, 22, 16], fill=(90, 255, 130, 230), width=3)
    return img


def side_from_top(top, comp, state):
    """Боковая текстура: полоса из центра канваса + тёмный низ."""
    img = top.crop((0, 12, 32, 28)).resize((32, 32))
    d = ImageDraw.Draw(img)
    pal = [hex2rgb(c) for c in comp.get("palette", ["#888888"])]
    d.rectangle([0, 22, 32, 32], fill=shade(pal[0], 0.55, 4) + (255,))
    if state == "on":
        glow = tuple(comp.get("glow_color", [255, 255, 255]))
        d.rectangle([0, 0, 32, 22], fill=glow + (60,))
    if state == "burnt":
        d.rectangle([0, 0, 32, 32], fill=(30, 25, 22, 70))
    return img


def bottom_texture(comp):
    pal = [hex2rgb(c) for c in comp.get("palette", ["#888888"])]
    img = Image.new("RGBA", (32, 32), shade(pal[0], 0.5, 6) + (255,))
    d = ImageDraw.Draw(img)
    for i in (4, 26):
        for j in (4, 26):
            d.rectangle([i - 1, j - 1, i + 1, j + 1], fill=(60, 60, 64, 255))
    return img


# ---------------------------------------------------------------------------- модели

def model_of(comp):
    w, h, dp = comp.get("size", [12, 6, 12])
    x0 = max(0, (16 - w) / 2.0)
    x1 = min(16, (16 + w) / 2.0)
    z0 = max(0, (16 - dp) / 2.0)
    z1 = min(16, (16 + dp) / 2.0)
    y1 = min(16, h)
    shade = 0.80
    return {
        "credit": "Electronics (generated)",
        "render_type": "minecraft:cutout",
        "elements": [{
            "from": [round(x0, 3), 0, round(z0, 3)],
            "to": [round(x1, 3), round(y1, 3), round(z1, 3)],
            "faces": {
                "down": {"uv": [0, 0, 16, 16], "texture": "#bottom"},
                "up": {"uv": [0, 0, 16, 16], "texture": "#top"},
                "north": {"uv": [0, 4, 16, 20], "texture": "#side", "shade": False},
                "south": {"uv": [0, 4, 16, 20], "texture": "#side"},
                "west": {"uv": [0, 4, 16, 20], "texture": "#side", "shade": False},
                "east": {"uv": [0, 4, 16, 20], "texture": "#side"},
            },
        }],
        "display": {
            "thirdperson_righthand": {"rotation": [40, 0, 0], "translation": [0, 2.5, 0], "scale": [0.5, 0.5, 0.5]},
            "thirdperson_lefthand": {"rotation": [40, 0, 0], "translation": [0, 2.5, 0], "scale": [0.5, 0.5, 0.5]},
            "firstperson_righthand": {"rotation": [30, 200, 0], "translation": [0, 3, 0], "scale": [0.6, 0.6, 0.6]},
            "firstperson_lefthand": {"rotation": [30, 160, 0], "translation": [0, 3, 0], "scale": [0.6, 0.6, 0.6]},
            "gui": {"rotation": [32, 45, 0], "translation": [0, 1, 0], "scale": [0.85, 0.85, 0.85]},
            "ground": {"translation": [0, 3, 0], "scale": [0.5, 0.5, 0.5]},
            "fixed": {"translation": [8, 1.5, 8], "scale": [1.0, 1.0, 1.0]},
        },
    }


# ---------------------------------------------------------------------------- recipes
RECIPES = {
    "bench": {"type": "shapeless", "ing": {"paper": 1, "repeater": 1}, "count": 2},
    # разведка недр, зачистка и кварц на тактовую частоту — только руками,
    # их не паяют (содержимое обязано совпадать с Recipes.DEFS)
    "crystal_16mhz": {"type": "shapeless", "ing": {"quartz": 2, "gold_nugget": 1}},
    "geoprobe": {"shape": ["Q", "I", "S"], "key": {"Q": "quartz", "I": "iron_ingot", "S": "stick"}},
    "wire_stripper": {"shape": ["SI", "CF"], "key": {"S": "shears", "I": "iron_ingot",
                                                    "C": "copper_ingot", "F": "flint"}},
    # станочные заготовки рудной цепочки: в датапак они не попадают (id в
    # meta.soldered), но таблица обязана совпадать с Recipes.DEFS
    "wire_bare": {"type": "shapeless", "ing": {"string": 2}},
    "wire_tinned": {"type": "shapeless", "ing": {"sand": 1}},
    "battery_zinc": {"type": "shapeless", "ing": {"coal": 1, "string": 1}},
    "mcu_atmega328": {"type": "shapeless", "ing": {"quartz": 1, "gold_nugget": 1}},
    "programmer_usb": {"shape": ["IG", "CR"], "key": {"I": "iron_nugget", "G": "gold_nugget",
                                                     "C": "copper_ingot", "R": "repeater"}},
    # металлургия: станки собираются в верстаке, рудный концентрат — тоже
    # (это и есть путь «без шахты»); сплавы и проволоку делает сам стан,
    # их содержимое описано в Metallurgy, а не здесь
    "crucible_station": {"shape": ["B B", "BLB", "IFI"], "key": {"B": "bricks",
                                                 "L": "lava_bucket", "I": "iron_ingot",
                                                 "F": "furnace"}},
    "mill_station": {"shape": ["IRI", "CPC", "IRI"], "key": {"I": "iron_ingot",
                                            "R": "redstone_block", "C": "copper_ingot",
                                            "P": "piston"}},
    "raw_copper_chunk": {"type": "shapeless", "ing": {"copper_ingot": 2, "calcite": 1}},
    "raw_tin_chunk": {"type": "shapeless", "ing": {"iron_nugget": 4, "quartz": 1,
                                                   "calcite": 1}},
    "raw_zinc_chunk": {"type": "shapeless", "ing": {"iron_nugget": 2, "coal": 1,
                                                    "gunpowder": 1, "calcite": 1}},
    "resistor_shunt_0r01": {"type": "shapeless", "ing": {"gold_nugget": 1}},
    "breadboard": {"shape": ["HRH", "RRR", "RSR"], "key": {"H": "hopper", "R": "repeater", "S": "redstone"}},
    "wire_red": {"type": "shapeless", "ing": {"string": 1, "copper_ingot": 1, "redstone": 1}, "count": 4},
    "wire_black": {"type": "shapeless", "ing": {"string": 1, "copper_ingot": 1, "ink_sac": 1}, "count": 4},
    "wire_yellow": {"type": "shapeless", "ing": {"string": 1, "copper_ingot": 1, "yellow_dye": 1}, "count": 4},
    "wire_blue": {"type": "shapeless", "ing": {"string": 1, "copper_ingot": 1, "lapis_lazuli": 1}, "count": 4},
    "battery_aa": {"shape": ["I", "R", "G"], "key": {"I": "iron_nugget", "R": "redstone", "G": "gold_nugget"}},
    "battery_3": {"type": "shapeless", "ing": {"copper_ingot": 2, "honeycomb": 1}},
    "battery_9": {"shape": ["GGG", "CRC", "GGG"], "key": {"G": "gold_nugget", "C": "copper_ingot", "R": "redstone"}},
    "power_supply": {"shape": ["CGC", "RBR", "IRI"], "key": {"C": "copper_ingot", "G": "glass", "B": "copper_bulb",
                                                             "R": "repeater", "I": "iron_ingot"}},
    "solar_cell": {"shape": [" GG", "QLQ", "GIG"], "key": {"G": "tinted_glass", "Q": "quartz", "L": "lapis_block",
                                                            "I": "iron_ingot"}},
    "source_ac": {"shape": ["GCG", "RIR", "G G"], "key": {"G": "gold_nugget", "C": "copper_ingot",
                                                           "R": "redstone", "I": "iron_ingot"}},
    "generator": {"shape": ["ICI", "RPR", "ICI"], "key": {"I": "iron_ingot", "C": "copper_ingot",
                                                           "P": "sticky_piston", "R": "repeater"}},
    "ground_bus": {"shape": ["CCC", "GRG", "CCC"], "key": {"C": "copper_block", "G": "lightning_rod", "R": "redstone"}},
    "diode": {"shape": ["CR"], "key": {"C": "copper_ingot", "R": "redstone"}, "count": 3},
    "led_red": {"shape": ["G", "R", "C"], "key": {"G": "glass", "R": "redstone", "C": "copper_ingot"}, "count": 2},
    "led_green": {"shape": ["G", "V", "C"], "key": {"G": "glass", "V": "lime_dye", "C": "copper_ingot"}, "count": 2},
    "led_blue": {"shape": ["G", "B", "C"], "key": {"G": "glass", "B": "lapis_lazuli", "C": "copper_ingot"}, "count": 2},
    "led_yellow": {"shape": ["G", "Y", "C"], "key": {"G": "glass", "Y": "yellow_dye", "C": "copper_ingot"}, "count": 2},
    "led_white": {"shape": ["G", "Q", "C"], "key": {"G": "glass", "Q": "quartz", "C": "copper_ingot"}, "count": 2},
    "lamp": {"shape": [" G ", "GCG", " R "], "key": {"G": "glass", "C": "copper_ingot", "R": "redstone"}},
    "button": {"shape": ["R", "S"], "key": {"R": "redstone", "S": "stone_pressure_plate"}},
    "switch_spst": {"shape": ["L", "R", "S"], "key": {"L": "lever", "R": "redstone", "S": "stone_button"}},
    "switch_spdt": {"shape": ["LL", "RS", "SS"], "key": {"L": "lever", "R": "redstone", "S": "stone_button"}},
    "transistor_npn": {"shape": ["R R", "GSG"], "key": {"R": "redstone", "G": "gold_nugget", "S": "sand"}},
    "transistor_pnp": {"shape": ["R R", "ISI"], "key": {"R": "redstone", "I": "iron_nugget", "S": "sand"}},
    "gate_and": {"shape": ["RSR", "SCS", "R R"], "key": {"R": "redstone", "S": "sand", "C": "copper_ingot"}},
    "gate_or": {"shape": ["R R", "SCS", "R R"], "key": {"R": "redstone", "S": "sand", "C": "copper_ingot"}},
    "gate_xor": {"shape": ["RSR", "SCS", "R R"], "key": {"R": "redstone", "S": "sand", "C": "gold_nugget"}},
    "gate_not": {"shape": [" R ", "R R", " C "], "key": {"R": "redstone", "C": "copper_ingot"}},
    "comparator": {"shape": ["AG", " C", "GA"], "key": {"A": "amethyst_shard", "G": "glass", "C": "copper_ingot"}},
    "buzzer": {"shape": [" C ", "CGC", " R "], "key": {"C": "copper_ingot", "G": "gold_nugget",
                                                       "R": "redstone_block"}},
    "motor": {"shape": [" C ", "CRC", " I "], "key": {"C": "copper_ingot", "R": "redstone", "I": "iron_ingot"}},
    "fuse": {"shape": ["GC", "CR", "CG"], "key": {"G": "glass", "C": "copper_ingot", "R": "redstone"}},
    # --- защита, выпрямление, стабилизация, трансформация (зеркало Recipes.DEFS) ---
    "fuse_cartridge_1a": {"shape": ["I I", "CGC", "I I"],
                          "key": {"I": "iron_nugget", "C": "copper_ingot", "G": "glass"}},
    "fuse_cartridge_5a": {"shape": ["I I", "CCC", "I I"],
                          "key": {"I": "iron_nugget", "C": "copper_ingot"}},
    "fuse_slow_2a": {"shape": ["I I", "CSC", "I I"],
                     "key": {"I": "iron_nugget", "C": "copper_ingot", "S": "sand"}},
    "ptc_500ma": {"type": "shapeless", "ing": {"clay_ball": 1, "coal": 1, "copper_ingot": 2}},
    "mov_275v": {"shape": [" C ", "CRC", " C "], "key": {"C": "copper_ingot", "R": "redstone"}},
    "bridge_kbu8": {"shape": ["DRD", "R R", "DRD"], "key": {"D": "gold_nugget", "R": "redstone"}},
    "shottky_sbr30": {"type": "shapeless", "ing": {"copper_ingot": 1, "quartz": 1, "redstone": 1}},
    "cap_elec_2200uf": {"shape": ["PCP", "C C", "PCP"], "key": {"P": "paper", "C": "copper_ingot"}},
    "reg_7805": {"shape": ["III", "GSG", "C C"],
                 "key": {"I": "iron_nugget", "G": "gold_nugget", "S": "sand", "C": "copper_ingot"}},
    "reg_7812": {"shape": ["III", "GRG", "C C"],
                 "key": {"I": "iron_nugget", "G": "gold_nugget", "R": "redstone", "C": "copper_ingot"}},
    "reg_lm317": {"shape": ["I I", "GSG", "CRC"],
                  "key": {"I": "iron_nugget", "G": "gold_nugget", "S": "sand", "R": "redstone",
                          "C": "copper_ingot"}},
    "xfmr_230_12": {"shape": ["CIC", "I I", "CIC"], "key": {"C": "copper_ingot", "I": "iron_ingot"}},
    "xfmr_230_9": {"shape": ["CIC", "I I", "C C"], "key": {"C": "copper_ingot", "I": "iron_ingot"}},
    "desolder_braid": {"type": "shapeless", "ing": {"string": 2, "copper_ingot": 1}, "count": 2},
    "voltmeter": {"shape": ["GCG", "IRI", " C "], "key": {"G": "glass", "C": "copper_ingot", "I": "iron_ingot",
                                                           "R": "redstone"}},
    "ammeter": {"shape": ["GCG", "ILI", " C "], "key": {"G": "glass", "C": "copper_ingot", "I": "iron_ingot",
                                                         "L": "lapis_lazuli"}},
    "multimeter": {"shape": ["GIG", "CRC", "W I"], "key": {"G": "glass", "I": "iron_ingot", "C": "copper_ingot",
                                                            "R": "redstone", "W": "clock"}},
    "wire_cutter": {"shape": ["I I", " S ", " S "], "key": {"I": "iron_ingot", "S": "stick"}},
    "led_probe": {"shape": ["G", "R", "S"], "key": {"G": "glass", "R": "redstone", "S": "stick"}},
    "schematic": {"shape": ["PPP", "PRP", "PIP"], "key": {"P": "paper", "R": "redstone", "I": "copper_ingot"}},
    "empty_circuit": {"shape": ["GRG", "RPR", "GRG"], "key": {"G": "gold_nugget", "R": "redstone", "P": "paper"}},
    "potentiometer": {"shape": [" R ", "RCR", " I "], "key": {"R": "resin_clump", "C": "copper_ingot",
                                                               "I": "iron_nugget"}},
    "cap_100nf": {"type": "shapeless", "ing": {"copper_ingot": 1, "paper": 1}, "count": 3},
    "cap_100uf": {"shape": ["P", "C", "P"], "key": {"P": "paper", "C": "copper_ingot"}, "count": 2},
    "cap_1000uf": {"shape": ["PCP"], "key": {"P": "paper", "C": "copper_ingot"}},
    # --- магниты, катушки, реле и чувствительные резисторы: материал = функция
    "magnet": {"shape": ["IRI"], "key": {"I": "iron_ingot", "R": "redstone_block"},
               "note": "сталь намагничивают редстоуном: без этого это просто брусок"},
    "electromagnet": {"shape": [" C ", "CIC", " C "], "key": {"C": "copper_ingot", "I": "iron_ingot"},
                      "note": "медь вокруг сердечника — как в реальной катушке"},
    "inductor_10mh": {"shape": ["S", "I", "S"], "key": {"S": "string", "I": "iron_nugget"},
                      "count": 2, "note": "витки провода на сердечнике"},
    "choke_10h": {"shape": ["CCC", "CIC", " C "], "key": {"C": "copper_ingot", "I": "iron_block"},
                  "note": "большой дроссель: много меди и железный сердечник"},
    "relay_5v": {"shape": ["CR", "RI", "CG"], "key": {"C": "copper_ingot", "R": "redstone",
                                                       "I": "iron_ingot", "G": "gold_nugget"},
                 "note": "катушка + сердечник + золотой контакт"},
    "reed_switch": {"shape": ["G", "I", "G"], "key": {"G": "glass", "I": "iron_nugget"},
                    "note": "герметичная колба с двумя контактами"},
    "photoresistor": {"shape": ["G", "S", "C"], "key": {"G": "glass", "S": "glowstone_dust",
                                                         "C": "copper_ingot"}, "count": 2,
                      "note": "сернистый кадмий под стеклом: темнота = большое R"},
    "thermistor_ntc": {"type": "shapeless", "ing": {"clay_ball": 1, "copper_ingot": 2, "redstone": 1},
                       "count": 2, "note": "керамический диск с выводами"},
    "soldering_station": {"shape": ["P P", "PFP", "ICI"],
                          "key": {"P": "oak_planks", "F": "furnace", "I": "iron_nugget",
                                  "C": "copper_ingot"},
                          "note": "столешница + горелка + медь: паять нужно у тепла"},
    "soldering_iron": {"shape": ["  C", " I ", "S  "],
                       "key": {"C": "copper_ingot", "I": "iron_nugget", "S": "stick"},
                       "note": "медное жало, железный стержень, деревянная рукоять"},
    "solder": {"type": "shapeless", "ing": {"iron_nugget": 2, "flint": 1}, "count": 2,
               "note": "мягкий металл + абразивная присадка"},
    "flux": {"type": "shapeless", "ing": {"glowstone_dust": 1, "honey_bottle": 1}, "count": 2,
             "note": "канифоль со растворителем: без флюса площадка окислится"},
}


def resistor_recipes():
    """Резисторы: медь + «цветной» множитель (код номинала)."""
    colors = {
        "resistor_10r": ("brick", 10),
        "resistor_100r": ("amethyst_shard", 100),
        "resistor_220r": ("redstone", 220),
        "resistor_330r": ("lapis_lazuli", 330),
        "resistor_1k": ("coal", 1000),
        "resistor_4k7": ("yellow_dye", 4700),
        "resistor_10k": ("glowstone_dust", 10000),
        "resistor_100k": ("quartz", 100000),
    }
    for cid, (ing, value) in colors.items():
        RECIPES[cid] = {
            "type": "shapeless",
            "ing": {"copper_ingot": 1, ing: 1},
            "count": max(1, int(1000 // max(1, value ** 0.5) / 10)),
            "note": "R = %s Ом" % value,
        }


def write_recipe(datapack_dir, cid, spec):
    name = cid if cid.isascii() else cid.replace("-", "_")
    path = os.path.join(datapack_dir, "data", NS, "recipe", name + ".json")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    result = {"id": f"{NS}:{cid}", "count": spec.get("count", 1)}
    if spec.get("type") == "shapeless":
        body = {
            "type": "minecraft:crafting_shapeless",
            "category": "misc",
            "ingredients": [{"item": f"minecraft:{k}"} for k, v in spec["ing"].items() for _ in range(v)],
            "result": result,
        }
    else:
        body = {
            "type": "minecraft:crafting_shaped",
            "category": "misc",
            "pattern": spec["shape"],
            "key": {ch: {"item": f"minecraft:{item}"} for ch, item in spec["key"].items()},
            "result": result,
        }
    with open(path, "w", encoding="utf-8") as f:
        json.dump(body, f, indent=1)
    return body


# ---------------------------------------------------------------------------- main

def main():
    if os.path.isdir(BUILD):
        shutil.rmtree(BUILD)
    os.makedirs(RP, exist_ok=True)
    os.makedirs(DP, exist_ok=True)
    resistor_recipes()

    fmt = SHARED["meta"]["resource_pack_format"]
    desc = {"text": "Electronics — учебная электроника (светодиоды, диоды, схемы). Без модов."}
    with open(out("pack.mcmeta"), "w", encoding="utf-8") as f:
        json.dump({"pack": {"pack_format": fmt, "description": desc}}, f, ensure_ascii=False, indent=2)

    def dir_of(*p):
        d = os.path.join(RP, *p)
        os.makedirs(d, exist_ok=True)
        return d

    tex_dir = dir_of("assets", NS, "textures", "item")
    model_dir = dir_of("assets", NS, "models", "item")
    items_dir = dir_of("assets", NS, "items")
    lang_dir = dir_of("assets", NS, "lang")

    ru = {"pack": "Electronics — электроника для уроков"}
    en = {"pack": "Electronics — circuits for lessons"}
    state_models = 0
    base_index = {}
    written = set()

    for comp in COMPONENTS:
        cid = comp["id"]
        ru[f"item.{NS}.{cid}"] = comp.get("ru", cid)
        en[f"item.{NS}.{cid}"] = comp.get("en", comp.get("ru", cid))
        nstates = int(comp.get("states", 1))
        base_index[cid] = (state_models, nstates)
        state_models += nstates * 6

        canvas = base_canvas(comp)
        models = {}
        for st in range(6):
            suffix = STATE_SUFFIX[st]
            name = f"{cid}{suffix}"
            if st >= nstates and suffix:
                # адрес состояния существует (плагин может попросить любое из 6),
                # поэтому делаем алиас на базовую модель, а не «битую» ссылку
                apath = os.path.join(model_dir, f"{name}.json")
                if not os.path.isfile(apath):
                    with open(apath, "w", encoding="utf-8") as f:
                        json.dump({"parent": f"{NS}:item/{cid}"}, f, indent=1)
                models[st] = f"{NS}:item/{name}"
                continue
            state_name = ["off", "dim", "on", "burnt", "rev", "closed"][st]
            top = apply_state(canvas.copy(), comp, state_name)
            side = side_from_top(top, comp, state_name)
            bottom = bottom_texture(comp)
            for tex, img in (("top", top), ("side", side), ("bottom", bottom)):
                p = os.path.join(tex_dir, f"{name}_{tex}.png")
                if p not in written:
                    img.save(p)
                    written.add(p)
            mpath = os.path.join(model_dir, f"{name}.json")
            model = model_of(comp)
            model["textures"] = {
                "top": f"{NS}:item/{name}_top",
                "side": f"{NS}:item/{name}_side",
                "bottom": f"{NS}:item/{name}_bottom",
            }
            if state_name in ("on", "dim"):
                model["gui_light"] = "front"
            with open(mpath, "w", encoding="utf-8") as f:
                json.dump(model, f, indent=1)
            models[st] = f"{NS}:item/{name}"

        # items definition. Для «лампочек/ключах» добавляем dispatch по
        # custom_model_data (индекс = состояние), остальным — базовая модель:
        # состояние в мире всё равно задаёт плагин через item_model.
        dispatch_kinds = {"LED", "SWITCH", "BUTTON", "TOGGLE", "FUSE", "LAMP", "DIODE", "PROBE", "VOLTMETER",
                          "AMMETER", "RELAY", "REED", "PTC", "VARISTOR", "BRIDGE", "REGULATOR", "TRANSFORMER", "MCU"}
        if comp["kind"] in dispatch_kinds and nstates > 1:
            entries = []
            for st in range(nstates):
                entries.append({"threshold": float(st),
                                "model": {"type": "minecraft:model", "model": models.get(st, models[0])}})
            item_def = {"model": {"type": "minecraft:range_dispatch",
                                  "property": "minecraft:custom_model_data",
                                  "index": 0, "entries": entries,
                                  "fallback": {"type": "minecraft:model", "model": models[0]}}}
        else:
            item_def = {"model": {"type": "minecraft:model", "model": models[0]}}
        with open(os.path.join(items_dir, f"{cid}.json"), "w", encoding="utf-8") as f:
            json.dump(item_def, f, indent=1)

    # атлас: обязательно для 1.21.9+
    atlases = dir_of("assets", "minecraft", "atlases")
    with open(os.path.join(atlases, "blocks.json"), "w", encoding="utf-8") as f:
        json.dump({
            "sources": [
                {"type": "directory", "source": f"{NS}/item", "prefix": f"{NS}:item/"}
            ]
        }, f, indent=1)

    # тема блока-основы (гладкий кварцевый полублок = «плата»)
    tb = dir_of("assets", "minecraft", "textures", "block")
    plate = Image.new("RGBA", (16, 16), (58, 108, 74, 255))
    dp = ImageDraw.Draw(plate)
    for i in range(0, 16, 3):
        dp.line([i, 0, i, 16], fill=(48, 92, 62, 255))
    for i in range(0, 16, 3):
        dp.line([0, i, 16, i], fill=(52, 98, 66, 255))
    for i in (2, 8, 13):
        for j in (2, 8, 13):
            dp.point([i, j], fill=(190, 190, 150, 255))
    plate.save(os.path.join(tb, "quartz_block_side.png"))
    Image.new("RGBA", (16, 16), (44, 84, 58, 255)).save(os.path.join(tb, "quartz_block_bottom.png"))
    side = plate.copy()
    d2 = ImageDraw.Draw(side)
    d2.rectangle([0, 12, 16, 16], fill=(40, 44, 48, 255))
    side.save(os.path.join(tb, "quartz_block_top.png"))

    with open(os.path.join(lang_dir, "ru_ru.json"), "w", encoding="utf-8") as f:
        json.dump(ru, f, ensure_ascii=False, indent=1)
    with open(os.path.join(lang_dir, "en_us.json"), "w", encoding="utf-8") as f:
        json.dump(en, f, ensure_ascii=False, indent=1)

    # иконка пака
    icon = Image.new("RGBA", (128, 128), (24, 32, 28, 255))
    di = ImageDraw.Draw(icon)
    di.rounded_rectangle([16, 16, 112, 112], 16, fill=(58, 108, 74, 255), outline=(180, 220, 190, 255), width=3)
    di.ellipse([44, 44, 72, 72], fill=(255, 90, 80, 255))
    di.line([58, 72, 58, 96], fill=(220, 220, 230, 255), width=4)
    di.line([44, 96, 72, 96], fill=(220, 220, 230, 255), width=4)
    di.polygon([(84, 40), (72, 74), (86, 70), (74, 100), (98, 62), (84, 66)], fill=(255, 220, 90, 255))
    icon.save(out("pack.png"))

    # --- датапак с рецептами ---
    os.makedirs(os.path.join(DP, "data", NS, "recipe"), exist_ok=True)
    with open(os.path.join(DP, "pack.mcmeta"), "w", encoding="utf-8") as f:
        json.dump({"pack": {"pack_format": 94, "description": desc}}, f, ensure_ascii=False, indent=2)
    nrecipes = 0
    missing = []
    # meta.soldered — то же правило читает плагин (Recipes.register их не регистрирует)
    soldered = set(((SHARED.get("meta") or {}).get("soldered")) or [])
    skipped_solder = 0
    for comp in COMPONENTS:
        cid = comp["id"]
        if cid in RECIPES and cid in soldered:
            skipped_solder += 1
            continue
        if cid in RECIPES:
            write_recipe(DP, cid, RECIPES[cid])
            nrecipes += 1
        else:
            missing.append(cid)

    # --- zips ---
    def zipdir(src, dst):
        with zipfile.ZipFile(dst, "w", zipfile.ZIP_DEFLATED) as z:
            for root, _, files in os.walk(src):
                for fn in files:
                    full = os.path.join(root, fn)
                    z.write(full, os.path.relpath(full, src))

    zipdir(RP, os.path.join(BUILD, "electronics-resourcepack.zip"))
    zipdir(DP, os.path.join(BUILD, "electronics-recipes-datapack.zip"))

    files = sum(len(fs) for _, _, fs in os.walk(RP))
    print(f"компонентов: {len(COMPONENTS)}; индексов состояния модели: {state_models}")
    print(f"файлов ресурспака: {files}; рецептов в датапаке: {nrecipes} "
          f"(через пайку: {skipped_solder})")
    BY_DESIGN = {"ingot_bronze", "ingot_brass", "slag", "wire_enamelled",
                 "ingot_copper", "ingot_tin", "ingot_zinc"}
    missing = [m for m in missing if m not in BY_DESIGN]
    if missing:
        print("рецепт не задан для:", ", ".join(missing))
    print("zip:", os.path.join(BUILD, "electronics-resourcepack.zip"))


if __name__ == "__main__":
    main()
