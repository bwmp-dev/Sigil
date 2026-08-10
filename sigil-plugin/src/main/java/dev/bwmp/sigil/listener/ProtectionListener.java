package dev.bwmp.sigil.listener;

import dev.bwmp.sigil.config.SigilSettings;
import dev.bwmp.sigil.item.ItemFactory;
import dev.bwmp.sigil.item.ItemResolver;
import dev.bwmp.sigil.item.SigilItem;
import dev.bwmp.sigil.item.UsesService;
import dev.bwmp.sigil.registry.SigilRegistries;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;

/**
 * Keeps vanilla stations from laundering or destroying a custom item's
 * identity.
 * <p>
 * Each rule is per-item with a server-wide default, because the right answer
 * genuinely differs: a decorative material may as well be enchantable, while a
 * balanced weapon should not be.
 */
public final class ProtectionListener implements Listener {

    private final ItemResolver resolver;
    private final SigilRegistries registries;
    private final SigilSettings settings;
    private final UsesService uses;
    private final ItemFactory factory;

    public ProtectionListener(ItemResolver resolver, SigilRegistries registries, SigilSettings settings,
                              UsesService uses, ItemFactory factory) {
        this.resolver = resolver;
        this.registries = registries;
        this.settings = settings;
        this.uses = uses;
        this.factory = factory;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onAnvil(PrepareAnvilEvent event) {
        AnvilInventory inventory = event.getInventory();
        ItemStack first = inventory.getItem(0);
        ItemStack second = inventory.getItem(1);

        Optional<SigilItem> item = resolver.idOf(first).flatMap(registries::sigilItem);
        if (item.isEmpty()) {
            return;
        }
        SigilItem sigilItem = item.get();

        boolean renameAllowed = sigilItem.definition().rules().anvilRename(settings.defaultAnvilRename());
        boolean combineAllowed = sigilItem.definition().rules().anvilCombine(settings.defaultAnvilCombine());

        if (second != null && resolver.sameItem(first, second)) {
            if (!combineAllowed) {
                event.setResult(null);
                return;
            }
            // Charges are summed and capped; the surplus stays on the sacrifice
            // rather than being silently eaten, which an anvil doing arithmetic
            // on a player's items really should not do.
            ItemStack merged = uses.merge(first, second, sigilItem);
            if (merged == null) {
                event.setResult(null);
                return;
            }
            event.setResult(merged);
            inventory.setRepairCost(settings.anvilCombineCost());
            return;
        }

        if (!renameAllowed) {
            String renameText = inventory.getRenameText();
            String currentName = first.hasItemMeta() && first.getItemMeta() != null
                    ? first.getItemMeta().getDisplayName()
                    : "";
            // A rename is only blocked when the text actually differs; putting
            // an item in an anvil and taking it out unchanged should still work.
            if (renameText != null && !renameText.isEmpty() && !renameText.equals(stripColour(currentName))) {
                event.setResult(null);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepareEnchant(PrepareItemEnchantEvent event) {
        if (!enchantingAllowed(event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEnchant(EnchantItemEvent event) {
        if (!enchantingAllowed(event.getItem())) {
            event.setCancelled(true);
        }
    }

    /**
     * Smithing on a custom item is blocked unless a Sigil recipe produced it.
     * Vanilla smithing would otherwise rewrite the base material and strip the
     * item to something unrecognisable.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onSmithing(PrepareSmithingEvent event) {
        ItemStack result = event.getResult();
        if (result != null && resolver.isCustom(result)) {
            return;
        }
        for (ItemStack stack : event.getInventory().getContents()) {
            if (resolver.isCustom(stack)) {
                event.setResult(null);
                return;
            }
        }
    }

    private boolean enchantingAllowed(ItemStack stack) {
        return resolver.idOf(stack)
                .flatMap(registries::sigilItem)
                .map(item -> item.definition().rules().enchanting(settings.defaultEnchanting()))
                .orElse(true);
    }

    private static String stripColour(String input) {
        return input == null ? "" : input.replaceAll("§[0-9a-fk-orA-FK-OR]", "");
    }
}
