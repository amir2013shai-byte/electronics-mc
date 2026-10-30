#!/usr/bin/env bash
# Тесты «голого» ядра плагина (без сервера): решатель MNA, топология и
# сверка ресурспака. Запускается и в песочнице, и у тебя на машине.
set -e
cd "$(dirname "$0")/.."
# Нужен JDK 21+ (switch-выражения в ядре). Ищем любой подходящий: в песочнице
# /usr живёт до первой перезагрузки, поэтому путь «java-21-openjdk-amd64» может
# и не существовать, а JDK 11 в /usr/lib/jvm есть всегда — на него брать нельзя.
for j in /usr/lib/jvm/java-21-openjdk-amd64/bin /usr/lib/jvm/*/bin "$JAVA_HOME/bin"; do
  [ -x "$j/javac" ] || continue
  ver=$("$j/javac" -version 2>&1 | sed -E 's/[^0-9]*([0-9]+).*/\1/')
  if [ "${ver:-0}" -ge 21 ]; then export PATH="$j:$PATH"; break; fi
done
javac -version >/dev/null 2>&1 || { echo "нет javac 21+: sudo apt-get install -y --no-install-recommends openjdk-21-jdk-headless" >&2; exit 1; }

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
  plugin/src/main/java/me/arena/electronics/deposit/Deposits.java \
  plugin/src/main/java/me/arena/electronics/kinetics/Kinetics.java \
  plugin/src/main/java/me/arena/electronics/fw/Firmware.java \
  tools/TestMna.java tools/TestTopology.java tools/TestCoupled.java \
  tools/TestDeposits.java tools/TestFirmware.java tools/TestKinetics.java
java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp "$OUT" TestMna
java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp "$OUT" TestTopology
java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp "$OUT" TestCoupled
java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp "$OUT" TestDeposits
java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp "$OUT" TestFirmware
java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp "$OUT" TestKinetics

# --- ресурспак: сверка ссылок на модели/текстуры (если пак собран) ----------------
if [ -d build/resourcepack ]; then
  python3 tools/check_pack_refs.py
else
  echo "пропуск: собери пак — bash tools/publish.sh"
fi
