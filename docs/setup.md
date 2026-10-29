# Пересборка окружения в песочнице (и где что лежит)

Это заметка «как вернуть всё к жизни», а не описание плагина — он в `README.md`.

## Что переживает границу хода, а что нет

Песочница Arena при каждом новом сообщении разворачивает снимок рабочего
каталога. Снимок ограничен ~128 МБ / 10 000 файлов, и из него **исключены**
каталоги `build/`, `target/`, `dist/`, `.cache/`, `.venv/`, `node_modules/`,
`__pycache__/`. Кроме того, снимок не хранит права файлов и всё, что лежит вне
`/home/user`. Практически это значит:

| пропадает после нового хода | как вернуть |
|---|---|
| `electronics/build/`, `plugin/target/` | `bash tools/publish.sh` (артефакты кладёт в `release/`) |
| `/usr/lib/jvm/java-21-openjdk-amd64` | `sudo apt-get install -y openjdk-21-jdk-headless` |
| бит `+x` у `tools/maven/bin/mvn` | `chmod +x tools/maven/bin/*` |
| скачанные jar в `tools/repo` (spigot-api, joml) | `mvn` дотянет сам, если дать `-Dmaven.repo.local=/home/user/tools/repo` |
| `/tmp/*.log` прогоны сервера | они не нужны; результаты зафиксированы в `README.md` |

Отсюда и репозиторий: `release/` (jar + оба архива + превью + `sha1sums.txt`)
существует именно потому, что `build/` и `target/` не переживают перезапуск
песочницы, а пересобирать артефакты каждый раз — терять детерминизм сборок.

## Порядок действий в свежей песочнице

```bash
# 0. JAVA_HOME/maven
sudo -n apt-get install -y -q openjdk-21-jdk-headless
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export PATH=$JAVA_HOME/bin:/home/user/tools/maven/bin:$PATH
chmod +x /home/user/tools/maven/bin/*

# 1. ресурспак + датапак + превью + артефакты в release/
bash tools/publish.sh

# 2. офлайн-тесты (MNA, топология, ссылки пака)
bash tools/run_tests.sh

# 3. плагин
(cd plugin && mvn -B -Dmaven.repo.local=/home/user/tools/repo -DskipTests package)

# 4. (.gitignore не трогать) srv/ в репозиторий не входит
```

## Тестовый сервер

Тестовый сервер лежит **вне** репозитория, в `/home/user/srv` (Paper весит
53 МБ — в git его тащить не нужно). Если его снесли:

```bash
mkdir -p /home/user/srv && cd /home/user/srv
curl -L -o paper.jar https://api.papermc.io/v2/projects/paper/versions/1.21.11/builds/132/downloads/paper-1.21.11-132.jar
# v2-эндпоинт отдаёт {"ok":false,"error":"sunset"} — берём адрес из v3:
curl -s https://fill.papermc.io/v3/projects/paper/versions/ver/1.21.11/builds/latest \
  | python3 -c 'import json,sys; d=json.load(sys.stdin)["build"]["downloads"]["server:default"]; print(d["url"], d["sha256"], sep="\n")'
```

Конфигурация: `server.properties` — flat-мир, `online-mode: false`;
`plugins/Electronics/{config.yml,registry.json,builds,labs}` создаёт сам плагин.

Прогон без игрока (команды с консоли, UTF-8 обязателен):

```bash
cd /home/user/srv && rm -rf plugins/Electronics/builds plugins/Electronics/registry.json && \
(sleep 30; echo "elec build led_basic world 20 70 20"; sleep 4; \
 echo "elec build rc_blink world 40 70 40"; sleep 5; \
 echo "elec sim 40"; echo "elec dump world 40"; sleep 2; echo "stop") | \
 /usr/lib/jvm/java-21-openjdk-amd64/bin/java -Xms512M -Xmx900M \
   -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -jar paper.jar --nogui 2>&1 | tee /tmp/srv.log
```

Ожидаемое (проверено на Paper 1.21.11 build 132): `Загружено компонентов: 52,
состояний модели ресурспака: 1338`, `registered recipes: 51 (пропущено 1)`,
после сборки — `islands=1 comps=12`, `ammeter I=20.19 мА`,
`led_red U=2.305 В b=1.00`. Перед каждым новым прогоном удаляй
`plugins/Electronics/builds` и `registry.json`, иначе файлы из прошлый раз
перехватят `builds/*.json` у jar-копии.

## Если нужно залить в GitHub

```bash
git init -b main && git add -A && git commit -m "..."   # локально — уже сделано
# дальше либо git push https://github.com/<owner>/<repo>.git main
# либо (без токена в песочнице) pull этого bundle у себя: git bundle → git fetch
```
