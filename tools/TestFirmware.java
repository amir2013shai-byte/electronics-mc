import me.arena.electronics.fw.Firmware;
import me.arena.electronics.fw.Firmware.Board;
import me.arena.electronics.fw.Firmware.Program;

/**
 * Офлайн-проверки интерпретатора прошивки: компиляция, номера строк ошибок,
 * развилка if/else (главная ловушка — переходы), квота while, delay в тиках
 * и АЦП. Настоящая плата — Simulation; здесь подставная, чтобы видеть числа.
 */
public class TestFirmware {

    private static int fails;

    static void check(String what, boolean ok, String detail) {
        System.out.println((ok ? "  ok   " : "  FAIL ") + what + "  —  " + detail);
        if (!ok) {
            fails++;
        }
    }

    /** Плата из трёх массивов: направление, уровень, ШИМ. Напряжения заданы вручную. */
    static final class Fake implements Board {
        final int[] dir = new int[6];
        final int[] lvl = new int[6];
        final int[] pwm = new int[6];
        final double[] u = new double[6];
        double vcc = 5.0;

        public double vcc() {
            return vcc;
        }

        public int dir(int p) {
            return dir[p];
        }

        public void dir(int p, int m) {
            dir[p] = m;
        }

        public int level(int p) {
            return lvl[p];
        }

        public void level(int p, int v) {
            lvl[p] = v;
        }

        public int pwm(int p) {
            return pwm[p];
        }

        public void pwm(int p, int v) {
            pwm[p] = v;
        }

        public double voltage(int p) {
            return u[p];
        }
    }

    /** Компиляция + несколько тиков (delay и «прогрев» ног не мешают коротким программам). */
    static Fake run(String src, Fake board) {
        Program p = Firmware.compile(src, 6);
        for (int i = 0; i < 4 && p.ok(); i++) {
            p.tick(board);
        }
        return board;
    }

    static String first(String src, Fake board) {
        Program p = Firmware.compile(src, 6);
        if (p.ok()) {
            p.tick(board);
        }
        return p.errorMessage() == null ? "" : p.errorMessage();
    }

