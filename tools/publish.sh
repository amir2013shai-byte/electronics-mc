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
python3 tools/sheets.py | tail -1   # раздаточные листы в release/textures/

mkdir -p release
cp build/electronics-resourcepack.zip release/
cp build/electronics-recipes-datapack.zip release/
cp build/preview.png release/preview.png

# Кадр экрана осциллографа — его рисует тот же код, что и в мире (ScopeScreen
# от Bukkit не зависит), поэтому превью собирается офлайн и врём тут не будет.
# Нет JDK — просто пропускаем: артефактdecorативный.
JAVAC="${JAVA_HOME:-/usr/lib/jvm/java-21-openjdk-amd64}/bin/javac"
if [ ! -x "$JAVAC" ]; then JAVAC=$(ls -d /usr/lib/jvm/*/bin/javac 2>/dev/null | head -1 || true); fi
if [ -n "${JAVAC:-}" ] && [ -x "$JAVAC" ]; then
  mkdir -p build/scopeframe
  if "$JAVAC" -encoding UTF-8 -d build/scopeframe \
       plugin/src/main/java/me/arena/electronics/circuit/ScopeTrace.java \
       plugin/src/main/java/me/arena/electronics/circuit/ScopeScreen.java \
       tools/ScopeFrame.java >/dev/null 2>&1 && \
     "${JAVAC%javac}java" -cp build/scopeframe me.arena.electronics.circuit.ScopeFrame \
       build/scope-screen.png >/dev/null 2>&1; then
    cp build/scope-screen.png release/scope-screen.png
    echo "release/scope-screen.png"
  fi
fi

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
        electronics-recipes-datapack.zip preview.png scope-screen.png textures/*.png \
        > sha1sums.txt 2>/dev/null || sha1sum * textures/*.png > sha1sums.txt
cd ..
echo "== release/ =="
ls -la release | tail -n +2
