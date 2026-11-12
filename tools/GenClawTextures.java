import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Random;
import javax.imageio.ImageIO;

/**
 * The Claw (Baral): his skin (64x64 player layout, wide arms) painted after the user's skin renders, and the sheet for his
 * extra parts (the three vial antennae, the plated claw arm, the blades). Also the item sprites for what he drops.
 *
 * Beaten, two more skins: torn and bloodied with the claw arm wrecked (claw_broken), then the mask split open as well, a
 * chunk of his head showing through (claw_cracked).
 *
 * Usage: java tools/GenClawTextures.java <resources dir> [skin|extras|items] (items reads the skin for the worn mask)
 */
public class GenClawTextures {
    static final Random R = new Random(1337);
    static BufferedImage img;
    static final String TEX = "assets/projectminecraft/textures/";

    public static void main(String[] args) throws IOException {
        String res = args.length > 0 ? args[0] : "src/main/resources";
        String only = args.length > 1 ? args[1] : "";
        if (only.isEmpty() || only.equals("skin")) {
            write(res, TEX + "entity/claw/claw.png", 64, 64, GenClawTextures::skin);
            write(res, TEX + "entity/claw/claw_broken.png", 64, 64, () -> {
                skin();
                beaten();
            });
            write(res, TEX + "entity/claw/claw_cracked.png", 64, 64, () -> {
                skin();
                beaten();
                shattered();
            });
        }
        if (only.isEmpty() || only.equals("extras")) write(res, TEX + "entity/claw/claw_extras.png", 64, 64, GenClawTextures::extras);
        if (only.isEmpty() || only.equals("items")) items(res);
        if (only.isEmpty() || only.equals("rebuilt")) rebuilt(res);
    }

    // ------------------------------------------------------------------ the rebuilt Claw (the rematch)

    /**
     * Rebuilt: the same man, the arm replaced with polished gunmetal (claw_rebuilt), the plates and blades bright steel and
     * the vials' glass a stronger colour (claw_extras_rebuilt), and his eyes burning behind the mask's holes (claw_eyes).
     */
    static void rebuilt(String res) throws IOException {
        write(res, TEX + "entity/claw/claw_rebuilt.png", 64, 64, () -> {
            skin();
            R.setSeed(4040);
            // the prosthetic left arm: polished dark steel, a lit edge down each face, plate seams
            part(32, 48, 4, 12, 4, 0x3A3E46, 6);
            for (int y = 0; y < 12; y += 3) fill(32, 52 + y, 16, 1, 0x1E2026, 2);
            for (int x : new int[]{33, 37, 41, 45}) for (int y = 52; y < 64; y++) px(x, y, 0x8A92A0);
        });
        write(res, TEX + "entity/claw/claw_extras_rebuilt.png", 64, 64, () -> {
            R.setSeed(4041);
            extras();
            // stronger doses: the glass brighter
            fill(32, 0, 16, 16, 0x5CFF6C, 8);
            fill(48, 0, 16, 16, 0x4A8CFF, 8);
            fill(0, 16, 16, 16, 0xFFB43A, 8);
            // the plates: gunmetal, polished, bright seams
            fill(16, 16, 32, 24, 0x40444C, 6);
            for (int y = 16; y < 40; y += 4) fill(16, y, 32, 1, 0x9AA2B0, 4);
            for (int i = 0; i < 30; i++) px(16 + R.nextInt(32), 16 + R.nextInt(24), 0xC8D0DC);
            // the blades: bright steel, clean
            for (int y = 0; y < 16; y++) fill(48, 16 + y, 16, 1, y % 4 == 0 ? 0xE0E6EE : 0xA8B0BC, 4);
        });
        write(res, TEX + "entity/claw/claw_eyes.png", 64, 64, () -> {
            // the two holes at eye height on the mask's face (8 + 2..5, 8 + 2..3), burning cold blue-white
            for (int[] h : new int[][]{{2, 2}, {5, 2}, {1, 3}, {6, 3}}) img.setRGB(8 + h[0], 8 + h[1], h[1] == 2 ? 0xFFB8E4FF : 0xFF3A7AFF);
        });
    }

    static void write(String res, String path, int w, int h, Runnable paint) throws IOException {
        img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        paint.run();
        File out = new File(res, path);
        out.getParentFile().mkdirs();
        ImageIO.write(img, "png", out);
        System.out.println("wrote " + out);
    }

