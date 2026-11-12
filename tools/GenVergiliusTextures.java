import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Random;
import javax.imageio.ImageIO;

/**
 * Vergilius: his skin (the user's, copied as is), the E.G.O skin (blood running down from under the crown, over his
 * right eye), the eye mask (only the eyes, lit), the sheet for his extra parts (laurel crown, thorns, spiked hair, the
 * pauldron, the gladius and the blood blade, the blood cape) and the Limbus bus (a 4x sheet: 256 units, 1024 pixels).
 *
 * Usage: java tools/GenVergiliusTextures.java <resources dir> <user skin png> [skin|extras|bus]
 */
public class GenVergiliusTextures {
    static final Random R = new Random(945);
    static BufferedImage img;
    static final String TEX = "assets/projectminecraft/textures/";
    /** Pixels per sheet unit (the bus sheet is drawn at 4). */
    static int K = 1;

    public static void main(String[] args) throws IOException {
        String res = args.length > 0 ? args[0] : "src/main/resources";
        String skin = args.length > 1 ? args[1] : "_ref/vergilius/skin/vergilius_skin.png";
        String only = args.length > 2 ? args[2] : "";
        if (only.isEmpty() || only.equals("skin")) {
            BufferedImage user = ImageIO.read(new File(skin));
            write(res, TEX + "entity/vergilius/vergilius.png", 64, 64, () -> copy(user));
            write(res, TEX + "entity/vergilius/vergilius_ego.png", 64, 64, () -> {
                copy(user);
                bloodyTears();
            });
            write(res, TEX + "entity/vergilius/vergilius_eyes.png", 64, 64, GenVergiliusTextures::eyes);
        }
        if (only.isEmpty() || only.equals("extras")) write(res, TEX + "entity/vergilius/vergilius_extras.png", 64, 64, GenVergiliusTextures::extras);
        if (only.isEmpty() || only.equals("items")) items(res);
        if (only.isEmpty() || only.equals("bus")) {
            K = 4;
            write(res, TEX + "entity/vergilius/limbus_bus.png", 1024, 1024, GenVergiliusTextures::bus);
            K = 1;
        }
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
        if (x >= 0 && y >= 0 && x < img.getWidth() && y < img.getHeight()) img.setRGB(x, y, 0xFF000000 | rgb);
    }

    static void pxa(int x, int y, int argb) {
        if (x >= 0 && y >= 0 && x < img.getWidth() && y < img.getHeight()) img.setRGB(x, y, argb);
    }

    static int shade(int rgb, int d) {
        int r = clamp(((rgb >> 16) & 255) + d), g = clamp(((rgb >> 8) & 255) + d), b = clamp((rgb & 255) + d);
        return (r << 16) | (g << 8) | b;
    }

