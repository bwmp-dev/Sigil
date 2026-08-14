package dev.bwmp.sigil.content;

import dev.bwmp.sigil.api.ability.Ability;
import dev.bwmp.sigil.api.item.InteractionRules;
import dev.bwmp.sigil.api.item.ItemDefinition;
import dev.bwmp.sigil.api.item.Uses;
import dev.bwmp.sigil.api.recipe.Ingredient;
import dev.bwmp.sigil.api.recipe.SigilRecipe;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The demo content, chosen so the set exercises every recipe type and a
 * representative spread of triggers, and doubles as worked documentation.
 * <p>
 * Turned off wholesale with {@code content.builtin: false} for a server that
 * wants only its own items.
 */
public final class BuiltinContent {

    public record Entry(ItemDefinition definition, List<Ability> abilities) {
    }

    private BuiltinContent() {
    }

    private static NamespacedKey key(String name) {
        return Objects.requireNonNull(NamespacedKey.fromString("sigil:" + name));
    }

    public static List<Entry> all() {
        List<Entry> entries = new ArrayList<>();

        entries.add(new Entry(definition("grapple", "Grapple", Material.TRIPWIRE_HOOK, "rare",
                List.of("A handy device for reaching high places."),
                Uses.infinite(),
                new InteractionRules(false, false, null, false),
                List.of(SigilRecipe.shaped(key("grapple"),
                        List.of(" SI", " SL", "I  "),
                        Map.of(
                                'S', Ingredient.vanilla(Material.STRING),
                                'L', Ingredient.vanilla(Material.LEAD),
                                'I', Ingredient.vanilla(Material.IRON_INGOT)), 1)),
                ""),
                List.of(new GrappleAbility())));

        return entries;
    }

    private static ItemDefinition definition(String name, String display, Material base, String rarity,
                                             List<String> description, Uses uses, InteractionRules rules,
                                             List<SigilRecipe> recipes, String category) {
        return new ItemDefinition(key(name), display, base, rarity, description,
                null, -1, uses, rules, recipes, null, true, category);
    }
}
