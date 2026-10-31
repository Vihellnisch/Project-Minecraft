package com.slop.projectminecraft.client.arsenal;

import static com.slop.projectminecraft.arsenal.ArsenalTiming.*;

import com.slop.projectminecraft.arsenal.ArsenalEvents;
import com.slop.projectminecraft.client.CinematicCamera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;

/** HUD of the Black Silence and the E.G.O: hit markers, the twelve weapons, ink-frame slashes, the E.G.O cut-in and Furioso. */
public final class ArsenalHud {
    private ArsenalHud() {}

    public static void render(GuiGraphics g, float partial) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || mc.options.hideGui) return;
        int w = g.guiWidth(), h = g.guiHeight();
        Font font = mc.font;
        boolean cine = CinematicCamera.locksInput();

        if (!cine) {
            hitmarker(g, w, h);
            if (com.slop.projectminecraft.arsenal.Gloves.worn(p) && !FuriosoClient.active(p.getId())) gloves(g, font, w, h, partial);
        }
        ink(g, w, h);
        ego(g, font, w, h, partial);
        furioso(g, font, w, h, partial);
    }

    static void hitmarker(GuiGraphics g, int w, int h) {
        if (ArsenalClient.hitmarker <= 0) return;
        int c = ArsenalClient.hitmarkerKind == 2 ? 0xFFFFD040 : ArsenalClient.hitmarkerKind == 1 ? 0xFFFF3030 : 0xFFFFFFFF;
        int cx = w / 2, cy = h / 2, s = 3 + ArsenalClient.hitmarker / 2;
        for (int i = 0; i < 4; i++) {
            int dx = (i & 1) == 0 ? 1 : -1, dy = (i & 2) == 0 ? 1 : -1;
            for (int k = 2; k < s + 2; k++) g.fill(cx + dx * k, cy + dy * k, cx + dx * k + 1, cy + dy * k + 1, c);
        }
    }
    /** Impact frames: huge slash lines across the screen while the ink frames flash. */
    static void ink(GuiGraphics g, int w, int h) {
        int ink = ArsenalClient.ink();
        if (ink == 0) return;
        java.util.Random r = new java.util.Random((long) (ArsenalClient.inkSeed() * 1000) + ink);
        int col = ink == 1 ? 0xFFFFFFFF : ink == 2 ? 0xFF000000 : 0xFFFFE0E0;
        for (int i = 0; i < 5; i++) {
            float ang = (float) Math.toRadians(-35 + r.nextInt(70) + (r.nextBoolean() ? 0 : 180));
            float cx = w * (0.15f + 0.7f * r.nextFloat()), cy = h * (0.2f + 0.6f * r.nextFloat());
            float len = w * (0.6f + 0.5f * r.nextFloat());
            g.pose().pushPose();
            g.pose().translate(cx, cy, 0);
            g.pose().mulPose(com.mojang.math.Axis.ZP.rotation(ang));
            int th = 1 + r.nextInt(3);
            g.fill((int) (-len / 2), -th, (int) (len / 2), th, col);
            g.fill((int) (-len / 2.6f), -th - 1, (int) (len / 2.6f), -th, (col & 0xFFFFFF) | 0x80000000);
            g.pose().popPose();
        }
    }

    /** The E.G.O cut-in: sin-coloured bands, your face, the name. Then the coins and their power. */
    static void ego(GuiGraphics g, Font font, int w, int h, float partial) {
        EgoClient.Ego e = EgoClient.mine();
        if (e == null) return;
        float t = e.age + partial;
        int color = EgoClient.COLORS[e.kind];
        if (t < EGO_CUTIN + 4) {
            float in = Mth.clamp(t / 5f, 0, 1), out = Mth.clamp((EGO_CUTIN + 4 - t) / 6f, 0, 1);
            float a = Math.min(in, out);
            int alpha = (int) (255 * a);
            g.pose().pushPose();
            g.pose().translate(w / 2f, h / 2f, 0);
            g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-12));
            // bands sweeping in from the sides
            float slide = (1 - in) * w;
            g.fill((int) (-w - slide), -46, (int) (w - slide), 34, ((alpha * 3 / 4) << 24) | (color & 0xFFFFFF));
            g.fill((int) (-w + slide), 34, (int) (w + slide), 40, (alpha << 24) | 0xFFFFFF);
            g.fill((int) (-w + slide), -52, (int) (w + slide), -46, (alpha << 24));
            // the face
            PlayerInfo info = Minecraft.getInstance().getConnection() == null ? null : Minecraft.getInstance().getConnection().getPlayerInfo(Minecraft.getInstance().player.getUUID());
            if (info != null) {
                int size = 64;
                net.minecraft.client.gui.components.PlayerFaceRenderer.draw(g, info.getSkin(), (int) (-w / 2f + 24 - slide * 0.5f), -40, size);
            }
            g.pose().pushPose();
            g.pose().translate(-w / 2f + 100 + slide * 0.3f, -26, 0);
            g.drawString(font, "E.G.O  ::  " + EgoClient.SINS[e.kind], 0, -10, (alpha << 24) | 0xFFFFFF, true);
            g.pose().scale(3.2f, 3.2f, 1);
            g.drawString(font, EgoClient.NAMES[e.kind], 1, 1, (alpha << 24), false);
            g.drawString(font, EgoClient.NAMES[e.kind], 0, 0, (alpha << 24) | 0xFFFFFF, false);
            g.pose().popPose();
            String who = Minecraft.getInstance().player.getName().getString();
            g.drawString(font, who, (int) (-w / 2f + 100 + slide * 0.3f), 18, (alpha << 24) | 0xE0E0E0, true);
            g.pose().popPose();
        }
        // coins and power, top centre, during the attack
        if (t > 8 && t < e.total() - 6) {
            int n = e.coins;
            int cx = w / 2, y = CinematicCamera.active() ? (int) (h * 0.12f) + 16 : 22;
            int pw = e.power(t);
            String ps = String.valueOf(pw);
            g.pose().pushPose();
            g.pose().translate(cx - n * 9 - 18, y, 0);
            g.pose().scale(2, 2, 1);
            g.drawString(font, ps, -font.width(ps) / 2, -4, 0xFF000000 | color, true);
            g.pose().popPose();
            for (int i = 0; i < n; i++) {
                int x = cx - (n - 1) * 9 + i * 18;
                int flip = e.flipTick(i);
                boolean landed = t >= flip;
                float spin = landed ? 1 : Math.abs(Mth.cos(t * 0.9f + i));
                int rad = 6;
                int wdt = Math.max(1, Math.round(rad * spin));
                int c = !landed ? 0xFFE0B040 : e.head(i) ? 0xFFFFD040 : 0xFF707070;
                g.fill(x - wdt, y - rad, x + wdt, y + rad, 0xFF000000);
                g.fill(x - wdt + 1, y - rad + 1, x + wdt - 1, y + rad - 1, c);
                if (landed && t < flip + 12) {
                    float a = 1 - (t - flip) / 12f;
                    String s = e.head(i) ? "+" + EgoClient.COIN_POWER[e.kind] : "-";
                    g.drawString(font, s, x - font.width(s) / 2, (int) (y - 18 - (t - flip)), ((int) (255 * a) << 24) | (e.head(i) ? 0xFFE060 : 0xA0A0A0), true);
                }
            }
            if (e.kind == ArsenalEvents.MAGIC_BULLET) {
                String b = "BULLET " + e.bullet + " / 7";
                g.drawString(font, b, cx - font.width(b) / 2, y + 12, e.bullet == 7 ? 0xFFFF4060 : 0xFF8090FF, true);
            }
        }
    }

    /** Furioso: the title card, the hit counter, a caption for each new weapon, and the final stamp. */
    private static final net.minecraft.resources.ResourceLocation CROSSHAIR_BG = net.minecraft.resources.ResourceLocation.withDefaultNamespace("hud/crosshair_attack_indicator_background"),
            CROSSHAIR_PROGRESS = net.minecraft.resources.ResourceLocation.withDefaultNamespace("hud/crosshair_attack_indicator_progress");

    /** Black Silence worn: the twelve weapons right of the hotbar, lit once used on the current enemy, and the swap caption. */
    static void gloves(GuiGraphics g, Font font, int w, int h, float partial) {
        int weapons = com.slop.projectminecraft.arsenal.FuriosoScript.WEAPONS, sel = GloveClient.mine();
        boolean ready = GloveClient.ready();
        float time = Minecraft.getInstance().level == null ? 0 : Minecraft.getInstance().level.getGameTime() + partial;
        int x0 = w / 2 + 98, y = h - 19;
        for (int i = 0; i < weapons; i++) {
            int x = x0 + i * 11;
            boolean used = (GloveClient.mask & (1 << i)) != 0;
            int border = i == sel ? 0xFFFFFFFF : used ? GloveWheel.GOLD : 0x80303030;
            g.fill(x - 1, y - 1, x + 10, y + 10, border);
            g.fill(x, y, x + 9, y + 9, used ? 0xC0302814 : 0xA0101010);
            GloveWheel.icon(g, i, x + 4.5f, y + 4.5f, 9);
            // still cooling down: shaded from the top, like the hotbar's item cooldowns
            int cd = Math.round(9 * GloveClient.cooldown(i, partial));
            if (cd > 0) g.fill(RenderType.guiOverlay(), x, y, x + 9, y + cd, 0x90FFFFFF);
        }
        // the weapon's cooldown under the crosshair, drawn like vanilla's attack indicator
        float cd = GloveClient.cooldown(sel, partial);
        if (cd > 0 && Minecraft.getInstance().options.getCameraType().isFirstPerson()) {
            int bx = w / 2 - 8, by = h / 2 - 7 + 16;
            g.blitSprite(CROSSHAIR_BG, bx, by, 16, 4);
            g.blitSprite(CROSSHAIR_PROGRESS, 16, 4, 0, 0, bx, by, (int) ((1 - cd) * 17), 4);
        }
        if (ready) {
            int a = (int) (180 + 75 * Mth.sin(time * 0.4f));
            String s = "FURIOSO  [" + GloveClient.WHEEL_KEY.getTranslatedKeyMessage().getString() + "]";
            g.drawString(font, s, x0, y - 11, (a << 24) | 0xE8C060, true);
        } else if (GloveClient.target >= 0) {
            g.drawString(font, Integer.bitCount(GloveClient.mask) + "/" + weapons, x0, y - 11, 0xFFB0B0B0, true);
        }
        if (GloveClient.swapAge >= 0) {
            float k = GloveClient.swapAge < 30 ? 1 : (40 - GloveClient.swapAge) / 10f;
            int a = Mth.clamp((int) (255 * k), 8, 255);
            String[] cap = FuriosoClient.CAPTIONS[sel];
            String s = cap[0].isEmpty() ? cap[1] : cap[0] + "  ::  " + cap[1];
            g.drawString(font, s, w / 2 - font.width(s) / 2, h / 2 + 22, (a << 24) | 0xE8C060, true);
        }
    }

    static void furioso(GuiGraphics g, Font font, int w, int h, float partial) {
        FuriosoClient.Run r = FuriosoClient.mine();
        if (r == null) return;
        double t = r.st(partial);
        int gold = 0xE8C060;
        if (t < com.slop.projectminecraft.arsenal.FuriosoScript.DASH + 3) {
            float in = Mth.clamp((float) t / 4f, 0, 1), out = Mth.clamp((float) (com.slop.projectminecraft.arsenal.FuriosoScript.DASH + 3 - t) / 5f, 0, 1);
            int a = (int) (255 * Math.min(in, out));
            float slide = (1 - in) * w * 0.4f;
            int cy = h / 2;
            g.fill(0, cy - 34, w, cy + 34, (Math.min(230, a) << 24));
            g.fill(0, cy - 36, w, cy - 34, (a << 24) | gold);
            g.fill(0, cy + 34, w, cy + 36, (a << 24) | gold);
            PlayerInfo info = Minecraft.getInstance().getConnection() == null ? null : Minecraft.getInstance().getConnection().getPlayerInfo(Minecraft.getInstance().player.getUUID());
            if (info != null) net.minecraft.client.gui.components.PlayerFaceRenderer.draw(g, info.getSkin(), (int) (24 - slide), cy - 28, 56);
            String title = "F U R I O S O";
            g.pose().pushPose();
            g.pose().translate(w / 2f + slide, cy - 12, 0);
            g.pose().scale(3.4f, 3.4f, 1);
            g.drawString(font, title, -font.width(title) / 2, 0, (a << 24) | gold, false);
            g.pose().popPose();
            String sub = "Black Silence  ::  " + Minecraft.getInstance().player.getName().getString();
            g.drawString(font, sub, w / 2 - font.width(sub) / 2 + (int) slide, cy + 20, (a << 24) | 0xC8C8C8, false);
            return;
        }
        // hits
        int hits = com.slop.projectminecraft.arsenal.FuriosoScript.hitsBy(t);
        if (hits > 0 && t < com.slop.projectminecraft.arsenal.FuriosoScript.END + 6) {
            float pulse = r.pulse;
            String n = String.valueOf(hits);
            g.pose().pushPose();
            g.pose().translate(w - 70, h * 0.32f, 0);
            float sc = 3.2f + 0.8f * pulse;
            g.pose().scale(sc, sc, 1);
            g.drawString(font, n, -font.width(n) / 2 + 1, 1, 0xFF000000, false);
            g.drawString(font, n, -font.width(n) / 2, 0, 0xFF000000 | (pulse > 0.5f ? 0xFFFFFF : gold), false);
            g.pose().popPose();
            g.drawString(font, "HITS", w - 70 - font.width("HITS") / 2, (int) (h * 0.32f) + 32, 0xFFC8C8C8, true);
        }
        // the weapon's name, lower third
        float[] cap = FuriosoClient.caption(r, partial);
        if (cap != null) {
            String[] c = FuriosoClient.CAPTIONS[(int) cap[0]];
            float k = cap[1];
            int a = (int) (255 * Math.min(Mth.clamp(k * 8, 0, 1), Mth.clamp((1 - k) * 4, 0, 1)));
            if (a > 8) {
                int y = (int) (h * 0.74f);
                int slide = (int) ((1 - Mth.clamp(k * 6, 0, 1)) * -120);
                int bw = Math.max(font.width(c[0]), font.width(c[1]) * 2) + 24;
                g.fill(24 + slide, y - 4, 24 + slide + bw, y + 30, (Math.min(200, a) << 24));
                g.fill(24 + slide, y - 4, 27 + slide, y + 30, (a << 24) | gold);
                if (!c[0].isEmpty()) g.drawString(font, c[0], 34 + slide, y, (a << 24) | gold, false);
                g.pose().pushPose();
                g.pose().translate(34 + slide, y + 10, 0);
                g.pose().scale(2, 2, 1);
                g.drawString(font, c[1], 0, 0, (a << 24) | 0xFFFFFF, false);
                g.pose().popPose();
            }
        }
        // the end
        if (t >= com.slop.projectminecraft.arsenal.FuriosoScript.KILL) {
            float k = (float) (t - com.slop.projectminecraft.arsenal.FuriosoScript.KILL);
            float a = Mth.clamp(k / 2f, 0, 1) * Mth.clamp((r.age - (com.slop.projectminecraft.arsenal.FuriosoScript.REAL_TOTAL - 8) < 0 ? 1 : 1 - (r.age + partial - (com.slop.projectminecraft.arsenal.FuriosoScript.REAL_TOTAL - 8)) / 8f), 0, 1);
            int ai = (int) (255 * a);
            if (ai > 8) {
                g.pose().pushPose();
                g.pose().translate(w / 2f, h * 0.36f, 0);
                g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-5));
                float sc = 5.0f - 1.5f * Mth.clamp(k / 3f, 0, 1);
                g.pose().scale(sc, sc, 1);
                String s = "FURIOSO";
                g.drawString(font, s, -font.width(s) / 2 + 1, 1, (ai << 24), false);
                g.drawString(font, s, -font.width(s) / 2, 0, (ai << 24) | gold, false);
                g.pose().popPose();
                String total = hits + " HITS";
                g.drawString(font, total, w / 2 - font.width(total) / 2, (int) (h * 0.36f) + 34, (ai << 24) | 0xFFFFFF, true);
            }
        }
    }
}
