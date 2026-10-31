package com.slop.projectminecraft.network;

import com.slop.projectminecraft.client.ClientNet;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class Network {
    private Network() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar r = event.registrar("1");
        // ClientNet is only touched inside the lambdas, which never run on a dedicated server
        r.playToClient(FxPayload.TYPE, FxPayload.CODEC, (p, ctx) -> ctx.enqueueWork(() -> ClientNet.fx(p)));
        r.playToClient(ArsenalFxPayload.TYPE, ArsenalFxPayload.CODEC, (p, ctx) -> ctx.enqueueWork(() -> ClientNet.arsenal(p)));
        r.playToServer(ArsenalActionPayload.TYPE, ArsenalActionPayload.CODEC, (p, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer sp) com.slop.projectminecraft.arsenal.ArsenalServer.onAction(sp, p);
        }));
    }

    public static void sendTracking(Entity entity, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(entity, payload);
    }

    public static void sendNear(ServerLevel level, Vec3 pos, double radius, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayersNear(level, null, pos.x, pos.y, pos.z, radius, payload);
    }

    public static void sendTo(ServerPlayer player, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayer(player, payload);
    }
}
