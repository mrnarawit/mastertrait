# Master Trait (NeoForge 1.21.1) - melee weapons

Every sword / axe / mace gets ONE random Unique Trait the first time it enters a player's inventory
(crafted, looted, traded...). Hover the item: purple "Unique Trait: <name>", hold Shift for details.

Build: needs JDK 21. If Gradle can't resolve the plugin/neo_version, copy the build files from the official
NeoForge 1.21.1 MDK and keep only `src/`.
  ./gradlew build   ->  build/libs/mastertrait-0.1.0.jar

Test: /mastertrait set <trait> | reroll | clear | list   (op level 2, hold a weapon)
Config: config/mastertrait-common.toml  (rollChance, disabledTraits)

Heroic traits (rainbow header) are rarer: `heroicChance` in the config (default 2%) replaces the Unique roll.

## v0.3.0
Required mods: Spell Engine (spell_engine), Critical Strike (critical_strike), MRPG Lib (mrpg_lib).
Traits apply these attributes through transient modifiers, looked up by id at runtime:
  critical_strike:chance, critical_strike:damage, spell_engine:damage_taken, mrpg_lib:lifesteal_modifier
If an attribute uses percentage points instead of fractions, set its scale to 100 in
config/mastertrait-common.toml [attributeScales]. Check values in game with:
  /attribute @s critical_strike:chance get

## v0.3.1
spell_engine:damage_taken and mrpg_lib:lifesteal_modifier use percentage points with a base of 100,
Config: damageReductionMode / lifestealMode = ATTRIBUTE | INTERNAL | BOTH.

## v0.3.2
Fixed "Format error" in trait descriptions: I18n.get() runs String.format, which chokes on "% d" / "% k" in
texts like "+7.5% damage". Descriptions are now read raw with Language#getOrDefault.

## v0.4.0
- 54 traits. Poseidon Force / Rain Dance bring 1 extra random trait; tridents always roll one of them.
- Satsujinki is now a weapon-bound kill counter (+0.05%/kill, max 20%). Old effect renamed Massacred.
- Tooltips show progress (Satsujinki, Massacred, Emerald Obsession) and cooldown (Ready / m:ss).
- Rage: set `rageAttribute` in the config to the attribute id of "rage"; empty = plain damage fallback.

## 0.5.0
- 62 traits (61 visible + hidden Mystic Eye). Tiers: Trait (gold), Unique (green), Divine (shimmer), Heroic (rainbow).
- Per-trait SERVER config (`world/serverconfig/mastertrait-server.toml`, or `defaultconfigs/` for modpacks): `[traits.<id>]` has `enabled` and every number. Tooltip numbers follow the config.
- Weapons made before 0.5.0 keep their old data; use `/mastertrait reroll` on them.
- Commands: `/mastertrait set|add <id>`, `reroll`, `clear`, `list` (op level 2). 

## 0.5.1
- Divine Enchant renamed Holy Chant (new chant, -30s CD per Smite level).
- New traits: True Enchanted, Curse Extract, Beast Tamer, Engineer. Atelier gains Sweeping Edge bonuses.
- Tempest 15s (+15s from Channeling lightning); Wicked Rune reworked (8% + Blast/Fire/Projectile Prot, radius grows with levels).
- Tooltip descriptions are word-wrapped.

## 0.5.2
- Scrap Gatherer, Phial Blade (was Atelier; Sweeping Edge now adds +5s CD per level), Battle Healing is Unique, new Unique Luminous Sword.
- Nitoryu R -40% damage, shown in blue without "Unique Trait" label; R also gets Battle Healing, L gets Luminous Sword. Elucidator tooltip frame black, Dark Repulser blue.

## 0.5.3
- Beast Tamer size +20%. Nitoryu L desc shows -40%. Scavenger -> Corpse Eater, Scrap Gatherer -> Scavenger (ids renamed). Mystic Eye shown as rainbow name only; red glow updates every tick.

## 0.5.4
- Nitoryu works with either sword attacking (Better Combat dual wield): R and L both apply -40% damage and share stacks.

