package com.slop.projectminecraft.compat;

import com.slop.projectminecraft.registry.ModItems;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

/** The only class that touches Curios; callers check Gloves.CURIOS first so it never loads without it. */
public final class CuriosCompat {
    private CuriosCompat() {}

    public static void setup() {
        // right-click puts the gloves straight on
        CuriosApi.registerCurio(ModItems.BLACK_SILENCE.get(), new ICurioItem() {
            @Override
            public boolean canEquipFromUse(SlotContext ctx, ItemStack stack) {
                return true;
            }
        });
    }

    public static boolean wearing(LivingEntity e) {
        return CuriosApi.getCuriosInventory(e).map(inv -> inv.isEquipped(ModItems.BLACK_SILENCE.get())).orElse(false);
    }
}
