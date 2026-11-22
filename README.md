# Master Trait

![Minecraft 1.21.1](https://img.shields.io/badge/Minecraft-1.21.1-62B47A)
![NeoForge 21.1](https://img.shields.io/badge/NeoForge-21.1.x-E0662E)
![Version 1.0.0](https://img.shields.io/badge/version-1.0.0-8A6BFF)
![License MIT](https://img.shields.io/badge/License-MIT-blue)

RPG-style **weapon traits** for Minecraft. Melee weapons, shields, bows, crossbows and tridents roll random traits:
passive perks such as bonus damage against certain enemies, lifesteal, critical hits, extra arrows or shield
counters. Every weapon you find feels a little different. Built as an add-on for **Spell Engine**,
**Critical Strike** and **MRPG Lib**.

## Features

- **132 traits** across melee weapons, swords, shields, bows, crossbows and tridents.
- Traits roll **automatically** the first time an item enters an inventory (crafted, looted, traded...).
- Clear **tooltips**: hover to see the trait, hold **Shift** for details. Numbers follow the config, and traits with
  progress or a cooldown show it (kill counters, Ready / m:ss).
- **Unique traits** tied to special items (maces, bows, gold weapons).
- **Reroll** a weapon's traits in an anvil with a **Heart of the Sea**.
- **Every trait is configurable**: turn it off or change any of its numbers.
- Works with the attributes of Critical Strike, Spell Engine and MRPG Lib.
- **Add-on API** for other mods to add traits, roll rules and new tiers.

## How traits are rolled

| Item | Traits |
|---|---|
| Swords, axes, daggers, shields | 1 random trait (`rollChance`, default 100%), then a 5% chance of a second one (`secondTraitChance`) |
| Bows and crossbows | **Sharp Shooter** (guaranteed) + 1 random trait |
| Maces | **Core Shaker** (guaranteed) + 1 random trait |
| Gold weapons (`#minecraft:piglin_loved`) | **Golden Rule** (guaranteed) + 1 random trait |
| Tridents | Always exactly **3** traits from the trident pool |

- Tridents roll from 18 trident-only traits plus Critical Eyes, Sky Striker, Scavenger, Banner Bearer,
  Bounty Hunter, Party Leader, Beast Tamer, Engineer and United.
- Daggers (`#rpg_series:weapon_type/dagger`) roll melee traits.
- "Critical" for Shatterpoint and Seraphic Judgement means a hit marked as critical by Critical Strike. If ranged
  crits are disabled in Critical Strike's config, these two never trigger.

### Rerolling

Put a weapon in the left slot of an anvil and a **Heart of the Sea** in the right slot (5 levels, `rerollLevelCost`).
Its normal traits are rolled again; Unique, Divine, Heroic and Mythical traits stay. The new traits are shown when you
take the item.

## Traits

<details>
<summary>All 132 traits by item type (hold Shift on an item in game for the full description)</summary>

**Melee weapons (60)**
Berserker Soul, Blazing, Bloodthirst, Core Shaker *(Unique)*, Corpse Eater, Curse Extract, Deadly Discipline,
Doom Slayer, Duelist, Dune Stalker, Echoing Blades, Emerald Obsession, Ethereal Steel, Frostborn, Giant Slayer,
Glacial Fang, Glass Cannon, Golden Rule *(Unique)*, Grand Strike, Hell Forged, Helm Breaker, Holy Armament,
Holy Chant, Illuminating, Insect Crusher, Know My Name, Light Absorb, Light Weight, Lone Wolf, Masochist, Massacred,
Maximum Might, Menacing, Midas Touch, Momentum, Moonlit Edge, Mount Master, Night Edge, Overwhelming,
Peak Performance, Phial Blade, Rampager, Ruinous Cleave, Satsujinki *(Unique)*, Slaughter, Solo Leveling, Steadfast,
Sunscorched, Tempered, Tempest, Titan's Vigor, Unstable Blade, Vampirism, Vanquished Soul, Venomous Edge, Void Bane,
Well Fed, Wicked Rune, Wither Hand, Wound Destroyer

**Swords only (1)**
Dragon Slayer

**Any weapon: melee, bows and crossbows (9)**
Banner Bearer, Beast Tamer, Bounty Hunter, Critical Eyes, Engineer, Party Leader, Scavenger, Sky Striker, United

**Bows and crossbows (28)**
Barrage, Behemoth Bane, Deadly Spot, Empowered, Fatal Trajectory, Frenzy, Line Up, Liquify, Marked, Merciless,
Oblivion Bane, Oiled, Opening Shot, Overdraw, Overpowered, Phoenix's Last Arrow, Predator, Quickdraw,
Requiem of Kings, Ricochet, Rupture Shot, Seraphic Judgement, Sharp Shooter *(Unique)*, Shatterpoint, Tailwind,
Tight String, Tri-Shot, Venom Coating

**Shields (16)**
Aegis, Bulwark, Counterstrike, Defiance, Fortitude, Hazard, Immovable Object, Iron Bastion, Kinetic Arsenal,
Perfect Guard, Shock Absorber, Spikey, Stance, Storm Reservoir, Tanker Spirit, Wonder Reflect

**Tridents (18)**
Abyssal Reaver, Aegis of the Deep, Beastial, Blessed from the Origin Water, Coral Piercer, Executioner's Prong,
Glacial Undertow, Lightning Judgement, Linebreaker, Ocean Splitter, Rain Dance, Relentless, Royal Impaler,
Stormblood, Sundering, Thorned Trident, Unyielding Tide, Will of the Ocean
</details>

Divine, Heroic and Mythical traits (such as Nitoryu, Mystic Eye and Twilight Lord) are in the add-on
**Master Trait: Extraordinaries**. By default their names stay scrambled until the player has slain the Wither.

## Requirements

| Mod | Version | |
|---|---|---|
| Minecraft | 1.21.1 | Required |
| NeoForge | 21.1.x | Required |
| Spell Engine | 1.10.9 or newer | Required |
| Critical Strike | 1.0.4 or newer | Required |
| MRPG Lib | 2.8.1 or newer | Required |
| Pehkui | any | Optional: Beast Tamer also makes your wolves bigger |

Install on **both the client and the server**.

## Commands

Operator only (permission level 2). Hold a sword, axe, mace, trident, bow, crossbow or shield in your main hand.

```
/mastertrait set <trait>   Replace the item's traits with this one
/mastertrait add <trait>   Add a trait
/mastertrait reroll        Roll the item's traits again
/mastertrait clear         Remove all traits
/mastertrait list          List every trait id
```

## Configuration

`<world>/serverconfig/mastertrait-server.toml` (server config, synced to clients so tooltips match the server).
Modpacks can ship defaults in `defaultconfigs/`.

| Section | Settings |
|---|---|
| `[rolling]` | `rollChance` (1.0), `secondTraitChance` (0.05), `heroicChance` (0.02, used when an add-on adds Heroic traits) |
| `[special]` | `hideTopTraitsUntilWitherSlain` (true), `rerollLevelCost` (5) |
| `[compat]` | `damageReductionMode`, `lifestealMode`, `reflectMode` (`ATTRIBUTE`, `INTERNAL` or `BOTH`), `rageAttribute` |
| `[compat.attributeScales]` | How bonuses are sent to the `critical_strike:*`, `spell_engine:damage_taken` and `mrpg_lib:*` attributes |
| `[traits.<group>.<trait>]` | `enabled` plus every number of that trait. Groups: `weapons`, `shields`, `unique`, `bows`, `swords`, `tridents` |

Every trait section has a comment with its name, and every number has a unit (percent, seconds, blocks, count).
Percent values are plain numbers (5 = 5%). After updating the mod, delete the old file so new defaults are written.

## Add-on API

Add-ons depend on `mastertrait` and call these in their mod constructor, before any item is rolled:

1. `TraitDefs.addonGroup("my_addon")` and `TraitDefs.registerAddon("my_trait", "damage", 10, ...)` declare the numbers.
2. `Config.defineTraits(builder, ids)` builds the same `[traits.<group>.<trait>]` layout in the add-on's own server config.
3. `Traits.register(new Trait("my_trait", Tier.UNIQUE) { ... })` registers the trait. Override the hooks in `Trait`
   (or `TridentTrait`) you need, plus `kind()` and `eligible()` to control which items it rolls on.
4. `Traits.addRollRule(rule)` gives items guaranteed traits and an optional custom pool for their random trait.
   All rules that apply to an item are used together; `Traits.currentRoller()` is the player being rolled for.
5. `Tier.UNIQUE`, `Tier.DIVINE`, `Tier.HEROIC` and `Tier.MYTHICAL` set how a trait is shown.
6. Lang keys: `trait.mastertrait.<id>.name` and `.desc`. In descriptions `{key}` shows the config number and
   `{key:min}` shows seconds as m:ss.

See *Master Trait: Extraordinaries* for a complete example.

## Building from source

Requires JDK 21.

```
Windows:      .\gradlew.bat build
macOS/Linux:  ./gradlew build
```

The jar is written to `build/libs/mastertraits-1.0.0+mc1.21.1neoforge.jar`.

## Changelog

See [CHANGELOG.md](CHANGELOG.md).

## License

[MIT](LICENSE)
