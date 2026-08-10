package dev.bwmp.sigil.api.item;

import dev.bwmp.sigil.api.recipe.SigilRecipe;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;

import java.util.List;
import java.util.Objects;

/**
 * Everything configurable about an item, resolved once at load.
 * <p>
 * Immutable, and read from a snapshot at runtime. The design this replaces
 * re-parsed the YAML file on every getter — six full file reads per lore
 * render — which is why every accessor here is a record component rather than
 * a lookup.
 *
 * @param id           namespaced identity, the only thing stamped onto stacks
 * @param displayName  MiniMessage
 * @param base         the vanilla material the item is built from
 * @param rarityId     resolved against the rarity registry at render time
 * @param description  MiniMessage lore lines
 * @param model        named item model key, or null
 * @param customModelData legacy fallback, or -1
 * @param uses         charge policy
 * @param rules        vanilla-mechanic behaviour
 * @param recipes      zero or more ways to obtain it
 * @param permission   explicit permission node, or null to use the default
 * @param enabled      whether it is registered at all
 * @param category     free-form grouping for menus, e.g. "material"
 */
public record ItemDefinition(
        NamespacedKey id,
        String displayName,
        Material base,
        String rarityId,
        List<String> description,
        NamespacedKey model,
        int customModelData,
        Uses uses,
        InteractionRules rules,
        List<SigilRecipe> recipes,
        String permission,
        boolean enabled,
        String category) {

    public ItemDefinition {
        description = description == null ? List.of() : List.copyOf(description);
        recipes = recipes == null ? List.of() : List.copyOf(recipes);
        uses = uses == null ? Uses.infinite() : uses;
        rules = rules == null ? InteractionRules.inherit() : rules;
    }

    /**
     * A hash over only the fields that affect a rendered stack.
     * <p>
     * Stamped onto every stack so that a config edit can be detected later and
     * the stack re-rendered in place. Deliberately excludes recipes, rules and
     * the enabled flag: changing those does not alter how an existing stack
     * looks, and including them would churn every item in every inventory for
     * no visible reason.
     */
    public int revision() {
        return Objects.hash(displayName, base, rarityId, description, model, customModelData,
                uses.limited(), uses.max(), uses.deleteAtZero());
    }

    public boolean isMaterial() {
        return "material".equalsIgnoreCase(category);
    }
}