    // ------------------------------------------------------------------ helpers

    static void px(int x, int y, int rgb) {
        img.setRGB(x, y, 0xFF000000 | rgb);
    }

    static void fill(int x, int y, int w, int h, int rgb, int noise) {
        for (int j = 0; j < h; j++) for (int i = 0; i < w; i++) px(x + i, y + j, shade(rgb, noise == 0 ? 0 : R.nextInt(noise * 2 + 1) - noise));
    }

    static int shade(int rgb, int d) {
        int r = clamp(((rgb >> 16) & 255) + d), g = clamp(((rgb >> 8) & 255) + d), b = clamp((rgb & 255) + d);
        return (r << 16) | (g << 8) | b;
    }

    static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    /** The whole net of a box part (u, v = top-left of its net), one colour with noise. */
    static void part(int u, int v, int w, int h, int d, int rgb, int noise) {
        fill(u + d, v, w * 2, d, shade(rgb, 6), noise);
        fill(u, v + d, d * 2 + w * 2, h, rgb, noise);
    }

    // ------------------------------------------------------------------ the skin

    static final int BARK = 0x3A2C24, COAT = 0x262628, COAT_DARK = 0x1C1C1E, SHIRT = 0xC8C8C8, BUTTON = 0x545458, TROUSERS = 0x1E1E20,
            SHOE = 0x0E0E10, RUST = 0x3E2E24, BELT = 0x161618, BUCKLE = 0xA4ACAC;

    static void skin() {
        // the same noise every time (the beaten skins are painted over this one)
        R.setSeed(1337);
        // the mask: a block of dark bark, holes punched through it, darker at the back
        part(0, 0, 8, 8, 8, BARK, 9);
        fill(24, 8, 8, 8, 0x14100E, 4);
        int[][] holes = {{2, 2}, {5, 2}, {1, 3}, {6, 3}, {2, 5}, {5, 5}, {1, 6}, {3, 6}, {4, 6}, {6, 6}, {2, 7}, {5, 7}};
        for (int[] h : holes) px(8 + h[0], 8 + h[1], 0x050505);
        for (int i = 0; i < 6; i++) px(8 + 1 + R.nextInt(6), 8 + R.nextInt(8), 0x5A4636); // lighter grain
        px(8 + 3, 8 + 4, 0x5A4636);
        px(8 + 4, 8 + 4, 0x5A4636);

        // the coat: dark, a seam of buttons down the middle, the shirt's collar at the neck, a belt
        part(16, 16, 8, 12, 4, COAT, 5);
        int fx = 20, fy = 20;
        fill(fx + 2, fy, 1, 2, SHIRT, 5);
        fill(fx + 5, fy, 1, 2, SHIRT, 5);
        fill(fx + 3, fy, 2, 1, COAT_DARK, 0);
        for (int y = 2; y < 12; y++) px(fx + 3, fy + y, COAT_DARK);
        for (int y : new int[]{3, 5, 9, 11}) px(fx + 4, fy + y, BUTTON);
        fill(fx, fy + 7, 8, 1, BELT, 0);
        fill(fx + 3, fy + 7, 2, 1, BUCKLE, 0);
        fill(32, 27, 8, 1, BELT, 0); // the belt round the back

        // his right arm: a coat sleeve; his left: the prosthetic, rust and plates
        part(40, 16, 4, 12, 4, COAT, 5);
        fill(44, 30, 4, 2, 0x2C2C2E, 3);
        part(32, 48, 4, 12, 4, RUST, 12);
        for (int y = 0; y < 12; y += 3) fill(32, 52 + y, 16, 1, 0x2A1E18, 3);

        // the coat's skirt over the legs, then trousers and shoes
        for (int[] leg : new int[][]{{0, 16}, {16, 48}}) {
            part(leg[0], leg[1], 4, 12, 4, TROUSERS, 4);
            fill(leg[0], leg[1] + 4, 16, 5, COAT, 5);
            fill(leg[0], leg[1] + 9, 16, 1, COAT_DARK, 0);
            fill(leg[0], leg[1] + 14, 16, 2, SHOE, 2);
        }
        for (int y : new int[]{1, 3}) px(4 + 3, 20 + y, BUTTON);
    }

