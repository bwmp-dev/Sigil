package dev.bwmp.sigil;

import dev.bwmp.sigil.api.SigilAPI;
import dev.bwmp.sigil.api.ability.Ability;
import dev.bwmp.sigil.api.ability.AbilityType;
import dev.bwmp.sigil.api.item.CustomItem;
import dev.bwmp.sigil.api.item.ItemDefinition;
import dev.bwmp.sigil.api.item.Rarity;
import dev.bwmp.sigil.item.SigilItem;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** The service other plugins receive from Bukkit's service manager. */
public final class SigilApiImpl implements SigilAPI {

    private final SigilPlugin plugin;

    public SigilApiImpl(SigilPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public CustomItem register(Plugin owner, ItemDefinition definition, Ability... abilities) {
        // Recorded as a code definition so it takes part in the same config
        // merge as built-in content: a third-party item gets its own YAML file
        // and can be retuned by the server owner exactly like any other.
        plugin.apiDefinitions().put(definition.id(), definition);
        plugin.apiAbilities().put(definition.id(), List.of(abilities));

        // Coalesced rather than reloading here. An addon registering six items
        // would otherwise trigger six full reloads - re-reading config,
        // rebuilding every definition and re-registering every recipe each
        // time - which is quadratic in the number of addons on the server.
        plugin.scheduleRebuild();

        return plugin.registries().sigilItem(definition.id())
                .map(item -> (CustomItem) item)
                .orElseGet(() -> new SigilItem(definition, List.of(abilities), plugin.factory()));
    }

    @Override
    public void registerAbilityType(Plugin owner, NamespacedKey id, AbilityType type) {
        plugin.registries().abilityTypes().register(owner, id, type);
    }

    @Override
    public Optional<CustomItem> item(NamespacedKey id) {
        return plugin.registries().item(id);
    }

    @Override
    public Collection<CustomItem> items() {
        return plugin.registries().items();
    }

    @Override
    public Optional<Rarity> rarity(String id) {
        return plugin.registries().rarity(id);
    }

    @Override
    public Collection<Rarity> rarities() {
        return plugin.registries().rarities();
    }

    @Override
    public Optional<CustomItem> resolve(ItemStack stack) {
        return plugin.resolver().resolve(stack);
    }

    @Override
    public int remainingUses(ItemStack stack) {
        return plugin.resolver().usesOf(stack);
    }

    @Override
    public boolean refresh(ItemStack stack) {
        return plugin.refreshService().refresh(stack);
    }
}
