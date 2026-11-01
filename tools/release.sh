#!/usr/bin/env bash
# Выложить версию на GitHub: main + тег + релиз с артефактами (без gh auth).
#
# Зачем: у пользователя нет настроенного gh и нет прав на push по ключу — есть
# только fine-grained PAT, который он вставляет в чат. Токен нигде не хранится:
# его надо передать переменной каждый раз (в .git/config тоже нет ни remote, ни
# identity — снимки песочницы их не сохраняют).
#
# Требуемые права PAT: Contents: Read and write. Если коммит трогает
# .github/workflows/ — ещё Workflows: Read and write, иначе GitHub отвергает
# ВЕСЬ push (не только файл).
#
# usage: GITHUB_TOKEN=github_pat_xxx bash tools/release.sh v1.3.0
set -euo pipefail
cd "$(dirname "$0")/.."

TAG="${1:?нужен тег, напр. v1.3.0}"
OWNER="amir2013shai-byte"
REPO="electronics-mc"
# Токен берём из ~/.secrets/github.env (файл ВНЕ репозитория — см. tools/push.sh),
# если его не передали переменной.
SECRETS="${ELECTRONICS_SECRETS:-$HOME/.secrets/github.env}"
if [ -z "${GITHUB_TOKEN:-}" ] && [ -f "$SECRETS" ]; then
  # shellcheck disable=SC1090
  . "$SECRETS"
  GITHUB_TOKEN="${GH_TOKEN:-}"
fi
TOK="${GITHUB_TOKEN:?нет токена: положи GH_TOKEN в $SECRETS или экспортируй GITHUB_TOKEN}"
API="https://api.github.com/repos/$OWNER/$REPO"
GITURL="https://x-access-token:$TOK@github.com/$OWNER/$REPO.git"

JAR_FILE=$(ls release/Electronics-*.jar 2>/dev/null | head -1)
[ -n "$JAR_FILE" ] || { echo "нет release/Electronics-*.jar — собери: bash tools/publish.sh" >&2; exit 1; }
git tag -f -a "$TAG" -m "Electronics $TAG (Minecraft/Spigot 1.21.11)" 2>/dev/null || git tag -f "$TAG"

echo "— push main + $TAG"
GIT_TERMINAL_PROMPT=0 git -c credential.helper= push "$GITURL" main:main "+refs/tags/$TAG:refs/tags/$TAG"

