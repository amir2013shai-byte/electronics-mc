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
  plugin/src/main/java/me/arena/electronics/circuit/ScopeTrace.java \
  plugin/src/main/java/me/arena/electronics/circuit/ScopeScreen.java \
  plugin/src/main/java/me/arena/electronics/registry/Kind.java \
  plugin/src/main/java/me/arena/electronics/deposit/Deposits.java \
  plugin/src/main/java/me/arena/electronics/kinetics/Kinetics.java \
  plugin/src/main/java/me/arena/electronics/fw/Firmware.java \
  tools/TestMna.java tools/TestTopology.java tools/TestCoupled.java \
  tools/TestDeposits.java tools/TestFirmware.java tools/TestKinetics.java \
  tools/TestScope.java tools/TestBattery.java tools/TestMovers.java tools/TestDynamo.java
java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp "$OUT" TestMna
java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp "$OUT" TestTopology
java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp "$OUT" TestCoupled
java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp "$OUT" TestDeposits
java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp "$OUT" TestFirmware
java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp "$OUT" TestKinetics
java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp "$OUT" TestScope
java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp "$OUT" TestBattery
# 1) имя артефакта — из версии, и она должна быть одна на весь проект:
# plugin.yml читает сервер, pom.xml читает maven, и расхождение значит, что в
# plugins/ ляжет jar с номером, который никто не проверял.
PV=$(sed -n 's/^version:[[:space:]]*\([0-9][0-9.]*\).*/\1/p' plugin/src/main/resources/plugin.yml | head -1)
MV=$(python3 -c "import re,io;s=io.open('plugin/pom.xml',encoding='utf-8').read();m=re.search(r'<artifactId>electronics</artifactId>\\s*<version>([0-9][^<]*)</version>',s);print(m.group(1) if m else '')")
if [ -n "$PV" ] && [ "$PV" = "$MV" ]; then
  echo "OK    версия плагина = версия артефакта: $PV (jar будет Electronics-$PV.jar)"
else
  echo "FAIL  plugin.yml: ${PV:-нет}, pom.xml: ${MV:-нет} — версии разошлись"; exit 1
fi

java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp "$OUT" TestMovers
java -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp "$OUT" TestDynamo

# --- ресурспак: сверка ссылок на модели/текстуры (если пак собран) ----------------
if [ -d build/resourcepack ]; then
  python3 tools/check_pack_refs.py
else
  echo "пропуск: собери пак — bash tools/publish.sh"
fi

# --- users_textures/README.md: список тегов обязан совпадать с генератором -------
# Документ генерируется из того же кода, что и имена тегов (gen_pack.user_candidates),
# поэтому расхождение значит лишь одно: MD не пересобрали после правки генератора.
if [ -f users_textures/README.md ]; then
  if python3 tools/user_tags.py doc --stdout | cmp -s - users_textures/README.md; then
    echo "OK    users_textures/README.md совпадает с генератором: $(wc -l < users_textures/README.md | tr -d ' ') строк, $(python3 tools/user_tags.py list | tail -n +2 | wc -l | tr -d ' ') тегов"
  else
    echo "FAIL  users_textures/README.md устарел — пересоберите: python3 tools/user_tags.py doc"
    exit 1
  fi
else
  echo "пропуск: нет users_textures/README.md — сгенерируйте: python3 tools/user_tags.py doc"
fi
