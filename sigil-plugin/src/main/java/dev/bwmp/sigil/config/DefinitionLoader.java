package dev.bwmp.sigil.config;

import dev.bwmp.keystone.config.LoadReport;
import dev.bwmp.sigil.api.ability.Ability;
import dev.bwmp.sigil.api.ability.AbilityType;
import dev.bwmp.sigil.api.item.InteractionRules;
import dev.bwmp.sigil.api.item.ItemDefinition;
import dev.bwmp.sigil.api.item.Uses;
import dev.bwmp.sigil.api.recipe.Ingredient;
import dev.bwmp.sigil.api.recipe.RecipeType;
import dev.bwmp.sigil.api.recipe.SigilRecipe;
import dev.bwmp.sigil.registry.SigilRegistries;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Reads {@code items/&lt;namespace&gt;/&lt;id&gt;.yml} into definitions.
 * <p>
 * Two kinds of file end up here. One overrides an item registered in code,
 * where the code supplies defaults and abilities. The other defines an item
 * outright, with abilities named by type — that is what lets a server add a
 * whole material and recipe chain without writing Java.
 * <p>
 * Problems accumulate into a {@link LoadReport} rather than throwing, so an
 * admin who has made three mistakes learns about all three now.
 */
public final class DefinitionLoader {

    /** A parsed file, before abilities are bound. */
    public record Parsed(ItemDefinition definition, List<Ability> abilities) {
    }

    private final File itemsDirectory;
    private final SigilRegistries registries;

    public DefinitionLoader(File itemsDirectory, SigilRegistries registries) {
        this.itemsDirectory = itemsDirectory;
        this.registries = registries;
    }

    /**
     * Loads every file. Exemplars map custom ids to their base material so
     * ingredient references can resolve; it is built from code-registered items
     * plus a first pass over the files, which is what allows two YAML items to
     * reference each other.
     */
    public Map<NamespacedKey, Parsed> loadAll(Map<NamespacedKey, ItemDefinition> codeDefaults,
                                              Map<NamespacedKey, List<Ability>> codeAbilities,
                                              LoadReport report) {
        Map<NamespacedKey, File> files = discover(report);

        // Pass one: work out what every id's base material is, so that pass two
        // can resolve ingredient references in any order.
        Map<NamespacedKey, Material> exemplars = new LinkedHashMap<>();
        codeDefaults.forEach((id, definition) -> exemplars.put(id, definition.base()));
        files.forEach((id, file) -> {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            Material base = Parsers.material(yaml.getString("base"))
                    .orElseGet(() -> codeDefaults.containsKey(id) ? codeDefaults.get(id).base() : null);
            if (base != null) {
                exemplars.put(id, base);
            }
        });

        Map<NamespacedKey, Parsed> loaded = new LinkedHashMap<>();

        // Code-registered items with no file yet still need to exist.
        codeDefaults.forEach((id, definition) -> {
            if (!files.containsKey(id)) {
                loaded.put(id, new Parsed(definition, codeAbilities.getOrDefault(id, List.of())));
                report.countLoaded();
            }
        });

        files.forEach((id, file) -> {
            try {
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
                String source = itemsDirectory.toPath().relativize(file.toPath()).toString();

                ItemDefinition fallback = codeDefaults.get(id);
                Optional<ItemDefinition> definition = parse(id, yaml, fallback, exemplars, source, report);
                if (definition.isEmpty()) {
                    return;
                }

                List<Ability> abilities = codeAbilities.get(id);
                if (abilities == null || yaml.contains("abilities")) {
                    abilities = parseAbilities(yaml, source, report);
                }

                loaded.put(id, new Parsed(definition.get(), abilities));
                report.countLoaded();
            } catch (RuntimeException exception) {
                report.error(file.getName(), "could not be read: " + exception);
            }
        });

        return loaded;
    }

