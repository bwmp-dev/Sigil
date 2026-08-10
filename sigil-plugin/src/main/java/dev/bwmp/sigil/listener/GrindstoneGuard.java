package dev.bwmp.sigil.listener;

import dev.bwmp.keystone.compat.Platform;
import dev.bwmp.sigil.item.ItemFactory;
import dev.bwmp.sigil.item.ItemResolver;
import dev.bwmp.sigil.item.SigilItem;
import dev.bwmp.sigil.registry.SigilRegistries;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Method;
import java.util.Optional;

/**
 * Keeps a custom item intact through a grindstone.
 * <p>
 * Vanilla builds the grindstone result as a fresh stack carrying only the
 * material, so without this a player gets back something that looks right but
 * has no identity, no lore and no charges — a broken item they cannot tell is
 * broken.
 * <p>
 * Registered reflectively because {@code PrepareGrindstoneEvent} does not exist
 * in the 1.18 API this module compiles against. Rather than raise the floor for
 * one feature, the event class is probed for and the handler is only wired up
 * where it resolves; on older servers grindstone protection is simply absent,
 * which is reported at startup.
 */
public final class GrindstoneGuard implements Listener {

    private static final String EVENT_CLASS = "org.bukkit.event.inventory.PrepareGrindstoneEvent";

    private final ItemResolver resolver;
    private final SigilRegistries registries;
    private final ItemFactory factory;

    public GrindstoneGuard(ItemResolver resolver, SigilRegistries registries, ItemFactory factory) {
        this.resolver = resolver;
        this.registries = registries;
        this.factory = factory;
    }

    public static boolean isSupported() {
        return Platform.classExists(EVENT_CLASS);
    }

    /** @return true when the handler was registered */
    @SuppressWarnings("unchecked")
    public boolean register(Plugin plugin) {
        if (!isSupported()) {
            return false;
        }
        try {
            Class<? extends Event> eventClass = (Class<? extends Event>) Class.forName(EVENT_CLASS);
            Method setResult = eventClass.getMethod("setResult", ItemStack.class);

            plugin.getServer().getPluginManager().registerEvent(
                    eventClass, this, EventPriority.HIGH,
                    (listener, event) -> handle(event, setResult),
                    plugin, true);
            return true;
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            plugin.getLogger().warning("Grindstone protection could not be enabled: " + exception);
            return false;
        }
    }

    private void handle(Event event, Method setResult) {
        if (!(event instanceof InventoryEvent inventoryEvent)) {
            return;
        }

        Inventory inventory = inventoryEvent.getInventory();
        ItemStack input = firstCustomInput(inventory);
        if (input == null) {
            return;
        }

        Optional<SigilItem> item = resolver.idOf(input).flatMap(registries::sigilItem);
        if (item.isEmpty()) {
            return;
        }

        ItemStack rebuilt = input.clone();
        // The grindstone's actual job still happens: player-applied enchants go.
        rebuilt.getEnchantments().keySet().forEach(rebuilt::removeEnchantment);
        // Re-rendered afterwards so anything the definition itself grants, such
        // as a rarity shimmer, comes back rather than being stripped with them.
        factory.rerender(rebuilt, item.get());

        try {
            setResult.invoke(event, rebuilt);
        } catch (ReflectiveOperationException ignored) {
            // Nothing useful to do here; leaving the vanilla result is no worse
            // than the state before this guard existed.
        }
    }

    private ItemStack firstCustomInput(Inventory inventory) {
        for (int slot = 0; slot < Math.min(2, inventory.getSize()); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (resolver.isCustom(stack)) {
                return stack;
            }
        }
        return null;
    }
}
