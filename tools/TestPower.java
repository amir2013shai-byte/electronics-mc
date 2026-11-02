import me.arena.electronics.circuit.Battery;
import me.arena.electronics.circuit.ChargeCtrl;
import me.arena.electronics.circuit.EnergyMeter;
import me.arena.electronics.circuit.Solar;
import me.arena.electronics.registry.Def;

/**
 * Проверка «энергетической» тройки: солнце, реле-регулятор, счётчик.
 *
 * <p>Здесь нет ни мира, ни решателя — только формулы, потому что именно формулы
 * и есть обещание детали: «панель 18 Вт», «отсечка на 14.4 В», «1600 импульсов
 * на кВт·ч». Если кто-то поправит одну из них, не тронув остальные, тест поймает
 * это на месте: например, что при четверти солнца ЭДС падает сильнее, чем должна,
 * или что счётчик наматает энергию из собственного потребления.</p>
 */
public final class TestPower {

    private static int fails = 0;
    private static int checks = 0;

    private static void check(String what, boolean ok, String detail) {
        checks++;
        System.out.println((ok ? "  OK    " : "  ПОЗОР ") + what + " — " + detail);
        if (!ok) {
            fails++;
        }
    }

    private static boolean near(double got, double want, double tol) {
        return Math.abs(got - want) <= tol;
    }

    private static String f2(double v) {
        return String.format(java.util.Locale.ROOT, "%.3f", v);
    }

    /** Панель 21.6 В / 1 А, реле-регулятор на 10 А, счётчик на 10 А. */
    private static Def panel() {
        Def d = new Def();
        d.voltage = 21.6;
        d.maxCurrent = 1.0;
        d.rInt = 6.0;
        d.resistance = 30.0;
        return d;
    }

    private static Def ctrl() {
        Def d = new Def();
        d.cutOffVoltage = 14.4;
        d.rechargeVoltage = 13.8;
        d.vDrop = 0.3;
        d.internalResistance = 0.4;
        d.maxCurrent = 10.0;
        d.vReverseMax = 40.0;
        return d;
    }

