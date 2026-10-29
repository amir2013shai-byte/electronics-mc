#!/usr/bin/env python3
"""Макет экрана GUI-редактора схемы (раскладка слотов, не скриншот).

Соответствует gui/SchematicGui.java: палитра 0..15 (16 деталей, ◀ ▶ на 16/17),
лист 18..44 (3 ряда по 9), кнопки 45..50, счётчик 52. Значения в подсказке —
из live-прогона решателя на Paper 1.21.11 (selftest: 2.306 В / 20.2 мА).
"""
import html

SLOT = 62
PAD = 26
COLS = 9
W = PAD * 2 + COLS * SLOT
H = PAD * 2 + 6 * SLOT + 150

# узлы так же, как их видит решатель: провод = узел, а не падение на нём
VOLT = {(0, 0): 0.0, (0, 1): 0.0, (0, 2): 8.97, (0, 3): 8.97, (0, 4): 2.31,
        (0, 5): 2.31, (0, 6): 0.0, (1, 6): 0.0, (2, 6): 0.0, (2, 5): 0.0,
        (2, 4): 0.0, (2, 3): 0.0, (2, 2): 0.0, (2, 1): 0.0, (1, 0): 0.0, (2, 0): 0.0}
CELLS = {(0, 0): ("wire", "провод"), (0, 1): ("source", "батарея 9 В"),
         (0, 2): ("wire", "провод"), (0, 3): ("resistor", "резистор 330"),
         (0, 4): ("wire", "провод"), (0, 5): ("led", "светодиод"),
         (0, 6): ("wire", "провод"), (1, 6): ("wire", "провод"),
         (2, 6): ("wire", "провод"), (2, 5): ("wire", "провод"),
         (2, 4): ("ground", "шина земли"), (2, 3): ("wire", "провод"),
         (2, 2): ("wire", "провод"), (2, 1): ("wire", "провод"),
         (1, 0): ("wire", "провод"), (2, 0): ("wire", "провод")}
PALETTE = [("source", "Батарея 9 В"), ("resistor", "Резистор 330"), ("led", "LED красный"),
           ("led", "LED зелёный"), ("other", "Диод"), ("cap", "Конд. 1000 мкФ"),
           ("switch", "Ключ"), ("other", "Кнопка"), ("other", "Транзистор NPN"),
           ("other", "Элемент И"), ("other", "Лампа"), ("other", "Амперметр"),
           ("other", "Вольтметр"), ("wire", "Провод"), ("ground", "Земля"),
           ("other", "Потенциометр")]
BUTTONS = ["Пересчитать", "В мире", "Сохранить", "Очистить", "Ластик", "Помощь"]


def color_for(v):
    if abs(v) < 0.05:
        return "#5d6b7a"
    if v > 8:
        return "#e0564b"
    if v > 2:
        return "#e6c229"
    return "#5fbd6b"


def glyph(kind, x, y, color):
    """Минимальный значок детали, чтобы макет читался."""
    if kind == "wire":
        return (f'<rect x="{x+14}" y="{y+28}" width="34" height="6" rx="3" fill="{color}"/>')
    if kind == "resistor":
        return (f'<rect x="{x+12}" y="{y+26}" width="38" height="11" rx="5" fill="{color}"/>'
                f'<line x1="{x+21}" y1="{y+26}" x2="{x+21}" y2="{y+37}" stroke="#22262b"/>'
                f'<line x1="{x+31}" y1="{y+26}" x2="{x+31}" y2="{y+37}" stroke="#22262b"/>')
    if kind == "led":
        return (f'<path d="M{x+20} {y+20} l17 11 l-17 11 z" fill="{color}"/>'
                f'<rect x="{x+37}" y="{y+20}" width="4" height="22" fill="{color}"/>')
    if kind == "source":
        return (f'<rect x="{x+13}" y="{y+17}" width="10" height="28" rx="2" fill="{color}"/>'
                f'<rect x="{x+28}" y="{y+23}" width="5" height="16" fill="{color}"/>'
                f'<rect x="{x+38}" y="{y+17}" width="10" height="28" rx="2" fill="#8d97a3"/>')
    if kind == "ground":
        return (f'<rect x="{x+11}" y="{y+24}" width="40" height="6" fill="{color}"/>'
                f'<rect x="{x+18}" y="{y+34}" width="26" height="5" fill="{color}"/>')
    if kind == "cap":
        return (f'<rect x="{x+17}" y="{y+17}" width="28" height="28" rx="6" fill="{color}"/>'
                f'<line x1="{x+31}" y1="{y+17}" x2="{x+31}" y2="{y+45}" stroke="#10141a"/>')
    if kind == "switch":
        return (f'<path d="M{x+14} {y+38} l26 -15" stroke="{color}" stroke-width="4" fill="none"/>'
                f'<circle cx="{x+14}" cy="{y+38}" r="4" fill="{color}"/>'
                f'<circle cx="{x+40}" cy="{y+38}" r="4" fill="{color}"/>')
    return f'<rect x="{x+16}" y="{y+18}" width="30" height="26" rx="4" fill="{color}"/>'


def slot(x, y, fill="#2b3038", stroke="#3d454f", width=1):
    return (f'<rect x="{x+3}" y="{y+3}" width="{SLOT-6}" height="{SLOT-6}" rx="6" '
            f'fill="{fill}" stroke="{stroke}" stroke-width="{width}"/>')


