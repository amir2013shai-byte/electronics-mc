package me.arena.electronics;

import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;

/** Настройки плагина (config.yml). */
public final class Config {

    // --- внешний вид ---
    public String renderMode = "display";       // display | none
    public double renderScale = 0.55;
    public double offsetY = 0.06;
    public boolean autoLight = true;
    public double viewRange = 48.0;
    public boolean textLabels = true;

    // --- блоки-основания ---
    public Material baseBlock = Material.SMOOTH_QUARTZ_SLAB;
    public Material pedestalBlock = Material.POLISHED_BLACKSTONE;
    public boolean requirePedestal = true;

    // --- симуляция ---
    public boolean simContinuous = true;
    public int componentsPerTick = 900;
    public int maxIslandNodes = 260;
    public int maxIslandComponents = 400;
    public int solvePasses = 2;
    public boolean faultsEnabled = true;
    public double conductorOhm = 1e-6;
    public double leakOhm = 5.0e7;
    public int generatorPeriodTicks = 10;
    public double solarDayVoltage = 5.0;
    public double solarNightVoltage = 0.05;
    public int simDtMillis = 50;
    public boolean benchWiring = false;

    // ---дра: закрытые жилы (worldgen их не ставит, их «вскрывает» геозонд) ---
    public boolean depositsEnabled = true;
    /** 0 = брать сид мира; иначе — свой, чтобы недра можно было воспроизвести. */
    public long depositSeed = 0;
    /** Доля чанков с жилой. 0.18 = одна жила примерно на шесть чанков. */
    public double depositChance = 0.18;
    public int depositYMin = -48;
    public int depositYBand = 40;
    public int depositYield = 1;
    public int depositVeinSize = 14;
    public int depositVeinJitter = 26;

    // --- мир/игроки ---
    public boolean protectBlocks = true;
    public boolean dropAsItem = true;
    public boolean soundsEnabled = true;
    public boolean hintsEnabled = true;
    public boolean recipesEnabled = true;
    public boolean creativeGiveAll = true;

    // ---- механика привода (v1.4.0)
    /** Главный выключатель: выключено — детали привода просто лежат блоками. */
    public boolean kineticsEnabled = true;
    /** Разрешить пиле и буру ломать блоки мира (сервер может это запретить). */
    public boolean kineticsBreak = true;
    /** Разрешить каретке на колёсах ездить (перенос механизма на блок вперёд). */
    public boolean kineticsMove = true;
    /** Сколько блоков за минуту имеет право разрушить один механизм. */
    public int kineticsBlocksPerMinute = 40;

    // --- прочее ---
    public boolean resourcePackAuto = false;
    public String resourcePackUrl = "";
    public String resourcePackHash = "";
    public boolean debug = false;
    /** Проверить сборку на старте (GUI-редактор + каталог) без игрока. */
    public boolean selftestOnEnable = false;

    private final ElectronicsPlugin plugin;

