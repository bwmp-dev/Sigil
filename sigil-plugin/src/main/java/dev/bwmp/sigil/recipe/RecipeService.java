package dev.bwmp.sigil.recipe;

import dev.bwmp.keystone.compat.Platform;
import dev.bwmp.keystone.config.LoadReport;
import dev.bwmp.sigil.api.recipe.Ingredient;
import dev.bwmp.sigil.api.recipe.RecipeType;
import dev.bwmp.sigil.api.recipe.SigilRecipe;
import dev.bwmp.sigil.item.ItemResolver;
import dev.bwmp.sigil.item.SigilItem;
import dev.bwmp.sigil.registry.RegistrySnapshot;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.BlastingRecipe;
import org.bukkit.inventory.CampfireRecipe;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.SmokingRecipe;
import org.bukkit.inventory.StonecuttingRecipe;
import org.bukkit.plugin.Plugin;

import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Registers Sigil recipes with the server and decides what they actually
 * produce.
 * <p>
 * The approach is deliberately hybrid. A real Bukkit recipe is registered so
 * that the recipe book, shift-click bulk crafting and ingredient consumption
 * all work natively — but with <em>permissive</em> ingredients matching only
 * the base material. Exact matching through {@code RecipeChoice.ExactChoice}
 * would be too strict to survive a lore refresh or a differing use count.
 * <p>
 * Vanilla therefore only opens the door; {@link #strictMatch} decides. That is
 * also what makes conflicts resolvable: two recipes with the same shape but
 * different custom ingredients are indistinguishable to vanilla, and Sigil
 * picks between them itself.
 */
public final class RecipeService {

    private final Plugin plugin;
    private final ItemResolver resolver;
    private final List<NamespacedKey> registered = new ArrayList<>();
    private final List<Entry> craftingRecipes = new ArrayList<>();

    /** A registered recipe paired with the item it makes. */
    public record Entry(SigilRecipe recipe, SigilItem item) {
    }

    public RecipeService(Plugin plugin, ItemResolver resolver) {
        this.plugin = plugin;
        this.resolver = resolver;
    }

    public void registerAll(RegistrySnapshot snapshot, LoadReport report) {
        unregisterAll();

        boolean smithingSupported = Platform.classExists("org.bukkit.inventory.SmithingTransformRecipe");

        for (SigilItem item : snapshot.items()) {
            List<SigilRecipe> recipes = item.definition().recipes();
            for (int index = 0; index < recipes.size(); index++) {
                SigilRecipe recipe = recipes.get(index);
                NamespacedKey key = new NamespacedKey(plugin,
                        item.id().getNamespace() + "__" + item.id().getKey() + "__" + index);
                try {
                    register(key, recipe, item, smithingSupported, report);
                } catch (RuntimeException | LinkageError exception) {
                    report.error(item.id().toString(), "recipe " + index + " could not be registered: " + exception);
                }
            }
        }
    }

    private void register(NamespacedKey key, SigilRecipe recipe, SigilItem item,
                          boolean smithingSupported, LoadReport report) {
        ItemStack result = item.createStack(recipe.amount());

        switch (recipe.type()) {
            case SHAPED -> {
                ShapedRecipe shaped = new ShapedRecipe(key, result);
                List<String> rows = padShape(recipe.shape());
                shaped.shape(rows.toArray(new String[0]));
                recipe.keys().forEach((symbol, ingredient) ->
                        shaped.setIngredient(symbol, choiceFor(ingredient)));
                addRecipe(key, shaped);
                craftingRecipes.add(new Entry(recipe, item));
            }
            case SHAPELESS -> {
                ShapelessRecipe shapeless = new ShapelessRecipe(key, result);
                for (Ingredient ingredient : recipe.ingredients()) {
                    // amount means "this many slots of it" for a shapeless
                    // recipe, since each slot contributes one item per craft.
                    for (int count = 0; count < ingredient.amount(); count++) {
                        shapeless.addIngredient(choiceFor(ingredient));
                    }
                }
                addRecipe(key, shapeless);
                craftingRecipes.add(new Entry(recipe, item));
            }
            case FURNACE -> addRecipe(key, new FurnaceRecipe(key, result,
                    choiceFor(recipe.ingredients().get(0)), recipe.experience(), recipe.cookTime()));
            case BLASTING -> addRecipe(key, new BlastingRecipe(key, result,
                    choiceFor(recipe.ingredients().get(0)), recipe.experience(), recipe.cookTime()));
            case SMOKING -> addRecipe(key, new SmokingRecipe(key, result,
                    choiceFor(recipe.ingredients().get(0)), recipe.experience(), recipe.cookTime()));
            case CAMPFIRE -> addRecipe(key, new CampfireRecipe(key, result,
                    choiceFor(recipe.ingredients().get(0)), recipe.experience(), recipe.cookTime()));
            case STONECUTTING -> addRecipe(key, new StonecuttingRecipe(key, result,
                    choiceFor(recipe.ingredients().get(0))));
            case SMITHING -> {
                if (!smithingSupported) {
                    // Skipped rather than registered as the older two-slot
                    // SmithingRecipe, whose semantics differ enough that
                    // silently substituting it would be a bug factory.
                    report.downgrade(item.id().toString(),
                            "smithing recipe skipped; SmithingTransformRecipe needs 1.19.4 or newer");
                    return;
                }
                registerSmithing(key, recipe, result, report, item);
            }
            default -> report.warn(item.id().toString(), "unsupported recipe type " + recipe.type());
        }
    }

    /**
     * Built reflectively because sigil-plugin compiles against the 1.18 API,
     * where {@code SmithingTransformRecipe} does not exist. The capability was
     * already probed before we got here.
     */
    private void registerSmithing(NamespacedKey key, SigilRecipe recipe, ItemStack result,
                                  LoadReport report, SigilItem item) {
        try {
            Class<?> type = Class.forName("org.bukkit.inventory.SmithingTransformRecipe");
            Constructor<?> constructor = type.getConstructor(
                    NamespacedKey.class, ItemStack.class,
                    RecipeChoice.class, RecipeChoice.class, RecipeChoice.class);

            int offset = recipe.hasSmithingTemplate() ? 1 : 0;
            // Looked up by name because the constant postdates the 1.18 API
            // this module compiles against. Safe here: we only reach this
            // method once SmithingTransformRecipe was found, which is 1.19.4+,
            // and the template material arrived in 1.20 alongside it.
            Material defaultTemplate = Material.matchMaterial("NETHERITE_UPGRADE_SMITHING_TEMPLATE");
            if (defaultTemplate == null) {
                report.downgrade(item.id().toString(),
                        "smithing recipe skipped; this server has no smithing templates");
                return;
            }
            RecipeChoice template = offset == 1
                    ? choiceFor(recipe.ingredients().get(0))
                    : new RecipeChoice.MaterialChoice(defaultTemplate);
            RecipeChoice base = choiceFor(recipe.ingredients().get(offset));
            RecipeChoice addition = choiceFor(recipe.ingredients().get(offset + 1));

            Object built = constructor.newInstance(key, result, template, base, addition);
            addRecipe(key, (org.bukkit.inventory.Recipe) built);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            report.error(item.id().toString(), "smithing recipe failed to register: " + exception);
        }
    }

    private void addRecipe(NamespacedKey key, org.bukkit.inventory.Recipe recipe) {
        Bukkit.addRecipe(recipe);
        registered.add(key);
    }

    private RecipeChoice choiceFor(Ingredient ingredient) {
        if (ingredient instanceof Ingredient.Tag tag) {
            return tag.materials().isEmpty()
                    ? new RecipeChoice.MaterialChoice(Material.BARRIER)
                    : new RecipeChoice.MaterialChoice(tag.materials());
        }
        if (ingredient instanceof Ingredient.AnyOf anyOf) {
            List<Material> materials = new ArrayList<>();
            for (Ingredient option : anyOf.options()) {
                Material material = option.exemplarMaterial();
                if (material != null && material != Material.AIR && !materials.contains(material)) {
                    materials.add(material);
                }
            }
            return new RecipeChoice.MaterialChoice(materials.isEmpty() ? List.of(Material.BARRIER) : materials);
        }
        Material material = ingredient.exemplarMaterial();
        return new RecipeChoice.MaterialChoice(material == null ? Material.BARRIER : material);
    }

    private List<String> padShape(List<String> shape) {
        List<String> rows = new ArrayList<>();
        int width = 0;
        for (String row : shape) {
            width = Math.max(width, row == null ? 0 : row.length());
        }
        width = Math.max(1, Math.min(3, width));

        for (String row : shape) {
            String value = row == null ? "" : row;
            if (value.length() > width) {
                value = value.substring(0, width);
            }
            rows.add(String.format("%-" + width + "s", value));
            if (rows.size() == 3) {
                break;
            }
        }
        return rows;
    }

    /**
     * The real match, run against the crafting grid after vanilla has accepted
     * one of our recipes.
     *
     * @return the entry whose ingredients the matrix genuinely satisfies
     */
    public Optional<Entry> strictMatch(ItemStack[] matrix) {
        if (matrix == null) {
            return Optional.empty();
        }
        for (Entry entry : craftingRecipes) {
            if (entry.recipe().type() == RecipeType.SHAPED) {
                if (matchesShaped(entry.recipe(), matrix)) {
                    return Optional.of(entry);
                }
            } else if (entry.recipe().type() == RecipeType.SHAPELESS && matchesShapeless(entry.recipe(), matrix)) {
                return Optional.of(entry);
            }
        }
        return Optional.empty();
    }

    private boolean matchesShaped(SigilRecipe recipe, ItemStack[] matrix) {
        int side = matrix.length == 4 ? 2 : 3;
        if (matrix.length != side * side) {
            return false;
        }

        List<String> shape = padShape(recipe.shape());
        int shapeHeight = shape.size();
        int shapeWidth = shape.isEmpty() ? 0 : shape.get(0).length();
        if (shapeHeight > side || shapeWidth > side) {
            return false;
        }

        // Vanilla lets a pattern sit anywhere in the grid, so every offset has
        // to be tried rather than assuming the top-left corner.
        for (int rowOffset = 0; rowOffset + shapeHeight <= side; rowOffset++) {
            for (int columnOffset = 0; columnOffset + shapeWidth <= side; columnOffset++) {
                if (matchesAt(recipe, shape, matrix, side, rowOffset, columnOffset)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean matchesAt(SigilRecipe recipe, List<String> shape, ItemStack[] matrix,
                              int side, int rowOffset, int columnOffset) {
        for (int row = 0; row < side; row++) {
            for (int column = 0; column < side; column++) {
                ItemStack slot = matrix[row * side + column];

                int shapeRow = row - rowOffset;
                int shapeColumn = column - columnOffset;
                boolean insideShape = shapeRow >= 0 && shapeRow < shape.size()
                        && shapeColumn >= 0 && shapeColumn < shape.get(shapeRow).length();

                if (!insideShape) {
                    if (isPresent(slot)) {
                        return false;
                    }
                    continue;
                }

                char symbol = shape.get(shapeRow).charAt(shapeColumn);
                if (symbol == ' ') {
                    if (isPresent(slot)) {
                        return false;
                    }
                    continue;
                }

                Ingredient ingredient = recipe.keys().get(symbol);
                if (ingredient == null || !satisfies(ingredient, slot)) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean matchesShapeless(SigilRecipe recipe, ItemStack[] matrix) {
        List<ItemStack> present = new ArrayList<>();
        for (ItemStack stack : matrix) {
            if (isPresent(stack)) {
                present.add(stack);
            }
        }

        List<Ingredient> required = new ArrayList<>();
        for (Ingredient ingredient : recipe.ingredients()) {
            for (int count = 0; count < ingredient.amount(); count++) {
                required.add(ingredient);
            }
        }
        if (present.size() != required.size()) {
            return false;
        }

        boolean[] used = new boolean[present.size()];
        for (Ingredient ingredient : required) {
            boolean matched = false;
            for (int index = 0; index < present.size(); index++) {
                if (!used[index] && satisfies(ingredient, present.get(index))) {
                    used[index] = true;
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                return false;
            }
        }
        return true;
    }

    /** The identity check: a custom ingredient is matched by id, never by look. */
    public boolean satisfies(Ingredient ingredient, ItemStack stack) {
        if (!isPresent(stack)) {
            return false;
        }

        if (ingredient instanceof Ingredient.Vanilla vanilla) {
            // A custom item must not satisfy a vanilla slot, or an Aether Ingot
            // would be accepted anywhere an iron ingot is.
            return stack.getType() == vanilla.material() && !resolver.isCustom(stack);
        }
        if (ingredient instanceof Ingredient.Custom custom) {
            return resolver.idOf(stack).map(custom.id()::equals).orElse(false);
        }
        if (ingredient instanceof Ingredient.Tag tag) {
            // Same rule as a vanilla slot: a custom item must not satisfy it,
            // or an Aether Ingot would count as any old iron ingot.
            return tag.contains(stack.getType()) && !resolver.isCustom(stack);
        }
        if (ingredient instanceof Ingredient.AnyOf anyOf) {
            for (Ingredient option : anyOf.options()) {
                if (satisfies(option, stack)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isPresent(ItemStack stack) {
        return stack != null && stack.getType() != Material.AIR && stack.getAmount() > 0;
    }

    /** True when a key belongs to Sigil, used to tell our recipes from vanilla's. */
    public boolean isOurs(NamespacedKey key) {
        return key != null && key.getNamespace().equals(plugin.getName().toLowerCase(Locale.ROOT));
    }

    public void unregisterAll() {
        for (NamespacedKey key : registered) {
            try {
                Bukkit.removeRecipe(key);
            } catch (RuntimeException ignored) {
                // Removal can fail if the server is mid-reload; a stale recipe
                // is harmless because strictMatch still governs the result.
            }
        }
        registered.clear();
        craftingRecipes.clear();
    }

    public List<NamespacedKey> registeredKeys() {
        return List.copyOf(registered);
    }

    public List<Entry> craftingEntries() {
        return List.copyOf(craftingRecipes);
    }

    @Override
    public String toString() {
        return "RecipeService(" + registered.size() + " registered, "
                + craftingRecipes.size() + " crafting)";
    }
}