    private Map<NamespacedKey, File> discover(LoadReport report) {
        Map<NamespacedKey, File> found = new LinkedHashMap<>();
        File[] namespaces = itemsDirectory.listFiles(File::isDirectory);
        if (namespaces == null) {
            return found;
        }

        for (File namespaceDirectory : namespaces) {
            String namespace = namespaceDirectory.getName().toLowerCase(Locale.ROOT);
            File[] files = namespaceDirectory.listFiles((dir, name) -> name.endsWith(".yml"));
            if (files == null) {
                continue;
            }
            for (File file : files) {
                String name = file.getName().substring(0, file.getName().length() - 4).toLowerCase(Locale.ROOT);
                NamespacedKey id = NamespacedKey.fromString(namespace + ":" + name);
                if (id == null) {
                    report.error(file.getName(), "'" + namespace + ":" + name + "' is not a valid id");
                    continue;
                }
                found.put(id, file);
            }
        }
        return found;
    }

    private Optional<ItemDefinition> parse(NamespacedKey id, ConfigurationSection yaml, ItemDefinition fallback,
                                           Map<NamespacedKey, Material> exemplars, String source, LoadReport report) {
        Material base = Parsers.material(yaml.getString("base"))
                .orElseGet(() -> fallback == null ? null : fallback.base());
        if (base == null) {
            report.error(source, "no valid 'base' material"
                    + (yaml.getString("base") == null ? "" : " ('" + yaml.getString("base") + "' is unknown here)"));
            return Optional.empty();
        }

        String display = yaml.getString("display", fallback == null ? id.getKey() : fallback.displayName());
        String rarity = yaml.getString("rarity", fallback == null ? "common" : fallback.rarityId())
                .toLowerCase(Locale.ROOT);
        if (registries.rarity(rarity).isEmpty()) {
            report.warn(source, "unknown rarity '" + rarity + "'; it will render grey");
        }

        List<String> description = yaml.contains("description")
                ? yaml.getStringList("description")
                : (fallback == null ? List.of() : fallback.description());

        NamespacedKey model = Parsers.key(yaml.getString("model"), id.getNamespace())
                .orElseGet(() -> fallback == null ? null : fallback.model());
        int customModelData = yaml.getInt("custom-model-data",
                fallback == null ? -1 : fallback.customModelData());

        Uses uses = parseUses(yaml.getConfigurationSection("uses"),
                fallback == null ? Uses.infinite() : fallback.uses());
        InteractionRules rules = parseRules(yaml.getConfigurationSection("rules"),
                fallback == null ? InteractionRules.inherit() : fallback.rules());

        List<SigilRecipe> recipes = parseRecipes(id, yaml, exemplars, source, report);
        if (recipes.isEmpty() && fallback != null && !yaml.contains("recipe") && !yaml.contains("recipes")) {
            recipes = fallback.recipes();
        }

        String permission = yaml.getString("permission", fallback == null ? null : fallback.permission());
        boolean enabled = yaml.getBoolean("enabled", fallback == null || fallback.enabled());
        String category = yaml.getString("category", fallback == null ? "" : fallback.category());

        // Absent means "keep what the code registered", the same as every other
        // key here. An explicitly empty list is how a file removes tags it
        // inherited, which is not the same thing.
        List<String> tags = yaml.contains("tags")
                ? yaml.getStringList("tags")
                : (fallback == null ? List.of() : fallback.tags());

        return Optional.of(new ItemDefinition(id, display, base, rarity, description, model, customModelData,
                uses, rules, recipes, permission, enabled, category, tags));
    }

    private Uses parseUses(ConfigurationSection section, Uses fallback) {
        if (section == null) {
            return fallback;
        }
        String type = section.getString("type", "infinite").toLowerCase(Locale.ROOT);
        if (!type.equals("limited")) {
            return Uses.infinite();
        }
        return Uses.limited(section.getInt("max", 1), section.getBoolean("delete-at-zero", true));
    }

