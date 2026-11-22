package com.mastertrait.trait;

import com.mastertrait.Config;
import com.mastertrait.compat.AttributeBridge;
import com.mastertrait.compat.PehkuiCompat;
import net.minecraft.ChatFormatting;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.FlyingMob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.animal.FlyingAnimal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.item.TridentItem;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static com.mastertrait.trait.TraitUtil.*;

/** Registry of every trait. Numbers come from the server config (see TraitDefs). */
public final class Traits {
    private static final Map<String, Trait> ALL = new LinkedHashMap<>();

    private static Trait reg(Trait t) {
        return register(t);
    }

    /** Public API: add-on mods register their own traits (before any item is rolled, e.g. in the mod constructor). */
    public static Trait register(Trait t) {
        ALL.put(t.id(), t);
        return t;
    }

    /** Old ids that were renamed; items rolled earlier keep working. */
    private static final Map<String, String> ALIAS = new java.util.HashMap<>(Map.of(
            "colossus_hunter", "behemoth_bane",
            "triad_volley", "tri_shot",
            "eagles_eyes", "predator"));

    /** Add-on API: items rolled with the old id keep working after a trait was renamed. */
    public static void addAlias(String oldId, String newId) {
        ALIAS.put(oldId, newId);
    }

    public static Trait get(String id) {
        Trait t = ALL.get(id);
        return t != null ? t : ALL.get(ALIAS.getOrDefault(id, id));
    }

    /** Per-thrown-trident marks (Ocean Splitter). */
    public static final String TRIDENT_CHARGE = "mt_tri_charge";
    public static final String TRIDENT_WATER = "mt_tri_water";

    /** General traits that tridents can also roll. */
    private static final Set<String> TRIDENT_GENERAL = Set.of("critical_eyes", "sky_striker", "scavenger", "banner_bearer",
            "bounty_hunter", "party_leader", "beast_tamer", "engineer", "united");

    public static Collection<String> ids() {
        return ALL.keySet();
    }

    // ------------------------------------------------------------------ rolling

    public static List<Trait> pool(ItemStack weapon, boolean heroic) {
        List<Trait> out = new ArrayList<>();
        boolean trident = TraitUtil.isTrident(weapon);
        for (Trait t : ALL.values()) {
            if (!t.rollable() || !Config.isEnabled(t.id())) continue;
            if (trident) {
                if (heroic || !(t.tridentOnly() || TRIDENT_GENERAL.contains(t.id()))) continue;
            } else if (t.tridentOnly()) continue;
            boolean isHeroic = t.tier() == Tier.HEROIC;
            if (isHeroic != heroic) continue;
            if (t.tier() == Tier.DIVINE) continue; // divine traits are not obtainable by rolling yet
            if (!t.eligible(weapon)) continue;
            Kind k = t.kind();
            if (!k.fits(weapon)) continue;
            out.add(t);
        }
        return out;
    }

    /**
     * Traits a weapon rolls. Tridents always get exactly three traits and ignore rollChance.
     * Other weapons get one trait (or none when rollChance fails). Unique traits only join the pool of
     * weapons they are eligible for (Nitoryu R: netherite sword, Nitoryu L: diamond sword, Golden Rule: gold).
     */
    public static List<Trait> rollFor(ItemStack weapon, RandomSource rand) {
        List<Trait> result = new ArrayList<>();
        List<RollRule> rules = rulesFor(weapon);
        List<Trait> custom = null;
        for (RollRule rule : rules) {
            for (Trait t : rule.guaranteed(weapon)) {
                if (Config.isEnabled(t.id()) && !result.contains(t)) result.add(t);
            }
            if (rule.onlyGuaranteed(weapon)) return sortByTier(result);
            if (custom == null) custom = rule.candidates(weapon);
        }
        // guaranteed traits
        if (TraitUtil.isRangedWeapon(weapon)) {
            if (Config.isEnabled(SHARP_SHOOTER.id())) result.add(SHARP_SHOOTER);
        } else if (weapon.getItem() instanceof MaceItem) {
            if (Config.isEnabled(CORE_SHAKER.id())) result.add(CORE_SHAKER);
        } else if (weapon.getItem() instanceof TridentItem) {
            // tridents always get exactly 3 traits from their own pool (trident-only + a few general ones)
            List<Trait> pool = pool(weapon, false);
            while (result.size() < 3 && !pool.isEmpty()) {
                result.add(pool.remove(rand.nextInt(pool.size())));
            }
            return sortByTier(result);
        }
        // Divine, Heroic and Mythical traits replace the normal random trait
        for (Trait t : result) {
            if (t.tier().isTop()) return sortByTier(result);
        }
        // random traits: rollChance for one, then secondTraitChance for another
        if (rand.nextDouble() >= Config.ROLL_CHANCE.get()) return sortByTier(result);
        if (!TraitUtil.isShield(weapon) && rules.isEmpty()) {
            List<Trait> heroic = pool(weapon, true);
            if (!heroic.isEmpty() && rand.nextDouble() < Config.HEROIC_CHANCE.get()) {
                result.add(heroic.get(rand.nextInt(heroic.size())));
                return sortByTier(result);
            }
        }
        for (int i = 0; i < 2; i++) {
            List<Trait> general = custom != null ? new ArrayList<>(custom) : pool(weapon, false);
            general.removeAll(result);
            if (general.isEmpty()) break;
            result.add(general.get(rand.nextInt(general.size())));
            if (rand.nextDouble() >= Config.SECOND_TRAIT_CHANCE.get()) break;
        }
        return sortByTier(result);
    }

    /** Highest tier first: Divine, Heroic, Mythical, Unique, Trait (stable). */
    public static List<Trait> sortByTier(List<Trait> traits) {
        traits.sort(java.util.Comparator.comparingInt(x -> x.tier().rank()));
        return traits;
    }

    /**
     * Anvil reroll: replaces the normal (Trait tier) traits of a weapon with new random ones, same count.
     * Returns the new trait list, or null if the weapon has no normal trait.
     */
    public static List<String> rerollNormal(ItemStack weapon, RandomSource rand) {
        List<Trait> keep = new ArrayList<>();
        int removed = 0;
        for (String id : TraitUtil.traitIds(weapon)) {
            Trait t = get(id);
            if (t != null && t.tier() == Tier.TRAIT) removed++;
            else if (t != null) keep.add(t);
        }
        if (removed == 0) return null;
        List<Trait> result = new ArrayList<>(keep);
        for (int i = 0; i < removed; i++) {
            List<Trait> pool = pool(weapon, false);
            pool.removeAll(result);
            if (pool.isEmpty()) break;
            result.add(pool.get(rand.nextInt(pool.size())));
        }
        sortByTier(result);
        List<String> ids = new ArrayList<>();
        for (Trait t : result) ids.add(t.id());
        return ids;
    }

    // ------------------------------------------------------------------ roll rules (add-on API)

    /** Add-on API: special rolling for certain items (guaranteed traits, a custom pool for the random trait). */
    public interface RollRule {
        /** True if this rule handles the item (the item is also rolled when it is not a normal weapon / shield). */
        boolean applies(ItemStack stack);

        /** Traits the item always gets. */
        List<Trait> guaranteed(ItemStack stack);

        /** True if the item gets no random trait at all. */
        default boolean onlyGuaranteed(ItemStack stack) {
            return false;
        }

        /** Pool of the random trait, or null for the normal pool of the item. */
        default List<Trait> candidates(ItemStack stack) {
            return null;
        }
    }

    private static final List<RollRule> RULES = new ArrayList<>();

    public static void addRollRule(RollRule rule) {
        RULES.add(rule);
    }

    public static boolean hasRollRule(ItemStack stack) {
        return !rulesFor(stack).isEmpty();
    }

    /** Every rule that applies: guaranteed traits are added together, the first custom pool wins. */
    private static List<RollRule> rulesFor(ItemStack stack) {
        if (stack.isEmpty()) return List.of();
        List<RollRule> out = new ArrayList<>();
        for (RollRule r : RULES) {
            if (r.applies(stack)) out.add(r);
        }
        return out;
    }

    private static Player roller;

    /** Add-on API: the player whose inventory is being rolled right now (null when unknown), e.g. to check the dimension. */
    public static Player currentRoller() {
        return roller;
    }

    public static void setRoller(Player p) {
        roller = p;
    }

    // ------------------------------------------------------------------ helpers

    public static long now(Player p) {
        return p.level().getGameTime();
    }

    private static boolean isStill(Player p) {
        double dx = p.getX() - p.xo;
        double dz = p.getZ() - p.zo;
        return dx * dx + dz * dz < 1.0E-5 && p.onGround();
    }

    private static boolean isNight(Level level) {
        if (level.dimensionType().hasFixedTime()) return false;
        long t = level.getDayTime() % 24000L;
        return t >= 13000L && t < 23000L;
    }

    private static int harmfulEffects(LivingEntity e) {
        int n = 0;
        for (MobEffectInstance inst : e.getActiveEffects()) {
            if (inst.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) n++;
        }
        return n;
    }

    private static boolean isBoss(Mob m) {
        return m.getType().is(Tags.EntityTypes.BOSSES) || m instanceof EnderDragon || m instanceof WitherBoss;
    }

    private static String num(double v) {
        if (Math.abs(v - Math.rint(v)) < 1.0E-9) return Long.toString(Math.round(v));
        String s = String.format(Locale.ROOT, "%.2f", v).replaceAll("0+$", "");
        return s.endsWith(".") ? s.substring(0, s.length() - 1) : s;
    }

    private static String normalize(String s) {
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
    }

    private static int enchantLevel(Level level, ItemStack stack, ResourceKey<Enchantment> key) {
        if (stack.isEmpty()) return 0;
        Holder<Enchantment> holder = level.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolderOrThrow(key);
        return stack.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(holder);
    }

    private static boolean isFlyingTarget(LivingEntity t) {
        if (t instanceof FlyingMob || t instanceof FlyingAnimal || t instanceof EnderDragon) return true;
        if (t.getType() == EntityType.BLAZE) return true;
        if (t.isFallFlying()) return true;
        return t instanceof Mob m && m.getNavigation() instanceof FlyingPathNavigation;
    }

    public static void addTrait(ItemStack weapon, String id) {
        List<String> ids = new ArrayList<>(traitIds(weapon));
        if (!ids.contains(id)) {
            ids.add(id);
            setTraits(weapon, ids);
        }
    }

    public static int cap(double perUnit, double maxPercent) {
        return perUnit <= 0 ? 0 : (int) Math.floor(maxPercent / perUnit + 1.0E-9);
    }

    // ------------------------------------------------------------------ damage vs. target type

    public static final Trait BLAZING = reg(new Trait("blazing") {
        @Override public double damageDealtBonus(HitContext c) {
            if (!c.target().isOnFire()) return 0;
            return pct("damage") + enchantLevel(c.attacker().level(), c.weapon(), Enchantments.FIRE_ASPECT) * pct("damagePerFireAspect");
        }
        @Override public Component progress(ItemStack w, Player viewer, long gameTime) {
            if (viewer == null) return null;
            int lvl = enchantLevel(viewer.level(), w, Enchantments.FIRE_ASPECT);
            return Component.literal("Fire Aspect " + lvl + ": +" + num((pct("damage") + lvl * pct("damagePerFireAspect")) * 100) + "% damage");
        }
    });

    public static final Trait INSECT_CRUSHER = reg(new Trait("insect_crusher") {
        @Override public double damageDealtBonus(HitContext c) {
            return c.target().getType().is(EntityTypeTags.ARTHROPOD) ? pct("damage") : 0;
        }
    });

    public static final Trait HOLY_ARMAMENT = reg(new Trait("holy_armament") {
        @Override public double damageDealtBonus(HitContext c) {
            return c.target().getType().is(EntityTypeTags.UNDEAD) ? pct("damage") : 0;
        }
    });

    private static final Set<EntityType<?>> END_MOBS =
            Set.<EntityType<?>>of(EntityType.ENDERMAN, EntityType.ENDERMITE, EntityType.SHULKER, EntityType.ENDER_DRAGON);

    public static final Trait VOID_BANE = reg(new Trait("void_bane") {
        @Override public double damageDealtBonus(HitContext c) {
            return END_MOBS.contains(c.target().getType()) ? pct("damage") : 0;
        }
    });

    public static final Trait SLAUGHTER = reg(new Trait("slaughter") {
        @Override public double damageDealtBonus(HitContext c) {
            EntityType<?> t = c.target().getType();
            return (t.is(EntityTypeTags.ILLAGER) || t == EntityType.VILLAGER) ? pct("damage") : 0;
        }
    });

    public static final Trait DEADLY_DISCIPLINE = reg(new Trait("deadly_discipline") {
        @Override public double damageDealtBonus(HitContext c) {
            return harmfulEffects(c.target()) * pct("damage");
        }
    });

    private static int woundStacks(Trait t, HitContext c) {
        CompoundTag d = c.attacker().getPersistentData();
        if (!d.hasUUID(WD_TARGET) || !d.getUUID(WD_TARGET).equals(c.target().getUUID())) return 0;
        if (now(c.attacker()) - d.getLong(WD_LAST) > t.ticks("resetSeconds")) return 0;
        return d.getInt(WD_STACKS);
    }

    public static double woundBonus(Trait t, HitContext c) {
        return Math.min(t.pct("maxDamage"), woundStacks(t, c) * t.pct("damage"));
    }

