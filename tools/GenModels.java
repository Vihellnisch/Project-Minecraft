import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;

/**
 * Generates the 3D weapon models: every weapon is built upright out of boxes (handle at the bottom, tip at the top,
 * centred on x = z = 8) and each element is rotated -45 degrees about the item centre, which lays it along the same
 * diagonal as the 2D sprite. That way vanilla's handheld display transforms fit, and the GUI keeps the pixel sprite
 * through neoforge:separate_transforms. Colours come from two palette textures (one row per weapon); glowing parts
 * use the animated glow palette and are rendered full-bright.
 *
 * Usage: java tools/GenModels.java <resources dir>
 */
public class GenModels {
    record Box(double x0, double y0, double z0, double x1, double y1, double z1, int color, boolean glow) {}

    static final class Weapon {
        final String name;
        final int row;
        final int[] pal, glow;
        final double shiftY;
        /** Guns are authored pointing forward (barrel along -z) and keep their own display transforms. */
        boolean gun;
        /** Mirrored left to right about the haft (x = 8): axes authored with the blade on +x end up with the edge facing the holder. */
        boolean mirror;
        /** Written as authored, like a gun (no shrink, no 45 degree tilt), but with the melee display: worn pieces. */
        boolean raw;
        /** Item property that swaps the model for an empty one (thrown axe, sheathed katana), or null. */
        String hideWhen;
        /** Standalone models (no item, no sprite) go to models/<folder>/ instead of models/item/3d/. */
        String folder;
        /** Worn rather than held (the Black Silence's gloves): nothing in the hand, the sprite in the inventory. */
        boolean worn;

        int sheet() {
            return row / GRID;
        }

        int localRow() {
            return row % GRID;
        }
        final List<Box> boxes = new ArrayList<>();

        Weapon(String name, int row, double shiftY, int[] pal, int[] glow) {
            this.name = name;
            this.row = row;
            this.shiftY = shiftY;
            this.pal = pal;
            this.glow = glow;
        }

        /** A bar centred on (cx, 8) running from y0 to y1, w wide and t thick. */
        Weapon bar(double cx, double y0, double y1, double w, double t, int c) {
            boxes.add(new Box(cx - w / 2, y0, 8 - t / 2, cx + w / 2, y1, 8 + t / 2, c, false));
            return this;
        }

        Weapon glowBar(double cx, double y0, double y1, double w, double t, int c) {
            boxes.add(new Box(cx - w / 2, y0, 8 - t / 2, cx + w / 2, y1, 8 + t / 2, c, true));
            return this;
        }

        Weapon box(double x0, double y0, double x1, double y1, double t, int c) {
            boxes.add(new Box(x0, y0, 8 - t / 2, x1, y1, 8 + t / 2, c, false));
            return this;
        }

        Weapon glowBox(double x0, double y0, double x1, double y1, double t, int c) {
            boxes.add(new Box(x0, y0, 8 - t / 2, x1, y1, 8 + t / 2, c, true));
            return this;
        }

        /** Free 3D box (for guns). */
        Weapon cube(double x0, double y0, double z0, double x1, double y1, double z1, int c, boolean glow) {
            boxes.add(new Box(x0, y0, z0, x1, y1, z1, c, glow));
            return this;
        }
    }

    /**
     * The E.G.O, the Black Silence and Furioso's weapons. Rows match Random Slop's palette sheets (where these lived
     * first), so the textures copied from it line up.
     */
    static List<Weapon> weapons() {
        List<Weapon> list = new ArrayList<>();
        int row = 17;
        Weapon w;
        // ---- 4th Match Flame: a matchstick the size of a greatsword, its head smouldering
        w = new Weapon("fourth_match_flame", row++, 0,
                new int[]{0xd8b07a, 0xa07848, 0x2a1a14, 0xb81e14, 0x6a0e0a, 0x3a2a2a},
                new int[]{0xff7a1a, 0xffd04a});
        w.bar(8, -6, -4.5, 2.0, 2.0, 5).bar(8, -4.5, 13, 1.8, 1.8, 0).bar(8, 0, 0.6, 1.95, 1.95, 1).bar(8, 6, 6.6, 1.95, 1.95, 1)
                .bar(8, 13, 16.5, 1.85, 1.85, 2).glowBar(8, 14.5, 14.9, 1.95, 1.95, 0)
                .bar(8, 16.5, 21, 3.2, 3.2, 3).bar(8, 21, 22.2, 2.4, 2.4, 3).bar(8, 17, 20.5, 3.35, 2.4, 4)
                .glowBar(8, 18, 18.5, 3.4, 3.4, 0).glowBar(8, 19.6, 20.0, 3.4, 3.4, 0).glowBar(8, 22.2, 23.6, 1.4, 1.4, 1).glowBar(8, 23.6, 24.6, 0.7, 0.7, 0);
        list.add(w);

        // ---- guns (barrel along -z)
        row = 21;
        // two pistols, The Living (white) and The Departed (black)
        w = new Weapon("solemn_lament", row++, 0,
                new int[]{0xeeeeee, 0x141418, 0xb0b4bc, 0x3a2a4a, 0x6a6a72},
                new int[]{0xbfe8ff, 0x9a6aff});
        w.gun = true;
        for (int g = 0; g < 2; g++) {
            double x = g == 0 ? 9.6 : 5.4;
            int body = g == 0 ? 0 : 1, trim = g == 0 ? 1 : 0;
            w.cube(x - 0.7, 6.4, -6, x + 0.7, 8.4, 6, body, false).cube(x - 0.45, 6.9, -8, x + 0.45, 7.8, -6, 2, false)
                    .cube(x - 0.6, 1.6, 4, x + 0.6, 6.4, 7, trim, false).cube(x - 0.6, 0.8, 5.2, x + 0.6, 2, 7.8, trim, false)
                    .cube(x - 0.25, 4.6, 2.2, x + 0.25, 6.4, 3.6, 4, false)
                    .cube(x - 0.75, 7.2, -2, x + 0.75, 7.7, 2, g == 0 ? 0 : 1, true);
        }
        list.add(w);

        // the Freischutz: a very long musket, blue steel, brass and dark wood, a rune down the barrel
        w = new Weapon("magic_bullet", row++, 0,
                new int[]{0x1e2a4a, 0x5a3418, 0xc8a050, 0x101018, 0x3a200e},
                new int[]{0x4a8aff, 0xb0d0ff});
        w.gun = true;
        w.cube(7.5, 7, -15.5, 8.5, 8, 6, 0, false).cube(7.4, 6.9, -15.9, 8.6, 8.1, -15.3, 2, false).cube(7.85, 8, -15, 8.15, 8.5, -14.4, 2, false)
                .cube(7.3, 6.2, -6, 8.7, 7.2, 8, 1, false).cube(7.2, 5.2, 6, 8.8, 7.8, 13, 1, false).cube(7.2, 3.2, 12, 8.8, 7.0, 20, 4, false)
                .cube(7.15, 3, 19.6, 8.85, 7.2, 20.4, 2, false)
                .cube(7.25, 6.6, 3, 8.75, 8.4, 6.5, 2, false).cube(7.7, 4.4, 6.5, 8.3, 5.2, 9.5, 2, false).cube(7.85, 8.4, 4.5, 8.15, 9.2, 5.5, 3, false)
                .cube(7.45, 7.3, -14, 7.5, 7.7, 2, 0, true).cube(8.5, 7.3, -14, 8.55, 7.7, 2, 0, true).cube(7.9, 8, -12, 8.1, 8.05, 0, 1, true);
        list.add(w);

        // ---- Black Silence: worn as gloves (GloveLayer), so the hand model is empty
        w = new Weapon("black_silence", row++, 0,
                new int[]{0x141418, 0x2a2a30, 0xc8a040, 0x0a0a0c, 0x48484e},
                new int[]{0xe8c060, 0xfff0c0});
        w.worn = true;
        w.bar(8, -5, 3, 3.3, 3.0, 0).bar(8, -5.6, -5, 3.6, 3.3, 2).bar(8, 2.4, 3, 3.6, 3.3, 2).bar(8, -1.6, -1.0, 3.45, 3.15, 3)
                .bar(8, 3, 9, 4.3, 3.6, 1).bar(8, 9, 10.4, 4.5, 3.0, 0).bar(8, 4.5, 8, 4.45, 3.75, 4)
                .glowBar(8, 6.0, 6.4, 4.55, 3.85, 0).glowBar(8, -4.5, 2.4, 0.3, 3.1, 1);
        for (int i = 0; i < 4; i++) w.box(6.1 + i * 1.1, 10.4, 6.8 + i * 1.1, 11.1, 2.4, 2);
        list.add(w);
        Furioso.add(list, row);
        return list;
    }

