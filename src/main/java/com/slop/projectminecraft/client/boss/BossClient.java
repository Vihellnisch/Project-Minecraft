package com.slop.projectminecraft.client.boss;

import com.slop.projectminecraft.boss.Roland;
import com.slop.projectminecraft.client.CinematicCamera;
import com.slop.projectminecraft.registry.ModSounds;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Boss subtitles and name cards (cut-in banners). */
public final class BossClient {
    private BossClient() {}

    /** Who can talk: entity id path (lang keys projectminecraft.boss.<id>.*) and subtitle colour. FxPayload BOSS_SAY pos2.x picks one. */
    private static final String[] SPEAKERS = {"roland", "angelica", "claw"};
    private static final int[] SPEAKER_COLORS = {0xB8B8B8, 0xF4F4FF, 0x6A9CFF};
    private static final float CHARS_PER_TICK = 1.6f;

    private static String line = "";
    private static int lineAge, lineLife, speaker;

    // ------------------------------------------------------------------ packets

    public static void say(int id, Vec3 pos, int who) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.position().distanceTo(pos) > 48) return;
        int s = Math.floorMod(who, SPEAKERS.length);
        if (id == Roland.BANNER) {
            bossBanner(Component.translatable("entity.projectminecraft." + SPEAKERS[s]), Component.translatable("projectminecraft.boss." + SPEAKERS[s] + ".title"), SPEAKER_COLORS[s]);
            return;
        }
        speaker = s;
        line = Component.translatable("projectminecraft.boss." + SPEAKERS[s] + ".line." + id).getString();
        lineAge = 0;
        lineLife = (int) (line.length() / CHARS_PER_TICK) + 45;
    }

    // ------------------------------------------------------------------ subtitles

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        if (lineAge < lineLife) lineAge++;
        BANNERS.removeIf(b -> ++b.age >= b.life);
    }

    public static void renderSubtitle(GuiGraphics g, DeltaTracker tracker) {
        if (lineAge >= lineLife || line.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        float a = lineAge + tracker.getGameTimeDeltaPartialTick(true);
        int alpha = (int) (255 * Mth.clamp((lineLife - a) / 10f, 0, 1));
        if (alpha < 8) return;
        String shown = line.substring(0, Math.min(line.length(), (int) (a * CHARS_PER_TICK)));
        String who = Component.translatable("entity.projectminecraft." + SPEAKERS[speaker]).getString() + ": ";
        int w = g.guiWidth(), h = g.guiHeight();
        int tw = font.width(who) + font.width(line);
        int x = (w - tw) / 2, y = h - (CinematicCamera.active() ? 52 : 72);
        g.fill(x - 6, y - 4, x + tw + 6, y + 12, (alpha * 3 / 5) << 24);
        g.drawString(font, who, x, y, (alpha << 24) | SPEAKER_COLORS[speaker], true);
        g.drawString(font, shown, x + font.width(who), y, (alpha << 24) | 0xFFFFFF, true);
    }

    // ------------------------------------------------------------------ banners

    private static final class Banner {
        final Component title, sub;
        final int color, life;
        final boolean big;
        final int casterId;
        int age;

        Banner(Component title, Component sub, int color, int life, boolean big) {
            this(title, sub, color, life, big, -1);
        }

        Banner(Component title, Component sub, int color, int life, boolean big, int casterId) {
            this.title = title;
            this.sub = sub;
            this.color = color;
            this.life = life;
            this.big = big;
            this.casterId = casterId;
        }
    }

    private static final List<Banner> BANNERS = new ArrayList<>();

    /** A boss name card: a big cut-in band with a sting. */
    public static void bossBanner(Component name, Component sub, int color) {
        BANNERS.add(new Banner(name, sub, color, 70, true));
        while (BANNERS.size() > 3) BANNERS.remove(0);
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(ModSounds.STING.get(), 0.8f, 1.0f));
    }

    /** Cut-in banners (drawn even during cutscenes). */
    public static void renderBanners(GuiGraphics g, DeltaTracker tracker) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        float partial = tracker.getGameTimeDeltaPartialTick(true);
        int w = g.guiWidth(), h = g.guiHeight();
        for (Banner b : BANNERS) renderCutIn(g, font, b, b.age + partial, w, h);
    }

    private static void renderCutIn(GuiGraphics g, Font font, Banner b, float a, int w, int h) {
        float in = Mth.clamp(a / 5f, 0, 1), out = Mth.clamp((b.life - a) / 6f, 0, 1);
        float ease = 1 - (1 - in) * (1 - in) * (1 - in);
        int alpha = (int) (230 * out);
        if (alpha < 6) return;
        float slide = (1 - ease) * w * 1.2f - (1 - out) * w * 0.4f;
        int cy = h / 2 - 50;
        g.pose().pushPose();
        g.pose().translate(w / 2f + slide, cy, 0);
        g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-6));
        int band = 34;
        g.fill(-w, -band / 2 - 3, w, band / 2 + 3, (alpha << 24));
        g.fill(-w, -band / 2, w, band / 2, (Math.min(255, alpha) << 24) | (b.color & 0xFFFFFF));
        g.fill(-w, -band / 2 + 2, w, -band / 2 + 4, ((alpha / 2) << 24) | 0xFFFFFF);
        // speed lines racing across the band
        for (int i = 0; i < 9; i++) {
            float lx = ((a * 40 + i * 137) % (w * 2)) - w;
            int ly = -band / 2 + 3 + (i * 7) % (band - 6);
            g.fill((int) lx, ly, (int) lx + 30 + i * 6, ly + 1, ((alpha / 2) << 24) | 0xFFFFFF);
        }
        String title = b.title.getString().toUpperCase();
        float s = 2.6f;
        g.pose().pushPose();
        g.pose().scale(s, s, 1);
        int tw = font.width(title);
        g.drawString(font, title, -tw / 2 + 1, -5, (alpha << 24), false);
        g.drawString(font, title, -tw / 2, -6, (alpha << 24) | 0xFFFFFF, false);
        g.pose().popPose();
        String sub = b.sub.getString();
        g.drawString(font, sub, -font.width(sub) / 2, band / 2 + 6, (alpha << 24) | 0xFFFFFF, true);
        face(g, b, -tw * s / 2 - 74 - (1 - ease) * 260, -30, 56, alpha);
        g.pose().popPose();
    }

    /** Anime cut-in portrait: the caster's own skin face, framed, sliding in against the band. */
    private static void face(GuiGraphics g, Banner b, float x, int y, int size, int alpha) {
        if (b.casterId < 0 || alpha < 40) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !(mc.level.getEntity(b.casterId) instanceof net.minecraft.client.player.AbstractClientPlayer p)) return;
        int ix = (int) x;
        g.fill(ix - 4, y - 4, ix + size + 4, y + size + 4, 0xFF000000);
        g.fill(ix - 2, y - 2, ix + size + 2, y + size + 2, 0xFF000000 | (b.color & 0xFFFFFF));
        net.minecraft.client.gui.components.PlayerFaceRenderer.draw(g, p.getSkin(), ix, y, size);
        // a glint sweeping across the portrait
        int gx = ix + (int) ((b.age * 9) % (size * 3)) - size;
        if (gx > ix - 6 && gx < ix + size) g.fill(Math.max(ix, gx), y, Math.min(ix + size, gx + 6), y + size, 0x60FFFFFF);
    }

    public static void clear() {
        line = "";
        lineAge = lineLife = 0;
        BANNERS.clear();
    }
}
