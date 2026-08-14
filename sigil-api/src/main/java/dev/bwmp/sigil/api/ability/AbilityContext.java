package dev.bwmp.sigil.api.ability;

import dev.bwmp.sigil.api.item.CustomItem;
import dev.bwmp.sigil.api.scheduler.SigilScheduler;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;

/** Everything an ability needs about the activation that reached it. */
public interface AbilityContext {

    Player player();

    /** The stack the ability is running from. Mutating it is allowed and persists. */
    ItemStack stack();

    CustomItem item();

    Trigger trigger();

    TriggerBinding.Slot slot();

    /** The block clicked, broken or placed, when the trigger involves one. */
    Optional<Block> block();

    /** The entity hit, or that did the hitting. */
    Optional<Entity> entity();

    /**
     * The Bukkit event that caused this, when there was one. Typed access
     * rather than a cast, so an ability asking for the wrong type gets an empty
     * Optional instead of a ClassCastException.
     */
    <T extends Event> Optional<T> event(Class<T> type);

    /** Config values for this ability, from a YAML-defined item. Empty for code-defined ones. */
    AbilityConfig config();

    /** Remaining uses, or -1 when unlimited. */
    int remainingUses();

    /**
     * Use this rather than BukkitRunnable. It is what makes an ability work on
     * Folia without the ability knowing which backend it is on.
     */
    SigilScheduler scheduler();

    /**
     * Sends the holder a MiniMessage string.
     * <p>
     * A string rather than a Component for the reason given in
     * {@code SigilScheduler}: Adventure is relocated inside Sigil's jar, so an
     * ability compiled against a {@code Component} here would fail to link at
     * runtime. Sigil parses it on the other side of the boundary.
     */
    void message(String miniMessage);

    /**
     * Same, on the action bar. Usually the right channel for ability feedback —
     * charge counts, shield strength, a failed lock-on — none of which belongs
     * in chat history.
     */
    void actionBar(String miniMessage);
}
