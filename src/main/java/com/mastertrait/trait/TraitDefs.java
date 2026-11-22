package com.mastertrait.trait;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Catalog of every trait id and its tunable numbers (default values). This is plain Java with no Minecraft
 * classes, so the config spec can be built from it very early. Percent values are plain numbers (5 = 5%),
 * durations are in seconds, ranges in blocks.
 */
public final class TraitDefs {
    public record ParamDef(String key, double def) {}

    private static final Map<String, List<ParamDef>> DEFS = new LinkedHashMap<>();
    private static final java.util.Set<String> CORE = new java.util.LinkedHashSet<>();

    /** Ids that belong to this mod itself (add-ons build their own config sections). */
    public static java.util.Set<String> coreIds() {
        return CORE;
    }

    private static void def(String id, Object... kv) {
        List<ParamDef> list = new ArrayList<>();
        for (int i = 0; i < kv.length; i += 2) {
            list.add(new ParamDef((String) kv[i], ((Number) kv[i + 1]).doubleValue()));
        }
        DEFS.put(id, list);
    }

    static {
        // damage vs. target type
        def("blazing", "damage", 4, "damagePerFireAspect", 1);
        def("insect_crusher", "damage", 7.5);
        def("holy_armament", "damage", 7.5);
        def("void_bane", "damage", 7.5);
        def("slaughter", "damage", 7.5);
        def("deadly_discipline", "damage", 4);
        def("wound_destroyer", "damage", 0.5, "maxDamage", 10, "resetSeconds", 5);
        // situational
                def("doom_slayer", "netherDamage", 10, "otherwiseDamage", 10);
        def("light_absorb", "damage", 8);
        def("maximum_might", "damage", 10);
        def("titans_vigor", "damagePerHealth", 0.15);
        def("menacing", "minMobs", 4, "range", 5, "damageReduction", 6);
        def("duelist", "range", 6, "damage", 8, "penalty", 8);
        def("peak_performance", "seconds", 10, "damage", 12);
        def("mount_master", "damage", 12, "damageReduction", 12);
        def("immovable_object", "damageReduction", 5, "knockbackResistance", 100);
        def("steadfast", "attackSpeed", 8, "knockbackResistance", 50);
        def("well_fed", "criticalChance", 3, "criticalDamage", 6);
        def("masochist", "damageReductionPerDebuff", 4, "maxDebuffs", 99);
        def("lone_wolf", "range", 16, "damage", 7.5, "healing", 7.5, "penalty", 10);
        def("helm_breaker", "armorPiercing", 4);
        // health based / random / collectors
        def("vanquished_soul", "criticalChance", 6, "healthBelow", 50);
        def("giant_slayer", "penalty", 50, "bonus", 15);
        def("overwhelming", "penalty", 50, "bonus", 15);
        def("grand_strike", "damage", 50);
        def("unstable_blade", "minPercent", 70, "maxPercent", 130);
        def("emerald_obsession", "damagePerEmerald", 0.05, "maxDamage", 16);
        def("satsujinki", "damagePerKill", 0.02, "maxDamage", 20);
        def("massacred", "damagePerStack", 5, "maxDamage", 20, "seconds", 10);
        // base stats
        def("critical_eyes", "criticalChance", 4);
        def("night_edge", "criticalDamage", 12);
        def("sky_striker", "damage", 24);
        def("tempered", "attackDamage", 0.5, "damage", 4);
        def("light_weight", "attackSpeed", 4);
        def("glass_cannon", "damage", 12, "damageTaken", 12);
        def("berserker_soul", "healthBelow", 50, "damageReduction", 10, "rage", 5);
        // status / healing
        def("vampirism", "lifesteal", 5);
        def("wither_hand", "witherSeconds", 5, "lifesteal", 4);
        def("venomous_edge", "damagePerPoisonLevel", 3);
        def("bloodthirst", "heal", 2);
        // on kill / special
        def("corpse_eater", "chance", 20, "hunger", 1);
        def("scavenger", "durability", 1);
        def("wicked_rune", "damagePercent", 8, "percentPerProtectionLevel", 1, "range", 4, "levelsPerRange", 4, "fixedDamage", 4, "hurtOwner", 1);
        def("midas_touch", "chance", 5, "chancePerLooting", 1, "absorptionSeconds", 30, "goldMin", 1, "goldMax", 3);
        def("solo_leveling", "experience", 25, "range", 16);
        
        def("hell_forged", "netherCriticalDamage", 8, "fireSeconds", 4, "blazingBrandSeconds", 12);
        def("rampager", "attackSpeedPerHit", 0.5, "maxAttackSpeed", 20, "resetSeconds", 3);
        def("tempest", "seconds", 15, "lightningDamageTaken", 100);
        def("phial_blade", "chance", 5, "seconds", 15, "cooldownSeconds", 60, "chancePerSweeping", 1,
                "durationPerSweeping", 5, "shareLevel", 3, "shareRange", 8, "shareSeconds", 15);
        def("holy_chant", "strengthSeconds", 60, "cooldownSeconds", 300, "cooldownReductionPerSmite", 30);
        def("illuminating", "damagePerLevel", 0.8);
        def("curse_extract", "damage", 4, "includeArmor", 1);
        def("beast_tamer", "range", 16, "damage", 50, "damageReduction", 50, "speed", 15, "sizeBonus", 20);
        def("engineer", "range", 8, "damage", 10, "damageReduction", 10, "damageThreshold", 30,
                "windowSeconds", 5, "healPercent", 20, "healRadius", 4, "cooldownSeconds", 180);
        def("party_leader", "range", 16, "damage", 25, "damageReduction", 40);
        def("know_my_name", "range", 16, "damage", 5, "damageReduction", 5);
        def("united", "range", 16, "damagePerPlayer", 4);
        def("echoing_blades", "chance", 5, "extraDamagePercent", 50, "damagePenalty", 5);
        def("ruinous_cleave", "chance", 5, "multiplier", 3, "criticalDamage", 20);
        def("moonlit_edge", "damage", 48);
        def("counterstrike", "seconds", 2, "damage", 12);
        def("momentum", "damagePerSecond", 5, "maxDamage", 20);
        def("banner_bearer", "range", 10, "movementSpeed", 4, "attackSpeed", 4);
        def("bounty_hunter", "minMaxHealth", 300, "expMultiplier", 3, "levels", 3, "rewards", 2);
        def("ethereal_steel", "chance", 25, "chanceLossPerLevel", 1, "damagePenalty", 20, "criticalChance", 4, "criticalDamage", 8);
        def("sunscorched", "damage", 12);
        def("dune_stalker", "movementSpeed", 6, "attackSpeed", 8);
        def("frostborn", "chance", 15, "seconds", 4);
        def("glacial_fang", "damage", 16, "healthAbove", 50, "extraDurability", 1);
        // shield
        def("spikey", "reflect", 10, "reflectPerThorns", 2.5);
        def("fortitude", "maxHealth", 2);
        def("shock_absorber", "damagePerStack", 1.5, "damagePerUnbreaking", 1, "radius", 3, "knockback", 0.8);
        def("bulwark", "armor", 2, "toughness", 1);
        def("hazard", "poisonSeconds", 5, "witherSeconds", 5);
        def("iron_bastion", "damageReductionPerBlock", 2, "maxDamageReduction", 10, "resetSeconds", 4);
        def("perfect_guard", "chance", 5, "damageDealtReduction", 5, "damageTakenIncrease", 5, "slownessLevel", 3, "seconds", 3, "debuffSeconds", 10);
        def("aegis", "chance", 10, "heal", 2, "cooldownSeconds", 30);
        def("stance", "damageReduction", 8);
        def("defiance", "toughnessPerBlock", 1, "maxToughness", 4, "resetSeconds", 4);
        def("kinetic_arsenal", "conversion", 20);
        def("storm_reservoir", "blocks", 10, "baseDamage", 8, "unbreakingScale", 0.5);
        def("wonder_reflect", "chance", 25, "damageMultiplier", 2);
        def("tanker_spirit", "lowHealthBelow", 15, "healPercent", 15, "cooldownSeconds", 300);
        // unique
        def("golden_rule", "experience", 15);
        def("core_shaker", "hits", 8, "range", 6, "baseDamage", 6, "damagePerBreach", 1, "damagePerMob", 1);
        // bow / crossbow
        def("sharp_shooter", "damagePerBlock", 2, "armorPiercingPerBlock", 1);
        def("oiled", "damage", 8, "extraFireSeconds", 1);
        def("rupture_shot", "damage", 5, "damagePerLevel", 3, "armorPiercingPerLevel", 1);
        def("behemoth_bane", "minMaxHealth", 300, "fixedDamage", 2, "damagePerLevel", 1);
        def("barrage", "chance", 15, "delayTicks", 3);
        def("merciless", "damage", 16, "healthBelow", 50);
        def("overdraw", "seconds", 6, "damage", 100);
        def("deadly_spot", "criticalDamage", 20);
        def("predator", "distance", 10, "criticalChance", 10);
        def("fatal_trajectory", "damage", 24, "healthBelow", 20, "armorPiercingPerLevel", 1);
        def("marked", "damage", 4, "maxDamage", 20, "resetSeconds", 5);
        def("quickdraw", "drawSpeed", 8);
        def("line_up", "targets", 3);
        def("tri_shot", "shots", 8);
        def("empowered", "shots", 6, "damage", 50);
        def("ricochet", "chance", 50, "range", 8);
        def("venom_coating", "damagePerLevel", 3, "maxDamage", 30, "maxPoisonLevel", 6);
        def("oblivion_bane", "range", 6, "witherSeconds", 5, "baseDamage", 4, "damagePerLevel", 2);
        def("overpowered", "damage", 50, "range", 3);
        def("seraphic_judgement", "damage", 16, "critHeal", 10);
        def("frenzy", "drawSpeedPerHit", 2, "maxDrawSpeed", 16, "damagePenalty", 8, "resetSeconds", 5);
        def("opening_shot", "damage", 100);
        def("liquify", "damage", 16);
        def("tailwind", "seconds", 10, "cooldownSeconds", 60);
        def("tight_string", "damage", 24, "drawSpeed", 24);
        def("shatterpoint", "armorIgnore", 50);
        def("phoenixs_last_arrow", "healthBelow", 20, "damage", 100, "heal", 10, "cooldownSeconds", 60);
        def("requiem_of_kings", "minMaxHealth", 300, "damage", 16, "range", 12, "buffDamage", 10, "buffSeconds", 12);
        // sword
        def("dragon_slayer", "minMaxHealth", 1800, "damage", 24, "damageReduction", 16);
        // trident
        def("will_of_the_ocean", "damage", 8, "damageReduction", 8);
        def("rain_dance", "rainDamage", 4, "rainAttackSpeed", 4, "thunderDamage", 8, "thunderAttackSpeed", 8);
        def("royal_impaler", "healthAbove", 75, "damage", 24, "penalty", 8);
        def("sundering", "armorIgnore", 20);
        def("coral_piercer", "healthMultiple", 2, "damage", 12, "criticalDamagePenalty", 8);
        def("executioners_prong", "healthBelow", 25, "damage", 24, "penalty", 8);
        def("relentless", "damagePerHit", 1, "maxDamage", 20, "resetSeconds", 4);
        def("linebreaker", "damage", 60, "range", 4);
        def("blessed_origin_water", "damage", 6, "attackSpeed", 6, "damageReduction", 6, "waterMultiplier", 2);
        def("abyssal_reaver", "aquaticDamage", 24, "damage", 8);
        def("glacial_undertow", "chance", 20, "seconds", 3, "slownessLevel", 1, "damage", 8);
        def("stormblood", "attackSpeedPerStack", 4, "maxAttackSpeed", 16, "seconds", 4);
        def("aegis_of_the_deep", "seconds", 15, "flatReduction", 1, "cooldownSeconds", 30);
        def("thorned_trident", "reflect", 30, "knockbackResistance", 20, "damage", 24);
        def("unyielding_tide", "damageReduction", 10, "seconds", 5);
        def("lightning_judgement", "hits", 10, "strikes", 3, "damage", 50, "range", 3);
        def("ocean_splitter", "seconds", 3, "damagePerSecond", 12.5, "waterMultiplier", 2);
        def("beastial", "criticalChance", 8, "criticalDamagePenalty", 8);
        // divine / heroic
        CORE.addAll(DEFS.keySet());
    }

    /** Add-on API: declares the numbers of an add-on trait (call before building the add-on's own config). */
    public static void registerAddon(String id, Object... kv) {
        def(id, kv);
    }

    public static Map<String, List<ParamDef>> all() {
        return DEFS;
    }

    public static double defaultOf(String id, String key) {
        List<ParamDef> list = DEFS.get(id);
        if (list != null) {
            for (ParamDef p : list) {
                if (p.key().equals(key)) return p.def();
            }
        }
        return 0;
    }

    private TraitDefs() {}
}