    private InteractionRules parseRules(ConfigurationSection section, InteractionRules fallback) {
        if (section == null) {
            return fallback;
        }
        return new InteractionRules(
                readRule(section, "vanilla-recipes", fallback.vanillaRecipes()),
                readRule(section, "anvil-rename", fallback.anvilRename()),
                readRule(section, "anvil-combine", fallback.anvilCombine()),
                readRule(section, "enchanting", fallback.enchanting()));
    }

    /**
     * Deliberately not a ternary.
     * <p>
     * Writing {@code section.contains(k) ? section.getBoolean(k) : fallback}
     * looks equivalent but is not: when one branch of a conditional is a
     * primitive {@code boolean} and the other a {@code Boolean}, Java unboxes
     * the whole expression — so a null fallback throws
     * {@code NullPointerException} rather than staying null. Null is meaningful
     * here; it is how an item says "inherit the server default".
     */
    private Boolean readRule(ConfigurationSection section, String key, Boolean fallback) {
        return section.contains(key) ? Boolean.valueOf(section.getBoolean(key)) : fallback;
    }

    private List<Ability> parseAbilities(ConfigurationSection yaml, String source, LoadReport report) {
        List<Ability> abilities = new ArrayList<>();
        List<Map<?, ?>> entries = yaml.getMapList("abilities");

        for (Map<?, ?> entry : entries) {
            Object typeRaw = entry.get("type");
            if (typeRaw == null) {
                report.error(source, "an ability is missing 'type'");
                continue;
            }

            Optional<NamespacedKey> typeId = Parsers.key(typeRaw.toString(), "sigil");
            if (typeId.isEmpty()) {
                report.error(source, "'" + typeRaw + "' is not a valid ability type id");
                continue;
            }

            Optional<AbilityType> type = registries.abilityType(typeId.get());
            if (type.isEmpty()) {
                report.error(source, "no ability type registered as '" + typeId.get()
                        + "'; known types: " + registries.abilityTypes().ids());
                continue;
            }

            ConfigurationSection section = toSection(entry);
            String abilityId = section.getString("id", typeId.get().getKey());
            String name = section.getString("name", abilityId);

            try {
                abilities.add(type.get().create(abilityId, name, new SectionAbilityConfig(section)));
            } catch (RuntimeException exception) {
                report.error(source, "ability '" + abilityId + "' rejected its config: " + exception.getMessage());
            }
        }
        return abilities;
    }

    private List<SigilRecipe> parseRecipes(NamespacedKey id, ConfigurationSection yaml,
                                           Map<NamespacedKey, Material> exemplars, String source, LoadReport report) {
        List<SigilRecipe> recipes = new ArrayList<>();

        if (yaml.isConfigurationSection("recipe")) {
            parseRecipe(id, yaml.getConfigurationSection("recipe"), exemplars, source, report).ifPresent(recipes::add);
        }
        for (Map<?, ?> entry : yaml.getMapList("recipes")) {
            parseRecipe(id, toSection(entry), exemplars, source, report).ifPresent(recipes::add);
        }
        return recipes;
    }

