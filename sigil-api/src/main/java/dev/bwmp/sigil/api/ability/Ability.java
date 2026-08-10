package dev.bwmp.sigil.api.ability;

import java.util.Set;

/**
 * A behaviour attached to an item.
 * <p>
 * An ability owns what it does and declares when it should run. It does
 * <em>not</em> enforce its own cooldown, check its own permission, or decrement
 * its own uses — the dispatcher does all three, so forgetting one is not
 * possible. That was a real failure mode in the design this replaces, where an
 * ability that omitted its cooldown call silently had no cooldown.
 * <p>
 * Implementations should be stateless. One instance is shared by every stack of
 * the item; per-player or per-item state belongs in the item's persistent data
 * or in the plugin that registered the ability.
 */
public interface Ability {

    AbilityMeta meta();

    /** When this ability should run. An empty set means it never fires. */
    Set<TriggerBinding> triggers();

    /**
     * Runs the ability.
     *
     * @return what happened; see {@link ActionResult} for why this is not a
     *         boolean
     */
    ActionResult execute(AbilityContext context);
}
