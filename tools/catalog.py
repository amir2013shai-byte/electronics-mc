#!/usr/bin/env python3
"""Генерирует docs/blocks.md — каталог всех блоков из shared/components.json.

Скрипт — единственный источник правды для этого файла: цифры (номиналы, число
состояний, диапазоны индексов модели) берутся из реестра, поэтому документ не
может разойтись с паком. Запуск: python3 tools/catalog.py
"""
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RP_MIN, DP_MIN, DP_MAX = 64, 80, 94     # те же границы, что пишет tools/gen_pack.py
SHARED = os.path.join(ROOT, "shared", "components.json")
OUT = os.path.join(ROOT, "docs", "blocks.md")

DATA = json.load(open(SHARED, encoding="utf-8"))
COMPS = DATA["components"]
META = DATA["meta"]
VIS = [{"suffix": ("_" + v["suffix"]) if v.get("suffix") else "",
        "name": v.get("name", ""), "index": v.get("index", i)}
       for i, v in enumerate(DATA.get("visual_states", []))]
ORIENTS = [o["name"] for o in DATA.get("orientations", [])]

# ---------------------------------------------------------------- человекочитаемо
GROUPS = [
    ("RESISTOR", "Резисторы", "Номинал задан в `resistance`, мощность в `rated_power`. "
     "Превышение 3× от номинала → состояние `burnt` и запрет работать до `/elec repair`."),
    ("CAPACITOR", "Конденсаторы", "Companion-модель `C/dt` (явный Эйлер, шаг — "
     "`simulation.time-step-ms`, по умолчанию 50 мс). Полярные (`polar`) сгорают при "
     "`U(+)-U(-) < -0.5 В`, ненарушенные держат `rated_voltage·1.3`."),
    ("LED", "Светодиоды", "Кусочно-линейная модель: порог `v_forward`, дифференциальное "
     "сопротивление 25 Ом, яркость = |I| / 20 мА. Обратное напряжение выше "
     "`v_reverse_max` → лавинный пробой."),
    ("LAMP", "Лампы", "Нелинейное сопротивление: холодное = `resistance·cold_factor`, "
     "горячее = `resistance`; яркость по мощности."),
    ("DIODE", "Диоды", "Тот же кусочно-линейный диод, но порог `v_drop`, обратный пробой "
     "по `v_rev_max`."),
    ("TRANSISTOR", "Транзисторы", "Упрощённая модель BJT: база эмулируется током `ib`, "
     "коллектор — управляемый источник с `beta`, насыщение при |U_ce| < 0.2 В."),
    ("GATE", "Логические микросхемы", "Выход — источник через 100 Ом к узлам `VCC` "
     "(контакт сверху) и `GND` (снизу): без подключения питания на подсказке острова "
     "висит «логическому элементу нужны VCC и GND»."),
    ("SOURCE", "Источники питания", "Тевенин: ЭДС `voltage` (у регулируемых — "
     "`voltage·tune/1000`, крутится ПКМ/`/elec tune`) и внутреннее `r_int`."),
    ("GENERATOR", "Генераторы", "Как источник, но ЭДС зависит от оборотов/нагрузки."),
    ("SOLAR", "Фотопанели", "Как источник, ЭДС умножается на освещённость блока сверху "
     "(0..1), поэтому ночью отдаёт 0 В."),
    ("AMMETER", "Амперметры", "Маленькое сопротивление `internal_resistance`, показывает "
     "ток своей ветви."),
    ("VOLTMETER", "Вольтметры", "Сопротивление 1 МОм, показывает разность узлов на своих "
     "контактах (`/elec dump` печатает то же самое)."),
    ("MULTIMETER", "Мультиметры", "Вольтметр/омметр/прозвонка в одном корпусе, режим "
     "переключается ПКМ."),
    ("PROBE", "Щупы", "Точка измерения: рисует потенциал узла относительно земли схемы."),
    ("WIRE", "Провода", "`hub: true` — провод объединяет все шесть соседних сетей, "
     "поэтому им делают «перекрёстки» и ответвления; верхняя и нижняя ножки дают "
     "вертикальные переходы."),
    ("GROUND", "Шина земли", "Замыкающая `hub`-деталь: её объединённый узел принимается "
     "за 0 В, от него считаются все потенциалы."),
    ("SWITCH", "Ключи", "ПКМ — замыкает/размыкает; `spdt` перекидывает между двумя "
     "контактами. Замкнутое состояние — состояние модели `closed`."),
    ("BUTTON", "Кнопки", "Как ключ, но возвращается в разомкнутое состояние через "
     "`simulation.button-hold-ticks`."),
    ("PTC", "Самовосстанавливающиеся предохранители", "Термистор R0·(1+0.4 %/°C) до "
     "температуры `trip_temp`, после — +порядок каждые 2.5 °C (до 1e7 Ом). Тепло копится "
     "по `heat_j` (Дж/К) и утекает через `thermal_k` (К/Вт): потому КЗ выключает деталь за "
     "доли секунды, а 1.2× номинала она держит минут\u0430ми. Остывает — возвращается сама; "
     "пик перегрева видно в подсказке острова."),
    ("VARISTOR", "Варисторы (MOV)", "Нелинейный R: I = K·U^α, K подогнан так, чтобы на "
     "пороге `clamp_voltage` шёл 1 мА. До порога — единицы наноампер, выше — «кирпич». "
     "Каждый поглощённый импульс добавляет деградацию (aux[1]), после трёх — открытое КЗ: "
     "варистор расходник, его меняют."),
    ("BRIDGE", "Диодные мосты", "Четыре p-n перехода в одном корпусе (150 В/8 А): "
     "выпрямление с падением ≈2·Uпорог ≈ 1.1 В. Выводы крестом — AC1/AC2 по оси, +/− "
     "перпендикулярно, поэтому в редакторе схемы мост ставится в средний ряд."),
    ("REGULATOR", "Линейные стабилизаторы", "Uвых = min(Uцель, Uвх − dropout); 78L05 — "
     "фиксированные 5 В, LM317 — `regulator`-формула от `tune/1000`. Внутреннее "
     "сопротивление `internal_resistance` держит стабилизацию, при токе отдачи выше "
     "`i_limit` деталь переключается в режим источника тока (коротыш = 1 А, а не 8 А). "
     "Корпус греется по (Uвх − Uвых)·I: при ΔT > 100 °C лимит падает, при 200 °C — отказ."),
    ("TRANSFORMER", "Трансформаторы", "Две связанные индуктивности, обратный Эйлер: "
     "L2 = L1/Кт², R2 = R1/Кт², M = k·√(L1·L2). Постоянный ток не передаёт (в обесточенном "
     "острове вторичка даёт микроамперы переходного процесса), на 50 Гц коэффициент "
     "получается 1/Кт. Проверка точности — `tools/TestCoupled.java` (2-порт решается "
     "аналитически, расхождение 0.5 %)."),
    ("SUPPLY", "Расходники и инструмент", "Припой, флюс, оплётка, заготовки: не имеют "
     "электричества, но без них не собирается ни одна деталь (см. пайку). Оплётка "
     "`desolder_braid` снимает припой и позволяет выпаять сгоревшую деталь обратно в рот."),
    ("FUSE", "Предохранители", "Копит I²t (`blow_i2t`): при 45 % бюджета деталь "
     "подсвечивается, при превышении — размыкается навсегда. Для стеклянных 5×20 без "
     "бюджета — мгновенно при |I| > 1.5·`max_current` и остаётся "
     "сгоревшим до `/elec repair`."),
    ("MOTOR", "Двигатели", "Обмотка `winding` Ом + ЭДС вращения `back_emf_per_volt`; "
     "перегруз по напряжению (`rated_voltage·1.6`) сжигает обмотку."),
    ("BUZZER", "Зуммеры", "Идеальная нагрузка 100 Ом, при токе > 5 мА играет "
     "`electronics.buzz` (см. config `sound.enabled`)."),
    ("POT", "Подстроечные резисторы", "Делитель: доля `tune/1000` общего `resistance`, "
     "крутится ПКМ или `/elec tune <id> <0..1000>`."),
    ("BOARD", "Подставки", "Служебные плиты: `bench`, `breadboard`-ряды. Не являются "
     "проводниками (`solid: false`) и никогда не объединяют сети."),
    ("BREADBOARD", "Макетные платы", "Ряды/шины макетки: каждый канал — отдельная сеть."),
    ("SCHEMATIC", "Планшеты", "Чистый лист для схемы: держит метку `/elec schematic`, "
     "в расчёте не участвует."),
    ("CUTTER", "Инструменты", "Кусачки/нож: снимают провод, не ломая подставку."),
    ("EMPTY", "Заглушки", "Зарезервировано реестром (используется алиасами состояний)."),
]

