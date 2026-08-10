package dev.bwmp.sigil.registry;

import dev.bwmp.sigil.api.item.Rarity;
import dev.bwmp.sigil.item.SigilItem;
import org.bukkit.NamespacedKey;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * An immutable view of every registered item and rarity.
 * <p>
 * Reload builds a whole new snapshot and publishes it in one write, so a reader
 * on any thread sees a consistent set and can never observe a half-applied
 * reload. That property is required on Folia, where there is no main thread to
 * confine reads to, and it is faster than the alternative everywhere else.
 */
public final class RegistrySnapshot {

    private final Map<NamespacedKey, SigilItem> items;
    private final Map<String, Rarity> rarities;
    private final List<SigilItem> sortedItems;

    public RegistrySnapshot(Map<NamespacedKey, SigilItem> items, Map<String, Rarity> rarities) {
        this.items = Map.copyOf(items);
        this.rarities = Map.copyOf(rarities);

        // Sorted once here rather than on every menu draw. Rarity order then
        // name, so listings are stable and Epic does not sort before Rare the
        // way an alphabetical sort would.
        this.sortedItems = items.values().stream()
                .sorted(Comparator
                        .comparingInt((SigilItem item) -> rarityOrder(item.definition().rarityId()))
                        .thenComparing(item -> item.id().toString()))
                .toList();
    }

    public static RegistrySnapshot empty() {
        return new RegistrySnapshot(Map.of(), new LinkedHashMap<>());
    }

    public Optional<SigilItem> item(NamespacedKey id) {
        return Optional.ofNullable(items.get(id));
    }

    public List<SigilItem> items() {
        return sortedItems;
    }

    public Optional<Rarity> rarity(String id) {
        return id == null ? Optional.empty() : Optional.ofNullable(rarities.get(id.toLowerCase()));
    }

    /** Never empty: an unknown rarity degrades to a grey placeholder rather than failing. */
    public Rarity rarityOrUnknown(String id) {
        return rarity(id).orElseGet(() -> Rarity.unknown(id == null ? "unknown" : id));
    }

    public List<Rarity> rarities() {
        return rarities.values().stream().sorted(Comparator.comparingInt(Rarity::order)).toList();
    }

    public int size() {
        return items.size();
    }

    private int rarityOrder(String rarityId) {
        return rarity(rarityId).map(Rarity::order).orElse(Integer.MAX_VALUE);
    }
}
