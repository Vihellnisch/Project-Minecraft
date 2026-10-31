# Generates the E.G.O and Black Silence item sprites in assets/projectminecraft/textures/item.
# Usage: powershell -ExecutionPolicy Bypass -File tools\gen_textures.ps1 [-Out <folder>]
# -Out writes to another folder (useful when the project path is too long for GDI+), mirroring src/main/resources.
param([string]$Out = (Join-Path (Get-Location) "src\main\resources"))
$ErrorActionPreference = "Stop"
Add-Type -ReferencedAssemblies System.Drawing -TypeDefinition @'
using System;
using System.Collections.Generic;
using System.Drawing;
using System.Drawing.Imaging;

public static class Tex {
    // ---------------------------------------------------------------- helpers
    static Random rng = new Random(1337);

    static Bitmap New(int w, int h) {
        var b = new Bitmap(w, h, PixelFormat.Format32bppArgb);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) b.SetPixel(x, y, Color.FromArgb(0, 0, 0, 0));
        return b;
    }

    static Color C(double r, double g, double b, double a = 255) {
        return Color.FromArgb(Clamp(a), Clamp(r), Clamp(g), Clamp(b));
    }

    static int Clamp(double v) { return (int)Math.Max(0, Math.Min(255, Math.Round(v))); }

    static Color Hex(string h) {
        return Color.FromArgb(255, Convert.ToInt32(h.Substring(0, 2), 16), Convert.ToInt32(h.Substring(2, 2), 16), Convert.ToInt32(h.Substring(4, 2), 16));
    }

    static double Hash(int x, int y, int s) {
        unchecked {
            int h = x * 374761393 + y * 668265263 + s * 982451653;
            h = (h ^ (h >> 13)) * 1274126177;
            h ^= h >> 16;
            return (h & 0x7fffffff) / (double)0x7fffffff;
        }
    }

    // value noise, tileable over period p
    static double Noise(double x, double y, int p, int seed) {
        int x0 = (int)Math.Floor(x), y0 = (int)Math.Floor(y);
        double fx = x - x0, fy = y - y0;
        fx = fx * fx * (3 - 2 * fx); fy = fy * fy * (3 - 2 * fy);
        Func<int, int, double> v = (a, b) => Hash(((a % p) + p) % p, ((b % p) + p) % p, seed);
        double a0 = v(x0, y0) + (v(x0 + 1, y0) - v(x0, y0)) * fx;
        double a1 = v(x0, y0 + 1) + (v(x0 + 1, y0 + 1) - v(x0, y0 + 1)) * fx;
        return a0 + (a1 - a0) * fy;
    }

    static double Fbm(double x, double y, int p, int seed) {
        double s = 0, a = 0.5;
        for (int i = 0; i < 4; i++) { s += a * Noise(x, y, p, seed + i * 31); x *= 2; y *= 2; p *= 2; a *= 0.5; }
        return s / 0.9375;
    }

    static void Save(Bitmap b, string path) {
        System.IO.Directory.CreateDirectory(System.IO.Path.GetDirectoryName(path));
        b.Save(path, ImageFormat.Png);
        b.Dispose();
    }

    // ---------------------------------------------------------------- pixel art from ASCII
    public static void Ascii(string path, string[] rows, string palette) {
        var map = new Dictionary<char, Color>();
        foreach (var e in palette.Split(' ')) {
            if (e.Length < 3) continue;
            map[e[0]] = Hex(e.Substring(2));
        }
        int h = rows.Length, w = rows[0].Length;
        var b = New(w, h);
        for (int y = 0; y < h; y++) {
            if (rows[y].Length != w) throw new Exception(path + ": row " + y + " has length " + rows[y].Length);
            for (int x = 0; x < w; x++) {
                char ch = rows[y][x];
                if (ch == '.') continue;
                if (!map.ContainsKey(ch)) throw new Exception(path + ": unknown colour '" + ch + "'");
                b.SetPixel(x, y, map[ch]);
            }
        }
        Save(b, path);
    }
}
'@

$root = Join-Path $Out "assets\projectminecraft\textures"

# ---- items (palettes: char=hex)
[Tex]::Ascii("$root\item\fourth_match_flame.png", @(
    "............FFf.",
    "...........FRRF.",
    "..........oRRRRo",
    "..........oRrRRo",
    ".........oCRRRo.",
    "........oCooo...",
    ".......oWo......",
    "......oWo.......",
    ".....oWo........",
    "....oWo.........",
    "...oWo..........",
    "..oWo...........",
    ".oWo............",
    "oNo.............",
    "oo..............",
    "................"
), "o=1a0c08 F=ffb020 f=fff060 R=c81e14 r=ff6a3a C=2a1a14 W=d8b07a N=3a2a2a")

[Tex]::Ascii("$root\item\solemn_lament.png", @(
    "................",
    ".ooooooooooo....",
    "oWWWWWWWWWWWo...",
    "oWWWKoooooooo...",
    "oWWo............",
    "oKKo..ooooooooo.",
    "oKKo.oDDDDDDDDDo",
    ".oo..oooooooDDDo",
    "............oDDo",
    "............oPPo",
    "............oPPo",
    ".............oo.",
    "................",
    "................",
    "................",
    "................"
), "o=0a0a0e W=eeeeee K=3a3a44 D=26262e P=bfe8ff")

[Tex]::Ascii("$root\item\magic_bullet.png", @(
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
    "......oooooooooo",
    "oooooBBBBrBBBBBo",
    "oWWWWGGBooooooo.",
    "oWWWWWGo........",
    ".oWWWoGo........",
    "..ooo.o.........",
    "................",
    "................",
    "................",
    "................"
), "o=101018 B=1e2a4a W=5a3418 G=c8a050 r=4a8aff")

[Tex]::Ascii("$root\item\black_silence.png", @(
    "................",
    "....oooooo......",
    "...oKGKGKo......",
    "..oKKKKKKKo.....",
    "..oKKKKKKKoo....",
    "..oSSSSSSKKo....",
    "..oKKKKKKKKo....",
    "...oKKyKKKo.....",
    "...oGGGGGGo.....",
    "...oBBBBBBo.....",
    "...oBBYBBBo.....",
    "...oDDDDDDo.....",
    "...oBBYBBBo.....",
    "...oGGGGGGo.....",
    "...oooooooo.....",
    "................"
), "o=050506 K=2a2a30 G=c8a040 S=48484e B=141418 D=0a0a0c Y=e8c060 y=fff0c0")
