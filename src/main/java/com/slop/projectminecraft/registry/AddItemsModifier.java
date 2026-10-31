package com.slop.projectminecraft.registry;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;

/** Items hidden in vanilla chests: each entry (an item id) rolls on its own. */
public class AddItemsModifier extends LootModifier {
    public record Entry(String what, float chance) {
        public static final Codec<Entry> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("what").forGetter(Entry::what),
                Codec.FLOAT.fieldOf("chance").forGetter(Entry::chance)).apply(i, Entry::new));
    }

    public static final MapCodec<AddItemsModifier> CODEC = RecordCodecBuilder.mapCodec(i -> codecStart(i)
            .and(Entry.CODEC.listOf().fieldOf("entries").forGetter(m -> m.entries)).apply(i, AddItemsModifier::new));

    final List<Entry> entries;

    public AddItemsModifier(LootItemCondition[] conditions, List<Entry> entries) {
        super(conditions);
        this.entries = entries;
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext ctx) {
        for (Entry e : entries) {
            if (ctx.getRandom().nextFloat() >= e.chance) continue;
            ItemStack s = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(e.what)));
            if (!s.isEmpty()) loot.add(s);
        }
        return loot;
    }

    @Override
    public MapCodec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
