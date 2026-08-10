package dev.bwmp.sigil.api.ability;

/**
 * The points at which an ability can fire.
 * <p>
 * A small set with conditions layered on top via {@link TriggerBinding},
 * rather than one constant per combination. The alternative — separate hooks
 * for left/shift-left/left-on-block and so on — means adding a modifier
 * multiplies the surface, and every item has to be revisited.
 */
public enum Trigger {

    LEFT_CLICK("Left Click"),
    RIGHT_CLICK("Right Click"),
    BLOCK_BREAK("Mining"),
    BLOCK_PLACE("Placing"),

    /** The holder hit something. */
    DAMAGE_ENTITY("Attacking"),

    /** The holder was hit. Fires for armour as well as held items. */
    TAKE_DAMAGE("When Hurt"),

    PROJECTILE_LAUNCH("Firing"),

    /**
     * A projectile fired from this item hit something. Sigil stamps the source
     * item onto the projectile at launch, which is what makes the item
     * identifiable here at all.
     */
    PROJECTILE_HIT("On Hit"),

    /** The F key. The conventional "activate" input for ability items. */
    SWAP_HANDS("Swap Hands"),

    DROP("Dropping"),
    CONSUME_ITEM("Eating"),
    EQUIP("When Equipped"),
    UNEQUIP("When Unequipped"),

    /**
     * Periodic, while held or worn. Only polled when at least one registered
     * ability asks for it.
     */
    TICK("Passive");

    private final String displayName;

    Trigger(String displayName) {
        this.displayName = displayName;
    }

    /**
     * How this reads in item lore.
     * <p>
     * Enum constants make poor player-facing text — "right_click" in a tooltip
     * looks like a leaked internal, which is exactly what it is.
     */
    public String displayName() {
        return displayName;
    }
}
