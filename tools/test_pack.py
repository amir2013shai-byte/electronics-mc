#!/usr/bin/env python3
"""Проверка ПРОВЕРОК пака: чекер обязан ловить и кражу ванили, и пропавшую плату.

Зачем: граница «что нам можно класть в assets/minecraft» менялась дважды, и оба
раза мимо:

  * v1.7.2 — пак клал свои textures/block/quartz_block_{top,side,bottom}.png,
    чтобы блок-основа выглядел платой. Их разделяет весь кварц (у гладкого
    кварца своей текстуры нет), и в мире зеленели стены, пол и ступени.
  * v1.7.3 — чужих файлов не стало вовсе. Вместе с ними исчезла и модель
    платы, «плата» снова стала куском кварца — жалоба того же пользователя.

Правило без теста — это правило, которое потеряют при первой правке. Поэтому
здесь каждый кейс: копия собранного пака -> поломка -> check_pack_refs.py, и
требуем не «прошло», а ровно нужную реакцию.

Копия дешёвая: всё, чего кейс не касается, — симлинки (иначе на каждый кейс
копировать 3164 файла). Под деревом поломки симлинки заменяются настоящими
файлами, иначе запись в симлинк дописалась бы к оригиналу.

usage: python3 tools/test_pack.py        # пакет должен быть собран
"""
import json, os, shutil, subprocess, sys, tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.environ.get("ELECTRONICS_PACK_DIR") or os.path.join(ROOT, "build", "resourcepack")
CHECKER = os.path.join(ROOT, "tools", "check_pack_refs.py")
BASE = "smooth_quartz_slab"          # blocks.base из default-конфига плагина
BOARD = "assets/electronics/textures/block/board_top.png"


def snapshot(materialize=()):
    """Копия пака симлинками; префиксы из materialize — настоящими файлами."""
    d = tempfile.mkdtemp(prefix="electronics-pack-")
    for dirpath, _, filenames in os.walk(SRC):
        rel_dir = os.path.relpath(dirpath, SRC)
        for fn in filenames:
            rel = os.path.normpath(os.path.join(rel_dir, fn))
            dst = os.path.join(d, rel)
            os.makedirs(os.path.dirname(dst), exist_ok=True)
            src = os.path.join(SRC, rel)
            if any(rel.startswith(m) for m in materialize):
                shutil.copy2(src, dst)
            else:
                os.symlink(os.path.abspath(src), dst)
    return d


def _real(d, rel):
    """Симлинк -> обычный файл, чтобы правка не уехала в оригинал."""
    p = os.path.join(d, rel)
    if os.path.islink(p):
        data = open(p, "rb").read()
        os.unlink(p)
        os.makedirs(os.path.dirname(p), exist_ok=True)
        with open(p, "wb") as f:
            f.write(data)
    return p


def drop(d, rel):
    p = os.path.join(d, rel)
    if os.path.lexists(p):
        os.unlink(p)


def write(d, rel, text):
    p = _real(d, rel)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p, "w", encoding="utf-8") as f:
        f.write(text)


def set_json(d, rel, obj):
    write(d, rel, json.dumps(obj, indent=1))


def patch_json(d, rel, mutate):
    path = os.path.join(d, rel)
    obj = json.load(open(path, encoding="utf-8"))
    mutate(obj)
    set_json(d, rel, obj)


