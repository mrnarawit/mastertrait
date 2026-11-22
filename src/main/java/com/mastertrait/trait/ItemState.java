package com.mastertrait.trait;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.function.Consumer;

/**
 * Per-weapon state (kill counters, cooldowns) stored in the item's custom_data under "mastertrait".
 * custom_data is synced to the client, so tooltips can show progress and cooldowns.
 */
public final class ItemState {
    private static final String ROOT = "mastertrait";

    public static final String KILLS = "kills";
    public static final String MS_STACKS = "ms_stacks";
    public static final String MS_TIME = "ms_time";

    private static CompoundTag read(ItemStack s) {
        CompoundTag all = s.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return all.getCompound(ROOT);
    }

    public static int getInt(ItemStack s, String key) {
        return read(s).getInt(key);
    }

    public static long getLong(ItemStack s, String key) {
        return read(s).getLong(key);
    }

    public static void update(ItemStack s, Consumer<CompoundTag> fn) {
        CustomData.update(DataComponents.CUSTOM_DATA, s, root -> {
            CompoundTag t = root.getCompound(ROOT);
            fn.accept(t);
            root.put(ROOT, t);
        });
    }

    // ---- cooldowns (game time at which the trait is ready again)

    public static long cooldownEnd(ItemStack s, String traitId) {
        return getLong(s, "cd_" + traitId);
    }

    public static boolean isReady(ItemStack s, String traitId, long now) {
        return now >= cooldownEnd(s, traitId);
    }

    public static void startCooldown(ItemStack s, String traitId, long endTick) {
        update(s, t -> t.putLong("cd_" + traitId, endTick));
    }

    /** Ticks -> m:ss */
    public static String formatTicks(long ticks) {
        long sec = Math.max(0, (ticks + 19) / 20);
        return (sec / 60) + ":" + String.format("%02d", sec % 60);
    }

    private ItemState() {}
}
