package com.mastertrait.trait;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;

/** One hit. {@code projectile} is the arrow for bow / crossbow hits and null for melee hits. */
public record HitContext(Player attacker, LivingEntity target, DamageSource source, ItemStack weapon, AbstractArrow projectile) {
    public HitContext(Player attacker, LivingEntity target, DamageSource source, ItemStack weapon) {
        this(attacker, target, source, weapon, null);
    }
}
