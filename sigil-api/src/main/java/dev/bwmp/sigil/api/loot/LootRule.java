package dev.bwmp.sigil.api.loot;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.EntityType;

/**
 * One way a registered item can enter the world without being crafted.
 * <p>
 * The selectors are ANDed, and at least one must be set. A rule naming both a
 * table and a source fires only where both hold, which is what makes
 * structure-specific rewards possible when several structures share one vanilla
 * loot table.
 *
 * @param item             the registered item to drop
 * @param table            vanilla loot table, e.g. {@code minecraft:chests/end_city_treasure}
 * @param source           a marker written onto a generated chest, e.g. {@code terra:valkyrie_temple}
 * @param mob              an entity type whose death drops this, or null
 * @param chance           greater than 0 and at most 1, rolled independently
 * @param minAmount        at least 1
 * @param maxAmount        at least {@code minAmount}
 * @param requirePlayerKill for mob rules: whether a mob killed by something
 *                          other than a player still drops it. Defaults on,
 *                          because otherwise a mob farm produces a boss weapon
 *                          nobody fought for
 * @param lootingBonus     added to {@code chance} per level of Looting on the
 *                         killing weapon; 0 to ignore the enchantment
 */
public record LootRule(
        NamespacedKey item,
        NamespacedKey table,
        NamespacedKey source,
        EntityType mob,
        double chance,
        int minAmount,
        int maxAmount,
        boolean requirePlayerKill,
        double lootingBonus) {

    public LootRule {
        if (item == null) {
            throw new IllegalArgumentException("a loot rule needs an item id");
        }
        if (table == null && source == null && mob == null) {
            throw new IllegalArgumentException("loot rule for " + item + " needs a table, source or mob");
        }
        if (chance <= 0.0 || chance > 1.0) {
            throw new IllegalArgumentException("loot rule for " + item + " needs 0 < chance <= 1, got " + chance);
        }
        minAmount = Math.max(1, minAmount);
        maxAmount = Math.max(minAmount, maxAmount);
        lootingBonus = Math.max(0.0, lootingBonus);
    }

    /** A chest rule: rolled when a loot table generates. */
    public static LootRule chest(NamespacedKey item, NamespacedKey table, double chance, int min, int max) {
        return new LootRule(item, table, null, null, chance, min, max, true, 0.0);
    }

    /** A rule keyed to a structure marker rather than to a vanilla table. */
    public static LootRule source(NamespacedKey item, NamespacedKey source, double chance, int min, int max) {
        return new LootRule(item, null, source, null, chance, min, max, true, 0.0);
    }

    /** A mob-death rule, player kills only. */
    public static LootRule mob(NamespacedKey item, EntityType mob, double chance) {
        return new LootRule(item, null, null, mob, chance, 1, 1, true, 0.0);
    }

    public boolean isMobRule() {
        return mob != null;
    }

    /** The effective chance for a kill made with {@code lootingLevel}. */
    public double chanceWithLooting(int lootingLevel) {
        return Math.min(1.0, chance + lootingBonus * Math.max(0, lootingLevel));
    }
}