    public static void woundHit(Trait t, HitContext c) {
        Player p = c.attacker();
        CompoundTag d = p.getPersistentData();
        int max = cap(t.value("damage"), t.value("maxDamage"));
        d.putUUID(WD_TARGET, c.target().getUUID());
        d.putInt(WD_STACKS, Math.min(max, woundStacks(t, c) + 1));
        d.putLong(WD_LAST, now(p));
    }

    public static final Trait WOUND_DESTROYER = reg(new Trait("wound_destroyer") {
        @Override public double damageDealtBonus(HitContext c) {
            return woundBonus(this, c);
        }
        @Override public void onHit(HitContext c, float dealt) {
            woundHit(this, c);
        }
    });

    // ------------------------------------------------------------------ trident traits

    private static final String P_RELENTLESS = "mt_relentless";
    private static final String P_RELENTLESS_T = "mt_relentless_t";
    private static final String P_STORM = "mt_storm";
    private static final String P_STORM_T = "mt_storm_t";
    private static final String P_THORN = "mt_thorn";
    private static final String P_TIDE_UNTIL = "mt_tide_until";
    private static final String P_LIGHTNING = "mt_lightning_hits";

    private static boolean aboveHealth(LivingEntity t, double fraction) {
        return t.getHealth() > t.getMaxHealth() * fraction;
    }

    private static boolean isAquatic(LivingEntity t) {
        return t.getType().is(EntityTypeTags.AQUATIC) || t instanceof net.minecraft.world.entity.animal.WaterAnimal
                || t instanceof net.minecraft.world.entity.monster.Drowned || t instanceof net.minecraft.world.entity.monster.Guardian;
    }

    public static final Trait WILL_OF_THE_OCEAN = reg(new TridentTrait("will_of_the_ocean") {
        @Override protected double damage(HitContext c, boolean thrown) {
            return c.attacker().isInWater() ? pct("damage") : 0;
        }
        @Override public void collectModifiers(Player holder, Stats s) {
            if (holder.isInWater()) s.damageTaken -= pct("damageReduction");
        }
    });

    public static final Trait RAIN_DANCE = reg(new TridentTrait("rain_dance") {
        private double weather(Level level, double rain, double thunder) {
            if (level.isThundering()) return thunder;
            if (level.isRaining()) return rain;
            return 0;
        }
        @Override protected double damage(HitContext c, boolean thrown) {
            return weather(c.attacker().level(), pct("rainDamage"), pct("thunderDamage"));
        }
        @Override public void collectModifiers(Player holder, Stats s) {
            s.attackSpeed += weather(holder.level(), pct("rainAttackSpeed"), pct("thunderAttackSpeed"));
        }
    });

    public static final Trait ROYAL_IMPALER = reg(new TridentTrait("royal_impaler") {
        @Override protected double damage(HitContext c, boolean thrown) {
            return aboveHealth(c.target(), pct("healthAbove")) ? pct("damage") : -pct("penalty");
        }
    });

    public static final Trait SUNDERING = reg(new TridentTrait("sundering") {
        @Override public double armorIgnore(HitContext c) {
            return pct("armorIgnore");
        }
    });

    public static final Trait CORAL_PIERCER = reg(new TridentTrait("coral_piercer") {
        private boolean big(HitContext c) {
            return c.target().getMaxHealth() >= c.attacker().getMaxHealth() * value("healthMultiple");
        }
        @Override protected double damage(HitContext c, boolean thrown) {
            return big(c) ? pct("damage") : 0;
        }
        @Override public void collectPreAttack(Player attacker, LivingEntity target, Stats s) {
            if (target.getMaxHealth() < attacker.getMaxHealth() * value("healthMultiple")) s.critDamage -= pct("criticalDamagePenalty");
        }
    });

    public static final Trait EXECUTIONERS_PRONG = reg(new TridentTrait("executioners_prong") {
        @Override protected double damage(HitContext c, boolean thrown) {
            return lowHealth(c.target(), pct("healthBelow")) ? pct("damage") : -pct("penalty");
        }
    });

    public static final Trait RELENTLESS = reg(new TridentTrait("relentless") {
        private int stacks(Player p) {
            CompoundTag d = p.getPersistentData();
            if (d.getInt(P_RELENTLESS) > 0 && now(p) - d.getLong(P_RELENTLESS_T) > ticks("resetSeconds")) d.putInt(P_RELENTLESS, 0);
            return d.getInt(P_RELENTLESS);
        }
        @Override protected double damage(HitContext c, boolean thrown) {
            return stacks(c.attacker()) * pct("damagePerHit");
        }
        @Override protected void hit(HitContext c, float dealt, boolean thrown) {
            if (!thrown) return;
            Player p = c.attacker();
            int max = cap(value("damagePerHit"), value("maxDamage"));
            p.getPersistentData().putInt(P_RELENTLESS, Math.min(max, stacks(p) + 1));
            p.getPersistentData().putLong(P_RELENTLESS_T, now(p));
        }
    });

    public static final Trait LINEBREAKER = reg(new TridentTrait("linebreaker") {
        @Override protected void hit(HitContext c, float dealt, boolean thrown) {
            if (!thrown || dealt <= 0) return;
            LivingEntity from = c.target();
            Vec3 dir = from.position().subtract(c.attacker().position()).multiply(1, 0, 1);
            if (dir.lengthSqr() < 1.0E-6) return;
            dir = dir.normalize();
            Mob next = null;
            for (Mob m : hostilesAround(from, value("range"))) {
                Vec3 off = m.position().subtract(from.position()).multiply(1, 0, 1);
                if (off.dot(dir) <= 0) continue;
                if (next == null || m.distanceToSqr(from) < next.distanceToSqr(from)) next = m;
            }
            if (next != null) extraDamage(c.attacker(), next, (float) (dealt * pct("damage")));
        }
    });

    public static final Trait BLESSED_FROM_THE_ORIGIN_WATER = reg(new TridentTrait("blessed_origin_water") {
        private double factor(Player p) {
            if (!p.level().isRaining()) return 0;
            return p.isInWater() ? value("waterMultiplier") : 1.0;
        }
        @Override protected double damage(HitContext c, boolean thrown) {
            return factor(c.attacker()) * pct("damage");
        }
        @Override public void collectModifiers(Player holder, Stats s) {
            double f = factor(holder);
            s.attackSpeed += f * pct("attackSpeed");
            s.damageTaken -= f * pct("damageReduction");
        }
    });

    public static final Trait ABYSSAL_REAVER = reg(new TridentTrait("abyssal_reaver") {
        @Override protected double damage(HitContext c, boolean thrown) {
            if (!c.attacker().isUnderWater()) return 0;
            return isAquatic(c.target()) ? pct("aquaticDamage") : pct("damage");
        }
    });

