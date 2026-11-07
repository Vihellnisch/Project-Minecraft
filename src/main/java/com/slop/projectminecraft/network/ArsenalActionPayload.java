package com.slop.projectminecraft.network;

import com.slop.projectminecraft.ProjectMinecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/** Client -> server for the worn Black Silence: the wheel and the empty-handed attack. */
public record ArsenalActionPayload(byte action, double x, double y, double z, int[] ints, float[] floats) implements CustomPacketPayload {
    public static final byte GLOVE_SELECT = 5;   // ints = [weapon, or -1 toggle random, or -2 Furioso] (Black Silence wheel)
    public static final byte GLOVE_ATTACK = 6;   // an empty-handed attack with the worn Black Silence
    public static final byte CLAW_SELECT = 7;    // ints = [serum] (the Handmade Claw's wheel)

    public static final Type<ArsenalActionPayload> TYPE = new Type<>(ProjectMinecraft.id("arsenal_action"));
    public static final StreamCodec<FriendlyByteBuf, ArsenalActionPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeByte(p.action);
                buf.writeDouble(p.x);
                buf.writeDouble(p.y);
                buf.writeDouble(p.z);
                buf.writeVarInt(p.ints.length);
                for (int i : p.ints) buf.writeVarInt(i + 1);
                buf.writeVarInt(p.floats.length);
                for (float f : p.floats) buf.writeFloat(f);
            },
            buf -> {
                byte action = buf.readByte();
                double x = buf.readDouble(), y = buf.readDouble(), z = buf.readDouble();
                int n = Math.min(buf.readVarInt(), 64);
                int[] ints = new int[n];
                for (int i = 0; i < n; i++) ints[i] = buf.readVarInt() - 1;
                int m = Math.min(buf.readVarInt(), 96);
                float[] floats = new float[m];
                for (int i = 0; i < m; i++) floats[i] = buf.readFloat();
                return new ArsenalActionPayload(action, x, y, z, ints, floats);
            });

    public static ArsenalActionPayload of(byte action) {
        return new ArsenalActionPayload(action, 0, 0, 0, new int[0], new float[0]);
    }

    public static ArsenalActionPayload at(byte action, Vec3 v, int... ints) {
        return new ArsenalActionPayload(action, v.x, v.y, v.z, ints, new float[0]);
    }

    public Vec3 pos() {
        return new Vec3(x, y, z);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
