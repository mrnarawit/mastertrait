package com.mastertrait.trait;

/** Display / rarity tier of a trait. */
public enum Tier {
    /** Normal trait (gold header). */
    TRAIT,
    /** Exclusive to special conditions (green header). */
    UNIQUE,
    /** Divine trait (shimmering header). */
    DIVINE,
    /** Heroic trait (rainbow header). */
    HEROIC,
    /** Mythical trait (header and name cycle through deep blue, indigo and violet). */
    MYTHICAL;

    /** Importance, 0 = highest: Divine, Heroic, Mythical, Unique, Trait. */
    public int rank() {
        return switch (this) {
            case DIVINE -> 0;
            case HEROIC -> 1;
            case MYTHICAL -> 2;
            case UNIQUE -> 3;
            case TRAIT -> 4;
        };
    }

    /** Divine, Heroic and Mythical: they replace the normal random trait and stay unreadable until the Wither is slain. */
    public boolean isTop() {
        return rank() <= 2;
    }
}
