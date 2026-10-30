package me.arena.electronics.fw;

import java.util.ArrayList;
import java.util.List;

/**
 * Прошивка микроконтроллера: крошечный «C++ для Ардуино», который читается
 * человеком и исполняется cooperatively внутри шага симуляции.
 *
 * <p>Язык — подмножество Arduino, ровно то, что нужно для лабораторной работы:
 * <pre>
 *   pin 2 output;                      // направление: input | output | pwm
 *   pin 3 input;
 *   digitalWrite(2, HIGH);             // HIGH=1, LOW=0
 *   analogWrite(2, 128);               // ШИМ 0..255 (сквозь него крутят мотор)
 *   if (digitalRead(3) == HIGH) { digitalWrite(2, LOW); }
 *   while (analogRead(4) &gt; 512) { pulse; }
 *   delay(20);                         // мс; квант симуляции 50 мс
 * </pre>
 *
 * <p>Осознанные ограничения. Тело — плоский список операторов с одним уровнем
 * вложенности блоков (if/else, while). Функций, переменных и арифметики нет:
 * состояние — это уровни на ногах, их и видно в GUI. За один шаг выполняется не
 * более {@link #STEPS_PER_TICK} операторов, а while ограничен сверху — так
 * бесконечный цикл не подвешивает сервер, он просто «жжёт» квоту шага.
 *
 * <p>Класс не знает про Bukkit: его можно запустить офлайн (см.
 * {@code tools/TestFirmware.java}) и прогнать через подставную плату.
 */
public final class Firmware {

    /** Потолок размера прошивки: больше — не читается глазами и не тестится. */
    public static final int MAX_STATEMENTS = 240;
    /** Сколько операторов прошивка успевает за один тик симуляции. */
    public static final int STEPS_PER_TICK = 64;
    /** Сколько раз максимум выполнится тело while за один проход. */
    public static final int WHILE_LIMIT = 64;
    /** Размер АЦП, как у ATmega328. */
    public static final int ADC_MAX = 1023;

    /**
     * Плата, на которую исполняется программа. Реализация в Simulation ходит в
     * решатель; тесты подставляют массивик.
     */
    public interface Board {
        /** Напряжение питания на VCC (0 = питание пропало, ноги становятся hi-Z). */
        double vcc();

        /** 0 = вход (hi-Z), 1 = выход, 2 = выход с ШИМ. */
        int dir(int pin);

        void dir(int pin, int mode);

        int level(int pin);

        void level(int pin, int high);

        int pwm(int pin);

        void pwm(int pin, int duty);

        /** Напряжение на ноге относительно GND (для аналогого входа). */
        double voltage(int pin);
    }

    /** Ошибка компиляции: номер строки и текст — их показывает GUI. */
    public static final class Error extends RuntimeException {
        private final int line;
        private final String text;

        public Error(int line, String text) {
            super("строка " + line + ": " + text);
            this.line = line;
            this.text = text;
        }

        public int line() {
            return line;
        }

        public String text() {
            return text;
        }

        @Override
        public String toString() {
            return "строка " + line + ": " + text;
        }
    }

    private static final int OP_PIN = 0;
    private static final int OP_WRITE = 1;
    private static final int OP_PWM = 2;
    private static final int OP_DELAY = 3;
    private static final int OP_IF = 4;
    private static final int OP_WHILE = 5;
    private static final int OP_END = 6;
    private static final int OP_ELSE = 7;

    /** Один оператор. Для if/while end — индекс оператора за блоком. */
    private static final class St {
        final int op;
        final int a;
        final int b;
        final int cmp;
        final int end;
        final int line;

        St(int op, int a, int b, int cmp, int end, int line) {
            this.op = op;
            this.a = a;
            this.b = b;
            this.cmp = cmp;
            this.end = end;
            this.line = line;
        }
    }

    private static final String[] OPS = {"pin", "digitalWrite", "analogWrite", "delay",
            "if", "while", "end", "else"};

    public static List<String> keywords() {
        List<String> out = new ArrayList<>();
        for (String s : OPS) {
            out.add(s);
        }
        out.add("HIGH");
        out.add("LOW");
        out.add("input");
        out.add("output");
        out.add("pwm");
        out.add("else");
        out.add("digitalRead");
        out.add("analogRead");
        out.add("millis");
        return out;
    }