    public Config(ElectronicsPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveDefaultConfig();
        FileConfiguration c = plugin.getConfig();
        c.options().copyDefaults(true);

        renderMode = str(c, "render.mode", renderMode);
        renderScale = c.getDouble("render.scale", renderScale);
        offsetY = c.getDouble("render.offset-y", offsetY);
        autoLight = c.getBoolean("render.block-light", autoLight);
        viewRange = c.getDouble("render.view-range", viewRange);
        textLabels = c.getBoolean("render.text-labels", textLabels);

        baseBlock = material(c.getString("blocks.base"), baseBlock);
        pedestalBlock = material(c.getString("blocks.pedestal"), pedestalBlock);
        requirePedestal = c.getBoolean("blocks.require-pedestal", requirePedestal);

        simContinuous = c.getBoolean("simulation.continuous", simContinuous);
        componentsPerTick = c.getInt("simulation.components-per-tick", componentsPerTick);
        maxIslandNodes = c.getInt("simulation.max-nodes-per-island", maxIslandNodes);
        maxIslandComponents = c.getInt("simulation.max-components-per-island", maxIslandComponents);
        solvePasses = Math.max(1, c.getInt("simulation.solve-passes", solvePasses));
        faultsEnabled = c.getBoolean("simulation.faults", faultsEnabled);
        conductorOhm = c.getDouble("simulation.conductor-ohm", conductorOhm);
        leakOhm = c.getDouble("simulation.leak-ohm", leakOhm);
        generatorPeriodTicks = Math.max(2, c.getInt("simulation.generator-period-ticks", generatorPeriodTicks));
        solarDayVoltage = c.getDouble("simulation.solar-day-voltage", solarDayVoltage);
        solarNightVoltage = c.getDouble("simulation.solar-night-voltage", solarNightVoltage);
        simDtMillis = Math.max(10, c.getInt("simulation.time-step-ms", simDtMillis));
        benchWiring = c.getBoolean("simulation.bench-wiring", benchWiring);
        depositsEnabled = c.getBoolean("deposits.enabled", depositsEnabled);
        depositSeed = c.getLong("deposits.seed", depositSeed);
        depositChance = c.getDouble("deposits.chunk-chance", depositChance);
        depositYMin = c.getInt("deposits.y-min", depositYMin);
        depositYBand = c.getInt("deposits.y-band", depositYBand);
        depositYield = c.getInt("deposits.yield-per-block", depositYield);
        depositVeinSize = c.getInt("deposits.vein-size", depositVeinSize);
        depositVeinJitter = c.getInt("deposits.vein-jitter", depositVeinJitter);

        protectBlocks = c.getBoolean("world.protect-blocks", protectBlocks);
        dropAsItem = c.getBoolean("world.drop-as-item", dropAsItem);
        soundsEnabled = c.getBoolean("world.sounds", soundsEnabled);
        hintsEnabled = c.getBoolean("world.hints", hintsEnabled);
        recipesEnabled = c.getBoolean("world.recipes", recipesEnabled);
        creativeGiveAll = c.getBoolean("world.give-in-creative", creativeGiveAll);

        kineticsEnabled = c.getBoolean("kinetics.enabled", kineticsEnabled);
        kineticsBreak = c.getBoolean("kinetics.break-blocks", kineticsBreak);
        kineticsMove = c.getBoolean("kinetics.carriage-move", kineticsMove);
        kineticsBlocksPerMinute = Math.max(1, c.getInt("kinetics.blocks-per-minute",
                kineticsBlocksPerMinute));

        resourcePackAuto = c.getBoolean("resource-pack.auto-apply", resourcePackAuto);
        resourcePackUrl = c.getString("resource-pack.url", resourcePackUrl);
        resourcePackHash = c.getString("resource-pack.sha1", resourcePackHash);
        debug = c.getBoolean("debug", debug);
        selftestOnEnable = c.getBoolean("selftest", selftestOnEnable);
    }

    public void save() {
        FileConfiguration c = plugin.getConfig();
        c.set("render.mode", renderMode);
        c.set("render.scale", renderScale);
        c.set("render.offset-y", offsetY);
        c.set("render.view-range", viewRange);
        c.set("render.text-labels", textLabels);
        c.set("blocks.base", baseBlock.name());
        c.set("blocks.pedestal", pedestalBlock.name());
        c.set("blocks.require-pedestal", requirePedestal);
        c.set("simulation.faults", faultsEnabled);
        c.set("simulation.bench-wiring", benchWiring);
        c.set("deposits.enabled", depositsEnabled);
        c.set("deposits.seed", depositSeed);
        c.set("deposits.chunk-chance", depositChance);
        c.set("deposits.y-min", depositYMin);
        c.set("deposits.y-band", depositYBand);
        c.set("deposits.vein-size", depositVeinSize);
        c.set("deposits.vein-jitter", depositVeinJitter);
        c.set("deposits.yield-per-block", depositYield);
        c.set("simulation.generator-period-ticks", generatorPeriodTicks);
        c.set("kinetics.enabled", kineticsEnabled);
        c.set("kinetics.break-blocks", kineticsBreak);
        c.set("kinetics.carriage-move", kineticsMove);
        c.set("kinetics.blocks-per-minute", kineticsBlocksPerMinute);
        c.set("world.hints", hintsEnabled);
        c.set("world.recipes", recipesEnabled);
        c.set("resource-pack.url", resourcePackUrl);
        c.set("resource-pack.sha1", resourcePackHash);
        plugin.saveConfig();
    }

    /** Время шага симуляции в секундах (один тик = 50 мс по умолчанию). */
    public double dt() {
        return simDtMillis / 1000.0;
    }

    public double gConductor() {
        return 1.0 / Math.max(1e-9, conductorOhm);
    }

    public double gLeak() {
        return 1.0 / Math.max(1.0, leakOhm);
    }

    private static Material material(String name, Material def) {
        if (name == null || name.isEmpty()) {
            return def;
        }
        Material m = Material.matchMaterial(name);
        return m == null ? def : m;
    }

    private static String str(FileConfiguration c, String path, String def) {
        String v = c.getString(path, def);
        return v == null ? def : v;
    }
}
