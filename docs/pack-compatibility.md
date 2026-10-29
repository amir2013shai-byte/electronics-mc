# Как это устроено: ресурспак и версии

## Форматы паков

| Версия игры | resource pack | data pack | комментарий |
|---|---|---|---|
| 1.21.7–1.21.8 | 64 | 80 | без атласов-`atlases/*.json` |
| 1.21.9–1.21.10 | 69 | 88 | введены `assets/minecraft/atlases/*` (свои текстуры надо объявлять) |
| **1.21.11** | **75** (75.0–75.1) | **94** | целевая версия проекта |
| 26.x | 76+ | 95+ | не тестировалось |

С 1.21.9 формат имеет минорную версию, и минор совместим внутри мажора: пак с
`"pack_format": 75` примут и 1.21.11, и хотфиксы 1.21.11.x. Поле
`supported_formats` в `pack.mcmeta` с 1.21.9 **запрещено** для паков с
`pack_format >= 65` — у нас его нет. `pack_format` теперь необязателен, но мы
его пишем, чтобы игрок видел «пак для 1.21.11».

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
3. `shared/components.json → meta.resource_pack_format` + `tools/gen_pack.py`
   (`fmt`, `94` для датапака) → значения из таблицы выше.
4. Проверить, что `atlases/blocks.json` нужен (≥1.21.9) или уже не нужен (<).
5. `mvn -DskipTests package && python3 tools/gen_pack.py`.

## Структура собранного ресурспака

```
build/resourcepack/
├── pack.mcmeta                       # pack_format 75 + описание
├── pack.png                          # иконка (генерируется: плата + LED)
├── assets/minecraft/atlases/blocks.json     # «шейте мои текстуры»
├── assets/minecraft/textures/block/quartz_block_{side,top,bottom}.png  # перекраска платы
├── assets/electronics/lang/{ru_ru,en_us}.json
├── assets/electronics/items/<id>.json                    # 52: выбор состояния модели
├── assets/electronics/models/item/<id>[_<state>].json    # 223: модель + display-трансформы
└── assets/electronics/textures/item/<id>_<state>_{top,side,bottom}.png  # 669 текстур 32×32
```

Всего 952 файла, zip ≈ 0.5 МБ (`build/electronics-resourcepack.zip`).
Состояние модели = `baseModelIndex + state·6 + orientation` — та же арифметика
в `registry/Components.java` (плагин) и в `tools/gen_pack.py` (пак); индекс
можно посмотреть командой `/elec model <id> <состояние> <ориентация>`.

## Если ресурспак не применить

Без пака мир **работает** (симуляция серверная), но вместо моделей деталей
игрок видит базовый блок `blocks.base` (кварцевый полублок) и подписи
TextDisplay. Поэтому `config.yml: resource-pack.auto-apply: true` +
`resource-pack.url` — рекомендуемая конфигурация для урока.
