# Java API и внутренности

## Что даёт плагин другой программе

```java
ElectronicsPlugin ep = (ElectronicsPlugin) Bukkit.getPluginManager()
        .getPlugin("Electronics");

ep.config();        // настройки (Config)
ep.circuit();       // хранилище компонентов + цикл расчёта (Circuit)
ep.renderer();      // ItemDisplay/TextDisplay (Renderer)
ep.items();         // предметы компонентов (Items)
ep.labs();          // практические работы (LabManager)
ep.builds();        // готовые сборки-пресеты (BuildLoader)
ep.recipes();       // регистрация крафтов (Recipes)
ep.persistence();   // JSON-реестр вместо PDC (Persistence)
ep.forceSimulate(); // пересчитать сейчас
ep.stateModelCount();  // 2784 — сколько состояний модели у ресурспака
```

### Компоненты и «реестр состояний модели»

```java
Def def = Components.byId("led_red");
int sliceStart = def.baseModelIndex();          // начало среза компонента
int modelIndex = def.stateModel(state, orient); // = base + state*6 + orient
String name    = ModelState.describe(modelIndex); // "led_red [on/east]"
```

* `state` — визуальное состояние: `0 off, 1 dim, 2 on, 3 burnt, 4 reverse,
  5 closed` (`CompStates`); `Def#clampState` не даёт выйти за
  `states` компонента.
* `orient` — `0 north, 1 east, 2 south, 3 west, 4 up, 5 down`.
* Сумма `states × 6` по всем 93 видам = 2784; `Components.stateModels()`.
* Ключ модели в ресурспаке: `electronics:<id>` для `off` и
  `electronics:<id>_<off|dim|on|burnt|rev|closed>` для остальных — см.
  `Items#applyModel`, имена синхронизированы с `tools/gen_pack.py`
  и проверяются `tools/check_pack_refs.py`.

### Цепь

```java
for (CompInstance c : ep.circuit().all()) {
    c.key;            // world + x,y,z
    c.def;            // описание
    c.orientation;    // куда «смотрит» первый вывод
    c.state;          // визуальное состояние
    c.voltage;        // В (для двухполюсника — падение)
    c.current;        // А
    c.power;          // Вт
    c.brightness;     // 0..1 (LED/лампа/зуммер)
    c.reading;        // показание прибора
    c.fault;          // "" или причина сгорания
    c.pinNet[0..5];   // ключи узлов выводов (raw)
    c.pinNets[0..5];  // те же узлы после склейки (объединённые)
    c.anchorNet;      // узел «блок + блок под ним» (опора/стопка)
}

List<CompInstance> island = Simulation.islandWith(ep.circuit(), c);
String human = Simulation.describe(c);        // «LED: 2.31 В, 20.2 мА, горит»
Map<String,String> nets = Simulation.mergedNets(ep.circuit());
```

### Топология (её правили дважды — вот финальное правило)

`Topology` — чистый класс без Bukkit, его можно тестировать и переиспользовать:

* **узел = неупорядоченная пара соседних блоков `{A,B}`**; пин компонента в
  направлении `d` даёт ключ `pairNet(A, A+d)`;
* двухполюсники имеют ровно 2 пина (вдоль своей оси) — поэтому два соседа в
  линии соединяются, а «веер» из 6 граней не возникает и цепочку не коротит;
* `bench` (`Kind.BOARD`) ничего не объединяет: инертная подставка не может
  замкнуть цепь;
* `WIRE`, `GROUND`, `DISTRIBUTOR` (флаг `hub: true` в `components.json`) —
  хабы: `pinDirs` даёт 6 направлений, `isConductor()` = true, и решатель
  склеивает все их узлы в один (это и «мостик», и шина, и отвод стопкой);
* `FUSE` — 4 пина (ось + вниз + вверх), но сопротивления не теряет;
* `TRANSISTOR` 3 пина, `GATE` 4 (NOT — 3), `POT` 3, `BREADBOARD` 0 активных
  (у неё свои внутренние шины).

### Как считается цепь (`sim/Mna.java`, `circuit/Simulation.java`)

