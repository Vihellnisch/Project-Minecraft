package com.slop.projectminecraft.network;

import com.slop.projectminecraft.ProjectMinecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * One backdrop for Serum W's trip: a slab of blocks copied out of a dimension (ClawWarp), drawn behind the scene on the
 * client (ClawWarpClient). `victim` names the run, `index` the stage (0 = where it began, then each dimension).
 * Blocks are palette indices (x fastest, then z, then y) into `palette` (block state ids); `sky` and `fog` are the colours
 * of the place, `ceiling` whether it has a roof (the Nether).
 */
public record ClawStagePayload(int victim, int index, String dimension, int sx, int sy, int sz, int[] palette, byte[] blocks, int sky, int fog,
                               boolean ceiling) implements CustomPacketPayload {
    public static final Type<ClawStagePayload> TYPE = new Type<>(ProjectMinecraft.id("claw_stage"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClawStagePayload> CODEC = StreamCodec.of((buf, p) -> {
        buf.writeVarInt(p.victim);
        buf.writeVarInt(p.index);
        buf.writeUtf(p.dimension);
        buf.writeVarInt(p.sx);
        buf.writeVarInt(p.sy);
        buf.writeVarInt(p.sz);
        buf.writeVarIntArray(p.palette);
        buf.writeByteArray(p.blocks);
        buf.writeInt(p.sky);
        buf.writeInt(p.fog);
        buf.writeBoolean(p.ceiling);
    }, buf -> new ClawStagePayload(buf.readVarInt(), buf.readVarInt(), buf.readUtf(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
            buf.readVarIntArray(), buf.readByteArray(), buf.readInt(), buf.readInt(), buf.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