    static final int BLOOD = 0x5A0E0E, BLOOD_DARK = 0x3A0808, FLESH = 0x7A2A26, FLESH_PALE = 0x9A4A42, STEEL = 0x8A8C92;

    /** Beaten: the coat slashed and bloodied, the mask cracked, the prosthetic arm torn open to flesh, steel and wire. */
    static void beaten() {
        // a slash across the coat, the shirt showing through, blood soaking out from it
        int[][] slash = {{20, 21}, {21, 22}, {22, 23}, {23, 23}, {24, 24}, {25, 25}, {26, 26}};
        for (int[] s : slash) {
            px(s[0], s[1], 0x8A8A8A);
            px(s[0], s[1] + 1, BLOOD);
        }
        for (int i = 0; i < 14; i++) px(20 + R.nextInt(8), 24 + R.nextInt(8), R.nextBoolean() ? BLOOD : BLOOD_DARK);
        for (int i = 0; i < 6; i++) px(32 + R.nextInt(8), 20 + R.nextInt(10), BLOOD_DARK); // the back
        // his good sleeve torn at the cuff, a cut on it
        fill(44, 28, 4, 1, 0x8A8A8A, 0);
        px(41, 24, BLOOD);
        px(42, 25, BLOOD);
        // cracks across the mask, a chunk gone at the corner
        int[][] crack = {{9, 8}, {10, 9}, {10, 10}, {11, 11}, {12, 11}, {13, 12}, {13, 13}, {14, 14}};
        for (int[] c : crack) px(c[0], c[1], 0x080606);
        fill(14, 8, 2, 2, FLESH, 6);
        // the prosthetic: plates cracked on the upper arm; below the elbow torn open, flesh and steel and wire
        for (int y = 52; y < 58; y++) if (R.nextInt(3) == 0) px(32 + R.nextInt(16), y, 0x0C0808);
        fill(32, 58, 16, 6, FLESH, 10);
        for (int y = 58; y < 64; y += 2) for (int x = 32; x < 48; x++) if (R.nextInt(3) == 0) px(x, y, FLESH_PALE);
        for (int y = 57; y < 64; y++) px(37, y, STEEL); // the rod they built into him, down the front
        for (int y = 59; y < 64; y++) px(45, y, STEEL);
        px(34, 60, 0x101010);
        px(34, 61, 0x101010);
        px(41, 59, 0xB01818);
        px(41, 60, 0xB01818);
        px(43, 62, 0xD8B020);
        for (int i = 0; i < 5; i++) px(32 + R.nextInt(16), 57, BLOOD_DARK); // the ragged edge
        // the end of the arm: a ring of meat round the rods
        fill(40, 48, 4, 4, FLESH, 8);
        px(41, 49, STEEL);
        px(42, 50, STEEL);
    }

    /**
     * The mask split open (nobody knows his face, and nobody sees it): cracks all across it, and a chunk broken out of the
     * corner of his head, torn flesh in the hole with a sliver of the steel they plated his skull with, blood running down.
     */
    static void shattered() {
        // more cracks, running out from the hole
        int[][] cracks = {{12, 9}, {13, 10}, {14, 11}, {15, 12}, {11, 12}, {10, 13}, {9, 14}, {12, 13}, {13, 14}, {12, 15}, {8, 12}, {9, 12}};
        for (int[] c : cracks) px(c[0], c[1], 0x080606);
        // the hole: the top corner of the front, over onto the top and the side of his head
        int[][] hole = {{8, 8}, {9, 8}, {10, 8}, {11, 8}, {8, 9}, {9, 9}, {10, 9}, {8, 10}, {9, 10}, {8, 11},
                {8, 5}, {9, 5}, {8, 6}, {9, 6}, {10, 6}, {8, 7}, {9, 7}, {10, 7}, {11, 7},
                {5, 8}, {6, 8}, {7, 8}, {6, 9}, {7, 9}, {7, 10}};
        for (int[] h : hole) px(h[0], h[1], R.nextInt(3) == 0 ? FLESH_PALE : FLESH);
        // the steel under it, and the broken rim of the bark
        px(9, 8, STEEL);
        px(10, 8, STEEL);
        px(9, 6, STEEL);
        int[][] rim = {{12, 8}, {11, 9}, {10, 10}, {9, 11}, {8, 12}, {12, 7}, {11, 6}, {11, 5}, {5, 9}, {6, 10}};
        for (int[] r : rim) px(r[0], r[1], 0x5A4636);
        // blood running down from it over the mask
        int[][] blood = {{9, 12}, {9, 13}, {10, 14}, {8, 13}, {8, 14}, {8, 15}, {7, 11}, {7, 12}};
        for (int[] b : blood) px(b[0], b[1], BLOOD);
        // the second chunk missing from the corner (beaten's) goes deeper
        fill(14, 8, 2, 3, FLESH, 6);
        px(15, 9, STEEL);
    }

