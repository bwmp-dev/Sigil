package dev.bwmp.sigil.content;

import dev.bwmp.sigil.api.ability.Ability;
import dev.bwmp.sigil.api.ability.AbilityConfig;
import dev.bwmp.sigil.api.item.InteractionRules;
import dev.bwmp.sigil.api.item.ItemDefinition;
import dev.bwmp.sigil.api.item.Uses;
import dev.bwmp.sigil.api.recipe.Ingredient;
import dev.bwmp.sigil.api.recipe.RecipeType;
import dev.bwmp.sigil.api.recipe.SigilRecipe;
import dev.bwmp.sigil.ability.builtin.BuiltinAbilities;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The demo content, chosen so the set exercises every recipe type and a
 * representative spread of triggers, and doubles as worked documentation.
 * <p>
 * Turned off wholesale with {@code content.builtin: false} for a server that
 * wants only its own items.
 */
public final class BuiltinContent {

    /** One item's code-side defaults plus the abilities bound to it. */
    public record Entry(ItemDefinition definition, List<Ability> abilities) {
    }

    private BuiltinContent() {
    }

    private static NamespacedKey key(String name) {
        return new NamespacedKey("sigil", name);
    }

    private static Ingredient custom(String name, Material exemplar) {
        return Ingredient.custom(key(name), exemplar, 1);
    }

    public static List<Entry> all() {
        List<Entry> entries = new ArrayList<>();

        entries.add(material("aether_nugget", "Aether Nugget", Material.IRON_NUGGET, "common",
                "A sliver of refined aether.", List.of()));

        entries.add(material("raw_aether", "Raw Aether", Material.RAW_IRON, "common",
                "Unrefined, and faintly warm.", List.of()));

        // Two ways to the same item, which is the point: it proves multi-recipe
        // support and gives the furnace path something to cook.
        entries.add(material("aether_ingot", "Aether Ingot", Material.IRON_INGOT, "uncommon",
                "Refined from raw aether.", List.of(
                        SigilRecipe.shaped(key("aether_ingot"),
                                List.of("NNN", "NNN", "NNN"),
                                Map.of('N', custom("aether_nugget", Material.IRON_NUGGET)), 1),
                        SigilRecipe.cooking(key("aether_ingot"), RecipeType.FURNACE,
                                custom("raw_aether", Material.RAW_IRON), 1, 200, 0.7f))));

        // A reversible pair, so compaction and the shapeless path both get
        // exercised in one place.
        entries.add(material("aether_block", "Aether Block", Material.IRON_BLOCK, "uncommon",
                "Nine ingots, pressed together.", List.of(
                        SigilRecipe.shaped(key("aether_block"),
                                List.of("III", "III", "III"),
                                Map.of('I', custom("aether_ingot", Material.IRON_INGOT)), 1))));

        entries.add(new Entry(definition("aether_blade", "Aether Blade", Material.DIAMOND_SWORD, "epic",
                List.of("Strikes with borrowed lightning."),
                Uses.limited(250, true),
                new InteractionRules(false, false, true, false),
                List.of(SigilRecipe.smithing(key("aether_blade"), null,
                        Ingredient.vanilla(Material.DIAMOND_SWORD),
                        custom("aether_ingot", Material.IRON_INGOT))),
                ""),
                List.of(BuiltinAbilities.all().get("smite")
                        .create("smite", "Smite", config(Map.of(
                                "description", "Calls down lightning on your target.",
                                "cooldown", "6s",
                                "damage", 4.0))))));

        entries.add(new Entry(definition("grapple", "Grapple", Material.TRIPWIRE_HOOK, "rare",
                List.of("A handy device for reaching high places."),
                Uses.infinite(),
                new InteractionRules(false, false, null, false),
                List.of(SigilRecipe.shaped(key("grapple"),
                        List.of(" SI", " SL", "I  "),
                        Map.of(
                                'S', Ingredient.vanilla(Material.STRING),
                                'L', Ingredient.vanilla(Material.LEAD),
                                'I', custom("aether_ingot", Material.IRON_INGOT)), 1)),
                ""),
                List.of(new GrappleAbility())));

        // Exists to exercise EQUIP/UNEQUIP, which have no Bukkit event behind
        // them and are polled instead.
        entries.add(new Entry(definition("aether_boots", "Aether Boots", Material.DIAMOND_BOOTS, "uncommon",
                List.of("Light on your feet while worn."),
                Uses.infinite(),
                new InteractionRules(false, false, null, true),
                List.of(SigilRecipe.shaped(key("aether_boots"),
                        List.of("I I", "I I"),
                        Map.of('I', custom("aether_ingot", Material.IRON_INGOT)), 1)),
                ""),
                List.of(BuiltinAbilities.all().get("worn_effect")
                        .create("swift", "Swift", config(Map.of(
                                "description", "Grants Speed while worn.",
                                "effect", "SPEED",
                                "amplifier", 1))))));

        entries.add(new Entry(definition("prospector", "Prospector", Material.DIAMOND_PICKAXE, "rare",
                List.of("Breaks a wider seam than it should."),
                Uses.limited(500, false),
                new InteractionRules(false, false, true, true),
                List.of(SigilRecipe.stonecutting(key("prospector"),
                        custom("aether_block", Material.IRON_BLOCK), 1)),
                ""),
                List.of(BuiltinAbilities.all().get("area_break")
                        .create("seam", "Seam", config(Map.of(
                                "description", "Breaks the blocks around what you mine.",
                                "radius", 1,
                                "cooldown", "0"))))));

        return entries;
    }

    private static Entry material(String name, String display, Material base, String rarity,
                                  String description, List<SigilRecipe> recipes) {
        return new Entry(definition(name, display, base, rarity, List.of(description),
                Uses.infinite(),
                // Materials are protected from vanilla recipes by default,
                // otherwise an Aether Ingot crafts into an ordinary iron block
                // and its identity is simply gone.
                new InteractionRules(false, false, null, null),
                recipes, "material"),
                List.of());
    }

    private static ItemDefinition definition(String name, String display, Material base, String rarity,
                                             List<String> description, Uses uses, InteractionRules rules,
                                             List<SigilRecipe> recipes, String category) {
        return new ItemDefinition(key(name), display, base, rarity, description,
                null, -1, uses, rules, recipes, null, true, category);
    }

    /** A tiny in-memory config, so code-defined abilities use the same path as YAML ones. */
    private static AbilityConfig config(Map<String, Object> values) {
        Map<String, Object> copy = new LinkedHashMap<>(values);
        return new AbilityConfig() {
            @Override
            public String getString(String key, String fallback) {
                Object value = copy.get(key);
                return value == null ? fallback : String.valueOf(value);
            }

            @Override
            public int getInt(String key, int fallback) {
                Object value = copy.get(key);
                return value instanceof Number ? ((Number) value).intValue() : fallback;
            }

            @Override
            public double getDouble(String key, double fallback) {
                Object value = copy.get(key);
                return value instanceof Number ? ((Number) value).doubleValue() : fallback;
            }

            @Override
            public boolean getBoolean(String key, boolean fallback) {
                Object value = copy.get(key);
                return value instanceof Boolean ? (Boolean) value : fallback;
            }

            @Override
            public List<String> getStringList(String key) {
                Object value = copy.get(key);
                if (value instanceof List<?> list) {
                    List<String> strings = new ArrayList<>();
                    list.forEach(entry -> strings.add(String.valueOf(entry)));
                    return strings;
                }
                return List.of();
            }

            @Override
            public boolean contains(String key) {
                return copy.containsKey(key);
            }
        };
    }
}
