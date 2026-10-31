package com.slop.projectminecraft.arsenal;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;

/**
 * Furioso: Roland's page, choreographed. Every weapon the Black Silence's gloves hold, in the order he unleashes them
 * (Atelier Logic pistols, Allas lance, Old Boys hammer, Mook katana, Ranga gauntlets and knife, Zelkova axe and mace,
 * Wheels Industry greatsword, Crystal Atelier short swords, Atelier Logic shotgun), then Durandal. Each weapon's hits
 * follow its combat page's dice.
 * <p>
 * Two clocks: <b>script time</b> (what happens, 0..TOTAL) and <b>real time</b> (ticks since the start). Script time
 * runs slower inside slow-motion windows so every big hit can be seen. Server and client share this table, so damage,
 * positions, camera and effects stay in step from one start packet.
 */
public final class FuriosoScript {
    // ---- weapons (what is in your hands), in Furioso order
    public static final int NONE = -1, PISTOLS = 0, LANCE = 1, HAMMER = 2, KATANA = 3, GAUNTLETS = 4, KNIFE = 5, AXE = 6, MACE = 7,
            GREATSWORD = 8, SHORTSWORDS = 9, SHOTGUN = 10, DURANDAL = 11, WEAPONS = 12;

    /** Weapons held in both hands at once. */
    public static boolean pair(int w) {
        return w == PISTOLS || w == GAUNTLETS || w == SHORTSWORDS;
    }

    // ---- beats, in script ticks
    public static final int CUTIN = 0, DASH = 20,
            PISTOLS_T = 22, LANCE_T = 34, HAMMER_T = 48, KATANA_T = 60, GAUNTLETS_T = 72, KNIFE_T = 82, AXE_T = 92, MACE_T = 104,
            GREAT_T = 118, TWIN_T = 134, SHOTGUN_T = 152, DURANDAL_T = 166, DRAW_END = 178,
            LANCE_PUSH = 42, IAI = 66, LAUNCH = 109, SLAM = 126, BLAST = 157, CUT1 = 183, CUT2 = 187, KILL = 196, END = 206, TOTAL = 216;

    /** Start of each weapon's beat, indexed by weapon id. */
    public static final int[] INTROS = {PISTOLS_T, LANCE_T, HAMMER_T, KATANA_T, GAUNTLETS_T, KNIFE_T, AXE_T, MACE_T, GREAT_T, TWIN_T, SHOTGUN_T, DURANDAL_T};

    /** Weapon in hand at a script time. */
    public static int weapon(double s) {
        if (s < PISTOLS_T || s >= END) return NONE;
        for (int w = WEAPONS - 1; w >= 0; w--) if (s >= INTROS[w]) return w;
        return NONE;
    }

    /** When this beat's weapon is tossed away (the next weapon's beat, or the end). */
    public static int tossAt(int w) {
        return w + 1 < WEAPONS ? INTROS[w + 1] : END;
    }

    // ---- hits
    public record Hit(double time, int weapon, float damage, boolean hard, boolean right) {}

    public static final List<Hit> HITS = new ArrayList<>();

    static {
        // Atelier Logic: pierce, pierce, one shot from each pistol
        HITS.add(new Hit(26, PISTOLS, 3, false, true));
        HITS.add(new Hit(29, PISTOLS, 3, false, false));
        // Allas Workshop: two thrusts, the second drives them back
        HITS.add(new Hit(39, LANCE, 4, false, true));
        HITS.add(new Hit(LANCE_PUSH, LANCE, 5, true, true));
        // Old Boys Workshop: guard, then the smash
        HITS.add(new Hit(55, HAMMER, 7, true, true));
        // Mook Workshop: one draw cut
        HITS.add(new Hit(IAI, KATANA, 9, true, true));
        // Ranga Workshop: the gauntlets jab twice, then the knife slashes
        HITS.add(new Hit(75, GAUNTLETS, 3, false, true));
        HITS.add(new Hit(77.5, GAUNTLETS, 3, false, false));
        HITS.add(new Hit(86, KNIFE, 4, false, true));
        // Zelkova Workshop: the axe, then the mace sends them up
        HITS.add(new Hit(97, AXE, 5, false, true));
        HITS.add(new Hit(LAUNCH, MACE, 5, true, true));
        // Wheels Industry: brought down from the air
        HITS.add(new Hit(SLAM, GREATSWORD, 14, true, true));
        // Crystal Atelier: (evade) slash, slash, counter
        HITS.add(new Hit(140, SHORTSWORDS, 3, false, true));
        HITS.add(new Hit(142, SHORTSWORDS, 3, false, false));
        HITS.add(new Hit(146, SHORTSWORDS, 4, true, true));
        // Atelier Logic: the shotgun, point blank
        HITS.add(new Hit(BLAST, SHOTGUN, 9, true, true));
        // Durandal: two cuts... and Furioso
        HITS.add(new Hit(CUT1, DURANDAL, 6, false, true));
        HITS.add(new Hit(CUT2, DURANDAL, 6, false, true));
        HITS.add(new Hit(KILL, DURANDAL, 250, true, true));
    }

