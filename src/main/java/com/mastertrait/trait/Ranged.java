package com.mastertrait.trait;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/** Helpers for bow / crossbow traits: which weapon fired an arrow, per-arrow marks, copying arrows. */
public final class Ranged {
    /** Per-arrow data keys (stored in the arrow's persistent data, so copies keep them). */
    public static final String SEEN = "mt_seen";
    public static final String MULT = "mt_mult";
    public static final String RICOCHET = "mt_ricochet";
    public static final String IGNORE_IFRAMES = "mt_noiframes";
    public static final String LIQUIFY = "mt_liquify";
    public static final String SPEED = "mt_speed";
    public static final String OX = "mt_ox";
    public static final String OY = "mt_oy";
    public static final String OZ = "mt_oz";
    /** Per-player keys. */
    public static final String SHOT_TICK = "mt_shot_tick";
    public static final String CHARGE_ACC = "mt_charge_acc";
    public static final String FRENZY = "mt_frenzy";
    public static final String FRENZY_LAST = "mt_frenzy_last";

    /** The bow / crossbow the player is shooting right now (the one in use, else main hand, else off hand). */
    public static ItemStack shotStack(Player p) {
        ItemStack use = p.getUseItem();
        if (TraitUtil.isRangedWeapon(use)) return use;
        if (TraitUtil.isRangedWeapon(p.getMainHandItem())) return p.getMainHandItem();
        if (TraitUtil.isRangedWeapon(p.getOffhandItem())) return p.getOffhandItem();
        return ItemStack.EMPTY;
    }

    /** The weapon an arrow was fired from (a copy made at firing time), or the owner's current bow as a fallback. */
    public static ItemStack bowOf(AbstractArrow arrow, Player owner) {
        ItemStack w = arrow.getWeaponItem();
        if (w != null && TraitUtil.isRangedWeapon(w)) return w;
        return shotStack(owner);
    }

    public static double mult(AbstractArrow arrow) {
        return arrow.getPersistentData().getDouble(MULT);
    }

    public static void addMult(AbstractArrow arrow, double fraction) {
        CompoundTag d = arrow.getPersistentData();
        d.putDouble(MULT, d.getDouble(MULT) + fraction);
    }

    /** Blocks between the place the arrow was fired and the target. */
    public static double distance(AbstractArrow arrow, LivingEntity target) {
        CompoundTag d = arrow.getPersistentData();
        if (d.contains(OX)) {
            return target.position().distanceTo(new Vec3(d.getDouble(OX), d.getDouble(OY), d.getDouble(OZ)));
        }
        Entity owner = arrow.getOwner();
        return owner == null ? 0 : owner.distanceTo(target);
    }

    /** An exact copy of an arrow (same owner, weapon, marks) that cannot be picked up. Not added to the level yet. */
    public static AbstractArrow copy(AbstractArrow src) {
        Level level = src.level();
        CompoundTag tag = new CompoundTag();
        src.saveWithoutId(tag);
        Entity e = src.getType().create(level);
        if (!(e instanceof AbstractArrow c)) return null;
        c.load(tag);
        c.setUUID(UUID.randomUUID());
        c.pickup = AbstractArrow.Pickup.DISALLOWED;
        return c;
    }

    /** Fires an extra arrow like src, turned by yawDegrees. */
    public static void spawnExtra(AbstractArrow src, double yawDegrees) {
        AbstractArrow c = copy(src);
        if (c == null) return;
        c.setDeltaMovement(src.getDeltaMovement().yRot((float) Math.toRadians(yawDegrees)));
        src.level().addFreshEntity(c);
    }

    private record Pending(Level level, AbstractArrow arrow, int[] ticksLeft) {}

    private static final java.util.List<Pending> PENDING = new java.util.ArrayList<>();

    /** An extra arrow that appears delayTicks after the shot and ignores invulnerability frames (Barrage). */
    public static void queueExtra(AbstractArrow src, int delayTicks) {
        AbstractArrow c = copy(src);
        if (c == null) return;
        c.getPersistentData().putBoolean(IGNORE_IFRAMES, true);
        PENDING.add(new Pending(src.level(), c, new int[]{Math.max(1, delayTicks)}));
    }

    public static void tickPending() {
        if (PENDING.isEmpty()) return;
        java.util.Iterator<Pending> it = PENDING.iterator();
        while (it.hasNext()) {
            Pending p = it.next();
            if (--p.ticksLeft()[0] > 0) continue;
            it.remove();
            p.level().addFreshEntity(p.arrow());
        }
    }

    public static void clearPending() {
        PENDING.clear();
    }

    private static java.lang.reflect.Method pierceSetter;

    /** Pierce level N lets an arrow hit N + 1 targets. setPierceLevel is private in AbstractArrow, so go through NBT if needed. */
    public static void setPierce(AbstractArrow arrow, int level) {
        if (arrow.getPierceLevel() >= level) return;
        try {
            if (pierceSetter == null) {
                pierceSetter = AbstractArrow.class.getDeclaredMethod("setPierceLevel", byte.class);
                pierceSetter.setAccessible(true);
            }
            pierceSetter.invoke(arrow, (byte) level);
            return;
        } catch (Throwable ignored) {
            // fall through to NBT
        }
        CompoundTag tag = new CompoundTag();
        arrow.saveWithoutId(tag);
        tag.putByte("PierceLevel", (byte) level);
        arrow.load(tag);
    }

    private Ranged() {}
}
