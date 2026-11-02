package me.arena.electronics.circuit;

import java.util.Locale;

/**
 * Счётчик электроэнергии: прибор, который копит, а не показывает.
 *
 * <ol>
 *   <li><b>Катушка тока — в разрыв, датчик напряжения — на нейтраль.</b> Из
 *       паспорта: 0.01 Ом последовательно — это цена токовой катушки, и 1 МОм
 *       между «нагрузка» и «ноль» — цена цепи напряжения. Пока четвёртый вывод
 *       не подключён, счётчик честно мотает 0 Вт·ч и говорит об этом.</li>
 *   <li><b>Наматывание = U·I·dt.</b> Никакой «средней мощности за 10 секунд»:
 *       интегратор считает на каждом шаге решателя, поэтому импульсы счётчика
 *       совпадают с энергией, а не с показаниями вольтметра.</li>
 *   <li><b>1600 импульсов на кВт·ч</b> — городской класс точности 2; значит один
 *       импульс = {@value #WH_PER_IMP} Вт·ч, и лампа на 10 Вт даст первый
 *       импульс через 2.25 минуты. Раньше — ноль, и это правильно.</li>
 *   <li><b>Обнуление не стирает ресурс.</b> Правая кнопка обнуляет намотанные
 *       Вт·ч, но не счётчик импульсов: по нему сверяют поверку прибора — как на
 *       настоящей «моталке».</li>
 * </ol>
 */
public final class EnergyMeter {

    /** aux[0]: намотанные Вт·ч (обнуляются правой кнопкой). */
    public static final int WATT_HOURS = 0;
    /** aux[1]: импульсов за всё время — не обнуляется никогда. */
    public static final int IMPULSES = 1;
    /** aux[2]: Вт·ч с прошлого импульса (недозврат для следующего). */
    public static final int SINCE_BLINK = 2;
    /** aux[3]: угол диска, градусы — для вертящегося барабана на модели. */
    public static final int DISK = 3;

    /** Импульсов на кВт·ч: паспорт городского индукционного счётчика. */
    public static final double IMP_PER_KWH = 1600.0;
    /** Вт·ч на один импульс = 1000/1600. */
    public static final double WH_PER_IMP = 1000.0 / IMP_PER_KWH;
    /** А, после которых катушка тока сгорает (2.5 × паспортного — в Simulation). */
    public static final double I_BURNT = 25.0;

    private EnergyMeter() {
    }

    /**
     * Один шаг решателя.
     *
     * @param uLoad напряжение между «нагрузкой» и «нулём», В
     * @param iLoad ток через катушку, А
     * @return true, если на этом шаге счётчик выдал импульс (моргнуть светодиодом)
     */
    public static boolean step(double[] aux, double uLoad, double iLoad, double dt) {
        double p = Math.abs(uLoad) * Math.abs(iLoad);
        double wh = p * dt / 3600.0;
        aux[WATT_HOURS] += wh;
        aux[SINCE_BLINK] += wh;
        boolean blink = false;
        while (aux[SINCE_BLINK] >= WH_PER_IMP) {
            aux[SINCE_BLINK] -= WH_PER_IMP;
            aux[IMPULSES] += 1.0;
            blink = true;
        }
        // барабан крутится пропорционально мощности: 360°/ч при 1 кВт
        double impPerSec = p / 1000.0 * IMP_PER_KWH / 3600.0;
        aux[DISK] = (aux[DISK] + impPerSec * 360.0 * dt) % 360.0;
        return blink;
    }

    /** Правая кнопка: обнулить намотанное, импульсы остаются. */
    public static void resetTotal(double[] aux) {
        aux[WATT_HOURS] = 0.0;
        aux[SINCE_BLINK] = 0.0;
    }

    /** Сколько импульсов в час даёт данная мощность, Вт. */
    public static double impulsesPerHour(double watts) {
        return watts / 1000.0 * IMP_PER_KWH;
    }

    /** Показание на табло: Вт·ч или кВт·ч (порог переключения — 1 кВт·ч). */
    public static String label(double[] aux, double watts) {
        double wh = aux[WATT_HOURS];
        String e = wh < 1.0 ? String.format(Locale.ROOT, "%.3f Вт·ч", wh)
                : (wh < 1000.0 ? String.format(Locale.ROOT, "%.2f Вт·ч", wh)
                : String.format(Locale.ROOT, "%.3f кВт·ч", wh / 1000.0));
        return e + " · " + String.format(Locale.ROOT, "%.0f имп/ч", impulsesPerHour(watts));
    }
}
