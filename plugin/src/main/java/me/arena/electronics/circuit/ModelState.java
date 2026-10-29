package me.arena.electronics.circuit;

import me.arena.electronics.registry.Components;
import me.arena.electronics.registry.Def;

/**
 * «Реестр состояний модели».
 *
 * Каждый компонент имеет несколько визуальных состояний (выкл/тускло/горит/
 * сгорел/обратно/замкнут) и 6 ориентаций. Их произведение даёт плоский индекс
 * 0..{@link Components#stateModels()}-1, который ресурспак превращает в модель
 * через `item_model`/custom_model_data — без клиентских модов.
 */
public final class ModelState {

    private ModelState() {
    }

    public static int of(CompInstance c) {
        return of(c.def, c.state, c.orientation == null ? 0 : c.orientation.index);
    }

    public static int of(Def def, int state, int orientationIndex) {
        if (def == null) {
            return 0;
        }
        return def.stateModel(state, orientationIndex);
    }

    /** Для отладки: человекочитаемое имя состояния модели. */
    public static String describe(int stateModel) {
        return Components.stateModelName(stateModel);
    }
}