out = [f'<svg xmlns="http://www.w3.org/2000/svg" width="{W}" height="{H}" '
       f'viewBox="0 0 {W} {H}" font-family="DejaVu Sans, sans-serif">',
       f'<rect width="{W}" height="{H}" fill="#1a1e24"/>',
       f'<text x="{PAD}" y="{PAD-6}" fill="#7fd8a4" font-size="15" font-weight="bold">'
       f'⚡ Схема — редактор · макет раскладки GUI, не скриншот</text>']

y0 = PAD + 10
for i in range(16):
    x = PAD + (i % 8) * SLOT
    y = y0 + (i // 8) * SLOT
    kind, name = PALETTE[i]
    sel = i == 2
    out.append(slot(x, y, "#33402f" if sel else "#2b3038", "#f1c40f" if sel else "#3d454f",
                    2 if sel else 1))
    out.append(glyph(kind, x, y, "#9fe8b8" if sel else "#9aa6b2"))
    out.append(f'<text x="{x+SLOT/2:.0f}" y="{y+SLOT-7}" fill="#aab4c0" font-size="8" '
               f'text-anchor="middle">{html.escape(name[:13])}</text>')
out.append(f'<text x="{PAD}" y="{y0+2*SLOT+13}" fill="#6d7a88" font-size="9">'
           f'слоты 0-15: палитра, 16 деталей на страницу; "‹" и "›" на 16/17; жёлтая рамка — кисть</text>')

top = y0 + 2 * SLOT + 22
for r in range(3):
    for c in range(9):
        x = PAD + c * SLOT
        y = top + r * SLOT
        key = (r, c)
        if key in CELLS:
            kind, name = CELLS[key]
            out.append(slot(x, y))
            out.append(glyph(kind, x, y, color_for(VOLT.get(key, 0.0))))
            out.append(f'<text x="{x+SLOT/2:.0f}" y="{y+SLOT-7}" fill="#cfd6db" font-size="8" '
                       f'text-anchor="middle">{html.escape(name[:13])}</text>')
            out.append(f'<text x="{x+SLOT-8}" y="{y+15}" fill="{color_for(VOLT.get(key, 0.0))}" '
                       f'font-size="8" text-anchor="end">{VOLT.get(key, 0.0):g}В</text>')
        else:
            out.append(f'<rect x="{x+3}" y="{y+3}" width="{SLOT-6}" height="{SLOT-6}" rx="6" '
                       f'fill="#33414f" opacity="0.5"/>')
            out.append(f'<text x="{x+SLOT/2:.0f}" y="{y+SLOT/2+3}" fill="#7c8a99" font-size="8" '
                       f'text-anchor="middle">пусто</text>')
out.append(f'<text x="{PAD}" y="{top+3*SLOT+13}" fill="#6d7a88" font-size="9">'
           f'слоты 18-44: лист 9x3 · ЛКМ ставить · ПКМ поворот · Shift+ПКМ замкнуть ключ '
           f'· цвет = потенциал</text>')

btop = top + 3 * SLOT + 22
for i, name in enumerate(BUTTONS):
    x = PAD + i * SLOT
    out.append(slot(x, btop, "#2f3742", "#4a5563"))
    out.append(f'<text x="{x+SLOT/2:.0f}" y="{btop+34}" fill="#cfe0ee" font-size="8.5" '
               f'text-anchor="middle">{html.escape(name)}</text>')
out.append(f'<text x="{PAD+6*SLOT+10}" y="{btop+28}" fill="#8fd6a8" font-size="10">'
           f'16 из 27 клеток занято</text>')
out.append(f'<text x="{PAD+6*SLOT+10}" y="{btop+42}" fill="#6d7a88" font-size="9">'
           f'островков: 1 · земли: 1 · источник: есть</text>')

bx, by = PAD + 40, btop + SLOT + 16
out.append(f'<rect x="{bx}" y="{by}" width="440" height="76" rx="8" fill="#10141a" '
           f'stroke="#4a5563"/>')
lines = [("⚡ светодиод [5,0]", "#ffffff"),
         ("светодиод · 25 Ом в открытом · порог 2.0 В", "#aab4c0"),
         ("ориентация: E · состояние: горит", "#aab4c0"),
         ("U = 2.306 В  |  I = 20.2 мА  |  P = 46.6 мВт", "#7fd8a4"),
         ("узел a: 4,0,0&4,1,0 -> b: 1,0,2&1,1,2", "#6d7a88"),
         ("7 потенциал узла для проводов и шины — отдельной строкой", "#4d5a68")]
for j, (t, col) in enumerate(lines):
    out.append(f'<text x="{bx+12}" y="{by+17+j*13}" fill="{col}" font-size="10">{html.escape(t)}</text>')
out.append(f'<text x="{bx+12}" y="{by-4}" fill="#e6c229" font-size="9">'
           f'↑ подсказка клетки, которую пишет «Пересчитать»</text>')
out.append('</svg>')
open("docs/editor-mockup.svg", "w", encoding="utf-8").write("\n".join(out))
print("docs/editor-mockup.svg:", len("\n".join(out)), "байт")
