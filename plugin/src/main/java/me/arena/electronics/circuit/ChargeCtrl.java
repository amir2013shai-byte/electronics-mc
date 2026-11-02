package me.arena.electronics.circuit;

import java.util.Locale;

import me.arena.electronics.registry.Def;
import me.arena.electronics.util.Num;

/**
 * Реле-регулятор заряда: ключ между «+ панели» и «+ банка» с двумя мозгами.
 *
 * <ol>
 *   <li><b>Идеальный диод.</b> Ключ закрыт, если на входе (панель) нет напряжения:
 *       ночью банк не разряжается обратно через панель, хотя без этой детали он
 *       отдаёт свой ток в утечку панели (см. {@link Solar}). Падение в открытом
 *       состоянии — {@code v_drop} из паспорта, 0.3 В, как у реального
 *       Шоттки-каскада; «сгоревший» ключ — это <em>коротыш</em>, и тогда защита
 *       исчезает, а цепь остаётся: честный отказ, а не «всё пропало».</li>
 *   <li><b>Отсечка заряда с гистерезисом.</b> Пока напряжение на банке (его меряют
 *       между выводом «нагрузка» и общей «−» — третий вывод детали) не дотянуло до
 *       {@code cut_off_v}, ключ открыт. Дотянуло — разомкнулся; вернётся ниже
 *       {@code recharge_v} — снова замкнётся. Порог 14.4 В — это не выдумка: выше
 *       него у свинца начинается электролиз воды ({@link Battery#V_GAS}), и ёмкость
 *       уходит безвозвратно. Гистерезис 0.6 В — чтобы ключ не дёргался на пороге.</li>
 * </ol>
 *
 * <p><b>Что модель честно не умеет.</b> Она не знает SOC банка: у реле нет
 * «подсказки от аккумулятора», есть только измеренное напряжение и ток. Поэтому
 * отсечка приходит ровно тогда, когда клеммы банка реально поднялись к порогу, —
 * на 12.65 В-банке с {@code r_int} 20 мОм этого не происходит (клеммы = ЭДС + I·r
 * ≈ 12.7 В при 2.4 А), и реле честно продолжает заряжать: в такой схеме панель
 * 21.6 В — это перезаряд, и для неё нужен либо банк на 24 В, либо ограничитель
 * по току. Третьего вывода (общей «−») нет в цепи — отсечку мерить нечем: реле
 * об этом говорит в {@code fault}, но ток пропускает и ампер-часы считает.</p>
 */
public final class ChargeCtrl {

    /** aux[0]: замкнут ли ключ сейчас (1/0). */
    public static final int CLOSED = 0;
    /** aux[1]: сколько ампер-часов прошло через ключ с момента установки. */
    public static final int AMPERE_HOURS = 1;
    /** aux[2]: сколько раз ключ снимал заряд (счётчик отсечек). */
    public static final int CUTS = 2;
    /** aux[3]: состояние ключа на прошлом такте — нужно, чтобы считать отсечку один раз. */
    public static final int PREV_CLOSED = 3;

    /** Порог отсечки: паспорт, а 0 в паспорте — порог свинца. */
    public static double cutOff(Def d) {
        return d.cutOffVoltage > 0 ? d.cutOffVoltage : Battery.V_GAS;
    }

    /** Порог возврата в заряд: на 0.6 В ниже отсечки, если не указан. */
    public static double recharge(Def d) {
        return d.rechargeVoltage > 0 ? d.rechargeVoltage : cutOff(d) - 0.6;
    }

    /** Падение на открытом ключе, В. */
    public static double forward(Def d) {
        return d.vDrop > 0 ? d.vDrop : 0.3;
    }

    /**
     * Замыкается ли ключ. Считает и сразу пишет результат в {@code aux[CLOSED]}:
     * у решателя нет «предыдущего прохода», состояние живёт в детали.
     *
     * @param uPv напряжение панели относительно общей «−» (NaN — панели в цепи нет)
     * @param uBat напряжение банка относительно той же «−» (NaN — банка нет)
     * @return true — ток через ключ идёт
     */
    public static boolean closed(double[] aux, Def d, double uPv, double uBat) {
        boolean was = aux[CLOSED] > 0.5;
        if (Double.isNaN(uPv) || Double.isNaN(uBat)) {
            return was;                     // мерить нечего: держим последнее решение
        }
        boolean allow = uPv - uBat > forward(d);          // «идеальный диод» + заряд идёт
        boolean full = was ? uBat >= cutOff(d) : uBat > recharge(d);
        boolean now = allow && !full;
        aux[CLOSED] = now ? 1 : 0;
        return now;
    }

    /** Сопротивление закрытого ключа: 0.4 Ом по умолчанию, не ноль (иначе коротыш). */
    public static double rOn(Def d) {
        return Math.max(0.05, d.internalResistance > 0 ? d.internalResistance : 0.4);
    }

    /** Подпись на детали: состояние, напряжение банка и пропущенные ампер-часы. */
    public static String label(boolean isClosed, double uBat, double i, double[] aux, double cutOff) {
        String state = isClosed
                ? (i > 0.02 ? "заряд" : "ждёт")
                : (uBat >= cutOff - 0.05 ? "полон" : "нет солнца");
        return state + " · " + Num.volt(uBat) + " · "
                + String.format(Locale.ROOT, "%.2f А·ч", aux[AMPERE_HOURS]);
    }
}
