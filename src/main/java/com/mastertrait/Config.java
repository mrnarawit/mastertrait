package com.mastertrait;

import com.mastertrait.trait.TraitDefs;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SERVER config (world/serverconfig/mastertrait-server.toml, synced to clients, so tooltips match the server).
 * Layout: [rolling], [compat] and [traits.<group>.<trait>] (enabled + numbers). Percent values are plain
 * numbers (5 = 5%), durations are seconds, ranges are blocks.
 */
public final class Config {
    /** ATTRIBUTE = use the other mod's attribute (internal math only if it is missing); INTERNAL = this mod's own math only; BOTH = both. */
    public enum Mode { ATTRIBUTE, INTERNAL, BOTH }

    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue HIDE_UNTIL_WITHER;
    public static final ModConfigSpec.IntValue REROLL_LEVEL_COST;
    public static final ModConfigSpec.DoubleValue ROLL_CHANCE;
    public static final ModConfigSpec.DoubleValue SECOND_TRAIT_CHANCE;
    public static final ModConfigSpec.DoubleValue HEROIC_CHANCE;
    public static final ModConfigSpec.EnumValue<Mode> DAMAGE_REDUCTION_MODE;
    public static final ModConfigSpec.EnumValue<Mode> LIFESTEAL_MODE;
    public static final ModConfigSpec.ConfigValue<String> RAGE_ATTRIBUTE;
    public static final ModConfigSpec.DoubleValue CRIT_CHANCE_SCALE;
    public static final ModConfigSpec.DoubleValue CRIT_DAMAGE_SCALE;
    public static final ModConfigSpec.DoubleValue DAMAGE_TAKEN_SCALE;
    public static final ModConfigSpec.EnumValue<Mode> REFLECT_MODE;
    public static final ModConfigSpec.DoubleValue LIFESTEAL_SCALE;
    public static final ModConfigSpec.DoubleValue REFLECT_SCALE;
    public static final ModConfigSpec.DoubleValue RAGE_SCALE;

    private static final Map<String, ModConfigSpec.BooleanValue> ENABLED = new HashMap<>();
    private static final Map<String, ModConfigSpec.DoubleValue> VALUES = new HashMap<>();

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.comment("How weapons and shields get their traits.").push("rolling");
        ROLL_CHANCE = b.comment("Chance (0.0 - 1.0) that a weapon or shield gets its random trait when it first enters an inventory.",
                "Guaranteed traits (Sharp Shooter on bows, Core Shaker on maces, Golden Rule on gold weapons) are always given.",
                "Tridents always get exactly 3 traits, whatever these chances say.")
                .defineInRange("rollChance", 1.0, 0.0, 1.0);
        SECOND_TRAIT_CHANCE = b.comment("Chance (0.0 - 1.0) of one more random trait after a random trait was rolled.")
                .defineInRange("secondTraitChance", 0.05, 0.0, 1.0);
        HEROIC_CHANCE = b.comment("Chance (0.0 - 1.0) that a rolled weapon gets a Heroic trait instead of a normal one (only if a Heroic trait exists).")
                .defineInRange("heroicChance", 0.02, 0.0, 1.0);
        b.pop();

        b.comment("Special rules.").push("special");
        HIDE_UNTIL_WITHER = b.comment("Divine, Heroic and Mythical traits show as scrambled text (name and description) until the player has slain the Wither.")
                .define("hideTopTraitsUntilWitherSlain", true);
        REROLL_LEVEL_COST = b.comment("Anvil: a weapon plus a Heart of the Sea rerolls its normal traits (Unique, Mythical, Heroic and Divine ones stay). Experience level cost.")
                .defineInRange("rerollLevelCost", 5, 0, 100);
        b.pop();