## 0.5.5
- New trait: Titan's Vigor (+0.225% damage per max HP, max 18%).

## 0.6.0
- Satsujinki is Unique. Mystic Eye instakill threshold 20 HP. Abyss Piercer -> Helm Breaker (+4 Armor Piercing, no depth limit).
- New: Moonlit Edge, Counterstrike, Momentum, Banner Bearer, Bounty Hunter, Gae Bolg (Heroic, tridents). Secret Technique adds a low-health heal.
- Blazing scales with Fire Aspect; Sky Striker 24%; Phial Blade: each Sweeping Edge level +5s effect duration.
- Enchant-scaled traits show a progress line (Blazing, Phial Blade, Holy Chant, Wicked Rune, True Enchanted, Curse Extract, Midas Touch).

## 0.6.1
- Secret Technique -> Fighting Spirit. Phial Capacity progress lists the effects that can currently roll.

## 0.7.0
- New: Radiance (Unique), Double Strike. Atelier id -> phial_blade. Poseidon Force: no Nether clause; +32% damage vs fire creatures.

## 0.8.0 - shields
- Shields roll traits too (16 shield traits, Immovable Object works on both). Counterstrike and Fighting Spirit are shield-only now.
- New melee: Intense Slash. True Enchanted -> Illuminating. Double Strike halves the extra hit and costs 5% damage. Titan's Vigor has no cap.
- Tempest: no Haste, double lightning damage. Beast Tamer +50% damage, +15% speed. Engineer ignores bosses.
- New config: reflectMode / mrpg_lib_damage_reflect_modifier scale.
- NOTE: serverconfig files keep old numbers. Delete world/serverconfig/mastertrait-server.toml to regenerate defaults.

## 0.8.1
- Perfect Block: damage debuff lasts 10s (debuffSeconds), Slowness stays 3s.

## 0.9.0
- Fighting Spirit merged into Tanker Spirit (15%/15%). Hazard Touch -> Hazard, Double Strike -> Echoing Blades, Intense Slash -> Ruinous Cleave.
- Shock Absorber: 1.5 x stacks + Unbreaking level. Spikey counts Thorns on armor. Emerald Obsession max 16%.
- New: Earth Shattering (guaranteed on maces + 1 random trait), Ethereal Steel, Sunscorched, Dune Stalker, Frostborn, Glacial Fang.

## 0.9.1
- Earth Shattering range 6/base 6. Storm Reservoir scales with Unbreaking. Glacial Fang 16%. Ethereal Steel 25%. Radiance 1 dmg/s with no knockback.

## 0.9.2
- Removed Poseidon Force, Gae Bolg, Twelve Labors of Hercules and Mystic Eye (moved to a planned add-on); Satsujinki no longer evolves.
- Tempest: Channeling extension removed. Holy Chant: Strength is lost when the weapon is no longer held.
- Description rewrites (Wicked Rune, Curse Extract, Tempest, Holy Chant, Bounty Hunter, Nitoryu R/L).

## 0.9.3 / 0.9.4 (0.9.4 build fix: PehkuiCompat.applyScale restored)
- Renamed to Master Trait: mod id, package, lang keys and config file are now `mastertrait` (old items with traits and old serverconfig do not carry over).
- Nitoryu R / L, Battle Healing, Luminous Sword and Radiance moved to the add-on "Master Trait : Extraordinaries".
- New add-on API: `Traits.register`, `TraitDefs.registerAddon`, `Config.defineTraits`.

## 0.9.5 (build fix: pierce level set through reflection; command renamed to /mastertrait)
- Bows and crossbows get traits: Sharp Shooter (guaranteed) + one random trait. 22 new traits (109 total), Critical Eyes now also rolls on bows / crossbows.
- Earth Shattering renamed to Core Shaker (id `core_shaker`).
- Ranged traits read the weapon from the arrow (so switching weapons mid-flight does not lose them). Extra arrows (Barrage, Triad Volley, Ricochet) are copies that cannot be picked up.
- New server config sections `[traits.<id>]` for every new trait. Delete `world/serverconfig/mastertrait-server.toml` to regenerate.

