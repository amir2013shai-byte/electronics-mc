#!/usr/bin/env bash
# Пересборка ресурспака под текстуры из users_textures/ + новый preview.png.
#
# Зачем: содержимое пака выводится из shared/components.json (и из того, что
# человек положил в users_textures/), поэтому «потыкать PNG руками в zip» —
# способ получить пак, который расходится с реестром. Этот скрипт делает то же,
# что tools/publish.sh, но сначала проверяет имена тегов: опечатка в имени
# выглядела бы как «я положил текстуру, а её не взяли», и молча прошла бы.
#
# Заодно пересобирает users_textures/README.md — список тегов берётся из кода
# генератора, поэтому документ не может устареть относительно сборки.
#
# usage:
#   bash tools/user_pack.sh              # пак + preview.png + листы в build/
#   bash tools/user_pack.sh --release    # то же + копирование в release/ и sha1sums
#   bash tools/user_pack.sh --doc        # только пересобрать users_textures/README.md
#   bash tools/user_pack.sh --check      # только проверить имена положенных файлов
#   bash tools/user_pack.sh --example [id]  # заготовка-пример в users_textures/_examples/
#
# Переменная ELECTRONICS_USER_TEXTURES меняет каталог (тесты, CI, «поиграть не
# трогая репозиторий»).
set -euo pipefail
cd "$(dirname "$0")/.."

U="${ELECTRONICS_USER_TEXTURES:-users_textures}"

case "${1:-}" in
  --doc)     exec python3 tools/user_tags.py doc ;;
  --check)   exec python3 tools/user_tags.py check ;;
  --example) shift; mkdir -p "$U"; exec python3 tools/user_tags.py example "$@" ;;
esac

mkdir -p "$U"
echo "— проверка имён в $U (что положенное действительно было тегом)"
python3 tools/user_tags.py check || echo "! некоторые файлы генератор не возьмёт — см. выше"

echo "— генерация ресурспака (и датапака рецептов)"
python3 tools/gen_pack.py | tail -24

echo "— превью: build/preview.png и раздаточные листы"
python3 tools/preview.py
python3 tools/sheets.py | tail -1

echo "— сверка ссылок на модели и текстуры"
python3 tools/check_pack_refs.py | tail -1

python3 tools/user_tags.py doc >/dev/null   # список тегов — за одно с паком

if [ "${1:-}" = "--release" ]; then
  mkdir -p release
  cp build/electronics-resourcepack.zip release/electronics-resourcepack.zip
  cp build/preview.png release/preview.png
  (cd release && sha1sum Electronics-1.0.0.jar electronics-resourcepack.zip \
      electronics-recipes-datapack.zip preview.png scope-screen.png textures/*.png \
      > sha1sums.txt 2>/dev/null || true)
  echo "— обновлено release/ (внимание: теперь ваш пак отличается от того, что в git)"
else
  echo "— release/ не тронут; нужен обновлённый раздаточный архив — перезапустите с --release"
fi
echo "готово: build/electronics-resourcepack.zip, build/preview.png"