MC = "assets/minecraft/models/block/%s.json"
CASES = [
    # (что проверяем, materialize, поломка, ожидаемое rc, ожидаемое слово)
    ("чистый пак проходит", (), None, 0, None),
    ("нет модели платы — основа снова ванильный блок", (MC % BASE,),
     lambda d: drop(d, MC % BASE), 1, "плата не перекрашена"),
    ("нет модели верхнего полублока", (MC % (BASE + "_top"),),
     lambda d: drop(d, MC % (BASE + "_top")), 1, "плата не перекрашена"),
    ("нет models/item/<основа>.json — предмет станет платой",
     ("assets/minecraft/models/item/",),
     lambda d: drop(d, "assets/minecraft/models/item/%s.json" % BASE), 1, "плата не перекрашена"),
    ("плата смотрит в чужую текстуру (тот самый баг 1.7.2)", (MC % BASE,),
     lambda d: set_json(d, MC % BASE, {"parent": "minecraft:block/slab",
                                        "textures": {f: "minecraft:block/quartz_block_bottom"
                                                     for f in ("top", "side", "bottom")}}),
     1, "чужую текстуру"),
    ("плата ссылается на текстуру, которой нет в паке", (MC % BASE,),
     lambda d: patch_json(d, MC % BASE,
                          lambda o: o["textures"].update({"top": "electronics:block/nope"})),
     1, "нет текстуры"),
    ("плата унаследована не от slab-модели (геометрия поедет)", (MC % BASE,),
     lambda d: patch_json(d, MC % BASE, lambda o: o.update({"parent": "minecraft:block/cube_all"})),
     1, "ждали"),
    ("атлас не объявил electronics/block — спрайты платы не соберутся",
     ("assets/minecraft/atlases/",),
     lambda d: patch_json(d, "assets/minecraft/atlases/blocks.json",
                          lambda o: o.__setitem__("sources", [s for s in o["sources"]
                                                               if "block" not in str(s.get("source"))])),
     1, "атлас"),
    ("чужая текстура блока в паке — это чужой блок, а не наш", (),
     lambda d: write(d, "assets/minecraft/textures/block/stone.png", "не png"),
     1, "перекрывает ванильные ресурсы"),
    # исключение из прошлого правила: кварц по явному выбору пользователя. Пока
    # в users_textures/ лежит host_*.png, пак ВПРАВЕ положить quartz_block_*.png
    # — это подписанный им перекрас всего кварца, и ругаться на него нельзя.
    ("кварц по явному host_*.png разрешён (исключение, а не дыра)", (),
     lambda d: write(d, "assets/minecraft/textures/block/quartz_block_top.png", "не png"),
     0, None),
    ("чужая модель блока (перекрасит чужой полублок)", ("assets/minecraft/models/",),
     lambda d: write(d, "assets/minecraft/models/block/quartz_slab.json", "{}"),
     1, "перекрывает ванильные ресурсы"),
    ("чужое items-определение предмета (1.21.9+ реестр)", ("assets/minecraft/items/",),
     lambda d: write(d, "assets/minecraft/items/%s.json" % BASE, "{}"),
     1, "перекрывает ванильные ресурсы"),
    ("у детали пропало items-определение", ("assets/electronics/items/",),
     lambda d: drop(d, "assets/electronics/items/breadboard_on.json"),
     1, "отсутствующих моделей: 1"),
    ("у платы пропала текстура (модель есть, рисунка нет)",
     ("assets/electronics/textures/block/",),
     lambda d: drop(d, BOARD), 1, "нет текстуры"),
    # blocks.base читается из default-конфига; если человек поменял его, а пак
    # пересобрать забыл, чекер обязан сказать об этом, а не молча пропустить
    # «разрешённые» файлы другого блока.
    ("пак собран под другой blocks.base — чекер обязан это увидеть",
     ("assets/minecraft/models/",),
     lambda d: None, 1, "плата не перекрашена",
     {"ELECTRONICS_BASE_BLOCK": "POLISHED_BLACKSTONE_SLAB"}),
]


def run(d, extra=None):
    env = dict(os.environ)
    env["ELECTRONICS_PACK_DIR"] = d
    env.update(extra or {})
    p = subprocess.run([sys.executable, CHECKER], cwd=ROOT, env=env,
                       capture_output=True, text=True)
    return p.returncode, (p.stdout or "") + (p.stderr or "")


def main():
    if not os.path.isdir(SRC):
        sys.exit("пак не собран: сначала python3 tools/gen_pack.py (ждал %s)" % SRC)
    ok = bad = 0
    for case in CASES:
        name, materialize, breakit, want_rc, want_word = case[:5]
        env_extra = case[5] if len(case) > 5 else {}
        d = snapshot(materialize)
        try:
            if breakit:
                breakit(d)
            rc, out = run(d, env_extra)
            if rc != want_rc:
                raise AssertionError("чекер ответил rc=%d, ждали %d\n%s" % (rc, want_rc, out[-700:]))
            if want_word and want_word not in out:
                raise AssertionError("в выводе нет «%s»:\n%s" % (want_word, out[-700:]))
            said = ""
            if want_rc == 1:
                line = [l for l in out.splitlines() if "ПОЗОР" in l or l.strip().startswith("-")]
                said = ("  — «" + line[0].strip()[:78] + "»") if line else ""
            print("  OK   %s%s" % (name, said))
            ok += 1
        except AssertionError as e:
            print("  ПОЗОР %s: %s" % (name, e))
            bad += 1
        finally:
            shutil.rmtree(d, ignore_errors=True)
    print("TestPack: %s (%d проверок)" % ("ALL OK" if not bad else "FAIL %d" % bad, ok + bad))
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main())