## 0.9.6
- Bows / crossbows: new Liquify and Tight String. Sharp Shooter is now a Unique trait (4% per block). Renamed Phantom Replication -> Empowered, Death Zone -> Oblivion Bane, Overpower -> Overpowered, Sanctified Hunt -> Sanctified (ids renamed too).
- Barrage 15%: the extra arrow follows 3 ticks later and ignores invulnerability frames. Ricochet range 8, Overdraw +100%, Triad Volley every 8th shot, Venom Coating 3%/level, Colossus Hunter 300+ HP / 2 damage (+1 per Power and Piercing level). Frenzy no longer shows a progress line.
- Bounty Hunter, Party Leader, Beast Tamer, Engineer, United, Banner Bearer and Scavenger can now roll on bows and crossbows.
- Immovable Object only rolls on shields.
- Core Shaker: anvil sound when it triggers, and the triggering hit ignores armor.
- 111 traits in total.

## 0.9.7
- New Tailwind (bow / crossbow): after an arrow hits, Speed I for 10s, CD 1 min. 112 traits.
- Every attack of a mace (Core Shaker) now ignores armor.
- Satsujinki: name and progress turn red when the weapon has all 1000 kills.
- Renamed Perfect Block -> Perfect Guard, Mountain Stance -> Stance, Magic Coating -> Wonder Reflect (ids renamed too).

## 0.9.8
- Renamed Earth Slam -> Core Shaker (id `core_shaker`).
- Trait chance: `rollChance` is now 10% (25% from 1.0.0) for the random trait of any weapon / shield; if one rolled, `secondTraitChance` (10%) gives a second one. Guaranteed traits (Sharp Shooter on bows, Core Shaker on maces, Will of the Ocean + Rain Dance on tridents) stay guaranteed.
- Sharp Shooter: 2% damage and +1 Armor Piercing per block. Sky Striker also rolls on bows / crossbows. Moonlit Edge 48%.

## 1.0.0
- First stable release. `rollChance` (chance of a weapon / shield getting its random trait) raised from 10% to 25%; `secondTraitChance` stays 10%. 112 traits.

## 1.0.1
- `rollChance` back to 100% (every weapon / shield gets a random trait); `secondTraitChance` stays 10%.
- Sanctified -> Seraphic Judgement (id `seraphic_judgement`): critical arrows also heal 10% of max health.

## 1.0.2 (132 traits)
- `secondTraitChance` 5% (rollChance stays 100%). Ruinous Cleave chance 1% -> 5%.
- New sword-only trait: Dragon Slayer (+24% damage to enemies with 1800+ max health, 16% less damage taken from them).
- Bows and crossbows: new Shatterpoint (critical arrows ignore 50% armor), Phoenix's Last Arrow, Requiem of Kings. Renamed Colossus Hunter -> Behemoth Bane, Triad Volley -> Tri-Shot, Eagle's Eyes -> Predator (old items keep working).
- "Critical" for Shatterpoint and Seraphic Judgement is a hit marked as critical by the Critical Strike mod (read from the damage source). If Critical Strike's ranged crits are disabled in its config, they never trigger.
- Tridents: always exactly 3 random traits (rollChance / secondTraitChance do not apply), from 18 trident-only traits plus Critical Eyes, Sky Striker, Scavenger, Banner Bearer, Bounty Hunter, Party Leader, Beast Tamer, Engineer, United. Will of the Ocean and Rain Dance are no longer guaranteed and are trident-only.
- 16 new trident traits: Royal Impaler, Sundering, Coral Piercer, Executioner's Prong, Relentless, Linebreaker, Blessed from the Origin Water, Abyssal Reaver, Glacial Undertow, Stormblood, Aegis of the Deep, Thorned Trident, Unyielding Tide, Lightning Judgement, Ocean Splitter, Beastial.
- Delete `world/serverconfig/mastertrait-server.toml` after updating so the new defaults apply.
