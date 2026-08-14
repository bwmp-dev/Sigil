package dev.bwmp.sigil;

import dev.bwmp.sigil.api.SigilAPI;
import dev.bwmp.sigil.api.ability.Ability;
import dev.bwmp.sigil.api.ability.AbilityType;
import dev.bwmp.sigil.api.item.CustomItem;
import dev.bwmp.sigil.api.item.ItemDefinition;
import dev.bwmp.sigil.api.item.Rarity;
import dev.bwmp.sigil.api.loot.LootRule;
import dev.bwmp.sigil.item.SigilItem;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
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
    public Collection<CustomItem> itemsWithTag(String tag) {
        if (tag == null || tag.isBlank()) {
            return List.of();
        }
        return plugin.registries().items().stream()
                .filter(item -> item.definition().hasTag(tag))
                .toList();
    }

    @Override
    public dev.bwmp.sigil.api.scheduler.SigilScheduler scheduler() {
        return plugin.scheduler();
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
    public int addUses(ItemStack stack, int amount) {
        return plugin.resolver().idOf(stack)
                .flatMap(plugin.registries()::sigilItem)
                .map(item -> plugin.uses().restore(stack, item, amount))
                .orElse(0);
    }

    @Override
    public long cooldownRemaining(Player player, ItemStack stack, String abilityId) {
        Optional<SigilItem> item = plugin.resolver().idOf(stack).flatMap(plugin.registries()::sigilItem);
        if (item.isEmpty()) {
            return 0L;
        }
        // The scope has to come from the ability itself: the same key under a
        // different scope is a different cooldown, so guessing PLAYER here
        // would silently report 0 for every stack-scoped charge item.
        return item.get().abilities().stream()
                .filter(ability -> ability.meta().id().equals(abilityId))
                .findFirst()
                .map(ability -> plugin.cooldowns().remaining(
                        player, stack, item.get().id() + "/" + abilityId, ability.meta().scope()))
                .orElse(0L);
    }

    @Override
    public void registerLoot(Plugin owner, LootRule rule) {
        plugin.loot().register(owner, rule);
    }

    @Override
    public dev.bwmp.sigil.api.data.PlayerStore playerStore(Plugin owner) {
        return plugin.playerStores().of(owner);
    }

    @Override
    public void send(CommandSender target, String miniMessage) {
        plugin.messenger().send(target, miniMessage);
    }

    @Override
    public void sendActionBar(Player target, String miniMessage) {
        plugin.messenger().sendActionBar(target, miniMessage);
    }

    @Override
    public boolean refresh(ItemStack stack) {
        return plugin.refreshService().refresh(stack);
    }
}
