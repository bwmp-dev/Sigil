package dev.bwmp.sigil.config;

import dev.bwmp.sigil.api.ability.Trigger;
import dev.bwmp.sigil.api.ability.TriggerBinding;
import dev.bwmp.sigil.api.recipe.Ingredient;
import dev.bwmp.sigil.api.recipe.RecipeType;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Lenient parsing of the small vocabulary used in item YAML.
 * <p>
 * Everything returns {@code Optional} rather than throwing, so a single typo
 * becomes one reported problem against one file instead of an exception that
 * stops the rest of the load.
 */
public final class Parsers {

    private Parsers() {
    }

    public static Optional<Material> material(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String name = raw.contains(":") ? raw.substring(raw.indexOf(':') + 1) : raw;
        return Optional.ofNullable(Material.matchMaterial(name.toUpperCase(Locale.ROOT)));
    }

    public static Optional<NamespacedKey> key(String raw, String defaultNamespace) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (!value.contains(":")) {
            value = defaultNamespace + ":" + value;
        }
        return Optional.ofNullable(NamespacedKey.fromString(value));
    }

    public static Optional<Trigger> trigger(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(Trigger.valueOf(raw.trim().toUpperCase(Locale.ROOT).replace('-', '_')));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    public static Optional<TriggerBinding.Target> target(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(TriggerBinding.Target.valueOf(raw.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    public static Optional<RecipeType> recipeType(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(RecipeType.valueOf(raw.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException ignored) {
            return Optional.empty();
        }
    }

    /**
     * Accepts {@code 2s}, {@code 500ms}, {@code 1m} or a bare number of
     * seconds, because config authors write all four and none of them is wrong.
     */
    public static long durationMillis(Object raw, long fallback) {
        if (raw == null) {
            return fallback;
        }
        if (raw instanceof Number) {
            return (long) (((Number) raw).doubleValue() * 1000);
        }

        String text = raw.toString().trim().toLowerCase(Locale.ROOT);
        if (text.isEmpty()) {
            return fallback;
        }
        try {
            if (text.endsWith("ms")) {
                return Long.parseLong(text.substring(0, text.length() - 2).trim());
            }
            if (text.endsWith("s")) {
                return (long) (Double.parseDouble(text.substring(0, text.length() - 1).trim()) * 1000);
            }
            if (text.endsWith("m")) {
                return (long) (Double.parseDouble(text.substring(0, text.length() - 1).trim()) * 60_000);
            }
            return (long) (Double.parseDouble(text) * 1000);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    /**
     * An ingredient, in any of the forms config accepts.
     * <p>
     * A bare {@code IRON_INGOT} is vanilla; {@code namespace:id} is a custom
     * item; a map supplies an amount; a list is "any of". Custom ids are
     * resolved first, so a Sigil item may deliberately shadow a vanilla name —
     * the loader warns when that happens rather than silently picking one.
     */
    public static Optional<Ingredient> ingredient(Object raw, String defaultNamespace,
                                                  Map<NamespacedKey, Material> customExemplars) {
        if (raw == null) {
            return Optional.empty();
        }

        if (raw instanceof List<?> options) {
            List<Ingredient> parsed = new ArrayList<>();
            for (Object option : options) {
                ingredient(option, defaultNamespace, customExemplars).ifPresent(parsed::add);
            }
            return parsed.isEmpty() ? Optional.empty() : Optional.of(Ingredient.anyOf(parsed));
        }

        if (raw instanceof Map<?, ?> map) {
            Object item = map.get("item");
            Object amount = map.get("amount");
            int count = amount instanceof Number ? ((Number) amount).intValue() : 1;
            return ingredient(item, defaultNamespace, customExemplars).map(base -> withAmount(base, count));
        }

        String text = raw.toString().trim();
        if (text.isEmpty()) {
            return Optional.empty();
        }

        if (text.startsWith("#")) {
            return tag(text.substring(1));
        }

        if (text.contains(":")) {
            Optional<NamespacedKey> id = key(text, defaultNamespace);
            if (id.isPresent() && customExemplars.containsKey(id.get())) {
                return Optional.of(Ingredient.custom(id.get(), customExemplars.get(id.get()), 1));
            }
        } else {
            Optional<NamespacedKey> id = key(text, defaultNamespace);
            if (id.isPresent() && customExemplars.containsKey(id.get())) {
                return Optional.of(Ingredient.custom(id.get(), customExemplars.get(id.get()), 1));
            }
        }

        return material(text).map(Ingredient::vanilla);
    }

    /**
     * Resolves {@code #minecraft:planks} to the materials in that tag.
     * <p>
     * Both item and block tag registries are tried, because config authors do
     * not distinguish between them and there is no reason they should.
     * Resolution happens once at load: the recipe then holds a fixed material
     * list, so what it accepts cannot drift and the preview menu has something
     * concrete to draw.
     */
    public static Optional<Ingredient> tag(String raw) {
        String value = raw.trim().toLowerCase(Locale.ROOT);
        NamespacedKey key = NamespacedKey.fromString(value.contains(":") ? value : "minecraft:" + value);
        if (key == null) {
            return Optional.empty();
        }

        for (String registry : new String[]{org.bukkit.Tag.REGISTRY_ITEMS, org.bukkit.Tag.REGISTRY_BLOCKS}) {
            org.bukkit.Tag<Material> tag = org.bukkit.Bukkit.getTag(registry, key, Material.class);
            if (tag == null) {
                continue;
            }
            List<Material> materials = new ArrayList<>(tag.getValues());
            if (!materials.isEmpty()) {
                return Optional.of(Ingredient.tag(key.toString(), materials, 1));
            }
        }
        return Optional.empty();
    }

    private static Ingredient withAmount(Ingredient base, int amount) {
        if (amount <= 1) {
            return base;
        }
        if (base instanceof Ingredient.Vanilla vanilla) {
            return Ingredient.vanilla(vanilla.material(), amount);
        }
        if (base instanceof Ingredient.Custom custom) {
            return Ingredient.custom(custom.id(), custom.exemplarMaterial(), amount);
        }
        return base;
    }
}
