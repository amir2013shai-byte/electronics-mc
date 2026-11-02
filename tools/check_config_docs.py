#!/usr/bin/env python3
"""Сверяет keys config.yml с таблицей docs/config.md (и наоборот).

Зачем: `config.yml` читает сервер, `docs/config.md` читает человек. Когда ключ
уезжает из одного из них, документация врёт ровно там, где её ищут — этот класс
ошибок уже ловился на числах в README.
"""
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CFG = os.path.join(ROOT, "plugin", "src", "main", "resources", "config.yml")
DOC = os.path.join(ROOT, "docs", "config.md")


def keys_from_config():
    """Плоские ключи вида «секция.ключ» из yaml без зависимостей."""
    out = []
    section = ""
    for raw in open(CFG, encoding="utf-8"):
        line = raw.rstrip("\n")
        if not line.strip() or line.lstrip().startswith("#"):
            continue
        indent = len(line) - len(line.lstrip(" "))
        body = line.strip()
        if indent == 0 and body.endswith(":"):
            section = body[:-1]
            continue
        if indent == 0:
            section = ""                      # ключ верхнего уровня — без секции
        if ":" not in body:
            continue
        key = body.split(":", 1)[0].strip()
        if key:
            out.append(f"{section}.{key}" if section else key)
    return out


def keys_from_doc(sections):
    """Ключы, которые док называет в `секция.ключ`. Имя секции берём из самого
    config.yml — иначе регулярка ловит и числа вида `0.05`, и док начинает
    «недоговаривать» там, где ничего не пропадало."""
    text = open(DOC, encoding="utf-8").read()
    found = set()
    for m in re.findall(r"`([a-z0-9_.-]+\.[a-z0-9_.-]+)`", text):
        if m.split(".", 1)[0] in sections:
            found.add(m)
    return sorted(found)


def main():
    cfg = keys_from_config()
    sections = {k.split(".", 1)[0] for k in cfg if "." in k}
    doc = keys_from_doc(sections)
    docset = set(doc)
    # ключи, которые документация обязана называть; служебные секции исключаем
    missing = [k for k in cfg if k not in docset and k not in {"debug", "selftest"}]
    extra = [k for k in doc if k not in set(cfg)]
    print(f"config.yml: {len(cfg)} ключей, docs/config.md упоминает {len(docset)}")
    if missing:
        print("ПОЗОР: в docs/config.md нет строк про:", ", ".join(missing[:8]))
    if extra:
        print("ПОЗОР: docs/config.md обещает ключи, которых нет в config.yml:",
              ", ".join(extra[:8]))
    if missing or extra:
        sys.exit(1)
    print("OK    все ключи config.yml описаны в docs/config.md, лишнего нет")


if __name__ == "__main__":
    main()