    /**
     * The weapons the Black Silence's gloves hold, drawn in the hand during Furioso (models/furioso/*.json, no item).
     * Held like any sword: grip at y = 0, blade up; guns barrel along -z.
     */
    static final class Furioso {
        static Weapon of(String name, int row, int[] pal, int[] glow) {
        Weapon w = new Weapon(name, row, 0, pal, glow);
        w.folder = "furioso";
        return w;
        }

        /**
         * Rebuilt from the reference sheet of Roland's arsenal: black steel and leather with polished silver trim, the
         * Zelkova axe on a wooden haft, Crystal Atelier blades veined with light. Palettes share one order where they can:
         * 0 black, 1 dark steel, 2 gunmetal, 3 silver, 4 bright silver, 5 leather wrap.
         */
        static final int[] STEEL = {0x141418, 0x26272c, 0x45474f, 0xa9adb5, 0xe6e9ee, 0x1e1c1b};

        static int[] steel(int... extra) {
        int[] p = java.util.Arrays.copyOf(STEEL, STEEL.length + extra.length);
        System.arraycopy(extra, 0, p, STEEL.length, extra.length);
        return p;
        }

        /** Wrapped grip: alternating leather bands from y0 to y1. */
        static void wrap(Weapon w, double y0, double y1, double width) {
        for (double y = y0; y < y1 - 0.01; y += 0.9) w.bar(8, y, Math.min(y1, y + 0.45), width + 0.15, width + 0.15, 5);
        }

