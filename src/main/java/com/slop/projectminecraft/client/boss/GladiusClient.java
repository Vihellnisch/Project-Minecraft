package com.slop.projectminecraft.client.boss;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.slop.projectminecraft.arsenal.EyeSlot;
import com.slop.projectminecraft.arsenal.GladiusServer;
import com.slop.projectminecraft.client.arsenal.GloveClient;
import com.slop.projectminecraft.client.render.FxRenderTypes;
import com.slop.projectminecraft.network.ArsenalActionPayload;
import com.slop.projectminecraft.network.ArsenalFxPayload;
import com.slop.projectminecraft.registry.ModItems;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Matrix4f;

/**
 * The gladius and the eyes for players: the skill state (synced), the B key (tap: next skill, hold: the wheel), the
 * selected skill over the hotbar, the eye slot in the inventory, and on every player model the eyes' glow and, in E.G.O,
 * the crown, pauldron and cape.
 */
public final class GladiusClient {
    private GladiusClient() {}

    public static int selected, flags, egoLeft;
    static final int[] COOLDOWN_LEFT = new int[GladiusServer.SKILLS], EGO_LEFT = new int[4];
    /** Players (ids) wearing the eyes; and in E.G.O. */
    static final IntOpenHashSet EYES = new IntOpenHashSet(), EGO = new IntOpenHashSet(), EYES_OFF = new IntOpenHashSet();
    /** Which face pixels glow, per player. */
    static final it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap MASKS = new it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap();
    /** This player's own settings (as last synced). */
    public static boolean glow = true;
    public static long mask = EyeSlot.DEFAULT_MASK;
    private static int wheelHeld;
    /** The eye slot in the survival inventory, relative to its top left (above the offhand slot). */
    static final int SLOT_X = 77, SLOT_Y = 44;

    public static boolean holding(Player p) {
        return p != null && p.getMainHandItem().is(ModItems.WHITE_HOT_GLADIUS.get());
    }

    static boolean eyes() {
        return (flags & 1) != 0;
    }

    public static boolean ego() {
        return (flags & 2) != 0;
    }

    /** How many skills are on the wheel now. */
    static int count() {
        return ego() ? 4 : eyes() ? GladiusServer.SKILLS : 4;
    }

    /** Lang key of skill slot `i` as things stand. */
    static String key(int i) {
        return "gui.projectminecraft.gladius." + (ego() ? "ego." : "skill.") + i;
    }

    static int left(int i) {
        return ego() ? EGO_LEFT[i] : COOLDOWN_LEFT[i];
    }

    static int max(int i) {
        return ego() ? Math.max(1, GladiusServer.EGO_COOLDOWN[i]) : GladiusServer.COOLDOWN[i];
    }

    /** From VergiliusClient: FX_HAND (state) and FX_EYES (who wears them). */
    static void handle(ArsenalFxPayload p) {
        Minecraft mc = Minecraft.getInstance();
        if (p.i(0) == EyeSlot.FX_EYES) {
            int who = p.i(1);
            if (p.i(2) == 1) EYES.add(who);
            else EYES.remove(who);
            if (p.i(3) == 0) EYES_OFF.add(who);
            else EYES_OFF.remove(who);
            long m = (p.i(4) & 0xFFFFFFFFL) | ((long) p.i(5) << 32);
            MASKS.put(who, m);
            if (mc.player != null && who == mc.player.getId()) {
                glow = p.i(3) != 0;
                mask = m;
            }
            return;
        }
        int id = p.i(1);
        boolean ego = (p.i(3) & 2) != 0;
        if (ego) EGO.add(id);
        else EGO.remove(id);
        if (mc.player == null || id != mc.player.getId()) return;
        selected = p.i(2);
        flags = p.i(3);
        egoLeft = p.i(4);
        for (int i = 0; i < GladiusServer.SKILLS; i++) COOLDOWN_LEFT[i] = p.i(5 + i);
        for (int i = 0; i < 4; i++) EGO_LEFT[i] = p.i(5 + GladiusServer.SKILLS + i);
    }

    public static void select(int skill) {
        selected = skill;
        PacketDistributor.sendToServer(new ArsenalActionPayload(ArsenalActionPayload.GLADIUS_SELECT, 0, 0, 0, new int[]{skill}, new float[0]));
    }

