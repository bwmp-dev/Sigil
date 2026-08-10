package dev.bwmp.sigil.item;

import dev.bwmp.sigil.api.item.CustomItem;
import dev.bwmp.sigil.registry.SigilRegistries;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Optional;

/**
 * Turns an ItemStack into the item it represents.
 * <p>
 * The single place that reads the identity key. Keeping it to one consumer is
 * what makes the persistent-data schema cheap to change later, and it means
 * every identity check in the plugin agrees by construction.
 */
public final class ItemResolver {

    private final Keys keys;
    private final SigilRegistries registries;

    public ItemResolver(Keys keys, SigilRegistries registries) {
        this.keys = keys;
        this.registries = registries;
    }

    public Optional<NamespacedKey> idOf(ItemStack stack) {
        PersistentDataContainer container = containerOf(stack);
        if (container == null) {
            return Optional.empty();
        }
        String raw = container.get(keys.id, PersistentDataType.STRING);
        return raw == null ? Optional.empty() : Optional.ofNullable(NamespacedKey.fromString(raw));
    }

    public Optional<CustomItem> resolve(ItemStack stack) {
        return idOf(stack).flatMap(registries::item);
    }

    public boolean isCustom(ItemStack stack) {
        return idOf(stack).isPresent();
    }

    /**
     * True when both stacks are the same custom item. Used by the anvil combine
     * path, where merging two different items would be a duplication bug.
     */
    public boolean sameItem(ItemStack first, ItemStack second) {
        Optional<NamespacedKey> a = idOf(first);
        Optional<NamespacedKey> b = idOf(second);
        return a.isPresent() && a.equals(b);
    }

    public int revisionOf(ItemStack stack) {
        PersistentDataContainer container = containerOf(stack);
        if (container == null) {
            return 0;
        }
        Integer value = container.get(keys.revision, PersistentDataType.INTEGER);
        return value == null ? 0 : value;
    }

    /** Remaining uses, or -1 when the stack carries no counter. */
    public int usesOf(ItemStack stack) {
        PersistentDataContainer container = containerOf(stack);
        if (container == null) {
            return -1;
        }
        Integer value = container.get(keys.uses, PersistentDataType.INTEGER);
        return value == null ? -1 : value;
    }

    public Optional<String> uidOf(ItemStack stack) {
        PersistentDataContainer container = containerOf(stack);
        return container == null
                ? Optional.empty()
                : Optional.ofNullable(container.get(keys.uid, PersistentDataType.STRING));
    }

    private PersistentDataContainer containerOf(ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR || !stack.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = stack.getItemMeta();
        return meta == null ? null : meta.getPersistentDataContainer();
    }
}
