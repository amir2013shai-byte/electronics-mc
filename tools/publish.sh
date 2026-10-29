#!/usr/bin/env bash
# Публикация артефактов.
#
# Зачем: каталоги build/ и target/ не попадают ни в снимки песочницы, ни в
# git (см. .gitignore) — они считаются одноразовыми. Всё, что нужно
# пользователю, складывается в release/, который и сохраняется, и коммитится.
#
# usage: bash tools/publish.sh
set -euo pipefail
cd "$(dirname "$0")/.."

python3 tools/gen_pack.py
python3 tools/preview.py
python3 tools/check_pack_refs.py | tail -1

mkdir -p release
cp build/electronics-resourcepack.zip release/
cp build/electronics-recipes-datapack.zip release/
cp build/preview.png release/preview.png

JAR=plugin/target/Electronics-1.0.0.jar
if [ ! -f "$JAR" ] && [ -f ../srv/plugins/Electronics-1.0.0.jar ]; then
  JAR=../srv/plugins/Electronics-1.0.0.jar   # собранный и проверенный на сервере
fi
if [ -f "$JAR" ]; then
  cp "$JAR" release/Electronics-1.0.0.jar
else
  echo "внимание: jar не найден — собери: (cd plugin && mvn -DskipTests package)" >&2
fi

cd release
rm -f sha1sums.txt
sha1sum Electronics-1.0.0.jar electronics-resourcepack.zip \
        electronics-recipes-datapack.zip preview.png > sha1sums.txt 2>/dev/null || \
  sha1sum * > sha1sums.txt
cd ..
echo "== release/ =="
ls -la release | tail -n +2
