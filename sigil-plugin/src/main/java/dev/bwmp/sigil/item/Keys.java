package dev.bwmp.sigil.item;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/**
 * The persistent data keys Sigil writes onto stacks.
 * <p>
 * Deliberately few. Identity is one string; everything else is derived from the
 * definition at render time. The design this replaces stored the material, name
 * and rarity in a hash and used that as the id, which meant editing any of the
 * three in config silently orphaned every existing copy.
 */
public final class Keys {

    /** The item's namespaced id. The only identity. */
    public final NamespacedKey id;

    /** Definition revision the stack was last rendered against. */
    public final NamespacedKey revision;

    /** Remaining uses. Absent means unlimited. */
    public final NamespacedKey uses;

    /**
     * A per-stack UUID.
     * <p>
     * Written for limited-use items so that two otherwise identical stacks
     * never merge — differing persistent data is what prevents stacking, and it
     * works on every supported version, unlike {@code setMaxStackSize} which
     * only exists from 1.20.5.
     */
    public final NamespacedKey uid;

    /**
     * The display name Sigil itself last wrote onto the stack.
     * <p>
     * Only ever read as an inequality probe: a display name that differs from
     * this one was put there by someone else, which on a renameable item means
     * a player at an anvil. Without it a re-render cannot tell a name it owns
     * from a name it must not overwrite.
     */
    public final NamespacedKey name;

    /** Stamped on projectiles so a hit can be traced back to the item that fired it. */
    public final NamespacedKey projectileSource;

    public Keys(Plugin plugin) {
        this.id = new NamespacedKey(plugin, "id");
        this.revision = new NamespacedKey(plugin, "rev");
        this.uses = new NamespacedKey(plugin, "uses");
        this.uid = new NamespacedKey(plugin, "uid");
        this.name = new NamespacedKey(plugin, "name");
        this.projectileSource = new NamespacedKey(plugin, "projectile_source");
    }
}