    // ------------------------------------------------------------------ the extras sheet

    /** Regions (16x16 unless noted): stick (0,0), wire (16,0), K green (32,0), W blue (48,0), R orange (0,16), rust plates
     *  (16,16) 32x24, blades (48,16); the wreck (8x8): black wire (0,32), red wire (8,32), yellow wire (0,40), steel (8,40),
     *  flesh (0,48), broken glass (8,48). */
    static void extras() {
        fill(0, 0, 16, 16, 0x4A3A2E, 8);
        fill(16, 0, 16, 16, 0x0C0C0C, 2);
        fill(32, 0, 16, 16, 0x3CD04C, 10);
        fill(48, 0, 16, 16, 0x2A5CE0, 10);
        fill(0, 16, 16, 16, 0xE8A020, 10);
        fill(16, 16, 32, 24, 0x4A3628, 14);
        for (int y = 16; y < 40; y += 4) fill(16, y, 32, 1, 0x2A1E16, 4);
        for (int i = 0; i < 30; i++) px(16 + R.nextInt(32), 16 + R.nextInt(24), 0x7A6048);
        // what's left once he's broken: torn wires, the steel inside him, raw flesh, glass shards
        fill(0, 32, 8, 8, 0x101010, 3);
        fill(8, 32, 8, 8, 0x9A1414, 8);
        fill(0, 40, 8, 8, 0xC8A020, 8);
        fill(8, 40, 8, 8, 0x8A8C92, 10);
        fill(0, 48, 8, 8, 0x7A2A26, 12);
        for (int i = 0; i < 12; i++) px(R.nextInt(8), 48 + R.nextInt(8), 0x9A4A42);
        fill(8, 48, 8, 8, 0xBFE4EE, 10);
        // blades: dark steel going to dried blood at the tips
        for (int y = 0; y < 16; y++) {
            int c = y < 8 ? 0x2A2420 : y < 12 ? 0x4A1E18 : 0x6A1410;
            fill(48, 16 + y, 16, 1, c, 4);
        }
    }

    // ------------------------------------------------------------------ item sprites (16x16)

    static void items(String res) throws IOException {
        write(res, TEX + "item/black_silence_book.png", 16, 16, () -> book(0x141414, 0xC8A040));
        write(res, TEX + "item/claw_mask.png", 16, 16, GenClawTextures::mask);
        maskWorn(res);
        write(res, TEX + "item/broken_claw.png", 16, 16, () -> claw(true));
        write(res, TEX + "item/claw_arm.png", 16, 16, () -> claw(false));
        write(res, TEX + "item/handmade_claw.png", 16, 16, () -> {
            claw(false);
            // the three vials, small, along the forearm
            pip(2, 7, 0x3CD04C);
            pip(4, 5, 0x2A5CE0);
            pip(6, 3, 0xE8A020);
        });
        int[] colors = {0x3CD04C, 0xE8A020, 0x2A5CE0};
        String[] names = {"k", "r", "w"};
        for (int i = 0; i < 3; i++) {
            int c = colors[i];
            write(res, TEX + "item/serum_" + names[i] + ".png", 16, 16, () -> vial(6, 2, c, false));
            write(res, TEX + "item/broken_serum_" + names[i] + ".png", 16, 16, () -> vial(6, 2, c, true));
        }
    }

