package com.mastertrait.event;

import com.mastertrait.Config;
import com.mastertrait.ModComponents;
import com.mastertrait.MasterTrait;
import com.mastertrait.compat.AttributeBridge;
import com.mastertrait.trait.HitContext;
import com.mastertrait.trait.Stats;
import com.mastertrait.trait.Trait;
import com.mastertrait.trait.TraitUtil;
import com.mastertrait.trait.Traits;
import net.minecraft.nbt.CompoundTag;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import com.mastertrait.trait.Ranged;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.damagesource.DamageTypes;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AnvilUpdateEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.item.Items;
import com.mastertrait.ModAttachments;
import net.neoforged.neoforge.event.entity.EntityStruckByLightningEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingExperienceDropEvent;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@EventBusSubscriber(modid = MasterTrait.MODID)
public final class CommonEvents {
    private static final Stats EMPTY = new Stats();

    // ------------------------------------------------------------------ rolling

    /** Gives a melee weapon its trait(s) the first time it is seen. */
    public static void roll(ItemStack stack, RandomSource rand) {
        if (!(TraitUtil.isMeleeWeapon(stack) || TraitUtil.isShield(stack) || TraitUtil.isRangedWeapon(stack) || Traits.hasRollRule(stack)) || stack.has(ModComponents.TRAITS.get())) return;
        List<Trait> rolled = Traits.rollFor(stack, rand);
        List<String> ids = new ArrayList<>();
        for (Trait t : rolled) ids.add(t.id());
        TraitUtil.setTraits(stack, ids);
        for (Trait t : rolled) t.onRolled(stack);
    }

    private static void rollInventory(Player p) {
        Inventory inv = p.getInventory();
        Traits.setRoller(p);
        try {
            for (int i = 0; i < inv.getContainerSize(); i++) {
                roll(inv.getItem(i), p.getRandom());
            }
        } finally {
            Traits.setRoller(null);
        }
    }

    // ------------------------------------------------------------------ attribute layers

    /** All attribute-style bonuses the player currently gets (held traits + Know My Name aura). */
    public static Stats computeStats(Player p) {
        Stats s = new Stats();
        for (Trait t : TraitUtil.heldTraits(p)) t.collectModifiers(p, s);
        if (TraitUtil.hasKnowMyNameAura(p)) s.damageTaken -= Traits.KNOW_MY_NAME.pct("damageReduction");
        return s;
    }

    public static void refresh(Player p) {
        AttributeBridge.apply(p, computeStats(p), AttributeBridge.HELD);
    }

    private static void clearPre(Player p) {
        CompoundTag d = p.getPersistentData();
        if (d.contains(TraitUtil.PRE_TICK)) {
            AttributeBridge.apply(p, EMPTY, AttributeBridge.PRE);
            d.remove(TraitUtil.PRE_TICK);
        }
    }