    /**
     * Скомпилированная прошивка плюс её счётчик команд. Компилятор не бросается:
     * текст ошибки живёт в {@link #errorMessage()} — так GUI показывает «строка 4:
     * не понимаю ...», а симуляция просто не исполняет битый код.
     */
    public static final class Program {
        private final List<St> code;
        private final int[] pc = new int[1];
        private final int[] delayLeft = new int[1];
        private final String errorMessage;
        private final int errorLine;
        private long ticks;

        Program(List<St> code, String errorMessage, int errorLine) {
            this.code = code;
            this.errorMessage = errorMessage;
            this.errorLine = errorLine;
        }

        public boolean ok() {
            return errorMessage == null;
        }

        public String errorMessage() {
            return errorMessage;
        }

        public int errorLine() {
            return errorLine;
        }

        public int statements() {
            return code.size();
        }

        /** Тик симуляции с начала запуска (50 мс за тик). */
        public long millis() {
            return ticks * 50;
        }

        public int pc() {
            return pc[0];
        }

        public int delayTicks() {
            return delayLeft[0];
        }

        /** Сброс — как кнопка reset на плате: счётчик команд в начало. */
        public void reset() {
            pc[0] = 0;
            delayLeft[0] = 0;
        }

        /**
         * Один шаг: исполнить операторы, пока не упрёмся в delay или в квоту.
         *
         * @return true, если программа дошла до конца
         */
        public boolean tick(Board board) {
            boolean done = run(code, pc, delayLeft, board, STEPS_PER_TICK);
            ticks++;
            return done;
        }
    }

    /** Сколько операторов в программе (0 = пусто). */
    public static int count(String src) {
        return compile(src, 6).statements();
    }

    /** Компиляция без исключений: годный код или текст ошибки для игрока. */
    public static Program compile(String src, int pins) {
        return compile(src, pins, MAX_STATEMENTS);
    }

    private static String glue(String line) {
        // «} else {» — одна конструкция: склеиваем, иначе else придёт, когда блок
        // уже закрыт и openBlock пуст
        return line.replaceAll("\\}\\s*else\\s*\\{", "}ELSE{");
    }

    public static Program compile(String src, int pins, int budget) {
        if (src == null || src.isBlank()) {
            return new Program(new ArrayList<>(), null, 0);
        }
        try {
            return new Program(parse(src, pins, budget), null, 0);
        } catch (Error e) {
            return new Program(new ArrayList<>(), e.text(), e.line());
        }
    }

    /**
     * Компиляция. {@code pins} — сколько ног у микросхемы, {@code budget} — лимит
     * операторов (чтобы GUI мог сказать «программа слишком большая»).
     *
     * @return список операторов; {@link Error} вылетает исключением с текстом для игрока
     */
    static List<St> parse(String src, int pins, int budget) {
        List<St> out = new ArrayList<>();
        List<Integer> openBlock = new ArrayList<>();
        int[] pendingIf = new int[]{-1};
        if (src == null) {
            return out;
        }
        int lineNo = 0;
        for (String raw : src.split("\n", -1)) {
            lineNo++;
            String line = glue(stripComment(raw).trim());
            if (line.isEmpty()) {
                continue;
            }
            while (!line.isEmpty()) {
                if (line.startsWith("}ELSE{")) {
                    line = line.substring(6).trim();
                    close(out, openBlock, pendingIf, lineNo);
                    emit(out, "else", lineNo, pins, budget, openBlock, pendingIf, true);
                    openBlock.add(out.size() - 1);
                    continue;
                }
                int sc = line.indexOf(';');
                int br = line.indexOf('{');
                int cut;
                boolean wantBlock = false;
                if (br >= 0 && (sc < 0 || br < sc)) {
                    cut = br;
                    wantBlock = true;
                } else if (sc >= 0) {
                    cut = sc;
                } else {
                    cut = line.length() - 1;
                }
                String stmt = line.substring(0, cut + 1).trim();
                line = line.substring(Math.min(cut + 1, line.length())).trim();
                if (!stmt.isEmpty()) {
                    emit(out, stmt, lineNo, pins, budget, openBlock, pendingIf, wantBlock);
                }
                if (wantBlock) {
                    // открывающий оператор — последний выданный (if/while), не следующий
                    openBlock.add(out.size() - 1);
                }
                // закрывающая скобка того же ряда завершает блок (а «}ELSE{» —
                // закрывает и тут же открывает ветку else)
                while (line.startsWith("}")) {
                    if (line.startsWith("}ELSE{")) {
                        line = line.substring(6).trim();
                        close(out, openBlock, pendingIf, lineNo);
                        emit(out, "else", lineNo, pins, budget, openBlock, pendingIf, true);
                        openBlock.add(out.size() - 1);
                        continue;
                    }
                    line = line.substring(1).trim();
                    close(out, openBlock, pendingIf, lineNo);
                }
                if (line.isEmpty()) {
                    break;
                }
            }
        }
        if (!openBlock.isEmpty()) {
            throw new Error(lineNo, "не закрыта { для оператора из строки "
                    + out.get(openBlock.get(openBlock.size() - 1)).line);
        }
        return out;
    }