    public static final Trait GLACIAL_UNDERTOW = reg(new TridentTrait("glacial_undertow") {
        @Override protected double damage(HitContext c, boolean thrown) {
            return c.target().hasEffect(MobEffects.MOVEMENT_SLOWDOWN) ? pct("damage") : 0;
        }
        @Override protected void hit(HitContext c, float dealt, boolean thrown) {
            if (c.attacker().getRandom().nextDouble() < pct("chance")) {
                c.target().addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks("seconds"),
                        Math.max(0, (int) Math.round(value("slownessLevel")) - 1)), c.attacker());
            }
        }
    });

    public static final Trait STORMBLOOD = reg(new TridentTrait("stormblood") {
        private int stacks(Player p) {
            CompoundTag d = p.getPersistentData();
            if (d.getInt(P_STORM) > 0 && now(p) - d.getLong(P_STORM_T) > ticks("seconds")) d.putInt(P_STORM, 0);
            return d.getInt(P_STORM);
        }
        @Override public void collectModifiers(Player holder, Stats s) {
            s.attackSpeed += stacks(holder) * pct("attackSpeedPerStack");
        }
        @Override public void onDamaged(Player holder, DamageSource source, float dealt) {
            if (dealt <= 0) return;
            int max = cap(value("attackSpeedPerStack"), value("maxAttackSpeed"));
            holder.getPersistentData().putInt(P_STORM, Math.min(max, stacks(holder) + 1));
            holder.getPersistentData().putLong(P_STORM_T, now(holder));
        }
    });

    public static final Trait AEGIS_OF_THE_DEEP = reg(new TridentTrait("aegis_of_the_deep") {
        @Override public boolean hasCooldown() {
            return true;
        }
        @Override public double flatDamageReduction(Player holder, DamageSource source) {
            return holder.isInWater() ? value("flatReduction") : 0;
        }
        @Override public void onTick(Player holder) {
            if (!holder.isInWater() || holder.tickCount % 10 != 0) return;
            ItemStack w = holder.getMainHandItem();
            long t = now(holder);
            if (!ItemState.isReady(w, id(), t)) return;
            holder.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, ticks("seconds"), 0));
            ItemState.startCooldown(w, id(), t + ticks("cooldownSeconds"));
        }
    });

    public static final Trait THORNED_TRIDENT = reg(new TridentTrait("thorned_trident") {
        @Override protected double damage(HitContext c, boolean thrown) {
            return c.attacker().getPersistentData().getBoolean(P_THORN) ? pct("damage") : 0;
        }
        @Override protected void hit(HitContext c, float dealt, boolean thrown) {
            c.attacker().getPersistentData().putBoolean(P_THORN, false);
        }
        @Override public void collectModifiers(Player holder, Stats s) {
            s.reflect += pct("reflect");
            s.knockbackRes += pct("knockbackResistance");
        }
        @Override public void onDamaged(Player holder, DamageSource source, float dealt) {
            if (dealt > 0) holder.getPersistentData().putBoolean(P_THORN, true);
        }
    });

    public static final Trait UNYIELDING_TIDE = reg(new TridentTrait("unyielding_tide") {
        @Override public void collectModifiers(Player holder, Stats s) {
            if (now(holder) < holder.getPersistentData().getLong(P_TIDE_UNTIL)) s.damageTaken -= pct("damageReduction");
        }
        @Override public void onKill(Player holder, LivingEntity victim) {
            if (victim instanceof Enemy) holder.getPersistentData().putLong(P_TIDE_UNTIL, now(holder) + ticks("seconds"));
        }
    });

    public static final Trait LIGHTNING_JUDGEMENT = reg(new TridentTrait("lightning_judgement") {
        @Override protected void hit(HitContext c, float dealt, boolean thrown) {
            Player p = c.attacker();
            CompoundTag d = p.getPersistentData();
            int n = d.getInt(P_LIGHTNING) + 1;
            if (n < Math.max(1, (int) Math.round(value("hits")))) {
                d.putInt(P_LIGHTNING, n);
                return;
            }
            d.putInt(P_LIGHTNING, 0);
            LivingEntity center = c.target();
            if (!(center.level() instanceof ServerLevel level) || dealt <= 0) return;
            List<LivingEntity> victims = new ArrayList<>();
            if (center.isAlive()) victims.add(center);
            victims.addAll(hostilesAround(center, value("range")));
            int strikes = Math.max(1, (int) Math.round(value("strikes")));
            for (int i = 0; i < strikes; i++) {
                LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
                if (bolt != null) {
                    bolt.moveTo(center.getX(), center.getY(), center.getZ());
                    bolt.setVisualOnly(true);
                    level.addFreshEntity(bolt);
                }
                for (LivingEntity v : victims) extraDamage(p, v, (float) (dealt * pct("damage")), true);
            }
        }
    });

    public static final Trait OCEAN_SPLITTER = reg(new TridentTrait("ocean_splitter") {
        @Override protected double damage(HitContext c, boolean thrown) {
            if (!thrown || c.projectile() == null) return 0;
            CompoundTag d = c.projectile().getPersistentData();
            double seconds = d.getInt(TRIDENT_CHARGE) / 20.0;
            int whole = (int) Math.floor(seconds - value("seconds") + 1.0E-9);
            if (whole <= 0) return 0;
            double bonus = whole * pct("damagePerSecond");
            return d.getBoolean(TRIDENT_WATER) ? bonus * value("waterMultiplier") : bonus;
        }
    });

    public static final Trait BEASTIAL = reg(new TridentTrait("beastial") {
        @Override public void collectModifiers(Player holder, Stats s) {
            s.critChance += pct("criticalChance");
            s.critDamage -= pct("criticalDamagePenalty");
        }
    });

    // ------------------------------------------------------------------ situational

    public static final Trait DOOM_SLAYER = reg(new Trait("doom_slayer") {
        @Override public double damageDealtBonus(HitContext c) {
            return c.attacker().level().dimension() == Level.NETHER ? pct("netherDamage") : -pct("otherwiseDamage");
        }
    });

    public static final Trait LIGHT_ABSORB = reg(new Trait("light_absorb") {
        @Override public double damageDealtBonus(HitContext c) {
            Player a = c.attacker();
            Level l = a.level();
            if (l.dimensionType().hasFixedTime() || l.dimensionType().hasCeiling()) return 0;
            boolean day = l.getDayTime() % 24000L < 12000L;
            return day && !l.isRaining() && l.canSeeSky(a.blockPosition()) ? pct("damage") : 0;
        }
    });

    public static final Trait MAXIMUM_MIGHT = reg(new Trait("maximum_might") {
        @Override public double damageDealtBonus(HitContext c) {
            Player p = c.attacker();
            return p.getHealth() >= p.getMaxHealth() ? pct("damage") : 0;
        }
    });

    public static final Trait TITANS_VIGOR = reg(new Trait("titans_vigor") {
        @Override public double damageDealtBonus(HitContext c) {
            return c.attacker().getMaxHealth() * pct("damagePerHealth");
        }
    });

    /** The damage reduction of mobs is applied in CommonEvents via TraitUtil.menacingReduction. */
    public static final Trait MENACING = reg(new Trait("menacing") { });

    public static final Trait DUELIST = reg(new Trait("duelist") {
        @Override public double damageDealtBonus(HitContext c) {
            int n = countHostiles(c.attacker(), value("range"));
            return (n == 1 && c.target() instanceof Enemy) ? pct("damage") : -pct("penalty");
        }
    });

    public static final Trait PEAK_PERFORMANCE = reg(new Trait("peak_performance") {
        @Override public double damageDealtBonus(HitContext c) {
            CompoundTag d = c.attacker().getPersistentData();
            if (!d.contains(LAST_HURT)) return pct("damage");
            return now(c.attacker()) - d.getLong(LAST_HURT) >= ticks("seconds") ? pct("damage") : 0;
        }
    });

    public static final Trait MOUNT_MASTER = reg(new Trait("mount_master") {
        @Override public double damageDealtBonus(HitContext c) {
            return c.attacker().isPassenger() ? pct("damage") : 0;
        }
        @Override public void collectModifiers(Player holder, Stats s) {
            if (holder.isPassenger()) s.damageTaken -= pct("damageReduction");
        }
    });

    public static final Trait IMMOVABLE_OBJECT = reg(new Trait("immovable_object") {
        @Override public Kind kind() {
            return Kind.SHIELD;
        }
        @Override public void collectModifiers(Player holder, Stats s) {
            if (isStill(holder)) {
                s.damageTaken -= pct("damageReduction");
                s.knockbackRes += pct("knockbackResistance");
            }
        }
    });

    public static final Trait STEADFAST = reg(new Trait("steadfast") {
        @Override public void collectModifiers(Player holder, Stats s) {
            if (holder.isShiftKeyDown()) {
                s.attackSpeed += pct("attackSpeed");
                s.knockbackRes += pct("knockbackResistance");
            }
        }
    });

    public static final Trait WELL_FED = reg(new Trait("well_fed") {
        @Override public void collectModifiers(Player holder, Stats s) {
            if (holder.getFoodData().getFoodLevel() >= 20) {
                s.critChance += pct("criticalChance");
                s.critDamage += pct("criticalDamage");
            }
        }
    });

    public static final Trait MASOCHIST = reg(new Trait("masochist") {
        @Override public void collectModifiers(Player holder, Stats s) {
            int debuffs = Math.min((int) Math.round(value("maxDebuffs")), harmfulEffects(holder));
            s.damageTaken -= pct("damageReductionPerDebuff") * debuffs;
        }
    });

    public static final Trait LONE_WOLF = reg(new Trait("lone_wolf") {
        private boolean alone(Player p) {
            return isLonely(p, value("range"));
        }
        @Override public double damageDealtBonus(HitContext c) {
            return alone(c.attacker()) ? pct("damage") : -pct("penalty");
        }
        @Override public double healingReceivedBonus(Player holder) {
            return alone(holder) ? pct("healing") : -pct("penalty");
        }
    });

    public static final Trait HELM_BREAKER = reg(new Trait("helm_breaker") {
        @Override public void collectModifiers(Player holder, Stats s) {
            s.armorPiercing += value("armorPiercing");
        }
    });

    // ------------------------------------------------------------------ health based / random / collectors

    public static final Trait VANQUISHED_SOUL = reg(new Trait("vanquished_soul") {
        @Override public void collectPreAttack(Player a, LivingEntity t, Stats s) {
            if (t.getHealth() < t.getMaxHealth() * pct("healthBelow")) s.critChance += pct("criticalChance");
        }
    });

    public static final Trait GIANT_SLAYER = reg(new Trait("giant_slayer") {
        @Override public double damageDealtBonus(HitContext c) {
            float t = c.target().getMaxHealth();
            float m = c.attacker().getMaxHealth();
            return t < m ? -pct("penalty") : (t > m ? pct("bonus") : 0);
        }
    });

    public static final Trait OVERWHELMING = reg(new Trait("overwhelming") {
        @Override public double damageDealtBonus(HitContext c) {
            float t = c.target().getMaxHealth();
            float m = c.attacker().getMaxHealth();
            return t > m ? -pct("penalty") : (t < m ? pct("bonus") : 0);
        }
    });

    public static final Trait GRAND_STRIKE = reg(new Trait("grand_strike") {
        @Override public double damageDealtBonus(HitContext c) {
            LivingEntity t = c.target();
            return t.getHealth() >= t.getMaxHealth() ? pct("damage") : 0;
        }
    });

    public static final Trait UNSTABLE_BLADE = reg(new Trait("unstable_blade") {
        @Override public double damageDealtBonus(HitContext c) {
            double min = pct("minPercent");
            double max = Math.max(min, pct("maxPercent"));
            return min + c.attacker().getRandom().nextDouble() * (max - min) - 1.0;
        }
    });

    public static final Trait EMERALD_OBSESSION = reg(new Trait("emerald_obsession") {
        private int emeralds(Player p) {
            Inventory inv = p.getInventory();
            int n = 0;
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack s = inv.getItem(i);
                if (s.is(Items.EMERALD)) n += s.getCount();
            }
            return n;
        }
        private double bonus(int emeralds) {
            return Math.min(pct("maxDamage"), emeralds * pct("damagePerEmerald"));
        }
        @Override public double damageDealtBonus(HitContext c) {
            return bonus(emeralds(c.attacker()));
        }
        @Override public Component progress(ItemStack w, Player viewer, long gameTime) {
            if (viewer == null) return null;
            int n = emeralds(viewer);
            return Component.literal(String.format(Locale.ROOT, "Emeralds: %d (+%.2f%% damage)", n, bonus(n) * 100));
        }
    });

    /** Weapon-bound kill counter, grants damage up to the configured maximum. */
    public static final Trait SATSUJINKI = reg(new Trait("satsujinki", Tier.UNIQUE) {
        private int need() {
            return Math.max(1, (int) Math.round(value("maxDamage") / Math.max(0.0001, value("damagePerKill"))));
        }
        private double bonus(int kills) {
            return Math.min(pct("maxDamage"), kills * pct("damagePerKill"));
        }
        @Override public double damageDealtBonus(HitContext c) {
            return bonus(ItemState.getInt(c.weapon(), ItemState.KILLS));
        }
        @Override public void onKill(Player p, LivingEntity victim) {
            ItemStack w = p.getMainHandItem();
            int kills = Math.min(need(), ItemState.getInt(w, ItemState.KILLS) + 1);
            ItemState.update(w, t -> t.putInt(ItemState.KILLS, kills));
            // a dagger with all its kills turns into the hidden Mystic Eye of Death Perception (from the add-on)
            if (kills >= need() && w.is(TraitUtil.DAGGER) && get("mystic_eye") != null) {
                TraitUtil.replaceTrait(w, id(), "mystic_eye");
            }
        }
        private boolean complete(ItemStack w) {
            return ItemState.getInt(w, ItemState.KILLS) >= need();
        }
        /** Name and progress turn red once the weapon has all its kills. */
        @Override public ChatFormatting nameColor(ItemStack w) {
            return complete(w) ? ChatFormatting.RED : null;
        }
        @Override public ChatFormatting progressColor(ItemStack w) {
            return complete(w) ? ChatFormatting.RED : ChatFormatting.AQUA;
        }
        @Override public Component progress(ItemStack w, Player viewer, long gameTime) {
            int kills = Math.min(need(), ItemState.getInt(w, ItemState.KILLS));
            return Component.literal(String.format(Locale.ROOT, "Kills: %d/%d (+%.2f%% damage)", kills, need(), bonus(kills) * 100));
        }
    });

    /** Timed kill stacks stored on the weapon. */
    public static final Trait MASSACRED = reg(new Trait("massacred") {
        private int max() {
            return cap(value("damagePerStack"), value("maxDamage"));
        }
        private int stacks(ItemStack w, long now) {
            if (now - ItemState.getLong(w, ItemState.MS_TIME) > ticks("seconds")) return 0;
            return ItemState.getInt(w, ItemState.MS_STACKS);
        }
        @Override public double damageDealtBonus(HitContext c) {
            return stacks(c.weapon(), now(c.attacker())) * pct("damagePerStack");
        }
        @Override public void onKill(Player p, LivingEntity victim) {
            ItemStack w = p.getMainHandItem();
            long t = now(p);
            int next = Math.min(max(), stacks(w, t) + 1);
            ItemState.update(w, tag -> {
                tag.putInt(ItemState.MS_STACKS, next);
                tag.putLong(ItemState.MS_TIME, t);
            });
        }
    });

    // ------------------------------------------------------------------ base stats

    public static final Trait CRITICAL_EYES = reg(new Trait("critical_eyes") {
        @Override public Kind kind() {
            return Kind.WEAPON;
        }
        @Override public void collectModifiers(Player holder, Stats s) {
            s.critChance += pct("criticalChance");
        }
    });

    public static final Trait NIGHT_EDGE = reg(new Trait("night_edge") {
        @Override public void collectModifiers(Player holder, Stats s) {
            if (isNight(holder.level())) s.critDamage += pct("criticalDamage");
        }
    });

    public static final Trait SKY_STRIKER = reg(new Trait("sky_striker") {
        @Override public Kind kind() {
            return Kind.WEAPON;
        }
        @Override public double damageDealtBonus(HitContext c) {
            return isFlyingTarget(c.target()) ? pct("damage") : 0;
        }
        @Override public double rangedDamageBonus(HitContext c) {
            return damageDealtBonus(c);
        }
    });

    public static final Trait TEMPERED = reg(new Trait("tempered") {
        @Override public void collectModifiers(Player holder, Stats s) {
            s.attackDamage += value("attackDamage");
        }
        @Override public double damageDealtBonus(HitContext c) {
            return pct("damage");
        }
    });

    public static final Trait LIGHT_WEIGHT = reg(new Trait("light_weight") {
        @Override public void collectModifiers(Player holder, Stats s) {
            s.attackSpeed += pct("attackSpeed");
        }
    });

    public static final Trait GLASS_CANNON = reg(new Trait("glass_cannon") {
        @Override public double damageDealtBonus(HitContext c) {
            return pct("damage");
        }
        @Override public void collectModifiers(Player holder, Stats s) {
            s.damageTaken += pct("damageTaken");
        }
    });

    public static final Trait BERSERKER_SOUL = reg(new Trait("berserker_soul") {
        private boolean low(Player p) {
            return p.getHealth() < p.getMaxHealth() * pct("healthBelow");
        }
        @Override public double damageDealtBonus(HitContext c) {
            // Rage replaces the plain damage bonus once a rage attribute is configured
            return low(c.attacker()) && !AttributeBridge.rageConfigured() ? pct("rage") : 0;
        }
        @Override public void collectModifiers(Player holder, Stats s) {
            if (low(holder)) {
                s.damageTaken -= pct("damageReduction");
                s.rage += pct("rage");
            }
        }
    });

    // ------------------------------------------------------------------ status / healing

    public static final Trait VAMPIRISM = reg(new Trait("vampirism") {
        @Override public void collectModifiers(Player holder, Stats s) {
            s.lifesteal += pct("lifesteal");
        }
        @Override public void onHit(HitContext c, float dealt) {
            // Internal lifesteal (when the attribute is missing, or the mode is INTERNAL/BOTH)
            if (AttributeBridge.lifestealInternal() && dealt > 0) c.attacker().heal(dealt * (float) pct("lifesteal"));
        }
    });

    public static final Trait WITHER_HAND = reg(new Trait("wither_hand") {
        @Override public void collectPreAttack(Player a, LivingEntity t, Stats s) {
            if (t.hasEffect(MobEffects.WITHER)) s.lifesteal += pct("lifesteal");
        }
        @Override public void onHit(HitContext c, float dealt) {
            LivingEntity t = c.target();
            if (AttributeBridge.lifestealInternal() && dealt > 0 && t.hasEffect(MobEffects.WITHER)) {
                c.attacker().heal(dealt * (float) pct("lifesteal"));
            }
            t.addEffect(new MobEffectInstance(MobEffects.WITHER, ticks("witherSeconds"), 0), c.attacker());
        }
    });

    public static final Trait VENOMOUS_EDGE = reg(new Trait("venomous_edge") {
        @Override public double damageDealtBonus(HitContext c) {
            MobEffectInstance poison = c.target().getEffect(MobEffects.POISON);
            return poison == null ? 0 : (poison.getAmplifier() + 1) * pct("damagePerPoisonLevel");
        }
    });

    public static final Trait BLOODTHIRST = reg(new Trait("bloodthirst") {
        @Override public void onKill(Player p, LivingEntity victim) {
            p.heal((float) value("heal"));
        }
    });

    // ------------------------------------------------------------------ on kill

    public static final Trait CORPSE_EATER = reg(new Trait("corpse_eater") {
        @Override public void onKill(Player p, LivingEntity victim) {
            if (p.getRandom().nextDouble() < pct("chance")) {
                p.getFoodData().eat((int) Math.round(value("hunger")), 0.0f);
            }
        }
    });

    public static final Trait SCAVENGER = reg(new Trait("scavenger") {
        @Override public Kind kind() {
            return Kind.WEAPON;
        }
        private final EquipmentSlot[] armor = {
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };
        @Override public void onKill(Player p, LivingEntity victim) {
            int amount = (int) Math.round(value("durability"));
            for (EquipmentSlot slot : armor) {
                ItemStack s = p.getItemBySlot(slot);
                if (!s.isEmpty() && s.isDamaged()) s.setDamageValue(Math.max(0, s.getDamageValue() - amount));
            }
        }
    });

    public static final Trait WICKED_RUNE = reg(new Trait("wicked_rune") {
        private int protectionLevels(Player p) {
            int total = 0;
            for (EquipmentSlot slot : new EquipmentSlot[]{
                    EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                ItemStack s = p.getItemBySlot(slot);
                total += enchantLevel(p.level(), s, Enchantments.BLAST_PROTECTION);
                total += enchantLevel(p.level(), s, Enchantments.FIRE_PROTECTION);
                total += enchantLevel(p.level(), s, Enchantments.PROJECTILE_PROTECTION);
            }
            return total;
        }
        private boolean isPetOrVillager(LivingEntity e) {
            return e instanceof AbstractVillager
                    || (e instanceof TamableAnimal t && t.isTame())
                    || (e instanceof AbstractHorse h && h.isTamed());
        }
        @Override public Component progress(ItemStack w, Player viewer, long gameTime) {
            if (viewer == null) return null;
            int levels = protectionLevels(viewer);
            double pctDamage = (pct("damagePercent") + levels * pct("percentPerProtectionLevel")) * 100;
            double radius = value("range") + Math.floor(levels / Math.max(1.0, value("levelsPerRange")));
            return Component.literal("Protection levels: " + levels + " (" + num(pctDamage) + "% max HP, " + num(radius) + " blocks)");
        }
        @Override public void onKill(Player p, LivingEntity victim) {
            if (!(p.level() instanceof ServerLevel level)) return;
            int levels = protectionLevels(p);
            double fraction = pct("damagePercent") + levels * pct("percentPerProtectionLevel");
            float mobDamage = (float) (victim.getMaxHealth() * fraction);
            float fixed = (float) value("fixedDamage");
            double perRange = Math.max(1.0, value("levelsPerRange"));
            double r = value("range") + Math.floor(levels / perRange);
            // magic damage has no attacker, so rune kills never chain into more rune explosions
            DamageSource src = level.damageSources().magic();
            AABB box = victim.getBoundingBox().inflate(r);
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box,
                    e -> e != victim && e.isAlive() && e.distanceToSqr(victim) <= r * r)) {
                if (e instanceof Player pl) {
                    if (pl == p && value("hurtOwner") < 0.5) continue;
                    if (pl.isCreative() || pl.isSpectator()) continue;
                    pl.hurt(src, fixed);
                } else if (isPetOrVillager(e)) {
                    e.hurt(src, fixed);
                } else if (e instanceof Mob) {
                    e.hurt(src, mobDamage);
                }
            }
        }
    });

    public static final Trait ILLUMINATING = reg(new Trait("illuminating") {
        @Override public double damageDealtBonus(HitContext c) {
            int total = 0;
            for (var e : c.weapon().getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).entrySet()) {
                total += e.getIntValue();
            }
            return total * pct("damagePerLevel");
        }
        @Override public Component progress(ItemStack w, Player viewer, long gameTime) {
            int total = 0;
            for (var e : w.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).entrySet()) total += e.getIntValue();
            return Component.literal("Enchant levels: " + total + " (+" + num(total * value("damagePerLevel")) + "% damage)");
        }
    });

    public static final Trait CURSE_EXTRACT = reg(new Trait("curse_extract") {
        private int curses(ItemStack s) {
            int n = 0;
            for (var e : s.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).entrySet()) {
                if (e.getKey().is(EnchantmentTags.CURSE)) n++;
            }
            return n;
        }
        @Override public double damageDealtBonus(HitContext c) {
            int n = curses(c.weapon());
            if (value("includeArmor") >= 0.5) {
                for (EquipmentSlot slot : new EquipmentSlot[]{
                        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                    n += curses(c.attacker().getItemBySlot(slot));
                }
            }
            return n * pct("damage");
        }
        @Override public Component progress(ItemStack w, Player viewer, long gameTime) {
            if (viewer == null) return null;
            int n = curses(w);
            if (value("includeArmor") >= 0.5) {
                for (EquipmentSlot slot : new EquipmentSlot[]{
                        EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                    n += curses(viewer.getItemBySlot(slot));
                }
            }
            return Component.literal("Curses: " + n + " (+" + num(n * value("damage")) + "% damage)");
        }
    });

    public static final Trait MIDAS_TOUCH = reg(new Trait("midas_touch") {
        @Override public void onKill(Player p, LivingEntity victim) {
            int looting = enchantLevel(p.level(), p.getMainHandItem(), Enchantments.LOOTING);
            double chance = pct("chance") + looting * pct("chancePerLooting");
            if (p.getRandom().nextDouble() >= chance) return;
            victim.getPersistentData().putBoolean(MIDAS, true);
            int min = (int) Math.round(value("goldMin"));
            int max = Math.max(min, (int) Math.round(value("goldMax")));
            int n = min + p.getRandom().nextInt(max - min + 1);
            if (n > 0) victim.spawnAtLocation(new ItemStack(Items.GOLD_INGOT, n));
            p.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, ticks("absorptionSeconds"), 0));
        }
        @Override public double expBonus(Player holder, LivingEntity victim) {
            return victim.getPersistentData().getBoolean(MIDAS) ? 1.0 : 0;
        }
        @Override public Component progress(ItemStack w, Player viewer, long gameTime) {
            if (viewer == null) return null;
            int looting = enchantLevel(viewer.level(), w, Enchantments.LOOTING);
            return Component.literal("Looting " + looting + ": " + num(value("chance") + looting * value("chancePerLooting")) + "% chance");
        }
    });

    public static final Trait SOLO_LEVELING = reg(new Trait("solo_leveling") {
        @Override public double expBonus(Player holder, LivingEntity victim) {
            return noOtherPlayersNearby(holder, value("range")) ? pct("experience") : 0;
        }
    });

    // ------------------------------------------------------------------ Hell Forged

    public static final Trait HELL_FORGED = reg(new Trait("hell_forged") {
        @Override public boolean negatesDamage(Player holder, DamageSource source) {
            return source.is(DamageTypeTags.IS_FIRE);
        }
        @Override public void collectModifiers(Player holder, Stats s) {
            if (holder.level().dimension() == Level.NETHER) s.critDamage += pct("netherCriticalDamage");
        }
        @Override public void onTick(Player p) {
            if (p.isOnFire()) p.clearFire();
            if (p.tickCount % 10 == 0) {
                p.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 40, 0, false, false, true));
            }
        }
        @Override public void onHit(HitContext c, float dealt) {
            LivingEntity t = c.target();
            t.setRemainingFireTicks(Math.max(t.getRemainingFireTicks(), ticks("fireSeconds")));
        }
    });

    // ------------------------------------------------------------------ utility / cooldowns

    public static final Trait RAMPAGER = reg(new Trait("rampager") {
        @Override public void onHit(HitContext c, float dealt) {
            Player p = c.attacker();
            CompoundTag d = p.getPersistentData();
            int max = cap(value("attackSpeedPerHit"), value("maxAttackSpeed"));
            d.putInt(RAMPAGE, Math.min(max, d.getInt(RAMPAGE) + 1));
            d.putLong(RAMPAGE_LAST, now(p));
        }
        @Override public void collectModifiers(Player holder, Stats s) {
            s.attackSpeed += holder.getPersistentData().getInt(RAMPAGE) * pct("attackSpeedPerHit");
        }
    });

    public static final Trait TEMPEST = reg(new Trait("tempest") {
        @Override public double damageTakenBonus(Player holder, DamageSource source) {
            return source.is(DamageTypes.LIGHTNING_BOLT) ? pct("lightningDamageTaken") : 0;
        }
        @Override public void onStruckByLightning(Player p, LightningBolt bolt) {
            int dur = ticks("seconds");
            p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, dur, 0));
            p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, dur, 0));
            p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, dur, 0));
            p.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, dur, 0));
        }
    });

    private static final List<Holder<MobEffect>> PHIAL_BLADE_GOOD = List.of(
            MobEffects.DAMAGE_RESISTANCE, MobEffects.DAMAGE_BOOST, MobEffects.DIG_SPEED,
            MobEffects.ABSORPTION, MobEffects.NIGHT_VISION, MobEffects.REGENERATION);
    private static final List<Holder<MobEffect>> PHIAL_BLADE_BAD = List.of(
            MobEffects.POISON, MobEffects.WITHER, MobEffects.WEAKNESS, MobEffects.MOVEMENT_SLOWDOWN);

    private static final String[] PHIAL_BLADE_GOOD_NAMES =
            {"Resistance", "Strength", "Haste", "Absorption", "Night Vision", "Regeneration"};
    private static final String[] PHIAL_BLADE_BAD_NAMES = {"Poison", "Wither", "Weakness", "Slowness"};

    public static final Trait PHIAL_BLADE = reg(new Trait("phial_blade") {
        @Override public boolean hasCooldown() {
            return true;
        }
        @Override public void onHit(HitContext c, float dealt) {
            Player p = c.attacker();
            ItemStack w = c.weapon();
            long t = now(p);
            if (!ItemState.isReady(w, id(), t)) return;
            int sweep = enchantLevel(p.level(), w, Enchantments.SWEEPING_EDGE);
            if (p.getRandom().nextDouble() >= pct("chance") + sweep * pct("chancePerSweeping")) return;

            // each Sweeping Edge level removes one debuff from the possible results
            List<Holder<MobEffect>> pool = new ArrayList<>(PHIAL_BLADE_GOOD);
            pool.addAll(PHIAL_BLADE_BAD.subList(Math.min(sweep, PHIAL_BLADE_BAD.size()), PHIAL_BLADE_BAD.size()));
            Holder<MobEffect> effect = pool.get(p.getRandom().nextInt(pool.size()));
            p.addEffect(new MobEffectInstance(effect, ticks("seconds") + sweep * ticks("durationPerSweeping"), 0));

            if (sweep >= value("shareLevel") && PHIAL_BLADE_GOOD.contains(effect)) {
                double r = value("shareRange");
                for (Player o : p.level().players()) {
                    if (o == p || o.isSpectator() || o.distanceToSqr(p) > r * r) continue;
                    o.addEffect(new MobEffectInstance(effect, ticks("shareSeconds"), 0));
                }
            }
            ItemState.startCooldown(w, id(), t + ticks("cooldownSeconds"));
        }
        @Override public Component progress(ItemStack w, Player viewer, long gameTime) {
            if (viewer == null) return null;
            int sweep = enchantLevel(viewer.level(), w, Enchantments.SWEEPING_EDGE);
            StringBuilder names = new StringBuilder();
            for (String n : PHIAL_BLADE_GOOD_NAMES) names.append(names.length() > 0 ? ", " : "").append(n);
            for (int i = Math.min(sweep, PHIAL_BLADE_BAD_NAMES.length); i < PHIAL_BLADE_BAD_NAMES.length; i++) {
                names.append(", ").append(PHIAL_BLADE_BAD_NAMES[i]);
            }
            return Component.literal("Phial Capacity: Sweeping Edge " + sweep + " (" + num(value("chance") + sweep * value("chancePerSweeping"))
                    + "% chance, " + num(value("seconds") + sweep * value("durationPerSweeping")) + "s effect)\nEffects: " + names);
        }
    });

    private static final String CHANT_PHRASE =
            normalize("Lord of Light, bless this blade and smite the wicked");

    private static final String CHANT_END = "ut_chant_end";

    /** Holy Chant: the Strength buff is lost when the weapon is no longer held. Called every tick for every player. */
    public static void chantTick(Player p) {
        CompoundTag d = p.getPersistentData();
        if (!d.contains(CHANT_END)) return;
        long end = d.getLong(CHANT_END);
        long t = now(p);
        if (t >= end) {
            d.remove(CHANT_END);
            return;
        }
        if (!holds(p, HOLY_CHANT)) {
            MobEffectInstance e = p.getEffect(MobEffects.DAMAGE_BOOST);
            if (e != null && e.getAmplifier() == 0 && e.getDuration() <= end - t + 20) p.removeEffect(MobEffects.DAMAGE_BOOST);
            d.remove(CHANT_END);
        }
    }

    public static final Trait HOLY_CHANT = reg(new Trait("holy_chant") {
        @Override public boolean hasCooldown() {
            return true;
        }
        @Override public void onChat(Player p, String message) {
            if (!normalize(message).equals(CHANT_PHRASE)) return;
            ItemStack w = p.getMainHandItem();
            long t = now(p);
            if (!ItemState.isReady(w, id(), t)) {
                long left = ItemState.cooldownEnd(w, id()) - t;
                p.displayClientMessage(Component.literal("Holy Chant CD: " + ItemState.formatTicks(left)), true);
                return;
            }
            int smite = enchantLevel(p.level(), w, Enchantments.SMITE);
            long cd = Math.max(0, ticks("cooldownSeconds") - (long) smite * ticks("cooldownReductionPerSmite"));
            p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, ticks("strengthSeconds"), 0));
            p.getPersistentData().putLong(CHANT_END, t + ticks("strengthSeconds"));
            ItemState.startCooldown(w, id(), t + cd);
        }
        @Override public Component progress(ItemStack w, Player viewer, long gameTime) {
            if (viewer == null) return null;
            int smite = enchantLevel(viewer.level(), w, Enchantments.SMITE);
            long cd = Math.max(0, ticks("cooldownSeconds") - (long) smite * ticks("cooldownReductionPerSmite"));
            return Component.literal("Smite " + smite + ": CD " + ItemState.formatTicks(cd));
        }
    });

    public static final Trait ECHOING_BLADES = reg(new Trait("echoing_blades") {
        @Override public double damageDealtBonus(HitContext c) {
            return -pct("damagePenalty");
        }
        @Override public void onHit(HitContext c, float dealt) {
            if (dealt > 0 && c.attacker().getRandom().nextDouble() < pct("chance")) {
                extraDamage(c.attacker(), c.target(), dealt * (float) pct("extraDamagePercent"));
            }
        }
    });

    public static final Trait RUINOUS_CLEAVE = reg(new Trait("ruinous_cleave") {
        @Override public double damageDealtBonus(HitContext c) {
            return c.attacker().getRandom().nextDouble() < pct("chance") ? value("multiplier") - 1.0 : 0;
        }
        @Override public void collectModifiers(Player holder, Stats s) {
            s.critDamage -= pct("criticalDamage");
        }
    });

    private static String biomePath(Entity e) {
        return e.level().getBiome(e.blockPosition()).unwrapKey().map(k -> k.location().getPath()).orElse("");
    }

    private static boolean inDesert(Entity e) {
        return biomePath(e).contains("desert");
    }

    private static boolean inSnow(Entity e) {
        String path = biomePath(e);
        return path.contains("snow") || path.contains("frozen") || path.contains("ice")
                || e.level().getBiome(e.blockPosition()).value().coldEnoughToSnow(e.blockPosition());
    }

    public static final Trait ETHEREAL_STEEL = reg(new Trait("ethereal_steel") {
        private double chance(Level level, ItemStack w) {
            int levels = enchantLevel(level, w, Enchantments.SHARPNESS)
                    + enchantLevel(level, w, Enchantments.SMITE)
                    + enchantLevel(level, w, Enchantments.BANE_OF_ARTHROPODS);
            return Math.max(0, pct("chance") - levels * pct("chanceLossPerLevel"));
        }
        @Override public boolean ignoresArmor(HitContext c) {
            return c.attacker().getRandom().nextDouble() < chance(c.attacker().level(), c.weapon());
        }
        @Override public double damageDealtBonus(HitContext c) {
            return -pct("damagePenalty");
        }
        @Override public void collectModifiers(Player holder, Stats s) {
            s.critChance -= pct("criticalChance");
            s.critDamage -= pct("criticalDamage");
        }
        @Override public Component progress(ItemStack w, Player viewer, long gameTime) {
            if (viewer == null) return null;
            return Component.literal("Armor ignore chance: " + num(chance(viewer.level(), w) * 100) + "%");
        }
    });

    public static final Trait SUNSCORCHED = reg(new Trait("sunscorched") {
        @Override public double damageDealtBonus(HitContext c) {
            return inDesert(c.attacker()) ? pct("damage") : 0;
        }
    });

    public static final Trait DUNE_STALKER = reg(new Trait("dune_stalker") {
        @Override public void collectModifiers(Player holder, Stats s) {
            if (inDesert(holder)) {
                s.moveSpeed += pct("movementSpeed");
                s.attackSpeed += pct("attackSpeed");
            }
        }
    });

    public static final Trait FROSTBORN = reg(new Trait("frostborn") {
        @Override public boolean negatesDamage(Player holder, DamageSource source) {
            return source.is(DamageTypes.FREEZE);
        }
        @Override public void onTick(Player p) {
            if (p.getTicksFrozen() > 0) p.setTicksFrozen(0);
        }
        @Override public void onHit(HitContext c, float dealt) {
            if (c.attacker().getRandom().nextDouble() < pct("chance")) {
                c.target().addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks("seconds"), 0), c.attacker());
            }
        }
    });

    public static final Trait GLACIAL_FANG = reg(new Trait("glacial_fang") {
        @Override public double damageDealtBonus(HitContext c) {
            LivingEntity t = c.target();
            return inSnow(c.attacker()) && t.getHealth() > t.getMaxHealth() * pct("healthAbove") ? pct("damage") : 0;
        }
        @Override public void onHit(HitContext c, float dealt) {
            int extra = (int) Math.round(value("extraDurability"));
            if (extra > 0) c.weapon().hurtAndBreak(extra, c.attacker(), EquipmentSlot.MAINHAND);
        }
    });

    public static final Trait MOONLIT_EDGE = reg(new Trait("moonlit_edge") {
        @Override public double damageDealtBonus(HitContext c) {
            Level l = c.attacker().level();
            return isNight(l) && l.getMoonPhase() == 0 ? pct("damage") : 0;
        }
    });

    public static final Trait COUNTERSTRIKE = reg(new Trait("counterstrike") {
        @Override public Kind kind() {
            return Kind.SHIELD;
        }
        private boolean window(Player p) {
            CompoundTag d = p.getPersistentData();
            return d.contains(COUNTER) && now(p) - d.getLong(COUNTER) <= ticks("seconds");
        }
        @Override public double damageDealtBonus(HitContext c) {
            return window(c.attacker()) ? pct("damage") : 0;
        }
        @Override public void onHit(HitContext c, float dealt) {
            c.attacker().getPersistentData().remove(COUNTER);
        }
    });

    public static final Trait MOMENTUM = reg(new Trait("momentum") {
        private static final String START = "ut_mom_start";
        private static final String LAST = "ut_mom_tick";
        @Override public void onTick(Player p) {
            CompoundTag d = p.getPersistentData();
            long t = now(p);
            if (p.isSprinting()) {
                if (!d.contains(START) || t - d.getLong(LAST) > 4) d.putLong(START, t);
                d.putLong(LAST, t);
            } else {
                d.remove(START);
            }
        }
        @Override public double damageDealtBonus(HitContext c) {
            Player p = c.attacker();
            CompoundTag d = p.getPersistentData();
            if (!p.isSprinting() || !d.contains(START)) return 0;
            double seconds = (now(p) - d.getLong(START)) / 20.0;
            return Math.min(pct("maxDamage"), seconds * pct("damagePerSecond"));
        }
        @Override public void onHit(HitContext c, float dealt) {
            Player p = c.attacker();
            if (p.isSprinting()) p.getPersistentData().putLong(START, now(p));
        }
    });

    private static final ResourceLocation BANNER_MOVE = ResourceLocation.fromNamespaceAndPath("mastertrait", "banner_move");
    private static final ResourceLocation BANNER_ATTACK = ResourceLocation.fromNamespaceAndPath("mastertrait", "banner_attack");
    private static final String BANNER_END = "ut_banner_end";

    public static final Trait BANNER_BEARER = reg(new Trait("banner_bearer") {
        @Override public Kind kind() {
            return Kind.WEAPON;
        }
        @Override public void onTick(Player p) {
            if (p.tickCount % 10 != 0) return;
            double r = value("range");
            for (Player o : p.level().players()) {
                if (o == p || o.isSpectator() || o.distanceToSqr(p) > r * r) continue;
                AttributeInstance move = o.getAttribute(Attributes.MOVEMENT_SPEED);
                if (move != null) move.addOrUpdateTransientModifier(new AttributeModifier(
                        BANNER_MOVE, pct("movementSpeed"), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
                AttributeInstance atk = o.getAttribute(Attributes.ATTACK_SPEED);
                if (atk != null) atk.addOrUpdateTransientModifier(new AttributeModifier(
                        BANNER_ATTACK, pct("attackSpeed"), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
                o.getPersistentData().putLong(BANNER_END, now(o) + 30);
            }
        }
    });

    /** Removes the Banner Bearer bonuses once the bearer is out of range. Called every 10 ticks for every player. */
    public static void bannerTick(Player p) {
        CompoundTag d = p.getPersistentData();
        if (!d.contains(BANNER_END) || now(p) <= d.getLong(BANNER_END)) return;
        AttributeInstance move = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (move != null) move.removeModifier(BANNER_MOVE);
        AttributeInstance atk = p.getAttribute(Attributes.ATTACK_SPEED);
        if (atk != null) atk.removeModifier(BANNER_ATTACK);
        d.remove(BANNER_END);
    }

    public static final Trait BOUNTY_HUNTER = reg(new Trait("bounty_hunter") {
        @Override public Kind kind() {
            return Kind.WEAPON;
        }
        private boolean qualifies(LivingEntity victim) {
            return victim.getMaxHealth() >= value("minMaxHealth");
        }
        @Override public double expBonus(Player holder, LivingEntity victim) {
            return qualifies(victim) ? value("expMultiplier") - 1.0 : 0;
        }
        @Override public void onKill(Player p, LivingEntity victim) {
            if (!qualifies(victim)) return;
            p.giveExperienceLevels((int) Math.round(value("levels")));
            RandomSource rand = p.getRandom();
            List<Integer> picks = new ArrayList<>(List.of(0, 1, 2, 3, 4, 5, 6, 7, 8));
            java.util.Collections.shuffle(picks, new java.util.Random(rand.nextLong()));
            int n = Math.min(picks.size(), (int) Math.round(value("rewards")));
            for (int i = 0; i < n; i++) {
                ItemStack reward = bountyReward(picks.get(i), rand, p.level());
                if (!reward.isEmpty()) victim.spawnAtLocation(reward);
            }
        }
    });

    private static ItemStack bountyReward(int kind, RandomSource r, Level level) {
        switch (kind) {
            case 0: return new ItemStack(Items.NETHERITE_SCRAP, 1 + r.nextInt(3));
            case 1: return new ItemStack(Items.DIAMOND, 1 + r.nextInt(6));
            case 2: {
                Holder<Enchantment> mending = level.registryAccess().registryOrThrow(Registries.ENCHANTMENT)
                        .getHolderOrThrow(Enchantments.MENDING);
                return EnchantedBookItem.createForEnchantment(new EnchantmentInstance(mending, 1));
            }
            case 3: return new ItemStack(Items.GOLDEN_APPLE, 1 + r.nextInt(3));
            case 4: return new ItemStack(Items.DRAGON_BREATH, 1 + r.nextInt(5));
            case 5: return new ItemStack(Items.AMETHYST_SHARD, 1 + r.nextInt(7));
            case 6: return new ItemStack(Items.EMERALD, 3 + r.nextInt(20));
            case 7: return new ItemStack(Items.ENDER_PEARL, 1 + r.nextInt(8));
            default: return new ItemStack(Items.CAKE);
        }
    }

    // ------------------------------------------------------------------ Shield traits

    private static ItemStack usedShield(Player p) {
        ItemStack sh = TraitUtil.shieldStack(p);
        return sh;
    }

    private static final String IB_STACKS = "ut_ib";
    private static final String IB_LAST = "ut_ib_last";
    private static final String DEF_STACKS = "ut_def";
    private static final String DEF_LAST = "ut_def_last";
    private static final String SA_STACKS = "ut_sa";
    private static final String KINETIC = "ut_kin";
    private static final String MOUNTAIN = "ut_mtn";
    private static final String PB_END = "ut_pb_end";

    public static final Trait SPIKEY = reg(new Trait("spikey") {
        @Override public Kind kind() {
            return Kind.SHIELD;
        }
        private double fraction(Player p) {
            int thorns = 0;
            for (EquipmentSlot slot : new EquipmentSlot[]{
                    EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                thorns += enchantLevel(p.level(), p.getItemBySlot(slot), Enchantments.THORNS);
            }
            return pct("reflect") + thorns * pct("reflectPerThorns");
        }
        @Override public void collectModifiers(Player holder, Stats s) {
            s.reflect += fraction(holder);
        }
        @Override public Component progress(ItemStack w, Player viewer, long gameTime) {
            if (viewer == null) return null;
            return Component.literal("Reflects " + num(fraction(viewer) * 100) + "% damage");
        }
    });

    public static final Trait FORTITUDE = reg(new Trait("fortitude") {
        @Override public Kind kind() {
            return Kind.SHIELD;
        }
        @Override public void collectModifiers(Player holder, Stats s) {
            s.maxHealth += value("maxHealth");
        }
    });

    public static final Trait SHOCK_ABSORBER = reg(new Trait("shock_absorber") {
        @Override public Kind kind() {
            return Kind.SHIELD;
        }
        @Override public void onBlock(Player p, ItemStack shield, DamageSource source, float blocked) {
            p.getPersistentData().putInt(SA_STACKS, p.getPersistentData().getInt(SA_STACKS) + 1);
        }
        @Override public void onShieldRelease(Player p, ItemStack shield) {
            CompoundTag d = p.getPersistentData();
            int stacks = d.getInt(SA_STACKS);
            d.putInt(SA_STACKS, 0);
            if (stacks <= 0) return;
            int unbreaking = enchantLevel(p.level(), shield, Enchantments.UNBREAKING);
            float damage = (float) (stacks * value("damagePerStack") + unbreaking * value("damagePerUnbreaking"));
            double r = value("radius");
            for (Mob m : p.level().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(r),
                    m -> m.isAlive() && m.distanceToSqr(p) <= r * r
                            && !(m instanceof AbstractVillager)
                            && !(m instanceof TamableAnimal t && t.isTame()))) {
                m.knockback(value("knockback"), p.getX() - m.getX(), p.getZ() - m.getZ());
                extraDamage(p, m, damage);
            }
        }
    });

    public static final Trait BULWARK = reg(new Trait("bulwark") {
        @Override public Kind kind() {
            return Kind.SHIELD;
        }
        @Override public void collectModifiers(Player holder, Stats s) {
            s.armor += value("armor");
            s.toughness += value("toughness");
        }
    });

    public static final Trait HAZARD = reg(new Trait("hazard") {
        @Override public Kind kind() {
            return Kind.SHIELD;
        }
        @Override public void onBlock(Player p, ItemStack shield, DamageSource source, float blocked) {
            if (source.getEntity() instanceof LivingEntity attacker && attacker != p) {
                attacker.addEffect(new MobEffectInstance(MobEffects.POISON, ticks("poisonSeconds"), 0), p);
                attacker.addEffect(new MobEffectInstance(MobEffects.WITHER, ticks("witherSeconds"), 0), p);
            }
        }
    });

    public static final Trait IRON_BASTION = reg(new Trait("iron_bastion") {
        @Override public Kind kind() {
            return Kind.SHIELD;
        }
        @Override public void onBlock(Player p, ItemStack shield, DamageSource source, float blocked) {
            CompoundTag d = p.getPersistentData();
            int max = cap(value("damageReductionPerBlock"), value("maxDamageReduction"));
            d.putInt(IB_STACKS, Math.min(max, d.getInt(IB_STACKS) + 1));
            d.putLong(IB_LAST, now(p));
        }
        @Override public void collectModifiers(Player holder, Stats s) {
            s.damageTaken -= holder.getPersistentData().getInt(IB_STACKS) * pct("damageReductionPerBlock");
        }
    });

    public static final Trait PERFECT_GUARD = reg(new Trait("perfect_guard") {
        @Override public Kind kind() {
            return Kind.SHIELD;
        }
        @Override public void onBlock(Player p, ItemStack shield, DamageSource source, float blocked) {
            if (!(source.getEntity() instanceof LivingEntity attacker) || attacker == p) return;
            if (p.getRandom().nextDouble() >= pct("chance")) return;
            attacker.getPersistentData().putLong(PB_END, now(p) + ticks("debuffSeconds"));
            attacker.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, ticks("seconds"),
                    Math.max(0, (int) Math.round(value("slownessLevel")) - 1)), p);
        }
    });

    /** Perfect Guard: how much less damage the marked attacker deals (fraction). */
    public static double perfectBlockDealt(Entity attacker) {
        if (attacker == null || attacker.getPersistentData().getLong(PB_END) <= attacker.level().getGameTime()) return 0;
        return PERFECT_GUARD.pct("damageDealtReduction");
    }

    /** Perfect Guard: how much more damage the marked entity takes (fraction). */
    public static double perfectBlockTaken(Entity victim) {
        if (victim.getPersistentData().getLong(PB_END) <= victim.level().getGameTime()) return 0;
        return PERFECT_GUARD.pct("damageTakenIncrease");
    }

    public static final Trait AEGIS = reg(new Trait("aegis") {
        @Override public Kind kind() {
            return Kind.SHIELD;
        }
        @Override public boolean hasCooldown() {
            return true;
        }
        @Override public void onBlock(Player p, ItemStack shield, DamageSource source, float blocked) {
            long t = now(p);
            if (!ItemState.isReady(shield, id(), t) || p.getRandom().nextDouble() >= pct("chance")) return;
            p.heal((float) value("heal"));
            ItemState.startCooldown(shield, id(), t + ticks("cooldownSeconds"));
        }
    });

    public static final Trait STANCE = reg(new Trait("stance") {
        @Override public Kind kind() {
            return Kind.SHIELD;
        }
        @Override public void onBlock(Player p, ItemStack shield, DamageSource source, float blocked) {
            p.getPersistentData().putBoolean(MOUNTAIN, true);
        }
        @Override public double damageTakenBonus(Player holder, DamageSource source) {
            CompoundTag d = holder.getPersistentData();
            if (!d.getBoolean(MOUNTAIN)) return 0;
            d.remove(MOUNTAIN);
            return -pct("damageReduction");
        }
    });

    public static final Trait DEFIANCE = reg(new Trait("defiance") {
        @Override public Kind kind() {
            return Kind.SHIELD;
        }
        @Override public void onBlock(Player p, ItemStack shield, DamageSource source, float blocked) {
            CompoundTag d = p.getPersistentData();
            int max = cap(value("toughnessPerBlock"), value("maxToughness"));
            d.putInt(DEF_STACKS, Math.min(max, d.getInt(DEF_STACKS) + 1));
            d.putLong(DEF_LAST, now(p));
        }
        @Override public void collectModifiers(Player holder, Stats s) {
            s.toughness += holder.getPersistentData().getInt(DEF_STACKS) * value("toughnessPerBlock");
        }
    });

    public static final Trait KINETIC_ARSENAL = reg(new Trait("kinetic_arsenal") {
        @Override public Kind kind() {
            return Kind.SHIELD;
        }
        @Override public void onBlock(Player p, ItemStack shield, DamageSource source, float blocked) {
            CompoundTag d = p.getPersistentData();
            d.putFloat(KINETIC, d.getFloat(KINETIC) + blocked);
        }
        @Override public double flatDamageBonus(HitContext c) {
            CompoundTag d = c.attacker().getPersistentData();
            float stored = d.getFloat(KINETIC);
            if (stored <= 0) return 0;
            d.remove(KINETIC);
            return stored * pct("conversion");
        }
    });

    public static final Trait STORM_RESERVOIR = reg(new Trait("storm_reservoir") {
        @Override public Kind kind() {
            return Kind.SHIELD;
        }
        @Override public void onBlock(Player p, ItemStack shield, DamageSource source, float blocked) {
            int need = Math.max(1, (int) Math.round(value("blocks")));
            int count = ItemState.getInt(shield, "sr") + 1;
            if (count < need) {
                ItemState.update(shield, tag -> tag.putInt("sr", count));
                return;
            }
            ItemState.update(shield, tag -> tag.putInt("sr", 0));
            if (!(source.getEntity() instanceof LivingEntity enemy) || enemy == p || !(p.level() instanceof ServerLevel level)) return;
            // the bolt is only visual: the damage is dealt directly so it never hurts the shield holder
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
            if (bolt != null) {
                bolt.moveTo(Vec3.atBottomCenterOf(enemy.blockPosition()));
                bolt.setVisualOnly(true);
                if (p instanceof ServerPlayer sp) bolt.setCause(sp);
                level.addFreshEntity(bolt);
            }
            int unbreaking = enchantLevel(p.level(), shield, Enchantments.UNBREAKING);
            extraDamage(p, enemy, (float) (value("baseDamage") * (1.0 + unbreaking * value("unbreakingScale"))));
        }
        @Override public Component progress(ItemStack w, Player viewer, long gameTime) {
            return Component.literal("Damage: " + num(value("baseDamage")) + " x (1 + Unbreaking level x " + num(value("unbreakingScale")) + ")");
        }
    });

    public static final Trait WONDER_REFLECT = reg(new Trait("wonder_reflect") {
        @Override public Kind kind() {
            return Kind.SHIELD;
        }
    });

    /** Called when a projectile is about to hit a blocking player. True = the projectile was reflected. */
    public static boolean tryMagicCoating(Player p, Projectile proj) {
        if (!hasTrait(p.getUseItem(), WONDER_REFLECT)) return false;
        Vec3 motion = proj.getDeltaMovement();
        if (motion.lengthSqr() < 1.0E-6 || motion.normalize().dot(p.getLookAngle()) > 0) return false; // from behind
        if (p.getRandom().nextDouble() >= WONDER_REFLECT.pct("chance")) return false;
        proj.setDeltaMovement(motion.scale(-1.0));
        proj.setOwner(p);
        if (proj instanceof AbstractArrow arrow) {
            arrow.setBaseDamage(arrow.getBaseDamage() * WONDER_REFLECT.value("damageMultiplier"));
        }
        proj.hurtMarked = true;
        proj.hasImpulse = true;
        return true;
    }

    public static final Trait TANKER_SPIRIT = reg(new Trait("tanker_spirit") {
        @Override public Kind kind() {
            return Kind.SHIELD;
        }
        @Override public boolean hasCooldown() {
            return true;
        }
        @Override public void onBlock(Player p, ItemStack shield, DamageSource source, float blocked) {
            long t = now(p);
            if (!ItemState.isReady(shield, id(), t)) return;
            if (p.getHealth() >= p.getMaxHealth() * pct("lowHealthBelow")) return;
            p.heal(p.getMaxHealth() * (float) pct("healPercent"));
            ItemState.startCooldown(shield, id(), t + ticks("cooldownSeconds"));
        }
    });

    /** Iron Bastion / Defiance stacks vanish when the shield is lowered or no block happens for a while. */
    public static void shieldStacksTick(Player p) {
        CompoundTag d = p.getPersistentData();
        long t = now(p);
        if (d.getInt(IB_STACKS) > 0 && (!p.isBlocking() || t - d.getLong(IB_LAST) > IRON_BASTION.ticks("resetSeconds"))) {
            d.putInt(IB_STACKS, 0);
        }
        if (d.getInt(DEF_STACKS) > 0 && (!p.isBlocking() || t - d.getLong(DEF_LAST) > DEFIANCE.ticks("resetSeconds"))) {
            d.putInt(DEF_STACKS, 0);
        }
    }

    /** Spikey (internal mode): hurts the attacker with a share of the damage. */
    public static void reflectDamage(Player victim, LivingEntity attacker, float amount) {
        if (amount <= 0 || !attacker.isAlive() || !(victim.level() instanceof ServerLevel level)) return;
        fixedDamageGuard = true;
        try {
            attacker.hurt(level.damageSources().thorns(victim), amount);
        } finally {
            fixedDamageGuard = false;
        }
    }

    // ------------------------------------------------------------------ group effects

    /** Villager buffs / "cannot damage villagers" are applied in CommonEvents. */
    public static final Trait PARTY_LEADER = reg(new Trait("party_leader") {
        @Override public Kind kind() {
            return Kind.WEAPON;
        }
        @Override public void onTick(Player p) {
            if (p.tickCount % 20 != 0) return;
            double r = value("range");
            for (Villager v : p.level().getEntitiesOfClass(Villager.class, p.getBoundingBox().inflate(r),
                    v -> v.distanceToSqr(p) <= r * r)) {
                v.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 60, 0, false, false, true));
            }
        }
    });

    /** The aura is applied in CommonEvents via TraitUtil.hasKnowMyNameAura. */
    public static final Trait KNOW_MY_NAME = reg(new Trait("know_my_name") { });

    private static final ResourceLocation TAMER_SPEED = ResourceLocation.fromNamespaceAndPath("mastertrait", "tamer_speed");

    public static final Trait BEAST_TAMER = reg(new Trait("beast_tamer") {
        @Override public Kind kind() {
            return Kind.WEAPON;
        }
    });

    /** True if this tamed wolf's owner holds Beast Tamer and is within range. */
    public static boolean tamerBuffed(Wolf w) {
        if (!w.isTame() || !(w.getOwner() instanceof Player owner)) return false;
        if (owner.distanceToSqr(w) > BEAST_TAMER.value("range") * BEAST_TAMER.value("range")) return false;
        return holds(owner, BEAST_TAMER);
    }

    /** Grows / restores the owner's wolves (size via Pehkui). Called every 10 ticks for every player. */
    public static void tamerTick(Player p) {
        boolean on = holds(p, BEAST_TAMER);
        CompoundTag d = p.getPersistentData();
        if (!on && !d.getBoolean("ut_tamer")) return;
        double r = BEAST_TAMER.value("range");
        double scan = on ? r : 128;
        float growth = 1.0f + (float) BEAST_TAMER.pct("sizeBonus");
        for (Wolf w : p.level().getEntitiesOfClass(Wolf.class, p.getBoundingBox().inflate(scan),
                w -> w.isTame() && w.getOwner() == p)) {
            boolean buff = on && w.distanceToSqr(p) <= r * r;
            PehkuiCompat.applyScale(w, buff, growth);
            AttributeInstance speed = w.getAttribute(Attributes.MOVEMENT_SPEED);
            if (speed != null) {
                if (buff) speed.addOrUpdateTransientModifier(new AttributeModifier(
                        TAMER_SPEED, BEAST_TAMER.pct("speed"), AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
                else speed.removeModifier(TAMER_SPEED);
            }
        }
        d.putBoolean("ut_tamer", on);
    }

    public static final Trait ENGINEER = reg(new Trait("engineer") {
        @Override public Kind kind() {
            return Kind.WEAPON;
        }
    });

    /** True if a player holding Engineer is within range of this iron golem. */
    public static boolean engineerNear(IronGolem g) {
        double r = ENGINEER.value("range");
        for (Player o : g.level().players()) {
            if (o.isSpectator() || o.distanceToSqr(g) > r * r) continue;
            if (holds(o, ENGINEER)) return true;
        }
        return false;
    }

    /** Heals a buffed golem that took enough damage in a short time (per-golem cooldown). */
    public static void engineerOnDamaged(IronGolem g, float dealt) {
        if (dealt <= 0 || g.level().isClientSide || !engineerNear(g)) return;
        CompoundTag d = g.getPersistentData();
        long t = g.level().getGameTime();
        if (t < d.getLong("ut_eng_cd")) return;
        if (!d.contains("ut_eng_start") || t - d.getLong("ut_eng_start") > ENGINEER.ticks("windowSeconds")) {
            d.putLong("ut_eng_start", t);
            d.putFloat("ut_eng_acc", 0f);
        }
        float acc = d.getFloat("ut_eng_acc") + dealt;
        d.putFloat("ut_eng_acc", acc);
        if (acc < g.getMaxHealth() * ENGINEER.pct("damageThreshold")) return;
        double r = ENGINEER.value("healRadius");
        float sum = 0;
        for (Mob m : g.level().getEntitiesOfClass(Mob.class, g.getBoundingBox().inflate(r),
                m -> m != g && m.isAlive() && m instanceof Enemy && !isBoss(m) && m.distanceToSqr(g) <= r * r)) {
            sum += m.getMaxHealth();
        }
        d.putFloat("ut_eng_acc", 0f);
        if (sum <= 0) return;
        g.heal(sum * (float) ENGINEER.pct("healPercent"));
        d.putLong("ut_eng_cd", t + ENGINEER.ticks("cooldownSeconds"));
    }

    public static final Trait UNITED = reg(new Trait("united") {
        @Override public Kind kind() {
            return Kind.WEAPON;
        }
        @Override public double damageDealtBonus(HitContext c) {
            int holders = countHolders(c.attacker(), this, value("range"));
            return Math.max(0, holders - 1) * pct("damagePerPlayer");
        }
        @Override public double rangedDamageBonus(HitContext c) {
            return damageDealtBonus(c);
        }
    });

    // ------------------------------------------------------------------ evolved (hidden) trait

    // ------------------------------------------------------------------ Unique traits

    /** Unique, guaranteed on weapons with the tag minecraft:piglin_loved (gold weapons), never rolled as a random trait. */
    public static final Trait GOLDEN_RULE = reg(new Trait("golden_rule", Tier.UNIQUE) {
        @Override public boolean rollable() {
            return false;
        }
        @Override public boolean eligible(ItemStack weapon) {
            return weapon.is(ItemTags.PIGLIN_LOVED);
        }
        @Override public ChatFormatting nameColor() {
            return ChatFormatting.GOLD;
        }
        @Override public double expBonus(Player holder, LivingEntity victim) {
            double bonus = pct("experience");
            return wearsFullGold(holder) ? bonus * 2.0 : bonus;
        }
    });

    static {
        addRollRule(new RollRule() {
            @Override public boolean applies(ItemStack s) {
                return s.is(ItemTags.PIGLIN_LOVED) && (TraitUtil.isMeleeWeapon(s) || TraitUtil.isShield(s) || TraitUtil.isRangedWeapon(s));
            }
            @Override public List<Trait> guaranteed(ItemStack s) {
                return List.of(GOLDEN_RULE);
            }
        });
    }

    /** Unique, guaranteed on maces (never rolled as a random trait). */
    public static final Trait CORE_SHAKER = reg(new Trait("core_shaker", Tier.UNIQUE) {
        private int need() {
            return Math.max(1, (int) Math.round(value("hits")));
        }
        @Override public boolean rollable() {
            return false;
        }
        @Override public boolean eligible(ItemStack weapon) {
            return weapon.getItem() instanceof MaceItem;
        }
        /** Every attack of this mace ignores the target's armor (not shown in the description). */
        @Override public boolean ignoresArmor(HitContext c) {
            return true;
        }
        @Override public void onHit(HitContext c, float dealt) {
            ItemStack w = c.weapon();
            int next = ItemState.getInt(w, "es") + 1;
            if (next < need()) {
                ItemState.update(w, tag -> tag.putInt("es", next));
                return;
            }
            ItemState.update(w, tag -> tag.putInt("es", 0));
            Player p = c.attacker();
            p.level().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 1.0f, 1.0f);
            double r = value("range");
            List<Mob> mobs = p.level().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(r),
                    m -> m instanceof Enemy && m.isAlive() && m.distanceToSqr(p) <= r * r);
            int breach = enchantLevel(p.level(), w, Enchantments.BREACH);
            float damage = (float) (value("baseDamage") + breach * value("damagePerBreach") + mobs.size() * value("damagePerMob"));
            for (Mob m : mobs) extraDamage(p, m, damage);
        }
        @Override public Component progress(ItemStack w, Player viewer, long gameTime) {
            int stacks = Math.min(need(), ItemState.getInt(w, "es"));
            int breach = viewer == null ? 0 : enchantLevel(viewer.level(), w, Enchantments.BREACH);
            return Component.literal("Stacks: " + stacks + "/" + need() + " (" + num(value("baseDamage") + breach * value("damagePerBreach"))
                    + " + " + num(value("damagePerMob")) + " per nearby mob damage)");
        }
    });

    // ------------------------------------------------------------------ Bow / Crossbow

    private static final Set<EntityType<?>> NETHER_MOBS = Set.<EntityType<?>>of(EntityType.PIGLIN, EntityType.PIGLIN_BRUTE,
            EntityType.ZOMBIFIED_PIGLIN, EntityType.HOGLIN, EntityType.ZOGLIN, EntityType.BLAZE, EntityType.GHAST,
            EntityType.MAGMA_CUBE, EntityType.WITHER_SKELETON, EntityType.STRIDER);

    private static int totalEnchantLevels(ItemStack s) {
        int total = 0;
        for (var e : s.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).entrySet()) total += e.getIntValue();
        return total;
    }

    /** Power level + Piercing level of the weapon. */
    private static int drawEnchantLevel(Level level, ItemStack weapon) {
        return enchantLevel(level, weapon, Enchantments.POWER) + enchantLevel(level, weapon, Enchantments.PIERCING);
    }

    /** Level of the Critical Strike mod's "Critical Impact" enchantment (critical_strike:critical_damage), 0 if missing. */
    private static int criticalImpactLevel(Level level, ItemStack weapon) {
        if (weapon.isEmpty()) return 0;
        ResourceKey<Enchantment> key = ResourceKey.create(Registries.ENCHANTMENT,
                ResourceLocation.fromNamespaceAndPath("critical_strike", "critical_damage"));
        var holder = level.registryAccess().registryOrThrow(Registries.ENCHANTMENT).getHolder(key);
        if (holder.isEmpty()) return 0;
        return weapon.getOrDefault(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY).getLevel(holder.get());
    }

    private static boolean lowHealth(LivingEntity t, double fraction) {
        return t.getHealth() < t.getMaxHealth() * fraction;
    }

    private static List<Mob> hostilesAround(LivingEntity center, double range) {
        return center.level().getEntitiesOfClass(Mob.class, center.getBoundingBox().inflate(range),
                m -> m != center && m instanceof Enemy && m.isAlive() && m.distanceToSqr(center) <= range * range);
    }

    /** Counts shots on the weapon itself; returns true on every n-th shot. */
    private static boolean everyNthShot(ItemStack weapon, String key, int n) {
        int next = ItemState.getInt(weapon, key) + 1;
        boolean fire = next >= Math.max(1, n);
        ItemState.update(weapon, tag -> tag.putInt(key, fire ? 0 : next));
        return fire;
    }

    /** Guaranteed on every bow and crossbow (never rolled as the random trait). */
    public static final Trait SHARP_SHOOTER = reg(new Trait("sharp_shooter", Tier.UNIQUE) {
        @Override public Kind kind() {
            return Kind.RANGED;
        }
        @Override public boolean rollable() {
            return false;
        }
        @Override public double rangedDamageBonus(HitContext c) {
            return Ranged.distance(c.projectile(), c.target()) * pct("damagePerBlock");
        }
        @Override public void collectPreShot(HitContext c, Stats s) {
            s.armorPiercing += Ranged.distance(c.projectile(), c.target()) * value("armorPiercingPerBlock");
        }
    });

    public static final Trait OILED = reg(new Trait("oiled") {
        @Override public Kind kind() {
            return Kind.RANGED;
        }
        @Override public double rangedDamageBonus(HitContext c) {
            return c.target().getRemainingFireTicks() > 0 ? pct("damage") : 0;
        }
        @Override public void onRangedHit(HitContext c, float dealt) {
            LivingEntity t = c.target();
            if (t.getRemainingFireTicks() > 0) t.setRemainingFireTicks(t.getRemainingFireTicks() + ticks("extraFireSeconds"));
        }
    });

    public static final Trait RUPTURE_SHOT = reg(new Trait("rupture_shot") {
        @Override public Kind kind() {
            return Kind.RANGED;
        }
        @Override public double rangedDamageBonus(HitContext c) {
            if (c.target().getArmorValue() <= 0) return 0;
            return pct("damage") + criticalImpactLevel(c.attacker().level(), c.weapon()) * pct("damagePerLevel");
        }
        @Override public void collectPreShot(HitContext c, Stats s) {
            if (c.target().getArmorValue() > 0) {
                s.armorPiercing += criticalImpactLevel(c.attacker().level(), c.weapon()) * value("armorPiercingPerLevel");
            }
        }
    });

    public static final Trait BEHEMOTH_BANE = reg(new Trait("behemoth_bane") {
        @Override public Kind kind() {
            return Kind.RANGED;
        }
        @Override public void onRangedHit(HitContext c, float dealt) {
            LivingEntity t = c.target();
            if (!(t instanceof Mob) || t.getMaxHealth() < value("minMaxHealth")) return;
            int lvl = drawEnchantLevel(c.attacker().level(), c.weapon());
            extraDamage(c.attacker(), t, (float) (value("fixedDamage") + lvl * value("damagePerLevel")), true);
        }
    });

    public static final Trait BARRAGE = reg(new Trait("barrage") {
        @Override public Kind kind() {
            return Kind.RANGED;
        }
        @Override public void onShoot(Player shooter, ItemStack weapon, AbstractArrow arrow, boolean first) {
            if (first && shooter.getRandom().nextDouble() < pct("chance")) Ranged.queueExtra(arrow, (int) Math.round(value("delayTicks")));
        }
    });

    public static final Trait MERCILESS = reg(new Trait("merciless") {
        @Override public Kind kind() {
            return Kind.RANGED;
        }
        @Override public double rangedDamageBonus(HitContext c) {
            return lowHealth(c.target(), pct("healthBelow")) ? pct("damage") : 0;
        }
    });

    public static final Trait OVERDRAW = reg(new Trait("overdraw") {
        @Override public Kind kind() {
            return Kind.BOW;
        }
        @Override public void onShoot(Player shooter, ItemStack weapon, AbstractArrow arrow, boolean first) {
            if (first && shooter.getTicksUsingItem() >= ticks("seconds")) Ranged.addMult(arrow, pct("damage"));
        }
    });

    public static final Trait DEADLY_SPOT = reg(new Trait("deadly_spot") {
        @Override public Kind kind() {
            return Kind.RANGED;
        }
        @Override public void collectModifiers(Player holder, Stats s) {
            s.critDamage += pct("criticalDamage");
        }
    });

    public static final Trait PREDATOR = reg(new Trait("predator") {
        @Override public Kind kind() {
            return Kind.RANGED;
        }
        @Override public void collectPreShot(HitContext c, Stats s) {
            if (Ranged.distance(c.projectile(), c.target()) >= value("distance")) s.critChance += pct("criticalChance");
        }
    });

    public static final Trait FATAL_TRAJECTORY = reg(new Trait("fatal_trajectory") {
        @Override public Kind kind() {
            return Kind.RANGED;
        }
        @Override public double rangedDamageBonus(HitContext c) {
            return lowHealth(c.target(), pct("healthBelow")) ? pct("damage") : 0;
        }
        @Override public void collectPreShot(HitContext c, Stats s) {
            if (lowHealth(c.target(), pct("healthBelow"))) {
                s.armorPiercing += totalEnchantLevels(c.weapon()) * value("armorPiercingPerLevel");
            }
        }
    });

    public static final Trait MARKED = reg(new Trait("marked") {
        @Override public Kind kind() {
            return Kind.RANGED;
        }
        @Override public double rangedDamageBonus(HitContext c) {
            return woundBonus(this, c);
        }
        @Override public void onRangedHit(HitContext c, float dealt) {
            woundHit(this, c);
        }
    });

    public static final Trait QUICKDRAW = reg(new Trait("quickdraw") {
        @Override public Kind kind() {
            return Kind.RANGED;
        }
        @Override public double chargeSpeed(Player holder, ItemStack weapon) {
            return pct("drawSpeed");
        }
    });

    public static final Trait LINE_UP = reg(new Trait("line_up") {
        @Override public Kind kind() {
            return Kind.BOW;
        }
        @Override public void onShoot(Player shooter, ItemStack weapon, AbstractArrow arrow, boolean first) {
            // pierce level N lets an arrow hit N + 1 targets
            int level = Math.max(0, (int) Math.round(value("targets")) - 1);
            Ranged.setPierce(arrow, level);
        }
    });

    public static final Trait TRI_SHOT = reg(new Trait("tri_shot") {
        @Override public Kind kind() {
            return Kind.RANGED;
        }
        @Override public void onShoot(Player shooter, ItemStack weapon, AbstractArrow arrow, boolean first) {
            if (!first || !everyNthShot(weapon, "triad", (int) Math.round(value("shots")))) return;
            Ranged.spawnExtra(arrow, 8.0);
            Ranged.spawnExtra(arrow, -8.0);
        }
        @Override public Component progress(ItemStack w, Player viewer, long gameTime) {
            return Component.literal("Shots: " + ItemState.getInt(w, "triad") + "/" + (int) Math.round(value("shots")));
        }
    });

    public static final Trait EMPOWERED = reg(new Trait("empowered") {
        @Override public Kind kind() {
            return Kind.RANGED;
        }
        @Override public void onShoot(Player shooter, ItemStack weapon, AbstractArrow arrow, boolean first) {
            if (first && everyNthShot(weapon, "phantom", (int) Math.round(value("shots")))) Ranged.addMult(arrow, pct("damage"));
        }
        @Override public Component progress(ItemStack w, Player viewer, long gameTime) {
            return Component.literal("Shots: " + ItemState.getInt(w, "phantom") + "/" + (int) Math.round(value("shots")));
        }
    });

    public static final Trait RICOCHET = reg(new Trait("ricochet") {
        @Override public Kind kind() {
            return Kind.RANGED;
        }
        @Override public void onRangedHit(HitContext c, float dealt) {
            AbstractArrow arrow = c.projectile();
            LivingEntity from = c.target();
            if (arrow.getPersistentData().getBoolean(Ranged.RICOCHET) || c.attacker().getRandom().nextDouble() >= pct("chance")) return;
            Mob next = null;
            for (Mob m : hostilesAround(from, value("range"))) {
                if (next == null || m.distanceToSqr(from) < next.distanceToSqr(from)) next = m;
            }
            if (next == null) return;
            AbstractArrow copy = Ranged.copy(arrow);
            if (copy == null) return;
            Vec3 start = from.position().add(0, from.getBbHeight() * 0.6, 0);
            Vec3 aim = next.position().add(0, next.getBbHeight() * 0.5, 0);
            Vec3 dir = aim.subtract(start).normalize();
            double speed = Math.max(1.5, arrow.getPersistentData().getDouble(Ranged.SPEED));
            copy.setPos(start.add(dir.scale(from.getBbWidth() / 2 + 0.6)));
            copy.setDeltaMovement(dir.scale(speed));
            copy.getPersistentData().putBoolean(Ranged.RICOCHET, true);
            arrow.level().addFreshEntity(copy);
        }
    });

    public static final Trait VENOM_COATING = reg(new Trait("venom_coating") {
        @Override public Kind kind() {
            return Kind.RANGED;
        }
        @Override public double rangedDamageBonus(HitContext c) {
            MobEffectInstance poison = c.target().getEffect(MobEffects.POISON);
            if (poison == null) return 0;
            return Math.min(pct("maxDamage"), (poison.getAmplifier() + 1) * pct("damagePerLevel"));
        }
        @Override public void onRangedHit(HitContext c, float dealt) {
            MobEffectInstance poison = c.target().getEffect(MobEffects.POISON);
            if (poison == null) return;
            int max = Math.max(0, (int) Math.round(value("maxPoisonLevel")) - 1);
            int next = Math.min(max, poison.getAmplifier() + 1);
            if (next > poison.getAmplifier()) {
                c.target().addEffect(new MobEffectInstance(MobEffects.POISON, poison.getDuration(), next), c.attacker());
            }
        }
    });

    public static final Trait OBLIVION_BANE = reg(new Trait("oblivion_bane") {
        @Override public Kind kind() {
            return Kind.RANGED;
        }
        @Override public void onRangedHit(HitContext c, float dealt) {
            if (!c.target().hasEffect(MobEffects.WITHER)) return;
            for (Mob m : hostilesAround(c.target(), value("range"))) {
                m.addEffect(new MobEffectInstance(MobEffects.WITHER, ticks("witherSeconds"), 0), c.attacker());
            }
        }
        @Override public void onRangedKill(HitContext c) {
            if (!c.target().hasEffect(MobEffects.WITHER)) return;
            int lvl = drawEnchantLevel(c.attacker().level(), c.weapon());
            float damage = (float) (value("baseDamage") + lvl * value("damagePerLevel"));
            for (Mob m : hostilesAround(c.target(), value("range"))) extraDamage(c.attacker(), m, damage, true);
        }
    });

    public static final Trait OVERPOWERED = reg(new Trait("overpowered") {
        @Override public Kind kind() {
            return Kind.RANGED;
        }
        @Override public void onRangedHit(HitContext c, float dealt) {
            if (!(c.target() instanceof Mob) || dealt <= 0) return;
            float splash = (float) (dealt * pct("damage"));
            for (Mob m : hostilesAround(c.target(), value("range"))) extraDamage(c.attacker(), m, splash, true);
        }
    });

    public static final Trait SERAPHIC_JUDGEMENT = reg(new Trait("seraphic_judgement") {
        @Override public Kind kind() {
            return Kind.RANGED;
        }
        @Override public double rangedDamageBonus(HitContext c) {
            LivingEntity t = c.target();
            boolean hit = t.getType().is(EntityTypeTags.UNDEAD) || NETHER_MOBS.contains(t.getType()) || t.fireImmune();
            return hit ? pct("damage") : 0;
        }
        @Override public void onRangedHit(HitContext c, float dealt) {
            if (TraitUtil.isCriticalHit(c.source()) && dealt > 0) {
                Player p = c.attacker();
                p.heal(p.getMaxHealth() * (float) pct("critHeal"));
            }
        }
    });

    public static final Trait FRENZY = reg(new Trait("frenzy") {
        private int stacks(Player p) {
            CompoundTag d = p.getPersistentData();
            if (d.getInt(Ranged.FRENZY) > 0 && now(p) - d.getLong(Ranged.FRENZY_LAST) > ticks("resetSeconds")) {
                d.putInt(Ranged.FRENZY, 0);
            }
            return d.getInt(Ranged.FRENZY);
        }
        @Override public Kind kind() {
            return Kind.RANGED;
        }
        @Override public double rangedDamageBonus(HitContext c) {
            return -pct("damagePenalty");
        }
        @Override public double chargeSpeed(Player holder, ItemStack weapon) {
            return stacks(holder) * pct("drawSpeedPerHit");
        }
        @Override public void onRangedHit(HitContext c, float dealt) {
            Player p = c.attacker();
            CompoundTag d = p.getPersistentData();
            int max = cap(value("drawSpeedPerHit"), value("maxDrawSpeed"));
            d.putInt(Ranged.FRENZY, Math.min(max, stacks(p) + 1));
            d.putLong(Ranged.FRENZY_LAST, now(p));
        }
    });

    public static final Trait OPENING_SHOT = reg(new Trait("opening_shot") {
        @Override public Kind kind() {
            return Kind.RANGED;
        }
        @Override public double rangedDamageBonus(HitContext c) {
            LivingEntity t = c.target();
            return t.getHealth() >= t.getMaxHealth() ? pct("damage") : 0;
        }
    });

    public static final Trait TAILWIND = reg(new Trait("tailwind") {
        @Override public Kind kind() {
            return Kind.RANGED;
        }
        @Override public boolean hasCooldown() {
            return true;
        }
        @Override public void onRangedHit(HitContext c, float dealt) {
            Player p = c.attacker();
            ItemStack real = Ranged.shotStack(p);
            boolean tracked = TraitUtil.hasTrait(real, this);
            long t = now(p);
            if (tracked && !ItemState.isReady(real, id(), t)) return;
            p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, ticks("seconds"), 0));
            if (tracked) ItemState.startCooldown(real, id(), t + ticks("cooldownSeconds"));
        }
    });

    public static final Trait LIQUIFY = reg(new Trait("liquify") {
        @Override public Kind kind() {
            return Kind.RANGED;
        }
        @Override public void onShoot(Player shooter, ItemStack weapon, AbstractArrow arrow, boolean first) {
            arrow.getPersistentData().putBoolean(Ranged.LIQUIFY, true);
            if (shooter.isInWater()) Ranged.addMult(arrow, pct("damage"));
        }
    });

    public static final Trait TIGHT_STRING = reg(new Trait("tight_string") {
        @Override public Kind kind() {
            return Kind.RANGED;
        }
        @Override public double rangedDamageBonus(HitContext c) {
            return pct("damage");
        }
        @Override public double chargeSpeed(Player holder, ItemStack weapon) {
            return -pct("drawSpeed");
        }
    });

    public static final Trait SHATTERPOINT = reg(new Trait("shatterpoint") {
        @Override public Kind kind() {
            return Kind.RANGED;
        }
        @Override public double armorIgnore(HitContext c) {
            return TraitUtil.isCriticalHit(c.source()) ? pct("armorIgnore") : 0;
        }
    });

    private static final String ARROW_PHOENIX = "mt_phoenix";

    public static final Trait PHOENIXS_LAST_ARROW = reg(new Trait("phoenixs_last_arrow") {
        @Override public Kind kind() {
            return Kind.RANGED;
        }
        @Override public boolean hasCooldown() {
            return true;
        }
        @Override public double rangedDamageBonus(HitContext c) {
            Player p = c.attacker();
            if (!lowHealth(p, pct("healthBelow"))) return 0;
            ItemStack real = Ranged.shotStack(p);
            boolean tracked = TraitUtil.hasTrait(real, this);
            long t = now(p);
            if (tracked && !ItemState.isReady(real, id(), t)) return 0;
            if (tracked) ItemState.startCooldown(real, id(), t + ticks("cooldownSeconds"));
            c.projectile().getPersistentData().putBoolean(ARROW_PHOENIX, true);
            return pct("damage");
        }
        @Override public void onRangedHit(HitContext c, float dealt) {
            if (!c.projectile().getPersistentData().getBoolean(ARROW_PHOENIX)) return;
            c.projectile().getPersistentData().putBoolean(ARROW_PHOENIX, false);
            Player p = c.attacker();
            p.heal(p.getMaxHealth() * (float) pct("heal"));
        }
    });

    private static final String P_REQUIEM_UNTIL = "mt_requiem_until";

    /** Requiem of Kings: damage bonus of a player who is inside the buff window. */
    public static double requiemBuff(Player p) {
        return now(p) < p.getPersistentData().getLong(P_REQUIEM_UNTIL) ? REQUIEM_OF_KINGS.pct("buffDamage") : 0;
    }

    public static final Trait REQUIEM_OF_KINGS = reg(new Trait("requiem_of_kings") {
        @Override public Kind kind() {
            return Kind.RANGED;
        }
        @Override public double rangedDamageBonus(HitContext c) {
            return c.target().getMaxHealth() >= value("minMaxHealth") ? pct("damage") : 0;
        }
        @Override public void onRangedKill(HitContext c) {
            LivingEntity v = c.target();
            if (v.getMaxHealth() < value("minMaxHealth")) return;
            double r = value("range");
            long until = now(c.attacker()) + ticks("buffSeconds");
            for (Player other : v.level().getEntitiesOfClass(Player.class, v.getBoundingBox().inflate(r),
                    pl -> pl.isAlive() && pl.distanceToSqr(v) <= r * r)) {
                other.getPersistentData().putLong(P_REQUIEM_UNTIL, until);
            }
            c.attacker().getPersistentData().putLong(P_REQUIEM_UNTIL, until);
        }
    });

    // ------------------------------------------------------------------ Sword

    public static final Trait DRAGON_SLAYER = reg(new Trait("dragon_slayer") {
        @Override public Kind kind() {
            return Kind.SWORD;
        }
        @Override public double damageDealtBonus(HitContext c) {
            return dragonHealth(c.target()) ? pct("damage") : 0;
        }
        @Override public double damageTakenBonus(Player holder, DamageSource source) {
            return source.getEntity() instanceof LivingEntity e && dragonHealth(e) ? -pct("damageReduction") : 0;
        }
        private boolean dragonHealth(LivingEntity e) {
            float hp = e.getMaxHealth();
            return Math.abs(hp - value("health1")) < 0.5 || Math.abs(hp - value("health2")) < 0.5;
        }
    });

    // ------------------------------------------------------------------ Divine / Heroic

    // ------------------------------------------------------------------ Shared helpers

    /** While true, our damage handlers ignore the hit (it is an extra hit we dealt ourselves). */
    public static boolean fixedDamageGuard = false;


    /** While true, knockback events are cancelled (used by damage-over-time effects such as Radiance). */
    public static boolean noKnockbackGuard = false;

    public static void extraDamage(Player attacker, LivingEntity target, float amount) {
        extraDamage(attacker, target, amount, false);
    }

    public static void extraDamage(Player attacker, LivingEntity target, float amount, boolean noKnockback) {
        if (amount <= 0 || !target.isAlive() || !(target.level() instanceof ServerLevel level)) return;
        fixedDamageGuard = true;
        noKnockbackGuard = noKnockback;
        try {
            target.invulnerableTime = 0;
            target.hurt(level.damageSources().indirectMagic(attacker, attacker), amount);
        } finally {
            fixedDamageGuard = false;
            noKnockbackGuard = false;
        }
    }

    private Traits() {}
}
