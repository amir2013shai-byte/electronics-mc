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
| скачанные jar (spigot-api, joml) | `mvn` дотянет сам; `tools/run_tests.sh` сам ищет их в `$REPO` → `~/.m2/repository` → `/home/user/tools/repo`, а не найдя — зовёт `mvn dependency:build-classpath` |
| сам `tools/maven` (снимок доехал не полностью: нет `boot/plexus-classworlds`, `lib/maven-embedder` → `ClassNotFoundException: org.apache.maven.cli.MavenCli`) | `sudo apt-get install -y maven` (3.9.9, `/usr/bin/mvn`) — проще, чем чинить копию. В этой песочнице `tools/` в итоге удалён совсем: 29 МБ на скачанные jar, которые maven тянет за 10 с |
| `/tmp/*.log` прогоны сервера | они не нужны; результаты зафиксированы в `README.md` |
| `.git/config` (снимок вырезает этот файл как чувствительный) | `git init` заново не нужен, достаточно вернуть конфиг: `printf '[core]\n\trepositoryformatversion = 0\n\tfilemode = true\n\tbare = false\n\tlogallrefupdates = true\n' > .git/config && git remote add origin https://github.com/<owner>/electronics-mc.git` |

Отсюда и репозиторий: `release/` (jar + оба архива + превью + `sha1sums.txt`)
существует именно потому, что `build/` и `target/` не переживают перезапуск
песочницы, а пересобирать артефакты каждый раз — терять детерминизм сборок.

## Порядок действий в свежей песочнице

```bash
# 0. JAVA_HOME + maven (проверено: пересборка занимает ~10 с, сеть не деградировала)
sudo -n apt-get install -y -q openjdk-21-jdk-headless maven
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64

# 1. ресурспак + датапак + превью + артефакты в release/
bash tools/publish.sh

# 2. офлайн-тесты (MNA, топология, ссылки пака)
bash tools/run_tests.sh

# 3. плагин
(cd plugin && mvn -B -U -Dmaven.repo.local=/home/user/tools/repo -DskipTests package)

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

Ожидаемое (проверено на Paper 1.21.11 build 132, v1.3.1): `Загружено компонентов: 100,
состояний модели ресурспака: 2886`,
`зарегистрировано рецептов: 35 (пайка: 61 витрина для JEI, пропущено 7, плавка 3)`
(пропущено 7 — это то, что в верстаке не собирается: слитки, сплавы, шлак и
эмальпровод — их делают станки);
`/elec selftest` печатает 22 строки `PASS` и ни одного `FAIL` (одна строка = одна
проверка числами), в том числе
`PASS геркон: без магнита I = 0.14 мкА, с магнитом I = 20.2 мА`,
`PASS дроссель 10 Гн: шаг 1 -> 13.2 мА, 20 шагов -> 18.7 мА` и
`PASS пайка: … ванильного рецепта светодиода нет: true`
(доказательство, что 61 детали крафтом в верстаке не получаются),
`PASS JEI: витрин 61/(нужно 61), … стол в сетке рецептов: 61/61,
в менеджере рецептов клиента: 96 (35 крафта + 61 витрина)`
— последняя строка доказывает, что JEI увидит все 100 предметов: рецепты лежат
в менеджере, который синхронизируется клиенту, а 57-я клетка сетки отдана
паяльному столу. Защита из v1.2.0 проверяется числами прямо на сервере:
`PASS предохранитель I²t`, `PASS PTC …`, `PASS варистор при 9 В не течёт:
I = 9.00e-09 А`, `PASS 7805: на 330 Ом 4.99 В … коротыш: U = 0 В, I = 1.01 А`,
`PASS синус меняет полярность`, а новые блоки — числами: `PASS недра: жила в 17%
чанков (config 18%) … выбрано 40/40 и жила пуста; печь: 3 рецепта, 2 куска →
слиток (ExactChoice, без датапака)` и `PASS МК: VCC 3.00 В, нога 2 = 2.85 В HIGH,
ток порта 2 мА = току светодиода 2 мА (U_LED 1.87 В)` плюс
`PASS коротыш порта в землю: 40 мА (паспорт 40 мА/нога, не 120), счётчик
«горячих» ног 1; синтаксическая ошибка — чип встаёт`, а металлургия — двумя
строками сразу: `PASS металлургия: 2 Cu + 1 Sn → бронза + шлак ×1, … цементация
Cu ×2 из Zn+Sn, цинк — только возгонкой 4→3; слиток меди → эмальпровод ×2` и
`PASS шунт 0,01 Ом в цепи 9 В с нагрузкой 10 Ом: I = 803 мА, на шунте 8 мВ
(по закону Ома 8 мВ)`.
после сборки — `islands=1 comps=12`, `ammeter I=20.19 мА`,
`led_red U=2.305 В b=1.00`. Перед каждым новым прогоном удаляй
`plugins/Electronics/builds` и `registry.json`, иначе файлы из прошлый раз
перехватят `builds/*.json` у jar-копии.

Jar собирается воспроизводимо: `project.build.outputTimestamp` в `pom.xml`
фиксирует mtime записей архива, поэтому `mvn clean package` дважды даёт
один и тот же sha1 (`cddbe42a0d5674b520cdac144b2cfa9dec031f85`, 239297 байт) —
на этом держится `release/sha1sums.txt`. Оба архива (`build/*.zip`) наоборот
не воспроизводимы: `zip` пишет текущее время, так что их sha1 меняется на
каждой пересборке при неизменном содержимом.

## Если нужно залить в GitHub

Репозиторий живёт здесь: **https://github.com/amir2013shai-byte/electronics-mc**
(ветка `main`, 57 файлов, 1.4 МБ, включая `release/` с артефактами).

```bash
# обычные операции из песочницы
git add -A && git commit -m "..."
git -c credential.helper= push origin main          # если есть права — токен в URL
# или без токена вообще: git bundle create ../b.bundle --all, у себя git fetch ../b.bundle main
```

Токен в remote не записывается: `git remote -v` должен показывать чистый
`https://github.com/...` без `user:token@`. Проверка: `grep -c github_pat .git/config` → 0.
Токен для пуша лежит в `~/.secrets/github.env` — **вне** репозитория (`git` до него
не дотягивается), и `.secrets/` на всякий случай в `.gitignore`. `tools/push.sh`
и `tools/release.sh` берут его оттуда сами, поэтому каждый раз вставлять ключ не
нужно: `bash tools/push.sh` — main, `bash tools/push.sh v1.3.2` — main + тег + релиз.