    private Optional<SigilRecipe> parseRecipe(NamespacedKey id, ConfigurationSection section,
                                              Map<NamespacedKey, Material> exemplars, String source,
                                              LoadReport report) {
        if (section == null) {
            return Optional.empty();
        }

        RecipeType type = Parsers.recipeType(section.getString("type", "shaped")).orElse(null);
        if (type == null) {
            report.error(source, "unknown recipe type '" + section.getString("type") + "'");
            return Optional.empty();
        }

        int amount = Math.max(1, section.getInt("amount", 1));
        String namespace = id.getNamespace();

        switch (type) {
            case SHAPED -> {
                List<String> shape = section.getStringList("shape");
                if (shape.isEmpty()) {
                    report.error(source, "shaped recipe has no 'shape'");
                    return Optional.empty();
                }
                Map<Character, Ingredient> keys = new LinkedHashMap<>();
                ConfigurationSection keySection = section.getConfigurationSection("keys");
                if (keySection != null) {
                    for (String symbol : keySection.getKeys(false)) {
                        if (symbol.isEmpty()) {
                            continue;
                        }
                        Optional<Ingredient> ingredient =
                                Parsers.ingredient(keySection.get(symbol), namespace, exemplars);
                        if (ingredient.isEmpty()) {
                            report.error(source, "key '" + symbol + "' -> '"
                                    + keySection.get(symbol) + "' is not a known item or material");
                            return Optional.empty();
                        }
                        keys.put(symbol.charAt(0), ingredient.get());
                    }
                }
                return Optional.of(SigilRecipe.shaped(id, shape, keys, amount));
            }
            case SHAPELESS -> {
                List<Ingredient> ingredients = readIngredientList(section, "ingredients", namespace,
                        exemplars, source, report);
                return ingredients.isEmpty() ? Optional.empty()
                        : Optional.of(SigilRecipe.shapeless(id, ingredients, amount));
            }
            case FURNACE, BLASTING, SMOKING, CAMPFIRE -> {
                Optional<Ingredient> input = Parsers.ingredient(section.get("input"), namespace, exemplars);
                if (input.isEmpty()) {
                    report.error(source, "cooking recipe has no valid 'input'");
                    return Optional.empty();
                }
                return Optional.of(SigilRecipe.cooking(id, type, input.get(), amount,
                        section.getInt("cook-time", 200), (float) section.getDouble("experience", 0.1)));
            }
            case SMITHING -> {
                Optional<Ingredient> base = Parsers.ingredient(section.get("base"), namespace, exemplars);
                Optional<Ingredient> addition = Parsers.ingredient(section.get("addition"), namespace, exemplars);
                if (base.isEmpty() || addition.isEmpty()) {
                    report.error(source, "smithing recipe needs both 'base' and 'addition'");
                    return Optional.empty();
                }
                Ingredient template = Parsers.ingredient(section.get("template"), namespace, exemplars).orElse(null);
                return Optional.of(SigilRecipe.smithing(id, template, base.get(), addition.get()));
            }
            case STONECUTTING -> {
                Optional<Ingredient> input = Parsers.ingredient(section.get("input"), namespace, exemplars);
                if (input.isEmpty()) {
                    report.error(source, "stonecutting recipe has no valid 'input'");
                    return Optional.empty();
                }
                return Optional.of(SigilRecipe.stonecutting(id, input.get(), amount));
            }
            default -> {
                return Optional.empty();
            }
        }
    }

    private List<Ingredient> readIngredientList(ConfigurationSection section, String path, String namespace,
                                                Map<NamespacedKey, Material> exemplars, String source,
                                                LoadReport report) {
        List<Ingredient> ingredients = new ArrayList<>();
        List<?> raw = section.getList(path);
        if (raw == null || raw.isEmpty()) {
            report.error(source, "recipe has no '" + path + "'");
            return ingredients;
        }
        for (Object entry : raw) {
            Optional<Ingredient> ingredient = Parsers.ingredient(entry, namespace, exemplars);
            if (ingredient.isEmpty()) {
                report.error(source, "'" + entry + "' is not a known item or material");
                return List.of();
            }
            ingredients.add(ingredient.get());
        }
        return ingredients;
    }

    /**
     * Wraps a raw map as a configuration section, recursively.
     * <p>
     * Bukkit converts maps to sections when it loads a file, but only down the
     * spine of the document — entries inside a list stay raw {@code Map}s, and
     * so does anything nested inside them. Setting such a map directly would
     * leave {@code getConfigurationSection} returning null, which is how a
     * recipe's whole {@code keys} block silently reads as empty and the recipe
     * registers with no ingredients at all.
     */
    private static ConfigurationSection toSection(Map<?, ?> map) {
        YamlConfiguration holder = new YamlConfiguration();
        copyInto(holder, map);
        return holder;
    }

    private static void copyInto(ConfigurationSection section, Map<?, ?> map) {
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = String.valueOf(entry.getKey());
            Object value = entry.getValue();
            if (value instanceof Map<?, ?> nested) {
                copyInto(section.createSection(key), nested);
            } else {
                section.set(key, value);
            }
        }
    }
}