        b.comment("How trait bonuses are delivered. ATTRIBUTE = through the other mod's attribute (this mod's own math is used",
                "only if that attribute is missing), INTERNAL = this mod's own math only, BOTH = both.").push("compat");
        DAMAGE_REDUCTION_MODE = b.comment("Damage reduction / damage taken (spell_engine:damage_taken).")
                .defineEnum("damageReductionMode", Mode.ATTRIBUTE);
        LIFESTEAL_MODE = b.comment("Lifesteal (mrpg_lib:lifesteal_modifier).")
                .defineEnum("lifestealMode", Mode.ATTRIBUTE);
        REFLECT_MODE = b.comment("Damage reflection, used by Spikey (mrpg_lib:damage_reflect_modifier).")
                .defineEnum("reflectMode", Mode.ATTRIBUTE);
        RAGE_ATTRIBUTE = b.comment("Attribute id used for 'Rage' (e.g. some_mod:rage). Empty = not set.",
                "When empty, rage traits (Berserker Soul) fall back to plain damage bonuses.")
                .define("rageAttribute", "");

        b.comment("Attribute amount = (bonus as a fraction) * scale.",
                "scale 1.0 = fractions (5% sent as 0.05). scale 100 = percentage points (5% sent as 5).",
                "spell_engine:damage_taken and mrpg_lib:lifesteal_modifier have a base of 100, so they use 100.",
                "critical_strike:chance and critical_strike:damage are in percentage points (+4% = 4), so they use 100.")
                .push("attributeScales");
        CRIT_CHANCE_SCALE = b.defineInRange("critical_strike_chance", 100.0, 0.0001, 10000.0);
        CRIT_DAMAGE_SCALE = b.defineInRange("critical_strike_damage", 100.0, 0.0001, 10000.0);
        DAMAGE_TAKEN_SCALE = b.defineInRange("spell_engine_damage_taken", 100.0, 0.0001, 10000.0);
        LIFESTEAL_SCALE = b.defineInRange("mrpg_lib_lifesteal_modifier", 100.0, 0.0001, 10000.0);
        REFLECT_SCALE = b.defineInRange("mrpg_lib_damage_reflect_modifier", 100.0, 0.0001, 10000.0);
        RAGE_SCALE = b.defineInRange("rage", 100.0, 0.0001, 10000.0);
        b.pop();
        b.pop();

        b.comment("Per-trait settings, grouped by item type: [traits.<group>.<trait>].",
                "'enabled' = false removes a trait from rolling and disables its effects.",
                "Numbers: percent values are plain numbers (5 = 5%), durations are in seconds, ranges are in blocks.",
                "Add-on mods write their own file (world/serverconfig/<addon>-server.toml) in the same layout.")
                .push("traits");
        defineTraits(b, TraitDefs.coreIds());
        b.pop();

        SPEC = b.build();
    }

    /**
     * Writes [<group>.<trait>] sections (enabled + numbers, each with a unit comment) for the given ids.
     * Add-ons call this inside their own "traits" section.
     */
    public static void defineTraits(ModConfigSpec.Builder b, java.util.Collection<String> ids) {
        Map<String, List<String>> byGroup = new java.util.LinkedHashMap<>();
        for (String id : ids) {
            if (TraitDefs.all().get(id) == null) continue;
            byGroup.computeIfAbsent(TraitDefs.groupOf(id), g -> new java.util.ArrayList<>()).add(id);
        }
        for (Map.Entry<String, List<String>> e : byGroup.entrySet()) {
            b.comment("Traits: " + e.getKey()).push(e.getKey());
            for (String id : e.getValue()) {
                b.comment(TraitDefs.prettyName(id)).push(id);
                ENABLED.put(id, b.comment("false = never rolled, effects disabled").define("enabled", true));
                for (TraitDefs.ParamDef p : TraitDefs.all().get(id)) {
                    String unit = TraitDefs.unitOf(id, p.key());
                    if (unit != null) b.comment(unit);
                    VALUES.put(id + "." + p.key(), b.defineInRange(p.key(), p.def(), 0.0, 1000000.0));
                }
                b.pop();
            }
            b.pop();
        }
    }

    public static boolean isEnabled(String id) {
        ModConfigSpec.BooleanValue v = ENABLED.get(id);
        if (v == null) return true;
        try {
            return v.get();
        } catch (RuntimeException e) {
            return true; // config not loaded yet
        }
    }

    public static double value(String id, String key) {
        ModConfigSpec.DoubleValue v = VALUES.get(id + "." + key);
        if (v == null) return TraitDefs.defaultOf(id, key);
        try {
            return v.get();
        } catch (RuntimeException e) {
            return TraitDefs.defaultOf(id, key); // config not loaded yet
        }
    }

    private Config() {}
}