STATE_HELP = {
    "": "покоится / обесточен",
    "off": "покоится / обесточен",
    "dim": "слабая яркость (< 15 % номинала)",
    "on": "горит / включено",
    "burnt": "отказ: деталь выпаяна и ждёт ремонта (паяльник + оплётка на столе "
             "или `/elec repair` для отладки)",
    "rev": "обратная полярность или обратное включение",
    "reverse": "обратная полярность / обратное включение (у LED — ещё и режим "
               "лавинного пробоя)",
    "closed": "ключ замкнут",
}


def fmt_params(c):
    out = []
    for key, label, unit, scale in (
        ("resistance", "R", "Ω", 1),
        ("rated_power", "Pном", "Вт", 1),
        ("capacitance", "C", "Ф", 1),
        ("rated_voltage", "Uмакс", "В", 1),
        ("voltage", "ЭДС", "В", 1),
        ("r_int", "r внут", "Ω", 1),
        ("v_forward", "U порог", "В", 1),
        ("v_drop", "U пад", "В", 1),
        ("v_reverse_max", "U обр", "В", 1),
        ("v_rev_max", "U обр", "В", 1),
        ("internal_resistance", "R внут", "Ω", 1),
        ("max_current", "I макс", "А", 1),
        ("min_current", "I мин", "А", 1),
        ("beta", "β", "", 1),
        ("cold_factor", "× холод", "", 1),
        ("back_emf_per_volt", "k ЭДС", "", 1),
        ("winding", "R обмотки", "Ω", 1),
    ):
        if key in c:
            val = c[key]
            if unit == "Ω" and val >= 1000:
                txt = f"{val/1000:g} кΩ"
            elif unit == "Ф":
                txt = (f"{val*1e6:g} мкФ" if val < 1 else f"{val:g} Ф")
            elif unit == "А" and val < 1:
                txt = f"{val*1000:g} мА"
            elif isinstance(val, float):
                txt = f"{val:g}"
            else:
                txt = str(val)
            out.append(f"{label} {txt}{(' ' + unit) if unit and unit not in txt else ''}")
    if c.get("logic"):
        out.append("логика " + c["logic"])
    if c.get("glow_color"):
        r, g, b = c["glow_color"]
        out.append(f"свечение #{r:02x}{g:02x}{b:02x}")
    return "; ".join(out) or "—"


