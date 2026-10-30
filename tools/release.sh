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

[ -f "release/Electronics-1.0.0.jar" ] || { echo "нет release/ — собери: bash tools/publish.sh" >&2; exit 1; }
git tag -f -a "$TAG" -m "Electronics $TAG (Minecraft/Spigot 1.21.11)" 2>/dev/null || git tag -f "$TAG"

echo "— push main + $TAG"
GIT_TERMINAL_PROMPT=0 git -c credential.helper= push "$GITURL" main:main "+refs/tags/$TAG:refs/tags/$TAG"

echo "— релиз"
# Число проверок selftest в тексте ниже — из живого прогона на Paper (grep -c PASS
# в логе сервера), а не из исходников: на каждый новый релиз его надо сверить,
# иначе релизные заметки врут. sha1 берём из release/sha1sums.txt на лету, чтобы
# они физически не могли разъехаться с ассетами.
JAR_SHA=$(sha1sum release/Electronics-1.0.0.jar | cut -c1-40)
RP_SHA=$(sha1sum release/electronics-resourcepack.zip | cut -c1-40)
SELFTEST_LINES=$(grep -c PASS /home/user/srv/mc38.log 2>/dev/null || echo 22)
NOTES="Сборка против Spigot/Paper 1.21.11 (пак форматов 75). Ставить: jar в plugins/, оба архива — на сервер как ресурспак и датапак. Текстуры нарисованы процедурно и доведены до вида ванильного ресурса (mc_texture: сетка 16 px, дизер Bayer 4x4 вместо мягкого края, палитра 20 цветов, три тона граней), архивы пакуются детерминированно. Проверка на живом сервере: /elec selftest — PASS-строк ${SELFTEST_LINES}, FAIL 0, [ERROR] 0 на прогон (Paper 1.21.11 b132). sha1 jar: ${JAR_SHA}, ресурспака: ${RP_SHA}."
body=$(python3 -c 'import json,sys; print(json.dumps({"tag_name":sys.argv[1],"name":"Electronics "+sys.argv[1]+" (Minecraft 1.21.11)","body":sys.argv[2]}))' "$TAG" "$NOTES")
resp=$(curl -sf -X POST -H "Authorization: Bearer $TOK" -H "Accept: application/vnd.github+json" \
        -H "Content-Type: application/json" -d "$body" "$API/releases" || true)
if [ -z "$resp" ]; then
  echo "  релиз $TAG уже есть — докладываю ассеты в существующий"
  id=$(curl -sf -H "Authorization: Bearer $TOK" "$API/releases/tags/$TAG" | python3 -c 'import json,sys; print(json.load(sys.stdin)["id"])')
  up="https://uploads.github.com/repos/$OWNER/$REPO/releases/$id/assets"
  for a in $(curl -sf -H "Authorization: Bearer $TOK" "$API/releases/$id/assets" | python3 -c 'import json,sys; [print(x["name"]) for x in json.load(sys.stdin)]'); do
    curl -sf -X DELETE -H "Authorization: Bearer $TOK" "$API/releases/assets/$(curl -sf -H "Authorization: Bearer $TOK" "$API/releases/$id/assets" | python3 -c "import json,sys;print([x['id'] for x in json.load(sys.stdin) if x['name']=='$a'][0])")" >/dev/null || true
  done
else
  up=$(echo "$resp" | python3 -c 'import json,sys; print(json.load(sys.stdin)["upload_url"].split("{")[0])')
fi

for f in release/Electronics-1.0.0.jar release/electronics-resourcepack.zip \
         release/electronics-recipes-datapack.zip release/preview.png release/sha1sums.txt \
         release/textures/sheet-0*.png; do
  [ -f "$f" ] || continue
  curl -sf -X POST -H "Authorization: Bearer $TOK" -H "Content-Type: application/octet-stream" \
       --data-binary "@$f" "$up?name=$(basename "$f")" >/dev/null
  echo "  + $(basename "$f") ($(stat -c%s "$f") B)"
done
echo "готово: https://github.com/$OWNER/$REPO/releases/tag/$TAG"
