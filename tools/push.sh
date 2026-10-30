#!/usr/bin/env bash
# Быстрый пуш в GitHub без спроса: main, а если передан тег — ещё тег + релиз.
#
# Зачем: пользователь просил не спрашивать токен каждый раз. Токен лежит в
# ~/.secrets/github.env — ВНЕ репозитория, поэтому он не может ни
# закоммититься, ни уехать в пуш. Файл читается только отсюда.
#
# usage: bash tools/push.sh [v1.3.2]
#   без аргумента — только main (+ теги, если они есть локально и их нет на GitHub? нет:
#                       только main, теги не трогаем)
#   с аргументом  — main + тег + релиз с ассетами (делегает в release.sh)
set -euo pipefail
cd "$(dirname "$0")/.."

SECRETS="${ELECTRONICS_SECRETS:-$HOME/.secrets/github.env}"
if [ -z "${GITHUB_TOKEN:-}" ] && [ -f "$SECRETS" ]; then
  # shellcheck disable=SC1090
  . "$SECRETS"
  GITHUB_TOKEN="${GH_TOKEN:-}"
  GH_LOGIN="${GH_LOGIN:-amir2013shai-byte}"
  GH_REPO="${GH_REPO:-electronics-mc}"
fi
GH_LOGIN="${GH_LOGIN:-amir2013shai-byte}"
GH_REPO="${GH_REPO:-electronics-mc}"
if [ -z "${GITHUB_TOKEN:-}" ]; then
  echo "нет токена: положи GH_TOKEN в $SECRETS (файл вне репозитория) или экспортируй GITHUB_TOKEN" >&2
  exit 1
fi

# Снимок песочницы не сохраняет .git/config — ни remote, ни identity. Поэтому
# и то, и другое задаём на время команды, а не в конфиг.
git config user.name  >/dev/null 2>&1 || true
NAME="$(git config user.name 2>/dev/null || true)"
MAIL="$(git config user.email 2>/dev/null || true)"
[ -n "$NAME" ] || NAME="$GH_LOGIN"
[ -n "$MAIL" ] || MAIL="$GH_LOGIN@users.noreply.github.com"

URL="https://x-access-token:$GITHUB_TOKEN@github.com/$GH_LOGIN/$GH_REPO.git"
if [ $# -ge 1 ]; then
  exec env GITHUB_TOKEN="$GITHUB_TOKEN" bash tools/release.sh "$1"
fi

git -c "user.name=$NAME" -c "user.email=$MAIL" \
    -c credential.helper= push "$URL" main:main \
    && echo "запушено в $GH_LOGIN/$GH_REPO: $(git rev-parse --short HEAD)"
