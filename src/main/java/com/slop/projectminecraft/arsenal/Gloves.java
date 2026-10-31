package com.slop.projectminecraft.arsenal;

import com.slop.projectminecraft.compat.CuriosCompat;
import com.slop.projectminecraft.registry.ModItems;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.fml.ModList;

/** Whether someone wears the Black Silence: the Curios hands slot, or the offhand without Curios (or as a fallback). */
public final class Gloves {
    private Gloves() {}

    public static final boolean CURIOS = ModList.get().isLoaded("curios");

    public static boolean worn(LivingEntity e) {
        if (e.getOffhandItem().is(ModItems.BLACK_SILENCE.get())) return true;
        return CURIOS && CuriosCompat.wearing(e);
    }

    /** The workshop weapons come out bare-handed: worn gloves and nothing in the main hand. */
    public static boolean armed(LivingEntity e) {
        return e.getMainHandItem().isEmpty() && worn(e);
    }
}
