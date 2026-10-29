#!/usr/bin/env bash
# Тесты «голого» ядра плагина (без сервера): решатель MNA и топология.
set -e
cd "$(dirname "$0")/.."
export PATH=/usr/lib/jvm/java-21-openjdk-amd64/bin:$PATH
REPO=/home/user/tools/repo
CP=$REPO/org/spigotmc/spigot-api/1.21.11-R0.2-SNAPSHOT/spigot-api-1.21.11-R0.2-20260324.161329-13.jar:$REPO/org/joml/joml/1.10.8/joml-1.10.8.jar
OUT=$(mktemp -d)
javac -encoding UTF-8 -d "$OUT" -cp "$CP:plugin/src/main/java" \
  plugin/src/main/java/me/arena/electronics/sim/Mna.java \
  plugin/src/main/java/me/arena/electronics/circuit/Topology.java \
  plugin/src/main/java/me/arena/electronics/registry/Kind.java \
  tools/TestMna.java tools/TestTopology.java
java -Dfile.encoding=UTF-8 -cp "$OUT" TestMna
java -Dfile.encoding=UTF-8 -cp "$OUT" TestTopology

# сверка ресурспака с логикой плагина (если пак собран)
if [ -d build/resourcepack ]; then
  python3 tools/check_pack_refs.py
else
  echo "пропуск: собери пак — python3 tools/gen_pack.py"
fi
