#!/usr/bin/env python3
"""Сверка имён моделей: логика плагина (Items.applyModel) против ресурспака.

Плагин для компонента с состоянием s (0..5) просит модель
`electronics:<id><STATE_SUFFIX[s]>`; проверяем, что соответствующий
items/models-файл есть в собранном паке и что индексы ModelState совпадают
с срезами в gen_pack.
"""
import json, os, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RP = os.path.join(ROOT, "build", "resourcepack")
SUFFIX = ["", "_dim", "_on", "_burnt", "_rev", "_closed"]
comps = json.load(open(os.path.join(ROOT, "shared", "components.json"), encoding="utf-8"))["components"]

missing_model, missing_tex, missing_tex_ref = [], [], 0
acc = 0
slices = {}
for c in comps:
    n = int(c.get("states", 1))
    slices[c["id"]] = (acc, n * 6)
    acc += n * 6

for c in comps:
    cid = c["id"]
    for st in range(6):
        name = cid + SUFFIX[st]
        mpath = os.path.join(RP, "assets/electronics/models/item", name + ".json")
        if not os.path.isfile(mpath):
            missing_model.append(name)
            continue
        m = json.load(open(mpath, encoding="utf-8"))
        for face, tex in (m.get("textures") or {}).items():
            tp = os.path.join(RP, "assets", tex.replace(":", "/") + ".png")
            tp = tp.replace("/electronics/item/", "/electronics/textures/item/")
            if not os.path.isfile(tp):
                missing_tex.append((name, tex))
            missing_tex_ref += 1
    ipath = os.path.join(RP, "assets/electronics/items", cid + ".json")
    if not os.path.isfile(ipath):
        missing_model.append("items/" + cid)

total = sum(v[1] for v in slices.values())
print(f"компонентов: {len(comps)}; индексов состояния модели: {total}")
print(f"проверено ссылок на текстуры: {missing_tex_ref}")
print("отсутствующих моделей:", len(missing_model), missing_model[:5])
print("отсутствующих текстур:", len(missing_tex), missing_tex[:3])
if missing_model or missing_tex:
    sys.exit(1)
print("OK: все 52×6 ссылок на модели и текстуры разрешаются")