    public static void main(String[] args) {
        // --- солнце ---------------------------------------------------------
        check("полдень ясно = паспорт", near(Solar.irradiance(6000, false, 1.0), 1.0, 1e-9),
                "0.88 прямого + 0.12 рассеянного = " + f2(Solar.irradiance(6000, false, 1.0)));
        check("ночь = ноль", Solar.irradiance(18000, false, 1.0) == 0.0,
                "тик 18000: солнца нет, панель не врёт «ночное излучение»");
        check("восход = одно небо", near(Solar.irradiance(0, false, 1.0), Solar.DIFFUSE, 1e-9),
                "солнце на горизонте: " + f2(Solar.DIFFUSE) + " от паспорта");
        double dusk = Solar.sunElevation(11999) * Solar.DIRECT_SHARE + Solar.DIFFUSE;
        check("сумерки кончаются на закате", near(Solar.irradiance(11999, false, 1.0), dusk, 1e-12)
                        && Solar.irradiance(12000, false, 1.0) == 0.0,
                "тик перед закатом — " + f2(Solar.DIFFUSE) + ", на закате — 0: шаг есть и в игре");
        check("полдень в дождь", near(Solar.irradiance(6000, true, 1.0), Solar.STORM_FACTOR, 1e-9),
                "×0.30 облачностью: " + f2(Solar.irradiance(6000, true, 1.0)));
        check("крыша глушит полностью", Solar.irradiance(6000, false, 0.0) == 0.0,
                "getLightFromSky = 0 над панелью — потока нет");
        check("поток пропорционален небу", near(Solar.irradiance(6000, false, 13 / 15.0),
                13 / 15.0, 1e-9), "листва снимает 2 единицы — и поток падает на те же 13 %");
        check("высота солнца = синус", near(Solar.sunElevation(3000), Math.sqrt(0.5), 1e-9),
                "9:00 (тик 3000) → 0.707");
        check("9:00 и 15:00 симметричны", near(Solar.sunElevation(3000), Solar.sunElevation(9000), 1e-12),
                "утро и вечер дают одинаковый поток");

        // --- вольтаж фотоэлемента -------------------------------------------
        double u1 = Solar.openCircuit(21.6, 1.0);
        check("на полном солнце — паспорт", near(u1, 21.6, 1e-9), f2(u1) + " В");
        double uq = Solar.openCircuit(21.6, 0.25);
        check("Uxx растёт логарифмом, а не линейно", uq > 0.30 * 21.6 && uq < 0.45 * 21.6,
                "четверть солнца → " + f2(uq) + " В (" + Math.round(uq / 21.6 * 100)
                        + "% номинала), а не 5.4 В");
        check("в темноте ЭДС ровно ноль", Solar.openCircuit(21.6, 0.0) == 0.0, "Uxx = 0 В");
        check("ток короткого линеен по потоку", near(Solar.shortCircuit(1.0, 0.5), 0.5, 1e-12),
                "0.5 солнца → 0.5 А: Isc ∝ поток, без оговорок");
        check("поток не бывает больше паспорта", near(Solar.shortCircuit(1.0, 1.5), 1.0, 1e-12),
                "f=1.5 обрезан до 1.0: зеркал в игре нет");
        check("утечка тёмной панели задана реестром", Solar.darkResistance(30.0) == 30.0
                        && Solar.darkResistance(0.0) >= 1e5,
                "30 Ом если указано, мегаомы если нет — «нет утечки» не значит «обрыв»");

        // --- что над панелью (геометрия тени, чистая функция) ----------------
        check("стекло свет не режет", Solar.attenuation("GLASS") > 0.9,
                f2(Solar.attenuation("GLASS")) + " против листвы " + f2(Solar.attenuation("OAK_LEAVES")));
        check("листва глушит сильнее стекла", Solar.attenuation("OAK_LEAVES") < Solar.attenuation("GLASS")
                        && Solar.attenuation("WATER") < Solar.attenuation("OAK_LEAVES"),
                "0.95 / 0.75 / 0.55 — порядок ванильного пропускания света");
        check("воздух не мешает вовсе", Solar.attenuation("AIR") == 1.0 && Solar.attenuation("") == 1.0,
                "1.0 — под открытым небом поток паспортный");

        // --- рабочая точка панели --------------------------------------------
        // Проверка не переписывает физику, а зовёт ту же Solar.terminal, которую
        // зовёт Simulation.stampSolar, и сходится к узлу вручную: 30 Ом утечки
        // параллельно r. Старый «или-или» давал на холостом ходу 72 В при Uxx 21.6.
        double ru = 0.05, g = 1.0 / ru, gLeak = 1.0 / 30.0;
        double uNoon = Solar.openCircuit(21.6, 1.0), iNoon = Solar.shortCircuit(2.4, 1.0);
        double u = 0.0;
        for (int k = 0; k < 200; k++) {
            u = (g * u + Solar.terminal(uNoon, iNoon, ru, u)) / (g + gLeak);
        }
        check("холостой ход не выше Uxx + 1 %", u <= uNoon * 1.01 + 1e-9 && u > uNoon * 0.9,
                f2(u) + " В при Uxx " + f2(uNoon) + " В (утечка 30 Ом), раньше было 72.0 В");
        check("накоротке — ровно Isc", near(Solar.terminal(uNoon, iNoon, ru, 0.0), iNoon, 1e-12)
                        && Solar.terminal(uNoon, iNoon, ru, uNoon) == 0.0,
                f2(iNoon) + " А при u = 0, 0.000 А при u = Uxx — потолок и ноль на месте");
        double uBank = 12.65;
        check("банк 12.65 В берёт с панели не больше паспорта",
                Solar.terminal(uNoon, iNoon, ru, uBank) <= iNoon + 1e-12,
                f2(Solar.terminal(uNoon, iNoon, ru, uBank)) + " А — линия Тевенина упёрлась в Isc");
        check("ночью терминаль не выдумывает ток",
                Solar.terminal(Solar.openCircuit(21.6, 0.0), Solar.shortCircuit(2.4, 0.0), ru, 12.0) == 0.0,
                "Uxx(0) = 0 В → 0 А: утечка банки разряжается сама, панель не мешает");

        // --- реле-регулятор --------------------------------------------------
        Def d = ctrl();
        double[] aux = new double[4];
        check("ночью ключ разомкнут", !ChargeCtrl.closed(aux, d, 0.0, 13.0),
                "панель 0 В против банка 13 В — обратного тока нет");
        check("день, банк пуст — ключ замкнут", ChargeCtrl.closed(aux, d, 21.0, 12.5),
                "21 В против 12.5 В: толкать есть что");
        check("слабее банка — не замыкает", !ChargeCtrl.closed(aux, d, 12.9, 13.0),
                "Uпанели − Uбанка = −0.1 В < порога 0.3 В");
        double[] lz = new double[4];
        lz[ChargeCtrl.CLOSED] = 1.0;
        check("замкнутый ключ держит заряд на лезвии порога",
                ChargeCtrl.closed(lz, d, 13.30, 12.65) && lz[ChargeCtrl.CLOSED] > 0.5,
                "uPv − uBat = 0.65 В ≥ 0: при замкнутом ключе падение равно своему же порогу, требовать «> vf» — значит заставить ключ мигать");
        lz[ChargeCtrl.CLOSED] = 1.0;
        check("но банк сильнее — ключ бросает заряд", !ChargeCtrl.closed(lz, d, 12.48, 12.65),
                "uPv − uBat = −0.17 В: пошёл обратный ток, идеальный диод закрывается");
        double[] edge = new double[4];
        edge[ChargeCtrl.CLOSED] = 1.0;
        edge[ChargeCtrl.PREV_CLOSED] = 1.0;     // ключ был замкнут весь прошлый такт
        ChargeCtrl.tick(edge, d, false, 14.45, 0.0, 0.05);
        double cuts1 = edge[ChargeCtrl.CUTS];
        ChargeCtrl.tick(edge, d, false, 14.45, 0.0, 0.05);
        check("отсечка считается один раз за переход", cuts1 == 1.0 && edge[ChargeCtrl.CUTS] == 1.0,
                "было замкнуто → стало разомкнуто на 14.45 В: +1; второй такт на тех же клеммах — всё ещё 1");
        edge[ChargeCtrl.CLOSED] = 1.0;
        edge[ChargeCtrl.PREV_CLOSED] = 1.0;
        ChargeCtrl.tick(edge, d, true, 13.0, 2.0, 0.05);
        check("ампер-часы растут только при замкнутом ключе",
                near(edge[ChargeCtrl.AMPERE_HOURS], 2.0 * 0.05 / 3600.0, 1e-12)
                        && edge[ChargeCtrl.CUTS] == 1.0,
                "2 А за 50 мс = 0.0000278 А·ч, отсечка не изменилась");
        double[] cold = new double[4];
        cold[ChargeCtrl.CLOSED] = 1.0;
        cold[ChargeCtrl.PREV_CLOSED] = 1.0;
        ChargeCtrl.tick(cold, d, false, 13.9, 0.0, 0.05);
        check("ключ упал раньше порога — отсечки нет", cold[ChargeCtrl.CUTS] == 0.0,
                "13.9 В < 14.35 В: ключ разомкнуло не «по полному банку» (например, панель ушла в тень)");
        aux[ChargeCtrl.CLOSED] = 1.0;
        check("отсечка на 14.4 В", !ChargeCtrl.closed(aux, d, 21.0, 14.45),
                "клеммы 14.45 В: заряд снят, банк не газируется");
        check("гистерезис: 14.2 В — всё ещё снят", !ChargeCtrl.closed(aux, d, 21.0, 14.2),
                "возврата на 14.2 нет, иначе ключ щёлкал бы каждый такт");
        aux[ChargeCtrl.CLOSED] = 0.0;
        check("возврат ниже 13.8 В", ChargeCtrl.closed(aux, d, 21.0, 13.7),
                "просело до 13.7 — заряд снова идёт");
        double[] na = new double[4];
        na[ChargeCtrl.CLOSED] = 1.0;
        check("без решённых узлов ключ не переключается", ChargeCtrl.closed(na, d, Double.NaN, Double.NaN)
                && na[ChargeCtrl.CLOSED] > 0.5, "prev пуст → держим прошлое состояние, не мигаем");
        check("пороги по умолчанию = ванильное газование",
                near(ChargeCtrl.cutOff(new Def()), Battery.V_GAS, 1e-12)
                        && near(ChargeCtrl.recharge(new Def()), Battery.V_GAS - 0.6, 1e-12),
                "в реестре пусто → 14.40 В и 13.80 В, тот же V_GAS, что у банки");

        // --- счётчик энергии -------------------------------------------------
        double[] m = new double[4];
        boolean blink = false;
        double wattSeconds = 0;
        for (int k = 0; k < 72000; k++) {               // 1 час по 50 мс
            blink |= EnergyMeter.step(m, 230.0, 4.35, 0.05);
            wattSeconds += 230.0 * 4.35 * 0.05;
        }
        double wantWh = wattSeconds / 3600.0;
        check("Вт·ч = ∫U·I·dt", near(m[EnergyMeter.WATT_HOURS], wantWh, 1e-6),
                f2(m[EnergyMeter.WATT_HOURS]) + " Вт·ч против " + f2(wantWh)
                        + " из суммы " + (long) wattSeconds + " Дж");
        double[] k1 = new double[4];
        EnergyMeter.step(k1, 1000.0, 1.0, 3600.0);
        check("за час на 1 кВт — 1000 Вт·ч", near(k1[EnergyMeter.WATT_HOURS], 1000.0, 1e-9),
                f2(k1[EnergyMeter.WATT_HOURS]) + " Вт·ч за шаг dt = 3600 с");
        double[] mm = new double[4];
        EnergyMeter.step(mm, 1000.0, 1.0, 3600.0);
        check("1600 импульсов на кВт·ч", mm[EnergyMeter.IMPULSES] == EnergyMeter.IMP_PER_KWH,
                (int) mm[EnergyMeter.IMPULSES] + " импульсов за 1000 Вт·ч, "
                        + f2(EnergyMeter.WH_PER_IMP) + " Вт·ч на импульс");
        double[] zero = new double[4];
        check("на нуле не капает", !EnergyMeter.step(zero, 0.0, 0.0, 1.0)
                        && zero[EnergyMeter.WATT_HOURS] == 0.0, "нет нагрузки — нет показаний");
        double[] rev = new double[4];
        EnergyMeter.step(rev, 230.0, -4.35, 0.05);
        check("отдача в сеть тоже пишется", rev[EnergyMeter.WATT_HOURS] > 0,
                "учёт модулем: двусторонний счётчик в игре был бы тёмным лесом");
        double[] rs = new double[4];
        EnergyMeter.step(rs, 230.0, 4.35, 3600.0);
        EnergyMeter.resetTotal(rs);
        check("сброс обнуляет Вт·ч, но не импульсы", rs[EnergyMeter.WATT_HOURS] == 0.0
                        && rs[EnergyMeter.IMPULSES] > 0,
                f2(rs[EnergyMeter.WATT_HOURS]) + " Вт·ч и " + (int) rs[EnergyMeter.IMPULSES]
                        + " имп. — пассив прибора остаётся");
        check("частота импульсов из паспорта", near(EnergyMeter.impulsesPerHour(500.0), 800.0, 1e-9),
                "500 Вт → " + (int) EnergyMeter.impulsesPerHour(500.0) + " имп/ч");
        double[] disk = new double[4];
        for (int k = 0; k < 200; k++) {
            EnergyMeter.step(disk, 230.0, 4.35, 0.05);
        }
        check("угол диска остаётся в 0..360", disk[EnergyMeter.DISK] >= 0 && disk[EnergyMeter.DISK] < 360.0,
                f2(disk[EnergyMeter.DISK]) + "° после 10 с на 1 кВт — 26.7 об/мин");
        check("мощность 1 кВт: 26.7 об/мин", near(1000.0 / 1000.0 * EnergyMeter.IMP_PER_KWH / 60.0,
                        26.6667, 1e-3), "1600 имп/ч / 60 = оборотов в минуту");
        check("подпись читается", EnergyMeter.label(new double[] {1234.5, 0, 0, 0}, 230.0)
                .contains("кВт·ч"), EnergyMeter.label(new double[] {1234.5, 0, 0, 0}, 230.0));

        // --- связка: панель + реле + банк ------------------------------------
        double[] pa = new double[4];
        double[] ca = new double[4];
        double f = Solar.irradiance(6000, false, 1.0);
        double uoc = Solar.openCircuit(panel().voltage, f);
        double isc = Solar.shortCircuit(panel().maxCurrent, f);
        boolean closed = ChargeCtrl.closed(ca, d, uoc, 12.6);
        check("полуденная панель открыта для банка 12.6 В", closed,
                f2(uoc) + " В против 12.6 В, поток " + f2(f) + ", потолок тока " + f2(isc) + " А");
        double pMax = 12.6 * isc;
        check("панель не обещает больше Uxx × Isc", pMax <= panel().voltage * panel().maxCurrent + 1e-9,
                f2(pMax) + " Вт в банк против " + f2(panel().voltage * panel().maxCurrent)
                        + " Вт паспорта");
        double night = Solar.irradiance(18000, false, 1.0);
        check("ночью ключ разомкнут", !ChargeCtrl.closed(new double[4], d,
                        Solar.openCircuit(panel().voltage, night), 12.6),
                "Uxx(0) = 0 В против банка 12.6 В");
        double drain = 12.6 / Solar.darkResistance(panel().resistance);
        check("без ключа банк терял бы ёмкость за сутки", drain * 24 >= 10.0,
                f2(drain) + " А через 30 Ом утечки = " + f2(drain * 24)
                        + " А·ч за сутки: поэтому между панелью и банком стоит реле");

        System.out.println(fails == 0 ? "ALL OK (" + checks + " проверок)"
                : "TestPower: ПРОВАЛ (" + fails + " из " + checks + ")");
        if (fails != 0) {
            System.exit(1);
        }
    }
}
