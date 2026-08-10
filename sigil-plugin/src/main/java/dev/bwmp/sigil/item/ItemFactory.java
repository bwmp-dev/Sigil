package dev.bwmp.sigil.item;

import dev.bwmp.keystone.compat.ItemAdapter;
import dev.bwmp.sigil.api.item.ItemDefinition;
import dev.bwmp.sigil.api.item.Rarity;
import dev.bwmp.sigil.config.SigilSettings;
import dev.bwmp.sigil.registry.SigilRegistries;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;
import java.util.UUID;

/** Builds and re-renders stacks from definitions. */
public final class ItemFactory {

    private final Keys keys;
    private final ItemAdapter adapter;
    private final LoreRenderer lore;
    private final SigilRegistries registries;
    private final ItemResolver resolver;
    private final SigilSettings settings;

    public ItemFactory(Keys keys, ItemAdapter adapter, LoreRenderer lore,
                       SigilRegistries registries, ItemResolver resolver, SigilSettings settings) {
        this.keys = keys;
        this.adapter = adapter;
        this.lore = lore;
        this.registries = registries;
        this.resolver = resolver;
        this.settings = settings;
    }

    public ItemResolver resolver() {
        return resolver;
    }

    public ItemStack create(SigilItem item, int amount) {
        ItemDefinition definition = item.definition();
        ItemStack stack = new ItemStack(definition.base(), Math.max(1, amount));
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }

        PersistentDataContainer data = meta.getPersistentDataContainer();
        data.set(keys.id, PersistentDataType.STRING, definition.id().toString());
        data.set(keys.revision, PersistentDataType.INTEGER, definition.revision());

        if (definition.uses().limited()) {
            data.set(keys.uses, PersistentDataType.INTEGER, definition.uses().max());
            // Unique per stack so two never merge and share one counter. This
            // works on every supported version; setMaxStackSize only exists
            // from 1.20.5 and is applied below purely so the tooltip agrees.
            data.set(keys.uid, PersistentDataType.STRING, UUID.randomUUID().toString());
            adapter.applyMaxStackSize(meta, 1);
        }

        applyPresentation(meta, definition, item, definition.uses().limited() ? definition.uses().max() : -1);
        stack.setItemMeta(meta);
        return stack;
    }

    /**
     * Re-renders an existing stack in place against the current definition,
     * preserving uses, uid, a player's own name for it, and any persistent data
     * written by other plugins.
     */
    public boolean rerender(ItemStack stack, SigilItem item) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return false;
        }

        ItemDefinition definition = item.definition();
        int remaining = resolver.usesOf(stack);
        String chosenName = playerChosenName(meta, definition);

        // Clear only what this renderer owns. Wiping the whole container would
        // discard other plugins' data, and clearing enchants unconditionally
        // would strip player-applied ones on an enchantable item.
        meta.setLore(null);
        meta.getPersistentDataContainer().set(keys.revision, PersistentDataType.INTEGER, definition.revision());

        applyPresentation(meta, definition, item, remaining);
        if (chosenName != null) {
            // Put the player's name back over the definition's. A re-render
            // happens on every spent charge, so without this a limited-use item
            // that allows renaming loses its label the first time it is used —
            // which is exactly when the player wants to keep track of it.
            meta.setDisplayName(chosenName);
        }
        stack.setItemMeta(meta);

        if (stack.getType() != definition.base()) {
            stack.setType(definition.base());
        }
        return true;
    }

    /**
     * The name a player gave this stack, or null when the name on it is Sigil's
     * own and may be re-rendered freely.
     */
    private String playerChosenName(ItemMeta meta, ItemDefinition definition) {
        if (!definition.rules().anvilRename(settings.defaultAnvilRename())) {
            // An item that refuses renaming has no player name worth keeping,
            // and resetting one that arrived some other way is the point of
            // that rule rather than a side effect of it.
            return null;
        }

        String stamped = meta.getPersistentDataContainer().get(keys.name, PersistentDataType.STRING);
        if (stamped == null) {
            // Rendered before Sigil stamped names, so there is nothing to
            // compare against. Assuming "player" here would freeze the stack's
            // name against every future config change, which is the failure
            // RefreshService exists to prevent; let this render set the stamp
            // and honour renames from then on.
            return null;
        }

        String current = meta.hasDisplayName() ? meta.getDisplayName() : null;
        return current != null && !current.equals(stamped) ? current : null;
    }

    private void applyPresentation(ItemMeta meta, ItemDefinition definition, SigilItem item, int remainingUses) {
        String rendered = lore.renderName(definition);
        meta.setDisplayName(rendered);
        // Stamped every render so the probe above always compares against what
        // Sigil most recently wrote, not what it wrote when the stack was made.
        meta.getPersistentDataContainer().set(keys.name, PersistentDataType.STRING, rendered);
        meta.setLore(lore.renderLore(definition, item.abilities(), remainingUses));
        adapter.applyModel(meta, definition.model(), definition.customModelData());

        Rarity rarity = registries.rarityOrUnknown(definition.rarityId());
        if (rarity.glow() && meta.getEnchants().isEmpty()) {
            // A level-zero enchant is the standard way to get the shimmer
            // without a real effect; the flag hides the meaningless line.
            meta.addEnchant(Enchantment.LURE, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
    }

    /** Uses remaining on a stack, or -1 when unlimited. */
    public int remainingUses(ItemStack stack) {
        return resolver.usesOf(stack);
    }

    public Keys keys() {
        return keys;
    }

    public ItemAdapter adapter() {
        return adapter;
    }

    public List<String> renderLoreFor(SigilItem item, int remainingUses) {
        return lore.renderLore(item.definition(), item.abilities(), remainingUses);
    }
}