    static int mix(int a, int b, double t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t), g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t),
                bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }

    static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    /** A rectangle in sheet units (scaled by K), one colour with noise. */
    static void fill(int x, int y, int w, int h, int rgb, int noise) {
        for (int j = 0; j < h * K; j++) for (int i = 0; i < w * K; i++) px(x * K + i, y * K + j, shade(rgb, noise == 0 ? 0 : R.nextInt(noise * 2 + 1) - noise));
    }

    static void copy(BufferedImage user) {
        for (int y = 0; y < 64; y++) for (int x = 0; x < 64; x++) img.setRGB(x, y, user.getRGB(x, y));
    }

    // ------------------------------------------------------------------ the skins

    static final int BLOOD = 0x9A1016, BLOOD_DARK = 0x5E080C, BLOOD_LIT = 0xC81C22;

    /** E.G.O: blood out from under the crown, down the forehead and over his right eye (face x 8..15, y 8..15), on the side too. */
    static void bloodyTears() {
        int[][] streaks = {{10, 8, 8}, {9, 8, 2}, {12, 8, 3}, {13, 9, 2}, {14, 8, 1}};
        for (int[] s : streaks) for (int y = s[1]; y < s[1] + s[2] && y < 16; y++) px(s[0], y, y == s[1] ? BLOOD_DARK : (y % 3 == 0 ? BLOOD_LIT : BLOOD));
        px(10, 12, 0xB01018); // the eye itself, red with it
        // the hat layer's fringe, bloodied where it falls over the forehead
        for (int y = 10; y < 12; y++) if ((img.getRGB(42, y) >>> 24) != 0) px(42, y, BLOOD);
        // down the right side of the head, past the ear
        for (int y = 9; y < 15; y++) px(1, y, y > 12 ? BLOOD_DARK : BLOOD);
        for (int y = 10; y < 13; y++) px(3, y, BLOOD);
    }

    /** The eye mask: his two red eyes (face (10,12) and (13,12)), bright, a faint bloom either side. */
    static void eyes() {
        for (int[] e : new int[][]{{10, 12}, {13, 12}}) {
            pxa(e[0], e[1], 0xFFFF3A30);
            pxa(e[0], e[1] - 1, 0xFF3A0A08);
            pxa(e[0] + (e[0] == 10 ? -1 : 1), e[1], 0xFF4A0E0A);
        }
    }

    // ------------------------------------------------------------------ the extras

    static void extras() {
        R.setSeed(945);
        // gold laurel leaves: gold with a darker vein down the middle and lit edges
        fill(0, 0, 16, 8, 0xC49A2E, 12);
        for (int x = 0; x < 16; x += 3) for (int y = 0; y < 8; y++) px(x, y, 0x8A6418);
        for (int x = 1; x < 16; x += 3) px(x, R.nextInt(8), 0xEECB62);
        // thorns: dark red-black
        fill(16, 0, 8, 8, 0x3E080C, 8);
        // the hair: his grey
        fill(24, 0, 8, 8, 0x45464A, 6);
        for (int i = 0; i < 10; i++) px(24 + R.nextInt(8), R.nextInt(8), 0x5C5C5E);
        // the pauldron: dark red plate, blood smeared over it, pinkish lit rims
        fill(32, 0, 32, 8, 0x7A121A, 10);
        for (int i = 0; i < 50; i++) px(32 + R.nextInt(32), R.nextInt(8), BLOOD_DARK);
        for (int i = 0; i < 24; i++) px(32 + R.nextInt(32), R.nextInt(8), 0xC24A58);
        // the gladius (32,8): red-orange, brighter toward the middle, darker red at the edges and in streaks; the ridge
        // (rows 12..15) lit hottest
        for (int y = 8; y < 16; y++) for (int x = 32; x < 48; x++) {
            double t = Math.abs(x - 39.5) / 8.0;
            int c = y >= 12 ? mix(0xFF9A52, 0xFF6A2A, t) : mix(0xFF6A2E, 0xC2301A, t);
            px(x, y, shade(c, R.nextInt(13) - 6));
        }
        for (int i = 0; i < 12; i++) px(32 + R.nextInt(16), 8 + R.nextInt(4), 0xA8261A);
        // the blood blade
        fill(48, 8, 16, 8, 0xC0141C, 14);
        for (int i = 0; i < 20; i++) px(48 + R.nextInt(16), 8 + R.nextInt(8), 0x620608);
        // grip, guard, the cores
        fill(0, 8, 8, 8, 0x141416, 4);
        fill(8, 8, 8, 8, 0x2C2C30, 8);
        for (int x = 8; x < 16; x += 2) px(x, 8, 0x5A5A60);
        fill(24, 8, 8, 8, 0xFFE2A0, 6);
        fill(16, 8, 8, 8, 0xFF4848, 6);
        // the blood cape: bright blood, darker blotches, streaks running down
        fill(0, 16, 64, 48, 0xB8141C, 10);
        for (int i = 0; i < 60; i++) {
            int x = R.nextInt(64), y = 16 + R.nextInt(48), w = 2 + R.nextInt(4), h = 1 + R.nextInt(3);
            for (int j = 0; j < h; j++) for (int k = 0; k < w; k++) px(x + k, y + j, shade(0x7A0A10, R.nextInt(9) - 4));
        }
        for (int i = 0; i < 26; i++) {
            int x = R.nextInt(64), y = 16 + R.nextInt(40), len = 3 + R.nextInt(8);
            for (int j = 0; j < len; j++) px(x, y + j, 0x5A060A);
        }
        for (int i = 0; i < 30; i++) px(R.nextInt(64), 16 + R.nextInt(48), 0xDC2830);
    }

    // ------------------------------------------------------------------ item sprites (16x16)

    static void items(String res) throws IOException {
        // the hitlist: a folded page, a red mark at its corner, the names blacked out but one
        write(res, TEX + "item/hitlist.png", 16, 16, () -> {
            R.setSeed(11);
            for (int y = 2; y < 15; y++) for (int x = 3; x < 13; x++) px(x, y, shade(0xE2D6BA, R.nextInt(9) - 4));
            for (int y = 2; y < 15; y++) px(8, y, 0xBFAF8E);
            for (int x = 3; x < 13; x++) { px(x, 2, 0x9A8A6A); px(x, 14, 0x9A8A6A); }
            for (int y = 2; y < 15; y++) { px(3, y, 0x9A8A6A); px(12, y, 0x9A8A6A); }
            for (int x = 4; x < 8; x++) px(x, 5, 0x7A1010);
            for (int[] bar : new int[][]{{4, 8, 7}, {4, 10, 6}, {9, 6, 11}, {9, 9, 11}, {9, 11, 10}}) for (int x = bar[0]; x <= bar[2]; x++) px(x, bar[1], 0x18120C);
            px(10, 3, 0xA01818); px(11, 3, 0xA01818); px(10, 4, 0xA01818); px(11, 4, 0xC02020);
        });
        // his eyes: two red eyes in a small stoppered jar of blood-dark glass
        write(res, TEX + "item/vergilius_eyes.png", 16, 16, () -> {
            for (int y = 4; y < 15; y++) for (int x = 4; x < 12; x++) if (!(y == 14 && (x == 4 || x == 11))) px(x, y, 0x3A1418);
            for (int y = 4; y < 15; y++) { px(4, y, 0x8AA0A8); px(11, y, 0x6A808A); }
            for (int x = 5; x < 11; x++) px(x, 14, 0x6A808A);
            for (int x = 5; x < 11; x++) { px(x, 2, 0x5A3A22); px(x, 3, 0x6A4A2A); }
            for (int[] e : new int[][]{{6, 8}, {9, 10}}) {
                px(e[0], e[1], 0xE8E0DC); px(e[0] + 1, e[1], 0xE8E0DC); px(e[0], e[1] + 1, 0xE8E0DC); px(e[0] + 1, e[1] + 1, 0xE8E0DC);
                px(e[0] + 1, e[1], 0xE01818); px(e[0], e[1] + 1, 0xA01010);
            }
            px(5, 5, 0xC8DCE4); px(5, 6, 0xA8C0C8);
        });
        // the gladius as an icon: the blade's swell, waist and long taper, corner to corner
        write(res, TEX + "item/white_hot_gladius.png", 16, 16, () -> {
            int[] half = {0, 1, 1, 2, 2, 1, 1, 1, 2, 1, 1, 1, 1, 0, 0};
            for (int k = 0; k < 15; k++) {
                int cx = 1 + k, cy = 14 - k;
                if (k < 3) { px(cx, cy, 0x141416); continue; }
                int c = k < 9 ? 0xFF6A2E : 0xFF8A40;
                px(cx, cy, 0xFFC27A);
                for (int h = 1; h <= half[k]; h++) { px(cx + h, cy + h, c); px(cx - h, cy - h, shade(c, -40)); }
            }
            px(2, 13, 0x2C2C30);
        });
        // the 3D gladius's texture: blade (rows 0..7), the hot ridge (8..11), the grip (12..15)
        write(res, TEX + "item/white_hot_gladius_blade.png", 16, 16, () -> {
            R.setSeed(12);
            for (int y = 0; y < 8; y++) for (int x = 0; x < 16; x++) px(x, y, shade(mix(0xFF6A2E, 0xC2301A, Math.abs(x - 7.5) / 8.0), R.nextInt(13) - 6));
            for (int y = 8; y < 12; y++) for (int x = 0; x < 16; x++) px(x, y, shade(0xFF9A52, R.nextInt(11) - 5));
            for (int y = 12; y < 16; y++) for (int x = 0; x < 16; x++) px(x, y, shade(0x141416, R.nextInt(5) - 2));
        });
    }

    // ------------------------------------------------------------------ the bus (sheet units; K = 4 pixels each)

    /** A rectangle in pixels of the 4x sheet. */
    static void rect(int x, int y, int w, int h, int rgb) {
        for (int j = 0; j < h; j++) for (int i = 0; i < w; i++) px(x + i, y + j, rgb);
    }

    static void bus() {
        R.setSeed(945);
        // black engraved panels: art-deco lines and chevrons, lighter where they catch the light
        for (int y = 0; y < 96 * K; y++) for (int x = 0; x < 256 * K; x++) {
            int c = shade(0x17171A, R.nextInt(7) - 3);
            int mx = x % 48, my = y % 40;
            boolean line = mx == 3 || mx == 4 || mx == 43 || mx == 44 || my == 2 || my == 37;
            boolean chevron = Math.abs(Math.abs(mx - 24) - (my % 20)) <= 0 && mx > 6 && mx < 42;
            boolean swirl = Math.abs(Math.sin(x * 0.13) * 6 + (y % 20) - 10) < 0.7 && my > 4 && my < 36;
            if (line) c = 0x2E2E34;
            else if (chevron) c = 0x3A3A42;
            else if (swirl) c = 0x26262C;
            if ((mx == 5 || my == 3) && !line) c = shade(c, 14);
            px(x, y, c);
        }
        // red: lacquer with darker engraved swirls, a lit edge band
        for (int y = 96 * K; y < 176 * K; y++) for (int x = 0; x < 256 * K; x++) {
            int c = shade(0x8E1420, R.nextInt(9) - 4);
            int mx = x % 40, my = y % 32;
            if (Math.abs(Math.sin(x * 0.16 + (y / 32) * 1.7) * 7 + my - 16) < 0.9) c = 0x560A12;
            else if (mx == 1 || my == 1) c = 0xB8202E;
            else if (Math.abs(mx - 20) + Math.abs(my - 16) == 12) c = 0x6A0C16;
            px(x, y, c);
        }
        // steel, with rivets
        fill(0, 176, 64, 32, 0x77797F, 8);
        for (int y = 176 * K; y < 208 * K; y += 8) for (int x = 0; x < 64 * K; x += 8) {
            px(x + 3, y + 3, 0xA6A8AE);
            px(x + 4, y + 3, 0xA6A8AE);
            px(x + 3, y + 4, 0x55575C);
        }
        // tinted glass: see-through, a streak of reflection
        for (int y = 176 * K; y < 208 * K; y++) for (int x = 64 * K; x < 128 * K; x++) {
            boolean streak = Math.floorMod(x - y, 60) < 6;
            pxa(x, y, streak ? 0x8CB0C8D0 : 0x8A16242C);
        }
        // the lit yellow panels, LIMBUS in dark blocky letters across both of the flank panel's faces
        fill(128, 176, 64, 32, 0xDCB83C, 8);
        letters(128 * K + 2, (176 + 13) * K + 3, 13 * K - 4, 4 * K - 6, 0x5A3E0A);
        letters((int) ((128 + 13.4) * K) + 2, (176 + 13) * K + 3, 13 * K - 4, 4 * K - 6, 0x5A3E0A);
        // lanterns: warm light in brass frames; tail lights
        fill(192, 176, 32, 32, 0xFFDC96, 6);
        for (int y = 176 * K; y < 208 * K; y++) for (int x = 192 * K; x < 224 * K; x++) if (x % 9 == 0 || y % 9 == 0) px(x, y, 0x9A7A3A);
        fill(224, 176, 32, 32, 0xFF3A30, 6);
        // wheels: rubber, a rim ring
        fill(0, 208, 64, 24, 0x141414, 4);
        for (int y = 208 * K; y < 232 * K; y++) for (int x = 0; x < 64 * K; x++) if (y % 12 == 5 || x % 12 == 5) px(x, y, 0x3A3A3E);
        // windows: dark glass in black frames, a faint reflection
        for (int y = 208 * K; y < 232 * K; y++) for (int x = 64 * K; x < 128 * K; x++) {
            int c = Math.floorMod(x + y, 40) < 4 ? 0x5A7A88 : shade(0x22303A, R.nextInt(5) - 2);
            if (x % 24 < 2 || y % 44 < 2) c = 0x0C0C0E;
            px(x, y, c);
        }
        // the door: black, a red frame, two tall narrow panes
        fill(128, 208, 64, 24, 0x1A1A1E, 4);
        for (int y = 208 * K; y < 232 * K; y++) for (int x = 128 * K; x < 192 * K; x++) {
            int lx = x % 14;
            if (lx == 0 || lx == 13) px(x, y, 0x8E1420);
            else if ((lx == 4 || lx == 5 || lx == 8 || lx == 9) && y % 76 > 8 && y % 76 < 60) px(x, y, 0x5A7A88);
        }
        // the driver's shape, and the dark
        fill(192, 208, 64, 24, 0x0A0A0C, 2);
        // the LIMBUS plate: lit yellow, a dark border, the name across both faces (the bus faces +z: its "back" face is the front)
        fill(0, 232, 32, 8, 0xE2C444, 6);
        letters((int) (0.5 * K) + 3, (int) (232.5 * K) + 6, (int) (14.8 * K) - 6, 7 * K - 12, 0x3A2A08);
        letters((int) (15.8 * K) + 3, (int) (232.5 * K) + 6, (int) (14.8 * K) - 6, 7 * K - 12, 0x3A2A08);
    }

    /** "LIMBUS" in a 5x7 block font, fitted into the rectangle (pixels). */
    static void letters(int x0, int y0, int w, int h, int rgb) {
        String[][] font = {
                {"10000", "10000", "10000", "10000", "10000", "10000", "11111"}, // L
                {"11111", "00100", "00100", "00100", "00100", "00100", "11111"}, // I
                {"10001", "11011", "10101", "10101", "10001", "10001", "10001"}, // M
                {"11110", "10001", "10001", "11110", "10001", "10001", "11110"}, // B
                {"10001", "10001", "10001", "10001", "10001", "10001", "01110"}, // U
                {"01111", "10000", "10000", "01110", "00001", "00001", "11110"}}; // S
        int cols = 6 * 5 + 5;
        double sx = w / (double) cols, sy = h / 7.0;
        for (int L = 0; L < 6; L++) for (int r = 0; r < 7; r++) for (int c = 0; c < 5; c++) {
            if (font[L][r].charAt(c) != '1') continue;
            int px0 = x0 + (int) ((L * 6 + c) * sx), py0 = y0 + (int) (r * sy);
            rect(px0, py0, Math.max(1, (int) Math.ceil(sx)), Math.max(1, (int) Math.ceil(sy)), rgb);
        }
    }
}