    public static void main(String[] args) {
        // 1. цифровой выход
        Fake b = new Fake();
        run("pin 2 output;\ndigitalWrite(2, HIGH);\n", b);
        check("цифровой выход поднят", b.dir[2] == 1 && b.lvl[2] == 1,
                "dir=" + b.dir[2] + " lvl=" + b.lvl[2]);

        // 2. ШИМ
        b = new Fake();
        run("pin 5 pwm;\nanalogWrite(5, 128);\n", b);
        check("ШИМ 128/255", b.dir[5] == 2 && b.pwm[5] == 128,
                "dir=" + b.dir[5] + " duty=" + b.pwm[5] + " ("
                        + Math.round(100.0 * b.pwm[5] / 255) + "% питания)");

        // 3. развилка: без правильного перехода ветка else не читалась бы никогда
        String branch = "pin 3 input;\npin 2 output;\n"
                + "if (digitalRead(3) == HIGH) { digitalWrite(2, HIGH); }"
                + " else { digitalWrite(2, LOW); }\n";
        b = new Fake();
        b.u[3] = 0.4;
        run(branch, b);
        check("низкий вход увёл в else", b.lvl[2] == 0, "lvl[2]=" + b.lvl[2] + " при 0.4 В");
        b = new Fake();
        b.lvl[2] = 1;                      // нога уже поднята — if должен её удержать
        b.u[3] = 3.3;
        run(branch, b);
        check("высокий вход прошёл по then", b.lvl[2] == 1, "lvl[2]=" + b.lvl[2] + " при 3.3 В");

        // 4. аналоговое чтение против АЦП: 3.0 В из 5 В = 614, порог 512
        b = new Fake();
        b.u[4] = 3.0;
        run("pin 4 input;\npin 5 output;\n"
                + "if (analogRead(4) > 512) { digitalWrite(5, HIGH); }"
                + " else { digitalWrite(5, LOW); }\n", b);
        check("АЦП 614 > 512", b.lvl[5] == 1 && Firmware.adc(3.0, 5.0) == 614,
                "adc(3.0 В) = " + Firmware.adc(3.0, 5.0) + ", lvl[5]=" + b.lvl[5]);
        b = new Fake();
        b.u[4] = 2.0;
        run("pin 4 input;\npin 5 output;\n"
                + "if (analogRead(4) > 512) { digitalWrite(5, HIGH); }\n", b);
        check("АЦП 410 < 512 — ветка молчит", b.lvl[5] == 0 && Firmware.adc(2.0, 5.0) == 409,
                "adc(2.0 В) = " + Firmware.adc(2.0, 5.0) + " (round 0.4×1023)");

        // 5. if без скобок (одна команда)
        b = new Fake();
        b.u[3] = 3.3;
        run("pin 3 input;\npin 2 output;\n"
                + "if (digitalRead(3) == HIGH) digitalWrite(2, LOW);\n", b);
        check("if без { тоже работает", b.lvl[2] == 0, "lvl[2]=" + b.lvl[2]);

        // 6. delay: 50 мс = ровно один тик симуляции, вперёд не бежит
        b = new Fake();
        Program p = Firmware.compile("pin 2 output;\ndelay(50);\ndigitalWrite(2, HIGH);\n", 6);
        boolean finished = p.tick(b);
        check("delay(50) = 1 тик", !finished && p.delayTicks() == 1 && b.lvl[2] == 0,
                "осталось " + p.delayTicks() + " тик(ов), lvl[2]=" + b.lvl[2]);
        for (int i = 0; i < 3; i++) {
            p.tick(b);
        }
        check("после задержки нога поднялась", b.lvl[2] == 1 && p.millis() >= 150,
                "lvl[2]=" + b.lvl[2] + ", счётчик " + p.millis() + " мс");

        // 7. while: квота на тик — сервер не виснет
        b = new Fake();
        b.u[3] = 5.0;
        p = Firmware.compile("pin 3 input;\npin 2 output;\n"
                + "while (digitalRead(3) == HIGH) { digitalWrite(2, HIGH); }\n", 6);
        long t0 = System.nanoTime();
        p.tick(b);
        long ms = (System.nanoTime() - t0) / 1000000;
        check("вечный while не подвешивает", ms < 200 && b.lvl[2] == 1,
                "тик за " + ms + " мс, квота " + Firmware.WHILE_LIMIT + " повторов");

        // 8. ошибки компиляции: текст и номер строки важны игроку
        b = new Fake();
        String err = first("pin 2 output;\nfart(2, HIGH);\n", b);
        Program bad = Firmware.compile("pin 2 output;\nfart(2, HIGH);\n", 6);
        check("мусор ловится со строкой 2", !bad.ok() && bad.errorLine() == 2
                        && bad.errorMessage().contains("не понимаю"),
                "«" + err + "»");
        bad = Firmware.compile("pin 7 output;\n", 6);
        check("нога вне корпуса ловится", !bad.ok() && bad.errorLine() == 1,
                "«" + bad.errorMessage() + "»");
        bad = Firmware.compile("if (digitalRead(3) == HIGH) {\ndigitalWrite(2, HIGH);\n", 6);
        check("{ без } ловится", !bad.ok() && bad.errorMessage().contains("не закрыта"),
                "«" + bad.errorMessage() + "»");
        bad = Firmware.compile("pin 2 output;\n}\n", 6);
        check("лишняя } ловится", !bad.ok() && bad.errorMessage().contains("лишняя"),
                "«" + bad.errorMessage() + "»");
        bad = Firmware.compile("pin 2 output;\ndigitalWrite(2, MEDIUM);\n", 6);
        check("уровень только HIGH/LOW", !bad.ok() && bad.errorMessage().contains("HIGH"),
                "«" + bad.errorMessage() + "»");
        bad = Firmware.compile("pin 2 output;\nanalogWrite(2, 300);\n", 6);
        check("ШИМ в пределах 0..255", !bad.ok() && bad.errorMessage().contains("255"),
                "«" + bad.errorMessage() + "»");

        // 9. пустая и закомментированная прошивка — не ошибка
        bad = Firmware.compile("// просто комментарий\n\n", 6);
        check("комментарий — не программа", bad.ok() && bad.statements() == 0,
                "операторов: " + bad.statements());
        check("число операторов", Firmware.count("pin 2 output;\ndigitalWrite(2, HIGH);\ndelay(20);\n") == 3,
                "3 оператора в трёх строках");
        check("подсветка знает синтаксис", Firmware.keywords().contains("analogWrite")
                        && Firmware.keywords().contains("digitalRead"),
                "ключевых слов: " + Firmware.keywords().size());
        check("АЦП на краях", Firmware.adc(5.0, 5.0) == 1023 && Firmware.adc(0.0, 5.0) == 0
                        && Firmware.adc(1.0, 0.0) == 0,
                "1023 / 0 / 0 при мертвом VCC");
        check("hex-литерал", Firmware.compile("pin 2 output;\nanalogWrite(2, 0xFF);\n", 6).ok(),
                "0xFF = 255 принят");

        System.out.println();
        System.out.println(fails == 0 ? "TestFirmware: ALL OK" : "TestFirmware: провалено " + fails);
        if (fails != 0) {
            System.exit(1);
        }
    }
}
