package dev.bwmp.sigil.listener;

import dev.bwmp.sigil.api.event.CustomItemCraftEvent;
import dev.bwmp.sigil.config.SigilSettings;
import dev.bwmp.sigil.item.ItemResolver;
import dev.bwmp.sigil.item.SigilItem;
import dev.bwmp.sigil.recipe.RecipeService;
import dev.bwmp.sigil.registry.SigilRegistries;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;

import java.util.Optional;

/**
 * Decides what the crafting grid actually produces.
 * <p>
 * Vanilla has already matched a permissive recipe by the time this runs; its
 * job is to confirm the match is genuine and to substitute a freshly rendered
 * result. It also guards the reverse case — a vanilla recipe trying to consume
 * a Sigil item as though it were the plain material it is built from.
 */
public final class CraftingListener implements Listener {

    private final RecipeService recipes;
    private final ItemResolver resolver;
    private final SigilRegistries registries;
    private final SigilSettings settings;

    public CraftingListener(RecipeService recipes, ItemResolver resolver,
                            SigilRegistries registries, SigilSettings settings) {
        this.recipes = recipes;
        this.resolver = resolver;
        this.registries = registries;
        this.settings = settings;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepare(PrepareItemCraftEvent event) {
        CraftingInventory inventory = event.getInventory();
        Recipe recipe = event.getRecipe();
        ItemStack[] matrix = inventory.getMatrix();

        if (isOurRecipe(recipe)) {
            // Vanilla matched on base materials only. Confirm the ingredients
            // really are what the recipe asks for, and pick between recipes
            // that share a shape but differ in custom ingredients - something
            // vanilla cannot express at all.
            Optional<RecipeService.Entry> match = recipes.strictMatch(matrix);
            if (match.isEmpty()) {
                inventory.setResult(null);
                return;
            }
            RecipeService.Entry entry = match.get();
            inventory.setResult(entry.item().createStack(entry.recipe().amount()));
            return;
        }

        // A vanilla recipe. Block it if it would swallow a custom item as
        // though it were the ordinary material underneath - otherwise an Aether
        // Ingot crafts into an iron block and its identity is simply gone.
        if (usesProtectedCustomItem(matrix)) {
            inventory.setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        CraftingInventory inventory = event.getInventory();
        ItemStack[] matrix = inventory.getMatrix();

        if (!isOurRecipe(event.getRecipe())) {
            if (usesProtectedCustomItem(matrix)) {
                event.setCancelled(true);
            }
            return;
        }

        // Re-validated rather than trusted: the prepare result is client-facing
        // and a desync between the two is exactly where duplication bugs live.
        Optional<RecipeService.Entry> match = recipes.strictMatch(matrix);
        if (match.isEmpty()) {
            event.setCancelled(true);
            return;
        }

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        RecipeService.Entry entry = match.get();
        ItemStack result = entry.item().createStack(entry.recipe().amount());

        CustomItemCraftEvent craftEvent = new CustomItemCraftEvent(player, entry.item(), result);
        player.getServer().getPluginManager().callEvent(craftEvent);
        if (craftEvent.isCancelled()) {
            event.setCancelled(true);
            return;
        }
        event.setCurrentItem(craftEvent.getResult());
    }

    private boolean isOurRecipe(Recipe recipe) {
        if (recipe instanceof org.bukkit.Keyed keyed) {
            return recipes.isOurs(keyed.getKey());
        }
        return false;
    }

    private boolean usesProtectedCustomItem(ItemStack[] matrix) {
        if (matrix == null) {
            return false;
        }
        for (ItemStack stack : matrix) {
            Optional<SigilItem> item = resolver.idOf(stack).flatMap(registries::sigilItem);
            if (item.isPresent()
                    && !item.get().definition().rules().vanillaRecipes(settings.defaultVanillaRecipes())) {
                return true;
            }
        }
        return false;
    }
}
