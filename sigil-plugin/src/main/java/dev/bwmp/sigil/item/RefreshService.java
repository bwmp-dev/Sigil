package dev.bwmp.sigil.item;

import dev.bwmp.sigil.registry.SigilRegistries;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;

/**
 * Brings already-owned stacks up to date when a definition changes.
 * <p>
 * Without this, editing an item's name or lore in config only affects newly
 * created copies, and every stack already in a chest stays wrong forever — the
 * behaviour of the design this replaces.
 * <p>
 * The stale check is one integer comparison against persistent data, so the
 * common case of an unchanged stack costs almost nothing and this can safely be
 * called from inventory events.
 */
public final class RefreshService {

    private final ItemResolver resolver;
    private final ItemFactory factory;
    private final SigilRegistries registries;

    public RefreshService(ItemResolver resolver, ItemFactory factory, SigilRegistries registries) {
        this.resolver = resolver;
        this.factory = factory;
        this.registries = registries;
    }

    /** @return true when the stack was re-rendered */
    public boolean refresh(ItemStack stack) {
        Optional<SigilItem> item = resolver.idOf(stack).flatMap(registries::sigilItem);
        if (item.isEmpty()) {
            return false;
        }

        SigilItem resolved = item.get();
        if (resolver.revisionOf(stack) == resolved.definition().revision()) {
            return false;
        }
        return factory.rerender(stack, resolved);
    }

    /** Refreshes everything a player is carrying. Returns how many changed. */
    public int refreshInventory(Player player) {
        int changed = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && refresh(stack)) {
                changed++;
            }
        }
        for (ItemStack stack : player.getInventory().getArmorContents()) {
            if (stack != null && refresh(stack)) {
                changed++;
            }
        }
        return changed;
    }
}