    /**
     * Закрылся блок. END получает ссылку на свой открывающий оператор (по ней
     * while возвращается в начало), а открывающий оператор — цель перехода:
     * if прыгает на сам END (тот пустой и просто идёт дальше, а если рядом был
     * else — то else уведёт за блок), while прыгает за END, чтобы не зациклиться
     * на пустом теле.
     */
    private static void close(List<St> out, List<Integer> openBlock, int[] pendingIf, int lineNo) {
        if (openBlock.isEmpty()) {
            throw new Error(lineNo, "лишняя }");
        }
        int idx = openBlock.remove(openBlock.size() - 1);
        St open = out.get(idx);
        out.add(new St(OP_END, idx, 0, 0, 0, open.line));
        int end = out.size() - 1;
        // while и else должны перешагнуть собственный END, if — на него
        out.set(idx, withEnd(open, open.op == OP_WHILE || open.op == OP_ELSE ? end + 1 : end));
        // за if можно зайти в else; за while и за else — уже нет
        pendingIf[0] = open.op == OP_IF ? idx : -1;
    }

    private static St withEnd(St s, int end) {
        return new St(s.op, s.a, s.b, s.cmp, end, s.line);
    }

    private static void emit(List<St> out, String stmt, int lineNo, int pins, int budget,
                             List<Integer> openBlock, int[] pendingIf, boolean wantBlock) {
        if (out.size() >= budget) {
            throw new Error(lineNo, "слишком много операторов (максимум " + budget + ")");
        }
        String s = stmt.replace(";", "").trim();
        if (s.equals("}")) {
            throw new Error(lineNo, "лишняя } — блока не было");
        }
        String head;
        String arg = "";
        int par = s.indexOf('(');
        if (par >= 0) {
            // вызов: digitalWrite(2, HIGH)
            head = s.substring(0, par).trim();
            int close = s.lastIndexOf(')');
            arg = s.substring(par + 1, close < par ? s.length() : close).trim();
        } else {
            // слово с аргументами: pin 2 output
            int sp = s.length();
            for (int i = 0; i < s.length(); i++) {
                if (Character.isWhitespace(s.charAt(i))) {
                    sp = i;
                    break;
                }
            }
            head = s.substring(0, sp).trim();
            arg = s.substring(Math.min(sp, s.length())).trim();
        }
        switch (head) {
            case "pin": {
                String[] parts = arg.split("\\s+");
                if (parts.length < 2 || parts[0].isEmpty()) {
                    throw new Error(lineNo, "pin 2 output; — номер и направление");
                }
                int pin = num(parts[0], lineNo, pins);
                if (pin < 2) {
                    throw new Error(lineNo, "нога " + pin + " — это питание (0 = VCC, 1 = GND),"
                            + " прошивка её не переназначает; управляй ногами 2.." + (pins - 1));
                }
                int mode;
                if (parts[1].startsWith("in")) {
                    mode = 0;
                } else if (parts[1].startsWith("pwm")) {
                    mode = 2;
                } else if (parts[1].startsWith("out")) {
                    mode = 1;
                } else {
                    throw new Error(lineNo, "направление бывает input, output или pwm");
                }
                out.add(new St(OP_PIN, pin, mode, 0, 0, lineNo));
                break;
            }
            case "digitalWrite": {
                String[] a = arg.split(",");
                if (a.length != 2) {
                    throw new Error(lineNo, "digitalWrite(нога, HIGH|LOW)");
                }
                int pin = num(a[0], lineNo, pins);
                if (pin < 2) {
                    throw new Error(lineNo, "нога " + pin + " — питание, digitalWrite туда не пишется");
                }
                int lvl = logic(a[1], lineNo);
                out.add(new St(OP_WRITE, pin, lvl, 0, 0, lineNo));
                break;
            }
            case "analogWrite": {
                String[] a = arg.split(",");
                if (a.length != 2) {
                    throw new Error(lineNo, "analogWrite(нога, 0..255)");
                }
                int pin = num(a[0], lineNo, pins);
                if (pin < 2) {
                    throw new Error(lineNo, "нога " + pin + " — питание, analogWrite туда не пишется");
                }
                int duty = clamp(num(a[1], lineNo, 255), 0, 255);
                out.add(new St(OP_PWM, pin, duty, 0, 0, lineNo));
                break;
            }
            case "delay": {
                out.add(new St(OP_DELAY, clamp(num(arg, lineNo, 100000), 0, 600000), 0, 0, 0, lineNo));
                break;
            }
            case "if":
            case "while": {
                Cond c = cond(arg, lineNo);
                out.add(new St(head.equals("if") ? OP_IF : OP_WHILE, c.pin, c.b, c.cmp,
                        wantBlock ? -1 : out.size() + 1, lineNo));
                if (!wantBlock) {
                    // if (cond) stmt; — блок из одного оператора
                    out.add(new St(OP_END, 0, 0, 0, 0, lineNo));
                }
                break;
            }
            case "else": {
                if (!out.isEmpty() && out.get(out.size() - 1).op != OP_END) {
                    throw new Error(lineNo, "else бывает только после закрытой }");
                }
                if (pendingIf[0] < 0) {
                    throw new Error(lineNo, "else без if");
                }
                // else — переход без условия; цель перешагнёт его собственный END
                out.add(new St(OP_ELSE, 0, 0, 0, -1, lineNo));
                break;
            }
            default:
                throw new Error(lineNo, "не понимаю «" + head + "»; есть только "
                        + "pin, digitalWrite, analogWrite, delay, if, while");
        }
    }

