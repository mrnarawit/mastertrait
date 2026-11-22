package com.mastertrait.trait;

/**
 * Attribute-style bonuses a trait contributes while held. All values are fractions (0.05 = 5%),
 * except attackDamage and armorPiercing which are flat amounts. Negative damageTaken = damage reduction.
 */
public final class Stats {
    public double attackSpeed;
    public double attackDamage;
    public double critChance;
    public double critDamage;
    public double damageTaken;
    public double lifesteal;
    public double knockbackRes;
    public double rage;
    public double armorPiercing;
    public double waterSpellPower;
    public double reflect;
    public double maxHealth;
    public double armor;
    public double toughness;
    public double moveSpeed;
}
