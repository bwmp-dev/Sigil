package dev.bwmp.sigil.registry;

import dev.bwmp.sigil.api.ability.AbilityType;
import dev.bwmp.sigil.api.item.CustomItem;
import dev.bwmp.sigil.api.item.Rarity;
import dev.bwmp.sigil.item.SigilItem;
// Imported at its real coordinates. Shade rewrites these references to
// dev.bwmp.sigil.libs.keystone.* at package time; writing the relocated name
// in source would simply not compile.
import dev.bwmp.keystone.config.Snapshot;
import dev.bwmp.keystone.registry.OwnedRegistry;
import org.bukkit.NamespacedKey;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Holds the live snapshot and the extension registry for ability types.
 * <p>
 * Items live in a swapped snapshot because they are rebuilt wholesale on
 * reload. Ability types live in an owned registry because they arrive from
 * other plugins at arbitrary times and must be dropped when those plugins
 * disable.
 */
public final class SigilRegistries {

    private final Snapshot<RegistrySnapshot> snapshot = new Snapshot<>(RegistrySnapshot.empty());
    private final OwnedRegistry<AbilityType> abilityTypes;

    public SigilRegistries(OwnedRegistry<AbilityType> abilityTypes) {
        this.abilityTypes = abilityTypes;
    }

    public RegistrySnapshot current() {
        return snapshot.get();
    }

    /** Publishes a rebuilt registry. Readers see old or new, never a mixture. */
    public void publish(RegistrySnapshot replacement) {
        snapshot.set(replacement);
    }

    public Optional<CustomItem> item(NamespacedKey id) {
        return current().item(id).map(item -> item);
    }

    public Optional<SigilItem> sigilItem(NamespacedKey id) {
        return current().item(id);
    }

    public Collection<CustomItem> items() {
        return List.copyOf(current().items());
    }

    public List<SigilItem> sigilItems() {
        return current().items();
    }

    public Optional<Rarity> rarity(String id) {
        return current().rarity(id);
    }

    public Rarity rarityOrUnknown(String id) {
        return current().rarityOrUnknown(id);
    }

    public Collection<Rarity> rarities() {
        return current().rarities();
    }

    public OwnedRegistry<AbilityType> abilityTypes() {
        return abilityTypes;
    }

    public Optional<AbilityType> abilityType(NamespacedKey id) {
        return abilityTypes.get(id);
    }
}
