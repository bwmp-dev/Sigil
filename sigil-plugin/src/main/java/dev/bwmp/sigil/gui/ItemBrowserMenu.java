package dev.bwmp.sigil.gui;

import dev.bwmp.keystone.gui.GuiButton;
import dev.bwmp.keystone.gui.PaginatedMenu;
import dev.bwmp.keystone.text.LegacyRenderer;
import dev.bwmp.sigil.SigilPlugin;
import dev.bwmp.sigil.api.item.Rarity;
import dev.bwmp.sigil.item.SigilItem;
import dev.bwmp.sigil.permission.Permissions;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Browses every registered item.
 * <p>
 * Entries are the items' own real stacks, so models, lore and rarity colours
 * appear exactly as they do in game rather than being approximated.
 */
public final class ItemBrowserMenu extends PaginatedMenu<SigilItem> {

    private final SigilPlugin plugin;
    private String rarityFilter;

    public ItemBrowserMenu(SigilPlugin plugin) {
        super("<dark_gray>Sigil <gray>| <white>Items", 6);
        this.plugin = plugin;
    }

    @Override
    protected List<SigilItem> contents() {
        List<SigilItem> items = new ArrayList<>();
        for (SigilItem item : plugin.registries().sigilItems()) {
            if (!item.definition().enabled()) {
                continue;
            }
            if (rarityFilter != null && !rarityFilter.equalsIgnoreCase(item.definition().rarityId())) {
                continue;
            }
            items.add(item);
        }
        return items;
    }

    @Override
    protected GuiButton renderEntry(SigilItem item) {
        ItemStack icon = item.createStack(1);
        return GuiButton.of(icon, click -> {
            Player viewer = click.player();
            if (click.isShift() && viewer.hasPermission(Permissions.COMMAND_ROOT + ".give")) {
                viewer.getInventory().addItem(item.createStack(1));
                return;
            }
            if (item.definition().recipes().isEmpty()) {
                return;
            }
            new RecipePreviewMenu(plugin, item, 0).open(viewer);
        });
    }

    @Override
    protected void decorateNavigation() {
        int row = (rows() - 1) * 9;

        set(row + 2, GuiButton.of(
                icon(Material.HOPPER, rarityFilter == null
                        ? "<yellow>Filter: <white>all"
                        : "<yellow>Filter: <white>" + rarityFilter),
                click -> {
                    cycleFilter();
                    refresh();
                }));

        set(row + 6, GuiButton.display(describeHelp()));
    }

    private void cycleFilter() {
        List<Rarity> rarities = new ArrayList<>(plugin.registries().rarities());
        if (rarities.isEmpty()) {
            return;
        }
        if (rarityFilter == null) {
            rarityFilter = rarities.get(0).id();
            return;
        }
        for (int index = 0; index < rarities.size(); index++) {
            if (rarities.get(index).id().equalsIgnoreCase(rarityFilter)) {
                // Wraps back to "all" past the end, so the control cycles
                // rather than dead-ending on the last rarity.
                rarityFilter = index + 1 < rarities.size() ? rarities.get(index + 1).id() : null;
                return;
            }
        }
        rarityFilter = null;
    }

    private ItemStack describeHelp() {
        ItemStack stack = new ItemStack(Material.BOOK);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(LegacyRenderer.renderMiniMessage("<yellow>How to use"));
            meta.setLore(List.of(
                    LegacyRenderer.renderMiniMessage("<gray>Click an item to see its recipe."),
                    LegacyRenderer.renderMiniMessage("<gray>Shift-click to give yourself one.")));
            stack.setItemMeta(meta);
        }
        return stack;
    }
}
