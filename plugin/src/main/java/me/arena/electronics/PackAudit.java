package me.arena.electronics;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Аудит ресурспака, который лежит рядом с плагином (его качают клиенты).
 *
 * Нужно ровно два факта. Первый: сколько в паке определений модели — если их
 * нет, клиенту нечем рисовать 702 состояния, и все детали в мире выглядят как
 * голый блок-основа. Второй: какой блок пак перекрашивает в «плату» — этот
 * переключатель лежит в assets/minecraft/models/block/ и обязан совпасть с
 * blocks.base. Разъезжаются они молча: админ ставит base: OAK_SLAB, а пак
 * напечатан под кварц, и вместо плат получаем «все блоки одной текстуры».
 */
public final class PackAudit {

    /** Имя файла — то, что кладёт tools/publish.sh; resourcepack.zip принят как старый вариант. */
    public static final String PACK_NAME = "electronics-resourcepack.zip";

    public final boolean present;
    public final String fileName;
    public final String sha1;
    /** сколько assets/electronics/items/*.json */
    public final int itemDefs;
    /** сколько состояний должен уметь показать пак: деталей × имён состояний */
    public final int defsWanted;
    /** какие блоки перекрашивает (имена без .json из assets/minecraft/models/block/) */
    public final List<String> plateTargets;
    /** blocks.base в нижнем регистре */
    public final String baseBlock;
    public final String error;

    private PackAudit(boolean present, String fileName, String sha1, int itemDefs, int defsWanted,
                      List<String> plateTargets, String baseBlock, String error) {
        this.present = present;
        this.fileName = fileName;
        this.sha1 = sha1;
        this.itemDefs = itemDefs;
        this.defsWanted = defsWanted;
        this.plateTargets = plateTargets;
        this.baseBlock = baseBlock;
        this.error = error;
    }

    /** Пак + конфиг в один взгляд; читать умеет только то, что не может бросить наружу. */
    public static PackAudit of(File dataFolder, String baseBlock) {
        int wanted = me.arena.electronics.registry.Components.size()
                * me.arena.electronics.registry.Components.STATE_NAMES.length;
        File pack = new File(dataFolder, PACK_NAME);
        if (!pack.isFile()) {
            File alt = new File(dataFolder, "resourcepack.zip");
            if (alt.isFile()) {
                pack = alt;
            } else {
                return new PackAudit(false, PACK_NAME, "—", -1, wanted, new ArrayList<>(),
                        baseBlock.toLowerCase(Locale.ROOT), null);
            }
        }
        int defs = 0;
        List<String> plate = new ArrayList<>();
        String sha = "—";
        String err = null;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            try (InputStream in = new FileInputStream(pack)) {
                byte[] buf = new byte[1 << 16];
                for (int n; (n = in.read(buf)) > 0;) {
                    md.update(buf, 0, n);
                }
            }
            StringBuilder hex = new StringBuilder();
            for (byte b : md.digest()) {
                hex.append(String.format("%02x", b));
            }
            sha = hex.toString();
            try (ZipFile zf = new ZipFile(pack)) {
                Enumeration<? extends ZipEntry> en = zf.entries();
                while (en.hasMoreElements()) {
                    String n = en.nextElement().getName();
                    if (n.startsWith("assets/electronics/items/") && n.endsWith(".json")) {
                        defs++;
                    } else if (n.startsWith("assets/minecraft/models/block/") && n.endsWith(".json")) {
                        plate.add(n.substring(n.lastIndexOf('/') + 1, n.length() - 5));
                    }
                }
            }
        } catch (Exception ex) {
            err = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
        }
        return new PackAudit(true, pack.getName(), sha, defs, wanted, plate,
                baseBlock.toLowerCase(Locale.ROOT), err);
    }

    /**
     * «Плата» обязана перекрашивать тот блок, который кладёт плагин. Пустой список
     * — легальный случай: пак собран с ELECTRONICS_PLATE=none, тогда плата и есть
     * ванильный блок.
     */
    public boolean plateMatchesBase() {
        return plateTargets.isEmpty() || plateTargets.contains(baseBlock);
    }

    public boolean defsOk() {
        return itemDefs == defsWanted;
    }

    public boolean ok() {
        return present && error == null && plateMatchesBase() && defsOk();
    }

    /** Одна строка — и в лог при включении, и в /elec selftest. */
    public String line() {
        if (!present) {
            return "пак " + fileName + " не найден в plugins/Electronics/ — клиенты играют ванилью, "
                    + "детали не получат ни моделей, ни текста";
        }
        return "пак " + fileName + ", sha1 " + sha1 + ": определений модели " + itemDefs + "/"
                + defsWanted + ", плата перекрашивает "
                + (plateTargets.isEmpty() ? "ничего (плата = ванильный блок)"
                        : String.join(" + ", plateTargets))
                + ", в конфиге base = " + baseBlock
                + (plateMatchesBase() ? "" : " ← пак не совпадает с blocks.base, пересоберите его"
                        + " (ELECTRONICS_BASE_BLOCK=" + baseBlock.toUpperCase(Locale.ROOT) + ")")
                + (defsOk() ? "" : " ← чужие определения не пересобраны")
                + (error == null ? "" : " (не читается: " + error + ")");
    }
}