    /** Hits landed by script time s. */
    public static int hitsBy(double s) {
        int n = 0;
        for (Hit h : HITS) if (h.time <= s) n++;
        return n;
    }

    // ---- slow motion: script speed in windows (everything else runs at 1x)
    private static final double[][] SLOW = {
            {CUTIN, DASH, 0.55},              // the title card
            {LANCE_PUSH - 1, LANCE_PUSH + 2, 0.3},
            {54, 57, 0.25},                   // the hammer
            {IAI - 1, IAI + 3, 0.2},          // the draw cut
            {LAUNCH - 1, LAUNCH + 3, 0.3},    // the mace launches them
            {SLAM - 1, SLAM + 3, 0.25},       // the greatsword
            {145, 148, 0.3},                  // the counter
            {BLAST - 1, BLAST + 2, 0.25},     // the shotgun
            {DURANDAL_T + 1, DRAW_END, 0.4},  // Durandal is drawn
            {KILL - 2, KILL + 4, 0.2}};       // Furioso

    public static double speed(double s) {
        for (int beat : INTROS) if (s >= beat && s < beat + 1) return 0.25;
        for (double[] w : SLOW) if (s >= w[0] && s < w[1]) return w[2];
        return 1;
    }

    /** True inside a slow-motion window (for sound pitch and screen grade). */
    public static boolean slow(double s) {
        return speed(s) < 0.99;
    }

    private static final double[] TABLE;
    public static final int REAL_TOTAL;

    static {
        List<Double> t = new ArrayList<>();
        double s = 0;
        t.add(0.0);
        while (s < TOTAL) {
            for (int i = 0; i < 20; i++) s += speed(s) * 0.05;
            t.add(Math.min(TOTAL, s));
        }
        TABLE = t.stream().mapToDouble(Double::doubleValue).toArray();
        REAL_TOTAL = TABLE.length - 1;
    }

    /** Script time at a real time (ticks since the start, with partials). */
    public static double scriptTime(double real) {
        if (real <= 0) return 0;
        if (real >= REAL_TOTAL) return TOTAL;
        int i = (int) real;
        double k = real - i;
        return TABLE[i] + (TABLE[i + 1] - TABLE[i]) * k;
    }

    // ---- choreography
    public final Vec3 p0, g, f, r;
    private static final Vec3 UP = new Vec3(0, 1, 0);

    public FuriosoScript(Vec3 p0, Vec3 m0, double groundY) {
        this.p0 = p0;
        this.g = new Vec3(m0.x, groundY, m0.z);
        Vec3 d = new Vec3(m0.x - p0.x, 0, m0.z - p0.z);
        this.f = d.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : d.normalize();
        this.r = f.cross(UP).normalize();
    }

