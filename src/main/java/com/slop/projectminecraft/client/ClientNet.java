package com.slop.projectminecraft.client;

import com.slop.projectminecraft.network.ArsenalFxPayload;
import com.slop.projectminecraft.network.FxPayload;

/** Client-side packet handlers (only class-loaded on the client, from inside the payload handler lambdas). */
public final class ClientNet {
    private ClientNet() {}

    public static void fx(FxPayload p) {
        if (p.fx() == FxPayload.BOSS_SAY) com.slop.projectminecraft.client.boss.BossClient.say(p.data(), p.pos(), (int) p.pos2().x);
    }

    public static void arsenal(ArsenalFxPayload p) {
        com.slop.projectminecraft.client.arsenal.ArsenalClient.handle(p);
    }
}
