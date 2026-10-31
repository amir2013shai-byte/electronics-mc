# Electronics — working circuits for Minecraft 1.21.11 (Spigot)

[![build](https://github.com/amir2013shai-byte/electronics-mc/actions/workflows/build.yml/badge.svg)](https://github.com/amir2013shai-byte/electronics-mc/actions/workflows/build.yml)

Plugin + resource pack that add **functioning** electronic components as new
blocks: LEDs, diodes, resistors, capacitors, transistors, logic gates, power
supplies, meters and a blank schematic board. Circuits are solved with real
Modified Nodal Analysis — voltage, current, power, capacitor charge, LED
brightness, burnt parts. No client mod.

| | |
|---|---|
| Game | Minecraft Java **1.21.11** |
| Server | **Spigot / Paper 1.21.11-R0.2** (verified on Paper 1.21.11 build 132) |
| Java | 21 |
| Resource pack format | **75** (75.0–75.1) |
| Data pack format (recipes) | **94** |
| Client mod | not required |
| Components | 110 ids (67 soldered parts + ore & alloy chain + microcontroller + four stations + nine drive parts), RU + EN names |
| Built pack | 2401 files: 1623 item textures, 660 models (110 × 6), 110 `items/*.json` |
| Resource-pack model states | **3246** = 541 visual states × 6 orientations |

## Why the "modify the player" trick became a state-model registry

Spigot's `Player` exposes no write channel to entity NBT/state (only NMS
`EntityPlayer#updateNbt` would give it, which breaks every snapshot and is not
"mod-free" in spirit), `Block` has no PersistentDataContainer, and
`Player#sendActionBar(String)` does not exist in this API. So the "800+
mutable states" are implemented where the vanilla client actually reads them:

1. **3246 resource-pack `items` definitions** — one per
   (component, visual state, orientation), selected by the server through the
   `minecraft:item_model` item component; `custom_model_data[0]` carries the
   state index for `range_dispatch` on lamp-like parts.
2. **Orientation + state** live server-side in the plugin registry and are
   pushed on every render: 6 orientations × 541 states = 3246 addresses ≥ 800.
3. **World visuals** via `ItemDisplay`/`TextDisplay` entities (vanilla, no
   client changes): the model floats above the board, meters show their reading.
4. `render.mode: none` drops displays entirely — item models + the retextured
   base block still work.

## Install

```
plugins/Electronics-1.0.0.jar                         # or: cd plugin && mvn -DskipTests package
release/electronics-resourcepack.zip                   # resource pack (set resource-pack.url or apply manually)
release/electronics-recipes-datapack.zip               # optional: 36 recipes — 67 parts are soldered instead
```

`release/` is what the repo and the GitHub Release carry; `build/*.zip` are the
same files before `tools/publish.sh` copies them over.

First start writes `config.yml`, three lab worksheets into `labs/`, two preset
builds into `builds/`, and `registry.json` (the component registry; Spigot has
no block PDC).

## Building circuits (topology)

A net is an ordered **pair of neighbouring blocks** {A, B}. A component's pin
faces the block in its orientation, so two parts touching "face to face"
share the pin pair and are connected; an air gap is bridged by placing a wire
in it; wires have a "cap" pin up and a "foot" pin down, so parts can be
stacked on a wire run; inert plates (bench) never merge nets, and hub parts
(wires, ground/power rails) union the nets around them. Rotate with
Shift+right-click, toggle switches/buttons with right-click.

Every electrical part now declares all six visual states (inert plates,
cutters and the empty board keep one). `Def.clampState()` clips a request to
`states-1`, so with `states: 2` a gate or a meter could never show `on`, and a
burnt resistor could never show its cracks — that was 17 defs worth of silently
missing visuals. `tools/check_pack_refs.py` now also asserts `states == 6` for
electrical kinds and that `meta.state_model_count` equals Σ(`states`×6).
Resistors got `glow_color: [255,110,30]`, so `on` reads as orange-hot rather
than bleached white: the mean colour of `resistor_330r_top` moves `#aa9e8a`
(idle) → `#ca9773` (overload), `burnt` is `#4d3f37`.

Every sprite also goes through `mc_texture()`, a pass that finishes the
procedural canvas into something that reads like a vanilla resource: a 16 px
grid (2×2 cells, NEAREST only back up), a 20-colour median-cut palette applied
only where a texture really has more than 26 tones (below that, 1 px seven-segment
digits merged into the panel), a Bayer 4×4 dither instead of a soft edge where
there is transparency, ±9/255 grain on a third of the pixels, and three face
tones (top ×1.16, middle ×0.91, bottom ×0.80) — the only source of volume a
block has without light. This also fixed a real bug: models render as
`minecraft:cutout`, which discards every pixel with α < 128, so the old
BICUBIC side crop left holes along the silhouette and the `on` glow (a rectangle
at α=60) and `burnt` soot (α=70) on side faces were never drawn in game at all.
Measured over the pack: pixels discarded by cutout 473 722 → 314 568, mean
luminance unchanged (94.8 → 95.0), and no texture lost coverage (worst per-file
delta 0). Pack archives are built deterministically — sorted entries, fixed
1980-01-01 timestamps — so two runs of `python3 tools/gen_pack.py` give the
same sha1 (`b1be027c18b2962881fc69433e7d126b31705ea5` for the resourcepack on
v1.5.0; the data pack is byte-identical to v1.4.0 — `1c254875146836c1d3d6c77a080ef9301e0595d6` —
because the oscilloscope is soldered, so no data-pack recipe was added).

Full block catalog (nominals, flags, model-index ranges): `docs/blocks.md`,
generated by `python3 tools/catalog.py`. Texture sheets for a quick look:
`release/textures/sheet-01..09.png` (`python3 tools/sheets.py`).

## Commands

```
/elec give <id|all> [n]      /elec list [filter]      /elec model <id> <state> <orient>
/elec info                   /elec sim [ticks]        /elec dump [world] [n]
/elec rotate                 /elec repair [all]       /elec clear [all]
/elec mode <display|none>    /elec recipe             /elec reload
/elec show                         catalog GUI: every item with a description
/elec schematic                    draw and test a circuit in the GUI editor
/elec schematic print|save|check <name>
/elec build list|<name>|save <name>
/elec rig                            what the nearest mechanism is doing
/elec rig place [world x y z]      build the teaching sawmill
/elec rig undo                     take it apart, restore every block
/lab list|show|check|save|start <name>
```

Permissions: `electronics.use`, `electronics.build`, `electronics.give`,
`electronics.admin`. `electro` is an alias of `elec` — `/electro show` works too.

![schematic editor](docs/editor-mockup.png)
*layout mockup (not a screenshot): slots 0-15 palette, 18-44 sheet, 45-50 buttons*

The schematic is a drawing, not a build: `/elec schematic` (or right-clicking
the Blank Schematic item) opens a 9x3 chest-style grid. Palette on top, LMB
places, RMB rotates, Shift+RMB toggles a switch or erases. "Recompute" feeds the
drawing into the same solver that computes blocks in the world
(`Simulation.solveVirtual`) and writes U / I / P / node state into every cell's
tooltip, while the cell name is tinted by node potential. "Place in world"
builds the drawing out of real blocks where you look; "Save build" writes
`builds/schem_<player>.json` for `/elec build`. Capacitors always start
uncharged in the editor — that is the moment power is applied.

## Presets

* `/elec build led_basic` — 9 V battery → 330 Ω → ammeter → red LED → ground
  bus, return path wired in the row behind. Measured on the live server:
  `I = 20.19 mA`, `U_LED = 2.305 V`, brightness `1.00`, resistor
  `0.134 W` — theory `(9 − 1.8)/(1.2 + 330 + 0.5 + 25) = 20.2 mA`.
* `/elec build rc_blink` — 12 V PSU → 1 kΩ → 1000 µF (polarised, `+` east) →
  green LED → ground bus; τ = R·C = 1 s, so the LED flashes and fades out as
  the capacitor fills. Stepped with `/elec sim N`:
  7.98 mA / U_C 1.72 V → 2.94 mA / 6.89 V → 0.42 mA / 9.47 V → 0.00 mA /
  9.90 V, `I₀ = (12 − 1.8)/1025 = 9.9 mA`, `U_C(∞) = 12 − 2.1 = 9.9 V`.
  Swap the 1 kΩ for 220 Ω and the solver honestly burns it (1.73 W vs a
  0.125 W rating) — that is a lesson too.
* Both presets also take console coordinates: `/elec build <name> <world> <x> <y> <z>`.

## Magnets, coils and the schematic GUI

Eight of the 60 ids are magnetic/inductive parts: permanent magnet (1.4 mTl on
its own cell — a reed switch next to it closes), electromagnet (60 Ω, 20 mH,
0.9 mTl per amp), 10 mH and 10 H inductors, 5 V relay (120 Ω coil, pulls in at
15 mA, drops off at 8 mA — hysteresis), reed switch (0.7 mTl), photoresistor
(100 kΩ dark, hundreds of Ω in light) and an NTC thermistor (B = 3950, with
self-heating). The field is summed over the reed's cell plus its six neighbours
and re-stamped until relay/reed states settle, so one «Recalculate» click is
enough. Inductors use the backward-Euler Norton companion `g = dt / (L + dt·R)`,
and the schematic GUI has a «Симулировать 20 тиков» button that runs 20 dt-steps
without resetting capacitor charges or coil currents — the transient is visible
in the tooltip numbers.

## Power & protection (v1.2.0)

The v1.3.0 additions are on the other end of the chain — where the current comes
from (ore, prospecting, smelting, stripping) — and at the top of it: a 6-pin
microcontroller that runs a program. See the two sections below.

Fifteen new ids cover the supply side: three fuses with an I²t budget
(`fuse_cartridge_1a`, `fuse_cartridge_5a`, `fuse_slow_2a`; a 100 mA `fuse` and a
10 A `fuse_slow_10a` came later — 5 in all). The budget integrates only the
*excess* above the rating (`I² − Iном²`), which is what lets 800 mA live forever
on a 1 A cartridge while a dead short still opens it in one step; the part turns
`dim` at 45 % of the budget, so a fuse about to go is visible. Then a self-resetting PTC
(`ptc_500ma`: thermal mass `heat_j` against `thermal_k`, so a short trips it in
~30 dt-steps and it cools back into conduction on its own), an MOV (`mov_275v`:
9 nA at 9 V, a dead short at its 390 V clamp, degrades into a failed key after
three absorbed pulses), a bridge rectifier and a Schottky diode (`bridge_kbu8`,
`shottky_sbr30`), a polar electrolytic (`cap_elec_2200uf` — reverse it and it
smokes), linear regulators (`reg_7805`, `reg_7812`, `reg_lm317`: Uout =
min(target, Uin − dropout), and a shorted output turns into a 1 A current source
instead of 8 A), two transformers (`xfmr_230_12`, `xfmr_230_9` — coupled
inductors, backward Euler, M = k·√(L1·L2); the turns ratio is checked offline to
0.5 % by `tools/TestCoupled.java`), a sine source (`source_ac`, 1 Hz — a 50 ms
step cannot resolve 50 Hz, Nyquist says 10 Hz) and desolder braid
(`desolder_braid`).

Failures are reversible by design: an overloaded part enters the `burnt` visual
state and stays in the world instead of vanishing (a motor is the exception on
purpose: it accumulates seconds of triple overload and dies after four of them,
because inrush current is not a fault — see the drive section). Repair it in survival — a
soldering station within 5 blocks, a hot iron, one braid and one solder
(`/elec repair`) — or for free with `electronics.admin` (and `/elec repair all`
for debugging). Both paths call `Simulation.repair()`, which clears the fault
and zeroes accumulated I²t, case temperature and MOV degradation.

## Soldering is the crafting gate

67 of the 110 ids (everything with leads — resistors, capacitors, LEDs, diodes,
transistors, gates, relay, reed, coils, motor, buzzer, meters, switches, solar
cell, fuses, PTC, MOV, bridge, electrolytics, regulators, transformers) cannot
be crafted in a crafting table at all: `Recipes.register()` skips
them and the data pack ships only the remaining 36 recipes, while the startup
log states `зарегистрировано рецептов: 39 (пайка: 67 витрин для JEI, пропущено 7,
плавка 3)` — 39 crafting recipes, 67 solder displays, 7 parts only machines make.
The list itself lives in
`shared/components.json` → `meta.soldered`, so plugin and pack generator share it.

* **soldering station** — `P P / PFP / ICI` (planks, furnace, iron nuggets,
  copper); a hot block (furnace, campfire, lava, magma) must touch it;
* **soldering iron** — `  C /  I / S  ` (copper tip, iron shaft, stick handle),
  64 joints of durability shown as a normal damage bar; right-clicking a hot
  block with it heats the tip to 100°, which then cools at ~1.2°/s, so
  «heat» and «solder» stay two separate actions;
* **solder** — 2 iron nuggets + flint; 1 per two-lead part, 2 per three/four-lead;
* **flux** — glowstone dust + honey bottle, mandatory for heat-sensitive bodies
  (LEDs, lamps, diodes, transistors, gates, relay, reed, coils, capacitors,
  motor, buzzer, meters).

60…99° gives a proper joint, 20…59° a **cold joint** (solder burnt, part not
produced, blanks kept), below 20° the attempt is refused. `/elec solder` opens
the station GUI by hand for a lesson. Every rule lives in the pure function
`recipes/Soldering.evaluate(...)`, and `/elec selftest` asserts exactly that
function, so GUI and tests cannot drift apart.

### JEI / REI / EMI without a client mod

JEI reads the recipe list the server already syncs to every client
(`ClientboundUpdateRecipesPacket`), so the plugin pushes **all 110 ids** into it:
39 real crafting recipes plus 67 *display* recipes (`recipe_solder_<id>`) for
the soldered parts. A display recipe is a `ShapedRecipe` with the same blank
grid the station demands, and the **soldering station sits in the free cell** —
so in JEI it reads as the machine of the recipe (and pressing `R` on the
station lists everything soldered on it). Crafting it by hand is impossible:
`Events#onCraftDisplay` cancels the `CraftItemEvent` and prints where to go
instead. Temperature, solder count and the flux rule live in the item tooltip
(`Items#componentItem` → `Soldering#solderHint`), which arrives with the recipe
result, so hovering the part in JEI already says `припой ×2, флюс ×1, жало 60°+`.
The four dense 3×3 recipes (solar cell, AND, XOR, multimeter) were trimmed to
eight blanks on purpose, so that the ninth cell can hold the station.

Notes: JEI is client-side only — nothing to install on the server; REI/EMI/FFBI
work the same way; `world.recipes: false` hides both crafts and displays; and a
true custom JEI *category* (progress bar, temperature gauge) would require a
client addon, which the task forbids. `/elec selftest` proves the visible part:
`PASS JEI: витрин 66/(нужно 66), … стол в сетке рецептов: 66/66, в менеджере
рецептов клиента: 106 (39 крафта + 67 витрин)` — all 110 ids reach the client.`

### Can our `custom_model_data` leak onto other items (a disc in a jukebox)?

No, and the pack build proves it rather than promises it:

* state selection happens through the **`item_model` component on the stack**
  (`electronics:<id>_<state>`); the `custom_model_data` branch in `Items.applyModel`
  is only a fallback for pre-1.21.4 cores, and `/elec selftest` fails if it ever
  engages: `PASS item_model is set on 110/110 items,
  custom_model_data не ставится ни на одном`;
* `custom_model_data` is read inside the definition of *that* item. All our
  definitions live in `assets/electronics/items/` (110 files, with a CMD
  dispatch); `assets/minecraft/items/` holds **zero** files, so `music_disc_13`,
  `jukebox`, potions and spawn eggs keep vanilla behaviour;
* `tools/check_pack_refs.py` aborts if the pack ever ships another
  `assets/minecraft/**` file beyond the atlas source list and the three quartz
  textures — a silent vanilla override is now a build error;
* the indices (0…3245) are not global numbers: in the fallback case the integer
  sits on a `SMOOTH_QUARTZ_SLAB`/`BRUSH` stack, never on a disc, and the plugin
  refuses to touch items that are not in its registry (`Items.defOf` → null);
* no `music_disc`/`jukebox`/`RECORD_` references exist in the plugin or tools
  (the only `disc` hit is the shape of a ceramic capacitor); buzzer/reed sounds
  go through `world.playSound`.

The one genuine override in a foreign namespace is `quartz_block_side/top/bottom`
— smooth quartz looks like a PCB world-wide, which is the point of the
`blocks.base` config. Which also shows how you *could* break a jukebox: set
`blocks.base: JUKEBOX` and every jukebox becomes a plate (and the inserted disc
disappears, since it is part of the block model). The default is a building
block, not a machine, for exactly this reason.

## Ore: geology as a function, stripping and smelting

Vanilla ore cannot carry our data — it drops vanilla loot, and we do not put our
texture on somebody else's block (no client mod). So a vein lives in the plugin as
a **function**, not as blocks: from chunk coordinates and the world seed it follows
deterministically whether a vein is here, which one, how large and how deep. It
cannot be half-mined and then lost, it survives restarts, and no worldgen has to
be taught about it.

* **Vein** — copper / tin / zinc, 14…40 blocks and a Y band from `config.yml`.
  Until it is **revealed**, digging the rock yields plain stone.
* **Geoprobe** (`geoprobe`, a tool): right-click rock — either "nothing here" or
  bearing + distance + depth + how many blocks are left. Clicking inside a vein
  reveals it (1 durability).
* **Raw chunks** (`raw_copper_chunk`, …) drop from blocks inside a revealed vein
  instead of cobblestone. Two chunks → one **ingot** in a vanilla furnace: a real
  `FurnaceRecipe` with `ExactChoice`, because our item is the same vanilla slab
  and JSON recipes cannot match its NBT.
* **Stripping/tinning**: `wire_stripper` (shift + right-click in the air) turns
  insulated wire into bare wire; tinning happens on the table (bare wire + solder
  + abrasive → `wire_tinned`).
* The rest runs through the soldering table, and part of the recipe is **our own**
  items, so it lives in `Soldering.OUR_INGREDIENTS` rather than `Recipes.DEFS`:
  2 copper ingots + 2 string → bare wire; bare wire + solder → tinned wire;
  2 zinc ingots + coal + string → zinc-air cell; tinned wire + 16 MHz crystal →
  microcontroller.
* Prospecting data is plugin state, persisted as `deposits.json` (reveal flags and
  how much of each vein was mined).

Arithmetic lives in `deposit/Deposits.java`, which knows nothing about Bukkit, so
it is tested offline (`tools/TestDeposits.java`, 16 checks: same seed ⇒ same field,
vein frequency vs config, Y band, "not revealed ⇒ not minable", depletion, file format).

## Microcontroller: an Arduino analogue with a C-like language

`mcu_atmega328` has 6 pins: **0 = VCC, 1 = GND, 2…5 = programmable ports**.
The part is electrically real — its pins are nodes of your circuit, not LEDs.

* Output is a **Thévenin source**: EMF = `VCC × level`, port resistance 25 Ω,
  40 mA per pin. A shorted port delivers 40 mA, not 120: the limit is soft (the
  key's resistance grows, like a real transistor leaving saturation). An "ideal
  current source" version lifted a floating node to 40 kV and the solver faithfully
  reported it — that is how this was caught.
* Input is hi-Z: node voltage read as a bit (threshold VCC/2) and as ADC 0…1023.
  Supply is mandatory — below 2.6 V the chip halts and releases its pins
  (brown-out); the die itself draws 470 kΩ from VCC to GND.
* The program is typed into a writable book (a chest GUI has no text field, and a
  book is the only multi-line input without a client mod). Open it from
  `programmer_usb` within 4 blocks of the chip: live per-pin readout — direction,
  level, volts, ADC, milliamps — plus the compiler error with its line number.
* Language is an Arduino subset: `pin N input|output|pwm;`, `digitalWrite(N, HIGH)`,
  `analogWrite(N, 0..255)`, `delay(ms)`, `if (...) { } else { }`, `while (...) { }`,
  `== != > < >= <=`, `digitalRead`/`analogRead`, `//` comments. No variables,
  functions or arithmetic on purpose: the state of the machine is its pin levels.
* Execution is cooperative, inside the 20 Hz sim step: at most 64 statements per
  tick, `while` bounded to 64 repeats, `delay(50)` = one tick. An infinite loop
  cannot hang the server — it only burns the tick budget.
* The program text is persisted (`"p"` in `registry.json`); pin state and the
  program counter are not — after a restart the chip starts from the beginning,
  like a real Arduino after a power cycle.

The interpreter is `fw/Firmware.java`, also Bukkit-free: `tools/TestFirmware.java`
checks compilation, error line numbers, `if/else` jumps (the trap is the else
branch), the `while` quota, `delay` in ticks and ADC (21 checks).


## Drive: kinetics on the shaft (v1.4.0)

Nine new ids, and only three of them touch the circuit: `supply_220v` (220 V
behind 6 Ω), `motor_220v`, `fuse_slow_10a`. The other six are `kind: KINETIC`
with **zero pins** — shaft, gearbox, flywheel, carriage wheel, saw, drill. The
solver never sees a shaft, and a shaft never closes a net. One coupling holds
them together, and it is physical: motor torque *is* armature current, and
armature current *is* speed back-EMF.

```
I = (U_term − Φ·ω) / R_wind       M = Φ·I       ω̇ = (M·i·η − M_load) / J
```

So a saw biting into a log drags the shaft down, which drops the back-EMF, which
lifts the current and the torque — and when the drive cannot lift it, the
winding cooks. All of it is `Kinetics.step()`, Bukkit-free, 38 offline checks in
`tools/TestKinetics.java`; `KineticEngine` only gathers the rigid cluster of
neighbouring parts once a tick and hands the solver the fraction of rated speed
that sets the back-EMF.

* **Starting is an event.** 220 V across 22 Ω is 7.86 A and 5.90 N·m at stall.
  Cutting a log is cheap; bearing friction is not — the same rig asked for
  4.62 N·m on stone footing and 0.96 N·m on rails. Miss the torque and the motor
  sits at 1.73 kW against a 500 W rating; the winding accumulates the overload and
  burns after ~26 s ("перегрев") — a timer, not a first-tick detonation. With
  `gearbox_3x` the blade gets 15.9 N·m — **×2.7, not ×3**,
  mesh η = 0.9 — and cuts 6 logs in 30 s at 1269 rpm, 425 W on shaft.
* **A flywheel stores, it does not create.** 0.4 kg·m² at 1757 rpm is 6.7 kJ,
  and every strike costs a quarter of it: one 1500 J log pulls 1200 J and drops
  the wheel to 1593 rpm. Spin-up from rest is 14.7 s — inertia, not solver lag.
* **Bearing friction is the block under the wheel.** μ = 0.005 on `RAIL`
  against 0.35 on stone: on stone the carriage says "not enough traction", on a
  rail it rolls at 2.75 m/s. `/elec rig place` lays rails under the wheels for
  no other reason.
* **The mechanics hit the circuit back.** A drill against stone keeps 7.86 A
  flowing, and `fuse_cartridge_5a` (I²t 45) integrates only the *excess* above
  its rating, so it reports "1.23 s left" — time enough to pull the rig away.
  Raw I²t would blow a 1 A cartridge at 1 A.
* `kinetics.enabled`, `kinetics.break-blocks`, `kinetics.carriage-move` and
  `kinetics.blocks-per-minute` (40, a sliding one-minute window **per mechanism**,
  not per server) stop a mill from turning a hillside into gravel.

`/elec rig` prints what the nearest mechanism is doing (shaft rpm, motor rpm,
torque, carriage traction, last note); `/elec rig place [world x y z]` builds
the teaching sawmill, `/elec rig undo` takes it down and restores every block it
touched. The live selftest drives the engine for 600 manual ticks and requires
`блоков срублено > 0` plus a clean undo.

---

How to add a part (registry → pack → solver → crafting → tests → release):
[`docs/adding-parts.md`](docs/adding-parts.md) (Russian).

## Oscilloscope: the waveform on a `TextDisplay` (v1.5.0)

The instrument list had voltmeters and multimeters — numbers only. `oscilloscope`
draws the shape. It is an ordinary two-terminal part with a 10 MΩ input
(`internal_resistance`): it must not load what it measures, because a scope is a
voltmeter with memory. Above the block hangs a `TextDisplay` with a 40×16 pixel
raster — a braille glyph is 2×4 pixels, so a row is 20 characters and the whole
screen is 4 rows. No client mod: grid, trace and afterglow are composited
server-side into one string. RMB on the instrument steps the time base
(×1/×2/×4/×8; at ×1 the screen spans 2 s at the 50 ms simulation step). The
trigger anchors on a rising edge so the picture does not crawl, and the vertical
scale is picked automatically with 30 % headroom. The label under the block reads
`pk-pk 12.0 V · 1.2 V/div · 1.00 Hz · 0.20 s/div`. Tunables: `scope.text-size`,
`scope.refresh-ticks`, `scope.afterglow` (the previous frame is redrawn through
every third column, so a rare pulse survives between refreshes).

---

**Why the jar is still `Electronics-1.0.0.jar`.** The file name is the Maven
artifact (`<version>` in `pom.xml`); renaming it would move paths referenced by
`.github/workflows`, `tools/publish.sh` and every existing `plugins/` folder.
The plugin version itself is real: `plugin.yml: version: 1.5.0`, which is what
the server logs on enable and what `/version Electronics` prints.

## Checks actually run

* `bash tools/run_tests.sh` → MNA solver `ALL OK` (16 checks), kinetics
  `TestKinetics: ALL OK (38 checks)`, scope `TestScope: ALL OK (30 checks)`,
  topology and
  circuit solve `OK=25 FAIL=0` (9 V / 220 Ω / LED loop: 28.46 mA against the
  hand-computed 28.9 mA), and the pack checker
  `OK: все 110×6 ссылок на модели и текстуры разрешаются` (also standalone:
  `python3 tools/check_pack_refs.py`).
* `mvn -DskipTests package` → `BUILD SUCCESS`, `target/Electronics-1.0.0.jar`.
* Live run on Paper 1.21.11 build 132: plugin enables — 110 components, 3246
  model states, 106 recipes (39 crafting + 67 JEI showcases), 3 worksheets;
  `selftest: true` → 24 PASS / 0 FAIL, including the in-world sawmill and two scope
  checks — `PASS осциллограф: кадр 83 пикс., строки 4×20 | Upp 12.0 В | f 1.000 Гц`
  and the in-world one, `PASS живой экран осциллографа: TextDisplay над блоком
  появился: true, после удаления детали исчез: true`;
  `/elec build` placed both presets (12 + 12 parts, 13 + 13 hosts),
  `/elec sim` reported `islands=1` for a single loop and `islands=2` for the
  two independent ones, `/elec dump` printed U/I/P per part.
* Restart test: `восстановлено компонентов из реестра: 50` — all 50 registry
  entries came back and both circuits solved to the same currents. Because no
  block state is used, parts only return when their chunk is loaded
  (`ChunkLoadEvent` plus a sweep every 2 s; `forceload add 16 16 48 48`
  reproduces it from the console). Initial capacitor voltage is not stored, so
  a reloaded capacitor starts empty and charges again.

CI: `.github/workflows/build.yml` runs `mvn package`, `tools/publish.sh`,
`tools/run_tests.sh`, `check_pack_refs.py` and asserts `pack_format == 75`;
artifacts go to the run, and a `v*` tag additionally publishes a GitHub Release.
The jar is reproducible (`project.build.outputTimestamp` in `pom.xml`): two clean
`mvn clean package` runs and the jar that
GitHub Actions publishes to the release all share sha1
`f759e930cc4e111dbd10da72171efc38344d4576` (361 711 B, re-verified on v1.5.0; on v1.4.0 it
was `ca9047ad5e86ce6554785047cc770b98fb9c84db`, 348 091 B), which is also what
`release/sha1sums.txt` records — so "works on my machine" can be checked by
everybody. The plugin targets Spigot 1.21.11 (`spigot-api` 1.21.11-R0.2); the
1.21.4–1.21.11 branch is the only one verified live, the newer Spigot 26.2.x
line is documented and code-covered, not tested.

Repo: **https://github.com/amir2013shai-byte/electronics-mc**
See `README.md` (Russian) for the full description, `docs/config.md`,
`docs/api.md`, `docs/pack-compatibility.md`, `docs/setup.md`.
