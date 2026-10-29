# Electronics — working circuits for Minecraft 1.21.11 (Spigot)

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
| Components | 52 ids (45 electrical + boards/tools), RU + EN display names |
| Built pack | 1041 files: 669 item textures, 312 models (52 × 6), 52 `items/*.json` |
| Resource-pack model states | **1338** = 223 visual states × 6 orientations |

## Why the "modify the player" trick became a state-model registry

Spigot's `Player` exposes no write channel to entity NBT/state (only NMS
`EntityPlayer#updateNbt` would give it, which breaks every snapshot and is not
"mod-free" in spirit), `Block` has no PersistentDataContainer, and
`Player#sendActionBar(String)` does not exist in this API. So the "800+
mutable states" are implemented where the vanilla client actually reads them:

1. **1338 resource-pack `items` definitions** — one per
   (component, visual state, orientation), selected by the server through the
   `minecraft:item_model` item component; `custom_model_data[0]` carries the
   state index for `range_dispatch` on lamp-like parts.
2. **Orientation + state** live server-side in the plugin registry and are
   pushed on every render: 6 orientations × 223 states = 1338 addresses ≥ 800.
3. **World visuals** via `ItemDisplay`/`TextDisplay` entities (vanilla, no
   client changes): the model floats above the board, meters show their reading.
4. `render.mode: none` drops displays entirely — item models + the retextured
   base block still work.

## Install

```
plugins/Electronics-1.0.0.jar                         # or: cd plugin && mvn -DskipTests package
build/electronics-resourcepack.zip                     # resource pack (set resource-pack.url or apply manually)
build/electronics-recipes-datapack.zip                 # optional: 52 vanilla crafting recipes
```

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

## Commands

```
/elec give <id|all> [n]      /elec list [filter]      /elec model <id> <state> <orient>
/elec info                   /elec sim [ticks]        /elec dump [world] [n]
/elec rotate                 /elec repair [all]       /elec clear [all]
/elec mode <display|none>    /elec recipe             /elec reload
/elec schematic print|save|check <name>
/elec build list|<name>|save <name>
/lab list|show|check|save|start <name>
```

Permissions: `electronics.use`, `electronics.build`, `electronics.give`,
`electronics.admin`.

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

## Checks actually run

* `bash tools/run_tests.sh` → MNA solver `ALL OK` (16 checks), topology and
  circuit solve `OK=25 FAIL=0` (9 V / 220 Ω / LED loop: 28.46 mA against the
  hand-computed 28.9 mA), and the pack checker
  `OK: все 52×6 ссылок на модели и текстуры разрешаются` (also standalone:
  `python3 tools/check_pack_refs.py`).
* `mvn -DskipTests package` → `BUILD SUCCESS`, `target/Electronics-1.0.0.jar`.
* Live run on Paper 1.21.11 build 132: plugin enables — 52 components, 1338
  model states, 51 recipes (one id has no `Material`), 3 worksheets;
  `/elec build` placed both presets (12 + 12 parts, 13 + 13 hosts),
  `/elec sim` reported `islands=1` for a single loop and `islands=2` for the
  two independent ones, `/elec dump` printed U/I/P per part.
* Restart test: `восстановлено компонентов из реестра: 50` — all 50 registry
  entries came back and both circuits solved to the same currents. Because no
  block state is used, parts only return when their chunk is loaded
  (`ChunkLoadEvent` plus a sweep every 2 s; `forceload add 16 16 48 48`
  reproduces it from the console). Initial capacitor voltage is not stored, so
  a reloaded capacitor starts empty and charges again.

See `README.md` (Russian) for the full description, `docs/config.md`,
`docs/api.md`, `docs/pack-compatibility.md`.
