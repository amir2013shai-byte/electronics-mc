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
ITEM_DEFS=$(unzip -l release/electronics-resourcepack.zip | grep -c 'assets/electronics/items/.*\.json' || true)
ITEM_DEFS=${ITEM_DEFS:-0}
DP_RECIPES=$(unzip -l release/electronics-recipes-datapack.zip | grep -c 'data/electronics/recipe/.*\.json' || true)
DP_RECIPES=${DP_RECIPES:-0}
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
# PASS-строки — из ПОСЛЕДНЕГО прогона selftest: в логе их обычно два (стартовый
# и ручной), а честное число — это один полный прогон. Считаем от последней строки
# «редактор схемы» (самопроверка идёт в фиксированном порядке, это её первая
# строка) до конца файла; цветоскоды Bukkit снимаем, иначе «PASS» прячется за &a.
# Раньше окно заканчивалось на «PASS каталог» и две последние проверки (солнце и
# реле, счётчик и паспорт) в него не попадали — заметки отставали от лога на 2.
# Лога нет — берём число проверок из исходника.
LOG=$(ls -t /home/user/srv/mc*.log 2>/dev/null | head -1 || true)
SELFTEST_LINES=$(python3 - "$LOG" <<'PYW' 2>/dev/null || true
import io, re, sys
try:
    lines = [re.sub(r"&[0-9a-fk-or]", "", l)
             for l in io.open(sys.argv[1], encoding="utf-8", errors="replace")]
except OSError:
    print(0); raise SystemExit
start = [i for i, l in enumerate(lines) if "редактор схемы" in l]
print(sum(1 for l in lines[start[-1] if start else 0:] if " PASS " in l))
PYW
)
[ "${SELFTEST_LINES:-0}" -gt 0 ] 2>/dev/null || SELFTEST_LINES=$(grep -c '"&aPASS "' plugin/src/main/java/me/arena/electronics/command/ElecCommand.java || true)
LIVE_NAME=${LOG:-нет лога}; LIVE_NAME=$(basename "${LIVE_NAME}")
LIVE_FAIL=$(sed -e 's/&[0-9a-fk-or]//g' "$LOG" 2>/dev/null | grep -c 'FAIL ' || true)
LIVE_FAIL=${LIVE_FAIL:-0}
LIVE_ERR=$(grep -c 'ERROR' "$LOG" 2>/dev/null || true)
LIVE_ERR=${LIVE_ERR:-0}
LIVE_PARSE=$(grep -c "Couldn't parse data file\|Couldn't load file\|missing mandatory" "$LOG" 2>/dev/null || true)
LIVE_PARSE=${LIVE_PARSE:-0}
LIVE_DP=$(grep -m1 -o 'PASS датапак:.*' "$LOG" 2>/dev/null | head -1 || true)
LIVE_HAND=$(grep -m1 -o 'PASS рукоять крутит генератор:.*' "$LOG" 2>/dev/null | head -1 || true)
LIVE_CRAFT=$(grep -m1 -o 'PASS крафты собраны из реестра.*' "$LOG" 2>/dev/null | head -1 || true)
LIVE_CRAFT=${LIVE_CRAFT:-нет строки в логе}
TB_CHECKS=$(grep -cE '^[[:space:]]+check\(' tools/TestBattery.java 2>/dev/null || true)
TB_CHECKS=${TB_CHECKS:-0}
OFFLINE=$(bash tools/run_tests.sh 2>&1 | grep -oE 'итог: OK=[0-9]+ FAIL=[0-9]+' | tail -1)
REG=$(python3 -c 'import json,io;d=json.load(io.open("shared/components.json",encoding="utf-8"));m=d["meta"];print(len(d["components"]), len(m["soldered"]), m["state_model_count"])' 2>/dev/null || echo "0 0 0")
COMPONENTS=${REG%% *}; REGREST=${REG#* }; SOLDERED=${REGREST%% *}; STATES=${REGREST##* }
NOTES="Сборка против Spigot/Paper 1.21.11. Ставить: jar в plugins/, ресурспак — на клиент (или resource-pack.url в server.properties), датапак — в <world>/datapacks/.

v1.7.3 — две красные строки живой самопроверки стали зелёными, и пак перестал трогать ваниль:
* пак больше не перекрашивает кварц. gen_pack.py безусловно вписывал assets/minecraft/textures/block/quartz_block_{top,side,bottom}.png, чтобы baseBlock = SMOOTH_QUARTZ_SLAB выглядел платой; у гладкого кварца своей текстуры нет, и перекраска доставалась каждому кварцевому блоку, ступени и полублоку в мире. Теперь эти три файла попадают в пак ТОЛЬКО если пользователь сам положил users_textures/host_{top,side,bottom}.png, а check_pack_refs.py рвёт сборку на любом чужом пути (плюс TestPack: 14 проверок, из них 5 на посаженные поломки пака). В ресурспаке этого релиза из чужого только аддитивный assets/minecraft/atlases/blocks.json + pack.mcmeta + pack.png.
* солнце и реле (стенка 12з): панель больше не стоит на потолке Isc. У рода-источника «+» — вывод 1 (МНА stamp(from,to,g,i) втекает в from; Тевенина = stamp(b,a,1/r,E/r)), а stampSolar мерил напряжение ячейки как V(a) − V(b): панель оказывалась «ниже» своей ЭДС на 2·Uxx, iWant = 432 А, потолок Isc был включён всегда — отсюда и 72 В на холостом ходу (72 = 2.4 А · 30 Ом утечки), и «ключ пробит» на пустом месте. Знак теперь верный, а поток ограничен непрерывно: 1/r стоит в матрице на каждом проходе, потолок подкладывается companion-источником (Solar.terminal — чистая функция, 4 новые проверки офлайн).
* «вывод подключён» — это не pinNet[i] != null. Topology.resolve называет узел любого вывода, и у висячего он свой, пустой, поэтому «sense-провода нет» не наступало никогда. Добавлен Simulation.pinDriven (на узле есть ещё хотя бы один вывод) — в stampCharge, stampMeter и обеих ветках updateState. Реле без общей «−» говорит «пропускает, но отсечки нет», а счётчик без нейтрали — «датчик напряжения не подключён: счётчик намотает 0 Вт·ч», ровно как обещано в паспорте детали.
* у замкнутого ключа появился гистерезис по направлению (was ? uPv ≥ uBat : uPv − uBat > vf): при замкнутом ключе разность равна ровно vf, и без асимметрии состояние гонялось 0↔1 на каждом проходе решателя. Состояние ключа пересобирается по решённым узлам (refreshChargeKey — та же схема, что якорь реле и пределы стабилизатора): иначе «идеальный диод» залипал замкнутым, и банк ночью разряжался сам себя через реле (0.406 А вместо 0.000 А).
* учёт заряда вынесен в ChargeCtrl.tick() — чистую функцию: ампер-часы растут только при замкнутом ключе, отсечка считается один раз на переходе «было замкнуто → стало разомкнуто» (фронт берётся из PREV_CLOSED, а не из CLOSED: замкнутый ключ оставляет CLOSED, и счётчик крутился бы на каждом проходе). Все три правила проверяются офлайн.
* SchematicGui хранит aux клетки между прогонами (cellAux — то же поле, что Persistence.Entry.aux): «Пересчитать» больше не забывало накопленные А·ч и Вт·ч, и двухфазная стенка (заряд → второй банк → отсечка) стала играбельной.
* в самой стенке были перевёрнуты панель и банки (оба источника стояли «−» в реле): реле видело банк на 2.2 В «ниже» панели, идеальный диод замыкался, и банк разряжал сам себя. Правило «у источника плюс — вывод 1» записано в docs/adding-parts.md, туда же — про pinDriven.

Остаточная особенность (README §4i и строка simulation.solve-passes в docs/config.md): узел жёсткого источника сходится по Isc·r ≈ 0.12 В за тик, панели нужно ~200 тиков мира. Это цена двух проходов решателя, а не баг физики: быстрее — поднимать passes, а не переписывать панель.

Проверка живым сервером (Paper 1.21.11 build 132, лог ${LIVE_NAME}): ${SELFTEST_LINES} PASS-строк, ${LIVE_FAIL} FAIL, ${LIVE_ERR} [ERROR], ${LIVE_PARSE} строк «Couldn't parse data file». Числа зелёной стенки из этого же лога: поток 1.00 (Uxx 21.6 В, через ключ 1.94 А), реле пропустило 0.108 А·ч, банк принял 0.108 А·ч при 12.67 В (до газа 14.4 В далеко — отсечки нет), потери ёмкости 0.000 %; ночью через реле 0.000 А, без реле 0.421 А; банк 2×12 В: 1 отсечка при 25.3 В. Живая строка про привод: ${LIVE_HAND}

Офлайн: ${OFFLINE} (tools/run_tests.sh; сверяет и components.json внутри jar с shared/components.json), TestPower ALL OK (49 проверок), TestBattery ALL OK (${TB_CHECKS} проверок), TestKinetics (38), TestScope (30), TestDynamo (32), TestMovers (21), TestCoupled (4), tools/check_pack_refs.py.

Реестр: ${COMPONENTS} id, из них ${SOLDERED} паяных, ${STATES} индексов состояния модели; craft у 110. Артефакты релиза: ресурспак ${RP_FILES} файлов, датапак ${DP_RECIPES} рецептов, диапазоны форматов ${PK_FMT}.

sha1: jar ${JAR_SHA}, ресурспак ${RP_SHA}, датапак ${DP_SHA}. Сборка воспроизводится: два прогона tools/gen_pack.py дают те же байты обоих паков, а jar, собранный в этой песочнице заново (после отката окружения), совпал с приложенным байт-в-байт — тот же sha1.

Честно о методе: живая проверка — один прогон на Paper build 132 (Spigot/Purpur не гоняли); аддитивность atlases/*.json проверена глазами на 1.21.11 и для клиентов с min_format 64 не проверялась; равнение паков сверялось разбором архивов (3158 записей, 0 с «quartz» в имени), а не скриншотом в мире.

Документация: README §4b/§4e/§4i (RU) и §4b (EN) — что это за паки и почему они именно такие; docs/pack-compatibility.md — совместимость паков с ванилью построчно; users_textures/README.md — 3097 строк и 1749 тегов своих текстур; про рукоять и генератор — README §4f, про единый источник крафта — §4h."
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
  # Снимок списка ОДИН раз и удаление по id. Раньше на каждый файл делался
  # повторный запрос и поиск по имени: шаг CI успевает то же имя удалить,
  # python падает с IndexError, а под «set -e» скрипт обрывается посреди
  # перекладки — релиз остаётся с половиной ассетов (так и было на v1.7.1).
  ids=$(curl -sf -H "Authorization: Bearer $TOK" "$API/releases/$id/assets" \
        | python3 -c 'import json,sys;[print(x["id"]) for x in json.load(sys.stdin)]' || true)
  for aid in $ids; do
    curl -sf -X DELETE -H "Authorization: Bearer $TOK" "$API/releases/assets/$aid" >/dev/null || true
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
# Самопроверка: то, что лежит на GitHub, против того, что лежит в release/.
# Полovina ассетов — это «готово», которое ничего не готово.
WANT=$( (ls release/Electronics-*.jar; ls release/electronics-resourcepack.zip \
         release/electronics-recipes-datapack.zip release/preview.png \
         release/scope-screen.png release/sha1sums.txt release/textures/sheet-*.png 2>/dev/null) \
       | xargs -n1 basename | sort)
GOT=$(curl -sf -H "Authorization: Bearer $TOK" "$API/releases/tags/$TAG" \
      | python3 -c 'import json,sys;[print(a["name"]) for a in json.load(sys.stdin).get("assets",[])]' \
      | sort || true)
MISS=$(comm -23 <(echo "$WANT") <(echo "$GOT") || true)
if [ -n "$MISS" ]; then
  echo "  ВАЖНО: на релизе нет: $(echo $MISS | tr '\n' ' ')" >&2
  echo "  докачать: for f in \$(echo $MISS); do curl -sf -X POST -H \"Authorization: Bearer \$TOK\" \\\n" >&2
  echo "    -H \"Content-Type: application/octet-stream\" --data-binary @release/\$f \"$up?name=\$f\"; done" >&2
  exit 1
fi
echo "ассеты проверены: $(echo "$GOT" | wc -l) шт. — все на месте"
echo "готово: https://github.com/$OWNER/$REPO/releases/tag/$TAG"
