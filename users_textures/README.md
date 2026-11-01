# Теги текстур Electronics — что и как назвать, чтобы пак подхватил

Этот файл **сгенерирован**: `python3 tools/user_tags.py doc`. Руками его не
правят — правят `shared/components.json` и `tools/gen_pack.py`, а список тегов
пересобирают. Причина простая: правила имён берёт сам генератор
(`gen_pack.user_candidates()`), и документ читает их оттуда же, поэтому
расхождение «в доке одно, в сборке другое» невозможно по построению.

Сейчас в реестре **114** компонентов, у них в сумме
**565** визуальных состояний и
**1695** текстурных тегов (состояние × грань) — ровно столько же
файлов кладёт генератор: 1695 PNG в `textures/item/`.

## Как положить свою текстуру

1. Положите PNG в `users_textures/` (рядом с этим файлом) с именем-**тегом** из
   таблицы ниже.
2. `bash tools/user_pack.sh` — пересоберётся пак, `build/preview.png`, листы.
3. Готовый архив — `build/electronics-resourcepack.zip`; в `release/` он окажется
   только после `bash tools/user_pack.sh --release`.

Генератор ищет файл по четырём вариантам имени — от частного к общему — и
берёт первый найденный:

| приоритет | имя | что заменяет |
|---|---|---|
| 1 | `<id>_<состояние>_<грань>.png` | ровно один слот, например `led_red_on_top.png` |
| 2 | `<id>_<состояние>.png` | то же для `top` (для `side`/`bottom` не работает) |
| 3 | `<id>_<грань>.png` | грань сразу для всех состояний |
| 4 | `<id>.png` или `<id>_top.png` | **канва**: генератор сам выведет из неё остальные пять состояний, боковину и низ |

Приоритет 4 — это и есть ответ на «сделай все остальные сам»: вы рисуете один
вид сверху, а `on`/`dim`/`burnt`/`rev`/`closed`, боковину и низ генератор
посчитает по вашим пикселям. Что именно он делает с состоянием:

| состояние | суффикс файла | как выводится |
|---|---|---|
| `off` | *без суффикса* | базовый вид: канва как есть |
| `dim` | `_dim` | полусвет: подсветка слабее, чем у on |
| `on` | `_on` | активное: подмешивается glow_color (у LED и ламп сильнее) и гало |
| `burnt` | `_burnt` | сгоревшее: копоть + тёмные трещины-штрихи |
| `rev` | `_rev` | обратная полярность/реверс: красный крест |
| `closed` | `_closed` | замкнуто: зелёная перемычка |

И отдельно:

* **боковина** — `side_from_top()`: вырезается полоса `(0,12)–(32,28)` из вида
  сверху, тянется в 32×32 только NEAREST, низ затемняется тоном палитры ×0.55,
  на `on` подмешивается 30 % свечения, на `burnt` — 72 % сажи; альфа берётся из
  исходника, поэтому силуэт не «поедет»;
* **низ** — `bottom_texture()`: тон палитры ×0.5 и четыре «ножки».

### Формат и ловушки

* размер **32×32** (наш процедурный масштаб). 16×16 тоже примем — растянется
  NEAREST в 2 раза, пиксели лягут ровно по 2×2; 48×48 и прочее дробится,
  так что лучше 16 или 32;
* PNG с альфой. Модель рендерится с `minecraft:cutout`: пиксели с
  **α < 128 не рисуются вообще**, полупрозрачных краёв не будет — либо α=255,
  либо α=0 (`tools/user_tags.py check` считает, сколько пикселей попадёт под
  отсечение);
* ваши файлы **не** проходят через `mc_texture()` (дизер Bayer, палитра 20
  цветов, три тона граней) — иначе вы получили бы не то, что нарисовали. Из
  вашей канвы выводятся состояния, но и они красятся пиксельными операциями,
  а не переквантовываются;
* `users_textures/_examples/` и любые подпапки не сканируются: генератор
  смотрит ровно один уровень, поэтому пример там и лежит, пока вы его
  не скопировали наверх (`python3 tools/user_tags.py example`).

### Блок-основа («плата»)

Компоненты стоят на ванильном `minecraft:smooth_quartz_slab`; наш пак
перекрашивает его текстуры (`quartz_block_top/side/bottom`) в зелёную плату.
Если хотите свою — теги:

| файл | размер | что это |
|---|---|---|
| `users_textures/host_top.png` | 16×16 | верх «платы»-основы |
| `users_textures/host_side.png` | 16×16 | боковина «платы» |
| `users_textures/host_bottom.png` | 16×16 | низ «платы» |

## Ванильные текстуры и из чего что сделано

Наши детали — не перекраска ванильных предметов: их рисует генератор по
`shape`/`size`/`palette`. Ванильное в паке только то, на что деталь опирается:

* **блок-основа** — `minecraft:smooth_quartz_slab`, её PNG в ваниле —
  `quartz_block_top/side/bottom` (мы их перезаписываем; теги `host_*` выше);
* **ингредиенты рецепта** — обычные ванильные предметы, они перечислены у
  каждого компонента в таблице ниже. Где у предмета текстура: ванильная модель
  `minecraft/models/item/<name>.json` почти всегда `minecraft:item/generated`
  со слоем `minecraft:item/<name>` → `assets/minecraft/textures/item/<name>.png`;
  у блочных ингредиентов (`glass`, `stone`, `calcite`, `sand`, `*_block`) модель
  ссылается на `assets/minecraft/textures/block/…` — берите цветотделку оттуда;
* путь к конкретному PNG ванили здесь **не** проверялся: в песочнице нет
  клиентских ассетов Minecraft. Сверяйте по `versions/<версия>/<версия>.jar`
  или в игре F3+A.

Через паяльный стол идут **69** id — у них в датапаке рецепта нет,
а список ингредиентов ниже генератор берёт из `RECIPES` — он обязан
совпадать с `Recipes.DEFS` на стороне плагина, и `selftest` это проверяет.

## Все теги по компонентам

Порядок — как в `shared/components.json`; `индекс` — с какого номера
состояния модели начинается этот компонент (`/elec model <id> <сост> <ориент>`);
всего индексов в реестре — **3390** =
565 × 6 ориентаций.

### `bench` — Рабочий стол

* по-английски: Workbench Plate
* kind `BOARD`, shape `plate`, размер [15, 1, 15], состояний 1, индексы моделей 0…5
* палитра процедурной отрисовки: `#3f6b52`, `#7fd8a4`, `#cfd6db`
* рецепт: `paper`×1, `repeater`×1

| положить (теги) | вместо | состояние | грань | файл в паке |
|---|---|---|---|---|
| `bench_off_top.png` / `bench_off.png` / `bench_top.png` / `bench.png` | off·top | `off` | `top` | `textures/item/bench_top.png` |
| `bench_off_side.png` / `bench_side.png` | off·side | `off` | `side` | `textures/item/bench_side.png` |
| `bench_off_bottom.png` / `bench_bottom.png` | off·bottom | `off` | `bottom` | `textures/item/bench_bottom.png` |

(состояний один набор; адреса остальных пяти состояний существуют —
плагин может попросить любой из шести — и ведут на ту же модель)

### `breadboard` — Макетная плата

* по-английски: Breadboard
* kind `BREADBOARD`, shape `breadboard`, размер [16, 3, 16], состояний 6, индексы моделей 6…41
* палитра процедурной отрисовки: `#dfe6e9`, `#b2bec3`, `#636e72`
* рецепт: `hopper`×2, `redstone`×1, `repeater`×6

| положить (теги) | вместо | состояние | грань | файл в паке |
|---|---|---|---|---|
| `breadboard_off_top.png` / `breadboard_off.png` / `breadboard_top.png` / `breadboard.png` | off·top | `off` | `top` | `textures/item/breadboard_top.png` |
| `breadboard_off_side.png` / `breadboard_side.png` | off·side | `off` | `side` | `textures/item/breadboard_side.png` |
| `breadboard_off_bottom.png` / `breadboard_bottom.png` | off·bottom | `off` | `bottom` | `textures/item/breadboard_bottom.png` |
| `breadboard_dim_top.png` / `breadboard_dim.png` | dim·top | `dim` | `top` | `textures/item/breadboard_dim_top.png` |
| `breadboard_dim_side.png` / `breadboard_side.png` | dim·side | `dim` | `side` | `textures/item/breadboard_dim_side.png` |
| `breadboard_dim_bottom.png` / `breadboard_bottom.png` | dim·bottom | `dim` | `bottom` | `textures/item/breadboard_dim_bottom.png` |
| `breadboard_on_top.png` / `breadboard_on.png` | on·top | `on` | `top` | `textures/item/breadboard_on_top.png` |
| `breadboard_on_side.png` / `breadboard_side.png` | on·side | `on` | `side` | `textures/item/breadboard_on_side.png` |
| `breadboard_on_bottom.png` / `breadboard_bottom.png` | on·bottom | `on` | `bottom` | `textures/item/breadboard_on_bottom.png` |
| `breadboard_burnt_top.png` / `breadboard_burnt.png` | burnt·top | `burnt` | `top` | `textures/item/breadboard_burnt_top.png` |
| `breadboard_burnt_side.png` / `breadboard_side.png` | burnt·side | `burnt` | `side` | `textures/item/breadboard_burnt_side.png` |
| `breadboard_burnt_bottom.png` / `breadboard_bottom.png` | burnt·bottom | `burnt` | `bottom` | `textures/item/breadboard_burnt_bottom.png` |
| `breadboard_rev_top.png` / `breadboard_rev.png` | rev·top | `rev` | `top` | `textures/item/breadboard_rev_top.png` |
| `breadboard_rev_side.png` / `breadboard_side.png` | rev·side | `rev` | `side` | `textures/item/breadboard_rev_side.png` |
| `breadboard_rev_bottom.png` / `breadboard_bottom.png` | rev·bottom | `rev` | `bottom` | `textures/item/breadboard_rev_bottom.png` |
| `breadboard_closed_top.png` / `breadboard_closed.png` | closed·top | `closed` | `top` | `textures/item/breadboard_closed_top.png` |
| `breadboard_closed_side.png` / `breadboard_side.png` | closed·side | `closed` | `side` | `textures/item/breadboard_closed_side.png` |
| `breadboard_closed_bottom.png` / `breadboard_bottom.png` | closed·bottom | `closed` | `bottom` | `textures/item/breadboard_closed_bottom.png` |

### `wire_red` — Провод (красный)

* по-английски: Wire (red)
* kind `WIRE`, shape `wire`, размер [16, 3, 4], состояний 6, индексы моделей 42…77
* палитра процедурной отрисовки: `#e74c3c`, `#c0392b`
* рецепт: `copper_ingot`×1, `redstone`×1, `string`×1

| положить (теги) | вместо | состояние | грань | файл в паке |
|---|---|---|---|---|
| `wire_red_off_top.png` / `wire_red_off.png` / `wire_red_top.png` / `wire_red.png` | off·top | `off` | `top` | `textures/item/wire_red_top.png` |
| `wire_red_off_side.png` / `wire_red_side.png` | off·side | `off` | `side` | `textures/item/wire_red_side.png` |
| `wire_red_off_bottom.png` / `wire_red_bottom.png` | off·bottom | `off` | `bottom` | `textures/item/wire_red_bottom.png` |
| `wire_red_dim_top.png` / `wire_red_dim.png` | dim·top | `dim` | `top` | `textures/item/wire_red_dim_top.png` |
| `wire_red_dim_side.png` / `wire_red_side.png` | dim·side | `dim` | `side` | `textures/item/wire_red_dim_side.png` |
| `wire_red_dim_bottom.png` / `wire_red_bottom.png` | dim·bottom | `dim` | `bottom` | `textures/item/wire_red_dim_bottom.png` |
| `wire_red_on_top.png` / `wire_red_on.png` | on·top | `on` | `top` | `textures/item/wire_red_on_top.png` |
| `wire_red_on_side.png` / `wire_red_side.png` | on·side | `on` | `side` | `textures/item/wire_red_on_side.png` |
| `wire_red_on_bottom.png` / `wire_red_bottom.png` | on·bottom | `on` | `bottom` | `textures/item/wire_red_on_bottom.png` |
| `wire_red_burnt_top.png` / `wire_red_burnt.png` | burnt·top | `burnt` | `top` | `textures/item/wire_red_burnt_top.png` |
| `wire_red_burnt_side.png` / `wire_red_side.png` | burnt·side | `burnt` | `side` | `textures/item/wire_red_burnt_side.png` |
| `wire_red_burnt_bottom.png` / `wire_red_bottom.png` | burnt·bottom | `burnt` | `bottom` | `textures/item/wire_red_burnt_bottom.png` |
| `wire_red_rev_top.png` / `wire_red_rev.png` | rev·top | `rev` | `top` | `textures/item/wire_red_rev_top.png` |
| `wire_red_rev_side.png` / `wire_red_side.png` | rev·side | `rev` | `side` | `textures/item/wire_red_rev_side.png` |
| `wire_red_rev_bottom.png` / `wire_red_bottom.png` | rev·bottom | `rev` | `bottom` | `textures/item/wire_red_rev_bottom.png` |
| `wire_red_closed_top.png` / `wire_red_closed.png` | closed·top | `closed` | `top` | `textures/item/wire_red_closed_top.png` |
| `wire_red_closed_side.png` / `wire_red_side.png` | closed·side | `closed` | `side` | `textures/item/wire_red_closed_side.png` |
| `wire_red_closed_bottom.png` / `wire_red_bottom.png` | closed·bottom | `closed` | `bottom` | `textures/item/wire_red_closed_bottom.png` |

### `wire_black` — Провод (чёрный)

* по-английски: Wire (black)
* kind `WIRE`, shape `wire`, размер [16, 3, 4], состояний 6, индексы моделей 78…113
* палитра процедурной отрисовки: `#2d3436`, `#1e272e`
* рецепт: `copper_ingot`×1, `ink_sac`×1, `string`×1

| положить (теги) | вместо | состояние | грань | файл в паке |
|---|---|---|---|---|
| `wire_black_off_top.png` / `wire_black_off.png` / `wire_black_top.png` / `wire_black.png` | off·top | `off` | `top` | `textures/item/wire_black_top.png` |
| `wire_black_off_side.png` / `wire_black_side.png` | off·side | `off` | `side` | `textures/item/wire_black_side.png` |
| `wire_black_off_bottom.png` / `wire_black_bottom.png` | off·bottom | `off` | `bottom` | `textures/item/wire_black_bottom.png` |
| `wire_black_dim_top.png` / `wire_black_dim.png` | dim·top | `dim` | `top` | `textures/item/wire_black_dim_top.png` |
| `wire_black_dim_side.png` / `wire_black_side.png` | dim·side | `dim` | `side` | `textures/item/wire_black_dim_side.png` |
| `wire_black_dim_bottom.png` / `wire_black_bottom.png` | dim·bottom | `dim` | `bottom` | `textures/item/wire_black_dim_bottom.png` |
| `wire_black_on_top.png` / `wire_black_on.png` | on·top | `on` | `top` | `textures/item/wire_black_on_top.png` |
| `wire_black_on_side.png` / `wire_black_side.png` | on·side | `on` | `side` | `textures/item/wire_black_on_side.png` |
| `wire_black_on_bottom.png` / `wire_black_bottom.png` | on·bottom | `on` | `bottom` | `textures/item/wire_black_on_bottom.png` |
| `wire_black_burnt_top.png` / `wire_black_burnt.png` | burnt·top | `burnt` | `top` | `textures/item/wire_black_burnt_top.png` |
| `wire_black_burnt_side.png` / `wire_black_side.png` | burnt·side | `burnt` | `side` | `textures/item/wire_black_burnt_side.png` |
| `wire_black_burnt_bottom.png` / `wire_black_bottom.png` | burnt·bottom | `burnt` | `bottom` | `textures/item/wire_black_burnt_bottom.png` |
| `wire_black_rev_top.png` / `wire_black_rev.png` | rev·top | `rev` | `top` | `textures/item/wire_black_rev_top.png` |
| `wire_black_rev_side.png` / `wire_black_side.png` | rev·side | `rev` | `side` | `textures/item/wire_black_rev_side.png` |
| `wire_black_rev_bottom.png` / `wire_black_bottom.png` | rev·bottom | `rev` | `bottom` | `textures/item/wire_black_rev_bottom.png` |
| `wire_black_closed_top.png` / `wire_black_closed.png` | closed·top | `closed` | `top` | `textures/item/wire_black_closed_top.png` |
| `wire_black_closed_side.png` / `wire_black_side.png` | closed·side | `closed` | `side` | `textures/item/wire_black_closed_side.png` |
| `wire_black_closed_bottom.png` / `wire_black_bottom.png` | closed·bottom | `closed` | `bottom` | `textures/item/wire_black_closed_bottom.png` |

### `wire_yellow` — Провод (жёлтый)

* по-английски: Wire (yellow)
* kind `WIRE`, shape `wire`, размер [16, 3, 4], состояний 6, индексы моделей 114…149
* палитра процедурной отрисовки: `#f1c40f`, `#d4ac0d`
* рецепт: `copper_ingot`×1, `string`×1, `yellow_dye`×1

| положить (теги) | вместо | состояние | грань | файл в паке |
|---|---|---|---|---|
| `wire_yellow_off_top.png` / `wire_yellow_off.png` / `wire_yellow_top.png` / `wire_yellow.png` | off·top | `off` | `top` | `textures/item/wire_yellow_top.png` |
| `wire_yellow_off_side.png` / `wire_yellow_side.png` | off·side | `off` | `side` | `textures/item/wire_yellow_side.png` |
| `wire_yellow_off_bottom.png` / `wire_yellow_bottom.png` | off·bottom | `off` | `bottom` | `textures/item/wire_yellow_bottom.png` |
| `wire_yellow_dim_top.png` / `wire_yellow_dim.png` | dim·top | `dim` | `top` | `textures/item/wire_yellow_dim_top.png` |
| `wire_yellow_dim_side.png` / `wire_yellow_side.png` | dim·side | `dim` | `side` | `textures/item/wire_yellow_dim_side.png` |
| `wire_yellow_dim_bottom.png` / `wire_yellow_bottom.png` | dim·bottom | `dim` | `bottom` | `textures/item/wire_yellow_dim_bottom.png` |
| `wire_yellow_on_top.png` / `wire_yellow_on.png` | on·top | `on` | `top` | `textures/item/wire_yellow_on_top.png` |
| `wire_yellow_on_side.png` / `wire_yellow_side.png` | on·side | `on` | `side` | `textures/item/wire_yellow_on_side.png` |
| `wire_yellow_on_bottom.png` / `wire_yellow_bottom.png` | on·bottom | `on` | `bottom` | `textures/item/wire_yellow_on_bottom.png` |
| `wire_yellow_burnt_top.png` / `wire_yellow_burnt.png` | burnt·top | `burnt` | `top` | `textures/item/wire_yellow_burnt_top.png` |
| `wire_yellow_burnt_side.png` / `wire_yellow_side.png` | burnt·side | `burnt` | `side` | `textures/item/wire_yellow_burnt_side.png` |
| `wire_yellow_burnt_bottom.png` / `wire_yellow_bottom.png` | burnt·bottom | `burnt` | `bottom` | `textures/item/wire_yellow_burnt_bottom.png` |
| `wire_yellow_rev_top.png` / `wire_yellow_rev.png` | rev·top | `rev` | `top` | `textures/item/wire_yellow_rev_top.png` |
| `wire_yellow_rev_side.png` / `wire_yellow_side.png` | rev·side | `rev` | `side` | `textures/item/wire_yellow_rev_side.png` |
| `wire_yellow_rev_bottom.png` / `wire_yellow_bottom.png` | rev·bottom | `rev` | `bottom` | `textures/item/wire_yellow_rev_bottom.png` |
| `wire_yellow_closed_top.png` / `wire_yellow_closed.png` | closed·top | `closed` | `top` | `textures/item/wire_yellow_closed_top.png` |
| `wire_yellow_closed_side.png` / `wire_yellow_side.png` | closed·side | `closed` | `side` | `textures/item/wire_yellow_closed_side.png` |
| `wire_yellow_closed_bottom.png` / `wire_yellow_bottom.png` | closed·bottom | `closed` | `bottom` | `textures/item/wire_yellow_closed_bottom.png` |

### `wire_blue` — Провод (синий)

* по-английски: Wire (blue)
* kind `WIRE`, shape `wire`, размер [16, 3, 4], состояний 6, индексы моделей 150…185
* палитра процедурной отрисовки: `#3498db`, `#2471a3`
* рецепт: `copper_ingot`×1, `lapis_lazuli`×1, `string`×1

| положить (теги) | вместо | состояние | грань | файл в паке |
|---|---|---|---|---|
| `wire_blue_off_top.png` / `wire_blue_off.png` / `wire_blue_top.png` / `wire_blue.png` | off·top | `off` | `top` | `textures/item/wire_blue_top.png` |
| `wire_blue_off_side.png` / `wire_blue_side.png` | off·side | `off` | `side` | `textures/item/wire_blue_side.png` |
| `wire_blue_off_bottom.png` / `wire_blue_bottom.png` | off·bottom | `off` | `bottom` | `textures/item/wire_blue_bottom.png` |
| `wire_blue_dim_top.png` / `wire_blue_dim.png` | dim·top | `dim` | `top` | `textures/item/wire_blue_dim_top.png` |
| `wire_blue_dim_side.png` / `wire_blue_side.png` | dim·side | `dim` | `side` | `textures/item/wire_blue_dim_side.png` |
| `wire_blue_dim_bottom.png` / `wire_blue_bottom.png` | dim·bottom | `dim` | `bottom` | `textures/item/wire_blue_dim_bottom.png` |
| `wire_blue_on_top.png` / `wire_blue_on.png` | on·top | `on` | `top` | `textures/item/wire_blue_on_top.png` |
| `wire_blue_on_side.png` / `wire_blue_side.png` | on·side | `on` | `side` | `textures/item/wire_blue_on_side.png` |
| `wire_blue_on_bottom.png` / `wire_blue_bottom.png` | on·bottom | `on` | `bottom` | `textures/item/wire_blue_on_bottom.png` |
| `wire_blue_burnt_top.png` / `wire_blue_burnt.png` | burnt·top | `burnt` | `top` | `textures/item/wire_blue_burnt_top.png` |
| `wire_blue_burnt_side.png` / `wire_blue_side.png` | burnt·side | `burnt` | `side` | `textures/item/wire_blue_burnt_side.png` |
| `wire_blue_burnt_bottom.png` / `wire_blue_bottom.png` | burnt·bottom | `burnt` | `bottom` | `textures/item/wire_blue_burnt_bottom.png` |
| `wire_blue_rev_top.png` / `wire_blue_rev.png` | rev·top | `rev` | `top` | `textures/item/wire_blue_rev_top.png` |
| `wire_blue_rev_side.png` / `wire_blue_side.png` | rev·side | `rev` | `side` | `textures/item/wire_blue_rev_side.png` |
| `wire_blue_rev_bottom.png` / `wire_blue_bottom.png` | rev·bottom | `rev` | `bottom` | `textures/item/wire_blue_rev_bottom.png` |
| `wire_blue_closed_top.png` / `wire_blue_closed.png` | closed·top | `closed` | `top` | `textures/item/wire_blue_closed_top.png` |
| `wire_blue_closed_side.png` / `wire_blue_side.png` | closed·side | `closed` | `side` | `textures/item/wire_blue_closed_side.png` |
| `wire_blue_closed_bottom.png` / `wire_blue_bottom.png` | closed·bottom | `closed` | `bottom` | `textures/item/wire_blue_closed_bottom.png` |

### `battery_3` — Батарея 3 В

* по-английски: Battery 3 V
* kind `SOURCE`, shape `battery`, размер [14, 7, 9], состояний 6, индексы моделей 186…221
* палитра процедурной отрисовки: `#27ae60`, `#bdc3c7`, `#e67e22`
* рецепт: `copper_ingot`×2, `honeycomb`×1

| положить (теги) | вместо | состояние | грань | файл в паке |
|---|---|---|---|---|
| `battery_3_off_top.png` / `battery_3_off.png` / `battery_3_top.png` / `battery_3.png` | off·top | `off` | `top` | `textures/item/battery_3_top.png` |
| `battery_3_off_side.png` / `battery_3_side.png` | off·side | `off` | `side` | `textures/item/battery_3_side.png` |
| `battery_3_off_bottom.png` / `battery_3_bottom.png` | off·bottom | `off` | `bottom` | `textures/item/battery_3_bottom.png` |
| `battery_3_dim_top.png` / `battery_3_dim.png` | dim·top | `dim` | `top` | `textures/item/battery_3_dim_top.png` |
| `battery_3_dim_side.png` / `battery_3_side.png` | dim·side | `dim` | `side` | `textures/item/battery_3_dim_side.png` |
| `battery_3_dim_bottom.png` / `battery_3_bottom.png` | dim·bottom | `dim` | `bottom` | `textures/item/battery_3_dim_bottom.png` |
| `battery_3_on_top.png` / `battery_3_on.png` | on·top | `on` | `top` | `textures/item/battery_3_on_top.png` |
| `battery_3_on_side.png` / `battery_3_side.png` | on·side | `on` | `side` | `textures/item/battery_3_on_side.png` |
| `battery_3_on_bottom.png` / `battery_3_bottom.png` | on·bottom | `on` | `bottom` | `textures/item/battery_3_on_bottom.png` |
| `battery_3_burnt_top.png` / `battery_3_burnt.png` | burnt·top | `burnt` | `top` | `textures/item/battery_3_burnt_top.png` |
| `battery_3_burnt_side.png` / `battery_3_side.png` | burnt·side | `burnt` | `side` | `textures/item/battery_3_burnt_side.png` |
| `battery_3_burnt_bottom.png` / `battery_3_bottom.png` | burnt·bottom | `burnt` | `bottom` | `textures/item/battery_3_burnt_bottom.png` |
| `battery_3_rev_top.png` / `battery_3_rev.png` | rev·top | `rev` | `top` | `textures/item/battery_3_rev_top.png` |
| `battery_3_rev_side.png` / `battery_3_side.png` | rev·side | `rev` | `side` | `textures/item/battery_3_rev_side.png` |
| `battery_3_rev_bottom.png` / `battery_3_bottom.png` | rev·bottom | `rev` | `bottom` | `textures/item/battery_3_rev_bottom.png` |
| `battery_3_closed_top.png` / `battery_3_closed.png` | closed·top | `closed` | `top` | `textures/item/battery_3_closed_top.png` |
| `battery_3_closed_side.png` / `battery_3_side.png` | closed·side | `closed` | `side` | `textures/item/battery_3_closed_side.png` |
| `battery_3_closed_bottom.png` / `battery_3_bottom.png` | closed·bottom | `closed` | `bottom` | `textures/item/battery_3_closed_bottom.png` |

### `battery_aa` — Пальчиковая батарейка AA 1.5 В

* по-английски: AA Cell 1.5 V
* kind `SOURCE`, shape `cylinder`, размер [5, 10, 5], состояний 6, индексы моделей 222…257
* палитра процедурной отрисовки: `#2d3436`, `#f1c40f`, `#ecf0f1`
* рецепт: `gold_nugget`×1, `iron_nugget`×1, `redstone`×1

| положить (теги) | вместо | состояние | грань | файл в паке |
|---|---|---|---|---|
| `battery_aa_off_top.png` / `battery_aa_off.png` / `battery_aa_top.png` / `battery_aa.png` | off·top | `off` | `top` | `textures/item/battery_aa_top.png` |
| `battery_aa_off_side.png` / `battery_aa_side.png` | off·side | `off` | `side` | `textures/item/battery_aa_side.png` |
| `battery_aa_off_bottom.png` / `battery_aa_bottom.png` | off·bottom | `off` | `bottom` | `textures/item/battery_aa_bottom.png` |
| `battery_aa_dim_top.png` / `battery_aa_dim.png` | dim·top | `dim` | `top` | `textures/item/battery_aa_dim_top.png` |
| `battery_aa_dim_side.png` / `battery_aa_side.png` | dim·side | `dim` | `side` | `textures/item/battery_aa_dim_side.png` |
| `battery_aa_dim_bottom.png` / `battery_aa_bottom.png` | dim·bottom | `dim` | `bottom` | `textures/item/battery_aa_dim_bottom.png` |
| `battery_aa_on_top.png` / `battery_aa_on.png` | on·top | `on` | `top` | `textures/item/battery_aa_on_top.png` |
| `battery_aa_on_side.png` / `battery_aa_side.png` | on·side | `on` | `side` | `textures/item/battery_aa_on_side.png` |
| `battery_aa_on_bottom.png` / `battery_aa_bottom.png` | on·bottom | `on` | `bottom` | `textures/item/battery_aa_on_bottom.png` |
| `battery_aa_burnt_top.png` / `battery_aa_burnt.png` | burnt·top | `burnt` | `top` | `textures/item/battery_aa_burnt_top.png` |
| `battery_aa_burnt_side.png` / `battery_aa_side.png` | burnt·side | `burnt` | `side` | `textures/item/battery_aa_burnt_side.png` |
| `battery_aa_burnt_bottom.png` / `battery_aa_bottom.png` | burnt·bottom | `burnt` | `bottom` | `textures/item/battery_aa_burnt_bottom.png` |
| `battery_aa_rev_top.png` / `battery_aa_rev.png` | rev·top | `rev` | `top` | `textures/item/battery_aa_rev_top.png` |
| `battery_aa_rev_side.png` / `battery_aa_side.png` | rev·side | `rev` | `side` | `textures/item/battery_aa_rev_side.png` |
| `battery_aa_rev_bottom.png` / `battery_aa_bottom.png` | rev·bottom | `rev` | `bottom` | `textures/item/battery_aa_rev_bottom.png` |
| `battery_aa_closed_top.png` / `battery_aa_closed.png` | closed·top | `closed` | `top` | `textures/item/battery_aa_closed_top.png` |
| `battery_aa_closed_side.png` / `battery_aa_side.png` | closed·side | `closed` | `side` | `textures/item/battery_aa_closed_side.png` |
| `battery_aa_closed_bottom.png` / `battery_aa_bottom.png` | closed·bottom | `closed` | `bottom` | `textures/item/battery_aa_closed_bottom.png` |

### `battery_9` — Крона 9 В

* по-английски: 9 V Battery
* kind `SOURCE`, shape `battery9`, размер [9, 12, 6], состояний 6, индексы моделей 258…293
* палитра процедурной отрисовки: `#2d3436`, `#95a5a6`, `#e67e22`
* рецепт: `copper_ingot`×2, `gold_nugget`×6, `redstone`×1

| положить (теги) | вместо | состояние | грань | файл в паке |
|---|---|---|---|---|
| `battery_9_off_top.png` / `battery_9_off.png` / `battery_9_top.png` / `battery_9.png` | off·top | `off` | `top` | `textures/item/battery_9_top.png` |
| `battery_9_off_side.png` / `battery_9_side.png` | off·side | `off` | `side` | `textures/item/battery_9_side.png` |
| `battery_9_off_bottom.png` / `battery_9_bottom.png` | off·bottom | `off` | `bottom` | `textures/item/battery_9_bottom.png` |
| `battery_9_dim_top.png` / `battery_9_dim.png` | dim·top | `dim` | `top` | `textures/item/battery_9_dim_top.png` |
| `battery_9_dim_side.png` / `battery_9_side.png` | dim·side | `dim` | `side` | `textures/item/battery_9_dim_side.png` |
| `battery_9_dim_bottom.png` / `battery_9_bottom.png` | dim·bottom | `dim` | `bottom` | `textures/item/battery_9_dim_bottom.png` |
| `battery_9_on_top.png` / `battery_9_on.png` | on·top | `on` | `top` | `textures/item/battery_9_on_top.png` |
| `battery_9_on_side.png` / `battery_9_side.png` | on·side | `on` | `side` | `textures/item/battery_9_on_side.png` |
| `battery_9_on_bottom.png` / `battery_9_bottom.png` | on·bottom | `on` | `bottom` | `textures/item/battery_9_on_bottom.png` |
| `battery_9_burnt_top.png` / `battery_9_burnt.png` | burnt·top | `burnt` | `top` | `textures/item/battery_9_burnt_top.png` |
| `battery_9_burnt_side.png` / `battery_9_side.png` | burnt·side | `burnt` | `side` | `textures/item/battery_9_burnt_side.png` |
| `battery_9_burnt_bottom.png` / `battery_9_bottom.png` | burnt·bottom | `burnt` | `bottom` | `textures/item/battery_9_burnt_bottom.png` |
| `battery_9_rev_top.png` / `battery_9_rev.png` | rev·top | `rev` | `top` | `textures/item/battery_9_rev_top.png` |
| `battery_9_rev_side.png` / `battery_9_side.png` | rev·side | `rev` | `side` | `textures/item/battery_9_rev_side.png` |
| `battery_9_rev_bottom.png` / `battery_9_bottom.png` | rev·bottom | `rev` | `bottom` | `textures/item/battery_9_rev_bottom.png` |
| `battery_9_closed_top.png` / `battery_9_closed.png` | closed·top | `closed` | `top` | `textures/item/battery_9_closed_top.png` |
| `battery_9_closed_side.png` / `battery_9_side.png` | closed·side | `closed` | `side` | `textures/item/battery_9_closed_side.png` |
| `battery_9_closed_bottom.png` / `battery_9_bottom.png` | closed·bottom | `closed` | `bottom` | `textures/item/battery_9_closed_bottom.png` |

### `battery_lead_12v` — Свинцовый аккумулятор 12 В / 7 А·ч

* по-английски: Lead-Acid Battery 12 V 7 Ah
* kind `BATTERY`, shape `accu`, размер [12, 10, 8], состояний 6, индексы моделей 294…329, *паяется*
* палитра процедурной отрисовки: `#1e2226`, `#c0392b`, `#dfe6e9`
* рецепт: `black_concrete`×2, `copper_ingot`×2, `glass_bottle`×1, `iron_ingot`×2, `redstone`×1

| положить (теги) | вместо | состояние | грань | файл в паке |
|---|---|---|---|---|
| `battery_lead_12v_off_top.png` / `battery_lead_12v_off.png` / `battery_lead_12v_top.png` / `battery_lead_12v.png` | off·top | `off` | `top` | `textures/item/battery_lead_12v_top.png` |
| `battery_lead_12v_off_side.png` / `battery_lead_12v_side.png` | off·side | `off` | `side` | `textures/item/battery_lead_12v_side.png` |
| `battery_lead_12v_off_bottom.png` / `battery_lead_12v_bottom.png` | off·bottom | `off` | `bottom` | `textures/item/battery_lead_12v_bottom.png` |
| `battery_lead_12v_dim_top.png` / `battery_lead_12v_dim.png` | dim·top | `dim` | `top` | `textures/item/battery_lead_12v_dim_top.png` |
| `battery_lead_12v_dim_side.png` / `battery_lead_12v_side.png` | dim·side | `dim` | `side` | `textures/item/battery_lead_12v_dim_side.png` |
| `battery_lead_12v_dim_bottom.png` / `battery_lead_12v_bottom.png` | dim·bottom | `dim` | `bottom` | `textures/item/battery_lead_12v_dim_bottom.png` |
| `battery_lead_12v_on_top.png` / `battery_lead_12v_on.png` | on·top | `on` | `top` | `textures/item/battery_lead_12v_on_top.png` |
| `battery_lead_12v_on_side.png` / `battery_lead_12v_side.png` | on·side | `on` | `side` | `textures/item/battery_lead_12v_on_side.png` |
| `battery_lead_12v_on_bottom.png` / `battery_lead_12v_bottom.png` | on·bottom | `on` | `bottom` | `textures/item/battery_lead_12v_on_bottom.png` |
| `battery_lead_12v_burnt_top.png` / `battery_lead_12v_burnt.png` | burnt·top | `burnt` | `top` | `textures/item/battery_lead_12v_burnt_top.png` |
| `battery_lead_12v_burnt_side.png` / `battery_lead_12v_side.png` | burnt·side | `burnt` | `side` | `textures/item/battery_lead_12v_burnt_side.png` |
| `battery_lead_12v_burnt_bottom.png` / `battery_lead_12v_bottom.png` | burnt·bottom | `burnt` | `bottom` | `textures/item/battery_lead_12v_burnt_bottom.png` |
| `battery_lead_12v_rev_top.png` / `battery_lead_12v_rev.png` | rev·top | `rev` | `top` | `textures/item/battery_lead_12v_rev_top.png` |
| `battery_lead_12v_rev_side.png` / `battery_lead_12v_side.png` | rev·side | `rev` | `side` | `textures/item/battery_lead_12v_rev_side.png` |
| `battery_lead_12v_rev_bottom.png` / `battery_lead_12v_bottom.png` | rev·bottom | `rev` | `bottom` | `textures/item/battery_lead_12v_rev_bottom.png` |
| `battery_lead_12v_closed_top.png` / `battery_lead_12v_closed.png` | closed·top | `closed` | `top` | `textures/item/battery_lead_12v_closed_top.png` |
| `battery_lead_12v_closed_side.png` / `battery_lead_12v_side.png` | closed·side | `closed` | `side` | `textures/item/battery_lead_12v_closed_side.png` |
| `battery_lead_12v_closed_bottom.png` / `battery_lead_12v_bottom.png` | closed·bottom | `closed` | `bottom` | `textures/item/battery_lead_12v_closed_bottom.png` |

### `power_supply` — Блок питания 0-12 В

* по-английски: Power Supply
* kind `SOURCE`, shape `psu`, размер [15, 7, 12], состояний 6, индексы моделей 330…365
* палитра процедурной отрисовки: `#ecf0f1`, `#c0392b`, `#2d3436`
* рецепт: `copper_bulb`×1, `copper_ingot`×2, `glass`×1, `iron_ingot`×2, `repeater`×3

| положить (теги) | вместо | состояние | грань | файл в паке |
|---|---|---|---|---|
| `power_supply_off_top.png` / `power_supply_off.png` / `power_supply_top.png` / `power_supply.png` | off·top | `off` | `top` | `textures/item/power_supply_top.png` |
| `power_supply_off_side.png` / `power_supply_side.png` | off·side | `off` | `side` | `textures/item/power_supply_side.png` |
| `power_supply_off_bottom.png` / `power_supply_bottom.png` | off·bottom | `off` | `bottom` | `textures/item/power_supply_bottom.png` |
| `power_supply_dim_top.png` / `power_supply_dim.png` | dim·top | `dim` | `top` | `textures/item/power_supply_dim_top.png` |
| `power_supply_dim_side.png` / `power_supply_side.png` | dim·side | `dim` | `side` | `textures/item/power_supply_dim_side.png` |
| `power_supply_dim_bottom.png` / `power_supply_bottom.png` | dim·bottom | `dim` | `bottom` | `textures/item/power_supply_dim_bottom.png` |
| `power_supply_on_top.png` / `power_supply_on.png` | on·top | `on` | `top` | `textures/item/power_supply_on_top.png` |
| `power_supply_on_side.png` / `power_supply_side.png` | on·side | `on` | `side` | `textures/item/power_supply_on_side.png` |
| `power_supply_on_bottom.png` / `power_supply_bottom.png` | on·bottom | `on` | `bottom` | `textures/item/power_supply_on_bottom.png` |
| `power_supply_burnt_top.png` / `power_supply_burnt.png` | burnt·top | `burnt` | `top` | `textures/item/power_supply_burnt_top.png` |
| `power_supply_burnt_side.png` / `power_supply_side.png` | burnt·side | `burnt` | `side` | `textures/item/power_supply_burnt_side.png` |
| `power_supply_burnt_bottom.png` / `power_supply_bottom.png` | burnt·bottom | `burnt` | `bottom` | `textures/item/power_supply_burnt_bottom.png` |
| `power_supply_rev_top.png` / `power_supply_rev.png` | rev·top | `rev` | `top` | `textures/item/power_supply_rev_top.png` |
| `power_supply_rev_side.png` / `power_supply_side.png` | rev·side | `rev` | `side` | `textures/item/power_supply_rev_side.png` |
| `power_supply_rev_bottom.png` / `power_supply_bottom.png` | rev·bottom | `rev` | `bottom` | `textures/item/power_supply_rev_bottom.png` |
| `power_supply_closed_top.png` / `power_supply_closed.png` | closed·top | `closed` | `top` | `textures/item/power_supply_closed_top.png` |
| `power_supply_closed_side.png` / `power_supply_side.png` | closed·side | `closed` | `side` | `textures/item/power_supply_closed_side.png` |
| `power_supply_closed_bottom.png` / `power_supply_bottom.png` | closed·bottom | `closed` | `bottom` | `textures/item/power_supply_closed_bottom.png` |

### `solar_cell` — Солнечная батарея

* по-английски: Solar Cell
* kind `SOLAR`, shape `plate`, размер [15, 3, 15], состояний 6, индексы моделей 366…401, *паяется*
* палитра процедурной отрисовки: `#1b4f72`, `#2e86c1`, `#aed6f1`
* рецепт: `iron_ingot`×1, `lapis_block`×1, `quartz`×2, `tinted_glass`×4

| положить (теги) | вместо | состояние | грань | файл в паке |
|---|---|---|---|---|
| `solar_cell_off_top.png` / `solar_cell_off.png` / `solar_cell_top.png` / `solar_cell.png` | off·top | `off` | `top` | `textures/item/solar_cell_top.png` |
| `solar_cell_off_side.png` / `solar_cell_side.png` | off·side | `off` | `side` | `textures/item/solar_cell_side.png` |
| `solar_cell_off_bottom.png` / `solar_cell_bottom.png` | off·bottom | `off` | `bottom` | `textures/item/solar_cell_bottom.png` |
| `solar_cell_dim_top.png` / `solar_cell_dim.png` | dim·top | `dim` | `top` | `textures/item/solar_cell_dim_top.png` |
| `solar_cell_dim_side.png` / `solar_cell_side.png` | dim·side | `dim` | `side` | `textures/item/solar_cell_dim_side.png` |
| `solar_cell_dim_bottom.png` / `solar_cell_bottom.png` | dim·bottom | `dim` | `bottom` | `textures/item/solar_cell_dim_bottom.png` |
| `solar_cell_on_top.png` / `solar_cell_on.png` | on·top | `on` | `top` | `textures/item/solar_cell_on_top.png` |
| `solar_cell_on_side.png` / `solar_cell_side.png` | on·side | `on` | `side` | `textures/item/solar_cell_on_side.png` |
| `solar_cell_on_bottom.png` / `solar_cell_bottom.png` | on·bottom | `on` | `bottom` | `textures/item/solar_cell_on_bottom.png` |
| `solar_cell_burnt_top.png` / `solar_cell_burnt.png` | burnt·top | `burnt` | `top` | `textures/item/solar_cell_burnt_top.png` |
| `solar_cell_burnt_side.png` / `solar_cell_side.png` | burnt·side | `burnt` | `side` | `textures/item/solar_cell_burnt_side.png` |
| `solar_cell_burnt_bottom.png` / `solar_cell_bottom.png` | burnt·bottom | `burnt` | `bottom` | `textures/item/solar_cell_burnt_bottom.png` |
| `solar_cell_rev_top.png` / `solar_cell_rev.png` | rev·top | `rev` | `top` | `textures/item/solar_cell_rev_top.png` |
| `solar_cell_rev_side.png` / `solar_cell_side.png` | rev·side | `rev` | `side` | `textures/item/solar_cell_rev_side.png` |
| `solar_cell_rev_bottom.png` / `solar_cell_bottom.png` | rev·bottom | `rev` | `bottom` | `textures/item/solar_cell_rev_bottom.png` |
| `solar_cell_closed_top.png` / `solar_cell_closed.png` | closed·top | `closed` | `top` | `textures/item/solar_cell_closed_top.png` |
| `solar_cell_closed_side.png` / `solar_cell_side.png` | closed·side | `closed` | `side` | `textures/item/solar_cell_closed_side.png` |
| `solar_cell_closed_bottom.png` / `solar_cell_bottom.png` | closed·bottom | `closed` | `bottom` | `textures/item/solar_cell_closed_bottom.png` |

### `generator` — Генератор (часовой сигнал)

* по-английски: Clock Generator
* kind `GENERATOR`, shape `psu`, размер [15, 7, 12], состояний 6, индексы моделей 402…437
* палитра процедурной отрисовки: `#7d6608`, `#f1c40f`, `#2d3436`
* рецепт: `copper_ingot`×2, `iron_ingot`×4, `repeater`×2, `sticky_piston`×1

| положить (теги) | вместо | состояние | грань | файл в паке |
|---|---|---|---|---|
| `generator_off_top.png` / `generator_off.png` / `generator_top.png` / `generator.png` | off·top | `off` | `top` | `textures/item/generator_top.png` |
| `generator_off_side.png` / `generator_side.png` | off·side | `off` | `side` | `textures/item/generator_side.png` |
| `generator_off_bottom.png` / `generator_bottom.png` | off·bottom | `off` | `bottom` | `textures/item/generator_bottom.png` |
| `generator_dim_top.png` / `generator_dim.png` | dim·top | `dim` | `top` | `textures/item/generator_dim_top.png` |
| `generator_dim_side.png` / `generator_side.png` | dim·side | `dim` | `side` | `textures/item/generator_dim_side.png` |
| `generator_dim_bottom.png` / `generator_bottom.png` | dim·bottom | `dim` | `bottom` | `textures/item/generator_dim_bottom.png` |
| `generator_on_top.png` / `generator_on.png` | on·top | `on` | `top` | `textures/item/generator_on_top.png` |
| `generator_on_side.png` / `generator_side.png` | on·side | `on` | `side` | `textures/item/generator_on_side.png` |
| `generator_on_bottom.png` / `generator_bottom.png` | on·bottom | `on` | `bottom` | `textures/item/generator_on_bottom.png` |
| `generator_burnt_top.png` / `generator_burnt.png` | burnt·top | `burnt` | `top` | `textures/item/generator_burnt_top.png` |
| `generator_burnt_side.png` / `generator_side.png` | burnt·side | `burnt` | `side` | `textures/item/generator_burnt_side.png` |
| `generator_burnt_bottom.png` / `generator_bottom.png` | burnt·bottom | `burnt` | `bottom` | `textures/item/generator_burnt_bottom.png` |
| `generator_rev_top.png` / `generator_rev.png` | rev·top | `rev` | `top` | `textures/item/generator_rev_top.png` |
| `generator_rev_side.png` / `generator_side.png` | rev·side | `rev` | `side` | `textures/item/generator_rev_side.png` |
| `generator_rev_bottom.png` / `generator_bottom.png` | rev·bottom | `rev` | `bottom` | `textures/item/generator_rev_bottom.png` |
| `generator_closed_top.png` / `generator_closed.png` | closed·top | `closed` | `top` | `textures/item/generator_closed_top.png` |
| `generator_closed_side.png` / `generator_side.png` | closed·side | `closed` | `side` | `textures/item/generator_closed_side.png` |
| `generator_closed_bottom.png` / `generator_bottom.png` | closed·bottom | `closed` | `bottom` | `textures/item/generator_closed_bottom.png` |

### `ground_bus` — Шина земли

* по-английски: Ground Bus
* kind `GROUND`, shape `plate`, размер [15, 3, 15], состояний 6, индексы моделей 438…473
* палитра процедурной отрисовки: `#145a32`, `#27ae60`, `#a9dfbf`
* рецепт: `copper_block`×6, `lightning_rod`×2, `redstone`×1

| положить (теги) | вместо | состояние | грань | файл в паке |
|---|---|---|---|---|
| `ground_bus_off_top.png` / `ground_bus_off.png` / `ground_bus_top.png` / `ground_bus.png` | off·top | `off` | `top` | `textures/item/ground_bus_top.png` |
| `ground_bus_off_side.png` / `ground_bus_side.png` | off·side | `off` | `side` | `textures/item/ground_bus_side.png` |
| `ground_bus_off_bottom.png` / `ground_bus_bottom.png` | off·bottom | `off` | `bottom` | `textures/item/ground_bus_bottom.png` |
| `ground_bus_dim_top.png` / `ground_bus_dim.png` | dim·top | `dim` | `top` | `textures/item/ground_bus_dim_top.png` |
| `ground_bus_dim_side.png` / `ground_bus_side.png` | dim·side | `dim` | `side` | `textures/item/ground_bus_dim_side.png` |
| `ground_bus_dim_bottom.png` / `ground_bus_bottom.png` | dim·bottom | `dim` | `bottom` | `textures/item/ground_bus_dim_bottom.png` |
| `ground_bus_on_top.png` / `ground_bus_on.png` | on·top | `on` | `top` | `textures/item/ground_bus_on_top.png` |
| `ground_bus_on_side.png` / `ground_bus_side.png` | on·side | `on` | `side` | `textures/item/ground_bus_on_side.png` |
| `ground_bus_on_bottom.png` / `ground_bus_bottom.png` | on·bottom | `on` | `bottom` | `textures/item/ground_bus_on_bottom.png` |
| `ground_bus_burnt_top.png` / `ground_bus_burnt.png` | burnt·top | `burnt` | `top` | `textures/item/ground_bus_burnt_top.png` |
| `ground_bus_burnt_side.png` / `ground_bus_side.png` | burnt·side | `burnt` | `side` | `textures/item/ground_bus_burnt_side.png` |
| `ground_bus_burnt_bottom.png` / `ground_bus_bottom.png` | burnt·bottom | `burnt` | `bottom` | `textures/item/ground_bus_burnt_bottom.png` |
| `ground_bus_rev_top.png` / `ground_bus_rev.png` | rev·top | `rev` | `top` | `textures/item/ground_bus_rev_top.png` |
| `ground_bus_rev_side.png` / `ground_bus_side.png` | rev·side | `rev` | `side` | `textures/item/ground_bus_rev_side.png` |
| `ground_bus_rev_bottom.png` / `ground_bus_bottom.png` | rev·bottom | `rev` | `bottom` | `textures/item/ground_bus_rev_bottom.png` |
| `ground_bus_closed_top.png` / `ground_bus_closed.png` | closed·top | `closed` | `top` | `textures/item/ground_bus_closed_top.png` |
| `ground_bus_closed_side.png` / `ground_bus_side.png` | closed·side | `closed` | `side` | `textures/item/ground_bus_closed_side.png` |
| `ground_bus_closed_bottom.png` / `ground_bus_bottom.png` | closed·bottom | `closed` | `bottom` | `textures/item/ground_bus_closed_bottom.png` |

### `resistor_10r` — Резистор 10 Ом

* по-английски: Resistor 10 Ω
* kind `RESISTOR`, shape `resistor`, размер [14, 4, 4], состояний 6, индексы моделей 474…509, *паяется*
* палитра процедурной отрисовки: `#d5b38b`, `#a04000`
* рецепта в `RECIPES` нет (предмет выдаётся командой или станком)

| положить (теги) | вместо | состояние | грань | файл в паке |
|---|---|---|---|---|
| `resistor_10r_off_top.png` / `resistor_10r_off.png` / `resistor_10r_top.png` / `resistor_10r.png` | off·top | `off` | `top` | `textures/item/resistor_10r_top.png` |
| `resistor_10r_off_side.png` / `resistor_10r_side.png` | off·side | `off` | `side` | `textures/item/resistor_10r_side.png` |
| `resistor_10r_off_bottom.png` / `resistor_10r_bottom.png` | off·bottom | `off` | `bottom` | `textures/item/resistor_10r_bottom.png` |
| `resistor_10r_dim_top.png` / `resistor_10r_dim.png` | dim·top | `dim` | `top` | `textures/item/resistor_10r_dim_top.png` |
| `resistor_10r_dim_side.png` / `resistor_10r_side.png` | dim·side | `dim` | `side` | `textures/item/resistor_10r_dim_side.png` |
| `resistor_10r_dim_bottom.png` / `resistor_10r_bottom.png` | dim·bottom | `dim` | `bottom` | `textures/item/resistor_10r_dim_bottom.png` |
| `resistor_10r_on_top.png` / `resistor_10r_on.png` | on·top | `on` | `top` | `textures/item/resistor_10r_on_top.png` |
| `resistor_10r_on_side.png` / `resistor_10r_side.png` | on·side | `on` | `side` | `textures/item/resistor_10r_on_side.png` |
| `resistor_10r_on_bottom.png` / `resistor_10r_bottom.png` | on·bottom | `on` | `bottom` | `textures/item/resistor_10r_on_bottom.png` |
| `resistor_10r_burnt_top.png` / `resistor_10r_burnt.png` | burnt·top | `burnt` | `top` | `textures/item/resistor_10r_burnt_top.png` |
| `resistor_10r_burnt_side.png` / `resistor_10r_side.png` | burnt·side | `burnt` | `side` | `textures/item/resistor_10r_burnt_side.png` |
| `resistor_10r_burnt_bottom.png` / `resistor_10r_bottom.png` | burnt·bottom | `burnt` | `bottom` | `textures/item/resistor_10r_burnt_bottom.png` |
| `resistor_10r_rev_top.png` / `resistor_10r_rev.png` | rev·top | `rev` | `top` | `textures/item/resistor_10r_rev_top.png` |
| `resistor_10r_rev_side.png` / `resistor_10r_side.png` | rev·side | `rev` | `side` | `textures/item/resistor_10r_rev_side.png` |
| `resistor_10r_rev_bottom.png` / `resistor_10r_bottom.png` | rev·bottom | `rev` | `bottom` | `textures/item/resistor_10r_rev_bottom.png` |
| `resistor_10r_closed_top.png` / `resistor_10r_closed.png` | closed·top | `closed` | `top` | `textures/item/resistor_10r_closed_top.png` |
| `resistor_10r_closed_side.png` / `resistor_10r_side.png` | closed·side | `closed` | `side` | `textures/item/resistor_10r_closed_side.png` |
| `resistor_10r_closed_bottom.png` / `resistor_10r_bottom.png` | closed·bottom | `closed` | `bottom` | `textures/item/resistor_10r_closed_bottom.png` |

### `resistor_100r` — Резистор 100 Ом

* по-английски: Resistor 100 Ω
* kind `RESISTOR`, shape `resistor`, размер [14, 4, 4], состояний 6, индексы моделей 510…545, *паяется*
* палитра процедурной отрисовки: `#d5b38b`, `#6c3483`
* рецепта в `RECIPES` нет (предмет выдаётся командой или станком)

| положить (теги) | вместо | состояние | грань | файл в паке |
|---|---|---|---|---|
| `resistor_100r_off_top.png` / `resistor_100r_off.png` / `resistor_100r_top.png` / `resistor_100r.png` | off·top | `off` | `top` | `textures/item/resistor_100r_top.png` |
| `resistor_100r_off_side.png` / `resistor_100r_side.png` | off·side | `off` | `side` | `textures/item/resistor_100r_side.png` |
| `resistor_100r_off_bottom.png` / `resistor_100r_bottom.png` | off·bottom | `off` | `bottom` | `textures/item/resistor_100r_bottom.png` |
| `resistor_100r_dim_top.png` / `resistor_100r_dim.png` | dim·top | `dim` | `top` | `textures/item/resistor_100r_dim_top.png` |
| `resistor_100r_dim_side.png` / `resistor_100r_side.png` | dim·side | `dim` | `side` | `textures/item/resistor_100r_dim_side.png` |
| `resistor_100r_dim_bottom.png` / `resistor_100r_bottom.png` | dim·bottom | `dim` | `bottom` | `textures/item/resistor_100r_dim_bottom.png` |
| `resistor_100r_on_top.png` / `resistor_100r_on.png` | on·top | `on` | `top` | `textures/item/resistor_100r_on_top.png` |
| `resistor_100r_on_side.png` / `resistor_100r_side.png` | on·side | `on` | `side` | `textures/item/resistor_100r_on_side.png` |
| `resistor_100r_on_bottom.png` / `resistor_100r_bottom.png` | on·bottom | `on` | `bottom` | `textures/item/resistor_100r_on_bottom.png` |
| `resistor_100r_burnt_top.png` / `resistor_100r_burnt.png` | burnt·top | `burnt` | `top` | `textures/item/resistor_100r_burnt_top.png` |
| `resistor_100r_burnt_side.png` / `resistor_100r_side.png` | burnt·side | `burnt` | `side` | `textures/item/resistor_100r_burnt_side.png` |
| `resistor_100r_burnt_bottom.png` / `resistor_100r_bottom.png` | burnt·bottom | `burnt` | `bottom` | `textures/item/resistor_100r_burnt_bottom.png` |
| `resistor_100r_rev_top.png` / `resistor_100r_rev.png` | rev·top | `rev` | `top` | `textures/item/resistor_100r_rev_top.png` |
| `resistor_100r_rev_side.png` / `resistor_100r_side.png` | rev·side | `rev` | `side` | `textures/item/resistor_100r_rev_side.png` |
| `resistor_100r_rev_bottom.png` / `resistor_100r_bottom.png` | rev·bottom | `rev` | `bottom` | `textures/item/resistor_100r_rev_bottom.png` |
| `resistor_100r_closed_top.png` / `resistor_100r_closed.png` | closed·top | `closed` | `top` | `textures/item/resistor_100r_closed_top.png` |
| `resistor_100r_closed_side.png` / `resistor_100r_side.png` | closed·side | `closed` | `side` | `textures/item/resistor_100r_closed_side.png` |
| `resistor_100r_closed_bottom.png` / `resistor_100r_bottom.png` | closed·bottom | `closed` | `bottom` | `textures/item/resistor_100r_closed_bottom.png` |

### `resistor_220r` — Резистор 220 Ом

* по-английски: Resistor 220 Ω
* kind `RESISTOR`, shape `resistor`, размер [14, 4, 4], состояний 6, индексы моделей 546…581, *паяется*
* палитра процедурной отрисовки: `#d5b38b`, `#e67e22`
* рецепта в `RECIPES` нет (предмет выдаётся командой или станком)

| положить (теги) | вместо | состояние | грань | файл в паке |
|---|---|---|---|---|
| `resistor_220r_off_top.png` / `resistor_220r_off.png` / `resistor_220r_top.png` / `resistor_220r.png` | off·top | `off` | `top` | `textures/item/resistor_220r_top.png` |
| `resistor_220r_off_side.png` / `resistor_220r_side.png` | off·side | `off` | `side` | `textures/item/resistor_220r_side.png` |
| `resistor_220r_off_bottom.png` / `resistor_220r_bottom.png` | off·bottom | `off` | `bottom` | `textures/item/resistor_220r_bottom.png` |
| `resistor_220r_dim_top.png` / `resistor_220r_dim.png` | dim·top | `dim` | `top` | `textures/item/resistor_220r_dim_top.png` |
| `resistor_220r_dim_side.png` / `resistor_220r_side.png` | dim·side | `dim` | `side` | `textures/item/resistor_220r_dim_side.png` |
| `resistor_220r_dim_bottom.png` / `resistor_220r_bottom.png` | dim·bottom | `dim` | `bottom` | `textures/item/resistor_220r_dim_bottom.png` |
| `resistor_220r_on_top.png` / `resistor_220r_on.png` | on·top | `on` | `top` | `textures/item/resistor_220r_on_top.png` |
| `resistor_220r_on_side.png` / `resistor_220r_side.png` | on·side | `on` | `side` | `textures/item/resistor_220r_on_side.png` |
| `resistor_220r_on_bottom.png` / `resistor_220r_bottom.png` | on·bottom | `on` | `bottom` | `textures/item/resistor_220r_on_bottom.png` |
| `resistor_220r_burnt_top.png` / `resistor_220r_burnt.png` | burnt·top | `burnt` | `top` | `textures/item/resistor_220r_burnt_top.png` |
| `resistor_220r_burnt_side.png` / `resistor_220r_side.png` | burnt·side | `burnt` | `side` | `textures/item/resistor_220r_burnt_side.png` |
| `resistor_220r_burnt_bottom.png` / `resistor_220r_bottom.png` | burnt·bottom | `burnt` | `bottom` | `textures/item/resistor_220r_burnt_bottom.png` |
| `resistor_220r_rev_top.png` / `resistor_220r_rev.png` | rev·top | `rev` | `top` | `textures/item/resistor_220r_rev_top.png` |
| `resistor_220r_rev_side.png` / `resistor_220r_side.png` | rev·side | `rev` | `side` | `textures/item/resistor_220r_rev_side.png` |
| `resistor_220r_rev_bottom.png` / `resistor_220r_bottom.png` | rev·bottom | `rev` | `bottom` | `textures/item/resistor_220r_rev_bottom.png` |
| `resistor_220r_closed_top.png` / `resistor_220r_closed.png` | closed·top | `closed` | `top` | `textures/item/resistor_220r_closed_top.png` |
| `resistor_220r_closed_side.png` / `resistor_220r_side.png` | closed·side | `closed` | `side` | `textures/item/resistor_220r_closed_side.png` |
| `resistor_220r_closed_bottom.png` / `resistor_220r_bottom.png` | closed·bottom | `closed` | `bottom` | `textures/item/resistor_220r_closed_bottom.png` |

### `resistor_330r` — Резистор 330 Ом

* по-английски: Resistor 330 Ω
* kind `RESISTOR`, shape `resistor`, размер [14, 4, 4], состояний 6, индексы моделей 582…617, *паяется*
* палитра процедурной отрисовки: `#d5b38b`, `#3366cc`
* рецепта в `RECIPES` нет (предмет выдаётся командой или станком)

| положить (теги) | вместо | состояние | грань | файл в паке |
|---|---|---|---|---|
| `resistor_330r_off_top.png` / `resistor_330r_off.png` / `resistor_330r_top.png` / `resistor_330r.png` | off·top | `off` | `top` | `textures/item/resistor_330r_top.png` |
| `resistor_330r_off_side.png` / `resistor_330r_side.png` | off·side | `off` | `side` | `textures/item/resistor_330r_side.png` |
| `resistor_330r_off_bottom.png` / `resistor_330r_bottom.png` | off·bottom | `off` | `bottom` | `textures/item/resistor_330r_bottom.png` |
| `resistor_330r_dim_top.png` / `resistor_330r_dim.png` | dim·top | `dim` | `top` | `textures/item/resistor_330r_dim_top.png` |
| `resistor_330r_dim_side.png` / `resistor_330r_side.png` | dim·side | `dim` | `side` | `textures/item/resistor_330r_dim_side.png` |
| `resistor_330r_dim_bottom.png` / `resistor_330r_bottom.png` | dim·bottom | `dim` | `bottom` | `textures/item/resistor_330r_dim_bottom.png` |
| `resistor_330r_on_top.png` / `resistor_330r_on.png` | on·top | `on` | `top` | `textures/item/resistor_330r_on_top.png` |
| `resistor_330r_on_side.png` / `resistor_330r_side.png` | on·side | `on` | `side` | `textures/item/resistor_330r_on_side.png` |
| `resistor_330r_on_bottom.png` / `resistor_330r_bottom.png` | on·bottom | `on` | `bottom` | `textures/item/resistor_330r_on_bottom.png` |
| `resistor_330r_burnt_top.png` / `resistor_330r_burnt.png` | burnt·top | `burnt` | `top` | `textures/item/resistor_330r_burnt_top.png` |
| `resistor_330r_burnt_side.png` / `resistor_330r_side.png` | burnt·side | `burnt` | `side` | `textures/item/resistor_330r_burnt_side.png` |
| `resistor_330r_burnt_bottom.png` / `resistor_330r_bottom.png` | burnt·bottom | `burnt` | `bottom` | `textures/item/resistor_330r_burnt_bottom.png` |
| `resistor_330r_rev_top.png` / `resistor_330r_rev.png` | rev·top | `rev` | `top` | `textures/item/resistor_330r_rev_top.png` |
| `resistor_330r_rev_side.png` / `resistor_330r_side.png` | rev·side | `rev` | `side` | `textures/item/resistor_330r_rev_side.png` |
| `resistor_330r_rev_bottom.png` / `resistor_330r_bottom.png` | rev·bottom | `rev` | `bottom` | `textures/item/resistor_330r_rev_bottom.png` |
| `resistor_330r_closed_top.png` / `resistor_330r_closed.png` | closed·top | `closed` | `top` | `textures/item/resistor_330r_closed_top.png` |
| `resistor_330r_closed_side.png` / `resistor_330r_side.png` | closed·side | `closed` | `side` | `textures/item/resistor_330r_closed_side.png` |
| `resistor_330r_closed_bottom.png` / `resistor_330r_bottom.png` | closed·bottom | `closed` | `bottom` | `textures/item/resistor_330r_closed_bottom.png` |

### `resistor_1k` — Резистор 1 кОм

* по-английски: Resistor 1 kΩ
* kind `RESISTOR`, shape `resistor`, размер [14, 4, 4], состояний 6, индексы моделей 618…653, *паяется*
* палитра процедурной отрисовки: `#d5b38b`, `#145a32`
* рецепта в `RECIPES` нет (предмет выдаётся командой или станком)

| положить (теги) | вместо | состояние | грань | файл в паке |
|---|---|---|---|---|
| `resistor_1k_off_top.png` / `resistor_1k_off.png` / `resistor_1k_top.png` / `resistor_1k.png` | off·top | `off` | `top` | `textures/item/resistor_1k_top.png` |
| `resistor_1k_off_side.png` / `resistor_1k_side.png` | off·side | `off` | `side` | `textures/item/resistor_1k_side.png` |
| `resistor_1k_off_bottom.png` / `resistor_1k_bottom.png` | off·bottom | `off` | `bottom` | `textures/item/resistor_1k_bottom.png` |
| `resistor_1k_dim_top.png` / `resistor_1k_dim.png` | dim·top | `dim` | `top` | `textures/item/resistor_1k_dim_top.png` |
| `resistor_1k_dim_side.png` / `resistor_1k_side.png` | dim·side | `dim` | `side` | `textures/item/resistor_1k_dim_side.png` |
| `resistor_1k_dim_bottom.png` / `resistor_1k_bottom.png` | dim·bottom | `dim` | `bottom` | `textures/item/resistor_1k_dim_bottom.png` |
| `resistor_1k_on_top.png` / `resistor_1k_on.png` | on·top | `on` | `top` | `textures/item/resistor_1k_on_top.png` |
| `resistor_1k_on_side.png` / `resistor_1k_side.png` | on·side | `on` | `side` | `textures/item/resistor_1k_on_side.png` |
| `resistor_1k_on_bottom.png` / `resistor_1k_bottom.png` | on·bottom | `on` | `bottom` | `textures/item/resistor_1k_on_bottom.png` |
| `resistor_1k_burnt_top.png` / `resistor_1k_burnt.png` | burnt·top | `burnt` | `top` | `textures/item/resistor_1k_burnt_top.png` |
| `resistor_1k_burnt_side.png` / `resistor_1k_side.png` | burnt·side | `burnt` | `side` | `textures/item/resistor_1k_burnt_side.png` |
| `resistor_1k_burnt_bottom.png` / `resistor_1k_bottom.png` | burnt·bottom | `burnt` | `bottom` | `textures/item/resistor_1k_burnt_bottom.png` |
| `resistor_1k_rev_top.png` / `resistor_1k_rev.png` | rev·top | `rev` | `top` | `textures/item/resistor_1k_rev_top.png` |
| `resistor_1k_rev_side.png` / `resistor_1k_side.png` | rev·side | `rev` | `side` | `textures/item/resistor_1k_rev_side.png` |
| `resistor_1k_rev_bottom.png` / `resistor_1k_bottom.png` | rev·bottom | `rev` | `bottom` | `textures/item/resistor_1k_rev_bottom.png` |
| `resistor_1k_closed_top.png` / `resistor_1k_closed.png` | closed·top | `closed` | `top` | `textures/item/resistor_1k_closed_top.png` |
| `resistor_1k_closed_side.png` / `resistor_1k_side.png` | closed·side | `closed` | `side` | `textures/item/resistor_1k_closed_side.png` |
| `resistor_1k_closed_bottom.png` / `resistor_1k_bottom.png` | closed·bottom | `closed` | `bottom` | `textures/item/resistor_1k_closed_bottom.png` |

### `resistor_4k7` — Резистор 4.7 кОм

* по-английски: Resistor 4.7 kΩ
* kind `RESISTOR`, shape `resistor`, размер [14, 4, 4], состояний 6, индексы моделей 654…689, *паяется*
* палитра процедурной отрисовки: `#d5b38b`, `#f4d03f`
* рецепта в `RECIPES` нет (предмет выдаётся командой или станком)

| положить (теги) | вместо | состояние | грань | файл в паке |
|---|---|---|---|---|
| `resistor_4k7_off_top.png` / `resistor_4k7_off.png` / `resistor_4k7_top.png` / `resistor_4k7.png` | off·top | `off` | `top` | `textures/item/resistor_4k7_top.png` |
| `resistor_4k7_off_side.png` / `resistor_4k7_side.png` | off·side | `off` | `side` | `textures/item/resistor_4k7_side.png` |
| `resistor_4k7_off_bottom.png` / `resistor_4k7_bottom.png` | off·bottom | `off` | `bottom` | `textures/item/resistor_4k7_bottom.png` |
| `resistor_4k7_dim_top.png` / `resistor_4k7_dim.png` | dim·top | `dim` | `top` | `textures/item/resistor_4k7_dim_top.png` |
| `resistor_4k7_dim_side.png` / `resistor_4k7_side.png` | dim·side | `dim` | `side` | `textures/item/resistor_4k7_dim_side.png` |
| `resistor_4k7_dim_bottom.png` / `resistor_4k7_bottom.png` | dim·bottom | `dim` | `bottom` | `textures/item/resistor_4k7_dim_bottom.png` |
| `resistor_4k7_on_top.png` / `resistor_4k7_on.png` | on·top | `on` | `top` | `textures/item/resistor_4k7_on_top.png` |
| `resistor_4k7_on_side.png` / `resistor_4k7_side.png` | on·side | `on` | `side` | `textures/item/resistor_4k7_on_side.png` |
| `resistor_4k7_on_bottom.png` / `resistor_4k7_bottom.png` | on·bottom | `on` | `bottom` | `textures/item/resistor_4k7_on_bottom.png` |
| `resistor_4k7_burnt_top.png` / `resistor_4k7_burnt.png` | burnt·top | `burnt` | `top` | `textures/item/resistor_4k7_burnt_top.png` |
| `resistor_4k7_burnt_side.png` / `resistor_4k7_side.png` | burnt·side | `burnt` | `side` | `textures/item/resistor_4k7_burnt_side.png` |
| `resistor_4k7_burnt_bottom.png` / `resistor_4k7_bottom.png` | burnt·bottom | `burnt` | `bottom` | `textures/item/resistor_4k7_burnt_bottom.png` |
| `resistor_4k7_rev_top.png` / `resistor_4k7_rev.png` | rev·top | `rev` | `top` | `textures/item/resistor_4k7_rev_top.png` |
| `resistor_4k7_rev_side.png` / `resistor_4k7_side.png` | rev·side | `rev` | `side` | `textures/item/resistor_4k7_rev_side.png` |
| `resistor_4k7_rev_bottom.png` / `resistor_4k7_bottom.png` | rev·bottom | `rev` | `bottom` | `textures/item/resistor_4k7_rev_bottom.png` |
| `resistor_4k7_closed_top.png` / `resistor_4k7_closed.png` | closed·top | `closed` | `top` | `textures/item/resistor_4k7_closed_top.png` |
| `resistor_4k7_closed_side.png` / `resistor_4k7_side.png` | closed·side | `closed` | `side` | `textures/item/resistor_4k7_closed_side.png` |
| `resistor_4k7_closed_bottom.png` / `resistor_4k7_bottom.png` | closed·bottom | `closed` | `bottom` | `textures/item/resistor_4k7_closed_bottom.png` |

### `resistor_10k` — Резистор 10 кОм

* по-английски: Resistor 10 kΩ
* kind `RESISTOR`, shape `resistor`, размер [14, 4, 4], состояний 6, индексы моделей 690…725, *паяется*
* палитра процедурной отрисовки: `#d5b38b`, `#b7950b`
* рецепта в `RECIPES` нет (предмет выдаётся командой или станком)

| положить (теги) | вместо | состояние | грань | файл в паке |
|---|---|---|---|---|
| `resistor_10k_off_top.png` / `resistor_10k_off.png` / `resistor_10k_top.png` / `resistor_10k.png` | off·top | `off` | `top` | `textures/item/resistor_10k_top.png` |
| `resistor_10k_off_side.png` / `resistor_10k_side.png` | off·side | `off` | `side` | `textures/item/resistor_10k_side.png` |
| `resistor_10k_off_bottom.png` / `resistor_10k_bottom.png` | off·bottom | `off` | `bottom` | `textures/item/resistor_10k_bottom.png` |
| `resistor_10k_dim_top.png` / `resistor_10k_dim.png` | dim·top | `dim` | `top` | `textures/item/resistor_10k_dim_top.png` |
| `resistor_10k_dim_side.png` / `resistor_10k_side.png` | dim·side | `dim` | `side` | `textures/item/resistor_10k_dim_side.png` |
| `resistor_10k_dim_bottom.png` / `resistor_10k_bottom.png` | dim·bottom | `dim` | `bottom` | `textures/item/resistor_10k_dim_bottom.png` |
| `resistor_10k_on_top.png` / `resistor_10k_on.png` | on·top | `on` | `top` | `textures/item/resistor_10k_on_top.png` |
| `resistor_10k_on_side.png` / `resistor_10k_side.png` | on·side | `on` | `side` | `textures/item/resistor_10k_on_side.png` |
| `resistor_10k_on_bottom.png` / `resistor_10k_bottom.png` | on·bottom | `on` | `bottom` | `textures/item/resistor_10k_on_bottom.png` |
| `resistor_10k_burnt_top.png` / `resistor_10k_burnt.png` | burnt·top | `burnt` | `top` | `textures/item/resistor_10k_burnt_top.png` |
| `resistor_10k_burnt_side.png` / `resistor_10k_side.png` | burnt·side | `burnt` | `side` | `textures/item/resistor_10k_burnt_side.png` |
| `resistor_10k_burnt_bottom.png` / `resistor_10k_bottom.png` | burnt·bottom | `burnt` | `bottom` | `textures/item/resistor_10k_burnt_bottom.png` |
| `resistor_10k_rev_top.png` / `resistor_10k_rev.png` | rev·top | `rev` | `top` | `textures/item/resistor_10k_rev_top.png` |
| `resistor_10k_rev_side.png` / `resistor_10k_side.png` | rev·side | `rev` | `side` | `textures/item/resistor_10k_rev_side.png` |
| `resistor_10k_rev_bottom.png` / `resistor_10k_bottom.png` | rev·bottom | `rev` | `bottom` | `textures/item/resistor_10k_rev_bottom.png` |
| `resistor_10k_closed_top.png` / `resistor_10k_closed.png` | closed·top | `closed` | `top` | `textures/item/resistor_10k_closed_top.png` |
| `resistor_10k_closed_side.png` / `resistor_10k_side.png` | closed·side | `closed` | `side` | `textures/item/resistor_10k_closed_side.png` |
| `resistor_10k_closed_bottom.png` / `resistor_10k_bottom.png` | closed·bottom | `closed` | `bottom` | `textures/item/resistor_10k_closed_bottom.png` |

### `resistor_100k` — Резистор 100 кОм

* по-английски: Resistor 100 kΩ
* kind `RESISTOR`, shape `resistor`, размер [14, 4, 4], состояний 6, индексы моделей 726…761, *паяется*
* палитра процедурной отрисовки: `#d5b38b`, `#ff5733`
* рецепта в `RECIPES` нет (предмет выдаётся командой или станком)

| положить (теги) | вместо | состояние | грань | файл в паке |
|---|---|---|---|---|
| `resistor_100k_off_top.png` / `resistor_100k_off.png` / `resistor_100k_top.png` / `resistor_100k.png` | off·top | `off` | `top` | `textures/item/resistor_100k_top.png` |
| `resistor_100k_off_side.png` / `resistor_100k_side.png` | off·side | `off` | `side` | `textures/item/resistor_100k_side.png` |
| `resistor_100k_off_bottom.png` / `resistor_100k_bottom.png` | off·bottom | `off` | `bottom` | `textures/item/resistor_100k_bottom.png` |
| `resistor_100k_dim_top.png` / `resistor_100k_dim.png` | dim·top | `dim` | `top` | `textures/item/resistor_100k_dim_top.png` |
| `resistor_100k_dim_side.png` / `resistor_100k_side.png` | dim·side | `dim` | `side` | `textures/item/resistor_100k_dim_side.png` |
| `resistor_100k_dim_bottom.png` / `resistor_100k_bottom.png` | dim·bottom | `dim` | `bottom` | `textures/item/resistor_100k_dim_bottom.png` |
| `resistor_100k_on_top.png` / `resistor_100k_on.png` | on·top | `on` | `top` | `textures/item/resistor_100k_on_top.png` |
| `resistor_100k_on_side.png` / `resistor_100k_side.png` | on·side | `on` | `side` | `textures/item/resistor_100k_on_side.png` |
| `resistor_100k_on_bottom.png` / `resistor_100k_bottom.png` | on·bottom | `on` | `bottom` | `textures/item/resistor_100k_on_bottom.png` |
| `resistor_100k_burnt_top.png` / `resistor_100k_burnt.png` | burnt·top | `burnt` | `top` | `textures/item/resistor_100k_burnt_top.png` |
| `resistor_100k_burnt_side.png` / `resistor_100k_side.png` | burnt·side | `burnt` | `side` | `textures/item/resistor_100k_burnt_side.png` |
| `resistor_100k_burnt_bottom.png` / `resistor_100k_bottom.png` | burnt·bottom | `burnt` | `bottom` | `textures/item/resistor_100k_burnt_bottom.png` |
| `resistor_100k_rev_top.png` / `resistor_100k_rev.png` | rev·top | `rev` | `top` | `textures/item/resistor_100k_rev_top.png` |
| `resistor_100k_rev_side.png` / `resistor_100k_side.png` | rev·side | `rev` | `side` | `textures/item/resistor_100k_rev_side.png` |
| `resistor_100k_rev_bottom.png` / `resistor_100k_bottom.png` | rev·bottom | `rev` | `bottom` | `textures/item/resistor_100k_rev_bottom.png` |
| `resistor_100k_closed_top.png` / `resistor_100k_closed.png` | closed·top | `closed` | `top` | `textures/item/resistor_100k_closed_top.png` |
| `resistor_100k_closed_side.png` / `resistor_100k_side.png` | closed·side | `closed` | `side` | `textures/item/resistor_100k_closed_side.png` |
| `resistor_100k_closed_bottom.png` / `resistor_100k_bottom.png` | closed·bottom | `closed` | `bottom` | `textures/item/resistor_100k_closed_bottom.png` |

### `potentiometer` — Подстроечный резистор 10 кОм

* по-английски: Potentiometer 10 kΩ
* kind `POT`, shape `pot`, размер [9, 9, 9], состояний 6, индексы моделей 762…797, *паяется*
* палитра процедурной отрисовки: `#1f618d`, `#f1c40f`, `#bdc3c7`
* рецепт: `copper_ingot`×1, `iron_nugget`×1, `resin_clump`×3

| положить (теги) | вместо | состояние | грань | файл в паке |
|---|---|---|---|---|
| `potentiometer_off_top.png` / `potentiometer_off.png` / `potentiometer_top.png` / `potentiometer.png` | off·top | `off` | `top` | `textures/item/potentiometer_top.png` |
| `potentiometer_off_side.png` / `potentiometer_side.png` | off·side | `off` | `side` | `textures/item/potentiometer_side.png` |
| `potentiometer_off_bottom.png` / `potentiometer_bottom.png` | off·bottom | `off` | `bottom` | `textures/item/potentiometer_bottom.png` |
| `potentiometer_dim_top.png` / `potentiometer_dim.png` | dim·top | `dim` | `top` | `textures/item/potentiometer_dim_top.png` |
| `potentiometer_dim_side.png` / `potentiometer_side.png` | dim·side | `dim` | `side` | `textures/item/potentiometer_dim_side.png` |
| `potentiometer_dim_bottom.png` / `potentiometer_bottom.png` | dim·bottom | `dim` | `bottom` | `textures/item/potentiometer_dim_bottom.png` |
| `potentiometer_on_top.png` / `potentiometer_on.png` | on·top | `on` | `top` | `textures/item/potentiometer_on_top.png` |
| `potentiometer_on_side.png` / `potentiometer_side.png` | on·side | `on` | `side` | `textures/item/potentiometer_on_side.png` |
| `potentiometer_on_bottom.png` / `potentiometer_bottom.png` | on·bottom | `on` | `bottom` | `textures/item/potentiometer_on_bottom.png` |
| `potentiometer_burnt_top.png` / `potentiometer_burnt.png` | burnt·top | `burnt` | `top` | `textures/item/potentiometer_burnt_top.png` |
| `potentiometer_burnt_side.png` / `potentiometer_side.png` | burnt·side | `burnt` | `side` | `textures/item/potentiometer_burnt_side.png` |
| `potentiometer_burnt_bottom.png` / `potentiometer_bottom.png` | burnt·bottom | `burnt` | `bottom` | `textures/item/potentiometer_burnt_bottom.png` |
| `potentiometer_rev_top.png` / `potentiometer_rev.png` | rev·top | `rev` | `top` | `textures/item/potentiometer_rev_top.png` |
| `potentiometer_rev_side.png` / `potentiometer_side.png` | rev·side | `rev` | `side` | `textures/item/potentiometer_rev_side.png` |
| `potentiometer_rev_bottom.png` / `potentiometer_bottom.png` | rev·bottom | `rev` | `bottom` | `textures/item/potentiometer_rev_bottom.png` |
| `potentiometer_closed_top.png` / `potentiometer_closed.png` | closed·top | `closed` | `top` | `textures/item/potentiometer_closed_top.png` |
| `potentiometer_closed_side.png` / `potentiometer_side.png` | closed·side | `closed` | `side` | `textures/item/potentiometer_closed_side.png` |
| `potentiometer_closed_bottom.png` / `potentiometer_bottom.png` | closed·bottom | `closed` | `bottom` | `textures/item/potentiometer_closed_bottom.png` |

### `cap_100nf` — Конденсатор 100 нФ

* по-английски: Capacitor 100 nF
* kind `CAPACITOR`, shape `disc`, размер [9, 6, 9], состояний 6, индексы моделей 798…833, *паяется*
* палитра процедурной отрисовки: `#1a5276`, `#aed6f1`
* рецепт: `copper_ingot`×1, `paper`×1

| положить (теги) | вместо | состояние | грань | файл в паке |
|---|---|---|---|---|
| `cap_100nf_off_top.png` / `cap_100nf_off.png` / `cap_100nf_top.png` / `cap_100nf.png` | off·top | `off` | `top` | `textures/item/cap_100nf_top.png` |
| `cap_100nf_off_side.png` / `cap_100nf_side.png` | off·side | `off` | `side` | `textures/item/cap_100nf_side.png` |
| `cap_100nf_off_bottom.png` / `cap_100nf_bottom.png` | off·bottom | `off` | `bottom` | `textures/item/cap_100nf_bottom.png` |
| `cap_100nf_dim_top.png` / `cap_100nf_dim.png` | dim·top | `dim` | `top` | `textures/item/cap_100nf_dim_top.png` |
| `cap_100nf_dim_side.png` / `cap_100nf_side.png` | dim·side | `dim` | `side` | `textures/item/cap_100nf_dim_side.png` |
| `cap_100nf_dim_bottom.png` / `cap_100nf_bottom.png` | dim·bottom | `dim` | `bottom` | `textures/item/cap_100nf_dim_bottom.png` |
| `cap_100nf_on_top.png` / `cap_100nf_on.png` | on·top | `on` | `top` | `textures/item/cap_100nf_on_top.png` |
| `cap_100nf_on_side.png` / `cap_100nf_side.png` | on·side | `on` | `side` | `textures/item/cap_100nf_on_side.png` |
| `cap_100nf_on_bottom.png` / `cap_100nf_bottom.png` | on·bottom | `on` | `bottom` | `textures/item/cap_100nf_on_bottom.png` |
| `cap_100nf_burnt_top.png` / `cap_100nf_burnt.png` | burnt·top | `burnt` | `top` | `textures/item/cap_100nf_burnt_top.png` |
| `cap_100nf_burnt_side.png` / `cap_100nf_side.png` | burnt·side | `burnt` | `side` | `textures/item/cap_100nf_burnt_side.png` |
| `cap_100nf_burnt_bottom.png` / `cap_100nf_bottom.png` | burnt·bottom | `burnt` | `bottom` | `textures/item/cap_100nf_burnt_bottom.png` |
| `cap_100nf_rev_top.png` / `cap_100nf_rev.png` | rev·top | `rev` | `top` | `textures/item/cap_100nf_rev_top.png` |
| `cap_100nf_rev_side.png` / `cap_100nf_side.png` | rev·side | `rev` | `side` | `textures/item/cap_100nf_rev_side.png` |
| `cap_100nf_rev_bottom.png` / `cap_100nf_bottom.png` | rev·bottom | `rev` | `bottom` | `textures/item/cap_100nf_rev_bottom.png` |
| `cap_100nf_closed_top.png` / `cap_100nf_closed.png` | closed·top | `closed` | `top` | `textures/item/cap_100nf_closed_top.png` |
| `cap_100nf_closed_side.png` / `cap_100nf_side.png` | closed·side | `closed` | `side` | `textures/item/cap_100nf_closed_side.png` |
| `cap_100nf_closed_bottom.png` / `cap_100nf_bottom.png` | closed·bottom | `closed` | `bottom` | `textures/item/cap_100nf_closed_bottom.png` |

### `cap_100uf` — Электролит 100 мкФ

* по-английски: Electrolytic 100 µF
* kind `CAPACITOR`, shape `electrolytic`, размер [8, 10, 8], состояний 6, индексы моделей 834…869, *паяется*
* палитра процедурной отрисовки: `#17202a`, `#5dade2`
* рецепт: `copper_ingot`×1, `paper`×2

| положить (теги) | вместо | состояние | грань | файл в паке |
|---|---|---|---|---|
| `cap_100uf_off_top.png` / `cap_100uf_off.png` / `cap_100uf_top.png` / `cap_100uf.png` | off·top | `off` | `top` | `textures/item/cap_100uf_top.png` |
| `cap_100uf_off_side.png` / `cap_100uf_side.png` | off·side | `off` | `side` | `textures/item/cap_100uf_side.png` |
| `cap_100uf_off_bottom.png` / `cap_100uf_bottom.png` | off·bottom | `off` | `bottom` | `textures/item/cap_100uf_bottom.png` |
| `cap_100uf_dim_top.png` / `cap_100uf_dim.png` | dim·top | `dim` | `top` | `textures/item/cap_100uf_dim_top.png` |
| `cap_100uf_dim_side.png` / `cap_100uf_side.png` | dim·side | `dim` | `side` | `textures/item/cap_100uf_dim_side.png` |
| `cap_100uf_dim_bottom.png` / `cap_100uf_bottom.png` | dim·bottom | `dim` | `bottom` | `textures/item/cap_100uf_dim_bottom.png` |
| `cap_100uf_on_top.png` / `cap_100uf_on.png` | on·top | `on` | `top` | `textures/item/cap_100uf_on_top.png` |
| `cap_100uf_on_side.png` / `cap_100uf_side.png` | on·side | `on` | `side` | `textures/item/cap_100uf_on_side.png` |
| `cap_100uf_on_bottom.png` / `cap_100uf_bottom.png` | on·bottom | `on` | `bottom` | `textures/item/cap_100uf_on_bottom.png` |
| `cap_100uf_burnt_top.png` / `cap_100uf_burnt.png` | burnt·top | `burnt` | `top` | `textures/item/cap_100uf_burnt_top.png` |
| `cap_100uf_burnt_side.png` / `cap_100uf_side.png` | burnt·side | `burnt` | `side` | `textures/item/cap_100uf_burnt_side.png` |
| `cap_100uf_burnt_bottom.png` / `cap_100uf_bottom.png` | burnt·bottom | `burnt` | `bottom` | `textures/item/cap_100uf_burnt_bottom.png` |
| `cap_100uf_rev_top.png` / `cap_100uf_rev.png` | rev·top | `rev` | `top` | `textures/item/cap_100uf_rev_top.png` |
| `cap_100uf_rev_side.png` / `cap_100uf_side.png` | rev·side | `rev` | `side` | `textures/item/cap_100uf_rev_side.png` |
| `cap_100uf_rev_bottom.png` / `cap_100uf_bottom.png` | rev·bottom | `rev` | `bottom` | `textures/item/cap_100uf_rev_bottom.png` |
| `cap_100uf_closed_top.png` / `cap_100uf_closed.png` | closed·top | `closed` | `top` | `textures/item/cap_100uf_closed_top.png` |
| `cap_100uf_closed_side.png` / `cap_100uf_side.png` | closed·side | `closed` | `side` | `textures/item/cap_100uf_closed_side.png` |
| `cap_100uf_closed_bottom.png` / `cap_100uf_bottom.png` | closed·bottom | `closed` | `bottom` | `textures/item/cap_100uf_closed_bottom.png` |

### `cap_1000uf` — Электролит 1000 мкФ

* по-английски: Electrolytic 1000 µF
* kind `CAPACITOR`, shape `electrolytic`, размер [10, 12, 10], состояний 6, индексы моделей 870…905, *паяется*
* палитра процедурной отрисовки: `#0b5345`, `#f1c40f`
* рецепт: `copper_ingot`×1, `paper`×2

| положить (теги) | вместо | состояние | грань | файл в паке |
|---|---|---|---|---|
| `cap_1000uf_off_top.png` / `cap_1000uf_off.png` / `cap_1000uf_top.png` / `cap_1000uf.png` | off·top | `off` | `top` | `textures/item/cap_1000uf_top.png` |
| `cap_1000uf_off_side.png` / `cap_1000uf_side.png` | off·side | `off` | `side` | `textures/item/cap_1000uf_side.png` |
| `cap_1000uf_off_bottom.png` / `cap_1000uf_bottom.png` | off·bottom | `off` | `bottom` | `textures/item/cap_1000uf_bottom.png` |
| `cap_1000uf_dim_top.png` / `cap_1000uf_dim.png` | dim·top | `dim` | `top` | `textures/item/cap_1000uf_dim_top.png` |
| `cap_1000uf_dim_side.png` / `cap_1000uf_side.png` | dim·side | `dim` | `side` | `textures/item/cap_1000uf_dim_side.png` |
| `cap_1000uf_dim_bottom.png` / `cap_1000uf_bottom.png` | dim·bottom | `dim` | `bottom` | `textures/item/cap_1000uf_dim_bottom.png` |
| `cap_1000uf_on_top.png` / `cap_1000uf_on.png` | on·top | `on` | `top` | `textures/item/cap_1000uf_on_top.png` |
| `cap_1000uf_on_side.png` / `cap_1000uf_side.png` | on·side | `on` | `side` | `textures/item/cap_1000uf_on_side.png` |
| `cap_1000uf_on_bottom.png` / `cap_1000uf_bottom.png` | on·bottom | `on` | `bottom` | `textures/item/cap_1000uf_on_bottom.png` |
| `cap_1000uf_burnt_top.png` / `cap_1000uf_burnt.png` | burnt·top | `burnt` | `top` | `textures/item/cap_1000uf_burnt_top.png` |
| `cap_1000uf_burnt_side.png` / `cap_1000uf_side.png` | burnt·side | `burnt` | `side` | `textures/item/cap_1000uf_burnt_side.png` |
| `cap_1000uf_burnt_bottom.png` / `cap_1000uf_bottom.png` | burnt·bottom | `burnt` | `bottom` | `textures/item/cap_1000uf_burnt_bottom.png` |
| `cap_1000uf_rev_top.png` / `cap_1000uf_rev.png` | rev·top | `rev` | `top` | `textures/item/cap_1000uf_rev_top.png` |
| `cap_1000uf_rev_side.png` / `cap_1000uf_side.png` | rev·side | `rev` | `side` | `textures/item/cap_1000uf_rev_side.png` |
| `cap_1000uf_rev_bottom.png` / `cap_1000uf_bottom.png` | rev·bottom | `rev` | `bottom` | `textures/item/cap_1000uf_rev_bottom.png` |
| `cap_1000uf_closed_top.png` / `cap_1000uf_closed.png` | closed·top | `closed` | `top` | `textures/item/cap_1000uf_closed_top.png` |
| `cap_1000uf_closed_side.png` / `cap_1000uf_side.png` | closed·side | `closed` | `side` | `textures/item/cap_1000uf_closed_side.png` |
| `cap_1000uf_closed_bottom.png` / `cap_1000uf_bottom.png` | closed·bottom | `closed` | `bottom` | `textures/item/cap_1000uf_closed_bottom.png` |

### `diode` — Диод 1N4007

* по-английски: Diode 1N4007
* kind `DIODE`, shape `resistor`, размер [14, 4, 4], состояний 6, индексы моделей 906…941, *паяется*
* палитра процедурной отрисовки: `#212f3d`, `#ecf0f1`
* рецепт: `copper_ingot`×1, `redstone`×1

| положить (теги) | вместо | состояние | грань | файл в паке |
|---|---|---|---|---|
| `diode_off_top.png` / `diode_off.png` / `diode_top.png` / `diode.png` | off·top | `off` | `top` | `textures/item/diode_top.png` |
| `diode_off_side.png` / `diode_side.png` | off·side | `off` | `side` | `textures/item/diode_side.png` |
| `diode_off_bottom.png` / `diode_bottom.png` | off·bottom | `off` | `bottom` | `textures/item/diode_bottom.png` |
| `diode_dim_top.png` / `diode_dim.png` | dim·top | `dim` | `top` | `textures/item/diode_dim_top.png` |
| `diode_dim_side.png` / `diode_side.png` | dim·side | `dim` | `side` | `textures/item/diode_dim_side.png` |
| `diode_dim_bottom.png` / `diode_bottom.png` | dim·bottom | `dim` | `bottom` | `textures/item/diode_dim_bottom.png` |
| `diode_on_top.png` / `diode_on.png` | on·top | `on` | `top` | `textures/item/diode_on_top.png` |
| `diode_on_side.png` / `diode_side.png` | on·side | `on` | `side` | `textures/item/diode_on_side.png` |
| `diode_on_bottom.png` / `diode_bottom.png` | on·bottom | `on` | `bottom` | `textures/item/diode_on_bottom.png` |
| `diode_burnt_top.png` / `diode_burnt.png` | burnt·top | `burnt` | `top` | `textures/item/diode_burnt_top.png` |
| `diode_burnt_side.png` / `diode_side.png` | burnt·side | `burnt` | `side` | `textures/item/diode_burnt_side.png` |
| `diode_burnt_bottom.png` / `diode_bottom.png` | burnt·bottom | `burnt` | `bottom` | `textures/item/diode_burnt_bottom.png` |
| `diode_rev_top.png` / `diode_rev.png` | rev·top | `rev` | `top` | `textures/item/diode_rev_top.png` |
| `diode_rev_side.png` / `diode_side.png` | rev·side | `rev` | `side` | `textures/item/diode_rev_side.png` |
| `diode_rev_bottom.png` / `diode_bottom.png` | rev·bottom | `rev` | `bottom` | `textures/item/diode_rev_bottom.png` |
| `diode_closed_top.png` / `diode_closed.png` | closed·top | `closed` | `top` | `textures/item/diode_closed_top.png` |
| `diode_closed_side.png` / `diode_side.png` | closed·side | `closed` | `side` | `textures/item/diode_closed_side.png` |
| `diode_closed_bottom.png` / `diode_bottom.png` | closed·bottom | `closed` | `bottom` | `textures/item/diode_closed_bottom.png` |

### `led_red` — Светодиод красный

* по-английски: LED (red)
* kind `LED`, shape `led`, размер [6, 9, 6], состояний 6, индексы моделей 942…977, *паяется*
* палитра процедурной отрисовки: `#e74c3c`, `#f5b7b1`, `#b03a2e`
* рецепт: `copper_ingot`×1, `glass`×1, `redstone`×1

| положить (теги) | вместо | состояние | грань | файл в паке |
|---|---|---|---|---|
| `led_red_off_top.png` / `led_red_off.png` / `led_red_top.png` / `led_red.png` | off·top | `off` | `top` | `textures/item/led_red_top.png` |
| `led_red_off_side.png` / `led_red_side.png` | off·side | `off` | `side` | `textures/item/led_red_side.png` |
| `led_red_off_bottom.png` / `led_red_bottom.png` | off·bottom | `off` | `bottom` | `textures/item/led_red_bottom.png` |
| `led_red_dim_top.png` / `led_red_dim.png` | dim·top | `dim` | `top` | `textures/item/led_red_dim_top.png` |
| `led_red_dim_side.png` / `led_red_side.png` | dim·side | `dim` | `side` | `textures/item/led_red_dim_side.png` |
| `led_red_dim_bottom.png` / `led_red_bottom.png` | dim·bottom | `dim` | `bottom` | `textures/item/led_red_dim_bottom.png` |
| `led_red_on_top.png` / `led_red_on.png` | on·top | `on` | `top` | `textures/item/led_red_on_top.png` |
| `led_red_on_side.png` / `led_red_side.png` | on·side | `on` | `side` | `textures/item/led_red_on_side.png` |
| `led_red_on_bottom.png` / `led_red_bottom.png` | on·bottom | `on` | `bottom` | `textures/item/led_red_on_bottom.png` |
| `led_red_burnt_top.png` / `led_red_burnt.png` | burnt·top | `burnt` | `top` | `textures/item/led_red_burnt_top.png` |
| `led_red_burnt_side.png` / `led_red_side.png` | burnt·side | `burnt` | `side` | `textures/item/led_red_burnt_side.png` |
| `led_red_burnt_bottom.png` / `led_red_bottom.png` | burnt·bottom | `burnt` | `bottom` | `textures/item/led_red_burnt_bottom.png` |
| `led_red_rev_top.png` / `led_red_rev.png` | rev·top | `rev` | `top` | `textures/item/led_red_rev_top.png` |
| `led_red_rev_side.png` / `led_red_side.png` | rev·side | `rev` | `side` | `textures/item/led_red_rev_side.png` |
| `led_red_rev_bottom.png` / `led_red_bottom.png` | rev·bottom | `rev` | `bottom` | `textures/item/led_red_rev_bottom.png` |
| `led_red_closed_top.png` / `led_red_closed.png` | closed·top | `closed` | `top` | `textures/item/led_red_closed_top.png` |
| `led_red_closed_side.png` / `led_red_side.png` | closed·side | `closed` | `side` | `textures/item/led_red_closed_side.png` |
| `led_red_closed_bottom.png` / `led_red_bottom.png` | closed·bottom | `closed` | `bottom` | `textures/item/led_red_closed_bottom.png` |

### `led_green` — Светодиод зелёный

* по-английски: LED (green)
* kind `LED`, shape `led`, размер [6, 9, 6], состояний 6, индексы моделей 978…1013, *паяется*
* палитра процедурной отрисовки: `#27ae60`, `#abebc6`, `#196f3d`
* рецепт: `copper_ingot`×1, `glass`×1, `lime_dye`×1

| положить (теги) | вместо | состояние | грань | файл в паке |
|---|---|---|---|---|
| `led_green_off_top.png` / `led_green_off.png` / `led_green_top.png` / `led_green.png` | off·top | `off` | `top` | `textures/item/led_green_top.png` |
| `led_green_off_side.png` / `led_green_side.png` | off·side | `off` | `side` | `textures/item/led_green_side.png` |
| `led_green_off_bottom.png` / `led_green_bottom.png` | off·bottom | `off` | `bottom` | `textures/item/led_green_bottom.png` |
| `led_green_dim_top.png` / `led_green_dim.png` | dim·top | `dim` | `top` | `textures/item/led_green_dim_top.png` |
| `led_green_dim_side.png` / `led_green_side.png` | dim·side | `dim` | `side` | `textures/item/led_green_dim_side.png` |
| `led_green_dim_bottom.png` / `led_green_bottom.png` | dim·bottom | `dim` | `bottom` | `textures/item/led_green_dim_bottom.png` |
| `led_green_on_top.png` / `led_green_on.png` | on·top | `on` | `top` | `textures/item/led_green_on_top.png` |
| `led_green_on_side.png` / `led_green_side.png` | on·side | `on` | `side` | `textures/item/led_green_on_side.png` |
| `led_green_on_bottom.png` / `led_green_bottom.png` | on·bottom | `on` | `bottom` | `textures/item/led_green_on_bottom.png` |
| `led_green_burnt_top.png` / `led_green_burnt.png` | burnt·top | `burnt` | `top` | `textures/item/led_green_burnt_top.png` |
| `led_green_burnt_side.png` / `led_green_side.png` | burnt·side | `burnt` | `side` | `textures/item/led_green_burnt_side.png` |
| `led_green_burnt_bottom.png` / `led_green_bottom.png` | burnt·bottom | `burnt` | `bottom` | `textures/item/led_green_burnt_bottom.png` |
| `led_green_rev_top.png` / `led_green_rev.png` | rev·top | `rev` | `top` | `textures/item/led_green_rev_top.png` |
| `led_green_rev_side.png` / `led_green_side.png` | rev·side | `rev` | `side` | `textures/item/led_green_rev_side.png` |
| `led_green_rev_bottom.png` / `led_green_bottom.png` | rev·bottom | `rev` | `bottom` | `textures/item/led_green_rev_bottom.png` |
| `led_green_closed_top.png` / `led_green_closed.png` | closed·top | `closed` | `top` | `textures/item/led_green_closed_top.png` |
| `led_green_closed_side.png` / `led_green_side.png` | closed·side | `closed` | `side` | `textures/item/led_green_closed_side.png` |
| `led_green_closed_bottom.png` / `led_green_bottom.png` | closed·bottom | `closed` | `bottom` | `textures/item/led_green_closed_bottom.png` |

### `led_blue` — Светодиод синий

* по-английски: LED (blue)
* kind `LED`, shape `led`, размер [6, 9, 6], состояний 6, индексы моделей 1014…1049, *паяется*
* палитра процедурной отрисовки: `#2e86c1`, `#aed6f1`, `#1b4f72`
* рецепт: `copper_ingot`×1, `glass`×1, `lapis_lazuli`×1

| положить (теги) | вместо | состояние | грань | файл в паке |
|---|---|---|---|---|
| `led_blue_off_top.png` / `led_blue_off.png` / `led_blue_top.png` / `led_blue.png` | off·top | `off` | `top` | `textures/item/led_blue_top.png` |
| `led_blue_off_side.png` / `led_blue_side.png` | off·side | `off` | `side` | `textures/item/led_blue_side.png` |
| `led_blue_off_bottom.png` / `led_blue_bottom.png` | off·bottom | `off` | `bottom` | `textures/item/led_blue_bottom.png` |
| `led_blue_dim_top.png` / `led_blue_dim.png` | dim·top | `dim` | `top` | `textures/item/led_blue_dim_top.png` |
| `led_blue_dim_side.png` / `led_blue_side.png` | dim·side | `dim` | `side` | `textures/item/led_blue_dim_side.png` |
| `led_blue_dim_bottom.png` / `led_blue_bottom.png` | dim·bottom | `dim` | `bottom` | `textures/item/led_blue_dim_bottom.png` |
| `led_blue_on_top.png` / `led_blue_on.png` | on·top | `on` | `top` | `textures/item/led_blue_on_top.png` |
| `led_blue_on_side.png` / `led_blue_side.png` | on·side | `on` | `side` | `textures/item/led_blue_on_side.png` |
| `led_blue_on_bottom.png` / `led_blue_bottom.png` | on·bottom | `on` | `bottom` | `textures/item/led_blue_on_bottom.png` |
| `led_blue_burnt_top.png` / `led_blue_burnt.png` | burnt·top | `burnt` | `top` | `textures/item/led_blue_burnt_top.png` |
| `led_blue_burnt_side.png` / `led_blue_side.png` | burnt·side | `burnt` | `side` | `textures/item/led_blue_burnt_side.png` |
| `led_blue_burnt_bottom.png` / `led_blue_bottom.png` | burnt·bottom | `burnt` | `bottom` | `textures/item/led_blue_burnt_bottom.png` |
| `led_blue_rev_top.png` / `led_blue_rev.png` | rev·top | `rev` | `top` | `textures/item/led_blue_rev_top.png` |
| `led_blue_rev_side.png` / `led_blue_side.png` | rev·side | `rev` | `side` | `textures/item/led_blue_rev_side.png` |
| `led_blue_rev_bottom.png` / `led_blue_bottom.png` | rev·bottom | `rev` | `bottom` | `textures/item/led_blue_rev_bottom.png` |
| `led_blue_closed_top.png` / `led_blue_closed.png` | closed·top | `closed` | `top` | `textures/item/led_blue_closed_top.png` |
| `led_blue_closed_side.png` / `led_blue_side.png` | closed·side | `closed` | `side` | `textures/item/led_blue_closed_side.png` |
| `led_blue_closed_bottom.png` / `led_blue_bottom.png` | closed·bottom | `closed` | `bottom` | `textures/item/led_blue_closed_bottom.png` |

### `led_yellow` — Светодиод жёлтый

* по-английски: LED (yellow)
* kind `LED`, shape `led`, размер [6, 9, 6], состояний 6, индексы моделей 1050…1085, *паяется*
* палитра процедурной отрисовки: `#f1c40f`, `#fcf3cf`, `#b7950b`
* рецепт: `copper_ingot`×1, `glass`×1, `yellow_dye`×1

| положить (теги) | вместо | состояние | грань | файл в паке |
|---|---|---|---|---|
| `led_yellow_off_top.png` / `led_yellow_off.png` / `led_yellow_top.png` / `led_yellow.png` | off·top | `off` | `top` | `textures/item/led_yellow_top.png` |
| `led_yellow_off_side.png` / `led_yellow_side.png` | off·side | `off` | `side` | `textures/item/led_yellow_side.png` |
| `led_yellow_off_bottom.png` / `led_yellow_bottom.png` | off·bottom | `off` | `bottom` | `textures/item/led_yellow_bottom.png` |
| `led_yellow_dim_top.png` / `led_yellow_dim.png` | dim·top | `dim` | `top` | `textures/item/led_yellow_dim_top.png` |
| `led_yellow_dim_side.png` / `led_yellow_side.png` | dim·side | `dim` | `side` | `textures/item/led_yellow_dim_side.png` |
| `led_yellow_dim_bottom.png` / `led_yellow_bottom.png` | dim·bottom | `dim` | `bottom` | `textures/item/led_yellow_dim_bottom.png` |
| `led_yellow_on_top.png` / `led_yellow_on.png` | on·top | `on` | `top` | `textures/item/led_yellow_on_top.png` |
| `led_yellow_on_side.png` / `led_yellow_side.png` | on·side | `on` | `side` | `textures/item/led_yellow_on_side.png` |
| `led_yellow_on_bottom.png` / `led_yellow_bottom.png` | on·bottom | `on` | `bottom` | `textures/item/led_yellow_on_bottom.png` |
| `led_yellow_burnt_top.png` / `led_yellow_burnt.png` | burnt·top | `burnt` | `top` | `textures/item/led_yellow_burnt_top.png` |
| `led_yellow_burnt_side.png` / `led_yellow_side.png` | burnt·side | `burnt` | `side` | `textures/item/led_yellow_burnt_side.png` |
| `led_yellow_burnt_bottom.png` / `led_yellow_bottom.png` | burnt·bottom | `burnt` | `bottom` | `textures/item/led_yellow_burnt_bottom.png` |
| `led_yellow_rev_top.png` / `led_yellow_rev.png` | rev·top | `rev` | `top` | `textures/item/led_yellow_rev_top.png` |
| `led_yellow_rev_side.png` / `led_yellow_side.png` | rev·side | `rev` | `side` | `textures/item/led_yellow_rev_side.png` |
| `led_yellow_rev_bottom.png` / `led_yellow_bottom.png` | rev·bottom | `rev` | `bottom` | `textures/item/led_yellow_rev_bottom.png` |
| `led_yellow_closed_top.png` / `led_yellow_closed.png` | closed·top | `closed` | `top` | `textures/item/led_yellow_closed_top.png` |
| `led_yellow_closed_side.png` / `led_yellow_side.png` | closed·side | `closed` | `side` | `textures/item/led_yellow_closed_side.png` |
| `led_yellow_closed_bottom.png` / `led_yellow_bottom.png` | closed·bottom | `closed` | `bottom` | `textures/item/led_yellow_closed_bottom.png` |

### `led_white` — Светодиод белый

* по-английски: LED (white)
* kind `LED`, shape `led`, размер [6, 9, 6], состояний 6, индексы моделей 1086…1121, *паяется*
* палитра процедурной отрисовки: `#ecf0f1`, `#ffffff`, `#b2bec3`
* рецепт: `copper_ingot`×1, `glass`×1, `quartz`×1

| положить (теги) | вместо | состояние | грань | файл в паке |
|---|---|---|---|---|
| `led_white_off_top.png` / `led_white_off.png` / `led_white_top.png` / `led_white.png` | off·top | `off` | `top` | `textures/item/led_white_top.png` |
| `led_white_off_side.png` / `led_white_side.png` | off·side | `off` | `side` | `textures/item/led_white_side.png` |
| `led_white_off_bottom.png` / `led_white_bottom.png` | off·bottom | `off` | `bottom` | `textures/item/led_white_bottom.png` |
| `led_white_dim_top.png` / `led_white_dim.png` | dim·top | `dim` | `top` | `textures/item/led_white_dim_top.png` |
| `led_white_dim_side.png` / `led_white_side.png` | dim·side | `dim` | `side` | `textures/item/led_white_dim_side.png` |
| `led_white_dim_bottom.png` / `led_white_bottom.png` | dim·bottom | `dim` | `bottom` | `textures/item/led_white_dim_bottom.png` |
| `led_white_on_top.png` / `led_white_on.png` | on·top | `on` | `top` | `textures/item/led_white_on_top.png` |
| `led_white_on_side.png` / `led_white_side.png` | on·side | `on` | `side` | `textures/item/led_white_on_side.png` |
| `led_white_on_bottom.png` / `led_white_bottom.png` | on·bottom | `on` | `bottom` | `textures/item/led_white_on_bottom.png` |
| `led_white_burnt_top.png` / `led_white_burnt.png` | burnt·top | `burnt` | `top` | `textures/item/led_white_burnt_top.png` |
| `led_white_burnt_side.png` / `led_white_side.png` | burnt·side | `burnt` | `side` | `textures/item/led_white_burnt_side.png` |
| `led_white_burnt_bottom.png` / `led_white_bottom.png` | burnt·bottom | `burnt` | `bottom` | `textures/item/led_white_burnt_bottom.png` |
| `led_white_rev_top.png` / `led_white_rev.png` | rev·top | `rev` | `top` | `textures/item/led_white_rev_top.png` |
| `led_white_rev_side.png` / `led_white_side.png` | rev·side | `rev` | `side` | `textures/item/led_white_rev_side.png` |
| `led_white_rev_bottom.png` / `led_white_bottom.png` | rev·bottom | `rev` | `bottom` | `textures/item/led_white_rev_bottom.png` |
| `led_white_closed_top.png` / `led_white_closed.png` | closed·top | `closed` | `top` | `textures/item/led_white_closed_top.png` |
| `led_white_closed_side.png` / `led_white_side.png` | closed·side | `closed` | `side` | `textures/item/led_white_closed_side.png` |
| `led_white_closed_bottom.png` / `led_white_bottom.png` | closed·bottom | `closed` | `bottom` | `textures/item/led_white_closed_bottom.png` |

### `lamp` — Лампочка накаливания

* по-английски: Incandescent Lamp
* kind `LAMP`, shape `bulb`, размер [10, 12, 10], состояний 6, индексы моделей 1122…1157, *паяется*
* палитра процедурной отрисовки: `#f7dc6f`, `#fef9e7`, `#7f8c8d`
* рецепт: `copper_ingot`×1, `glass`×3, `redstone`×1

| положить (теги) | вместо | состояние | грань | файл в паке |
|---|---|---|---|---|
| `lamp_off_top.png` / `lamp_off.png` / `lamp_top.png` / `lamp.png` | off·top | `off` | `top` | `textures/item/lamp_top.png` |
| `lamp_off_side.png` / `lamp_side.png` | off·side | `off` | `side` | `textures/item/lamp_side.png` |
| `lamp_off_bottom.png` / `lamp_bottom.png` | off·bottom | `off` | `bottom` | `textures/item/lamp_bottom.png` |
| `lamp_dim_top.png` / `lamp_dim.png` | dim·top | `dim` | `top` | `textures/item/lamp_dim_top.png` |
| `lamp_dim_side.png` / `lamp_side.png` | dim·side | `dim` | `side` | `textures/item/lamp_dim_side.png` |
| `lamp_dim_bottom.png` / `lamp_bottom.png` | dim·bottom | `dim` | `bottom` | `textures/item/lamp_dim_bottom.png` |
| `lamp_on_top.png` / `lamp_on.png` | on·top | `on` | `top` | `textures/item/lamp_on_top.png` |
| `lamp_on_side.png` / `lamp_side.png` | on·side | `on` | `side` | `textures/item/lamp_on_side.png` |
| `lamp_on_bottom.png` / `lamp_bottom.png` | on·bottom | `on` | `bottom` | `textures/item/lamp_on_bottom.png` |
| `lamp_burnt_top.png` / `lamp_burnt.png` | burnt·top | `burnt` | `top` | `textures/item/lamp_burnt_top.png` |
| `lamp_burnt_side.png` / `lamp_side.png` | burnt·side | `burnt` | `side` | `textures/item/lamp_burnt_side.png` |
| `lamp_burnt_bottom.png` / `lamp_bottom.png` | burnt·bottom | `burnt` | `bottom` | `textures/item/lamp_burnt_bottom.png` |
| `lamp_rev_top.png` / `lamp_rev.png` | rev·top | `rev` | `top` | `textures/item/lamp_rev_top.png` |
| `lamp_rev_side.png` / `lamp_side.png` | rev·side | `rev` | `side` | `textures/item/lamp_rev_side.png` |
| `lamp_rev_bottom.png` / `lamp_bottom.png` | rev·bottom | `rev` | `bottom` | `textures/item/lamp_rev_bottom.png` |
| `lamp_closed_top.png` / `lamp_closed.png` | closed·top | `closed` | `top` | `textures/item/lamp_closed_top.png` |
| `lamp_closed_side.png` / `lamp_side.png` | closed·side | `closed` | `side` | `textures/item/lamp_closed_side.png` |
| `lamp_closed_bottom.png` / `lamp_bottom.png` | closed·bottom | `closed` | `bottom` | `textures/item/lamp_closed_bottom.png` |

### `button` — Кнопка (без фиксации)

* по-английски: Push Button
* kind `BUTTON`, shape `button`, размер [10, 6, 10], состояний 6, индексы моделей 1158…1193, *паяется*
* палитра процедурной отрисовки: `#c0392b`, `#bdc3c7`
* рецепт: `redstone`×1, `stone_pressure_plate`×1

| положить (теги) | вместо | состояние | грань | файл в паке |
|---|---|---|---|---|
| `button_off_top.png` / `button_off.png` / `button_top.png` / `button.png` | off·top | `off` | `top` | `textures/item/button_top.png` |
| `button_off_side.png` / `button_side.png` | off·side | `off` | `side` | `textures/item/button_side.png` |
| `button_off_bottom.png` / `button_bottom.png` | off·bottom | `off` | `bottom` | `textures/item/button_bottom.png` |
| `button_dim_top.png` / `button_dim.png` | dim·top | `dim` | `top` | `textures/item/button_dim_top.png` |
| `button_dim_side.png` / `button_side.png` | dim·side | `dim` | `side` | `textures/item/button_dim_side.png` |
| `button_dim_bottom.png` / `button_bottom.png` | dim·bottom | `dim` | `bottom` | `textures/item/button_dim_bottom.png` |
| `button_on_top.png` / `button_on.png` | on·top | `on` | `top` | `textures/item/button_on_top.png` |
| `button_on_side.png` / `button_side.png` | on·side | `on` | `side` | `textures/item/button_on_side.png` |
| `button_on_bottom.png` / `button_bottom.png` | on·bottom | `on` | `bottom` | `textures/item/button_on_bottom.png` |
| `button_burnt_top.png` / `button_burnt.png` | burnt·top | `burnt` | `top` | `textures/item/button_burnt_top.png` |
| `button_burnt_side.png` / `button_side.png` | burnt·side | `burnt` | `side` | `textures/item/button_burnt_side.png` |
| `button_burnt_bottom.png` / `button_bottom.png` | burnt·bottom | `burnt` | `bottom` | `textures/item/button_burnt_bottom.png` |
| `button_rev_top.png` / `button_rev.png` | rev·top | `rev` | `top` | `textures/item/button_rev_top.png` |
| `button_rev_side.png` / `button_side.png` | rev·side | `rev` | `side` | `textures/item/button_rev_side.png` |
| `button_rev_bottom.png` / `button_bottom.png` | rev·bottom | `rev` | `bottom` | `textures/item/button_rev_bottom.png` |
| `button_closed_top.png` / `button_closed.png` | closed·top | `closed` | `top` | `textures/item/button_closed_top.png` |
| `button_closed_side.png` / `button_side.png` | closed·side | `closed` | `side` | `textures/item/button_closed_side.png` |
| `button_closed_bottom.png` / `button_bottom.png` | closed·bottom | `closed` | `bottom` | `textures/item/button_closed_bottom.png` |

### `switch_spst` — Выключатель

* по-английски: Switch (SPST)
* kind `SWITCH`, shape `switch`, размер [12, 7, 8], состояний 6, индексы моделей 1194…1229, *паяется*
* палитра процедурной отрисовки: `#95a5a6`, `#e74c3c`, `#2d3436`
* рецепт: `lever`×1, `redstone`×1, `stone_button`×1

| положить (теги) | вместо | состояние | грань | файл в паке |
|---|---|---|---|---|
| `switch_spst_off_top.png` / `switch_spst_off.png` / `switch_spst_top.png` / `switch_spst.png` | off·top | `off` | `top` | `textures/item/switch_spst_top.png` |
| `switch_spst_off_side.png` / `switch_spst_side.png` | off·side | `off` | `side` | `textures/item/switch_spst_side.png` |
| `switch_spst_off_bottom.png` / `switch_spst_bottom.png` | off·bottom | `off` | `bottom` | `textures/item/switch_spst_bottom.png` |
| `switch_spst_dim_top.png` / `switch_spst_dim.png` | dim·top | `dim` | `top` | `textures/item/switch_spst_dim_top.png` |
| `switch_spst_dim_side.png` / `switch_spst_side.png` | dim·side | `dim` | `side` | `textures/item/switch_spst_dim_side.png` |
| `switch_spst_dim_bottom.png` / `switch_spst_bottom.png` | dim·bottom | `dim` | `bottom` | `textures/item/switch_spst_dim_bottom.png` |
| `switch_spst_on_top.png` / `switch_spst_on.png` | on·top | `on` | `top` | `textures/item/switch_spst_on_top.png` |
| `switch_spst_on_side.png` / `switch_spst_side.png` | on·side | `on` | `side` | `textures/item/switch_spst_on_side.png` |
| `switch_spst_on_bottom.png` / `switch_spst_bottom.png` | on·bottom | `on` | `bottom` | `textures/item/switch_spst_on_bottom.png` |
| `switch_spst_burnt_top.png` / `switch_spst_burnt.png` | burnt·top | `burnt` | `top` | `textures/item/switch_spst_burnt_top.png` |
| `switch_spst_burnt_side.png` / `switch_spst_side.png` | burnt·side | `burnt` | `side` | `textures/item/switch_spst_burnt_side.png` |
| `switch_spst_burnt_bottom.png` / `switch_spst_bottom.png` | burnt·bottom | `burnt` | `bottom` | `textures/item/switch_spst_burnt_bottom.png` |
| `switch_spst_rev_top.png` / `switch_spst_rev.png` | rev·top | `rev` | `top` | `textures/item/switch_spst_rev_top.png` |
| `switch_spst_rev_side.png` / `switch_spst_side.png` | rev·side | `rev` | `side` | `textures/item/switch_spst_rev_side.png` |
| `switch_spst_rev_bottom.png` / `switch_spst_bottom.png` | rev·bottom | `rev` | `bottom` | `textures/item/switch_spst_rev_bottom.png` |
| `switch_spst_closed_top.png` / `switch_spst_closed.png` | closed·top | `closed` | `top` | `textures/item/switch_spst_closed_top.png` |
| `switch_spst_closed_side.png` / `switch_spst_side.png` | closed·side | `closed` | `side` | `textures/item/switch_spst_closed_side.png` |
| `switch_spst_closed_bottom.png` / `switch_spst_bottom.png` | closed·bottom | `closed` | `bottom` | `textures/item/switch_spst_closed_bottom.png` |

### `switch_spdt` — Переключатель П2т

* по-английски: Switch (SPDT)
* kind `SWITCH`, shape `switch3`, размер [12, 7, 10], состояний 6, индексы моделей 1230…1265, *паяется*
* палитра процедурной отрисовки: `#7f8c8d`, `#f1c40f`, `#2d3436`
* рецепт: `lever`×2, `redstone`×1, `stone_button`×3

| положить (теги) | вместо | состояние | грань | файл в паке |
|---|---|---|---|---|
| `switch_spdt_off_top.png` / `switch_spdt_off.png` / `switch_spdt_top.png` / `switch_spdt.png` | off·top | `off` | `top` | `textures/item/switch_spdt_top.png` |
| `switch_spdt_off_side.png` / `switch_spdt_side.png` | off·side | `off` | `side` | `textures/item/switch_spdt_side.png` |
| `switch_spdt_off_bottom.png` / `switch_spdt_bottom.png` | off·bottom | `off` | `bottom` | `textures/item/switch_spdt_bottom.png` |
| `switch_spdt_dim_top.png` / `switch_spdt_dim.png` | dim·top | `dim` | `top` | `textures/item/switch_spdt_dim_top.png` |
| `switch_spdt_dim_side.png` / `switch_spdt_side.png` | dim·side | `dim` | `side` | `textures/item/switch_spdt_dim_side.png` |
| `switch_spdt_dim_bottom.png` / `switch_spdt_bottom.png` | dim·bottom | `dim` | `bottom` | `textures/item/switch_spdt_dim_bottom.png` |
| `switch_spdt_on_top.png` / `switch_spdt_on.png` | on·top | `on` | `top` | `textures/item/switch_spdt_on_top.png` |
| `switch_spdt_on_side.png` / `switch_spdt_side.png` | on·side | `on` | `side` | `textures/item/switch_spdt_on_side.png` |
| `switch_spdt_on_bottom.png` / `switch_spdt_bottom.png` | on·bottom | `on` | `bottom` | `textures/item/switch_spdt_on_bottom.png` |
| `switch_spdt_burnt_top.png` / `switch_spdt_burnt.png` | burnt·top | `burnt` | `top` | `textures/item/switch_spdt_burnt_top.png` |
| `switch_spdt_burnt_side.png` / `switch_spdt_side.png` | burnt·side | `burnt` | `side` | `textures/item/switch_spdt_burnt_side.png` |
| `switch_spdt_burnt_bottom.png` / `switch_spdt_bottom.png` | burnt·bottom | `burnt` | `bottom` | `textures/item/switch_spdt_burnt_bottom.png` |
| `switch_spdt_rev_top.png` / `switch_spdt_rev.png` | rev·top | `rev` | `top` | `textures/item/switch_spdt_rev_top.png` |
| `switch_spdt_rev_side.png` / `switch_spdt_side.png` | rev·side | `rev` | `side` | `textures/item/switch_spdt_rev_side.png` |
| `switch_spdt_rev_bottom.png` / `switch_spdt_bottom.png` | rev·bottom | `rev` | `bottom` | `textures/item/switch_spdt_rev_bottom.png` |
| `switch_spdt_closed_top.png` / `switch_spdt_closed.png` | closed·top | `closed` | `top` | `textures/item/switch_spdt_closed_top.png` |
| `switch_spdt_closed_side.png` / `switch_spdt_side.png` | closed·side | `closed` | `side` | `textures/item/switch_spdt_closed_side.png` |
| `switch_spdt_closed_bottom.png` / `switch_spdt_bottom.png` | closed·bottom | `closed` | `bottom` | `textures/item/switch_spdt_closed_bottom.png` |

### `transistor_npn` — Транзистор NPN (BC547)

* по-английски: Transistor NPN (BC547)
* kind `TRANSISTOR`, shape `to92`, размер [9, 9, 5], состояний 6, индексы моделей 1266…1301, *паяется*
* палитра процедурной отрисовки: `#17202a`, `#bdc3c7`
* рецепт: `gold_nugget`×2, `redstone`×2, `sand`×1

| положить (теги) | вместо | состояние | грань | файл в паке |
|---|---|---|---|---|
| `transistor_npn_off_top.png` / `transistor_npn_off.png` / `transistor_npn_top.png` / `transistor_npn.png` | off·top | `off` | `top` | `textures/item/transistor_npn_top.png` |
| `transistor_npn_off_side.png` / `transistor_npn_side.png` | off·side | `off` | `side` | `textures/item/transistor_npn_side.png` |
| `transistor_npn_off_bottom.png` / `transistor_npn_bottom.png` | off·bottom | `off` | `bottom` | `textures/item/transistor_npn_bottom.png` |
| `transistor_npn_dim_top.png` / `transistor_npn_dim.png` | dim·top | `dim` | `top` | `textures/item/transistor_npn_dim_top.png` |
| `transistor_npn_dim_side.png` / `transistor_npn_side.png` | dim·side | `dim` | `side` | `textures/item/transistor_npn_dim_side.png` |
| `transistor_npn_dim_bottom.png` / `transistor_npn_bottom.png` | dim·bottom | `dim` | `bottom` | `textures/item/transistor_npn_dim_bottom.png` |
| `transistor_npn_on_top.png` / `transistor_npn_on.png` | on·top | `on` | `top` | `textures/item/transistor_npn_on_top.png` |
| `transistor_npn_on_side.png` / `transistor_npn_side.png` | on·side | `on` | `side` | `textures/item/transistor_npn_on_side.png` |
| `transistor_npn_on_bottom.png` / `transistor_npn_bottom.png` | on·bottom | `on` | `bottom` | `textures/item/transistor_npn_on_bottom.png` |
| `transistor_npn_burnt_top.png` / `transistor_npn_burnt.png` | burnt·top | `burnt` | `top` | `textures/item/transistor_npn_burnt_top.png` |
| `transistor_npn_burnt_side.png` / `transistor_npn_side.png` | burnt·side | `burnt` | `side` | `textures/item/transistor_npn_burnt_side.png` |
| `transistor_npn_burnt_bottom.png` / `transistor_npn_bottom.png` | burnt·bottom | `burnt` | `bottom` | `textures/item/transistor_npn_burnt_bottom.png` |
| `transistor_npn_rev_top.png` / `transistor_npn_rev.png` | rev·top | `rev` | `top` | `textures/item/transistor_npn_rev_top.png` |
| `transistor_npn_rev_side.png` / `transistor_npn_side.png` | rev·side | `rev` | `side` | `textures/item/transistor_npn_rev_side.png` |
| `transistor_npn_rev_bottom.png` / `transistor_npn_bottom.png` | rev·bottom | `rev` | `bottom` | `textures/item/transistor_npn_rev_bottom.png` |
| `transistor_npn_closed_top.png` / `transistor_npn_closed.png` | closed·top | `closed` | `top` | `textures/item/transistor_npn_closed_top.png` |
| `transistor_npn_closed_side.png` / `transistor_npn_side.png` | closed·side | `closed` | `side` | `textures/item/transistor_npn_closed_side.png` |
| `transistor_npn_closed_bottom.png` / `transistor_npn_bottom.png` | closed·bottom | `closed` | `bottom` | `textures/item/transistor_npn_closed_bottom.png` |

### `transistor_pnp` — Транзистор PNP (BC557)

* по-английски: Transistor PNP (BC557)
* kind `TRANSISTOR`, shape `to92`, размер [9, 9, 5], состояний 6, индексы моделей 1302…1337, *паяется*
* палитра процедурной отрисовки: `#4a235a`, `#bdc3c7`
* рецепт: `iron_nugget`×2, `redstone`×2, `sand`×1

| положить (теги) | вместо | состояние | грань | файл в паке |
|---|---|---|---|---|
| `transistor_pnp_off_top.png` / `transistor_pnp_off.png` / `transistor_pnp_top.png` / `transistor_pnp.png` | off·top | `off` | `top` | `textures/item/transistor_pnp_top.png` |
| `transistor_pnp_off_side.png` / `transistor_pnp_side.png` | off·side | `off` | `side` | `textures/item/transistor_pnp_side.png` |
| `transistor_pnp_off_bottom.png` / `transistor_pnp_bottom.png` | off·bottom | `off` | `bottom` | `textures/item/transistor_pnp_bottom.png` |
| `transistor_pnp_dim_top.png` / `transistor_pnp_dim.png` | dim·top | `dim` | `top` | `textures/item/transistor_pnp_dim_top.png` |
| `transistor_pnp_dim_side.png` / `transistor_pnp_side.png` | dim·side | `dim` | `side` | `textures/item/transistor_pnp_dim_side.png` |
| `transistor_pnp_dim_bottom.png` / `transistor_pnp_bottom.png` | dim·bottom | `dim` | `bottom` | `textures/item/transistor_pnp_dim_bottom.png` |
| `transistor_pnp_on_top.png` / `transistor_pnp_on.png` | on·top | `on` | `top` | `textures/item/transistor_pnp_on_top.png` |
| `transistor_pnp_on_side.png` / `transistor_pnp_side.png` | on·side | `on` | `side` | `textures/item/transistor_pnp_on_side.png` |
| `transistor_pnp_on_bottom.png` / `transistor_pnp_bottom.png` | on·bottom | `on` | `bottom` | `textures/item/transistor_pnp_on_bottom.png` |
| `transistor_pnp_burnt_top.png` / `transistor_pnp_burnt.png` | burnt·top | `burnt` | `top` | `textures/item/transistor_pnp_burnt_top.png` |
| `transistor_pnp_burnt_side.png` / `transistor_pnp_side.png` | burnt·side | `burnt` | `side` | `textures/item/transistor_pnp_burnt_side.png` |
| `transistor_pnp_burnt_bottom.png` / `transistor_pnp_bottom.png` | burnt·bottom | `burnt` | `bottom` | `textures/item/transistor_pnp_burnt_bottom.png` |
| `transistor_pnp_rev_top.png` / `transistor_pnp_rev.png` | rev·top | `rev` | `top` | `textures/item/transistor_pnp_rev_top.png` |
| `transistor_pnp_rev_side.png` / `transistor_pnp_side.png` | rev·side | `rev` | `side` | `textures/item/transistor_pnp_rev_side.png` |
| `transistor_pnp_rev_bottom.png` / `transistor_pnp_bottom.png` | rev·bottom | `rev` | `bottom` | `textures/item/transistor_pnp_rev_bottom.png` |
| `transistor_pnp_closed_top.png` / `transistor_pnp_closed.png` | closed·top | `closed` | `top` | `textures/item/transistor_pnp_closed_top.png` |
| `transistor_pnp_closed_side.png` / `transistor_pnp_side.png` | closed·side | `closed` | `side` | `textures/item/transistor_pnp_closed_side.png` |
| `transistor_pnp_closed_bottom.png` / `transistor_pnp_bottom.png` | closed·bottom | `closed` | `bottom` | `textures/item/transistor_pnp_closed_bottom.png` |

### `gate_and` — Микросхема И (AND)

* по-английски: IC AND
* kind `GATE`, shape `chip`, размер [13, 4, 8], состояний 6, индексы моделей 1338…1373, *паяется*
* палитра процедурной отрисовки: `#17202a`, `#f1c40f`
* рецепт: `copper_ingot`×1, `redstone`×4, `sand`×3

| положить (теги) | вместо | состояние | грань | файл в паке |
|---|---|---|---|---|
| `gate_and_off_top.png` / `gate_and_off.png` / `gate_and_top.png` / `gate_and.png` | off·top | `off` | `top` | `textures/item/gate_and_top.png` |
| `gate_and_off_side.png` / `gate_and_side.png` | off·side | `off` | `side` | `textures/item/gate_and_side.png` |
| `gate_and_off_bottom.png` / `gate_and_bottom.png` | off·bottom | `off` | `bottom` | `textures/item/gate_and_bottom.png` |
| `gate_and_dim_top.png` / `gate_and_dim.png` | dim·top | `dim` | `top` | `textures/item/gate_and_dim_top.png` |
| `gate_and_dim_side.png` / `gate_and_side.png` | dim·side | `dim` | `side` | `textures/item/gate_and_dim_side.png` |
| `gate_and_dim_bottom.png` / `gate_and_bottom.png` | dim·bottom | `dim` | `bottom` | `textures/item/gate_and_dim_bottom.png` |
| `gate_and_on_top.png` / `gate_and_on.png` | on·top | `on` | `top` | `textures/item/gate_and_on_top.png` |
| `gate_and_on_side.png` / `gate_and_side.png` | on·side | `on` | `side` | `textures/item/gate_and_on_side.png` |
| `gate_and_on_bottom.png` / `gate_and_bottom.png` | on·bottom | `on` | `bottom` | `textures/item/gate_and_on_bottom.png` |
| `gate_and_burnt_top.png` / `gate_and_burnt.png` | burnt·top | `burnt` | `top` | `textures/item/gate_and_burnt_top.png` |
| `gate_and_burnt_side.png` / `gate_and_side.png` | burnt·side | `burnt` | `side` | `textures/item/gate_and_burnt_side.png` |
| `gate_and_burnt_bottom.png` / `gate_and_bottom.png` | burnt·bottom | `burnt` | `bottom` | `textures/item/gate_and_burnt_bottom.png` |
| `gate_and_rev_top.png` / `gate_and_rev.png` | rev·top | `rev` | `top` | `textures/item/gate_and_rev_top.png` |
| `gate_and_rev_side.png` / `gate_and_side.png` | rev·side | `rev` | `side` | `textures/item/gate_and_rev_side.png` |
| `gate_and_rev_bottom.png` / `gate_and_bottom.png` | rev·bottom | `rev` | `bottom` | `textures/item/gate_and_rev_bottom.png` |
| `gate_and_closed_top.png` / `gate_and_closed.png` | closed·top | `closed` | `top` | `textures/item/gate_and_closed_top.png` |
| `gate_and_closed_side.png` / `gate_and_side.png` | closed·side | `closed` | `side` | `textures/item/gate_and_closed_side.png` |
| `gate_and_closed_bottom.png` / `gate_and_bottom.png` | closed·bottom | `closed` | `bottom` | `textures/item/gate_and_closed_bottom.png` |

### `gate_or` — Микросхема ИЛИ (OR)

* по-английски: IC OR
* kind `GATE`, shape `chip`, размер [13, 4, 8], состояний 6, индексы моделей 1374…1409, *паяется*
* палитра процедурной отрисовки: `#17202a`, `#e67e22`
* рецепт: `copper_ingot`×1, `redstone`×4, `sand`×2

| положить (теги) | вместо | состояние | грань | файл в паке |
|---|---|---|---|---|
| `gate_or_off_top.png` / `gate_or_off.png` / `gate_or_top.png` / `gate_or.png` | off·top | `off` | `top` | `textures/item/gate_or_top.png` |
| `gate_or_off_side.png` / `gate_or_side.png` | off·side | `off` | `side` | `textures/item/gate_or_side.png` |
| `gate_or_off_bottom.png` / `gate_or_bottom.png` | off·bottom | `off` | `bottom` | `textures/item/gate_or_bottom.png` |
| `gate_or_dim_top.png` / `gate_or_dim.png` | dim·top | `dim` | `top` | `textures/item/gate_or_dim_top.png` |
| `gate_or_dim_side.png` / `gate_or_side.png` | dim·side | `dim` | `side` | `textures/item/gate_or_dim_side.png` |
| `gate_or_dim_bottom.png` / `gate_or_bottom.png` | dim·bottom | `dim` | `bottom` | `textures/item/gate_or_dim_bottom.png` |
| `gate_or_on_top.png` / `gate_or_on.png` | on·top | `on` | `top` | `textures/item/gate_or_on_top.png` |
| `gate_or_on_side.png` / `gate_or_side.png` | on·side | `on` | `side` | `textures/item/gate_or_on_side.png` |
| `gate_or_on_bottom.png` / `gate_or_bottom.png` | on·bottom | `on` | `bottom` | `textures/item/gate_or_on_bottom.png` |
| `gate_or_burnt_top.png` / `gate_or_burnt.png` | burnt·top | `burnt` | `top` | `textures/item/gate_or_burnt_top.png` |
| `gate_or_burnt_side.png` / `gate_or_side.png` | burnt·side | `burnt` | `side` | `textures/item/gate_or_burnt_side.png` |
| `gate_or_burnt_bottom.png` / `gate_or_bottom.png` | burnt·bottom | `burnt` | `bottom` | `textures/item/gate_or_burnt_bottom.png` |
| `gate_or_rev_top.png` / `gate_or_rev.png` | rev·top | `rev` | `top` | `textures/item/gate_or_rev_top.png` |
| `gate_or_rev_side.png` / `gate_or_side.png` | rev·side | `rev` | `side` | `textures/item/gate_or_rev_side.png` |
| `gate_or_rev_bottom.png` / `gate_or_bottom.png` | rev·bottom | `rev` | `bottom` | `textures/item/gate_or_rev_bottom.png` |
| `gate_or_closed_top.png` / `gate_or_closed.png` | closed·top | `closed` | `top` | `textures/item/gate_or_closed_top.png` |
| `gate_or_closed_side.png` / `gate_or_side.png` | closed·side | `closed` | `side` | `textures/item/gate_or_closed_side.png` |
| `gate_or_closed_bottom.png` / `gate_or_bottom.png` | closed·bottom | `closed` | `bottom` | `textures/item/gate_or_closed_bottom.png` |

### `gate_xor` — Микросхема Исключающее ИЛИ (XOR)

* по-английски: IC XOR
* kind `GATE`, shape `chip`, размер [13, 4, 8], состояний 6, индексы моделей 1410…1445, *паяется*
* палитра процедурной отрисовки: `#17202a`, `#48c9b0`
* рецепт: `gold_nugget`×1, `redstone`×4, `sand`×3

| положить (теги) | вместо | состояние | грань | файл в паке |
|---|---|---|---|---|
| `gate_xor_off_top.png` / `gate_xor_off.png` / `gate_xor_top.png` / `gate_xor.png` | off·top | `off` | `top` | `textures/item/gate_xor_top.png` |
| `gate_xor_off_side.png` / `gate_xor_side.png` | off·side | `off` | `side` | `textures/item/gate_xor_side.png` |
| `gate_xor_off_bottom.png` / `gate_xor_bottom.png` | off·bottom | `off` | `bottom` | `textures/item/gate_xor_bottom.png` |
| `gate_xor_dim_top.png` / `gate_xor_dim.png` | dim·top | `dim` | `top` | `textures/item/gate_xor_dim_top.png` |
| `gate_xor_dim_side.png` / `gate_xor_side.png` | dim·side | `dim` | `side` | `textures/item/gate_xor_dim_side.png` |
| `gate_xor_dim_bottom.png` / `gate_xor_bottom.png` | dim·bottom | `dim` | `bottom` | `textures/item/gate_xor_dim_bottom.png` |
| `gate_xor_on_top.png` / `gate_xor_on.png` | on·top | `on` | `top` | `textures/item/gate_xor_on_top.png` |
| `gate_xor_on_side.png` / `gate_xor_side.png` | on·side | `on` | `side` | `textures/item/gate_xor_on_side.png` |
| `gate_xor_on_bottom.png` / `gate_xor_bottom.png` | on·bottom | `on` | `bottom` | `textures/item/gate_xor_on_bottom.png` |
| `gate_xor_burnt_top.png` / `gate_xor_burnt.png` | burnt·top | `burnt` | `top` | `textures/item/gate_xor_burnt_top.png` |
| `gate_xor_burnt_side.png` / `gate_xor_side.png` | burnt·side | `burnt` | `side` | `textures/item/gate_xor_burnt_side.png` |
| `gate_xor_burnt_bottom.png` / `gate_xor_bottom.png` | burnt·bottom | `burnt` | `bottom` | `textures/item/gate_xor_burnt_bottom.png` |
| `gate_xor_rev_top.png` / `gate_xor_rev.png` | rev·top | `rev` | `top` | `textures/item/gate_xor_rev_top.png` |
| `gate_xor_rev_side.png` / `gate_xor_side.png` | rev·side | `rev` | `side` | `textures/item/gate_xor_rev_side.png` |
| `gate_xor_rev_bottom.png` / `gate_xor_bottom.png` | rev·bottom | `rev` | `bottom` | `textures/item/gate_xor_rev_bottom.png` |
| `gate_xor_closed_top.png` / `gate_xor_closed.png` | closed·top | `closed` | `top` | `textures/item/gate_xor_closed_top.png` |
| `gate_xor_closed_side.png` / `gate_xor_side.png` | closed·side | `closed` | `side` | `textures/item/gate_xor_closed_side.png` |
| `gate_xor_closed_bottom.png` / `gate_xor_bottom.png` | closed·bottom | `closed` | `bottom` | `textures/item/gate_xor_closed_bottom.png` |

### `gate_not` — Микросхема НЕ (INVERTER)

* по-английски: IC NOT
* kind `GATE`, shape `chip`, размер [11, 4, 8], состояний 6, индексы моделей 1446…1481, *паяется*
* палитра процедурной отрисовки: `#17202a`, `#eb984e`
* рецепт: `copper_ingot`×1, `redstone`×3

| положить (теги) | вместо | состояние | грань | файл в паке |
|---|---|---|---|---|
| `gate_not_off_top.png` / `gate_not_off.png` / `gate_not_top.png` / `gate_not.png` | off·top | `off` | `top` | `textures/item/gate_not_top.png` |
| `gate_not_off_side.png` / `gate_not_side.png` | off·side | `off` | `side` | `textures/item/gate_not_side.png` |
| `gate_not_off_bottom.png` / `gate_not_bottom.png` | off·bottom | `off` | `bottom` | `textures/item/gate_not_bottom.png` |
| `gate_not_dim_top.png` / `gate_not_dim.png` | dim·top | `dim` | `top` | `textures/item/gate_not_dim_top.png` |
| `gate_not_dim_side.png` / `gate_not_side.png` | dim·side | `dim` | `side` | `textures/item/gate_not_dim_side.png` |
| `gate_not_dim_bottom.png` / `gate_not_bottom.png` | dim·bottom | `dim` | `bottom` | `textures/item/gate_not_dim_bottom.png` |
| `gate_not_on_top.png` / `gate_not_on.png` | on·top | `on` | `top` | `textures/item/gate_not_on_top.png` |
| `gate_not_on_side.png` / `gate_not_side.png` | on·side | `on` | `side` | `textures/item/gate_not_on_side.png` |
| `gate_not_on_bottom.png` / `gate_not_bottom.png` | on·bottom | `on` | `bottom` | `textures/item/gate_not_on_bottom.png` |
| `gate_not_burnt_top.png` / `gate_not_burnt.png` | burnt·top | `burnt` | `top` | `textures/item/gate_not_burnt_top.png` |
| `gate_not_burnt_side.png` / `gate_not_side.png` | burnt·side | `burnt` | `side` | `textures/item/gate_not_burnt_side.png` |
| `gate_not_burnt_bottom.png` / `gate_not_bottom.png` | burnt·bottom | `burnt` | `bottom` | `textures/item/gate_not_burnt_bottom.png` |
| `gate_not_rev_top.png` / `gate_not_rev.png` | rev·top | `rev` | `top` | `textures/item/gate_not_rev_top.png` |
| `gate_not_rev_side.png` / `gate_not_side.png` | rev·side | `rev` | `side` | `textures/item/gate_not_rev_side.png` |
| `gate_not_rev_bottom.png` / `gate_not_bottom.png` | rev·bottom | `rev` | `bottom` | `textures/item/gate_not_rev_bottom.png` |
| `gate_not_closed_top.png` / `gate_not_closed.png` | closed·top | `closed` | `top` | `textures/item/gate_not_closed_top.png` |
| `gate_not_closed_side.png` / `gate_not_side.png` | closed·side | `closed` | `side` | `textures/item/gate_not_closed_side.png` |
| `gate_not_closed_bottom.png` / `gate_not_bottom.png` | closed·bottom | `closed` | `bottom` | `textures/item/gate_not_closed_bottom.png` |

### `comparator` — Компаратор

* по-английски: Comparator
* kind `GATE`, shape `chip`, размер [13, 4, 8], состояний 6, индексы моделей 1482…1517, *паяется*
* палитра процедурной отрисовки: `#17202a`, `#af7ac5`
* рецепт: `amethyst_shard`×2, `copper_ingot`×1, `glass`×2

| положить (теги) | вместо | состояние | грань | файл в паке |
|---|---|---|---|---|
| `comparator_off_top.png` / `comparator_off.png` / `comparator_top.png` / `comparator.png` | off·top | `off` | `top` | `textures/item/comparator_top.png` |
| `comparator_off_side.png` / `comparator_side.png` | off·side | `off` | `side` | `textures/item/comparator_side.png` |
| `comparator_off_bottom.png` / `comparator_bottom.png` | off·bottom | `off` | `bottom` | `textures/item/comparator_bottom.png` |
| `comparator_dim_top.png` / `comparator_dim.png` | dim·top | `dim` | `top` | `textures/item/comparator_dim_top.png` |
| `comparator_dim_side.png` / `comparator_side.png` | dim·side | `dim` | `side` | `textures/item/comparator_dim_side.png` |
| `comparator_dim_bottom.png` / `comparator_bottom.png` | dim·bottom | `dim` | `bottom` | `textures/item/comparator_dim_bottom.png` |
| `comparator_on_top.png` / `comparator_on.png` | on·top | `on` | `top` | `textures/item/comparator_on_top.png` |
| `comparator_on_side.png` / `comparator_side.png` | on·side | `on` | `side` | `textures/item/comparator_on_side.png` |
| `comparator_on_bottom.png` / `comparator_bottom.png` | on·bottom | `on` | `bottom` | `textures/item/comparator_on_bottom.png` |
| `comparator_burnt_top.png` / `comparator_burnt.png` | burnt·top | `burnt` | `top` | `textures/item/comparator_burnt_top.png` |
| `comparator_burnt_side.png` / `comparator_side.png` | burnt·side | `burnt` | `side` | `textures/item/comparator_burnt_side.png` |
| `comparator_burnt_bottom.png` / `comparator_bottom.png` | burnt·bottom | `burnt` | `bottom` | `textures/item/comparator_burnt_bottom.png` |
| `comparator_rev_top.png` / `comparator_rev.png` | rev·top | `rev` | `top` | `textures/item/comparator_rev_top.png` |
| `comparator_rev_side.png` / `comparator_side.png` | rev·side | `rev` | `side` | `textures/item/comparator_rev_side.png` |
| `comparator_rev_bottom.png` / `comparator_bottom.png` | rev·bottom | `rev` | `bottom` | `textures/item/comparator_rev_bottom.png` |
| `comparator_closed_top.png` / `comparator_closed.png` | closed·top | `closed` | `top` | `textures/item/comparator_closed_top.png` |
| `comparator_closed_side.png` / `comparator_side.png` | closed·side | `closed` | `side` | `textures/item/comparator_closed_side.png` |
| `comparator_closed_bottom.png` / `comparator_bottom.png` | closed·bottom | `closed` | `bottom` | `textures/item/comparator_closed_bottom.png` |

### `buzzer` — Зуммер

* по-английски: Buzzer
* kind `BUZZER`, shape `disc`, размер [10, 4, 10], состояний 6, индексы моделей 1518…1553, *паяется*
* палитра процедурной отрисовки: `#17202a`, `#f39c12`
* рецепт: `copper_ingot`×3, `gold_nugget`×1, `redstone_block`×1

| положить (теги) | вместо | состояние | грань | файл в паке |
|---|---|---|---|---|
| `buzzer_off_top.png` / `buzzer_off.png` / `buzzer_top.png` / `buzzer.png` | off·top | `off` | `top` | `textures/item/buzzer_top.png` |
| `buzzer_off_side.png` / `buzzer_side.png` | off·side | `off` | `side` | `textures/item/buzzer_side.png` |
| `buzzer_off_bottom.png` / `buzzer_bottom.png` | off·bottom | `off` | `bottom` | `textures/item/buzzer_bottom.png` |
| `buzzer_dim_top.png` / `buzzer_dim.png` | dim·top | `dim` | `top` | `textures/item/buzzer_dim_top.png` |
| `buzzer_dim_side.png` / `buzzer_side.png` | dim·side | `dim` | `side` | `textures/item/buzzer_dim_side.png` |
| `buzzer_dim_bottom.png` / `buzzer_bottom.png` | dim·bottom | `dim` | `bottom` | `textures/item/buzzer_dim_bottom.png` |
| `buzzer_on_top.png` / `buzzer_on.png` | on·top | `on` | `top` | `textures/item/buzzer_on_top.png` |
| `buzzer_on_side.png` / `buzzer_side.png` | on·side | `on` | `side` | `textures/item/buzzer_on_side.png` |
| `buzzer_on_bottom.png` / `buzzer_bottom.png` | on·bottom | `on` | `bottom` | `textures/item/buzzer_on_bottom.png` |
| `buzzer_burnt_top.png` / `buzzer_burnt.png` | burnt·top | `burnt` | `top` | `textures/item/buzzer_burnt_top.png` |
| `buzzer_burnt_side.png` / `buzzer_side.png` | burnt·side | `burnt` | `side` | `textures/item/buzzer_burnt_side.png` |
| `buzzer_burnt_bottom.png` / `buzzer_bottom.png` | burnt·bottom | `burnt` | `bottom` | `textures/item/buzzer_burnt_bottom.png` |
| `buzzer_rev_top.png` / `buzzer_rev.png` | rev·top | `rev` | `top` | `textures/item/buzzer_rev_top.png` |
| `buzzer_rev_side.png` / `buzzer_side.png` | rev·side | `rev` | `side` | `textures/item/buzzer_rev_side.png` |
| `buzzer_rev_bottom.png` / `buzzer_bottom.png` | rev·bottom | `rev` | `bottom` | `textures/item/buzzer_rev_bottom.png` |
| `buzzer_closed_top.png` / `buzzer_closed.png` | closed·top | `closed` | `top` | `textures/item/buzzer_closed_top.png` |
| `buzzer_closed_side.png` / `buzzer_side.png` | closed·side | `closed` | `side` | `textures/item/buzzer_closed_side.png` |
| `buzzer_closed_bottom.png` / `buzzer_bottom.png` | closed·bottom | `closed` | `bottom` | `textures/item/buzzer_closed_bottom.png` |

### `motor` — Электромотор

* по-английски: Electric Motor
* kind `MOTOR`, shape `motor`, размер [9, 9, 13], состояний 6, индексы моделей 1554…1589, *паяется*
* палитра процедурной отрисовки: `#95a5a6`, `#e74c3c`
* рецепт: `copper_ingot`×3, `iron_ingot`×1, `redstone`×1

| положить (теги) | вместо | состояние | грань | файл в паке |
|---|---|---|---|---|
| `motor_off_top.png` / `motor_off.png` / `motor_top.png` / `motor.png` | off·top | `off` | `top` | `textures/item/motor_top.png` |
| `motor_off_side.png` / `motor_side.png` | off·side | `off` | `side` | `textures/item/motor_side.png` |
| `motor_off_bottom.png` / `motor_bottom.png` | off·bottom | `off` | `bottom` | `textures/item/motor_bottom.png` |
| `motor_dim_top.png` / `motor_dim.png` | dim·top | `dim` | `top` | `textures/item/motor_dim_top.png` |
| `motor_dim_side.png` / `motor_side.png` | dim·side | `dim` | `side` | `textures/item/motor_dim_side.png` |
| `motor_dim_bottom.png` / `motor_bottom.png` | dim·bottom | `dim` | `bottom` | `textures/item/motor_dim_bottom.png` |
| `motor_on_top.png` / `motor_on.png` | on·top | `on` | `top` | `textures/item/motor_on_top.png` |
| `motor_on_side.png` / `motor_side.png` | on·side | `on` | `side` | `textures/item/motor_on_side.png` |
| `motor_on_bottom.png` / `motor_bottom.png` | on·bottom | `on` | `bottom` | `textures/item/motor_on_bottom.png` |
| `motor_burnt_top.png` / `motor_burnt.png` | burnt·top | `burnt` | `top` | `textures/item/motor_burnt_top.png` |
| `motor_burnt_side.png` / `motor_side.png` | burnt·side | `burnt` | `side` | `textures/item/motor_burnt_side.png` |
| `motor_burnt_bottom.png` / `motor_bottom.png` | burnt·bottom | `burnt` | `bottom` | `textures/item/motor_burnt_bottom.png` |
| `motor_rev_top.png` / `motor_rev.png` | rev·top | `rev` | `top` | `textures/item/motor_rev_top.png` |
| `motor_rev_side.png` / `motor_side.png` | rev·side | `rev` | `side` | `textures/item/motor_rev_side.png` |
| `motor_rev_bottom.png` / `motor_bottom.png` | rev·bottom | `rev` | `bottom` | `textures/item/motor_rev_bottom.png` |
| `motor_closed_top.png` / `motor_closed.png` | closed·top | `closed` | `top` | `textures/item/motor_closed_top.png` |
| `motor_closed_side.png` / `motor_side.png` | closed·side | `closed` | `side` | `textures/item/motor_closed_side.png` |
| `motor_closed_bottom.png` / `motor_bottom.png` | closed·bottom | `closed` | `bottom` | `textures/item/motor_closed_bottom.png` |

### `fuse` — Предохранитель 100 мА

* по-английски: Fuse 100 mA
* kind `FUSE`, shape `fuse`, размер [12, 4, 4], состояний 6, индексы моделей 1590…1625, *паяется*
* палитра процедурной отрисовки: `#f0e68c`, `#bdc3c7`, `#e74c3c`
* рецепт: `copper_ingot`×3, `glass`×2, `redstone`×1

| положить (теги) | вместо | состояние | грань | файл в паке |
|---|---|---|---|---|
| `fuse_off_top.png` / `fuse_off.png` / `fuse_top.png` / `fuse.png` | off·top | `off` | `top` | `textures/item/fuse_top.png` |
| `fuse_off_side.png` / `fuse_side.png` | off·side | `off` | `side` | `textures/item/fuse_side.png` |
| `fuse_off_bottom.png` / `fuse_bottom.png` | off·bottom | `off` | `bottom` | `textures/item/fuse_bottom.png` |
| `fuse_dim_top.png` / `fuse_dim.png` | dim·top | `dim` | `top` | `textures/item/fuse_dim_top.png` |
| `fuse_dim_side.png` / `fuse_side.png` | dim·side | `dim` | `side` | `textures/item/fuse_dim_side.png` |
| `fuse_dim_bottom.png` / `fuse_bottom.png` | dim·bottom | `dim` | `bottom` | `textures/item/fuse_dim_bottom.png` |
| `fuse_on_top.png` / `fuse_on.png` | on·top | `on` | `top` | `textures/item/fuse_on_top.png` |
| `fuse_on_side.png` / `fuse_side.png` | on·side | `on` | `side` | `textures/item/fuse_on_side.png` |
| `fuse_on_bottom.png` / `fuse_bottom.png` | on·bottom | `on` | `bottom` | `textures/item/fuse_on_bottom.png` |
| `fuse_burnt_top.png` / `fuse_burnt.png` | burnt·top | `burnt` | `top` | `textures/item/fuse_burnt_top.png` |
| `fuse_burnt_side.png` / `fuse_side.png` | burnt·side | `burnt` | `side` | `textures/item/fuse_burnt_side.png` |
| `fuse_burnt_bottom.png` / `fuse_bottom.png` | burnt·bottom | `burnt` | `bottom` | `textures/item/fuse_burnt_bottom.png` |
| `fuse_rev_top.png` / `fuse_rev.png` | rev·top | `rev` | `top` | `textures/item/fuse_rev_top.png` |
| `fuse_rev_side.png` / `fuse_side.png` | rev·side | `rev` | `side` | `textures/item/fuse_rev_side.png` |
| `fuse_rev_bottom.png` / `fuse_bottom.png` | rev·bottom | `rev` | `bottom` | `textures/item/fuse_rev_bottom.png` |
| `fuse_closed_top.png` / `fuse_closed.png` | closed·top | `closed` | `top` | `textures/item/fuse_closed_top.png` |
| `fuse_closed_side.png` / `fuse_side.png` | closed·side | `closed` | `side` | `textures/item/fuse_closed_side.png` |
| `fuse_closed_bottom.png` / `fuse_bottom.png` | closed·bottom | `closed` | `bottom` | `textures/item/fuse_closed_bottom.png` |

### `voltmeter` — Вольтметр

* по-английски: Voltmeter
* kind `VOLTMETER`, shape `meter`, размер [14, 8, 10], состояний 6, индексы моделей 1626…1661, *паяется*
* палитра процедурной отрисовки: `#f7dc6f`, `#212f3d`, `#e74c3c`
* рецепт: `copper_ingot`×2, `glass`×2, `iron_ingot`×2, `redstone`×1

| положить (теги) | вместо | состояние | грань | файл в паке |
|---|---|---|---|---|
| `voltmeter_off_top.png` / `voltmeter_off.png` / `voltmeter_top.png` / `voltmeter.png` | off·top | `off` | `top` | `textures/item/voltmeter_top.png` |
| `voltmeter_off_side.png` / `voltmeter_side.png` | off·side | `off` | `side` | `textures/item/voltmeter_side.png` |
| `voltmeter_off_bottom.png` / `voltmeter_bottom.png` | off·bottom | `off` | `bottom` | `textures/item/voltmeter_bottom.png` |
| `voltmeter_dim_top.png` / `voltmeter_dim.png` | dim·top | `dim` | `top` | `textures/item/voltmeter_dim_top.png` |
| `voltmeter_dim_side.png` / `voltmeter_side.png` | dim·side | `dim` | `side` | `textures/item/voltmeter_dim_side.png` |
| `voltmeter_dim_bottom.png` / `voltmeter_bottom.png` | dim·bottom | `dim` | `bottom` | `textures/item/voltmeter_dim_bottom.png` |
| `voltmeter_on_top.png` / `voltmeter_on.png` | on·top | `on` | `top` | `textures/item/voltmeter_on_top.png` |
| `voltmeter_on_side.png` / `voltmeter_side.png` | on·side | `on` | `side` | `textures/item/voltmeter_on_side.png` |
| `voltmeter_on_bottom.png` / `voltmeter_bottom.png` | on·bottom | `on` | `bottom` | `textures/item/voltmeter_on_bottom.png` |
| `voltmeter_burnt_top.png` / `voltmeter_burnt.png` | burnt·top | `burnt` | `top` | `textures/item/voltmeter_burnt_top.png` |
| `voltmeter_burnt_side.png` / `voltmeter_side.png` | burnt·side | `burnt` | `side` | `textures/item/voltmeter_burnt_side.png` |
| `voltmeter_burnt_bottom.png` / `voltmeter_bottom.png` | burnt·bottom | `burnt` | `bottom` | `textures/item/voltmeter_burnt_bottom.png` |
| `voltmeter_rev_top.png` / `voltmeter_rev.png` | rev·top | `rev` | `top` | `textures/item/voltmeter_rev_top.png` |
| `voltmeter_rev_side.png` / `voltmeter_side.png` | rev·side | `rev` | `side` | `textures/item/voltmeter_rev_side.png` |
| `voltmeter_rev_bottom.png` / `voltmeter_bottom.png` | rev·bottom | `rev` | `bottom` | `textures/item/voltmeter_rev_bottom.png` |
| `voltmeter_closed_top.png` / `voltmeter_closed.png` | closed·top | `closed` | `top` | `textures/item/voltmeter_closed_top.png` |
| `voltmeter_closed_side.png` / `voltmeter_side.png` | closed·side | `closed` | `side` | `textures/item/voltmeter_closed_side.png` |
| `voltmeter_closed_bottom.png` / `voltmeter_bottom.png` | closed·bottom | `closed` | `bottom` | `textures/item/voltmeter_closed_bottom.png` |

### `oscilloscope` — Осциллограф

* по-английски: Oscilloscope
* kind `SCOPE`, shape `scope`, размер [14, 10, 12], состояний 6, индексы моделей 1662…1697, *паяется*
* палитра процедурной отрисовки: `#0e1f16`, `#3ddc84`, `#212f3d`
* рецепт: `copper_ingot`×2, `glass`×2, `iron_ingot`×1, `redstone`×3

| положить (теги) | вместо | состояние | грань | файл в паке |
|---|---|---|---|---|
| `oscilloscope_off_top.png` / `oscilloscope_off.png` / `oscilloscope_top.png` / `oscilloscope.png` | off·top | `off` | `top` | `textures/item/oscilloscope_top.png` |
| `oscilloscope_off_side.png` / `oscilloscope_side.png` | off·side | `off` | `side` | `textures/item/oscilloscope_side.png` |
| `oscilloscope_off_bottom.png` / `oscilloscope_bottom.png` | off·bottom | `off` | `bottom` | `textures/item/oscilloscope_bottom.png` |
| `oscilloscope_dim_top.png` / `oscilloscope_dim.png` | dim·top | `dim` | `top` | `textures/item/oscilloscope_dim_top.png` |
| `oscilloscope_dim_side.png` / `oscilloscope_side.png` | dim·side | `dim` | `side` | `textures/item/oscilloscope_dim_side.png` |
| `oscilloscope_dim_bottom.png` / `oscilloscope_bottom.png` | dim·bottom | `dim` | `bottom` | `textures/item/oscilloscope_dim_bottom.png` |
| `oscilloscope_on_top.png` / `oscilloscope_on.png` | on·top | `on` | `top` | `textures/item/oscilloscope_on_top.png` |
| `oscilloscope_on_side.png` / `oscilloscope_side.png` | on·side | `on` | `side` | `textures/item/oscilloscope_on_side.png` |
| `oscilloscope_on_bottom.png` / `oscilloscope_bottom.png` | on·bottom | `on` | `bottom` | `textures/item/oscilloscope_on_bottom.png` |
| `oscilloscope_burnt_top.png` / `oscilloscope_burnt.png` | burnt·top | `burnt` | `top` | `textures/item/oscilloscope_burnt_top.png` |
| `oscilloscope_burnt_side.png` / `oscilloscope_side.png` | burnt·side | `burnt` | `side` | `textures/item/oscilloscope_burnt_side.png` |
| `oscilloscope_burnt_bottom.png` / `oscilloscope_bottom.png` | burnt·bottom | `burnt` | `bottom` | `textures/item/oscilloscope_burnt_bottom.png` |
| `oscilloscope_rev_top.png` / `oscilloscope_rev.png` | rev·top | `rev` | `top` | `textures/item/oscilloscope_rev_top.png` |
| `oscilloscope_rev_side.png` / `oscilloscope_side.png` | rev·side | `rev` | `side` | `textures/item/oscilloscope_rev_side.png` |
| `oscilloscope_rev_bottom.png` / `oscilloscope_bottom.png` | rev·bottom | `rev` | `bottom` | `textures/item/oscilloscope_rev_bottom.png` |
| `oscilloscope_closed_top.png` / `oscilloscope_closed.png` | closed·top | `closed` | `top` | `textures/item/oscilloscope_closed_top.png` |
| `oscilloscope_closed_side.png` / `oscilloscope_side.png` | closed·side | `closed` | `side` | `textures/item/oscilloscope_closed_side.png` |
| `oscilloscope_closed_bottom.png` / `oscilloscope_bottom.png` | closed·bottom | `closed` | `bottom` | `textures/item/oscilloscope_closed_bottom.png` |

### `ammeter` — Амперметр

* по-английски: Ammeter
* kind `AMMETER`, shape `meter`, размер [14, 8, 10], состояний 6, индексы моделей 1698…1733, *паяется*
* палитра процедурной отрисовки: `#f7dc6f`, `#212f3d`, `#3498db`
* рецепт: `copper_ingot`×2, `glass`×2, `iron_ingot`×2, `lapis_lazuli`×1

| положить (теги) | вместо | состояние | грань | файл в паке |
|---|---|---|---|---|
| `ammeter_off_top.png` / `ammeter_off.png` / `ammeter_top.png` / `ammeter.png` | off·top | `off` | `top` | `textures/item/ammeter_top.png` |
| `ammeter_off_side.png` / `ammeter_side.png` | off·side | `off` | `side` | `textures/item/ammeter_side.png` |
| `ammeter_off_bottom.png` / `ammeter_bottom.png` | off·bottom | `off` | `bottom` | `textures/item/ammeter_bottom.png` |
| `ammeter_dim_top.png` / `ammeter_dim.png` | dim·top | `dim` | `top` | `textures/item/ammeter_dim_top.png` |
| `ammeter_dim_side.png` / `ammeter_side.png` | dim·side | `dim` | `side` | `textures/item/ammeter_dim_side.png` |
| `ammeter_dim_bottom.png` / `ammeter_bottom.png` | dim·bottom | `dim` | `bottom` | `textures/item/ammeter_dim_bottom.png` |
| `ammeter_on_top.png` / `ammeter_on.png` | on·top | `on` | `top` | `textures/item/ammeter_on_top.png` |
| `ammeter_on_side.png` / `ammeter_side.png` | on·side | `on` | `side` | `textures/item/ammeter_on_side.png` |
| `ammeter_on_bottom.png` / `ammeter_bottom.png` | on·bottom | `on` | `bottom` | `textures/item/ammeter_on_bottom.png` |
| `ammeter_burnt_top.png` / `ammeter_burnt.png` | burnt·top | `burnt` | `top` | `textures/item/ammeter_burnt_top.png` |
| `ammeter_burnt_side.png` / `ammeter_side.png` | burnt·side | `burnt` | `side` | `textures/item/ammeter_burnt_side.png` |
| `ammeter_burnt_bottom.png` / `ammeter_bottom.png` | burnt·bottom | `burnt` | `bottom` | `textures/item/ammeter_burnt_bottom.png` |
| `ammeter_rev_top.png` / `ammeter_rev.png` | rev·top | `rev` | `top` | `textures/item/ammeter_rev_top.png` |
| `ammeter_rev_side.png` / `ammeter_side.png` | rev·side | `rev` | `side` | `textures/item/ammeter_rev_side.png` |
| `ammeter_rev_bottom.png` / `ammeter_bottom.png` | rev·bottom | `rev` | `bottom` | `textures/item/ammeter_rev_bottom.png` |
| `ammeter_closed_top.png` / `ammeter_closed.png` | closed·top | `closed` | `top` | `textures/item/ammeter_closed_top.png` |
| `ammeter_closed_side.png` / `ammeter_side.png` | closed·side | `closed` | `side` | `textures/item/ammeter_closed_side.png` |
| `ammeter_closed_bottom.png` / `ammeter_bottom.png` | closed·bottom | `closed` | `bottom` | `textures/item/ammeter_closed_bottom.png` |

### `schematic` — Пустая схема

* по-английски: Blank Schematic
* kind `SCHEMATIC`, shape `paper`, размер [15, 1, 15], состояний 2, индексы моделей 1734…1745
* палитра процедурной отрисовки: `#fdf6e3`, `#5dade2`, `#2c3e50`
* рецепт: `copper_ingot`×1, `paper`×7, `redstone`×1

| положить (теги) | вместо | состояние | грань | файл в паке |
|---|---|---|---|---|
| `schematic_off_top.png` / `schematic_off.png` / `schematic_top.png` / `schematic.png` | off·top | `off` | `top` | `textures/item/schematic_top.png` |
| `schematic_off_side.png` / `schematic_side.png` | off·side | `off` | `side` | `textures/item/schematic_side.png` |
| `schematic_off_bottom.png` / `schematic_bottom.png` | off·bottom | `off` | `bottom` | `textures/item/schematic_bottom.png` |
| `schematic_dim_top.png` / `schematic_dim.png` | dim·top | `dim` | `top` | `textures/item/schematic_dim_top.png` |
| `schematic_dim_side.png` / `schematic_side.png` | dim·side | `dim` | `side` | `textures/item/schematic_dim_side.png` |
| `schematic_dim_bottom.png` / `schematic_bottom.png` | dim·bottom | `dim` | `bottom` | `textures/item/schematic_dim_bottom.png` |

### `multimeter` — Мультиметр

* по-английски: Multimeter
* kind `MULTIMETER`, shape `handheld`, размер [9, 2, 13], состояний 6, индексы моделей 1746…1781, *паяется*
* палитра процедурной отрисовки: `#f4d03f`, `#212f3d`, `#e74c3c`
* рецепт: `clock`×1, `copper_ingot`×2, `glass`×2, `iron_ingot`×2, `redstone`×1

| положить (теги) | вместо | состояние | грань | файл в паке |
|---|---|---|---|---|
| `multimeter_off_top.png` / `multimeter_off.png` / `multimeter_top.png` / `multimeter.png` | off·top | `off` | `top` | `textures/item/multimeter_top.png` |
| `multimeter_off_side.png` / `multimeter_side.png` | off·side | `off` | `side` | `textures/item/multimeter_side.png` |
| `multimeter_off_bottom.png` / `multimeter_bottom.png` | off·bottom | `off` | `bottom` | `textures/item/multimeter_bottom.png` |
| `multimeter_dim_top.png` / `multimeter_dim.png` | dim·top | `dim` | `top` | `textures/item/multimeter_dim_top.png` |
| `multimeter_dim_side.png` / `multimeter_side.png` | dim·side | `dim` | `side` | `textures/item/multimeter_dim_side.png` |
| `multimeter_dim_bottom.png` / `multimeter_bottom.png` | dim·bottom | `dim` | `bottom` | `textures/item/multimeter_dim_bottom.png` |
| `multimeter_on_top.png` / `multimeter_on.png` | on·top | `on` | `top` | `textures/item/multimeter_on_top.png` |
| `multimeter_on_side.png` / `multimeter_side.png` | on·side | `on` | `side` | `textures/item/multimeter_on_side.png` |
| `multimeter_on_bottom.png` / `multimeter_bottom.png` | on·bottom | `on` | `bottom` | `textures/item/multimeter_on_bottom.png` |
| `multimeter_burnt_top.png` / `multimeter_burnt.png` | burnt·top | `burnt` | `top` | `textures/item/multimeter_burnt_top.png` |
| `multimeter_burnt_side.png` / `multimeter_side.png` | burnt·side | `burnt` | `side` | `textures/item/multimeter_burnt_side.png` |
| `multimeter_burnt_bottom.png` / `multimeter_bottom.png` | burnt·bottom | `burnt` | `bottom` | `textures/item/multimeter_burnt_bottom.png` |
| `multimeter_rev_top.png` / `multimeter_rev.png` | rev·top | `rev` | `top` | `textures/item/multimeter_rev_top.png` |
| `multimeter_rev_side.png` / `multimeter_side.png` | rev·side | `rev` | `side` | `textures/item/multimeter_rev_side.png` |
| `multimeter_rev_bottom.png` / `multimeter_bottom.png` | rev·bottom | `rev` | `bottom` | `textures/item/multimeter_rev_bottom.png` |
| `multimeter_closed_top.png` / `multimeter_closed.png` | closed·top | `closed` | `top` | `textures/item/multimeter_closed_top.png` |
| `multimeter_closed_side.png` / `multimeter_side.png` | closed·side | `closed` | `side` | `textures/item/multimeter_closed_side.png` |
| `multimeter_closed_bottom.png` / `multimeter_bottom.png` | closed·bottom | `closed` | `bottom` | `textures/item/multimeter_closed_bottom.png` |

### `wire_cutter` — Кусачки

* по-английски: Wire Cutters
* kind `CUTTER`, shape `handheld`, размер [4, 2, 14], состояний 1, индексы моделей 1782…1787
* палитра процедурной отрисовки: `#95a5a6`, `#e74c3c`
* рецепт: `iron_ingot`×2, `stick`×2

| положить (теги) | вместо | состояние | грань | файл в паке |
|---|---|---|---|---|
| `wire_cutter_off_top.png` / `wire_cutter_off.png` / `wire_cutter_top.png` / `wire_cutter.png` | off·top | `off` | `top` | `textures/item/wire_cutter_top.png` |
| `wire_cutter_off_side.png` / `wire_cutter_side.png` | off·side | `off` | `side` | `textures/item/wire_cutter_side.png` |
| `wire_cutter_off_bottom.png` / `wire_cutter_bottom.png` | off·bottom | `off` | `bottom` | `textures/item/wire_cutter_bottom.png` |

(состояний один набор; адреса остальных пяти состояний существуют —
плагин может попросить любой из шести — и ведут на ту же модель)

### `led_probe` — Пробник со светодиодом

* по-английски: LED Test Probe
* kind `PROBE`, shape `handheld`, размер [4, 2, 15], состояний 6, индексы моделей 1788…1823
* палитра процедурной отрисовки: `#ecf0f1`, `#e74c3c`
* рецепт: `glass`×1, `redstone`×1, `stick`×1

| положить (теги) | вместо | состояние | грань | файл в паке |
|---|---|---|---|---|
| `led_probe_off_top.png` / `led_probe_off.png` / `led_probe_top.png` / `led_probe.png` | off·top | `off` | `top` | `textures/item/led_probe_top.png` |
| `led_probe_off_side.png` / `led_probe_side.png` | off·side | `off` | `side` | `textures/item/led_probe_side.png` |
| `led_probe_off_bottom.png` / `led_probe_bottom.png` | off·bottom | `off` | `bottom` | `textures/item/led_probe_bottom.png` |
| `led_probe_dim_top.png` / `led_probe_dim.png` | dim·top | `dim` | `top` | `textures/item/led_probe_dim_top.png` |
| `led_probe_dim_side.png` / `led_probe_side.png` | dim·side | `dim` | `side` | `textures/item/led_probe_dim_side.png` |
| `led_probe_dim_bottom.png` / `led_probe_bottom.png` | dim·bottom | `dim` | `bottom` | `textures/item/led_probe_dim_bottom.png` |
| `led_probe_on_top.png` / `led_probe_on.png` | on·top | `on` | `top` | `textures/item/led_probe_on_top.png` |
| `led_probe_on_side.png` / `led_probe_side.png` | on·side | `on` | `side` | `textures/item/led_probe_on_side.png` |
| `led_probe_on_bottom.png` / `led_probe_bottom.png` | on·bottom | `on` | `bottom` | `textures/item/led_probe_on_bottom.png` |
| `led_probe_burnt_top.png` / `led_probe_burnt.png` | burnt·top | `burnt` | `top` | `textures/item/led_probe_burnt_top.png` |
| `led_probe_burnt_side.png` / `led_probe_side.png` | burnt·side | `burnt` | `side` | `textures/item/led_probe_burnt_side.png` |
| `led_probe_burnt_bottom.png` / `led_probe_bottom.png` | burnt·bottom | `burnt` | `bottom` | `textures/item/led_probe_burnt_bottom.png` |
| `led_probe_rev_top.png` / `led_probe_rev.png` | rev·top | `rev` | `top` | `textures/item/led_probe_rev_top.png` |
| `led_probe_rev_side.png` / `led_probe_side.png` | rev·side | `rev` | `side` | `textures/item/led_probe_rev_side.png` |
| `led_probe_rev_bottom.png` / `led_probe_bottom.png` | rev·bottom | `rev` | `bottom` | `textures/item/led_probe_rev_bottom.png` |
| `led_probe_closed_top.png` / `led_probe_closed.png` | closed·top | `closed` | `top` | `textures/item/led_probe_closed_top.png` |
| `led_probe_closed_side.png` / `led_probe_side.png` | closed·side | `closed` | `side` | `textures/item/led_probe_closed_side.png` |
| `led_probe_closed_bottom.png` / `led_probe_bottom.png` | closed·bottom | `closed` | `bottom` | `textures/item/led_probe_closed_bottom.png` |

### `empty_circuit` — Пустая заготовка платы

* по-английски: Blank Circuit Board
* kind `EMPTY`, shape `plate`, размер [15, 1, 15], состояний 1, индексы моделей 1824…1829
* палитра процедурной отрисовки: `#1e8449`, `#a9dfbf`, `#f7dc6f`
* рецепт: `gold_nugget`×4, `paper`×1, `redstone`×4

| положить (теги) | вместо | состояние | грань | файл в паке |
|---|---|---|---|---|
| `empty_circuit_off_top.png` / `empty_circuit_off.png` / `empty_circuit_top.png` / `empty_circuit.png` | off·top | `off` | `top` | `textures/item/empty_circuit_top.png` |
| `empty_circuit_off_side.png` / `empty_circuit_side.png` | off·side | `off` | `side` | `textures/item/empty_circuit_side.png` |
| `empty_circuit_off_bottom.png` / `empty_circuit_bottom.png` | off·bottom | `off` | `bottom` | `textures/item/empty_circuit_bottom.png` |

(состояний один набор; адреса остальных пяти состояний существуют —
плагин может попросить любой из шести — и ведут на ту же модель)

### `magnet` — Постоянный магнит

* по-английски: Permanent Magnet
* kind `MAGNET`, shape `magnet`, размер [12, 6, 12], состояний 6, индексы моделей 1830…1865
* палитра процедурной отрисовки: `#26364f`, `#8b97a6`, `#c0392b`
* рецепт: `iron_ingot`×2, `redstone_block`×1

| положить (теги) | вместо | состояние | грань | файл в паке |
|---|---|---|---|---|
| `magnet_off_top.png` / `magnet_off.png` / `magnet_top.png` / `magnet.png` | off·top | `off` | `top` | `textures/item/magnet_top.png` |
| `magnet_off_side.png` / `magnet_side.png` | off·side | `off` | `side` | `textures/item/magnet_side.png` |
| `magnet_off_bottom.png` / `magnet_bottom.png` | off·bottom | `off` | `bottom` | `textures/item/magnet_bottom.png` |
| `magnet_dim_top.png` / `magnet_dim.png` | dim·top | `dim` | `top` | `textures/item/magnet_dim_top.png` |
| `magnet_dim_side.png` / `magnet_side.png` | dim·side | `dim` | `side` | `textures/item/magnet_dim_side.png` |
| `magnet_dim_bottom.png` / `magnet_bottom.png` | dim·bottom | `dim` | `bottom` | `textures/item/magnet_dim_bottom.png` |
| `magnet_on_top.png` / `magnet_on.png` | on·top | `on` | `top` | `textures/item/magnet_on_top.png` |
| `magnet_on_side.png` / `magnet_side.png` | on·side | `on` | `side` | `textures/item/magnet_on_side.png` |
| `magnet_on_bottom.png` / `magnet_bottom.png` | on·bottom | `on` | `bottom` | `textures/item/magnet_on_bottom.png` |
| `magnet_burnt_top.png` / `magnet_burnt.png` | burnt·top | `burnt` | `top` | `textures/item/magnet_burnt_top.png` |
| `magnet_burnt_side.png` / `magnet_side.png` | burnt·side | `burnt` | `side` | `textures/item/magnet_burnt_side.png` |
| `magnet_burnt_bottom.png` / `magnet_bottom.png` | burnt·bottom | `burnt` | `bottom` | `textures/item/magnet_burnt_bottom.png` |
| `magnet_rev_top.png` / `magnet_rev.png` | rev·top | `rev` | `top` | `textures/item/magnet_rev_top.png` |
| `magnet_rev_side.png` / `magnet_side.png` | rev·side | `rev` | `side` | `textures/item/magnet_rev_side.png` |
| `magnet_rev_bottom.png` / `magnet_bottom.png` | rev·bottom | `rev` | `bottom` | `textures/item/magnet_rev_bottom.png` |
| `magnet_closed_top.png` / `magnet_closed.png` | closed·top | `closed` | `top` | `textures/item/magnet_closed_top.png` |
| `magnet_closed_side.png` / `magnet_side.png` | closed·side | `closed` | `side` | `textures/item/magnet_closed_side.png` |
| `magnet_closed_bottom.png` / `magnet_bottom.png` | closed·bottom | `closed` | `bottom` | `textures/item/magnet_closed_bottom.png` |

### `electromagnet` — Электромагнит

* по-английски: Electromagnet
* kind `ELECTROMAGNET`, shape `coil`, размер [12, 10, 12], состояний 6, индексы моделей 1866…1901, *паяется*
* палитра процедурной отрисовки: `#b3541e`, `#f0c27b`, `#5d6b7a`
* рецепт: `copper_ingot`×4, `iron_ingot`×1

| положить (теги) | вместо | состояние | грань | файл в паке |
|---|---|---|---|---|
| `electromagnet_off_top.png` / `electromagnet_off.png` / `electromagnet_top.png` / `electromagnet.png` | off·top | `off` | `top` | `textures/item/electromagnet_top.png` |
| `electromagnet_off_side.png` / `electromagnet_side.png` | off·side | `off` | `side` | `textures/item/electromagnet_side.png` |
| `electromagnet_off_bottom.png` / `electromagnet_bottom.png` | off·bottom | `off` | `bottom` | `textures/item/electromagnet_bottom.png` |
| `electromagnet_dim_top.png` / `electromagnet_dim.png` | dim·top | `dim` | `top` | `textures/item/electromagnet_dim_top.png` |
| `electromagnet_dim_side.png` / `electromagnet_side.png` | dim·side | `dim` | `side` | `textures/item/electromagnet_dim_side.png` |
| `electromagnet_dim_bottom.png` / `electromagnet_bottom.png` | dim·bottom | `dim` | `bottom` | `textures/item/electromagnet_dim_bottom.png` |
| `electromagnet_on_top.png` / `electromagnet_on.png` | on·top | `on` | `top` | `textures/item/electromagnet_on_top.png` |
| `electromagnet_on_side.png` / `electromagnet_side.png` | on·side | `on` | `side` | `textures/item/electromagnet_on_side.png` |
| `electromagnet_on_bottom.png` / `electromagnet_bottom.png` | on·bottom | `on` | `bottom` | `textures/item/electromagnet_on_bottom.png` |
| `electromagnet_burnt_top.png` / `electromagnet_burnt.png` | burnt·top | `burnt` | `top` | `textures/item/electromagnet_burnt_top.png` |
| `electromagnet_burnt_side.png` / `electromagnet_side.png` | burnt·side | `burnt` | `side` | `textures/item/electromagnet_burnt_side.png` |
| `electromagnet_burnt_bottom.png` / `electromagnet_bottom.png` | burnt·bottom | `burnt` | `bottom` | `textures/item/electromagnet_burnt_bottom.png` |
| `electromagnet_rev_top.png` / `electromagnet_rev.png` | rev·top | `rev` | `top` | `textures/item/electromagnet_rev_top.png` |
| `electromagnet_rev_side.png` / `electromagnet_side.png` | rev·side | `rev` | `side` | `textures/item/electromagnet_rev_side.png` |
| `electromagnet_rev_bottom.png` / `electromagnet_bottom.png` | rev·bottom | `rev` | `bottom` | `textures/item/electromagnet_rev_bottom.png` |
| `electromagnet_closed_top.png` / `electromagnet_closed.png` | closed·top | `closed` | `top` | `textures/item/electromagnet_closed_top.png` |
| `electromagnet_closed_side.png` / `electromagnet_side.png` | closed·side | `closed` | `side` | `textures/item/electromagnet_closed_side.png` |
| `electromagnet_closed_bottom.png` / `electromagnet_bottom.png` | closed·bottom | `closed` | `bottom` | `textures/item/electromagnet_closed_bottom.png` |

### `inductor_10mh` — Катушка 10 мГн

* по-английски: Inductor 10 mH
* kind `INDUCTOR`, shape `coil`, размер [11, 9, 11], состояний 6, индексы моделей 1902…1937, *паяется*
* палитра процедурной отрисовки: `#c9772f`, `#7f8c9a`
* рецепт: `iron_nugget`×1, `string`×2

| положить (теги) | вместо | состояние | грань | файл в паке |
|---|---|---|---|---|
| `inductor_10mh_off_top.png` / `inductor_10mh_off.png` / `inductor_10mh_top.png` / `inductor_10mh.png` | off·top | `off` | `top` | `textures/item/inductor_10mh_top.png` |
| `inductor_10mh_off_side.png` / `inductor_10mh_side.png` | off·side | `off` | `side` | `textures/item/inductor_10mh_side.png` |
| `inductor_10mh_off_bottom.png` / `inductor_10mh_bottom.png` | off·bottom | `off` | `bottom` | `textures/item/inductor_10mh_bottom.png` |
| `inductor_10mh_dim_top.png` / `inductor_10mh_dim.png` | dim·top | `dim` | `top` | `textures/item/inductor_10mh_dim_top.png` |
| `inductor_10mh_dim_side.png` / `inductor_10mh_side.png` | dim·side | `dim` | `side` | `textures/item/inductor_10mh_dim_side.png` |
| `inductor_10mh_dim_bottom.png` / `inductor_10mh_bottom.png` | dim·bottom | `dim` | `bottom` | `textures/item/inductor_10mh_dim_bottom.png` |
| `inductor_10mh_on_top.png` / `inductor_10mh_on.png` | on·top | `on` | `top` | `textures/item/inductor_10mh_on_top.png` |
| `inductor_10mh_on_side.png` / `inductor_10mh_side.png` | on·side | `on` | `side` | `textures/item/inductor_10mh_on_side.png` |
| `inductor_10mh_on_bottom.png` / `inductor_10mh_bottom.png` | on·bottom | `on` | `bottom` | `textures/item/inductor_10mh_on_bottom.png` |
| `inductor_10mh_burnt_top.png` / `inductor_10mh_burnt.png` | burnt·top | `burnt` | `top` | `textures/item/inductor_10mh_burnt_top.png` |
| `inductor_10mh_burnt_side.png` / `inductor_10mh_side.png` | burnt·side | `burnt` | `side` | `textures/item/inductor_10mh_burnt_side.png` |
| `inductor_10mh_burnt_bottom.png` / `inductor_10mh_bottom.png` | burnt·bottom | `burnt` | `bottom` | `textures/item/inductor_10mh_burnt_bottom.png` |
| `inductor_10mh_rev_top.png` / `inductor_10mh_rev.png` | rev·top | `rev` | `top` | `textures/item/inductor_10mh_rev_top.png` |
| `inductor_10mh_rev_side.png` / `inductor_10mh_side.png` | rev·side | `rev` | `side` | `textures/item/inductor_10mh_rev_side.png` |
| `inductor_10mh_rev_bottom.png` / `inductor_10mh_bottom.png` | rev·bottom | `rev` | `bottom` | `textures/item/inductor_10mh_rev_bottom.png` |
| `inductor_10mh_closed_top.png` / `inductor_10mh_closed.png` | closed·top | `closed` | `top` | `textures/item/inductor_10mh_closed_top.png` |
| `inductor_10mh_closed_side.png` / `inductor_10mh_side.png` | closed·side | `closed` | `side` | `textures/item/inductor_10mh_closed_side.png` |
| `inductor_10mh_closed_bottom.png` / `inductor_10mh_bottom.png` | closed·bottom | `closed` | `bottom` | `textures/item/inductor_10mh_closed_bottom.png` |

### `choke_10h` — Дроссель 10 Гн

* по-английски: Choke 10 H
* kind `INDUCTOR`, shape `coil`, размер [13, 11, 13], состояний 6, индексы моделей 1938…1973, *паяется*
* палитра процедурной отрисовки: `#3d5a80`, `#c9772f`
* рецепт: `copper_ingot`×6, `iron_block`×1

| положить (теги) | вместо | состояние | грань | файл в паке |
|---|---|---|---|---|
| `choke_10h_off_top.png` / `choke_10h_off.png` / `choke_10h_top.png` / `choke_10h.png` | off·top | `off` | `top` | `textures/item/choke_10h_top.png` |
| `choke_10h_off_side.png` / `choke_10h_side.png` | off·side | `off` | `side` | `textures/item/choke_10h_side.png` |
| `choke_10h_off_bottom.png` / `choke_10h_bottom.png` | off·bottom | `off` | `bottom` | `textures/item/choke_10h_bottom.png` |
| `choke_10h_dim_top.png` / `choke_10h_dim.png` | dim·top | `dim` | `top` | `textures/item/choke_10h_dim_top.png` |
| `choke_10h_dim_side.png` / `choke_10h_side.png` | dim·side | `dim` | `side` | `textures/item/choke_10h_dim_side.png` |
| `choke_10h_dim_bottom.png` / `choke_10h_bottom.png` | dim·bottom | `dim` | `bottom` | `textures/item/choke_10h_dim_bottom.png` |
| `choke_10h_on_top.png` / `choke_10h_on.png` | on·top | `on` | `top` | `textures/item/choke_10h_on_top.png` |
| `choke_10h_on_side.png` / `choke_10h_side.png` | on·side | `on` | `side` | `textures/item/choke_10h_on_side.png` |
| `choke_10h_on_bottom.png` / `choke_10h_bottom.png` | on·bottom | `on` | `bottom` | `textures/item/choke_10h_on_bottom.png` |
| `choke_10h_burnt_top.png` / `choke_10h_burnt.png` | burnt·top | `burnt` | `top` | `textures/item/choke_10h_burnt_top.png` |
| `choke_10h_burnt_side.png` / `choke_10h_side.png` | burnt·side | `burnt` | `side` | `textures/item/choke_10h_burnt_side.png` |
| `choke_10h_burnt_bottom.png` / `choke_10h_bottom.png` | burnt·bottom | `burnt` | `bottom` | `textures/item/choke_10h_burnt_bottom.png` |
| `choke_10h_rev_top.png` / `choke_10h_rev.png` | rev·top | `rev` | `top` | `textures/item/choke_10h_rev_top.png` |
| `choke_10h_rev_side.png` / `choke_10h_side.png` | rev·side | `rev` | `side` | `textures/item/choke_10h_rev_side.png` |
| `choke_10h_rev_bottom.png` / `choke_10h_bottom.png` | rev·bottom | `rev` | `bottom` | `textures/item/choke_10h_rev_bottom.png` |
| `choke_10h_closed_top.png` / `choke_10h_closed.png` | closed·top | `closed` | `top` | `textures/item/choke_10h_closed_top.png` |
| `choke_10h_closed_side.png` / `choke_10h_side.png` | closed·side | `closed` | `side` | `textures/item/choke_10h_closed_side.png` |
| `choke_10h_closed_bottom.png` / `choke_10h_bottom.png` | closed·bottom | `closed` | `bottom` | `textures/item/choke_10h_closed_bottom.png` |

### `relay_5v` — Реле 5 В

* по-английски: Relay 5 V
* kind `RELAY`, shape `relay`, размер [13, 10, 13], состояний 6, индексы моделей 1974…2009, *паяется*
* палитра процедурной отрисовки: `#1e2b3a`, `#c9772f`, `#e0e0e0`
* рецепт: `copper_ingot`×2, `gold_nugget`×1, `iron_ingot`×1, `redstone`×2

| положить (теги) | вместо | состояние | грань | файл в паке |
|---|---|---|---|---|
| `relay_5v_off_top.png` / `relay_5v_off.png` / `relay_5v_top.png` / `relay_5v.png` | off·top | `off` | `top` | `textures/item/relay_5v_top.png` |
| `relay_5v_off_side.png` / `relay_5v_side.png` | off·side | `off` | `side` | `textures/item/relay_5v_side.png` |
| `relay_5v_off_bottom.png` / `relay_5v_bottom.png` | off·bottom | `off` | `bottom` | `textures/item/relay_5v_bottom.png` |
| `relay_5v_dim_top.png` / `relay_5v_dim.png` | dim·top | `dim` | `top` | `textures/item/relay_5v_dim_top.png` |
| `relay_5v_dim_side.png` / `relay_5v_side.png` | dim·side | `dim` | `side` | `textures/item/relay_5v_dim_side.png` |
| `relay_5v_dim_bottom.png` / `relay_5v_bottom.png` | dim·bottom | `dim` | `bottom` | `textures/item/relay_5v_dim_bottom.png` |
| `relay_5v_on_top.png` / `relay_5v_on.png` | on·top | `on` | `top` | `textures/item/relay_5v_on_top.png` |
| `relay_5v_on_side.png` / `relay_5v_side.png` | on·side | `on` | `side` | `textures/item/relay_5v_on_side.png` |
| `relay_5v_on_bottom.png` / `relay_5v_bottom.png` | on·bottom | `on` | `bottom` | `textures/item/relay_5v_on_bottom.png` |
| `relay_5v_burnt_top.png` / `relay_5v_burnt.png` | burnt·top | `burnt` | `top` | `textures/item/relay_5v_burnt_top.png` |
| `relay_5v_burnt_side.png` / `relay_5v_side.png` | burnt·side | `burnt` | `side` | `textures/item/relay_5v_burnt_side.png` |
| `relay_5v_burnt_bottom.png` / `relay_5v_bottom.png` | burnt·bottom | `burnt` | `bottom` | `textures/item/relay_5v_burnt_bottom.png` |
| `relay_5v_rev_top.png` / `relay_5v_rev.png` | rev·top | `rev` | `top` | `textures/item/relay_5v_rev_top.png` |
| `relay_5v_rev_side.png` / `relay_5v_side.png` | rev·side | `rev` | `side` | `textures/item/relay_5v_rev_side.png` |
| `relay_5v_rev_bottom.png` / `relay_5v_bottom.png` | rev·bottom | `rev` | `bottom` | `textures/item/relay_5v_rev_bottom.png` |
| `relay_5v_closed_top.png` / `relay_5v_closed.png` | closed·top | `closed` | `top` | `textures/item/relay_5v_closed_top.png` |
| `relay_5v_closed_side.png` / `relay_5v_side.png` | closed·side | `closed` | `side` | `textures/item/relay_5v_closed_side.png` |
| `relay_5v_closed_bottom.png` / `relay_5v_bottom.png` | closed·bottom | `closed` | `bottom` | `textures/item/relay_5v_closed_bottom.png` |

### `reed_switch` — Геркон

* по-английски: Reed Switch
* kind `REED`, shape `reed`, размер [13, 5, 7], состояний 6, индексы моделей 2010…2045, *паяется*
* палитра процедурной отрисовки: `#dfe6ea`, `#8b97a6`
* рецепт: `glass`×2, `iron_nugget`×1

| положить (теги) | вместо | состояние | грань | файл в паке |
|---|---|---|---|---|
| `reed_switch_off_top.png` / `reed_switch_off.png` / `reed_switch_top.png` / `reed_switch.png` | off·top | `off` | `top` | `textures/item/reed_switch_top.png` |
| `reed_switch_off_side.png` / `reed_switch_side.png` | off·side | `off` | `side` | `textures/item/reed_switch_side.png` |
| `reed_switch_off_bottom.png` / `reed_switch_bottom.png` | off·bottom | `off` | `bottom` | `textures/item/reed_switch_bottom.png` |
| `reed_switch_dim_top.png` / `reed_switch_dim.png` | dim·top | `dim` | `top` | `textures/item/reed_switch_dim_top.png` |
| `reed_switch_dim_side.png` / `reed_switch_side.png` | dim·side | `dim` | `side` | `textures/item/reed_switch_dim_side.png` |
| `reed_switch_dim_bottom.png` / `reed_switch_bottom.png` | dim·bottom | `dim` | `bottom` | `textures/item/reed_switch_dim_bottom.png` |
| `reed_switch_on_top.png` / `reed_switch_on.png` | on·top | `on` | `top` | `textures/item/reed_switch_on_top.png` |
| `reed_switch_on_side.png` / `reed_switch_side.png` | on·side | `on` | `side` | `textures/item/reed_switch_on_side.png` |
| `reed_switch_on_bottom.png` / `reed_switch_bottom.png` | on·bottom | `on` | `bottom` | `textures/item/reed_switch_on_bottom.png` |
| `reed_switch_burnt_top.png` / `reed_switch_burnt.png` | burnt·top | `burnt` | `top` | `textures/item/reed_switch_burnt_top.png` |
| `reed_switch_burnt_side.png` / `reed_switch_side.png` | burnt·side | `burnt` | `side` | `textures/item/reed_switch_burnt_side.png` |
| `reed_switch_burnt_bottom.png` / `reed_switch_bottom.png` | burnt·bottom | `burnt` | `bottom` | `textures/item/reed_switch_burnt_bottom.png` |
| `reed_switch_rev_top.png` / `reed_switch_rev.png` | rev·top | `rev` | `top` | `textures/item/reed_switch_rev_top.png` |
| `reed_switch_rev_side.png` / `reed_switch_side.png` | rev·side | `rev` | `side` | `textures/item/reed_switch_rev_side.png` |
| `reed_switch_rev_bottom.png` / `reed_switch_bottom.png` | rev·bottom | `rev` | `bottom` | `textures/item/reed_switch_rev_bottom.png` |
| `reed_switch_closed_top.png` / `reed_switch_closed.png` | closed·top | `closed` | `top` | `textures/item/reed_switch_closed_top.png` |
| `reed_switch_closed_side.png` / `reed_switch_side.png` | closed·side | `closed` | `side` | `textures/item/reed_switch_closed_side.png` |
| `reed_switch_closed_bottom.png` / `reed_switch_bottom.png` | closed·bottom | `closed` | `bottom` | `textures/item/reed_switch_closed_bottom.png` |

### `photoresistor` — Фоторезистор

* по-английски: Photoresistor
* kind `RESISTOR`, shape `photo`, размер [11, 8, 11], состояний 6, индексы моделей 2046…2081, *паяется*
* палитра процедурной отрисовки: `#2e7d5b`, `#e8d9a0`
* рецепт: `copper_ingot`×1, `glass`×1, `glowstone_dust`×1

| положить (теги) | вместо | состояние | грань | файл в паке |
|---|---|---|---|---|
| `photoresistor_off_top.png` / `photoresistor_off.png` / `photoresistor_top.png` / `photoresistor.png` | off·top | `off` | `top` | `textures/item/photoresistor_top.png` |
| `photoresistor_off_side.png` / `photoresistor_side.png` | off·side | `off` | `side` | `textures/item/photoresistor_side.png` |
| `photoresistor_off_bottom.png` / `photoresistor_bottom.png` | off·bottom | `off` | `bottom` | `textures/item/photoresistor_bottom.png` |
| `photoresistor_dim_top.png` / `photoresistor_dim.png` | dim·top | `dim` | `top` | `textures/item/photoresistor_dim_top.png` |
| `photoresistor_dim_side.png` / `photoresistor_side.png` | dim·side | `dim` | `side` | `textures/item/photoresistor_dim_side.png` |
| `photoresistor_dim_bottom.png` / `photoresistor_bottom.png` | dim·bottom | `dim` | `bottom` | `textures/item/photoresistor_dim_bottom.png` |
| `photoresistor_on_top.png` / `photoresistor_on.png` | on·top | `on` | `top` | `textures/item/photoresistor_on_top.png` |
| `photoresistor_on_side.png` / `photoresistor_side.png` | on·side | `on` | `side` | `textures/item/photoresistor_on_side.png` |
| `photoresistor_on_bottom.png` / `photoresistor_bottom.png` | on·bottom | `on` | `bottom` | `textures/item/photoresistor_on_bottom.png` |
| `photoresistor_burnt_top.png` / `photoresistor_burnt.png` | burnt·top | `burnt` | `top` | `textures/item/photoresistor_burnt_top.png` |
| `photoresistor_burnt_side.png` / `photoresistor_side.png` | burnt·side | `burnt` | `side` | `textures/item/photoresistor_burnt_side.png` |
| `photoresistor_burnt_bottom.png` / `photoresistor_bottom.png` | burnt·bottom | `burnt` | `bottom` | `textures/item/photoresistor_burnt_bottom.png` |
| `photoresistor_rev_top.png` / `photoresistor_rev.png` | rev·top | `rev` | `top` | `textures/item/photoresistor_rev_top.png` |
| `photoresistor_rev_side.png` / `photoresistor_side.png` | rev·side | `rev` | `side` | `textures/item/photoresistor_rev_side.png` |
| `photoresistor_rev_bottom.png` / `photoresistor_bottom.png` | rev·bottom | `rev` | `bottom` | `textures/item/photoresistor_rev_bottom.png` |
| `photoresistor_closed_top.png` / `photoresistor_closed.png` | closed·top | `closed` | `top` | `textures/item/photoresistor_closed_top.png` |
| `photoresistor_closed_side.png` / `photoresistor_side.png` | closed·side | `closed` | `side` | `textures/item/photoresistor_closed_side.png` |
| `photoresistor_closed_bottom.png` / `photoresistor_bottom.png` | closed·bottom | `closed` | `bottom` | `textures/item/photoresistor_closed_bottom.png` |

### `thermistor_ntc` — Термистор NTC

* по-английски: NTC Thermistor
* kind `RESISTOR`, shape `thermistor`, размер [9, 9, 9], состояний 6, индексы моделей 2082…2117, *паяется*
* палитра процедурной отрисовки: `#1f1f24`, `#c0392b`
* рецепт: `clay_ball`×1, `copper_ingot`×2, `redstone`×1

| положить (теги) | вместо | состояние | грань | файл в паке |
|---|---|---|---|---|
| `thermistor_ntc_off_top.png` / `thermistor_ntc_off.png` / `thermistor_ntc_top.png` / `thermistor_ntc.png` | off·top | `off` | `top` | `textures/item/thermistor_ntc_top.png` |
| `thermistor_ntc_off_side.png` / `thermistor_ntc_side.png` | off·side | `off` | `side` | `textures/item/thermistor_ntc_side.png` |
| `thermistor_ntc_off_bottom.png` / `thermistor_ntc_bottom.png` | off·bottom | `off` | `bottom` | `textures/item/thermistor_ntc_bottom.png` |
| `thermistor_ntc_dim_top.png` / `thermistor_ntc_dim.png` | dim·top | `dim` | `top` | `textures/item/thermistor_ntc_dim_top.png` |
| `thermistor_ntc_dim_side.png` / `thermistor_ntc_side.png` | dim·side | `dim` | `side` | `textures/item/thermistor_ntc_dim_side.png` |
| `thermistor_ntc_dim_bottom.png` / `thermistor_ntc_bottom.png` | dim·bottom | `dim` | `bottom` | `textures/item/thermistor_ntc_dim_bottom.png` |
| `thermistor_ntc_on_top.png` / `thermistor_ntc_on.png` | on·top | `on` | `top` | `textures/item/thermistor_ntc_on_top.png` |
| `thermistor_ntc_on_side.png` / `thermistor_ntc_side.png` | on·side | `on` | `side` | `textures/item/thermistor_ntc_on_side.png` |
| `thermistor_ntc_on_bottom.png` / `thermistor_ntc_bottom.png` | on·bottom | `on` | `bottom` | `textures/item/thermistor_ntc_on_bottom.png` |
| `thermistor_ntc_burnt_top.png` / `thermistor_ntc_burnt.png` | burnt·top | `burnt` | `top` | `textures/item/thermistor_ntc_burnt_top.png` |
| `thermistor_ntc_burnt_side.png` / `thermistor_ntc_side.png` | burnt·side | `burnt` | `side` | `textures/item/thermistor_ntc_burnt_side.png` |
| `thermistor_ntc_burnt_bottom.png` / `thermistor_ntc_bottom.png` | burnt·bottom | `burnt` | `bottom` | `textures/item/thermistor_ntc_burnt_bottom.png` |
| `thermistor_ntc_rev_top.png` / `thermistor_ntc_rev.png` | rev·top | `rev` | `top` | `textures/item/thermistor_ntc_rev_top.png` |
| `thermistor_ntc_rev_side.png` / `thermistor_ntc_side.png` | rev·side | `rev` | `side` | `textures/item/thermistor_ntc_rev_side.png` |
| `thermistor_ntc_rev_bottom.png` / `thermistor_ntc_bottom.png` | rev·bottom | `rev` | `bottom` | `textures/item/thermistor_ntc_rev_bottom.png` |
| `thermistor_ntc_closed_top.png` / `thermistor_ntc_closed.png` | closed·top | `closed` | `top` | `textures/item/thermistor_ntc_closed_top.png` |
| `thermistor_ntc_closed_side.png` / `thermistor_ntc_side.png` | closed·side | `closed` | `side` | `textures/item/thermistor_ntc_closed_side.png` |
| `thermistor_ntc_closed_bottom.png` / `thermistor_ntc_bottom.png` | closed·bottom | `closed` | `bottom` | `textures/item/thermistor_ntc_closed_bottom.png` |

### `soldering_station` — Паяльный стол

* по-английски: Soldering Station
* kind `STATION`, shape `station`, размер [16, 9, 16], состояний 1, индексы моделей 2118…2123
* палитра процедурной отрисовки: `#2f3b45`, `#7f8c8d`, `#e8b04b`, `#c0392b`
* рецепт: `copper_ingot`×1, `furnace`×1, `iron_nugget`×2, `oak_planks`×4

| положить (теги) | вместо | состояние | грань | файл в паке |
|---|---|---|---|---|
| `soldering_station_off_top.png` / `soldering_station_off.png` / `soldering_station_top.png` / `soldering_station.png` | off·top | `off` | `top` | `textures/item/soldering_station_top.png` |
| `soldering_station_off_side.png` / `soldering_station_side.png` | off·side | `off` | `side` | `textures/item/soldering_station_side.png` |
| `soldering_station_off_bottom.png` / `soldering_station_bottom.png` | off·bottom | `off` | `bottom` | `textures/item/soldering_station_bottom.png` |

(состояний один набор; адреса остальных пяти состояний существуют —
плагин может попросить любой из шести — и ведут на ту же модель)

### `soldering_iron` — Паяльник

* по-английски: Soldering Iron
* kind `TOOL`, shape `tool`, размер [14, 2, 2], состояний 1, индексы моделей 2124…2129
* палитра процедурной отрисовки: `#bdc3c7`, `#e67e22`, `#f5f5f5`
* рецепт: `copper_ingot`×1, `iron_nugget`×1, `stick`×1

| положить (теги) | вместо | состояние | грань | файл в паке |
|---|---|---|---|---|
| `soldering_iron_off_top.png` / `soldering_iron_off.png` / `soldering_iron_top.png` / `soldering_iron.png` | off·top | `off` | `top` | `textures/item/soldering_iron_top.png` |
| `soldering_iron_off_side.png` / `soldering_iron_side.png` | off·side | `off` | `side` | `textures/item/soldering_iron_side.png` |
| `soldering_iron_off_bottom.png` / `soldering_iron_bottom.png` | off·bottom | `off` | `bottom` | `textures/item/soldering_iron_bottom.png` |

(состояний один набор; адреса остальных пяти состояний существуют —
плагин может попросить любой из шести — и ведут на ту же модель)

### `solder` — Припой

* по-английски: Solder
* kind `SUPPLY`, shape `supply`, размер [12, 6, 12], состояний 1, индексы моделей 2130…2135
* палитра процедурной отрисовки: `#95a5a6`, `#d6dbdf`, `#7f8c8d`
* рецепт: `flint`×1, `iron_nugget`×2

| положить (теги) | вместо | состояние | грань | файл в паке |
|---|---|---|---|---|
| `solder_off_top.png` / `solder_off.png` / `solder_top.png` / `solder.png` | off·top | `off` | `top` | `textures/item/solder_top.png` |
| `solder_off_side.png` / `solder_side.png` | off·side | `off` | `side` | `textures/item/solder_side.png` |
| `solder_off_bottom.png` / `solder_bottom.png` | off·bottom | `off` | `bottom` | `textures/item/solder_bottom.png` |

(состояний один набор; адреса остальных пяти состояний существуют —
плагин может попросить любой из шести — и ведут на ту же модель)

### `flux` — Флюс (канифоль)

* по-английски: Rosin Flux
* kind `SUPPLY`, shape `supply`, размер [10, 6, 10], состояний 1, индексы моделей 2136…2141
* палитра процедурной отрисовки: `#e8c39e`, `#b9770e`, `#f7dc6f`
* рецепт: `glowstone_dust`×1, `honey_bottle`×1

| положить (теги) | вместо | состояние | грань | файл в паке |
|---|---|---|---|---|
| `flux_off_top.png` / `flux_off.png` / `flux_top.png` / `flux.png` | off·top | `off` | `top` | `textures/item/flux_top.png` |
| `flux_off_side.png` / `flux_side.png` | off·side | `off` | `side` | `textures/item/flux_side.png` |
| `flux_off_bottom.png` / `flux_bottom.png` | off·bottom | `off` | `bottom` | `textures/item/flux_bottom.png` |

(состояний один набор; адреса остальных пяти состояний существуют —
плагин может попросить любой из шести — и ведут на ту же модель)

### `fuse_cartridge_1a` — Предохранитель плавкий 1 А (5×20)

* по-английски: Cartridge Fuse 1 A (5×20)
* kind `FUSE`, shape `fuse`, размер [13, 5, 5], состояний 6, индексы моделей 2142…2177, *паяется*
* палитра процедурной отрисовки: `#e8f4fb`, `#c9a227`, `#7f8c8d`
* рецепт: `copper_ingot`×2, `glass`×1, `iron_nugget`×4

| положить (теги) | вместо | состояние | грань | файл в паке |
|---|---|---|---|---|
| `fuse_cartridge_1a_off_top.png` / `fuse_cartridge_1a_off.png` / `fuse_cartridge_1a_top.png` / `fuse_cartridge_1a.png` | off·top | `off` | `top` | `textures/item/fuse_cartridge_1a_top.png` |
| `fuse_cartridge_1a_off_side.png` / `fuse_cartridge_1a_side.png` | off·side | `off` | `side` | `textures/item/fuse_cartridge_1a_side.png` |
| `fuse_cartridge_1a_off_bottom.png` / `fuse_cartridge_1a_bottom.png` | off·bottom | `off` | `bottom` | `textures/item/fuse_cartridge_1a_bottom.png` |
| `fuse_cartridge_1a_dim_top.png` / `fuse_cartridge_1a_dim.png` | dim·top | `dim` | `top` | `textures/item/fuse_cartridge_1a_dim_top.png` |
| `fuse_cartridge_1a_dim_side.png` / `fuse_cartridge_1a_side.png` | dim·side | `dim` | `side` | `textures/item/fuse_cartridge_1a_dim_side.png` |
| `fuse_cartridge_1a_dim_bottom.png` / `fuse_cartridge_1a_bottom.png` | dim·bottom | `dim` | `bottom` | `textures/item/fuse_cartridge_1a_dim_bottom.png` |
| `fuse_cartridge_1a_on_top.png` / `fuse_cartridge_1a_on.png` | on·top | `on` | `top` | `textures/item/fuse_cartridge_1a_on_top.png` |
| `fuse_cartridge_1a_on_side.png` / `fuse_cartridge_1a_side.png` | on·side | `on` | `side` | `textures/item/fuse_cartridge_1a_on_side.png` |
| `fuse_cartridge_1a_on_bottom.png` / `fuse_cartridge_1a_bottom.png` | on·bottom | `on` | `bottom` | `textures/item/fuse_cartridge_1a_on_bottom.png` |
| `fuse_cartridge_1a_burnt_top.png` / `fuse_cartridge_1a_burnt.png` | burnt·top | `burnt` | `top` | `textures/item/fuse_cartridge_1a_burnt_top.png` |
| `fuse_cartridge_1a_burnt_side.png` / `fuse_cartridge_1a_side.png` | burnt·side | `burnt` | `side` | `textures/item/fuse_cartridge_1a_burnt_side.png` |
| `fuse_cartridge_1a_burnt_bottom.png` / `fuse_cartridge_1a_bottom.png` | burnt·bottom | `burnt` | `bottom` | `textures/item/fuse_cartridge_1a_burnt_bottom.png` |
| `fuse_cartridge_1a_rev_top.png` / `fuse_cartridge_1a_rev.png` | rev·top | `rev` | `top` | `textures/item/fuse_cartridge_1a_rev_top.png` |
| `fuse_cartridge_1a_rev_side.png` / `fuse_cartridge_1a_side.png` | rev·side | `rev` | `side` | `textures/item/fuse_cartridge_1a_rev_side.png` |
| `fuse_cartridge_1a_rev_bottom.png` / `fuse_cartridge_1a_bottom.png` | rev·bottom | `rev` | `bottom` | `textures/item/fuse_cartridge_1a_rev_bottom.png` |
| `fuse_cartridge_1a_closed_top.png` / `fuse_cartridge_1a_closed.png` | closed·top | `closed` | `top` | `textures/item/fuse_cartridge_1a_closed_top.png` |
| `fuse_cartridge_1a_closed_side.png` / `fuse_cartridge_1a_side.png` | closed·side | `closed` | `side` | `textures/item/fuse_cartridge_1a_closed_side.png` |
| `fuse_cartridge_1a_closed_bottom.png` / `fuse_cartridge_1a_bottom.png` | closed·bottom | `closed` | `bottom` | `textures/item/fuse_cartridge_1a_closed_bottom.png` |

### `fuse_cartridge_5a` — Предохранитель плавкий 5 А (5×20)

* по-английски: Cartridge Fuse 5 A (5×20)
* kind `FUSE`, shape `fuse`, размер [13, 5, 5], состояний 6, индексы моделей 2178…2213, *паяется*
* палитра процедурной отрисовки: `#fbeee6`, `#c9a227`, `#7f8c8d`
* рецепт: `copper_ingot`×3, `iron_nugget`×4

| положить (теги) | вместо | состояние | грань | файл в паке |
|---|---|---|---|---|
| `fuse_cartridge_5a_off_top.png` / `fuse_cartridge_5a_off.png` / `fuse_cartridge_5a_top.png` / `fuse_cartridge_5a.png` | off·top | `off` | `top` | `textures/item/fuse_cartridge_5a_top.png` |
| `fuse_cartridge_5a_off_side.png` / `fuse_cartridge_5a_side.png` | off·side | `off` | `side` | `textures/item/fuse_cartridge_5a_side.png` |
| `fuse_cartridge_5a_off_bottom.png` / `fuse_cartridge_5a_bottom.png` | off·bottom | `off` | `bottom` | `textures/item/fuse_cartridge_5a_bottom.png` |
| `fuse_cartridge_5a_dim_top.png` / `fuse_cartridge_5a_dim.png` | dim·top | `dim` | `top` | `textures/item/fuse_cartridge_5a_dim_top.png` |
| `fuse_cartridge_5a_dim_side.png` / `fuse_cartridge_5a_side.png` | dim·side | `dim` | `side` | `textures/item/fuse_cartridge_5a_dim_side.png` |
| `fuse_cartridge_5a_dim_bottom.png` / `fuse_cartridge_5a_bottom.png` | dim·bottom | `dim` | `bottom` | `textures/item/fuse_cartridge_5a_dim_bottom.png` |
| `fuse_cartridge_5a_on_top.png` / `fuse_cartridge_5a_on.png` | on·top | `on` | `top` | `textures/item/fuse_cartridge_5a_on_top.png` |
| `fuse_cartridge_5a_on_side.png` / `fuse_cartridge_5a_side.png` | on·side | `on` | `side` | `textures/item/fuse_cartridge_5a_on_side.png` |
| `fuse_cartridge_5a_on_bottom.png` / `fuse_cartridge_5a_bottom.png` | on·bottom | `on` | `bottom` | `textures/item/fuse_cartridge_5a_on_bottom.png` |
| `fuse_cartridge_5a_burnt_top.png` / `fuse_cartridge_5a_burnt.png` | burnt·top | `burnt` | `top` | `textures/item/fuse_cartridge_5a_burnt_top.png` |
| `fuse_cartridge_5a_burnt_side.png` / `fuse_cartridge_5a_side.png` | burnt·side | `burnt` | `side` | `textures/item/fuse_cartridge_5a_burnt_side.png` |
| `fuse_cartridge_5a_burnt_bottom.png` / `fuse_cartridge_5a_bottom.png` | burnt·bottom | `burnt` | `bottom` | `textures/item/fuse_cartridge_5a_burnt_bottom.png` |
| `fuse_cartridge_5a_rev_top.png` / `fuse_cartridge_5a_rev.png` | rev·top | `rev` | `top` | `textures/item/fuse_cartridge_5a_rev_top.png` |
| `fuse_cartridge_5a_rev_side.png` / `fuse_cartridge_5a_side.png` | rev·side | `rev` | `side` | `textures/item/fuse_cartridge_5a_rev_side.png` |
| `fuse_cartridge_5a_rev_bottom.png` / `fuse_cartridge_5a_bottom.png` | rev·bottom | `rev` | `bottom` | `textures/item/fuse_cartridge_5a_rev_bottom.png` |
| `fuse_cartridge_5a_closed_top.png` / `fuse_cartridge_5a_closed.png` | closed·top | `closed` | `top` | `textures/item/fuse_cartridge_5a_closed_top.png` |
| `fuse_cartridge_5a_closed_side.png` / `fuse_cartridge_5a_side.png` | closed·side | `closed` | `side` | `textures/item/fuse_cartridge_5a_closed_side.png` |
| `fuse_cartridge_5a_closed_bottom.png` / `fuse_cartridge_5a_bottom.png` | closed·bottom | `closed` | `bottom` | `textures/item/fuse_cartridge_5a_closed_bottom.png` |

### `fuse_slow_2a` — Предохранитель медленный 2 А (пусковые токи)

* по-английски: Slow-blow Fuse 2 A
* kind `FUSE`, shape `fuse`, размер [13, 5, 5], состояний 6, индексы моделей 2214…2249, *паяется*
* палитра процедурной отрисовки: `#5d6d7e`, `#f0b27a`, `#7f8c8d`
* рецепт: `copper_ingot`×2, `iron_nugget`×4, `sand`×1

| положить (теги) | вместо | состояние | грань | файл в паке |
|---|---|---|---|---|
| `fuse_slow_2a_off_top.png` / `fuse_slow_2a_off.png` / `fuse_slow_2a_top.png` / `fuse_slow_2a.png` | off·top | `off` | `top` | `textures/item/fuse_slow_2a_top.png` |
| `fuse_slow_2a_off_side.png` / `fuse_slow_2a_side.png` | off·side | `off` | `side` | `textures/item/fuse_slow_2a_side.png` |
| `fuse_slow_2a_off_bottom.png` / `fuse_slow_2a_bottom.png` | off·bottom | `off` | `bottom` | `textures/item/fuse_slow_2a_bottom.png` |
| `fuse_slow_2a_dim_top.png` / `fuse_slow_2a_dim.png` | dim·top | `dim` | `top` | `textures/item/fuse_slow_2a_dim_top.png` |
| `fuse_slow_2a_dim_side.png` / `fuse_slow_2a_side.png` | dim·side | `dim` | `side` | `textures/item/fuse_slow_2a_dim_side.png` |
| `fuse_slow_2a_dim_bottom.png` / `fuse_slow_2a_bottom.png` | dim·bottom | `dim` | `bottom` | `textures/item/fuse_slow_2a_dim_bottom.png` |
| `fuse_slow_2a_on_top.png` / `fuse_slow_2a_on.png` | on·top | `on` | `top` | `textures/item/fuse_slow_2a_on_top.png` |
| `fuse_slow_2a_on_side.png` / `fuse_slow_2a_side.png` | on·side | `on` | `side` | `textures/item/fuse_slow_2a_on_side.png` |
| `fuse_slow_2a_on_bottom.png` / `fuse_slow_2a_bottom.png` | on·bottom | `on` | `bottom` | `textures/item/fuse_slow_2a_on_bottom.png` |
| `fuse_slow_2a_burnt_top.png` / `fuse_slow_2a_burnt.png` | burnt·top | `burnt` | `top` | `textures/item/fuse_slow_2a_burnt_top.png` |
| `fuse_slow_2a_burnt_side.png` / `fuse_slow_2a_side.png` | burnt·side | `burnt` | `side` | `textures/item/fuse_slow_2a_burnt_side.png` |
| `fuse_slow_2a_burnt_bottom.png` / `fuse_slow_2a_bottom.png` | burnt·bottom | `burnt` | `bottom` | `textures/item/fuse_slow_2a_burnt_bottom.png` |
| `fuse_slow_2a_rev_top.png` / `fuse_slow_2a_rev.png` | rev·top | `rev` | `top` | `textures/item/fuse_slow_2a_rev_top.png` |
| `fuse_slow_2a_rev_side.png` / `fuse_slow_2a_side.png` | rev·side | `rev` | `side` | `textures/item/fuse_slow_2a_rev_side.png` |
| `fuse_slow_2a_rev_bottom.png` / `fuse_slow_2a_bottom.png` | rev·bottom | `rev` | `bottom` | `textures/item/fuse_slow_2a_rev_bottom.png` |
| `fuse_slow_2a_closed_top.png` / `fuse_slow_2a_closed.png` | closed·top | `closed` | `top` | `textures/item/fuse_slow_2a_closed_top.png` |
| `fuse_slow_2a_closed_side.png` / `fuse_slow_2a_side.png` | closed·side | `closed` | `side` | `textures/item/fuse_slow_2a_closed_side.png` |
| `fuse_slow_2a_closed_bottom.png` / `fuse_slow_2a_bottom.png` | closed·bottom | `closed` | `bottom` | `textures/item/fuse_slow_2a_closed_bottom.png` |

### `ptc_500ma` — Самовосстанавливающийся предохранитель 500 мА

* по-английски: Resettable Fuse PTC 500 mA
* kind `PTC`, shape `ptc`, размер [9, 5, 9], состояний 6, индексы моделей 2250…2285, *паяется*
* палитра процедурной отрисовки: `#2e86c1`, `#f8f9f9`, `#aeb6bf`
* рецепт: `clay_ball`×1, `coal`×1, `copper_ingot`×2

| положить (теги) | вместо | состояние | грань | файл в паке |
|---|---|---|---|---|
| `ptc_500ma_off_top.png` / `ptc_500ma_off.png` / `ptc_500ma_top.png` / `ptc_500ma.png` | off·top | `off` | `top` | `textures/item/ptc_500ma_top.png` |
| `ptc_500ma_off_side.png` / `ptc_500ma_side.png` | off·side | `off` | `side` | `textures/item/ptc_500ma_side.png` |
| `ptc_500ma_off_bottom.png` / `ptc_500ma_bottom.png` | off·bottom | `off` | `bottom` | `textures/item/ptc_500ma_bottom.png` |
| `ptc_500ma_dim_top.png` / `ptc_500ma_dim.png` | dim·top | `dim` | `top` | `textures/item/ptc_500ma_dim_top.png` |
| `ptc_500ma_dim_side.png` / `ptc_500ma_side.png` | dim·side | `dim` | `side` | `textures/item/ptc_500ma_dim_side.png` |
| `ptc_500ma_dim_bottom.png` / `ptc_500ma_bottom.png` | dim·bottom | `dim` | `bottom` | `textures/item/ptc_500ma_dim_bottom.png` |
| `ptc_500ma_on_top.png` / `ptc_500ma_on.png` | on·top | `on` | `top` | `textures/item/ptc_500ma_on_top.png` |
| `ptc_500ma_on_side.png` / `ptc_500ma_side.png` | on·side | `on` | `side` | `textures/item/ptc_500ma_on_side.png` |
| `ptc_500ma_on_bottom.png` / `ptc_500ma_bottom.png` | on·bottom | `on` | `bottom` | `textures/item/ptc_500ma_on_bottom.png` |
| `ptc_500ma_burnt_top.png` / `ptc_500ma_burnt.png` | burnt·top | `burnt` | `top` | `textures/item/ptc_500ma_burnt_top.png` |
| `ptc_500ma_burnt_side.png` / `ptc_500ma_side.png` | burnt·side | `burnt` | `side` | `textures/item/ptc_500ma_burnt_side.png` |
| `ptc_500ma_burnt_bottom.png` / `ptc_500ma_bottom.png` | burnt·bottom | `burnt` | `bottom` | `textures/item/ptc_500ma_burnt_bottom.png` |
| `ptc_500ma_rev_top.png` / `ptc_500ma_rev.png` | rev·top | `rev` | `top` | `textures/item/ptc_500ma_rev_top.png` |
| `ptc_500ma_rev_side.png` / `ptc_500ma_side.png` | rev·side | `rev` | `side` | `textures/item/ptc_500ma_rev_side.png` |
| `ptc_500ma_rev_bottom.png` / `ptc_500ma_bottom.png` | rev·bottom | `rev` | `bottom` | `textures/item/ptc_500ma_rev_bottom.png` |
| `ptc_500ma_closed_top.png` / `ptc_500ma_closed.png` | closed·top | `closed` | `top` | `textures/item/ptc_500ma_closed_top.png` |
| `ptc_500ma_closed_side.png` / `ptc_500ma_side.png` | closed·side | `closed` | `side` | `textures/item/ptc_500ma_closed_side.png` |
| `ptc_500ma_closed_bottom.png` / `ptc_500ma_bottom.png` | closed·bottom | `closed` | `bottom` | `textures/item/ptc_500ma_closed_bottom.png` |

### `mov_275v` — Варистор 275 В (защита от импульсных выбросов)

* по-английски: Varistor MOV 275 V
* kind `VARISTOR`, shape `varistor`, размер [11, 8, 11], состояний 6, индексы моделей 2286…2321, *паяется*
* палитра процедурной отрисовки: `#2874a6`, `#d6dbdf`, `#154360`
* рецепт: `copper_ingot`×4, `redstone`×1

| положить (теги) | вместо | состояние | грань | файл в паке |
|---|---|---|---|---|
| `mov_275v_off_top.png` / `mov_275v_off.png` / `mov_275v_top.png` / `mov_275v.png` | off·top | `off` | `top` | `textures/item/mov_275v_top.png` |
| `mov_275v_off_side.png` / `mov_275v_side.png` | off·side | `off` | `side` | `textures/item/mov_275v_side.png` |
| `mov_275v_off_bottom.png` / `mov_275v_bottom.png` | off·bottom | `off` | `bottom` | `textures/item/mov_275v_bottom.png` |
| `mov_275v_dim_top.png` / `mov_275v_dim.png` | dim·top | `dim` | `top` | `textures/item/mov_275v_dim_top.png` |
| `mov_275v_dim_side.png` / `mov_275v_side.png` | dim·side | `dim` | `side` | `textures/item/mov_275v_dim_side.png` |
| `mov_275v_dim_bottom.png` / `mov_275v_bottom.png` | dim·bottom | `dim` | `bottom` | `textures/item/mov_275v_dim_bottom.png` |
| `mov_275v_on_top.png` / `mov_275v_on.png` | on·top | `on` | `top` | `textures/item/mov_275v_on_top.png` |
| `mov_275v_on_side.png` / `mov_275v_side.png` | on·side | `on` | `side` | `textures/item/mov_275v_on_side.png` |
| `mov_275v_on_bottom.png` / `mov_275v_bottom.png` | on·bottom | `on` | `bottom` | `textures/item/mov_275v_on_bottom.png` |
| `mov_275v_burnt_top.png` / `mov_275v_burnt.png` | burnt·top | `burnt` | `top` | `textures/item/mov_275v_burnt_top.png` |
| `mov_275v_burnt_side.png` / `mov_275v_side.png` | burnt·side | `burnt` | `side` | `textures/item/mov_275v_burnt_side.png` |
| `mov_275v_burnt_bottom.png` / `mov_275v_bottom.png` | burnt·bottom | `burnt` | `bottom` | `textures/item/mov_275v_burnt_bottom.png` |
| `mov_275v_rev_top.png` / `mov_275v_rev.png` | rev·top | `rev` | `top` | `textures/item/mov_275v_rev_top.png` |
| `mov_275v_rev_side.png` / `mov_275v_side.png` | rev·side | `rev` | `side` | `textures/item/mov_275v_rev_side.png` |
| `mov_275v_rev_bottom.png` / `mov_275v_bottom.png` | rev·bottom | `rev` | `bottom` | `textures/item/mov_275v_rev_bottom.png` |
| `mov_275v_closed_top.png` / `mov_275v_closed.png` | closed·top | `closed` | `top` | `textures/item/mov_275v_closed_top.png` |
| `mov_275v_closed_side.png` / `mov_275v_side.png` | closed·side | `closed` | `side` | `textures/item/mov_275v_closed_side.png` |
| `mov_275v_closed_bottom.png` / `mov_275v_bottom.png` | closed·bottom | `closed` | `bottom` | `textures/item/mov_275v_closed_bottom.png` |

### `bridge_kbu8` — Диодный мост KBU8 (800 В / 4 А)

* по-английски: Bridge Rectifier KBU8
* kind `BRIDGE`, shape `bridge`, размер [12, 7, 12], состояний 6, индексы моделей 2322…2357, *паяется*
* палитра процедурной отрисовки: `#1b1b1f`, `#b9c0c8`, `#5d6d7e`
* рецепт: `gold_nugget`×4, `redstone`×4

| положить (теги) | вместо | состояние | грань | файл в паке |
|---|---|---|---|---|
| `bridge_kbu8_off_top.png` / `bridge_kbu8_off.png` / `bridge_kbu8_top.png` / `bridge_kbu8.png` | off·top | `off` | `top` | `textures/item/bridge_kbu8_top.png` |
| `bridge_kbu8_off_side.png` / `bridge_kbu8_side.png` | off·side | `off` | `side` | `textures/item/bridge_kbu8_side.png` |
| `bridge_kbu8_off_bottom.png` / `bridge_kbu8_bottom.png` | off·bottom | `off` | `bottom` | `textures/item/bridge_kbu8_bottom.png` |
| `bridge_kbu8_dim_top.png` / `bridge_kbu8_dim.png` | dim·top | `dim` | `top` | `textures/item/bridge_kbu8_dim_top.png` |
| `bridge_kbu8_dim_side.png` / `bridge_kbu8_side.png` | dim·side | `dim` | `side` | `textures/item/bridge_kbu8_dim_side.png` |
| `bridge_kbu8_dim_bottom.png` / `bridge_kbu8_bottom.png` | dim·bottom | `dim` | `bottom` | `textures/item/bridge_kbu8_dim_bottom.png` |
| `bridge_kbu8_on_top.png` / `bridge_kbu8_on.png` | on·top | `on` | `top` | `textures/item/bridge_kbu8_on_top.png` |
| `bridge_kbu8_on_side.png` / `bridge_kbu8_side.png` | on·side | `on` | `side` | `textures/item/bridge_kbu8_on_side.png` |
| `bridge_kbu8_on_bottom.png` / `bridge_kbu8_bottom.png` | on·bottom | `on` | `bottom` | `textures/item/bridge_kbu8_on_bottom.png` |
| `bridge_kbu8_burnt_top.png` / `bridge_kbu8_burnt.png` | burnt·top | `burnt` | `top` | `textures/item/bridge_kbu8_burnt_top.png` |
| `bridge_kbu8_burnt_side.png` / `bridge_kbu8_side.png` | burnt·side | `burnt` | `side` | `textures/item/bridge_kbu8_burnt_side.png` |
| `bridge_kbu8_burnt_bottom.png` / `bridge_kbu8_bottom.png` | burnt·bottom | `burnt` | `bottom` | `textures/item/bridge_kbu8_burnt_bottom.png` |
| `bridge_kbu8_rev_top.png` / `bridge_kbu8_rev.png` | rev·top | `rev` | `top` | `textures/item/bridge_kbu8_rev_top.png` |
| `bridge_kbu8_rev_side.png` / `bridge_kbu8_side.png` | rev·side | `rev` | `side` | `textures/item/bridge_kbu8_rev_side.png` |
| `bridge_kbu8_rev_bottom.png` / `bridge_kbu8_bottom.png` | rev·bottom | `rev` | `bottom` | `textures/item/bridge_kbu8_rev_bottom.png` |
| `bridge_kbu8_closed_top.png` / `bridge_kbu8_closed.png` | closed·top | `closed` | `top` | `textures/item/bridge_kbu8_closed_top.png` |
| `bridge_kbu8_closed_side.png` / `bridge_kbu8_side.png` | closed·side | `closed` | `side` | `textures/item/bridge_kbu8_closed_side.png` |
| `bridge_kbu8_closed_bottom.png` / `bridge_kbu8_bottom.png` | closed·bottom | `closed` | `bottom` | `textures/item/bridge_kbu8_closed_bottom.png` |

### `shottky_sbr30` — Диод Шоттки 3 А / 30 В

* по-английски: Schottky Diode 3 A / 30 V
* kind `DIODE`, shape `resistor`, размер [14, 5, 5], состояний 6, индексы моделей 2358…2393, *паяется*
* палитра процедурной отрисовки: `#17202a`, `#f7dc6f`, `#566573`
* рецепт: `copper_ingot`×1, `quartz`×1, `redstone`×1

| положить (теги) | вместо | состояние | грань | файл в паке |
|---|---|---|---|---|
| `shottky_sbr30_off_top.png` / `shottky_sbr30_off.png` / `shottky_sbr30_top.png` / `shottky_sbr30.png` | off·top | `off` | `top` | `textures/item/shottky_sbr30_top.png` |
| `shottky_sbr30_off_side.png` / `shottky_sbr30_side.png` | off·side | `off` | `side` | `textures/item/shottky_sbr30_side.png` |
| `shottky_sbr30_off_bottom.png` / `shottky_sbr30_bottom.png` | off·bottom | `off` | `bottom` | `textures/item/shottky_sbr30_bottom.png` |
| `shottky_sbr30_dim_top.png` / `shottky_sbr30_dim.png` | dim·top | `dim` | `top` | `textures/item/shottky_sbr30_dim_top.png` |
| `shottky_sbr30_dim_side.png` / `shottky_sbr30_side.png` | dim·side | `dim` | `side` | `textures/item/shottky_sbr30_dim_side.png` |
| `shottky_sbr30_dim_bottom.png` / `shottky_sbr30_bottom.png` | dim·bottom | `dim` | `bottom` | `textures/item/shottky_sbr30_dim_bottom.png` |
| `shottky_sbr30_on_top.png` / `shottky_sbr30_on.png` | on·top | `on` | `top` | `textures/item/shottky_sbr30_on_top.png` |
| `shottky_sbr30_on_side.png` / `shottky_sbr30_side.png` | on·side | `on` | `side` | `textures/item/shottky_sbr30_on_side.png` |
| `shottky_sbr30_on_bottom.png` / `shottky_sbr30_bottom.png` | on·bottom | `on` | `bottom` | `textures/item/shottky_sbr30_on_bottom.png` |
| `shottky_sbr30_burnt_top.png` / `shottky_sbr30_burnt.png` | burnt·top | `burnt` | `top` | `textures/item/shottky_sbr30_burnt_top.png` |
| `shottky_sbr30_burnt_side.png` / `shottky_sbr30_side.png` | burnt·side | `burnt` | `side` | `textures/item/shottky_sbr30_burnt_side.png` |
| `shottky_sbr30_burnt_bottom.png` / `shottky_sbr30_bottom.png` | burnt·bottom | `burnt` | `bottom` | `textures/item/shottky_sbr30_burnt_bottom.png` |
| `shottky_sbr30_rev_top.png` / `shottky_sbr30_rev.png` | rev·top | `rev` | `top` | `textures/item/shottky_sbr30_rev_top.png` |
| `shottky_sbr30_rev_side.png` / `shottky_sbr30_side.png` | rev·side | `rev` | `side` | `textures/item/shottky_sbr30_rev_side.png` |
| `shottky_sbr30_rev_bottom.png` / `shottky_sbr30_bottom.png` | rev·bottom | `rev` | `bottom` | `textures/item/shottky_sbr30_rev_bottom.png` |
| `shottky_sbr30_closed_top.png` / `shottky_sbr30_closed.png` | closed·top | `closed` | `top` | `textures/item/shottky_sbr30_closed_top.png` |
| `shottky_sbr30_closed_side.png` / `shottky_sbr30_side.png` | closed·side | `closed` | `side` | `textures/item/shottky_sbr30_closed_side.png` |
| `shottky_sbr30_closed_bottom.png` / `shottky_sbr30_bottom.png` | closed·bottom | `closed` | `bottom` | `textures/item/shottky_sbr30_closed_bottom.png` |

### `cap_elec_2200uf` — Электролит 2200 мкФ / 25 В

* по-английски: Electrolytic 2200 µF / 25 V
* kind `CAPACITOR`, shape `electrolytic`, размер [11, 14, 11], состояний 6, индексы моделей 2394…2429, *паяется*
* палитра процедурной отрисовки: `#0b3d91`, `#aed6f1`
* рецепт: `copper_ingot`×4, `paper`×4

| положить (теги) | вместо | состояние | грань | файл в паке |
|---|---|---|---|---|
| `cap_elec_2200uf_off_top.png` / `cap_elec_2200uf_off.png` / `cap_elec_2200uf_top.png` / `cap_elec_2200uf.png` | off·top | `off` | `top` | `textures/item/cap_elec_2200uf_top.png` |
| `cap_elec_2200uf_off_side.png` / `cap_elec_2200uf_side.png` | off·side | `off` | `side` | `textures/item/cap_elec_2200uf_side.png` |
| `cap_elec_2200uf_off_bottom.png` / `cap_elec_2200uf_bottom.png` | off·bottom | `off` | `bottom` | `textures/item/cap_elec_2200uf_bottom.png` |
| `cap_elec_2200uf_dim_top.png` / `cap_elec_2200uf_dim.png` | dim·top | `dim` | `top` | `textures/item/cap_elec_2200uf_dim_top.png` |
| `cap_elec_2200uf_dim_side.png` / `cap_elec_2200uf_side.png` | dim·side | `dim` | `side` | `textures/item/cap_elec_2200uf_dim_side.png` |
| `cap_elec_2200uf_dim_bottom.png` / `cap_elec_2200uf_bottom.png` | dim·bottom | `dim` | `bottom` | `textures/item/cap_elec_2200uf_dim_bottom.png` |
| `cap_elec_2200uf_on_top.png` / `cap_elec_2200uf_on.png` | on·top | `on` | `top` | `textures/item/cap_elec_2200uf_on_top.png` |
| `cap_elec_2200uf_on_side.png` / `cap_elec_2200uf_side.png` | on·side | `on` | `side` | `textures/item/cap_elec_2200uf_on_side.png` |
| `cap_elec_2200uf_on_bottom.png` / `cap_elec_2200uf_bottom.png` | on·bottom | `on` | `bottom` | `textures/item/cap_elec_2200uf_on_bottom.png` |
| `cap_elec_2200uf_burnt_top.png` / `cap_elec_2200uf_burnt.png` | burnt·top | `burnt` | `top` | `textures/item/cap_elec_2200uf_burnt_top.png` |
| `cap_elec_2200uf_burnt_side.png` / `cap_elec_2200uf_side.png` | burnt·side | `burnt` | `side` | `textures/item/cap_elec_2200uf_burnt_side.png` |
| `cap_elec_2200uf_burnt_bottom.png` / `cap_elec_2200uf_bottom.png` | burnt·bottom | `burnt` | `bottom` | `textures/item/cap_elec_2200uf_burnt_bottom.png` |
| `cap_elec_2200uf_rev_top.png` / `cap_elec_2200uf_rev.png` | rev·top | `rev` | `top` | `textures/item/cap_elec_2200uf_rev_top.png` |
| `cap_elec_2200uf_rev_side.png` / `cap_elec_2200uf_side.png` | rev·side | `rev` | `side` | `textures/item/cap_elec_2200uf_rev_side.png` |
| `cap_elec_2200uf_rev_bottom.png` / `cap_elec_2200uf_bottom.png` | rev·bottom | `rev` | `bottom` | `textures/item/cap_elec_2200uf_rev_bottom.png` |
| `cap_elec_2200uf_closed_top.png` / `cap_elec_2200uf_closed.png` | closed·top | `closed` | `top` | `textures/item/cap_elec_2200uf_closed_top.png` |
| `cap_elec_2200uf_closed_side.png` / `cap_elec_2200uf_side.png` | closed·side | `closed` | `side` | `textures/item/cap_elec_2200uf_closed_side.png` |
| `cap_elec_2200uf_closed_bottom.png` / `cap_elec_2200uf_bottom.png` | closed·bottom | `closed` | `bottom` | `textures/item/cap_elec_2200uf_closed_bottom.png` |

### `reg_7805` — Стабилизатор 7805 (5 В / 1 А)

* по-английски: Regulator 7805 (5 V / 1 A)
* kind `REGULATOR`, shape `to220`, размер [11, 4, 7], состояний 6, индексы моделей 2430…2465, *паяется*
* палитра процедурной отрисовки: `#17202a`, `#d5d8dc`, `#7f8c8d`
* рецепт: `copper_ingot`×2, `gold_nugget`×2, `iron_nugget`×3, `sand`×1

| положить (теги) | вместо | состояние | грань | файл в паке |
|---|---|---|---|---|
| `reg_7805_off_top.png` / `reg_7805_off.png` / `reg_7805_top.png` / `reg_7805.png` | off·top | `off` | `top` | `textures/item/reg_7805_top.png` |
| `reg_7805_off_side.png` / `reg_7805_side.png` | off·side | `off` | `side` | `textures/item/reg_7805_side.png` |
| `reg_7805_off_bottom.png` / `reg_7805_bottom.png` | off·bottom | `off` | `bottom` | `textures/item/reg_7805_bottom.png` |
| `reg_7805_dim_top.png` / `reg_7805_dim.png` | dim·top | `dim` | `top` | `textures/item/reg_7805_dim_top.png` |
| `reg_7805_dim_side.png` / `reg_7805_side.png` | dim·side | `dim` | `side` | `textures/item/reg_7805_dim_side.png` |
| `reg_7805_dim_bottom.png` / `reg_7805_bottom.png` | dim·bottom | `dim` | `bottom` | `textures/item/reg_7805_dim_bottom.png` |
| `reg_7805_on_top.png` / `reg_7805_on.png` | on·top | `on` | `top` | `textures/item/reg_7805_on_top.png` |
| `reg_7805_on_side.png` / `reg_7805_side.png` | on·side | `on` | `side` | `textures/item/reg_7805_on_side.png` |
| `reg_7805_on_bottom.png` / `reg_7805_bottom.png` | on·bottom | `on` | `bottom` | `textures/item/reg_7805_on_bottom.png` |
| `reg_7805_burnt_top.png` / `reg_7805_burnt.png` | burnt·top | `burnt` | `top` | `textures/item/reg_7805_burnt_top.png` |
| `reg_7805_burnt_side.png` / `reg_7805_side.png` | burnt·side | `burnt` | `side` | `textures/item/reg_7805_burnt_side.png` |
| `reg_7805_burnt_bottom.png` / `reg_7805_bottom.png` | burnt·bottom | `burnt` | `bottom` | `textures/item/reg_7805_burnt_bottom.png` |
| `reg_7805_rev_top.png` / `reg_7805_rev.png` | rev·top | `rev` | `top` | `textures/item/reg_7805_rev_top.png` |
| `reg_7805_rev_side.png` / `reg_7805_side.png` | rev·side | `rev` | `side` | `textures/item/reg_7805_rev_side.png` |
| `reg_7805_rev_bottom.png` / `reg_7805_bottom.png` | rev·bottom | `rev` | `bottom` | `textures/item/reg_7805_rev_bottom.png` |
| `reg_7805_closed_top.png` / `reg_7805_closed.png` | closed·top | `closed` | `top` | `textures/item/reg_7805_closed_top.png` |
| `reg_7805_closed_side.png` / `reg_7805_side.png` | closed·side | `closed` | `side` | `textures/item/reg_7805_closed_side.png` |
| `reg_7805_closed_bottom.png` / `reg_7805_bottom.png` | closed·bottom | `closed` | `bottom` | `textures/item/reg_7805_closed_bottom.png` |

### `reg_7812` — Стабилизатор 7812 (12 В / 1 А)

* по-английски: Regulator 7812 (12 V / 1 A)
* kind `REGULATOR`, shape `to220`, размер [11, 4, 7], состояний 6, индексы моделей 2466…2501, *паяется*
* палитра процедурной отрисовки: `#1c2833`, `#aeb6bf`, `#7f8c8d`
* рецепт: `copper_ingot`×2, `gold_nugget`×2, `iron_nugget`×3, `redstone`×1

| положить (теги) | вместо | состояние | грань | файл в паке |
|---|---|---|---|---|
| `reg_7812_off_top.png` / `reg_7812_off.png` / `reg_7812_top.png` / `reg_7812.png` | off·top | `off` | `top` | `textures/item/reg_7812_top.png` |
| `reg_7812_off_side.png` / `reg_7812_side.png` | off·side | `off` | `side` | `textures/item/reg_7812_side.png` |
| `reg_7812_off_bottom.png` / `reg_7812_bottom.png` | off·bottom | `off` | `bottom` | `textures/item/reg_7812_bottom.png` |
| `reg_7812_dim_top.png` / `reg_7812_dim.png` | dim·top | `dim` | `top` | `textures/item/reg_7812_dim_top.png` |
| `reg_7812_dim_side.png` / `reg_7812_side.png` | dim·side | `dim` | `side` | `textures/item/reg_7812_dim_side.png` |
| `reg_7812_dim_bottom.png` / `reg_7812_bottom.png` | dim·bottom | `dim` | `bottom` | `textures/item/reg_7812_dim_bottom.png` |
| `reg_7812_on_top.png` / `reg_7812_on.png` | on·top | `on` | `top` | `textures/item/reg_7812_on_top.png` |
| `reg_7812_on_side.png` / `reg_7812_side.png` | on·side | `on` | `side` | `textures/item/reg_7812_on_side.png` |
| `reg_7812_on_bottom.png` / `reg_7812_bottom.png` | on·bottom | `on` | `bottom` | `textures/item/reg_7812_on_bottom.png` |
| `reg_7812_burnt_top.png` / `reg_7812_burnt.png` | burnt·top | `burnt` | `top` | `textures/item/reg_7812_burnt_top.png` |
| `reg_7812_burnt_side.png` / `reg_7812_side.png` | burnt·side | `burnt` | `side` | `textures/item/reg_7812_burnt_side.png` |
| `reg_7812_burnt_bottom.png` / `reg_7812_bottom.png` | burnt·bottom | `burnt` | `bottom` | `textures/item/reg_7812_burnt_bottom.png` |
| `reg_7812_rev_top.png` / `reg_7812_rev.png` | rev·top | `rev` | `top` | `textures/item/reg_7812_rev_top.png` |
| `reg_7812_rev_side.png` / `reg_7812_side.png` | rev·side | `rev` | `side` | `textures/item/reg_7812_rev_side.png` |
| `reg_7812_rev_bottom.png` / `reg_7812_bottom.png` | rev·bottom | `rev` | `bottom` | `textures/item/reg_7812_rev_bottom.png` |
| `reg_7812_closed_top.png` / `reg_7812_closed.png` | closed·top | `closed` | `top` | `textures/item/reg_7812_closed_top.png` |
| `reg_7812_closed_side.png` / `reg_7812_side.png` | closed·side | `closed` | `side` | `textures/item/reg_7812_closed_side.png` |
| `reg_7812_closed_bottom.png` / `reg_7812_bottom.png` | closed·bottom | `closed` | `bottom` | `textures/item/reg_7812_closed_bottom.png` |

### `reg_lm317` — Стабилизатор LM317 (1.25–24 В, подстроечный)

* по-английски: Regulator LM317 (adj)
* kind `REGULATOR`, shape `to220`, размер [11, 4, 7], состояний 6, индексы моделей 2502…2537, *паяется*
* палитра процедурной отрисовки: `#212f3d`, `#f0f3f4`, `#7f8c8d`
* рецепт: `copper_ingot`×2, `gold_nugget`×2, `iron_nugget`×2, `redstone`×1, `sand`×1

| положить (теги) | вместо | состояние | грань | файл в паке |
|---|---|---|---|---|
| `reg_lm317_off_top.png` / `reg_lm317_off.png` / `reg_lm317_top.png` / `reg_lm317.png` | off·top | `off` | `top` | `textures/item/reg_lm317_top.png` |
| `reg_lm317_off_side.png` / `reg_lm317_side.png` | off·side | `off` | `side` | `textures/item/reg_lm317_side.png` |
| `reg_lm317_off_bottom.png` / `reg_lm317_bottom.png` | off·bottom | `off` | `bottom` | `textures/item/reg_lm317_bottom.png` |
| `reg_lm317_dim_top.png` / `reg_lm317_dim.png` | dim·top | `dim` | `top` | `textures/item/reg_lm317_dim_top.png` |
| `reg_lm317_dim_side.png` / `reg_lm317_side.png` | dim·side | `dim` | `side` | `textures/item/reg_lm317_dim_side.png` |
| `reg_lm317_dim_bottom.png` / `reg_lm317_bottom.png` | dim·bottom | `dim` | `bottom` | `textures/item/reg_lm317_dim_bottom.png` |
| `reg_lm317_on_top.png` / `reg_lm317_on.png` | on·top | `on` | `top` | `textures/item/reg_lm317_on_top.png` |
| `reg_lm317_on_side.png` / `reg_lm317_side.png` | on·side | `on` | `side` | `textures/item/reg_lm317_on_side.png` |
| `reg_lm317_on_bottom.png` / `reg_lm317_bottom.png` | on·bottom | `on` | `bottom` | `textures/item/reg_lm317_on_bottom.png` |
| `reg_lm317_burnt_top.png` / `reg_lm317_burnt.png` | burnt·top | `burnt` | `top` | `textures/item/reg_lm317_burnt_top.png` |
| `reg_lm317_burnt_side.png` / `reg_lm317_side.png` | burnt·side | `burnt` | `side` | `textures/item/reg_lm317_burnt_side.png` |
| `reg_lm317_burnt_bottom.png` / `reg_lm317_bottom.png` | burnt·bottom | `burnt` | `bottom` | `textures/item/reg_lm317_burnt_bottom.png` |
| `reg_lm317_rev_top.png` / `reg_lm317_rev.png` | rev·top | `rev` | `top` | `textures/item/reg_lm317_rev_top.png` |
| `reg_lm317_rev_side.png` / `reg_lm317_side.png` | rev·side | `rev` | `side` | `textures/item/reg_lm317_rev_side.png` |
| `reg_lm317_rev_bottom.png` / `reg_lm317_bottom.png` | rev·bottom | `rev` | `bottom` | `textures/item/reg_lm317_rev_bottom.png` |
| `reg_lm317_closed_top.png` / `reg_lm317_closed.png` | closed·top | `closed` | `top` | `textures/item/reg_lm317_closed_top.png` |
| `reg_lm317_closed_side.png` / `reg_lm317_side.png` | closed·side | `closed` | `side` | `textures/item/reg_lm317_closed_side.png` |
| `reg_lm317_closed_bottom.png` / `reg_lm317_bottom.png` | closed·bottom | `closed` | `bottom` | `textures/item/reg_lm317_closed_bottom.png` |

### `xfmr_230_12` — Трансформатор 230→12 В / 10 ВА

* по-английски: Transformer 230→12 V / 10 VA
* kind `TRANSFORMER`, shape `transformer`, размер [14, 11, 14], состояний 6, индексы моделей 2538…2573, *паяется*
* палитра процедурной отрисовки: `#4d5656`, `#b9770e`, `#1b2a38`
* рецепт: `copper_ingot`×4, `iron_ingot`×4

| положить (теги) | вместо | состояние | грань | файл в паке |
|---|---|---|---|---|
| `xfmr_230_12_off_top.png` / `xfmr_230_12_off.png` / `xfmr_230_12_top.png` / `xfmr_230_12.png` | off·top | `off` | `top` | `textures/item/xfmr_230_12_top.png` |
| `xfmr_230_12_off_side.png` / `xfmr_230_12_side.png` | off·side | `off` | `side` | `textures/item/xfmr_230_12_side.png` |
| `xfmr_230_12_off_bottom.png` / `xfmr_230_12_bottom.png` | off·bottom | `off` | `bottom` | `textures/item/xfmr_230_12_bottom.png` |
| `xfmr_230_12_dim_top.png` / `xfmr_230_12_dim.png` | dim·top | `dim` | `top` | `textures/item/xfmr_230_12_dim_top.png` |
| `xfmr_230_12_dim_side.png` / `xfmr_230_12_side.png` | dim·side | `dim` | `side` | `textures/item/xfmr_230_12_dim_side.png` |
| `xfmr_230_12_dim_bottom.png` / `xfmr_230_12_bottom.png` | dim·bottom | `dim` | `bottom` | `textures/item/xfmr_230_12_dim_bottom.png` |
| `xfmr_230_12_on_top.png` / `xfmr_230_12_on.png` | on·top | `on` | `top` | `textures/item/xfmr_230_12_on_top.png` |
| `xfmr_230_12_on_side.png` / `xfmr_230_12_side.png` | on·side | `on` | `side` | `textures/item/xfmr_230_12_on_side.png` |
| `xfmr_230_12_on_bottom.png` / `xfmr_230_12_bottom.png` | on·bottom | `on` | `bottom` | `textures/item/xfmr_230_12_on_bottom.png` |
| `xfmr_230_12_burnt_top.png` / `xfmr_230_12_burnt.png` | burnt·top | `burnt` | `top` | `textures/item/xfmr_230_12_burnt_top.png` |
| `xfmr_230_12_burnt_side.png` / `xfmr_230_12_side.png` | burnt·side | `burnt` | `side` | `textures/item/xfmr_230_12_burnt_side.png` |
| `xfmr_230_12_burnt_bottom.png` / `xfmr_230_12_bottom.png` | burnt·bottom | `burnt` | `bottom` | `textures/item/xfmr_230_12_burnt_bottom.png` |
| `xfmr_230_12_rev_top.png` / `xfmr_230_12_rev.png` | rev·top | `rev` | `top` | `textures/item/xfmr_230_12_rev_top.png` |
| `xfmr_230_12_rev_side.png` / `xfmr_230_12_side.png` | rev·side | `rev` | `side` | `textures/item/xfmr_230_12_rev_side.png` |
| `xfmr_230_12_rev_bottom.png` / `xfmr_230_12_bottom.png` | rev·bottom | `rev` | `bottom` | `textures/item/xfmr_230_12_rev_bottom.png` |
| `xfmr_230_12_closed_top.png` / `xfmr_230_12_closed.png` | closed·top | `closed` | `top` | `textures/item/xfmr_230_12_closed_top.png` |
| `xfmr_230_12_closed_side.png` / `xfmr_230_12_side.png` | closed·side | `closed` | `side` | `textures/item/xfmr_230_12_closed_side.png` |
| `xfmr_230_12_closed_bottom.png` / `xfmr_230_12_bottom.png` | closed·bottom | `closed` | `bottom` | `textures/item/xfmr_230_12_closed_bottom.png` |

### `xfmr_230_9` — Трансформатор 230→9 В / 6 ВА

* по-английски: Transformer 230→9 V / 6 VA
* kind `TRANSFORMER`, shape `transformer`, размер [13, 10, 13], состояний 6, индексы моделей 2574…2609, *паяется*
* палитра процедурной отрисовки: `#566573`, `#d68910`, `#1b263b`
* рецепт: `copper_ingot`×4, `iron_ingot`×3

| положить (теги) | вместо | состояние | грань | файл в паке |
|---|---|---|---|---|
| `xfmr_230_9_off_top.png` / `xfmr_230_9_off.png` / `xfmr_230_9_top.png` / `xfmr_230_9.png` | off·top | `off` | `top` | `textures/item/xfmr_230_9_top.png` |
| `xfmr_230_9_off_side.png` / `xfmr_230_9_side.png` | off·side | `off` | `side` | `textures/item/xfmr_230_9_side.png` |
| `xfmr_230_9_off_bottom.png` / `xfmr_230_9_bottom.png` | off·bottom | `off` | `bottom` | `textures/item/xfmr_230_9_bottom.png` |
| `xfmr_230_9_dim_top.png` / `xfmr_230_9_dim.png` | dim·top | `dim` | `top` | `textures/item/xfmr_230_9_dim_top.png` |
| `xfmr_230_9_dim_side.png` / `xfmr_230_9_side.png` | dim·side | `dim` | `side` | `textures/item/xfmr_230_9_dim_side.png` |
| `xfmr_230_9_dim_bottom.png` / `xfmr_230_9_bottom.png` | dim·bottom | `dim` | `bottom` | `textures/item/xfmr_230_9_dim_bottom.png` |
| `xfmr_230_9_on_top.png` / `xfmr_230_9_on.png` | on·top | `on` | `top` | `textures/item/xfmr_230_9_on_top.png` |
| `xfmr_230_9_on_side.png` / `xfmr_230_9_side.png` | on·side | `on` | `side` | `textures/item/xfmr_230_9_on_side.png` |
| `xfmr_230_9_on_bottom.png` / `xfmr_230_9_bottom.png` | on·bottom | `on` | `bottom` | `textures/item/xfmr_230_9_on_bottom.png` |
| `xfmr_230_9_burnt_top.png` / `xfmr_230_9_burnt.png` | burnt·top | `burnt` | `top` | `textures/item/xfmr_230_9_burnt_top.png` |
| `xfmr_230_9_burnt_side.png` / `xfmr_230_9_side.png` | burnt·side | `burnt` | `side` | `textures/item/xfmr_230_9_burnt_side.png` |
| `xfmr_230_9_burnt_bottom.png` / `xfmr_230_9_bottom.png` | burnt·bottom | `burnt` | `bottom` | `textures/item/xfmr_230_9_burnt_bottom.png` |
| `xfmr_230_9_rev_top.png` / `xfmr_230_9_rev.png` | rev·top | `rev` | `top` | `textures/item/xfmr_230_9_rev_top.png` |
| `xfmr_230_9_rev_side.png` / `xfmr_230_9_side.png` | rev·side | `rev` | `side` | `textures/item/xfmr_230_9_rev_side.png` |
| `xfmr_230_9_rev_bottom.png` / `xfmr_230_9_bottom.png` | rev·bottom | `rev` | `bottom` | `textures/item/xfmr_230_9_rev_bottom.png` |
| `xfmr_230_9_closed_top.png` / `xfmr_230_9_closed.png` | closed·top | `closed` | `top` | `textures/item/xfmr_230_9_closed_top.png` |
| `xfmr_230_9_closed_side.png` / `xfmr_230_9_side.png` | closed·side | `closed` | `side` | `textures/item/xfmr_230_9_closed_side.png` |
| `xfmr_230_9_closed_bottom.png` / `xfmr_230_9_bottom.png` | closed·bottom | `closed` | `bottom` | `textures/item/xfmr_230_9_closed_bottom.png` |

### `desolder_braid` — Оплётка для выпайки

* по-английски: Desoldering Braid
* kind `SUPPLY`, shape `supply`, размер [11, 6, 11], состояний 1, индексы моделей 2610…2615
* палитра процедурной отрисовки: `#b3b6b7`, `#ecf0f1`, `#7f8c8d`
* рецепт: `copper_ingot`×1, `string`×2

| положить (теги) | вместо | состояние | грань | файл в паке |
|---|---|---|---|---|
| `desolder_braid_off_top.png` / `desolder_braid_off.png` / `desolder_braid_top.png` / `desolder_braid.png` | off·top | `off` | `top` | `textures/item/desolder_braid_top.png` |
| `desolder_braid_off_side.png` / `desolder_braid_side.png` | off·side | `off` | `side` | `textures/item/desolder_braid_side.png` |
| `desolder_braid_off_bottom.png` / `desolder_braid_bottom.png` | off·bottom | `off` | `bottom` | `textures/item/desolder_braid_bottom.png` |

(состояний один набор; адреса остальных пяти состояний существуют —
плагин может попросить любой из шести — и ведут на ту же модель)

### `source_ac` — Синусоидальный источник 0–24 В / 1 Гц

* по-английски: AC Source 0-24 V / 1 Hz
* kind `SOURCE`, shape `psu`, размер [15, 7, 12], состояний 6, индексы моделей 2616…2651
* палитра процедурной отрисовки: `#d6eaf8`, `#e67e22`, `#212f3d`
* рецепт: `copper_ingot`×1, `gold_nugget`×4, `iron_ingot`×1, `redstone`×2

| положить (теги) | вместо | состояние | грань | файл в паке |
|---|---|---|---|---|
| `source_ac_off_top.png` / `source_ac_off.png` / `source_ac_top.png` / `source_ac.png` | off·top | `off` | `top` | `textures/item/source_ac_top.png` |
| `source_ac_off_side.png` / `source_ac_side.png` | off·side | `off` | `side` | `textures/item/source_ac_side.png` |
| `source_ac_off_bottom.png` / `source_ac_bottom.png` | off·bottom | `off` | `bottom` | `textures/item/source_ac_bottom.png` |
| `source_ac_dim_top.png` / `source_ac_dim.png` | dim·top | `dim` | `top` | `textures/item/source_ac_dim_top.png` |
| `source_ac_dim_side.png` / `source_ac_side.png` | dim·side | `dim` | `side` | `textures/item/source_ac_dim_side.png` |
| `source_ac_dim_bottom.png` / `source_ac_bottom.png` | dim·bottom | `dim` | `bottom` | `textures/item/source_ac_dim_bottom.png` |
| `source_ac_on_top.png` / `source_ac_on.png` | on·top | `on` | `top` | `textures/item/source_ac_on_top.png` |
| `source_ac_on_side.png` / `source_ac_side.png` | on·side | `on` | `side` | `textures/item/source_ac_on_side.png` |
| `source_ac_on_bottom.png` / `source_ac_bottom.png` | on·bottom | `on` | `bottom` | `textures/item/source_ac_on_bottom.png` |
| `source_ac_burnt_top.png` / `source_ac_burnt.png` | burnt·top | `burnt` | `top` | `textures/item/source_ac_burnt_top.png` |
| `source_ac_burnt_side.png` / `source_ac_side.png` | burnt·side | `burnt` | `side` | `textures/item/source_ac_burnt_side.png` |
| `source_ac_burnt_bottom.png` / `source_ac_bottom.png` | burnt·bottom | `burnt` | `bottom` | `textures/item/source_ac_burnt_bottom.png` |
| `source_ac_rev_top.png` / `source_ac_rev.png` | rev·top | `rev` | `top` | `textures/item/source_ac_rev_top.png` |
| `source_ac_rev_side.png` / `source_ac_side.png` | rev·side | `rev` | `side` | `textures/item/source_ac_rev_side.png` |
| `source_ac_rev_bottom.png` / `source_ac_bottom.png` | rev·bottom | `rev` | `bottom` | `textures/item/source_ac_rev_bottom.png` |
| `source_ac_closed_top.png` / `source_ac_closed.png` | closed·top | `closed` | `top` | `textures/item/source_ac_closed_top.png` |
| `source_ac_closed_side.png` / `source_ac_side.png` | closed·side | `closed` | `side` | `textures/item/source_ac_closed_side.png` |
| `source_ac_closed_bottom.png` / `source_ac_bottom.png` | closed·bottom | `closed` | `bottom` | `textures/item/source_ac_closed_bottom.png` |

### `raw_copper_chunk` — Кусок медной руды

* по-английски: Raw Copper Chunk
* kind `SUPPLY`, shape `supply`, размер [9, 7, 9], состояний 1, индексы моделей 2652…2657
* палитра процедурной отрисовки: `#b87333`, `#8c5a2b`, `#5d4037`
* рецепт: `calcite`×1, `copper_ingot`×2

| положить (теги) | вместо | состояние | грань | файл в паке |
|---|---|---|---|---|
| `raw_copper_chunk_off_top.png` / `raw_copper_chunk_off.png` / `raw_copper_chunk_top.png` / `raw_copper_chunk.png` | off·top | `off` | `top` | `textures/item/raw_copper_chunk_top.png` |
| `raw_copper_chunk_off_side.png` / `raw_copper_chunk_side.png` | off·side | `off` | `side` | `textures/item/raw_copper_chunk_side.png` |
| `raw_copper_chunk_off_bottom.png` / `raw_copper_chunk_bottom.png` | off·bottom | `off` | `bottom` | `textures/item/raw_copper_chunk_bottom.png` |

(состояний один набор; адреса остальных пяти состояний существуют —
плагин может попросить любой из шести — и ведут на ту же модель)

### `raw_tin_chunk` — Кусок оловянной руды

* по-английски: Raw Tin Chunk
* kind `SUPPLY`, shape `supply`, размер [9, 7, 9], состояний 1, индексы моделей 2658…2663
* палитра процедурной отрисовки: `#d5dbdb`, `#aeb6bf`, `#808b96`
* рецепт: `calcite`×1, `iron_nugget`×4, `quartz`×1

| положить (теги) | вместо | состояние | грань | файл в паке |
|---|---|---|---|---|
| `raw_tin_chunk_off_top.png` / `raw_tin_chunk_off.png` / `raw_tin_chunk_top.png` / `raw_tin_chunk.png` | off·top | `off` | `top` | `textures/item/raw_tin_chunk_top.png` |
| `raw_tin_chunk_off_side.png` / `raw_tin_chunk_side.png` | off·side | `off` | `side` | `textures/item/raw_tin_chunk_side.png` |
| `raw_tin_chunk_off_bottom.png` / `raw_tin_chunk_bottom.png` | off·bottom | `off` | `bottom` | `textures/item/raw_tin_chunk_bottom.png` |

(состояний один набор; адреса остальных пяти состояний существуют —
плагин может попросить любой из шести — и ведут на ту же модель)

### `raw_zinc_chunk` — Кусок цинковой руды

* по-английски: Raw Zinc Chunk
* kind `SUPPLY`, shape `supply`, размер [9, 7, 9], состояний 1, индексы моделей 2664…2669
* палитра процедурной отрисовки: `#c8cfd6`, `#95a5a6`, `#616a6b`
* рецепт: `calcite`×1, `coal`×1, `gunpowder`×1, `iron_nugget`×2

| положить (теги) | вместо | состояние | грань | файл в паке |
|---|---|---|---|---|
| `raw_zinc_chunk_off_top.png` / `raw_zinc_chunk_off.png` / `raw_zinc_chunk_top.png` / `raw_zinc_chunk.png` | off·top | `off` | `top` | `textures/item/raw_zinc_chunk_top.png` |
| `raw_zinc_chunk_off_side.png` / `raw_zinc_chunk_side.png` | off·side | `off` | `side` | `textures/item/raw_zinc_chunk_side.png` |
| `raw_zinc_chunk_off_bottom.png` / `raw_zinc_chunk_bottom.png` | off·bottom | `off` | `bottom` | `textures/item/raw_zinc_chunk_bottom.png` |

(состояний один набор; адреса остальных пяти состояний существуют —
плагин может попросить любой из шести — и ведут на ту же модель)

### `ingot_copper` — Слиток меди

* по-английски: Copper Ingot
* kind `SUPPLY`, shape `supply`, размер [12, 5, 8], состояний 1, индексы моделей 2670…2675
* палитра процедурной отрисовки: `#e67e22`, `#b87333`, `#7e5109`
* рецепта в `RECIPES` нет (предмет выдаётся командой или станком)

| положить (теги) | вместо | состояние | грань | файл в паке |
|---|---|---|---|---|
| `ingot_copper_off_top.png` / `ingot_copper_off.png` / `ingot_copper_top.png` / `ingot_copper.png` | off·top | `off` | `top` | `textures/item/ingot_copper_top.png` |
| `ingot_copper_off_side.png` / `ingot_copper_side.png` | off·side | `off` | `side` | `textures/item/ingot_copper_side.png` |
| `ingot_copper_off_bottom.png` / `ingot_copper_bottom.png` | off·bottom | `off` | `bottom` | `textures/item/ingot_copper_bottom.png` |

(состояний один набор; адреса остальных пяти состояний существуют —
плагин может попросить любой из шести — и ведут на ту же модель)

### `ingot_tin` — Слиток олова

* по-английски: Tin Ingot
* kind `SUPPLY`, shape `supply`, размер [12, 5, 8], состояний 1, индексы моделей 2676…2681
* палитра процедурной отрисовки: `#ecf0f1`, `#bdc3c7`, `#7f8c8d`
* рецепта в `RECIPES` нет (предмет выдаётся командой или станком)

| положить (теги) | вместо | состояние | грань | файл в паке |
|---|---|---|---|---|
| `ingot_tin_off_top.png` / `ingot_tin_off.png` / `ingot_tin_top.png` / `ingot_tin.png` | off·top | `off` | `top` | `textures/item/ingot_tin_top.png` |
| `ingot_tin_off_side.png` / `ingot_tin_side.png` | off·side | `off` | `side` | `textures/item/ingot_tin_side.png` |
| `ingot_tin_off_bottom.png` / `ingot_tin_bottom.png` | off·bottom | `off` | `bottom` | `textures/item/ingot_tin_bottom.png` |

(состояний один набор; адреса остальных пяти состояний существуют —
плагин может попросить любой из шести — и ведут на ту же модель)

### `ingot_zinc` — Слиток цинка

* по-английски: Zinc Ingot
* kind `SUPPLY`, shape `supply`, размер [12, 5, 8], состояний 1, индексы моделей 2682…2687
* палитра процедурной отрисовки: `#d6dbdf`, `#a5b1c2`, `#6c7a89`
* рецепта в `RECIPES` нет (предмет выдаётся командой или станком)

| положить (теги) | вместо | состояние | грань | файл в паке |
|---|---|---|---|---|
| `ingot_zinc_off_top.png` / `ingot_zinc_off.png` / `ingot_zinc_top.png` / `ingot_zinc.png` | off·top | `off` | `top` | `textures/item/ingot_zinc_top.png` |
| `ingot_zinc_off_side.png` / `ingot_zinc_side.png` | off·side | `off` | `side` | `textures/item/ingot_zinc_side.png` |
| `ingot_zinc_off_bottom.png` / `ingot_zinc_bottom.png` | off·bottom | `off` | `bottom` | `textures/item/ingot_zinc_bottom.png` |

(состояний один набор; адреса остальных пяти состояний существуют —
плагин может попросить любой из шести — и ведут на ту же модель)

### `wire_bare` — Провод голый (монтажный)

* по-английски: Bare Hookup Wire
* kind `WIRE`, shape `wire`, размер [16, 3, 4], состояний 6, индексы моделей 2688…2723, *паяется*
* палитра процедурной отрисовки: `#e67e22`, `#b87333`
* рецепт: `string`×2

| положить (теги) | вместо | состояние | грань | файл в паке |
|---|---|---|---|---|
| `wire_bare_off_top.png` / `wire_bare_off.png` / `wire_bare_top.png` / `wire_bare.png` | off·top | `off` | `top` | `textures/item/wire_bare_top.png` |
| `wire_bare_off_side.png` / `wire_bare_side.png` | off·side | `off` | `side` | `textures/item/wire_bare_side.png` |
| `wire_bare_off_bottom.png` / `wire_bare_bottom.png` | off·bottom | `off` | `bottom` | `textures/item/wire_bare_bottom.png` |
| `wire_bare_dim_top.png` / `wire_bare_dim.png` | dim·top | `dim` | `top` | `textures/item/wire_bare_dim_top.png` |
| `wire_bare_dim_side.png` / `wire_bare_side.png` | dim·side | `dim` | `side` | `textures/item/wire_bare_dim_side.png` |
| `wire_bare_dim_bottom.png` / `wire_bare_bottom.png` | dim·bottom | `dim` | `bottom` | `textures/item/wire_bare_dim_bottom.png` |
| `wire_bare_on_top.png` / `wire_bare_on.png` | on·top | `on` | `top` | `textures/item/wire_bare_on_top.png` |
| `wire_bare_on_side.png` / `wire_bare_side.png` | on·side | `on` | `side` | `textures/item/wire_bare_on_side.png` |
| `wire_bare_on_bottom.png` / `wire_bare_bottom.png` | on·bottom | `on` | `bottom` | `textures/item/wire_bare_on_bottom.png` |
| `wire_bare_burnt_top.png` / `wire_bare_burnt.png` | burnt·top | `burnt` | `top` | `textures/item/wire_bare_burnt_top.png` |
| `wire_bare_burnt_side.png` / `wire_bare_side.png` | burnt·side | `burnt` | `side` | `textures/item/wire_bare_burnt_side.png` |
| `wire_bare_burnt_bottom.png` / `wire_bare_bottom.png` | burnt·bottom | `burnt` | `bottom` | `textures/item/wire_bare_burnt_bottom.png` |
| `wire_bare_rev_top.png` / `wire_bare_rev.png` | rev·top | `rev` | `top` | `textures/item/wire_bare_rev_top.png` |
| `wire_bare_rev_side.png` / `wire_bare_side.png` | rev·side | `rev` | `side` | `textures/item/wire_bare_rev_side.png` |
| `wire_bare_rev_bottom.png` / `wire_bare_bottom.png` | rev·bottom | `rev` | `bottom` | `textures/item/wire_bare_rev_bottom.png` |
| `wire_bare_closed_top.png` / `wire_bare_closed.png` | closed·top | `closed` | `top` | `textures/item/wire_bare_closed_top.png` |
| `wire_bare_closed_side.png` / `wire_bare_side.png` | closed·side | `closed` | `side` | `textures/item/wire_bare_closed_side.png` |
| `wire_bare_closed_bottom.png` / `wire_bare_bottom.png` | closed·bottom | `closed` | `bottom` | `textures/item/wire_bare_closed_bottom.png` |

### `wire_tinned` — Провод лужёный

* по-английски: Tinned Wire
* kind `WIRE`, shape `wire`, размер [16, 3, 4], состояний 6, индексы моделей 2724…2759, *паяется*
* палитра процедурной отрисовки: `#d7dbdd`, `#aeb6bf`, `#e67e22`
* рецепт: `sand`×1

| положить (теги) | вместо | состояние | грань | файл в паке |
|---|---|---|---|---|
| `wire_tinned_off_top.png` / `wire_tinned_off.png` / `wire_tinned_top.png` / `wire_tinned.png` | off·top | `off` | `top` | `textures/item/wire_tinned_top.png` |
| `wire_tinned_off_side.png` / `wire_tinned_side.png` | off·side | `off` | `side` | `textures/item/wire_tinned_side.png` |
| `wire_tinned_off_bottom.png` / `wire_tinned_bottom.png` | off·bottom | `off` | `bottom` | `textures/item/wire_tinned_bottom.png` |
| `wire_tinned_dim_top.png` / `wire_tinned_dim.png` | dim·top | `dim` | `top` | `textures/item/wire_tinned_dim_top.png` |
| `wire_tinned_dim_side.png` / `wire_tinned_side.png` | dim·side | `dim` | `side` | `textures/item/wire_tinned_dim_side.png` |
| `wire_tinned_dim_bottom.png` / `wire_tinned_bottom.png` | dim·bottom | `dim` | `bottom` | `textures/item/wire_tinned_dim_bottom.png` |
| `wire_tinned_on_top.png` / `wire_tinned_on.png` | on·top | `on` | `top` | `textures/item/wire_tinned_on_top.png` |
| `wire_tinned_on_side.png` / `wire_tinned_side.png` | on·side | `on` | `side` | `textures/item/wire_tinned_on_side.png` |
| `wire_tinned_on_bottom.png` / `wire_tinned_bottom.png` | on·bottom | `on` | `bottom` | `textures/item/wire_tinned_on_bottom.png` |
| `wire_tinned_burnt_top.png` / `wire_tinned_burnt.png` | burnt·top | `burnt` | `top` | `textures/item/wire_tinned_burnt_top.png` |
| `wire_tinned_burnt_side.png` / `wire_tinned_side.png` | burnt·side | `burnt` | `side` | `textures/item/wire_tinned_burnt_side.png` |
| `wire_tinned_burnt_bottom.png` / `wire_tinned_bottom.png` | burnt·bottom | `burnt` | `bottom` | `textures/item/wire_tinned_burnt_bottom.png` |
| `wire_tinned_rev_top.png` / `wire_tinned_rev.png` | rev·top | `rev` | `top` | `textures/item/wire_tinned_rev_top.png` |
| `wire_tinned_rev_side.png` / `wire_tinned_side.png` | rev·side | `rev` | `side` | `textures/item/wire_tinned_rev_side.png` |
| `wire_tinned_rev_bottom.png` / `wire_tinned_bottom.png` | rev·bottom | `rev` | `bottom` | `textures/item/wire_tinned_rev_bottom.png` |
| `wire_tinned_closed_top.png` / `wire_tinned_closed.png` | closed·top | `closed` | `top` | `textures/item/wire_tinned_closed_top.png` |
| `wire_tinned_closed_side.png` / `wire_tinned_side.png` | closed·side | `closed` | `side` | `textures/item/wire_tinned_closed_side.png` |
| `wire_tinned_closed_bottom.png` / `wire_tinned_bottom.png` | closed·bottom | `closed` | `bottom` | `textures/item/wire_tinned_closed_bottom.png` |

### `geoprobe` — Геозонд (разведка недр)

* по-английски: Geo Probe
* kind `TOOL`, shape `tool`, размер [14, 2, 2], состояний 1, индексы моделей 2760…2765
* палитра процедурной отрисовки: `#1b2631`, `#d6dbdf`, `#e67e22`
* рецепт: `iron_ingot`×1, `quartz`×1, `stick`×1

| положить (теги) | вместо | состояние | грань | файл в паке |
|---|---|---|---|---|
| `geoprobe_off_top.png` / `geoprobe_off.png` / `geoprobe_top.png` / `geoprobe.png` | off·top | `off` | `top` | `textures/item/geoprobe_top.png` |
| `geoprobe_off_side.png` / `geoprobe_side.png` | off·side | `off` | `side` | `textures/item/geoprobe_side.png` |
| `geoprobe_off_bottom.png` / `geoprobe_bottom.png` | off·bottom | `off` | `bottom` | `textures/item/geoprobe_bottom.png` |

(состояний один набор; адреса остальных пяти состояний существуют —
плагин может попросить любой из шести — и ведут на ту же модель)

### `wire_stripper` — Зачистка/лужение проводов

* по-английски: Wire Stripper
* kind `TOOL`, shape `tool`, размер [13, 4, 3], состояний 1, индексы моделей 2766…2771
* палитра процедурной отрисовки: `#c0392b`, `#bdc3c7`, `#f5f5f5`
* рецепт: `copper_ingot`×1, `flint`×1, `iron_ingot`×1, `shears`×1

| положить (теги) | вместо | состояние | грань | файл в паке |
|---|---|---|---|---|
| `wire_stripper_off_top.png` / `wire_stripper_off.png` / `wire_stripper_top.png` / `wire_stripper.png` | off·top | `off` | `top` | `textures/item/wire_stripper_top.png` |
| `wire_stripper_off_side.png` / `wire_stripper_side.png` | off·side | `off` | `side` | `textures/item/wire_stripper_side.png` |
| `wire_stripper_off_bottom.png` / `wire_stripper_bottom.png` | off·bottom | `off` | `bottom` | `textures/item/wire_stripper_bottom.png` |

(состояний один набор; адреса остальных пяти состояний существуют —
плагин может попросить любой из шести — и ведут на ту же модель)

### `battery_zinc` — Воздушно-цинковый элемент 1.4 В

* по-английски: Zinc-Air Cell 1.4 V
* kind `SOURCE`, shape `battery`, размер [12, 4, 12], состояний 6, индексы моделей 2772…2807, *паяется*
* палитра процедурной отрисовки: `#ecf0f1`, `#95a5a6`, `#e67e22`
* рецепт: `coal`×1, `string`×1

| положить (теги) | вместо | состояние | грань | файл в паке |
|---|---|---|---|---|
| `battery_zinc_off_top.png` / `battery_zinc_off.png` / `battery_zinc_top.png` / `battery_zinc.png` | off·top | `off` | `top` | `textures/item/battery_zinc_top.png` |
| `battery_zinc_off_side.png` / `battery_zinc_side.png` | off·side | `off` | `side` | `textures/item/battery_zinc_side.png` |
| `battery_zinc_off_bottom.png` / `battery_zinc_bottom.png` | off·bottom | `off` | `bottom` | `textures/item/battery_zinc_bottom.png` |
| `battery_zinc_dim_top.png` / `battery_zinc_dim.png` | dim·top | `dim` | `top` | `textures/item/battery_zinc_dim_top.png` |
| `battery_zinc_dim_side.png` / `battery_zinc_side.png` | dim·side | `dim` | `side` | `textures/item/battery_zinc_dim_side.png` |
| `battery_zinc_dim_bottom.png` / `battery_zinc_bottom.png` | dim·bottom | `dim` | `bottom` | `textures/item/battery_zinc_dim_bottom.png` |
| `battery_zinc_on_top.png` / `battery_zinc_on.png` | on·top | `on` | `top` | `textures/item/battery_zinc_on_top.png` |
| `battery_zinc_on_side.png` / `battery_zinc_side.png` | on·side | `on` | `side` | `textures/item/battery_zinc_on_side.png` |
| `battery_zinc_on_bottom.png` / `battery_zinc_bottom.png` | on·bottom | `on` | `bottom` | `textures/item/battery_zinc_on_bottom.png` |
| `battery_zinc_burnt_top.png` / `battery_zinc_burnt.png` | burnt·top | `burnt` | `top` | `textures/item/battery_zinc_burnt_top.png` |
| `battery_zinc_burnt_side.png` / `battery_zinc_side.png` | burnt·side | `burnt` | `side` | `textures/item/battery_zinc_burnt_side.png` |
| `battery_zinc_burnt_bottom.png` / `battery_zinc_bottom.png` | burnt·bottom | `burnt` | `bottom` | `textures/item/battery_zinc_burnt_bottom.png` |
| `battery_zinc_rev_top.png` / `battery_zinc_rev.png` | rev·top | `rev` | `top` | `textures/item/battery_zinc_rev_top.png` |
| `battery_zinc_rev_side.png` / `battery_zinc_side.png` | rev·side | `rev` | `side` | `textures/item/battery_zinc_rev_side.png` |
| `battery_zinc_rev_bottom.png` / `battery_zinc_bottom.png` | rev·bottom | `rev` | `bottom` | `textures/item/battery_zinc_rev_bottom.png` |
| `battery_zinc_closed_top.png` / `battery_zinc_closed.png` | closed·top | `closed` | `top` | `textures/item/battery_zinc_closed_top.png` |
| `battery_zinc_closed_side.png` / `battery_zinc_side.png` | closed·side | `closed` | `side` | `textures/item/battery_zinc_closed_side.png` |
| `battery_zinc_closed_bottom.png` / `battery_zinc_bottom.png` | closed·bottom | `closed` | `bottom` | `textures/item/battery_zinc_closed_bottom.png` |

### `mcu_atmega328` — Микроконтроллер ATMega328 (C-like прошивка)

* по-английски: MCU ATMega328 (C-like firmware)
* kind `MCU`, shape `chip`, размер [14, 5, 8], состояний 6, индексы моделей 2808…2843, *паяется*
* палитра процедурной отрисовки: `#212f3d`, `#f0f3f4`, `#7f8c8d`
* рецепт: `gold_nugget`×1, `quartz`×1

| положить (теги) | вместо | состояние | грань | файл в паке |
|---|---|---|---|---|
| `mcu_atmega328_off_top.png` / `mcu_atmega328_off.png` / `mcu_atmega328_top.png` / `mcu_atmega328.png` | off·top | `off` | `top` | `textures/item/mcu_atmega328_top.png` |
| `mcu_atmega328_off_side.png` / `mcu_atmega328_side.png` | off·side | `off` | `side` | `textures/item/mcu_atmega328_side.png` |
| `mcu_atmega328_off_bottom.png` / `mcu_atmega328_bottom.png` | off·bottom | `off` | `bottom` | `textures/item/mcu_atmega328_bottom.png` |
| `mcu_atmega328_dim_top.png` / `mcu_atmega328_dim.png` | dim·top | `dim` | `top` | `textures/item/mcu_atmega328_dim_top.png` |
| `mcu_atmega328_dim_side.png` / `mcu_atmega328_side.png` | dim·side | `dim` | `side` | `textures/item/mcu_atmega328_dim_side.png` |
| `mcu_atmega328_dim_bottom.png` / `mcu_atmega328_bottom.png` | dim·bottom | `dim` | `bottom` | `textures/item/mcu_atmega328_dim_bottom.png` |
| `mcu_atmega328_on_top.png` / `mcu_atmega328_on.png` | on·top | `on` | `top` | `textures/item/mcu_atmega328_on_top.png` |
| `mcu_atmega328_on_side.png` / `mcu_atmega328_side.png` | on·side | `on` | `side` | `textures/item/mcu_atmega328_on_side.png` |
| `mcu_atmega328_on_bottom.png` / `mcu_atmega328_bottom.png` | on·bottom | `on` | `bottom` | `textures/item/mcu_atmega328_on_bottom.png` |
| `mcu_atmega328_burnt_top.png` / `mcu_atmega328_burnt.png` | burnt·top | `burnt` | `top` | `textures/item/mcu_atmega328_burnt_top.png` |
| `mcu_atmega328_burnt_side.png` / `mcu_atmega328_side.png` | burnt·side | `burnt` | `side` | `textures/item/mcu_atmega328_burnt_side.png` |
| `mcu_atmega328_burnt_bottom.png` / `mcu_atmega328_bottom.png` | burnt·bottom | `burnt` | `bottom` | `textures/item/mcu_atmega328_burnt_bottom.png` |
| `mcu_atmega328_rev_top.png` / `mcu_atmega328_rev.png` | rev·top | `rev` | `top` | `textures/item/mcu_atmega328_rev_top.png` |
| `mcu_atmega328_rev_side.png` / `mcu_atmega328_side.png` | rev·side | `rev` | `side` | `textures/item/mcu_atmega328_rev_side.png` |
| `mcu_atmega328_rev_bottom.png` / `mcu_atmega328_bottom.png` | rev·bottom | `rev` | `bottom` | `textures/item/mcu_atmega328_rev_bottom.png` |
| `mcu_atmega328_closed_top.png` / `mcu_atmega328_closed.png` | closed·top | `closed` | `top` | `textures/item/mcu_atmega328_closed_top.png` |
| `mcu_atmega328_closed_side.png` / `mcu_atmega328_side.png` | closed·side | `closed` | `side` | `textures/item/mcu_atmega328_closed_side.png` |
| `mcu_atmega328_closed_bottom.png` / `mcu_atmega328_bottom.png` | closed·bottom | `closed` | `bottom` | `textures/item/mcu_atmega328_closed_bottom.png` |

### `programmer_usb` — USB-программатор

* по-английски: USB Programmer
* kind `STATION`, shape `station`, размер [13, 7, 13], состояний 1, индексы моделей 2844…2849
* палитра процедурной отрисовки: `#2c3e50`, `#3498db`, `#e67e22`, `#ecf0f1`
* рецепт: `copper_ingot`×1, `gold_nugget`×1, `iron_nugget`×1, `repeater`×1

| положить (теги) | вместо | состояние | грань | файл в паке |
|---|---|---|---|---|
| `programmer_usb_off_top.png` / `programmer_usb_off.png` / `programmer_usb_top.png` / `programmer_usb.png` | off·top | `off` | `top` | `textures/item/programmer_usb_top.png` |
| `programmer_usb_off_side.png` / `programmer_usb_side.png` | off·side | `off` | `side` | `textures/item/programmer_usb_side.png` |
| `programmer_usb_off_bottom.png` / `programmer_usb_bottom.png` | off·bottom | `off` | `bottom` | `textures/item/programmer_usb_bottom.png` |

(состояний один набор; адреса остальных пяти состояний существуют —
плагин может попросить любой из шести — и ведут на ту же модель)

### `crystal_16mhz` — Кварцевый резонатор 16 МГц

* по-английски: Crystal 16 MHz
* kind `SUPPLY`, shape `supply`, размер [12, 5, 6], состояний 1, индексы моделей 2850…2855
* палитра процедурной отрисовки: `#d6dbdf`, `#f5b041`, `#7f8c8d`
* рецепт: `gold_nugget`×1, `quartz`×2

| положить (теги) | вместо | состояние | грань | файл в паке |
|---|---|---|---|---|
| `crystal_16mhz_off_top.png` / `crystal_16mhz_off.png` / `crystal_16mhz_top.png` / `crystal_16mhz.png` | off·top | `off` | `top` | `textures/item/crystal_16mhz_top.png` |
| `crystal_16mhz_off_side.png` / `crystal_16mhz_side.png` | off·side | `off` | `side` | `textures/item/crystal_16mhz_side.png` |
| `crystal_16mhz_off_bottom.png` / `crystal_16mhz_bottom.png` | off·bottom | `off` | `bottom` | `textures/item/crystal_16mhz_bottom.png` |

(состояний один набор; адреса остальных пяти состояний существуют —
плагин может попросить любой из шести — и ведут на ту же модель)

### `crucible_station` — Тигельная печь (сплавы и обмен руд)

* по-английски: Crucible Furnace (alloys & ore exchange)
* kind `STATION`, shape `station`, размер [14, 9, 14], состояний 1, индексы моделей 2856…2861
* палитра процедурной отрисовки: `#3d2b1f`, `#e67e22`, `#b9770e`, `#ecf0f1`
* рецепт: `bricks`×4, `furnace`×1, `iron_ingot`×2, `lava_bucket`×1

| положить (теги) | вместо | состояние | грань | файл в паке |
|---|---|---|---|---|
| `crucible_station_off_top.png` / `crucible_station_off.png` / `crucible_station_top.png` / `crucible_station.png` | off·top | `off` | `top` | `textures/item/crucible_station_top.png` |
| `crucible_station_off_side.png` / `crucible_station_side.png` | off·side | `off` | `side` | `textures/item/crucible_station_side.png` |
| `crucible_station_off_bottom.png` / `crucible_station_bottom.png` | off·bottom | `off` | `bottom` | `textures/item/crucible_station_bottom.png` |

(состояний один набор; адреса остальных пяти состояний существуют —
плагин может попросить любой из шести — и ведут на ту же модель)

### `mill_station` — Прокатно-волочильный стан

* по-английски: Rolling & Wire-Drawing Mill
* kind `STATION`, shape `station`, размер [14, 8, 14], состояний 1, индексы моделей 2862…2867
* палитра процедурной отрисовки: `#5d6d7e`, `#bdc3c7`, `#f5b041`, `#7f8c8d`
* рецепт: `copper_ingot`×2, `iron_ingot`×4, `piston`×1, `redstone_block`×2

| положить (теги) | вместо | состояние | грань | файл в паке |
|---|---|---|---|---|
| `mill_station_off_top.png` / `mill_station_off.png` / `mill_station_top.png` / `mill_station.png` | off·top | `off` | `top` | `textures/item/mill_station_top.png` |
| `mill_station_off_side.png` / `mill_station_side.png` | off·side | `off` | `side` | `textures/item/mill_station_side.png` |
| `mill_station_off_bottom.png` / `mill_station_bottom.png` | off·bottom | `off` | `bottom` | `textures/item/mill_station_bottom.png` |

(состояний один набор; адреса остальных пяти состояний существуют —
плагин может попросить любой из шести — и ведут на ту же модель)

### `ingot_bronze` — Слиток бронзы

* по-английски: Bronze Ingot
* kind `SUPPLY`, shape `supply`, размер [12, 5, 8], состояний 1, индексы моделей 2868…2873
* палитра процедурной отрисовки: `#cd7f32`, `#a0522d`, `#7e5109`
* рецепта в `RECIPES` нет (предмет выдаётся командой или станком)

| положить (теги) | вместо | состояние | грань | файл в паке |
|---|---|---|---|---|
| `ingot_bronze_off_top.png` / `ingot_bronze_off.png` / `ingot_bronze_top.png` / `ingot_bronze.png` | off·top | `off` | `top` | `textures/item/ingot_bronze_top.png` |
| `ingot_bronze_off_side.png` / `ingot_bronze_side.png` | off·side | `off` | `side` | `textures/item/ingot_bronze_side.png` |
| `ingot_bronze_off_bottom.png` / `ingot_bronze_bottom.png` | off·bottom | `off` | `bottom` | `textures/item/ingot_bronze_bottom.png` |

(состояний один набор; адреса остальных пяти состояний существуют —
плагин может попросить любой из шести — и ведут на ту же модель)

### `ingot_brass` — Слиток латуни

* по-английски: Brass Ingot
* kind `SUPPLY`, shape `supply`, размер [12, 5, 8], состояний 1, индексы моделей 2874…2879
* палитра процедурной отрисовки: `#d4ac0d`, `#b7950b`, `#7d6608`
* рецепта в `RECIPES` нет (предмет выдаётся командой или станком)

| положить (теги) | вместо | состояние | грань | файл в паке |
|---|---|---|---|---|
| `ingot_brass_off_top.png` / `ingot_brass_off.png` / `ingot_brass_top.png` / `ingot_brass.png` | off·top | `off` | `top` | `textures/item/ingot_brass_top.png` |
| `ingot_brass_off_side.png` / `ingot_brass_side.png` | off·side | `off` | `side` | `textures/item/ingot_brass_side.png` |
| `ingot_brass_off_bottom.png` / `ingot_brass_bottom.png` | off·bottom | `off` | `bottom` | `textures/item/ingot_brass_bottom.png` |

(состояний один набор; адреса остальных пяти состояний существуют —
плагин может попросить любой из шести — и ведут на ту же модель)

### `slag` — Шлак (отвал плавки)

* по-английски: Slag (smelting dump)
* kind `SUPPLY`, shape `supply`, размер [10, 6, 10], состояний 1, индексы моделей 2880…2885
* палитра процедурной отрисовки: `#616a6b`, `#808b96`, `#424949`
* рецепта в `RECIPES` нет (предмет выдаётся командой или станком)

| положить (теги) | вместо | состояние | грань | файл в паке |
|---|---|---|---|---|
| `slag_off_top.png` / `slag_off.png` / `slag_top.png` / `slag.png` | off·top | `off` | `top` | `textures/item/slag_top.png` |
| `slag_off_side.png` / `slag_side.png` | off·side | `off` | `side` | `textures/item/slag_side.png` |
| `slag_off_bottom.png` / `slag_bottom.png` | off·bottom | `off` | `bottom` | `textures/item/slag_bottom.png` |

(состояний один набор; адреса остальных пяти состояний существуют —
плагин может попросить любой из шести — и ведут на ту же модель)

### `wire_enamelled` — Эмальпровод ПЭВ-0.2 (в лаке)

* по-английски: Enamelled Wire PEV-0.2
* kind `WIRE`, shape `wire`, размер [16, 3, 4], состояний 6, индексы моделей 2886…2921
* палитра процедурной отрисовки: `#b9770e`, `#e67e22`
* рецепта в `RECIPES` нет (предмет выдаётся командой или станком)

| положить (теги) | вместо | состояние | грань | файл в паке |
|---|---|---|---|---|
| `wire_enamelled_off_top.png` / `wire_enamelled_off.png` / `wire_enamelled_top.png` / `wire_enamelled.png` | off·top | `off` | `top` | `textures/item/wire_enamelled_top.png` |
| `wire_enamelled_off_side.png` / `wire_enamelled_side.png` | off·side | `off` | `side` | `textures/item/wire_enamelled_side.png` |
| `wire_enamelled_off_bottom.png` / `wire_enamelled_bottom.png` | off·bottom | `off` | `bottom` | `textures/item/wire_enamelled_bottom.png` |
| `wire_enamelled_dim_top.png` / `wire_enamelled_dim.png` | dim·top | `dim` | `top` | `textures/item/wire_enamelled_dim_top.png` |
| `wire_enamelled_dim_side.png` / `wire_enamelled_side.png` | dim·side | `dim` | `side` | `textures/item/wire_enamelled_dim_side.png` |
| `wire_enamelled_dim_bottom.png` / `wire_enamelled_bottom.png` | dim·bottom | `dim` | `bottom` | `textures/item/wire_enamelled_dim_bottom.png` |
| `wire_enamelled_on_top.png` / `wire_enamelled_on.png` | on·top | `on` | `top` | `textures/item/wire_enamelled_on_top.png` |
| `wire_enamelled_on_side.png` / `wire_enamelled_side.png` | on·side | `on` | `side` | `textures/item/wire_enamelled_on_side.png` |
| `wire_enamelled_on_bottom.png` / `wire_enamelled_bottom.png` | on·bottom | `on` | `bottom` | `textures/item/wire_enamelled_on_bottom.png` |
| `wire_enamelled_burnt_top.png` / `wire_enamelled_burnt.png` | burnt·top | `burnt` | `top` | `textures/item/wire_enamelled_burnt_top.png` |
| `wire_enamelled_burnt_side.png` / `wire_enamelled_side.png` | burnt·side | `burnt` | `side` | `textures/item/wire_enamelled_burnt_side.png` |
| `wire_enamelled_burnt_bottom.png` / `wire_enamelled_bottom.png` | burnt·bottom | `burnt` | `bottom` | `textures/item/wire_enamelled_burnt_bottom.png` |
| `wire_enamelled_rev_top.png` / `wire_enamelled_rev.png` | rev·top | `rev` | `top` | `textures/item/wire_enamelled_rev_top.png` |
| `wire_enamelled_rev_side.png` / `wire_enamelled_side.png` | rev·side | `rev` | `side` | `textures/item/wire_enamelled_rev_side.png` |
| `wire_enamelled_rev_bottom.png` / `wire_enamelled_bottom.png` | rev·bottom | `rev` | `bottom` | `textures/item/wire_enamelled_rev_bottom.png` |
| `wire_enamelled_closed_top.png` / `wire_enamelled_closed.png` | closed·top | `closed` | `top` | `textures/item/wire_enamelled_closed_top.png` |
| `wire_enamelled_closed_side.png` / `wire_enamelled_side.png` | closed·side | `closed` | `side` | `textures/item/wire_enamelled_closed_side.png` |
| `wire_enamelled_closed_bottom.png` / `wire_enamelled_bottom.png` | closed·bottom | `closed` | `bottom` | `textures/item/wire_enamelled_closed_bottom.png` |

### `resistor_shunt_0r01` — Шунт латунный 0,01 Ом (измерение больших токов)

* по-английски: Brass Shunt 0.01 Ohm
* kind `RESISTOR`, shape `resistor`, размер [14, 4, 4], состояний 6, индексы моделей 2922…2957, *паяется*
* палитра процедурной отрисовки: `#d4ac0d`, `#f7dc6f`, `#ecf0f1`
* рецепт: `gold_nugget`×1

| положить (теги) | вместо | состояние | грань | файл в паке |
|---|---|---|---|---|
| `resistor_shunt_0r01_off_top.png` / `resistor_shunt_0r01_off.png` / `resistor_shunt_0r01_top.png` / `resistor_shunt_0r01.png` | off·top | `off` | `top` | `textures/item/resistor_shunt_0r01_top.png` |
| `resistor_shunt_0r01_off_side.png` / `resistor_shunt_0r01_side.png` | off·side | `off` | `side` | `textures/item/resistor_shunt_0r01_side.png` |
| `resistor_shunt_0r01_off_bottom.png` / `resistor_shunt_0r01_bottom.png` | off·bottom | `off` | `bottom` | `textures/item/resistor_shunt_0r01_bottom.png` |
| `resistor_shunt_0r01_dim_top.png` / `resistor_shunt_0r01_dim.png` | dim·top | `dim` | `top` | `textures/item/resistor_shunt_0r01_dim_top.png` |
| `resistor_shunt_0r01_dim_side.png` / `resistor_shunt_0r01_side.png` | dim·side | `dim` | `side` | `textures/item/resistor_shunt_0r01_dim_side.png` |
| `resistor_shunt_0r01_dim_bottom.png` / `resistor_shunt_0r01_bottom.png` | dim·bottom | `dim` | `bottom` | `textures/item/resistor_shunt_0r01_dim_bottom.png` |
| `resistor_shunt_0r01_on_top.png` / `resistor_shunt_0r01_on.png` | on·top | `on` | `top` | `textures/item/resistor_shunt_0r01_on_top.png` |
| `resistor_shunt_0r01_on_side.png` / `resistor_shunt_0r01_side.png` | on·side | `on` | `side` | `textures/item/resistor_shunt_0r01_on_side.png` |
| `resistor_shunt_0r01_on_bottom.png` / `resistor_shunt_0r01_bottom.png` | on·bottom | `on` | `bottom` | `textures/item/resistor_shunt_0r01_on_bottom.png` |
| `resistor_shunt_0r01_burnt_top.png` / `resistor_shunt_0r01_burnt.png` | burnt·top | `burnt` | `top` | `textures/item/resistor_shunt_0r01_burnt_top.png` |
| `resistor_shunt_0r01_burnt_side.png` / `resistor_shunt_0r01_side.png` | burnt·side | `burnt` | `side` | `textures/item/resistor_shunt_0r01_burnt_side.png` |
| `resistor_shunt_0r01_burnt_bottom.png` / `resistor_shunt_0r01_bottom.png` | burnt·bottom | `burnt` | `bottom` | `textures/item/resistor_shunt_0r01_burnt_bottom.png` |
| `resistor_shunt_0r01_rev_top.png` / `resistor_shunt_0r01_rev.png` | rev·top | `rev` | `top` | `textures/item/resistor_shunt_0r01_rev_top.png` |
| `resistor_shunt_0r01_rev_side.png` / `resistor_shunt_0r01_side.png` | rev·side | `rev` | `side` | `textures/item/resistor_shunt_0r01_rev_side.png` |
| `resistor_shunt_0r01_rev_bottom.png` / `resistor_shunt_0r01_bottom.png` | rev·bottom | `rev` | `bottom` | `textures/item/resistor_shunt_0r01_rev_bottom.png` |
| `resistor_shunt_0r01_closed_top.png` / `resistor_shunt_0r01_closed.png` | closed·top | `closed` | `top` | `textures/item/resistor_shunt_0r01_closed_top.png` |
| `resistor_shunt_0r01_closed_side.png` / `resistor_shunt_0r01_side.png` | closed·side | `closed` | `side` | `textures/item/resistor_shunt_0r01_closed_side.png` |
| `resistor_shunt_0r01_closed_bottom.png` / `resistor_shunt_0r01_bottom.png` | closed·bottom | `closed` | `bottom` | `textures/item/resistor_shunt_0r01_closed_bottom.png` |

### `supply_220v` — Приводной источник 220 В / 1,5 кВт

* по-английски: Drive Supply 220 V
* kind `SOURCE`, shape `psu`, размер [15, 9, 12], состояний 6, индексы моделей 2958…2993
* палитра процедурной отрисовки: `#2c3e50`, `#e67e22`, `#ecf0f1`
* рецепт: `copper_ingot`×2, `glass`×1, `iron_block`×2, `quartz_block`×2, `repeater`×2

| положить (теги) | вместо | состояние | грань | файл в паке |
|---|---|---|---|---|
| `supply_220v_off_top.png` / `supply_220v_off.png` / `supply_220v_top.png` / `supply_220v.png` | off·top | `off` | `top` | `textures/item/supply_220v_top.png` |
| `supply_220v_off_side.png` / `supply_220v_side.png` | off·side | `off` | `side` | `textures/item/supply_220v_side.png` |
| `supply_220v_off_bottom.png` / `supply_220v_bottom.png` | off·bottom | `off` | `bottom` | `textures/item/supply_220v_bottom.png` |
| `supply_220v_dim_top.png` / `supply_220v_dim.png` | dim·top | `dim` | `top` | `textures/item/supply_220v_dim_top.png` |
| `supply_220v_dim_side.png` / `supply_220v_side.png` | dim·side | `dim` | `side` | `textures/item/supply_220v_dim_side.png` |
| `supply_220v_dim_bottom.png` / `supply_220v_bottom.png` | dim·bottom | `dim` | `bottom` | `textures/item/supply_220v_dim_bottom.png` |
| `supply_220v_on_top.png` / `supply_220v_on.png` | on·top | `on` | `top` | `textures/item/supply_220v_on_top.png` |
| `supply_220v_on_side.png` / `supply_220v_side.png` | on·side | `on` | `side` | `textures/item/supply_220v_on_side.png` |
| `supply_220v_on_bottom.png` / `supply_220v_bottom.png` | on·bottom | `on` | `bottom` | `textures/item/supply_220v_on_bottom.png` |
| `supply_220v_burnt_top.png` / `supply_220v_burnt.png` | burnt·top | `burnt` | `top` | `textures/item/supply_220v_burnt_top.png` |
| `supply_220v_burnt_side.png` / `supply_220v_side.png` | burnt·side | `burnt` | `side` | `textures/item/supply_220v_burnt_side.png` |
| `supply_220v_burnt_bottom.png` / `supply_220v_bottom.png` | burnt·bottom | `burnt` | `bottom` | `textures/item/supply_220v_burnt_bottom.png` |
| `supply_220v_rev_top.png` / `supply_220v_rev.png` | rev·top | `rev` | `top` | `textures/item/supply_220v_rev_top.png` |
| `supply_220v_rev_side.png` / `supply_220v_side.png` | rev·side | `rev` | `side` | `textures/item/supply_220v_rev_side.png` |
| `supply_220v_rev_bottom.png` / `supply_220v_bottom.png` | rev·bottom | `rev` | `bottom` | `textures/item/supply_220v_rev_bottom.png` |
| `supply_220v_closed_top.png` / `supply_220v_closed.png` | closed·top | `closed` | `top` | `textures/item/supply_220v_closed_top.png` |
| `supply_220v_closed_side.png` / `supply_220v_side.png` | closed·side | `closed` | `side` | `textures/item/supply_220v_closed_side.png` |
| `supply_220v_closed_bottom.png` / `supply_220v_bottom.png` | closed·bottom | `closed` | `bottom` | `textures/item/supply_220v_closed_bottom.png` |

### `motor_220v` — Мотор привода 220 В, 2800 об/мин

* по-английски: Drive Motor 220 V
* kind `MOTOR`, shape `motor`, размер [11, 11, 15], состояний 6, индексы моделей 2994…3029, *паяется*
* палитра процедурной отрисовки: `#7f8c8d`, `#c0392b`, `#f1c40f`
* рецепт: `copper_ingot`×2, `iron_ingot`×2, `redstone`×1

| положить (теги) | вместо | состояние | грань | файл в паке |
|---|---|---|---|---|
| `motor_220v_off_top.png` / `motor_220v_off.png` / `motor_220v_top.png` / `motor_220v.png` | off·top | `off` | `top` | `textures/item/motor_220v_top.png` |
| `motor_220v_off_side.png` / `motor_220v_side.png` | off·side | `off` | `side` | `textures/item/motor_220v_side.png` |
| `motor_220v_off_bottom.png` / `motor_220v_bottom.png` | off·bottom | `off` | `bottom` | `textures/item/motor_220v_bottom.png` |
| `motor_220v_dim_top.png` / `motor_220v_dim.png` | dim·top | `dim` | `top` | `textures/item/motor_220v_dim_top.png` |
| `motor_220v_dim_side.png` / `motor_220v_side.png` | dim·side | `dim` | `side` | `textures/item/motor_220v_dim_side.png` |
| `motor_220v_dim_bottom.png` / `motor_220v_bottom.png` | dim·bottom | `dim` | `bottom` | `textures/item/motor_220v_dim_bottom.png` |
| `motor_220v_on_top.png` / `motor_220v_on.png` | on·top | `on` | `top` | `textures/item/motor_220v_on_top.png` |
| `motor_220v_on_side.png` / `motor_220v_side.png` | on·side | `on` | `side` | `textures/item/motor_220v_on_side.png` |
| `motor_220v_on_bottom.png` / `motor_220v_bottom.png` | on·bottom | `on` | `bottom` | `textures/item/motor_220v_on_bottom.png` |
| `motor_220v_burnt_top.png` / `motor_220v_burnt.png` | burnt·top | `burnt` | `top` | `textures/item/motor_220v_burnt_top.png` |
| `motor_220v_burnt_side.png` / `motor_220v_side.png` | burnt·side | `burnt` | `side` | `textures/item/motor_220v_burnt_side.png` |
| `motor_220v_burnt_bottom.png` / `motor_220v_bottom.png` | burnt·bottom | `burnt` | `bottom` | `textures/item/motor_220v_burnt_bottom.png` |
| `motor_220v_rev_top.png` / `motor_220v_rev.png` | rev·top | `rev` | `top` | `textures/item/motor_220v_rev_top.png` |
| `motor_220v_rev_side.png` / `motor_220v_side.png` | rev·side | `rev` | `side` | `textures/item/motor_220v_rev_side.png` |
| `motor_220v_rev_bottom.png` / `motor_220v_bottom.png` | rev·bottom | `rev` | `bottom` | `textures/item/motor_220v_rev_bottom.png` |
| `motor_220v_closed_top.png` / `motor_220v_closed.png` | closed·top | `closed` | `top` | `textures/item/motor_220v_closed_top.png` |
| `motor_220v_closed_side.png` / `motor_220v_side.png` | closed·side | `closed` | `side` | `textures/item/motor_220v_closed_side.png` |
| `motor_220v_closed_bottom.png` / `motor_220v_bottom.png` | closed·bottom | `closed` | `bottom` | `textures/item/motor_220v_closed_bottom.png` |

### `fuse_slow_10a` — Предохранитель 10 А, медленный

* по-английски: Slow-blow Fuse 10 A
* kind `FUSE`, shape `fuse`, размер [12, 4, 4], состояний 6, индексы моделей 3030…3065
* палитра процедурной отрисовки: `#f0e68c`, `#bdc3c7`, `#e67e22`
* рецепт: `clay`×1, `copper_ingot`×2, `iron_nugget`×4

| положить (теги) | вместо | состояние | грань | файл в паке |
|---|---|---|---|---|
| `fuse_slow_10a_off_top.png` / `fuse_slow_10a_off.png` / `fuse_slow_10a_top.png` / `fuse_slow_10a.png` | off·top | `off` | `top` | `textures/item/fuse_slow_10a_top.png` |
| `fuse_slow_10a_off_side.png` / `fuse_slow_10a_side.png` | off·side | `off` | `side` | `textures/item/fuse_slow_10a_side.png` |
| `fuse_slow_10a_off_bottom.png` / `fuse_slow_10a_bottom.png` | off·bottom | `off` | `bottom` | `textures/item/fuse_slow_10a_bottom.png` |
| `fuse_slow_10a_dim_top.png` / `fuse_slow_10a_dim.png` | dim·top | `dim` | `top` | `textures/item/fuse_slow_10a_dim_top.png` |
| `fuse_slow_10a_dim_side.png` / `fuse_slow_10a_side.png` | dim·side | `dim` | `side` | `textures/item/fuse_slow_10a_dim_side.png` |
| `fuse_slow_10a_dim_bottom.png` / `fuse_slow_10a_bottom.png` | dim·bottom | `dim` | `bottom` | `textures/item/fuse_slow_10a_dim_bottom.png` |
| `fuse_slow_10a_on_top.png` / `fuse_slow_10a_on.png` | on·top | `on` | `top` | `textures/item/fuse_slow_10a_on_top.png` |
| `fuse_slow_10a_on_side.png` / `fuse_slow_10a_side.png` | on·side | `on` | `side` | `textures/item/fuse_slow_10a_on_side.png` |
| `fuse_slow_10a_on_bottom.png` / `fuse_slow_10a_bottom.png` | on·bottom | `on` | `bottom` | `textures/item/fuse_slow_10a_on_bottom.png` |
| `fuse_slow_10a_burnt_top.png` / `fuse_slow_10a_burnt.png` | burnt·top | `burnt` | `top` | `textures/item/fuse_slow_10a_burnt_top.png` |
| `fuse_slow_10a_burnt_side.png` / `fuse_slow_10a_side.png` | burnt·side | `burnt` | `side` | `textures/item/fuse_slow_10a_burnt_side.png` |
| `fuse_slow_10a_burnt_bottom.png` / `fuse_slow_10a_bottom.png` | burnt·bottom | `burnt` | `bottom` | `textures/item/fuse_slow_10a_burnt_bottom.png` |
| `fuse_slow_10a_rev_top.png` / `fuse_slow_10a_rev.png` | rev·top | `rev` | `top` | `textures/item/fuse_slow_10a_rev_top.png` |
| `fuse_slow_10a_rev_side.png` / `fuse_slow_10a_side.png` | rev·side | `rev` | `side` | `textures/item/fuse_slow_10a_rev_side.png` |
| `fuse_slow_10a_rev_bottom.png` / `fuse_slow_10a_bottom.png` | rev·bottom | `rev` | `bottom` | `textures/item/fuse_slow_10a_rev_bottom.png` |
| `fuse_slow_10a_closed_top.png` / `fuse_slow_10a_closed.png` | closed·top | `closed` | `top` | `textures/item/fuse_slow_10a_closed_top.png` |
| `fuse_slow_10a_closed_side.png` / `fuse_slow_10a_side.png` | closed·side | `closed` | `side` | `textures/item/fuse_slow_10a_closed_side.png` |
| `fuse_slow_10a_closed_bottom.png` / `fuse_slow_10a_bottom.png` | closed·bottom | `closed` | `bottom` | `textures/item/fuse_slow_10a_closed_bottom.png` |

### `shaft_kinetic` — Вал привода

* по-английски: Drive Shaft
* kind `KINETIC`, shape `shaft`, размер [16, 4, 4], состояний 6, индексы моделей 3066…3101
* палитра процедурной отрисовки: `#95a5a6`, `#7f8c8d`, `#ecf0f1`
* рецепт: `iron_ingot`×3

| положить (теги) | вместо | состояние | грань | файл в паке |
|---|---|---|---|---|
| `shaft_kinetic_off_top.png` / `shaft_kinetic_off.png` / `shaft_kinetic_top.png` / `shaft_kinetic.png` | off·top | `off` | `top` | `textures/item/shaft_kinetic_top.png` |
| `shaft_kinetic_off_side.png` / `shaft_kinetic_side.png` | off·side | `off` | `side` | `textures/item/shaft_kinetic_side.png` |
| `shaft_kinetic_off_bottom.png` / `shaft_kinetic_bottom.png` | off·bottom | `off` | `bottom` | `textures/item/shaft_kinetic_bottom.png` |
| `shaft_kinetic_dim_top.png` / `shaft_kinetic_dim.png` | dim·top | `dim` | `top` | `textures/item/shaft_kinetic_dim_top.png` |
| `shaft_kinetic_dim_side.png` / `shaft_kinetic_side.png` | dim·side | `dim` | `side` | `textures/item/shaft_kinetic_dim_side.png` |
| `shaft_kinetic_dim_bottom.png` / `shaft_kinetic_bottom.png` | dim·bottom | `dim` | `bottom` | `textures/item/shaft_kinetic_dim_bottom.png` |
| `shaft_kinetic_on_top.png` / `shaft_kinetic_on.png` | on·top | `on` | `top` | `textures/item/shaft_kinetic_on_top.png` |
| `shaft_kinetic_on_side.png` / `shaft_kinetic_side.png` | on·side | `on` | `side` | `textures/item/shaft_kinetic_on_side.png` |
| `shaft_kinetic_on_bottom.png` / `shaft_kinetic_bottom.png` | on·bottom | `on` | `bottom` | `textures/item/shaft_kinetic_on_bottom.png` |
| `shaft_kinetic_burnt_top.png` / `shaft_kinetic_burnt.png` | burnt·top | `burnt` | `top` | `textures/item/shaft_kinetic_burnt_top.png` |
| `shaft_kinetic_burnt_side.png` / `shaft_kinetic_side.png` | burnt·side | `burnt` | `side` | `textures/item/shaft_kinetic_burnt_side.png` |
| `shaft_kinetic_burnt_bottom.png` / `shaft_kinetic_bottom.png` | burnt·bottom | `burnt` | `bottom` | `textures/item/shaft_kinetic_burnt_bottom.png` |
| `shaft_kinetic_rev_top.png` / `shaft_kinetic_rev.png` | rev·top | `rev` | `top` | `textures/item/shaft_kinetic_rev_top.png` |
| `shaft_kinetic_rev_side.png` / `shaft_kinetic_side.png` | rev·side | `rev` | `side` | `textures/item/shaft_kinetic_rev_side.png` |
| `shaft_kinetic_rev_bottom.png` / `shaft_kinetic_bottom.png` | rev·bottom | `rev` | `bottom` | `textures/item/shaft_kinetic_rev_bottom.png` |
| `shaft_kinetic_closed_top.png` / `shaft_kinetic_closed.png` | closed·top | `closed` | `top` | `textures/item/shaft_kinetic_closed_top.png` |
| `shaft_kinetic_closed_side.png` / `shaft_kinetic_side.png` | closed·side | `closed` | `side` | `textures/item/shaft_kinetic_closed_side.png` |
| `shaft_kinetic_closed_bottom.png` / `shaft_kinetic_bottom.png` | closed·bottom | `closed` | `bottom` | `textures/item/shaft_kinetic_closed_bottom.png` |

### `gearbox_3x` — Редуктор 1:3

* по-английски: Gearbox 1:3
* kind `KINETIC`, shape `gear`, размер [12, 10, 12], состояний 6, индексы моделей 3102…3137, *паяется*
* палитра процедурной отрисовки: `#b8860b`, `#8b6508`, `#ecf0f1`
* рецепт: `iron_nugget`×2, `redstone`×2

| положить (теги) | вместо | состояние | грань | файл в паке |
|---|---|---|---|---|
| `gearbox_3x_off_top.png` / `gearbox_3x_off.png` / `gearbox_3x_top.png` / `gearbox_3x.png` | off·top | `off` | `top` | `textures/item/gearbox_3x_top.png` |
| `gearbox_3x_off_side.png` / `gearbox_3x_side.png` | off·side | `off` | `side` | `textures/item/gearbox_3x_side.png` |
| `gearbox_3x_off_bottom.png` / `gearbox_3x_bottom.png` | off·bottom | `off` | `bottom` | `textures/item/gearbox_3x_bottom.png` |
| `gearbox_3x_dim_top.png` / `gearbox_3x_dim.png` | dim·top | `dim` | `top` | `textures/item/gearbox_3x_dim_top.png` |
| `gearbox_3x_dim_side.png` / `gearbox_3x_side.png` | dim·side | `dim` | `side` | `textures/item/gearbox_3x_dim_side.png` |
| `gearbox_3x_dim_bottom.png` / `gearbox_3x_bottom.png` | dim·bottom | `dim` | `bottom` | `textures/item/gearbox_3x_dim_bottom.png` |
| `gearbox_3x_on_top.png` / `gearbox_3x_on.png` | on·top | `on` | `top` | `textures/item/gearbox_3x_on_top.png` |
| `gearbox_3x_on_side.png` / `gearbox_3x_side.png` | on·side | `on` | `side` | `textures/item/gearbox_3x_on_side.png` |
| `gearbox_3x_on_bottom.png` / `gearbox_3x_bottom.png` | on·bottom | `on` | `bottom` | `textures/item/gearbox_3x_on_bottom.png` |
| `gearbox_3x_burnt_top.png` / `gearbox_3x_burnt.png` | burnt·top | `burnt` | `top` | `textures/item/gearbox_3x_burnt_top.png` |
| `gearbox_3x_burnt_side.png` / `gearbox_3x_side.png` | burnt·side | `burnt` | `side` | `textures/item/gearbox_3x_burnt_side.png` |
| `gearbox_3x_burnt_bottom.png` / `gearbox_3x_bottom.png` | burnt·bottom | `burnt` | `bottom` | `textures/item/gearbox_3x_burnt_bottom.png` |
| `gearbox_3x_rev_top.png` / `gearbox_3x_rev.png` | rev·top | `rev` | `top` | `textures/item/gearbox_3x_rev_top.png` |
| `gearbox_3x_rev_side.png` / `gearbox_3x_side.png` | rev·side | `rev` | `side` | `textures/item/gearbox_3x_rev_side.png` |
| `gearbox_3x_rev_bottom.png` / `gearbox_3x_bottom.png` | rev·bottom | `rev` | `bottom` | `textures/item/gearbox_3x_rev_bottom.png` |
| `gearbox_3x_closed_top.png` / `gearbox_3x_closed.png` | closed·top | `closed` | `top` | `textures/item/gearbox_3x_closed_top.png` |
| `gearbox_3x_closed_side.png` / `gearbox_3x_side.png` | closed·side | `closed` | `side` | `textures/item/gearbox_3x_closed_side.png` |
| `gearbox_3x_closed_bottom.png` / `gearbox_3x_bottom.png` | closed·bottom | `closed` | `bottom` | `textures/item/gearbox_3x_closed_bottom.png` |

### `flywheel_kinetic` — Маховик 20 кг

* по-английски: Flywheel 20 kg
* kind `KINETIC`, shape `flywheel`, размер [14, 14, 6], состояний 6, индексы моделей 3138…3173, *паяется*
* палитра процедурной отрисовки: `#7f8c8d`, `#2c3e50`, `#bdc3c7`
* рецепт: `iron_nugget`×8

| положить (теги) | вместо | состояние | грань | файл в паке |
|---|---|---|---|---|
| `flywheel_kinetic_off_top.png` / `flywheel_kinetic_off.png` / `flywheel_kinetic_top.png` / `flywheel_kinetic.png` | off·top | `off` | `top` | `textures/item/flywheel_kinetic_top.png` |
| `flywheel_kinetic_off_side.png` / `flywheel_kinetic_side.png` | off·side | `off` | `side` | `textures/item/flywheel_kinetic_side.png` |
| `flywheel_kinetic_off_bottom.png` / `flywheel_kinetic_bottom.png` | off·bottom | `off` | `bottom` | `textures/item/flywheel_kinetic_bottom.png` |
| `flywheel_kinetic_dim_top.png` / `flywheel_kinetic_dim.png` | dim·top | `dim` | `top` | `textures/item/flywheel_kinetic_dim_top.png` |
| `flywheel_kinetic_dim_side.png` / `flywheel_kinetic_side.png` | dim·side | `dim` | `side` | `textures/item/flywheel_kinetic_dim_side.png` |
| `flywheel_kinetic_dim_bottom.png` / `flywheel_kinetic_bottom.png` | dim·bottom | `dim` | `bottom` | `textures/item/flywheel_kinetic_dim_bottom.png` |
| `flywheel_kinetic_on_top.png` / `flywheel_kinetic_on.png` | on·top | `on` | `top` | `textures/item/flywheel_kinetic_on_top.png` |
| `flywheel_kinetic_on_side.png` / `flywheel_kinetic_side.png` | on·side | `on` | `side` | `textures/item/flywheel_kinetic_on_side.png` |
| `flywheel_kinetic_on_bottom.png` / `flywheel_kinetic_bottom.png` | on·bottom | `on` | `bottom` | `textures/item/flywheel_kinetic_on_bottom.png` |
| `flywheel_kinetic_burnt_top.png` / `flywheel_kinetic_burnt.png` | burnt·top | `burnt` | `top` | `textures/item/flywheel_kinetic_burnt_top.png` |
| `flywheel_kinetic_burnt_side.png` / `flywheel_kinetic_side.png` | burnt·side | `burnt` | `side` | `textures/item/flywheel_kinetic_burnt_side.png` |
| `flywheel_kinetic_burnt_bottom.png` / `flywheel_kinetic_bottom.png` | burnt·bottom | `burnt` | `bottom` | `textures/item/flywheel_kinetic_burnt_bottom.png` |
| `flywheel_kinetic_rev_top.png` / `flywheel_kinetic_rev.png` | rev·top | `rev` | `top` | `textures/item/flywheel_kinetic_rev_top.png` |
| `flywheel_kinetic_rev_side.png` / `flywheel_kinetic_side.png` | rev·side | `rev` | `side` | `textures/item/flywheel_kinetic_rev_side.png` |
| `flywheel_kinetic_rev_bottom.png` / `flywheel_kinetic_bottom.png` | rev·bottom | `rev` | `bottom` | `textures/item/flywheel_kinetic_rev_bottom.png` |
| `flywheel_kinetic_closed_top.png` / `flywheel_kinetic_closed.png` | closed·top | `closed` | `top` | `textures/item/flywheel_kinetic_closed_top.png` |
| `flywheel_kinetic_closed_side.png` / `flywheel_kinetic_side.png` | closed·side | `closed` | `side` | `textures/item/flywheel_kinetic_closed_side.png` |
| `flywheel_kinetic_closed_bottom.png` / `flywheel_kinetic_bottom.png` | closed·bottom | `closed` | `bottom` | `textures/item/flywheel_kinetic_closed_bottom.png` |

### `wheel_kinetic` — Колесо каретки

* по-английски: Carriage Wheel
* kind `KINETIC`, shape `wheel`, размер [13, 13, 3], состояний 6, индексы моделей 3174…3209
* палитра процедурной отрисовки: `#34495e`, `#95a5a6`, `#ecf0f1`
* рецепт: `copper_ingot`×1, `iron_ingot`×2, `stick`×1

| положить (теги) | вместо | состояние | грань | файл в паке |
|---|---|---|---|---|
| `wheel_kinetic_off_top.png` / `wheel_kinetic_off.png` / `wheel_kinetic_top.png` / `wheel_kinetic.png` | off·top | `off` | `top` | `textures/item/wheel_kinetic_top.png` |
| `wheel_kinetic_off_side.png` / `wheel_kinetic_side.png` | off·side | `off` | `side` | `textures/item/wheel_kinetic_side.png` |
| `wheel_kinetic_off_bottom.png` / `wheel_kinetic_bottom.png` | off·bottom | `off` | `bottom` | `textures/item/wheel_kinetic_bottom.png` |
| `wheel_kinetic_dim_top.png` / `wheel_kinetic_dim.png` | dim·top | `dim` | `top` | `textures/item/wheel_kinetic_dim_top.png` |
| `wheel_kinetic_dim_side.png` / `wheel_kinetic_side.png` | dim·side | `dim` | `side` | `textures/item/wheel_kinetic_dim_side.png` |
| `wheel_kinetic_dim_bottom.png` / `wheel_kinetic_bottom.png` | dim·bottom | `dim` | `bottom` | `textures/item/wheel_kinetic_dim_bottom.png` |
| `wheel_kinetic_on_top.png` / `wheel_kinetic_on.png` | on·top | `on` | `top` | `textures/item/wheel_kinetic_on_top.png` |
| `wheel_kinetic_on_side.png` / `wheel_kinetic_side.png` | on·side | `on` | `side` | `textures/item/wheel_kinetic_on_side.png` |
| `wheel_kinetic_on_bottom.png` / `wheel_kinetic_bottom.png` | on·bottom | `on` | `bottom` | `textures/item/wheel_kinetic_on_bottom.png` |
| `wheel_kinetic_burnt_top.png` / `wheel_kinetic_burnt.png` | burnt·top | `burnt` | `top` | `textures/item/wheel_kinetic_burnt_top.png` |
| `wheel_kinetic_burnt_side.png` / `wheel_kinetic_side.png` | burnt·side | `burnt` | `side` | `textures/item/wheel_kinetic_burnt_side.png` |
| `wheel_kinetic_burnt_bottom.png` / `wheel_kinetic_bottom.png` | burnt·bottom | `burnt` | `bottom` | `textures/item/wheel_kinetic_burnt_bottom.png` |
| `wheel_kinetic_rev_top.png` / `wheel_kinetic_rev.png` | rev·top | `rev` | `top` | `textures/item/wheel_kinetic_rev_top.png` |
| `wheel_kinetic_rev_side.png` / `wheel_kinetic_side.png` | rev·side | `rev` | `side` | `textures/item/wheel_kinetic_rev_side.png` |
| `wheel_kinetic_rev_bottom.png` / `wheel_kinetic_bottom.png` | rev·bottom | `rev` | `bottom` | `textures/item/wheel_kinetic_rev_bottom.png` |
| `wheel_kinetic_closed_top.png` / `wheel_kinetic_closed.png` | closed·top | `closed` | `top` | `textures/item/wheel_kinetic_closed_top.png` |
| `wheel_kinetic_closed_side.png` / `wheel_kinetic_side.png` | closed·side | `closed` | `side` | `textures/item/wheel_kinetic_closed_side.png` |
| `wheel_kinetic_closed_bottom.png` / `wheel_kinetic_bottom.png` | closed·bottom | `closed` | `bottom` | `textures/item/wheel_kinetic_closed_bottom.png` |

### `saw_kinetic` — Механическая пила

* по-английски: Mechanical Saw
* kind `KINETIC`, shape `saw`, размер [15, 15, 3], состояний 6, индексы моделей 3210…3245, *паяется*
* палитра процедурной отрисовки: `#bdc3c7`, `#7f8c8d`, `#ecf0f1`
* рецепт: `iron_nugget`×4, `stick`×1

| положить (теги) | вместо | состояние | грань | файл в паке |
|---|---|---|---|---|
| `saw_kinetic_off_top.png` / `saw_kinetic_off.png` / `saw_kinetic_top.png` / `saw_kinetic.png` | off·top | `off` | `top` | `textures/item/saw_kinetic_top.png` |
| `saw_kinetic_off_side.png` / `saw_kinetic_side.png` | off·side | `off` | `side` | `textures/item/saw_kinetic_side.png` |
| `saw_kinetic_off_bottom.png` / `saw_kinetic_bottom.png` | off·bottom | `off` | `bottom` | `textures/item/saw_kinetic_bottom.png` |
| `saw_kinetic_dim_top.png` / `saw_kinetic_dim.png` | dim·top | `dim` | `top` | `textures/item/saw_kinetic_dim_top.png` |
| `saw_kinetic_dim_side.png` / `saw_kinetic_side.png` | dim·side | `dim` | `side` | `textures/item/saw_kinetic_dim_side.png` |
| `saw_kinetic_dim_bottom.png` / `saw_kinetic_bottom.png` | dim·bottom | `dim` | `bottom` | `textures/item/saw_kinetic_dim_bottom.png` |
| `saw_kinetic_on_top.png` / `saw_kinetic_on.png` | on·top | `on` | `top` | `textures/item/saw_kinetic_on_top.png` |
| `saw_kinetic_on_side.png` / `saw_kinetic_side.png` | on·side | `on` | `side` | `textures/item/saw_kinetic_on_side.png` |
| `saw_kinetic_on_bottom.png` / `saw_kinetic_bottom.png` | on·bottom | `on` | `bottom` | `textures/item/saw_kinetic_on_bottom.png` |
| `saw_kinetic_burnt_top.png` / `saw_kinetic_burnt.png` | burnt·top | `burnt` | `top` | `textures/item/saw_kinetic_burnt_top.png` |
| `saw_kinetic_burnt_side.png` / `saw_kinetic_side.png` | burnt·side | `burnt` | `side` | `textures/item/saw_kinetic_burnt_side.png` |
| `saw_kinetic_burnt_bottom.png` / `saw_kinetic_bottom.png` | burnt·bottom | `burnt` | `bottom` | `textures/item/saw_kinetic_burnt_bottom.png` |
| `saw_kinetic_rev_top.png` / `saw_kinetic_rev.png` | rev·top | `rev` | `top` | `textures/item/saw_kinetic_rev_top.png` |
| `saw_kinetic_rev_side.png` / `saw_kinetic_side.png` | rev·side | `rev` | `side` | `textures/item/saw_kinetic_rev_side.png` |
| `saw_kinetic_rev_bottom.png` / `saw_kinetic_bottom.png` | rev·bottom | `rev` | `bottom` | `textures/item/saw_kinetic_rev_bottom.png` |
| `saw_kinetic_closed_top.png` / `saw_kinetic_closed.png` | closed·top | `closed` | `top` | `textures/item/saw_kinetic_closed_top.png` |
| `saw_kinetic_closed_side.png` / `saw_kinetic_side.png` | closed·side | `closed` | `side` | `textures/item/saw_kinetic_closed_side.png` |
| `saw_kinetic_closed_bottom.png` / `saw_kinetic_bottom.png` | closed·bottom | `closed` | `bottom` | `textures/item/saw_kinetic_closed_bottom.png` |

### `drill_kinetic` — Горный бур

* по-английски: Mining Drill
* kind `KINETIC`, shape `drill`, размер [10, 10, 14], состояний 6, индексы моделей 3246…3281, *паяется*
* палитра процедурной отрисовки: `#7f8c8d`, `#f39c12`, `#bdc3c7`
* рецепт: `iron_ingot`×2, `stone`×1

| положить (теги) | вместо | состояние | грань | файл в паке |
|---|---|---|---|---|
| `drill_kinetic_off_top.png` / `drill_kinetic_off.png` / `drill_kinetic_top.png` / `drill_kinetic.png` | off·top | `off` | `top` | `textures/item/drill_kinetic_top.png` |
| `drill_kinetic_off_side.png` / `drill_kinetic_side.png` | off·side | `off` | `side` | `textures/item/drill_kinetic_side.png` |
| `drill_kinetic_off_bottom.png` / `drill_kinetic_bottom.png` | off·bottom | `off` | `bottom` | `textures/item/drill_kinetic_bottom.png` |
| `drill_kinetic_dim_top.png` / `drill_kinetic_dim.png` | dim·top | `dim` | `top` | `textures/item/drill_kinetic_dim_top.png` |
| `drill_kinetic_dim_side.png` / `drill_kinetic_side.png` | dim·side | `dim` | `side` | `textures/item/drill_kinetic_dim_side.png` |
| `drill_kinetic_dim_bottom.png` / `drill_kinetic_bottom.png` | dim·bottom | `dim` | `bottom` | `textures/item/drill_kinetic_dim_bottom.png` |
| `drill_kinetic_on_top.png` / `drill_kinetic_on.png` | on·top | `on` | `top` | `textures/item/drill_kinetic_on_top.png` |
| `drill_kinetic_on_side.png` / `drill_kinetic_side.png` | on·side | `on` | `side` | `textures/item/drill_kinetic_on_side.png` |
| `drill_kinetic_on_bottom.png` / `drill_kinetic_bottom.png` | on·bottom | `on` | `bottom` | `textures/item/drill_kinetic_on_bottom.png` |
| `drill_kinetic_burnt_top.png` / `drill_kinetic_burnt.png` | burnt·top | `burnt` | `top` | `textures/item/drill_kinetic_burnt_top.png` |
| `drill_kinetic_burnt_side.png` / `drill_kinetic_side.png` | burnt·side | `burnt` | `side` | `textures/item/drill_kinetic_burnt_side.png` |
| `drill_kinetic_burnt_bottom.png` / `drill_kinetic_bottom.png` | burnt·bottom | `burnt` | `bottom` | `textures/item/drill_kinetic_burnt_bottom.png` |
| `drill_kinetic_rev_top.png` / `drill_kinetic_rev.png` | rev·top | `rev` | `top` | `textures/item/drill_kinetic_rev_top.png` |
| `drill_kinetic_rev_side.png` / `drill_kinetic_side.png` | rev·side | `rev` | `side` | `textures/item/drill_kinetic_rev_side.png` |
| `drill_kinetic_rev_bottom.png` / `drill_kinetic_bottom.png` | rev·bottom | `rev` | `bottom` | `textures/item/drill_kinetic_rev_bottom.png` |
| `drill_kinetic_closed_top.png` / `drill_kinetic_closed.png` | closed·top | `closed` | `top` | `textures/item/drill_kinetic_closed_top.png` |
| `drill_kinetic_closed_side.png` / `drill_kinetic_side.png` | closed·side | `closed` | `side` | `textures/item/drill_kinetic_closed_side.png` |
| `drill_kinetic_closed_bottom.png` / `drill_kinetic_bottom.png` | closed·bottom | `closed` | `bottom` | `textures/item/drill_kinetic_closed_bottom.png` |

### `crank_hand` — Ручная рукоять (мускульный привод)

* по-английски: Hand Crank
* kind `MOVER`, shape `crank`, размер [10, 12, 10], состояний 6, индексы моделей 3282…3317
* палитра процедурной отрисовки: `#6d4c41`, `#95a5a6`, `#ecf0f1`
* рецепт: `iron_ingot`×3, `stick`×1

| положить (теги) | вместо | состояние | грань | файл в паке |
|---|---|---|---|---|
| `crank_hand_off_top.png` / `crank_hand_off.png` / `crank_hand_top.png` / `crank_hand.png` | off·top | `off` | `top` | `textures/item/crank_hand_top.png` |
| `crank_hand_off_side.png` / `crank_hand_side.png` | off·side | `off` | `side` | `textures/item/crank_hand_side.png` |
| `crank_hand_off_bottom.png` / `crank_hand_bottom.png` | off·bottom | `off` | `bottom` | `textures/item/crank_hand_bottom.png` |
| `crank_hand_dim_top.png` / `crank_hand_dim.png` | dim·top | `dim` | `top` | `textures/item/crank_hand_dim_top.png` |
| `crank_hand_dim_side.png` / `crank_hand_side.png` | dim·side | `dim` | `side` | `textures/item/crank_hand_dim_side.png` |
| `crank_hand_dim_bottom.png` / `crank_hand_bottom.png` | dim·bottom | `dim` | `bottom` | `textures/item/crank_hand_dim_bottom.png` |
| `crank_hand_on_top.png` / `crank_hand_on.png` | on·top | `on` | `top` | `textures/item/crank_hand_on_top.png` |
| `crank_hand_on_side.png` / `crank_hand_side.png` | on·side | `on` | `side` | `textures/item/crank_hand_on_side.png` |
| `crank_hand_on_bottom.png` / `crank_hand_bottom.png` | on·bottom | `on` | `bottom` | `textures/item/crank_hand_on_bottom.png` |
| `crank_hand_burnt_top.png` / `crank_hand_burnt.png` | burnt·top | `burnt` | `top` | `textures/item/crank_hand_burnt_top.png` |
| `crank_hand_burnt_side.png` / `crank_hand_side.png` | burnt·side | `burnt` | `side` | `textures/item/crank_hand_burnt_side.png` |
| `crank_hand_burnt_bottom.png` / `crank_hand_bottom.png` | burnt·bottom | `burnt` | `bottom` | `textures/item/crank_hand_burnt_bottom.png` |
| `crank_hand_rev_top.png` / `crank_hand_rev.png` | rev·top | `rev` | `top` | `textures/item/crank_hand_rev_top.png` |
| `crank_hand_rev_side.png` / `crank_hand_side.png` | rev·side | `rev` | `side` | `textures/item/crank_hand_rev_side.png` |
| `crank_hand_rev_bottom.png` / `crank_hand_bottom.png` | rev·bottom | `rev` | `bottom` | `textures/item/crank_hand_rev_bottom.png` |
| `crank_hand_closed_top.png` / `crank_hand_closed.png` | closed·top | `closed` | `top` | `textures/item/crank_hand_closed_top.png` |
| `crank_hand_closed_side.png` / `crank_hand_side.png` | closed·side | `closed` | `side` | `textures/item/crank_hand_closed_side.png` |
| `crank_hand_closed_bottom.png` / `crank_hand_bottom.png` | closed·bottom | `closed` | `bottom` | `textures/item/crank_hand_closed_bottom.png` |

### `wheel_water` — Водяное колесо

* по-английски: Water Wheel
* kind `MOVER`, shape `waterwheel`, размер [15, 15, 6], состояний 6, индексы моделей 3318…3353
* палитра процедурной отрисовки: `#5d4037`, `#8d6e63`, `#2e86c1`
* рецепт: `iron_bars`×1, `spruce_planks`×6

| положить (теги) | вместо | состояние | грань | файл в паке |
|---|---|---|---|---|
| `wheel_water_off_top.png` / `wheel_water_off.png` / `wheel_water_top.png` / `wheel_water.png` | off·top | `off` | `top` | `textures/item/wheel_water_top.png` |
| `wheel_water_off_side.png` / `wheel_water_side.png` | off·side | `off` | `side` | `textures/item/wheel_water_side.png` |
| `wheel_water_off_bottom.png` / `wheel_water_bottom.png` | off·bottom | `off` | `bottom` | `textures/item/wheel_water_bottom.png` |
| `wheel_water_dim_top.png` / `wheel_water_dim.png` | dim·top | `dim` | `top` | `textures/item/wheel_water_dim_top.png` |
| `wheel_water_dim_side.png` / `wheel_water_side.png` | dim·side | `dim` | `side` | `textures/item/wheel_water_dim_side.png` |
| `wheel_water_dim_bottom.png` / `wheel_water_bottom.png` | dim·bottom | `dim` | `bottom` | `textures/item/wheel_water_dim_bottom.png` |
| `wheel_water_on_top.png` / `wheel_water_on.png` | on·top | `on` | `top` | `textures/item/wheel_water_on_top.png` |
| `wheel_water_on_side.png` / `wheel_water_side.png` | on·side | `on` | `side` | `textures/item/wheel_water_on_side.png` |
| `wheel_water_on_bottom.png` / `wheel_water_bottom.png` | on·bottom | `on` | `bottom` | `textures/item/wheel_water_on_bottom.png` |
| `wheel_water_burnt_top.png` / `wheel_water_burnt.png` | burnt·top | `burnt` | `top` | `textures/item/wheel_water_burnt_top.png` |
| `wheel_water_burnt_side.png` / `wheel_water_side.png` | burnt·side | `burnt` | `side` | `textures/item/wheel_water_burnt_side.png` |
| `wheel_water_burnt_bottom.png` / `wheel_water_bottom.png` | burnt·bottom | `burnt` | `bottom` | `textures/item/wheel_water_burnt_bottom.png` |
| `wheel_water_rev_top.png` / `wheel_water_rev.png` | rev·top | `rev` | `top` | `textures/item/wheel_water_rev_top.png` |
| `wheel_water_rev_side.png` / `wheel_water_side.png` | rev·side | `rev` | `side` | `textures/item/wheel_water_rev_side.png` |
| `wheel_water_rev_bottom.png` / `wheel_water_bottom.png` | rev·bottom | `rev` | `bottom` | `textures/item/wheel_water_rev_bottom.png` |
| `wheel_water_closed_top.png` / `wheel_water_closed.png` | closed·top | `closed` | `top` | `textures/item/wheel_water_closed_top.png` |
| `wheel_water_closed_side.png` / `wheel_water_side.png` | closed·side | `closed` | `side` | `textures/item/wheel_water_closed_side.png` |
| `wheel_water_closed_bottom.png` / `wheel_water_bottom.png` | closed·bottom | `closed` | `bottom` | `textures/item/wheel_water_closed_bottom.png` |

### `dynamo_12v` — Машинный генератор 12 В

* по-английски: Dynamo 12 V
* kind `DYNAMO`, shape `dynamo`, размер [12, 10, 14], состояний 6, индексы моделей 3354…3389, *паяется*
* палитра процедурной отрисовки: `#b87333`, `#7f8c8d`, `#f1c40f`
* рецепт: `copper_ingot`×3, `gold_block`×1, `iron_ingot`×2, `redstone`×1

| положить (теги) | вместо | состояние | грань | файл в паке |
|---|---|---|---|---|
| `dynamo_12v_off_top.png` / `dynamo_12v_off.png` / `dynamo_12v_top.png` / `dynamo_12v.png` | off·top | `off` | `top` | `textures/item/dynamo_12v_top.png` |
| `dynamo_12v_off_side.png` / `dynamo_12v_side.png` | off·side | `off` | `side` | `textures/item/dynamo_12v_side.png` |
| `dynamo_12v_off_bottom.png` / `dynamo_12v_bottom.png` | off·bottom | `off` | `bottom` | `textures/item/dynamo_12v_bottom.png` |
| `dynamo_12v_dim_top.png` / `dynamo_12v_dim.png` | dim·top | `dim` | `top` | `textures/item/dynamo_12v_dim_top.png` |
| `dynamo_12v_dim_side.png` / `dynamo_12v_side.png` | dim·side | `dim` | `side` | `textures/item/dynamo_12v_dim_side.png` |
| `dynamo_12v_dim_bottom.png` / `dynamo_12v_bottom.png` | dim·bottom | `dim` | `bottom` | `textures/item/dynamo_12v_dim_bottom.png` |
| `dynamo_12v_on_top.png` / `dynamo_12v_on.png` | on·top | `on` | `top` | `textures/item/dynamo_12v_on_top.png` |
| `dynamo_12v_on_side.png` / `dynamo_12v_side.png` | on·side | `on` | `side` | `textures/item/dynamo_12v_on_side.png` |
| `dynamo_12v_on_bottom.png` / `dynamo_12v_bottom.png` | on·bottom | `on` | `bottom` | `textures/item/dynamo_12v_on_bottom.png` |
| `dynamo_12v_burnt_top.png` / `dynamo_12v_burnt.png` | burnt·top | `burnt` | `top` | `textures/item/dynamo_12v_burnt_top.png` |
| `dynamo_12v_burnt_side.png` / `dynamo_12v_side.png` | burnt·side | `burnt` | `side` | `textures/item/dynamo_12v_burnt_side.png` |
| `dynamo_12v_burnt_bottom.png` / `dynamo_12v_bottom.png` | burnt·bottom | `burnt` | `bottom` | `textures/item/dynamo_12v_burnt_bottom.png` |
| `dynamo_12v_rev_top.png` / `dynamo_12v_rev.png` | rev·top | `rev` | `top` | `textures/item/dynamo_12v_rev_top.png` |
| `dynamo_12v_rev_side.png` / `dynamo_12v_side.png` | rev·side | `rev` | `side` | `textures/item/dynamo_12v_rev_side.png` |
| `dynamo_12v_rev_bottom.png` / `dynamo_12v_bottom.png` | rev·bottom | `rev` | `bottom` | `textures/item/dynamo_12v_rev_bottom.png` |
| `dynamo_12v_closed_top.png` / `dynamo_12v_closed.png` | closed·top | `closed` | `top` | `textures/item/dynamo_12v_closed_top.png` |
| `dynamo_12v_closed_side.png` / `dynamo_12v_side.png` | closed·side | `closed` | `side` | `textures/item/dynamo_12v_closed_side.png` |
| `dynamo_12v_closed_bottom.png` / `dynamo_12v_bottom.png` | closed·bottom | `closed` | `bottom` | `textures/item/dynamo_12v_closed_bottom.png` |

