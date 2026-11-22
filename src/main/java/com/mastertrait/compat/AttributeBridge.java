package com.mastertrait.compat;

import com.mojang.logging.LogUtils;
import com.mastertrait.Config;
import com.mastertrait.MasterTrait;
import com.mastertrait.trait.Stats;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Applies trait bonuses as transient attribute modifiers. Attributes from other mods (Critical Strike,
 * Spell Engine, MRPG Lib) are looked up by id at runtime, so there is no compile-time dependency and a
 * missing attribute is simply skipped.
 */
public final class AttributeBridge {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final ResourceLocation CRIT_CHANCE = ResourceLocation.fromNamespaceAndPath("critical_strike", "chance");
    public static final ResourceLocation CRIT_DAMAGE = ResourceLocation.fromNamespaceAndPath("critical_strike", "damage");
    public static final ResourceLocation DAMAGE_TAKEN = ResourceLocation.fromNamespaceAndPath("spell_engine", "damage_taken");
    public static final ResourceLocation LIFESTEAL = ResourceLocation.fromNamespaceAndPath("mrpg_lib", "lifesteal_modifier");
    public static final ResourceLocation ARMOR_PIERCING = ResourceLocation.fromNamespaceAndPath("mrpg_lib", "armor_piercing");

    public static final ResourceLocation REFLECT = ResourceLocation.fromNamespaceAndPath("mrpg_lib", "damage_reflect_modifier");
    public static final ResourceLocation SPELL_POWER_WATER = ResourceLocation.fromNamespaceAndPath("spell_power", "water");

    /** Modifier ids for one "layer" of bonuses. HELD = while weapon is held, PRE = just before an attack. */
    public static final class Slots {
        final ResourceLocation speed, damage, knockback, critChance, critDamage, taken, lifesteal, rage, piercing, water, reflect, health, armor, toughness, move;

        Slots(String prefix) {
            speed = id(prefix + "_attack_speed");
            damage = id(prefix + "_attack_damage");
            knockback = id(prefix + "_knockback_resistance");
            critChance = id(prefix + "_crit_chance");
            critDamage = id(prefix + "_crit_damage");
            taken = id(prefix + "_damage_taken");
            lifesteal = id(prefix + "_lifesteal");
            rage = id(prefix + "_rage");
            piercing = id(prefix + "_armor_piercing");
            water = id(prefix + "_water_spell_power");
            reflect = id(prefix + "_reflect");
            health = id(prefix + "_max_health");
            armor = id(prefix + "_armor");
            toughness = id(prefix + "_armor_toughness");
            move = id(prefix + "_move_speed");
        }
    }

    public static final Slots HELD = new Slots("held");
    public static final Slots PRE = new Slots("pre");

