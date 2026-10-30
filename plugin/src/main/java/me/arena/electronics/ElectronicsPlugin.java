package me.arena.electronics;

import me.arena.electronics.circuit.Circuit;
import me.arena.electronics.command.ElecCommand;
import me.arena.electronics.command.LabCommand;
import me.arena.electronics.lab.LabManager;
import me.arena.electronics.recipes.Recipes;
import me.arena.electronics.registry.Components;
import me.arena.electronics.render.Renderer;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

/**
 * Electronics — учебная электроника для Spigot 1.21.11.
 *
 * Блоки («светодиод», «диод», «блок питания», «лампочка», «пустая схема» и др.)
 * не требуют клиентского мода: сервер выдаёт обычный ванильный предмет, у которого
 * меняется только `item_model` (а значит и 3D-модель, и подсветка, и вид в руках) —
 * а ресурспак описывает 800+ таких состояний. Электрика считается узлами и
 * матрицей проводимостей (MNA), поэтому светодиод реально «выбирает» ток,
 * диод пропускает только в одну сторону, а перегруз сжигает компонент.
 */
public final class ElectronicsPlugin extends JavaPlugin {

    private Config config;
    private Circuit circuit;
    private Renderer renderer;
    private Items items;
    private Sounds sounds;
    private LabManager labs;
    private Recipes recipes;
    private me.arena.electronics.lab.BuildLoader builds;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        config = new Config(this);
        config.load();

        Components.load(getResource("components.json"), new File(getDataFolder(), "components"));
        if (Components.size() == 0) {
            getLogger().severe("Не удалось загрузить components.json — плагин отключается.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getLogger().info("Загружено компонентов: " + Components.size()
                + ", состояний модели ресурспака: " + Components.stateModels());

        circuit = new Circuit(this);
        renderer = new Renderer(this);
        items = new Items(this);
        sounds = new Sounds(this);
        labs = new LabManager(this);
        recipes = new Recipes(this);
        deposits = new me.arena.electronics.deposit.Deposits();
        applyDepositConfig();
        deposits.load(new File(getDataFolder(), "deposits.json").toPath());
        builds = new me.arena.electronics.lab.BuildLoader(this);
        builds.installBuiltin();

        labs.loadAll();
        recipes.register();
        persistence().rescanAllWorlds();
        int pending = persistence().pendingCount();
        if (pending > 0) {
            getLogger().info("записей в чанках, которые ещё не загружены: " + pending
                    + " (поднимутся сами при заходе игрока)");
        }
        // Paper грузит чанки асинхронно: раз в 2 секунды подбираем то, что не поймал ChunkLoadEvent
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (persistence().pendingCount() > 0) {
                persistence().loadPending();
            }
        }, 40L, 40L);

        getServer().getPluginManager().registerEvents(new Events(this), this);
        ElecCommand elecCmd = new ElecCommand(this);
        registerCommand("elec", elecCmd);
        registerCommand("lab", new LabCommand(this));
        if (config.selftestOnEnable) {
            // через 3 секунды: чанки уже подгружены, есть чем свериться
            Bukkit.getScheduler().runTaskLater(this,
                    () -> elecCmd.selftest(Bukkit.getConsoleSender()), 60L);
        }

        long period = 1L;
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            try {
                engineTick();
            } catch (RuntimeException ex) {
                if (config.debug) {
                    ex.printStackTrace();
                }
            }
        }, period, period);

        if (config.resourcePackAuto && !config.resourcePackUrl.isEmpty()) {
            byte[] hash = config.resourcePackHash.isEmpty() ? null
                    : java.util.Arrays.stream(config.resourcePackHash.split("(?<=..)"))
                    .mapToInt(ch -> Integer.parseInt(ch, 16)).collect(() -> new java.io.ByteArrayOutputStream(),
                            (b, v) -> b.write((byte) v), (a, b) -> { }).toByteArray();
            byte[] h = hash == null ? null : (hash.length == 20 ? hash : null);
            for (org.bukkit.entity.Player p : Bukkit.getOnlinePlayers()) {
                p.setResourcePack(config.resourcePackUrl, h, true);
            }
        }
    }

    @Override
    public void onDisable() {
        me.arena.electronics.gui.SchematicGui.clearSessions();
        if (renderer != null) {
            renderer.dropAll();
        }
        if (recipes != null) {
            recipes.unregisterAll();
        }
        if (deposits != null) {
            try {
                deposits.save(new File(getDataFolder(), "deposits.json").toPath());
            } catch (java.io.IOException e) {
                getLogger().warning("не удалось сохранить deposits.json: " + e.getMessage());
            }
        }
        saveAll();
    }

    /** Перечитать параметры залежей (после /elec reload или правки config.yml). */
    public void applyDepositConfig() {
        Config c = config();
        long seed = c.depositSeed != 0 ? c.depositSeed : getServer().getWorlds().stream()
                .filter(w -> w != null).map(w -> w.getSeed()).findFirst().orElse(0L);
        deposits.configure(seed, c.depositsEnabled, c.depositChance, c.depositVeinSize,
                c.depositVeinJitter, c.depositYMin, c.depositYBand, c.depositYield);
    }

    public me.arena.electronics.deposit.Deposits deposits() {
        return deposits;
    }

    private void registerCommand(String name, Object executor) {
        PluginCommand cmd = getCommand(name);
        if (cmd == null) {
            getLogger().warning("Команда " + name + " не найдена в plugin.yml");
            return;
        }
        if (executor instanceof org.bukkit.command.CommandExecutor ex) {
            cmd.setExecutor(ex);
            if (ex instanceof org.bukkit.command.TabCompleter tc) {
                cmd.setTabCompleter(tc);
            }
        }
    }

    public Config config() {
        return config;
    }

    private me.arena.electronics.deposit.Deposits deposits;

    public Circuit circuit() {
        return circuit;
    }

    public Renderer renderer() {
        return renderer;
    }

    public Items items() {
        return items;
    }

    public Sounds sounds() {
        return sounds;
    }

    public LabManager labs() {
        return labs;
    }

    public Recipes recipes() {
        return recipes;
    }

    public me.arena.electronics.lab.BuildLoader builds() {
        return builds;
    }

    public me.arena.electronics.circuit.Persistence persistence() {
        return me.arena.electronics.circuit.Persistence.get(this);
    }

    public void reloadAll() {
        config.load();
        Components.reload(getResource("components.json"), new File(getDataFolder(), "components"));
        labs.loadAll();
        applyDepositConfig();
        recipes.register();
        circuit.markDirty();
    }

    public void saveAll() {
        me.arena.electronics.circuit.Persistence.get(this).flush();
    }

    /** Один тик движка (используется командой /elec sim). */
    public void forceSimulate() {
        circuit.markDirty();
        circuit.engineTick();
    }

    /** Порядок на тик: звук -> расчёт цепей -> отрисовка. */
    private void engineTick() {
        sounds.tick();
        circuit.engineTick();
    }

    public int stateModelCount() {
        return Components.stateModels();
    }
}
