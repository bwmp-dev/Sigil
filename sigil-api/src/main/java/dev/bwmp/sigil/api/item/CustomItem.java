package dev.bwmp.sigil.api.item;

import dev.bwmp.sigil.api.ability.Ability;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * A registered item.
 * <p>
 * Sigil has one content type. What other plugins call a "material" is simply a
 * CustomItem with no abilities — which is what makes "a recipe whose ingredient
 * is a custom item" work without a second parallel concept.
 */
public interface CustomItem {

    NamespacedKey id();

    ItemDefinition definition();

    /** Empty for a material. */
    List<Ability> abilities();

    /** A fresh stack, fully rendered and stamped. */
    ItemStack createStack(int amount);

    /** True when {@code stack} is this item, tested by persistent id. */
    boolean matches(ItemStack stack);
}
