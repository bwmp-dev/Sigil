package dev.bwmp.sigil.config;

import dev.bwmp.sigil.api.item.ItemDefinition;
import dev.bwmp.sigil.api.recipe.Ingredient;
import dev.bwmp.sigil.api.recipe.SigilRecipe;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Writes a default file for each code-registered item on first run.
 * <p>
 * Only ever creates; never overwrites. An admin's edits are theirs, and a
 * plugin that rewrites config on boot is one that loses them.
 */
public final class DefinitionWriter {

    private final File itemsDirectory;
    private final Logger logger;

    public DefinitionWriter(File itemsDirectory, Logger logger) {
        this.itemsDirectory = itemsDirectory;
        this.logger = logger;
    }

    public void writeIfAbsent(ItemDefinition definition) {
        File namespaceDirectory = new File(itemsDirectory, definition.id().getNamespace());
        File file = new File(namespaceDirectory, definition.id().getKey() + ".yml");
        if (file.exists()) {
            return;
        }
        //noinspection ResultOfMethodCallIgnored
        namespaceDirectory.mkdirs();

        YamlConfiguration yaml = new YamlConfiguration();
        yaml.options().header("Sigil item: " + definition.id()
                + "\nEdit freely. Sigil never rewrites this file once it exists."
                + "\nRun /sigil reload to apply changes; items already in inventories update themselves.");

        yaml.set("enabled", definition.enabled());
        yaml.set("display", definition.displayName());
        yaml.set("base", definition.base().name());
        yaml.set("rarity", definition.rarityId());
        if (!definition.description().isEmpty()) {
            yaml.set("description", definition.description());
        }
        if (!definition.category().isEmpty()) {
            yaml.set("category", definition.category());
        }
        if (!definition.tags().isEmpty()) {
            yaml.set("tags", definition.tags());
        }
        if (definition.model() != null) {
            yaml.set("model", definition.model().toString());
        }
        if (definition.customModelData() >= 0) {
            yaml.set("custom-model-data", definition.customModelData());
        }

        if (definition.uses().limited()) {
            yaml.set("uses.type", "limited");
            yaml.set("uses.max", definition.uses().max());
            yaml.set("uses.delete-at-zero", definition.uses().deleteAtZero());
        } else {
            yaml.set("uses.type", "infinite");
        }

        writeRules(yaml, definition);
        writeRecipes(yaml, definition);

        try {
            yaml.save(file);
        } catch (IOException exception) {
            logger.warning("Could not write default config for " + definition.id() + ": " + exception.getMessage());
        }
    }

    private void writeRules(YamlConfiguration yaml, ItemDefinition definition) {
        // Only keys the item has an opinion about. Writing every rule with the
        // current global default would silently pin it, so a later change to
        // the server-wide setting would not reach items that never cared.
        if (definition.rules().vanillaRecipes() != null) {
            yaml.set("rules.vanilla-recipes", definition.rules().vanillaRecipes());
        }
        if (definition.rules().anvilRename() != null) {
            yaml.set("rules.anvil-rename", definition.rules().anvilRename());
        }
        if (definition.rules().anvilCombine() != null) {
            yaml.set("rules.anvil-combine", definition.rules().anvilCombine());
        }
        if (definition.rules().enchanting() != null) {
            yaml.set("rules.enchanting", definition.rules().enchanting());
        }
    }

    private void writeRecipes(YamlConfiguration yaml, ItemDefinition definition) {
        List<SigilRecipe> recipes = definition.recipes();
        if (recipes.isEmpty()) {
            return;
        }

        if (recipes.size() == 1) {
            Map<String, Object> single = describe(recipes.get(0));
            single.forEach((key, value) -> yaml.set("recipe." + key, value));
            return;
        }

        List<Map<String, Object>> all = new ArrayList<>();
        for (SigilRecipe recipe : recipes) {
            all.add(describe(recipe));
        }
        yaml.set("recipes", all);
    }

    private Map<String, Object> describe(SigilRecipe recipe) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", recipe.type().name().toLowerCase(Locale.ROOT));
        if (recipe.amount() != 1) {
            map.put("amount", recipe.amount());
        }

        switch (recipe.type()) {
            case SHAPED -> {
                map.put("shape", recipe.shape());
                Map<String, Object> keys = new LinkedHashMap<>();
                recipe.keys().forEach((symbol, ingredient) -> keys.put(String.valueOf(symbol), describe(ingredient)));
                map.put("keys", keys);
            }
            case SHAPELESS -> {
                List<Object> ingredients = new ArrayList<>();
                recipe.ingredients().forEach(ingredient -> ingredients.add(describe(ingredient)));
                map.put("ingredients", ingredients);
            }
            case FURNACE, BLASTING, SMOKING, CAMPFIRE -> {
                map.put("input", describe(recipe.ingredients().get(0)));
                map.put("cook-time", recipe.cookTime());
                map.put("experience", recipe.experience());
            }
            case SMITHING -> {
                int offset = recipe.hasSmithingTemplate() ? 1 : 0;
                if (offset == 1) {
                    map.put("template", describe(recipe.ingredients().get(0)));
                }
                map.put("base", describe(recipe.ingredients().get(offset)));
                map.put("addition", describe(recipe.ingredients().get(offset + 1)));
            }
            case STONECUTTING -> map.put("input", describe(recipe.ingredients().get(0)));
            default -> {
            }
        }
        return map;
    }

    private Object describe(Ingredient ingredient) {
        if (ingredient instanceof Ingredient.Vanilla vanilla) {
            return ingredient.amount() > 1
                    ? amountMap(vanilla.material().name(), ingredient.amount())
                    : vanilla.material().name();
        }
        if (ingredient instanceof Ingredient.Custom custom) {
            return ingredient.amount() > 1
                    ? amountMap(custom.id().toString(), ingredient.amount())
                    : custom.id().toString();
        }
        if (ingredient instanceof Ingredient.Tag tag) {
            return ingredient.amount() > 1
                    ? amountMap("#" + tag.tagName(), ingredient.amount())
                    : "#" + tag.tagName();
        }
        if (ingredient instanceof Ingredient.AnyOf anyOf) {
            List<Object> options = new ArrayList<>();
            anyOf.options().forEach(option -> options.add(describe(option)));
            return options;
        }
        return "AIR";
    }

    private Map<String, Object> amountMap(String item, int amount) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("item", item);
        map.put("amount", amount);
        return map;
    }
}