        static int add(List<Weapon> list, int row) {
        // ---- Atelier Logic pistol: a long black handgun, the muzzle a slotted block, silver scrollwork down the
        // slide, a raked grip on a silver base plate, a silver trigger guard
        Weapon w = of("pistol", row++, steel(), new int[]{0xe6e9ee, 0xffffff});
        w.gun = true;
        w.cube(7.15, 6.6, -4, 8.85, 9, 5.2, 0, false)                              // slide
                .cube(6.95, 6.35, -8.2, 9.05, 9.2, -3.6, 1, false)                 // muzzle block
                .cube(6.9, 7.2, -8.0, 9.1, 7.45, -3.9, 3, false).cube(6.9, 7.85, -8.0, 9.1, 8.1, -3.9, 3, false)
                .cube(7.3, 7.0, -8.3, 8.7, 8.6, -8.2, 0, false)                    // bore
                .cube(6.9, 6.3, -3.6, 9.1, 9.25, -3.25, 3, false).cube(6.9, 6.3, -2.7, 9.1, 9.25, -2.4, 4, false) // bands
                .cube(7.6, 9.0, -2.2, 8.4, 9.2, 4.6, 3, false)                     // top rail
                .cube(7.1, 7.45, -2, 7.15, 7.7, 3.4, 4, false).cube(8.85, 7.45, -2, 8.9, 7.7, 3.4, 4, false)   // scrollwork
                .cube(7.1, 8.1, 0, 7.15, 8.35, 2.6, 3, false).cube(8.85, 8.1, 0, 8.9, 8.35, 2.6, 3, false)
                .cube(7.1, 7.7, 2.4, 7.15, 8.1, 2.7, 3, false).cube(8.85, 7.7, 2.4, 8.9, 8.1, 2.7, 3, false)
                .cube(7.4, 7.6, 5.2, 8.6, 9, 6.4, 1, false)                        // hammer block
                .cube(7.2, 4.1, 3.6, 8.8, 6.6, 6.3, 0, false)                      // grip, raked back
                .cube(7.2, 2.0, 4.5, 8.8, 4.1, 7.2, 0, false)
                .cube(7.25, 2.8, 5.0, 8.75, 5.6, 5.3, 2, false)                    // grip panel line
                .cube(7.1, 1.6, 4.3, 8.9, 2.0, 7.5, 3, false)                      // base plate
                .cube(7.75, 5.05, 1.4, 8.25, 5.3, 3.6, 3, false).cube(7.75, 5.05, 1.4, 8.25, 6.6, 1.65, 3, false) // guard
                .cube(7.85, 5.6, 2.5, 8.15, 6.6, 2.8, 0, false);                   // trigger
        list.add(w);

        // ---- Allas Workshop lance: a long cone of a spike on a polished collar, a narrow neck flaring into a broad
        // angular guard, then a short black handle with a knot and a spiked pommel
        w = of("lance", row++, steel(0x6a6e76), new int[]{0xe6e9ee, 0xffffff});
        w.bar(8, -15.6, -14.8, 0.35, 0.35, 3).bar(8, -14.8, -14, 0.8, 0.8, 3).bar(8, -14, -2.6, 0.95, 0.95, 0);
        wrap(w, -11.5, -6.5, 0.95);
        w.bar(8, -9.6, -8.4, 1.4, 1.4, 1).bar(8, -3, -2.4, 1.3, 1.3, 3);
        // the guard: wide at the handle, narrowing to the neck, silver along its edges
        w.bar(8, -2.4, -1.4, 2.6, 2.0, 0).bar(8, -1.4, 0.6, 4.4, 2.6, 0).bar(8, 0.6, 2.2, 3.6, 2.3, 0).bar(8, 2.2, 3.4, 2.4, 1.9, 0)
                .box(5.7, -1.4, 5.95, 0.6, 2.7, 3).box(10.05, -1.4, 10.3, 0.6, 2.7, 3)
                .box(6.15, 0.6, 6.35, 2.2, 2.4, 3).box(9.65, 0.6, 9.85, 2.2, 2.4, 3)
                .bar(8, -0.6, -0.2, 4.5, 2.7, 2).bar(8, 1.3, 1.6, 3.7, 2.4, 2);
        w.bar(8, 3.4, 5.0, 1.3, 1.3, 2).bar(8, 5.0, 6.0, 2.4, 2.4, 3).bar(8, 6.0, 8.6, 3.3, 3.3, 4).bar(8, 7.9, 8.3, 3.45, 3.45, 3);
        // a fat cone, nearly as wide as the guard at its base
        for (double y = 8.6; y < 31; y += 1.0) {
            double k = (y - 8.6) / 22.4, d = 3.0 * (1 - k) + 0.15;
            w.bar(8, y, y + 1.0, d, d, k < 0.45 ? 3 : k < 0.8 ? 6 : 1).box(8 - d / 2, y, 8 - d / 2 + 0.25, y + 1.0, d * 0.7, 4);
        }
        list.add(w);

        // ---- Old Boys Workshop hammer: two drum-like silver heads either side of a socket with a short spike,
        // a long wrapped haft with silver rings and a capped butt
        w = of("hammer", row++, steel(0x5c6068), new int[]{0xe6e9ee, 0xffffff});
        w.bar(8, -10.2, -9.6, 1.25, 1.25, 1).bar(8, -9.6, -9, 1.7, 1.7, 3).bar(8, -9, 13.2, 1.2, 1.2, 0);
        wrap(w, -8.5, -3, 1.2);
        wrap(w, 3, 9, 1.2);
        w.bar(8, -2.6, -2.1, 1.6, 1.6, 3).bar(8, 10.6, 11.2, 1.6, 1.6, 3).bar(8, 11.2, 13.2, 1.45, 1.45, 1);
        // socket and spike
        w.bar(8, 13.2, 17.6, 2.8, 2.8, 4).bar(8, 12.9, 13.5, 3.1, 3.1, 2).bar(8, 17.3, 17.9, 3.1, 3.1, 2)
                .bar(8, 17.9, 19.3, 1.0, 1.0, 3).bar(8, 19.3, 20.1, 0.45, 0.45, 4);
        // the heads: a box crossed with a slimmer one reads as a drum; dark rings near each face
        for (int s = -1; s <= 1; s += 2) {
            double in = 8 + s * 1.4, out = 8 + s * 6.2;
            double lo = Math.min(in, out), hi = Math.max(in, out);
            w.box(lo, 13.7, hi, 17.1, 3.7, 3).box(lo, 13.3, hi, 17.5, 2.7, 3)
                    .box(s > 0 ? hi - 0.5 : lo, 13.5, s > 0 ? hi : lo + 0.5, 17.3, 3.3, 4)
                    .box(s > 0 ? hi - 1.3 : lo + 1.0, 13.6, s > 0 ? hi - 1.0 : lo + 1.3, 17.2, 3.8, 6)
                    .box(s > 0 ? lo + 0.4 : hi - 0.7, 13.6, s > 0 ? lo + 0.7 : hi - 0.4, 17.2, 3.8, 6);
        }
        list.add(w);

        // ---- Mook Workshop katana: a long wrapped hilt with a silver butt, a squared black guard, a black blade
        // collar ruled in silver, then a long pale blade that sweeps up at the tip
        w = of("katana", row++, steel(0xd8dde4, 0xf8fafc), new int[]{0xffffff, 0xd0e8ff});
        w.mirror = true;
        w.bar(8, -7.6, -7, 1.4, 1.4, 4).bar(8, -7, 2, 1.2, 1.1, 0);
        wrap(w, -6.6, 1.6, 1.15);
        w.bar(8, 2, 2.8, 2.6, 1.8, 0).bar(8, 2.1, 2.7, 2.7, 1.9, 2).bar(8, 2.8, 3.3, 1.5, 0.8, 3);
        // blade collar
        w.bar(8, 3.3, 8, 1.35, 0.6, 0).box(8.3, 3.6, 8.5, 7.6, 0.65, 3).box(7.4, 5.5, 8.1, 5.75, 0.65, 3);
        for (double y = 8; y < 26; y += 1.0) {
            double k = Math.max(0, (y - 21) / 5), cx = 8 - 0.9 * k * k, wd = 1.15 - 0.55 * k;
            w.bar(cx, y, y + 1.0, wd, 0.35, 6).box(cx + wd / 2 - 0.22, y, cx + wd / 2, y + 1.0, 0.3, 7).box(cx - wd / 2, y, cx - wd / 2 + 0.2, y + 1.0, 0.38, 3);
        }
        w.bar(6.8, 26, 26.6, 0.45, 0.3, 7);
        list.add(w);

        // ---- Ranga Workshop gauntlet: worn, not held. Built in the arm's own pixels (y runs down the forearm to
        // the knuckles at y = 10, x = 8 / z = 8 is the arm's axis, +x the palm side, -z the thumb side): a plated
        // forearm under a crown of spikes, a silver wrist ring, an armoured back of the hand, and four jointed
        // fingers plus a thumb, each ending in a claw that hooks in toward the palm.
        w = of("gauntlet", row++, steel(), new int[]{0xe6e9ee, 0xffffff});
        w.raw = true;
        w.cube(5.3, 2.4, 5.3, 10.7, 6.6, 10.7, 0, false)                          // forearm shell
                .cube(5.15, 4.2, 5.15, 10.85, 4.5, 10.85, 3, false)                  // a silver seam round it
                .cube(5.0, 2.8, 6.4, 5.3, 4.0, 9.6, 3, false).cube(5.0, 4.8, 6.8, 5.3, 6.2, 9.2, 2, false)  // outer plates
                .cube(5.0, 1.8, 5.0, 11.0, 2.6, 11.0, 1, false);                     // flared cuff
        // crown of spikes at the elbow end, pointing back up the arm
        double[][] crown = {{5.0, 5.6, 7.6, 8.4}, {10.4, 11.0, 7.6, 8.4}, {7.6, 8.4, 5.0, 5.6}, {7.6, 8.4, 10.4, 11.0},
                {5.0, 5.7, 5.0, 5.7}, {10.3, 11.0, 5.0, 5.7}, {5.0, 5.7, 10.3, 11.0}, {10.3, 11.0, 10.3, 11.0}};
        for (int i = 0; i < crown.length; i++) {
            double[] c = crown[i];
            double len = i < 4 ? 1.6 : 1.0;
            w.cube(c[0], 1.8 - len, c[2], c[1], 1.8, c[3], 3, false)
                    .cube(c[0] + 0.15, 1.8 - len - 0.7, c[2] + 0.15, c[1] - 0.15, 1.8 - len, c[3] - 0.15, 4, false);
        }
        w.cube(5.0, 6.6, 5.0, 11.0, 7.2, 11.0, 3, false)                            // wrist ring
                .cube(5.4, 7.2, 5.4, 10.6, 10.2, 10.6, 0, false)                     // hand
                .cube(5.1, 7.5, 5.8, 5.4, 8.6, 10.2, 3, false).cube(5.1, 8.9, 5.8, 5.4, 9.9, 10.2, 2, false) // back plates
                .cube(5.2, 9.5, 5.4, 6.4, 10.3, 10.6, 3, false);                     // knuckle ridge
        // fingers across the hand (along z), each in three joints bending toward the palm, then a claw
        for (int i = 0; i < 4; i++) {
            double z = 5.75 + i * 1.3, s = i == 3 ? 0.85 : 1.0;
            w.cube(6.4, 10.2, z - 0.5, 7.8, 10.2 + 1.5 * s, z + 0.5, 0, false)
                    .cube(6.5, 10.2 + 1.2 * s, z - 0.55, 7.7, 10.2 + 1.5 * s, z + 0.55, 3, false)
                    .cube(7.0, 10.2 + 1.5 * s, z - 0.45, 8.3, 10.2 + 2.7 * s, z + 0.45, 0, false)
                    .cube(7.7, 10.2 + 2.7 * s, z - 0.36, 8.9, 10.2 + 3.7 * s, z + 0.36, 3, false)
                    .cube(8.5, 10.2 + 3.7 * s, z - 0.26, 9.6, 10.2 + 4.5 * s, z + 0.26, 4, false)
                    .cube(9.4, 10.2 + 4.3 * s, z - 0.16, 10.3, 10.2 + 4.9 * s, z + 0.16, 4, false);
        }
        // thumb on the front, hooking in the same way
        w.cube(7.6, 7.8, 4.2, 9.0, 9.4, 5.4, 0, false).cube(8.2, 9.2, 3.6, 9.4, 10.6, 4.4, 3, false)
                .cube(9.0, 10.4, 3.7, 10.0, 11.4, 4.2, 4, false);
        list.add(w);
        // the left hand is its mirror image (palm on -x, thumb still in front); it shares the palette row
        Weapon left = of("gauntlet_left", w.row, w.pal, w.glow);
        left.raw = true;
        for (Box b : w.boxes) left.boxes.add(new Box(16 - b.x1(), b.y0(), b.z0(), 16 - b.x0(), b.y1(), b.z1(), b.color(), b.glow()));
        list.add(left);

        // ---- Ranga Workshop combat knife: a short black hilt with a ring pommel and a small guard, a broad blade
        // with a sawtooth spine and a clipped point
        w = of("knife", row++, steel(0xd8dde4), new int[]{0xffffff, 0xd0e8ff});
        w.mirror = true;
        w.bar(8, -5.2, -4.4, 1.7, 1.0, 3).bar(8, -4.4, 1.9, 1.35, 1.35, 0);
        wrap(w, -3.8, 1.5, 1.35);
        w.bar(8, 1.9, 2.6, 2.8, 1.5, 1).bar(8, 2.6, 3.0, 2.0, 1.0, 3);
        for (double y = 3.0; y < 13; y += 0.5) {
            double k = (y - 3.0) / 10, wd = k < 0.7 ? 2.0 : 2.0 * (1 - (k - 0.7) / 0.3) + 0.2, cx = 8 + (k < 0.7 ? 0 : (k - 0.7) * 1.2);
            w.bar(cx, y, y + 0.5, wd, 0.4, 6).box(cx + wd / 2 - 0.25, y, cx + wd / 2, y + 0.5, 0.32, 4);
            // sawtooth spine
            if (k < 0.6) w.box(cx - wd / 2 - (((int) (y * 2)) % 2 == 0 ? 0.45 : 0.2), y, cx - wd / 2, y + 0.5, 0.45, 1);
        }
        w.box(7.6, 3.4, 8.0, 9.5, 0.45, 2);
        list.add(w);

        // ---- Zelkova Workshop axe: a wooden haft bound in black at both ends, a silver socket, a broad bearded
        // blade and a hooked back spike
        w = of("axe", row++, steel(0x4a3426, 0x6a4a34), new int[]{0xe6e9ee, 0xffffff});
        w.mirror = true;
        w.bar(8, -8, 13, 1.35, 1.25, 6).box(7.6, -7, 7.8, 12, 1.3, 7);
        wrap(w, -7.8, -4, 1.35);
        wrap(w, 6.5, 10.5, 1.35);
        w.bar(8, -8.6, -8, 1.5, 1.4, 1);
        // socket
        w.bar(8, 13, 18, 2.0, 1.9, 3).bar(8, 17.8, 18.4, 1.6, 1.5, 4);
        // bearded blade on +x: flat top, a long hooked beard, an edge that bulges out
        for (double y = 9.0; y < 18.2; y += 0.5) {
            double u = (y - 9.0) / 9.2;
            double edge = 13.4 + 1.9 * Math.sin(Math.PI * (0.1 + 0.85 * u));
            double inner = u < 0.55 ? 9.0 + 3.8 * Math.pow(1 - u / 0.55, 1.4) : 9.0;
            if (u < 0.05) inner = Math.max(inner, edge - 1.4);
            w.box(inner, y, edge - 0.6, y + 0.5, 0.5, 2).box(edge - 0.6, y, edge, y + 0.5, 0.34, 4);
        }
        w.box(10.0, 12.6, 11.4, 13.0, 0.56, 0).box(11.0, 14.2, 11.6, 14.8, 0.56, 0);
        // back spike on -x
        w.box(6.0, 15.0, 7.0, 16.6, 0.8, 3).box(4.6, 15.3, 6.0, 16.3, 0.6, 3).box(3.4, 15.6, 4.6, 16.0, 0.45, 4)
                .box(4.2, 16.3, 5.0, 16.9, 0.5, 3);
        list.add(w);

        // ---- Zelkova Workshop mace: a long wrapped staff, a silver collar, an elongated flanged head and a spike
        w = of("mace", row++, steel(), new int[]{0xe6e9ee, 0xffffff});
        w.bar(8, -11, -10.4, 1.15, 1.15, 3).bar(8, -10.4, 13, 1.05, 1.05, 0);
        wrap(w, -9.6, 11, 1.05);
        w.bar(8, 11.2, 11.8, 1.55, 1.55, 3).bar(8, 11.8, 13.2, 1.3, 1.3, 1).bar(8, 13.2, 13.8, 1.9, 1.9, 4);
        for (double y = 13.8; y < 23.4; y += 0.6) {
            double u = (y - 13.8) / 9.6, r = 0.6 + 2.7 * Math.sin(Math.PI * Math.pow(u, 0.75));
            w.bar(8, y, y + 0.6, 1.6, 1.6, 0);
            // four flanges: two in the plane, two front and back
            w.box(8 - r, y, 8 - 0.7, y + 0.6, 0.4, 3).box(8 + 0.7, y, 8 + r, y + 0.6, 0.4, 3)
                    .cube(7.8, y, 8 - r * 0.85, 8.2, y + 0.6, 8 - 0.7, 3, false).cube(7.8, y, 8 + 0.7, 8.2, y + 0.6, 8 + r * 0.85, 3, false)
                    .box(8 - r, y, 8 - r + 0.25, y + 0.6, 0.45, 4).box(8 + r - 0.25, y, 8 + r, y + 0.6, 0.45, 4);
        }
        w.bar(8, 23.4, 24.4, 0.9, 0.9, 3).bar(8, 24.4, 25.6, 0.4, 0.4, 4);
        list.add(w);

        // ---- Wheels Industry greatsword: a massive black slab on an industrial frame, a polished silver spine,
        // pistons set into the flat, two cogwheels at the root and a third for a pommel
        w = of("greatsword", row++, steel(0x5c6068), new int[]{0xe6e9ee, 0xffffff});
        gear(w, 8, -12.2, 1.5, 2.2);
        w.bar(8, -10.6, 3, 1.6, 1.6, 0);
        wrap(w, -9.8, 1.6, 1.6);
        w.bar(8, 2.4, 3.0, 2.2, 2.2, 3);
        // root block with two cogs
        w.box(3.6, 3, 12.6, 9, 2.4, 1).box(3.4, 3.4, 3.8, 8.6, 2.2, 3);
        gear(w, 10.4, 4.6, 2.6, 1.6);
        gear(w, 10.6, 7.9, 2.6, 1.15);
        // the slab
        w.box(4.0, 9, 11.8, 37, 1.9, 0)
                .box(11.8, 9, 12.8, 37, 1.5, 4)                                       // polished spine
                .box(3.4, 9, 4.0, 36.4, 1.2, 2)                                       // edge
                .box(5.0, 10.5, 6.8, 22.5, 2.3, 3).box(5.3, 10.8, 6.5, 22.2, 2.5, 4)  // lower piston
                .box(5.0, 24.5, 6.8, 34.0, 2.3, 3).box(5.3, 24.8, 6.5, 33.7, 2.5, 4)  // upper piston
                .box(7.4, 11, 11.2, 34.5, 2.05, 1)                                    // recessed panel
                .box(7.4, 22.8, 11.2, 24.2, 2.15, 2);
        w.box(4.6, 37, 12.6, 38.6, 1.7, 2).box(5.8, 38.6, 12.0, 39.4, 1.5, 3).box(3.6, 3.6, 4.4, 5.2, 2.6, 2);
        list.add(w);

        // ---- Crystal Atelier short sword: a black hilt with an angular winged guard, and a curved silver blade
        // veined with light that sweeps up at the tip
        w = of("shortsword", row++, steel(0xd8dde4), new int[]{0xbff4ff, 0xffffff});
        w.bar(8, -4.2, -3.4, 1.2, 1.2, 1).bar(8, -3.4, 2, 1.1, 1.1, 0);
        wrap(w, -3, 1.6, 1.1);
        w.bar(8, 2, 2.9, 2.2, 1.4, 0).box(5.6, 2.3, 6.9, 3.4, 1.2, 1).box(9.1, 1.6, 10.4, 2.7, 1.2, 1).bar(8, 2.9, 3.4, 1.6, 0.9, 3);
        for (double y = 3.4; y < 16.6; y += 0.6) {
            double k = (y - 3.4) / 13.2, wd = 1.9 - 1.5 * k * k, cx = 8 + 1.6 * k * k * k;
            w.bar(cx, y, y + 0.6, wd, 0.4, 6).box(cx + wd / 2 - 0.25, y, cx + wd / 2, y + 0.6, 0.32, 4);
            // the veins: a light line that wanders across the flat
            double vx = cx + Math.sin(y * 1.7) * wd * 0.25;
            w.glowBox(vx - 0.1, y, vx + 0.1, y + 0.6, 0.46, (int) (y * 2) % 2);
        }
        list.add(w);

        // ---- Atelier Logic shotgun: a slotted silver muzzle shroud on a long black barrel, a receiver with a
        // silver flash along the top, a trigger, and a long stock ending in a silver plate
        w = of("shotgun", row++, steel(), new int[]{0xe6e9ee, 0xffffff});
        w.gun = true;
        w.cube(7.1, 6.6, -14, 8.9, 8.4, -11, 3, false);                                // shroud
        for (int i = 0; i < 6; i++) w.cube(7.0, 6.9 + i * 0.22, -13.8 + i * 0.45, 9.0, 7.0 + i * 0.22, -13.5 + i * 0.45, 0, false);
        for (int i = 0; i < 6; i++) w.cube(7.05, 6.7, -13.8 + i * 0.48, 8.95, 8.3, -13.55 + i * 0.48, 0, false);
        w.cube(7.3, 6.8, -11, 8.7, 8.2, 1, 0, false)                                       // barrel
                .cube(7.25, 7.3, -9, 8.75, 7.6, -8.6, 2, false).cube(7.25, 7.3, -7.8, 8.75, 7.6, -7.4, 2, false)
                .cube(7.2, 6.3, -6, 8.8, 6.8, 1, 1, false)                                 // fore-end
                .cube(7.0, 6.2, 1, 9.0, 8.7, 6.2, 0, false)                                // receiver
                .cube(7.6, 8.5, -3.2, 8.4, 8.75, 3.5, 4, false).cube(7.55, 8.4, 3.0, 8.45, 8.8, 4.2, 3, false) // silver flash
                .cube(6.95, 7.3, 1.6, 7.0, 7.6, 5.6, 3, false).cube(9.0, 7.3, 1.6, 9.05, 7.6, 5.6, 3, false)
                .cube(7.75, 5.0, 3.0, 8.25, 6.2, 3.3, 3, false).cube(7.75, 4.9, 3.0, 8.25, 5.15, 5.0, 3, false)
                .cube(7.85, 5.3, 4.0, 8.15, 6.2, 4.3, 0, false);                           // trigger
        // stock, dropping a little toward the butt, wrapped
        w.cube(7.2, 5.0, 6.2, 8.8, 8.4, 9.5, 0, false).cube(7.2, 4.2, 9.5, 8.8, 7.9, 13, 0, false)
                .cube(7.2, 3.6, 13, 8.8, 7.6, 15.4, 0, false).cube(7.1, 3.4, 15.4, 8.9, 7.8, 16, 3, false);
        for (int i = 0; i < 4; i++) w.cube(7.15, 4.3 - i * 0.15, 9.8 + i * 1.3, 8.85, 7.85 - i * 0.07, 10.3 + i * 1.3, 5, false);
        list.add(w);

        // ---- Durandal: Roland's longsword, dark steel with brighter edges and a long fuller, a wide crossguard with
        // turned-down tips, a long wrapped grip and a small pommel
        w = of("durandal", row++, steel(0x34363d, 0x5a5e68), new int[]{0xd8ecff, 0xffffff});
        w.bar(8, -8.2, -7.4, 1.4, 1.4, 2).bar(8, -7.4, 2, 1.15, 1.15, 0);
        wrap(w, -7, 1.6, 1.15);
        w.bar(8, 2, 2.8, 7.0, 1.2, 2).bar(8, 2.1, 2.7, 1.8, 1.5, 3)
                .box(4.3, 1.4, 4.9, 2.8, 1.2, 3).box(11.1, 1.4, 11.7, 2.8, 1.2, 3)        // turned-down tips
                .box(4.2, 1.0, 4.7, 1.4, 1.1, 2).box(11.3, 1.0, 11.8, 1.4, 1.1, 2);
        for (double y = 2.8; y < 29; y += 1.0) {
            double k = Math.max(0, (y - 24) / 5), wd = 1.9 * (1 - k) + 0.25;
            w.bar(8, y, y + 1.0, wd, 0.42, 6).box(8 + wd / 2 - 0.25, y, 8 + wd / 2, y + 1.0, 0.34, 7).box(8 - wd / 2, y, 8 - wd / 2 + 0.25, y + 1.0, 0.34, 7);
            if (y < 23) w.bar(8, y, y + 1.0, 0.35, 0.48, 0);
        }
        w.glowBar(8, 4, 26, 0.1, 0.5, 0);
        list.add(w);

        // ---- the sheaths Roland draws from: Mook's katana sheath (black, ruled with angular silver lines) and
        // Durandal's, wrapped in straps marked with white sigils, tapering to a point. Mouth at the bottom
        // (y = 0 is where the hand holds it), tip at the top.
        w = of("katana_sheath", row++, steel(), new int[]{0xe6e9ee, 0xffffff});
        w.bar(8, -1.2, 1.2, 2.0, 1.2, 1).bar(8, -1.3, -0.9, 2.1, 1.3, 3).box(6.6, 0.2, 7.0, 1.0, 1.0, 3)
                .bar(8, 1.2, 25.5, 1.75, 0.95, 0).bar(8, 25.5, 26.6, 1.55, 0.9, 2);
        for (double y = 6; y < 25; y += 6.5) w.box(6.95, y, 9.05, y + 0.3, 1.0, 3).box(8.6, y + 0.3, 9.05, y + 1.4, 1.0, 3).box(6.95, y - 1.1, 7.4, y, 1.0, 3);
        list.add(w);

        w = of("durandal_sheath", row++, steel(0x2f2e33, 0xf4f4f4), new int[]{0xffffff, 0xe0e8ff});
        w.bar(8, -1.4, 0.4, 2.6, 1.5, 1).box(6.9, -1.4, 7.4, -0.8, 1.6, 2).box(8.6, -1.4, 9.1, -0.8, 1.6, 2);
        for (double y = 0.4; y < 27; y += 1.0) {
            double k = (y - 0.4) / 26.6, wd = 2.4 * (1 - k) + 0.45;
            w.bar(8, y, y + 1.0, wd, 1.2 * (1 - k * 0.5), 0);
        }
        // diagonal straps near the mouth, with white sigils on them
        for (int i = 0; i < 5; i++) w.box(6.7, 1.5 + i * 1.5, 9.3, 2.1 + i * 1.5, 1.3, 6);
        w.box(7.6, 2.0, 8.6, 2.6, 1.35, 7).box(7.2, 4.1, 8.4, 4.6, 1.35, 7).box(7.8, 6.4, 8.8, 7.0, 1.35, 7);
        list.add(w);
        return row;
        }