    private static final Map<ResourceLocation, Holder<Attribute>> FOUND = new ConcurrentHashMap<>();
    private static final Set<ResourceLocation> MISSING = ConcurrentHashMap.newKeySet();

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MasterTrait.MODID, path);
    }

    public static Holder<Attribute> find(ResourceLocation id) {
        Holder<Attribute> cached = FOUND.get(id);
        if (cached != null) return cached;
        if (MISSING.contains(id)) return null;
        Holder.Reference<Attribute> ref = BuiltInRegistries.ATTRIBUTE.getHolder(id).orElse(null);
        if (ref == null) {
            MISSING.add(id);
            LOGGER.warn("Attribute {} is not registered; traits that use it will have no effect.", id);
            return null;
        }
        FOUND.put(id, ref);
        return ref;
    }

    public static boolean has(ResourceLocation id) {
        return find(id) != null;
    }

    private static boolean useAttribute(Config.Mode m, ResourceLocation id) {
        return m != Config.Mode.INTERNAL && has(id);
    }

    private static boolean useInternal(Config.Mode m, ResourceLocation id) {
        return m != Config.Mode.ATTRIBUTE || !has(id);
    }

    /** The attribute configured as "Rage" (rageAttribute in the config), or null if unset/missing. */
    public static Holder<Attribute> rageHolder() {
        String raw = Config.RAGE_ATTRIBUTE.get().trim();
        if (raw.isEmpty()) return null;
        ResourceLocation id = ResourceLocation.tryParse(raw);
        return id == null ? null : find(id);
    }

    public static boolean rageConfigured() {
        return rageHolder() != null;
    }

    public static boolean damageTakenViaAttribute() {
        return useAttribute(Config.DAMAGE_REDUCTION_MODE.get(), DAMAGE_TAKEN);
    }

    public static boolean damageTakenInternal() {
        return useInternal(Config.DAMAGE_REDUCTION_MODE.get(), DAMAGE_TAKEN);
    }

    public static boolean lifestealViaAttribute() {
        return useAttribute(Config.LIFESTEAL_MODE.get(), LIFESTEAL);
    }

    public static boolean reflectViaAttribute() {
        return useAttribute(Config.REFLECT_MODE.get(), REFLECT);
    }

    public static boolean reflectInternal() {
        return useInternal(Config.REFLECT_MODE.get(), REFLECT);
    }

    public static boolean lifestealInternal() {
        return useInternal(Config.LIFESTEAL_MODE.get(), LIFESTEAL);
    }

    public static void apply(Player p, Stats s, Slots slots) {
        set(p, Attributes.ATTACK_SPEED, slots.speed, s.attackSpeed, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        set(p, Attributes.ATTACK_DAMAGE, slots.damage, s.attackDamage, AttributeModifier.Operation.ADD_VALUE);
        set(p, Attributes.KNOCKBACK_RESISTANCE, slots.knockback, s.knockbackRes, AttributeModifier.Operation.ADD_VALUE);
        setCustom(p, CRIT_CHANCE, slots.critChance, s.critChance, Config.CRIT_CHANCE_SCALE.get(), true);
        setCustom(p, CRIT_DAMAGE, slots.critDamage, s.critDamage, Config.CRIT_DAMAGE_SCALE.get(), true);
        setCustom(p, DAMAGE_TAKEN, slots.taken, damageTakenViaAttribute() ? s.damageTaken : 0, Config.DAMAGE_TAKEN_SCALE.get());
        setCustom(p, ARMOR_PIERCING, slots.piercing, s.armorPiercing, 1.0);
        setCustom(p, REFLECT, slots.reflect, reflectViaAttribute() ? s.reflect : 0, Config.REFLECT_SCALE.get());
        set(p, Attributes.MAX_HEALTH, slots.health, s.maxHealth, AttributeModifier.Operation.ADD_VALUE);
        set(p, Attributes.MOVEMENT_SPEED, slots.move, s.moveSpeed, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        set(p, Attributes.ARMOR, slots.armor, s.armor, AttributeModifier.Operation.ADD_VALUE);
        set(p, Attributes.ARMOR_TOUGHNESS, slots.toughness, s.toughness, AttributeModifier.Operation.ADD_VALUE);
        Holder<Attribute> water = (s.waterSpellPower == 0 && !FOUND.containsKey(SPELL_POWER_WATER)) ? null : find(SPELL_POWER_WATER);
        if (water != null) set(p, water, slots.water, s.waterSpellPower, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        Holder<Attribute> rage = rageHolder();
        if (rage != null) set(p, rage, slots.rage, s.rage * Config.RAGE_SCALE.get(), AttributeModifier.Operation.ADD_VALUE);
        setCustom(p, LIFESTEAL, slots.lifesteal, lifestealViaAttribute() ? s.lifesteal : 0, Config.LIFESTEAL_SCALE.get());
    }

    private static void setCustom(Player p, ResourceLocation attr, ResourceLocation modId, double fraction, double scale) {
        setCustom(p, attr, modId, fraction, scale, false);
    }

    /** exactFlat: the final value rises by exactly the bonus, even when other mods multiply the attribute (e.g. a +5% total). */
    private static void setCustom(Player p, ResourceLocation attr, ResourceLocation modId, double fraction, double scale, boolean exactFlat) {
        // Do not look the attribute up when there is nothing to apply and nothing to remove.
        Holder<Attribute> holder = fraction == 0 && !FOUND.containsKey(attr) ? null : find(attr);
        if (holder == null) return;
        double amount = fraction * scale;
        if (exactFlat && amount != 0) {
            AttributeInstance inst = p.getAttribute(holder);
            if (inst != null) {
                // ADD_VALUE is multiplied afterwards by (1 + sum of BASE) * product(1 + TOTAL): divide that out.
                double base = 0, total = 1;
                for (AttributeModifier m : inst.getModifiers()) {
                    if (m.id().equals(modId)) continue;
                    if (m.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_BASE) base += m.amount();
                    else if (m.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL) total *= 1 + m.amount();
                }
                double mult = (1 + base) * total;
                if (mult > 1.0E-6) amount /= mult;
            }
        }
        set(p, holder, modId, amount, AttributeModifier.Operation.ADD_VALUE);
    }

    private static void set(Player p, Holder<Attribute> attribute, ResourceLocation modId,
                            double amount, AttributeModifier.Operation op) {
        AttributeInstance inst = p.getAttribute(attribute);
        if (inst == null) return;
        AttributeModifier existing = inst.getModifier(modId);
        if (amount == 0) {
            if (existing != null) inst.removeModifier(modId);
            return;
        }
        if (existing != null && existing.amount() == amount && existing.operation() == op) return;
        inst.addOrUpdateTransientModifier(new AttributeModifier(modId, amount, op));
    }

    private AttributeBridge() {}
}
