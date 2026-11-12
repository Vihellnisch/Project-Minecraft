package com.slop.projectminecraft.network;

import com.slop.projectminecraft.ProjectMinecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/**
 * Visuals of the cinematic moves: a kind, two vectors and a short list of ints (entity ids, flags, counts).
 * Layouts are documented next to each kind.
 */
public record ArsenalFxPayload(byte kind, double ax, double ay, double az, double bx, double by, double bz, int[] ints)
        implements CustomPacketPayload {
    public static final byte EGO = 19;           // a = caster, b = flat direction, ints = [player, ego, coins, heads bits, bullet, targets...]
    public static final byte FURIOSO = 22;        // a = player start, b = victim start, ints = [player, victim, ground y x16]
    public static final byte GLOVES = 23;         // ints = [player, selected weapon, used mask, target or -1, random, 1 if the weapon was just drawn]
    public static final byte GLOVE_ATTACK = 24;   // a = from (hand), b = to, ints = [player, weapon, right hand, victims...]
    // ---- the Black Silence's reception
    public static final byte ROLAND = 36;         // a, b, ints = [RolandMoves.FX_*, ...]
    // ---- the Claw
    public static final byte CLAW = 37;           // a, b, ints = [ClawMoves.FX_* or ClawWarp.FX_WARP_*, entity, ...]
    // ---- Vergilius
    public static final byte VERGILIUS = 38;      // a, b, ints = [VergiliusMoves.FX_*, him, ...] (and GladiusServer's, from FX_HAND)

    public static final Type<ArsenalFxPayload> TYPE = new Type<>(ProjectMinecraft.id("arsenal_fx"));
    public static final StreamCodec<FriendlyByteBuf, ArsenalFxPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeByte(p.kind);
                buf.writeDouble(p.ax);
                buf.writeDouble(p.ay);
                buf.writeDouble(p.az);
                buf.writeDouble(p.bx);
                buf.writeDouble(p.by);
                buf.writeDouble(p.bz);
                buf.writeVarInt(p.ints.length);
                for (int i : p.ints) buf.writeVarInt(i + 1);
            },
            buf -> {
                byte kind = buf.readByte();
                double ax = buf.readDouble(), ay = buf.readDouble(), az = buf.readDouble();
                double bx = buf.readDouble(), by = buf.readDouble(), bz = buf.readDouble();
                int n = Math.min(buf.readVarInt(), 96);
                int[] ints = new int[n];
                for (int i = 0; i < n; i++) ints[i] = buf.readVarInt() - 1;
                return new ArsenalFxPayload(kind, ax, ay, az, bx, by, bz, ints);
            });

    public static ArsenalFxPayload of(byte kind, Vec3 a, Vec3 b, int... ints) {
        return new ArsenalFxPayload(kind, a.x, a.y, a.z, b.x, b.y, b.z, ints);
    }

    public static ArsenalFxPayload of(byte kind, int... ints) {
        return new ArsenalFxPayload(kind, 0, 0, 0, 0, 0, 0, ints);
    }

    public Vec3 a() {
        return new Vec3(ax, ay, az);
    }

    public Vec3 b() {
        return new Vec3(bx, by, bz);
    }

    public int i(int index) {
        return index < ints.length ? ints[index] : -1;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
