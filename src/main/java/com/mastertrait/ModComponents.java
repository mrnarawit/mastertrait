package com.mastertrait;

import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.List;

public final class ModComponents {
    public static final DeferredRegister<DataComponentType<?>> REGISTER =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, MasterTrait.MODID);

    /**
     * Trait ids on the item. Absent = not rolled yet; empty list = rolled, no trait.
     * A trident holds three, other weapons at most one.
     */
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<List<String>>> TRAITS =
            REGISTER.register("traits", () -> DataComponentType.<List<String>>builder()
                    .persistent(Codec.STRING.listOf())
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()))
                    .build());

    private ModComponents() {}
}