1. сбор island'ов по объединённым узлам (union-find над ключами пин-пар);
2. штампы: `G = 1/R` между двумя узлами, источник — модель Тевенена
   (`emf`, `r_int`), конденсатор — companion `C/dt` (явный Эйлер, `dt` из
   `simulation.time-step-ms`), LED/диод — кусочно-линейно (`v_f`, `r_f`),

   Знаки важны. Везде `a = pinNet[0]`, `b = pinNet[1]`, `mna.stamp(x, y, g, i)`
   добавляет проводимость между x и y и втекает ток `i` в `x`. Тогда
   * LED/диод/резистор/cond: `stamp(a, b, g, …)` — ток течёт a → b,
     `c.voltage = V(a) − V(b)`;
   * конденсатор: `stamp(a, b, C/dt, (C/dt)·vcPrev)` — **в обе части одинаково**,
     иначе получается положительная обратная связь: цепь не сходится,
     напряжение «уезжает» в минус, а полярный конденсатор честно сгорает по
     защите `voltage < -0.5`;
   * источник: `stamp(b, a, 1/r, (emf+V(b))/r)` — у источника `a` это «−»,
     поэтому `/elec dump` и показывает у батарей `U = -8.976 В` при верном
     токе (мощность `P = I·|U|` считается правильно);
   * полярный электролит ставь так, чтобы `a` (восточная нога при `o=1`,
     западная при `o=3`) смотрел туда, где в цепь втекает ток: в сборках
     обратный путь идёт по соседнему ряду, поэтому у `cap_1000uf` правильный
     вариант — `o=1`.
   транзистор — управляемые источники по `ib`/`vbe`, вентили — выход через
   100 Ом к `VCC`/`GND`, лампа — нелинейное `R(T)`;
3.gaussian elimination с выбором ведущего элемента + `bond` (идеальные
   перемычки схлопываются в один узел);
4. `simulation.solve-passes` итераций для нелинейки;
5. `protect()` — пороги отказа: обратный пробой, `P > 3·rated`, перенапряжение
   и смена полярности электролита, `I > 1.5·max` для предохранителя,
   `U > 1.6·rated` для мотора → `c.burn(fault)`.

Плавающий узел (нет земли) заземляется на первый узел острова — потенциалы
при этом показываются относительно минусового вывода источника (об этом
говорит подсказка в action bar).

## Как добавить свой компонент

1. дописать объект в `shared/components.json` (`id`, `kind`, `shape`, `size`,
   `palette`, электрика: `resistance`/`voltage`/`capacitance`/`v_forward`…,
   `states`, при необходимости `hub: true`);
2. скопировать в `plugin/src/main/resources/components.json`
   (или положить переопределение в `plugins/Electronics/components/`);
3. если форма новая — добавить рисовалку в `base_canvas()` в
   `tools/gen_pack.py`, затем `python3 tools/gen_pack.py` и
   `python3 tools/check_pack_refs.py`;
4. крафт: словарь `RECIPES` в `gen_pack.py` (датапак) и `DEFS` в
   `recipes/Recipes.java` (серверный). Списки должны совпадать id-в-id —
   `tools/run_tests.sh` это сверяет;
5. если деталь надо паять, а не клеить в верстаке — добавить её id в
   `meta.soldered` в `shared/components.json` (его читают и `Recipes.register`,
   и `tools/gen_pack.py`, так что датапак и сервер расходятся не могут),
   а объяснение для каталога — в `WHAT`/`WHAT_ID` в `gui/CatalogGui.java`.

Индексная арифметика (`base + state*6 + orient`) обновится сама: она считается
по порядку и полям `states` в `components.json` — и в Java, и в генераторе.

## Виртуальные цепи (редактор схемы)

`Simulation.solveVirtual(plugin, byPos, components)` — тот же расчёт, что и для
блоков мира, но на «бумажных» координатах: компоненты создаются напрямую
(`new CompInstance(new LocationKey(null, x, 0, z), def)`), а соседство
(«стоит ли под деталью проводник») берётся из переданной `Map<String, CompInstance>`
с ключами `Circuit.pos(x, y, z)`. Ни `Block`, ни чанки, ни `Persistence` не
участвуют; рендер и сохранение в этой ветке отключены (`notify=false`).

Именно так живёт GUI-редактор (`gui/SchematicGui`): он хранит только `defId` и
поворот клетки, а экземпляры пересоздаёт при каждом «Пересчитать». Физику в
редакторе не дублируют — единственный источник правил это `Topology` +
`stampAll`.

## События

Плагин не объявляет собственных событий; точка расширения — `Events`
(`PlayerInteractEvent`, `BlockPlace/Break`, `PrepareItemCraftEvent`,
`PlayerJoinEvent`, `ChunkLoadEvent`) и `Circuit#markDirty()`.

`ChunkLoadEvent` нужен для восстановления: `Persistence#loadChunk(world, cx, cz)`
поднимает из `registry.json` детали загруженного чанка, а
`Persistence#loadPending()` (по таймеру раз в 40 тиков) подбирает то, на что
Paper не послал события — асинхронная загрузка чанков. `pendingCount()`
показывает, сколько записей ещё ждёт своего чанка (`/elec info`). Если нужен хук «цепь
пересчитана» — добавь `CircuitSolvedEvent` и публикуй его в конце
`Circuit#engineTick()` (там уже есть `lastIslands()`, `lastSolveNanos()`).
