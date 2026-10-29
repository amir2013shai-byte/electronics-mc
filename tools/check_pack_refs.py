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
DOC = json.load(open(os.path.join(ROOT, "shared", "components.json"), encoding="utf-8"))
comps = DOC["components"]

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
# --- целостность самого реестра (этот класс багов уже ловил: states < 6 обрезает
#     запросы _on/_burnt через clampState, и деталь никогда не покажет свечение) ---
INERT = {"BOARD", "CUTTER", "EMPTY", "SCHEMATIC"}
bad_states = [f"{c['id']}({c.get('kind')}):{c.get('states', 1)}"
              for c in comps if c.get("kind") not in INERT and int(c.get("states", 1)) != 6]
for msg in bad_states:
    print("ПОЗОР:", msg, "— states должен быть 6")
meta_total = (DOC.get("meta") or {}).get("state_model_count")
if meta_total is not None and meta_total != total:
    print(f"ПОЗОР: meta.state_model_count = {meta_total}, а по факту Σ(states*6) = {total}")
if bad_states or (meta_total is not None and meta_total != total):
    sys.exit(1)
print(f"OK: все {len(comps)}×6 ссылок на модели и текстуры разрешаются")
