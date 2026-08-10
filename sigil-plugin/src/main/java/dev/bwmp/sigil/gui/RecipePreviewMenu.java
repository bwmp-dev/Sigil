package dev.bwmp.sigil.gui;

import dev.bwmp.keystone.gui.GuiButton;
import dev.bwmp.keystone.gui.GuiMenu;
import dev.bwmp.keystone.text.LegacyRenderer;
import dev.bwmp.sigil.SigilPlugin;
import dev.bwmp.sigil.api.recipe.Ingredient;
import dev.bwmp.sigil.api.recipe.SigilRecipe;
import dev.bwmp.sigil.item.SigilItem;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Shows one recipe laid out the way its station looks.
 * <p>
 * Ingredients that are themselves custom items are clickable and navigate to
 * their own recipe, so a whole chain — nugget to ingot to tool — can be walked
 * without leaving the menu.
 */
public final class RecipePreviewMenu extends GuiMenu {

    private static final int[] GRID_SLOTS = {10, 11, 12, 19, 20, 21, 28, 29, 30};
    private static final int RESULT_SLOT = 24;

    private final SigilPlugin plugin;
    private final SigilItem item;
    private final int recipeIndex;

    public RecipePreviewMenu(SigilPlugin plugin, SigilItem item, int recipeIndex) {
        super("<dark_gray>Recipe <gray>| <white>" + stripTags(item.definition().displayName()), 5);
        this.plugin = plugin;
        this.item = item;
        this.recipeIndex = recipeIndex;
    }

    @Override
    protected void build() {
        List<SigilRecipe> recipes = item.definition().recipes();
        if (recipes.isEmpty()) {
            set(RESULT_SLOT, GuiButton.display(item.createStack(1)));
            return;
        }

        int index = Math.max(0, Math.min(recipeIndex, recipes.size() - 1));
        SigilRecipe recipe = recipes.get(index);

        switch (recipe.type()) {
            case SHAPED -> renderShaped(recipe);
            case SHAPELESS -> renderFlat(recipe.ingredients());
            default -> renderFlat(recipe.ingredients());
        }

        set(RESULT_SLOT, GuiButton.display(item.createStack(recipe.amount())));
        set(4, GuiButton.display(label(recipe)));

        if (recipes.size() > 1) {
            // Only shown when there is somewhere to go, so the control never
            // looks live while doing nothing.
            if (index > 0) {
                set(36, GuiButton.of(named(Material.ARROW, "<yellow>Previous recipe"),
                        click -> new RecipePreviewMenu(plugin, item, index - 1).open(click.player())));
            }
            if (index < recipes.size() - 1) {
                set(44, GuiButton.of(named(Material.ARROW, "<yellow>Next recipe"),
                        click -> new RecipePreviewMenu(plugin, item, index + 1).open(click.player())));
            }
        }

        set(40, GuiButton.of(named(Material.BARRIER, "<red>Back to items"),
                click -> new ItemBrowserMenu(plugin).open(click.player())));
    }

    private void renderShaped(SigilRecipe recipe) {
        List<String> shape = recipe.shape();
        for (int row = 0; row < Math.min(3, shape.size()); row++) {
            String line = shape.get(row);
            for (int column = 0; column < Math.min(3, line.length()); column++) {
                char symbol = line.charAt(column);
                if (symbol == ' ') {
                    continue;
                }
                Ingredient ingredient = recipe.keys().get(symbol);
                if (ingredient != null) {
                    set(GRID_SLOTS[row * 3 + column], ingredientButton(ingredient));
                }
            }
        }
    }

    private void renderFlat(List<Ingredient> ingredients) {
        for (int index = 0; index < Math.min(GRID_SLOTS.length, ingredients.size()); index++) {
            set(GRID_SLOTS[index], ingredientButton(ingredients.get(index)));
        }
    }

    private GuiButton ingredientButton(Ingredient ingredient) {
        if (ingredient instanceof Ingredient.Custom custom) {
            return plugin.registries().sigilItem(custom.id())
                    .map(referenced -> {
                        ItemStack icon = referenced.createStack(Math.max(1, ingredient.amount()));
                        return GuiButton.of(icon, click -> {
                            if (!referenced.definition().recipes().isEmpty()) {
                                new RecipePreviewMenu(plugin, referenced, 0).open(click.player());
                            }
                        });
                    })
                    .orElseGet(() -> GuiButton.display(missing(custom.id().toString())));
        }

        ItemStack icon = new ItemStack(ingredient.exemplarMaterial(), Math.max(1, ingredient.amount()));
        ItemMeta meta = icon.getItemMeta();
        if (meta != null) {
            meta.setLore(List.of(LegacyRenderer.renderMiniMessage("<dark_gray>" + ingredient.describe())));
            icon.setItemMeta(meta);
        }
        return GuiButton.display(icon);
    }

    private ItemStack missing(String id) {
        ItemStack stack = new ItemStack(Material.BARRIER);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(LegacyRenderer.renderMiniMessage("<red>Missing: <white>" + id));
            meta.setLore(List.of(LegacyRenderer.renderMiniMessage(
                    "<gray>This recipe references an item that is not registered.")));
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private ItemStack label(SigilRecipe recipe) {
        ItemStack stack = new ItemStack(stationIcon(recipe));
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(LegacyRenderer.renderMiniMessage(
                    "<yellow>" + recipe.type().name().toLowerCase(Locale.ROOT).replace('_', ' ')));
            List<String> lore = new ArrayList<>();
            lore.add(LegacyRenderer.renderMiniMessage("<gray>Makes <white>" + recipe.amount()));
            if (recipe.type().isCooking()) {
                lore.add(LegacyRenderer.renderMiniMessage("<gray>Cook time: <white>" + recipe.cookTime() + " ticks"));
            }
            meta.setLore(lore);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private Material stationIcon(SigilRecipe recipe) {
        return switch (recipe.type()) {
            case SHAPED, SHAPELESS -> Material.CRAFTING_TABLE;
            case FURNACE -> Material.FURNACE;
            case BLASTING -> Material.BLAST_FURNACE;
            case SMOKING -> Material.SMOKER;
            case CAMPFIRE -> Material.CAMPFIRE;
            case SMITHING -> Material.SMITHING_TABLE;
            case STONECUTTING -> Material.STONECUTTER;
        };
    }

    private static ItemStack named(Material material, String miniMessageName) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(LegacyRenderer.renderMiniMessage(miniMessageName));
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static String stripTags(String input) {
        return input == null ? "" : input.replaceAll("<[^>]*>", "");
    }
}
