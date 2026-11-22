package com.mastertrait.trait;

import net.minecraft.world.entity.player.Player;

/**
 * Base for trident-only traits. The same damage and on-hit logic runs for melee hits and for thrown tridents
 * ({@code thrown} tells them apart).
 */
public abstract class TridentTrait extends Trait {
    protected TridentTrait(String id) {
        super(id);
    }

    protected TridentTrait(String id, Tier tier) {
        super(id, tier);
    }

    @Override public final Kind kind() {
        return Kind.TRIDENT;
    }

    @Override public final boolean tridentOnly() {
        return true;
    }

    /** Fractional damage bonus for one hit. */
    protected double damage(HitContext c, boolean thrown) {
        return 0;
    }

    /** After a hit landed. */
    protected void hit(HitContext c, float dealt, boolean thrown) {
    }

    @Override public final double damageDealtBonus(HitContext c) {
        return damage(c, false);
    }

    @Override public final double thrownDamageBonus(HitContext c) {
        return damage(c, true);
    }

    @Override public final void onHit(HitContext c, float dealt) {
        hit(c, dealt, false);
    }

    @Override public final void onThrownHit(HitContext c, float dealt) {
        hit(c, dealt, true);
    }

    protected static long gameTime(Player p) {
        return p.level().getGameTime();
    }
}
