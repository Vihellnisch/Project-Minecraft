import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Random;
import javax.imageio.ImageIO;

/**
 * Generates the Black Silence's gloves: the worn texture (64x32, two glove + cuff boxes) in
 * assets/projectminecraft/textures/entity and the 16x16 inventory icon.
 *
 * Usage: java tools/GenGloveTextures.java <resources dir>
 */
public class GenGloveTextures {
    static final Random R = new Random(4242);
    static BufferedImage img;

    public static void main(String[] args) throws IOException {
        String res = args.length > 0 ? args[0] : "src/main/resources";
        write(res, "assets/projectminecraft/textures/entity/black_silence_gloves.png", 64, 32, GenGloveTextures::gloves);
        write(res, "assets/projectminecraft/textures/item/black_silence.png", 16, 16, GenGloveTextures::glovesIcon);
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

    /** Fill with a little per-pixel brightness noise so flat areas don't look like plastic. */
    static void fill(int x, int y, int w, int h, int rgb, int noise) {
        for (int j = 0; j < h; j++) {
            for (int i = 0; i < w; i++) px(x + i, y + j, shade(rgb, noise == 0 ? 0 : R.nextInt(noise * 2 + 1) - noise));
        }
    }

    static int shade(int rgb, int d) {
        int r = clamp(((rgb >> 16) & 255) + d), g = clamp(((rgb >> 8) & 255) + d), b = clamp((rgb & 255) + d);
        return (r << 16) | (g << 8) | b;
    }

    static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    /** All six faces of a box part: u,v = top-left of the part's net, w/h/d in pixels. */
    static void part(int u, int v, int w, int h, int d, int rgb, int noise) {
        fill(u + d, v, w, d, rgb, noise);               // top
        fill(u + d + w, v, w, d, shade(rgb, -20), noise); // bottom
        fill(u, v + d, d * 2 + w * 2, h, rgb, noise);   // sides, front, back
    }

    // ------------------------------------------------------------------ the Black Silence's gloves

    static final int LEATHER = 0x18181c, STITCH = 0x34343c, CUFF = 0x2c2c32, EMBLEM = 0xe6e6e6;

    /** A 4x6x4 glove box at (u, v); the emblem goes on the outer face (column 0 right, column 2 left). */
    static void glove(int u, int v, int outer) {
        part(u, v, 4, 6, 4, LEATHER, 5);
        // stitched knuckle lines across the front and back, near the fist
        for (int c = 0; c < 4; c++) {
            for (int i = 0; i < 4; i++) px(u + c * 4 + i, v + 4 + 4, STITCH);
        }
        // the emblem: a pulse, then the flat line
        int ox = u + outer * 4, oy = v + 4 + 1;
        px(ox, oy + 2, EMBLEM); px(ox + 1, oy, EMBLEM); px(ox + 1, oy + 1, EMBLEM); px(ox + 2, oy + 3, EMBLEM); px(ox + 3, oy + 2, EMBLEM);
    }

    static void gloves() {
        glove(0, 0, 0);
        glove(32, 0, 2);
        // cuffs (4x2x4 boxes): dark grey with a thin pale seam
        for (int u : new int[] {0, 32}) {
            part(u, 16, 4, 2, 4, CUFF, 4);
            fill(u, 20, 16, 1, 0x5a5a62, 2);
        }
    }

    /** The inventory icon: a pair of black gloves, the pulse on the back of each. */
    static void glovesIcon() {
        int[][] hand = {
                {0, 0, 1, 1, 0, 1, 1, 0},
                {0, 1, 1, 1, 1, 1, 1, 0},
                {0, 1, 1, 1, 1, 1, 1, 0},
                {1, 1, 1, 1, 1, 1, 1, 0},
                {1, 1, 1, 1, 1, 1, 1, 0},
                {0, 1, 1, 1, 1, 1, 1, 0},
                {0, 1, 1, 1, 1, 1, 0, 0},
                {0, 0, 2, 2, 2, 2, 0, 0},
                {0, 0, 2, 2, 2, 2, 0, 0}};
        for (int g = 0; g < 2; g++) {
            int ox = g == 0 ? 1 : 8, oy = g == 0 ? 2 : 5;
            for (int y = 0; y < hand.length; y++) {
                for (int x = 0; x < 8; x++) {
                    int c = hand[y][g == 0 ? x : 7 - x];
                    if (c == 0) continue;
                    px(ox + x, oy + y, c == 1 ? shade(LEATHER, R.nextInt(14) - 4) : CUFF);
                }
            }
            // outline highlight and the pulse
            int ex = ox + 2, ey = oy + 4;
            px(ex, ey, EMBLEM); px(ex + 1, ey - 1, EMBLEM); px(ex + 2, ey + 1, EMBLEM); px(ex + 3, ey, EMBLEM);
        }
    }
}