    /** Условие вида digitalRead(3) == HIGH / analogRead(4) > 512. */
    private static final class Cond {
        final int pin;
        final int b;
        final int cmp;

        Cond(int pin, int b, int cmp) {
            this.pin = pin;
            this.b = b;
            this.cmp = cmp;
        }
    }

    private static final int CMP_EQ = 0;
    private static final int CMP_NE = 1;
    private static final int CMP_LT = 2;
    private static final int CMP_GT = 3;
    private static final int CMP_LE = 4;
    private static final int CMP_GE = 5;

    private static Cond cond(String arg, int lineNo) {
        String s = arg.trim();
        if (s.startsWith("(")) {
            s = s.substring(1, s.lastIndexOf(')') < 0 ? s.length() : s.lastIndexOf(')')).trim();
        }
        int op = 0;
        String cmp = null;
        String[] signs = {"==", "!=", ">=", "<=", ">", "<"};
        int[] codes = {CMP_EQ, CMP_NE, CMP_GE, CMP_LE, CMP_GT, CMP_LT};
        for (int i = 0; i < signs.length; i++) {
            int at = s.indexOf(signs[i]);
            if (at > 0) {
                op = codes[i];
                cmp = signs[i];
                break;
            }
        }
        if (cmp == null) {
            throw new Error(lineNo, "условие нужно вида digitalRead(3) == HIGH");
        }
        String left = s.substring(0, s.indexOf(cmp)).trim();
        String right = s.substring(s.indexOf(cmp) + cmp.length()).trim();
        int pin = readPin(left, lineNo);
        int b;
        boolean analog = left.trim().startsWith("analog");
        if (right.toUpperCase().startsWith("HIGH")) {
            b = 1;
        } else if (right.toUpperCase().startsWith("LOW")) {
            b = 0;
        } else {
            b = num(right, lineNo, 1 << 20);
        }
        // analogRead сравнивается по АЦП, digitalRead — по порогу VCC/2.
        // знак номера ноги и есть этот флаг (экономим поле в операторе).
        return new Cond(analog ? -pin - 1 : pin, b, op);
    }

    private static int readPin(String expr, int lineNo) {
        String s = expr.trim();
        int par = s.indexOf('(');
        int close = s.lastIndexOf(')');
        if (par < 0 || close < par) {
            throw new Error(lineNo, "читать можно digitalRead(нога) или analogRead(нога)");
        }
        String name = s.substring(0, par).trim();
        if (!name.equals("digitalRead") && !name.equals("analogRead")) {
            throw new Error(lineNo, "не знаю функцию «" + name + "»");
        }
        return num(s.substring(par + 1, close), lineNo, 1 << 20);
    }

    private static int logic(String s, int lineNo) {
        String t = s.trim().toUpperCase();
        if (t.startsWith("HIGH") || t.equals("1")) {
            return 1;
        }
        if (t.startsWith("LOW") || t.equals("0")) {
            return 0;
        }
        throw new Error(lineNo, "уровень бывает HIGH или LOW");
    }

