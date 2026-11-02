#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Обслуживание `users_textures/`: теги, проверка имён, большой README, примеры.

Зачем: генератор пака (`tools/gen_pack.py`) умеет брать текстуры из
`users_textures/`, а человек должен знать, КАК назвать файл, чтобы он подхватился.
Правило имён живёт в одном месте — `gen_pack.user_candidates()` — и этот скрипт
берёт его оттуда же. Поэтому список тегов и проверка не могут разъехаться с тем,
что реально делает сборка пака: и то, и другое читается из одного кода.

Запуск:
    python3 tools/user_tags.py list              # все теги, TSV (для grep/Excel)
    python3 tools/user_tags.py check             # что из положенного применится
    python3 tools/user_tags.py doc               # users_textures/README.md
    python3 tools/user_tags.py example [id]      # заготовка-пример
    python3 tools/user_tags.py count             # только числа (для сверки с доками)
"""

import difflib
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import gen_pack as G  # noqa: E402  — один источник истины по именам тегов и реестру

FACES = ("top", "side", "bottom")
HOST_DIR = os.path.join(G.ROOT, "users_textures")
HOST_FILES = {"host_top.png": "верх «платы»-основы",
              "host_side.png": "боковина «платы»",
              "host_bottom.png": "низ «платы»"}
# что делает apply_state()/side_from_top() с состоянием — коротко, подробности
# в tools/gen_pack.py
STATE_NOTE = {
    "off": "базовый вид: канва как есть",
    "dim": "полусвет: подсветка слабее, чем у on",
    "on": "активное: подмешивается glow_color (у LED и ламп сильнее) и гало",
    "burnt": "сгоревшее: копоть + тёмные трещины-штрихи",
    "rev": "обратная полярность/реверс: красный крест",
    "closed": "замкнуто: зелёная перемычка",
}


def users_dir():
    """Каталог, из которого генератор читает теги (можно переопределить)."""
    return os.environ.get("ELECTRONICS_USER_TEXTURES", HOST_DIR)


def comps():
    return G.COMPONENTS


def nstates_of(comp):
    return int(comp.get("states", 1))


def all_tags():
    """(cid, state, face, pack-файл, [файлы-кандидаты]) для всех реальных состояний."""
    for comp in comps():
        cid = comp["id"]
        for st in range(nstates_of(comp)):
            state = G.STATE_NAMES[st]
            suffix = G.STATE_SUFFIX[st]
            for face in FACES:
                yield (cid, state, face, f"{cid}{suffix}_{face}.png",
                       G.user_candidates(cid, state, face))


def known_tags():
    """Все имена файлов, которые генератор вообще способен подхватить."""
    known = set(HOST_FILES)
    for _cid, _state, _face, _pack, cands in all_tags():
        known.update(cands)
    for comp in comps():
        known.update(G.user_candidates(comp["id"], None, "top"))
    return known


def by_component():
    """cid -> {ru, en, kind, palette, size, states, host, ing}."""
    out = {}
    for comp in comps():
        cid = comp["id"]
        rec = G.RECIPES.get(cid)
        ing = {}
        if rec:
            if "key" in rec:
                for ch, mat in rec["key"].items():
                    n = sum(row.count(ch) for row in rec.get("shape", []))
                    ing[mat] = ing.get(mat, 0) + max(1, n)
            else:
                ing = dict(rec.get("ing", {}))
        out[cid] = {
            "ru": comp.get("ru", cid),
            "en": comp.get("en", comp.get("ru", cid)),
            "kind": comp.get("kind", "?"),
            "shape": comp.get("shape", "?"),
            "size": comp.get("size", []),
            "palette": comp.get("palette", []),
            "states": nstates_of(comp),
            "soldered": cid in G.SHARED["meta"]["soldered"],
            "ing": ing,
        }
    return out


# --------------------------------------------------------------------------- команды

def cmd_list():
    print("# tag\tcomponent\tstate\tface\tpack\tможно_положить")
    for cid, state, face, pack, cands in all_tags():
        print(f"{cid}_{state}_{face}\t{cid}\t{state}\t{face}\t{pack}\t{' | '.join(cands)}")


def cmd_count():
    tags = list(all_tags())
    print(f"компонентов: {len(comps())}; состояний (сумма): {len(set((c, s) for c, s, _f, _p, _c in tags))}; "
          f"тегов текстур: {len(tags)}; пайка: {len(G.SHARED['meta']['soldered'])}")


def cmd_check():
    d = users_dir()
    if not os.path.isdir(d):
        print(f"папки {d} нет — нечего проверять")
        return 0
    files = sorted(f for f in os.listdir(d)
                   if f.lower().endswith((".png", ".jpg", ".jpeg")) and os.path.isfile(os.path.join(d, f)))
    if not files:
        print(f"в {d}/ пока нет ни одного PNG — пак соберётся процедурный")
        return 0
    known = known_tags()
    ids = {c["id"] for c in comps()} | {"host"}
    bad = 0
    print(f"файлов в {d}/: {len(files)}")
    for f in files:
        path = os.path.join(d, f)
        full = os.path.realpath(path)
        if f not in known:
            stem = f.rsplit(".", 1)[0]
            guess = difflib.get_close_matches(stem, sorted(G.user_tag_names()), n=1)
            hint = f" — ближайший тег «{guess[0]}.png»" if guess else ""
            if not guess and stem.split("_")[0] not in ids:
                hint = f" — компонента «{stem.split('_')[0]}» в реестре нет"
            print(f"  ✗ {f:38s} имя не является тегом{hint}")
            bad += 1
            continue
        try:
            from PIL import Image
            with Image.open(path) as im:
                w, h = im.size
                mode = im.mode
        except Exception as ex:                        # noqa: BLE001
            print(f"  ✗ {f:38s} не читается: {ex}")
            bad += 1
            continue
        note = "" if (w, h) in ((32, 32), (16, 16)) else f"  (растянем {w}×{h} → 32×32 NEAREST)"
        low = ""
        if mode not in ("RGBA", "LA", "P"):
            low = "  (!) без альфы — cutout-края не обрезать"
        print(f"  ✓ {f:38s} {w}x{h} {mode}{note}{low}")
        if (w, h) == (32, 32):
            from PIL import Image
            with Image.open(path) as im:
                im = im.convert("RGBA")
                semi = sum(1 for px in im.getdata() if 0 < px[3] < 128)
            if semi:
                print(f"      ↳ {semi} пикс(елей) с 0<α<128: игра их не нарисует "
                      f"(minecraft:cutout отбрасывает α<128)")
    if bad:
        print(f"пропущено файлов: {bad} — генератор их не увидит")
    return 1 if bad else 0


def cmd_example(argv):
    cid = argv[0] if argv else "led_red"
    comp = next((c for c in comps() if c["id"] == cid), None)
    if comp is None:
        print(f"нет компонента «{cid}» — см. python3 tools/user_tags.py list | cut -f2 | sort -u")
        return 1
    from PIL import Image, ImageDraw
    d = os.path.join(users_dir(), "_examples")
    os.makedirs(d, exist_ok=True)
    pal = [G.hex2rgb(x) + (255,) for x in comp.get("palette", ["#888888", "#dddddd"])]
    img = Image.new("RGBA", (16, 16), pal[0])
    dr = ImageDraw.Draw(img)
    dr.rectangle([1, 1, 14, 14], outline=pal[-1], width=1)
    dr.ellipse([4, 4, 11, 11], fill=pal[min(1, len(pal) - 1)])
    for x in (0, 15):
        dr.line([x, 7, x, 8], fill=(60, 60, 64, 255))
    path = os.path.join(d, f"{cid}_top.png")
    img.save(path)
    print(f"заготовка: {path}")
    rel = os.path.relpath(users_dir(), G.ROOT)
    print(f"чтобы она заработала: cp {d}/{cid}_top.png {rel}/{cid}_top.png")
    print("и дальше: bash tools/user_pack.sh")
    return 0


# ------------------------------------------------------------------------- большой MD

def cmd_doc(to_stdout=False):
    os.makedirs(HOST_DIR, exist_ok=True)
    meta = G.SHARED["meta"]
    tags = list(all_tags())
    info = by_component()
    sold = meta["soldered"]
    lines = []
    a = lines.append

    a("# Теги текстур Electronics — что и как назвать, чтобы пак подхватил")
    a("")
    a("Этот файл **сгенерирован**: `python3 tools/user_tags.py doc`. Руками его не")
    a("правят — правят `shared/components.json` и `tools/gen_pack.py`, а список тегов")
    a("пересобирают. Причина простая: правила имён берёт сам генератор")
    a("(`gen_pack.user_candidates()`), и документ читает их оттуда же, поэтому")
    a("расхождение «в доке одно, в сборке другое» невозможно по построению.")
    a("")
    a(f"Сейчас в реестре **{len(comps())}** компонентов, у них в сумме")
    a(f"**{sum(v['states'] for v in info.values())}** визуальных состояний и")
    a(f"**{len(tags)}** текстурных тегов (состояние × грань) — ровно столько же")
    a(f"файлов кладёт генератор: {len(tags)} PNG в `textures/item/`.")
    a("")
    a("## Как положить свою текстуру")
    a("")
    a("1. Положите PNG в `users_textures/` (рядом с этим файлом) с именем-**тегом** из")
    a("   таблицы ниже.")
    a("2. `bash tools/user_pack.sh` — пересоберётся пак, `build/preview.png`, листы.")
    a("3. Готовый архив — `build/electronics-resourcepack.zip`; в `release/` он окажется")
    a("   только после `bash tools/user_pack.sh --release`.")
    a("")
    a("Генератор ищет файл по четырём вариантам имени — от частного к общему — и")
    a("берёт первый найденный:")
    a("")
    a("| приоритет | имя | что заменяет |")
    a("|---|---|---|")
    a("| 1 | `<id>_<состояние>_<грань>.png` | ровно один слот, например `led_red_on_top.png` |")
    a("| 2 | `<id>_<состояние>.png` | то же для `top` (для `side`/`bottom` не работает) |")
    a("| 3 | `<id>_<грань>.png` | грань сразу для всех состояний |")
    a("| 4 | `<id>.png` или `<id>_top.png` | **канва**: генератор сам выведет из неё "
      "остальные пять состояний, боковину и низ |")
    a("")
    a("Приоритет 4 — это и есть ответ на «сделай все остальные сам»: вы рисуете один")
    a("вид сверху, а `on`/`dim`/`burnt`/`rev`/`closed`, боковину и низ генератор")
    a("посчитает по вашим пикселям. Что именно он делает с состоянием:")
    a("")
    a("| состояние | суффикс файла | как выводится |")
    a("|---|---|---|")
    for st, suf in zip(G.STATE_NAMES, G.STATE_SUFFIX):
        tag = f"`{suf}`" if suf else "*без суффикса*"
        a(f"| `{st}` | {tag} | {STATE_NOTE[st]} |")
    a("")
    a("И отдельно:")
    a("")
    a("* **боковина** — `side_from_top()`: вырезается полоса `(0,12)–(32,28)` из вида")
    a("  сверху, тянется в 32×32 только NEAREST, низ затемняется тоном палитры ×0.55,")
    a("  на `on` подмешивается 30 % свечения, на `burnt` — 72 % сажи; альфа берётся из")
    a("  исходника, поэтому силуэт не «поедет»;")
    a("* **низ** — `bottom_texture()`: тон палитры ×0.5 и четыре «ножки».")
    a("")
    a("### Формат и ловушки")
    a("")
    a("* размер **32×32** (наш процедурный масштаб). 16×16 тоже примем — растянется")
    a("  NEAREST в 2 раза, пиксели лягут ровно по 2×2; 48×48 и прочее дробится,")
    a("  так что лучше 16 или 32;")
    a("* PNG с альфой. Модель рендерится с `minecraft:cutout`: пиксели с")
    a("  **α < 128 не рисуются вообще**, полупрозрачных краёв не будет — либо α=255,")
    a("  либо α=0 (`tools/user_tags.py check` считает, сколько пикселей попадёт под")
    a("  отсечение);")
    a("* ваши файлы **не** проходят через `mc_texture()` (дизер Bayer, палитра 20")
    a("  цветов, три тона граней) — иначе вы получили бы не то, что нарисовали. Из")
    a("  вашей канвы выводятся состояния, но и они красятся пиксельными операциями,")
    a("  а не переквантовываются;")
    a("* `users_textures/_examples/` и любые подпапки не сканируются: генератор")
    a("  смотрит ровно один уровень, поэтому пример там и лежит, пока вы его")
    a("  не скопировали наверх (`python3 tools/user_tags.py example`).")
    a("")
    a("### Блок-основа («плата»)")
    a("")
    a("Компоненты стоят на ванильном `minecraft:smooth_quartz_slab`; наш пак")
    a("перекрашивает его текстуры (`quartz_block_top/side/bottom`) в зелёную плату.")
    a("Если хотите свою — теги:")
    a("")
    a("| файл | размер | что это |")
    a("|---|---|---|")
    for name, note in HOST_FILES.items():
        a(f"| `users_textures/{name}` | 16×16 | {note} |")
    a("")
    a("## Ванильные текстуры и из чего что сделано")
    a("")
    a("Наши детали — не перекраска ванильных предметов: их рисует генератор по")
    a("`shape`/`size`/`palette`. Ванильное в паке только то, на что деталь опирается:")
    a("")
    a("* **блок-основа** — `minecraft:smooth_quartz_slab`, её PNG в ваниле —")
    a("  `quartz_block_top/side/bottom` (мы их перезаписываем; теги `host_*` выше);")
    a("* **ингредиенты рецепта** — обычные ванильные предметы, они перечислены у")
    a("  каждого компонента в таблице ниже. Где у предмета текстура: ванильная модель")
    a("  `minecraft/models/item/<name>.json` почти всегда `minecraft:item/generated`")
    a("  со слоем `minecraft:item/<name>` → `assets/minecraft/textures/item/<name>.png`;")
    a("  у блочных ингредиентов (`glass`, `stone`, `calcite`, `sand`, `*_block`) модель")
    a("  ссылается на `assets/minecraft/textures/block/…` — берите цветотделку оттуда;")
    a("* путь к конкретному PNG ванили здесь **не** проверялся: в песочнице нет")
    a("  клиентских ассетов Minecraft. Сверяйте по `versions/<версия>/<версия>.jar`")
    a("  или в игре F3+A.")
    a("")
    a(f"Через паяльный стол идут **{len(sold)}** id — у них в датапаке рецепта нет,")
    a("а список ингредиентов ниже берётся из реестра (`components[].craft`) — он обязан")
    a("совпадать с `Recipes.DEFS` на стороне плагина, и `selftest` это проверяет.")
    a("")
    a("## Все теги по компонентам")
    a("")
    a(f"Порядок — как в `shared/components.json`; `индекс` — с какого номера")
    a(f"состояния модели начинается этот компонент (`/elec model <id> <сост> <ориент>`);")
    a(f"всего индексов в реестре — **{meta['state_model_count']}** =")
    a(f"{sum(v['states'] for v in info.values())} × 6 ориентаций.")
    a("")
    base = 0
    for comp in comps():
        cid = comp["id"]
        v = info[cid]
        a(f"### `{cid}` — {v['ru']}")
        a("")
        a(f"* по-английски: {v['en']}")
        a(f"* kind `{v['kind']}`, shape `{v['shape']}`, размер {v['size']}, состояний {v['states']}, "
          f"индексы моделей {base}…{base + v['states'] * 6 - 1}"
          + (", *паяется*" if v["soldered"] else ""))
        if v["palette"]:
            a("* палитра процедурной отрисовки: " + ", ".join(f"`{c}`" for c in v["palette"]))
        if v["ing"]:
            a("* рецепт: " + ", ".join(f"`{k}`×{n}" for k, n in sorted(v["ing"].items())))
        else:
            a("* рецепта в реестре нет (предмет выдаётся командой или станком)")
        a("")
        a("| положить (теги) | вместо | состояние | грань | файл в паке |")
        a("|---|---|---|---|---|")
        for st in range(v["states"]):
            state = G.STATE_NAMES[st]
            suffix = G.STATE_SUFFIX[st]
            for face in FACES:
                cands = G.user_candidates(cid, state, face)
                packf = f"{cid}{suffix}_{face}.png"
                a(f"| {' / '.join('`%s`' % c for c in cands)} | {state}·{face} "
                  f"| `{state}` | `{face}` | `textures/item/{packf}` |")
        if v["states"] == 1:
            a("")
            a("(состояний один набор; адреса остальных пяти состояний существуют —")
            a("плагин может попросить любой из шести — и ведут на ту же модель)")
        a("")
        base += v["states"] * 6
    text = "\n".join(lines) + "\n"
    if to_stdout:
        # для проверки «док не устарел» (её делает tools/run_tests.sh): пишем
        # в трубу, ничего в дереве не трогаем
        sys.stdout.write(text)
        return
    path = os.path.join(HOST_DIR, "README.md")
    with open(path, "w", encoding="utf-8") as f:
        f.write(text)
    print(f"{path}: {len(lines)} строк, тегов {len(tags)}, компонентов {len(comps())}")


def main():
    argv = sys.argv[1:]
    mode = argv[0] if argv else "doc"
    if mode == "list":
        cmd_list()
    elif mode == "count":
        cmd_count()
    elif mode == "check":
        raise SystemExit(cmd_check())
    elif mode == "example":
        raise SystemExit(cmd_example(argv[1:]))
    elif mode == "doc":
        cmd_doc("--stdout" in argv[1:])
    else:
        print(__doc__)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
