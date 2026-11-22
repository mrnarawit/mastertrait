package com.mastertrait.trait;

import net.minecraft.world.item.ItemStack;

/** Which kind of item a trait can roll on. */
public enum Kind {
    /** Swords, axes, maces, tridents. */
    MELEE,
    SHIELD,
    /** Melee weapons and shields. */
    BOTH,
    BOW,
    CROSSBOW,
    /** Bows and crossbows. */
    RANGED,
    /** Melee weapons, bows and crossbows. */
    WEAPON,
    /** Swords only. */
    SWORD,
    /** Tridents only. */
    TRIDENT;

    public boolean fits(ItemStack s) {
        boolean shield = TraitUtil.isShield(s);
        boolean bow = TraitUtil.isBow(s);
        boolean crossbow = TraitUtil.isCrossbow(s);
        boolean melee = !shield && !bow && !crossbow;
        return switch (this) {
            case MELEE -> melee;
            case SHIELD -> shield;
            case BOTH -> melee || shield;
            case BOW -> bow;
            case CROSSBOW -> crossbow;
            case RANGED -> bow || crossbow;
            case WEAPON -> melee || bow || crossbow;
            case SWORD -> TraitUtil.isSword(s);
            case TRIDENT -> TraitUtil.isTrident(s);
        };
    }
}