    private static int num(String s, int lineNo, int maxInclusive) {
        String t = s.trim().replace("_", "");
        int base = 10;
        if (t.startsWith("0x") || t.startsWith("0X")) {
            base = 16;
            t = t.substring(2);
        }
        try {
            int v = Integer.parseInt(t, base);
            if (v < 0) {
                throw new NumberFormatException();
            }
            if (maxInclusive < (1 << 20) && v > maxInclusive) {
                throw new Error(lineNo, "число " + v + " больше " + maxInclusive);
            }
            return v;
        } catch (NumberFormatException e) {
            throw new Error(lineNo, "ожидалось число, а написано «" + s.trim() + "»");
        }
    }

    private static String stripComment(String line) {
        int slash = line.indexOf("//");
        return slash < 0 ? line : line.substring(0, slash);
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : Math.min(v, hi);
    }

    // ------------------------------------------------------------------ исполнение

    /**
     * Прогнать прошивку до упора: пока не упрётся в delay или в конец.
     *
     * @return true, если программа дошла до конца и может «отдохнуть»
     */
    public static boolean runOnce(List<St> code, int[] pc, int[] delayLeft, Board board) {
        return run(code, pc, delayLeft, board, STEPS_PER_TICK);
    }

    private static boolean run(List<St> code, int[] pc, int[] delayLeft, Board board, int budget) {
        if (code.isEmpty()) {
            return true;
        }
        int spent = 0;
        int guard = 0;
        int whileSpent = 0;
        while (pc[0] < code.size()) {
            if (delayLeft[0] > 0) {
                delayLeft[0]--;
                return false;
            }
            if (spent++ >= budget || guard++ > code.size() + budget) {
                return false;
            }
            St s = code.get(pc[0]);
            switch (s.op) {
                case OP_PIN:
                    board.dir(s.a, s.b);
                    pc[0]++;
                    break;
                case OP_WRITE:
                    board.pwm(s.a, 0);
                    board.level(s.a, s.b);
                    pc[0]++;
                    break;
                case OP_PWM:
                    board.dir(s.a, 2);
                    board.pwm(s.a, s.b);
                    pc[0]++;
                    break;
                case OP_DELAY:
                    delayLeft[0] = Math.max(1, (s.a + 49) / 50);
                    pc[0]++;
                    return false;
                case OP_ELSE:
                    pc[0] = s.end;            // безусловный переход за блок
                    break;
                case OP_IF: {
                    boolean ok = eval(s, board);
                    if (ok) {
                        pc[0]++;
                    } else {
                        pc[0] = s.end;
                    }
                    break;
                }
                case OP_WHILE: {
                    boolean ok = eval(s, board);
                    if (ok) {
                        pc[0]++;
                    } else {
                        pc[0] = s.end;
                    }
                    break;
                }
                case OP_END: {
                    St open = code.get(s.a);
                    if (open.op == OP_WHILE) {
                        if (whileSpent++ >= WHILE_LIMIT) {
                            whileSpent = 0;
                            pc[0] = open.end;   // надоело: уходим, продолжим в следующем тике
                        } else {
                            pc[0] = s.a;         // тело while: снова к условию
                        }
                    } else {
                        pc[0]++;                // конец if/else — просто дальше
                    }
                    break;
                }
                default:
                    pc[0]++;
                    break;
            }
        }
        pc[0] = 0;
        return true;
    }

    private static boolean eval(St s, Board board) {
        int v = read(s.a, board);
        switch (s.cmp) {
            case CMP_EQ:
                return v == s.b;
            case CMP_NE:
                return v != s.b;
            case CMP_LT:
                return v < s.b;
            case CMP_LE:
                return v <= s.b;
            case CMP_GE:
                return v >= s.b;
            default:
                return v > s.b;
        }
    }

    /**
     * Чтение ноги. Цифровое чтение аналогового входа — это «больше половины VCC»,
     * ровно как в Arduino (там тот же регистр).
     */
    private static int read(int pin, Board board) {
        if (pin >= 0) {
            double vcc = board.vcc();
            double half = vcc <= 0.1 ? 2.5 : vcc * 0.5;
            return board.voltage(pin) >= half ? 1 : 0;
        }
        int p = -pin - 1;
        return adc(board.voltage(p), board.vcc());
    }

    /** 10-битное АЦП-значение напряжения ноги (для показа в GUI). */
    public static int adc(double voltage, double vcc) {
        if (vcc <= 0.1) {
            return 0;
        }
        double f = voltage / vcc;
        if (f <= 0) {
            return 0;
        }
        return f >= 1 ? ADC_MAX : (int) Math.round(f * ADC_MAX);
    }
}
