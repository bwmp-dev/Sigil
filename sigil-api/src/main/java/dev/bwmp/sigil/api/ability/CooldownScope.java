package dev.bwmp.sigil.api.ability;

/** Who or what a cooldown applies to. */
public enum CooldownScope {

    /**
     * Per player. Almost always the right choice: it survives the item being
     * dropped, re-picked-up, moved between slots or duplicated in creative,
     * none of which should refund an ability.
     */
    PLAYER,

    /**
     * Per individual item stack. For charge-like items where owning two really
     * should mean two activations. Requires the item to carry a unique id.
     */
    STACK,

    /** Server-wide. For abilities that affect shared state. */
    GLOBAL
}
