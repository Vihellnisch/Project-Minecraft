import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Random;
import javax.imageio.ImageIO;

/**
 * The Black Silence's reception: placeholder skins for Roland (suit, face lost in shadow), Roland settled into his
 * distortion (the dark ring for a head), Roland's face for when he falls, Angelica (all white), and the Images of the
 * Bygones (pale silhouettes); the distorted beast's flesh texture (shared with the byproducts), Angelica's long hair,
 * and the Library Invitation card. Skins use the 64x64 player layout; Angelica and the images are slim-armed.
 *
 * Usage: java tools/GenRolandTextures.java <resources dir> [skins|beast|items]
 */
public class GenRolandTextures {
    static BufferedImage img;
    static final Random RNG = new Random(5050);
    static final String DIR = "assets/projectminecraft/textures/entity/roland/";

    public static void main(String[] args) throws IOException {
        String res = args.length > 0 ? args[0] : "src/main/resources";
        String only = args.length > 1 ? args[1] : "";
        // Roland's and Angelica's skins are the user's own now; "placeholders" redraws the old stand-ins
        if (only.equals("placeholders")) {
            write(res, DIR + "roland.png", 64, 64, () -> roland(false, false));
            write(res, DIR + "roland_misty.png", 64, 64, () -> roland(true, false));
            write(res, DIR + "roland_face.png", 64, 64, () -> roland(false, true));
            write(res, DIR + "angelica.png", 64, 64, GenRolandTextures::angelica);
        }
        if (only.isEmpty() || only.equals("skins")) {
            write(res, DIR + "angelica_hair.png", 64, 32, GenRolandTextures::hair);
            write(res, DIR + "bygone.png", 64, 64, GenRolandTextures::bygone);
        }
        if (only.isEmpty() || only.equals("skins") || only.equals("mist")) mist(res);
        if (only.isEmpty() || only.equals("distorted")) distorted(res);
        if (only.isEmpty() || only.equals("beast")) write(res, DIR + "roland_beast.png", 256, 128, GenRolandTextures::beast);
        if (only.isEmpty() || only.equals("items")) write(res, "assets/projectminecraft/textures/item/library_invitation.png", 16, 16, GenRolandTextures::invitation);
    }

    static void write(String res, String path, int w, int h, Runnable paint) throws IOException {
        img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        paint.run();
        File out = new File(res, path);
        out.getParentFile().mkdirs();
        ImageIO.write(img, "png", out);
        System.out.println("wrote " + out);
    }

    static void px(int x, int y, int c) {
        if (x >= 0 && y >= 0 && x < img.getWidth() && y < img.getHeight()) img.setRGB(x, y, c);
    }