        /** A cogwheel facing sideways (axis along z): a dark hub, a silver rim and teeth, centred on (cx, cy). */
        static void gear(Weapon w, double cx, double cy, double t, double r) {
        w.box(cx - r * 0.7, cy - r * 0.7, cx + r * 0.7, cy + r * 0.7, t, 3).box(cx - r * 0.35, cy - r * 0.35, cx + r * 0.35, cy + r * 0.35, t + 0.1, 0);
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4, x = cx + Math.cos(a) * r * 0.85, y = cy + Math.sin(a) * r * 0.85, s = r * 0.22;
            w.box(x - s, y - s, x + s, y + s, t * 0.9, 4);
        }
        }
    }

    static final int CELL = 8, GRID = 16, FRAMES = 8;
    /** Models are authored long; shrink them about the grip (y = 0) so the tip stays on screen in first person. */
    static final double SCALE = 0.72, SCALE_Z = 0.85;

    static double sx(double x) { return 8 + (x - 8) * SCALE; }
    static double sy(double y) { return y * SCALE; }
    static double sz(double z) { return 8 + (z - 8) * SCALE_Z; }

    public static void main(String[] args) throws IOException {
        Path res = Path.of(args.length > 0 ? args[0] : "src/main/resources");
        Path models = res.resolve("assets/projectminecraft/models/item");
        Path tex = res.resolve("assets/projectminecraft/textures/item");
        Files.createDirectories(models.resolve("3d"));
        Files.createDirectories(tex);
        List<Weapon> list = weapons();

        // 16 rows per palette sheet (rows can skip: they match Random Slop's)
        int sheets = 1 + list.stream().mapToInt(w -> w.row).max().orElse(0) / GRID;
        BufferedImage[] pal = new BufferedImage[sheets], glow = new BufferedImage[sheets];
        for (int s = 0; s < sheets; s++) {
            pal[s] = new BufferedImage(CELL * GRID, CELL * GRID, BufferedImage.TYPE_INT_ARGB);
            glow[s] = new BufferedImage(CELL * GRID, CELL * GRID * FRAMES, BufferedImage.TYPE_INT_ARGB);
        }
        for (Weapon w : list) {
            for (int i = 0; i < w.pal.length; i++) fill(pal[w.sheet()], i, w.localRow(), 0, w.pal[i], 1.0);
            for (int f = 0; f < FRAMES; f++) {
                double k = 0.78 + 0.22 * Math.cos(f * Math.PI * 2 / FRAMES);
                for (int i = 0; i < w.glow.length; i++) fill(glow[w.sheet()], i, w.localRow(), f, w.glow[i], k);
            }
            if (w.folder != null) {
                Path dir = res.resolve("assets/projectminecraft/models/" + w.folder);
                Files.createDirectories(dir);
                Files.writeString(dir.resolve(w.name + ".json"), model3d(w));
                continue;
            }
            Files.writeString(models.resolve("3d/" + w.name + ".json"), model3d(w));
            Files.writeString(models.resolve(w.name + ".json"), wrapper(w));
            // hidden in the hand (thrown, sheathed) but still drawn in the inventory
            if (w.hideWhen != null) Files.writeString(models.resolve(w.name + "_hidden.json"), hidden(w));
        }
        for (int s = 0; s < sheets; s++) {
            final int sheet = s;
            if (list.stream().noneMatch(w -> w.sheet() == sheet)) continue; // sheet 0 is all Random Slop's
            ImageIO.write(pal[s], "png", tex.resolve(sheetName("weapon_palette", s) + ".png").toFile());
            ImageIO.write(glow[s], "png", tex.resolve(sheetName("weapon_glow", s) + ".png").toFile());
            Files.writeString(tex.resolve(sheetName("weapon_glow", s) + ".png.mcmeta"), "{ \"animation\": { \"frametime\": 2, \"interpolate\": true } }\n");
        }
        // what a thrown axe or a sheathed katana looks like in your hand: nothing
        Files.writeString(models.resolve("empty.json"), "{ \"textures\": { \"particle\": \"projectminecraft:item/weapon_palette_2\" }, \"elements\": [] }\n");
        System.out.println("wrote " + list.size() + " weapon models");
    }

    static String sheetName(String base, int sheet) {
        return sheet == 0 ? base : base + "_" + (sheet + 1);
    }

    static void fill(BufferedImage img, int col, int row, int frame, int rgb, double k) {
        int r = (int) Math.min(255, ((rgb >> 16) & 255) * k), g = (int) Math.min(255, ((rgb >> 8) & 255) * k), b = (int) Math.min(255, (rgb & 255) * k);
        for (int y = 0; y < CELL; y++) for (int x = 0; x < CELL; x++) {
            img.setRGB(col * CELL + x, frame * CELL * GRID + row * CELL + y, 0xFF000000 | (r << 16) | (g << 8) | b);
        }
    }

    static String n(double v) {
        String s = String.format(Locale.ROOT, "%.3f", v);
        s = s.replaceAll("0+$", "");
        return s.endsWith(".") ? s.substring(0, s.length() - 1) : s;
    }

    static String model3d(Weapon w) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n  \"textures\": {\n    \"pal\": \"projectminecraft:item/").append(sheetName("weapon_palette", w.sheet()))
                .append("\",\n    \"glow\": \"projectminecraft:item/").append(sheetName("weapon_glow", w.sheet())).append("\",\n")
                .append("    \"particle\": \"projectminecraft:item/").append(w.folder != null ? sheetName("weapon_palette", w.sheet()) : w.name).append("\"\n  },\n");
        sb.append(w.gun ? GUN_DISPLAY : DISPLAY);
        sb.append("  \"elements\": [\n");
        for (int i = 0; i < w.boxes.size(); i++) {
            Box b = w.boxes.get(i);
            double u0 = b.color + 0.25, v0 = w.localRow() + 0.25, u1 = b.color + 0.75, v1 = w.localRow() + 0.75;
            String uv = "[" + n(u0) + ", " + n(v0) + ", " + n(u1) + ", " + n(v1) + "]";
            String t = b.glow ? "#glow" : "#pal";
            if (w.gun || w.raw) {
                sb.append("    { \"from\": [").append(n(b.x0)).append(", ").append(n(b.y0)).append(", ").append(n(b.z0)).append("], ")
                        .append("\"to\": [").append(n(b.x1)).append(", ").append(n(b.y1)).append(", ").append(n(b.z1)).append("],\n");
            } else {
                double x0 = w.mirror ? 16 - b.x1 : b.x0, x1 = w.mirror ? 16 - b.x0 : b.x1;
                sb.append("    { \"from\": [").append(n(sx(x0))).append(", ").append(n(sy(b.y0 + w.shiftY))).append(", ").append(n(sz(b.z0))).append("], ")
                        .append("\"to\": [").append(n(sx(x1))).append(", ").append(n(sy(b.y1 + w.shiftY))).append(", ").append(n(sz(b.z1))).append("],\n")
                        .append("      \"rotation\": { \"angle\": -45, \"axis\": \"z\", \"origin\": [8, 8, 8] },\n");
            }
            if (b.glow) sb.append("      \"shade\": false, \"neoforge_data\": { \"block_light\": 15, \"sky_light\": 15 },\n");
            sb.append("      \"faces\": {");
            String[] dirs = {"north", "east", "south", "west", "up", "down"};
            for (int d = 0; d < dirs.length; d++) {
                sb.append(" \"").append(dirs[d]).append("\": { \"uv\": ").append(uv).append(", \"texture\": \"").append(t).append("\" }").append(d < dirs.length - 1 ? "," : "");
            }
            sb.append(" } }").append(i < w.boxes.size() - 1 ? "," : "").append("\n");
        }
        sb.append("  ]\n}\n");
        return sb.toString();
    }

    static String hidden(Weapon w) {
        String flat = "{ \"parent\": \"minecraft:item/handheld\", \"textures\": { \"layer0\": \"projectminecraft:item/" + w.name + "\" } }";
        return "{\n  \"loader\": \"neoforge:separate_transforms\",\n  \"gui_light\": \"front\",\n"
                + "  \"base\": { \"parent\": \"projectminecraft:item/empty\" },\n"
                + "  \"perspectives\": {\n    \"gui\": " + flat + ",\n    \"fixed\": " + flat + "\n  }\n}\n";
    }

    static String wrapper(Weapon w) {
        String flat = "{ \"parent\": \"minecraft:item/handheld\", \"textures\": { \"layer0\": \"projectminecraft:item/" + w.name + "\" } }";
        return "{\n  \"loader\": \"neoforge:separate_transforms\",\n  \"gui_light\": \"front\",\n"
                + "  \"base\": { \"parent\": \"projectminecraft:item/" + (w.worn ? "empty" : "3d/" + w.name) + "\" },\n"
                + "  \"perspectives\": {\n    \"gui\": " + flat + ",\n    \"fixed\": " + flat + "\n  }"
                + (w.hideWhen == null ? "" : ",\n  \"overrides\": [ { \"predicate\": { \"" + w.hideWhen + "\": 1 }, \"model\": \"projectminecraft:item/" + w.name + "_hidden\" } ]")
                + "\n}\n";
    }

    // guns point the barrel (-z) away from the player in first person and down the arm in third person
    static final String GUN_DISPLAY = """
              "display": {
                "thirdperson_righthand": { "rotation": [0, 0, 0], "translation": [0, 2.5, 3], "scale": [0.55, 0.55, 0.55] },
                "thirdperson_lefthand": { "rotation": [0, 0, 0], "translation": [0, 2.5, 3], "scale": [0.55, 0.55, 0.55] },
                "firstperson_righthand": { "rotation": [0, 0, 0], "translation": [3.5, -1.5, -3], "scale": [0.65, 0.65, 0.65] },
                "firstperson_lefthand": { "rotation": [0, 0, 0], "translation": [3.5, -1.5, -3], "scale": [0.65, 0.65, 0.65] },
                "ground": { "rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.4, 0.4, 0.4] },
                "head": { "rotation": [0, 180, 0], "translation": [0, 13, 7], "scale": [1, 1, 1] },
                "fixed": { "rotation": [0, 90, 0], "scale": [0.6, 0.6, 0.6] },
                "gui": { "rotation": [30, 225, 0], "scale": [0.5, 0.5, 0.5] }
              },
            """;

    // vanilla item/generated + item/handheld display transforms (the models are laid out like the sprite)
    static final String DISPLAY = """
              "display": {
                "thirdperson_righthand": { "rotation": [0, -90, 55], "translation": [0, 4.0, 0.5], "scale": [0.85, 0.85, 0.85] },
                "thirdperson_lefthand": { "rotation": [0, 90, -55], "translation": [0, 4.0, 0.5], "scale": [0.85, 0.85, 0.85] },
                "firstperson_righthand": { "rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68] },
                "firstperson_lefthand": { "rotation": [0, 90, -25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68] },
                "ground": { "rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5] },
                "head": { "rotation": [0, 180, 0], "translation": [0, 13, 7], "scale": [1, 1, 1] },
                "fixed": { "rotation": [0, 180, 0], "scale": [1, 1, 1] },
                "gui": { "rotation": [30, 225, 0], "scale": [0.625, 0.625, 0.625] }
              },
            """;
}