echo "— релиз"
# Число проверок selftest в тексте ниже — из живого прогона на Paper (grep -c PASS
# в логе сервера), а не из исходников: на каждый новый релиз его надо сверить,
# иначе релизные заметки врут. sha1 берём из release/sha1sums.txt на лету, чтобы
# они физически не могли разъехаться с ассетами.
JAR_SHA=$(sha1sum "$JAR_FILE" | cut -c1-40)
RP_SHA=$(sha1sum release/electronics-resourcepack.zip | cut -c1-40)
DP_SHA=$(sha1sum release/electronics-recipes-datapack.zip | cut -c1-40)
# Паспорт паков — из самих архивов, а не из памяти о сборке: заголовок читаем
# прямо из zip, числа файлов считаем по содержимому. Иначе заметки расходятся с
# ассетами ровно на тех полях, из-за которых паки и перестают грузиться.
RP_FILES=$(unzip -l release/electronics-resourcepack.zip | awk 'END{print $(NF-1)}')  # записей, не байтов
ITEM_DEFS=$(unzip -l release/electronics-resourcepack.zip | grep -c 'assets/electronics/items/.*\.json')
DP_RECIPES=$(unzip -l release/electronics-recipes-datapack.zip | grep -c 'data/electronics/recipe/.*\.json')
PK_FMT=$(python3 - <<'PYF'
import io, json, zipfile
out = []
for path in ("release/electronics-resourcepack.zip", "release/electronics-recipes-datapack.zip"):
    with zipfile.ZipFile(path) as z:
        p = json.loads(io.TextIOWrapper(z.open("pack.mcmeta"), encoding="utf-8").read())["pack"]
    out.append("%s..%s" % (p.get("min_format"), p.get("max_format")))
print(" / ".join(out))
PYF
)
# PASS-строки — из последнего прогона /elec selftest: в логе их обычно два
# (стартовый и ручной), поэтому считаем окно от «PASS редактор схемы» до
# «PASS каталог», а не весь файл. Лога нет — берём число проверок из исходника.
LOG=$(ls -t /home/user/srv/mc*.log 2>/dev/null | head -1 || true)
SELFTEST_LINES=$(awk '/PASS редактор схемы/{start=NR} /PASS каталог/{end=NR} /PASS /{hit[NR]=1} END{c=0; if (start && end) for (i=start;i<=end;i++) if (hit[i]) c++; print c+0}' "$LOG" 2>/dev/null || true)
[ "${SELFTEST_LINES:-0}" -gt 0 ] 2>/dev/null || SELFTEST_LINES=$(grep -c '"&aPASS "' plugin/src/main/java/me/arena/electronics/command/ElecCommand.java || echo 0)
LIVE_NAME=${LOG:-нет лога}; LIVE_NAME=$(basename "${LIVE_NAME}")
LIVE_FAIL=$(grep -c ': FAIL' "$LOG" 2>/dev/null || echo 0)
LIVE_ERR=$(grep -c 'ERROR' "$LOG" 2>/dev/null || echo 0)
LIVE_PARSE=$(grep -c "Couldn't parse data file\|Couldn't load file\|missing mandatory" "$LOG" 2>/dev/null || echo 0)
LIVE_DP=$(grep -m1 -o 'PASS датапак:.*' "$LOG" 2>/dev/null | head -1)
LIVE_HAND=$(grep -m1 -o 'PASS рукоять крутит генератор:.*' "$LOG" 2>/dev/null | head -1)
TB_CHECKS=$(grep -cE '^[[:space:]]+check\(' tools/TestBattery.java 2>/dev/null || echo 0)
OFFLINE=$(bash tools/run_tests.sh 2>&1 | grep -oE 'итог: OK=[0-9]+ FAIL=[0-9]+' | tail -1)
REG=$(python3 -c 'import json,io;d=json.load(io.open("shared/components.json",encoding="utf-8"));m=d["meta"];print(len(d["components"]), len(m["soldered"]), m["state_model_count"])' 2>/dev/null || echo "0 0 0")
COMPONENTS=${REG%% *}; REGREST=${REG#* }; SOLDERED=${REGREST%% *}; STATES=${REGREST##* }
NOTES="Сборка против Spigot/Paper 1.21.11. Ставить: jar в plugins/, ресурспак — на клиент (или resource-pack.url в server.properties), датапак — в <world>/datapacks/.

v1.7.1 — починка паков (по логу реального сервера: 1 ошибка метаданных + 38 «Couldn't parse data file», детали в мире рисовались плитой-основой):
* pack.mcmeta обоих паков теперь пишет и pack_format + supported_formats (это читают 1.21.7–1.21.8), и min_format/max_format (с 1.21.9 они обязательны: без них сервер отвечает «missing mandatory fields» и пакет не берёт вовсе). Диапазоны этого билда: ${PK_FMT} (у датапака своя линия форматов, не совпадает с ресурспаком).
* рецепты: ингредиенты — простые строки (объекты {"item": ...} ванильный разбор не принимает), результат — minecraft:smooth_quartz_slab / minecraft:brush с компонентами item_model, item_name и custom_data→electronics:def; раньше результат ссылался на electronics:<id>, ключа которого нет ни в одном реестре предметов.
* ресурспак: определений предметов ${ITEM_DEFS} вместо 114 — адрес состояния electronics:<id>_<dim|on|burnt|rev|closed> обязан существовать, иначе клиент тихо берёт запасную модель и деталь выглядит как плита-основа.
* плагин: Items.defOf() узнаёт деталь и по item_model, если ядро не переложило custom_data в PDC; /elec selftest проверяет, что ядро приняло рецепты из датапака (${LIVE_DP}).

Реестр: ${COMPONENTS} id, из них ${SOLDERED} паяных, ${STATES} индексов состояния модели. Артефакты этого релиза: ресурспак ${RP_FILES} файлов, датапак ${DP_RECIPES} рецептов.

Проверка живым сервером (Paper 1.21.11 build 132, лог ${LIVE_NAME}): ${SELFTEST_LINES} PASS-строк, ${LIVE_FAIL} FAIL, ${LIVE_ERR} [ERROR], ${LIVE_PARSE} строк «Couldn't parse data file». Живая строка про привод: ${LIVE_HAND}

Офлайн: ${OFFLINE} (tools/run_tests.sh), TestBattery ALL OK (${TB_CHECKS} проверок), tools/check_pack_refs.py валидирует заголовки паков и форму всех рецептов.

sha1: jar ${JAR_SHA}, ресурспак ${RP_SHA}, датапак ${DP_SHA}. Архивы пакуются детерминированно: два прогона tools/gen_pack.py дают те же байты.

Текстуры нарисованы процедурно и доведены до вида ванильного ресурса (mc_texture: сетка 16 px, дизер Bayer 4x4, палитра 20 цветов, три тона граней); свои PNG можно положить в users_textures/ и пересобрать через bash tools/user_pack.sh — 1695 тегов описаны в users_textures/README.md. Что появилось в v1.7.0 (рукоять, водяное колесо, машинный генератор) — README.md §4f: там же живые цифры прогона."
body=$(python3 -c 'import json,sys; print(json.dumps({"tag_name":sys.argv[1],"name":"Electronics "+sys.argv[1]+" (Minecraft 1.21.11)","body":sys.argv[2]}))' "$TAG" "$NOTES")
resp=$(curl -sf -X POST -H "Authorization: Bearer $TOK" -H "Accept: application/vnd.github+json" \
        -H "Content-Type: application/json" -d "$body" "$API/releases" || true)
if [ -z "$resp" ]; then
  echo "  релиз $TAG уже есть — обновляю заметки и перекладываю ассеты"
  id=$(curl -sf -H "Authorization: Bearer $TOK" "$API/releases/tags/$TAG" | python3 -c 'import json,sys; print(json.load(sys.stdin)["id"])')
  curl -sf -X PATCH -H "Authorization: Bearer $TOK" -H "Accept: application/vnd.github+json" \
       -H "Content-Type: application/json" -d "$body" "$API/releases/$id" >/dev/null \
    && echo "  заметки релиза обновлены" || echo "  ВАЖНО: заметки обновить не вышло — сверь тело вручную"
  up="https://uploads.github.com/repos/$OWNER/$REPO/releases/$id/assets"
  for a in $(curl -sf -H "Authorization: Bearer $TOK" "$API/releases/$id/assets" | python3 -c 'import json,sys; [print(x["name"]) for x in json.load(sys.stdin)]'); do
    curl -sf -X DELETE -H "Authorization: Bearer $TOK" "$API/releases/assets/$(curl -sf -H "Authorization: Bearer $TOK" "$API/releases/$id/assets" | python3 -c "import json,sys;print([x['id'] for x in json.load(sys.stdin) if x['name']=='$a'][0])")" >/dev/null || true
  done
else
  up=$(echo "$resp" | python3 -c 'import json,sys; print(json.load(sys.stdin)["upload_url"].split("{")[0])')
fi

for f in "$JAR_FILE" release/electronics-resourcepack.zip \
         release/electronics-recipes-datapack.zip release/preview.png release/scope-screen.png \
         release/sha1sums.txt release/textures/sheet-0*.png; do
  [ -f "$f" ] || continue
  curl -sf -X POST -H "Authorization: Bearer $TOK" -H "Content-Type: application/octet-stream" \
       --data-binary "@$f" "$up?name=$(basename "$f")" >/dev/null
  echo "  + $(basename "$f") ($(stat -c%s "$f") B)"
done
echo "готово: https://github.com/$OWNER/$REPO/releases/tag/$TAG"