    static int shade(int rgb, float k) {
        int r = Math.min(255, Math.max(0, (int) (((rgb >> 16) & 255) * k))), g = Math.min(255, Math.max(0, (int) (((rgb >> 8) & 255) * k))),
                b = Math.min(255, Math.max(0, (int) ((rgb & 255) * k)));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    static int n(int rgb, float noise) {
        return shade(rgb, 1 + (RNG.nextFloat() - 0.5f) * noise);
    }

    // ---- the player-skin box layout: for a w*h*d box at (u, v) the top is (u+d, v), the bottom (u+d+w, v), and the
    // row at v+d holds right side, front, left side, back
    enum Face { TOP, BOTTOM, RIGHT, FRONT, LEFT, BACK }

    interface Paint {
        int at(Face f, int x, int y, int fw, int fh);
    }

    static void box(int u, int v, int w, int h, int d, Paint p) {
        face(u + d, v, w, d, Face.TOP, p);
        face(u + d + w, v, w, d, Face.BOTTOM, p);
        face(u, v + d, d, h, Face.RIGHT, p);
        face(u + d, v + d, w, h, Face.FRONT, p);
        face(u + d + w, v + d, d, h, Face.LEFT, p);
        face(u + 2 * d + w, v + d, w, h, Face.BACK, p);
    }

    static void face(int x0, int y0, int fw, int fh, Face f, Paint p) {
        for (int y = 0; y < fh; y++) for (int x = 0; x < fw; x++) {
            int c = p.at(f, x, y, fw, fh);
            if (c != 0) px(x0 + x, y0 + y, c);
        }
    }

    static final int SUIT = 0xFF17171B, SHIRT = 0xFFE6E6E8, TIE = 0xFF08080A, GLOVE = 0xFF0B0B0D, SHOE = 0xFF050506, HAIR = 0xFF131316,
            SKIN = 0xFFD8BEA8, VOID = 0xFF000000, RIM = 0xFF4A4A50;

    /** Roland: the black suit, white shirt and black tie, black gloves; his head in phase 1 is hair and shadow. */
    static void roland(boolean misty, boolean face) {
        int suit = misty ? 0xFF1C1C21 : SUIT;
        // head
        box(0, 0, 8, 8, 8, (f, x, y, w, h) -> {
            if (misty) {
                // the ring: a dark barrel of slats around a void
                if (f == Face.FRONT) return (x == 0 || x == 7 || y == 0 || y == 7) ? n(RIM, 0.2f) : VOID;
                if (f == Face.BOTTOM) return VOID;
                return x % 2 == 0 ? n(0xFF35353B, 0.15f) : n(0xFF26262B, 0.15f);
            }
            if (f != Face.FRONT) return n(HAIR, 0.3f);
            if (y <= 1 || (y == 2 && (x <= 2 || x >= 6))) return n(HAIR, 0.3f);
            if (!face) {
                // his face lost in shadow, only the line of his jaw catching the light
                if (y == 7 && x >= 2 && x <= 5) return 0xFF4A3E38;
                return n(0xFF0E0E11, 0.2f);
            }
            if (y == 3 && (x == 1 || x == 2 || x == 5 || x == 6)) return 0xFF2A2420;
            if (y == 4 && (x == 2 || x == 5)) return 0xFF3A3A44;
            if (y == 4 && (x == 1 || x == 6)) return 0xFFF0F0F0;
            if (y == 6 && (x == 3 || x == 4)) return 0xFF8A6A5C;
            if (y == 7) return n(shade(SKIN, 0.82f), 0.1f);
            return n(SKIN, 0.06f);
        });
        // hair falling over the face (outer layer), or the ring's rim standing out
        box(32, 0, 8, 8, 8, (f, x, y, w, h) -> {
            if (misty) {
                if (f == Face.FRONT) return (x == 0 || x == 7 || y == 0 || y == 7) ? n(0xFF5C5C64, 0.15f) : 0;
                if (f == Face.RIGHT || f == Face.LEFT) return y % 3 == 0 ? n(0xFF46464E, 0.1f) : 0;
                return 0;
            }
            if (f == Face.FRONT) return y <= 1 || (y == 2 && x % 3 != 1) || (!face && y == 3 && (x == 0 || x == 3 || x == 7)) ? n(HAIR, 0.3f) : 0;
            if (f == Face.RIGHT || f == Face.LEFT) return y <= 4 ? n(HAIR, 0.3f) : 0;
            if (f == Face.BACK) return y <= 6 ? n(HAIR, 0.3f) : 0;
            if (f == Face.TOP) return n(HAIR, 0.3f);
            return 0;
        });
        // body: jacket, the shirt's V, the tie
        box(16, 16, 8, 12, 4, (f, x, y, w, h) -> {
            if (f == Face.FRONT) {
                double half = (5 - y) * 0.7;
                if (y < 5 && Math.abs(x - 3.5) < half) {
                    if (x == 3 || x == 4) return y == 0 ? 0xFF1A1A1E : TIE;
                    return n(SHIRT, 0.04f);
                }
                if ((x == 3 || x == 4) && y < 7) return TIE;
                if (y == 5 && (x == 2 || x == 5)) return shade(suit, 0.6f);
                if (x == 3 && (y == 8 || y == 10)) return 0xFF6A6A70;
                if (y == 11) return shade(suit, 0.7f);
            }
            return n(suit, 0.18f);
        });
        // arms (wide): sleeves, white cuffs, black gloves
        Paint arm = (f, x, y, w, h) -> {
            if (f == Face.BOTTOM) return GLOVE;
            if (f == Face.TOP) return n(suit, 0.15f);
            if (y == 9) return n(SHIRT, 0.05f);
            if (y >= 10) return n(GLOVE, 0.2f);
            return n(suit, 0.18f);
        };
        box(40, 16, 4, 12, 4, arm);
        box(32, 48, 4, 12, 4, arm);
        Paint leg = (f, x, y, w, h) -> {
            if (f == Face.BOTTOM || y >= 11) return SHOE;
            if (y == 10) return 0xFF0E0E10;
            return n(0xFF141418, 0.16f);
        };
        box(0, 16, 4, 12, 4, leg);
        box(16, 48, 4, 12, 4, leg);
        if (misty) {
            // smoke clinging to the suit
            for (int i = 0; i < 160; i++) {
                int x = 16 + RNG.nextInt(40), y = 16 + RNG.nextInt(16);
                if ((img.getRGB(x, y) >>> 24) != 0) px(x, y, n(0xFF2C2C33, 0.3f));
            }
        }
    }

    static final int WHITE_SUIT = 0xFFF2F2F5, PALE = 0xFFF4EEF0;

    /** Angelica, all in white: long white hair, a white suit, her eyes closed. Slim arms. */
    static void angelica() {
        box(0, 0, 8, 8, 8, (f, x, y, w, h) -> {
            if (f != Face.FRONT) return n(0xFFFAFAFC, 0.06f);
            if (y <= 1 || x == 0 || x == 7) return n(0xFFFAFAFC, 0.06f);
            if (y == 4 && (x == 1 || x == 2 || x == 5 || x == 6)) return 0xFFB4ACB8;
            if (y == 6 && (x == 3 || x == 4)) return 0xFFD8B8C0;
            return n(PALE, 0.04f);
        });
        box(32, 0, 8, 8, 8, (f, x, y, w, h) -> {
            if (f == Face.FRONT) return y == 0 || (y == 1 && x != 3 && x != 4) || x == 0 || x == 7 ? n(0xFFFFFFFF, 0.05f) : 0;
            return n(0xFFFFFFFF, 0.05f);
        });
        box(16, 16, 8, 12, 4, (f, x, y, w, h) -> {
            if (f == Face.FRONT) {
                double half = (5 - y) * 0.7;
                if (y < 5 && Math.abs(x - 3.5) < half) return (x == 3 || x == 4) ? 0xFFC8C8D0 : 0xFFFFFFFF;
                if ((x == 3 || x == 4) && y < 7) return 0xFFC8C8D0;
            }
            return n(WHITE_SUIT, 0.05f);
        });
        Paint arm = (f, x, y, w, h) -> y >= 10 && f != Face.TOP ? n(PALE, 0.04f) : n(WHITE_SUIT, 0.05f);
        box(40, 16, 3, 12, 4, arm);
        box(32, 48, 3, 12, 4, arm);
        Paint leg = (f, x, y, w, h) -> y >= 11 || f == Face.BOTTOM ? 0xFFE0E0E8 : n(WHITE_SUIT, 0.05f);
        box(0, 16, 4, 12, 4, leg);
        box(16, 48, 4, 12, 4, leg);
    }

    /**
     * Angelica as white mist: her own skin bleached almost to white (its shading kept faintly, so her face, hair and suit
     * still read), for the renderer to draw translucent and glowing.
     */
    static void mist(String res) throws IOException {
        BufferedImage src = ImageIO.read(new File(res, DIR + "angelica.png"));
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < src.getHeight(); y++) for (int x = 0; x < src.getWidth(); x++) {
            int c = src.getRGB(x, y), a = c >>> 24;
            if (a == 0) continue;
            int gray = (((c >> 16) & 255) * 30 + ((c >> 8) & 255) * 59 + (c & 255) * 11) / 100;
            int v = 255 - (255 - gray) * 30 / 100;
            out.setRGB(x, y, (a << 24) | (v << 16) | (v << 8) | Math.min(255, v + 6));
        }
        File f = new File(res, DIR + "angelica_mist.png");
        ImageIO.write(out, "png", f);
        System.out.println("wrote " + f);
    }

