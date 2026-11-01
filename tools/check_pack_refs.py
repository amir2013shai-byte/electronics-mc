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
if not os.path.isdir(RP):
    sys.exit("пак не собран: сначала python3 tools/gen_pack.py (ждал "
             + os.path.relpath(RP, ROOT) + ")")

DOC = json.load(open(os.path.join(ROOT, "shared", "components.json"), encoding="utf-8"))
comps = DOC["components"]

# --- пак не имеет права перекрывать ванильные определения предметов и блоков:
#     иначе чужие вещи (пластинка в проигрывателе, зелье, яйцо спавна) начнут
#     одевать наши модели. Разрешены ровно два «чужих» файла: список источников
#     атласа (он дополняет ванильный, а не заменяет) и текстура блока-основы платы.
ALLOWED_MC_DIRS = ("textures/block/quartz_block_",)
ALLOWED_MC_FILES = {"atlases/blocks.json"}
stomp = []
mc_root = os.path.join(RP, "assets", "minecraft")
for sub, _, files in os.walk(mc_root):
    for fn in files:
        rel = os.path.relpath(os.path.join(sub, fn), mc_root).replace(os.sep, "/")
        if rel in ALLOWED_MC_FILES or rel.startswith(ALLOWED_MC_DIRS):
            continue
        stomp.append(rel)
if stomp:
    print("ПОЗОР: пак перекрывает ванильные ресурсы:", stomp[:8])
    sys.exit(1)
atlas = os.path.join(mc_root, "atlases", "blocks.json")
if os.path.isfile(atlas):
    srcs = json.load(open(atlas, encoding="utf-8")).get("sources", [])
    alien = [x for x in srcs if not str(x.get("source", "")).startswith("electronics/")]
    if alien:
        print("ПОЗОР: atlases/blocks.json претендует на чужие папки (заменил бы ванильный список):", alien)
        sys.exit(1)
print("ванильные определения не перекрываем: в assets/minecraft только",
      "атлас-источник (дополняется) и текстуры кварца-основы")

missing_model, missing_tex, missing_tex_ref = [], [], 0
item_defs = 0
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
    # items/<адрес>: и базовый, и все 5 состояний. Плагин ставит item_model =
    # electronics:<id><suffix> на СТЕКЕ (в т.ч. у item_display в мире), и если
    # определения нет, клиент тихо берёт запасную модель = блок-основу.
    for st in range(6):
        ipath = os.path.join(RP, "assets/electronics/items", cid + SUFFIX[st] + ".json")
        if not os.path.isfile(ipath):
            missing_model.append("items/" + cid + SUFFIX[st])
            continue
        d = json.load(open(ipath, encoding="utf-8"))
        # определение может быть и прямым {"model": ...}, и dispatch'ем по
        # custom_model_data: собираем ВСЕ ссылки на модели, куда оно ведёт.
        refs, stack = set(), [d.get("model")]
        while stack:
            x = stack.pop()
            if isinstance(x, dict):
                for k, v in x.items():
                    if k == "model" and isinstance(v, str):
                        refs.add(v)
                    else:
                        stack.append(v)
            elif isinstance(x, list):
                stack.extend(x)
        if not refs:
            missing_model.append(f"items/{cid}{SUFFIX[st]}: ни одной ссылки на модель")
        for tgt in sorted(refs):
            # id модели ns:path -> assets/<ns>/models/<path>.json
            ns, _, tail = str(tgt).partition(":")
            ref = os.path.join(RP, "assets", ns, "models", tail + ".json")
            if not os.path.isfile(ref):
                missing_model.append(f"items/{cid}{SUFFIX[st]} -> {tgt}")
        item_defs += 1

total = sum(v[1] for v in slices.values())
print(f"компонентов: {len(comps)}; индексов состояния модели: {total}")
print(f"проверено ссылок на текстуры: {missing_tex_ref}")
print("отсутствующих моделей:", len(missing_model), missing_model[:5])
print("отсутствующих текстур:", len(missing_tex), missing_tex[:3])
if missing_model or missing_tex:
    sys.exit(1)
# --- целостность самого реестра (этот класс багов уже ловил: states < 6 обрезает
#     запросы _on/_burnt через clampState, и деталь никогда не покажет свечение) ---
INERT = {"BOARD", "CUTTER", "EMPTY", "SCHEMATIC", "STATION", "TOOL", "SUPPLY"}
bad_states = [f"{c['id']}({c.get('kind')}):{c.get('states', 1)}"
              for c in comps if c.get("kind") not in INERT and int(c.get("states", 1)) != 6]
for msg in bad_states:
    print("ПОЗОР:", msg, "— states должен быть 6")
