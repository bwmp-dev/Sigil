package dev.bwmp.sigil.ability;

import dev.bwmp.keystone.scheduler.KeystoneScheduler;
import dev.bwmp.keystone.scheduler.KeystoneTask;
import dev.bwmp.sigil.api.ability.Ability;
import dev.bwmp.sigil.api.ability.Trigger;
import dev.bwmp.sigil.api.ability.TriggerBinding;
import dev.bwmp.sigil.item.SigilItem;
import dev.bwmp.sigil.registry.SigilRegistries;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Polls players for the triggers that have no Bukkit event behind them:
 * {@link Trigger#TICK}, {@link Trigger#EQUIP} and {@link Trigger#UNEQUIP}.
 * <p>
 * Two design points matter here.
 * <p>
 * <b>It does not run unless something needs it.</b> The loop only starts when a
 * registered ability actually declares one of the three triggers, and the
 * interval is configurable rather than fixed. The design this replaces polled
 * every player's hands and armour every two ticks unconditionally.
 * <p>
 * <b>Each player is inspected on the thread that owns them.</b> The outer timer
 * only enumerates players; all reading of inventories happens inside
 * {@code atEntity}. Touching another region's player from a global task is a
 * data race on Folia rather than merely slow, and it is invisible on every
 * other server — which is exactly the kind of bug that only shows up in
 * production.
 */
public final class PlayerWatchService {

    private static final Set<Trigger> WATCHED =
            EnumSet.of(Trigger.TICK, Trigger.EQUIP, Trigger.UNEQUIP);

    private final SigilRegistries registries;
    private final AbilityDispatcher dispatcher;
    private final KeystoneScheduler scheduler;
    private final JavaPlugin plugin;
    private final int intervalTicks;

    private final Map<UUID, ItemStack[]> lastArmour = new ConcurrentHashMap<>();
    private final QuitListener quitListener = new QuitListener();

    private KeystoneTask task;
    private boolean ticksWanted;
    private boolean equipWanted;

    public PlayerWatchService(JavaPlugin plugin, SigilRegistries registries, AbilityDispatcher dispatcher,
                              KeystoneScheduler scheduler, int intervalTicks) {
        this.plugin = plugin;
        this.registries = registries;
        this.dispatcher = dispatcher;
        this.scheduler = scheduler;
        this.intervalTicks = Math.max(1, intervalTicks);
    }

    /** @return true when the loop was actually needed and started */
    public boolean start() {
        stop();

        ticksWanted = wants(Trigger.TICK);
        equipWanted = wants(Trigger.EQUIP) || wants(Trigger.UNEQUIP);
        if (!ticksWanted && !equipWanted) {
            return false;
        }

        plugin.getServer().getPluginManager().registerEvents(quitListener, plugin);
        task = scheduler.runTimer(this::sweep, intervalTicks, intervalTicks);
        return true;
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        HandlerList.unregisterAll(quitListener);
        lastArmour.clear();
    }

    private boolean wants(Trigger trigger) {
        for (SigilItem item : registries.sigilItems()) {
            for (Ability ability : item.abilities()) {
                for (TriggerBinding binding : ability.triggers()) {
                    if (binding.trigger() == trigger) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Enumerates players only. Every actual inspection is handed to the thread
     * owning that player, which is what makes this safe under region threading.
     */
    private void sweep() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            scheduler.atEntity(player, () -> inspect(player));
        }
    }

    private void inspect(Player player) {
        if (!player.isOnline()) {
            lastArmour.remove(player.getUniqueId());
            return;
        }

        if (equipWanted) {
            diffArmour(player);
        }
        if (!ticksWanted) {
            return;
        }

        dispatch(player, player.getInventory().getItemInMainHand(), Trigger.TICK, TriggerBinding.Slot.MAIN_HAND);
        dispatch(player, player.getInventory().getItemInOffHand(), Trigger.TICK, TriggerBinding.Slot.OFF_HAND);
        for (ItemStack armour : player.getInventory().getArmorContents()) {
            dispatch(player, armour, Trigger.TICK, TriggerBinding.Slot.ARMOUR);
        }
    }

    /**
     * Fires UNEQUIP for what left a slot and EQUIP for what entered it.
     * <p>
     * Polled rather than driven by {@code PlayerArmorChangeEvent}, which is
     * Paper-only and absent from the API this module compiles against. Polling
     * costs four comparisons per player per interval and behaves identically on
     * Spigot, Paper and Folia, which is worth more than the latency it adds.
     */
    private void diffArmour(Player player) {
        ItemStack[] current = player.getInventory().getArmorContents();
        ItemStack[] previous = lastArmour.get(player.getUniqueId());

        // Store a defensive copy: getArmorContents already returns clones, but
        // relying on that is a promise the API does not actually make.
        lastArmour.put(player.getUniqueId(), current.clone());

        if (previous == null) {
            // First sighting. Treat what they are wearing as newly equipped, so
            // a player who logs in wearing an ability item gets its EQUIP.
            for (ItemStack stack : current) {
                dispatch(player, stack, Trigger.EQUIP, TriggerBinding.Slot.ARMOUR);
            }
            return;
        }

        for (int slot = 0; slot < Math.min(previous.length, current.length); slot++) {
            ItemStack before = previous[slot];
            ItemStack after = current[slot];
            if (same(before, after)) {
                continue;
            }
            dispatch(player, before, Trigger.UNEQUIP, TriggerBinding.Slot.ARMOUR);
            dispatch(player, after, Trigger.EQUIP, TriggerBinding.Slot.ARMOUR);
        }
    }

    private static boolean same(ItemStack first, ItemStack second) {
        if (first == null || first.getType().isAir()) {
            return second == null || second.getType().isAir();
        }
        return first.isSimilar(second);
    }

    private void dispatch(Player player, ItemStack stack, Trigger trigger, TriggerBinding.Slot slot) {
        if (stack == null || stack.getType().isAir()) {
            return;
        }
        dispatcher.dispatch(player, stack, trigger, slot, TriggerBinding.Target.ANY, null, null, null);
    }

    private final class QuitListener implements Listener {
        @EventHandler
        public void onQuit(PlayerQuitEvent event) {
            lastArmour.remove(event.getPlayer().getUniqueId());
        }
    }

    /** The triggers this service is responsible for, for documentation. */
    public static Set<Trigger> watchedTriggers() {
        return Set.copyOf(WATCHED);
    }
}
