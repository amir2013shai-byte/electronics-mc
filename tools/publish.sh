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

# Имя jar — с версией плагина, и берётся оно из единственного места, plugin.yml:
# артефакт вида Electronics-1.6.0.jar нельзя перепутать с предыдущим, лежащим в
# plugins/, а `tools/publish.sh` сам выбрасывает старые имена из release/.
VER=$(sed -n 's/^version:[[:space:]]*\([0-9][0-9.]*\).*/\1/p' plugin/src/main/resources/plugin.yml | head -1)
VER=${VER:-0}
JAR=plugin/target/Electronics-$VER.jar
[ -f "$JAR" ] || JAR=$(ls -t plugin/target/Electronics-*.jar 2>/dev/null | head -1)
[ -f "$JAR" ] || JAR=$(ls -t ../srv/plugins/Electronics-*.jar 2>/dev/null | head -1)
if [ -n "$JAR" ]; then
  rm -f release/Electronics-*.jar
  cp "$JAR" "release/Electronics-$VER.jar"
  echo "release/Electronics-$VER.jar  (из $JAR)"
else
  echo "внимание: jar не найден — собери: (cd plugin && mvn -DskipTests package)" >&2
fi

cd release
rm -f sha1sums.txt
sha1sum "Electronics-$VER.jar" electronics-resourcepack.zip \
        electronics-recipes-datapack.zip preview.png scope-screen.png textures/*.png \
        > sha1sums.txt 2>/dev/null || sha1sum * textures/*.png > sha1sums.txt
cd ..
echo "== release/ =="
ls -la release | tail -n +2
