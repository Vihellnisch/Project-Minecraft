package com.slop.projectminecraft.network;

import com.slop.projectminecraft.ProjectMinecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;

/** One-shot visual effect: a type, a position, an optional second position/direction and an int of extra data. */
public record FxPayload(byte fx, double x, double y, double z, double x2, double y2, double z2, int data) implements CustomPacketPayload {
    public static final byte BOSS_SAY = 60;             // pos = boss eyes, pos2.x = speaker (BossClient), data = line (Roland.LINE_*)

    public static final Type<FxPayload> TYPE = new Type<>(ProjectMinecraft.id("fx"));
    public static final StreamCodec<FriendlyByteBuf, FxPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeByte(p.fx);
                buf.writeDouble(p.x);
                buf.writeDouble(p.y);
                buf.writeDouble(p.z);
                buf.writeDouble(p.x2);
                buf.writeDouble(p.y2);
                buf.writeDouble(p.z2);
                buf.writeInt(p.data);
            },
            buf -> new FxPayload(buf.readByte(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
                    buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readInt()));

    public static FxPayload at(byte fx, Vec3 pos) {
        return new FxPayload(fx, pos.x, pos.y, pos.z, 0, 0, 0, 0);
    }

    public FxPayload to(Vec3 second) {
        return new FxPayload(fx, x, y, z, second.x, second.y, second.z, data);
    }

    public FxPayload data(int value) {
        return new FxPayload(fx, x, y, z, x2, y2, z2, value);
    }

    public Vec3 pos() {
        return new Vec3(x, y, z);
    }

    public Vec3 pos2() {
        return new Vec3(x2, y2, z2);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