meta_total = (DOC.get("meta") or {}).get("state_model_count")
if meta_total is not None and meta_total != total:
    print(f"ПОЗОР: meta.state_model_count = {meta_total}, а по факту Σ(states*6) = {total}")
if bad_states or (meta_total is not None and meta_total != total):
    sys.exit(1)
print(f"OK: все {len(comps)}×6 ссылок на модели и текстуры разрешаются;",
      f"определений предметов в паке: {item_defs}")

# ------------------------------------------------------------------ датапак
# Ровно тот класс багов, из-за которого 1.21.11 выбросил 38 строк
# "Couldn't parse data file": поля формата, которого ванильный разбор не ждёт.
NS = "electronics"          # тот же префикс, что у Items/Components в плагине
DP = os.path.join(ROOT, "build", "datapack")
RCP = os.path.join(DP, "data", "electronics", "recipe")
bad = []
nrec = 0
if os.path.isdir(RCP):
    for fn in sorted(os.listdir(RCP)):
        if not fn.endswith(".json"):
            continue
        nrec += 1
        r = json.load(open(os.path.join(RCP, fn), encoding="utf-8"))
        typ = r.get("type")
        if typ not in ("minecraft:crafting_shaped", "minecraft:crafting_shapeless"):
            bad.append(f"{fn}: неизвестный type {typ!r}")
            continue
        ing = r.get("ingredients")
        if typ.endswith("shapeless"):
            if not isinstance(ing, list) or not (1 <= len(ing) <= 9):
                bad.append(f"{fn}: ingredients должен быть массивом 1..9")
            seq = ing or []
        else:
            pat = r.get("pattern")
            key = r.get("key") or {}
            if not isinstance(pat, list) or not (1 <= len(pat) <= 3):
                bad.append(f"{fn}: pattern — массив 1..3 строк")
                seq = []
            else:
                seq = []
                for row in pat:
                    if not isinstance(row, str) or len(row) > 3:
                        bad.append(f"{fn}: строка паттерна длиннее 3: {row!r}")
                    for ch in row:
                        if ch != " " and ch not in key:
                            bad.append(f"{fn}: символ {ch!r} паттерна не описан в key")
                seq = list(key.values())
        for x in seq:
            if not (isinstance(x, str) or (isinstance(x, dict) and set(x) <= {"tag"}
                                            and isinstance(x.get("tag"), str))):
                bad.append(f"{fn}: ингредиент должен быть строкой или {{\"tag\": ...}}, "
                           f"а не {type(x).__name__} {x!r}")
        res = r.get("result") or {}
        rid = str(res.get("id", ""))
        if not rid.startswith("minecraft:"):
            bad.append(f"{fn}: результат {rid!r} — реестра {NS}:item не существует, "
                       "крафт не соберётся")
        comp = res.get("components") or {}
        if NS + ":" not in str(comp.get("minecraft:item_model", "")):
            bad.append(f"{fn}: нет minecraft:item_model — деталь не отличить от основы")
        cd = comp.get("minecraft:custom_data") or {}
        if not isinstance(cd.get(f"{NS}:def"), str):
            bad.append(f"{fn}: нет custom_data {NS}:def (строкой) — Items.defOf не узнает")
    print(f"датапак: рецептов {nrec}, ошибок формата {len(bad)}")
    for m in bad[:10]:
        print("  ПОЗОР:", m)
    if bad:
        sys.exit(1)
else:
    print("датапак не собран — рецептов не проверялось")

# pack.mcmeta: 1.21.9+ не читает пак без min_format/max_format
for label, path, lo, hi in (("ресурспак", os.path.join(RP, "pack.mcmeta"), 64, 75),
                            ("датапак", os.path.join(DP, "pack.mcmeta"), 80, 94)):
    if not os.path.isfile(path):
        print("ПОЗОР: нет pack.mcmeta у", label); sys.exit(1)
    p = json.load(open(path, encoding="utf-8"))["pack"]
    for field, want in (("min_format", lo), ("max_format", hi), ("pack_format", lo)):
        if p.get(field) != want:
            print(f"ПОЗОР: {label}: pack.{field} = {p.get(field)!r}, ждём {want!r}")
            sys.exit(1)
    sf = p.get("supported_formats") or {}
    if (sf.get("min_inclusive"), sf.get("max_inclusive")) != (lo, hi):
        print(f"ПОЗОР: {label}: supported_formats = {sf}, ждём {lo}..{hi}")
        sys.exit(1)
    print(f"{label}/pack.mcmeta: {lo}..{hi} (pack_format + supported_formats + "
          "min_format/max_format) — принимают и 1.21.7, и 1.21.11")