def flags(c):
    f = []
    if c.get("hub"):
        f.append("hub")
    if c.get("polar"):
        f.append("полярный")
    if c.get("tunable"):
        f.append("регулируемый")
    if c.get("spdt"):
        f.append("перекидной")
    if c.get("stack") is False:
        f.append("не стакается")
    if c.get("solid") is False:
        f.append("не проводник")
    if not c.get("orientable", True):
        f.append("без ориентации")
    return ", ".join(f) or "—"


def main():
    total_states = sum(int(c.get("states", 1)) for c in COMPS)
    lines = [
        "# Каталог блоков",
        "",
        "Сгенерировано `tools/catalog.py` из `shared/components.json` — не правь руками.",
        "",
        f"* Minecraft **{META['mc_version']}**: форматы паков — ресурспак "
        f"`{RP_MIN}..{META['resource_pack_format']}`, датапак `{DP_MIN}..{DP_MAX}` "
        f"(и `pack_format`, и `min_format`/`max_format`) — см. docs/pack-compatibility.md",
        f"* компонентов: **{len(COMPS)}**, визуальных состояний: **{total_states}**, "
        f"индексов состояния модели: **{META['state_model_count']}** "
        f"= Σ(`states` × 6 ориентаций)",
        f"* namespace: `{META['namespace']}` — ключ модели выглядит как "
        f"`{META['namespace']}:<id><суффикс состояния>`",
        "",
        "## Состояния модели",
        "",
        "`Def.stateModel(state, orient) = base + state·6 + orient`, где `base` — "
        "префиксная сумма по порядку этого файла. Суффиксы и что они значат:",
        "",
        "| суффикс | индекс | смысл |",
        "|---|---|---|",
    ]
    for v in VIS:
        lines.append(f"| `{v['suffix'] or '(нет суффикса)'}` | {v['index']} | "
                     f"`{v['name']}` — {STATE_HELP.get(v['name'], STATE_HELP.get(v['suffix'], ''))} |")
    lines += [
        "",
        "Ориентации: " + ", ".join(f"{i} — {n}" for i, n in enumerate(ORIENTS)) + ". "
        "У детали без `orientable: false` ноги всегда смотрят в две стороны оси, "
        "поэтому «узел» — это пара соседних блоков.",
        "",
        "## Группы",
        "",
    ]
    base = 0
    by_kind = {}
    for c in COMPS:
        by_kind.setdefault(c["kind"], []).append((base, c))
        base += int(c.get("states", 1)) * 6
    for kind, title, note in GROUPS:
        rows = by_kind.pop(kind, None)
        if not rows:
            continue
        lines += [f"### {title} (`{kind}`, {len(rows)} шт.)", "", note, "",
                  "| id | название | что внутри | состояний | флаги | индексы модели |",
                  "|---|---|---|---|---|---|"]
        for b, c in rows:
            n = int(c.get("states", 1))
            idx = f"{b}…{b + n * 6 - 1}" if n else "—"
            lines.append(f"| `{c['id']}` | {c.get('ru', c['id'])} / {c.get('en', '—')} "
                         f"| {fmt_params(c)} | {n} | {flags(c)} | {idx} |")
        lines.append("")
    if by_kind:
        lines += ["### Прочее", "", "| id | kind | название |", "|---|---|---|"]
        for kind, rows in by_kind.items():
            for _, c in rows:
                lines.append(f"| `{c['id']}` | `{kind}` | {c.get('ru', c['id'])} |")
        lines.append("")

    lines += [
        "## Текстуры и модели",
        "",
        "* генерируются в `build/resourcepack/` (одноразовый каталог — пересобери "
        "`bash tools/publish.sh`);",
        "* текстуры предметов: "
        "`build/resourcepack/assets/electronics/textures/item/<id><суффикс>_{top,side,bottom}.png` "
        "— 32×32, процедурные (`tools/gen_pack.py`, функция `base_canvas()`);",
        "* модели: `build/resourcepack/assets/electronics/models/item/<id><суффикс>.json`;",
        "* состояние модели: `build/resourcepack/assets/electronics/items/<id>.json`;",
        "* лист-превью всех состояний: `release/preview.png`, постранично "
        "(4×, удобно читать с телефона): `release/textures/sheet-0N.png` — "
        "генерирует `python3 tools/sheets.py`.",
        "",
    ]
    text = "\n".join(lines)
    while "\n\n\n" in text:          # не тащить пустые строки от пропущенных примечаний
        text = text.replace("\n\n\n", "\n\n")
    open(OUT, "w", encoding="utf-8").write(text)
    print(f"{OUT}: {len(COMPS)} блоков, {total_states} состояний, "
          f"{META['state_model_count']} индексов")


if __name__ == "__main__":
    main()