    @SubscribeEvent
    public static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused()) return;
        for (int i = 0; i < COOLDOWN_LEFT.length; i++) if (COOLDOWN_LEFT[i] > 0) COOLDOWN_LEFT[i]--;
        for (int i = 0; i < EGO_LEFT.length; i++) if (EGO_LEFT[i] > 0) EGO_LEFT[i]--;
        if (egoLeft > 0) egoLeft--;
        boolean usable = mc.screen == null && mc.player != null && holding(mc.player);
        if (!usable) {
            wheelHeld = 0;
            return;
        }
        while (GloveClient.WHEEL_KEY.consumeClick()) {}
        if (GloveClient.WHEEL_KEY.isDown()) {
            if (++wheelHeld == 5) mc.setScreen(new GladiusWheel());
        } else {
            if (wheelHeld > 0 && wheelHeld < 5) select((selected + 1) % count());
            wheelHeld = 0;
        }
    }

    /** Sends this player's eye settings to the server (which shares them). */
    public static void sendLook(boolean on, long m) {
        glow = on;
        mask = m;
        PacketDistributor.sendToServer(new ArsenalActionPayload(ArsenalActionPayload.EYE_LOOK, 0, 0, 0, new int[]{on ? 1 : 0, (int) m, (int) (m >>> 32)}, new float[0]));
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        EYES_OFF.clear();
        MASKS.clear();
        EYES.clear();
        EGO.clear();
        flags = 0;
        selected = 0;
    }

    /** Over the hotbar while the gladius is in hand: the selected skill and its cooldown. */
    public static void renderHud(GuiGraphics g, DeltaTracker tracker) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !holding(mc.player) || mc.options.hideGui) return;
        int i = Math.min(selected, count() - 1), l = left(i);
        String name = Component.translatable(key(i)).getString() + (l > 0 ? "  " + (l + 19) / 20 + "s" : "");
        int x = g.guiWidth() / 2 - mc.font.width(name) / 2, y = g.guiHeight() - 59 - (mc.player.isCreative() ? 0 : 14);
        g.drawString(mc.font, name, x, y, l > 0 ? 0xFF8A8A8A : ego() ? 0xFFFF4040 : 0xFFFF9A4A, true);
        if (ego()) {
            String t = Component.translatable("gui.projectminecraft.gladius.ego_left", (egoLeft + 19) / 20).getString();
            g.drawString(mc.font, t, g.guiWidth() / 2 - mc.font.width(t) / 2, y - 10, 0xFFD02828, true);
        }
    }

    // ------------------------------------------------------------------ the eye slot

    static boolean overSlot(InventoryScreen s, double mx, double my) {
        int x = s.getGuiLeft() + SLOT_X, y = s.getGuiTop() + SLOT_Y;
        return mx >= x - 1 && mx < x + 17 && my >= y - 1 && my < y + 17;
    }

    /** The two small buttons over the slot: 0 = glow on/off, 1 = edit which pixels glow; -1 = neither. */
    static int overButton(InventoryScreen s, double mx, double my) {
        int y = s.getGuiTop() + SLOT_Y - 10;
        if (my < y || my >= y + 8) return -1;
        int x = s.getGuiLeft() + SLOT_X;
        if (mx >= x && mx < x + 8) return 0;
        if (mx >= x + 9 && mx < x + 17) return 1;
        return -1;
    }

    @SubscribeEvent
    public static void onInventoryRender(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof InventoryScreen s)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        GuiGraphics g = event.getGuiGraphics();
        int x = s.getGuiLeft() + SLOT_X, y = s.getGuiTop() + SLOT_Y;
        // a slot frame, an eye drawn in it while empty
        g.fill(x - 1, y - 1, x + 17, y + 17, 0xFF373737);
        g.fill(x, y, x + 17, y + 17, 0xFFFFFFFF);
        g.fill(x, y, x + 16, y + 16, 0xFF8B8B8B);
        boolean worn = EYES.contains(mc.player.getId());
        if (worn) g.renderItem(new ItemStack(ModItems.VERGILIUS_EYES.get()), x, y);
        else {
            g.fill(x + 3, y + 7, x + 13, y + 9, 0xFF6E6E6E);
            g.fill(x + 6, y + 6, x + 10, y + 10, 0xFF5A5A5A);
        }
        // the toggle (a red eye, or a dim one when off) and the edit button (a little grid)
        int by = y - 10;
        g.fill(x, by, x + 8, by + 8, 0xFF373737);
        g.fill(x + 1, by + 1, x + 7, by + 7, 0xFF8B8B8B);
        g.fill(x + 2, by + 3, x + 6, by + 5, glow ? 0xFFE01818 : 0xFF505050);
        g.fill(x + 9, by, x + 17, by + 8, 0xFF373737);
        g.fill(x + 10, by + 1, x + 16, by + 7, 0xFF8B8B8B);
        for (int k = 0; k < 3; k++) for (int j = 0; j < 3; j++) if ((k + j) % 2 == 0) g.fill(x + 10 + k * 2, by + 1 + j * 2, x + 12 + k * 2, by + 3 + j * 2, k == 1 && j == 1 ? 0xFFE01818 : 0xFF5A5A5A);
        int b = overButton(s, event.getMouseX(), event.getMouseY());
        if (b >= 0 && s.getMenu().getCarried().isEmpty())
            g.renderTooltip(mc.font, Component.translatable(b == 0 ? (glow ? "gui.projectminecraft.eye_slot.glow_on" : "gui.projectminecraft.eye_slot.glow_off") : "gui.projectminecraft.eye_slot.edit"), event.getMouseX(), event.getMouseY());
        if (overSlot(s, event.getMouseX(), event.getMouseY())) {
            g.fill(x, y, x + 16, y + 16, 0x80FFFFFF);
            if (s.getMenu().getCarried().isEmpty())
                g.renderTooltip(mc.font, Component.translatable(worn ? "gui.projectminecraft.eye_slot.worn" : "gui.projectminecraft.eye_slot.empty"), event.getMouseX(), event.getMouseY());
        }
    }

    @SubscribeEvent
    public static void onInventoryClick(ScreenEvent.MouseButtonPressed.Pre event) {
        if (!(event.getScreen() instanceof InventoryScreen s) || event.getButton() != 0) return;
        int b = overButton(s, event.getMouseX(), event.getMouseY());
        if (b == 0) {
            sendLook(!glow, mask);
            event.setCanceled(true);
            return;
        }
        if (b == 1) {
            Minecraft.getInstance().setScreen(new EyeEditorScreen(s));
            event.setCanceled(true);
            return;
        }
        if (!overSlot(s, event.getMouseX(), event.getMouseY())) return;
        PacketDistributor.sendToServer(ArsenalActionPayload.of(ArsenalActionPayload.EYE_SLOT));
        event.setCanceled(true);
    }

    // ------------------------------------------------------------------ on player models

    /** The eyes glowing on a player's face, and in E.G.O the crown, pauldron and cape. */
    public static final class Layer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
        final VergiliusRenderer.Parts x;

        public Layer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent, ModelPart root) {
            super(parent);
            x = new VergiliusRenderer.Parts(root);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer p, float limb, float limbAmount, float partial, float age, float headYaw, float headPitch) {
            if (p.isInvisible()) return;
            PlayerModel<AbstractClientPlayer> m = getParentModel();
            if (EGO.contains(p.getId())) VergiliusRenderer.drawEgo(m, x, pose, buffers, light, limbAmount, age);
            if (!EYES.contains(p.getId()) || EYES_OFF.contains(p.getId())) return;
            // the chosen face pixels lit red (head space: the face is z -4; texture column c is x -4 + c, row r is y -8 + r)
            long bits = MASKS.containsKey(p.getId()) ? MASKS.get(p.getId()) : EyeSlot.DEFAULT_MASK;
            pose.pushPose();
            m.head.translateAndRotate(pose);
            Matrix4f mat = pose.last().pose();
            VertexConsumer vc = buffers.getBuffer(FxRenderTypes.GLOW);
            for (int bit = 0; bit < 64; bit++) {
                if ((bits & (1L << bit)) == 0) continue;
                int r = bit / 8, c = bit % 8;
                float x0 = (-4 + c) / 16f, x1 = x0 + 1 / 16f, y0 = (-8 + r) / 16f, y1 = y0 + 1 / 16f, z = -4.06f / 16;
                vc.addVertex(mat, x0, y0, z).setColor(1, 0.12f, 0.1f, 1);
                vc.addVertex(mat, x0, y1, z).setColor(1, 0.12f, 0.1f, 1);
                vc.addVertex(mat, x1, y1, z).setColor(1, 0.12f, 0.1f, 1);
                vc.addVertex(mat, x1, y0, z).setColor(1, 0.12f, 0.1f, 1);
            }
            pose.popPose();
        }
    }
}
