package dev.bwmp.sigil.loot;

import org.bukkit.NamespacedKey;

public record LootEntry(
        NamespacedKey itemId,
        NamespacedKey tableId,
        NamespacedKey sourceId,
        double chance,
        int minAmount,
        int maxAmount) {

    public boolean matches(NamespacedKey table, NamespacedKey source) {
        return (tableId == null || tableId.equals(table))
                && (sourceId == null || sourceId.equals(source));
    }
}
