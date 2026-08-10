package dev.bwmp.sigil.item;

import dev.bwmp.sigil.api.ability.Ability;
import dev.bwmp.sigil.api.item.CustomItem;
import dev.bwmp.sigil.api.item.ItemDefinition;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Objects;

/** A registered item: its definition plus the abilities bound to it. */
public final class SigilItem implements CustomItem {

    private final ItemDefinition definition;
    private final List<Ability> abilities;
    private final ItemFactory factory;

    public SigilItem(ItemDefinition definition, List<Ability> abilities, ItemFactory factory) {
        this.definition = definition;
        this.abilities = List.copyOf(abilities);
        this.factory = factory;
    }

    @Override
    public NamespacedKey id() {
        return definition.id();
    }

    @Override
    public ItemDefinition definition() {
        return definition;
    }

    @Override
    public List<Ability> abilities() {
        return abilities;
    }

    @Override
    public ItemStack createStack(int amount) {
        return factory.create(this, amount);
    }

    @Override
    public boolean matches(ItemStack stack) {
        return factory.resolver().idOf(stack).map(definition.id()::equals).orElse(false);
    }

    /** A copy carrying a new definition, for reload without rebuilding abilities. */
    public SigilItem withDefinition(ItemDefinition replacement) {
        return new SigilItem(replacement, abilities, factory);
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof SigilItem && ((SigilItem) other).definition.id().equals(definition.id());
    }

    @Override
    public int hashCode() {
        return Objects.hash(definition.id());
    }

    @Override
    public String toString() {
        return "SigilItem(" + definition.id() + ")";
    }
}
