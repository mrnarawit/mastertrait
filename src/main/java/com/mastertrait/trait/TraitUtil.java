package com.mastertrait.trait;

import com.mastertrait.Config;
import com.mastertrait.ModComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TieredItem;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;

public final class TraitUtil {
    // Keys stored in the player's persistent data (short-lived state)
    public static final String LAST_HURT = "ut_last_hurt";
    public static final String RAMPAGE = "ut_rampage";
    public static final String RAMPAGE_LAST = "ut_rampage_last";
    public static final String PRE_TICK = "ut_pre_tick";
    public static final String WD_TARGET = "ut_wd_target";
    public static final String WD_STACKS = "ut_wd_stacks";
    public static final String WD_LAST = "ut_wd_last";
    /** Flag stored on a killed mob: its experience is doubled (Midas Touch). */
    public static final String COUNTER = "ut_counter";
    public static final String MIDAS = "ut_midas";

    public static boolean isShield(ItemStack s) {
        return !s.isEmpty() && s.getItem() instanceof net.minecraft.world.item.ShieldItem;
    }

    /** The shield in the off hand, or in the main hand when that is the one holding a shield. */
    public static ItemStack shieldStack(Player p) {
        if (isShield(p.getOffhandItem())) return p.getOffhandItem();
        if (isShield(p.getMainHandItem())) return p.getMainHandItem();
        return ItemStack.EMPTY;
    }

    /** Traits of the held shield, unless it is the main-hand item (those are already the weapon traits). */
    public static List<Trait> shieldTraits(Player p) {
        ItemStack sh = shieldStack(p);
        if (sh.isEmpty() || sh == p.getMainHandItem()) return List.of();
        return getTraits(sh, true);
    }

    public static boolean isBow(ItemStack s) {
        return !s.isEmpty() && s.getItem() instanceof net.minecraft.world.item.BowItem;
    }

    public static boolean isCrossbow(ItemStack s) {
        return !s.isEmpty() && s.getItem() instanceof net.minecraft.world.item.CrossbowItem;
    }

    public static boolean isSword(ItemStack s) {
        return !s.isEmpty() && (s.is(ItemTags.SWORDS) || s.getItem() instanceof SwordItem);
    }

    public static boolean isTrident(ItemStack s) {
        return !s.isEmpty() && s.getItem() instanceof TridentItem;
    }

    private static java.lang.reflect.Method critMethod;
    private static boolean critLookedUp;

    /** True when the Critical Strike mod marked this damage source as a critical hit (its DamageSource mixin). */
    public static boolean isCriticalHit(DamageSource source) {
        if (source == null) return false;
        try {
            if (!critLookedUp) {
                critLookedUp = true;
                critMethod = DamageSource.class.getMethod("rng_isCritical");
            }
            return critMethod != null && (Boolean) critMethod.invoke(source);
        } catch (Throwable t) {
            return false;
        }
    }

    public static boolean isRangedWeapon(ItemStack s) {
        return isBow(s) || isCrossbow(s);
    }

    public static boolean isMeleeWeapon(ItemStack s) {
        if (s.isEmpty()) return false;
        return s.is(ItemTags.SWORDS) || s.is(ItemTags.AXES)
                || s.getItem() instanceof SwordItem
                || s.getItem() instanceof AxeItem
                || s.getItem() instanceof MaceItem
                || s.getItem() instanceof TridentItem;
    }

    // ------------------------------------------------------------------ traits on stacks

    /** Trait ids stored on the stack (may be empty). */
    public static List<String> traitIds(ItemStack s) {
        List<String> ids = s.get(ModComponents.TRAITS.get());
        return ids == null ? List.of() : ids;
    }

    /** Traits on this stack. With onlyEnabled, traits disabled in the config are skipped. */
    public static List<Trait> getTraits(ItemStack s, boolean onlyEnabled) {
        if (s.isEmpty()) return List.of();
        List<String> ids = traitIds(s);
        if (ids.isEmpty()) return List.of();
        List<Trait> out = new ArrayList<>(ids.size());
        for (String id : ids) {
            Trait t = Traits.get(id);
            if (t == null) continue;
            if (onlyEnabled && !Config.isEnabled(id)) continue;
            out.add(t);
        }
        return out;
    }

    public static List<Trait> getTraits(ItemStack s) {
        return getTraits(s, true);
    }

    /** Traits of the main-hand weapon plus those of a held shield. */
    public static List<Trait> heldTraits(Player p) {
        List<Trait> main = getTraits(p.getMainHandItem(), true);
        List<Trait> shield = shieldTraits(p);
        if (shield.isEmpty()) return main;
        List<Trait> out = new ArrayList<>(main);
        for (Trait t : shield) {
            if (!out.contains(t)) out.add(t);
        }
        return out;
    }

