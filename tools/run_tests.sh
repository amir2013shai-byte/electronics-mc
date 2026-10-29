#!/usr/bin/env bash
# Тесты «голого» ядра плагина (без сервера): решатель MNA, топология и
# сверка ресурспака. Запускается и в песочнице, и у тебя на машине.
set -e
cd "$(dirname "$0")/.."
for j in /usr/lib/jvm/java-21-openjdk-amd64/bin "$JAVA_HOME/bin"; do
  [ -x "$j/javac" ] && export PATH="$j:$PATH" && break
done

# --- classpath: spigot-api + joml -------------------------------------------------
# Ищем в maven-репозиториях по маске (имена snapshot-файлов содержат дату сборки,
# поэтому точное имя здесь было бы хрупким); если пусто — просим maven дотянуть.
for cand in "${REPO:-}" "$HOME/.m2/repository" /home/user/tools/repo; do
  [ -n "$cand" ] && [ -d "$cand" ] && REPO="$cand" && break
done
REPO="${REPO:-$HOME/.m2/repository}"
JARS=$(ls "$REPO"/org/spigotmc/spigot-api/*/*.jar "$REPO"/org/joml/joml/*/*.jar 2>/dev/null \
       | grep -v sources | tr '\n' ':' || true)
if [ -z "$JARS" ]; then
  echo "в $REPO нет spigot-api/joml — подтягиваю через maven (нужна сеть)"
  (cd plugin && mvn -q -B -Dmaven.repo.local="$REPO" dependency:build-classpath \
      -Dmdep.outputFile=/tmp/electronics-cp.txt >/dev/null)
  JARS=$(cat /tmp/electronics-cp.txt | tr ':' '\n' | grep -E 'spigot-api|joml' | tr '\n' ':')
fi
CP="${JARS}plugin/src/main/java"

OUT=$(mktemp -d)
javac -encoding UTF-8 -d "$OUT" -cp "$CP" \
  plugin/src/main/java/me/arena/electronics/sim/Mna.java \
  plugin/src/main/java/me/arena/electronics/circuit/Topology.java \
  plugin/src/main/java/me/arena/electronics/registry/Kind.java \
  tools/TestMna.java tools/TestTopology.java
java -Dfile.encoding=UTF-8 -cp "$OUT" TestMna
java -Dfile.encoding=UTF-8 -cp "$OUT" TestTopology

# --- ресурспак: сверка ссылок на модели/текстуры (если пак собран) ----------------
if [ -d build/resourcepack ]; then
  python3 tools/check_pack_refs.py
else
  echo "пропуск: собери пак — bash tools/publish.sh"
fi
