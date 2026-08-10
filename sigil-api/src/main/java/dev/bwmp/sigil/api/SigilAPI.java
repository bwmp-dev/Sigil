package dev.bwmp.sigil.api;

import dev.bwmp.sigil.api.ability.AbilityType;
import dev.bwmp.sigil.api.item.CustomItem;
import dev.bwmp.sigil.api.item.ItemDefinition;
import dev.bwmp.sigil.api.item.Rarity;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.Collection;
import java.util.Optional;

/**
 * Sigil's public entry point, published through Bukkit's service manager.
 * <p>
 * Bukkit's own services manager is used rather than a static holder because it
 * is the one registry that genuinely spans plugin boundaries, and because it
 * makes the dependency explicit and unregisters cleanly.
 */
public interface SigilAPI {

    static Optional<SigilAPI> get() {
        RegisteredServiceProvider<SigilAPI> provider =
                Bukkit.getServicesManager().getRegistration(SigilAPI.class);
        return provider == null ? Optional.empty() : Optional.of(provider.getProvider());
    }

    /** Registers an item defined in code. Config may still override its fields. */
    CustomItem register(Plugin owner, ItemDefinition definition, dev.bwmp.sigil.api.ability.Ability... abilities);

    /**
     * Registers an ability type usable from YAML by any item on the server.
     * The id's namespace must match {@code owner}.
     */
    void registerAbilityType(Plugin owner, NamespacedKey id, AbilityType type);

    Optional<CustomItem> item(NamespacedKey id);

    Collection<CustomItem> items();

    Optional<Rarity> rarity(String id);

    Collection<Rarity> rarities();

    /** The item a stack is, if any. The single supported way to identify one. */
    Optional<CustomItem> resolve(ItemStack stack);

    /** Convenience for {@code resolve(stack).isPresent()}. */
    default boolean isCustom(ItemStack stack) {
        return resolve(stack).isPresent();
    }

    /**
     * Remaining uses on a stack, or -1 when unlimited or not a Sigil item.
     */
    int remainingUses(ItemStack stack);

    /**
     * Re-renders a stack against the current definition if it is stale,
     * preserving uses and any foreign persistent data.
     *
     * @return true when the stack was changed
     */
    boolean refresh(ItemStack stack);
}
