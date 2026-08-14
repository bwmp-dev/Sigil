package dev.bwmp.sigil.api;

import dev.bwmp.sigil.api.ability.AbilityType;
import dev.bwmp.sigil.api.data.PlayerStore;
import dev.bwmp.sigil.api.item.CustomItem;
import dev.bwmp.sigil.api.item.ItemDefinition;
import dev.bwmp.sigil.api.item.Rarity;
import dev.bwmp.sigil.api.loot.LootRule;
import dev.bwmp.sigil.api.scheduler.SigilScheduler;
import dev.bwmp.sigil.api.visual.DisplayService;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.Collection;
import java.util.Optional;

/**
 * Sigil's public entry point, published through Bukkit's service manager.
 * <p>
 * Bukkit's own services manager is used rather than a static holder because it
 * is the one registry that genuinely spans plugin boundaries, and because it
 * makes the dependency explicit and unregisters cleanly.
 */
public interface SigilAPI {

    static Optional<SigilAPI> get() {
        RegisteredServiceProvider<SigilAPI> provider =
                Bukkit.getServicesManager().getRegistration(SigilAPI.class);
        return provider == null ? Optional.empty() : Optional.of(provider.getProvider());
    }

    /** Registers an item defined in code. Config may still override its fields. */
    CustomItem register(Plugin owner, ItemDefinition definition, dev.bwmp.sigil.api.ability.Ability... abilities);

    /**
     * Registers an ability type usable from YAML by any item on the server.
     * The id's namespace must match {@code owner}.
     */
    void registerAbilityType(Plugin owner, NamespacedKey id, AbilityType type);

    Optional<CustomItem> item(NamespacedKey id);

    Collection<CustomItem> items();

    /**
     * Every registered item carrying {@code tag}, case-insensitively.
     * <p>
     * For anything that has to act on a <em>set</em> of items chosen by the
     * server owner rather than on a list fixed in code.
     */
    Collection<CustomItem> itemsWithTag(String tag);

    /**
     * Sigil's scheduler, for work that is not an ability activation.
     * <p>
     * An ability gets one through its context, but a passive system — a set
     * bonus, a periodic sweep — has no activation to get one from, and using
     * {@code BukkitRunnable} instead is what makes an add-on Folia-incompatible.
     */
    SigilScheduler scheduler();

    /** Shared, lifecycle-managed display effects; unavailable before 1.19.4. */
    DisplayService displays();

    Optional<Rarity> rarity(String id);

    Collection<Rarity> rarities();

    /** The item a stack is, if any. The single supported way to identify one. */
    Optional<CustomItem> resolve(ItemStack stack);

    /** Convenience for {@code resolve(stack).isPresent()}. */
    default boolean isCustom(ItemStack stack) {
        return resolve(stack).isPresent();
    }

    /**
     * Remaining uses on a stack, or -1 when unlimited or not a Sigil item.
     */
    int remainingUses(ItemStack stack);

    /**
     * Restores charges to a stack, capped at the item's maximum, and re-renders
     * its lore.
     * <p>
     * The counterpart to the dispatcher spending them, which is otherwise the
     * only thing that can move the number. Without this a repair kit — or any
     * ability that gives charges back — would have to write Sigil's persistent
     * data itself and guess at the key.
     *
     * @param amount charges to add; negative removes them
     * @return charges actually added, so a caller can refuse to spend itself on
     *         an item that was already full
     */
    int addUses(ItemStack stack, int amount);

    /**
     * Milliseconds until {@code abilityId} on {@code stack} may next run for
     * this player, or 0 when it is ready.
     * <p>
     * Read-only, and it does not start a cooldown. For a GUI or an action-bar
     * readout that has to show a countdown without touching it.
     */
    long cooldownRemaining(Player player, ItemStack stack, String abilityId);

    /**
     * Adds a way for a registered item to be found in the world.
     * <p>
     * Rules registered here sit alongside the server owner's {@code loot.yml}
     * entries rather than replacing them, so an add-on ships working drops out
     * of the box and the owner can still add or retune their own. Dropped
     * automatically when {@code owner} disables.
     */
    void registerLoot(Plugin owner, LootRule rule);

    /**
     * A persistent per-player store scoped to {@code owner}.
     * <p>
     * For resources that belong to the player rather than to an item — a mana
     * pool, an unlock, a running total. Item state belongs on the stack, where
     * it travels with the item; this is for what does not.
     */
    PlayerStore playerStore(Plugin owner);

    /**
     * Sends a MiniMessage string, resolved against Sigil's own parser.
     * <p>
     * Exists because Adventure is relocated inside Sigil's jar: an add-on
     * cannot build a Component that Sigil would accept, and on Spigot it has no
     * Adventure at all. Passing the markup across as text is the only form that
     * works on every supported platform.
     */
    void send(CommandSender target, String miniMessage);

    void sendActionBar(Player target, String miniMessage);

    /**
     * Re-renders a stack against the current definition if it is stale,
     * preserving uses and any foreign persistent data.
     *
     * @return true when the stack was changed
     */
    boolean refresh(ItemStack stack);
}