    /** Target-dependent bonuses (crit chance / lifesteal) must be in place before the hit is processed. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAttack(AttackEntityEvent event) {
        Player p = event.getEntity();
        if (p.level().isClientSide) return;
        if (!(event.getTarget() instanceof LivingEntity target)) return;
        Stats pre = new Stats();
        for (Trait t : TraitUtil.heldTraits(p)) t.collectPreAttack(p, target, pre);
        AttributeBridge.apply(p, pre, AttributeBridge.PRE);
        p.getPersistentData().putLong(TraitUtil.PRE_TICK, p.level().getGameTime());
    }

    // ------------------------------------------------------------------ damage

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (Traits.fixedDamageGuard) return;
        LivingEntity victim = event.getEntity();
        DamageSource source = event.getSource();
        double dealt = 0;
        double taken = 0;

        if (victim instanceof Player player) {
            for (Trait t : TraitUtil.heldTraits(player)) {
                if (t.negatesDamage(player, source)) {
                    event.setCanceled(true);
                    return;
                }
            }
            for (Trait t : TraitUtil.heldTraits(player)) taken += t.damageTakenBonus(player, source);
            refresh(player);
            // Internal damage-reduction math (when the attribute is missing, or the mode is INTERNAL/BOTH).
            if (AttributeBridge.damageTakenInternal()) {
                taken += computeStats(player).damageTaken;
            }
        }
        taken += Traits.perfectBlockTaken(victim);
        dealt -= Traits.perfectBlockDealt(source.getEntity());
        double flat = 0;
        boolean ignoreArmor = false;
        double armorFrac = 0;
        double flatTaken = 0;
        if (victim instanceof Player pv) {
            for (Trait t : TraitUtil.heldTraits(pv)) flatTaken += t.flatDamageReduction(pv, source);
        }
        if (victim instanceof Villager villager && TraitUtil.hasPartyLeaderAura(villager)) {
            taken -= Traits.PARTY_LEADER.pct("damageReduction");
        }
        if (victim instanceof Wolf wolf && Traits.tamerBuffed(wolf)) {
            taken -= Traits.BEAST_TAMER.pct("damageReduction");
        }
        if (victim instanceof IronGolem golem && Traits.engineerNear(golem)) {
            taken -= Traits.ENGINEER.pct("damageReduction");
        }

        Entity attackerEntity = source.getEntity();
        if (attackerEntity instanceof Player attacker) {
            if (TraitUtil.hasKnowMyNameAura(attacker)) dealt += Traits.KNOW_MY_NAME.pct("damage");
            dealt += Traits.requiemBuff(attacker);
            // Party Leader: the holder cannot hurt villagers
            if (victim instanceof Villager && TraitUtil.holds(attacker, Traits.PARTY_LEADER)) {
                event.setCanceled(true);
                return;
            }
            if (source.getDirectEntity() == attacker) {
                ItemStack weapon = attacker.getMainHandItem();
                HitContext ctx = new HitContext(attacker, victim, source, weapon);
                boolean kill = false;
                for (Trait t : TraitUtil.getTraits(weapon)) {
                    dealt += t.damageDealtBonus(ctx);
                    flat += t.flatDamageBonus(ctx);
                    if (t.instakill(ctx)) kill = true;
                    if (t.ignoresArmor(ctx)) ignoreArmor = true;
                    armorFrac = Math.max(armorFrac, t.armorIgnore(ctx));
                }
                for (Trait t : TraitUtil.shieldTraits(attacker)) {
                    dealt += t.damageDealtBonus(ctx);
                    flat += t.flatDamageBonus(ctx);
                }
                if (kill) {
                    event.setAmount(1.0E6f);
                    return;
                }
            } else if (source.getDirectEntity() instanceof ThrownTrident tt) {
                HitContext ctx = new HitContext(attacker, victim, source, tt.getPickupItemStackOrigin(), tt);
                for (Trait t : TraitUtil.getTraits(ctx.weapon())) {
                    dealt += t.thrownDamageBonus(ctx);
                    armorFrac = Math.max(armorFrac, t.armorIgnore(ctx));
                }
            } else if (source.getDirectEntity() instanceof AbstractArrow arrow) {
                ItemStack bow = Ranged.bowOf(arrow, attacker);
                if (arrow.getPersistentData().getBoolean(Ranged.IGNORE_IFRAMES)) victim.invulnerableTime = 0;
                if (!bow.isEmpty()) {
                    HitContext ctx = new HitContext(attacker, victim, source, bow, arrow);
                    for (Trait t : TraitUtil.getTraits(bow)) {
                        dealt += t.rangedDamageBonus(ctx);
                        armorFrac = Math.max(armorFrac, t.armorIgnore(ctx));
                    }
                    dealt += Ranged.mult(arrow);
                }
            }
        } else if (attackerEntity instanceof Villager villager && TraitUtil.hasPartyLeaderAura(villager)) {
            dealt += Traits.PARTY_LEADER.pct("damage");
        } else if (attackerEntity instanceof Wolf wolf && Traits.tamerBuffed(wolf)) {
            dealt += Traits.BEAST_TAMER.pct("damage");
        } else if (attackerEntity instanceof IronGolem golem && Traits.engineerNear(golem)) {
            dealt += Traits.ENGINEER.pct("damage");
        } else if (attackerEntity instanceof Mob mob && mob instanceof Enemy) {
            dealt -= TraitUtil.menacingReduction(mob);
        }

        if (dealt != 0 || taken != 0) {
            double mult = Math.max(0, 1 + dealt) * Math.max(0, 1 + taken);
            event.setAmount((float) (event.getAmount() * mult));
        }
        if (flatTaken > 0) event.setAmount((float) Math.max(0, event.getAmount() - flatTaken));
        if (ignoreArmor) {
            event.addReductionModifier(DamageContainer.Reduction.ARMOR, (container, reduction) -> 0f);
        } else if (armorFrac > 0) {
            final float keep = (float) Math.max(0, 1.0 - armorFrac);
            event.addReductionModifier(DamageContainer.Reduction.ARMOR, (container, reduction) -> reduction * keep);
        }
        if (flat > 0) event.setAmount(event.getAmount() + (float) flat);
    }

    @SubscribeEvent
    public static void onDamagePost(LivingDamageEvent.Post event) {
        if (Traits.fixedDamageGuard) return;
        LivingEntity victim = event.getEntity();
        DamageSource source = event.getSource();
        float dealt = event.getNewDamage();

        if (source.getEntity() instanceof Player attacker && source.getDirectEntity() == attacker) {
            ItemStack weapon = attacker.getMainHandItem();
            HitContext ctx = new HitContext(attacker, victim, source, weapon);
            for (Trait t : TraitUtil.getTraits(weapon)) t.onHit(ctx, dealt);
            for (Trait t : TraitUtil.shieldTraits(attacker)) t.onHit(ctx, dealt);
        }

        if (source.getEntity() instanceof Player thrower && source.getDirectEntity() instanceof ThrownTrident tt) {
            HitContext ctx = new HitContext(thrower, victim, source, tt.getPickupItemStackOrigin(), tt);
            for (Trait t : TraitUtil.getTraits(ctx.weapon())) t.onThrownHit(ctx, dealt);
        }

        if (source.getEntity() instanceof Player shooter && source.getDirectEntity() instanceof AbstractArrow arrow
                && !(arrow instanceof ThrownTrident)) {
            ItemStack bow = Ranged.bowOf(arrow, shooter);
            if (!bow.isEmpty()) {
                HitContext ctx = new HitContext(shooter, victim, source, bow, arrow);
                for (Trait t : TraitUtil.getTraits(bow)) t.onRangedHit(ctx, dealt);
            }
        }

        if (victim instanceof IronGolem golem) Traits.engineerOnDamaged(golem, dealt);

        if (victim instanceof Player hurt && dealt > 0 && AttributeBridge.reflectInternal()
                && source.getEntity() instanceof LivingEntity reflected && reflected != hurt
                && !source.is(DamageTypes.THORNS)) {
            double reflect = computeStats(hurt).reflect;
            if (reflect > 0) Traits.reflectDamage(hurt, reflected, (float) (dealt * reflect));
        }

        if (victim instanceof Player player) {
            if (dealt > 0) {
                player.getPersistentData().putLong(TraitUtil.LAST_HURT, player.level().getGameTime());
                player.getPersistentData().putLong(TraitUtil.COUNTER, player.level().getGameTime());
            }
            for (Trait t : TraitUtil.heldTraits(player)) t.onDamaged(player, source, dealt);
        }
    }

    /** Runs last so other mods (crit, lifesteal) have already read the temporary attack-time modifiers. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDamagePostCleanup(LivingDamageEvent.Post event) {
        if (event.getSource().getEntity() instanceof Player attacker && !attacker.level().isClientSide) {
            clearPre(attacker);
        }
    }

    @SubscribeEvent
    public static void onShieldBlock(LivingShieldBlockEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide) return;
        player.getPersistentData().putLong(TraitUtil.COUNTER, player.level().getGameTime());
        ItemStack shield = player.getUseItem();
        if (!TraitUtil.isShield(shield)) return;
        for (Trait t : TraitUtil.getTraits(shield)) {
            t.onBlock(player, shield, event.getDamageSource(), event.getBlockedDamage());
        }
        // Spikey (internal mode) also reflects blocked damage
        if (AttributeBridge.reflectInternal() && event.getDamageSource().getEntity() instanceof LivingEntity src
                && src != player && !event.getDamageSource().is(DamageTypes.THORNS)) {
            double reflect = computeStats(player).reflect;
            if (reflect > 0) Traits.reflectDamage(player, src, (float) (event.getBlockedDamage() * reflect));
        }
    }

    @SubscribeEvent
    public static void onKnockback(LivingKnockBackEvent event) {
        if (Traits.noKnockbackGuard) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onShieldLowered(LivingEntityUseItemEvent.Stop event) {
        if (event.getEntity() instanceof Player player && !player.level().isClientSide && TraitUtil.isShield(event.getItem())) {
            for (Trait t : TraitUtil.getTraits(event.getItem())) t.onShieldRelease(player, event.getItem());
        }
    }

    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        Projectile proj = event.getProjectile();
        if (proj.level().isClientSide) return;
        if (proj instanceof AbstractArrow arrow && !(arrow instanceof ThrownTrident) && arrow.getOwner() instanceof Player owner
                && event.getRayTraceResult() instanceof EntityHitResult shot && shot.getEntity() instanceof LivingEntity target
                && !Traits.fixedDamageGuard) {
            ItemStack bow = Ranged.bowOf(arrow, owner);
            if (!bow.isEmpty()) {
                HitContext ctx = new HitContext(owner, target, owner.level().damageSources().arrow(arrow, owner), bow, arrow);
                Stats pre = new Stats();
                for (Trait t : TraitUtil.getTraits(bow)) t.collectPreShot(ctx, pre);
                AttributeBridge.apply(owner, pre, AttributeBridge.PRE);
                owner.getPersistentData().putLong(TraitUtil.PRE_TICK, owner.level().getGameTime());
            }
        }
        if (proj instanceof ThrownTrident tt && tt.getOwner() instanceof Player owner
                && event.getRayTraceResult() instanceof EntityHitResult shot && shot.getEntity() instanceof LivingEntity target
                && !Traits.fixedDamageGuard) {
            Stats pre = new Stats();
            for (Trait t : TraitUtil.getTraits(tt.getPickupItemStackOrigin())) t.collectPreAttack(owner, target, pre);
            AttributeBridge.apply(owner, pre, AttributeBridge.PRE);
            owner.getPersistentData().putLong(TraitUtil.PRE_TICK, owner.level().getGameTime());
        }
        if (event.getRayTraceResult() instanceof EntityHitResult hit && hit.getEntity() instanceof Player p && p.isBlocking()) {
            if (Traits.tryMagicCoating(p, proj)) event.setCanceled(true);
        }
    }

    /** A new arrow: mark it, then let the bow's traits react (extra arrows, pierce, charged shots...). */
    @SubscribeEvent
    public static void onArrowJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide || event.loadedFromDisk()) return;
        if (event.getEntity() instanceof ThrownTrident tt && tt.getOwner() instanceof Player thrower) {
            // remember how long the throw was charged (Ocean Splitter) and whether it started in water
            CompoundTag td = tt.getPersistentData();
            td.putInt(Traits.TRIDENT_CHARGE, thrower.getTicksUsingItem());
            td.putBoolean(Traits.TRIDENT_WATER, thrower.isInWater());
            return;
        }
        if (!(event.getEntity() instanceof AbstractArrow arrow) || arrow instanceof ThrownTrident) return;
        if (!(arrow.getOwner() instanceof Player p)) return;
        CompoundTag data = arrow.getPersistentData();
        if (data.getBoolean(Ranged.SEEN)) return;
        data.putBoolean(Ranged.SEEN, true);

        ItemStack bow = Ranged.shotStack(p);
        if (bow.isEmpty() || !TraitUtil.isRangedWeapon(bow)) bow = Ranged.bowOf(arrow, p);
        if (bow.isEmpty()) return;
        List<Trait> traits = TraitUtil.getTraits(bow);
        if (traits.isEmpty()) return;

        data.putDouble(Ranged.OX, arrow.getX());
        data.putDouble(Ranged.OY, arrow.getY());
        data.putDouble(Ranged.OZ, arrow.getZ());
        data.putDouble(Ranged.SPEED, arrow.getDeltaMovement().length());

        long now = p.level().getGameTime();
        CompoundTag pd = p.getPersistentData();
        boolean first = !pd.contains(Ranged.SHOT_TICK) || pd.getLong(Ranged.SHOT_TICK) != now;
        pd.putLong(Ranged.SHOT_TICK, now);
        for (Trait t : traits) t.onShoot(p, bow, arrow, first);
    }

    /**
     * Quickdraw / Frenzy / Tight String: changes the draw or load speed by using up extra use ticks
     * (faster) or giving some back (slower).
     */
    @SubscribeEvent
    public static void onUseTick(LivingEntityUseItemEvent.Tick event) {
        if (!(event.getEntity() instanceof Player p)) return;
        ItemStack s = event.getItem();
        if (!TraitUtil.isRangedWeapon(s)) return;
        double net = 0;
        for (Trait t : TraitUtil.getTraits(s)) net += t.chargeSpeed(p, s);
        if (net == 0) return;
        double rate = Math.max(0.1, 1.0 + net);
        CompoundTag d = p.getPersistentData();
        double acc = d.getDouble(Ranged.CHARGE_ACC) + (rate - 1.0);
        int whole = (int) acc; // truncates toward zero
        d.putDouble(Ranged.CHARGE_ACC, acc - whole);
        if (whole != 0) event.setDuration(event.getDuration() - whole);
    }

    /** Liquify: arrows keep their speed in water (vanilla slows them to 60% per tick). */
    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Post event) {
        if (event.getEntity() instanceof AbstractArrow arrow && !(arrow instanceof ThrownTrident)
                && arrow.isInWater() && arrow.getPersistentData().getBoolean(Ranged.LIQUIFY)) {
            arrow.setDeltaMovement(arrow.getDeltaMovement().scale(0.99 / 0.6));
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        Ranged.tickPending();
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        Ranged.clearPending();
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof Player dead) {
            return;
        }
        if (event.getEntity() instanceof WitherBoss wither && !wither.level().isClientSide) {
            // everyone close enough took part in the fight: they can now read Divine, Heroic and Mythical traits
            for (Player nearby : wither.level().getEntitiesOfClass(Player.class, wither.getBoundingBox().inflate(64))) {
                nearby.setData(ModAttachments.WITHER_SLAIN.get(), true);
            }
        }
        if (!(event.getEntity() instanceof Mob victim)) return;
        if (event.getSource().getEntity() instanceof Player shooter && event.getSource().getDirectEntity() instanceof AbstractArrow arrow
                && !(arrow instanceof ThrownTrident)) {
            ItemStack bow = Ranged.bowOf(arrow, shooter);
            if (!bow.isEmpty()) {
                HitContext ctx = new HitContext(shooter, victim, event.getSource(), bow, arrow);
                for (Trait t : TraitUtil.getTraits(bow)) t.onRangedKill(ctx);
            }
        }
        if (event.getSource().getEntity() instanceof Player player) {
            for (Trait t : TraitUtil.heldTraits(player)) t.onKill(player, victim);
        }
    }

    public static final String REROLL_FLAG = "mt_reroll_preview";

    /** Weapon + Heart of the Sea in an anvil rerolls the weapon's normal traits. The preview hides the result. */
    @SubscribeEvent
    public static void onAnvil(AnvilUpdateEvent event) {
        ItemStack left = event.getLeft();
        if (!event.getRight().is(Items.HEART_OF_THE_SEA) || left.isEmpty() || left.getCount() != 1
                || !left.has(ModComponents.TRAITS.get())) return;
        if (Traits.rerollNormal(left, event.getPlayer().getRandom()) == null) return;
        ItemStack out = left.copy();
        net.minecraft.world.item.component.CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA, out,
                tag -> tag.putBoolean(REROLL_FLAG, true));
        event.setOutput(out);
        event.setMaterialCost(1);
        event.setCost(Config.REROLL_LEVEL_COST.get());
    }

    /** The real roll happens only when the player takes the result out of the anvil. */
    @SubscribeEvent
    public static void onAnvilTake(net.neoforged.neoforge.event.entity.player.AnvilRepairEvent event) {
        ItemStack out = event.getOutput();
        net.minecraft.world.item.component.CustomData data = out.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        if (data == null || !data.contains(REROLL_FLAG)) return;
        net.minecraft.world.item.component.CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA, out,
                tag -> tag.remove(REROLL_FLAG));
        List<String> ids = Traits.rerollNormal(out, event.getEntity().getRandom());
        if (ids != null) TraitUtil.setTraits(out, ids);
    }

    @SubscribeEvent
    public static void onExperienceDrop(LivingExperienceDropEvent event) {
        Player player = event.getAttackingPlayer();
        if (player == null) return;
        double bonus = 0;
        for (Trait t : TraitUtil.heldTraits(player)) bonus += t.expBonus(player, event.getEntity());
        if (bonus == 0) return;
        double value = event.getDroppedExperience() * (1 + bonus);
        int whole = (int) value;
        if (event.getEntity().getRandom().nextDouble() < value - whole) whole++;
        event.setDroppedExperience(whole);
    }

    @SubscribeEvent
    public static void onHeal(LivingHealEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide) return;
        double bonus = 0;
        for (Trait t : TraitUtil.heldTraits(player)) bonus += t.healingReceivedBonus(player);
        if (bonus != 0) event.setAmount((float) (event.getAmount() * (1 + bonus)));
    }

    @SubscribeEvent
    public static void onLightning(EntityStruckByLightningEvent event) {
        if (event.getEntity() instanceof Player player && !player.level().isClientSide) {
            for (Trait t : TraitUtil.heldTraits(player)) t.onStruckByLightning(player, event.getLightning());
        }
    }

    @SubscribeEvent
    public static void onChat(ServerChatEvent event) {
        ServerPlayer player = event.getPlayer();
        String message = event.getRawText();
        for (Trait t : TraitUtil.heldTraits(player)) t.onChat(player, message);
    }

    // ------------------------------------------------------------------ tick

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player p = event.getEntity();
        if (p.level().isClientSide) return;

        if (p.tickCount % 10 == 0) {
            ModAttachments.syncMarker(p);
            rollInventory(p);
            Traits.tamerTick(p);
            Traits.bannerTick(p);
        }

        resetStacks(p);
        Traits.shieldStacksTick(p);
        for (Trait t : TraitUtil.heldTraits(p)) t.onTick(p);

        if (p.tickCount % 2 == 0) refresh(p);

        CompoundTag d = p.getPersistentData();
        if (d.contains(TraitUtil.PRE_TICK) && p.level().getGameTime() - d.getLong(TraitUtil.PRE_TICK) >= 3) {
            clearPre(p);
        }
    }

    private static void resetStacks(Player p) {
        CompoundTag d = p.getPersistentData();
        long now = p.level().getGameTime();

        // Rampager: resets after 3 seconds without hitting, or when the weapon is no longer held
        if (d.getInt(TraitUtil.RAMPAGE) > 0
                && (!TraitUtil.holds(p, Traits.RAMPAGER) || now - d.getLong(TraitUtil.RAMPAGE_LAST) > Traits.RAMPAGER.ticks("resetSeconds"))) {
            d.putInt(TraitUtil.RAMPAGE, 0);
        }

        Traits.chantTick(p);
    }

    private CommonEvents() {}
}
