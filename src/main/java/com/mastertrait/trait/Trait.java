package com.mastertrait.trait;

import com.mastertrait.Config;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;

/**
 * Base class for every trait. All hooks have no-op defaults. Bonuses are fractions: 0.10 = +10%.
 * Numbers come from the server config through {@link #value(String)} / {@link #pct(String)}.
 */
public abstract class Trait {
    private final String id;
    private final Tier tier;

    protected Trait(String id) {
        this(id, Tier.TRAIT);
    }

    protected Trait(String id, Tier tier) {
        this.id = id;
        this.tier = tier;
    }

    public String id() {
        return id;
    }

    public Tier tier() {
        return tier;
    }

    /** Item kind this trait can roll on. */
    public Kind kind() {
        return Kind.MELEE;
    }

    /** Which config section holds this trait's numbers (Nitoryu L shares Nitoryu R's). */
    public String paramsId() {
        return id;
    }

    /** False for traits that are never rolled (evolved or not obtainable yet). */
    public boolean rollable() {
        return true;
    }

    /** True for traits that only appear on tridents (never rolled on other weapons). */
    public boolean tridentOnly() {
        return false;
    }

    /** Whether this weapon can roll this trait (used by Unique traits tied to certain items). */
    public boolean eligible(ItemStack weapon) {
        return true;
    }

    /** Called once after the trait was rolled onto the weapon (e.g. to rename it). */
    public void onRolled(ItemStack weapon) {
    }

    /** True if this trait has a cooldown stored on the weapon (shown as Ready / m:ss in the tooltip). */
    public boolean hasCooldown() {
        return false;
    }

    /** Colour of the trait name in the tooltip, or null for the tier default. */
    public ChatFormatting nameColor() {
        return null;
    }

    /** Name colour for this particular item (e.g. Satsujinki turns red when complete); defaults to nameColor(). */
    public ChatFormatting nameColor(ItemStack weapon) {
        return nameColor();
    }

    /** Colour of the progress lines for this item. */
    public ChatFormatting progressColor(ItemStack weapon) {
        return ChatFormatting.AQUA;
    }

    /** Base hue (0-1) of the shimmering header/name of Divine traits. */
    public float divineHue() {
        return 0.58f;
    }

    /** False to hide the "Unique Trait:" style label and show only the name. */
    public boolean showLabel() {
        return true;
    }

    /** True to draw the trait name in animated rainbow colours (used without a label). */
    public boolean rainbowName() {
        return false;
    }

    /** Colour of the description lines in the tooltip. */
    public ChatFormatting descColor() {
        return ChatFormatting.YELLOW;
    }

    public String nameKey() {
        return "trait.mastertrait." + id + ".name";
    }

    public String descKey() {
        return "trait.mastertrait." + id + ".desc";
    }

    // ------------------------------------------------------------------ config access

    public double value(String key) {
        return Config.value(paramsId(), key);
    }

    /** Value as a fraction (5 -> 0.05). */
    public double pct(String key) {
        return value(key) / 100.0;
    }

    /** Seconds -> ticks. */
    public int ticks(String key) {
        return (int) Math.round(value(key) * 20.0);
    }

    // ------------------------------------------------------------------ hooks

    /** Bonus to damage dealt with a melee hit (event math, evaluated before the hit lands). */
    public double damageDealtBonus(HitContext c) {
        return 0;
    }

    /** True to make this melee hit ignore the target's armor. */
    public boolean ignoresArmor(HitContext c) {
        return false;
    }

    /** Fraction (0-1) of the target's armor this hit ignores (melee, thrown trident or arrow). */
    public double armorIgnore(HitContext c) {
        return 0;
    }

    /** Flat damage removed from every hit the holder takes (before armor). */
    public double flatDamageReduction(Player holder, DamageSource source) {
        return 0;
    }

    /** True to make this melee hit kill the target outright. */
    public boolean instakill(HitContext c) {
        return false;
    }

    /** Attribute-style bonuses while the weapon is in the main hand (refreshed every 2 ticks). */
    public void collectModifiers(Player holder, Stats stats) {
    }

    /** Temporary attribute bonuses that depend on the target, applied just before an attack lands. */
    public void collectPreAttack(Player attacker, LivingEntity target, Stats stats) {
    }

    /** Return true to fully cancel an incoming damage source (e.g. fire immunity). */
    public boolean negatesDamage(Player holder, DamageSource source) {
        return false;
    }

    /** After a melee hit landed. {@code dealt} is the final damage. */
    public void onHit(HitContext c, float dealt) {
    }

    /** The holder killed a mob. */
    public void onKill(Player holder, LivingEntity victim) {
    }

    /** The holder took damage (after reductions). */
    public void onDamaged(Player holder, DamageSource source, float dealt) {
    }

    /** Every server tick while the weapon is in the main hand. */
    public void onTick(Player holder) {
    }

    public void onStruckByLightning(Player holder, LightningBolt bolt) {
    }

    /** The holder said something in chat. */
    public void onChat(Player holder, String message) {
    }

    /** Fractional bonus to experience dropped by a mob the holder killed. */
    public double expBonus(Player holder, LivingEntity victim) {
        return 0;
    }

    /** Fractional bonus to healing the holder receives. */
    public double healingReceivedBonus(Player holder) {
        return 0;
    }

    /** Extra damage fraction for a thrown trident that carries this trait. */
    public double thrownDamageBonus(HitContext c) {
        return 0;
    }

    /** A thrown trident carrying this trait hit a target. */
    public void onThrownHit(HitContext c, float dealt) {
    }

    /** Fraction added to damage the holder takes from this source (negative = reduction). */
    public double damageTakenBonus(Player holder, DamageSource source) {
        return 0;
    }

    /** Flat damage added after all multipliers on a melee hit. */
    public double flatDamageBonus(HitContext c) {
        return 0;
    }

    /** The holder blocked a hit with the shield carrying this trait. */
    public void onBlock(Player holder, ItemStack shield, DamageSource source, float blocked) {
    }

    /** The holder lowered the shield carrying this trait. */
    public void onShieldRelease(Player holder, ItemStack shield) {
    }

    /** Tooltip progress line (client side). Return null for none. */
    public Component progress(ItemStack weapon, Player viewer, long gameTime) {
        return null;
    }

    // ------------------------------------------------------------------ bow / crossbow hooks

    /** Bonus to damage dealt by an arrow fired from this weapon (ctx.projectile() is the arrow). */
    public double rangedDamageBonus(HitContext c) {
        return 0;
    }

    /** Target-dependent attribute bonuses (crit chance, armor piercing) applied just before an arrow hits. */
    public void collectPreShot(HitContext c, Stats stats) {
    }

    /** After an arrow from this weapon hit a target. */
    public void onRangedHit(HitContext c, float dealt) {
    }

    /** After an arrow from this weapon killed the target. */
    public void onRangedKill(HitContext c) {
    }

    /** Fraction by which drawing / loading is faster (0.08 = 8% less charge time). */
    public double chargeSpeed(Player holder, ItemStack weapon) {
        return 0;
    }

    /** When an arrow is fired. firstOfShot is false for the 2nd, 3rd... arrow of one shot (multishot). */
    public void onShoot(Player shooter, ItemStack weapon, AbstractArrow arrow, boolean firstOfShot) {
    }
}
