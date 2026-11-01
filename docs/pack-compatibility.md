# Как это устроено: ресурспак и версии

## Форматы паков

| Версия игры | resource pack | data pack | комментарий |
|---|---|---|---|
| 1.21.7–1.21.8 | 64 | 80 | без атласов-`atlases/*.json` |
| 1.21.9–1.21.10 | 69 | 88 | введены `assets/minecraft/atlases/*` (свои текстуры надо объявлять) |
| **1.21.11** | **75** (75.0–75.1) | **94** | целевая версия проекта |
| 26.x | 76+ | 95+ | не тестировалось |

С 1.21.9 формат имеет минорную версию, и минор совместим внутри мажора: пак с
`75` примут и 1.21.11, и хотфиксы 1.21.11.x. Главное изменение — `pack_format`
больше **не достаточен**: `min_format`/`max_format` обязательны, а
`supported_formats` разрешено ровно тогда, когда диапазон начинается ниже
65 (ресурспак) / 82 (датапак), и тогда оно обязано совпадать с мажорами
`min_format`/`max_format`. Наш заголовок (`tools/gen_pack.py` → `mcmeta()`)
пишет все четыре поля, поэтому один и тот же архив принимают 1.21.7 и 1.21.11:
ресурспак `64..75`, датапак `80..94`.

Цена ошибки — молчание, а не exception: лог живого 1.21.11 на старом заголовке
(`pack_format: 94`, без min/max) был ровно

```
Pack declares support for version newer than 81, but is missing mandatory
fields min_format and max_format
```

и пакета не было вовсе.

## Что из чего требует какая версия

| Приём | Minimum | Где используется |
|---|---|---|
| `minecraft:item_model` (компонент предмета) | 1.21.4 | `Items#componentItem`, смена модели по состоянию |
| `assets/<ns>/items/*.json` + `select`/`range_dispatch` по `custom_model_data` | 1.21.4 | реестр состояний модели (`tools/gen_pack.py`) |
| `ItemDisplay`/`TextDisplay` + `setItemModel` | 1.17.1 / 1.21.4 | `render/Renderer.java` |
| `Block#getBlockData().setLight(...)` (`org.bukkit.data.BlockLight`) | 1.21 | подсветка под LED |
| `assets/minecraft/atlases/blocks.json` | **1.21.9** | без него свои текстуры не попадут в атлас |
| `data/<ns>/recipe/*.json` (папка в единственном числе) | 1.20.5 | датапак рецептов |
| `Player#setResourcePack(String, byte[])` | 1.20.2+ (hash = 20 байт) | `resource-pack.*` в конфиге |

## Как перенести на другую версию

1. `plugin/pom.xml` → `<version>1.21.XX-R0.2-SNAPSHOT</version>` спигот-API.
2. `plugin/src/main/resources/plugin.yml` → `api-version`.
3. `shared/components.json → meta.resource_pack_format` (верхняя граница
   ресурспака) + `tools/gen_pack.py` → `RP_MIN_FORMAT`/`RP_MAX_FORMAT` и
   `DP_MIN_FORMAT`/`DP_MAX_FORMAT` (у датапака своя линия форматов!) → значения
   из таблицы выше. `tools/check_pack_refs.py` и шаг CI сверяют заголовок.
4. Проверить, что `atlases/blocks.json` нужен (≥1.21.9) или уже не нужен (<).
5. `mvn -DskipTests package && python3 tools/gen_pack.py`.

## Структура собранного ресурспака

```
build/resourcepack/
├── pack.mcmeta                       # 64..75: pack_format + supported_formats + min/max
├── pack.png                          # иконка (генерируется: плата + LED)
├── assets/minecraft/atlases/blocks.json     # «шейте мои текстуры»
├── assets/minecraft/textures/block/quartz_block_{side,top,bottom}.png  # перекраска платы
├── assets/electronics/lang/{ru_ru,en_us}.json
├── assets/electronics/items/<id>[_<state>].json          # 684: 114 базовых + адрес каждого состояния
├── assets/electronics/models/item/<id>[_<state>].json    # 684: модель + display-трансформы
└── assets/electronics/textures/item/<id>_<state>_{top,side,bottom}.png  # 1695 текстур 32×32 (или то, что положили в users_textures/)
```

Файлы в чужом namespace ровно два вида, и оба безопасны:

* `assets/minecraft/atlases/blocks.json` — **дополняет** список источников
  атласа (клиент склеивает `sources` из всех паков, а не заменяет файл), поэтому
  ванильные `block/` и `item/` никуда не деваются; у пака нет ни одного файла в
  `assets/minecraft/items/`, то есть определения предметов ванилы (включая
  `music_disc_*` и `jukebox`) не перезаписаны — `tools/check_pack_refs.py` падает,
  если там что-то появится;
* `assets/minecraft/textures/block/quartz_block_*.png` — текстура блока-основы.

С 1.21.11 текстуры предметов вынесены в отдельный атлас `items`, и одна модель
не может смешивать атласы: все наши спрайты (1692 текстур предмета плюс 3
перекрашенных текстур слаба = 1695) попадают в `blocks`
через наш же источник `electronics/item`, поэтому каждая модель одноатласная ✓.

Всего 2501 файл, zip ≈ 2.5 МБ (`build/electronics-resourcepack.zip`).
Состояние модели = `baseModelIndex + state·6 + orientation` — та же арифметика
в `registry/Components.java` (плагин) и в `tools/gen_pack.py` (пак); индекс
можно посмотреть командой `/elec model <id> <состояние> <ориентация>`.

## Если ресурспак не применить

Без пака мир **работает** (симуляция серверная), но вместо моделей деталей
игрок видит базовый блок `blocks.base` (кварцевый полублок) и подписи
TextDisplay. Поэтому `config.yml: resource-pack.auto-apply: true` +
`resource-pack.url` — рекомендуемая конфигурация для урока.
