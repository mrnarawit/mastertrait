package com.mastertrait;

import com.mojang.serialization.Codec;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

public final class ModAttachments {
    public static final DeferredRegister<AttachmentType<?>> REGISTER =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, MasterTrait.MODID);

    /** True once the player has taken part in slaying the Wither. Kept on death and synced to the client. */
    public static final Supplier<AttachmentType<Boolean>> WITHER_SLAIN = REGISTER.register("wither_slain",
            () -> AttachmentType.builder(() -> false)
                    .serialize(Codec.BOOL)
                    .copyOnDeath()
                    .build());

    /** Marker modifier (amount 0 on Luck). Attributes are synced to the client, so it tells the client the flag is set. */
    public static final net.minecraft.resources.ResourceLocation MARKER =
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(MasterTrait.MODID, "wither_slain");

    /** Server side: keep the synced marker in line with the saved flag. */
    public static void syncMarker(net.minecraft.world.entity.player.Player p) {
        net.minecraft.world.entity.ai.attributes.AttributeInstance a = p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.LUCK);
        if (a == null) return;
        boolean has = a.hasModifier(MARKER);
        if (p.getData(WITHER_SLAIN.get())) {
            if (!has) a.addPermanentModifier(new net.minecraft.world.entity.ai.attributes.AttributeModifier(MARKER, 0.0,
                    net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE));
        } else if (has) {
            a.removeModifier(MARKER);
        }
    }

    /** Client side: true when the flag is set for this player. */
    public static boolean clientFlag(net.minecraft.world.entity.player.Player p) {
        net.minecraft.world.entity.ai.attributes.AttributeInstance a = p.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.LUCK);
        return a != null && a.hasModifier(MARKER);
    }

    private ModAttachments() {}
}