    public static boolean hasTrait(ItemStack s, Trait t) {
        for (Trait x : getTraits(s, true)) {
            if (x == t) return true;
        }
        return false;
    }

    public static boolean holds(Player p, Trait t) {
        return hasTrait(p.getMainHandItem(), t);
    }

    public static void setTraits(ItemStack s, List<String> ids) {
        s.set(ModComponents.TRAITS.get(), List.copyOf(ids));
    }

    /** Swap one trait id for another on the stack (e.g. Satsujinki -> Mystic Eye). */
    public static void replaceTrait(ItemStack s, String oldId, String newId) {
        List<String> ids = new ArrayList<>(traitIds(s));
        int i = ids.indexOf(oldId);
        if (i < 0) return;
        ids.set(i, newId);
        setTraits(s, ids);
    }

    // ------------------------------------------------------------------ gold

    public static boolean isGold(ItemStack s) {
        if (s.isEmpty()) return false;
        Item item = s.getItem();
        if (item instanceof TieredItem t && t.getTier() == Tiers.GOLD) return true;
        return BuiltInRegistries.ITEM.getKey(item).getPath().contains("gold");
    }

    public static boolean wearsFullGold(Player p) {
        return isGold(p.getItemBySlot(EquipmentSlot.HEAD))
                && isGold(p.getItemBySlot(EquipmentSlot.CHEST))
                && isGold(p.getItemBySlot(EquipmentSlot.LEGS))
                && isGold(p.getItemBySlot(EquipmentSlot.FEET));
    }

    // ------------------------------------------------------------------ surroundings

    /** Number of living hostile mobs within r blocks of the player. */
    public static int countHostiles(Player p, double r) {
        AABB box = p.getBoundingBox().inflate(r);
        return p.level().getEntitiesOfClass(Mob.class, box,
                m -> m instanceof Enemy && m.isAlive() && m.distanceToSqr(p) <= r * r).size();
    }

    /** True if no other (non-spectator) player is within r blocks of p. */
    public static boolean noOtherPlayersNearby(Player p, double r) {
        for (Player o : p.level().players()) {
            if (o == p || o.isSpectator()) continue;
            if (o.distanceToSqr(p) <= r * r) return false;
        }
        return true;
    }

    /** Lone Wolf: no other players, villagers or wolves within r blocks. */
    public static boolean isLonely(Player p, double r) {
        if (!noOtherPlayersNearby(p, r)) return false;
        Level level = p.level();
        AABB box = p.getBoundingBox().inflate(r);
        return level.getEntitiesOfClass(Villager.class, box, v -> v.distanceToSqr(p) <= r * r).isEmpty()
                && level.getEntitiesOfClass(Wolf.class, box, w -> w.distanceToSqr(p) <= r * r).isEmpty();
    }

    /** Players (including p) within range of p whose main hand holds trait t. */
    public static int countHolders(Player p, Trait t, double range) {
        int n = 0;
        for (Player o : p.level().players()) {
            if (o.isSpectator()) continue;
            if (o.distanceToSqr(p) > range * range) continue;
            if (holds(o, t)) n++;
        }
        return n;
    }

    /** True if a player holding Party Leader is within range of this villager. */
    public static boolean hasPartyLeaderAura(Villager v) {
        double range = Traits.PARTY_LEADER.value("range");
        for (Player p : v.level().players()) {
            if (p.isSpectator()) continue;
            if (p.distanceToSqr(v) > range * range) continue;
            if (holds(p, Traits.PARTY_LEADER)) return true;
        }
        return false;
    }

    /** True if some OTHER player within range of p holds Know My Name. Never stacks. */
    public static boolean hasKnowMyNameAura(Player p) {
        double range = Traits.KNOW_MY_NAME.value("range");
        for (Player other : p.level().players()) {
            if (other == p || other.isSpectator()) continue;
            if (other.distanceToSqr(p) > range * range) continue;
            if (holds(other, Traits.KNOW_MY_NAME)) return true;
        }
        return false;
    }

    /** Menacing: fraction by which this hostile mob's damage is reduced (0 if no holder is nearby). */
    public static double menacingReduction(Mob mob) {
        double range = Traits.MENACING.value("range");
        int min = (int) Math.round(Traits.MENACING.value("minMobs"));
        for (Player p : mob.level().players()) {
            if (p.isSpectator() || p.distanceToSqr(mob) > range * range) continue;
            if (!holds(p, Traits.MENACING)) continue;
            if (countHostiles(p, range) >= min) return Traits.MENACING.pct("damageReduction");
        }
        return 0;
    }

    private TraitUtil() {}
}