    /**
     * His weapons once he's distorted (after the reception's art): the same weapons gone black, their metal a dull
     * gunmetal, only the sharpest edges still catching the light, nothing glowing. Darkened copies of the weapon
     * palettes, and copies of the Furioso models (models/furioso_distorted) that use them.
     */
    static void distorted(String res) throws IOException {
        String tex = "assets/projectminecraft/textures/item/";
        for (String n : new String[]{"weapon_palette_2", "weapon_palette_3", "weapon_glow_2", "weapon_glow_3"}) {
            BufferedImage src = ImageIO.read(new File(res, tex + n + ".png"));
            boolean glow = n.contains("glow");
            BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < src.getHeight(); y++) for (int x = 0; x < src.getWidth(); x++) {
                int c = src.getRGB(x, y), a = c >>> 24;
                if (a == 0) continue;
                int gray = (((c >> 16) & 255) * 30 + ((c >> 8) & 255) * 59 + (c & 255) * 11) / 100;
                // darker all over, but the bright edges fall off less, so they still read as steel
                int v = glow ? gray * 22 / 100 : gray < 140 ? gray * 45 / 100 : 63 + (gray - 140) * 80 / 100;
                out.setRGB(x, y, (a << 24) | (v << 16) | (v << 8) | Math.min(255, v + 4));
            }
            File f = new File(res, tex + "distorted/" + n + ".png");
            f.getParentFile().mkdirs();
            ImageIO.write(out, "png", f);
            File meta = new File(res, tex + n + ".png.mcmeta");
            if (meta.exists()) java.nio.file.Files.copy(meta.toPath(), new File(res, tex + "distorted/" + n + ".png.mcmeta").toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            System.out.println("wrote " + f);
        }
        File models = new File(res, "assets/projectminecraft/models/furioso"), outDir = new File(res, "assets/projectminecraft/models/furioso_distorted");
        outDir.mkdirs();
        for (File m : models.listFiles((d, name) -> name.endsWith(".json"))) {
            String json = java.nio.file.Files.readString(m.toPath()).replace("projectminecraft:item/weapon_", "projectminecraft:item/distorted/weapon_");
            java.nio.file.Files.writeString(new File(outDir, m.getName()).toPath(), json);
        }
        System.out.println("wrote " + outDir);
    }

