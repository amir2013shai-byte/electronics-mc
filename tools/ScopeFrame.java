package me.arena.electronics.circuit;

import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Кадр экрана осциллографа в PNG — офлайн, без сервера и без клиента.
 *
 * <p>Зачем: то, что игрок увидит над блоком, рисует {@link ScopeScreen}, а он
 * от Bukkit не зависит. Значит картинку можно собрать тем же кодом, что работает
 * в мире, и положить рядом с превью текстур: любое расхождение «в тесте одно, в
 * игре другое» здесь становится видно сразу. Рисунок — не имитация: берётся
 * тот же растр 40&#215;16, те же {@code grid()}, {@code autoscale()} и
 * {@code trace()}, только пиксель увеличивается в 8 раз.
 *
 * <p>Запуск: {@code bash tools/scope-frame.sh} (или см. {@code tools/publish.sh}).
 */
public final class ScopeFrame {

    private static final int SCALE = 8;

    public static void main(String[] args) throws Exception {
        String out = args.length > 0 ? args[0] : "build/scope-screen.png";

        // 1 Гц синус 6 В со смещением +1 В: ровно два периода в окне, смещение
        // заодно показывает, что автомасштаб считает середину, а не ноль.
        ScopeTrace tr = new ScopeTrace();
        tr.setRate(0.05, 1);
        for (int i = 0; i < 160; i++) {
            tr.sample(1.0 + 6.0 * Math.sin(2 * Math.PI * i * 0.05));
        }
        ScopeTrace.Stats st = tr.measure();
        double[] sc = ScopeScreen.autoscale(st.mean(), st.vpp(), 1.3);
        double[] v = new double[ScopeTrace.SCREEN_SAMPLES];
        for (int i = 0; i < v.length; i++) {
            v[i] = tr.at(st.start() + i);
        }
        boolean[] px = ScopeScreen.mask();
        ScopeScreen.grid(px, 8, 4);
        ScopeScreen.trace(v, sc[0], sc[1], px);

        BufferedImage img = new BufferedImage(ScopeScreen.W * SCALE, ScopeScreen.H * SCALE,
                BufferedImage.TYPE_INT_RGB);
        int bg = 0x0e1f16, lit = 0x3ddc84;
        for (int y = 0; y < ScopeScreen.H; y++) {
            for (int x = 0; x < ScopeScreen.W; x++) {
                int c = px[ScopeScreen.idx(x, y)] ? lit : bg;
                for (int dy = 0; dy < SCALE; dy++) {
                    for (int dx = 0; dx < SCALE; dx++) {
                        img.setRGB(x * SCALE + dx, y * SCALE + dy, c);
                    }
                }
            }
        }
        new File(out).getParentFile().mkdirs();
        ImageIO.write(img, "png", new File(out));

        String[] rows = ScopeScreen.braille(px);
        StringBuilder pic = new StringBuilder();
        for (String row : rows) {
            pic.append(row).append('\n');
        }
        System.out.println("кадр " + ScopeScreen.W + "\u00d7" + ScopeScreen.H + " -> " + pic.length()
                + " знаков, " + ScopeScreen.lit(px) + " светящихся точек");
        System.out.print(pic);
        System.out.println("Upp " + String.format(java.util.Locale.US, "%.2f", st.vpp())
                + " В, f " + String.format(java.util.Locale.US, "%.3f", st.freqHz())
                + " Гц, шкала [" + String.format(java.util.Locale.US, "%.2f", sc[0]) + " .. "
                + String.format(java.util.Locale.US, "%.2f", sc[1]) + "] В");
        System.out.println("saved " + out);
    }
}