    static double k(double s, double a, double b) {
        double t = Math.max(0, Math.min(1, (s - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    static double out(double s, double a, double b) {
        double t = Math.max(0, Math.min(1, (s - a) / (b - a)));
        return 1 - (1 - t) * (1 - t);
    }

    static double in(double s, double a, double b) {
        double t = Math.max(0, Math.min(1, (s - a) / (b - a)));
        return t * t;
    }

    static Vec3 lerp(Vec3 a, Vec3 b, double t) {
        return a.add(b.subtract(a).scale(t));
    }

    /** Where the victim gets pushed: by the lance, then by the shotgun. */
    public Vec3 g1() {
        return g.add(f.scale(3));
    }

    public Vec3 g2() {
        return g1().add(f.scale(2.5));
    }

    Vec3 air() {
        return g1().add(0, 4.5, 0);
    }

    /** Victim's feet at script time s. */
    public Vec3 victim(double s) {
        if (s < LANCE_PUSH) return g;
        if (s < HAMMER_T) return lerp(g, g1(), out(s, LANCE_PUSH, LANCE_PUSH + 4));
        if (s < LAUNCH) return g1();
        if (s < SLAM) return lerp(g1(), air(), out(s, LAUNCH, LAUNCH + 7)).add(0, s > LAUNCH + 7 ? 0.15 * Math.sin((s - LAUNCH) * 0.4) : 0, 0);
        if (s < TWIN_T) return lerp(air(), g1(), in(s, SLAM, SLAM + 2));
        if (s < BLAST) return g1();
        if (s < DURANDAL_T) return lerp(g1(), g2(), out(s, BLAST, BLAST + 4)).add(0, Math.sin(Math.min(1, (s - BLAST) / 4) * Math.PI) * 0.8, 0);
        return g2();
    }

    /**
     * Where Roland is, as keys relative to the victim: {script tick, along f, along r, height above the ground}.
     * He never stops: he strafes while shooting, dashes in and out, cuts through and lands behind, circles to a new
     * side for the next weapon. Catmull-Rom between keys, so every dash flows into the next move.
     */
    private static final double[][] PATH = {
            {PISTOLS_T, -6, 1.5, 0}, {24, -5.5, 2.5, 0}, {29, -5, -1.5, 0}, {33, -4.5, -2, 0},               // strafing, firing
            {36, -4.2, -1.8, 0}, {38, -2.0, -0.4, 0}, {40, -2.6, -0.6, 0}, {LANCE_PUSH, -1.9, 0, 0}, {46, -2.2, 0.5, 0}, // lance lunges
            {HAMMER_T, -2.6, 1.8, 0}, {52, -2.0, 1.4, 0}, {55, -1.6, 1.0, 0}, {58, -3.5, 1.5, 0},           // round to the right, smash
            {KATANA_T, -4, 0.5, 0}, {64, -3.6, 0.3, 0}, {65, -1.0, 0.1, 0}, {IAI, 2.2, -0.2, 0}, {68, 3.0, -0.5, 0}, // iai: through them
            {GAUNTLETS_T, 2.0, -0.3, 0}, {75, 1.5, -0.4, 0}, {77.5, 1.4, 0.2, 0},                          // from behind
            {80, 1.2, 1.5, 0}, {84, 0.3, 1.6, 0}, {86, 0.2, 1.4, 0}, {88, -0.5, 1.8, 0},                   // circling, the knife
            {AXE_T, -1.6, 1.2, 0}, {95, -1.5, 1.0, 0}, {97, -1.4, 0.8, 0},
            {MACE_T, -1.5, 0.4, 0}, {107, -1.3, 0.2, 0}, {LAUNCH, -1.3, 0.2, 0},
            {GREAT_T, -1.8, 0.2, 0}, {123, -1.8, 0.2, 5.0}, {SLAM, -1.2, 0, 4.0}, {130, -1.4, 0, 0},       // up after them, and down
            {TWIN_T, -1.6, 0, 0}, {137, -3.2, 1.2, 0}, {139, -1.2, -0.4, 0}, {140, -1.0, -0.6, 0},          // evade, then a flurry
            {142, -0.9, 0.5, 0}, {145, -1.4, 1.2, 0}, {146, -1.0, 0.9, 0}, {150, -2.0, 0.4, 0},
            {SHOTGUN_T, -2.5, 0.2, 0}, {155, -1.4, 0, 0}, {BLAST, -1.2, 0, 0}, {158, -3.6, 0, 0},         // point blank; they fly
            {DURANDAL_T, -4.5, 0, 0}, {DRAW_END, -4.5, 0, 0}, {182, -1.6, 0.3, 0}, {CUT1, -1.4, 0.4, 0},    // draw, cut, cut
            {185, -1.6, -0.6, 0}, {CUT2, -1.4, -0.5, 0}, {190, -4.5, 0.2, 0}, {194, -2.0, 0, 0},
            {KILL, 3.4, 0, 0}, {200, 3.6, 0, 0}, {TOTAL, 3.6, 0, 0}};                                     // Furioso: through

    /** Player's feet at script time s. */
    public Vec3 player(double s) {
        if (s < DASH) return p0;
        Vec3 v = victim(Math.min(s, KILL));
        Vec3 base = new Vec3(v.x, g.y, v.z);
        if (s < PATH[0][0]) {
            Vec3 first = base.add(f.scale(PATH[0][1])).add(r.scale(PATH[0][2]));
            return lerp(p0, first, k(s, DASH, PATH[0][0]));
        }
        int i = 0;
        while (i < PATH.length - 2 && s > PATH[i + 1][0]) i++;
        double[] a = PATH[Math.max(0, i - 1)], b = PATH[i], c = PATH[i + 1], d = PATH[Math.min(PATH.length - 1, i + 2)];
        double u = Math.max(0, Math.min(1, (s - b[0]) / Math.max(1e-4, c[0] - b[0])));
        double along = catmull(a[1], b[1], c[1], d[1], u), across = catmull(a[2], b[2], c[2], d[2], u), up = Math.max(0, catmull(a[3], b[3], c[3], d[3], u));
        return base.add(f.scale(along)).add(r.scale(across)).add(0, up, 0);
    }

    static double catmull(double p0, double p1, double p2, double p3, double u) {
        double u2 = u * u, u3 = u2 * u;
        return 0.5 * (2 * p1 + (-p0 + p2) * u + (2 * p0 - 5 * p1 + 4 * p2 - p3) * u2 + (-p0 + 3 * p1 - 3 * p2 + p3) * u3);
    }

    /** The way the player faces at script time s: at the victim, except while cutting through them. */
    public Vec3 facing(double s) {
        if (s < PISTOLS_T || (s >= IAI - 1 && s < IAI + 4) || s >= KILL - 1) return f;
        Vec3 d = victim(s).subtract(player(s));
        d = new Vec3(d.x, 0, d.z);
        return d.lengthSqr() < 0.16 ? f : d.normalize();
    }

    /** The player's right at script time s. */
    public Vec3 right(double s) {
        return facing(s).cross(UP).normalize();
    }

    public static float yaw(Vec3 dir) {
        return (float) (Math.toDegrees(Math.atan2(dir.z, dir.x)) - 90);
    }
}
