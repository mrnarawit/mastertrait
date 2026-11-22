package com.mastertrait;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;

@Mod(MasterTrait.MODID)
public class MasterTrait {
    public static final String MODID = "mastertrait";

    public MasterTrait(IEventBus modBus, ModContainer container) {
        ModComponents.REGISTER.register(modBus);
        container.registerConfig(ModConfig.Type.SERVER, Config.SPEC);
    }
}
