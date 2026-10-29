# config.yml, practical work и готовые сборки

Файл: `plugins/Electronics/config.yml` (полный с комментарями создаётся при
первом старте). Все значения применяются по `/elec reload`.

## render

| ключ | по умолчанию | что делает |
|---|---|---|
| `render.mode` | `display` | `display` — ItemDisplay/TextDisplay над платой; `none` — только предмет/блок-модель |
| `render.scale` | `0.55` | размер модели компонента (1.0 = блок) |
| `render.offset-y` | `0.06` | подъём над платой |
| `render.block-light` | `true` | подсветка блока под LED/лампой (через `BlockData#setLight`) |
| `render.view-range` | `48.0` | дистанция показа дисплеев игроку |
| `render.text-labels` | `true` | подписи значений над приборами |

## blocks

| ключ | по умолчанию | комментарий |
|---|---|---|
| `blocks.base` | `SMOOTH_QUARTZ_SLAB` | «плата», на которой стоят детали. ресурспак перекрашивает её в зелёную плату с дорожками |
| `blocks.pedestal` | `POLISHED_BLACKSTONE` | подставка под платой |
| `blocks.require-pedestal` | `true` | ставить детали только когда под платой есть блок |

Если хотите «настоящие» блоки вместо подставки — поменяйте `blocks.base` на
любой неполный блок (например `OAK_SLAB`); ресурспак переопределяет модель
только для кварцевого полублока, для других придётся скопировать
`assets/minecraft/blockstates/<блок>.json` из собранного пака.

## simulation

| ключ | по умолчанию | смысл |
|---|---|---|
| `simulation.continuous` | `true` | пересчёт каждый тик; `false` — только по событиям/`/elec sim` |
| `simulation.components-per-tick` | `900` | бюджет тика: сколько компонентов обработать за раз |
| `simulation.max-nodes-per-island` | `260` | предел узлов в одном «острове» цепи |
| `simulation.max-components-per-island` | `400` | предел деталей в острове |
| `simulation.solve-passes` | `2` | итерации нелинейного решателя (LED/транзисторы) |
| `simulation.faults` | `true` | КЗ, перегрев, пробой, сгорание |
| `simulation.conductor-ohm` | `1e-6` | сопротивление «идеального» провода |
| `simulation.leak-ohm` | `5e7` | утечка между несоединёнными узлами (нужна, чтобы матрица не была вырожденной) |
| `simulation.generator-period-ticks` | `10` | период меандра генератора (10 тик = 5 Гц) |
| `simulation.solar-day-voltage` / `-night-` | `5.0` / `0.05` | солнечная ячейка |
| `simulation.time-step-ms` | `50` | шаг интегрирования конденсаторов |
| `simulation.bench-wiring` | `false` | legacy-режим: соединять по всем 6 граням (замыкает цепочки — только для «песочницы») |

## world / resource-pack / debug

| ключ | по умолчанию | смысл |
|---|---|---|
| `world.protect-blocks` | `true` | защита плат и деталей от поломки чужими |
| `world.drop-as-item` | `true` | при поломке платы падает предмет-компонент |
| `world.sounds` | `true` | щелчки ключей, писк зуммера, «перегорание» |
| `world.hints` | `true` | подсказки в action bar (`&7нет земли`, `&eнужна опора`…) |
| `world.recipes` | `true` | регистрировать крафты на сервере |
| `world.give-in-creative` | `true` | в креативе `/elec give all` выдаёт по 1 шт. всех видов |
| `resource-pack.auto-apply` | `false` | отправлять пак при входе |
| `resource-pack.url` | `""` | прямая ссылка на zip (https!) |
| `resource-pack.sha1` | `""` | 20 байт sha1 hex; пусто = клиент не проверяет |
| `debug` | `false` | ошибки решателя и тайминги в консоль |

---

## Практические работы: `plugins/Electronics/labs/*.yml`

```yaml
name: "Светодиод и гасящий резистор"        # показывается в /lab list
task: >
  multiline текст задания
expect: "LED горит, I ~ 20 мА, P ~ 0.05 Вт"  # что сверяем (текстом + составом)
components:                                  # список материалов (id: количество)
  battery_9: 1
  resistor_330r: 1
  led_red: 1
  wire_red: 2
  ground_bus: 1
signature: ""                                # эталонная топология; пусто = не сверять
```

* `/lab start <имя>` — показать задание и (при праве `electronics.give`)
  выдать перечисленные компоненты.
* `/lab check <имя>` — сверить цепь под прицелом: состав + `signature`
  (сигнатура netlist'а, координатно-независимая: «типы и соединения», а не
  позиции). Совпало — «Работа зачтена».
* `/lab save <имя>` — записать текущую цепь как эталон в тот же файл.

---

## Готовые сборки: `plugins/Electronics/builds/*.json`

```json
{
  "name": "RC-цепочка: заряд конденсатора",
  "origin": [0, 0, 0],
  "blocks":     [{"d": "bench", "p": [0, 0, 0], "host": true}],
  "components": [{"d": "power_supply", "p": [0, 1, 0], "o": 1, "t": 1.0}]
}
```

* `p` — смещение от `origin`, `o` — ориентация (0 north, 1 east, 2 south,
  3 west, 4 up, 5 down), `t` — положение подстроечника (0..1).
* **Важно про `o`:** контакт детали «смотрит» в сторону её ориентации, поэтому
  соседние детали должны быть ориентированы навстречу (0/2 — по Z, 1/3 — по X).
* `/elec build <имя>` — поставить под прицелом; с консоли
  `/elec build <имя> <мир> <x> <y> <z>`; `/elec build save <имя>` — сохранить
  свою цепь как новый пресет.