    /** Her hair: white, in long strands. */
    static void hair() {
        for (int y = 0; y < 32; y++) for (int x = 0; x < 64; x++) {
            int c = x % 3 == 0 ? 0xFFE8E8EE : x % 3 == 1 ? 0xFFFFFFFF : 0xFFF4F4F8;
            if (RNG.nextInt(9) == 0) c = 0xFFDCDCE4;
            px(x, y, c);
        }
    }

    /** An Image of the Bygones: a pale, featureless shape of someone he once fought. Slim arms. */
    static void bygone() {
        int base = 0xFFC4C8D4;
        box(0, 0, 8, 8, 8, (f, x, y, w, h) -> n(f == Face.FRONT && y >= 3 ? 0xFFB0B4C2 : base, 0.08f));
        box(16, 16, 8, 12, 4, (f, x, y, w, h) -> n(f == Face.FRONT && (x == 3 || x == 4) && y < 7 ? 0xFFA8ACBA : base, 0.08f));
        Paint limb = (f, x, y, w, h) -> n(base, 0.1f);
        box(40, 16, 3, 12, 4, limb);
        box(32, 48, 3, 12, 4, limb);
        box(0, 16, 4, 12, 4, limb);
        box(16, 48, 4, 12, 4, limb);
    }

    /**
     * The beast: red-black flesh in sinewy stripes (0,0 to 256,64), black for the tendrils and eye holes (0,64), bone
     * for teeth and claws (128,64), the dark inside of a maw (0,96).
     */
    static void beast() {
        for (int y = 0; y < 64; y++) for (int x = 0; x < 256; x++) {
            double stripe = Math.sin((x + y * 0.6) * 0.55 + Math.sin(y * 0.3) * 1.5);
            int c = stripe > 0.55 ? 0xFF8A2626 : stripe < -0.6 ? 0xFF2A0909 : 0xFF5A1616;
            if (RNG.nextInt(11) == 0) c = 0xFF1A0505;
            px(x, y, n(c, 0.25f));
        }
        // dark veins wandering through it
        for (int v = 0; v < 52; v++) {
            double x = RNG.nextInt(256), y = RNG.nextInt(64), a = RNG.nextDouble() * Math.PI * 2;
            for (int s = 0; s < 30; s++) {
                px((int) x, (int) y, 0xFF140303);
                a += (RNG.nextDouble() - 0.5) * 0.8;
                x = (x + Math.cos(a) + 256) % 256;
                y = Math.max(0, Math.min(63, y + Math.sin(a)));
            }
        }
        for (int y = 64; y < 96; y++) for (int x = 0; x < 128; x++) px(x, y, n(RNG.nextInt(5) == 0 ? 0xFF2A0A0A : 0xFF0D0909, 0.3f));
        for (int y = 64; y < 96; y++) for (int x = 128; x < 256; x++) px(x, y, n(y % 4 == 0 ? 0xFFC8BCA8 : 0xFFE8E0CE, 0.08f));
        for (int y = 96; y < 128; y++) for (int x = 0; x < 128; x++) px(x, y, n(RNG.nextInt(4) == 0 ? 0xFF4A0A0A : 0xFF1A0404, 0.3f));
    }

    /** A black card edged in gold, with a gold emblem: an open book. */
    static void invitation() {
        for (int y = 1; y <= 14; y++) for (int x = 2; x <= 13; x++) {
            boolean edge = x == 2 || x == 13 || y == 1 || y == 14;
            boolean inner = x == 4 || x == 11 || y == 3 || y == 12;
            int c = edge ? 0xFFD8B050 : inner && x >= 4 && x <= 11 && y >= 3 && y <= 12 ? 0xFF6A5420 : n(0xFF121216, 0.2f);
            px(x, y, c);
        }
        // the corner turned down
        px(13, 1, 0);
        px(12, 1, 0xFFD8B050);
        px(13, 2, 0xFFD8B050);
        // the book
        int g = 0xFFE8C060, d = 0xFFA07830;
        for (int x = 5; x <= 10; x++) {
            px(x, 7, g);
            px(x, 9, d);
        }
        px(5, 6, g);
        px(6, 6, g);
        px(9, 6, g);
        px(10, 6, g);
        px(7, 7, d);
        px(8, 7, d);
        px(7, 8, g);
        px(8, 8, g);
        for (int x = 5; x <= 10; x++) if (x != 7 && x != 8) px(x, 8, 0xFFF4EAD0);
    }
}
