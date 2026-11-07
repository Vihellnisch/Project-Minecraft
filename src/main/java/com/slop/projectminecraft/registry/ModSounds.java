package com.slop.projectminecraft.registry;

import com.slop.projectminecraft.ProjectMinecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModSounds {
    private ModSounds() {}

    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, ProjectMinecraft.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> AXE_HIT = reg("arsenal.axe_hit");
    public static final DeferredHolder<SoundEvent, SoundEvent> BIG_IMPACT = reg("apocalypse.big_impact");
    public static final DeferredHolder<SoundEvent, SoundEvent> BLUNT = reg("arsenal.blunt");
    public static final DeferredHolder<SoundEvent, SoundEvent> BUTTERFLIES = reg("arsenal.butterflies");
    public static final DeferredHolder<SoundEvent, SoundEvent> CLAW = reg("arsenal.claw");
    public static final DeferredHolder<SoundEvent, SoundEvent> COIN_HEADS = reg("arsenal.coin_heads");
    public static final DeferredHolder<SoundEvent, SoundEvent> COIN_TAILS = reg("arsenal.coin_tails");
    public static final DeferredHolder<SoundEvent, SoundEvent> DURANDAL = reg("arsenal.durandal");
    public static final DeferredHolder<SoundEvent, SoundEvent> EGO_ACTIVATE = reg("arsenal.ego_activate");
    public static final DeferredHolder<SoundEvent, SoundEvent> FURIOSO_STING = reg("arsenal.furioso_sting");
    public static final DeferredHolder<SoundEvent, SoundEvent> IAI_DASH = reg("arsenal.iai_dash");
    public static final DeferredHolder<SoundEvent, SoundEvent> IAI_SLASHES = reg("arsenal.iai_slashes");
    public static final DeferredHolder<SoundEvent, SoundEvent> KAIJU_STOMP = reg("arsenal.kaiju_stomp");
    public static final DeferredHolder<SoundEvent, SoundEvent> MAGIC_BULLET = reg("arsenal.magic_bullet");
    public static final DeferredHolder<SoundEvent, SoundEvent> MAGIC_CIRCLE = reg("arsenal.magic_circle");
    public static final DeferredHolder<SoundEvent, SoundEvent> MATCH_IGNITE = reg("arsenal.match_ignite");
    public static final DeferredHolder<SoundEvent, SoundEvent> MATCH_STROKE = reg("arsenal.match_stroke");
    public static final DeferredHolder<SoundEvent, SoundEvent> REVOLVER = reg("arsenal.revolver");
    public static final DeferredHolder<SoundEvent, SoundEvent> ROTATION = reg("arsenal.rotation");
    public static final DeferredHolder<SoundEvent, SoundEvent> RUSH_HIT = reg("arsenal.rush_hit");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHOTGUN = reg("arsenal.shotgun");
    public static final DeferredHolder<SoundEvent, SoundEvent> SLICE = reg("dash.slice");
    public static final DeferredHolder<SoundEvent, SoundEvent> SLOWMO = reg("arsenal.slowmo");
    public static final DeferredHolder<SoundEvent, SoundEvent> SOLEMN_SHOT = reg("arsenal.solemn_shot");
    public static final DeferredHolder<SoundEvent, SoundEvent> SPEAR = reg("arsenal.spear");
    public static final DeferredHolder<SoundEvent, SoundEvent> STING = reg("apocalypse.sting");
    public static final DeferredHolder<SoundEvent, SoundEvent> WEAPON_SWAP = reg("arsenal.weapon_swap");

    public static final DeferredHolder<SoundEvent, SoundEvent> CLAW_BLINK = reg("claw.blink");
    public static final DeferredHolder<SoundEvent, SoundEvent> CLAW_SLASH = reg("claw.slash");
    public static final DeferredHolder<SoundEvent, SoundEvent> CLAW_INJECT = reg("claw.inject");
    public static final DeferredHolder<SoundEvent, SoundEvent> CLAW_CHARGE = reg("claw.charge");
    public static final DeferredHolder<SoundEvent, SoundEvent> CLAW_RIFT = reg("claw.rift");
    public static final DeferredHolder<SoundEvent, SoundEvent> CLAW_SHATTER = reg("claw.shatter");

    private static DeferredHolder<SoundEvent, SoundEvent> reg(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(ProjectMinecraft.id(name)));
    }
}