    /**
     * The mask as worn: the head of his skin (base, with the hat layer over it) cut into a 32x32 sheet for the worn model
     * (models/item/claw_mask_worn.json): front, right, left, back on the top row, the top under the front.
     */
    static void maskWorn(String res) throws IOException {
        BufferedImage skin = ImageIO.read(new File(res, TEX + "entity/claw/claw.png"));
        int[][] faces = {{8, 8, 0, 0}, {0, 8, 8, 0}, {16, 8, 16, 0}, {24, 8, 24, 0}, {8, 0, 0, 8}};
        write(res, TEX + "item/claw_mask_worn.png", 32, 32, () -> {
            for (int[] f : faces) {
                for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) {
                    int hat = skin.getRGB(f[0] + 32 + x, f[1] + y);
                    img.setRGB(f[2] + x, f[3] + y, (hat >>> 24) > 0 ? hat : skin.getRGB(f[0] + x, f[1] + y));
                }
            }
        });
    }

    static void clear() {
        for (int y = 0; y < img.getHeight(); y++) for (int x = 0; x < img.getWidth(); x++) img.setRGB(x, y, 0);
    }

    static void book(int cover, int trim) {
        clear();
        fill(3, 2, 10, 12, cover, 4);
        fill(3, 2, 1, 12, 0x060606, 0);
        fill(4, 13, 9, 1, 0xD8D0C0, 0); // pages
        fill(12, 3, 1, 10, 0xD8D0C0, 0);
        // the Black Silence's flat-lining pulse on the cover
        int[][] pulse = {{5, 8}, {6, 8}, {7, 7}, {8, 5}, {8, 6}, {9, 9}, {10, 8}, {11, 8}};
        for (int[] p : pulse) px(p[0], p[1], trim);
    }

    static void mask() {
        clear();
        fill(4, 2, 8, 12, 0x3A2C24, 10);
        fill(3, 4, 1, 8, 0x2A201A, 4);
        fill(12, 4, 1, 8, 0x2A201A, 4);
        int[][] holes = {{6, 5}, {9, 5}, {5, 6}, {10, 6}, {6, 9}, {9, 9}, {5, 10}, {7, 10}, {8, 10}, {10, 10}, {6, 11}, {9, 11}};
        for (int[] h : holes) px(h[0], h[1], 0x050505);
    }

    static void claw(boolean broken) {
        clear();
        // the forearm, plated, diagonal from the bottom left
        for (int i = 0; i < 7; i++) fill(1 + i, 14 - i - 2, 3, 3, i % 2 == 0 ? 0x4A3628 : 0x3A2A20, 6);
        // three long blades out of the hand
        int[][] tips = {{14, 1}, {15, 4}, {12, 0}};
        for (int b = 0; b < 3; b++) {
            int x0 = 8, y0 = 6 + b;
            int len = broken && b > 0 ? 3 : 7;
            for (int s = 0; s < len; s++) {
                int x = x0 + s * (tips[b][0] - x0) / 7, y = y0 + s * (tips[b][1] - y0) / 7;
                px(x, y, s < len - 2 ? 0x2A2420 : 0x6A1410);
            }
        }
        if (broken) {
            px(10, 3, 0x6A6A6A);
            px(12, 7, 0x6A6A6A);
        }
    }

    static void pip(int x, int y, int c) {
        px(x, y, 0x2A2420);
        px(x, y + 1, c);
        px(x, y + 2, shade(c, -30));
    }

    /** A vial in a dark holder, `c` inside; broken: cracked and half empty. */
    static void vial(int x, int y, int c, boolean broken) {
        if (img.getWidth() == 16 && x == 6) clear();
        fill(x, y, 4, 1, 0x4A3A2E, 0);
        fill(x, y + 1, 1, 9, 0x2A2420, 0);
        fill(x + 3, y + 1, 1, 9, 0x2A2420, 0);
        fill(x + 1, y + 1, 2, 9, broken ? 0x1A1A1A : shade(c, 30), 0);
        if (broken) {
            fill(x + 1, y + 6, 2, 4, c, 6);
            px(x + 1, y + 2, 0xD0D0D0);
            px(x + 2, y + 4, 0xD0D0D0);
        } else {
            fill(x + 1, y + 2, 2, 8, c, 8);
            px(x + 1, y + 2, 0xFFFFFF);
        }
        fill(x, y + 10, 4, 1, 0x4A3A2E, 0);
        fill(x + 1, y + 11, 2, 2, 0x8A8A8A, 0); // the needle
    }
}
